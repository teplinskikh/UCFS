/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesClusterStateUpdateRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateAckListener;
import org.elasticsearch.cluster.ClusterStateTaskExecutor;
import org.elasticsearch.cluster.ClusterStateTaskListener;
import org.elasticsearch.cluster.SimpleBatchedAckListenerTaskExecutor;
import org.elasticsearch.cluster.metadata.AliasAction.NewAliasValidator;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.cluster.service.MasterServiceTaskQueue;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.xcontent.NamedXContentRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.elasticsearch.indices.cluster.IndicesClusterStateService.AllocatedIndices.IndexRemovalReason.NO_LONGER_ASSIGNED;

/**
 * Service responsible for submitting add and remove aliases requests
 */
public class MetadataIndexAliasesService {

    private final IndicesService indicesService;

    private final NamedXContentRegistry xContentRegistry;

    private final ClusterStateTaskExecutor<ApplyAliasesTask> executor;
    private final MasterServiceTaskQueue<ApplyAliasesTask> taskQueue;
    private final ClusterService clusterService;

    @Inject
    public MetadataIndexAliasesService(
        ClusterService clusterService,
        IndicesService indicesService,
        NamedXContentRegistry xContentRegistry
    ) {
        this.clusterService = clusterService;
        this.indicesService = indicesService;
        this.xContentRegistry = xContentRegistry;
        this.executor = new SimpleBatchedAckListenerTaskExecutor<>() {

            @Override
            public Tuple<ClusterState, ClusterStateAckListener> executeTask(ApplyAliasesTask applyAliasesTask, ClusterState clusterState) {
                return new Tuple<>(applyAliasActions(clusterState, applyAliasesTask.request().actions()), applyAliasesTask);
            }
        };
        this.taskQueue = clusterService.createTaskQueue("index-aliases", Priority.URGENT, this.executor);
    }

    public void indicesAliases(
        final IndicesAliasesClusterStateUpdateRequest request,
        final ActionListener<IndicesAliasesResponse> listener
    ) {
        taskQueue.submitTask("index-aliases", new ApplyAliasesTask(request, listener), null); 
    }

    /**
     * Handles the cluster state transition to a version that reflects the provided {@link AliasAction}s.
     */
    public ClusterState applyAliasActions(ClusterState currentState, Iterable<AliasAction> actions) {
        List<Index> indicesToClose = new ArrayList<>();
        Map<String, IndexService> indices = new HashMap<>();
        try {
            boolean changed = false;
            Set<Index> indicesToDelete = new HashSet<>();
            for (AliasAction action : actions) {
                if (action.removeIndex()) {
                    IndexMetadata index = currentState.metadata().getIndices().get(action.getIndex());
                    if (index == null) {
                        throw new IndexNotFoundException(action.getIndex());
                    }
                    validateAliasTargetIsNotDSBackingIndex(currentState, action);
                    indicesToDelete.add(index.getIndex());
                    changed = true;
                }
            }
            if (changed) {
                currentState = MetadataDeleteIndexService.deleteIndices(currentState, indicesToDelete, clusterService.getSettings());
            }
            Metadata.Builder metadata = Metadata.builder(currentState.metadata());
            final Set<String> maybeModifiedIndices = new HashSet<>();
            for (AliasAction action : actions) {
                if (action.removeIndex()) {
                    continue;
                }

                /* It is important that we look up the index using the metadata builder we are modifying so we can remove an
                 * index and replace it with an alias. */
                Function<String, String> lookup = name -> {
                    IndexMetadata imd = metadata.get(name);
                    if (imd != null) {
                        return imd.getIndex().getName();
                    }
                    DataStream dataStream = metadata.dataStream(name);
                    if (dataStream != null) {
                        return dataStream.getName();
                    }
                    return null;
                };

                DataStream dataStream = metadata.dataStream(action.getIndex());
                if (dataStream != null) {
                    NewAliasValidator newAliasValidator = (alias, indexRouting, searchRouting, filter, writeIndex) -> {
                        AliasValidator.validateAlias(alias, action.getIndex(), indexRouting, lookup);
                        if (Strings.hasLength(filter)) {
                            for (Index index : dataStream.getIndices()) {
                                IndexMetadata imd = metadata.get(index.getName());
                                if (imd == null) {
                                    throw new IndexNotFoundException(action.getIndex());
                                }
                                IndexSettings.MODE.get(imd.getSettings()).validateAlias(indexRouting, searchRouting);
                                validateFilter(indicesToClose, indices, action, imd, alias, filter);
                            }
                        }
                    };
                    if (action.apply(newAliasValidator, metadata, null)) {
                        changed = true;
                    }
                    continue;
                }

                IndexMetadata index = metadata.get(action.getIndex());
                if (index == null) {
                    throw new IndexNotFoundException(action.getIndex());
                }
                validateAliasTargetIsNotDSBackingIndex(currentState, action);
                NewAliasValidator newAliasValidator = (alias, indexRouting, searchRouting, filter, writeIndex) -> {
                    AliasValidator.validateAlias(alias, action.getIndex(), indexRouting, lookup);
                    IndexSettings.MODE.get(index.getSettings()).validateAlias(indexRouting, searchRouting);
                    if (Strings.hasLength(filter)) {
                        validateFilter(indicesToClose, indices, action, index, alias, filter);
                    }
                };
                if (action.apply(newAliasValidator, metadata, index)) {
                    changed = true;
                    maybeModifiedIndices.add(index.getIndex().getName());
                }
            }

            for (final String maybeModifiedIndex : maybeModifiedIndices) {
                final IndexMetadata currentIndexMetadata = currentState.metadata().index(maybeModifiedIndex);
                final IndexMetadata newIndexMetadata = metadata.get(maybeModifiedIndex);
                if (currentIndexMetadata.getAliases().equals(newIndexMetadata.getAliases()) == false) {
                    assert currentIndexMetadata.getAliasesVersion() == newIndexMetadata.getAliasesVersion();
                    metadata.put(new IndexMetadata.Builder(newIndexMetadata).aliasesVersion(1 + currentIndexMetadata.getAliasesVersion()));
                }
            }

            if (changed) {
                ClusterState updatedState = ClusterState.builder(currentState).metadata(metadata).build();
                if (updatedState.metadata().equalsAliases(currentState.metadata()) == false) {
                    return updatedState;
                }
            }
            return currentState;
        } finally {
            for (Index index : indicesToClose) {
                indicesService.removeIndex(index, NO_LONGER_ASSIGNED, "created for alias processing");
            }
        }
    }

    ClusterStateTaskExecutor<ApplyAliasesTask> getExecutor() {
        return executor;
    }

    private void validateFilter(
        List<Index> indicesToClose,
        Map<String, IndexService> indices,
        AliasAction action,
        IndexMetadata index,
        String alias,
        String filter
    ) {
        IndexService indexService = indices.get(index.getIndex().getName());
        if (indexService == null) {
            indexService = indicesService.indexService(index.getIndex());
            if (indexService == null) {
                try {
                    indexService = indicesService.createIndex(index, emptyList(), false);
                    indicesToClose.add(index.getIndex());
                } catch (IOException e) {
                    throw new ElasticsearchException("Failed to create temporary index for parsing the alias", e);
                }
                indexService.mapperService().merge(index, MapperService.MergeReason.MAPPING_RECOVERY);
            }
            indices.put(action.getIndex(), indexService);
        }
        AliasValidator.validateAliasFilter(
            alias,
            filter,
            indexService.newSearchExecutionContext(0, 0, null, System::currentTimeMillis, null, emptyMap()),
            xContentRegistry
        );
    }

    private static void validateAliasTargetIsNotDSBackingIndex(ClusterState currentState, AliasAction action) {
        IndexAbstraction indexAbstraction = currentState.metadata().getIndicesLookup().get(action.getIndex());
        assert indexAbstraction != null : "invalid cluster metadata. index [" + action.getIndex() + "] was not found";
        if (indexAbstraction.getParentDataStream() != null) {
            throw new IllegalArgumentException(
                "The provided index ["
                    + action.getIndex()
                    + "] is a backing index belonging to data stream ["
                    + indexAbstraction.getParentDataStream().getName()
                    + "]. Data stream backing indices don't support alias operations."
            );
        }
    }

    /**
     * A cluster state update task that consists of the cluster state request and the listeners that need to be notified upon completion.
     */
    record ApplyAliasesTask(IndicesAliasesClusterStateUpdateRequest request, ActionListener<IndicesAliasesResponse> listener)
        implements
            ClusterStateTaskListener,
            ClusterStateAckListener {

        @Override
        public void onFailure(Exception e) {
            listener.onFailure(e);
        }

        @Override
        public boolean mustAck(DiscoveryNode discoveryNode) {
            return true;
        }

        @Override
        public void onAllNodesAcked() {
            listener.onResponse(IndicesAliasesResponse.build(request.getActionResults()));
        }

        @Override
        public void onAckFailure(Exception e) {
            listener.onResponse(IndicesAliasesResponse.NOT_ACKNOWLEDGED);
        }

        @Override
        public void onAckTimeout() {
            listener.onResponse(IndicesAliasesResponse.NOT_ACKNOWLEDGED);
        }

        @Override
        public TimeValue ackTimeout() {
            return request.ackTimeout();
        }
    }
}
/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.bulk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRunnable;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.admin.indices.create.AutoCreateAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.rollover.LazyRolloverAction;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest;
import org.elasticsearch.action.admin.indices.rollover.RolloverResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.ingest.IngestActionForwarder;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.action.support.RefCountingRunnable;
import org.elasticsearch.action.support.WriteResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.internal.OriginSettingClient;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateObserver;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.IndexAbstraction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.MetadataIndexTemplateService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.concurrent.AtomicArray;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.core.Assertions;
import org.elasticsearch.core.Releasable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.features.FeatureService;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexingPressure;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.indices.SystemIndices;
import org.elasticsearch.ingest.IngestService;
import org.elasticsearch.node.NodeClosedException;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.threadpool.ThreadPool.Names;
import org.elasticsearch.transport.TransportService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static org.elasticsearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.elasticsearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;

/**
 * Groups bulk request items by shard, optionally creating non-existent indices and
 * delegates to {@link TransportShardBulkAction} for shard-level bulk execution
 */
public class TransportBulkAction extends HandledTransportAction<BulkRequest, BulkResponse> {

    public static final String NAME = "indices:data/write/bulk";
    public static final ActionType<BulkResponse> TYPE = new ActionType<>(NAME);
    private static final Logger logger = LogManager.getLogger(TransportBulkAction.class);
    public static final String LAZY_ROLLOVER_ORIGIN = "lazy_rollover";

    private final ActionType<BulkResponse> bulkAction;
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final IngestService ingestService;
    private final FeatureService featureService;
    private final LongSupplier relativeTimeProvider;
    private final IngestActionForwarder ingestForwarder;
    private final NodeClient client;
    private final IndexNameExpressionResolver indexNameExpressionResolver;
    private final IndexingPressure indexingPressure;
    private final SystemIndices systemIndices;
    private final OriginSettingClient rolloverClient;

    private final Executor writeExecutor;
    private final Executor systemWriteExecutor;

    @Inject
    public TransportBulkAction(
        ThreadPool threadPool,
        TransportService transportService,
        ClusterService clusterService,
        IngestService ingestService,
        FeatureService featureService,
        NodeClient client,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        IndexingPressure indexingPressure,
        SystemIndices systemIndices
    ) {
        this(
            threadPool,
            transportService,
            clusterService,
            ingestService,
            featureService,
            client,
            actionFilters,
            indexNameExpressionResolver,
            indexingPressure,
            systemIndices,
            System::nanoTime
        );
    }

    public TransportBulkAction(
        ThreadPool threadPool,
        TransportService transportService,
        ClusterService clusterService,
        IngestService ingestService,
        FeatureService featureService,
        NodeClient client,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        IndexingPressure indexingPressure,
        SystemIndices systemIndices,
        LongSupplier relativeTimeProvider
    ) {
        this(
            TYPE,
            BulkRequest::new,
            threadPool,
            transportService,
            clusterService,
            ingestService,
            featureService,
            client,
            actionFilters,
            indexNameExpressionResolver,
            indexingPressure,
            systemIndices,
            relativeTimeProvider
        );
    }

    TransportBulkAction(
        ActionType<BulkResponse> bulkAction,
        Writeable.Reader<BulkRequest> requestReader,
        ThreadPool threadPool,
        TransportService transportService,
        ClusterService clusterService,
        IngestService ingestService,
        FeatureService featureService,
        NodeClient client,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        IndexingPressure indexingPressure,
        SystemIndices systemIndices,
        LongSupplier relativeTimeProvider
    ) {
        super(bulkAction.name(), transportService, actionFilters, requestReader, EsExecutors.DIRECT_EXECUTOR_SERVICE);
        Objects.requireNonNull(relativeTimeProvider);
        this.bulkAction = bulkAction;
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.ingestService = ingestService;
        this.featureService = featureService;
        this.relativeTimeProvider = relativeTimeProvider;
        this.ingestForwarder = new IngestActionForwarder(transportService);
        this.client = client;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.indexingPressure = indexingPressure;
        this.systemIndices = systemIndices;
        clusterService.addStateApplier(this.ingestForwarder);
        this.rolloverClient = new OriginSettingClient(client, LAZY_ROLLOVER_ORIGIN);
        this.writeExecutor = threadPool.executor(Names.WRITE);
        this.systemWriteExecutor = threadPool.executor(Names.SYSTEM_WRITE);
    }

    /**
     * Retrieves the {@link IndexRequest} from the provided {@link DocWriteRequest} for index or upsert actions.  Upserts are
     * modeled as {@link IndexRequest} inside the {@link UpdateRequest}. Ignores {@link org.elasticsearch.action.delete.DeleteRequest}'s
     *
     * @param docWriteRequest The request to find the {@link IndexRequest}
     * @return the found {@link IndexRequest} or {@code null} if one can not be found.
     */
    public static IndexRequest getIndexWriteRequest(DocWriteRequest<?> docWriteRequest) {
        IndexRequest indexRequest = null;
        if (docWriteRequest instanceof IndexRequest) {
            indexRequest = (IndexRequest) docWriteRequest;
        } else if (docWriteRequest instanceof UpdateRequest updateRequest) {
            indexRequest = updateRequest.docAsUpsert() ? updateRequest.doc() : updateRequest.upsertRequest();
        }
        return indexRequest;
    }

    public static <Response extends ReplicationResponse & WriteResponse> ActionListener<BulkResponse> unwrappingSingleItemBulkResponse(
        final ActionListener<Response> listener
    ) {
        return listener.delegateFailureAndWrap((l, bulkItemResponses) -> {
            assert bulkItemResponses.getItems().length == 1 : "expected exactly one item in bulk response";
            final BulkItemResponse bulkItemResponse = bulkItemResponses.getItems()[0];
            if (bulkItemResponse.isFailed() == false) {
                @SuppressWarnings("unchecked")
                final Response response = (Response) bulkItemResponse.getResponse();
                l.onResponse(response);
            } else {
                l.onFailure(bulkItemResponse.getFailure().getCause());
            }
        });
    }

    @Override
    protected void doExecute(Task task, BulkRequest bulkRequest, ActionListener<BulkResponse> listener) {
        /*
         * This is called on the Transport thread so we can check the indexing
         * memory pressure *quickly* but we don't want to keep the transport
         * thread busy. Then, as soon as we have the indexing pressure in we fork
         * to one of the write thread pools. We do this because juggling the
         * bulk request can get expensive for a few reasons:
         * 1. Figuring out which shard should receive a bulk request might require
         *    parsing the _source.
         * 2. When dispatching the sub-requests to shards we may have to compress
         *    them. LZ4 is super fast, but slow enough that it's best not to do it
         *    on the transport thread, especially for large sub-requests.
         *
         * We *could* detect these cases and only fork in then, but that is complex
         * to get right and the fork is fairly low overhead.
         */
        final int indexingOps = bulkRequest.numberOfActions();
        final long indexingBytes = bulkRequest.ramBytesUsed();
        final boolean isOnlySystem = isOnlySystem(bulkRequest, clusterService.state().metadata().getIndicesLookup(), systemIndices);
        final Releasable releasable = indexingPressure.markCoordinatingOperationStarted(indexingOps, indexingBytes, isOnlySystem);
        final ActionListener<BulkResponse> releasingListener = ActionListener.runBefore(listener, releasable::close);
        final Executor executor = isOnlySystem ? systemWriteExecutor : writeExecutor;
        ensureClusterStateThenForkAndExecute(task, bulkRequest, executor, releasingListener);
    }

    private void ensureClusterStateThenForkAndExecute(
        Task task,
        BulkRequest bulkRequest,
        Executor executor,
        ActionListener<BulkResponse> releasingListener
    ) {
        final ClusterState initialState = clusterService.state();
        final ClusterBlockException blockException = initialState.blocks().globalBlockedException(ClusterBlockLevel.WRITE);
        if (blockException != null) {
            if (false == blockException.retryable()) {
                releasingListener.onFailure(blockException);
                return;
            }
            logger.trace("cluster is blocked, waiting for it to recover", blockException);
            final ClusterStateObserver clusterStateObserver = new ClusterStateObserver(
                initialState,
                clusterService,
                bulkRequest.timeout(),
                logger,
                threadPool.getThreadContext()
            );
            clusterStateObserver.waitForNextChange(new ClusterStateObserver.Listener() {
                @Override
                public void onNewClusterState(ClusterState state) {
                    forkAndExecute(task, bulkRequest, executor, releasingListener);
                }

                @Override
                public void onClusterServiceClose() {
                    releasingListener.onFailure(new NodeClosedException(clusterService.localNode()));
                }

                @Override
                public void onTimeout(TimeValue timeout) {
                    releasingListener.onFailure(blockException);
                }
            }, newState -> false == newState.blocks().hasGlobalBlockWithLevel(ClusterBlockLevel.WRITE));
        } else {
            forkAndExecute(task, bulkRequest, executor, releasingListener);
        }
    }

    private void forkAndExecute(Task task, BulkRequest bulkRequest, Executor executor, ActionListener<BulkResponse> releasingListener) {
        executor.execute(new ActionRunnable<>(releasingListener) {
            @Override
            protected void doRun() {
                doInternalExecute(task, bulkRequest, executor, releasingListener);
            }
        });
    }

    protected void doInternalExecute(Task task, BulkRequest bulkRequest, Executor executor, ActionListener<BulkResponse> listener) {
        final long startTime = relativeTime();

        boolean hasIndexRequestsWithPipelines = false;
        final Metadata metadata = clusterService.state().getMetadata();
        for (DocWriteRequest<?> actionRequest : bulkRequest.requests) {
            IndexRequest indexRequest = getIndexWriteRequest(actionRequest);
            if (indexRequest != null) {
                IngestService.resolvePipelinesAndUpdateIndexRequest(actionRequest, indexRequest, metadata);
                hasIndexRequestsWithPipelines |= IngestService.hasPipeline(indexRequest);
            }

            if (actionRequest instanceof IndexRequest ir) {
                if (ir.getAutoGeneratedTimestamp() != IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP) {
                    throw new IllegalArgumentException("autoGeneratedTimestamp should not be set externally");
                }
            }
        }

        if (hasIndexRequestsWithPipelines) {
            ActionListener.run(listener, l -> {
                if (Assertions.ENABLED) {
                    final boolean arePipelinesResolved = bulkRequest.requests()
                        .stream()
                        .map(TransportBulkAction::getIndexWriteRequest)
                        .filter(Objects::nonNull)
                        .allMatch(IndexRequest::isPipelineResolved);
                    assert arePipelinesResolved : bulkRequest;
                }
                if (clusterService.localNode().isIngestNode()) {
                    processBulkIndexIngestRequest(task, bulkRequest, executor, metadata, l);
                } else {
                    ingestForwarder.forwardIngestRequest(bulkAction, bulkRequest, l);
                }
            });
            return;
        }

        final Map<String, ReducedRequestInfo> indices = bulkRequest.requests.stream()
            .filter(
                request -> request.opType() != DocWriteRequest.OpType.DELETE
                    || request.versionType() == VersionType.EXTERNAL
                    || request.versionType() == VersionType.EXTERNAL_GTE
            )
            .collect(
                Collectors.toMap(
                    DocWriteRequest::index,
                    request -> ReducedRequestInfo.of(request.isRequireAlias(), request.isRequireDataStream()),
                    (existing, updated) -> ReducedRequestInfo.of(
                        existing.isRequireAlias || updated.isRequireAlias,
                        existing.isRequireDataStream || updated.isRequireDataStream
                    )
                )
            );

        final Map<String, IndexNotFoundException> indicesThatCannotBeCreated = new HashMap<>();
        final ClusterState state = clusterService.state();
        Map<String, Boolean> indicesToAutoCreate = indices.entrySet()
            .stream()
            .filter(entry -> indexNameExpressionResolver.hasIndexAbstraction(entry.getKey(), state) == false)
            .filter(entry -> entry.getValue().isRequireAlias == false)
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().isRequireDataStream));

        Set<String> dataStreamsToBeRolledOver = featureService.clusterHasFeature(state, LazyRolloverAction.DATA_STREAM_LAZY_ROLLOVER)
            ? indices.keySet().stream().filter(target -> {
                DataStream dataStream = state.metadata().dataStreams().get(target);
                return dataStream != null && dataStream.rolloverOnWrite();
            }).collect(Collectors.toSet())
            : Set.of();

        createMissingIndicesAndIndexData(
            task,
            bulkRequest,
            executor,
            listener,
            indicesToAutoCreate,
            dataStreamsToBeRolledOver,
            indicesThatCannotBeCreated,
            startTime
        );
    }

    /*
     * This method is responsible for creating any missing indices, rolling over a data stream when needed and then
     *  indexing the data in the BulkRequest
     */
    protected void createMissingIndicesAndIndexData(
        Task task,
        BulkRequest bulkRequest,
        Executor executor,
        ActionListener<BulkResponse> listener,
        Map<String, Boolean> indicesToAutoCreate,
        Set<String> dataStreamsToBeRolledOver,
        Map<String, IndexNotFoundException> indicesThatCannotBeCreated,
        long startTime
    ) {
        final AtomicArray<BulkItemResponse> responses = new AtomicArray<>(bulkRequest.requests.size());
        if (indicesToAutoCreate.isEmpty() && dataStreamsToBeRolledOver.isEmpty()) {
            executeBulk(task, bulkRequest, startTime, listener, executor, responses, indicesThatCannotBeCreated);
            return;
        }
        Runnable executeBulkRunnable = () -> executor.execute(new ActionRunnable<>(listener) {
            @Override
            protected void doRun() {
                executeBulk(task, bulkRequest, startTime, listener, executor, responses, indicesThatCannotBeCreated);
            }
        });
        try (RefCountingRunnable refs = new RefCountingRunnable(executeBulkRunnable)) {
            for (Map.Entry<String, Boolean> indexEntry : indicesToAutoCreate.entrySet()) {
                final String index = indexEntry.getKey();
                createIndex(index, indexEntry.getValue(), bulkRequest.timeout(), ActionListener.releaseAfter(new ActionListener<>() {
                    @Override
                    public void onResponse(CreateIndexResponse createIndexResponse) {}

                    @Override
                    public void onFailure(Exception e) {
                        final Throwable cause = ExceptionsHelper.unwrapCause(e);
                        if (cause instanceof IndexNotFoundException indexNotFoundException) {
                            synchronized (indicesThatCannotBeCreated) {
                                indicesThatCannotBeCreated.put(index, indexNotFoundException);
                            }
                        } else if ((cause instanceof ResourceAlreadyExistsException) == false) {
                            failRequestsWhenPrerequisiteActionFailed(index, bulkRequest, responses, e);
                        }
                    }
                }, refs.acquire()));
            }
            for (String dataStream : dataStreamsToBeRolledOver) {
                lazyRolloverDataStream(dataStream, bulkRequest.timeout(), ActionListener.releaseAfter(new ActionListener<>() {

                    @Override
                    public void onResponse(RolloverResponse result) {
                        assert result.isRolledOver() : "An successful lazy rollover should always result in a rolled over data stream";
                    }

                    @Override
                    public void onFailure(Exception e) {
                        failRequestsWhenPrerequisiteActionFailed(dataStream, bulkRequest, responses, e);
                    }
                }, refs.acquire()));
            }
        }
    }

    /**
     * Fails all requests involving this index or data stream because the prerequisite action failed too.
     */
    private static void failRequestsWhenPrerequisiteActionFailed(
        String target,
        BulkRequest bulkRequest,
        AtomicArray<BulkItemResponse> responses,
        Exception error
    ) {
        for (int i = 0; i < bulkRequest.requests.size(); i++) {
            DocWriteRequest<?> request = bulkRequest.requests.get(i);
            if (request != null && setResponseFailureIfIndexMatches(responses, i, request, target, error)) {
                bulkRequest.requests.set(i, null);
            }
        }
    }

    /*
     * This returns the IngestService to be used for the given request. The default implementation ignores the request and always returns
     * the same ingestService, but child classes might use information in the request in creating an IngestService specific to that request.
     */
    protected IngestService getIngestService(BulkRequest request) {
        return ingestService;
    }

    static void prohibitAppendWritesInBackingIndices(DocWriteRequest<?> writeRequest, Metadata metadata) {
        DocWriteRequest.OpType opType = writeRequest.opType();
        if ((opType == OpType.CREATE || opType == OpType.INDEX) == false) {
            return;
        }
        IndexAbstraction indexAbstraction = metadata.getIndicesLookup().get(writeRequest.index());
        if (indexAbstraction == null) {
            return;
        }
        if (indexAbstraction.getType() != IndexAbstraction.Type.CONCRETE_INDEX) {
            return;
        }
        if (indexAbstraction.getParentDataStream() == null) {
            return;
        }

        DataStream dataStream = indexAbstraction.getParentDataStream();


        if (opType == DocWriteRequest.OpType.CREATE) {
            throw new IllegalArgumentException(
                "index request with op_type=create targeting backing indices is disallowed, "
                    + "target corresponding data stream ["
                    + dataStream.getName()
                    + "] instead"
            );
        }
        if (opType == DocWriteRequest.OpType.INDEX
            && writeRequest.ifPrimaryTerm() == UNASSIGNED_PRIMARY_TERM
            && writeRequest.ifSeqNo() == UNASSIGNED_SEQ_NO) {
            throw new IllegalArgumentException(
                "index request with op_type=index and no if_primary_term and if_seq_no set "
                    + "targeting backing indices is disallowed, target corresponding data stream ["
                    + dataStream.getName()
                    + "] instead"
            );
        }
    }

    static void prohibitCustomRoutingOnDataStream(DocWriteRequest<?> writeRequest, Metadata metadata) {
        IndexAbstraction indexAbstraction = metadata.getIndicesLookup().get(writeRequest.index());
        if (indexAbstraction == null) {
            return;
        }
        if (indexAbstraction.getType() != IndexAbstraction.Type.DATA_STREAM) {
            return;
        }

        if (writeRequest.routing() != null) {
            DataStream dataStream = (DataStream) indexAbstraction;
            if (dataStream.isAllowCustomRouting() == false) {
                throw new IllegalArgumentException(
                    "index request targeting data stream ["
                        + dataStream.getName()
                        + "] specifies a custom routing but the [allow_custom_routing] setting was "
                        + "not enabled in the data stream's template."
                );
            }
        }
    }

    static boolean isOnlySystem(BulkRequest request, SortedMap<String, IndexAbstraction> indicesLookup, SystemIndices systemIndices) {
        return request.getIndices().stream().allMatch(indexName -> isSystemIndex(indicesLookup, systemIndices, indexName));
    }

    private static boolean isSystemIndex(SortedMap<String, IndexAbstraction> indicesLookup, SystemIndices systemIndices, String indexName) {
        final IndexAbstraction abstraction = indicesLookup.get(indexName);
        if (abstraction != null) {
            return abstraction.isSystem();
        } else {
            return systemIndices.isSystemIndex(indexName);
        }
    }

    void createIndex(String index, boolean requireDataStream, TimeValue timeout, ActionListener<CreateIndexResponse> listener) {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest();
        createIndexRequest.index(index);
        createIndexRequest.requireDataStream(requireDataStream);
        createIndexRequest.cause("auto(bulk api)");
        createIndexRequest.masterNodeTimeout(timeout);
        client.execute(AutoCreateAction.INSTANCE, createIndexRequest, listener);
    }

    void lazyRolloverDataStream(String dataStream, TimeValue timeout, ActionListener<RolloverResponse> listener) {
        RolloverRequest rolloverRequest = new RolloverRequest(dataStream, null);
        rolloverRequest.masterNodeTimeout(timeout);
        rolloverClient.execute(LazyRolloverAction.INSTANCE, rolloverRequest, listener);
    }

    private static boolean setResponseFailureIfIndexMatches(
        AtomicArray<BulkItemResponse> responses,
        int idx,
        DocWriteRequest<?> request,
        String index,
        Exception e
    ) {
        if (index.equals(request.index())) {
            BulkItemResponse.Failure failure = new BulkItemResponse.Failure(request.index(), request.id(), e);
            responses.set(idx, BulkItemResponse.failure(idx, request.opType(), failure));
            return true;
        }
        return false;
    }

    protected long buildTookInMillis(long startTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(relativeTime() - startTimeNanos);
    }

    private enum ReducedRequestInfo {

        REQUIRE_ALIAS_AND_DATA_STREAM(true, true),
        REQUIRE_ALIAS_NOT_DATA_STREAM(true, false),

        REQUIRE_DATA_STREAM_NOT_ALIAS(false, true),
        REQUIRE_NOTHING(false, false);

        private final boolean isRequireAlias;
        private final boolean isRequireDataStream;

        ReducedRequestInfo(boolean isRequireAlias, boolean isRequireDataStream) {
            this.isRequireAlias = isRequireAlias;
            this.isRequireDataStream = isRequireDataStream;
        }

        static ReducedRequestInfo of(boolean isRequireAlias, boolean isRequireDataStream) {
            if (isRequireAlias) {
                return isRequireDataStream ? REQUIRE_ALIAS_AND_DATA_STREAM : REQUIRE_ALIAS_NOT_DATA_STREAM;
            }
            return isRequireDataStream ? REQUIRE_DATA_STREAM_NOT_ALIAS : REQUIRE_NOTHING;
        }

    }

    void executeBulk(
        Task task,
        BulkRequest bulkRequest,
        long startTimeNanos,
        ActionListener<BulkResponse> listener,
        Executor executor,
        AtomicArray<BulkItemResponse> responses,
        Map<String, IndexNotFoundException> indicesThatCannotBeCreated
    ) {
        new BulkOperation(
            task,
            threadPool,
            executor,
            clusterService,
            bulkRequest,
            client,
            responses,
            indicesThatCannotBeCreated,
            indexNameExpressionResolver,
            relativeTimeProvider,
            startTimeNanos,
            listener
        ).run();
    }

    private long relativeTime() {
        return relativeTimeProvider.getAsLong();
    }

    private void processBulkIndexIngestRequest(
        Task task,
        BulkRequest original,
        Executor executor,
        Metadata metadata,
        ActionListener<BulkResponse> listener
    ) {
        final long ingestStartTimeInNanos = System.nanoTime();
        final BulkRequestModifier bulkRequestModifier = new BulkRequestModifier(original);
        getIngestService(original).executeBulkRequest(
            original.numberOfActions(),
            () -> bulkRequestModifier,
            bulkRequestModifier::markItemAsDropped,
            (indexName) -> shouldStoreFailure(indexName, metadata, threadPool.absoluteTimeInMillis()),
            bulkRequestModifier::markItemForFailureStore,
            bulkRequestModifier::markItemAsFailed,
            (originalThread, exception) -> {
                if (exception != null) {
                    logger.debug("failed to execute pipeline for a bulk request", exception);
                    listener.onFailure(exception);
                } else {
                    long ingestTookInMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ingestStartTimeInNanos);
                    BulkRequest bulkRequest = bulkRequestModifier.getBulkRequest();
                    ActionListener<BulkResponse> actionListener = bulkRequestModifier.wrapActionListenerIfNeeded(
                        ingestTookInMillis,
                        listener
                    );
                    if (bulkRequest.requests().isEmpty()) {
                        actionListener.onResponse(new BulkResponse(new BulkItemResponse[0], 0));
                    } else {
                        ActionRunnable<BulkResponse> runnable = new ActionRunnable<>(actionListener) {
                            @Override
                            protected void doRun() {
                                doInternalExecute(task, bulkRequest, executor, actionListener);
                            }

                            @Override
                            public boolean isForceExecution() {
                                return true;
                            }
                        };
                        if (originalThread == Thread.currentThread()) {
                            runnable.run();
                        } else {
                            executor.execute(runnable);
                        }
                    }
                }
            },
            executor
        );
    }

    /**
     * Determines if an index name is associated with either an existing data stream or a template
     * for one that has the failure store enabled.
     * @param indexName The index name to check.
     * @param metadata Cluster state metadata.
     * @param epochMillis A timestamp to use when resolving date math in the index name.
     * @return true if the given index name corresponds to a data stream with a failure store,
     * or if it matches a template that has a data stream failure store enabled.
     */
    static boolean shouldStoreFailure(String indexName, Metadata metadata, long epochMillis) {
        return DataStream.isFailureStoreFeatureFlagEnabled()
            && resolveFailureStoreFromMetadata(indexName, metadata, epochMillis).or(
                () -> resolveFailureStoreFromTemplate(indexName, metadata)
            ).orElse(false);
    }

    /**
     * Determines if an index name is associated with an existing data stream that has a failure store enabled.
     * @param indexName The index name to check.
     * @param metadata Cluster state metadata.
     * @param epochMillis A timestamp to use when resolving date math in the index name.
     * @return true if the given index name corresponds to an existing data stream with a failure store enabled.
     */
    private static Optional<Boolean> resolveFailureStoreFromMetadata(String indexName, Metadata metadata, long epochMillis) {
        if (indexName == null) {
            return Optional.empty();
        }

        IndexAbstraction indexAbstraction = metadata.getIndicesLookup()
            .get(IndexNameExpressionResolver.resolveDateMathExpression(indexName, epochMillis));

        if (indexAbstraction == null || indexAbstraction.isDataStreamRelated() == false) {
            return Optional.empty();
        }

        Index writeIndex = indexAbstraction.getWriteIndex();
        assert writeIndex != null : "Could not resolve write index for resource [" + indexName + "]";
        IndexAbstraction writeAbstraction = metadata.getIndicesLookup().get(writeIndex.getName());
        DataStream targetDataStream = writeAbstraction.getParentDataStream();

        return Optional.of(targetDataStream != null && targetDataStream.isFailureStoreEnabled());
    }

    /**
     * Determines if an index name is associated with an index template that has a data stream failure store enabled.
     * @param indexName The index name to check.
     * @param metadata Cluster state metadata.
     * @return true if the given index name corresponds to an index template with a data stream failure store enabled.
     */
    private static Optional<Boolean> resolveFailureStoreFromTemplate(String indexName, Metadata metadata) {
        if (indexName == null) {
            return Optional.empty();
        }

        String template = MetadataIndexTemplateService.findV2Template(metadata, indexName, false);
        if (template != null) {
            ComposableIndexTemplate composableIndexTemplate = metadata.templatesV2().get(template);
            if (composableIndexTemplate.getDataStreamTemplate() != null) {
                return Optional.of(composableIndexTemplate.getDataStreamTemplate().hasFailureStore());
            }
        }

        return Optional.empty();
    }
}
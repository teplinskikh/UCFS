/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.health.HealthStatus;
import org.elasticsearch.persistent.PersistentTasksCustomMetadata;
import org.elasticsearch.persistent.PersistentTasksService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.transform.TransformMessages;
import org.elasticsearch.xpack.core.transform.action.StartTransformAction;
import org.elasticsearch.xpack.core.transform.action.ValidateTransformAction;
import org.elasticsearch.xpack.core.transform.transforms.AuthorizationState;
import org.elasticsearch.xpack.core.transform.transforms.TransformConfig;
import org.elasticsearch.xpack.core.transform.transforms.TransformEffectiveSettings;
import org.elasticsearch.xpack.core.transform.transforms.TransformState;
import org.elasticsearch.xpack.core.transform.transforms.TransformTaskParams;
import org.elasticsearch.xpack.core.transform.transforms.TransformTaskState;
import org.elasticsearch.xpack.transform.TransformExtensionHolder;
import org.elasticsearch.xpack.transform.TransformServices;
import org.elasticsearch.xpack.transform.notifications.TransformAuditor;
import org.elasticsearch.xpack.transform.persistence.AuthorizationStatePersistenceUtils;
import org.elasticsearch.xpack.transform.persistence.TransformConfigManager;
import org.elasticsearch.xpack.transform.persistence.TransformIndex;
import org.elasticsearch.xpack.transform.transforms.TransformNodes;
import org.elasticsearch.xpack.transform.transforms.TransformTask;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.core.Strings.format;
import static org.elasticsearch.xpack.core.transform.TransformMessages.CANNOT_START_FAILED_TRANSFORM;

public class TransportStartTransformAction extends TransportMasterNodeAction<StartTransformAction.Request, StartTransformAction.Response> {

    private static final Logger logger = LogManager.getLogger(TransportStartTransformAction.class);
    private final TransformConfigManager transformConfigManager;
    private final PersistentTasksService persistentTasksService;
    private final Client client;
    private final TransformAuditor auditor;
    private final Settings destIndexSettings;

    @Inject
    public TransportStartTransformAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        IndexNameExpressionResolver indexNameExpressionResolver,
        TransformServices transformServices,
        PersistentTasksService persistentTasksService,
        Client client,
        TransformExtensionHolder transformExtensionHolder
    ) {
        this(
            StartTransformAction.NAME,
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            indexNameExpressionResolver,
            transformServices,
            persistentTasksService,
            client,
            transformExtensionHolder
        );
    }

    protected TransportStartTransformAction(
        String name,
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        IndexNameExpressionResolver indexNameExpressionResolver,
        TransformServices transformServices,
        PersistentTasksService persistentTasksService,
        Client client,
        TransformExtensionHolder transformExtensionHolder
    ) {
        super(
            name,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            StartTransformAction.Request::new,
            indexNameExpressionResolver,
            StartTransformAction.Response::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.transformConfigManager = transformServices.getConfigManager();
        this.persistentTasksService = persistentTasksService;
        this.client = client;
        this.auditor = transformServices.getAuditor();
        this.destIndexSettings = transformExtensionHolder.getTransformExtension().getTransformDestinationIndexSettings();
    }

    @Override
    protected void masterOperation(
        Task ignoredTask,
        StartTransformAction.Request request,
        ClusterState state,
        ActionListener<StartTransformAction.Response> listener
    ) {
        TransformNodes.warnIfNoTransformNodes(state);

        final SetOnce<TransformTaskParams> transformTaskParamsHolder = new SetOnce<>();
        final SetOnce<TransformConfig> transformConfigHolder = new SetOnce<>();

        ActionListener<PersistentTasksCustomMetadata.PersistentTask<TransformTaskParams>> newPersistentTaskActionListener = ActionListener
            .wrap(task -> {
                TransformTaskParams transformTask = transformTaskParamsHolder.get();
                assert transformTask != null;
                waitForTransformTaskStarted(
                    task.getId(),
                    transformTask,
                    request.ackTimeout(),
                    ActionListener.wrap(taskStarted -> listener.onResponse(new StartTransformAction.Response(true)), listener::onFailure)
                );
            }, listener::onFailure);

        ActionListener<Boolean> createOrGetIndexListener = ActionListener.wrap(unused -> {
            TransformTaskParams transformTask = transformTaskParamsHolder.get();
            assert transformTask != null;
            PersistentTasksCustomMetadata.PersistentTask<?> existingTask = TransformTask.getTransformTask(transformTask.getId(), state);
            if (existingTask == null) {
                persistentTasksService.sendStartRequest(
                    transformTask.getId(),
                    TransformTaskParams.NAME,
                    transformTask,
                    null,
                    newPersistentTaskActionListener
                );
            } else {
                TransformState transformState = (TransformState) existingTask.getState();
                if (transformState != null && transformState.getTaskState() == TransformTaskState.FAILED) {
                    listener.onFailure(
                        new ElasticsearchStatusException(
                            TransformMessages.getMessage(CANNOT_START_FAILED_TRANSFORM, request.getId(), transformState.getReason()),
                            RestStatus.CONFLICT
                        )
                    );
                } else {
                    listener.onFailure(
                        new ElasticsearchStatusException(
                            "Cannot start transform [{}] as it is already started.",
                            RestStatus.CONFLICT,
                            request.getId()
                        )
                    );
                }
            }
        }, listener::onFailure);

        ActionListener<ValidateTransformAction.Response> validationListener = ActionListener.wrap(validationResponse -> {
            if (TransformEffectiveSettings.isUnattended(transformConfigHolder.get().getSettings())) {
                logger.debug(
                    () -> format("[%s] Skip dest index creation as this is an unattended transform", transformConfigHolder.get().getId())
                );
                createOrGetIndexListener.onResponse(true);
                return;
            }
            TransformIndex.createDestinationIndex(
                client,
                auditor,
                indexNameExpressionResolver,
                state,
                transformConfigHolder.get(),
                destIndexSettings,
                validationResponse.getDestIndexMappings(),
                createOrGetIndexListener
            );
        }, e -> {
            if (TransformEffectiveSettings.isUnattended(transformConfigHolder.get().getSettings())) {
                logger.debug(
                    () -> format("[%s] Skip dest index creation as this is an unattended transform", transformConfigHolder.get().getId())
                );
                createOrGetIndexListener.onResponse(true);
                return;
            }
            listener.onFailure(e);
        });

        ActionListener<AuthorizationState> fetchAuthStateListener = ActionListener.wrap(authState -> {
            if (authState != null && HealthStatus.RED.equals(authState.getStatus())) {
                listener.onFailure(new ElasticsearchSecurityException(authState.getLastAuthError(), RestStatus.FORBIDDEN));
                return;
            }

            TransformConfig config = transformConfigHolder.get();

            ActionRequestValidationException validationException = config.validate(null);
            if (request.from() != null && config.getSyncConfig() == null) {
                validationException = addValidationError(
                    "[from] parameter is currently not supported for batch (non-continuous) transforms",
                    validationException
                );
            }
            if (validationException != null) {
                listener.onFailure(
                    new ElasticsearchStatusException(
                        TransformMessages.getMessage(
                            TransformMessages.TRANSFORM_CONFIGURATION_INVALID,
                            request.getId(),
                            validationException.getMessage()
                        ),
                        RestStatus.BAD_REQUEST
                    )
                );
                return;
            }
            transformTaskParamsHolder.set(
                new TransformTaskParams(
                    config.getId(),
                    config.getVersion(),
                    request.from(),
                    config.getFrequency(),
                    config.getSource().requiresRemoteCluster()
                )
            );
            ClientHelper.executeAsyncWithOrigin(
                client,
                ClientHelper.TRANSFORM_ORIGIN,
                ValidateTransformAction.INSTANCE,
                new ValidateTransformAction.Request(config, false, request.ackTimeout()),
                validationListener
            );
        }, listener::onFailure);

        ActionListener<TransformConfig> getTransformListener = ActionListener.wrap(config -> {
            transformConfigHolder.set(config);

            if (TransformEffectiveSettings.isUnattended(config.getSettings())) {
                fetchAuthStateListener.onResponse(null);
            } else {
                AuthorizationStatePersistenceUtils.fetchAuthState(transformConfigManager, request.getId(), fetchAuthStateListener);
            }
        }, listener::onFailure);

        transformConfigManager.getTransformConfiguration(request.getId(), getTransformListener);
    }

    @Override
    protected ClusterBlockException checkBlock(StartTransformAction.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    private void cancelTransformTask(String taskId, String transformId, Exception exception, Consumer<Exception> onFailure) {
        persistentTasksService.sendRemoveRequest(taskId, null, new ActionListener<>() {
            @Override
            public void onResponse(PersistentTasksCustomMetadata.PersistentTask<?> task) {
                onFailure.accept(exception);
            }

            @Override
            public void onFailure(Exception e) {
                logger.error(
                    "["
                        + transformId
                        + "] Failed to cancel persistent task that could "
                        + "not be assigned due to ["
                        + exception.getMessage()
                        + "]",
                    e
                );
                onFailure.accept(exception);
            }
        });
    }

    private void waitForTransformTaskStarted(
        String taskId,
        TransformTaskParams params,
        TimeValue timeout,
        ActionListener<Boolean> listener
    ) {
        TransformPredicate predicate = new TransformPredicate();
        persistentTasksService.waitForPersistentTaskCondition(
            taskId,
            predicate,
            timeout,
            new PersistentTasksService.WaitForPersistentTaskListener<TransformTaskParams>() {
                @Override
                public void onResponse(PersistentTasksCustomMetadata.PersistentTask<TransformTaskParams> persistentTask) {
                    if (predicate.exception != null) {
                        cancelTransformTask(taskId, params.getId(), predicate.exception, listener::onFailure);
                    } else {
                        listener.onResponse(true);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }

                @Override
                public void onTimeout(TimeValue timeout) {
                    listener.onFailure(
                        new ElasticsearchStatusException(
                            "Starting transform [{}] timed out after [{}]",
                            RestStatus.REQUEST_TIMEOUT,
                            params.getId(),
                            timeout
                        )
                    );
                }
            }
        );
    }

    /**
     * Important: the methods of this class must NOT throw exceptions.  If they did then the callers
     * of endpoints waiting for a condition tested by this predicate would never get a response.
     */
    private static class TransformPredicate implements Predicate<PersistentTasksCustomMetadata.PersistentTask<?>> {

        private volatile Exception exception;

        @Override
        public boolean test(PersistentTasksCustomMetadata.PersistentTask<?> persistentTask) {
            if (persistentTask == null) {
                return false;
            }
            PersistentTasksCustomMetadata.Assignment assignment = persistentTask.getAssignment();
            if (assignment != null
                && assignment.equals(PersistentTasksCustomMetadata.INITIAL_ASSIGNMENT) == false
                && assignment.isAssigned() == false) {
                exception = new ElasticsearchStatusException(
                    "Could not start transform, allocation explanation [" + assignment.getExplanation() + "]",
                    RestStatus.TOO_MANY_REQUESTS
                );
                return true;
            }
            return assignment != null && assignment.isAssigned() && isNotStopped(persistentTask);
        }

        private static boolean isNotStopped(PersistentTasksCustomMetadata.PersistentTask<?> task) {
            TransformState state = (TransformState) task.getState();
            return state != null && state.getTaskState().equals(TransformTaskState.STOPPED) == false;
        }
    }
}
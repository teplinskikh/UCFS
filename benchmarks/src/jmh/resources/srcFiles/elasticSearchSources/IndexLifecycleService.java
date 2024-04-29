/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ilm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateApplier;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.ClusterStateUpdateTask;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.LifecycleExecutionState;
import org.elasticsearch.cluster.metadata.SingleNodeShutdownMetadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.Lifecycle.State;
import org.elasticsearch.common.scheduler.SchedulerEngine;
import org.elasticsearch.common.scheduler.TimeValueSchedule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.IndexEventListener;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.plugins.ShutdownAwarePlugin;
import org.elasticsearch.shutdown.PluginShutdownService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xpack.core.XPackField;
import org.elasticsearch.xpack.core.ilm.CheckShrinkReadyStep;
import org.elasticsearch.xpack.core.ilm.DownsampleStep;
import org.elasticsearch.xpack.core.ilm.IndexLifecycleMetadata;
import org.elasticsearch.xpack.core.ilm.LifecyclePolicy;
import org.elasticsearch.xpack.core.ilm.LifecycleSettings;
import org.elasticsearch.xpack.core.ilm.OperationMode;
import org.elasticsearch.xpack.core.ilm.OperationModeUpdateTask;
import org.elasticsearch.xpack.core.ilm.SetSingleNodeAllocateStep;
import org.elasticsearch.xpack.core.ilm.ShrinkAction;
import org.elasticsearch.xpack.core.ilm.ShrinkStep;
import org.elasticsearch.xpack.core.ilm.ShrunkShardsAllocatedStep;
import org.elasticsearch.xpack.core.ilm.Step;
import org.elasticsearch.xpack.core.ilm.Step.StepKey;
import org.elasticsearch.xpack.ilm.history.ILMHistoryStore;

import java.io.Closeable;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static org.elasticsearch.core.Strings.format;
import static org.elasticsearch.xpack.core.ilm.IndexLifecycleOriginationDateParser.parseIndexNameAndExtractDate;
import static org.elasticsearch.xpack.core.ilm.IndexLifecycleOriginationDateParser.shouldParseIndexName;
import static org.elasticsearch.xpack.core.ilm.LifecycleOperationMetadata.currentILMMode;

/**
 * A service which runs the {@link LifecyclePolicy}s associated with indexes.
 */
public class IndexLifecycleService
    implements
        ClusterStateListener,
        ClusterStateApplier,
        SchedulerEngine.Listener,
        Closeable,
        IndexEventListener,
        ShutdownAwarePlugin {
    private static final Logger logger = LogManager.getLogger(IndexLifecycleService.class);
    private static final Set<String> IGNORE_STEPS_MAINTENANCE_REQUESTED = Set.of(ShrinkStep.NAME, DownsampleStep.NAME);
    private volatile boolean isMaster = false;
    private volatile TimeValue pollInterval;

    private final SetOnce<SchedulerEngine> scheduler = new SetOnce<>();
    private final Clock clock;
    private final PolicyStepsRegistry policyRegistry;
    private final IndexLifecycleRunner lifecycleRunner;
    private final Settings settings;
    private final ClusterService clusterService;
    private final LongSupplier nowSupplier;
    private SchedulerEngine.Job scheduledJob;

    @SuppressWarnings("this-escape")
    public IndexLifecycleService(
        Settings settings,
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool,
        Clock clock,
        LongSupplier nowSupplier,
        NamedXContentRegistry xContentRegistry,
        ILMHistoryStore ilmHistoryStore,
        XPackLicenseState licenseState
    ) {
        super();
        this.settings = settings;
        this.clusterService = clusterService;
        this.clock = clock;
        this.nowSupplier = nowSupplier;
        this.scheduledJob = null;
        this.policyRegistry = new PolicyStepsRegistry(xContentRegistry, client, licenseState);
        this.lifecycleRunner = new IndexLifecycleRunner(policyRegistry, ilmHistoryStore, clusterService, threadPool, nowSupplier);
        this.pollInterval = LifecycleSettings.LIFECYCLE_POLL_INTERVAL_SETTING.get(settings);
        clusterService.addStateApplier(this);
        clusterService.addListener(this);
        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(LifecycleSettings.LIFECYCLE_POLL_INTERVAL_SETTING, this::updatePollInterval);
    }

    public void maybeRunAsyncAction(ClusterState clusterState, IndexMetadata indexMetadata, StepKey nextStepKey) {
        lifecycleRunner.maybeRunAsyncAction(clusterState, indexMetadata, indexMetadata.getLifecyclePolicyName(), nextStepKey);
    }

    /**
     * Resolve the given phase, action, and name into a real {@link StepKey}. The phase is always
     * required, but the action and name are optional. If a name is specified, an action is also required.
     */
    public StepKey resolveStepKey(ClusterState state, Index index, String phase, @Nullable String action, @Nullable String name) {
        if (name == null) {
            if (action == null) {
                return this.policyRegistry.getFirstStepForPhase(state, index, phase);
            } else {
                return this.policyRegistry.getFirstStepForPhaseAndAction(state, index, phase, action);
            }
        } else {
            assert action != null
                : "action should never be null because we don't allow constructing a partial step key with only a phase and name";
            return new StepKey(phase, action, name);
        }
    }

    /**
     * Move the cluster state to an arbitrary step for the provided index.
     *
     * In order to avoid a check-then-set race condition, the current step key
     * is required in order to validate that the index is currently on the
     * provided step. If it is not, an {@link IllegalArgumentException} is
     * thrown.
     * @throws IllegalArgumentException if the step movement cannot be validated
     */
    public ClusterState moveClusterStateToStep(ClusterState currentState, Index index, StepKey currentStepKey, StepKey newStepKey) {
        IndexLifecycleTransition.validateTransition(currentState.getMetadata().index(index), currentStepKey, newStepKey, policyRegistry);
        return IndexLifecycleTransition.moveClusterStateToStep(index, currentState, newStepKey, nowSupplier, policyRegistry, true);
    }

    public ClusterState moveClusterStateToPreviouslyFailedStep(ClusterState currentState, String[] indices) {
        ClusterState newState = currentState;
        for (String index : indices) {
            newState = IndexLifecycleTransition.moveClusterStateToPreviouslyFailedStep(newState, index, nowSupplier, policyRegistry, false);
        }
        return newState;
    }

    void onMaster(ClusterState clusterState) {
        maybeScheduleJob();

        final IndexLifecycleMetadata currentMetadata = clusterState.metadata().custom(IndexLifecycleMetadata.TYPE);
        if (currentMetadata != null) {
            OperationMode currentMode = currentILMMode(clusterState);
            if (OperationMode.STOPPED.equals(currentMode)) {
                return;
            }

            boolean safeToStop = true; 

            for (IndexMetadata idxMeta : clusterState.metadata().indices().values()) {
                if (clusterState.metadata().isIndexManagedByILM(idxMeta)) {
                    String policyName = idxMeta.getLifecyclePolicyName();
                    final LifecycleExecutionState lifecycleState = idxMeta.getLifecycleExecutionState();
                    StepKey stepKey = Step.getCurrentStepKey(lifecycleState);

                    try {
                        if (OperationMode.STOPPING == currentMode) {
                            if (stepKey != null && IGNORE_STEPS_MAINTENANCE_REQUESTED.contains(stepKey.name())) {
                                logger.info(
                                    "waiting to stop ILM because index [{}] with policy [{}] is currently in step [{}]",
                                    idxMeta.getIndex().getName(),
                                    policyName,
                                    stepKey.name()
                                );
                                lifecycleRunner.maybeRunAsyncAction(clusterState, idxMeta, policyName, stepKey);
                                safeToStop = false;
                            } else {
                                logger.info(
                                    "skipping policy execution of step [{}] for index [{}] with policy [{}]" + " because ILM is stopping",
                                    stepKey == null ? "n/a" : stepKey.name(),
                                    idxMeta.getIndex().getName(),
                                    policyName
                                );
                            }
                        } else {
                            lifecycleRunner.maybeRunAsyncAction(clusterState, idxMeta, policyName, stepKey);
                        }
                    } catch (Exception e) {
                        if (logger.isTraceEnabled()) {
                            logger.warn(
                                () -> format(
                                    "async action execution failed during master election trigger"
                                        + " for index [%s] with policy [%s] in step [%s], lifecycle state: [%s]",
                                    idxMeta.getIndex().getName(),
                                    policyName,
                                    stepKey,
                                    lifecycleState.asMap()
                                ),
                                e
                            );
                        } else {
                            logger.warn(
                                () -> format(
                                    "async action execution failed during master election trigger"
                                        + " for index [%s] with policy [%s] in step [%s]",
                                    idxMeta.getIndex().getName(),
                                    policyName,
                                    stepKey
                                ),
                                e
                            );

                        }
                    }
                }
            }

            if (safeToStop && OperationMode.STOPPING == currentMode) {
                stopILM();
            }
        }
    }

    private void stopILM() {
        submitUnbatchedTask("ilm_operation_mode_update[stopped]", OperationModeUpdateTask.ilmMode(OperationMode.STOPPED));
    }

    @Override
    public void beforeIndexAddedToCluster(Index index, Settings indexSettings) {
        if (shouldParseIndexName(indexSettings)) {
            parseIndexNameAndExtractDate(index.getName());
        }
    }

    private void updatePollInterval(TimeValue newInterval) {
        this.pollInterval = newInterval;
        maybeScheduleJob();
    }

    SchedulerEngine getScheduler() {
        return scheduler.get();
    }

    SchedulerEngine.Job getScheduledJob() {
        return scheduledJob;
    }

    private synchronized void maybeScheduleJob() {
        if (this.isMaster) {
            if (scheduler.get() == null) {
                if (isClusterServiceStoppedOrClosed() == false) {
                    scheduler.set(new SchedulerEngine(settings, clock));
                    scheduler.get().register(this);
                }
            }

            if (scheduler.get() != null) {
                scheduledJob = new SchedulerEngine.Job(XPackField.INDEX_LIFECYCLE, new TimeValueSchedule(pollInterval));
                scheduler.get().add(scheduledJob);
            }
        }
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (event.state().blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            return;
        }

        final boolean prevIsMaster = this.isMaster;
        if (prevIsMaster != event.localNodeMaster()) {
            this.isMaster = event.localNodeMaster();
            if (this.isMaster) {
                onMaster(event.state());
            } else {
                cancelJob();
                policyRegistry.clear();
            }
        }

        if (this.isMaster) {
            if (event.state().metadata().indices() != event.previousState().metadata().indices()) {
                clusterService.getClusterApplierService().threadPool().executor(ThreadPool.Names.MANAGEMENT).execute(() -> {
                    for (Index index : event.indicesDeleted()) {
                        policyRegistry.delete(index);
                    }
                });
            }

            triggerPolicies(event.state(), true);
        }
    }

    @Override
    public void applyClusterState(ClusterChangedEvent event) {
        if (event.localNodeMaster()) { 
            final IndexLifecycleMetadata ilmMetadata = event.state().metadata().custom(IndexLifecycleMetadata.TYPE);
            if (ilmMetadata != null
                && (event.previousState().nodes().isLocalNodeElectedMaster() == false
                    || ilmMetadata != event.previousState().metadata().custom(IndexLifecycleMetadata.TYPE))) {
                policyRegistry.update(ilmMetadata);
            }
        }
    }

    private void cancelJob() {
        if (scheduler.get() != null) {
            scheduler.get().remove(XPackField.INDEX_LIFECYCLE);
            scheduledJob = null;
        }
    }

    @Override
    public void triggered(SchedulerEngine.Event event) {
        if (event.getJobName().equals(XPackField.INDEX_LIFECYCLE)) {
            logger.trace("job triggered: " + event.getJobName() + ", " + event.getScheduledTime() + ", " + event.getTriggeredTime());
            triggerPolicies(clusterService.state(), false);
        }
    }

    public boolean policyExists(String policyId) {
        return policyRegistry.policyExists(policyId);
    }

    /**
     * executes the policy execution on the appropriate indices by running cluster-state tasks per index.
     *
     * If stopping ILM was requested, and it is safe to stop, this will also be done here
     * when possible after no policies are executed.
     *
     * @param clusterState the current cluster state
     * @param fromClusterStateChange whether things are triggered from the cluster-state-listener or the scheduler
     */
    void triggerPolicies(ClusterState clusterState, boolean fromClusterStateChange) {
        IndexLifecycleMetadata currentMetadata = clusterState.metadata().custom(IndexLifecycleMetadata.TYPE);

        OperationMode currentMode = currentILMMode(clusterState);
        if (currentMetadata == null) {
            if (currentMode == OperationMode.STOPPING) {
                stopILM();
            }
            return;
        }

        if (OperationMode.STOPPED.equals(currentMode)) {
            return;
        }

        boolean safeToStop = true; 

        for (IndexMetadata idxMeta : clusterState.metadata().indices().values()) {
            if (clusterState.metadata().isIndexManagedByILM(idxMeta)) {
                String policyName = idxMeta.getLifecyclePolicyName();
                final LifecycleExecutionState lifecycleState = idxMeta.getLifecycleExecutionState();
                StepKey stepKey = Step.getCurrentStepKey(lifecycleState);

                try {
                    if (OperationMode.STOPPING == currentMode) {
                        if (stepKey != null && IGNORE_STEPS_MAINTENANCE_REQUESTED.contains(stepKey.name())) {
                            logger.info(
                                "waiting to stop ILM because index [{}] with policy [{}] is currently in step [{}]",
                                idxMeta.getIndex().getName(),
                                policyName,
                                stepKey.name()
                            );
                            if (fromClusterStateChange) {
                                lifecycleRunner.runPolicyAfterStateChange(policyName, idxMeta);
                            } else {
                                lifecycleRunner.runPeriodicStep(policyName, clusterState.metadata(), idxMeta);
                            }
                            safeToStop = false;
                        } else {
                            logger.info(
                                "skipping policy execution of step [{}] for index [{}] with policy [{}] because ILM is stopping",
                                stepKey == null ? "n/a" : stepKey.name(),
                                idxMeta.getIndex().getName(),
                                policyName
                            );
                        }
                    } else {
                        if (fromClusterStateChange) {
                            lifecycleRunner.runPolicyAfterStateChange(policyName, idxMeta);
                        } else {
                            lifecycleRunner.runPeriodicStep(policyName, clusterState.metadata(), idxMeta);
                        }
                    }
                } catch (Exception e) {
                    if (logger.isTraceEnabled()) {
                        logger.warn(
                            () -> format(
                                "async action execution failed during policy trigger"
                                    + " for index [%s] with policy [%s] in step [%s], lifecycle state: [%s]",
                                idxMeta.getIndex().getName(),
                                policyName,
                                stepKey,
                                lifecycleState.asMap()
                            ),
                            e
                        );
                    } else {
                        logger.warn(
                            () -> format(
                                "async action execution failed during policy trigger" + " for index [%s] with policy [%s] in step [%s]",
                                idxMeta.getIndex().getName(),
                                policyName,
                                stepKey
                            ),
                            e
                        );

                    }
                }
            }
        }

        if (safeToStop && OperationMode.STOPPING == currentMode) {
            stopILM();
        }
    }

    @Override
    public synchronized void close() {
        assert isClusterServiceStoppedOrClosed()
            : "close is called by closing the plugin, which is expected to happen after " + "the cluster service is stopped";
        SchedulerEngine engine = scheduler.get();
        if (engine != null) {
            engine.stop();
        }
    }

    /**
     * Method that checks if the lifecycle state of the cluster service is stopped or closed. This
     * enhances the readability of the code.
     */
    private boolean isClusterServiceStoppedOrClosed() {
        final State state = clusterService.lifecycleState();
        return state == State.STOPPED || state == State.CLOSED;
    }

    PolicyStepsRegistry getPolicyRegistry() {
        return policyRegistry;
    }

    static Set<String> indicesOnShuttingDownNodesInDangerousStep(ClusterState state, String nodeId) {
        final Set<String> shutdownNodes = PluginShutdownService.shutdownTypeNodes(
            state,
            SingleNodeShutdownMetadata.Type.REMOVE,
            SingleNodeShutdownMetadata.Type.SIGTERM,
            SingleNodeShutdownMetadata.Type.REPLACE
        );
        if (shutdownNodes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> indicesPreventingShutdown = state.metadata()
            .indices()
            .entrySet()
            .stream()
            .filter(indexToMetadata -> Strings.hasText(indexToMetadata.getValue().getLifecyclePolicyName()))
            .filter(indexToMetadata -> ShrinkAction.NAME.equals(indexToMetadata.getValue().getLifecycleExecutionState().action()))
            .filter(indexToMetadata -> {
                String step = indexToMetadata.getValue().getLifecycleExecutionState().step();
                return SetSingleNodeAllocateStep.NAME.equals(step)
                    || CheckShrinkReadyStep.NAME.equals(step)
                    || ShrinkStep.NAME.equals(step)
                    || ShrunkShardsAllocatedStep.NAME.equals(step);
            })
            .filter(indexToMetadata -> {
                String nodePicked = indexToMetadata.getValue()
                    .getSettings()
                    .get(IndexMetadata.INDEX_ROUTING_REQUIRE_GROUP_SETTING.getKey() + "_id");
                return nodeId.equals(nodePicked);
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        logger.trace(
            "with nodes marked as shutdown for removal {}, indices {} are preventing shutdown",
            shutdownNodes,
            indicesPreventingShutdown
        );
        return indicesPreventingShutdown;
    }

    @Override
    public boolean safeToShutdown(String nodeId, SingleNodeShutdownMetadata.Type shutdownType) {
        switch (shutdownType) {
            case RESTART:
                return true;
            case REPLACE:
            case REMOVE:
            case SIGTERM:
                Set<String> indices = indicesOnShuttingDownNodesInDangerousStep(clusterService.state(), nodeId);
                return indices.isEmpty();
            default:
                throw new IllegalArgumentException("unknown shutdown type: " + shutdownType);
        }
    }

    @Override
    public void signalShutdown(Collection<String> shutdownNodeIds) {
    }

    @SuppressForbidden(reason = "legacy usage of unbatched task") 
    private void submitUnbatchedTask(@SuppressWarnings("SameParameterValue") String source, ClusterStateUpdateTask task) {
        clusterService.submitUnbatchedStateUpdateTask(source, task);
    }
}
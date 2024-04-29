/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.health;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.replication.ClusterStateCreationUtils;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.node.DiscoveryNodeUtils;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.logging.ESLogMessage;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.scheduler.SchedulerEngine;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.telemetry.TelemetryProvider;
import org.elasticsearch.telemetry.metric.LongGaugeMetric;
import org.elasticsearch.telemetry.metric.MeterRegistry;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.MockLogAppender;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.elasticsearch.health.HealthStatus.GREEN;
import static org.elasticsearch.health.HealthStatus.RED;
import static org.elasticsearch.health.HealthStatus.YELLOW;
import static org.elasticsearch.test.ClusterServiceUtils.createClusterService;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class HealthPeriodicLoggerTests extends ESTestCase {
    private ThreadPool threadPool;

    private NodeClient client;
    private ClusterService clusterService;

    private HealthPeriodicLogger testHealthPeriodicLogger;
    private ClusterSettings clusterSettings;
    private final DiscoveryNode node1 = DiscoveryNodeUtils.builder("node_1").roles(Set.of(DiscoveryNodeRole.MASTER_ROLE)).build();
    private final DiscoveryNode node2 = DiscoveryNodeUtils.builder("node_2")
        .roles(Set.of(DiscoveryNodeRole.MASTER_ROLE, DiscoveryNodeRole.DATA_ROLE))
        .build();
    private ClusterState stateWithLocalHealthNode;

    private NodeClient getTestClient() {
        return mock(NodeClient.class);
    }

    private HealthService getMockedHealthService() {
        return mock(HealthService.class);
    }

    private MeterRegistry getMockedMeterRegistry() {
        return mock(MeterRegistry.class);
    }

    private TelemetryProvider getMockedTelemetryProvider() {
        return mock(TelemetryProvider.class);
    }

    @Before
    public void setupServices() {
        threadPool = new TestThreadPool(getTestName());
        stateWithLocalHealthNode = ClusterStateCreationUtils.state(node2, node1, node2, new DiscoveryNode[] { node1, node2 });
        this.clusterSettings = new ClusterSettings(Settings.EMPTY, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        this.clusterService = createClusterService(stateWithLocalHealthNode, this.threadPool, clusterSettings);
        this.client = getTestClient();
    }

    @After
    public void cleanup() {
        clusterService.close();
        if (testHealthPeriodicLogger != null) {
            if (testHealthPeriodicLogger.lifecycleState() == Lifecycle.State.STARTED) {
                testHealthPeriodicLogger.stop();
            }
            if (testHealthPeriodicLogger.lifecycleState() == Lifecycle.State.INITIALIZED
                || testHealthPeriodicLogger.lifecycleState() == Lifecycle.State.STOPPED) {
                testHealthPeriodicLogger.close();
            }
        }
        threadPool.shutdownNow();
    }

    public void testConvertToLoggedFields() {
        var results = getTestIndicatorResults();
        var overallStatus = HealthStatus.merge(results.stream().map(HealthIndicatorResult::status));

        Map<String, Object> loggerResults = HealthPeriodicLogger.convertToLoggedFields(results);

        assertThat(loggerResults.size(), equalTo(results.size() + 2));

        assertThat(loggerResults.get(makeHealthStatusString("master_is_stable")), equalTo("green"));
        assertThat(loggerResults.get(makeHealthStatusString("disk")), equalTo("yellow"));
        assertThat(loggerResults.get(makeHealthStatusString("shards_availability")), equalTo("yellow"));

        assertThat(loggerResults.get(makeHealthStatusString("overall")), equalTo(overallStatus.xContentValue()));

        assertThat(
            loggerResults.get(HealthPeriodicLogger.MESSAGE_FIELD),
            equalTo(String.format(Locale.ROOT, "health=%s [disk,shards_availability]", overallStatus.xContentValue()))
        );

        {
            List<HealthIndicatorResult> empty = new ArrayList<>();
            Map<String, Object> emptyResults = HealthPeriodicLogger.convertToLoggedFields(empty);

            assertThat(emptyResults.size(), equalTo(0));
        }

        {
            results = getTestIndicatorResultsAllGreen();
            loggerResults = HealthPeriodicLogger.convertToLoggedFields(results);
            overallStatus = HealthStatus.merge(results.stream().map(HealthIndicatorResult::status));

            assertThat(
                loggerResults.get(HealthPeriodicLogger.MESSAGE_FIELD),
                equalTo(String.format(Locale.ROOT, "health=%s", overallStatus.xContentValue()))
            );
        }
    }

    public void testHealthNodeIsSelected() {
        HealthService testHealthService = this.getMockedHealthService();
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(clusterService, testHealthService, randomBoolean());

        assertFalse(testHealthPeriodicLogger.isHealthNode());

        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue(testHealthPeriodicLogger.isHealthNode());
    }

    public void testJobScheduling() throws Exception {
        HealthService testHealthService = this.getMockedHealthService();

        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(clusterService, testHealthService, false);

        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());
        assertTrue("health logger should be enabled", testHealthPeriodicLogger.enabled());

        assertNull(testHealthPeriodicLogger.getScheduler());
        testHealthPeriodicLogger.start();
        AtomicReference<SchedulerEngine> scheduler = new AtomicReference<>();
        assertBusy(() -> {
            var s = testHealthPeriodicLogger.getScheduler();
            assertNotNull(s);
            scheduler.set(s);
        });
        assertTrue(scheduler.get().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME));

        ClusterState noHealthNode = ClusterStateCreationUtils.state(node2, node1, new DiscoveryNode[] { node1, node2 });
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", noHealthNode, stateWithLocalHealthNode));
        assertFalse(testHealthPeriodicLogger.isHealthNode());
        assertFalse(scheduler.get().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME));
    }

    public void testEnabled() {
        HealthService testHealthService = this.getMockedHealthService();
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(clusterService, testHealthService, true);

        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        verifyLoggerIsReadyToRun(testHealthPeriodicLogger);

        {
            this.clusterSettings.applySettings(Settings.builder().put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), false).build());
            assertFalse(testHealthPeriodicLogger.enabled());
            assertFalse(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
        }

        {
            this.clusterSettings.applySettings(Settings.builder().put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true).build());
            assertTrue(testHealthPeriodicLogger.enabled());
            assertTrue(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
        }
        {
            testHealthPeriodicLogger.stop();
            assertFalse(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
            this.clusterSettings.applySettings(Settings.builder().put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true).build());
            assertTrue(testHealthPeriodicLogger.enabled());
            assertFalse(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
        }
    }

    public void testUpdatePollInterval() {
        HealthService testHealthService = this.getMockedHealthService();
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(clusterService, testHealthService, false);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());
        assertTrue("health logger should be enabled", testHealthPeriodicLogger.enabled());
        {
            TimeValue pollInterval = TimeValue.timeValueSeconds(randomIntBetween(15, 59));
            this.clusterSettings.applySettings(
                Settings.builder()
                    .put(HealthPeriodicLogger.POLL_INTERVAL_SETTING.getKey(), pollInterval)
                    .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                    .build()
            );
            assertTrue("health logger should be enabled", testHealthPeriodicLogger.enabled());
            assertEquals(pollInterval, testHealthPeriodicLogger.getPollInterval());
            assertNull(testHealthPeriodicLogger.getScheduler());
        }

        testHealthPeriodicLogger.start();
        {
            TimeValue pollInterval = TimeValue.timeValueSeconds(randomIntBetween(15, 59));
            this.clusterSettings.applySettings(
                Settings.builder()
                    .put(HealthPeriodicLogger.POLL_INTERVAL_SETTING.getKey(), pollInterval)
                    .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                    .build()
            );
            assertEquals(pollInterval, testHealthPeriodicLogger.getPollInterval());
            verifyLoggerIsReadyToRun(testHealthPeriodicLogger);
            assertNotNull(testHealthPeriodicLogger.getScheduler());
            assertTrue(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
        }

        {
            TimeValue pollInterval = TimeValue.timeValueSeconds(randomIntBetween(15, 59));
            this.clusterSettings.applySettings(
                Settings.builder()
                    .put(HealthPeriodicLogger.POLL_INTERVAL_SETTING.getKey(), pollInterval)
                    .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), false)
                    .build()
            );
            assertFalse(testHealthPeriodicLogger.enabled());
            assertEquals(pollInterval, testHealthPeriodicLogger.getPollInterval());
            assertFalse(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
            this.clusterSettings.applySettings(
                Settings.builder().put(HealthPeriodicLogger.POLL_INTERVAL_SETTING.getKey(), pollInterval).build()
            );
        }

        testHealthPeriodicLogger.stop();
        {
            this.clusterSettings.applySettings(
                Settings.builder()
                    .put(HealthPeriodicLogger.POLL_INTERVAL_SETTING.getKey(), TimeValue.timeValueSeconds(30))
                    .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                    .build()
            );
            assertTrue("health logger should be enabled", testHealthPeriodicLogger.enabled());
            assertFalse(
                testHealthPeriodicLogger.getScheduler().scheduledJobIds().contains(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME)
            );
        }
    }

    public void testTriggeredJobCallsTryToLogHealth() throws Exception {
        AtomicBoolean calledGetHealth = new AtomicBoolean();
        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            assertNotNull(listener);
            calledGetHealth.set(true);
            listener.onResponse(getTestIndicatorResults());
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, true);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));

        verifyLoggerIsReadyToRun(testHealthPeriodicLogger);

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);
        testHealthPeriodicLogger.triggered(event);
        assertBusy(() -> assertTrue(calledGetHealth.get()));
    }

    public void testResultFailureHandling() throws Exception {
        AtomicInteger getHealthCalled = new AtomicInteger(0);

        HealthService testHealthService = this.getMockedHealthService();

        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, true);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

        {
            doAnswer(invocation -> {
                ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
                listener.onFailure(new Exception("fake failure"));
                getHealthCalled.incrementAndGet();
                return null;
            }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(1)));
        }

        {
            doAnswer(invocation -> {
                ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
                listener.onResponse(getTestIndicatorResults());
                getHealthCalled.incrementAndGet();
                return null;
            }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(2)));
        }
    }

    public void testTryToLogHealthConcurrencyControlWithResults() throws Exception {
        AtomicInteger getHealthCalled = new AtomicInteger(0);

        CountDownLatch waitForSecondRun = new CountDownLatch(1);
        CountDownLatch waitForRelease = new CountDownLatch(1);
        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            getHealthCalled.incrementAndGet();
            waitForSecondRun.await();
            listener.onResponse(getTestIndicatorResults());
            waitForRelease.countDown();
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());

        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, true);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        verifyLoggerIsReadyToRun(testHealthPeriodicLogger);

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

        {
            Thread logHealthThread = new Thread(() -> testHealthPeriodicLogger.triggered(event));
            logHealthThread.start();
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(1)));
            assertFalse(testHealthPeriodicLogger.tryToLogHealth());
            waitForSecondRun.countDown();
        }

        {
            waitForRelease.await();
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(2)));
        }
    }

    public void testTryToLogHealthConcurrencyControl() throws Exception {
        AtomicInteger getHealthCalled = new AtomicInteger(0);

        CountDownLatch waitForSecondRun = new CountDownLatch(1);
        CountDownLatch waitForRelease = new CountDownLatch(1);

        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            assertNotNull(listener);

            getHealthCalled.incrementAndGet();

            waitForSecondRun.await();
            listener.onResponse(getTestIndicatorResults());
            waitForRelease.countDown();
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());

        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, false);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

        {
            Thread logHealthThread = new Thread(() -> testHealthPeriodicLogger.triggered(event));
            logHealthThread.start();
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(1)));
        }

        {
            assertFalse(testHealthPeriodicLogger.tryToLogHealth());
            waitForSecondRun.countDown();
        }

        {
            waitForRelease.await();
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(2)));
        }
    }

    public void testTryToLogHealthConcurrencyControlWithException() throws Exception {
        AtomicInteger getHealthCalled = new AtomicInteger(0);

        HealthService testHealthService = this.getMockedHealthService();

        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, false);
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

        {
            doThrow(new ResourceNotFoundException("No preflight indicators")).when(testHealthService)
                .getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(0)));
        }

        {
            doAnswer(invocation -> {
                ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
                listener.onResponse(getTestIndicatorResults());
                getHealthCalled.incrementAndGet();
                return null;
            }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
            testHealthPeriodicLogger.triggered(event);
            assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(1)));
        }
    }

    public void testClosingWhenRunInProgress() throws Exception {
        {
            AtomicInteger getHealthCalled = new AtomicInteger(0);

            HealthService testHealthService = this.getMockedHealthService();
            doAnswer(invocation -> {
                ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
                assertNotNull(listener);

                getHealthCalled.incrementAndGet();
                return null;
            }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());

            HealthPeriodicLogger healthLoggerThatWillNotFinish = createAndInitHealthPeriodicLogger(
                this.clusterService,
                testHealthService,
                true
            );
            healthLoggerThatWillNotFinish.clusterChanged(
                new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE)
            );
            assertTrue("local node should be the health node", healthLoggerThatWillNotFinish.isHealthNode());
            assertTrue("health logger should be enabled", healthLoggerThatWillNotFinish.enabled());

            SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

            {
                healthLoggerThatWillNotFinish.triggered(event);
                assertBusy(() -> assertThat(getHealthCalled.get(), equalTo(1)));
            }
            healthLoggerThatWillNotFinish.stop();
            assertEquals(Lifecycle.State.STOPPED, healthLoggerThatWillNotFinish.lifecycleState());
            healthLoggerThatWillNotFinish.close();
            assertBusy(() -> assertEquals(Lifecycle.State.CLOSED, healthLoggerThatWillNotFinish.lifecycleState()), 5, TimeUnit.SECONDS);
        }

        {
            AtomicInteger getHealthCalled = new AtomicInteger(0);

            CountDownLatch waitForCloseToBeTriggered = new CountDownLatch(1);
            CountDownLatch waitForRelease = new CountDownLatch(1);

            HealthService testHealthService = this.getMockedHealthService();
            doAnswer(invocation -> {
                ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
                assertNotNull(listener);

                getHealthCalled.incrementAndGet();

                waitForCloseToBeTriggered.await();
                listener.onResponse(getTestIndicatorResults());
                waitForRelease.countDown();
                return null;
            }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());

            testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, true);
            testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
            verifyLoggerIsReadyToRun(testHealthPeriodicLogger);

            SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);

            {
                Thread logHealthThread = new Thread(() -> testHealthPeriodicLogger.triggered(event));
                logHealthThread.start();
                assertBusy(() -> assertTrue(testHealthPeriodicLogger.currentlyRunning()));
            }

            {
                testHealthPeriodicLogger.stop();
                assertEquals(Lifecycle.State.STOPPED, testHealthPeriodicLogger.lifecycleState());
                assertTrue(testHealthPeriodicLogger.currentlyRunning());
                Thread closeHealthLogger = new Thread(() -> testHealthPeriodicLogger.close());
                closeHealthLogger.start();
                assertBusy(() -> assertTrue(testHealthPeriodicLogger.waitingToFinishCurrentRun()));
                waitForCloseToBeTriggered.countDown();
                assertBusy(() -> assertEquals(Lifecycle.State.CLOSED, testHealthPeriodicLogger.lifecycleState()));
            }
        }
    }

    public void testLoggingHappens() {
        MockLogAppender mockAppender = new MockLogAppender();
        mockAppender.start();
        mockAppender.addExpectation(
            new MockLogAppender.SeenEventExpectation(
                "overall",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"yellow\"", makeHealthStatusString("overall"))
            )
        );
        mockAppender.addExpectation(
            new MockLogAppender.SeenEventExpectation(
                "master_is_stable",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"green\"", makeHealthStatusString("master_is_stable"))
            )
        );
        mockAppender.addExpectation(
            new MockLogAppender.SeenEventExpectation(
                "disk",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"yellow\"", makeHealthStatusString("disk"))
            )
        );
        mockAppender.addExpectation(
            new MockLogAppender.UnseenEventExpectation(
                "ilm",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"red\"", makeHealthStatusString("ilm"))
            )
        );
        Logger periodicLoggerLogger = LogManager.getLogger(HealthPeriodicLogger.class);
        Loggers.addAppender(periodicLoggerLogger, mockAppender);

        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            assertNotNull(listener);
            listener.onResponse(getTestIndicatorResults());
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, false);

        this.clusterSettings.applySettings(
            Settings.builder()
                .put(HealthPeriodicLogger.OUTPUT_MODE_SETTING.getKey(), HealthPeriodicLogger.OutputMode.LOGS)
                .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                .build()
        );
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);
        testHealthPeriodicLogger.triggered(event);

        try {
            mockAppender.assertAllExpectationsMatched();
        } finally {
            Loggers.removeAppender(periodicLoggerLogger, mockAppender);
            mockAppender.stop();
        }
    }

    public void testOutputModeNoLogging() {
        MockLogAppender mockAppender = new MockLogAppender();
        mockAppender.start();
        mockAppender.addExpectation(
            new MockLogAppender.UnseenEventExpectation(
                "overall",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"yellow\"", makeHealthStatusString("overall"))
            )
        );
        mockAppender.addExpectation(
            new MockLogAppender.UnseenEventExpectation(
                "master_is_stable",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"green\"", makeHealthStatusString("master_is_stable"))
            )
        );
        mockAppender.addExpectation(
            new MockLogAppender.UnseenEventExpectation(
                "disk",
                HealthPeriodicLogger.class.getCanonicalName(),
                Level.INFO,
                String.format(Locale.ROOT, "%s=\"yellow\"", makeHealthStatusString("disk"))
            )
        );
        Logger periodicLoggerLogger = LogManager.getLogger(HealthPeriodicLogger.class);
        Loggers.addAppender(periodicLoggerLogger, mockAppender);

        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            assertNotNull(listener);
            listener.onResponse(getTestIndicatorResults());
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(this.clusterService, testHealthService, false);

        this.clusterSettings.applySettings(
            Settings.builder()
                .put(HealthPeriodicLogger.OUTPUT_MODE_SETTING.getKey(), HealthPeriodicLogger.OutputMode.METRICS)
                .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                .build()
        );
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);
        testHealthPeriodicLogger.triggered(event);

        try {
            mockAppender.assertAllExpectationsMatched();
        } finally {
            Loggers.removeAppender(periodicLoggerLogger, mockAppender);
            mockAppender.stop();
        }
    }

    public void testMetricsMode() {
        List<String> logs = new ArrayList<>();
        List<Long> metrics = new ArrayList<>();

        BiConsumer<LongGaugeMetric, Long> metricWriter = (metric, value) -> metrics.add(value);
        Consumer<ESLogMessage> logWriter = msg -> logs.add(msg.asString());
        List<HealthIndicatorResult> results = getTestIndicatorResultsWithRed();
        HealthService testHealthService = this.getMockedHealthService();
        doAnswer(invocation -> {
            ActionListener<List<HealthIndicatorResult>> listener = invocation.getArgument(4);
            assertNotNull(listener);
            listener.onResponse(results);
            return null;
        }).when(testHealthService).getHealth(any(), isNull(), anyBoolean(), anyInt(), any());
        testHealthPeriodicLogger = createAndInitHealthPeriodicLogger(
            this.clusterService,
            testHealthService,
            false,
            metricWriter,
            logWriter
        );

        this.clusterSettings.applySettings(
            Settings.builder()
                .put(HealthPeriodicLogger.OUTPUT_MODE_SETTING.getKey(), HealthPeriodicLogger.OutputMode.METRICS)
                .put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true)
                .build()
        );
        testHealthPeriodicLogger.clusterChanged(new ClusterChangedEvent("test", stateWithLocalHealthNode, ClusterState.EMPTY_STATE));
        assertTrue("local node should be the health node", testHealthPeriodicLogger.isHealthNode());

        assertEquals(0, metrics.size());

        SchedulerEngine.Event event = new SchedulerEngine.Event(HealthPeriodicLogger.HEALTH_PERIODIC_LOGGER_JOB_NAME, 0, 0);
        testHealthPeriodicLogger.triggered(event);

        assertEquals(0, logs.size());
        assertEquals(4, metrics.size());
    }

    private void verifyLoggerIsReadyToRun(HealthPeriodicLogger healthPeriodicLogger) {
        assertTrue("local node should be the health node", healthPeriodicLogger.isHealthNode());
        assertTrue("health logger should be enabled", healthPeriodicLogger.enabled());
        assertEquals("health logger is started", Lifecycle.State.STARTED, healthPeriodicLogger.lifecycleState());
    }

    private List<HealthIndicatorResult> getTestIndicatorResults() {
        var networkLatency = new HealthIndicatorResult("master_is_stable", GREEN, null, null, null, null);
        var slowTasks = new HealthIndicatorResult("disk", YELLOW, null, null, null, null);
        var shardsAvailable = new HealthIndicatorResult("shards_availability", YELLOW, null, null, null, null);

        return List.of(networkLatency, slowTasks, shardsAvailable);
    }

    private List<HealthIndicatorResult> getTestIndicatorResultsAllGreen() {
        var networkLatency = new HealthIndicatorResult("master_is_stable", GREEN, null, null, null, null);
        var slowTasks = new HealthIndicatorResult("disk", GREEN, null, null, null, null);
        var shardsAvailable = new HealthIndicatorResult("shards_availability", GREEN, null, null, null, null);

        return List.of(networkLatency, slowTasks, shardsAvailable);
    }

    private List<HealthIndicatorResult> getTestIndicatorResultsWithRed() {
        var networkLatency = new HealthIndicatorResult("master_is_stable", GREEN, null, null, null, null);
        var slowTasks = new HealthIndicatorResult("disk", GREEN, null, null, null, null);
        var shardsAvailable = new HealthIndicatorResult("shards_availability", RED, null, null, null, null);

        return List.of(networkLatency, slowTasks, shardsAvailable);
    }

    private String makeHealthStatusString(String key) {
        return String.format(Locale.ROOT, "%s.%s.status", HealthPeriodicLogger.HEALTH_FIELD_PREFIX, key);
    }

    private HealthPeriodicLogger createAndInitHealthPeriodicLogger(
        ClusterService clusterService,
        HealthService testHealthService,
        boolean started
    ) {
        return createAndInitHealthPeriodicLogger(clusterService, testHealthService, started, null, null);
    }

    private HealthPeriodicLogger createAndInitHealthPeriodicLogger(
        ClusterService clusterService,
        HealthService testHealthService,
        boolean started,
        BiConsumer<LongGaugeMetric, Long> metricWriter,
        Consumer<ESLogMessage> logWriter
    ) {
        var provider = getMockedTelemetryProvider();
        var registry = getMockedMeterRegistry();
        doReturn(registry).when(provider).getMeterRegistry();
        if (metricWriter != null || logWriter != null) {
            testHealthPeriodicLogger = HealthPeriodicLogger.create(
                Settings.EMPTY,
                clusterService,
                this.client,
                testHealthService,
                provider,
                metricWriter,
                logWriter
            );
        } else {
            testHealthPeriodicLogger = HealthPeriodicLogger.create(
                Settings.EMPTY,
                clusterService,
                this.client,
                testHealthService,
                provider
            );
        }
        if (started) {
            testHealthPeriodicLogger.start();
        }
        clusterSettings.applySettings(Settings.EMPTY);
        clusterSettings.applySettings(Settings.builder().put(HealthPeriodicLogger.ENABLED_SETTING.getKey(), true).build());

        return testHealthPeriodicLogger;
    }
}
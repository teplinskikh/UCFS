/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.persistent;

import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.persistent.PersistentTasksCustomMetadata.PersistentTask;
import org.elasticsearch.persistent.PersistentTasksService.WaitForPersistentTaskListener;
import org.elasticsearch.persistent.TestPersistentTasksPlugin.State;
import org.elasticsearch.persistent.TestPersistentTasksPlugin.TestParams;
import org.elasticsearch.persistent.TestPersistentTasksPlugin.TestPersistentTasksExecutor;
import org.elasticsearch.persistent.TestPersistentTasksPlugin.TestTasksRequestBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskInfo;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.After;
import org.junit.Before;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFutureThrows;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE, minNumDataNodes = 2)
public class PersistentTasksExecutorIT extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(TestPersistentTasksPlugin.class);
    }

    @Before
    public void resetNonClusterStateCondition() {
        TestPersistentTasksExecutor.setNonClusterStateCondition(true);
    }

    @After
    public void cleanup() throws Exception {
        assertNoRunningTasks();
    }

    public static class WaitForPersistentTaskFuture<Params extends PersistentTaskParams> extends PlainActionFuture<PersistentTask<Params>>
        implements
            WaitForPersistentTaskListener<Params> {}

    public void testPersistentActionFailure() throws Exception {
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future);
        long allocationId = future.get().getAllocationId();
        waitForTaskToStart();
        TaskInfo firstRunningTask = clusterAdmin().prepareListTasks()
            .setActions(TestPersistentTasksExecutor.NAME + "[c]")
            .get()
            .getTasks()
            .get(0);
        logger.info("Found running task with id {} and parent {}", firstRunningTask.id(), firstRunningTask.parentTaskId());
        assertThat(firstRunningTask.parentTaskId().getId(), equalTo(allocationId));
        assertThat(firstRunningTask.parentTaskId().getNodeId(), equalTo("cluster"));

        logger.info("Failing the running task");
        assertThat(
            new TestTasksRequestBuilder(client()).setOperation("fail").setTargetTaskId(firstRunningTask.taskId()).get().getTasks().size(),
            equalTo(1)
        );

        logger.info("Waiting for persistent task with id {} to disappear", firstRunningTask.id());
        assertBusy(() -> {
            assertThat(clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks(), empty());
        });
    }

    public void testPersistentActionCompletion() throws Exception {
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        String taskId = UUIDs.base64UUID();
        persistentTasksService.sendStartRequest(taskId, TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future);
        long allocationId = future.get().getAllocationId();
        waitForTaskToStart();
        TaskInfo firstRunningTask = clusterAdmin().prepareListTasks()
            .setActions(TestPersistentTasksExecutor.NAME + "[c]")
            .setDetailed(true)
            .get()
            .getTasks()
            .get(0);
        logger.info("Found running task with id {} and parent {}", firstRunningTask.id(), firstRunningTask.parentTaskId());
        assertThat(firstRunningTask.parentTaskId().getId(), equalTo(allocationId));
        assertThat(firstRunningTask.parentTaskId().getNodeId(), equalTo("cluster"));
        assertThat(firstRunningTask.description(), equalTo("id=" + taskId));

        if (randomBoolean()) {
            logger.info("Simulating errant completion notification");
            PlainActionFuture<PersistentTask<?>> failedCompletionNotificationFuture = new PlainActionFuture<>();
            persistentTasksService.sendCompletionRequest(taskId, Long.MAX_VALUE, null, null, null, failedCompletionNotificationFuture);
            assertFutureThrows(failedCompletionNotificationFuture, ResourceNotFoundException.class);
            assertThat(
                clusterAdmin().prepareListTasks()
                    .setActions(TestPersistentTasksExecutor.NAME + "[c]")
                    .setDetailed(true)
                    .get()
                    .getTasks()
                    .size(),
                equalTo(1)
            );
        }

        stopOrCancelTask(firstRunningTask.taskId());
    }

    public void testPersistentActionWithNoAvailableNode() throws Exception {
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        TestParams testParams = new TestParams("Blah");
        testParams.setExecutorNodeAttr("test");
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, testParams, null, future);
        String taskId = future.get().getId();

        Settings nodeSettings = Settings.builder().put(nodeSettings(0, Settings.EMPTY)).put("node.attr.test_attr", "test").build();
        String newNode = internalCluster().startNode(nodeSettings);
        String newNodeId = getNodeId(newNode);
        waitForTaskToStart();

        TaskInfo taskInfo = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks().get(0);

        assertThat(taskInfo.taskId().getNodeId(), equalTo(newNodeId));

        internalCluster().stopNode(
            internalCluster().getNodeNameThat(settings -> Objects.equals(settings.get("node.attr.test_attr"), "test"))
        );

        assertBusy(() -> {
            assertThat(clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks(), empty());
        });

        PlainActionFuture<PersistentTask<?>> removeFuture = new PlainActionFuture<>();
        persistentTasksService.sendRemoveRequest(taskId, null, removeFuture);
        assertEquals(removeFuture.get().getId(), taskId);
    }

    public void testPersistentActionWithNonClusterStateCondition() throws Exception {
        PersistentTasksClusterService persistentTasksClusterService = internalCluster().getInstance(
            PersistentTasksClusterService.class,
            internalCluster().getMasterName()
        );
        persistentTasksClusterService.setRecheckInterval(TimeValue.timeValueMillis(1));

        TestPersistentTasksExecutor.setNonClusterStateCondition(false);

        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        TestParams testParams = new TestParams("Blah");
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, testParams, null, future);
        String taskId = future.get().getId();

        assertThat(clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks(), empty());

        TestPersistentTasksExecutor.setNonClusterStateCondition(true);

        waitForTaskToStart();
        TaskInfo taskInfo = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks().get(0);

        assertThat(taskInfo.taskId().getNodeId(), notNullValue());

        PlainActionFuture<PersistentTask<?>> removeFuture = new PlainActionFuture<>();
        persistentTasksService.sendRemoveRequest(taskId, null, removeFuture);
        assertEquals(removeFuture.get().getId(), taskId);
    }

    public void testPersistentActionStatusUpdate() throws Exception {
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future);
        String taskId = future.get().getId();
        waitForTaskToStart();
        TaskInfo firstRunningTask = clusterAdmin().prepareListTasks()
            .setActions(TestPersistentTasksExecutor.NAME + "[c]")
            .get()
            .getTasks()
            .get(0);

        List<PersistentTask<?>> tasksInProgress = findTasks(internalCluster().clusterService().state(), TestPersistentTasksExecutor.NAME);
        assertThat(tasksInProgress.size(), equalTo(1));
        assertThat(tasksInProgress.iterator().next().getState(), nullValue());

        int numberOfUpdates = randomIntBetween(1, 10);
        for (int i = 0; i < numberOfUpdates; i++) {
            logger.info("Updating the task states");
            assertThat(
                new TestTasksRequestBuilder(client()).setOperation("update_status")
                    .setTargetTaskId(firstRunningTask.taskId())
                    .get()
                    .getTasks()
                    .size(),
                equalTo(1)
            );

            int finalI = i;
            WaitForPersistentTaskFuture<?> future1 = new WaitForPersistentTaskFuture<>();
            persistentTasksService.waitForPersistentTaskCondition(
                taskId,
                task -> task != null
                    && task.getState() != null
                    && task.getState().toString() != null
                    && task.getState().toString().equals("{\"phase\":\"phase " + (finalI + 1) + "\"}"),
                TimeValue.timeValueSeconds(10),
                future1
            );
            assertThat(future1.get().getId(), equalTo(taskId));
        }

        WaitForPersistentTaskFuture<?> future1 = new WaitForPersistentTaskFuture<>();
        persistentTasksService.waitForPersistentTaskCondition(taskId, task -> false, TimeValue.timeValueMillis(10), future1);

        assertFutureThrows(future1, IllegalStateException.class, "timed out after 10ms");

        PlainActionFuture<PersistentTask<?>> failedUpdateFuture = new PlainActionFuture<>();
        persistentTasksService.sendUpdateStateRequest(taskId, -2, new State("should fail"), null, failedUpdateFuture);
        assertFutureThrows(
            failedUpdateFuture,
            ResourceNotFoundException.class,
            "the task with id " + taskId + " and allocation id -2 doesn't exist"
        );

        WaitForPersistentTaskFuture<?> future2 = new WaitForPersistentTaskFuture<>();
        persistentTasksService.waitForPersistentTaskCondition(taskId, Objects::isNull, TimeValue.timeValueSeconds(10), future2);

        logger.info("Completing the running task");
        assertThat(
            new TestTasksRequestBuilder(client()).setOperation("finish").setTargetTaskId(firstRunningTask.taskId()).get().getTasks().size(),
            equalTo(1)
        );

        assertThat(future2.get(), nullValue());
    }

    public void testCreatePersistentTaskWithDuplicateId() throws Exception {
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        String taskId = UUIDs.base64UUID();
        persistentTasksService.sendStartRequest(taskId, TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future);
        future.get();

        PlainActionFuture<PersistentTask<TestParams>> future2 = new PlainActionFuture<>();
        persistentTasksService.sendStartRequest(taskId, TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future2);
        assertFutureThrows(future2, ResourceAlreadyExistsException.class);

        waitForTaskToStart();

        TaskInfo firstRunningTask = clusterAdmin().prepareListTasks()
            .setActions(TestPersistentTasksExecutor.NAME + "[c]")
            .get()
            .getTasks()
            .get(0);

        logger.info("Completing the running task");
        assertThat(
            new TestTasksRequestBuilder(client()).setOperation("finish").setTargetTaskId(firstRunningTask.taskId()).get().getTasks().size(),
            equalTo(1)
        );

        logger.info("Waiting for persistent task with id {} to disappear", firstRunningTask.id());
        assertBusy(() -> {
            assertThat(clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks(), empty());
        });
    }

    public void testUnassignRunningPersistentTask() throws Exception {
        PersistentTasksClusterService persistentTasksClusterService = internalCluster().getInstance(
            PersistentTasksClusterService.class,
            internalCluster().getMasterName()
        );
        persistentTasksClusterService.setRecheckInterval(TimeValue.timeValueMillis(1));
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        TestParams testParams = new TestParams("Blah");
        testParams.setExecutorNodeAttr("test");
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, testParams, null, future);
        PersistentTask<TestParams> task = future.get();
        String taskId = task.getId();

        Settings nodeSettings = Settings.builder().put(nodeSettings(0, Settings.EMPTY)).put("node.attr.test_attr", "test").build();
        internalCluster().startNode(nodeSettings);

        waitForTaskToStart();

        PlainActionFuture<PersistentTask<?>> unassignmentFuture = new PlainActionFuture<>();

        TestPersistentTasksExecutor.setNonClusterStateCondition(false);

        persistentTasksClusterService.unassignPersistentTask(taskId, task.getAllocationId() + 1, "unassignment test", unassignmentFuture);
        PersistentTask<?> unassignedTask = unassignmentFuture.get();
        assertThat(unassignedTask.getId(), equalTo(taskId));
        assertThat(unassignedTask.getAssignment().getExplanation(), equalTo("unassignment test"));
        assertThat(unassignedTask.getAssignment().getExecutorNode(), is(nullValue()));

        assertBusy(() -> {
            List<TaskInfo> tasks = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks();
            assertThat(tasks.size(), equalTo(0));

            assertClusterStateHasTask(taskId);
        });

        TestPersistentTasksExecutor.setNonClusterStateCondition(true);

        waitForTaskToStart();

        assertClusterStateHasTask(taskId);

        TaskInfo taskInfo = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks().get(0);
        stopOrCancelTask(taskInfo.taskId());
    }

    public void testAbortLocally() throws Exception {
        PersistentTasksClusterService persistentTasksClusterService = internalCluster().getInstance(
            PersistentTasksClusterService.class,
            internalCluster().getMasterName()
        );
        persistentTasksClusterService.setRecheckInterval(TimeValue.timeValueMillis(1));
        PersistentTasksService persistentTasksService = internalCluster().getInstance(PersistentTasksService.class);
        PlainActionFuture<PersistentTask<TestParams>> future = new PlainActionFuture<>();
        persistentTasksService.sendStartRequest(UUIDs.base64UUID(), TestPersistentTasksExecutor.NAME, new TestParams("Blah"), null, future);
        String taskId = future.get().getId();
        long allocationId = future.get().getAllocationId();
        waitForTaskToStart();
        TaskInfo firstRunningTask = clusterAdmin().prepareListTasks()
            .setActions(TestPersistentTasksExecutor.NAME + "[c]")
            .get()
            .getTasks()
            .get(0);

        TestPersistentTasksExecutor.setNonClusterStateCondition(false);

        assertThat(firstRunningTask.parentTaskId().getId(), equalTo(allocationId));
        assertThat(firstRunningTask.parentTaskId().getNodeId(), equalTo("cluster"));

        assertThat(
            new TestTasksRequestBuilder(client()).setOperation("abort_locally")
                .setTargetTaskId(firstRunningTask.taskId())
                .get()
                .getTasks()
                .size(),
            equalTo(1)
        );

        assertBusy(() -> {
            List<TaskInfo> tasks = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks();
            assertThat(tasks.size(), equalTo(0));

            PersistentTask<?> task = assertClusterStateHasTask(taskId);
            assertThat(task.getAssignment().getExecutorNode(), nullValue());
            assertThat(
                task.getAssignment().getExplanation(),
                either(equalTo("Simulating local abort")).or(equalTo("non cluster state condition prevents assignment"))
            );
        });

        TestPersistentTasksExecutor.setNonClusterStateCondition(true);

        waitForTaskToStart();

        assertBusy(() -> {
            PersistentTask<?> task = assertClusterStateHasTask(taskId);
            assertThat(task.getAssignment().getExplanation(), not(equalTo("Simulating local abort")));
        });

        TaskInfo taskInfo = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks().get(0);
        stopOrCancelTask(taskInfo.taskId());
    }

    private void stopOrCancelTask(TaskId taskId) {
        if (randomBoolean()) {
            logger.info("Completing the running task");
            assertThat(
                new TestTasksRequestBuilder(client()).setOperation("finish").setTargetTaskId(taskId).get().getTasks().size(),
                equalTo(1)
            );

        } else {
            logger.info("Cancelling the running task");
            assertThat(clusterAdmin().prepareCancelTasks().setTargetTaskId(taskId).get().getTasks().size(), equalTo(1));
        }
    }

    private static void waitForTaskToStart() throws Exception {
        assertBusy(() -> {
            assertThat(
                clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks().size(),
                equalTo(1)
            );
        });
    }

    private static PersistentTask<?> assertClusterStateHasTask(String taskId) {
        ClusterState state = internalCluster().clusterService().state();
        Collection<PersistentTask<?>> clusterTasks = findTasks(state, TestPersistentTasksExecutor.NAME);
        assertThat(clusterTasks, hasSize(1));
        PersistentTask<?> task = clusterTasks.iterator().next();
        assertThat(task.getId(), equalTo(taskId));
        return task;
    }

    private void assertNoRunningTasks() throws Exception {
        assertBusy(() -> {
            List<TaskInfo> tasks = clusterAdmin().prepareListTasks().setActions(TestPersistentTasksExecutor.NAME + "[c]").get().getTasks();
            logger.info("Found {} tasks", tasks.size());
            assertThat(tasks.size(), equalTo(0));

            ClusterState state = internalCluster().clusterService().state();
            assertThat(findTasks(state, TestPersistentTasksExecutor.NAME), empty());
        });
    }

}
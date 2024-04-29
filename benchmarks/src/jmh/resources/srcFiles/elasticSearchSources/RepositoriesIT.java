/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.snapshots;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.TransportDeleteSnapshotAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.ClusterStateTaskListener;
import org.elasticsearch.cluster.SimpleBatchedExecutor;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.RepositoriesMetadata;
import org.elasticsearch.cluster.metadata.RepositoryMetadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.repositories.RepositoryConflictException;
import org.elasticsearch.repositories.RepositoryException;
import org.elasticsearch.repositories.RepositoryVerificationException;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.snapshots.mockstore.MockRepository;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.threadpool.ThreadPool;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;

import static org.elasticsearch.repositories.blobstore.BlobStoreRepository.READONLY_SETTING_KEY;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ESIntegTestCase.ClusterScope(minNumDataNodes = 2)
public class RepositoriesIT extends AbstractSnapshotIntegTestCase {
    public void testRepositoryCreation() throws Exception {
        Client client = client();

        Path location = randomRepoPath();

        createRepository("test-repo-1", "fs", location);

        logger.info("--> verify the repository");
        int numberOfFiles = FileSystemUtils.files(location).length;
        VerifyRepositoryResponse verifyRepositoryResponse = client.admin().cluster().prepareVerifyRepository("test-repo-1").get();
        assertThat(verifyRepositoryResponse.getNodes().size(), equalTo(cluster().numDataAndMasterNodes()));

        logger.info("--> verify that we didn't leave any files as a result of verification");
        assertThat(FileSystemUtils.files(location).length, equalTo(numberOfFiles));

        logger.info("--> check that repository is really there");
        ClusterStateResponse clusterStateResponse = client.admin().cluster().prepareState().clear().setMetadata(true).get();
        Metadata metadata = clusterStateResponse.getState().getMetadata();
        RepositoriesMetadata repositoriesMetadata = metadata.custom(RepositoriesMetadata.TYPE);
        assertThat(repositoriesMetadata, notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1").type(), equalTo("fs"));

        logger.info("-->  creating another repository");
        createRepository("test-repo-2", "fs");

        logger.info("--> check that both repositories are in cluster state");
        clusterStateResponse = client.admin().cluster().prepareState().clear().setMetadata(true).get();
        metadata = clusterStateResponse.getState().getMetadata();
        repositoriesMetadata = metadata.custom(RepositoriesMetadata.TYPE);
        assertThat(repositoriesMetadata, notNullValue());
        assertThat(repositoriesMetadata.repositories().size(), equalTo(2));
        assertThat(repositoriesMetadata.repository("test-repo-1"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1").type(), equalTo("fs"));
        assertThat(repositoriesMetadata.repository("test-repo-2"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-2").type(), equalTo("fs"));

        logger.info("--> check that both repositories can be retrieved by getRepositories query");
        GetRepositoriesResponse repositoriesResponse = client.admin()
            .cluster()
            .prepareGetRepositories(randomFrom("_all", "*", "test-repo-*"))
            .get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(2));
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-1"), notNullValue());
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-2"), notNullValue());

        logger.info("--> check that trying to create a repository with the same settings repeatedly does not update cluster state");
        String beforeStateUuid = clusterStateResponse.getState().stateUUID();
        assertThat(
            client.admin()
                .cluster()
                .preparePutRepository("test-repo-1")
                .setType("fs")
                .setSettings(Settings.builder().put("location", location))
                .get()
                .isAcknowledged(),
            equalTo(true)
        );
        assertEquals(beforeStateUuid, client.admin().cluster().prepareState().clear().get().getState().stateUUID());

        logger.info("--> delete repository test-repo-1");
        client.admin().cluster().prepareDeleteRepository("test-repo-1").get();
        repositoriesResponse = client.admin().cluster().prepareGetRepositories().get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(1));
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-2"), notNullValue());

        logger.info("--> delete repository test-repo-2");
        client.admin().cluster().prepareDeleteRepository("test-repo-2").get();
        repositoriesResponse = client.admin().cluster().prepareGetRepositories().get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(0));
    }

    private RepositoryMetadata findRepository(List<RepositoryMetadata> repositories, String name) {
        for (RepositoryMetadata repository : repositories) {
            if (repository.name().equals(name)) {
                return repository;
            }
        }
        return null;
    }

    public void testMisconfiguredRepository() {
        Client client = client();

        logger.info("--> trying creating repository with incorrect settings");
        try {
            client.admin().cluster().preparePutRepository("test-repo").setType("fs").get();
            fail("Shouldn't be here");
        } catch (RepositoryException ex) {
            assertThat(ex.getCause().getMessage(), equalTo("[test-repo] missing location"));
        }

        logger.info("--> trying creating fs repository with location that is not registered in path.repo setting");
        Path invalidRepoPath = createTempDir().toAbsolutePath();
        String location = invalidRepoPath.toString();
        try {
            clusterAdmin().preparePutRepository("test-repo").setType("fs").setSettings(Settings.builder().put("location", location)).get();
            fail("Shouldn't be here");
        } catch (RepositoryException ex) {
            assertThat(
                ex.getCause().getMessage(),
                containsString("location [" + location + "] doesn't match any of the locations specified by path.repo")
            );
        }
    }

    public void testRepositoryAckTimeout() {
        logger.info("-->  creating repository test-repo-1 with 0s timeout - shouldn't ack");
        AcknowledgedResponse putRepositoryResponse = clusterAdmin().preparePutRepository("test-repo-1")
            .setType("fs")
            .setSettings(
                Settings.builder()
                    .put("location", randomRepoPath())
                    .put("compress", randomBoolean())
                    .put("chunk_size", randomIntBetween(5, 100), ByteSizeUnit.BYTES)
            )
            .setTimeout(TimeValue.ZERO)
            .get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(false));

        logger.info("-->  creating repository test-repo-2 with standard timeout - should ack");
        putRepositoryResponse = clusterAdmin().preparePutRepository("test-repo-2")
            .setType("fs")
            .setSettings(
                Settings.builder()
                    .put("location", randomRepoPath())
                    .put("compress", randomBoolean())
                    .put("chunk_size", randomIntBetween(5, 100), ByteSizeUnit.BYTES)
            )
            .get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("-->  deleting repository test-repo-2 with 0s timeout - shouldn't ack");
        AcknowledgedResponse deleteRepositoryResponse = clusterAdmin().prepareDeleteRepository("test-repo-2")
            .setTimeout(TimeValue.ZERO)
            .get();
        assertThat(deleteRepositoryResponse.isAcknowledged(), equalTo(false));

        logger.info("-->  deleting repository test-repo-1 with standard timeout - should ack");
        deleteRepositoryResponse = clusterAdmin().prepareDeleteRepository("test-repo-1").get();
        assertThat(deleteRepositoryResponse.isAcknowledged(), equalTo(true));
    }

    public void testRepositoryVerification() {
        disableRepoConsistencyCheck("This test does not create any data in the repository.");

        Client client = client();

        Settings settings = Settings.builder().put("location", randomRepoPath()).put("random_control_io_exception_rate", 1.0).build();
        Settings readonlySettings = Settings.builder().put(settings).put(READONLY_SETTING_KEY, true).build();
        logger.info("-->  creating repository that cannot write any files - should fail");
        ActionRequestBuilder<?, ?> builder3 = client.admin()
            .cluster()
            .preparePutRepository("test-repo-1")
            .setType("mock")
            .setSettings(settings);
        expectThrows(RepositoryVerificationException.class, builder3);

        logger.info("-->  creating read-only repository that cannot read any files - should fail");
        ActionRequestBuilder<?, ?> builder2 = client.admin()
            .cluster()
            .preparePutRepository("test-repo-2")
            .setType("mock")
            .setSettings(readonlySettings);
        expectThrows(RepositoryVerificationException.class, builder2);

        logger.info("-->  creating repository that cannot write any files, but suppress verification - should be acked");
        assertAcked(client.admin().cluster().preparePutRepository("test-repo-1").setType("mock").setSettings(settings).setVerify(false));

        logger.info("-->  verifying repository");
        ActionRequestBuilder<?, ?> builder1 = client.admin().cluster().prepareVerifyRepository("test-repo-1");
        expectThrows(RepositoryVerificationException.class, builder1);

        logger.info("-->  creating read-only repository that cannot read any files, but suppress verification - should be acked");
        assertAcked(
            client.admin().cluster().preparePutRepository("test-repo-2").setType("mock").setSettings(readonlySettings).setVerify(false)
        );

        logger.info("-->  verifying repository");
        ActionRequestBuilder<?, ?> builder = client.admin().cluster().prepareVerifyRepository("test-repo-2");
        expectThrows(RepositoryVerificationException.class, builder);

        Path location = randomRepoPath();

        logger.info("-->  creating repository");
        try {
            client.admin()
                .cluster()
                .preparePutRepository("test-repo-1")
                .setType("mock")
                .setSettings(Settings.builder().put("location", location).put("localize_location", true))
                .get();
            fail("RepositoryVerificationException wasn't generated");
        } catch (RepositoryVerificationException ex) {
            assertThat(ExceptionsHelper.stackTrace(ex), containsString("is not shared"));
        }
    }

    public void testRepositoryConflict() throws Exception {
        logger.info("--> creating repository");
        final String repo = "test-repo";
        assertAcked(
            clusterAdmin().preparePutRepository(repo)
                .setType("mock")
                .setSettings(
                    Settings.builder()
                        .put("location", randomRepoPath())
                        .put("random", randomAlphaOfLength(10))
                        .put("wait_after_unblock", 200)
                )
        );

        logger.info("--> snapshot");
        final String index = "test-idx";
        assertAcked(prepareCreate(index, 1, Settings.builder().put("number_of_shards", 1).put("number_of_replicas", 0)));
        for (int i = 0; i < 10; i++) {
            indexDoc(index, Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        final String snapshot1 = "test-snap1";
        clusterAdmin().prepareCreateSnapshot(repo, snapshot1).setWaitForCompletion(true).get();
        String blockedNode = internalCluster().getMasterName();
        blockMasterOnWriteIndexFile(repo);
        logger.info("--> start deletion of snapshot");
        ActionFuture<AcknowledgedResponse> future = clusterAdmin().prepareDeleteSnapshot(repo, snapshot1).execute();
        logger.info("--> waiting for block to kick in on node [{}]", blockedNode);
        waitForBlock(blockedNode, repo);

        assertTrue(
            clusterAdmin().prepareListTasks()
                .setActions(TransportDeleteSnapshotAction.TYPE.name())
                .setDetailed(true)
                .get()
                .getTasks()
                .stream()
                .anyMatch(ti -> ("[" + repo + "][" + snapshot1 + "]").equals(ti.description()))
        );

        logger.info("--> try deleting the repository, should fail because the deletion of the snapshot is in progress");
        RepositoryConflictException e1 = expectThrows(RepositoryConflictException.class, clusterAdmin().prepareDeleteRepository(repo));
        assertThat(e1.status(), equalTo(RestStatus.CONFLICT));
        assertThat(e1.getMessage(), containsString("trying to modify or unregister repository that is currently used"));

        logger.info("--> try updating the repository, should fail because the deletion of the snapshot is in progress");
        RepositoryConflictException e2 = expectThrows(
            RepositoryConflictException.class,
            clusterAdmin().preparePutRepository(repo).setType("mock").setSettings(Settings.builder().put("location", randomRepoPath()))
        );
        assertThat(e2.status(), equalTo(RestStatus.CONFLICT));
        assertThat(e2.getMessage(), containsString("trying to modify or unregister repository that is currently used"));

        logger.info("--> unblocking blocked node [{}]", blockedNode);
        unblockNode(repo, blockedNode);

        logger.info("--> wait until snapshot deletion is finished");
        assertAcked(future.actionGet());
    }

    public void testLeakedStaleIndicesAreDeletedBySubsequentDelete() throws Exception {
        Client client = client();
        Path repositoryPath = randomRepoPath();
        final String repositoryName = "test-repo";
        final String snapshot1Name = "test-snap-1";
        final String snapshot2Name = "test-snap-2";

        logger.info("-->  creating repository at {}", repositoryPath.toAbsolutePath());
        createRepository(repositoryName, "mock", repositoryPath);

        logger.info("--> creating index-1 and ingest data");
        createIndex("test-idx-1");
        ensureGreen();
        for (int j = 0; j < 10; j++) {
            indexDoc("test-idx-1", Integer.toString(10 + j), "foo", "bar" + 10 + j);
        }
        refresh();

        logger.info("--> creating first snapshot");
        createFullSnapshot(repositoryName, snapshot1Name);

        logger.info("--> creating index-2 and ingest data");
        createIndex("test-idx-2");
        ensureGreen();
        for (int j = 0; j < 10; j++) {
            indexDoc("test-idx-2", Integer.toString(10 + j), "foo", "bar" + 10 + j);
        }
        refresh();

        logger.info("--> creating second snapshot");
        createFullSnapshot(repositoryName, snapshot2Name);

        final var repository = (MockRepository) internalCluster().getCurrentMasterNodeInstance(RepositoriesService.class)
            .repository(repositoryName);
        repository.setFailOnDeleteContainer(true);

        logger.info("--> delete the second snapshot");
        client.admin().cluster().prepareDeleteSnapshot(repositoryName, snapshot2Name).get();

        repository.setFailOnDeleteContainer(false);

        logger.info("--> delete snapshot one");
        client.admin().cluster().prepareDeleteSnapshot(repositoryName, snapshot1Name).get();

        logger.info("--> check no leftover files");
        assertFileCount(repositoryPath, 2); 

        logger.info("--> done");
    }

    public void testCleanupStaleBlobsConcurrency() throws Exception {

        final var client = client();
        final var repositoryPath = randomRepoPath();
        final var repositoryName = "test-repo";
        createRepository(repositoryName, "mock", repositoryPath);

        final var threadPool = internalCluster().getCurrentMasterNodeInstance(ThreadPool.class);
        final var snapshotPoolSize = threadPool.info(ThreadPool.Names.SNAPSHOT).getMax();
        final var indexCount = snapshotPoolSize * 3;

        for (int i = 0; i < indexCount; i++) {
            createIndex("test-idx-" + i);
            for (int j = 0; j < 10; j++) {
                indexDoc("test-idx-" + i, Integer.toString(10 + j), "foo", "bar" + 10 + j);
            }
        }

        ensureGreen();

        final var snapshotName = "test-snap";
        createFullSnapshot(repositoryName, snapshotName);

        final var executor = threadPool.executor(ThreadPool.Names.SNAPSHOT);
        final var barrier = new CyclicBarrier(snapshotPoolSize + 1);
        final var keepBlocking = new AtomicBoolean(true);
        final var clusterService = internalCluster().getCurrentMasterNodeInstance(ClusterService.class);
        final ToLongFunction<ClusterState> repoGenFn = s -> RepositoriesMetadata.get(s).repository(repositoryName).generation();
        final var repositoryGenerationBeforeDelete = repoGenFn.applyAsLong(clusterService.state());
        final ClusterStateListener clusterStateListener = event -> {
            if (repoGenFn.applyAsLong(event.previousState()) == repositoryGenerationBeforeDelete
                && repoGenFn.applyAsLong(event.state()) > repositoryGenerationBeforeDelete) {

                for (int i = 0; i < snapshotPoolSize - 1; i++) {
                    executor.execute(() -> {
                        while (keepBlocking.get()) {
                            safeAwait(barrier);
                            safeAwait(barrier);
                        }
                    });
                }

                new Runnable() {
                    @Override
                    public void run() {
                        executor.execute(() -> {
                            safeAwait(barrier);
                            safeAwait(barrier);
                            if (keepBlocking.get()) {
                                this.run();
                            }
                        });
                    }
                }.run();
            }
        };
        clusterService.addListener(clusterStateListener);

        final var deleteFuture = new PlainActionFuture<AcknowledgedResponse>();
        client.admin().cluster().prepareDeleteSnapshot(repositoryName, snapshotName).execute(deleteFuture);

        safeAwait(barrier); 
        clusterService.removeListener(clusterStateListener);

        PlainActionFuture.get(fut -> clusterService.createTaskQueue("test", Priority.NORMAL, new SimpleBatchedExecutor<>() {
            @Override
            public Tuple<ClusterState, Object> executeTask(ClusterStateTaskListener clusterStateTaskListener, ClusterState clusterState) {
                return Tuple.tuple(clusterState, null);
            }

            @Override
            public void taskSucceeded(ClusterStateTaskListener clusterStateTaskListener, Object ignored) {
                fut.onResponse(null);
            }
        }).submitTask("test", e -> fail(), null), 10, TimeUnit.SECONDS);

        final IntSupplier queueLength = () -> threadPool.stats()
            .stats()
            .stream()
            .filter(s -> s.name().equals(ThreadPool.Names.SNAPSHOT))
            .findFirst()
            .orElseThrow()
            .queue();

        assertThat(queueLength.getAsInt(), equalTo(snapshotPoolSize + 1));

        safeAwait(barrier); 
        safeAwait(barrier); 

        assertThat(queueLength.getAsInt(), equalTo(snapshotPoolSize));

        safeAwait(barrier); 
        safeAwait(barrier); 

        assertThat(queueLength.getAsInt(), equalTo(0));

        assertFileCount(repositoryPath, 2); 

        keepBlocking.set(false);
        safeAwait(barrier); 
        assertTrue(deleteFuture.get(10, TimeUnit.SECONDS).isAcknowledged());
    }
}
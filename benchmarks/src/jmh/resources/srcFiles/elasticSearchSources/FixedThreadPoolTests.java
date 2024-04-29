/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.threadpool;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.telemetry.metric.MeterRegistry;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.equalTo;

public class FixedThreadPoolTests extends ESThreadPoolTestCase {

    public void testRejectedExecutionCounter() throws InterruptedException {
        final String threadPoolName = randomThreadPool(ThreadPool.ThreadPoolType.FIXED);
        final int size = randomIntBetween(1, EsExecutors.allocatedProcessors(Settings.EMPTY));
        final int queueSize = randomIntBetween(1, 16);
        final long rejections = randomIntBetween(1, 16);

        ThreadPool threadPool = null;
        final Settings nodeSettings = Settings.builder()
            .put("node.name", "testRejectedExecutionCounter")
            .put("thread_pool." + threadPoolName + ".size", size)
            .put("thread_pool." + threadPoolName + ".queue_size", queueSize)
            .build();
        try {
            threadPool = new ThreadPool(nodeSettings, MeterRegistry.NOOP);

            final CountDownLatch latch = new CountDownLatch(size);
            final CountDownLatch block = new CountDownLatch(1);
            for (int i = 0; i < size; i++) {
                threadPool.executor(threadPoolName).execute(() -> {
                    try {
                        latch.countDown();
                        block.await();
                    } catch (InterruptedException e) {
                        fail(e.toString());
                    }
                });
            }

            latch.await();

            for (int i = 0; i < queueSize; i++) {
                threadPool.executor(threadPoolName).execute(() -> {});
            }

            long counter = 0;
            for (int i = 0; i < rejections; i++) {
                try {
                    threadPool.executor(threadPoolName).execute(() -> {});
                } catch (EsRejectedExecutionException e) {
                    counter++;
                }
            }

            block.countDown();

            assertThat(counter, equalTo(rejections));
            assertThat(stats(threadPool, threadPoolName).rejected(), equalTo(rejections));
        } finally {
            terminateThreadPoolIfNeeded(threadPool);
        }
    }

}
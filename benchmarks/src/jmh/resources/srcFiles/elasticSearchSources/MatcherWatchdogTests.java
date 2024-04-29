/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.grok;

import org.elasticsearch.test.ESTestCase;
import org.joni.Matcher;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class MatcherWatchdogTests extends ESTestCase {

    public void testInterrupt() throws Exception {
        AtomicBoolean run = new AtomicBoolean(true); 
        MatcherWatchdog watchdog = MatcherWatchdog.newInstance(10, 100, System::currentTimeMillis, (delay, command) -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            Thread thread = new Thread(() -> {
                if (run.get()) {
                    command.run();
                }
            });
            thread.start();
        });

        Map<?, ?> registry = ((MatcherWatchdog.Default) watchdog).registry;
        assertThat(registry.size(), is(0));
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            Matcher matcher = mock(Matcher.class);
            watchdog.register(matcher);
            verify(matcher, timeout(9999).atLeastOnce()).interrupt();
            interrupted.set(true);
            while (run.get()) {
            } 
            watchdog.unregister(matcher);
        });
        thread.start();
        assertBusy(() -> {
            assertThat(interrupted.get(), is(true));
            assertThat(registry.size(), is(1));
        });
        run.set(false);
        assertBusy(() -> { assertThat(registry.size(), is(0)); });
    }

    public void testIdleIfNothingRegistered() throws Exception {
        long interval = 1L;
        ScheduledExecutorService threadPool = mock(ScheduledExecutorService.class);
        MatcherWatchdog watchdog = MatcherWatchdog.newInstance(
            interval,
            Long.MAX_VALUE,
            System::currentTimeMillis,
            (delay, command) -> threadPool.schedule(command, delay, TimeUnit.MILLISECONDS)
        );
        verifyNoMoreInteractions(threadPool);
        CompletableFuture<Runnable> commandFuture = new CompletableFuture<>();
        doAnswer(invocationOnMock -> {
            commandFuture.complete((Runnable) invocationOnMock.getArguments()[0]);
            return null;
        }).when(threadPool).schedule(any(Runnable.class), eq(interval), eq(TimeUnit.MILLISECONDS));
        Matcher matcher = mock(Matcher.class);
        watchdog.register(matcher);
        Runnable command = commandFuture.get(1L, TimeUnit.MILLISECONDS);
        Mockito.reset(threadPool);
        watchdog.unregister(matcher);
        command.run();
        verifyNoMoreInteractions(threadPool);
        watchdog.register(matcher);
        Thread otherThread = new Thread(() -> {
            Matcher otherMatcher = mock(Matcher.class);
            watchdog.register(otherMatcher);
        });
        try {
            verify(threadPool).schedule(any(Runnable.class), eq(interval), eq(TimeUnit.MILLISECONDS));
            verifyNoMoreInteractions(threadPool);
            otherThread.start();
        } finally {
            otherThread.join();
        }
    }
}
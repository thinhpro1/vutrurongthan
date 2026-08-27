package com.project.game.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterLifecycleSchedulerTest {
    @Test
    void runsTickOnNamedDaemonThread() throws Exception {
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();

        MonsterLifecycleScheduler scheduler = new MonsterLifecycleScheduler(
                () -> {
                    threadName.set(Thread.currentThread().getName());
                    daemon.set(Thread.currentThread().isDaemon());
                    ticked.countDown();
                },
                10L);

        try {
            scheduler.start();

            assertTrue(ticked.await(1, TimeUnit.SECONDS));
            assertEquals("monster-lifecycle", threadName.get());
            assertTrue(daemon.get());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void continuesSchedulingAfterTickThrows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch secondCall = new CountDownLatch(1);

        MonsterLifecycleScheduler scheduler = new MonsterLifecycleScheduler(
                () -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        throw new IllegalStateException("synthetic lifecycle failure");
                    }
                    secondCall.countDown();
                },
                10L);

        try {
            scheduler.start();

            assertTrue(secondCall.await(1, TimeUnit.SECONDS));
            assertTrue(calls.get() >= 2);
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void startStopAreIdempotentAndSchedulerCanRestart() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        MonsterLifecycleScheduler scheduler = new MonsterLifecycleScheduler(
                calls::incrementAndGet,
                10L);

        scheduler.start();
        scheduler.start();

        awaitAtLeast(calls, 1);

        scheduler.stop();
        scheduler.stop();

        int beforeRestart = calls.get();

        scheduler.start();
        awaitAtLeast(calls, beforeRestart + 1);
        scheduler.stop();
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new MonsterLifecycleScheduler(() -> {}, 0L));
    }

    private static void awaitAtLeast(AtomicInteger value, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertTrue(value.get() >= expected,
                () -> "expected at least " + expected + " ticks but saw " + value.get());
    }
}

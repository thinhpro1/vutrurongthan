package com.project.game.monster;

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
        AtomicBoolean restarted = new AtomicBoolean();
        CountDownLatch firstTick = new CountDownLatch(1);
        CountDownLatch restartedTick = new CountDownLatch(1);

        MonsterLifecycleScheduler scheduler = new MonsterLifecycleScheduler(
                () -> {
                    calls.incrementAndGet();
                    (restarted.get() ? restartedTick : firstTick).countDown();
                },
                10L);

        scheduler.start();
        scheduler.start();

        assertTrue(firstTick.await(1, TimeUnit.SECONDS));

        scheduler.stop();
        scheduler.stop();

        restarted.set(true);
        scheduler.start();
        assertTrue(restartedTick.await(1, TimeUnit.SECONDS));
        scheduler.stop();
        assertTrue(calls.get() >= 2);
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new MonsterLifecycleScheduler(() -> {}, 0L));
    }
}

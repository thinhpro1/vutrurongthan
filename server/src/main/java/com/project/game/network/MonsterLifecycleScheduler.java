package com.project.game.network;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MonsterLifecycleScheduler {
    private static final Logger LOGGER = Logger.getLogger(MonsterLifecycleScheduler.class.getName());

    private final Runnable tick;
    private final long periodMillis;
    private ScheduledExecutorService executor;

    MonsterLifecycleScheduler(Runnable tick, long periodMillis) {
        this.tick = Objects.requireNonNull(tick, "tick");
        if (periodMillis < 1L) {
            throw new IllegalArgumentException("periodMillis must be positive");
        }
        this.periodMillis = periodMillis;
    }

    synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "monster-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::runSafely,
                periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    synchronized void stop() {
        ScheduledExecutorService current = executor;
        if (current == null) {
            return;
        }
        executor = null;
        current.shutdownNow();
    }

    private void runSafely() {
        try {
            tick.run();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Monster lifecycle tick failed", exception);
        }
    }
}

package com.project.game.monster;

import com.project.game.map.MapManager;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterManagerLifecycleTest {
    @Test
    void runsTickOnNamedDaemonThread() throws Exception {
        CountDownLatch ticked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();
        Clock clock = new RecordingClock(() -> {
            threadName.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            ticked.countDown();
        });
        MonsterManager manager = new MonsterManager(resources(), clock, new java.util.Random(1L));

        try {
            manager.start(mapManager(manager));

            assertTrue(ticked.await(2, TimeUnit.SECONDS));
            assertEquals("monster-lifecycle", threadName.get());
            assertTrue(daemon.get());
        } finally {
            manager.stop();
        }
    }

    @Test
    void continuesSchedulingAfterTickThrows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch secondCall = new CountDownLatch(1);
        Clock clock = new RecordingClock(() -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new IllegalStateException("synthetic lifecycle failure");
            }
            secondCall.countDown();
        });
        MonsterManager manager = new MonsterManager(resources(), clock, new java.util.Random(1L));

        try {
            manager.start(mapManager(manager));

            assertTrue(secondCall.await(2, TimeUnit.SECONDS));
            assertTrue(calls.get() >= 2);
        } finally {
            manager.stop();
        }
    }

    @Test
    void startStopAreIdempotentAndManagerCanRestart() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean restarted = new AtomicBoolean();
        CountDownLatch firstTick = new CountDownLatch(1);
        CountDownLatch restartedTick = new CountDownLatch(1);
        Clock clock = new RecordingClock(() -> {
            calls.incrementAndGet();
            (restarted.get() ? restartedTick : firstTick).countDown();
        });
        MonsterManager manager = new MonsterManager(resources(), clock, new java.util.Random(1L));
        MapManager maps = mapManager(manager);

        manager.start(maps);
        manager.start(maps);

        assertTrue(firstTick.await(2, TimeUnit.SECONDS));

        manager.stop();
        manager.stop();

        restarted.set(true);
        manager.start(maps);
        assertTrue(restartedTick.await(2, TimeUnit.SECONDS));
        manager.stop();
        assertTrue(calls.get() >= 2);
    }

    private static MapManager mapManager(MonsterManager manager) {
        AreaService area = new AreaService(new PlayerPacketWriter(), new MonsterPacketWriter());
        return new MapManager(MapTestSupport.canonicalMaps(), manager, area);
    }

    private static GameResources resources() {
        return GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                MonsterTestSupport.canonicalRepository());
    }

    private static final class RecordingClock extends Clock {
        private final Runnable read;

        private RecordingClock(Runnable read) {
            this.read = read;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            read.run();
            return 1_000_000L;
        }
    }
}

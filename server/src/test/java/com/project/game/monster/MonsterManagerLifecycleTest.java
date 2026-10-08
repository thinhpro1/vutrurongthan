package com.project.game.monster;

import com.project.game.map.MapManager;
import com.project.game.network.Session;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.service.AreaService;
import com.project.game.testsupport.GameplayTestSupport;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterManagerLifecycleTest {
    @Test
    void zoneUpdatesOnItsOwnVirtualThreadOnlyWhileItHasPlayers() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean virtual = new AtomicBoolean();
        CountDownLatch ticked = new CountDownLatch(1);
        Clock clock = new RecordingClock(() -> {
            calls.incrementAndGet();
            virtual.set(Thread.currentThread().isVirtual());
            ticked.countDown();
        });
        MonsterManager manager = new MonsterManager(resources(), clock, new java.util.Random(1L));
        MapManager maps = mapManager(manager);

        try {
            manager.start(maps);
            Thread.sleep(300);
            assertEquals(0, calls.get(), "empty Zones must stay frozen");

            Session session = joinHome(maps);
            assertTrue(ticked.await(2, TimeUnit.SECONDS));
            assertTrue(virtual.get());

            maps.leave(session);
            Thread.sleep(300);
            int afterLeave = calls.get();
            Thread.sleep(300);
            assertEquals(afterLeave, calls.get(), "Zone must freeze after its last Player leaves");
        } finally {
            manager.stop(maps);
        }
    }

    @Test
    void continuesUpdatingAfterTickThrows() throws Exception {
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
        MapManager maps = mapManager(manager);

        try {
            manager.start(maps);
            joinHome(maps);

            assertTrue(secondCall.await(2, TimeUnit.SECONDS));
            assertTrue(calls.get() >= 2);
        } finally {
            manager.stop(maps);
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
        joinHome(maps);

        manager.start(maps);
        manager.start(maps);
        assertTrue(firstTick.await(2, TimeUnit.SECONDS));

        manager.stop(maps);
        manager.stop(maps);
        Thread.sleep(300);
        int afterStop = calls.get();
        Thread.sleep(300);
        assertEquals(afterStop, calls.get());

        restarted.set(true);
        manager.start(maps);
        // Không gửi input nào: Zone còn Player phải tự update lại.
        assertTrue(restartedTick.await(2, TimeUnit.SECONDS));
        manager.stop(maps);
        assertTrue(calls.get() >= 2);
    }

    @Test
    void eachZoneGetsItsOwnRandomGenerator() {
        List<RandomGenerator> created = new ArrayList<>();
        MonsterManager manager = new MonsterManager(resources(), Clock.systemUTC(), () -> {
            RandomGenerator random = new java.util.Random(created.size());
            created.add(random);
            return random;
        });
        MapManager maps = mapManager(manager);
        int zoneCount = 0;
        for (com.project.game.map.Map map : maps.maps()) {
            zoneCount += map.zones().size();
        }

        try {
            manager.start(maps);

            assertEquals(zoneCount, created.size());
            Set<RandomGenerator> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            distinct.addAll(created);
            assertEquals(zoneCount, distinct.size(), "Zones ticking in parallel must not share a generator");
        } finally {
            manager.stop(maps);
        }
    }

    private static Session joinHome(MapManager maps) {
        Session session = GameplayTestSupport.session(
                TestPlayers.at(TestPlayers.initial(1L, 7, "alpha1", 0), 0, 0, 1250, 648));
        assertTrue(maps.finishLoad(session));
        return session;
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

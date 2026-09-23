package com.project.game.monster;

import com.project.game.map.*;
import com.project.game.combat.*;
import com.project.game.network.*;
import com.project.game.network.message.*;
import com.project.game.network.packet.*;
import com.project.game.network.codec.*;
import com.project.game.network.transport.*;
import com.project.game.player.*;
import com.project.game.account.*;
import com.project.game.resource.*;
import com.project.game.testsupport.GameplayServices;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.project.game.testsupport.GameplayTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MonsterServiceSnapshotTest {

    @Test
    void snapshotsUseOnlyPolicyValidZonesAndLifecycleVisitsRegisteredZones() {
        ZoneRegistry zones = new ZoneRegistry(
                com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                new MonsterFactory(
                GameResources.fromFrameRoot(Path.of("resources", "json"),
                        com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                        com.project.game.testsupport.MonsterTestSupport.canonicalRepository())));
        MonsterService monsters = new MonsterService(
                zones, new MonsterPacketWriter(), new PlayerPacketWriter());

        assertEquals(2, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(2, zones.snapshot().size());

        assertNotNull(monsters.monsterSnapshots(0, 0));
        assertEquals(2, zones.snapshot().size());
        assertNotNull(monsters.monsterSnapshots(0, 1));
        assertEquals(3, zones.snapshot().size());
        monsters.tickLifecycle();
        assertEquals(3, zones.snapshot().size());
        assertThrows(IllegalArgumentException.class, () -> monsters.monsterSnapshots(99, 0));
    }

    @Test
    void monsterSnapshotCreatesZoneWithoutJoiningPlayer() {
        GameplayServices maps = mapsWithMonsters();

        List<MonsterSnapshot> monsters = maps.monsterService().monsterSnapshots(1, 0);

        assertEquals(6, monsters.size());
        assertEquals(
                List.of(101, 102, 103, 104, 105, 106),
                monsters.stream().map(MonsterSnapshot::id).toList());
        assertEquals(0, maps.mapService().memberCount(1, 0));
    }

    @Test
    void mapZeroZoneStartsWithoutMonsters() {
        GameplayServices maps = mapsWithMonsters();
        assertTrue(maps.monsterService().monsterSnapshots(0, 0).isEmpty());
        assertEquals(0, maps.mapService().memberCount(0, 0));
    }

    @Test
    void finishLoadReusesZoneCreatedForMonsterSnapshot() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        List<MonsterSnapshot> before = maps.monsterService().monsterSnapshots(1, 0);
        Session joining = session(player(1, 1, 0), maps);

        assertEquals(0, maps.mapService().memberCount(1, 0));
        maps.mapService().finishLoad(joining);

        assertEquals(1, maps.mapService().memberCount(1, 0));
        assertEquals(before, maps.monsterService().monsterSnapshots(1, 0));
    }

    @Test
    void differentMap1ZonesStartWithEquivalentSeeds() {
        GameplayServices maps = mapsWithMonsters();
        List<MonsterSnapshot> zone0 = maps.monsterService().monsterSnapshots(1, 0);
        List<MonsterSnapshot> zone1 = maps.monsterService().monsterSnapshots(1, 1);

        assertEquals(zone0, zone1);
        assertEquals(6, zone0.size());
        assertEquals(6, zone1.size());
    }

    @Test
    void concurrentMonsterSnapshotsRemainStableForSameZone() throws Exception {
        GameplayServices maps = mapsWithMonsters();
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<List<MonsterSnapshot>> first = new AtomicReference<>();
        AtomicReference<List<MonsterSnapshot>> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread one = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                first.set(maps.monsterService().monsterSnapshots(1, 0));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });

        Thread two = Thread.ofVirtual().start(() -> {
            try {
                start.await();
                second.set(maps.monsterService().monsterSnapshots(1, 0));
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        });

        start.await();
        one.join();
        two.join();

        if (failure.get() != null) {
            throw new AssertionError(
                    "concurrent monster snapshot failed",
                    failure.get());
        }

        assertEquals(first.get(), second.get());
        assertEquals(6, first.get().size());
        assertEquals(0, maps.mapService().memberCount(1, 0));
    }
}

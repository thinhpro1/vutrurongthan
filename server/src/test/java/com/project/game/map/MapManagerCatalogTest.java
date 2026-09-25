package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.MapTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapManagerCatalogTest {
    @Test
    void onlineStartupCreatesExactlyMinimumZonesAndOfflineCreatesNone() {
        MapManager registry = registry();
        com.project.game.map.Map runtimeMap = registry.findMap(1);

        assertNotNull(runtimeMap);
        assertNotNull(runtimeMap.findZone(0));
        assertNotNull(runtimeMap.findZone(1));
        assertNull(runtimeMap.findZone(2));
        assertNull(runtimeMap.findZone(3));
        assertNull(registry.findMap(0));
        assertEquals(2, registry.zones().size());
    }

    @Test
    void validOnlineZonesAreCreatedLazilyWithinExclusiveMaximum() {
        MapManager registry = registry();

        com.project.game.map.Map runtimeMap = registry.getMap(1);
        Zone zone2 = runtimeMap.getOrCreateZone(2);
        Zone zone3 = runtimeMap.getOrCreateZone(3);

        assertSame(zone2, runtimeMap.getOrCreateZone(2));
        assertSame(zone3, runtimeMap.findZone(3));
        assertEquals(4, registry.zones().size());
        assertThrows(IllegalArgumentException.class, () -> runtimeMap.getOrCreateZone(4));
        assertThrows(IllegalArgumentException.class, () -> runtimeMap.getOrCreateZone(-1));
    }

    @Test
    void unknownAndOfflineMapsCannotCreateNormalZones() {
        MapManager registry = registry();

        assertThrows(IllegalArgumentException.class, () -> registry.getMap(99));
        assertThrows(IllegalArgumentException.class, () -> registry.getMap(0));
        assertNull(registry.findMap(0));
    }

    @Test
    void eachZoneGetsIndependentMonsterRuntimeState() {
        MapManager registry = registry();

        com.project.game.map.Map runtimeMap = registry.getMap(1);
        Zone first = runtimeMap.getOrCreateZone(0);
        Zone second = runtimeMap.getOrCreateZone(1);
        assertNotSame(first, second);
        assertEquals(300L, second.monsterSnapshots().getFirst().hp());

        first.damageMonster(101, 1, 10L, 0L);

        assertEquals(290L, first.monsterSnapshots().getFirst().hp());
        assertEquals(300L, second.monsterSnapshots().getFirst().hp());
    }

    @Test
    void catalogIsDefensivelyCopiedAndSnapshotContainsOnlyRegisteredZones() {
        java.util.Map<Integer, MapTemplate> source = new HashMap<>(maps());
        MapManager registry = new MapManager(source, monsterFactory(), new PlayerPacketWriter());
        source.clear();

        assertEquals(2, registry.zones().size());
        com.project.game.map.Map runtimeMap = registry.findMap(1);
        assertNotNull(runtimeMap.findZone(0));
        assertNull(runtimeMap.findZone(2));
        assertEquals(2, runtimeMap.findZone(0).maxPlayer());
    }

    private static MapManager registry() {
        return new MapManager(maps(), monsterFactory(), new PlayerPacketWriter());
    }

    private static java.util.Map<Integer, MapTemplate> maps() {
        java.util.Map<Integer, MapTemplate> canonical = MapTestSupport.canonicalMaps();
        MapTemplate online = withPolicy(canonical.get(1), "ONLINE", 2, 4, 2);
        MapTemplate offline = withPolicy(canonical.get(0), "OFFLINE", 2, 4, 2);
        return java.util.Map.of(online.id(), online, offline.id(), offline);
    }

    private static java.util.Map<Integer, MapTemplate> canonicalMaps() {
        return MapTestSupport.canonicalMaps();
    }

    private static MapTemplate withPolicy(
            MapTemplate map, String type, int minZone, int maxZone, int maxPlayer) {
        return new MapTemplate(
                map.id(), map.name(), type, map.planet(), minZone, maxZone, maxPlayer,
                map.dataId(), map.data(), map.waypoints());
    }

    private static MonsterFactory monsterFactory() {
        return new MonsterFactory(GameResources.fromFrameRoot(
                Path.of("resources", "json"), canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
    }
}

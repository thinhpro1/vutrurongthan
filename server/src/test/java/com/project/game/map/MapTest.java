package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.MapTestSupport;
import com.project.game.testsupport.MonsterTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapTest {
    @Test
    void constructorCreatesMinimumZonesAndFindDoesNotCreate() {
        MapTemplate template = policy(MapTestSupport.canonicalMaps().get(1), 2, 4, 2);
        Map map = new Map(template, monsterFactory());

        assertEquals(List.of(0, 1), map.zones().stream().map(Zone::zoneId).toList());
        assertNotSame(map.findZone(1), map.findZone(0));
        assertNull(map.findZone(2));
        assertEquals(2, map.zones().size());
    }

    @Test
    void constructorCreatesExactlyMinimumZonesAndFindsOnlyExistingZones() {
        MapTemplate template = policy(MapTestSupport.canonicalMaps().get(1), 1, 4, 2);
        Map map = new Map(template, monsterFactory());

        assertEquals(List.of(0), map.zones().stream().map(Zone::zoneId).toList());
        assertSame(map.zones().getFirst(), map.findZone(0));
        assertNull(map.findZone(1));
        assertNull(map.findZone(3));
        assertEquals(1, map.zones().size());
    }

    @Test
    void findsWaypointUsingTheMapTemplateRules() {
        Map map = new Map(policy(MapTestSupport.canonicalMaps().get(0), 1, 2, 2), monsterFactory());
        Waypoint expected = map.template().waypoints().getFirst();

        assertSame(expected, map.findWaypoint(expected.x(), expected.y()));
        assertNull(map.findWaypoint(-1, -1));
    }

    @Test
    void runtimeMapsDoNotShareZones() {
        MapTemplate template = policy(MapTestSupport.canonicalMaps().get(1), 1, 2, 2);

        Map first = new Map(template, monsterFactory());
        Map second = new Map(template, monsterFactory());

        assertNotSame(first.findZone(0), second.findZone(0));
    }

    private static MapTemplate policy(MapTemplate source, int minZone, int maxZone, int maxPlayer) {
        return new MapTemplate(source.id(), source.name(), "ONLINE", source.planet(),
                minZone, maxZone, maxPlayer, source.dataId(), source.data(), source.waypoints());
    }

    private static MonsterFactory monsterFactory() {
        return new MonsterFactory(GameResources.fromFrameRoot(
                Path.of("resources", "json"), MapTestSupport.canonicalMaps(), 2,
                MonsterTestSupport.canonicalRepository()));
    }
}

package com.project.game.network.packet;

import com.project.game.map.LegacyMapTemplate;
import com.project.game.map.LegacyWaypoint;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.PlayerProfile;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapPacketWriterTest {
    @Test
    void serializesCanonicalMap0TemplateAndRuntimeState() throws Exception {
        LegacyMapTemplate map = GameResources
                .fromRoots(null, Path.of("resources", "json"))
                .map(0)
                .orElseThrow();
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(0, 3, 1234, 567);
        List<String> targets = map.waypoints().stream()
                .map(waypoint -> "target-" + waypoint.goMap())
                .toList();
        MonsterSnapshot monster = new MonsterSnapshot(2, 9, 77, 3, 1,
                111, 222, 1000L, 900L, 0);

        Message message = new MapPacketWriter().mapInfo(
                player, map, true, targets, List.of(monster));

        assertEquals(MessageName.MAP_INFO, message.command());
        var reader = message.reader();
        assertEquals(map.id(), reader.readShort());
        assertEquals(map.iconId(), reader.readShort());
        assertEquals(map.name(), reader.readUtf());
        assertEquals(map.row(), reader.readShort());
        assertEquals(map.column(), reader.readShort());
        assertEquals(map.data(), reader.readUtf());
        for (int image : map.imagesBgr()) {
            assertEquals(image, reader.readShort());
        }
        for (List<Integer> row : map.colorsBgr()) {
            for (int value : row) {
                assertEquals(value, reader.readShort());
            }
        }
        assertEquals(map.line(), reader.readBoolean());
        if (map.line()) {
            assertEquals(map.dataLine(), reader.readUtf());
        }
        assertEquals(player.zoneId(), reader.readByte());
        assertEquals(player.x(), reader.readShort());
        assertEquals(player.y(), reader.readShort());
        assertEquals(map.waypoints().size(), reader.readByte());
        for (int index = 0; index < map.waypoints().size(); index++) {
            LegacyWaypoint waypoint = map.waypoints().get(index);
            assertEquals(waypoint.x(), reader.readShort());
            assertEquals(waypoint.y(), reader.readShort());
            assertEquals(waypoint.type(), reader.readByte());
            assertEquals(targets.get(index), reader.readUtf());
        }
        assertEquals(0, reader.readByte());
        assertEquals(1, reader.readByte());
        assertEquals(monster.type(), reader.readByte());
        assertEquals(monster.templateId(), reader.readShort());
        assertEquals(monster.id(), reader.readInt());
        assertEquals(monster.level(), reader.readShort());
        assertEquals(monster.levelStatus(), reader.readByte());
        assertEquals(monster.x(), reader.readShort());
        assertEquals(monster.y(), reader.readShort());
        assertEquals(monster.maxHp(), reader.readLong());
        assertEquals(monster.hp(), reader.readLong());
        assertEquals(monster.status(), reader.readByte());
        assertEquals(0, reader.readShort());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void cachedMapInfoOmitsTemplateButKeepsRuntimeLayout() throws Exception {
        LegacyMapTemplate map = new LegacyMapTemplate(
                4, 5, "cached", 1, 1, "data", List.of(6),
                List.of(List.of(7)), false, null,
                List.of(new LegacyWaypoint(1, 2, 100, 200, 300, 400, 0)));
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(4, 2, 123, 456);

        Message message = new MapPacketWriter().mapInfo(
                player, map, false, List.of("next"), List.of());

        var reader = message.reader();
        assertEquals(4, reader.readShort());
        assertEquals(2, reader.readByte());
        assertEquals(123, reader.readShort());
        assertEquals(456, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(100, reader.readShort());
        assertEquals(200, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals("next", reader.readUtf());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsWaypointNameCountMismatch() {
        LegacyMapTemplate map = simpleMap(List.of(
                new LegacyWaypoint(1, 2, 10, 20, 30, 40, 0)));
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(4, 0, 1, 2);

        assertThrows(IllegalArgumentException.class, () ->
                new MapPacketWriter().mapInfo(player, map, false, List.of(), List.of()));
    }

    @Test
    void rejectsTooManyWaypointsAndMonsters() {
        List<LegacyWaypoint> waypoints = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            waypoints.add(new LegacyWaypoint(index, 2, 10, 20, 30, 40, 0));
        }
        LegacyMapTemplate map = simpleMap(waypoints);
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(4, 0, 1, 2);
        List<String> names = waypoints.stream().map(ignored -> "target").toList();
        List<MonsterSnapshot> monsters = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            monsters.add(new MonsterSnapshot(0, 1, index, 1, 0,
                    1, 2, 3L, 2L, 0));
        }

        assertThrows(IOException.class, () ->
                new MapPacketWriter().mapInfo(player, map, false, names, List.of()));
        LegacyMapTemplate smallMap = simpleMap(List.of());
        assertThrows(IOException.class, () ->
                new MapPacketWriter().mapInfo(player, smallMap, false, List.of(), monsters));
    }

    @Test
    void rejectsLineMapWithoutDataLine() {
        LegacyMapTemplate map = new LegacyMapTemplate(
                4, 5, "line", 1, 1, "data", List.of(), List.of(),
                true, null, List.of());
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0)
                .withLocation(4, 0, 1, 2);

        assertThrows(IOException.class, () ->
                new MapPacketWriter().mapInfo(player, map, true, List.of(), List.of()));
    }

    private static LegacyMapTemplate simpleMap(List<LegacyWaypoint> waypoints) {
        return new LegacyMapTemplate(
                4, 5, "simple", 1, 1, "data", List.of(), List.of(),
                false, null, waypoints);
    }
}

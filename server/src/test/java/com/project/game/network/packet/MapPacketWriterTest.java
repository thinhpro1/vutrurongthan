package com.project.game.network.packet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.project.game.map.MapData;
import com.project.game.map.MapTemplate;
import com.project.game.map.Waypoint;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.Player;
import com.project.game.resource.loader.MapDataLoader;
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPacketWriterTest {
    private static final Path MAP_ROOT = Path.of("resources", "maps");

    @Test
    void serializesCanonicalMap0TemplateAndRuntimeStateInLegacyFieldOrder() throws Exception {
        MapTemplate map = map0();
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(0, 3, 1234, 567);
        List<String> targets = List.of("target-1");

        Message message = new MapPacketWriter().mapInfo(player.zoneId(), player.x(), player.y(), map, true, targets, List.of());

        assertEquals(MessageName.MAP_INFO, message.command());
        var reader = message.reader();
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals("Núi Paozu", reader.readUtf());
        assertEquals(20, reader.readShort());
        assertEquals(62, reader.readShort());
        assertEquals(map.data().collision().data(), reader.readUtf());
        assertEquals(51, reader.readShort());
        assertEquals(52, reader.readShort());
        assertEquals(53, reader.readShort());
        for (int value : List.of(128, 213, 242, 141, 185, 128,
                90, 154, 64, 69, 153, 51)) {
            assertEquals(value, reader.readShort());
        }
        assertFalse(reader.readBoolean());
        assertEquals(3, reader.readByte());
        assertEquals(1234, reader.readShort());
        assertEquals(567, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(4464, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals("target-1", reader.readUtf());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());

        assertEquals(1319, message.payload().length);
        assertEquals("8dacf54d95300568d3290eef90a7c79ee92bfb6da5435f9f08159c93ce3d0d5d",
                sha256(message.payload()));
    }

    @Test
    void dataIdDoesNotBecomeLegacyTerrainId() throws Exception {
        MapTemplate map = new MapTemplate(
                1, "Bờ sông Pu", "OFFLINE", "NAMEK", 1, 3, 40, 2,
                MapDataLoader.load(MAP_ROOT, 2), List.of());
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(1, 0, 1, 2);

        var reader = new MapPacketWriter()
                .mapInfo(player.zoneId(), player.x(), player.y(), map, true, List.of(), List.of())
                .reader();

        assertEquals(1, reader.readShort());
        assertEquals(1, reader.readShort());
    }

    @Test
    void serializesBlockOnlyLineMapAsCompatibilityJson() throws Exception {
        MapTemplate map = lineMap();
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 1, 2);

        var reader = new MapPacketWriter()
                .mapInfo(player.zoneId(), player.x(), player.y(), map, true, List.of(), List.of())
                .reader();

        assertEquals(4, reader.readShort());
        assertEquals(130, reader.readShort());
        assertEquals("line", reader.readUtf());
        assertEquals(20, reader.readShort());
        assertEquals(62, reader.readShort());
        assertEquals("", reader.readUtf());
        assertEquals(51, reader.readShort());
        assertEquals(52, reader.readShort());
        assertEquals(53, reader.readShort());
        for (int value : List.of(128, 213, 242, 141, 185, 128,
                90, 154, 64, 69, 153, 51)) {
            assertEquals(value, reader.readShort());
        }
        assertTrue(reader.readBoolean());

        JsonObject line = JsonParser.parseString(reader.readUtf()).getAsJsonObject();
        assertEquals(4464, line.get("MapWidth").getAsInt());
        assertEquals(1440, line.get("MapHeight").getAsInt());
        assertEquals("BLOCK", line.getAsJsonArray("Lines").get(0)
                .getAsJsonObject().get("Type").getAsString());
        assertEquals(List.of(0, 936), point(line, 0));
        assertEquals(List.of(720, 936), point(line, 1));
        assertEquals(0, reader.readByte());
        assertEquals(1, reader.readShort());
        assertEquals(2, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesMixedBlockAndPlatformLineMapAndKeepsRuntimeBytesAligned() throws Exception {
        MapTemplate map = mixedLineMap();
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 123, 456);

        var reader = new MapPacketWriter()
                .mapInfo(player.zoneId(), player.x(), player.y(), map, true, List.of(), List.of())
                .reader();

        assertEquals(4, reader.readShort());
        assertEquals(130, reader.readShort());
        assertEquals("mixed-line", reader.readUtf());
        assertEquals(20, reader.readShort());
        assertEquals(62, reader.readShort());
        assertEquals("", reader.readUtf());
        assertEquals(51, reader.readShort());
        assertEquals(52, reader.readShort());
        assertEquals(53, reader.readShort());
        for (int value : List.of(128, 213, 242, 141, 185, 128,
                90, 154, 64, 69, 153, 51)) {
            assertEquals(value, reader.readShort());
        }
        assertTrue(reader.readBoolean());

        JsonObject line = JsonParser.parseString(reader.readUtf()).getAsJsonObject();
        assertEquals(4464, line.get("MapWidth").getAsInt());
        assertEquals(1440, line.get("MapHeight").getAsInt());
        assertEquals(2, line.getAsJsonArray("Lines").size());
        assertEquals("BLOCK", line.getAsJsonArray("Lines").get(0)
                .getAsJsonObject().get("Type").getAsString());
        assertEquals(List.of(0, 936), point(line, 0));
        assertEquals("PLATFORM", line.getAsJsonArray("Lines").get(1)
                .getAsJsonObject().get("Type").getAsString());
        assertEquals(List.of(1200, 720), point(line, 1, 0));

        assertEquals(0, reader.readByte());
        assertEquals(123, reader.readShort());
        assertEquals(456, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void cachedMapInfoOmitsTemplateButKeepsRuntimeLayout() throws Exception {
        MapTemplate map = simpleMap(List.of(
                new Waypoint(1, 2, 100, 200, 300, 400, 0)));
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 2, 123, 456);

        Message message = new MapPacketWriter().mapInfo(player.zoneId(), player.x(), player.y(), map, false, List.of("next"), List.of());

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
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesBackgroundImageSentinelAndKeepsPacketAligned() throws Exception {
        MapTemplate map = simpleMap(List.of());
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 1, 2);

        var reader = new MapPacketWriter()
                .mapInfo(player.zoneId(), player.x(), player.y(), map, true, List.of(), List.of())
                .reader();

        assertEquals(4, reader.readShort());
        assertEquals(5, reader.readShort());
        assertEquals("simple", reader.readUtf());
        assertEquals(1, reader.readShort());
        assertEquals(1, reader.readShort());
        assertEquals("0", reader.readUtf());
        assertEquals(-1, reader.readShort());
        assertEquals(-1, reader.readShort());
        assertEquals(-1, reader.readShort());
        for (int index = 0; index < 12; index++) {
            assertEquals(0, reader.readShort());
        }
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.readByte());
        assertEquals(1, reader.readShort());
        assertEquals(2, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsBackgroundImageOutsideSentinelRange() {
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 1, 2);
        MapPacketWriter writer = new MapPacketWriter();

        assertThrows(IOException.class, () -> writer.mapInfo(player.zoneId(), player.x(), player.y(), simpleMapWithImages(List.of(), -2, -1, -1), true, List.of(), List.of()));
        assertThrows(IOException.class, () -> writer.mapInfo(player.zoneId(), player.x(), player.y(), simpleMapWithImages(List.of(), 32768, -1, -1), true, List.of(), List.of()));
    }

    @Test
    void rejectsWaypointNameCountMismatch() {
        MapTemplate map = simpleMap(List.of(
                new Waypoint(1, 2, 10, 20, 30, 40, 0)));
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 1, 2);

        assertThrows(IllegalArgumentException.class, () ->
                new MapPacketWriter().mapInfo(player.zoneId(), player.x(), player.y(), map, false, List.of(), List.of()));
    }

    @Test
    void rejectsTooManyWaypointsAndMonsters() {
        List<Waypoint> waypoints = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            waypoints.add(new Waypoint(index, 2, 10, 20, 30, 40, 0));
        }
        MapTemplate map = simpleMap(waypoints);
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        player.changeMap(4, 0, 1, 2);
        List<String> names = waypoints.stream().map(ignored -> "target").toList();
        List<MonsterSnapshot> monsters = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            monsters.add(new MonsterSnapshot(0, 1, index, 1, 0,
                    1, 2, 3L, 2L, 0));
        }

        assertThrows(IOException.class, () ->
                new MapPacketWriter().mapInfo(player.zoneId(), player.x(), player.y(), map, false, names, List.of()));
        MapTemplate smallMap = simpleMap(List.of());
        assertThrows(IOException.class, () ->
                new MapPacketWriter().mapInfo(player.zoneId(), player.x(), player.y(), smallMap, false, List.of(), monsters));
    }

    private static MapTemplate map0() {
        return new MapTemplate(
                0, "Núi Paozu", "ONLINE", "EARTH", 1, 3, 40, 1,
                MapDataLoader.load(MAP_ROOT, 1),
                List.of(new Waypoint(2, 1, 4464, 936, 90, 1008, 1)));
    }

    private static MapTemplate lineMap() {
        MapData.Background background = new MapData.Background(
                List.of(128, 213, 242),
                List.of(
                        new MapData.Layer(51, List.of(141, 185, 128)),
                        new MapData.Layer(52, List.of(90, 154, 64)),
                        new MapData.Layer(53, List.of(69, 153, 51))));
        MapData.Line block = new MapData.Line(MapData.LineType.BLOCK, List.of(
                new MapData.Point(0, 936), new MapData.Point(720, 936),
                new MapData.Point(720, 1152), new MapData.Point(4464, 1152),
                new MapData.Point(4464, 1440), new MapData.Point(0, 1440),
                new MapData.Point(0, 936)));
        MapData data = new MapData(9, 130, 20, 62, background,
                new MapData.Collision(MapData.CollisionType.LINE, null, List.of(block)));
        return new MapTemplate(4, "line", "ONLINE", "EARTH", 1, 1, 1, 9, data, List.of());
    }

    private static MapTemplate mixedLineMap() {
        MapData.Background background = new MapData.Background(
                List.of(128, 213, 242),
                List.of(
                        new MapData.Layer(51, List.of(141, 185, 128)),
                        new MapData.Layer(52, List.of(90, 154, 64)),
                        new MapData.Layer(53, List.of(69, 153, 51))));
        MapData.Line block = new MapData.Line(MapData.LineType.BLOCK, List.of(
                new MapData.Point(0, 936), new MapData.Point(720, 936),
                new MapData.Point(720, 1152), new MapData.Point(4464, 1152),
                new MapData.Point(4464, 1440), new MapData.Point(0, 1440),
                new MapData.Point(0, 936)));
        MapData.Line platform = new MapData.Line(MapData.LineType.PLATFORM, List.of(
                new MapData.Point(1200, 720), new MapData.Point(1800, 720)));
        MapData data = new MapData(9, 130, 20, 62, background,
                new MapData.Collision(MapData.CollisionType.LINE, null, List.of(block, platform)));
        return new MapTemplate(4, "mixed-line", "ONLINE", "EARTH", 1, 1, 1, 9, data, List.of());
    }

    private static MapTemplate simpleMap(List<Waypoint> waypoints) {
        return simpleMapWithImages(waypoints, -1, -1, -1);
    }

    private static MapTemplate simpleMapWithImages(
            List<Waypoint> waypoints, int firstImage, int secondImage, int thirdImage) {
        MapData data = new MapData(7, 5, 1, 1,
                new MapData.Background(
                        List.of(0, 0, 0),
                        List.of(new MapData.Layer(firstImage, List.of(0, 0, 0)),
                                new MapData.Layer(secondImage, List.of(0, 0, 0)),
                                new MapData.Layer(thirdImage, List.of(0, 0, 0)))),
                new MapData.Collision(MapData.CollisionType.GRID, "0", List.of()));
        return new MapTemplate(4, "simple", "ONLINE", "EARTH", 1, 1, 1, 7, data, waypoints);
    }

    private static List<Integer> point(JsonObject line, int index) {
        return point(line, 0, index);
    }

    private static List<Integer> point(JsonObject line, int lineIndex, int pointIndex) {
        var point = line.getAsJsonArray("Lines").get(lineIndex).getAsJsonObject()
                .getAsJsonArray("Points").get(pointIndex).getAsJsonArray();
        return List.of(point.get(0).getAsInt(), point.get(1).getAsInt());
    }

    private static String sha256(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }
}

package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapLoaderTest {
    @Test
    void loadsExactMapZeroBootstrap() throws Exception {
        var maps = MapLoader.load(Path.of("resources", "json"), true);

        var map = maps.get(0);
        var map1 = maps.get(1);

        assertEquals(0, map.id());
        assertEquals(0, map.iconId());
        assertEquals("Núi Paozu", map.name());
        assertEquals(20, map.row());
        assertEquals(62, map.column());
        assertEquals(1240, map.data().length());
        assertTrue(map.data().chars().allMatch(ch -> ch == '0' || ch == '1'));
        assertEquals(List.of(51, 52, 53), map.imagesBgr());
        assertEquals(List.of(
                List.of(128, 213, 242),
                List.of(141, 185, 128),
                List.of(90, 154, 64),
                List.of(69, 153, 51)
        ), map.colorsBgr());
        assertFalse(map.line());
        assertNull(map.dataLine());

        assertEquals(1, map1.id());
        assertEquals(1, map1.iconId());
        assertEquals("Bờ sông Pu", map1.name());
        assertEquals(20, map1.row());
        assertEquals(62, map1.column());
        assertEquals(1240, map1.data().length());
        assertEquals("12ab5df64139502d93e61d4049bae5a5ed0639a0bb70623312b082f449ce7365",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(map1.data().getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(51, 52, 53), map1.imagesBgr());
        assertEquals(map.colorsBgr(), map1.colorsBgr());
        assertFalse(map1.line());
        assertNull(map1.dataLine());

        assertEquals(1, map.waypoints().size());
        var toMap1 = map.waypoints().getFirst();
        assertEquals(2, toMap1.id());
        assertEquals(1, toMap1.goMap());
        assertEquals(4464, toMap1.x());
        assertEquals(936, toMap1.y());
        assertEquals(90, toMap1.goX());
        assertEquals(1008, toMap1.goY());
        assertEquals(1, toMap1.type());
        assertTrue(toMap1.contains(4414, 736));
        assertTrue(toMap1.contains(4464, 936));
        assertTrue(toMap1.contains(4440, 900));
        assertFalse(toMap1.contains(4413, 900));
        assertFalse(toMap1.contains(4465, 900));
        assertFalse(toMap1.contains(4440, 735));
        assertFalse(toMap1.contains(4440, 937));

        assertEquals(1, map1.waypoints().size());
        var toMap0 = map1.waypoints().getFirst();
        assertEquals(3, toMap0.id());
        assertEquals(0, toMap0.goMap());
        assertEquals(0, toMap0.x());
        assertEquals(1008, toMap0.y());
        assertEquals(4374, toMap0.goX());
        assertEquals(936, toMap0.goY());
        assertEquals(0, toMap0.type());
        assertTrue(toMap0.contains(0, 808));
        assertTrue(toMap0.contains(50, 1008));
        assertTrue(toMap0.contains(20, 950));
        assertFalse(toMap0.contains(-1, 950));
        assertFalse(toMap0.contains(51, 950));
        assertFalse(toMap0.contains(20, 807));
        assertFalse(toMap0.contains(20, 1009));
    }

    @Test
    void pinsLegacyMapZeroGridHash() throws Exception {
        var map = MapLoader.load(Path.of("resources", "json"), true).get(0);

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(map.data().getBytes(StandardCharsets.UTF_8)));

        assertEquals("9d27d23a843599772be153cc4c94ca4b19d30403c66cb07457afb2df467a1229", hash);
    }

    @Test
    void rejectsMapZeroGridDataLengthMismatch(@TempDir Path root) throws IOException {
        var bootstrap = JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "MapBootstrap.json")))
                .getAsJsonObject();
        var map0 = bootstrap.getAsJsonObject("0");
        map0.addProperty("row", 2);
        map0.addProperty("column", 2);
        map0.addProperty("data", "010");
        Files.writeString(root.resolve("MapBootstrap.json"),
                new GsonBuilder().serializeNulls().create().toJson(bootstrap));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> MapLoader.load(root, true));
        assertTrue(failure.getMessage().contains("grid"), failure.getMessage());
        assertTrue(failure.getMessage().contains("data length"), failure.getMessage());
    }

    @Test
    void pinsCanonicalMapBootstrapHash() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("resources", "json", "MapBootstrap.json"));
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        assertEquals("6298f902bb2797f63c539acbe6e51c176847954cdbb7a1c5cc3e017cd9530df3", hash);
    }

    @Test
    void rejectsMapBootstrapMissingMap1(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        bootstrap.remove("1");

        assertMapBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMapBootstrapExtraMap(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        bootstrap.add("2", bootstrap.getAsJsonObject("1").deepCopy());

        assertMapBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsWaypointTargetOutsideLoadedMaps(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        bootstrap.getAsJsonObject("0").getAsJsonArray("waypoints")
                .get(0).getAsJsonObject().addProperty("goMap", 9);

        assertMapBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsUnsupportedWaypointType(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        bootstrap.getAsJsonObject("0").getAsJsonArray("waypoints")
                .get(0).getAsJsonObject().addProperty("type", 3);

        assertMapBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMap1DataLengthMismatch(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        bootstrap.getAsJsonObject("1").addProperty("data", "0");

        assertMapBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsDuplicateWaypointId(@TempDir Path root) throws IOException {
        var bootstrap = productionMapBootstrap();
        var waypoints = bootstrap.getAsJsonObject("0").getAsJsonArray("waypoints");
        waypoints.add(waypoints.get(0).deepCopy());

        assertMapBootstrapRejected(root, bootstrap);
    }

    private static com.google.gson.JsonObject productionMapBootstrap() throws IOException {
        return JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "MapBootstrap.json")))
                .getAsJsonObject();
    }

    private static void assertMapBootstrapRejected(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.writeString(root.resolve("MapBootstrap.json"),
                new Gson().toJson(bootstrap));
        assertThrows(IllegalArgumentException.class,
                () -> MapLoader.load(root, true));
    }
}

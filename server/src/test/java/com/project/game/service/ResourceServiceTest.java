package com.project.game.service;

import com.project.game.frame.FrameTemplate;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceServiceTest {
    @Test
    void loadsOnlyNumericPngFilesBelowConfiguredRoot(@TempDir Path root) throws IOException {
        byte[] expected = new byte[]{1, 2, 3, 4};
        Files.write(root.resolve("5.png"), expected);
        ResourceService resources = ResourceService.fromIconRoot(root);

        assertArrayEquals(expected, resources.loadIcon(5).orElseThrow());
        assertTrue(resources.loadIcon(6).isEmpty());
    }

    @Test
    void absentRootReportsMissingWithoutFabricatingBytes(@TempDir Path root) {
        ResourceService resources = ResourceService.fromIconRoot(root.resolve("does-not-exist"));

        assertTrue(resources.loadIcon(5).isEmpty());
    }

    @Test
    void unavailableServiceReportsMissing() {
        assertTrue(ResourceService.unavailable().loadIcon(5).isEmpty());
    }

    @Test
    void loadsRequiredFramesFromCanonicalClientResource(@TempDir Path ignored) {
        ResourceService resources = ResourceService.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));

        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                resources.frames().stream().map(FrameTemplate::id).toList());

        assertFrame(resources.frames().get(0), 3, 0, 8779, 8780, 7645, 8741, 8743,
                8750, 8749, 8751, 8763, 8752);
        assertFrame(resources.frames().get(1), 4, 0, 8339, 8340, 7641, 8301, 8303,
                8310, 8309, 8311, 8323, 8312);
        assertFrame(resources.frames().get(2), 5, 0, 8379, 8380, 7643, 8341, 8343,
                8350, 8349, 8351, 8363, 8352);
        assertFrame(resources.frames().get(3), 6, 1, -1, -1, -1, 7159, 7161,
                7168, 7167, 7169, 7181, 7170);
        assertFrame(resources.frames().get(4), 7, 1, -1, -1, -1, 8111, 8113,
                8120, 8119, 8121, 8133, 8122);
        assertFrame(resources.frames().get(5), 8, 1, -1, -1, -1, 7921, 7923,
                7930, 7929, 7931, 7943, 7932);
        assertFrame(resources.frames().get(6), 21, 1, -1, -1, -1, 10580, 10582,
                10589, 10588, 10590, 10602, 10591);
        assertFrame(resources.frames().get(7), 22, 1, -1, -1, -1, 10542, 10544,
                10551, 10550, 10552, 10564, 10553);
        assertFrame(resources.frames().get(8), 23, 1, -1, -1, -1, 10504, 10506,
                10513, 10512, 10514, 10526, 10515);
    }

    @Test
    void ignoresUnknownJsonNullValuesWhenLoadingFrames(@TempDir Path root) throws IOException {
        String frame = "{\"type\":0,\"hp_bar\":1,\"chat\":2,\"dead\":[3,4],"
                + "\"stand\":[5,6],\"run\":[7,8,9,10,11,12],\"fly\":13,"
                + "\"jump\":14,\"fall\":15,\"injure\":16,\"action\":{\"11\":17},"
                + "\"dx\":0,\"dy\":0,\"width\":66,\"height\":90,\"metadata\":null}";
        String json = "{\"3\":" + frame + ",\"4\":" + frame + ",\"5\":" + frame
                + ",\"6\":" + frame + ",\"7\":" + frame + ",\"8\":" + frame
                + ",\"21\":" + frame + ",\"22\":" + frame + ",\"23\":" + frame + "}";
        Files.writeString(root.resolve("Frame.json"), json);

        ResourceService resources = ResourceService.fromFrameRoot(root);

        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                resources.frames().stream().map(FrameTemplate::id).toList());
    }

    @Test
    void loadsExactFreshPlayerSkillIdsByGender() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));

        assertEquals(List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36),
                resources.playerSkills(0).stream().map(ResourceService.LegacyPlayerSkill::id).toList());
        assertEquals(List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36),
                resources.playerSkills(1).stream().map(ResourceService.LegacyPlayerSkill::id).toList());
        assertEquals(List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36),
                resources.playerSkills(2).stream().map(ResourceService.LegacyPlayerSkill::id).toList());

        for (int gender = 0; gender < 3; gender++) {
            List<ResourceService.LegacyPlayerSkill> skills = resources.playerSkills(gender);
            assertEquals(1, skills.get(0).level());
            assertTrue(skills.stream().skip(1).allMatch(skill -> skill.level() == 0));
            assertTrue(skills.stream().allMatch(skill -> skill.upgrade() == 0
                    && skill.point() == 0 && skill.cooldownReduction() == 0));
        }

        ResourceService.LegacyPlayerSkill earth = resources.playerSkills(0).get(0);
        ResourceService.LegacyPlayerSkill namek = resources.playerSkills(1).get(0);
        ResourceService.LegacyPlayerSkill saiyan = resources.playerSkills(2).get(0);
        assertEquals(List.of(1879, 1885), earth.icons());
        assertEquals(List.of(1879, 1883), namek.icons());
        assertEquals(List.of(1879, 1880), saiyan.icons());
        assertEquals(1, earth.levelRequire());
        assertEquals(7, earth.maxLevel());
        assertEquals(7, earth.maxUpgrade());
        assertEquals(List.of(0, 30_000, 35_000, 40_000, 45_000, 50_000, 55_000),
                earth.pointUpgrade());
        assertEquals(List.of("50.0", "100.0"),
                earth.paints().stream().map(ResourceService.LegacySkillPaint::percent).toList());
        ResourceService.LegacyPlayerSkill teleport = resources.playerSkills(0).stream()
                .filter(skill -> skill.id() == 31)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("10.0", "20.0", "30.0"),
                teleport.paints().stream().map(ResourceService.LegacySkillPaint::percent).toList());
    }

    @Test
    void loadsExactMapZeroBootstrap() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));

        var map = resources.map(0).orElseThrow();
        var map1 = resources.map(1).orElseThrow();

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
    void loadsExactLegacyLevels() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        var levels = resources.levels();

        assertEquals(102, levels.size());
        assertEquals(new ResourceService.LegacyLevel(0, "Tân binh", 0L), levels.get(0));
        assertEquals(new ResourceService.LegacyLevel(1, "Tân binh", 1L), levels.get(1));
        assertEquals(new ResourceService.LegacyLevel(2, "Tân binh", 100L), levels.get(2));
        assertEquals(new ResourceService.LegacyLevel(
                101, "Thần # cấp 5", 6_000_000_000_000_000L), levels.get(101));

        for (int id = 0; id < levels.size(); id++) {
            assertEquals(id, levels.get(id).id());
            if (id > 0) {
                assertTrue(levels.get(id).power() > levels.get(id - 1).power());
            }
        }

        assertEquals("9023e7e2e3a74c1ebdf97586b66f59c602d63c0a7daff421e5dc254d8f6526fb",
                levelTableSha256(levels));
    }

    @Test
    void loadsCanonicalMovementEffects() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));

        var effects = resources.effects();

        assertEquals(List.of(6, 7),
                effects.stream().map(ResourceService.LegacyEffectImage::id).toList());
        assertEquals(2, effects.size());
        assertTrue(effects.stream().allMatch(effect -> !effect.icons().isEmpty()));
        assertTrue(effects.stream().allMatch(effect -> effect.icons().size() <= Byte.MAX_VALUE));

        assertEquals(new ResourceService.LegacyEffectImage(6, 0, 0, 100, List.of(71, 72)),
                effects.get(0));
        assertEquals(new ResourceService.LegacyEffectImage(7, 0, 0, 100, List.of(68, 69, 70)),
                effects.get(1));
        for (var effect : effects) {
            assertTrue(effect.id() >= Short.MIN_VALUE && effect.id() <= Short.MAX_VALUE);
            assertTrue(effect.dx() >= Short.MIN_VALUE && effect.dx() <= Short.MAX_VALUE);
            assertTrue(effect.dy() >= Short.MIN_VALUE && effect.dy() <= Short.MAX_VALUE);
            assertTrue(effect.delay() >= Short.MIN_VALUE && effect.delay() <= Short.MAX_VALUE);
            assertTrue(effect.icons().stream()
                    .allMatch(id -> id >= Short.MIN_VALUE && id <= Short.MAX_VALUE));
        }
    }

    @Test
    void pinsCanonicalMovementEffectBootstrapHash() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("resources", "json", "EffectBootstrap.json"));
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        assertEquals("0eedf588888a3461c370fd5dcaada81e29dc16dd21aa0b2bc444712026b21091", hash);
    }

    @Test
    void pinsLegacyMapZeroGridHash() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        var map = resources.map(0).orElseThrow();

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(map.data().getBytes(StandardCharsets.UTF_8)));

        assertEquals("9d27d23a843599772be153cc4c94ca4b19d30403c66cb07457afb2df467a1229", hash);
    }

    @Test
    void rejectsMapZeroGridDataLengthMismatch(@TempDir Path root) throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));
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
                () -> ResourceService.fromFrameRoot(root));
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

    @Test
    void rejectsNonIncreasingInitialPaintThresholds(@TempDir Path root) throws IOException {
        Path resourceRoot = Path.of("resources", "json");
        Files.copy(resourceRoot.resolve("Frame.json"), root.resolve("Frame.json"));
        var bootstrap = JsonParser.parseString(
                Files.readString(resourceRoot.resolve("PlayerSkillBootstrap.json")))
                .getAsJsonObject();
        bootstrap.getAsJsonObject("templates")
                .getAsJsonObject("0")
                .getAsJsonArray("initialPaints")
                .get(1)
                .getAsJsonObject()
                .addProperty("percent", "50.0");
        Files.writeString(root.resolve("PlayerSkillBootstrap.json"), new Gson().toJson(bootstrap));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ResourceService.fromFrameRoot(root));
        assertTrue(failure.getMessage().contains("initialPaints"));
    }

    @Test
    void rejectsNonIncreasingLegacyLevelPower(@TempDir Path root) throws IOException {
        Path resourceRoot = Path.of("resources", "json");
        Files.copy(resourceRoot.resolve("Frame.json"), root.resolve("Frame.json"));
        var bootstrap = JsonParser.parseString(
                Files.readString(resourceRoot.resolve("LevelBootstrap.json")))
                .getAsJsonObject();
        bootstrap.getAsJsonArray("levels")
                .get(2)
                .getAsJsonObject()
                .addProperty("power", 1);
        Files.writeString(root.resolve("LevelBootstrap.json"), new Gson().toJson(bootstrap));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ResourceService.fromFrameRoot(root));
        assertTrue(failure.getMessage().contains("LevelBootstrap"));
        assertTrue(failure.getMessage().contains("increasing"));
    }

    private static String levelTableSha256(List<ResourceService.LegacyLevel> levels)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder canonical = new StringBuilder();
        for (var level : levels) {
            canonical.append(level.id())
                    .append('|')
                    .append(level.name())
                    .append('|')
                    .append(level.power())
                    .append('\n');
        }
        return HexFormat.of().formatHex(
                digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static com.google.gson.JsonObject productionMapBootstrap() throws IOException {
        return JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "MapBootstrap.json")))
                .getAsJsonObject();
    }

    private static void assertMapBootstrapRejected(Path root,
                                                    com.google.gson.JsonObject bootstrap)
            throws IOException {
        Files.copy(Path.of("resources", "json", "Frame.json"), root.resolve("Frame.json"));
        Files.writeString(root.resolve("MapBootstrap.json"),
                new GsonBuilder().serializeNulls().create().toJson(bootstrap));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceService.fromFrameRoot(root));
    }

    private static void assertFrame(FrameTemplate frame, int id, int type, int hpBar, int chat,
                                    int deadStart, int standStart, int runStart, int fly,
                                    int jump, int fall, int injure, int actionStart) {
        assertEquals(id, frame.id());
        assertEquals(type, frame.type());
        assertEquals(hpBar, frame.hpBar());
        assertEquals(chat, frame.chat());
        assertEquals(deadStart < 0 ? List.of(-1, -1) : List.of(deadStart, deadStart + 1), frame.dead());
        assertEquals(List.of(standStart, standStart + 1), frame.stand());
        assertEquals(range(runStart, 6), frame.run());
        assertEquals(fly, frame.fly());
        assertEquals(jump, frame.jump());
        assertEquals(fall, frame.fall());
        assertEquals(injure, frame.injure());
        assertEquals(actionRange(actionStart), frame.action());
        assertEquals(0, frame.dx());
        assertEquals(0, frame.dy());
        assertEquals(66, frame.width());
        assertEquals(90, frame.height());
    }

    private static List<Integer> range(int start, int count) {
        List<Integer> values = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            values.add(start + offset);
        }
        return values;
    }

    private static Map<Integer, Integer> actionRange(int firstIcon) {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (int actionId = 11; actionId <= 37; actionId++) {
            values.put(actionId, firstIcon + actionId - 11);
        }
        return values;
    }
}

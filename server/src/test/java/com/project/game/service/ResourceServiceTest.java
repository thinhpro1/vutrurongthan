package com.project.game.service;

import com.project.game.frame.FrameTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

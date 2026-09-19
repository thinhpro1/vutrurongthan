package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.project.game.resource.LegacyPlayerSkill;
import com.project.game.resource.LegacySkillPaint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkillLoaderTest {
    @Test
    void loadsExactFreshPlayerSkillIdsByGender() {
        var skills = PlayerSkillLoader.load(Path.of("resources", "json"), true);

        assertEquals(List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36),
                skills.get(0).stream().map(LegacyPlayerSkill::id).toList());
        assertEquals(List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36),
                skills.get(1).stream().map(LegacyPlayerSkill::id).toList());
        assertEquals(List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36),
                skills.get(2).stream().map(LegacyPlayerSkill::id).toList());

        for (int gender = 0; gender < 3; gender++) {
            List<LegacyPlayerSkill> genderSkills = skills.get(gender);
            assertEquals(1, genderSkills.get(0).level());
            assertTrue(genderSkills.stream().skip(1).allMatch(skill -> skill.level() == 0));
            assertTrue(genderSkills.stream().allMatch(skill -> skill.upgrade() == 0
                    && skill.point() == 0 && skill.cooldownReduction() == 0));
        }

        LegacyPlayerSkill earth = skills.get(0).get(0);
        LegacyPlayerSkill namek = skills.get(1).get(0);
        LegacyPlayerSkill saiyan = skills.get(2).get(0);
        assertEquals(List.of(1879, 1885), earth.icons());
        assertEquals(List.of(1879, 1883), namek.icons());
        assertEquals(List.of(1879, 1880), saiyan.icons());
        assertEquals(1, earth.levelRequire());
        assertEquals(7, earth.maxLevel());
        assertEquals(7, earth.maxUpgrade());
        assertEquals(List.of(0, 30_000, 35_000, 40_000, 45_000, 50_000, 55_000),
                earth.pointUpgrade());
        assertEquals(List.of("50.0", "100.0"),
                earth.paints().stream().map(LegacySkillPaint::percent).toList());
        LegacyPlayerSkill teleport = skills.get(0).stream()
                .filter(skill -> skill.id() == 31)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("10.0", "20.0", "30.0"),
                teleport.paints().stream().map(LegacySkillPaint::percent).toList());
    }

    @Test
    void rejectsNonIncreasingInitialPaintThresholds(@TempDir Path root) throws IOException {
        Path resourceRoot = Path.of("resources", "json");
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
                () -> PlayerSkillLoader.load(root, true));
        assertTrue(failure.getMessage().contains("initialPaints"));
    }
}

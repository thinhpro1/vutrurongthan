package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.project.game.resource.LevelTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelLoaderTest {
    @Test
    void loadsExactLevelTemplates() throws Exception {
        var levels = LevelLoader.load(Path.of("resources", "json"), true);

        assertEquals(102, levels.size());
        assertEquals(new LevelTemplate(0, "Tân binh", 0L), levels.get(0));
        assertEquals(new LevelTemplate(1, "Tân binh", 1L), levels.get(1));
        assertEquals(new LevelTemplate(2, "Tân binh", 100L), levels.get(2));
        assertEquals(new LevelTemplate(
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
    void rejectsNonIncreasingLevelTemplatePower(@TempDir Path root) throws IOException {
        var bootstrap = JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "LevelBootstrap.json")))
                .getAsJsonObject();
        bootstrap.getAsJsonArray("levels")
                .get(2)
                .getAsJsonObject()
                .addProperty("power", 1);
        Files.writeString(root.resolve("LevelBootstrap.json"), new Gson().toJson(bootstrap));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LevelLoader.load(root, true));
        assertTrue(failure.getMessage().contains("LevelBootstrap"));
        assertTrue(failure.getMessage().contains("increasing"));
    }

    private static String levelTableSha256(java.util.List<LevelTemplate> levels)
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
}

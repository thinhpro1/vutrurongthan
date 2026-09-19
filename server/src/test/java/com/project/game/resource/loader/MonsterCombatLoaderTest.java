package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonsterCombatLoaderTest {
    @Test
    void loadsCanonicalMonsterCombatTemplate() {
        var templates = MonsterCombatLoader.load(Path.of("resources", "json"), true);

        assertEquals(10L, templates.get(1).damage());
        assertEquals(10L, templates.get(1).potentialReward());
    }

    @Test
    void rejectsMonsterCombatBootstrapVersionTwo(@TempDir Path root) throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.addProperty("version", 2);
        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterCombatBootstrapMissingTemplateOne(@TempDir Path root) throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.getAsJsonArray("templates").remove(0);
        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterCombatBootstrapDuplicateTemplate(@TempDir Path root) throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.getAsJsonArray("templates")
                .add(bootstrap.getAsJsonArray("templates").get(0).deepCopy());
        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterCombatBootstrapNonPositiveDamage(@TempDir Path root) throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty("damage", 0);
        assertMonsterCombatBootstrapRejected(root, bootstrap);

        bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty("damage", -1);
        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterCombatBootstrapUnexpectedTemplate(@TempDir Path root) throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.getAsJsonArray("templates").add(
                JsonParser.parseString("{\"templateId\":2,\"damage\":10}"));
        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    @Test
    void monsterCombatBootstrapWithoutPotentialRewardDefaultsToZero(@TempDir Path root)
            throws IOException {
        Files.writeString(root.resolve("MonsterCombatBootstrap.json"),
                new Gson().toJson(canonicalMonsterCombatBootstrap()));

        var templates = MonsterCombatLoader.load(root, true);

        assertEquals(10L, templates.get(1).damage());
        assertEquals(0L, templates.get(1).potentialReward());
    }

    @Test
    void rejectsMonsterCombatBootstrapNegativePotentialReward(@TempDir Path root)
            throws IOException {
        var bootstrap = canonicalMonsterCombatBootstrap();
        bootstrap.getAsJsonArray("templates")
                .get(0)
                .getAsJsonObject()
                .addProperty("potentialReward", -1L);

        assertMonsterCombatBootstrapRejected(root, bootstrap);
    }

    private static com.google.gson.JsonObject canonicalMonsterCombatBootstrap() {
        return JsonParser.parseString("{\"version\":1,\"templates\":["
                + "{\"templateId\":1,\"damage\":10}]}").getAsJsonObject();
    }

    private static void assertMonsterCombatBootstrapRejected(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.writeString(root.resolve("MonsterCombatBootstrap.json"),
                new Gson().toJson(bootstrap));
        assertThrows(IllegalArgumentException.class,
                () -> MonsterCombatLoader.load(root, true));
    }
}

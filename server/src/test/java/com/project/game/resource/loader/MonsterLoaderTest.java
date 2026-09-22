package com.project.game.resource.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterLoaderTest {
    @Test
    void loadsCanonicalStaticMap1MonsterBootstrap() throws Exception {
        var monsters = MonsterLoader.load(Path.of("resources", "json"), true, 2);

        assertEquals(2, monsters.version());
        assertEquals(6, monsters.darts().size());
        assertEquals(List.of(0, 1, 2, 3, 4, 5), monsters.darts().stream()
                .map(dart -> dart.id()).toList());
        assertEquals(List.of(false, true, false, false, false, true), monsters.darts().stream()
                .map(dart -> dart.meteorite()).toList());
        assertEquals(1, monsters.templates().size());
        assertTrue(monsters.spawns().get(0).isEmpty());
        assertEquals(6, monsters.spawns().get(1).size());

        var dart = monsters.darts().getFirst();
        assertEquals(0, dart.id());
        assertFalse(dart.meteorite());
        assertEquals(List.of(2198, 2199, 2200), dart.light().icons());
        assertEquals(0, dart.light().dx());
        assertEquals(0, dart.light().dy());
        assertEquals(30, dart.light().delay());
        assertEquals(List.of(2190, 2191, 2192), dart.bullet().icons());
        assertEquals(0, dart.bullet().dx());
        assertEquals(0, dart.bullet().dy());
        assertEquals(30, dart.bullet().delay());
        assertEquals(List.of(2193, 2194, 2195, 2196, 2197), dart.explode().icons());
        assertEquals(0, dart.explode().dx());
        assertEquals(0, dart.explode().dy());
        assertEquals(20, dart.explode().delay());

        var template = monsters.templates().getFirst();
        assertEquals(1, template.id());
        assertEquals("Hổ nanh kiếm", template.name());
        assertEquals(100, template.rangeMove());
        assertEquals(1, template.speed());
        assertEquals(1, template.type());
        assertEquals(0, template.dartId());
        assertEquals(List.of(11818, 11819, 11820, 11821, 11822), template.iconsMove());
        assertEquals(List.of(11824), template.iconsInjure());
        assertEquals(List.of(11823), template.iconsAttack());
        assertEquals(175, template.w());
        assertEquals(95, template.h());

        var map1 = monsters.spawns().get(1);
        assertEquals(List.of(0, 1, 2, 3, 4, 5), map1.stream()
                .map(spawn -> spawn.id()).toList());
        assertEquals(List.of(975, 1348, 1800, 2250, 2600, 2950), map1.stream()
                .map(spawn -> spawn.x()).toList());
        assertTrue(map1.stream().allMatch(spawn -> spawn.type() == 0
                && spawn.templateId() == 1
                && spawn.level() == 2
                && spawn.levelStatus() == 0
                && spawn.y() == 936
                && spawn.maxHp() == 300
                && spawn.hp() == 300
                && spawn.status() == 0));
    }

    @Test
    void rejectsReintroducedMonsterBootstrapVersionField(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.addProperty("version", 0);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsReintroducedMonsterTemplateLegacyFields(@TempDir Path root) throws IOException {
        for (String field : List.of("iconInjure", "iconAttack", "dx", "dy")) {
            var bootstrap = productionMonsterBootstrap();
            bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject()
                    .addProperty(field, 0);
            assertMonsterBootstrapRejected(root, bootstrap);
        }
    }

    @Test
    void rejectsInvalidMonsterAnimationArrays(@TempDir Path root) throws IOException {
        for (String field : List.of("iconsMove", "iconsInjure", "iconsAttack")) {
            assertTemplateAnimationRejected(root, field, null);
            assertTemplateAnimationRejected(root, field, new com.google.gson.JsonPrimitive(1));
            assertTemplateAnimationRejected(root, field, new JsonArray());
            JsonArray overflowing = new JsonArray();
            for (int index = 0; index < 128; index++) {
                overflowing.add(1);
            }
            assertTemplateAnimationRejected(root, field, overflowing);
            assertTemplateAnimationRejected(root, field, singleAnimationValue(-1));
            assertTemplateAnimationRejected(root, field, singleAnimationValue(32768));
            assertTemplateAnimationRejected(root, field, singleAnimationValue(1.5));
        }
    }

    @Test
    void rejectsNonCanonicalDecimalMonsterAnimationIntegers(@TempDir Path root) throws IOException {
        for (String field : List.of("iconsMove", "iconsInjure", "iconsAttack")) {
            var bootstrap = productionMonsterBootstrap();
            var icons = bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject()
                    .getAsJsonArray(field);
            String canonicalValue = icons.get(0).getAsString();
            icons.set(0, JsonParser.parseString("[" + canonicalValue + ".0]")
                    .getAsJsonArray().get(0));
            assertMonsterBootstrapRejected(root, bootstrap);
        }
    }

    @Test
    void rejectsMissingMonsterDartSource(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        writeMonsterBootstrap(root, bootstrap);
        assertThrows(IllegalArgumentException.class,
                () -> MonsterLoader.load(root, true, 2));
    }

    @Test
    void rejectsMonsterBootstrapDartsField(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.add("darts", new com.google.gson.JsonArray());
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapTemplateDartReference(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty("dartId", 6);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsTemplateDartReferenceMissingFromDartSource(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        var darts = productionMonsterDartTemplates();
        darts.remove("0");
        Files.writeString(root.resolve("MonsterDartTemplate.json"),
                new GsonBuilder().serializeNulls().create().toJson(darts));
        writeMonsterBootstrap(root, bootstrap);

        assertThrows(IllegalArgumentException.class,
                () -> MonsterLoader.load(root, true, 2));
    }

    @Test
    void optionalMissingMonsterBootstrapReturnsUnavailableFamily(@TempDir Path root) {
        var monsters = MonsterLoader.load(root, false, 2);

        assertEquals(-1, monsters.version());
        assertTrue(monsters.darts().isEmpty());
        assertTrue(monsters.templates().isEmpty());
        assertTrue(monsters.spawns().isEmpty());
    }

    @Test
    void rejectsMonsterBootstrapTemplateId(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty("id", 2);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapNonEmptyMapZero(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        var mapSpawns = bootstrap.getAsJsonObject("mapSpawns");
        mapSpawns.add("0", mapSpawns.getAsJsonArray("1").deepCopy());
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapOnlyFiveMap1Spawns(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonObject("mapSpawns").getAsJsonArray("1").remove(5);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapDuplicateRuntimeId(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonObject("mapSpawns").getAsJsonArray("1")
                .get(1).getAsJsonObject().addProperty("id", 0);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapUnknownTemplateReference(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonObject("mapSpawns").getAsJsonArray("1")
                .get(0).getAsJsonObject().addProperty("templateId", 2);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMonsterBootstrapHpMismatch(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.getAsJsonObject("mapSpawns").getAsJsonArray("1")
                .get(0).getAsJsonObject().addProperty("hp", 301);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    private static com.google.gson.JsonObject productionMonsterBootstrap() throws IOException {
        return JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "MonsterBootstrap.json")))
                .getAsJsonObject();
    }

    private static com.google.gson.JsonObject productionMonsterDartTemplates() throws IOException {
        return JsonParser.parseString(
                Files.readString(Path.of("resources", "json", "MonsterDartTemplate.json")))
                .getAsJsonObject();
    }

    private static void assertMonsterBootstrapRejected(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.copy(Path.of("resources", "json", "MonsterDartTemplate.json"),
                root.resolve("MonsterDartTemplate.json"), StandardCopyOption.REPLACE_EXISTING);
        writeMonsterBootstrap(root, bootstrap);
        assertThrows(IllegalArgumentException.class,
                () -> MonsterLoader.load(root, true, 2));
    }

    private static void assertTemplateAnimationRejected(
            Path root, String field, JsonElement replacement) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        var template = bootstrap.getAsJsonArray("templates").get(0).getAsJsonObject();
        if (replacement == null) {
            template.remove(field);
        } else {
            template.add(field, replacement);
        }
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    private static JsonArray singleAnimationValue(Number value) {
        JsonArray result = new JsonArray();
        result.add(value);
        return result;
    }

    private static void writeMonsterBootstrap(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.writeString(root.resolve("MonsterBootstrap.json"),
                new GsonBuilder().serializeNulls().create().toJson(bootstrap));
    }
}

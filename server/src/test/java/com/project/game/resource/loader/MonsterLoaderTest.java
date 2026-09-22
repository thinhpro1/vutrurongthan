package com.project.game.resource.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterLoaderTest {
    @Test
    void loadsCanonicalStaticMap1MonsterBootstrap() throws Exception {
        var monsters = MonsterLoader.load(Path.of("resources", "json"), true);

        assertEquals(1, monsters.version());
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
        assertEquals(11824, template.iconInjure());
        assertEquals(11823, template.iconAttack());
        assertEquals(175, template.w());
        assertEquals(95, template.h());
        assertEquals(0, template.dx());
        assertEquals(0, template.dy());

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
    void rejectsMonsterBootstrapVersionZero(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        bootstrap.addProperty("version", 0);
        assertMonsterBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsMissingMonsterDartSource(@TempDir Path root) throws IOException {
        var bootstrap = productionMonsterBootstrap();
        writeMonsterBootstrap(root, bootstrap);
        assertThrows(IllegalArgumentException.class,
                () -> MonsterLoader.load(root, true));
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
    void optionalMissingMonsterBootstrapReturnsUnavailableFamily(@TempDir Path root) {
        var monsters = MonsterLoader.load(root, false);

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

    private static void assertMonsterBootstrapRejected(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.copy(Path.of("resources", "json", "MonsterDartTemplate.json"),
                root.resolve("MonsterDartTemplate.json"));
        writeMonsterBootstrap(root, bootstrap);
        assertThrows(IllegalArgumentException.class,
                () -> MonsterLoader.load(root, true));
    }

    private static void writeMonsterBootstrap(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.writeString(root.resolve("MonsterBootstrap.json"),
                new GsonBuilder().serializeNulls().create().toJson(bootstrap));
    }
}

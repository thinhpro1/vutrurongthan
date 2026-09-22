package com.project.game.resource.loader;

import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterSpawn;
import com.project.game.persistence.monster.MonsterRepository;
import com.project.game.testsupport.MapTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterCatalogLoaderTest {
    private static final Path JSON_ROOT = Path.of("resources", "json");
    private static final Map<Integer, MapTemplate> MAPS = MapTestSupport.canonicalMaps();

    @Test
    void loadsDatabaseTemplatesAndSpawnsOnceInDeterministicImmutableCatalog() {
        AtomicInteger templateQueries = new AtomicInteger();
        AtomicInteger spawnQueries = new AtomicInteger();
        MonsterRepository repository = repository(
                List.of(template(2, 1, 1), template(1, 2, 0)),
                List.of(new MonsterRepository.SpawnRow(102, 1, 1, 1348, 936),
                        new MonsterRepository.SpawnRow(101, 1, 1, 975, 936)),
                templateQueries, spawnQueries);

        MonsterCatalogLoader.LoadedMonsters loaded = MonsterCatalogLoader.load(
                repository, JSON_ROOT, 2, MAPS);

        assertEquals(1, templateQueries.get());
        assertEquals(1, spawnQueries.get());
        assertEquals(2, loaded.version());
        assertEquals(List.of(1, 2), loaded.templates().stream()
                .map(template -> template.id()).toList());
        assertEquals(List.of(101, 102), loaded.spawns().get(1).stream()
                .map(MonsterSpawn::id).toList());
        assertEquals(List.of(2, 2), loaded.spawns().get(1).stream()
                .map(MonsterSpawn::level).toList());
        assertTrue(loaded.spawns().get(1).stream().allMatch(spawn ->
                spawn.type() == 0
                        && spawn.levelStatus() == 0
                        && spawn.maxHp() == 300L
                        && spawn.hp() == 300L
                        && spawn.status() == 0));
        assertEquals(List.of(11818, 11819, 11820, 11821, 11822),
                loaded.templates().getFirst().iconsMove());
        assertEquals(10L, loaded.templates().getFirst().damage());
        assertEquals(10L, loaded.templates().getFirst().potentialReward());
        assertThrows(UnsupportedOperationException.class,
                () -> loaded.templates().getFirst().iconsMove().add(99));
        assertThrows(UnsupportedOperationException.class,
                () -> loaded.spawns().get(1).add(new MonsterSpawn(0, 1, 103, 2,
                        0, 1, 1, 300, 300, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> loaded.spawns().put(0, List.of()));
    }

    @Test
    void allowsEmptySpawnTable() {
        MonsterCatalogLoader.LoadedMonsters loaded = MonsterCatalogLoader.load(
                repository(List.of(template(1, 2, 0)), List.of(),
                        new AtomicInteger(), new AtomicInteger()),
                JSON_ROOT, 2, MAPS);

        assertTrue(loaded.spawns().isEmpty());
    }

    @Test
    void acceptsInclusiveMapBounds() {
        MapTemplate map = MAPS.get(1);
        MonsterCatalogLoader.LoadedMonsters loaded = MonsterCatalogLoader.load(
                repository(List.of(template(1, 2, 0)),
                        List.of(new MonsterRepository.SpawnRow(
                                101, 1, 1, map.data().width(), map.data().height())),
                        new AtomicInteger(), new AtomicInteger()),
                JSON_ROOT, 2, MAPS);

        assertEquals(map.data().width(), loaded.spawns().get(1).getFirst().x());
        assertEquals(map.data().height(), loaded.spawns().get(1).getFirst().y());
    }

    @Test
    void rejectsDuplicateTemplateIds() {
        assertTemplateRejected(List.of(template(1, 2, 0), template(1, 2, 0)));
    }

    @Test
    void rejectsInvalidTemplateScalarFields() {
        assertTemplateRejected(template(-1, 2, 0));
        assertTemplateRejected(template(32768, 2, 0));
        assertTemplateRejected(row -> withName(row, null));
        assertTemplateRejected(row -> withName(row, ""));
        assertTemplateRejected(row -> withName(row, "x".repeat(51)));
        assertTemplateRejected(row -> withLevel(row, -1));
        assertTemplateRejected(row -> withLevel(row, 32768));
        assertTemplateRejected(row -> withHp(row, 0L));
        assertTemplateRejected(row -> withHp(row, -1L));
        assertTemplateRejected(row -> withDamage(row, 0L));
        assertTemplateRejected(row -> withDamage(row, -1L));
        assertTemplateRejected(row -> withReward(row, -1L));
        assertTemplateRejected(row -> withRangeMove(row, -1));
        assertTemplateRejected(row -> withRangeMove(row, 32768));
        assertTemplateRejected(row -> withSpeed(row, -1));
        assertTemplateRejected(row -> withSpeed(row, 128));
        assertTemplateRejected(row -> withTypeMove(row, -1));
        assertTemplateRejected(row -> withTypeMove(row, 3));
        assertTemplateRejected(row -> withDartId(row, -1));
        assertTemplateRejected(row -> withDartId(row, 128));
        assertTemplateRejected(row -> withDartId(row, 6));
        assertTemplateRejected(row -> withW(row, 0));
        assertTemplateRejected(row -> withW(row, 32768));
        assertTemplateRejected(row -> withH(row, 0));
        assertTemplateRejected(row -> withH(row, 32768));
    }

    @Test
    void rejectsInvalidAnimationColumnsForEveryAction() {
        for (String field : List.of("iconMove", "iconInjure", "iconAttack")) {
            assertTemplateRejected(row -> withAnimation(row, field, null));
            assertTemplateRejected(row -> withAnimation(row, field, ""));
            assertTemplateRejected(row -> withAnimation(row, field, "not-json"));
            assertTemplateRejected(row -> withAnimation(row, field, "{}"));
            assertTemplateRejected(row -> withAnimation(row, field, "1"));
            assertTemplateRejected(row -> withAnimation(row, field, "[]"));
            assertTemplateRejected(row -> withAnimation(row, field, overflowingAnimation()));
            assertTemplateRejected(row -> withAnimation(row, field, "[-1]"));
            assertTemplateRejected(row -> withAnimation(row, field, "[32768]"));
            assertTemplateRejected(row -> withAnimation(row, field, decimalAnimation(field)));
            assertTemplateRejected(row -> withAnimation(row, field, "[1.5]"));
        }
    }

    @Test
    void rejectsInvalidSpawnRows() {
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 1, 1, 1),
                new MonsterRepository.SpawnRow(101, 1, 1, 2, 2)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(0, 1, 1, 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, -1, 1, 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 32768, 1, 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 2, 1, 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 2, 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 1, -1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 1, 32768, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 1, 1, -1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(101, 1, 1, 1, 32768)));
        MapTemplate map = MAPS.get(1);
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(
                101, 1, 1, map.data().width() + 1, 1)));
        assertSpawnRejected(List.of(new MonsterRepository.SpawnRow(
                101, 1, 1, 1, map.data().height() + 1)));
    }

    private static String overflowingAnimation() {
        return IntStream.rangeClosed(1, 128)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String decimalAnimation(String field) {
        return switch (field) {
            case "iconMove" -> "[11818.0,11819,11820,11821,11822]";
            case "iconInjure" -> "[11824.0]";
            case "iconAttack" -> "[11823.0]";
            default -> throw new IllegalArgumentException(field);
        };
    }

    private static void assertTemplateRejected(UnaryOperator<MonsterRepository.TemplateRow> change) {
        assertTemplateRejected(List.of(change.apply(template(1, 2, 0))));
    }

    private static void assertTemplateRejected(MonsterRepository.TemplateRow row) {
        assertTemplateRejected(List.of(row));
    }

    private static void assertTemplateRejected(List<MonsterRepository.TemplateRow> rows) {
        assertThrows(IllegalArgumentException.class, () -> MonsterCatalogLoader.load(
                repository(rows, List.of(), new AtomicInteger(), new AtomicInteger()),
                JSON_ROOT, 2, MAPS));
    }

    private static void assertSpawnRejected(List<MonsterRepository.SpawnRow> rows) {
        assertThrows(IllegalArgumentException.class, () -> MonsterCatalogLoader.load(
                repository(List.of(template(1, 2, 0)), rows,
                        new AtomicInteger(), new AtomicInteger()),
                JSON_ROOT, 2, MAPS));
    }

    private static MonsterRepository.TemplateRow withName(
            MonsterRepository.TemplateRow row, String value) {
        return new MonsterRepository.TemplateRow(row.id(), value, row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withLevel(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), value, row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withHp(
            MonsterRepository.TemplateRow row, long value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), value, row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withDamage(
            MonsterRepository.TemplateRow row, long value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), value,
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withReward(
            MonsterRepository.TemplateRow row, long value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                value, row.rangeMove(), row.speed(), row.typeMove(), row.dartId(), row.iconMove(),
                row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withRangeMove(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), value, row.speed(), row.typeMove(), row.dartId(), row.iconMove(),
                row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withSpeed(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), value, row.typeMove(), row.dartId(), row.iconMove(),
                row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withTypeMove(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), value, row.dartId(), row.iconMove(),
                row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withDartId(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), value, row.iconMove(),
                row.iconAttack(), row.iconInjure(), row.w(), row.h());
    }

    private static MonsterRepository.TemplateRow withW(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), value, row.h());
    }

    private static MonsterRepository.TemplateRow withH(
            MonsterRepository.TemplateRow row, int value) {
        return new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(), row.hp(), row.damage(),
                row.potentialReward(), row.rangeMove(), row.speed(), row.typeMove(), row.dartId(),
                row.iconMove(), row.iconAttack(), row.iconInjure(), row.w(), value);
    }

    private static MonsterRepository.TemplateRow withAnimation(
            MonsterRepository.TemplateRow row, String field, String value) {
        return switch (field) {
            case "iconMove" -> new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(),
                    row.hp(), row.damage(), row.potentialReward(), row.rangeMove(), row.speed(),
                    row.typeMove(), row.dartId(), value, row.iconAttack(), row.iconInjure(),
                    row.w(), row.h());
            case "iconInjure" -> new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(),
                    row.hp(), row.damage(), row.potentialReward(), row.rangeMove(), row.speed(),
                    row.typeMove(), row.dartId(), row.iconMove(), row.iconAttack(), value,
                    row.w(), row.h());
            case "iconAttack" -> new MonsterRepository.TemplateRow(row.id(), row.name(), row.level(),
                    row.hp(), row.damage(), row.potentialReward(), row.rangeMove(), row.speed(),
                    row.typeMove(), row.dartId(), row.iconMove(), value, row.iconInjure(),
                    row.w(), row.h());
            default -> throw new IllegalArgumentException(field);
        };
    }

    private static MonsterRepository.TemplateRow template(int id, int level, int dartId) {
        return new MonsterRepository.TemplateRow(
                id, id == 1 ? "Hổ nanh kiếm" : "Quái vật phụ", level, 300L, 10L, 10L,
                100, 1, 1, dartId,
                "[11818,11819,11820,11821,11822]",
                "[11823]",
                "[11824]",
                175, 95);
    }

    private static MonsterRepository repository(
            List<MonsterRepository.TemplateRow> templates,
            List<MonsterRepository.SpawnRow> spawns,
            AtomicInteger templateQueries,
            AtomicInteger spawnQueries) {
        return new MonsterRepository() {
            @Override
            public List<TemplateRow> findAllTemplates() {
                templateQueries.incrementAndGet();
                return List.copyOf(templates);
            }

            @Override
            public List<SpawnRow> findAllSpawns() {
                spawnQueries.incrementAndGet();
                return List.copyOf(spawns);
            }
        };
    }
}

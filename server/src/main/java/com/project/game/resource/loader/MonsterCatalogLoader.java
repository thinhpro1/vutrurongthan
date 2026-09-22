package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.project.game.map.MapTemplate;
import com.project.game.monster.MonsterDart;
import com.project.game.monster.MonsterSpawn;
import com.project.game.monster.MonsterTemplate;
import com.project.game.persistence.monster.MonsterRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Composes the validated database monster catalog and static dart animations. */
final class MonsterCatalogLoader {
    private MonsterCatalogLoader() {
    }

    static LoadedMonsters load(
            MonsterRepository repository,
            Path jsonRoot,
            int monsterVersion,
            Map<Integer, MapTemplate> maps) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(jsonRoot, "jsonRoot");
        Objects.requireNonNull(maps, "maps");
        if (monsterVersion < 1 || monsterVersion > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "monster resource version must be between 1 and 127: " + monsterVersion);
        }

        List<MonsterRepository.TemplateRow> templateRows = requireRows(
                repository.findAllTemplates(), "monster template rows");
        List<MonsterRepository.SpawnRow> spawnRows = requireRows(
                repository.findAllSpawns(), "monster spawn rows");
        List<MonsterDart> darts = MonsterDartLoader.load(jsonRoot, true);
        Map<Integer, MonsterDart> dartsById = indexDarts(darts);

        Map<Integer, MonsterTemplate> templatesById = new TreeMap<>();
        for (MonsterRepository.TemplateRow row : templateRows) {
            MonsterTemplate template = readTemplate(row, dartsById);
            if (templatesById.put(template.id(), template) != null) {
                throw new IllegalArgumentException("duplicate monster template id: " + template.id());
            }
        }
        if (templatesById.isEmpty()) {
            throw new IllegalArgumentException("monster template catalog must not be empty");
        }

        Set<Integer> spawnIds = new HashSet<>();
        Map<Integer, List<MonsterSpawn>> spawnsByMap = new TreeMap<>();
        spawnRows.stream()
                .sorted(java.util.Comparator.comparingInt(MonsterRepository.SpawnRow::id))
                .forEach(row -> {
                    MonsterSpawn spawn = readSpawn(row, templatesById, maps, spawnIds);
                    spawnsByMap.computeIfAbsent(row.mapId(), ignored -> new ArrayList<>())
                            .add(spawn);
                });

        Map<Integer, List<MonsterSpawn>> immutableSpawns = new TreeMap<>();
        spawnsByMap.forEach((mapId, spawns) -> immutableSpawns.put(mapId, List.copyOf(spawns)));
        return new LoadedMonsters(
                monsterVersion,
                List.copyOf(darts),
                List.copyOf(templatesById.values()),
                Collections.unmodifiableMap(immutableSpawns));
    }

    private static Map<Integer, MonsterDart> indexDarts(List<MonsterDart> darts) {
        Map<Integer, MonsterDart> result = new HashMap<>();
        for (MonsterDart dart : darts) {
            if (result.put(dart.id(), dart) != null) {
                throw new IllegalArgumentException("duplicate monster dart id: " + dart.id());
            }
        }
        return Map.copyOf(result);
    }

    private static MonsterTemplate readTemplate(
            MonsterRepository.TemplateRow row,
            Map<Integer, MonsterDart> dartsById) {
        if (row == null) {
            throw new IllegalArgumentException("monster template row must not be null");
        }
        requireRange(row.id(), 0, Short.MAX_VALUE, "monster template id");
        if (row.name() == null || row.name().isBlank() || row.name().length() > 50) {
            throw new IllegalArgumentException(
                    "monster template name must be non-blank and at most 50 characters");
        }
        requireRange(row.level(), 0, Short.MAX_VALUE, "monster template level");
        if (row.hp() <= 0L) {
            throw new IllegalArgumentException("monster template hp must be positive");
        }
        if (row.damage() <= 0L) {
            throw new IllegalArgumentException("monster template damage must be positive");
        }
        if (row.potentialReward() < 0L) {
            throw new IllegalArgumentException(
                    "monster template potential reward must be non-negative");
        }
        requireRange(row.rangeMove(), 0, Short.MAX_VALUE, "monster template rangeMove");
        requireRange(row.speed(), 0, Byte.MAX_VALUE, "monster template speed");
        requireRange(row.typeMove(), 0, 2, "monster template typeMove");
        requireRange(row.dartId(), 0, Byte.MAX_VALUE, "monster template dartId");
        if (!dartsById.containsKey(row.dartId())) {
            throw new IllegalArgumentException(
                    "monster template references missing dart " + row.dartId());
        }
        requireRange(row.w(), 1, Short.MAX_VALUE, "monster template w");
        requireRange(row.h(), 1, Short.MAX_VALUE, "monster template h");
        return new MonsterTemplate(
                row.id(),
                row.name(),
                row.level(),
                row.hp(),
                row.damage(),
                row.potentialReward(),
                row.rangeMove(),
                row.speed(),
                row.typeMove(),
                row.dartId(),
                readAnimation(row.iconMove(), "iconMove"),
                readAnimation(row.iconInjure(), "iconInjure"),
                readAnimation(row.iconAttack(), "iconAttack"),
                row.w(),
                row.h());
    }

    private static List<Integer> readAnimation(String json, String field) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("monster template " + field + " must not be blank");
        }
        final JsonElement value;
        try {
            value = JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                    "monster template " + field + " must contain valid JSON", exception);
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(
                    "monster template " + field + " must be a JSON array");
        }
        if (value.getAsJsonArray().isEmpty()
                || value.getAsJsonArray().size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "monster template " + field + " must contain 1..127 icons");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            int icon = JsonResourceReader.readCanonicalInt(element, field);
            requireRange(icon, 0, Short.MAX_VALUE, "monster template " + field + " icon");
            result.add(icon);
        }
        return List.copyOf(result);
    }

    private static MonsterSpawn readSpawn(
            MonsterRepository.SpawnRow row,
            Map<Integer, MonsterTemplate> templatesById,
            Map<Integer, MapTemplate> maps,
            Set<Integer> spawnIds) {
        if (row == null) {
            throw new IllegalArgumentException("monster spawn row must not be null");
        }
        if (row.id() <= 0) {
            throw new IllegalArgumentException("monster spawn id must be positive: " + row.id());
        }
        if (!spawnIds.add(row.id())) {
            throw new IllegalArgumentException("duplicate monster spawn id: " + row.id());
        }
        requireRange(row.mapId(), 0, Short.MAX_VALUE, "monster spawn map id");
        MapTemplate map = maps.get(row.mapId());
        if (map == null) {
            throw new IllegalArgumentException(
                    "monster spawn references unavailable map " + row.mapId());
        }
        requireRange(row.monsterId(), 0, Short.MAX_VALUE, "monster spawn monster id");
        MonsterTemplate template = templatesById.get(row.monsterId());
        if (template == null) {
            throw new IllegalArgumentException(
                    "monster spawn references missing template " + row.monsterId());
        }
        requireRange(row.x(), 0, Short.MAX_VALUE, "monster spawn x");
        requireRange(row.y(), 0, Short.MAX_VALUE, "monster spawn y");
        if (row.x() > map.data().width() || row.y() > map.data().height()) {
            throw new IllegalArgumentException(
                    "monster spawn coordinate is outside map bounds: " + row.id());
        }
        return new MonsterSpawn(
                0,
                template.id(),
                row.id(),
                template.level(),
                0,
                row.x(),
                row.y(),
                template.hp(),
                template.hp(),
                0);
    }

    private static <T> List<T> requireRows(List<T> rows, String label) {
        if (rows == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return rows;
    }

    private static void requireRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " must fit " + minimum + ".." + maximum + ": " + value);
        }
    }

    record LoadedMonsters(
            int version,
            List<MonsterDart> darts,
            List<MonsterTemplate> templates,
            Map<Integer, List<MonsterSpawn>> spawns
    ) {
    }
}

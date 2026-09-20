package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.monster.LegacyMonsterDart;
import com.project.game.monster.LegacyMonsterDartPhase;
import com.project.game.monster.LegacyMonsterSpawn;
import com.project.game.monster.LegacyMonsterTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MonsterLoader {
    private MonsterLoader() {
    }

    static LoadedMonsters load(Path root, boolean required) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve("MonsterBootstrap.json").normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "MonsterBootstrap.json is not readable below " + normalizedRoot);
            }
            return new LoadedMonsters(-1, List.of(), List.of(), Map.of());
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "MonsterBootstrap.json");
        JsonResourceReader.requireExactFields(rootObject,
                Set.of("version", "darts", "templates", "mapSpawns"),
                "MonsterBootstrap.json");
        if (JsonResourceReader.readStrictInt(rootObject, "version") != 1) {
            throw new IllegalArgumentException("MonsterBootstrap.json version must be 1");
        }

        JsonElement dartsValue = JsonResourceReader.required(rootObject, "darts");
        JsonElement templatesValue = JsonResourceReader.required(rootObject, "templates");
        JsonElement mapSpawnsValue = JsonResourceReader.required(rootObject, "mapSpawns");
        if (!dartsValue.isJsonArray() || dartsValue.getAsJsonArray().size() != 1) {
            throw new IllegalArgumentException("MonsterBootstrap.json must contain exactly one dart");
        }
        if (!templatesValue.isJsonArray() || templatesValue.getAsJsonArray().size() != 1) {
            throw new IllegalArgumentException("MonsterBootstrap.json must contain exactly one template");
        }
        if (!mapSpawnsValue.isJsonObject()) {
            throw new IllegalArgumentException("MonsterBootstrap.json mapSpawns must be an object");
        }
        JsonObject mapSpawnsObject = mapSpawnsValue.getAsJsonObject();
        if (!mapSpawnsObject.keySet().equals(Set.of("0", "1"))) {
            throw new IllegalArgumentException(
                    "MonsterBootstrap.json mapSpawns must contain exactly maps 0 and 1");
        }

        LegacyMonsterDart dart = readMonsterDart(dartsValue.getAsJsonArray().get(0));
        LegacyMonsterTemplate template = readMonsterTemplate(
                templatesValue.getAsJsonArray().get(0), dart);
        List<LegacyMonsterSpawn> map0 = readMonsterSpawns(
                mapSpawnsObject.get("0"), 0, template);
        List<LegacyMonsterSpawn> map1 = readMonsterSpawns(
                mapSpawnsObject.get("1"), 1, template);
        if (!map0.isEmpty()) {
            throw new IllegalArgumentException("MonsterBootstrap Map0 must contain no monsters");
        }
        if (map1.size() != 6) {
            throw new IllegalArgumentException("MonsterBootstrap Map1 must contain exactly six monsters");
        }
        List<Integer> expectedX = List.of(975, 1348, 1800, 2250, 2600, 2950);
        for (int index = 0; index < map1.size(); index++) {
            LegacyMonsterSpawn spawn = map1.get(index);
            if (spawn.id() != index || spawn.x() != expectedX.get(index)
                    || spawn.y() != 936 || spawn.type() != 0 || spawn.templateId() != 1
                    || spawn.level() != 2 || spawn.levelStatus() != 0
                    || spawn.maxHp() != 300L || spawn.hp() != 300L || spawn.status() != 0) {
                throw new IllegalArgumentException(
                        "MonsterBootstrap Map1 spawn " + index + " is not canonical");
            }
        }

        Map<Integer, List<LegacyMonsterSpawn>> spawns = new HashMap<>();
        spawns.put(0, map0);
        spawns.put(1, map1);
        return new LoadedMonsters(1, List.of(dart), List.of(template),
                Collections.unmodifiableMap(spawns));
    }

    private static LegacyMonsterDart readMonsterDart(JsonElement value) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("MonsterBootstrap dart must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        JsonResourceReader.requireExactFields(object,
                Set.of("id", "isMeteorite", "light", "bullet", "explode"),
                "MonsterBootstrap dart");
        int id = JsonResourceReader.readShortValue(object, "id");
        boolean meteorite = JsonResourceReader.readBoolean(object, "isMeteorite");
        if (id != 0 || meteorite) {
            throw new IllegalArgumentException("MonsterBootstrap dart must be id 0 and non-meteorite");
        }
        LegacyMonsterDartPhase light = readMonsterDartPhase(object, "light");
        LegacyMonsterDartPhase bullet = readMonsterDartPhase(object, "bullet");
        LegacyMonsterDartPhase explode = readMonsterDartPhase(object, "explode");
        if (!light.icons().equals(List.of(2198, 2199, 2200)) || light.dx() != 0
                || light.dy() != 0 || light.delay() != 30
                || !bullet.icons().equals(List.of(2190, 2191, 2192)) || bullet.dx() != 0
                || bullet.dy() != 0 || bullet.delay() != 30
                || !explode.icons().equals(List.of(2193, 2194, 2195, 2196, 2197))
                || explode.dx() != 0 || explode.dy() != 0 || explode.delay() != 20) {
            throw new IllegalArgumentException("MonsterBootstrap dart 0 is not canonical");
        }
        return new LegacyMonsterDart(id, meteorite, light, bullet, explode);
    }

    private static LegacyMonsterDartPhase readMonsterDartPhase(JsonObject parent, String field) {
        JsonObject object = JsonResourceReader.requiredObject(parent, field);
        JsonResourceReader.requireExactFields(object,
                Set.of("icons", "dx", "dy", "delay"),
                "MonsterBootstrap dart " + field);
        List<Integer> icons = JsonResourceReader.readShortList(object, "icons");
        if (icons.size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("too many icons for MonsterBootstrap dart " + field);
        }
        return new LegacyMonsterDartPhase(icons,
                JsonResourceReader.readShortValue(object, "dx"),
                JsonResourceReader.readShortValue(object, "dy"),
                JsonResourceReader.readShortValue(object, "delay"));
    }

    private static LegacyMonsterTemplate readMonsterTemplate(JsonElement value,
                                                               LegacyMonsterDart dart) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("MonsterBootstrap template must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        JsonResourceReader.requireExactFields(object,
                Set.of("id", "name", "rangeMove", "speed", "type", "dartId",
                        "iconsMove", "iconInjure", "iconAttack", "w", "h", "dx", "dy"),
                "MonsterBootstrap template");
        int id = JsonResourceReader.readShortValue(object, "id");
        String name = JsonResourceReader.readString(object, "name");
        int rangeMove = JsonResourceReader.readShortValue(object, "rangeMove");
        int speed = JsonResourceReader.readByteValue(object, "speed");
        int type = JsonResourceReader.readByteValue(object, "type");
        int dartId = JsonResourceReader.readByteValue(object, "dartId");
        List<Integer> iconsMove = JsonResourceReader.readShortList(object, "iconsMove");
        if (iconsMove.size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("too many move icons for MonsterBootstrap template");
        }
        int iconInjure = JsonResourceReader.readShortValue(object, "iconInjure");
        int iconAttack = JsonResourceReader.readShortValue(object, "iconAttack");
        int width = JsonResourceReader.readShortValue(object, "w");
        int height = JsonResourceReader.readShortValue(object, "h");
        int dx = JsonResourceReader.readByteValue(object, "dx");
        int dy = JsonResourceReader.readByteValue(object, "dy");
        if (id != 1 || !"Hổ nanh kiếm".equals(name) || rangeMove != 100 || speed != 1
                || type != 1 || dartId != 0
                || !iconsMove.equals(List.of(11818, 11819, 11820, 11821, 11822))
                || iconInjure != 11824 || iconAttack != 11823 || width != 175 || height != 95
                || dx != 0 || dy != 0 || dart.id() != dartId) {
            throw new IllegalArgumentException("MonsterBootstrap template 1 is not canonical");
        }
        return new LegacyMonsterTemplate(id, name, rangeMove, speed, type, dartId,
                iconsMove, iconInjure, iconAttack, width, height, dx, dy);
    }

    private static List<LegacyMonsterSpawn> readMonsterSpawns(JsonElement value, int mapId,
                                                                LegacyMonsterTemplate template) {
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(
                    "MonsterBootstrap map " + mapId + " spawns must be an array");
        }
        List<LegacyMonsterSpawn> result = new java.util.ArrayList<>(value.getAsJsonArray().size());
        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < value.getAsJsonArray().size(); index++) {
            JsonElement element = value.getAsJsonArray().get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("MonsterBootstrap map " + mapId
                        + " spawn " + index + " must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            JsonResourceReader.requireExactFields(object,
                    Set.of("type", "templateId", "id", "level", "levelStatus",
                            "x", "y", "maxHp", "hp", "status"),
                    "MonsterBootstrap map " + mapId + " spawn " + index);
            int type = JsonResourceReader.readByteValue(object, "type");
            int templateId = JsonResourceReader.readShortValue(object, "templateId");
            int id = JsonResourceReader.readStrictInt(object, "id");
            int level = JsonResourceReader.readShortValue(object, "level");
            int levelStatus = JsonResourceReader.readByteValue(object, "levelStatus");
            int x = JsonResourceReader.readShortValue(object, "x");
            int y = JsonResourceReader.readShortValue(object, "y");
            long maxHp = JsonResourceReader.readStrictLong(object, "maxHp");
            long hp = JsonResourceReader.readStrictLong(object, "hp");
            int status = JsonResourceReader.readByteValue(object, "status");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate MonsterBootstrap runtime id " + id);
            }
            if (templateId != template.id()) {
                throw new IllegalArgumentException("MonsterBootstrap spawn references missing template "
                        + templateId);
            }
            result.add(new LegacyMonsterSpawn(type, templateId, id, level, levelStatus,
                    x, y, maxHp, hp, status));
        }
        return List.copyOf(result);
    }

    record LoadedMonsters(
            int version,
            List<LegacyMonsterDart> darts,
            List<LegacyMonsterTemplate> templates,
            Map<Integer, List<LegacyMonsterSpawn>> spawns
    ) {
    }
}

package com.project.game.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.project.game.frame.FrameTemplate;
import com.project.game.map.LegacyWaypoint;
import com.project.game.map.LegacyMapTemplate;
import com.project.game.monster.LegacyMonsterDart;
import com.project.game.monster.LegacyMonsterDartPhase;
import com.project.game.monster.LegacyMonsterSpawn;
import com.project.game.monster.LegacyMonsterTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Development resource access for static legacy data such as numeric-ID icons. */
public final class ResourceService {
    private static final Set<Integer> SUPPORTED_MAP_IDS = Set.of(0, 1);
    private static final List<Integer> REQUIRED_FRAME_IDS = List.of(3, 4, 5, 6, 7, 8, 21, 22, 23);
    private static final List<Integer> REQUIRED_MOVEMENT_EFFECT_IDS = List.of(6, 7);
    private static final Set<Integer> REQUIRED_PLAYER_SKILL_IDS = Set.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            30, 31, 32, 33, 34, 35, 36);
    private static final List<List<Integer>> PLAYER_SKILL_IDS = List.of(
            List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36),
            List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36),
            List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36));
    private static final ResourceService UNAVAILABLE = new ResourceService(null, null, false);
    private final Path iconRoot;
    private final List<FrameTemplate> frames;
    private final Map<Integer, List<LegacyPlayerSkill>> playerSkills;
    private final Map<Integer, LegacyMapTemplate> maps;
    private final List<LegacyLevel> levels;
    private final List<LegacyEffectImage> effects;
    private final int monsterVersion;
    private final List<LegacyMonsterDart> monsterDarts;
    private final List<LegacyMonsterTemplate> monsterTemplates;
    private final Map<Integer, List<LegacyMonsterSpawn>> monsterSpawns;

    private ResourceService(Path iconRoot, Path frameRoot, boolean requirePlayerSkills) {
        this.iconRoot = iconRoot == null ? null : iconRoot.toAbsolutePath().normalize();
        this.frames = frameRoot == null ? List.of() : loadFrames(frameRoot);
        this.playerSkills = frameRoot == null
                ? Map.of()
                : loadPlayerSkills(frameRoot, requirePlayerSkills);
        this.maps = frameRoot == null
                ? Map.of()
                : loadMaps(frameRoot, requirePlayerSkills);
        this.levels = frameRoot == null
                ? List.of()
                : loadLevels(frameRoot, requirePlayerSkills);
        this.effects = frameRoot == null
                ? List.of()
                : loadEffects(frameRoot, requirePlayerSkills);
        if (frameRoot == null) {
            this.monsterVersion = -1;
            this.monsterDarts = List.of();
            this.monsterTemplates = List.of();
            this.monsterSpawns = Map.of();
        } else {
            MonsterResources monsters = loadMonsters(frameRoot, requirePlayerSkills);
            this.monsterVersion = monsters.version();
            this.monsterDarts = monsters.darts();
            this.monsterTemplates = monsters.templates();
            this.monsterSpawns = monsters.spawns();
        }
    }

    public static ResourceService unavailable() {
        return UNAVAILABLE;
    }

    public static ResourceService fromIconRoot(Path iconRoot) {
        return new ResourceService(Objects.requireNonNull(iconRoot, "iconRoot"), null, false);
    }

    public static ResourceService fromFrameRoot(Path frameRoot) {
        return new ResourceService(null, Objects.requireNonNull(frameRoot, "frameRoot"), false);
    }

    public static ResourceService fromRoots(Path iconRoot, Path frameRoot) {
        return new ResourceService(iconRoot, frameRoot, true);
    }

    public Optional<byte[]> loadIcon(int iconId) {
        if (iconRoot == null) {
            return Optional.empty();
        }
        Path icon = iconRoot.resolve(Integer.toString(iconId) + ".png").normalize();
        if (!icon.startsWith(iconRoot) || !Files.isRegularFile(icon) || !Files.isReadable(icon)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(icon));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public List<FrameTemplate> frames() {
        return frames;
    }

    public List<LegacyPlayerSkill> playerSkills(int gender) {
        return playerSkills.getOrDefault(gender, List.of());
    }

    public Optional<LegacyMapTemplate> map(int mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public List<LegacyLevel> levels() {
        return levels;
    }

    public List<LegacyEffectImage> effects() {
        return effects;
    }

    public int monsterVersion() {
        return monsterVersion;
    }

    public List<LegacyMonsterDart> monsterDarts() {
        return monsterDarts;
    }

    public List<LegacyMonsterTemplate> monsterTemplates() {
        return monsterTemplates;
    }

    public List<LegacyMonsterSpawn> monstersForMap(int mapId) {
        return monsterSpawns.getOrDefault(mapId, List.of());
    }

    private static List<FrameTemplate> loadFrames(Path frameRoot) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("Frame.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("Frame.json is not readable below " + root);
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Frame.json root must be an object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            List<FrameTemplate> selected = new ArrayList<>(REQUIRED_FRAME_IDS.size());
            for (int id : REQUIRED_FRAME_IDS) {
                JsonElement value = rootObject.get(Integer.toString(id));
                if (value == null || !value.isJsonObject()) {
                    throw new IllegalArgumentException("Frame.json is missing required frame " + id);
                }
                selected.add(readFrame(id, value.getAsJsonObject()));
            }
            return List.copyOf(selected);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid Frame.json at " + source, exception);
        }
    }

    private static Map<Integer, List<LegacyPlayerSkill>> loadPlayerSkills(
            Path frameRoot, boolean required) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("PlayerSkillBootstrap.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "PlayerSkillBootstrap.json is not readable below " + root);
            }
            return Map.of();
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("PlayerSkillBootstrap.json root must be an object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            JsonObject genderObject = requiredObject(rootObject, "genderSkills");
            JsonObject templateObject = requiredObject(rootObject, "templates");

            Set<Integer> templateIds = new HashSet<>();
            Map<Integer, LegacyPlayerSkill> templates = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : templateObject.entrySet()) {
                int id = parseId(entry.getKey(), "skill template");
                if (!templateIds.add(id) || !REQUIRED_PLAYER_SKILL_IDS.contains(id)) {
                    throw new IllegalArgumentException("unexpected or duplicate skill template " + id);
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("skill template " + id + " must be an object");
                }
                LegacyPlayerSkill skill = readSkill(entry.getValue().getAsJsonObject());
                if (skill.id() != id) {
                    throw new IllegalArgumentException("skill template key/id mismatch for " + id);
                }
                templates.put(id, skill);
            }
            if (!templateIds.equals(REQUIRED_PLAYER_SKILL_IDS)) {
                throw new IllegalArgumentException("PlayerSkillBootstrap.json must contain exactly the 25 approved templates");
            }

            Map<Integer, List<LegacyPlayerSkill>> byGender = new HashMap<>();
            for (int gender = 0; gender < PLAYER_SKILL_IDS.size(); gender++) {
                String key = Integer.toString(gender);
                JsonElement idsValue = genderObject.get(key);
                if (idsValue == null || !idsValue.isJsonArray()) {
                    throw new IllegalArgumentException("missing gender skill list " + gender);
                }
                List<Integer> expectedIds = readIntList(idsValue, "genderSkills." + key);
                if (!expectedIds.equals(PLAYER_SKILL_IDS.get(gender))) {
                    throw new IllegalArgumentException("invalid fresh skill list for gender " + gender);
                }
                List<LegacyPlayerSkill> skills = new ArrayList<>(expectedIds.size());
                for (int id : expectedIds) {
                    LegacyPlayerSkill skill = templates.get(id);
                    if (skill == null) {
                        throw new IllegalArgumentException("gender " + gender + " references missing skill " + id);
                    }
                    skills.add(skill);
                }
                byGender.put(gender, List.copyOf(skills));
            }
            return Collections.unmodifiableMap(byGender);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid PlayerSkillBootstrap.json at " + source, exception);
        }
    }

    private static Map<Integer, LegacyMapTemplate> loadMaps(Path frameRoot, boolean required) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("MapBootstrap.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException("MapBootstrap.json is not readable below " + root);
            }
            return Map.of();
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("MapBootstrap.json root must be an object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            Map<Integer, LegacyMapTemplate> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
                int key = parseId(entry.getKey(), "map");
                if (!SUPPORTED_MAP_IDS.contains(key)) {
                    throw new IllegalArgumentException("MapBootstrap.json contains unsupported map " + key);
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("Map" + key + " must be an object");
                }
                LegacyMapTemplate map = readMap(entry.getValue().getAsJsonObject());
                if (map.id() != key) {
                    throw new IllegalArgumentException("Map" + key + " key/id mismatch");
                }
                validateMap(map);
                if (loaded.put(key, map) != null) {
                    throw new IllegalArgumentException("duplicate Map" + key + " bootstrap");
                }
            }
            if (!loaded.keySet().equals(SUPPORTED_MAP_IDS)) {
                throw new IllegalArgumentException(
                        "MapBootstrap.json must contain exactly Map0 and Map1");
            }
            validateWaypointTopology(loaded);
            return Collections.unmodifiableMap(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid MapBootstrap.json at " + source, exception);
        }
    }

    private static List<LegacyLevel> loadLevels(Path frameRoot, boolean required) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("LevelBootstrap.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "LevelBootstrap.json is not readable below " + root);
            }
            return List.of();
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("LevelBootstrap.json root must be an object");
            }
            JsonElement levelsValue = required(parsed.getAsJsonObject(), "levels");
            if (!levelsValue.isJsonArray()) {
                throw new IllegalArgumentException("LevelBootstrap.json field levels must be an array");
            }
            if (levelsValue.getAsJsonArray().size() != 102) {
                throw new IllegalArgumentException("LevelBootstrap.json must contain exactly 102 levels");
            }

            List<LegacyLevel> loaded = new ArrayList<>(levelsValue.getAsJsonArray().size());
            long previousPower = -1L;
            for (int index = 0; index < levelsValue.getAsJsonArray().size(); index++) {
                JsonElement element = levelsValue.getAsJsonArray().get(index);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("LevelBootstrap level " + index
                            + " must be an object");
                }
                JsonObject levelObject = element.getAsJsonObject();
                int id = readInt(levelObject, "id");
                if (id != index) {
                    throw new IllegalArgumentException("LevelBootstrap level " + index
                            + " id must be " + index + " but was " + id);
                }
                String name = readString(levelObject, "name");
                if (name.isBlank()) {
                    throw new IllegalArgumentException("LevelBootstrap level " + index
                            + " name must not be blank");
                }
                long power = readLong(levelObject, "power");
                if (power < 0L) {
                    throw new IllegalArgumentException("LevelBootstrap level " + index
                            + " power must be non-negative");
                }
                if (index > 0 && power <= previousPower) {
                    throw new IllegalArgumentException("LevelBootstrap level " + index
                            + " power must be strictly increasing");
                }
                loaded.add(new LegacyLevel(id, name, power));
                previousPower = power;
            }
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid LevelBootstrap.json at " + source, exception);
        }
    }

    private static List<LegacyEffectImage> loadEffects(Path frameRoot, boolean required) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("EffectBootstrap.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "EffectBootstrap.json is not readable below " + root);
            }
            return List.of();
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("EffectBootstrap.json root must be an object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            requireExactFields(rootObject, Set.of("version", "images"), "EffectBootstrap.json");
            if (readShortValue(rootObject, "version") != 0) {
                throw new IllegalArgumentException("EffectBootstrap.json version must be 0");
            }
            JsonElement imagesValue = required(rootObject, "images");
            if (!imagesValue.isJsonArray()) {
                throw new IllegalArgumentException("EffectBootstrap.json field images must be an array");
            }
            if (imagesValue.getAsJsonArray().size() != REQUIRED_MOVEMENT_EFFECT_IDS.size()) {
                throw new IllegalArgumentException("EffectBootstrap.json must contain exactly 2 images");
            }

            List<LegacyEffectImage> loaded = new ArrayList<>(imagesValue.getAsJsonArray().size());
            Set<Integer> ids = new HashSet<>();
            for (int index = 0; index < imagesValue.getAsJsonArray().size(); index++) {
                JsonElement element = imagesValue.getAsJsonArray().get(index);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("EffectBootstrap image " + index
                            + " must be an object");
                }
                JsonObject image = element.getAsJsonObject();
                requireExactFields(image, Set.of("id", "dx", "dy", "delay", "icons"),
                        "EffectBootstrap image " + index);
                int id = readShortValue(image, "id");
                int expectedId = REQUIRED_MOVEMENT_EFFECT_IDS.get(index);
                if (id != expectedId) {
                    throw new IllegalArgumentException("EffectBootstrap image " + index
                            + " id must be " + expectedId + " but was " + id);
                }
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("duplicate EffectBootstrap image " + id);
                }
                List<Integer> icons = readShortList(image, "icons");
                if (icons.isEmpty()) {
                    throw new IllegalArgumentException("EffectBootstrap image " + id
                            + " icons must not be empty");
                }
                if (icons.size() > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("too many icons for EffectBootstrap image " + id);
                }
                loaded.add(new LegacyEffectImage(
                        id,
                        readShortValue(image, "dx"),
                        readShortValue(image, "dy"),
                        readShortValue(image, "delay"),
                        icons));
            }
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid EffectBootstrap.json at " + source, exception);
        }
    }

    private static MonsterResources loadMonsters(Path frameRoot, boolean required) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("MonsterBootstrap.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "MonsterBootstrap.json is not readable below " + root);
            }
            return new MonsterResources(-1, List.of(), List.of(), Map.of());
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("MonsterBootstrap.json root must be an object");
            }
            JsonObject rootObject = parsed.getAsJsonObject();
            requireExactFields(rootObject, Set.of("version", "darts", "templates", "mapSpawns"),
                    "MonsterBootstrap.json");
            if (readStrictInt(rootObject, "version") != 1) {
                throw new IllegalArgumentException("MonsterBootstrap.json version must be 1");
            }

            JsonElement dartsValue = required(rootObject, "darts");
            JsonElement templatesValue = required(rootObject, "templates");
            JsonElement mapSpawnsValue = required(rootObject, "mapSpawns");
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
            return new MonsterResources(
                    1,
                    List.of(dart),
                    List.of(template),
                    Collections.unmodifiableMap(spawns));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid MonsterBootstrap.json at " + source, exception);
        }
    }

    private static LegacyMonsterDart readMonsterDart(JsonElement value) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("MonsterBootstrap dart must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        requireExactFields(object, Set.of("id", "isMeteorite", "light", "bullet", "explode"),
                "MonsterBootstrap dart");
        int id = readShortValue(object, "id");
        boolean meteorite = readBoolean(object, "isMeteorite");
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
        JsonObject object = requiredObject(parent, field);
        requireExactFields(object, Set.of("icons", "dx", "dy", "delay"),
                "MonsterBootstrap dart " + field);
        List<Integer> icons = readShortList(object, "icons");
        if (icons.size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("too many icons for MonsterBootstrap dart " + field);
        }
        return new LegacyMonsterDartPhase(
                icons,
                readShortValue(object, "dx"),
                readShortValue(object, "dy"),
                readShortValue(object, "delay"));
    }

    private static LegacyMonsterTemplate readMonsterTemplate(JsonElement value,
                                                               LegacyMonsterDart dart) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("MonsterBootstrap template must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        requireExactFields(object, Set.of("id", "name", "rangeMove", "speed", "type",
                "dartId", "iconsMove", "iconInjure", "iconAttack", "w", "h", "dx", "dy"),
                "MonsterBootstrap template");
        int id = readShortValue(object, "id");
        String name = readString(object, "name");
        int rangeMove = readShortValue(object, "rangeMove");
        int speed = readByteValue(object, "speed");
        int type = readByteValue(object, "type");
        int dartId = readByteValue(object, "dartId");
        List<Integer> iconsMove = readShortList(object, "iconsMove");
        if (iconsMove.size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("too many move icons for MonsterBootstrap template");
        }
        int iconInjure = readShortValue(object, "iconInjure");
        int iconAttack = readShortValue(object, "iconAttack");
        int width = readShortValue(object, "w");
        int height = readShortValue(object, "h");
        int dx = readByteValue(object, "dx");
        int dy = readByteValue(object, "dy");
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
            throw new IllegalArgumentException("MonsterBootstrap map " + mapId + " spawns must be an array");
        }
        List<LegacyMonsterSpawn> result = new ArrayList<>(value.getAsJsonArray().size());
        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < value.getAsJsonArray().size(); index++) {
            JsonElement element = value.getAsJsonArray().get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("MonsterBootstrap map " + mapId
                        + " spawn " + index + " must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            requireExactFields(object, Set.of("type", "templateId", "id", "level", "levelStatus",
                    "x", "y", "maxHp", "hp", "status"),
                    "MonsterBootstrap map " + mapId + " spawn " + index);
            int type = readByteValue(object, "type");
            int templateId = readShortValue(object, "templateId");
            int id = readStrictInt(object, "id");
            int level = readShortValue(object, "level");
            int levelStatus = readByteValue(object, "levelStatus");
            int x = readShortValue(object, "x");
            int y = readShortValue(object, "y");
            long maxHp = readLongStrict(object, "maxHp");
            long hp = readLongStrict(object, "hp");
            int status = readByteValue(object, "status");
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

    private static int readByteValue(JsonObject object, String field) {
        int value = readStrictInt(object, field);
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("resource field " + field
                    + " must fit signed byte: " + value);
        }
        return value;
    }

    private static long readLongStrict(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be a long integer", exception);
        }
    }

    private record MonsterResources(
            int version,
            List<LegacyMonsterDart> darts,
            List<LegacyMonsterTemplate> templates,
            Map<Integer, List<LegacyMonsterSpawn>> spawns
    ) {}

    private static void requireExactFields(JsonObject object, Set<String> expected, String label) {
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(label + " must contain exactly fields " + expected
                    + " but found " + object.keySet());
        }
    }

    private static List<Integer> readShortList(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("resource field " + field
                        + " contains a non-number");
            }
            result.add(readShortValue(element, field));
        }
        return List.copyOf(result);
    }

    private static int readShortValue(JsonObject object, String field) {
        return readShortValue(required(object, field), field);
    }

    private static int readShortValue(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            long number = new BigDecimal(value.getAsString()).longValueExact();
            if (number < Short.MIN_VALUE || number > Short.MAX_VALUE) {
                throw new IllegalArgumentException("resource field " + field
                        + " must fit signed short: " + number);
            }
            return (int) number;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be an integer", exception);
        }
    }

    private static LegacyMapTemplate readMap(JsonObject value) {
        requireExactFields(value, Set.of("id", "iconId", "name", "row", "column", "data",
                "imagesBgr", "colorsBgr", "isLine", "dataLine", "waypoints"),
                "MapBootstrap map");
        JsonElement lineValue = required(value, "isLine");
        if (!lineValue.isJsonPrimitive() || !lineValue.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("MapBootstrap map field isLine must be boolean");
        }
        int mapId = readInt(value, "id");
        return new LegacyMapTemplate(
                mapId,
                readInt(value, "iconId"),
                readString(value, "name"),
                readInt(value, "row"),
                readInt(value, "column"),
                readString(value, "data"),
                List.copyOf(readIntList(value, "imagesBgr")),
                immutableMatrix(readIntMatrix(value, "colorsBgr")),
                lineValue.getAsBoolean(),
                readNullableString(value, "dataLine"),
                readWaypoints(value, mapId));
    }

    private static void validateMap(LegacyMapTemplate map) {
        if (!SUPPORTED_MAP_IDS.contains(map.id())) {
            throw new IllegalArgumentException("unsupported map id " + map.id());
        }
        String expectedName = map.id() == 0 ? "Núi Paozu" : "Bờ sông Pu";
        int expectedIcon = map.id();
        if (map.iconId() != expectedIcon || !expectedName.equals(map.name())) {
            throw new IllegalArgumentException("Map" + map.id() + " static metadata is not canonical");
        }
        if (map.row() <= 0 || map.column() <= 0) {
            throw new IllegalArgumentException("Map" + map.id() + " grid dimensions must be positive");
        }
        long expectedLength = (long) map.row() * map.column();
        if (expectedLength > Integer.MAX_VALUE || map.data().length() != expectedLength) {
            throw new IllegalArgumentException("Map" + map.id() + " grid data length must be "
                    + expectedLength + " but was " + map.data().length());
        }
        if (!map.data().chars().allMatch(ch -> ch == '0' || ch == '1')) {
            throw new IllegalArgumentException("Map" + map.id() + " grid data must contain only 0/1");
        }
        if (map.imagesBgr().size() != 3) {
            throw new IllegalArgumentException("Map" + map.id()
                    + " must contain exactly 3 background images");
        }
        if (map.colorsBgr().size() != 4
                || map.colorsBgr().stream().anyMatch(row -> row.size() != 3)) {
            throw new IllegalArgumentException("Map" + map.id() + " colorsBgr must be a 4x3 matrix");
        }
        if (!map.line() && map.dataLine() != null) {
            throw new IllegalArgumentException("Map" + map.id()
                    + " dataLine must be null when isLine is false");
        }
        if (map.line() && map.dataLine() == null) {
            throw new IllegalArgumentException("Map" + map.id() + " line map is missing dataLine");
        }
    }

    private static List<LegacyWaypoint> readWaypoints(JsonObject mapObject, int ownerMapId) {
        JsonElement value = required(mapObject, "waypoints");
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("Map" + ownerMapId + " waypoints must be an array");
        }
        List<LegacyWaypoint> waypoints = new ArrayList<>(value.getAsJsonArray().size());
        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < value.getAsJsonArray().size(); index++) {
            JsonElement element = value.getAsJsonArray().get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Map" + ownerMapId
                        + " waypoint " + index + " must be an object");
            }
            JsonObject waypoint = element.getAsJsonObject();
            requireExactFields(waypoint, Set.of("id", "goMap", "x", "y", "goX", "goY", "type"),
                    "Map" + ownerMapId + " waypoint " + index);
            int id = readStrictInt(waypoint, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate waypoint id " + id + " in Map" + ownerMapId);
            }
            int goMap = readStrictInt(waypoint, "goMap");
            int x = readShortValue(waypoint, "x");
            int y = readShortValue(waypoint, "y");
            int goX = readShortValue(waypoint, "goX");
            int goY = readShortValue(waypoint, "goY");
            int type = readStrictInt(waypoint, "type");
            if (type < 0 || type > 2) {
                throw new IllegalArgumentException("Map" + ownerMapId
                        + " waypoint " + id + " type must be 0..2");
            }
            waypoints.add(new LegacyWaypoint(id, goMap, x, y, goX, goY, type));
        }
        return List.copyOf(waypoints);
    }

    private static void validateWaypointTopology(Map<Integer, LegacyMapTemplate> maps) {
        LegacyWaypoint map0Waypoint = requireSingleWaypoint(maps.get(0), 2, 1, 1);
        LegacyWaypoint map1Waypoint = requireSingleWaypoint(maps.get(1), 3, 0, 0);
        if (map0Waypoint.goMap() != 1 || map1Waypoint.goMap() != 0) {
            throw new IllegalArgumentException("MapBootstrap waypoint topology must be Map0 <-> Map1");
        }
        for (LegacyMapTemplate map : maps.values()) {
            for (LegacyWaypoint waypoint : map.waypoints()) {
                if (!maps.containsKey(waypoint.goMap())) {
                    throw new IllegalArgumentException("waypoint target map unavailable: "
                            + waypoint.goMap());
                }
            }
        }
    }

    private static LegacyWaypoint requireSingleWaypoint(
            LegacyMapTemplate map, int id, int goMap, int type) {
        if (map == null || map.waypoints().size() != 1) {
            throw new IllegalArgumentException("Map" + (map == null ? "?" : map.id())
                    + " must contain exactly one supported waypoint");
        }
        LegacyWaypoint waypoint = map.waypoints().getFirst();
        if (waypoint.id() != id || waypoint.goMap() != goMap || waypoint.type() != type) {
            throw new IllegalArgumentException("Map" + map.id() + " has unsupported waypoint topology");
        }
        return waypoint;
    }

    private static LegacyPlayerSkill readSkill(JsonObject value) {
        return new LegacyPlayerSkill(
                readInt(value, "id"),
                readStringList(value, "names"),
                readStringList(value, "descriptions"),
                readInt(value, "type"),
                readBoolean(value, "proactive"),
                readIntList(value, "icons"),
                readIntMatrix(value, "dx"),
                readIntMatrix(value, "dy"),
                readInt(value, "levelRequire"),
                readInt(value, "maxLevel"),
                readInt(value, "maxUpgrade"),
                readIntList(value, "pointUpgrade"),
                readIntMatrix(value, "coolDown"),
                readInt(value, "typeMana"),
                readIntMatrix(value, "mana"),
                readOptions(value, "options"),
                readInt(value, "initialLevel"),
                readInt(value, "initialUpgrade"),
                readInt(value, "initialPoint"),
                readInt(value, "initialCooldownReduction"),
                readLong(value, "initialTimeCanUse"),
                readPaints(value, "initialPaints"));
    }

    private static List<LegacySkillOption> readOptions(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("skill field " + field + " must be an array");
        }
        List<LegacySkillOption> options = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("skill option must be an object");
            }
            JsonObject option = element.getAsJsonObject();
            options.add(new LegacySkillOption(
                    readInt(option, "id"),
                    readString(option, "name"),
                    readIntList(option, "normal"),
                    readIntList(option, "upgrade")));
        }
        return options;
    }

    private static List<LegacySkillPaint> readPaints(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("skill field " + field + " must be an array");
        }
        List<LegacySkillPaint> paints = new ArrayList<>(value.getAsJsonArray().size());
        double previousPercent = 0d;
        int paintIndex = 0;
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("skill paint must be an object");
            }
            JsonObject paint = element.getAsJsonObject();
            String percent = readString(paint, "percent");
            final double cumulativePercent;
            try {
                cumulativePercent = Double.parseDouble(percent);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "skill field " + field + " paint " + paintIndex
                                + " percent must be numeric: " + percent,
                        exception);
            }
            if (!Double.isFinite(cumulativePercent)
                    || cumulativePercent <= previousPercent
                    || cumulativePercent > 100d) {
                throw new IllegalArgumentException(
                        "skill field " + field + " paint " + paintIndex
                                + " percent must be strictly increasing and within (0,100]: "
                                + percent);
            }
            paints.add(new LegacySkillPaint(
                    percent,
                    readInt(paint, "paintId")));
            previousPercent = cumulativePercent;
            paintIndex++;
        }
        return paints;
    }

    private static List<String> readStringList(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<String> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("resource field " + field + " contains a non-string");
            }
            result.add(element.getAsString());
        }
        return result;
    }

    private static List<List<Integer>> readIntMatrix(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<List<Integer>> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement row : value.getAsJsonArray()) {
            result.add(readIntList(row, field));
        }
        return result;
    }

    private static List<Integer> readIntList(JsonObject object, String field) {
        return readIntList(required(object, field), field);
    }

    private static List<Integer> readIntList(JsonElement value, String field) {
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("resource field " + field + " contains a non-number");
            }
            result.add(element.getAsInt());
        }
        return result;
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("resource field " + field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing resource field " + field);
        }
        return value;
    }

    private static int parseId(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid " + label + " id " + value, exception);
        }
    }

    private static int readInt(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        return value.getAsInt();
    }

    private static int readStrictInt(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be an integer", exception);
        }
    }

    private static long readLong(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        return value.getAsLong();
    }

    private static boolean readBoolean(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("resource field " + field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static String readString(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("resource field " + field + " must be a string");
        }
        return value.getAsString();
    }

    private static String readNullableString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("missing resource field " + field);
        }
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("resource field " + field + " must be a string or null");
        }
        return value.getAsString();
    }

    private static FrameTemplate readFrame(int id, JsonObject value) {
        return new FrameTemplate(id,
                readInt(value, "type"),
                readInt(value, "hp_bar"),
                readInt(value, "chat"),
                readIntList(value, "dead"),
                readIntList(value, "stand"),
                readIntList(value, "run"),
                readInt(value, "fly"),
                readInt(value, "jump"),
                readInt(value, "fall"),
                readInt(value, "injure"),
                readIntMap(value, "action"),
                readInt(value, "dx"),
                readInt(value, "dy"),
                readInt(value, "width"),
                readInt(value, "height"));
    }

    private static Map<Integer, Integer> readIntMap(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("missing Frame object " + field);
        }
        Map<Integer, Integer> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            final int actionId;
            try {
                actionId = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid Frame action id " + entry.getKey(), exception);
            }
            JsonElement icon = entry.getValue();
            if (!icon.isJsonPrimitive() || !icon.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Frame action " + entry.getKey() + " is not numeric");
            }
            result.put(actionId, icon.getAsInt());
        }
        return result;
    }

    public record LegacyPlayerSkill(
            int id,
            List<String> names,
            List<String> descriptions,
            int type,
            boolean proactive,
            List<Integer> icons,
            List<List<Integer>> dx,
            List<List<Integer>> dy,
            int levelRequire,
            int maxLevel,
            int maxUpgrade,
            List<Integer> pointUpgrade,
            List<List<Integer>> coolDown,
            int typeMana,
            List<List<Integer>> mana,
            List<LegacySkillOption> options,
            int level,
            int upgrade,
            int point,
            int cooldownReduction,
            long timeCanUse,
            List<LegacySkillPaint> paints
    ) {
        public LegacyPlayerSkill {
            names = immutableStrings(names);
            descriptions = immutableStrings(descriptions);
            icons = List.copyOf(Objects.requireNonNull(icons, "icons"));
            dx = immutableMatrix(dx);
            dy = immutableMatrix(dy);
            pointUpgrade = List.copyOf(Objects.requireNonNull(pointUpgrade, "pointUpgrade"));
            coolDown = immutableMatrix(coolDown);
            mana = immutableMatrix(mana);
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            paints = List.copyOf(Objects.requireNonNull(paints, "paints"));
        }
    }

    public record LegacySkillOption(
            int id,
            String name,
            List<Integer> normal,
            List<Integer> upgrade
    ) {
        public LegacySkillOption {
            Objects.requireNonNull(name, "name");
            normal = List.copyOf(Objects.requireNonNull(normal, "normal"));
            upgrade = List.copyOf(Objects.requireNonNull(upgrade, "upgrade"));
        }
    }

    public record LegacySkillPaint(String percent, int paintId) {
        public LegacySkillPaint {
            Objects.requireNonNull(percent, "percent");
        }
    }

    public record LegacyLevel(int id, String name, long power) {
        public LegacyLevel {
            Objects.requireNonNull(name, "name");
        }
    }

    public record LegacyEffectImage(
            int id,
            int dx,
            int dy,
            int delay,
            List<Integer> icons
    ) {
        public LegacyEffectImage {
            icons = List.copyOf(Objects.requireNonNull(icons, "icons"));
        }
    }

    private static List<String> immutableStrings(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values, "values"));
    }

    private static List<List<Integer>> immutableMatrix(List<List<Integer>> values) {
        Objects.requireNonNull(values, "values");
        List<List<Integer>> copy = new ArrayList<>(values.size());
        for (List<Integer> row : values) {
            copy.add(List.copyOf(Objects.requireNonNull(row, "matrix row")));
        }
        return List.copyOf(copy);
    }
}

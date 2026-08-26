package com.project.game.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.project.game.frame.FrameTemplate;
import com.project.game.map.LegacyMapTemplate;

import java.io.IOException;
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
    private static final List<Integer> REQUIRED_FRAME_IDS = List.of(3, 4, 5, 6, 7, 8, 21, 22, 23);
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
                if (key != 0) {
                    throw new IllegalArgumentException("MapBootstrap.json contains unsupported map " + key);
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("Map0 must be an object");
                }
                LegacyMapTemplate map = readMap(entry.getValue().getAsJsonObject());
                if (map.id() != key) {
                    throw new IllegalArgumentException("Map0 key/id mismatch");
                }
                validateMap(map);
                if (loaded.put(key, map) != null) {
                    throw new IllegalArgumentException("duplicate Map0 bootstrap");
                }
            }
            if (!loaded.containsKey(0)) {
                throw new IllegalArgumentException("MapBootstrap.json is missing Map0");
            }
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

    private static LegacyMapTemplate readMap(JsonObject value) {
        JsonElement lineValue = value.has("isLine") ? value.get("isLine") : value.get("line");
        if (lineValue == null || lineValue.isJsonNull()
                || !lineValue.isJsonPrimitive() || !lineValue.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Map0 field isLine must be boolean");
        }
        return new LegacyMapTemplate(
                readInt(value, "id"),
                readInt(value, "iconId"),
                readString(value, "name"),
                readInt(value, "row"),
                readInt(value, "column"),
                readString(value, "data"),
                List.copyOf(readIntList(value, "imagesBgr")),
                immutableMatrix(readIntMatrix(value, "colorsBgr")),
                lineValue.getAsBoolean(),
                readNullableString(value, "dataLine"));
    }

    private static void validateMap(LegacyMapTemplate map) {
        if (map.id() != 0) {
            throw new IllegalArgumentException("Map0 id must be 0");
        }
        if (map.row() <= 0 || map.column() <= 0) {
            throw new IllegalArgumentException("Map0 grid dimensions must be positive");
        }
        long expectedLength = (long) map.row() * map.column();
        if (expectedLength > Integer.MAX_VALUE || map.data().length() != expectedLength) {
            throw new IllegalArgumentException("Map0 grid data length must be "
                    + expectedLength + " but was " + map.data().length());
        }
        if (!map.data().chars().allMatch(ch -> ch == '0' || ch == '1')) {
            throw new IllegalArgumentException("Map0 grid data must contain only 0/1");
        }
        if (map.imagesBgr().size() != 3) {
            throw new IllegalArgumentException("Map0 must contain exactly 3 background images");
        }
        if (map.colorsBgr().size() != 4
                || map.colorsBgr().stream().anyMatch(row -> row.size() != 3)) {
            throw new IllegalArgumentException("Map0 colorsBgr must be a 4x3 matrix");
        }
        if (!map.line() && map.dataLine() != null) {
            throw new IllegalArgumentException("Map0 dataLine must be null when isLine is false");
        }
        if (map.line() && map.dataLine() == null) {
            throw new IllegalArgumentException("Map0 line map is missing dataLine");
        }
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

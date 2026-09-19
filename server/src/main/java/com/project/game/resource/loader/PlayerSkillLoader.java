package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.resource.LegacyPlayerSkill;
import com.project.game.resource.LegacySkillOption;
import com.project.game.resource.LegacySkillPaint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlayerSkillLoader {
    private static final Set<Integer> REQUIRED_PLAYER_SKILL_IDS = Set.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            30, 31, 32, 33, 34, 35, 36);
    private static final List<List<Integer>> PLAYER_SKILL_IDS = List.of(
            List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36),
            List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36),
            List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36));

    private PlayerSkillLoader() {
    }

    static Map<Integer, List<LegacyPlayerSkill>> load(Path root, boolean required) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve("PlayerSkillBootstrap.json").normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "PlayerSkillBootstrap.json is not readable below " + normalizedRoot);
            }
            return Map.of();
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "PlayerSkillBootstrap.json");
        JsonObject genderObject = JsonResourceReader.requiredObject(rootObject, "genderSkills");
        JsonObject templateObject = JsonResourceReader.requiredObject(rootObject, "templates");

        Set<Integer> templateIds = new HashSet<>();
        Map<Integer, LegacyPlayerSkill> templates = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : templateObject.entrySet()) {
            int id = JsonResourceReader.parseId(entry.getKey(), "skill template");
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
            throw new IllegalArgumentException(
                    "PlayerSkillBootstrap.json must contain exactly the 25 approved templates");
        }

        Map<Integer, List<LegacyPlayerSkill>> byGender = new HashMap<>();
        for (int gender = 0; gender < PLAYER_SKILL_IDS.size(); gender++) {
            String key = Integer.toString(gender);
            JsonElement idsValue = genderObject.get(key);
            if (idsValue == null || !idsValue.isJsonArray()) {
                throw new IllegalArgumentException("missing gender skill list " + gender);
            }
            List<Integer> expectedIds = JsonResourceReader.readIntList(idsValue, "genderSkills." + key);
            if (!expectedIds.equals(PLAYER_SKILL_IDS.get(gender))) {
                throw new IllegalArgumentException("invalid fresh skill list for gender " + gender);
            }
            List<LegacyPlayerSkill> skills = new ArrayList<>(expectedIds.size());
            for (int id : expectedIds) {
                LegacyPlayerSkill skill = templates.get(id);
                if (skill == null) {
                    throw new IllegalArgumentException("gender " + gender
                            + " references missing skill " + id);
                }
                skills.add(skill);
            }
            byGender.put(gender, List.copyOf(skills));
        }
        return Map.copyOf(byGender);
    }

    private static LegacyPlayerSkill readSkill(JsonObject value) {
        return new LegacyPlayerSkill(
                JsonResourceReader.readInt(value, "id"),
                JsonResourceReader.readStringList(value, "names"),
                JsonResourceReader.readStringList(value, "descriptions"),
                JsonResourceReader.readInt(value, "type"),
                JsonResourceReader.readBoolean(value, "proactive"),
                JsonResourceReader.readIntList(value, "icons"),
                JsonResourceReader.readIntMatrix(value, "dx"),
                JsonResourceReader.readIntMatrix(value, "dy"),
                JsonResourceReader.readInt(value, "levelRequire"),
                JsonResourceReader.readInt(value, "maxLevel"),
                JsonResourceReader.readInt(value, "maxUpgrade"),
                JsonResourceReader.readIntList(value, "pointUpgrade"),
                JsonResourceReader.readIntMatrix(value, "coolDown"),
                JsonResourceReader.readInt(value, "typeMana"),
                JsonResourceReader.readIntMatrix(value, "mana"),
                readOptions(value, "options"),
                JsonResourceReader.readInt(value, "initialLevel"),
                JsonResourceReader.readInt(value, "initialUpgrade"),
                JsonResourceReader.readInt(value, "initialPoint"),
                JsonResourceReader.readInt(value, "initialCooldownReduction"),
                JsonResourceReader.readLong(value, "initialTimeCanUse"),
                readPaints(value, "initialPaints"));
    }

    private static List<LegacySkillOption> readOptions(JsonObject object, String field) {
        JsonElement value = JsonResourceReader.required(object, field);
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
                    JsonResourceReader.readInt(option, "id"),
                    JsonResourceReader.readString(option, "name"),
                    JsonResourceReader.readIntList(option, "normal"),
                    JsonResourceReader.readIntList(option, "upgrade")));
        }
        return options;
    }

    private static List<LegacySkillPaint> readPaints(JsonObject object, String field) {
        JsonElement value = JsonResourceReader.required(object, field);
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
            String percent = JsonResourceReader.readString(paint, "percent");
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
            paints.add(new LegacySkillPaint(percent,
                    JsonResourceReader.readInt(paint, "paintId")));
            previousPercent = cumulativePercent;
            paintIndex++;
        }
        return paints;
    }
}

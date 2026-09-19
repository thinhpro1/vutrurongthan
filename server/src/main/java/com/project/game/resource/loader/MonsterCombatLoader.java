package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.monster.LegacyMonsterCombatTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class MonsterCombatLoader {
    private static final int MONSTER_COMBAT_VERSION = 1;
    private static final Set<Integer> REQUIRED_TEMPLATE_IDS = Set.of(1);

    private MonsterCombatLoader() {
    }

    static Map<Integer, LegacyMonsterCombatTemplate> load(Path root, boolean required) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve("MonsterCombatBootstrap.json").normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "MonsterCombatBootstrap.json is not readable below " + normalizedRoot);
            }
            return Map.of();
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "MonsterCombatBootstrap.json");
        JsonResourceReader.requireExactFields(rootObject, Set.of("version", "templates"),
                "MonsterCombatBootstrap.json");
        if (JsonResourceReader.readStrictInt(rootObject, "version") != MONSTER_COMBAT_VERSION) {
            throw new IllegalArgumentException("MonsterCombatBootstrap.json version must be 1");
        }
        JsonElement templatesValue = JsonResourceReader.required(rootObject, "templates");
        if (!templatesValue.isJsonArray()) {
            throw new IllegalArgumentException(
                    "MonsterCombatBootstrap.json field templates must be an array");
        }

        Set<Integer> ids = new HashSet<>();
        Map<Integer, LegacyMonsterCombatTemplate> loaded = new HashMap<>();
        for (int index = 0; index < templatesValue.getAsJsonArray().size(); index++) {
            JsonElement element = templatesValue.getAsJsonArray().get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "MonsterCombatBootstrap template " + index + " must be an object");
            }
            JsonObject template = element.getAsJsonObject();
            Set<String> fields = template.keySet();
            Set<String> legacyFields = Set.of("templateId", "damage");
            Set<String> rewardFields = Set.of("templateId", "damage", "potentialReward");
            if (!fields.equals(legacyFields) && !fields.equals(rewardFields)) {
                throw new IllegalArgumentException(
                        "MonsterCombatBootstrap template " + index
                                + " must contain fields " + legacyFields + " or " + rewardFields
                                + " but found " + fields);
            }
            int templateId = JsonResourceReader.readStrictInt(template, "templateId");
            if (!ids.add(templateId) || !REQUIRED_TEMPLATE_IDS.contains(templateId)) {
                throw new IllegalArgumentException(
                        "unexpected or duplicate monster combat template " + templateId);
            }
            long damage = JsonResourceReader.readStrictLong(template, "damage");
            if (damage <= 0L) {
                throw new IllegalArgumentException(
                        "MonsterCombatBootstrap template " + templateId
                                + " damage must be positive");
            }
            long potentialReward = template.has("potentialReward")
                    ? JsonResourceReader.readStrictLong(template, "potentialReward")
                    : 0L;
            if (potentialReward < 0L) {
                throw new IllegalArgumentException(
                        "MonsterCombatBootstrap template " + templateId
                                + " potentialReward must be non-negative");
            }
            loaded.put(templateId,
                    new LegacyMonsterCombatTemplate(templateId, damage, potentialReward));
        }
        if (!ids.equals(REQUIRED_TEMPLATE_IDS)) {
            throw new IllegalArgumentException(
                    "MonsterCombatBootstrap.json must contain exactly template 1");
        }
        return Map.copyOf(loaded);
    }
}

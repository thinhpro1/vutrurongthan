package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.resource.LegacyLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LevelLoader {
    private LevelLoader() {
    }

    static List<LegacyLevel> load(Path root, boolean required) {
        Path source = root.toAbsolutePath().normalize().resolve("LevelBootstrap.json").normalize();
        if (!source.startsWith(root.toAbsolutePath().normalize())
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "LevelBootstrap.json is not readable below " + root.toAbsolutePath().normalize());
            }
            return List.of();
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "LevelBootstrap.json");
        JsonElement levelsValue = JsonResourceReader.required(rootObject, "levels");
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
            int id = JsonResourceReader.readInt(levelObject, "id");
            if (id != index) {
                throw new IllegalArgumentException("LevelBootstrap level " + index
                        + " id must be " + index + " but was " + id);
            }
            String name = JsonResourceReader.readString(levelObject, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("LevelBootstrap level " + index
                        + " name must not be blank");
            }
            long power = JsonResourceReader.readLong(levelObject, "power");
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
    }
}

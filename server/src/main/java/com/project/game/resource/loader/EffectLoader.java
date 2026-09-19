package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.resource.LegacyEffectImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class EffectLoader {
    private static final List<Integer> REQUIRED_EFFECT_IMAGE_IDS = List.of(6, 7, 13, 17);

    private EffectLoader() {
    }

    static List<LegacyEffectImage> load(Path root, boolean required) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve("EffectBootstrap.json").normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(
                        "EffectBootstrap.json is not readable below " + normalizedRoot);
            }
            return List.of();
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "EffectBootstrap.json");
        JsonResourceReader.requireExactFields(rootObject, Set.of("version", "images"),
                "EffectBootstrap.json");
        if (JsonResourceReader.readShortValue(rootObject, "version") != 2) {
            throw new IllegalArgumentException("EffectBootstrap.json version must be 2");
        }
        JsonElement imagesValue = JsonResourceReader.required(rootObject, "images");
        if (!imagesValue.isJsonArray()) {
            throw new IllegalArgumentException("EffectBootstrap.json field images must be an array");
        }
        if (imagesValue.getAsJsonArray().size() != REQUIRED_EFFECT_IMAGE_IDS.size()) {
            throw new IllegalArgumentException("EffectBootstrap.json must contain exactly 4 images");
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
            JsonResourceReader.requireExactFields(image,
                    Set.of("id", "dx", "dy", "delay", "icons"),
                    "EffectBootstrap image " + index);
            int id = JsonResourceReader.readShortValue(image, "id");
            int expectedId = REQUIRED_EFFECT_IMAGE_IDS.get(index);
            if (id != expectedId) {
                throw new IllegalArgumentException("EffectBootstrap image " + index
                        + " id must be " + expectedId + " but was " + id);
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate EffectBootstrap image " + id);
            }
            List<Integer> icons = JsonResourceReader.readShortList(image, "icons");
            if (icons.isEmpty()) {
                throw new IllegalArgumentException("EffectBootstrap image " + id
                        + " icons must not be empty");
            }
            if (icons.size() > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("too many icons for EffectBootstrap image " + id);
            }
            int dx = JsonResourceReader.readShortValue(image, "dx");
            int dy = JsonResourceReader.readShortValue(image, "dy");
            int delay = JsonResourceReader.readShortValue(image, "delay");
            if (id == 13
                    && (dx != 0 || dy != 0 || delay != 100
                    || !icons.equals(List.of(971, 972, 973)))) {
                throw new IllegalArgumentException(
                        "EffectBootstrap image 13 does not match canonical melee impact effect");
            }
            if (id == 17
                    && (dx != 0 || dy != -10 || delay != 50
                    || !icons.equals(List.of(1911, 1912, 1913, 1914)))) {
                throw new IllegalArgumentException(
                        "EffectBootstrap image 17 does not match canonical death effect");
            }
            loaded.add(new LegacyEffectImage(id, dx, dy, delay, icons));
        }
        return List.copyOf(loaded);
    }
}

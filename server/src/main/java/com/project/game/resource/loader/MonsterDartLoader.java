package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.monster.MonsterTemplate.Dart;
import com.project.game.monster.MonsterTemplate.Dart.Phase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class MonsterDartLoader {
    private static final String FILE_NAME = "MonsterDartTemplate.json";
    private static final Set<String> DART_FIELDS =
            Set.of("light", "bullet", "explode", "is_meteorite");
    private static final Set<String> PHASE_FIELDS = Set.of("icon", "dx", "dy", "delay");

    private MonsterDartLoader() {
    }

    static List<Dart> load(Path root, boolean required) {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve(FILE_NAME).normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source)
                || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException(FILE_NAME + " is not readable below "
                        + normalizedRoot);
            }
            return List.of();
        }

        JsonObject rootObject = JsonResourceReader.readObject(root, FILE_NAME);
        Map<Integer, Dart> dartsById = new TreeMap<>();
        for (String key : rootObject.keySet()) {
            int id = parseId(key);
            Dart dart = readDart(JsonResourceReader.required(rootObject, key), id);
            if (dartsById.put(id, dart) != null) {
                throw new IllegalArgumentException("duplicate " + FILE_NAME + " dart id " + id);
            }
        }
        return List.copyOf(dartsById.values());
    }

    private static int parseId(String key) {
        if (!key.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException(FILE_NAME
                    + " dart keys must be canonical non-negative decimal ids: " + key);
        }
        try {
            int id = Integer.parseInt(key);
            if (id > Short.MAX_VALUE) {
                throw new IllegalArgumentException(FILE_NAME
                        + " dart id must fit signed short: " + key);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(FILE_NAME
                    + " dart id must fit signed short: " + key, exception);
        }
    }

    private static Dart readDart(JsonElement value, int id) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(FILE_NAME + " dart " + id + " must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        JsonResourceReader.requireExactFields(object, DART_FIELDS,
                FILE_NAME + " dart " + id);
        return new Dart(id,
                JsonResourceReader.readBoolean(object, "is_meteorite"),
                readPhase(object, "light", id),
                readPhase(object, "bullet", id),
                readPhase(object, "explode", id));
    }

    private static Phase readPhase(JsonObject parent, String field, int dartId) {
        JsonObject object = JsonResourceReader.requiredObject(parent, field);
        JsonResourceReader.requireExactFields(object, PHASE_FIELDS,
                FILE_NAME + " dart " + dartId + " " + field);
        List<Integer> icons = readCanonicalIconList(object, "icon");
        if (icons.isEmpty() || icons.size() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(FILE_NAME + " dart " + dartId + " " + field
                    + " must contain 1 to 127 icons");
        }
        int dx = readCanonicalSignedShort(object, "dx");
        int dy = readCanonicalSignedShort(object, "dy");
        int delay = readCanonicalNonNegativeShort(object, "delay");
        return new Phase(icons, dx, dy, delay);
    }

    private static List<Integer> readCanonicalIconList(JsonObject object, String field) {
        JsonElement value = JsonResourceReader.required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            int icon = JsonResourceReader.readCanonicalInt(element, field);
            if (icon < 0 || icon > Short.MAX_VALUE) {
                throw new IllegalArgumentException("resource field " + field
                        + " must contain non-negative signed shorts");
            }
            result.add(icon);
        }
        return List.copyOf(result);
    }

    private static int readCanonicalSignedShort(JsonObject object, String field) {
        int value = JsonResourceReader.readCanonicalInt(
                JsonResourceReader.required(object, field), field);
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("resource field " + field
                    + " must fit signed short: " + value);
        }
        return value;
    }

    private static int readCanonicalNonNegativeShort(JsonObject object, String field) {
        int value = readCanonicalSignedShort(object, field);
        if (value < 0) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be non-negative");
        }
        return value;
    }
}

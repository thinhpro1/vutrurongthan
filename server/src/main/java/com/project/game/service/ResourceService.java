package com.project.game.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.project.game.frame.FrameTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Development resource access for static legacy data such as numeric-ID icons. */
public final class ResourceService {
    private static final List<Integer> REQUIRED_FRAME_IDS = List.of(3, 4, 5, 21, 22, 23);
    private static final ResourceService UNAVAILABLE = new ResourceService(null, null);
    private final Path iconRoot;
    private final List<FrameTemplate> frames;

    private ResourceService(Path iconRoot, Path frameRoot) {
        this.iconRoot = iconRoot == null ? null : iconRoot.toAbsolutePath().normalize();
        this.frames = frameRoot == null ? List.of() : loadFrames(frameRoot);
    }

    public static ResourceService unavailable() {
        return UNAVAILABLE;
    }

    public static ResourceService fromIconRoot(Path iconRoot) {
        return new ResourceService(Objects.requireNonNull(iconRoot, "iconRoot"), null);
    }

    public static ResourceService fromFrameRoot(Path frameRoot) {
        return new ResourceService(null, Objects.requireNonNull(frameRoot, "frameRoot"));
    }

    public static ResourceService fromRoots(Path iconRoot, Path frameRoot) {
        return new ResourceService(iconRoot, frameRoot);
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

    private static int readInt(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric Frame field " + field);
        }
        return value.getAsInt();
    }

    private static List<Integer> readIntList(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("missing Frame array " + field);
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Frame array " + field + " contains a non-number");
            }
            result.add(element.getAsInt());
        }
        return result;
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
}

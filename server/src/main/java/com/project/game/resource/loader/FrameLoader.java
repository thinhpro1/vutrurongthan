package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.resource.FrameTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FrameLoader {
    private static final List<Integer> REQUIRED_FRAME_IDS = List.of(3, 4, 5, 6, 7, 8, 21, 22, 23);

    private FrameLoader() {
    }

    static List<FrameTemplate> load(Path root) {
        JsonObject rootObject = JsonResourceReader.readObject(root, "Frame.json");
        List<FrameTemplate> selected = new ArrayList<>(REQUIRED_FRAME_IDS.size());
        for (int id : REQUIRED_FRAME_IDS) {
            JsonElement value = rootObject.get(Integer.toString(id));
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException("Frame.json is missing required frame " + id);
            }
            selected.add(readFrame(id, value.getAsJsonObject()));
        }
        return List.copyOf(selected);
    }

    private static FrameTemplate readFrame(int id, JsonObject value) {
        return new FrameTemplate(id,
                JsonResourceReader.readInt(value, "type"),
                JsonResourceReader.readInt(value, "hp_bar"),
                JsonResourceReader.readInt(value, "chat"),
                JsonResourceReader.readIntList(value, "dead"),
                JsonResourceReader.readIntList(value, "stand"),
                JsonResourceReader.readIntList(value, "run"),
                JsonResourceReader.readInt(value, "fly"),
                JsonResourceReader.readInt(value, "jump"),
                JsonResourceReader.readInt(value, "fall"),
                JsonResourceReader.readInt(value, "injure"),
                readIntMap(value, "action"),
                JsonResourceReader.readInt(value, "dx"),
                JsonResourceReader.readInt(value, "dy"),
                JsonResourceReader.readInt(value, "width"),
                JsonResourceReader.readInt(value, "height"));
    }

    private static Map<Integer, Integer> readIntMap(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("missing Frame object " + field);
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
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

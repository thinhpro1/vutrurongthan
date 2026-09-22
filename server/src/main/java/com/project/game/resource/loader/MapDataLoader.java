package com.project.game.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.map.MapData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Loads and validates one canonical static map-data file. */
public final class MapDataLoader {
    private static final Set<String> MAP_FIELDS =
            Set.of("terrain", "row", "column", "background", "collision");
    private static final Set<String> BACKGROUND_FIELDS = Set.of("skyColor", "layers");
    private static final Set<String> LAYER_FIELDS = Set.of("image", "fillColor");
    private static final Set<String> GRID_FIELDS = Set.of("type", "data");
    private static final Set<String> LINE_FIELDS = Set.of("type", "lines");
    private static final Set<String> LINE_ENTRY_FIELDS = Set.of("type", "points");

    public static MapData load(Path mapRoot, int dataId) {
        if (dataId < 0 || dataId > Short.MAX_VALUE) {
            throw new IllegalArgumentException("map data id must fit 0..32767: " + dataId);
        }
        JsonObject value = JsonResourceReader.readObject(mapRoot, dataId + ".json");
        JsonResourceReader.requireExactFields(value, MAP_FIELDS, "MapData");

        int terrain = JsonResourceReader.readCanonicalInt(value, "terrain");
        if (terrain < 0 || terrain > Short.MAX_VALUE) {
            throw new IllegalArgumentException("terrain must fit 0..32767: " + terrain);
        }

        int row = JsonResourceReader.readCanonicalInt(value, "row");
        int column = JsonResourceReader.readCanonicalInt(value, "column");
        if (row <= 0 || column <= 0) {
            throw new IllegalArgumentException("row and column must be positive");
        }

        long width = (long) column * MapData.TILE_SIZE;
        long height = (long) row * MapData.TILE_SIZE;
        if (width > Short.MAX_VALUE || height > Short.MAX_VALUE) {
            throw new IllegalArgumentException("derived map dimensions must fit signed short: "
                    + width + "x" + height);
        }

        MapData.Background background = readBackground(value);
        MapData.Collision collision = readCollision(value, row, column, (int) width, (int) height);
        return new MapData(dataId, terrain, row, column, background, collision);
    }

    private static MapData.Background readBackground(JsonObject map) {
        JsonObject background = JsonResourceReader.requiredObject(map, "background");
        JsonResourceReader.requireExactFields(background, BACKGROUND_FIELDS, "MapData background");

        List<Integer> skyColor = readRgb(background.get("skyColor"), "background.skyColor");
        JsonElement layersValue = JsonResourceReader.required(background, "layers");
        if (!layersValue.isJsonArray() || layersValue.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("background.layers must contain exactly 3 layers");
        }

        List<MapData.Layer> layers = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            JsonElement layerValue = layersValue.getAsJsonArray().get(index);
            if (!layerValue.isJsonObject()) {
                throw new IllegalArgumentException("background layer " + index + " must be an object");
            }
            JsonObject layer = layerValue.getAsJsonObject();
            JsonResourceReader.requireExactFields(layer, LAYER_FIELDS,
                    "MapData background layer " + index);
            int image = JsonResourceReader.readCanonicalInt(layer, "image");
            if (image < -1 || image > Short.MAX_VALUE) {
                throw new IllegalArgumentException("background image must be -1..32767: " + image);
            }
            List<Integer> fillColor = readRgb(layer.get("fillColor"),
                    "background.layers[" + index + "].fillColor");
            layers.add(new MapData.Layer(image, fillColor));
        }
        return new MapData.Background(skyColor, layers);
    }

    private static List<Integer> readRgb(JsonElement value, String label) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(label + " must contain exactly 3 components");
        }
        JsonArray components = value.getAsJsonArray();
        List<Integer> color = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int component = JsonResourceReader.readCanonicalInt(
                    components.get(index), label + "[" + index + "]");
            if (component < 0 || component > 255) {
                throw new IllegalArgumentException(label + " component must fit 0..255: " + component);
            }
            color.add(component);
        }
        return List.copyOf(color);
    }

    private static MapData.Collision readCollision(
            JsonObject map, int row, int column, int width, int height) {
        JsonObject collision = JsonResourceReader.requiredObject(map, "collision");
        String type = JsonResourceReader.readString(collision, "type");
        return switch (type) {
            case "GRID" -> readGridCollision(collision, row, column);
            case "LINE" -> readLineCollision(collision, width, height);
            default -> throw new IllegalArgumentException("unsupported collision type " + type);
        };
    }

    private static MapData.Collision readGridCollision(JsonObject collision, int row, int column) {
        JsonResourceReader.requireExactFields(collision, GRID_FIELDS, "MapData GRID collision");
        String data = JsonResourceReader.readString(collision, "data");
        long expectedLength = (long) row * column;
        if (expectedLength > Integer.MAX_VALUE || data.length() != expectedLength) {
            throw new IllegalArgumentException("GRID collision data length must be "
                    + expectedLength + " but was " + data.length());
        }
        if (!data.chars().allMatch(value -> value == '0' || value == '1')) {
            throw new IllegalArgumentException("GRID collision data must contain only 0/1");
        }
        return new MapData.Collision(MapData.CollisionType.GRID, data, List.of());
    }

    private static MapData.Collision readLineCollision(JsonObject collision, int width, int height) {
        JsonResourceReader.requireExactFields(collision, LINE_FIELDS, "MapData LINE collision");
        JsonElement linesValue = JsonResourceReader.required(collision, "lines");
        if (!linesValue.isJsonArray() || linesValue.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("LINE collision lines must be non-empty");
        }

        List<MapData.Line> lines = new ArrayList<>(linesValue.getAsJsonArray().size());
        for (int index = 0; index < linesValue.getAsJsonArray().size(); index++) {
            JsonElement lineValue = linesValue.getAsJsonArray().get(index);
            if (!lineValue.isJsonObject()) {
                throw new IllegalArgumentException("LINE entry " + index + " must be an object");
            }
            JsonObject line = lineValue.getAsJsonObject();
            JsonResourceReader.requireExactFields(line, LINE_ENTRY_FIELDS,
                    "MapData LINE entry " + index);
            String type = JsonResourceReader.readString(line, "type");
            MapData.LineType lineType = switch (type) {
                case "BLOCK" -> MapData.LineType.BLOCK;
                case "PLATFORM" -> MapData.LineType.PLATFORM;
                default -> throw new IllegalArgumentException("unsupported line type " + type);
            };
            List<MapData.Point> points = readPoints(line.get("points"), index, width, height);
            if (lineType == MapData.LineType.BLOCK) {
                if (points.size() < 4) {
                    throw new IllegalArgumentException("BLOCK line must contain at least 4 points");
                }
                if (!points.getFirst().equals(points.getLast())) {
                    throw new IllegalArgumentException("BLOCK line must be explicitly closed");
                }
            } else if (points.size() < 2) {
                throw new IllegalArgumentException("PLATFORM line must contain at least 2 points");
            }
            lines.add(new MapData.Line(lineType, points));
        }
        return new MapData.Collision(MapData.CollisionType.LINE, null, lines);
    }

    private static List<MapData.Point> readPoints(
            JsonElement value, int lineIndex, int width, int height) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("LINE entry " + lineIndex + " points must be an array");
        }
        JsonArray pointValues = value.getAsJsonArray();
        List<MapData.Point> points = new ArrayList<>(pointValues.size());
        for (int index = 0; index < pointValues.size(); index++) {
            JsonElement pointValue = pointValues.get(index);
            if (!pointValue.isJsonArray() || pointValue.getAsJsonArray().size() != 2) {
                throw new IllegalArgumentException("LINE point must contain exactly [x, y]");
            }
            JsonArray point = pointValue.getAsJsonArray();
            int x = JsonResourceReader.readCanonicalInt(point.get(0),
                    "lines[" + lineIndex + "].points[" + index + "][0]");
            int y = JsonResourceReader.readCanonicalInt(point.get(1),
                    "lines[" + lineIndex + "].points[" + index + "][1]");
            if (x < 0 || x > width || y < 0 || y > height) {
                throw new IllegalArgumentException("LINE point is outside map bounds: " + x + "," + y);
            }
            points.add(new MapData.Point(x, y));
        }
        return List.copyOf(points);
    }
}

package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.project.game.map.MapTemplate;
import com.project.game.map.Waypoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class MapLoader {
    private static final Set<Integer> SUPPORTED_MAP_IDS = Set.of(0, 1);

    private MapLoader() {
    }

    static Map<Integer, MapTemplate> load(Path root, boolean required) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve("MapBootstrap.json").normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            if (required) {
                throw new IllegalArgumentException("MapBootstrap.json is not readable below " + normalizedRoot);
            }
            return Map.of();
        }
        JsonObject rootObject = JsonResourceReader.readObject(root, "MapBootstrap.json");
        Map<Integer, MapTemplate> loaded = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
            int key = JsonResourceReader.parseId(entry.getKey(), "map");
            if (!SUPPORTED_MAP_IDS.contains(key)) {
                throw new IllegalArgumentException("MapBootstrap.json contains unsupported map " + key);
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Map" + key + " must be an object");
            }
            MapTemplate map = readMap(entry.getValue().getAsJsonObject());
            if (map.id() != key) {
                throw new IllegalArgumentException("Map" + key + " key/id mismatch");
            }
            validateMap(map);
            if (loaded.put(key, map) != null) {
                throw new IllegalArgumentException("duplicate Map" + key + " bootstrap");
            }
        }
        if (!loaded.keySet().equals(SUPPORTED_MAP_IDS)) {
            throw new IllegalArgumentException("MapBootstrap.json must contain exactly Map0 and Map1");
        }
        validateWaypointTopology(loaded);
        return Collections.unmodifiableMap(loaded);
    }

    private static MapTemplate readMap(JsonObject value) {
        JsonResourceReader.requireExactFields(value,
                Set.of("id", "iconId", "name", "row", "column", "data",
                        "imagesBgr", "colorsBgr", "isLine", "dataLine", "waypoints"),
                "MapBootstrap map");
        JsonElement lineValue = JsonResourceReader.required(value, "isLine");
        if (!lineValue.isJsonPrimitive() || !lineValue.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("MapBootstrap map field isLine must be boolean");
        }
        int mapId = JsonResourceReader.readInt(value, "id");
        return new MapTemplate(
                mapId,
                JsonResourceReader.readInt(value, "iconId"),
                JsonResourceReader.readString(value, "name"),
                JsonResourceReader.readInt(value, "row"),
                JsonResourceReader.readInt(value, "column"),
                JsonResourceReader.readString(value, "data"),
                List.copyOf(JsonResourceReader.readIntList(value, "imagesBgr")),
                immutableMatrix(JsonResourceReader.readIntMatrix(value, "colorsBgr")),
                lineValue.getAsBoolean(),
                JsonResourceReader.readNullableString(value, "dataLine"),
                readWaypoints(value, mapId));
    }

    private static void validateMap(MapTemplate map) {
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

    private static List<Waypoint> readWaypoints(JsonObject mapObject, int ownerMapId) {
        JsonElement value = JsonResourceReader.required(mapObject, "waypoints");
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("Map" + ownerMapId + " waypoints must be an array");
        }
        List<Waypoint> waypoints = new ArrayList<>(value.getAsJsonArray().size());
        Set<Integer> ids = new HashSet<>();
        for (int index = 0; index < value.getAsJsonArray().size(); index++) {
            JsonElement element = value.getAsJsonArray().get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Map" + ownerMapId
                        + " waypoint " + index + " must be an object");
            }
            JsonObject waypoint = element.getAsJsonObject();
            JsonResourceReader.requireExactFields(waypoint,
                    Set.of("id", "goMap", "x", "y", "goX", "goY", "type"),
                    "Map" + ownerMapId + " waypoint " + index);
            int id = JsonResourceReader.readStrictInt(waypoint, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate waypoint id " + id + " in Map" + ownerMapId);
            }
            int goMap = JsonResourceReader.readStrictInt(waypoint, "goMap");
            int x = JsonResourceReader.readShortValue(waypoint, "x");
            int y = JsonResourceReader.readShortValue(waypoint, "y");
            int goX = JsonResourceReader.readShortValue(waypoint, "goX");
            int goY = JsonResourceReader.readShortValue(waypoint, "goY");
            int type = JsonResourceReader.readStrictInt(waypoint, "type");
            if (type < 0 || type > 2) {
                throw new IllegalArgumentException("Map" + ownerMapId
                        + " waypoint " + id + " type must be 0..2");
            }
            waypoints.add(new Waypoint(id, goMap, x, y, goX, goY, type));
        }
        return List.copyOf(waypoints);
    }

    private static void validateWaypointTopology(Map<Integer, MapTemplate> maps) {
        Waypoint map0Waypoint = requireSingleWaypoint(maps.get(0), 2, 1, 1);
        Waypoint map1Waypoint = requireSingleWaypoint(maps.get(1), 3, 0, 0);
        if (map0Waypoint.goMap() != 1 || map1Waypoint.goMap() != 0) {
            throw new IllegalArgumentException("MapBootstrap waypoint topology must be Map0 <-> Map1");
        }
        for (MapTemplate map : maps.values()) {
            for (Waypoint waypoint : map.waypoints()) {
                if (!maps.containsKey(waypoint.goMap())) {
                    throw new IllegalArgumentException("waypoint target map unavailable: "
                            + waypoint.goMap());
                }
            }
        }
    }

    private static Waypoint requireSingleWaypoint(
            MapTemplate map, int id, int goMap, int type) {
        if (map == null || map.waypoints().size() != 1) {
            throw new IllegalArgumentException("Map" + (map == null ? "?" : map.id())
                    + " must contain exactly one supported waypoint");
        }
        Waypoint waypoint = map.waypoints().getFirst();
        if (waypoint.id() != id || waypoint.goMap() != goMap || waypoint.type() != type) {
            throw new IllegalArgumentException("Map" + map.id() + " has unsupported waypoint topology");
        }
        return waypoint;
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

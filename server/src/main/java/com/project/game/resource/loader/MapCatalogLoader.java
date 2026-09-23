package com.project.game.resource.loader;

import com.project.game.map.MapData;
import com.project.game.map.MapTemplate;
import com.project.game.map.Waypoint;
import com.project.game.persistence.map.MapRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Composes validated database map metadata, waypoints, and canonical static map data. */
public final class MapCatalogLoader {
    private static final Set<String> MAP_TYPES = Set.of("ONLINE", "OFFLINE");
    private static final Set<String> PLANETS = Set.of(
            "EARTH", "NAMEK", "SURVIVAL", "FIRE", "COLD", "YARDRAT", "BILL");

    private MapCatalogLoader() {
    }

    public static Map<Integer, MapTemplate> load(MapRepository repository, Path mapDataRoot) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(mapDataRoot, "mapDataRoot");

        List<MapRepository.MapRow> mapRows = requireRows(repository.findAllMaps(), "map rows");
        List<MapRepository.WaypointRow> waypointRows =
                requireRows(repository.findAllWaypoints(), "waypoint rows");

        Map<Integer, MapRepository.MapRow> rowsById = new LinkedHashMap<>();
        for (MapRepository.MapRow row : mapRows) {
            validateMapRow(row);
            if (rowsById.put(row.id(), row) != null) {
                throw new IllegalArgumentException("duplicate map id: " + row.id());
            }
        }

        Map<Integer, MapData> dataById = new HashMap<>();
        Map<Integer, List<Waypoint>> waypointsByMap = new HashMap<>();
        Map<Integer, MapTemplate> enabledMaps = new LinkedHashMap<>();
        for (MapRepository.MapRow row : rowsById.values()) {
            if (!row.enabled()) {
                continue;
            }
            MapData data = dataById.computeIfAbsent(
                    row.data(), dataId -> MapDataLoader.load(mapDataRoot, dataId));
            waypointsByMap.put(row.id(), new ArrayList<>());
            enabledMaps.put(row.id(), new MapTemplate(
                    row.id(), row.name(), row.type(), row.planet(), row.minZone(), row.maxZone(),
                    row.maxPlayer(), row.data(), data, List.of()));
        }

        Set<Integer> waypointIds = new HashSet<>();
        for (MapRepository.WaypointRow row : waypointRows) {
            validateWaypointRow(row);
            if (!waypointIds.add(row.id())) {
                throw new IllegalArgumentException("duplicate waypoint id: " + row.id());
            }

            MapRepository.MapRow owner = rowsById.get(row.mapId());
            if (owner == null) {
                throw new IllegalArgumentException("waypoint owner map does not exist: " + row.mapId());
            }
            MapRepository.MapRow target = rowsById.get(row.goMap());
            if (target == null) {
                throw new IllegalArgumentException("waypoint target map does not exist: " + row.goMap());
            }
            if (!owner.enabled()) {
                continue;
            }
            if (!target.enabled()) {
                throw new IllegalArgumentException(
                        "enabled map waypoint targets disabled map: " + row.goMap());
            }

            MapData ownerData = enabledMaps.get(owner.id()).data();
            MapData targetData = enabledMaps.get(target.id()).data();
            validateCoordinates(row, ownerData, targetData);
            waypointsByMap.get(owner.id()).add(new Waypoint(
                    row.id(), row.goMap(), row.x(), row.y(), row.goX(), row.goY(), row.type()));
        }

        Map<Integer, MapTemplate> composed = new LinkedHashMap<>();
        for (MapRepository.MapRow row : rowsById.values()) {
            if (!row.enabled()) {
                continue;
            }
            MapTemplate loaded = enabledMaps.get(row.id());
            composed.put(row.id(), new MapTemplate(
                    loaded.id(), loaded.name(), loaded.type(), loaded.planet(), loaded.minZone(),
                    loaded.maxZone(), loaded.maxPlayer(), loaded.dataId(), loaded.data(),
                    waypointsByMap.get(row.id())));
        }
        return Collections.unmodifiableMap(composed);
    }

    private static <T> List<T> requireRows(List<T> rows, String label) {
        if (rows == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return rows;
    }

    private static void validateMapRow(MapRepository.MapRow row) {
        if (row == null) {
            throw new IllegalArgumentException("map row must not be null");
        }
        requireRange(row.id(), 0, Short.MAX_VALUE, "map id");
        if (row.name() == null || row.name().isBlank() || row.name().length() > 50) {
            throw new IllegalArgumentException("map name must be non-blank and at most 50 characters");
        }
        if (!MAP_TYPES.contains(row.type())) {
            throw new IllegalArgumentException("unsupported map type: " + row.type());
        }
        if (!PLANETS.contains(row.planet())) {
            throw new IllegalArgumentException("unsupported map planet: " + row.planet());
        }
        requireRange(row.minZone(), 1, 128, "minZone");
        requireRange(row.maxZone(), 1, 128, "maxZone");
        if (row.maxZone() < row.minZone()) {
            throw new IllegalArgumentException("maxZone must be >= minZone");
        }
        requireRange(row.maxPlayer(), 1, 255, "maxPlayer");
        requireRange(row.data(), 0, Short.MAX_VALUE, "map data id");
    }

    private static void validateWaypointRow(MapRepository.WaypointRow row) {
        if (row == null) {
            throw new IllegalArgumentException("waypoint row must not be null");
        }
        if (row.id() <= 0) {
            throw new IllegalArgumentException("waypoint id must be positive: " + row.id());
        }
        requireRange(row.mapId(), 0, Short.MAX_VALUE, "waypoint map id");
        requireRange(row.goMap(), 0, Short.MAX_VALUE, "waypoint target map id");
        requireRange(row.x(), 0, Short.MAX_VALUE, "waypoint x");
        requireRange(row.y(), 0, Short.MAX_VALUE, "waypoint y");
        requireRange(row.goX(), 0, Short.MAX_VALUE, "waypoint goX");
        requireRange(row.goY(), 0, Short.MAX_VALUE, "waypoint goY");
        requireRange(row.type(), 0, 2, "waypoint type");
    }

    private static void validateCoordinates(
            MapRepository.WaypointRow row, MapData ownerData, MapData targetData) {
        if (row.x() > ownerData.width() || row.y() > ownerData.height()) {
            throw new IllegalArgumentException("waypoint owner coordinate is outside map bounds: " + row.id());
        }
        if (row.goX() > targetData.width() || row.goY() > targetData.height()) {
            throw new IllegalArgumentException(
                    "waypoint destination coordinate is outside map bounds: " + row.id());
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " must fit " + minimum + ".." + maximum + ": " + value);
        }
    }

}

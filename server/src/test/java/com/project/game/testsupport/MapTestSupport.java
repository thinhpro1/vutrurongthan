package com.project.game.testsupport;

import com.project.game.map.MapTemplate;
import com.project.game.persistence.map.MapRepository;
import com.project.game.resource.loader.MapCatalogLoader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the canonical Map0/Map1 test catalog through the production loader. */
public final class MapTestSupport {
    private static final Path MAP_ROOT = Path.of("resources", "maps");

    private MapTestSupport() {
    }

    public static Map<Integer, MapTemplate> canonicalMaps() {
        return MapCatalogLoader.load(new Repository(), MAP_ROOT);
    }

    public static Map<Integer, MapTemplate> publicGameplayMaps() {
        Map<Integer, MapTemplate> canonical = canonicalMaps();
        Map<Integer, MapTemplate> maps = new HashMap<>();
        canonical.forEach((id, map) -> maps.put(id, new MapTemplate(
                map.id(), map.name(), map.type(), map.planet(), 2, map.maxZone(),
                map.maxPlayer(), map.dataId(), map.data(), map.waypoints())));
        return maps;
    }

    private static final class Repository implements MapRepository {
        @Override
        public List<MapRow> findAllMaps() {
            return List.of(
                    new MapRow(0, "Núi Paozu", "ONLINE", "EARTH", 1, 3, 40, 1, true),
                    new MapRow(1, "Bờ sông Pu", "ONLINE", "NAMEK", 1, 3, 40, 2, true));
        }

        @Override
        public List<WaypointRow> findAllWaypoints() {
            return List.of(
                    new WaypointRow(2, 0, 4464, 936, 1, 1, 90, 1008),
                    new WaypointRow(3, 1, 0, 1008, 0, 0, 4374, 936));
        }
    }
}

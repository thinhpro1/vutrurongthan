package com.project.game.resource.loader;

import com.project.game.map.MapData;
import com.project.game.map.MapTemplate;
import com.project.game.map.Waypoint;
import com.project.game.persistence.map.MapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCatalogLoaderTest {
    private static final Path PRODUCTION_MAP_ROOT = Path.of("resources", "maps");

    @Test
    void composesEnabledMapsAndPreservesDatabaseMetadataWaypointOrderAndImmutability() {
        FakeMapRepository repository = repository(
                List.of(
                        map(0, "Map0", "ONLINE", "EARTH", 1, 3, 40, 1, true),
                        map(1, "Map1", "OFFLINE", "NAMEK", 2, 4, 50, 2, true)),
                List.of(
                        waypoint(20, 0, 1, 4464, 1440, 1, 4464, 1440),
                        waypoint(21, 0, 1, 10, 20, 0, 30, 40),
                        waypoint(30, 1, 0, 0, 0, 0, 4464, 1440)));

        Map<Integer, MapTemplate> catalog =
                MapCatalogLoader.load(repository, PRODUCTION_MAP_ROOT);

        assertEquals(1, repository.mapCalls);
        assertEquals(1, repository.waypointCalls);
        assertEquals(2, catalog.size());
        assertEquals("Map0", catalog.get(0).name());
        assertEquals("ONLINE", catalog.get(0).type());
        assertEquals("EARTH", catalog.get(0).planet());
        assertEquals(1, catalog.get(0).minZone());
        assertEquals(3, catalog.get(0).maxZone());
        assertEquals(40, catalog.get(0).maxPlayer());
        assertEquals(1, catalog.get(0).dataId());
        assertEquals(1, catalog.get(0).data().id());
        assertEquals(List.of(
                new Waypoint(20, 1, 4464, 1440, 4464, 1440, 1),
                new Waypoint(21, 1, 10, 20, 30, 40, 0)), catalog.get(0).waypoints());
        assertEquals(List.of(new Waypoint(30, 0, 0, 0, 4464, 1440, 0)),
                catalog.get(1).waypoints());

        assertThrows(UnsupportedOperationException.class,
                () -> catalog.put(9, catalog.get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.get(0).waypoints().add(new Waypoint(99, 1, 0, 0, 0, 0, 0)));
    }

    @Test
    void reusesOneMapDataInstanceForSharedDataId() {
        FakeMapRepository repository = repository(
                List.of(
                        map(10, "First", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                        map(11, "Second", "ONLINE", "EARTH", 1, 1, 1, 1, true)),
                List.of());

        Map<Integer, MapTemplate> catalog =
                MapCatalogLoader.load(repository, PRODUCTION_MAP_ROOT);

        assertSame(catalog.get(10).data(), catalog.get(11).data());
    }

    @Test
    void omitsDisabledMapWithoutLoadingItsMissingData() {
        FakeMapRepository repository = repository(
                List.of(
                        map(0, "Enabled", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                        map(99, "Disabled", "OFFLINE", "COLD", 1, 1, 1, 999, false)),
                List.of());

        Map<Integer, MapTemplate> catalog =
                MapCatalogLoader.load(repository, PRODUCTION_MAP_ROOT);

        assertEquals(1, catalog.size());
        assertFalse(catalog.containsKey(99));
    }

    @Test
    void skipsWaypointsOwnedByDisabledMaps() {
        FakeMapRepository repository = repository(
                List.of(
                        map(0, "Enabled", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                        map(1, "Disabled", "OFFLINE", "COLD", 1, 1, 1, 999, false)),
                List.of(waypoint(7, 1, 0, 0, 0, 0, 0, 0)));

        Map<Integer, MapTemplate> catalog =
                MapCatalogLoader.load(repository, PRODUCTION_MAP_ROOT);

        assertEquals(List.of(), catalog.get(0).waypoints());
    }

    @Test
    void rejectsEnabledWaypointToDisabledDestination() {
        FakeMapRepository repository = repository(
                List.of(
                        map(0, "Enabled", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                        map(1, "Disabled", "OFFLINE", "COLD", 1, 1, 1, 999, false)),
                List.of(waypoint(7, 0, 1, 0, 0, 0, 0, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository, PRODUCTION_MAP_ROOT));
    }

    @Test
    void rejectsMapRowsWithInvalidMetadata() {
        List<MapRepository.MapRow> invalidRows = List.of(
                map(-1, "Map", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                map(32768, "Map", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                map(0, "", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                map(0, "   ", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                map(0, "123456789012345678901234567890123456789012345678901", "ONLINE",
                        "EARTH", 1, 1, 1, 1, true),
                map(0, "Map", "PVP", "EARTH", 1, 1, 1, 1, true),
                map(0, "Map", "ONLINE", "MARS", 1, 1, 1, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 0, 1, 1, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 2, 1, 1, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 1, 256, 1, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 1, 1, 0, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 1, 1, 256, 1, true),
                map(0, "Map", "ONLINE", "EARTH", 1, 1, 1, -1, true),
                map(0, "Map", "ONLINE", "EARTH", 1, 1, 1, 32768, true));

        for (MapRepository.MapRow row : invalidRows) {
            assertThrows(IllegalArgumentException.class,
                    () -> MapCatalogLoader.load(repository(List.of(row), List.of()), PRODUCTION_MAP_ROOT),
                    row.toString());
        }

        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(
                        List.of(map(0, "Map", "ONLINE", "EARTH", 1, 1, 1, 1, true),
                                map(0, "Duplicate", "ONLINE", "EARTH", 1, 1, 1, 1, true)),
                        List.of()), PRODUCTION_MAP_ROOT));
    }

    @Test
    void validatesDisabledMapMetadataToo() {
        MapRepository.MapRow disabledWithBlankName =
                map(9, " ", "OFFLINE", "EARTH", 1, 1, 1, 999, false);

        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(List.of(disabledWithBlankName), List.of()),
                        PRODUCTION_MAP_ROOT));
    }

    @Test
    void rejectsMissingAndInvalidEnabledMapData(@TempDir Path root) throws IOException {
        MapRepository.MapRow map = map(0, "Enabled", "ONLINE", "EARTH", 1, 1, 1, 1, true);
        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(List.of(map), List.of()), root));

        Files.writeString(root.resolve("1.json"), "{}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(List.of(map), List.of()), root));
    }

    @Test
    void rejectsEnabledPlatformCollisionBeforeGameplayComposition(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("1.json"), """
                {
                  "terrain": 0,
                  "row": 1,
                  "column": 1,
                  "background": {
                    "skyColor": [0, 0, 0],
                    "layers": [
                      {"image": -1, "fillColor": [0, 0, 0]},
                      {"image": -1, "fillColor": [0, 0, 0]},
                      {"image": -1, "fillColor": [0, 0, 0]}
                    ]
                  },
                  "collision": {
                    "type": "LINE",
                    "lines": [
                      {"type": "PLATFORM", "points": [[0, 0], [72, 0]]}
                    ]
                  }
                }
                """, StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(
                        List.of(map(0, "Platform map", "ONLINE", "EARTH", 1, 1, 1, 1, true)),
                        List.of()), root));

        assertTrue(exception.getMessage().contains("PLATFORM"));
    }

    @Test
    void MapTemplateCopiesWaypointsAndRejectsNullOwnedObjects() {
        MapData data = MapDataLoader.load(PRODUCTION_MAP_ROOT, 1);
        List<Waypoint> waypoints = new ArrayList<>();
        waypoints.add(new Waypoint(1, 0, 0, 0, 0, 0, 0));

        MapTemplate template = new MapTemplate(
                0, "Map0", "ONLINE", "EARTH", 1, 1, 1, 1, data, waypoints);
        waypoints.clear();

        assertEquals(1, template.waypoints().size());
        assertThrows(NullPointerException.class, () -> new MapTemplate(
                0, "Map0", "ONLINE", "EARTH", 1, 1, 1, 1, data,
                new ArrayList<>(List.of((Waypoint) null))));
    }

    @Test
    void rejectsInvalidWaypointRowsAndCoordinates() {
        List<MapRepository.MapRow> maps = validMaps();
        List<MapRepository.WaypointRow> valid =
                List.of(waypoint(1, 0, 1, 0, 0, 0, 0, 0));
        List<List<MapRepository.WaypointRow>> invalidRows = List.of(
                List.of(waypoint(0, 0, 1, 0, 0, 0, 0, 0)),
                List.of(waypoint(1, 99, 1, 0, 0, 0, 0, 0)),
                List.of(waypoint(1, 0, 99, 0, 0, 0, 0, 0)),
                List.of(waypoint(1, 0, 1, -1, 0, 0, 0, 0)),
                List.of(waypoint(1, 0, 1, 32768, 0, 0, 0, 0)),
                List.of(waypoint(1, 0, 1, 4465, 0, 0, 0, 0)),
                List.of(waypoint(1, 0, 1, 0, 1441, 0, 0, 0)),
                List.of(waypoint(1, 0, 1, 0, 0, 4465, 0, 0)),
                List.of(waypoint(1, 0, 1, 0, 0, 0, 4465, 0)),
                List.of(waypoint(1, 0, 1, 0, 0, 0, 0, 1441)),
                List.of(waypoint(1, 0, 1, 0, 0, 3, 0, 0)));

        for (List<MapRepository.WaypointRow> rows : invalidRows) {
            assertThrows(IllegalArgumentException.class,
                    () -> MapCatalogLoader.load(repository(maps, rows), PRODUCTION_MAP_ROOT),
                    rows.getFirst().toString());
        }

        assertThrows(IllegalArgumentException.class,
                () -> MapCatalogLoader.load(repository(maps,
                        List.of(valid.getFirst(), waypoint(1, 1, 0, 0, 0, 0, 0, 0))),
                        PRODUCTION_MAP_ROOT));
    }

    private static List<MapRepository.MapRow> validMaps() {
        return List.of(
                map(0, "Map0", "ONLINE", "EARTH", 1, 3, 40, 1, true),
                map(1, "Map1", "OFFLINE", "NAMEK", 1, 3, 40, 2, true));
    }

    private static MapRepository.MapRow map(
            int id, String name, String type, String planet, int minZone, int maxZone,
            int maxPlayer, int data, boolean enabled) {
        return new MapRepository.MapRow(
                id, name, type, planet, minZone, maxZone, maxPlayer, data, enabled);
    }

    private static MapRepository.WaypointRow waypoint(
            int id, int mapId, int goMap, int x, int y, int type, int goX, int goY) {
        return new MapRepository.WaypointRow(id, mapId, x, y, type, goMap, goX, goY);
    }

    private static FakeMapRepository repository(
            List<MapRepository.MapRow> maps, List<MapRepository.WaypointRow> waypoints) {
        return new FakeMapRepository(maps, waypoints);
    }

    private static final class FakeMapRepository implements MapRepository {
        private final List<MapRow> maps;
        private final List<WaypointRow> waypoints;
        private int mapCalls;
        private int waypointCalls;

        private FakeMapRepository(List<MapRow> maps, List<WaypointRow> waypoints) {
            this.maps = maps;
            this.waypoints = waypoints;
        }

        @Override
        public List<MapRow> findAllMaps() {
            mapCalls++;
            return maps;
        }

        @Override
        public List<WaypointRow> findAllWaypoints() {
            waypointCalls++;
            return waypoints;
        }
    }
}

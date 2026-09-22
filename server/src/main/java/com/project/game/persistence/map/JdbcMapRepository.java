package com.project.game.persistence.map;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcMapRepository implements MapRepository {
    private static final String FIND_ALL_MAPS_SQL =
            "SELECT id, name, type, planet, min_zone, max_zone, max_player, data, enabled "
                    + "FROM map_template ORDER BY id";
    private static final String FIND_ALL_WAYPOINTS_SQL =
            "SELECT id, map_id, x, y, type, go_map, go_x, go_y "
                    + "FROM map_waypoint ORDER BY map_id, id";

    private final DataSource dataSource;

    public JdbcMapRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<MapRow> findAllMaps() {
        List<MapRow> maps = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(FIND_ALL_MAPS_SQL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                maps.add(new MapRow(
                        results.getInt("id"),
                        results.getString("name"),
                        results.getString("type"),
                        results.getString("planet"),
                        results.getInt("min_zone"),
                        results.getInt("max_zone"),
                        results.getInt("max_player"),
                        results.getInt("data"),
                        results.getBoolean("enabled")));
            }
            return List.copyOf(maps);
        } catch (SQLException exception) {
            throw new MapRepositoryException("failed to find map templates", exception);
        }
    }

    @Override
    public List<WaypointRow> findAllWaypoints() {
        List<WaypointRow> waypoints = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(FIND_ALL_WAYPOINTS_SQL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                waypoints.add(new WaypointRow(
                        results.getLong("id"),
                        results.getInt("map_id"),
                        results.getInt("x"),
                        results.getInt("y"),
                        results.getInt("type"),
                        results.getInt("go_map"),
                        results.getInt("go_x"),
                        results.getInt("go_y")));
            }
            return List.copyOf(waypoints);
        } catch (SQLException exception) {
            throw new MapRepositoryException("failed to find map waypoints", exception);
        }
    }
}

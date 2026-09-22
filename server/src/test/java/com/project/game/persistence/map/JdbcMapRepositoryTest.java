package com.project.game.persistence.map;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcMapRepositoryTest {
    @Test
    void mapsAllMapColumnsAndUsesStableIdOrdering() {
        String[] sql = new String[1];
        ResultSet rows = resultSet(List.of(Map.of(
                "id", 7,
                "name", "Bờ sông Pu",
                "type", "ONLINE",
                "planet", "EARTH",
                "min_zone", 1,
                "max_zone", 3,
                "max_player", 40,
                "data", 12,
                "enabled", true)));

        List<MapRepository.MapRow> maps = new JdbcMapRepository(dataSource(sql, rows))
                .findAllMaps();

        assertIterableEquals(List.of(new MapRepository.MapRow(
                7, "Bờ sông Pu", "ONLINE", "EARTH", 1, 3, 40, 12, true)), maps);
        assertEquals("SELECT id, name, type, planet, min_zone, max_zone, max_player, data, enabled "
                + "FROM map_template ORDER BY id", sql[0]);
    }

    @Test
    void mapsAllWaypointColumnsAndUsesStableMapAndIdOrdering() {
        String[] sql = new String[1];
        ResultSet rows = resultSet(List.of(Map.of(
                "id", 3_000_000_001L,
                "map_id", 7,
                "x", 4464,
                "y", 936,
                "type", 1,
                "go_map", 1,
                "go_x", 90,
                "go_y", 1008)));

        List<MapRepository.WaypointRow> waypoints = new JdbcMapRepository(dataSource(sql, rows))
                .findAllWaypoints();

        assertIterableEquals(List.of(new MapRepository.WaypointRow(
                3_000_000_001L, 7, 4464, 936, 1, 1, 90, 1008)), waypoints);
        assertEquals("SELECT id, map_id, x, y, type, go_map, go_x, go_y "
                + "FROM map_waypoint ORDER BY map_id, id", sql[0]);
    }

    @Test
    void wrapsSqlFailureWithoutLeakingSQLException() {
        SQLException failure = new SQLException("database unavailable");

        MapRepositoryException exception = assertThrows(MapRepositoryException.class,
                () -> new JdbcMapRepository(dataSource(new String[1], failure)).findAllMaps());

        assertEquals("failed to find map templates", exception.getMessage());
        assertEquals(failure, exception.getCause());
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        AtomicInteger index = new AtomicInteger(-1);
        return proxy(ResultSet.class, (method, args) -> {
            if (method.getName().equals("next")) {
                return index.incrementAndGet() < rows.size();
            }
            if (method.getName().startsWith("get")) {
                Object value = rows.get(index.get()).get(args[0]);
                return value;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static DataSource dataSource(String[] sql, ResultSet resultSet) {
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
            if (method.getName().equals("executeQuery")) {
                return resultSet;
            }
            return defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                sql[0] = (String) args[0];
                return statement;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(DataSource.class, (method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static DataSource dataSource(String[] sql, SQLException failure) {
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
            if (method.getName().equals("executeQuery")) {
                throw failure;
            }
            return defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                sql[0] = (String) args[0];
                return statement;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(DataSource.class, (method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invoker invoker) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> invoker.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    @FunctionalInterface
    private interface Invoker {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}

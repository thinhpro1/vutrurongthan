package com.project.game.persistence.monster;

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

class JdbcMonsterRepositoryTest {
    @Test
    void mapsAllTemplateScalarsPreservesAnimationJsonAndUsesStableIdOrdering() {
        String[] sql = new String[1];
        String moveIcons = "[11818,11819,11820]";
        String attackIcons = "[11821]";
        String injureIcons = "[11822, 11823]";
        ResultSet rows = resultSet(List.of(Map.ofEntries(
                Map.entry("id", 4),
                Map.entry("name", "Sói"),
                Map.entry("level", 15),
                Map.entry("hp", 5_000_000_001L),
                Map.entry("damage", 4_000_000_002L),
                Map.entry("potential_reward", 9_000_000_003L),
                Map.entry("range_move", 250),
                Map.entry("speed", 12),
                Map.entry("type_move", 1),
                Map.entry("dart_id", 8),
                Map.entry("icon_move", moveIcons),
                Map.entry("icon_attack", attackIcons),
                Map.entry("icon_injure", injureIcons),
                Map.entry("w", 80),
                Map.entry("h", 100))));

        List<MonsterRepository.TemplateRow> templates = new JdbcMonsterRepository(
                dataSource(sql, rows)).findAllTemplates();

        assertIterableEquals(List.of(new MonsterRepository.TemplateRow(
                4, "Sói", 15, 5_000_000_001L, 4_000_000_002L, 9_000_000_003L,
                250, 12, 1, 8, moveIcons, attackIcons, injureIcons, 80, 100)), templates);
        assertEquals("SELECT id, name, level, hp, damage, potential_reward, range_move, speed, "
                + "type_move, dart_id, icon_move, icon_attack, icon_injure, w, h "
                + "FROM monster_template ORDER BY id", sql[0]);
    }

    @Test
    void mapsAllSpawnColumnsAndUsesStableMapAndIdOrdering() {
        String[] sql = new String[1];
        ResultSet rows = resultSet(List.of(Map.of(
                "id", 3_000_000_002L,
                "map_id", 7,
                "monster_id", 4,
                "x", 1250,
                "y", 648)));

        List<MonsterRepository.SpawnRow> spawns = new JdbcMonsterRepository(dataSource(sql, rows))
                .findAllSpawns();

        assertIterableEquals(List.of(new MonsterRepository.SpawnRow(
                3_000_000_002L, 7, 4, 1250, 648)), spawns);
        assertEquals("SELECT id, map_id, monster_id, x, y "
                + "FROM monster_spawn ORDER BY map_id, id", sql[0]);
    }

    @Test
    void wrapsSqlFailureWithoutLeakingSQLException() {
        SQLException failure = new SQLException("database unavailable");

        MonsterRepositoryException exception = assertThrows(MonsterRepositoryException.class,
                () -> new JdbcMonsterRepository(dataSource(new String[1], failure)).findAllTemplates());

        assertEquals("failed to find monster templates", exception.getMessage());
        assertEquals(failure, exception.getCause());
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        AtomicInteger index = new AtomicInteger(-1);
        return proxy(ResultSet.class, (method, args) -> {
            if (method.getName().equals("next")) {
                return index.incrementAndGet() < rows.size();
            }
            if (method.getName().startsWith("get")) {
                return rows.get(index.get()).get(args[0]);
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

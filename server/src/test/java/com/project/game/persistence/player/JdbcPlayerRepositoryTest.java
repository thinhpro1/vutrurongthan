package com.project.game.persistence.player;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcPlayerRepositoryTest {
    @Test
    void startupProbeUsesTableAccessWithoutDeserializingAPlayerRow() throws Exception {
        String[] sql = new String[1];
        DataSource dataSource = dataSource(sql, emptyResultSet());

        JdbcPlayerRepository repository = new JdbcPlayerRepository(dataSource);
        repository.probeTable();

        assertEquals("SELECT 1 FROM player LIMIT 1", sql[0]);
    }

    @Test
    void oversizedJsonIntIsRejectedInsideRepositoryBoundary() {
        AtomicBoolean first = new AtomicBoolean(true);
        ResultSet row = proxy(ResultSet.class, (method, args) -> {
            if (method.getName().equals("next")) {
                return first.getAndSet(false);
            }
            if (method.getName().equals("getString")) {
                return switch ((String) args[0]) {
                    case "name" -> "alpha1";
                    case "base_stats" -> "{\"hp\":200,\"mp\":200,\"damage\":10,\"armor\":0,\"critical\":0,\"dodge\":0,\"constitution\":5,\"speed\":12}";
                    case "current_stats" -> "{\"maxHp\":2147483648,\"maxMp\":200,\"damage\":10,\"armor\":0,\"critical\":0,\"dodge\":0,\"constitution\":5,\"speed\":12}";
                    case "appearance" -> "{\"head\":5,\"body\":6,\"mount\":-1,\"bag\":-1,\"medal\":-1,\"aura\":-1,\"spaceship\":0}";
                    case "position" -> "{\"mapId\":0,\"x\":1250,\"y\":648}";
                    default -> null;
                };
            }
            if (method.getName().equals("getInt")) {
                return switch ((String) args[0]) {
                    case "id" -> 1;
                    case "gender", "level", "diamond", "ruby" -> 0;
                    case "hp", "mp" -> 200;
                    default -> 0;
                };
            }
            if (method.getName().equals("getLong")) {
                return switch ((String) args[0]) {
                    case "account_id" -> 101L;
                    case "power", "potential" -> 1L;
                    case "exp", "coin", "coin_lock" -> 0L;
                    default -> 0L;
                };
            }
            return defaultValue(method.getReturnType());
        });
        String[] sql = new String[1];

        PlayerRepositoryException failure = assertThrows(PlayerRepositoryException.class,
                () -> new JdbcPlayerRepository(dataSource(sql, row)).findByAccountId(101L));

        assertEquals("failed to parse player row", failure.getMessage());
    }

    private static ResultSet emptyResultSet() {
        return proxy(ResultSet.class, (method, args) -> {
            if (method.getName().equals("next")) {
                return false;
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
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}

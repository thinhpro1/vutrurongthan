package com.project.game.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseCatalogSeederTest {
    private static final List<String> CATALOG_TABLES = List.of(
            "map_template", "map_waypoint", "monster_template", "monster_spawn");

    @Test
    void missingSeedFileFailsBeforeOpeningDatabase(@TempDir Path directory) {
        FakeDatabase database = new FakeDatabase();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, directory.resolve("missing.sql")));

        assertTrue(failure.getMessage().contains("catalog seed file"));
        assertTrue(database.events().isEmpty());
    }

    @Test
    void directoryInsteadOfFileFails(@TempDir Path directory) {
        FakeDatabase database = new FakeDatabase();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, directory));

        assertTrue(failure.getMessage().contains("catalog seed file"));
        assertTrue(database.events().isEmpty());
    }

    @Test
    void emptySeedFileFails(@TempDir Path directory) throws Exception {
        Path seed = write(directory, "seed.sql", "-- comments only;\n");
        FakeDatabase database = new FakeDatabase();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, seed));

        assertTrue(failure.getMessage().contains("no executable statements"));
        assertTrue(database.events().isEmpty());
    }

    @Test
    void freshCatalogLocksInspectsAllTablesAndCommitsStatements(@TempDir Path directory)
            throws Exception {
        Path seed = write(directory, "seed.sql", "FIRST; SECOND;");
        FakeDatabase database = new FakeDatabase();

        DatabaseCatalogSeeder.seed(database, seed);

        assertEquals(List.of(
                "GET_LOCK",
                "COUNT_map_template",
                "COUNT_map_waypoint",
                "COUNT_monster_template",
                "COUNT_monster_spawn",
                "SET_AUTOCOMMIT_FALSE",
                "FIRST",
                "SECOND",
                "COMMIT",
                "SET_AUTOCOMMIT_TRUE",
                "RELEASE_LOCK"), database.events());
        assertEquals(List.of("FIRST", "SECOND"), database.executedStatements());
        assertTrue(database.committed());
        assertFalse(database.rolledBack());
    }

    @Test
    void existingCatalogPreservesRowsAndSkipsAllSeedStatements(@TempDir Path directory)
            throws Exception {
        Path seed = write(directory, "seed.sql", "SHOULD_NOT_RUN;");

        for (String existingTable : CATALOG_TABLES) {
            FakeDatabase database = new FakeDatabase();
            database.rowCounts.put(existingTable, 1L);

            DatabaseCatalogSeeder.seed(database, seed);

            assertEquals(List.of("GET_LOCK",
                    "COUNT_map_template",
                    "COUNT_map_waypoint",
                    "COUNT_monster_template",
                    "COUNT_monster_spawn",
                    "RELEASE_LOCK"), database.events(), existingTable);
            assertTrue(database.executedStatements().isEmpty(), existingTable);
            assertFalse(database.autoCommitWasDisabled(), existingTable);
        }
    }

    @Test
    void seedFailureRollsBackDoesNotCommitAndReleasesLock(@TempDir Path directory)
            throws Exception {
        Path seed = write(directory, "seed.sql", "FIRST; SECOND; THIRD;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("SECOND", new SQLException("synthetic seed failure"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, seed));

        assertEquals("database catalog seed failed", failure.getMessage());
        assertEquals(List.of("FIRST", "SECOND"), database.executedStatements());
        assertTrue(database.rolledBack());
        assertFalse(database.committed());
        assertEquals("RELEASE_LOCK", database.events().get(database.events().size() - 1));
    }

    @Test
    void rollbackFailureIsSuppressedUnderSeedFailure(@TempDir Path directory) throws Exception {
        Path seed = write(directory, "seed.sql", "FIRST; FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("FAIL", new SQLException("synthetic seed failure"));
        database.rollbackFailure = new SQLException("synthetic rollback failure");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, seed));

        assertEquals("database catalog seed failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("synthetic rollback failure", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void lockZeroAndNullFailWithoutInspectingOrSeeding(@TempDir Path directory) throws Exception {
        Path seed = write(directory, "seed.sql", "SHOULD_NOT_RUN;");
        for (Integer lockResult : Arrays.asList(0, null)) {
            FakeDatabase database = new FakeDatabase();
            database.lockResult = lockResult;

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> DatabaseCatalogSeeder.seed(database, seed));

            assertTrue(failure.getMessage().contains("could not acquire"));
            assertEquals(List.of("GET_LOCK"), database.events());
            assertTrue(database.executedStatements().isEmpty());
        }
    }

    @Test
    void releaseZeroAndNullFailAfterSuccessfulFreshSeed(@TempDir Path directory)
            throws Exception {
        Path seed = write(directory, "seed.sql", "FIRST;");
        for (Integer releaseResult : Arrays.asList(0, null)) {
            FakeDatabase database = new FakeDatabase();
            database.releaseResult = releaseResult;

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> DatabaseCatalogSeeder.seed(database, seed));

            assertTrue(failure.getMessage().contains("failed to release"));
            assertTrue(database.committed());
            assertEquals(List.of("FIRST"), database.executedStatements());
        }
    }

    @Test
    void seedFailureRemainsPrimaryWhenReleaseFails(@TempDir Path directory) throws Exception {
        Path seed = write(directory, "seed.sql", "FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("FAIL", new SQLException("synthetic seed failure"));
        database.releaseResult = 0;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseCatalogSeeder.seed(database, seed));

        assertEquals("database catalog seed failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("failed to release"));
    }

    @Test
    void errorRemainsPrimaryWhenReleaseFails(@TempDir Path directory) throws Exception {
        Path seed = write(directory, "seed.sql", "FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("FAIL", new AssertionError("synthetic seed error"));
        database.releaseResult = 0;

        AssertionError failure = assertThrows(AssertionError.class,
                () -> DatabaseCatalogSeeder.seed(database, seed));

        assertEquals("synthetic seed error", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("failed to release"));
    }

    @Test
    void baselineSeedPinsCanonicalRowsAndRejectsForbiddenStatements() throws Exception {
        String sql = Files.readString(Path.of("database", "seeds", "baseline_catalog.sql"),
                StandardCharsets.UTF_8);
        List<String> statements = DatabaseMigrator.splitStatements(sql);

        assertEquals(4, statements.size());
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("(0, 'núi paozu', 'online', 'earth', 10, 100, 15, 1, true)"));
        assertTrue(normalized.contains("(1, 'bờ sông pu', 'online', 'earth', 10, 100, 15, 2, true)"));
        assertTrue(normalized.contains("(0, 4464, 936, 1, 1, 90, 1008)"));
        assertTrue(normalized.contains("(1, 0, 1008, 0, 0, 4374, 936)"));
        assertTrue(normalized.contains(
                "(1, 'hổ nanh kiếm', 2, 300, 10, 10, 100, 1, 1, 0, "
                        + "'[11818,11819,11820,11821,11822]', '[11823]', '[11824]', 175, 95)"));
        for (String spawn : List.of(
                "(1, 1, 975, 936)",
                "(1, 1, 1348, 936)",
                "(1, 1, 1800, 936)",
                "(1, 1, 2250, 936)",
                "(1, 1, 2600, 936)",
                "(1, 1, 2950, 936)")) {
            assertTrue(normalized.contains(spawn), spawn);
        }
        for (String forbidden : List.of(
                "UPDATE", "DELETE", "TRUNCATE", "DROP", "ALTER", "REPLACE",
                "ON DUPLICATE KEY", "INSERT IGNORE", "CREATE")) {
            assertFalse(normalized.contains(forbidden.toLowerCase(Locale.ROOT)), forbidden);
        }
    }

    private static Path write(Path directory, String name, String sql) throws Exception {
        Path path = directory.resolve(name);
        Files.writeString(path, sql, StandardCharsets.UTF_8);
        return path;
    }

    private static final class FakeDatabase implements DataSource {
        private final List<String> events = new ArrayList<>();
        private final List<String> executedStatements = new ArrayList<>();
        private final Map<String, Long> rowCounts = new LinkedHashMap<>();
        private Integer lockResult = 1;
        private Integer releaseResult = 1;
        private Throwable releaseFailure;
        private Throwable rollbackFailure;
        private String failingStatement;
        private Throwable statementFailure;
        private boolean committed;
        private boolean rolledBack;
        private boolean autoCommitWasDisabled;
        private boolean autoCommit = true;

        private FakeDatabase() {
            for (String table : CATALOG_TABLES) {
                rowCounts.put(table, 0L);
            }
        }

        List<String> events() {
            return events;
        }

        List<String> executedStatements() {
            return executedStatements;
        }

        boolean committed() {
            return committed;
        }

        boolean rolledBack() {
            return rolledBack;
        }

        boolean autoCommitWasDisabled() {
            return autoCommitWasDisabled;
        }

        void failOnStatement(String statement, Throwable failure) {
            failingStatement = statement;
            statementFailure = failure;
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "prepareStatement" -> preparedStatementProxy((String) arguments[0]);
                        case "createStatement" -> statementProxy();
                        case "setAutoCommit" -> {
                            autoCommit = (Boolean) arguments[0];
                            events.add(autoCommit ? "SET_AUTOCOMMIT_TRUE" : "SET_AUTOCOMMIT_FALSE");
                            if (!autoCommit) {
                                autoCommitWasDisabled = true;
                            }
                            yield null;
                        }
                        case "commit" -> {
                            committed = true;
                            events.add("COMMIT");
                            yield null;
                        }
                        case "rollback" -> {
                            rolledBack = true;
                            events.add("ROLLBACK");
                            if (rollbackFailure != null) {
                                throw rollbackFailure;
                            }
                            yield null;
                        }
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "getAutoCommit" -> autoCommit;
                        case "toString" -> "FakeCatalogSeedConnection";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatementProxy(String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "executeQuery" -> queryResult(sql);
                        case "close", "setString", "setInt", "setLong" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet queryResult(String sql) throws SQLException {
            String upper = sql.toUpperCase(Locale.ROOT);
            if (upper.contains("GET_LOCK")) {
                events.add("GET_LOCK");
                return resultSet(List.<Object[]>of(new Object[]{lockResult}));
            }
            if (upper.contains("RELEASE_LOCK")) {
                events.add("RELEASE_LOCK");
                if (releaseFailure != null) {
                    throwFailure(releaseFailure);
                }
                return resultSet(List.<Object[]>of(new Object[]{releaseResult}));
            }
            for (String table : CATALOG_TABLES) {
                if (upper.contains("FROM " + table.toUpperCase(Locale.ROOT))) {
                    events.add("COUNT_" + table);
                    return resultSet(List.<Object[]>of(new Object[]{rowCounts.get(table)}));
                }
            }
            return resultSet(List.of());
        }

        private static void throwFailure(Throwable failure) throws SQLException {
            if (failure instanceof SQLException exception) {
                throw exception;
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new SQLException("synthetic failure", failure);
        }

        private Statement statementProxy() {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "execute" -> {
                            String sql = ((String) arguments[0]).trim();
                            events.add(sql);
                            executedStatements.add(sql);
                            if (failingStatement != null && sql.contains(failingStatement)) {
                                throw statementFailure;
                            }
                            yield true;
                        }
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSet(List<Object[]> rows) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    new java.lang.reflect.InvocationHandler() {
                        private int index = -1;

                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method,
                                             Object[] arguments) {
                            return switch (method.getName()) {
                                case "next" -> ++index < rows.size();
                                case "getObject" -> rows.get(index)[((Number) arguments[0]).intValue() - 1];
                                case "getInt" -> ((Number) rows.get(index)[((Number) arguments[0]).intValue() - 1]).intValue();
                                case "getLong" -> ((Number) rows.get(index)[((Number) arguments[0]).intValue() - 1]).longValue();
                                case "close" -> null;
                                default -> defaultValue(method.getReturnType());
                            };
                        }
                    });
        }

        @Override
        public Connection getConnection() {
            return connectionProxy();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connectionProxy();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) {
            return null;
        }
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
        throw new IllegalArgumentException("unsupported primitive " + type);
    }
}

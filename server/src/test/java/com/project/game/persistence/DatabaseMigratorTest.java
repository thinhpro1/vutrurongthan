package com.project.game.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigratorTest {
    private static final String GET_LOCK = "GET_LOCK";
    private static final String RELEASE_LOCK = "RELEASE_LOCK";

    @Test
    void sortsNumericVersionsAndAppliesThemInOrder(@TempDir Path directory) throws Exception {
        for (int version = 10; version >= 1; version--) {
            write(directory, "V%03d__version.sql".formatted(version), "VERSION_%d;".formatted(version));
        }
        write(directory, "README.txt", "ignored documentation");
        FakeDatabase database = new FakeDatabase();

        DatabaseMigrator.migrate(database, directory);

        assertEquals(List.of(
                "VERSION_1", "VERSION_2", "VERSION_3", "VERSION_4", "VERSION_5",
                "VERSION_6", "VERSION_7", "VERSION_8", "VERSION_9", "VERSION_10"),
                database.executedStatements());
    }

    @Test
    void rejectsDuplicateVersion(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");
        write(directory, "V001__other.sql", "OTHER;");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory));

        assertTrue(failure.getMessage().contains("duplicate migration version"));
    }

    @Test
    void rejectsVersionGapIndependently(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");
        write(directory, "V003__three.sql", "THREE;");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory));

        assertTrue(failure.getMessage().contains("contiguous starting at 1"));
    }

    @Test
    void rejectsMissingBaselineIndependently(@TempDir Path directory) throws Exception {
        write(directory, "V002__two.sql", "TWO;");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory));

        assertTrue(failure.getMessage().contains("expected 1 but found 2"));
    }

    @Test
    void rejectsMalformedVersionedSqlIndependently(@TempDir Path directory) throws Exception {
        write(directory, "Vbad__broken.sql", "BROKEN;");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory));

        assertTrue(failure.getMessage().contains("malformed migration filename"));
    }

    @Test
    void rejectsMissingDirectoryAndNoMigrations(@TempDir Path directory) throws Exception {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory.resolve("missing")));
        assertTrue(missing.getMessage().contains("migration directory"));

        Files.writeString(directory.resolve("README.txt"), "documentation", StandardCharsets.UTF_8);
        IllegalStateException empty = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(new FakeDatabase(), directory));
        assertTrue(empty.getMessage().contains("no SQL migrations"));
    }

    @Test
    void splitsCommentsQuotesAndEscapedCharacters() {
        String sql = "-- first; comment\n"
                + "CREATE TABLE `semi;table` (value VARCHAR(20) DEFAULT 'a;\\'b');\n"
                + "INSERT INTO \"semi;table\" VALUES (\"quoted;value\");\n"
                + "-- only comment;\n"
                + "SELECT 'it''s; safe', \"also\\\"safe\";";

        assertEquals(List.of(
                "CREATE TABLE `semi;table` (value VARCHAR(20) DEFAULT 'a;\\'b')",
                "INSERT INTO \"semi;table\" VALUES (\"quoted;value\")",
                "SELECT 'it''s; safe', \"also\\\"safe\""),
                DatabaseMigrator.splitStatements(sql));
    }

    @Test
    void rejectsUnterminatedQuotedString() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.splitStatements("SELECT 'unterminated;"));

        assertTrue(failure.getMessage().contains("unterminated quoted string"));
    }

    @Test
    void createsHistoryLocksAppliesAndRecordsChecksum(@TempDir Path directory) throws Exception {
        Path migration = write(directory, "V001__one.sql", "ONE; TWO;");
        FakeDatabase database = new FakeDatabase();

        DatabaseMigrator.migrate(database, directory);

        assertEquals(1, database.lockCalls());
        assertEquals(1, database.releaseCalls());
        assertEquals(List.of("ONE", "TWO"), database.executedStatements());
        assertEquals(List.of(new HistoryRow(1, "one", sha256(Files.readAllBytes(migration)))),
                database.historyRows());
        assertTrue(database.events().indexOf("GET_LOCK") < database.events().indexOf("CREATE_HISTORY"));
        assertTrue(database.events().indexOf("CREATE_HISTORY") < database.events().indexOf("ONE"));
        assertTrue(database.events().indexOf("ONE") < database.events().indexOf("INSERT_HISTORY"));
        assertTrue(database.events().indexOf("INSERT_HISTORY") < database.events().indexOf("RELEASE_LOCK"));
    }

    @Test
    void releaseResultZeroFailsAfterSuccessfulMigration(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");
        FakeDatabase database = new FakeDatabase();
        database.releaseResult = 0;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertTrue(failure.getMessage().contains("failed to release database migration lock"));
        assertEquals(1, database.historyRows().size());
    }

    @Test
    void releaseResultNullFailsAfterSuccessfulMigration(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");
        FakeDatabase database = new FakeDatabase();
        database.releaseResult = null;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertTrue(failure.getMessage().contains("failed to release database migration lock"));
        assertEquals(1, database.historyRows().size());
    }

    @Test
    void migrationFailureRemainsPrimaryWhenReleaseAlsoFails(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE; FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("FAIL");
        database.releaseResult = 0;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertEquals("database migration failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage()
                .contains("failed to release database migration lock"));
    }

    @Test
    void errorRemainsPrimaryWhenReleaseAlsoFails(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE; FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failWithErrorOnStatement("FAIL");
        database.releaseResult = 0;

        AssertionError failure = assertThrows(AssertionError.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertEquals("synthetic migration error", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage()
                .contains("failed to release database migration lock"));
    }

    @Test
    void errorRemainsPrimaryWhenReleaseThrowsError(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE; FAIL;");
        FakeDatabase database = new FakeDatabase();
        database.failWithErrorOnStatement("FAIL");
        database.releaseError = new AssertionError("synthetic release error");

        AssertionError failure = assertThrows(AssertionError.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertEquals("synthetic migration error", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("synthetic release error", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void repeatsAppliedMigrationWithoutExecutingDdl(@TempDir Path directory) throws Exception {
        Path migration = write(directory, "V001__one.sql", "ONE;");
        FakeDatabase database = new FakeDatabase();
        database.addHistory(new HistoryRow(1, "one", sha256(Files.readAllBytes(migration))));

        DatabaseMigrator.migrate(database, directory);

        assertEquals(List.of(), database.executedStatements());
        assertEquals(1, database.historyRows().size());
    }

    @Test
    void rejectsChecksumAndNameMismatch(@TempDir Path directory) throws Exception {
        Path migration = write(directory, "V001__one.sql", "ONE;");
        String checksum = sha256(Files.readAllBytes(migration));

        FakeDatabase checksumDatabase = new FakeDatabase();
        checksumDatabase.addHistory(new HistoryRow(1, "one", "wrong"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(checksumDatabase, directory))
                .getMessage().contains("checksum mismatch"));

        FakeDatabase nameDatabase = new FakeDatabase();
        nameDatabase.addHistory(new HistoryRow(1, "renamed", checksum));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(nameDatabase, directory))
                .getMessage().contains("name mismatch"));
    }

    @Test
    void rejectsAppliedVersionMissingLocally(@TempDir Path directory) throws Exception {
        Path first = write(directory, "V001__one.sql", "ONE;");
        Path second = write(directory, "V002__two.sql", "TWO;");
        FakeDatabase database = new FakeDatabase();
        database.addHistory(new HistoryRow(1, "one", sha256(Files.readAllBytes(first))));
        database.addHistory(new HistoryRow(2, "two", sha256(Files.readAllBytes(second))));
        database.addHistory(new HistoryRow(3, "three", "checksum"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertTrue(failure.getMessage().contains("not present locally"));
    }

    @Test
    void rejectsAppliedHistoryGap(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");
        write(directory, "V002__two.sql", "TWO;");
        FakeDatabase database = new FakeDatabase();
        database.addHistory(new HistoryRow(2, "two", sha256(Files.readAllBytes(
                directory.resolve("V002__two.sql")))));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(database, directory));

        assertTrue(failure.getMessage().contains("history versions must be contiguous"));
    }

    @Test
    void failedStatementDoesNotRecordOrRunLaterMigration(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE; FAIL;");
        write(directory, "V002__two.sql", "TWO;");
        FakeDatabase database = new FakeDatabase();
        database.failOnStatement("FAIL");

        assertThrows(IllegalStateException.class, () -> DatabaseMigrator.migrate(database, directory));

        assertEquals(List.of("ONE", "FAIL"), database.executedStatements());
        assertEquals(List.of(), database.historyRows());
        assertEquals(1, database.releaseCalls());
    }

    @Test
    void lockTimeoutAndNullFailWithoutRunningMigration(@TempDir Path directory) throws Exception {
        write(directory, "V001__one.sql", "ONE;");

        FakeDatabase timeout = new FakeDatabase();
        timeout.lockResult = 0;
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(timeout, directory))
                .getMessage().contains("could not acquire"));
        assertEquals(List.of(), timeout.executedStatements());
        assertEquals(0, timeout.releaseCalls());

        FakeDatabase databaseError = new FakeDatabase();
        databaseError.lockResult = null;
        assertTrue(assertThrows(IllegalStateException.class,
                () -> DatabaseMigrator.migrate(databaseError, directory))
                .getMessage().contains("could not acquire"));
        assertEquals(0, databaseError.releaseCalls());
    }

    @Test
    void baselineMigrationContainsAllRuntimeTablesAndNoSeedRows() throws Exception {
        String sql = Files.readString(Path.of("database", "migrations", "V001__baseline_schema.sql"));
        for (String table : List.of(
                "account", "player", "map_template", "map_waypoint", "monster_template", "monster_spawn")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table));
        }
        assertFalse(sql.contains("INSERT INTO"));
    }

    private static Path write(Path directory, String name, String sql) throws Exception {
        Path path = directory.resolve(name);
        Files.writeString(path, sql, StandardCharsets.UTF_8);
        return path;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private record HistoryRow(int version, String name, String checksum) {
    }

    private static final class FakeDatabase implements DataSource {
        private final List<String> events = new ArrayList<>();
        private final List<String> executedStatements = new ArrayList<>();
        private final List<HistoryRow> historyRows = new ArrayList<>();
        private Integer lockResult = 1;
        private Integer releaseResult = 1;
        private Error releaseError;
        private String failingStatement;
        private String failingErrorStatement;
        private int lockCalls;
        private int releaseCalls;

        @Override
        public Connection getConnection() {
            return connectionProxy();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connectionProxy();
        }

        List<String> events() {
            return events;
        }

        List<String> executedStatements() {
            return executedStatements;
        }

        List<HistoryRow> historyRows() {
            return historyRows;
        }

        int lockCalls() {
            return lockCalls;
        }

        int releaseCalls() {
            return releaseCalls;
        }

        void addHistory(HistoryRow history) {
            historyRows.add(history);
        }

        void failOnStatement(String statement) {
            failingStatement = statement;
        }

        void failWithErrorOnStatement(String statement) {
            failingErrorStatement = statement;
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "prepareStatement" -> preparedStatementProxy((String) arguments[0]);
                        case "createStatement" -> statementProxy();
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "getAutoCommit" -> true;
                        case "toString" -> "FakeDatabaseConnection";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatementProxy(String sql) {
            Object[] parameters = new Object[3];
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "setString", "setInt" -> {
                            parameters[((Number) arguments[0]).intValue() - 1] = arguments[1];
                            yield null;
                        }
                        case "executeQuery" -> queryResult(sql);
                        case "executeUpdate" -> {
                            if (sql.toUpperCase().contains("INSERT INTO SCHEMA_MIGRATION")) {
                                events.add("INSERT_HISTORY");
                                historyRows.add(new HistoryRow(
                                        ((Number) parameters[0]).intValue(),
                                        (String) parameters[1],
                                        (String) parameters[2]));
                            }
                            yield 1;
                        }
                        case "close" -> null;
                        case "toString" -> sql;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet queryResult(String sql) {
            String upper = sql.toUpperCase();
            if (upper.contains(GET_LOCK)) {
                lockCalls++;
                events.add(GET_LOCK);
                List<Object[]> rows = new ArrayList<>();
                rows.add(new Object[]{lockResult});
                return resultSet(rows);
            }
            if (upper.contains(RELEASE_LOCK)) {
                releaseCalls++;
                events.add(RELEASE_LOCK);
                if (releaseError != null) {
                    throw releaseError;
                }
                List<Object[]> rows = new ArrayList<>();
                rows.add(new Object[]{releaseResult});
                return resultSet(rows);
            }
            return resultSet(new ArrayList<>(historyRows));
        }

        private Statement statementProxy() {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "execute" -> {
                            String sql = ((String) arguments[0]).trim();
                            if (sql.toUpperCase().contains("CREATE TABLE IF NOT EXISTS SCHEMA_MIGRATION")) {
                                events.add("CREATE_HISTORY");
                            } else {
                                executedStatements.add(sql);
                                events.add(sql);
                                if (Objects.equals(sql, failingErrorStatement)) {
                                    throw new AssertionError("synthetic migration error");
                                }
                                if (Objects.equals(sql, failingStatement)) {
                                    throw new SQLException("synthetic migration failure");
                                }
                            }
                            yield true;
                        }
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSet(List<?> rows) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    new ResultSetHandler(rows));
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }

        private static Object defaultValue(Class<?> type) {
            if (type == void.class) return null;
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            throw new IllegalArgumentException("unsupported primitive " + type);
        }

        private final class ResultSetHandler implements java.lang.reflect.InvocationHandler {
            private final List<?> rows;
            private int index = -1;

            private ResultSetHandler(List<?> rows) {
                this.rows = rows;
            }

            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments) {
                return switch (method.getName()) {
                    case "next" -> ++index < rows.size();
                    case "getInt" -> rowValue(arguments[0], Integer.class);
                    case "getString" -> rowValue(arguments[0], String.class);
                    case "getObject" -> rowValue(arguments[0], Object.class);
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                };
            }

            private <T> T rowValue(Object column, Class<T> type) {
                Object row = rows.get(index);
                Object value;
                if (row instanceof HistoryRow history) {
                    value = switch (((Number) column).intValue()) {
                        case 1 -> history.version();
                        case 2 -> history.name();
                        case 3 -> history.checksum();
                        default -> throw new IllegalArgumentException("column " + column);
                    };
                } else {
                    value = ((Object[]) row)[0];
                }
                if (type == Integer.class && value == null) return null;
                return type.cast(value);
            }
        }
    }
}

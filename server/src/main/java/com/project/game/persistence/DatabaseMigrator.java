package com.project.game.persistence;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns the in-house, immutable database schema migration history. */
public final class DatabaseMigrator {
    private static final String LOCK_NAME = "rongthan_schema_migration";
    private static final Pattern MIGRATION_FILE =
            Pattern.compile("^V(\\d+)__([^/\\\\]+)\\.sql$");
    private static final String CREATE_HISTORY_TABLE = """
            CREATE TABLE IF NOT EXISTS schema_migration (
                version INT NOT NULL,
                name VARCHAR(128) NOT NULL,
                checksum CHAR(64) NOT NULL,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                PRIMARY KEY (version)
            ) ENGINE=InnoDB
              DEFAULT CHARSET=utf8mb4
              COLLATE=utf8mb4_unicode_ci
            """;

    private DatabaseMigrator() {
    }

    public static void migrate(DataSource dataSource, Path migrationDirectory) {
        Objects.requireNonNull(dataSource, "dataSource");
        List<Migration> migrations = discoverMigrations(migrationDirectory);

        try (Connection connection = dataSource.getConnection()) {
            boolean lockAcquired = false;
            RuntimeException failure = null;
            try {
                Integer lockResult = callLock(connection, "SELECT GET_LOCK(?, ?)", 30);
                if (!Integer.valueOf(1).equals(lockResult)) {
                    throw new IllegalStateException(
                            "could not acquire database migration lock: " + lockResult);
                }
                lockAcquired = true;

                createHistoryTable(connection);
                Map<Integer, AppliedMigration> applied = readAppliedHistory(connection);
                verifyAppliedHistory(applied, migrations);
                for (Migration migration : migrations) {
                    if (!applied.containsKey(migration.version())) {
                        apply(connection, migration);
                    }
                }
            } catch (SQLException exception) {
                failure = new IllegalStateException("database migration failed", exception);
                throw failure;
            } catch (RuntimeException exception) {
                failure = exception;
                throw exception;
            } finally {
                if (lockAcquired) {
                    try {
                        Integer releaseResult = callLock(connection, "SELECT RELEASE_LOCK(?)", null);
                        if (!Integer.valueOf(1).equals(releaseResult)) {
                            throw new IllegalStateException(
                                    "failed to release database migration lock: " + releaseResult);
                        }
                    } catch (SQLException | RuntimeException exception) {
                        RuntimeException releaseFailure = exception instanceof RuntimeException runtime
                                ? runtime
                                : new IllegalStateException(
                                        "failed to release database migration lock", exception);
                        if (failure != null) {
                            failure.addSuppressed(releaseFailure);
                        } else {
                            throw releaseFailure;
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database migration failed", exception);
        }
    }

    static List<Migration> discoverMigrations(Path migrationDirectory) {
        Objects.requireNonNull(migrationDirectory, "migrationDirectory");
        if (!Files.isDirectory(migrationDirectory)) {
            throw new IllegalStateException(
                    "migration directory does not exist or is not a directory: "
                            + migrationDirectory);
        }

        List<Migration> migrations = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(migrationDirectory)) {
            for (Path file : files) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String filename = file.getFileName().toString();
                if (!filename.startsWith("V") || !filename.endsWith(".sql")) {
                    continue;
                }
                Matcher matcher = MIGRATION_FILE.matcher(filename);
                if (!matcher.matches()) {
                    throw new IllegalStateException("malformed migration filename: " + filename);
                }
                long parsedVersion;
                try {
                    parsedVersion = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException(
                            "migration version is too large: " + filename, exception);
                }
                if (parsedVersion < 1 || parsedVersion > Integer.MAX_VALUE) {
                    throw new IllegalStateException(
                            "migration version must be a positive integer: " + filename);
                }
                String name = matcher.group(2);
                if (name.isBlank()) {
                    throw new IllegalStateException("migration name must not be blank: " + filename);
                }
                if (name.length() > 128) {
                    throw new IllegalStateException(
                            "migration name exceeds schema_migration limit: " + filename);
                }
                byte[] bytes = Files.readAllBytes(file);
                migrations.add(new Migration(
                        (int) parsedVersion,
                        name,
                        sha256(bytes),
                        splitStatements(new String(bytes, StandardCharsets.UTF_8))));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "cannot read migration directory: " + migrationDirectory, exception);
        }

        if (migrations.isEmpty()) {
            throw new IllegalStateException(
                    "no SQL migrations found in migration directory: " + migrationDirectory);
        }

        migrations.sort(Comparator.comparingInt(Migration::version));
        Set<Integer> versions = new HashSet<>();
        for (int index = 0; index < migrations.size(); index++) {
            Migration migration = migrations.get(index);
            if (!versions.add(migration.version())) {
                throw new IllegalStateException(
                        "duplicate migration version: " + migration.version());
            }
            int expectedVersion = index + 1;
            if (migration.version() != expectedVersion) {
                throw new IllegalStateException(
                        "migration versions must be contiguous starting at 1; expected "
                                + expectedVersion + " but found " + migration.version());
            }
        }
        return List.copyOf(migrations);
    }

    static List<String> splitStatements(String sql) {
        Objects.requireNonNull(sql, "sql");
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Quote quote = Quote.NONE;

        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (quote == Quote.NONE) {
                if (character == '-' && index + 1 < sql.length()
                        && sql.charAt(index + 1) == '-') {
                    index += 2;
                    while (index < sql.length()
                            && sql.charAt(index) != '\n'
                            && sql.charAt(index) != '\r') {
                        index++;
                    }
                    if (index < sql.length()) {
                        current.append(sql.charAt(index));
                    }
                    continue;
                }
                if (character == '\'') {
                    quote = Quote.SINGLE;
                    current.append(character);
                } else if (character == '"') {
                    quote = Quote.DOUBLE;
                    current.append(character);
                } else if (character == '`') {
                    quote = Quote.BACKTICK;
                    current.append(character);
                } else if (character == ';') {
                    appendStatement(statements, current);
                    current.setLength(0);
                } else {
                    current.append(character);
                }
                continue;
            }

            current.append(character);
            if (character == '\\' && quote != Quote.BACKTICK && index + 1 < sql.length()) {
                current.append(sql.charAt(++index));
                continue;
            }
            char closingCharacter = quote == Quote.SINGLE ? '\''
                    : quote == Quote.DOUBLE ? '"' : '`';
            if (character == closingCharacter) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == closingCharacter) {
                    current.append(sql.charAt(++index));
                } else {
                    quote = Quote.NONE;
                }
            }
        }

        if (quote != Quote.NONE) {
            throw new IllegalStateException("unterminated quoted string in migration SQL");
        }
        appendStatement(statements, current);
        return List.copyOf(statements);
    }

    private static void appendStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(statement);
        }
    }

    private static Integer callLock(Connection connection, String sql, Integer timeout)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LOCK_NAME);
            if (timeout != null) {
                statement.setInt(2, timeout);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Object value = result.getObject(1);
                return value == null ? null : ((Number) value).intValue();
            }
        }
    }

    private static void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_HISTORY_TABLE);
        }
    }

    private static Map<Integer, AppliedMigration> readAppliedHistory(Connection connection)
            throws SQLException {
        Map<Integer, AppliedMigration> applied = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, name, checksum FROM schema_migration ORDER BY version");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                AppliedMigration migration = new AppliedMigration(
                        result.getInt(1), result.getString(2), result.getString(3));
                if (applied.putIfAbsent(migration.version(), migration) != null) {
                    throw new IllegalStateException(
                            "duplicate migration history version: " + migration.version());
                }
            }
        }
        return applied;
    }

    private static void verifyAppliedHistory(
            Map<Integer, AppliedMigration> applied,
            List<Migration> migrations) {
        List<Integer> appliedVersions = new ArrayList<>(applied.keySet());
        appliedVersions.sort(Integer::compareTo);
        for (int index = 0; index < appliedVersions.size(); index++) {
            int expectedVersion = index + 1;
            if (appliedVersions.get(index) != expectedVersion) {
                throw new IllegalStateException(
                        "history versions must be contiguous starting at 1; expected "
                                + expectedVersion + " but found " + appliedVersions.get(index));
            }
        }

        Map<Integer, Migration> local = new HashMap<>();
        for (Migration migration : migrations) {
            local.put(migration.version(), migration);
        }
        for (AppliedMigration appliedMigration : applied.values()) {
            Migration localMigration = local.get(appliedMigration.version());
            if (localMigration == null) {
                throw new IllegalStateException(
                        "applied migration version is not present locally: "
                                + appliedMigration.version());
            }
            if (!localMigration.name().equals(appliedMigration.name())) {
                throw new IllegalStateException(
                        "migration name mismatch for version " + appliedMigration.version());
            }
            if (!localMigration.checksum().equals(appliedMigration.checksum())) {
                throw new IllegalStateException(
                        "migration checksum mismatch for version " + appliedMigration.version());
            }
        }
    }

    private static void apply(Connection connection, Migration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.statements()) {
                statement.execute(sql);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migration (version, name, checksum) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.name());
            statement.setString(3, migration.checksum());
            statement.executeUpdate();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private enum Quote {
        NONE,
        SINGLE,
        DOUBLE,
        BACKTICK
    }

    static record Migration(int version, String name, String checksum, List<String> statements) {
    }

    private record AppliedMigration(int version, String name, String checksum) {
    }
}

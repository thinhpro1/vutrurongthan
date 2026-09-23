package com.project.game.persistence;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/** Seeds the baseline production catalog only when the catalog is completely empty. */
public final class DatabaseCatalogSeeder {
    private static final String LOCK_NAME = "rongthan_catalog_seed";
    private static final List<String> CATALOG_TABLES = List.of(
            "map_template", "map_waypoint", "monster_template", "monster_spawn");

    private DatabaseCatalogSeeder() {
    }

    public static void seed(DataSource dataSource, Path seedFile) {
        Objects.requireNonNull(dataSource, "dataSource");
        List<String> seedStatements = readSeedStatements(seedFile);

        try (Connection connection = dataSource.getConnection()) {
            boolean lockAcquired = false;
            Throwable failure = null;
            try {
                Integer lockResult = callLock(connection, "SELECT GET_LOCK(?, ?)", 30);
                if (!Integer.valueOf(1).equals(lockResult)) {
                    throw new IllegalStateException(
                            "could not acquire database catalog seed lock: " + lockResult);
                }
                lockAcquired = true;

                if (catalogAlreadyExists(connection)) {
                    return;
                }

                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    executeSeed(connection, seedStatements);
                    connection.commit();
                } catch (SQLException exception) {
                    IllegalStateException wrapped = new IllegalStateException(
                            "database catalog seed failed", exception);
                    rollback(connection, wrapped);
                    failure = wrapped;
                    throw wrapped;
                } catch (RuntimeException exception) {
                    rollback(connection, exception);
                    failure = exception;
                    throw exception;
                } catch (Error error) {
                    rollback(connection, error);
                    failure = error;
                    throw error;
                } finally {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException | RuntimeException exception) {
                        RuntimeException restoreFailure = exception instanceof RuntimeException runtime
                                ? runtime
                                : new IllegalStateException(
                                        "failed to restore database auto-commit", exception);
                        if (failure != null) {
                            failure.addSuppressed(restoreFailure);
                        } else {
                            throw restoreFailure;
                        }
                    } catch (Error error) {
                        if (failure != null) {
                            failure.addSuppressed(error);
                        } else {
                            throw error;
                        }
                    }
                }
            } catch (SQLException exception) {
                IllegalStateException wrapped = new IllegalStateException(
                        "database catalog seed failed", exception);
                failure = wrapped;
                throw wrapped;
            } catch (RuntimeException exception) {
                failure = exception;
                throw exception;
            } catch (Error error) {
                failure = error;
                throw error;
            } finally {
                if (lockAcquired) {
                    try {
                        Integer releaseResult = callLock(
                                connection, "SELECT RELEASE_LOCK(?)", null);
                        if (!Integer.valueOf(1).equals(releaseResult)) {
                            throw new IllegalStateException(
                                    "failed to release database catalog seed lock: "
                                            + releaseResult);
                        }
                    } catch (SQLException | RuntimeException exception) {
                        RuntimeException releaseFailure = exception instanceof RuntimeException runtime
                                ? runtime
                                : new IllegalStateException(
                                        "failed to release database catalog seed lock", exception);
                        if (failure != null) {
                            failure.addSuppressed(releaseFailure);
                        } else {
                            throw releaseFailure;
                        }
                    } catch (Error error) {
                        if (failure != null) {
                            failure.addSuppressed(error);
                        } else {
                            throw error;
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database catalog seed failed", exception);
        }
    }

    private static List<String> readSeedStatements(Path seedFile) {
        Objects.requireNonNull(seedFile, "seedFile");
        if (!Files.isRegularFile(seedFile)) {
            throw new IllegalStateException(
                    "catalog seed file does not exist or is not a file: " + seedFile);
        }
        final String sql;
        try {
            sql = Files.readString(seedFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read catalog seed file: " + seedFile, exception);
        }
        List<String> statements = DatabaseMigrator.splitStatements(sql);
        if (statements.isEmpty()) {
            throw new IllegalStateException(
                    "catalog seed file contains no executable statements: " + seedFile);
        }
        return statements;
    }

    private static boolean catalogAlreadyExists(Connection connection) throws SQLException {
        boolean existing = false;
        for (String table : CATALOG_TABLES) {
            if (readCount(connection, table) > 0L) {
                existing = true;
            }
        }
        return existing;
    }

    private static long readCount(Connection connection, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                throw new IllegalStateException("catalog row-count query returned no row: " + table);
            }
            return results.getLong(1);
        }
    }

    private static void executeSeed(Connection connection, List<String> statements)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static void rollback(Connection connection, Throwable primary) {
        try {
            connection.rollback();
        } catch (Throwable rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
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
}

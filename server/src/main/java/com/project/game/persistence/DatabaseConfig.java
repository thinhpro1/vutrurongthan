package com.project.game.persistence;

import java.util.Objects;
import java.util.Properties;

public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String passwordEnvironmentVariable,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMillis) {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/rongthanchibi";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD_ENV = "GAME_DB_PASSWORD";
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    private static final int DEFAULT_MINIMUM_IDLE = 1;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 5_000L;

    public DatabaseConfig {
        jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl").trim();
        username = Objects.requireNonNull(username, "username").trim();
        passwordEnvironmentVariable = Objects.requireNonNull(
                passwordEnvironmentVariable, "passwordEnvironmentVariable").trim();
        if (jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("database jdbcUrl must not be blank");
        }
        if (passwordEnvironmentVariable.isEmpty()) {
            throw new IllegalArgumentException(
                    "database password environment variable must not be blank");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("database maximumPoolSize must be positive");
        }
        if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException(
                    "database minimumIdle must be between zero and maximumPoolSize");
        }
        if (connectionTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "database connectionTimeoutMillis must be positive");
        }
    }

    public static DatabaseConfig defaults() {
        return fromProperties(new Properties());
    }

    public static DatabaseConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        return new DatabaseConfig(
                properties.getProperty("game.db.url", DEFAULT_URL),
                properties.getProperty("game.db.username", DEFAULT_USERNAME),
                properties.getProperty("game.db.password-env", DEFAULT_PASSWORD_ENV),
                integerProperty(properties, "game.db.maximum-pool-size", DEFAULT_MAXIMUM_POOL_SIZE),
                integerProperty(properties, "game.db.minimum-idle", DEFAULT_MINIMUM_IDLE),
                longProperty(properties, "game.db.connection-timeout-ms",
                        DEFAULT_CONNECTION_TIMEOUT_MILLIS));
    }

    private static int integerProperty(Properties properties, String key, int defaultValue) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)).trim());
    }

    private static long longProperty(Properties properties, String key, long defaultValue) {
        return Long.parseLong(properties.getProperty(key, Long.toString(defaultValue)).trim());
    }
}

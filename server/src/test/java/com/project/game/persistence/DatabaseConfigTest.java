package com.project.game.persistence;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {
    @Test
    void parsesConfiguredValuesAndKeepsOnlyPasswordEnvironmentName() {
        Properties properties = new Properties();
        properties.setProperty("game.db.url", "jdbc:mysql://db.example:3306/game");
        properties.setProperty("game.db.username", "game_user");
        properties.setProperty("game.db.password-env", "GAME_DB_PASSWORD");
        properties.setProperty("game.db.allow-empty-password", "true");
        properties.setProperty("game.db.maximum-pool-size", "6");
        properties.setProperty("game.db.minimum-idle", "2");
        properties.setProperty("game.db.connection-timeout-ms", "2500");

        DatabaseConfig config = DatabaseConfig.fromProperties(properties);

        assertEquals("jdbc:mysql://db.example:3306/game", config.jdbcUrl());
        assertEquals("game_user", config.username());
        assertEquals("GAME_DB_PASSWORD", config.passwordEnvironmentVariable());
        assertTrue(config.allowEmptyPassword());
        assertEquals(6, config.maximumPoolSize());
        assertEquals(2, config.minimumIdle());
        assertEquals(2500L, config.connectionTimeoutMillis());
        assertFalse(config.toString().contains("super-secret"));
    }

    @Test
    void usesSafeDefaultsForOptionalPoolSettings() {
        DatabaseConfig config = DatabaseConfig.fromProperties(new Properties());

        assertEquals("jdbc:mysql://localhost:3306/rongthanchibi", config.jdbcUrl());
        assertEquals("root", config.username());
        assertEquals("GAME_DB_PASSWORD", config.passwordEnvironmentVariable());
        assertFalse(config.allowEmptyPassword());
        assertEquals(10, config.maximumPoolSize());
        assertEquals(1, config.minimumIdle());
        assertEquals(5000L, config.connectionTimeoutMillis());
    }

    @Test
    void databaseManagerRejectsBlankUsernameBeforeResolvingPassword() {
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:mysql://localhost:3306/rongthanchibi",
                "  ",
                "UNSET_DATABASE_PASSWORD_FOR_TEST",
                false,
                10,
                1,
                5000L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new DatabaseManager(config));

        assertEquals("database username is not configured", exception.getMessage());
    }

    @Test
    void parsesExplicitFalseForAllowEmptyPassword() {
        Properties properties = new Properties();
        properties.setProperty("game.db.allow-empty-password", "false");

        DatabaseConfig config = DatabaseConfig.fromProperties(properties);

        assertFalse(config.allowEmptyPassword());
    }

    @Test
    void rejectsBlankJdbcUrl() {
        Properties properties = new Properties();
        properties.setProperty("game.db.url", "  ");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromProperties(properties));
    }

    @Test
    void rejectsInvalidPoolSize() {
        Properties properties = new Properties();
        properties.setProperty("game.db.maximum-pool-size", "0");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromProperties(properties));
    }

    @Test
    void rejectsMinimumIdleAboveMaximumPoolSize() {
        Properties properties = new Properties();
        properties.setProperty("game.db.maximum-pool-size", "2");
        properties.setProperty("game.db.minimum-idle", "3");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromProperties(properties));
    }

    @Test
    void rejectsNonPositiveConnectionTimeout() {
        Properties properties = new Properties();
        properties.setProperty("game.db.connection-timeout-ms", "0");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromProperties(properties));
    }
}

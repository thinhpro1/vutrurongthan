package com.project.game.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseManagerTest {
    private static final String PASSWORD_ENV = "DATABASE_MANAGER_TEST_PASSWORD";

    @Test
    void rejectsMissingPasswordEnvironmentVariableWhenEmptyPasswordIsNotAllowed() {
        DatabaseConfig config = config(false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatabaseManager.resolvePassword(config, ignored -> null));

        assertEquals(
                "database password environment variable is not configured: " + PASSWORD_ENV,
                exception.getMessage());
    }

    @Test
    void resolvesEmptyPasswordWhenEnvironmentVariableIsMissingAndAllowed() {
        DatabaseConfig config = config(true);

        String password = DatabaseManager.resolvePassword(config, ignored -> null);

        assertEquals("", password);
    }

    @Test
    void usesPresentEnvironmentValueIncludingEmptyRegardlessOfEmptyPasswordSetting() {
        assertEquals("protected-password",
                DatabaseManager.resolvePassword(config(false), ignored -> "protected-password"));
        assertEquals("",
                DatabaseManager.resolvePassword(config(false), ignored -> ""));
        assertEquals("protected-password",
                DatabaseManager.resolvePassword(config(true), ignored -> "protected-password"));
    }

    private static DatabaseConfig config(boolean allowEmptyPassword) {
        return new DatabaseConfig(
                "jdbc:mysql://localhost:3306/rongthanchibi",
                "root",
                PASSWORD_ENV,
                allowEmptyPassword,
                10,
                1,
                5000L);
    }
}

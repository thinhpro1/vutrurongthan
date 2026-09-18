package com.project.game.persistence.account;

import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAccountRepositoryIntegrationTest {
    @Test
    void persistsAccountAndMapsSuccessfulLoginFields() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("game.db.integration-test"),
                "real MySQL integration test is opt-in");

        Properties properties = loadDatabaseProperties();
        DatabaseConfig config = DatabaseConfig.fromProperties(properties);
        DatabaseManager manager = new DatabaseManager(config);
        String username = "it" + HexFormat.of().formatHex(
                UUID.randomUUID().toString().replace("-", "").getBytes())
                .substring(0, 16);
        byte[] passwordHash = bytes(32, 0x31);
        byte[] passwordSalt = bytes(16, 0x51);

        try {
            JdbcAccountRepository repository = new JdbcAccountRepository(manager.dataSource());
            assertTrue(repository.findByUsername(username).isEmpty());

            long id = repository.create(username, passwordHash, passwordSalt, "2001:db8::1");
            assertTrue(id > 0);

            AccountRecord created = repository.findByUsername(username).orElseThrow();
            assertEquals(id, created.id());
            assertEquals(username, created.username());
            assertArrayEquals(passwordHash, created.passwordHash());
            assertArrayEquals(passwordSalt, created.passwordSalt());
            assertEquals(0, created.role());
            assertFalse(created.locked());
            assertEquals("2001:db8::1", created.ipAddress());
            assertNotNull(created.createdAt());
            assertNotNull(created.updatedAt());
            assertNull(created.lastLoginAt());

            byte[] returnedHash = created.passwordHash();
            returnedHash[0] ^= 0x7f;
            assertNotEquals(returnedHash[0], created.passwordHash()[0]);

            Instant loginAt = Instant.parse("2026-01-02T03:04:05Z");
            repository.updateSuccessfulLogin(id, "192.0.2.10", loginAt);
            AccountRecord updated = repository.findByUsername(username).orElseThrow();
            assertEquals("192.0.2.10", updated.ipAddress());
            assertEquals(loginAt, updated.lastLoginAt());
            assertThrows(DuplicateAccountException.class,
                    () -> repository.create(username, passwordHash, passwordSalt, null));
        } finally {
            try (Connection connection = manager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM account WHERE username = ?")) {
                statement.setString(1, username);
                statement.executeUpdate();
            } finally {
                manager.close();
                manager.close();
            }
        }
    }

    private static Properties loadDatabaseProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = JdbcAccountRepositoryIntegrationTest.class
                .getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        for (String key : new String[]{
                "game.db.url",
                "game.db.username",
                "game.db.password-env",
                "game.db.allow-empty-password",
                "game.db.maximum-pool-size",
                "game.db.minimum-idle",
                "game.db.connection-timeout-ms"}) {
            String override = System.getProperty(key);
            if (override != null) {
                properties.setProperty(key, override);
            }
        }
        return properties;
    }

    private static byte[] bytes(int length, int value) {
        byte[] result = new byte[length];
        java.util.Arrays.fill(result, (byte) value);
        return result;
    }
}

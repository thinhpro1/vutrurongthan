package com.project.game.account;

import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.JdbcAccountRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceDatabaseIntegrationTest {
    @Test
    void accountCredentialSurvivesAuthServiceRestart() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("game.db.integration-test"),
                "real MySQL integration test is opt-in");

        DatabaseManager manager = new DatabaseManager(
                DatabaseConfig.fromProperties(loadDatabaseProperties()));
        JdbcAccountRepository repository = new JdbcAccountRepository(manager.dataSource());
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String password = "secret1";
        try {
            AuthService firstServer = new AuthService(repository);
            AuthService.AuthResult registered =
                    firstServer.register(username, password, "192.0.2.10");
            assertTrue(registered.success());

            AccountRecord created = repository.findByUsername(username).orElseThrow();
            assertEquals(32, created.passwordHash().length);
            assertEquals(16, created.passwordSalt().length);
            assertEquals("192.0.2.10", created.ipAddress());

            AuthService restartedServer = new AuthService(repository);
            AuthService.LoginResult login = restartedServer.login(username, password);
            assertTrue(login.success());
            assertEquals(created.id(), login.accountId());
            assertEquals(username, login.accountName());
            assertTrue(restartedServer.markSuccessfulLogin(
                    login.accountId(), "198.51.100.20").success());

            AccountRecord loggedIn = repository.findByUsername(username).orElseThrow();
            assertEquals("198.51.100.20", loggedIn.ipAddress());
            assertNotNull(loggedIn.lastLoginAt());
            assertFalse(restartedServer.login(username, "secret2").success());
            assertFalse(restartedServer.register(
                    username, password, "203.0.113.30").success());
        } finally {
            try (Connection connection = manager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM account WHERE username = ?")) {
                statement.setString(1, username);
                statement.executeUpdate();
            } finally {
                manager.close();
            }
        }
    }

    private static Properties loadDatabaseProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = AuthServiceDatabaseIntegrationTest.class
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
}

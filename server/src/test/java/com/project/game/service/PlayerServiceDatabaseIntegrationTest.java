package com.project.game.service;

import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.account.JdbcAccountRepository;
import com.project.game.persistence.player.JdbcPlayerRepository;
import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerServiceDatabaseIntegrationTest {
    @Test
    void playerSurvivesRepositoryAndServiceRestart() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("game.db.integration-test"),
                "real MySQL integration test is opt-in");

        DatabaseManager manager = new DatabaseManager(
                DatabaseConfig.fromProperties(loadDatabaseProperties()));
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String playerName = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            JdbcAccountRepository accounts = new JdbcAccountRepository(manager.dataSource());
            long accountId = accounts.create(username, new byte[32], new byte[16], "127.0.0.1");
            JdbcPlayerRepository firstRepository = new JdbcPlayerRepository(manager.dataSource());
            PlayerService first = new PlayerService(firstRepository);
            PlayerProfile created = first.create(accountId, playerName, 0).player();
            PlayerProfile changed = created.withPotential(123).withHp(77)
                    .withLocation(1, 0, 90, 1008);
            assertTrue(first.checkpoint(changed));

            PlayerService restarted = new PlayerService(
                    new JdbcPlayerRepository(manager.dataSource()));
            PlayerProfile loaded = restarted.load(accountId).player();
            assertEquals(changed.id(), loaded.id());
            assertEquals(accountId, loaded.accountId());
            assertEquals(playerName, loaded.name());
            assertEquals(changed.gender(), loaded.gender());
            assertEquals(changed.potential(), loaded.potential());
            assertEquals(changed.hp(), loaded.hp());
            assertEquals(changed.baseStats(), loaded.baseStats());
            assertEquals(changed.currentStats(), loaded.currentStats());
            assertEquals(changed.appearance(), loaded.appearance());
            assertEquals(0, loaded.zoneId());
            assertEquals(changed.mapId(), loaded.mapId());
            assertEquals(changed.x(), loaded.x());
            assertEquals(changed.y(), loaded.y());
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
        try (InputStream input = PlayerServiceDatabaseIntegrationTest.class
                .getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        for (String key : new String[]{
                "game.db.url", "game.db.username", "game.db.password-env",
                "game.db.allow-empty-password", "game.db.maximum-pool-size",
                "game.db.minimum-idle", "game.db.connection-timeout-ms"}) {
            String override = System.getProperty(key);
            if (override != null) {
                properties.setProperty(key, override);
            }
        }
        return properties;
    }
}

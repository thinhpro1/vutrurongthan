package com.project.game.persistence.player;

import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.account.JdbcAccountRepository;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPlayerRepositoryIntegrationTest {
    @Test
    void createsGeneratedIdRejectsDuplicatesAndRoundTripsJson() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("game.db.integration-test"),
                "real MySQL integration test is opt-in");

        DatabaseManager manager = new DatabaseManager(
                DatabaseConfig.fromProperties(loadDatabaseProperties()));
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        try {
            long accountId = new JdbcAccountRepository(manager.dataSource())
                    .create(username, new byte[32], new byte[16], "127.0.0.1");
            Player createdPlayer = Player.create(accountId, "alpha1", 0);
            PlayerRecord initial = PlayerRecord.withoutId(PlayerSaveData.capture(createdPlayer));
            JdbcPlayerRepository repository = new JdbcPlayerRepository(manager.dataSource());
            PlayerRecord created = repository.create(initial);

            assertTrue(created.id() > 0);
            assertEquals(created, repository.findByAccountId(accountId).orElseThrow());
            assertThrows(DuplicatePlayerException.class, () -> repository.create(initial));

            Player persisted = created.toPlayer(0);
            persisted.addPotential(98);
            persisted.injure(17);
            repository.save(PlayerSaveData.capture(persisted));
            PlayerRecord reloaded = repository.findByAccountId(accountId).orElseThrow();
            assertEquals(99, reloaded.potential());
            assertEquals(183, reloaded.hp());
            assertEquals(persisted.mapId(), reloaded.mapId());
            assertEquals(persisted.x(), reloaded.x());
            assertEquals(persisted.y(), reloaded.y());
            assertEquals(0, reloaded.toPlayer(0).zoneId());
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

    @Test
    void sqlValidButDomainInvalidRowBecomesControlledLoadFailure() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("game.db.integration-test"),
                "real MySQL integration test is opt-in");

        DatabaseManager manager = new DatabaseManager(
                DatabaseConfig.fromProperties(loadDatabaseProperties()));
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        try {
            long accountId = new JdbcAccountRepository(manager.dataSource())
                    .create(username, new byte[32], new byte[16], "127.0.0.1");
            try (Connection connection = manager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO player (
                             account_id, name, gender, power, potential, level, exp,
                             base_stats, current_stats, hp, mp, appearance,
                             coin, coin_lock, diamond, ruby, position
                         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {
                int index = 1;
                statement.setLong(index++, accountId);
                statement.setString(index++, "broken1");
                statement.setInt(index++, 0);
                statement.setLong(index++, 1L);
                statement.setLong(index++, 1L);
                statement.setInt(index++, 1);
                statement.setLong(index++, 0L);
                statement.setString(index++, "{\"hp\":200,\"mp\":200,\"damage\":10,\"armor\":0,\"critical\":0,\"dodge\":0,\"constitution\":5,\"speed\":12}");
                statement.setString(index++, "{\"maxHp\":200,\"maxMp\":200,\"damage\":10,\"armor\":0,\"critical\":0,\"dodge\":0,\"constitution\":5,\"speed\":12}");
                statement.setInt(index++, 201);
                statement.setInt(index++, 200);
                statement.setString(index++, "{\"head\":5,\"body\":6,\"mount\":-1,\"bag\":-1,\"medal\":-1,\"aura\":-1,\"spaceship\":0}");
                statement.setLong(index++, 0L);
                statement.setLong(index++, 10_000L);
                statement.setInt(index++, 0);
                statement.setInt(index++, 25);
                statement.setString(index, "{\"mapId\":0,\"x\":1250,\"y\":648}");
                statement.executeUpdate();
            }

            assertThrows(PlayerRepositoryException.class,
                    () -> new JdbcPlayerRepository(manager.dataSource()).findByAccountId(accountId));
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
        try (InputStream input = JdbcPlayerRepositoryIntegrationTest.class
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

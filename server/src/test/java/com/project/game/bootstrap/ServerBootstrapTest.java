package com.project.game.bootstrap;

import com.project.game.account.AuthService;
import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.NetworkServer;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.service.ServerServices;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.TestAccountRepository;
import com.project.game.testsupport.TestServices;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerBootstrapTest {
    private static final String TEST_JDBC_URL = "jdbc:server-bootstrap-test:unused";

    static {
        try {
            DriverManager.registerDriver(new LifecycleTestJdbcDriver());
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Test
    void overlaysOnlySupportedGameSystemPropertyNamespaces() {
        String networkKey = "game.network.port";
        String resourceKey = "game.resource.image-version";
        String dbKey = "game.db.url";
        String clientKey = "game.client.version";
        String securityKey = "game.security.mode";
        Properties previous = new Properties();
        for (String key : new String[]{networkKey, resourceKey, dbKey, clientKey, securityKey}) {
            String value = System.getProperty(key);
            if (value != null) {
                previous.setProperty(key, value);
            }
        }
        try {
            System.setProperty(networkKey, "1708");
            System.setProperty(resourceKey, "3");
            System.setProperty(dbKey, "jdbc:mysql://override/rongthanchibi");
            System.setProperty(clientKey, "override-client");
            System.setProperty(securityKey, "override-security");
            Properties properties = new Properties();
            properties.setProperty(networkKey, "1707");
            properties.setProperty(resourceKey, "2");
            properties.setProperty(dbKey, "jdbc:mysql://baseline/rongthanchibi");
            properties.setProperty(clientKey, "baseline-client");
            properties.setProperty(securityKey, "baseline-security");

            ServerBootstrap.overlaySystemProperties(properties);

            assertEquals("1708", properties.getProperty(networkKey));
            assertEquals("3", properties.getProperty(resourceKey));
            assertEquals("jdbc:mysql://override/rongthanchibi", properties.getProperty(dbKey));
            assertEquals("baseline-client", properties.getProperty(clientKey));
            assertEquals("baseline-security", properties.getProperty(securityKey));
        } finally {
            for (String key : new String[]{networkKey, resourceKey, dbKey, clientKey, securityKey}) {
                restoreProperty(key, previous.getProperty(key));
            }
        }
    }

    @Test
    void closesDatabaseManagerWhenBootstrapCompositionFailsAfterDatabaseCreation() {
        Properties properties = new Properties();
        properties.setProperty("game.resource.icon-dir", "");
        properties.setProperty("game.resource.json-dir", "");
        properties.setProperty("game.resource.image-version", "not-a-number");
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        Supplier<DatabaseManager> managerFactory = () -> {
            DatabaseManager manager = databaseManager();
            createdManager.set(manager);
            return manager;
        };

        assertThrows(NumberFormatException.class,
                () -> ServerBootstrap.fromProperties(properties, managerFactory));

        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void closesDatabaseManagerWhenNetworkServerStartFails() throws Exception {
        DatabaseManager manager = databaseManager();
        AtomicBoolean checkpointCalled = new AtomicBoolean();
        AtomicBoolean checkpointSawOpenDatabase = new AtomicBoolean();
        PlayerRepository repository = new PlayerRepository() {
            @Override
            public Optional<PlayerRecord> findByAccountId(long accountId) {
                return Optional.empty();
            }

            @Override
            public PlayerRecord create(PlayerRecord initialWithoutId) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public void updateCheckpoint(PlayerProfile player, Instant playedAt) {
                checkpointCalled.set(true);
                checkpointSawOpenDatabase.set(!isClosed(manager));
            }
        };
        ServerServices services = servicesWithPlayerRepository(repository);
        try (ServerSocket occupied = new ServerSocket(0)) {
            NetworkServer server = networkServer("127.0.0.1", occupied.getLocalPort(), services);
            Session session = addPlayerSession(server, services);
            ServerBootstrap bootstrap = new ServerBootstrap(server, manager);

            assertThrows(IOException.class, bootstrap::start);

            assertEquals(0, server.localPort());
            assertEquals(SessionState.CLOSED, session.state());
            assertTrue(checkpointCalled.get());
            assertTrue(checkpointSawOpenDatabase.get());
            assertTrue(isClosed(manager));
        }
    }

    @Test
    void stopsSessionsBeforeClosingDatabaseManager() {
        DatabaseManager manager = databaseManager();
        AtomicBoolean checkpointCalled = new AtomicBoolean();
        AtomicBoolean checkpointSawOpenDatabase = new AtomicBoolean();
        PlayerRepository repository = new PlayerRepository() {
            @Override
            public Optional<PlayerRecord> findByAccountId(long accountId) {
                return Optional.empty();
            }

            @Override
            public PlayerRecord create(PlayerRecord initialWithoutId) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public void updateCheckpoint(PlayerProfile player, Instant playedAt) {
                checkpointCalled.set(true);
                checkpointSawOpenDatabase.set(!isClosed(manager));
            }
        };
        ServerServices services = servicesWithPlayerRepository(repository);
        NetworkServer server = networkServer("127.0.0.1", 0, services);
        Session session = addPlayerSession(server, services);
        ServerBootstrap bootstrap = new ServerBootstrap(server, manager);

        bootstrap.stop();

        assertTrue(checkpointCalled.get());
        assertTrue(checkpointSawOpenDatabase.get());
        assertTrue(isClosed(manager));
    }

    @Test
    void repeatedStopIsHarmless() {
        DatabaseManager manager = databaseManager();
        AtomicInteger checkpointCount = new AtomicInteger();
        PlayerRepository repository = new PlayerRepository() {
            @Override
            public Optional<PlayerRecord> findByAccountId(long accountId) {
                return Optional.empty();
            }

            @Override
            public PlayerRecord create(PlayerRecord initialWithoutId) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public void updateCheckpoint(PlayerProfile player, Instant playedAt) {
                checkpointCount.incrementAndGet();
            }
        };
        ServerServices services = servicesWithPlayerRepository(repository);
        NetworkServer server = networkServer("127.0.0.1", 0, services);
        Session session = addPlayerSession(server, services);
        ServerBootstrap bootstrap = new ServerBootstrap(server, manager);

        bootstrap.stop();

        assertDoesNotThrow(bootstrap::stop);
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(1, checkpointCount.get());
        assertTrue(isClosed(manager));
    }

    private static ServerServices servicesWithPlayerRepository(PlayerRepository repository) {
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        return TestServices.serverServices(
                new AuthService(new TestAccountRepository()), resources, gameplay,
                new PlayerService(repository));
    }

    private static Session addPlayerSession(NetworkServer server, ServerServices services) {
        SessionManager sessions = server.sessions();
        Session session = new Session(sessions.nextId(), new TestTransport(), sessions,
                new LegacyPacketCodec(1024), "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                4, services, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        assertTrue(sessions.tryAdd(session, 1));
        assertTrue(sessions.beginAccountAdmission(session, 10L, "alpha1"));
        sessions.finishAccountAdmission(session, true);
        session.bindPlayer(PlayerProfile.initial(10L, 1, "alpha1", 0));
        return session;
    }

    private static NetworkServer networkServer(String host, int port, ServerServices services) {
        return new NetworkServer(host, port, 20, 1024, 8, 1000,
                "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII), services, null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
    }

    private static DatabaseManager databaseManager() {
        Properties properties = new Properties();
        properties.setProperty("game.db.url", TEST_JDBC_URL);
        properties.setProperty("game.db.username", "test");
        properties.setProperty("game.db.password-env", "SERVER_BOOTSTRAP_TEST_PASSWORD_UNSET");
        properties.setProperty("game.db.allow-empty-password", "true");
        properties.setProperty("game.db.maximum-pool-size", "1");
        properties.setProperty("game.db.minimum-idle", "0");
        return new DatabaseManager(DatabaseConfig.fromProperties(properties));
    }

    private static boolean isClosed(DatabaseManager manager) {
        return manager != null && ((HikariDataSource) manager.dataSource()).isClosed();
    }

    private static final class TestTransport implements ClientTransport {
        @Override
        public ByteArrayInputStream input() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public ByteArrayOutputStream output() {
            return new ByteArrayOutputStream();
        }

        @Override
        public String remoteAddress() {
            return "127.0.0.1";
        }

        @Override
        public void close() {
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class LifecycleTestJdbcDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            AtomicBoolean closed = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "close" -> {
                            closed.set(true);
                            yield null;
                        }
                        case "isClosed" -> closed.get();
                        case "isValid" -> true;
                        case "getAutoCommit" -> true;
                        case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                        case "equals" -> proxy == arguments[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "LifecycleTestConnection";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:server-bootstrap-test:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        throw new IllegalArgumentException("unsupported primitive " + type);
    }
}

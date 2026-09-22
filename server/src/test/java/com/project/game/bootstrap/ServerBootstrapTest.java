package com.project.game.bootstrap;
import com.project.game.testsupport.TestPlayerProfiles;

import com.project.game.account.AuthService;
import com.project.game.network.ClientConfig;
import com.project.game.network.NetworkServer;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.map.MapRepository;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String monsterResourceKey = "game.resource.monster-version";
        String dbKey = "game.db.url";
        String clientKey = "game.client.version";
        Properties previous = new Properties();
        for (String key : new String[]{networkKey, resourceKey, monsterResourceKey, dbKey, clientKey}) {
            String value = System.getProperty(key);
            if (value != null) {
                previous.setProperty(key, value);
            }
        }
        try {
            System.setProperty(networkKey, "1708");
            System.setProperty(resourceKey, "3");
            System.setProperty(monsterResourceKey, "4");
            System.setProperty(dbKey, "jdbc:mysql://override/rongthanchibi");
            System.setProperty(clientKey, "override-client");
            Properties properties = new Properties();
            properties.setProperty(networkKey, "1707");
            properties.setProperty(resourceKey, "2");
            properties.setProperty(monsterResourceKey, "2");
            properties.setProperty(dbKey, "jdbc:mysql://baseline/rongthanchibi");
            properties.setProperty(clientKey, "baseline-client");

            ServerBootstrap.overlaySystemProperties(properties);

            assertEquals("1708", properties.getProperty(networkKey));
            assertEquals("3", properties.getProperty(resourceKey));
            assertEquals("4", properties.getProperty(monsterResourceKey));
            assertEquals("jdbc:mysql://override/rongthanchibi", properties.getProperty(dbKey));
            assertEquals("baseline-client", properties.getProperty(clientKey));
        } finally {
            for (String key : new String[]{networkKey, resourceKey, monsterResourceKey, dbKey, clientKey}) {
                restoreProperty(key, previous.getProperty(key));
            }
        }
    }

    @Test
    void rejectsInvalidConfiguredMonsterVersion() {
        for (String configured : new String[]{null, "", "0", "128", "not-an-integer"}) {
            Properties properties = startupProperties();
            if (configured == null) {
                properties.remove("game.resource.monster-version");
            } else {
                properties.setProperty("game.resource.monster-version", configured);
            }
            assertThrows(IllegalStateException.class, () -> ServerBootstrap.fromProperties(
                    properties, ServerBootstrapTest::databaseManager,
                    ignored -> mapRepository(canonicalMapRows())));
        }
    }

    @Test
    void closesDatabaseManagerWhenBootstrapCompositionFailsAfterDatabaseCreation() {
        Properties properties = startupProperties();
        properties.setProperty("game.resource.image-version", "not-a-number");
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        Supplier<DatabaseManager> managerFactory = () -> {
            DatabaseManager manager = databaseManager();
            createdManager.set(manager);
            return manager;
        };

        assertThrows(NumberFormatException.class, () -> ServerBootstrap.fromProperties(
                properties, managerFactory, ignored -> mapRepository(canonicalMapRows())));

        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void loadsMapCatalogBeforeReturningBootstrapAndLeavesNetworkStartExplicit() {
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        ServerBootstrap bootstrap = ServerBootstrap.fromProperties(
                startupProperties(), () -> {
                    DatabaseManager manager = databaseManager();
                    createdManager.set(manager);
                    return manager;
                }, ignored -> mapRepository(canonicalMapRows()));

        assertFalse(isClosed(createdManager.get()));

        bootstrap.stop();
        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void emptyEnabledMapCatalogFailsBeforeNetworkConstructionAndClosesDatabase() {
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ServerBootstrap.fromProperties(
                        startupProperties(), () -> {
                            DatabaseManager manager = databaseManager();
                            createdManager.set(manager);
                            return manager;
                        }, ignored -> mapRepository(List.of())));

        assertEquals("enabled map catalog is empty", failure.getMessage());
        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void missingEnabledMapZeroFailsBeforeNetworkConstructionAndClosesDatabase() {
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ServerBootstrap.fromProperties(
                        startupProperties(), () -> {
                            DatabaseManager manager = databaseManager();
                            createdManager.set(manager);
                            return manager;
                        }, ignored -> mapRepository(List.of(
                                new MapRepository.MapRow(
                                        1, "Bờ sông Pu", "OFFLINE", "NAMEK", 1, 3, 40, 2, true)))));

        assertEquals("enabled map 0 is required", failure.getMessage());
        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void mapCatalogFailureClosesDatabaseBeforeNetworkConstruction() {
        AtomicReference<DatabaseManager> createdManager = new AtomicReference<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ServerBootstrap.fromProperties(
                        startupProperties(), () -> {
                            DatabaseManager manager = databaseManager();
                            createdManager.set(manager);
                            return manager;
                        }, ignored -> new MapRepository() {
                            @Override
                            public List<MapRow> findAllMaps() {
                                throw new IllegalStateException("map catalog failure");
                            }

                            @Override
                            public List<WaypointRow> findAllWaypoints() {
                                return List.of();
                            }
                        }));

        assertEquals("map catalog failure", failure.getMessage());
        assertTrue(isClosed(createdManager.get()));
    }

    @Test
    void honorsConfiguredMapDataDirectory(@org.junit.jupiter.api.io.TempDir Path mapRoot)
            throws Exception {
        Files.copy(Path.of("resources", "maps", "1.json"), mapRoot.resolve("1.json"));
        Properties properties = startupProperties();
        properties.setProperty("game.resource.map-dir", mapRoot.toString());

        ServerBootstrap bootstrap = ServerBootstrap.fromProperties(
                properties, ServerBootstrapTest::databaseManager,
                ignored -> mapRepository(canonicalMapRows()));

        assertDoesNotThrow(bootstrap::stop);
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
        SessionServices services = servicesWithPlayerRepository(repository);
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
        SessionServices services = servicesWithPlayerRepository(repository);
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
        SessionServices services = servicesWithPlayerRepository(repository);
        NetworkServer server = networkServer("127.0.0.1", 0, services);
        Session session = addPlayerSession(server, services);
        ServerBootstrap bootstrap = new ServerBootstrap(server, manager);

        bootstrap.stop();

        assertDoesNotThrow(bootstrap::stop);
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(1, checkpointCount.get());
        assertTrue(isClosed(manager));
    }

    private static SessionServices servicesWithPlayerRepository(PlayerRepository repository) {
        GameResources resources = GameResources.unavailable();
        GameplayServices gameplay = new GameplayServices(resources);
        return TestServices.serverServices(
                new AuthService(new TestAccountRepository()), resources, gameplay,
                new PlayerService(repository));
    }

    private static Session addPlayerSession(NetworkServer server, SessionServices services) {
        SessionManager sessions = server.sessions();
        Session session = new Session(sessions.nextId(), new TestTransport(), sessions,
                new LegacyPacketCodec(1024), "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                4, services, ClientConfig.defaults());
        assertTrue(sessions.tryAdd(session, 1));
        assertTrue(sessions.beginAccountAdmission(session, 10L, "alpha1"));
        sessions.finishAccountAdmission(session, true);
        session.bindPlayer(TestPlayerProfiles.initial(10L, 1, "alpha1", 0));
        return session;
    }

    private static NetworkServer networkServer(String host, int port, SessionServices services) {
        return new NetworkServer(host, port, 20, 1024, 8, 1000,
                "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII), services, null,
                ClientConfig.defaults());
    }

    private static Properties startupProperties() {
        Properties properties = new Properties();
        properties.setProperty("game.network.host", "127.0.0.1");
        properties.setProperty("game.network.port", "0");
        properties.setProperty("game.resource.icon-dir", "");
        properties.setProperty("game.resource.json-dir", "resources/json");
        properties.setProperty("game.resource.map-dir", "resources/maps");
        properties.setProperty("game.resource.image-version", "2");
        properties.setProperty("game.resource.monster-version", "2");
        return properties;
    }

    private static List<MapRepository.MapRow> canonicalMapRows() {
        return List.of(new MapRepository.MapRow(
                0, "Núi Paozu", "ONLINE", "EARTH", 1, 3, 40, 1, true));
    }

    private static MapRepository mapRepository(List<MapRepository.MapRow> maps) {
        return new MapRepository() {
            @Override
            public List<MapRow> findAllMaps() {
                return maps;
            }

            @Override
            public List<WaypointRow> findAllWaypoints() {
                return List.of();
            }
        };
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
            ResultSet emptyResultSet = (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "next" -> false;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
            PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "executeQuery" -> emptyResultSet;
                        case "close", "setString", "setLong", "setInt", "setBytes",
                                "setTimestamp" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
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
                        case "prepareStatement" -> statement;
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
        if (type == void.class) {
            return null;
        }
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

package com.project.game.bootstrap;

import com.project.game.account.AuthService;
import com.project.game.combat.CombatService;
import com.project.game.map.MapService;
import com.project.game.map.ZoneRegistry;
import com.project.game.monster.MonsterFactory;
import com.project.game.monster.MonsterManager;
import com.project.game.network.ClientConfig;
import com.project.game.network.NetworkServer;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.transport.TlsContextFactory;
import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.DatabaseMigrator;
import com.project.game.persistence.DatabaseCatalogSeeder;
import com.project.game.persistence.account.JdbcAccountRepository;
import com.project.game.persistence.map.JdbcMapRepository;
import com.project.game.persistence.map.MapRepository;
import com.project.game.persistence.monster.JdbcMonsterRepository;
import com.project.game.persistence.monster.MonsterRepository;
import com.project.game.persistence.player.JdbcPlayerRepository;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.map.MapTemplate;
import com.project.game.resource.loader.MapCatalogLoader;
import com.project.game.network.SessionServices;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Map;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;

/** Creates the application services and owns their shared database lifecycle. */
public final class ServerBootstrap {
    private final NetworkServer server;
    private final DatabaseManager databaseManager;
    private boolean stopped;

    ServerBootstrap(NetworkServer server, DatabaseManager databaseManager) {
        this.server = Objects.requireNonNull(server, "server");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public static ServerBootstrap fromSystemProperties() {
        Properties properties = new Properties();
        try (var input = ServerBootstrap.class.getResourceAsStream("/application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load application.properties", exception);
        }
        overlaySystemProperties(properties);
        return fromProperties(properties,
                () -> new DatabaseManager(DatabaseConfig.fromProperties(properties)),
                manager -> new JdbcMapRepository(manager.dataSource()),
                manager -> new JdbcMonsterRepository(manager.dataSource()),
                (manager, migrationDirectory) -> DatabaseMigrator.migrate(
                        manager.dataSource(), migrationDirectory),
                (manager, catalogSeedFile) -> DatabaseCatalogSeeder.seed(
                        manager.dataSource(), catalogSeedFile));
    }

    static ServerBootstrap fromProperties(
            Properties properties,
            Supplier<DatabaseManager> databaseManagerFactory,
            Function<DatabaseManager, MapRepository> mapRepositoryFactory,
            Function<DatabaseManager, MonsterRepository> monsterRepositoryFactory,
            BiConsumer<DatabaseManager, Path> migrationAction,
            BiConsumer<DatabaseManager, Path> catalogSeedAction) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(databaseManagerFactory, "databaseManagerFactory");
        Objects.requireNonNull(mapRepositoryFactory, "mapRepositoryFactory");
        Objects.requireNonNull(monsterRepositoryFactory, "monsterRepositoryFactory");
        Objects.requireNonNull(migrationAction, "migrationAction");
        Objects.requireNonNull(catalogSeedAction, "catalogSeedAction");

        String transport = properties.getProperty("game.network.transport", "LEGACY_TCP").trim();
        SSLContext tlsContext;
        try {
            tlsContext = switch (transport.toUpperCase(Locale.ROOT)) {
                case "LEGACY_TCP" -> null;
                case "TLS" -> TlsContextFactory.fromProperties(properties);
                default -> throw new IllegalStateException("unsupported network transport: " + transport);
            };
        } catch (IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("cannot initialize TLS network transport", exception);
        }

        Path migrationDirectory = requiredPath(properties, "game.db.migration-dir");
        Path catalogSeedFile = requiredPath(properties, "game.db.catalog-seed-file");
        DatabaseManager databaseManager = databaseManagerFactory.get();
        try {
            migrationAction.accept(databaseManager, migrationDirectory);
            catalogSeedAction.accept(databaseManager, catalogSeedFile);
            MapRepository mapRepository = Objects.requireNonNull(
                    mapRepositoryFactory.apply(databaseManager), "mapRepository");
            Path mapDataRoot = requiredPath(properties, "game.resource.map-dir");
            Map<Integer, MapTemplate> mapCatalog =
                    MapCatalogLoader.load(mapRepository, mapDataRoot);
            requireEnabledMaps(mapCatalog);
            MonsterRepository monsterRepository = Objects.requireNonNull(
                    monsterRepositoryFactory.apply(databaseManager), "monsterRepository");
            GameResources resources = loadResources(properties, mapCatalog, monsterRepository);
            MonsterFactory monsterFactory = new MonsterFactory(resources);
            PlayerPacketWriter playerPackets = new PlayerPacketWriter();
            MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
            ZoneRegistry zones = new ZoneRegistry(mapCatalog, monsterFactory);
            MapService maps = new MapService(zones, playerPackets);
            CombatService combat = new CombatService(zones, playerPackets, monsterPackets);
            MonsterManager monsterManager = new MonsterManager(zones, monsterPackets, playerPackets);

            JdbcAccountRepository accountRepository =
                    new JdbcAccountRepository(databaseManager.dataSource());
            accountRepository.findByUsername("__startup_probe__");
            JdbcPlayerRepository playerRepository =
                    new JdbcPlayerRepository(databaseManager.dataSource());
            playerRepository.probeTable();

            AuthService auth = new AuthService(accountRepository);
            SessionServices services = new SessionServices(
                    auth, resources, maps, combat, monsterManager, new PlayerService(playerRepository));
            NetworkServer server = new NetworkServer(
                    properties.getProperty("game.network.host", "127.0.0.1"),
                    integer(properties, "game.network.port", 1707),
                    integer(properties, "game.network.max-session-per-ip", 20),
                    integer(properties, "game.network.max-packet-size", 65535),
                    integer(properties, "game.network.send-queue-size", 256),
                    integer(properties, "game.network.handshake-timeout-ms", 10000),
                    "abc".getBytes(StandardCharsets.US_ASCII),
                    services, tlsContext, ClientConfig.fromProperties(properties));
            return new ServerBootstrap(server, databaseManager);
        } catch (RuntimeException | Error exception) {
            databaseManager.close();
            throw exception;
        }
    }

    public void start() throws IOException {
        try {
            server.start();
        } catch (IOException | RuntimeException | Error exception) {
            try {
                stop();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != exception) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            server.stop();
        } finally {
            databaseManager.close();
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    private static GameResources loadResources(
            Properties properties,
            Map<Integer, MapTemplate> maps,
            MonsterRepository monsterRepository) {
        String configuredIconRoot = properties.getProperty("game.resource.icon-dir", "").trim();
        String configuredJsonRoot = properties.getProperty("game.resource.json-dir", "").trim();
        int imageVersion = integer(properties, "game.resource.image-version", -1);
        int monsterVersion = requiredMonsterVersion(properties);
        java.nio.file.Path iconRoot = configuredIconRoot.isEmpty()
                ? null : java.nio.file.Path.of(configuredIconRoot);
        if (configuredJsonRoot.isEmpty()) {
            throw new IllegalStateException(
                    "game.resource.json-dir must be configured for normal startup");
        }
        return GameResources.fromRoots(
                iconRoot,
                java.nio.file.Path.of(configuredJsonRoot),
                imageVersion,
                monsterVersion,
                maps,
                monsterRepository);
    }

    private static int requiredMonsterVersion(Properties properties) {
        String configured = properties.getProperty("game.resource.monster-version");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException(
                    "game.resource.monster-version must be configured for normal startup");
        }
        final int version;
        try {
            version = Integer.parseInt(configured.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "game.resource.monster-version must be an integer", exception);
        }
        if (version < 1 || version > Byte.MAX_VALUE) {
            throw new IllegalStateException(
                    "game.resource.monster-version must be between 1 and 127: " + version);
        }
        return version;
    }

    private static Path requiredPath(Properties properties, String key) {
        String configured = properties.getProperty(key, "").trim();
        if (configured.isEmpty()) {
            throw new IllegalStateException(key + " must be configured for normal startup");
        }
        return Path.of(configured);
    }

    private static void requireEnabledMaps(Map<Integer, MapTemplate> maps) {
        if (maps.isEmpty()) {
            throw new IllegalStateException("enabled map catalog is empty");
        }
        if (!maps.containsKey(0)) {
            throw new IllegalStateException("enabled map 0 is required");
        }
    }

    static void overlaySystemProperties(Properties properties) {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("game.network.")
                    || key.startsWith("game.resource.")
                    || key.startsWith("game.db.")) {
                properties.setProperty(key, System.getProperty(key));
            }
        }
    }
}

package com.project.game.bootstrap;

import com.project.game.account.AuthService;
import com.project.game.combat.CombatService;
import com.project.game.map.MapService;
import com.project.game.map.ZoneRegistry;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterService;
import com.project.game.network.NetworkConfig;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.NetworkServer;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.transport.TlsContextFactory;
import com.project.game.persistence.DatabaseConfig;
import com.project.game.persistence.DatabaseManager;
import com.project.game.persistence.account.JdbcAccountRepository;
import com.project.game.persistence.player.JdbcPlayerRepository;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.service.ServerServices;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/** Creates the application services and owns their shared database lifecycle. */
public final class ServerBootstrap {
    private final NetworkServer server;
    private final DatabaseManager databaseManager;

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
                () -> new DatabaseManager(DatabaseConfig.fromProperties(properties)));
    }

    static ServerBootstrap fromProperties(
            Properties properties,
            Supplier<DatabaseManager> databaseManagerFactory) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(databaseManagerFactory, "databaseManagerFactory");

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

        DatabaseManager databaseManager = databaseManagerFactory.get();
        try {
            GameResources resources = resourceService(properties);
            MonsterRuntimeFactory monsterFactory = new MonsterRuntimeFactory(resources);
            PlayerPacketWriter playerPackets = new PlayerPacketWriter();
            MonsterPacketWriter monsterPackets = new MonsterPacketWriter();
            ZoneRegistry zones = new ZoneRegistry(monsterFactory);
            MapService maps = new MapService(zones, playerPackets);
            CombatService combat = new CombatService(zones, playerPackets, monsterPackets);
            MonsterService monsters = new MonsterService(zones, monsterPackets, playerPackets);

            JdbcAccountRepository accountRepository =
                    new JdbcAccountRepository(databaseManager.dataSource());
            accountRepository.findByUsername("__startup_probe__");
            JdbcPlayerRepository playerRepository =
                    new JdbcPlayerRepository(databaseManager.dataSource());
            playerRepository.probeTable();

            AuthService auth = new AuthService(accountRepository);
            ServerServices services = new ServerServices(
                    auth, resources, maps, combat, monsters, new PlayerService(playerRepository));
            NetworkServer server = new NetworkServer(
                    properties.getProperty("game.network.host", "127.0.0.1"),
                    integer(properties, "game.network.port", 1707),
                    integer(properties, "game.network.max-session-per-ip", 20),
                    integer(properties, "game.network.max-packet-size", 65535),
                    integer(properties, "game.network.send-queue-size", 256),
                    integer(properties, "game.network.handshake-timeout-ms", 10000),
                    "abc".getBytes(StandardCharsets.US_ASCII),
                    services, tlsContext, NetworkConfig.fromProperties(properties),
                    NetworkEventObserver.NO_OP);
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
            databaseManager.close();
            throw exception;
        }
    }

    public void stop() {
        try {
            server.stop();
        } finally {
            databaseManager.close();
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    private static GameResources resourceService(Properties properties) {
        String configuredIconRoot = properties.getProperty("game.resource.icon-dir", "").trim();
        String configuredJsonRoot = properties.getProperty("game.resource.json-dir", "").trim();
        int imageVersion = integer(properties, "game.resource.image-version", -1);
        java.nio.file.Path iconRoot = configuredIconRoot.isEmpty()
                ? null : java.nio.file.Path.of(configuredIconRoot);
        if (configuredJsonRoot.isEmpty()) {
            return iconRoot == null
                    ? GameResources.unavailable()
                    : GameResources.fromIconRoot(iconRoot, imageVersion);
        }
        return GameResources.fromRoots(
                iconRoot,
                java.nio.file.Path.of(configuredJsonRoot),
                imageVersion);
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

package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.network.transport.TlsContextFactory;
import com.project.game.network.transport.TlsTcpTransport;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.util.Properties;
import java.util.Objects;
import java.util.logging.Logger;

/** Legacy TCP accept loop for the new project; the old server is not referenced. */
public final class NetworkServer {
    private static final Logger LOGGER = Logger.getLogger(NetworkServer.class.getName());
    private final String host;
    private final int port;
    private final int maxSessionsPerIp;
    private final int maxPacketSize;
    private final int sendQueueSize;
    private final int handshakeTimeoutMillis;
    private final byte[] handshakeKey;
    private final ServerServices services;
    private final SSLContext tlsContext;
    private final NetworkConfig networkConfig;
    private final NetworkEventObserver eventObserver;
    private final SessionManager sessions = new SessionManager();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey,
                         ServerServices services, SSLContext tlsContext, NetworkConfig networkConfig,
                         NetworkEventObserver eventObserver) {
        if (port < 0 || port > 65535 || maxSessionsPerIp < 1 || maxPacketSize < 1 || sendQueueSize < 1
                || handshakeTimeoutMillis < 1) {
            throw new IllegalArgumentException("invalid network configuration");
        }
        this.host = host;
        this.port = port;
        this.maxSessionsPerIp = maxSessionsPerIp;
        this.maxPacketSize = maxPacketSize;
        this.sendQueueSize = sendQueueSize;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.handshakeKey = Objects.requireNonNull(handshakeKey, "handshakeKey").clone();
        if (this.handshakeKey.length == 0) {
            throw new IllegalArgumentException("handshakeKey must not be empty");
        }
        this.services = Objects.requireNonNull(services, "services");
        this.tlsContext = tlsContext;
        this.networkConfig = Objects.requireNonNull(networkConfig, "networkConfig");
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
    }

    public static NetworkServer fromSystemProperties() {
        Properties properties = new Properties();
        try (var input = NetworkServer.class.getResourceAsStream("/application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load application.properties", exception);
        }
        overlaySystemProperties(properties);
        String transport = properties.getProperty("game.network.transport", "LEGACY_TCP").trim();
        SSLContext tlsContext;
        try {
            tlsContext = switch (transport.toUpperCase(java.util.Locale.ROOT)) {
                case "LEGACY_TCP" -> null;
                case "TLS" -> TlsContextFactory.fromProperties(properties);
                default -> throw new IllegalStateException("unsupported network transport: " + transport);
            };
        } catch (IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("cannot initialize TLS network transport", exception);
        }
        return new NetworkServer(
                properties.getProperty("game.network.host", "127.0.0.1"),
                integer(properties, "game.network.port", 1707),
                integer(properties, "game.network.max-session-per-ip", 20),
                integer(properties, "game.network.max-packet-size", 65535),
                integer(properties, "game.network.send-queue-size", 256),
                integer(properties, "game.network.handshake-timeout-ms", 10000),
                "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resourceService(properties)), tlsContext,
                NetworkConfig.fromProperties(properties), NetworkEventObserver.NO_OP);
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket listener;
        if (tlsContext == null) {
            listener = new ServerSocket();
        } else {
            listener = (SSLServerSocket) tlsContext.getServerSocketFactory().createServerSocket();
        }
        listener.bind(new InetSocketAddress(host, port));
        serverSocket = listener;
        running = true;
        LOGGER.info(() -> "Network server listening on " + host + ':' + port
                + " transport=" + (tlsContext == null ? "LEGACY_TCP" : "TLS"));
        while (running) {
            try {
                ClientTransport transport;
                if (tlsContext == null) {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(handshakeTimeoutMillis);
                    transport = new LegacyTcpTransport(socket);
                } else {
                    transport = TlsTcpTransport.accept((SSLServerSocket) listener, handshakeTimeoutMillis);
                }
                LegacyPacketCodec codec = new LegacyPacketCodec(maxPacketSize);
                Session session = new Session(sessions.nextId(), transport, sessions, codec, handshakeKey,
                        sendQueueSize, services, networkConfig, eventObserver);
                if (!sessions.tryAdd(session, maxSessionsPerIp)) {
                    transport.close();
                    continue;
                }
                try {
                    session.start();
                } catch (IOException exception) {
                    session.close();
                    throw exception;
                }
            } catch (IOException exception) {
                if (running) {
                    LOGGER.warning("Accept failed: " + exception.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        sessions.closeAll();
    }

    public SessionManager sessions() {
        return sessions;
    }

    public int localPort() {
        ServerSocket listener = serverSocket;
        return listener == null ? 0 : listener.getLocalPort();
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    private static ResourceService resourceService(Properties properties) {
        String configuredRoot = properties.getProperty("game.resource.icon-dir", "").trim();
        return configuredRoot.isEmpty()
                ? ResourceService.unavailable()
                : ResourceService.fromIconRoot(java.nio.file.Path.of(configuredRoot));
    }

    private static void overlaySystemProperties(Properties properties) {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("game.network.") || key.startsWith("game.resource.")) {
                properties.setProperty(key, System.getProperty(key));
            }
        }
    }
}

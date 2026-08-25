package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.network.transport.TlsContextFactory;
import com.project.game.network.transport.TlsTcpTransport;
import com.project.game.service.AuthService;

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
    private final AuthService authService;
    private final SSLContext tlsContext;
    private final SessionManager sessions = new SessionManager();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey) {
        this(host, port, maxSessionsPerIp, maxPacketSize, sendQueueSize, handshakeTimeoutMillis,
                handshakeKey, new AuthService(), null);
    }

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey,
                         AuthService authService) {
        this(host, port, maxSessionsPerIp, maxPacketSize, sendQueueSize, handshakeTimeoutMillis,
                handshakeKey, authService, null);
    }

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey,
                         AuthService authService, SSLContext tlsContext) {
        if (port < 1 || port > 65535 || maxSessionsPerIp < 1 || maxPacketSize < 1 || sendQueueSize < 1
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
        this.authService = Objects.requireNonNull(authService, "authService");
        this.tlsContext = tlsContext;
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
                new AuthService(), tlsContext);
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
                        sendQueueSize, authService);
                if (!sessions.tryAdd(session, maxSessionsPerIp)) {
                    transport.close();
                    continue;
                }
                session.start();
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

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
    }

    private static void overlaySystemProperties(Properties properties) {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("game.network.")) {
                properties.setProperty(key, System.getProperty(key));
            }
        }
    }
}

package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.LegacyTcpTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
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
    private final SessionManager sessions = new SessionManager();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey) {
        this.host = host;
        this.port = port;
        this.maxSessionsPerIp = maxSessionsPerIp;
        this.maxPacketSize = maxPacketSize;
        this.sendQueueSize = sendQueueSize;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.handshakeKey = handshakeKey.clone();
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
        return new NetworkServer(
                properties.getProperty("game.network.host", "127.0.0.1"),
                integer(properties, "game.network.port", 1707),
                integer(properties, "game.network.max-session-per-ip", 20),
                integer(properties, "game.network.max-packet-size", 65535),
                integer(properties, "game.network.send-queue-size", 256),
                integer(properties, "game.network.handshake-timeout-ms", 10000),
                "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(host, port));
        running = true;
        LOGGER.info(() -> "Network server listening on " + host + ':' + port);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(handshakeTimeoutMillis);
                LegacyTcpTransport transport = new LegacyTcpTransport(socket);
                LegacyPacketCodec codec = new LegacyPacketCodec(maxPacketSize);
                Session session = new Session(sessions.nextId(), transport, sessions, codec, handshakeKey, sendQueueSize);
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
}

package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.network.transport.TlsTcpTransport;
import com.project.game.monster.MonsterLifecycleScheduler;
import com.project.game.network.SessionServices;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.util.Objects;
import java.util.logging.Logger;

/** Legacy TCP accept loop for the new project; the old server is not referenced. */
public final class NetworkServer {
    private static final Logger LOGGER = Logger.getLogger(NetworkServer.class.getName());
    private static final long MONSTER_LIFECYCLE_PERIOD_MILLIS = 100L;
    private final String host;
    private final int port;
    private final int maxSessionsPerIp;
    private final int maxPacketSize;
    private final int sendQueueSize;
    private final int handshakeTimeoutMillis;
    private final byte[] handshakeKey;
    private final SessionServices services;
    private final MonsterLifecycleScheduler monsterLifecycleScheduler;
    private final SSLContext tlsContext;
    private final ClientConfig networkConfig;
    private final SessionManager sessions = new SessionManager();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public NetworkServer(String host, int port, int maxSessionsPerIp, int maxPacketSize,
                         int sendQueueSize, int handshakeTimeoutMillis, byte[] handshakeKey,
                         SessionServices services, SSLContext tlsContext, ClientConfig networkConfig) {
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
        this.monsterLifecycleScheduler = new MonsterLifecycleScheduler(
                this.services.monsterManager()::update,
                MONSTER_LIFECYCLE_PERIOD_MILLIS);
        this.tlsContext = tlsContext;
        this.networkConfig = Objects.requireNonNull(networkConfig, "networkConfig");
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket listener = null;
        try {
            if (tlsContext == null) {
                listener = new ServerSocket();
            } else {
                listener = (SSLServerSocket) tlsContext.getServerSocketFactory().createServerSocket();
            }
            listener.bind(new InetSocketAddress(host, port));
        } catch (IOException | RuntimeException exception) {
            if (listener != null) {
                try {
                    listener.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw exception;
        }
        serverSocket = listener;
        running = true;
        try {
            monsterLifecycleScheduler.start();
        } catch (RuntimeException exception) {
            running = false;
            serverSocket = null;
            try {
                listener.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
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
                        sendQueueSize, services, networkConfig);
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
        monsterLifecycleScheduler.stop();
        sessions.closeAll();
    }

    public SessionManager sessions() {
        return sessions;
    }

    public int localPort() {
        ServerSocket listener = serverSocket;
        return listener == null ? 0 : listener.getLocalPort();
    }

}

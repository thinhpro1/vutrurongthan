package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.handler.MessageHandler;
import com.project.game.network.message.Message;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.map.MapManager;
import com.project.game.network.SessionServices;
import com.project.game.player.PlayerService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.logging.Level;

/** One client connection: transport, protocol cursors, lifecycle and bounded writer queue. */
public final class Session implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(Session.class.getName());
    private final int id;
    private final ClientTransport transport;
    private final SessionManager manager;
    private final LegacyPacketCodec codec;
    private final LegacyCipher cipher;
    private final byte[] handshakeKey;
    private final BlockingQueue<Message> sendQueue;
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.CONNECTED);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final MapManager mapManager;
    private final PlayerService playerService;
    private final Object writeLock = new Object();
    private final MessageHandler handler;
    private final Set<Integer> sentMapTemplates = ConcurrentHashMap.newKeySet();
    private volatile String accountName;
    private volatile long accountId;
    private boolean accountAdmissionPending;
    private volatile PlayerProfile player;
    private int protocolViolations;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile Thread readerThread;
    private volatile Thread writerThread;

    public Session(int id, ClientTransport transport, SessionManager manager,
                   LegacyPacketCodec codec, byte[] handshakeKey, int queueSize,
                   SessionServices services, ClientConfig networkConfig) {
        if (queueSize < 1) {
            throw new IllegalArgumentException("queueSize must be positive");
        }
        this.id = id;
        this.transport = transport;
        this.manager = manager;
        this.codec = codec;
        this.handshakeKey = handshakeKey.clone();
        this.cipher = new LegacyCipher(handshakeKey);
        this.sendQueue = new ArrayBlockingQueue<>(queueSize);
        this.mapManager = Objects.requireNonNull(services, "services").maps();
        this.playerService = services.players();
        this.handler = new MessageHandler(this, services, networkConfig);
    }

    public int id() {
        return id;
    }

    public SessionManager manager() {
        return manager;
    }

    public String remoteAddress() {
        return transport.remoteAddress();
    }

    public SessionState state() {
        return state.get();
    }

    public int queuedMessages() {
        return sendQueue.size();
    }

    public int maxPacketSize() {
        return codec.maxPacketSize();
    }

    public String accountName() {
        return accountName;
    }

    public long accountId() {
        return accountId;
    }

    public PlayerProfile player() {
        return player;
    }

    void bindAccount(long accountId, String accountName) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        this.accountId = accountId;
        this.accountName = accountName;
    }

    boolean accountAdmissionPending() {
        return accountAdmissionPending;
    }

    void markAccountAdmissionPending() {
        accountAdmissionPending = true;
    }

    void clearAccountAdmissionPending() {
        accountAdmissionPending = false;
    }

    public void bindPlayer(PlayerProfile player) {
        this.player = player;
    }

    public boolean hasSentMapTemplate(int mapId) {
        return sentMapTemplates.contains(mapId);
    }

    public void markMapTemplateSent(int mapId) {
        sentMapTemplates.add(mapId);
    }

    public boolean recordProtocolViolation() {
        return ++protocolViolations >= 3;
    }

    public void start() throws IOException {
        try {
            input = transport.input();
            output = transport.output();
            LOGGER.info(() -> "SESSION_OPEN id=" + id + " ip=" + remoteAddress());
            readerThread = startThread("session-reader-" + id, this::readLoop);
            writerThread = startThread("session-writer-" + id, this::writeLoop);
        } catch (IOException exception) {
            close("start failure");
            throw exception;
        }
    }

    public void completeHandshake() throws IOException {
        if (state() != SessionState.CONNECTED) {
            throw new IOException("handshake is not valid in state " + state());
        }
        synchronized (writeLock) {
            codec.writeHandshakeKey(output, handshakeKey);
        }
        transport.setReadTimeout(0);
        if (!state.compareAndSet(SessionState.CONNECTED, SessionState.HANDSHAKE_DONE)) {
            throw new IOException("handshake state changed unexpectedly");
        }
        LOGGER.info(() -> "HANDSHAKE_OK id=" + id);
    }

    public boolean trySend(Message message) {
        return message != null && state() != SessionState.CLOSED && sendQueue.offer(message);
    }

    public boolean send(Message message) {
        if (!trySend(message)) {
            close("outbound queue overflow or closed session");
            return false;
        }
        return true;
    }

    public boolean transition(SessionState expected, SessionState next) {
        boolean transitioned = state.compareAndSet(expected, next);
        if (transitioned) {
            LOGGER.info(() -> "STATE id=" + id + " " + expected + " -> " + next);
        }
        return transitioned;
    }

    @Override
    public void close() {
        close("requested");
    }

    private void close(String reason) {
        Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            state.set(SessionState.CLOSED);
        }
        LOGGER.info(() -> "SESSION_CLOSE id=" + id + " ip=" + remoteAddress() + " reason=" + reason);
        sendQueue.clear();
        if (readerThread != null && readerThread != currentThread) {
            readerThread.interrupt();
        }
        if (writerThread != null && writerThread != currentThread) {
            writerThread.interrupt();
        }
        try {
            mapManager.leave(this);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Map cleanup failed for session id=" + id, exception);
        }
        PlayerProfile snapshot = player;
        try {
            if (snapshot != null && snapshot.id() > 0 && accountId > 0L) {
                boolean interruptedBeforeCheckpoint = Thread.interrupted();
                try {
                    playerService.checkpoint(snapshot);
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING, "Player checkpoint failed for session id=" + id, exception);
                } finally {
                    if (interruptedBeforeCheckpoint) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            manager.unbindAccount(this);
        }
        try {
            transport.close();
        } catch (IOException ignored) {
            // Closing an already broken socket is best-effort.
        }
        manager.remove(this);
    }

    private void readLoop() {
        try {
            while (state() != SessionState.CLOSED) {
                Message message = codec.read(input, cipher, state() != SessionState.CONNECTED);
                LOGGER.fine(() -> "RX id=" + id + " cmd=" + message.command()
                        + " len=" + message.payload().length);
                handler.onMessage(message);
            }
        } catch (IOException ignored) {
            close("read failure or peer disconnect");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Unhandled session failure id=" + id, exception);
            close("unexpected handler failure");
        }
    }

    private void writeLoop() {
        try {
            while (state() != SessionState.CLOSED) {
                Message message = sendQueue.take();
                synchronized (writeLock) {
                    codec.write(output, cipher, state() != SessionState.CONNECTED, message);
                    LOGGER.fine(() -> "TX id=" + id + " cmd=" + message.command()
                            + " len=" + message.payload().length);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close("writer interrupted");
        } catch (IOException ignored) {
            close("write failure");
        }
    }

    private static Thread startThread(String name, Runnable task) {
        return Thread.ofVirtual().name(name).start(task);
    }
}

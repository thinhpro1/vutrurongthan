package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.service.AuthService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One client connection: transport, protocol cursors, lifecycle and bounded writer queue. */
public final class Session implements AutoCloseable {
    private final int id;
    private final ClientTransport transport;
    private final SessionManager manager;
    private final LegacyPacketCodec codec;
    private final LegacyCipher cipher;
    private final byte[] handshakeKey;
    private final BlockingQueue<Message> sendQueue;
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.CONNECTED);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object writeLock = new Object();
    private final MessageHandler handler;
    private volatile String accountName;
    private volatile PlayerProfile player;
    private int protocolViolations;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile Thread readerThread;
    private volatile Thread writerThread;

    public Session(int id, ClientTransport transport, SessionManager manager,
                   LegacyPacketCodec codec, byte[] handshakeKey, int queueSize) {
        this(id, transport, manager, codec, handshakeKey, queueSize, new AuthService());
    }

    public Session(int id, ClientTransport transport, SessionManager manager,
                   LegacyPacketCodec codec, byte[] handshakeKey, int queueSize,
                   AuthService authService) {
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
        this.handler = new MessageHandler(this, authService);
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

    public String accountName() {
        return accountName;
    }

    public PlayerProfile player() {
        return player;
    }

    public void bindAccount(String accountName) {
        this.accountName = accountName;
    }

    public void bindPlayer(PlayerProfile player) {
        this.player = player;
    }

    public boolean recordProtocolViolation() {
        return ++protocolViolations >= 3;
    }

    public void start() throws IOException {
        input = transport.input();
        output = transport.output();
        readerThread = startThread("session-reader-" + id, this::readLoop);
        writerThread = startThread("session-writer-" + id, this::writeLoop);
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
    }

    public boolean send(Message message) {
        if (message == null || state() == SessionState.CLOSED || !sendQueue.offer(message)) {
            close();
            return false;
        }
        return true;
    }

    public boolean transition(SessionState expected, SessionState next) {
        if (next == SessionState.CLOSED) {
            return state.compareAndSet(expected, next);
        }
        return state.compareAndSet(expected, next);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        state.set(SessionState.CLOSED);
        sendQueue.clear();
        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (writerThread != null) {
            writerThread.interrupt();
        }
        try {
            transport.close();
        } catch (IOException ignored) {
            // Closing an already broken socket is best-effort.
        }
        manager.unbindAccount(this);
        manager.remove(this);
    }

    private void readLoop() {
        try {
            while (state() != SessionState.CLOSED) {
                Message message = codec.read(input, cipher, state() != SessionState.CONNECTED);
                handler.onMessage(message);
            }
        } catch (IOException ignored) {
            close();
        }
    }

    private void writeLoop() {
        try {
            while (state() != SessionState.CLOSED) {
                Message message = sendQueue.take();
                synchronized (writeLock) {
                    codec.write(output, cipher, state() != SessionState.CONNECTED, message);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close();
        } catch (IOException ignored) {
            close();
        }
    }

    private static Thread startThread(String name, Runnable task) {
        return Thread.ofVirtual().name(name).start(task);
    }
}

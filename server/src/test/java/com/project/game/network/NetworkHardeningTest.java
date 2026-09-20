package com.project.game.network;

import com.project.game.testsupport.TestServices;
import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.account.AuthService;
import com.project.game.resource.GameResources;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkHardeningTest {
    @Test
    void closesWhenConnectContainsTrailingBytes() throws Exception {
        SessionManager manager = new SessionManager();
        PipedInputStream input = new PipedInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PipedOutputStream client = new PipedOutputStream(input)) {
            TestTransport transport = new TestTransport(input, output, "127.0.0.1");
            Session session = new Session(manager.nextId(), transport, manager, new LegacyPacketCodec(1024),
                    "abc".getBytes(StandardCharsets.US_ASCII), 4, TestServices.serverServices(),
                    NetworkConfig.defaults());
            assertTrue(manager.tryAdd(session, 1));
            session.start();
            try {
                LegacyPacketCodec codec = new LegacyPacketCodec(1024);
                codec.writeClient(client, null, false, new Message(MessageName.CONNECT_SERVER,
                        new MessageWriter().writeByte(99).toByteArray()));

                waitForClosed(session);

                assertEquals(0, output.size(), "malformed connect sent handshake bytes");
                assertEquals(0, manager.onlineCount());
            } finally {
                session.close();
            }
        }
    }

    @Test
    void perIpCounterIsReleasedAfterAcceptedSessionCloses() {
        SessionManager manager = new SessionManager();
        Session first = session(manager, "127.0.0.1", 4);
        Session second = session(manager, "127.0.0.1", 4);
        Session third = session(manager, "127.0.0.1", 4);
        assertTrue(manager.tryAdd(first, 2));
        assertTrue(manager.tryAdd(second, 2));
        assertFalse(manager.tryAdd(third, 2));
        first.close();
        assertTrue(manager.tryAdd(third, 2));
    }

    @Test
    void queueOverflowClosesSessionWithoutBlockingProducer() throws Exception {
        SessionManager manager = new SessionManager();
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            BlockingOutput output = new BlockingOutput();
            TestTransport transport = new TestTransport(input, output, "127.0.0.1");
            Session session = new Session(manager.nextId(), transport, manager, new LegacyPacketCodec(1024),
                    "abc".getBytes(StandardCharsets.US_ASCII), 1, TestServices.serverServices(),
                    NetworkConfig.defaults());
            assertTrue(manager.tryAdd(session, 1));
            session.start();
            assertTrue(session.send(new Message(MessageName.DIALOG_OK)));
            assertTrue(output.writeStarted.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(session.send(new Message(MessageName.DIALOG_OK)));
            assertFalse(session.send(new Message(MessageName.DIALOG_OK)));
            assertEquals(SessionState.CLOSED, session.state());
            assertTrue(transport.isClosed());
            assertEquals(0, manager.onlineCount());
        }
    }

    private static Session session(SessionManager manager, String ip, int queueSize) {
        return new Session(manager.nextId(), new TestTransport(new java.io.ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), ip), manager, new LegacyPacketCodec(1024),
                "abc".getBytes(StandardCharsets.US_ASCII), queueSize, TestServices.serverServices(),
                NetworkConfig.defaults());
    }

    private static void waitForClosed(Session session) throws InterruptedException {
        waitForState(session, SessionState.CLOSED);
    }

    private static void waitForState(Session session, SessionState expected) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (session.state() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, session.state());
    }

    private static final class BlockingOutput extends OutputStream {
        private final CountDownLatch writeStarted = new CountDownLatch(1);

        @Override
        public void write(int value) throws IOException {
            writeStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("writer interrupted", exception);
            }
        }
    }
}

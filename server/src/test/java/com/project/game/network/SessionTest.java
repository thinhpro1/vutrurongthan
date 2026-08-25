package com.project.game.network;

import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.transport.ClientTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {
    @Test
    void startFailureClosesTransportAndRemovesRegisteredSession() {
        SessionManager manager = new SessionManager();
        AtomicBoolean transportClosed = new AtomicBoolean();
        ClientTransport transport = new ClientTransport() {
            @Override
            public InputStream input() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public OutputStream output() throws IOException {
                throw new IOException("output unavailable");
            }

            @Override
            public String remoteAddress() {
                return "127.0.0.1";
            }

            @Override
            public void close() {
                transportClosed.set(true);
            }
        };
        Session session = new Session(manager.nextId(), transport, manager,
                new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 4);
        assertTrue(manager.tryAdd(session, 1));

        assertThrows(IOException.class, session::start);

        assertTrue(transportClosed.get());
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(0, manager.onlineCount());
    }

    @Test
    void outboundQueueWritesBackToBackMessagesInFifoOrderWithoutInterleaving() throws Exception {
        PipedInputStream input = new PipedInputStream();
        try (PipedOutputStream inputWriter = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            SessionManager manager = new SessionManager();
            Session session = new Session(manager.nextId(), new TestTransport(input, output, "127.0.0.1"), manager,
                    new LegacyPacketCodec(1024), "abc".getBytes(StandardCharsets.US_ASCII), 8);
            List<Message> expected = List.of(
                    new Message(MessageName.DIALOG_OK, new byte[]{1}),
                    new Message(MessageName.START_CREATE_PLAYER_SCREEN, new byte[]{2, 3}),
                    new Message(MessageName.CREATE_PLAYER, new byte[]{4, 5, 6}));
            session.start();
            for (Message message : expected) {
                assertTrue(session.send(message));
            }
            waitForBytes(output, 15);

            ByteArrayInputStream wire = new ByteArrayInputStream(output.toByteArray());
            LegacyPacketCodec codec = new LegacyPacketCodec(1024);
            assertEquals(expected.get(0), codec.readServerResponse(wire, null, false));
            assertEquals(expected.get(1), codec.readServerResponse(wire, null, false));
            assertEquals(expected.get(2), codec.readServerResponse(wire, null, false));
            assertEquals(0, wire.available());
            session.close();
        }
    }

    private static void waitForBytes(ByteArrayOutputStream output, int expectedBytes) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (output.size() >= expectedBytes) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expectedBytes, output.size(), "timed out waiting for outbound frames");
    }
}

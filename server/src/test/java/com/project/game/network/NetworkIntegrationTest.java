package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkIntegrationTest {
    @Test
    void javaClientReceivesRequestIconResponseWithLegacySpecialFraming(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = new byte[]{1, 2, 3, 4};
        Files.write(iconRoot.resolve("5.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.fromIconRoot(iconRoot)),
                null, NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Throwable failure) {
                serverFailure.set(failure);
            }
        });
        try {
            waitForPort(server);
            runIconRequest(server.localPort());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during icon integration test");
    }

    @Test
    void javaClientCompletesLegacyG1BootstrapAndReconnectsWithoutDuplicateMonsterRequest() throws Exception {
        AtomicInteger updateCount = new AtomicInteger();
        AtomicInteger monsterUpdateCount = new AtomicInteger();
        CountDownLatch firstUpdate = new CountDownLatch(1);
        CountDownLatch secondUpdate = new CountDownLatch(1);
        CountDownLatch firstMonsterUpdate = new CountDownLatch(1);
        CountDownLatch secondMonsterUpdate = new CountDownLatch(1);
        NetworkEventObserver observer = (session, type) -> {
            if (type == -1) {
                if (updateCount.incrementAndGet() == 1) {
                    firstUpdate.countDown();
                } else {
                    secondUpdate.countDown();
                }
            } else if (type == 4) {
                if (monsterUpdateCount.incrementAndGet() == 1) {
                    firstMonsterUpdate.countDown();
                } else {
                    secondMonsterUpdate.countDown();
                }
            }
        };
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), ResourceService.unavailable()),
                null, NetworkConfig.defaults(), observer);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Throwable failure) {
                serverFailure.set(failure);
            }
        });
        try {
            waitForPort(server);
            runBootstrap(server.localPort(), true);
            assertTrue(firstUpdate.await(1, TimeUnit.SECONDS));
            assertTrue(firstMonsterUpdate.await(1, TimeUnit.SECONDS));
            waitForNoSessions(server);
            // Model a restart after the client persisted monster version 0.
            runBootstrap(server.localPort(), false);
            assertTrue(secondUpdate.await(1, TimeUnit.SECONDS));
            assertTrue(!secondMonsterUpdate.await(100, TimeUnit.MILLISECONDS));
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during integration test");
    }

    private static void runBootstrap(int port, boolean requestMonster) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            byte[] key = reconstructKey(handshake.payload());
            LegacyCipher cipher = new LegacyCipher(key);
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, manifest.command());
            assertEquals(14, manifest.payload().length);
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1
            }, manifest.payload());
            var manifestReader = manifest.reader();
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(0, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(-1, manifestReader.readByte());
            assertEquals(0, manifestReader.remaining());

            if (requestMonster) {
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(4).toByteArray()));
                Message monster = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, monster.command());
                assertArrayEquals(new byte[]{4, 0, 0, 0, 0, 0}, monster.payload());
                assertEquals(6, monster.payload().length);
                var monsterReader = monster.reader();
                assertEquals(4, monsterReader.readByte());
                assertEquals(0, monsterReader.readByte());
                assertEquals(0, monsterReader.readUnsignedShort());
                assertEquals(0, monsterReader.readUnsignedShort());
                assertEquals(0, monsterReader.remaining());
            }
        }
    }

    private static void runIconRequest(int port) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REQUEST_ICON, new MessageWriter().writeShort(5).toByteArray()));
            Message response = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.REQUEST_ICON, response.command());
            assertArrayEquals(new byte[]{0, 5, 0, 0, 0, 4, 1, 2, 3, 4}, response.payload());
            var reader = response.reader();
            assertEquals(5, reader.readShort());
            assertEquals(4, reader.readInt());
            assertArrayEquals(new byte[]{1, 2, 3, 4}, reader.readBytes(4));
            assertEquals(0, reader.remaining());
        }
    }

    private static byte[] reconstructKey(byte[] payload) {
        int length = Byte.toUnsignedInt(payload[0]);
        byte[] key = new byte[length];
        key[0] = payload[1];
        for (int index = 1; index < length; index++) {
            key[index] = (byte) (Byte.toUnsignedInt(payload[index + 1]) ^ Byte.toUnsignedInt(key[index - 1]));
        }
        return key;
    }

    private static void waitForPort(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.localPort() != 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("server did not bind a port");
    }

    private static void waitForNoSessions(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.sessions().onlineCount() == 0) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(server.sessions().onlineCount() == 0, "session leak after disconnect");
    }
}

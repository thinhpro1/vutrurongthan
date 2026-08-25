package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.frame.FrameTemplate;
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
    void javaClientReceivesLegacyFrameDefinitionsInUnityFieldOrder() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), NetworkEventObserver.NO_OP);
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
            runFrameRequest(server.localPort(), resources.frames());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during frame integration test");
    }

    @Test
    void javaClientVersionCacheSkipsSecondFrameRequest() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        AtomicInteger frameUpdateCount = new AtomicInteger();
        NetworkEventObserver observer = (session, type) -> {
            if (type == 7) {
                frameUpdateCount.incrementAndGet();
            }
        };
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                new ServerServices(new AuthService(), resources), null,
                NetworkConfig.defaults(), observer);
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
            runFrameBootstrap(server.localPort(), true);
            waitForNoSessions(server);
            assertEquals(1, frameUpdateCount.get());

            // Model a restart after the client persisted frame version 0.
            runFrameBootstrap(server.localPort(), false);
            waitForNoSessions(server);
            assertEquals(1, frameUpdateCount.get());
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during frame cache test");
    }

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
    void javaClientReceivesLargeIconAboveNormalTwoByteLength(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = patternedBytes(70_000);
        Files.write(iconRoot.resolve("2170.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
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
            runIconRequest(server.localPort(), 2170, iconData);
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during large icon integration test");
    }

    @Test
    void javaClientReceivesObservedLargestIconPayload(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = patternedBytes(127_617);
        Files.write(iconRoot.resolve("2170.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
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
            runIconRequest(server.localPort(), 2170, iconData);
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during observed icon integration test");
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

    private static void runFrameRequest(int port, java.util.List<FrameTemplate> expectedFrames) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(4096);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(1_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(7).toByteArray()));
            Message response = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, response.command());
            var reader = response.reader();
            assertEquals(7, reader.readByte());
            assertEquals(0, reader.readByte());
            assertEquals(expectedFrames.size(), reader.readUnsignedShort());
            for (FrameTemplate expected : expectedFrames) {
                assertEquals(expected.id(), reader.readShort());
                assertEquals(expected.hpBar(), reader.readShort());
                assertEquals(expected.chat(), reader.readShort());
                assertEquals(expected.dead().size(), reader.readUnsignedByte());
                for (int iconId : expected.dead()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.stand().size(), reader.readUnsignedByte());
                for (int iconId : expected.stand()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.run().size(), reader.readUnsignedByte());
                for (int iconId : expected.run()) {
                    assertEquals(iconId, reader.readShort());
                }
                assertEquals(expected.fly(), reader.readShort());
                assertEquals(expected.jump(), reader.readShort());
                assertEquals(expected.fall(), reader.readShort());
                assertEquals(expected.injure(), reader.readShort());
                assertEquals(expected.action().size(), reader.readUnsignedByte());
                for (var action : expected.action().entrySet()) {
                    assertEquals(action.getKey(), reader.readByte());
                    assertEquals(action.getValue(), reader.readShort());
                }
                assertEquals(expected.dx(), reader.readShort());
                assertEquals(expected.dy(), reader.readShort());
                assertEquals(expected.width(), reader.readShort());
                assertEquals(expected.height(), reader.readShort());
            }
            assertEquals(0, reader.remaining());
        }
    }

    private static void runFrameBootstrap(int port, boolean requestFrame) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(4096);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(1_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, -1, 0, -1, -1, 0, -1, -1, -1, -1
            }, manifest.payload());

            if (requestFrame) {
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(7).toByteArray()));
                Message frame = codec.readServerResponse(transport.input(), cipher, true);
                var reader = frame.reader();
                assertEquals(7, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(6, reader.readUnsignedShort());
            }
        }
    }

    private static void runIconRequest(int port) throws Exception {
        runIconRequest(port, 5, new byte[]{1, 2, 3, 4});
    }

    private static void runIconRequest(int port, int iconId, byte[] expectedBytes) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(Math.max(1024, expectedBytes.length + 6));
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(2_000);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REQUEST_ICON, new MessageWriter().writeShort(iconId).toByteArray()));
            Message response = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.REQUEST_ICON, response.command());
            var reader = response.reader();
            assertEquals(iconId, reader.readShort());
            assertEquals(expectedBytes.length, reader.readInt());
            assertArrayEquals(expectedBytes, reader.readBytes(expectedBytes.length));
            assertEquals(0, reader.remaining());
        }
    }

    private static byte[] patternedBytes(int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31 + 7);
        }
        return bytes;
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

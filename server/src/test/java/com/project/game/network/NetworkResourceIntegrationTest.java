package com.project.game.network;

import com.project.game.testsupport.TestServices;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.resource.FrameTemplate;
import com.project.game.resource.IconFingerprint;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.project.game.network.NetworkIntegrationTestSupport.*;

class NetworkResourceIntegrationTest {

    @Test
    void javaClientReceivesLegacyFrameDefinitionsInUnityFieldOrder() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources), null,
                ClientConfig.defaults());
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
    void javaClientLoadsLegacyLevelResource() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources), null,
                ClientConfig.defaults());
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
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(2_000);
                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                Message version = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.VERSION_SOURCE, version.command());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(-1).toByteArray()));
                Message manifest = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, manifest.command());
                var manifestReader = manifest.reader();
                assertEquals(-1, manifestReader.readByte()); // subtype
                assertEquals(-1, manifestReader.readByte()); // image
                assertEquals(-1, manifestReader.readByte()); // item
                assertEquals(-1, manifestReader.readByte()); // item option
                assertEquals(-1, manifestReader.readByte()); // npc
                assertEquals(2, manifestReader.readByte()); // effect
                assertEquals(1, manifestReader.readByte()); // monster
                assertEquals(-1, manifestReader.readByte()); // medal
                assertEquals(0, manifestReader.readByte()); // level
                assertEquals(1, manifestReader.readByte()); // frame
                assertEquals(-1, manifestReader.readByte()); // mount
                assertEquals(-1, manifestReader.readByte()); // bag
                assertEquals(-1, manifestReader.readByte()); // skill paint
                assertEquals(-1, manifestReader.readByte()); // aura
                assertEquals(0, manifestReader.remaining());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(6).toByteArray()));
                Message levelResponse = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, levelResponse.command());
                var reader = levelResponse.reader();
                assertEquals(6, reader.readByte());
                assertEquals(0, reader.readByte());
                assertEquals(102, reader.readUnsignedShort());
                long previousPower = -1L;
                for (int id = 0; id < 102; id++) {
                    assertEquals(id, reader.readShort());
                    String name = reader.readUtf();
                    long power = reader.readLong();
                    assertTrue(power > previousPower);
                    previousPower = power;
                    if (id == 0 || id == 1) {
                        assertEquals("Tân binh", name);
                        assertEquals(id, power);
                    } else if (id == 2) {
                        assertEquals("Tân binh", name);
                        assertEquals(100L, power);
                    } else if (id == 101) {
                        assertEquals("Thần # cấp 5", name);
                        assertEquals(6_000_000_000_000_000L, power);
                    }
                }
                assertEquals(0, reader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during level resource integration test");
    }

    @Test
    void javaClientLoadsMovementEffectResource(@TempDir Path iconRoot) throws Exception {
        GameResources resources = GameResources.fromRoots(
                iconRoot,
                Path.of("resources", "json"),
                7);
        NetworkServer server = new NetworkServer(
                "127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources),
                null,
                ClientConfig.defaults());
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
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(2_000);

                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                assertEquals(MessageName.VERSION_SOURCE,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(-1).toByteArray()));
                Message manifest = codec.readServerResponse(transport.input(), cipher, true);
                var manifestReader = manifest.reader();
                assertEquals(-1, manifestReader.readByte());
                assertEquals(7, manifestReader.readByte()); // image
                assertEquals(-1, manifestReader.readByte()); // item
                assertEquals(-1, manifestReader.readByte()); // item option
                assertEquals(-1, manifestReader.readByte()); // npc
                assertEquals(2, manifestReader.readByte());  // effect
                assertEquals(1, manifestReader.readByte());  // monster
                assertEquals(-1, manifestReader.readByte()); // medal
                assertEquals(0, manifestReader.readByte());  // level
                assertEquals(1, manifestReader.readByte());  // frame
                assertEquals(-1, manifestReader.readByte()); // mount
                assertEquals(-1, manifestReader.readByte()); // bag
                assertEquals(-1, manifestReader.readByte()); // skill paint
                assertEquals(-1, manifestReader.readByte()); // aura
                assertEquals(0, manifestReader.remaining());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(3).toByteArray()));
                Message response = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.UPDATE_DATA, response.command());

                var reader = response.reader();
                assertEquals(3, reader.readByte());
                assertEquals(2, reader.readByte());
                assertEquals(4, reader.readUnsignedShort());
                for (var expected : resources.effects()) {
                    assertEquals(expected.id(), reader.readShort());
                    assertEquals(expected.dx(), reader.readShort());
                    assertEquals(expected.dy(), reader.readShort());
                    assertEquals(expected.delay(), reader.readShort());
                    assertEquals(expected.icons().size(), reader.readUnsignedByte());
                    for (int iconId : expected.icons()) {
                        assertEquals(iconId, reader.readShort());
                    }
                }
                assertEquals(0, reader.readUnsignedShort());
                assertEquals(0, reader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }

        assertNull(serverFailure.get(),
                "network server failed during movement effect integration test");
    }

    @Test
    void manifestDoesNotPushFrameResourceWithoutRequest() throws Exception {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("..", "client", "Assets", "Resources", "Jsons"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 4096, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources), null,
                ClientConfig.defaults());
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
            runFrameManifestWithoutRequest(server.localPort());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during frame manifest test");
    }

    @Test
    void javaClientReceivesRequestIconResponseWithLegacySpecialFraming(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = new byte[]{1, 2, 3, 4};
        Files.write(iconRoot.resolve("5.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), GameResources.fromIconRoot(iconRoot)),
                null, ClientConfig.defaults());
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
    void javaClientNegotiatesPerIconManifestAndRequestsIcon(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = new byte[]{1, 2, 3, 4};
        byte[] otherIconData = new byte[]{5, 6, 7};
        Files.write(iconRoot.resolve("5.png"), iconData);
        Files.write(iconRoot.resolve("10.png"), otherIconData);
        GameResources resources = GameResources.fromIconRoot(iconRoot, 2);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), resources),
                null, ClientConfig.defaults());
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
            LegacyPacketCodec codec = new LegacyPacketCodec(1024);
            try (LegacyTcpTransport transport = LegacyTcpTransport.connect(
                    "127.0.0.1", server.localPort(), 1_000)) {
                transport.socket().setSoTimeout(2_000);
                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                assertEquals(MessageName.VERSION_SOURCE,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(-1).toByteArray()));
                Message resourceManifest = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(2, resourceManifest.payload()[1]);

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA,
                                new MessageWriter().writeByte(12).toByteArray()));
                Message iconManifest = codec.readServerResponse(transport.input(), cipher, true);
                var manifestReader = iconManifest.reader();
                assertEquals(12, manifestReader.readByte());
                assertEquals(2, manifestReader.readUnsignedShort());
                assertEquals(5, manifestReader.readShort());
                assertEquals(IconFingerprint.fingerprint64(iconData), manifestReader.readLong());
                assertEquals(10, manifestReader.readShort());
                assertEquals(IconFingerprint.fingerprint64(otherIconData), manifestReader.readLong());
                assertEquals(0, manifestReader.remaining());

                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.REQUEST_ICON,
                                new MessageWriter().writeShort(5).toByteArray()));
                Message response = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.REQUEST_ICON, response.command());
                var iconReader = response.reader();
                assertEquals(5, iconReader.readShort());
                assertEquals(iconData.length, iconReader.readInt());
                assertArrayEquals(iconData, iconReader.readBytes(iconData.length));
                assertEquals(0, iconReader.remaining());
            }
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during per-icon manifest test");
    }

    @Test
    void javaClientReceivesLargeIconAboveNormalTwoByteLength(@TempDir Path iconRoot) throws Exception {
        byte[] iconData = patternedBytes(70_000);
        Files.write(iconRoot.resolve("2170.png"), iconData);
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), GameResources.fromIconRoot(iconRoot)),
                null, ClientConfig.defaults());
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
                TestServices.serverServices(TestServices.authService(), GameResources.fromIconRoot(iconRoot)),
                null, ClientConfig.defaults());
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
    void manifestDoesNotPushMonsterResourceWithoutRequest() throws Exception {
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 1024, 8, 1_000,
                "abc".getBytes(StandardCharsets.US_ASCII),
                TestServices.serverServices(TestServices.authService(), GameResources.fromFrameRoot(
                        Path.of("resources", "json"))),
                null, ClientConfig.defaults());
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
            runMonsterManifestWithoutRequest(server.localPort());
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(), "network server failed during monster manifest test");
    }

    private static void runMonsterManifestWithoutRequest(int port) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(100);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, manifest.command());
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, 2, 1, -1, 0, 1, -1, -1, -1, -1
            }, manifest.payload());
            assertThrows(SocketTimeoutException.class,
                    () -> codec.readServerResponse(transport.input(), cipher, true));
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
            assertEquals(1, reader.readByte());
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

    private static void runFrameManifestWithoutRequest(int port) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(4096);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(100);
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());

            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            Message manifest = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.UPDATE_DATA, manifest.command());
            assertArrayEquals(new byte[]{
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, -1, -1, -1, -1
            }, manifest.payload());
            assertThrows(SocketTimeoutException.class,
                    () -> codec.readServerResponse(transport.input(), cipher, true));
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
}

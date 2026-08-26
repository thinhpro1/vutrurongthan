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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    void javaClientCreatesFreshPlayerAndParsesLegacyPlayerInfo() throws Exception {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        NetworkServer server = new NetworkServer("127.0.0.1", 0, 2, 262_144, 8, 1_000,
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
            ParsedPlayerInfo earth = runCreatePlayer(server.localPort(), "user01", "alpha1", 0);
            assertFreshPlayer(earth, "alpha1", 0, 5, 6,
                    List.of(0, 3, 6, 9, 12, 15, 30, 31, 32, 33, 36));
            assertEquals(List.of(new ParsedPaint("50.0", 0), new ParsedPaint("100.0", 1)),
                    earth.paints().get(0));
            assertEquals(List.of(new ParsedPaint("10.0", 5), new ParsedPaint("20.0", 17),
                            new ParsedPaint("30.0", 25)),
                    earth.paints().get(31));
            waitForNoSessions(server);

            ParsedPlayerInfo namek = runCreatePlayer(server.localPort(), "user02", "beta22", 1);
            assertFreshPlayer(namek, "beta22", 1, 3, 7,
                    List.of(1, 4, 7, 10, 13, 16, 30, 31, 32, 34, 36));
            waitForNoSessions(server);

            ParsedPlayerInfo saiyan = runCreatePlayer(server.localPort(), "user03", "gamma3", 2);
            assertFreshPlayer(saiyan, "gamma3", 2, 4, 8,
                    List.of(2, 5, 8, 11, 14, 17, 30, 31, 32, 35, 36));
            waitForNoSessions(server);
        } finally {
            server.stop();
            serverThread.join(1_000);
        }
        assertNull(serverFailure.get(),
                "network server failed during create-player PLAYER_INFO integration test");
    }

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

            // Model a restart after the client persisted frame version 1.
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
                    -1, -1, -1, -1, -1, -1, 0, -1, -1, 1, -1, -1, -1, -1
            }, manifest.payload());

            if (requestFrame) {
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(7).toByteArray()));
                Message frame = codec.readServerResponse(transport.input(), cipher, true);
                var reader = frame.reader();
                assertEquals(7, reader.readByte());
                assertEquals(1, reader.readByte());
                assertEquals(9, reader.readUnsignedShort());
            }
        }
    }

    private static void runIconRequest(int port) throws Exception {
        runIconRequest(port, 5, new byte[]{1, 2, 3, 4});
    }

    private static ParsedPlayerInfo runCreatePlayer(int port, String username,
                                                    String name, int gender) throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000)) {
            transport.socket().setSoTimeout(5_000);
            codec.writeClient(transport.output(), null, false,
                    new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
            LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
            Message version = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.VERSION_SOURCE, version.command());
            assertEquals("0.9.5", version.reader().readUtf());

            MessageWriter register = new MessageWriter()
                    .writeUtf(username)
                    .writeUtf("secret1");
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REGISTER_USER, register.toByteArray()));
            Message dialog = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.DIALOG_OK, dialog.command());
            assertEquals("Đăng ký thành công", dialog.reader().readUtf());

            MessageWriter login = new MessageWriter()
                    .writeUtf("0.9.5")
                    .writeUtf(username)
                    .writeUtf("secret1")
                    .writeByte(1);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.LOGIN, login.toByteArray()));
            Message createScreen = codec.readServerResponse(transport.input(), cipher, true);
            assertEquals(MessageName.START_CREATE_PLAYER_SCREEN, createScreen.command());
            assertEquals(0, createScreen.payload().length);

            MessageWriter create = new MessageWriter()
                    .writeUtf(name)
                    .writeByte(gender);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.CREATE_PLAYER, create.toByteArray()));
            return parsePlayerInfo(codec.readServerResponse(transport.input(), cipher, true));
        }
    }

    private static ParsedPlayerInfo parsePlayerInfo(Message message) throws IOException {
        assertEquals(MessageName.PLAYER_INFO, message.command());
        var reader = message.reader();
        assertEquals(0, reader.readByte());
        int id = reader.readInt();
        String name = reader.readUtf();
        int gender = reader.readByte();
        reader.readLong(); // power
        reader.readLong(); // potential
        reader.readShort(); // level
        reader.readShort(); // point skill
        int head = reader.readShort();
        int body = reader.readShort();
        reader.readShort(); // mount
        reader.readShort(); // bag
        reader.readShort(); // medal
        reader.readShort(); // aura
        int baseDamage = reader.readInt();
        int baseHp = reader.readInt();
        int baseMp = reader.readInt();
        int baseConstitution = reader.readInt();
        reader.readLong(); // potential up damage
        reader.readLong(); // potential up hp
        reader.readLong(); // potential up mp
        reader.readLong(); // potential up constitution
        long maxHp = reader.readLong();
        long maxMp = reader.readLong();
        long hp = reader.readLong();
        long mp = reader.readLong();
        reader.readByte(); // speed
        reader.readByte(); // point pk
        reader.readShort(); // point activity
        reader.readByte(); // barrack count
        reader.readUtf(); // dodge
        reader.readUtf(); // critical
        reader.readUtf(); // reduce damage
        reader.readUtf(); // bloodsucking
        reader.readUtf(); // mana sucking
        reader.readUtf(); // strike back
        reader.readLong(); // damage
        reader.readLong(); // coin
        reader.readLong(); // coin lock
        reader.readInt(); // diamond
        reader.readInt(); // ruby
        reader.readByte(); // spaceship

        int skillCount = reader.readUnsignedByte();
        List<Integer> skillIds = new ArrayList<>(skillCount);
        Map<Integer, List<ParsedPaint>> paintsBySkillId = new LinkedHashMap<>();
        for (int skillIndex = 0; skillIndex < skillCount; skillIndex++) {
            int skillId = reader.readByte();
            skillIds.add(skillId);
            skipUtfList(reader);
            skipUtfList(reader);
            reader.readByte(); // type
            boolean proactive = reader.readBoolean();
            skipShortList(reader);
            skipShortMatrix(reader);
            skipShortMatrix(reader);
            reader.readShort(); // level require
            reader.readByte(); // max level
            reader.readByte(); // max upgrade
            skipIntList(reader);
            skipIntMatrix(reader);
            reader.readByte(); // type mana
            skipIntMatrix(reader);
            int optionCount = reader.readUnsignedByte();
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                reader.readByte();
                reader.readUtf();
                skipShortList(reader);
                skipShortList(reader);
            }
            int level = reader.readByte();
            reader.readByte(); // upgrade
            reader.readInt(); // point
            reader.readByte(); // cooldown reduction
            if (level > 0 && proactive) {
                reader.readLong();
            }
            int paintCount = reader.readUnsignedByte();
            List<ParsedPaint> paints = new ArrayList<>(paintCount);
            for (int paintIndex = 0; paintIndex < paintCount; paintIndex++) {
                paints.add(new ParsedPaint(reader.readUtf(), reader.readShort()));
            }
            paintsBySkillId.put(skillId, List.copyOf(paints));
        }
        int keySkillCount = reader.readUnsignedByte();
        List<Integer> keySkillIds = new ArrayList<>(keySkillCount);
        for (int index = 0; index < keySkillCount; index++) {
            keySkillIds.add((int) reader.readByte());
        }
        int mySkillId = reader.readByte();
        int effectCount = reader.readUnsignedByte();
        for (int index = 0; index < effectCount; index++) {
            reader.readShort();
            reader.readLong();
        }
        assertEquals(0, reader.remaining());
        return new ParsedPlayerInfo(id, name, gender, head, body, baseDamage, baseHp, baseMp,
                baseConstitution, maxHp, maxMp, hp, mp,
                List.copyOf(skillIds), Map.copyOf(paintsBySkillId), List.copyOf(keySkillIds), mySkillId);
    }

    private static void assertFreshPlayer(ParsedPlayerInfo player, String name, int gender,
                                          int head, int body, List<Integer> skillIds) {
        assertTrue(player.id() > 0);
        assertEquals(name, player.name());
        assertEquals(gender, player.gender());
        assertEquals(head, player.head());
        assertEquals(body, player.body());
        assertEquals(10, player.baseDamage());
        assertEquals(5, player.baseHp());
        assertEquals(5, player.baseMp());
        assertEquals(5, player.baseConstitution());
        assertEquals(150, player.maxHp());
        assertEquals(150, player.maxMp());
        assertEquals(100, player.hp());
        assertEquals(100, player.mp());
        assertEquals(skillIds, player.skillIds());
        assertEquals(List.of(gender, -1, -1, -1, -1, -1), player.keySkillIds());
        assertEquals(gender, player.mySkillId());
    }

    private static void skipUtfList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readUtf();
        }
    }

    private static void skipShortList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readShort();
        }
    }

    private static void skipShortMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipShortList(reader);
        }
    }

    private static void skipIntList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readInt();
        }
    }

    private static void skipIntMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipIntList(reader);
        }
    }

    private record ParsedPlayerInfo(
            int id,
            String name,
            int gender,
            int head,
            int body,
            int baseDamage,
            int baseHp,
            int baseMp,
            int baseConstitution,
            long maxHp,
            long maxMp,
            long hp,
            long mp,
            List<Integer> skillIds,
            Map<Integer, List<ParsedPaint>> paints,
            List<Integer> keySkillIds,
            int mySkillId
    ) {}

    private record ParsedPaint(String percent, int paintId) {}

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

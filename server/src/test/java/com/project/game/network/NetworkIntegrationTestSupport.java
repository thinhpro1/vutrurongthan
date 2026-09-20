package com.project.game.network;


import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;
import com.project.game.testsupport.GameplayServices;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkIntegrationTestSupport {

    static void sendMove(
            LegacyPacketCodec codec,
            LegacyTcpTransport transport,
            LegacyCipher cipher,
            int x,
            int y) throws IOException {
        codec.writeClient(
                transport.output(),
                cipher,
                true,
                new Message(
                        MessageName.PLAYER_MOVE,
                        new MessageWriter()
                                .writeShort(x)
                                .writeShort(y)
                                .toByteArray()));
    }

    static void waitForPlayerPosition(
            NetworkServer server,
            String accountName,
            int expectedX,
            int expectedY) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Session session = server.sessions().findByAccount(accountName);
            if (session != null
                    && session.player() != null
                    && session.player().x() == expectedX
                    && session.player().y() == expectedY) {
                return;
            }
            Thread.sleep(10);
        }

        Session session = server.sessions().findByAccount(accountName);
        if (session == null) {
            throw new AssertionError("session disappeared before PLAYER_MOVE was observed");
        }
        throw new AssertionError(
                "expected PLAYER_MOVE position "
                        + expectedX + "," + expectedY
                        + " but was "
                        + session.player().x() + "," + session.player().y());
    }

    static EnterGameResponses runCreatePlayer(int port, String username,
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
            Message playerMessage = codec.readServerResponse(transport.input(), cipher, true);
            Message mapMessage = codec.readServerResponse(transport.input(), cipher, true);
            return new EnterGameResponses(parsePlayerInfo(playerMessage), parseMapInfo(mapMessage));
        }
    }

    static EnterGameResponses runLoginExistingPlayer(int port, String username,
                                                              String password) throws Exception {
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

            MessageWriter login = new MessageWriter()
                    .writeUtf("0.9.5")
                    .writeUtf(username)
                    .writeUtf(password)
                    .writeByte(1);
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.LOGIN, login.toByteArray()));
            Message playerMessage = codec.readServerResponse(transport.input(), cipher, true);
            Message mapMessage = codec.readServerResponse(transport.input(), cipher, true);
            return new EnterGameResponses(parsePlayerInfo(playerMessage), parseMapInfo(mapMessage));
        }
    }

    static ParsedPlayerInfo parsePlayerInfo(Message message) throws IOException {
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

    static ParsedMapInfo parseMapInfo(Message message) throws IOException {
        assertEquals(MessageName.MAP_INFO, message.command());
        return parseMapInfo(message, new HashSet<>());
    }

    static ParsedMapInfo parseMapInfo(Message message, Set<Integer> cachedMapIds)
            throws IOException {
        assertEquals(MessageName.MAP_INFO, message.command());
        var reader = message.reader();

        int mapId = reader.readShort();
        int iconId = 0;
        String name = null;
        int row = 0;
        int column = 0;
        String data = null;
        List<Integer> imagesBgr = List.of();
        List<List<Integer>> colorsBgr = List.of();
        boolean line = false;
        String dataLine = null;
        if (cachedMapIds.add(mapId)) {
            iconId = reader.readShort();
            name = reader.readUtf();
            row = reader.readShort();
            column = reader.readShort();
            data = reader.readUtf();

            List<Integer> imageValues = new ArrayList<>(3);
            for (int index = 0; index < 3; index++) {
                imageValues.add((int) reader.readShort());
            }
            imagesBgr = List.copyOf(imageValues);

            List<List<Integer>> colorValues = new ArrayList<>(4);
            for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
                List<Integer> color = new ArrayList<>(3);
                for (int columnIndex = 0; columnIndex < 3; columnIndex++) {
                    color.add((int) reader.readShort());
                }
                colorValues.add(List.copyOf(color));
            }
            colorsBgr = List.copyOf(colorValues);

            line = reader.readBoolean();
            dataLine = line ? reader.readUtf() : null;
        }

        int zoneId = reader.readByte();
        int x = reader.readShort();
        int y = reader.readShort();
        int waypointCount = reader.readUnsignedByte();
        List<ParsedWaypoint> waypoints = new ArrayList<>(waypointCount);
        for (int index = 0; index < waypointCount; index++) {
            waypoints.add(new ParsedWaypoint(
                    reader.readShort(), reader.readShort(), reader.readByte(), reader.readUtf()));
        }
        int npcCount = reader.readUnsignedByte();
        int monsterCount = reader.readUnsignedByte();
        List<ParsedMonsterSpawn> monsters = new ArrayList<>(monsterCount);
        for (int index = 0; index < monsterCount; index++) {
            monsters.add(new ParsedMonsterSpawn(
                    reader.readByte(),
                    reader.readShort(),
                    reader.readInt(),
                    reader.readShort(),
                    reader.readByte(),
                    reader.readShort(),
                    reader.readShort(),
                    reader.readLong(),
                    reader.readLong(),
                    reader.readByte()));
        }
        int itemMapCount = reader.readUnsignedShort();
        boolean dragonActive = reader.readBoolean();

        return new ParsedMapInfo(
                mapId, iconId, name, row, column, data,
                List.copyOf(imagesBgr), List.copyOf(colorsBgr), line, dataLine,
                zoneId, x, y, waypoints, npcCount, List.copyOf(monsters), itemMapCount,
                dragonActive, reader.remaining());
    }

    static void assertAddPlayer(Message message, int expectedId,
                                        String expectedName, int expectedGender) throws IOException {
        assertEquals(MessageName.ADD_PLAYER, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedName, reader.readUtf());
        assertEquals(expectedGender, reader.readByte());
        int expectedHead = switch (expectedGender) {
            case 0 -> 5;
            case 1 -> 3;
            default -> 4;
        };
        int expectedBody = switch (expectedGender) {
            case 0 -> 6;
            case 1 -> 7;
            default -> 8;
        };
        assertEquals(expectedHead, reader.readShort());
        assertEquals(expectedBody, reader.readShort());
        assertEquals(-1, reader.readShort()); // mount
        assertEquals(-1, reader.readShort()); // bag
        assertEquals(-1, reader.readShort()); // medal
        assertEquals(-1, reader.readShort()); // aura
        assertEquals(1250, reader.readShort());
        assertEquals(648, reader.readShort());
        assertEquals(200, reader.readLong());
        assertEquals(200, reader.readLong());
        assertEquals(0, reader.readByte()); // typePk
        assertEquals(0, reader.readByte()); // typeFlag
        assertEquals(1, reader.readShort());
        assertEquals(0, reader.readByte()); // spaceship
        assertEquals(12, reader.readByte()); // speed
        assertEquals(-1, reader.readInt()); // no clan
        assertEquals(-1, reader.readByte()); // no equipped upgrade
        assertEquals(0, reader.readByte()); // no runtime effects
        assertEquals(0, reader.remaining());
    }

    static void assertAddPlayerId(Message message, int expectedId) throws IOException {
        assertEquals(MessageName.ADD_PLAYER, message.command());
        assertEquals(expectedId, message.reader().readInt());
    }

    static void assertMonsterInjure(Message message, int expectedId,
                                             long expectedDamage, long expectedHp) throws IOException {
        assertEquals(MessageName.MONSTER_INJURE, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertEquals(expectedHp, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    static void assertMonsterDeath(Message message, int expectedId,
                                            long expectedDamage) throws IOException {
        assertEquals(MessageName.MONSTER_START_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    static void assertPotentialReward(Message message, long expectedPotential)
            throws IOException {
        assertEquals(MessageName.PLAYER_INFO, message.command());
        var reader = message.reader();
        assertEquals(62, reader.readByte());
        assertEquals(expectedPotential, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    static void assertMonsterRespawn(Message message, int expectedId,
                                              int expectedLevelStatus, long expectedHp)
            throws IOException {
        assertEquals(MessageName.MONSTER_RESPAWN, message.command());
        var reader = message.reader();
        assertEquals(expectedId, reader.readInt());
        assertEquals(expectedLevelStatus, reader.readByte());
        assertEquals(expectedHp, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    static void assertMonsterAttack(Message message, int expectedMonsterId,
                                             int expectedPlayerId, long expectedDamage)
            throws IOException {
        assertEquals(MessageName.MONSTER_ATTACK, message.command());
        assertEquals(17, message.payload().length);
        var reader = message.reader();
        assertEquals(expectedMonsterId, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(expectedPlayerId, reader.readInt());
        assertEquals(expectedDamage, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    static MonsterMoveView readMonsterMove(LivePlayerClient client, int expectedMonsterId)
            throws IOException {
        while (true) {
            Message message = client.readRawServerMessage();
            if (message.command() != MessageName.MONSTER_MOVE) {
                continue;
            }
            var reader = message.reader();
            MonsterMoveView move = new MonsterMoveView(
                    reader.readInt(), reader.readShort(), reader.readShort(), reader.readByte());
            assertEquals(0, reader.remaining());
            if (move.monsterId() == expectedMonsterId) {
                return move;
            }
        }
    }

    static void assertMeDie(Message message, int expectedX, int expectedY)
            throws IOException {
        assertEquals(MessageName.ME_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedX, reader.readShort());
        assertEquals(expectedY, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    static void assertPlayerDie(Message message, int expectedPlayerId,
                                         int expectedX, int expectedY) throws IOException {
        assertEquals(MessageName.PLAYER_DIE, message.command());
        var reader = message.reader();
        assertEquals(expectedPlayerId, reader.readInt());
        assertEquals(expectedX, reader.readShort());
        assertEquals(expectedY, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    static void assertNoServerMessage(LivePlayerClient client) throws Exception {
        client.transport.socket().setSoTimeout(50);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        try {
            while (System.nanoTime() < deadline) {
                try {
                    Message message = client.readRawServerMessage();
                    if (message.command() != MessageName.MONSTER_MOVE) {
                        throw new AssertionError("received an unexpected server packet: "
                                + message.command());
                    }
                } catch (SocketTimeoutException expected) {
                    return;
                }
            }
        } finally {
            client.transport.socket().setSoTimeout(5_000);
        }
    }

    static void skipUtfList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readUtf();
        }
    }

    static void skipShortList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readShort();
        }
    }

    static void skipShortMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipShortList(reader);
        }
    }

    static void skipIntList(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int count = reader.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            reader.readInt();
        }
    }

    static void skipIntMatrix(com.project.game.network.message.MessageReader reader)
            throws IOException {
        int rows = reader.readUnsignedByte();
        for (int index = 0; index < rows; index++) {
            skipIntList(reader);
        }
    }

    static byte[] reconstructKey(byte[] payload) {
        int length = Byte.toUnsignedInt(payload[0]);
        byte[] key = new byte[length];
        key[0] = payload[1];
        for (int index = 1; index < length; index++) {
            key[index] = (byte) (Byte.toUnsignedInt(payload[index + 1]) ^ Byte.toUnsignedInt(key[index - 1]));
        }
        return key;
    }

    static void waitForPort(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.localPort() != 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("server did not bind a port");
    }

    static void awaitPlayerPosition(NetworkServer server, String account,
                                             int x, int y) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Session session = server.sessions().findByAccount(account);
            if (session != null && session.player() != null
                    && session.player().x() == x && session.player().y() == y) {
                return;
            }
            Thread.sleep(1);
        }
        Session session = server.sessions().findByAccount(account);
        assertTrue(session != null && session.player() != null,
                "player session disappeared while awaiting movement");
        assertEquals(x, session.player().x());
        assertEquals(y, session.player().y());
    }

    static void awaitMemberCount(GameplayServices maps, int mapId, int zoneId, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (maps.memberCount(mapId, zoneId) == expected) {
                return;
            }
            Thread.sleep(1);
        }
        assertEquals(expected, maps.memberCount(mapId, zoneId));
    }

    static void waitForNoSessions(NetworkServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (server.sessions().onlineCount() == 0) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(server.sessions().onlineCount() == 0, "session leak after disconnect");
    }

    record ParsedPlayerInfo(
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

    record ParsedPaint(String percent, int paintId) {}

    record EnterGameResponses(
            ParsedPlayerInfo playerInfo,
            ParsedMapInfo mapInfo
    ) {}

    record ParsedMapInfo(
            int mapId,
            int iconId,
            String name,
            int row,
            int column,
            String data,
            List<Integer> imagesBgr,
            List<List<Integer>> colorsBgr,
            boolean line,
            String dataLine,
            int zoneId,
            int x,
            int y,
            List<ParsedWaypoint> waypoints,
            int npcCount,
            List<ParsedMonsterSpawn> monsters,
            int itemMapCount,
            boolean dragonActive,
            int remaining
    ) {
        int monsterCount() {
            return monsters.size();
        }
    }

    record ParsedMonsterSpawn(
            int type,
            int templateId,
            int id,
            int level,
            int levelStatus,
            int x,
            int y,
            long maxHp,
            long hp,
            int status
    ) {}

    record MonsterMoveView(int monsterId, int x, int y, int dir) {}

    record ParsedWaypoint(int x, int y, int type, String name) {}

    static final class LivePlayerClient implements AutoCloseable {
        final LegacyPacketCodec codec;
        final LegacyTcpTransport transport;
        final LegacyCipher cipher;
        final ParsedPlayerInfo playerInfo;
        final Set<Integer> cachedMapIds;

        LivePlayerClient(LegacyPacketCodec codec, LegacyTcpTransport transport,
                                 LegacyCipher cipher, ParsedPlayerInfo playerInfo,
                                 Set<Integer> cachedMapIds) {
            this.codec = codec;
            this.transport = transport;
            this.cipher = cipher;
            this.playerInfo = playerInfo;
            this.cachedMapIds = cachedMapIds;
        }

        static LivePlayerClient create(int port, String username,
                                               String playerName, int gender) throws Exception {
            LegacyPacketCodec codec = new LegacyPacketCodec(262_144);
            LegacyTcpTransport transport = LegacyTcpTransport.connect("127.0.0.1", port, 1_000);
            try {
                transport.socket().setSoTimeout(5_000);
                codec.writeClient(transport.output(), null, false,
                        new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(transport.input(), null, false);
                assertEquals(MessageName.SEND_SESSION_KEY, handshake.command());
                LegacyCipher cipher = new LegacyCipher(reconstructKey(handshake.payload()));
                Message version = codec.readServerResponse(transport.input(), cipher, true);
                assertEquals(MessageName.VERSION_SOURCE, version.command());
                assertEquals("0.9.5", version.reader().readUtf());

                MessageWriter login = new MessageWriter()
                        .writeUtf("0.9.5")
                        .writeUtf(username)
                        .writeUtf("secret1")
                        .writeByte(1);
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.LOGIN, login.toByteArray()));
                assertEquals(MessageName.START_CREATE_PLAYER_SCREEN,
                        codec.readServerResponse(transport.input(), cipher, true).command());

                MessageWriter create = new MessageWriter()
                        .writeUtf(playerName)
                        .writeByte(gender);
                codec.writeClient(transport.output(), cipher, true,
                        new Message(MessageName.CREATE_PLAYER, create.toByteArray()));
                ParsedPlayerInfo player = parsePlayerInfo(
                        codec.readServerResponse(transport.input(), cipher, true));
                Set<Integer> cachedMapIds = new HashSet<>();
                parseMapInfo(codec.readServerResponse(transport.input(), cipher, true), cachedMapIds);
                return new LivePlayerClient(codec, transport, cipher, player, cachedMapIds);
            } catch (Throwable failure) {
                try {
                    transport.close();
                } catch (IOException ignored) {
                }
                throw failure;
            }
        }

        ParsedPlayerInfo playerInfo() {
            return playerInfo;
        }

        void finishLoadMap() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.FINISH_LOAD_MAP));
        }

        void returnTownFromDie() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.RETURN_TOWN_FROM_DIE));
        }

        void wakeUpFromDieRequest() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.WAKE_UP_FROM_DIE));
        }

        void prepareMonsterAttack(int skillId, int monsterId) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(
                            MessageName.PLAYER_START_USE_ULTIMATE,
                            new MessageWriter()
                                    .writeByte(skillId)
                                    .writeByte(1)
                                    .writeInt(monsterId)
                                    .toByteArray()));
        }

        void impactMonster(int monsterId) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(
                            MessageName.USE_SKILL,
                            new MessageWriter()
                                    .writeByte(1)
                                    .writeInt(monsterId)
                                    .toByteArray()));
        }

        void move(int x, int y) throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.PLAYER_MOVE,
                            new MessageWriter().writeShort(x).writeShort(y).toByteArray()));
        }

        Message readServerMessage() throws IOException {
            Message message;
            do {
                message = readRawServerMessage();
            } while (message.command() == MessageName.MONSTER_MOVE);
            return message;
        }

        Message readRawServerMessage() throws IOException {
            return codec.readServerResponse(transport.input(), cipher, true);
        }

        ParsedMapInfo readMapInfo() throws IOException {
            return parseMapInfo(readServerMessage(), cachedMapIds);
        }

        void requestChangeMap() throws IOException {
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.REQUEST_CHANGE_MAP));
        }

        @Override
        public void close() throws IOException {
            transport.close();
        }
    }
}

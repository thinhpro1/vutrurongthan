package com.project.game.network.packet;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerPacketWriterTest {
    @Test
    void usesLegacyCommandIds() {
        PlayerPacketWriter writer = new PlayerPacketWriter();
        PlayerProfile player = PlayerProfile.initial(1L, 7, "alpha1", 0);

        assertEquals(MessageName.ADD_PLAYER, writer.addPlayer(player).command());
        assertEquals(MessageName.REMOVE_PLAYER, writer.removePlayer(player.id()).command());
        assertEquals(MessageName.PLAYER_MOVE, writer.movePlayer(player.id(), 1260, 640).command());
        assertEquals(MessageName.PLAYER_INFO, writer.potentialUpdate(11L).command());
    }

    @Test
    void serializesRemovePlayerAsOnlyPlayerId() throws Exception {
        Message message = new PlayerPacketWriter().removePlayer(0x01020304);
        var reader = message.reader();

        assertEquals(0x01020304, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesAuthoritativePotentialUpdate() throws Exception {
        Message message = new PlayerPacketWriter().potentialUpdate(0x0102030405060708L);

        assertEquals(MessageName.PLAYER_INFO, message.command());

        var reader = message.reader();
        assertEquals(62, reader.readByte());
        assertEquals(0x0102030405060708L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesMeDiePacketExactly() throws Exception {
        Message packet = new PlayerPacketWriter().meDie(321, 654);

        assertEquals(MessageName.ME_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(321, reader.readShort());
        assertEquals(654, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesPlayerDiePacketExactly() throws Exception {
        Message packet = new PlayerPacketWriter().playerDie(77, 321, 654);

        assertEquals(MessageName.PLAYER_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(77, reader.readInt());
        assertEquals(321, reader.readShort());
        assertEquals(654, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesWakeUpFromDiePacketExactly() throws Exception {
        PlayerProfile player = PlayerProfile.initial(77L, 77, "wake1", 0)
                .revivedAt(0, 0, 1250, 648);

        Message packet = new PlayerPacketWriter().wakeUpFromDie(player);

        assertEquals(MessageName.WAKE_UP_FROM_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(77, reader.readInt());
        assertEquals(1250, reader.readShort());
        assertEquals(648, reader.readShort());
        assertEquals(player.currentStats().maxHp(), reader.readLong());
        assertEquals(player.currentStats().maxMp(), reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesOtherPlayerMovement() throws Exception {
        Message message = new PlayerPacketWriter().movePlayer(0x01020304, 1260, 640);
        var reader = message.reader();

        assertEquals(0x01020304, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesCanonicalNormalPlayerPayload() throws Exception {
        PlayerProfile player = PlayerProfile.initial(1L, 0x01020304, "alpha1", 0);
        Message message = new PlayerPacketWriter().addPlayer(player);
        var reader = message.reader();

        assertEquals(player.id(), reader.readInt());
        assertEquals(player.name(), reader.readUtf());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(player.appearance().head(), reader.readShort());
        assertEquals(player.appearance().body(), reader.readShort());
        assertEquals(player.appearance().mount(), reader.readShort());
        assertEquals(player.appearance().bag(), reader.readShort());
        assertEquals(player.appearance().medal(), reader.readShort());
        assertEquals(player.appearance().aura(), reader.readShort());
        assertEquals(player.x(), reader.readShort());
        assertEquals(player.y(), reader.readShort());
        assertEquals(player.currentStats().maxHp(), reader.readLong());
        assertEquals(player.hp(), reader.readLong());
        assertEquals(0, reader.readByte()); // normal typePk
        assertEquals(0, reader.readByte()); // normal typeFlag
        assertEquals(player.level(), reader.readShort());
        assertEquals(player.appearance().spaceship(), reader.readByte());
        assertEquals(player.currentStats().speed(), reader.readByte());
        assertEquals(-1, reader.readInt()); // no clan
        assertEquals(-1, reader.readByte()); // old upgrade/levelEquip sentinel
        assertEquals(0, reader.readByte()); // no runtime effects
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsValuesThatWouldBeTruncatedByLegacyAddPlayerWire() {
        PlayerPacketWriter writer = new PlayerPacketWriter();

        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(Short.MAX_VALUE + 1, 12, 0, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, Byte.MAX_VALUE + 1, 0, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, 12, Byte.MAX_VALUE + 1, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, 12, 0, Short.MAX_VALUE + 1, 648)));
    }

    private static PlayerProfile playerWith(
            int level, int speed, int spaceship, int x, int y) {
        var base = new com.project.game.player.BaseStats(200, 200, 10, 0, 0, 0, 5, speed);
        var current = new com.project.game.player.CurrentStats(
                200, 200, 10, 0, 0, 0, 5, speed);
        return new PlayerProfile(
                7, 1L, "alpha1", 0, 1L, 1L, level, 0L,
                base, current, 200, 200,
                new com.project.game.player.Appearance(5, 6, -1, -1, -1, -1, spaceship),
                0L, 10_000L, 0, 25, 0, 0, x, y);
    }
}

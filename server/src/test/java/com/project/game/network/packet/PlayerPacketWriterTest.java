package com.project.game.network.packet;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPacketWriterTest {
    @Test
    void usesLegacyCommandIds() {
        PlayerPacketWriter writer = new PlayerPacketWriter();
        PlayerProfile player = PlayerProfile.initial("user01", 7, "alpha1", 0);

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
        PlayerProfile player = PlayerProfile.initial("user01", 0x01020304, "alpha1", 0);
        Message message = new PlayerPacketWriter().addPlayer(player);
        var reader = message.reader();

        assertEquals(player.id(), reader.readInt());
        assertEquals(player.name(), reader.readUtf());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(player.head(), reader.readShort());
        assertEquals(player.body(), reader.readShort());
        assertEquals(player.mount(), reader.readShort());
        assertEquals(player.bag(), reader.readShort());
        assertEquals(player.medal(), reader.readShort());
        assertEquals(player.aura(), reader.readShort());
        assertEquals(player.x(), reader.readShort());
        assertEquals(player.y(), reader.readShort());
        assertEquals(player.maxHp(), reader.readLong());
        assertEquals(player.hp(), reader.readLong());
        assertEquals(0, reader.readByte()); // normal typePk
        assertEquals(0, reader.readByte()); // normal typeFlag
        assertEquals(player.level(), reader.readShort());
        assertEquals(player.spaceship(), reader.readByte());
        assertEquals(player.speed(), reader.readByte());
        assertEquals(-1, reader.readInt()); // no clan
        assertEquals(-1, reader.readByte()); // old upgrade/levelEquip sentinel
        assertEquals(0, reader.readByte()); // no runtime effects
        assertEquals(0, reader.remaining());
    }
}

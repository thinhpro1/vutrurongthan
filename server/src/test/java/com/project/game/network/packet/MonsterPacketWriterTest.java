package com.project.game.network.packet;

import com.project.game.monster.Monster;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.Player;
import com.project.game.testsupport.TestPlayers;
import com.project.game.testsupport.TestZone;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Bytes giữ nguyên như trước khi bỏ các record kết quả: Unity không phải sửa. */
class MonsterPacketWriterTest {
    private final MonsterPacketWriter writer = new MonsterPacketWriter();

    @Test
    void writesMonsterInjurePacket() throws Exception {
        Monster monster = monster(101);
        monster.injure(attacker(), 10, 1_000_000L);

        Message message = writer.injure(monster, 10);

        assertEquals(MessageName.MONSTER_INJURE, message.command());
        var reader = message.reader();
        assertEquals(101, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesMonsterStartDiePacket() throws Exception {
        Message message = writer.startDie(monster(101), 10);

        assertEquals(MessageName.MONSTER_START_DIE, message.command());
        var reader = message.reader();
        assertEquals(101, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesMonsterRespawnPacket() throws Exception {
        Message message = writer.respawn(monster(101));

        assertEquals(MessageName.MONSTER_RESPAWN, message.command());
        assertEquals(13, message.payload().length);
        var reader = message.reader();
        assertEquals(101, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(300L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesExactPlayerTargetMonsterAttackPayload() throws Exception {
        Player target = TestPlayers.initial(42L, 42, "player42", 1);

        Message message = writer.attackPlayer(monster(101), target, 10L);

        assertEquals(MessageName.MONSTER_ATTACK, message.command());
        assertEquals(17, message.payload().length);
        var reader = message.reader();
        assertEquals(101, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(42, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesExactMonsterMovePayload() throws Exception {
        Monster monster = monster(101);
        setInt(monster, "x", 1234);
        setInt(monster, "moveDir", -1);

        Message message = writer.move(monster);

        assertEquals(MessageName.MONSTER_MOVE, message.command());
        assertEquals(9, message.payload().length);
        var reader = message.reader();
        assertEquals(101, reader.readInt());
        assertEquals(1234, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(-1, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsNullMonster() {
        assertThrows(NullPointerException.class, () -> writer.respawn(null));
        assertThrows(NullPointerException.class, () -> writer.move(null));
        assertThrows(NullPointerException.class, () -> writer.injure(null, 1));
        assertThrows(NullPointerException.class,
                () -> writer.attackPlayer(monster(101), null, 1));
    }

    private static Monster monster(int id) {
        return new TestZone().monster(id);
    }

    private static Player attacker() {
        return TestPlayers.initial(7L, 7, "player7", 1);
    }

    private static void setInt(Monster monster, String name, int value) throws Exception {
        Field field = Monster.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(monster, value);
    }
}

package com.project.game.network.packet;

import com.project.game.monster.MonsterDamageResult;
import com.project.game.monster.MonsterAttackResult;
import com.project.game.monster.MonsterRespawnResult;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonsterPacketWriterTest {
    @Test
    void writesMonsterInjurePacket() throws Exception {
        MonsterDamageResult result = new MonsterDamageResult(3, 10, 290, false);
        Message message = new MonsterPacketWriter().injure(result);

        assertEquals(MessageName.MONSTER_INJURE, message.command());
        var reader = message.reader();
        assertEquals(3, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(290L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesMonsterStartDiePacket() throws Exception {
        MonsterDamageResult result = new MonsterDamageResult(3, 10, 0, true);
        Message message = new MonsterPacketWriter().startDie(result);

        assertEquals(MessageName.MONSTER_START_DIE, message.command());
        var reader = message.reader();
        assertEquals(3, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsMismatchedCombatResultPacketType() {
        MonsterPacketWriter writer = new MonsterPacketWriter();

        assertThrows(IllegalArgumentException.class,
                () -> writer.injure(new MonsterDamageResult(0, 10, 0, true)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.startDie(new MonsterDamageResult(0, 10, 290, false)));
    }

    @Test
    void writesMonsterRespawnPacket() throws Exception {
        MonsterRespawnResult result = new MonsterRespawnResult(3, 0, 300L);

        Message message = new MonsterPacketWriter().respawn(result);

        assertEquals(MessageName.MONSTER_RESPAWN, message.command());
        assertEquals(13, message.payload().length);

        var reader = message.reader();
        assertEquals(3, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(300L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsNullMonsterRespawnResult() {
        MonsterPacketWriter writer = new MonsterPacketWriter();

        assertThrows(NullPointerException.class, () -> writer.respawn(null));
    }

    @Test
    void writesExactPlayerTargetMonsterAttackPayload() throws Exception {
        Message message = new MonsterPacketWriter().attackPlayer(
                new MonsterAttackResult(17, 42, 10L, 90L));

        assertEquals(MessageName.MONSTER_ATTACK, message.command());
        assertEquals(17, message.payload().length);
        var reader = message.reader();
        assertEquals(17, reader.readInt());
        assertEquals(0, reader.readByte());
        assertEquals(42, reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void monsterAttackPacketDoesNotEncodeHpAfter() {
        MonsterPacketWriter writer = new MonsterPacketWriter();

        Message first = writer.attackPlayer(new MonsterAttackResult(1, 2, 10L, 90L));
        Message second = writer.attackPlayer(new MonsterAttackResult(1, 2, 10L, 80L));

        assertArrayEquals(first.payload(), second.payload());
    }

    @Test
    void rejectsNullMonsterAttackResult() {
        assertThrows(NullPointerException.class,
                () -> new MonsterPacketWriter().attackPlayer(null));
    }
}

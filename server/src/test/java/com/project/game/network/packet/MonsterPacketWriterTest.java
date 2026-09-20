package com.project.game.network.packet;

import com.project.game.monster.Monster;

import com.project.game.monster.MonsterAttack;
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
        Monster.Damage result = new Monster.Damage(3, 10, 290, false, 0L);
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
        Monster.Damage result = new Monster.Damage(3, 10, 0, true, 10L);
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
                () -> writer.injure(new Monster.Damage(0, 10, 0, true, 10L)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.startDie(new Monster.Damage(0, 10, 290, false, 0L)));
    }

    @Test
    void writesMonsterRespawnPacket() throws Exception {
        Monster.Respawn result = new Monster.Respawn(3, 0, 300L);

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
    void rejectsNullRespawn() {
        MonsterPacketWriter writer = new MonsterPacketWriter();

        assertThrows(NullPointerException.class, () -> writer.respawn(null));
    }

    @Test
    void writesExactPlayerTargetMonsterAttackPayload() throws Exception {
        Message message = new MonsterPacketWriter().attackPlayer(
                new MonsterAttack(17, 42, 10L, 90L, false));

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

        Message first = writer.attackPlayer(new MonsterAttack(1, 2, 10L, 90L, false));
        Message second = writer.attackPlayer(new MonsterAttack(1, 2, 10L, 80L, false));

        assertArrayEquals(first.payload(), second.payload());
    }

    @Test
    void rejectsNullMonsterAttack() {
        assertThrows(NullPointerException.class,
                () -> new MonsterPacketWriter().attackPlayer(null));
    }

    @Test
    void writesExactMonsterMovePayload() throws Exception {
        Message message = new MonsterPacketWriter().move(
                new Monster.Move(17, 1234, 936, -1));

        assertEquals(MessageName.MONSTER_MOVE, message.command());
        assertEquals(9, message.payload().length);

        var reader = message.reader();
        assertEquals(17, reader.readInt());
        assertEquals(1234, reader.readShort());
        assertEquals(936, reader.readShort());
        assertEquals(-1, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsNullMove() {
        assertThrows(NullPointerException.class,
                () -> new MonsterPacketWriter().move(null));
    }
}

package com.project.game.network.packet;

import com.project.game.monster.MonsterDamageResult;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

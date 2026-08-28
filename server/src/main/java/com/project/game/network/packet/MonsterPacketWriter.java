package com.project.game.network.packet;

import com.project.game.monster.MonsterDamageResult;
import com.project.game.monster.MonsterAttackResult;
import com.project.game.monster.MonsterRespawnResult;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;

import java.util.Objects;

public final class MonsterPacketWriter {
    public Message injure(MonsterDamageResult result) {
        Objects.requireNonNull(result, "result");
        if (result.killed()) {
            throw new IllegalArgumentException("killed result requires startDie");
        }
        return new Message(
                MessageName.MONSTER_INJURE,
                new MessageWriter()
                        .writeInt(result.monsterId())
                        .writeLong(result.damage())
                        .writeLong(result.hpAfter())
                        .writeBoolean(false)
                        .toByteArray());
    }

    public Message startDie(MonsterDamageResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.killed()) {
            throw new IllegalArgumentException("live result requires injure");
        }
        return new Message(
                MessageName.MONSTER_START_DIE,
                new MessageWriter()
                        .writeInt(result.monsterId())
                        .writeLong(result.damage())
                        .writeBoolean(false)
                        .toByteArray());
    }

    public Message respawn(MonsterRespawnResult result) {
        Objects.requireNonNull(result, "result");
        return new Message(
                MessageName.MONSTER_RESPAWN,
                new MessageWriter()
                        .writeInt(result.monsterId())
                        .writeByte(result.levelStatus())
                        .writeLong(result.hp())
                        .toByteArray());
    }

    public Message attackPlayer(MonsterAttackResult result) {
        Objects.requireNonNull(result, "result");
        return new Message(
                MessageName.MONSTER_ATTACK,
                new MessageWriter()
                        .writeInt(result.monsterId())
                        .writeByte(0)
                        .writeInt(result.playerId())
                        .writeLong(result.damage())
                        .toByteArray());
    }
}

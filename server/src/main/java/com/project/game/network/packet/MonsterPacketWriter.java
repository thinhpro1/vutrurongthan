package com.project.game.network.packet;

import com.project.game.monster.Monster;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.Player;

import java.util.Objects;

/** Bytes của các packet Monster; gameplay quyết định chuyện gì xảy ra, file này chỉ đóng gói. */
public final class MonsterPacketWriter {
    public Message injure(Monster monster, long damage) {
        Objects.requireNonNull(monster, "monster");
        return new Message(
                MessageName.MONSTER_INJURE,
                new MessageWriter()
                        .writeInt(monster.id())
                        .writeLong(damage)
                        .writeLong(monster.hp())
                        .writeBoolean(false)
                        .toByteArray());
    }

    public Message startDie(Monster monster, long damage) {
        Objects.requireNonNull(monster, "monster");
        return new Message(
                MessageName.MONSTER_START_DIE,
                new MessageWriter()
                        .writeInt(monster.id())
                        .writeLong(damage)
                        .writeBoolean(false)
                        .toByteArray());
    }

    public Message respawn(Monster monster) {
        Objects.requireNonNull(monster, "monster");
        return new Message(
                MessageName.MONSTER_RESPAWN,
                new MessageWriter()
                        .writeInt(monster.id())
                        .writeByte(monster.levelStatus())
                        .writeLong(monster.hp())
                        .toByteArray());
    }

    public Message attackPlayer(Monster monster, Player target, long damage) {
        Objects.requireNonNull(monster, "monster");
        Objects.requireNonNull(target, "target");
        return new Message(
                MessageName.MONSTER_ATTACK,
                new MessageWriter()
                        .writeInt(monster.id())
                        .writeByte(0)
                        .writeInt(target.id())
                        .writeLong(damage)
                        .toByteArray());
    }

    public Message move(Monster monster) {
        Objects.requireNonNull(monster, "monster");
        if (monster.moveDir() != -1 && monster.moveDir() != 1) {
            throw new IllegalArgumentException("monster move dir must be -1 or 1");
        }
        return new Message(
                MessageName.MONSTER_MOVE,
                new MessageWriter()
                        .writeInt(monster.id())
                        .writeShort(monster.x())
                        .writeShort(monster.y())
                        .writeByte(monster.moveDir())
                        .toByteArray());
    }
}

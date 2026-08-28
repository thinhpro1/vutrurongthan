package com.project.game.network.packet;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;

import java.io.IOException;
import java.util.Objects;

/** Writes the legacy server-to-client player presence packets. */
public final class PlayerPacketWriter {
    public Message addPlayer(PlayerProfile player) {
        Objects.requireNonNull(player, "player");
        try {
            MessageWriter writer = new MessageWriter()
                    .writeInt(player.id())
                    .writeUtf(player.name())
                    .writeByte(player.gender())
                    .writeShort(player.head())
                    .writeShort(player.body())
                    .writeShort(player.mount())
                    .writeShort(player.bag())
                    .writeShort(player.medal())
                    .writeShort(player.aura())
                    .writeShort(player.x())
                    .writeShort(player.y())
                    .writeLong(player.maxHp())
                    .writeLong(player.hp())
                    .writeByte(0) // normal typePk
                    .writeByte(0) // normal typeFlag
                    .writeShort(player.level())
                    .writeByte(player.spaceship())
                    .writeByte(player.speed())
                    .writeInt(-1) // no clan
                    .writeByte(-1) // no equipped upgrade
                    .writeByte(0); // no runtime effects
            return new Message(MessageName.ADD_PLAYER, writer.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode player name", exception);
        }
    }

    public Message removePlayer(int playerId) {
        return new Message(MessageName.REMOVE_PLAYER,
                new MessageWriter().writeInt(playerId).toByteArray());
    }

    public Message movePlayer(int playerId, int x, int y) {
        return new Message(MessageName.PLAYER_MOVE,
                new MessageWriter().writeInt(playerId).writeShort(x).writeShort(y).toByteArray());
    }

    public Message meDie(int x, int y) {
        return new Message(
                MessageName.ME_DIE,
                new MessageWriter()
                        .writeShort(x)
                        .writeShort(y)
                        .toByteArray());
    }

    public Message playerDie(int playerId, int x, int y) {
        return new Message(
                MessageName.PLAYER_DIE,
                new MessageWriter()
                        .writeInt(playerId)
                        .writeShort(x)
                        .writeShort(y)
                        .toByteArray());
    }

    public Message wakeUpFromDie(PlayerProfile player) {
        Objects.requireNonNull(player, "player");
        if (player.hp() <= 0L) {
            throw new IllegalArgumentException("wake-up player must be alive");
        }
        return new Message(
                MessageName.WAKE_UP_FROM_DIE,
                new MessageWriter()
                        .writeInt(player.id())
                        .writeShort(player.x())
                        .writeShort(player.y())
                        .writeLong(player.hp())
                        .writeLong(player.mp())
                        .toByteArray());
    }

    public Message potentialUpdate(long potentialAfter) {
        if (potentialAfter < 0L) {
            throw new IllegalArgumentException("potentialAfter must be non-negative");
        }
        return new Message(
                MessageName.PLAYER_INFO,
                new MessageWriter()
                        .writeByte(62)
                        .writeLong(potentialAfter)
                        .toByteArray());
    }
}

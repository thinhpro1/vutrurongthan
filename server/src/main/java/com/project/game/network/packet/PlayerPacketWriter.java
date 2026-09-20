package com.project.game.network.packet;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.resource.LegacyPlayerSkill;
import com.project.game.resource.LegacySkillOption;
import com.project.game.resource.LegacySkillPaint;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Writes the legacy server-to-client player presence packets. */
public final class PlayerPacketWriter {
    public Message playerInfo(PlayerProfile player, List<LegacyPlayerSkill> skills)
            throws IOException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(skills, "skills");
        PlayerPacketValidator.validatePlayerInfo(player);
        MessageWriter writer = new MessageWriter()
                .writeByte(0)
                .writeInt(player.id())
                .writeUtf(player.name())
                .writeByte(player.gender())
                .writeLong(player.power())
                .writeLong(player.potential())
                .writeShort(player.level())
                .writeShort(1)
                .writeShort(player.appearance().head())
                .writeShort(player.appearance().body())
                .writeShort(player.appearance().mount())
                .writeShort(player.appearance().bag())
                .writeShort(player.appearance().medal())
                .writeShort(player.appearance().aura())
                .writeInt(player.baseStats().damage())
                .writeInt(player.baseStats().hp())
                .writeInt(player.baseStats().mp())
                .writeInt(player.baseStats().constitution())
                .writeLong(10)
                .writeLong(10)
                .writeLong(10)
                .writeLong(10)
                .writeLong(player.currentStats().maxHp())
                .writeLong(player.currentStats().maxMp())
                .writeLong(player.hp())
                .writeLong(player.mp())
                .writeByte(player.currentStats().speed())
                .writeByte(0)
                .writeShort(0)
                .writeByte(1)
                .writeUtf(player.currentStats().dodge() + "%")
                .writeUtf(player.currentStats().critical() + "%")
                .writeUtf("0%")
                .writeUtf("0%")
                .writeUtf("0%")
                .writeUtf("0%")
                .writeLong(player.currentStats().damage())
                .writeLong(player.coin())
                .writeLong(player.coinLock())
                .writeInt(player.diamond())
                .writeInt(player.ruby())
                .writeByte(player.appearance().spaceship());
        writer.writeByte(skills.size());
        for (LegacyPlayerSkill skill : skills) {
            writePlayerSkill(writer, skill);
        }
        writer.writeByte(6)
                .writeByte(player.gender())
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(player.gender())
                .writeByte(0);
        return new Message(MessageName.PLAYER_INFO, writer.toByteArray());
    }

    public Message addPlayer(PlayerProfile player) {
        Objects.requireNonNull(player, "player");
        PlayerPacketValidator.validateAddPlayer(player);
        try {
            MessageWriter writer = new MessageWriter()
                    .writeInt(player.id())
                    .writeUtf(player.name())
                    .writeByte(player.gender())
                    .writeShort(player.appearance().head())
                    .writeShort(player.appearance().body())
                    .writeShort(player.appearance().mount())
                    .writeShort(player.appearance().bag())
                    .writeShort(player.appearance().medal())
                    .writeShort(player.appearance().aura())
                    .writeShort(player.x())
                    .writeShort(player.y())
                    .writeLong(player.currentStats().maxHp())
                    .writeLong(player.hp())
                    .writeByte(0) // normal typePk
                    .writeByte(0) // normal typeFlag
                    .writeShort(player.level())
                    .writeByte(player.appearance().spaceship())
                    .writeByte(player.currentStats().speed())
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
        PlayerPacketValidator.validatePosition(player.x(), player.y());
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

    private void writePlayerSkill(MessageWriter writer, LegacyPlayerSkill skill)
            throws IOException {
        writer.writeByte(skill.id())
                .writeByte(skill.names().size());
        for (String name : skill.names()) {
            writer.writeUtf(name);
        }
        writer.writeByte(skill.descriptions().size());
        for (String description : skill.descriptions()) {
            writer.writeUtf(description);
        }
        writer.writeByte(skill.type())
                .writeBoolean(skill.proactive())
                .writeByte(skill.icons().size());
        for (int icon : skill.icons()) {
            writer.writeShort(icon);
        }
        writer.writeByte(skill.dx().size());
        for (var row : skill.dx()) {
            writer.writeByte(row.size());
            for (int value : row) {
                writer.writeShort(value);
            }
        }
        writer.writeByte(skill.dy().size());
        for (var row : skill.dy()) {
            writer.writeByte(row.size());
            for (int value : row) {
                writer.writeShort(value);
            }
        }
        writer.writeShort(skill.levelRequire())
                .writeByte(skill.maxLevel())
                .writeByte(skill.maxUpgrade())
                .writeByte(skill.pointUpgrade().size());
        for (int point : skill.pointUpgrade()) {
            writer.writeInt(point);
        }
        writer.writeByte(skill.coolDown().size());
        for (var row : skill.coolDown()) {
            writer.writeByte(row.size());
            for (int value : row) {
                writer.writeInt(value);
            }
        }
        writer.writeByte(skill.typeMana())
                .writeByte(skill.mana().size());
        for (var row : skill.mana()) {
            writer.writeByte(row.size());
            for (int value : row) {
                writer.writeInt(value);
            }
        }
        writer.writeByte(skill.options().size());
        for (LegacySkillOption option : skill.options()) {
            writer.writeByte(option.id())
                    .writeUtf(option.name())
                    .writeByte(option.normal().size());
            for (int value : option.normal()) {
                writer.writeShort(value);
            }
            writer.writeByte(option.upgrade().size());
            for (int value : option.upgrade()) {
                writer.writeShort(value);
            }
        }
        writer.writeByte(skill.level())
                .writeByte(skill.upgrade())
                .writeInt(skill.point())
                .writeByte(skill.cooldownReduction());
        if (skill.level() > 0 && skill.proactive()) {
            writer.writeLong(skill.timeCanUse());
        }
        writer.writeByte(skill.paints().size());
        for (LegacySkillPaint paint : skill.paints()) {
            writer.writeUtf(paint.percent())
                    .writeShort(paint.paintId());
        }
    }
}

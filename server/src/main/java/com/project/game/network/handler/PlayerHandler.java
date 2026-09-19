package com.project.game.network.handler;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.LegacyPlayerCompatibilityValidator;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;
import com.project.game.resource.LegacyPlayerSkill;
import com.project.game.resource.LegacySkillOption;
import com.project.game.resource.LegacySkillPaint;

import java.io.IOException;

/** Handles player creation and the legacy enter-game packet sequence. */
final class PlayerHandler {
    private final Session session;
    private final PlayerService playerService;
    private final GameResources resources;
    private final MapHandler mapHandler;

    PlayerHandler(Session session, PlayerService playerService, GameResources resources,
                  MapHandler mapHandler) {
        this.session = session;
        this.playerService = playerService;
        this.resources = resources;
        this.mapHandler = mapHandler;
    }

    void handleCreatePlayer(Message message) throws IOException {
        var reader = message.reader();
        String name = reader.readUtf();
        int gender = reader.readUnsignedByte();
        if (reader.remaining() != 0) {
            throw new IOException("trailing CREATE_PLAYER payload bytes");
        }
        PlayerService.PlayerResult result = playerService.create(
                session.accountId(), name, gender);
        if (!result.success()) {
            sendDialog(result.message());
            return;
        }
        session.bindPlayer(result.player());
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        enterGame(result.player());
    }

    void enterGame(PlayerProfile player) throws IOException {
        sendPlayerInfo(player);
        mapHandler.sendMapInfo(player);
    }

    private void sendPlayerInfo(PlayerProfile player) throws IOException {
        LegacyPlayerCompatibilityValidator.validatePlayerInfo(player);
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
        var skills = resources.playerSkills(player.gender());
        if (skills.size() != 11) {
            throw new IOException(
                    "legacy player skill bootstrap unavailable for gender " + player.gender());
        }
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
        session.send(new Message(MessageName.PLAYER_INFO, writer.toByteArray()));
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

    private void sendDialog(String text) throws IOException {
        MessageWriter writer = new MessageWriter().writeUtf(text);
        session.send(new Message(MessageName.DIALOG_OK, writer.toByteArray()));
    }
}

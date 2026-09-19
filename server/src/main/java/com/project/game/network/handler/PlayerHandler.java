package com.project.game.network.handler;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;

import java.io.IOException;

/** Handles player creation and the legacy enter-game packet sequence. */
final class PlayerHandler {
    private final Session session;
    private final PlayerService playerService;
    private final GameResources resources;
    private final MapHandler mapHandler;
    private final PlayerPacketWriter playerPackets = new PlayerPacketWriter();

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
        var skills = resources.playerSkills(player.gender());
        if (skills.size() != 11) {
            throw new IOException(
                    "legacy player skill bootstrap unavailable for gender " + player.gender());
        }
        session.send(playerPackets.playerInfo(player, skills));
        mapHandler.sendMapInfo(player);
    }

    private void sendDialog(String text) throws IOException {
        MessageWriter writer = new MessageWriter().writeUtf(text);
        session.send(new Message(MessageName.DIALOG_OK, writer.toByteArray()));
    }
}

package com.project.game.network.handler;

import com.project.game.persistence.player.DuplicatePlayerException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.resource.GameResources;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Xử lý tạo Player và chuỗi packet vào game tương thích legacy. */
final class PlayerHandler {
    private static final Logger LOGGER = Logger.getLogger(PlayerHandler.class.getName());
    private static final String SYSTEM_BUSY = "Hệ thống đang bận, vui lòng thử lại";
    private final Session session;
    private final PlayerRepository playerRepository;
    private final GameResources resources;
    private final MapHandler mapHandler;
    private final PlayerPacketWriter playerPackets = new PlayerPacketWriter();

    PlayerHandler(Session session, PlayerRepository playerRepository, GameResources resources,
                  MapHandler mapHandler) {
        this.session = session;
        this.playerRepository = playerRepository;
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
        final Player initial;
        try {
            initial = Player.create(session.accountId(), name, gender);
        } catch (RuntimeException exception) {
            sendDialog("Thông tin nhân vật không hợp lệ");
            return;
        }
        Player created;
        try {
            PlayerRecord record = playerRepository.create(
                    PlayerRecord.withoutId(PlayerSaveData.capture(initial)));
            created = record.toPlayer(0);
        } catch (DuplicatePlayerException exception) {
            sendDialog("Nhân vật đã tồn tại");
            return;
        } catch (PlayerRepositoryException exception) {
            LOGGER.log(Level.WARNING,
                    "PLAYER create repository failure accountId=" + session.accountId(), exception);
            sendDialog(SYSTEM_BUSY);
            return;
        }
        session.bindPlayer(created);
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        enterGame(created);
    }

    void enterGame(Player player) throws IOException {
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

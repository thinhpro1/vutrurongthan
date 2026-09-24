package com.project.game.network.handler;

import com.project.game.account.AccountAuth;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.Player;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Xử lý luồng protocol đăng nhập, đăng ký và tiếp nhận tài khoản. */
final class AuthHandler {
    private static final Logger LOGGER = Logger.getLogger(AuthHandler.class.getName());
    private static final String SYSTEM_BUSY = "Hệ thống đang bận, vui lòng thử lại";
    private final Session session;
    private final AccountAuth auth;
    private final PlayerRepository playerRepository;
    private final ClientConfig networkConfig;
    private final PlayerHandler playerHandler;

    AuthHandler(Session session, AccountAuth auth, PlayerRepository playerRepository,
                ClientConfig networkConfig, PlayerHandler playerHandler) {
        this.session = session;
        this.auth = auth;
        this.playerRepository = playerRepository;
        this.networkConfig = networkConfig;
        this.playerHandler = playerHandler;
    }

    void handleLogin(Message message) throws IOException {
        var reader = message.reader();
        String clientVersion = reader.readUtf();
        String username = reader.readUtf();
        String password = reader.readUtf();
        if (!networkConfig.clientVersion().equals(clientVersion)) {
            sendDialog("Phiên bản client không được hỗ trợ");
            return;
        }
        if (reader.readByte() < networkConfig.loginVersion()) {
            sendDialog("Vui lòng cập nhật phiên bản mới");
            return;
        }
        if (reader.remaining() != 0) {
            throw new IOException("trailing login payload bytes");
        }
        AccountAuth.LoginResult result = auth.login(username, password);
        if (!result.success()) {
            sendDialog(result.message());
            return;
        }
        String accountName = result.accountName();
        if (!session.manager().beginAccountAdmission(session, result.accountId(), accountName)) {
            sendDialog("Tài khoản đang đăng nhập ở thiết bị khác");
            return;
        }
        boolean admissionSucceeded = false;
        try {
            AccountAuth.AuthResult metadata =
                    auth.markSuccessfulLogin(result.accountId(), session.remoteAddress());
            if (!metadata.success()) {
                if (session.state() != SessionState.CLOSED) {
                    sendDialog(metadata.value());
                }
                return;
            }
            Player player;
            try {
                Optional<PlayerRecord> loaded = playerRepository.findByAccountId(result.accountId());
                player = loaded.map(record -> record.toPlayer(0)).orElse(null);
            } catch (PlayerRepositoryException exception) {
                LOGGER.log(Level.WARNING,
                        "PLAYER load repository failure accountId=" + result.accountId(), exception);
                if (session.state() != SessionState.CLOSED) {
                    sendDialog(SYSTEM_BUSY);
                }
                return;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING,
                        "PLAYER load invalid persisted data accountId=" + result.accountId(), exception);
                if (session.state() != SessionState.CLOSED) {
                    sendDialog(SYSTEM_BUSY);
                }
                return;
            }
            if (!session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED)) {
                return;
            }
            admissionSucceeded = true;
            if (player == null) {
                session.send(new Message(MessageName.START_CREATE_PLAYER_SCREEN));
            } else {
                session.bindPlayer(player);
                session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
                playerHandler.enterGame(player);
            }
        } finally {
            session.manager().finishAccountAdmission(session, admissionSucceeded);
        }
    }

    void handleRegister(Message message) throws IOException {
        var reader = message.reader();
        String username = reader.readUtf();
        String password = reader.readUtf();
        if (reader.remaining() != 0) {
            throw new IOException("trailing register payload bytes");
        }
        AccountAuth.AuthResult result = auth.register(username, password, session.remoteAddress());
        sendDialog(result.value());
    }

    private void sendDialog(String text) throws IOException {
        MessageWriter writer = new MessageWriter().writeUtf(text);
        session.send(new Message(MessageName.DIALOG_OK, writer.toByteArray()));
    }
}

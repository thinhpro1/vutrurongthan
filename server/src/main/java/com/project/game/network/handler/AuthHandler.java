package com.project.game.network.handler;

import com.project.game.account.AuthService;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.player.PlayerService;

import java.io.IOException;

/** Handles login, registration, and account-admission protocol flows. */
final class AuthHandler {
    private final Session session;
    private final AuthService authService;
    private final PlayerService playerService;
    private final ClientConfig networkConfig;
    private final PlayerHandler playerHandler;

    AuthHandler(Session session, AuthService authService, PlayerService playerService,
                ClientConfig networkConfig, PlayerHandler playerHandler) {
        this.session = session;
        this.authService = authService;
        this.playerService = playerService;
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
        AuthService.LoginResult result = authService.login(username, password);
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
            AuthService.AuthResult metadata =
                    authService.markSuccessfulLogin(result.accountId(), session.remoteAddress());
            if (!metadata.success()) {
                if (session.state() != SessionState.CLOSED) {
                    sendDialog(metadata.value());
                }
                return;
            }
            PlayerService.PlayerLoadResult loaded = playerService.load(result.accountId());
            if (!loaded.success()) {
                if (session.state() != SessionState.CLOSED) {
                    sendDialog(loaded.message());
                }
                return;
            }
            if (!session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED)) {
                return;
            }
            admissionSucceeded = true;
            if (!loaded.found()) {
                session.send(new Message(MessageName.START_CREATE_PLAYER_SCREEN));
            } else {
                PlayerProfile player = loaded.player();
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
        AuthService.AuthResult result = authService.register(username, password, session.remoteAddress());
        sendDialog(result.value());
    }

    private void sendDialog(String text) throws IOException {
        MessageWriter writer = new MessageWriter().writeUtf(text);
        session.send(new Message(MessageName.DIALOG_OK, writer.toByteArray()));
    }
}

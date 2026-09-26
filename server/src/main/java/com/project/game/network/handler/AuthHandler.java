package com.project.game.network.handler;

import com.project.game.account.AccountAuth;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.message.MessageReader;

import java.io.IOException;

/** Xử lý luồng protocol đăng nhập, đăng ký và tiếp nhận tài khoản. */
final class AuthHandler {
    private final Session session;
    private final AccountAuth auth;
    private final ClientConfig networkConfig;
    private final PlayerHandler playerHandler;

    AuthHandler(Session session, AccountAuth auth, ClientConfig networkConfig,
                PlayerHandler playerHandler) {
        this.session = session;
        this.auth = auth;
        this.networkConfig = networkConfig;
        this.playerHandler = playerHandler;
    }

    void handleLogin(Message message) throws IOException {
        MessageReader reader = message.reader();
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
            if (!session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED)) {
                return;
            }
            admissionSucceeded = playerHandler.openPlayerForAuthenticatedAccount(result.accountId());
            if (!admissionSucceeded) {
                session.transition(SessionState.AUTHENTICATED, SessionState.HANDSHAKE_DONE);
            }
        } finally {
            session.manager().finishAccountAdmission(session, admissionSucceeded);
        }
    }

    void handleRegister(Message message) throws IOException {
        MessageReader reader = message.reader();
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

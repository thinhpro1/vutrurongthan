package com.project.game.network;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.service.AuthService;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** N8 bootstrap plus N11 LEGACY_DEV authentication and create-player flow. */
public final class MessageHandler {
    private static final Logger LOGGER = Logger.getLogger(MessageHandler.class.getName());
    private final Session session;
    private final AuthService authService;
    private final NetworkConfig networkConfig;

    public MessageHandler(Session session, AuthService authService) {
        this(session, authService, NetworkConfig.defaults());
    }

    public MessageHandler(Session session, AuthService authService, NetworkConfig networkConfig) {
        this.session = session;
        this.authService = authService;
        this.networkConfig = networkConfig;
    }

    public void onMessage(Message message) {
        if (!isAllowed(session.state(), message.command())) {
            LOGGER.warning(() -> "REJECT cmd=" + message.command() + " state=" + session.state());
            if (session.recordProtocolViolation()) {
                session.close();
            }
            return;
        }
        try {
            switch (message.command()) {
                case MessageName.CONNECT_SERVER -> handleConnect();
                case MessageName.UPDATE_DATA -> handleUpdateData(message);
                case MessageName.LOGIN -> handleLogin(message);
                case MessageName.REGISTER_USER -> handleRegister(message);
                case MessageName.CREATE_PLAYER -> handleCreatePlayer(message);
                default -> LOGGER.fine(() -> "RX cmd=" + message.command()
                        + " len=" + message.payload().length);
            }
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Malformed packet cmd=" + message.command(), exception);
            session.close();
        }
    }

    private void handleConnect() throws IOException {
        LOGGER.fine(() -> "CONNECT session=" + session.id());
        session.completeHandshake();
        MessageWriter writer = new MessageWriter().writeUtf(networkConfig.clientVersion());
        session.send(new Message(MessageName.VERSION_SOURCE, writer.toByteArray()));
    }

    private void handleUpdateData(Message message) throws IOException {
        int type = message.reader().readByte();
        LOGGER.fine(() -> "UPDATE_DATA type=" + type + " session=" + session.id());
    }

    private void handleLogin(Message message) throws IOException {
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
        AuthService.AuthResult result = authService.login(username, password);
        if (!result.success()) {
            sendDialog(result.value());
            return;
        }
        String accountName = result.value();
        if (!session.manager().bindAccount(session, accountName)) {
            sendDialog("Tài khoản đang đăng nhập ở thiết bị khác");
            return;
        }
        session.transition(SessionState.HANDSHAKE_DONE, SessionState.AUTHENTICATED);
        PlayerProfile player = authService.findPlayer(accountName);
        if (player == null) {
            session.send(new Message(MessageName.START_CREATE_PLAYER_SCREEN));
        } else {
            session.bindPlayer(player);
            session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
            sendPlayerInfo(player);
        }
        LOGGER.info(() -> "LOGIN success session=" + session.id());
    }

    private void handleRegister(Message message) throws IOException {
        var reader = message.reader();
        AuthService.AuthResult result = authService.register(reader.readUtf(), reader.readUtf());
        sendDialog(result.value());
    }

    private void handleCreatePlayer(Message message) throws IOException {
        var reader = message.reader();
        AuthService.PlayerResult result = authService.createPlayer(
                session.accountName(), reader.readUtf(), reader.readUnsignedByte());
        if (!result.success()) {
            sendDialog(result.message());
            return;
        }
        session.bindPlayer(result.player());
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        sendPlayerInfo(result.player());
    }

    private void sendPlayerInfo(PlayerProfile player) throws IOException {
        MessageWriter writer = new MessageWriter()
                .writeUtf(player.name())
                .writeByte(player.gender());
        session.send(new Message(MessageName.PLAYER_INFO, writer.toByteArray()));
    }

    private void sendDialog(String text) throws IOException {
        MessageWriter writer = new MessageWriter().writeUtf(text);
        session.send(new Message(MessageName.DIALOG_OK, writer.toByteArray()));
    }

    private boolean isAllowed(SessionState current, int command) {
        return switch (current) {
            case CONNECTED -> command == MessageName.CONNECT_SERVER;
            case HANDSHAKE_DONE -> command == MessageName.UPDATE_DATA
                    || command == MessageName.LOGIN
                    || command == MessageName.REGISTER_USER;
            case AUTHENTICATED -> command == MessageName.CREATE_PLAYER;
            case IN_GAME -> false;
            case CLOSED -> false;
        };
    }
}

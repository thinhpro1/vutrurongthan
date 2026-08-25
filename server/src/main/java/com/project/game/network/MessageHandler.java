package com.project.game.network;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.player.PlayerProfile;
import com.project.game.service.AuthService;
import com.project.game.service.ResourceService;
import com.project.game.service.ServerServices;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** N8 bootstrap plus N11 LEGACY_DEV authentication and create-player flow. */
public final class MessageHandler {
    private static final Logger LOGGER = Logger.getLogger(MessageHandler.class.getName());
    // Development-only bootstrap values; canonical resource datasets are not in this repository yet.
    private static final int NOT_PROVIDED_VERSION = -1;
    private static final int DEV_MONSTER_VERSION = 0;
    private final Session session;
    private final AuthService authService;
    private final ResourceService resourceService;
    private final NetworkConfig networkConfig;
    private final NetworkEventObserver eventObserver;

    public MessageHandler(Session session, AuthService authService) {
        this(session, authService, NetworkConfig.defaults());
    }

    public MessageHandler(Session session, AuthService authService, NetworkConfig networkConfig) {
        this(session, authService, networkConfig, NetworkEventObserver.NO_OP);
    }

    public MessageHandler(Session session, AuthService authService, NetworkConfig networkConfig,
                          NetworkEventObserver eventObserver) {
        this(session, new ServerServices(authService, ResourceService.unavailable()), networkConfig, eventObserver);
    }

    public MessageHandler(Session session, ServerServices services, NetworkConfig networkConfig,
                          NetworkEventObserver eventObserver) {
        this.session = session;
        services = Objects.requireNonNull(services, "services");
        this.authService = services.auth();
        this.resourceService = services.resources();
        this.networkConfig = networkConfig;
        this.eventObserver = eventObserver;
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
                case MessageName.REQUEST_ICON -> handleRequestIcon(message);
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
        var reader = message.reader();
        int type = reader.readByte();
        if (reader.remaining() != 0) {
            throw new IOException("trailing UPDATE_DATA payload bytes");
        }
        LOGGER.fine(() -> "UPDATE_DATA type=" + type + " session=" + session.id());
        eventObserver.onUpdateData(session, type);
        switch (type) {
            case -1 -> sendResourceManifest();
            case 4 -> sendMonsterResource();
            default -> LOGGER.fine(() -> "UPDATE_DATA type=" + type
                    + " is not provided by the development bootstrap session=" + session.id());
        }
    }

    private void sendResourceManifest() throws IOException {
        MessageWriter writer = new MessageWriter()
                .writeByte(-1)
                .writeByte(NOT_PROVIDED_VERSION) // image
                .writeByte(NOT_PROVIDED_VERSION) // item template
                .writeByte(NOT_PROVIDED_VERSION) // item option template
                .writeByte(NOT_PROVIDED_VERSION) // npc
                .writeByte(NOT_PROVIDED_VERSION) // effect
                .writeByte(DEV_MONSTER_VERSION) // monster
                .writeByte(NOT_PROVIDED_VERSION) // medal
                .writeByte(NOT_PROVIDED_VERSION) // level
                .writeByte(NOT_PROVIDED_VERSION) // frame
                .writeByte(NOT_PROVIDED_VERSION) // mount
                .writeByte(NOT_PROVIDED_VERSION) // bag
                .writeByte(NOT_PROVIDED_VERSION) // skill paint
                .writeByte(NOT_PROVIDED_VERSION); // aura (client 0.9.5)
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
        LOGGER.fine(() -> "UPDATE_DATA_TX type=-1 session=" + session.id());
    }

    private void sendMonsterResource() throws IOException {
        MessageWriter writer = new MessageWriter()
                .writeByte(4)
                .writeByte(DEV_MONSTER_VERSION)
                .writeShort(0) // dart template count
                .writeShort(0); // monster template count
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
        LOGGER.fine(() -> "UPDATE_DATA_TX type=4 session=" + session.id()
                + " monsterVersion=" + DEV_MONSTER_VERSION + " darts=0 monsters=0");
    }

    private void handleRequestIcon(Message message) throws IOException {
        var reader = message.reader();
        int iconId = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IOException("trailing REQUEST_ICON payload bytes");
        }
        LOGGER.fine(() -> "REQUEST_ICON id=" + iconId + " session=" + session.id());

        var data = resourceService.loadIcon(iconId);
        if (data.isEmpty()) {
            LOGGER.fine(() -> "REQUEST_ICON_MISS id=" + iconId + " session=" + session.id());
            return;
        }
        byte[] bytes = data.get();
        if (bytes.length == 0) {
            LOGGER.fine(() -> "REQUEST_ICON_MISS id=" + iconId + " session=" + session.id());
            return;
        }
        MessageWriter writer = new MessageWriter()
                .writeShort(iconId)
                .writeInt(bytes.length)
                .writeBytes(bytes);
        byte[] payload = writer.toByteArray();
        if (payload.length > session.maxPacketSize()) {
            LOGGER.fine(() -> "REQUEST_ICON_TOO_LARGE id=" + iconId + " bytes=" + bytes.length
                    + " maxPacketSize=" + session.maxPacketSize() + " session=" + session.id());
            return;
        }
        if (session.send(new Message(MessageName.REQUEST_ICON, payload))) {
            LOGGER.fine(() -> "REQUEST_ICON_TX id=" + iconId + " bytes=" + bytes.length
                    + " session=" + session.id());
        }
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
                    || command == MessageName.REGISTER_USER
                    || command == MessageName.REQUEST_ICON;
            case AUTHENTICATED -> command == MessageName.CREATE_PLAYER
                    || command == MessageName.REQUEST_ICON;
            case IN_GAME -> command == MessageName.REQUEST_ICON;
            case CLOSED -> false;
        };
    }
}

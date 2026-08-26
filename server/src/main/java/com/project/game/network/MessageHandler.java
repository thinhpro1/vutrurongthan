package com.project.game.network;

import com.project.game.frame.FrameTemplate;
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
    // Development-only bootstrap values for the legacy client resource protocol.
    private static final int NOT_PROVIDED_VERSION = -1;
    private static final int DEV_MONSTER_VERSION = 0;
    private static final int DEV_FRAME_VERSION = 1;
    private final Session session;
    private final AuthService authService;
    private final ResourceService resourceService;
    private final NetworkConfig networkConfig;
    private final NetworkEventObserver eventObserver;

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
            case 7 -> sendFrameResource();
            default -> LOGGER.fine(() -> "UPDATE_DATA type=" + type
                    + " is not provided by the development bootstrap session=" + session.id());
        }
    }

    private void sendResourceManifest() throws IOException {
        int frameVersion = resourceService.frames().isEmpty()
                ? NOT_PROVIDED_VERSION : DEV_FRAME_VERSION;
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
                .writeByte(frameVersion) // frame
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

    private void sendFrameResource() throws IOException {
        var frames = resourceService.frames();
        if (frames.isEmpty()) {
            LOGGER.fine(() -> "UPDATE_DATA type=7 skipped because Frame resources are unavailable session="
                    + session.id());
            return;
        }
        if (frames.size() > Short.MAX_VALUE) {
            throw new IOException("too many frame templates: " + frames.size());
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(7)
                .writeByte(DEV_FRAME_VERSION)
                .writeShort(frames.size());
        for (FrameTemplate frame : frames) {
            writer.writeShort(frame.id())
                    .writeShort(frame.hpBar())
                    .writeShort(frame.chat())
                    .writeByte(frame.dead().size());
            frame.dead().forEach(writer::writeShort);
            writer.writeByte(frame.stand().size());
            frame.stand().forEach(writer::writeShort);
            writer.writeByte(frame.run().size());
            frame.run().forEach(writer::writeShort);
            writer.writeShort(frame.fly())
                    .writeShort(frame.jump())
                    .writeShort(frame.fall())
                    .writeShort(frame.injure())
                    .writeByte(frame.action().size());
            frame.action().forEach((actionId, iconId) -> writer.writeByte(actionId).writeShort(iconId));
            writer.writeShort(frame.dx())
                    .writeShort(frame.dy())
                    .writeShort(frame.width())
                    .writeShort(frame.height());
        }
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
        LOGGER.fine(() -> "UPDATE_DATA_TX type=7 session=" + session.id()
                + " frameVersion=" + DEV_FRAME_VERSION + " count=" + frames.size());
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
            enterGame(player);
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
        String name = reader.readUtf();
        int gender = reader.readUnsignedByte();
        if (reader.remaining() != 0) {
            throw new IOException("trailing CREATE_PLAYER payload bytes");
        }
        AuthService.PlayerResult result = authService.createPlayer(
                session.accountName(), name, gender);
        if (!result.success()) {
            sendDialog(result.message());
            return;
        }
        session.bindPlayer(result.player());
        session.transition(SessionState.AUTHENTICATED, SessionState.IN_GAME);
        enterGame(result.player());
    }

    private void enterGame(PlayerProfile player) throws IOException {
        sendPlayerInfo(player);
        sendMapInfo(player);
    }

    private void sendPlayerInfo(PlayerProfile player) throws IOException {
        MessageWriter writer = new MessageWriter()
                .writeByte(0)
                .writeInt(player.id())
                .writeUtf(player.name())
                .writeByte(player.gender())
                .writeLong(player.power())
                .writeLong(player.potential())
                .writeShort(player.level())
                .writeShort(player.pointSkill())
                .writeShort(player.head())
                .writeShort(player.body())
                .writeShort(player.mount())
                .writeShort(player.bag())
                .writeShort(player.medal())
                .writeShort(player.aura())
                .writeInt(player.baseDamage())
                .writeInt(player.baseHp())
                .writeInt(player.baseMp())
                .writeInt(player.baseConstitution())
                .writeLong(player.potentialUpDamage())
                .writeLong(player.potentialUpHp())
                .writeLong(player.potentialUpMp())
                .writeLong(player.potentialUpConstitution())
                .writeLong(player.maxHp())
                .writeLong(player.maxMp())
                .writeLong(player.hp())
                .writeLong(player.mp())
                .writeByte(player.speed())
                .writeByte(player.pointPk())
                .writeShort(player.pointActivity())
                .writeByte(player.countBarrack())
                .writeUtf(player.dodge())
                .writeUtf(player.critical())
                .writeUtf(player.reduceDamage())
                .writeUtf(player.bloodsucking())
                .writeUtf(player.manaSucking())
                .writeUtf(player.strikeBack())
                .writeLong(player.damage())
                .writeLong(player.coin())
                .writeLong(player.coinLock())
                .writeInt(player.diamond())
                .writeInt(player.ruby())
                .writeByte(player.spaceship());
        var skills = resourceService.playerSkills(player.gender());
        if (skills.size() != 11) {
            throw new IOException(
                    "legacy player skill bootstrap unavailable for gender " + player.gender());
        }
        writer.writeByte(skills.size());
        for (ResourceService.LegacyPlayerSkill skill : skills) {
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

    private void sendMapInfo(PlayerProfile player) throws IOException {
        var map = resourceService.map(player.mapId())
                .orElseThrow(() -> new IOException(
                        "legacy map bootstrap unavailable for map " + player.mapId()));

        MessageWriter writer = new MessageWriter()
                .writeShort(map.id())
                .writeShort(map.iconId())
                .writeUtf(map.name())
                .writeShort(map.row())
                .writeShort(map.column())
                .writeUtf(map.data());

        for (int imageId : map.imagesBgr()) {
            writer.writeShort(imageId);
        }
        for (var colorRow : map.colorsBgr()) {
            for (int value : colorRow) {
                writer.writeShort(value);
            }
        }

        writer.writeBoolean(map.line());
        if (map.line()) {
            if (map.dataLine() == null) {
                throw new IOException("line map missing dataLine for map " + map.id());
            }
            writer.writeUtf(map.dataLine());
        }

        writer.writeByte(player.zoneId())
                .writeShort(player.x())
                .writeShort(player.y())
                .writeByte(0)
                .writeByte(0)
                .writeByte(0)
                .writeShort(0)
                .writeBoolean(false);

        if (session.send(new Message(MessageName.MAP_INFO, writer.toByteArray()))) {
            LOGGER.info(() -> "MAP_INFO_TX map=" + map.id()
                    + " zone=" + player.zoneId()
                    + " x=" + player.x()
                    + " y=" + player.y()
                    + " session=" + session.id());
        }
    }

    private void writePlayerSkill(MessageWriter writer, ResourceService.LegacyPlayerSkill skill)
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
        for (ResourceService.LegacySkillOption option : skill.options()) {
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
        for (ResourceService.LegacySkillPaint paint : skill.paints()) {
            writer.writeUtf(paint.percent())
                    .writeShort(paint.paintId());
        }
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

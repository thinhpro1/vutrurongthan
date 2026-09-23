package com.project.game.network.handler;

import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.SessionServices;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Thin stateful protocol dispatcher for the legacy session. */
public final class MessageHandler {
    private static final Logger LOGGER = Logger.getLogger(MessageHandler.class.getName());

    private final Session session;
    private final ConnectionHandler connectionHandler;
    private final ResourceHandler resourceHandler;
    private final AuthHandler authHandler;
    private final PlayerHandler playerHandler;
    private final MapHandler mapHandler;
    private final CombatHandler combatHandler;

    public MessageHandler(Session session, SessionServices services, ClientConfig networkConfig) {
        this.session = Objects.requireNonNull(session, "session");
        services = Objects.requireNonNull(services, "services");
        networkConfig = Objects.requireNonNull(networkConfig, "networkConfig");

        this.connectionHandler = new ConnectionHandler(session, networkConfig);
        this.resourceHandler = new ResourceHandler(session, services.resources());
        this.mapHandler = new MapHandler(session, services.maps(), services.monsterManager(),
                services.players(), services.resources());
        this.playerHandler = new PlayerHandler(session, services.players(), services.resources(), mapHandler);
        this.authHandler = new AuthHandler(session, services.auth(), services.players(),
                networkConfig, playerHandler);
        this.combatHandler = new CombatHandler(session, services.combat());
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
                case MessageName.CONNECT_SERVER -> connectionHandler.handleConnect(message);
                case MessageName.UPDATE_DATA -> resourceHandler.handleUpdateData(message);
                case MessageName.REQUEST_ICON -> resourceHandler.handleRequestIcon(message);
                case MessageName.LOGIN -> authHandler.handleLogin(message);
                case MessageName.REGISTER_USER -> authHandler.handleRegister(message);
                case MessageName.CREATE_PLAYER -> playerHandler.handleCreatePlayer(message);
                case MessageName.FINISH_LOAD_MAP -> mapHandler.handleFinishLoadMap(message);
                case MessageName.RETURN_TOWN_FROM_DIE -> {
                    combatHandler.clearPendingAttack();
                    mapHandler.handleReturnTownFromDie(message);
                }
                case MessageName.WAKE_UP_FROM_DIE -> {
                    combatHandler.clearPendingAttack();
                    mapHandler.handleUnsupportedWakeUpFromDie(message);
                }
                case MessageName.REQUEST_CHANGE_MAP -> {
                    combatHandler.clearPendingAttack();
                    mapHandler.handleRequestChangeMap(message);
                }
                case MessageName.PLAYER_MOVE -> mapHandler.handlePlayerMove(message);
                case MessageName.PLAYER_START_USE_ULTIMATE ->
                        combatHandler.handlePrepareMonsterAttack(message);
                case MessageName.USE_SKILL -> combatHandler.handleMonsterAttackImpact(message);
                default -> LOGGER.fine(() -> "RX cmd=" + message.command()
                        + " len=" + message.payload().length);
            }
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Malformed packet cmd=" + message.command(), exception);
            session.close();
        }
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
            case IN_GAME -> command == MessageName.REQUEST_ICON
                    || command == MessageName.FINISH_LOAD_MAP
                    || command == MessageName.RETURN_TOWN_FROM_DIE
                    || command == MessageName.WAKE_UP_FROM_DIE
                    || command == MessageName.REQUEST_CHANGE_MAP
                    || command == MessageName.PLAYER_MOVE
                    || command == MessageName.PLAYER_START_USE_ULTIMATE
                    || command == MessageName.USE_SKILL;
            case CLOSED -> false;
        };
    }
}

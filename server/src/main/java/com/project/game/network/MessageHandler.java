package com.project.game.network;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bootstrap handler for N8. Domain handlers are intentionally added in later feature gates. */
public final class MessageHandler {
    private static final Logger LOGGER = Logger.getLogger(MessageHandler.class.getName());
    private final Session session;

    public MessageHandler(Session session) {
        this.session = session;
    }

    public void onMessage(Message message) {
        if (!isAllowed(session.state(), message.command())) {
            LOGGER.warning(() -> "REJECT cmd=" + message.command() + " state=" + session.state());
            return;
        }
        try {
            switch (message.command()) {
                case MessageName.CONNECT_SERVER -> handleConnect();
                case MessageName.UPDATE_DATA -> handleUpdateData(message);
                case MessageName.LOGIN -> handleLogin(message);
                case MessageName.REGISTER_USER -> handleRegister(message);
                default -> LOGGER.fine(() -> "RX cmd=" + message.command() + " len=" + message.payload().length);
            }
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Malformed packet cmd=" + message.command(), exception);
            session.close();
        }
    }

    private void handleConnect() throws IOException {
        LOGGER.fine(() -> "CONNECT session=" + session.id());
        session.completeHandshake();
        MessageWriter writer = new MessageWriter().writeUtf("0.9.5");
        session.send(new Message(MessageName.VERSION_SOURCE, writer.toByteArray()));
    }

    private void handleUpdateData(Message message) throws IOException {
        int type = message.reader().readByte();
        LOGGER.fine(() -> "UPDATE_DATA type=" + type + " session=" + session.id());
    }

    private void handleLogin(Message message) throws IOException {
        var reader = message.reader();
        reader.readUtf();
        reader.readUtf();
        reader.readUtf();
        if (reader.remaining() > 0) {
            reader.readByte();
        }
        LOGGER.info(() -> "LOGIN payload accepted for session=" + session.id());
    }

    private void handleRegister(Message message) throws IOException {
        var reader = message.reader();
        reader.readUtf();
        reader.readUtf();
        LOGGER.info(() -> "REGISTER payload accepted for session=" + session.id());
    }

    private boolean isAllowed(SessionState current, int command) {
        return switch (current) {
            case CONNECTED -> command == MessageName.CONNECT_SERVER;
            case HANDSHAKE_DONE -> command == MessageName.UPDATE_DATA
                    || command == MessageName.LOGIN
                    || command == MessageName.REGISTER_USER;
            case AUTHENTICATED -> command == MessageName.CREATE_PLAYER;
            case IN_GAME -> true;
            case CLOSED -> false;
        };
    }
}

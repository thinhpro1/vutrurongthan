package com.project.game.network.handler;

import com.project.game.network.NetworkConfig;
import com.project.game.network.Session;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;

import java.io.IOException;

/** Handles the legacy connection handshake and version response. */
final class ConnectionHandler {
    private final Session session;
    private final NetworkConfig networkConfig;

    ConnectionHandler(Session session, NetworkConfig networkConfig) {
        this.session = session;
        this.networkConfig = networkConfig;
    }

    void handleConnect(Message message) throws IOException {
        if (message.reader().remaining() != 0) {
            throw new IOException("trailing connect payload bytes");
        }
        session.completeHandshake();
        MessageWriter writer = new MessageWriter().writeUtf(networkConfig.clientVersion());
        session.send(new Message(MessageName.VERSION_SOURCE, writer.toByteArray()));
    }
}

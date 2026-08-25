package com.project.game.network.message;

import java.util.Arrays;

/** Immutable protocol message. It deliberately knows nothing about sockets or gameplay. */
public final class Message {
    private final int command;
    private final byte[] payload;

    public Message(int command) {
        this(command, new byte[0]);
    }

    public Message(int command, byte[] payload) {
        if (command < Byte.MIN_VALUE || command > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("command must fit in one signed byte: " + command);
        }
        this.command = command;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public int command() {
        return command;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public MessageReader reader() {
        return new MessageReader(payload);
    }

    @Override
    public String toString() {
        return "Message{command=" + command + ", payloadLength=" + payload.length + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Message message)) {
            return false;
        }
        return command == message.command && Arrays.equals(payload, message.payload);
    }

    @Override
    public int hashCode() {
        return 31 * command + Arrays.hashCode(payload);
    }
}

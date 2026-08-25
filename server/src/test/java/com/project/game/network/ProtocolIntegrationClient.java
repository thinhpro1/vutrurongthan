package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageReader;
import com.project.game.network.message.MessageWriter;
import com.project.game.network.transport.LegacyTcpTransport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** N9 Java client that exercises the same wire sequence as the legacy Unity client. */
public final class ProtocolIntegrationClient {
    private static final byte[] DEFAULT_KEY = "abc".getBytes(StandardCharsets.US_ASCII);

    private ProtocolIntegrationClient() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1707;
        LegacyPacketCodec codec = new LegacyPacketCodec(65535);
        try (LegacyTcpTransport transport = LegacyTcpTransport.connect(host, port, 3_000)) {
            codec.writeClient(transport.output(), null, false, new Message(MessageName.CONNECT_SERVER));
            Message handshake = codec.read(transport.input(), null, false);
            byte[] key = reconstructKey(handshake);
            LegacyCipher cipher = new LegacyCipher(key);

            Message version = codec.readServerResponse(transport.input(), cipher, true);
            String versionText = version.reader().readUtf();
            if (!"0.9.5".equals(versionText)) {
                throw new IOException("unexpected server version: " + versionText);
            }
            codec.writeClient(transport.output(), cipher, true,
                    new Message(MessageName.UPDATE_DATA, new MessageWriter().writeByte(-1).toByteArray()));
            System.out.printf("N9 PASS host=%s port=%d key=%s version=%s update=-1%n",
                    host, port, new String(key, StandardCharsets.US_ASCII), versionText);
            if (args.length >= 4) {
                runAuthFlow(codec, transport, cipher, args[2], args[3]);
            }
        }
    }

    private static void runAuthFlow(LegacyPacketCodec codec, LegacyTcpTransport transport,
                                    LegacyCipher cipher, String username, String password) throws Exception {
        MessageWriter register = new MessageWriter().writeUtf(username).writeUtf(password);
        codec.writeClient(transport.output(), cipher, true,
                new Message(MessageName.REGISTER_USER, register.toByteArray()));
        requireCommand(codec.read(transport.input(), cipher, true), MessageName.DIALOG_OK);

        MessageWriter login = new MessageWriter().writeUtf("0.9.5")
                .writeUtf(username).writeUtf(password).writeByte(1);
        codec.writeClient(transport.output(), cipher, true,
                new Message(MessageName.LOGIN, login.toByteArray()));
        requireCommand(codec.read(transport.input(), cipher, true), MessageName.START_CREATE_PLAYER_SCREEN);

        MessageWriter player = new MessageWriter().writeUtf(username).writeByte(0);
        codec.writeClient(transport.output(), cipher, true,
                new Message(MessageName.CREATE_PLAYER, player.toByteArray()));
        Message playerInfo = codec.read(transport.input(), cipher, true);
        requireCommand(playerInfo, MessageName.PLAYER_INFO);
        MessageReader reader = playerInfo.reader();
        String playerName = reader.readUtf();
        reader.readUnsignedByte();
        System.out.printf("N11 PASS account=%s player=%s state=IN_GAME%n", username, playerName);
    }

    private static void requireCommand(Message message, int expected) throws IOException {
        if (message.command() != expected) {
            throw new IOException("expected command " + expected + ", got " + message.command());
        }
    }

    private static byte[] reconstructKey(Message message) throws IOException {
        if (message.command() != MessageName.SEND_SESSION_KEY) {
            throw new IOException("expected session key, got " + message.command());
        }
        MessageReader reader = message.reader();
        int length = reader.readUnsignedByte();
        if (length == 0 || reader.remaining() != length) {
            throw new IOException("invalid handshake key payload");
        }
        byte[] cumulative = reader.readBytes(length);
        byte[] key = new byte[length];
        key[0] = cumulative[0];
        for (int i = 1; i < length; i++) {
            key[i] = (byte) (Byte.toUnsignedInt(cumulative[i]) ^ Byte.toUnsignedInt(key[i - 1]));
        }
        return key;
    }
}

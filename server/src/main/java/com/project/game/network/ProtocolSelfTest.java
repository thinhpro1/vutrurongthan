package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageReader;
import com.project.game.network.message.MessageWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Dependency-free protocol checks for CI/bootstrap environments without Maven test artifacts. */
public final class ProtocolSelfTest {
    private static final byte[] KEY = "abc".getBytes(StandardCharsets.US_ASCII);

    private ProtocolSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        primitiveEndianAndUtf();
        handshakeFrame();
        continuousCipher();
        specialResponse();
        truncatedPacket();
        System.out.println("ProtocolSelfTest: PASS");
    }

    private static void primitiveEndianAndUtf() throws Exception {
        byte[] data = new MessageWriter().writeInt(0x01020304).writeUtf("Rồng Thần").toByteArray();
        MessageReader reader = new MessageReader(data);
        check(reader.readInt() == 0x01020304, "big-endian int");
        check("Rồng Thần".equals(reader.readUtf()), "UTF-8");
        check(reader.remaining() == 0, "reader consumed payload");
    }

    private static void handshakeFrame() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(65535);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writeHandshakeKey(output, KEY);
        check(Arrays.equals(output.toByteArray(), new byte[]{(byte) 0x80, 0, 4, 3, 0x61, 3, 1}), "handshake frame");
    }

    private static void continuousCipher() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(65535);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writeClient(output, new LegacyCipher(KEY), true,
                new Message(MessageName.UPDATE_DATA, new byte[]{-1}));
        check(Arrays.equals(output.toByteArray(), new byte[]{(byte) 0xe2, 0x62, 0x62, (byte) 0x9e}), "known update frame");
        Message decoded = codec.read(new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(KEY), true);
        check(decoded.command() == MessageName.UPDATE_DATA, "decoded command");
        check(Arrays.equals(decoded.payload(), new byte[]{-1}), "decoded payload");
    }

    private static void specialResponse() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(65535);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, new LegacyCipher(KEY), true,
                new Message(MessageName.VERSION_SOURCE, new byte[]{1, 2, 3}));
        Message decoded = codec.readServerResponse(new ByteArrayInputStream(output.toByteArray()), new LegacyCipher(KEY), true);
        check(decoded.command() == MessageName.VERSION_SOURCE, "special command");
        check(Arrays.equals(decoded.payload(), new byte[]{1, 2, 3}), "special payload");
        check(output.size() == 7, "special frame size");
    }

    private static void truncatedPacket() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(65535);
        try {
            codec.read(new ByteArrayInputStream(new byte[]{(byte) MessageName.UPDATE_DATA, 0, 3, 1}),
                    new LegacyCipher(KEY), false);
            throw new AssertionError("truncated packet was accepted");
        } catch (EOFException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
    }
}

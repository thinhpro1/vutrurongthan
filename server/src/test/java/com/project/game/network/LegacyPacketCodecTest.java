package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyPacketCodecTest {
    private static final byte[] KEY = "abc".getBytes(StandardCharsets.US_ASCII);

    @Test
    void continuousCipherDecodesThreeConsecutiveClientFramesWithOneReaderCursor() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        LegacyCipher writer = new LegacyCipher(KEY);
        List<Message> expected = List.of(
                new Message(MessageName.UPDATE_DATA, new byte[]{-1}),
                new Message(MessageName.LOGIN, new byte[]{1, 2}),
                new Message(MessageName.REGISTER_USER, new byte[]{3, 4, 5}));
        for (Message message : expected) {
            codec.writeClient(wire, writer, true, message);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(wire.toByteArray());
        LegacyCipher reader = new LegacyCipher(KEY);
        List<Message> actual = new ArrayList<>();
        for (int index = 0; index < expected.size(); index++) {
            actual.add(codec.read(input, reader, true));
        }

        assertEquals(expected, actual);
    }

    @Test
    void versionSourceFrameMatchesIndependentUnityReadKeyDecoder() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        Message expected = new Message(MessageName.VERSION_SOURCE, new byte[]{1, 2, 3});
        codec.write(wire, new LegacyCipher(KEY), true, expected);

        assertEquals(expected, decodeAsUnitySession(wire.toByteArray(), KEY));
    }

    private static Message decodeAsUnitySession(byte[] wire, byte[] key) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(wire);
        UnityCipher cipher = new UnityCipher(key);
        int command = cipher.readKey(readSignedByte(input));
        int length1 = cipher.readKey(readSignedByte(input)) + 128;
        int length2 = cipher.readKey(readSignedByte(input)) + 128;
        int length3 = cipher.readKey(readSignedByte(input)) + 128;
        int length = (length3 * 256 + length2) * 256 + length1;
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new IOException("truncated Unity-compatible payload");
        }
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) cipher.readKey(payload[index]);
        }
        return new Message(command, payload);
    }

    private static byte readSignedByte(ByteArrayInputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("truncated Unity-compatible frame");
        }
        return (byte) value;
    }

    private static final class UnityCipher {
        private final byte[] key;
        private int cursor;

        private UnityCipher(byte[] key) {
            this.key = key.clone();
        }

        private int readKey(byte value) {
            int decoded = Byte.toUnsignedInt(key[cursor++]) ^ Byte.toUnsignedInt(value);
            cursor %= key.length;
            return (byte) decoded;
        }
    }
}

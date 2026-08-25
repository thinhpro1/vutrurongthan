package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageReader;
import com.project.game.network.message.MessageWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageReaderWriterTest {
    @Test
    void writesBigEndianIntegersAndUtf8() throws Exception {
        assertArrayEquals(new byte[]{1, 2, 3, 4}, new MessageWriter().writeInt(0x01020304).toByteArray());
        assertArrayEquals(new byte[]{0, 3, 'a', 'b', 'c'}, new MessageWriter().writeUtf("abc").toByteArray());
        byte[] unicode = new MessageWriter().writeUtf("Rồng Thần").toByteArray();
        assertEquals("Rồng Thần", new MessageReader(unicode).readUtf());
    }

    @Test
    void writesExactHandshakeAndKnownUpdateFrame() throws Exception {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        codec.writeHandshakeKey(handshake, "abc".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(new byte[]{(byte) 0x80, 0, 4, 3, 0x61, 3, 1}, handshake.toByteArray());

        ByteArrayOutputStream update = new ByteArrayOutputStream();
        codec.writeClient(update, new LegacyCipher("abc".getBytes(StandardCharsets.US_ASCII)), true,
                new Message(MessageName.UPDATE_DATA, new byte[]{-1}));
        assertArrayEquals(new byte[]{(byte) 0xe2, 0x62, 0x62, (byte) 0x9e}, update.toByteArray());
    }

    @Test
    void rejectsTruncatedFrames() {
        LegacyPacketCodec codec = new LegacyPacketCodec(1024);
        assertThrows(EOFException.class, () -> codec.read(
                new ByteArrayInputStream(new byte[]{(byte) MessageName.UPDATE_DATA, 0, 3, 1}),
                new LegacyCipher("abc".getBytes(StandardCharsets.US_ASCII)), false));
    }
}

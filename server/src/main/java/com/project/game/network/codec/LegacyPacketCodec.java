package com.project.game.network.codec;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Codec for the exact packet framing used by the old Unity client. */
public final class LegacyPacketCodec {
    private static final int MAX_SPECIAL_LENGTH = 0x00ffffff;

    private final int maxPacketSize;

    public LegacyPacketCodec(int maxPacketSize) {
        if (maxPacketSize < 0 || maxPacketSize > MAX_SPECIAL_LENGTH) {
            throw new IllegalArgumentException("invalid max packet size");
        }
        this.maxPacketSize = maxPacketSize;
    }

    public int maxPacketSize() {
        return maxPacketSize;
    }

    public Message read(InputStream input, LegacyCipher cipher, boolean keyReady) throws IOException {
        // Server receives client frames. The legacy Unity client always uses a 2-byte length here,
        // including UPDATE_DATA and REQUEST_ICON.
        return read(input, cipher, keyReady, false);
    }

    /** Decoder helper for a client-side test reading server responses. */
    public Message readServerResponse(InputStream input, LegacyCipher cipher, boolean keyReady)
            throws IOException {
        return read(input, cipher, keyReady, true);
    }

    private Message read(InputStream input, LegacyCipher cipher, boolean keyReady, boolean allowSpecialLength)
            throws IOException {
        int wireCommand = readUnsigned(input);
        int command = keyReady ? cipher.decode((byte) wireCommand) : (byte) wireCommand;
        boolean special = allowSpecialLength && isSpecial(command);
        int length;
        if (special) {
            int first = readUnsigned(input);
            int second = readUnsigned(input);
            int third = readUnsigned(input);
            if (keyReady) {
                first = Byte.toUnsignedInt(cipher.decode((byte) (first + 128)));
                second = Byte.toUnsignedInt(cipher.decode((byte) (second + 128)));
                third = Byte.toUnsignedInt(cipher.decode((byte) (third + 128)));
            }
            length = first | (second << 8) | (third << 16);
        } else {
            int high = readUnsigned(input);
            int low = readUnsigned(input);
            if (keyReady) {
                high = Byte.toUnsignedInt(cipher.decode((byte) high));
                low = Byte.toUnsignedInt(cipher.decode((byte) low));
            }
            length = (high << 8) | low;
        }
        validateLength(length, special);
        byte[] payload = readFully(input, length);
        if (keyReady) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = cipher.decode(payload[i]);
            }
        }
        return new Message(command, payload);
    }

    public void write(OutputStream output, LegacyCipher cipher, boolean keyReady, Message message)
            throws IOException {
        write(output, cipher, keyReady, message, true);
    }

    /** Writes a client-to-server frame, where every packet has a 2-byte length. */
    public void writeClient(OutputStream output, LegacyCipher cipher, boolean keyReady, Message message)
            throws IOException {
        write(output, cipher, keyReady, message, false);
    }

    private void write(OutputStream output, LegacyCipher cipher, boolean keyReady, Message message,
                       boolean allowSpecialLength) throws IOException {
        int command = message.command();
        byte[] payload = message.payload();
        boolean special = allowSpecialLength && isSpecial(command);
        validateLength(payload.length, special);

        int encodedCommand = keyReady ? Byte.toUnsignedInt(cipher.encode((byte) command)) : command & 0xff;
        output.write(encodedCommand);
        if (special) {
            writeSpecialLength(output, cipher, keyReady, payload.length);
        } else {
            writeNormalLength(output, cipher, keyReady, payload.length);
        }
        if (keyReady) {
            for (byte value : payload) {
                output.write(Byte.toUnsignedInt(cipher.encode(value)));
            }
        } else {
            output.write(payload);
        }
        output.flush();
    }

    /** Writes the unencrypted CONNECT_SERVER response containing cumulative XOR key bytes. */
    public void writeHandshakeKey(OutputStream output, byte[] key) throws IOException {
        if (key == null || key.length == 0 || key.length > 255) {
            throw new IllegalArgumentException("invalid handshake key");
        }
        byte[] payload = new byte[key.length + 1];
        payload[0] = (byte) key.length;
        payload[1] = key[0];
        for (int i = 1; i < key.length; i++) {
            payload[i + 1] = (byte) (Byte.toUnsignedInt(key[i]) ^ Byte.toUnsignedInt(key[i - 1]));
        }
        write(output, null, false, new Message(MessageName.SEND_SESSION_KEY, payload));
    }

    private void writeNormalLength(OutputStream output, LegacyCipher cipher, boolean keyReady, int length)
            throws IOException {
        writeByte(output, cipher, keyReady, (length >>> 8) & 0xff);
        writeByte(output, cipher, keyReady, length & 0xff);
    }

    private void writeSpecialLength(OutputStream output, LegacyCipher cipher, boolean keyReady, int length)
            throws IOException {
        // The legacy client adds 128 before decoding each encrypted length byte.
        for (int shift = 0; shift <= 16; shift += 8) {
            int encoded = keyReady ? Byte.toUnsignedInt(cipher.encode((byte) (length >>> shift)))
                    : (length >>> shift) & 0xff;
            output.write((encoded - 128) & 0xff);
        }
    }

    private void writeByte(OutputStream output, LegacyCipher cipher, boolean keyReady, int value)
            throws IOException {
        output.write(keyReady ? Byte.toUnsignedInt(cipher.encode((byte) value)) : value);
    }

    private boolean isSpecial(int command) {
        return command == MessageName.VERSION_SOURCE
                || command == MessageName.REQUEST_ICON
                || command == MessageName.UPDATE_DATA;
    }

    private void validateLength(int length, boolean special) throws IOException {
        if (length < 0 || length > maxPacketSize || (!special && length > 0xffff)) {
            throw new IOException("invalid packet length " + length);
        }
    }

    private static int readUnsigned(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("truncated packet");
        }
        return value;
    }

    private static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] payload = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(payload, offset, length - offset);
            if (count < 0) {
                throw new EOFException("truncated packet payload");
            }
            if (count == 0) {
                continue;
            }
            offset += count;
        }
        return payload;
    }
}

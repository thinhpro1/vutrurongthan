package com.project.game.network.message;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Big-endian reader matching MyReader in the legacy Unity client. */
public final class MessageReader {
    private final byte[] data;
    private int position;

    public MessageReader(byte[] data) {
        this.data = data == null ? new byte[0] : data.clone();
    }

    public int remaining() {
        return data.length - position;
    }

    public byte readByte() throws IOException {
        require(1);
        return data[position++];
    }

    public int readUnsignedByte() throws IOException {
        return Byte.toUnsignedInt(readByte());
    }

    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    public short readShort() throws IOException {
        require(2);
        return (short) ((Byte.toUnsignedInt(data[position++]) << 8)
                | Byte.toUnsignedInt(data[position++]));
    }

    public int readUnsignedShort() throws IOException {
        return Short.toUnsignedInt(readShort());
    }

    public int readInt() throws IOException {
        require(4);
        return (Byte.toUnsignedInt(data[position++]) << 24)
                | (Byte.toUnsignedInt(data[position++]) << 16)
                | (Byte.toUnsignedInt(data[position++]) << 8)
                | Byte.toUnsignedInt(data[position++]);
    }

    public long readLong() throws IOException {
        require(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | Byte.toUnsignedInt(data[position++]);
        }
        return value;
    }

    public String readUtf() throws IOException {
        int byteLength = readUnsignedShort();
        require(byteLength);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data, position, byteLength))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("malformed UTF-8 payload", exception);
        } finally {
            position += byteLength;
        }
    }

    public byte[] readBytes(int length) throws IOException {
        if (length < 0) {
            throw new IOException("negative byte length");
        }
        require(length);
        byte[] result = java.util.Arrays.copyOfRange(data, position, position + length);
        position += length;
        return result;
    }

    private void require(int length) throws EOFException {
        if (length < 0 || position > data.length - length) {
            throw new EOFException("message payload ended at " + position + ", required " + length);
        }
    }
}

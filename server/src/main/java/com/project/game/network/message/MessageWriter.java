package com.project.game.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Big-endian writer matching MyWriter in the legacy Unity client. */
public final class MessageWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public MessageWriter writeByte(int value) {
        output.write(value);
        return this;
    }

    public MessageWriter writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public MessageWriter writeShort(int value) {
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
        return this;
    }

    public MessageWriter writeInt(int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
        return this;
    }

    public MessageWriter writeLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
        return this;
    }

    public MessageWriter writeUtf(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) {
            throw new IOException("UTF payload is larger than 65535 bytes");
        }
        return writeShort(bytes.length).writeBytes(bytes);
    }

    public MessageWriter writeBytes(byte[] bytes) {
        if (bytes != null) {
            output.writeBytes(bytes);
        }
        return this;
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }
}

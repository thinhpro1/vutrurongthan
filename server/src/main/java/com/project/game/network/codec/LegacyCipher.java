package com.project.game.network.codec;

import java.util.Arrays;

/** Continuous XOR cursors used by the legacy client after CONNECT_SERVER. */
public final class LegacyCipher {
    private final byte[] key;
    private int readIndex;
    private int writeIndex;

    public LegacyCipher(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("legacy key must not be empty");
        }
        this.key = key.clone();
    }

    public byte decode(byte value) {
        byte result = xor(value, readIndex);
        readIndex = (readIndex + 1) % key.length;
        return result;
    }

    public byte encode(byte value) {
        byte result = xor(value, writeIndex);
        writeIndex = (writeIndex + 1) % key.length;
        return result;
    }

    public byte[] key() {
        return key.clone();
    }

    public void reset() {
        readIndex = 0;
        writeIndex = 0;
    }

    private byte xor(byte value, int index) {
        return (byte) (Byte.toUnsignedInt(value) ^ Byte.toUnsignedInt(key[index]));
    }
}

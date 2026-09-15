package com.project.game.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record IconFingerprint(int iconId, long fingerprint) {
    public IconFingerprint {
        if (iconId < 0 || iconId > Short.MAX_VALUE) {
            throw new IllegalArgumentException("icon id must be between 0 and 32767: " + iconId);
        }
    }

    public static long fingerprint64(byte[] data) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }

        byte[] hash = digest.digest(data);
        long value = 0L;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (hash[index] & 0xFFL);
        }
        return value;
    }
}

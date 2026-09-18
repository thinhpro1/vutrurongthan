package com.project.game.persistence.account;

import java.time.Instant;
import java.util.Objects;

public record AccountRecord(
        long id,
        String username,
        byte[] passwordHash,
        byte[] passwordSalt,
        int role,
        boolean locked,
        String ipAddress,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {
    public AccountRecord {
        if (id <= 0) {
            throw new IllegalArgumentException("account id must be positive");
        }
        username = Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("account username must not be blank");
        }
        passwordHash = copyWithLength(passwordHash, 32, "passwordHash");
        passwordSalt = copyWithLength(passwordSalt, 16, "passwordSalt");
        if (role < 0) {
            throw new IllegalArgumentException("account role must not be negative");
        }
        if (ipAddress != null && ipAddress.length() > 45) {
            throw new IllegalArgumentException("account ipAddress is too long");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public byte[] passwordHash() {
        return passwordHash.clone();
    }

    @Override
    public byte[] passwordSalt() {
        return passwordSalt.clone();
    }

    private static byte[] copyWithLength(byte[] value, int expectedLength, String name) {
        Objects.requireNonNull(value, name);
        if (value.length != expectedLength) {
            throw new IllegalArgumentException(
                    name + " must contain exactly " + expectedLength + " bytes");
        }
        return value.clone();
    }
}

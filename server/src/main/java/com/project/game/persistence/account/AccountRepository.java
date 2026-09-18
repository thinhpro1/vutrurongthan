package com.project.game.persistence.account;

import java.time.Instant;
import java.util.Optional;

public interface AccountRepository {
    Optional<AccountRecord> findByUsername(String username);

    long create(String username, byte[] passwordHash, byte[] passwordSalt, String ipAddress);

    void updateSuccessfulLogin(long accountId, String ipAddress, Instant loginAt);
}

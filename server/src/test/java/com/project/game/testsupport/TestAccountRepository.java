package com.project.game.testsupport;

import com.project.game.persistence.account.AccountRecord;
import com.project.game.persistence.account.AccountRepository;
import com.project.game.persistence.account.AccountRepositoryException;
import com.project.game.persistence.account.DuplicateAccountException;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TestAccountRepository implements AccountRepository {
    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<String, AccountRecord> accounts = new ConcurrentHashMap<>();
    private final AtomicInteger metadataUpdateCount = new AtomicInteger();
    private volatile boolean failCreate;
    private volatile boolean failFind;
    private volatile boolean failUpdate;

    @Override
    public Optional<AccountRecord> findByUsername(String username) {
        if (failFind) throw new AccountRepositoryException("injected find failure");
        return Optional.ofNullable(accounts.get(username));
    }

    @Override
    public long create(String username, byte[] passwordHash, byte[] passwordSalt, String ipAddress) {
        if (failCreate) throw new AccountRepositoryException("injected create failure");
        long id = nextId.getAndIncrement();
        Instant now = Instant.now();
        AccountRecord record = new AccountRecord(id, username, passwordHash, passwordSalt, 0,
                false, ipAddress, now, now, null);
        if (accounts.putIfAbsent(username, record) != null) {
            throw new DuplicateAccountException("duplicate test account", null);
        }
        return id;
    }

    @Override
    public void updateSuccessfulLogin(long accountId, String ipAddress, Instant loginAt) {
        if (failUpdate) throw new AccountRepositoryException("injected update failure");
        accounts.computeIfPresent(findUsername(accountId), (username, current) -> {
            metadataUpdateCount.incrementAndGet();
            return new AccountRecord(current.id(), current.username(), current.passwordHash(),
                    current.passwordSalt(), current.role(), current.locked(), ipAddress,
                    current.createdAt(), loginAt, loginAt);
        });
    }

    private String findUsername(long accountId) {
        return accounts.values().stream().filter(account -> account.id() == accountId)
                .map(AccountRecord::username).findFirst()
                .orElseThrow(() -> new AccountRepositoryException("missing test account id"));
    }

    public AccountRecord requireAccount(String username) {
        AccountRecord record = accounts.get(username);
        if (record == null) throw new AssertionError("missing test account: " + username);
        return record;
    }

    public int accountCount() { return accounts.size(); }

    public int metadataUpdateCount() { return metadataUpdateCount.get(); }

    public void lock(String username) {
        accounts.computeIfPresent(username, (ignored, current) -> new AccountRecord(
                current.id(), current.username(), current.passwordHash(), current.passwordSalt(),
                current.role(), true, current.ipAddress(), current.createdAt(),
                current.updatedAt(), current.lastLoginAt()));
    }

    public void failCreate(boolean value) { failCreate = value; }

    public void failFind(boolean value) { failFind = value; }

    public void failUpdate(boolean value) { failUpdate = value; }
}

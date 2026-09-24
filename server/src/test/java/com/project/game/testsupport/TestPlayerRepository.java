package com.project.game.testsupport;

import com.project.game.persistence.player.DuplicatePlayerException;
import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;
import com.project.game.player.PlayerSaveData;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestPlayerRepository implements PlayerRepository {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Long, PlayerRecord> byAccount = new ConcurrentHashMap<>();
    private final Map<String, PlayerRecord> byName = new ConcurrentHashMap<>();
    private volatile boolean failFind;
    private volatile boolean failFindRuntime;
    private volatile boolean failCreate;
    private volatile boolean failUpdate;

    @Override
    public Optional<PlayerRecord> findByAccountId(long accountId) {
        if (failFind) {
            throw new PlayerRepositoryException("injected find failure");
        }
        if (failFindRuntime) {
            throw new IllegalArgumentException("injected corrupt row failure");
        }
        return Optional.ofNullable(byAccount.get(accountId));
    }

    @Override
    public PlayerRecord create(PlayerRecord initialWithoutId) {
        if (failCreate) {
            throw new PlayerRepositoryException("injected create failure");
        }
        if (initialWithoutId.id() != 0) {
            throw new IllegalArgumentException("test create requires id 0");
        }
        if (byAccount.containsKey(initialWithoutId.accountId())
                || byName.containsKey(initialWithoutId.name())) {
            throw new DuplicatePlayerException("duplicate test player", null);
        }
        PlayerRecord created = withId(initialWithoutId, nextId.getAndIncrement());
        if (byAccount.putIfAbsent(created.accountId(), created) != null
                || byName.putIfAbsent(created.name(), created) != null) {
            byAccount.remove(created.accountId(), created);
            byName.remove(created.name(), created);
            throw new DuplicatePlayerException("duplicate test player", null);
        }
        return created;
    }

    @Override
    public void save(PlayerSaveData player) {
        if (failUpdate) {
            throw new PlayerRepositoryException("injected update failure");
        }
        PlayerRecord updated = PlayerRecord.fromSaveData(player);
        byAccount.compute(player.accountId(), (ignored, current) -> {
            if (current == null || current.id() != player.id()) {
                throw new PlayerRepositoryException("missing test player");
            }
            byName.put(player.name(), updated);
            return updated;
        });
    }

    public void failFind(boolean value) {
        failFind = value;
    }

    public void failFindRuntime(boolean value) {
        failFindRuntime = value;
    }

    public void failCreate(boolean value) {
        failCreate = value;
    }

    public void failUpdate(boolean value) {
        failUpdate = value;
    }

    public PlayerRecord requireByAccountId(long accountId) {
        return byAccount.get(accountId);
    }

    private static PlayerRecord withId(PlayerRecord player, int id) {
        return new PlayerRecord(
                id, player.accountId(), player.name(), player.gender(), player.power(),
                player.potential(), player.level(), player.exp(), player.baseStats(),
                player.currentStats(), player.hp(), player.mp(), player.appearance(),
                player.coin(), player.coinLock(), player.diamond(), player.ruby(),
                player.mapId(), player.x(), player.y());
    }
}

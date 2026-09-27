package com.project.game.player;

import com.project.game.persistence.player.PlayerRecord;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.persistence.player.PlayerRepositoryException;

import java.util.Objects;

/** Player lifecycle bridge between runtime state and persistence. */
public final class PlayerManager {
    private final PlayerRepository repository;

    public PlayerManager(PlayerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Player load(long accountId) {
        PlayerRecord record = repository.findByAccountId(accountId).orElse(null);
        if (record == null) {
            return null;
        }
        try {
            return record.toPlayer(0);
        } catch (RuntimeException exception) {
            throw new PlayerRepositoryException("failed to load player runtime", exception);
        }
    }

    public Player create(long accountId, String name, int gender) {
        Player initial = Player.create(accountId, name, gender);
        PlayerSaveData stable = PlayerSaveData.capture(initial);
        PlayerRecord created = repository.create(PlayerRecord.withoutId(stable));
        return created.toPlayer(0);
    }

    public void save(PlayerSaveData player) {
        repository.save(player);
    }
}

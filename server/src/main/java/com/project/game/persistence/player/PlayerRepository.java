package com.project.game.persistence.player;

import com.project.game.player.PlayerSaveData;

import java.util.Optional;

public interface PlayerRepository {
    Optional<PlayerRecord> findByAccountId(long accountId);

    PlayerRecord create(PlayerRecord initialWithoutId);

    void save(PlayerSaveData player);
}

package com.project.game.persistence.player;

import com.project.game.player.PlayerProfile;

import java.time.Instant;
import java.util.Optional;

public interface PlayerRepository {
    Optional<PlayerRecord> findByAccountId(long accountId);

    PlayerRecord create(PlayerRecord initialWithoutId);

    void updateCheckpoint(PlayerProfile player, Instant playedAt);
}

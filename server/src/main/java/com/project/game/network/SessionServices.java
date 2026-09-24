package com.project.game.network;

import com.project.game.account.AccountAuth;
import com.project.game.combat.Combat;
import com.project.game.map.MapManager;
import com.project.game.monster.MonsterManager;
import com.project.game.persistence.player.PlayerRepository;
import com.project.game.resource.GameResources;

import java.util.Objects;

/** Dependency bundle dùng chung cho một Session legacy đã được chấp nhận. */
public record SessionServices(AccountAuth auth, GameResources resources, MapManager maps,
                             Combat combat, MonsterManager monsterManager,
                             PlayerRepository playerRepository) {
    public SessionServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(maps, "maps");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(monsterManager, "monsterManager");
        Objects.requireNonNull(playerRepository, "playerRepository");
    }

}

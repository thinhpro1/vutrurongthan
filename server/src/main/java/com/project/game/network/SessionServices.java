package com.project.game.network;

import com.project.game.account.AuthService;
import com.project.game.combat.CombatService;
import com.project.game.map.MapManager;
import com.project.game.monster.MonsterManager;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;

import java.util.Objects;

/** Shared coarse services used by one accepted legacy session. */
public record SessionServices(AuthService auth, GameResources resources, MapManager maps,
                             CombatService combat, MonsterManager monsterManager, PlayerService players) {
    public SessionServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(maps, "maps");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(monsterManager, "monsterManager");
        Objects.requireNonNull(players, "players");
    }

}

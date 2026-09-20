package com.project.game.service;

import com.project.game.account.AuthService;
import com.project.game.combat.CombatService;
import com.project.game.map.MapService;
import com.project.game.monster.MonsterService;
import com.project.game.player.PlayerService;
import com.project.game.resource.GameResources;

import java.util.Objects;

/** Shared coarse services used by one accepted legacy session. */
public record ServerServices(AuthService auth, GameResources resources, MapService maps,
                             CombatService combat, MonsterService monsters, PlayerService players) {
    public ServerServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(maps, "maps");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(monsters, "monsters");
        Objects.requireNonNull(players, "players");
    }

}

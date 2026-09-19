package com.project.game.service;

import com.project.game.map.MapService;

import java.util.Objects;

/** Shared coarse services used by one accepted legacy session. */
public record ServerServices(AuthService auth, ResourceService resources, MapService maps,
                             PlayerService players) {
    public ServerServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(maps, "maps");
        Objects.requireNonNull(players, "players");
    }

}

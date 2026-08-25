package com.project.game.service;

import java.util.Objects;

/** Shared coarse services used by one accepted legacy session. */
public record ServerServices(AuthService auth, ResourceService resources) {
    public ServerServices {
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(resources, "resources");
    }

    public static ServerServices defaults() {
        return new ServerServices(new AuthService(), ResourceService.unavailable());
    }
}

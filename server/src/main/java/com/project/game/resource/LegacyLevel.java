package com.project.game.resource;

import java.util.Objects;

public record LegacyLevel(int id, String name, long power) {
    public LegacyLevel {
        Objects.requireNonNull(name, "name");
    }
}

package com.project.game.resource;

import java.util.Objects;

public record LevelTemplate(int id, String name, long power) {
    public LevelTemplate {
        Objects.requireNonNull(name, "name");
    }
}

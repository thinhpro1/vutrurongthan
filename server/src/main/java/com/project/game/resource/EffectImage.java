package com.project.game.resource;

import java.util.List;
import java.util.Objects;

public record EffectImage(
        int id,
        int dx,
        int dy,
        int delay,
        List<Integer> icons
) {
    public EffectImage {
        icons = List.copyOf(Objects.requireNonNull(icons, "icons"));
    }
}

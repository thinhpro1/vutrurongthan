package com.project.game.resource;

import java.util.List;
import java.util.Objects;

public record LegacyEffectImage(
        int id,
        int dx,
        int dy,
        int delay,
        List<Integer> icons
) {
    public LegacyEffectImage {
        icons = List.copyOf(Objects.requireNonNull(icons, "icons"));
    }
}

package com.project.game.resource;

import java.util.List;
import java.util.Objects;

public record LegacySkillOption(
        int id,
        String name,
        List<Integer> normal,
        List<Integer> upgrade
) {
    public LegacySkillOption {
        Objects.requireNonNull(name, "name");
        normal = List.copyOf(Objects.requireNonNull(normal, "normal"));
        upgrade = List.copyOf(Objects.requireNonNull(upgrade, "upgrade"));
    }
}

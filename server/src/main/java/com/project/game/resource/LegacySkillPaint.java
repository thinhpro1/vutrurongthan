package com.project.game.resource;

import java.util.Objects;

public record LegacySkillPaint(String percent, int paintId) {
    public LegacySkillPaint {
        Objects.requireNonNull(percent, "percent");
    }
}

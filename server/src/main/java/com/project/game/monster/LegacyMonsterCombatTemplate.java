package com.project.game.monster;

public record LegacyMonsterCombatTemplate(
        int templateId,
        long damage,
        long potentialReward
) {
    public LegacyMonsterCombatTemplate {
        if (potentialReward < 0L) {
            throw new IllegalArgumentException("monster potential reward must be non-negative");
        }
    }
}

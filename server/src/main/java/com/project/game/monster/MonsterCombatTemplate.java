package com.project.game.monster;

public record MonsterCombatTemplate(
        int templateId,
        long damage,
        long potentialReward
) {
    public MonsterCombatTemplate {
        if (potentialReward < 0L) {
            throw new IllegalArgumentException("monster potential reward must be non-negative");
        }
    }
}

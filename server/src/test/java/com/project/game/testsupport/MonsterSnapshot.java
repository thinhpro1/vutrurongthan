package com.project.game.testsupport;

import com.project.game.monster.Monster;

/** Bản chụp trạng thái một Monster cho test (chụp trên thread của Zone). */
public record MonsterSnapshot(
        int type,
        int templateId,
        int id,
        int level,
        int levelStatus,
        int x,
        int y,
        long maxHp,
        long hp,
        int status
) {
    public static MonsterSnapshot of(Monster monster) {
        return new MonsterSnapshot(monster.type(), monster.templateId(), monster.id(),
                monster.level(), monster.levelStatus(), monster.x(), monster.y(),
                monster.maxHp(), monster.hp(), monster.status());
    }
}

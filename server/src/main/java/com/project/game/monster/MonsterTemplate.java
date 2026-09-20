package com.project.game.monster;

import java.util.List;

/** Immutable client-visible monster template. */
public record MonsterTemplate(
        int id,
        String name,
        int rangeMove,
        int speed,
        int type,
        int dartId,
        List<Integer> iconsMove,
        int iconInjure,
        int iconAttack,
        int w,
        int h,
        int dx,
        int dy
) {
    public MonsterTemplate {
        iconsMove = List.copyOf(iconsMove);
    }
}

package com.project.game.monster;

import java.util.List;
import java.util.Objects;

/** Immutable client-visible monster template. */
public record MonsterTemplate(
        int id,
        String name,
        int rangeMove,
        int speed,
        int type,
        int dartId,
        List<Integer> iconsMove,
        List<Integer> iconsInjure,
        List<Integer> iconsAttack,
        int w,
        int h
) {
    public MonsterTemplate {
        name = Objects.requireNonNull(name, "name");
        iconsMove = List.copyOf(Objects.requireNonNull(iconsMove, "iconsMove"));
        iconsInjure = List.copyOf(Objects.requireNonNull(iconsInjure, "iconsInjure"));
        iconsAttack = List.copyOf(Objects.requireNonNull(iconsAttack, "iconsAttack"));
    }
}

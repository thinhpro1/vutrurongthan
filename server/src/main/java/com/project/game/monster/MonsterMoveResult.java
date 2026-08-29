package com.project.game.monster;

public record MonsterMoveResult(
        int monsterId,
        int x,
        int y,
        int dir
) {
}

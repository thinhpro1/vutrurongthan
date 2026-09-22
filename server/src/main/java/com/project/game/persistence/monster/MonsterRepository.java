package com.project.game.persistence.monster;

import java.util.List;

public interface MonsterRepository {
    List<TemplateRow> findAllTemplates();

    List<SpawnRow> findAllSpawns();

    record TemplateRow(
            int id,
            String name,
            int level,
            long hp,
            long damage,
            long potentialReward,
            int rangeMove,
            int speed,
            int typeMove,
            int dartId,
            String iconMove,
            String iconAttack,
            String iconInjure,
            int w,
            int h
    ) {}

    record SpawnRow(
            int id,
            int mapId,
            int monsterId,
            int x,
            int y
    ) {}
}

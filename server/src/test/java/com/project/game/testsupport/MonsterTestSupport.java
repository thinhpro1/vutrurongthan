package com.project.game.testsupport;

import com.project.game.persistence.monster.MonsterRepository;

import java.util.List;

/** Builds the canonical database-backed monster catalog used by server tests. */
public final class MonsterTestSupport {
    private MonsterTestSupport() {
    }

    public static MonsterRepository canonicalRepository() {
        return new Repository();
    }

    private static final class Repository implements MonsterRepository {
        @Override
        public List<TemplateRow> findAllTemplates() {
            return List.of(new TemplateRow(
                    1,
                    "Hổ nanh kiếm",
                    2,
                    300L,
                    10L,
                    10L,
                    100,
                    1,
                    1,
                    0,
                    "[11818,11819,11820,11821,11822]",
                    "[11823]",
                    "[11824]",
                    175,
                    95));
        }

        @Override
        public List<SpawnRow> findAllSpawns() {
            return List.of(
                    new SpawnRow(101, 1, 1, 975, 936),
                    new SpawnRow(102, 1, 1, 1348, 936),
                    new SpawnRow(103, 1, 1, 1800, 936),
                    new SpawnRow(104, 1, 1, 2250, 936),
                    new SpawnRow(105, 1, 1, 2600, 936),
                    new SpawnRow(106, 1, 1, 2950, 936));
        }
    }
}

package com.project.game.monster;

import com.project.game.map.MapManager;
import com.project.game.map.Zone;

import java.time.Clock;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Traverses public Map/Zone lifecycle; Zone owns each Monster update. */
public final class MonsterManager {
    private final MapManager maps;
    private final Clock clock;
    private final RandomGenerator random;

    public MonsterManager(MapManager maps) {
        this(maps, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public MonsterManager(MapManager maps, Clock clock, RandomGenerator random) {
        this.maps = Objects.requireNonNull(maps, "maps");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public void update() {
        long nowMillis = clock.millis();
        for (com.project.game.map.Map map : maps.maps()) {
            for (Zone zone : map.zones()) {
                zone.updateMonsters(nowMillis, random);
            }
        }
    }
}

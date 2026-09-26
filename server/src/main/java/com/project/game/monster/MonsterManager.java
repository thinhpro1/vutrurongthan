package com.project.game.monster;

import com.project.game.map.MapManager;
import com.project.game.map.Zone;
import com.project.game.resource.GameResources;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns Monster templates, runtime creation, and the public-world lifecycle. */
public final class MonsterManager {
    private static final long LIFECYCLE_PERIOD_MILLIS = 100L;
    private static final Logger LOGGER = Logger.getLogger(MonsterManager.class.getName());

    private final GameResources resources;
    private final Map<Integer, MonsterTemplate> templates;
    private final Clock clock;
    private final RandomGenerator random;
    private ScheduledExecutorService executor;

    public MonsterManager(GameResources resources) {
        this(resources, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public MonsterManager(GameResources resources, Clock clock, RandomGenerator random) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");

        Map<Integer, MonsterTemplate> index = new HashMap<>();
        for (MonsterTemplate template : resources.monsterTemplates()) {
            if (index.putIfAbsent(template.id(), template) != null) {
                throw new IllegalArgumentException("duplicate monster template " + template.id());
            }
        }
        templates = Map.copyOf(index);
    }

    /** Finds a static Monster template without creating anything. */
    public MonsterTemplate findTemplate(int id) {
        return templates.get(id);
    }

    /** Creates independent runtime Monsters for every spawn on a Map. */
    public List<Monster> createForMap(int mapId) {
        List<MonsterSpawn> spawns = resources.monstersForMap(mapId);
        List<Monster> monsters = new ArrayList<>(spawns.size());
        for (MonsterSpawn spawn : spawns) {
            MonsterTemplate template = findTemplate(spawn.templateId());
            if (template == null) {
                throw new IllegalStateException("missing monster template " + spawn.templateId());
            }
            monsters.add(new Monster(spawn, template));
        }
        return List.copyOf(monsters);
    }

    /** Updates every public Zone; each Zone remains the mutation owner. */
    public void update(MapManager maps) {
        Objects.requireNonNull(maps, "maps");
        long nowMillis = clock.millis();
        for (com.project.game.map.Map map : maps.maps()) {
            for (Zone zone : map.zones()) {
                zone.updateMonsters(nowMillis, random);
            }
        }
    }

    /** Starts the 100 ms public-world trigger once. */
    public synchronized void start(MapManager maps) {
        Objects.requireNonNull(maps, "maps");
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "monster-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(
                () -> runSafely(maps),
                LIFECYCLE_PERIOD_MILLIS,
                LIFECYCLE_PERIOD_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /** Stops the lifecycle trigger and allows a later start. */
    public synchronized void stop() {
        ScheduledExecutorService current = executor;
        if (current == null) {
            return;
        }
        executor = null;
        current.shutdownNow();
    }

    private void runSafely(MapManager maps) {
        try {
            update(maps);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Monster lifecycle tick failed", exception);
        }
    }
}

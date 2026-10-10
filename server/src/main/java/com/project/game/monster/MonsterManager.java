package com.project.game.monster;

import com.project.game.map.MapManager;
import com.project.game.map.Zone;
import com.project.game.monster.MonsterTemplate.Spawn;
import com.project.game.resource.GameResources;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Giữ template Monster, tạo Monster cho Map, và bật/tắt vòng update của các Zone public. */
public final class MonsterManager {
    private final GameResources resources;
    private final Map<Integer, MonsterTemplate> templates;
    private final Clock clock;
    // Các Zone tick song song: mỗi Zone nhận generator riêng từ đây.
    private final Supplier<RandomGenerator> newRandom;

    /** Server thật: mỗi Zone một RandomGenerator mới, không Zone nào dùng chung. */
    public MonsterManager(GameResources resources) {
        this(resources, Clock.systemUTC(), RandomGenerator::getDefault);
    }

    /**
     * Test cần kết quả tái lập: mọi Zone dùng chung {@code random}. Kiểu là
     * {@link java.util.Random} vì JDK bảo đảm nó an toàn khi nhiều thread cùng gọi.
     */
    public MonsterManager(GameResources resources, Clock clock, Random random) {
        this(resources, clock, sharedRandom(random));
    }

    MonsterManager(GameResources resources, Clock clock, Supplier<RandomGenerator> newRandom) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.newRandom = Objects.requireNonNull(newRandom, "newRandom");

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
        List<Spawn> spawns = resources.monstersForMap(mapId);
        List<Monster> monsters = new ArrayList<>(spawns.size());
        for (Spawn spawn : spawns) {
            MonsterTemplate template = findTemplate(spawn.templateId());
            if (template == null) {
                throw new IllegalStateException("missing monster template " + spawn.templateId());
            }
            monsters.add(new Monster(spawn, template));
        }
        return List.copyOf(monsters);
    }

    /** Một nhịp update cho mọi Zone public, chạy từ bên ngoài (test, công cụ). */
    public void update(MapManager maps) {
        Objects.requireNonNull(maps, "maps");
        long nowMillis = clock.millis();
        RandomGenerator random = newRandom.get(); // chỉ dùng trên thread gọi, lần lượt từng Zone
        for (com.project.game.map.Map map : maps.maps()) {
            for (Zone zone : map.zones()) {
                zone.tick(nowMillis, random);
            }
        }
    }

    /** Bật vòng update của mọi Zone public; mỗi Zone tự chạy trên virtual thread của nó. */
    public void start(MapManager maps) {
        Objects.requireNonNull(maps, "maps");
        for (com.project.game.map.Map map : maps.maps()) {
            for (Zone zone : map.zones()) {
                zone.startUpdate(clock, newRandom.get());
            }
        }
    }

    /** Tắt vòng update của mọi Zone public; có thể start lại sau. */
    public void stop(MapManager maps) {
        Objects.requireNonNull(maps, "maps");
        for (com.project.game.map.Map map : maps.maps()) {
            for (Zone zone : map.zones()) {
                zone.stopUpdate();
            }
        }
    }

    private static Supplier<RandomGenerator> sharedRandom(Random random) {
        Objects.requireNonNull(random, "random");
        return () -> random;
    }
}

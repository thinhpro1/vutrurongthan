package com.project.game.monster;

import com.project.game.service.ResourceService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMonsterTest {
    @Test
    void appliesNonLethalDamage() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result = monster.applyDamage(10).orElseThrow();

        assertEquals(new MonsterDamageResult(0, 10, 290, false), result);
        assertTrue(monster.isAlive());
        assertEquals(290, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void clampsLethalDamageToZeroAndMarksDead() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result = monster.applyDamage(500).orElseThrow();

        assertEquals(0, result.hpAfter());
        assertTrue(result.killed());
        assertFalse(monster.isAlive());
        assertEquals(0, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void deadMonsterRejectsFurtherDamage() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(500).orElseThrow();

        assertTrue(monster.applyDamage(10).isEmpty());
        assertEquals(0, monster.snapshot().hp());
    }

    @Test
    void invalidDamageIsIgnored() {
        RuntimeMonster monster = map1Monster();

        assertTrue(monster.applyDamage(0).isEmpty());
        assertTrue(monster.applyDamage(-1).isEmpty());
        assertEquals(300, monster.snapshot().hp());
    }

    private static RuntimeMonster map1Monster() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        return new MonsterRuntimeFactory(resources).createForMap(1).getFirst();
    }
}

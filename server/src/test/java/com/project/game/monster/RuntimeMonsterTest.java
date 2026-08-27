package com.project.game.monster;

import com.project.game.service.ResourceService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMonsterTest {
    private static final long NOW = 1_000_000L;
    private static final long RESPAWN_DELAY = 9_000L;

    @Test
    void appliesNonLethalDamage() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result = monster.applyDamage(10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new MonsterDamageResult(0, 10, 290, false), result);
        assertTrue(monster.isAlive());
        assertEquals(290, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void clampsLethalDamageToZeroAndMarksDead() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result = monster.applyDamage(500, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(0, result.hpAfter());
        assertTrue(result.killed());
        assertFalse(monster.isAlive());
        assertEquals(0, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void deadMonsterRejectsFurtherDamageUntilRespawn() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.applyDamage(10, NOW + 1_000, RESPAWN_DELAY).isEmpty());
        assertEquals(0L, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void invalidDamageIsIgnored() {
        RuntimeMonster monster = map1Monster();

        assertTrue(monster.applyDamage(0, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(-1, NOW, RESPAWN_DELAY).isEmpty());
        assertEquals(300, monster.snapshot().hp());
    }

    @Test
    void nonLethalDamageDoesNotScheduleRespawn() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result =
                monster.applyDamage(10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new MonsterDamageResult(0, 10, 290, false), result);
        assertTrue(monster.respawnIfDue(Long.MAX_VALUE).isEmpty());
        assertTrue(monster.isAlive());
        assertEquals(290L, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void lethalDamageUsesStrictGreaterThanRespawnDeadline() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult death =
                monster.applyDamage(500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(death.killed());
        assertEquals(0L, death.hpAfter());
        assertFalse(monster.isAlive());

        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY - 1).isEmpty());
        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY).isEmpty());

        MonsterRespawnResult respawn =
                monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(new MonsterRespawnResult(0, 0, 300L), respawn);
        assertTrue(monster.isAlive());
        assertEquals(300L, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void respawnTransitionOccursOnlyOnceAndKeepsRuntimeId() {
        RuntimeMonster monster = map1Monster();
        int runtimeId = monster.id();

        monster.applyDamage(500, NOW, RESPAWN_DELAY).orElseThrow();

        MonsterRespawnResult first =
                monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(runtimeId, first.monsterId());
        assertEquals(runtimeId, monster.id());
        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY + 2).isEmpty());
        assertTrue(monster.isAlive());
    }

    @Test
    void respawnRestoresCanonicalSpawnCoordinates() throws Exception {
        RuntimeMonster monster = map1Monster();

        setIntField(monster, "x", 1111);
        setIntField(monster, "y", 2222);

        monster.applyDamage(500, NOW, RESPAWN_DELAY).orElseThrow();
        monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        MonsterSnapshot snapshot = monster.snapshot();
        assertEquals(975, snapshot.x());
        assertEquals(936, snapshot.y());
    }

    @Test
    void lethalDamageRejectsRespawnDeadlineOverflow() {
        RuntimeMonster monster = map1Monster();

        assertThrows(ArithmeticException.class,
                () -> monster.applyDamage(500, Long.MAX_VALUE - 10, 100));
    }

    private static void setIntField(RuntimeMonster monster, String fieldName, int value)
            throws Exception {
        var field = RuntimeMonster.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(monster, value);
    }

    private static RuntimeMonster map1Monster() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        return new MonsterRuntimeFactory(resources).createForMap(1).getFirst();
    }
}

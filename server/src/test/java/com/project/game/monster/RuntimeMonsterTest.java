package com.project.game.monster;

import com.project.game.service.ResourceService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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

        MonsterDamageResult result = monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new MonsterDamageResult(0, 10, 290, false, 0L), result);
        assertFalse(result.killed());
        assertEquals(0L, result.potentialReward());
        assertTrue(monster.isAlive());
        assertEquals(290, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void clampsLethalDamageToZeroAndMarksDead() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result = monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(0, result.hpAfter());
        assertTrue(result.killed());
        assertEquals(10L, result.potentialReward());
        assertFalse(monster.isAlive());
        assertEquals(0, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void deadMonsterRejectsFurtherDamageUntilRespawn() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.applyDamage(8, 10, NOW + 1_000, RESPAWN_DELAY).isEmpty());
        assertEquals(0L, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void invalidDamageIsIgnored() {
        RuntimeMonster monster = map1Monster();

        assertTrue(monster.applyDamage(7, 0, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(8, -1, NOW, RESPAWN_DELAY).isEmpty());
        assertEquals(300, monster.snapshot().hp());
    }

    @Test
    void nonLethalDamageDoesNotScheduleRespawn() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult result =
                monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new MonsterDamageResult(0, 10, 290, false, 0L), result);
        assertFalse(result.killed());
        assertEquals(0L, result.potentialReward());
        assertTrue(monster.respawnIfDue(Long.MAX_VALUE).isEmpty());
        assertTrue(monster.isAlive());
        assertEquals(290L, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void lethalDamageUsesStrictGreaterThanRespawnDeadline() {
        RuntimeMonster monster = map1Monster();

        MonsterDamageResult death =
                monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(death.killed());
        assertEquals(10L, death.potentialReward());
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

        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

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

        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();
        monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        MonsterSnapshot snapshot = monster.snapshot();
        assertEquals(975, snapshot.x());
        assertEquals(936, snapshot.y());
    }

    @Test
    void storesCanonicalRunMovementMetadata() {
        RuntimeMonster monster = map1Monster();

        assertEquals(100, monster.rangeMove());
        assertEquals(1, monster.speed());
        assertEquals(1, monster.moveType());
        assertEquals(1, monster.moveDir());
        assertEquals(975, monster.xFirst());
        assertEquals(975, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
    }

    @Test
    void patrolMovesByExactServerStepAndKeepsSpawnY() {
        RuntimeMonster monster = map1Monster();

        MonsterMoveResult move = monster.patrolOrReturn().orElseThrow();

        assertEquals(new MonsterMoveResult(0, 979, 936, 1), move);
        assertEquals(979, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
    }

    @Test
    void patrolClampsAtRightBoundaryAndWalksBackTowardCorridor() throws Exception {
        RuntimeMonster monster = map1Monster();

        setIntField(monster, "x", 1073);
        setIntField(monster, "moveDir", 1);

        MonsterMoveResult boundary = monster.patrolOrReturn().orElseThrow();

        assertEquals(1075, boundary.x());
        assertEquals(-1, boundary.dir());

        setIntField(monster, "x", 1200);
        MonsterMoveResult returning = monster.patrolOrReturn().orElseThrow();
        assertEquals(1196, returning.x());
        assertEquals(-1, returning.dir());
    }

    @Test
    void patrolFlipsDirectionWhenStepLandsExactlyOnBoundary() throws Exception {
        RuntimeMonster monster = map1Monster();

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);

        MonsterMoveResult right = monster.patrolOrReturn().orElseThrow();

        assertEquals(1075, right.x());
        assertEquals(-1, right.dir());

        MonsterMoveResult afterRight = monster.patrolOrReturn().orElseThrow();
        assertEquals(1071, afterRight.x());
        assertEquals(-1, afterRight.dir());

        setIntField(monster, "x", 879);
        setIntField(monster, "moveDir", -1);

        MonsterMoveResult left = monster.patrolOrReturn().orElseThrow();

        assertEquals(875, left.x());
        assertEquals(1, left.dir());

        MonsterMoveResult afterLeft = monster.patrolOrReturn().orElseThrow();
        assertEquals(879, afterLeft.x());
        assertEquals(1, afterLeft.dir());
    }

    @Test
    void patrolMovesInwardImmediatelyAfterChaseEndsAtBoundary() throws Exception {
        RuntimeMonster monster = map1Monster();

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);

        MonsterMoveResult chase = monster.moveToward(1971).orElseThrow();

        assertEquals(1075, chase.x());
        assertEquals(1, chase.dir());

        MonsterMoveResult patrol = monster.patrolOrReturn().orElseThrow();

        assertEquals(1071, patrol.x());
        assertEquals(936, patrol.y());
        assertEquals(-1, patrol.dir());

        setIntField(monster, "x", 879);
        setIntField(monster, "moveDir", -1);

        MonsterMoveResult chaseLeft = monster.moveToward(-21).orElseThrow();
        assertEquals(875, chaseLeft.x());
        assertEquals(-1, chaseLeft.dir());

        MonsterMoveResult patrolRight = monster.patrolOrReturn().orElseThrow();
        assertEquals(879, patrolRight.x());
        assertEquals(1, patrolRight.dir());
    }

    @Test
    void deadMonsterDoesNotMoveAndRespawnResetsDirection() throws Exception {
        RuntimeMonster monster = map1Monster();

        setIntField(monster, "x", 900);
        setIntField(monster, "moveDir", -1);
        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.patrolOrReturn().isEmpty());

        monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(975, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
        assertEquals(1, monster.moveDir());
    }

    @Test
    void lethalDamageRejectsRespawnDeadlineOverflow() {
        RuntimeMonster monster = map1Monster();

        assertThrows(ArithmeticException.class,
                () -> monster.applyDamage(7, 500, Long.MAX_VALUE - 10, 100));
    }

    @Test
    void successfulDamageRegistersAttackerAsEnemy() {
        RuntimeMonster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.hasEnemy(7));
        assertEquals(1, monster.enemyCount());
        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void repeatedDamageKeepsOneEnemyAndAccumulatesBookkeeping() {
        RuntimeMonster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();
        monster.applyDamage(7, 10, NOW + 1, RESPAWN_DELAY).orElseThrow();

        assertEquals(1, monster.enemyCount());
        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void removeEnemyRemovesOnlyRequestedPlayer() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();
        monster.applyDamage(8, 10, NOW + 1, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.removeEnemy(7));

        assertFalse(monster.hasEnemy(7));
        assertTrue(monster.hasEnemy(8));
        assertEquals(1, monster.enemyCount());
        assertEquals(List.of(8), monster.enemyPlayerIds());
    }

    @Test
    void removeMissingEnemyIsHarmless() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(8, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertFalse(monster.removeEnemy(7));

        assertEquals(List.of(8), monster.enemyPlayerIds());
    }

    @Test
    void rejectedOrDeadDamageDoesNotRegisterNewEnemy() {
        RuntimeMonster monster = map1Monster();

        assertTrue(monster.applyDamage(7, 0, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(7, -1, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).isPresent());
        assertTrue(monster.applyDamage(8, 10, NOW + 1, RESPAWN_DELAY).isEmpty());

        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertFalse(monster.beginAttackAttemptIfDue(NOW + 1));
    }

    @Test
    void cooldownUsesCanonicalFormulaAndStrictDueTiming() {
        RuntimeMonster monster = map1Monster();
        assertEquals(2_000L, monster.attackDelayMillis());

        for (int id = 1; id <= 5; id++) {
            monster.applyDamage(id, 1, NOW + id, RESPAWN_DELAY).orElseThrow();
            assertEquals(Math.max(2_000L - 400L * id, 500L), monster.attackDelayMillis());
        }

        assertTrue(monster.beginAttackAttemptIfDue(NOW + 6));
        assertFalse(monster.beginAttackAttemptIfDue(NOW + 7));
        assertFalse(monster.beginAttackAttemptIfDue(NOW + 506));
        assertTrue(monster.beginAttackAttemptIfDue(NOW + 507));
    }

    @Test
    void firstRetaliationIsEligibleOnNextTick() {
        RuntimeMonster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.beginAttackAttemptIfDue(NOW + 1));
    }

    @Test
    void respawnClearsEnemiesAndResetsAttackTiming() {
        RuntimeMonster monster = map1Monster();
        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();
        assertTrue(monster.hasEnemy(7));

        monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(0, monster.enemyCount());
        assertTrue(monster.enemyPlayerIds().isEmpty());
        assertFalse(monster.beginAttackAttemptIfDue(NOW + RESPAWN_DELAY + 2));

        monster.applyDamage(8, 10, NOW + RESPAWN_DELAY + 2, RESPAWN_DELAY).orElseThrow();
        assertTrue(monster.beginAttackAttemptIfDue(NOW + RESPAWN_DELAY + 3));
    }

    @Test
    void constructorRequiresMatchingPositiveCombatTemplate() {
        LegacyMonsterSpawn spawn = new LegacyMonsterSpawn(0, 1, 9, 2, 0,
                1, 2, 300, 300, 0);
        LegacyMonsterTemplate movement = map1Movement();

        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeMonster(spawn,
                        new LegacyMonsterCombatTemplate(2, 10, 0), movement));
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeMonster(spawn,
                        new LegacyMonsterCombatTemplate(1, 0, 0), movement));
        LegacyMonsterTemplate wrongMovement = new LegacyMonsterTemplate(
                2, "wrong", 100, 1, 1, 0,
                List.of(1), 2, 3, 10, 10, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeMonster(spawn,
                        new LegacyMonsterCombatTemplate(1, 10, 0), wrongMovement));
        assertThrows(IllegalArgumentException.class,
                () -> new LegacyMonsterCombatTemplate(1, 10L, -1L));
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

    private static LegacyMonsterTemplate map1Movement() {
        ResourceService resources = ResourceService.fromFrameRoot(Path.of("resources", "json"));
        return resources.monsterTemplates().getFirst();
    }
}

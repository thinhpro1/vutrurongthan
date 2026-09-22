package com.project.game.monster;

import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterTest {
    private static final long NOW = 1_000_000L;
    private static final long RESPAWN_DELAY = 9_000L;

    @Test
    void appliesNonLethalDamage() {
        Monster monster = map1Monster();

        Monster.Damage result = monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new Monster.Damage(101, 10, 290, false, 0L), result);
        assertFalse(result.killed());
        assertEquals(0L, result.potentialReward());
        assertTrue(monster.isAlive());
        assertEquals(290, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void clampsLethalDamageToZeroAndMarksDead() {
        Monster monster = map1Monster();

        Monster.Damage result = monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(0, result.hpAfter());
        assertTrue(result.killed());
        assertEquals(10L, result.potentialReward());
        assertFalse(monster.isAlive());
        assertEquals(0, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void deadMonsterRejectsFurtherDamageUntilRespawn() {
        Monster monster = map1Monster();
        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.applyDamage(8, 10, NOW + 1_000, RESPAWN_DELAY).isEmpty());
        assertEquals(0L, monster.snapshot().hp());
        assertEquals(1, monster.snapshot().status());
    }

    @Test
    void invalidDamageIsIgnored() {
        Monster monster = map1Monster();

        assertTrue(monster.applyDamage(7, 0, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(8, -1, NOW, RESPAWN_DELAY).isEmpty());
        assertEquals(300, monster.snapshot().hp());
    }

    @Test
    void nonLethalDamageDoesNotScheduleRespawn() {
        Monster monster = map1Monster();

        Monster.Damage result =
                monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertEquals(new Monster.Damage(101, 10, 290, false, 0L), result);
        assertFalse(result.killed());
        assertEquals(0L, result.potentialReward());
        assertTrue(monster.respawnIfDue(Long.MAX_VALUE).isEmpty());
        assertTrue(monster.isAlive());
        assertEquals(290L, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void lethalDamageUsesStrictGreaterThanRespawnDeadline() {
        Monster monster = map1Monster();

        Monster.Damage death =
                monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(death.killed());
        assertEquals(10L, death.potentialReward());
        assertEquals(0L, death.hpAfter());
        assertFalse(monster.isAlive());

        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY - 1).isEmpty());
        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY).isEmpty());

        Monster.Respawn respawn =
                monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(new Monster.Respawn(101, 0, 300L), respawn);
        assertTrue(monster.isAlive());
        assertEquals(300L, monster.snapshot().hp());
        assertEquals(0, monster.snapshot().status());
    }

    @Test
    void respawnTransitionOccursOnlyOnceAndKeepsRuntimeId() {
        Monster monster = map1Monster();
        int runtimeId = monster.id();

        monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).orElseThrow();

        Monster.Respawn first =
                monster.respawnIfDue(NOW + RESPAWN_DELAY + 1).orElseThrow();

        assertEquals(runtimeId, first.monsterId());
        assertEquals(runtimeId, monster.id());
        assertTrue(monster.respawnIfDue(NOW + RESPAWN_DELAY + 2).isEmpty());
        assertTrue(monster.isAlive());
    }

    @Test
    void respawnRestoresCanonicalSpawnCoordinates() throws Exception {
        Monster monster = map1Monster();

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
        Monster monster = map1Monster();

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
        Monster monster = map1Monster();

        Monster.Move move = monster.patrolOrReturn().orElseThrow();

        assertEquals(new Monster.Move(101, 979, 936, 1), move);
        assertEquals(979, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
    }

    @Test
    void patrolClampsAtRightBoundaryAndWalksBackTowardCorridor() throws Exception {
        Monster monster = map1Monster();

        setIntField(monster, "x", 1073);
        setIntField(monster, "moveDir", 1);

        Monster.Move boundary = monster.patrolOrReturn().orElseThrow();

        assertEquals(1075, boundary.x());
        assertEquals(-1, boundary.dir());

        setIntField(monster, "x", 1200);
        Monster.Move returning = monster.patrolOrReturn().orElseThrow();
        assertEquals(1196, returning.x());
        assertEquals(-1, returning.dir());
    }

    @Test
    void patrolFlipsDirectionWhenStepLandsExactlyOnBoundary() throws Exception {
        Monster monster = map1Monster();

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);

        Monster.Move right = monster.patrolOrReturn().orElseThrow();

        assertEquals(1075, right.x());
        assertEquals(-1, right.dir());

        Monster.Move afterRight = monster.patrolOrReturn().orElseThrow();
        assertEquals(1071, afterRight.x());
        assertEquals(-1, afterRight.dir());

        setIntField(monster, "x", 879);
        setIntField(monster, "moveDir", -1);

        Monster.Move left = monster.patrolOrReturn().orElseThrow();

        assertEquals(875, left.x());
        assertEquals(1, left.dir());

        Monster.Move afterLeft = monster.patrolOrReturn().orElseThrow();
        assertEquals(879, afterLeft.x());
        assertEquals(1, afterLeft.dir());
    }

    @Test
    void patrolMovesInwardImmediatelyAfterChaseEndsAtBoundary() throws Exception {
        Monster monster = map1Monster();

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);

        Monster.Move chase = monster.moveToward(1971).orElseThrow();

        assertEquals(1075, chase.x());
        assertEquals(1, chase.dir());

        Monster.Move patrol = monster.patrolOrReturn().orElseThrow();

        assertEquals(1071, patrol.x());
        assertEquals(936, patrol.y());
        assertEquals(-1, patrol.dir());

        setIntField(monster, "x", 879);
        setIntField(monster, "moveDir", -1);

        Monster.Move chaseLeft = monster.moveToward(-21).orElseThrow();
        assertEquals(875, chaseLeft.x());
        assertEquals(-1, chaseLeft.dir());

        Monster.Move patrolRight = monster.patrolOrReturn().orElseThrow();
        assertEquals(879, patrolRight.x());
        assertEquals(1, patrolRight.dir());
    }

    @Test
    void deadMonsterDoesNotMoveAndRespawnResetsDirection() throws Exception {
        Monster monster = map1Monster();

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
        Monster monster = map1Monster();

        assertThrows(ArithmeticException.class,
                () -> monster.applyDamage(7, 500, Long.MAX_VALUE - 10, 100));
    }

    @Test
    void successfulDamageRegistersAttackerAsEnemy() {
        Monster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.hasEnemy(7));
        assertEquals(1, monster.enemyCount());
        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void repeatedDamageKeepsOneEnemyAndAccumulatesBookkeeping() {
        Monster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();
        monster.applyDamage(7, 10, NOW + 1, RESPAWN_DELAY).orElseThrow();

        assertEquals(1, monster.enemyCount());
        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void removeEnemyRemovesOnlyRequestedPlayer() {
        Monster monster = map1Monster();
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
        Monster monster = map1Monster();
        monster.applyDamage(8, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertFalse(monster.removeEnemy(7));

        assertEquals(List.of(8), monster.enemyPlayerIds());
    }

    @Test
    void rejectedOrDeadDamageDoesNotRegisterNewEnemy() {
        Monster monster = map1Monster();

        assertTrue(monster.applyDamage(7, 0, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(7, -1, NOW, RESPAWN_DELAY).isEmpty());
        assertTrue(monster.applyDamage(7, 500, NOW, RESPAWN_DELAY).isPresent());
        assertTrue(monster.applyDamage(8, 10, NOW + 1, RESPAWN_DELAY).isEmpty());

        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertFalse(monster.beginAttackAttemptIfDue(NOW + 1));
    }

    @Test
    void cooldownUsesCanonicalFormulaAndStrictDueTiming() {
        Monster monster = map1Monster();
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
        Monster monster = map1Monster();

        monster.applyDamage(7, 10, NOW, RESPAWN_DELAY).orElseThrow();

        assertTrue(monster.beginAttackAttemptIfDue(NOW + 1));
    }

    @Test
    void respawnClearsEnemiesAndResetsAttackTiming() {
        Monster monster = map1Monster();
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
    void constructorRequiresMatchingPositiveTemplate() {
        MonsterSpawn spawn = new MonsterSpawn(0, 1, 9, 2, 0,
                1, 2, 300, 300, 0);
        MonsterTemplate movement = map1Movement();

        assertThrows(IllegalArgumentException.class,
                () -> new Monster(spawn, new MonsterTemplate(
                        2, "wrong", 2, 300L, 10L, 0L, 100, 1, 1, 0,
                        List.of(1), List.of(2), List.of(3), 10, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> new Monster(spawn, new MonsterTemplate(
                        1, "invalid", 2, 300L, 0L, 0L, 100, 1, 1, 0,
                        List.of(1), List.of(2), List.of(3), 10, 10)));
        MonsterTemplate wrongMovement = new MonsterTemplate(
                2, "wrong", 2, 300L, 10L, 0L, 100, 1, 1, 0,
                List.of(1), List.of(2), List.of(3), 10, 10);
        assertThrows(IllegalArgumentException.class,
                () -> new Monster(spawn, wrongMovement));
    }

    private static void setIntField(Monster monster, String fieldName, int value)
            throws Exception {
        var field = Monster.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(monster, value);
    }

    private static Monster map1Monster() {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        return new MonsterFactory(resources).createForMap(1).getFirst();
    }

    private static MonsterTemplate map1Movement() {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        return resources.monsterTemplates().getFirst();
    }
}

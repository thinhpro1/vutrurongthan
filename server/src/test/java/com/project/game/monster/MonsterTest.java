package com.project.game.monster;

import com.project.game.monster.Monster.Snapshot;
import com.project.game.player.Player;
import com.project.game.testsupport.TestPlayers;
import com.project.game.testsupport.TestZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Monster nằm trong một Zone thật; writer đang rảnh nên test gọi thẳng method của Monster. */
class MonsterTest {
    private static final long NOW = 1_000_000L;

    private TestZone area;
    private Monster monster;

    @BeforeEach
    void setUp() {
        area = new TestZone();
        monster = area.monster(101);
    }

    @Test
    void appliesDamageAndRegistersTheAttacker() {
        monster.injure(attacker(7), 10, NOW);

        assertTrue(monster.isAlive());
        assertEquals(290L, monster.hp());
        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertEquals(290L, monster.snapshot().hp());
    }

    @Test
    void ignoresInvalidDamageAndDamageAfterDeath() {
        Player first = attacker(7);
        monster.injure(first, 0, NOW);
        monster.injure(first, -1, NOW);
        assertEquals(300L, monster.hp());
        assertTrue(monster.enemyPlayerIds().isEmpty());

        monster.injure(first, 500, NOW);
        assertFalse(monster.isAlive());
        monster.injure(attacker(8), 10, NOW + 1);

        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertEquals(0L, monster.hp());
    }

    @Test
    void killerGetsRewardAndRespawnDelayUsesPlayersInZoneAtDeath() {
        Player killer = area.join(7, 975, 936);

        monster.injure(killer, 500, NOW);

        assertEquals(11L, killer.potential());
        assertFalse(monster.respawn(NOW + 9_000));
        assertTrue(monster.respawn(NOW + 9_001));
        assertTrue(monster.isAlive());
        assertEquals(300L, monster.hp());
    }

    @Test
    void clampsRespawnDelayAtFiveSecondsForLargeZones() {
        for (int id = 1; id <= 6; id++) {
            area.join(id, 975, 936);
        }

        monster.injure(attacker(7), 500, NOW);

        assertFalse(monster.respawn(NOW + 5_000));
        assertTrue(monster.respawn(NOW + 5_001));
    }

    @Test
    void rejectsRespawnDeadlineOverflowWithoutChangingState() {
        monster.injure(attacker(7), 10, NOW);
        Snapshot before = monster.snapshot();

        assertThrows(ArithmeticException.class,
                () -> monster.injure(attacker(8), 500, Long.MAX_VALUE - 10));

        assertEquals(before, monster.snapshot());
        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void respawnRestoresSpawnStateAndClearsCombatState() throws Exception {
        setIntField(monster, "x", 1075);
        setIntField(monster, "y", 1234);
        setIntField(monster, "moveDir", -1);
        monster.injure(attacker(7), 10, NOW);
        monster.injure(attacker(8), 500, NOW + 1);

        monster.update(NOW + 10_002, new Random(1L));
        assertTrue(monster.isAlive());

        assertEquals(975, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
        assertEquals(1, monster.moveDir());
        assertTrue(monster.enemyPlayerIds().isEmpty());
        assertFalse(monster.updateAttack(List.of(), NOW + 10_003, new Random(1L)));
    }

    @Test
    void patrolMovesByTheRunStepAndFlipsAtBoundaries() throws Exception {

        assertTrue(monster.updateMove(List.of()));
        assertEquals(979, monster.x());
        assertEquals(1, monster.moveDir());

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);
        assertTrue(monster.updateMove(List.of()));
        assertEquals(1075, monster.x());
        assertEquals(-1, monster.moveDir());
        assertTrue(monster.updateMove(List.of()));
        assertEquals(1071, monster.x());
        assertEquals(-1, monster.moveDir());
    }

    @Test
    void doesNotMoveWhenAHostilePlayerIsInStrictAttackRange() {

        assertFalse(monster.updateMove(List.of(playerAt(7, 975 + 899, 936))));
        assertTrue(monster.updateMove(List.of(playerAt(7, 975 + 900, 936))));
        assertEquals(979, monster.x());
    }

    @Test
    void stopsForAnInRangePlayerBeforeChoosingAChaseTarget() throws Exception {
        setIntField(monster, "x", 2300);
        Player chaseTarget = playerAt(7, 1975, 1936);
        Player inRangeBeyondSpawnLeash = playerAt(8, 2400, 936);

        assertFalse(monster.updateMove(List.of(chaseTarget, inRangeBeyondSpawnLeash)));
        assertEquals(2300, monster.x());
        assertFalse(monster.updateMove(List.of(inRangeBeyondSpawnLeash, chaseTarget)));
        assertEquals(2300, monster.x());
    }

    @Test
    void chasesTheNearestLeashedPlayerAndBreaksDistanceTiesByPlayerId() {
        Player lowerId = playerAt(7, -25, 936);
        Player higherId = playerAt(8, 1975, 936);

        assertTrue(monster.updateMove(List.of(higherId, lowerId)));
        assertEquals(971, monster.x());
        assertEquals(-1, monster.moveDir());
    }

    @Test
    void ignoresAHostilePlayerBeyondTheLeashAndPatrolsInstead() {

        assertTrue(monster.updateMove(List.of(playerAt(7, 975 + 1201, 936))));
        assertEquals(979, monster.x());
    }

    @Test
    void chaseNeverOvershootsTheTargetX() throws Exception {
        setIntField(monster, "x", 2000);

        assertTrue(monster.updateMove(List.of(playerAt(7, 2002, 1936))));
        assertEquals(2002, monster.x());
    }

    @Test
    void deadMonsterDoesNotMove() {
        monster.injure(attacker(7), 500, NOW);

        assertFalse(monster.updateMove(List.of()));
    }

    @Test
    void firstRetaliationIsEligibleOnTheNextTickAndCooldownIsStrict() {
        Player target = area.join(7, 975, 936);
        monster.injure(target, 10, NOW);

        monster.update(NOW + 1, new Random(1L));
        assertEquals(190, target.hp());
        assertFalse(monster.updateAttack(List.of(target), NOW + 1_601, new Random(1L)));
        assertTrue(monster.updateAttack(List.of(target), NOW + 1_602, new Random(1L)));
        assertEquals(180, target.hp());
    }

    @Test
    void failedAttackAttemptStillConsumesCooldown() {
        Player target = area.join(7, 975, 936);
        monster.injure(target, 10, NOW);

        assertFalse(monster.updateAttack(List.of(), NOW + 1, new Random(1L)));
        assertFalse(monster.updateAttack(List.of(target), NOW + 1_601, new Random(1L)));
        assertTrue(monster.updateAttack(List.of(target), NOW + 1_602, new Random(1L)));
        assertEquals(190, target.hp());
    }

    @Test
    void attackChoosesOnlyCurrentCandidatesAndClampsPlayerHpAtZero() {
        Player first = area.join(7, 975, 936);
        Player second = area.join(8, 975, 936);
        second.injure(195);
        monster.injure(first, 10, NOW);
        monster.injure(second, 10, NOW + 1);

        assertTrue(monster.updateAttack(List.of(first, second), NOW + 2, new Random(0L)));

        boolean hitFirst = first.hp() == 190;
        boolean hitSecond = second.hp() == 0;
        assertTrue(hitFirst != hitSecond);
        if (hitSecond) {
            assertTrue(second.isDead());
            assertFalse(monster.hasEnemy(8), "a killed Player is forgotten by every Monster");
            assertEquals(200, first.hp());
        }
    }

    @Test
    void removesOnlyTheRequestedEnemy() {
        monster.injure(attacker(7), 10, NOW);
        monster.injure(attacker(8), 10, NOW + 1);

        assertTrue(monster.removeEnemy(7));
        assertFalse(monster.hasEnemy(7));
        assertTrue(monster.hasEnemy(8));
        assertFalse(monster.removeEnemy(7));
    }

    @Test
    void repeatedHitsKeepEnemyOrderAndRemovalAdjustsRetaliationCooldown() {
        Player first = area.join(8, 975, 936);
        Player second = area.join(7, 975, 936);
        FirstTargetRandom random = new FirstTargetRandom();
        monster.injure(first, 10, NOW);
        monster.injure(second, 10, NOW + 1);
        monster.injure(first, 10, NOW + 2);

        assertEquals(List.of(8, 7), monster.enemyPlayerIds());
        monster.update(NOW + 3, random);
        assertEquals(190, first.hp());
        monster.update(NOW + 1_203, random);
        assertEquals(190, first.hp());

        assertTrue(monster.removeEnemy(8));
        assertEquals(List.of(7), monster.enemyPlayerIds());
        monster.update(NOW + 1_204, random);
        monster.update(NOW + 1_603, random);
        assertEquals(200, second.hp());
        monster.update(NOW + 1_604, random);
        assertEquals(190, second.hp());
        assertEquals(List.of(2, 1), random.bounds);
    }

    private static final class FirstTargetRandom extends Random {
        private final List<Integer> bounds = new ArrayList<>();

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return 0;
        }
    }

    /** Player chưa vào Zone, chỉ dùng làm người đánh. */
    private static Player attacker(int id) {
        return playerAt(id, 975, 936);
    }

    private static Player playerAt(int id, int x, int y) {
        return TestPlayers.at(TestPlayers.initial((long) id, id, "player" + id, 1), 1, 0, x, y);
    }

    private static void setIntField(Monster monster, String fieldName, int value)
            throws Exception {
        var field = Monster.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(monster, value);
    }
}

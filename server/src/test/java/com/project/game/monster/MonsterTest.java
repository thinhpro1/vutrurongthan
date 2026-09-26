package com.project.game.monster;

import com.project.game.player.Player;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.TestPlayers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterTest {
    private static final long NOW = 1_000_000L;

    @Test
    void appliesDamageAndRegistersTheAttacker() {
        Monster monster = map1Monster();

        Monster.Damage result = monster.injure(7, 10, NOW, 0);

        assertEquals(new Monster.Damage(101, 10, 290, false, 0L), result);
        assertTrue(monster.isAlive());
        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertEquals(290L, monster.snapshot().hp());
    }

    @Test
    void ignoresInvalidDamageAndDamageAfterDeath() {
        Monster monster = map1Monster();

        assertNull(monster.injure(7, 0, NOW, 0));
        assertNull(monster.injure(7, -1, NOW, 0));
        assertTrue(monster.injure(7, 500, NOW, 0).killed());
        assertNull(monster.injure(8, 10, NOW + 1, 0));

        assertEquals(List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void capturesRespawnDelayFromDeathTimeMembership() {
        Monster monster = map1Monster();

        Monster.Damage death = monster.injure(7, 500, NOW, 1);

        assertTrue(death.killed());
        assertEquals(10L, death.potentialReward());
        assertNull(monster.updateRespawn(NOW + 9_000));
        assertEquals(new Monster.Respawn(101, 0, 300L),
                monster.updateRespawn(NOW + 9_001));
    }

    @Test
    void clampsRespawnDelayAtFiveSecondsForLargeZones() {
        Monster monster = map1Monster();

        monster.injure(7, 500, NOW, 6);

        assertNull(monster.updateRespawn(NOW + 5_000));
        assertEquals(new Monster.Respawn(101, 0, 300L),
                monster.updateRespawn(NOW + 5_001));
    }

    @Test
    void rejectsRespawnDeadlineOverflowAndNegativePlayerCount() {
        Monster monster = map1Monster();

        assertThrows(IllegalArgumentException.class, () -> monster.injure(7, 10, NOW, -1));
        assertThrows(ArithmeticException.class,
                () -> monster.injure(7, 500, Long.MAX_VALUE - 10, 0));
    }

    @Test
    void respawnRestoresSpawnStateAndClearsCombatState() throws Exception {
        Monster monster = map1Monster();
        setIntField(monster, "x", 1075);
        setIntField(monster, "y", 1234);
        setIntField(monster, "moveDir", -1);
        monster.injure(7, 10, NOW, 0);
        monster.injure(8, 500, NOW + 1, 0);

        assertEquals(new Monster.Respawn(101, 0, 300L),
                monster.updateRespawn(NOW + 10_002));

        assertEquals(975, monster.snapshot().x());
        assertEquals(936, monster.snapshot().y());
        assertEquals(1, monster.moveDir());
        assertTrue(monster.enemyPlayerIds().isEmpty());
        assertNull(monster.updateAttack(List.of(), NOW + 10_003, new Random(1L)));
    }

    @Test
    void patrolMovesByTheRunStepAndFlipsAtBoundaries() throws Exception {
        Monster monster = map1Monster();

        assertEquals(new Monster.Move(101, 979, 936, 1), monster.updateMove(List.of()));

        setIntField(monster, "x", 1071);
        setIntField(monster, "moveDir", 1);
        assertEquals(new Monster.Move(101, 1075, 936, -1), monster.updateMove(List.of()));
        assertEquals(new Monster.Move(101, 1071, 936, -1), monster.updateMove(List.of()));
    }

    @Test
    void doesNotMoveWhenAHostilePlayerIsInStrictAttackRange() {
        Monster monster = map1Monster();

        assertNull(monster.updateMove(List.of(playerAt(7, 975 + 899, 936))));
        assertEquals(new Monster.Move(101, 979, 936, 1),
                monster.updateMove(List.of(playerAt(7, 975 + 900, 936))));
    }

    @Test
    void chasesTheNearestLeashedPlayerAndBreaksDistanceTiesByPlayerId() {
        Monster monster = map1Monster();
        Player lowerId = playerAt(7, -25, 936);
        Player higherId = playerAt(8, 1975, 936);

        Monster.Move move = monster.updateMove(List.of(higherId, lowerId));

        assertEquals(new Monster.Move(101, 971, 936, -1), move);
    }

    @Test
    void ignoresAHostilePlayerBeyondTheLeashAndPatrolsInstead() {
        Monster monster = map1Monster();

        assertEquals(new Monster.Move(101, 979, 936, 1),
                monster.updateMove(List.of(playerAt(7, 975 + 1201, 936))));
    }

    @Test
    void chaseNeverOvershootsTheTargetX() throws Exception {
        Monster monster = map1Monster();
        setIntField(monster, "x", 2000);

        Monster.Move move = monster.updateMove(List.of(playerAt(7, 2002, 1936)));

        assertEquals(new Monster.Move(101, 2002, 936, 1), move);
    }

    @Test
    void deadMonsterDoesNotMove() {
        Monster monster = map1Monster();
        monster.injure(7, 500, NOW, 0);

        assertNull(monster.updateMove(List.of()));
    }

    @Test
    void firstRetaliationIsEligibleOnTheNextTickAndCooldownIsStrict() {
        Monster monster = map1Monster();
        Player target = playerAt(7, 975, 936);
        monster.injure(7, 10, NOW, 0);

        assertEquals(new Monster.Attack(101, 7, 10, 190, false),
                monster.updateAttack(List.of(target), NOW + 1, new Random(1L)));
        assertNull(monster.updateAttack(List.of(target), NOW + 1_601, new Random(1L)));
        assertEquals(new Monster.Attack(101, 7, 10, 180, false),
                monster.updateAttack(List.of(target), NOW + 1_602, new Random(1L)));
    }

    @Test
    void failedAttackAttemptStillConsumesCooldown() {
        Monster monster = map1Monster();
        Player target = playerAt(7, 975, 936);
        monster.injure(7, 10, NOW, 0);

        assertNull(monster.updateAttack(List.of(), NOW + 1, new Random(1L)));
        assertNull(monster.updateAttack(List.of(target), NOW + 1_601, new Random(1L)));
        assertEquals(new Monster.Attack(101, 7, 10, 190, false),
                monster.updateAttack(List.of(target), NOW + 1_602, new Random(1L)));
    }

    @Test
    void attackChoosesOnlyCurrentCandidatesAndClampsPlayerHpAtZero() {
        Monster monster = map1Monster();
        Player first = playerAt(7, 975, 936);
        Player second = playerAt(8, 975, 936);
        second.injure(195);
        monster.injure(7, 10, NOW, 0);
        monster.injure(8, 10, NOW + 1, 0);

        Monster.Attack attack = monster.updateAttack(List.of(first, second), NOW + 2, new Random(0L));

        assertTrue(attack.playerId() == 7 || attack.playerId() == 8);
        if (attack.playerId() == 8) {
            assertEquals(0L, attack.hpAfter());
            assertTrue(attack.killed());
        }
        assertEquals(attack.playerId() == 7 ? 190L : 200L, first.hp());
    }

    @Test
    void removesOnlyTheRequestedEnemy() {
        Monster monster = map1Monster();
        monster.injure(7, 10, NOW, 0);
        monster.injure(8, 10, NOW + 1, 0);

        assertTrue(monster.removeEnemy(7));
        assertFalse(monster.hasEnemy(7));
        assertTrue(monster.hasEnemy(8));
        assertFalse(monster.removeEnemy(7));
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

    private static Monster map1Monster() {
        GameResources resources = GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(),
                2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository());
        return new MonsterFactory(resources).createForMap(1).getFirst();
    }
}

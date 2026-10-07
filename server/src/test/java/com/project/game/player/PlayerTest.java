package com.project.game.player;

import com.project.game.monster.Monster;
import com.project.game.monster.MonsterManager;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTest {
    @Test
    void freshPlayerUsesJavaInitializationDefaults() {
        Player player = Player.createWithId(1, 101L, "Alpha1", 0);

        assertEquals(101L, player.accountId());
        assertEquals("alpha1", player.name());
        assertEquals(0, player.mapId());
        assertEquals(0, player.zoneId());
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
        assertEquals(200, player.baseStats().hp());
        assertEquals(200, player.currentStats().maxHp());
        assertEquals(200, player.hp());
        assertEquals(12, player.currentStats().speed());
    }

    @Test
    void moveMutatesSamePlayerAndPreservesOtherState() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        player.changeMap(1, 2, 90, 1008);
        player.injure(77);
        long potential = player.addPotential(99);

        assertTrue(player.move(1337, 611));
        assertEquals(1337, player.x());
        assertEquals(611, player.y());
        assertEquals(1, player.mapId());
        assertEquals(2, player.zoneId());
        assertEquals(123, player.hp());
        assertEquals(potential, player.potential());
    }

    @Test
    void deadPlayerCannotMove() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        player.injure(Long.MAX_VALUE);

        assertFalse(player.move(1, 2));
        assertEquals(1250, player.x());
        assertEquals(648, player.y());
    }

    @Test
    void injureClampsHpAtZeroAndRejectsNegativeDamage() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);

        assertEquals(150, player.injure(50));
        assertEquals(0, player.injure(Long.MAX_VALUE));
        assertTrue(player.isDead());
        assertThrows(IllegalArgumentException.class, () -> player.injure(-1));
    }

    @Test
    void reviveRestoresVitalsAndChangesOnlyLocation() {
        Player player = Player.createWithId(7, 101L, "reviver", 0);
        player.injure(Long.MAX_VALUE);
        player.addPotential(123);
        long potential = player.potential();
        Player.Appearance appearance = player.appearance();
        Player.BaseStats baseStats = player.baseStats();

        player.revive(1, 2, 333, 444);

        assertEquals(200, player.hp());
        assertEquals(200, player.mp());
        assertEquals(1, player.mapId());
        assertEquals(2, player.zoneId());
        assertEquals(333, player.x());
        assertEquals(444, player.y());
        assertEquals(potential, player.potential());
        assertSame(appearance, player.appearance());
        assertSame(baseStats, player.baseStats());
    }

    @Test
    void changeMapChangesOnlyLocation() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        int hp = player.hp();
        long power = player.power();

        player.changeMap(3, 4, 5, 6);

        assertEquals(3, player.mapId());
        assertEquals(4, player.zoneId());
        assertEquals(5, player.x());
        assertEquals(6, player.y());
        assertEquals(hp, player.hp());
        assertEquals(power, player.power());
    }

    @Test
    void addPotentialSaturatesWithoutChangingPower() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        long power = player.power();
        player.addPotential(Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, player.potential());
        assertEquals(power, player.power());
        assertThrows(IllegalArgumentException.class, () -> player.addPotential(-1));
    }

    @Test
    void saveDataCaptureIsStableAfterPlayerMutation() {
        Player player = Player.createWithId(1, 101L, "alpha1", 0);
        PlayerSaveData saved = PlayerSaveData.capture(player);

        player.move(1, 2);
        player.injure(50);
        player.addPotential(20);

        assertEquals(0, saved.mapId());
        assertEquals(1250, saved.x());
        assertEquals(648, saved.y());
        assertEquals(200, saved.hp());
        assertEquals(1, saved.potential());
        assertNotSame(player, saved);
    }

    @Test
    void canTargetRejectsNullMonster() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);

        assertFalse(player.canTarget(null));
        assertNull(player.attackMonster(null, 1_000_000L, 1));
    }

    @Test
    void deadMonsterCannotBeTargetedOrAttacked() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        Monster monster = map1Monster();
        monster.injure(8, 300L, 1_000_000L, 1);

        assertFalse(player.canTarget(monster));
        assertNull(player.attackMonster(monster, 1_000_001L, 1));
        assertEquals(1L, player.potential());
    }

    @Test
    void deadPlayerCannotTargetOrAttackLiveMonster() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        Monster monster = map1Monster();
        player.injure(200L);

        assertFalse(player.canTarget(monster));
        assertNull(player.attackMonster(monster, 1_000_000L, 1));
        assertEquals(300L, monster.hp());
        assertEquals(1L, player.potential());
    }

    @Test
    void zeroDamageCanTargetButCannotAttack() {
        Player player = playerWithDamage(0);
        Monster monster = map1Monster();

        assertTrue(player.canTarget(monster));
        assertNull(player.attackMonster(monster, 1_000_000L, 1));
        assertEquals(300L, monster.hp());
        assertEquals(1L, player.potential());
    }

    @Test
    void attackUsesPlayerDamageWithoutRewardingNonlethalHit() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        Monster monster = map1Monster();

        assertTrue(player.canTarget(monster));
        assertEquals(new Monster.Damage(101, 10L, 290L, false, 0L),
                player.attackMonster(monster, 1_000_000L, 1));
        assertEquals(1L, player.potential());
        assertEquals(java.util.List.of(7), monster.enemyPlayerIds());
    }

    @Test
    void killingHitRewardsPlayerExactlyOnce() {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        Monster monster = map1Monster();
        for (int hit = 0; hit < 29; hit++) {
            assertFalse(player.attackMonster(monster, 1_000_000L, 1).killed());
            assertEquals(1L, player.potential());
        }

        assertEquals(new Monster.Damage(101, 10L, 0L, true, 10L),
                player.attackMonster(monster, 1_000_000L, 1));
        assertEquals(11L, player.potential());
        assertNull(player.attackMonster(monster, 1_000_001L, 1));
        assertEquals(11L, player.potential());
    }

    @Test
    void lethalRewardSaturatesPotential() {
        Player player = playerWithDamage(300);
        Monster monster = map1Monster();
        player.addPotential(Long.MAX_VALUE - 5L - player.potential());

        assertTrue(player.attackMonster(monster, 1_000_000L, 1).killed());
        assertEquals(Long.MAX_VALUE, player.potential());
    }

    private static Monster map1Monster() {
        MonsterManager monsters = new MonsterManager(GameResources.fromFrameRoot(
                Path.of("resources", "json"),
                com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
        return monsters.createForMap(1).getFirst();
    }

    private static Player playerWithDamage(int damage) {
        Player player = Player.createWithId(7, 101L, "alpha1", 0);
        Player.CurrentStats stats = player.currentStats();
        Player.CurrentStats current = new Player.CurrentStats(
                stats.maxHp(), stats.maxMp(), damage, stats.armor(), stats.critical(),
                stats.dodge(), stats.constitution(), stats.speed());
        return new Player(player.id(), player.accountId(), player.name(), player.gender(),
                player.power(), player.potential(), player.level(), player.exp(),
                player.baseStats(), current, player.hp(), player.mp(), player.appearance(),
                player.coin(), player.coinLock(), player.diamond(), player.ruby(),
                player.mapId(), player.zoneId(), player.x(), player.y());
    }
}

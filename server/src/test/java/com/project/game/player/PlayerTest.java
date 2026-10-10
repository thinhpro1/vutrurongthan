package com.project.game.player;

import com.project.game.monster.Monster;
import com.project.game.network.message.MessageName;
import com.project.game.testsupport.TestPlayers;
import com.project.game.testsupport.TestZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void moveMutatesSamePlayerAndTellsOthers() {
        TestZone area = new TestZone();
        Player player = area.join(7, 90, 1008);
        Player observer = area.join(8, 90, 1008);
        area.commands(player);
        player.injure(77);
        long potential = player.addPotential(99);

        assertTrue(area.run(player, () -> player.move(1337, 611)));
        assertEquals(1337, player.x());
        assertEquals(611, player.y());
        assertEquals(1, player.mapId());
        assertEquals(0, player.zoneId());
        assertEquals(123, player.hp());
        assertEquals(potential, player.potential());
        assertEquals(List.of(), area.commands(player));
        assertEquals(List.of(MessageName.PLAYER_MOVE), area.commands(observer));
    }

    @Test
    void deadPlayerCannotMove() {
        TestZone area = new TestZone();
        Player player = area.join(1, 90, 1008);
        player.injure(Long.MAX_VALUE);

        assertFalse(area.run(player, () -> player.move(1, 2)));
        assertEquals(90, player.x());
        assertEquals(1008, player.y());
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

        player.changeMap(0, 0, 1, 2);
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
    }

    @Test
    void deadMonsterCannotBeTargetedOrAttacked() {
        TestZone area = new TestZone();
        Player killer = area.join(withDamage(TestPlayers.at(
                TestPlayers.initial(8L, 8, "player8", 1), 1, 0, 975, 936), 300));
        Player player = area.join(7, 975, 936);
        Monster monster = area.monster(101);
        assertTrue(area.run(killer, () -> killer.attackMonster(monster, 1_000_000L)));

        assertFalse(player.canTarget(monster));
        assertFalse(area.run(player, () -> player.attackMonster(monster, 1_000_001L)));
        assertEquals(1L, player.potential());
    }

    @Test
    void deadPlayerCannotTargetOrAttackLiveMonster() {
        TestZone area = new TestZone();
        Player player = area.join(7, 975, 936);
        Monster monster = area.monster(101);
        player.injure(200L);

        assertFalse(player.canTarget(monster));
        assertFalse(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
        assertEquals(300L, monster.hp());
        assertEquals(1L, player.potential());
    }

    @Test
    void zeroDamageCanTargetButCannotAttack() {
        TestZone area = new TestZone();
        Player player = area.join(playerWithDamage(0));
        Monster monster = area.monster(101);

        assertTrue(player.canTarget(monster));
        assertFalse(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
        assertEquals(300L, monster.hp());
        assertEquals(1L, player.potential());
    }

    @Test
    void attackUsesPlayerDamageWithoutRewardingNonlethalHit() {
        TestZone area = new TestZone();
        Player player = area.join(7, 975, 936);
        Monster monster = area.monster(101);

        assertTrue(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
        assertEquals(290L, monster.hp());
        assertEquals(1L, player.potential());
        assertEquals(List.of(7), monster.enemyPlayerIds());
        assertEquals(List.of(MessageName.MONSTER_INJURE), area.commands(player));
    }

    @Test
    void killingHitRewardsPlayerExactlyOnce() {
        TestZone area = new TestZone();
        Player player = area.join(7, 975, 936);
        Monster monster = area.monster(101);
        for (int hit = 0; hit < 29; hit++) {
            assertTrue(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
            assertTrue(monster.isAlive());
            assertEquals(1L, player.potential());
            area.commands(player);
        }

        assertTrue(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
        assertFalse(monster.isAlive());
        assertEquals(11L, player.potential());
        assertEquals(List.of(MessageName.MONSTER_START_DIE, MessageName.PLAYER_INFO),
                area.commands(player));
        assertFalse(area.run(player, () -> player.attackMonster(monster, 1_000_001L)));
        assertEquals(11L, player.potential());
    }

    @Test
    void lethalRewardSaturatesPotential() {
        TestZone area = new TestZone();
        Player player = area.join(playerWithDamage(300));
        Monster monster = area.monster(101);
        player.addPotential(Long.MAX_VALUE - 5L - player.potential());

        assertTrue(area.run(player, () -> player.attackMonster(monster, 1_000_000L)));
        assertFalse(monster.isAlive());
        assertEquals(Long.MAX_VALUE, player.potential());
    }

    @Test
    void attackHitsOnlyTheMonsterChosenByUseSkill() {
        TestZone area = new TestZone();
        Player player = area.join(7, 975, 936);
        Monster monster = area.monster(101);

        assertFalse(area.run(player, () -> player.attack(101, 1_000_000L)));
        assertTrue(area.run(player, () -> player.useSkill(0, 101)));
        assertFalse(area.run(player, () -> player.attack(102, 1_000_000L)));
        assertFalse(area.run(player, () -> player.attack(101, 1_000_000L)));
        assertEquals(300L, monster.hp());

        assertTrue(area.run(player, () -> player.useSkill(0, 101)));
        assertTrue(area.run(player, () -> player.attack(101, 1_000_000L)));
        assertFalse(area.run(player, () -> player.attack(101, 1_000_000L)));
        assertEquals(290L, monster.hp());
    }

    @Test
    void changingMapForgetsChosenMonster() {
        TestZone area = new TestZone();
        Player player = area.join(7, 975, 936);

        assertTrue(area.run(player, () -> player.useSkill(0, 101)));
        area.runOnWriter(() -> player.changeMap(1, 0, 975, 936));

        assertFalse(area.run(player, () -> player.attack(101, 1_000_000L)));
        assertEquals(300L, area.monster(101).hp());
    }

    private static Player playerWithDamage(int damage) {
        return withDamage(TestPlayers.at(
                TestPlayers.initial(7L, 7, "alpha1", 1), 1, 0, 975, 936), damage);
    }

    private static Player withDamage(Player player, int damage) {
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

package com.project.game.map;
import com.project.game.testsupport.TestPlayerProfiles;

import com.project.game.testsupport.TestServices;

import com.project.game.monster.MonsterFactory;
import com.project.game.monster.MonsterAttack;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.monster.Monster;
import com.project.game.network.ClientConfig;
import com.project.game.network.Session;
import com.project.game.network.SessionManager;
import com.project.game.network.SessionState;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.transport.ClientTransport;
import com.project.game.player.PlayerProfile;
import com.project.game.resource.GameResources;
import com.project.game.network.SessionServices;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneMonsterCombatTest {
    private static final long NOW = 1_000_000L;

    @Test
    void ownsLiveMonsterDamageAndDeathState() {
        Zone zone = map1Zone();

        assertTrue(zone.hasLiveMonster(101));
        assertFalse(zone.hasLiveMonster(99));

        var nonLethal = zone.damageMonster(101, 7, 10, NOW).orElseThrow();
        assertEquals(101, nonLethal.monsterId());
        assertEquals(10L, nonLethal.damage());
        assertEquals(290L, nonLethal.hpAfter());
        assertFalse(nonLethal.killed());
        assertEquals(290L, zone.monsterSnapshots().getFirst().hp());
        assertEquals(300L, zone.monsterSnapshots().get(1).hp());
        assertEquals(List.of(101, 102, 103, 104, 105, 106),
                zone.monsterSnapshots().stream().map(MonsterSnapshot::id).toList());

        var lethal = zone.damageMonster(101, 7, 300, NOW + 1).orElseThrow();
        assertTrue(lethal.killed());
        assertEquals(0L, lethal.hpAfter());
        assertFalse(zone.hasLiveMonster(101));
        assertTrue(zone.damageMonster(101, 8, 10, NOW + 2).isEmpty());
    }

    @Test
    void zoneOwnsOrderedMonsterSnapshots() {
        Zone zone = map1Zone();

        List<MonsterSnapshot> snapshots = zone.monsterSnapshots();
        assertEquals(6, snapshots.size());
        assertEquals(
                List.of(101, 102, 103, 104, 105, 106),
                snapshots.stream().map(MonsterSnapshot::id).toList());
        assertEquals(
                List.of(975, 1348, 1800, 2250, 2600, 2950),
                snapshots.stream().map(MonsterSnapshot::x).toList());
    }

    @Test
    void zoneRejectsDuplicateMonsterRuntimeIds() {
        MonsterFactory factory = new MonsterFactory(
                GameResources.fromFrameRoot(Path.of("resources", "json"),
                        com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                        com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
        List<Monster> runtimes = factory.createForMap(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Zone(
                        1,
                        0,
                        List.of(runtimes.getFirst(), runtimes.getFirst())));
    }

    @Test
    void containsRequiresExactSessionIdentity() {
        Zone zone = new Zone(1, 0, List.of());
        Session first = session(TestPlayerProfiles.initial(1L, 7, "alpha1", 1));
        Session equivalent = session(TestPlayerProfiles.initial(2L, 7, "alpha2", 1));

        zone.add(first);

        assertTrue(zone.contains(first));
        assertFalse(zone.contains(equivalent));
        assertFalse(zone.contains(null));
    }

    @Test
    void computesCanonicalRespawnDelayFromDeathTimeMembership() {
        assertEquals(10_000L, Zone.respawnDelayMillis(0));
        assertEquals(9_000L, Zone.respawnDelayMillis(1));
        assertEquals(8_000L, Zone.respawnDelayMillis(2));
        assertEquals(7_000L, Zone.respawnDelayMillis(3));
        assertEquals(6_000L, Zone.respawnDelayMillis(4));
        assertEquals(5_000L, Zone.respawnDelayMillis(5));
        assertEquals(5_000L, Zone.respawnDelayMillis(6));
        assertEquals(5_000L, Zone.respawnDelayMillis(Integer.MAX_VALUE));
    }

    @Test
    void rejectsNegativeRespawnPlayerCount() {
        assertThrows(IllegalArgumentException.class, () -> Zone.respawnDelayMillis(-1));
    }

    @Test
    void oneMemberDeathRespawnsOnlyAfterNineSecondDeadline() {
        Zone zone = map1Zone();
        Session player = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 1));
        zone.add(player);

        zone.damageMonster(101, 1, 500, NOW).orElseThrow();

        assertTrue(zone.respawnDueMonsters(NOW + 8_999).isEmpty());
        assertTrue(zone.respawnDueMonsters(NOW + 9_000).isEmpty());

        var due = zone.respawnDueMonsters(NOW + 9_001);

        assertEquals(List.of(new Monster.Respawn(101, 0, 300L)), due);
        assertTrue(zone.hasLiveMonster(101));
    }

    @Test
    void respawnDeadlineDoesNotChangeWhenMembershipChangesAfterDeath() {
        Zone zone = map1Zone();
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 1));
        Session second = session(TestPlayerProfiles.initial(2L, 2, "beta22", 1));

        zone.add(first);
        zone.add(second);

        zone.damageMonster(101, 1, 500, NOW).orElseThrow();
        zone.remove(second);

        assertTrue(zone.respawnDueMonsters(NOW + 8_000).isEmpty());
        assertEquals(List.of(new Monster.Respawn(101, 0, 300L)),
                zone.respawnDueMonsters(NOW + 8_001));
    }

    @Test
    void joinsAfterDeathDoNotShortenExistingRespawnDeadline() {
        Zone zone = map1Zone();
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 1));
        zone.add(first);

        zone.damageMonster(101, 1, 500, NOW).orElseThrow();

        for (int id = 2; id <= 6; id++) {
            zone.add(session(TestPlayerProfiles.initial(
                    (long) id, id, "player" + id, 1)));
        }

        assertTrue(zone.respawnDueMonsters(NOW + 5_001).isEmpty());
        assertTrue(zone.respawnDueMonsters(NOW + 9_000).isEmpty());
        assertEquals(List.of(new Monster.Respawn(101, 0, 300L)),
                zone.respawnDueMonsters(NOW + 9_001));
    }

    @Test
    void returnsMultipleDueRespawnsOnceInRuntimeOrder() {
        Zone zone = map1Zone();
        Session first = session(TestPlayerProfiles.initial(1L, 1, "alpha1", 1));
        zone.add(first);

        zone.damageMonster(101, 1, 500, NOW).orElseThrow();
        zone.damageMonster(102, 1, 500, NOW).orElseThrow();

        assertEquals(List.of(
                        new Monster.Respawn(101, 0, 300L),
                        new Monster.Respawn(102, 0, 300L)),
                zone.respawnDueMonsters(NOW + 9_001));
        assertTrue(zone.respawnDueMonsters(NOW + 9_002).isEmpty());
    }

    @Test
    void noHostilityProducesNoAttack() {
        Zone zone = map1Zone();
        Session player = playerAt(7, 975, 936);
        zone.add(player);

        assertTrue(zone.attackDueMonsters(NOW, new Random(12345L)).isEmpty());
        assertEquals(100L, player.player().hp());
    }

    @Test
    void successfulHitEnablesOneNonLethalRetaliationAndUpdatesAuthoritativeHp() {
        Zone zone = map1Zone();
        Session player = playerAt(7, 975, 936);
        zone.add(player);

        zone.damageMonster(101, 7, 10, NOW).orElseThrow();

        assertEquals(List.of(new MonsterAttack(101, 7, 10L, 90L, false)),
                zone.attackDueMonsters(NOW + 1, new Random(12345L)));
        assertEquals(90L, player.player().hp());
    }

    @Test
    void attackRangeIsStrictlyLessThanNineHundred() {
        assertEquals(90L, attackAtX(975 + 899).hp());
        assertEquals(100L, attackAtX(975 + 900).hp());
        assertEquals(100L, attackAtX(975 + 901).hp());
    }

    @Test
    void attackAllowsExactLethalMonsterDamage() {
        Zone allowed = map1Zone();
        Session twenty = playerAt(20, 975, 936);
        twenty.bindPlayer(twenty.player().withHp(20));
        allowed.add(twenty);
        allowed.damageMonster(101, 20, 10, NOW).orElseThrow();
        assertEquals(10L, allowed.attackDueMonsters(NOW + 1, new Random(1L))
                .getFirst().hpAfter());

        Zone lethal = map1Zone();
        Session ten = playerAt(10, 975, 936);
        ten.bindPlayer(ten.player().withHp(10));
        lethal.add(ten);
        lethal.damageMonster(101, 10, 10, NOW).orElseThrow();
        assertEquals(List.of(new MonsterAttack(101, 10, 10L, 0L, true)),
                lethal.attackDueMonsters(NOW + 1, new Random(1L)));
        assertEquals(0L, ten.player().hp());
    }

    @Test
    void closedOrRemovedEnemyIsNotTargeted() {
        Zone closed = map1Zone();
        Session closedPlayer = playerAt(7, 975, 936);
        closed.add(closedPlayer);
        closed.damageMonster(101, 7, 10, NOW).orElseThrow();
        closedPlayer.transition(SessionState.CONNECTED, SessionState.CLOSED);
        assertTrue(closed.attackDueMonsters(NOW + 1, new Random(1L)).isEmpty());

        Zone removed = map1Zone();
        Session removedPlayer = playerAt(8, 975, 936);
        removed.add(removedPlayer);
        removed.damageMonster(101, 8, 10, NOW).orElseThrow();
        removed.remove(removedPlayer);
        assertTrue(removed.attackDueMonsters(NOW + 1, new Random(1L)).isEmpty());
    }

    @Test
    void failedTargetAttemptConsumesCooldown() {
        Zone zone = map1Zone();
        Session player = playerAt(7, 975 + 901, 936);
        zone.add(player);
        zone.damageMonster(101, 7, 10, NOW).orElseThrow();

        assertTrue(zone.attackDueMonsters(NOW + 1, new Random(1L)).isEmpty());
        player.bindPlayer(player.player().withPosition(975, 936));
        assertTrue(zone.attackDueMonsters(NOW + 1_601, new Random(1L)).isEmpty());
        assertEquals(90L, zone.attackDueMonsters(NOW + 1_602, new Random(1L))
                .getFirst().hpAfter());
    }

    @Test
    void deterministicSelectionAmongTwoEnemies() {
        Zone zone = map1Zone();
        Session first = playerAt(7, 975, 936);
        Session second = playerAt(8, 975, 936);
        zone.add(first);
        zone.add(second);
        zone.damageMonster(101, 7, 10, NOW).orElseThrow();
        zone.damageMonster(101, 8, 10, NOW + 1).orElseThrow();

        List<MonsterAttack> attacks = zone.attackDueMonsters(NOW + 2, new Random(12345L));

        assertEquals(1, attacks.size());
        assertTrue(attacks.getFirst().playerId() == 7 || attacks.getFirst().playerId() == 8);
        assertEquals(90L, attacks.getFirst().playerId() == 7 ? first.player().hp() : second.player().hp());
    }

    @Test
    void deadMonsterCannotAttackAndRespawnClearsHostility() {
        Zone zone = map1Zone();
        Session player = playerAt(7, 975, 936);
        zone.add(player);
        zone.damageMonster(101, 7, 300, NOW).orElseThrow();
        assertTrue(zone.attackDueMonsters(NOW + 1, new Random(1L)).isEmpty());

        zone.respawnDueMonsters(NOW + 9_001);
        assertTrue(zone.attackDueMonsters(NOW + 9_002, new Random(1L)).isEmpty());
    }

    private static PlayerProfile attackAtX(int x) {
        Zone zone = map1Zone();
        Session player = playerAt(7, x, 936);
        zone.add(player);
        zone.damageMonster(101, 7, 10, NOW).orElseThrow();
        List<MonsterAttack> attacks = zone.attackDueMonsters(NOW + 1, new Random(1L));
        return player.player();
    }

    private static Session playerAt(int id, int x, int y) {
        return session(TestPlayerProfiles.initial((long) id, id, "player" + id, 1)
                .withLocation(1, 0, x, y)
                .withHp(100));
    }

    private static Zone map1Zone() {
        MonsterFactory factory = new MonsterFactory(
                GameResources.fromFrameRoot(Path.of("resources", "json"),
                        com.project.game.testsupport.MapTestSupport.canonicalMaps(), 2,
                        com.project.game.testsupport.MonsterTestSupport.canonicalRepository()));
        return new Zone(1, 0, factory.createForMap(1));
    }

    private static Session session(PlayerProfile player) {
        SessionManager manager = new SessionManager();
        Session session = new Session(manager.nextId(), new NoopTransport(), manager,
                new LegacyPacketCodec(1024), "abc".getBytes(), 8,
                TestServices.serverServices(), ClientConfig.defaults());
        session.bindPlayer(player);
        return session;
    }

    private static final class NoopTransport implements ClientTransport {
        private final InputStream input = new ByteArrayInputStream(new byte[0]);
        private final OutputStream output = new ByteArrayOutputStream();

        @Override
        public InputStream input() { return input; }

        @Override
        public OutputStream output() { return output; }

        @Override
        public String remoteAddress() { return "zone-monster-test"; }

        @Override
        public void close() throws IOException {
            input.close();
            output.close();
        }
    }
}

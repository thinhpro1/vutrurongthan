package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.MessageName;
import com.project.game.player.Player;
import com.project.game.player.PlayerSaveData;
import com.project.game.resource.GameResources;
import com.project.game.testsupport.GameplayServices;
import com.project.game.testsupport.MapTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.project.game.testsupport.GameplayTestSupport.at;
import static com.project.game.testsupport.GameplayTestSupport.commands;
import static com.project.game.testsupport.GameplayTestSupport.drain;
import static com.project.game.testsupport.GameplayTestSupport.mapsWithoutMonsters;
import static com.project.game.testsupport.GameplayTestSupport.player;
import static com.project.game.testsupport.GameplayTestSupport.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vào game, đi map, về nhà, thoát game — theo luồng giống joinMap / teleport của rongthan. */
class MapManagerTest {
    private static final int WAYPOINT_X = 4464;
    private static final int WAYPOINT_Y = 936;

    // ------------------------------------------------------------------
    // Vào game
    // ------------------------------------------------------------------

    @Test
    void loginSendsMapInfoAndOthersSeePlayerOnlyAfterLoading() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0), maps);
        maps.finishLoad(first);
        drain(first);
        Session second = session(player(2, 0, 0), maps);
        Zone zone = maps.findZone(0, 0);

        maps.mapManager().enterGame(second.player());
        ZoneTestHooks.drain(zone);

        assertEquals(List.of(MessageName.MAP_INFO), commands(drain(second)));
        assertEquals(List.of(), drain(first), "người khác chưa thấy người đang tải map");
        assertTrue(second.player().isLoading());

        zone.finishLoadMap(second.player());
        ZoneTestHooks.drain(zone);

        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(second)));
        assertEquals(List.of(MessageName.ADD_PLAYER), commands(drain(first)));
        assertEquals(2, maps.memberCount(0, 0));
    }

    @Test
    void repeatedFinishLoadDoesNotDuplicatePresence() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session first = session(player(1, 0, 0), maps);
        Session second = session(player(2, 0, 0), maps);
        maps.finishLoad(first);
        maps.finishLoad(second);
        drain(first);
        drain(second);

        Zone zone = second.zone();
        zone.finishLoadMap(second.player());
        ZoneTestHooks.drain(zone);

        assertEquals(List.of(), drain(first));
        assertEquals(List.of(), drain(second));
    }

    @Test
    void differentZonesDoNotSeeEachOther() throws Exception {
        GameplayServices maps = twoZoneMaps(10, 10);
        Session first = session(player(1, 0, 0), maps);
        Session second = session(player(2, 0, 1), maps);

        maps.finishLoad(first);
        maps.finishLoad(second);

        assertEquals(List.of(MessageName.MAP_INFO), commands(drain(first)));
        assertEquals(List.of(MessageName.MAP_INFO), commands(drain(second)));
        assertEquals(1, maps.memberCount(0, 0));
        assertEquals(1, maps.memberCount(0, 1));
    }

    @Test
    void loginIntoMissingZoneUsesAnExistingOneWithoutCreatingZones() {
        GameplayServices maps = twoZoneMaps(10, 10);
        Session session = session(player(1, 1, 9), maps);

        maps.finishLoad(session);

        assertEquals(1, session.player().mapId());
        assertNotNull(session.zone());
        assertNull(maps.findZone(1, 9));
    }

    @Test
    void loginIntoClosedMapGoesHome() {
        GameplayServices maps = mapsWithoutMonsters();
        Session session = session(player(1, 77, 0), maps);

        maps.finishLoad(session);

        assertEquals(MapManager.HOME_MAP_ID, session.player().mapId());
        assertSame(maps.findZone(0, 0), session.zone());
    }

    // ------------------------------------------------------------------
    // Trong khu vực
    // ------------------------------------------------------------------

    @Test
    void movementIsSentToOthersButNotToMover() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session mover = session(player(1, 0, 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.finishLoad(mover);
        maps.finishLoad(observer);
        drain(mover);
        drain(observer);

        assertTrue(maps.movePlayer(mover, 1260, 640));

        assertEquals(List.of(), drain(mover));
        assertEquals(List.of(MessageName.PLAYER_MOVE), commands(drain(observer)));
    }

    @Test
    void leavingNotifiesOthersOnce() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session leaving = session(player(1, 0, 0), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.finishLoad(leaving);
        maps.finishLoad(observer);
        drain(observer);

        assertNotNull(maps.mapManager().leave(leaving));
        maps.mapManager().leave(leaving);

        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(drain(observer)));
        assertNull(leaving.zone());
        assertEquals(1, maps.memberCount(0, 0));
    }

    // ------------------------------------------------------------------
    // Đi map
    // ------------------------------------------------------------------

    @Test
    void waypointMovesPlayerToTheNextMap() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session moving = session(at(player(1, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
        Session observer = session(player(2, 0, 0), maps);
        maps.finishLoad(moving);
        maps.finishLoad(observer);
        drain(moving);
        drain(observer);
        Waypoint waypoint = maps.mapManager().getMap(0).findWaypoint(WAYPOINT_X, WAYPOINT_Y);

        assertTrue(maps.changeMap(moving));

        Player player = moving.player();
        assertEquals(waypoint.goMap(), player.mapId());
        assertEquals(waypoint.goX(), player.x());
        assertEquals(waypoint.goY(), player.y());
        assertSame(maps.findZone(waypoint.goMap(), player.zoneId()), moving.zone());
        assertEquals(List.of(MessageName.MAP_INFO), commands(drain(moving)));
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(drain(observer)));
        assertEquals(1, maps.memberCount(0, 0));
    }

    @Test
    void notStandingOnWaypointDoesNothing() {
        GameplayServices maps = mapsWithoutMonsters();
        Session session = session(player(1, 0, 0), maps);
        maps.finishLoad(session);
        Zone zone = session.zone();

        assertFalse(maps.changeMap(session));

        assertSame(zone, session.zone());
        assertEquals(0, session.player().mapId());
    }

    @Test
    void deadPlayerCannotUseWaypoint() {
        GameplayServices maps = mapsWithoutMonsters();
        Session session = session(at(player(1, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
        maps.finishLoad(session);
        session.player().injure(Long.MAX_VALUE);

        assertFalse(maps.changeMap(session));
        assertEquals(0, session.player().mapId());
    }

    @Test
    void fullZoneSendsNewcomerToTheLeastCrowdedZone() {
        GameplayServices maps = twoZoneMaps(10, 1);
        Session occupant = session(player(1, 1, 0), maps);
        maps.finishLoad(occupant);
        Session moving = session(at(player(2, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
        maps.finishLoad(moving);

        assertTrue(maps.changeMap(moving));

        assertEquals(1, moving.player().mapId());
        assertEquals(1, moving.player().zoneId());
        assertSame(maps.findZone(1, 1), moving.zone());
    }

    @Test
    void whenEveryZoneIsFullPlayerStillArrives() {
        GameplayServices maps = twoZoneMaps(10, 1);
        maps.finishLoad(session(player(1, 1, 0), maps));
        maps.finishLoad(session(player(2, 1, 1), maps));
        Session moving = session(at(player(3, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
        maps.finishLoad(moving);

        assertTrue(maps.changeMap(moving));

        assertEquals(1, moving.player().mapId());
        assertNotNull(moving.zone(), "không để người chơi kẹt giữa đường khi map đầy");
    }

    // ------------------------------------------------------------------
    // Chết và về nhà
    // ------------------------------------------------------------------

    @Test
    void returnTownFromDeadRevivesAtHomeAndSendsMapInfoThenWakeUp() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session victim = session(player(1, 1, 0), maps);
        Session observer = session(player(2, 1, 0), maps);
        maps.finishLoad(victim);
        maps.finishLoad(observer);
        drain(victim);
        drain(observer);
        victim.player().injure(Long.MAX_VALUE);

        assertTrue(maps.returnHomeFromDeath(victim));

        Player player = victim.player();
        assertEquals(MapManager.HOME_MAP_ID, player.mapId());
        assertEquals(MapManager.HOME_X, player.x());
        assertEquals(MapManager.HOME_Y, player.y());
        assertEquals(player.currentStats().maxHp(), player.hp());
        assertEquals(List.of(MessageName.MAP_INFO, MessageName.WAKE_UP_FROM_DIE),
                commands(drain(victim)));
        assertEquals(List.of(MessageName.REMOVE_PLAYER), commands(drain(observer)));
    }

    @Test
    void returnTownFromDeadIgnoresLivingPlayer() {
        GameplayServices maps = mapsWithoutMonsters();
        Session session = session(player(1, 1, 0), maps);
        maps.finishLoad(session);

        assertFalse(maps.returnHomeFromDeath(session));
        assertEquals(1, session.player().mapId());
    }

    // ------------------------------------------------------------------
    // Thoát game
    // ------------------------------------------------------------------

    @Test
    void deadThenDisconnectIsSavedRevivedAtHome() {
        GameplayServices maps = mapsWithoutMonsters();
        Session session = session(player(1, 1, 0), maps);
        maps.finishLoad(session);
        session.player().injure(Long.MAX_VALUE);
        session.transition(SessionState.IN_GAME, SessionState.CLOSED);

        PlayerSaveData saved = maps.mapManager().leave(session);

        assertEquals(MapManager.HOME_MAP_ID, saved.mapId());
        assertEquals(MapManager.HOME_X, saved.x());
        assertEquals(session.player().currentStats().maxHp(), saved.hp());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void disconnectWhileTravellingNeverLeavesAClosedPlayerInTheDestination() throws Exception {
        GameplayServices maps = mapsWithoutMonsters();
        Session moving = session(at(player(1, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
        maps.finishLoad(moving);
        Zone destination = maps.findZone(1, 0);
        CountDownLatch destinationBusy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        assertTrue(ZoneTestHooks.submit(destination, () -> {
            destinationBusy.countDown();
            await(release);
        }));
        assertTrue(destinationBusy.await(5, TimeUnit.SECONDS));

        // Rời map 0; việc "vào map 1" xếp hàng sau việc đang chặn Zone đích.
        Zone source = moving.zone();
        assertTrue(ZoneTestHooks.run(source, moving.player(), () -> {
            moving.player().requestChangeMap();
            return true;
        }));
        assertNull(moving.zone());
        assertSame(destination, moving.player().travelingTo());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread close = Thread.ofVirtual().start(() -> {
            try {
                moving.close();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        release.countDown();
        close.join(5_000);

        assertFalse(close.isAlive());
        assertNull(failure.get());
        assertEquals(SessionState.CLOSED, moving.state());
        assertNull(moving.zone());
        assertNull(moving.player().travelingTo());
        assertEquals(0, maps.memberCount(1, 0));
    }

    @Test
    void travelAndDisconnectRacingAlwaysEndWithPlayerNowhere() throws Exception {
        for (int round = 0; round < 50; round++) {
            GameplayServices maps = mapsWithoutMonsters();
            Session moving = session(at(player(1, 0, 0), WAYPOINT_X, WAYPOINT_Y), maps);
            maps.finishLoad(moving);
            Player player = moving.player();
            Zone source = moving.zone();

            source.post(player, player::requestChangeMap);
            moving.close();

            assertNull(player.zone(), "round " + round);
            assertNull(player.travelingTo(), "round " + round);
            assertEquals(0, maps.memberCount(0, 0), "round " + round);
            assertEquals(0, maps.memberCount(1, 0), "round " + round);
        }
    }

    // ------------------------------------------------------------------

    private static GameplayServices twoZoneMaps(int map0MaxPlayer, int map1MaxPlayer) {
        java.util.Map<Integer, MapTemplate> canonical = MapTestSupport.canonicalMaps();
        MapTemplate map0 = withPolicy(canonical.get(0), map0MaxPlayer);
        MapTemplate map1 = withPolicy(canonical.get(1), map1MaxPlayer);
        return new GameplayServices(
                java.util.Map.of(map0.id(), map0, map1.id(), map1), GameResources.unavailable());
    }

    private static MapTemplate withPolicy(MapTemplate map, int maxPlayer) {
        return new MapTemplate(map.id(), map.name(), "ONLINE", map.planet(), 2, 2, maxPlayer,
                map.dataId(), map.data(), map.waypoints());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

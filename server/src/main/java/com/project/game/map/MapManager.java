package com.project.game.map;

import com.project.game.monster.MonsterFactory;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/** Owns the map catalog, authoritative Zones, and map-level player orchestration. */
public final class MapManager {
    private static final int DEATH_RETURN_MAP_ID = 0;
    private static final int DEATH_RETURN_ZONE_ID = 0;
    private static final int DEATH_RETURN_X = 1250;
    private static final int DEATH_RETURN_Y = 648;

    private record ZoneKey(int mapId, int zoneId) {
    }

    private final Map<Integer, MapTemplate> maps;
    private final MonsterFactory monsterFactory;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();
    private final PlayerPacketWriter packets;

    public MapManager(Map<Integer, MapTemplate> maps, MonsterFactory monsterFactory,
                      PlayerPacketWriter packets) {
        Objects.requireNonNull(maps, "maps");
        TreeMap<Integer, MapTemplate> copiedMaps = new TreeMap<>();
        maps.forEach((mapId, map) -> {
            if (mapId == null || map == null) {
                throw new NullPointerException("maps must not contain null entries");
            }
            if (mapId != map.id()) {
                throw new IllegalArgumentException("map catalog key does not match map id");
            }
            copiedMaps.put(mapId, map);
        });
        this.maps = Collections.unmodifiableMap(copiedMaps);
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
        this.packets = Objects.requireNonNull(packets, "packets");
        for (MapTemplate map : this.maps.values()) {
            if ("ONLINE".equals(map.type())) {
                for (int zoneId = 0; zoneId < map.minZone(); zoneId++) {
                    zones.put(new ZoneKey(map.id(), zoneId), create(map, zoneId));
                }
            }
        }
    }

    /** Returns an existing Zone without creating one. */
    public Zone findZone(int mapId, int zoneId) {
        return zones.get(new ZoneKey(mapId, zoneId));
    }

    /** Returns a policy-valid Zone, creating it atomically when first requested. */
    public Zone getZone(int mapId, int zoneId) {
        MapTemplate map = requireOnlineMap(mapId, zoneId);
        ZoneKey key = new ZoneKey(mapId, zoneId);
        return zones.computeIfAbsent(key, ignored -> create(map, zoneId));
    }

    /** Returns all registered Zones in stable map/zone order. */
    public List<Zone> zones() {
        return zones.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparingInt(ZoneKey::mapId)
                                .thenComparingInt(ZoneKey::zoneId)))
                .map(Map.Entry::getValue)
                .toList();
    }

    public boolean finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        PlayerProfile joining = session.player();
        if (joining == null) {
            return false;
        }
        Zone zone = resolveZone(joining.mapId(), joining.zoneId());
        if (zone == null) {
            return false;
        }

        try {
            Join result = zone.tryCall(() -> {
                if (session.state() == SessionState.CLOSED || session.player() == null) {
                    return new Join(false, List.of());
                }

                Zone.JoinResult admission = zone.addPlayer(session);
                if (admission.status() == Zone.JoinStatus.FULL
                        || admission.status() == Zone.JoinStatus.PLAYER_ID_CONFLICT) {
                    return new Join(false, List.of());
                }
                if (admission.status() == Zone.JoinStatus.ALREADY_PRESENT) {
                    return new Join(true, List.of());
                }

                PlayerProfile current = session.player();
                List<Session> rejectedObservers = new ArrayList<>();
                for (Session member : admission.existing()) {
                    if (member == session || member.state() == SessionState.CLOSED
                            || member.player() == null) {
                        continue;
                    }
                    if (!session.trySend(packets.addPlayer(member.player()))) {
                        rejectedObservers.add(session);
                        return new Join(true, rejectedObservers);
                    }
                    if (!member.trySend(packets.addPlayer(current))) {
                        rejectedObservers.add(member);
                    }
                }
                return new Join(true, rejectedObservers);
            });
            closeRejected(result.rejectedObservers());
            return result.accepted();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public void leave(Session session) {
        if (session == null || session.player() == null) {
            return;
        }
        PlayerProfile leaving = session.player();
        Zone zone = findZone(leaving.mapId(), leaving.zoneId());
        if (zone == null) {
            return;
        }

        try {
            List<Session> rejectedObservers = zone.call(() -> {
                if (!zone.removePlayer(session)) {
                    return List.of();
                }

                Message packet = packets.removePlayer(leaving.id());
                List<Session> rejected = new ArrayList<>();
                for (Session member : zone.players()) {
                    if (member != session && member.state() != SessionState.CLOSED
                            && !member.trySend(packet)) {
                        rejected.add(member);
                    }
                }
                return List.copyOf(rejected);
            });
            closeRejected(rejectedObservers);
        } catch (RejectedExecutionException exception) {
            // A stopped Zone cannot accept more authoritative work.
        }
    }

    public boolean movePlayer(Session session, int x, int y) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }
        PlayerProfile observed = session.player();
        if (observed == null) {
            return false;
        }
        Zone zone = findZone(observed.mapId(), observed.zoneId());
        if (zone == null) {
            return false;
        }

        try {
            MoveDelivery result = zone.tryCall(() -> {
                Zone.Move moved = zone.movePlayer(
                        session, observed.mapId(), observed.zoneId(), x, y);
                if (!moved.moved()) {
                    return new MoveDelivery(false, List.of());
                }

                Message packet = packets.movePlayer(
                        moved.player().id(), moved.player().x(), moved.player().y());
                List<Session> rejectedObservers = new ArrayList<>();
                for (Session member : moved.observers()) {
                    if (member != session && member.state() != SessionState.CLOSED
                            && !member.trySend(packet)) {
                        rejectedObservers.add(member);
                    }
                }
                return new MoveDelivery(true, rejectedObservers);
            });
            closeRejected(result.rejectedObservers());
            return result.moved();
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    public Optional<PlayerProfile> returnTownFromDeath(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return Optional.empty();
        }

        PlayerProfile observed = session.player();
        if (observed == null || observed.hp() > 0L) {
            return Optional.empty();
        }

        Zone townZone = resolveZone(DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID);
        if (townZone == null || !canAddPlayer(townZone, session)) {
            return Optional.empty();
        }

        Zone sourceZone = findZone(observed.mapId(), observed.zoneId());
        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() > 0L
                    || current.mapId() != observed.mapId()
                    || current.zoneId() != observed.zoneId()) {
                return Optional.empty();
            }
            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID, DEATH_RETURN_X, DEATH_RETURN_Y);
            session.bindPlayer(revived);
            return Optional.of(revived);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() > 0L
                    || current.mapId() != observed.mapId()
                    || current.zoneId() != observed.zoneId()) {
                return Optional.empty();
            }

            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID, DEATH_RETURN_ZONE_ID, DEATH_RETURN_X, DEATH_RETURN_Y);
            if (sourceZone == townZone) {
                session.bindPlayer(revived);
                return Optional.of(revived);
            }

            boolean removed = sourceZone.removePlayer(session);
            session.bindPlayer(revived);
            if (removed) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.players()) {
                    if (member != session && member.state() != SessionState.CLOSED) {
                        member.send(packet);
                    }
                }
            }
            return Optional.of(revived);
        }
    }

    public Optional<PlayerProfile> changeMap(
            Session session,
            int expectedMapId,
            int expectedZoneId,
            int destinationMapId,
            int destinationZoneId,
            int destinationX,
            int destinationY) {
        if (session == null || session.state() == SessionState.CLOSED
                || session.player() == null) {
            return Optional.empty();
        }

        Zone destinationZone = resolveZone(destinationMapId, destinationZoneId);
        if (destinationZone == null || !canAddPlayer(destinationZone, session)) {
            return Optional.empty();
        }

        Zone sourceZone = findZone(expectedMapId, expectedZoneId);
        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != expectedMapId
                    || current.zoneId() != expectedZoneId) {
                return Optional.empty();
            }
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);
            return Optional.of(changed);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L
                    || current.mapId() != expectedMapId
                    || current.zoneId() != expectedZoneId) {
                return Optional.empty();
            }

            if (sourceZone == destinationZone) {
                PlayerProfile changed = current.withLocation(
                        destinationMapId, destinationZoneId, destinationX, destinationY);
                session.bindPlayer(changed);
                return Optional.of(changed);
            }

            boolean removed = sourceZone.removePlayer(session);
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);
            if (removed) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.players()) {
                    if (member != session && member.state() != SessionState.CLOSED) {
                        member.send(packet);
                    }
                }
            }
            return Optional.of(changed);
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = findZone(mapId, zoneId);
        return zone == null ? 0 : zone.size();
    }

    /** Resolves a policy-valid normal Zone before a handler emits map state. */
    public boolean ensureZone(int mapId, int zoneId) {
        return resolveZone(mapId, zoneId) != null;
    }

    private Zone resolveZone(int mapId, int zoneId) {
        try {
            return getZone(mapId, zoneId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean canAddPlayer(Zone zone, Session session) {
        return zone.canAddPlayer(session);
    }

    private MapTemplate requireOnlineMap(int mapId, int zoneId) {
        MapTemplate map = maps.get(mapId);
        if (map == null) {
            throw new IllegalArgumentException("unknown map " + mapId);
        }
        if (!"ONLINE".equals(map.type())) {
            throw new IllegalArgumentException("map " + mapId + " is not ONLINE");
        }
        if (zoneId < 0 || zoneId >= map.maxZone()) {
            throw new IllegalArgumentException(
                    "zone " + zoneId + " is outside map " + mapId + " bound 0.."
                            + (map.maxZone() - 1));
        }
        return map;
    }

    private Zone create(MapTemplate map, int zoneId) {
        return new Zone(map.id(), zoneId, map.maxPlayer(), monsterFactory.createForMap(map.id()));
    }

    private static void closeRejected(List<Session> rejectedObservers) {
        for (Session rejectedObserver : rejectedObservers) {
            rejectedObserver.close();
        }
    }

    private record Join(boolean accepted, List<Session> rejectedObservers) {
        private Join {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }

    private record MoveDelivery(boolean moved, List<Session> rejectedObservers) {
        private MoveDelivery {
            rejectedObservers = List.copyOf(rejectedObservers);
        }
    }
}

package com.project.game.map;

import com.project.game.network.Session;
import com.project.game.player.PlayerProfile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent runtime membership for one map zone. */
public final class Zone {
    private final int mapId;
    private final int zoneId;
    private final ConcurrentHashMap<Integer, Session> members = new ConcurrentHashMap<>();

    public Zone(int mapId, int zoneId) {
        this.mapId = mapId;
        this.zoneId = zoneId;
    }

    public int mapId() {
        return mapId;
    }

    public int zoneId() {
        return zoneId;
    }

    public synchronized boolean add(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        return members.putIfAbsent(player.id(), session) == null;
    }

    public synchronized boolean remove(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        return members.remove(player.id(), session);
    }

    /**
     * Returns the members present before this session was added, or {@code null} when it was already
     * a member. The snapshot and insertion share this Zone monitor so a join cannot miss another
     * concurrent join.
     */
    synchronized List<Session> addAndSnapshot(Session session) {
        Objects.requireNonNull(session, "session");
        PlayerProfile player = requirePlayer(session);
        List<Session> existing = List.copyOf(members.values());
        if (members.putIfAbsent(player.id(), session) != null) {
            return null;
        }
        return existing;
    }

    public synchronized boolean containsPlayer(int playerId) {
        return members.containsKey(playerId);
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized List<Session> snapshot() {
        return List.copyOf(members.values());
    }

    private static PlayerProfile requirePlayer(Session session) {
        PlayerProfile player = session.player();
        if (player == null) {
            throw new IllegalStateException("zone membership requires a bound player");
        }
        return player;
    }
}

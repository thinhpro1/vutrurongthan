package com.project.game.map;

import com.project.game.monster.MonsterDamageResult;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.monster.MonsterRespawnResult;
import com.project.game.monster.MonsterSnapshot;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.message.Message;
import com.project.game.player.PlayerProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/** Coordinates presence and movement within the current map/zone keys. */
public final class MapService {
    private static final int DEATH_RETURN_MAP_ID = 0;
    private static final int DEATH_RETURN_ZONE_ID = 0;
    private static final int DEATH_RETURN_X = 1250;
    private static final int DEATH_RETURN_Y = 648;

    private record ZoneKey(int mapId, int zoneId) {
    }

    private final PlayerPacketWriter packets;
    private final MonsterPacketWriter monsterPackets;
    private final MonsterRuntimeFactory monsterFactory;
    private final Clock clock;
    private final RandomGenerator random;
    private final ConcurrentHashMap<ZoneKey, Zone> zones = new ConcurrentHashMap<>();

    public MapService(PlayerPacketWriter packets,
                      MonsterPacketWriter monsterPackets,
                      MonsterRuntimeFactory monsterFactory) {
        this(packets, monsterPackets, monsterFactory, Clock.systemUTC(),
                RandomGenerator.getDefault());
    }

    public MapService(PlayerPacketWriter packets,
                      MonsterPacketWriter monsterPackets,
                      MonsterRuntimeFactory monsterFactory,
                      Clock clock) {
        this(packets, monsterPackets, monsterFactory, clock, RandomGenerator.getDefault());
    }

    public MapService(PlayerPacketWriter packets,
                      MonsterPacketWriter monsterPackets,
                      MonsterRuntimeFactory monsterFactory,
                      Clock clock,
                      RandomGenerator random) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.monsterFactory = Objects.requireNonNull(monsterFactory, "monsterFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public boolean canTargetMonster(Session session, int monsterId) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return false;
        }

        PlayerProfile player = session.player();
        if (player == null) {
            return false;
        }

        Zone zone = zones.get(keyOf(player));
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            return current != null
                    && current.hp() > 0L
                    && zone.contains(session)
                    && zone.hasLiveMonster(monsterId);
        }
    }

    public boolean attackMonster(Session session, int monsterId, long damage) {
        if (session == null
                || session.state() == SessionState.CLOSED
                || damage <= 0) {
            return false;
        }

        PlayerProfile player = session.player();
        if (player == null) {
            return false;
        }

        Zone zone = zones.get(keyOf(player));
        if (zone == null) {
            return false;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !zone.contains(session)) {
                return false;
            }

            Optional<MonsterDamageResult> result = zone.damageMonster(
                    monsterId, current.id(), damage, clock.millis());
            if (result.isEmpty()) {
                return false;
            }

            MonsterDamageResult combat = result.orElseThrow();
            PlayerProfile rewarded = null;
            if (combat.killed() && combat.potentialReward() > 0L) {
                PlayerProfile rewardCurrent = session.player();
                if (rewardCurrent == null || !zone.contains(session)) {
                    throw new IllegalStateException(
                            "killer left authoritative zone during serialized attack");
                }
                long potentialAfter = saturatingAddNonNegative(
                        rewardCurrent.potential(), combat.potentialReward());
                rewarded = rewardCurrent.withPotential(potentialAfter);
                session.bindPlayer(rewarded);
            }

            Message packet = combat.killed()
                    ? monsterPackets.startDie(combat)
                    : monsterPackets.injure(combat);

            for (Session member : zone.snapshot()) {
                if (member.state() != SessionState.CLOSED) {
                    member.send(packet);
                }
            }

            if (rewarded != null && session.state() != SessionState.CLOSED) {
                session.send(packets.potentialUpdate(rewarded.potential()));
            }

            return true;
        }
    }

    public void tickMonsterLifecycle() {
        long nowMillis = clock.millis();
        for (Zone zone : zones.values()) {
            synchronized (zone) {
                List<MonsterRespawnResult> respawned = zone.respawnDueMonsters(nowMillis);
                List<com.project.game.monster.MonsterAttackResult> attacks =
                        zone.attackDueMonsters(nowMillis, random);
                List<Session> members = zone.snapshot();
                for (MonsterRespawnResult result : respawned) {
                    Message packet = monsterPackets.respawn(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }
                }
                for (var result : attacks) {
                    Message packet = monsterPackets.attackPlayer(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }

                    if (!result.killed()) {
                        continue;
                    }

                    Session victim = members.stream()
                            .filter(member -> member.player() != null)
                            .filter(member -> member.player().id() == result.playerId())
                            .findFirst()
                            .orElse(null);
                    if (victim == null || victim.player() == null) {
                        continue;
                    }

                    PlayerProfile dead = victim.player();
                    Message selfDeath = packets.meDie(dead.x(), dead.y());
                    Message observerDeath = packets.playerDie(dead.id(), dead.x(), dead.y());
                    for (Session member : members) {
                        if (member.state() == SessionState.CLOSED) {
                            continue;
                        }
                        member.send(member == victim ? selfDeath : observerDeath);
                    }
                }
            }
        }
    }

    public void finishLoad(Session session) {
        if (session == null || session.state() == SessionState.CLOSED) {
            return;
        }
        PlayerProfile joining = session.player();
        if (joining == null) {
            return;
        }
        Zone zone = getOrCreateZone(joining.mapId(), joining.zoneId());
        synchronized (zone) {
            var existing = zone.addAndSnapshot(session);
            if (existing == null) {
                return;
            }
            for (Session member : existing) {
                if (member == session || member.state() == SessionState.CLOSED || member.player() == null) {
                    continue;
                }
                session.send(packets.addPlayer(member.player()));
                if (session.state() != SessionState.CLOSED) {
                    member.send(packets.addPlayer(joining));
                }
            }
        }
    }

    public void leave(Session session) {
        if (session == null) {
            return;
        }
        PlayerProfile leaving = session.player();
        if (leaving == null) {
            return;
        }
        ZoneKey key = keyOf(leaving);
        Zone zone = zones.get(key);
        if (zone == null) {
            return;
        }
        synchronized (zone) {
            if (!zone.remove(session)) {
                return;
            }
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED) {
                    member.send(packets.removePlayer(leaving.id()));
                }
            }
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

        ZoneKey sourceKey = keyOf(observed);
        Zone sourceZone = zones.get(sourceKey);

        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null
                    || current.hp() > 0L
                    || !sourceKey.equals(keyOf(current))) {
                return Optional.empty();
            }
            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID,
                    DEATH_RETURN_ZONE_ID,
                    DEATH_RETURN_X,
                    DEATH_RETURN_Y);
            session.bindPlayer(revived);
            return Optional.of(revived);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null
                    || current.hp() > 0L
                    || !sourceKey.equals(keyOf(current))) {
                return Optional.empty();
            }

            boolean removed = sourceZone.remove(session);
            PlayerProfile revived = current.revivedAt(
                    DEATH_RETURN_MAP_ID,
                    DEATH_RETURN_ZONE_ID,
                    DEATH_RETURN_X,
                    DEATH_RETURN_Y);
            session.bindPlayer(revived);

            if (removed) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.snapshot()) {
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
        if (session == null || session.state() == SessionState.CLOSED) {
            return Optional.empty();
        }

        ZoneKey sourceKey = new ZoneKey(expectedMapId, expectedZoneId);
        Zone sourceZone = zones.get(sourceKey);
        if (sourceZone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !sourceKey.equals(keyOf(current))) {
                return Optional.empty();
            }

            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);
            return Optional.of(changed);
        }

        synchronized (sourceZone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !sourceKey.equals(keyOf(current))) {
                return Optional.empty();
            }

            boolean removed = sourceZone.remove(session);
            PlayerProfile changed = current.withLocation(
                    destinationMapId, destinationZoneId, destinationX, destinationY);
            session.bindPlayer(changed);

            if (removed) {
                Message packet = packets.removePlayer(current.id());
                for (Session member : sourceZone.snapshot()) {
                    if (member != session && member.state() != SessionState.CLOSED) {
                        member.send(packet);
                    }
                }
            }
            return Optional.of(changed);
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
        ZoneKey expectedKey = keyOf(observed);
        Zone zone = zones.get(expectedKey);
        if (zone == null) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !expectedKey.equals(keyOf(current))) {
                return false;
            }
            session.bindPlayer(current.withPosition(x, y));
            return true;
        }

        synchronized (zone) {
            PlayerProfile current = session.player();
            if (current == null || current.hp() <= 0L || !expectedKey.equals(keyOf(current))) {
                return false;
            }

            boolean currentMember = zone.contains(session);
            PlayerProfile moved = current.withPosition(x, y);
            session.bindPlayer(moved);
            if (!currentMember) {
                return true;
            }

            Message packet = packets.movePlayer(moved.id(), moved.x(), moved.y());
            for (Session member : zone.snapshot()) {
                if (member != session && member.state() != SessionState.CLOSED) {
                    member.send(packet);
                }
            }
            return true;
        }
    }

    public int memberCount(int mapId, int zoneId) {
        Zone zone = zones.get(new ZoneKey(mapId, zoneId));
        return zone == null ? 0 : zone.size();
    }

    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        return getOrCreateZone(mapId, zoneId).monsterSnapshots();
    }

    private Zone getOrCreateZone(int mapId, int zoneId) {
        return zones.computeIfAbsent(
                new ZoneKey(mapId, zoneId),
                key -> new Zone(
                        key.mapId(),
                        key.zoneId(),
                        monsterFactory.createForMap(key.mapId())));
    }

    private static ZoneKey keyOf(PlayerProfile player) {
        return new ZoneKey(player.mapId(), player.zoneId());
    }

    private static long saturatingAddNonNegative(long current, long delta) {
        if (current < 0L || delta < 0L) {
            throw new IllegalArgumentException("values must be non-negative");
        }
        if (current > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }
}

package com.project.game.monster;

import com.project.game.map.Map;
import com.project.game.map.MapManager;
import com.project.game.map.Zone;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Điều phối ảnh chụp trạng thái Monster có thẩm quyền, nhịp vòng đời và phát thông báo. */
public final class MonsterManager {
    private final MapManager maps;
    private final MonsterPacketWriter monsterPackets;
    private final PlayerPacketWriter playerPackets;
    private final Clock clock;
    private final RandomGenerator random;

    public MonsterManager(MapManager maps,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets) {
        this(maps, monsterPackets, packets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public MonsterManager(MapManager maps,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets,
                          Clock clock,
                          RandomGenerator random) {
        this.maps = Objects.requireNonNull(maps, "maps");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.playerPackets = Objects.requireNonNull(packets, "packets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public void update() {
        long nowMillis = clock.millis();
        for (Zone zone : maps.zones()) {
            try {
                LifecycleDelivery delivery = zone.call(() -> {
                    synchronized (zone) {
                        List<Session> rejected = new java.util.ArrayList<>();
                        List<Monster.Move> moved = zone.moveMonsters();
                        List<Monster.Respawn> respawned = zone.respawnDueMonsters(nowMillis);
                        List<MonsterAttack> attacks = zone.attackDueMonsters(nowMillis, random);
                        List<Session> members = zone.members();
                        for (Monster.Move result : moved) {
                            sendToMembers(monsterPackets.move(result), members, rejected);
                        }
                        for (Monster.Respawn result : respawned) {
                            sendToMembers(monsterPackets.respawn(result), members, rejected);
                        }
                        for (MonsterAttack result : attacks) {
                            sendToMembers(monsterPackets.attackPlayer(result), members, rejected);
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

                            Player dead = victim.player();
                            Message selfDeath = playerPackets.meDie(dead.x(), dead.y());
                            Message observerDeath = playerPackets.playerDie(
                                    dead.id(), dead.x(), dead.y());
                            for (Session member : members) {
                                if (member.state() != SessionState.CLOSED
                                        && !member.trySend(member == victim ? selfDeath : observerDeath)) {
                                    rejected.add(member);
                                }
                            }
                        }
                        return new LifecycleDelivery(rejected);
                    }
                });
                closeRejected(delivery.rejected());
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                // Zone đang dừng, bỏ qua một nhịp lifecycle.
            }
        }
    }

    private static void sendToMembers(
            Message packet, List<Session> members, List<Session> rejected) {
        for (Session member : members) {
            if (member.state() != SessionState.CLOSED && !member.trySend(packet)) {
                rejected.add(member);
            }
        }
    }

    private static void closeRejected(List<Session> sessions) {
        for (Session session : sessions) {
            session.close();
        }
    }

    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        Map map = maps.getMap(mapId);
        Zone zone = map.getOrCreateZone(zoneId);
        return zone.monsterSnapshots();
    }

    private record LifecycleDelivery(List<Session> rejected) {
        private LifecycleDelivery {
            rejected = List.copyOf(rejected);
        }
    }
}

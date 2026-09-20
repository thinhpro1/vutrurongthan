package com.project.game.monster;

import com.project.game.map.Zone;
import com.project.game.map.ZoneRegistry;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.PlayerProfile;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Coordinates authoritative monster snapshots, lifecycle ticks, and broadcasts. */
public final class MonsterService {
    private final ZoneRegistry zones;
    private final MonsterPacketWriter monsterPackets;
    private final PlayerPacketWriter packets;
    private final Clock clock;
    private final RandomGenerator random;

    public MonsterService(ZoneRegistry zones,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets) {
        this(zones, monsterPackets, packets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public MonsterService(ZoneRegistry zones,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets,
                          Clock clock,
                          RandomGenerator random) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        return zones.getOrCreate(mapId, zoneId).monsterSnapshots();
    }

    public void tickLifecycle() {
        long nowMillis = clock.millis();
        for (Zone zone : zones.snapshot()) {
            synchronized (zone) {
                List<MonsterMoveResult> moved = zone.moveMonsters();
                List<MonsterRespawnResult> respawned = zone.respawnDueMonsters(nowMillis);
                List<MonsterAttackResult> attacks = zone.attackDueMonsters(nowMillis, random);
                List<Session> members = zone.snapshot();
                for (MonsterMoveResult result : moved) {
                    Message packet = monsterPackets.move(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }
                }
                for (MonsterRespawnResult result : respawned) {
                    Message packet = monsterPackets.respawn(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }
                }
                for (MonsterAttackResult result : attacks) {
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
}

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
public final class MonsterManager {
    private final ZoneRegistry zones;
    private final MonsterPacketWriter monsterPackets;
    private final PlayerPacketWriter playerPackets;
    private final Clock clock;
    private final RandomGenerator random;

    public MonsterManager(ZoneRegistry zones,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets) {
        this(zones, monsterPackets, packets, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    public MonsterManager(ZoneRegistry zones,
                          MonsterPacketWriter monsterPackets,
                          PlayerPacketWriter packets,
                          Clock clock,
                          RandomGenerator random) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
        this.playerPackets = Objects.requireNonNull(packets, "packets");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public void update() {
        long nowMillis = clock.millis();
        for (Zone zone : zones.snapshot()) {
            synchronized (zone) {
                List<Monster.Move> moved = zone.moveMonsters();
                List<Monster.Respawn> respawned = zone.respawnDueMonsters(nowMillis);
                List<MonsterAttack> attacks = zone.attackDueMonsters(nowMillis, random);
                List<Session> members = zone.snapshot();
                for (Monster.Move result : moved) {
                    Message packet = monsterPackets.move(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }
                }
                for (Monster.Respawn result : respawned) {
                    Message packet = monsterPackets.respawn(result);
                    for (Session member : members) {
                        if (member.state() != SessionState.CLOSED) {
                            member.send(packet);
                        }
                    }
                }
                for (MonsterAttack result : attacks) {
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
                    Message selfDeath = playerPackets.meDie(dead.x(), dead.y());
                    Message observerDeath = playerPackets.playerDie(dead.id(), dead.x(), dead.y());
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

    public List<MonsterSnapshot> monsterSnapshots(int mapId, int zoneId) {
        return zones.getOrCreate(mapId, zoneId).monsterSnapshots();
    }
}

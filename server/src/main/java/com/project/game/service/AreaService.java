package com.project.game.service;

import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Sends same-Zone presence and Monster packets without changing gameplay state. */
public final class AreaService {
    private final PlayerPacketWriter playerPackets;
    private final MonsterPacketWriter monsterPackets;

    public AreaService(PlayerPacketWriter playerPackets, MonsterPacketWriter monsterPackets) {
        this.playerPackets = Objects.requireNonNull(playerPackets, "playerPackets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
    }

    /** Exchanges presence between a joining Player and existing members. */
    public List<Session> addPlayer(Session joining, Player player, List<Session> existing) {
        Objects.requireNonNull(joining, "joining");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(existing, "existing");
        List<Session> rejected = new ArrayList<>();
        for (Session member : existing) {
            if (member == joining || member.state() == SessionState.CLOSED || member.player() == null) {
                continue;
            }
            if (!joining.trySend(playerPackets.addPlayer(member.player()))) {
                rejected.add(joining);
                break;
            }
            if (!member.trySend(playerPackets.addPlayer(player))) {
                rejected.add(member);
            }
        }
        return List.copyOf(rejected);
    }

    /** Notifies the remaining members that a Player has left. */
    public List<Session> removePlayer(Session leaving, int playerId, List<Session> remaining) {
        Objects.requireNonNull(leaving, "leaving");
        return send(playerPackets.removePlayer(playerId), remaining, leaving);
    }

    /** Notifies other members of a Player movement. */
    public List<Session> move(Session mover, Player player, List<Session> members) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(player, "player");
        return send(playerPackets.movePlayer(player.id(), player.x(), player.y()), members, mover);
    }

    /** Broadcasts the matching Monster damage or death packet in the current Zone. */
    public List<Session> monsterDamage(Monster.Damage result, List<Session> members) {
        Objects.requireNonNull(result, "result");
        return send(result.killed() ? monsterPackets.startDie(result) : monsterPackets.injure(result),
                members, null);
    }

    /** Broadcasts a Monster movement packet in the current Zone. */
    public List<Session> monsterMove(Monster.Move result, List<Session> members) {
        Objects.requireNonNull(result, "result");
        return send(monsterPackets.move(result), members, null);
    }

    /** Broadcasts a Monster respawn packet in the current Zone. */
    public List<Session> monsterRespawn(Monster.Respawn result, List<Session> members) {
        Objects.requireNonNull(result, "result");
        return send(monsterPackets.respawn(result), members, null);
    }

    /** Broadcasts a Monster attack, followed by target/self death packets when lethal. */
    public List<Session> monsterAttack(Monster.Attack result, List<Session> members) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(members, "members");
        LinkedHashSet<Session> rejected = new LinkedHashSet<>(
                send(monsterPackets.attackPlayer(result), members, null));
        if (!result.killed()) {
            return List.copyOf(rejected);
        }

        Session victim = null;
        for (Session member : members) {
            Player player = member.player();
            if (player != null && player.id() == result.playerId()) {
                victim = member;
                break;
            }
        }
        if (victim == null || victim.state() == SessionState.CLOSED || victim.player() == null) {
            return List.copyOf(rejected);
        }

        Player player = victim.player();
        Message selfDeath = playerPackets.meDie(player.x(), player.y());
        Message observedDeath = playerPackets.playerDie(player.id(), player.x(), player.y());
        for (Session member : members) {
            if (member.state() == SessionState.CLOSED) {
                continue;
            }
            Message death = member == victim ? selfDeath : observedDeath;
            if (!member.trySend(death)) {
                rejected.add(member);
            }
        }
        return List.copyOf(rejected);
    }

    private static List<Session> send(Message packet, List<Session> members, Session excluded) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(members, "members");
        LinkedHashSet<Session> rejected = new LinkedHashSet<>();
        for (Session member : members) {
            if (member == excluded || member.state() == SessionState.CLOSED) {
                continue;
            }
            if (!member.trySend(packet)) {
                rejected.add(member);
            }
        }
        return List.copyOf(rejected);
    }
}

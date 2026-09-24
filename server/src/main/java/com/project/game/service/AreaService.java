package com.project.game.service;

import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Gửi các thông báo hiện diện của Player trong cùng một Zone. */
public final class AreaService {
    private final PlayerPacketWriter packets;

    public AreaService(PlayerPacketWriter packets) {
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    /** Trao đổi hiện diện giữa Player mới và các thành viên đã có. */
    public List<Session> addPlayer(Session joining, Player player, List<Session> existing) {
        Objects.requireNonNull(joining, "joining");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(existing, "existing");
        List<Session> rejected = new ArrayList<>();
        for (Session member : existing) {
            if (member == joining || member.state() == SessionState.CLOSED || member.player() == null) {
                continue;
            }
            if (!joining.trySend(packets.addPlayer(member.player()))) {
                rejected.add(joining);
                break;
            }
            if (!member.trySend(packets.addPlayer(player))) {
                rejected.add(member);
            }
        }
        return List.copyOf(rejected);
    }

    /** Thông báo Player rời Zone cho các thành viên còn lại. */
    public List<Session> removePlayer(Session leaving, int playerId, List<Session> remaining) {
        Objects.requireNonNull(leaving, "leaving");
        Objects.requireNonNull(remaining, "remaining");
        var packet = packets.removePlayer(playerId);
        List<Session> rejected = new ArrayList<>();
        for (Session member : remaining) {
            if (member == leaving || member.state() == SessionState.CLOSED || !member.trySend(packet)) {
                if (member != leaving && member.state() != SessionState.CLOSED) {
                    rejected.add(member);
                }
            }
        }
        return List.copyOf(rejected);
    }

    /** Thông báo vị trí mới cho các thành viên khác, không gửi lại cho người di chuyển. */
    public List<Session> move(Session mover, Player player, List<Session> members) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(members, "members");
        var packet = packets.movePlayer(player.id(), player.x(), player.y());
        List<Session> rejected = new ArrayList<>();
        for (Session member : members) {
            if (member == mover || member.state() == SessionState.CLOSED) {
                continue;
            }
            if (!member.trySend(packet)) {
                rejected.add(member);
            }
        }
        return List.copyOf(rejected);
    }
}

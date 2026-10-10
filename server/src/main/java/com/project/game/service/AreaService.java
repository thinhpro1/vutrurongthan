package com.project.game.service;

import com.project.game.map.Zone;
import com.project.game.monster.Monster;
import com.project.game.network.Session;
import com.project.game.network.SessionState;
import com.project.game.network.message.Message;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.player.Player;

import java.util.List;
import java.util.Objects;

/**
 * Gửi packet cho những người trong cùng Zone. Gọi trên writer của Zone đó.
 * Người nhận bị đầy hàng đợi gửi thì bị kick; Zone đóng Session của họ sau khi rời writer.
 */
public final class AreaService {
    private final PlayerPacketWriter playerPackets;
    private final MonsterPacketWriter monsterPackets;

    public AreaService(PlayerPacketWriter playerPackets, MonsterPacketWriter monsterPackets) {
        this.playerPackets = Objects.requireNonNull(playerPackets, "playerPackets");
        this.monsterPackets = Objects.requireNonNull(monsterPackets, "monsterPackets");
    }

    // ------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------

    /** Player mới vào và những người đang ở trong Zone thấy nhau. */
    public void addPlayer(Zone zone, Player joining, List<Player> existing) {
        for (Player member : existing) {
            if (member == joining) {
                continue;
            }
            if (isClosed(member)) {
                continue;
            }
            if (!send(zone, joining, playerPackets.addPlayer(member))) {
                return;
            }
            send(zone, member, playerPackets.addPlayer(joining));
        }
    }

    /** Báo những người còn lại rằng Player đã rời Zone. */
    public void removePlayer(Zone zone, Player leaving) {
        sendToAll(zone, playerPackets.removePlayer(leaving.id()), leaving);
    }

    public void playerMove(Player player) {
        sendToAll(player.zone(), playerPackets.movePlayer(player.id(), player.x(), player.y()), player);
    }

    /** Tổng tiềm năng mới, chỉ gửi cho chính Player. */
    public void playerPotential(Player player) {
        send(player.zone(), player, playerPackets.potentialUpdate(player.potential()));
    }

    // ------------------------------------------------------------------
    // Monster
    // ------------------------------------------------------------------

    public void monsterInjure(Monster monster, long damage) {
        sendToAll(monster.zone(), monsterPackets.injure(monster, damage), null);
    }

    public void monsterStartDie(Monster monster, long damage) {
        sendToAll(monster.zone(), monsterPackets.startDie(monster, damage), null);
    }

    public void monsterMove(Monster monster) {
        sendToAll(monster.zone(), monsterPackets.move(monster), null);
    }

    public void monsterRespawn(Monster monster) {
        sendToAll(monster.zone(), monsterPackets.respawn(monster), null);
    }

    /** Monster đánh Player; nếu Player chết thì báo thêm cho chính họ và người xung quanh. */
    public void monsterAttack(Monster monster, Player target, long damage) {
        Zone zone = monster.zone();
        sendToAll(zone, monsterPackets.attackPlayer(monster, target, damage), null);
        if (!target.isDead()) {
            return;
        }
        if (isClosed(target)) {
            return;
        }
        Message selfDeath = playerPackets.meDie(target.x(), target.y());
        Message observedDeath = playerPackets.playerDie(target.id(), target.x(), target.y());
        for (Player member : zone.players()) {
            if (member == target) {
                send(zone, member, selfDeath);
            } else {
                send(zone, member, observedDeath);
            }
        }
    }

    // ------------------------------------------------------------------

    private void sendToAll(Zone zone, Message packet, Player except) {
        for (Player member : zone.players()) {
            if (member == except) {
                continue;
            }
            send(zone, member, packet);
        }
    }

    private static boolean isClosed(Player player) {
        Session session = player.session();
        return session == null || session.state() == SessionState.CLOSED;
    }

    /** Gửi không chặn; đầy hàng đợi thì kick. Trả về false nếu không gửi được. */
    private static boolean send(Zone zone, Player player, Message packet) {
        if (isClosed(player)) {
            return false;
        }
        Session session = player.session();
        if (session.trySend(packet)) {
            return true;
        }
        zone.kick(player);
        return false;
    }
}

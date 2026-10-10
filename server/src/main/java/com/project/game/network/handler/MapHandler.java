package com.project.game.network.handler;

import com.project.game.map.Zone;
import com.project.game.network.Session;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageReader;
import com.project.game.player.Player;

import java.io.IOException;
import java.util.function.Consumer;

/** Đọc packet di chuyển / chuyển map / tải map xong rồi chuyển cho Player trên thread của Zone. */
final class MapHandler {
    private final Session session;

    MapHandler(Session session) {
        this.session = session;
    }

    void handleFinishLoadMap(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing FINISH_LOAD_MAP payload bytes");
        }
        Player player = session.player();
        if (player == null) {
            throw new IOException("FINISH_LOAD_MAP without bound player");
        }
        Zone zone = player.zone();
        if (zone == null) {
            throw new IOException("FINISH_LOAD_MAP before MAP_INFO");
        }
        zone.finishLoadMap(player);
    }

    void handleReturnTownFromDie(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing RETURN_TOWN_FROM_DIE payload bytes");
        }
        post(player -> player.returnTownFromDead());
    }

    void handleUnsupportedWakeUpFromDie(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing WAKE_UP_FROM_DIE request payload bytes");
        }
    }

    void handleRequestChangeMap(Message message) throws IOException {
        if (message.payload().length != 0) {
            throw new IOException("trailing REQUEST_CHANGE_MAP payload bytes");
        }
        post(player -> player.requestChangeMap());
    }

    void handlePlayerMove(Message message) throws IOException {
        MessageReader reader = message.reader();
        int x = reader.readShort();
        int y = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IOException("trailing PLAYER_MOVE payload bytes");
        }
        post(player -> player.move(x, y));
    }

    /** Xếp lệnh vào Zone của người chơi; đang đi giữa hai Zone thì bỏ lệnh. */
    private void post(Consumer<Player> action) throws IOException {
        Player player = session.player();
        if (player == null) {
            throw new IOException("map command without bound player");
        }
        Zone zone = player.zone();
        if (zone == null) {
            return;
        }
        zone.post(player, () -> action.accept(player));
    }
}

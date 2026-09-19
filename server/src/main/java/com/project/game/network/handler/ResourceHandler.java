package com.project.game.network.handler;

import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.message.Message;
import com.project.game.network.packet.ResourcePacketWriter;
import com.project.game.resource.GameResources;

import java.io.IOException;

/** Handles legacy static-resource bootstrap and icon requests. */
final class ResourceHandler {
    private static final int NOT_PROVIDED_VERSION = -1;
    private static final int DEV_EFFECT_VERSION = 2;
    private static final int DEV_LEVEL_VERSION = 0;
    private static final int DEV_FRAME_VERSION = 1;

    private final Session session;
    private final GameResources resources;
    private final NetworkEventObserver eventObserver;
    private final ResourcePacketWriter resourcePackets = new ResourcePacketWriter();

    ResourceHandler(Session session, GameResources resources, NetworkEventObserver eventObserver) {
        this.session = session;
        this.resources = resources;
        this.eventObserver = eventObserver;
    }

    void handleUpdateData(Message message) throws IOException {
        var reader = message.reader();
        int type = reader.readByte();
        if (reader.remaining() != 0) {
            throw new IOException("trailing UPDATE_DATA payload bytes");
        }
        eventObserver.onUpdateData(session, type);
        switch (type) {
            case -1 -> sendResourceManifest();
            case 12 -> sendIconManifest();
            case 3 -> sendEffectResource();
            case 4 -> sendMonsterResource();
            case 6 -> sendLevelResource();
            case 7 -> sendFrameResource();
            default -> { }
        }
    }

    private void sendResourceManifest() throws IOException {
        int frameVersion = resources.frames().isEmpty()
                ? NOT_PROVIDED_VERSION : DEV_FRAME_VERSION;
        int levelVersion = resources.levels().isEmpty()
                ? NOT_PROVIDED_VERSION : DEV_LEVEL_VERSION;
        int effectVersion = resources.effects().isEmpty()
                ? NOT_PROVIDED_VERSION : DEV_EFFECT_VERSION;
        int monsterVersion = resources.monsterVersion();
        session.send(resourcePackets.resourceManifest(
                resources.imageVersion(), effectVersion, monsterVersion,
                levelVersion, frameVersion));
    }

    private void sendIconManifest() throws IOException {
        Message packet = resourcePackets.iconManifest(resources.iconManifest());
        if (packet.payload().length > session.maxPacketSize()) {
            throw new IOException("icon manifest exceeds max packet size: "
                    + packet.payload().length);
        }
        session.send(packet);
    }

    private void sendEffectResource() throws IOException {
        var effects = resources.effects();
        if (effects.isEmpty()) {
            return;
        }
        session.send(resourcePackets.effectResource(DEV_EFFECT_VERSION, effects));
    }

    private void sendMonsterResource() throws IOException {
        int version = resources.monsterVersion();
        var darts = resources.monsterDarts();
        var templates = resources.monsterTemplates();
        if (version < 0 || darts.isEmpty() || templates.isEmpty()) {
            return;
        }
        session.send(resourcePackets.monsterResource(version, darts, templates));
    }

    private void sendLevelResource() throws IOException {
        var levels = resources.levels();
        if (levels.isEmpty()) {
            return;
        }
        session.send(resourcePackets.levelResource(DEV_LEVEL_VERSION, levels));
    }

    private void sendFrameResource() throws IOException {
        var frames = resources.frames();
        if (frames.isEmpty()) {
            return;
        }
        session.send(resourcePackets.frameResource(DEV_FRAME_VERSION, frames));
    }

    void handleRequestIcon(Message message) throws IOException {
        var reader = message.reader();
        int iconId = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IOException("trailing REQUEST_ICON payload bytes");
        }
        var data = resources.loadIcon(iconId);
        if (data.isEmpty() || data.get().length == 0) {
            return;
        }
        byte[] bytes = data.get();
        Message packet = resourcePackets.icon(iconId, bytes);
        if (packet.payload().length > session.maxPacketSize()) {
            return;
        }
        session.send(packet);
    }
}

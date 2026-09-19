package com.project.game.network.handler;

import com.project.game.monster.LegacyMonsterDartPhase;
import com.project.game.network.NetworkEventObserver;
import com.project.game.network.Session;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.resource.FrameTemplate;
import com.project.game.resource.GameResources;
import com.project.game.resource.IconFingerprint;

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
        MessageWriter writer = new MessageWriter()
                .writeByte(-1)
                .writeByte(resources.imageVersion())
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(effectVersion)
                .writeByte(monsterVersion)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(levelVersion)
                .writeByte(frameVersion)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(NOT_PROVIDED_VERSION)
                .writeByte(NOT_PROVIDED_VERSION);
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
    }

    private void sendIconManifest() throws IOException {
        var manifest = resources.iconManifest();
        if (manifest.size() > Short.MAX_VALUE) {
            throw new IOException("too many icon manifest entries: " + manifest.size());
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(12)
                .writeShort(manifest.size());
        for (IconFingerprint icon : manifest) {
            writer.writeShort(icon.iconId()).writeLong(icon.fingerprint());
        }
        byte[] payload = writer.toByteArray();
        if (payload.length > session.maxPacketSize()) {
            throw new IOException("icon manifest exceeds max packet size: " + payload.length);
        }
        session.send(new Message(MessageName.UPDATE_DATA, payload));
    }

    private void sendEffectResource() throws IOException {
        var effects = resources.effects();
        if (effects.isEmpty()) {
            return;
        }
        if (effects.size() > Short.MAX_VALUE) {
            throw new IOException("too many legacy movement effects: " + effects.size());
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(3)
                .writeByte(DEV_EFFECT_VERSION)
                .writeShort(effects.size());
        for (var effect : effects) {
            if (effect.icons().size() > Byte.MAX_VALUE) {
                throw new IOException("too many icons for legacy effect " + effect.id());
            }
            writer.writeShort(effect.id())
                    .writeShort(effect.dx())
                    .writeShort(effect.dy())
                    .writeShort(effect.delay())
                    .writeByte(effect.icons().size());
            effect.icons().forEach(writer::writeShort);
        }
        writer.writeShort(0);
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
    }

    private void sendMonsterResource() throws IOException {
        int version = resources.monsterVersion();
        var darts = resources.monsterDarts();
        var templates = resources.monsterTemplates();
        if (version < 0 || darts.isEmpty() || templates.isEmpty()) {
            return;
        }
        if (darts.size() > Short.MAX_VALUE || templates.size() > Short.MAX_VALUE) {
            throw new IOException("too many monster resources");
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(4)
                .writeByte(version)
                .writeShort(darts.size());
        for (var dart : darts) {
            writer.writeShort(dart.id()).writeBoolean(dart.meteorite());
            writeMonsterDartPhase(writer, dart.light());
            writeMonsterDartPhase(writer, dart.bullet());
            writeMonsterDartPhase(writer, dart.explode());
        }
        writer.writeShort(templates.size());
        for (var template : templates) {
            if (template.iconsMove().size() > Byte.MAX_VALUE) {
                throw new IOException("too many move icons for monster template " + template.id());
            }
            writer.writeShort(template.id())
                    .writeUtf(template.name())
                    .writeShort(template.rangeMove())
                    .writeByte(template.speed())
                    .writeByte(template.type())
                    .writeByte(template.dartId())
                    .writeByte(template.iconsMove().size());
            for (int icon : template.iconsMove()) {
                writer.writeShort(icon);
            }
            writer.writeShort(template.iconInjure())
                    .writeShort(template.iconAttack())
                    .writeShort(template.w())
                    .writeShort(template.h())
                    .writeByte(template.dx())
                    .writeByte(template.dy());
        }
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
    }

    private void writeMonsterDartPhase(MessageWriter writer, LegacyMonsterDartPhase phase)
            throws IOException {
        if (phase.icons().size() > Byte.MAX_VALUE) {
            throw new IOException("too many monster dart phase icons: " + phase.icons().size());
        }
        writer.writeByte(phase.icons().size());
        for (int icon : phase.icons()) {
            writer.writeShort(icon);
        }
        writer.writeShort(phase.dx()).writeShort(phase.dy()).writeShort(phase.delay());
    }

    private void sendLevelResource() throws IOException {
        var levels = resources.levels();
        if (levels.isEmpty()) {
            return;
        }
        if (levels.size() > Short.MAX_VALUE) {
            throw new IOException("too many legacy levels: " + levels.size());
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(6)
                .writeByte(DEV_LEVEL_VERSION)
                .writeShort(levels.size());
        for (var level : levels) {
            writer.writeShort(level.id()).writeUtf(level.name()).writeLong(level.power());
        }
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
    }

    private void sendFrameResource() throws IOException {
        var frames = resources.frames();
        if (frames.isEmpty()) {
            return;
        }
        if (frames.size() > Short.MAX_VALUE) {
            throw new IOException("too many frame templates: " + frames.size());
        }
        MessageWriter writer = new MessageWriter()
                .writeByte(7)
                .writeByte(DEV_FRAME_VERSION)
                .writeShort(frames.size());
        for (FrameTemplate frame : frames) {
            writer.writeShort(frame.id())
                    .writeShort(frame.hpBar())
                    .writeShort(frame.chat())
                    .writeByte(frame.dead().size());
            frame.dead().forEach(writer::writeShort);
            writer.writeByte(frame.stand().size());
            frame.stand().forEach(writer::writeShort);
            writer.writeByte(frame.run().size());
            frame.run().forEach(writer::writeShort);
            writer.writeShort(frame.fly())
                    .writeShort(frame.jump())
                    .writeShort(frame.fall())
                    .writeShort(frame.injure())
                    .writeByte(frame.action().size());
            frame.action().forEach((actionId, iconId) -> writer.writeByte(actionId).writeShort(iconId));
            writer.writeShort(frame.dx())
                    .writeShort(frame.dy())
                    .writeShort(frame.width())
                    .writeShort(frame.height());
        }
        session.send(new Message(MessageName.UPDATE_DATA, writer.toByteArray()));
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
        MessageWriter writer = new MessageWriter()
                .writeShort(iconId)
                .writeInt(bytes.length)
                .writeBytes(bytes);
        byte[] payload = writer.toByteArray();
        if (payload.length > session.maxPacketSize()) {
            return;
        }
        session.send(new Message(MessageName.REQUEST_ICON, payload));
    }
}

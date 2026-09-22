package com.project.game.network.packet;

import com.project.game.monster.MonsterDart;
import com.project.game.monster.MonsterDart.Phase;
import com.project.game.monster.MonsterTemplate;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageWriter;
import com.project.game.resource.FrameTemplate;
import com.project.game.resource.IconFingerprint;
import com.project.game.resource.EffectImage;
import com.project.game.resource.LevelTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Serializes static legacy bootstrap resources and icon bytes. */
public final class ResourcePacketWriter {
    public Message resourceManifest(
            int imageVersion,
            int effectVersion,
            int monsterVersion,
            int levelVersion,
            int frameVersion) {
        MessageWriter writer = new MessageWriter()
                .writeByte(-1)
                .writeByte(imageVersion)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(effectVersion)
                .writeByte(monsterVersion)
                .writeByte(-1)
                .writeByte(levelVersion)
                .writeByte(frameVersion)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1)
                .writeByte(-1);
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    public Message iconManifest(List<IconFingerprint> manifest) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        requireShortCount(manifest.size(), "icon manifest entries");
        MessageWriter writer = new MessageWriter()
                .writeByte(12)
                .writeShort(manifest.size());
        for (IconFingerprint icon : manifest) {
            Objects.requireNonNull(icon, "icon");
            writer.writeShort(icon.iconId()).writeLong(icon.fingerprint());
        }
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    public Message effectResource(int version, List<EffectImage> effects)
            throws IOException {
        Objects.requireNonNull(effects, "effects");
        requireShortCount(effects.size(), "legacy movement effects");
        MessageWriter writer = new MessageWriter()
                .writeByte(3)
                .writeByte(version)
                .writeShort(effects.size());
        for (EffectImage effect : effects) {
            Objects.requireNonNull(effect, "effect");
            requireByteCount(effect.icons().size(), "icons for legacy effect " + effect.id());
            writer.writeShort(effect.id())
                    .writeShort(effect.dx())
                    .writeShort(effect.dy())
                    .writeShort(effect.delay())
                    .writeByte(effect.icons().size());
            for (int icon : effect.icons()) {
                writer.writeShort(icon);
            }
        }
        writer.writeShort(0);
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    public Message monsterResource(
            int version,
            List<MonsterDart> darts,
            List<MonsterTemplate> templates) throws IOException {
        Objects.requireNonNull(darts, "darts");
        Objects.requireNonNull(templates, "templates");
        requireShortCount(darts.size(), "monster darts");
        requireShortCount(templates.size(), "monster templates");
        MessageWriter writer = new MessageWriter()
                .writeByte(4)
                .writeByte(version)
                .writeShort(darts.size());
        for (MonsterDart dart : darts) {
            Objects.requireNonNull(dart, "dart");
            writer.writeShort(dart.id()).writeBoolean(dart.meteorite());
            writeMonsterDartPhase(writer, dart.light());
            writeMonsterDartPhase(writer, dart.bullet());
            writeMonsterDartPhase(writer, dart.explode());
        }
        writer.writeShort(templates.size());
        for (MonsterTemplate template : templates) {
            Objects.requireNonNull(template, "template");
            writer.writeShort(template.id())
                    .writeUtf(template.name())
                    .writeShort(template.rangeMove())
                    .writeByte(template.speed())
                    .writeByte(template.type())
                    .writeByte(template.dartId());
            writeMonsterAnimation(writer, template.iconsMove(),
                    "move icons for monster template " + template.id());
            writeMonsterAnimation(writer, template.iconsInjure(),
                    "injure icons for monster template " + template.id());
            writeMonsterAnimation(writer, template.iconsAttack(),
                    "attack icons for monster template " + template.id());
            writer.writeShort(template.w()).writeShort(template.h());
        }
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    private static void writeMonsterDartPhase(MessageWriter writer,
                                                MonsterDart.Phase phase)
            throws IOException {
        Objects.requireNonNull(phase, "phase");
        requireByteCount(phase.icons().size(), "monster dart phase icons");
        writer.writeByte(phase.icons().size());
        for (int icon : phase.icons()) {
            writer.writeShort(icon);
        }
        writer.writeShort(phase.dx())
                .writeShort(phase.dy())
                .writeShort(phase.delay());
    }

    private static void writeMonsterAnimation(
            MessageWriter writer, List<Integer> icons, String field) throws IOException {
        Objects.requireNonNull(icons, field);
        if (icons.isEmpty() || icons.size() > Byte.MAX_VALUE) {
            throw new IOException(field + " must contain 1..127 icons: " + icons.size());
        }
        writer.writeByte(icons.size());
        for (Integer icon : icons) {
            if (icon == null || icon < 0 || icon > Short.MAX_VALUE) {
                throw new IOException(field + " icon must be between 0 and 32767: " + icon);
            }
            writer.writeShort(icon);
        }
    }

    public Message levelResource(int version, List<LevelTemplate> levels) throws IOException {
        Objects.requireNonNull(levels, "levels");
        requireShortCount(levels.size(), "legacy levels");
        MessageWriter writer = new MessageWriter()
                .writeByte(6)
                .writeByte(version)
                .writeShort(levels.size());
        for (LevelTemplate level : levels) {
            Objects.requireNonNull(level, "level");
            writer.writeShort(level.id()).writeUtf(level.name()).writeLong(level.power());
        }
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    public Message frameResource(int version, List<FrameTemplate> frames) throws IOException {
        Objects.requireNonNull(frames, "frames");
        requireShortCount(frames.size(), "frame templates");
        MessageWriter writer = new MessageWriter()
                .writeByte(7)
                .writeByte(version)
                .writeShort(frames.size());
        for (FrameTemplate frame : frames) {
            Objects.requireNonNull(frame, "frame");
            requireByteCount(frame.dead().size(), "dead frame icons");
            requireByteCount(frame.stand().size(), "stand frame icons");
            requireByteCount(frame.run().size(), "run frame icons");
            requireByteCount(frame.action().size(), "frame actions");
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
            frame.action().forEach((actionId, iconId) ->
                    writer.writeByte(actionId).writeShort(iconId));
            writer.writeShort(frame.dx())
                    .writeShort(frame.dy())
                    .writeShort(frame.width())
                    .writeShort(frame.height());
        }
        return new Message(MessageName.UPDATE_DATA, writer.toByteArray());
    }

    public Message icon(int iconId, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        MessageWriter writer = new MessageWriter()
                .writeShort(iconId)
                .writeInt(bytes.length)
                .writeBytes(bytes);
        return new Message(MessageName.REQUEST_ICON, writer.toByteArray());
    }

    private static void requireShortCount(int count, String field) throws IOException {
        if (count > Short.MAX_VALUE) {
            throw new IOException("too many " + field + ": " + count);
        }
    }

    private static void requireByteCount(int count, String field) throws IOException {
        if (count > Byte.MAX_VALUE) {
            throw new IOException("too many " + field + ": " + count);
        }
    }
}

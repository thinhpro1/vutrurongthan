package com.project.game.network.packet;

import com.project.game.monster.MonsterDart;
import com.project.game.monster.MonsterDart.Phase;
import com.project.game.monster.MonsterTemplate;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.resource.FrameTemplate;
import com.project.game.resource.IconFingerprint;
import com.project.game.resource.EffectImage;
import com.project.game.resource.LevelTemplate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePacketWriterTest {
    private final ResourcePacketWriter writer = new ResourcePacketWriter();

    @Test
    void rejectsMonsterDartCountThatWouldBeTruncated() {
        assertThrows(IOException.class, () -> writer.monsterResource(
                1, Collections.nCopies(Short.MAX_VALUE + 1, validDart()), List.of()));
    }

    @Test
    void rejectsMonsterTemplateCountThatWouldBeTruncated() {
        assertThrows(IOException.class, () -> writer.monsterResource(
                1, List.of(), Collections.nCopies(Short.MAX_VALUE + 1, validTemplate())));
    }

    @Test
    void rejectsMonsterDartPhaseIconCountThatWouldBeTruncated() {
        MonsterDart.Phase overflowing = new MonsterDart.Phase(
                Collections.nCopies(Byte.MAX_VALUE + 1, 1), 0, 0, 0);
        MonsterDart dart = new MonsterDart(
                1, false, overflowing, validPhase(), validPhase());

        assertThrows(IOException.class, () -> writer.monsterResource(1, List.of(dart), List.of()));
    }

    @Test
    void rejectsMonsterMoveIconCountThatWouldBeTruncated() {
        MonsterTemplate overflowing = new MonsterTemplate(
                1, "bat", 1, 1, 1, 1,
                Collections.nCopies(Byte.MAX_VALUE + 1, 1), List.of(1), List.of(1),
                1, 1);

        assertThrows(IOException.class, () -> writer.monsterResource(1, List.of(), List.of(overflowing)));
    }

    @Test
    void rejectsEmptyOrOversizedMonsterAnimationArrays() {
        for (int animation = 0; animation < 3; animation++) {
            List<Integer> move = animation == 0 ? List.of() : List.of(1);
            List<Integer> injure = animation == 1 ? List.of() : List.of(1);
            List<Integer> attack = animation == 2 ? List.of() : List.of(1);
            MonsterTemplate empty = new MonsterTemplate(
                    1, "bat", 1, 1, 1, 1, move, injure, attack, 1, 1);
            assertThrows(IOException.class, () -> writer.monsterResource(1, List.of(), List.of(empty)));

            List<Integer> overflowingMove = animation == 0
                    ? Collections.nCopies(Byte.MAX_VALUE + 1, 1) : List.of(1);
            List<Integer> overflowingInjure = animation == 1
                    ? Collections.nCopies(Byte.MAX_VALUE + 1, 1) : List.of(1);
            List<Integer> overflowingAttack = animation == 2
                    ? Collections.nCopies(Byte.MAX_VALUE + 1, 1) : List.of(1);
            MonsterTemplate overflowing = new MonsterTemplate(
                    1, "bat", 1, 1, 1, 1,
                    overflowingMove, overflowingInjure, overflowingAttack, 1, 1);
            assertThrows(IOException.class,
                    () -> writer.monsterResource(1, List.of(), List.of(overflowing)));
        }
    }

    @Test
    void rejectsLevelCountThatWouldBeTruncated() {
        assertThrows(IOException.class, () -> writer.levelResource(
                1, Collections.nCopies(Short.MAX_VALUE + 1, new LevelTemplate(1, "level", 1L))));
    }

    @Test
    void rejectsFrameCountThatWouldBeTruncated() {
        assertThrows(IOException.class, () -> writer.frameResource(
                1, Collections.nCopies(Short.MAX_VALUE + 1, validFrame())));
    }

    @Test
    void serializesResourceManifestInLegacyVersionOrder() throws Exception {
        Message message = writer.resourceManifest(1, 2, 3, 4, 5);
        assertEquals(MessageName.UPDATE_DATA, message.command());
        var reader = message.reader();
        assertEquals(-1, reader.readByte());
        assertEquals(1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(2, reader.readByte());
        assertEquals(3, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(4, reader.readByte());
        assertEquals(5, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesIconManifestInInputOrder() throws Exception {
        Message message = writer.iconManifest(List.of(
                new IconFingerprint(2, 0x0102030405060708L),
                new IconFingerprint(10, -3L)));
        var reader = message.reader();
        assertEquals(12, reader.readByte());
        assertEquals(2, reader.readShort());
        assertEquals(2, reader.readShort());
        assertEquals(0x0102030405060708L, reader.readLong());
        assertEquals(10, reader.readShort());
        assertEquals(-3L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesEffectResourceWithTemplateSentinel() throws Exception {
        EffectImage effect = new EffectImage(17, -2, 3, 40, List.of(9, 10));
        var reader = writer.effectResource(2, List.of(effect)).reader();
        assertEquals(3, reader.readByte());
        assertEquals(2, reader.readByte());
        assertEquals(1, reader.readShort());
        assertEquals(17, reader.readShort());
        assertEquals(-2, reader.readShort());
        assertEquals(3, reader.readShort());
        assertEquals(40, reader.readShort());
        assertEquals(2, reader.readByte());
        assertEquals(9, reader.readShort());
        assertEquals(10, reader.readShort());
        assertEquals(0, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesMonsterDartsAndTemplatesInV2Shape() throws Exception {
        MonsterDart.Phase light = new MonsterDart.Phase(List.of(1), 2, 3, 4);
        MonsterDart.Phase bullet = new MonsterDart.Phase(List.of(5, 6), 7, 8, 9);
        MonsterDart.Phase explode = new MonsterDart.Phase(List.of(10), 11, 12, 13);
        MonsterDart dart = new MonsterDart(4, true, light, bullet, explode);
        MonsterTemplate template = new MonsterTemplate(
                8, "bat", 50, 6, 2, 4, List.of(20, 21), List.of(30, 31, 32),
                List.of(40), 24, 25);

        var reader = writer.monsterResource(3, List.of(dart), List.of(template)).reader();
        assertEquals(4, reader.readByte());
        assertEquals(3, reader.readByte());
        assertEquals(1, reader.readShort());
        assertEquals(4, reader.readShort());
        assertEquals(true, reader.readBoolean());
        assertPhase(reader, light);
        assertPhase(reader, bullet);
        assertPhase(reader, explode);
        assertEquals(1, reader.readShort());
        assertEquals(template.id(), reader.readShort());
        assertEquals(template.name(), reader.readUtf());
        assertEquals(template.rangeMove(), reader.readShort());
        assertEquals(template.speed(), reader.readByte());
        assertEquals(template.type(), reader.readByte());
        assertEquals(template.dartId(), reader.readByte());
        assertEquals(template.iconsMove().size(), reader.readByte());
        assertEquals(template.iconsMove().get(0), reader.readShort());
        assertEquals(template.iconsMove().get(1), reader.readShort());
        assertEquals(template.iconsInjure().size(), reader.readByte());
        for (int icon : template.iconsInjure()) {
            assertEquals(icon, reader.readShort());
        }
        assertEquals(template.iconsAttack().size(), reader.readByte());
        for (int icon : template.iconsAttack()) {
            assertEquals(icon, reader.readShort());
        }
        assertEquals(template.w(), reader.readShort());
        assertEquals(template.h(), reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesLevelFrameAndIconPackets() throws Exception {
        var levelReader = writer.levelResource(0, List.of(new LevelTemplate(2, "level", 99L))).reader();
        assertEquals(6, levelReader.readByte());
        assertEquals(0, levelReader.readByte());
        assertEquals(1, levelReader.readShort());
        assertEquals(2, levelReader.readShort());
        assertEquals("level", levelReader.readUtf());
        assertEquals(99L, levelReader.readLong());
        assertEquals(0, levelReader.remaining());

        FrameTemplate frame = new FrameTemplate(
                3, 0, 4, 5, List.of(6), List.of(7), List.of(8),
                9, 10, 11, 12, Map.of(1, 13), 14, 15, 16, 17);
        var frameReader = writer.frameResource(1, List.of(frame)).reader();
        assertEquals(7, frameReader.readByte());
        assertEquals(1, frameReader.readByte());
        assertEquals(1, frameReader.readShort());
        assertEquals(frame.id(), frameReader.readShort());
        assertEquals(frame.hpBar(), frameReader.readShort());
        assertEquals(frame.chat(), frameReader.readShort());
        assertEquals(1, frameReader.readByte());
        assertEquals(6, frameReader.readShort());
        assertEquals(1, frameReader.readByte());
        assertEquals(7, frameReader.readShort());
        assertEquals(1, frameReader.readByte());
        assertEquals(8, frameReader.readShort());
        assertEquals(9, frameReader.readShort());
        assertEquals(10, frameReader.readShort());
        assertEquals(11, frameReader.readShort());
        assertEquals(12, frameReader.readShort());
        assertEquals(1, frameReader.readByte());
        assertEquals(1, frameReader.readByte());
        assertEquals(13, frameReader.readShort());
        assertEquals(14, frameReader.readShort());
        assertEquals(15, frameReader.readShort());
        assertEquals(16, frameReader.readShort());
        assertEquals(17, frameReader.readShort());
        assertEquals(0, frameReader.remaining());

        byte[] bytes = {1, 2, 3};
        var iconReader = writer.icon(77, bytes).reader();
        assertEquals(77, iconReader.readShort());
        assertEquals(3, iconReader.readInt());
        assertArrayEquals(bytes, iconReader.readBytes(3));
        assertEquals(0, iconReader.remaining());
    }

    @Test
    void rejectsCountValuesThatWouldBeTruncated() {
        EffectImage effect = new EffectImage(1, 0, 0, 0,
                java.util.Collections.nCopies(128, 1));
        assertThrows(IOException.class, () -> writer.effectResource(1, List.of(effect)));
        assertThrows(IOException.class, () -> writer.effectResource(1,
                java.util.Collections.nCopies(Short.MAX_VALUE + 1, effect)));
        assertThrows(IOException.class, () -> writer.iconManifest(
                java.util.Collections.nCopies(Short.MAX_VALUE + 1, new IconFingerprint(1, 1L))));
    }

    private static void assertPhase(com.project.game.network.message.MessageReader reader,
                                     MonsterDart.Phase phase) throws Exception {
        assertEquals(phase.icons().size(), reader.readByte());
        for (int icon : phase.icons()) {
            assertEquals(icon, reader.readShort());
        }
        assertEquals(phase.dx(), reader.readShort());
        assertEquals(phase.dy(), reader.readShort());
        assertEquals(phase.delay(), reader.readShort());
    }

    private static MonsterDart validDart() {
        return new MonsterDart(1, false, validPhase(), validPhase(), validPhase());
    }

    private static MonsterDart.Phase validPhase() {
        return new MonsterDart.Phase(List.of(1), 0, 0, 0);
    }

    private static MonsterTemplate validTemplate() {
        return new MonsterTemplate(
                1, "bat", 1, 1, 1, 1, List.of(1),
                List.of(1), List.of(1), 1, 1);
    }

    private static FrameTemplate validFrame() {
        return new FrameTemplate(
                1, 0, 1, 1, List.of(1, 2, 3), List.of(1, 2, 3), List.of(1, 2, 3),
                1, 1, 1, 1, Map.of(1, 1), 1, 1, 1, 1);
    }

}

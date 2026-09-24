package com.project.game.network.packet;
import com.project.game.testsupport.TestPlayers;

import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.player.Player;
import com.project.game.resource.SkillTemplate;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerPacketWriterTest {
    @Test
    void usesLegacyCommandIds() {
        PlayerPacketWriter writer = new PlayerPacketWriter();
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);

        assertEquals(MessageName.ADD_PLAYER, writer.addPlayer(player).command());
        assertEquals(MessageName.REMOVE_PLAYER, writer.removePlayer(player.id()).command());
        assertEquals(MessageName.PLAYER_MOVE, writer.movePlayer(player.id(), 1260, 640).command());
        assertEquals(MessageName.PLAYER_INFO, writer.potentialUpdate(11L).command());
    }

    @Test
    void serializesRemovePlayerAsOnlyPlayerId() throws Exception {
        Message message = new PlayerPacketWriter().removePlayer(0x01020304);
        var reader = message.reader();

        assertEquals(0x01020304, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesAuthoritativePotentialUpdate() throws Exception {
        Message message = new PlayerPacketWriter().potentialUpdate(0x0102030405060708L);

        assertEquals(MessageName.PLAYER_INFO, message.command());

        var reader = message.reader();
        assertEquals(62, reader.readByte());
        assertEquals(0x0102030405060708L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesMeDiePacketExactly() throws Exception {
        Message packet = new PlayerPacketWriter().meDie(321, 654);

        assertEquals(MessageName.ME_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(321, reader.readShort());
        assertEquals(654, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesPlayerDiePacketExactly() throws Exception {
        Message packet = new PlayerPacketWriter().playerDie(77, 321, 654);

        assertEquals(MessageName.PLAYER_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(77, reader.readInt());
        assertEquals(321, reader.readShort());
        assertEquals(654, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void writesWakeUpFromDiePacketExactly() throws Exception {
        Player player = TestPlayers.initial(77L, 77, "wake1", 0);
        player.revive(0, 0, 1250, 648);

        Message packet = new PlayerPacketWriter().wakeUpFromDie(player);

        assertEquals(MessageName.WAKE_UP_FROM_DIE, packet.command());
        var reader = packet.reader();
        assertEquals(77, reader.readInt());
        assertEquals(1250, reader.readShort());
        assertEquals(648, reader.readShort());
        assertEquals(player.currentStats().maxHp(), reader.readLong());
        assertEquals(player.currentStats().maxMp(), reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesOtherPlayerMovement() throws Exception {
        Message message = new PlayerPacketWriter().movePlayer(0x01020304, 1260, 640);
        var reader = message.reader();

        assertEquals(0x01020304, reader.readInt());
        assertEquals(1260, reader.readShort());
        assertEquals(640, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesCanonicalNormalPlayerPayload() throws Exception {
        Player player = TestPlayers.initial(1L, 0x01020304, "alpha1", 0);
        Message message = new PlayerPacketWriter().addPlayer(player);
        var reader = message.reader();

        assertEquals(player.id(), reader.readInt());
        assertEquals(player.name(), reader.readUtf());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(player.appearance().head(), reader.readShort());
        assertEquals(player.appearance().body(), reader.readShort());
        assertEquals(player.appearance().mount(), reader.readShort());
        assertEquals(player.appearance().bag(), reader.readShort());
        assertEquals(player.appearance().medal(), reader.readShort());
        assertEquals(player.appearance().aura(), reader.readShort());
        assertEquals(player.x(), reader.readShort());
        assertEquals(player.y(), reader.readShort());
        assertEquals(player.currentStats().maxHp(), reader.readLong());
        assertEquals(player.hp(), reader.readLong());
        assertEquals(0, reader.readByte()); // normal typePk
        assertEquals(0, reader.readByte()); // normal typeFlag
        assertEquals(player.level(), reader.readShort());
        assertEquals(player.appearance().spaceship(), reader.readByte());
        assertEquals(player.currentStats().speed(), reader.readByte());
        assertEquals(-1, reader.readInt()); // no clan
        assertEquals(-1, reader.readByte()); // old upgrade/levelEquip sentinel
        assertEquals(0, reader.readByte()); // no runtime effects
        assertEquals(0, reader.remaining());
    }

    @Test
    void serializesCanonicalPlayerInfoWithActiveSkillWireShape() throws Exception {
        Player player = TestPlayers.initial(1L, 7, "alpha1", 0);
        List<SkillTemplate> skills = GameResources
                .fromRoots(null, Path.of("resources", "json"))
                .playerSkills(0);

        Message message = new PlayerPacketWriter().playerInfo(player, skills);

        assertEquals(MessageName.PLAYER_INFO, message.command());
        var reader = message.reader();
        assertEquals(0, reader.readByte());
        assertEquals(player.id(), reader.readInt());
        assertEquals(player.name(), reader.readUtf());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(player.power(), reader.readLong());
        assertEquals(player.potential(), reader.readLong());
        assertEquals(player.level(), reader.readShort());
        assertEquals(1, reader.readShort());
        assertEquals(player.appearance().head(), reader.readShort());
        assertEquals(player.appearance().body(), reader.readShort());
        assertEquals(player.appearance().mount(), reader.readShort());
        assertEquals(player.appearance().bag(), reader.readShort());
        assertEquals(player.appearance().medal(), reader.readShort());
        assertEquals(player.appearance().aura(), reader.readShort());
        assertEquals(player.baseStats().damage(), reader.readInt());
        assertEquals(player.baseStats().hp(), reader.readInt());
        assertEquals(player.baseStats().mp(), reader.readInt());
        assertEquals(player.baseStats().constitution(), reader.readInt());
        assertEquals(10L, reader.readLong());
        assertEquals(10L, reader.readLong());
        assertEquals(10L, reader.readLong());
        assertEquals(10L, reader.readLong());
        assertEquals(player.currentStats().maxHp(), reader.readLong());
        assertEquals(player.currentStats().maxMp(), reader.readLong());
        assertEquals(player.hp(), reader.readLong());
        assertEquals(player.mp(), reader.readLong());
        assertEquals(player.currentStats().speed(), reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(player.currentStats().dodge() + "%", reader.readUtf());
        assertEquals(player.currentStats().critical() + "%", reader.readUtf());
        assertEquals("0%", reader.readUtf());
        assertEquals("0%", reader.readUtf());
        assertEquals("0%", reader.readUtf());
        assertEquals("0%", reader.readUtf());
        assertEquals(player.currentStats().damage(), reader.readLong());
        assertEquals(player.coin(), reader.readLong());
        assertEquals(player.coinLock(), reader.readLong());
        assertEquals(player.diamond(), reader.readInt());
        assertEquals(player.ruby(), reader.readInt());
        assertEquals(player.appearance().spaceship(), reader.readByte());

        assertEquals(11, reader.readByte());
        for (SkillTemplate skill : skills) {
            assertSkillWire(reader, skill);
        }
        assertEquals(6, reader.readByte());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(-1, reader.readByte());
        assertEquals(player.gender(), reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    private static void assertSkillWire(com.project.game.network.message.MessageReader reader,
                                         SkillTemplate skill) throws Exception {
        assertEquals(skill.id(), reader.readByte());
        assertEquals(skill.names().size(), reader.readByte());
        for (String name : skill.names()) {
            assertEquals(name, reader.readUtf());
        }
        assertEquals(skill.descriptions().size(), reader.readByte());
        for (String description : skill.descriptions()) {
            assertEquals(description, reader.readUtf());
        }
        assertEquals(skill.type(), reader.readByte());
        assertEquals(skill.proactive(), reader.readBoolean());
        assertEquals(skill.icons().size(), reader.readByte());
        for (int icon : skill.icons()) {
            assertEquals(icon, reader.readShort());
        }
        assertMatrix(reader, skill.dx());
        assertMatrix(reader, skill.dy());
        assertEquals(skill.levelRequire(), reader.readShort());
        assertEquals(skill.maxLevel(), reader.readByte());
        assertEquals(skill.maxUpgrade(), reader.readByte());
        assertEquals(skill.pointUpgrade().size(), reader.readByte());
        for (int point : skill.pointUpgrade()) {
            assertEquals(point, reader.readInt());
        }
        assertMatrixInts(reader, skill.coolDown());
        assertEquals(skill.typeMana(), reader.readByte());
        assertMatrixInts(reader, skill.mana());
        assertEquals(skill.options().size(), reader.readByte());
        for (SkillTemplate.Option option : skill.options()) {
            assertEquals(option.id(), reader.readByte());
            assertEquals(option.name(), reader.readUtf());
            assertIntList(reader, option.normal(), true);
            assertIntList(reader, option.upgrade(), true);
        }
        assertEquals(skill.level(), reader.readByte());
        assertEquals(skill.upgrade(), reader.readByte());
        assertEquals(skill.point(), reader.readInt());
        assertEquals(skill.cooldownReduction(), reader.readByte());
        if (skill.level() > 0 && skill.proactive()) {
            assertEquals(skill.timeCanUse(), reader.readLong());
        }
        assertEquals(skill.paints().size(), reader.readByte());
        for (SkillTemplate.Paint paint : skill.paints()) {
            assertEquals(paint.percent(), reader.readUtf());
            assertEquals(paint.paintId(), reader.readShort());
        }
    }

    private static void assertMatrix(com.project.game.network.message.MessageReader reader,
                                      List<List<Integer>> matrix) throws Exception {
        assertEquals(matrix.size(), reader.readByte());
        for (List<Integer> row : matrix) {
            assertIntList(reader, row, true);
        }
    }

    private static void assertMatrixInts(com.project.game.network.message.MessageReader reader,
                                         List<List<Integer>> matrix) throws Exception {
        assertEquals(matrix.size(), reader.readByte());
        for (List<Integer> row : matrix) {
            assertIntList(reader, row, false);
        }
    }

    private static void assertIntList(com.project.game.network.message.MessageReader reader,
                                      List<Integer> values, boolean shorts) throws Exception {
        assertEquals(values.size(), reader.readByte());
        for (int value : values) {
            assertEquals(value, shorts ? reader.readShort() : reader.readInt());
        }
    }

    @Test
    void rejectsValuesThatWouldBeTruncatedByLegacyAddPlayerWire() {
        PlayerPacketWriter writer = new PlayerPacketWriter();

        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(Short.MAX_VALUE + 1, 12, 0, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, Byte.MAX_VALUE + 1, 0, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, 12, Byte.MAX_VALUE + 1, 1250, 648)));
        assertThrows(IllegalArgumentException.class,
                () -> writer.addPlayer(playerWith(1, 12, 0, Short.MAX_VALUE + 1, 648)));
    }

    private static Player playerWith(
            int level, int speed, int spaceship, int x, int y) {
        var base = new com.project.game.player.BaseStats(200, 200, 10, 0, 0, 0, 5, speed);
        var current = new com.project.game.player.CurrentStats(
                200, 200, 10, 0, 0, 0, 5, speed);
        return new Player(
                7, 1L, "alpha1", 0, 1L, 1L, level, 0L,
                base, current, 200, 200,
                new com.project.game.player.Appearance(5, 6, -1, -1, -1, -1, spaceship),
                0L, 10_000L, 0, 25, 0, 0, x, y);
    }
}

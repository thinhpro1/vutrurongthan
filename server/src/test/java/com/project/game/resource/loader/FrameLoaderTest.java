package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameLoaderTest {
    @Test
    void loadsRequiredFramesFromCanonicalClientResource() {
        var frames = FrameLoader.load(Path.of("resources", "json"));

        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                frames.stream().map(frame -> frame.id()).toList());

        assertFrame(frames.get(0), 3, 0, 8779, 8780, 7645, 8741, 8743,
                8750, 8749, 8751, 8763, 8752);
        assertFrame(frames.get(1), 4, 0, 8339, 8340, 7641, 8301, 8303,
                8310, 8309, 8311, 8323, 8312);
        assertFrame(frames.get(2), 5, 0, 8379, 8380, 7643, 8341, 8343,
                8350, 8349, 8351, 8363, 8352);
        assertFrame(frames.get(3), 6, 1, -1, -1, -1, 7159, 7161,
                7168, 7167, 7169, 7181, 7170);
        assertFrame(frames.get(4), 7, 1, -1, -1, -1, 8111, 8113,
                8120, 8119, 8121, 8133, 8122);
        assertFrame(frames.get(5), 8, 1, -1, -1, -1, 7921, 7923,
                7930, 7929, 7931, 7943, 7932);
        assertFrame(frames.get(6), 21, 1, -1, -1, -1, 10580, 10582,
                10589, 10588, 10590, 10602, 10591);
        assertFrame(frames.get(7), 22, 1, -1, -1, -1, 10542, 10544,
                10551, 10550, 10552, 10564, 10553);
        assertFrame(frames.get(8), 23, 1, -1, -1, -1, 10504, 10506,
                10513, 10512, 10514, 10526, 10515);
    }

    @Test
    void ignoresUnknownJsonNullValuesWhenLoadingFrames(@TempDir Path root) throws IOException {
        String frame = "{\"type\":0,\"hp_bar\":1,\"chat\":2,\"dead\":[3,4],"
                + "\"stand\":[5,6],\"run\":[7,8,9,10,11,12],\"fly\":13,"
                + "\"jump\":14,\"fall\":15,\"injure\":16,\"action\":{\"11\":17},"
                + "\"dx\":0,\"dy\":0,\"width\":66,\"height\":90,\"metadata\":null}";
        String json = "{\"3\":" + frame + ",\"4\":" + frame + ",\"5\":" + frame
                + ",\"6\":" + frame + ",\"7\":" + frame + ",\"8\":" + frame
                + ",\"21\":" + frame + ",\"22\":" + frame + ",\"23\":" + frame + "}";
        Files.writeString(root.resolve("Frame.json"), json);

        var frames = FrameLoader.load(root);

        assertEquals(List.of(3, 4, 5, 6, 7, 8, 21, 22, 23),
                frames.stream().map(frameValue -> frameValue.id()).toList());
    }

    @Test
    void missingFrameRootIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameLoader.load(Path.of("resources", "missing")));
    }

    private static void assertFrame(com.project.game.resource.FrameTemplate frame, int id,
                                    int type, int hpBar, int chat, int deadStart,
                                    int standStart, int runStart, int fly, int jump,
                                    int fall, int injure, int actionStart) {
        assertEquals(id, frame.id());
        assertEquals(type, frame.type());
        assertEquals(hpBar, frame.hpBar());
        assertEquals(chat, frame.chat());
        assertEquals(deadStart < 0 ? List.of(-1, -1) : List.of(deadStart, deadStart + 1),
                frame.dead());
        assertEquals(List.of(standStart, standStart + 1), frame.stand());
        assertEquals(range(runStart, 6), frame.run());
        assertEquals(fly, frame.fly());
        assertEquals(jump, frame.jump());
        assertEquals(fall, frame.fall());
        assertEquals(injure, frame.injure());
        assertEquals(actionRange(actionStart), frame.action());
        assertEquals(0, frame.dx());
        assertEquals(0, frame.dy());
        assertEquals(66, frame.width());
        assertEquals(90, frame.height());
    }

    private static List<Integer> range(int start, int count) {
        List<Integer> values = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            values.add(start + offset);
        }
        return values;
    }

    private static Map<Integer, Integer> actionRange(int firstIcon) {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (int actionId = 11; actionId <= 37; actionId++) {
            values.put(actionId, firstIcon + actionId - 11);
        }
        return values;
    }
}

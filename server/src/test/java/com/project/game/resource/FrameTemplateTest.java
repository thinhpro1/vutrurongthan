package com.project.game.resource;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameTemplateTest {
    @Test
    void rejectsDeadIconCountThatDoesNotFitLegacyByte() {
        assertThrows(IllegalArgumentException.class, () -> frame(
                Collections.nCopies(Byte.MAX_VALUE + 1, 1), List.of(1), List.of(1), Map.of(1, 1)));
    }

    @Test
    void rejectsStandIconCountThatDoesNotFitLegacyByte() {
        assertThrows(IllegalArgumentException.class, () -> frame(
                List.of(1), Collections.nCopies(Byte.MAX_VALUE + 1, 1), List.of(1), Map.of(1, 1)));
    }

    @Test
    void rejectsRunIconCountThatDoesNotFitLegacyByte() {
        assertThrows(IllegalArgumentException.class, () -> frame(
                List.of(1), List.of(1), Collections.nCopies(Byte.MAX_VALUE + 1, 1), Map.of(1, 1)));
    }

    @Test
    void rejectsActionCountThatDoesNotFitLegacyByte() {
        Map<Integer, Integer> actions = new LinkedHashMap<>();
        for (int actionId = Byte.MIN_VALUE; actionId <= Byte.MAX_VALUE; actionId++) {
            actions.put(actionId, 1);
        }

        assertThrows(IllegalArgumentException.class, () -> frame(
                List.of(1), List.of(1), List.of(1), actions));
    }

    private static FrameTemplate frame(
            List<Integer> dead,
            List<Integer> stand,
            List<Integer> run,
            Map<Integer, Integer> action) {
        return new FrameTemplate(
                1, 0, 1, 1, dead, stand, run,
                1, 1, 1, 1, action, 1, 1, 1, 1);
    }
}

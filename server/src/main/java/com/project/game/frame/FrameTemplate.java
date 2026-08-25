package com.project.game.frame;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable legacy Frame definition matching the Unity type=7 field shape. */
public record FrameTemplate(
        int id,
        int type,
        int hpBar,
        int chat,
        List<Integer> dead,
        List<Integer> stand,
        List<Integer> run,
        int fly,
        int jump,
        int fall,
        int injure,
        Map<Integer, Integer> action,
        int dx,
        int dy,
        int width,
        int height) {

    public FrameTemplate {
        dead = immutableList(dead, "dead");
        stand = immutableList(stand, "stand");
        run = immutableList(run, "run");
        action = immutableAction(action);
        validateCount(dead.size(), "dead");
        validateCount(stand.size(), "stand");
        validateCount(run.size(), "run");
        validateCount(action.size(), "action");
        validateShort(id, "id");
        validateShort(hpBar, "hpBar");
        validateShort(chat, "chat");
        dead.forEach(iconId -> validateShort(iconId, "dead icon"));
        stand.forEach(iconId -> validateShort(iconId, "stand icon"));
        run.forEach(iconId -> validateShort(iconId, "run icon"));
        validateShort(fly, "fly");
        validateShort(jump, "jump");
        validateShort(fall, "fall");
        validateShort(injure, "injure");
        action.forEach((actionId, iconId) -> {
            if (actionId < Byte.MIN_VALUE || actionId > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("action id must fit signed byte: " + actionId);
            }
            validateShort(iconId, "action icon");
        });
        validateShort(dx, "dx");
        validateShort(dy, "dy");
        validateShort(width, "width");
        validateShort(height, "height");
    }

    private static List<Integer> immutableList(List<Integer> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field));
    }

    private static Map<Integer, Integer> immutableAction(Map<Integer, Integer> values) {
        Objects.requireNonNull(values, "action");
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static void validateCount(int count, String field) {
        if (count > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(field + " count must fit signed byte: " + count);
        }
    }

    private static void validateShort(int value, String field) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must fit signed short: " + value);
        }
    }
}

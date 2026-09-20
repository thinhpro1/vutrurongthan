package com.project.game.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SkillTemplate(
        int id,
        List<String> names,
        List<String> descriptions,
        int type,
        boolean proactive,
        List<Integer> icons,
        List<List<Integer>> dx,
        List<List<Integer>> dy,
        int levelRequire,
        int maxLevel,
        int maxUpgrade,
        List<Integer> pointUpgrade,
        List<List<Integer>> coolDown,
        int typeMana,
        List<List<Integer>> mana,
        List<SkillTemplate.Option> options,
        int level,
        int upgrade,
        int point,
        int cooldownReduction,
        long timeCanUse,
        List<SkillTemplate.Paint> paints
) {
    public SkillTemplate {
        names = immutableStrings(names);
        descriptions = immutableStrings(descriptions);
        icons = List.copyOf(Objects.requireNonNull(icons, "icons"));
        dx = immutableMatrix(dx);
        dy = immutableMatrix(dy);
        pointUpgrade = List.copyOf(Objects.requireNonNull(pointUpgrade, "pointUpgrade"));
        coolDown = immutableMatrix(coolDown);
        mana = immutableMatrix(mana);
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        paints = List.copyOf(Objects.requireNonNull(paints, "paints"));
    }

    private static List<String> immutableStrings(List<String> values) {
        return List.copyOf(Objects.requireNonNull(values, "values"));
    }

    private static List<List<Integer>> immutableMatrix(List<List<Integer>> values) {
        Objects.requireNonNull(values, "values");
        List<List<Integer>> copy = new ArrayList<>(values.size());
        for (List<Integer> row : values) {
            copy.add(List.copyOf(Objects.requireNonNull(row, "matrix row")));
        }
        return List.copyOf(copy);
    }
    public record Option(
            int id,
            String name,
            List<Integer> normal,
            List<Integer> upgrade
    ) {
        public Option {
            Objects.requireNonNull(name, "name");
            normal = List.copyOf(Objects.requireNonNull(normal, "normal"));
            upgrade = List.copyOf(Objects.requireNonNull(upgrade, "upgrade"));
        }
    }

    public record Paint(String percent, int paintId) {
        public Paint {
            Objects.requireNonNull(percent, "percent");
        }
    }}

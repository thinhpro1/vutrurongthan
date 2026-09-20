package com.project.game.player;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Single Java-code boundary for fresh-player gameplay initialization. */
public final class PlayerProfileFactory {
    private static final Pattern PLAYER_NAME = Pattern.compile("^[a-z0-9]{5,10}$");
    private static final long INITIAL_POWER = 1L;
    private static final long INITIAL_POTENTIAL = 1L;
    private static final int INITIAL_LEVEL = 1;
    private static final long INITIAL_EXP = 0L;
    private static final int INITIAL_BASE_HP = 200;
    private static final int INITIAL_BASE_MP = 200;
    private static final int INITIAL_BASE_DAMAGE = 10;
    private static final int INITIAL_BASE_ARMOR = 0;
    private static final int INITIAL_BASE_CRITICAL = 0;
    private static final int INITIAL_BASE_DODGE = 0;
    private static final int INITIAL_BASE_CONSTITUTION = 5;
    private static final int INITIAL_BASE_SPEED = 12;

    public PlayerProfile create(long accountId, String name, int gender) {
        return createWithId(0, accountId, name, gender);
    }

    public PlayerProfile createWithId(int id, long accountId, String name, int gender) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        String normalized = normalize(name);
        if (!PLAYER_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("player name must match [a-z0-9]{5,10}");
        }
        BaseStats base = switch (gender) {
            case 0 -> new BaseStats(
                    INITIAL_BASE_HP, INITIAL_BASE_MP, INITIAL_BASE_DAMAGE,
                    INITIAL_BASE_ARMOR, INITIAL_BASE_CRITICAL, INITIAL_BASE_DODGE,
                    INITIAL_BASE_CONSTITUTION, INITIAL_BASE_SPEED);
            case 1 -> new BaseStats(
                    INITIAL_BASE_HP, INITIAL_BASE_MP, INITIAL_BASE_DAMAGE,
                    INITIAL_BASE_ARMOR, INITIAL_BASE_CRITICAL, INITIAL_BASE_DODGE,
                    INITIAL_BASE_CONSTITUTION, INITIAL_BASE_SPEED);
            case 2 -> new BaseStats(
                    INITIAL_BASE_HP, INITIAL_BASE_MP, INITIAL_BASE_DAMAGE,
                    INITIAL_BASE_ARMOR, INITIAL_BASE_CRITICAL, INITIAL_BASE_DODGE,
                    INITIAL_BASE_CONSTITUTION, INITIAL_BASE_SPEED);
            default -> throw new IllegalArgumentException("gender must be 0..2");
        };
        CurrentStats current = new CurrentStats(
                base.hp(), base.mp(), base.damage(), base.armor(), base.critical(),
                base.dodge(), base.constitution(), base.speed());
        Appearance appearance = switch (gender) {
            case 0 -> new Appearance(5, 6, -1, -1, -1, -1, 0);
            case 1 -> new Appearance(3, 7, -1, -1, -1, -1, 0);
            case 2 -> new Appearance(4, 8, -1, -1, -1, -1, 0);
            default -> throw new IllegalArgumentException("gender must be 0..2");
        };
        return new PlayerProfile(
                id,
                accountId,
                normalized,
                gender,
                INITIAL_POWER,
                INITIAL_POTENTIAL,
                INITIAL_LEVEL,
                INITIAL_EXP,
                base,
                current,
                current.maxHp(),
                current.maxMp(),
                appearance,
                0L,
                10_000L,
                0,
                25,
                0,
                0,
                1250,
                648);
    }

    private static String normalize(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }
}

package com.project.game.player;

public record PlayerProfile(
        String accountName,
        int id,
        String name,
        int gender,
        long power,
        long potential,
        int level,
        int pointSkill,
        int head,
        int body,
        int mount,
        int bag,
        int medal,
        int aura,
        int baseDamage,
        int baseHp,
        int baseMp,
        int baseConstitution,
        long potentialUpDamage,
        long potentialUpHp,
        long potentialUpMp,
        long potentialUpConstitution,
        long maxHp,
        long maxMp,
        long hp,
        long mp,
        int speed,
        int pointPk,
        int pointActivity,
        int countBarrack,
        String dodge,
        String critical,
        String reduceDamage,
        String bloodsucking,
        String manaSucking,
        String strikeBack,
        long damage,
        long coin,
        long coinLock,
        int diamond,
        int ruby,
        int spaceship,
        int mapId,
        int zoneId,
        int x,
        int y
) {
    public static PlayerProfile initial(String accountName, int id, String name, int gender) {
        int head = switch (gender) {
            case 0 -> 5;
            case 1 -> 3;
            case 2 -> 4;
            default -> throw new IllegalArgumentException("gender must be 0..2");
        };
        int body = switch (gender) {
            case 0 -> 6;
            case 1 -> 7;
            case 2 -> 8;
            default -> throw new IllegalArgumentException("gender must be 0..2");
        };
        return new PlayerProfile(accountName, id, name, gender,
                1, 1, 1, 1,
                head, body, -1, -1, -1, -1,
                10, 5, 5, 5,
                10, 10, 10, 10,
                150, 150, 100, 100,
                12, 0, 0, 1,
                "0%", "0%", "0%", "0%", "0%", "0%",
                10, 0, 10_000, 0, 25, 0,
                0, 0, 1250, 648);
    }
}

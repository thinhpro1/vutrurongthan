package com.project.game.service;

import com.project.game.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    @Test
    void createPlayerAssignsPositiveIncreasingIds() {
        AuthService auth = registeredAuthWithTwoUsers();

        PlayerProfile first = auth.createPlayer("user01", "alpha1", 0).player();
        PlayerProfile second = auth.createPlayer("user02", "beta22", 1).player();

        assertEquals(1, first.id());
        assertEquals(2, second.id());
    }

    @Test
    void freshPlayerUsesLegacyInitialScalarSnapshot() {
        AuthService auth = registeredAuth();
        PlayerProfile player = auth.createPlayer("user01", "alpha1", 0).player();

        assertEquals(1, player.power());
        assertEquals(1, player.potential());
        assertEquals(1, player.level());
        assertEquals(1, player.pointSkill());

        assertEquals(10, player.baseDamage());
        assertEquals(5, player.baseHp());
        assertEquals(5, player.baseMp());
        assertEquals(5, player.baseConstitution());

        assertEquals(10, player.potentialUpDamage());
        assertEquals(10, player.potentialUpHp());
        assertEquals(10, player.potentialUpMp());
        assertEquals(10, player.potentialUpConstitution());

        assertEquals(150, player.maxHp());
        assertEquals(150, player.maxMp());
        assertEquals(100, player.hp());
        assertEquals(100, player.mp());

        assertEquals(12, player.speed());
        assertEquals(0, player.pointPk());
        assertEquals(0, player.pointActivity());
        assertEquals(1, player.countBarrack());

        assertEquals("0%", player.dodge());
        assertEquals("0%", player.critical());
        assertEquals("0%", player.reduceDamage());
        assertEquals("0%", player.bloodsucking());
        assertEquals("0%", player.manaSucking());
        assertEquals("0%", player.strikeBack());

        assertEquals(10, player.damage());
        assertEquals(0, player.coin());
        assertEquals(10_000, player.coinLock());
        assertEquals(0, player.diamond());
        assertEquals(25, player.ruby());
        assertEquals(0, player.spaceship());
    }

    @Test
    void freshPlayerUsesLegacyGenderParts() {
        AuthService auth = registeredAuthWithTwoUsers();
        PlayerProfile gender0 = auth.createPlayer("user01", "alpha1", 0).player();
        PlayerProfile gender1 = auth.createPlayer("user02", "beta22", 1).player();

        AuthService thirdAuth = registeredAuthFor("user03");
        PlayerProfile gender2 = thirdAuth.createPlayer("user03", "gamma3", 2).player();

        assertEquals(5, gender0.head());
        assertEquals(6, gender0.body());
        assertEquals(3, gender1.head());
        assertEquals(7, gender1.body());
        assertEquals(4, gender2.head());
        assertEquals(8, gender2.body());
        for (PlayerProfile player : new PlayerProfile[]{gender0, gender1, gender2}) {
            assertEquals(-1, player.mount());
            assertEquals(-1, player.bag());
            assertEquals(-1, player.medal());
            assertEquals(-1, player.aura());
        }
    }

    private static AuthService registeredAuth() {
        return registeredAuthFor("user01");
    }

    private static AuthService registeredAuthWithTwoUsers() {
        AuthService auth = registeredAuthFor("user01");
        assertTrue(auth.register("user02", "secret2").success());
        return auth;
    }

    private static AuthService registeredAuthFor(String username) {
        AuthService auth = new AuthService();
        assertTrue(auth.register(username, "secret1").success());
        return auth;
    }
}

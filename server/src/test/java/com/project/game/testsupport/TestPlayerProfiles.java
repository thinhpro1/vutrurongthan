package com.project.game.testsupport;

import com.project.game.player.PlayerInitialProfileFactory;
import com.project.game.player.PlayerProfile;

public final class TestPlayerProfiles {
    private static final PlayerInitialProfileFactory FACTORY = new PlayerInitialProfileFactory();

    private TestPlayerProfiles() {
    }

    public static PlayerProfile initial(long accountId, int id, String name, int gender) {
        return FACTORY.createWithId(id, accountId, name, gender);
    }
}

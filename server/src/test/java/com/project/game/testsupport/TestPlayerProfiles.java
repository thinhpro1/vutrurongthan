package com.project.game.testsupport;

import com.project.game.player.PlayerProfileFactory;
import com.project.game.player.PlayerProfile;

public final class TestPlayerProfiles {
    private static final PlayerProfileFactory FACTORY = new PlayerProfileFactory();

    private TestPlayerProfiles() {
    }

    public static PlayerProfile initial(long accountId, int id, String name, int gender) {
        return FACTORY.createWithId(id, accountId, name, gender);
    }
}

package com.project.game.combat;

import com.project.game.map.ZoneRegistry;
import com.project.game.monster.MonsterRuntimeFactory;
import com.project.game.network.packet.MonsterPacketWriter;
import com.project.game.network.packet.PlayerPacketWriter;
import com.project.game.resource.GameResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CombatServiceTest {
    @Test
    void targetingAndAttackingDoNotCreateAbsentZones() {
        ZoneRegistry zones = new ZoneRegistry(new MonsterRuntimeFactory(GameResources.unavailable()));
        CombatService combat = new CombatService(
                zones, new PlayerPacketWriter(), new MonsterPacketWriter());

        assertFalse(combat.canTargetMonster(null, 0));
        assertFalse(combat.attackMonster(null, 0, 1L));
        assertNull(zones.find(1, 0));
    }
}

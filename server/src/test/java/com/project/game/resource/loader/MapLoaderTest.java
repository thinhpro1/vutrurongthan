package com.project.game.resource.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapLoaderTest {
    @Test
    void loadsCanonicalMapPairAndWaypointTopology() {
        var maps = MapLoader.load(Path.of("resources", "json"), true);

        assertEquals(2, maps.size());
        assertEquals("Núi Paozu", maps.get(0).name());
        assertEquals("Bờ sông Pu", maps.get(1).name());
        assertEquals(1, maps.get(0).waypoints().getFirst().goMap());
        assertEquals(0, maps.get(1).waypoints().getFirst().goMap());
    }
}

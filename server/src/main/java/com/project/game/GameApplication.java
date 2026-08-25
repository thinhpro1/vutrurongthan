package com.project.game;

import com.project.game.network.NetworkServer;

/** Standalone entry point for the new V7 network server. */
public final class GameApplication {
    private GameApplication() {
    }

    public static void main(String[] args) throws Exception {
        NetworkServer server = NetworkServer.fromSystemProperties();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "network-shutdown"));
        server.start();
    }
}

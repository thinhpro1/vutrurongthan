package com.project.game;

import com.project.game.bootstrap.ServerBootstrap;

/** Standalone entry point for the new V7 network server. */
public final class GameApplication {
    private GameApplication() {
    }

    public static void main(String[] args) throws Exception {
        ServerBootstrap bootstrap = ServerBootstrap.fromSystemProperties();
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::stop, "network-shutdown"));
        bootstrap.start();
    }
}

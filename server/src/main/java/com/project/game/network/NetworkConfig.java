package com.project.game.network;

import java.util.Objects;
import java.util.Properties;

/** Client compatibility values loaded once and shared by the network pipeline. */
public record NetworkConfig(String clientVersion, int loginVersion) {
    private static final String DEFAULT_CLIENT_VERSION = "0.9.5";
    private static final int DEFAULT_LOGIN_VERSION = 1;

    public NetworkConfig {
        clientVersion = Objects.requireNonNull(clientVersion, "clientVersion").trim();
        if (clientVersion.isEmpty() || loginVersion < 0 || loginVersion > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("invalid client compatibility configuration");
        }
    }

    public static NetworkConfig defaults() {
        return new NetworkConfig(DEFAULT_CLIENT_VERSION, DEFAULT_LOGIN_VERSION);
    }

    public static NetworkConfig fromProperties(Properties properties) {
        return new NetworkConfig(
                properties.getProperty("game.client.version", DEFAULT_CLIENT_VERSION),
                Integer.parseInt(properties.getProperty("game.client.login-version",
                        Integer.toString(DEFAULT_LOGIN_VERSION))));
    }
}

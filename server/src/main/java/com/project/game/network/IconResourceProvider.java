package com.project.game.network;

import java.util.Optional;

/** Boundary for legacy icon bytes; a missing icon is not a protocol failure. */
@FunctionalInterface
public interface IconResourceProvider {
    IconResourceProvider UNAVAILABLE = iconId -> Optional.empty();

    Optional<byte[]> load(int iconId);
}

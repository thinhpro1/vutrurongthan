package com.project.game.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Development resource access for static legacy data such as numeric-ID icons. */
public final class ResourceService {
    private static final ResourceService UNAVAILABLE = new ResourceService(null);
    private final Path iconRoot;

    private ResourceService(Path iconRoot) {
        this.iconRoot = iconRoot == null ? null : iconRoot.toAbsolutePath().normalize();
    }

    public static ResourceService unavailable() {
        return UNAVAILABLE;
    }

    public static ResourceService fromIconRoot(Path iconRoot) {
        return new ResourceService(Objects.requireNonNull(iconRoot, "iconRoot"));
    }

    public Optional<byte[]> loadIcon(int iconId) {
        if (iconRoot == null) {
            return Optional.empty();
        }
        Path icon = iconRoot.resolve(Integer.toString(iconId) + ".png").normalize();
        if (!icon.startsWith(iconRoot) || !Files.isRegularFile(icon) || !Files.isReadable(icon)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(icon));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}

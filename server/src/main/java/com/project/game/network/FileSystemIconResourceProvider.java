package com.project.game.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** DEV-only numeric-ID icon lookup rooted at one configured directory. */
public final class FileSystemIconResourceProvider implements IconResourceProvider {
    private final Path root;

    public FileSystemIconResourceProvider(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public Optional<byte[]> load(int iconId) {
        Path icon = root.resolve(Integer.toString(iconId) + ".png").normalize();
        if (!icon.startsWith(root) || !Files.isRegularFile(icon) || !Files.isReadable(icon)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(icon));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}

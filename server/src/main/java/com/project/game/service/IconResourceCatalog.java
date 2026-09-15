package com.project.game.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IconResourceCatalog {
    private static final Logger LOGGER = Logger.getLogger(IconResourceCatalog.class.getName());
    private static final Pattern CANONICAL_ICON = Pattern.compile("(0|[1-9][0-9]*)\\.png");

    private record Entry(Path path, long fingerprint) {
    }

    private final List<IconFingerprint> manifest;
    private final Map<Integer, Entry> entries;
    private final ConcurrentHashMap<Integer, byte[]> bytesById = new ConcurrentHashMap<>();

    private IconResourceCatalog(List<IconFingerprint> manifest, Map<Integer, Entry> entries) {
        this.manifest = List.copyOf(manifest);
        this.entries = Map.copyOf(entries);
    }

    public static IconResourceCatalog fromRoot(Path root) {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return new IconResourceCatalog(List.of(), Map.of());
        }

        Map<Integer, Entry> loaded = new HashMap<>();
        try (var paths = Files.list(normalizedRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> addIfCanonical(loaded, path));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot scan icon resources below " + normalizedRoot,
                    exception);
        }

        List<Integer> ids = new ArrayList<>(loaded.keySet());
        ids.sort(Comparator.naturalOrder());
        List<IconFingerprint> manifest = new ArrayList<>(ids.size());
        for (int id : ids) {
            manifest.add(new IconFingerprint(id, loaded.get(id).fingerprint()));
        }
        return new IconResourceCatalog(manifest, loaded);
    }

    public List<IconFingerprint> manifest() {
        return manifest;
    }

    public Optional<byte[]> loadIcon(int iconId) {
        Entry entry = entries.get(iconId);
        if (entry == null) {
            return Optional.empty();
        }

        byte[] cached = bytesById.get(iconId);
        if (cached != null) {
            return Optional.of(cached);
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(entry.path());
        } catch (IOException exception) {
            return Optional.empty();
        }

        if (IconFingerprint.fingerprint64(bytes) != entry.fingerprint()) {
            LOGGER.warning(() -> "ICON_RESOURCE_CHANGED_AFTER_START id=" + iconId
                    + " restartRequired=true");
            return Optional.empty();
        }

        byte[] existing = bytesById.putIfAbsent(iconId, bytes);
        return Optional.of(existing == null ? bytes : existing);
    }

    private static void addIfCanonical(Map<Integer, Entry> entries, Path path) {
        Matcher matcher = CANONICAL_ICON.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }

        int iconId;
        try {
            iconId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return;
        }
        if (iconId < 0 || iconId > Short.MAX_VALUE) {
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            entries.put(iconId, new Entry(path, IconFingerprint.fingerprint64(bytes)));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read icon resource " + path, exception);
        }
    }
}

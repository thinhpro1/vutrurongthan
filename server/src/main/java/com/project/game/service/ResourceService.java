package com.project.game.service;

import com.project.game.frame.FrameTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Development resource access for static legacy data such as numeric-ID icons. */
public final class ResourceService {
    private static final List<Integer> REQUIRED_FRAME_IDS = List.of(3, 4, 5, 21, 22, 23);
    private static final Set<Integer> REQUIRED_FRAME_ID_SET = Set.copyOf(REQUIRED_FRAME_IDS);
    private static final ResourceService UNAVAILABLE = new ResourceService(null, null);
    private final Path iconRoot;
    private final List<FrameTemplate> frames;

    private ResourceService(Path iconRoot, Path frameRoot) {
        this.iconRoot = iconRoot == null ? null : iconRoot.toAbsolutePath().normalize();
        this.frames = frameRoot == null ? List.of() : loadFrames(frameRoot);
    }

    public static ResourceService unavailable() {
        return UNAVAILABLE;
    }

    public static ResourceService fromIconRoot(Path iconRoot) {
        return new ResourceService(Objects.requireNonNull(iconRoot, "iconRoot"), null);
    }

    public static ResourceService fromFrameRoot(Path frameRoot) {
        return new ResourceService(null, Objects.requireNonNull(frameRoot, "frameRoot"));
    }

    public static ResourceService fromRoots(Path iconRoot, Path frameRoot) {
        return new ResourceService(iconRoot, frameRoot);
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

    public List<FrameTemplate> frames() {
        return frames;
    }

    private static List<FrameTemplate> loadFrames(Path frameRoot) {
        Path root = Objects.requireNonNull(frameRoot, "frameRoot").toAbsolutePath().normalize();
        Path source = root.resolve("Frame.json").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("Frame.json is not readable below " + root);
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            Map<Integer, FrameTemplate> parsed = new FrameJsonParser(json).parse();
            List<FrameTemplate> selected = new ArrayList<>(REQUIRED_FRAME_IDS.size());
            for (int id : REQUIRED_FRAME_IDS) {
                FrameTemplate frame = parsed.get(id);
                if (frame == null) {
                    throw new IllegalArgumentException("Frame.json is missing required frame " + id);
                }
                selected.add(frame);
            }
            return List.copyOf(selected);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("cannot read " + source, exception);
        }
    }

    private static final class FrameJsonParser {
        private final String input;
        private int index;

        private FrameJsonParser(String input) {
            this.input = Objects.requireNonNull(input, "input");
        }

        private Map<Integer, FrameTemplate> parse() {
            expect('{');
            Map<Integer, FrameTemplate> frames = new HashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return frames;
            }
            while (true) {
                int id = Integer.parseInt(readString());
                expect(':');
                FrameTemplate frame = readFrame(id);
                if (REQUIRED_FRAME_ID_SET.contains(id)) {
                    frames.put(id, frame);
                }
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return frames;
                }
                expect(',');
            }
        }

        private FrameTemplate readFrame(int id) {
            expect('{');
            int type = 0;
            Integer hpBar = null;
            Integer chat = null;
            List<Integer> dead = null;
            List<Integer> stand = null;
            List<Integer> run = null;
            Integer fly = null;
            Integer jump = null;
            Integer fall = null;
            Integer injure = null;
            Map<Integer, Integer> action = null;
            Integer dx = null;
            Integer dy = null;
            Integer width = null;
            Integer height = null;
            skipWhitespace();
            if (!peek('}')) {
                while (true) {
                    String field = readString();
                    expect(':');
                    switch (field) {
                        case "type" -> type = readInt();
                        case "hp_bar" -> hpBar = readInt();
                        case "chat" -> chat = readInt();
                        case "dead" -> dead = readIntList();
                        case "stand" -> stand = readIntList();
                        case "run" -> run = readIntList();
                        case "fly" -> fly = readInt();
                        case "jump" -> jump = readInt();
                        case "fall" -> fall = readInt();
                        case "injure" -> injure = readInt();
                        case "action" -> action = readIntMap();
                        case "dx" -> dx = readInt();
                        case "dy" -> dy = readInt();
                        case "width" -> width = readInt();
                        case "height" -> height = readInt();
                        default -> skipValue();
                    }
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        break;
                    }
                    expect(',');
                }
            } else {
                index++;
            }
            return new FrameTemplate(id, type, required(hpBar, "hp_bar"), required(chat, "chat"),
                    required(dead, "dead"), required(stand, "stand"), required(run, "run"),
                    required(fly, "fly"), required(jump, "jump"), required(fall, "fall"),
                    required(injure, "injure"), required(action, "action"), required(dx, "dx"),
                    required(dy, "dy"), required(width, "width"), required(height, "height"));
        }

        private List<Integer> readIntList() {
            expect('[');
            List<Integer> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }
            while (true) {
                values.add(readInt());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return values;
                }
                expect(',');
            }
        }

        private Map<Integer, Integer> readIntMap() {
            expect('{');
            Map<Integer, Integer> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return values;
            }
            while (true) {
                int key = Integer.parseInt(readString());
                expect(':');
                values.put(key, readInt());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return values;
                }
                expect(',');
            }
        }

        private void skipValue() {
            skipWhitespace();
            if (peek('{')) {
                index++;
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return;
                }
                while (true) {
                    readString();
                    expect(':');
                    skipValue();
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return;
                    }
                    expect(',');
                }
            }
            if (peek('[')) {
                index++;
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return;
                }
                while (true) {
                    skipValue();
                    skipWhitespace();
                    if (peek(']')) {
                        index++;
                        return;
                    }
                    expect(',');
                }
            }
            if (peek('"')) {
                readString();
            } else {
                readInt();
            }
        }

        private int readInt() {
            skipWhitespace();
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index || (input.charAt(start) == '-' && start + 1 == index)) {
                throw error("expected integer");
            }
            return Integer.parseInt(input.substring(start, index));
        }

        private String readString() {
            skipWhitespace();
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < input.length()) {
                char current = input.charAt(index++);
                if (current == '"') {
                    return value.toString();
                }
                if (current == '\\') {
                    if (index >= input.length()) {
                        throw error("unterminated escape");
                    }
                    char escaped = input.charAt(index++);
                    value.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw error("unsupported escape");
                    });
                } else {
                    value.append(current);
                }
            }
            throw error("unterminated string");
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != expected) {
                throw error("expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char value) {
            return index < input.length() && input.charAt(index) == value;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + index);
        }

        private static <T> T required(T value, String field) {
            return Objects.requireNonNull(value, "missing Frame field " + field);
        }
    }
}

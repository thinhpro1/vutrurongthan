package com.project.game.resource.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class JsonResourceReader {
    private JsonResourceReader() {
    }

    static JsonObject readObject(Path root, String fileName) {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Path source = normalizedRoot.resolve(fileName).normalize();
        if (!source.startsWith(normalizedRoot)
                || !Files.isRegularFile(source)
                || !Files.isReadable(source)) {
            throw new IllegalArgumentException(fileName + " is not readable below " + normalizedRoot);
        }
        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(fileName + " root must be an object");
            }
            return parsed.getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + source, exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("invalid " + fileName + " at " + source, exception);
        }
    }

    static void requireExactFields(JsonObject object, Set<String> expected, String label) {
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(label + " must contain exactly fields " + expected
                    + " but found " + object.keySet());
        }
    }

    static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing resource field " + field);
        }
        return value;
    }

    static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("resource field " + field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static int parseId(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid " + label + " id " + value, exception);
        }
    }

    static int readInt(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        return value.getAsInt();
    }

    static int readStrictInt(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be an integer", exception);
        }
    }

    static long readLong(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        return value.getAsLong();
    }

    static long readStrictLong(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "resource field " + field + " must be an integer", exception);
        }
    }

    static long readLongStrict(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be a long integer", exception);
        }
    }

    static boolean readBoolean(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("resource field " + field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    static String readString(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("resource field " + field + " must be a string");
        }
        return value.getAsString();
    }

    static String readNullableString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("missing resource field " + field);
        }
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("resource field " + field + " must be a string or null");
        }
        return value.getAsString();
    }

    static List<String> readStringList(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<String> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("resource field " + field + " contains a non-string");
            }
            result.add(element.getAsString());
        }
        return result;
    }

    static List<List<Integer>> readIntMatrix(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<List<Integer>> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement row : value.getAsJsonArray()) {
            result.add(readIntList(row, field));
        }
        return result;
    }

    static List<Integer> readIntList(JsonObject object, String field) {
        return readIntList(required(object, field), field);
    }

    static List<Integer> readIntList(JsonElement value, String field) {
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("resource field " + field + " contains a non-number");
            }
            result.add(element.getAsInt());
        }
        return result;
    }

    static List<Integer> readShortList(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("resource field " + field + " must be an array");
        }
        List<Integer> result = new ArrayList<>(value.getAsJsonArray().size());
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("resource field " + field
                        + " contains a non-number");
            }
            result.add(readShortValue(element, field));
        }
        return List.copyOf(result);
    }

    static int readShortValue(JsonObject object, String field) {
        return readShortValue(required(object, field), field);
    }

    static int readShortValue(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("resource field " + field + " must be numeric");
        }
        try {
            long number = new BigDecimal(value.getAsString()).longValueExact();
            if (number < Short.MIN_VALUE || number > Short.MAX_VALUE) {
                throw new IllegalArgumentException("resource field " + field
                        + " must fit signed short: " + number);
            }
            return (int) number;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("resource field " + field
                    + " must be an integer", exception);
        }
    }

    static int readByteValue(JsonObject object, String field) {
        int value = readStrictInt(object, field);
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("resource field " + field
                    + " must fit signed byte: " + value);
        }
        return value;
    }
}

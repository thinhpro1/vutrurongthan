package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterDartLoaderTest {
    private static final Path PRODUCTION_ROOT = Path.of("resources", "json");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Test
    void loadsProductionDartsInAscendingOrderWithImmutablePhases() {
        var darts = MonsterDartLoader.load(PRODUCTION_ROOT, true);

        assertEquals(List.of(0, 1, 2, 3, 4, 5), darts.stream()
                .map(dart -> dart.id()).toList());
        assertEquals(List.of(false, true, false, false, false, true), darts.stream()
                .map(dart -> dart.meteorite()).toList());

        var dart0 = darts.getFirst();
        assertEquals(List.of(2198, 2199, 2200), dart0.light().icons());
        assertEquals(0, dart0.light().dx());
        assertEquals(0, dart0.light().dy());
        assertEquals(30, dart0.light().delay());
        assertEquals(List.of(2190, 2191, 2192), dart0.bullet().icons());
        assertEquals(0, dart0.bullet().dx());
        assertEquals(0, dart0.bullet().dy());
        assertEquals(30, dart0.bullet().delay());
        assertEquals(List.of(2193, 2194, 2195, 2196, 2197), dart0.explode().icons());
        assertEquals(0, dart0.explode().dx());
        assertEquals(0, dart0.explode().dy());
        assertEquals(20, dart0.explode().delay());

        assertThrows(UnsupportedOperationException.class,
                () -> darts.add(dart0));
        assertThrows(UnsupportedOperationException.class,
                () -> dart0.light().icons().add(1));
    }

    @Test
    void missingOptionalFileReturnsEmpty(@TempDir Path root) {
        assertTrue(MonsterDartLoader.load(root, false).isEmpty());
    }

    @Test
    void missingRequiredFileFails(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> MonsterDartLoader.load(root, true));
    }

    @Test
    void rootMustBeObject(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("MonsterDartTemplate.json"), "[]");

        assertThrows(IllegalArgumentException.class,
                () -> MonsterDartLoader.load(root, true));
    }

    @Test
    void rejectsNonCanonicalIdKeys(@TempDir Path root) throws IOException {
        assertRejects(root, rootWithKey("01"));
        assertRejects(root, rootWithKey("-1"));
        assertRejects(root, rootWithKey("+1"));
        assertRejects(root, rootWithKey("1.0"));
        assertRejects(root, rootWithKey("abc"));
    }

    @Test
    void rejectsOutOfRangeAndDuplicateNumericIds(@TempDir Path root) throws IOException {
        assertRejects(root, rootWithKey("32768"));

        JsonObject duplicate = new JsonObject();
        duplicate.add("1", validDart());
        duplicate.add("01", validDart());
        assertRejects(root, duplicate);
    }

    @Test
    void rejectsMissingAndUnexpectedDartFields(@TempDir Path root) throws IOException {
        JsonObject missing = validDart();
        missing.remove("light");
        assertRejects(root, rootWithValue("0", missing));

        JsonObject unexpected = validDart();
        unexpected.addProperty("extra", true);
        assertRejects(root, rootWithValue("0", unexpected));
    }

    @Test
    void requiresBooleanMeteorite(@TempDir Path root) throws IOException {
        JsonObject dart = validDart();
        dart.addProperty("is_meteorite", "false");

        assertRejects(root, rootWithValue("0", dart));
    }

    @Test
    void rejectsMissingAndUnexpectedPhaseFields(@TempDir Path root) throws IOException {
        JsonObject missing = validDart();
        missing.getAsJsonObject("light").remove("delay");
        assertRejects(root, rootWithValue("0", missing));

        JsonObject unexpected = validDart();
        unexpected.getAsJsonObject("light").addProperty("extra", 1);
        assertRejects(root, rootWithValue("0", unexpected));
    }

    @Test
    void validatesPhaseIconArrayAndCount(@TempDir Path root) throws IOException {
        JsonObject notArray = validDart();
        notArray.getAsJsonObject("light").addProperty("icon", 1);
        assertRejects(root, rootWithValue("0", notArray));

        JsonObject empty = validDart();
        empty.getAsJsonObject("light").add("icon", new JsonArray());
        assertRejects(root, rootWithValue("0", empty));

        JsonObject tooMany = validDart();
        JsonArray icons = new JsonArray();
        for (int index = 0; index < 128; index++) {
            icons.add(index);
        }
        tooMany.getAsJsonObject("light").add("icon", icons);
        assertRejects(root, rootWithValue("0", tooMany));
    }

    @Test
    void validatesIconIdsAsNonNegativeSignedShorts(@TempDir Path root) throws IOException {
        JsonObject negative = validDart();
        negative.getAsJsonObject("light").getAsJsonArray("icon").set(0,
                new com.google.gson.JsonPrimitive(-1));
        assertRejects(root, rootWithValue("0", negative));

        JsonObject tooLarge = validDart();
        tooLarge.getAsJsonObject("light").getAsJsonArray("icon").set(0,
                new com.google.gson.JsonPrimitive(32768));
        assertRejects(root, rootWithValue("0", tooLarge));

        JsonObject fractional = validDart();
        fractional.getAsJsonObject("light").getAsJsonArray("icon").set(0,
                new com.google.gson.JsonPrimitive(1.5));
        assertRejects(root, rootWithValue("0", fractional));
    }

    @Test
    void validatesOffsetsAsSignedShorts(@TempDir Path root) throws IOException {
        JsonObject dxLow = validDart();
        dxLow.getAsJsonObject("light").addProperty("dx", -32769);
        assertRejects(root, rootWithValue("0", dxLow));

        JsonObject dxHigh = validDart();
        dxHigh.getAsJsonObject("light").addProperty("dx", 32768);
        assertRejects(root, rootWithValue("0", dxHigh));

        JsonObject dyLow = validDart();
        dyLow.getAsJsonObject("light").addProperty("dy", -32769);
        assertRejects(root, rootWithValue("0", dyLow));

        JsonObject dyHigh = validDart();
        dyHigh.getAsJsonObject("light").addProperty("dy", 32768);
        assertRejects(root, rootWithValue("0", dyHigh));
    }

    @Test
    void validatesNonNegativeDelayAsSignedShort(@TempDir Path root) throws IOException {
        JsonObject negative = validDart();
        negative.getAsJsonObject("light").addProperty("delay", -1);
        assertRejects(root, rootWithValue("0", negative));

        JsonObject tooLarge = validDart();
        tooLarge.getAsJsonObject("light").addProperty("delay", 32768);
        assertRejects(root, rootWithValue("0", tooLarge));

        JsonObject fractional = validDart();
        fractional.getAsJsonObject("light").addProperty("delay", 1.5);
        assertRejects(root, rootWithValue("0", fractional));
    }

    @Test
    void rejectsLexicalIntegralDecimals(@TempDir Path root) throws IOException {
        JsonObject icon = validDart();
        icon.getAsJsonObject("light").getAsJsonArray("icon").set(0,
                new com.google.gson.JsonPrimitive(1.0));
        assertRejects(root, rootWithValue("0", icon));

        JsonObject dx = validDart();
        dx.getAsJsonObject("light").addProperty("dx", 1.0);
        assertRejects(root, rootWithValue("0", dx));

        JsonObject dy = validDart();
        dy.getAsJsonObject("light").addProperty("dy", 1.0);
        assertRejects(root, rootWithValue("0", dy));

        JsonObject delay = validDart();
        delay.getAsJsonObject("light").addProperty("delay", 1.0);
        assertRejects(root, rootWithValue("0", delay));
    }

    private static void assertRejects(Path root, JsonObject value) throws IOException {
        write(root, value);
        assertThrows(IllegalArgumentException.class,
                () -> MonsterDartLoader.load(root, true));
    }

    private static JsonObject rootWithKey(String key) throws IOException {
        return rootWithValue(key, validDart());
    }

    private static JsonObject rootWithValue(String key, JsonObject value) {
        JsonObject root = new JsonObject();
        root.add(key, value);
        return root;
    }

    private static JsonObject validDart() {
        JsonObject dart = new JsonObject();
        dart.add("light", phase(List.of(2198, 2199, 2200), 0, 0, 30));
        dart.add("bullet", phase(List.of(2190, 2191, 2192), 0, 0, 30));
        dart.add("explode", phase(List.of(2193, 2194, 2195, 2196, 2197), 0, 0, 20));
        dart.addProperty("is_meteorite", false);
        return dart;
    }

    private static JsonObject phase(List<Integer> icons, int dx, int dy, int delay) {
        JsonObject phase = new JsonObject();
        JsonArray iconArray = new JsonArray();
        icons.forEach(iconArray::add);
        phase.add("icon", iconArray);
        phase.addProperty("dx", dx);
        phase.addProperty("dy", dy);
        phase.addProperty("delay", delay);
        return phase;
    }

    private static void write(Path root, JsonObject value) throws IOException {
        Files.writeString(root.resolve("MonsterDartTemplate.json"), GSON.toJson(value));
    }
}

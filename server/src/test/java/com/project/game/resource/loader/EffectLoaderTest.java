package com.project.game.resource.loader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.project.game.resource.EffectImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectLoaderTest {
    @Test
    void loadsCanonicalMovementEffects() throws Exception {
        var effects = EffectLoader.load(Path.of("resources", "json"), true);

        assertEquals(List.of(6, 7, 13, 17),
                effects.stream().map(EffectImage::id).toList());
        assertEquals(4, effects.size());
        assertTrue(effects.stream().allMatch(effect -> !effect.icons().isEmpty()));
        assertTrue(effects.stream().allMatch(effect -> effect.icons().size() <= Byte.MAX_VALUE));

        assertEquals(new EffectImage(6, 0, 0, 100, List.of(71, 72)),
                effects.get(0));
        assertEquals(new EffectImage(7, 0, 0, 100, List.of(68, 69, 70)),
                effects.get(1));
        assertEquals(new EffectImage(
                13, 0, 0, 100, List.of(971, 972, 973)), effects.get(2));
        assertEquals(new EffectImage(
                17, 0, -10, 50, List.of(1911, 1912, 1913, 1914)), effects.get(3));
        for (var effect : effects) {
            assertTrue(effect.id() >= Short.MIN_VALUE && effect.id() <= Short.MAX_VALUE);
            assertTrue(effect.dx() >= Short.MIN_VALUE && effect.dx() <= Short.MAX_VALUE);
            assertTrue(effect.dy() >= Short.MIN_VALUE && effect.dy() <= Short.MAX_VALUE);
            assertTrue(effect.delay() >= Short.MIN_VALUE && effect.delay() <= Short.MAX_VALUE);
            assertTrue(effect.icons().stream()
                    .allMatch(id -> id >= Short.MIN_VALUE && id <= Short.MAX_VALUE));
        }
    }

    @Test
    void rejectsEffectBootstrapVersionOne(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.addProperty("version", 1);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapMissingImage13(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").remove(2);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapExtraImage(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        var images = bootstrap.getAsJsonArray("images");
        images.add(images.get(0).deepCopy());
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImageOrder(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        var images = bootstrap.getAsJsonArray("images");
        var first = images.get(0);
        images.set(0, images.get(1));
        images.set(1, first);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage13Dx(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(2).getAsJsonObject().addProperty("dx", 1);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage13Dy(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(2).getAsJsonObject().addProperty("dy", 1);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage13Delay(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(2).getAsJsonObject().addProperty("delay", 50);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage13Icons(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(2).getAsJsonObject().add(
                "icons", JsonParser.parseString("[971,972,974]"));
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage17Dy(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(3).getAsJsonObject().addProperty("dy", -9);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage17Delay(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(3).getAsJsonObject().addProperty("delay", 100);
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void rejectsEffectBootstrapWrongImage17Icons(@TempDir Path root) throws IOException {
        var bootstrap = canonicalEffectBootstrap();
        bootstrap.getAsJsonArray("images").get(3).getAsJsonObject().add(
                "icons", JsonParser.parseString("[1911,1912,1913,1915]"));
        assertEffectBootstrapRejected(root, bootstrap);
    }

    @Test
    void pinsCanonicalMovementEffectBootstrapHash() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("resources", "json", "EffectBootstrap.json"));
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        assertEquals("229a7b3bf4ce5f9339ba597335e6e813a841edb8fc034d048db1bb6a65815bd1", hash);
    }

    private static com.google.gson.JsonObject canonicalEffectBootstrap() {
        return JsonParser.parseString("{\"version\":2,\"images\":["
                + "{\"id\":6,\"dx\":0,\"dy\":0,\"delay\":100,\"icons\":[71,72]},"
                + "{\"id\":7,\"dx\":0,\"dy\":0,\"delay\":100,\"icons\":[68,69,70]},"
                + "{\"id\":13,\"dx\":0,\"dy\":0,\"delay\":100,\"icons\":[971,972,973]},"
                + "{\"id\":17,\"dx\":0,\"dy\":-10,\"delay\":50,\"icons\":[1911,1912,1913,1914]}"
                + "]}").getAsJsonObject();
    }

    private static void assertEffectBootstrapRejected(
            Path root, com.google.gson.JsonObject bootstrap) throws IOException {
        Files.writeString(root.resolve("EffectBootstrap.json"),
                new GsonBuilder().serializeNulls().create().toJson(bootstrap));
        assertThrows(IllegalArgumentException.class,
                () -> EffectLoader.load(root, true));
    }
}

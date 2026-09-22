package com.project.game.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.project.game.map.MapData;
import com.project.game.map.MapTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapDataLoaderTest {
    private static final Path PRODUCTION_MAP_ROOT = Path.of("resources", "maps");
    private static final MapDataLoader LOADER = new MapDataLoader();

    @Test
    void loadsProductionMapsAndMatchesLegacyStaticFixture() throws Exception {
        Map<Integer, MapTemplate> legacy = MapLoader.load(Path.of("resources", "json"), true);

        assertMatchesLegacy(LOADER.load(PRODUCTION_MAP_ROOT, 1), 1, legacy.get(0),
                "9d27d23a843599772be153cc4c94ca4b19d30403c66cb07457afb2df467a1229");
        assertMatchesLegacy(LOADER.load(PRODUCTION_MAP_ROOT, 2), 2, legacy.get(1),
                "12ab5df64139502d93e61d4049bae5a5ed0639a0bb70623312b082f449ce7365");
    }

    @Test
    void loadsLineCollisionWithClosedBlockAndOpenPlatform(@TempDir Path root) throws IOException {
        write(root, lineFixture());

        MapData map = LOADER.load(root, 1);

        assertEquals(1, map.id());
        assertEquals(MapData.CollisionType.LINE, map.collision().type());
        assertNull(map.collision().data());
        assertEquals(2, map.collision().lines().size());
        MapData.Line block = map.collision().lines().get(0);
        assertEquals(MapData.LineType.BLOCK, block.type());
        assertEquals(List.of(
                new MapData.Point(0, 936),
                new MapData.Point(720, 936),
                new MapData.Point(720, 1152),
                new MapData.Point(4464, 1152),
                new MapData.Point(4464, 1440),
                new MapData.Point(0, 1440),
                new MapData.Point(0, 936)), block.points());
        assertEquals(block.points().getFirst(), block.points().getLast());
        MapData.Line platform = map.collision().lines().get(1);
        assertEquals(MapData.LineType.PLATFORM, platform.type());
        assertEquals(List.of(new MapData.Point(1200, 720), new MapData.Point(1800, 720)),
                platform.points());
        assertFalse(platform.points().getFirst().equals(platform.points().getLast()));
    }

    @Test
    void derivesDimensionsFromEngineTileSize(@TempDir Path root) throws IOException {
        write(root, baseGrid());

        MapData map = LOADER.load(root, 1);

        assertEquals(72, MapData.TILE_SIZE);
        assertEquals(72, map.width());
        assertEquals(72, map.height());
    }

    @Test
    void rejectsMissingAndOutOfRangeDataIds(@TempDir Path root) throws IOException {
        write(root, baseGrid());
        Files.move(root.resolve("1.json"), root.resolve("0.json"));
        assertEquals(0, LOADER.load(root, 0).id());
        assertThrows(IllegalArgumentException.class, () -> LOADER.load(root, -1));
        assertThrows(IllegalArgumentException.class, () -> LOADER.load(root, Short.MAX_VALUE + 1));
        assertThrows(IllegalArgumentException.class, () -> LOADER.load(root, 1));
    }

    @Test
    void rejectsTopLevelAndStrictNumericShapeViolations(@TempDir Path root) throws IOException {
        JsonObject missing = baseGrid();
        missing.remove("terrain");
        assertRejected(root, missing);

        JsonObject unexpected = baseGrid();
        unexpected.addProperty("id", 1);
        assertRejected(root, unexpected);

        JsonObject decimal = baseGrid();
        decimal.addProperty("terrain", 1.0);
        assertRejected(root, decimal);

        JsonObject fractional = baseGrid();
        fractional.addProperty("row", 1.5);
        assertRejected(root, fractional);
    }

    @Test
    void rejectsTerrainDimensionsAndGridDataViolations(@TempDir Path root) throws IOException {
        JsonObject terrainLow = baseGrid();
        terrainLow.addProperty("terrain", -1);
        assertRejected(root, terrainLow);

        JsonObject terrainHigh = baseGrid();
        terrainHigh.addProperty("terrain", 32768);
        assertRejected(root, terrainHigh);

        JsonObject rowZero = baseGrid();
        rowZero.addProperty("row", 0);
        assertRejected(root, rowZero);

        JsonObject columnZero = baseGrid();
        columnZero.addProperty("column", 0);
        assertRejected(root, columnZero);

        JsonObject widthTooLarge = baseGrid();
        widthTooLarge.addProperty("column", 456);
        assertRejected(root, widthTooLarge);

        JsonObject heightTooLarge = baseGrid();
        heightTooLarge.addProperty("row", 456);
        assertRejected(root, heightTooLarge);

        JsonObject gridExtra = baseGrid();
        gridExtra.getAsJsonObject("collision").add("lines", new JsonArray());
        assertRejected(root, gridExtra);

        JsonObject gridMissing = baseGrid();
        gridMissing.getAsJsonObject("collision").remove("data");
        assertRejected(root, gridMissing);

        JsonObject gridLength = baseGrid();
        gridLength.getAsJsonObject("collision").addProperty("data", "00");
        assertRejected(root, gridLength);

        JsonObject gridValue = baseGrid();
        gridValue.getAsJsonObject("collision").addProperty("data", "2");
        assertRejected(root, gridValue);
    }

    @Test
    void rejectsBackgroundShapeAndRangeViolations(@TempDir Path root) throws IOException {
        JsonObject skyLength = baseGrid();
        JsonArray shortSky = color(0, 0, 0);
        shortSky.remove(2);
        skyLength.getAsJsonObject("background").add("skyColor", shortSky);
        assertRejected(root, skyLength);

        JsonObject skyLow = baseGrid();
        skyLow.getAsJsonObject("background").add("skyColor", color(-1, 0, 0));
        assertRejected(root, skyLow);

        JsonObject skyHigh = baseGrid();
        skyHigh.getAsJsonObject("background").add("skyColor", color(256, 0, 0));
        assertRejected(root, skyHigh);

        JsonObject layersCount = baseGrid();
        layersCount.getAsJsonObject("background").getAsJsonArray("layers").remove(2);
        assertRejected(root, layersCount);

        JsonObject layerField = baseGrid();
        layerField.getAsJsonObject("background").getAsJsonArray("layers")
                .get(0).getAsJsonObject().remove("image");
        assertRejected(root, layerField);

        JsonObject imageLow = baseGrid();
        imageLow.getAsJsonObject("background").getAsJsonArray("layers")
                .get(0).getAsJsonObject().addProperty("image", -2);
        assertRejected(root, imageLow);

        JsonObject imageHigh = baseGrid();
        imageHigh.getAsJsonObject("background").getAsJsonArray("layers")
                .get(0).getAsJsonObject().addProperty("image", 32768);
        assertRejected(root, imageHigh);

        JsonObject fillHigh = baseGrid();
        fillHigh.getAsJsonObject("background").getAsJsonArray("layers")
                .get(0).getAsJsonObject().add("fillColor", color(0, 0, 256));
        assertRejected(root, fillHigh);
    }

    @Test
    void rejectsLineShapeAndPointViolations(@TempDir Path root) throws IOException {
        JsonObject lineExtra = lineFixture();
        lineExtra.getAsJsonObject("collision").addProperty("data", "0");
        assertRejected(root, lineExtra);

        JsonObject lineMissing = lineFixture();
        lineMissing.getAsJsonObject("collision").remove("lines");
        assertRejected(root, lineMissing);

        JsonObject emptyLines = lineFixture();
        emptyLines.getAsJsonObject("collision").add("lines", new JsonArray());
        assertRejected(root, emptyLines);

        JsonObject unknownType = lineFixture();
        unknownType.getAsJsonObject("collision").getAsJsonArray("lines")
                .get(0).getAsJsonObject().addProperty("type", "WALL");
        assertRejected(root, unknownType);

        JsonObject pointShape = lineFixture();
        JsonArray shortPoint = new JsonArray();
        shortPoint.add(0);
        pointShape.getAsJsonObject("collision").getAsJsonArray("lines")
                .get(0).getAsJsonObject().getAsJsonArray("points").set(0, shortPoint);
        assertRejected(root, pointShape);

        JsonObject fractionalPoint = lineFixture();
        JsonArray fractional = new JsonArray();
        fractional.add(0.5);
        fractional.add(0);
        fractionalPoint.getAsJsonObject("collision").getAsJsonArray("lines")
                .get(0).getAsJsonObject().getAsJsonArray("points").set(0, fractional);
        assertRejected(root, fractionalPoint);

        JsonObject outsidePoint = lineFixture();
        JsonArray outside = new JsonArray();
        outside.add(4465);
        outside.add(0);
        outsidePoint.getAsJsonObject("collision").getAsJsonArray("lines")
                .get(0).getAsJsonObject().getAsJsonArray("points").set(0, outside);
        assertRejected(root, outsidePoint);

        JsonObject shortBlock = lineFixture();
        JsonArray blockPoints = shortBlock.getAsJsonObject("collision")
                .getAsJsonArray("lines").get(0).getAsJsonObject().getAsJsonArray("points");
        while (!blockPoints.isEmpty()) {
            blockPoints.remove(blockPoints.size() - 1);
        }
        blockPoints.add(point(0, 936));
        blockPoints.add(point(720, 936));
        blockPoints.add(point(0, 936));
        assertRejected(root, shortBlock);

        JsonObject openBlock = lineFixture();
        openBlock.getAsJsonObject("collision").getAsJsonArray("lines")
                .get(0).getAsJsonObject().getAsJsonArray("points").set(6, point(1, 1));
        assertRejected(root, openBlock);

        JsonObject shortPlatform = lineFixture();
        JsonArray platformPoints = shortPlatform.getAsJsonObject("collision")
                .getAsJsonArray("lines").get(1).getAsJsonObject().getAsJsonArray("points");
        platformPoints.remove(1);
        assertRejected(root, shortPlatform);
    }

    private static void assertMatchesLegacy(
            MapData actual, int expectedId, MapTemplate legacy, String expectedHash) throws Exception {
        assertEquals(expectedId, actual.id());
        assertEquals(legacy.iconId(), actual.terrain());
        assertEquals(legacy.row(), actual.row());
        assertEquals(legacy.column(), actual.column());
        assertEquals(legacy.column() * MapData.TILE_SIZE, actual.width());
        assertEquals(legacy.row() * MapData.TILE_SIZE, actual.height());
        assertEquals(List.copyOf(legacy.colorsBgr().get(0)), actual.background().skyColor());
        assertEquals(3, actual.background().layers().size());
        for (int index = 0; index < 3; index++) {
            MapData.Layer layer = actual.background().layers().get(index);
            assertEquals(legacy.imagesBgr().get(index), layer.image());
            assertEquals(List.copyOf(legacy.colorsBgr().get(index + 1)), layer.fillColor());
        }
        assertEquals(MapData.CollisionType.GRID, actual.collision().type());
        assertEquals(legacy.data(), actual.collision().data());
        assertTrue(actual.collision().lines().isEmpty());
        assertEquals(expectedHash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(actual.collision().data().getBytes(StandardCharsets.UTF_8))));
    }

    private static JsonObject baseGrid() {
        return JsonParser.parseString("""
                {
                  "terrain": 0,
                  "row": 1,
                  "column": 1,
                  "background": {
                    "skyColor": [0, 0, 0],
                    "layers": [
                      {"image": -1, "fillColor": [0, 0, 0]},
                      {"image": 51, "fillColor": [1, 2, 3]},
                      {"image": 52, "fillColor": [4, 5, 6]}
                    ]
                  },
                  "collision": {"type": "GRID", "data": "0"}
                }
                """).getAsJsonObject();
    }

    private static JsonObject lineFixture() {
        return JsonParser.parseString("""
                {
                  "terrain": 130,
                  "row": 20,
                  "column": 62,
                  "background": {
                    "skyColor": [128, 213, 242],
                    "layers": [
                      {"image": 51, "fillColor": [141, 185, 128]},
                      {"image": 52, "fillColor": [90, 154, 64]},
                      {"image": 53, "fillColor": [69, 153, 51]}
                    ]
                  },
                  "collision": {
                    "type": "LINE",
                    "lines": [
                      {"type": "BLOCK", "points": [[0, 936], [720, 936], [720, 1152],
                        [4464, 1152], [4464, 1440], [0, 1440], [0, 936]]},
                      {"type": "PLATFORM", "points": [[1200, 720], [1800, 720]]}
                    ]
                  }
                }
                """).getAsJsonObject();
    }

    private static JsonArray color(int red, int green, int blue) {
        JsonArray color = new JsonArray();
        color.add(red);
        color.add(green);
        color.add(blue);
        return color;
    }

    private static JsonArray point(int x, int y) {
        JsonArray point = new JsonArray();
        point.add(x);
        point.add(y);
        return point;
    }

    private static void assertRejected(Path root, JsonObject json) throws IOException {
        write(root, json);
        assertThrows(IllegalArgumentException.class, () -> LOADER.load(root, 1));
    }

    private static void write(Path root, JsonObject json) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("1.json"), new Gson().toJson(json), StandardCharsets.UTF_8);
    }
}

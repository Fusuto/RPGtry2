package org.main.engine;

import org.main.core.Library;

import java.util.ArrayList;
import java.util.List;

public class DungeonMap {
    private static final int FLOOR = 0;
    private static final int WALL = 1;
    private static final int DOOR_CLOSED = 2;
    private static final int ALT_WALL = 3;
    private static final int ALT_FLOOR = 4;
    private static final int ALT_DOOR_CLOSED = 5;
    private static final int FISHING_WATER = 6;
    private static final int WATER = 7;

    private final Library.TileType[][] tiles;
    private final int[][] environmentThemeIndexes;
    private final MapPaintData paintData;
    private final MapGeometryData geometryData;
    private final MobAreaData mobAreaData;
    private final MapLightingSettings lightingSettings;
    private final List<MapLight> lights;

    public DungeonMap(Library.TileType[][] tiles) {
        this(tiles, new int[tiles.length][tiles[0].length]);
    }

    public DungeonMap(Library.TileType[][] tiles, int[][] environmentThemeIndexes) {
        this(tiles, environmentThemeIndexes, MapPaintData.blank(tiles[0].length, tiles.length));
    }

    public DungeonMap(Library.TileType[][] tiles, int[][] environmentThemeIndexes, MapPaintData paintData) {
        this(tiles, environmentThemeIndexes, paintData, MapGeometryData.blank(tiles[0].length, tiles.length));
    }

    public DungeonMap(
            Library.TileType[][] tiles,
            int[][] environmentThemeIndexes,
            MapPaintData paintData,
            MapGeometryData geometryData
    ) {
        this(tiles, environmentThemeIndexes, paintData, geometryData,
                MobAreaData.blank(tiles[0].length, tiles.length));
    }

    public DungeonMap(
            Library.TileType[][] tiles,
            int[][] environmentThemeIndexes,
            MapPaintData paintData,
            MapGeometryData geometryData,
            MobAreaData mobAreaData
    ) {
        this(tiles, environmentThemeIndexes, paintData, geometryData, mobAreaData,
                MapLightingSettings.defaultSettings(), List.of());
    }

    public DungeonMap(
            Library.TileType[][] tiles,
            int[][] environmentThemeIndexes,
            MapPaintData paintData,
            MapGeometryData geometryData,
            MobAreaData mobAreaData,
            MapLightingSettings lightingSettings,
            List<MapLight> lights
    ) {
        this.tiles = tiles;
        this.environmentThemeIndexes = environmentThemeIndexes;
        this.paintData = paintData == null
                ? MapPaintData.blank(tiles[0].length, tiles.length)
                : paintData;
        this.geometryData = geometryData == null
                ? MapGeometryData.blank(tiles[0].length, tiles.length)
                : geometryData;
        this.mobAreaData = mobAreaData == null
                ? MobAreaData.blank(tiles[0].length, tiles.length)
                : mobAreaData;
        this.lightingSettings = lightingSettings == null
                ? MapLightingSettings.defaultSettings()
                : lightingSettings;
        this.lights = lights == null ? new ArrayList<>() : new ArrayList<>(lights);
    }

    public int getWidth() {
        return tiles[0].length;
    }

    public int getHeight() {
        return tiles.length;
    }

    public Library.TileType getTile(int x, int y) {
        if (isOutOfBounds(x, y)) {
            return Library.TileType.WALL;
        }

        return tiles[y][x];
    }

    public int getEnvironmentThemeIndex(int x, int y) {
        if (isOutOfBounds(x, y)) {
            return 0;
        }

        return environmentThemeIndexes[y][x];
    }

    public MapPaintData getPaintData() {
        return paintData;
    }

    public MapGeometryData getGeometryData() {
        return geometryData;
    }

    public MobAreaData getMobAreaData() {
        return mobAreaData;
    }

    public MapLightingSettings getLightingSettings() {
        return lightingSettings;
    }

    public List<MapLight> getLightsView() {
        return List.copyOf(lights);
    }

    public String getMobAreaId(int x, int y) {
        return isOutOfBounds(x, y) ? "" : mobAreaData.get(x, y);
    }

    public String getPaintBrushId(MapPaintData.Layer layer, int x, int y) {
        if (isOutOfBounds(x, y) || paintData == null) {
            return "";
        }

        return paintData.get(layer, x, y);
    }

    public int getHeightLevel(int x, int y) {
        if (isOutOfBounds(x, y) || geometryData == null) {
            return MapGeometryData.DEFAULT_HEIGHT_LEVEL;
        }

        return geometryData.getHeightLevel(x, y);
    }

    public double getHeightMultiplier(int x, int y) {
        if (isOutOfBounds(x, y) || geometryData == null) {
            return MapGeometryData.DEFAULT_HEIGHT_LEVEL;
        }

        return geometryData.getHeightMultiplier(x, y);
    }

    public boolean isWalkable(int x, int y) {
        return !getTile(x, y).blocksMovement();
    }

    public boolean isWallLike(int x, int y) {
        return getTile(x, y).isWallLike();
    }

    public boolean isOutOfBounds(int x, int y) {
        return y < 0 || y >= tiles.length || x < 0 || x >= tiles[0].length;
    }

    public static DungeonMap testMap() {
        /*
         * Visual map codes:
         * 0 = floor, 1 = wall, 2 = closed door
         * 3 = alternate wall, 4 = alternate floor, 5 = alternate closed door
         * 6 = fishable water, 7 = decorative water
         */
        int[][] raw = {
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 0, 0, 2, 0, 0, 0, 4, 4, 4, 2, 0, 0, 1},
                {1, 0, 1, 1, 1, 0, 0, 4, 3, 4, 1, 1, 0, 1},
                {1, 0, 1, 7, 6, 0, 0, 4, 3, 4, 0, 0, 0, 1},
                {1, 0, 1, 7, 7, 0, 0, 4, 4, 4, 1, 1, 0, 1},
                {1, 0, 1, 1, 1, 0, 0, 4, 4, 4, 1, 1, 0, 1},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 1},
                {1, 1, 1, 2, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1},
                {1, 4, 4, 4, 1, 0, 0, 0, 1, 0, 0, 0, 4, 1},
                {1, 4, 4, 4, 2, 0, 0, 0, 1, 0, 5, 4, 4, 1},
                {1, 4, 4, 4, 1, 1, 1, 1, 1, 0, 4, 4, 4, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        };

        Library.TileType[][] tiles = new Library.TileType[raw.length][raw[0].length];
        int[][] environmentThemeIndexes = new int[raw.length][raw[0].length];

        for (int y = 0; y < raw.length; y++) {
            for (int x = 0; x < raw[y].length; x++) {
                tiles[y][x] = switch (raw[y][x]) {
                    case WALL, ALT_WALL -> Library.TileType.WALL;
                    case DOOR_CLOSED, ALT_DOOR_CLOSED -> Library.TileType.DOOR_CLOSED;
                    case FISHING_WATER -> Library.TileType.FISHING_WATER;
                    case WATER -> Library.TileType.WATER;
                    default -> Library.TileType.FLOOR;
                };

                environmentThemeIndexes[y][x] = switch (raw[y][x]) {
                    case ALT_WALL, ALT_FLOOR, ALT_DOOR_CLOSED -> 1;
                    default -> 0;
                };
            }
        }

        return new DungeonMap(
                tiles,
                environmentThemeIndexes,
                MapPaintData.blank(raw[0].length, raw.length),
                MapGeometryData.blank(raw[0].length, raw.length)
        );
    }

    public void setTile(int x, int y, Library.TileType tileType) {
        if (isOutOfBounds(x, y)) {
            return;
        }

        tiles[y][x] = tileType;
    }
}

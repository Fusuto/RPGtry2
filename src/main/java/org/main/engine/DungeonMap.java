package org.main.engine;

import org.main.core.Library;

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

    public DungeonMap(Library.TileType[][] tiles) {
        this(tiles, new int[tiles.length][tiles[0].length]);
    }

    public DungeonMap(Library.TileType[][] tiles, int[][] environmentThemeIndexes) {
        this.tiles = tiles;
        this.environmentThemeIndexes = environmentThemeIndexes;
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
                {1, 0, 0, 2, 0, 0, 1, 4, 4, 4, 2, 0, 0, 1},
                {1, 0, 1, 1, 1, 0, 1, 4, 3, 4, 1, 1, 0, 1},
                {1, 0, 1, 7, 6, 0, 2, 4, 3, 4, 0, 0, 0, 1},
                {1, 0, 1, 7, 7, 0, 1, 4, 3, 4, 1, 1, 0, 1},
                {1, 0, 1, 1, 1, 0, 1, 4, 4, 4, 1, 1, 0, 1},
                {1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 1},
                {1, 1, 1, 2, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1},
                {1, 4, 4, 4, 1, 0, 1, 0, 1, 0, 0, 0, 4, 1},
                {1, 4, 3, 4, 2, 0, 0, 0, 1, 0, 5, 4, 4, 1},
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

        return new DungeonMap(tiles, environmentThemeIndexes);
    }

    public void setTile(int x, int y, Library.TileType tileType) {
        if (isOutOfBounds(x, y)) {
            return;
        }

        tiles[y][x] = tileType;
    }
}

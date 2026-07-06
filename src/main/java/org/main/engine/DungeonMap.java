package org.main.engine;

import org.main.core.Library;

public class DungeonMap {
    private final Library.TileType[][] tiles;

    public DungeonMap(Library.TileType[][] tiles) {
        this.tiles = tiles;
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
        int[][] raw = {
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 0, 0, 2, 1, 0, 0, 0, 0, 1},
                {1, 0, 1, 0, 1, 0, 1, 1, 0, 1},
                {1, 0, 1, 0, 0, 0, 0, 1, 0, 1},
                {1, 0, 1, 2, 1, 0, 0, 1, 0, 1},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {1, 1, 1, 1, 0, 0, 0, 1, 0, 1},
                {1, 0, 0, 0, 0, 0, 0, 1, 0, 1},
                {1, 0, 1, 1, 1, 1, 0, 0, 0, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        };

        Library.TileType[][] tiles = new Library.TileType[raw.length][raw[0].length];

        for (int y = 0; y < raw.length; y++) {
            for (int x = 0; x < raw[y].length; x++) {
                tiles[y][x] = switch (raw[y][x]) {
                    case 1 -> Library.TileType.WALL;
                    case 2 -> Library.TileType.DOOR_CLOSED;
                    default -> Library.TileType.FLOOR;
                };
            }
        }

        return new DungeonMap(tiles);
    }

    public void setTile(int x, int y, Library.TileType tileType) {
        if (isOutOfBounds(x, y)) {
            return;
        }

        tiles[y][x] = tileType;
    }
}
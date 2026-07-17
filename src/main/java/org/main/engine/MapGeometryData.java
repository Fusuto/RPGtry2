package org.main.engine;

public final class MapGeometryData {
    public static final int DEFAULT_HEIGHT_LEVEL = 1;
    public static final int MIN_HEIGHT_LEVEL = 1;
    public static final int MAX_HEIGHT_LEVEL = 8;

    private final int width;
    private final int height;
    private final int[][] heightLevels;

    private MapGeometryData(int width, int height, int[][] heightLevels) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.heightLevels = normalize(heightLevels, this.width, this.height);
    }

    public static MapGeometryData blank(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int[][] levels = new int[safeHeight][safeWidth];
        for (int y = 0; y < safeHeight; y++) {
            for (int x = 0; x < safeWidth; x++) {
                levels[y][x] = DEFAULT_HEIGHT_LEVEL;
            }
        }
        return new MapGeometryData(safeWidth, safeHeight, levels);
    }

    public static MapGeometryData of(int width, int height, int[][] heightLevels) {
        return new MapGeometryData(width, height, heightLevels);
    }

    public MapGeometryData copy() {
        return new MapGeometryData(width, height, heightLevels);
    }

    public MapGeometryData resized(int newWidth, int newHeight) {
        MapGeometryData resized = blank(newWidth, newHeight);
        int copyWidth = Math.min(width, resized.width);
        int copyHeight = Math.min(height, resized.height);
        for (int y = 0; y < copyHeight; y++) {
            for (int x = 0; x < copyWidth; x++) {
                resized.setHeightLevel(x, y, getHeightLevel(x, y));
            }
        }
        return resized;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int getHeightLevel(int x, int y) {
        if (isOutOfBounds(x, y)) {
            return DEFAULT_HEIGHT_LEVEL;
        }
        return heightLevels[y][x];
    }

    public double getHeightMultiplier(int x, int y) {
        return Math.max(MIN_HEIGHT_LEVEL, getHeightLevel(x, y));
    }

    public void setHeightLevel(int x, int y, int heightLevel) {
        if (isOutOfBounds(x, y)) {
            return;
        }
        heightLevels[y][x] = clampHeightLevel(heightLevel);
    }

    public int[][] copyHeightLevels() {
        int[][] copy = new int[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(heightLevels[y], 0, copy[y], 0, width);
        }
        return copy;
    }

    private boolean isOutOfBounds(int x, int y) {
        return y < 0 || y >= height || x < 0 || x >= width;
    }

    public static int clampHeightLevel(int heightLevel) {
        return Math.max(MIN_HEIGHT_LEVEL, Math.min(MAX_HEIGHT_LEVEL, heightLevel));
    }

    private static int[][] normalize(int[][] source, int width, int height) {
        int[][] normalized = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                normalized[y][x] = DEFAULT_HEIGHT_LEVEL;
            }
        }
        if (source == null) {
            return normalized;
        }

        int copyHeight = Math.min(height, source.length);
        for (int y = 0; y < copyHeight; y++) {
            if (source[y] == null) {
                continue;
            }
            int copyWidth = Math.min(width, source[y].length);
            for (int x = 0; x < copyWidth; x++) {
                normalized[y][x] = clampHeightLevel(source[y][x]);
            }
        }
        return normalized;
    }
}

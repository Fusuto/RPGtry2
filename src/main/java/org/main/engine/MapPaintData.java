package org.main.engine;

public final class MapPaintData {
    public enum Layer {
        FLOOR,
        WALL,
        DOOR,
        ROOF
    }

    private final int width;
    private final int height;
    private final String[][] floorBrushes;
    private final String[][] wallBrushes;
    private final String[][] doorBrushes;
    private final String[][] roofBrushes;

    private MapPaintData(
            int width,
            int height,
            String[][] floorBrushes,
            String[][] wallBrushes,
            String[][] doorBrushes,
            String[][] roofBrushes
    ) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.floorBrushes = normalize(floorBrushes, this.width, this.height);
        this.wallBrushes = normalize(wallBrushes, this.width, this.height);
        this.doorBrushes = normalize(doorBrushes, this.width, this.height);
        this.roofBrushes = normalize(roofBrushes, this.width, this.height);
    }

    public static MapPaintData blank(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        return new MapPaintData(
                safeWidth,
                safeHeight,
                new String[safeHeight][safeWidth],
                new String[safeHeight][safeWidth],
                new String[safeHeight][safeWidth],
                new String[safeHeight][safeWidth]
        );
    }

    public static MapPaintData of(
            int width,
            int height,
            String[][] floorBrushes,
            String[][] wallBrushes,
            String[][] doorBrushes,
            String[][] roofBrushes
    ) {
        return new MapPaintData(width, height, floorBrushes, wallBrushes, doorBrushes, roofBrushes);
    }

    public MapPaintData copy() {
        return new MapPaintData(width, height, floorBrushes, wallBrushes, doorBrushes, roofBrushes);
    }

    public MapPaintData resized(int newWidth, int newHeight) {
        MapPaintData resized = blank(newWidth, newHeight);
        int copyWidth = Math.min(width, resized.width);
        int copyHeight = Math.min(height, resized.height);
        for (Layer layer : Layer.values()) {
            for (int y = 0; y < copyHeight; y++) {
                for (int x = 0; x < copyWidth; x++) {
                    resized.set(layer, x, y, get(layer, x, y));
                }
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

    public String get(Layer layer, int x, int y) {
        if (layer == null || isOutOfBounds(x, y)) {
            return "";
        }

        String value = arrayFor(layer)[y][x];
        return value == null ? "" : value;
    }

    public void set(Layer layer, int x, int y, String brushId) {
        if (layer == null || isOutOfBounds(x, y)) {
            return;
        }

        arrayFor(layer)[y][x] = brushId == null ? "" : brushId.trim();
    }

    public boolean hasBrush(int x, int y) {
        if (isOutOfBounds(x, y)) {
            return false;
        }

        for (Layer layer : Layer.values()) {
            if (!get(layer, x, y).isBlank()) {
                return true;
            }
        }
        return false;
    }

    public String[][] copyLayer(Layer layer) {
        String[][] source = arrayFor(layer);
        String[][] copy = new String[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, width);
        }
        return copy;
    }

    private boolean isOutOfBounds(int x, int y) {
        return y < 0 || y >= height || x < 0 || x >= width;
    }

    private String[][] arrayFor(Layer layer) {
        return switch (layer) {
            case FLOOR -> floorBrushes;
            case WALL -> wallBrushes;
            case DOOR -> doorBrushes;
            case ROOF -> roofBrushes;
        };
    }

    private static String[][] normalize(String[][] source, int width, int height) {
        String[][] normalized = new String[height][width];
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
                normalized[y][x] = source[y][x] == null ? "" : source[y][x].trim();
            }
        }
        return normalized;
    }
}

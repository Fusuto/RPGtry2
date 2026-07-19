package org.main.engine;

import java.util.HashSet;
import java.util.Set;

/**
 * Non-visual gameplay paint identifying the roaming area assigned to each tile.
 */
public final class MobAreaData {
    private final int width;
    private final int height;
    private final String[][] areaIds;

    private MobAreaData(int width, int height, String[][] areaIds) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.areaIds = normalize(areaIds, this.width, this.height);
    }

    public static MobAreaData blank(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        return new MobAreaData(safeWidth, safeHeight, new String[safeHeight][safeWidth]);
    }

    public static MobAreaData of(int width, int height, String[][] areaIds) {
        return new MobAreaData(width, height, areaIds);
    }

    public MobAreaData copy() {
        return new MobAreaData(width, height, areaIds);
    }

    public MobAreaData resized(int newWidth, int newHeight) {
        MobAreaData resized = blank(newWidth, newHeight);
        int copyWidth = Math.min(width, resized.width);
        int copyHeight = Math.min(height, resized.height);
        for (int y = 0; y < copyHeight; y++) {
            for (int x = 0; x < copyWidth; x++) {
                resized.set(x, y, get(x, y));
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

    public String get(int x, int y) {
        if (isOutOfBounds(x, y)) {
            return "";
        }
        String value = areaIds[y][x];
        return value == null ? "" : value;
    }

    public void set(int x, int y, String areaId) {
        if (!isOutOfBounds(x, y)) {
            areaIds[y][x] = normalizeId(areaId);
        }
    }

    public Set<String> areaIds() {
        Set<String> ids = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String id = get(x, y);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return Set.copyOf(ids);
    }

    public String[][] copyRows() {
        return normalize(areaIds, width, height);
    }

    private boolean isOutOfBounds(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height;
    }

    private static String[][] normalize(String[][] source, int width, int height) {
        String[][] normalized = new String[height][width];
        if (source == null) {
            return normalized;
        }
        for (int y = 0; y < Math.min(height, source.length); y++) {
            if (source[y] == null) {
                continue;
            }
            for (int x = 0; x < Math.min(width, source[y].length); x++) {
                normalized[y][x] = normalizeId(source[y][x]);
            }
        }
        return normalized;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }
}

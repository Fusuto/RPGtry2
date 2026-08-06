package org.main.engine;

import org.main.core.GameConfiguration;

public final class TerrainGeometry {
    private static volatile long cachedConfigurationRevision = Long.MIN_VALUE;
    private static volatile double cachedHeightStep = 0.35;
    private static volatile int cachedMaxWalkableDelta = 1;

    public enum Corner {
        NORTH_WEST,
        NORTH_EAST,
        SOUTH_EAST,
        SOUTH_WEST
    }

    private TerrainGeometry() {
    }

    public static double groundY(DungeonMap map, int tileX, int tileY) {
        int heightLevel = heightLevel(map, tileX, tileY);
        return (heightLevel - MapGeometryData.DEFAULT_HEIGHT_LEVEL) * heightStep();
    }

    public static double groundYAtWorld(DungeonMap map, double worldX, double worldZ) {
        if (map == null) {
            return 0.0;
        }

        int tileX = clamp((int) Math.floor(worldX), 0, map.getWidth() - 1);
        int tileY = clamp((int) Math.floor(worldZ), 0, map.getHeight() - 1);
        double localX = clamp(worldX - tileX, 0.0, 1.0);
        double localZ = clamp(worldZ - tileY, 0.0, 1.0);

        double northWest = cornerGroundY(map, tileX, tileY, Corner.NORTH_WEST);
        double northEast = cornerGroundY(map, tileX, tileY, Corner.NORTH_EAST);
        double southEast = cornerGroundY(map, tileX, tileY, Corner.SOUTH_EAST);
        double southWest = cornerGroundY(map, tileX, tileY, Corner.SOUTH_WEST);

        double north = lerp(northWest, northEast, localX);
        double south = lerp(southWest, southEast, localX);
        return lerp(north, south, localZ);
    }

    public static double cornerGroundY(DungeonMap map, int tileX, int tileY, Corner corner) {
        int vertexX = switch (corner) {
            case NORTH_WEST, SOUTH_WEST -> tileX;
            case NORTH_EAST, SOUTH_EAST -> tileX + 1;
        };
        int vertexY = switch (corner) {
            case NORTH_WEST, NORTH_EAST -> tileY;
            case SOUTH_WEST, SOUTH_EAST -> tileY + 1;
        };

        double averagedLevel = connectedVertexHeightLevel(map, vertexX, vertexY, tileX, tileY);
        return (averagedLevel - MapGeometryData.DEFAULT_HEIGHT_LEVEL) * heightStep();
    }

    private static double connectedVertexHeightLevel(DungeonMap map, int vertexX, int vertexY, int ownerX, int ownerY) {
        if (map == null || map.isOutOfBounds(ownerX, ownerY)) {
            return MapGeometryData.DEFAULT_HEIGHT_LEVEL;
        }

        int ownerIndex = candidateIndex(vertexX, vertexY, ownerX, ownerY);
        if (ownerIndex < 0) {
            return heightLevel(map, ownerX, ownerY);
        }

        boolean ownerWallLike = map.isWallLike(ownerX, ownerY);
        int visitedMask = 1 << ownerIndex;
        boolean changed;
        do {
            changed = false;
            for (int currentIndex = 0; currentIndex < 4; currentIndex++) {
                if ((visitedMask & (1 << currentIndex)) == 0
                        || !candidateInBounds(map, vertexX, vertexY, currentIndex)) {
                    continue;
                }
                int currentX = candidateX(vertexX, currentIndex);
                int currentY = candidateY(vertexY, currentIndex);
                for (int candidateIndex = 0; candidateIndex < 4; candidateIndex++) {
                    int candidateBit = 1 << candidateIndex;
                    if ((visitedMask & candidateBit) != 0
                            || !candidateInBounds(map, vertexX, vertexY, candidateIndex)) {
                        continue;
                    }
                    int candidateX = candidateX(vertexX, candidateIndex);
                    int candidateY = candidateY(vertexY, candidateIndex);
                    if (!isCardinalNeighbor(currentX, currentY, candidateX, candidateY)
                            || map.isWallLike(candidateX, candidateY) != ownerWallLike
                            || edgeKind(map, currentX, currentY, candidateX, candidateY)
                            == TerrainEdgeKind.CLIFF) {
                        continue;
                    }
                    visitedMask |= candidateBit;
                    changed = true;
                }
            }
        } while (changed);

        int total = 0;
        int count = 0;
        for (int index = 0; index < 4; index++) {
            if ((visitedMask & (1 << index)) == 0
                    || !candidateInBounds(map, vertexX, vertexY, index)) {
                continue;
            }
            total += heightLevel(map, candidateX(vertexX, index), candidateY(vertexY, index));
            count++;
        }
        return count == 0 ? heightLevel(map, ownerX, ownerY) : total / (double) count;
    }

    private static int candidateIndex(int vertexX, int vertexY, int tileX, int tileY) {
        for (int index = 0; index < 4; index++) {
            if (candidateX(vertexX, index) == tileX && candidateY(vertexY, index) == tileY) {
                return index;
            }
        }
        return -1;
    }

    private static boolean candidateInBounds(DungeonMap map, int vertexX, int vertexY, int index) {
        return !map.isOutOfBounds(candidateX(vertexX, index), candidateY(vertexY, index));
    }

    private static int candidateX(int vertexX, int index) {
        return index == 0 || index == 3 ? vertexX - 1 : vertexX;
    }

    private static int candidateY(int vertexY, int index) {
        return index == 0 || index == 1 ? vertexY - 1 : vertexY;
    }

    public static TerrainEdgeKind edgeKind(DungeonMap map, int x1, int y1, int x2, int y2) {
        if (map == null || map.isOutOfBounds(x1, y1) || map.isOutOfBounds(x2, y2)) {
            return TerrainEdgeKind.BLOCKED;
        }
        if (!isCardinalNeighbor(x1, y1, x2, y2)) {
            return TerrainEdgeKind.BLOCKED;
        }
        return edgeKind(heightLevel(map, x1, y1), heightLevel(map, x2, y2));
    }

    public static TerrainEdgeKind edgeKind(int heightLevelA, int heightLevelB) {
        int delta = Math.abs(heightLevelA - heightLevelB);
        if (delta == 0) {
            return TerrainEdgeKind.FLAT;
        }
        if (delta <= maxWalkableDelta()) {
            return TerrainEdgeKind.SLOPE;
        }
        return TerrainEdgeKind.CLIFF;
    }

    public static boolean canTraverse(DungeonMap map, int x1, int y1, int x2, int y2) {
        if (map == null || map.isOutOfBounds(x2, y2) || !map.isWalkable(x2, y2)) {
            return false;
        }
        TerrainEdgeKind kind = edgeKind(map, x1, y1, x2, y2);
        return kind == TerrainEdgeKind.FLAT || kind == TerrainEdgeKind.SLOPE;
    }

    public static int heightLevel(DungeonMap map, int tileX, int tileY) {
        return map == null ? MapGeometryData.DEFAULT_HEIGHT_LEVEL : map.getHeightLevel(tileX, tileY);
    }

    public static double heightStep() {
        refreshConfigurationValues();
        return cachedHeightStep;
    }

    public static int maxWalkableDelta() {
        refreshConfigurationValues();
        return cachedMaxWalkableDelta;
    }

    public static String cliffTexturePath() {
        return GameConfiguration.stringValue(
                "terrain.cliffTexturePath",
                "assets/images/building/wall_rock.png"
        );
    }

    private static boolean isCardinalNeighbor(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1) == 1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void refreshConfigurationValues() {
        long revision = GameConfiguration.revision();
        if (revision == cachedConfigurationRevision) {
            return;
        }
        synchronized (TerrainGeometry.class) {
            if (revision == cachedConfigurationRevision) {
                return;
            }
            cachedHeightStep = Math.max(0.01,
                    GameConfiguration.doubleValue("terrain.heightStep", 0.35));
            cachedMaxWalkableDelta = Math.max(0,
                    GameConfiguration.intValue("terrain.maxWalkableDelta", 1));
            cachedConfigurationRevision = revision;
        }
    }
}

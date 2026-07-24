package org.main.engine;

import org.main.core.GameConfiguration;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TerrainGeometry {
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

        List<Point> candidates = vertexTiles(map, vertexX, vertexY);
        Point owner = new Point(ownerX, ownerY);
        if (!candidates.contains(owner)) {
            return heightLevel(map, ownerX, ownerY);
        }

        boolean ownerWallLike = map.isWallLike(ownerX, ownerY);
        ArrayDeque<Point> queue = new ArrayDeque<>();
        Set<Point> visited = new HashSet<>();
        queue.add(owner);
        visited.add(owner);

        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            for (Point candidate : candidates) {
                if (visited.contains(candidate)
                        || !isCardinalNeighbor(current.x, current.y, candidate.x, candidate.y)
                        || map.isWallLike(candidate.x, candidate.y) != ownerWallLike
                        || edgeKind(map, current.x, current.y, candidate.x, candidate.y) == TerrainEdgeKind.CLIFF) {
                    continue;
                }
                visited.add(candidate);
                queue.addLast(candidate);
            }
        }

        int total = 0;
        for (Point point : visited) {
            total += heightLevel(map, point.x, point.y);
        }
        return visited.isEmpty() ? heightLevel(map, ownerX, ownerY) : total / (double) visited.size();
    }

    private static List<Point> vertexTiles(DungeonMap map, int vertexX, int vertexY) {
        int[][] positions = {
                {vertexX - 1, vertexY - 1},
                {vertexX, vertexY - 1},
                {vertexX, vertexY},
                {vertexX - 1, vertexY}
        };
        List<Point> points = new ArrayList<>();
        for (int[] position : positions) {
            if (!map.isOutOfBounds(position[0], position[1])) {
                points.add(new Point(position[0], position[1]));
            }
        }
        return points;
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
        return Math.max(0.01, GameConfiguration.doubleValue("terrain.heightStep", 0.35));
    }

    public static int maxWalkableDelta() {
        return Math.max(0, GameConfiguration.intValue("terrain.maxWalkableDelta", 1));
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
}

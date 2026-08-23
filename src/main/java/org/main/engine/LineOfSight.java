package org.main.engine;

/**
 * Shared grid ray traversal for AI, lighting, and visibility checks.
 */
public final class LineOfSight {
    private LineOfSight() {
    }

    public static boolean between(
            DungeonMap map,
            int fromX,
            int fromY,
            int targetX,
            int targetY,
            Blocker blocker,
            boolean targetCanBlock
    ) {
        if (map == null || map.isOutOfBounds(fromX, fromY) || map.isOutOfBounds(targetX, targetY)) {
            return false;
        }
        Blocker safeBlocker = blocker == null ? Blocker.WALL_LIKE : blocker;
        int dx = Math.abs(targetX - fromX);
        int dy = Math.abs(targetY - fromY);
        int stepX = fromX < targetX ? 1 : -1;
        int stepY = fromY < targetY ? 1 : -1;
        int error = dx - dy;
        int x = fromX;
        int y = fromY;
        while (x != targetX || y != targetY) {
            int previousX = x;
            int previousY = y;
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubledError < dx) {
                error += dx;
                y += stepY;
            }
            if (map.isOutOfBounds(x, y)
                    || TerrainGeometry.edgeKind(map, previousX, previousY, x, y) == TerrainEdgeKind.CLIFF) {
                return false;
            }
            boolean target = x == targetX && y == targetY;
            if ((targetCanBlock || !target) && safeBlocker.blocks(map, x, y)) {
                return false;
            }
        }
        return true;
    }

    public enum Blocker {
        WALL_LIKE {
            @Override
            boolean blocks(DungeonMap map, int x, int y) {
                return map.isWallLike(x, y);
            }
        },
        MOVEMENT {
            @Override
            boolean blocks(DungeonMap map, int x, int y) {
                return map.getTile(x, y).blocksMovement();
            }
        };

        abstract boolean blocks(DungeonMap map, int x, int y);
    }
}

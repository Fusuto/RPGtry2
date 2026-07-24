package org.main.engine;

import java.awt.Point;

public class MovementEngine {

    public Point move(int playerX, int playerY, int dx, int dy, DungeonMap map) {
        int nx = playerX + dx;
        int ny = playerY + dy;

        if (TerrainGeometry.canTraverse(map, playerX, playerY, nx, ny)) {
            return new Point(nx, ny);
        }

        return new Point(playerX, playerY);
    }

    public int turnLeft(int dir) {
        return (dir + 3) % 4;
    }

    public int turnRight(int dir) {
        return (dir + 1) % 4;
    }

    public int forwardX(int dir) {
        return switch (dir) {
            case 1 -> 1;   // east
            case 3 -> -1;  // west
            default -> 0;
        };
    }

    public int forwardY(int dir) {
        return switch (dir) {
            case 0 -> -1;  // north
            case 2 -> 1;   // south
            default -> 0;
        };
    }

    public int leftX(int dir) {
        return switch (dir) {
            case 0 -> -1;
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 0;
            default -> 0;
        };
    }

    public int leftY(int dir) {
        return switch (dir) {
            case 0 -> 0;
            case 1 -> -1;
            case 2 -> 0;
            case 3 -> 1;
            default -> 0;
        };
    }

    public int rightX(int dir) {
        return -leftX(dir);
    }

    public int rightY(int dir) {
        return -leftY(dir);
    }
}

package org.main.core;

import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.awt.Color;
import java.awt.Graphics2D;

public class MiniMapRenderer {
    private static final int TILE_SIZE = 18;
    private static final int START_X = 20;
    private static final int START_Y = 20;

    public void draw(Graphics2D g, GameState gameState) {
        if (!gameState.isMiniMapVisible()
                || (!gameState.isMiniMapUnlocked() && !gameState.isMiniMapDebugMode())) {
            return;
        }

        DungeonMap dungeonMap = gameState.getDungeonMap();

        drawMapTiles(g, dungeonMap, gameState);
        drawEntities(g, gameState);
        drawPlayer(g, gameState);
    }

    private void drawMapTiles(Graphics2D g, DungeonMap dungeonMap, GameState gameState) {
        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                if (!gameState.isMiniMapTileDiscovered(x, y)) {
                    continue;
                }

                Library.TileType tileType = dungeonMap.getTile(x, y);

                g.setColor(tileType.isWallLike() ? Color.DARK_GRAY : Color.GRAY);
                g.fillRect(
                        START_X + x * TILE_SIZE,
                        START_Y + y * TILE_SIZE,
                        TILE_SIZE - 2,
                        TILE_SIZE - 2
                );
            }
        }
    }

    private void drawEntities(Graphics2D g, GameState gameState) {
        for (MapEntity entity : gameState.getEntities()) {
            if (!gameState.isMiniMapTileDiscovered(entity.getX(), entity.getY())) {
                continue;
            }

            g.setColor(entity.getType() == Library.EntityType.ENEMY ? Color.MAGENTA : Color.CYAN);

            g.fillOval(
                    START_X + entity.getX() * TILE_SIZE + 4,
                    START_Y + entity.getY() * TILE_SIZE + 4,
                    TILE_SIZE - 10,
                    TILE_SIZE - 10
            );
        }
    }

    private void drawPlayer(Graphics2D g, GameState gameState) {
        g.setColor(Color.RED);

        g.fillOval(
                START_X + gameState.getPlayerX() * TILE_SIZE + 3,
                START_Y + gameState.getPlayerY() * TILE_SIZE + 3,
                TILE_SIZE - 8,
                TILE_SIZE - 8
        );
    }
}

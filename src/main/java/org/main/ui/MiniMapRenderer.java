package org.main.ui;

import org.main.core.GameState;
import org.main.core.Library;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;

public class MiniMapRenderer {
    private static final int TILE_SIZE = 18;
    private static final int START_X = 20;
    private static final int START_Y = 20;
    private static final int VIEW_RADIUS_TILES = 5;
    private static final int VIEW_DIAMETER_TILES = VIEW_RADIUS_TILES * 2 + 1;
    private static final int VIEW_SIZE = VIEW_DIAMETER_TILES * TILE_SIZE;
    private static final int FRAME_PADDING = 4;

    public void draw(Graphics2D g, GameState gameState) {
        if (!gameState.isMiniMapVisible()
                || (!gameState.isMiniMapUnlocked() && !gameState.isMiniMapDebugMode())) {
            return;
        }

        DungeonMap dungeonMap = gameState.getDungeonMap();

        Shape oldClip = g.getClip();
        Composite oldComposite = g.getComposite();
        drawFrame(g);
        g.setClip(START_X, START_Y, VIEW_SIZE, VIEW_SIZE);
        drawMapTiles(g, dungeonMap, gameState);
        drawEntities(g, gameState);
        drawPlayer(g, gameState);
        g.setClip(oldClip);
        g.setComposite(oldComposite);
    }

    private void drawMapTiles(Graphics2D g, DungeonMap dungeonMap, GameState gameState) {
        int minX = gameState.getPlayerX() - VIEW_RADIUS_TILES;
        int maxX = gameState.getPlayerX() + VIEW_RADIUS_TILES;
        int minY = gameState.getPlayerY() - VIEW_RADIUS_TILES;
        int maxY = gameState.getPlayerY() + VIEW_RADIUS_TILES;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (dungeonMap.isOutOfBounds(x, y)) {
                    continue;
                }

                if (!gameState.isMiniMapTileDiscovered(x, y)) {
                    continue;
                }

                Library.TileType tileType = dungeonMap.getTile(x, y);

                g.setColor(tileType.isWallLike() ? Color.DARK_GRAY : Color.GRAY);
                g.fillRect(
                        tileScreenX(gameState, x),
                        tileScreenY(gameState, y),
                        TILE_SIZE - 2,
                        TILE_SIZE - 2
                );
            }
        }
    }

    private void drawEntities(Graphics2D g, GameState gameState) {
        for (MapEntity entity : gameState.getEntities()) {
            if (!isInMiniMapWindow(gameState, entity.getX(), entity.getY())) {
                continue;
            }

            if (!gameState.isMiniMapTileDiscovered(entity.getX(), entity.getY())) {
                continue;
            }

            g.setColor(entity.getType() == Library.EntityType.ENEMY ? Color.MAGENTA : Color.CYAN);

            g.fillOval(
                    tileScreenX(gameState, entity.getX()) + 4,
                    tileScreenY(gameState, entity.getY()) + 4,
                    TILE_SIZE - 10,
                    TILE_SIZE - 10
            );
        }
    }

    private void drawPlayer(Graphics2D g, GameState gameState) {
        g.setColor(Color.RED);

        g.fillOval(
                tileScreenX(gameState, gameState.getPlayerX()) + 3,
                tileScreenY(gameState, gameState.getPlayerY()) + 3,
                TILE_SIZE - 8,
                TILE_SIZE - 8
        );
    }

    private void drawFrame(Graphics2D g) {
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.58f));
        g.setColor(Color.BLACK);
        g.fillRect(
                START_X - FRAME_PADDING,
                START_Y - FRAME_PADDING,
                VIEW_SIZE + FRAME_PADDING * 2,
                VIEW_SIZE + FRAME_PADDING * 2
        );
        g.setComposite(oldComposite);
        g.setColor(new Color(185, 175, 150));
        g.drawRect(START_X - 1, START_Y - 1, VIEW_SIZE + 1, VIEW_SIZE + 1);
    }

    private int tileScreenX(GameState gameState, int worldX) {
        return START_X + (worldX - gameState.getPlayerX() + VIEW_RADIUS_TILES) * TILE_SIZE;
    }

    private int tileScreenY(GameState gameState, int worldY) {
        return START_Y + (worldY - gameState.getPlayerY() + VIEW_RADIUS_TILES) * TILE_SIZE;
    }

    private boolean isInMiniMapWindow(GameState gameState, int worldX, int worldY) {
        return Math.abs(worldX - gameState.getPlayerX()) <= VIEW_RADIUS_TILES
                && Math.abs(worldY - gameState.getPlayerY()) <= VIEW_RADIUS_TILES;
    }
}

package org.main.core;

import org.junit.jupiter.api.Test;
import org.main.engine.DungeonMap;

import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameUiChromeTest {
    @Test
    void compactWindowStaysInsideGameplayArea() {
        Rectangle bounds = GameUiChrome.centeredWindow(1280, 720, 72, 760, 680);

        assertTrue(bounds.width < 1280);
        assertTrue(bounds.height < 720 - 72);
        assertTrue(bounds.x >= GameUiChrome.VIEWPORT_MARGIN);
        assertTrue(bounds.y >= GameUiChrome.VIEWPORT_MARGIN);
        assertTrue(bounds.y + bounds.height <= 720 - 72 - GameUiChrome.VIEWPORT_MARGIN);
        assertTrue(bounds.contains(GameUiChrome.closeBounds(bounds)));
    }

    @Test
    void inventoryCloseControlUsesTranslatedCompactWindowCoordinates() {
        Library.TileType[][] tiles = {
                {Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.FLOOR}
        };
        GameState gameState = new GameState(new DungeonMap(tiles));
        gameState.setInventoryOpen(true);
        InventorySystem.InventoryPanel panel = new InventorySystem.InventoryPanel(
                gameState.getInventory(), gameState, null);

        int width = 1280;
        int height = 648;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.draw(graphics, width, height);
        graphics.dispose();

        Rectangle window = GameUiChrome.centeredWindow(width, height, 0, 760, 680);
        Rectangle close = GameUiChrome.closeBounds(window);
        MouseEvent click = new MouseEvent(
                new Canvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                close.x + close.width / 2, close.y + close.height / 2,
                1, false, MouseEvent.BUTTON1);

        assertTrue(panel.handleMousePressed(click));
        assertFalse(gameState.isInventoryOpen());
    }
}

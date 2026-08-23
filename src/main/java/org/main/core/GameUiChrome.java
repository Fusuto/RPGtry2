package org.main.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Shared geometry and chrome for compact in-game windows.
 */
final class GameUiChrome {
    static final int HEADER_HEIGHT = 42;
    static final int VIEWPORT_MARGIN = 16;

    private GameUiChrome() {
    }

    static Rectangle centeredWindow(
            int viewportWidth,
            int viewportHeight,
            int reservedBottom,
            int preferredWidth,
            int preferredHeight
    ) {
        int availableWidth = Math.max(1, viewportWidth - VIEWPORT_MARGIN * 2);
        int availableHeight = Math.max(1, viewportHeight - reservedBottom - VIEWPORT_MARGIN * 2);
        int width = Math.min(preferredWidth, availableWidth);
        int height = Math.min(preferredHeight, availableHeight);
        int x = Math.max(VIEWPORT_MARGIN, (viewportWidth - width) / 2);
        int y = Math.max(VIEWPORT_MARGIN, (viewportHeight - reservedBottom - height) / 2);
        return new Rectangle(x, y, width, height);
    }

    static Rectangle closeBounds(Rectangle windowBounds) {
        return new Rectangle(windowBounds.x + windowBounds.width - 34, windowBounds.y + 10, 22, 22);
    }

    static void drawWindow(Graphics2D g, Rectangle bounds, String title) {
        g.setColor(new Color(7, 8, 12, 238));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
        g.setColor(new Color(132, 105, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
        g.setColor(new Color(75, 61, 39));
        g.drawLine(bounds.x + 12, bounds.y + HEADER_HEIGHT,
                bounds.x + bounds.width - 12, bounds.y + HEADER_HEIGHT);

        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, 17f));
        g.setColor(new Color(242, 226, 177));
        g.drawString(title == null ? "" : title, bounds.x + 16, bounds.y + 27);
        g.setFont(oldFont);
        drawCloseButton(g, closeBounds(bounds));
    }

    static void drawCloseButton(Graphics2D g, Rectangle bounds) {
        g.setColor(new Color(39, 31, 26, 230));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        g.setColor(new Color(148, 114, 72));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
        FontMetrics metrics = g.getFontMetrics();
        String label = "X";
        g.setColor(new Color(238, 228, 190));
        g.drawString(label,
                bounds.x + (bounds.width - metrics.stringWidth(label)) / 2,
                bounds.y + (bounds.height + metrics.getAscent()) / 2 - 2);
        g.setFont(oldFont);
    }
}

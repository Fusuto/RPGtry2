package org.main.ui;

import org.main.content.PlayerRegionLibrary;
import org.main.core.PlayerStat;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.util.Map;

public final class AetherMenuScreens {
    private AetherMenuScreens() {
    }

    public static void drawStartMenu(Graphics2D g, int width, int height, String message) {
        drawFramedBackground(g, width, height);

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SERIF, Font.BOLD, 52));
        drawCenteredText(g, width, "Aether", height / 4, new Color(235, 225, 200));

        drawButton(g, "New", startMenuButtonBounds(width, height, 0));
        drawButton(g, "Load", startMenuButtonBounds(width, height, 1));
        drawButton(g, "Quit", startMenuButtonBounds(width, height, 2));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            Rectangle lastButton = startMenuButtonBounds(width, height, 2);
            drawCenteredText(g, width, message, lastButton.y + lastButton.height + 34, new Color(220, 205, 170));
        }

        g.setFont(previousFont);
    }

    public static Rectangle startMenuButtonBounds(int width, int height, int index) {
        int buttonWidth = 220;
        int buttonHeight = 48;
        int gap = 16;
        int totalHeight = buttonHeight * 3 + gap * 2;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - totalHeight / 2 + index * (buttonHeight + gap);
        return new Rectangle(x, y, buttonWidth, buttonHeight);
    }

    public static void drawGameOver(
            Graphics2D g,
            int width,
            int height,
            Image cover,
            Image titleBackground,
            String message
    ) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        if (cover != null) {
            g.drawImage(cover, 0, 0, width, height, null);
        }

        g.setColor(new Color(0, 0, 0, 85));
        g.fillRect(0, 0, width, height);

        Font previousFont = g.getFont();
        Rectangle titleBounds = gameOverTitleBounds(width, height);

        if (titleBackground != null) {
            g.drawImage(
                    titleBackground,
                    titleBounds.x,
                    titleBounds.y,
                    titleBounds.width,
                    titleBounds.height,
                    null
            );
        } else {
            g.setColor(new Color(8, 8, 10, 210));
            g.fillRoundRect(titleBounds.x, titleBounds.y, titleBounds.width, titleBounds.height, 8, 8);
        }

        g.setFont(new Font(Font.SERIF, Font.BOLD, 58));
        drawCenteredText(g, width, "GAME OVER", titleBounds.y + titleBounds.height / 2 + 20, new Color(235, 225, 210));

        drawButton(g, "Main Menu", gameOverButtonBounds(width, height, 0));
        drawButton(g, "Load", gameOverButtonBounds(width, height, 1));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            drawCenteredText(g, width, message, gameOverButtonBounds(width, height, 1).y + 82, new Color(220, 205, 170));
        }

        g.setFont(previousFont);
    }

    private static Rectangle gameOverTitleBounds(int width, int height) {
        int titleWidth = Math.min(560, width - 96);
        int titleHeight = 128;
        return new Rectangle((width - titleWidth) / 2, Math.max(44, height / 4 - titleHeight / 2), titleWidth, titleHeight);
    }

    public static Rectangle gameOverButtonBounds(int width, int height, int index) {
        int buttonWidth = 240;
        int buttonHeight = 50;
        int gap = 18;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 + index * (buttonHeight + gap);
        return new Rectangle(x, y, buttonWidth, buttonHeight);
    }

    public static void drawCharacterCreation(
            Graphics2D g,
            int width,
            int height,
            String characterName,
            String message,
            PlayerRegionLibrary selectedPlayerRegion
    ) {
        Paint previousPaint = g.getPaint();
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 18), 0, height, new Color(28, 25, 22)));
        g.fillRect(0, 0, width, height);
        g.setPaint(previousPaint);

        Font previousFont = g.getFont();
        g.setColor(new Color(95, 76, 54));
        g.drawRect(34, 34, width - 68, height - 68);
        g.setColor(new Color(170, 36, 32));
        g.drawRect(42, 42, width - 84, height - 84);

        g.setFont(new Font(Font.SERIF, Font.BOLD, 42));
        drawCenteredText(g, width, "Create Character", 92, new Color(235, 225, 200));

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(230, 220, 195));
        g.drawString("Name", characterPanelX(width), 150);
        drawNameField(g, nameFieldBounds(width), characterName);

        g.drawString("Region", characterPanelX(width), 242);

        int index = 0;
        for (PlayerRegionLibrary playerRegion : PlayerRegionLibrary.values()) {
            drawRegionOption(g, playerRegion, regionButtonBounds(width, index), playerRegion == selectedPlayerRegion);
            index++;
        }

        drawRegionDetails(g, classDetailsBounds(width), selectedPlayerRegion);
        drawButton(g, "Confirm", confirmCharacterButtonBounds(width, height));
        drawButton(g, "Back", backCharacterButtonBounds(width, height));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            drawCenteredText(g, width, message, confirmCharacterButtonBounds(width, height).y + 76, new Color(220, 205, 170));
        }

        g.setFont(previousFont);
    }

    public static Rectangle nameFieldBounds(int width) {
        return new Rectangle(characterPanelX(width), 165, 300, 46);
    }

    public static Rectangle regionButtonBounds(int width, int index) {
        return new Rectangle(characterPanelX(width), 258 + index * 62, 220, 48);
    }

    public static Rectangle confirmCharacterButtonBounds(int width, int height) {
        return new Rectangle(width / 2 - 230, height - 112, 200, 48);
    }

    public static Rectangle backCharacterButtonBounds(int width, int height) {
        return new Rectangle(width / 2 + 30, height - 112, 200, 48);
    }

    private static Rectangle classDetailsBounds(int width) {
        return new Rectangle(characterPanelX(width) + 390, 145, 400, 335);
    }

    private static int characterPanelX(int width) {
        return Math.max(58, width / 2 - 380);
    }

    public static void drawButton(Graphics2D g, String label, Rectangle bounds) {
        g.setColor(new Color(12, 12, 12, 215));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(170, 36, 32));
        g.drawLine(bounds.x + 14, bounds.y + bounds.height - 7, bounds.x + bounds.width - 14, bounds.y + bounds.height - 7);

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics metrics = g.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.setColor(new Color(235, 230, 210));
        g.drawString(label, textX, textY);
        g.setFont(previousFont);
    }

    private static void drawFramedBackground(Graphics2D g, int width, int height) {
        GradientPaint background = new GradientPaint(
                0,
                0,
                new Color(12, 12, 18),
                0,
                height,
                new Color(35, 27, 20)
        );
        Paint previousPaint = g.getPaint();
        g.setPaint(background);
        g.fillRect(0, 0, width, height);
        g.setPaint(previousPaint);

        g.setColor(new Color(170, 36, 32));
        g.drawRect(18, 18, width - 36, height - 36);
        g.setColor(new Color(95, 76, 54));
        g.drawRect(24, 24, width - 48, height - 48);
    }

    private static void drawNameField(Graphics2D g, Rectangle bounds, String characterName) {
        g.setColor(new Color(8, 8, 10, 230));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setColor(new Color(235, 230, 210));
        g.drawString((characterName == null ? "" : characterName) + "_", bounds.x + 14, bounds.y + 31);
    }

    private static void drawRegionOption(Graphics2D g, PlayerRegionLibrary playerRegion, Rectangle bounds, boolean selected) {
        g.setColor(selected ? new Color(75, 45, 38, 235) : new Color(12, 12, 12, 215));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(selected ? new Color(210, 70, 58) : new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(235, 230, 210));
        g.drawString(playerRegion.getDisplayName(), bounds.x + 16, bounds.y + 30);
    }

    private static void drawRegionDetails(Graphics2D g, Rectangle bounds, PlayerRegionLibrary selectedPlayerRegion) {
        g.setColor(new Color(8, 8, 10, 190));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(95, 76, 54));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(235, 230, 210));
        g.drawString(selectedPlayerRegion.getDisplayName(), bounds.x + 18, bounds.y + 32);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(210, 200, 180));
        drawWrappedText(g, selectedPlayerRegion.getDescription(), bounds.x + 18, bounds.y + 60, bounds.width - 36, 18);

        int y = bounds.y + 112;
        g.drawString("Starter limbs:", bounds.x + 18, y);
        y += 24;

        for (var limb : selectedPlayerRegion.createStarterLimbs()) {
            g.drawString(limb.getLimbSlot().getDisplayName() + " - " + limb.getName(), bounds.x + 34, y);
            y += 20;
        }

        y += 8;
        g.drawString("Notes:", bounds.x + 18, y);
        y += 24;

        drawWrappedText(
                g,
                "Classes are disabled. Your body and grafted limbs define your stats and abilities.",
                bounds.x + 18,
                y,
                bounds.width - 36,
                17
        );
    }

    private static void drawCenteredText(Graphics2D g, int width, String text, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics();
        int x = (width - metrics.stringWidth(text)) / 2;
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isBlank()) {
            return;
        }

        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();

        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;

            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g.drawString(line.toString(), x, y);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }

        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, y);
        }
    }
}

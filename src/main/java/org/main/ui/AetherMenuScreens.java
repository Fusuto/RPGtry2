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
    private static final int START_TITLE_FONT_SIZE = 52;
    private static final int START_TITLE_HEIGHT_DIVISOR = 4;
    private static final int START_BUTTON_WIDTH = 220;
    private static final int START_BUTTON_HEIGHT = 48;
    private static final int START_BUTTON_GAP = 16;
    private static final int START_BUTTON_COUNT = 3;
    private static final int START_MESSAGE_OFFSET_Y = 34;
    private static final int MESSAGE_FONT_SIZE = 15;
    private static final Color TITLE_TEXT_COLOR = new Color(235, 225, 200);
    private static final Color MESSAGE_TEXT_COLOR = new Color(220, 205, 170);

    private static final int GAME_OVER_OVERLAY_ALPHA = 85;
    private static final int GAME_OVER_FALLBACK_ALPHA = 210;
    private static final int GAME_OVER_CORNER_RADIUS = 8;
    private static final int GAME_OVER_TITLE_FONT_SIZE = 58;
    private static final int GAME_OVER_TITLE_BASELINE_OFFSET = 20;
    private static final int GAME_OVER_TITLE_MAX_WIDTH = 560;
    private static final int GAME_OVER_TITLE_HORIZONTAL_MARGIN = 96;
    private static final int GAME_OVER_TITLE_HEIGHT = 128;
    private static final int GAME_OVER_TITLE_MIN_Y = 44;
    private static final int GAME_OVER_BUTTON_WIDTH = 240;
    private static final int GAME_OVER_BUTTON_HEIGHT = 50;
    private static final int GAME_OVER_BUTTON_GAP = 18;
    private static final int GAME_OVER_MESSAGE_OFFSET_Y = 82;

    private static final int CHARACTER_OUTER_FRAME_MARGIN = 34;
    private static final int CHARACTER_INNER_FRAME_MARGIN = 42;
    private static final int CHARACTER_TITLE_FONT_SIZE = 42;
    private static final int CHARACTER_TITLE_Y = 92;
    private static final int CHARACTER_LABEL_FONT_SIZE = 18;
    private static final int CHARACTER_NAME_LABEL_Y = 150;
    private static final int CHARACTER_REGION_LABEL_Y = 242;
    private static final int CHARACTER_NAME_FIELD_Y = 165;
    private static final int CHARACTER_NAME_FIELD_WIDTH = 300;
    private static final int CHARACTER_NAME_FIELD_HEIGHT = 46;
    private static final int CHARACTER_REGION_BUTTON_Y = 258;
    private static final int CHARACTER_REGION_BUTTON_STEP_Y = 62;
    private static final int CHARACTER_REGION_BUTTON_WIDTH = 220;
    private static final int CHARACTER_REGION_BUTTON_HEIGHT = 48;
    private static final int CHARACTER_ACTION_BUTTON_WIDTH = 200;
    private static final int CHARACTER_ACTION_BUTTON_HEIGHT = 48;
    private static final int CHARACTER_ACTION_BUTTON_Y_OFFSET = 112;
    private static final int CHARACTER_CONFIRM_X_OFFSET = 230;
    private static final int CHARACTER_BACK_X_OFFSET = 30;
    private static final int CHARACTER_DETAILS_X_OFFSET = 390;
    private static final int CHARACTER_DETAILS_Y = 145;
    private static final int CHARACTER_DETAILS_WIDTH = 400;
    private static final int CHARACTER_DETAILS_HEIGHT = 335;
    private static final int CHARACTER_PANEL_MIN_X = 58;
    private static final int CHARACTER_PANEL_CENTER_OFFSET = 380;
    private static final int CHARACTER_MESSAGE_OFFSET_Y = 76;

    private static final int BUTTON_CORNER_RADIUS = 8;
    private static final int BUTTON_FONT_SIZE = 20;
    private static final int BUTTON_UNDERLINE_HORIZONTAL_PADDING = 14;
    private static final int BUTTON_UNDERLINE_BOTTOM_OFFSET = 7;
    private static final Color BUTTON_BACKGROUND_COLOR = new Color(12, 12, 12, 215);
    private static final Color BUTTON_BORDER_COLOR = new Color(105, 88, 62);
    private static final Color BUTTON_ACCENT_COLOR = new Color(170, 36, 32);
    private static final Color BUTTON_TEXT_COLOR = new Color(235, 230, 210);

    private AetherMenuScreens() {
    }

    public static void drawStartMenu(Graphics2D g, int width, int height, String message) {
        drawFramedBackground(g, width, height);

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SERIF, Font.BOLD, START_TITLE_FONT_SIZE));
        drawCenteredText(g, width, "Aether", height / START_TITLE_HEIGHT_DIVISOR, TITLE_TEXT_COLOR);

        drawButton(g, "New", startMenuButtonBounds(width, height, 0));
        drawButton(g, "Load", startMenuButtonBounds(width, height, 1));
        drawButton(g, "Quit", startMenuButtonBounds(width, height, 2));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, MESSAGE_FONT_SIZE));
            Rectangle lastButton = startMenuButtonBounds(width, height, 2);
            drawCenteredText(g, width, message, lastButton.y + lastButton.height + START_MESSAGE_OFFSET_Y, MESSAGE_TEXT_COLOR);
        }

        g.setFont(previousFont);
    }

    public static Rectangle startMenuButtonBounds(int width, int height, int index) {
        int buttonWidth = START_BUTTON_WIDTH;
        int buttonHeight = START_BUTTON_HEIGHT;
        int gap = START_BUTTON_GAP;
        int totalHeight = buttonHeight * START_BUTTON_COUNT + gap * (START_BUTTON_COUNT - 1);
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

        g.setColor(new Color(0, 0, 0, GAME_OVER_OVERLAY_ALPHA));
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
            g.setColor(new Color(8, 8, 10, GAME_OVER_FALLBACK_ALPHA));
            g.fillRoundRect(titleBounds.x, titleBounds.y, titleBounds.width, titleBounds.height, GAME_OVER_CORNER_RADIUS, GAME_OVER_CORNER_RADIUS);
        }

        g.setFont(new Font(Font.SERIF, Font.BOLD, GAME_OVER_TITLE_FONT_SIZE));
        drawCenteredText(g, width, "GAME OVER", titleBounds.y + titleBounds.height / 2 + GAME_OVER_TITLE_BASELINE_OFFSET, new Color(235, 225, 210));

        drawButton(g, "Main Menu", gameOverButtonBounds(width, height, 0));
        drawButton(g, "Load", gameOverButtonBounds(width, height, 1));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, MESSAGE_FONT_SIZE));
            drawCenteredText(g, width, message, gameOverButtonBounds(width, height, 1).y + GAME_OVER_MESSAGE_OFFSET_Y, MESSAGE_TEXT_COLOR);
        }

        g.setFont(previousFont);
    }

    private static Rectangle gameOverTitleBounds(int width, int height) {
        int titleWidth = Math.min(GAME_OVER_TITLE_MAX_WIDTH, width - GAME_OVER_TITLE_HORIZONTAL_MARGIN);
        int titleHeight = GAME_OVER_TITLE_HEIGHT;
        return new Rectangle((width - titleWidth) / 2, Math.max(GAME_OVER_TITLE_MIN_Y, height / START_TITLE_HEIGHT_DIVISOR - titleHeight / 2), titleWidth, titleHeight);
    }

    public static Rectangle gameOverButtonBounds(int width, int height, int index) {
        int buttonWidth = GAME_OVER_BUTTON_WIDTH;
        int buttonHeight = GAME_OVER_BUTTON_HEIGHT;
        int gap = GAME_OVER_BUTTON_GAP;
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
        g.drawRect(
                CHARACTER_OUTER_FRAME_MARGIN,
                CHARACTER_OUTER_FRAME_MARGIN,
                width - CHARACTER_OUTER_FRAME_MARGIN * 2,
                height - CHARACTER_OUTER_FRAME_MARGIN * 2
        );
        g.setColor(BUTTON_ACCENT_COLOR);
        g.drawRect(
                CHARACTER_INNER_FRAME_MARGIN,
                CHARACTER_INNER_FRAME_MARGIN,
                width - CHARACTER_INNER_FRAME_MARGIN * 2,
                height - CHARACTER_INNER_FRAME_MARGIN * 2
        );

        g.setFont(new Font(Font.SERIF, Font.BOLD, CHARACTER_TITLE_FONT_SIZE));
        drawCenteredText(g, width, "Create Character", CHARACTER_TITLE_Y, TITLE_TEXT_COLOR);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CHARACTER_LABEL_FONT_SIZE));
        g.setColor(new Color(230, 220, 195));
        g.drawString("Name", characterPanelX(width), CHARACTER_NAME_LABEL_Y);
        drawNameField(g, nameFieldBounds(width), characterName);

        g.drawString("Region", characterPanelX(width), CHARACTER_REGION_LABEL_Y);

        int index = 0;
        for (PlayerRegionLibrary playerRegion : PlayerRegionLibrary.values()) {
            drawRegionOption(g, playerRegion, regionButtonBounds(width, index), playerRegion == selectedPlayerRegion);
            index++;
        }

        drawRegionDetails(g, classDetailsBounds(width), selectedPlayerRegion);
        drawButton(g, "Confirm", confirmCharacterButtonBounds(width, height));
        drawButton(g, "Back", backCharacterButtonBounds(width, height));

        if (message != null && !message.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, MESSAGE_FONT_SIZE));
            drawCenteredText(g, width, message, confirmCharacterButtonBounds(width, height).y + CHARACTER_MESSAGE_OFFSET_Y, MESSAGE_TEXT_COLOR);
        }

        g.setFont(previousFont);
    }

    public static Rectangle nameFieldBounds(int width) {
        return new Rectangle(characterPanelX(width), CHARACTER_NAME_FIELD_Y, CHARACTER_NAME_FIELD_WIDTH, CHARACTER_NAME_FIELD_HEIGHT);
    }

    public static Rectangle regionButtonBounds(int width, int index) {
        return new Rectangle(
                characterPanelX(width),
                CHARACTER_REGION_BUTTON_Y + index * CHARACTER_REGION_BUTTON_STEP_Y,
                CHARACTER_REGION_BUTTON_WIDTH,
                CHARACTER_REGION_BUTTON_HEIGHT
        );
    }

    public static Rectangle confirmCharacterButtonBounds(int width, int height) {
        return new Rectangle(
                width / 2 - CHARACTER_CONFIRM_X_OFFSET,
                height - CHARACTER_ACTION_BUTTON_Y_OFFSET,
                CHARACTER_ACTION_BUTTON_WIDTH,
                CHARACTER_ACTION_BUTTON_HEIGHT
        );
    }

    public static Rectangle backCharacterButtonBounds(int width, int height) {
        return new Rectangle(
                width / 2 + CHARACTER_BACK_X_OFFSET,
                height - CHARACTER_ACTION_BUTTON_Y_OFFSET,
                CHARACTER_ACTION_BUTTON_WIDTH,
                CHARACTER_ACTION_BUTTON_HEIGHT
        );
    }

    private static Rectangle classDetailsBounds(int width) {
        return new Rectangle(
                characterPanelX(width) + CHARACTER_DETAILS_X_OFFSET,
                CHARACTER_DETAILS_Y,
                CHARACTER_DETAILS_WIDTH,
                CHARACTER_DETAILS_HEIGHT
        );
    }

    private static int characterPanelX(int width) {
        return Math.max(CHARACTER_PANEL_MIN_X, width / 2 - CHARACTER_PANEL_CENTER_OFFSET);
    }

    public static void drawButton(Graphics2D g, String label, Rectangle bounds) {
        g.setColor(BUTTON_BACKGROUND_COLOR);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS);
        g.setColor(BUTTON_BORDER_COLOR);
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS);
        g.setColor(BUTTON_ACCENT_COLOR);
        g.drawLine(
                bounds.x + BUTTON_UNDERLINE_HORIZONTAL_PADDING,
                bounds.y + bounds.height - BUTTON_UNDERLINE_BOTTOM_OFFSET,
                bounds.x + bounds.width - BUTTON_UNDERLINE_HORIZONTAL_PADDING,
                bounds.y + bounds.height - BUTTON_UNDERLINE_BOTTOM_OFFSET
        );

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, BUTTON_FONT_SIZE));
        FontMetrics metrics = g.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.setColor(BUTTON_TEXT_COLOR);
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

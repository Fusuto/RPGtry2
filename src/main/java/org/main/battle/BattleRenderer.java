package org.main.battle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

public class BattleRenderer {

    private static final int PANEL_PADDING = 24;
    private static final int BUTTON_GAP = 8;

    private static final int MESSAGE_BOX_HEIGHT = 44;
    private static final int MESSAGE_BOX_MARGIN = 20;
    private static final int MESSAGE_BOX_MAX_WIDTH = 520;

    private final Map<BattleCommand, Rectangle> commandBounds = new EnumMap<>(BattleCommand.class);

    private BattleAssets assets;

    /*
     * 1.0 means "use the full available command panel width".
     * 0.8 would mean "use 80% of the available command panel width".
     */
    private static final double BUTTON_WIDTH_RATIO = 1.0;

    public BattleCommand getCommandAt(Point point) {
        for (Map.Entry<BattleCommand, Rectangle> entry : commandBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public void setAssets(BattleAssets assets) {
        this.assets = assets;
    }

    public void draw(Graphics2D g, BattleEncounter encounter, int width, int height) {
        commandBounds.clear();

        if (encounter == null) {
            return;
        }

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        int battleAreaHeight = (int) (height * 0.70);
        int bottomHeight = height - battleAreaHeight;
        int menuWidth = width / 2;

        drawBattleArea(g, encounter, 0, 0, width, battleAreaHeight);

        /*
         * The battle message now floats over the battle area instead of
         * consuming space inside the command panel.
         */
        drawBattleMessageOverlay(g, encounter, 0, 0, width, battleAreaHeight);

        drawCommandMenu(g, 0, battleAreaHeight, menuWidth, bottomHeight);
        drawPartyStatus(g, encounter, menuWidth, battleAreaHeight, width - menuWidth, bottomHeight);
    }

    private void drawBattleArea(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
        BufferedImage background = assets != null ? assets.getBattleBackground() : null;

        drawImageOrFill(
                g,
                background,
                x,
                y,
                width,
                height,
                new Color(235, 235, 230)
        );

        drawPanelBorder(g, x, y, width, height);

        drawAllies(g, encounter.getAllies(), x, y, width / 2, height);
        drawEnemies(g, encounter.getEnemies(), x + width / 2, y, width / 2, height);
    }

    private void drawAllies(Graphics2D g, List<BattleActor> allies, int x, int y, int width, int height) {
        for (int i = 0; i < allies.size(); i++) {
            BattleActor ally = allies.get(i);

            int spriteSize = 96;
            int spacing = 120;

            int drawX = x + 80;
            int drawY = y + 60 + i * spacing;

            drawActorSprite(g, ally, drawX, drawY, spriteSize, spriteSize);
        }
    }

    private void drawEnemies(Graphics2D g, List<BattleActor> enemies, int x, int y, int width, int height) {
        for (int i = 0; i < enemies.size(); i++) {
            BattleActor enemy = enemies.get(i);

            int spriteSize = 128;
            int spacing = 150;

            int drawX = x + width - 200;
            int drawY = y + 50 + i * spacing;

            drawHpBar(g, drawX, drawY - 18, spriteSize, 10, enemy);
            drawActorSprite(g, enemy, drawX, drawY, spriteSize, spriteSize);
        }
    }

    private void drawActorSprite(Graphics2D g, BattleActor actor, int x, int y, int width, int height) {
        BufferedImage image = actor.getImage();

        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(actor.isEnemy() ? Color.RED : Color.BLUE);
            g.fillRect(x, y, width, height);

            g.setColor(Color.WHITE);
            g.drawString(actor.getName(), x + 8, y + height / 2);
        }

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
    }

    private void drawCommandMenu(
            Graphics2D g,
            int x,
            int y,
            int width,
            int height
    ) {
        BufferedImage panelImage = assets != null ? assets.getCommandPanelBackground() : null;

        drawImageOrFill(
                g,
                panelImage,
                x,
                y,
                width,
                height,
                Color.WHITE
        );

        drawPanelBorder(g, x, y, width, height);

        BufferedImage buttonImage = assets != null ? assets.getButtonNormal() : null;

        int contentX = x + PANEL_PADDING;
        int contentY = y + PANEL_PADDING;
        int contentWidth = width - PANEL_PADDING * 2;
        int contentHeight = height - PANEL_PADDING * 2;

        Dimension buttonSize = calculateButtonSize(
                buttonImage,
                contentWidth,
                contentHeight,
                3,
                BUTTON_GAP
        );

        int buttonWidth = buttonSize.width;
        int buttonHeight = buttonSize.height;

        drawCommandButton(
                g,
                BattleCommand.ATTACK,
                "Attack",
                new Rectangle(contentX, contentY, buttonWidth, buttonHeight)
        );

        drawCommandButton(
                g,
                BattleCommand.SKILL,
                "Skill",
                new Rectangle(contentX, contentY + buttonHeight + BUTTON_GAP, buttonWidth, buttonHeight)
        );

        drawCommandButton(
                g,
                BattleCommand.RUN,
                "Run",
                new Rectangle(contentX, contentY + (buttonHeight + BUTTON_GAP) * 2, buttonWidth, buttonHeight)
        );
    }

    private Dimension calculateButtonSize(
            BufferedImage buttonImage,
            int availableWidth,
            int availableHeight,
            int buttonCount,
            int gap
    ) {
        int totalGapHeight = gap * Math.max(0, buttonCount - 1);
        int maxButtonHeight = (availableHeight - totalGapHeight) / buttonCount;

        if (buttonImage == null) {
            return new Dimension(
                    Math.max(1, availableWidth),
                    Math.max(1, Math.min(30, maxButtonHeight))
            );
        }

        double imageAspect = buttonImage.getHeight() / (double) buttonImage.getWidth();

        int buttonWidth = (int) Math.round(availableWidth * BUTTON_WIDTH_RATIO);
        int buttonHeight = (int) Math.round(buttonWidth * imageAspect);

        /*
         * If preserving the image ratio makes the buttons too tall,
         * shrink by height instead.
         */
        if (buttonHeight > maxButtonHeight) {
            buttonHeight = Math.max(1, maxButtonHeight);
            buttonWidth = (int) Math.round(buttonHeight / imageAspect);
        }

        return new Dimension(
                Math.max(1, buttonWidth),
                Math.max(1, buttonHeight)
        );
    }

    private void drawCommandButton(
            Graphics2D g,
            BattleCommand command,
            String label,
            Rectangle bounds
    ) {
        commandBounds.put(command, bounds);

        BufferedImage buttonImage = assets != null ? assets.getButtonNormal() : null;

        drawImageOrFill(
                g,
                buttonImage,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                new Color(245, 245, 245)
        );

        g.setColor(Color.BLACK);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        FontMetrics metrics = g.getFontMetrics();

        int textX = bounds.x + 12;
        int textY = bounds.y
                + (bounds.height - metrics.getHeight()) / 2
                + metrics.getAscent();

        g.drawString(label, textX, textY);
    }

    private void drawBattleMessageOverlay(
            Graphics2D g,
            BattleEncounter encounter,
            int x,
            int y,
            int width,
            int height
    ) {
        String message = encounter.getBattleMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        int boxWidth = Math.min(width - MESSAGE_BOX_MARGIN * 2, MESSAGE_BOX_MAX_WIDTH);
        int boxHeight = MESSAGE_BOX_HEIGHT;

        int boxX = x + MESSAGE_BOX_MARGIN;
        int boxY = y + height - boxHeight - MESSAGE_BOX_MARGIN;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f));
        g.setColor(Color.WHITE);
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12);

        g.setComposite(oldComposite);

        FontMetrics metrics = g.getFontMetrics();
        String displayText = trimTextToFit(g, message, boxWidth - 24);

        int textX = boxX + 12;
        int textY = boxY
                + (boxHeight - metrics.getHeight()) / 2
                + metrics.getAscent();

        g.setColor(Color.WHITE);
        g.drawString(displayText, textX, textY);
    }

    private void drawPartyStatus(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
        BufferedImage panelImage = assets != null ? assets.getStatusPanelBackground() : null;

        drawImageOrFill(
                g,
                panelImage,
                x,
                y,
                width,
                height,
                Color.WHITE
        );

        drawPanelBorder(g, x, y, width, height);

        int contentX = x + PANEL_PADDING;
        int contentY = y + PANEL_PADDING;
        int contentWidth = width - PANEL_PADDING * 2;

        int currentY = contentY + 10;

        for (BattleActor ally : encounter.getAllies()) {
            g.setColor(Color.WHITE);
            g.drawString(ally.getName(), contentX, currentY);

            drawHpBar(
                    g,
                    contentX + 100,
                    currentY - 12,
                    contentWidth - 120,
                    12,
                    ally
            );

            currentY += 35;
        }
    }

    private void drawHpBar(Graphics2D g, int x, int y, int width, int height, BattleActor actor) {
        double percent = actor.getCurrentHp() / (double) actor.getMaxHp();
        int filledWidth = (int) Math.round(width * percent);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(x, y, width, height);

        g.setColor(new Color(80, 180, 80));
        g.fillRect(x, y, filledWidth, height);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
    }

    private void drawImageOrFill(
            Graphics2D g,
            BufferedImage image,
            int x,
            int y,
            int width,
            int height,
            Color fallbackColor
    ) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(fallbackColor);
            g.fillRect(x, y, width, height);
        }
    }

    private void drawPanelBorder(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(Color.BLACK);
        g.drawRect(x, y, width - 1, height - 1);
    }

    private String trimTextToFit(Graphics2D g, String text, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();

        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";

        for (int i = text.length() - 1; i >= 0; i--) {
            String candidate = text.substring(0, i) + ellipsis;

            if (metrics.stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }

        return ellipsis;
    }
}
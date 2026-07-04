package org.main.battle;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class BattlePanel extends JPanel {
    private final BattleEncounter encounter;

    public BattlePanel(BattleEncounter encounter) {
        this.encounter = encounter;
        setBackground(Color.BLACK);
        setFocusable(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth();
        int h = getHeight();

        int battleAreaHeight = (int) (h * 0.70);
        int bottomHeight = h - battleAreaHeight;
        int menuWidth = w / 2;

        drawBattleArea(g, 0, 0, w, battleAreaHeight);
        drawCommandMenu(g, 0, battleAreaHeight, menuWidth, bottomHeight);
        drawPartyStatus(g, menuWidth, battleAreaHeight, w - menuWidth, bottomHeight);
    }

    private void drawBattleArea(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(235, 235, 230));
        g.fillRect(x, y, width, height);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width - 1, height - 1);

        drawAllies(g, x, y, width / 2, height);
        drawEnemies(g, x + width / 2, y, width / 2, height);
    }

    private void drawAllies(Graphics2D g, int x, int y, int width, int height) {
        List<BattleActor> allies = encounter.getAllies();

        for (int i = 0; i < allies.size(); i++) {
            BattleActor ally = allies.get(i);

            int spriteSize = 96;
            int spacing = 120;

            int drawX = x + 80;
            int drawY = y + 60 + i * spacing;

            drawActorSprite(g, ally, drawX, drawY, spriteSize, spriteSize);
        }
    }

    private void drawEnemies(Graphics2D g, int x, int y, int width, int height) {
        List<BattleActor> enemies = encounter.getEnemies();

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

    private void drawCommandMenu(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width - 1, height - 1);

        int textX = x + 20;
        int textY = y + 35;

        g.drawString("Attack", textX, textY);
        g.drawString("Skill", textX, textY + 28);
        g.drawString("Run", textX, textY + 56);
    }

    private void drawPartyStatus(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width - 1, height - 1);

        int currentY = y + 30;

        for (BattleActor ally : encounter.getAllies()) {
            g.setColor(Color.BLACK);
            g.drawString(ally.getName(), x + 20, currentY);

            drawHpBar(g, x + 120, currentY - 12, width - 160, 12, ally);

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
}
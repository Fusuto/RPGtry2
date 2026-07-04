package org.main.battle;

import java.awt.image.BufferedImage;

public class BattleActor {
    private final String name;
    private final int maxHp;
    private int currentHp;
    private final BufferedImage image;
    private final boolean enemy;

    public BattleActor(String name, int maxHp, int currentHp, BufferedImage image, boolean enemy) {
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = currentHp;
        this.image = image;
        this.enemy = enemy;
    }

    public String getName() {
        return name;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public BufferedImage getImage() {
        return image;
    }

    public boolean isEnemy() {
        return enemy;
    }

    public void takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public boolean isAlive() {
        return currentHp > 0;
    }
}
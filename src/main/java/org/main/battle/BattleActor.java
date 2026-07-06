package org.main.battle;

import org.main.engine.EntityType;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleActor {
    private final String name;
    private final int maxHp;
    private final BufferedImage image;
    private final boolean enemy;
    private int currentHp;
    private int slot = 0;
    private BattleRow row = BattleRow.FRONT;
    private final EntityType entityType;

    private final List<BattleSkill> skills = new ArrayList<>();

    public BattleActor(String name, int maxHp, int currentHp, BufferedImage image, boolean enemy, EntityType entityType) {
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = currentHp;
        this.image = image;
        this.enemy = enemy;
        this.entityType = entityType;
    }

    public List<BattleSkill> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public void addSkill(BattleSkill skill) {
        if (skill != null) {
            skills.add(skill);
        }
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public BattleRow getRow() {
        return row;
    }

    public int getSlot() {
        return slot;
    }

    public void setBattlePosition(BattleRow row, int slot) {
        this.row = row;
        this.slot = slot;
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
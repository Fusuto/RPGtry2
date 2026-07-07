package org.main.battle;

import org.main.core.Library;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleActor {
    private final String name;
    private final int maxHp;
    private final BufferedImage image;
    private int currentHp;
    private int slot = 0;
    private Library.BattleRow row = Library.BattleRow.FRONT;
    private final Library.EntityType EntityType;
    private final int attackDamage;
    private String attackSoundPath;
    private String hitSoundPath;

    private final List<BattleSkill> skills = new ArrayList<>();

    public BattleActor(String name, int maxHp, int currentHp, BufferedImage image, Library.EntityType entityType) {
        this(name, maxHp, currentHp, image, entityType, 5);
    }

    public BattleActor(
            String name,
            int maxHp,
            int currentHp,
            BufferedImage image,
            Library.EntityType entityType,
            int attackDamage
    ) {
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = currentHp;
        this.image = image;
        this.EntityType = entityType;
        this.attackDamage = attackDamage;
    }

    public List<BattleSkill> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public void addSkill(BattleSkill skill) {
        if (skill != null) {
            skills.add(skill);
        }
    }

    public Library.EntityType getEntityType() {
        return EntityType;
    }

    public Library.BattleRow getRow() {
        return row;
    }

    public int getSlot() {
        return slot;
    }

    public void setBattlePosition(Library.BattleRow row, int slot) {
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
        return EntityType == Library.EntityType.ENEMY;
    }

    public String getAttackSoundPath() {
        return attackSoundPath;
    }

    public void setAttackSoundPath(String attackSoundPath) {
        this.attackSoundPath = attackSoundPath;
    }

    public String getHitSoundPath() {
        return hitSoundPath;
    }

    public void setHitSoundPath(String hitSoundPath) {
        this.hitSoundPath = hitSoundPath;
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public void takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public void healDamage(int amount) {
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    public boolean isAlive() {
        return currentHp > 0;
    }
}

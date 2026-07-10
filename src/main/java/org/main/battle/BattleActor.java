package org.main.battle;

import org.main.core.Library;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BattleActor {
    private final String name;
    private final int maxHp;
    private final BufferedImage image;
    private int currentHp;
    private int slot = 0;
    private Library.BattleRow row = Library.BattleRow.FRONT;
    private final Library.EntityType EntityType;
    private final int attackDamage;
    private final int defense;
    private String attackSoundPath;
    private String hitSoundPath;
    private int defendingTurns = 0;
    private double defenseDamageReduction = 0.0;
    private int intelligence = 0;
    private String speciesId;
    private int experienceReward = 0;

    private final List<BattleSkill> skills = new ArrayList<>();
    private final Map<BattleStatusType, BattleStatus> statuses = new EnumMap<>(BattleStatusType.class);

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
        this(name, maxHp, currentHp, image, entityType, attackDamage, 0);
    }

    public BattleActor(
            String name,
            int maxHp,
            int currentHp,
            BufferedImage image,
            Library.EntityType entityType,
            int attackDamage,
            int defense
    ) {
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = currentHp;
        this.image = image;
        this.EntityType = entityType;
        this.attackDamage = attackDamage;
        this.defense = Math.max(0, defense);
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

    public int takeDamage(int amount) {
        int adjustedAmount = amount;

        if (defendingTurns > 0) {
            adjustedAmount = (int) Math.ceil(amount * (1.0 - defenseDamageReduction));
        }

        adjustedAmount = Math.max(0, adjustedAmount - defense);
        currentHp = Math.max(0, currentHp - adjustedAmount);
        return adjustedAmount;
    }

    public void healDamage(int amount) {
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public boolean isStunned() {
        return hasStatus(BattleStatusType.STUN);
    }

    public int getStunnedTurns() {
        BattleStatus status = statuses.get(BattleStatusType.STUN);
        return status == null ? 0 : status.getRemainingTurns();
    }

    public int getDefendingTurns() {
        return defendingTurns;
    }

    public void applyStun(int turns) {
        applyStatus(BattleStatusType.STUN, turns);
    }

    public void applyStatus(BattleStatusType type, int turns) {
        if (type == null || turns <= 0) {
            return;
        }

        BattleStatus status = statuses.get(type);

        if (status == null) {
            statuses.put(type, new BattleStatus(type, turns));
            return;
        }

        status.refresh(turns);
    }

    public boolean hasStatus(BattleStatusType type) {
        BattleStatus status = statuses.get(type);
        return status != null && !status.isExpired();
    }

    public List<BattleStatus> getStatuses() {
        return List.copyOf(statuses.values());
    }

    public void applyDefend(int turns, double damageReduction) {
        defendingTurns = Math.max(defendingTurns, turns);
        defenseDamageReduction = Math.max(defenseDamageReduction, damageReduction);
    }

    public void tickStatusDurations() {
        if (defendingTurns > 0) {
            defendingTurns--;
        }

        if (defendingTurns == 0) {
            defenseDamageReduction = 0.0;
        }

        Iterator<BattleStatus> iterator = statuses.values().iterator();

        while (iterator.hasNext()) {
            BattleStatus status = iterator.next();
            status.tick();

            if (status.isExpired()) {
                iterator.remove();
            }
        }
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = Math.max(0, Math.min(10, intelligence));
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public void setSpeciesId(String speciesId) {
        this.speciesId = speciesId;
    }

    public int getExperienceReward() {
        return experienceReward;
    }

    public void setExperienceReward(int experienceReward) {
        this.experienceReward = Math.max(0, experienceReward);
    }
}

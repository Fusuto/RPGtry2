package org.main.monsters;

public class Monster {
    private final MonsterType type;

    private int currentHp;

    public Monster(MonsterType type) {
        this.type = type;
        this.currentHp = type.getMaxHp();
    }

    public MonsterType getType() {
        return type;
    }

    public String getName() {
        return type.getDisplayName();
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return type.getMaxHp();
    }

    public int getAttack() {
        return type.getAttack();
    }

    public int getDefense() {
        return type.getDefense();
    }

    public int getXpReward() {
        return type.getXpReward();
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public void takeDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public void heal(int amount) {
        currentHp = Math.min(getMaxHp(), currentHp + amount);
    }
}
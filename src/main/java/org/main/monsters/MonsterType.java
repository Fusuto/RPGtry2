package org.main.monsters;

public enum MonsterType {
    SLIME(
            "Slime",
            6,
            2,
            0,
            5,
            "A quivering mass of dungeon slime."
    ),

    GOBLIN(
            "Goblin",
            10,
            4,
            1,
            12,
            "A wiry goblin clutching a crude blade."
    ),

    SKELETON(
            "Skeleton",
            12,
            5,
            2,
            18,
            "A rattling corpse animated by old magic."
    );

    private final String displayName;
    private final int maxHp;
    private final int attack;
    private final int defense;
    private final int xpReward;
    private final String description;

    MonsterType(
            String displayName,
            int maxHp,
            int attack,
            int defense,
            int xpReward,
            String description
    ) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.xpReward = xpReward;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getAttack() {
        return attack;
    }

    public int getDefense() {
        return defense;
    }

    public int getXpReward() {
        return xpReward;
    }

    public String getDescription() {
        return description;
    }
}
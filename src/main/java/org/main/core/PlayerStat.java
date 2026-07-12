package org.main.core;

public enum PlayerStat {
    ATTACK("Attack"),
    STRENGTH("Strength"),
    DEFENSE("Defense"),
    AGILITY("Agility"),
    INTELLIGENCE("Intelligence"),
    WILLPOWER("Willpower"),
    VITALITY("Vitality");

    private final String displayName;

    PlayerStat(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

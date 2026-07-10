package org.main.core;

public enum PlayerStat {
    STRENGTH("Strength"),
    DEFENSE("Defense"),
    AGILITY("Agility"),
    INTELLIGENCE("Intelligence"),
    VITALITY("Vitality");

    private final String displayName;

    PlayerStat(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

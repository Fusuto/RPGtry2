package org.main.core;

public enum CharacterSkill {
    ATTACK("Attack"),
    STRENGTH("Strength"),
    DEFENSE("Defense"),
    MAGIC_ACCURACY("Magic Accuracy"),
    MAGIC_POWER("Magic Power"),
    MINING("Mining"),
    SMITHING("Smithing"),
    FISHING("Fishing"),
    COOKING("Cooking"),
    BUTCHERING("Butchering"),
    GRAFTING("Grafting");

    private final String displayName;

    CharacterSkill(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

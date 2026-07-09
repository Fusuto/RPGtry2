package org.main.core;

public enum CharacterSkill {
    MINING("Mining"),
    SMITHING("Smithing"),
    FISHING("Fishing"),
    COOKING("Cooking");

    private final String displayName;

    CharacterSkill(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

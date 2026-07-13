package org.main.content;

public enum InteractionLibrary {
    MERCHANT_TRADE("Merchant Trade", "merchant_basic", false, true),
    OLD_GUARD_INTRO("Old Guard Intro", "old_guard_intro", false, true),
    CHEST_PROMPT("Chest Prompt", "chest_basic", true, true),
    DUNGEON_EXIT("Dungeon Exit", "dungeon_exit", true, true),
    GENERATED_DUNGEON_GATE("Generated Dungeon Gate", "generated_dungeon_gate", true, false);

    private final String displayName;
    private final String interactionId;
    private final boolean placeable;
    private final boolean followUp;

    InteractionLibrary(String displayName, String interactionId, boolean placeable, boolean followUp) {
        this.displayName = displayName;
        this.interactionId = interactionId;
        this.placeable = placeable;
        this.followUp = followUp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInteractionId() {
        return interactionId;
    }

    public boolean isPlaceable() {
        return placeable;
    }

    public boolean isFollowUp() {
        return followUp;
    }
}

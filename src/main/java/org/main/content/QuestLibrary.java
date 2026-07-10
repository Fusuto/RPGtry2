package org.main.content;

import java.util.List;

public enum QuestLibrary {
    SKELETON_HAT(
            "skeleton_hat",
            "Skeleton's Favor",
            List.of(
                    "Speak with the skeleton in the test dungeon.",
                    "Kill a slime, then return to the skeleton.",
                    "Ask the merchant for a hat.",
                    "Bring the hat back to the skeleton.",
                    "Complete. The skeleton has been properly capped."
            )
    );

    private final String id;
    private final String displayName;
    private final List<String> stageDescriptions;

    QuestLibrary(String id, String displayName, List<String> stageDescriptions) {
        this.id = id;
        this.displayName = displayName;
        this.stageDescriptions = List.copyOf(stageDescriptions);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxStage() {
        return stageDescriptions.size() - 1;
    }

    public boolean isComplete(int stage) {
        return stage >= getMaxStage();
    }

    public String getStageDescription(int stage) {
        int safeStage = Math.max(0, Math.min(getMaxStage(), stage));
        return stageDescriptions.get(safeStage);
    }
}

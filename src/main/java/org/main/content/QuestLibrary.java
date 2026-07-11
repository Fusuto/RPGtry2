package org.main.content;

import org.main.core.CharacterSkill;
import org.main.core.LimbItem;

import java.util.List;

public enum QuestLibrary {
    SKELETON_HAT(
            "skeleton_hat",
            "Tipping the Hat",
            List.of(
                    "Speak with the skeleton in the test dungeon.",
                    "Kill a slime, then return to the skeleton.",
                    "Tell the skeleton the slime is dead.",
                    "Ask the merchant for a hat, then return to the skeleton.",
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

    public static String skillExperienceRewardText(CharacterSkill skill, int amount) {
        String skillName = skill == null ? "Skill" : skill.getDisplayName();
        return "+" + Math.max(0, amount) + " " + skillName + " xp";
    }

    public static String limbRewardText(LimbItem limb) {
        if (limb == null) {
            return "+1 Limb";
        }

        return "+1 " + limb.getName();
    }
}

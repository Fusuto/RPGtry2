package org.main.core;

import org.main.content.MapDesignLibrary;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared validation, application, and rollback primitives for quest/dialogue rewards.
 */
public final class QuestRewardService {
    private QuestRewardService() {
    }

    public static String validate(GameState gameState, MapDesignLibrary.RewardDefinition reward) {
        if (gameState == null || reward == null) {
            return "Reward data is missing.";
        }
        if (reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP) {
            return reward.skill() == null ? "A skill XP reward has no skill assigned." : "";
        }
        String itemId = itemId(reward);
        return gameState.createItemByNameOrId(itemId) == null ? "Quest reward item is missing: " + itemId : "";
    }

    public static String validateAll(GameState gameState, List<MapDesignLibrary.RewardDefinition> rewards) {
        if (rewards == null) {
            return "";
        }
        for (MapDesignLibrary.RewardDefinition reward : rewards) {
            String issue = validate(gameState, reward);
            if (!issue.isBlank()) {
                return issue;
            }
        }
        return "";
    }

    public static boolean grant(GameState gameState, MapDesignLibrary.RewardDefinition reward) {
        if (gameState == null || reward == null) {
            return false;
        }
        if (reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP) {
            if (reward.skill() == null || gameState.getPlayerCharacter() == null) {
                return false;
            }
            gameState.getPlayerCharacter().addSkillExperience(reward.skill(), reward.amount());
            return true;
        }
        return grantInventory(gameState, reward);
    }

    public static boolean grantInventory(GameState gameState, MapDesignLibrary.RewardDefinition reward) {
        if (gameState == null || reward == null || reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP) {
            return false;
        }
        InventorySystem.Item first = gameState.createItemByNameOrId(itemId(reward));
        if (first == null) {
            return false;
        }
        if (first.isStackable()) {
            first.addQuantity(Math.max(0, reward.amount() - 1));
            return gameState.getInventory().addItem(first);
        }
        for (int count = 0; count < reward.amount(); count++) {
            InventorySystem.Item item = count == 0 ? first : gameState.createItemByNameOrId(itemId(reward));
            if (item == null || !gameState.getInventory().addItem(item)) {
                return false;
            }
        }
        return true;
    }

    public static PlayerProgressSnapshot capturePlayerProgress(GameState gameState) {
        PlayerCharacter player = gameState == null ? null : gameState.getPlayerCharacter();
        if (player == null) {
            return new PlayerProgressSnapshot(Map.of(), Map.of());
        }
        return new PlayerProgressSnapshot(player.getSkillsView(), player.getSkillExperienceView());
    }

    public static void restorePlayerProgress(GameState gameState, PlayerProgressSnapshot snapshot) {
        if (gameState == null || gameState.getPlayerCharacter() == null || snapshot == null) {
            return;
        }
        for (CharacterSkill skill : CharacterSkill.values()) {
            gameState.getPlayerCharacter().setSkillLevel(skill, snapshot.skillLevels().getOrDefault(skill, 0));
            gameState.getPlayerCharacter().setSkillExperience(skill, snapshot.skillExperience().getOrDefault(skill, 0));
        }
    }

    private static String itemId(MapDesignLibrary.RewardDefinition reward) {
        return reward.type() == MapDesignLibrary.QuestRewardType.GOLD ? "GOLD" : reward.itemId();
    }

    public record PlayerProgressSnapshot(
            Map<CharacterSkill, Integer> skillLevels,
            Map<CharacterSkill, Integer> skillExperience
    ) {
        public PlayerProgressSnapshot {
            skillLevels = immutableEnumMap(skillLevels);
            skillExperience = immutableEnumMap(skillExperience);
        }

        private static Map<CharacterSkill, Integer> immutableEnumMap(Map<CharacterSkill, Integer> source) {
            EnumMap<CharacterSkill, Integer> copy = new EnumMap<>(CharacterSkill.class);
            if (source != null) copy.putAll(source);
            return Map.copyOf(copy);
        }
    }
}

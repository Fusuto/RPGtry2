package org.main.core;

import org.main.content.MapDesignLibrary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the skill guide from the same authored/runtime rules used by gameplay. */
public final class SkillProgressionCatalog {
    private SkillProgressionCatalog() {
    }

    public enum Kind { EQUIPMENT, ACTIVITY }

    public record Unlock(
            CharacterSkill skill,
            int requiredLevel,
            String stableId,
            String displayName,
            Kind kind,
            InventorySystem.Item item
    ) {
        public Unlock {
            requiredLevel = Math.max(1, requiredLevel);
            stableId = stableId == null ? "" : stableId;
            displayName = displayName == null || displayName.isBlank() ? stableId : displayName;
            kind = kind == null ? Kind.ACTIVITY : kind;
        }
    }

    public static List<Unlock> forSkill(CharacterSkill selectedSkill, GameState gameState) {
        if (selectedSkill == null) {
            return List.of();
        }
        Map<String, Unlock> unlocks = new LinkedHashMap<>();
        try {
            MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
            Map<String, MapDesignLibrary.CustomItem> items = new LinkedHashMap<>();
            for (MapDesignLibrary.CustomItem item : content.customItems()) {
                items.put(item.itemId(), item);
                if (item.equipmentSkill() == selectedSkill) {
                    add(unlocks, new Unlock(selectedSkill,
                            EquipmentRequirementRules.requiredLevel(selectedSkill, item.material()),
                            "equipment:" + item.itemId(), item.displayName(), Kind.EQUIPMENT, item.createItem()));
                }
            }

            List<MapDesignLibrary.CustomGatheringNode> nodes = gameState == null
                    ? content.customGatheringNodes() : gameState.getCustomGatheringNodes();
            for (MapDesignLibrary.CustomGatheringNode node : nodes) {
                if (node.gatheringSkill() == selectedSkill) {
                    String outputName = itemName(items, node.outputItemId(), node.displayName());
                    add(unlocks, new Unlock(selectedSkill, node.requiredLevel(),
                            "gather:" + node.nodeId(), outputName, Kind.ACTIVITY, null));
                }
                if (selectedSkill == CharacterSkill.SMITHING && !node.smeltOutputItemId().isBlank()) {
                    add(unlocks, new Unlock(selectedSkill, node.smeltRequiredLevel(),
                            "smelt-node:" + node.nodeId(),
                            itemName(items, node.smeltOutputItemId(), node.smeltOutputItemId()), Kind.ACTIVITY, null));
                }
            }

            List<MapDesignLibrary.CraftingRecipe> crafting = gameState == null
                    ? content.craftingRecipes() : gameState.getCraftingRecipes();
            for (MapDesignLibrary.CraftingRecipe recipe : crafting) {
                if (recipe.requiredSkill() == selectedSkill) {
                    add(unlocks, new Unlock(selectedSkill, recipe.requiredLevel(),
                            "craft:" + recipe.recipeId(), recipe.displayName(), Kind.ACTIVITY, null));
                }
                if (selectedSkill == CharacterSkill.SMITHING && !recipe.smeltOutputItemId().isBlank()) {
                    add(unlocks, new Unlock(selectedSkill, recipe.smeltRequiredLevel(),
                            "smelt-recipe:" + recipe.recipeId(),
                            itemName(items, recipe.smeltOutputItemId(), recipe.smeltOutputItemId()), Kind.ACTIVITY, null));
                }
            }

            List<MapDesignLibrary.CustomCookingRecipe> cooking = gameState == null
                    ? content.customCookingRecipes() : gameState.getCustomCookingRecipes();
            if (selectedSkill == CharacterSkill.COOKING) {
                for (MapDesignLibrary.CustomCookingRecipe recipe : cooking) {
                    add(unlocks, new Unlock(selectedSkill, recipe.requiredLevel(),
                            "cook:" + recipe.recipeId(), recipe.displayName(), Kind.ACTIVITY, null));
                }
            }

            if (selectedSkill == CharacterSkill.SMITHING) {
                for (CraftingSystem.SmithingRecipe recipe : CraftingSystem.allSmithingRecipes()) {
                    add(unlocks, new Unlock(selectedSkill, recipe.requiredLevel(),
                            "smith:" + recipe.previewItem().getContentId(), recipe.displayName(), Kind.ACTIVITY, null));
                }
            }
        } catch (IOException ignored) {
            // Built-in method unlocks below remain available if authored content is unavailable.
        }

        if (selectedSkill == CharacterSkill.BUTCHERING) {
            for (ButcherySystem.ButcheryMethod method : ButcherySystem.ButcheryMethod.values()) {
                add(unlocks, new Unlock(selectedSkill, method.requiredLevel(),
                        "butchery:" + method.name(), method.displayName(), Kind.ACTIVITY, null));
            }
        }
        if (selectedSkill == CharacterSkill.GRAFTING) {
            add(unlocks, new Unlock(selectedSkill, 1, "grafting:unskilled", "Unskilled", Kind.ACTIVITY, null));
            add(unlocks, new Unlock(selectedSkill, 5, "grafting:hazardous", "Hazardous", Kind.ACTIVITY, null));
            add(unlocks, new Unlock(selectedSkill, 5, "grafting:perfect", "Perfectly", Kind.ACTIVITY, null));
        }

        List<Unlock> result = new ArrayList<>(unlocks.values());
        result.sort(Comparator.comparingInt(Unlock::requiredLevel)
                .thenComparing(Unlock::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Unlock::stableId));
        return List.copyOf(result);
    }

    private static void add(Map<String, Unlock> unlocks, Unlock unlock) {
        String key = unlock.skill().name() + "|" + unlock.stableId();
        unlocks.putIfAbsent(key, unlock);
    }

    private static String itemName(
            Map<String, MapDesignLibrary.CustomItem> items,
            String itemId,
            String fallback) {
        MapDesignLibrary.CustomItem item = items.get(itemId);
        return item == null ? fallback : item.displayName();
    }
}

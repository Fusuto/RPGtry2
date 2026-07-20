package org.main.core;

import org.main.content.MapDesignLibrary;

import java.io.IOException;
import java.util.List;

public final class CraftingSystem {
    private CraftingSystem() {
    }

    public static boolean isSmeltableItem(InventorySystem.Item item) {
        return smeltingRecipeFor(item) != null;
    }

    public static SmeltingRecipe smeltingRecipeFor(InventorySystem.Item item) {
        if (item == null) {
            return null;
        }

        return customSmeltingRecipeFor(item);
    }

    public static boolean isSmithingMaterial(InventorySystem.Item item) {
        return item != null && !smithingRecipesForMaterial(item.getName()).isEmpty();
    }

    public static List<SmithingRecipe> smithingRecipesForMaterial(String materialName) {
        return customSmithingRecipesForMaterial(materialName);
    }

    public static InventorySystem.Item createSmithingResultByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        for (SmithingRecipe recipe : allSmithingRecipes()) {
            if (recipe.displayName().equalsIgnoreCase(displayName.trim())) {
                return recipe.createResult();
            }
        }

        return null;
    }

    public static boolean isSmithingResult(InventorySystem.Item item) {
        return item != null && createSmithingResultByDisplayName(item.getName()) != null;
    }

    private static List<SmithingRecipe> allSmithingRecipes() {
        return customSmithingRecipes();
    }

    public static String smithingMaterialNameFor(GearMaterial material) {
        if (material == null || material.getFamily() != GearMaterial.MaterialFamily.METAL) {
            return "";
        }

        return switch (material) {
            case COPPER -> "Copper Bar";
            case BRONZE -> "Bronze Bar";
            case IRON -> "Iron Bar";
            case STEEL -> "Steel Bar";
            case SILVER -> "Silver Bar";
            default -> material.getDisplayName() + " Bar";
        };
    }

    private static List<SmithingRecipe> customSmithingRecipesForMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return List.of();
        }

        return customSmithingRecipes().stream()
                .filter(recipe -> recipe.materialName().equalsIgnoreCase(materialName.trim()))
                .toList();
    }

    private static SmeltingRecipe customSmeltingRecipeFor(InventorySystem.Item item) {
        if (item == null) {
            return null;
        }

        try {
            MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
            for (MapDesignLibrary.CustomGatheringNode node : content.customGatheringNodes()) {
                if (node == null
                        || node.outputItemId().isBlank()
                        || node.smeltOutputItemId().isBlank()) {
                    continue;
                }

                InventorySystem.Item ore = createCustomContentItem(content, node.outputItemId());
                InventorySystem.Item bar = createCustomContentItem(content, node.smeltOutputItemId());
                if (ore != null && bar != null && ore.getName().equalsIgnoreCase(item.getName())) {
                    return new SmeltingRecipe(ore.getName(), bar.getName(), bar, node.smeltRequiredLevel(), node.smeltXpReward());
                }
            }

            for (MapDesignLibrary.CraftingRecipe recipe : content.craftingRecipes()) {
                if (recipe == null || recipe.outputItemId().isBlank() || recipe.smeltOutputItemId().isBlank()) {
                    continue;
                }

                InventorySystem.Item smeltInput = createCustomContentItem(content, recipe.outputItemId());
                InventorySystem.Item smeltOutput = createCustomContentItem(content, recipe.smeltOutputItemId());
                if (smeltInput != null && smeltOutput != null && smeltInput.getName().equalsIgnoreCase(item.getName())) {
                    return new SmeltingRecipe(
                            smeltInput.getName(),
                            smeltOutput.getName(),
                            smeltOutput,
                            recipe.smeltRequiredLevel(),
                            recipe.smeltXpReward()
                    );
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }

    private static InventorySystem.Item createCustomContentItem(MapDesignLibrary.AuthoredContent content, String itemIdOrName) {
        if (content == null || itemIdOrName == null || itemIdOrName.isBlank()) {
            return null;
        }

        for (MapDesignLibrary.CustomItem customItem : content.customItems()) {
            if (itemIdOrName.equalsIgnoreCase(customItem.itemId())
                    || itemIdOrName.equalsIgnoreCase(customItem.displayName())) {
                return customItem.createItem();
            }
        }

        return null;
    }

    private static List<SmithingRecipe> customSmithingRecipes() {
        try {
            return MapDesignLibrary.loadSharedContent().customItems().stream()
                    .filter(MapDesignLibrary.CustomItem::smithingRecipeEnabled)
                    .map(CraftingSystem::customSmithingRecipe)
                    .filter(recipe -> recipe != null)
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static SmithingRecipe customSmithingRecipe(MapDesignLibrary.CustomItem item) {
        if (item == null || !item.smithingRecipeEnabled()) {
            return null;
        }

        String materialName = smithingMaterialNameFor(item.material());
        if (materialName.isBlank()) {
            return null;
        }

        return new SmithingRecipe(
                item.displayName(),
                materialName,
                item.smithingRequiredBars(),
                item.smithingRequiredLevel(),
                item.smithingXpReward(),
                item.createItem()
        );
    }

    public record SmeltingRecipe(String inputName, String outputName, InventorySystem.Item outputItem, int requiredLevel, int xpReward) {
        public InventorySystem.Item createOutput() {
            return outputItem.copy();
        }
    }

    public record SmithingRecipe(
            String displayName,
            String materialName,
            int requiredBars,
            int requiredLevel,
            int xpReward,
            InventorySystem.Item previewItem
    ) {
        public InventorySystem.Item createResult() {
            return previewItem.copy();
        }
    }
}

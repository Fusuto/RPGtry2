package org.main.content;

import org.main.core.GearMaterial;
import org.main.core.InventorySystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeLibrary {
    private static final int COPPER_SMELTING_XP_REWARD = 7;
    private static final int COPPER_DAGGER_SMITHING_XP_REWARD = 25;
    private static final String DAGGER_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/dagger.png";
    private static final String MACE_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/mace1.png";
    private static final String MED_HELM_ICON_PATH = "assets/images/monster/Nov-2015/item/armour/headgear/helmet1.png";
    private static final String PLATE_ICON_PATH = "assets/images/monster/Nov-2015/item/armour/plate1.png";
    private static final String CHAIN_PLATE_ICON_PATH = "assets/images/monster/Nov-2015/item/armour/ring_mail1.png";
    private static final String SHIELD_ICON_PATH = "assets/images/monster/Nov-2015/item/armour/shields/shield1.png";

    private RecipeLibrary() {
    }

    public static boolean isSmeltableItem(InventorySystem.Item item) {
        return smeltingRecipeFor(item) != null;
    }

    public static SmeltingRecipe smeltingRecipeFor(InventorySystem.Item item) {
        if (item == null) {
            return null;
        }

        if (ItemLibrary.COPPER_ORE.getDisplayName().equalsIgnoreCase(item.getName())) {
            return new SmeltingRecipe(
                    ItemLibrary.COPPER_ORE.getDisplayName(),
                    ItemLibrary.COPPER_BAR,
                    COPPER_SMELTING_XP_REWARD
            );
        }

        return null;
    }

    public static boolean isSmithingMaterial(InventorySystem.Item item) {
        return item != null && !smithingRecipesForMaterial(item.getName()).isEmpty();
    }

    public static List<SmithingRecipe> smithingRecipesForMaterial(String materialName) {
        List<SmithingRecipe> recipes = new ArrayList<>();
        if (ItemLibrary.COPPER_BAR.getDisplayName().equalsIgnoreCase(materialName)) {
            recipes.add(copperDaggerRecipe());
            recipes.add(copperMaceRecipe());
            recipes.add(copperShieldRecipe());
        }

        recipes.addAll(customSmithingRecipesForMaterial(materialName));
        return dedupeRecipes(recipes);
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
        List<SmithingRecipe> recipes = new ArrayList<>();
        recipes.add(copperDaggerRecipe());
        recipes.add(copperMaceRecipe());
        recipes.add(copperShieldRecipe());
        recipes.addAll(customSmithingRecipes());
        return dedupeRecipes(recipes);
    }

    public static String smithingMaterialNameFor(GearMaterial material) {
        if (material == null || material.getFamily() != GearMaterial.MaterialFamily.METAL) {
            return "";
        }

        return switch (material) {
            case COPPER -> ItemLibrary.COPPER_BAR.getDisplayName();
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

    private static List<SmithingRecipe> customSmithingRecipes() {
        try {
            return MapDesignLibrary.loadSharedContent().customItems().stream()
                    .filter(MapDesignLibrary.CustomItem::smithingRecipeEnabled)
                    .map(RecipeLibrary::customSmithingRecipe)
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

    private static List<SmithingRecipe> dedupeRecipes(List<SmithingRecipe> recipes) {
        Map<String, SmithingRecipe> byKey = new LinkedHashMap<>();
        for (SmithingRecipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            byKey.put((recipe.materialName() + "|" + recipe.displayName()).toLowerCase(), recipe);
        }
        return List.copyOf(byKey.values());
    }

    private static SmithingRecipe copperDaggerRecipe() {
        InventorySystem.Item previewItem = new InventorySystem.Item(
                "Copper Dagger",
                InventorySystem.ItemType.WEAPON,
                DAGGER_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                28,
                "A small copper dagger. It is easy to shape, but too soft to trust for long."
        );

        return new SmithingRecipe(
                "Copper Dagger",
                ItemLibrary.COPPER_BAR.getDisplayName(),
                1,
                1,
                COPPER_DAGGER_SMITHING_XP_REWARD,
                previewItem
        );
    }

    private static SmithingRecipe copperMaceRecipe() {
        InventorySystem.Item previewItem = new InventorySystem.Item(
                "Copper Mace",
                InventorySystem.ItemType.WEAPON,
                MACE_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                32,
                "A small copper mace. It is easy to shape, but too soft to trust for long."
        );

        return new SmithingRecipe(
                "Copper Mace",
                ItemLibrary.COPPER_BAR.getDisplayName(),
                2,
                5,
                COPPER_DAGGER_SMITHING_XP_REWARD,
                previewItem
        );
    }

    private static SmithingRecipe copperMedHelmetRecipe() {
        String name = "Copper Medhelm";
        InventorySystem.Item previewItem = new InventorySystem.Item(
                name,
                InventorySystem.ItemType.HEAD_GEAR,
                MED_HELM_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                32,
                "A small copper mace. It is easy to shape, but too soft to trust for long."
        );

        return new SmithingRecipe(
                name,
                ItemLibrary.COPPER_BAR.getDisplayName(),
                1,
                1,
                28,
                previewItem
        );
    }

    private static SmithingRecipe copperChainArmorRecipe() {
        String name = "Copper Chain armor";
        InventorySystem.Item previewItem = new InventorySystem.Item(
                name,
                InventorySystem.ItemType.CHEST_ARMOR,
                CHAIN_PLATE_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                32,
                "A small copper mace. It is easy to shape, but too soft to trust for long."
        );

        return new SmithingRecipe(
                name,
                ItemLibrary.COPPER_BAR.getDisplayName(),
                1,
                1,
                28,
                previewItem
        );
    }

    private static SmithingRecipe copperShieldRecipe() {
        String name = "Copper Shield";
        InventorySystem.Item previewItem = new InventorySystem.Item(
                name,
                InventorySystem.ItemType.SHIELD,
                SHIELD_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                36,
                "A light copper shield. It dents easily, but it is still better than catching blows bare-handed."
        );

        return new SmithingRecipe(
                name,
                ItemLibrary.COPPER_BAR.getDisplayName(),
                2,
                1,
                32,
                previewItem
        );
    }

    public record SmeltingRecipe(String inputName, ItemLibrary output, int xpReward) {
        public InventorySystem.Item createOutput() {
            return output.createItem();
        }

        public String outputName() {
            return output.getDisplayName();
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

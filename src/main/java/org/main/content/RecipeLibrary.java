package org.main.content;

import org.main.core.GearMaterial;
import org.main.core.InventorySystem;

import java.awt.Color;
import java.util.List;

public final class RecipeLibrary {
    private static final int COPPER_SMELTING_XP_REWARD = 7;
    private static final int COPPER_DAGGER_SMITHING_XP_REWARD = 25;
    private static final Color COPPER_TINT = new Color(150, 82, 44);
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
        return item != null && ItemLibrary.COPPER_BAR.getDisplayName().equalsIgnoreCase(item.getName());
    }

    public static List<SmithingRecipe> smithingRecipesForMaterial(String materialName) {
        if (ItemLibrary.COPPER_BAR.getDisplayName().equalsIgnoreCase(materialName)) {
            return List.of(copperDaggerRecipe(), copperMaceRecipe(), copperShieldRecipe());
        }

        return List.of();
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
        return List.of(copperDaggerRecipe(), copperMaceRecipe(), copperShieldRecipe());
    }

    private static SmithingRecipe copperDaggerRecipe() {
        InventorySystem.Item previewItem = ItemLibrary.createTintedWeapon(
                "Copper Dagger",
                DAGGER_ICON_PATH,
                COPPER_TINT,
                GearMaterial.BRONZE,
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
        InventorySystem.Item previewItem = ItemLibrary.createTintedWeapon(
                "Copper Mace",
                MACE_ICON_PATH,
                COPPER_TINT,
                GearMaterial.BRONZE,
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
        InventorySystem.Item previewItem = ItemLibrary.createTintedHelmet(
                name,
                MED_HELM_ICON_PATH,
                COPPER_TINT,
                GearMaterial.BRONZE,
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
        InventorySystem.Item previewItem = ItemLibrary.createTintedArmor(
                name,
                CHAIN_PLATE_ICON_PATH,
                COPPER_TINT,
                GearMaterial.BRONZE,
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
        InventorySystem.Item previewItem = ItemLibrary.createTintedShield(
                name,
                SHIELD_ICON_PATH,
                COPPER_TINT,
                GearMaterial.BRONZE,
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

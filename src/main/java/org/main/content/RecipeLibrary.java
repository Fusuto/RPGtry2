package org.main.content;

import org.main.core.GearMaterial;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeLibrary {
    private static final int COPPER_SMELTING_XP_REWARD = 7;
    private static final int COPPER_DAGGER_SMITHING_XP_REWARD = 25;
    private static final String DAGGER_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/dagger.png";
    private static final String SWORD_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/short_sword1.png";
    private static final String MACE_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/mace1.png";
    private static final String GREATSWORD_ICON_PATH = "assets/images/monster/Nov-2015/item/weapon/greatsword1.png";
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

        return customSmeltingRecipeFor(item);
    }

    public static boolean isSmithingMaterial(InventorySystem.Item item) {
        return item != null && !smithingRecipesForMaterial(item.getName()).isEmpty();
    }

    public static List<SmithingRecipe> smithingRecipesForMaterial(String materialName) {
        List<SmithingRecipe> recipes = new ArrayList<>();
        if (ItemLibrary.COPPER_BAR.getDisplayName().equalsIgnoreCase(materialName)) {
            recipes.add(copperDaggerRecipe());
            recipes.add(copperSwordRecipe());
            recipes.add(copperMaceRecipe());
            recipes.add(copperGreatswordRecipe());
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
        recipes.add(copperSwordRecipe());
        recipes.add(copperMaceRecipe());
        recipes.add(copperGreatswordRecipe());
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

            for (MapDesignLibrary.CustomCompositeRecipe recipe : content.customCompositeRecipes()) {
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

        for (ItemLibrary libraryItem : ItemLibrary.values()) {
            if (itemIdOrName.equalsIgnoreCase(libraryItem.name())
                    || itemIdOrName.equalsIgnoreCase(libraryItem.getDisplayName())) {
                return libraryItem.createItem();
            }
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
                "A small copper dagger. It is easy to shape, quick to swing, but too soft to trust for long.",
                null,
                "",
                WeaponType.DAGGER
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

    private static SmithingRecipe copperSwordRecipe() {
        InventorySystem.Item previewItem = new InventorySystem.Item(
                "Copper Sword",
                InventorySystem.ItemType.WEAPON,
                SWORD_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                36,
                "A simple copper sword. It is steadier than a dagger, but still soft by real weapon standards.",
                null,
                "",
                WeaponType.SWORD
        );

        return new SmithingRecipe(
                "Copper Sword",
                ItemLibrary.COPPER_BAR.getDisplayName(),
                2,
                3,
                40,
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
                "A small copper mace. It swings slower than a sword, but lands with more force.",
                null,
                "",
                WeaponType.MACE
        );

        return new SmithingRecipe(
                "Copper Mace",
                ItemLibrary.COPPER_BAR.getDisplayName(),
                2,
                5,
                45,
                previewItem
        );
    }

    private static SmithingRecipe copperGreatswordRecipe() {
        InventorySystem.Item previewItem = new InventorySystem.Item(
                "Copper Greatsword",
                InventorySystem.ItemType.WEAPON,
                GREATSWORD_ICON_PATH,
                null,
                0,
                GearMaterial.COPPER,
                org.main.core.GearDurability.PERFECT,
                54,
                "A heavy copper greatsword. Slow to recover, but built to hit hard.",
                null,
                "",
                WeaponType.GREATSWORD
        );

        return new SmithingRecipe(
                "Copper Greatsword",
                ItemLibrary.COPPER_BAR.getDisplayName(),
                3,
                8,
                70,
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

    public record SmeltingRecipe(String inputName, String outputName, InventorySystem.Item outputItem, int requiredLevel, int xpReward) {
        public SmeltingRecipe(String inputName, ItemLibrary output, int xpReward) {
            this(inputName, output.getDisplayName(), output.createItem(), 1, xpReward);
        }

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

package org.main.core;

import org.junit.jupiter.api.Test;
import org.main.content.MapDesignLibrary;
import org.main.engine.DungeonMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElementalCraftingContentTest {
    @Test
    void seededContentIncludesDaggerCarvingAssemblyAndPillars() throws Exception {
        MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();

        List<MapDesignLibrary.CraftingRecipe> carving = content.craftingRecipes().stream()
                .filter(MapDesignLibrary.CraftingRecipe::usesWeaponTool)
                .toList();
        assertEquals(2, carving.size());
        assertTrue(carving.stream().allMatch(recipe -> recipe.requiredToolWeaponType() == WeaponType.DAGGER));
        assertEquals(10, content.craftingRecipes().stream()
                .filter(recipe -> recipe.recipeId().matches("recipe_oak_(wand|staff|fire_wand|fire_staff|frost_wand|frost_staff|storm_wand|storm_staff|earth_wand|earth_staff)"))
                .count());

        List<MapDesignLibrary.CustomFurnitureDefinition> pillars = content.customFurniture().stream()
                .filter(furniture -> furniture.attunementPillar() != null)
                .toList();
        assertEquals(4, pillars.size());
        assertEquals(List.of(10, 20, 30, 40), pillars.stream()
                .map(furniture -> furniture.attunementPillar().requiredLevel())
                .sorted().toList());

        MapDesignLibrary.CustomItem fireWand = content.customItems().stream()
                .filter(item -> item.itemId().equals("custom_item_oak_fire_wand"))
                .findFirst().orElseThrow();
        assertEquals(WeaponType.WAND, fireWand.weaponType());
        assertEquals(CombatElement.FIRE, fireWand.elementalAffinity());
        assertEquals(0.10, fireWand.matchingElementSpellDamageBonus(), 0.0001);
    }

    @Test
    void inventoryDaggerOpensCarveMenuAndIsNotAnIngredient() throws Exception {
        MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
        PlayerCharacter player = new PlayerCharacter("Crafter", 20, 20);
        GameState state = new GameState(floor(), player);
        state.setCustomItems(content.customItems());
        state.setCraftingRecipes(content.craftingRecipes());

        InventorySystem.Item dagger = authoredItem(content, "custom_item_bronze_dagger");
        InventorySystem.Item logs = authoredItem(content, "custom_item_oak_log");
        logs.addQuantity(1);
        assertTrue(player.getInventory().addItem(dagger));
        assertTrue(player.getInventory().addItem(logs));
        int daggerSlot = slotOf(player.getInventory(), dagger);
        int logSlot = slotOf(player.getInventory(), logs);

        state.selectWorldUseItem(daggerSlot);
        assertTrue(state.tryUseSelectedWorldItemOnInventoryItem(logSlot));
        assertEquals("Carve Oak Log", state.getActiveInteraction().getModel().getTitle());
        List<String> labels = state.getActiveInteraction().getModel().getOptions().stream()
                .map(InteractionSystem.InteractionOption::getLabel).toList();
        assertTrue(labels.stream().anyMatch(label -> label.contains("Oak Wand Blank") && label.contains("1 Oak Log")));
        assertTrue(labels.stream().anyMatch(label -> label.contains("Oak Staff Shaft") && label.contains("2 Oak Log")));
        assertSame(dagger, player.getInventory().getItem(daggerSlot));

        PlayerCharacter equippedOnlyPlayer = new PlayerCharacter("Equipped", 20, 20);
        GameState equippedOnlyState = new GameState(floor(), equippedOnlyPlayer);
        equippedOnlyState.setCustomItems(content.customItems());
        equippedOnlyState.setCraftingRecipes(content.craftingRecipes());
        equippedOnlyPlayer.getInventory().setEquippedItem(
                InventorySystem.EquipmentSlot.WEAPON,
                authoredItem(content, "custom_item_bronze_dagger"));
        InventorySystem.Item equippedOnlyLogs = authoredItem(content, "custom_item_oak_log");
        assertTrue(equippedOnlyPlayer.getInventory().addItem(equippedOnlyLogs));
        assertFalse(equippedOnlyState.tryUseSelectedWorldItemOnInventoryItem(
                slotOf(equippedOnlyPlayer.getInventory(), equippedOnlyLogs)));
    }

    @Test
    void pillarAttunesOneSelectedStoneAndCanBeUsedAgain() throws Exception {
        MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
        PlayerCharacter player = new PlayerCharacter("Crafter", 20, 20);
        player.setSkillLevel(CharacterSkill.CRAFTING, 10);
        GameState state = new GameState(floor(), player);
        state.setCustomItems(content.customItems());
        state.setCustomFurniture(content.customFurniture());

        InventorySystem.Item inert = authoredItem(content, "custom_item_inert_stone");
        assertTrue(player.getInventory().addItem(inert));
        state.selectWorldUseItem(slotOf(player.getInventory(), inert));
        player.setSkillLevel(CharacterSkill.CRAFTING, 9);
        assertFalse(state.attuneSelectedStone("pillar_flame").success());
        assertSame(inert, player.getInventory().getItem(slotOf(player.getInventory(), inert)));
        player.setSkillLevel(CharacterSkill.CRAFTING, 10);
        GameState.AttunementResult first = state.attuneSelectedStone("pillar_flame");
        assertTrue(first.success(), first.message());
        assertTrue(hasContentId(player.getInventory(), "custom_item_fire_stone"));

        InventorySystem.Item secondInert = authoredItem(content, "custom_item_inert_stone");
        assertTrue(player.getInventory().addItem(secondInert));
        state.selectWorldUseItem(slotOf(player.getInventory(), secondInert));
        assertTrue(state.attuneSelectedStone("pillar_flame").success());
    }

    @Test
    void ambiguousPairsOfferAChoiceWhileUniquePairsCraftImmediately() throws Exception {
        MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
        PlayerCharacter player = new PlayerCharacter("Assembler", 20, 20);
        GameState state = new GameState(floor(), player);
        state.setCustomItems(content.customItems());
        MapDesignLibrary.CraftingRecipe neutralWand = content.craftingRecipes().stream()
                .filter(recipe -> recipe.recipeId().equals("recipe_oak_wand")).findFirst().orElseThrow();
        MapDesignLibrary.CraftingRecipe alternate = new MapDesignLibrary.CraftingRecipe(
                "recipe_test_alternate_wand", "Alternate Oak Wand", neutralWand.category(),
                neutralWand.primaryItemId(), neutralWand.secondaryItemId(), "custom_item_oak_fire_wand",
                CharacterSkill.CRAFTING, 1, 0, true, true, "", 1, 0,
                1, 1, MapDesignLibrary.CraftingOutputType.ITEM, null, 0, WeaponType.NONE);
        state.setCraftingRecipes(List.of(alternate));
        InventorySystem.Item blank = authoredItem(content, "custom_item_oak_wand_blank");
        InventorySystem.Item inert = authoredItem(content, "custom_item_inert_stone");
        assertTrue(player.getInventory().addItem(blank));
        assertTrue(player.getInventory().addItem(inert));
        state.selectWorldUseItem(slotOf(player.getInventory(), blank));
        assertTrue(state.tryUseSelectedWorldItemOnInventoryItem(slotOf(player.getInventory(), inert)));
        assertTrue(state.getActiveInteraction().getModel().getOptions().size() >= 3);

        PlayerCharacter uniquePlayer = new PlayerCharacter("Assembler", 20, 20);
        GameState uniqueState = new GameState(floor(), uniquePlayer);
        uniqueState.setCustomItems(content.customItems());
        InventorySystem.Item shaft = authoredItem(content, "custom_item_oak_staff_shaft");
        InventorySystem.Item uniqueInert = authoredItem(content, "custom_item_inert_stone");
        assertTrue(uniquePlayer.getInventory().addItem(shaft));
        assertTrue(uniquePlayer.getInventory().addItem(uniqueInert));
        uniqueState.selectWorldUseItem(slotOf(uniquePlayer.getInventory(), shaft));
        assertTrue(uniqueState.tryUseSelectedWorldItemOnInventoryItem(
                slotOf(uniquePlayer.getInventory(), uniqueInert)));
        assertEquals("Oak Staff", uniqueState.getActiveInteraction().getModel().getTitle());
        assertTrue(hasContentId(uniquePlayer.getInventory(), "custom_item_oak_staff"));
    }

    private static InventorySystem.Item authoredItem(
            MapDesignLibrary.AuthoredContent content, String itemId) {
        return content.customItems().stream()
                .filter(item -> item.itemId().equals(itemId))
                .findFirst().orElseThrow().createItem();
    }

    private static int slotOf(InventorySystem.Inventory inventory, InventorySystem.Item item) {
        for (int index = 0; index < InventorySystem.Inventory.SLOT_COUNT; index++) {
            if (inventory.getItem(index) == item) return index;
        }
        return -1;
    }

    private static boolean hasContentId(InventorySystem.Inventory inventory, String itemId) {
        for (int index = 0; index < InventorySystem.Inventory.SLOT_COUNT; index++) {
            InventorySystem.Item item = inventory.getItem(index);
            if (item != null && itemId.equals(item.getContentId())) return true;
        }
        return false;
    }

    private static DungeonMap floor() {
        return new DungeonMap(new Library.TileType[][]{
                {Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.FLOOR}
        });
    }
}

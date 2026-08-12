package org.main.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.main.content.MapDesignLibrary;
import org.main.engine.DungeonMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanternSystemTest {
    private List<MaterialDefinition> originalMaterials;

    @BeforeEach
    void rememberMaterials() {
        originalMaterials = MaterialCatalog.snapshot().definitions();
    }

    @AfterEach
    void restoreMaterials() {
        MaterialCatalog.installSnapshot(originalMaterials);
    }

    @Test
    void proportionalTierMappingSupportsUnequalCatalogs() {
        List<MaterialDefinition> definitions = new ArrayList<>();
        definitions.add(material("none", GearMaterial.MaterialFamily.NONE, 0, "", 0, 0));
        for (int index = 1; index <= 4; index++) {
            definitions.add(material("metal_" + index, GearMaterial.MaterialFamily.METAL,
                    index * 10, "", 600, 0));
        }
        for (int index = 1; index <= 3; index++) {
            definitions.add(material("wood_" + index, GearMaterial.MaterialFamily.WOOD,
                    100 + index * 10, "log_" + index, 0, 300));
        }
        MaterialCatalog.installSnapshot(definitions);

        assertEquals(List.of("wood_1"), ids(LanternSystem.compatibleWoodMaterials("metal_1")));
        assertEquals(List.of("wood_1", "wood_2"), ids(LanternSystem.compatibleWoodMaterials("metal_2")));
        assertEquals(List.of("wood_1", "wood_2", "wood_3"), ids(LanternSystem.compatibleWoodMaterials("metal_4")));
    }

    @Test
    void refuelConsumesExactlyOneLogAndReportsOverflow() {
        MaterialCatalog.installSnapshot(List.of(
                material("none", GearMaterial.MaterialFamily.NONE, 0, "", 0, 0),
                material("copper", GearMaterial.MaterialFamily.METAL, 10, "", 600, 0),
                material("tin", GearMaterial.MaterialFamily.METAL, 15, "", 600, 0),
                material("oak", GearMaterial.MaterialFamily.WOOD, 20, "oak_log", 0, 300),
                material("yew", GearMaterial.MaterialFamily.WOOD, 30, "yew_log", 0, 500)));
        InventorySystem.Inventory inventory = new InventorySystem.Inventory();
        InventorySystem.Item logs = stack("Oak Logs", "oak_log", 3);
        assertTrue(inventory.addItemAt(logs, 0));
        InventorySystem.Item lantern = lantern("copper").withLanternFuelMillis(500_000L);

        LanternSystem.RefuelResult result = LanternSystem.refuel(inventory, 0, lantern);

        assertTrue(result.success());
        assertEquals(600_000L, lantern.getLanternFuelMillis());
        assertEquals(100_000L, result.acceptedMillis());
        assertEquals(200_000L, result.overflowMillis());
        assertEquals(2, inventory.getItem(0).getQuantity());
        assertTrue(result.message().contains("discarded"));
    }

    @Test
    void incompatibleAndFullRefuelsConsumeNothing() {
        MaterialCatalog.installSnapshot(List.of(
                material("none", GearMaterial.MaterialFamily.NONE, 0, "", 0, 0),
                material("copper", GearMaterial.MaterialFamily.METAL, 10, "", 600, 0),
                material("tin", GearMaterial.MaterialFamily.METAL, 15, "", 600, 0),
                material("oak", GearMaterial.MaterialFamily.WOOD, 20, "oak_log", 0, 300),
                material("yew", GearMaterial.MaterialFamily.WOOD, 30, "yew_log", 0, 500)));
        InventorySystem.Inventory inventory = new InventorySystem.Inventory();
        assertTrue(inventory.addItemAt(stack("Yew Logs", "yew_log", 2), 0));
        InventorySystem.Item lantern = lantern("copper");

        assertFalse(LanternSystem.refuel(inventory, 0, lantern).success());
        assertEquals(2, inventory.getItem(0).getQuantity());
        assertEquals(0L, lantern.getLanternFuelMillis());

        lantern.setLanternFuelMillis(lantern.getLanternCapacityMillis());
        inventory.removeItem(0);
        assertTrue(inventory.addItemAt(stack("Oak Logs", "oak_log", 2), 0));
        assertFalse(LanternSystem.refuel(inventory, 0, lantern).success());
        assertEquals(2, inventory.getItem(0).getQuantity());
    }

    @Test
    void equippedLanternBurnsOnlyDuringActiveExplorationAndSnapshotPreservesFuel() {
        MaterialCatalog.installSnapshot(List.of(
                material("none", GearMaterial.MaterialFamily.NONE, 0, "", 0, 0),
                material("copper", GearMaterial.MaterialFamily.METAL, 10, "", 600, 0),
                material("oak", GearMaterial.MaterialFamily.WOOD, 20, "oak_log", 0, 300)));
        Library.TileType[][] tiles = {{Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.FLOOR}};
        GameState state = new GameState(new DungeonMap(tiles), new PlayerCharacter("Lantern Tester", 10, 10));
        state.setGameMode(GameState.GameMode.DUNGEON);
        InventorySystem.Item lantern = lantern("copper").withLanternFuelMillis(2_500L);
        state.getInventory().setEquippedItem(InventorySystem.EquipmentSlot.POCKET, lantern);

        assertTrue(LanternSystem.updateFuel(state, 1_000));
        assertEquals(1_500L, lantern.getLanternFuelMillis());
        state.setInventoryOpen(true);
        assertFalse(LanternSystem.updateFuel(state, 1_000));
        assertEquals(1_500L, lantern.getLanternFuelMillis());
        state.closeInventory();
        state.setGameMode(GameState.GameMode.BATTLE);
        assertFalse(LanternSystem.updateFuel(state, 1_000));
        assertNotNull(LanternSystem.activeLantern(state.getInventory()),
                "the light remains active during battle");

        InventorySystem.Inventory.Snapshot snapshot = state.getInventory().snapshot();
        lantern.setLanternFuelMillis(0L);
        state.getInventory().restore(snapshot);
        InventorySystem.Item restored = state.getInventory()
                .getEquippedItem(InventorySystem.EquipmentSlot.POCKET);
        assertEquals(1_500L, restored.getLanternFuelMillis());
        state.setGameMode(GameState.GameMode.DUNGEON);
        restored.setLanternFuelMillis(500L);
        int messagesBefore = state.getWorldMessageLog().entries().size();
        assertTrue(LanternSystem.updateFuel(state, 1_000));
        assertEquals(0L, restored.getLanternFuelMillis());
        assertEquals(messagesBefore + 1, state.getWorldMessageLog().entries().size());
        assertFalse(LanternSystem.updateFuel(state, 1_000));
        assertEquals(messagesBefore + 1, state.getWorldMessageLog().entries().size());
    }

    @Test
    void copperLanternRecipeAndSaveRoundTripUseAuthoredValues() throws Exception {
        assertEquals(List.of("oak"), ids(LanternSystem.compatibleWoodMaterials("copper")));
        assertEquals(List.of("oak"), ids(LanternSystem.compatibleWoodMaterials("tin")));
        assertEquals(List.of("oak", "yew"), ids(LanternSystem.compatibleWoodMaterials("bronze")));
        assertEquals(List.of("oak", "yew"), ids(LanternSystem.compatibleWoodMaterials("silver")));
        assertEquals(List.of("oak", "yew", "ironwood"), ids(LanternSystem.compatibleWoodMaterials("iron")));
        MapDesignLibrary.CustomItem definition = MapDesignLibrary.loadSharedContent().customItems().stream()
                .filter(item -> item.itemId().equals("custom_item_copper_lantern"))
                .findFirst().orElseThrow();
        assertEquals(InventorySystem.ItemType.UTILITY, definition.itemType());
        assertEquals(2, definition.smithingRequiredBars());
        assertEquals(1, definition.smithingRequiredLevel());
        assertEquals(30, definition.baseGoldValue());
        CraftingSystem.SmithingRecipe recipe = CraftingSystem.allSmithingRecipes().stream()
                .filter(candidate -> candidate.previewItem().getContentId().equals(definition.itemId()))
                .findFirst().orElseThrow();
        assertEquals("COPPER_BAR", recipe.barItemId());
        assertEquals(24, recipe.xpReward());
        assertEquals(0L, recipe.createResult().getLanternFuelMillis());

        InventorySystem.Item item = definition.createItem().withLanternFuelMillis(123_456L);
        Method writer = SaveSystem.class.getDeclaredMethod("itemKey", InventorySystem.Item.class);
        Method reader = SaveSystem.class.getDeclaredMethod("readInventoryItem", String.class);
        writer.setAccessible(true);
        reader.setAccessible(true);
        String serialized = (String) writer.invoke(null, item);
        InventorySystem.Item restored = (InventorySystem.Item) reader.invoke(null, serialized);
        assertNotNull(restored);
        assertEquals(123_456L, restored.getLanternFuelMillis());
    }

    private static MaterialDefinition material(
            String id, GearMaterial.MaterialFamily family, int order, String raw,
            int capacity, int burnSeconds) {
        return new MaterialDefinition(id, id, family, order, 0, 1.0, 0xFFFFFF, 0.0f,
                raw, "", capacity, burnSeconds);
    }

    private static InventorySystem.Item lantern(String materialId) {
        return new InventorySystem.Item("Lantern", InventorySystem.ItemType.UTILITY, (java.awt.image.BufferedImage) null,
                "", 0, GearMaterial.of(materialId), GearDurability.PERFECT, 30)
                .withContentId("lantern")
                .withLanternDefinition(LanternDefinition.pocketLanternDefaults());
    }

    private static InventorySystem.Item stack(String name, String id, int quantity) {
        return new InventorySystem.Item(name, InventorySystem.ItemType.MISC, (java.awt.image.BufferedImage) null,
                "", 0, GearMaterial.NONE, GearDurability.PERFECT, 1, "", null,
                true, quantity).withContentId(id);
    }

    private static List<String> ids(List<MaterialDefinition> definitions) {
        return definitions.stream().map(MaterialDefinition::id).toList();
    }
}

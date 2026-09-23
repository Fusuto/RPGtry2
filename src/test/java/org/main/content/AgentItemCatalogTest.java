package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentItemCatalogTest {
    @TempDir Path directory;

    @Test void itemCatalogSurvivesArbitraryStagingFilename() throws Exception {
        var original = MapDesignLibrary.loadContentSegment(
                Path.of("src/main/resources/assets/editor/content/item.properties")).customItems();
        Path stage = directory.resolve("random-temporary-name.properties");
        MapDesignLibrary.saveItemCatalog(original, stage);
        Path published = directory.resolve("item.properties");
        Files.move(stage, published);
        assertEquals(original, MapDesignLibrary.loadContentSegment(published).customItems());
        assertTrue(original.size() > 10);
    }

    @Test void maceIsConfiguredAndPlacedInItemTestMap() throws Exception {
        var items = MapDesignLibrary.loadContentSegment(
                Path.of("src/main/resources/assets/editor/content/item.properties")).customItems();
        var mace = items.stream().filter(i -> i.itemId().equals("custom_item_iron_mace")).findFirst().orElseThrow();
        assertEquals(org.main.core.WeaponType.MACE, mace.weaponType());
        assertEquals(org.main.core.GearMaterial.IRON, mace.material());
        assertFalse(mace.twoHanded());
        assertFalse(mace.stackable());
        assertTrue(Files.isRegularFile(Path.of("src/main/resources").resolve(mace.firstPersonModelPath())));
        var rigs = FirstPersonCombatLibrary.load(Path.of("src/main/resources/assets/editor/content/first_person_rig.properties"));
        assertNotNull(rigs.itemProfiles().get(mace.itemId()));
        assertEquals("mace", rigs.weaponDefaults().get(mace.weaponType()));
        var map = MapDesignLibrary.loadGeometryOnly(Path.of("src/main/resources/assets/editor/maps/Bryan_TestMap.properties"));
        assertTrue(map.placements().contains(new MapDesignLibrary.MapPlacement(
                MapDesignLibrary.PlacementKind.ITEM, mace.itemId(), 11, 14)));
        assertFalse(map.tiles()[14][11].blocksMovement());
    }
}

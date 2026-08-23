package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.core.CombatElement;
import org.main.core.WeaponType;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElementalContentRoundTripTest {
    @TempDir Path temporaryDirectory;

    @Test
    void recipePillarAndCreatureFieldsRoundTrip() throws Exception {
        MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
        MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
                4, 4, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        design.customItems().addAll(content.customItems());
        design.craftingRecipes().add(content.craftingRecipes().stream()
                .filter(MapDesignLibrary.CraftingRecipe::usesWeaponTool).findFirst().orElseThrow());
        MapDesignLibrary.CustomFurnitureDefinition pillar = content.customFurniture().stream()
                .filter(value -> value.attunementPillar() != null).findFirst().orElseThrow();
        design.customFurniture().add(pillar);
        MapDesignLibrary.CustomMob source = content.customMobs().getFirst();
        design.customMobs().add(new MapDesignLibrary.CustomMob(
                source.mobId(), source.displayName(), source.imagePath(), source.paperDollSourcePath(),
                source.statValues(), source.xpReward(), source.description(), source.attackSoundPath(),
                source.damageSoundPath(), source.combatAiIntelligence(), source.awarenessRadius(),
                source.movementIntervalMs(), source.respawnDelayMs(), source.skillIds(),
                source.dropEntries(), source.characterModel(), source.butcheryProfile(),
                Map.of(CombatElement.FIRE, 1.5)));

        Path recipePath = temporaryDirectory.resolve("crafting_recipe.properties");
        Path furniturePath = temporaryDirectory.resolve("furniture.properties");
        Path mobPath = temporaryDirectory.resolve("mob.properties");
        MapDesignLibrary.saveContentSegment(design, recipePath);
        MapDesignLibrary.saveContentSegment(design, furniturePath);
        MapDesignLibrary.saveContentSegment(design, mobPath);

        assertEquals(WeaponType.DAGGER, MapDesignLibrary.loadContentSegment(recipePath)
                .craftingRecipes().getFirst().requiredToolWeaponType());
        assertEquals(pillar.attunementPillar(), MapDesignLibrary.loadContentSegment(furniturePath)
                .customFurniture().getFirst().attunementPillar());
        assertEquals(1.5, MapDesignLibrary.loadContentSegment(mobPath).customMobs().getFirst()
                .elementalDamageMultipliers().get(CombatElement.FIRE), 0.0001);
    }
}

package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.battle.BattleActor;
import org.main.core.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WandWeaponOverridesTest {
    @TempDir Path temporaryDirectory;

    @Test
    void wandDefaultsAndOverridesDrivePhysicalStatsAndSpeed() {
        assertEquals(0, WeaponType.WAND.getAccuracyBonus());
        assertEquals(0, WeaponType.WAND.getPowerBonus());
        assertEquals(0.85, WeaponType.WAND.getSpeedMultiplier(), 0.0001);
        InventorySystem.Item wand = wand().withWeaponStatOverrides(
                new WeaponStatOverrides(true, 7, 9, 0.42));
        assertEquals(7, wand.getWeaponAccuracyBonus());
        assertEquals(9, wand.getWeaponPowerBonus());
        assertEquals(0.42, wand.getWeaponSpeedMultiplier(), 0.0001);
        assertEquals(wand.getWeaponStatOverrides(), wand.copy().getWeaponStatOverrides());
        assertEquals(0.10, new WeaponStatOverrides(true, 0, 0, -4).attackIntervalMultiplier(), 0.0001);
        assertEquals(5.00, new WeaponStatOverrides(true, 0, 0, 20).attackIntervalMultiplier(), 0.0001);
    }

    @Test
    void authoredOverridesRoundTripAndMagicBonusesReachBattleActor() throws Exception {
        WeaponStatOverrides overrides = new WeaponStatOverrides(true, 3, 4, 0.70);
        MapDesignLibrary.CustomItem custom = new MapDesignLibrary.CustomItem(
                "test_wand", "Test Wand", InventorySystem.ItemType.WEAPON,
                "", "", "", WeaponType.WAND, false, GearMaterial.NONE,
                0, 10, "A test wand.", null, false, false, 1, 1,
                6, 8, "", EquipmentViewModelProfile.defaults(), "",
                ItemModelIconProfile.defaults(), CharacterSkill.MAGIC_ACCURACY,
                LanternDefinition.none(), overrides, CombatElement.FIRE, 0.10);
        MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
                4, 4, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        design.customItems().add(custom);
        Path output = temporaryDirectory.resolve("item.properties");
        MapDesignLibrary.saveContentSegment(design, output);
        MapDesignLibrary.CustomItem loaded = MapDesignLibrary.loadContentSegment(output).customItems().get(0);
        assertEquals(overrides, loaded.weaponStatOverrides());
        assertEquals(WeaponType.WAND, loaded.weaponType());
        assertFalse(loaded.twoHanded());
        assertEquals(CombatElement.FIRE, loaded.elementalAffinity());
        assertEquals(0.10, loaded.matchingElementSpellDamageBonus(), 0.0001);

        PlayerCharacter player = new PlayerCharacter("Mage", 20, 20);
        player.equipLimb(testArm(LimbSlot.LEFT_ARM));
        player.equipLimb(testArm(LimbSlot.RIGHT_ARM));
        player.getInventory().setEquippedItem(
                InventorySystem.EquipmentSlot.WEAPON, loaded.createItem());
        BattleActor actor = new BattleActor("Mage", 20, 20, null, Library.EntityType.ALLY);
        actor.copyCombatProfileFrom(player);
        assertEquals(player.getSkillLevel(CharacterSkill.MAGIC_ACCURACY) + 6,
                actor.getCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY));
        assertEquals(player.getSkillLevel(CharacterSkill.MAGIC_POWER) + 8,
                actor.getCombatSkillLevel(CharacterSkill.MAGIC_POWER));
        assertEquals(3, actor.getWeaponAccuracyBonus());
        assertEquals(4, actor.getWeaponPowerBonus());
        assertEquals(0.70, actor.getWeaponSpeedMultiplier(), 0.0001);
        assertEquals(CombatElement.FIRE, actor.getWeaponElement());
        assertEquals(1.10, actor.matchingSpellDamageMultiplier(CombatElement.FIRE), 0.0001);
    }

    private static InventorySystem.Item wand() {
        return new InventorySystem.Item(
                "Wand", InventorySystem.ItemType.WEAPON, (java.awt.image.BufferedImage) null,
                "", 0, GearMaterial.NONE, GearDurability.PERFECT, 10, "", null,
                false, 1, "", WeaponType.WAND, false);
    }

    private static LimbItem testArm(LimbSlot slot) {
        return new LimbItem(slot.name(), "Test", slot, java.util.Map.of(), java.util.List.of(),
                GearDurability.PERFECT, "");
    }
}

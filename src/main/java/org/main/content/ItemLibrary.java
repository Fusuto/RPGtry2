package org.main.content;

import org.main.core.InventorySystem;
import org.main.core.GearDurability;
import org.main.core.GearMaterial;

public enum ItemLibrary {
    POTION(
            "Potion",
            InventorySystem.ItemType.CONSUMABLE,
            "assets/images/monster/Nov-2015/item/potion/brilliant_blue.png",
            "assets/sounds/generated/drink_potion.wav",
            5,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            15,
            "A bright blue potion with a clean medicinal smell. Restores 5 HP when used."
    ),

    BONES(
            "Bones",
            InventorySystem.ItemType.MISC,
            "assets/images/monster/Nov-2015/item/amulet/bone_gray.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            3,
            "Dry bones from something that did not stay buried. Mostly useful as proof that trouble was here."
    ),

    SLIME(
            "Slime",
            InventorySystem.ItemType.MISC,
            "assets/images/monster/Nov-2015/item/misc/runes/rune_slime.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            2,
            "A cool wobbling lump of dungeon slime. It twitches faintly when held near magic."
    ),

    RAW_FISH(
            "Raw Fish",
            InventorySystem.ItemType.MISC,
            "assets/images/monster/Ancient/Oct-5-2010/dc-mon/animals/big_fish.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            4,
            "A freshly caught fish from shallow dungeon water. Not very useful until someone cooks it."
    ),

    IRON_SWORD(
            "Iron Sword",
            InventorySystem.ItemType.WEAPON,
            "assets/images/monster/Nov-2015/item/weapon/long_sword1.png",
            null,
            0,
            GearMaterial.IRON,
            GearDurability.GOOD,
            60,
            "A plain iron blade with a steady weight. Requires working arms to wield."
    ),

    LEATHER_CAP(
            "Leather Cap",
            InventorySystem.ItemType.HEAD_GEAR,
            "assets/images/monster/Nov-2015/item/armour/headgear/elven_leather_helm.png",
            null,
            0,
            GearMaterial.LEATHER,
            GearDurability.GOOD,
            40,
            "A soft leather cap. It will not stop a warhammer, but it does make a skeleton feel dignified."
    ),

    SILVER_RING(
            "Silver Ring",
            InventorySystem.ItemType.RING,
            "assets/images/monster/Nov-2015/item/ring/artefact/urand_shadows.png",
            null,
            0,
            GearMaterial.SILVER,
            GearDurability.PERFECT,
            75,
            "A polished silver ring. It hums softly, as if remembering moonlight."
    );

    private final String displayName;
    private final InventorySystem.ItemType itemType;
    private final String iconPath;
    private final String useSoundPath;
    private final int healAmount;
    private final GearMaterial material;
    private final GearDurability durability;
    private final int baseGoldValue;
    private final String examineText;

    ItemLibrary(
            String displayName,
            InventorySystem.ItemType itemType,
            String iconPath,
            String useSoundPath,
            int healAmount,
            GearMaterial material,
            GearDurability durability,
            int baseGoldValue,
            String examineText
    ) {
        this.displayName = displayName;
        this.itemType = itemType;
        this.iconPath = iconPath;
        this.useSoundPath = useSoundPath;
        this.healAmount = Math.max(0, healAmount);
        this.material = material == null ? GearMaterial.NONE : material;
        this.durability = durability == null ? GearDurability.PERFECT : durability;
        this.baseGoldValue = Math.max(1, baseGoldValue);
        this.examineText = examineText == null ? "" : examineText;
    }

    public InventorySystem.Item createItem() {
        return new InventorySystem.Item(displayName, itemType, iconPath, useSoundPath, healAmount, material, durability, baseGoldValue, examineText);
    }

    public String getDisplayName() {
        return displayName;
    }

    public InventorySystem.ItemType getItemType() {
        return itemType;
    }

    public String getUseSoundPath() {
        return useSoundPath;
    }

    public int getHealAmount() {
        return healAmount;
    }

    public GearMaterial getMaterial() {
        return material;
    }

    public GearDurability getDurability() {
        return durability;
    }

    public static ItemLibrary fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        for (ItemLibrary item : values()) {
            if (item.displayName.equalsIgnoreCase(displayName.trim())) {
                return item;
            }
        }

        return null;
    }
}

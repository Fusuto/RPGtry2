package org.main.content;

import org.main.core.InventorySystem;
import org.main.core.GearDurability;
import org.main.core.GearMaterial;
import org.main.engine.AssetLoader;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public enum ItemLibrary {
    GOLD(
            "Gold",
            InventorySystem.ItemType.MISC,
            "assets/images/ui/01_UI_Resources/A1ICON/Gold.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            1,
            "A small pile of spendable gold."
    ),

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

    COOKED_FISH(
            "Cooked Fish",
            InventorySystem.ItemType.CONSUMABLE,
            "assets/images/monster/Ancient/Oct-5-2010/dc-mon/animals/big_fish.png",
            null,
            6,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            8,
            "Warm, flaky dungeon fish. Restores 6 HP when eaten."
    ),

    BURNT_FISH(
            "Burnt Fish",
            InventorySystem.ItemType.MISC,
            "assets/images/monster/Ancient/Oct-5-2010/dc-mon/animals/big_fish.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            1,
            "A sad blackened fish. It smells like ambition meeting reality."
    ),

    COPPER_ORE(
            "Copper Ore",
            InventorySystem.ItemType.MISC,
            "assets/images/generic/64x64/A_Rock1_Node1.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            6,
            "A chunk of copper-bearing ore. Smelt it at a furnace to make a copper bar."
    ),

    COPPER_BAR(
            "Copper Bar",
            InventorySystem.ItemType.MISC,
            "assets/images/resourceMaterial/bronze_bar.png",
            null,
            0,
            GearMaterial.NONE,
            GearDurability.PERFECT,
            12,
            "A simple copper bar. It is soft, workable, and ready for beginner smithing."
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
        if (this == GOLD) {
            return createGold(1);
        }

        return new InventorySystem.Item(displayName, itemType, iconPath, useSoundPath, healAmount, material, durability, baseGoldValue, examineText);
    }

    public static InventorySystem.Item createGold(int amount) {
        return new InventorySystem.Item(
                GOLD.displayName,
                GOLD.itemType,
                AssetLoader.loadImage(GOLD.iconPath),
                GOLD.useSoundPath,
                GOLD.healAmount,
                GOLD.material,
                GOLD.durability,
                GOLD.baseGoldValue,
                GOLD.examineText,
                null,
                true,
                Math.max(1, amount)
        );
    }

    public static InventorySystem.Item createTintedWeapon(
            String displayName,
            String iconPath,
            Color tint,
            GearMaterial material,
            int baseGoldValue,
            String examineText
    ) {
        return new InventorySystem.Item(
                displayName,
                InventorySystem.ItemType.WEAPON,
                tintImage(AssetLoader.loadImage(iconPath), tint),
                null,
                0,
                material,
                GearDurability.PERFECT,
                baseGoldValue,
                examineText
        );
    }
    public static InventorySystem.Item createTintedArmor(
            String displayName,
            String iconPath,
            Color tint,
            GearMaterial material,
            int baseGoldValue,
            String examineText
    ) {
        return new InventorySystem.Item(
                displayName,
                InventorySystem.ItemType.CHEST_ARMOR,
                tintImage(AssetLoader.loadImage(iconPath), tint),
                null,
                0,
                material,
                GearDurability.PERFECT,
                baseGoldValue,
                examineText
        );
    }
    public static InventorySystem.Item createTintedHelmet(
            String displayName,
            String iconPath,
            Color tint,
            GearMaterial material,
            int baseGoldValue,
            String examineText
    ) {
        return new InventorySystem.Item(
                displayName,
                InventorySystem.ItemType.HEAD_GEAR,
                tintImage(AssetLoader.loadImage(iconPath), tint),
                null,
                0,
                material,
                GearDurability.PERFECT,
                baseGoldValue,
                examineText
        );
    }
    public static InventorySystem.Item createTintedLegs(
            String displayName,
            String iconPath,
            Color tint,
            GearMaterial material,
            int baseGoldValue,
            String examineText
    ) {
        return new InventorySystem.Item(
                displayName,
                InventorySystem.ItemType.LEG_ARMOR,
                tintImage(AssetLoader.loadImage(iconPath), tint),
                null,
                0,
                material,
                GearDurability.PERFECT,
                baseGoldValue,
                examineText
        );
    }
    public static InventorySystem.Item createTintedShield(
            String displayName,
            String iconPath,
            Color tint,
            GearMaterial material,
            int baseGoldValue,
            String examineText
    ) {
        return new InventorySystem.Item(
                displayName,
                InventorySystem.ItemType.SHIELD,
                tintImage(AssetLoader.loadImage(iconPath), tint),
                null,
                0,
                material,
                GearDurability.PERFECT,
                baseGoldValue,
                examineText
        );
    }

    private static BufferedImage tintImage(BufferedImage source, Color tint) {
        if (source == null || tint == null) {
            return source;
        }

        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tinted.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.setComposite(AlphaComposite.SrcAtop.derive(0.42f));
        graphics.setColor(tint);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        return tinted;
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

package org.main.content;

import org.main.core.InventorySystem;

public enum ItemLibrary {
    POTION(
            "Potion",
            InventorySystem.ItemType.CONSUMABLE,
            "assets/images/monster/Nov-2015/item/potion/brilliant_blue.png",
            "assets/sounds/generated/drink_potion.wav",
            5
    ),

    IRON_SWORD(
            "Iron Sword",
            InventorySystem.ItemType.WEAPON,
            "assets/images/monster/Nov-2015/item/weapon/long_sword1.png",
            null,
            0
    ),

    LEATHER_CAP(
            "Leather Cap",
            InventorySystem.ItemType.HEAD_GEAR,
            "assets/images/monster/Nov-2015/item/armour/headgear/elven_leather_helm.png",
            null,
            0
    ),

    SILVER_RING(
            "Silver Ring",
            InventorySystem.ItemType.RING,
            "assets/images/monster/Nov-2015/item/ring/artefact/urand_shadows.png",
            null,
            0
    );

    private final String displayName;
    private final InventorySystem.ItemType itemType;
    private final String iconPath;
    private final String useSoundPath;
    private final int healAmount;

    ItemLibrary(
            String displayName,
            InventorySystem.ItemType itemType,
            String iconPath,
            String useSoundPath,
            int healAmount
    ) {
        this.displayName = displayName;
        this.itemType = itemType;
        this.iconPath = iconPath;
        this.useSoundPath = useSoundPath;
        this.healAmount = Math.max(0, healAmount);
    }

    public InventorySystem.Item createItem() {
        return new InventorySystem.Item(displayName, itemType, iconPath, useSoundPath, healAmount);
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
}

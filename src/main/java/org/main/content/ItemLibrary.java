package org.main.content;

import org.main.core.InventorySystem;

public enum ItemLibrary {
    POTION(
            "Potion",
            InventorySystem.ItemType.CONSUMABLE,
            "src/main/java/org/main/images/monster/Nov-2015/item/potion/brilliant_blue.png"
    ),

    IRON_SWORD(
            "Iron Sword",
            InventorySystem.ItemType.WEAPON,
            "src/main/java/org/main/images/monster/Nov-2015/item/weapon/long_sword1.png"
    ),

    LEATHER_CAP(
            "Leather Cap",
            InventorySystem.ItemType.HEAD_GEAR,
            "src/main/java/org/main/images/monster/Nov-2015/item/armour/headgear/elven_leather_helm.png"
    ),

    SILVER_RING(
            "Silver Ring",
            InventorySystem.ItemType.RING,
            "src/main/java/org/main/images/monster/Nov-2015/item/ring/artefact/urand_shadows.png"
    );

    private final String displayName;
    private final InventorySystem.ItemType itemType;
    private final String iconPath;

    ItemLibrary(
            String displayName,
            InventorySystem.ItemType itemType,
            String iconPath
    ) {
        this.displayName = displayName;
        this.itemType = itemType;
        this.iconPath = iconPath;
    }

    public InventorySystem.Item createItem() {
        return new InventorySystem.Item(displayName, itemType, iconPath);
    }

    public String getDisplayName() {
        return displayName;
    }

    public InventorySystem.ItemType getItemType() {
        return itemType;
    }
}

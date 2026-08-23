package org.main.core;

import java.awt.Color;

/**
 * Shared fallback presentation colors for items without authored icons.
 */
public final class ItemPresentationPalette {
    private ItemPresentationPalette() {
    }

    public static Color fallbackColor(InventorySystem.Item item) {
        return fallbackColor(item.getItemType());
    }

    public static Color fallbackColor(InventorySystem.ItemType type) {
        return switch (type) {
            case HEAD_GEAR -> new Color(120, 120, 180);
            case CHEST_ARMOR -> new Color(130, 90, 70);
            case LEG_ARMOR -> new Color(90, 110, 150);
            case RING -> new Color(200, 170, 70);
            case WEAPON -> new Color(170, 170, 180);
            case SHIELD -> new Color(115, 145, 165);
            case UTILITY -> new Color(224, 164, 76);
            case LIMB -> new Color(180, 90, 120);
            case CONSUMABLE -> new Color(120, 180, 120);
            case MISC -> new Color(150, 150, 150);
        };
    }
}

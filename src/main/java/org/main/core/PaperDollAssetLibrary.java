package org.main.core;

public final class PaperDollAssetLibrary {
    public static final String DEFAULT_BASE = "assets/images/monster/Nov-2015/player/base/human_m.png";

    private static final String PLAYER_ROOT = "assets/images/monster/Nov-2015/player/";

    private PaperDollAssetLibrary() {
    }

    public static String sourceForLimb(LimbItem limb) {
        if (limb == null) {
            return DEFAULT_BASE;
        }

        if (limb.getPaperDollSourcePath() != null && !limb.getPaperDollSourcePath().isBlank()) {
            return limb.getPaperDollSourcePath();
        }

        return DEFAULT_BASE;
    }

    public static String overlayForItem(InventorySystem.Item item, InventorySystem.EquipmentSlot slot) {
        if (item == null || slot == null) {
            return "";
        }

        if (item.isEquippable()
                && item.getPaperDollOverlayPath() != null
                && !item.getPaperDollOverlayPath().isBlank()) {
            return item.getPaperDollOverlayPath();
        }

        String name = item.getName() == null ? "" : item.getName().toLowerCase();
        return switch (slot) {
            case HEAD -> firstKnown(name,
                    match(name, "leather", PLAYER_ROOT + "head/cap_brown.png"),
                    match(name, "iron", PLAYER_ROOT + "head/helmet1.png"),
                    match(name, "steel", PLAYER_ROOT + "head/helmet1.png")
            );
            case CHEST -> firstKnown(name,
                    match(name, "leather", PLAYER_ROOT + "body/animal_skin.png"),
                    match(name, "iron", PLAYER_ROOT + "body/bplate_metal1.png"),
                    match(name, "steel", PLAYER_ROOT + "body/plate_metal1.png")
            );
            case LEGS -> firstKnown(name,
                    match(name, "leather", PLAYER_ROOT + "legs/pants_brown.png"),
                    match(name, "iron", PLAYER_ROOT + "legs/leg_armour_metal1.png"),
                    match(name, "steel", PLAYER_ROOT + "legs/leg_armour_metal1.png")
            );
            case WEAPON -> firstKnown(name,
                    match(name, "dagger", PLAYER_ROOT + "hand1/dagger.png"),
                    match(name, "sword", PLAYER_ROOT + "hand1/sword.png")
            );
            case SHIELD -> firstKnown(name, PLAYER_ROOT + "hand2/buckler_green.png");
            case RING_LEFT, RING_RIGHT -> "";
        };
    }

    private static String match(String name, String token, String path) {
        return name.contains(token) ? path : "";
    }

    private static String firstKnown(String name, String... paths) {
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                return path;
            }
        }
        return "";
    }
}

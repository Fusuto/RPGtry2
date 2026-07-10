package org.main.core;

public enum GearMaterial {
    NONE("None", MaterialFamily.NONE, 0, 1.0),
    BRONZE("Bronze", MaterialFamily.METAL, 1, 1.0),
    IRON("Iron", MaterialFamily.METAL, 2, 1.4),
    STEEL("Steel", MaterialFamily.METAL, 3, 1.9),
    OAK("Oak", MaterialFamily.WOOD, 1, 1.0),
    YEW("Yew", MaterialFamily.WOOD, 2, 1.4),
    IRONWOOD("Ironwood", MaterialFamily.WOOD, 3, 1.9),
    LEATHER("Leather", MaterialFamily.HIDE, 1, 1.0),
    SILVER("Silver", MaterialFamily.METAL, 2, 1.6);

    public enum MaterialFamily {
        NONE,
        METAL,
        WOOD,
        HIDE
    }

    private final String displayName;
    private final MaterialFamily family;
    private final int statBonus;
    private final double priceMultiplier;

    GearMaterial(String displayName, MaterialFamily family, int statBonus, double priceMultiplier) {
        this.displayName = displayName;
        this.family = family;
        this.statBonus = Math.max(0, statBonus);
        this.priceMultiplier = Math.max(0.1, priceMultiplier);
    }

    public String getDisplayName() {
        return displayName;
    }

    public MaterialFamily getFamily() {
        return family;
    }

    public int getStatBonus() {
        return statBonus;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }
}

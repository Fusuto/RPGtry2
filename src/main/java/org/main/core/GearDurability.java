package org.main.core;

public enum GearDurability {
    PERFECT("Perfect", 1.0, 1.0),
    GOOD("Good", 0.8, 0.8),
    WORN("Worn", 0.55, 0.55),
    DAMAGED("Damaged", 0.25, 0.3),
    BROKEN("Broken", 0.0, 0.05);

    private final String displayName;
    private final double statMultiplier;
    private final double priceMultiplier;

    GearDurability(String displayName, double statMultiplier, double priceMultiplier) {
        this.displayName = displayName;
        this.statMultiplier = Math.max(0.0, statMultiplier);
        this.priceMultiplier = Math.max(0.0, priceMultiplier);
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getStatMultiplier() {
        return statMultiplier;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }
}

package org.main.core;

public enum WeaponType {
    NONE("None", 0, 0, 1.00),
    DAGGER("Dagger", 0, 0, 0.95),
    SWORD("Sword", 1, 1, 1.00),
    MACE("Mace", 0, 2, 1.08),
    STAFF("Staff", 0, 0, 1.05),
    GREATSWORD("Greatsword", -1, 4, 1.16);

    private final String displayName;
    private final int accuracyBonus;
    private final int powerBonus;
    private final double speedMultiplier;

    WeaponType(String displayName, int accuracyBonus, int powerBonus, double speedMultiplier) {
        this.displayName = displayName;
        this.accuracyBonus = accuracyBonus;
        this.powerBonus = powerBonus;
        this.speedMultiplier = speedMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getAccuracyBonus() {
        return accuracyBonus;
    }

    public int getPowerBonus() {
        return powerBonus;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

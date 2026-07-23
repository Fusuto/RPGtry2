package org.main.core;

/** Authored first-person pose shared by weapons, shields, and chest gauntlets. */
public record EquipmentViewModelProfile(
        double positionX, double positionY, double positionZ,
        double rotationX, double rotationY, double rotationZ,
        double normalizedHeight,
        double swingAxisX, double swingAxisY, double swingAxisZ,
        boolean pairedHands
) {
    public EquipmentViewModelProfile {
        positionX = finite(positionX, 0.38); positionY = finite(positionY, -0.45); positionZ = finite(positionZ, -0.86);
        rotationX = finite(rotationX, -16.0); rotationY = finite(rotationY, 8.0); rotationZ = finite(rotationZ, -28.0);
        normalizedHeight = Math.max(0.02, finite(normalizedHeight, 0.72));
        swingAxisX = finite(swingAxisX, 0.0); swingAxisY = finite(swingAxisY, 0.0); swingAxisZ = finite(swingAxisZ, 1.0);
        if (Math.abs(swingAxisX) + Math.abs(swingAxisY) + Math.abs(swingAxisZ) < 0.0001) swingAxisZ = 1.0;
    }

    public static EquipmentViewModelProfile defaults() {
        return new EquipmentViewModelProfile(0.38, -0.45, -0.86, -16, 8, -28,
                0.72, 0, 0, 1, false);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}

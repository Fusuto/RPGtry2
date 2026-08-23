package org.main.core;

/**
 * Optional per-item replacement for the physical defaults supplied by a weapon type.
 */
public record WeaponStatOverrides(
        boolean enabled,
        int accuracyBonus,
        int powerBonus,
        double attackIntervalMultiplier
) {
    public static final double MIN_INTERVAL_MULTIPLIER = 0.10;
    public static final double MAX_INTERVAL_MULTIPLIER = 5.00;

    public WeaponStatOverrides {
        accuracyBonus = Math.max(0, accuracyBonus);
        powerBonus = Math.max(0, powerBonus);
        attackIntervalMultiplier = Double.isFinite(attackIntervalMultiplier)
                ? Math.max(MIN_INTERVAL_MULTIPLIER,
                Math.min(MAX_INTERVAL_MULTIPLIER, attackIntervalMultiplier))
                : 1.0;
    }

    public static WeaponStatOverrides inherited() {
        return new WeaponStatOverrides(false, 0, 0, 1.0);
    }
}

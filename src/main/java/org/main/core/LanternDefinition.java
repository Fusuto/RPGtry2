package org.main.core;

/** Authored passive light behavior for a fuelled Pocket utility item. */
public record LanternDefinition(
        boolean enabled,
        int colorRgb,
        double radius,
        double intensity,
        double flickerAmount
) {
    public static final int DEFAULT_COLOR = 0xFFB45A;

    public LanternDefinition {
        colorRgb &= 0xFFFFFF;
        radius = enabled ? clamp(radius, 0.1, 64.0) : 0.0;
        intensity = enabled ? clamp(intensity, 0.0, 8.0) : 0.0;
        flickerAmount = enabled ? clamp(flickerAmount, 0.0, 1.0) : 0.0;
    }

    public static LanternDefinition none() {
        return new LanternDefinition(false, DEFAULT_COLOR, 0.0, 0.0, 0.0);
    }

    public static LanternDefinition pocketLanternDefaults() {
        return new LanternDefinition(true, DEFAULT_COLOR, 5.0, 1.0, 0.12);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

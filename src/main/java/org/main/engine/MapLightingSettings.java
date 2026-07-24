package org.main.engine;

import java.util.Locale;

public record MapLightingSettings(
        boolean lightingEnabled,
        int ambientColorRgb,
        double ambientIntensity,
        boolean fogEnabled,
        int fogColorRgb,
        double fogDensity
) {
    private static final int DEFAULT_AMBIENT_COLOR = 0x3D1826;
    private static final int DEFAULT_FOG_COLOR = 0x140912;

    public MapLightingSettings {
        ambientColorRgb &= 0xFFFFFF;
        fogColorRgb &= 0xFFFFFF;
        ambientIntensity = clamp(ambientIntensity, 0.0, 2.0);
        fogDensity = clamp(fogDensity, 0.0, 1.0);
    }

    public static MapLightingSettings defaultSettings() {
        return new MapLightingSettings(true, DEFAULT_AMBIENT_COLOR, 0.36, true, DEFAULT_FOG_COLOR, 0.025);
    }

    public static int parseColor(String value, int fallbackRgb) {
        if (value == null || value.isBlank()) {
            return fallbackRgb & 0xFFFFFF;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            return fallbackRgb & 0xFFFFFF;
        }
    }

    public static String colorHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public float red() {
        return ((ambientColorRgb >> 16) & 0xFF) / 255.0f;
    }

    public float green() {
        return ((ambientColorRgb >> 8) & 0xFF) / 255.0f;
    }

    public float blue() {
        return (ambientColorRgb & 0xFF) / 255.0f;
    }

    public float fogRed() {
        return ((fogColorRgb >> 16) & 0xFF) / 255.0f;
    }

    public float fogGreen() {
        return ((fogColorRgb >> 8) & 0xFF) / 255.0f;
    }

    public float fogBlue() {
        return (fogColorRgb & 0xFF) / 255.0f;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}

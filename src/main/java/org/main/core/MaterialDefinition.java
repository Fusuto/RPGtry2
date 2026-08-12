package org.main.core;

import java.awt.Color;
import java.util.Locale;

/** Immutable authored description of an equipment/crafting material tier. */
public record MaterialDefinition(
        String id,
        String displayName,
        GearMaterial.MaterialFamily family,
        int sortOrder,
        int statBonus,
        double priceMultiplier,
        int tintRgb,
        float tintStrength,
        String rawResourceItemId,
        String processedResourceItemId,
        int lanternFuelCapacitySeconds,
        int lanternBurnSecondsPerLog
) {
    public MaterialDefinition {
        id = normalizeId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        family = family == null ? GearMaterial.MaterialFamily.NONE : family;
        statBonus = Math.max(0, statBonus);
        priceMultiplier = Math.max(0.1, priceMultiplier);
        tintRgb &= 0xFFFFFF;
        tintStrength = Math.max(0.0f, Math.min(1.0f, tintStrength));
        rawResourceItemId = normalizeReference(rawResourceItemId);
        processedResourceItemId = normalizeReference(processedResourceItemId);
        lanternFuelCapacitySeconds = family == GearMaterial.MaterialFamily.METAL
                ? Math.max(0, lanternFuelCapacitySeconds) : 0;
        lanternBurnSecondsPerLog = family == GearMaterial.MaterialFamily.WOOD
                ? Math.max(0, lanternBurnSecondsPerLog) : 0;
    }

    /** Schema-1/source compatibility. */
    public MaterialDefinition(
            String id, String displayName, GearMaterial.MaterialFamily family, int sortOrder,
            int statBonus, double priceMultiplier, int tintRgb, float tintStrength,
            String rawResourceItemId, String processedResourceItemId
    ) {
        this(id, displayName, family, sortOrder, statBonus, priceMultiplier, tintRgb, tintStrength,
                rawResourceItemId, processedResourceItemId,
                family == GearMaterial.MaterialFamily.METAL ? 600 : 0,
                family == GearMaterial.MaterialFamily.WOOD ? 300 : 0);
    }

    public Color tintColor() {
        return family == GearMaterial.MaterialFamily.NONE ? null : new Color(tintRgb);
    }

    public String tintHex() {
        return String.format(Locale.ROOT, "#%06X", tintRgb);
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String normalizeReference(String value) {
        return value == null ? "" : value.trim();
    }
}

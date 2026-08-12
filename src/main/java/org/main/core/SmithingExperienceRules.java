package org.main.core;

import java.util.List;

/** Material-wide experience rules for finished items made at an anvil. */
public final class SmithingExperienceRules {
    private static final int FALLBACK_XP_PER_BAR = 12;

    private SmithingExperienceRules() {
    }

    public static List<GearMaterial> smithingMaterials() {
        return java.util.Arrays.stream(GearMaterial.values())
                .filter(material -> material.getFamily() == GearMaterial.MaterialFamily.METAL)
                .toList();
    }

    public static String configurationKey(GearMaterial material) {
        GearMaterial safeMaterial = material == null ? GearMaterial.NONE : material;
        return "smithing.xpPerBar." + safeMaterial.id();
    }

    public static int xpPerBar(GearMaterial material) {
        if (material == null || material.getFamily() != GearMaterial.MaterialFamily.METAL) {
            return 0;
        }
        return Math.max(0, GameConfiguration.intValue(
                configurationKey(material),
                defaultXpPerBar(material)));
    }

    public static int calculate(GearMaterial material, int requiredBars) {
        long calculated = (long) xpPerBar(material) * Math.max(1, requiredBars);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, calculated));
    }

    public static int defaultXpPerBar(GearMaterial material) {
        if (material == null || material.getFamily() != GearMaterial.MaterialFamily.METAL) {
            return 0;
        }
        return FALLBACK_XP_PER_BAR;
    }
}

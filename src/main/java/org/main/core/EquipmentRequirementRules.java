package org.main.core;

import java.util.Locale;

/** Live skill/material equipment requirements shared by runtime and tools. */
public final class EquipmentRequirementRules {
    private EquipmentRequirementRules() {
    }

    public static String configurationKey(CharacterSkill skill, GearMaterial material) {
        CharacterSkill safeSkill = skill == null ? CharacterSkill.DEFENSE : skill;
        GearMaterial safeMaterial = material == null ? GearMaterial.NONE : material;
        return "levelGate.equipment."
                + safeSkill.name().toLowerCase(Locale.ROOT)
                + "."
                + safeMaterial.id();
    }

    public static int requiredLevel(CharacterSkill skill, GearMaterial material) {
        if (skill == null) {
            return 0;
        }
        return Math.max(1, GameConfiguration.intValue(configurationKey(skill, material), defaultLevel(material)));
    }

    public static int defaultLevel(GearMaterial material) {
        return 1;
    }
}

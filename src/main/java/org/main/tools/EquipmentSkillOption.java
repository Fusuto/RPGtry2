package org.main.tools;

import org.main.core.CharacterSkill;

record EquipmentSkillOption(CharacterSkill skill) {
    @Override
    public String toString() {
        return skill == null ? "None / Unrestricted" : skill.getDisplayName();
    }
}


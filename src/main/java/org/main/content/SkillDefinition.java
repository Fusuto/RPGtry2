package org.main.content;

import org.main.core.Library;

import java.util.List;

public record SkillDefinition(
        String id,
        String displayName,
        String description,
        Library.SkillTargetShape targetShape,
        Library.EntityType targetTeam,
        Library.BattleTargetingMode targetingMode,
        String useSoundPath,
        String presentationStyle,
        double cooldownSeconds,
        boolean consumesAutoAction,
        List<SkillEffectDefinition> effects
) {
    public SkillDefinition {
        id = BattleContentCatalog.normalizeId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        description = description == null ? "" : description;
        targetShape = targetShape == null ? Library.SkillTargetShape.SINGLE_TARGET : targetShape;
        targetTeam = targetTeam == null ? Library.EntityType.ENEMY : targetTeam;
        targetingMode = targetingMode == null ? Library.BattleTargetingMode.MAGIC : targetingMode;
        useSoundPath = useSoundPath == null ? "" : useSoundPath.trim().replace('\\', '/');
        presentationStyle = presentationStyle == null ? "AUTO" : presentationStyle.trim().toUpperCase();
        cooldownSeconds = Math.max(0.0, cooldownSeconds);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}

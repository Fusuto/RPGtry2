package org.main.content;

import org.main.battle.BattleSkill;
import org.main.core.Library;

public enum EnemySkillLibrary {
    ABSORB(
            "Absorb",
            "Deals small damage and heals the user.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            null,
            2,
            Library.EffectType.DAMAGE,
            0.0,
            0,
            0,
            0.0,
            0.5
    );

    private final String displayName;
    private final String description;
    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final String useSoundPath;
    private final int damage;
    private final Library.EffectType effectType;
    private final double stunChance;
    private final int stunTurns;
    private final int defendTurns;
    private final double damageReduction;
    private final double selfHealPercent;

    EnemySkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            int damage,
            Library.EffectType effectType,
            double stunChance,
            int stunTurns,
            int defendTurns,
            double damageReduction,
            double selfHealPercent
    ) {
        this.displayName = displayName;
        this.description = description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.useSoundPath = useSoundPath;
        this.damage = damage;
        this.effectType = effectType;
        this.stunChance = stunChance;
        this.stunTurns = stunTurns;
        this.defendTurns = defendTurns;
        this.damageReduction = damageReduction;
        this.selfHealPercent = selfHealPercent;
    }

    public BattleSkill createSkill() {
        return new BattleSkill(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                damage,
                stunChance,
                stunTurns,
                defendTurns,
                damageReduction,
                selfHealPercent
        );
    }
}

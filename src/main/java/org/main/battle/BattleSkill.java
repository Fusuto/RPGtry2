package org.main.battle;

import org.main.core.Library;

public class BattleSkill {
    private final String name;
    private final String description;

    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final Library.EffectType effectType;
    private final String useSoundPath;
    private final int damage;
    private final double stunChance;
    private final int stunTurns;
    private final int defendTurns;
    private final double damageReduction;
    private final double selfHealPercent;

    public BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            Library.EffectType effectType, int damage
    ) {
        this(name, description, targetShape, targetTeam, targetingMode, null, effectType, damage);
    }

    public BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int damage
    ) {
        this(name, description, targetShape, targetTeam, targetingMode, useSoundPath, effectType, damage, 0.0, 0, 0, 0.0, 0.0);
    }

    public BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int damage,
            double stunChance,
            int stunTurns,
            int defendTurns,
            double damageReduction,
            double selfHealPercent
    ) {
        this.name = name;
        this.description = description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.effectType = effectType;
        this.useSoundPath = useSoundPath;
        this.damage = damage;
        this.stunChance = Math.max(0.0, Math.min(1.0, stunChance));
        this.stunTurns = Math.max(0, stunTurns);
        this.defendTurns = Math.max(0, defendTurns);
        this.damageReduction = Math.max(0.0, Math.min(0.95, damageReduction));
        this.selfHealPercent = Math.max(0.0, selfHealPercent);
    }

    public BattleSkill(
            String name,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode, Library.EffectType effectType, int damage
    ) {
        this(name, "", targetShape, targetTeam, targetingMode, effectType, damage);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Library.SkillTargetShape getTargetShape() {
        return targetShape;
    }

    public Library.EntityType getTargetTeam() {
        return targetTeam;
    }

    public Library.BattleTargetingMode getTargetingMode() {
        return targetingMode;
    }

    public String getUseSoundPath() {
        return useSoundPath;
    }

    public Library.EffectType getEffectType() {
        return effectType;
    }

    public int getDamage() {
        return damage;
    }

    public double getStunChance() {
        return stunChance;
    }

    public int getStunTurns() {
        return stunTurns;
    }

    public int getDefendTurns() {
        return defendTurns;
    }

    public double getDamageReduction() {
        return damageReduction;
    }

    public double getSelfHealPercent() {
        return selfHealPercent;
    }

    public boolean healsCasterFromDamage() {
        return selfHealPercent > 0.0;
    }
}

final class BattleTargetResolver {
    private BattleTargetResolver() {
    }

    static boolean matchesSkillShape(
            BattleActor actor,
            BattleActor anchor,
            Library.SkillTargetShape shape
    ) {
        return switch (shape) {
            case ENTIRE_SIDE -> true;
            case SINGLE_TARGET -> actor == anchor;
            case SINGLE_COLUMN -> actor.getRow() == anchor.getRow();
            case SINGLE_ROW -> actor.getSlot() == anchor.getSlot();
        };
    }
}

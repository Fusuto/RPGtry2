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
    private final SummonMode summonMode;
    private final double summonChance;
    private final String summonSpeciesId;
    private final String summonDisplayName;
    private final String skillId;
    private final double baseCooldownSeconds;
    private final boolean consumesAutoAction;

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
        this(name, description, targetShape, targetTeam, targetingMode, useSoundPath, effectType, damage,
                stunChance, stunTurns, defendTurns, damageReduction, selfHealPercent, SummonMode.NONE, 0.0);
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
            double selfHealPercent,
            SummonMode summonMode,
            double summonChance
    ) {
        this(name, description, targetShape, targetTeam, targetingMode, useSoundPath, effectType, damage,
                stunChance, stunTurns, defendTurns, damageReduction, selfHealPercent, summonMode, summonChance, "", "");
    }

    private BattleSkill(
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
            double selfHealPercent,
            SummonMode summonMode,
            double summonChance,
            String summonSpeciesId,
            String summonDisplayName
    ) {
        this(
                name,
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
                selfHealPercent,
                summonMode,
                summonChance,
                summonSpeciesId,
                summonDisplayName,
                "",
                0.0,
                true
        );
    }

    private BattleSkill(
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
            double selfHealPercent,
            SummonMode summonMode,
            double summonChance,
            String summonSpeciesId,
            String summonDisplayName,
            String skillId,
            double baseCooldownSeconds,
            boolean consumesAutoAction
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
        this.summonMode = summonMode == null ? SummonMode.NONE : summonMode;
        this.summonChance = Math.max(0.0, Math.min(1.0, summonChance));
        this.summonSpeciesId = summonSpeciesId == null ? "" : summonSpeciesId;
        this.summonDisplayName = summonDisplayName == null ? "" : summonDisplayName;
        this.skillId = skillId == null || skillId.isBlank() ? fallbackSkillId(name) : skillId;
        this.baseCooldownSeconds = Math.max(0.0, baseCooldownSeconds);
        this.consumesAutoAction = consumesAutoAction;
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

    public SummonMode getSummonMode() {
        return summonMode;
    }

    public double getSummonChance() {
        return summonChance;
    }

    public String getSummonSpeciesId() {
        return summonSpeciesId;
    }

    public String getSummonDisplayName() {
        return summonDisplayName;
    }

    public boolean isSummonSkill() {
        return effectType == Library.EffectType.SUMMON && summonMode != SummonMode.NONE;
    }

    public String getSkillId() {
        return skillId;
    }

    public double getBaseCooldownSeconds() {
        return baseCooldownSeconds;
    }

    public boolean consumesAutoAction() {
        return consumesAutoAction;
    }

    public BattleSkill withSummonSource(String speciesId, String displayName) {
        return new BattleSkill(
                name,
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
                selfHealPercent,
                summonMode,
                summonChance,
                speciesId,
                displayName,
                skillId,
                baseCooldownSeconds,
                consumesAutoAction
        );
    }

    public BattleSkill withCooldown(String skillId, double baseCooldownSeconds, boolean consumesAutoAction) {
        return new BattleSkill(
                name,
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
                selfHealPercent,
                summonMode,
                summonChance,
                summonSpeciesId,
                summonDisplayName,
                skillId,
                baseCooldownSeconds,
                consumesAutoAction
        );
    }

    private static String fallbackSkillId(String name) {
        return name == null || name.isBlank()
                ? "skill"
                : name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    public enum SummonMode {
        NONE,
        SAME_SPECIES,
        SKELETON
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

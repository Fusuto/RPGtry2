package org.main.battle;

import org.main.core.Library;

public class BattleSkill {
    private static final int DEFEND_DEFAULT_TURNS = 2;
    private static final double DAMAGE_HEAL_PERCENT = 0.50;

    private final String name;
    private final String description;
    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final String useSoundPath;
    private final Library.EffectType effectType;
    private final int potency;
    private final BattleStatusType onHitStatusType;
    private final int onHitStatusTurns;
    private final SummonMode summonMode;
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
            String useSoundPath,
            Library.EffectType effectType,
            int potency
    ) {
        this(
                name,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                null,
                0
        );
    }

    public BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int potency,
            BattleStatusType onHitStatusType,
            int onHitStatusTurns
    ) {
        this(
                name,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                onHitStatusType,
                onHitStatusTurns,
                SummonMode.NONE,
                "",
                "",
                "",
                0.0,
                true
        );
    }

    public BattleSkill(
            String name,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            Library.EffectType effectType,
            int potency
    ) {
        this(name, "", targetShape, targetTeam, targetingMode, null, effectType, potency);
    }

    private BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int potency,
            BattleStatusType onHitStatusType,
            int onHitStatusTurns,
            SummonMode summonMode,
            String summonSpeciesId,
            String summonDisplayName,
            String skillId,
            double baseCooldownSeconds,
            boolean consumesAutoAction
    ) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.useSoundPath = useSoundPath;
        this.effectType = effectType;
        this.potency = Math.max(0, potency);
        this.onHitStatusType = onHitStatusType;
        this.onHitStatusTurns = Math.max(0, onHitStatusTurns);
        this.summonMode = summonMode == null ? SummonMode.NONE : summonMode;
        this.summonSpeciesId = summonSpeciesId == null ? "" : summonSpeciesId;
        this.summonDisplayName = summonDisplayName == null ? "" : summonDisplayName;
        this.skillId = skillId == null || skillId.isBlank() ? fallbackSkillId(name) : skillId;
        this.baseCooldownSeconds = Math.max(0.0, baseCooldownSeconds);
        this.consumesAutoAction = consumesAutoAction;
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

    public int getPotency() {
        return potency;
    }

    public int getDamage() {
        return potency;
    }

    public double getStunChance() {
        return onHitStatusType == BattleStatusType.STUN ? onHitStatusType.getDefaultApplyChance() : 0.0;
    }

    public int getStunTurns() {
        return onHitStatusType == BattleStatusType.STUN ? onHitStatusTurns : 0;
    }

    public int getDefendTurns() {
        return effectType == Library.EffectType.DEFEND && potency > 0 ? DEFEND_DEFAULT_TURNS : 0;
    }

    public double getDamageReduction() {
        return effectType == Library.EffectType.DEFEND ? Math.max(0.0, Math.min(0.95, potency / 100.0)) : 0.0;
    }

    public double getSelfHealPercent() {
        return healsCasterFromDamage() ? DAMAGE_HEAL_PERCENT : 0.0;
    }

    public boolean healsCasterFromDamage() {
        return effectType == Library.EffectType.DAMAGE_HEAL;
    }

    public BattleStatusType getOnHitStatusType() {
        return onHitStatusType;
    }

    public double getOnHitStatusChance() {
        return onHitStatusType == null ? 0.0 : onHitStatusType.getDefaultApplyChance();
    }

    public int getOnHitStatusTurns() {
        return onHitStatusTurns;
    }

    public boolean hasOnHitStatus() {
        return onHitStatusType != null && onHitStatusTurns > 0 && getOnHitStatusChance() > 0.0;
    }

    public SummonMode getSummonMode() {
        return summonMode;
    }

    public double getSummonChance() {
        return effectType == Library.EffectType.SUMMON ? Math.max(0.0, Math.min(1.0, potency / 100.0)) : 0.0;
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

    public BattleSkill withSummonMode(SummonMode summonMode) {
        return new BattleSkill(
                name,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                onHitStatusType,
                onHitStatusTurns,
                summonMode,
                summonSpeciesId,
                summonDisplayName,
                skillId,
                baseCooldownSeconds,
                consumesAutoAction
        );
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
                potency,
                onHitStatusType,
                onHitStatusTurns,
                summonMode,
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
                potency,
                onHitStatusType,
                onHitStatusTurns,
                summonMode,
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

package org.main.content;

import org.main.battle.BattleSkill;
import org.main.battle.BattleStatusType;
import org.main.core.GameConfiguration;
import org.main.core.Library;

import java.util.List;

public enum SkillLibrary {
    WAIT(
            "Skip Turn",
            "Debug: ends your turn without doing anything.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.DEFEND,
            0
    ),

    DEBUG_DROP_HP(
            "Drop HP to 10%",
            "Debug: drops the player to 10% HP to test the warning loop.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.DEFEND,
            0
    ),

    FIREBALL(
            "Fireball",
            "Hits every enemy.",
            Library.SkillTargetShape.ENTIRE_SIDE,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            "assets/sounds/generated/thrown_fireball.wav",
            Library.EffectType.DAMAGE,
            5
    ),

    PIERCING_LINE(
            "Piercing Line",
            "Hits one horizontal lane.",
            Library.SkillTargetShape.SINGLE_ROW,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.RANGED,
            null,
            Library.EffectType.DAMAGE,
            5
    ),

    CRUSH_COLUMN(
            "Crush Column",
            "Hits either the front or back column.",
            Library.SkillTargetShape.SINGLE_COLUMN,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.DAMAGE,
            5
    ),

    HEAL(
            "Heal",
            "Targets one ally.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            "assets/sounds/generated/fire_wave.wav",
            Library.EffectType.HEAL,
            5
    ),

    DEFEND(
            "Defend",
            "Reduces incoming damage for 2 turns.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.DEFEND,
            50
    ),

    BASH(
            "Bash",
            "Deals damage and has a chance to stun.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.NORMAL_MELEE,
            null,
            Library.EffectType.DAMAGE,
            4,
            BattleStatusType.STUN,
            1
    ),

    ABSORB(
            "Absorb",
            "Deals small damage and heals the user.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.DAMAGE_HEAL,
            2
    ),

    ROTTING_GRASP(
            "Rotting Grasp",
            "Deals damage and slows the target with rotting flesh.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.NORMAL_MELEE,
            null,
            Library.EffectType.DAMAGE,
            3,
            BattleStatusType.ROTTING_GRASP,
            2
    ),

    WAR_CRY(
            "War Cry",
            "Attempts to call another creature of the same kind.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.SUMMON,
            65,
            BattleSkill.SummonMode.SAME_SPECIES
    ),

    RAISE_SKELETON(
            "Raise Skeleton",
            "Attempts to call a skeleton into the battle.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            Library.EffectType.SUMMON,
            75,
            BattleSkill.SummonMode.SKELETON
    );

    private static final List<SkillLibrary> DEFAULT_PLAYER_SKILLS = List.of(
            WAIT,
            FIREBALL,
            PIERCING_LINE,
            CRUSH_COLUMN,
            HEAL
    );

    private static final List<SkillLibrary> UNIVERSAL_PLAYER_SKILLS = List.of(
    );

    private static final List<SkillLibrary> DEBUG_PLAYER_SKILLS = List.of(
            DEBUG_DROP_HP,
            WAIT
    );

    private final String displayName;
    private final String description;
    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final String useSoundPath;
    private final Library.EffectType effectType;
    private final int potency;
    private final BattleStatusType onHitStatusType;
    private final int onHitStatusTurns;
    private final BattleSkill.SummonMode summonMode;

    SkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int potency
    ) {
        this(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                null,
                0,
                BattleSkill.SummonMode.NONE
        );
    }

    SkillLibrary(
            String displayName,
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
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                onHitStatusType,
                onHitStatusTurns,
                BattleSkill.SummonMode.NONE
        );
    }

    SkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int potency,
            BattleSkill.SummonMode summonMode
    ) {
        this(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                null,
                0,
                summonMode
        );
    }

    SkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            Library.EffectType effectType,
            int potency,
            BattleStatusType onHitStatusType,
            int onHitStatusTurns,
            BattleSkill.SummonMode summonMode
    ) {
        this.displayName = displayName;
        this.description = description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.useSoundPath = useSoundPath;
        this.effectType = effectType;
        this.potency = Math.max(0, potency);
        this.onHitStatusType = onHitStatusType;
        this.onHitStatusTurns = Math.max(0, onHitStatusTurns);
        this.summonMode = summonMode == null ? BattleSkill.SummonMode.NONE : summonMode;
    }

    public BattleSkill createSkill() {
        BattleSkill skill = onHitStatusType == null
                ? new BattleSkill(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency
        )
                : new BattleSkill(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                effectType,
                potency,
                onHitStatusType,
                onHitStatusTurns
        );

        if (summonMode != BattleSkill.SummonMode.NONE) {
            skill = skill.withSummonMode(summonMode);
        }

        return skill.withCooldown(name(), configuredCooldownSeconds(), true);
    }

    private double configuredCooldownSeconds() {
        return Math.max(0.0, GameConfiguration.doubleValue(
                "battle.skillCooldown." + name() + ".seconds",
                defaultCooldownSeconds()
        ));
    }

    private double defaultCooldownSeconds() {
        return switch (this) {
            case WAIT, DEBUG_DROP_HP -> 0.0;
            case BASH, DEFEND -> 6.0;
            case PIERCING_LINE, CRUSH_COLUMN -> 7.0;
            case FIREBALL, ABSORB, ROTTING_GRASP -> 8.0;
            case HEAL -> 10.0;
            case WAR_CRY, RAISE_SKELETON -> 20.0;
        };
    }

    public static List<BattleSkill> createDefaultPlayerSkills() {
        return DEFAULT_PLAYER_SKILLS.stream()
                .map(SkillLibrary::createSkill)
                .toList();
    }

    public static List<BattleSkill> createUniversalPlayerSkills() {
        return UNIVERSAL_PLAYER_SKILLS.stream()
                .map(SkillLibrary::createSkill)
                .toList();
    }

    public static List<BattleSkill> createDebugPlayerSkills() {
        return DEBUG_PLAYER_SKILLS.stream()
                .map(SkillLibrary::createSkill)
                .toList();
    }

    public static boolean isDebugDropHpSkill(BattleSkill skill) {
        return skill != null && DEBUG_DROP_HP.displayName.equals(skill.getName());
    }

    public String getDisplayName() {
        return displayName;
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

    public int getPotency() {
        return potency;
    }

    public int getDamage() {
        return potency;
    }

    public Library.EffectType getEffectType() {
        return effectType;
    }

    public double getStunChance() {
        return onHitStatusType == BattleStatusType.STUN ? onHitStatusType.getDefaultApplyChance() : 0.0;
    }

    public int getStunTurns() {
        return onHitStatusType == BattleStatusType.STUN ? onHitStatusTurns : 0;
    }

    public int getDefendTurns() {
        return effectType == Library.EffectType.DEFEND && potency > 0 ? 2 : 0;
    }

    public double getDamageReduction() {
        return effectType == Library.EffectType.DEFEND ? Math.max(0.0, Math.min(0.95, potency / 100.0)) : 0.0;
    }

    public double getSelfHealPercent() {
        return effectType == Library.EffectType.DAMAGE_HEAL ? 0.50 : 0.0;
    }

    public BattleSkill.SummonMode getSummonMode() {
        return summonMode;
    }

    public double getSummonChance() {
        return effectType == Library.EffectType.SUMMON ? Math.max(0.0, Math.min(1.0, potency / 100.0)) : 0.0;
    }
}

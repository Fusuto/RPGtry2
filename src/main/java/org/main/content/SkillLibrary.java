package org.main.content;

import org.main.battle.BattleSkill;
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
            0,
            Library.EffectType.DEFEND
    ),

    DEBUG_DROP_HP(
            "Drop HP to 10%",
            "Debug: drops the player to 10% HP to test the warning loop.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            0,
            Library.EffectType.DEFEND
    ),

    FIREBALL(
            "Fireball",
            "Hits every enemy.",
            Library.SkillTargetShape.ENTIRE_SIDE,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            "assets/sounds/generated/thrown_fireball.wav",
            5,
            Library.EffectType.DAMAGE
    ),

    PIERCING_LINE(
            "Piercing Line",
            "Hits one horizontal lane.",
            Library.SkillTargetShape.SINGLE_ROW,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.RANGED,
            null,
            5,
            Library.EffectType.DAMAGE
    ),

    CRUSH_COLUMN(
            "Crush Column",
            "Hits either the front or back column.",
            Library.SkillTargetShape.SINGLE_COLUMN,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            null,
            5,
            Library.EffectType.DAMAGE
    ),

    HEAL(
            "Heal",
            "Targets one ally.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            "assets/sounds/generated/fire_wave.wav",
            5,
            Library.EffectType.HEAL
    ),

    DEFEND(
            "Defend",
            "Reduces incoming damage for 2 turns.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ALLY,
            Library.BattleTargetingMode.MAGIC,
            null,
            0,
            Library.EffectType.DEFEND,
            0.0,
            0,
            2,
            0.5,
            0.0
    ),

    BASH(
            "Bash",
            "Deals damage and has a chance to stun.",
            Library.SkillTargetShape.SINGLE_TARGET,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.NORMAL_MELEE,
            null,
            4,
            Library.EffectType.DAMAGE,
            0.45,
            1,
            0,
            0.0,
            0.0
    ),

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
    private final int damage;
    private final Library.EffectType effectType;
    private final double stunChance;
    private final int stunTurns;
    private final int defendTurns;
    private final double damageReduction;
    private final double selfHealPercent;

    SkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath, int damage, Library.EffectType effectType
    ) {
        this(
                displayName,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                damage,
                effectType,
                0.0,
                0,
                0,
                0.0,
                0.0
        );
    }

    SkillLibrary(
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

    public int getDamage() {
        return damage;
    }

    public Library.EffectType getEffectType() {
        return effectType;
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
}

package org.main.content;

import org.main.battle.BattleSkill;
import org.main.core.Library;

import java.util.List;

public enum SkillLibrary {
    FIREBALL(
            "Fireball",
            "Hits every enemy.",
            Library.SkillTargetShape.ENTIRE_SIDE,
            Library.EntityType.ENEMY,
            Library.BattleTargetingMode.MAGIC,
            "src/main/java/org/main/sounds/generated/thrown_fireball.wav",
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
            "src/main/java/org/main/sounds/generated/fire_wave.wav",
            5,
            Library.EffectType.HEAL
    );

    private static final List<SkillLibrary> DEFAULT_PLAYER_SKILLS = List.of(
            FIREBALL,
            PIERCING_LINE,
            CRUSH_COLUMN,
            HEAL
    );

    private final String displayName;
    private final String description;
    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final String useSoundPath;
    private final int damage;
    private final Library.EffectType effectType;

    SkillLibrary(
            String displayName,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath, int damage, Library.EffectType effectType
    ) {
        this.displayName = displayName;
        this.description = description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.useSoundPath = useSoundPath;
        this.damage = damage;
        this.effectType = effectType;
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
                damage
        );
    }

    public static List<BattleSkill> createDefaultPlayerSkills() {
        return DEFAULT_PLAYER_SKILLS.stream()
                .map(SkillLibrary::createSkill)
                .toList();
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
}

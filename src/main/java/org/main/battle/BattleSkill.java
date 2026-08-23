package org.main.battle;

import org.main.content.SkillDefinition;
import org.main.content.SkillEffectDefinition;
import org.main.core.Library;
import org.main.core.CombatElement;

import java.util.List;
import java.util.Map;

public final class BattleSkill {
    private final String name;
    private final String description;
    private final Library.SkillTargetShape targetShape;
    private final Library.EntityType targetTeam;
    private final Library.BattleTargetingMode targetingMode;
    private final String useSoundPath;
    private final String skillId;
    private final double baseCooldownSeconds;
    private final boolean consumesAutoAction;
    private final List<SkillEffectDefinition> effects;
    private final String presentationStyle;
    private final String summonSpeciesOverride;
    private final String summonDisplayName;
    private final CombatElement element;

    private BattleSkill(
            String name,
            String description,
            Library.SkillTargetShape targetShape,
            Library.EntityType targetTeam,
            Library.BattleTargetingMode targetingMode,
            String useSoundPath,
            String skillId,
            double baseCooldownSeconds,
            boolean consumesAutoAction,
            List<SkillEffectDefinition> effects,
            String presentationStyle,
            String summonSpeciesOverride,
            String summonDisplayName,
            CombatElement element
    ) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.targetShape = targetShape;
        this.targetTeam = targetTeam;
        this.targetingMode = targetingMode;
        this.useSoundPath = useSoundPath == null ? "" : useSoundPath;
        this.skillId = skillId == null ? "" : skillId;
        this.baseCooldownSeconds = Math.max(0.0, baseCooldownSeconds);
        this.consumesAutoAction = consumesAutoAction;
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.presentationStyle = presentationStyle == null ? "AUTO" : presentationStyle;
        this.summonSpeciesOverride = summonSpeciesOverride == null ? "" : summonSpeciesOverride;
        this.summonDisplayName = summonDisplayName == null ? "" : summonDisplayName;
        this.element = element == null ? CombatElement.NEUTRAL : element;
    }

    public static BattleSkill fromDefinition(SkillDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Skill definition is required.");
        }
        return new BattleSkill(
                definition.displayName(),
                definition.description(),
                definition.targetShape(),
                definition.targetTeam(),
                definition.targetingMode(),
                definition.useSoundPath(),
                definition.id(),
                definition.cooldownSeconds(),
                definition.consumesAutoAction(),
                definition.effects(),
                definition.presentationStyle(),
                "",
                "",
                definition.element()
        );
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

    public int getPrimaryPotency() {
        return effects.stream()
                .filter(effect -> "damage".equals(effect.kindId()) || "heal".equals(effect.kindId()))
                .mapToInt(effect -> effect.intParameter("potency", 0))
                .findFirst()
                .orElse(0);
    }

    public boolean healsCasterFromDamage() {
        return hasEffect("heal_from_damage");
    }

    public List<String> getAppliedStatusIds() {
        return effects.stream()
                .filter(effect -> "apply_status".equals(effect.kindId()))
                .map(effect -> effect.parameter("statusId", ""))
                .filter(statusId -> !statusId.isBlank())
                .toList();
    }

    public SummonMode getSummonMode() {
        return effects.stream()
                .filter(effect -> "summon".equals(effect.kindId()))
                .map(effect -> enumValue(
                        SummonMode.class,
                        effect.parameter("mode", "NONE"),
                        SummonMode.NONE))
                .findFirst()
                .orElse(SummonMode.NONE);
    }

    public double getSummonChance() {
        return effects.stream()
                .filter(effect -> "summon".equals(effect.kindId()))
                .mapToDouble(effect -> Math.max(0.0, Math.min(1.0,
                        effect.doubleParameter("successPercent", 100) / 100.0)))
                .findFirst()
                .orElse(0.0);
    }

    public String getSummonSpeciesId() {
        if (!summonSpeciesOverride.isBlank()) {
            return summonSpeciesOverride;
        }
        return effects.stream()
                .filter(effect -> "summon".equals(effect.kindId()))
                .map(effect -> effect.parameter("speciesId", ""))
                .findFirst()
                .orElse("");
    }

    public String getSummonDisplayName() {
        return summonDisplayName;
    }

    public boolean isSummonSkill() {
        return hasEffect("summon") && getSummonMode() != SummonMode.NONE;
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

    public List<SkillEffectDefinition> getEffects() {
        return effects;
    }

    public String getPresentationStyle() {
        return presentationStyle;
    }

    public CombatElement getElement() {
        return element;
    }

    public boolean hasEffect(String kindId) {
        String normalized = kindId == null ? "" : kindId.trim().toLowerCase();
        return effects.stream().anyMatch(effect -> normalized.equals(effect.kindId()));
    }

    public BattleSkill effectView(String kindId, int effectPotency) {
        return new BattleSkill(
                name,
                description,
                targetShape,
                targetTeam,
                targetingMode,
                useSoundPath,
                skillId,
                baseCooldownSeconds,
                consumesAutoAction,
                List.of(new SkillEffectDefinition(
                        kindId,
                        SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                        SkillEffectDefinition.ActivationCondition.ALWAYS,
                        1.0,
                        Map.of("potency", String.valueOf(effectPotency)))),
                presentationStyle,
                summonSpeciesOverride,
                summonDisplayName,
                element
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
                skillId,
                baseCooldownSeconds,
                consumesAutoAction,
                effects,
                presentationStyle,
                speciesId,
                displayName,
                element
        );
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
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

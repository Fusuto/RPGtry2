package org.main.content;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkillEffectDefinition(
        String kindId,
        RecipientScope recipientScope,
        ActivationCondition condition,
        double chance,
        Map<String, String> parameters
) {
    public SkillEffectDefinition {
        kindId = normalize(kindId);
        recipientScope = recipientScope == null ? RecipientScope.RESOLVED_TARGETS : recipientScope;
        condition = condition == null ? ActivationCondition.ALWAYS : condition;
        chance = Math.max(0.0, Math.min(1.0, chance));
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }

    public String parameter(String key, String fallback) {
        String value = parameters.get(key);
        return value == null ? fallback : value;
    }

    public int intParameter(String key, int fallback) {
        try {
            return Integer.parseInt(parameter(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public double doubleParameter(String key, double fallback) {
        try {
            return Double.parseDouble(parameter(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public enum RecipientScope {
        RESOLVED_TARGETS,
        CASTER,
        BOTH
    }

    public enum ActivationCondition {
        ALWAYS,
        PREVIOUS_EFFECT_HIT,
        PREVIOUS_EFFECT_DEALT_DAMAGE,
        ANY_SKILL_DAMAGE_DEALT,
        TARGET_DEFEATED
    }
}

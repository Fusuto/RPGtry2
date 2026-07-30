package org.main.content;

import java.util.LinkedHashMap;
import java.util.Map;

public record StatusDefinition(
        String id,
        String displayName,
        String description,
        String iconPath,
        Polarity polarity,
        String behaviorKindId,
        int defaultDuration,
        StackingPolicy stackingPolicy,
        int maxStacks,
        Map<String, String> parameters
) {
    public StatusDefinition {
        id = BattleContentCatalog.normalizeId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        description = description == null ? "" : description;
        iconPath = iconPath == null ? "" : iconPath.trim().replace('\\', '/');
        polarity = polarity == null ? Polarity.NEUTRAL : polarity;
        behaviorKindId = behaviorKindId == null ? "" : behaviorKindId.trim().toLowerCase();
        defaultDuration = Math.max(1, defaultDuration);
        stackingPolicy = stackingPolicy == null ? StackingPolicy.REFRESH : stackingPolicy;
        maxStacks = stackingPolicy == StackingPolicy.STACK ? Math.max(2, maxStacks) : 1;
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }

    public int intParameter(String key, int fallback) {
        try {
            return Integer.parseInt(parameters.getOrDefault(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public double doubleParameter(String key, double fallback) {
        try {
            return Double.parseDouble(parameters.getOrDefault(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public enum Polarity {
        BENEFICIAL,
        HARMFUL,
        NEUTRAL
    }

    public enum StackingPolicy {
        REFRESH,
        REPLACE,
        STACK
    }
}

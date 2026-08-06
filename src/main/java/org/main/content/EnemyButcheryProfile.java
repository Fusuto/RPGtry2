package org.main.content;

import org.main.core.LimbSlot;

import java.util.EnumMap;
import java.util.Map;

/**
 * Authored products recovered when an enemy corpse is butchered.
 */
public record EnemyButcheryProfile(
        Type type,
        Map<LimbSlot, String> limbProductIds,
        String leatherItemId,
        Integer baseValueOverride
) {
    public enum Type {
        HUMANOID_LIMBS("Humanoid Limbs"),
        LEATHER("Leather");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public EnemyButcheryProfile {
        type = type == null ? Type.HUMANOID_LIMBS : type;
        EnumMap<LimbSlot, String> safeIds = new EnumMap<>(LimbSlot.class);
        if (limbProductIds != null) {
            for (LimbSlot slot : LimbSlot.values()) {
                String id = limbProductIds.get(slot);
                if (id != null && !id.isBlank()) {
                    safeIds.put(slot, id.trim());
                }
            }
        }
        limbProductIds = Map.copyOf(safeIds);
        leatherItemId = leatherItemId == null ? "" : leatherItemId.trim();
        baseValueOverride = baseValueOverride == null ? null : Math.max(1, baseValueOverride);
    }

    public static EnemyButcheryProfile humanoid(Map<LimbSlot, String> limbProductIds) {
        return new EnemyButcheryProfile(Type.HUMANOID_LIMBS, limbProductIds, "", null);
    }

    public static EnemyButcheryProfile defaultHumanoid() {
        return humanoid(Map.of());
    }

    public boolean hasValueOverride() {
        return baseValueOverride != null;
    }

    public String productId(LimbSlot slot) {
        return slot == null ? "" : limbProductIds.getOrDefault(slot, "");
    }
}

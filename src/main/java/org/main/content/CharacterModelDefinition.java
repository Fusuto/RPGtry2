package org.main.content;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Authored skeletal-character model and independently replaceable animation clips. */
public final class CharacterModelDefinition {
    public static final double DEFAULT_IMPACT_FRACTION = 0.55;

    public enum AnimationSlot {
        IDLE("Idle", true),
        WALK("Walk", true),
        ATTACK("Attack", false),
        HIT("Hit / Recoil", false),
        BLOCK("Block", false),
        DODGE("Dodge", false),
        CAST("Cast", false),
        DEATH("Death", false);

        private final String displayName;
        private final boolean looping;

        AnimationSlot(String displayName, boolean looping) {
            this.displayName = displayName;
            this.looping = looping;
        }

        public String displayName() {
            return displayName;
        }

        public boolean looping() {
            return looping;
        }
    }

    public record AnimationBinding(
            String path,
            String clipName,
            double playbackSpeed,
            double impactFraction
    ) {
        public AnimationBinding {
            path = normalize(path);
            clipName = clipName == null ? "" : clipName.trim();
            playbackSpeed = Double.isFinite(playbackSpeed) && playbackSpeed > 0.0
                    ? playbackSpeed
                    : 1.0;
            impactFraction = Double.isFinite(impactFraction)
                    ? Math.max(0.0, Math.min(1.0, impactFraction))
                    : DEFAULT_IMPACT_FRACTION;
        }

        public static AnimationBinding ofPath(String path) {
            return new AnimationBinding(path, "", 1.0, DEFAULT_IMPACT_FRACTION);
        }

        public boolean isPresent() {
            return !path.isBlank();
        }
    }

    private final String modelPath;
    private final String rigId;
    private final double scale;
    private final double facingRotationDegrees;
    private final double verticalOffset;
    private final Map<AnimationSlot, AnimationBinding> animationBindings;

    public CharacterModelDefinition(
            String modelPath,
            String rigId,
            double scale,
            double facingRotationDegrees,
            double verticalOffset,
            Map<AnimationSlot, AnimationBinding> animationBindings
    ) {
        this(modelPath, rigId, scale, facingRotationDegrees, verticalOffset, animationBindings, true);
    }

    private CharacterModelDefinition(
            String modelPath,
            String rigId,
            double scale,
            double facingRotationDegrees,
            double verticalOffset,
            Map<AnimationSlot, AnimationBinding> animationBindings,
            boolean ignored
    ) {
        this.modelPath = normalize(modelPath);
        this.rigId = rigId == null ? "" : rigId.trim();
        this.scale = Double.isFinite(scale) ? Math.max(0.01, scale) : 1.0;
        this.facingRotationDegrees = Double.isFinite(facingRotationDegrees)
                ? facingRotationDegrees
                : 0.0;
        this.verticalOffset = Double.isFinite(verticalOffset) ? verticalOffset : 0.0;
        EnumMap<AnimationSlot, AnimationBinding> safe = new EnumMap<>(AnimationSlot.class);
        if (animationBindings != null) {
            for (AnimationSlot slot : AnimationSlot.values()) {
                AnimationBinding binding = animationBindings.get(slot);
                if (binding != null && binding.isPresent()) {
                    safe.put(slot, binding);
                }
            }
        }
        this.animationBindings = Map.copyOf(safe);
    }

    public static CharacterModelDefinition empty() {
        return new CharacterModelDefinition("", "", 1.0, 0.0, 0.0, Map.of());
    }

    public String modelPath() {
        return modelPath;
    }

    public String rigId() {
        return rigId;
    }

    public double scale() {
        return scale;
    }

    public double facingRotationDegrees() {
        return facingRotationDegrees;
    }

    public double verticalOffset() {
        return verticalOffset;
    }

    public Map<AnimationSlot, AnimationBinding> animationBindings() {
        return animationBindings;
    }

    public boolean hasModel() {
        return !modelPath.isBlank();
    }

    public String animationPath(AnimationSlot slot) {
        return animationBinding(slot).path();
    }

    public AnimationBinding animationBinding(AnimationSlot slot) {
        if (slot == null) {
            return AnimationBinding.ofPath("");
        }
        return animationBindings.getOrDefault(slot, AnimationBinding.ofPath(""));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CharacterModelDefinition that)) {
            return false;
        }
        return Double.compare(scale, that.scale) == 0
                && Double.compare(facingRotationDegrees, that.facingRotationDegrees) == 0
                && Double.compare(verticalOffset, that.verticalOffset) == 0
                && modelPath.equals(that.modelPath)
                && rigId.equals(that.rigId)
                && animationBindings.equals(that.animationBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelPath, rigId, scale, facingRotationDegrees, verticalOffset, animationBindings);
    }
}

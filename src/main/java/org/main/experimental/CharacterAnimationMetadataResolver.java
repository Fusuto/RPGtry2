package org.main.experimental;

import org.main.content.CharacterModelDefinition;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CPU-side animation metadata shared by world motion and authored-content
 * validation. Resolving metadata never creates OpenGL resources.
 */
public final class CharacterAnimationMetadataResolver {
    public record SlotMetadata(
            boolean explicitlyAssigned,
            boolean available,
            boolean skeletonCompatible,
            double durationSeconds,
            double playbackSpeed,
            String diagnostic
    ) {
        public SlotMetadata {
            durationSeconds = Math.max(0.0, durationSeconds);
            playbackSpeed = Double.isFinite(playbackSpeed) && playbackSpeed > 0.0
                    ? playbackSpeed
                    : 1.0;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    public record ModelMetadata(
            Map<CharacterModelDefinition.AnimationSlot, SlotMetadata> slots,
            List<String> diagnostics
    ) {
        public ModelMetadata {
            slots = slots == null ? Map.of() : Map.copyOf(slots);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public SlotMetadata slot(CharacterModelDefinition.AnimationSlot slot) {
            return slots.getOrDefault(
                    slot,
                    new SlotMetadata(false, false, true, 0.0, 1.0, ""));
        }
    }

    private static final Map<CharacterModelDefinition, ModelMetadata> CACHE =
            new HashMap<>();

    private CharacterAnimationMetadataResolver() {
    }

    public static synchronized ModelMetadata resolve(
            CharacterModelDefinition definition
    ) throws IOException {
        CharacterModelDefinition safe = definition == null
                ? CharacterModelDefinition.empty()
                : definition;
        ModelMetadata cached = CACHE.get(safe);
        if (cached != null) {
            return cached;
        }
        LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(safe);
        EnumMap<CharacterModelDefinition.AnimationSlot, SlotMetadata> slots =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        for (CharacterModelDefinition.AnimationSlot slot
                : CharacterModelDefinition.AnimationSlot.values()) {
            CharacterModelDefinition.AnimationBinding binding =
                    safe.animationBinding(slot);
            String diagnostic = model.diagnostics().stream()
                    .filter(message -> message.startsWith(slot.displayName() + ":"))
                    .findFirst()
                    .orElse("");
            String normalizedDiagnostic = diagnostic.toLowerCase(Locale.ROOT);
            boolean compatible = !normalizedDiagnostic.contains("skeleton hierarchy")
                    && !normalizedDiagnostic.contains("incompatible");
            slots.put(slot, new SlotMetadata(
                    binding.isPresent(),
                    model.hasClip(slot),
                    compatible,
                    model.clipDurationSeconds(slot),
                    binding.playbackSpeed(),
                    diagnostic));
        }
        ModelMetadata resolved = new ModelMetadata(slots, model.diagnostics());
        CACHE.put(safe, resolved);
        return resolved;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }
}

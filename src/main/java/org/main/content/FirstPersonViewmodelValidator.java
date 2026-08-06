package org.main.content;

import org.main.content.FirstPersonCombatLibrary.AnimationSet;
import org.main.content.FirstPersonCombatLibrary.AnimationSlot;
import org.main.content.FirstPersonCombatLibrary.ClipBinding;
import org.main.content.FirstPersonCombatLibrary.Content;
import org.main.content.FirstPersonCombatLibrary.Diagnostic;
import org.main.content.FirstPersonCombatLibrary.ItemProfile;
import org.main.content.FirstPersonCombatLibrary.RigDefinition;
import org.main.core.WeaponType;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Expensive, Apply-time validation for authored first-person content. */
public final class FirstPersonViewmodelValidator {
    private FirstPersonViewmodelValidator() {
    }

    public static List<Diagnostic> validate(Content content, Set<String> knownItemIds) {
        List<Diagnostic> issues = new ArrayList<>();
        if (content == null || content.rigs().isEmpty()) {
            issues.add(error("catalog", "At least one first-person rig is required."));
            return issues;
        }
        if (!content.rigs().containsKey(content.defaultRigId())) {
            issues.add(error("catalog", "Default rig " + content.defaultRigId() + " does not exist."));
        }

        Map<String, LwjglSkinnedModel> loadedRigs = new HashMap<>();
        for (RigDefinition rig : content.rigs().values()) {
            validateRig(content, rig, loadedRigs, issues);
        }
        for (AnimationSet set : content.animationSets().values()) {
            validateAnimationSet(content, set, loadedRigs, issues);
        }
        Set<String> normalizedItems = new HashSet<>();
        if (knownItemIds != null) {
            knownItemIds.stream().map(FirstPersonCombatLibrary::normalizeId)
                    .forEach(normalizedItems::add);
        }
        for (ItemProfile profile : content.itemProfiles().values()) {
            validateProfile(content, profile, normalizedItems, issues);
        }
        content.weaponDefaults().forEach((type, setId) -> {
            if (!setId.isBlank() && !content.animationSets().containsKey(setId)) {
                issues.add(error("weaponDefault." + type.name(),
                        type.getDisplayName() + " references missing animation set " + setId + "."));
            }
        });
        return List.copyOf(issues);
    }

    private static void validateRig(
            Content content,
            RigDefinition rig,
            Map<String, LwjglSkinnedModel> loadedRigs,
            List<Diagnostic> issues
    ) {
        String owner = "rig:" + rig.rigId();
        if (rig.rigId().isBlank()) {
            issues.add(error(owner, "Rig ID is required."));
            return;
        }
        if (rig.displayName().isBlank()) {
            issues.add(error(owner, "Display name is required."));
        }
        if (rig.modelPath().isBlank()) {
            issues.add(error(owner, "Skeleton/base model is required."));
            return;
        }
        LwjglSkinnedModel model;
        try {
            model = LwjglSkinnedModel.loadCached(FirstPersonAnimationRuntime.definitionFor(
                    content, rig, WeaponType.NONE, null,
                    FirstPersonCombatLibrary.WieldHand.RIGHT));
            loadedRigs.put(rig.rigId(), model);
        } catch (Exception exception) {
            issues.add(error(owner, "Base model could not be loaded: " + rootMessage(exception)));
            return;
        }
        validateBone(model, owner, "left hand", rig.leftHandBone(), true, issues);
        validateBone(model, owner, "right hand", rig.rightHandBone(), true, issues);
        validateBone(model, owner, "left shoulder", rig.leftShoulderBone(), false, issues);
        validateBone(model, owner, "left elbow", rig.leftElbowBone(), false, issues);
        validateBone(model, owner, "right shoulder", rig.rightShoulderBone(), false, issues);
        validateBone(model, owner, "right elbow", rig.rightElbowBone(), false, issues);
        validateBone(model, owner, "camera anchor", rig.cameraAnchorBone(), false, issues);
        if (rig.cameraAnchorBone().isBlank()) {
            issues.add(warning(owner, "No camera anchor bone is configured; the rig uses a detached camera-space root."));
        }
        validateArmAttachment(rig, model, true, issues);
        validateArmAttachment(rig, model, false, issues);
        rig.fallbackBindings().forEach((slot, binding) ->
                validateBinding(owner, rig, slot, binding, issues));
        long availableActions = java.util.Arrays.stream(AnimationSlot.values())
                .filter(slot -> rig.fallback(slot) != null).count();
        if (availableActions == 0 && content.animationSets().values().stream()
                .noneMatch(set -> set.rigId().isBlank() || set.rigId().equals(rig.rigId()))) {
            issues.add(warning(owner, "Rig has no fallback clips or compatible animation sets."));
        }
    }

    private static void validateArmAttachment(
            RigDefinition rig,
            LwjglSkinnedModel base,
            boolean left,
            List<Diagnostic> issues
    ) {
        String side = left ? "left" : "right";
        String owner = "rig:" + rig.rigId();
        String path = left ? rig.defaultLeftArmPath() : rig.defaultRightArmPath();
        Set<String> visibleMeshes = left ? rig.leftVisibleMeshes() : rig.rightVisibleMeshes();
        LwjglSkinnedModel source = base;
        if (!path.isBlank()) {
            try {
                CharacterModelDefinition definition = new CharacterModelDefinition(
                        path, rig.rigId(), 1, 0, 0, base.definition().animationBindings());
                source = LwjglSkinnedModel.loadCached(definition);
                if (!base.skeletonSignature().equals(source.skeletonSignature())) {
                    issues.add(error(owner, "Default " + side
                            + " arm uses an incompatible skeleton."));
                    return;
                }
            } catch (Exception exception) {
                issues.add(error(owner, "Default " + side + " arm could not be loaded: "
                        + rootMessage(exception)));
                return;
            }
        } else if (visibleMeshes.isEmpty()) {
            issues.add(warning(owner, "No " + side
                    + " arm model or base-model mesh selection is configured."));
        }
        Set<String> actual = new HashSet<>();
        source.meshNames().stream().map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(actual::add);
        for (String mesh : visibleMeshes) {
            if (!actual.contains(mesh.toLowerCase(Locale.ROOT))) {
                issues.add(error(owner, "The " + side + " arm selects missing mesh " + mesh + "."));
            }
        }
    }

    private static void validateAnimationSet(
            Content content,
            AnimationSet set,
            Map<String, LwjglSkinnedModel> loadedRigs,
            List<Diagnostic> issues
    ) {
        String owner = "animationSet:" + set.id();
        if (set.id().isBlank()) {
            issues.add(error(owner, "Animation-set ID is required."));
            return;
        }
        RigDefinition rig = content.rig(set.rigId());
        if (!set.rigId().isBlank() && !content.rigs().containsKey(set.rigId())) {
            issues.add(error(owner, "Animation set references missing rig " + set.rigId() + "."));
            return;
        }
        if (set.bindings().isEmpty()) {
            issues.add(warning(owner, "Animation set has no clips."));
            return;
        }
        for (Map.Entry<AnimationSlot, ClipBinding> entry : set.bindings().entrySet()) {
            ClipBinding binding = entry.getValue();
            if (binding.clipName().isBlank()) {
                issues.add(error(owner, entry.getKey().name() + " requires an explicit clip selection."));
                continue;
            }
            validateBinding(owner, rig, entry.getKey(), binding, issues);
        }
    }

    private static void validateBinding(
            String owner,
            RigDefinition rig,
            AnimationSlot authoredSlot,
            ClipBinding binding,
            List<Diagnostic> issues
    ) {
        if (binding == null || !binding.present()) return;
        if (binding.clipName().isBlank()) {
            issues.add(error(owner, authoredSlot.name() + " requires an explicit clip selection."));
            return;
        }
        try {
            CharacterModelDefinition.AnimationSlot slot = characterSlot(authoredSlot);
            EnumMap<CharacterModelDefinition.AnimationSlot,
                    CharacterModelDefinition.AnimationBinding> bindings =
                    new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
            bindings.put(slot, new CharacterModelDefinition.AnimationBinding(
                    binding.path(), binding.clipName(), binding.playbackSpeed(),
                    binding.impactFraction()));
            LwjglSkinnedModel resolved = LwjglSkinnedModel.loadCached(
                    new CharacterModelDefinition(rig.modelPath(), rig.rigId(), rig.scale(),
                            rig.rotationY(), rig.positionY(), bindings));
            if (!resolved.hasClip(slot)) {
                issues.add(error(owner, authoredSlot.name()
                        + " does not resolve to a compatible clip."));
            }
        } catch (Exception exception) {
            issues.add(error(owner, authoredSlot.name() + " could not be loaded: "
                    + rootMessage(exception)));
        }
    }

    private static void validateProfile(
            Content content,
            ItemProfile profile,
            Set<String> knownItems,
            List<Diagnostic> issues
    ) {
        String owner = "itemProfile:" + profile.itemId();
        if (!knownItems.isEmpty() && !knownItems.contains(profile.itemId())) {
            issues.add(error(owner, "Profile references missing item " + profile.itemId() + "."));
        }
        if (!profile.rigId().isBlank() && !content.rigs().containsKey(profile.rigId())) {
            issues.add(error(owner, "Profile references missing rig " + profile.rigId() + "."));
        }
        AnimationSet set = content.animationSets().get(profile.animationSetId());
        if (!profile.animationSetId().isBlank() && set == null) {
            issues.add(error(owner, "Profile references missing animation set "
                    + profile.animationSetId() + "."));
        }
        RigDefinition rig = content.rigFor(profile);
        if (set != null && !set.rigId().isBlank()
                && !set.rigId().equals(rig.rigId())) {
            issues.add(error(owner, "Animation set " + set.id()
                    + " is not compatible with rig " + rig.rigId() + "."));
        }
        profile.overrides().forEach((slot, binding) ->
                validateBinding(owner, rig, slot, binding, issues));
        validateProfileAttachment(content, owner, "left armor", profile.leftArmorPath(),
                rig, profile, issues);
        validateProfileAttachment(content, owner, "right armor", profile.rightArmorPath(),
                rig, profile, issues);
        boolean secondaryGrip = Math.abs(profile.secondaryGripX()) > 0.0001
                || Math.abs(profile.secondaryGripY()) > 0.0001
                || Math.abs(profile.secondaryGripZ()) > 0.0001;
        FirstPersonCombatLibrary.WieldHand offHand = profile.wieldHand().opposite();
        if (secondaryGrip && (rig.shoulderBone(offHand).isBlank()
                || rig.elbowBone(offHand).isBlank()
                || rig.handBone(offHand).isBlank())) {
            issues.add(warning(owner, "Secondary grip is authored, but the off-hand IK chain is incomplete."));
        }
    }

    private static void validateProfileAttachment(
            Content content,
            String owner,
            String label,
            String path,
            RigDefinition rig,
            ItemProfile profile,
            List<Diagnostic> issues
    ) {
        if (path == null || path.isBlank()) return;
        try {
            CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                    content, rig, WeaponType.NONE, profile, profile.wieldHand());
            LwjglSkinnedModel base = LwjglSkinnedModel.loadCached(definition);
            LwjglSkinnedModel attachment = LwjglSkinnedModel.loadCached(new CharacterModelDefinition(
                    path, rig.rigId(), 1, 0, 0, definition.animationBindings()));
            if (!base.skeletonSignature().equals(attachment.skeletonSignature())) {
                issues.add(error(owner, label + " uses an incompatible skeleton."));
            }
        } catch (Exception exception) {
            issues.add(error(owner, label + " could not be loaded: " + rootMessage(exception)));
        }
    }

    private static void validateBone(
            LwjglSkinnedModel model,
            String owner,
            String label,
            String bone,
            boolean required,
            List<Diagnostic> issues
    ) {
        if (bone == null || bone.isBlank()) {
            if (required) issues.add(error(owner, "The " + label + " bone is required."));
            return;
        }
        if (!model.hasNode(bone)) {
            issues.add(error(owner, "The " + label + " bone " + bone + " does not exist."));
        }
    }

    private static CharacterModelDefinition.AnimationSlot characterSlot(AnimationSlot slot) {
        return switch (slot) {
            case IDLE_LEFT, IDLE_RIGHT -> CharacterModelDefinition.AnimationSlot.IDLE;
            case ATTACK_LEFT, ATTACK_RIGHT -> CharacterModelDefinition.AnimationSlot.ATTACK;
            case BLOCK_LEFT, BLOCK_RIGHT -> CharacterModelDefinition.AnimationSlot.BLOCK;
            case CAST -> CharacterModelDefinition.AnimationSlot.CAST;
            case HIT -> CharacterModelDefinition.AnimationSlot.HIT;
            case DODGE -> CharacterModelDefinition.AnimationSlot.DODGE;
        };
    }

    private static Diagnostic error(String owner, String message) {
        return new Diagnostic(Diagnostic.Severity.ERROR, owner, message);
    }

    private static Diagnostic warning(String owner, String message) {
        return new Diagnostic(Diagnostic.Severity.WARNING, owner, message);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

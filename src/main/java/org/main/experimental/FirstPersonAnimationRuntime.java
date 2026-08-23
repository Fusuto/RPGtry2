package org.main.experimental;

import org.main.battle.BattleActor;
import org.main.battle.BattlePresentationDirector;
import org.main.battle.BattleTiming;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.core.InventorySystem;
import org.main.core.LimbItem;
import org.main.core.LimbSlot;
import org.main.core.WeaponType;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Resolves authored first-person clips into the existing skeletal animation player. */
public final class FirstPersonAnimationRuntime {
    public record ResolvedRig(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.RigDefinition rig,
            FirstPersonCombatLibrary.ItemProfile itemProfile,
            FirstPersonCombatLibrary.WieldHand wieldHand,
            FirstPersonCombatLibrary.AnimationCompositionMode compositionMode,
            boolean twoHanded,
            CharacterModelDefinition modelDefinition,
            LwjglSkinnedModel model
    ) {
        public boolean usable() {
            return model != null && rig != null && rig.configured();
        }

        public boolean independent() {
            return compositionMode.independent(twoHanded);
        }
    }

    public record PresentationTiming(int durationMs, double impactFraction) {
        public static PresentationTiming fallback() {
            return new PresentationTiming(0, CharacterModelDefinition.DEFAULT_IMPACT_FRACTION);
        }
    }

    private static final Map<CharacterModelDefinition, LwjglSkinnedModel> MODELS =
            new ConcurrentHashMap<>();
    private static final Set<String> WARNED_MISSING_ATTACHMENT_BONES = ConcurrentHashMap.newKeySet();
    private static final Logger LOGGER = Logger.getLogger(FirstPersonAnimationRuntime.class.getName());

    private FirstPersonAnimationRuntime() {
    }

    public static ResolvedRig resolve(BattleActor player) {
        if (player == null || player.getSourcePlayer() == null) return empty();
        InventorySystem.Item weapon = player.getSourcePlayer().getInventory()
                .getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = player.getSourcePlayer().getInventory()
                .getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.load();
        FirstPersonCombatLibrary.ItemProfile profile = content.itemProfile(weapon);
        FirstPersonCombatLibrary.ItemProfile shieldProfile = content.itemProfile(shield);
        FirstPersonCombatLibrary.WieldHand hand = profile != null
                ? profile.wieldHand()
                : FirstPersonCombatLibrary.WieldHand.RIGHT;
        FirstPersonCombatLibrary.WieldHand blockHand = shieldProfile == null
                ? hand.opposite() : shieldProfile.wieldHand();
        FirstPersonCombatLibrary.ItemProfile rigProfile = profile == null
                ? shieldProfile : profile;
        FirstPersonCombatLibrary.RigDefinition rig = content.rigFor(rigProfile);
        if ((profile == null || profile.rigId().isBlank())) {
            LimbItem wieldingArm = player.getSourcePlayer().getEquippedLimb(
                    hand == FirstPersonCombatLibrary.WieldHand.LEFT
                            ? LimbSlot.LEFT_ARM
                            : LimbSlot.RIGHT_ARM);
            if (wieldingArm != null
                    && !wieldingArm.getFirstPersonRigId().isBlank()
                    && content.rigs().containsKey(FirstPersonCombatLibrary.normalizeId(
                            wieldingArm.getFirstPersonRigId()))) {
                rig = content.rig(wieldingArm.getFirstPersonRigId());
            }
        }
        CharacterModelDefinition definition = definitionFor(
                content, rig,
                weapon == null ? WeaponType.NONE : weapon.getWeaponType(),
                profile,
                hand,
                shieldProfile,
                blockHand,
                shieldProfile == null
                        ? weapon == null ? WeaponType.NONE : weapon.getWeaponType()
                        : WeaponType.NONE);
        FirstPersonCombatLibrary.ItemProfile compositionProfile = profile == null
                ? shieldProfile : profile;
        FirstPersonCombatLibrary.AnimationCompositionMode compositionMode = compositionProfile == null
                ? FirstPersonCombatLibrary.AnimationCompositionMode.AUTO
                : compositionProfile.animationComposition();
        boolean twoHanded = weapon != null && weapon.isTwoHanded();
        if (!definition.hasModel()) {
            return new ResolvedRig(content, rig, profile, hand, compositionMode, twoHanded,
                    definition, null);
        }
        LwjglSkinnedModel model;
        try {
            model = MODELS.computeIfAbsent(definition, key -> {
                try {
                    return LwjglSkinnedModel.load(key);
                } catch (IOException exception) {
                    throw new ModelLoadFailure(exception);
                }
            });
        } catch (ModelLoadFailure failure) {
            model = null;
        }
        return new ResolvedRig(content, rig, profile, hand, compositionMode, twoHanded,
                definition, model);
    }

    public static PresentationTiming timing(
            BattleActor actor,
            BattlePresentationDirector.ActionType actionType
    ) {
        ResolvedRig resolved = resolve(actor);
        if (!resolved.usable()) return PresentationTiming.fallback();
        CharacterModelDefinition.AnimationSlot slot = characterSlot(actionType);
        FirstPersonCombatLibrary.AnimationSlot namedSlot = independentSlot(resolved, actionType);
        boolean named = namedSlot != null && resolved.model().hasNamedClip(namedSlot.name());
        if (!named && !resolved.model().hasClip(slot)) return PresentationTiming.fallback();
        double naturalSeconds = named
                ? resolved.model().namedClipDurationSeconds(namedSlot.name())
                : resolved.model().clipDurationSeconds(slot);
        if (!(naturalSeconds > 0.0)) return PresentationTiming.fallback();
        if (actionType == BattlePresentationDirector.ActionType.AUTO_ATTACK) {
            double attackInterval = BattleTiming.calculateAttackIntervalSeconds(
                    actor.getAgilityStat(), actor.getWeaponSpeedMultiplier());
            naturalSeconds = Math.min(naturalSeconds, Math.max(0.05, attackInterval));
        }
        return new PresentationTiming(
                Math.max(3, (int) Math.round(naturalSeconds * 1000.0)),
                named ? resolved.model().namedImpactFraction(namedSlot.name())
                        : resolved.model().impactFraction(slot));
    }

    private static FirstPersonCombatLibrary.AnimationSlot independentSlot(
            ResolvedRig resolved,
            BattlePresentationDirector.ActionType actionType
    ) {
        if (resolved == null || !resolved.independent() || actionType == null) return null;
        return switch (actionType) {
            case AUTO_ATTACK, PHYSICAL_SKILL, RANGED -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
            case SPELL, HEAL, SUMMON -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.CAST_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.CAST_RIGHT;
            default -> null;
        };
    }

    public static CharacterModelDefinition.AnimationSlot characterSlot(
            BattlePresentationDirector.ActionType actionType
    ) {
        if (actionType == null) return CharacterModelDefinition.AnimationSlot.IDLE;
        return switch (actionType) {
            case AUTO_ATTACK, PHYSICAL_SKILL, RANGED ->
                    CharacterModelDefinition.AnimationSlot.ATTACK;
            case SPELL, HEAL, SUMMON -> CharacterModelDefinition.AnimationSlot.CAST;
            case DEFEND -> CharacterModelDefinition.AnimationSlot.BLOCK;
            case DEATH -> CharacterModelDefinition.AnimationSlot.HIT;
        };
    }

    public static void clearCaches() {
        MODELS.clear();
        WARNED_MISSING_ATTACHMENT_BONES.clear();
        StaticModelPlacementMetadataResolver.clearCache();
    }

    /**
     * Resolves the parent node used by both runtime rendering and editor previews.
     * Invalid externally-authored explicit nodes safely fall back to the inherited hand.
     */
    public static String resolveAttachmentBone(
            FirstPersonCombatLibrary.RigDefinition rig,
            FirstPersonCombatLibrary.ItemProfile profile,
            LwjglSkinnedModel model
    ) {
        FirstPersonCombatLibrary.WieldHand hand = profile == null
                ? FirstPersonCombatLibrary.WieldHand.RIGHT : profile.wieldHand();
        String inherited = rig == null ? "" : rig.handBone(hand);
        String explicit = profile == null ? "" : profile.attachmentBone();
        if (explicit.isBlank()) return inherited;
        if (model == null || model.hasNode(explicit) && model.followsAnimatedHierarchy(explicit)) {
            return explicit;
        }
        String warningKey = (rig == null ? "" : rig.rigId()) + "\u0000" + explicit;
        if (WARNED_MISSING_ATTACHMENT_BONES.add(warningKey)) {
            LOGGER.warning("First-person attachment bone '" + explicit
                    + "' is missing or does not follow the animated hierarchy in rig '"
                    + (rig == null ? "" : rig.rigId())
                    + "'; falling back to inherited " + hand.name().toLowerCase()
                    + " hand bone '" + inherited + "'.");
        }
        return inherited;
    }

    public static CharacterModelDefinition definitionFor(
            FirstPersonCombatLibrary.Content content,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.WieldHand hand
    ) {
        return definitionFor(content, content.rigFor(profile), weaponType, profile, hand);
    }

    public static CharacterModelDefinition definitionFor(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.RigDefinition rig,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.WieldHand hand
    ) {
        return definitionFor(content, rig, weaponType, profile, hand,
                profile, hand.opposite(), weaponType);
    }

    public static CharacterModelDefinition definitionFor(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.RigDefinition rig,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.WieldHand hand,
            FirstPersonCombatLibrary.ItemProfile blockProfile,
            FirstPersonCombatLibrary.WieldHand blockHand,
            WeaponType blockWeaponType
    ) {
        EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> bindings =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        put(bindings, CharacterModelDefinition.AnimationSlot.IDLE,
                content.resolveBinding(weaponType, profile, rig, hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT));
        put(bindings, CharacterModelDefinition.AnimationSlot.ATTACK,
                content.resolveBinding(weaponType, profile, rig, hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT));
        put(bindings, CharacterModelDefinition.AnimationSlot.BLOCK,
                content.resolveBinding(blockWeaponType, blockProfile, rig,
                        blockHand == FirstPersonCombatLibrary.WieldHand.LEFT
                                ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                                : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT));
        put(bindings, CharacterModelDefinition.AnimationSlot.CAST,
                content.resolveBinding(weaponType, profile, rig, FirstPersonCombatLibrary.AnimationSlot.CAST));
        put(bindings, CharacterModelDefinition.AnimationSlot.HIT,
                content.resolveBinding(weaponType, profile, rig, FirstPersonCombatLibrary.AnimationSlot.HIT));
        put(bindings, CharacterModelDefinition.AnimationSlot.DODGE,
                content.resolveBinding(weaponType, profile, rig, FirstPersonCombatLibrary.AnimationSlot.DODGE));
        Map<String, CharacterModelDefinition.AnimationBinding> namedBindings = new java.util.LinkedHashMap<>();
        for (FirstPersonCombatLibrary.AnimationSlot namedSlot
                : FirstPersonCombatLibrary.AnimationSlot.values()) {
            FirstPersonCombatLibrary.ItemProfile sourceProfile = switch (namedSlot) {
                case BLOCK_LEFT, BLOCK_RIGHT -> blockProfile;
                default -> profile;
            };
            WeaponType sourceWeaponType = switch (namedSlot) {
                case BLOCK_LEFT, BLOCK_RIGHT -> blockWeaponType;
                default -> weaponType;
            };
            FirstPersonCombatLibrary.ClipBinding source = content.resolveBinding(
                    sourceWeaponType, sourceProfile, rig, namedSlot);
            if (source == null || !source.present()) continue;
            namedBindings.put(namedSlot.name(), new CharacterModelDefinition.AnimationBinding(
                    source.path(), source.clipName(), source.playbackSpeed(), source.impactFraction()));
        }
        return new CharacterModelDefinition(
                rig.modelPath(), rig.rigId(), rig.scale(),
                rig.rotationY(), rig.positionY(), bindings, namedBindings);
    }

    private static void put(
            EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> target,
            CharacterModelDefinition.AnimationSlot slot,
            FirstPersonCombatLibrary.ClipBinding source
    ) {
        if (source == null || !source.present()) return;
        target.put(slot, new CharacterModelDefinition.AnimationBinding(
                source.path(), source.clipName(), source.playbackSpeed(), source.impactFraction()));
    }

    private static ResolvedRig empty() {
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.load();
        return new ResolvedRig(content, content.rig(), null, FirstPersonCombatLibrary.WieldHand.RIGHT,
                FirstPersonCombatLibrary.AnimationCompositionMode.AUTO, false,
                CharacterModelDefinition.empty(), null);
    }

    private static final class ModelLoadFailure extends RuntimeException {
        private ModelLoadFailure(IOException cause) {
            super(cause);
        }
    }
}

package org.main.experimental;

import org.main.battle.BattleActor;
import org.main.battle.BattlePresentationDirector;
import org.main.battle.BattleTiming;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves authored first-person clips into the existing skeletal animation player. */
public final class FirstPersonAnimationRuntime {
    public record ResolvedRig(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.ItemProfile itemProfile,
            FirstPersonCombatLibrary.WieldHand wieldHand,
            CharacterModelDefinition modelDefinition,
            LwjglSkinnedModel model
    ) {
        public boolean usable() {
            return model != null && content.rig().configured();
        }
    }

    public record PresentationTiming(int durationMs, double impactFraction) {
        public static PresentationTiming fallback() {
            return new PresentationTiming(0, CharacterModelDefinition.DEFAULT_IMPACT_FRACTION);
        }
    }

    private static final Map<CharacterModelDefinition, LwjglSkinnedModel> MODELS =
            new ConcurrentHashMap<>();

    private FirstPersonAnimationRuntime() {
    }

    public static ResolvedRig resolve(BattleActor player) {
        if (player == null || player.getSourcePlayer() == null) return empty();
        InventorySystem.Item weapon = player.getSourcePlayer().getInventory()
                .getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.load();
        FirstPersonCombatLibrary.ItemProfile profile = content.itemProfile(weapon);
        FirstPersonCombatLibrary.WieldHand hand = profile == null
                ? FirstPersonCombatLibrary.WieldHand.RIGHT : profile.wieldHand();
        CharacterModelDefinition definition = definitionFor(
                content,
                weapon == null ? WeaponType.NONE : weapon.getWeaponType(),
                profile,
                hand);
        if (!definition.hasModel()) {
            return new ResolvedRig(content, profile, hand, definition, null);
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
        return new ResolvedRig(content, profile, hand, definition, model);
    }

    public static PresentationTiming timing(
            BattleActor actor,
            BattlePresentationDirector.ActionType actionType
    ) {
        ResolvedRig resolved = resolve(actor);
        if (!resolved.usable()) return PresentationTiming.fallback();
        CharacterModelDefinition.AnimationSlot slot = characterSlot(actionType);
        if (!resolved.model().hasClip(slot)) return PresentationTiming.fallback();
        double naturalSeconds = resolved.model().clipDurationSeconds(slot);
        if (!(naturalSeconds > 0.0)) return PresentationTiming.fallback();
        if (actionType == BattlePresentationDirector.ActionType.AUTO_ATTACK) {
            double attackInterval = BattleTiming.calculateAttackIntervalSeconds(
                    actor.getAgilityStat(), actor.getWeaponSpeedMultiplier());
            naturalSeconds = Math.min(naturalSeconds, Math.max(0.05, attackInterval));
        }
        return new PresentationTiming(
                Math.max(3, (int) Math.round(naturalSeconds * 1000.0)),
                resolved.model().impactFraction(slot));
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
    }

    public static CharacterModelDefinition definitionFor(
            FirstPersonCombatLibrary.Content content,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.WieldHand hand
    ) {
        EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> bindings =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        put(bindings, CharacterModelDefinition.AnimationSlot.IDLE,
                content.resolveBinding(weaponType, profile, hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT));
        put(bindings, CharacterModelDefinition.AnimationSlot.ATTACK,
                content.resolveBinding(weaponType, profile, hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT));
        FirstPersonCombatLibrary.WieldHand shieldHand = hand.opposite();
        put(bindings, CharacterModelDefinition.AnimationSlot.BLOCK,
                content.resolveBinding(weaponType, profile,
                        shieldHand == FirstPersonCombatLibrary.WieldHand.LEFT
                                ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                                : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT));
        put(bindings, CharacterModelDefinition.AnimationSlot.CAST,
                content.resolveBinding(weaponType, profile, FirstPersonCombatLibrary.AnimationSlot.CAST));
        put(bindings, CharacterModelDefinition.AnimationSlot.HIT,
                content.resolveBinding(weaponType, profile, FirstPersonCombatLibrary.AnimationSlot.HIT));
        put(bindings, CharacterModelDefinition.AnimationSlot.DODGE,
                content.resolveBinding(weaponType, profile, FirstPersonCombatLibrary.AnimationSlot.DODGE));
        FirstPersonCombatLibrary.RigDefinition rig = content.rig();
        return new CharacterModelDefinition(
                rig.modelPath(), rig.rigId(), rig.scale(),
                rig.rotationY(), rig.positionY(), bindings);
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
        return new ResolvedRig(content, null, FirstPersonCombatLibrary.WieldHand.RIGHT,
                CharacterModelDefinition.empty(), null);
    }

    private static final class ModelLoadFailure extends RuntimeException {
        private ModelLoadFailure(IOException cause) {
            super(cause);
        }
    }
}

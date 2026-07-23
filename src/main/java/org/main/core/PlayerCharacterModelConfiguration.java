package org.main.core;

import org.main.content.CharacterModelDefinition;

import java.util.EnumMap;

/** Global authored player rig used when constructing the player's battle actor. */
public final class PlayerCharacterModelConfiguration {
    public static final String PREFIX = "battle.playerModel.";

    private PlayerCharacterModelConfiguration() { }

    public static CharacterModelDefinition load() {
        EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> bindings =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            String slotPrefix = PREFIX + "animation." + slot.name();
            String path = GameConfiguration.stringValue(slotPrefix + ".path", "");
            if (!path.isBlank()) bindings.put(slot, new CharacterModelDefinition.AnimationBinding(
                    path,
                    GameConfiguration.stringValue(slotPrefix + ".clipName", ""),
                    GameConfiguration.doubleValue(slotPrefix + ".speed", 1.0),
                    GameConfiguration.doubleValue(slotPrefix + ".impactFraction",
                            CharacterModelDefinition.DEFAULT_IMPACT_FRACTION)
            ));
        }
        return new CharacterModelDefinition(
                GameConfiguration.stringValue(PREFIX + "path", ""),
                GameConfiguration.stringValue(PREFIX + "rigId", ""),
                GameConfiguration.doubleValue(PREFIX + "scale", 1.0),
                GameConfiguration.doubleValue(PREFIX + "facingRotationDegrees", 0.0),
                GameConfiguration.doubleValue(PREFIX + "verticalOffset", 0.0),
                bindings
        );
    }
}

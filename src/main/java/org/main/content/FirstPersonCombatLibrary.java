package org.main.content;

import org.main.core.EquipmentViewModelProfile;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;
import org.main.engine.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Authored first-person arm rig, animation sets, and per-item equipment profiles. */
public final class FirstPersonCombatLibrary {
    public static final String RIG_ASSET = "assets/editor/content/first_person_rig.properties";
    public static final String ANIMATION_ASSET = "assets/editor/content/weapon_animation.properties";

    public enum WieldHand {
        LEFT,
        RIGHT;

        public WieldHand opposite() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    public enum ArmCoverage {
        OVERLAY,
        HIDE_HAND,
        HIDE_FOREARM,
        HIDE_FULL_ARM
    }

    public enum AnimationSlot {
        IDLE_LEFT(true),
        IDLE_RIGHT(true),
        ATTACK_LEFT(false),
        ATTACK_RIGHT(false),
        BLOCK_LEFT(false),
        BLOCK_RIGHT(false),
        CAST(false),
        HIT(false),
        DODGE(false);

        private final boolean looping;

        AnimationSlot(boolean looping) {
            this.looping = looping;
        }

        public boolean looping() {
            return looping;
        }
    }

    public record ClipBinding(
            String path,
            String clipName,
            double playbackSpeed,
            double impactFraction
    ) {
        public ClipBinding {
            path = normalize(path);
            clipName = clipName == null ? "" : clipName.trim();
            playbackSpeed = finitePositive(playbackSpeed, 1.0);
            impactFraction = clamp(impactFraction, 0.05, 0.95, 0.55);
        }

        public boolean present() {
            return !path.isBlank();
        }
    }

    public record RigDefinition(
            String modelPath,
            String rigId,
            String defaultLeftArmPath,
            String defaultRightArmPath,
            String leftHandBone,
            String rightHandBone,
            double positionX,
            double positionY,
            double positionZ,
            double rotationX,
            double rotationY,
            double rotationZ,
            double scale,
            Map<AnimationSlot, ClipBinding> fallbackBindings
    ) {
        public RigDefinition {
            modelPath = normalize(modelPath);
            rigId = rigId == null ? "" : rigId.trim();
            defaultLeftArmPath = normalize(defaultLeftArmPath);
            defaultRightArmPath = normalize(defaultRightArmPath);
            leftHandBone = blankDefault(leftHandBone, "Hand.L");
            rightHandBone = blankDefault(rightHandBone, "Hand.R");
            positionX = finite(positionX, 0.0);
            positionY = finite(positionY, 0.0);
            positionZ = finite(positionZ, -0.75);
            rotationX = finite(rotationX, 0.0);
            rotationY = finite(rotationY, 0.0);
            rotationZ = finite(rotationZ, 0.0);
            scale = finitePositive(scale, 1.0);
            fallbackBindings = fallbackBindings == null ? Map.of() : Map.copyOf(fallbackBindings);
        }

        public boolean configured() {
            return !modelPath.isBlank();
        }

        public String handBone(WieldHand hand) {
            return hand == WieldHand.LEFT ? leftHandBone : rightHandBone;
        }
    }

    public record AnimationSet(
            String id,
            String displayName,
            Map<AnimationSlot, ClipBinding> bindings
    ) {
        public AnimationSet {
            id = normalizeId(id);
            displayName = blankDefault(displayName, id);
            bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
        }

        public ClipBinding binding(AnimationSlot slot) {
            return bindings.get(slot);
        }
    }

    public record ItemProfile(
            String itemId,
            WieldHand wieldHand,
            String animationSetId,
            EquipmentViewModelProfile socketTransform,
            double secondaryGripX,
            double secondaryGripY,
            double secondaryGripZ,
            String leftArmorPath,
            String rightArmorPath,
            ArmCoverage leftCoverage,
            ArmCoverage rightCoverage,
            Map<AnimationSlot, ClipBinding> overrides
    ) {
        public ItemProfile {
            itemId = normalizeId(itemId);
            wieldHand = wieldHand == null ? WieldHand.RIGHT : wieldHand;
            animationSetId = normalizeId(animationSetId);
            socketTransform = socketTransform == null ? socketDefaults() : socketTransform;
            secondaryGripX = finite(secondaryGripX, 0.0);
            secondaryGripY = finite(secondaryGripY, 0.0);
            secondaryGripZ = finite(secondaryGripZ, 0.0);
            leftArmorPath = normalize(leftArmorPath);
            rightArmorPath = normalize(rightArmorPath);
            leftCoverage = leftCoverage == null ? ArmCoverage.OVERLAY : leftCoverage;
            rightCoverage = rightCoverage == null ? ArmCoverage.OVERLAY : rightCoverage;
            overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        }

        public ClipBinding override(AnimationSlot slot) {
            return overrides.get(slot);
        }

        public static EquipmentViewModelProfile socketDefaults() {
            return new EquipmentViewModelProfile(
                    0, 0, 0, 0, 0, 0, 1,
                    0, 0, 1, false);
        }
    }

    public record Content(
            RigDefinition rig,
            Map<String, AnimationSet> animationSets,
            Map<String, ItemProfile> itemProfiles,
            Map<WeaponType, String> weaponDefaults
    ) {
        public Content {
            rig = rig == null ? emptyRig() : rig;
            animationSets = animationSets == null ? Map.of() : Map.copyOf(animationSets);
            itemProfiles = itemProfiles == null ? Map.of() : Map.copyOf(itemProfiles);
            weaponDefaults = weaponDefaults == null ? Map.of() : Map.copyOf(weaponDefaults);
        }

        public ItemProfile itemProfile(InventorySystem.Item item) {
            if (item == null) return null;
            String id = normalizeId(item.getContentId());
            if (!id.isBlank() && itemProfiles.containsKey(id)) return itemProfiles.get(id);
            return itemProfiles.get(normalizeId(item.getName()));
        }

        public AnimationSet animationSet(InventorySystem.Item weapon, ItemProfile profile) {
            return animationSet(weapon == null ? WeaponType.NONE : weapon.getWeaponType(), profile);
        }

        public AnimationSet animationSet(WeaponType weaponType, ItemProfile profile) {
            String id = profile == null ? "" : profile.animationSetId();
            if (id.isBlank()) {
                id = weaponType == null || weaponType == WeaponType.NONE
                        ? "unarmed"
                        : weaponDefaults.getOrDefault(weaponType,
                                weaponType.name().toLowerCase(Locale.ROOT));
            }
            return animationSets.get(normalizeId(id));
        }

        public ClipBinding resolveBinding(
                InventorySystem.Item weapon,
                ItemProfile profile,
                AnimationSlot slot
        ) {
            return resolveBinding(weapon == null ? WeaponType.NONE : weapon.getWeaponType(),
                    profile, slot);
        }

        public ClipBinding resolveBinding(
                WeaponType weaponType,
                ItemProfile profile,
                AnimationSlot slot
        ) {
            if (profile != null) {
                ClipBinding override = profile.override(slot);
                if (override != null && override.present()) return override;
            }
            AnimationSet set = animationSet(weaponType, profile);
            if (set != null) {
                ClipBinding binding = set.binding(slot);
                if (binding != null && binding.present()) return binding;
            }
            ClipBinding fallback = rig.fallbackBindings().get(slot);
            return fallback != null && fallback.present() ? fallback : null;
        }
    }

    private static volatile Content cached;

    private FirstPersonCombatLibrary() {
    }

    public static Content load() {
        Content value = cached;
        if (value != null) return value;
        synchronized (FirstPersonCombatLibrary.class) {
            if (cached == null) cached = loadFresh();
            return cached;
        }
    }

    public static Content reload() {
        synchronized (FirstPersonCombatLibrary.class) {
            cached = loadFresh();
            return cached;
        }
    }

    public static Content loadFresh() {
        Properties rigProperties = loadProperties(RIG_ASSET);
        Properties animationProperties = loadProperties(ANIMATION_ASSET);
        return new Content(
                readRig(rigProperties),
                readAnimationSets(animationProperties),
                readItemProfiles(animationProperties),
                readWeaponDefaults(animationProperties));
    }

    public static RigDefinition emptyRig() {
        return new RigDefinition("", "", "", "", "Hand.L", "Hand.R",
                0, 0, -0.75, 0, 0, 0, 1, Map.of());
    }

    public static String itemKey(InventorySystem.Item item) {
        if (item == null) return "";
        String contentId = normalizeId(item.getContentId());
        return contentId.isBlank() ? normalizeId(item.getName()) : contentId;
    }

    public static String defaultSetId(WeaponType type) {
        if (type == null || type == WeaponType.NONE) return "unarmed";
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static RigDefinition readRig(Properties properties) {
        EnumMap<AnimationSlot, ClipBinding> bindings = new EnumMap<>(AnimationSlot.class);
        for (AnimationSlot slot : AnimationSlot.values()) {
            ClipBinding binding = readBinding(properties, "rig.animation." + slot.name() + ".");
            if (binding.present()) bindings.put(slot, binding);
        }
        return new RigDefinition(
                properties.getProperty("rig.modelPath", ""),
                properties.getProperty("rig.rigId", ""),
                properties.getProperty("rig.defaultLeftArmPath", ""),
                properties.getProperty("rig.defaultRightArmPath", ""),
                properties.getProperty("rig.leftHandBone", "Hand.L"),
                properties.getProperty("rig.rightHandBone", "Hand.R"),
                number(properties, "rig.positionX", 0),
                number(properties, "rig.positionY", 0),
                number(properties, "rig.positionZ", -0.75),
                number(properties, "rig.rotationX", 0),
                number(properties, "rig.rotationY", 0),
                number(properties, "rig.rotationZ", 0),
                number(properties, "rig.scale", 1),
                bindings);
    }

    private static Map<String, AnimationSet> readAnimationSets(Properties properties) {
        int count = integer(properties, "animationSet.count", 0);
        Map<String, AnimationSet> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "animationSet." + index + ".";
            String id = normalizeId(properties.getProperty(prefix + "id", ""));
            if (id.isBlank()) continue;
            EnumMap<AnimationSlot, ClipBinding> bindings = new EnumMap<>(AnimationSlot.class);
            for (AnimationSlot slot : AnimationSlot.values()) {
                ClipBinding binding = readBinding(properties, prefix + "animation." + slot.name() + ".");
                if (binding.present()) bindings.put(slot, binding);
            }
            result.put(id, new AnimationSet(
                    id, properties.getProperty(prefix + "displayName", id), bindings));
        }
        return result;
    }

    private static Map<String, ItemProfile> readItemProfiles(Properties properties) {
        int count = integer(properties, "itemProfile.count", 0);
        Map<String, ItemProfile> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "itemProfile." + index + ".";
            String itemId = normalizeId(properties.getProperty(prefix + "itemId", ""));
            if (itemId.isBlank()) continue;
            EquipmentViewModelProfile transform = new EquipmentViewModelProfile(
                    number(properties, prefix + "socket.positionX", 0),
                    number(properties, prefix + "socket.positionY", 0),
                    number(properties, prefix + "socket.positionZ", 0),
                    number(properties, prefix + "socket.rotationX", 0),
                    number(properties, prefix + "socket.rotationY", 0),
                    number(properties, prefix + "socket.rotationZ", 0),
                    number(properties, prefix + "socket.scale", 1),
                    0, 0, 1, false);
            EnumMap<AnimationSlot, ClipBinding> overrides = new EnumMap<>(AnimationSlot.class);
            for (AnimationSlot slot : AnimationSlot.values()) {
                ClipBinding binding = readBinding(properties, prefix + "override." + slot.name() + ".");
                if (binding.present()) overrides.put(slot, binding);
            }
            ItemProfile profile = new ItemProfile(
                    itemId,
                    enumValue(WieldHand.class, properties.getProperty(prefix + "wieldHand", ""), WieldHand.RIGHT),
                    properties.getProperty(prefix + "animationSetId", ""),
                    transform,
                    number(properties, prefix + "secondaryGripX", 0),
                    number(properties, prefix + "secondaryGripY", 0),
                    number(properties, prefix + "secondaryGripZ", 0),
                    properties.getProperty(prefix + "leftArmorPath", ""),
                    properties.getProperty(prefix + "rightArmorPath", ""),
                    enumValue(ArmCoverage.class, properties.getProperty(prefix + "leftCoverage", ""),
                            ArmCoverage.OVERLAY),
                    enumValue(ArmCoverage.class, properties.getProperty(prefix + "rightCoverage", ""),
                            ArmCoverage.OVERLAY),
                    overrides);
            result.put(itemId, profile);
        }
        return result;
    }

    private static Map<WeaponType, String> readWeaponDefaults(Properties properties) {
        EnumMap<WeaponType, String> result = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            if (type == WeaponType.NONE) continue;
            result.put(type, normalizeId(properties.getProperty(
                    "weaponDefault." + type.name(), defaultSetId(type))));
        }
        return result;
    }

    private static ClipBinding readBinding(Properties properties, String prefix) {
        return new ClipBinding(
                properties.getProperty(prefix + "path", ""),
                properties.getProperty(prefix + "clipName", ""),
                number(properties, prefix + "speed", 1),
                number(properties, prefix + "impactFraction", 0.55));
    }

    private static Properties loadProperties(String path) {
        Properties properties = new Properties();
        try (InputStream input = AssetLoader.openAssetStream(path)) {
            properties.load(input);
        } catch (IOException ignored) {
            // Empty content is a supported backward-compatible state.
        }
        return properties;
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double number(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String value,
            E fallback
    ) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}

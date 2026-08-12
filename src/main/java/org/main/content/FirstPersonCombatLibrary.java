package org.main.content;

import org.main.core.EquipmentViewModelProfile;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;
import org.main.engine.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Versioned authored first-person viewmodel catalog. Rigs, motion sets and
 * equipment profiles deliberately share one snapshot so editor drafts and the
 * runtime can never observe a half-updated combination.
 */
public final class FirstPersonCombatLibrary {
    public static final int SCHEMA_VERSION = 3;
    private static final int MINIMUM_READABLE_SCHEMA_VERSION = 2;
    public static final String ASSET = "assets/editor/content/first_person_rig.properties";
    public static final Path RESOURCE_PATH = Path.of(
            "src", "main", "resources", "assets", "editor", "content",
            "first_person_rig.properties");
    private static volatile Content cached;
    private static volatile long revision = 1;

    private FirstPersonCombatLibrary() {
    }

    public static Content emptyContent() {
        return new Content("", Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static RigDefinition emptyRig() {
        return new RigDefinition(
                "", "", "", "", "",
                "", "", "Hand.L", "", "", "Hand.R", "",
                Set.of(), Set.of(),
                0, 0, -0.75, 0, 0, 0, 1,
                70, 0.05, 120, Map.of());
    }

    public static RigDefinition newRig(String id, String name) {
        return new RigDefinition(
                id, name, "", "", "",
                "", "", "Hand.L", "", "", "Hand.R", "",
                Set.of(), Set.of(),
                0, 0, -0.75, 0, 0, 0, 1,
                70, 0.05, 120, Map.of());
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
            revision++;
            return cached;
        }
    }

    public static void install(Content content) {
        synchronized (FirstPersonCombatLibrary.class) {
            cached = content == null ? emptyContent() : content;
            revision++;
        }
    }

    public static long revision() {
        return revision;
    }

    public static Content loadFresh() {
        Properties properties = loadProperties(ASSET);
        return read(properties);
    }

    public static Content load(Path path) throws IOException {
        Properties properties = new Properties();
        if (path != null && Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }
        }
        return read(properties);
    }

    public static void save(Path path, Content content) throws IOException {
        if (path == null) throw new IOException("First-person catalog path is missing.");
        Properties properties = write(content == null ? emptyContent() : content);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Aether first-person viewmodel catalog");
        }
        try {
            Files.move(temporary, path,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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

    private static Content read(Properties properties) {
        int schemaVersion = integer(properties, "schemaVersion", 0);
        if (schemaVersion < MINIMUM_READABLE_SCHEMA_VERSION || schemaVersion > SCHEMA_VERSION) {
            return emptyContent();
        }
        LinkedHashMap<String, RigDefinition> rigs = new LinkedHashMap<>();
        int rigCount = integer(properties, "rig.count", 0);
        for (int index = 0; index < rigCount; index++) {
            String prefix = "rig." + index + ".";
            String id = normalizeId(properties.getProperty(prefix + "id", ""));
            if (id.isBlank()) continue;
            EnumMap<AnimationSlot, ClipBinding> fallback = readBindings(
                    properties, prefix + "fallback.");
            RigDefinition rig = new RigDefinition(
                    id,
                    properties.getProperty(prefix + "displayName", id),
                    properties.getProperty(prefix + "modelPath", ""),
                    properties.getProperty(prefix + "left.modelPath", ""),
                    properties.getProperty(prefix + "right.modelPath", ""),
                    properties.getProperty(prefix + "left.shoulderBone", ""),
                    properties.getProperty(prefix + "left.elbowBone", ""),
                    properties.getProperty(prefix + "left.handBone", "Hand.L"),
                    properties.getProperty(prefix + "right.shoulderBone", ""),
                    properties.getProperty(prefix + "right.elbowBone", ""),
                    properties.getProperty(prefix + "right.handBone", "Hand.R"),
                    properties.getProperty(prefix + "cameraAnchorBone", ""),
                    splitNames(properties.getProperty(prefix + "left.visibleMeshes", "")),
                    splitNames(properties.getProperty(prefix + "right.visibleMeshes", "")),
                    number(properties, prefix + "positionX", 0),
                    number(properties, prefix + "positionY", 0),
                    number(properties, prefix + "positionZ", -0.75),
                    number(properties, prefix + "rotationX", 0),
                    number(properties, prefix + "rotationY", 0),
                    number(properties, prefix + "rotationZ", 0),
                    number(properties, prefix + "scale", 1),
                    number(properties, prefix + "fieldOfViewDegrees", 70),
                    number(properties, prefix + "nearPlane", 0.05),
                    integer(properties, prefix + "crossfadeMs", 120),
                    fallback);
            rigs.put(id, rig);
        }

        LinkedHashMap<String, AnimationSet> sets = new LinkedHashMap<>();
        int setCount = integer(properties, "animationSet.count", 0);
        for (int index = 0; index < setCount; index++) {
            String prefix = "animationSet." + index + ".";
            String id = normalizeId(properties.getProperty(prefix + "id", ""));
            if (id.isBlank()) continue;
            sets.put(id, new AnimationSet(
                    id,
                    properties.getProperty(prefix + "displayName", id),
                    properties.getProperty(prefix + "rigId", ""),
                    readBindings(properties, prefix + "binding.")));
        }

        LinkedHashMap<String, ItemProfile> profiles = new LinkedHashMap<>();
        int profileCount = integer(properties, "itemProfile.count", 0);
        for (int index = 0; index < profileCount; index++) {
            String prefix = "itemProfile." + index + ".";
            String itemId = normalizeId(properties.getProperty(prefix + "itemId", ""));
            if (itemId.isBlank()) continue;
            EquipmentViewModelProfile socket = new EquipmentViewModelProfile(
                    number(properties, prefix + "socket.positionX", 0),
                    number(properties, prefix + "socket.positionY", 0),
                    number(properties, prefix + "socket.positionZ", 0),
                    number(properties, prefix + "socket.rotationX", 0),
                    number(properties, prefix + "socket.rotationY", 0),
                    number(properties, prefix + "socket.rotationZ", 0),
                    number(properties, prefix + "socket.scale", 1),
                    0, 0, 1, false);
            ItemProfile profile = new ItemProfile(
                    itemId,
                    properties.getProperty(prefix + "rigId", ""),
                    enumValue(WieldHand.class, properties.getProperty(prefix + "wieldHand", ""), WieldHand.RIGHT),
                    properties.getProperty(prefix + "animationSetId", ""),
                    socket,
                    number(properties, prefix + "secondaryGripX", 0),
                    number(properties, prefix + "secondaryGripY", 0),
                    number(properties, prefix + "secondaryGripZ", 0),
                    properties.getProperty(prefix + "leftArmorPath", ""),
                    properties.getProperty(prefix + "rightArmorPath", ""),
                    enumValue(ArmCoverage.class, properties.getProperty(prefix + "leftCoverage", ""), ArmCoverage.OVERLAY),
                    enumValue(ArmCoverage.class, properties.getProperty(prefix + "rightCoverage", ""), ArmCoverage.OVERLAY),
                    properties.getProperty(prefix + "attachmentBone", ""),
                    readBindings(properties, prefix + "override."));
            profiles.put(itemId, profile);
        }

        EnumMap<WeaponType, String> defaults = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            if (type == WeaponType.NONE) continue;
            defaults.put(type, normalizeId(properties.getProperty(
                    "weaponDefault." + type.name(), defaultSetId(type))));
        }
        return new Content(properties.getProperty("defaultRigId", ""), rigs, sets, profiles, defaults);
    }

    private static Properties write(Content content) {
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", String.valueOf(SCHEMA_VERSION));
        properties.setProperty("defaultRigId", content.defaultRigId());
        properties.setProperty("rig.count", String.valueOf(content.rigs().size()));
        int rigIndex = 0;
        for (RigDefinition rig : content.rigs().values()) {
            String prefix = "rig." + rigIndex++ + ".";
            properties.setProperty(prefix + "id", rig.rigId());
            properties.setProperty(prefix + "displayName", rig.displayName());
            properties.setProperty(prefix + "modelPath", rig.modelPath());
            properties.setProperty(prefix + "left.modelPath", rig.defaultLeftArmPath());
            properties.setProperty(prefix + "right.modelPath", rig.defaultRightArmPath());
            properties.setProperty(prefix + "left.shoulderBone", rig.leftShoulderBone());
            properties.setProperty(prefix + "left.elbowBone", rig.leftElbowBone());
            properties.setProperty(prefix + "left.handBone", rig.leftHandBone());
            properties.setProperty(prefix + "right.shoulderBone", rig.rightShoulderBone());
            properties.setProperty(prefix + "right.elbowBone", rig.rightElbowBone());
            properties.setProperty(prefix + "right.handBone", rig.rightHandBone());
            properties.setProperty(prefix + "cameraAnchorBone", rig.cameraAnchorBone());
            properties.setProperty(prefix + "left.visibleMeshes", String.join(";", rig.leftVisibleMeshes()));
            properties.setProperty(prefix + "right.visibleMeshes", String.join(";", rig.rightVisibleMeshes()));
            setNumber(properties, prefix + "positionX", rig.positionX());
            setNumber(properties, prefix + "positionY", rig.positionY());
            setNumber(properties, prefix + "positionZ", rig.positionZ());
            setNumber(properties, prefix + "rotationX", rig.rotationX());
            setNumber(properties, prefix + "rotationY", rig.rotationY());
            setNumber(properties, prefix + "rotationZ", rig.rotationZ());
            setNumber(properties, prefix + "scale", rig.scale());
            setNumber(properties, prefix + "fieldOfViewDegrees", rig.fieldOfViewDegrees());
            setNumber(properties, prefix + "nearPlane", rig.nearPlane());
            properties.setProperty(prefix + "crossfadeMs", String.valueOf(rig.crossfadeMs()));
            writeBindings(properties, prefix + "fallback.", rig.fallbackBindings());
        }

        properties.setProperty("animationSet.count", String.valueOf(content.animationSets().size()));
        int setIndex = 0;
        for (AnimationSet set : content.animationSets().values()) {
            String prefix = "animationSet." + setIndex++ + ".";
            properties.setProperty(prefix + "id", set.id());
            properties.setProperty(prefix + "displayName", set.displayName());
            properties.setProperty(prefix + "rigId", set.rigId());
            writeBindings(properties, prefix + "binding.", set.bindings());
        }

        properties.setProperty("itemProfile.count", String.valueOf(content.itemProfiles().size()));
        int profileIndex = 0;
        for (ItemProfile profile : content.itemProfiles().values()) {
            String prefix = "itemProfile." + profileIndex++ + ".";
            properties.setProperty(prefix + "itemId", profile.itemId());
            properties.setProperty(prefix + "rigId", profile.rigId());
            properties.setProperty(prefix + "wieldHand", profile.wieldHand().name());
            properties.setProperty(prefix + "animationSetId", profile.animationSetId());
            EquipmentViewModelProfile socket = profile.socketTransform();
            setNumber(properties, prefix + "socket.positionX", socket.positionX());
            setNumber(properties, prefix + "socket.positionY", socket.positionY());
            setNumber(properties, prefix + "socket.positionZ", socket.positionZ());
            setNumber(properties, prefix + "socket.rotationX", socket.rotationX());
            setNumber(properties, prefix + "socket.rotationY", socket.rotationY());
            setNumber(properties, prefix + "socket.rotationZ", socket.rotationZ());
            setNumber(properties, prefix + "socket.scale", socket.normalizedHeight());
            setNumber(properties, prefix + "secondaryGripX", profile.secondaryGripX());
            setNumber(properties, prefix + "secondaryGripY", profile.secondaryGripY());
            setNumber(properties, prefix + "secondaryGripZ", profile.secondaryGripZ());
            properties.setProperty(prefix + "leftArmorPath", profile.leftArmorPath());
            properties.setProperty(prefix + "rightArmorPath", profile.rightArmorPath());
            properties.setProperty(prefix + "leftCoverage", profile.leftCoverage().name());
            properties.setProperty(prefix + "rightCoverage", profile.rightCoverage().name());
            properties.setProperty(prefix + "attachmentBone", profile.attachmentBone());
            writeBindings(properties, prefix + "override.", profile.overrides());
        }
        content.weaponDefaults().forEach((type, id) ->
                properties.setProperty("weaponDefault." + type.name(), id));
        return properties;
    }

    private static EnumMap<AnimationSlot, ClipBinding> readBindings(Properties properties, String prefix) {
        EnumMap<AnimationSlot, ClipBinding> result = new EnumMap<>(AnimationSlot.class);
        for (AnimationSlot slot : AnimationSlot.values()) {
            String slotPrefix = prefix + slot.name() + ".";
            ClipBinding binding = new ClipBinding(
                    properties.getProperty(slotPrefix + "path", ""),
                    properties.getProperty(slotPrefix + "clipName", ""),
                    number(properties, slotPrefix + "speed", 1),
                    number(properties, slotPrefix + "impactFraction", 0.55),
                    new CameraFraming(
                            number(properties, slotPrefix + "camera.positionX", 0),
                            number(properties, slotPrefix + "camera.positionY", 0),
                            number(properties, slotPrefix + "camera.positionZ", 0),
                            number(properties, slotPrefix + "camera.rotationX", 0),
                            number(properties, slotPrefix + "camera.rotationY", 0),
                            number(properties, slotPrefix + "camera.rotationZ", 0)));
            if (binding.present()) result.put(slot, binding);
        }
        return result;
    }

    private static void writeBindings(
            Properties properties,
            String prefix,
            Map<AnimationSlot, ClipBinding> bindings
    ) {
        if (bindings == null) return;
        bindings.forEach((slot, binding) -> {
            if (slot == null || binding == null || !binding.present()) return;
            String slotPrefix = prefix + slot.name() + ".";
            properties.setProperty(slotPrefix + "path", binding.path());
            properties.setProperty(slotPrefix + "clipName", binding.clipName());
            setNumber(properties, slotPrefix + "speed", binding.playbackSpeed());
            setNumber(properties, slotPrefix + "impactFraction", binding.impactFraction());
            CameraFraming camera = binding.cameraFraming();
            setNumber(properties, slotPrefix + "camera.positionX", camera.positionX());
            setNumber(properties, slotPrefix + "camera.positionY", camera.positionY());
            setNumber(properties, slotPrefix + "camera.positionZ", camera.positionZ());
            setNumber(properties, slotPrefix + "camera.rotationX", camera.rotationX());
            setNumber(properties, slotPrefix + "camera.rotationY", camera.rotationY());
            setNumber(properties, slotPrefix + "camera.rotationZ", camera.rotationZ());
        });
    }

    private static Properties loadProperties(String path) {
        Properties properties = new Properties();
        try (InputStream input = AssetLoader.openAssetStream(path)) {
            properties.load(input);
        } catch (IOException ignored) {
            // Empty catalog is a valid authored state.
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

    private static void setNumber(Properties properties, String key, double value) {
        properties.setProperty(key, formatNumber(value));
    }

    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.rint(value) == value) return String.valueOf((long) value);
        return String.format(Locale.US, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, safe(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static <T> Map<String, T> immutableByKey(Map<String, T> source) {
        LinkedHashMap<String, T> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((id, value) -> {
            String key = normalizeId(id);
            if (!key.isBlank() && value != null) copy.put(key, value);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<AnimationSlot, ClipBinding> immutableBindings(
            Map<AnimationSlot, ClipBinding> source
    ) {
        EnumMap<AnimationSlot, ClipBinding> copy = new EnumMap<>(AnimationSlot.class);
        if (source != null) source.forEach((slot, binding) -> {
            if (slot != null && binding != null && binding.present()) copy.put(slot, binding);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableNames(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.stream().map(FirstPersonCombatLibrary::safe)
                .filter(value -> !value.isBlank()).forEach(result::add);
        return java.util.Collections.unmodifiableSet(result);
    }

    private static Set<String> splitNames(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value != null) {
            for (String part : value.split(";")) {
                if (!part.isBlank()) result.add(part.trim());
            }
        }
        return result;
    }

    public static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
            double impactFraction,
            CameraFraming cameraFraming
    ) {
        public ClipBinding {
            path = normalizePath(path);
            clipName = safe(clipName);
            playbackSpeed = finitePositive(playbackSpeed, 1.0);
            impactFraction = clamp(impactFraction, 0.0, 1.0, 0.55);
            cameraFraming = cameraFraming == null ? CameraFraming.identity() : cameraFraming;
        }

        public ClipBinding(
                String path,
                String clipName,
                double playbackSpeed,
                double impactFraction
        ) {
            this(path, clipName, playbackSpeed, impactFraction, CameraFraming.identity());
        }

        public static ClipBinding empty() {
            return new ClipBinding("", "", 1.0, 0.55);
        }

        public boolean present() {
            return !path.isBlank();
        }
    }

    /**
     * Per-animation adjustment applied on top of the rig's baseline camera framing.
     */
    public record CameraFraming(
            double positionX,
            double positionY,
            double positionZ,
            double rotationX,
            double rotationY,
            double rotationZ
    ) {
        public CameraFraming {
            positionX = finite(positionX, 0.0);
            positionY = finite(positionY, 0.0);
            positionZ = finite(positionZ, 0.0);
            rotationX = finite(rotationX, 0.0);
            rotationY = finite(rotationY, 0.0);
            rotationZ = finite(rotationZ, 0.0);
        }

        public static CameraFraming identity() {
            return new CameraFraming(0, 0, 0, 0, 0, 0);
        }
    }

    public record RigDefinition(
            String rigId,
            String displayName,
            String modelPath,
            String defaultLeftArmPath,
            String defaultRightArmPath,
            String leftShoulderBone,
            String leftElbowBone,
            String leftHandBone,
            String rightShoulderBone,
            String rightElbowBone,
            String rightHandBone,
            String cameraAnchorBone,
            Set<String> leftVisibleMeshes,
            Set<String> rightVisibleMeshes,
            double positionX,
            double positionY,
            double positionZ,
            double rotationX,
            double rotationY,
            double rotationZ,
            double scale,
            double fieldOfViewDegrees,
            double nearPlane,
            int crossfadeMs,
            Map<AnimationSlot, ClipBinding> fallbackBindings
    ) {
        public RigDefinition {
            rigId = normalizeId(rigId);
            displayName = displayName == null || displayName.isBlank() ? rigId : displayName.trim();
            modelPath = normalizePath(modelPath);
            defaultLeftArmPath = normalizePath(defaultLeftArmPath);
            defaultRightArmPath = normalizePath(defaultRightArmPath);
            leftShoulderBone = safe(leftShoulderBone);
            leftElbowBone = safe(leftElbowBone);
            leftHandBone = blankDefault(leftHandBone, "Hand.L");
            rightShoulderBone = safe(rightShoulderBone);
            rightElbowBone = safe(rightElbowBone);
            rightHandBone = blankDefault(rightHandBone, "Hand.R");
            cameraAnchorBone = safe(cameraAnchorBone);
            leftVisibleMeshes = immutableNames(leftVisibleMeshes);
            rightVisibleMeshes = immutableNames(rightVisibleMeshes);
            positionX = finite(positionX, 0.0);
            positionY = finite(positionY, 0.0);
            positionZ = finite(positionZ, -0.75);
            rotationX = finite(rotationX, 0.0);
            rotationY = finite(rotationY, 0.0);
            rotationZ = finite(rotationZ, 0.0);
            scale = finitePositive(scale, 1.0);
            fieldOfViewDegrees = clamp(fieldOfViewDegrees, 30.0, 120.0, 70.0);
            nearPlane = clamp(nearPlane, 0.001, 1.0, 0.05);
            crossfadeMs = Math.max(0, Math.min(2000, crossfadeMs));
            fallbackBindings = immutableBindings(fallbackBindings);
        }

        public boolean configured() {
            return !rigId.isBlank() && !modelPath.isBlank();
        }

        public String handBone(WieldHand hand) {
            return hand == WieldHand.LEFT ? leftHandBone : rightHandBone;
        }

        public String shoulderBone(WieldHand hand) {
            return hand == WieldHand.LEFT ? leftShoulderBone : rightShoulderBone;
        }

        public String elbowBone(WieldHand hand) {
            return hand == WieldHand.LEFT ? leftElbowBone : rightElbowBone;
        }

        public String armPath(WieldHand hand) {
            return hand == WieldHand.LEFT ? defaultLeftArmPath : defaultRightArmPath;
        }

        public Set<String> visibleMeshes(WieldHand hand) {
            return hand == WieldHand.LEFT ? leftVisibleMeshes : rightVisibleMeshes;
        }

        public ClipBinding fallback(AnimationSlot slot) {
            return fallbackBindings.get(slot);
        }
    }

    public record AnimationSet(
            String id,
            String displayName,
            String rigId,
            Map<AnimationSlot, ClipBinding> bindings
    ) {
        public AnimationSet {
            id = normalizeId(id);
            displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
            rigId = normalizeId(rigId);
            bindings = immutableBindings(bindings);
        }

        public ClipBinding binding(AnimationSlot slot) {
            return bindings.get(slot);
        }
    }

    public record ItemProfile(
            String itemId,
            String rigId,
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
            String attachmentBone,
            Map<AnimationSlot, ClipBinding> overrides
    ) {
        public ItemProfile {
            itemId = normalizeId(itemId);
            rigId = normalizeId(rigId);
            wieldHand = wieldHand == null ? WieldHand.RIGHT : wieldHand;
            animationSetId = normalizeId(animationSetId);
            socketTransform = socketTransform == null ? socketDefaults() : socketTransform;
            secondaryGripX = finite(secondaryGripX, 0.0);
            secondaryGripY = finite(secondaryGripY, 0.0);
            secondaryGripZ = finite(secondaryGripZ, 0.0);
            leftArmorPath = normalizePath(leftArmorPath);
            rightArmorPath = normalizePath(rightArmorPath);
            leftCoverage = leftCoverage == null ? ArmCoverage.OVERLAY : leftCoverage;
            rightCoverage = rightCoverage == null ? ArmCoverage.OVERLAY : rightCoverage;
            attachmentBone = attachmentBone == null ? "" : attachmentBone.trim();
            overrides = immutableBindings(overrides);
        }

        /** Schema-2/source-compatible constructor. Blank attachment inherits the selected rig hand. */
        public ItemProfile(
                String itemId,
                String rigId,
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
            this(itemId, rigId, wieldHand, animationSetId, socketTransform,
                    secondaryGripX, secondaryGripY, secondaryGripZ,
                    leftArmorPath, rightArmorPath, leftCoverage, rightCoverage, "", overrides);
        }

        public ItemProfile(
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
            this(itemId, "", wieldHand, animationSetId, socketTransform,
                    secondaryGripX, secondaryGripY, secondaryGripZ,
                    leftArmorPath, rightArmorPath, leftCoverage, rightCoverage, "", overrides);
        }

        public static EquipmentViewModelProfile socketDefaults() {
            return new EquipmentViewModelProfile(
                    0, 0, 0, 0, 0, 0, 1,
                    0, 0, 1, false);
        }

        public ClipBinding override(AnimationSlot slot) {
            return overrides.get(slot);
        }
    }

    public record Content(
            String defaultRigId,
            Map<String, RigDefinition> rigs,
            Map<String, AnimationSet> animationSets,
            Map<String, ItemProfile> itemProfiles,
            Map<WeaponType, String> weaponDefaults
    ) {
        public Content {
            defaultRigId = normalizeId(defaultRigId);
            rigs = immutableByKey(rigs);
            animationSets = immutableByKey(animationSets);
            itemProfiles = immutableByKey(itemProfiles);
            EnumMap<WeaponType, String> defaults = new EnumMap<>(WeaponType.class);
            if (weaponDefaults != null) {
                weaponDefaults.forEach((type, id) -> {
                    if (type != null && type != WeaponType.NONE) {
                        defaults.put(type, normalizeId(id));
                    }
                });
            }
            weaponDefaults = Map.copyOf(defaults);
            if (defaultRigId.isBlank() && !rigs.isEmpty()) {
                defaultRigId = rigs.keySet().iterator().next();
            }
        }

        public RigDefinition rig() {
            return rig(defaultRigId);
        }

        public RigDefinition rig(String id) {
            RigDefinition selected = rigs.get(normalizeId(id));
            if (selected != null) return selected;
            selected = rigs.get(defaultRigId);
            return selected == null ? emptyRig() : selected;
        }

        public RigDefinition rigFor(ItemProfile profile) {
            return profile == null || profile.rigId().isBlank()
                    ? rig()
                    : rig(profile.rigId());
        }

        /** Resolves the authored parent node without consulting a loaded model. */
        public String resolveAttachmentBone(ItemProfile profile) {
            RigDefinition selectedRig = rigFor(profile);
            if (profile == null) return selectedRig.handBone(WieldHand.RIGHT);
            return profile.attachmentBone().isBlank()
                    ? selectedRig.handBone(profile.wieldHand())
                    : profile.attachmentBone();
        }

        public ItemProfile itemProfile(InventorySystem.Item item) {
            if (item == null) return null;
            String id = normalizeId(item.getContentId());
            if (!id.isBlank() && itemProfiles.containsKey(id)) return itemProfiles.get(id);
            return itemProfiles.get(normalizeId(item.getName()));
        }

        public AnimationSet animationSet(
                WeaponType weaponType,
                ItemProfile profile
        ) {
            String id = profile == null ? "" : profile.animationSetId();
            if (id.isBlank()) {
                id = weaponType == null || weaponType == WeaponType.NONE
                        ? "unarmed"
                        : weaponDefaults.getOrDefault(weaponType, defaultSetId(weaponType));
            }
            return animationSets.get(normalizeId(id));
        }

        public AnimationSet animationSet(InventorySystem.Item weapon, ItemProfile profile) {
            return animationSet(weapon == null ? WeaponType.NONE : weapon.getWeaponType(), profile);
        }

        public ClipBinding resolveBinding(
                WeaponType weaponType,
                ItemProfile profile,
                AnimationSlot slot
        ) {
            return resolveBinding(weaponType, profile, rigFor(profile), slot);
        }

        public ClipBinding resolveBinding(
                WeaponType weaponType,
                ItemProfile profile,
                RigDefinition resolvedRig,
                AnimationSlot slot
        ) {
            if (profile != null) {
                ClipBinding override = profile.override(slot);
                if (override != null && override.present()) return override;
            }
            AnimationSet set = animationSet(weaponType, profile);
            RigDefinition rig = resolvedRig == null ? rigFor(profile) : resolvedRig;
            if (set != null && (set.rigId().isBlank() || set.rigId().equals(rig.rigId()))) {
                ClipBinding binding = set.binding(slot);
                if (binding != null && binding.present()) return binding;
            }
            ClipBinding fallback = rig.fallback(slot);
            return fallback != null && fallback.present() ? fallback : null;
        }

        public ClipBinding resolveBinding(
                InventorySystem.Item weapon,
                ItemProfile profile,
                AnimationSlot slot
        ) {
            return resolveBinding(weapon == null ? WeaponType.NONE : weapon.getWeaponType(), profile, slot);
        }

        public Content withRig(RigDefinition rig) {
            LinkedHashMap<String, RigDefinition> copy = new LinkedHashMap<>(rigs);
            copy.put(rig.rigId(), rig);
            return new Content(defaultRigId.isBlank() ? rig.rigId() : defaultRigId,
                    copy, animationSets, itemProfiles, weaponDefaults);
        }

        public Content withAnimationSet(AnimationSet set) {
            LinkedHashMap<String, AnimationSet> copy = new LinkedHashMap<>(animationSets);
            copy.put(set.id(), set);
            return new Content(defaultRigId, rigs, copy, itemProfiles, weaponDefaults);
        }

        public Content withItemProfile(ItemProfile profile) {
            LinkedHashMap<String, ItemProfile> copy = new LinkedHashMap<>(itemProfiles);
            copy.put(profile.itemId(), profile);
            return new Content(defaultRigId, rigs, animationSets, copy, weaponDefaults);
        }

        public Content withoutItemProfile(String itemId) {
            LinkedHashMap<String, ItemProfile> copy = new LinkedHashMap<>(itemProfiles);
            copy.remove(normalizeId(itemId));
            return new Content(defaultRigId, rigs, animationSets, copy, weaponDefaults);
        }
    }

    public record Diagnostic(Severity severity, String ownerId, String message) {
        public Diagnostic {
            severity = severity == null ? Severity.ERROR : severity;
            ownerId = safe(ownerId);
            message = safe(message);
        }

        public enum Severity {ERROR, WARNING, INFO}
    }
}

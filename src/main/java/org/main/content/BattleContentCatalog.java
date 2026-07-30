package org.main.content;

import org.main.battle.BattleSkill;
import org.main.core.Library;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Versioned global repository for authored combat skills and statuses.
 */
public final class BattleContentCatalog {
    public static final int SCHEMA_VERSION = 1;
    public static final Path CONTENT_FOLDER = Path.of(
            "src", "main", "resources", "assets", "editor", "content");
    public static final Path SKILL_PATH = CONTENT_FOLDER.resolve("skill.properties");
    public static final Path STATUS_PATH = CONTENT_FOLDER.resolve("status.properties");
    private static final String SKILL_RESOURCE = "assets/editor/content/skill.properties";
    private static final String STATUS_RESOURCE = "assets/editor/content/status.properties";

    private static volatile Snapshot current;

    private BattleContentCatalog() {
    }

    public static Snapshot current() {
        Snapshot snapshot = current;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (BattleContentCatalog.class) {
            if (current == null) {
                current = loadQuietly();
            }
            return current;
        }
    }

    public static synchronized Snapshot reload() throws IOException {
        current = load();
        return current;
    }

    public static synchronized void installForTests(Snapshot snapshot) {
        current = snapshot == null ? defaults() : snapshot;
    }

    public static SkillDefinition findSkill(String id) {
        return current().skills().get(normalizeId(id));
    }

    public static BattleSkill createSkill(String id) {
        String normalized = normalizeId(id);
        SkillDefinition definition = findSkill(normalized);
        if (definition != null) {
            return BattleSkill.fromDefinition(definition);
        }
        return BattleSkill.fromDefinition(new SkillDefinition(
                normalized,
                "Missing Skill [" + normalized + "]",
                "Unresolved authored battle-skill reference.",
                Library.SkillTargetShape.SINGLE_TARGET,
                Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC,
                "",
                "UTILITY",
                0,
                true,
                List.of(new SkillEffectDefinition(
                        "no_op",
                        SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                        SkillEffectDefinition.ActivationCondition.ALWAYS,
                        1.0,
                        Map.of()))));
    }

    public static List<BattleSkill> createSkills(Collection<String> ids) {
        return ids == null ? List.of() : ids.stream().map(BattleContentCatalog::createSkill).toList();
    }

    public static List<BattleSkill> defaultPlayerSkills() {
        return createSkills(current().defaultPlayerSkillIds());
    }

    public static List<BattleSkill> universalPlayerSkills() {
        return createSkills(current().universalPlayerSkillIds());
    }

    public static List<BattleSkill> debugPlayerSkills() {
        return createSkills(current().debugPlayerSkillIds());
    }

    public static StatusDefinition findStatus(String id) {
        return current().statuses().get(normalizeId(id));
    }

    public static Snapshot load() throws IOException {
        Map<String, StatusDefinition> statuses = loadStatuses();
        SkillLoadResult skillResult = loadSkills();
        Snapshot loaded = new Snapshot(
                SCHEMA_VERSION,
                skillResult.skills(),
                statuses,
                skillResult.defaultPlayerSkillIds(),
                skillResult.universalPlayerSkillIds(),
                skillResult.debugPlayerSkillIds());
        return loaded;
    }

    public static synchronized void save(Snapshot snapshot) throws IOException {
        List<String> errors = validate(snapshot);
        if (!errors.isEmpty()) {
            throw new IOException("Battle content was not saved: " + String.join(" ", errors));
        }
        Files.createDirectories(CONTENT_FOLDER);
        boolean hadSkillFile = Files.isRegularFile(SKILL_PATH);
        boolean hadStatusFile = Files.isRegularFile(STATUS_PATH);
        byte[] originalSkills = hadSkillFile ? Files.readAllBytes(SKILL_PATH) : null;
        byte[] originalStatuses = hadStatusFile ? Files.readAllBytes(STATUS_PATH) : null;

        Path skillTemp = CONTENT_FOLDER.resolve("skill.properties.tmp");
        Path statusTemp = CONTENT_FOLDER.resolve("status.properties.tmp");
        try {
            writeSkills(snapshot, skillTemp);
            writeStatuses(snapshot, statusTemp);
            moveAtomically(statusTemp, STATUS_PATH);
            moveAtomically(skillTemp, SKILL_PATH);
            current = snapshot;
        } catch (IOException failure) {
            restoreAfterFailedTransaction(STATUS_PATH, originalStatuses, hadStatusFile);
            restoreAfterFailedTransaction(SKILL_PATH, originalSkills, hadSkillFile);
            throw failure;
        } finally {
            Files.deleteIfExists(skillTemp);
            Files.deleteIfExists(statusTemp);
        }
    }

    private static void restoreAfterFailedTransaction(
            Path target, byte[] originalContents, boolean existedBefore) throws IOException {
        if (existedBefore) {
            if (originalContents == null) {
                throw new IOException("Missing original catalog content for " + target + ".");
            }
            Files.write(target, originalContents);
        } else if (!existedBefore) {
            Files.deleteIfExists(target);
        }
    }

    public static List<String> validate(Snapshot snapshot) {
        List<String> issues = new ArrayList<>();
        if (snapshot == null) {
            return List.of("Battle content snapshot is missing.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (SkillDefinition skill : snapshot.skills().values()) {
            if (skill.id().isBlank()) {
                issues.add("A skill is missing its id.");
            } else if (!ids.add(skill.id())) {
                issues.add("Duplicate skill id " + skill.id() + ".");
            }
            if (skill.effects().isEmpty()) {
                issues.add("Skill " + skill.id() + " has no effects.");
            }
            for (SkillEffectDefinition effect : skill.effects()) {
                for (String issue : BattleContentTypeRegistry.validateDefinition(effect)) {
                    if (!issue.startsWith("Unknown effect kind")) {
                        issues.add("Skill " + skill.id() + ": " + issue);
                    }
                }
                if (("apply_status".equals(effect.kindId()) || "remove_status".equals(effect.kindId()))
                        && !effect.parameter("statusId", "").isBlank()
                        && !snapshot.statuses().containsKey(normalizeId(effect.parameter("statusId", "")))) {
                    issues.add("Skill " + skill.id() + " references unknown status "
                            + effect.parameter("statusId", "") + ".");
                }
            }
        }
        ids.clear();
        for (StatusDefinition status : snapshot.statuses().values()) {
            if (status.id().isBlank()) {
                issues.add("A status is missing its id.");
            } else if (!ids.add(status.id())) {
                issues.add("Duplicate status id " + status.id() + ".");
            }
            for (String issue : BattleContentTypeRegistry.validateDefinition(status)) {
                if (!issue.startsWith("Unknown status behavior")) {
                    issues.add("Status " + status.id() + ": " + issue);
                }
            }
        }
        validatePool("default", snapshot.defaultPlayerSkillIds(), snapshot.skills(), issues);
        validatePool("universal", snapshot.universalPlayerSkillIds(), snapshot.skills(), issues);
        validatePool("debug", snapshot.debugPlayerSkillIds(), snapshot.skills(), issues);
        return List.copyOf(issues);
    }

    public static Snapshot defaults() {
        LinkedHashMap<String, StatusDefinition> statuses = new LinkedHashMap<>();
        put(statuses, new StatusDefinition(
                "stun", "Stun", "Cannot act while stunned.",
                "assets/images/ui/01_UI_Resources/A1ICON/icon_ban.png",
                StatusDefinition.Polarity.HARMFUL, "action_lock", 1,
                StatusDefinition.StackingPolicy.REFRESH, 1, Map.of("magnitude", "0")));
        put(statuses, new StatusDefinition(
                "rotting_grasp", "Rotting", "Agility is reduced by rotting flesh.",
                "assets/images/ui/01_UI_Resources/A1ICON/Weakness.png",
                StatusDefinition.Polarity.HARMFUL, "stat_modifier", 2,
                StatusDefinition.StackingPolicy.REFRESH, 1,
                Map.of("stat", "AGILITY", "magnitude", "-2")));
        put(statuses, new StatusDefinition(
                "guard", "Guarded", "Incoming damage is reduced.",
                "assets/images/ui/01_UI_Resources/A1ICON/Shield.png",
                StatusDefinition.Polarity.BENEFICIAL, "incoming_damage_modifier", 2,
                StatusDefinition.StackingPolicy.REFRESH, 1, Map.of("percent", "-50")));

        LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>();
        put(skills, skill("wait", "Skip Turn", "Debug: ends your turn without doing anything.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "", 0, "UTILITY",
                effect("no_op", Map.of())));
        put(skills, skill("debug_drop_hp", "Drop HP to 10%",
                "Debug: drops the player to 10% HP to test the warning loop.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "", 0, "DEBUG",
                new SkillEffectDefinition("set_hp_percent",
                        SkillEffectDefinition.RecipientScope.CASTER,
                        SkillEffectDefinition.ActivationCondition.ALWAYS, 1.0,
                        Map.of("percent", "10"))));
        put(skills, skill("fireball", "Fireball", "Hits every enemy.",
                Library.SkillTargetShape.ENTIRE_SIDE, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.MAGIC, "assets/sounds/generated/thrown_fireball.wav",
                8, "SPELL", damage(5)));
        put(skills, skill("piercing_line", "Piercing Line", "Hits one horizontal lane.",
                Library.SkillTargetShape.SINGLE_ROW, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.RANGED, "", 7, "RANGED", damage(5)));
        put(skills, skill("crush_column", "Crush Column", "Hits either the front or back column.",
                Library.SkillTargetShape.SINGLE_COLUMN, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.MAGIC, "", 7, "SPELL", damage(5)));
        put(skills, skill("heal", "Heal", "Targets one ally.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "assets/sounds/generated/fire_wave.wav",
                10, "HEAL", effect("heal", Map.of("potency", "5"))));
        put(skills, skill("defend", "Defend", "Reduces incoming damage for 2 turns.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "", 6, "DEFEND",
                effect("apply_status", Map.of("statusId", "guard", "duration", "2", "magnitude", "50"))));
        put(skills, skill("bash", "Bash", "Deals damage and has a chance to stun.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.NORMAL_MELEE, "", 6, "PHYSICAL_SKILL",
                damage(4),
                new SkillEffectDefinition("apply_status",
                        SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                        SkillEffectDefinition.ActivationCondition.PREVIOUS_EFFECT_HIT,
                        0.45, Map.of("statusId", "stun", "duration", "1", "magnitude", "0"))));
        put(skills, skill("absorb", "Absorb", "Deals small damage and heals the user.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.MAGIC, "", 8, "SPELL",
                damage(2),
                new SkillEffectDefinition("heal_from_damage",
                        SkillEffectDefinition.RecipientScope.CASTER,
                        SkillEffectDefinition.ActivationCondition.ANY_SKILL_DAMAGE_DEALT,
                        1.0, Map.of("percent", "50"))));
        put(skills, skill("rotting_grasp", "Rotting Grasp",
                "Deals damage and slows the target with rotting flesh.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.NORMAL_MELEE, "", 8, "PHYSICAL_SKILL",
                damage(3),
                new SkillEffectDefinition("apply_status",
                        SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                        SkillEffectDefinition.ActivationCondition.PREVIOUS_EFFECT_HIT,
                        0.75, Map.of("statusId", "rotting_grasp", "duration", "2", "magnitude", "2"))));
        put(skills, skill("war_cry", "War Cry",
                "Attempts to call another creature of the same kind.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "", 150, "SUMMON",
                effect("summon", Map.of("mode", "SAME_SPECIES", "successPercent", "65"))));
        put(skills, skill("raise_skeleton", "Raise Skeleton",
                "Attempts to call a skeleton into the battle.",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ALLY,
                Library.BattleTargetingMode.MAGIC, "", 20, "SUMMON",
                effect("summon", Map.of("mode", "SKELETON", "successPercent", "75"))));

        return new Snapshot(
                SCHEMA_VERSION, skills, statuses,
                List.of("wait", "fireball", "piercing_line", "crush_column", "heal"),
                List.of(),
                List.of("debug_drop_hp", "wait"));
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static Snapshot loadQuietly() {
        try {
            return load();
        } catch (IOException exception) {
            System.err.println("Battle content load warning: " + exception.getMessage());
            return defaults();
        }
    }

    private static Map<String, StatusDefinition> loadStatuses() throws IOException {
        Properties properties = loadProperties(STATUS_PATH, STATUS_RESOURCE);
        if (properties == null) {
            return defaults().statuses();
        }
        int count = integer(properties, "status.count", 0);
        LinkedHashMap<String, StatusDefinition> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "status." + index + ".";
            String id = normalizeId(properties.getProperty(prefix + "id", ""));
            Map<String, String> parameters = readParameters(properties, prefix);
            StatusDefinition definition = new StatusDefinition(
                    id,
                    properties.getProperty(prefix + "displayName", id),
                    properties.getProperty(prefix + "description", ""),
                    properties.getProperty(prefix + "iconPath", ""),
                    enumValue(StatusDefinition.Polarity.class,
                            properties.getProperty(prefix + "polarity"), StatusDefinition.Polarity.NEUTRAL),
                    properties.getProperty(prefix + "behaviorKind", ""),
                    integer(properties, prefix + "defaultDuration", 1),
                    enumValue(StatusDefinition.StackingPolicy.class,
                            properties.getProperty(prefix + "stackingPolicy"),
                            StatusDefinition.StackingPolicy.REFRESH),
                    integer(properties, prefix + "maxStacks", 1),
                    parameters);
            put(values, definition);
        }
        return values;
    }

    private static SkillLoadResult loadSkills() throws IOException {
        Properties properties = loadProperties(SKILL_PATH, SKILL_RESOURCE);
        if (properties == null) {
            Snapshot defaults = defaults();
            return new SkillLoadResult(
                    defaults.skills(),
                    defaults.defaultPlayerSkillIds(),
                    defaults.universalPlayerSkillIds(),
                    defaults.debugPlayerSkillIds());
        }
        int count = integer(properties, "skill.count", 0);
        LinkedHashMap<String, SkillDefinition> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "skill." + index + ".";
            String id = normalizeId(properties.getProperty(prefix + "id", ""));
            int effectCount = integer(properties, prefix + "effect.count", 0);
            List<SkillEffectDefinition> effects = new ArrayList<>();
            for (int effectIndex = 0; effectIndex < effectCount; effectIndex++) {
                String effectPrefix = prefix + "effect." + effectIndex + ".";
                effects.add(new SkillEffectDefinition(
                        properties.getProperty(effectPrefix + "kind", ""),
                        enumValue(SkillEffectDefinition.RecipientScope.class,
                                properties.getProperty(effectPrefix + "recipient"),
                                SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS),
                        enumValue(SkillEffectDefinition.ActivationCondition.class,
                                properties.getProperty(effectPrefix + "condition"),
                                SkillEffectDefinition.ActivationCondition.ALWAYS),
                        decimal(properties, effectPrefix + "chance", 1.0),
                        readParameters(properties, effectPrefix)));
            }
            SkillDefinition definition = new SkillDefinition(
                    id,
                    properties.getProperty(prefix + "displayName", id),
                    properties.getProperty(prefix + "description", ""),
                    enumValue(Library.SkillTargetShape.class,
                            properties.getProperty(prefix + "targetShape"),
                            Library.SkillTargetShape.SINGLE_TARGET),
                    enumValue(Library.EntityType.class,
                            properties.getProperty(prefix + "targetTeam"), Library.EntityType.ENEMY),
                    enumValue(Library.BattleTargetingMode.class,
                            properties.getProperty(prefix + "targetingMode"),
                            Library.BattleTargetingMode.MAGIC),
                    properties.getProperty(prefix + "useSoundPath", ""),
                    properties.getProperty(prefix + "presentationStyle", "AUTO"),
                    decimal(properties, prefix + "cooldownSeconds", 0),
                    Boolean.parseBoolean(properties.getProperty(prefix + "consumesAutoAction", "true")),
                    effects);
            put(values, definition);
        }
        return new SkillLoadResult(
                values,
                readIds(properties.getProperty("pool.default", "")),
                readIds(properties.getProperty("pool.universal", "")),
                readIds(properties.getProperty("pool.debug", "")));
    }

    private static Properties loadProperties(Path editablePath, String resourcePath) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(editablePath)) {
            try (InputStream input = Files.newInputStream(editablePath)) {
                properties.load(input);
            }
            return properties;
        }
        try (InputStream input = BattleContentCatalog.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            properties.load(input);
            return properties;
        }
    }

    private static void writeSkills(Snapshot snapshot, Path path) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", String.valueOf(SCHEMA_VERSION));
        properties.setProperty("pool.default", joinIds(snapshot.defaultPlayerSkillIds()));
        properties.setProperty("pool.universal", joinIds(snapshot.universalPlayerSkillIds()));
        properties.setProperty("pool.debug", joinIds(snapshot.debugPlayerSkillIds()));
        List<SkillDefinition> skills = new ArrayList<>(snapshot.skills().values());
        skills.sort(Comparator.comparing(SkillDefinition::id));
        properties.setProperty("skill.count", String.valueOf(skills.size()));
        for (int index = 0; index < skills.size(); index++) {
            SkillDefinition skill = skills.get(index);
            String prefix = "skill." + index + ".";
            properties.setProperty(prefix + "id", skill.id());
            properties.setProperty(prefix + "displayName", skill.displayName());
            properties.setProperty(prefix + "description", skill.description());
            properties.setProperty(prefix + "targetShape", skill.targetShape().name());
            properties.setProperty(prefix + "targetTeam", skill.targetTeam().name());
            properties.setProperty(prefix + "targetingMode", skill.targetingMode().name());
            properties.setProperty(prefix + "useSoundPath", skill.useSoundPath());
            properties.setProperty(prefix + "presentationStyle", skill.presentationStyle());
            properties.setProperty(prefix + "cooldownSeconds", String.valueOf(skill.cooldownSeconds()));
            properties.setProperty(prefix + "consumesAutoAction", String.valueOf(skill.consumesAutoAction()));
            properties.setProperty(prefix + "effect.count", String.valueOf(skill.effects().size()));
            for (int effectIndex = 0; effectIndex < skill.effects().size(); effectIndex++) {
                SkillEffectDefinition effect = skill.effects().get(effectIndex);
                String effectPrefix = prefix + "effect." + effectIndex + ".";
                properties.setProperty(effectPrefix + "kind", effect.kindId());
                properties.setProperty(effectPrefix + "recipient", effect.recipientScope().name());
                properties.setProperty(effectPrefix + "condition", effect.condition().name());
                properties.setProperty(effectPrefix + "chance", String.valueOf(effect.chance()));
                writeParameters(properties, effectPrefix, effect.parameters());
            }
        }
        store(properties, path, "Aether data-driven battle skills");
    }

    private static void writeStatuses(Snapshot snapshot, Path path) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", String.valueOf(SCHEMA_VERSION));
        List<StatusDefinition> statuses = new ArrayList<>(snapshot.statuses().values());
        statuses.sort(Comparator.comparing(StatusDefinition::id));
        properties.setProperty("status.count", String.valueOf(statuses.size()));
        for (int index = 0; index < statuses.size(); index++) {
            StatusDefinition status = statuses.get(index);
            String prefix = "status." + index + ".";
            properties.setProperty(prefix + "id", status.id());
            properties.setProperty(prefix + "displayName", status.displayName());
            properties.setProperty(prefix + "description", status.description());
            properties.setProperty(prefix + "iconPath", status.iconPath());
            properties.setProperty(prefix + "polarity", status.polarity().name());
            properties.setProperty(prefix + "behaviorKind", status.behaviorKindId());
            properties.setProperty(prefix + "defaultDuration", String.valueOf(status.defaultDuration()));
            properties.setProperty(prefix + "stackingPolicy", status.stackingPolicy().name());
            properties.setProperty(prefix + "maxStacks", String.valueOf(status.maxStacks()));
            writeParameters(properties, prefix, status.parameters());
        }
        store(properties, path, "Aether data-driven battle statuses");
    }

    private static void store(Properties properties, Path path, String comment) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, comment);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, String> readParameters(Properties properties, String prefix) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String parameterPrefix = prefix + "param.";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(parameterPrefix)) {
                values.put(key.substring(parameterPrefix.length()), properties.getProperty(key, ""));
            }
        }
        return values;
    }

    private static void writeParameters(
            Properties properties, String prefix, Map<String, String> values) {
        values.forEach((key, value) -> properties.setProperty(prefix + "param." + key, value));
    }

    private static List<String> readIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            String id = normalizeId(part);
            if (!id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static String joinIds(Collection<String> values) {
        return String.join(",", values.stream().map(BattleContentCatalog::normalizeId).toList());
    }

    private static void validatePool(
            String name, List<String> values, Map<String, SkillDefinition> skills, List<String> issues) {
        Set<String> seen = new LinkedHashSet<>();
        for (String rawId : values) {
            String id = normalizeId(rawId);
            if (!skills.containsKey(id)) {
                issues.add("The " + name + " player pool references unknown skill " + rawId + ".");
            } else if (!seen.add(id)) {
                issues.add("The " + name + " player pool contains duplicate skill " + id + ".");
            }
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double decimal(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static SkillDefinition skill(
            String id,
            String name,
            String description,
            Library.SkillTargetShape shape,
            Library.EntityType team,
            Library.BattleTargetingMode mode,
            String sound,
            double cooldown,
            String presentation,
            SkillEffectDefinition... effects) {
        return new SkillDefinition(id, name, description, shape, team, mode, sound,
                presentation, cooldown, true, List.of(effects));
    }

    private static SkillEffectDefinition damage(int potency) {
        return effect("damage", Map.of("potency", String.valueOf(potency)));
    }

    private static SkillEffectDefinition effect(String kind, Map<String, String> parameters) {
        return new SkillEffectDefinition(
                kind,
                SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                SkillEffectDefinition.ActivationCondition.ALWAYS,
                1.0,
                parameters);
    }

    private static <T> void put(Map<String, T> target, T value) {
        String id = value instanceof SkillDefinition skill ? skill.id() : ((StatusDefinition) value).id();
        target.put(id, value);
    }

    public record Snapshot(
            int schemaVersion,
            Map<String, SkillDefinition> skills,
            Map<String, StatusDefinition> statuses,
            List<String> defaultPlayerSkillIds,
            List<String> universalPlayerSkillIds,
            List<String> debugPlayerSkillIds
    ) {
        public Snapshot {
            schemaVersion = Math.max(1, schemaVersion);
            skills = immutableLinkedMap(skills);
            statuses = immutableLinkedMap(statuses);
            defaultPlayerSkillIds = normalizedIds(defaultPlayerSkillIds);
            universalPlayerSkillIds = normalizedIds(universalPlayerSkillIds);
            debugPlayerSkillIds = normalizedIds(debugPlayerSkillIds);
        }

        public Snapshot withSkills(
                Collection<SkillDefinition> definitions,
                List<String> defaultPool,
                List<String> universalPool,
                List<String> debugPool) {
            LinkedHashMap<String, SkillDefinition> updated = new LinkedHashMap<>();
            definitions.forEach(value -> updated.put(value.id(), value));
            return new Snapshot(schemaVersion, updated, statuses, defaultPool, universalPool, debugPool);
        }

        public Snapshot withStatuses(Collection<StatusDefinition> definitions) {
            LinkedHashMap<String, StatusDefinition> updated = new LinkedHashMap<>();
            definitions.forEach(value -> updated.put(value.id(), value));
            return new Snapshot(schemaVersion, skills, updated,
                    defaultPlayerSkillIds, universalPlayerSkillIds, debugPlayerSkillIds);
        }

        private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
            return java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(source == null ? Map.of() : source));
        }

        private static List<String> normalizedIds(List<String> source) {
            return source == null ? List.of() : source.stream()
                    .map(BattleContentCatalog::normalizeId)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
    }

    private record SkillLoadResult(
            Map<String, SkillDefinition> skills,
            List<String> defaultPlayerSkillIds,
            List<String> universalPlayerSkillIds,
            List<String> debugPlayerSkillIds
    ) {
    }
}

package org.main.content;

import org.main.engine.AssetRepository;
import org.main.pack.ContentMount;
import org.main.pack.ContentPackManifest;
import org.main.pack.ContentPackRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Function;

/**
 * Immutable, indexed authored-content snapshot rebuilt only when the active mount revision changes.
 */
public final class ContentRepository {
    private static final List<String> AUXILIARY_CATALOGS = List.of(
            "skill.properties", "status.properties", "material.properties", "first_person_rig.properties");
    private static final ContentRepository SHARED = new ContentRepository(AssetRepository.shared().registry());

    private final ContentPackRegistry registry;
    private volatile ContentSnapshot snapshot;

    public ContentRepository(ContentPackRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public static ContentRepository shared() {
        return SHARED;
    }

    public ContentSnapshot snapshot() throws IOException {
        ContentSnapshot current = snapshot;
        long packRevision = registry.snapshot().revision();
        if (current != null && current.packRevision() == packRevision) {
            return current;
        }
        return rebuildIfStale();
    }

    public synchronized ContentSnapshot reload() throws IOException {
        registry.reload();
        snapshot = null;
        return rebuildSnapshot(registry.snapshot().revision());
    }

    private synchronized ContentSnapshot rebuildIfStale() throws IOException {
        long packRevision = registry.snapshot().revision();
        ContentSnapshot current = snapshot;
        if (current != null && current.packRevision() == packRevision) {
            return current;
        }
        return rebuildSnapshot(packRevision);
    }

    private ContentSnapshot rebuildSnapshot(long packRevision) throws IOException {
        ContentMount baseMount = baseMount();
        Accumulator accumulator = new Accumulator(loadFromMount(baseMount), baseMount.manifest().id());

        List<ContentMount> active = new ArrayList<>(registry.snapshot().activeHighestPriorityFirst());
        Collections.reverse(active);
        for (ContentMount mount : active) {
            if (mount == baseMount || mount.origin() == ContentMount.Origin.BUNDLED
                    || mount.origin() == ContentMount.Origin.DEVELOPMENT
                    || mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
                continue;
            }
            accumulator.merge(loadFromMount(mount), mount.manifest());
        }

        ContentSnapshot next = indexed(packRevision, accumulator.content(), loadAuxiliaryCatalogs(baseMount));
        snapshot = next;
        return next;
    }

    public synchronized ContentSnapshot saveAndReload(MapDesignLibrary.AuthoredContent desired) throws IOException {
        ContentMount baseMount = baseMount();
        ContentMount projectMount = registry.snapshot().activeHighestPriorityFirst().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.PROJECT)
                .findFirst().orElse(null);
        Accumulator lower = new Accumulator(loadFromMount(baseMount), baseMount.manifest().id());
        List<ContentMount> active = new ArrayList<>(registry.snapshot().activeHighestPriorityFirst());
        Collections.reverse(active);
        for (ContentMount mount : active) {
            if (mount == baseMount || mount == projectMount
                    || mount.origin() == ContentMount.Origin.BUNDLED
                    || mount.origin() == ContentMount.Origin.DEVELOPMENT
                    || mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
                continue;
            }
            lower.merge(loadFromMount(mount), mount.manifest());
        }
        MapDesignLibrary.AuthoredContent lowerContent = lower.content();
        MapDesignLibrary.AuthoredContent delta = difference(desired, lowerContent);
        validateProjectIds(delta, lowerContent, projectMount);
        ensureProjectOverrides(projectMount, delta, lower);
        MapDesignContentStore.saveSharedContent(delta);
        registry.reload();
        snapshot = null;
        return rebuildSnapshot(registry.snapshot().revision());
    }

    private static void validateProjectIds(
            MapDesignLibrary.AuthoredContent delta,
            MapDesignLibrary.AuthoredContent base,
            ContentMount projectMount
    ) throws IOException {
        if (projectMount == null) {
            return;
        }
        String prefix = projectMount.manifest().namespace() + "__";
        Set<TypedId> baseIds = new LinkedHashSet<>(typedIds(base));
        for (TypedId id : typedIds(delta)) {
            if (baseIds.contains(id)) {
                continue;
            }
            if (!id.id().startsWith(prefix) || id.id().length() == prefix.length()) {
                throw new IOException("New " + id.type() + " ID '" + id.id()
                        + "' must begin with " + prefix + ".");
            }
        }
    }

    private static void ensureProjectOverrides(
            ContentMount mount,
            MapDesignLibrary.AuthoredContent delta,
            Accumulator lower
    ) throws IOException {
        if (!(mount instanceof org.main.pack.DirectoryContentMount directory)) {
            return;
        }
        List<OverrideTarget> overrides = typedIds(delta).stream()
                .map(id -> new OverrideTarget(lower.origin(id), id))
                .filter(target -> !target.packId().isBlank())
                .toList();
        if (overrides.isEmpty()) {
            return;
        }
        Path path = directory.root().resolve(ContentPackManifest.MANIFEST_PATH);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        int dependencyCount = integer(properties, "dependency.count");
        Set<String> dependencies = new LinkedHashSet<>();
        for (int index = 0; index < dependencyCount; index++) {
            dependencies.add(key(properties.getProperty("dependency." + index + ".id", "")));
        }
        for (String targetPack : overrides.stream().map(OverrideTarget::packId).distinct().toList()) {
            if (!dependencies.add(targetPack)) {
                continue;
            }
            String prefix = "dependency." + dependencyCount++ + ".";
            properties.setProperty(prefix + "id", targetPack);
            properties.setProperty(prefix + "minVersion", "0.0.0");
            properties.setProperty(prefix + "maxVersionExclusive", "");
        }
        properties.setProperty("dependency.count", String.valueOf(dependencyCount));
        int overrideCount = integer(properties, "override.count");
        Set<OverrideTarget> declared = new LinkedHashSet<>();
        for (int index = 0; index < overrideCount; index++) {
            String prefix = "override." + index + ".";
            declared.add(new OverrideTarget(properties.getProperty(prefix + "targetPack", ""),
                    new TypedId(properties.getProperty(prefix + "type", ""),
                            properties.getProperty(prefix + "id", ""))));
        }
        for (OverrideTarget override : overrides) {
            if (!declared.add(override)) {
                continue;
            }
            String prefix = "override." + overrideCount++ + ".";
            properties.setProperty(prefix + "targetPack", override.packId());
            properties.setProperty(prefix + "type", override.id().type());
            properties.setProperty(prefix + "id", override.id().id());
        }
        properties.setProperty("override.count", String.valueOf(overrideCount));
        Path temporary = path.resolveSibling(path.getFileName() + ".new");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Aether Construction Kit content-pack manifest");
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int integer(Properties properties, String key) throws IOException {
        try {
            return Integer.parseInt(properties.getProperty(key, "0").trim());
        } catch (NumberFormatException error) {
            throw new IOException("Invalid project manifest count for " + key + ".", error);
        }
    }

    private static List<TypedId> typedIds(MapDesignLibrary.AuthoredContent content) {
        List<TypedId> result = new ArrayList<>();
        content.authoredDialogues().forEach(value -> result.add(new TypedId("dialogue", value.interactionId())));
        content.authoredQuests().forEach(value -> result.add(new TypedId("quest", value.questId())));
        content.customItems().forEach(value -> result.add(new TypedId("item", value.itemId())));
        content.customMobs().forEach(value -> result.add(new TypedId("mob", value.mobId())));
        content.customLimbs().forEach(value -> result.add(new TypedId("limb", value.limbId())));
        content.customNpcs().forEach(value -> result.add(new TypedId("npc", value.npcId())));
        content.customFurniture().forEach(value -> result.add(new TypedId("furniture", value.furnitureId())));
        content.customGatheringNodes().forEach(value -> result.add(new TypedId("gathering", value.nodeId())));
        content.customCookingRecipes().forEach(value -> result.add(new TypedId("cooking", value.recipeId())));
        content.craftingRecipes().forEach(value -> result.add(new TypedId("crafting", value.recipeId())));
        return List.copyOf(result);
    }

    private record TypedId(String type, String id) {
        private TypedId {
            type = key(type);
            id = key(id);
        }
    }

    private record OverrideTarget(String packId, TypedId id) {
        private OverrideTarget {
            packId = key(packId);
        }
    }

    public long revision() throws IOException {
        return snapshot().revision();
    }

    private ContentMount baseMount() throws IOException {
        return registry.snapshot().activeHighestPriorityFirst().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.DEVELOPMENT)
                .findFirst()
                .orElseGet(() -> registry.snapshot().available().get("aether.core"));
    }

    private static MapDesignLibrary.AuthoredContent loadFromMount(ContentMount mount) throws IOException {
        if (mount == null) {
            throw new IOException("Aether core content mount is unavailable.");
        }
        String contentRoot = switch (mount.origin()) {
            case BUNDLED, DEVELOPMENT -> "assets/editor/content";
            case PROJECT, INSTALLED, WORKSHOP -> "assets/packs/" + mount.manifest().namespace() + "/editor/content";
        };
        MapDesignLibrary.MapDesign combined = MapDesignLibrary.createBlank(
                3, 3, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        for (String fileName : MapDesignContentStore.catalogFiles().stream().sorted().toList()) {
            String logicalPath = contentRoot + "/" + fileName;
            try (InputStream input = mount.open(logicalPath)) {
                if (input == null) {
                    continue;
                }
                MapDesignLibrary.MapDesign segment = MapDesignLibrary.loadContentSegment(
                        Path.of(logicalPath), input);
                MapDesignLibrary.mergeAuthoredContent(combined, MapDesignLibrary.authoredContentOf(segment));
            }
        }
        return MapDesignLibrary.authoredContentOf(combined);
    }

    private static MapDesignLibrary.AuthoredContent difference(
            MapDesignLibrary.AuthoredContent desired,
            MapDesignLibrary.AuthoredContent base
    ) {
        return new MapDesignLibrary.AuthoredContent(
                changed(desired.authoredDialogues(), base.authoredDialogues(), MapDesignLibrary.AuthoredDialogue::interactionId),
                changed(desired.authoredQuests(), base.authoredQuests(), MapDesignLibrary.AuthoredQuest::questId),
                changed(desired.customItems(), base.customItems(), MapDesignLibrary.CustomItem::itemId),
                changed(desired.customMobs(), base.customMobs(), MapDesignLibrary.CustomMob::mobId),
                changed(desired.customLimbs(), base.customLimbs(), MapDesignLibrary.CustomLimb::limbId),
                changed(desired.customNpcs(), base.customNpcs(), MapDesignLibrary.CustomNpc::npcId),
                changed(desired.customFurniture(), base.customFurniture(),
                        MapDesignLibrary.CustomFurnitureDefinition::furnitureId),
                changed(desired.customGatheringNodes(), base.customGatheringNodes(),
                        MapDesignLibrary.CustomGatheringNode::nodeId),
                changed(desired.customCookingRecipes(), base.customCookingRecipes(),
                        MapDesignLibrary.CustomCookingRecipe::recipeId),
                changed(desired.craftingRecipes(), base.craftingRecipes(), MapDesignLibrary.CraftingRecipe::recipeId)
        );
    }

    private static <T> List<T> changed(List<T> desired, List<T> base, Function<T, String> id) {
        Map<String, T> baseById = byId(base, id);
        return desired.stream().filter(value -> !Objects.equals(baseById.get(key(id.apply(value))), value)).toList();
    }

    private Map<String, List<CatalogSegment>> loadAuxiliaryCatalogs(ContentMount baseMount) throws IOException {
        List<ContentMount> mounts = new ArrayList<>();
        mounts.add(baseMount);
        List<ContentMount> active = new ArrayList<>(registry.snapshot().activeHighestPriorityFirst());
        Collections.reverse(active);
        for (ContentMount mount : active) {
            if (mount == baseMount || mount.origin() == ContentMount.Origin.BUNDLED
                    || mount.origin() == ContentMount.Origin.DEVELOPMENT
                    || mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
                continue;
            }
            mounts.add(mount);
        }
        Map<String, List<CatalogSegment>> result = new LinkedHashMap<>();
        for (String fileName : AUXILIARY_CATALOGS) {
            List<CatalogSegment> segments = new ArrayList<>();
            for (ContentMount mount : mounts) {
                String root = switch (mount.origin()) {
                    case BUNDLED, DEVELOPMENT -> "assets/editor/content";
                    case PROJECT, INSTALLED, WORKSHOP ->
                            "assets/packs/" + mount.manifest().namespace() + "/editor/content";
                };
                String logicalPath = root + "/" + fileName;
                try (InputStream input = mount.open(logicalPath)) {
                    if (input == null) {
                        continue;
                    }
                    Properties properties = new Properties();
                    properties.load(input);
                    Map<String, String> values = new LinkedHashMap<>();
                    properties.stringPropertyNames().stream().sorted()
                            .forEach(key -> values.put(key, properties.getProperty(key)));
                    segments.add(new CatalogSegment(mount.manifest(), logicalPath, Map.copyOf(values)));
                }
            }
            result.put(fileName, List.copyOf(segments));
        }
        return Map.copyOf(result);
    }

    private static ContentSnapshot indexed(
            long packRevision,
            MapDesignLibrary.AuthoredContent content,
            Map<String, List<CatalogSegment>> auxiliaryCatalogs
    ) {
        long revision = packRevision;
        return new ContentSnapshot(
                revision,
                packRevision,
                content,
                byId(content.authoredDialogues(), MapDesignLibrary.AuthoredDialogue::interactionId),
                byId(content.authoredQuests(), MapDesignLibrary.AuthoredQuest::questId),
                byId(content.customItems(), MapDesignLibrary.CustomItem::itemId),
                byId(content.customMobs(), MapDesignLibrary.CustomMob::mobId),
                byId(content.customLimbs(), MapDesignLibrary.CustomLimb::limbId),
                byId(content.customNpcs(), MapDesignLibrary.CustomNpc::npcId),
                byId(content.customFurniture(), MapDesignLibrary.CustomFurnitureDefinition::furnitureId),
                byId(content.customGatheringNodes(), MapDesignLibrary.CustomGatheringNode::nodeId),
                byId(content.customCookingRecipes(), MapDesignLibrary.CustomCookingRecipe::recipeId),
                byId(content.craftingRecipes(), MapDesignLibrary.CraftingRecipe::recipeId),
                byName(content.customItems(), MapDesignLibrary.CustomItem::displayName),
                byName(content.customMobs(), MapDesignLibrary.CustomMob::displayName),
                auxiliaryCatalogs
        );
    }

    private static <T> Map<String, T> byId(List<T> values, Function<T, String> id) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            result.put(key(id.apply(value)), value);
        }
        return Map.copyOf(result);
    }

    private static <T> Map<String, T> byName(List<T> values, Function<T, String> name) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            result.putIfAbsent(key(name.apply(value)), value);
        }
        return Map.copyOf(result);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ContentSnapshot(
            long revision,
            long packRevision,
            MapDesignLibrary.AuthoredContent content,
            Map<String, MapDesignLibrary.AuthoredDialogue> dialogueById,
            Map<String, MapDesignLibrary.AuthoredQuest> questById,
            Map<String, MapDesignLibrary.CustomItem> itemById,
            Map<String, MapDesignLibrary.CustomMob> mobById,
            Map<String, MapDesignLibrary.CustomLimb> limbById,
            Map<String, MapDesignLibrary.CustomNpc> npcById,
            Map<String, MapDesignLibrary.CustomFurnitureDefinition> furnitureById,
            Map<String, MapDesignLibrary.CustomGatheringNode> gatheringNodeById,
            Map<String, MapDesignLibrary.CustomCookingRecipe> cookingRecipeById,
            Map<String, MapDesignLibrary.CraftingRecipe> craftingRecipeById,
            Map<String, MapDesignLibrary.CustomItem> itemByDisplayName,
            Map<String, MapDesignLibrary.CustomMob> mobByDisplayName,
            Map<String, List<CatalogSegment>> auxiliaryCatalogs
    ) {
        public List<CatalogSegment> catalogSegments(String fileName) {
            return auxiliaryCatalogs.getOrDefault(fileName, List.of());
        }
    }

    public record CatalogSegment(
            ContentPackManifest manifest,
            String logicalPath,
            Map<String, String> properties
    ) {
        public CatalogSegment {
            properties = Map.copyOf(properties);
        }

        public Properties asProperties() {
            Properties result = new Properties();
            result.putAll(properties);
            return result;
        }
    }

    private static final class Accumulator {
        private final List<MapDesignLibrary.AuthoredDialogue> dialogues;
        private final List<MapDesignLibrary.AuthoredQuest> quests;
        private final List<MapDesignLibrary.CustomItem> items;
        private final List<MapDesignLibrary.CustomMob> mobs;
        private final List<MapDesignLibrary.CustomLimb> limbs;
        private final List<MapDesignLibrary.CustomNpc> npcs;
        private final List<MapDesignLibrary.CustomFurnitureDefinition> furniture;
        private final List<MapDesignLibrary.CustomGatheringNode> gathering;
        private final List<MapDesignLibrary.CustomCookingRecipe> cooking;
        private final List<MapDesignLibrary.CraftingRecipe> crafting;
        private final Map<String, Map<String, String>> origins = new HashMap<>();

        private Accumulator(MapDesignLibrary.AuthoredContent base, String origin) {
            dialogues = new ArrayList<>(base.authoredDialogues());
            quests = new ArrayList<>(base.authoredQuests());
            items = new ArrayList<>(base.customItems());
            mobs = new ArrayList<>(base.customMobs());
            limbs = new ArrayList<>(base.customLimbs());
            npcs = new ArrayList<>(base.customNpcs());
            furniture = new ArrayList<>(base.customFurniture());
            gathering = new ArrayList<>(base.customGatheringNodes());
            cooking = new ArrayList<>(base.customCookingRecipes());
            crafting = new ArrayList<>(base.craftingRecipes());
            recordOrigins("dialogue", dialogues, MapDesignLibrary.AuthoredDialogue::interactionId, origin);
            recordOrigins("quest", quests, MapDesignLibrary.AuthoredQuest::questId, origin);
            recordOrigins("item", items, MapDesignLibrary.CustomItem::itemId, origin);
            recordOrigins("mob", mobs, MapDesignLibrary.CustomMob::mobId, origin);
            recordOrigins("limb", limbs, MapDesignLibrary.CustomLimb::limbId, origin);
            recordOrigins("npc", npcs, MapDesignLibrary.CustomNpc::npcId, origin);
            recordOrigins("furniture", furniture, MapDesignLibrary.CustomFurnitureDefinition::furnitureId, origin);
            recordOrigins("gathering", gathering, MapDesignLibrary.CustomGatheringNode::nodeId, origin);
            recordOrigins("cooking", cooking, MapDesignLibrary.CustomCookingRecipe::recipeId, origin);
            recordOrigins("crafting", crafting, MapDesignLibrary.CraftingRecipe::recipeId, origin);
        }

        private void merge(MapDesignLibrary.AuthoredContent incoming, ContentPackManifest manifest) throws IOException {
            mergeType("dialogue", dialogues, incoming.authoredDialogues(),
                    MapDesignLibrary.AuthoredDialogue::interactionId, manifest);
            mergeType("quest", quests, incoming.authoredQuests(), MapDesignLibrary.AuthoredQuest::questId, manifest);
            mergeType("item", items, incoming.customItems(), MapDesignLibrary.CustomItem::itemId, manifest);
            mergeType("mob", mobs, incoming.customMobs(), MapDesignLibrary.CustomMob::mobId, manifest);
            mergeType("limb", limbs, incoming.customLimbs(), MapDesignLibrary.CustomLimb::limbId, manifest);
            mergeType("npc", npcs, incoming.customNpcs(), MapDesignLibrary.CustomNpc::npcId, manifest);
            mergeType("furniture", furniture, incoming.customFurniture(),
                    MapDesignLibrary.CustomFurnitureDefinition::furnitureId, manifest);
            mergeType("gathering", gathering, incoming.customGatheringNodes(),
                    MapDesignLibrary.CustomGatheringNode::nodeId, manifest);
            mergeType("cooking", cooking, incoming.customCookingRecipes(),
                    MapDesignLibrary.CustomCookingRecipe::recipeId, manifest);
            mergeType("crafting", crafting, incoming.craftingRecipes(),
                    MapDesignLibrary.CraftingRecipe::recipeId, manifest);
        }

        private <T> void mergeType(
                String type,
                List<T> target,
                List<T> incoming,
                Function<T, String> id,
                ContentPackManifest manifest
        ) throws IOException {
            Map<String, String> typeOrigins = origins.computeIfAbsent(type, ignored -> new HashMap<>());
            for (T value : incoming) {
                String contentId = key(id.apply(value));
                int existingIndex = -1;
                for (int index = 0; index < target.size(); index++) {
                    if (key(id.apply(target.get(index))).equals(contentId)) {
                        existingIndex = index;
                        break;
                    }
                }
                if (existingIndex < 0) {
                    String requiredPrefix = manifest.namespace() + "__";
                    if (!contentId.startsWith(requiredPrefix)) {
                        throw new IOException("New " + type + " ID '" + contentId + "' from "
                                + manifest.id() + " must begin with " + requiredPrefix + ".");
                    }
                    target.add(value);
                    typeOrigins.put(contentId, manifest.id());
                    continue;
                }
                String targetPack = typeOrigins.getOrDefault(contentId, "aether.core");
                boolean declared = manifest.overrides().stream().anyMatch(override ->
                        override.targetPack().equals(targetPack)
                                && override.contentType().equals(type)
                                && key(override.contentId()).equals(contentId));
                if (!declared) {
                    throw new IOException("Undeclared " + type + " collision for '" + contentId
                            + "' between " + targetPack + " and " + manifest.id() + ".");
                }
                target.set(existingIndex, value);
                typeOrigins.put(contentId, manifest.id());
            }
        }

        private <T> void recordOrigins(
                String type,
                List<T> values,
                Function<T, String> id,
                String origin
        ) {
            Map<String, String> typeOrigins = origins.computeIfAbsent(type, ignored -> new HashMap<>());
            values.forEach(value -> typeOrigins.put(key(id.apply(value)), origin));
        }

        private MapDesignLibrary.AuthoredContent content() {
            return new MapDesignLibrary.AuthoredContent(dialogues, quests, items, mobs, limbs, npcs,
                    furniture, gathering, cooking, crafting);
        }

        private String origin(TypedId id) {
            return origins.getOrDefault(id.type(), Map.of()).getOrDefault(id.id(), "");
        }
    }
}

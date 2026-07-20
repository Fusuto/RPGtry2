package org.main.content;

import org.main.engine.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

public final class WorldManifestLibrary {
    public static final int FORMAT_VERSION = 1;
    public static final int DEFAULT_CHUNK_SIZE = 32;
    public static final String WORLD_RESOURCE_FOLDER = "assets/editor/worlds";
    public static final Path WORLD_FOLDER = MapDesignLibrary.EDITOR_RESOURCE_FOLDER.resolve("worlds");
    public static final Path DATA_WORLD_FOLDER = Path.of("data", "worlds");
    public static final String MANIFEST_FILE_NAME = "world.properties";

    private WorldManifestLibrary() {
    }

    public static WorldManifest create(String worldId, String displayName, int chunkWidth, int chunkHeight) {
        String safeId = safeId(worldId);
        return new WorldManifest(
                FORMAT_VERSION,
                safeId,
                displayName == null || displayName.isBlank() ? safeId : displayName.trim(),
                "",
                Math.max(3, chunkWidth),
                Math.max(3, chunkHeight),
                1,
                1,
                new LinkedHashMap<>()
        );
    }

    public static void save(WorldManifest manifest, Path path) throws IOException {
        if (manifest == null || path == null) {
            throw new IOException("World manifest and path are required.");
        }

        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties properties = new Properties();
        properties.setProperty("type", "open-world");
        properties.setProperty("formatVersion", String.valueOf(FORMAT_VERSION));
        properties.setProperty("worldId", manifest.worldId());
        properties.setProperty("displayName", manifest.displayName());
        properties.setProperty("description", manifest.description());
        properties.setProperty("chunkWidth", String.valueOf(manifest.chunkWidth()));
        properties.setProperty("chunkHeight", String.valueOf(manifest.chunkHeight()));
        properties.setProperty("startX", String.valueOf(manifest.startX()));
        properties.setProperty("startY", String.valueOf(manifest.startY()));

        List<Map.Entry<ChunkCoordinate, String>> chunks = manifest.chunks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        properties.setProperty("chunk.count", String.valueOf(chunks.size()));
        for (int i = 0; i < chunks.size(); i++) {
            Map.Entry<ChunkCoordinate, String> entry = chunks.get(i);
            String prefix = "chunk." + i + ".";
            properties.setProperty(prefix + "x", String.valueOf(entry.getKey().x()));
            properties.setProperty(prefix + "y", String.valueOf(entry.getKey().y()));
            properties.setProperty(prefix + "path", entry.getValue());
        }

        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "Aether open world manifest");
        }
    }

    public static WorldManifest load(Path path) throws IOException {
        if (path == null) {
            throw new IOException("World manifest path is missing.");
        }

        Properties properties = new Properties();
        try (InputStream inputStream = openWorldStream(path)) {
            properties.load(inputStream);
        }
        if (!"open-world".equalsIgnoreCase(properties.getProperty("type", ""))) {
            throw new IOException("Not an open-world manifest: " + path);
        }

        int version = readInt(properties, "formatVersion", FORMAT_VERSION);
        String worldId = safeId(properties.getProperty("worldId", worldIdFromPath(path)));
        String displayName = properties.getProperty("displayName", worldId).trim();
        String description = properties.getProperty("description", "").trim();
        int chunkWidth = Math.max(3, readInt(properties, "chunkWidth", DEFAULT_CHUNK_SIZE));
        int chunkHeight = Math.max(3, readInt(properties, "chunkHeight", DEFAULT_CHUNK_SIZE));
        int startX = readInt(properties, "startX", 1);
        int startY = readInt(properties, "startY", 1);
        int chunkCount = Math.max(0, readInt(properties, "chunk.count", 0));
        Map<ChunkCoordinate, String> chunks = new LinkedHashMap<>();
        for (int i = 0; i < chunkCount; i++) {
            String prefix = "chunk." + i + ".";
            int x = readInt(properties, prefix + "x", 0);
            int y = readInt(properties, prefix + "y", 0);
            String chunkPath = properties.getProperty(prefix + "path", "").trim().replace('\\', '/');
            if (!chunkPath.isBlank()) {
                ChunkCoordinate coordinate = new ChunkCoordinate(x, y);
                if (chunks.putIfAbsent(coordinate, chunkPath) != null) {
                    throw new IOException("World manifest contains duplicate chunk coordinate " + coordinate + ".");
                }
            }
        }
        return new WorldManifest(
                version,
                worldId,
                displayName,
                description,
                chunkWidth,
                chunkHeight,
                startX,
                startY,
                chunks
        );
    }

    public static boolean isWorldManifest(Path path) {
        if (path == null) {
            return false;
        }
        try {
            load(path);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static Path resolveChunkPath(Path manifestPath, String chunkPath) {
        if (chunkPath == null || chunkPath.isBlank()) {
            return null;
        }
        String normalized = chunkPath.replace('\\', '/');
        if (normalized.startsWith("assets/")) {
            return Path.of(normalized);
        }

        Path manifestParent = manifestPath == null ? null : manifestPath.getParent();
        if (manifestParent == null) {
            return Path.of(normalized).normalize();
        }
        return manifestParent.resolve(normalized).normalize();
    }

    public static String relativeChunkPath(Path manifestPath, Path chunkPath) {
        if (manifestPath == null || chunkPath == null || manifestPath.getParent() == null) {
            return chunkPath == null ? "" : chunkPath.toString().replace('\\', '/');
        }
        Path parent = manifestPath.toAbsolutePath().normalize().getParent();
        Path absoluteChunk = chunkPath.toAbsolutePath().normalize();
        if (absoluteChunk.startsWith(parent)) {
            return parent.relativize(absoluteChunk).toString().replace('\\', '/');
        }
        return MapDesignLibrary.resourcePathForMap(chunkPath);
    }

    public static List<Path> listSavedWorlds() throws IOException {
        List<Path> worlds = new ArrayList<>();
        addWorldFiles(worlds, WORLD_FOLDER);
        for (String resourcePath : AssetLoader.listAssetFiles(WORLD_RESOURCE_FOLDER)) {
            if (resourcePath.toLowerCase(Locale.ROOT).endsWith("/" + MANIFEST_FILE_NAME)
                    || resourcePath.equalsIgnoreCase(WORLD_RESOURCE_FOLDER + "/" + MANIFEST_FILE_NAME)) {
                Path resourceWorld = Path.of(resourcePath);
                if (worlds.stream().noneMatch(existing -> worldListingKey(existing).equals(worldListingKey(resourceWorld)))) {
                    worlds.add(resourceWorld);
                }
            }
        }
        addWorldFiles(worlds, DATA_WORLD_FOLDER);
        worlds.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return worlds;
    }

    public static MapDesignLibrary.AuthoredContent loadWorldContent(
            WorldManifest manifest,
            Path manifestPath
    ) throws IOException {
        if (manifest == null) {
            return MapDesignLibrary.authoredContentOf(null);
        }

        MapDesignLibrary.MapDesign catalog = MapDesignLibrary.createBlank(
                3,
                3,
                ThemeLibrary.STONE_WOOD,
                ThemeLibrary.SANDSTONE_GATE
        );
        for (Map.Entry<ChunkCoordinate, String> entry : manifest.chunks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            Path chunkPath = resolveChunkPath(manifestPath, entry.getValue());
            MapDesignLibrary.MapDesign chunk = MapDesignLibrary.load(chunkPath);
            MapDesignLibrary.mergeAuthoredContent(catalog, MapDesignLibrary.authoredContentOf(chunk));
        }
        return MapDesignLibrary.authoredContentOf(catalog);
    }

    public static List<MapDesignLibrary.ValidationIssue> validate(WorldManifest manifest, Path manifestPath) {
        List<MapDesignLibrary.ValidationIssue> issues = new ArrayList<>();
        if (manifest == null) {
            issues.add(error("World manifest is missing."));
            return issues;
        }
        if (manifest.formatVersion() != FORMAT_VERSION) {
            issues.add(error("Unsupported world format version " + manifest.formatVersion() + "."));
        }
        if (manifest.chunks().isEmpty()) {
            issues.add(error("World has no chunks."));
            return issues;
        }

        Map<String, Object> dialogueIds = new LinkedHashMap<>();
        Map<String, Object> questIds = new LinkedHashMap<>();
        Map<String, Object> itemIds = new LinkedHashMap<>();
        Map<String, Object> mobIds = new LinkedHashMap<>();
        Map<String, Object> limbIds = new LinkedHashMap<>();
        Map<String, Object> npcIds = new LinkedHashMap<>();
        Map<String, Object> gatheringIds = new LinkedHashMap<>();
        Map<String, Object> cookingIds = new LinkedHashMap<>();
        Map<String, Object> craftingRecipeIds = new LinkedHashMap<>();

        for (Map.Entry<ChunkCoordinate, String> entry : manifest.chunks().entrySet()) {
            Path chunkPath = resolveChunkPath(manifestPath, entry.getValue());
            try {
                MapDesignLibrary.MapDesign design = MapDesignLibrary.load(chunkPath);
                if (design.width() != manifest.chunkWidth() || design.height() != manifest.chunkHeight()) {
                    issues.add(error("Chunk " + entry.getKey() + " is "
                            + design.width() + "x" + design.height() + " but the world requires "
                            + manifest.chunkWidth() + "x" + manifest.chunkHeight() + "."));
                }
                for (MapDesignLibrary.ValidationIssue issue : MapDesignLibrary.validate(design)) {
                    if (issue.severity() == MapDesignLibrary.ValidationSeverity.ERROR) {
                        issues.add(error("Chunk " + entry.getKey() + ": " + issue.message()));
                    }
                }
                checkContentConflicts(issues, entry.getKey(), "dialogue", design.authoredDialogues(),
                        MapDesignLibrary.AuthoredDialogue::interactionId, dialogueIds);
                checkContentConflicts(issues, entry.getKey(), "quest", design.authoredQuests(),
                        MapDesignLibrary.AuthoredQuest::questId, questIds);
                checkContentConflicts(issues, entry.getKey(), "item", design.customItems(),
                        MapDesignLibrary.CustomItem::itemId, itemIds);
                checkContentConflicts(issues, entry.getKey(), "mob", design.customMobs(),
                        MapDesignLibrary.CustomMob::mobId, mobIds);
                checkContentConflicts(issues, entry.getKey(), "limb", design.customLimbs(),
                        MapDesignLibrary.CustomLimb::limbId, limbIds);
                checkContentConflicts(issues, entry.getKey(), "NPC", design.customNpcs(),
                        MapDesignLibrary.CustomNpc::npcId, npcIds);
                checkContentConflicts(issues, entry.getKey(), "gathering node", design.customGatheringNodes(),
                        MapDesignLibrary.CustomGatheringNode::nodeId, gatheringIds);
                checkContentConflicts(issues, entry.getKey(), "cooking recipe", design.customCookingRecipes(),
                        MapDesignLibrary.CustomCookingRecipe::recipeId, cookingIds);
                checkContentConflicts(issues, entry.getKey(), "crafting recipe", design.craftingRecipes(),
                        MapDesignLibrary.CraftingRecipe::recipeId, craftingRecipeIds);
                validateMapLinks(issues, entry.getKey(), design);
            } catch (IOException exception) {
                issues.add(error("Chunk " + entry.getKey() + " could not be loaded: " + entry.getValue()));
            }
        }

        ChunkCoordinate startChunk = manifest.chunkForGlobal(manifest.startX(), manifest.startY());
        String startChunkPath = manifest.chunks().get(startChunk);
        if (startChunkPath == null) {
            issues.add(error("World start position resolves to missing chunk " + startChunk + "."));
        } else {
            try {
                MapDesignLibrary.MapDesign startDesign =
                        MapDesignLibrary.load(resolveChunkPath(manifestPath, startChunkPath));
                int localX = manifest.localX(manifest.startX());
                int localY = manifest.localY(manifest.startY());
                if (startDesign.tiles()[localY][localX].blocksMovement()) {
                    issues.add(error("World start position is blocked."));
                }
            } catch (IOException exception) {
                issues.add(error("World start chunk could not be checked."));
            }
        }
        return List.copyOf(issues);
    }

    private static <T> void checkContentConflicts(
            List<MapDesignLibrary.ValidationIssue> issues,
            ChunkCoordinate coordinate,
            String contentType,
            List<T> values,
            Function<T, String> idExtractor,
            Map<String, Object> knownValues
    ) {
        if (values == null) {
            return;
        }
        for (T value : values) {
            String id = value == null ? "" : idExtractor.apply(value);
            if (id == null || id.isBlank()) {
                continue;
            }
            Object previous = knownValues.putIfAbsent(id, value);
            if (previous != null && !previous.equals(value)) {
                issues.add(error("Chunk " + coordinate + " defines " + contentType + " id " + id
                        + " differently from another chunk."));
            }
        }
    }

    private static void validateMapLinks(
            List<MapDesignLibrary.ValidationIssue> issues,
            ChunkCoordinate sourceChunk,
            MapDesignLibrary.MapDesign design
    ) {
        for (MapDesignLibrary.MapPlacement placement
                : design.placements() == null ? List.<MapDesignLibrary.MapPlacement>of() : design.placements()) {
            if (placement.kind() != MapDesignLibrary.PlacementKind.INTERACTION
                    || placement.id() == null
                    || !placement.id().startsWith("map_link|")) {
                continue;
            }
            String[] parts = placement.id().split("\\|", -1);
            if (parts.length != 4 || parts[1].isBlank()) {
                continue;
            }
            int targetX;
            int targetY;
            try {
                targetX = Integer.parseInt(parts[2]);
                targetY = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            try {
                Path targetPath = resolveMapLinkPath(parts[1]);
                if (isWorldManifest(targetPath)) {
                    WorldManifest targetWorld = load(targetPath);
                    ChunkCoordinate targetChunk = targetWorld.chunkForGlobal(targetX, targetY);
                    String targetChunkPath = targetWorld.chunks().get(targetChunk);
                    if (targetChunkPath == null) {
                        issues.add(error("Chunk " + sourceChunk + " map link at " + placement.x() + ","
                                + placement.y() + " targets missing world chunk " + targetChunk + "."));
                        continue;
                    }
                    MapDesignLibrary.MapDesign target = MapDesignLibrary.load(
                            resolveChunkPath(targetPath, targetChunkPath));
                    validateMapLinkTile(
                            issues,
                            sourceChunk,
                            placement,
                            target,
                            targetWorld.localX(targetX),
                            targetWorld.localY(targetY)
                    );
                } else {
                    MapDesignLibrary.MapDesign target = MapDesignLibrary.load(targetPath);
                    validateMapLinkTile(issues, sourceChunk, placement, target, targetX, targetY);
                }
            } catch (IOException | RuntimeException exception) {
                issues.add(error("Chunk " + sourceChunk + " map link at " + placement.x() + ","
                        + placement.y() + " cannot load target " + parts[1] + "."));
            }
        }
    }

    private static void validateMapLinkTile(
            List<MapDesignLibrary.ValidationIssue> issues,
            ChunkCoordinate sourceChunk,
            MapDesignLibrary.MapPlacement placement,
            MapDesignLibrary.MapDesign target,
            int targetX,
            int targetY
    ) {
        if (targetX < 0 || targetY < 0 || targetX >= target.width() || targetY >= target.height()) {
            issues.add(error("Chunk " + sourceChunk + " map link at " + placement.x() + ","
                    + placement.y() + " targets an out-of-bounds tile."));
        } else if (target.tiles()[targetY][targetX].blocksMovement()) {
            issues.add(error("Chunk " + sourceChunk + " map link at " + placement.x() + ","
                    + placement.y() + " targets a blocked tile."));
        }
    }

    private static Path resolveMapLinkPath(String rawPath) {
        Path path = Path.of(rawPath);
        if (!Files.isRegularFile(path) && !rawPath.replace('\\', '/').startsWith("assets/")) {
            path = MapDesignLibrary.MAP_FOLDER.resolve(rawPath).normalize();
        }
        return path;
    }

    public static Path defaultManifestPath(String worldId) {
        return WORLD_FOLDER.resolve(safeId(worldId)).resolve(MANIFEST_FILE_NAME);
    }

    public static Path defaultChunkPath(Path manifestPath, ChunkCoordinate coordinate) {
        Path parent = manifestPath == null || manifestPath.getParent() == null
                ? WORLD_FOLDER
                : manifestPath.getParent();
        return parent.resolve("chunks").resolve(coordinate.x() + "_" + coordinate.y() + ".properties");
    }

    private static InputStream openWorldStream(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.startsWith("src/main/resources/")) {
            normalized = normalized.substring("src/main/resources/".length());
        } else if (!normalized.startsWith("assets/")) {
            normalized = WORLD_RESOURCE_FOLDER + "/" + normalized;
        }
        return AssetLoader.openAssetStream(normalized);
    }

    private static void addWorldFiles(List<Path> worlds, Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> MANIFEST_FILE_NAME.equalsIgnoreCase(path.getFileName().toString()))
                    .filter(path -> worlds.stream().noneMatch(
                            existing -> worldListingKey(existing).equals(worldListingKey(path))))
                    .forEach(worlds::add);
        }
    }

    private static String worldListingKey(Path path) {
        String value = path == null ? "" : path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        int worldsIndex = value.indexOf("/worlds/");
        if (worldsIndex >= 0) {
            return value.substring(worldsIndex + "/worlds/".length());
        }
        if (value.startsWith("assets/editor/worlds/")) {
            return value.substring("assets/editor/worlds/".length());
        }
        if (value.startsWith("data/worlds/")) {
            return value.substring("data/worlds/".length());
        }
        return value;
    }

    private static MapDesignLibrary.ValidationIssue error(String message) {
        return new MapDesignLibrary.ValidationIssue(MapDesignLibrary.ValidationSeverity.ERROR, message);
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String worldIdFromPath(Path path) {
        Path parent = path == null ? null : path.getParent();
        return parent == null || parent.getFileName() == null ? "world" : parent.getFileName().toString();
    }

    public static String safeId(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        return safe.isBlank() ? "world" : safe;
    }

    public record ChunkCoordinate(int x, int y) implements Comparable<ChunkCoordinate> {
        @Override
        public int compareTo(ChunkCoordinate other) {
            int yComparison = Integer.compare(y, other.y);
            return yComparison != 0 ? yComparison : Integer.compare(x, other.x);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    public record WorldManifest(
            int formatVersion,
            String worldId,
            String displayName,
            String description,
            int chunkWidth,
            int chunkHeight,
            int startX,
            int startY,
            Map<ChunkCoordinate, String> chunks
    ) {
        public WorldManifest {
            worldId = safeId(worldId);
            displayName = displayName == null || displayName.isBlank() ? worldId : displayName.trim();
            description = description == null ? "" : description.trim();
            chunkWidth = Math.max(3, chunkWidth);
            chunkHeight = Math.max(3, chunkHeight);
            chunks = chunks == null ? Map.of() : Map.copyOf(chunks);
        }

        public ChunkCoordinate chunkForGlobal(int globalX, int globalY) {
            return new ChunkCoordinate(Math.floorDiv(globalX, chunkWidth), Math.floorDiv(globalY, chunkHeight));
        }

        public int localX(int globalX) {
            return Math.floorMod(globalX, chunkWidth);
        }

        public int localY(int globalY) {
            return Math.floorMod(globalY, chunkHeight);
        }

        public int globalX(ChunkCoordinate chunk, int localX) {
            return chunk.x() * chunkWidth + localX;
        }

        public int globalY(ChunkCoordinate chunk, int localY) {
            return chunk.y() * chunkHeight + localY;
        }

        public WorldManifest withChunk(ChunkCoordinate coordinate, String path) {
            Map<ChunkCoordinate, String> updated = new LinkedHashMap<>(chunks);
            updated.put(coordinate, path == null ? "" : path.replace('\\', '/'));
            return new WorldManifest(
                    formatVersion, worldId, displayName, description, chunkWidth, chunkHeight,
                    startX, startY, updated
            );
        }

        public WorldManifest withoutChunk(ChunkCoordinate coordinate) {
            Map<ChunkCoordinate, String> updated = new LinkedHashMap<>(chunks);
            updated.remove(coordinate);
            return new WorldManifest(
                    formatVersion, worldId, displayName, description, chunkWidth, chunkHeight,
                    startX, startY, updated
            );
        }

        public WorldManifest withMetadata(String name, String newDescription, int newStartX, int newStartY) {
            return new WorldManifest(
                    formatVersion, worldId, name, newDescription, chunkWidth, chunkHeight,
                    newStartX, newStartY, chunks
            );
        }
    }
}

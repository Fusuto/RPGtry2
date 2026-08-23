package org.main.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.main.content.MapDesignLibrary.AuthoredContent;
import static org.main.content.MapDesignLibrary.MapDesign;

final class MapDesignContentStore {
    static final String DIALOGUE_FILE = "dialogue.properties";
    static final String QUEST_FILE = "quest.properties";
    static final String ITEM_FILE = "item.properties";
    static final String MOB_FILE = "mob.properties";
    static final String LIMB_FILE = "limb.properties";
    static final String NPC_FILE = "npc.properties";
    static final String FURNITURE_FILE = "furniture.properties";
    static final String GATHERING_NODE_FILE = "gathering_node.properties";
    static final String COOKING_RECIPE_FILE = "cooking_recipe.properties";
    static final String CRAFTING_RECIPE_FILE = "crafting_recipe.properties";

    private static final Set<String> CATALOG_FILES = Set.of(
            DIALOGUE_FILE,
            QUEST_FILE,
            ITEM_FILE,
            MOB_FILE,
            LIMB_FILE,
            NPC_FILE,
            FURNITURE_FILE,
            GATHERING_NODE_FILE,
            COOKING_RECIPE_FILE,
            CRAFTING_RECIPE_FILE
    );

    private MapDesignContentStore() {
    }

    static Set<String> catalogFiles() {
        return CATALOG_FILES;
    }

    static AuthoredContent loadSharedContent() throws IOException {
        MapDesign dialogues = loadRequiredSegment(DIALOGUE_FILE);
        MapDesign quests = loadRequiredSegment(QUEST_FILE);
        MapDesign items = loadRequiredSegment(ITEM_FILE);
        MapDesign mobs = loadRequiredSegment(MOB_FILE);
        MapDesign limbs = loadRequiredSegment(LIMB_FILE);
        MapDesign npcs = loadRequiredSegment(NPC_FILE);
        MapDesign furniture = loadRequiredSegment(FURNITURE_FILE);
        MapDesign gatheringNodes = loadRequiredSegment(GATHERING_NODE_FILE);
        MapDesign cookingRecipes = loadRequiredSegment(COOKING_RECIPE_FILE);
        MapDesign craftingRecipes = loadRequiredSegment(CRAFTING_RECIPE_FILE);
        return new AuthoredContent(
                dialogues.authoredDialogues(),
                quests.authoredQuests(),
                items.customItems(),
                mobs.customMobs(),
                limbs.customLimbs(),
                npcs.customNpcs(),
                furniture.customFurniture(),
                gatheringNodes.customGatheringNodes(),
                cookingRecipes.customCookingRecipes(),
                craftingRecipes.craftingRecipes()
        );
    }

    static void saveSharedContent(AuthoredContent content) throws IOException {
        if (content == null) {
            return;
        }

        List<SegmentWrite> writes = List.of(
                new SegmentWrite(DIALOGUE_FILE, new AuthoredContent(
                        content.authoredDialogues(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(QUEST_FILE, new AuthoredContent(
                        List.of(), content.authoredQuests(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(ITEM_FILE, new AuthoredContent(
                        List.of(), List.of(), content.customItems(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(MOB_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), content.customMobs(), List.of(),
                        List.of(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(LIMB_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), content.customLimbs(),
                        List.of(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(NPC_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        content.customNpcs(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(FURNITURE_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), content.customFurniture(), List.of(), List.of(), List.of()
                )),
                new SegmentWrite(GATHERING_NODE_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), content.customGatheringNodes(), List.of(), List.of()
                )),
                new SegmentWrite(COOKING_RECIPE_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), content.customCookingRecipes(), List.of()
                )),
                new SegmentWrite(CRAFTING_RECIPE_FILE, new AuthoredContent(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), content.craftingRecipes()
                ))
        );
        saveSegmentsTransactionally(writes);
    }

    static boolean isContentCatalogPath(Path path) {
        return path != null
                && path.getFileName() != null
                && CATALOG_FILES.contains(path.getFileName().toString());
    }

    private static MapDesign loadRequiredSegment(String fileName) throws IOException {
        Path editablePath = MapDesignLibrary.CONTENT_FOLDER.resolve(fileName);
        if (Files.isRegularFile(editablePath)) {
            return MapDesignLibrary.loadContentSegment(editablePath);
        }

        Path resourcePath = Path.of(MapDesignLibrary.CONTENT_RESOURCE_FOLDER, fileName);
        try {
            return MapDesignLibrary.loadContentSegment(resourcePath);
        } catch (IOException missingResource) {
            throw new IOException("Required content catalog is missing: " + fileName, missingResource);
        }
    }

    private static void saveSegmentsTransactionally(List<SegmentWrite> writes) throws IOException {
        Path contentFolder = MapDesignLibrary.CONTENT_FOLDER.toAbsolutePath().normalize();
        Files.createDirectories(contentFolder);
        Path stagingFolder = contentFolder.resolve(".transaction-" + UUID.randomUUID()).normalize();
        if (!stagingFolder.getParent().equals(contentFolder)) {
            throw new IOException("Invalid content transaction staging path.");
        }
        Files.createDirectories(stagingFolder);

        Map<String, Boolean> existed = new LinkedHashMap<>();
        Map<String, byte[]> originalContents = new LinkedHashMap<>();
        try {
            for (SegmentWrite write : writes) {
                Path target = contentFolder.resolve(write.fileName()).normalize();
                if (!target.getParent().equals(contentFolder)) {
                    throw new IOException("Invalid content catalog path: " + write.fileName());
                }
                boolean targetExists = Files.isRegularFile(target);
                existed.put(write.fileName(), targetExists);
                if (targetExists) {
                    originalContents.put(write.fileName(), Files.readAllBytes(target));
                }
                MapDesignLibrary.saveContentSegment(
                        contentDesign(write.fileName(), write.content()),
                        stagingFolder.resolve(write.fileName())
                );
            }

            try {
                for (SegmentWrite write : writes) {
                    moveReplacing(
                            stagingFolder.resolve(write.fileName()),
                            contentFolder.resolve(write.fileName())
                    );
                }
            } catch (IOException writeFailure) {
                IOException rollbackFailure = rollbackCatalogs(
                        writes,
                        existed,
                        originalContents,
                        contentFolder
                );
                if (rollbackFailure != null) {
                    writeFailure.addSuppressed(rollbackFailure);
                }
                throw writeFailure;
            }
        } finally {
            deleteDirectory(stagingFolder);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static IOException rollbackCatalogs(
            List<SegmentWrite> writes,
            Map<String, Boolean> existed,
            Map<String, byte[]> originalContents,
            Path contentFolder
    ) {
        IOException failure = null;
        for (SegmentWrite write : writes) {
            Path target = contentFolder.resolve(write.fileName());
            try {
                if (Boolean.TRUE.equals(existed.get(write.fileName()))) {
                    byte[] original = originalContents.get(write.fileName());
                    if (original == null) {
                        throw new IOException("Missing original content for " + write.fileName() + ".");
                    }
                    Files.write(target, original);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException rollbackError) {
                if (failure == null) {
                    failure = new IOException("Content transaction rollback was incomplete.");
                }
                failure.addSuppressed(rollbackError);
            }
        }
        return failure;
    }

    private static void deleteDirectory(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A stale staging folder is harmless and can be removed on the next editor cleanup.
                }
            });
        } catch (IOException ignored) {
            // Preserve the original transaction result if cleanup alone fails.
        }
    }

    private static MapDesign contentDesign(String fileName, AuthoredContent content) {
        MapDesign blank = MapDesignLibrary.createBlank(
                3,
                3,
                ThemeLibrary.STONE_WOOD,
                ThemeLibrary.SANDSTONE_GATE
        );
        return new MapDesign(
                blank.width(),
                blank.height(),
                fileName,
                "Construction Kit content catalog.",
                "",
                "",
                blank.primaryTheme(),
                blank.alternateTheme(),
                blank.tiles(),
                blank.themeIndexes(),
                blank.mapPaint(),
                blank.mapGeometry(),
                blank.mobAreas(),
                blank.placements(),
                blank.placedObjects(),
                new ArrayList<>(content.authoredDialogues()),
                new ArrayList<>(content.authoredQuests()),
                new ArrayList<>(content.customItems()),
                new ArrayList<>(content.customMobs()),
                new ArrayList<>(content.customLimbs()),
                new ArrayList<>(content.customNpcs()),
                new ArrayList<>(content.customFurniture()),
                new ArrayList<>(content.customGatheringNodes()),
                new ArrayList<>(content.customCookingRecipes()),
                new ArrayList<>(content.craftingRecipes()),
                new ArrayList<>(),
                blank.spawnX(),
                blank.spawnY()
        );
    }

    private record SegmentWrite(String fileName, AuthoredContent content) {
    }
}

package org.main.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.main.content.MapDesignLibrary.AuthoredContent;
import static org.main.content.MapDesignLibrary.AuthoredDialogue;
import static org.main.content.MapDesignLibrary.AuthoredQuest;
import static org.main.content.MapDesignLibrary.CustomCompositeRecipe;
import static org.main.content.MapDesignLibrary.CustomCookingRecipe;
import static org.main.content.MapDesignLibrary.CustomGatheringNode;
import static org.main.content.MapDesignLibrary.CustomItem;
import static org.main.content.MapDesignLibrary.CustomLimb;
import static org.main.content.MapDesignLibrary.CustomMob;
import static org.main.content.MapDesignLibrary.CustomNpc;
import static org.main.content.MapDesignLibrary.MapDesign;

final class MapDesignContentStore {
    static final String DIALOGUE_FILE = "dialogue.properties";
    static final String QUEST_FILE = "quest.properties";
    static final String ITEM_FILE = "item.properties";
    static final String MOB_FILE = "mob.properties";
    static final String LIMB_FILE = "limb.properties";
    static final String NPC_FILE = "npc.properties";
    static final String GATHERING_NODE_FILE = "gathering_node.properties";
    static final String COOKING_RECIPE_FILE = "cooking_recipe.properties";
    static final String COMPOSITE_RECIPE_FILE = "composite_recipe.properties";

    private static final Set<String> CATALOG_FILES = Set.of(
            DIALOGUE_FILE,
            QUEST_FILE,
            ITEM_FILE,
            MOB_FILE,
            LIMB_FILE,
            NPC_FILE,
            GATHERING_NODE_FILE,
            COOKING_RECIPE_FILE,
            COMPOSITE_RECIPE_FILE,
            "authored_content.properties"
    );

    private MapDesignContentStore() {
    }

    static AuthoredContent loadSharedContent() throws IOException {
        MapDesign dialogues = loadSegment(DIALOGUE_FILE);
        MapDesign quests = loadSegment(QUEST_FILE);
        MapDesign items = loadSegment(ITEM_FILE);
        MapDesign mobs = loadSegment(MOB_FILE);
        MapDesign limbs = loadSegment(LIMB_FILE);
        MapDesign npcs = loadSegment(NPC_FILE);
        MapDesign gatheringNodes = loadSegment(GATHERING_NODE_FILE);
        MapDesign cookingRecipes = loadSegment(COOKING_RECIPE_FILE);
        MapDesign compositeRecipes = loadSegment(COMPOSITE_RECIPE_FILE);

        boolean hasSegmentCatalog = dialogues != null
                || quests != null
                || items != null
                || mobs != null
                || limbs != null
                || npcs != null
                || gatheringNodes != null
                || cookingRecipes != null
                || compositeRecipes != null;
        if (hasSegmentCatalog) {
            return new AuthoredContent(
                    dialogues == null ? List.of() : dialogues.authoredDialogues(),
                    quests == null ? List.of() : quests.authoredQuests(),
                    items == null ? List.of() : items.customItems(),
                    mobs == null ? List.of() : mobs.customMobs(),
                    limbs == null ? List.of() : limbs.customLimbs(),
                    npcs == null ? List.of() : npcs.customNpcs(),
                    gatheringNodes == null ? List.of() : gatheringNodes.customGatheringNodes(),
                    cookingRecipes == null ? List.of() : cookingRecipes.customCookingRecipes(),
                    compositeRecipes == null ? List.of() : compositeRecipes.customCompositeRecipes()
            );
        }

        return loadLegacySharedContent();
    }

    static void saveSharedContent(AuthoredContent content) throws IOException {
        if (content == null) {
            return;
        }

        saveSegment(DIALOGUE_FILE, new AuthoredContent(
                content.authoredDialogues(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        ));
        saveSegment(QUEST_FILE, new AuthoredContent(
                List.of(), content.authoredQuests(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        ));
        saveSegment(ITEM_FILE, new AuthoredContent(
                List.of(), List.of(), content.customItems(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        ));
        saveSegment(MOB_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), content.customMobs(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        ));
        saveSegment(LIMB_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), content.customLimbs(),
                List.of(), List.of(), List.of(), List.of()
        ));
        saveSegment(NPC_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                content.customNpcs(), List.of(), List.of(), List.of()
        ));
        saveSegment(GATHERING_NODE_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), content.customGatheringNodes(), List.of(), List.of()
        ));
        saveSegment(COOKING_RECIPE_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), content.customCookingRecipes(), List.of()
        ));
        saveSegment(COMPOSITE_RECIPE_FILE, new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), content.customCompositeRecipes()
        ));
    }

    static boolean isContentCatalogPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return CATALOG_FILES.contains(path.getFileName().toString());
    }

    private static MapDesign loadSegment(String fileName) throws IOException {
        Path editablePath = MapDesignLibrary.CONTENT_FOLDER.resolve(fileName);
        if (Files.isRegularFile(editablePath)) {
            return MapDesignLibrary.loadContentSegment(editablePath);
        }

        Path resourcePath = Path.of(MapDesignLibrary.CONTENT_RESOURCE_FOLDER, fileName);
        try {
            return MapDesignLibrary.loadContentSegment(resourcePath);
        } catch (IOException missingResource) {
            return null;
        }
    }

    private static void saveSegment(String fileName, AuthoredContent content) throws IOException {
        MapDesignLibrary.saveContentSegment(
                contentDesign(fileName, content),
                MapDesignLibrary.CONTENT_FOLDER.resolve(fileName)
        );
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
                blank.primaryTheme(),
                blank.alternateTheme(),
                blank.tiles(),
                blank.themeIndexes(),
                blank.placements(),
                new ArrayList<>(content.authoredDialogues()),
                new ArrayList<>(content.authoredQuests()),
                new ArrayList<>(content.customItems()),
                new ArrayList<>(content.customMobs()),
                new ArrayList<>(content.customLimbs()),
                new ArrayList<>(content.customNpcs()),
                new ArrayList<>(content.customGatheringNodes()),
                new ArrayList<>(content.customCookingRecipes()),
                new ArrayList<>(content.customCompositeRecipes()),
                new ArrayList<>(),
                blank.spawnX(),
                blank.spawnY()
        );
    }

    private static AuthoredContent loadLegacySharedContent() throws IOException {
        Path editableResourcePath = MapDesignLibrary.LEGACY_SHARED_CONTENT_PATH;
        boolean hasEditableResource = Files.isRegularFile(editableResourcePath);
        AuthoredContent bundledContent = loadLegacySharedContentFrom(hasEditableResource
                ? editableResourcePath
                : Path.of(MapDesignLibrary.CONTENT_RESOURCE_FOLDER, "authored_content.properties"));

        Path dataPath = MapDesignLibrary.DATA_LEGACY_SHARED_CONTENT_PATH;
        if (!Files.isRegularFile(dataPath)) {
            return bundledContent;
        }

        AuthoredContent dataContent = loadLegacySharedContentFrom(dataPath);
        long bundledModified = hasEditableResource
                ? Files.getLastModifiedTime(editableResourcePath).toMillis()
                : Long.MIN_VALUE;
        long dataModified = Files.getLastModifiedTime(dataPath).toMillis();
        return dataModified > bundledModified
                ? mergeContent(bundledContent, dataContent)
                : mergeContent(dataContent, bundledContent);
    }

    private static AuthoredContent loadLegacySharedContentFrom(Path path) throws IOException {
        try {
            MapDesign contentDesign = MapDesignLibrary.loadContentSegment(path);
            return MapDesignLibrary.authoredContentOf(contentDesign);
        } catch (IOException exception) {
            if (Files.isRegularFile(path)) {
                throw exception;
            }
            return emptyContent();
        }
    }

    private static AuthoredContent mergeContent(AuthoredContent base, AuthoredContent override) {
        List<AuthoredDialogue> dialogues = new ArrayList<>(base.authoredDialogues());
        List<AuthoredQuest> quests = new ArrayList<>(base.authoredQuests());
        List<CustomItem> items = new ArrayList<>(base.customItems());
        List<CustomMob> mobs = new ArrayList<>(base.customMobs());
        List<CustomLimb> limbs = new ArrayList<>(base.customLimbs());
        List<CustomNpc> npcs = new ArrayList<>(base.customNpcs());
        List<CustomGatheringNode> gatheringNodes = new ArrayList<>(base.customGatheringNodes());
        List<CustomCookingRecipe> cookingRecipes = new ArrayList<>(base.customCookingRecipes());
        List<CustomCompositeRecipe> compositeRecipes = new ArrayList<>(base.customCompositeRecipes());

        mergeById(dialogues, override.authoredDialogues(), AuthoredDialogue::interactionId);
        mergeById(quests, override.authoredQuests(), AuthoredQuest::questId);
        mergeById(items, override.customItems(), CustomItem::itemId);
        mergeById(mobs, override.customMobs(), CustomMob::mobId);
        mergeById(limbs, override.customLimbs(), CustomLimb::limbId);
        mergeById(npcs, override.customNpcs(), CustomNpc::npcId);
        mergeById(gatheringNodes, override.customGatheringNodes(), CustomGatheringNode::nodeId);
        mergeById(cookingRecipes, override.customCookingRecipes(), CustomCookingRecipe::recipeId);
        mergeById(compositeRecipes, override.customCompositeRecipes(), CustomCompositeRecipe::recipeId);
        return new AuthoredContent(dialogues, quests, items, mobs, limbs, npcs, gatheringNodes, cookingRecipes, compositeRecipes);
    }

    private static <T> void mergeById(List<T> target, List<T> source, Function<T, String> idFunction) {
        for (T entry : source) {
            String id = idFunction.apply(entry);
            target.removeIf(existing -> idFunction.apply(existing).equals(id));
            target.add(entry);
        }
    }

    private static AuthoredContent emptyContent() {
        return new AuthoredContent(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }
}

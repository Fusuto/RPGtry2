package org.main.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private MapDesignContentStore() {
    }

    static AuthoredContent loadSharedContent() throws IOException {
        AuthoredContent defaultContent = defaultAuthoredContent();
        AuthoredContent bundledContent = loadSharedContentFrom(Path.of(MapDesignLibrary.CONTENT_RESOURCE_FOLDER, "authored_content.properties"));
        AuthoredContent dataContent = Files.isRegularFile(MapDesignLibrary.DATA_SHARED_CONTENT_PATH)
                ? loadSharedContentFrom(MapDesignLibrary.DATA_SHARED_CONTENT_PATH)
                : emptyContent();
        return mergeContent(mergeContent(defaultContent, bundledContent), dataContent);
    }

    static void saveSharedContent(AuthoredContent content) throws IOException {
        if (content == null) {
            return;
        }

        MapDesign contentDesign = MapDesignLibrary.createBlank(3, 3, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        contentDesign = new MapDesign(
                contentDesign.width(),
                contentDesign.height(),
                "Authored Content",
                "Shared reusable authored content for the Aether Construction Kit.",
                contentDesign.primaryTheme(),
                contentDesign.alternateTheme(),
                contentDesign.tiles(),
                contentDesign.themeIndexes(),
                contentDesign.placements(),
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
                contentDesign.spawnX(),
                contentDesign.spawnY()
        );
        MapDesignLibrary.save(contentDesign, MapDesignLibrary.SHARED_CONTENT_PATH);
    }

    private static AuthoredContent defaultAuthoredContent() {
        return new AuthoredContent(
                List.of(),
                List.of(),
                MapDesignLibrary.builtInItemDefinitions(),
                MapDesignLibrary.defaultEnemies(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static AuthoredContent loadSharedContentFrom(Path path) throws IOException {
        try {
            MapDesign contentDesign = MapDesignLibrary.load(path);
            return new AuthoredContent(
                    contentDesign.authoredDialogues(),
                    contentDesign.authoredQuests(),
                    contentDesign.customItems(),
                    contentDesign.customMobs(),
                    contentDesign.customLimbs(),
                    contentDesign.customNpcs(),
                    contentDesign.customGatheringNodes(),
                    contentDesign.customCookingRecipes(),
                    contentDesign.customCompositeRecipes()
            );
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
        return new AuthoredContent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}

package org.main.core;

import org.main.content.MapDesignLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.util.Collections;
import java.util.List;

public record GeneratedDungeon(
        DungeonMap dungeonMap,
        List<MapEntity> entities,
        int playerX,
        int playerY,
        List<TileInteraction> tileInteractions,
        List<MapDesignLibrary.AuthoredDialogue> authoredDialogues,
        List<MapDesignLibrary.AuthoredQuest> authoredQuests,
        List<MapDesignLibrary.CustomItem> customItems,
        List<MapDesignLibrary.CustomLimb> customLimbs,
        List<MapDesignLibrary.CustomGatheringNode> customGatheringNodes,
        List<MapDesignLibrary.CustomCookingRecipe> customCookingRecipes,
        List<MapDesignLibrary.CraftingRecipe> craftingRecipes,
        List<MapDesignLibrary.MapTrigger> mapTriggers
) {
    public GeneratedDungeon(DungeonMap dungeonMap, List<MapEntity> entities, int playerX, int playerY) {
        this(dungeonMap, entities, playerX, playerY, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public GeneratedDungeon(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            int playerX,
            int playerY,
            List<TileInteraction> tileInteractions
    ) {
        this(dungeonMap, entities, playerX, playerY, tileInteractions, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public GeneratedDungeon(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            int playerX,
            int playerY,
            List<TileInteraction> tileInteractions,
            List<MapDesignLibrary.AuthoredDialogue> authoredDialogues
    ) {
        this(dungeonMap, entities, playerX, playerY, tileInteractions, authoredDialogues, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public GeneratedDungeon(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            int playerX,
            int playerY,
            List<TileInteraction> tileInteractions,
            List<MapDesignLibrary.AuthoredDialogue> authoredDialogues,
            List<MapDesignLibrary.AuthoredQuest> authoredQuests
    ) {
        this(dungeonMap, entities, playerX, playerY, tileInteractions, authoredDialogues, authoredQuests, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public GeneratedDungeon(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            int playerX,
            int playerY,
            List<TileInteraction> tileInteractions,
            List<MapDesignLibrary.AuthoredDialogue> authoredDialogues,
            List<MapDesignLibrary.AuthoredQuest> authoredQuests,
            List<MapDesignLibrary.CustomItem> customItems,
            List<MapDesignLibrary.CustomLimb> customLimbs
    ) {
        this(dungeonMap, entities, playerX, playerY, tileInteractions, authoredDialogues, authoredQuests, customItems, customLimbs, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public record TileInteraction(int x, int y, String interactionId) {
    }
}

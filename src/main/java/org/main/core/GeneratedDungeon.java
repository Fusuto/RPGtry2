package org.main.core;

import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.util.Collections;
import java.util.List;

public record GeneratedDungeon(
        DungeonMap dungeonMap,
        List<MapEntity> entities,
        int playerX,
        int playerY,
        List<TileInteraction> tileInteractions
) {
    public GeneratedDungeon(DungeonMap dungeonMap, List<MapEntity> entities, int playerX, int playerY) {
        this(dungeonMap, entities, playerX, playerY, Collections.emptyList());
    }

    public record TileInteraction(int x, int y, String interactionId) {
    }
}

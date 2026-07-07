package org.main.core;

import org.main.battle.BattleAssets;
import org.main.battle.BattleRenderer;
import org.main.engine.DungeonRenderer;
import org.main.engine.TextureManager;

public final class RendererBootstrap {
    private RendererBootstrap() {
    }

    public static void configureDefaultRenderers(
            DungeonRenderer dungeonRenderer,
            BattleRenderer battleRenderer
    ) {
        TextureManager textureManager = new TextureManager();
        textureManager.loadFromFolder("src/main/java/org/main/images/building");

        dungeonRenderer.setTextureManager(textureManager);
        dungeonRenderer.setWallTextureTheme("wall", "brick", "stone");
        dungeonRenderer.setDoorTextureTheme("door", "wood", "handle");
        dungeonRenderer.setFloorTextureTheme("floor", "wood", "planks", "wide");

        battleRenderer.setAssets(BattleAssets.loadDefault());
    }
}

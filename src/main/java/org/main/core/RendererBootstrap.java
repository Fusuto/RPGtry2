package org.main.core;

import org.main.battle.BattleAssets;
import org.main.battle.BattleRenderer;
import org.main.content.EnvironmentLibrary;
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
        dungeonRenderer.setEnvironmentThemes(EnvironmentLibrary.STARTER_DUNGEON.getThemes());

        battleRenderer.setAssets(BattleAssets.loadDefault());
    }
}

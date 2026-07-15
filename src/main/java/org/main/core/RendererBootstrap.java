package org.main.core;

import org.main.battle.BattleAssets;
import org.main.battle.BattleRenderer;
import org.main.content.EnvironmentLibrary;
import org.main.engine.DungeonRenderSystem;
import org.main.engine.TextureManager;

public final class RendererBootstrap {
    private RendererBootstrap() {
    }

    public static void configureDefaultRenderers(
            DungeonRenderSystem dungeonRenderSystem,
            BattleRenderer battleRenderer
    ) {
        TextureManager textureManager = new TextureManager();
        textureManager.loadFromFolder("assets/images/building");

        dungeonRenderSystem.setTextureManager(textureManager);
        dungeonRenderSystem.setEnvironmentThemes(EnvironmentLibrary.STARTER_DUNGEON.getThemes());

        battleRenderer.setAssets(BattleAssets.loadDefault());
    }
}

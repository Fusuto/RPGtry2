package org.main.engine;

import org.main.core.PlayerCharacter;

import java.awt.Graphics2D;
import java.util.List;

public interface DungeonRenderBackend {
    void renderDungeon(Graphics2D graphics, DungeonRenderContext context);

    DungeonRenderDebugInfo getDebugInfo();

    void setTextureManager(TextureManager textureManager);

    void setEnvironmentThemes(List<EnvironmentTheme> themes);

    void setPlayerCharacter(PlayerCharacter playerCharacter);
}

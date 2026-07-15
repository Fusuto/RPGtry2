package org.main.engine;

import org.main.core.GameConfiguration;
import org.main.core.PlayerCharacter;

import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;

public class DungeonRenderSystem {
    private static final String BACKEND_JAVA2D = "java2d";

    private final DungeonRenderBackend backend;

    public DungeonRenderSystem() {
        this.backend = createBackend(GameConfiguration.stringValue("renderer.backend", BACKEND_JAVA2D));
    }

    public void render(Graphics2D graphics, DungeonRenderContext context) {
        backend.setPlayerCharacter(context.playerCharacter());
        backend.renderDungeon(graphics, context);
    }

    public DungeonRenderDebugInfo getDebugInfo() {
        return backend.getDebugInfo();
    }

    public void setTextureManager(TextureManager textureManager) {
        backend.setTextureManager(textureManager);
    }

    public void setEnvironmentThemes(List<EnvironmentTheme> themes) {
        backend.setEnvironmentThemes(themes);
    }

    public void setPlayerCharacter(PlayerCharacter playerCharacter) {
        backend.setPlayerCharacter(playerCharacter);
    }

    private DungeonRenderBackend createBackend(String backendName) {
        String normalized = backendName == null ? BACKEND_JAVA2D : backendName.trim().toLowerCase(Locale.ROOT);
        if (!BACKEND_JAVA2D.equals(normalized)) {
            normalized = BACKEND_JAVA2D;
        }

        return new DungeonRenderer();
    }
}

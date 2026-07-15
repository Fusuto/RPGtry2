package org.main.engine;

import org.main.core.PlayerCharacter;

import java.util.List;

public record DungeonRenderContext(
        DungeonMap map,
        List<MapEntity> entities,
        PlayerCharacter playerCharacter,
        int playerX,
        int playerY,
        int direction,
        int viewportWidth,
        int viewportHeight,
        double cameraOffsetForward,
        double cameraOffsetSide,
        double cameraRotationRadians
) {
}

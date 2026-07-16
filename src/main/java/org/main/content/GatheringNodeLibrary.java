package org.main.content;

import org.main.core.Library;
import org.main.engine.AssetLoader;
import org.main.engine.MapEntity;
import org.main.engine.SpriteAnimation;

import java.awt.image.BufferedImage;

public enum GatheringNodeLibrary {
    FISHING_SHOAL(
            "Fishing Shoal",
            "fishing_shoal",
            new String[]{
                    "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance1.png",
                    "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance2.png",
                    "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance3.png"
            },
            260
    ),

    DECORATIVE_SHOAL(
            "Shallow Water",
            null,
            new String[]{
                    "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water4.png"
            },
            1000
    ),

    MINERAL_ROCK_A(
            "Mineral Rock",
            "mineral_rock_basic",
            new String[]{
                    "assets/images/generic/64x64/A_Rock1_Node1.png",
                    "assets/images/generic/64x64/A_Rock1_Node2.png",
                    "assets/images/generic/64x64/A_Rock1_Node3.png"
            },
            1000
    );

    private final String displayName;
    private final String interactionId;
    private final String[] framePaths;
    private final int frameDurationMs;

    GatheringNodeLibrary(String displayName, String interactionId, String[] framePaths, int frameDurationMs) {
        this.displayName = displayName;
        this.interactionId = interactionId;
        this.framePaths = framePaths;
        this.frameDurationMs = frameDurationMs;
    }

    public MapEntity createEntity(int x, int y) {
        MapEntity entity;

        if (this == MINERAL_ROCK_A) {
            entity = new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    getImageForExhaustion(0)
            )
                    .blocksMovement(true)
                    .withVisualScale(1.35);
        } else if (framePaths.length > 1) {
            entity = new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    new SpriteAnimation(loadFrames(), frameDurationMs)
            );
        } else {
            entity = new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    AssetLoader.loadImage(framePaths[0])
            );
        }

        if (interactionId != null && !interactionId.isBlank()) {
            entity.withInteractionId(interactionId);
        }

        return entity;
    }

    public BufferedImage getImageForExhaustion(int exhaustionLevel) {
        int safeIndex = Math.max(0, Math.min(framePaths.length - 1, exhaustionLevel));
        return AssetLoader.loadImage(framePaths[safeIndex]);
    }

    public String getInteractionId() {
        return interactionId;
    }

    public String[] getFramePaths() {
        return framePaths.clone();
    }

    public int getFrameDurationMs() {
        return frameDurationMs;
    }

    private BufferedImage[] loadFrames() {
        BufferedImage[] frames = new BufferedImage[framePaths.length];

        for (int i = 0; i < framePaths.length; i++) {
            frames[i] = AssetLoader.loadImage(framePaths[i]);
        }

        return frames;
    }
}

package org.main.core;

import org.main.engine.AssetLoader;
import org.main.engine.MapEntity;
import org.main.engine.SpriteAnimation;

import java.awt.image.BufferedImage;

public enum CraftingStationType {
    CAMPFIRE(
            "Campfire",
            "campfire_basic",
            VisualMode.GIF,
            "assets/images/generic/Campfire2D.gif",
            120,
            1.65
    ),

    FURNACE(
            "Furnace",
            "furnace_basic",
            VisualMode.NUMBERED_FRAMES,
            "assets/images/resourceObjects/furnace_%d.png",
            180,
            2.65
    ),

    ANVIL(
            "Anvil",
            "anvil_basic",
            VisualMode.STATIC,
            "assets/images/resourceObjects/anvil_1.png",
            1000,
            0.65
    );

    private static final int FURNACE_FIRST_LIT_FRAME = 1;
    private static final int FURNACE_FRAME_COUNT = 3;

    private final String displayName;
    private final String interactionId;
    private final VisualMode visualMode;
    private final String imagePath;
    private final int frameDurationMs;
    private final double visualScale;

    CraftingStationType(
            String displayName,
            String interactionId,
            VisualMode visualMode,
            String imagePath,
            int frameDurationMs,
            double visualScale
    ) {
        this.displayName = displayName;
        this.interactionId = interactionId;
        this.visualMode = visualMode;
        this.imagePath = imagePath;
        this.frameDurationMs = frameDurationMs;
        this.visualScale = visualScale;
    }

    public MapEntity createEntity(int x, int y) {
        MapEntity entity = switch (visualMode) {
            case GIF -> new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    SpriteAnimation.fromGif(imagePath, frameDurationMs)
            );
            case NUMBERED_FRAMES -> new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    createNumberedAnimation()
            );
            case STATIC -> new MapEntity(
                    displayName,
                    Library.EntityType.TRAP,
                    x,
                    y,
                    AssetLoader.loadImage(imagePath)
            );
        };

        return entity
                .withInteractionId(interactionId)
                .withVisualScale(visualScale)
                .blocksMovement(true);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInteractionId() {
        return interactionId;
    }

    private SpriteAnimation createNumberedAnimation() {
        BufferedImage[] frames = new BufferedImage[FURNACE_FRAME_COUNT];

        for (int i = 0; i < frames.length; i++) {
            frames[i] = AssetLoader.loadImage(String.format(imagePath, i + FURNACE_FIRST_LIT_FRAME));
        }

        return new SpriteAnimation(frames, frameDurationMs);
    }

    private enum VisualMode {
        GIF,
        NUMBERED_FRAMES,
        STATIC
    }
}

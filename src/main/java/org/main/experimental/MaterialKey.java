package org.main.experimental;

import java.awt.image.BufferedImage;

record MaterialKey(
        BufferedImage texture,
        int flags,
        LwjglDungeonSceneBuilder.AnimatedTexture animatedTexture
) {

    BufferedImage currentTexture() {
        return animatedTexture == null ? texture : animatedTexture.currentFrame();
    }
}

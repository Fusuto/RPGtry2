package org.main.engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class SpriteAnimation {
    private final BufferedImage[] frames;
    private final int frameDurationMs;

    private int currentFrame = 0;
    private int elapsedMs = 0;

    public SpriteAnimation(BufferedImage[] frames, int frameDurationMs) {
        this.frames = frames;
        this.frameDurationMs = frameDurationMs;
    }

    public static SpriteAnimation fromSpriteSheet(
            String path,
            int startX,
            int startY,
            int frameWidth,
            int frameHeight,
            int frameCount,
            int frameDurationMs
    ) {
        try {
            BufferedImage sheet = ImageIO.read(new File(path));
            BufferedImage[] frames = new BufferedImage[frameCount];

            for (int i = 0; i < frameCount; i++) {
                frames[i] = sheet.getSubimage(
                        startX + i * frameWidth,
                        startY,
                        frameWidth,
                        frameHeight
                );
            }

            return new SpriteAnimation(frames, frameDurationMs);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sprite sheet: " + path, e);
        }
    }

    public void update(int deltaMs) {
        elapsedMs += deltaMs;

        while (elapsedMs >= frameDurationMs) {
            elapsedMs -= frameDurationMs;
            currentFrame = (currentFrame + 1) % frames.length;
        }
    }

    public BufferedImage getCurrentFrame() {
        return frames[currentFrame];
    }
}
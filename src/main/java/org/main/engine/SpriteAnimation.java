package org.main.engine;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        BufferedImage sheet = AssetLoader.loadImage(path);

        if (sheet == null) {
            throw new RuntimeException("Failed to load sprite sheet: " + path);
        }

        try {
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
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to load sprite sheet: " + path, e);
        }
    }

    public static SpriteAnimation fromGif(String path, int frameDurationMs) {
        try (InputStream stream = AssetLoader.openAssetStream(path);
             ImageInputStream imageInput = ImageIO.createImageInputStream(stream)) {
            if (imageInput == null) {
                throw new IOException("Failed to create GIF stream");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF image reader available");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, false);
                int frameCount = reader.getNumImages(true);
                List<BufferedImage> loadedFrames = new ArrayList<>();

                for (int i = 0; i < frameCount; i++) {
                    BufferedImage frame = reader.read(i);
                    if (frame != null) {
                        loadedFrames.add(copyFrame(frame));
                    }
                }

                if (loadedFrames.isEmpty()) {
                    throw new IOException("GIF contained no readable frames");
                }

                return new SpriteAnimation(loadedFrames.toArray(BufferedImage[]::new), frameDurationMs);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GIF animation: " + path, e);
        }
    }

    private static BufferedImage copyFrame(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
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

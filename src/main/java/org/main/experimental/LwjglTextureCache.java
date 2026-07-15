package org.main.experimental;

import org.lwjgl.BufferUtils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

final class LwjglTextureCache {
    private final Map<BufferedImage, Integer> textures = new IdentityHashMap<>();
    private int fallbackTexture;

    int bind(BufferedImage image) {
        if (image == null) {
            return bindFallback();
        }

        Integer existingTexture = textures.get(image);
        if (existingTexture != null) {
            glBindTexture(GL_TEXTURE_2D, existingTexture);
            return existingTexture;
        }

        int textureId = upload(image);
        textures.put(image, textureId);
        glBindTexture(GL_TEXTURE_2D, textureId);
        return textureId;
    }

    int textureCount() {
        return textures.size() + (fallbackTexture == 0 ? 0 : 1);
    }

    void shutdown() {
        for (int textureId : textures.values()) {
            glDeleteTextures(textureId);
        }
        textures.clear();

        if (fallbackTexture != 0) {
            glDeleteTextures(fallbackTexture);
            fallbackTexture = 0;
        }
    }

    private int bindFallback() {
        if (fallbackTexture == 0) {
            fallbackTexture = upload(createFallbackImage());
        }

        glBindTexture(GL_TEXTURE_2D, fallbackTexture);
        return fallbackTexture;
    }

    private int upload(BufferedImage source) {
        BufferedImage image = toArgbImage(source);
        ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xFF));
                pixels.put((byte) ((argb >> 8) & 0xFF));
                pixels.put((byte) (argb & 0xFF));
                pixels.put((byte) ((argb >> 24) & 0xFF));
            }
        }

        pixels.flip();

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,
                image.getWidth(),
                image.getHeight(),
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                pixels
        );
        return textureId;
    }

    private BufferedImage toArgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private BufferedImage createFallbackImage() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF00FF);
        image.setRGB(1, 0, 0xFF111111);
        image.setRGB(0, 1, 0xFF111111);
        image.setRGB(1, 1, 0xFFFF00FF);
        return image;
    }
}

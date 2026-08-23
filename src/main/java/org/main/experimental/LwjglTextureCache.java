package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.main.core.GameConfiguration;
import org.main.engine.AssetRepository;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

final class LwjglTextureCache {
    private final Map<BufferedImage, Integer> textures = new LinkedHashMap<>(128, 0.75f, true);
    private final int[] boundTextureByUnit = new int[8];
    private int fallbackTexture;
    private int whiteTexture;
    private long assetRevision = -1L;
    private long uploadedBytes;
    private long uploadCount;

    LwjglTextureCache() {
        invalidateBindings();
    }

    int bind(BufferedImage image) {
        glActiveTexture(GL_TEXTURE0);
        return bindToActiveUnit(image, 0);
    }

    int bind(BufferedImage image, int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        return bindToActiveUnit(image, textureUnit);
    }

    int bindWhite(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        if (whiteTexture == 0) {
            whiteTexture = upload(createSolidImage(0xFFFFFFFF));
        }
        bindTexture(whiteTexture, textureUnit);
        return whiteTexture;
    }

    void invalidateBindings() {
        java.util.Arrays.fill(boundTextureByUnit, -1);
    }

    private int bindToActiveUnit(BufferedImage image, int textureUnit) {
        invalidateChangedAssets();
        if (image == null) {
            return bindFallback(textureUnit);
        }

        Integer existingTexture = textures.get(image);
        if (existingTexture != null) {
            bindTexture(existingTexture, textureUnit);
            return existingTexture;
        }

        int textureId = upload(image);
        textures.put(image, textureId);
        trimResidency();
        bindTexture(textureId, textureUnit);
        return textureId;
    }

    int textureCount() {
        return textures.size() + (fallbackTexture == 0 ? 0 : 1);
    }

    long uploadedBytes() {
        return uploadedBytes;
    }

    long uploadCount() {
        return uploadCount;
    }

    private void invalidateChangedAssets() {
        long revision = AssetRepository.shared().revision();
        if (assetRevision < 0L) {
            assetRevision = revision;
        } else if (revision != assetRevision) {
            clearAssetTextures();
            assetRevision = revision;
        }
    }

    private void trimResidency() {
        int maximum = Math.max(64, GameConfiguration.intValue(
                "renderer.texture.gpuCache.maxEntries", 1024));
        while (textures.size() > maximum) {
            var iterator = textures.entrySet().iterator();
            Map.Entry<BufferedImage, Integer> eldest = iterator.next();
            glDeleteTextures(eldest.getValue());
            iterator.remove();
        }
    }

    private void clearAssetTextures() {
        for (int textureId : textures.values()) {
            glDeleteTextures(textureId);
        }
        textures.clear();
        invalidateBindings();
    }

    void shutdown() {
        clearAssetTextures();

        if (fallbackTexture != 0) {
            glDeleteTextures(fallbackTexture);
            fallbackTexture = 0;
        }
        if (whiteTexture != 0) {
            glDeleteTextures(whiteTexture);
            whiteTexture = 0;
        }
    }

    private int bindFallback(int textureUnit) {
        if (fallbackTexture == 0) {
            fallbackTexture = upload(createFallbackImage());
        }

        bindTexture(fallbackTexture, textureUnit);
        return fallbackTexture;
    }

    private void bindTexture(int textureId, int textureUnit) {
        int index = Math.max(0, Math.min(boundTextureByUnit.length - 1, textureUnit));
        if (boundTextureByUnit[index] != textureId) {
            glBindTexture(GL_TEXTURE_2D, textureId);
            boundTextureByUnit[index] = textureId;
        }
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
        uploadedBytes += (long) image.getWidth() * image.getHeight() * 4L;
        uploadCount++;
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

    private BufferedImage createSolidImage(int argb) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        return image;
    }
}

package org.main.engine;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.logging.Logger;

public class TextureManager {
    private static final Logger LOGGER = Logger.getLogger(TextureManager.class.getName());

    public record SelectedTexture(
            BufferedImage image,
            boolean defaultTexture
    ) {
    }

    private record TextureKey(
            String location,
            String material1,
            String material2,
            String side
    ) {
    }

    private static class TextureEntry {
        BufferedImage image;
        boolean isDefault;

        TextureEntry(BufferedImage image, boolean isDefault) {
            this.image = image;
            this.isDefault = isDefault;
        }
    }

    private final Map<TextureKey, List<TextureEntry>> textures = new HashMap<>();

    public void loadFromFolder(String folderPath) {
        List<AssetLoader.ImageAsset> imageAssets = AssetLoader.loadImagesFromFolder(folderPath);

        if (imageAssets.isEmpty()) {
            LOGGER.warning(() -> "Texture folder not found or empty: " + folderPath);
            return;
        }

        for (AssetLoader.ImageAsset imageAsset : imageAssets) {
            loadTexture(imageAsset.fileName(), imageAsset.image());
        }
    }

    private void loadTexture(String fileName, BufferedImage image) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if (!lowerName.endsWith(".png")
                && !lowerName.endsWith(".jpg")
                && !lowerName.endsWith(".jpeg")) {
            return;
        }

        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String[] parts = baseName.split("_");

        // Expected:
        // location_material1_material2_side_optionalVariant
        //
        // Example:
        // wall_brick_stone_center_banner
        if (parts.length < 4) {
            LOGGER.warning(() -> "Skipping texture with invalid name: " + fileName);
            return;
        }

        String location = parts[0].toLowerCase(Locale.ROOT);
        String material1 = parts[1].toLowerCase(Locale.ROOT);
        String material2 = parts[2].toLowerCase(Locale.ROOT);
        String side = parts[3].toLowerCase(Locale.ROOT);

        TextureKey key = new TextureKey(location, material1, material2, side);
        boolean isDefault = parts.length == 4;

        if (image == null) {
            LOGGER.warning(() -> "Skipping unreadable texture: " + fileName);
            return;
        }

        textures
                .computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new TextureEntry(image, isDefault));

        LOGGER.fine(() -> "Loaded texture: " + fileName);
    }

    public BufferedImage getTexture(
            String location,
            String material1,
            String material2,
            String side,
            int worldX,
            int worldY
    ) {
        SelectedTexture selectedTexture = getSelectedTexture(location, material1, material2, side, worldX, worldY);
        return selectedTexture == null ? null : selectedTexture.image();
    }

    public SelectedTexture getSelectedTexture(
            String location,
            String material1,
            String material2,
            String side,
            int worldX,
            int worldY
    ) {
        TextureKey key = new TextureKey(
                location.toLowerCase(Locale.ROOT),
                material1.toLowerCase(Locale.ROOT),
                material2.toLowerCase(Locale.ROOT),
                side.toLowerCase(Locale.ROOT)
        );

        List<TextureEntry> variants = textures.get(key);

        if (variants == null || variants.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(
                Objects.hash(location, material1, material2, side, worldX, worldY),
                variants.size()
        );

        TextureEntry entry = variants.get(index);
        return new SelectedTexture(entry.image, entry.isDefault);
    }

    public BufferedImage getDefaultTexture(
            String location,
            String material1,
            String material2,
            String side
    ) {
        TextureKey key = new TextureKey(
                location.toLowerCase(Locale.ROOT),
                material1.toLowerCase(Locale.ROOT),
                material2.toLowerCase(Locale.ROOT),
                side.toLowerCase(Locale.ROOT)
        );

        List<TextureEntry> variants = textures.get(key);

        if (variants == null || variants.isEmpty()) {
            return null;
        }

        for (TextureEntry entry : variants) {
            if (entry.isDefault) {
                return entry.image;
            }
        }

        // Fallback if no true default exists.
        return variants.getFirst().image;
    }
}

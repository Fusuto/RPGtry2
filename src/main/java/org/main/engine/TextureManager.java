package org.main.engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TextureManager {
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
        Path folder = Path.of(folderPath);

        if (!Files.exists(folder)) {
            System.out.println("Texture folder not found: " + folder.toAbsolutePath());
            return;
        }

        try {
            Files.list(folder)
                    .filter(Files::isRegularFile)
                    .forEach(this::loadTextureFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadTextureFile(Path path) {
        String fileName = path.getFileName().toString();
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
            System.out.println("Skipping texture with invalid name: " + fileName);
            return;
        }

        String location = parts[0].toLowerCase(Locale.ROOT);
        String material1 = parts[1].toLowerCase(Locale.ROOT);
        String material2 = parts[2].toLowerCase(Locale.ROOT);
        String side = parts[3].toLowerCase(Locale.ROOT);

        TextureKey key = new TextureKey(location, material1, material2, side);
        boolean isDefault = parts.length == 4;

        try {
            BufferedImage image = ImageIO.read(path.toFile());

            textures
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new TextureEntry(image, isDefault));

            System.out.println("Loaded texture: " + fileName);
        } catch (IOException e) {
            System.out.println("Failed to load texture: " + fileName);
            e.printStackTrace();
        }
    }

    public BufferedImage getTexture(
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

        return variants.get(index).image;
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
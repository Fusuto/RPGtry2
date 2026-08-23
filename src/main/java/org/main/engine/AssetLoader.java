package org.main.engine;

import org.main.pack.ContentResource;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AssetLoader {
    private static final Logger LOGGER = Logger.getLogger(AssetLoader.class.getName());

    private AssetLoader() {
    }

    public static BufferedImage loadImage(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }
        try {
            BufferedImage image = AssetRepository.shared().image(assetPath);
            if (image == null) {
                LOGGER.warning(() -> "Image resource not found: " + assetPath);
            }
            return image;
        } catch (IOException error) {
            LOGGER.log(Level.WARNING, "Failed to load image: " + assetPath, error);
            return null;
        }
    }

    public static AudioInputStream openAudioStream(String assetPath) throws Exception {
        InputStream stream = openAssetStream(assetPath);
        return AudioSystem.getAudioInputStream(new BufferedInputStream(stream));
    }

    public static InputStream openAssetStream(String assetPath) throws IOException {
        InputStream stream = AssetRepository.shared().open(assetPath);
        if (stream == null) {
            throw new IOException("Asset resource not found: " + assetPath);
        }
        return stream;
    }

    public static List<ImageAsset> loadImagesFromFolder(String folderPath) {
        List<ImageAsset> assets = new ArrayList<>();
        Set<String> loadedNames = new LinkedHashSet<>();
        try {
            for (ContentResource resource : AssetRepository.shared().list(folderPath, false)) {
                String fileName = Path.of(resource.logicalPath()).getFileName().toString();
                if (!isImageFile(fileName) || !loadedNames.add(fileName)) {
                    continue;
                }
                BufferedImage image = loadImage(resource.logicalPath());
                if (image != null) {
                    assets.add(new ImageAsset(fileName, image));
                }
            }
        } catch (IOException error) {
            LOGGER.log(Level.WARNING, "Failed to list image folder: " + folderPath, error);
        }
        return assets;
    }

    /**
     * Returns recursive logical paths from every active content mount.
     */
    public static List<String> listAssetFiles(String folderPath) {
        try {
            return AssetRepository.shared().list(folderPath, true).stream()
                    .map(ContentResource::logicalPath)
                    .toList();
        } catch (IOException error) {
            LOGGER.log(Level.WARNING, "Failed to list asset folder: " + folderPath, error);
            return List.of();
        }
    }

    public static List<ContentResource> listAssetEntries(String folderPath, boolean recursive) {
        try {
            return AssetRepository.shared().list(folderPath, recursive);
        } catch (IOException error) {
            LOGGER.log(Level.WARNING, "Failed to list asset folder: " + folderPath, error);
            return List.of();
        }
    }

    public static void refreshContentPacks() throws IOException {
        AssetRepository.shared().reload();
    }

    public static Path generatedSoundsFolder() {
        return ApplicationPaths.dataFolder().resolve("sounds").resolve("generated");
    }

    public static Path assetPacksFolder() {
        return ApplicationPaths.contentPacksFolder().resolve("installed");
    }

    private static boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif");
    }

    public record ImageAsset(String fileName, BufferedImage image) {
    }
}

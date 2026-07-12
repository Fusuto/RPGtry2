package org.main.engine;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AssetLoader {
    public record ImageAsset(String fileName, BufferedImage image) {
    }

    private static final String LEGACY_IMAGE_PREFIX = "src/main/java/org/main/images/";
    private static final String LEGACY_SOUND_PREFIX = "src/main/java/org/main/sounds/";
    private static final String GENERATED_SOUND_PREFIX = "generated/";
    private static final Path DATA_FOLDER = Path.of("data");
    private static final ClassLoader CLASS_LOADER = AssetLoader.class.getClassLoader();

    private AssetLoader() {
    }

    public static BufferedImage loadImage(String assetPath) {
        if (isBlank(assetPath)) {
            return null;
        }

        Path externalPath = resolveExternalPath(assetPath);
        if (externalPath != null && Files.exists(externalPath)) {
            try {
                return ImageIO.read(externalPath.toFile());
            } catch (IOException e) {
                System.out.println("Failed to load image file: " + assetPath);
                e.printStackTrace();
                return null;
            }
        }

        try (InputStream stream = openResourceStream(normalizeResourcePath(assetPath))) {
            if (stream == null) {
                System.out.println("Image resource not found: " + assetPath);
                return null;
            }

            return ImageIO.read(stream);
        } catch (IOException e) {
            System.out.println("Failed to load image resource: " + assetPath);
            e.printStackTrace();
            return null;
        }
    }

    public static AudioInputStream openAudioStream(String assetPath) throws Exception {
        Path externalPath = resolveExternalPath(assetPath);
        if (externalPath != null && Files.exists(externalPath)) {
            return AudioSystem.getAudioInputStream(externalPath.toFile());
        }

        InputStream stream = openResourceStream(normalizeResourcePath(assetPath));
        if (stream == null) {
            throw new IOException("Audio resource not found: " + assetPath);
        }

        return AudioSystem.getAudioInputStream(new BufferedInputStream(stream));
    }

    public static InputStream openAssetStream(String assetPath) throws IOException {
        Path externalPath = resolveExternalPath(assetPath);
        if (externalPath != null && Files.exists(externalPath)) {
            return Files.newInputStream(externalPath);
        }

        InputStream stream = openResourceStream(normalizeResourcePath(assetPath));
        if (stream == null) {
            throw new IOException("Asset resource not found: " + assetPath);
        }

        return new BufferedInputStream(stream);
    }

    public static List<ImageAsset> loadImagesFromFolder(String folderPath) {
        List<ImageAsset> assets = new ArrayList<>();
        Set<String> loadedFileNames = new LinkedHashSet<>();
        Path externalPath = resolveExternalPath(folderPath);

        if (externalPath != null && Files.isDirectory(externalPath)) {
            try {
                Files.list(externalPath)
                        .filter(Files::isRegularFile)
                        .filter(path -> isImageFile(path.getFileName().toString()))
                        .forEach(path -> {
                            try {
                                BufferedImage image = ImageIO.read(path.toFile());
                                String fileName = path.getFileName().toString();
                                assets.add(new ImageAsset(fileName, image));
                                loadedFileNames.add(fileName);
                            } catch (IOException e) {
                                System.out.println("Failed to load image file: " + path);
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                System.out.println("Failed to list image folder: " + folderPath);
                e.printStackTrace();
            }
        }

        for (String resourcePath : listResourceFiles(normalizeResourcePath(folderPath))) {
            String fileName = Path.of(resourcePath).getFileName().toString();
            if (!isImageFile(fileName) || loadedFileNames.contains(fileName)) {
                continue;
            }

            try (InputStream stream = openResourceStream(resourcePath)) {
                if (stream != null) {
                    assets.add(new ImageAsset(fileName, ImageIO.read(stream)));
                    loadedFileNames.add(fileName);
                }
            } catch (IOException e) {
                System.out.println("Failed to load image resource: " + resourcePath);
                e.printStackTrace();
            }
        }

        return assets;
    }

    public static Path generatedSoundsFolder() {
        return ApplicationPaths.dataFolder().resolve("sounds").resolve("generated");
    }

    public static Path assetPacksFolder() {
        return ApplicationPaths.dataFolder().resolve("asset-packs");
    }

    private static Path resolveExternalPath(String assetPath) {
        if (isBlank(assetPath)) {
            return null;
        }

        String normalizedPath = normalizeSlashes(assetPath);
        Path directPath = Path.of(normalizedPath);
        if (directPath.isAbsolute() || Files.exists(directPath)) {
            return directPath;
        }

        if (normalizedPath.startsWith(LEGACY_SOUND_PREFIX + GENERATED_SOUND_PREFIX)) {
            String fileName = normalizedPath.substring((LEGACY_SOUND_PREFIX + GENERATED_SOUND_PREFIX).length());
            return generatedSoundsFolder().resolve(fileName);
        }

        if (normalizedPath.startsWith("sounds/generated/")) {
            return generatedSoundsFolder().resolve(normalizedPath.substring("sounds/generated/".length()));
        }

        if (normalizedPath.startsWith("data/")) {
            return ApplicationPaths.resolveApplicationPath(normalizedPath);
        }

        return directPath;
    }

    private static String normalizeResourcePath(String assetPath) {
        String normalizedPath = normalizeSlashes(assetPath);

        if (normalizedPath.startsWith(LEGACY_IMAGE_PREFIX)) {
            return "assets/images/" + normalizedPath.substring(LEGACY_IMAGE_PREFIX.length());
        }

        if (normalizedPath.startsWith(LEGACY_SOUND_PREFIX)) {
            return "assets/sounds/" + normalizedPath.substring(LEGACY_SOUND_PREFIX.length());
        }

        if (normalizedPath.startsWith("images/")) {
            return "assets/" + normalizedPath;
        }

        if (normalizedPath.startsWith("sounds/")) {
            return "assets/" + normalizedPath;
        }

        if (normalizedPath.startsWith("data/sounds/generated/")) {
            return "assets/sounds/generated/" + normalizedPath.substring("data/sounds/generated/".length());
        }

        return normalizedPath;
    }

    private static List<String> listResourceFiles(String folderPath) {
        Set<String> resourcePaths = new LinkedHashSet<>();
        String normalizedFolder = trimTrailingSlash(folderPath);

        try {
            Enumeration<URL> urls = CLASS_LOADER.getResources(normalizedFolder);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();

                if ("file".equals(url.getProtocol())) {
                    Path folder = Path.of(url.toURI());
                    if (Files.isDirectory(folder)) {
                        Files.list(folder)
                                .filter(Files::isRegularFile)
                                .map(path -> normalizedFolder + "/" + path.getFileName())
                                .forEach(resourcePaths::add);
                    }
                }

                if ("jar".equals(url.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    collectJarEntries(connection.getJarFile(), normalizedFolder, resourcePaths);
                }
            }

            URL ownLocation = AssetLoader.class.getProtectionDomain().getCodeSource().getLocation();
            if (ownLocation != null && ownLocation.getPath().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(Path.of(ownLocation.toURI()).toFile())) {
                    collectJarEntries(jarFile, normalizedFolder, resourcePaths);
                }
            }

            for (Path assetPack : listExternalAssetPacks()) {
                try (JarFile jarFile = new JarFile(assetPack.toFile())) {
                    collectJarEntries(jarFile, normalizedFolder, resourcePaths);
                }
            }
        } catch (IOException | URISyntaxException e) {
            System.out.println("Failed to list resource folder: " + folderPath);
            e.printStackTrace();
        }

        return new ArrayList<>(resourcePaths);
    }

    private static InputStream openResourceStream(String resourcePath) throws IOException {
        for (Path assetPack : listExternalAssetPacks()) {
            try (JarFile jarFile = new JarFile(assetPack.toFile())) {
                JarEntry entry = jarFile.getJarEntry(resourcePath);
                if (entry == null || entry.isDirectory()) {
                    continue;
                }

                try (InputStream stream = jarFile.getInputStream(entry)) {
                    return new ByteArrayInputStream(stream.readAllBytes());
                }
            }
        }

        InputStream bundledStream = CLASS_LOADER.getResourceAsStream(resourcePath);
        if (bundledStream != null) {
            return bundledStream;
        }

        return null;
    }

    private static List<Path> listExternalAssetPacks() {
        if (!Files.isDirectory(assetPacksFolder())) {
            return List.of();
        }

        try {
            return Files.list(assetPacksFolder())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList();
        } catch (IOException e) {
            System.out.println("Failed to list asset packs: " + assetPacksFolder());
            e.printStackTrace();
            return List.of();
        }
    }

    private static void collectJarEntries(JarFile jarFile, String folderPath, Set<String> resourcePaths) {
        String prefix = trimTrailingSlash(folderPath) + "/";
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }

            String remainder = name.substring(prefix.length());
            if (!remainder.contains("/")) {
                resourcePaths.add(name);
            }
        }
    }

    private static boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg");
    }

    private static String trimTrailingSlash(String value) {
        String result = normalizeSlashes(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizeSlashes(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package org.main.engine;

import java.net.URISyntaxException;
import java.nio.file.Path;

public final class ApplicationPaths {
    public static final String APPLICATION_ROOT_PROPERTY = "aether.app.root";
    public static final String DEVELOPMENT_RESOURCES_PROPERTY = "aether.dev.resources";
    public static final String ACTIVE_PROJECT_PROPERTY = "aether.project.root";

    private ApplicationPaths() {
    }

    public static Path applicationFolder() {
        Path configuredRoot = configuredDirectory(APPLICATION_ROOT_PROPERTY);
        if (configuredRoot != null) {
            return configuredRoot;
        }

        String jpackageAppPath = System.getProperty("jpackage.app-path");

        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            Path launcherPath = Path.of(jpackageAppPath).toAbsolutePath();
            Path parent = launcherPath.getParent();

            if (parent != null) {
                return parent;
            }
        }

        try {
            Path codeSource = Path.of(ApplicationPaths.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .toAbsolutePath();

            if (codeSource.toString().toLowerCase().endsWith(".jar")) {
                Path parent = codeSource.getParent();

                if (parent != null) {
                    return parent;
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }

        return Path.of(".").toAbsolutePath().normalize();
    }

    public static Path dataFolder() {
        return applicationFolder().resolve("data");
    }

    public static Path constructionKitFolder() {
        return dataFolder().resolve("construction-kit");
    }

    public static Path contentPacksFolder() {
        return dataFolder().resolve("content-packs");
    }

    /**
     * Returns the explicitly configured repository resource tree, or {@code null}
     * for an installed application. Packaged tools must never infer or create a
     * source tree beside the application.
     */
    public static Path developmentResourcesFolder() {
        return configuredDirectory(DEVELOPMENT_RESOURCES_PROPERTY);
    }

    public static Path activeProjectFolder() {
        return configuredDirectory(ACTIVE_PROJECT_PROPERTY);
    }

    public static Path constructionKitProjectFolder(String packId) {
        String safeId = packId == null ? "" : packId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!safeId.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("Invalid Construction Kit project ID: " + packId);
        }
        return constructionKitFolder().resolve("projects").resolve(safeId).normalize();
    }

    public static Path resolveApplicationPath(String path) {
        Path configuredPath = Path.of(path);

        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        return applicationFolder().resolve(configuredPath).normalize();
    }

    private static Path configuredDirectory(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}

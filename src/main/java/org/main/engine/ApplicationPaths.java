package org.main.engine;

import java.net.URISyntaxException;
import java.nio.file.Path;

public final class ApplicationPaths {
    private ApplicationPaths() {
    }

    public static Path applicationFolder() {
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

    public static Path resolveApplicationPath(String path) {
        Path configuredPath = Path.of(path);

        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        return applicationFolder().resolve(configuredPath).normalize();
    }
}

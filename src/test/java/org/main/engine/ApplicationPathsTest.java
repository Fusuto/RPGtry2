package org.main.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationPathsTest {
    private final String previousRoot = System.getProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY);
    private final String previousResources = System.getProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);

    @AfterEach
    void restoreProperties() {
        restore(ApplicationPaths.APPLICATION_ROOT_PROPERTY, previousRoot);
        restore(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY, previousResources);
    }

    @Test
    void explicitApplicationRootControlsEveryWritableLocation() {
        Path root = Path.of("target", "application-root-test").toAbsolutePath().normalize();
        System.setProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY, root.toString());

        assertEquals(root, ApplicationPaths.applicationFolder());
        assertEquals(root.resolve("data"), ApplicationPaths.dataFolder());
        assertEquals(root.resolve("data/construction-kit"), ApplicationPaths.constructionKitFolder());
        assertEquals(root.resolve("data/content-packs"), ApplicationPaths.contentPacksFolder());
        assertEquals(root.resolve("game/Aether.jar"),
                ApplicationPaths.resolveApplicationPath("game/Aether.jar"));
    }

    @Test
    void developmentResourcesRequireAnExplicitProperty() {
        System.clearProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);
        assertNull(ApplicationPaths.developmentResourcesFolder());

        Path resources = Path.of("src", "main", "resources").toAbsolutePath().normalize();
        System.setProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY, resources.toString());
        assertEquals(resources, ApplicationPaths.developmentResourcesFolder());
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}

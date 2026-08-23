package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.engine.ApplicationPaths;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

class FirstPersonProjectPublicationIntegrationTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void appliedCatalogSurvivesReopenAndFreshConstructionKitRuntime() throws Exception {
        String oldRoot = System.getProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY);
        String oldProject = System.getProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
        String oldDevelopment = System.getProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);
        try {
            System.setProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY, temporaryFolder.toString());
            System.clearProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
            System.clearProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);

            invokeIsolated("writeAndVerify");
            System.clearProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
            invokeIsolated("verifyPersisted");
        } finally {
            restoreProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY, oldRoot);
            restoreProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY, oldProject);
            restoreProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY, oldDevelopment);
        }
    }

    private void invokeIsolated(String methodName) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        URL[] urls = Arrays.stream(classPath.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .map(Path::toUri)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (java.net.MalformedURLException error) {
                        throw new IllegalArgumentException(error);
                    }
                })
                .toArray(URL[]::new);
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> probe = Class.forName(
                    "org.main.content.FirstPersonProjectPublicationProbe", true, loader);
            try {
                probe.getMethod(methodName, Path.class).invoke(null, temporaryFolder);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error fatal) throw fatal;
                throw error;
            }
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}

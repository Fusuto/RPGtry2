package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.pack.ContentPackManifest;
import org.main.pack.ContentPackRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentRepositoryConflictTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void identicalDuplicateIdsStillRequireDeclaredWholeRecordOverrides() throws Exception {
        String duplicateItemId;
        try (ContentPackRegistry coreRegistry = new ContentPackRegistry(
                temporaryFolder.resolve("core-managed"), ContentRepository.class, null, null)) {
            duplicateItemId = new ContentRepository(coreRegistry).snapshot().itemById().keySet()
                    .stream().findFirst().orElseThrow();
        }
        Path project = temporaryFolder.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=duplicate.test
                pack.namespace=duplicate_test
                pack.version=1.0.0
                pack.title=Duplicate Test
                pack.author=Test
                pack.description=Duplicate record validation
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """);
        Path duplicateCatalog = project.resolve(
                "assets/packs/duplicate_test/editor/content/item.properties");
        Files.createDirectories(duplicateCatalog.getParent());
        Properties bundled = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/assets/editor/content/item.properties")) {
            if (input == null) throw new IOException("Bundled item catalog is missing from the test classpath.");
            bundled.load(input);
        }
        int itemCount = Integer.parseInt(bundled.getProperty("item.count"));
        int duplicateIndex = -1;
        for (int index = 0; index < itemCount; index++) {
            if (duplicateItemId.equalsIgnoreCase(bundled.getProperty("item." + index + ".itemId", ""))) {
                duplicateIndex = index;
                break;
            }
        }
        if (duplicateIndex < 0) throw new IOException("Unable to locate duplicate item " + duplicateItemId);
        Properties duplicate = new Properties();
        duplicate.setProperty("item.schemaVersion", bundled.getProperty("item.schemaVersion"));
        duplicate.setProperty("item.count", "1");
        String sourcePrefix = "item." + duplicateIndex + ".";
        for (String key : bundled.stringPropertyNames()) {
            if (key.startsWith(sourcePrefix)) {
                duplicate.setProperty("item.0." + key.substring(sourcePrefix.length()), bundled.getProperty(key));
            }
        }
        try (var output = Files.newOutputStream(duplicateCatalog)) {
            duplicate.store(output, "Identical duplicate item test");
        }

        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("managed"), ContentRepository.class, null, project)) {
            IOException error = assertThrows(IOException.class,
                    () -> new ContentRepository(registry).snapshot());
            assertTrue(error.getMessage().contains("Undeclared item collision"), error.getMessage());
        }
    }
}

package org.main.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.engine.AssetLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopContentPackProviderTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void subscribeUpdateAndUnsubscribeUseReadOnlyFolderMounts() throws Exception {
        Path project = temporaryFolder.resolve("project");
        Path folder = temporaryFolder.resolve("workshop-item");
        Path asset = project.resolve("assets/packs/workshop_test/readme.txt");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "version one");
        writeManifest(project, "1.0.0");
        new PackExportService().exportFolder(project, folder);

        MutableLocations locations = new MutableLocations();
        locations.items.add(new WorkshopPackLocationProvider.WorkshopItemLocation("42", folder));
        WorkshopContentPackProvider workshop = new WorkshopContentPackProvider(locations);
        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("packs"), AssetLoader.class, null, null, List.of(workshop))) {
            assertEquals("1.0.0", registry.snapshot().available().get("workshop.test").manifest().version());
            assertTrue(registry.snapshot().available().get("workshop.test").readOnly());
            registry.setEnabled("workshop.test", true);
            assertEquals("workshop.test", registry.resolve(
                    "assets/packs/workshop_test/readme.txt").orElseThrow().mount().manifest().id());

            writeManifest(project, "1.1.0");
            deleteTree(folder);
            new PackExportService().exportFolder(project, folder);
            registry.reload();
            assertEquals("1.1.0", registry.snapshot().available().get("workshop.test").manifest().version());

            locations.items.clear();
            registry.reload();
            assertFalse(registry.snapshot().available().containsKey("workshop.test"));
        }
    }

    private static void deleteTree(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void writeManifest(Path folder, String version) throws Exception {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=workshop.test
                pack.namespace=workshop_test
                pack.version=%s
                pack.title=Workshop Test
                pack.author=Test
                pack.description=Provider boundary test
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """.formatted(version));
    }

    private static final class MutableLocations implements WorkshopPackLocationProvider {
        private final List<WorkshopItemLocation> items = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<WorkshopItemLocation> subscribedItems() {
            return List.copyOf(items);
        }
    }
}

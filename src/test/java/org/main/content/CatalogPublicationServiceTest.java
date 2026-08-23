package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.pack.ContentPackManifest;
import org.main.pack.ContentPackRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPublicationServiceTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void reloadDiscoversChangedProjectCatalogWithoutManualPackRefresh() throws Exception {
        Path project = createProject();
        Path catalog = materialCatalog(project);
        writeMaterialCatalog(catalog, "before");

        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("reload-managed"), getClass(), null, project)) {
            ContentRepository repository = new ContentRepository(registry);
            assertEquals("before", marker(repository));

            writeMaterialCatalog(catalog, "after");
            repository.reload();

            assertEquals("after", marker(repository));
        }
    }

    @Test
    void failedPublicationRestoresCatalogManifestAndMountedSnapshot() throws Exception {
        Path project = createProject();
        Path manifest = project.resolve(ContentPackManifest.MANIFEST_PATH);
        Path catalog = materialCatalog(project);
        writeMaterialCatalog(catalog, "before");
        byte[] originalCatalog = Files.readAllBytes(catalog);
        byte[] originalManifest = Files.readAllBytes(manifest);

        try (ContentPackRegistry registry = new ContentPackRegistry(
                temporaryFolder.resolve("rollback-managed"), getClass(), null, project)) {
            ContentRepository repository = new ContentRepository(registry);
            assertEquals("before", marker(repository));

            IOException failure = assertThrows(IOException.class,
                    () -> CatalogPublicationService.publishProjectCatalogs(
                            List.of(catalog),
                            project,
                            repository,
                            () -> {
                                writeMaterialCatalog(catalog, "unpublished");
                                Files.writeString(manifest,
                                        new String(originalManifest, StandardCharsets.UTF_8)
                                                .replace("pack.title=Publication Test",
                                                        "pack.title=Unpublished Test"));
                            },
                            () -> {
                                throw new IOException("forced validation failure");
                            }));

            assertTrue(failure.getMessage().contains("forced validation failure"), failure.getMessage());
            assertArrayEquals(originalCatalog, Files.readAllBytes(catalog));
            assertArrayEquals(originalManifest, Files.readAllBytes(manifest));
            assertEquals("before", marker(repository));
        }
    }

    private Path createProject() throws IOException {
        Path project = temporaryFolder.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=publication.test
                pack.namespace=publication_test
                pack.version=1.0.0
                pack.title=Publication Test
                pack.author=Test
                pack.description=Catalog publication test project
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """);
        return project;
    }

    private static Path materialCatalog(Path project) {
        return project.resolve("assets/packs/publication_test/editor/content/material.properties");
    }

    private static void writeMaterialCatalog(Path path, String marker) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                schemaVersion=2
                material.count=0
                test.marker=%s
                """.formatted(marker));
    }

    private static String marker(ContentRepository repository) throws IOException {
        return repository.snapshot().catalogSegments("material.properties").stream()
                .filter(segment -> segment.manifest().id().equals("publication.test"))
                .findFirst()
                .orElseThrow()
                .properties()
                .get("test.marker");
    }
}

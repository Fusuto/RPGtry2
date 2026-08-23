package org.main.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPackRoundTripTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void deterministicExportInstallsAndMounts() throws Exception {
        Path project = createProject("example.content", "example_content");
        Path asset = project.resolve("assets/packs/example_content/readme.txt");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "Aether content pack\n");

        PackExportService exporter = new PackExportService();
        Path first = temporaryFolder.resolve("first.aetherpack");
        Path second = temporaryFolder.resolve("second.aetherpack");
        PackExportService.ExportResult firstResult = exporter.export(project, first);
        PackExportService.ExportResult secondResult = exporter.export(project, second);

        assertEquals(firstResult.digest(), secondResult.digest());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

        Path managed = temporaryFolder.resolve("managed");
        PackInstaller.InstalledPack installed = new PackInstaller(managed).install(first);
        assertTrue(installed.newlyInstalled());
        assertTrue(Files.isRegularFile(installed.path()));

        try (ZipContentMount mount = new ZipContentMount(installed.path(), ContentMount.Origin.INSTALLED)) {
            assertEquals("example.content", mount.manifest().id());
            assertEquals(List.of("assets/packs/example_content/readme.txt"),
                    mount.list("assets/packs/example_content", true));
            assertEquals("Aether content pack\n",
                    new String(mount.open("assets/packs/example_content/readme.txt").readAllBytes()));
        }
    }

    @Test
    void unsafeAndExecutablePathsAreRejected() throws Exception {
        assertThrows(IOException.class, () -> PackPaths.normalize("../escape.txt"));
        assertThrows(IOException.class, () -> PackPaths.normalize("/absolute.txt"));
        assertThrows(IOException.class, () -> PackPaths.normalize("C:/drive.txt"));

        Path project = createProject("example.unsafe", "example_unsafe");
        Path executable = project.resolve("assets/packs/example_unsafe/Injected.class");
        Files.createDirectories(executable.getParent());
        Files.write(executable, new byte[]{1, 2, 3});
        assertThrows(IOException.class,
                () -> new PackExportService().export(project, temporaryFolder.resolve("unsafe.aetherpack")));
        assertFalse(Files.exists(temporaryFolder.resolve("unsafe.aetherpack")));
    }

    @Test
    void assetsOutsideNamespaceAndMissingManifestInventoryAreRejected() throws Exception {
        Path foreign = createProject("example.foreign", "example_foreign");
        Path wrongAsset = foreign.resolve("assets/packs/someone_else/readme.txt");
        Files.createDirectories(wrongAsset.getParent());
        Files.writeString(wrongAsset, "wrong namespace");
        assertThrows(IOException.class,
                () -> new PackExportService().export(foreign, temporaryFolder.resolve("foreign.aetherpack")));

        Path missing = createProject("example.missing", "example_missing");
        Files.writeString(missing.resolve(ContentPackManifest.MANIFEST_PATH),
                Files.readString(missing.resolve(ContentPackManifest.MANIFEST_PATH))
                        + "map.count=1\nmap.0.path=assets/packs/example_missing/editor/maps/missing.properties\n");
        assertThrows(IOException.class,
                () -> new PackExportService().export(missing, temporaryFolder.resolve("missing.aetherpack")));
    }

    @Test
    void packLockRoundTripsExactOrderVersionsAndDigests() throws Exception {
        PackLock expected = new PackLock(1, List.of(
                new PackLock.Entry("example.high", "2.0.0", "A".repeat(64)),
                new PackLock.Entry("example.low", "1.4.3", "B".repeat(64))
        ));
        Properties properties = new Properties();
        expected.writeTo(properties, "packLock.");

        assertEquals(expected, PackLock.readFrom(properties, "packLock."));
        properties.remove("packLock.1.digest");
        assertThrows(IOException.class, () -> PackLock.readFrom(properties, "packLock."));
    }

    @Test
    void supportedGameVersionBoundsAreExactAndSemverValidated() throws Exception {
        Path project = createProject("example.versioned", "example_versioned");
        Path manifestPath = project.resolve(ContentPackManifest.MANIFEST_PATH);
        Files.writeString(manifestPath, Files.readString(manifestPath)
                + "game.minVersion=0.8.0\n"
                + "game.maxVersionExclusive=0.9.0\n");
        ContentPackManifest manifest;
        try (var input = Files.newInputStream(manifestPath)) {
            manifest = ContentPackManifest.read(input);
        }
        assertTrue(manifest.supportsGameVersion("0.8.0"));
        assertTrue(manifest.supportsGameVersion("0.8.99"));
        assertFalse(manifest.supportsGameVersion("0.7.9"));
        assertFalse(manifest.supportsGameVersion("0.9.0"));

        Files.writeString(manifestPath, Files.readString(manifestPath)
                .replace("game.minVersion=0.8.0", "game.minVersion=0.08.0"));
        assertThrows(IOException.class, () -> {
            try (var input = Files.newInputStream(manifestPath)) {
                ContentPackManifest.read(input);
            }
        });
    }

    @Test
    void saveLockSelectsRetainedVersionAndFailedSelectionRollsBack() throws Exception {
        Path project = createProject("example.retained", "example_retained");
        Path asset = project.resolve("assets/packs/example_retained/readme.txt");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "version one");
        PackExportService exporter = new PackExportService();
        Path firstArchive = temporaryFolder.resolve("retained-1.aetherpack");
        PackExportService.ExportResult first = exporter.export(project, firstArchive);

        Path manifest = project.resolve(ContentPackManifest.MANIFEST_PATH);
        Files.writeString(manifest, Files.readString(manifest)
                .replace("pack.version=1.0.0", "pack.version=2.0.0"));
        Files.writeString(asset, "version two");
        Path secondArchive = temporaryFolder.resolve("retained-2.aetherpack");
        exporter.export(project, secondArchive);

        Path managed = temporaryFolder.resolve("retained-managed");
        new PackInstaller(managed).install(firstArchive);
        new PackInstaller(managed).install(secondArchive);
        try (ContentPackRegistry registry = new ContentPackRegistry(
                managed, ContentPackRoundTripTest.class, null, null)) {
            registry.setEnabled("example.retained", true);
            assertEquals("2.0.0", registry.activePackLock().packs().getFirst().version());

            PackLock firstVersion = new PackLock(1, List.of(new PackLock.Entry(
                    "example.retained", "1.0.0", first.digest())));
            registry.requirePackLock(firstVersion);
            assertEquals(firstVersion, registry.activePackLock());

            PackLock unavailable = new PackLock(1, List.of(new PackLock.Entry(
                    "example.retained", "1.0.0", "f".repeat(64))));
            assertThrows(IOException.class, () -> registry.requirePackLock(unavailable));
            assertEquals(firstVersion, registry.activePackLock());
        }
    }

    @Test
    void coreDependencyStaysFixedAndIsNotWrittenIntoUserLoadOrder() throws Exception {
        Path project = createProject("example.core-dependent", "example_core_dependent");
        Path manifest = project.resolve(ContentPackManifest.MANIFEST_PATH);
        Files.writeString(manifest, Files.readString(manifest)
                .replace("dependency.count=0", """
                        dependency.count=1
                        dependency.0.id=aether.core
                        dependency.0.minVersion=0.0.0
                        dependency.0.maxVersionExclusive=
                        """));
        Path asset = project.resolve("assets/packs/example_core_dependent/readme.txt");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "core extension");
        Path archive = temporaryFolder.resolve("core-dependent.aetherpack");
        new PackExportService().export(project, archive);
        Path managed = temporaryFolder.resolve("core-dependent-managed");
        new PackInstaller(managed).install(archive);

        try (ContentPackRegistry registry = new ContentPackRegistry(
                managed, ContentPackRoundTripTest.class, null, null)) {
            registry.setEnabled("example.core-dependent", true);
            assertEquals(List.of("example.core-dependent"), registry.activePackLock().packs().stream()
                    .map(PackLock.Entry::packId).toList());
            registry.reorder(List.of("example.core-dependent"));
            assertEquals("example.core-dependent", registry.snapshot().activeHighestPriorityFirst()
                    .getFirst().manifest().id());
            assertEquals("aether.core", registry.snapshot().activeHighestPriorityFirst()
                    .getLast().manifest().id());
        }
    }

    @Test
    void exportRejectsSupersededContentSchemasBeforeInstallation() throws Exception {
        Path project = createProject("example.schema", "example_schema");
        Path catalog = project.resolve("assets/packs/example_schema/editor/content/item.properties");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog, "item.schemaVersion=7\nitem.count=0\n");
        IOException error = assertThrows(IOException.class,
                () -> new PackExportService().export(project,
                        temporaryFolder.resolve("old-schema.aetherpack")));
        assertTrue(error.getMessage().contains("expected exactly 8"));

        Files.writeString(catalog, "item.schemaVersion=8\nitem.count=0\n");
        assertTrue(Files.isRegularFile(new PackExportService().export(project,
                temporaryFolder.resolve("current-schema.aetherpack")).path()));
    }

    private Path createProject(String packId, String namespace) throws IOException {
        Path project = temporaryFolder.resolve(packId);
        Files.createDirectories(project);
        Files.writeString(project.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=content
                pack.id=%s
                pack.namespace=%s
                pack.version=1.0.0
                pack.title=Example Pack
                pack.author=Test
                pack.description=Round-trip test
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """.formatted(packId, namespace));
        return project;
    }
}

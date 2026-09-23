package org.main.tools;

import org.main.engine.ApplicationPaths;
import org.main.engine.AssetLoader;
import org.main.engine.AssetRepository;
import org.main.pack.ContentPackManifest;
import org.main.pack.PackExportService;
import org.main.pack.PackInstaller;
import org.main.pack.ProjectManifestOverrides;
import org.main.pack.WorkshopExportService;
import org.main.content.WorldManifestLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the writable Construction Kit project; bundled and installed packs stay read-only.
 */
public final class ConstructionKitProjectService {
    private static final String DEFAULT_PROJECT_ID = "aether.user";
    private static final String DEFAULT_NAMESPACE = "aether_user";
    private static final ConstructionKitProjectService INSTANCE = create();

    private final Path projectRoot;
    private final Path resourceRoot;
    private final String namespace;
    private final boolean coreDevelopment;
    private final PackExportService exporter = new PackExportService();
    private final PackInstaller installer = new PackInstaller(ApplicationPaths.contentPacksFolder());
    private final WorkshopExportService workshopExporter = new WorkshopExportService();
    private final WorldProjectCopyService worldCopier = new WorldProjectCopyService();

    private ConstructionKitProjectService(
            Path projectRoot,
            Path resourceRoot,
            String namespace,
            boolean coreDevelopment
    ) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.resourceRoot = resourceRoot.toAbsolutePath().normalize();
        this.namespace = namespace;
        this.coreDevelopment = coreDevelopment;
    }

    public static ConstructionKitProjectService active() {
        return INSTANCE;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path resourceRoot() {
        return resourceRoot;
    }

    public boolean isCoreDevelopment() {
        return coreDevelopment;
    }

    public String namespace() {
        return namespace;
    }

    public Path resourcePath(String logicalPath) {
        String normalized = logicalPath == null ? "" : logicalPath.replace('\\', '/');
        Path result = resourceRoot.resolve(normalized).normalize();
        if (!result.startsWith(resourceRoot)) {
            throw new IllegalArgumentException("Project path escapes the resource root: " + logicalPath);
        }
        return result;
    }

    public Path packAssetPath(String packRelativePath) {
        if (coreDevelopment) {
            return resourcePath("assets/" + packRelativePath);
        }
        return resourcePath("assets/packs/" + namespace + "/" + packRelativePath);
    }

    public EditableWorld editableWorld(Path sourceManifestPath) throws IOException {
        if (sourceManifestPath == null) {
            throw new IOException("No world was selected.");
        }
        Path source = sourceManifestPath.toAbsolutePath().normalize();
        if (Files.isRegularFile(source) && source.startsWith(resourceRoot)) {
            return new EditableWorld(source, false);
        }

        WorldManifestLibrary.WorldManifest sourceWorld = WorldManifestLibrary.load(sourceManifestPath);
        Path destination = packAssetPath("editor/worlds/" + sourceWorld.worldId() + "/"
                + WorldManifestLibrary.MANIFEST_FILE_NAME);
        if (Files.isRegularFile(destination)) {
            return new EditableWorld(destination, false);
        }

        ContentPackManifest sourcePack = sourcePack(sourceManifestPath);
        worldCopier.copy(sourceManifestPath, destination);
        String projectPath = projectRoot.relativize(destination).toString().replace('\\', '/');
        ProjectManifestOverrides.declareWorldResource(projectPath, sourceWorld.worldId(), sourcePack);
        AssetLoader.refreshContentPacks();
        return new EditableWorld(destination, true);
    }

    public PackExportService.ExportResult exportActiveProject(Path destination) throws IOException {
        if (coreDevelopment) {
            throw new IOException("Core development resources are packaged by Maven, not exported as a user pack.");
        }
        return exporter.export(projectRoot, destination);
    }

    public PackInstaller.InstalledPack install(Path archive) throws IOException {
        PackInstaller.InstalledPack installed = installer.install(archive);
        AssetLoader.refreshContentPacks();
        return installed;
    }

    public PackExportService.FolderExportResult exportWorkshopFolder(Path destination) throws IOException {
        if (coreDevelopment) {
            throw new IOException("Core development resources are packaged by Maven, not exported as a Workshop item.");
        }
        return workshopExporter.exportValidatedFolder(projectRoot, destination);
    }

    private static ContentPackManifest sourcePack(Path sourceManifestPath) throws IOException {
        String logicalPath = sourceManifestPath.toString().replace('\\', '/');
        if (!logicalPath.startsWith("assets/")) {
            return null;
        }
        return AssetRepository.shared().registry().resolve(logicalPath)
                .map(resolved -> resolved.mount().manifest())
                .orElse(null);
    }

    private static ConstructionKitProjectService create() {
        Path developmentResources = ApplicationPaths.developmentResourcesFolder();
        if (developmentResources != null) {
            return new ConstructionKitProjectService(
                    developmentResources.getParent() == null ? developmentResources : developmentResources.getParent(),
                    developmentResources,
                    "aether_core",
                    true
            );
        }

        Path configuredProject = ApplicationPaths.activeProjectFolder();
        Path project = configuredProject == null
                ? ApplicationPaths.constructionKitProjectFolder(DEFAULT_PROJECT_ID)
                : configuredProject;
        try {
            ensureProject(project);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
        System.setProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY, project.toString());
        return new ConstructionKitProjectService(project, project, DEFAULT_NAMESPACE, false);
    }

    private static void ensureProject(Path project) throws IOException {
        Path packAssets = project.resolve("assets/packs/" + DEFAULT_NAMESPACE);
        Files.createDirectories(packAssets.resolve("editor/content"));
        Files.createDirectories(packAssets.resolve("editor/maps"));
        Files.createDirectories(packAssets.resolve("editor/worlds"));
        Files.createDirectories(packAssets.resolve("images"));
        Files.createDirectories(packAssets.resolve("sounds"));
        Files.createDirectories(packAssets.resolve("3D"));
        Path manifest = project.resolve(ContentPackManifest.MANIFEST_PATH);
        if (!Files.exists(manifest)) {
            Files.writeString(manifest, """
                    pack.formatVersion=1
                    pack.type=content
                    pack.id=aether.user
                    pack.namespace=aether_user
                    pack.version=1.0.0
                    pack.title=My Aether Content
                    pack.author=Creator
                    pack.description=Content created with the Aether Construction Kit
                    pack.license=All rights reserved
                    contentApiVersion=1
                    dependency.count=0
                    override.count=0
                    """, StandardCharsets.UTF_8);
        }
    }

    public record EditableWorld(Path manifestPath, boolean copied) {
    }
}

package org.main.pack;

import java.io.IOException;
import java.nio.file.Path;

/**
 * SDK-independent validation/staging boundary for a future Steam Workshop adapter.
 */
public final class WorkshopExportService {
    private final PackExportService exportService = new PackExportService();

    public Path validateStagingFolder(Path projectFolder) throws IOException {
        exportService.validateProject(projectFolder);
        return projectFolder.toAbsolutePath().normalize();
    }

    public PackExportService.FolderExportResult exportValidatedFolder(
            Path projectFolder,
            Path destination
    ) throws IOException {
        PackExportService.FolderExportResult result = exportService.exportFolder(projectFolder, destination);
        exportService.validateProject(result.path());
        return result;
    }
}

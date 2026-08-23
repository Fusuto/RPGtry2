package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Adapts provider-supplied Workshop folders into the same read-only mounts used by local packs.
 */
public final class WorkshopContentPackProvider implements ContentPackProvider {
    private static final long MAX_MANIFEST_BYTES = 1024 * 1024;

    private final WorkshopPackLocationProvider locations;
    private volatile List<String> diagnostics = List.of();

    public WorkshopContentPackProvider(WorkshopPackLocationProvider locations) {
        this.locations = locations == null ? WorkshopPackLocationProvider.unavailable() : locations;
    }

    @Override
    public String providerId() {
        return "steam-workshop";
    }

    @Override
    public List<ContentMount> discover() throws IOException {
        if (!locations.available()) {
            diagnostics = List.of();
            return List.of();
        }
        List<ContentMount> mounts = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<WorkshopPackLocationProvider.WorkshopItemLocation> items = new ArrayList<>(locations.subscribedItems());
        items.sort(Comparator.comparing(WorkshopPackLocationProvider.WorkshopItemLocation::itemId));
        for (WorkshopPackLocationProvider.WorkshopItemLocation item : items) {
            Path folder = item.folder();
            try {
                if (folder == null || !Files.isDirectory(folder) || Files.isSymbolicLink(folder)) {
                    throw new IOException("installed item folder is missing or is a symlink");
                }
                Path manifestPath = folder.resolve(ContentPackManifest.MANIFEST_PATH);
                if (!Files.isRegularFile(manifestPath) || Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
                    throw new IOException("manifest is missing or too large");
                }
                ContentPackManifest manifest;
                try (InputStream input = Files.newInputStream(manifestPath)) {
                    manifest = ContentPackManifest.read(input);
                }
                new PackExportService().validateProject(folder);
                if (!Files.isRegularFile(folder.resolve(PackInstaller.CHECKSUM_PATH))) {
                    throw new IOException("Workshop item is missing " + PackInstaller.CHECKSUM_PATH);
                }
                mounts.add(new DirectoryContentMount(folder, manifest, ContentMount.Origin.WORKSHOP, true));
            } catch (IOException | RuntimeException error) {
                errors.add("Workshop item " + item.itemId() + " is invalid: " + error.getMessage());
            }
        }
        diagnostics = List.copyOf(errors);
        return List.copyOf(mounts);
    }

    @Override
    public List<String> diagnostics() {
        return diagnostics;
    }

    @Override
    public void refresh() throws IOException {
        locations.refresh();
    }
}

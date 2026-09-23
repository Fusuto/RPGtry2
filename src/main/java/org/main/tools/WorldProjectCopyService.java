package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.content.WorldManifestLibrary.ChunkCoordinate;
import org.main.content.WorldManifestLibrary.WorldManifest;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Transactionally materializes a mounted world as normal writable project files.
 */
public final class WorldProjectCopyService {
    public WorldManifest copy(Path sourceManifestPath, Path destinationManifestPath) throws IOException {
        if (sourceManifestPath == null || destinationManifestPath == null) {
            throw new IOException("Source and destination world manifests are required.");
        }
        WorldManifest source = WorldManifestLibrary.load(sourceManifestPath);
        Path destination = destinationManifestPath.toAbsolutePath().normalize();
        Path destinationFolder = destination.getParent();
        if (destinationFolder == null || destination.getFileName() == null) {
            throw new IOException("The editable world needs a project destination folder.");
        }
        if (Files.exists(destinationFolder)) {
            throw new IOException("An editable world already exists at " + destinationFolder + ".");
        }
        Path worldsFolder = destinationFolder.getParent();
        if (worldsFolder == null) {
            throw new IOException("The editable world destination is unsafe.");
        }
        Files.createDirectories(worldsFolder);
        Path staging = worldsFolder.resolve("." + destinationFolder.getFileName()
                + ".import-" + UUID.randomUUID()).normalize();
        if (!staging.getParent().equals(worldsFolder) || Files.exists(staging)) {
            throw new IOException("The editable world staging path is unsafe.");
        }

        try {
            Files.createDirectory(staging);
            Path stagedManifest = staging.resolve(destination.getFileName().toString());
            Map<ChunkCoordinate, String> copiedChunks = new LinkedHashMap<>();
            for (Map.Entry<ChunkCoordinate, String> entry : source.chunks().entrySet()) {
                ChunkCoordinate coordinate = entry.getKey();
                Path sourceChunk = WorldManifestLibrary.resolveChunkPath(sourceManifestPath, entry.getValue());
                if (sourceChunk == null) {
                    throw new IOException("World chunk " + coordinate + " has no source path.");
                }
                Path stagedChunk = WorldManifestLibrary.defaultChunkPath(stagedManifest, coordinate);
                MapDesignLibrary.save(MapDesignLibrary.loadGeometryOnly(sourceChunk), stagedChunk);
                copiedChunks.put(coordinate,
                        WorldManifestLibrary.relativeChunkPath(stagedManifest, stagedChunk));
            }
            WorldManifest copied = new WorldManifest(
                    source.formatVersion(),
                    source.worldId(),
                    source.displayName(),
                    source.description(),
                    source.chunkWidth(),
                    source.chunkHeight(),
                    source.startX(),
                    source.startY(),
                    copiedChunks);
            WorldManifestLibrary.save(copied, stagedManifest);
            try {
                Files.move(staging, destinationFolder, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staging, destinationFolder);
            }
            return copied;
        } catch (IOException | RuntimeException error) {
            deleteStaging(staging, worldsFolder);
            throw error;
        }
    }

    private static void deleteStaging(Path staging, Path expectedParent) {
        if (staging == null || !expectedParent.equals(staging.getParent()) || !Files.exists(staging)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }
}

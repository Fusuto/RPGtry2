package org.main.content;

import org.main.engine.ApplicationPaths;
import org.main.pack.ContentPackManifest;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactionally publishes authored auxiliary catalogs and their project
 * manifest declarations into a freshly mounted content snapshot.
 */
public final class CatalogPublicationService {
    @FunctionalInterface
    public interface CheckedWriter {
        void write() throws IOException;
    }

    @FunctionalInterface
    public interface CheckedLoader<T> {
        T load() throws IOException;
    }

    private CatalogPublicationService() {
    }

    public static synchronized <T> T publishProjectCatalogs(
            Collection<Path> catalogFiles,
            CheckedWriter writer,
            CheckedLoader<T> loadAndValidate
    ) throws IOException {
        return publishProjectCatalogs(
                catalogFiles,
                ApplicationPaths.activeProjectFolder(),
                ContentRepository.shared(),
                writer,
                loadAndValidate);
    }

    static synchronized <T> T publishProjectCatalogs(
            Collection<Path> catalogFiles,
            Path activeProject,
            ContentRepository repository,
            CheckedWriter writer,
            CheckedLoader<T> loadAndValidate
    ) throws IOException {
        if (catalogFiles == null || catalogFiles.isEmpty()) {
            throw new IOException("Catalog publication requires at least one managed file.");
        }
        if (repository == null || writer == null || loadAndValidate == null) {
            throw new IOException("Catalog publication services are incomplete.");
        }

        Map<Path, FileBackup> backups = new LinkedHashMap<>();
        for (Path catalogFile : catalogFiles) {
            addBackup(backups, catalogFile);
        }
        if (activeProject != null) {
            addBackup(backups, activeProject.resolve(ContentPackManifest.MANIFEST_PATH));
        }

        try {
            writer.write();
            repository.reload();
            return loadAndValidate.load();
        } catch (IOException | RuntimeException publicationFailure) {
            String publicationMessage = "Catalog publication failed: " + rootMessage(publicationFailure);
            IOException rollbackFailure = restore(backups);
            try {
                repository.reload();
            } catch (IOException | RuntimeException refreshFailure) {
                if (rollbackFailure == null) {
                    rollbackFailure = new IOException("Restored catalogs could not be republished.", refreshFailure);
                } else {
                    rollbackFailure.addSuppressed(refreshFailure);
                }
            }
            IOException failure = new IOException(rollbackFailure == null
                    ? publicationMessage
                    : publicationMessage + " Rollback also failed: " + rootMessage(rollbackFailure),
                    publicationFailure);
            if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
            throw failure;
        }
    }

    private static void addBackup(Map<Path, FileBackup> backups, Path path) throws IOException {
        if (path == null) {
            throw new IOException("Catalog publication path is missing.");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (backups.containsKey(normalized)) {
            return;
        }
        boolean existed = Files.exists(normalized);
        byte[] contents = existed ? Files.readAllBytes(normalized) : null;
        backups.put(normalized, new FileBackup(existed, contents));
    }

    private static IOException restore(Map<Path, FileBackup> backups) {
        IOException failure = null;
        for (Map.Entry<Path, FileBackup> entry : backups.entrySet()) {
            try {
                restore(entry.getKey(), entry.getValue());
            } catch (IOException error) {
                if (failure == null) {
                    failure = new IOException("One or more catalog files could not be restored.");
                }
                failure.addSuppressed(error);
            }
        }
        return failure;
    }

    private static void restore(Path path, FileBackup backup) throws IOException {
        if (!backup.existed()) {
            Files.deleteIfExists(path);
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".rollback-" + UUID.randomUUID());
        try {
            Files.write(temporary, backup.contents());
            try {
                Files.move(temporary, path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private record FileBackup(boolean existed, byte[] contents) {
        private FileBackup {
            contents = contents == null ? null : contents.clone();
        }

        @Override
        public byte[] contents() {
            return contents == null ? null : contents.clone();
        }
    }
}

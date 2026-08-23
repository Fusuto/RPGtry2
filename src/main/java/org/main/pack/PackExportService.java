package org.main.pack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackExportService {
    public ExportResult export(Path projectFolder, Path destination) throws IOException {
        Path root = projectFolder.toAbsolutePath().normalize();
        Path manifestPath = root.resolve(ContentPackManifest.MANIFEST_PATH);
        ContentPackManifest manifest;
        try (InputStream input = Files.newInputStream(manifestPath)) {
            manifest = ContentPackManifest.read(input);
        }

        Map<String, byte[]> files = readProjectFiles(root, manifest);
        byte[] checksums = checksumFile(files);
        files.put(PackInstaller.CHECKSUM_PATH, checksums);

        Path target = destination.toAbsolutePath().normalize();
        if (!target.getFileName().toString().endsWith(".aetherpack")) {
            target = target.resolveSibling(target.getFileName() + ".aetherpack");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".new");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
            output.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0L);
                output.putNextEntry(entry);
                output.write(file.getValue());
                output.closeEntry();
            }
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return new ExportResult(manifest, target, PackInstaller.sha256(target), files.size());
    }

    public void validateProject(Path projectFolder) throws IOException {
        Path root = projectFolder.toAbsolutePath().normalize();
        ContentPackManifest manifest;
        try (InputStream input = Files.newInputStream(root.resolve(ContentPackManifest.MANIFEST_PATH))) {
            manifest = ContentPackManifest.read(input);
        }
        Map<String, byte[]> files = readProjectFiles(root, manifest);
        validateExistingChecksums(root, files);
    }

    public FolderExportResult exportFolder(Path projectFolder, Path destination) throws IOException {
        Path root = projectFolder.toAbsolutePath().normalize();
        ContentPackManifest manifest;
        try (InputStream input = Files.newInputStream(root.resolve(ContentPackManifest.MANIFEST_PATH))) {
            manifest = ContentPackManifest.read(input);
        }
        Map<String, byte[]> files = readProjectFiles(root, manifest);
        files.put(PackInstaller.CHECKSUM_PATH, checksumFile(files));
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Workshop export folder already exists: " + target);
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Workshop export folder needs a parent directory.");
        Files.createDirectories(parent);
        Path staging = parent.resolve("." + target.getFileName() + ".new-" + UUID.randomUUID()).normalize();
        if (!staging.getParent().equals(parent)) throw new IOException("Unsafe Workshop staging path.");
        try {
            Files.createDirectory(staging);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path output = PackPaths.resolve(staging, file.getKey());
                Files.createDirectories(output.getParent());
                Files.write(output, file.getValue());
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(staging, target);
            }
        } catch (IOException failure) {
            deleteStaging(staging, parent);
            throw failure;
        }
        return new FolderExportResult(manifest, target, files.size());
    }

    private static Map<String, byte[]> readProjectFiles(
            Path root,
            ContentPackManifest manifest
    ) throws IOException {
        List<Path> walked;
        try (Stream<Path> stream = Files.walk(root)) {
            walked = stream.sorted(Comparator.comparing(path -> root.relativize(path).toString())).toList();
        }
        for (Path path : walked) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Content-pack projects cannot contain symlinks: " + path);
            }
        }
        List<Path> paths = walked.stream().filter(Files::isRegularFile).toList();
        if (paths.size() > PackInstaller.MAX_ENTRIES) {
            throw new IOException("Project exceeds the content-pack entry limit.");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        Set<String> caseFoldedPaths = new HashSet<>();
        long expandedBytes = 0;
        for (Path path : paths) {
            String logicalPath = PackPaths.normalize(root.relativize(path).toString());
            if (!caseFoldedPaths.add(logicalPath.toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate or case-colliding project path: " + logicalPath);
            }
            if (logicalPath.equals(PackInstaller.CHECKSUM_PATH)
                    || logicalPath.equals("META-INF/aether/signature.ed25519")) {
                continue;
            }
            PackInstaller.validateAllowedPath(logicalPath, manifest);
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > PackInstaller.MAX_ENTRY_BYTES) {
                throw new IOException("Project file exceeds the pack-entry size limit: " + logicalPath);
            }
            PackDataSchemas.validate(logicalPath, bytes);
            expandedBytes += bytes.length;
            if (expandedBytes > PackInstaller.MAX_EXPANDED_BYTES) {
                throw new IOException("Project exceeds the expanded content-pack size limit.");
            }
            result.put(logicalPath, bytes);
        }
        if (!result.containsKey(ContentPackManifest.MANIFEST_PATH)) {
            throw new IOException("Project is missing its content-pack manifest.");
        }
        Set<String> missingDeclared = new HashSet<>(manifest.declaredResources());
        missingDeclared.removeAll(result.keySet());
        if (!missingDeclared.isEmpty()) {
            throw new IOException("Manifest declares missing project resources: " + missingDeclared);
        }
        return result;
    }

    private static byte[] checksumFile(Map<String, byte[]> files) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            result.append(PackInstaller.sha256(new ByteArrayInputStream(file.getValue())))
                    .append("  ").append(file.getKey()).append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void validateExistingChecksums(Path root, Map<String, byte[]> files) throws IOException {
        Path checksum = root.resolve(PackInstaller.CHECKSUM_PATH);
        if (!Files.exists(checksum)) return;
        if (!Files.isRegularFile(checksum) || Files.isSymbolicLink(checksum)
                || !Arrays.equals(Files.readAllBytes(checksum), checksumFile(files))) {
            throw new IOException("Workshop folder checksums are missing, stale, or invalid.");
        }
    }

    private static void deleteStaging(Path staging, Path expectedParent) {
        if (!staging.getParent().equals(expectedParent) || !Files.exists(staging)) return;
        try (Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    public record ExportResult(ContentPackManifest manifest, Path path, String digest, int entryCount) {
    }

    public record FolderExportResult(ContentPackManifest manifest, Path path, int entryCount) {
    }
}

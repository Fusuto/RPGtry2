package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipContentMount implements ContentMount {
    private final Path archive;
    private final ZipFile zipFile;
    private final ContentPackManifest manifest;
    private final Origin origin;
    private final Map<String, ZipEntry> entries;
    private final String contentDigest;

    public ZipContentMount(Path archive, Origin origin) throws IOException {
        this(archive, origin, null);
    }

    ZipContentMount(Path archive, Origin origin, ContentPackManifest suppliedManifest) throws IOException {
        this.archive = archive.toAbsolutePath().normalize();
        this.origin = origin;
        this.zipFile = new ZipFile(this.archive.toFile());
        try {
            this.entries = index(zipFile);
            if (suppliedManifest != null) {
                this.manifest = suppliedManifest;
            } else {
                ZipEntry manifestEntry = entries.get(ContentPackManifest.MANIFEST_PATH);
                if (manifestEntry == null) {
                    throw new IOException("Content pack has no " + ContentPackManifest.MANIFEST_PATH + ": " + archive);
                }
                try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                    this.manifest = ContentPackManifest.read(input);
                }
            }
            String fileName = this.archive.getFileName().toString();
            String candidate = fileName.endsWith(".aetherpack")
                    ? fileName.substring(0, fileName.length() - ".aetherpack".length()) : "";
            this.contentDigest = candidate.matches("[0-9a-fA-F]{64}")
                    ? candidate.toLowerCase(Locale.ROOT) : PackInstaller.sha256(this.archive);
        } catch (IOException | RuntimeException error) {
            zipFile.close();
            throw error;
        }
    }

    @Override
    public ContentPackManifest manifest() {
        return manifest;
    }

    @Override
    public String revision() {
        try {
            return Files.size(archive) + ":" + Files.getLastModifiedTime(archive).toMillis();
        } catch (IOException error) {
            return "missing:" + archive;
        }
    }

    @Override
    public String contentDigest() {
        return contentDigest;
    }

    @Override
    public Origin origin() {
        return origin;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public List<String> list(String prefix, boolean recursive) throws IOException {
        String normalizedPrefix = PackPaths.normalizePrefix(prefix);
        String matchPrefix = normalizedPrefix.isBlank() ? "" : normalizedPrefix + "/";
        List<String> result = new ArrayList<>();
        for (String name : entries.keySet()) {
            if (!name.startsWith(matchPrefix)) {
                continue;
            }
            String remainder = name.substring(matchPrefix.length());
            if (recursive || !remainder.contains("/")) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public InputStream open(String logicalPath) throws IOException {
        ZipEntry entry = entries.get(PackPaths.normalize(logicalPath));
        if (entry == null) {
            return null;
        }
        return zipFile.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private static Map<String, ZipEntry> index(ZipFile zipFile) throws IOException {
        List<ZipEntry> values = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (!entry.isDirectory()) {
                values.add(entry);
            }
        }
        values.sort(Comparator.comparing(entry -> entry.getName().toLowerCase(Locale.ROOT)));
        Map<String, ZipEntry> result = new LinkedHashMap<>();
        for (ZipEntry entry : values) {
            String normalized = PackPaths.normalize(entry.getName());
            String caseFolded = normalized.toLowerCase(Locale.ROOT);
            boolean duplicate = result.keySet().stream()
                    .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(caseFolded));
            if (duplicate) {
                throw new IOException("Duplicate or case-colliding content-pack path: " + normalized);
            }
            result.put(normalized, entry);
        }
        return Map.copyOf(result);
    }
}

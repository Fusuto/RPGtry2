package org.main.pack;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackInstaller {
    public static final String CHECKSUM_PATH = "META-INF/aether/checksums.sha256";
    public static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024;
    public static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;
    public static final int MAX_ENTRIES = 50_000;
    public static final double MAX_EXPANSION_RATIO = 200.0;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_CHECKSUM_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CENTRAL_DIRECTORY_BYTES = 64 * 1024 * 1024;

    private static final Set<String> CONTENT_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "wav", "aiff", "au", "glb", "fbx",
            "properties", "txt", "md"
    );
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "af", "psd", "pxcp", "blend", "blend1", "zip"
    );

    private final Path root;

    public PackInstaller(Path contentPacksFolder) {
        this.root = contentPacksFolder.toAbsolutePath().normalize();
    }

    public InstalledPack install(Path sourceArchive) throws IOException {
        Path source = sourceArchive.toAbsolutePath().normalize();
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".aetherpack")) {
            throw new IOException("Content packs must use the .aetherpack extension.");
        }
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_ARCHIVE_BYTES) {
            throw new IOException("Content pack is missing or exceeds the archive size limit: " + source);
        }

        Validation validation = validate(source);
        String digest = sha256(source);
        Path versionFolder = root.resolve("installed")
                .resolve(validation.manifest().id())
                .resolve(validation.manifest().version())
                .normalize();
        ensureWithin(root.resolve("installed"), versionFolder);
        Files.createDirectories(versionFolder);

        try (var siblings = Files.list(versionFolder)) {
            List<Path> existing = siblings.filter(Files::isRegularFile).toList();
            if (!existing.isEmpty()) {
                Path exact = versionFolder.resolve(digest + ".aetherpack");
                if (Files.isRegularFile(exact)) {
                    return new InstalledPack(validation.manifest(), exact, digest, false);
                }
                throw new IOException("Pack " + validation.manifest().id() + " version "
                        + validation.manifest().version() + " is already installed with a different digest.");
            }
        }

        Path incoming = root.resolve("incoming");
        Files.createDirectories(incoming);
        Path staged = incoming.resolve(UUID.randomUUID() + ".aetherpack");
        Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
        Path target = versionFolder.resolve(digest + ".aetherpack");
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
        return new InstalledPack(validation.manifest(), target, digest, true);
    }

    public Validation validate(Path archive) throws IOException {
        rejectSymlinkEntries(archive);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipEntry manifestEntry = zipFile.getEntry(ContentPackManifest.MANIFEST_PATH);
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IOException("Content pack is missing " + ContentPackManifest.MANIFEST_PATH + ".");
            }
            requireMetadataSize(manifestEntry, MAX_MANIFEST_BYTES);
            ContentPackManifest manifest;
            try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                manifest = ContentPackManifest.read(new java.io.ByteArrayInputStream(
                        readLimited(input, MAX_MANIFEST_BYTES, ContentPackManifest.MANIFEST_PATH)));
            }

            List<ZipEntry> entries = entries(zipFile);
            Map<String, String> expectedChecksums = readChecksums(zipFile);
            Set<String> actualFiles = new HashSet<>();
            Set<String> caseFoldedPaths = new HashSet<>();
            long expandedBytes = 0;
            for (ZipEntry entry : entries) {
                String path = PackPaths.normalize(entry.getName());
                String caseFolded = path.toLowerCase(Locale.ROOT);
                if (!caseFoldedPaths.add(caseFolded)) {
                    throw new IOException("Duplicate or case-colliding pack path: " + path);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                actualFiles.add(path);
                long size = entry.getSize();
                if (size < 0 || size > MAX_ENTRY_BYTES) {
                    throw new IOException("Pack entry exceeds the size limit: " + path);
                }
                expandedBytes = Math.addExact(expandedBytes, size);
                if (expandedBytes > MAX_EXPANDED_BYTES) {
                    throw new IOException("Content pack exceeds the expanded size limit.");
                }
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > 0 && size / (double) compressedSize > MAX_EXPANSION_RATIO) {
                    throw new IOException("Suspicious compression ratio for pack entry: " + path);
                }
                validateAllowedPath(path, manifest.type());
                validateNamespace(path, manifest);
                if (!path.equals(CHECKSUM_PATH)) {
                    String expected = expectedChecksums.get(path);
                    if (expected == null) {
                        throw new IOException("Pack checksum list does not include: " + path);
                    }
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        HashResult hash = sha256Bounded(input, MAX_ENTRY_BYTES);
                        if (hash.bytesRead() != size) {
                            throw new IOException("Pack entry expanded size differs from its header: " + path);
                        }
                        String actual = hash.digest();
                        if (!actual.equalsIgnoreCase(expected)) {
                            throw new IOException("Checksum mismatch for pack entry: " + path);
                        }
                    }
                    if (path.toLowerCase(Locale.ROOT).endsWith(".properties")) {
                        try (InputStream input = zipFile.getInputStream(entry)) {
                            PackDataSchemas.validate(path,
                                    readLimited(input, PackDataSchemas.MAX_PROPERTIES_BYTES, path));
                        }
                    }
                }
            }
            Set<String> listed = new HashSet<>(expectedChecksums.keySet());
            listed.removeAll(actualFiles);
            if (!listed.isEmpty()) {
                throw new IOException("Checksum list references missing pack entries: " + listed);
            }
            Set<String> missingDeclared = new HashSet<>(manifest.declaredResources());
            missingDeclared.removeAll(actualFiles);
            if (!missingDeclared.isEmpty()) {
                throw new IOException("Manifest declares missing pack resources: " + missingDeclared);
            }
            return new Validation(manifest, entries.size(), expandedBytes);
        } catch (ArithmeticException error) {
            throw new IOException("Content pack expanded size overflow.", error);
        }
    }

    private static void validateNamespace(String path, ContentPackManifest manifest) throws IOException {
        if (!path.startsWith("assets/") || manifest.type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
            return;
        }
        String namespaceRoot = "assets/packs/" + manifest.namespace() + "/";
        if (!path.startsWith(namespaceRoot)) {
            throw new IOException("Content-pack assets must be inside " + namespaceRoot + ": " + path);
        }
    }

    static void validateAllowedPath(String path, ContentPackManifest.PackType type) throws IOException {
        if (path.equals(ContentPackManifest.MANIFEST_PATH) || path.equals(CHECKSUM_PATH)
                || path.equals("META-INF/aether/signature.ed25519")) {
            return;
        }
        if (path.equals("preview.png") || path.startsWith("licenses/")) {
            requireExtension(path, CONTENT_EXTENSIONS);
            return;
        }
        if (path.startsWith("assets/")) {
            requireExtension(path, CONTENT_EXTENSIONS);
            return;
        }
        if (type == ContentPackManifest.PackType.AUTHORING_SOURCE && path.startsWith("sources/")) {
            Set<String> allowed = new HashSet<>(CONTENT_EXTENSIONS);
            allowed.addAll(SOURCE_EXTENSIONS);
            requireExtension(path, allowed);
            return;
        }
        throw new IOException("Unsupported content-pack path: " + path);
    }

    static void validateAllowedPath(String path, ContentPackManifest manifest) throws IOException {
        validateAllowedPath(path, manifest.type());
        validateNamespace(path, manifest);
    }

    private static List<ZipEntry> entries(ZipFile zipFile) throws IOException {
        List<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            entries.add(enumeration.nextElement());
            if (entries.size() > MAX_ENTRIES) {
                throw new IOException("Content pack exceeds the entry-count limit.");
            }
        }
        entries.sort(Comparator.comparing(ZipEntry::getName));
        return entries;
    }

    private static Map<String, String> readChecksums(ZipFile zipFile) throws IOException {
        ZipEntry checksumEntry = zipFile.getEntry(CHECKSUM_PATH);
        if (checksumEntry == null) {
            throw new IOException("Content pack is missing " + CHECKSUM_PATH + ".");
        }
        requireMetadataSize(checksumEntry, MAX_CHECKSUM_BYTES);
        Map<String, String> result = new HashMap<>();
        try (InputStream input = zipFile.getInputStream(checksumEntry)) {
            for (String line : new String(readLimited(input, MAX_CHECKSUM_BYTES, CHECKSUM_PATH),
                    StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                int separator = line.indexOf("  ");
                if (separator != 64) {
                    throw new IOException("Malformed pack checksum line: " + line);
                }
                String hash = line.substring(0, separator).toLowerCase(Locale.ROOT);
                String path = PackPaths.normalize(line.substring(separator + 2));
                if (!hash.matches("[0-9a-f]{64}") || result.put(path, hash) != null) {
                    throw new IOException("Invalid or duplicate pack checksum entry: " + path);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static void requireMetadataSize(ZipEntry entry, int maximum) throws IOException {
        if (entry.getSize() < 0 || entry.getSize() > maximum) {
            throw new IOException("Pack metadata exceeds its size limit: " + entry.getName());
        }
    }

    /**
     * Java's ZipEntry omits Unix file modes, so inspect the bounded central directory explicitly.
     */
    private static void rejectSymlinkEntries(Path archive) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
            long length = file.length();
            int tailLength = (int) Math.min(length, 65_557L);
            byte[] tail = new byte[tailLength];
            file.seek(length - tailLength);
            file.readFully(tail);
            int eocd = -1;
            for (int index = tail.length - 22; index >= 0; index--) {
                if (unsignedInt(tail, index) == 0x06054b50L
                        && index + 22 + unsignedShort(tail, index + 20) == tail.length) {
                    eocd = index;
                    break;
                }
            }
            if (eocd < 0) throw new IOException("Content pack has no valid ZIP end record.");
            if (unsignedShort(tail, eocd + 4) != 0 || unsignedShort(tail, eocd + 6) != 0
                    || unsignedShort(tail, eocd + 8) != unsignedShort(tail, eocd + 10)) {
                throw new IOException("Multi-disk content packs are not supported.");
            }
            int entryCount = unsignedShort(tail, eocd + 10);
            long centralSize = unsignedInt(tail, eocd + 12);
            long centralOffset = unsignedInt(tail, eocd + 16);
            if (entryCount == 0xffff || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
                throw new IOException("ZIP64 content packs are not supported.");
            }
            if (centralSize > MAX_CENTRAL_DIRECTORY_BYTES || centralOffset + centralSize > length) {
                throw new IOException("Content-pack central directory exceeds its safety limit.");
            }
            byte[] central = new byte[(int) centralSize];
            file.seek(centralOffset);
            file.readFully(central);
            int cursor = 0;
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                if (cursor + 46 > central.length || unsignedInt(central, cursor) != 0x02014b50L) {
                    throw new IOException("Malformed content-pack central directory.");
                }
                int madeByHost = unsignedShort(central, cursor + 4) >>> 8;
                long externalAttributes = unsignedInt(central, cursor + 38);
                int unixMode = (int) ((externalAttributes >>> 16) & 0xffff);
                if ((madeByHost == 3 || madeByHost == 19) && (unixMode & 0170000) == 0120000) {
                    throw new IOException("Symbolic links are not allowed in content packs.");
                }
                int nameLength = unsignedShort(central, cursor + 28);
                int extraLength = unsignedShort(central, cursor + 30);
                int commentLength = unsignedShort(central, cursor + 32);
                long next = (long) cursor + 46 + nameLength + extraLength + commentLength;
                if (next > central.length) throw new IOException("Malformed content-pack central directory entry.");
                cursor = (int) next;
            }
        }
    }

    private static int unsignedShort(byte[] bytes, int offset) throws IOException {
        if (offset < 0 || offset + 2 > bytes.length) throw new IOException("Truncated ZIP metadata.");
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long unsignedInt(byte[] bytes, int offset) throws IOException {
        if (offset < 0 || offset + 4 > bytes.length) throw new IOException("Truncated ZIP metadata.");
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static byte[] readLimited(InputStream input, int maximum, String label) throws IOException {
        byte[] bytes = input.readNBytes(maximum + 1);
        if (bytes.length > maximum || input.read() >= 0) {
            throw new IOException("Pack metadata exceeds its size limit: " + label);
        }
        return bytes;
    }

    private static void requireExtension(String path, Set<String> allowed) throws IOException {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(extension)) {
            throw new IOException("Unsupported file type in content pack: " + path);
        }
    }

    private static void ensureWithin(Path root, Path child) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!child.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
            throw new IOException("Pack installation path escapes managed root.");
        }
    }

    static String sha256(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return sha256(input);
        }
    }

    static String sha256(InputStream input) throws IOException {
        return sha256Bounded(input, Long.MAX_VALUE).digest();
    }

    private static HashResult sha256Bounded(InputStream input, long maximumBytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) >= 0) {
                total = Math.addExact(total, read);
                if (total > maximumBytes) {
                    throw new IOException("Pack entry exceeds the expanded size limit while reading.");
                }
                digest.update(buffer, 0, read);
            }
            return new HashResult(HexFormat.of().formatHex(digest.digest()), total);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        } catch (ArithmeticException error) {
            throw new IOException("Pack entry expanded size overflow.", error);
        }
    }

    private record HashResult(String digest, long bytesRead) {
    }

    public record Validation(ContentPackManifest manifest, int entryCount, long expandedBytes) {
    }

    public record InstalledPack(ContentPackManifest manifest, Path path, String digest, boolean newlyInstalled) {
    }
}

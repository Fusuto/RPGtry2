package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class DirectoryContentMount implements ContentMount {
    private final Path root;
    private volatile ContentPackManifest manifest;
    private final Origin origin;
    private final boolean readOnly;
    private volatile String revision;
    private volatile String contentDigest;

    public DirectoryContentMount(Path root, ContentPackManifest manifest, Origin origin, boolean readOnly) {
        this.root = root.toAbsolutePath().normalize();
        this.manifest = manifest;
        this.origin = origin;
        this.readOnly = readOnly;
        refreshUnchecked();
    }

    @Override
    public ContentPackManifest manifest() {
        return manifest;
    }

    public Path root() {
        return root;
    }

    @Override
    public String revision() {
        return revision;
    }

    @Override
    public String contentDigest() {
        return contentDigest;
    }

    @Override
    public void refresh() throws IOException {
        if (origin == Origin.PROJECT) {
            Path manifestPath = root.resolve(ContentPackManifest.MANIFEST_PATH);
            try (InputStream input = Files.newInputStream(manifestPath)) {
                manifest = ContentPackManifest.read(input);
            }
        }
        revision = fingerprint(root);
        contentDigest = origin == Origin.PROJECT || origin == Origin.WORKSHOP
                ? contentFingerprint(root) : revision;
    }

    private void refreshUnchecked() {
        try {
            refresh();
        } catch (IOException error) {
            revision = "missing:" + root;
            contentDigest = revision;
        }
    }

    @Override
    public Origin origin() {
        return origin;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public List<String> list(String prefix, boolean recursive) throws IOException {
        String normalizedPrefix = PackPaths.normalizePrefix(prefix);
        Path folder = PackPaths.resolve(root, normalizedPrefix);
        ensureSafe(folder);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        int depth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> paths = Files.walk(folder, depth)) {
            List<Path> candidates = paths.toList();
            for (Path candidate : candidates) {
                ensureSafe(candidate);
            }
            return candidates.stream().filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT))).toList();
        }
    }

    @Override
    public InputStream open(String logicalPath) throws IOException {
        Path path = PackPaths.resolve(root, logicalPath);
        ensureSafe(path);
        return Files.isRegularFile(path) ? Files.newInputStream(path) : null;
    }

    private void ensureSafe(Path candidate) throws IOException {
        Path current = root;
        if (Files.isSymbolicLink(current)) {
            throw new IOException("Content mount root cannot be a symlink: " + root);
        }
        Path relative = root.relativize(candidate.toAbsolutePath().normalize());
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Content mount path cannot traverse a symlink: " + current);
            }
        }
        if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Content mount path escapes its root: " + candidate);
        }
    }

    private static String fingerprint(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return "missing:" + root;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> paths = Files.walk(root)) {
                List<Path> candidates = paths.toList();
                if (candidates.stream().anyMatch(Files::isSymbolicLink)) {
                    throw new IOException("Content mount cannot contain symlinks: " + root);
                }
                for (Path path : candidates.stream().filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(value -> root.relativize(value).toString()
                                .toLowerCase(Locale.ROOT))).toList()) {
                    String record = root.relativize(path).toString().replace('\\', '/') + "\0"
                            + Files.size(path) + "\0" + Files.getLastModifiedTime(path).toMillis() + "\n";
                    digest.update(record.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String contentFingerprint(Path root) throws IOException {
        Path checksum = root.resolve(PackInstaller.CHECKSUM_PATH);
        if (Files.isRegularFile(checksum) && !Files.isSymbolicLink(checksum)) {
            return PackInstaller.sha256(checksum);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> paths = Files.walk(root)) {
                List<Path> candidates = paths.toList();
                if (candidates.stream().anyMatch(Files::isSymbolicLink)) {
                    throw new IOException("Content mount cannot contain symlinks: " + root);
                }
                byte[] buffer = new byte[64 * 1024];
                for (Path path : candidates.stream().filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(value -> root.relativize(value).toString()
                                .toLowerCase(Locale.ROOT))).toList()) {
                    String logicalPath = root.relativize(path).toString().replace('\\', '/');
                    digest.update(logicalPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    try (InputStream input = Files.newInputStream(path)) {
                        int read;
                        while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
                    }
                    digest.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}

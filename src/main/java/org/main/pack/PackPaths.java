package org.main.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;

public final class PackPaths {
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_SEGMENT_LENGTH = 255;

    private PackPaths() {
    }

    public static String normalize(String logicalPath) throws IOException {
        if (logicalPath == null || logicalPath.isBlank()) {
            throw new IOException("Content path is blank.");
        }
        String value = logicalPath.replace('\\', '/');
        if (value.length() > MAX_PATH_LENGTH) {
            throw new IOException("Content path exceeds " + MAX_PATH_LENGTH + " characters.");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new IOException("Content path must use canonical Unicode (NFC): " + logicalPath);
        }
        if (value.startsWith("/") || value.isBlank() || value.contains(":") || value.startsWith("../")
                || value.endsWith("/..") || value.contains("/../") || value.startsWith("./")
                || value.endsWith("/.") || value.contains("/./") || value.contains("//")
                || value.codePoints().anyMatch(character -> character < 32 || character == 127)) {
            throw new IOException("Unsafe content path: " + logicalPath);
        }
        for (String segment : value.split("/", -1)) {
            if (segment.length() > MAX_SEGMENT_LENGTH) {
                throw new IOException("Content path segment exceeds " + MAX_SEGMENT_LENGTH + " characters.");
            }
        }
        return value;
    }

    public static String normalizePrefix(String prefix) throws IOException {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String value = normalize(prefix);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static Path resolve(Path root, String logicalPath) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String normalized = logicalPath == null || logicalPath.isBlank() ? "" : normalize(logicalPath);
        Path resolved = normalizedRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("Content path escapes mount: " + logicalPath);
        }
        return resolved;
    }
}

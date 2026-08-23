package org.main.tools;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.main.engine.AttributionNotices;

/**
 * Clean-release gate for inventory coverage, forbidden sources, and decodable Kit assets.
 */
public final class ReleaseArtifactVerifier {
    private static final long MAX_JAR_BYTES = 90L * 1024 * 1024;
    private static final Set<String> FORBIDDEN = Set.of("af", "psd", "pxcp", "blend", "blend1", "zip");
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif");
    private static final Set<String> AUDIO = Set.of("wav", "aiff", "au");
    private static final Set<String> MODELS = Set.of("glb", "fbx");

    private ReleaseArtifactVerifier() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IOException("Usage: ReleaseArtifactVerifier <Aether.jar> <src/main/resources>");
        }
        Path jarPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path resourceRoot = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jarPath) || Files.size(jarPath) > MAX_JAR_BYTES) {
            throw new IOException("Release JAR is missing or exceeds 90 MiB: " + jarPath);
        }
        List<String> failures = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Set<String> entries = new HashSet<>();
            List<JarEntry> files = new ArrayList<>();
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                entries.add(entry.getName());
                files.add(entry);
            }
            for (JarEntry entry : files) {
                String extension = extension(entry.getName());
                if (FORBIDDEN.contains(extension)) {
                    failures.add("Forbidden source/archive format is packaged: " + entry.getName());
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    validateEntry(entry.getName(), extension, input, entries, failures);
                } catch (Exception error) {
                    failures.add("Unreadable " + entry.getName() + ": " + error.getMessage());
                }
            }
            try (Stream<Path> resourceFiles = Files.walk(resourceRoot)) {
                for (Path file : resourceFiles.filter(Files::isRegularFile).toList()) {
                    String logicalPath = resourceRoot.relativize(file).toString().replace('\\', '/');
                    if (!entries.contains(logicalPath)) {
                        failures.add("Canonical resource is absent from JAR: " + logicalPath);
                    }
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Release verification failed:\n - " + String.join("\n - ", failures));
        }
        ReleaseSelfCheck.main(new String[0]);
        AttributionNotices.verifyPackagedNotice();
        System.out.println("Release artifact verification passed for " + jarPath.getFileName() + ".");
    }

    private static void validateEntry(
            String path,
            String extension,
            InputStream input,
            Set<String> entries,
            List<String> failures
    ) throws Exception {
        byte[] bytes = input.readAllBytes();
        if (IMAGES.contains(extension)) {
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new IOException("no ImageIO decoder accepted the file");
            }
        } else if (AUDIO.contains(extension)) {
            try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                audio.getFormat();
            }
        } else if (extension.equals("glb")) {
            if (bytes.length < 12 || bytes[0] != 'g' || bytes[1] != 'l' || bytes[2] != 'T' || bytes[3] != 'F') {
                throw new IOException("invalid GLB header");
            }
        } else if (extension.equals("fbx")) {
            String header = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
            if (!header.startsWith("Kaydara FBX Binary") && !header.contains("FBXHeaderExtension")) {
                throw new IOException("invalid FBX header");
            }
        } else if (extension.equals("properties")) {
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(bytes));
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key, "").trim().replace('\\', '/');
                if (value.startsWith("data/") || value.startsWith("src/main/")) {
                    failures.add(path + " uses a developer/local path in " + key + ": " + value);
                }
                if (value.startsWith("assets/") && looksLikeSingleAssetReference(value)
                        && !entries.contains(value)) {
                    failures.add(path + " references missing asset in " + key + ": " + value);
                }
            }
        }
    }

    private static boolean looksLikeSingleAssetReference(String value) {
        String extension = extension(value);
        return IMAGES.contains(extension) || AUDIO.contains(extension) || MODELS.contains(extension)
                || extension.equals("properties");
    }

    private static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot <= slash ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

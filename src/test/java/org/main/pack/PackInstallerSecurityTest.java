package org.main.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackInstallerSecurityTest {
    private static final String ASSET = "assets/packs/security_test/readme.txt";

    @TempDir
    Path temporaryFolder;

    @Test
    void rejectsTraversalAndDrivePathsWithoutCreatingAnInstall() throws Exception {
        assertThrows(IOException.class, () -> PackPaths.normalize("a".repeat(513)));
        assertThrows(IOException.class, () -> PackPaths.normalize("assets/" + "a".repeat(256) + ".txt"));
        for (String unsafe : new String[]{"../escape.txt", "C:/escape.txt", "/absolute.txt"}) {
            Map<String, byte[]> entries = validEntries();
            entries.put(unsafe, "escape".getBytes(StandardCharsets.UTF_8));
            Path archive = writePack(unsafe.replaceAll("[^A-Za-z]", "_") + ".aetherpack",
                    entries, Set.of(unsafe), Map.of());
            Path managed = temporaryFolder.resolve("managed-" + Math.abs(unsafe.hashCode()));

            assertThrows(IOException.class, () -> new PackInstaller(managed).install(archive));
            assertFalse(Files.exists(managed));
        }
    }

    @Test
    void rejectsCaseCollisionsExecutablesAndUnlistedFiles() throws Exception {
        Map<String, byte[]> collision = validEntries();
        collision.put("assets/packs/security_test/A.txt", new byte[]{1});
        collision.put("assets/packs/security_test/a.txt", new byte[]{2});
        IOException collisionError = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(
                        writePack("collision.aetherpack", collision, Set.of(), Map.of())));
        assertTrue(collisionError.getMessage().contains("case-colliding"));

        Map<String, byte[]> executable = validEntries();
        executable.put("assets/packs/security_test/Injected.class", new byte[]{1, 2, 3});
        IOException executableError = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(
                        writePack("executable.aetherpack", executable, Set.of(), Map.of())));
        assertTrue(executableError.getMessage().contains("Unsupported file type"));

        Map<String, byte[]> unlisted = validEntries();
        unlisted.put("assets/packs/security_test/unlisted.txt", new byte[]{4});
        IOException unlistedError = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(
                        writePack("unlisted.aetherpack", unlisted,
                                Set.of("assets/packs/security_test/unlisted.txt"), Map.of())));
        assertTrue(unlistedError.getMessage().contains("checksum list does not include"));
    }

    @Test
    void rejectsChecksumMismatchAndDecompressionBombRatio() throws Exception {
        IOException checksumError = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(
                        writePack("checksum.aetherpack", validEntries(), Set.of(),
                                Map.of(ASSET, "0".repeat(64)))));
        assertTrue(checksumError.getMessage().contains("Checksum mismatch"));

        Map<String, byte[]> compressed = validEntries();
        compressed.put("assets/packs/security_test/highly-compressed.txt", new byte[1024 * 1024]);
        IOException ratioError = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(
                        writePack("ratio.aetherpack", compressed, Set.of(), Map.of())));
        assertTrue(ratioError.getMessage().contains("compression ratio"));
    }

    @Test
    void rejectsUnixSymlinkEntriesEvenThoughPacksStayArchived() throws Exception {
        String linkPath = "assets/packs/security_test/link.txt";
        Map<String, byte[]> entries = validEntries();
        entries.put(linkPath, "../../escape".getBytes(StandardCharsets.UTF_8));
        Path archive = writePack("symlink.aetherpack", entries, Set.of(), Map.of());
        markAsUnixSymlink(archive, linkPath);

        IOException error = assertThrows(IOException.class,
                () -> new PackInstaller(temporaryFolder).validate(archive));
        assertTrue(error.getMessage().contains("Symbolic links"));
    }

    private static Map<String, byte[]> validEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ContentPackManifest.MANIFEST_PATH, """
                pack.formatVersion=1
                pack.type=content
                pack.id=security.test
                pack.namespace=security_test
                pack.version=1.0.0
                pack.title=Security Test
                pack.author=Test
                pack.description=Malicious archive validation test
                pack.license=Test
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """.getBytes(StandardCharsets.UTF_8));
        entries.put(ASSET, "safe".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private Path writePack(
            String name,
            Map<String, byte[]> entries,
            Set<String> omittedChecksums,
            Map<String, String> checksumOverrides
    ) throws Exception {
        StringBuilder checksums = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (omittedChecksums.contains(entry.getKey())) continue;
            checksums.append(checksumOverrides.getOrDefault(entry.getKey(), sha256(entry.getValue())))
                    .append("  ").append(entry.getKey()).append('\n');
        }
        Path archive = temporaryFolder.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(PackInstaller.CHECKSUM_PATH));
            output.write(checksums.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void markAsUnixSymlink(Path archive, String path) throws IOException {
        byte[] bytes = Files.readAllBytes(archive);
        byte[] name = path.getBytes(StandardCharsets.UTF_8);
        for (int index = 46; index + name.length <= bytes.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < name.length; offset++) {
                if (bytes[index + offset] != name[offset]) {
                    matches = false;
                    break;
                }
            }
            int header = index - 46;
            if (!matches || (bytes[header] & 0xff) != 0x50 || (bytes[header + 1] & 0xff) != 0x4b
                    || (bytes[header + 2] & 0xff) != 0x01 || (bytes[header + 3] & 0xff) != 0x02) {
                continue;
            }
            bytes[header + 5] = 3; // "version made by" host: Unix.
            long attributes = (long) 0120777 << 16;
            for (int offset = 0; offset < 4; offset++) {
                bytes[header + 38 + offset] = (byte) (attributes >>> (offset * 8));
            }
            Files.write(archive, bytes);
            return;
        }
        throw new IOException("Unable to locate synthetic ZIP central-directory entry.");
    }
}

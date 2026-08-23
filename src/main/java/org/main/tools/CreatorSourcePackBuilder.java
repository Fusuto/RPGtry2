package org.main.tools;

import org.main.pack.ContentPackManifest;
import org.main.pack.PackExportService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Release-build entrypoint for the optional, version-matched creator source pack.
 */
public final class CreatorSourcePackBuilder {
    private CreatorSourcePackBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: CreatorSourcePackBuilder <asset-source> <output.aetherpack> <version>");
        }
        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        String releaseVersion = args[2].trim();
        String version = semanticVersion(releaseVersion);
        if (!Files.isDirectory(source)) throw new IOException("asset-source folder is missing: " + source);
        Path staging = output.getParent().resolve("creator-source-project").normalize();
        if (!staging.getParent().equals(output.getParent())) throw new IOException("Unsafe creator staging path.");
        deleteGeneratedTree(staging);
        Files.createDirectories(staging.resolve("sources"));
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Source pack cannot contain symlinks: " + path);
                Path relative = source.relativize(path);
                Path target = staging.resolve("sources").resolve(relative).normalize();
                if (!target.startsWith(staging.resolve("sources")))
                    throw new IOException("Unsafe source path: " + relative);
                Files.createDirectories(target.getParent());
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(staging.resolve(ContentPackManifest.MANIFEST_PATH), """
                pack.formatVersion=1
                pack.type=authoring-source
                pack.id=aether.creator.sources
                pack.namespace=aether_creator_sources
                pack.version=%s
                pack.title=Aether Creator Sources
                pack.author=Aether
                pack.description=Optional editable PSD, Affinity, Pixelmator, Blender, and vendor archive sources.
                pack.license=See bundled source licenses and Aether credits.
                contentApiVersion=1
                dependency.count=0
                override.count=0
                """.formatted(version));
        PackExportService.ExportResult result = new PackExportService().export(staging, output);
        System.out.println(result.path() + " " + result.digest() + " " + result.entryCount());
        deleteGeneratedTree(staging);
    }

    private static void deleteGeneratedTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static String semanticVersion(String releaseVersion) {
        String[] build = releaseVersion.split("[+]", 2);
        String[] pre = build[0].split("-", 2);
        String[] numbers = pre[0].split("[.]", -1);
        if (numbers.length < 1 || numbers.length > 3) {
            throw new IllegalArgumentException("Release version cannot be represented as SemVer: " + releaseVersion);
        }
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < 3; index++) {
            if (index > 0) normalized.append('.');
            String part = index < numbers.length ? numbers[index] : "0";
            if (!part.matches("[0-9]+"))
                throw new IllegalArgumentException("Invalid release version: " + releaseVersion);
            normalized.append(new java.math.BigInteger(part).toString());
        }
        if (pre.length > 1) normalized.append('-').append(pre[1]);
        if (build.length > 1) normalized.append('+').append(build[1]);
        return normalized.toString();
    }
}

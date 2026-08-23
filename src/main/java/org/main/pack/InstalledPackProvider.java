package org.main.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

public final class InstalledPackProvider implements ContentPackProvider {
    private final Path installedFolder;
    private volatile List<String> diagnostics = List.of();
    private volatile Map<String, PackLock.Entry> lockedSelections = Map.of();
    private final Map<Path, String> validatedArchives = new LinkedHashMap<>();

    public InstalledPackProvider(Path contentPacksFolder) {
        this.installedFolder = contentPacksFolder.resolve("installed").toAbsolutePath().normalize();
    }

    @Override
    public String providerId() {
        return "local-installed";
    }

    @Override
    public List<ContentMount> discover() throws IOException {
        if (!Files.isDirectory(installedFolder)) {
            return List.of();
        }
        List<Path> archives;
        try (Stream<Path> paths = Files.walk(installedFolder)) {
            archives = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".aetherpack"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        Map<String, List<ZipContentMount>> candidates = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        validatedArchives.keySet().retainAll(archives);
        for (Path archive : archives) {
            try {
                String stamp = Files.size(archive) + ":" + Files.getLastModifiedTime(archive).toMillis();
                if (!stamp.equals(validatedArchives.get(archive))) {
                    new PackInstaller(installedFolder).validate(archive);
                    validatedArchives.put(archive, stamp);
                }
                ZipContentMount candidate = new ZipContentMount(archive, ContentMount.Origin.INSTALLED);
                candidates.computeIfAbsent(candidate.manifest().id(), ignored -> new ArrayList<>()).add(candidate);
            } catch (IOException | RuntimeException error) {
                errors.add("Invalid installed pack " + archive.getFileName() + ": " + error.getMessage());
            }
        }
        Map<String, ZipContentMount> selected = new LinkedHashMap<>();
        for (Map.Entry<String, List<ZipContentMount>> group : candidates.entrySet()) {
            PackLock.Entry locked = lockedSelections.get(group.getKey());
            ZipContentMount chosen = locked == null
                    ? newest(group.getValue())
                    : group.getValue().stream().filter(candidate ->
                            candidate.manifest().version().equals(locked.version())
                                    && candidate.contentDigest().equalsIgnoreCase(locked.digest()))
                    .findFirst().orElse(null);
            if (chosen == null) {
                errors.add("Required retained version is unavailable: " + locked.packId() + "@"
                        + locked.version() + "#" + locked.digest());
            } else {
                selected.put(group.getKey(), chosen);
            }
            for (ZipContentMount candidate : group.getValue()) {
                if (candidate != chosen) {
                    candidate.close();
                }
            }
        }
        diagnostics = List.copyOf(errors);
        return List.copyOf(selected.values());
    }

    @Override
    public List<String> diagnostics() {
        return diagnostics;
    }

    public void selectLockedVersions(PackLock lock) throws IOException {
        lock.validate();
        Map<String, PackLock.Entry> selections = new LinkedHashMap<>();
        for (PackLock.Entry entry : lock.packs()) {
            selections.put(entry.packId(), entry);
        }
        lockedSelections = Map.copyOf(selections);
    }

    private static ZipContentMount newest(List<ZipContentMount> candidates) {
        return candidates.stream().max((left, right) -> SemanticVersion.parse(left.manifest().version())
                .compareTo(SemanticVersion.parse(right.manifest().version()))).orElseThrow();
    }
}

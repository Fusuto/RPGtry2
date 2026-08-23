package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ContentPackRegistry implements AutoCloseable {
    private static final String ACTIVE_FILE = "active.properties";

    private final Path root;
    private final List<ContentPackProvider> providers;
    private final InstalledPackProvider installedProvider;
    private final ContentMount bundled;
    private final ContentMount development;
    private final ContentMount project;
    private final AtomicLong revision = new AtomicLong();
    private volatile Snapshot snapshot;

    public ContentPackRegistry(
            Path root,
            Class<?> bundleAnchor,
            Path developmentResources,
            Path activeProject
    ) throws IOException {
        this(root, bundleAnchor, developmentResources, activeProject,
                List.of(new WorkshopContentPackProvider(WorkshopPackLocationProvider.unavailable())));
    }

    public ContentPackRegistry(
            Path root,
            Class<?> bundleAnchor,
            Path developmentResources,
            Path activeProject,
            List<? extends ContentPackProvider> additionalProviders
    ) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        List<ContentPackProvider> configuredProviders = new ArrayList<>();
        this.installedProvider = new InstalledPackProvider(this.root);
        configuredProviders.add(installedProvider);
        if (additionalProviders != null) {
            configuredProviders.addAll(additionalProviders);
        }
        this.providers = List.copyOf(configuredProviders);
        this.bundled = new BundledContentMount(bundleAnchor);
        this.development = developmentResources == null ? null : new DirectoryContentMount(
                developmentResources,
                developmentManifest(),
                ContentMount.Origin.DEVELOPMENT,
                false
        );
        this.project = activeProject == null ? null : projectMount(activeProject);
        this.snapshot = new Snapshot(0, List.of(), Map.of(), List.of());
        reload();
    }

    public synchronized Snapshot reload() throws IOException {
        closeProviderMounts(snapshot.available().values());
        bundled.refresh();
        if (development != null) {
            development.refresh();
        }
        if (project != null) {
            project.refresh();
        }
        Map<String, ContentMount> discovered = new LinkedHashMap<>();
        discovered.put(bundled.manifest().id(), bundled);
        if (development != null) {
            discovered.put(development.manifest().id(), development);
        }
        if (project != null) {
            discovered.put(project.manifest().id(), project);
        }
        for (ContentPackProvider provider : providers) {
            provider.refresh();
            for (ContentMount mount : provider.discover()) {
                ContentMount previous = discovered.putIfAbsent(mount.manifest().id(), mount);
                if (previous != null) {
                    mount.close();
                    throw new IOException("Duplicate installed content-pack ID: " + mount.manifest().id());
                }
            }
        }

        ActiveState activeState = readActiveState();
        List<String> diagnostics = new ArrayList<>();
        providers.forEach(provider -> diagnostics.addAll(provider.diagnostics()));
        List<ContentMount> active = orderActive(discovered, activeState, diagnostics);
        Snapshot next = new Snapshot(revision.incrementAndGet(), active, Map.copyOf(discovered), diagnostics);
        snapshot = next;
        return next;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public PackLock activePackLock() {
        List<PackLock.Entry> entries = snapshot.activeHighestPriorityFirst().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.PROJECT
                        || mount.origin() == ContentMount.Origin.INSTALLED
                        || mount.origin() == ContentMount.Origin.WORKSHOP)
                .map(mount -> new PackLock.Entry(mount.manifest().id(), mount.manifest().version(),
                        mount.contentDigest()))
                .toList();
        return new PackLock(ContentPackManifest.CURRENT_CONTENT_API_VERSION, entries);
    }

    public synchronized void requirePackLock(PackLock expected) throws IOException {
        expected.validate();
        PackLock actual = activePackLock();
        if (expected.packs().equals(actual.packs())) {
            return;
        }
        ActiveState previousState = readActiveState();
        PackLock previousLock = actual;
        try {
            installedProvider.selectLockedVersions(expected);
            List<String> runtimePackIds = expected.packs().stream()
                    .map(PackLock.Entry::packId)
                    .filter(id -> project == null || !project.manifest().id().equals(id))
                    .filter(id -> development == null || !development.manifest().id().equals(id))
                    .toList();
            writeActiveState(new ActiveState(runtimePackIds, runtimePackIds));
            reload();
            actual = activePackLock();
            if (!expected.packs().equals(actual.packs())) {
                throw new IOException("Save content-pack lock does not match the available packs. Required: "
                        + expected.describe() + "; resolved: " + actual.describe() + ". Diagnostics: "
                        + String.join(" ", snapshot.diagnostics()) + ".");
            }
        } catch (IOException error) {
            try {
                installedProvider.selectLockedVersions(previousLock);
                writeActiveState(previousState);
                reload();
            } catch (IOException restoreError) {
                error.addSuppressed(restoreError);
            }
            throw error;
        }
    }

    public synchronized Snapshot setEnabled(String packId, boolean enabled) throws IOException {
        ActiveState state = readActiveState();
        LinkedHashSet<String> enabledIds = new LinkedHashSet<>(state.enabled().stream()
                .filter(id -> userActivatable(snapshot.available().get(id))).toList());
        List<String> order = new ArrayList<>(state.order().stream()
                .filter(enabledIds::contains).toList());
        if (enabled) {
            ContentMount mount = snapshot.available().get(packId);
            if (mount == null || (mount.origin() != ContentMount.Origin.INSTALLED
                    && mount.origin() != ContentMount.Origin.WORKSHOP)) {
                throw new IOException("Unknown or immutable content pack: " + packId);
            }
            if (mount.manifest().type() != ContentPackManifest.PackType.CONTENT) {
                throw new IOException("Authoring-source packs are not runtime content packs: " + packId);
            }
            if (!mount.manifest().supportsGameVersion(ContentPackManifest.CURRENT_GAME_VERSION)) {
                throw new IOException(packId + " does not support Aether "
                        + ContentPackManifest.CURRENT_GAME_VERSION + ".");
            }
            for (ContentPackManifest.Dependency dependency : mount.manifest().dependencies()) {
                ContentMount dependencyMount = snapshot.available().get(dependency.id());
                if (dependencyMount == null) {
                    throw new IOException("Missing dependency " + dependency.id() + " for " + packId);
                }
                if (!dependency.accepts(dependencyMount.manifest().version())) {
                    throw new IOException("Dependency " + dependency.id() + " version "
                            + dependencyMount.manifest().version() + " does not satisfy " + packId + ".");
                }
                if (!dependencyMount.manifest().supportsGameVersion(ContentPackManifest.CURRENT_GAME_VERSION)) {
                    throw new IOException("Dependency " + dependency.id() + " does not support Aether "
                            + ContentPackManifest.CURRENT_GAME_VERSION + ".");
                }
                if (userActivatable(dependencyMount)) {
                    enabledIds.add(dependency.id());
                    if (!order.contains(dependency.id())) {
                        order.add(dependency.id());
                    }
                }
            }
            enabledIds.add(packId);
            if (!order.contains(packId)) {
                order.add(0, packId);
            }
        } else {
            boolean required = snapshot.available().values().stream()
                    .filter(candidate -> enabledIds.contains(candidate.manifest().id()))
                    .flatMap(candidate -> candidate.manifest().dependencies().stream())
                    .anyMatch(dependency -> dependency.id().equals(packId));
            if (required) {
                throw new IOException("Content pack is required by another enabled pack: " + packId);
            }
            enabledIds.remove(packId);
            order.remove(packId);
        }
        writeActiveState(new ActiveState(List.copyOf(enabledIds), List.copyOf(order)));
        return reload();
    }

    public synchronized Snapshot reorder(List<String> highestPriorityFirst) throws IOException {
        ActiveState state = readActiveState();
        List<String> enabledRuntime = state.enabled().stream()
                .filter(id -> userActivatable(snapshot.available().get(id))).toList();
        LinkedHashSet<String> requested = new LinkedHashSet<>(highestPriorityFirst);
        if (!requested.equals(new LinkedHashSet<>(enabledRuntime))) {
            throw new IOException("Load order must contain every enabled pack exactly once.");
        }
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int index = 0; index < highestPriorityFirst.size(); index++) {
            positions.put(highestPriorityFirst.get(index), index);
        }
        for (String id : highestPriorityFirst) {
            ContentMount mount = snapshot.available().get(id);
            if (mount == null) {
                throw new IOException("Unknown content pack in load order: " + id);
            }
            if (!mount.manifest().supportsGameVersion(ContentPackManifest.CURRENT_GAME_VERSION)) {
                throw new IOException(id + " does not support Aether "
                        + ContentPackManifest.CURRENT_GAME_VERSION + ".");
            }
            for (ContentPackManifest.Dependency dependency : mount.manifest().dependencies()) {
                ContentMount dependencyMount = snapshot.available().get(dependency.id());
                if (dependencyMount == null || !dependency.accepts(dependencyMount.manifest().version())) {
                    throw new IOException("Dependency " + dependency.id() + " has an incompatible version for "
                            + id + ".");
                }
                if (!userActivatable(dependencyMount)) {
                    continue;
                }
                Integer dependencyIndex = positions.get(dependency.id());
                if (dependencyIndex == null || dependencyIndex <= positions.get(id)) {
                    throw new IOException("Dependency " + dependency.id()
                            + " must load below dependent pack " + id + ".");
                }
            }
        }
        writeActiveState(new ActiveState(enabledRuntime, List.copyOf(highestPriorityFirst)));
        return reload();
    }

    public Optional<ResolvedResource> resolve(String logicalPath) throws IOException {
        String normalized = PackPaths.normalize(logicalPath);
        for (ContentMount mount : snapshot.activeHighestPriorityFirst()) {
            if (mount.contains(normalized)) {
                return Optional.of(new ResolvedResource(mount, normalized));
            }
        }
        return Optional.empty();
    }

    public List<ContentResource> list(String prefix, boolean recursive) throws IOException {
        Map<String, ContentResource> resources = new LinkedHashMap<>();
        for (ContentMount mount : snapshot.activeHighestPriorityFirst()) {
            for (String path : mount.list(prefix, recursive)) {
                resources.putIfAbsent(path, new ContentResource(path, mount.manifest().id(),
                        mount.contentDigest(), mount.origin(), mount.readOnly()));
            }
        }
        return List.copyOf(resources.values());
    }

    @Override
    public synchronized void close() throws IOException {
        closeProviderMounts(snapshot.available().values());
        bundled.close();
        if (development != null) {
            development.close();
        }
        if (project != null) {
            project.close();
        }
    }

    private List<ContentMount> orderActive(
            Map<String, ContentMount> discovered,
            ActiveState activeState,
            List<String> diagnostics
    ) {
        List<ContentMount> active = new ArrayList<>();
        if (project != null) {
            active.add(project);
        }
        if (development != null) {
            active.add(development);
        }
        Set<String> added = new LinkedHashSet<>();
        Set<String> considered = new LinkedHashSet<>();
        Map<String, Integer> configuredPositions = new LinkedHashMap<>();
        for (int index = 0; index < activeState.order().size(); index++) {
            configuredPositions.put(activeState.order().get(index), index);
        }
        for (String id : activeState.order()) {
            if (!activeState.enabled().contains(id)) {
                continue;
            }
            considered.add(id);
            ContentMount mount = discovered.get(id);
            if (mount == null) {
                diagnostics.add("Enabled content pack is not installed: " + id);
                continue;
            }
            if (mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
                continue;
            }
            if (mount.origin() != ContentMount.Origin.INSTALLED
                    && mount.origin() != ContentMount.Origin.WORKSHOP) {
                continue;
            }
            if (!mount.manifest().supportsGameVersion(ContentPackManifest.CURRENT_GAME_VERSION)) {
                diagnostics.add("Disabled " + id + " because it does not support Aether "
                        + ContentPackManifest.CURRENT_GAME_VERSION + ".");
                continue;
            }
            boolean missingDependency = mount.manifest().dependencies().stream().anyMatch(dependency -> {
                ContentMount dependencyMount = discovered.get(dependency.id());
                if (dependencyMount == null
                        || !dependency.accepts(dependencyMount.manifest().version())
                        || !dependencyMount.manifest().supportsGameVersion(
                        ContentPackManifest.CURRENT_GAME_VERSION)) {
                    return true;
                }
                if (!userActivatable(dependencyMount)) return false;
                Integer packPosition = configuredPositions.get(id);
                Integer dependencyPosition = configuredPositions.get(dependency.id());
                return !activeState.enabled().contains(dependency.id()) || packPosition == null
                        || dependencyPosition == null || dependencyPosition <= packPosition;
            });
            if (missingDependency) {
                diagnostics.add("Disabled " + id + " because a dependency is missing or disabled.");
                continue;
            }
            active.add(mount);
            added.add(id);
        }
        activeState.enabled().stream().sorted().filter(id -> !considered.contains(id)).forEach(id -> {
            ContentMount mount = discovered.get(id);
            if (mount == null) {
                diagnostics.add("Enabled content pack is not installed: " + id);
                return;
            }
            if (mount.manifest().type() != ContentPackManifest.PackType.CONTENT) {
                return;
            }
            if (mount.origin() != ContentMount.Origin.INSTALLED
                    && mount.origin() != ContentMount.Origin.WORKSHOP) {
                return;
            }
            if (!mount.manifest().supportsGameVersion(ContentPackManifest.CURRENT_GAME_VERSION)) {
                diagnostics.add("Disabled " + id + " because it does not support Aether "
                        + ContentPackManifest.CURRENT_GAME_VERSION + ".");
                return;
            }
            boolean missingDependency = mount.manifest().dependencies().stream().anyMatch(dependency -> {
                ContentMount dependencyMount = discovered.get(dependency.id());
                if (dependencyMount == null
                        || !dependency.accepts(dependencyMount.manifest().version())
                        || !dependencyMount.manifest().supportsGameVersion(
                        ContentPackManifest.CURRENT_GAME_VERSION)) {
                    return true;
                }
                if (!userActivatable(dependencyMount)) return false;
                Integer packPosition = configuredPositions.get(id);
                Integer dependencyPosition = configuredPositions.get(dependency.id());
                return !activeState.enabled().contains(dependency.id()) || packPosition == null
                        || dependencyPosition == null || dependencyPosition <= packPosition;
            });
            if (missingDependency) {
                diagnostics.add("Disabled " + id + " because a dependency is missing or disabled.");
                return;
            }
            active.add(mount);
            added.add(id);
        });
        active.add(bundled);
        return List.copyOf(active);
    }

    private ActiveState readActiveState() throws IOException {
        Path path = root.resolve(ACTIVE_FILE);
        if (!Files.isRegularFile(path)) {
            return new ActiveState(List.of(), List.of());
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new ActiveState(csv(properties.getProperty("enabled", "")),
                csv(properties.getProperty("order", "")));
    }

    private void writeActiveState(ActiveState state) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve(ACTIVE_FILE);
        Path temporary = root.resolve(ACTIVE_FILE + ".new");
        Properties properties = new Properties();
        properties.setProperty("enabled", String.join(",", state.enabled()));
        properties.setProperty("order", String.join(",", state.order()));
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Aether content-pack activation and highest-first load order");
        }
        try {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static DirectoryContentMount projectMount(Path projectRoot) throws IOException {
        Path manifestPath = projectRoot.resolve(ContentPackManifest.MANIFEST_PATH);
        try (InputStream input = Files.newInputStream(manifestPath)) {
            return new DirectoryContentMount(projectRoot, ContentPackManifest.read(input),
                    ContentMount.Origin.PROJECT, false);
        }
    }

    private static ContentPackManifest developmentManifest() {
        return new ContentPackManifest(1, ContentPackManifest.PackType.CONTENT,
                "aether.development", "aether_development", "0.0.0",
                "Aether Development Resources", "Aether", "", "Bundled", 1,
                List.of(), List.of());
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(part -> !part.isBlank()).distinct().toList();
    }

    private void closeProviderMounts(Collection<ContentMount> mounts) {
        for (ContentMount mount : mounts) {
            if (mount.origin() != ContentMount.Origin.INSTALLED
                    && mount.origin() != ContentMount.Origin.WORKSHOP) {
                continue;
            }
            try {
                mount.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean userActivatable(ContentMount mount) {
        return mount != null && (mount.origin() == ContentMount.Origin.INSTALLED
                || mount.origin() == ContentMount.Origin.WORKSHOP);
    }

    private record ActiveState(List<String> enabled, List<String> order) {
    }

    public record Snapshot(
            long revision,
            List<ContentMount> activeHighestPriorityFirst,
            Map<String, ContentMount> available,
            List<String> diagnostics
    ) {
        public Snapshot {
            activeHighestPriorityFirst = List.copyOf(activeHighestPriorityFirst);
            available = Map.copyOf(available);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record ResolvedResource(ContentMount mount, String logicalPath) {
        public InputStream open() throws IOException {
            return mount.open(logicalPath);
        }

        public ContentResource descriptor() {
            return new ContentResource(logicalPath, mount.manifest().id(), mount.contentDigest(),
                    mount.origin(), mount.readOnly());
        }
    }
}

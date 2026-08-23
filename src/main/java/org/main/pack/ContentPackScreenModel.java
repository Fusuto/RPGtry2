package org.main.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared game/Construction Kit model for pack state, dependency details, and highest-first ordering.
 */
public final class ContentPackScreenModel {
    private final ContentPackRegistry registry;
    private volatile boolean requiredRestart;

    public ContentPackScreenModel(ContentPackRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public List<Row> rows() {
        ContentPackRegistry.Snapshot snapshot = registry.snapshot();
        Map<String, Integer> activePositions = new LinkedHashMap<>();
        int position = 0;
        for (ContentMount mount : snapshot.activeHighestPriorityFirst()) {
            if (mount.origin() == ContentMount.Origin.INSTALLED
                    || mount.origin() == ContentMount.Origin.WORKSHOP) {
                activePositions.put(mount.manifest().id(), position++);
            }
        }
        return snapshot.available().values().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.INSTALLED
                        || mount.origin() == ContentMount.Origin.WORKSHOP
                        || mount.origin() == ContentMount.Origin.BUNDLED)
                .sorted(Comparator
                        .comparingInt((ContentMount mount) -> mount.origin() == ContentMount.Origin.BUNDLED
                                ? Integer.MAX_VALUE
                                : activePositions.getOrDefault(mount.manifest().id(), Integer.MAX_VALUE - 1))
                        .thenComparing(mount -> mount.manifest().id()))
                .map(mount -> row(mount, activePositions.containsKey(mount.manifest().id()), snapshot.diagnostics()))
                .toList();
    }

    public List<String> diagnostics() {
        return registry.snapshot().diagnostics();
    }

    public boolean requiredRestart() {
        return requiredRestart;
    }

    public void setEnabled(String packId, boolean enabled) throws IOException {
        registry.setEnabled(packId, enabled);
        requiredRestart = true;
    }

    public void move(String packId, int delta) throws IOException {
        List<String> order = enabledOrder();
        int index = order.indexOf(packId);
        if (index < 0) {
            throw new IOException("Enable the pack before changing its load order.");
        }
        int target = Math.max(0, Math.min(order.size() - 1, index + delta));
        if (target == index) return;
        order.remove(index);
        order.add(target, packId);
        reorder(order);
    }

    public void moveBefore(String packId, String targetPackId) throws IOException {
        List<String> order = enabledOrder();
        int source = order.indexOf(packId);
        int target = order.indexOf(targetPackId);
        if (source < 0 || target < 0 || source == target) return;
        order.remove(source);
        order.add(target, packId);
        reorder(order);
    }

    public void reorder(List<String> highestPriorityFirst) throws IOException {
        registry.reorder(highestPriorityFirst);
        requiredRestart = true;
    }

    public List<String> enabledOrder() {
        return registry.snapshot().activeHighestPriorityFirst().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.INSTALLED
                        || mount.origin() == ContentMount.Origin.WORKSHOP)
                .map(mount -> mount.manifest().id())
                .toList();
    }

    private static Row row(ContentMount mount, boolean externalEnabled, List<String> diagnostics) {
        ContentPackManifest manifest = mount.manifest();
        boolean core = mount.origin() == ContentMount.Origin.BUNDLED;
        boolean authoring = manifest.type() == ContentPackManifest.PackType.AUTHORING_SOURCE;
        List<String> dependencies = manifest.dependencies().stream()
                .map(dependency -> dependency.id()
                        + (dependency.minVersion().isBlank() ? "" : " >=" + dependency.minVersion())
                        + (dependency.maxVersionExclusive().isBlank()
                        ? "" : " <" + dependency.maxVersionExclusive()))
                .toList();
        List<String> relevantDiagnostics = new ArrayList<>();
        for (String diagnostic : diagnostics) {
            if (core || diagnostic.contains(manifest.id())) relevantDiagnostics.add(diagnostic);
        }
        return new Row(manifest.id(), manifest.title(), manifest.version(), mount.origin(), manifest.type(),
                core || externalEnabled, !core && !authoring, !core && !authoring && externalEnabled,
                dependencies, manifest.overrides().size(), relevantDiagnostics);
    }

    public record Row(
            String id,
            String title,
            String version,
            ContentMount.Origin origin,
            ContentPackManifest.PackType type,
            boolean enabled,
            boolean canToggle,
            boolean canReorder,
            List<String> dependencies,
            int declaredOverrideCount,
            List<String> diagnostics
    ) {
        public Row {
            dependencies = List.copyOf(dependencies);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean core() {
            return origin == ContentMount.Origin.BUNDLED;
        }

        @Override
        public String toString() {
            String state = core() ? "[core]" : enabled ? "[x]" : "[ ]";
            return state + " " + title + " " + version + " (" + id + ", "
                    + origin.name().toLowerCase() + ")";
        }
    }
}

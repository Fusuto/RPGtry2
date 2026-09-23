package org.main.pack;

import org.main.content.ContentRepository;
import org.main.engine.ApplicationPaths;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Adds the explicit dependencies/whole-record overrides required by copy-on-write editor saves.
 */
public final class ProjectManifestOverrides {
    private ProjectManifestOverrides() {
    }

    public static void declareCatalogRecords(
            String catalogFile,
            Map<String, ? extends Collection<String>> records
    ) throws IOException {
        if (ApplicationPaths.developmentResourcesFolder() != null || records == null || records.isEmpty()) return;
        Path project = ApplicationPaths.activeProjectFolder();
        if (project == null) return;
        Path manifestPath = project.resolve(ContentPackManifest.MANIFEST_PATH);
        if (!Files.isRegularFile(manifestPath)) return;

        Properties properties = new Properties();
        ContentPackManifest projectManifest;
        try (InputStream input = Files.newInputStream(manifestPath)) {
            properties.load(input);
        }
        try (InputStream input = Files.newInputStream(manifestPath)) {
            projectManifest = ContentPackManifest.read(input);
        }
        String namespacePrefix = projectManifest.namespace() + "__";
        Map<String, Target> targets = lowerLayerTargets(catalogFile, records.keySet(), projectManifest.id());
        Map<String, Target> required = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Collection<String>> type : records.entrySet()) {
            String contentType = normalize(type.getKey());
            for (String rawId : type.getValue()) {
                String id = normalize(rawId);
                if (id.isBlank() || id.startsWith(namespacePrefix)) continue;
                Target target = targets.get(key(contentType, id));
                if (target == null && contentType.equals("first_person_item_profile")
                        && ContentRepository.shared().snapshot().itemById().containsKey(id)) {
                    target = new Target("aether.core", "0.0.0");
                }
                if (target == null) {
                    throw new IOException("New " + contentType + " ID '" + id
                            + "' must begin with " + namespacePrefix + ".");
                }
                required.put(key(contentType, id), target);
            }
        }
        if (required.isEmpty()) return;

        int dependencyCount = integer(properties, "dependency.count");
        Set<String> dependencies = new LinkedHashSet<>();
        for (int index = 0; index < dependencyCount; index++) {
            dependencies.add(normalize(properties.getProperty("dependency." + index + ".id")));
        }
        for (Target target : required.values()) {
            if (!dependencies.add(target.packId())) continue;
            String prefix = "dependency." + dependencyCount++ + ".";
            properties.setProperty(prefix + "id", target.packId());
            properties.setProperty(prefix + "minVersion", target.version());
            properties.setProperty(prefix + "maxVersionExclusive", "");
        }
        properties.setProperty("dependency.count", String.valueOf(dependencyCount));

        int overrideCount = integer(properties, "override.count");
        Set<String> overrides = new LinkedHashSet<>();
        for (int index = 0; index < overrideCount; index++) {
            String prefix = "override." + index + ".";
            overrides.add(properties.getProperty(prefix + "targetPack", "") + "\n"
                    + normalize(properties.getProperty(prefix + "type")) + "\n"
                    + normalize(properties.getProperty(prefix + "id")));
        }
        for (Map.Entry<String, Target> entry : required.entrySet()) {
            String[] parts = entry.getKey().split("\n", 2);
            String declaration = entry.getValue().packId() + "\n" + parts[0] + "\n" + parts[1];
            if (!overrides.add(declaration)) continue;
            String prefix = "override." + overrideCount++ + ".";
            properties.setProperty(prefix + "targetPack", entry.getValue().packId());
            properties.setProperty(prefix + "type", parts[0]);
            properties.setProperty(prefix + "id", parts[1]);
        }
        properties.setProperty("override.count", String.valueOf(overrideCount));
        publishManifest(properties, manifestPath);
    }

    public static void declareWorldResource(
            String logicalPath,
            String worldId,
            ContentPackManifest sourcePack
    ) throws IOException {
        if (ApplicationPaths.developmentResourcesFolder() != null) return;
        Path project = ApplicationPaths.activeProjectFolder();
        if (project == null) return;
        Path manifestPath = project.resolve(ContentPackManifest.MANIFEST_PATH);
        if (!Files.isRegularFile(manifestPath)) return;

        String normalizedPath = PackPaths.normalize(logicalPath);
        String normalizedWorldId = normalize(worldId);
        if (normalizedWorldId.isBlank()) {
            throw new IOException("A copied world must have an ID.");
        }
        Properties properties = new Properties();
        ContentPackManifest projectManifest;
        try (InputStream input = Files.newInputStream(manifestPath)) {
            properties.load(input);
        }
        try (InputStream input = Files.newInputStream(manifestPath)) {
            projectManifest = ContentPackManifest.read(input);
        }

        int worldCount = integer(properties, "world.count");
        boolean declared = false;
        for (int index = 0; index < worldCount; index++) {
            if (normalizedPath.equals(properties.getProperty("world." + index + ".path", ""))) {
                declared = true;
                break;
            }
        }
        if (!declared) {
            properties.setProperty("world." + worldCount++ + ".path", normalizedPath);
            properties.setProperty("world.count", String.valueOf(worldCount));
        }

        if (sourcePack != null && !sourcePack.id().equals(projectManifest.id())) {
            int dependencyCount = integer(properties, "dependency.count");
            boolean dependencyDeclared = false;
            for (int index = 0; index < dependencyCount; index++) {
                if (sourcePack.id().equals(normalize(properties.getProperty("dependency." + index + ".id")))) {
                    dependencyDeclared = true;
                    break;
                }
            }
            if (!dependencyDeclared) {
                String prefix = "dependency." + dependencyCount++ + ".";
                properties.setProperty(prefix + "id", sourcePack.id());
                properties.setProperty(prefix + "minVersion", sourcePack.version());
                properties.setProperty(prefix + "maxVersionExclusive", "");
                properties.setProperty("dependency.count", String.valueOf(dependencyCount));
            }

            int overrideCount = integer(properties, "override.count");
            boolean overrideDeclared = false;
            for (int index = 0; index < overrideCount; index++) {
                String prefix = "override." + index + ".";
                if (sourcePack.id().equals(normalize(properties.getProperty(prefix + "targetPack")))
                        && "world".equals(normalize(properties.getProperty(prefix + "type")))
                        && normalizedWorldId.equals(normalize(properties.getProperty(prefix + "id")))) {
                    overrideDeclared = true;
                    break;
                }
            }
            if (!overrideDeclared) {
                String prefix = "override." + overrideCount++ + ".";
                properties.setProperty(prefix + "targetPack", sourcePack.id());
                properties.setProperty(prefix + "type", "world");
                properties.setProperty(prefix + "id", normalizedWorldId);
                properties.setProperty("override.count", String.valueOf(overrideCount));
            }
        }
        publishManifest(properties, manifestPath);
    }

    private static Map<String, Target> lowerLayerTargets(
            String catalogFile,
            Set<String> contentTypes,
            String projectId
    ) throws IOException {
        Map<String, Target> result = new LinkedHashMap<>();
        for (ContentRepository.CatalogSegment segment
                : ContentRepository.shared().snapshot().catalogSegments(catalogFile)) {
            if (segment.manifest().id().equals(projectId)) continue;
            Properties properties = segment.asProperties();
            for (String type : contentTypes) {
                for (String id : ids(properties, normalize(type))) {
                    result.put(key(type, id), new Target(
                            segment.manifest().id(), segment.manifest().version()));
                }
            }
        }
        return result;
    }

    private static Set<String> ids(Properties properties, String contentType) throws IOException {
        return switch (contentType) {
            case "battle_skill" -> indexed(properties, "skill", "id");
            case "battle_status" -> indexed(properties, "status", "id");
            case "material" -> indexed(properties, "material", "id");
            case "first_person_rig" -> indexed(properties, "rig", "id");
            case "first_person_animation_set" -> indexed(properties, "animationSet", "id");
            case "first_person_item_profile" -> indexed(properties, "itemProfile", "itemId");
            case "first_person_weapon_default" -> properties.stringPropertyNames().stream()
                    .filter(name -> name.startsWith("weaponDefault."))
                    .map(name -> normalize(name.substring("weaponDefault.".length())))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            case "first_person_default" -> properties.getProperty("defaultRigId", "").isBlank()
                    ? Set.of() : Set.of("default_rig");
            default -> Set.of();
        };
    }

    private static Set<String> indexed(Properties properties, String root, String idKey) throws IOException {
        int count = integer(properties, root + ".count");
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String id = normalize(properties.getProperty(root + "." + index + "." + idKey));
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    private static void publishManifest(Properties properties, Path path) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "Aether Construction Kit content-pack manifest");
            bytes = output.toByteArray();
        }
        ContentPackManifest.read(new ByteArrayInputStream(bytes));
        Path temporary = path.resolveSibling(path.getFileName() + ".new");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            output.write(bytes);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int integer(Properties properties, String key) throws IOException {
        try {
            return Integer.parseInt(properties.getProperty(key, "0").trim());
        } catch (NumberFormatException error) {
            throw new IOException("Invalid project manifest/catalog count for " + key + ".", error);
        }
    }

    private static String key(String type, String id) {
        return normalize(type) + "\n" + normalize(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Target(String packId, String version) {
    }
}

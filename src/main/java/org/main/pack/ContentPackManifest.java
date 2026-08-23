package org.main.pack;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public record ContentPackManifest(
        int formatVersion,
        PackType type,
        String id,
        String namespace,
        String version,
        String title,
        String author,
        String description,
        String license,
        int contentApiVersion,
        String gameVersionMin,
        String gameVersionMaxExclusive,
        List<String> catalogs,
        List<String> maps,
        List<String> worlds,
        Map<String, String> metadata,
        List<Dependency> dependencies,
        List<DeclaredOverride> overrides
) {
    public static final String MANIFEST_PATH = "aether-pack.properties";
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final int CURRENT_CONTENT_API_VERSION = 1;
    /**
     * Runtime SemVer used when evaluating a pack's supported game-version bounds.
     */
    public static final String CURRENT_GAME_VERSION = "0.8.0";

    private static final Pattern PACK_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z][a-z0-9_]{1,31}");
    private static final Pattern SEMVER = Pattern.compile(
            "[0-9]+[.][0-9]+[.][0-9]+(?:-[0-9A-Za-z-]+(?:[.][0-9A-Za-z-]+)*)?"
                    + "(?:[+][0-9A-Za-z-]+(?:[.][0-9A-Za-z-]+)*)?");

    public ContentPackManifest {
        type = type == null ? PackType.CONTENT : type;
        id = normalized(id);
        namespace = normalized(namespace);
        version = safe(version);
        title = safe(title);
        author = safe(author);
        description = safe(description);
        license = safe(license);
        gameVersionMin = safe(gameVersionMin);
        gameVersionMaxExclusive = safe(gameVersionMaxExclusive);
        catalogs = resourceList(catalogs);
        maps = resourceList(maps);
        worlds = resourceList(worlds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
    }

    public ContentPackManifest(
            int formatVersion,
            PackType type,
            String id,
            String namespace,
            String version,
            String title,
            String author,
            String description,
            String license,
            int contentApiVersion,
            List<Dependency> dependencies,
            List<DeclaredOverride> overrides
    ) {
        this(formatVersion, type, id, namespace, version, title, author, description, license,
                contentApiVersion, "", "", List.of(), List.of(), List.of(), Map.of(), dependencies, overrides);
    }

    public static ContentPackManifest core() {
        return new ContentPackManifest(CURRENT_FORMAT_VERSION, PackType.CONTENT,
                "aether.core", "aether_core", "0.0.0", "Aether Core", "Aether",
                "Bundled game and Construction Kit content.", "Bundled",
                CURRENT_CONTENT_API_VERSION, "", "", List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of());
    }

    public static ContentPackManifest read(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);

        int dependencyCount = integer(properties, "dependency.count", 0);
        List<Dependency> dependencies = new ArrayList<>(dependencyCount);
        for (int index = 0; index < dependencyCount; index++) {
            String prefix = "dependency." + index + ".";
            dependencies.add(new Dependency(
                    properties.getProperty(prefix + "id", ""),
                    properties.getProperty(prefix + "minVersion", ""),
                    properties.getProperty(prefix + "maxVersionExclusive", "")
            ));
        }

        int overrideCount = integer(properties, "override.count", 0);
        List<DeclaredOverride> overrides = new ArrayList<>(overrideCount);
        for (int index = 0; index < overrideCount; index++) {
            String prefix = "override." + index + ".";
            overrides.add(new DeclaredOverride(
                    properties.getProperty(prefix + "targetPack", ""),
                    properties.getProperty(prefix + "type", ""),
                    properties.getProperty(prefix + "id", "")
            ));
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.startsWith("metadata.")) {
                metadata.put(key.substring("metadata.".length()), safe(properties.getProperty(key)));
            }
        }

        ContentPackManifest result = new ContentPackManifest(
                integer(properties, "pack.formatVersion", 0),
                PackType.parse(properties.getProperty("pack.type", "content")),
                properties.getProperty("pack.id", ""),
                properties.getProperty("pack.namespace", ""),
                properties.getProperty("pack.version", ""),
                properties.getProperty("pack.title", ""),
                properties.getProperty("pack.author", ""),
                properties.getProperty("pack.description", ""),
                properties.getProperty("pack.license", ""),
                integer(properties, "contentApiVersion", 0),
                properties.getProperty("game.minVersion", ""),
                properties.getProperty("game.maxVersionExclusive", ""),
                resourceList(properties, "catalog"),
                resourceList(properties, "map"),
                resourceList(properties, "world"),
                metadata,
                dependencies,
                overrides
        );
        result.validate();
        return result;
    }

    public void validate() throws IOException {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IOException("Unsupported pack format version " + formatVersion
                    + "; expected " + CURRENT_FORMAT_VERSION + ".");
        }
        if (contentApiVersion != CURRENT_CONTENT_API_VERSION) {
            throw new IOException("Unsupported content API version " + contentApiVersion
                    + "; expected " + CURRENT_CONTENT_API_VERSION + ".");
        }
        if (!PACK_ID.matcher(id).matches()) {
            throw new IOException("Invalid pack ID: " + id);
        }
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IOException("Invalid pack namespace: " + namespace);
        }
        validatedSemanticVersion(version, "pack version");
        if (title.isBlank() || author.isBlank() || description.isBlank() || license.isBlank()) {
            throw new IOException("Pack title, author, description, and license are required.");
        }
        SemanticVersion minimum = gameVersionMin.isBlank() ? null
                : validatedSemanticVersion(gameVersionMin, "supported game minimum version");
        SemanticVersion maximum = gameVersionMaxExclusive.isBlank() ? null
                : validatedSemanticVersion(gameVersionMaxExclusive, "supported game maximum version");
        if (minimum != null && maximum != null && minimum.compareTo(maximum) >= 0) {
            throw new IOException("Supported game version range is empty.");
        }
        for (String path : declaredResources()) {
            PackPaths.normalize(path);
        }
        requireUnique("catalog", catalogs);
        requireUnique("map", maps);
        requireUnique("world", worlds);
        java.util.Set<String> dependencyIds = new java.util.HashSet<>();
        for (Dependency dependency : dependencies) {
            dependency.validate();
            if (dependency.id().equals(id) || !dependencyIds.add(dependency.id())) {
                throw new IOException("Pack dependencies must be unique and cannot reference the pack itself: "
                        + dependency.id());
            }
        }
        java.util.Set<String> declaredOverrideKeys = new java.util.HashSet<>();
        for (DeclaredOverride override : overrides) {
            override.validate();
            String overrideKey = override.targetPack() + "\n" + override.contentType() + "\n"
                    + override.contentId().toLowerCase(Locale.ROOT);
            if (!declaredOverrideKeys.add(overrideKey)) {
                throw new IOException("Duplicate declared override: " + override.targetPack() + ":"
                        + override.contentType() + ":" + override.contentId());
            }
            boolean dependsOnTarget = dependencies.stream()
                    .anyMatch(dependency -> dependency.id().equals(override.targetPack()));
            if (!dependsOnTarget) {
                throw new IOException("Override target must also be a dependency: " + override.targetPack());
            }
        }
    }

    public List<String> declaredResources() {
        List<String> result = new ArrayList<>(catalogs.size() + maps.size() + worlds.size());
        result.addAll(catalogs);
        result.addAll(maps);
        result.addAll(worlds);
        return List.copyOf(result);
    }

    public boolean supportsGameVersion(String gameVersion) {
        SemanticVersion current = SemanticVersion.parse(gameVersion);
        if (!gameVersionMin.isBlank()
                && current.compareTo(SemanticVersion.parse(gameVersionMin)) < 0) {
            return false;
        }
        return gameVersionMaxExclusive.isBlank()
                || current.compareTo(SemanticVersion.parse(gameVersionMaxExclusive)) < 0;
    }

    private static SemanticVersion validatedSemanticVersion(String value, String label) throws IOException {
        if (!SEMVER.matcher(value).matches()) {
            throw new IOException("Invalid " + label + ": " + value);
        }
        try {
            return SemanticVersion.parse(value);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid " + label + ": " + value, error);
        }
    }

    private static int integer(Properties properties, String key, int fallback) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw new IOException("Invalid integer for " + key + ": " + value, error);
        }
    }

    private static List<String> resourceList(Properties properties, String key) throws IOException {
        int count = integer(properties, key + ".count", 0);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String value = safe(properties.getProperty(key + "." + index + ".path"));
            if (value.isBlank()) {
                throw new IOException("Missing declared " + key + " path at index " + index + ".");
            }
            values.add(value);
        }
        return values;
    }

    private static List<String> resourceList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(ContentPackManifest::safe).filter(value -> !value.isBlank()).toList();
    }

    private static void requireUnique(String type, List<String> values) throws IOException {
        if (values.stream().distinct().count() != values.size()) {
            throw new IOException("Declared " + type + " resources must be unique.");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalized(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    public enum PackType {
        CONTENT,
        AUTHORING_SOURCE;

        static PackType parse(String value) throws IOException {
            String normalized = safe(value).replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new IOException("Unsupported pack type: " + value, error);
            }
        }
    }

    public record Dependency(String id, String minVersion, String maxVersionExclusive) {
        public Dependency {
            id = normalized(id);
            minVersion = safe(minVersion);
            maxVersionExclusive = safe(maxVersionExclusive);
        }

        private void validate() throws IOException {
            if (!PACK_ID.matcher(id).matches()) {
                throw new IOException("Invalid dependency pack ID: " + id);
            }
            SemanticVersion minimum = minVersion.isBlank() ? null
                    : validatedSemanticVersion(minVersion, "dependency minimum version");
            SemanticVersion maximum = maxVersionExclusive.isBlank() ? null
                    : validatedSemanticVersion(maxVersionExclusive, "dependency maximum version");
            if (minimum != null && maximum != null && minimum.compareTo(maximum) >= 0) {
                throw new IOException("Dependency version range is empty for " + id + ".");
            }
        }

        public boolean accepts(String version) {
            SemanticVersion candidate = SemanticVersion.parse(version);
            return (minVersion.isBlank() || candidate.compareTo(SemanticVersion.parse(minVersion)) >= 0)
                    && (maxVersionExclusive.isBlank()
                    || candidate.compareTo(SemanticVersion.parse(maxVersionExclusive)) < 0);
        }
    }

    public record DeclaredOverride(String targetPack, String contentType, String contentId) {
        public DeclaredOverride {
            targetPack = normalized(targetPack);
            contentType = normalized(contentType);
            contentId = safe(contentId);
        }

        private void validate() throws IOException {
            if (!PACK_ID.matcher(targetPack).matches() || contentType.isBlank() || contentId.isBlank()) {
                throw new IOException("Invalid declared override for " + targetPack + ":" + contentId);
            }
        }
    }
}

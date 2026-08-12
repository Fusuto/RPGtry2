package org.main.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/** Versioned, refreshable catalog for data-driven material tiers. */
public final class MaterialCatalog {
    public static final int SCHEMA_VERSION = 2;
    public static final String RESOURCE_PATH = "assets/editor/content/material.properties";
    public static final Path SOURCE_PATH = Path.of("src", "main", "resources", RESOURCE_PATH);

    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(loadSnapshot());

    private MaterialCatalog() {
    }

    public static Snapshot snapshot() {
        return CURRENT.get();
    }

    public static Snapshot refresh() {
        Snapshot loaded = loadSnapshot();
        CURRENT.set(loaded);
        return loaded;
    }

    static void installSnapshot(List<MaterialDefinition> definitions) {
        CURRENT.set(new Snapshot(normalize(definitions)));
    }

    public static void save(List<MaterialDefinition> definitions) throws IOException {
        List<MaterialDefinition> normalized = write(definitions);
        CURRENT.set(new Snapshot(normalized));
    }

    /** Writes a complete catalog transaction without publishing it to live runtime readers. */
    public static List<MaterialDefinition> write(List<MaterialDefinition> definitions) throws IOException {
        validateForSave(definitions);
        List<MaterialDefinition> normalized = normalize(definitions);
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", String.valueOf(SCHEMA_VERSION));
        properties.setProperty("material.count", String.valueOf(normalized.size()));
        for (int i = 0; i < normalized.size(); i++) {
            MaterialDefinition material = normalized.get(i);
            String prefix = "material." + i + ".";
            properties.setProperty(prefix + "id", material.id());
            properties.setProperty(prefix + "name", material.displayName());
            properties.setProperty(prefix + "family", material.family().name());
            properties.setProperty(prefix + "order", String.valueOf(material.sortOrder()));
            properties.setProperty(prefix + "statBonus", String.valueOf(material.statBonus()));
            properties.setProperty(prefix + "priceMultiplier", String.valueOf(material.priceMultiplier()));
            properties.setProperty(prefix + "tint", material.tintHex());
            properties.setProperty(prefix + "tintStrength", String.valueOf(material.tintStrength()));
            properties.setProperty(prefix + "rawResourceItemId", material.rawResourceItemId());
            properties.setProperty(prefix + "processedResourceItemId", material.processedResourceItemId());
            properties.setProperty(prefix + "lanternCapacitySeconds", String.valueOf(material.lanternFuelCapacitySeconds()));
            properties.setProperty(prefix + "lanternBurnSecondsPerLog", String.valueOf(material.lanternBurnSecondsPerLog()));
        }

        Files.createDirectories(SOURCE_PATH.toAbsolutePath().getParent());
        Path temporary = SOURCE_PATH.resolveSibling(SOURCE_PATH.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Aether material catalog schema " + SCHEMA_VERSION);
        }
        try {
            Files.move(temporary, SOURCE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, SOURCE_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
        return normalized;
    }

    private static void validateForSave(List<MaterialDefinition> definitions) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (definitions == null) {
            return;
        }
        for (MaterialDefinition definition : definitions) {
            if (definition == null || definition.id().equals("none")) {
                continue;
            }
            if (definition.id().isBlank()) {
                throw new IllegalArgumentException("Material IDs cannot be blank.");
            }
            if (definition.family() == GearMaterial.MaterialFamily.NONE) {
                throw new IllegalArgumentException("Authored material " + definition.id() + " must have a family.");
            }
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate material ID: " + definition.id());
            }
            if (definition.family() == GearMaterial.MaterialFamily.METAL
                    && definition.lanternFuelCapacitySeconds() <= 0) {
                throw new IllegalArgumentException("Metal material " + definition.id()
                        + " needs a positive lantern capacity.");
            }
            if (definition.family() == GearMaterial.MaterialFamily.WOOD
                    && definition.lanternBurnSecondsPerLog() <= 0) {
                throw new IllegalArgumentException("Wood material " + definition.id()
                        + " needs a positive burn time per log.");
            }
        }
    }

    private static Snapshot loadSnapshot() {
        Properties properties = new Properties();
        boolean loaded = false;
        if (Files.isRegularFile(SOURCE_PATH)) {
            try (InputStream input = Files.newInputStream(SOURCE_PATH)) {
                properties.load(input);
                loaded = true;
            } catch (IOException ignored) {
                // The packaged catalog remains available below.
            }
        }
        if (!loaded) {
            try (InputStream input = MaterialCatalog.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
                if (input != null) {
                    properties.load(input);
                    loaded = true;
                }
            } catch (IOException ignored) {
                // Built-in definitions are the final safe fallback.
            }
        }
        return new Snapshot(loaded ? parse(properties) : builtIns());
    }

    private static List<MaterialDefinition> parse(Properties properties) {
        int count = parseInt(properties.getProperty("material.count"), 0);
        List<MaterialDefinition> materials = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "material." + i + ".";
            String id = MaterialDefinition.normalizeId(properties.getProperty(prefix + "id"));
            if (id.isBlank()) {
                continue;
            }
            materials.add(new MaterialDefinition(
                    id,
                    properties.getProperty(prefix + "name", id),
                    parseFamily(properties.getProperty(prefix + "family")),
                    parseInt(properties.getProperty(prefix + "order"), i),
                    parseInt(properties.getProperty(prefix + "statBonus"), 0),
                    parseDouble(properties.getProperty(prefix + "priceMultiplier"), 1.0),
                    parseColor(properties.getProperty(prefix + "tint"), 0xB4B4B4),
                    (float) parseDouble(properties.getProperty(prefix + "tintStrength"), 0.35),
                    properties.getProperty(prefix + "rawResourceItemId", ""),
                    properties.getProperty(prefix + "processedResourceItemId", ""),
                    parseInt(properties.getProperty(prefix + "lanternCapacitySeconds"),
                            parseFamily(properties.getProperty(prefix + "family")) == GearMaterial.MaterialFamily.METAL ? 600 : 0),
                    parseInt(properties.getProperty(prefix + "lanternBurnSecondsPerLog"),
                            parseFamily(properties.getProperty(prefix + "family")) == GearMaterial.MaterialFamily.WOOD ? 300 : 0)
            ));
        }
        return normalize(materials);
    }

    private static List<MaterialDefinition> normalize(List<MaterialDefinition> definitions) {
        Map<String, MaterialDefinition> byId = new LinkedHashMap<>();
        byId.put("none", builtIns().getFirst());
        if (definitions != null) {
            for (MaterialDefinition definition : definitions) {
                if (definition != null && !definition.id().isBlank() && !definition.id().equals("none")) {
                    byId.put(definition.id(), definition);
                }
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingInt(MaterialDefinition::sortOrder)
                        .thenComparing(MaterialDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<MaterialDefinition> builtIns() {
        return List.of(
                material("none", "None", GearMaterial.MaterialFamily.NONE, 0, 0, 1.0, 0xB4B4B4, 0.0f, "", ""),
                material("copper", "Copper", GearMaterial.MaterialFamily.METAL, 10, 1, 1.0, 0x96522C, 0.45f, "COPPER_ORE", "COPPER_BAR"),
                material("tin", "Tin", GearMaterial.MaterialFamily.METAL, 20, 0, 1.0, 0xCDD4DC, 0.35f, "custom_item_tin_ore", ""),
                material("bronze", "Bronze", GearMaterial.MaterialFamily.METAL, 30, 2, 1.25, 0xB07E3E, 0.45f, "", "custom_item_bronze_bar"),
                material("silver", "Silver", GearMaterial.MaterialFamily.METAL, 40, 2, 1.6, 0xB4CDDC, 0.35f, "", ""),
                material("iron", "Iron", GearMaterial.MaterialFamily.METAL, 50, 3, 1.5, 0xA5AAB0, 0.35f, "custom_item_iron_ore", "custom_item_iron_bar"),
                material("steel", "Steel", GearMaterial.MaterialFamily.METAL, 60, 4, 2.0, 0xCDD2D6, 0.35f, "custom_item_coal_iron_mix", "custom_item_steel_bar"),
                material("oak", "Oak", GearMaterial.MaterialFamily.WOOD, 70, 1, 1.0, 0x966838, 0.45f, "custom_item_oak_log", ""),
                material("yew", "Yew", GearMaterial.MaterialFamily.WOOD, 80, 2, 1.4, 0x5E8248, 0.45f, "", ""),
                material("ironwood", "Ironwood", GearMaterial.MaterialFamily.WOOD, 90, 3, 1.9, 0x5C5C52, 0.45f, "", ""),
                material("leather", "Leather", GearMaterial.MaterialFamily.HIDE, 100, 1, 1.0, 0x78482C, 0.45f, "", "")
        );
    }

    private static MaterialDefinition material(String id, String name, GearMaterial.MaterialFamily family,
                                                 int order, int bonus, double multiplier, int color,
                                                 float strength, String raw, String processed) {
        return new MaterialDefinition(id, name, family, order, bonus, multiplier, color, strength, raw, processed);
    }

    private static GearMaterial.MaterialFamily parseFamily(String value) {
        try {
            return GearMaterial.MaterialFamily.valueOf(value == null ? "NONE" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GearMaterial.MaterialFamily.NONE;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            String normalized = value == null ? "" : value.trim().replace("#", "");
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static final class Snapshot {
        private final List<MaterialDefinition> definitions;
        private final Map<String, MaterialDefinition> byId;

        private Snapshot(List<MaterialDefinition> definitions) {
            this.definitions = List.copyOf(definitions);
            Map<String, MaterialDefinition> mapped = new LinkedHashMap<>();
            for (MaterialDefinition definition : definitions) {
                mapped.put(definition.id(), definition);
            }
            this.byId = Map.copyOf(mapped);
        }

        public List<MaterialDefinition> definitions() {
            return definitions;
        }

        public MaterialDefinition find(String id) {
            return byId.get(MaterialDefinition.normalizeId(id));
        }

        public MaterialDefinition require(String id) {
            MaterialDefinition found = find(id);
            return found == null ? byId.get("none") : found;
        }

        public boolean contains(String id) {
            return find(id) != null;
        }
    }
}

package org.main.pack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Lightweight exact-version gate run before a project, local archive, or Workshop folder is mounted.
 */
final class PackDataSchemas {
    static final int MAX_PROPERTIES_BYTES = 16 * 1024 * 1024;
    private static final Map<String, Schema> CONTENT = Map.ofEntries(
            Map.entry("dialogue.properties", new Schema("dialogue.schemaVersion", 3, "dialogue.count")),
            Map.entry("quest.properties", new Schema("quest.schemaVersion", 3, "quest.count")),
            Map.entry("item.properties", new Schema("item.schemaVersion", 8, "item.count")),
            Map.entry("mob.properties", new Schema("mob.schemaVersion", 3, "mob.count")),
            Map.entry("limb.properties", new Schema("limb.schemaVersion", 2, "limb.count")),
            Map.entry("npc.properties", new Schema("npc.schemaVersion", 2, "npc.count")),
            Map.entry("furniture.properties", new Schema("furniture.schemaVersion", 2, "furniture.count")),
            Map.entry("gathering_node.properties", new Schema(
                    "gatheringNode.schemaVersion", 1, "gatheringNode.count")),
            Map.entry("cooking_recipe.properties", new Schema(
                    "cookingRecipe.schemaVersion", 1, "cookingRecipe.count")),
            Map.entry("crafting_recipe.properties", new Schema(
                    "craftingRecipe.schemaVersion", 2, "craftingRecipe.count")),
            Map.entry("skill.properties", new Schema("schemaVersion", 2, "skill.count")),
            Map.entry("status.properties", new Schema("schemaVersion", 2, "status.count")),
            Map.entry("material.properties", new Schema("schemaVersion", 2, "material.count")),
            Map.entry("first_person_rig.properties", new Schema("schemaVersion", 4, "rig.count"))
    );

    private PackDataSchemas() {
    }

    static void validate(String logicalPath, byte[] bytes) throws IOException {
        String normalized = logicalPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".properties") || normalized.startsWith("sources/")
                || normalized.equals(ContentPackManifest.MANIFEST_PATH)) {
            return;
        }
        if (bytes.length > MAX_PROPERTIES_BYTES) {
            throw new IOException("Properties catalog exceeds 16 MiB: " + logicalPath);
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        Schema schema = normalized.contains("/editor/content/") ? CONTENT.get(fileName) : null;
        if (schema == null && (normalized.contains("/editor/maps/")
                || normalized.contains("/editor/worlds/"))) {
            schema = new Schema("formatVersion", 1, "");
        }
        if (schema == null) return;
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(bytes));
        int actual;
        try {
            actual = Integer.parseInt(properties.getProperty(schema.versionKey(), "-1").trim());
        } catch (NumberFormatException error) {
            throw new IOException("Invalid schema version in " + logicalPath + ".", error);
        }
        if (actual != schema.version()) {
            throw new IOException("Unsupported schema version " + actual + " in " + logicalPath
                    + "; expected exactly " + schema.version() + ".");
        }
        if (!schema.countKey().isBlank()) {
            int count;
            try {
                count = Integer.parseInt(properties.getProperty(schema.countKey(), "-1").trim());
            } catch (NumberFormatException error) {
                throw new IOException("Invalid record count in " + logicalPath + ".", error);
            }
            if (count < 0 || count > 1_000_000) {
                throw new IOException("Invalid record count in " + logicalPath + ": " + count);
            }
        }
    }

    private record Schema(String versionKey, int version, String countKey) {
    }
}

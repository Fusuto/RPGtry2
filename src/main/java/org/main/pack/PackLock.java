package org.main.pack;

import java.util.List;
import java.io.IOException;
import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

public record PackLock(int contentApiVersion, List<Entry> packs) {
    public PackLock {
        packs = packs == null ? List.of() : List.copyOf(packs);
    }

    public void writeTo(Properties properties, String prefix) {
        properties.setProperty(prefix + "contentApiVersion", String.valueOf(contentApiVersion));
        properties.setProperty(prefix + "count", String.valueOf(packs.size()));
        for (int index = 0; index < packs.size(); index++) {
            Entry entry = packs.get(index);
            String item = prefix + index + ".";
            properties.setProperty(item + "id", entry.packId());
            properties.setProperty(item + "version", entry.version());
            properties.setProperty(item + "digest", entry.digest());
        }
    }

    public static PackLock readFrom(Properties properties, String prefix) throws IOException {
        int api = integer(properties, prefix + "contentApiVersion");
        int count = integer(properties, prefix + "count");
        if (count < 0 || count > 1_000) {
            throw new IOException("Invalid save content-pack count: " + count);
        }
        java.util.ArrayList<Entry> entries = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String item = prefix + index + ".";
            String id = required(properties, item + "id");
            String version = required(properties, item + "version");
            String digest = required(properties, item + "digest");
            entries.add(new Entry(id, version, digest));
        }
        PackLock lock = new PackLock(api, entries);
        lock.validate();
        return lock;
    }

    public void validate() throws IOException {
        if (contentApiVersion != ContentPackManifest.CURRENT_CONTENT_API_VERSION) {
            throw new IOException("Unsupported save content API version: " + contentApiVersion);
        }
        Set<String> ids = new HashSet<>();
        for (Entry entry : packs) {
            if (!entry.packId().matches("[a-z0-9][a-z0-9._-]{2,63}") || !ids.add(entry.packId())) {
                throw new IOException("Invalid or duplicate save pack ID: " + entry.packId());
            }
            try {
                SemanticVersion.parse(entry.version());
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid save pack version for " + entry.packId() + ": "
                        + entry.version(), error);
            }
            if (!entry.digest().matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid save pack digest for " + entry.packId() + ".");
            }
        }
    }

    public String describe() {
        if (packs.isEmpty()) {
            return "no external packs";
        }
        return packs.stream().map(entry -> entry.packId() + "@" + entry.version()
                + "#" + entry.digest()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static int integer(Properties properties, String key) throws IOException {
        try {
            return Integer.parseInt(required(properties, key));
        } catch (NumberFormatException error) {
            throw new IOException("Invalid integer save property: " + key, error);
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required save property: " + key);
        }
        return value.trim();
    }

    public record Entry(String packId, String version, String digest) {
        public Entry {
            packId = packId == null ? "" : packId.trim();
            version = version == null ? "" : version.trim();
            digest = digest == null ? "" : digest.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}

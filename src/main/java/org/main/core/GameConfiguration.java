package org.main.core;

import org.main.engine.ApplicationPaths;
import org.main.engine.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GameConfiguration {
    private static final Path CONFIG_PATH = ApplicationPaths.dataFolder().resolve("configuration.properties");
    private static final String PACKAGED_CONFIG_PATH = "assets/configuration.properties";
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Properties PROPERTIES = new Properties();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Map<String, Integer> INTEGER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Double> DOUBLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> BOOLEAN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, KeyDescriptor> KEY_REGISTRY = new LinkedHashMap<>();

    static {
        loadPackagedDefaults();
        load();
    }

    private GameConfiguration() {
    }

    public static int intValue(String key, int fallback) {
        Integer cached = INTEGER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            INTEGER_CACHE.put(key, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static double doubleValue(String key, double fallback) {
        Double cached = DOUBLE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            DOUBLE_CACHE.put(key, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static String stringValue(String key, String fallback) {
        String value = PROPERTIES.getProperty(key, DEFAULTS.getOrDefault(key, fallback));
        return value == null ? fallback : value;
    }

    public static boolean hasValue(String key) {
        return key != null && !key.isBlank()
                && (PROPERTIES.containsKey(key) || DEFAULTS.containsKey(key));
    }

    public static boolean booleanValue(String key, boolean fallback) {
        Boolean cached = BOOLEAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1")) {
            BOOLEAN_CACHE.put(key, true);
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0")) {
            BOOLEAN_CACHE.put(key, false);
            return false;
        }
        return fallback;
    }

    public static void setValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        String safeValue = value == null ? "" : value.trim();
        KeyDescriptor descriptor = descriptorFor(key);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown configuration key: " + key);
        }
        descriptor.requireValid(safeValue);
        String previousValue = PROPERTIES.getProperty(key);
        if (safeValue.equals(previousValue)) {
            return;
        }
        PROPERTIES.setProperty(key, safeValue);
        INTEGER_CACHE.remove(key);
        DOUBLE_CACHE.remove(key);
        BOOLEAN_CACHE.remove(key);
        REVISION.incrementAndGet();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Runtime config edits should not crash editor/game tools.
        }
    }

    public static void removeValue(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String packagedDefault = DEFAULTS.get(key);
        boolean changed;
        if (packagedDefault == null) {
            changed = PROPERTIES.remove(key) != null;
        } else {
            changed = !packagedDefault.equals(PROPERTIES.getProperty(key));
            PROPERTIES.setProperty(key, packagedDefault);
        }
        INTEGER_CACHE.remove(key);
        DOUBLE_CACHE.remove(key);
        BOOLEAN_CACHE.remove(key);
        if (!changed) {
            return;
        }
        REVISION.incrementAndGet();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Runtime config edits should not crash editor/game tools.
        }
    }

    /**
     * Monotonically increases when a runtime setting changes. Renderers can use
     * this instead of reparsing every configuration value every frame.
     */
    public static long revision() {
        return REVISION.get();
    }

    public static Map<String, KeyDescriptor> registeredKeys() {
        return Map.copyOf(KEY_REGISTRY);
    }

    private static void load() {
        PROPERTIES.putAll(DEFAULTS);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.isRegularFile(CONFIG_PATH)) {
                try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                    Properties installed = new Properties();
                    installed.load(inputStream);
                    for (String key : installed.stringPropertyNames()) {
                        KeyDescriptor descriptor = descriptorFor(key);
                        if (descriptor != null && descriptor.valid(installed.getProperty(key, ""))) {
                            String value = installed.getProperty(key, "").trim().replace('\\', '/');
                            if (!value.startsWith("data/")) {
                                PROPERTIES.setProperty(key, installed.getProperty(key));
                            }
                        }
                    }
                }
            }
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Configuration is a convenience layer; bad disk state should not prevent the game from booting.
        }
    }

    private static void loadPackagedDefaults() {
        try (InputStream inputStream = AssetLoader.openAssetStream(PACKAGED_CONFIG_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Packaged configuration is missing: " + PACKAGED_CONFIG_PATH);
            }

            Properties packagedDefaults = new Properties();
            packagedDefaults.load(inputStream);
            for (String key : packagedDefaults.stringPropertyNames()) {
                String value = packagedDefaults.getProperty(key);
                DEFAULTS.put(key, value);
                KEY_REGISTRY.put(key, inferDescriptor(key, value));
            }
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void writeDefaultsAndCurrentValues() throws IOException {
        Properties output = new Properties();
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            output.setProperty(entry.getKey(), PROPERTIES.getProperty(entry.getKey(), entry.getValue()));
        }
        for (String key : PROPERTIES.stringPropertyNames()) {
            if (!output.containsKey(key) && descriptorFor(key) != null) {
                output.setProperty(key, PROPERTIES.getProperty(key));
            }
        }

        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            output.store(outputStream, "Aether editable gameplay configuration");
        }
    }

    private static String configuredValue(String key) {
        String value = PROPERTIES.getProperty(key);
        return value == null ? DEFAULTS.get(key) : value;
    }

    private static KeyDescriptor descriptorFor(String key) {
        KeyDescriptor descriptor = KEY_REGISTRY.get(key);
        if (descriptor != null) {
            return descriptor;
        }
        if (key != null && key.startsWith("smithing.xpPerBar.")) {
            return new KeyDescriptor(ValueType.INTEGER, 0.0, null);
        }
        return null;
    }

    private static KeyDescriptor inferDescriptor(String key, String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.equalsIgnoreCase("true") || safe.equalsIgnoreCase("false")) {
            return new KeyDescriptor(ValueType.BOOLEAN, null, null);
        }
        try {
            Integer.parseInt(safe);
            return numericDescriptor(key, ValueType.INTEGER);
        } catch (NumberFormatException ignored) {
        }
        try {
            Double.parseDouble(safe);
            return numericDescriptor(key, ValueType.DOUBLE);
        } catch (NumberFormatException ignored) {
            return new KeyDescriptor(ValueType.STRING, null, null);
        }
    }

    private static KeyDescriptor numericDescriptor(String key, ValueType type) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("chance") || normalized.contains("volume")) {
            return new KeyDescriptor(type, 0.0, 1.0);
        }
        return new KeyDescriptor(type, null, null);
    }

    public enum ValueType {INTEGER, DOUBLE, BOOLEAN, STRING}

    public record KeyDescriptor(ValueType type, Double minimum, Double maximum) {
        public boolean valid(String value) {
            try {
                return switch (type) {
                    case INTEGER -> inRange(Integer.parseInt(value.trim()));
                    case DOUBLE -> inRange(Double.parseDouble(value.trim()));
                    case BOOLEAN -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                            || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no")
                            || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off")
                            || value.equals("1") || value.equals("0");
                    case STRING -> true;
                };
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        public void requireValid(String value) {
            if (!valid(value)) {
                throw new IllegalArgumentException("Invalid " + type.name().toLowerCase(java.util.Locale.ROOT)
                        + " configuration value '" + value + "'"
                        + (minimum == null && maximum == null ? "." : " in range " + minimum + ".." + maximum + "."));
            }
        }

        private boolean inRange(double value) {
            return Double.isFinite(value)
                    && (minimum == null || value >= minimum)
                    && (maximum == null || value <= maximum);
        }
    }

}

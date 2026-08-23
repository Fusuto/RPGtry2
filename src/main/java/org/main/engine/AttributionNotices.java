package org.main.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Renders the canonical attribution manifest into the packaged notice and in-game credits.
 */
public final class AttributionNotices {
    public static final String MANIFEST_PATH = "assets/attributions.properties";
    public static final String NOTICE_PATH = "assets/attributions.txt";

    private AttributionNotices() {
    }

    public static String loadRendered() throws IOException {
        try (InputStream input = AssetLoader.openAssetStream(MANIFEST_PATH)) {
            if (input == null) throw new IOException("Missing attribution manifest: " + MANIFEST_PATH);
            Properties properties = new Properties();
            properties.load(input);
            return render(properties);
        }
    }

    public static void verifyPackagedNotice() throws IOException {
        String expected = loadRendered();
        try (InputStream input = AssetLoader.openAssetStream(NOTICE_PATH)) {
            if (input == null) throw new IOException("Missing generated attribution notice: " + NOTICE_PATH);
            String actual = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            if (!expected.equals(actual)) {
                throw new IOException("Generated attribution notice is stale; run scripts/generate-attributions.ps1.");
            }
        }
    }

    public static String render(Properties properties) throws IOException {
        int count;
        try {
            count = Integer.parseInt(properties.getProperty("attribution.count", "-1"));
        } catch (NumberFormatException error) {
            throw new IOException("Invalid attribution.count.", error);
        }
        if (count < 0 || count > 10_000) throw new IOException("Invalid attribution.count: " + count);
        StringBuilder result = new StringBuilder("Aether Third-Party Credits\n");
        for (int index = 0; index < count; index++) {
            String prefix = "attribution." + index + ".";
            result.append('\n').append(required(properties, prefix + "title")).append('\n')
                    .append("Credit: ").append(required(properties, prefix + "credit")).append('\n')
                    .append("License: ").append(required(properties, prefix + "license")).append('\n')
                    .append("Source: ").append(required(properties, prefix + "source")).append('\n');
        }
        return result.toString();
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key, "").trim();
        if (value.isBlank()) throw new IOException("Missing attribution property: " + key);
        return value;
    }
}

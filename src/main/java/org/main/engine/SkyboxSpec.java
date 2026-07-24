package org.main.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkyboxSpec(
        String back,
        String bottom,
        String front,
        String left,
        String right,
        String top
) {
    public static final String PREFIX = "skybox6|";
    public static final SkyboxSpec DEFAULT = new SkyboxSpec(
            "assets/images/skybox/26-07-24-20-59-51_Back.png",
            "assets/images/skybox/26-07-24-20-59-51_Bottom.png",
            "assets/images/skybox/26-07-24-20-59-51_Front.png",
            "assets/images/skybox/26-07-24-20-59-51_Left.png",
            "assets/images/skybox/26-07-24-20-59-51_Right.png",
            "assets/images/skybox/26-07-24-20-59-51_Top.png"
    );

    public SkyboxSpec {
        back = normalize(back);
        bottom = normalize(bottom);
        front = normalize(front);
        left = normalize(left);
        right = normalize(right);
        top = normalize(top);
    }

    public static boolean isCubeSpec(String value) {
        return value != null && value.trim().startsWith(PREFIX);
    }

    public static SkyboxSpec parseOrDefault(String value) {
        if (value == null || value.isBlank() || !isCubeSpec(value)) {
            return DEFAULT;
        }
        Map<String, String> values = new LinkedHashMap<>();
        String[] pieces = value.trim().substring(PREFIX.length()).split("\\|");
        for (String piece : pieces) {
            int equals = piece.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            values.put(piece.substring(0, equals).trim().toLowerCase(), piece.substring(equals + 1).trim());
        }
        return new SkyboxSpec(
                valueOrDefault(values.get("back"), DEFAULT.back),
                valueOrDefault(values.get("bottom"), DEFAULT.bottom),
                valueOrDefault(values.get("front"), DEFAULT.front),
                valueOrDefault(values.get("left"), DEFAULT.left),
                valueOrDefault(values.get("right"), DEFAULT.right),
                valueOrDefault(values.get("top"), DEFAULT.top)
        );
    }

    public String encode() {
        return PREFIX
                + "back=" + back
                + "|bottom=" + bottom
                + "|front=" + front
                + "|left=" + left
                + "|right=" + right
                + "|top=" + top;
    }

    public boolean complete() {
        return !back.isBlank()
                && !bottom.isBlank()
                && !front.isBlank()
                && !left.isBlank()
                && !right.isBlank()
                && !top.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private static String valueOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}

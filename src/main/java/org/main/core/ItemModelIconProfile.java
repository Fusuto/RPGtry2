package org.main.core;

/**
 * Authored camera/model framing for a transparent, model-backed inventory icon.
 * No rendered pixels are persisted; changing the referenced model automatically
 * changes the icon while retaining this framing.
 */
public record ItemModelIconProfile(
        double rotationX,
        double rotationY,
        double rotationZ,
        double zoom,
        double offsetX,
        double offsetY
) {
    public static final double MIN_ZOOM = 0.2;
    public static final double MAX_ZOOM = 4.0;
    public static final double MIN_OFFSET = -1.0;
    public static final double MAX_OFFSET = 1.0;

    public ItemModelIconProfile {
        rotationX = finiteOr(rotationX, -24.0);
        rotationY = finiteOr(rotationY, 35.0);
        rotationZ = finiteOr(rotationZ, 0.0);
        zoom = clamp(finiteOr(zoom, 1.0), MIN_ZOOM, MAX_ZOOM);
        offsetX = clamp(finiteOr(offsetX, 0.0), MIN_OFFSET, MAX_OFFSET);
        offsetY = clamp(finiteOr(offsetY, 0.0), MIN_OFFSET, MAX_OFFSET);
    }

    public static ItemModelIconProfile defaults() {
        return new ItemModelIconProfile(-24.0, 35.0, 0.0, 1.0, 0.0, 0.0);
    }

    public ItemModelIconProfile withRotation(double x, double y, double z) {
        return new ItemModelIconProfile(x, y, z, zoom, offsetX, offsetY);
    }

    public ItemModelIconProfile withZoom(double value) {
        return new ItemModelIconProfile(rotationX, rotationY, rotationZ, value, offsetX, offsetY);
    }

    public ItemModelIconProfile withOffset(double x, double y) {
        return new ItemModelIconProfile(rotationX, rotationY, rotationZ, zoom, x, y);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

package org.main.engine;

public record MapLight(
        String id,
        int x,
        int y,
        int colorRgb,
        double radius,
        double intensity,
        double heightOffset,
        double offsetX,
        double offsetZ,
        double flickerAmount,
        boolean enabled
) {
    public MapLight {
        id = id == null ? "" : id.trim();
        colorRgb &= 0xFFFFFF;
        radius = Math.max(0.1, radius);
        intensity = Math.max(0.0, intensity);
        heightOffset = Double.isFinite(heightOffset) ? heightOffset : 0.65;
        offsetX = clamp(offsetX, -4.0, 4.0);
        offsetZ = clamp(offsetZ, -4.0, 4.0);
        flickerAmount = clamp(flickerAmount, 0.0, 1.0);
    }

    public MapLight(
            String id,
            int x,
            int y,
            int colorRgb,
            double radius,
            double intensity,
            double heightOffset,
            double flickerAmount,
            boolean enabled
    ) {
        this(id, x, y, colorRgb, radius, intensity, heightOffset, 0.0, 0.0, flickerAmount, enabled);
    }

    public MapLight translated(int offsetX, int offsetY, String idPrefix) {
        String prefix = idPrefix == null || idPrefix.isBlank() ? "" : idPrefix;
        return new MapLight(
                prefix + id,
                x + offsetX,
                y + offsetY,
                colorRgb,
                radius,
                intensity,
                heightOffset,
                this.offsetX,
                offsetZ,
                flickerAmount,
                enabled
        );
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}

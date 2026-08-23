package org.main.tools;

record LightPreset(
        String label,
        int colorRgb,
        double radius,
        double intensity,
        double heightOffset,
        double flickerAmount
) {
    @Override
    public String toString() {
        return label;
    }
}


package org.main.experimental;

record RuntimeLight(
        double x,
        double y,
        double z,
        int colorRgb,
        double radius,
        double intensity,
        double flickerAmount,
        double lifetimeSeconds
) {
}

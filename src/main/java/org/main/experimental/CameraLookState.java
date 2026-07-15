package org.main.experimental;

public record CameraLookState(
        double yawOffsetDegrees,
        double pitchOffsetDegrees,
        boolean active
) {
    public static CameraLookState centered() {
        return new CameraLookState(0.0, 0.0, false);
    }
}

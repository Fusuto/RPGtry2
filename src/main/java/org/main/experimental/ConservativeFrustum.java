package org.main.experimental;

/** Conservative horizontal-frustum test shared by scene culling and tests. */
final class ConservativeFrustum {
    private static final double EDGE_PADDING_DEGREES = 75.0;

    private ConservativeFrustum() {
    }

    static boolean includes(
            double deltaX,
            double deltaZ,
            double cameraYawDegrees,
            double fieldOfViewDegrees,
            double maximumDistance,
            double boundsRadius
    ) {
        double radius = Math.max(0.0, boundsRadius);
        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        double limit = Math.max(0.0, maximumDistance) + radius;
        if (distanceSquared > limit * limit) {
            return false;
        }
        double distance = Math.sqrt(distanceSquared);
        if (distance <= radius + 1.0) {
            return true;
        }
        // Camera yaw uses the OpenGL view convention used by LwjglDungeonViewport:
        // north=0, east=-90, south=180, west=90.  Negating deltaX here is
        // essential; the opposite sign classifies the entire east/west forward
        // half-plane as behind the player and produces missing terrain cells.
        double objectYaw = Math.toDegrees(Math.atan2(-deltaX, -deltaZ));
        double angularRadius = Math.toDegrees(Math.asin(Math.min(1.0, radius / distance)));
        double threshold = Math.min(180.0,
                Math.max(1.0, fieldOfViewDegrees) * 0.5
                        + EDGE_PADDING_DEGREES + angularRadius);
        return Math.abs(shortestAngle(cameraYawDegrees, objectYaw)) <= threshold;
    }

    private static double shortestAngle(double source, double target) {
        double difference = (target - source) % 360.0;
        if (difference > 180.0) {
            difference -= 360.0;
        } else if (difference < -180.0) {
            difference += 360.0;
        }
        return difference;
    }
}

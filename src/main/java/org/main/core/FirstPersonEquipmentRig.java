package org.main.core;

import java.util.List;

/**
 * Shared camera-space layout for the player's combat equipment. Both the
 * runtime renderer and Construction Kit preview use these poses so authored
 * item transforms have one interpretation.
 */
public final class FirstPersonEquipmentRig {
    public enum Attachment {
        NONE,
        PRIMARY_HAND,
        SECONDARY_HAND,
        BOTH_HANDS
    }

    public record Pose(
            double x,
            double y,
            double z,
            double normalizedHeight,
            double rotationX,
            double rotationY,
            double rotationZ,
            boolean mirrored,
            Attachment attachment
    ) {
    }

    public static final double PRIMARY_HAND_X = 0.34;
    public static final double PRIMARY_HAND_Y = -0.48;
    public static final double PRIMARY_HAND_Z = -0.82;

    private FirstPersonEquipmentRig() {
    }

    public static Pose weapon(
            EquipmentViewModelProfile profile,
            boolean twoHanded,
            boolean casting
    ) {
        return weapon(profile, twoHanded, casting ? 1.0 : 0.0);
    }

    public static Pose weapon(
            EquipmentViewModelProfile profile,
            boolean twoHanded,
            double castingAmount
    ) {
        double casting = clamp01(castingAmount);
        return offset(profile,
                twoHanded ? 0.10 : 0.38,
                -0.45 - casting * 0.28,
                -0.86,
                0.72,
                -16,
                8,
                -28,
                false,
                Attachment.PRIMARY_HAND);
    }

    public static Pose shield(
            EquipmentViewModelProfile profile,
            boolean casting,
            double blockAmount
    ) {
        return shield(profile, casting ? 1.0 : 0.0, blockAmount);
    }

    public static Pose shield(
            EquipmentViewModelProfile profile,
            double castingAmount,
            double blockAmount
    ) {
        double casting = clamp01(castingAmount);
        double block = clamp01(blockAmount);
        return offset(profile,
                -0.43,
                -0.38 - casting * 0.34 + block * 0.22,
                -0.92 - block * 0.12,
                0.58,
                -block * 12,
                12,
                8,
                false,
                Attachment.SECONDARY_HAND);
    }

    public static List<Pose> chestHands(
            EquipmentViewModelProfile profile,
            boolean twoHanded
    ) {
        if (profile.pairedHands()) {
            return List.of(offset(profile,
                    0.0, -0.48, -0.82, 0.52,
                    0, 0, 0, false, Attachment.BOTH_HANDS));
        }
        if (twoHanded) {
            return List.of(
                    offset(profile,
                            -0.10, -0.43, -0.76, 0.38,
                            4, 12, 20, false, Attachment.SECONDARY_HAND),
                    offset(profile,
                            0.18, -0.34, -0.91, 0.38,
                            -8, -12, -12, true, Attachment.PRIMARY_HAND));
        }
        return List.of(
                offset(profile,
                        -0.34, -0.48, -0.82, 0.38,
                        0, 18, 15, false, Attachment.SECONDARY_HAND),
                offset(profile,
                        0.34, -0.48, -0.82, 0.38,
                        0, -18, -15, true, Attachment.PRIMARY_HAND));
    }

    public static List<Pose> builtInHands(boolean twoHanded, double unarmedStrikeAmount) {
        double strike = clamp01(unarmedStrikeAmount);
        if (twoHanded) {
            return List.of(
                    new Pose(-0.10, -0.43, -0.76, 0.38,
                            4, 12, 20, false, Attachment.SECONDARY_HAND),
                    new Pose(0.18, -0.34, -0.91, 0.38,
                            -8, -12, -12, true, Attachment.PRIMARY_HAND));
        }
        return List.of(
                new Pose(-0.30, -0.50, -0.94, 0.38,
                        0, 0, 12, false, Attachment.SECONDARY_HAND),
                new Pose(0.30, -0.50 + 0.08 * strike, -0.94 - 0.16 * strike, 0.38,
                        0, 0, -12, true, Attachment.PRIMARY_HAND));
    }

    public static boolean followsPrimaryMotion(Pose pose, boolean twoHanded) {
        return pose != null && (pose.attachment() == Attachment.PRIMARY_HAND
                || pose.attachment() == Attachment.BOTH_HANDS
                || (twoHanded && pose.attachment() == Attachment.SECONDARY_HAND));
    }

    private static Pose offset(
            EquipmentViewModelProfile profile,
            double x,
            double y,
            double z,
            double height,
            double rotationX,
            double rotationY,
            double rotationZ,
            boolean mirrored,
            Attachment attachment
    ) {
        EquipmentViewModelProfile value = profile == null
                ? EquipmentViewModelProfile.defaults()
                : profile;
        return new Pose(
                value.positionX() + (x - 0.38),
                value.positionY() + (y + 0.45),
                value.positionZ() + (z + 0.86),
                height / 0.72 * value.normalizedHeight(),
                value.rotationX() + (rotationX + 16),
                value.rotationY() + (rotationY - 8),
                value.rotationZ() + (rotationZ + 28),
                mirrored,
                attachment);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

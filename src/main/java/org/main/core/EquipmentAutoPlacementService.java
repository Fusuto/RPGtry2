package org.main.core;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;
import org.main.experimental.StaticModelPlacementMetadataResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Converts model grip metadata or geometry into the existing authored socket fields. */
public final class EquipmentAutoPlacementService {
    public static final String PRIMARY_GRIP_NODE = "FP_GRIP_PRIMARY";
    public static final String SECONDARY_GRIP_NODE = "FP_GRIP_SECONDARY";

    private EquipmentAutoPlacementService() {
    }

    public static boolean shouldAutoPlace(
            org.main.content.FirstPersonCombatLibrary.ItemProfile existingProfile,
            InventorySystem.ItemType itemType,
            String modelPath
    ) {
        return existingProfile == null
                && modelPath != null && !modelPath.isBlank()
                && (itemType == InventorySystem.ItemType.WEAPON
                || itemType == InventorySystem.ItemType.SHIELD);
    }

    public static PlacementProposal propose(
            String modelPath,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            boolean flipped
    ) throws IOException {
        return propose(StaticModelPlacementMetadataResolver.resolve(modelPath), itemType,
                weaponType, twoHanded, flipped);
    }

    /** Uses the selected animated attachment node to infer the weapon's outward direction. */
    public static PlacementProposal proposeForAttachment(
            String modelPath,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            boolean flipped,
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.ItemProfile profile
    ) throws IOException {
        Vector3d heldDirection = new Vector3d(0, 1, 0);
        Vector3d cameraFacingDirection = null;
        Vector3d attachmentOffset = new Vector3d();
        if ((itemType == InventorySystem.ItemType.WEAPON
                || itemType == InventorySystem.ItemType.SHIELD)
                && content != null && profile != null) {
            FirstPersonCombatLibrary.RigDefinition rig = content.rigFor(profile);
            CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                    content, rig, weaponType, profile, profile.wieldHand());
            LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(definition);
            String bone = FirstPersonAnimationRuntime.resolveAttachmentBone(rig, profile, model);
            if (itemType == InventorySystem.ItemType.WEAPON) {
                Vector3f direction = model.outwardDirectionFromParentNormalized(
                        CharacterModelDefinition.AnimationSlot.IDLE, 0.0, bone);
                heldDirection.set(direction.x, direction.y, direction.z);
                Matrix4f nodeTransform = model.nodeTransformNormalized(
                        CharacterModelDefinition.AnimationSlot.IDLE, 0.0, bone);
                if (nodeTransform != null) {
                    Matrix3d modelToCamera = new Matrix3d()
                            .rotateX(Math.toRadians(rig.rotationX()))
                            .rotateY(Math.toRadians(rig.rotationY()))
                            .rotateZ(Math.toRadians(rig.rotationZ()))
                            .mul(new Matrix3d().set(nodeTransform));
                    cameraFacingDirection = modelToCamera.invert()
                            .transform(new Vector3d(0, 0, 1)).normalize();
                }
            }
            Vector3f gripOffset = model.handGripOffsetNormalized(
                    CharacterModelDefinition.AnimationSlot.IDLE, 0.0, bone);
            attachmentOffset.set(gripOffset.x, gripOffset.y, gripOffset.z);
        }
        PlacementProposal proposal = propose(
                StaticModelPlacementMetadataResolver.resolve(modelPath), itemType,
                weaponType, twoHanded, flipped, heldDirection, cameraFacingDirection);
        if (attachmentOffset.lengthSquared() < 0.000001) return proposal;
        EquipmentViewModelProfile socket = proposal.socket();
        EquipmentViewModelProfile offsetSocket = new EquipmentViewModelProfile(
                socket.positionX() + attachmentOffset.x,
                socket.positionY() + attachmentOffset.y,
                socket.positionZ() + attachmentOffset.z,
                socket.rotationX(), socket.rotationY(), socket.rotationZ(),
                socket.normalizedHeight(), socket.swingAxisX(), socket.swingAxisY(),
                socket.swingAxisZ(), socket.pairedHands());
        List<String> diagnostics = new ArrayList<>(proposal.diagnostics());
        diagnostics.add("Grip was offset from the wrist joint to the palm center.");
        return new PlacementProposal(offsetSocket, proposal.primaryGrip(),
                proposal.secondaryGrip(), proposal.source(), proposal.confidence(),
                List.copyOf(diagnostics), proposal.flipped());
    }

    public static PlacementProposal propose(
            StaticModelPlacementMetadataResolver.Metadata metadata,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            boolean flipped
    ) {
        return propose(metadata, itemType, weaponType, twoHanded, flipped,
                new Vector3d(0, 1, 0), null);
    }

    public static PlacementProposal propose(
            StaticModelPlacementMetadataResolver.Metadata metadata,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            boolean flipped,
            Vector3d heldDirection
    ) {
        return propose(metadata, itemType, weaponType, twoHanded, flipped,
                heldDirection, null);
    }

    public static PlacementProposal propose(
            StaticModelPlacementMetadataResolver.Metadata metadata,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            boolean flipped,
            Vector3d heldDirection,
            Vector3d cameraFacingDirection
    ) {
        if (metadata == null || metadata.vertices().size() < 3) {
            throw new IllegalArgumentException("Model has no usable placement geometry.");
        }
        InventorySystem.ItemType type = itemType == null
                ? InventorySystem.ItemType.WEAPON : itemType;
        if (type != InventorySystem.ItemType.WEAPON && type != InventorySystem.ItemType.SHIELD) {
            throw new IllegalArgumentException("Automatic placement supports weapons and shields only.");
        }
        List<String> diagnostics = new ArrayList<>();
        for (String duplicate : metadata.duplicateNodeNames()) {
            if (duplicate.equalsIgnoreCase(PRIMARY_GRIP_NODE)
                    || duplicate.equalsIgnoreCase(SECONDARY_GRIP_NODE)) {
                diagnostics.add("Duplicate grip node " + duplicate + "; the first transform was used.");
            }
        }
        Matrix4d primaryMarker = metadata.node(PRIMARY_GRIP_NODE);
        Vector3d primary;
        Vector3d secondary = null;
        Matrix3d rotation;
        PlacementSource source;
        Confidence confidence;
        if (primaryMarker != null && finite(primaryMarker)) {
            primary = primaryMarker.getTranslation(new Vector3d());
            Quaterniond markerRotation = primaryMarker.getUnnormalizedRotation(new Quaterniond()).normalize();
            rotation = markerRotation.invert().get(new Matrix3d());
            source = PlacementSource.MODEL_MARKERS;
            confidence = Confidence.EXACT;
            Matrix4d secondaryMarker = metadata.node(SECONDARY_GRIP_NODE);
            if (twoHanded && type == InventorySystem.ItemType.WEAPON
                    && secondaryMarker != null && finite(secondaryMarker)) {
                secondary = secondaryMarker.getTranslation(new Vector3d());
            } else if (twoHanded && type == InventorySystem.ItemType.WEAPON) {
                diagnostics.add("No FP_GRIP_SECONDARY node was found; an off-hand grip was estimated.");
            }
        } else if (type == InventorySystem.ItemType.SHIELD) {
            Heuristic heuristic = shieldHeuristic(metadata, flipped);
            primary = heuristic.primary();
            rotation = heuristic.rotation();
            source = PlacementSource.GEOMETRY_GUESS;
            confidence = heuristic.confidence();
            diagnostics.add("Shield placement was inferred from its thinnest axis.");
        } else {
            Heuristic heuristic = weaponHeuristic(
                    metadata, flipped, heldDirection, cameraFacingDirection);
            primary = heuristic.primary();
            rotation = heuristic.rotation();
            source = PlacementSource.GEOMETRY_GUESS;
            confidence = heuristic.confidence();
            diagnostics.add("Grip placement was inferred from the model's narrowest end.");
        }
        if (twoHanded && type == InventorySystem.ItemType.WEAPON && secondary == null) {
            Vector3d direction = new Vector3d(metadata.axes().major());
            ProjectionRange range = projectionRange(metadata.vertices(), metadata.bounds().center(), direction);
            double sign = direction.dot(new Vector3d(metadata.bounds().center()).sub(primary)) >= 0 ? 1 : -1;
            secondary = new Vector3d(primary).fma(sign * range.length() * 0.22, direction);
        }
        if (confidence == Confidence.LOW) {
            diagnostics.add(type == InventorySystem.ItemType.SHIELD
                    ? "The shield face is ambiguous; use Flip Shield Face or Edit Grip."
                    : "Both model ends look similar; use Flip Grip End or Edit Grip if necessary.");
        }
        double normalizedHeight = defaultHeight(type, weaponType);
        EquipmentViewModelProfile socket = socketFor(
                metadata.bounds(), primary, rotation, normalizedHeight);
        return new PlacementProposal(socket, primary, secondary, source, confidence,
                List.copyOf(diagnostics), flipped);
    }

    public static EquipmentViewModelProfile socketFor(
            StaticModelPlacementMetadataResolver.Bounds bounds,
            Vector3d grip,
            Matrix3d rotation,
            double normalizedHeight
    ) {
        double scale = normalizedHeight / bounds.height();
        Vector3d relative = new Vector3d(grip).sub(bounds.renderPivot()).mul(scale);
        rotation.transform(relative);
        Vector3d position = relative.negate();
        Vector3d euler = rotation.getEulerAnglesXYZ(new Vector3d()).mul(180.0 / Math.PI);
        return new EquipmentViewModelProfile(position.x, position.y, position.z,
                normalizeDegrees(euler.x), normalizeDegrees(euler.y), normalizeDegrees(euler.z),
                normalizedHeight, 0, 0, 1, false);
    }

    /** Recovers the model-space point currently mapped onto the hand origin. */
    public static Vector3d gripForSocket(
            StaticModelPlacementMetadataResolver.Bounds bounds,
            EquipmentViewModelProfile socket
    ) {
        double scale = socket.normalizedHeight() / bounds.height();
        Matrix3d rotation = new Matrix3d()
                .rotateX(Math.toRadians(socket.rotationX()))
                .rotateY(Math.toRadians(socket.rotationY()))
                .rotateZ(Math.toRadians(socket.rotationZ()));
        Vector3d relative = new Vector3d(-socket.positionX(), -socket.positionY(),
                -socket.positionZ());
        rotation.invert().transform(relative).div(Math.max(0.000001, scale));
        return relative.add(bounds.renderPivot());
    }

    private static Heuristic weaponHeuristic(
            StaticModelPlacementMetadataResolver.Metadata metadata,
            boolean flipped,
            Vector3d heldDirection,
            Vector3d cameraFacingDirection
    ) {
        Vector3d center = metadata.bounds().center();
        Vector3d axis = new Vector3d(metadata.axes().major()).normalize();
        ProjectionRange range = projectionRange(metadata.vertices(), center, axis);
        EndScore minimum = endScore(metadata.vertices(), center, axis, range, true);
        EndScore maximum = endScore(metadata.vertices(), center, axis, range, false);
        boolean minimumHandle = minimum.radius() <= maximum.radius();
        if (flipped) minimumHandle = !minimumHandle;
        double projection = minimumHandle
                ? range.minimum() + range.length() * 0.08
                : range.maximum() - range.length() * 0.08;
        Vector3d primary = new Vector3d(center).fma(projection, axis);
        Vector3d outward = new Vector3d(axis).mul(minimumHandle ? 1 : -1);
        Vector3d target = heldDirection == null || heldDirection.lengthSquared() < 0.000001
                ? new Vector3d(0, 1, 0) : new Vector3d(heldDirection).normalize();
        Matrix3d rotation = align(outward, target);
        if (cameraFacingDirection != null
                && cameraFacingDirection.lengthSquared() >= 0.000001) {
            Vector3d currentNormal = rotation.transform(
                    new Vector3d(metadata.axes().minor())).normalize();
            Vector3d desiredNormal = new Vector3d(cameraFacingDirection).normalize();
            currentNormal.fma(-currentNormal.dot(target), target);
            desiredNormal.fma(-desiredNormal.dot(target), target);
            if (currentNormal.lengthSquared() >= 0.000001
                    && desiredNormal.lengthSquared() >= 0.000001) {
                currentNormal.normalize();
                desiredNormal.normalize();
                double sine = target.dot(new Vector3d(currentNormal).cross(desiredNormal));
                double cosine = Math.max(-1.0, Math.min(1.0,
                        currentNormal.dot(desiredNormal)));
                Matrix3d roll = new Quaterniond().rotationAxis(
                        Math.atan2(sine, cosine), target.x, target.y, target.z)
                        .get(new Matrix3d());
                rotation = roll.mul(rotation);
            }
        }
        double ratio = Math.max(minimum.radius(), maximum.radius())
                / Math.max(0.000001, Math.min(minimum.radius(), maximum.radius()));
        Confidence confidence = ratio >= 1.45 ? Confidence.HIGH
                : ratio >= 1.15 ? Confidence.MEDIUM : Confidence.LOW;
        return new Heuristic(primary, rotation, confidence);
    }

    private static Heuristic shieldHeuristic(
            StaticModelPlacementMetadataResolver.Metadata metadata,
            boolean flipped
    ) {
        Vector3d center = metadata.bounds().center();
        Vector3d normal = new Vector3d(metadata.axes().minor()).normalize();
        if (flipped) normal.negate();
        ProjectionRange thickness = projectionRange(metadata.vertices(), center, normal);
        Vector3d primary = new Vector3d(center).fma(thickness.minimum() * 0.7, normal);
        Matrix3d rotation = align(normal, new Vector3d(0, 0, 1));
        double ratio = metadata.axes().middleVariance()
                / Math.max(1e-12, metadata.axes().minorVariance());
        Confidence confidence = ratio >= 8 ? Confidence.HIGH
                : ratio >= 3 ? Confidence.MEDIUM : Confidence.LOW;
        return new Heuristic(primary, rotation, confidence);
    }

    private static EndScore endScore(
            List<Vector3d> vertices,
            Vector3d center,
            Vector3d axis,
            ProjectionRange range,
            boolean minimum
    ) {
        double cutoff = range.length() * 0.18;
        double radiusSquared = 0;
        int count = 0;
        for (Vector3d vertex : vertices) {
            Vector3d relative = new Vector3d(vertex).sub(center);
            double projection = relative.dot(axis);
            if (minimum ? projection > range.minimum() + cutoff
                    : projection < range.maximum() - cutoff) continue;
            Vector3d radial = relative.fma(-projection, axis);
            radiusSquared += radial.lengthSquared();
            count++;
        }
        return new EndScore(Math.sqrt(radiusSquared / Math.max(1, count)), count);
    }

    private static ProjectionRange projectionRange(
            List<Vector3d> vertices,
            Vector3d center,
            Vector3d axis
    ) {
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (Vector3d vertex : vertices) {
            double projection = new Vector3d(vertex).sub(center).dot(axis);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        return new ProjectionRange(minimum, maximum);
    }

    private static Matrix3d align(Vector3d source, Vector3d target) {
        Quaterniond quaternion = new Quaterniond().rotationTo(
                new Vector3d(source).normalize(), new Vector3d(target).normalize());
        return quaternion.get(new Matrix3d());
    }

    private static double defaultHeight(InventorySystem.ItemType type, WeaponType weaponType) {
        if (type == InventorySystem.ItemType.SHIELD) return 0.28;
        WeaponType weapon = weaponType == null ? WeaponType.SWORD : weaponType;
        return switch (weapon) {
            case DAGGER -> 0.65;
            case MACE -> 0.75;
            case STAFF -> 1.00;
            case GREATSWORD -> 1.20;
            case NONE, SWORD -> 0.85;
        };
    }

    private static boolean finite(Matrix4d matrix) {
        double[] values = new double[16];
        matrix.get(values);
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double normalizeDegrees(double value) {
        double result = value % 360.0;
        return result < 0 ? result + 360.0 : result;
    }

    public enum PlacementSource {
        MODEL_MARKERS("Model grip markers"),
        GEOMETRY_GUESS("Geometry analysis");

        private final String label;

        PlacementSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Confidence { EXACT, HIGH, MEDIUM, LOW }

    public record PlacementProposal(
            EquipmentViewModelProfile socket,
            Vector3d primaryGrip,
            Vector3d secondaryGrip,
            PlacementSource source,
            Confidence confidence,
            List<String> diagnostics,
            boolean flipped
    ) {
        public String summary() {
            return source.label() + " - " + confidence.name().toLowerCase(Locale.ROOT) + " confidence";
        }
    }

    private record ProjectionRange(double minimum, double maximum) {
        double length() { return Math.max(0.0001, maximum - minimum); }
    }

    private record EndScore(double radius, int count) {
    }

    private record Heuristic(Vector3d primary, Matrix3d rotation, Confidence confidence) {
    }
}

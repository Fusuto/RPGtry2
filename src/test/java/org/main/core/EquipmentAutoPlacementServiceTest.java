package org.main.core;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.main.content.FirstPersonCombatLibrary;
import org.main.experimental.StaticModelPlacementMetadataResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentAutoPlacementServiceTest {
    @Test
    void namedGripMarkersTakePrecedenceAndSecondaryGripIsPreserved() {
        Matrix4d primary = new Matrix4d().translate(1.25, 0.2, -0.1).rotateY(0.4);
        Matrix4d secondary = new Matrix4d().translate(-0.4, 0.1, 0.05);
        StaticModelPlacementMetadataResolver.Metadata metadata = metadata(
                weaponVertices(), Map.of(
                        "fp_grip_primary", primary,
                        "fp_grip_secondary", secondary));

        EquipmentAutoPlacementService.PlacementProposal proposal =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.WEAPON, WeaponType.GREATSWORD, true, false);

        assertEquals(EquipmentAutoPlacementService.PlacementSource.MODEL_MARKERS,
                proposal.source());
        assertEquals(EquipmentAutoPlacementService.Confidence.EXACT, proposal.confidence());
        assertVectorEquals(new Vector3d(1.25, 0.2, -0.1), proposal.primaryGrip(), 1e-8);
        assertVectorEquals(new Vector3d(-0.4, 0.1, 0.05), proposal.secondaryGrip(), 1e-8);
        assertGripMapsToHand(metadata.bounds(), proposal.primaryGrip(), proposal.socket());
    }

    @Test
    void markerlessWeaponUsesGeometryAndCanFlipGripEnd() {
        StaticModelPlacementMetadataResolver.Metadata metadata = metadata(
                weaponVertices(), Map.of());

        EquipmentAutoPlacementService.PlacementProposal normal =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.WEAPON, WeaponType.SWORD, false, false);
        EquipmentAutoPlacementService.PlacementProposal flipped =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.WEAPON, WeaponType.SWORD, false, true);

        assertEquals(EquipmentAutoPlacementService.PlacementSource.GEOMETRY_GUESS,
                normal.source());
        assertTrue(normal.primaryGrip().x < 0, "narrow end should be selected as handle");
        assertTrue(flipped.primaryGrip().x > 0, "flipping should select the opposite end");
        assertGripMapsToHand(metadata.bounds(), normal.primaryGrip(), normal.socket());
        assertEquals(0.85, normal.socket().normalizedHeight(), 1e-9);
    }

    @Test
    void rigAwarePlacementRollsWeaponBroadFaceTowardCamera() {
        StaticModelPlacementMetadataResolver.Metadata metadata = metadata(
                weaponVertices(), Map.of());
        EquipmentAutoPlacementService.PlacementProposal proposal =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.WEAPON, WeaponType.DAGGER,
                        false, false, new Vector3d(0, 1, 0),
                        new Vector3d(0, 0, 1));

        var rotation = new org.joml.Matrix3d()
                .rotateX(Math.toRadians(proposal.socket().rotationX()))
                .rotateY(Math.toRadians(proposal.socket().rotationY()))
                .rotateZ(Math.toRadians(proposal.socket().rotationZ()));
        Vector3d faceNormal = rotation.transform(
                new Vector3d(metadata.axes().minor())).normalize();
        assertTrue(Math.abs(faceNormal.dot(new Vector3d(0, 0, 1))) > 0.99,
                "the blade's broad face should not be edge-on in the combined preview");
    }

    @Test
    void shieldUsesThinAxisAndIgnoresSecondaryGrip() {
        List<Vector3d> vertices = box(-1.2, 1.2, -1.0, 1.0, -0.08, 0.08, 4);
        StaticModelPlacementMetadataResolver.Metadata metadata = new StaticModelPlacementMetadataResolver.Metadata(
                "shield.glb", 1, vertices,
                StaticModelPlacementMetadataResolver.Bounds.of(vertices),
                new StaticModelPlacementMetadataResolver.PrincipalAxes(
                        new Vector3d(1, 0, 0), new Vector3d(0, 1, 0),
                        new Vector3d(0, 0, 1), 3, 2, 0.01), Map.of(), List.of());

        EquipmentAutoPlacementService.PlacementProposal proposal =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.SHIELD, WeaponType.NONE, true, false);

        assertNull(proposal.secondaryGrip());
        assertEquals(0.28, proposal.socket().normalizedHeight(), 1e-9);
        assertGripMapsToHand(metadata.bounds(), proposal.primaryGrip(), proposal.socket());
    }

    @Test
    void twoHandedFallbackCreatesOffHandGrip() {
        StaticModelPlacementMetadataResolver.Metadata metadata = metadata(
                weaponVertices(), Map.of());
        EquipmentAutoPlacementService.PlacementProposal proposal =
                EquipmentAutoPlacementService.propose(metadata,
                        InventorySystem.ItemType.WEAPON, WeaponType.STAFF, true, false);

        assertNotNull(proposal.secondaryGrip());
        assertTrue(proposal.secondaryGrip().distance(proposal.primaryGrip()) > 0.1);
    }

    @Test
    void existingProfilePreventsAutomaticOverwrite() {
        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                "item", "rig", FirstPersonCombatLibrary.WieldHand.RIGHT, "set",
                FirstPersonCombatLibrary.ItemProfile.socketDefaults(), 0, 0, 0,
                "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of());

        assertFalse(EquipmentAutoPlacementService.shouldAutoPlace(
                profile, InventorySystem.ItemType.WEAPON, "assets/weapon.glb"));
        assertTrue(EquipmentAutoPlacementService.shouldAutoPlace(
                null, InventorySystem.ItemType.WEAPON, "assets/weapon.glb"));
        assertTrue(EquipmentAutoPlacementService.shouldAutoPlace(
                null, InventorySystem.ItemType.SHIELD, "assets/shield.glb"));
        assertFalse(EquipmentAutoPlacementService.shouldAutoPlace(
                null, InventorySystem.ItemType.CHEST_ARMOR, "assets/armor.glb"));
    }

    @Test
    void resolverFindsModelNodesAndCacheCanBeInvalidated() throws Exception {
        String path = "assets/3D/pack1/Dagger.glb";
        StaticModelPlacementMetadataResolver.clearCache();
        StaticModelPlacementMetadataResolver.Metadata first =
                StaticModelPlacementMetadataResolver.resolve(path);
        StaticModelPlacementMetadataResolver.Metadata cached =
                StaticModelPlacementMetadataResolver.resolve(path);
        assertSame(first, cached);
        assertNotNull(first.node("Dagger"));

        StaticModelPlacementMetadataResolver.invalidate(path);
        StaticModelPlacementMetadataResolver.Metadata reloaded =
                StaticModelPlacementMetadataResolver.resolve(path);
        assertNotSame(first, reloaded);
        assertEquals(first.fingerprint(), reloaded.fingerprint());
    }

    private static StaticModelPlacementMetadataResolver.Metadata metadata(
            List<Vector3d> vertices,
            Map<String, Matrix4d> nodes
    ) {
        LinkedHashMap<String, Matrix4d> normalized = new LinkedHashMap<>();
        nodes.forEach((key, value) -> normalized.put(key.toLowerCase(), value));
        return new StaticModelPlacementMetadataResolver.Metadata(
                "test.glb", 1, List.copyOf(vertices),
                StaticModelPlacementMetadataResolver.Bounds.of(vertices),
                new StaticModelPlacementMetadataResolver.PrincipalAxes(
                        new Vector3d(1, 0, 0), new Vector3d(0, 1, 0),
                        new Vector3d(0, 0, 1), 5, 1, 0.5),
                normalized, List.of());
    }

    private static List<Vector3d> weaponVertices() {
        List<Vector3d> result = new ArrayList<>();
        result.addAll(box(-2.0, -1.0, -0.12, 0.12, -0.12, 0.12, 2));
        result.addAll(box(-1.0, 2.2, -0.45, 0.45, -0.35, 0.35, 5));
        return result;
    }

    private static List<Vector3d> box(
            double minX, double maxX, double minY, double maxY,
            double minZ, double maxZ, int steps
    ) {
        List<Vector3d> result = new ArrayList<>();
        for (int x = 0; x <= steps; x++) {
            for (int y = 0; y <= steps; y++) {
                for (int z = 0; z <= steps; z++) {
                    if (x != 0 && x != steps && y != 0 && y != steps
                            && z != 0 && z != steps) continue;
                    result.add(new Vector3d(
                            minX + (maxX - minX) * x / steps,
                            minY + (maxY - minY) * y / steps,
                            minZ + (maxZ - minZ) * z / steps));
                }
            }
        }
        return result;
    }

    private static void assertGripMapsToHand(
            StaticModelPlacementMetadataResolver.Bounds bounds,
            Vector3d grip,
            EquipmentViewModelProfile socket
    ) {
        double scale = socket.normalizedHeight() / bounds.height();
        Vector3d mapped = new Vector3d(grip).sub(bounds.renderPivot()).mul(scale);
        new org.joml.Matrix3d().rotateX(Math.toRadians(socket.rotationX()))
                .rotateY(Math.toRadians(socket.rotationY()))
                .rotateZ(Math.toRadians(socket.rotationZ())).transform(mapped);
        mapped.add(socket.positionX(), socket.positionY(), socket.positionZ());
        assertEquals(0, mapped.length(), 1e-7);
    }

    private static void assertVectorEquals(
            Vector3d expected, Vector3d actual, double tolerance
    ) {
        assertNotNull(actual);
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}

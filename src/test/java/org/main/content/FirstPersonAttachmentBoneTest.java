package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.WeaponType;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class FirstPersonAttachmentBoneTest {
    @TempDir Path temporaryDirectory;

    @Test
    void currentSchemaRoundTripsExplicitBoneAndRejectsOldSchemas() throws Exception {
        Path schemaTwo = temporaryDirectory.resolve("schema-2.properties");
        Properties properties = new Properties();
        properties.setProperty("schemaVersion", "2");
        properties.setProperty("defaultRigId", "test_rig");
        properties.setProperty("rig.count", "1");
        properties.setProperty("rig.0.id", "test_rig");
        properties.setProperty("rig.0.modelPath", "model.glb");
        properties.setProperty("rig.0.left.handBone", "Hand.L");
        properties.setProperty("rig.0.right.handBone", "Hand.R");
        properties.setProperty("animationSet.count", "0");
        properties.setProperty("itemProfile.count", "1");
        properties.setProperty("itemProfile.0.itemId", "dagger");
        properties.setProperty("itemProfile.0.rigId", "test_rig");
        properties.setProperty("itemProfile.0.wieldHand", "RIGHT");
        try (OutputStream output = Files.newOutputStream(schemaTwo)) {
            properties.store(output, "schema two fixture");
        }

        assertThrows(java.io.IOException.class, () -> FirstPersonCombatLibrary.load(schemaTwo));

        FirstPersonCombatLibrary.Content current = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.ItemProfile oldProfile = current.itemProfiles().values().stream()
                .findFirst().orElseThrow();

        FirstPersonCombatLibrary.ItemProfile explicit = copyWithBone(oldProfile, "ForeArm.R");
        FirstPersonCombatLibrary.Content updated = current.withItemProfile(explicit);
        Path schemaThree = temporaryDirectory.resolve("schema-3.properties");
        FirstPersonCombatLibrary.save(schemaThree, updated);

        Properties written = new Properties();
        try (var input = Files.newInputStream(schemaThree)) { written.load(input); }
        assertEquals("4", written.getProperty("schemaVersion"));
        FirstPersonCombatLibrary.ItemProfile roundTripped =
                FirstPersonCombatLibrary.load(schemaThree).itemProfiles().get(explicit.itemId());
        assertEquals("ForeArm.R", roundTripped.attachmentBone());
        assertEquals(oldProfile.socketTransform(), roundTripped.socketTransform());
        assertEquals(FirstPersonCombatLibrary.AnimationCompositionMode.AUTO,
                roundTripped.animationComposition());
    }

    @Test
    void inheritedExplicitAndMissingBoneUseOneRuntimeResolver() throws Exception {
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.RigDefinition rig = content.rig();
        assertTrue(rig.configured());
        FirstPersonCombatLibrary.ItemProfile inherited = new FirstPersonCombatLibrary.ItemProfile(
                "test", rig.rigId(), FirstPersonCombatLibrary.WieldHand.RIGHT, "",
                FirstPersonCombatLibrary.ItemProfile.socketDefaults(), 0, 0, 0,
                "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, "", Map.of());
        LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(
                FirstPersonAnimationRuntime.definitionFor(content, rig, WeaponType.NONE,
                        inherited, inherited.wieldHand()));

        assertEquals(rig.rightHandBone(),
                FirstPersonAnimationRuntime.resolveAttachmentBone(rig, inherited, model));
        FirstPersonCombatLibrary.ItemProfile explicit = copyWithBone(inherited, rig.leftHandBone());
        assertEquals(rig.leftHandBone(),
                FirstPersonAnimationRuntime.resolveAttachmentBone(rig, explicit, model));
        FirstPersonCombatLibrary.ItemProfile missing = copyWithBone(inherited, "Removed.Hand.Socket");
        assertEquals(rig.rightHandBone(),
                FirstPersonAnimationRuntime.resolveAttachmentBone(rig, missing, model));
        assertFalse(model.followsAnimatedHierarchy("FP_Hand_Right"));
        FirstPersonCombatLibrary.ItemProfile staticMesh = copyWithBone(inherited, "FP_Hand_Right");
        assertEquals(rig.rightHandBone(),
                FirstPersonAnimationRuntime.resolveAttachmentBone(rig, staticMesh, model));
    }

    @Test
    void skeletonMetadataExposesHierarchyCategoriesAndAnimatedTransforms() throws Exception {
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.RigDefinition rig = content.rig();
        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                "test", rig.rigId(), FirstPersonCombatLibrary.WieldHand.RIGHT, "",
                new EquipmentViewModelProfile(0, 0, 0, 0, 0, 0, 1, 0, 0, 1, false),
                0, 0, 0, "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, "", Map.of());
        LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(
                FirstPersonAnimationRuntime.definitionFor(content, rig, WeaponType.NONE,
                        profile, profile.wieldHand()));

        assertFalse(model.skeletonNodes().isEmpty());
        LwjglSkinnedModel.SkeletonNodeMetadata rightHand = model.skeletonNodes().stream()
                .filter(node -> node.name().equalsIgnoreCase(rig.rightHandBone()))
                .findFirst().orElseThrow();
        assertFalse(rightHand.parentName().isBlank());
        assertTrue(rightHand.displayLabel().startsWith(rig.rightHandBone() + " ["));
        assertTrue(model.skeletonNodes().stream()
                .anyMatch(node -> node.weightedBone() || node.animatedNode()));
        assertNotNull(rightHand.bindPoseTransform());
        assertEquals(model.skeletonNodes().size(), model.skeletonPose(
                CharacterModelDefinition.AnimationSlot.IDLE, 0.5).size());
        assertEquals(0.20f, model.handGripOffsetNormalized(
                CharacterModelDefinition.AnimationSlot.IDLE, 0.0,
                rig.rightHandBone()).length(), 0.0001f);
        assertEquals(0.0f, model.handGripOffsetNormalized(
                CharacterModelDefinition.AnimationSlot.IDLE, 0.0,
                rightHand.parentName()).length(), 0.0001f);
    }

    private static FirstPersonCombatLibrary.ItemProfile copyWithBone(
            FirstPersonCombatLibrary.ItemProfile source,
            String bone
    ) {
        return new FirstPersonCombatLibrary.ItemProfile(source.itemId(), source.rigId(),
                source.wieldHand(), source.animationSetId(), source.socketTransform(),
                source.secondaryGripX(), source.secondaryGripY(), source.secondaryGripZ(),
                source.leftArmorPath(), source.rightArmorPath(), source.leftCoverage(),
                source.rightCoverage(), bone, new LinkedHashMap<>(source.overrides()));
    }
}

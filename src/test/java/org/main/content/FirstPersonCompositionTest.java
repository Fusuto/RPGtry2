package org.main.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Matrix4f;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.WeaponType;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FirstPersonCompositionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void schemaFourRoundTripsComposition() throws Exception {
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                "composition_test", content.defaultRigId(), FirstPersonCombatLibrary.WieldHand.RIGHT,
                "wand", EquipmentViewModelProfile.defaults(), 0, 0, 0, "", "",
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, "",
                FirstPersonCombatLibrary.AnimationCompositionMode.INDEPENDENT, Map.of());
        Path output = temporaryDirectory.resolve("composition.properties");
        FirstPersonCombatLibrary.save(output, content.withItemProfile(profile));

        FirstPersonCombatLibrary.ItemProfile loaded = FirstPersonCombatLibrary.load(output)
                .itemProfiles().get("composition_test");
        assertEquals(FirstPersonCombatLibrary.AnimationCompositionMode.INDEPENDENT,
                loaded.animationComposition());
        assertTrue(loaded.independent(true));
        assertTrue(content.weaponDefaults().containsKey(WeaponType.WAND));
        assertEquals("wand", content.weaponDefaults().get(WeaponType.WAND));
    }

    @Test
    void blockLeftAndAttackRightComposeLocallyWithoutChangingGlobalRoot() throws Exception {
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.loadFresh();
        FirstPersonCombatLibrary.RigDefinition rig = content.rig();
        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                "composition_test", rig.rigId(), FirstPersonCombatLibrary.WieldHand.RIGHT,
                "wand", FirstPersonCombatLibrary.ItemProfile.socketDefaults(), 0, 0, 0,
                "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, "",
                FirstPersonCombatLibrary.AnimationCompositionMode.INDEPENDENT, Map.of());
        LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(
                FirstPersonAnimationRuntime.definitionFor(content, rig, WeaponType.WAND,
                        profile, profile.wieldHand()));
        assertTrue(model.hasNamedClip("BLOCK_LEFT"));
        assertTrue(model.hasNamedClip("ATTACK_RIGHT"));

        Set<String> leftMask = model.descendantNodeNames(rig.leftShoulderBone());
        Set<String> rightMask = model.descendantNodeNames(rig.rightShoulderBone());
        LinkedHashSet<String> globalMask = new LinkedHashSet<>(model.allNodeNames());
        globalMask.removeAll(leftMask);
        globalMask.removeAll(rightMask);
        List<LwjglSkinnedModel.PoseLayer> layers = new ArrayList<>();
        layers.add(layer("IDLE_RIGHT", 0.25, globalMask));
        layers.add(layer("BLOCK_LEFT", model.namedImpactFraction("BLOCK_LEFT"), leftMask));
        layers.add(layer("ATTACK_RIGHT", 0.55, rightMask));
        LwjglSkinnedModel.Pose composed = model.composePose(layers);

        assertMatrixEquals(model.nodeTransform(
                        model.composePose(List.of(layer("BLOCK_LEFT",
                                model.namedImpactFraction("BLOCK_LEFT"), Set.of()))),
                        rig.leftHandBone()),
                model.nodeTransform(composed, rig.leftHandBone()));
        assertMatrixEquals(model.nodeTransform(
                        model.composePose(List.of(layer("ATTACK_RIGHT", 0.55, Set.of()))),
                        rig.rightHandBone()),
                model.nodeTransform(composed, rig.rightHandBone()));
        assertMatrixEquals(model.nodeTransform(
                        model.composePose(List.of(layer("IDLE_RIGHT", 0.25, Set.of()))),
                        rig.cameraAnchorBone()),
                model.nodeTransform(composed, rig.cameraAnchorBone()));
    }

    private static LwjglSkinnedModel.PoseLayer layer(String key, double progress, Set<String> mask) {
        return new LwjglSkinnedModel.PoseLayer(
                null, new LwjglSkinnedModel.NamedSample(key, progress), 1.0, mask);
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        float[] expectedValues = expected.get(new float[16]);
        float[] actualValues = actual.get(new float[16]);
        for (int index = 0; index < expectedValues.length; index++) {
            assertEquals(expectedValues[index], actualValues[index], 0.0001f,
                    "matrix element " + index);
        }
    }
}

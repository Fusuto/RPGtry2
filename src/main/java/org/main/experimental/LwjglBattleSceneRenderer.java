package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.main.battle.BattleActor;
import org.main.battle.BattleEncounter;
import org.main.battle.BattlePresentationDirector;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.core.AetherGameRuntime;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.FirstPersonEquipmentRig;
import org.main.core.LimbItem;
import org.main.core.LimbSlot;
import org.main.core.WeaponType;
import org.main.engine.DungeonRenderContext;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Predicate;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/** Native battle pass layered over the live map backdrop. */
final class LwjglBattleSceneRenderer {
    private static final Logger LOGGER = Logger.getLogger(LwjglBattleSceneRenderer.class.getName());
    private static final double COLUMN_SPACING = 1.35;
    private static final double ALLY_FRONT_Z = -0.75;
    private static final double ALLY_BACK_Z = 0.65;
    private static final double ENEMY_FRONT_Z = -3.15;
    private static final double ENEMY_BACK_Z = -4.45;
    private static final double CAMERA_EYE_HEIGHT = 0.58;
    private static final double STANDARD_ACTOR_HEIGHT = 0.95;
    private static final double STANDARD_ACTOR_WIDTH = STANDARD_ACTOR_HEIGHT * 0.74;

    private final LwjglTextureCache textureCache;
    private final Map<CharacterModelDefinition, LwjglSkinnedModel> skinnedModels = new HashMap<>();
    private final Set<CharacterModelDefinition> failedSkinnedModels = new HashSet<>();
    private final Map<String, LwjglStaticModel> staticModels = new HashMap<>();
    private final Set<String> failedStaticModels = new HashSet<>();
    private final Set<String> warnedFirstPersonFallbacks = new HashSet<>();
    private final IdentityHashMap<LwjglSkinnedModel.SkinnedMesh, MeshBuffers> buffers = new IdentityHashMap<>();
    private final IdentityHashMap<LwjglStaticModel.Mesh, MeshBuffers> staticBuffers = new IdentityHashMap<>();
    private final Map<BattleActor, Point> projectedActors = new IdentityHashMap<>();
    private final Map<BattleActor, FirstPersonTransition> firstPersonTransitions =
            new IdentityHashMap<>();
    private final Map<BattleActor, LayeredTransitionState> layeredFirstPersonTransitions =
            new IdentityHashMap<>();
    private final float[] encounterLight = {1f, 1f, 1f};
    private FixedFunctionPrimitives fixedPrimitives;
    private long firstPersonCatalogRevision = -1;

    LwjglBattleSceneRenderer(LwjglTextureCache textureCache) {
        this.textureCache = textureCache;
    }

    Map<BattleActor, Point> render(DungeonRenderContext context, CameraLookState lookState,
                                   AetherGameRuntime runtime, int width, int height,
                                   float[] lightTint) {
        projectedActors.clear();
        if (fixedPrimitives == null) {
            fixedPrimitives = new FixedFunctionPrimitives();
        }
        encounterLight[0] = lightTint == null || lightTint.length < 3 ? 1f : clampLight(lightTint[0]);
        encounterLight[1] = lightTint == null || lightTint.length < 3 ? 1f : clampLight(lightTint[1]);
        encounterLight[2] = lightTint == null || lightTint.length < 3 ? 1f : clampLight(lightTint[2]);
        BattleEncounter encounter = runtime == null || runtime.gameState() == null
                ? null : runtime.gameState().getCurrentEncounter();
        if (encounter == null) return Map.of();
        CameraLookState look = lookState == null ? CameraLookState.centered() : lookState;
        BattleActor player = encounter.getAllies().stream()
                .filter(actor -> actor.getSourcePlayer() != null).findFirst()
                .orElse(encounter.getFirstLivingAlly());
        Position playerCell = player == null ? new Position(0, 0, 0) : formationPosition(player, true);
        List<BattlePresentationDirector.ActionSnapshot> actions = encounter.getPresentationDirector().snapshots();

        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glRotated(look.pitchOffsetDegrees(), 1, 0, 0);
        glRotated(-look.yawOffsetDegrees(), 0, 1, 0);
        double cameraLunge = cameraLunge(encounter, player);
        glTranslated(0, -CAMERA_EYE_HEIGHT, cameraLunge);
        double playerDeath = actions.stream()
                .filter(action -> action.attacker() == player
                        && action.actionType() == BattlePresentationDirector.ActionType.DEATH)
                .findFirst().map(LwjglBattleSceneRenderer::actionEnvelope).orElse(0.0);
        if (playerDeath > 0.0) {
            glTranslated(0, -0.42 * playerDeath, 0);
            glRotated(58 * playerDeath, 0, 0, 1);
        }
        for (BattleActor actor : encounter.getAllies()) {
            if (actor == player || (!actor.isAlive() && !hasDeathPresentation(actor, actions))) continue;
            drawActor(actor, relative(formationPosition(actor, true), playerCell),
                    playerCell, actions, encounter);
        }
        for (BattleActor actor : encounter.getEnemies()) {
            if (!actor.isAlive() && !hasDeathPresentation(actor, actions)) continue;
            drawActor(actor, relative(formationPosition(actor, false), playerCell),
                    playerCell, actions, encounter);
        }
        projectActors(encounter, player, playerCell, look, width, height);

        renderEquipment(player, actions, width, height);
        renderEffects(actions, playerCell, look);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1f, 1f, 1f, 1f);
        return Map.copyOf(projectedActors);
    }

    private void drawActor(BattleActor actor, Position base, Position playerCell,
                           List<BattlePresentationDirector.ActionSnapshot> actions,
                           BattleEncounter encounter) {
        Position animated = animatePosition(actor, base, playerCell, actions);
        CharacterModelDefinition.AnimationSlot slot = animationSlot(actor, actions);
        CharacterModelDefinition definition = actor.getCharacterModel();
        LwjglSkinnedModel skinned = getSkinnedModel(definition);
        double progress = animationProgress(actor, actions, skinned, slot);
        glPushMatrix();
        glTranslated(animated.x(), animated.y() + definition.verticalOffset(), animated.z());
        glRotated(facingYaw(actor, animated, playerCell, encounter)
                + definition.facingRotationDegrees(), 0, 1, 0);
        if (skinned == null || !skinned.hasClip(slot)) applyProceduralWholeModel(actor, slot, actions);
        if (skinned != null) {
            double scale = skinned.normalizedScaleForHeight(STANDARD_ACTOR_HEIGHT) * definition.scale();
            glScaled(scale, scale, scale);
            glTranslated(-skinned.centerX(), -skinned.baseY(), -skinned.centerZ());
            drawSkinnedModel(skinned, skinned.skinNormalized(slot, progress));
        } else if (definition.hasModel()) {
            LwjglStaticModel model = getStaticModel(definition.modelPath());
            if (model != null) {
                double scale = model.normalizedScaleForHeight(STANDARD_ACTOR_HEIGHT) * definition.scale();
                glScaled(scale, scale, scale);
                glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
                for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
            } else {
                drawBillboard(actor.getImage(),
                        STANDARD_ACTOR_WIDTH * definition.scale(),
                        STANDARD_ACTOR_HEIGHT * definition.scale());
            }
        } else {
            applyProceduralReaction(actor, actions);
            drawBillboard(actor.getImage(),
                    STANDARD_ACTOR_WIDTH * definition.scale(),
                    STANDARD_ACTOR_HEIGHT * definition.scale());
        }
        glPopMatrix();
    }

    private void drawSkinnedModel(LwjglSkinnedModel model, LwjglSkinnedModel.Frame frame) {
        drawSkinnedModel(model, frame, ignored -> true);
    }

    private void drawSkinnedModel(
            LwjglSkinnedModel model,
            LwjglSkinnedModel.Frame frame,
            Predicate<LwjglSkinnedModel.SkinnedMesh> visible
    ) {
        for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
            LwjglSkinnedModel.SkinnedMesh mesh = model.meshes().get(meshIndex);
            if (!visible.test(mesh)) continue;
            MeshBuffers gpu = buffers.computeIfAbsent(mesh, this::createBuffers);
            FloatBuffer positions = gpu.positionStaging();
            positions.clear();
            positions.put(frame.meshPositions().get(meshIndex)).flip();
            glBindBuffer(GL_ARRAY_BUFFER, gpu.positionVbo());
            glBufferData(GL_ARRAY_BUFFER, (long) positions.remaining() * Float.BYTES, GL_DYNAMIC_DRAW);
            glBufferSubData(GL_ARRAY_BUFFER, 0, positions);
            glEnableClientState(GL_VERTEX_ARRAY);
            glVertexPointer(3, GL_FLOAT, 0, 0L);

            LwjglSkinnedModel.Material material = mesh.material();
            if (material.texture() != null) {
                glEnable(GL_TEXTURE_2D); textureCache.bind(material.texture());
                glBindBuffer(GL_ARRAY_BUFFER, gpu.uvVbo());
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glTexCoordPointer(2, GL_FLOAT, 0, 0L);
            } else {
                glDisable(GL_TEXTURE_2D); glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            setLitColor(material.red(), material.green(), material.blue(), material.alpha());
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gpu.indexBuffer());
            glDrawElements(GL_TRIANGLES, mesh.indices().length, GL_UNSIGNED_INT, 0L);
            glDisableClientState(GL_VERTEX_ARRAY);
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private MeshBuffers createBuffers(LwjglSkinnedModel.SkinnedMesh mesh) {
        int position = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, position);
        glBufferData(GL_ARRAY_BUFFER, (long) mesh.bindPositions().length * Float.BYTES, GL_DYNAMIC_DRAW);
        int uv = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, uv);
        FloatBuffer uvData = BufferUtils.createFloatBuffer(mesh.texCoords().length).put(mesh.texCoords()).flip();
        glBufferData(GL_ARRAY_BUFFER, uvData, GL_STATIC_DRAW);
        int indices = glGenBuffers(); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        IntBuffer indexData = BufferUtils.createIntBuffer(mesh.indices().length).put(mesh.indices()).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW);
        return new MeshBuffers(
                position,
                uv,
                indices,
                BufferUtils.createFloatBuffer(mesh.bindPositions().length),
                mesh.indices().length);
    }

    private void drawStaticMesh(LwjglStaticModel.Mesh mesh) {
        MeshBuffers gpu = staticBuffers.computeIfAbsent(mesh, this::createStaticBuffers);
        glBindBuffer(GL_ARRAY_BUFFER, gpu.positionVbo());
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, 0L);
        if (mesh.texture() == null) {
            glDisable(GL_TEXTURE_2D);
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        } else {
            glEnable(GL_TEXTURE_2D);
            textureCache.bind(mesh.texture());
            glBindBuffer(GL_ARRAY_BUFFER, gpu.uvVbo());
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, 0, 0L);
        }
        setLitColor(mesh.red(), mesh.green(), mesh.blue(), mesh.alpha());
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gpu.indexBuffer());
        glDrawElements(GL_TRIANGLES, gpu.indexCount(), GL_UNSIGNED_INT, 0L);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
    }

    private MeshBuffers createStaticBuffers(LwjglStaticModel.Mesh mesh) {
        int position = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, position);
        FloatBuffer positionData = BufferUtils.createFloatBuffer(mesh.positions().length)
                .put(mesh.positions()).flip();
        glBufferData(GL_ARRAY_BUFFER, positionData, GL_STATIC_DRAW);
        int uv = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uv);
        FloatBuffer uvData = BufferUtils.createFloatBuffer(mesh.texCoords().length)
                .put(mesh.texCoords()).flip();
        glBufferData(GL_ARRAY_BUFFER, uvData, GL_STATIC_DRAW);
        int indices = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        IntBuffer indexData = BufferUtils.createIntBuffer(mesh.indices().length)
                .put(mesh.indices()).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW);
        return new MeshBuffers(position, uv, indices, null, mesh.indices().length);
    }

    private void drawBillboard(BufferedImage image, double width, double height) {
        if (image != null) {
            glEnable(GL_TEXTURE_2D);
            textureCache.bind(image);
            setLitColor(1, 1, 1, 1);
        } else {
            glDisable(GL_TEXTURE_2D);
            setLitColor(0.55f, 0.18f, 0.65f, 1f);
        }
        fixedPrimitives.drawBillboard(width, height, image != null);
    }

    private boolean renderSkeletalEquipment(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int viewportWidth,
            int viewportHeight
    ) {
        refreshFirstPersonCachesIfNeeded();
        FirstPersonAnimationRuntime.ResolvedRig resolved =
                FirstPersonAnimationRuntime.resolve(player);
        if (!resolved.usable()) return false;
        FirstPersonCombatLibrary.RigDefinition rig = resolved.rig();
        InventorySystem.Inventory inventory = player.getSourcePlayer().getInventory();
        InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = inventory.getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        InventorySystem.Item chest = inventory.getEquippedItem(InventorySystem.EquipmentSlot.CHEST);
        FirstPersonCombatLibrary.ItemProfile weaponProfile = resolved.itemProfile();
        FirstPersonCombatLibrary.ItemProfile shieldProfile = compatibleProfile(
                resolved.content().itemProfile(shield), rig, "shield");
        FirstPersonCombatLibrary.ItemProfile armorProfile = compatibleProfile(
                resolved.content().itemProfile(chest), rig, "chest armor");

        // Authored camera-space models stay on their dedicated rendering path
        // until a socket/attachment profile is created for them.
        if (weapon != null && weapon.hasFirstPersonModel() && weaponProfile == null) return false;
        if (shield != null && shield.hasFirstPersonModel() && shieldProfile == null) {
            warnFirstPersonFallback("profile:shield",
                    "Shield has no profile compatible with the active rig; other viewmodel components remain active.");
        }
        if (chest != null && chest.hasFirstPersonModel() && (armorProfile == null
                || (armorProfile.leftArmorPath().isBlank()
                && armorProfile.rightArmorPath().isBlank()))) {
            warnFirstPersonFallback("profile:chest",
                    "Chest armor has no compatible sleeve attachments; other viewmodel components remain active.");
        }

        CharacterModelDefinition.AnimationSlot slot = firstPersonSlot(player, actions);
        if (!resolved.model().hasClip(slot)) {
            warnFirstPersonFallback("clip:" + slot,
                    "First-person " + slot.displayName()
                            + " clip is unavailable; retaining the lit skeletal rig in idle/bind pose.");
            slot = CharacterModelDefinition.AnimationSlot.IDLE;
        }
        double progress = firstPersonProgress(player, actions, slot, resolved.model());
        FirstPersonAnimationView animation = firstPersonAnimationView(
                player, slot, progress, rig.crossfadeMs());
        LayeredAnimationView layeredAnimation = resolved.independent()
                ? layeredAnimationView(player, actions, resolved, shieldProfile, rig.crossfadeMs())
                : null;
        NamedAnimationView coupledAnimation = layeredAnimation == null
                ? coupledAnimationView(animation, resolved, shieldProfile) : null;
        LwjglSkinnedModel.Pose composedRigPose = layeredAnimation == null
                ? composedFullPose(resolved.model(), coupledAnimation)
                : composedPose(resolved.model(), rig, layeredAnimation);

        LimbItem leftLimb = player.getSourcePlayer().getEquippedLimb(LimbSlot.LEFT_ARM);
        LimbItem rightLimb = player.getSourcePlayer().getEquippedLimb(LimbSlot.RIGHT_ARM);
        String leftPath = compatibleLimbPath(leftLimb, rig, rig.defaultLeftArmPath());
        String rightPath = compatibleLimbPath(rightLimb, rig, rig.defaultRightArmPath());
        LwjglSkinnedModel leftArm = attachmentModel(resolved, leftPath);
        LwjglSkinnedModel rightArm = attachmentModel(resolved, rightPath);
        if (leftArm == null && !leftPath.equals(rig.defaultLeftArmPath())) {
            leftArm = attachmentModel(resolved, rig.defaultLeftArmPath());
        }
        if (rightArm == null && !rightPath.equals(rig.defaultRightArmPath())) {
            rightArm = attachmentModel(resolved, rig.defaultRightArmPath());
        }
        if (leftArm == null && !rig.leftVisibleMeshes().isEmpty()) leftArm = resolved.model();
        if (rightArm == null && !rig.rightVisibleMeshes().isEmpty()) rightArm = resolved.model();
        LwjglSkinnedModel leftArmor = attachmentModel(
                resolved, armorProfile == null ? "" : armorProfile.leftArmorPath());
        LwjglSkinnedModel rightArmor = attachmentModel(
                resolved, armorProfile == null ? "" : armorProfile.rightArmorPath());
        if (leftArm == null) warnFirstPersonFallback("arm-signature:left",
                "The left first-person arm is unavailable; other valid viewmodel components remain active.");
        if (rightArm == null) warnFirstPersonFallback("arm-signature:right",
                "The right first-person arm is unavailable; other valid viewmodel components remain active.");
        if (leftArm == null && rightArm == null) {
            warnFirstPersonFallback("arms",
                    "First-person rig has no usable arm attachments or base-model arm mesh selections; "
                            + "socketed equipment will remain visible.");
            boolean hasSocketedEquipment = weapon != null && weapon.hasFirstPersonModel()
                    && weaponProfile != null
                    || shield != null && shield.hasFirstPersonModel() && shieldProfile != null
                    || leftArmor != null || rightArmor != null;
            if (!hasSocketedEquipment) return false;
        }

        pushViewmodelProjection(rig, viewportWidth, viewportHeight);
        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        FirstPersonCombatLibrary.CameraFraming cameraFraming = cameraFraming(
                resolved,
                weapon == null ? WeaponType.NONE : weapon.getWeaponType(),
                shieldProfile,
                animation);
        applyRigRoot(rig, resolved.model(), animation, composedRigPose, cameraFraming);

        FirstPersonCombatLibrary.ArmCoverage leftCoverage = armorProfile == null
                ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : armorProfile.leftCoverage();
        FirstPersonCombatLibrary.ArmCoverage rightCoverage = armorProfile == null
                ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : armorProfile.rightCoverage();
        FirstPersonCombatLibrary.WieldHand weaponHand = resolved.wieldHand();
        Vector3f secondaryGripOffset = weapon != null && weapon.isTwoHanded()
                ? secondaryGripOffset(resolved, weapon, weaponProfile, weaponHand, animation,
                composedRigPose)
                : new Vector3f();
        drawArmAttachment(leftArm, animation, layeredAnimation, coupledAnimation, rig,
                FirstPersonCombatLibrary.WieldHand.LEFT,
                leftCoverage, rig.leftVisibleMeshes(),
                weaponHand == FirstPersonCombatLibrary.WieldHand.RIGHT
                        ? secondaryGripOffset : new Vector3f());
        drawArmAttachment(rightArm, animation, layeredAnimation, coupledAnimation, rig,
                FirstPersonCombatLibrary.WieldHand.RIGHT,
                rightCoverage, rig.rightVisibleMeshes(),
                weaponHand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? secondaryGripOffset : new Vector3f());
        drawArmAttachment(leftArmor, animation, layeredAnimation, coupledAnimation, rig,
                FirstPersonCombatLibrary.WieldHand.LEFT,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of(),
                weaponHand == FirstPersonCombatLibrary.WieldHand.RIGHT
                        ? secondaryGripOffset : new Vector3f());
        drawArmAttachment(rightArmor, animation, layeredAnimation, coupledAnimation, rig,
                FirstPersonCombatLibrary.WieldHand.RIGHT,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of(),
                weaponHand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? secondaryGripOffset : new Vector3f());

        if (weapon != null && weapon.hasFirstPersonModel()) {
            drawSocketEquipment(weapon, weaponProfile.socketTransform(), resolved.model(),
                    FirstPersonAnimationRuntime.resolveAttachmentBone(
                            rig, weaponProfile, resolved.model()), animation, composedRigPose);
        }
        if (shield != null && shield.hasFirstPersonModel() && shieldProfile != null
                && (weapon == null || !weapon.isTwoHanded())) {
            FirstPersonCombatLibrary.WieldHand shieldHand = shieldProfile.wieldHand();
            drawSocketEquipment(shield, shieldProfile.socketTransform(), resolved.model(),
                    FirstPersonAnimationRuntime.resolveAttachmentBone(
                            rig, shieldProfile, resolved.model()), animation, composedRigPose);
        }
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
        popViewmodelProjection();
        return true;
    }

    private FirstPersonCombatLibrary.ItemProfile compatibleProfile(
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.RigDefinition rig,
            String label
    ) {
        if (profile == null || profile.rigId().isBlank() || profile.rigId().equals(rig.rigId())) {
            return profile;
        }
        warnFirstPersonFallback("profile-rig:" + label + ":" + profile.itemId(),
                "The " + label + " profile targets rig " + profile.rigId()
                        + " and cannot attach to active rig " + rig.rigId() + ".");
        return null;
    }

    private void refreshFirstPersonCachesIfNeeded() {
        long current = FirstPersonCombatLibrary.revision();
        if (firstPersonCatalogRevision == current) return;
        for (MeshBuffers mesh : buffers.values()) {
            glDeleteBuffers(mesh.positionVbo());
            glDeleteBuffers(mesh.uvVbo());
            glDeleteBuffers(mesh.indexBuffer());
        }
        for (MeshBuffers mesh : staticBuffers.values()) {
            glDeleteBuffers(mesh.positionVbo());
            glDeleteBuffers(mesh.uvVbo());
            glDeleteBuffers(mesh.indexBuffer());
        }
        buffers.clear();
        staticBuffers.clear();
        skinnedModels.clear();
        failedSkinnedModels.clear();
        staticModels.clear();
        failedStaticModels.clear();
        firstPersonTransitions.clear();
        layeredFirstPersonTransitions.clear();
        warnedFirstPersonFallbacks.clear();
        firstPersonCatalogRevision = current;
    }

    private void pushViewmodelProjection(
            FirstPersonCombatLibrary.RigDefinition rig,
            int viewportWidth,
            int viewportHeight
    ) {
        double near = Math.max(0.001, rig.nearPlane());
        double top = near * Math.tan(Math.toRadians(rig.fieldOfViewDegrees()) * 0.5);
        double aspect = Math.max(1, viewportWidth) / (double) Math.max(1, viewportHeight);
        double right = top * aspect;
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glFrustum(-right, right, -top, top, near, 100.0);
        glMatrixMode(GL_MODELVIEW);
    }

    private void popViewmodelProjection() {
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private String compatibleLimbPath(
            LimbItem limb,
            FirstPersonCombatLibrary.RigDefinition rig,
            String fallback
    ) {
        if (limb == null || !limb.hasFirstPersonModel()) return fallback;
        String limbRig = FirstPersonCombatLibrary.normalizeId(limb.getFirstPersonRigId());
        if (!limbRig.isBlank() && !limbRig.equals(rig.rigId())) {
            warnFirstPersonFallback("limb-rig:" + limb.getName(),
                    limb.getName() + " targets first-person rig " + limbRig
                            + ", so the " + rig.displayName() + " default arm is used instead.");
            return fallback;
        }
        return limb.getFirstPersonModelPath();
    }

    private LwjglSkinnedModel attachmentModel(
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            String path
    ) {
        if (path == null || path.isBlank()) return null;
        CharacterModelDefinition attachmentDefinition = new CharacterModelDefinition(
                path,
                resolved.rig().rigId(),
                1.0,
                0.0,
                0.0,
                resolved.modelDefinition().animationBindings(),
                resolved.modelDefinition().namedAnimationBindings());
        LwjglSkinnedModel attachment = getSkinnedModel(attachmentDefinition);
        if (attachment == null
                || !attachment.skeletonSignature().equals(resolved.model().skeletonSignature())) {
            return null;
        }
        return attachment;
    }

    private void warnFirstPersonFallback(String key, String message) {
        if (warnedFirstPersonFallbacks.add(key)) LOGGER.warning(message);
    }

    private void applyRigRoot(
            FirstPersonCombatLibrary.RigDefinition rig,
            LwjglSkinnedModel model,
            FirstPersonAnimationView animation,
            LwjglSkinnedModel.Pose composedPose,
            FirstPersonCombatLibrary.CameraFraming camera
    ) {
        FirstPersonCombatLibrary.CameraFraming framing = camera == null
                ? FirstPersonCombatLibrary.CameraFraming.identity() : camera;
        glTranslated(
                rig.positionX() + framing.positionX(),
                rig.positionY() + framing.positionY(),
                rig.positionZ() + framing.positionZ());
        glRotated(rig.rotationX() + framing.rotationX(), 1, 0, 0);
        glRotated(rig.rotationY() + framing.rotationY(), 0, 1, 0);
        glRotated(rig.rotationZ() + framing.rotationZ(), 0, 0, 1);
        glScaled(rig.scale(), rig.scale(), rig.scale());
        if (!rig.cameraAnchorBone().isBlank()) {
            Matrix4f anchor = composedPose == null
                    ? animatedNodeTransform(model, rig.cameraAnchorBone(), animation)
                    : socketTransformWithoutScale(model.nodeTransform(composedPose, rig.cameraAnchorBone()));
            if (anchor != null) {
                Vector3f position = anchor.getTranslation(new Vector3f());
                glTranslated(-position.x, -position.y, -position.z);
            }
        }
    }

    private FirstPersonCombatLibrary.CameraFraming cameraFraming(
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile shieldProfile,
            FirstPersonAnimationView animation
    ) {
        FirstPersonCombatLibrary.CameraFraming target = cameraFraming(
                resolved, weaponType, shieldProfile, animation.slot());
        if (animation.fromSlot() == null || animation.blend() >= 1.0) return target;
        FirstPersonCombatLibrary.CameraFraming source = cameraFraming(
                resolved, weaponType, shieldProfile, animation.fromSlot());
        double amount = smooth(animation.blend());
        return new FirstPersonCombatLibrary.CameraFraming(
                lerp(source.positionX(), target.positionX(), amount),
                lerp(source.positionY(), target.positionY(), amount),
                lerp(source.positionZ(), target.positionZ(), amount),
                lerpAngle(source.rotationX(), target.rotationX(), amount),
                lerpAngle(source.rotationY(), target.rotationY(), amount),
                lerpAngle(source.rotationZ(), target.rotationZ(), amount));
    }

    private FirstPersonCombatLibrary.CameraFraming cameraFraming(
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            WeaponType weaponType,
            FirstPersonCombatLibrary.ItemProfile shieldProfile,
            CharacterModelDefinition.AnimationSlot slot
    ) {
        FirstPersonCombatLibrary.AnimationSlot authoredSlot = switch (slot) {
            case IDLE -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
            case ATTACK -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
            case BLOCK -> (shieldProfile == null
                    ? resolved.wieldHand().opposite()
                    : shieldProfile.wieldHand()) == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
            case CAST -> resolved.independent()
                    ? resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.CAST_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.CAST_RIGHT
                    : FirstPersonCombatLibrary.AnimationSlot.CAST;
            case HIT -> FirstPersonCombatLibrary.AnimationSlot.HIT;
            case DODGE -> FirstPersonCombatLibrary.AnimationSlot.DODGE;
            default -> null;
        };
        if (authoredSlot == null) return FirstPersonCombatLibrary.CameraFraming.identity();
        FirstPersonCombatLibrary.ItemProfile animationProfile = slot
                == CharacterModelDefinition.AnimationSlot.BLOCK && shieldProfile != null
                ? shieldProfile : resolved.itemProfile();
        WeaponType animationWeaponType = slot == CharacterModelDefinition.AnimationSlot.BLOCK
                && shieldProfile != null ? WeaponType.NONE : weaponType;
        FirstPersonCombatLibrary.ClipBinding binding = resolved.content().resolveBinding(
                animationWeaponType, animationProfile, resolved.rig(), authoredSlot);
        return binding == null
                ? FirstPersonCombatLibrary.CameraFraming.identity()
                : binding.cameraFraming();
    }

    private static double lerp(double source, double target, double amount) {
        return source + (target - source) * Math.max(0.0, Math.min(1.0, amount));
    }

    private static double lerpAngle(double source, double target, double amount) {
        double difference = (target - source) % 360.0;
        if (difference > 180.0) difference -= 360.0;
        if (difference < -180.0) difference += 360.0;
        return source + difference * Math.max(0.0, Math.min(1.0, amount));
    }

    private void drawArmAttachment(
            LwjglSkinnedModel model,
            FirstPersonAnimationView animation,
            LayeredAnimationView layeredAnimation,
            NamedAnimationView coupledAnimation,
            FirstPersonCombatLibrary.RigDefinition rig,
            FirstPersonCombatLibrary.WieldHand side,
            FirstPersonCombatLibrary.ArmCoverage coverage,
            Set<String> selectedMeshes,
            Vector3f offset
    ) {
        if (model == null || coverage == FirstPersonCombatLibrary.ArmCoverage.HIDE_FULL_ARM) return;
        LwjglSkinnedModel.Pose localPose = layeredAnimation == null
                ? composedFullPose(model, coupledAnimation)
                : composedPose(model, rig, layeredAnimation);
        LwjglSkinnedModel.Frame frame = localPose == null
                ? model.skinNormalized(animation.slot(), animation.progress())
                : model.skinPose(localPose);
        glPushMatrix();
        if (offset != null) glTranslated(offset.x, offset.y, offset.z);
        drawSkinnedModel(model, frame, mesh -> (selectedMeshes == null || selectedMeshes.isEmpty()
                || selectedMeshes.stream().anyMatch(name -> name.equalsIgnoreCase(mesh.name())))
                && regionVisible(mesh.name(), side, coverage));
        glPopMatrix();
    }

    private Vector3f secondaryGripOffset(
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            InventorySystem.Item weapon,
            FirstPersonCombatLibrary.ItemProfile profile,
            FirstPersonCombatLibrary.WieldHand weaponHand,
            FirstPersonAnimationView animation,
            LwjglSkinnedModel.Pose composedPose
    ) {
        if (profile == null || (Math.abs(profile.secondaryGripX()) < 0.0001
                && Math.abs(profile.secondaryGripY()) < 0.0001
                && Math.abs(profile.secondaryGripZ()) < 0.0001)) return new Vector3f();
        String weaponBone = FirstPersonAnimationRuntime.resolveAttachmentBone(
                resolved.rig(), profile, resolved.model());
        Matrix4f weaponSocket = composedPose == null
                ? animatedNodeTransform(resolved.model(), weaponBone, animation)
                : socketTransformWithoutScale(resolved.model().nodeTransform(composedPose, weaponBone));
        String offHandBone = resolved.rig().handBone(weaponHand.opposite());
        Matrix4f offHandSocket = composedPose == null
                ? animatedNodeTransform(resolved.model(), offHandBone, animation)
                : socketTransformWithoutScale(resolved.model().nodeTransform(composedPose, offHandBone));
        LwjglStaticModel weaponModel = weapon == null
                ? null : getStaticModel(weapon.getFirstPersonModelPath());
        if (weaponSocket == null || offHandSocket == null || weaponModel == null) return new Vector3f();
        EquipmentViewModelProfile socket = profile.socketTransform();
        float modelScale = (float) weaponModel.normalizedScaleForHeight(socket.normalizedHeight());
        Matrix4f gripTransform = new Matrix4f(weaponSocket)
                .translate((float) socket.positionX(), (float) socket.positionY(),
                        (float) socket.positionZ())
                .rotateXYZ((float) Math.toRadians(socket.rotationX()),
                        (float) Math.toRadians(socket.rotationY()),
                        (float) Math.toRadians(socket.rotationZ()))
                .scale(modelScale)
                .translate((float) -weaponModel.centerX(), (float) -weaponModel.baseY(),
                        (float) -weaponModel.centerZ());
        Vector3f target = gripTransform.transformPosition(new Vector3f(
                (float) profile.secondaryGripX(), (float) profile.secondaryGripY(),
                (float) profile.secondaryGripZ()));
        Vector3f current = offHandSocket.getTranslation(new Vector3f());
        Vector3f delta = target.sub(current, new Vector3f());
        if (delta.length() > 0.65f) delta.normalize().mul(0.65f);
        return delta;
    }

    private Matrix4f animatedNodeTransform(
            LwjglSkinnedModel model,
            String node,
            FirstPersonAnimationView animation
    ) {
        Matrix4f transform = socketTransformWithoutScale(model.nodeTransformNormalized(
                animation.slot(), animation.progress(), node));
        if (transform != null && animation.blend() < 1.0 && animation.fromSlot() != null) {
            Matrix4f from = socketTransformWithoutScale(model.nodeTransformNormalized(
                    animation.fromSlot(), animation.fromProgress(), node));
            if (from != null) transform = from.lerp(transform,
                    (float) smooth(animation.blend()), new Matrix4f());
        }
        return transform;
    }

    private Matrix4f socketTransformWithoutScale(Matrix4f source) {
        if (source == null) return null;
        Vector3f translation = source.getTranslation(new Vector3f());
        Quaternionf rotation = source.getUnnormalizedRotation(new Quaternionf()).normalize();
        return new Matrix4f().translation(translation).rotate(rotation);
    }

    private boolean regionVisible(
            String meshName,
            FirstPersonCombatLibrary.WieldHand side,
            FirstPersonCombatLibrary.ArmCoverage coverage
    ) {
        if (coverage == null || coverage == FirstPersonCombatLibrary.ArmCoverage.OVERLAY) return true;
        String name = meshName == null ? "" : meshName.toLowerCase(Locale.ROOT);
        String suffix = side == FirstPersonCombatLibrary.WieldHand.LEFT ? "l" : "r";
        boolean correctSide = name.contains("." + suffix) || name.contains("_" + suffix)
                || name.endsWith(suffix);
        if (!correctSide) return true;
        boolean hand = name.contains("hand");
        boolean forearm = name.contains("forearm");
        return switch (coverage) {
            case OVERLAY -> true;
            case HIDE_HAND -> !hand;
            case HIDE_FOREARM -> !hand && !forearm;
            case HIDE_FULL_ARM -> false;
        };
    }

    private void drawSocketEquipment(
            InventorySystem.Item item,
            EquipmentViewModelProfile transform,
            LwjglSkinnedModel rigModel,
            String handBone,
            FirstPersonAnimationView animation,
            LwjglSkinnedModel.Pose composedPose
    ) {
        if (item == null || transform == null || rigModel == null) return;
        LwjglStaticModel model = getStaticModel(item.getFirstPersonModelPath());
        if (model != null) model = model.withMaterial(item.getMaterial());
        Matrix4f socket = composedPose == null
                ? animatedNodeTransform(rigModel, handBone, animation)
                : socketTransformWithoutScale(rigModel.nodeTransform(composedPose, handBone));
        if (model == null || socket == null) return;
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        socket.get(matrix);
        glPushMatrix();
        glMultMatrixf(matrix);
        glTranslated(transform.positionX(), transform.positionY(), transform.positionZ());
        glRotated(transform.rotationX(), 1, 0, 0);
        glRotated(transform.rotationY(), 0, 1, 0);
        glRotated(transform.rotationZ(), 0, 0, 1);
        double scale = model.normalizedScaleForHeight(transform.normalizedHeight());
        glScaled(scale, scale, scale);
        glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
        for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
        glPopMatrix();
    }

    private LayeredAnimationView layeredAnimationView(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            FirstPersonCombatLibrary.ItemProfile shieldProfile,
            int crossfadeMs
    ) {
        LwjglSkinnedModel model = resolved.model();
        long now = System.nanoTime();
        LayerRequest left = new LayerRequest("IDLE_LEFT", loopingNamedProgress(model, "IDLE_LEFT"), -3L);
        LayerRequest right = new LayerRequest("IDLE_RIGHT", loopingNamedProgress(model, "IDLE_RIGHT"), -2L);
        LayerRequest global = new LayerRequest("IDLE_RIGHT", right.progress(), -1L);
        FirstPersonCombatLibrary.WieldHand blockHand = shieldProfile == null
                ? resolved.wieldHand().opposite() : shieldProfile.wieldHand();
        String blockKey = blockHand == FirstPersonCombatLibrary.WieldHand.LEFT
                ? "BLOCK_LEFT" : "BLOCK_RIGHT";
        if (player.getDefendingTurns() > 0) {
            LayerRequest guard = new LayerRequest(blockKey,
                    model.hasNamedClip(blockKey) ? model.namedImpactFraction(blockKey) : 0.55, -4L);
            if (blockHand == FirstPersonCombatLibrary.WieldHand.LEFT) left = guard;
            else right = guard;
        }

        LayerRequest full = null;
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == player) {
                switch (action.actionType()) {
                    case AUTO_ATTACK, PHYSICAL_SKILL, RANGED -> {
                        String key = resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                                ? "ATTACK_LEFT" : "ATTACK_RIGHT";
                        LayerRequest request = new LayerRequest(key, action.overallProgress(), action.sequence());
                        if (resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                                && request.newerThan(left)) left = request;
                        if (resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.RIGHT
                                && request.newerThan(right)) right = request;
                    }
                    case SPELL, HEAL, SUMMON -> {
                        String key = resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                                ? "CAST_LEFT" : "CAST_RIGHT";
                        LayerRequest request = new LayerRequest(key, action.overallProgress(), action.sequence());
                        if (resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                                && request.newerThan(left)) left = request;
                        if (resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.RIGHT
                                && request.newerThan(right)) right = request;
                    }
                    case DEFEND -> {
                        LayerRequest request = new LayerRequest(blockKey, action.overallProgress(), action.sequence());
                        if (blockHand == FirstPersonCombatLibrary.WieldHand.LEFT
                                && request.newerThan(left)) left = request;
                        if (blockHand == FirstPersonCombatLibrary.WieldHand.RIGHT
                                && request.newerThan(right)) right = request;
                    }
                    case DEATH -> {
                        LayerRequest request = new LayerRequest("HIT", action.overallProgress(), action.sequence());
                        if (request.newerThan(full)) full = request;
                    }
                }
            }
            for (BattlePresentationDirector.TargetReaction target : action.targets()) {
                if (target.target() != player) continue;
                switch (target.reaction()) {
                    case BLOCK -> {
                        LayerRequest request = new LayerRequest(blockKey, action.overallProgress(), action.sequence());
                        if (blockHand == FirstPersonCombatLibrary.WieldHand.LEFT
                                && request.newerThan(left)) left = request;
                        if (blockHand == FirstPersonCombatLibrary.WieldHand.RIGHT
                                && request.newerThan(right)) right = request;
                    }
                    case HIT, DODGE -> {
                        String key = target.reaction() == BattlePresentationDirector.Reaction.DODGE
                                ? "DODGE" : "HIT";
                        LayerRequest request = new LayerRequest(key, action.overallProgress(), action.sequence());
                        if (request.newerThan(full)) full = request;
                    }
                    case NONE -> {
                    }
                }
            }
        }

        left = availableRequest(model, left, "IDLE_LEFT");
        right = availableRequest(model, right, "IDLE_RIGHT");
        global = availableRequest(model, global, "IDLE_RIGHT");
        if (full != null && !model.hasNamedClip(full.key())) full = null;
        LayeredTransitionState state = layeredFirstPersonTransitions.computeIfAbsent(
                player, ignored -> new LayeredTransitionState());
        return new LayeredAnimationView(
                updateNamedTransition(state.global, global, crossfadeMs, now, false),
                updateNamedTransition(state.left, left, crossfadeMs, now, false),
                updateNamedTransition(state.right, right, crossfadeMs, now, false),
                updateNamedTransition(state.full, full, crossfadeMs, now, true));
    }

    private static LayerRequest availableRequest(
            LwjglSkinnedModel model,
            LayerRequest requested,
            String fallback
    ) {
        if (requested != null && model.hasNamedClip(requested.key())) return requested;
        return new LayerRequest(fallback, loopingNamedProgress(model, fallback),
                requested == null ? -1L : requested.identity());
    }

    private static double loopingNamedProgress(LwjglSkinnedModel model, String key) {
        double duration = model != null && model.hasNamedClip(key)
                ? model.namedClipDurationSeconds(key) : 1.0;
        return (System.nanoTime() / 1_000_000_000.0) / Math.max(0.001, duration) % 1.0;
    }

    private static NamedAnimationView updateNamedTransition(
            NamedTransition state,
            LayerRequest request,
            int crossfadeMs,
            long now,
            boolean fadeInitial
    ) {
        if (!state.initialized) {
            state.initialized = true;
            state.toKey = request == null ? null : request.key();
            state.toProgress = request == null ? 0.0 : request.progress();
            state.identity = request == null ? Long.MIN_VALUE : request.identity();
            state.startedNanos = fadeInitial && request != null ? now : 0L;
        } else {
            String requestedKey = request == null ? null : request.key();
            long requestedIdentity = request == null ? Long.MIN_VALUE : request.identity();
            if (!Objects.equals(state.toKey, requestedKey) || state.identity != requestedIdentity) {
                state.fromKey = state.toKey;
                state.fromProgress = state.toProgress;
                state.toKey = requestedKey;
                state.toProgress = request == null ? 0.0 : request.progress();
                state.identity = requestedIdentity;
                state.startedNanos = now;
            } else if (request != null) {
                state.toProgress = request.progress();
            }
        }
        double blend = state.startedNanos == 0L ? 1.0
                : Math.min(1.0, (now - state.startedNanos)
                / Math.max(1_000_000.0, crossfadeMs * 1_000_000.0));
        NamedAnimationView view = new NamedAnimationView(
                state.fromKey, state.fromProgress, state.toKey, state.toProgress, blend);
        if (blend >= 1.0) {
            state.fromKey = null;
            state.startedNanos = 0L;
        }
        return view;
    }

    private LwjglSkinnedModel.Pose composedPose(
            LwjglSkinnedModel model,
            FirstPersonCombatLibrary.RigDefinition rig,
            LayeredAnimationView animation
    ) {
        if (model == null || animation == null) return null;
        Set<String> leftMask = model.descendantNodeNames(rig.leftShoulderBone());
        Set<String> rightMask = model.descendantNodeNames(rig.rightShoulderBone());
        LinkedHashSet<String> globalMask = new LinkedHashSet<>(model.allNodeNames());
        globalMask.removeAll(leftMask);
        globalMask.removeAll(rightMask);
        List<LwjglSkinnedModel.PoseLayer> layers = new ArrayList<>(4);
        addPoseLayer(layers, animation.global(), globalMask);
        addPoseLayer(layers, animation.left(), leftMask);
        addPoseLayer(layers, animation.right(), rightMask);
        addPoseLayer(layers, animation.full(), Set.of());
        return model.composePose(layers);
    }

    private static LwjglSkinnedModel.Pose composedFullPose(
            LwjglSkinnedModel model,
            NamedAnimationView animation
    ) {
        if (model == null || animation == null
                || animation.fromKey() == null && animation.toKey() == null) return null;
        List<LwjglSkinnedModel.PoseLayer> layers = new ArrayList<>(1);
        addPoseLayer(layers, animation, Set.of());
        return model.composePose(layers);
    }

    private static NamedAnimationView coupledAnimationView(
            FirstPersonAnimationView animation,
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            FirstPersonCombatLibrary.ItemProfile shieldProfile
    ) {
        if (animation == null || resolved == null) return null;
        return new NamedAnimationView(
                coupledBindingKey(animation.fromSlot(), resolved, shieldProfile),
                animation.fromProgress(),
                coupledBindingKey(animation.slot(), resolved, shieldProfile),
                animation.progress(), animation.blend());
    }

    private static String coupledBindingKey(
            CharacterModelDefinition.AnimationSlot slot,
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            FirstPersonCombatLibrary.ItemProfile shieldProfile
    ) {
        if (slot == null) return null;
        return switch (slot) {
            case IDLE -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? "IDLE_LEFT" : "IDLE_RIGHT";
            case ATTACK -> resolved.wieldHand() == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? "ATTACK_LEFT" : "ATTACK_RIGHT";
            case BLOCK -> (shieldProfile == null
                    ? resolved.wieldHand().opposite() : shieldProfile.wieldHand())
                    == FirstPersonCombatLibrary.WieldHand.LEFT ? "BLOCK_LEFT" : "BLOCK_RIGHT";
            case CAST -> "CAST";
            case HIT, DEATH -> "HIT";
            case DODGE -> "DODGE";
            default -> null;
        };
    }

    private static void addPoseLayer(
            List<LwjglSkinnedModel.PoseLayer> layers,
            NamedAnimationView view,
            Set<String> mask
    ) {
        if (view == null || view.fromKey() == null && view.toKey() == null) return;
        LwjglSkinnedModel.NamedSample from = view.fromKey() == null ? null
                : new LwjglSkinnedModel.NamedSample(view.fromKey(), view.fromProgress());
        LwjglSkinnedModel.NamedSample to = view.toKey() == null ? null
                : new LwjglSkinnedModel.NamedSample(view.toKey(), view.toProgress());
        layers.add(new LwjglSkinnedModel.PoseLayer(from, to, view.blend(), mask));
    }

    private CharacterModelDefinition.AnimationSlot firstPersonSlot(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        BattlePresentationDirector.ActionSnapshot action = actions.stream()
                .filter(candidate -> candidate.attacker() == player
                        || candidate.targets().stream().anyMatch(target -> target.target() == player))
                .max(Comparator.comparingLong(BattlePresentationDirector.ActionSnapshot::sequence))
                .orElse(null);
        if (action != null) {
            if (action.attacker() == player) return FirstPersonAnimationRuntime.characterSlot(action.actionType());
            for (BattlePresentationDirector.TargetReaction target : action.targets()) {
                if (target.target() != player) continue;
                return switch (target.reaction()) {
                    case BLOCK -> CharacterModelDefinition.AnimationSlot.BLOCK;
                    case DODGE -> CharacterModelDefinition.AnimationSlot.DODGE;
                    case HIT -> CharacterModelDefinition.AnimationSlot.HIT;
                    case NONE -> CharacterModelDefinition.AnimationSlot.IDLE;
                };
            }
        }
        return CharacterModelDefinition.AnimationSlot.IDLE;
    }

    private double firstPersonProgress(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            CharacterModelDefinition.AnimationSlot slot,
            LwjglSkinnedModel model
    ) {
        Optional<BattlePresentationDirector.ActionSnapshot> newest = actions.stream()
                .filter(action -> action.attacker() == player
                        || action.targets().stream().anyMatch(target -> target.target() == player))
                .max(Comparator.comparingLong(BattlePresentationDirector.ActionSnapshot::sequence));
        if (newest.isPresent()) return newest.get().overallProgress();
        if (slot != CharacterModelDefinition.AnimationSlot.IDLE) {
            return 0.0;
        }
        double duration = model != null && model.hasClip(slot)
                ? model.clipDurationSeconds(slot)
                : 1.0;
        return (System.nanoTime() / 1_000_000_000.0)
                / Math.max(0.001, duration) % 1.0;
    }

    private FirstPersonAnimationView firstPersonAnimationView(
            BattleActor player,
            CharacterModelDefinition.AnimationSlot requestedSlot,
            double requestedProgress,
            int crossfadeMs
    ) {
        long now = System.nanoTime();
        FirstPersonTransition state = firstPersonTransitions.computeIfAbsent(
                player, ignored -> new FirstPersonTransition(requestedSlot, requestedProgress));
        if (state.slot != requestedSlot) {
            state.fromSlot = state.slot;
            state.fromProgress = state.progress;
            state.slot = requestedSlot;
            state.startedNanos = now;
        }
        state.progress = requestedProgress;
        double blend = state.startedNanos == 0
                ? 1.0
                : Math.min(1.0, (now - state.startedNanos)
                        / Math.max(1_000_000.0, crossfadeMs * 1_000_000.0));
        if (blend >= 1.0) {
            state.fromSlot = null;
            state.startedNanos = 0;
        }
        return new FirstPersonAnimationView(
                state.slot, state.progress, state.fromSlot, state.fromProgress, blend);
    }

    private void renderEquipment(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int viewportWidth,
            int viewportHeight
    ) {
        if (player == null || player.getSourcePlayer() == null) return;
        if (renderSkeletalEquipment(player, actions, viewportWidth, viewportHeight)) return;
        InventorySystem.Inventory inventory = player.getSourcePlayer().getInventory();
        InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = inventory.getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        InventorySystem.Item chest = inventory.getEquippedItem(InventorySystem.EquipmentSlot.CHEST);
        double attackSwing = actions.stream()
                .filter(action -> action.attacker() == player
                        && (action.actionType() == BattlePresentationDirector.ActionType.AUTO_ATTACK
                        || action.actionType() == BattlePresentationDirector.ActionType.PHYSICAL_SKILL))
                .findFirst().map(LwjglBattleSceneRenderer::actionEnvelope).orElse(0.0);
        double castLower = actions.stream()
                .filter(action -> action.attacker() == player
                        && (action.actionType() == BattlePresentationDirector.ActionType.SPELL
                        || action.actionType() == BattlePresentationDirector.ActionType.HEAL
                        || action.actionType() == BattlePresentationDirector.ActionType.SUMMON))
                .findFirst().map(LwjglBattleSceneRenderer::actionEnvelope).orElse(0.0);
        double blockRaise = actions.stream()
                .filter(action -> action.targets().stream().anyMatch(target ->
                        target.target() == player
                                && target.reaction() == BattlePresentationDirector.Reaction.BLOCK))
                .findFirst().map(LwjglBattleSceneRenderer::actionEnvelope).orElse(0.0);
        boolean twoHanded = weapon != null && weapon.isTwoHanded();
        EquipmentViewModelProfile motionProfile = weapon == null
                ? EquipmentViewModelProfile.defaults()
                : weapon.getViewModelProfile();
        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        if (chest != null && chest.hasFirstPersonModel()) {
            for (FirstPersonEquipmentRig.Pose pose
                    : FirstPersonEquipmentRig.chestHands(chest.getViewModelProfile(), twoHanded)) {
                drawAttachedEquipmentModel(
                        chest,
                        pose,
                        motionProfile,
                        FirstPersonEquipmentRig.followsPrimaryMotion(pose, twoHanded)
                                ? -attackSwing * 58 : 0);
            }
        } else {
            for (FirstPersonEquipmentRig.Pose pose
                    : FirstPersonEquipmentRig.builtInHands(
                            twoHanded, weapon == null ? attackSwing : 0)) {
                drawAttachedBuiltInHand(
                        pose,
                        motionProfile,
                        weapon != null && FirstPersonEquipmentRig.followsPrimaryMotion(pose, twoHanded)
                                ? -attackSwing * 58 : 0);
            }
        }
        if (weapon != null && weapon.hasFirstPersonModel()) {
            FirstPersonEquipmentRig.Pose pose = FirstPersonEquipmentRig.weapon(
                    weapon.getViewModelProfile(), twoHanded, castLower);
            drawAttachedEquipmentModel(weapon, pose, motionProfile, -attackSwing * 58);
        }
        if (shield != null && shield.hasFirstPersonModel() && !twoHanded) {
            drawEquipmentModel(shield, FirstPersonEquipmentRig.shield(
                    shield.getViewModelProfile(), castLower, blockRaise));
        }
    }

    private void drawAttachedEquipmentModel(
            InventorySystem.Item item,
            FirstPersonEquipmentRig.Pose pose,
            EquipmentViewModelProfile motionProfile,
            double motionDegrees
    ) {
        glPushMatrix();
        applyPrimaryHandMotion(motionProfile, motionDegrees);
        drawEquipmentModel(item, pose);
        glPopMatrix();
    }

    private void drawEquipmentModel(InventorySystem.Item item, FirstPersonEquipmentRig.Pose pose) {
        LwjglStaticModel model = getStaticModel(item.getFirstPersonModelPath());
        if (model != null) model = model.withMaterial(item.getMaterial());
        if (model == null) return;
        glPushMatrix();
        glTranslated(pose.x(), pose.y(), pose.z());
        glRotated(pose.rotationX(), 1, 0, 0);
        glRotated(pose.rotationY(), 0, 1, 0);
        glRotated(pose.rotationZ(), 0, 0, 1);
        double scale = model.normalizedScaleForHeight(pose.normalizedHeight());
        glScaled(pose.mirrored() ? -scale : scale, scale, scale);
        glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
        for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
        glPopMatrix();
    }

    private void drawAttachedBuiltInHand(
            FirstPersonEquipmentRig.Pose pose,
            EquipmentViewModelProfile motionProfile,
            double motionDegrees
    ) {
        glPushMatrix();
        applyPrimaryHandMotion(motionProfile, motionDegrees);
        drawBuiltInHand(pose);
        glPopMatrix();
    }

    private void applyPrimaryHandMotion(EquipmentViewModelProfile profile, double degrees) {
        if (Math.abs(degrees) < 0.0001) return;
        glTranslated(FirstPersonEquipmentRig.PRIMARY_HAND_X,
                FirstPersonEquipmentRig.PRIMARY_HAND_Y,
                FirstPersonEquipmentRig.PRIMARY_HAND_Z);
        glRotated(degrees, profile.swingAxisX(), profile.swingAxisY(), profile.swingAxisZ());
        glTranslated(-FirstPersonEquipmentRig.PRIMARY_HAND_X,
                -FirstPersonEquipmentRig.PRIMARY_HAND_Y,
                -FirstPersonEquipmentRig.PRIMARY_HAND_Z);
    }

    private void drawBuiltInHand(FirstPersonEquipmentRig.Pose pose) {
        glDisable(GL_TEXTURE_2D);
        glPushMatrix();
        glTranslated(pose.x(), pose.y(), pose.z());
        glRotated(pose.rotationX(), 1, 0, 0);
        glRotated(pose.rotationY(), 0, 1, 0);
        glRotated(pose.rotationZ(), 0, 0, 1);

        setLitColor(0.68f, 0.46f, 0.31f, 1f);
        drawBox(0.075, 0.095, 0.105);
        glTranslated(0, -0.135, 0.045);
        setLitColor(0.60f, 0.39f, 0.26f, 1f);
        drawBox(0.052, 0.075, 0.070);
        glPopMatrix();
    }

    private void drawBox(double halfWidth, double halfHeight, double halfDepth) {
        fixedPrimitives.drawBox(halfWidth, halfHeight, halfDepth);
    }

    private void renderEffects(List<BattlePresentationDirector.ActionSnapshot> actions,
                               Position playerCell, CameraLookState look) {
        glDisable(GL_DEPTH_TEST); glDisable(GL_TEXTURE_2D);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        glRotated(look.pitchOffsetDegrees(), 1, 0, 0);
        glRotated(-look.yawOffsetDegrees(), 0, 1, 0);
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.phase() != BattlePresentationDirector.Phase.IMPACT
                    || (action.actionType() != BattlePresentationDirector.ActionType.SPELL
                    && action.actionType() != BattlePresentationDirector.ActionType.HEAL)) continue;
            List<BattlePresentationDirector.TargetReaction> targets = action.targets().isEmpty()
                    ? List.of(new BattlePresentationDirector.TargetReaction(action.attacker(),
                    BattlePresentationDirector.Reaction.NONE, 0)) : action.targets();
            for (BattlePresentationDirector.TargetReaction target : targets) {
                Position p = relative(formationPosition(target.target(), !target.target().isEnemy()), playerCell);
                glPushMatrix(); glTranslated(p.x(), 0.85, p.z() + 0.03);
                if (action.actionType() == BattlePresentationDirector.ActionType.HEAL) {
                    glColor4f(0.35f, 1.0f, 1.0f, 0.78f);
                } else {
                    java.awt.Color color = target.element().getColor();
                    glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                            color.getBlue() / 255f, 0.78f);
                }
                fixedPrimitives.drawCenteredQuad(0.56, 0.56, false);
                glPopMatrix();
            }
        }
    }

    private void applyProceduralReaction(BattleActor actor, List<BattlePresentationDirector.ActionSnapshot> actions) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) for (var target : action.targets()) if (target.target() == actor) {
            double amount = actionEnvelope(action);
            if (target.reaction() == BattlePresentationDirector.Reaction.HIT) glRotated(-10 * amount, 1, 0, 0);
            else if (target.reaction() == BattlePresentationDirector.Reaction.DODGE) glTranslated(0.35 * amount, 0, 0);
            else if (target.reaction() == BattlePresentationDirector.Reaction.BLOCK) glRotated(8 * amount, 0, 0, 1);
        }
    }

    private void applyProceduralWholeModel(BattleActor actor,
                                           CharacterModelDefinition.AnimationSlot slot,
                                           List<BattlePresentationDirector.ActionSnapshot> actions) {
        double amount = animationProgress(
                actor,
                actions,
                null,
                animationSlot(actor, actions));
        switch (slot) {
            case DEATH -> { glTranslated(0, -0.55 * amount, 0); glRotated(78 * amount, 0, 0, 1); }
            case ATTACK -> glRotated(-16 * Math.sin(Math.PI * amount), 1, 0, 0);
            case CAST -> glTranslated(0, 0.08 * Math.sin(Math.PI * amount), 0);
            default -> applyProceduralReaction(actor, actions);
        }
    }

    private Position animatePosition(BattleActor actor, Position base, Position playerCell,
                                     List<BattlePresentationDirector.ActionSnapshot> actions) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() != actor || action.targets().isEmpty()
                    || (action.actionType() != BattlePresentationDirector.ActionType.AUTO_ATTACK
                    && action.actionType() != BattlePresentationDirector.ActionType.PHYSICAL_SKILL)) continue;
            BattleActor target = action.targets().get(0).target();
            Position targetPosition = relative(formationPosition(target, !target.isEnemy()), playerCell);
            double travel = actionEnvelope(action);
            double dx = targetPosition.x() - base.x(), dz = targetPosition.z() - base.z();
            double length = Math.max(0.001, Math.hypot(dx, dz));
            double stop = Math.max(0.0, length - 0.75);
            double lateral = Math.sin(Math.PI * travel) * 0.28 * (actor.getSlot() == 2 ? -1 : 1);
            return new Position(base.x() + dx / length * stop * travel - dz / length * lateral,
                    base.y(), base.z() + dz / length * stop * travel + dx / length * lateral);
        }
        return base;
    }

    private static double actionEnvelope(BattleActor actor, List<BattlePresentationDirector.ActionSnapshot> actions) {
        return actions.stream().filter(action -> action.attacker() == actor).findFirst()
                .map(LwjglBattleSceneRenderer::actionEnvelope).orElse(0.0);
    }

    private static double actionEnvelope(BattlePresentationDirector.ActionSnapshot action) {
        return switch (action.phase()) {
            case WINDUP -> smooth(action.progress());
            case IMPACT -> 1.0;
            case RECOVERY -> 1.0 - smooth(action.progress());
        };
    }

    private static double cameraLunge(BattleEncounter encounter, BattleActor player) {
        if (player == null) return 0.0;
        return encounter.getPresentationDirector().snapshots().stream()
                .filter(action -> action.attacker() == player
                        && (action.actionType() == BattlePresentationDirector.ActionType.AUTO_ATTACK
                        || action.actionType() == BattlePresentationDirector.ActionType.PHYSICAL_SKILL))
                .findFirst().map(action -> -0.22 * actionEnvelope(action)).orElse(0.0);
    }

    private static CharacterModelDefinition.AnimationSlot animationSlot(
            BattleActor actor, List<BattlePresentationDirector.ActionSnapshot> actions) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == actor) return switch (action.actionType()) {
                case DEATH -> CharacterModelDefinition.AnimationSlot.DEATH;
                case SPELL, HEAL, SUMMON -> CharacterModelDefinition.AnimationSlot.CAST;
                case DEFEND -> CharacterModelDefinition.AnimationSlot.BLOCK;
                default -> CharacterModelDefinition.AnimationSlot.ATTACK;
            };
            for (var target : action.targets()) if (target.target() == actor) return switch (target.reaction()) {
                case BLOCK -> CharacterModelDefinition.AnimationSlot.BLOCK;
                case DODGE -> CharacterModelDefinition.AnimationSlot.DODGE;
                case HIT -> CharacterModelDefinition.AnimationSlot.HIT;
                default -> CharacterModelDefinition.AnimationSlot.IDLE;
            };
        }
        return actor.isAlive() ? CharacterModelDefinition.AnimationSlot.IDLE : CharacterModelDefinition.AnimationSlot.DEATH;
    }

    private static double animationProgress(
            BattleActor actor,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            LwjglSkinnedModel model,
            CharacterModelDefinition.AnimationSlot slot
    ) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == actor || action.targets().stream().anyMatch(target -> target.target() == actor)) {
                return action.overallProgress();
            }
        }
        if (slot != CharacterModelDefinition.AnimationSlot.IDLE) {
            return 0.0;
        }
        double duration = model != null && model.hasClip(slot)
                ? model.clipDurationSeconds(slot)
                : 1.0;
        return (System.nanoTime() / 1_000_000_000.0)
                / Math.max(0.001, duration) % 1.0;
    }

    private static double facingYaw(
            BattleActor actor,
            Position actorPosition,
            Position playerCell,
            BattleEncounter encounter
    ) {
        List<BattleActor> opponents = actor.isEnemy()
                ? encounter.getAllies()
                : encounter.getEnemies();
        Position nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (BattleActor opponent : opponents) {
            if (opponent == null || !opponent.isAlive()) {
                continue;
            }
            Position opponentPosition = relative(
                    formationPosition(opponent, !opponent.isEnemy()),
                    playerCell);
            double deltaX = opponentPosition.x() - actorPosition.x();
            double deltaZ = opponentPosition.z() - actorPosition.z();
            double distance = deltaX * deltaX + deltaZ * deltaZ;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = opponentPosition;
            }
        }
        if (nearest == null) {
            return actor.isEnemy() ? 0.0 : 180.0;
        }
        double deltaX = nearest.x() - actorPosition.x();
        double deltaZ = nearest.z() - actorPosition.z();
        return Math.toDegrees(Math.atan2(deltaX, deltaZ));
    }

    private static boolean hasDeathPresentation(BattleActor actor,
                                                List<BattlePresentationDirector.ActionSnapshot> actions) {
        return actions.stream().anyMatch(action -> action.attacker() == actor
                && action.actionType() == BattlePresentationDirector.ActionType.DEATH);
    }

    private void projectActors(BattleEncounter encounter, BattleActor player, Position playerCell,
                               CameraLookState look, int width, int height) {
        List<BattleActor> actors = new ArrayList<>(); actors.addAll(encounter.getAllies()); actors.addAll(encounter.getEnemies());
        for (BattleActor actor : actors) {
            if (actor == player || !actor.isAlive()) continue;
            Position p = relative(formationPosition(actor, !actor.isEnemy()), playerCell);
            CharacterModelDefinition definition = actor.getCharacterModel();
            double actorHeight = STANDARD_ACTOR_HEIGHT * Math.max(0.01, definition.scale());
            double markerY = p.y() + definition.verticalOffset() + actorHeight + 0.08;
            Point point = project(p.x(), markerY, p.z(), look, width, height);
            if (point != null) projectedActors.put(actor, point);
        }
    }

    private static Point project(double x, double y, double z, CameraLookState look, int width, int height) {
        double yaw = Math.toRadians(-look.yawOffsetDegrees()), pitch = Math.toRadians(look.pitchOffsetDegrees());
        y -= CAMERA_EYE_HEIGHT;
        double vx = x * Math.cos(yaw) + z * Math.sin(yaw), yz = -x * Math.sin(yaw) + z * Math.cos(yaw);
        double vy = y * Math.cos(pitch) - yz * Math.sin(pitch), vz = y * Math.sin(pitch) + yz * Math.cos(pitch);
        if (vz >= -0.05) return null;
        double tan = Math.tan(Math.toRadians(70) / 2), aspect = width / (double) Math.max(1, height);
        double nx = (vx / -vz) / (tan * aspect), ny = (vy / -vz) / tan;
        if (Math.abs(nx) > 1.1 || Math.abs(ny) > 1.1) return null;
        return new Point((int) ((nx + 1) * width / 2), (int) ((1 - ny) * height / 2));
    }

    private static Position formationPosition(BattleActor actor, boolean ally) {
        double x = (actor.getSlot() - 1) * COLUMN_SPACING;
        double z = ally ? (actor.getRow() == Library.BattleRow.FRONT ? ALLY_FRONT_Z : ALLY_BACK_Z)
                : (actor.getRow() == Library.BattleRow.FRONT ? ENEMY_FRONT_Z : ENEMY_BACK_Z);
        return new Position(x, 0, z);
    }
    private static Position relative(Position value, Position origin) {
        return new Position(value.x() - origin.x(), value.y() - origin.y(), value.z() - origin.z());
    }
    private static double smooth(double v) { v = Math.max(0, Math.min(1, v)); return v * v * (3 - 2 * v); }

    private void setLitColor(float red, float green, float blue, float alpha) {
        glColor4f(
                red * encounterLight[0],
                green * encounterLight[1],
                blue * encounterLight[2],
                alpha);
    }

    private static float clampLight(float value) {
        return Math.max(0.02f, Math.min(4.0f, value));
    }

    private LwjglSkinnedModel getSkinnedModel(CharacterModelDefinition definition) {
        if (definition == null || !definition.hasModel() || failedSkinnedModels.contains(definition)) return null;
        LwjglSkinnedModel cached = skinnedModels.get(definition); if (cached != null) return cached;
        try { LwjglSkinnedModel loaded = LwjglSkinnedModel.loadCached(definition); skinnedModels.put(definition, loaded); return loaded; }
        catch (Exception exception) { failedSkinnedModels.add(definition); LOGGER.log(Level.WARNING, "Character model fallback: " + definition.modelPath(), exception); return null; }
    }
    private LwjglStaticModel getStaticModel(String path) {
        if (path == null || path.isBlank() || failedStaticModels.contains(path)) return null;
        LwjglStaticModel cached = staticModels.get(path); if (cached != null) return cached;
        try { LwjglStaticModel loaded = LwjglStaticModel.load(path); staticModels.put(path, loaded); return loaded; }
        catch (IOException exception) { failedStaticModels.add(path); return null; }
    }

    void shutdown() {
        if (fixedPrimitives != null) {
            fixedPrimitives.shutdown();
            fixedPrimitives = null;
        }
        for (MeshBuffers mesh : buffers.values()) { glDeleteBuffers(mesh.positionVbo()); glDeleteBuffers(mesh.uvVbo()); glDeleteBuffers(mesh.indexBuffer()); }
        for (MeshBuffers mesh : staticBuffers.values()) { glDeleteBuffers(mesh.positionVbo()); glDeleteBuffers(mesh.uvVbo()); glDeleteBuffers(mesh.indexBuffer()); }
        buffers.clear(); staticBuffers.clear(); skinnedModels.clear(); staticModels.clear(); firstPersonTransitions.clear();
        layeredFirstPersonTransitions.clear();
        warnedFirstPersonFallbacks.clear();
    }

    private static final class FirstPersonTransition {
        private CharacterModelDefinition.AnimationSlot slot;
        private double progress;
        private CharacterModelDefinition.AnimationSlot fromSlot;
        private double fromProgress;
        private long startedNanos;

        private FirstPersonTransition(
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            this.slot = slot;
            this.progress = progress;
        }
    }

    private record FirstPersonAnimationView(
            CharacterModelDefinition.AnimationSlot slot,
            double progress,
            CharacterModelDefinition.AnimationSlot fromSlot,
            double fromProgress,
            double blend
    ) { }

    private record LayerRequest(String key, double progress, long identity) {
        boolean newerThan(LayerRequest other) {
            return other == null || identity > other.identity;
        }
    }

    private static final class NamedTransition {
        private boolean initialized;
        private String fromKey;
        private double fromProgress;
        private String toKey;
        private double toProgress;
        private long identity = Long.MIN_VALUE;
        private long startedNanos;
    }

    private static final class LayeredTransitionState {
        private final NamedTransition global = new NamedTransition();
        private final NamedTransition left = new NamedTransition();
        private final NamedTransition right = new NamedTransition();
        private final NamedTransition full = new NamedTransition();
    }

    private record NamedAnimationView(
            String fromKey,
            double fromProgress,
            String toKey,
            double toProgress,
            double blend
    ) {
    }

    private record LayeredAnimationView(
            NamedAnimationView global,
            NamedAnimationView left,
            NamedAnimationView right,
            NamedAnimationView full
    ) {
    }

    private record Position(double x, double y, double z) { }
    private record MeshBuffers(
            int positionVbo,
            int uvVbo,
            int indexBuffer,
            FloatBuffer positionStaging,
            int indexCount
    ) { }
}

package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
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
    private final Map<BattleActor, Point> projectedActors = new IdentityHashMap<>();
    private final Map<BattleActor, FirstPersonTransition> firstPersonTransitions =
            new IdentityHashMap<>();

    LwjglBattleSceneRenderer(LwjglTextureCache textureCache) {
        this.textureCache = textureCache;
    }

    Map<BattleActor, Point> render(DungeonRenderContext context, CameraLookState lookState,
                                   AetherGameRuntime runtime, int width, int height) {
        projectedActors.clear();
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
            drawActor(actor, relative(formationPosition(actor, true), playerCell), playerCell, actions);
        }
        for (BattleActor actor : encounter.getEnemies()) {
            if (!actor.isAlive() && !hasDeathPresentation(actor, actions)) continue;
            drawActor(actor, relative(formationPosition(actor, false), playerCell), playerCell, actions);
        }
        projectActors(encounter, player, playerCell, look, width, height);

        renderEquipment(player, actions);
        renderEffects(actions, playerCell, look);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1f, 1f, 1f, 1f);
        return Map.copyOf(projectedActors);
    }

    private void drawActor(BattleActor actor, Position base, Position playerCell,
                           List<BattlePresentationDirector.ActionSnapshot> actions) {
        Position animated = animatePosition(actor, base, playerCell, actions);
        CharacterModelDefinition.AnimationSlot slot = animationSlot(actor, actions);
        double progress = animationProgress(actor, actions);
        CharacterModelDefinition definition = actor.getCharacterModel();
        LwjglSkinnedModel skinned = getSkinnedModel(definition);
        glPushMatrix();
        glTranslated(animated.x(), animated.y() + definition.verticalOffset(), animated.z());
        glRotated(actor.isEnemy() ? 180.0 + definition.facingRotationDegrees()
                : definition.facingRotationDegrees(), 0, 1, 0);
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
            FloatBuffer positions = BufferUtils.createFloatBuffer(frame.meshPositions().get(meshIndex).length);
            positions.put(frame.meshPositions().get(meshIndex)).flip();
            glBindBuffer(GL_ARRAY_BUFFER, gpu.positionVbo());
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
            glColor4f(material.red(), material.green(), material.blue(), material.alpha());
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
        return new MeshBuffers(position, uv, indices);
    }

    private void drawStaticMesh(LwjglStaticModel.Mesh mesh) {
        if (mesh.texture() == null) glDisable(GL_TEXTURE_2D);
        else { glEnable(GL_TEXTURE_2D); textureCache.bind(mesh.texture()); }
        glColor4f(mesh.red(), mesh.green(), mesh.blue(), mesh.alpha());
        glBegin(GL_TRIANGLES);
        for (int index : mesh.indices()) {
            if (mesh.texture() != null) glTexCoord2f(mesh.texCoords()[index * 2], mesh.texCoords()[index * 2 + 1]);
            glVertex3f(mesh.positions()[index * 3], mesh.positions()[index * 3 + 1], mesh.positions()[index * 3 + 2]);
        }
        glEnd();
    }

    private void drawBillboard(BufferedImage image, double width, double height) {
        if (image != null) { glEnable(GL_TEXTURE_2D); textureCache.bind(image); glColor4f(1, 1, 1, 1); }
        else { glDisable(GL_TEXTURE_2D); glColor4f(0.55f, 0.18f, 0.65f, 1f); }
        glBegin(GL_QUADS);
        glTexCoord2d(0, 1); glVertex3d(-width / 2, 0, 0);
        glTexCoord2d(1, 1); glVertex3d(width / 2, 0, 0);
        glTexCoord2d(1, 0); glVertex3d(width / 2, height, 0);
        glTexCoord2d(0, 0); glVertex3d(-width / 2, height, 0);
        glEnd();
    }

    private boolean renderSkeletalEquipment(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        FirstPersonAnimationRuntime.ResolvedRig resolved =
                FirstPersonAnimationRuntime.resolve(player);
        if (!resolved.usable()) return false;
        FirstPersonCombatLibrary.RigDefinition rig = resolved.content().rig();
        InventorySystem.Inventory inventory = player.getSourcePlayer().getInventory();
        InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = inventory.getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        InventorySystem.Item chest = inventory.getEquippedItem(InventorySystem.EquipmentSlot.CHEST);
        FirstPersonCombatLibrary.ItemProfile weaponProfile = resolved.itemProfile();
        FirstPersonCombatLibrary.ItemProfile shieldProfile = resolved.content().itemProfile(shield);
        FirstPersonCombatLibrary.ItemProfile armorProfile = resolved.content().itemProfile(chest);

        // Authored legacy camera-space models stay on the proven fallback path
        // until a socket/attachment profile is created for them.
        if (weapon != null && weapon.hasFirstPersonModel() && weaponProfile == null) return false;
        if (shield != null && shield.hasFirstPersonModel() && shieldProfile == null) return false;
        if (chest != null && chest.hasFirstPersonModel()
                && (armorProfile == null
                || (armorProfile.leftArmorPath().isBlank()
                && armorProfile.rightArmorPath().isBlank()))) return false;

        CharacterModelDefinition.AnimationSlot slot = firstPersonSlot(player, actions);
        if (!resolved.model().hasClip(slot)) {
            warnFirstPersonFallback("clip:" + slot,
                    "First-person " + slot.displayName()
                            + " clip is unavailable; using procedural equipment animation.");
            return false;
        }
        double progress = firstPersonProgress(player, actions, slot);
        FirstPersonAnimationView animation = firstPersonAnimationView(player, slot, progress);

        LimbItem leftLimb = player.getSourcePlayer().getEquippedLimb(LimbSlot.LEFT_ARM);
        LimbItem rightLimb = player.getSourcePlayer().getEquippedLimb(LimbSlot.RIGHT_ARM);
        String leftPath = leftLimb != null && leftLimb.hasFirstPersonModel()
                ? leftLimb.getFirstPersonModelPath() : rig.defaultLeftArmPath();
        String rightPath = rightLimb != null && rightLimb.hasFirstPersonModel()
                ? rightLimb.getFirstPersonModelPath() : rig.defaultRightArmPath();
        if (leftPath.isBlank() && rightPath.isBlank()) {
            warnFirstPersonFallback("arms",
                    "First-person rig has no default or grafted arm attachments; using procedural hands.");
            return false;
        }

        LwjglSkinnedModel leftArm = attachmentModel(resolved, leftPath);
        LwjglSkinnedModel rightArm = attachmentModel(resolved, rightPath);
        LwjglSkinnedModel leftArmor = attachmentModel(
                resolved, armorProfile == null ? "" : armorProfile.leftArmorPath());
        LwjglSkinnedModel rightArmor = attachmentModel(
                resolved, armorProfile == null ? "" : armorProfile.rightArmorPath());
        if ((leftArm == null && !leftPath.isBlank()) || (rightArm == null && !rightPath.isBlank())) {
            warnFirstPersonFallback("arm-signature",
                    "A first-person arm attachment is missing or has an incompatible skeleton; "
                            + "using procedural hands.");
            return false;
        }

        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        applyRigRoot(rig);

        FirstPersonCombatLibrary.ArmCoverage leftCoverage = armorProfile == null
                ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : armorProfile.leftCoverage();
        FirstPersonCombatLibrary.ArmCoverage rightCoverage = armorProfile == null
                ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : armorProfile.rightCoverage();
        drawArmAttachment(leftArm, animation, FirstPersonCombatLibrary.WieldHand.LEFT, leftCoverage);
        drawArmAttachment(rightArm, animation, FirstPersonCombatLibrary.WieldHand.RIGHT, rightCoverage);
        drawArmAttachment(leftArmor, animation, FirstPersonCombatLibrary.WieldHand.LEFT,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY);
        drawArmAttachment(rightArmor, animation, FirstPersonCombatLibrary.WieldHand.RIGHT,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY);

        FirstPersonCombatLibrary.WieldHand weaponHand = resolved.wieldHand();
        if (weapon != null && weapon.hasFirstPersonModel()) {
            drawSocketEquipment(weapon, weaponProfile.socketTransform(), resolved.model(),
                    rig.handBone(weaponHand), animation);
        }
        if (shield != null && shield.hasFirstPersonModel()
                && (weapon == null || !weapon.isTwoHanded())) {
            FirstPersonCombatLibrary.WieldHand shieldHand = weaponHand.opposite();
            drawSocketEquipment(shield, shieldProfile.socketTransform(), resolved.model(),
                    rig.handBone(shieldHand), animation);
        }
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
        return true;
    }

    private LwjglSkinnedModel attachmentModel(
            FirstPersonAnimationRuntime.ResolvedRig resolved,
            String path
    ) {
        if (path == null || path.isBlank()) return null;
        CharacterModelDefinition attachmentDefinition = new CharacterModelDefinition(
                path,
                resolved.content().rig().rigId(),
                1.0,
                0.0,
                0.0,
                resolved.modelDefinition().animationBindings());
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

    private void applyRigRoot(FirstPersonCombatLibrary.RigDefinition rig) {
        glTranslated(rig.positionX(), rig.positionY(), rig.positionZ());
        glRotated(rig.rotationX(), 1, 0, 0);
        glRotated(rig.rotationY(), 0, 1, 0);
        glRotated(rig.rotationZ(), 0, 0, 1);
        glScaled(rig.scale(), rig.scale(), rig.scale());
    }

    private void drawArmAttachment(
            LwjglSkinnedModel model,
            FirstPersonAnimationView animation,
            FirstPersonCombatLibrary.WieldHand side,
            FirstPersonCombatLibrary.ArmCoverage coverage
    ) {
        if (model == null || coverage == FirstPersonCombatLibrary.ArmCoverage.HIDE_FULL_ARM) return;
        LwjglSkinnedModel.Frame frame = model.skinNormalized(animation.slot(), animation.progress());
        if (animation.blend() < 1.0 && animation.fromSlot() != null) {
            LwjglSkinnedModel.Frame from = model.skinNormalized(
                    animation.fromSlot(), animation.fromProgress());
            frame = blendFrames(from, frame, animation.blend());
        }
        drawSkinnedModel(model, frame, mesh -> regionVisible(mesh.name(), side, coverage));
    }

    private LwjglSkinnedModel.Frame blendFrames(
            LwjglSkinnedModel.Frame from,
            LwjglSkinnedModel.Frame to,
            double amount
    ) {
        float blend = (float) smooth(amount);
        List<float[]> positions = new ArrayList<>(to.meshPositions().size());
        for (int mesh = 0; mesh < to.meshPositions().size(); mesh++) {
            float[] target = to.meshPositions().get(mesh);
            float[] source = mesh < from.meshPositions().size()
                    ? from.meshPositions().get(mesh) : target;
            float[] output = target.clone();
            for (int index = 0; index < output.length && index < source.length; index++) {
                output[index] = source[index] + (target[index] - source[index]) * blend;
            }
            positions.add(output);
        }
        return new LwjglSkinnedModel.Frame(List.copyOf(positions), to.normalizedProgress());
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
            FirstPersonAnimationView animation
    ) {
        if (item == null || transform == null || rigModel == null) return;
        LwjglStaticModel model = getStaticModel(item.getFirstPersonModelPath());
        Matrix4f socket = rigModel.nodeTransformNormalized(
                animation.slot(), animation.progress(), handBone);
        if (socket != null && animation.blend() < 1.0 && animation.fromSlot() != null) {
            Matrix4f from = rigModel.nodeTransformNormalized(
                    animation.fromSlot(), animation.fromProgress(), handBone);
            if (from != null) socket = from.lerp(socket, (float) smooth(animation.blend()),
                    new Matrix4f());
        }
        if (model == null || socket == null) return;
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        socket.get(matrix);
        glPushMatrix();
        glMultMatrixf(matrix);
        glTranslated(transform.positionX(), transform.positionY(), transform.positionZ());
        glRotated(transform.rotationX(), 1, 0, 0);
        glRotated(transform.rotationY(), 0, 1, 0);
        glRotated(transform.rotationZ(), 0, 0, 1);
        double scale = transform.normalizedHeight();
        glScaled(scale, scale, scale);
        for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
        glPopMatrix();
    }

    private CharacterModelDefinition.AnimationSlot firstPersonSlot(
            BattleActor player,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == player) {
                return FirstPersonAnimationRuntime.characterSlot(action.actionType());
            }
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
            CharacterModelDefinition.AnimationSlot slot
    ) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == player
                    || action.targets().stream().anyMatch(target -> target.target() == player)) {
                return action.overallProgress();
            }
        }
        return slot == CharacterModelDefinition.AnimationSlot.IDLE
                ? (System.nanoTime() / 1_000_000_000.0) % 1.0 : 0.0;
    }

    private FirstPersonAnimationView firstPersonAnimationView(
            BattleActor player,
            CharacterModelDefinition.AnimationSlot requestedSlot,
            double requestedProgress
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
                : Math.min(1.0, (now - state.startedNanos) / 120_000_000.0);
        if (blend >= 1.0) {
            state.fromSlot = null;
            state.startedNanos = 0;
        }
        return new FirstPersonAnimationView(
                state.slot, state.progress, state.fromSlot, state.fromProgress, blend);
    }

    private void renderEquipment(BattleActor player, List<BattlePresentationDirector.ActionSnapshot> actions) {
        if (player == null || player.getSourcePlayer() == null) return;
        if (renderSkeletalEquipment(player, actions)) return;
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

        glColor4f(0.68f, 0.46f, 0.31f, 1f);
        drawBox(0.075, 0.095, 0.105);
        glTranslated(0, -0.135, 0.045);
        glColor4f(0.60f, 0.39f, 0.26f, 1f);
        drawBox(0.052, 0.075, 0.070);
        glPopMatrix();
    }

    private void drawBox(double halfWidth, double halfHeight, double halfDepth) {
        double left = -halfWidth;
        double right = halfWidth;
        double bottom = -halfHeight;
        double top = halfHeight;
        double near = halfDepth;
        double far = -halfDepth;
        glBegin(GL_QUADS);
        glVertex3d(left, bottom, near); glVertex3d(right, bottom, near);
        glVertex3d(right, top, near); glVertex3d(left, top, near);
        glVertex3d(right, bottom, far); glVertex3d(left, bottom, far);
        glVertex3d(left, top, far); glVertex3d(right, top, far);
        glVertex3d(left, bottom, far); glVertex3d(left, bottom, near);
        glVertex3d(left, top, near); glVertex3d(left, top, far);
        glVertex3d(right, bottom, near); glVertex3d(right, bottom, far);
        glVertex3d(right, top, far); glVertex3d(right, top, near);
        glVertex3d(left, top, near); glVertex3d(right, top, near);
        glVertex3d(right, top, far); glVertex3d(left, top, far);
        glVertex3d(left, bottom, far); glVertex3d(right, bottom, far);
        glVertex3d(right, bottom, near); glVertex3d(left, bottom, near);
        glEnd();
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
                glColor4f(action.actionType() == BattlePresentationDirector.ActionType.HEAL ? 0.35f : 0.45f,
                        action.actionType() == BattlePresentationDirector.ActionType.HEAL ? 1.0f : 0.72f,
                        1f, 0.78f);
                glBegin(GL_QUADS);
                glVertex3d(-0.28, -0.28, 0); glVertex3d(0.28, -0.28, 0);
                glVertex3d(0.28, 0.28, 0); glVertex3d(-0.28, 0.28, 0);
                glEnd(); glPopMatrix();
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
        double amount = animationProgress(actor, actions);
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

    private static double animationProgress(BattleActor actor, List<BattlePresentationDirector.ActionSnapshot> actions) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.attacker() == actor || action.targets().stream().anyMatch(target -> target.target() == actor)) {
                return action.overallProgress();
            }
        }
        return (System.nanoTime() / 1_000_000_000.0) % 1.0;
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

    private LwjglSkinnedModel getSkinnedModel(CharacterModelDefinition definition) {
        if (definition == null || !definition.hasModel() || failedSkinnedModels.contains(definition)) return null;
        LwjglSkinnedModel cached = skinnedModels.get(definition); if (cached != null) return cached;
        try { LwjglSkinnedModel loaded = LwjglSkinnedModel.load(definition); skinnedModels.put(definition, loaded); return loaded; }
        catch (Exception exception) { failedSkinnedModels.add(definition); LOGGER.log(Level.WARNING, "Character model fallback: " + definition.modelPath(), exception); return null; }
    }
    private LwjglStaticModel getStaticModel(String path) {
        if (path == null || path.isBlank() || failedStaticModels.contains(path)) return null;
        LwjglStaticModel cached = staticModels.get(path); if (cached != null) return cached;
        try { LwjglStaticModel loaded = LwjglStaticModel.load(path); staticModels.put(path, loaded); return loaded; }
        catch (IOException exception) { failedStaticModels.add(path); return null; }
    }

    void shutdown() {
        for (MeshBuffers mesh : buffers.values()) { glDeleteBuffers(mesh.positionVbo()); glDeleteBuffers(mesh.uvVbo()); glDeleteBuffers(mesh.indexBuffer()); }
        buffers.clear(); skinnedModels.clear(); staticModels.clear(); firstPersonTransitions.clear();
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

    private record Position(double x, double y, double z) { }
    private record MeshBuffers(int positionVbo, int uvVbo, int indexBuffer) { }
}

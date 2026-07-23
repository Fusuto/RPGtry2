package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.main.battle.BattleActor;
import org.main.battle.BattleEncounter;
import org.main.battle.BattlePresentationDirector;
import org.main.content.CharacterModelDefinition;
import org.main.core.AetherGameRuntime;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.EquipmentViewModelProfile;
import org.main.engine.DungeonRenderContext;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final LwjglTextureCache textureCache;
    private final Map<CharacterModelDefinition, LwjglSkinnedModel> skinnedModels = new HashMap<>();
    private final Set<CharacterModelDefinition> failedSkinnedModels = new HashSet<>();
    private final Map<String, LwjglStaticModel> staticModels = new HashMap<>();
    private final Set<String> failedStaticModels = new HashSet<>();
    private final IdentityHashMap<LwjglSkinnedModel.SkinnedMesh, MeshBuffers> buffers = new IdentityHashMap<>();
    private final Map<BattleActor, Point> projectedActors = new IdentityHashMap<>();

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
        glTranslated(0, -0.58, cameraLunge);
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
            double scale = skinned.normalizedScaleForHeight(1.55) * definition.scale();
            glScaled(scale, scale, scale);
            drawSkinnedModel(skinned, skinned.skinNormalized(slot, progress));
        } else if (definition.hasModel()) {
            LwjglStaticModel model = getStaticModel(definition.modelPath());
            if (model != null) {
                double scale = model.normalizedScaleForHeight(1.55) * definition.scale();
                glScaled(scale, scale, scale);
                glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
                for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
            } else drawBillboard(actor.getImage(), 1.15, 1.55);
        } else {
            applyProceduralReaction(actor, actions);
            drawBillboard(actor.getImage(), 1.15, 1.55);
        }
        glPopMatrix();
    }

    private void drawSkinnedModel(LwjglSkinnedModel model, LwjglSkinnedModel.Frame frame) {
        for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
            LwjglSkinnedModel.SkinnedMesh mesh = model.meshes().get(meshIndex);
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

    private void renderEquipment(BattleActor player, List<BattlePresentationDirector.ActionSnapshot> actions) {
        if (player == null || player.getSourcePlayer() == null) return;
        InventorySystem.Inventory inventory = player.getSourcePlayer().getInventory();
        InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = inventory.getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        InventorySystem.Item chest = inventory.getEquippedItem(InventorySystem.EquipmentSlot.CHEST);
        boolean casting = actions.stream().anyMatch(action -> action.attacker() == player
                && (action.actionType() == BattlePresentationDirector.ActionType.SPELL
                || action.actionType() == BattlePresentationDirector.ActionType.HEAL));
        double swing = actionEnvelope(player, actions);
        double blockRaise = actions.stream().flatMap(action -> action.targets().stream())
                .filter(target -> target.target() == player
                        && target.reaction() == BattlePresentationDirector.Reaction.BLOCK)
                .findFirst().map(ignored -> 1.0).orElse(0.0);
        glClear(GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        if (chest != null && chest.hasFirstPersonModel()) {
            if (chest.getViewModelProfile().pairedHands()) {
                drawEquipmentModel(chest, 0.0, -0.48, -0.82, 0.52, 0, 0, 0, false);
            } else if (weapon != null && weapon.isTwoHanded()) {
                drawEquipmentModel(chest, -0.10, -0.43, -0.76, 0.38, 4, 12, 20, false);
                drawEquipmentModel(chest, 0.18, -0.34, -0.91, 0.38, -8, -12, -12, true);
            } else {
                drawEquipmentModel(chest, -0.34, -0.48, -0.82, 0.38, 0, 18, 15, false);
                drawEquipmentModel(chest, 0.34, -0.48, -0.82, 0.38, 0, -18, -15, true);
            }
        } else {
            drawBuiltInHand(-0.30, -0.48, -0.80);
            drawBuiltInHand(0.30, -0.48 + (weapon == null ? 0.10 * swing : 0),
                    -0.80 - (weapon == null ? 0.24 * swing : 0));
        }
        if (weapon != null && weapon.hasFirstPersonModel()) {
            double lower = casting ? -0.28 : 0.0;
            drawEquipmentModel(weapon, weapon.isTwoHanded() ? 0.10 : 0.38, -0.45 + lower, -0.86,
                    0.72, -16, 8, -28 - swing * 58, false);
        }
        if (shield != null && shield.hasFirstPersonModel() && (weapon == null || !weapon.isTwoHanded())) {
            drawEquipmentModel(shield, -0.43, casting ? -0.72 : -0.38 + blockRaise * 0.22,
                    -0.92 - blockRaise * 0.12, 0.58, -blockRaise * 12, 12, 8, false);
        }
    }

    private void drawEquipmentModel(InventorySystem.Item item, double x, double y, double z,
                                    double height, double rx, double ry, double rz, boolean mirror) {
        LwjglStaticModel model = getStaticModel(item.getFirstPersonModelPath());
        if (model == null) return;
        EquipmentViewModelProfile pose = item.getViewModelProfile();
        glPushMatrix();
        glTranslated(pose.positionX() + (x - 0.38), pose.positionY() + (y + 0.45), pose.positionZ() + (z + 0.86));
        glRotated(pose.rotationX() + (rx + 16), 1, 0, 0);
        glRotated(pose.rotationY() + (ry - 8), 0, 1, 0);
        glRotated(pose.rotationZ() + (rz + 28), pose.swingAxisX(), pose.swingAxisY(), pose.swingAxisZ());
        double scale = model.normalizedScaleForHeight(height / 0.72 * pose.normalizedHeight());
        glScaled(mirror ? -scale : scale, scale, scale);
        glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
        for (LwjglStaticModel.Mesh mesh : model.meshes()) drawStaticMesh(mesh);
        glPopMatrix();
    }

    private void drawBuiltInHand(double x, double y, double z) {
        glDisable(GL_TEXTURE_2D); glColor4f(0.67f, 0.45f, 0.30f, 1f); glPushMatrix(); glTranslated(x, y, z);
        glScaled(0.13, 0.18, 0.28); glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) { glVertex3d(side, -1, -1); glVertex3d(side, 1, -1); glVertex3d(side, 1, 1); glVertex3d(side, -1, 1); }
        glEnd(); glPopMatrix();
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
                return switch (action.phase()) {
                    case WINDUP -> action.progress() * 0.55;
                    case IMPACT -> 0.55 + action.progress() * 0.15;
                    case RECOVERY -> 0.70 + action.progress() * 0.30;
                };
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
            Point point = project(p.x(), p.y() + 1.7, p.z(), look, width, height);
            if (point != null) projectedActors.put(actor, point);
        }
    }

    private static Point project(double x, double y, double z, CameraLookState look, int width, int height) {
        double yaw = Math.toRadians(-look.yawOffsetDegrees()), pitch = Math.toRadians(look.pitchOffsetDegrees());
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
        buffers.clear(); skinnedModels.clear(); staticModels.clear();
    }

    private record Position(double x, double y, double z) { }
    private record MeshBuffers(int positionVbo, int uvVbo, int indexBuffer) { }
}

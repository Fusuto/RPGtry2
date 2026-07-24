package org.main.tools;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector4d;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.FirstPersonEquipmentRig;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;
import org.main.experimental.LwjglStaticModel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * First-person camera preview for equipment authoring. It deliberately uses
 * the same equipment-rig poses as the native battle renderer.
 */
final class EquipmentCombinationPreviewPanel extends JPanel {
    enum Pose { REST, ATTACK, BLOCK, CAST }

    private static final double FOV_DEGREES = 70.0;
    private final Supplier<String> pathSupplier;
    private final Supplier<EquipmentViewModelProfile> profileSupplier;
    private final Supplier<InventorySystem.ItemType> itemTypeSupplier;
    private final Supplier<Boolean> twoHandedSupplier;
    private final Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier;
    private final Supplier<WeaponType> weaponTypeSupplier;
    private final JComboBox<Pose> poseBox = new JComboBox<>(Pose.values());
    private final JButton playButton = new JButton("Play");
    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JCheckBox loopBox = new JCheckBox("Loop", true);
    private final Canvas canvas = new Canvas();
    private final JLabel status = new JLabel(" ");
    private final Timer animationTimer;
    private long lastAnimationTickNanos;
    private LwjglStaticModel model;
    private SkeletalPreview skeletalPreview;

    EquipmentCombinationPreviewPanel(
            Supplier<String> pathSupplier,
            Supplier<EquipmentViewModelProfile> profileSupplier,
            Supplier<InventorySystem.ItemType> itemTypeSupplier,
            Supplier<Boolean> twoHandedSupplier,
            Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier,
            Supplier<WeaponType> weaponTypeSupplier
    ) {
        super(new BorderLayout(4, 4));
        this.pathSupplier = pathSupplier;
        this.profileSupplier = profileSupplier;
        this.itemTypeSupplier = itemTypeSupplier;
        this.twoHandedSupplier = twoHandedSupplier;
        this.itemProfileSupplier = itemProfileSupplier;
        this.weaponTypeSupplier = weaponTypeSupplier;
        JButton load = new JButton("Load / Refresh");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.add(load);
        controls.add(new JLabel("Combat Pose"));
        controls.add(poseBox);
        controls.add(playButton);
        timeline.setPreferredSize(new Dimension(150, 24));
        controls.add(timeline);
        controls.add(loopBox);
        controls.add(status);
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        canvas.setPreferredSize(new Dimension(560, 315));
        animationTimer = new Timer(16, event -> advanceAnimation());
        animationTimer.setCoalesce(true);
        poseBox.addActionListener(event -> {
            stopAnimation();
            timeline.setValue(0);
            canvas.repaint();
        });
        playButton.addActionListener(event -> {
            if (animationTimer.isRunning()) {
                stopAnimation();
            } else {
                if (timeline.getValue() >= timeline.getMaximum()) {
                    timeline.setValue(0);
                }
                lastAnimationTickNanos = System.nanoTime();
                animationTimer.start();
                playButton.setText("Pause");
            }
        });
        timeline.addChangeListener(event -> canvas.repaint());
        load.addActionListener(event -> {
            load.setEnabled(false);
            status.setText("Loading model...");
            new SwingWorker<LoadedPreview, Void>() {
                @Override
                protected LoadedPreview doInBackground() throws Exception {
                    String path = pathSupplier.get();
                    LwjglStaticModel staticModel = path == null || path.isBlank()
                            ? null : LwjglStaticModel.load(path);
                    return new LoadedPreview(staticModel, loadSkeletalPreview());
                }

                @Override
                protected void done() {
                    load.setEnabled(true);
                    try {
                        LoadedPreview loaded = get();
                        model = loaded.staticModel();
                        skeletalPreview = loaded.skeletalPreview();
                        String diagnostic = skeletalPreview == null ? "" : skeletalPreview.diagnostic();
                        status.setText(model == null && skeletalPreview == null
                                ? "No model selected"
                                : diagnostic.isBlank() ? "Runtime camera framing"
                                : "Warning: " + shorten(diagnostic, 72));
                        status.setToolTipText(diagnostic.isBlank() ? null : diagnostic);
                    } catch (Exception exception) {
                        model = null;
                        skeletalPreview = null;
                        status.setText("Could not load model");
                    }
                    canvas.repaint();
                }
            }.execute();
        });
        SwingUtilities.invokeLater(() -> {
            String initialPath = pathSupplier.get();
            if (initialPath != null && !initialPath.isBlank()) {
                load.doClick();
            }
        });
    }

    void refreshPose() {
        canvas.repaint();
    }

    @Override
    public void removeNotify() {
        stopAnimation();
        super.removeNotify();
    }

    private void advanceAnimation() {
        long now = System.nanoTime();
        double elapsedSeconds = Math.max(
                0.0, (now - lastAnimationTickNanos) / 1_000_000_000.0);
        lastAnimationTickNanos = now;
        int advance = (int) Math.max(
                1, Math.round(elapsedSeconds / 1.12 * timeline.getMaximum()));
        int next = timeline.getValue() + advance;
        if (next >= timeline.getMaximum()) {
            if (loopBox.isSelected()) {
                next %= timeline.getMaximum();
            } else {
                next = timeline.getMaximum();
                stopAnimation();
            }
        }
        timeline.setValue(next);
    }

    private void stopAnimation() {
        animationTimer.stop();
        playButton.setText("Play");
    }

    private double animationAmount(Pose pose) {
        if (pose == null || pose == Pose.REST) return 0.0;
        double progress = timeline.getValue()
                / (double) Math.max(1, timeline.getMaximum());
        if (progress < 0.52) return smooth(progress / 0.52);
        if (progress < 0.61) return 1.0;
        return 1.0 - smooth((progress - 0.61) / 0.39);
    }

    private SkeletalPreview loadSkeletalPreview() {
        FirstPersonCombatLibrary.ItemProfile profile = itemProfileSupplier.get();
        FirstPersonCombatLibrary.Content content = FirstPersonCombatLibrary.loadFresh();
        if (profile == null || !content.rig().configured()) return null;
        FirstPersonCombatLibrary.WieldHand hand = profile.wieldHand();
        CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                content,
                weaponTypeSupplier.get(),
                profile,
                hand);
        try {
            LwjglSkinnedModel rig = LwjglSkinnedModel.load(definition);
            LwjglSkinnedModel left = loadAttachment(
                    content.rig().defaultLeftArmPath(), definition, rig.skeletonSignature());
            LwjglSkinnedModel right = loadAttachment(
                    content.rig().defaultRightArmPath(), definition, rig.skeletonSignature());
            LwjglSkinnedModel leftArmor = loadAttachment(
                    profile.leftArmorPath(), definition, rig.skeletonSignature());
            LwjglSkinnedModel rightArmor = loadAttachment(
                    profile.rightArmorPath(), definition, rig.skeletonSignature());
            List<String> diagnostics = new ArrayList<>(rig.diagnostics());
            validateAttachment(diagnostics, "Default left arm",
                    content.rig().defaultLeftArmPath(), left);
            validateAttachment(diagnostics, "Default right arm",
                    content.rig().defaultRightArmPath(), right);
            validateAttachment(diagnostics, "Left armor",
                    profile.leftArmorPath(), leftArmor);
            validateAttachment(diagnostics, "Right armor",
                    profile.rightArmorPath(), rightArmor);
            if (!rig.hasNode(content.rig().leftHandBone())) {
                diagnostics.add("Missing left hand bone " + content.rig().leftHandBone() + ".");
            }
            if (!rig.hasNode(content.rig().rightHandBone())) {
                diagnostics.add("Missing right hand bone " + content.rig().rightHandBone() + ".");
            }
            return new SkeletalPreview(content, profile, rig, left, right, leftArmor, rightArmor,
                    String.join(" ", diagnostics));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void validateAttachment(
            List<String> diagnostics,
            String label,
            String path,
            LwjglSkinnedModel model
    ) {
        if (path != null && !path.isBlank() && model == null) {
            diagnostics.add(label + " is missing or uses an incompatible skeleton.");
        }
    }

    private static String shorten(String value, int maximum) {
        if (value == null || value.length() <= maximum) return value == null ? "" : value;
        return value.substring(0, Math.max(1, maximum - 1)) + "…";
    }

    private LwjglSkinnedModel loadAttachment(
            String path,
            CharacterModelDefinition rigDefinition,
            String signature
    ) throws Exception {
        if (path == null || path.isBlank()) return null;
        CharacterModelDefinition definition = new CharacterModelDefinition(
                path, rigDefinition.rigId(), 1, 0, 0, rigDefinition.animationBindings());
        LwjglSkinnedModel attachment = LwjglSkinnedModel.load(definition);
        return signature.equals(attachment.skeletonSignature()) ? attachment : null;
    }

    private final class Canvas extends JPanel {
        private Canvas() {
            setBackground(new Color(116, 154, 210));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBattleBackdrop(g);

            EquipmentViewModelProfile profile = profileSupplier.get();
            InventorySystem.ItemType type = itemTypeSupplier.get();
            boolean twoHanded = Boolean.TRUE.equals(twoHandedSupplier.get());
            Pose mode = (Pose) poseBox.getSelectedItem();
            double animationAmount = animationAmount(mode);
            double attackDegrees = mode == Pose.ATTACK ? -58.0 * animationAmount : 0.0;

            if (skeletalPreview != null
                    && drawSkeletalAssembly(g, type, mode,
                    timeline.getValue() / (double) Math.max(1, timeline.getMaximum()))) {
                g.dispose();
                return;
            }
            drawReferenceHands(g, type, profile, twoHanded, attackDegrees);
            if (model == null) {
                g.setColor(new Color(245, 245, 245, 225));
                g.drawString("Load the GLB/FBX to preview the player's combat view.", 18, 28);
                g.dispose();
                return;
            }

            List<FirstPersonEquipmentRig.Pose> equipmentPoses = posesFor(
                    type, profile, twoHanded, mode, animationAmount);
            for (FirstPersonEquipmentRig.Pose equipmentPose : equipmentPoses) {
                double motion = FirstPersonEquipmentRig.followsPrimaryMotion(equipmentPose, twoHanded)
                        ? attackDegrees : 0.0;
                drawModel(g, model, equipmentPose, profile, motion);
            }
            drawGripMarker(g, profile, attackDegrees);
            g.dispose();
        }

        private boolean drawSkeletalAssembly(
                Graphics2D g,
                InventorySystem.ItemType type,
                Pose mode,
                double progress
        ) {
            CharacterModelDefinition.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> CharacterModelDefinition.AnimationSlot.IDLE;
                case ATTACK -> CharacterModelDefinition.AnimationSlot.ATTACK;
                case BLOCK -> CharacterModelDefinition.AnimationSlot.BLOCK;
                case CAST -> CharacterModelDefinition.AnimationSlot.CAST;
            };
            if (slot != CharacterModelDefinition.AnimationSlot.IDLE
                    && !skeletalPreview.rig().hasClip(slot)) return false;
            FirstPersonCombatLibrary.RigDefinition rig = skeletalPreview.content().rig();
            Matrix4d root = new Matrix4d()
                    .translate(rig.positionX(), rig.positionY(), rig.positionZ())
                    .rotateX(Math.toRadians(rig.rotationX()))
                    .rotateY(Math.toRadians(rig.rotationY()))
                    .rotateZ(Math.toRadians(rig.rotationZ()))
                    .scale(rig.scale());
            drawSkinnedAttachment(g, skeletalPreview.leftArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    skeletalPreview.profile().leftCoverage());
            drawSkinnedAttachment(g, skeletalPreview.rightArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    skeletalPreview.profile().rightCoverage());
            drawSkinnedAttachment(g, skeletalPreview.leftArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY);
            drawSkinnedAttachment(g, skeletalPreview.rightArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY);

            if (model != null && (type == InventorySystem.ItemType.WEAPON
                    || type == InventorySystem.ItemType.SHIELD)) {
                FirstPersonCombatLibrary.WieldHand hand =
                        type == InventorySystem.ItemType.SHIELD
                                ? skeletalPreview.profile().wieldHand().opposite()
                                : skeletalPreview.profile().wieldHand();
                Matrix4f socket = skeletalPreview.rig().nodeTransformNormalized(
                        slot, progress, rig.handBone(hand));
                if (socket != null) {
                    Matrix4d equipmentTransform = new Matrix4d(root)
                            .mul(new Matrix4d().set(socket));
                    EquipmentViewModelProfile pose = skeletalPreview.profile().socketTransform();
                    equipmentTransform
                            .translate(pose.positionX(), pose.positionY(), pose.positionZ())
                            .rotateX(Math.toRadians(pose.rotationX()))
                            .rotateY(Math.toRadians(pose.rotationY()))
                            .rotateZ(Math.toRadians(pose.rotationZ()))
                            .scale(pose.normalizedHeight());
                    drawStaticModel(g, model, equipmentTransform);
                    if (type == InventorySystem.ItemType.WEAPON) {
                        drawSecondaryGripGuide(g, equipmentTransform, root, slot, progress);
                    }
                }
            }
            drawSocketGuides(g, root, slot, progress);
            drawImpactGuide(g, mode, progress);
            g.setColor(new Color(255, 255, 255, 225));
            g.drawString("Skeletal rig preview - " + slot.name(), 18, 28);
            return true;
        }

        private void drawSocketGuides(
                Graphics2D g,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            drawSocketGuide(g, root, slot, progress,
                    skeletalPreview.content().rig().leftHandBone(), new Color(80, 210, 255, 230));
            drawSocketGuide(g, root, slot, progress,
                    skeletalPreview.content().rig().rightHandBone(), new Color(255, 120, 90, 230));
        }

        private void drawSocketGuide(
                Graphics2D g,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress,
                String bone,
                Color color
        ) {
            Matrix4f transform = skeletalPreview.rig().nodeTransformNormalized(slot, progress, bone);
            if (transform == null) return;
            Vector4d point = new Vector4d(0, 0, 0, 1);
            new Matrix4d(root).mul(new Matrix4d().set(transform)).transform(point);
            Point projected = projectWorld(point);
            if (projected == null) return;
            g.setColor(color);
            g.drawLine(projected.x - 5, projected.y, projected.x + 5, projected.y);
            g.drawLine(projected.x, projected.y - 5, projected.x, projected.y + 5);
        }

        private void drawImpactGuide(Graphics2D g, Pose mode, double progress) {
            FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> skeletalPreview.profile().wieldHand()
                        == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
                case ATTACK -> skeletalPreview.profile().wieldHand()
                        == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
                case BLOCK -> skeletalPreview.profile().wieldHand().opposite()
                        == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
                case CAST -> FirstPersonCombatLibrary.AnimationSlot.CAST;
            };
            FirstPersonCombatLibrary.ClipBinding binding =
                    skeletalPreview.content().resolveBinding(
                            weaponTypeSupplier.get(), skeletalPreview.profile(), slot);
            if (binding == null) return;
            int width = 150;
            int x = Math.max(12, getWidth() - width - 18);
            int y = 18;
            int marker = x + (int) Math.round(width * binding.impactFraction());
            int playhead = x + (int) Math.round(width * Math.max(0, Math.min(1, progress)));
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(x - 5, y - 11, width + 10, 31, 8, 8);
            g.setColor(new Color(230, 230, 230, 210));
            g.drawLine(x, y + 10, x + width, y + 10);
            g.setColor(new Color(255, 200, 50, 240));
            g.drawLine(marker, y + 3, marker, y + 15);
            g.setColor(new Color(100, 225, 255, 240));
            g.drawLine(playhead, y + 6, playhead, y + 14);
            g.setColor(Color.WHITE);
            g.drawString("Impact " + Math.round(binding.impactFraction() * 100) + "%", x, y);
        }

        private void drawSkinnedAttachment(
                Graphics2D g,
                LwjglSkinnedModel source,
                CharacterModelDefinition.AnimationSlot slot,
                double progress,
                Matrix4d transform,
                FirstPersonCombatLibrary.WieldHand side,
                FirstPersonCombatLibrary.ArmCoverage coverage
        ) {
            if (source == null || coverage == FirstPersonCombatLibrary.ArmCoverage.HIDE_FULL_ARM) return;
            LwjglSkinnedModel.Frame frame = source.skinNormalized(slot, progress);
            List<PaintTriangle> triangles = new ArrayList<>();
            for (int meshIndex = 0; meshIndex < source.meshes().size(); meshIndex++) {
                LwjglSkinnedModel.SkinnedMesh mesh = source.meshes().get(meshIndex);
                if (!regionVisible(mesh.name(), side, coverage)) continue;
                float[] positions = frame.meshPositions().get(meshIndex);
                appendTriangles(triangles, positions, mesh.texCoords(), mesh.indices(),
                        mesh.material().texture(), mesh.material().red(), mesh.material().green(),
                        mesh.material().blue(), mesh.material().alpha(), transform);
            }
            paintTriangles(g, triangles);
        }

        private boolean regionVisible(
                String meshName,
                FirstPersonCombatLibrary.WieldHand side,
                FirstPersonCombatLibrary.ArmCoverage coverage
        ) {
            if (coverage == null || coverage == FirstPersonCombatLibrary.ArmCoverage.OVERLAY) return true;
            String name = meshName == null ? "" : meshName.toLowerCase(java.util.Locale.ROOT);
            String suffix = side == FirstPersonCombatLibrary.WieldHand.LEFT ? "l" : "r";
            boolean correctSide = name.contains("." + suffix)
                    || name.contains("_" + suffix) || name.endsWith(suffix);
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

        private void drawStaticModel(Graphics2D g, LwjglStaticModel source, Matrix4d transform) {
            List<PaintTriangle> triangles = new ArrayList<>();
            for (LwjglStaticModel.Mesh mesh : source.meshes()) {
                appendTriangles(triangles, mesh.positions(), mesh.texCoords(), mesh.indices(),
                        mesh.texture(), mesh.red(), mesh.green(), mesh.blue(), mesh.alpha(), transform);
            }
            paintTriangles(g, triangles);
        }

        private void appendTriangles(
                List<PaintTriangle> triangles,
                float[] positions,
                float[] texCoords,
                int[] indices,
                BufferedImage texture,
                float red,
                float green,
                float blue,
                float alpha,
                Matrix4d transform
        ) {
            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                int a = indices[triangle], b = indices[triangle + 1], c = indices[triangle + 2];
                ScreenVertex va = transformVertex(positions, a, transform);
                ScreenVertex vb = transformVertex(positions, b, transform);
                ScreenVertex vc = transformVertex(positions, c, transform);
                if (va == null || vb == null || vc == null) continue;
                triangles.add(new PaintTriangle(
                        new Polygon(new int[] {va.x(), vb.x(), vc.x()},
                                new int[] {va.y(), vb.y(), vc.y()}, 3),
                        (va.z() + vb.z() + vc.z()) / 3.0,
                        triangleColor(texture, texCoords, a, b, c, red, green, blue, alpha)));
            }
        }

        private ScreenVertex transformVertex(float[] positions, int index, Matrix4d transform) {
            int offset = index * 3;
            Vector4d point = new Vector4d(
                    positions[offset], positions[offset + 1], positions[offset + 2], 1);
            transform.transform(point);
            if (point.z >= -0.05) return null;
            double focal = (getHeight() * 0.5) / Math.tan(Math.toRadians(FOV_DEGREES * 0.5));
            return new ScreenVertex(
                    (int) Math.round(getWidth() * 0.5 + point.x / -point.z * focal),
                    (int) Math.round(getHeight() * 0.5 - point.y / -point.z * focal),
                    point.z);
        }

        private void paintTriangles(Graphics2D g, List<PaintTriangle> triangles) {
            triangles.sort(Comparator.comparingDouble(PaintTriangle::depth));
            for (PaintTriangle triangle : triangles) {
                g.setColor(triangle.color());
                g.fillPolygon(triangle.polygon());
            }
        }

        private void drawSecondaryGripGuide(
                Graphics2D g,
                Matrix4d equipmentTransform,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            FirstPersonCombatLibrary.ItemProfile profile = skeletalPreview.profile();
            Vector4d secondary = new Vector4d(
                    profile.secondaryGripX(), profile.secondaryGripY(),
                    profile.secondaryGripZ(), 1);
            equipmentTransform.transform(secondary);
            String offHandBone = skeletalPreview.content().rig()
                    .handBone(profile.wieldHand().opposite());
            Matrix4f offHand = skeletalPreview.rig().nodeTransformNormalized(
                    slot, progress, offHandBone);
            if (offHand == null) return;
            Vector4d hand = new Vector4d(0, 0, 0, 1);
            new Matrix4d(root).mul(new Matrix4d().set(offHand)).transform(hand);
            Point gripPoint = projectWorld(secondary);
            Point handPoint = projectWorld(hand);
            if (gripPoint == null || handPoint == null) return;
            g.setColor(new Color(255, 205, 70, 220));
            g.drawOval(gripPoint.x - 5, gripPoint.y - 5, 10, 10);
            g.setColor(new Color(90, 220, 255, 220));
            g.drawOval(handPoint.x - 5, handPoint.y - 5, 10, 10);
            g.drawLine(gripPoint.x, gripPoint.y, handPoint.x, handPoint.y);
            double distance = Math.sqrt(
                    Math.pow(secondary.x - hand.x, 2)
                            + Math.pow(secondary.y - hand.y, 2)
                            + Math.pow(secondary.z - hand.z, 2));
            g.setColor(Color.WHITE);
            g.drawString(String.format(java.util.Locale.US,
                    "Secondary grip error: %.3f", distance), 18, 46);
        }

        private Point projectWorld(Vector4d point) {
            if (point.z >= -0.05) return null;
            double focal = (getHeight() * 0.5) / Math.tan(Math.toRadians(FOV_DEGREES * 0.5));
            return new Point(
                    (int) Math.round(getWidth() * 0.5 + point.x / -point.z * focal),
                    (int) Math.round(getHeight() * 0.5 - point.y / -point.z * focal));
        }

        private Color triangleColor(
                BufferedImage texture,
                float[] texCoords,
                int a,
                int b,
                int c,
                float red,
                float green,
                float blue,
                float alpha
        ) {
            if (texture == null || texCoords.length == 0) {
                return new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
            }
            double u = (texCoords[a * 2] + texCoords[b * 2] + texCoords[c * 2]) / 3.0;
            double v = (texCoords[a * 2 + 1] + texCoords[b * 2 + 1]
                    + texCoords[c * 2 + 1]) / 3.0;
            int tx = Math.max(0, Math.min(texture.getWidth() - 1,
                    (int) Math.floor(fract(u) * texture.getWidth())));
            int ty = Math.max(0, Math.min(texture.getHeight() - 1,
                    (int) Math.floor(fract(v) * texture.getHeight())));
            Color sampled = new Color(texture.getRGB(tx, ty), true);
            return new Color(
                    clamp(sampled.getRed() / 255f * red),
                    clamp(sampled.getGreen() / 255f * green),
                    clamp(sampled.getBlue() / 255f * blue),
                    clamp(sampled.getAlpha() / 255f * alpha));
        }

        private void drawBattleBackdrop(Graphics2D g) {
            int horizon = (int) (getHeight() * 0.56);
            GradientPaint ground = new GradientPaint(
                    0, horizon, new Color(55, 75, 67),
                    0, getHeight(), new Color(23, 30, 29));
            g.setPaint(ground);
            g.fillRect(0, horizon, getWidth(), getHeight() - horizon);
            g.setColor(new Color(255, 255, 255, 38));
            g.drawLine(0, horizon, getWidth(), horizon);
            g.setColor(new Color(0, 0, 0, 115));
            g.fillRoundRect(10, getHeight() - 30, 250, 20, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString("First-person combat viewport (70° FOV)", 18, getHeight() - 16);
        }

        private void drawReferenceHands(
                Graphics2D g,
                InventorySystem.ItemType type,
                EquipmentViewModelProfile profile,
                boolean twoHanded,
                double motionDegrees
        ) {
            if (type == InventorySystem.ItemType.CHEST_ARMOR) {
                drawReferenceWeapon(g, profile, twoHanded, motionDegrees);
                return;
            }
            for (FirstPersonEquipmentRig.Pose hand
                    : FirstPersonEquipmentRig.builtInHands(twoHanded, 0)) {
                double motion = FirstPersonEquipmentRig.followsPrimaryMotion(hand, twoHanded)
                        ? motionDegrees : 0.0;
                Point screen = projectPosePoint(hand.x(), hand.y(), hand.z(), profile, motion);
                if (screen == null) continue;
                int width = 34;
                int height = 70;
                g.setColor(new Color(149, 99, 69, 215));
                g.fillRoundRect(screen.x - width / 2, screen.y - height / 2,
                        width, height, 14, 14);
                g.setColor(new Color(255, 255, 255, 90));
                g.drawRoundRect(screen.x - width / 2, screen.y - height / 2,
                        width, height, 14, 14);
            }
        }

        private void drawReferenceWeapon(
                Graphics2D g,
                EquipmentViewModelProfile profile,
                boolean twoHanded,
                double motionDegrees
        ) {
            FirstPersonEquipmentRig.Pose weapon =
                    FirstPersonEquipmentRig.weapon(EquipmentViewModelProfile.defaults(), twoHanded, false);
            Point grip = projectPosePoint(weapon.x(), weapon.y(), weapon.z(), profile, motionDegrees);
            Point tip = projectPosePoint(weapon.x(), weapon.y() + 0.72, weapon.z(), profile, motionDegrees);
            if (grip == null || tip == null) return;
            g.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(105, 72, 47, 205));
            g.drawLine(grip.x, grip.y, tip.x, tip.y);
            g.setStroke(new BasicStroke(4));
            g.setColor(new Color(215, 220, 228, 215));
            g.drawLine(tip.x - 34, tip.y, tip.x + 34, tip.y);
        }

        private void drawGripMarker(
                Graphics2D g,
                EquipmentViewModelProfile profile,
                double motionDegrees
        ) {
            Point grip = projectPosePoint(
                    FirstPersonEquipmentRig.PRIMARY_HAND_X,
                    FirstPersonEquipmentRig.PRIMARY_HAND_Y,
                    FirstPersonEquipmentRig.PRIMARY_HAND_Z,
                    profile,
                    motionDegrees);
            if (grip == null) return;
            g.setColor(new Color(255, 211, 75, 210));
            g.drawOval(grip.x - 5, grip.y - 5, 10, 10);
            g.drawLine(grip.x - 8, grip.y, grip.x + 8, grip.y);
            g.drawLine(grip.x, grip.y - 8, grip.x, grip.y + 8);
        }

        private void drawModel(
                Graphics2D g,
                LwjglStaticModel source,
                FirstPersonEquipmentRig.Pose pose,
                EquipmentViewModelProfile motionProfile,
                double motionDegrees
        ) {
            Matrix4d transform = modelTransform(source, pose, motionProfile, motionDegrees);
            List<PaintTriangle> triangles = new ArrayList<>();
            for (LwjglStaticModel.Mesh mesh : source.meshes()) {
                for (int triangle = 0; triangle + 2 < mesh.indices().length; triangle += 3) {
                    int a = mesh.indices()[triangle];
                    int b = mesh.indices()[triangle + 1];
                    int c = mesh.indices()[triangle + 2];
                    ScreenVertex va = transformVertex(mesh, a, transform);
                    ScreenVertex vb = transformVertex(mesh, b, transform);
                    ScreenVertex vc = transformVertex(mesh, c, transform);
                    if (va == null || vb == null || vc == null) continue;
                    Color color = triangleColor(mesh, a, b, c);
                    triangles.add(new PaintTriangle(
                            new Polygon(
                                    new int[] {va.x(), vb.x(), vc.x()},
                                    new int[] {va.y(), vb.y(), vc.y()},
                                    3),
                            (va.z() + vb.z() + vc.z()) / 3.0,
                            color));
                }
            }
            triangles.sort(Comparator.comparingDouble(PaintTriangle::depth));
            for (PaintTriangle triangle : triangles) {
                g.setColor(triangle.color());
                g.fillPolygon(triangle.polygon());
            }
        }

        private Matrix4d modelTransform(
                LwjglStaticModel source,
                FirstPersonEquipmentRig.Pose pose,
                EquipmentViewModelProfile motionProfile,
                double motionDegrees
        ) {
            Matrix4d modelMatrix = new Matrix4d()
                    .translate(pose.x(), pose.y(), pose.z())
                    .rotateX(Math.toRadians(pose.rotationX()))
                    .rotateY(Math.toRadians(pose.rotationY()))
                    .rotateZ(Math.toRadians(pose.rotationZ()));
            double scale = source.normalizedScaleForHeight(pose.normalizedHeight());
            modelMatrix.scale(pose.mirrored() ? -scale : scale, scale, scale)
                    .translate(-source.centerX(), -source.baseY(), -source.centerZ());
            if (Math.abs(motionDegrees) < 0.0001) return modelMatrix;
            return primaryMotion(motionProfile, motionDegrees).mul(modelMatrix);
        }

        private ScreenVertex transformVertex(
                LwjglStaticModel.Mesh mesh,
                int index,
                Matrix4d transform
        ) {
            int offset = index * 3;
            Vector4d point = new Vector4d(
                    mesh.positions()[offset],
                    mesh.positions()[offset + 1],
                    mesh.positions()[offset + 2],
                    1.0);
            transform.transform(point);
            if (point.z >= -0.05) return null;
            double focal = (getHeight() * 0.5) / Math.tan(Math.toRadians(FOV_DEGREES * 0.5));
            return new ScreenVertex(
                    (int) Math.round(getWidth() * 0.5 + point.x / -point.z * focal),
                    (int) Math.round(getHeight() * 0.5 - point.y / -point.z * focal),
                    point.z);
        }

        private Point projectPosePoint(
                double x,
                double y,
                double z,
                EquipmentViewModelProfile profile,
                double motionDegrees
        ) {
            Vector4d point = new Vector4d(x, y, z, 1.0);
            if (Math.abs(motionDegrees) > 0.0001) {
                primaryMotion(profile, motionDegrees).transform(point);
            }
            if (point.z >= -0.05) return null;
            double focal = (getHeight() * 0.5) / Math.tan(Math.toRadians(FOV_DEGREES * 0.5));
            return new Point(
                    (int) Math.round(getWidth() * 0.5 + point.x / -point.z * focal),
                    (int) Math.round(getHeight() * 0.5 - point.y / -point.z * focal));
        }

        private Matrix4d primaryMotion(EquipmentViewModelProfile profile, double degrees) {
            return new Matrix4d()
                    .translate(FirstPersonEquipmentRig.PRIMARY_HAND_X,
                            FirstPersonEquipmentRig.PRIMARY_HAND_Y,
                            FirstPersonEquipmentRig.PRIMARY_HAND_Z)
                    .rotate(Math.toRadians(degrees),
                            profile.swingAxisX(), profile.swingAxisY(), profile.swingAxisZ())
                    .translate(-FirstPersonEquipmentRig.PRIMARY_HAND_X,
                            -FirstPersonEquipmentRig.PRIMARY_HAND_Y,
                            -FirstPersonEquipmentRig.PRIMARY_HAND_Z);
        }

        private Color triangleColor(LwjglStaticModel.Mesh mesh, int a, int b, int c) {
            BufferedImage texture = mesh.texture();
            if (texture == null || mesh.texCoords().length == 0) {
                return new Color(clamp(mesh.red()), clamp(mesh.green()),
                        clamp(mesh.blue()), clamp(mesh.alpha()));
            }
            double u = (mesh.texCoords()[a * 2] + mesh.texCoords()[b * 2] + mesh.texCoords()[c * 2]) / 3.0;
            double v = (mesh.texCoords()[a * 2 + 1] + mesh.texCoords()[b * 2 + 1]
                    + mesh.texCoords()[c * 2 + 1]) / 3.0;
            int tx = Math.max(0, Math.min(texture.getWidth() - 1,
                    (int) Math.floor(fract(u) * texture.getWidth())));
            int ty = Math.max(0, Math.min(texture.getHeight() - 1,
                    (int) Math.floor(fract(v) * texture.getHeight())));
            Color sampled = new Color(texture.getRGB(tx, ty), true);
            return new Color(
                    clamp(sampled.getRed() / 255f * mesh.red()),
                    clamp(sampled.getGreen() / 255f * mesh.green()),
                    clamp(sampled.getBlue() / 255f * mesh.blue()),
                    clamp(sampled.getAlpha() / 255f * mesh.alpha()));
        }
    }

    private static List<FirstPersonEquipmentRig.Pose> posesFor(
            InventorySystem.ItemType type,
            EquipmentViewModelProfile profile,
            boolean twoHanded,
            Pose mode,
            double animationAmount
    ) {
        if (type == InventorySystem.ItemType.CHEST_ARMOR) {
            return FirstPersonEquipmentRig.chestHands(profile, twoHanded);
        }
        if (type == InventorySystem.ItemType.SHIELD) {
            return List.of(FirstPersonEquipmentRig.shield(
                    profile,
                    mode == Pose.CAST ? animationAmount : 0.0,
                    mode == Pose.BLOCK ? animationAmount : 0.0));
        }
        return List.of(FirstPersonEquipmentRig.weapon(
                profile, twoHanded, mode == Pose.CAST ? animationAmount : 0.0));
    }

    private static double smooth(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }

    private record ScreenVertex(int x, int y, double z) {
    }

    private record PaintTriangle(Polygon polygon, double depth, Color color) {
    }

    private record LoadedPreview(
            LwjglStaticModel staticModel,
            SkeletalPreview skeletalPreview
    ) {
    }

    private record SkeletalPreview(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.ItemProfile profile,
            LwjglSkinnedModel rig,
            LwjglSkinnedModel leftArm,
            LwjglSkinnedModel rightArm,
            LwjglSkinnedModel leftArmor,
            LwjglSkinnedModel rightArmor,
            String diagnostic
    ) {
    }
}

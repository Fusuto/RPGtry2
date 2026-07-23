package org.main.tools;

import org.joml.Matrix4d;
import org.joml.Vector4d;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.FirstPersonEquipmentRig;
import org.main.core.InventorySystem;
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
    private final JComboBox<Pose> poseBox = new JComboBox<>(Pose.values());
    private final JButton playButton = new JButton("Play");
    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JCheckBox loopBox = new JCheckBox("Loop", true);
    private final Canvas canvas = new Canvas();
    private final JLabel status = new JLabel(" ");
    private final Timer animationTimer;
    private long lastAnimationTickNanos;
    private LwjglStaticModel model;

    EquipmentCombinationPreviewPanel(
            Supplier<String> pathSupplier,
            Supplier<EquipmentViewModelProfile> profileSupplier,
            Supplier<InventorySystem.ItemType> itemTypeSupplier,
            Supplier<Boolean> twoHandedSupplier
    ) {
        super(new BorderLayout(4, 4));
        this.pathSupplier = pathSupplier;
        this.profileSupplier = profileSupplier;
        this.itemTypeSupplier = itemTypeSupplier;
        this.twoHandedSupplier = twoHandedSupplier;
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
            new SwingWorker<LwjglStaticModel, Void>() {
                @Override
                protected LwjglStaticModel doInBackground() throws Exception {
                    String path = pathSupplier.get();
                    return path == null || path.isBlank() ? null : LwjglStaticModel.load(path);
                }

                @Override
                protected void done() {
                    load.setEnabled(true);
                    try {
                        model = get();
                        status.setText(model == null ? "No model selected" : "Runtime camera framing");
                    } catch (Exception exception) {
                        model = null;
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
}

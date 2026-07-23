package org.main.tools;

import org.main.core.EquipmentViewModelProfile;
import org.main.experimental.LwjglStaticModel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/** Pose preview for equipment authoring, including casting/block/two-hand visibility rules. */
final class EquipmentCombinationPreviewPanel extends JPanel {
    enum Pose { ONE_HANDED, TWO_HANDED, BLOCK, HIT, DODGE, CAST }

    private final Supplier<String> pathSupplier;
    private final Supplier<EquipmentViewModelProfile> profileSupplier;
    private final JComboBox<Pose> poseBox = new JComboBox<>(Pose.values());
    private final Canvas canvas = new Canvas();
    private LwjglStaticModel model;

    EquipmentCombinationPreviewPanel(Supplier<String> pathSupplier,
                                     Supplier<EquipmentViewModelProfile> profileSupplier) {
        super(new BorderLayout(4, 4));
        this.pathSupplier = pathSupplier; this.profileSupplier = profileSupplier;
        JButton load = new JButton("Load / Refresh");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.add(load); controls.add(new JLabel("Pose")); controls.add(poseBox);
        add(controls, BorderLayout.NORTH); add(canvas, BorderLayout.CENTER);
        canvas.setPreferredSize(new Dimension(500, 260));
        poseBox.addActionListener(event -> canvas.repaint());
        load.addActionListener(event -> {
            load.setEnabled(false);
            new SwingWorker<LwjglStaticModel, Void>() {
                @Override protected LwjglStaticModel doInBackground() throws Exception { return LwjglStaticModel.load(pathSupplier.get()); }
                @Override protected void done() {
                    load.setEnabled(true);
                    try { model = get(); } catch (Exception ignored) { model = null; }
                    canvas.repaint();
                }
            }.execute();
        });
    }

    private final class Canvas extends JPanel {
        private Canvas() { setBackground(new Color(28, 30, 36)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create();
            EquipmentViewModelProfile pose = profileSupplier.get();
            Pose mode = (Pose) poseBox.getSelectedItem();
            double swing = switch (mode == null ? Pose.ONE_HANDED : mode) {
                case HIT -> -18; case DODGE -> 15; case BLOCK -> 28; case CAST -> 45; default -> 0;
            };
            g.translate(getWidth() / 2.0 + pose.positionX() * 150, getHeight() * 0.75 - pose.positionY() * 90);
            g.rotate(Math.toRadians(pose.rotationZ() + swing));
            if (model == null) {
                g.setColor(new Color(185, 188, 195)); g.drawString("Load an equipment GLB/FBX", -95, -80);
                drawHands(g, mode); g.dispose(); return;
            }
            double scale = 120 * pose.normalizedHeight();
            for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                g.setColor(new Color(clamp(mesh.red()), clamp(mesh.green()), clamp(mesh.blue()), 210));
                for (int triangle = 0; triangle + 2 < mesh.indices().length; triangle += 3) {
                    Polygon polygon = new Polygon();
                    for (int corner = 0; corner < 3; corner++) {
                        int vertex = mesh.indices()[triangle + corner] * 3;
                        polygon.addPoint((int) Math.round((mesh.positions()[vertex] - model.centerX()) * scale),
                                (int) Math.round(-(mesh.positions()[vertex + 1] - model.baseY()) * scale));
                    }
                    g.fillPolygon(polygon);
                }
            }
            drawHands(g, mode); g.dispose();
        }

        private void drawHands(Graphics2D g, Pose mode) {
            g.setColor(new Color(150, 100, 70, 210));
            g.fillRoundRect(-58, 28, 34, 58, 14, 14);
            if (mode == Pose.TWO_HANDED || mode == Pose.CAST) g.fillRoundRect(24, 18, 34, 58, 14, 14);
            if (mode == Pose.BLOCK) {
                g.setColor(new Color(95, 105, 120, 220)); g.fillOval(-100, -70, 85, 100);
            }
        }
    }
    private static int clamp(float value) { return Math.max(0, Math.min(255, Math.round(value * 255))); }
}

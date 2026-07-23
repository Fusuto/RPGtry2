package org.main.tools;

import org.main.content.CharacterModelDefinition;
import org.main.experimental.LwjglSkinnedModel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Lightweight CPU-skinned preview that loads away from Swing's event thread. */
final class CharacterAnimationPreviewPanel extends JPanel {
    private final Supplier<CharacterModelDefinition> definitionSupplier;
    private final DoubleSupplier impactGetter;
    private final Consumer<Double> impactSetter;
    private final Supplier<String> clipNameGetter;
    private final Consumer<String> clipNameSetter;
    private final JComboBox<CharacterModelDefinition.AnimationSlot> slotBox =
            new JComboBox<>(CharacterModelDefinition.AnimationSlot.values());
    private final JSlider scrubber = new JSlider(0, 1000, 0);
    private final JSlider impact = new JSlider(5, 95, 55);
    private final JButton loadButton = new JButton("Load / Refresh");
    private final JToggleButton playButton = new JToggleButton("Play");
    private final JComboBox<String> clipBox = new JComboBox<>();
    private final JLabel status = new JLabel("Choose a model, then load the preview.");
    private final PreviewCanvas canvas = new PreviewCanvas();
    private LwjglSkinnedModel model;

    CharacterAnimationPreviewPanel(Supplier<CharacterModelDefinition> definitionSupplier,
                                   DoubleSupplier impactGetter, Consumer<Double> impactSetter,
                                   Supplier<String> clipNameGetter, Consumer<String> clipNameSetter) {
        super(new BorderLayout(4, 4));
        this.definitionSupplier = definitionSupplier;
        this.impactGetter = impactGetter;
        this.impactSetter = impactSetter;
        this.clipNameGetter = clipNameGetter;
        this.clipNameSetter = clipNameSetter;
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.add(loadButton); controls.add(slotBox); controls.add(new JLabel("Clip")); controls.add(clipBox); controls.add(playButton);
        controls.add(new JLabel("Time")); scrubber.setPreferredSize(new Dimension(150, 22)); controls.add(scrubber);
        controls.add(new JLabel("Impact")); impact.setPreferredSize(new Dimension(120, 22)); controls.add(impact);
        add(controls, BorderLayout.NORTH); add(canvas, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        canvas.setPreferredSize(new Dimension(520, 310));
        loadButton.addActionListener(event -> loadAsync());
        slotBox.addActionListener(event -> { syncImpactFromEditor(); syncClipFromEditor(); canvas.repaint(); });
        clipBox.addActionListener(event -> {
            Object selected = clipBox.getSelectedItem();
            if (selected != null) clipNameSetter.accept(selected.toString());
        });
        scrubber.addChangeListener(event -> canvas.repaint());
        impact.addChangeListener(event -> {
            if (!impact.getValueIsAdjusting()) impactSetter.accept(impact.getValue() / 100.0);
            canvas.repaint();
        });
        new Timer(33, event -> {
            if (playButton.isSelected()) scrubber.setValue((scrubber.getValue() + 12) % 1001);
        }).start();
    }

    CharacterModelDefinition.AnimationSlot selectedSlot() {
        CharacterModelDefinition.AnimationSlot selected =
                (CharacterModelDefinition.AnimationSlot) slotBox.getSelectedItem();
        return selected == null ? CharacterModelDefinition.AnimationSlot.IDLE : selected;
    }

    private void syncImpactFromEditor() {
        impact.setValue((int) Math.round(Math.max(0.05, Math.min(0.95, impactGetter.getAsDouble())) * 100));
    }

    private void loadAsync() {
        CharacterModelDefinition definition = definitionSupplier.get();
        loadButton.setEnabled(false); status.setText("Loading model and checking skeleton...");
        new SwingWorker<LwjglSkinnedModel, Void>() {
            @Override protected LwjglSkinnedModel doInBackground() throws Exception { return LwjglSkinnedModel.load(definition); }
            @Override protected void done() {
                loadButton.setEnabled(true);
                try {
                    model = get();
                    clipBox.removeAllItems();
                    clipBox.addItem("");
                    model.clipNames().stream().sorted().forEach(clipBox::addItem);
                    syncClipFromEditor();
                    List<String> diagnostics = model.diagnostics();
                    status.setText(diagnostics.isEmpty()
                            ? "Rig OK · signature " + model.skeletonSignature().substring(0, 12)
                            : String.join(" | ", diagnostics));
                } catch (Exception exception) {
                    model = null;
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Preview unavailable: " + cause.getMessage());
                }
                canvas.repaint();
            }
        }.execute();
    }

    private void syncClipFromEditor() {
        String value = clipNameGetter.get();
        clipBox.setSelectedItem(value == null ? "" : value);
    }

    private final class PreviewCanvas extends JPanel {
        private PreviewCanvas() { setBackground(new Color(28, 30, 36)); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (model == null) {
                g.setColor(new Color(175, 180, 190)); g.drawString("No loaded character model", 18, 28); g.dispose(); return;
            }
            LwjglSkinnedModel.Frame frame = model.skinNormalized(selectedSlot(), scrubber.getValue() / 1000.0);
            Bounds bounds = CharacterAnimationPreviewPanel.bounds(frame.meshPositions());
            double scale = Math.min((getWidth() - 40) / Math.max(0.001, bounds.maxX - bounds.minX),
                    (getHeight() - 40) / Math.max(0.001, bounds.maxY - bounds.minY));
            double centerX = (bounds.minX + bounds.maxX) / 2.0;
            for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
                var mesh = model.meshes().get(meshIndex); float[] positions = frame.meshPositions().get(meshIndex);
                var material = mesh.material();
                Color fill = new Color(clamp(material.red()), clamp(material.green()), clamp(material.blue()), 210);
                for (int triangle = 0; triangle + 2 < mesh.indices().length; triangle += 3) {
                    Polygon polygon = new Polygon();
                    for (int corner = 0; corner < 3; corner++) {
                        int vertex = mesh.indices()[triangle + corner] * 3;
                        double x = positions[vertex] - centerX, y = positions[vertex + 1], z = positions[vertex + 2];
                        double rotatedX = x * 0.86 + z * 0.50;
                        polygon.addPoint((int) Math.round(getWidth() / 2.0 + rotatedX * scale),
                                (int) Math.round(getHeight() - 20 - (y - bounds.minY) * scale));
                    }
                    g.setColor(fill); g.fillPolygon(polygon); g.setColor(new Color(0, 0, 0, 35)); g.drawPolygon(polygon);
                }
            }
            int markerX = (int) Math.round(impact.getValue() / 100.0 * getWidth());
            g.setColor(new Color(255, 194, 70, 210)); g.drawLine(markerX, 0, markerX, 16);
            g.dispose();
        }
    }

    private static Bounds bounds(List<float[]> meshes) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (float[] positions : meshes) for (int i = 0; i + 2 < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]); maxX = Math.max(maxX, positions[i]);
            minY = Math.min(minY, positions[i + 1]); maxY = Math.max(maxY, positions[i + 1]);
        }
        return Double.isFinite(minX) ? new Bounds(minX, minY, maxX, maxY) : new Bounds(-1, 0, 1, 2);
    }
    private static int clamp(float value) { return Math.max(0, Math.min(255, Math.round(value * 255))); }
    private record Bounds(double minX, double minY, double maxX, double maxY) { }
}

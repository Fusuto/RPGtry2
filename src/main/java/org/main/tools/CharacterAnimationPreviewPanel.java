package org.main.tools;

import org.main.content.CharacterModelDefinition;
import org.main.experimental.LwjglSkinnedModel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * CPU-skinned, game-resolution character preview. The software raster target
 * bounds work by visible preview pixels instead of issuing one Java2D draw and
 * outline operation for every source triangle.
 */
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
    private final JTextArea status = new JTextArea("Choose a model, then load the preview.", 2, 48);
    private final PreviewCanvas canvas = new PreviewCanvas();
    private final Timer playbackTimer;
    private LwjglSkinnedModel model;
    private boolean syncingClipBox;

    CharacterAnimationPreviewPanel(
            Supplier<CharacterModelDefinition> definitionSupplier,
            DoubleSupplier impactGetter,
            Consumer<Double> impactSetter,
            Supplier<String> clipNameGetter,
            Consumer<String> clipNameSetter
    ) {
        super(new BorderLayout(4, 4));
        this.definitionSupplier = definitionSupplier;
        this.impactGetter = impactGetter;
        this.impactSetter = impactSetter;
        this.clipNameGetter = clipNameGetter;
        this.clipNameSetter = clipNameSetter;

        clipBox.setPreferredSize(new Dimension(220, 24));
        clipBox.setMinimumSize(new Dimension(140, 24));
        clipBox.setMaximumSize(new Dimension(220, 24));
        clipBox.setPrototypeDisplayValue("Moderately_Long_Animation_Clip_Name");
        clipBox.setRenderer(new CompactClipRenderer());

        JPanel clipControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        clipControls.add(loadButton);
        clipControls.add(slotBox);
        clipControls.add(new JLabel("Embedded Clip"));
        clipControls.add(clipBox);

        JPanel playbackControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        playbackControls.add(playButton);
        playbackControls.add(new JLabel("Time"));
        scrubber.setPreferredSize(new Dimension(150, 22));
        playbackControls.add(scrubber);
        playbackControls.add(new JLabel("Impact"));
        impact.setPreferredSize(new Dimension(120, 22));
        playbackControls.add(impact);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(clipControls);
        controls.add(playbackControls);
        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        canvas.setPreferredSize(new Dimension(440, 280));

        loadButton.addActionListener(event -> loadAsync());
        slotBox.addActionListener(event -> {
            syncImpactFromEditor();
            syncClipFromEditor();
            canvas.invalidateFrame();
            canvas.repaint();
        });
        clipBox.addActionListener(event -> {
            if (syncingClipBox) {
                return;
            }
            Object selected = clipBox.getSelectedItem();
            if (selected != null) {
                clipNameSetter.accept(selected.toString());
                canvas.invalidateFrame();
                canvas.repaint();
            }
        });
        scrubber.addChangeListener(event -> {
            canvas.invalidateFrame();
            canvas.repaint();
        });
        impact.addChangeListener(event -> {
            if (!impact.getValueIsAdjusting()) {
                impactSetter.accept(impact.getValue() / 100.0);
            }
            canvas.repaint();
        });
        playbackTimer = new Timer(80, event -> {
            if (playButton.isSelected() && canvas.isShowing()) {
                scrubber.setValue((scrubber.getValue() + 30) % 1001);
            }
        });
        playbackTimer.start();
    }

    @Override
    public void removeNotify() {
        playbackTimer.stop();
        super.removeNotify();
    }

    CharacterModelDefinition.AnimationSlot selectedSlot() {
        CharacterModelDefinition.AnimationSlot selected =
                (CharacterModelDefinition.AnimationSlot) slotBox.getSelectedItem();
        return selected == null ? CharacterModelDefinition.AnimationSlot.IDLE : selected;
    }

    private void syncImpactFromEditor() {
        impact.setValue((int) Math.round(
                Math.max(0.05, Math.min(0.95, impactGetter.getAsDouble())) * 100));
    }

    private void loadAsync() {
        CharacterModelDefinition definition = definitionSupplier.get();
        loadButton.setEnabled(false);
        status.setText("Loading model and checking skeleton...");
        new SwingWorker<LwjglSkinnedModel, Void>() {
            @Override
            protected LwjglSkinnedModel doInBackground() throws Exception {
                return LwjglSkinnedModel.load(definition);
            }

            @Override
            protected void done() {
                loadButton.setEnabled(true);
                try {
                    model = get();
                    syncingClipBox = true;
                    clipBox.removeAllItems();
                    clipBox.addItem("");
                    model.clipNames().stream().sorted().forEach(clipBox::addItem);
                    syncClipFromEditor();
                    syncingClipBox = false;
                    List<String> diagnostics = model.diagnostics();
                    status.setText(diagnostics.isEmpty()
                            ? "Rig OK \u00b7 textured game-scale preview \u00b7 signature "
                                    + model.skeletonSignature().substring(0, 12)
                            : String.join(" | ", diagnostics));
                } catch (Exception exception) {
                    model = null;
                    syncingClipBox = false;
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Preview unavailable: " + cause.getMessage());
                }
                canvas.invalidateFrame();
                canvas.repaint();
            }
        }.execute();
    }

    private void syncClipFromEditor() {
        String value = clipNameGetter.get();
        syncingClipBox = true;
        clipBox.setSelectedItem(value == null ? "" : value);
        syncingClipBox = false;
    }

    private final class PreviewCanvas extends JPanel {
        private static final int RASTER_WIDTH = 300;
        private static final int RASTER_HEIGHT = 210;
        private static final int RASTER_GROUND_Y = 190;
        private static final double BATTLE_EYE_HEIGHT = 0.58;
        private static final double STANDARD_ACTOR_HEIGHT = 0.95;
        private static final double PIXELS_PER_WORLD_UNIT = 150.0;
        private BufferedImage cachedFrame;
        private int cachedProgress = -1;
        private CharacterModelDefinition.AnimationSlot cachedSlot;
        private String cachedClip = "";

        private PreviewCanvas() {
            setBackground(new Color(28, 30, 36));
        }

        private void invalidateFrame() {
            cachedFrame = null;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            if (model == null) {
                g.setColor(new Color(175, 180, 190));
                g.drawString("No loaded character model", 18, 28);
                g.dispose();
                return;
            }

            CharacterModelDefinition.AnimationSlot slot = selectedSlot();
            String clip = clipNameGetter.get() == null ? "" : clipNameGetter.get().trim();
            int progress = scrubber.getValue();
            if (cachedFrame == null
                    || cachedProgress != progress
                    || cachedSlot != slot
                    || !cachedClip.equals(clip)) {
                LwjglSkinnedModel.Frame frame =
                        model.skinEmbeddedClipNormalized(clip, slot, progress / 1000.0);
                cachedFrame = rasterize(frame);
                cachedProgress = progress;
                cachedSlot = slot;
                cachedClip = clip;
            }

            int availableWidth = Math.max(1, getWidth() - 24);
            int availableHeight = Math.max(1, getHeight() - 24);
            double scale = Math.min(
                    availableWidth / (double) RASTER_WIDTH,
                    availableHeight / (double) RASTER_HEIGHT);
            int drawWidth = Math.max(1, (int) Math.round(RASTER_WIDTH * scale));
            int drawHeight = Math.max(1, (int) Math.round(RASTER_HEIGHT * scale));
            int drawX = (getWidth() - drawWidth) / 2;
            int drawY = (getHeight() - drawHeight) / 2;
            drawBattleGuides(g, drawX, drawY, drawWidth, drawHeight);
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cachedFrame, drawX, drawY, drawWidth, drawHeight, null);

            int markerX = (int) Math.round(impact.getValue() / 100.0 * getWidth());
            g.setColor(new Color(255, 194, 70, 210));
            g.drawLine(markerX, 0, markerX, 16);
            g.dispose();
        }

        private void drawBattleGuides(Graphics2D g, int x, int y, int width, int height) {
            int groundY = y + (int) Math.round(RASTER_GROUND_Y / (double) RASTER_HEIGHT * height);
            int eyeY = groundY - (int) Math.round(
                    BATTLE_EYE_HEIGHT * PIXELS_PER_WORLD_UNIT / RASTER_HEIGHT * height);
            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{5.0f, 5.0f}, 0.0f));
            g.setColor(new Color(110, 185, 230, 125));
            g.drawLine(x, eyeY, x + width, eyeY);
            g.drawString("Camera eye level", x + 6, Math.max(y + 12, eyeY - 3));
            g.setStroke(oldStroke);
            g.setColor(new Color(120, 205, 135, 150));
            g.drawLine(x, groundY, x + width, groundY);
            g.drawString("Ground", x + 6, groundY - 3);
        }

        private BufferedImage rasterize(LwjglSkinnedModel.Frame frame) {
            BufferedImage image =
                    new BufferedImage(RASTER_WIDTH, RASTER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            float[] depth = new float[pixels.length];
            Arrays.fill(depth, Float.POSITIVE_INFINITY);

            double facing = Math.toRadians(model.definition().facingRotationDegrees() + 25.0);
            double cos = Math.cos(facing);
            double sin = Math.sin(facing);
            double modelScale = model.normalizedScaleForHeight(STANDARD_ACTOR_HEIGHT)
                    * model.definition().scale();
            double centerX = model.centerX();
            double centerZ = model.centerZ();
            double baseY = model.baseY();
            double verticalOffset = model.definition().verticalOffset();

            for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
                LwjglSkinnedModel.SkinnedMesh mesh = model.meshes().get(meshIndex);
                float[] positions = frame.meshPositions().get(meshIndex);
                int vertexCount = positions.length / 3;
                float[] screenX = new float[vertexCount];
                float[] screenY = new float[vertexCount];
                float[] screenDepth = new float[vertexCount];
                for (int vertex = 0; vertex < vertexCount; vertex++) {
                    int offset = vertex * 3;
                    double x = positions[offset] - centerX;
                    double y = positions[offset + 1] - baseY;
                    double z = positions[offset + 2] - centerZ;
                    double rotatedX = x * cos + z * sin;
                    double rotatedZ = -x * sin + z * cos;
                    screenX[vertex] = (float) (RASTER_WIDTH * 0.5
                            + rotatedX * modelScale * PIXELS_PER_WORLD_UNIT);
                    screenY[vertex] = (float) (RASTER_GROUND_Y
                            - (y * modelScale + verticalOffset) * PIXELS_PER_WORLD_UNIT);
                    screenDepth[vertex] = (float) (rotatedZ * modelScale);
                }
                rasterizeMesh(mesh, screenX, screenY, screenDepth, pixels, depth);
            }
            return image;
        }

        private void rasterizeMesh(
                LwjglSkinnedModel.SkinnedMesh mesh,
                float[] x,
                float[] y,
                float[] z,
                int[] pixels,
                float[] depth
        ) {
            int[] indices = mesh.indices();
            float[] uv = mesh.texCoords();
            BufferedImage texture = mesh.material().texture();
            int textureWidth = texture == null ? 0 : texture.getWidth();
            int textureHeight = texture == null ? 0 : texture.getHeight();
            int baseColor = rgba(
                    mesh.material().red(),
                    mesh.material().green(),
                    mesh.material().blue(),
                    mesh.material().alpha());

            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                int a = indices[triangle];
                int b = indices[triangle + 1];
                int c = indices[triangle + 2];
                if (a < 0 || b < 0 || c < 0
                        || a >= x.length || b >= x.length || c >= x.length) {
                    continue;
                }
                float area = edge(x[a], y[a], x[b], y[b], x[c], y[c]);
                if (Math.abs(area) < 0.01f) {
                    continue;
                }

                int minX = Math.max(0,
                        (int) Math.floor(Math.min(x[a], Math.min(x[b], x[c]))));
                int maxX = Math.min(RASTER_WIDTH - 1,
                        (int) Math.ceil(Math.max(x[a], Math.max(x[b], x[c]))));
                int minY = Math.max(0,
                        (int) Math.floor(Math.min(y[a], Math.min(y[b], y[c]))));
                int maxY = Math.min(RASTER_HEIGHT - 1,
                        (int) Math.ceil(Math.max(y[a], Math.max(y[b], y[c]))));
                if (minX > maxX || minY > maxY) {
                    continue;
                }

                for (int py = minY; py <= maxY; py++) {
                    for (int px = minX; px <= maxX; px++) {
                        float sampleX = px + 0.5f;
                        float sampleY = py + 0.5f;
                        float wa = edge(x[b], y[b], x[c], y[c], sampleX, sampleY) / area;
                        float wb = edge(x[c], y[c], x[a], y[a], sampleX, sampleY) / area;
                        float wc = 1.0f - wa - wb;
                        if (wa < -0.0001f || wb < -0.0001f || wc < -0.0001f) {
                            continue;
                        }
                        float pixelDepth = wa * z[a] + wb * z[b] + wc * z[c];
                        int pixelIndex = py * RASTER_WIDTH + px;
                        if (pixelDepth >= depth[pixelIndex]) {
                            continue;
                        }

                        int color = baseColor;
                        if (texture != null && uv.length >= x.length * 2) {
                            float u = wa * uv[a * 2] + wb * uv[b * 2] + wc * uv[c * 2];
                            float v = wa * uv[a * 2 + 1]
                                    + wb * uv[b * 2 + 1]
                                    + wc * uv[c * 2 + 1];
                            int tx = Math.max(0, Math.min(
                                    textureWidth - 1,
                                    Math.round(u * (textureWidth - 1))));
                            int ty = Math.max(0, Math.min(
                                    textureHeight - 1,
                                    Math.round((1.0f - v) * (textureHeight - 1))));
                            color = multiply(texture.getRGB(tx, ty), baseColor);
                        }
                        if ((color >>> 24) < 16) {
                            continue;
                        }
                        depth[pixelIndex] = pixelDepth;
                        pixels[pixelIndex] = color;
                    }
                }
            }
        }
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static int rgba(float red, float green, float blue, float alpha) {
        return clamp(alpha) << 24
                | clamp(red) << 16
                | clamp(green) << 8
                | clamp(blue);
    }

    private static int multiply(int texture, int factor) {
        int alpha = ((texture >>> 24) * (factor >>> 24 & 0xFF) + 127) / 255;
        int red = ((texture >>> 16 & 0xFF) * (factor >>> 16 & 0xFF) + 127) / 255;
        int green = ((texture >>> 8 & 0xFF) * (factor >>> 8 & 0xFF) + 127) / 255;
        int blue = ((texture & 0xFF) * (factor & 0xFF) + 127) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }

    private static final class CompactClipRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            String fullName = value == null ? "" : value.toString();
            label.setText(ellipsize(fullName, 34));
            label.setToolTipText(fullName.isBlank() ? null : fullName);
            return label;
        }

        private static String ellipsize(String value, int maximumCharacters) {
            if (value == null || value.length() <= maximumCharacters) {
                return value == null ? "" : value;
            }
            return value.substring(0, Math.max(1, maximumCharacters - 3)) + "...";
        }
    }

}

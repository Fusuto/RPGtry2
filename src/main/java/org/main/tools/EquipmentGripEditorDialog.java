package org.main.tools;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.main.content.FirstPersonCombatLibrary;
import org.main.core.EquipmentAutoPlacementService;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;
import org.main.experimental.LwjglStaticModel;
import org.main.experimental.StaticModelPlacementMetadataResolver;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Visual model-space grip editor backed by the normal combined equipment preview. */
final class EquipmentGripEditorDialog extends JDialog {
    private final String modelPath;
    private final InventorySystem.ItemType itemType;
    private final WeaponType weaponType;
    private final boolean twoHanded;
    private final FirstPersonCombatLibrary.Content content;
    private final FirstPersonCombatLibrary.ItemProfile baseProfile;
    private final GripCanvas gripCanvas;
    private final EquipmentCombinationPreviewPanel equippedPreview;
    private final JSpinner gripX = decimal(0), gripY = decimal(0), gripZ = decimal(0);
    private final JSpinner rotationX = degrees(0), rotationY = degrees(0), rotationZ = degrees(0);
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(0.8, 0.02, 20.0, 0.01));
    private final JSpinner secondaryX = decimal(0), secondaryY = decimal(0), secondaryZ = decimal(0);
    private final JRadioButton primaryMode = new JRadioButton("Primary grip", true);
    private final JRadioButton secondaryMode = new JRadioButton("Secondary grip");
    private StaticModelPlacementMetadataResolver.Metadata metadata;
    private EquipmentAutoPlacementService.PlacementProposal proposal;
    private FirstPersonCombatLibrary.ItemProfile liveProfile;
    private Result result;
    private boolean loadingControls;

    private EquipmentGripEditorDialog(
            Window owner,
            String modelPath,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.ItemProfile profile,
            EquipmentAutoPlacementService.PlacementProposal initial
    ) throws Exception {
        super(owner, "Edit Equipment Grip", ModalityType.APPLICATION_MODAL);
        this.modelPath = modelPath;
        this.itemType = itemType;
        this.weaponType = weaponType;
        this.twoHanded = twoHanded;
        this.content = content == null ? FirstPersonCombatLibrary.loadFresh() : content;
        this.baseProfile = profile;
        metadata = StaticModelPlacementMetadataResolver.resolve(modelPath);
        if (initial != null) {
            proposal = initial;
        } else if (profile != null) {
            Vector3d primary = EquipmentAutoPlacementService.gripForSocket(
                    metadata.bounds(), profile.socketTransform());
            Vector3d secondary = twoHanded && itemType == InventorySystem.ItemType.WEAPON
                    ? new Vector3d(profile.secondaryGripX(), profile.secondaryGripY(),
                    profile.secondaryGripZ()) : null;
            proposal = new EquipmentAutoPlacementService.PlacementProposal(
                    profile.socketTransform(), primary, secondary,
                    EquipmentAutoPlacementService.PlacementSource.GEOMETRY_GUESS,
                    EquipmentAutoPlacementService.Confidence.MEDIUM,
                    List.of("Existing authored placement."), false);
        } else {
            proposal = automaticProposal(false);
        }
        liveProfile = withPlacement(profile, proposal.socket(), proposal.secondaryGrip());
        gripCanvas = new GripCanvas();
        equippedPreview = new EquipmentCombinationPreviewPanel(
                () -> this.modelPath,
                () -> liveProfile.socketTransform(),
                () -> this.itemType,
                () -> this.twoHanded,
                () -> liveProfile,
                () -> this.weaponType,
                () -> this.content,
                () -> liveProfile.rigId());
        buildUi();
        loadProposal(proposal);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 650));
        setSize(1220, 760);
        setLocationRelativeTo(owner);
    }

    static Result show(
            Window owner,
            String modelPath,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.ItemProfile profile,
            EquipmentAutoPlacementService.PlacementProposal initial
    ) throws Exception {
        EquipmentGripEditorDialog dialog = new EquipmentGripEditorDialog(owner, modelPath,
                itemType, weaponType, twoHanded, content, profile, initial);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void buildUi() {
        setLayout(new BorderLayout(7, 7));
        JSplitPane previewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                gripCanvas, equippedPreview);
        previewSplit.setResizeWeight(0.44);
        add(previewSplit, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(BorderFactory.createTitledBorder("Grip transform"));
        ButtonGroup modes = new ButtonGroup();
        modes.add(primaryMode);
        modes.add(secondaryMode);
        secondaryMode.setEnabled(twoHanded && itemType == InventorySystem.ItemType.WEAPON);
        addRow(controls, 0, "Edit", compact(primaryMode, secondaryMode));
        addRow(controls, 1, "Grip X / Y / Z", compact(gripX, gripY, gripZ));
        addRow(controls, 2, "Rotation X / Y / Z", compact(rotationX, rotationY, rotationZ));
        addRow(controls, 3, "Model Height", height);
        addRow(controls, 4, "Secondary X / Y / Z", compact(secondaryX, secondaryY, secondaryZ));
        secondaryX.setEnabled(secondaryMode.isEnabled());
        secondaryY.setEnabled(secondaryMode.isEnabled());
        secondaryZ.setEnabled(secondaryMode.isEnabled());
        JButton reset = new JButton("Reset to Auto Placement");
        reset.addActionListener(event -> {
            try {
                proposal = automaticProposal(false);
                loadProposal(proposal);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(this, rootMessage(exception), "Edit Grip",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton flip = new JButton(itemType == InventorySystem.ItemType.SHIELD
                ? "Flip Shield Face" : "Flip Grip End");
        flip.addActionListener(event -> {
            try {
                proposal = automaticProposal(!proposal.flipped());
                loadProposal(proposal);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(this, rootMessage(exception), "Edit Grip",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel tools = compact(reset, flip);
        addRow(controls, 5, "Tools", tools);

        ChangeListener changed = event -> controlsChanged();
        for (JSpinner spinner : List.of(gripX, gripY, gripZ, rotationX, rotationY,
                rotationZ, height, secondaryX, secondaryY, secondaryZ)) {
            spinner.addChangeListener(changed);
        }
        primaryMode.addActionListener(event -> gripCanvas.repaint());
        secondaryMode.addActionListener(event -> gripCanvas.repaint());

        JButton apply = new JButton("Apply Grip");
        JButton cancel = new JButton("Cancel");
        apply.addActionListener(event -> {
            controlsChanged();
            result = new Result(liveProfile.socketTransform(),
                    liveProfile.secondaryGripX(), liveProfile.secondaryGripY(),
                    liveProfile.secondaryGripZ());
            dispose();
        });
        cancel.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(apply);
        buttons.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(apply);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        installNudgeBindings();
    }

    private EquipmentAutoPlacementService.PlacementProposal automaticProposal(boolean flipped)
            throws Exception {
        if (baseProfile != null) {
            return EquipmentAutoPlacementService.proposeForAttachment(
                    modelPath, itemType, weaponType, twoHanded, flipped, content, baseProfile);
        }
        return EquipmentAutoPlacementService.propose(
                metadata, itemType, weaponType, twoHanded, flipped);
    }

    private void installNudgeBindings() {
        bindNudge("LEFT", -1, 0, 0, false);
        bindNudge("RIGHT", 1, 0, 0, false);
        bindNudge("UP", 0, 1, 0, false);
        bindNudge("DOWN", 0, -1, 0, false);
        bindNudge("PAGE_UP", 0, 0, 1, false);
        bindNudge("PAGE_DOWN", 0, 0, -1, false);
        bindNudge("shift LEFT", -1, 0, 0, true);
        bindNudge("shift RIGHT", 1, 0, 0, true);
        bindNudge("shift UP", 0, 1, 0, true);
        bindNudge("shift DOWN", 0, -1, 0, true);
    }

    private void bindNudge(String stroke, int x, int y, int z, boolean coarse) {
        String key = "grip-nudge-" + stroke;
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), key);
        root.getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                double amount = coarse ? 0.05 : 0.01;
                JSpinner sx = selectedX(), sy = selectedY(), sz = selectedZ();
                sx.setValue(number(sx) + x * amount);
                sy.setValue(number(sy) + y * amount);
                sz.setValue(number(sz) + z * amount);
            }
        });
    }

    private void loadProposal(EquipmentAutoPlacementService.PlacementProposal value) {
        loadingControls = true;
        gripX.setValue(value.primaryGrip().x);
        gripY.setValue(value.primaryGrip().y);
        gripZ.setValue(value.primaryGrip().z);
        EquipmentViewModelProfile socket = value.socket();
        rotationX.setValue(socket.rotationX());
        rotationY.setValue(socket.rotationY());
        rotationZ.setValue(socket.rotationZ());
        height.setValue(socket.normalizedHeight());
        Vector3d secondary = value.secondaryGrip();
        secondaryX.setValue(secondary == null ? 0 : secondary.x);
        secondaryY.setValue(secondary == null ? 0 : secondary.y);
        secondaryZ.setValue(secondary == null ? 0 : secondary.z);
        loadingControls = false;
        controlsChanged();
    }

    private void controlsChanged() {
        if (loadingControls) return;
        Vector3d primary = new Vector3d(number(gripX), number(gripY), number(gripZ));
        Matrix3d rotation = new Matrix3d().rotateX(Math.toRadians(number(rotationX)))
                .rotateY(Math.toRadians(number(rotationY)))
                .rotateZ(Math.toRadians(number(rotationZ)));
        EquipmentViewModelProfile socket = EquipmentAutoPlacementService.socketFor(
                metadata.bounds(), primary, rotation, number(height));
        Vector3d secondary = secondaryMode.isEnabled()
                ? new Vector3d(number(secondaryX), number(secondaryY), number(secondaryZ)) : null;
        liveProfile = withPlacement(baseProfile, socket, secondary);
        gripCanvas.repaint();
        equippedPreview.refreshPose();
    }

    private FirstPersonCombatLibrary.ItemProfile withPlacement(
            FirstPersonCombatLibrary.ItemProfile base,
            EquipmentViewModelProfile socket,
            Vector3d secondary
    ) {
        FirstPersonCombatLibrary.ItemProfile safe = base == null
                ? new FirstPersonCombatLibrary.ItemProfile("preview", "",
                itemType == InventorySystem.ItemType.SHIELD
                        ? FirstPersonCombatLibrary.WieldHand.LEFT
                        : FirstPersonCombatLibrary.WieldHand.RIGHT,
                FirstPersonCombatLibrary.defaultSetId(weaponType), socket,
                0, 0, 0, "", "", FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, java.util.Map.of())
                : base;
        return new FirstPersonCombatLibrary.ItemProfile(safe.itemId(), safe.rigId(),
                safe.wieldHand(), safe.animationSetId(), socket,
                secondary == null ? 0 : secondary.x,
                secondary == null ? 0 : secondary.y,
                secondary == null ? 0 : secondary.z,
                safe.leftArmorPath(), safe.rightArmorPath(), safe.leftCoverage(),
                safe.rightCoverage(), safe.attachmentBone(), safe.overrides());
    }

    private JSpinner selectedX() { return secondaryMode.isSelected() ? secondaryX : gripX; }
    private JSpinner selectedY() { return secondaryMode.isSelected() ? secondaryY : gripY; }
    private JSpinner selectedZ() { return secondaryMode.isSelected() ? secondaryZ : gripZ; }

    private static JSpinner decimal(double value) {
        return new JSpinner(new SpinnerNumberModel(value, -100.0, 100.0, 0.01));
    }

    private static JSpinner degrees(double value) {
        return new JSpinner(new SpinnerNumberModel(value, -360.0, 360.0, 1.0));
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static JPanel compact(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component component : components) panel.add(component);
        return panel;
    }

    private static void addRow(JPanel panel, int row, String label, Component component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = row; left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(label), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = row; right.weightx = 1; right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(2, 4, 2, 4);
        panel.add(component, right);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record Result(EquipmentViewModelProfile socket, double secondaryX,
                  double secondaryY, double secondaryZ) {
    }

    private final class GripCanvas extends JPanel {
        private LwjglStaticModel model;
        private String error = "";
        private double yaw = -35, pitch = 20, zoom = 1;
        private Point dragOrigin;
        private boolean orbiting;
        private boolean movingGrip;
        private boolean rotatingGrip;

        GripCanvas() {
            setPreferredSize(new Dimension(480, 430));
            setBackground(new Color(24, 27, 32));
            setFocusable(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    dragOrigin = event.getPoint();
                    orbiting = SwingUtilities.isRightMouseButton(event);
                    movingGrip = SwingUtilities.isLeftMouseButton(event) && !event.isAltDown();
                    rotatingGrip = SwingUtilities.isLeftMouseButton(event) && event.isAltDown();
                    if (movingGrip) selectNearestVertex(event.getPoint());
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (dragOrigin == null) return;
                    int deltaX = event.getX() - dragOrigin.x;
                    int deltaY = event.getY() - dragOrigin.y;
                    if (orbiting) {
                        yaw += deltaX * 0.6;
                        pitch += deltaY * 0.6;
                    } else if (rotatingGrip) {
                        rotationY.setValue(number(rotationY) + deltaX * 0.5);
                        rotationX.setValue(number(rotationX) + deltaY * 0.5);
                    } else if (movingGrip && model != null) {
                        Projection projection = projection();
                        Vector4d movement = new Vector4d(
                                deltaX / projection.scale,
                                -deltaY / projection.scale, 0, 0);
                        new Matrix4d(projection.rotation).invert().transform(movement);
                        selectedX().setValue(number(selectedX()) + movement.x);
                        selectedY().setValue(number(selectedY()) + movement.y);
                        selectedZ().setValue(number(selectedZ()) + movement.z);
                    }
                    dragOrigin = event.getPoint();
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent event) {
                    dragOrigin = null;
                    orbiting = false;
                    movingGrip = false;
                    rotatingGrip = false;
                }

                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    zoom = Math.max(0.2, Math.min(6, zoom * Math.pow(1.1,
                            -event.getPreciseWheelRotation())));
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            new SwingWorker<LwjglStaticModel, Void>() {
                @Override protected LwjglStaticModel doInBackground() throws Exception {
                    return LwjglStaticModel.load(modelPath);
                }
                @Override protected void done() {
                    try { model = get(); }
                    catch (Exception exception) { error = rootMessage(exception); }
                    repaint();
                }
            }.execute();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (model == null) {
                    g.setColor(Color.WHITE);
                    g.drawString(error.isBlank() ? "Loading model..." : error, 18, 26);
                    return;
                }
                Projection projection = projection();
                List<PaintTriangle> triangles = triangles(projection);
                triangles.sort(Comparator.comparingDouble(PaintTriangle::depth));
                for (PaintTriangle triangle : triangles) {
                    g.setColor(triangle.color());
                    g.fillPolygon(triangle.polygon());
                }
                drawGrip(g, projection, new Vector3d(number(gripX), number(gripY), number(gripZ)),
                        primaryMode.isSelected() ? new Color(255, 210, 60) : new Color(240, 240, 240));
                if (secondaryMode.isEnabled()) {
                    drawGrip(g, projection, new Vector3d(number(secondaryX), number(secondaryY),
                                    number(secondaryZ)),
                            secondaryMode.isSelected() ? new Color(80, 210, 255) : new Color(180, 220, 235));
                }
                g.setColor(Color.WHITE);
                g.drawString("Left-click/drag: place grip   Alt-drag: rotate   Right-drag: orbit   Wheel: zoom", 12, 20);
                g.drawString("Arrow/Page keys: fine nudge   Shift: coarse nudge", 12, getHeight() - 12);
            } finally { g.dispose(); }
        }

        private void selectNearestVertex(Point click) {
            if (model == null) return;
            Projection projection = projection();
            double best = 18 * 18;
            Vector3d selected = null;
            for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                float[] positions = mesh.positions();
                for (int offset = 0; offset + 2 < positions.length; offset += 3) {
                    Vector3d vertex = new Vector3d(positions[offset], positions[offset + 1],
                            positions[offset + 2]);
                    Point projected = project(vertex, projection);
                    double distance = projected.distanceSq(click);
                    if (distance < best) { best = distance; selected = vertex; }
                }
            }
            if (selected == null) return;
            selectedX().setValue(selected.x);
            selectedY().setValue(selected.y);
            selectedZ().setValue(selected.z);
        }

        private Projection projection() {
            Matrix4d rotation = new Matrix4d().rotateX(Math.toRadians(pitch))
                    .rotateY(Math.toRadians(yaw));
            Vector3d center = metadata.bounds().center();
            double extent = 0.0001;
            for (Vector3d vertex : metadata.vertices()) {
                Vector4d point = new Vector4d(new Vector3d(vertex).sub(center), 1);
                rotation.transform(point);
                extent = Math.max(extent, Math.max(Math.abs(point.x), Math.abs(point.y)));
            }
            double scale = Math.min(getWidth(), getHeight()) * 0.39 * zoom / extent;
            return new Projection(rotation, center, scale, getWidth() * 0.5, getHeight() * 0.52);
        }

        private List<PaintTriangle> triangles(Projection projection) {
            List<PaintTriangle> result = new ArrayList<>();
            for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                int[] indices = mesh.indices();
                for (int index = 0; index + 2 < indices.length; index += 3) {
                    Projected a = projected(mesh.positions(), indices[index], projection);
                    Projected b = projected(mesh.positions(), indices[index + 1], projection);
                    Projected c = projected(mesh.positions(), indices[index + 2], projection);
                    Polygon polygon = new Polygon(new int[]{a.x, b.x, c.x},
                            new int[]{a.y, b.y, c.y}, 3);
                    result.add(new PaintTriangle(polygon, (a.z + b.z + c.z) / 3,
                            color(mesh)));
                }
            }
            return result;
        }

        private void drawGrip(Graphics2D g, Projection projection, Vector3d grip, Color color) {
            Point point = project(grip, projection);
            g.setStroke(new BasicStroke(2));
            g.setColor(color);
            g.drawOval(point.x - 7, point.y - 7, 14, 14);
            g.drawLine(point.x - 11, point.y, point.x + 11, point.y);
            g.drawLine(point.x, point.y - 11, point.x, point.y + 11);
            g.setColor(new Color(235, 80, 70)); g.drawLine(point.x, point.y, point.x + 24, point.y);
            g.setColor(new Color(90, 230, 110)); g.drawLine(point.x, point.y, point.x, point.y - 24);
            g.setColor(new Color(90, 150, 255)); g.drawLine(point.x, point.y, point.x - 17, point.y + 17);
        }

        private Point project(Vector3d vertex, Projection projection) {
            Projected value = projected(vertex, projection);
            return new Point(value.x, value.y);
        }

        private Projected projected(float[] positions, int index, Projection projection) {
            int offset = index * 3;
            return projected(new Vector3d(positions[offset], positions[offset + 1],
                    positions[offset + 2]), projection);
        }

        private Projected projected(Vector3d vertex, Projection projection) {
            Vector4d point = new Vector4d(new Vector3d(vertex).sub(projection.center), 1);
            projection.rotation.transform(point);
            return new Projected((int) Math.round(projection.centerX + point.x * projection.scale),
                    (int) Math.round(projection.centerY - point.y * projection.scale), point.z);
        }

        private Color color(LwjglStaticModel.Mesh mesh) {
            BufferedImage texture = mesh.texture();
            if (texture != null) {
                Color sampled = new Color(texture.getRGB(texture.getWidth() / 2,
                        texture.getHeight() / 2), true);
                return new Color(sampled.getRed(), sampled.getGreen(), sampled.getBlue(),
                        Math.max(40, sampled.getAlpha()));
            }
            return new Color(channel(mesh.red()), channel(mesh.green()), channel(mesh.blue()),
                    channel(mesh.alpha()));
        }

        private int channel(float value) { return Math.max(0, Math.min(255, Math.round(value * 255))); }

        private record Projection(Matrix4d rotation, Vector3d center, double scale,
                                  double centerX, double centerY) { }
        private record Projected(int x, int y, double z) { }
        private record PaintTriangle(Polygon polygon, double depth, Color color) { }
    }
}

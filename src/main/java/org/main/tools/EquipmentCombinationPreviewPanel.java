package org.main.tools;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * First-person camera preview for equipment authoring. It deliberately uses
 * the same equipment-rig poses as the native battle renderer.
 */
final class EquipmentCombinationPreviewPanel extends JPanel {
    enum Pose { REST, ATTACK, BLOCK, CAST, HIT, DODGE }

    private static final double DEFAULT_FOV_DEGREES = 70.0;
    private final Supplier<String> pathSupplier;
    private final Supplier<EquipmentViewModelProfile> profileSupplier;
    private final Supplier<InventorySystem.ItemType> itemTypeSupplier;
    private final Supplier<Boolean> twoHandedSupplier;
    private final Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier;
    private final Supplier<WeaponType> weaponTypeSupplier;
    private final Supplier<FirstPersonCombatLibrary.Content> contentSupplier;
    private final Supplier<String> rigIdSupplier;
    private final Supplier<FirstPersonCombatLibrary.CameraFraming> cameraFramingSupplier;
    private final JComboBox<Pose> poseBox = new JComboBox<>(Pose.values());
    private final JButton playButton = new JButton("Play");
    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JCheckBox loopBox = new JCheckBox("Loop", true);
    private final Canvas canvas = new Canvas();
    private final JLabel status = new JLabel(" ");
    private final JLabel modelScaleLabel = new JLabel("Model Height");
    private final JSpinner modelScaleSpinner = new JSpinner(
            new SpinnerNumberModel(1.0, 0.02, 20.0, 0.02));
    private final JButton reloadButton = new JButton("Reload Model");
    private final Timer animationTimer;
    private long lastAnimationTickNanos;
    private LwjglStaticModel model;
    private SkeletalPreview skeletalPreview;
    private Consumer<String> bonePickConsumer;
    private String selectedAttachmentBone = "";
    private Supplier<Double> modelScaleSupplier;
    private DoubleConsumer modelScaleConsumer;
    private boolean synchronizingModelScale;

    EquipmentCombinationPreviewPanel(
            Supplier<String> pathSupplier,
            Supplier<EquipmentViewModelProfile> profileSupplier,
            Supplier<InventorySystem.ItemType> itemTypeSupplier,
            Supplier<Boolean> twoHandedSupplier,
            Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier,
            Supplier<WeaponType> weaponTypeSupplier
    ) {
        this(pathSupplier, profileSupplier, itemTypeSupplier, twoHandedSupplier,
                itemProfileSupplier, weaponTypeSupplier,
                FirstPersonCombatLibrary::loadFresh,
                () -> {
                    FirstPersonCombatLibrary.ItemProfile profile = itemProfileSupplier.get();
                    return profile == null ? "" : profile.rigId();
                }, () -> null);
    }

    EquipmentCombinationPreviewPanel(
            Supplier<String> pathSupplier,
            Supplier<EquipmentViewModelProfile> profileSupplier,
            Supplier<InventorySystem.ItemType> itemTypeSupplier,
            Supplier<Boolean> twoHandedSupplier,
            Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier,
            Supplier<WeaponType> weaponTypeSupplier,
            Supplier<FirstPersonCombatLibrary.Content> contentSupplier,
            Supplier<String> rigIdSupplier
    ) {
        this(pathSupplier, profileSupplier, itemTypeSupplier, twoHandedSupplier,
                itemProfileSupplier, weaponTypeSupplier, contentSupplier, rigIdSupplier,
                () -> null);
    }

    EquipmentCombinationPreviewPanel(
            Supplier<String> pathSupplier,
            Supplier<EquipmentViewModelProfile> profileSupplier,
            Supplier<InventorySystem.ItemType> itemTypeSupplier,
            Supplier<Boolean> twoHandedSupplier,
            Supplier<FirstPersonCombatLibrary.ItemProfile> itemProfileSupplier,
            Supplier<WeaponType> weaponTypeSupplier,
            Supplier<FirstPersonCombatLibrary.Content> contentSupplier,
            Supplier<String> rigIdSupplier,
            Supplier<FirstPersonCombatLibrary.CameraFraming> cameraFramingSupplier
    ) {
        super(new BorderLayout(4, 4));
        this.pathSupplier = pathSupplier;
        this.profileSupplier = profileSupplier;
        this.itemTypeSupplier = itemTypeSupplier;
        this.twoHandedSupplier = twoHandedSupplier;
        this.itemProfileSupplier = itemProfileSupplier;
        this.weaponTypeSupplier = weaponTypeSupplier;
        this.contentSupplier = contentSupplier;
        this.rigIdSupplier = rigIdSupplier;
        this.cameraFramingSupplier = cameraFramingSupplier == null ? () -> null : cameraFramingSupplier;
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        controls.add(reloadButton);
        controls.add(new JLabel("Combat Pose"));
        controls.add(poseBox);
        controls.add(playButton);
        JButton restartButton = new JButton("|<");
        JButton previousFrameButton = new JButton("<");
        JButton nextFrameButton = new JButton(">");
        controls.add(restartButton);
        controls.add(previousFrameButton);
        controls.add(nextFrameButton);
        timeline.setPreferredSize(new Dimension(150, 24));
        controls.add(timeline);
        controls.add(loopBox);
        modelScaleSpinner.setPreferredSize(new Dimension(72, 24));
        modelScaleLabel.setVisible(false);
        modelScaleSpinner.setVisible(false);
        controls.add(modelScaleLabel);
        controls.add(modelScaleSpinner);
        controls.add(status);
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        canvas.setPreferredSize(new Dimension(560, 315));
        animationTimer = new Timer(16, event -> advanceAnimation());
        animationTimer.setCoalesce(true);
        modelScaleSpinner.addChangeListener(event -> {
            if (synchronizingModelScale || modelScaleConsumer == null) return;
            modelScaleConsumer.accept(((Number) modelScaleSpinner.getValue()).doubleValue());
            canvas.invalidateFrameCache();
            canvas.repaint();
        });
        poseBox.addActionListener(event -> {
            stopAnimation();
            timeline.setValue(0);
            canvas.invalidateSkinCache();
            canvas.invalidateFrameCache();
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
        restartButton.addActionListener(event -> {
            stopAnimation();
            timeline.setValue(0);
        });
        previousFrameButton.addActionListener(event -> {
            stopAnimation();
            timeline.setValue(Math.max(0, timeline.getValue() - 10));
        });
        nextFrameButton.addActionListener(event -> {
            stopAnimation();
            timeline.setValue(Math.min(timeline.getMaximum(), timeline.getValue() + 10));
        });
        timeline.addChangeListener(event -> {
            canvas.invalidateSkinCache();
            canvas.invalidateFrameCache();
            canvas.repaint();
        });
        reloadButton.addActionListener(event -> {
            reloadButton.setEnabled(false);
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
                    reloadButton.setEnabled(true);
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
                    canvas.invalidateSkinCache();
                    canvas.invalidateFrameCache();
                    canvas.repaint();
                }
            }.execute();
        });
        SwingUtilities.invokeLater(() -> {
            String initialPath = pathSupplier.get();
            FirstPersonCombatLibrary.Content initialContent = contentSupplier.get();
            if ((initialPath != null && !initialPath.isBlank())
                    || (initialContent != null && !initialContent.rigs().isEmpty())) {
                reloadButton.doClick();
            }
        });
    }

    void refreshPose() {
        canvas.invalidateFrameCache();
        canvas.repaint();
    }

    void bindModelScale(Supplier<Double> scaleSupplier, DoubleConsumer scaleConsumer) {
        modelScaleSupplier = scaleSupplier;
        modelScaleConsumer = scaleConsumer;
        boolean available = scaleSupplier != null && scaleConsumer != null;
        modelScaleLabel.setVisible(available);
        modelScaleSpinner.setVisible(available);
        syncModelScale();
    }

    void syncModelScale() {
        if (modelScaleSupplier == null) return;
        Double value = modelScaleSupplier.get();
        if (value == null || !Double.isFinite(value)) return;
        synchronizingModelScale = true;
        try {
            modelScaleSpinner.setValue(Math.max(0.02, Math.min(20.0, value)));
        } finally {
            synchronizingModelScale = false;
        }
    }

    void setSelectedAttachmentBone(String boneName) {
        selectedAttachmentBone = boneName == null ? "" : boneName.trim();
        canvas.invalidateFrameCache();
        canvas.repaint();
    }

    void pickAttachmentBone(Consumer<String> selectionConsumer) {
        bonePickConsumer = selectionConsumer;
        canvas.invalidateFrameCache();
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        status.setText("Pick a joint in the viewport (Esc cancels)");
        canvas.requestFocusInWindow();
        canvas.repaint();
    }

    List<LwjglSkinnedModel.SkeletonNodeMetadata> skeletonNodes() {
        return skeletalPreview == null ? List.of() : skeletalPreview.rig().skeletonNodes();
    }

    /**
     * Transform-only rig edits reuse the already imported meshes while
     * reading framing and bone selections from the current editor draft.
     * Path changes keep the loaded snapshot until an explicit reload has
     * completed so skeletons and attachments cannot become mismatched.
     */
    private FirstPersonCombatLibrary.RigDefinition liveRigDefinition() {
        FirstPersonCombatLibrary.RigDefinition loaded = skeletalPreview == null
                ? null : skeletalPreview.rigDefinition();
        FirstPersonCombatLibrary.Content content = contentSupplier.get();
        String requestedId = rigIdSupplier.get();
        FirstPersonCombatLibrary.RigDefinition current = content == null || requestedId == null
                ? null : content.rigs().get(FirstPersonCombatLibrary.normalizeId(requestedId));
        if (loaded == null) {
            return current == null ? FirstPersonCombatLibrary.emptyRig() : current;
        }
        if (current == null
                || !current.modelPath().equals(loaded.modelPath())
                || !current.defaultLeftArmPath().equals(loaded.defaultLeftArmPath())
                || !current.defaultRightArmPath().equals(loaded.defaultRightArmPath())) {
            return loaded;
        }
        return current;
    }

    void reloadPreview() {
        if (reloadButton.isEnabled()) reloadButton.doClick();
    }

    void selectAnimationSlot(FirstPersonCombatLibrary.AnimationSlot slot) {
        if (slot == null) return;
        Pose pose = switch (slot) {
            case IDLE_LEFT, IDLE_RIGHT -> Pose.REST;
            case ATTACK_LEFT, ATTACK_RIGHT -> Pose.ATTACK;
            case BLOCK_LEFT, BLOCK_RIGHT -> Pose.BLOCK;
            case CAST -> Pose.CAST;
            case HIT -> Pose.HIT;
            case DODGE -> Pose.DODGE;
        };
        poseBox.setSelectedItem(pose);
        timeline.setValue(0);
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
                1, Math.round(elapsedSeconds / previewDurationSeconds() * timeline.getMaximum()));
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

    private double previewDurationSeconds() {
        if (skeletalPreview == null) return 1.12;
        Pose pose = (Pose) poseBox.getSelectedItem();
        CharacterModelDefinition.AnimationSlot slot = characterSlot(pose);
        double duration = skeletalPreview.rig().clipNaturalDurationSeconds(slot);
        FirstPersonCombatLibrary.ClipBinding binding = liveBinding(pose);
        if (binding != null) {
            duration /= Math.max(0.0001, binding.playbackSpeed());
        }
        return duration > 0.0 ? duration : 1.12;
    }

    private FirstPersonCombatLibrary.ClipBinding liveBinding(Pose mode) {
        if (skeletalPreview == null) return null;
        FirstPersonCombatLibrary.ItemProfile profile = itemProfileSupplier.get();
        if (profile == null) profile = skeletalPreview.profile();
        FirstPersonCombatLibrary.WieldHand animationHand = animationHand(profile);
        FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
            case REST -> animationHand == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
            case ATTACK -> animationHand == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
            case BLOCK -> (itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                    ? profile.wieldHand() : profile.wieldHand().opposite())
                    == FirstPersonCombatLibrary.WieldHand.LEFT
                    ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                    : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
            case CAST -> FirstPersonCombatLibrary.AnimationSlot.CAST;
            case HIT -> FirstPersonCombatLibrary.AnimationSlot.HIT;
            case DODGE -> FirstPersonCombatLibrary.AnimationSlot.DODGE;
        };
        FirstPersonCombatLibrary.Content content = contentSupplier.get();
        if (content == null) content = skeletalPreview.content();
        return content.resolveBinding(
                itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                        ? WeaponType.NONE : weaponTypeSupplier.get(),
                profile, liveRigDefinition(), slot);
    }

    private static CharacterModelDefinition.AnimationSlot characterSlot(Pose pose) {
        return switch (pose == null ? Pose.REST : pose) {
            case REST -> CharacterModelDefinition.AnimationSlot.IDLE;
            case ATTACK -> CharacterModelDefinition.AnimationSlot.ATTACK;
            case BLOCK -> CharacterModelDefinition.AnimationSlot.BLOCK;
            case CAST -> CharacterModelDefinition.AnimationSlot.CAST;
            case HIT -> CharacterModelDefinition.AnimationSlot.HIT;
            case DODGE -> CharacterModelDefinition.AnimationSlot.DODGE;
        };
    }

    private SkeletalPreview loadSkeletalPreview() {
        FirstPersonCombatLibrary.ItemProfile profile = itemProfileSupplier.get();
        FirstPersonCombatLibrary.Content content = contentSupplier.get();
        if (content == null) return null;
        FirstPersonCombatLibrary.RigDefinition rigDefinition = content.rig(rigIdSupplier.get());
        if (profile == null || !rigDefinition.configured()) return null;
        boolean shield = itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD;
        FirstPersonCombatLibrary.WieldHand hand = animationHand(profile);
        CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                content, rigDefinition,
                shield ? WeaponType.NONE : weaponTypeSupplier.get(),
                profile,
                hand,
                profile,
                shield ? hand : hand.opposite(),
                shield ? WeaponType.NONE : weaponTypeSupplier.get());
        try {
            LwjglSkinnedModel rig = LwjglSkinnedModel.loadCached(definition);
            LwjglSkinnedModel left = loadAttachment(
                    rigDefinition.defaultLeftArmPath(), definition, rig.skeletonSignature());
            LwjglSkinnedModel right = loadAttachment(
                    rigDefinition.defaultRightArmPath(), definition, rig.skeletonSignature());
            if (left == null && !rigDefinition.leftVisibleMeshes().isEmpty()) left = rig;
            if (right == null && !rigDefinition.rightVisibleMeshes().isEmpty()) right = rig;
            LwjglSkinnedModel leftArmor = loadAttachment(
                    profile.leftArmorPath(), definition, rig.skeletonSignature());
            LwjglSkinnedModel rightArmor = loadAttachment(
                    profile.rightArmorPath(), definition, rig.skeletonSignature());
            List<String> diagnostics = new ArrayList<>(rig.diagnostics());
            validateAttachment(diagnostics, "Default left arm",
                    rigDefinition.defaultLeftArmPath(), left);
            validateAttachment(diagnostics, "Default right arm",
                    rigDefinition.defaultRightArmPath(), right);
            validateAttachment(diagnostics, "Left armor",
                    profile.leftArmorPath(), leftArmor);
            validateAttachment(diagnostics, "Right armor",
                    profile.rightArmorPath(), rightArmor);
            if (!rig.hasNode(rigDefinition.leftHandBone())) {
                diagnostics.add("Missing left hand bone " + rigDefinition.leftHandBone() + ".");
            }
            if (!rig.hasNode(rigDefinition.rightHandBone())) {
                diagnostics.add("Missing right hand bone " + rigDefinition.rightHandBone() + ".");
            }
            return new SkeletalPreview(content, rigDefinition, profile, rig, left, right, leftArmor, rightArmor,
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

    private FirstPersonCombatLibrary.WieldHand animationHand(
            FirstPersonCombatLibrary.ItemProfile profile
    ) {
        FirstPersonCombatLibrary.WieldHand equippedHand = profile == null
                ? FirstPersonCombatLibrary.WieldHand.RIGHT : profile.wieldHand();
        return itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                ? equippedHand.opposite() : equippedHand;
    }

    private LwjglSkinnedModel loadAttachment(
            String path,
            CharacterModelDefinition rigDefinition,
            String signature
    ) throws Exception {
        if (path == null || path.isBlank()) return null;
        CharacterModelDefinition definition = new CharacterModelDefinition(
                path, rigDefinition.rigId(), 1, 0, 0, rigDefinition.animationBindings());
        LwjglSkinnedModel attachment = LwjglSkinnedModel.loadCached(definition);
        return signature.equals(attachment.skeletonSignature()) ? attachment : null;
    }

    private final class Canvas extends JPanel {
        private static final double INSPECTION_SENSITIVITY = 0.65;
        private static final double MAX_INSPECTION_PITCH = 85.0;
        private static final double PLAYER_VIEWPORT_ASPECT = 16.0 / 9.0;
        private final Matrix4d inspectionView = new Matrix4d();
        private Point lastInspectionPoint;
        private boolean inspecting;
        private double inspectionYawDegrees;
        private double inspectionPitchDegrees;
        private String hoveredBone = "";
        private BufferedImage stableSkeletalFrame;
        private final Map<LwjglSkinnedModel, LwjglSkinnedModel.Frame> stableSkinFrames =
                new IdentityHashMap<>();
        private CharacterModelDefinition.AnimationSlot stableSkinSlot;
        private double stableSkinProgress = Double.NaN;
        private final int[] triangleX = new int[3];
        private final int[] triangleY = new int[3];

        private Canvas() {
            setBackground(new Color(116, 154, 210));
            setFocusable(true);
            setToolTipText("Right-drag to inspect; use the mouse wheel to scale the equipment (Shift for fine steps). Release right-drag to return.");
            MouseAdapter inspectionMouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event) && bonePickConsumer != null) {
                        String picked = nearestBone(event.getPoint(), 15.0);
                        if (!picked.isBlank()) {
                            Consumer<String> callback = bonePickConsumer;
                            bonePickConsumer = null;
                            selectedAttachmentBone = picked;
                            hoveredBone = "";
                            setCursor(Cursor.getDefaultCursor());
                            status.setText("Attached to " + picked);
                            invalidateFrameCache();
                            callback.accept(picked);
                            repaint();
                        }
                        event.consume();
                        return;
                    }
                    if (!SwingUtilities.isRightMouseButton(event)) return;
                    inspecting = true;
                    lastInspectionPoint = event.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    repaint();
                    event.consume();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (!inspecting || lastInspectionPoint == null) return;
                    int deltaX = event.getX() - lastInspectionPoint.x;
                    int deltaY = event.getY() - lastInspectionPoint.y;
                    inspectionYawDegrees = normalizeInspectionYaw(
                            inspectionYawDegrees + deltaX * INSPECTION_SENSITIVITY);
                    inspectionPitchDegrees = Math.max(-MAX_INSPECTION_PITCH,
                            Math.min(MAX_INSPECTION_PITCH,
                                    inspectionPitchDegrees + deltaY * INSPECTION_SENSITIVITY));
                    lastInspectionPoint = event.getPoint();
                    repaint();
                    event.consume();
                }


                @Override
                public void mouseMoved(MouseEvent event) {
                    if (bonePickConsumer == null) return;
                    hoveredBone = nearestBone(event.getPoint(), 15.0);
                    setToolTipText(hoveredBone.isBlank() ? "Click a visible joint"
                            : skeletonLabel(hoveredBone));
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (!inspecting) return;
                    resetInspectionView();
                    event.consume();
                }
            };
            addMouseListener(inspectionMouse);
            addMouseMotionListener(inspectionMouse);
            addMouseWheelListener(event -> {
                if (modelScaleConsumer == null || modelScaleSupplier == null) return;
                Double current = modelScaleSupplier.get();
                if (current == null || !Double.isFinite(current)) return;
                double step = event.isShiftDown() ? 0.01 : 0.05;
                double adjusted = Math.max(0.02, Math.min(20.0,
                        current - event.getPreciseWheelRotation() * step));
                modelScaleConsumer.accept(adjusted);
                syncModelScale();
                invalidateFrameCache();
                repaint();
                event.consume();
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelBonePick");
            getActionMap().put("cancelBonePick", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    bonePickConsumer = null;
                    hoveredBone = "";
                    setCursor(Cursor.getDefaultCursor());
                    status.setText("Bone picking cancelled");
                    invalidateFrameCache();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawBattleBackdrop(g);
            Rectangle viewport = playerViewport();
            g.clipRect(viewport.x, viewport.y, viewport.width, viewport.height);

            EquipmentViewModelProfile profile = profileSupplier.get();
            InventorySystem.ItemType type = itemTypeSupplier.get();
            boolean twoHanded = Boolean.TRUE.equals(twoHandedSupplier.get());
            Pose mode = (Pose) poseBox.getSelectedItem();
            double animationAmount = animationAmount(mode);
            double attackDegrees = mode == Pose.ATTACK ? -58.0 * animationAmount : 0.0;
            updateInspectionView(type, profile, twoHanded, mode, animationAmount);

            if (skeletalPreview != null) {
                boolean stable = !animationTimer.isRunning() && !inspecting
                        && bonePickConsumer == null;
                if (stable && stableSkeletalFrame != null
                        && stableSkeletalFrame.getWidth() == viewport.width
                        && stableSkeletalFrame.getHeight() == viewport.height) {
                    g.drawImage(stableSkeletalFrame, viewport.x, viewport.y, null);
                    g.dispose();
                    return;
                }
                double progress = timeline.getValue()
                        / (double) Math.max(1, timeline.getMaximum());
                if (stable && viewport.width > 0 && viewport.height > 0) {
                    BufferedImage cached = new BufferedImage(
                            viewport.width, viewport.height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D cachedGraphics = cached.createGraphics();
                    cachedGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    cachedGraphics.translate(-viewport.x, -viewport.y);
                    cachedGraphics.clipRect(viewport.x, viewport.y, viewport.width, viewport.height);
                    drawSkeletalAssembly(cachedGraphics, type, mode, progress);
                    cachedGraphics.dispose();
                    stableSkeletalFrame = cached;
                    g.drawImage(cached, viewport.x, viewport.y, null);
                    g.dispose();
                    return;
                }
                if (drawSkeletalAssembly(g, type, mode, progress)) {
                    g.dispose();
                    return;
                }
            }
            drawReferenceHands(g, type, profile, twoHanded, attackDegrees);
            if (model == null) {
                g.setColor(new Color(245, 245, 245, 225));
                g.drawString("Load the GLB/FBX to preview the player's combat view.",
                        viewport.x + 18, viewport.y + 28);
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

        private void resetInspectionView() {
            inspecting = false;
            lastInspectionPoint = null;
            inspectionYawDegrees = 0.0;
            inspectionPitchDegrees = 0.0;
            inspectionView.identity();
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }

        private void invalidateFrameCache() {
            stableSkeletalFrame = null;
        }

        private void invalidateSkinCache() {
            stableSkinFrames.clear();
            stableSkinSlot = null;
            stableSkinProgress = Double.NaN;
        }

        private Map<LwjglSkinnedModel, LwjglSkinnedModel.Frame> skinFrameCache(
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            if (animationTimer.isRunning()) return new IdentityHashMap<>();
            if (stableSkinSlot != slot || Math.abs(stableSkinProgress - progress) > 0.0000001) {
                stableSkinFrames.clear();
                stableSkinSlot = slot;
                stableSkinProgress = progress;
            }
            return stableSkinFrames;
        }

        private void updateInspectionView(
                InventorySystem.ItemType type,
                EquipmentViewModelProfile profile,
                boolean twoHanded,
                Pose mode,
                double animationAmount
        ) {
            inspectionView.identity();
            if (!inspecting) return;
            Vector4d pivot = inspectionPivot(type, profile, twoHanded, mode, animationAmount);
            inspectionView
                    .translate(pivot.x, pivot.y, pivot.z)
                    .rotateY(Math.toRadians(inspectionYawDegrees))
                    .rotateX(Math.toRadians(inspectionPitchDegrees))
                    .translate(-pivot.x, -pivot.y, -pivot.z);
        }

        private Vector4d inspectionPivot(
                InventorySystem.ItemType type,
                EquipmentViewModelProfile profile,
                boolean twoHanded,
                Pose mode,
                double animationAmount
        ) {
            if (skeletalPreview != null) {
                FirstPersonCombatLibrary.RigDefinition rig = liveRigDefinition();
                LwjglSkinnedModel source = skeletalPreview.rig();
                CharacterModelDefinition.AnimationSlot slot = characterSlot(mode);
                if (!source.hasClip(slot)) slot = CharacterModelDefinition.AnimationSlot.IDLE;
                double progress = timeline.getValue()
                        / (double) Math.max(1, timeline.getMaximum());
                Matrix4d root = skeletalRoot(
                        rig, source, slot, progress, cameraFraming(mode));
                Vector4d pivot = new Vector4d(
                        source.centerX(), modelCenterY(source), source.centerZ(), 1.0);
                root.transform(pivot);
                return pivot;
            }
            if (model != null) {
                List<FirstPersonEquipmentRig.Pose> poses = posesFor(
                        type, profile, twoHanded, mode, animationAmount);
                if (!poses.isEmpty()) {
                    FirstPersonEquipmentRig.Pose pose = poses.get(0);
                    double motion = FirstPersonEquipmentRig.followsPrimaryMotion(pose, twoHanded)
                            && mode == Pose.ATTACK ? -58.0 * animationAmount : 0.0;
                    Vector4d pivot = new Vector4d(
                            model.centerX(), modelCenterY(model), model.centerZ(), 1.0);
                    modelTransform(model, pose, profile, motion).transform(pivot);
                    return pivot;
                }
            }
            return new Vector4d(0.0, -0.15, -0.9, 1.0);
        }

        private double modelCenterY(LwjglSkinnedModel source) {
            double modelHeight = 1.0 / Math.max(0.0001, source.normalizedScaleForHeight(1.0));
            return source.baseY() + modelHeight * 0.5;
        }

        private double modelCenterY(LwjglStaticModel source) {
            double modelHeight = 1.0 / Math.max(0.0001, source.normalizedScaleForHeight(1.0));
            return source.baseY() + modelHeight * 0.5;
        }

        private boolean drawSkeletalAssembly(
                Graphics2D g,
                InventorySystem.ItemType type,
                Pose mode,
                double progress
        ) {
            CharacterModelDefinition.AnimationSlot slot = characterSlot(mode);
            if (!skeletalPreview.rig().hasClip(slot)) {
                slot = CharacterModelDefinition.AnimationSlot.IDLE;
            }
            FirstPersonCombatLibrary.RigDefinition rig = liveRigDefinition();
            FirstPersonCombatLibrary.ItemProfile profile = liveProfile();
            Matrix4d root = skeletalRoot(
                    rig, skeletalPreview.rig(), slot, progress, cameraFraming(mode));
            List<PaintTriangle> sceneTriangles = new ArrayList<>();
            Map<LwjglSkinnedModel, LwjglSkinnedModel.Frame> frameCache =
                    skinFrameCache(slot, progress);
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.leftArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    profile.leftCoverage(), rig.leftVisibleMeshes(), frameCache);
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.rightArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    profile.rightCoverage(), rig.rightVisibleMeshes(), frameCache);
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.leftArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of(), frameCache);
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.rightArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of(), frameCache);

            Matrix4d equipmentTransformForGuide = null;
            Matrix4f selectedAttachmentTransform = null;
            if (model != null && (type == InventorySystem.ItemType.WEAPON
                    || type == InventorySystem.ItemType.SHIELD)) {
                FirstPersonCombatLibrary.WieldHand hand =
                        profile.wieldHand();
                String attachmentBone = FirstPersonAnimationRuntime.resolveAttachmentBone(
                        rig, profile, skeletalPreview.rig());
                Matrix4f socket = socketTransformWithoutScale(
                        skeletalPreview.rig().nodeTransformNormalized(
                                slot, progress, attachmentBone));
                selectedAttachmentTransform = socket;
                if (socket != null) {
                    Matrix4d equipmentTransform = new Matrix4d(root)
                            .mul(new Matrix4d().set(socket));
                    EquipmentViewModelProfile pose = profile.socketTransform();
                    equipmentTransform
                            .translate(pose.positionX(), pose.positionY(), pose.positionZ())
                            .rotateX(Math.toRadians(pose.rotationX()))
                            .rotateY(Math.toRadians(pose.rotationY()))
                            .rotateZ(Math.toRadians(pose.rotationZ()))
                            .scale(model.normalizedScaleForHeight(pose.normalizedHeight()))
                            .translate(-model.centerX(), -model.baseY(), -model.centerZ());
                    appendStaticModel(sceneTriangles, model, equipmentTransform);
                    equipmentTransformForGuide = equipmentTransform;
                }
            }
            paintTriangles(g, sceneTriangles);
            if (type == InventorySystem.ItemType.WEAPON
                    && equipmentTransformForGuide != null) {
                drawSecondaryGripGuide(g, equipmentTransformForGuide, root, slot, progress);
            }
            drawSocketGuides(g, root, slot, progress);
            drawSkeletonPicker(g, root, slot, progress, selectedAttachmentTransform);
            drawImpactGuide(g, mode, progress);
            g.setColor(new Color(255, 255, 255, 225));
            Rectangle viewport = playerViewport();
            g.drawString("Skeletal rig preview - " + slot.name(),
                    viewport.x + 18, viewport.y + 28);
            return true;
        }

        private void drawSkeletonPicker(
                Graphics2D g,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress,
                Matrix4f selectedTransform
        ) {
            if (bonePickConsumer == null && selectedAttachmentBone.isBlank()) return;
            if (bonePickConsumer == null) {
                Matrix4f transform = selectedTransform != null ? selectedTransform
                        : socketTransformWithoutScale(skeletalPreview.rig().nodeTransformNormalized(
                        slot, progress, selectedAttachmentBone));
                if (transform != null) drawSelectedBoneAxes(g, root, transform);
                return;
            }
            Map<String, Point> points = projectedSkeleton(root, slot, progress);
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(130, 215, 255, 115));
            for (LwjglSkinnedModel.SkeletonNodeMetadata node : skeletalPreview.rig().skeletonNodes()) {
                Point child = points.get(node.name());
                Point parent = points.get(node.parentName());
                if (child != null && parent != null) g.drawLine(parent.x, parent.y, child.x, child.y);
            }
            for (LwjglSkinnedModel.SkeletonNodeMetadata node : skeletalPreview.rig().skeletonNodes()) {
                if (bonePickConsumer == null && !node.name().equalsIgnoreCase(selectedAttachmentBone)) continue;
                Point point = points.get(node.name());
                if (point == null) continue;
                boolean selected = node.name().equalsIgnoreCase(selectedAttachmentBone);
                boolean hover = node.name().equalsIgnoreCase(hoveredBone);
                int radius = selected || hover ? 6 : node.weightedBone() ? 4 : 3;
                g.setColor(selected ? new Color(255, 210, 60, 245)
                        : hover ? Color.WHITE
                        : node.weightedBone() || node.animatedNode()
                        ? new Color(80, 205, 255, 215) : new Color(190, 190, 205, 170));
                g.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
                if (selected) {
                    Matrix4f transform = skeletalPreview.rig().nodeTransformNormalized(
                            slot, progress, node.name());
                    if (transform != null) drawSelectedBoneAxes(g, root,
                            socketTransformWithoutScale(transform));
                }
            }
            if (!hoveredBone.isBlank()) {
                Point point = points.get(hoveredBone);
                if (point != null) {
                    g.setColor(new Color(20, 20, 24, 220));
                    String label = skeletonLabel(hoveredBone);
                    int width = g.getFontMetrics().stringWidth(label) + 10;
                    g.fillRoundRect(point.x + 9, point.y - 22, width, 20, 6, 6);
                    g.setColor(Color.WHITE);
                    g.drawString(label, point.x + 14, point.y - 8);
                }
            }
        }

        private void drawSelectedBoneAxes(Graphics2D g, Matrix4d root, Matrix4f node) {
            Matrix4d transform = new Matrix4d(root).mul(new Matrix4d().set(node));
            Point origin = projectTransformed(transform, 0, 0, 0);
            Point x = projectTransformed(transform, 0.09, 0, 0);
            Point y = projectTransformed(transform, 0, 0.09, 0);
            Point z = projectTransformed(transform, 0, 0, 0.09);
            if (origin == null) return;
            if (x != null) { g.setColor(Color.RED); g.drawLine(origin.x, origin.y, x.x, x.y); }
            if (y != null) { g.setColor(Color.GREEN); g.drawLine(origin.x, origin.y, y.x, y.y); }
            if (z != null) { g.setColor(Color.CYAN); g.drawLine(origin.x, origin.y, z.x, z.y); }
        }

        private Point projectTransformed(Matrix4d transform, double x, double y, double z) {
            Vector4d point = new Vector4d(x, y, z, 1);
            transform.transform(point);
            return projectWorld(point);
        }

        private Map<String, Point> projectedSkeleton(
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            Map<String, Point> result = new HashMap<>();
            for (LwjglSkinnedModel.SkeletonNodePose node : skeletalPreview.rig()
                    .skeletonPose(slot, progress)) {
                Vector4d point = new Vector4d(0, 0, 0, 1);
                new Matrix4d(root).mul(new Matrix4d().set(node.currentTransform())).transform(point);
                Point projected = projectWorld(point);
                if (projected != null) result.put(node.metadata().name(), projected);
            }
            return result;
        }

        private String nearestBone(Point mouse, double maximumDistance) {
            if (skeletalPreview == null) return "";
            Pose mode = (Pose) poseBox.getSelectedItem();
            CharacterModelDefinition.AnimationSlot slot = characterSlot(mode);
            if (!skeletalPreview.rig().hasClip(slot)) slot = CharacterModelDefinition.AnimationSlot.IDLE;
            double progress = timeline.getValue() / (double) Math.max(1, timeline.getMaximum());
            Matrix4d root = skeletalRoot(liveRigDefinition(), skeletalPreview.rig(), slot,
                    progress, cameraFraming(mode));
            Map<String, Point> points = projectedSkeleton(root, slot, progress);
            String nearest = "";
            double best = maximumDistance * maximumDistance;
            for (LwjglSkinnedModel.SkeletonNodeMetadata node : skeletalPreview.rig().skeletonNodes()) {
                Point candidate = points.get(node.name());
                if (candidate == null) continue;
                double distance = candidate.distanceSq(mouse);
                if (distance <= best) { best = distance; nearest = node.name(); }
            }
            return nearest;
        }

        private String skeletonLabel(String boneName) {
            if (skeletalPreview == null) return boneName;
            return skeletalPreview.rig().skeletonNodes().stream()
                    .filter(node -> node.name().equalsIgnoreCase(boneName))
                    .map(LwjglSkinnedModel.SkeletonNodeMetadata::displayLabel)
                    .findFirst().orElse(boneName);
        }

        private Matrix4d skeletalRoot(
                FirstPersonCombatLibrary.RigDefinition rig,
                LwjglSkinnedModel source,
                CharacterModelDefinition.AnimationSlot slot,
                double progress,
                FirstPersonCombatLibrary.CameraFraming camera
        ) {
            FirstPersonCombatLibrary.CameraFraming framing = camera == null
                    ? FirstPersonCombatLibrary.CameraFraming.identity() : camera;
            Matrix4d root = new Matrix4d()
                    .translate(
                            rig.positionX() + framing.positionX(),
                            rig.positionY() + framing.positionY(),
                            rig.positionZ() + framing.positionZ())
                    .rotateX(Math.toRadians(rig.rotationX() + framing.rotationX()))
                    .rotateY(Math.toRadians(rig.rotationY() + framing.rotationY()))
                    .rotateZ(Math.toRadians(rig.rotationZ() + framing.rotationZ()))
                    .scale(rig.scale());
            if (!rig.cameraAnchorBone().isBlank()) {
                Matrix4f anchor = source.nodeTransformNormalized(
                        slot, progress, rig.cameraAnchorBone());
                if (anchor != null) {
                    Vector3f position = anchor.getTranslation(new Vector3f());
                    root.translate(-position.x, -position.y, -position.z);
                }
            }
            return root;
        }

        private FirstPersonCombatLibrary.CameraFraming cameraFraming(Pose mode) {
            FirstPersonCombatLibrary.CameraFraming edited = cameraFramingSupplier.get();
            if (edited != null) return edited;
            if (skeletalPreview == null) return FirstPersonCombatLibrary.CameraFraming.identity();
            FirstPersonCombatLibrary.ItemProfile profile = liveProfile();
            FirstPersonCombatLibrary.WieldHand hand = animationHand(profile);
            FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
                case ATTACK -> hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
                case BLOCK -> (itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                        ? profile.wieldHand() : hand.opposite()) == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
                case CAST -> FirstPersonCombatLibrary.AnimationSlot.CAST;
                case HIT -> FirstPersonCombatLibrary.AnimationSlot.HIT;
                case DODGE -> FirstPersonCombatLibrary.AnimationSlot.DODGE;
            };
            FirstPersonCombatLibrary.Content content = contentSupplier.get();
            if (content == null) content = skeletalPreview.content();
            FirstPersonCombatLibrary.ClipBinding binding = content.resolveBinding(
                    itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                            ? WeaponType.NONE : weaponTypeSupplier.get(), profile,
                    liveRigDefinition(), slot);
            return binding == null
                    ? FirstPersonCombatLibrary.CameraFraming.identity()
                    : binding.cameraFraming();
        }

        private void drawSocketGuides(
                Graphics2D g,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            drawSocketGuide(g, root, slot, progress,
                    liveRigDefinition().leftHandBone(), new Color(80, 210, 255, 230));
            drawSocketGuide(g, root, slot, progress,
                    liveRigDefinition().rightHandBone(), new Color(255, 120, 90, 230));
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
            FirstPersonCombatLibrary.ItemProfile profile = liveProfile();
            FirstPersonCombatLibrary.WieldHand animationHand = animationHand(profile);
            FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> animationHand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
                case ATTACK -> animationHand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
                case BLOCK -> (itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                        ? profile.wieldHand() : profile.wieldHand().opposite())
                        == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
                case CAST -> FirstPersonCombatLibrary.AnimationSlot.CAST;
                case HIT -> FirstPersonCombatLibrary.AnimationSlot.HIT;
                case DODGE -> FirstPersonCombatLibrary.AnimationSlot.DODGE;
            };
            FirstPersonCombatLibrary.ClipBinding binding = liveBinding(mode);
            if (binding == null) return;
            int width = 150;
            Rectangle viewport = playerViewport();
            int x = Math.max(viewport.x + 12,
                    viewport.x + viewport.width - width - 18);
            int y = viewport.y + 18;
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

        private FirstPersonCombatLibrary.ItemProfile liveProfile() {
            FirstPersonCombatLibrary.ItemProfile current = itemProfileSupplier.get();
            return current == null ? skeletalPreview.profile() : current;
        }

        private void appendSkinnedAttachment(
                List<PaintTriangle> triangles,
                LwjglSkinnedModel source,
                CharacterModelDefinition.AnimationSlot slot,
                double progress,
                Matrix4d transform,
                FirstPersonCombatLibrary.WieldHand side,
                FirstPersonCombatLibrary.ArmCoverage coverage,
                Set<String> selectedMeshes,
                Map<LwjglSkinnedModel, LwjglSkinnedModel.Frame> frameCache
        ) {
            if (source == null || coverage == FirstPersonCombatLibrary.ArmCoverage.HIDE_FULL_ARM) return;
            LwjglSkinnedModel.Frame frame = frameCache.computeIfAbsent(source,
                    ignored -> snapshotFrame(source.skinNormalized(slot, progress)));
            for (int meshIndex = 0; meshIndex < source.meshes().size(); meshIndex++) {
                LwjglSkinnedModel.SkinnedMesh mesh = source.meshes().get(meshIndex);
                if (selectedMeshes != null && !selectedMeshes.isEmpty()
                        && selectedMeshes.stream().noneMatch(
                        name -> name.equalsIgnoreCase(mesh.name()))) continue;
                if (!regionVisible(mesh.name(), side, coverage)) continue;
                float[] positions = frame.meshPositions().get(meshIndex);
                appendTriangles(triangles, positions, mesh.texCoords(), mesh.indices(),
                        mesh.material().texture(), mesh.material().red(), mesh.material().green(),
                        mesh.material().blue(), mesh.material().alpha(), transform, false);
            }
        }

        private LwjglSkinnedModel.Frame snapshotFrame(LwjglSkinnedModel.Frame source) {
            if (animationTimer.isRunning()) return source;
            List<float[]> positions = new ArrayList<>(source.meshPositions().size());
            for (float[] mesh : source.meshPositions()) positions.add(mesh.clone());
            return new LwjglSkinnedModel.Frame(
                    List.copyOf(positions), source.normalizedProgress());
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

        private void appendStaticModel(
                List<PaintTriangle> triangles,
                LwjglStaticModel source,
                Matrix4d transform
        ) {
            for (LwjglStaticModel.Mesh mesh : source.meshes()) {
                appendTriangles(triangles, mesh.positions(), mesh.texCoords(), mesh.indices(),
                        mesh.texture(), mesh.red(), mesh.green(), mesh.blue(), mesh.alpha(), transform, true);
            }
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
                Matrix4d transform,
                boolean equipmentLighting
        ) {
            ScreenVertex[] transformed = new ScreenVertex[positions.length / 3];
            for (int index = 0; index < transformed.length; index++) {
                transformed[index] = transformVertex(positions, index, transform);
            }
            Color flatColor = texture == null
                    ? new Color(previewComponent(red, equipmentLighting),
                    previewComponent(green, equipmentLighting),
                    previewComponent(blue, equipmentLighting), clamp(alpha))
                    : null;
            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                int a = indices[triangle], b = indices[triangle + 1], c = indices[triangle + 2];
                if (a < 0 || b < 0 || c < 0 || a >= transformed.length
                        || b >= transformed.length || c >= transformed.length) continue;
                ScreenVertex va = transformed[a];
                ScreenVertex vb = transformed[b];
                ScreenVertex vc = transformed[c];
                if (va == null || vb == null || vc == null) continue;
                triangles.add(new PaintTriangle(
                        va, vb, vc,
                        (va.z() + vb.z() + vc.z()) / 3.0,
                        flatColor == null
                                ? triangleColor(texture, texCoords, a, b, c,
                                red, green, blue, alpha, equipmentLighting)
                                : flatColor));
            }
        }

        private ScreenVertex transformVertex(float[] positions, int index, Matrix4d transform) {
            int offset = index * 3;
            Vector4d point = new Vector4d(
                    positions[offset], positions[offset + 1], positions[offset + 2], 1);
            transform.transform(point);
            inspectionView.transform(point);
            if (point.z >= -previewNearPlane()) return null;
            Rectangle viewport = playerViewport();
            double focal = (viewport.height * 0.5)
                    / Math.tan(Math.toRadians(previewFov() * 0.5));
            return new ScreenVertex(
                    (int) Math.round(viewport.getCenterX() + point.x / -point.z * focal),
                    (int) Math.round(viewport.getCenterY() - point.y / -point.z * focal),
                    point.z);
        }

        private void paintTriangles(Graphics2D g, List<PaintTriangle> triangles) {
            Rectangle viewport = playerViewport();
            if (viewport.width <= 0 || viewport.height <= 0 || triangles.isEmpty()) return;
            double minimumDepth = Double.POSITIVE_INFINITY;
            double maximumDepth = Double.NEGATIVE_INFINITY;
            for (PaintTriangle triangle : triangles) {
                minimumDepth = Math.min(minimumDepth, triangle.depth());
                maximumDepth = Math.max(maximumDepth, triangle.depth());
            }
            double depthRange = Math.max(0.000001, maximumDepth - minimumDepth);
            final int depthBins = 128;
            Map<Long, Path2D.Float> opaqueBatches = new HashMap<>();
            List<PaintTriangle> translucent = new ArrayList<>();
            for (PaintTriangle triangle : triangles) {
                if (triangle.color().getAlpha() < 255) {
                    translucent.add(triangle);
                    continue;
                }
                int bin = Math.max(0, Math.min(depthBins - 1, (int) Math.floor(
                        (triangle.depth() - minimumDepth) / depthRange * (depthBins - 1))));
                long key = ((long) bin << 32)
                        | (triangle.color().getRGB() & 0xffffffffL);
                Path2D.Float path = opaqueBatches.computeIfAbsent(key,
                        ignored -> new Path2D.Float(Path2D.WIND_NON_ZERO));
                path.moveTo(triangle.a().x(), triangle.a().y());
                path.lineTo(triangle.b().x(), triangle.b().y());
                path.lineTo(triangle.c().x(), triangle.c().y());
                path.closePath();
            }
            opaqueBatches.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        g.setColor(new Color((int) (entry.getKey() & 0xffffffffL), true));
                        g.fill(entry.getValue());
                    });
            translucent.sort(Comparator.comparingDouble(PaintTriangle::depth));
            for (PaintTriangle triangle : translucent) {
                g.setColor(triangle.color());
                triangleX[0] = triangle.a().x();
                triangleX[1] = triangle.b().x();
                triangleX[2] = triangle.c().x();
                triangleY[0] = triangle.a().y();
                triangleY[1] = triangle.b().y();
                triangleY[2] = triangle.c().y();
                g.fillPolygon(triangleX, triangleY, 3);
            }
        }

        private void drawSecondaryGripGuide(
                Graphics2D g,
                Matrix4d equipmentTransform,
                Matrix4d root,
                CharacterModelDefinition.AnimationSlot slot,
                double progress
        ) {
            FirstPersonCombatLibrary.ItemProfile profile = liveProfile();
            if (Math.abs(profile.secondaryGripX()) < 0.0001
                    && Math.abs(profile.secondaryGripY()) < 0.0001
                    && Math.abs(profile.secondaryGripZ()) < 0.0001) return;
            Vector4d secondary = new Vector4d(
                    profile.secondaryGripX(), profile.secondaryGripY(),
                    profile.secondaryGripZ(), 1);
            equipmentTransform.transform(secondary);
            String offHandBone = liveRigDefinition()
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
            Rectangle viewport = playerViewport();
            g.drawString(String.format(java.util.Locale.US,
                    "Secondary grip error: %.3f", distance),
                    viewport.x + 18, viewport.y + 46);
        }

        private Point projectWorld(Vector4d point) {
            Vector4d inspectedPoint = new Vector4d(point);
            inspectionView.transform(inspectedPoint);
            if (inspectedPoint.z >= -previewNearPlane()) return null;
            Rectangle viewport = playerViewport();
            double focal = (viewport.height * 0.5)
                    / Math.tan(Math.toRadians(previewFov() * 0.5));
            return new Point(
                    (int) Math.round(viewport.getCenterX() + inspectedPoint.x / -inspectedPoint.z * focal),
                    (int) Math.round(viewport.getCenterY() - inspectedPoint.y / -inspectedPoint.z * focal));
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
                float alpha,
                boolean equipmentLighting
        ) {
            if (texture == null || texCoords.length == 0) {
                return new Color(previewComponent(red, equipmentLighting),
                        previewComponent(green, equipmentLighting),
                        previewComponent(blue, equipmentLighting), clamp(alpha));
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
                    previewComponent(sampled.getRed() / 255f * red, equipmentLighting),
                    previewComponent(sampled.getGreen() / 255f * green, equipmentLighting),
                    previewComponent(sampled.getBlue() / 255f * blue, equipmentLighting),
                    clamp(sampled.getAlpha() / 255f * alpha));
        }

        private int previewComponent(float value, boolean equipmentLighting) {
            float safe = Math.max(0f, Math.min(1f, value));
            if (!equipmentLighting) return clamp(safe);
            // The authoring preview uses a neutral work light so dark or
            // unresolved model materials cannot disappear into the ground.
            return clamp((float) (0.30 + 0.70 * Math.pow(safe, 0.62)));
        }

        private void drawBattleBackdrop(Graphics2D g) {
            Rectangle viewport = playerViewport();
            g.setColor(new Color(17, 19, 22));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(116, 154, 210));
            g.fillRect(viewport.x, viewport.y, viewport.width, viewport.height);
            int horizon = viewport.y + (int) (viewport.height * 0.56);
            GradientPaint ground = new GradientPaint(
                    0, horizon, new Color(55, 75, 67),
                    0, viewport.y + viewport.height, new Color(23, 30, 29));
            g.setPaint(ground);
            g.fillRect(viewport.x, horizon, viewport.width,
                    viewport.y + viewport.height - horizon);
            g.setColor(new Color(255, 255, 255, 38));
            g.drawLine(viewport.x, horizon, viewport.x + viewport.width, horizon);
            g.setColor(new Color(255, 255, 255, 80));
            g.drawRect(viewport.x, viewport.y,
                    Math.max(0, viewport.width - 1), Math.max(0, viewport.height - 1));
            g.setColor(new Color(0, 0, 0, 115));
            String caption = inspecting
                    ? String.format(java.util.Locale.US,
                    "Inspecting: yaw %.0f\u00B0, pitch %.0f\u00B0 - release right mouse to reset",
                    inspectionYawDegrees, inspectionPitchDegrees)
                    : String.format(java.util.Locale.US,
                    "16:9 player viewport (%.0f\u00B0 FOV) - right-drag to inspect", previewFov());
            int captionWidth = g.getFontMetrics().stringWidth(caption) + 16;
            int captionX = viewport.x + 10;
            int captionY = viewport.y + viewport.height - 30;
            g.fillRoundRect(captionX, captionY, captionWidth, 20, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(caption, captionX + 8, captionY + 14);
        }

        private Rectangle playerViewport() {
            int availableWidth = Math.max(1, getWidth());
            int availableHeight = Math.max(1, getHeight());
            int width = Math.min(availableWidth,
                    Math.max(1, (int) Math.round(availableHeight * PLAYER_VIEWPORT_ASPECT)));
            int height = Math.min(availableHeight,
                    Math.max(1, (int) Math.round(width / PLAYER_VIEWPORT_ASPECT)));
            return new Rectangle(
                    (availableWidth - width) / 2,
                    (availableHeight - height) / 2,
                    width,
                    height);
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
                            va, vb, vc,
                            (va.z() + vb.z() + vc.z()) / 3.0,
                            color));
                }
            }
            paintTriangles(g, triangles);
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
            inspectionView.transform(point);
            if (point.z >= -previewNearPlane()) return null;
            Rectangle viewport = playerViewport();
            double focal = (viewport.height * 0.5)
                    / Math.tan(Math.toRadians(previewFov() * 0.5));
            return new ScreenVertex(
                    (int) Math.round(viewport.getCenterX() + point.x / -point.z * focal),
                    (int) Math.round(viewport.getCenterY() - point.y / -point.z * focal),
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
            inspectionView.transform(point);
            if (point.z >= -previewNearPlane()) return null;
            Rectangle viewport = playerViewport();
            double focal = (viewport.height * 0.5)
                    / Math.tan(Math.toRadians(previewFov() * 0.5));
            return new Point(
                    (int) Math.round(viewport.getCenterX() + point.x / -point.z * focal),
                    (int) Math.round(viewport.getCenterY() - point.y / -point.z * focal));
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
                return new Color(previewComponent(mesh.red(), true),
                        previewComponent(mesh.green(), true),
                        previewComponent(mesh.blue(), true), clamp(mesh.alpha()));
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
                    previewComponent(sampled.getRed() / 255f * mesh.red(), true),
                    previewComponent(sampled.getGreen() / 255f * mesh.green(), true),
                    previewComponent(sampled.getBlue() / 255f * mesh.blue(), true),
                    clamp(sampled.getAlpha() / 255f * mesh.alpha()));
        }

        private double normalizeInspectionYaw(double value) {
            double normalized = value % 360.0;
            if (normalized > 180.0) normalized -= 360.0;
            if (normalized < -180.0) normalized += 360.0;
            return normalized;
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

    private double previewFov() {
        return skeletalPreview == null
                ? DEFAULT_FOV_DEGREES
                : liveRigDefinition().fieldOfViewDegrees();
    }

    private double previewNearPlane() {
        return skeletalPreview == null
                ? 0.05
                : Math.max(0.001, liveRigDefinition().nearPlane());
    }

    private static Matrix4f socketTransformWithoutScale(Matrix4f source) {
        if (source == null) return null;
        Vector3f translation = source.getTranslation(new Vector3f());
        Quaternionf rotation = source.getUnnormalizedRotation(new Quaternionf()).normalize();
        return new Matrix4f().translation(translation).rotate(rotation);
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

    private record PaintTriangle(
            ScreenVertex a,
            ScreenVertex b,
            ScreenVertex c,
            double depth,
            Color color
    ) {
    }

    private record LoadedPreview(
            LwjglStaticModel staticModel,
            SkeletalPreview skeletalPreview
    ) {
    }

    private record SkeletalPreview(
            FirstPersonCombatLibrary.Content content,
            FirstPersonCombatLibrary.RigDefinition rigDefinition,
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

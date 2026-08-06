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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
    private final JButton reloadButton = new JButton("Load / Refresh");
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
        timeline.addChangeListener(event -> canvas.repaint());
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
        canvas.repaint();
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
        CharacterModelDefinition.AnimationSlot slot = characterSlot((Pose) poseBox.getSelectedItem());
        double duration = skeletalPreview.rig().clipDurationSeconds(slot);
        return duration > 0.0 ? duration : 1.12;
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
        FirstPersonCombatLibrary.WieldHand hand = profile.wieldHand();
        boolean shield = itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD;
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
        private BufferedImage sceneColorBuffer;
        private double[] sceneDepthBuffer = new double[0];

        private Canvas() {
            setBackground(new Color(116, 154, 210));
            setToolTipText("Right-click and drag to inspect the assembled viewmodel. Release to return to the authored view.");
            MouseAdapter inspectionMouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
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
                public void mouseReleased(MouseEvent event) {
                    if (!inspecting) return;
                    resetInspectionView();
                    event.consume();
                }
            };
            addMouseListener(inspectionMouse);
            addMouseMotionListener(inspectionMouse);
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

            if (skeletalPreview != null
                    && drawSkeletalAssembly(g, type, mode,
                    timeline.getValue() / (double) Math.max(1, timeline.getMaximum()))) {
                g.dispose();
                return;
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
                FirstPersonCombatLibrary.RigDefinition rig = skeletalPreview.rigDefinition();
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
            FirstPersonCombatLibrary.RigDefinition rig = skeletalPreview.rigDefinition();
            FirstPersonCombatLibrary.ItemProfile profile = liveProfile();
            Matrix4d root = skeletalRoot(
                    rig, skeletalPreview.rig(), slot, progress, cameraFraming(mode));
            List<PaintTriangle> sceneTriangles = new ArrayList<>();
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.leftArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    profile.leftCoverage(), rig.leftVisibleMeshes());
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.rightArm(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    profile.rightCoverage(), rig.rightVisibleMeshes());
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.leftArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.LEFT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of());
            appendSkinnedAttachment(sceneTriangles, skeletalPreview.rightArmor(), slot, progress, root,
                    FirstPersonCombatLibrary.WieldHand.RIGHT,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Set.of());

            Matrix4d equipmentTransformForGuide = null;
            if (model != null && (type == InventorySystem.ItemType.WEAPON
                    || type == InventorySystem.ItemType.SHIELD)) {
                FirstPersonCombatLibrary.WieldHand hand =
                        profile.wieldHand();
                Matrix4f socket = socketTransformWithoutScale(
                        skeletalPreview.rig().nodeTransformNormalized(
                                slot, progress, rig.handBone(hand)));
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
            drawImpactGuide(g, mode, progress);
            g.setColor(new Color(255, 255, 255, 225));
            Rectangle viewport = playerViewport();
            g.drawString("Skeletal rig preview - " + slot.name(),
                    viewport.x + 18, viewport.y + 28);
            return true;
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
            FirstPersonCombatLibrary.WieldHand hand = profile.wieldHand();
            FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
                case ATTACK -> hand == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.ATTACK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.ATTACK_RIGHT;
                case BLOCK -> (itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                        ? hand : hand.opposite()) == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.BLOCK_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.BLOCK_RIGHT;
                case CAST -> FirstPersonCombatLibrary.AnimationSlot.CAST;
                case HIT -> FirstPersonCombatLibrary.AnimationSlot.HIT;
                case DODGE -> FirstPersonCombatLibrary.AnimationSlot.DODGE;
            };
            FirstPersonCombatLibrary.ClipBinding binding = skeletalPreview.content().resolveBinding(
                    itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                            ? WeaponType.NONE : weaponTypeSupplier.get(), profile,
                    skeletalPreview.rigDefinition(), slot);
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
                    skeletalPreview.rigDefinition().leftHandBone(), new Color(80, 210, 255, 230));
            drawSocketGuide(g, root, slot, progress,
                    skeletalPreview.rigDefinition().rightHandBone(), new Color(255, 120, 90, 230));
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
            FirstPersonCombatLibrary.AnimationSlot slot = switch (mode == null ? Pose.REST : mode) {
                case REST -> profile.wieldHand()
                        == FirstPersonCombatLibrary.WieldHand.LEFT
                        ? FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT
                        : FirstPersonCombatLibrary.AnimationSlot.IDLE_RIGHT;
                case ATTACK -> profile.wieldHand()
                        == FirstPersonCombatLibrary.WieldHand.LEFT
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
            FirstPersonCombatLibrary.ClipBinding binding =
                    skeletalPreview.content().resolveBinding(
                            itemTypeSupplier.get() == InventorySystem.ItemType.SHIELD
                                    ? WeaponType.NONE : weaponTypeSupplier.get(), profile, slot);
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
                Set<String> selectedMeshes
        ) {
            if (source == null || coverage == FirstPersonCombatLibrary.ArmCoverage.HIDE_FULL_ARM) return;
            LwjglSkinnedModel.Frame frame = source.skinNormalized(slot, progress);
            for (int meshIndex = 0; meshIndex < source.meshes().size(); meshIndex++) {
                LwjglSkinnedModel.SkinnedMesh mesh = source.meshes().get(meshIndex);
                if (selectedMeshes != null && !selectedMeshes.isEmpty()
                        && selectedMeshes.stream().noneMatch(
                        name -> name.equalsIgnoreCase(mesh.name()))) continue;
                if (!regionVisible(mesh.name(), side, coverage)) continue;
                float[] positions = frame.meshPositions().get(meshIndex);
                appendTriangles(triangles, positions, mesh.texCoords(), mesh.indices(),
                        mesh.material().texture(), mesh.material().red(), mesh.material().green(),
                        mesh.material().blue(), mesh.material().alpha(), transform);
            }
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
                        mesh.texture(), mesh.red(), mesh.green(), mesh.blue(), mesh.alpha(), transform);
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
                Matrix4d transform
        ) {
            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                int a = indices[triangle], b = indices[triangle + 1], c = indices[triangle + 2];
                ScreenVertex va = transformVertex(positions, a, transform);
                ScreenVertex vb = transformVertex(positions, b, transform);
                ScreenVertex vc = transformVertex(positions, c, transform);
                if (va == null || vb == null || vc == null) continue;
                triangles.add(new PaintTriangle(
                        va, vb, vc,
                        (va.z() + vb.z() + vc.z()) / 3.0,
                        triangleColor(texture, texCoords, a, b, c, red, green, blue, alpha)));
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
            ensureSceneBuffers(viewport.width, viewport.height);
            int[] pixels = ((DataBufferInt) sceneColorBuffer.getRaster()
                    .getDataBuffer()).getData();
            Arrays.fill(pixels, 0);
            Arrays.fill(sceneDepthBuffer, Double.NEGATIVE_INFINITY);

            // Far-to-near ordering gives translucent material a sensible source-over result;
            // the per-pixel reciprocal-depth test provides the actual opaque occlusion.
            triangles.sort(Comparator.comparingDouble(PaintTriangle::depth));
            for (PaintTriangle triangle : triangles) {
                rasterizeTriangle(triangle, viewport, pixels);
            }
            g.drawImage(sceneColorBuffer, viewport.x, viewport.y, null);
        }

        private void ensureSceneBuffers(int width, int height) {
            if (sceneColorBuffer != null
                    && sceneColorBuffer.getWidth() == width
                    && sceneColorBuffer.getHeight() == height) return;
            sceneColorBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            sceneDepthBuffer = new double[width * height];
        }

        private void rasterizeTriangle(
                PaintTriangle triangle,
                Rectangle viewport,
                int[] pixels
        ) {
            double ax = triangle.a().x() - viewport.x;
            double ay = triangle.a().y() - viewport.y;
            double bx = triangle.b().x() - viewport.x;
            double by = triangle.b().y() - viewport.y;
            double cx = triangle.c().x() - viewport.x;
            double cy = triangle.c().y() - viewport.y;
            double area = edge(ax, ay, bx, by, cx, cy);
            if (Math.abs(area) < 0.000001) return;

            int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
            int maxX = Math.min(viewport.width - 1,
                    (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
            int maxY = Math.min(viewport.height - 1,
                    (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
            if (minX > maxX || minY > maxY) return;

            double inverseZa = 1.0 / -triangle.a().z();
            double inverseZb = 1.0 / -triangle.b().z();
            double inverseZc = 1.0 / -triangle.c().z();
            int sourceArgb = triangle.color().getRGB();
            for (int y = minY; y <= maxY; y++) {
                double sampleY = y + 0.5;
                int row = y * viewport.width;
                for (int x = minX; x <= maxX; x++) {
                    double sampleX = x + 0.5;
                    double weightA = edge(bx, by, cx, cy, sampleX, sampleY) / area;
                    double weightB = edge(cx, cy, ax, ay, sampleX, sampleY) / area;
                    double weightC = 1.0 - weightA - weightB;
                    if (weightA < -0.000001 || weightB < -0.000001
                            || weightC < -0.000001) continue;
                    double inverseDepth = weightA * inverseZa
                            + weightB * inverseZb + weightC * inverseZc;
                    int pixel = row + x;
                    if (inverseDepth <= sceneDepthBuffer[pixel]) continue;
                    pixels[pixel] = sourceOver(sourceArgb, pixels[pixel]);
                    sceneDepthBuffer[pixel] = inverseDepth;
                }
            }
        }

        private double edge(
                double ax, double ay,
                double bx, double by,
                double px, double py
        ) {
            return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        }

        private int sourceOver(int source, int destination) {
            int sourceAlpha = source >>> 24;
            if (sourceAlpha >= 255 || destination == 0) return source;
            if (sourceAlpha <= 0) return destination;
            int destinationAlpha = destination >>> 24;
            int inverseSourceAlpha = 255 - sourceAlpha;
            int outAlpha = sourceAlpha + (destinationAlpha * inverseSourceAlpha + 127) / 255;
            if (outAlpha <= 0) return 0;
            int sourceRed = (source >>> 16) & 0xff;
            int sourceGreen = (source >>> 8) & 0xff;
            int sourceBlue = source & 0xff;
            int destinationRed = (destination >>> 16) & 0xff;
            int destinationGreen = (destination >>> 8) & 0xff;
            int destinationBlue = destination & 0xff;
            int destinationWeight = (destinationAlpha * inverseSourceAlpha + 127) / 255;
            int outRed = (sourceRed * sourceAlpha + destinationRed * destinationWeight)
                    / outAlpha;
            int outGreen = (sourceGreen * sourceAlpha + destinationGreen * destinationWeight)
                    / outAlpha;
            int outBlue = (sourceBlue * sourceAlpha + destinationBlue * destinationWeight)
                    / outAlpha;
            return outAlpha << 24 | outRed << 16 | outGreen << 8 | outBlue;
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
            String offHandBone = skeletalPreview.rigDefinition()
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
                : skeletalPreview.rigDefinition().fieldOfViewDegrees();
    }

    private double previewNearPlane() {
        return skeletalPreview == null
                ? 0.05
                : Math.max(0.001, skeletalPreview.rigDefinition().nearPlane());
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

package org.main.tools;

import org.main.battle.DifficultyResolver;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.EnemyButcheryProfile;
import org.main.content.PaintBrushLibrary;
import org.main.core.CraftingSystem;
import org.main.core.CraftingStationType;
import org.main.content.BattleContentCatalog;
import org.main.content.SkillDefinition;
import org.main.content.StatusDefinition;
import org.main.content.ThemeLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.content.WorldManifestLibrary.ChunkCoordinate;
import org.main.content.WorldManifestLibrary.WorldManifest;
import org.main.core.CharacterSkill;
import org.main.core.EquipmentAutoPlacementService;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.GameConfiguration;
import org.main.core.GearMaterial;
import org.main.core.MaterialCatalog;
import org.main.core.MaterialDefinition;
import org.main.core.EquipmentRequirementRules;
import org.main.core.GearDurability;
import org.main.core.InventorySystem;
import org.main.core.ItemModelIconProfile;
import org.main.core.InteractionSystem;
import org.main.core.Library;
import org.main.core.LanternDefinition;
import org.main.core.LanternSystem;
import org.main.core.LimbSlot;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.core.SmithingExperienceRules;
import org.main.core.WeaponType;
import org.main.engine.AssetLoader;
import org.main.engine.DungeonMap;
import org.main.engine.DungeonRenderContext;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MapLight;
import org.main.engine.MapLightingSettings;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.engine.SkyboxSpec;
import org.main.engine.TerrainEdgeKind;
import org.main.engine.TerrainGeometry;
import org.main.engine.TextureManager;
import org.main.experimental.CameraLookState;
import org.main.experimental.CharacterAnimationMetadataResolver;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglDungeonViewport;
import org.main.experimental.LwjglSkinnedModel;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;

public class AetherConstructionKit extends JFrame {
    private static final int DEFAULT_WIDTH = 14;
    private static final int DEFAULT_HEIGHT = 12;
    private static final int MIN_DIMENSION = 3;
    private static final int MAX_DIMENSION = 80;
    private static final int MAX_HISTORY_STATES = 80;
    private static final int AUTOSAVE_INTERVAL_MS = 60_000;
    private static final int DEFAULT_FISHING_FRAME_DURATION_MS = 260;
    private static final List<String> DEFAULT_FISHING_FRAME_PATHS = List.of(
            "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance1.png",
            "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance2.png",
            "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance3.png");
    private static final String DEFAULT_LIMB_ICON = "assets/images/monster/Ancient/Oct-5-2010/player/hand1/misc/head.png";
    private static final String DEFAULT_LEATHER_ICON =
            "assets/images/monster/Nov-2015/item/food/beef_jerky.png";
    private static final Path CONFIG_RESOURCE_PATH = Path.of("src", "main", "resources", "assets",
            "configuration.properties");
    private static final Path AUTOSAVE_PATH = Path.of("data", "editor", "autosave",
            "aether_construction_kit_recovery.properties");
    private static final Path PREFAB_FOLDER = Path.of("src", "main", "resources", "assets", "editor", "prefabs");
    private static final List<LightPreset> LIGHT_PRESETS = List.of(
            new LightPreset("Flesh Moon Glow", 0xB7374B, 8.0, 0.75, 1.20, 0.02),
            new LightPreset("Torch", 0xFF9A3D, 5.0, 1.15, 0.85, 0.18),
            new LightPreset("Campfire", 0xFF7628, 6.5, 1.35, 0.45, 0.24),
            new LightPreset("Furnace", 0xFF5A1E, 5.5, 1.50, 0.70, 0.12),
            new LightPreset("Candle", 0xFFD887, 2.5, 0.70, 0.45, 0.16),
            new LightPreset("Sickly Green", 0x77D66D, 4.0, 0.95, 0.65, 0.05),
            new LightPreset("Cold Blue", 0x70A8FF, 5.0, 0.90, 0.75, 0.03));

    private final MapCanvas mapCanvas = new MapCanvas();
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JSpinner widthSpinner = new JSpinner(
            new SpinnerNumberModel(DEFAULT_WIDTH, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JSpinner heightSpinner = new JSpinner(
            new SpinnerNumberModel(DEFAULT_HEIGHT, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JComboBox<ThemeLibrary> primaryThemeBox = new JComboBox<>(ThemeLibrary.values());
    private final JComboBox<PaintBrushLibrary.Palette> paletteBox = new JComboBox<>(
            PaintBrushLibrary.palettes().toArray(new PaintBrushLibrary.Palette[0]));
    private final JComboBox<PaintBrushLibrary.PaintBrush> brushBox = new JComboBox<>();
    private final JComboBox<String> mobAreaBox = new JComboBox<>();
    private final JComboBox<PaintMode> paintModeBox = new JComboBox<>(PaintMode.values());
    private final JComboBox<Library.TileType> tileTypeBox = new JComboBox<>(Library.TileType.values());
    private final JSpinner brushSizeSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9, 1));
    private final JSpinner heightLevelSpinner = new JSpinner(new SpinnerNumberModel(
            MapGeometryData.DEFAULT_HEIGHT_LEVEL,
            MapGeometryData.MIN_HEIGHT_LEVEL,
            MapGeometryData.MAX_HEIGHT_LEVEL,
            1));
    private final JCheckBox terrainOverlayBox = new JCheckBox("Terrain");
    private final JSpinner zoomSpinner = new JSpinner(new SpinnerNumberModel(100, 25, 300, 25));
    private final JComboBox<MapPrefab> prefabBox = new JComboBox<>();
    private final JComboBox<PlaceableCategory> placeableCategoryBox =
            new JComboBox<>(PlaceableCategory.values());
    private final JComboBox<PlaceableOption> placeableBox = new JComboBox<>();
    private final JTextField mapNameField = new JTextField("new_map", 14);
    private final JButton undoButton = new JButton("Undo");
    private final JButton redoButton = new JButton("Redo");
    private final JComboBox<ContentCategory> contentCategoryBox = new JComboBox<>(ContentCategory.values());
    private final JTextField contentSearchField = new JTextField(18);
    private final DefaultListModel<ContentEntry> contentModel = new DefaultListModel<>();
    private final JList<ContentEntry> contentList = new JList<>(contentModel);
    private final JTextArea inspectorArea = new JTextArea();
    private final Deque<MapDesignLibrary.MapDesign> undoStack = new ArrayDeque<>();
    private final Deque<MapDesignLibrary.MapDesign> redoStack = new ArrayDeque<>();
    private final Timer autosaveTimer = new Timer(AUTOSAVE_INTERVAL_MS, event -> autosaveRecovery());
    private final Map<String, Dimension> popupSizes = new HashMap<>();
    private MapDesignLibrary.AuthoredContent sharedContentBaseline = emptyAuthoredContent();
    private FirstPersonCombatLibrary.ItemProfile pendingItemFirstPersonProfile;
    private boolean dirty;
    private String pendingTriggerId = "";
    private MapDesignLibrary.TriggerFireMode pendingTriggerFireMode = MapDesignLibrary.TriggerFireMode.ON_ENTRY;
    private boolean pendingTriggerOneShot = true;
    private String pendingTriggerQuestId = "";
    private String pendingTriggerQuestProgress = "";
    private String wiringTriggerId = "";
    private String lastFindKey = "";
    private int lastFindIndex = -1;
    private Point inspectedTile;
    private MapDesignLibrary.MapPlacement inspectedPlacement;
    private MapDesignLibrary.PlacedObjectInstance inspectedPlacedObject;
    private MapDesignLibrary.MapTrigger inspectedTrigger;
    private MapLight inspectedLight;
    private Point inspectedTriggerTarget;
    private Path currentMapPath;
    private Path currentWorldManifestPath;
    private WorldManifest activeWorld;
    private ChunkCoordinate activeWorldChunk;
    private MapDesignLibrary.AuthoredContent activeWorldContent;
    private final Map<ChunkCoordinate, MapDesignLibrary.MapDesign> worldNeighborDesigns = new HashMap<>();

    private MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            ThemeLibrary.STONE_WOOD,
            ThemeLibrary.SANDSTONE_GATE);

    public AetherConstructionKit() {
        super("Aether Construction Kit");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(980, 720));

        loadSharedContentIntoDesign();
        loadPrefabs();
        populatePlaceables();
        populateBrushes();

        add(createToolbar(), BorderLayout.NORTH);
        add(createEditorBody(), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        installKeyboardShortcuts();
        refreshContentBrowser();
        offerAutosaveRecovery();
        autosaveTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                autosaveTimer.stop();
                if (dirty) {
                    autosaveRecovery();
                }
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        toolbar.add(createFileMenuButton());
        toolbar.addSeparator();
        toolbar.add(createMapConfigMenuButton());
        toolbar.add(createModifyMenuButton());

        toolbar.addSeparator();
        toolbar.add(new JLabel("Primary"));
        toolbar.add(primaryThemeBox);
        toolbar.add(new JLabel("Palette"));
        paletteBox.addActionListener(event -> populateBrushes());
        toolbar.add(paletteBox);
        toolbar.add(new JLabel("Paint"));
        toolbar.add(brushBox);
        toolbar.add(new JLabel("Area"));
        toolbar.add(mobAreaBox);

        toolbar.addSeparator();
        toolbar.add(new JLabel("Mode"));
        paintModeBox.addActionListener(event -> {
            populateBrushes();
            populateMobAreas();
            mapCanvas.repaint();
        });
        toolbar.add(paintModeBox);
        toolbar.add(new JLabel("Brush"));
        toolbar.add(brushSizeSpinner);
        toolbar.add(new JLabel("Elevation"));
        toolbar.add(heightLevelSpinner);
        terrainOverlayBox.addActionListener(event -> mapCanvas.repaint());
        toolbar.add(terrainOverlayBox);
        toolbar.add(new JLabel("Zoom"));
        zoomSpinner.addChangeListener(event -> updateMapZoom());
        toolbar.add(zoomSpinner);
        toolbar.add(new JLabel("Tile"));
        toolbar.add(tileTypeBox);

        toolbar.addSeparator();
        toolbar.add(createToolsMenuButton());
        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(event -> showAuthoringHelp());
        toolbar.add(helpButton);

        toolbar.addSeparator();
        undoButton.addActionListener(event -> undoMapEdit());
        redoButton.addActionListener(event -> redoMapEdit());
        toolbar.add(undoButton);
        toolbar.add(redoButton);
        updateHistoryButtons();

        return toolbar;
    }

    private JButton createMapConfigMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "New", this::createNewMap);
        addMenuItem(menu, "Resize", this::resizeCurrentMap);
        addMenuItem(menu, "Metadata", this::editMetadata);
        menu.addSeparator();
        addMenuItem(menu, "New World", this::createNewWorld);
        addMenuItem(menu, "Open World", this::openWorld);
        addMenuItem(menu, "World Settings", this::editWorldSettings);
        addMenuItem(menu, "Add Chunk", this::addWorldChunk);
        addMenuItem(menu, "Remove Chunk", this::removeWorldChunk);
        addMenuItem(menu, "Validate World", this::validateWorld);
        return menuButton("Map Config", menu);
    }

    private JButton createModifyMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Sound Effects", this::manageSoundEffectConfiguration);
        addMenuItem(menu, "First-Person Viewmodels", this::openFirstPersonViewmodelEditor);
        return menuButton("Modify", menu);
    }

    private void openFirstPersonViewmodelEditor() {
        List<FirstPersonViewmodelEditorWorkspace.ItemOption> items = design.customItems().stream()
                .filter(item -> item.itemType() == InventorySystem.ItemType.WEAPON
                        || item.itemType() == InventorySystem.ItemType.SHIELD
                        || item.itemType() == InventorySystem.ItemType.CHEST_ARMOR)
                .map(item -> new FirstPersonViewmodelEditorWorkspace.ItemOption(
                        item.itemId(),
                        item.displayName(),
                        item.itemType(),
                        item.weaponType(),
                        item.twoHanded(),
                        item.firstPersonModelPath(),
                        item.viewModelProfile()))
                .toList();
        FirstPersonViewmodelEditorWorkspace workspace =
                new FirstPersonViewmodelEditorWorkspace(
                        this,
                        items,
                        field -> showAssetBrowser(field, AssetBrowserType.MODELS),
                        rigRenames -> {
                            boolean referencesSaved = rewriteFirstPersonRigReferences(rigRenames);
                            refreshContentBrowser();
                            if (referencesSaved) setStatus("Applied first-person viewmodel catalog.");
                        });
        workspace.setVisible(true);
    }

    private boolean rewriteFirstPersonRigReferences(Map<String, String> renames) {
        if (renames == null || renames.isEmpty()) return true;
        boolean changed = false;
        for (int index = 0; index < design.customLimbs().size(); index++) {
            MapDesignLibrary.CustomLimb limb = design.customLimbs().get(index);
            String replacement = renames.get(FirstPersonCombatLibrary.normalizeId(limb.firstPersonRigId()));
            if (replacement == null || replacement.equals(limb.firstPersonRigId())) continue;
            design.customLimbs().set(index, new MapDesignLibrary.CustomLimb(
                    limb.limbId(), limb.displayName(), limb.limbSlot(), limb.iconPath(), limb.condition(),
                    limb.description(), limb.sourceCreatureId(), limb.paperDollSourcePath(),
                    limb.statBonuses(), limb.skillIds(), limb.firstPersonModelPath(), replacement,
                    limb.paperDollDerivedIcon(), limb.baseGoldValue()));
            changed = true;
        }
        return !changed || persistSharedContent("first-person rig references");
    }

    private JSplitPane createEditorBody() {
        JScrollPane mapScrollPane = new JScrollPane(mapCanvas);
        mapScrollPane.setBorder(BorderFactory.createTitledBorder("Map"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createContentBrowserPanel(), mapScrollPane);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerLocation(300);
        return splitPane;
    }

    private JPanel createContentBrowserPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Content Browser"));
        panel.setPreferredSize(new Dimension(300, 640));

        panel.add(createContentBrowserControls(), BorderLayout.NORTH);

        contentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contentList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.label());
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        contentList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateInspector();
            }
        });
        contentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    editSelectedContent();
                }
            }
        });

        inspectorArea.setEditable(false);
        inspectorArea.setLineWrap(true);
        inspectorArea.setWrapStyleWord(true);
        inspectorArea.setRows(10);

        JSplitPane browserSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(contentList),
                new JScrollPane(inspectorArea));
        browserSplit.setResizeWeight(0.62);
        panel.add(browserSplit, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
        JButton editButton = new JButton("Edit");
        JButton duplicateButton = new JButton("Duplicate");
        JButton deleteButton = new JButton("Delete");
        JButton selectButton = new JButton("Select");
        JButton findButton = new JButton("Find");
        JButton dependenciesButton = new JButton("Deps");
        JButton graphButton = new JButton("Graph");
        JButton refreshButton = new JButton("Refresh");
        editButton.addActionListener(event -> editSelectedContent());
        duplicateButton.addActionListener(event -> duplicateSelectedContent());
        deleteButton.addActionListener(event -> deleteSelectedContent());
        selectButton.addActionListener(event -> selectContentForPlacement());
        findButton.addActionListener(event -> findSelectedContentOnMap());
        dependenciesButton.addActionListener(event -> showSelectedDependencies());
        graphButton.addActionListener(event -> showSelectedGraph());
        refreshButton.addActionListener(event -> refreshContentBrowser());
        buttons.add(editButton);
        buttons.add(duplicateButton);
        buttons.add(deleteButton);
        buttons.add(selectButton);
        buttons.add(findButton);
        buttons.add(dependenciesButton);
        buttons.add(graphButton);
        buttons.add(refreshButton);
        JPanel browserFooter = new JPanel(new BorderLayout(4, 4));
        browserFooter.add(buttons, BorderLayout.NORTH);
        browserFooter.add(createPrefabBrowserPanel(), BorderLayout.SOUTH);
        panel.add(browserFooter, BorderLayout.SOUTH);

        contentCategoryBox.addActionListener(event -> refreshContentBrowser());
        contentSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshContentBrowser();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshContentBrowser();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshContentBrowser();
            }
        });

        return panel;
    }

    private JPanel createContentBrowserControls() {
        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints sectionConstraints = new GridBagConstraints();
        sectionConstraints.gridx = 0;
        sectionConstraints.weightx = 1.0;
        sectionConstraints.fill = GridBagConstraints.HORIZONTAL;
        sectionConstraints.anchor = GridBagConstraints.NORTHWEST;

        JButton createButton = createCreateMenuButton();
        sectionConstraints.gridy = 0;
        controls.add(createButton, sectionConstraints);

        JPanel placement = new JPanel(new GridBagLayout());
        placement.setBorder(BorderFactory.createTitledBorder("Object Placement"));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(2, 2, 2, 6);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(2, 0, 2, 2);

        labelConstraints.gridy = 0;
        fieldConstraints.gridy = 0;
        placement.add(new JLabel("Object Type"), labelConstraints);
        placement.add(placeableCategoryBox, fieldConstraints);

        labelConstraints.gridy = 1;
        fieldConstraints.gridy = 1;
        placement.add(new JLabel("Object"), labelConstraints);
        placement.add(placeableBox, fieldConstraints);
        placeableCategoryBox.addActionListener(event -> populatePlaceables());
        sectionConstraints.gridy = 1;
        controls.add(placement, sectionConstraints);

        JPanel filters = new JPanel(new BorderLayout(4, 4));
        filters.setBorder(BorderFactory.createTitledBorder("Browse"));
        filters.add(contentCategoryBox, BorderLayout.NORTH);
        filters.add(contentSearchField, BorderLayout.SOUTH);
        sectionConstraints.gridy = 2;
        controls.add(filters, sectionConstraints);

        return controls;
    }

    private JPanel createPrefabBrowserPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Prefabs"));
        prefabBox.setPrototypeDisplayValue(new MapPrefab(
                "Long Prefab Name",
                1,
                1,
                new Library.TileType[][] { { Library.TileType.FLOOR } },
                new int[][] { { 0 } },
                MapPaintData.blank(1, 1),
                MapGeometryData.blank(1, 1),
                MobAreaData.blank(1, 1),
                List.of(),
                List.of()));
        panel.add(prefabBox, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        JButton placeButton = new JButton("Place");
        JButton createButton = new JButton("Create");
        JButton manageButton = new JButton("Manage");
        placeButton.addActionListener(event -> {
            if (prefabBox.getSelectedItem() == null) {
                setStatus("No prefab selected.");
                return;
            }
            paintModeBox.setSelectedItem(PaintMode.PLACE_PREFAB);
            setStatus("Selected prefab placement mode.");
        });
        createButton.addActionListener(event -> createPrefabFromRegion());
        manageButton.addActionListener(event -> managePrefabs());
        buttons.add(placeButton);
        buttons.add(createButton);
        buttons.add(manageButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JButton createCreateMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Dialogue", () -> openNewQuestDialogueEditor(
                QuestDialogueEditorWorkspace.Kind.DIALOGUE));
        addMenuItem(menu, "Quest", () -> openNewQuestDialogueEditor(
                QuestDialogueEditorWorkspace.Kind.QUEST));
        addMenuItem(menu, "Material", this::createMaterialTier);
        addMenuItem(menu, "Item", this::createCustomItem);
        addMenuItem(menu, "Enemy", this::createCustomMob);
        addMenuItem(menu, "NPC", this::createCustomNpc);
        addMenuItem(menu, "Furniture", this::createCustomFurniture);
        addMenuItem(menu, "Limb", this::createCustomLimb);
        addMenuItem(menu, "Battle Skill", () -> openNewBattleContentEditor(
                BattleSkillEditorWorkspace.Kind.SKILL));
        addMenuItem(menu, "Status", () -> openNewBattleContentEditor(
                BattleSkillEditorWorkspace.Kind.STATUS));
        addMenuItem(menu, "First-Person Viewmodel", this::openFirstPersonViewmodelEditor);
        addMenuItem(menu, "Gathering Node", this::createCustomGatheringNode);
        addMenuItem(menu, "Cooking Recipe", this::createCookingRecipe);
        addMenuItem(menu, "Crafting Recipe", this::createCraftingRecipe);
        addMenuItem(menu, "Mob Area", this::createMobArea);
        addMenuItem(menu, "Map Link", this::createMapLink);
        addMenuItem(menu, "Light", this::createLight);
        addMenuItem(menu, "Trigger", this::createTrigger);
        return menuButton("Create", menu);
    }

    private JButton createToolsMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Asset Browser", () -> showAssetBrowser(null));
        addMenuItem(menu, "Battle Skill & Status Editor", () -> openBattleContentEditor(
                BattleSkillEditorWorkspace.Kind.SKILL, ""));
        addMenuItem(menu, "Quest Flow Editor", () -> openQuestDialogueEditor(
                QuestDialogueEditorWorkspace.Kind.QUEST, ""));
        addMenuItem(menu, "Dialogue Flow Editor", () -> openQuestDialogueEditor(
                QuestDialogueEditorWorkspace.Kind.DIALOGUE, ""));
        addMenuItem(menu, "Level Gates", this::manageLevelGates);
        addMenuItem(menu, "Gathering Tool Animations", this::manageAnimations);
        addMenuItem(menu, "Light Manager", this::manageLights);
        addMenuItem(menu, "Trigger Manager", this::manageTriggers);
        addMenuItem(menu, "Sound Designer", () -> openToolWindow(new SoundDesignerTool()));
        addMenuItem(menu, "Song Designer", () -> openToolWindow(new SongDesignerTool()));
        addMenuItem(menu, "Sprite Sheet Splitter", () -> openToolWindow(new SpriteSheetSplitterTool()));
        return menuButton("Tools", menu);
    }

    private void openNewBattleContentEditor(BattleSkillEditorWorkspace.Kind kind) {
        BattleSkillEditorWorkspace.openNew(this, kind, battleContentEditorHost());
    }

    private void openBattleContentEditor(BattleSkillEditorWorkspace.Kind kind, String selectedId) {
        BattleSkillEditorWorkspace.open(this, kind, selectedId, battleContentEditorHost());
    }

    private BattleSkillEditorWorkspace.Host battleContentEditorHost() {
        return new BattleSkillEditorWorkspace.Host() {
            @Override
            public List<String> referencesToSkill(String id) {
                String normalized = BattleContentCatalog.normalizeId(id);
                List<String> references = new ArrayList<>();
                for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
                    if (mob.skillIds().stream().anyMatch(skillId -> skillId.equals(normalized))) {
                        references.add("Enemy " + mob.displayName());
                    }
                }
                for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
                    if (limb.skillIds().stream().anyMatch(skillId -> skillId.equals(normalized))) {
                        references.add("Limb " + limb.displayName());
                    }
                }
                return references;
            }

            @Override
            public void catalogsSaved(
                    Map<String, String> skillReplacements,
                    Map<String, String> statusReplacements
            ) throws IOException {
                if (!skillReplacements.isEmpty()) {
                    rewriteBattleSkillReferences(skillReplacements);
                    if (!persistSharedContent("battle skill reference update")) {
                        throw new IOException("Skill catalogs were saved, but dependent enemy/limb content failed to save.");
                    }
                }
                refreshContentBrowser();
                setStatus("Battle skill and status catalogs reloaded.");
            }
        };
    }

    private void openNewQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind kind) {
        QuestDialogueEditorWorkspace.openNew(this, kind, questDialogueEditorHost());
    }

    private void openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind kind, String selectedId) {
        QuestDialogueEditorWorkspace.open(this, kind, selectedId, questDialogueEditorHost());
    }

    private QuestDialogueEditorWorkspace.Host questDialogueEditorHost() {
        return new QuestDialogueEditorWorkspace.Host() {
            @Override
            public List<MapDesignLibrary.AuthoredQuest> quests() {
                return List.copyOf(design.authoredQuests());
            }

            @Override
            public List<MapDesignLibrary.AuthoredDialogue> dialogues() {
                return List.copyOf(design.authoredDialogues());
            }

            @Override
            public List<MapDesignLibrary.CustomNpc> npcs() {
                return List.copyOf(design.customNpcs());
            }

            @Override
            public List<MapDesignLibrary.CustomItem> items() {
                return List.copyOf(design.customItems());
            }

            @Override
            public List<MapDesignLibrary.CustomLimb> limbs() {
                return List.copyOf(design.customLimbs());
            }

            @Override
            public List<MapDesignLibrary.CustomMob> mobs() {
                return List.copyOf(design.customMobs());
            }

            @Override
            public List<MapDesignLibrary.ValidationIssue> validate(
                    List<MapDesignLibrary.AuthoredQuest> quests,
                    List<MapDesignLibrary.AuthoredDialogue> dialogues,
                    List<MapDesignLibrary.CustomNpc> npcs
            ) {
                return MapDesignLibrary.validateQuestDialogueContent(
                        quests,
                        dialogues,
                        npcs,
                        design.customItems(),
                        design.customLimbs(),
                        design.customMobs()
                );
            }

            @Override
            public void save(
                    List<MapDesignLibrary.AuthoredQuest> quests,
                    List<MapDesignLibrary.AuthoredDialogue> dialogues,
                    List<MapDesignLibrary.CustomNpc> npcs
            ) throws IOException {
                List<MapDesignLibrary.AuthoredQuest> previousQuests =
                        new ArrayList<>(design.authoredQuests());
                List<MapDesignLibrary.AuthoredDialogue> previousDialogues =
                        new ArrayList<>(design.authoredDialogues());
                List<MapDesignLibrary.CustomNpc> previousNpcs =
                        new ArrayList<>(design.customNpcs());
                design.authoredQuests().clear();
                design.authoredQuests().addAll(quests);
                design.authoredDialogues().clear();
                design.authoredDialogues().addAll(dialogues);
                design.customNpcs().clear();
                design.customNpcs().addAll(npcs);
                if (!persistSharedContent("quest and dialogue catalogs")) {
                    design.authoredQuests().clear();
                    design.authoredQuests().addAll(previousQuests);
                    design.authoredDialogues().clear();
                    design.authoredDialogues().addAll(previousDialogues);
                    design.customNpcs().clear();
                    design.customNpcs().addAll(previousNpcs);
                    throw new IOException("The content transaction could not be saved.");
                }
                populatePlaceables();
                refreshContentBrowser();
                mapCanvas.repaint();
                setStatus("Quest, dialogue, and NPC catalogs updated.");
            }
        };
    }

    private void rewriteBattleSkillReferences(Map<String, String> replacements) {
        for (int index = 0; index < design.customMobs().size(); index++) {
            MapDesignLibrary.CustomMob mob = design.customMobs().get(index);
            List<String> updated = rewrittenSkills(mob.skillIds(), replacements);
            if (updated.equals(mob.skillIds())) continue;
            design.customMobs().set(index, new MapDesignLibrary.CustomMob(
                    mob.mobId(),
                    mob.displayName(),
                    mob.imagePath(),
                    mob.paperDollSourcePath(),
                    mob.statValues(),
                    mob.xpReward(),
                    mob.description(),
                    mob.attackSoundPath(),
                    mob.damageSoundPath(),
                    mob.combatAiIntelligence(),
                    mob.awarenessRadius(),
                    mob.movementIntervalMs(),
                    mob.respawnDelayMs(),
                    updated,
                    mob.dropEntries(),
                    mob.characterModel(),
                    mob.butcheryProfile()));
        }
        for (int index = 0; index < design.customLimbs().size(); index++) {
            MapDesignLibrary.CustomLimb limb = design.customLimbs().get(index);
            List<String> updated = rewrittenSkills(limb.skillIds(), replacements);
            if (updated.equals(limb.skillIds())) continue;
            design.customLimbs().set(index, new MapDesignLibrary.CustomLimb(
                    limb.limbId(),
                    limb.displayName(),
                    limb.limbSlot(),
                    limb.iconPath(),
                    limb.condition(),
                    limb.description(),
                    limb.sourceCreatureId(),
                    limb.paperDollSourcePath(),
                    limb.statBonuses(),
                    updated,
                    limb.firstPersonModelPath(),
                    limb.firstPersonRigId(),
                    limb.paperDollDerivedIcon(),
                    limb.baseGoldValue()));
        }
    }

    private List<String> rewrittenSkills(
            List<String> source,
            Map<String, String> replacements
    ) {
        LinkedHashMap<String, String> updated = new LinkedHashMap<>();
        for (String skillId : source) {
            String replacement = replacements.getOrDefault(skillId, skillId);
            if (replacement != null && !replacement.isBlank()) {
                String normalized = BattleContentCatalog.normalizeId(replacement);
                updated.put(normalized, normalized);
            }
        }
        return List.copyOf(updated.values());
    }

    private JButton createFileMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Validate", this::validateMap);
        addMenuItem(menu, "Save", this::saveMap);
        addMenuItem(menu, "Load", this::loadMap);
        return menuButton("File", menu);
    }

    private void openToolWindow(JFrame toolWindow) {
        toolWindow.setLocationRelativeTo(this);
        toolWindow.setVisible(true);
        setStatus("Opened " + toolWindow.getTitle() + ".");
    }

    private void showAssetBrowser(JTextField targetField) {
        showAssetBrowser(targetField, AssetBrowserType.ALL);
    }

    private void showAssetBrowser(JTextField targetField, AssetBrowserType initialType) {
        invalidateModelAssetCaches();
        List<AssetBrowserEntry> assets = scanEditorAssets();
        DefaultListModel<AssetBrowserEntry> assetModel = new DefaultListModel<>();
        JList<AssetBrowserEntry> assetList = new JList<>(assetModel);
        JTextField searchField = new JTextField(24);
        JComboBox<AssetBrowserType> typeBox = new JComboBox<>(AssetBrowserType.values());
        typeBox.setSelectedItem(initialType == null ? AssetBrowserType.ALL : initialType);
        JLabel previewLabel = new JLabel("No preview", JLabel.CENTER);
        JTextArea detailArea = new JTextArea(8, 32);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);

        Runnable refreshAssets = () -> {
            String filter = searchField.getText() == null
                    ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            AssetBrowserType selectedType = (AssetBrowserType) typeBox.getSelectedItem();
            assetModel.clear();
            assets.stream()
                    .filter(asset -> selectedType == null || selectedType == AssetBrowserType.ALL
                            || asset.type() == selectedType)
                    .filter(asset -> filter.isBlank() || asset.searchText().contains(filter))
                    .sorted(Comparator
                            .comparing((AssetBrowserEntry asset) -> asset.type().label(), String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(AssetBrowserEntry::assetPath, String.CASE_INSENSITIVE_ORDER))
                    .forEach(assetModel::addElement);
            if (!assetModel.isEmpty()) {
                assetList.setSelectedIndex(0);
            } else {
                previewLabel.setIcon(null);
                previewLabel.setText("No matching assets");
                detailArea.setText("");
            }
        };

        assetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assetList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateAssetPreview(assetList.getSelectedValue(), previewLabel, detailArea);
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshAssets.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshAssets.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshAssets.run();
            }
        });
        typeBox.addActionListener(event -> refreshAssets.run());

        JButton copyButton = new JButton("Copy Path");
        JButton useButton = new JButton("Use Path");
        JButton refreshButton = new JButton("Rescan");
        JButton closeButton = new JButton("Close");
        copyButton.addActionListener(event -> {
            AssetBrowserEntry selected = assetList.getSelectedValue();
            if (selected == null) {
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected.assetPath()),
                    null);
            setStatus("Copied asset path " + selected.assetPath() + ".");
        });
        useButton.addActionListener(event -> {
            AssetBrowserEntry selected = assetList.getSelectedValue();
            if (selected == null || targetField == null) {
                return;
            }
            targetField.setText(selected.assetPath());
            setStatus("Selected asset path " + selected.assetPath() + ".");
        });
        useButton.setEnabled(targetField != null);
        refreshButton.addActionListener(event -> {
            invalidateModelAssetCaches();
            assets.clear();
            assets.addAll(scanEditorAssets());
            refreshAssets.run();
        });

        JPanel filters = new JPanel(new BorderLayout(4, 4));
        filters.add(typeBox, BorderLayout.WEST);
        filters.add(searchField, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        previewLabel.setPreferredSize(new Dimension(260, 220));
        previewLabel.setBorder(BorderFactory.createTitledBorder("Preview"));
        rightPanel.add(previewLabel, BorderLayout.CENTER);
        rightPanel.add(new JScrollPane(detailArea), BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(assetList), rightPanel);
        splitPane.setResizeWeight(0.45);

        JPanel buttons = new JPanel();
        buttons.add(copyButton);
        buttons.add(useButton);
        buttons.add(refreshButton);
        buttons.add(closeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(filters, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshAssets.run();

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {}, null);
        var dialog = pane.createDialog(this, "Asset Browser");
        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private void manageSoundEffectConfiguration() {
        JTextField doorOpenField = new JTextField(GameConfiguration.stringValue("sound.doorOpen.path", ""), 28);
        JTextField doorCloseField = new JTextField(GameConfiguration.stringValue("sound.doorClose.path", ""), 28);
        JTextField autoAttackField = new JTextField(
                GameConfiguration.stringValue("battle.playerAutoAttack.soundPath", ""),
                28);

        JPanel fields = createFormPanel();
        addSoundPathRow(fields, "Door Open", doorOpenField);
        addSoundPathRow(fields, "Door Close", doorCloseField);
        addSoundPathRow(fields, "Player Auto Attack", autoAttackField);

        JTextArea note = new JTextArea(
                "Choose sound assets for doors and the player's default auto attack. "
                        + "An equipped weapon's own use sound takes precedence over the default auto-attack sound.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(fields, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Sound Effects");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("sound.doorOpen.path", doorOpenField.getText().trim());
        values.put("sound.doorClose.path", doorCloseField.getText().trim());
        values.put("battle.playerAutoAttack.soundPath", autoAttackField.getText().trim());
        Properties properties = loadPackagedConfigurationProperties();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
            GameConfiguration.setValue(entry.getKey(), entry.getValue());
        }
        try {
            writePropertiesAtomically(CONFIG_RESOURCE_PATH, properties,
                    "Aether packaged gameplay configuration");
            setStatus("Updated door and player auto-attack sound effects.");
        } catch (IOException exception) {
            setStatus("Sound effect configuration save failed: " + exception.getMessage());
        }
    }

    private void addSoundPathRow(JPanel fields, String label, JTextField pathField) {
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(event -> showAssetBrowser(pathField, AssetBrowserType.SOUNDS));
        addFormRow(fields, label, pathFieldPanel(pathField, browseButton));
    }

    private void replaceSharedContentInDesign(MapDesignLibrary.AuthoredContent content) {
        design.authoredDialogues().clear();
        design.authoredDialogues().addAll(content.authoredDialogues());
        design.authoredQuests().clear();
        design.authoredQuests().addAll(content.authoredQuests());
        design.customItems().clear();
        design.customItems().addAll(content.customItems());
        design.customMobs().clear();
        design.customMobs().addAll(content.customMobs());
        design.customLimbs().clear();
        design.customLimbs().addAll(content.customLimbs());
        design.customNpcs().clear();
        design.customNpcs().addAll(content.customNpcs());
        design.customFurniture().clear();
        design.customFurniture().addAll(content.customFurniture());
        design.customGatheringNodes().clear();
        design.customGatheringNodes().addAll(content.customGatheringNodes());
        design.customCookingRecipes().clear();
        design.customCookingRecipes().addAll(content.customCookingRecipes());
        design.craftingRecipes().clear();
        design.craftingRecipes().addAll(content.craftingRecipes());
    }

    private List<AssetBrowserEntry> scanEditorAssets() {
        List<AssetBrowserEntry> assets = new ArrayList<>();
        addAssetFiles(assets, Path.of("src", "main", "resources"), Path.of("src", "main", "resources", "assets"));
        addAssetFiles(assets, Path.of("."), Path.of("data", "images"));
        addAssetFiles(assets, Path.of("."), Path.of("data", "sounds"));
        addAssetFiles(assets, Path.of("."), Path.of("data", "songs"));
        return assets;
    }

    private void addAssetFiles(List<AssetBrowserEntry> assets, Path pathPrefix, Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }

        try (var stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> toAssetBrowserEntry(pathPrefix, path))
                    .filter(entry -> entry.type() != AssetBrowserType.OTHER)
                    .forEach(assets::add);
        } catch (IOException exception) {
            setStatus("Asset scan warning: " + exception.getMessage());
        }
    }

    private AssetBrowserEntry toAssetBrowserEntry(Path pathPrefix, Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        AssetBrowserType type;
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")) {
            type = AssetBrowserType.IMAGES;
        } else if (lowerName.endsWith(".wav") || lowerName.endsWith(".aiff") || lowerName.endsWith(".au")) {
            type = AssetBrowserType.SOUNDS;
        } else if (lowerName.endsWith(".glb") || lowerName.endsWith(".fbx")) {
            type = AssetBrowserType.MODELS;
        } else if (lowerName.endsWith(".properties")) {
            type = AssetBrowserType.DATA;
        } else {
            type = AssetBrowserType.OTHER;
        }

        Path normalizedPrefix = pathPrefix.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        String assetPath = normalizedPath.startsWith(normalizedPrefix)
                ? normalizedPrefix.relativize(normalizedPath).toString()
                : path.toString();
        assetPath = assetPath.replace('\\', '/');
        return new AssetBrowserEntry(assetPath, type, path);
    }

    private void updateAssetPreview(AssetBrowserEntry selected, JLabel previewLabel, JTextArea detailArea) {
        if (selected == null) {
            previewLabel.setIcon(null);
            previewLabel.setText("No preview");
            detailArea.setText("");
            return;
        }

        detailArea.setText("Type: " + selected.type().label()
                + "\nPath: " + selected.assetPath()
                + "\nFile: " + selected.sourcePath().toAbsolutePath().normalize());
        detailArea.setCaretPosition(0);

        if (selected.type() != AssetBrowserType.IMAGES) {
            previewLabel.setIcon(null);
            previewLabel.setText(selected.type().label());
            return;
        }

        BufferedImage image = AssetLoader.loadImage(selected.assetPath());
        if (image == null) {
            previewLabel.setIcon(null);
            previewLabel.setText("Image failed to load");
            return;
        }

        int maxWidth = Math.max(1, previewLabel.getWidth() - 24);
        int maxHeight = Math.max(1, previewLabel.getHeight() - 42);
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        scale = Math.min(8.0, Math.max(0.1, scale));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_FAST);
        previewLabel.setText("");
        previewLabel.setIcon(new ImageIcon(scaled));
    }

    private JButton menuButton(String label, JPopupMenu menu) {
        JButton button = new JButton(label);
        button.addActionListener(event -> menu.show(button, 0, button.getHeight()));
        return button;
    }

    private void addMenuItem(JPopupMenu menu, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    private void manageLevelGates() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTabbedPane tabs = new JTabbedPane();
        Map<String, JSpinner> intSpinners = new LinkedHashMap<>();
        Map<String, JSpinner> doubleSpinners = new LinkedHashMap<>();

        JPanel equipmentTab = new JPanel(new BorderLayout(6, 6));
        JComboBox<CharacterSkill> equipmentSkillBox = new JComboBox<>(CharacterSkill.values());
        equipmentSkillBox.setSelectedItem(CharacterSkill.DEFENSE);
        JPanel equipmentFields = configGridPanel();
        Runnable rebuildEquipmentFields = () -> {
            equipmentFields.removeAll();
            equipmentFields.add(new JLabel("Material"));
            equipmentFields.add(new JLabel("Required Level"));
            equipmentFields.add(new JLabel("Key"));
            CharacterSkill selectedSkill = (CharacterSkill) equipmentSkillBox.getSelectedItem();
            for (GearMaterial material : GearMaterial.values()) {
                String key = EquipmentRequirementRules.configurationKey(selectedSkill, material);
                JSpinner spinner = intSpinners.computeIfAbsent(key, ignored -> new JSpinner(
                        new SpinnerNumberModel(EquipmentRequirementRules.requiredLevel(selectedSkill, material),
                                1, 100, 1)));
                equipmentFields.add(new JLabel(material.getDisplayName()
                        + (GameConfiguration.hasValue(key) ? "" : " (fallback)")));
                equipmentFields.add(spinner);
                equipmentFields.add(new JLabel(key));
            }
            equipmentFields.revalidate();
            equipmentFields.repaint();
        };
        equipmentSkillBox.addActionListener(event -> rebuildEquipmentFields.run());
        rebuildEquipmentFields.run();
        equipmentTab.add(formRow("Equipment Skill", equipmentSkillBox), BorderLayout.NORTH);
        equipmentTab.add(new JScrollPane(equipmentFields), BorderLayout.CENTER);
        tabs.addTab("Equipment", equipmentTab);

        JPanel resourceFields = configGridPanel();
        resourceFields.add(new JLabel("Resource Tuning"));
        resourceFields.add(new JLabel("Value"));
        resourceFields.add(new JLabel("Key"));
        addIntConfigRow(resourceFields, intSpinners, "Gathering Attempt MS", "resource.gatheringAttemptIntervalMs",
                GameConfiguration.intValue("resource.gatheringAttemptIntervalMs", 2500), 1, 600_000);
        addIntConfigRow(resourceFields, intSpinners, "Resource Respawn MS", "resource.respawnMs",
                GameConfiguration.intValue("resource.respawnMs", 300_000), 1, 3_600_000);
        addIntConfigRow(resourceFields, intSpinners, "Attempts Per Exhaustion Roll",
                "resource.attemptsPerExhaustionRoll",
                GameConfiguration.intValue("resource.attemptsPerExhaustionRoll", 2), 1, 100);
        addIntConfigRow(resourceFields, intSpinners, "Max Exhaustion Level", "resource.maxExhaustionLevel",
                GameConfiguration.intValue("resource.maxExhaustionLevel", 2), 0, 10);
        addDoubleConfigRow(resourceFields, doubleSpinners, "Exhaustion Chance", "resource.exhaustionChance",
                GameConfiguration.doubleValue("resource.exhaustionChance", 0.50), 0.0, 1.0, 0.05);
        tabs.addTab("Resources", new JScrollPane(resourceFields));

        JPanel skillFields = configGridPanel();
        skillFields.add(new JLabel("Skill Tuning"));
        skillFields.add(new JLabel("Value"));
        skillFields.add(new JLabel("Key"));
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Base Chance", "fishing.baseSuccessChance",
                GameConfiguration.doubleValue("fishing.baseSuccessChance", 0.35), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Chance Per Level", "fishing.successChancePerLevel",
                GameConfiguration.doubleValue("fishing.successChancePerLevel", 0.03), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Max Chance", "fishing.maxSuccessChance",
                GameConfiguration.doubleValue("fishing.maxSuccessChance", 0.85), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Fishing XP", "fishing.xpReward",
                GameConfiguration.intValue("fishing.xpReward", 18), 0, 10_000);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Base Chance", "mining.baseSuccessChance",
                GameConfiguration.doubleValue("mining.baseSuccessChance", 0.40), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Chance Per Level", "mining.successChancePerLevel",
                GameConfiguration.doubleValue("mining.successChancePerLevel", 0.03), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Max Chance", "mining.maxSuccessChance",
                GameConfiguration.doubleValue("mining.maxSuccessChance", 0.88), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Mining XP", "mining.xpReward",
                GameConfiguration.intValue("mining.xpReward", 18), 0, 10_000);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Base Chance", "cooking.baseSuccessChance",
                GameConfiguration.doubleValue("cooking.baseSuccessChance", 0.45), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Chance Per Level", "cooking.successChancePerLevel",
                GameConfiguration.doubleValue("cooking.successChancePerLevel", 0.035), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Max Chance", "cooking.maxSuccessChance",
                GameConfiguration.doubleValue("cooking.maxSuccessChance", 0.90), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Cooking XP", "cooking.xpReward",
                GameConfiguration.intValue("cooking.xpReward", 20), 0, 10_000);
        tabs.addTab("Skill Rates", new JScrollPane(skillFields));

        JPanel smithingFields = new JPanel(new java.awt.GridLayout(0, 4, 8, 6));
        smithingFields.add(new JLabel("Material"));
        smithingFields.add(new JLabel("XP Per Bar"));
        smithingFields.add(new JLabel("Configuration Key"));
        smithingFields.add(new JLabel("Calculated Examples"));
        for (GearMaterial material : SmithingExperienceRules.smithingMaterials()) {
            String key = SmithingExperienceRules.configurationKey(material);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    SmithingExperienceRules.xpPerBar(material), 0, 100_000, 1));
            JLabel examples = new JLabel();
            Runnable refreshExamples = () -> {
                int perBar = ((Number) spinner.getValue()).intValue();
                examples.setText("1: " + perBar
                        + "  2: " + perBar * 2
                        + "  3: " + perBar * 3
                        + "  5: " + perBar * 5);
            };
            spinner.addChangeListener(event -> refreshExamples.run());
            refreshExamples.run();
            intSpinners.put(key, spinner);
            smithingFields.add(new JLabel(material.getDisplayName()));
            smithingFields.add(spinner);
            smithingFields.add(new JLabel(key));
            smithingFields.add(examples);
        }
        tabs.addTab("Smithing XP", new JScrollPane(smithingFields));

        JTextArea note = new JTextArea(
                "These values are saved to the packaged configuration and mirrored to the editable runtime configuration."
                        + " Equipment gates are selected by skill and material; Smithing XP is calculated from material XP per bar multiplied by bars used.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        panel.add(tabs, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Level Gates");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Properties properties = loadPackagedConfigurationProperties();
        Map<String, String> updatedValues = new LinkedHashMap<>();
        for (Map.Entry<String, JSpinner> entry : intSpinners.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(Math.max(0, ((Number) entry.getValue().getValue()).intValue()));
            properties.setProperty(key, value);
            updatedValues.put(key, value);
        }
        for (Map.Entry<String, JSpinner> entry : doubleSpinners.entrySet()) {
            String key = entry.getKey();
            String value = formatConfigNumber(((Number) entry.getValue().getValue()).doubleValue());
            properties.setProperty(key, value);
            updatedValues.put(key, value);
        }

        try {
            writePropertiesAtomically(CONFIG_RESOURCE_PATH, properties,
                    "Aether packaged gameplay configuration");
            for (Map.Entry<String, String> entry : updatedValues.entrySet()) {
                GameConfiguration.setValue(entry.getKey(), entry.getValue());
            }
            setStatus("Updated level gate configuration.");
        } catch (IOException exception) {
            setStatus("Level gate configuration save failed: " + exception.getMessage());
        }
    }

    private void manageAnimations() {
        String[] toolPrefixes = { "mining", "woodcutting", "fishing" };
        String[] toolNames = { "Pickaxe", "Axe", "Fishing Rod" };
        String[] toolModels = {
                "assets/3D/gatheringTool/toReplace_pickaxe.glb",
                "assets/3D/gatheringTool/toReplace_axe.glb",
                "assets/3D/gatheringTool/toReplace_fishing_rod_stick.glb"
        };

        Map<String, JSpinner> allSpinners = new LinkedHashMap<>();
        Map<String, JTextField> allSoundFields = new LinkedHashMap<>();
        JTabbedPane tabs = new JTabbedPane();
        List<GatheringToolPreviewPanel> previews = new ArrayList<>();

        for (int i = 0; i < toolPrefixes.length; i++) {
            String prefix = toolPrefixes[i];
            JPanel controls = configGridPanel();

            controls.add(new JLabel("— Orientation —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            addDoubleConfigRow(controls, allSpinners, "Rotation X (°)", prefix + ".viewModel.rotationX",
                    GameConfiguration.doubleValue(prefix + ".viewModel.rotationX", -18.0), -360.0, 360.0, 1.0);
            addDoubleConfigRow(controls, allSpinners, "Rotation Y (°)", prefix + ".viewModel.rotationY",
                    GameConfiguration.doubleValue(prefix + ".viewModel.rotationY", 0.0), -360.0, 360.0, 1.0);
            addDoubleConfigRow(controls, allSpinners, "Rotation Z (°)", prefix + ".viewModel.rotationZ",
                    GameConfiguration.doubleValue(prefix + ".viewModel.rotationZ", -24.0), -360.0, 360.0, 1.0);

            controls.add(new JLabel("— Position —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            addDoubleConfigRow(controls, allSpinners, "Position X", prefix + ".viewModel.positionX",
                    GameConfiguration.doubleValue(prefix + ".viewModel.positionX", 0.42), -5.0, 5.0, 0.02);
            addDoubleConfigRow(controls, allSpinners, "Position Y", prefix + ".viewModel.positionY",
                    GameConfiguration.doubleValue(prefix + ".viewModel.positionY", -0.46), -5.0, 5.0, 0.02);
            addDoubleConfigRow(controls, allSpinners, "Position Z", prefix + ".viewModel.positionZ",
                    GameConfiguration.doubleValue(prefix + ".viewModel.positionZ", -0.92), -5.0, 5.0, 0.02);

            controls.add(new JLabel("— Swing Axis —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            addDoubleConfigRow(controls, allSpinners, "Swing Axis X", prefix + ".viewModel.swingAxisX",
                    GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisX", 0.0), -1.0, 1.0, 0.1);
            addDoubleConfigRow(controls, allSpinners, "Swing Axis Y", prefix + ".viewModel.swingAxisY",
                    GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisY", 0.0), -1.0, 1.0, 0.1);
            addDoubleConfigRow(controls, allSpinners, "Swing Axis Z", prefix + ".viewModel.swingAxisZ",
                    GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisZ", 1.0), -1.0, 1.0, 0.1);

            controls.add(new JLabel("— Animation —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            addDoubleConfigRow(controls, allSpinners, "Windup Degrees", prefix + ".viewModel.windupDegrees",
                    GameConfiguration.doubleValue(prefix + ".viewModel.windupDegrees", 25.0), -180.0, 180.0, 1.0);
            addDoubleConfigRow(controls, allSpinners, "Success Strike Deg", prefix + ".viewModel.successDegrees",
                    GameConfiguration.doubleValue(prefix + ".viewModel.successDegrees", -55.0), -180.0, 180.0, 1.0);
            addDoubleConfigRow(controls, allSpinners, "Failure Strike Deg", prefix + ".viewModel.failureDegrees",
                    GameConfiguration.doubleValue(prefix + ".viewModel.failureDegrees", -20.0), -180.0, 180.0, 1.0);
            addDoubleConfigRow(controls, allSpinners, "Success Penetration", prefix + ".viewModel.successPenetration",
                    GameConfiguration.doubleValue(prefix + ".viewModel.successPenetration", 0.32), 0.0, 2.0, 0.02);
            addDoubleConfigRow(controls, allSpinners, "Failure Penetration", prefix + ".viewModel.failurePenetration",
                    GameConfiguration.doubleValue(prefix + ".viewModel.failurePenetration", 0.10), 0.0, 2.0, 0.02);

            controls.add(new JLabel("— Scale —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            addDoubleConfigRow(controls, allSpinners, "Scale Height", prefix + ".viewModel.height",
                    GameConfiguration.doubleValue(prefix + ".viewModel.height", 0.76), 0.05, 5.0, 0.05);

            controls.add(new JLabel("— Sounds —"));
            controls.add(new JLabel(""));
            controls.add(new JLabel(""));
            JTextField successSound = new JTextField(GameConfiguration.stringValue(prefix + ".success.soundPath", ""),
                    22);
            JTextField failureSound = new JTextField(GameConfiguration.stringValue(prefix + ".failure.soundPath", ""),
                    22);
            allSoundFields.put(prefix + ".success.soundPath", successSound);
            allSoundFields.put(prefix + ".failure.soundPath", failureSound);
            addSoundPathRow(controls, "Success", successSound);
            addSoundPathRow(controls, "Failure", failureSound);

            GatheringToolPreviewPanel preview = new GatheringToolPreviewPanel(
                    toolNames[i], toolModels[i], prefix, allSpinners);
            previews.add(preview);

            JPanel toolTab = new JPanel(new BorderLayout(10, 10));
            toolTab.add(new JScrollPane(controls), BorderLayout.WEST);
            toolTab.add(preview, BorderLayout.CENTER);
            tabs.addTab(toolNames[i], toolTab);
        }

        JTextArea note = new JTextArea(
                "Adjust orientation, position, and animation parameters for each gathering tool."
                        + " Changes are saved to the packaged configuration and take effect at runtime.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Gathering Tool Animations");

        previews.forEach(GatheringToolPreviewPanel::stopPreview);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Properties properties = loadPackagedConfigurationProperties();
        for (Map.Entry<String, JSpinner> entry : allSpinners.entrySet()) {
            String key = entry.getKey();
            String value = formatSignedConfigNumber(((Number) entry.getValue().getValue()).doubleValue());
            properties.setProperty(key, value);
            GameConfiguration.setValue(key, value);
        }
        for (Map.Entry<String, JTextField> entry : allSoundFields.entrySet()) {
            String value = entry.getValue().getText().trim();
            properties.setProperty(entry.getKey(), value);
            GameConfiguration.setValue(entry.getKey(), value);
        }

        try {
            Files.createDirectories(CONFIG_RESOURCE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_RESOURCE_PATH)) {
                properties.store(outputStream, "Aether packaged gameplay configuration");
            }
            setStatus("Updated animation configuration.");
        } catch (IOException exception) {
            setStatus("Animation configuration save failed: " + exception.getMessage());
        }
    }

    private JPanel configGridPanel() {

        return new JPanel(new java.awt.GridLayout(0, 3, 8, 6));
    }

    private void addIntConfigRow(
            JPanel fields,
            Map<String, JSpinner> spinners,
            String label,
            String key,
            int currentValue,
            int minimum,
            int maximum) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(currentValue, minimum, maximum, 1));
        spinners.put(key, spinner);
        fields.add(new JLabel(label));
        fields.add(spinner);
        fields.add(new JLabel(key));
    }

    private void addDoubleConfigRow(
            JPanel fields,
            Map<String, JSpinner> spinners,
            String label,
            String key,
            double currentValue,
            double minimum,
            double maximum,
            double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(currentValue, minimum, maximum, step));
        spinners.put(key, spinner);
        fields.add(new JLabel(label));
        fields.add(spinner);
        fields.add(new JLabel(key));
    }

    private Properties loadPackagedConfigurationProperties() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(CONFIG_RESOURCE_PATH)) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(CONFIG_RESOURCE_PATH)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            setStatus("Ability configuration load warning: " + exception.getMessage());
        }
        return properties;
    }

    private void writePropertiesAtomically(Path target, Properties properties, String comment) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, comment);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String formatConfigNumber(double value) {
        double safeValue = Math.max(0.0, value);
        if (Math.rint(safeValue) == safeValue) {
            return String.valueOf((long) safeValue);
        }
        return String.format(java.util.Locale.US, "%.2f", safeValue).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private JPanel modelPathBrowser(JTextField field) {
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> showAssetBrowser(field, AssetBrowserType.MODELS));
        return pathFieldPanel(field, browse);
    }

    private static double tableNumber(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String safeText(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String placementRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "Unknown placement error";
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String normalizedPath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private static String normalizeContentId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private void putConfiguration(Properties properties, String key, String value) {
        String safe = value == null ? "" : value;
        properties.setProperty(key, safe);
        GameConfiguration.setValue(key, safe);
    }

    private static String formatSignedConfigNumber(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.4f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private void installKeyboardShortcuts() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        bindShortcut(inputMap, actionMap, "save-map", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
                true, this::saveMap);
        bindShortcut(inputMap, actionMap, "load-map", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
                true, this::loadMap);
        bindShortcut(inputMap, actionMap, "validate-map", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), true,
                this::validateMap);
        bindShortcut(inputMap, actionMap, "undo-map", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
                false, this::undoMapEdit);
        bindShortcut(inputMap, actionMap, "redo-map", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
                false, this::redoMapEdit);
        bindShortcut(inputMap, actionMap, "redo-map-shift",
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false,
                this::redoMapEdit);
        bindShortcut(inputMap, actionMap, "delete-selection", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), false,
                this::deleteSelectedContent);
        bindShortcut(inputMap, actionMap, "duplicate-selection",
                KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), false,
                this::duplicateSelectedContent);
        bindShortcut(inputMap, actionMap, "find-selection",
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), false,
                this::findSelectedContentOnMap);
        bindShortcut(inputMap, actionMap, "paint-selection",
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), false,
                this::selectContentForPlacement);
    }

    private void bindShortcut(InputMap inputMap, ActionMap actionMap, String id, KeyStroke keyStroke,
            boolean allowWhileTyping, Runnable action) {
        inputMap.put(keyStroke, id);
        actionMap.put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!allowWhileTyping && isTextEditingFocus()) {
                    return;
                }
                action.run();
            }
        });
    }

    private boolean isTextEditingFocus() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextComponent;
    }

    private void captureHistory(String reason) {
        undoStack.push(copyDesign(design));
        while (undoStack.size() > MAX_HISTORY_STATES) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateHistoryButtons();
    }

    private void undoMapEdit() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(copyDesign(design));
        design = undoStack.pop();
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Undid map edit.");
    }

    private void redoMapEdit() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(copyDesign(design));
        design = redoStack.pop();
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Redid map edit.");
    }

    private void syncEditorFromDesign() {
        normalizeThemeIndexesToPrimary();
        mapNameField.setText(design.displayName());
        widthSpinner.setValue(design.width());
        heightSpinner.setValue(design.height());
        primaryThemeBox.setSelectedItem(design.primaryTheme());
        populatePlaceables();
        populateBrushes();
        populateMobAreas();
        refreshContentBrowser();
        mapCanvas.revalidate();
        mapCanvas.repaint();
        updateHistoryButtons();
    }

    private void normalizeThemeIndexesToPrimary() {
        if (design == null || design.themeIndexes() == null) {
            return;
        }

        for (int y = 0; y < design.themeIndexes().length; y++) {
            if (design.themeIndexes()[y] == null) {
                continue;
            }
            for (int x = 0; x < design.themeIndexes()[y].length; x++) {
                design.themeIndexes()[y][x] = 0;
            }
        }
    }

    private void markDirty(boolean dirty) {
        this.dirty = dirty;
        setTitle("Aether Construction Kit" + (dirty ? " *" : ""));
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        undoButton.setEnabled(!undoStack.isEmpty());
        redoButton.setEnabled(!redoStack.isEmpty());
    }

    private void updateMapZoom() {
        mapCanvas.revalidate();
        mapCanvas.repaint();
        if (inspectedTile != null) {
            mapCanvas.scrollToTile(inspectedTile.x, inspectedTile.y);
        }
    }

    private void offerAutosaveRecovery() {
        if (!Files.isRegularFile(AUTOSAVE_PATH)) {
            return;
        }

        int result = showAdaptiveTextConfirmDialog(
                this,
                "A Construction Kit recovery file was found.\n\nLoad it now?",
                "Recover Unsaved Map",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            design = MapDesignLibrary.load(AUTOSAVE_PATH);
            loadSharedContentIntoDesign();
            undoStack.clear();
            redoStack.clear();
            syncEditorFromDesign();
            markDirty(true);
            setStatus("Loaded autosave recovery file.");
        } catch (Exception exception) {
            setStatus("Recovery load failed: " + exception.getMessage());
        }
    }

    private void autosaveRecovery() {
        if (!dirty) {
            return;
        }

        try {
            Files.createDirectories(AUTOSAVE_PATH.getParent());
            syncThemes();
            MapDesignLibrary.save(design, AUTOSAVE_PATH);
        } catch (Exception exception) {
            setStatus("Autosave failed: " + exception.getMessage());
        }
    }

    private void clearAutosaveRecovery() {
        try {
            Files.deleteIfExists(AUTOSAVE_PATH);
        } catch (IOException exception) {
            setStatus("Autosave cleanup failed: " + exception.getMessage());
        }
    }

    private MapDesignLibrary.MapDesign copyDesign(MapDesignLibrary.MapDesign source) {
        Library.TileType[][] tiles = new Library.TileType[source.height()][source.width()];
        for (int y = 0; y < source.height(); y++) {
            System.arraycopy(source.tiles()[y], 0, tiles[y], 0, source.width());
        }
        int[][] themeIndexes = new int[source.height()][source.width()];
        for (int y = 0; y < source.height(); y++) {
            System.arraycopy(source.themeIndexes()[y], 0, themeIndexes[y], 0, source.width());
        }

        return new MapDesignLibrary.MapDesign(
                source.width(),
                source.height(),
                source.displayName(),
                source.description(),
                source.musicPath(),
                source.skyboxPath(),
                source.primaryTheme(),
                source.primaryTheme(),
                tiles,
                themeIndexes,
                source.mapPaint() == null
                        ? MapPaintData.blank(source.width(), source.height())
                        : source.mapPaint().copy(),
                source.mapGeometry() == null
                        ? MapGeometryData.blank(source.width(), source.height())
                        : source.mapGeometry().copy(),
                source.mobAreas() == null
                        ? MobAreaData.blank(source.width(), source.height())
                        : source.mobAreas().copy(),
                new ArrayList<>(source.placements()),
                new ArrayList<>(source.placedObjects()),
                new ArrayList<>(source.authoredDialogues()),
                new ArrayList<>(source.authoredQuests()),
                new ArrayList<>(source.customItems()),
                new ArrayList<>(source.customMobs()),
                new ArrayList<>(source.customLimbs()),
                new ArrayList<>(source.customNpcs()),
                new ArrayList<>(source.customFurniture()),
                new ArrayList<>(source.customGatheringNodes()),
                new ArrayList<>(source.customCookingRecipes()),
                new ArrayList<>(source.craftingRecipes()),
                new ArrayList<>(source.triggers()),
                source.lightingSettings(),
                new ArrayList<>(source.lights()),
                source.spawnX(),
                source.spawnY());
    }

    private void populatePlaceables() {
        PlaceableCategory selectedCategory = (PlaceableCategory) placeableCategoryBox.getSelectedItem();
        if (selectedCategory == null) {
            selectedCategory = PlaceableCategory.ITEMS;
        }
        placeableBox.removeAllItems();
        for (PlaceableOption option : sortedPlaceableOptions(placeableOptionsFor(selectedCategory, true))) {
            placeableBox.addItem(option);
        }
    }

    private void populateBrushes() {
        PaintBrushLibrary.PaintBrush previous = (PaintBrushLibrary.PaintBrush) brushBox.getSelectedItem();
        PaintBrushLibrary.Palette palette = (PaintBrushLibrary.Palette) paletteBox.getSelectedItem();
        MapPaintData.Layer layer = layerForPaintMode((PaintMode) paintModeBox.getSelectedItem());
        String paletteId = palette == null ? "" : palette.id();
        List<PaintBrushLibrary.PaintBrush> brushes = layer == null
                ? PaintBrushLibrary.brushesForPalette(paletteId)
                : PaintBrushLibrary.brushesForPaletteAndLayer(paletteId, layer);

        brushBox.setModel(new DefaultComboBoxModel<>(brushes.toArray(new PaintBrushLibrary.PaintBrush[0])));
        if (previous != null) {
            for (PaintBrushLibrary.PaintBrush brush : brushes) {
                if (brush.id().equals(previous.id())) {
                    brushBox.setSelectedItem(brush);
                    return;
                }
            }
        }
    }

    private void populateMobAreas() {
        String selected = (String) mobAreaBox.getSelectedItem();
        java.util.Set<String> ids = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (design.mobAreas() != null) {
            ids.addAll(design.mobAreas().areaIds());
        }
        for (MapDesignLibrary.MapDesign neighbor : worldNeighborDesigns.values()) {
            if (neighbor != null && neighbor.mobAreas() != null) {
                ids.addAll(neighbor.mobAreas().areaIds());
            }
        }
        mobAreaBox.setModel(new DefaultComboBoxModel<>(ids.toArray(new String[0])));
        if (selected != null && !selected.isBlank()) {
            if (!ids.contains(selected)) {
                mobAreaBox.addItem(selected);
            }
            mobAreaBox.setSelectedItem(selected);
        }
    }

    private void createMobArea() {
        JTextField nameField = new JTextField("Mob Area", 24);
        if (showScrollableFormDialog(formRow("Area Name", nameField), "Create Mob Area") != JOptionPane.OK_OPTION) {
            return;
        }
        String areaId = safeId(nameField.getText());
        if (areaId.isBlank()) {
            setStatus("Mob area needs a name.");
            return;
        }
        boolean exists = false;
        for (int i = 0; i < mobAreaBox.getItemCount(); i++) {
            if (areaId.equalsIgnoreCase(mobAreaBox.getItemAt(i))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            mobAreaBox.addItem(areaId);
        }
        mobAreaBox.setSelectedItem(areaId);
        paintModeBox.setSelectedItem(PaintMode.MOB_AREA);
        setStatus("Created mob area " + areaId + ". Paint its allowed tiles.");
    }

    private void editMobArea(MobAreaEntry area) {
        JTextField nameField = new JTextField(area.areaId(), 24);
        if (showScrollableFormDialog(formRow("Area Name", nameField), "Rename Mob Area") != JOptionPane.OK_OPTION) {
            return;
        }
        String replacement = safeId(nameField.getText());
        if (replacement.isBlank() || replacement.equals(area.areaId())) {
            return;
        }
        if (activeWorld != null && showAdaptiveTextConfirmDialog(
                this,
                "Rename mob area '" + area.areaId() + "' to '" + replacement
                        + "' in every chunk in this world?",
                "Rename Mob Area",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        replaceMobAreaId(area.areaId(), replacement);
        populateMobAreas();
        refreshContentBrowser();
        markDirty(true);
    }

    private void deleteMobArea(MobAreaEntry area) {
        if (showAdaptiveTextConfirmDialog(
                this,
                "Clear mob area '" + area.areaId() + "'"
                        + (activeWorld == null ? " from this map?" : " from every chunk in this world?"),
                "Delete Mob Area",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        replaceMobAreaId(area.areaId(), "");
        populateMobAreas();
        refreshContentBrowser();
        markDirty(true);
    }

    private void replaceMobAreaId(String sourceId, String replacementId) {
        replaceMobAreaId(design, sourceId, replacementId);
        if (activeWorld == null || currentWorldManifestPath == null) {
            return;
        }
        for (Map.Entry<ChunkCoordinate, String> entry : activeWorld.chunks().entrySet()) {
            if (entry.getKey().equals(activeWorldChunk)) {
                continue;
            }
            try {
                Path path = WorldManifestLibrary.resolveChunkPath(currentWorldManifestPath, entry.getValue());
                MapDesignLibrary.MapDesign chunk = MapDesignLibrary.load(path);
                if (replaceMobAreaId(chunk, sourceId, replacementId)) {
                    MapDesignLibrary.save(chunk, path);
                }
            } catch (IOException exception) {
                setStatus("Mob area update failed for chunk " + entry.getKey() + ": " + exception.getMessage());
            }
        }
        refreshWorldNeighbors();
    }

    private boolean replaceMobAreaId(
            MapDesignLibrary.MapDesign target,
            String sourceId,
            String replacementId) {
        boolean changed = false;
        for (int y = 0; y < target.height(); y++) {
            for (int x = 0; x < target.width(); x++) {
                if (sourceId.equals(target.mobAreas().get(x, y))) {
                    target.mobAreas().set(x, y, replacementId);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private MapPaintData.Layer layerForPaintMode(PaintMode mode) {
        if (mode == null) {
            return null;
        }

        return switch (mode) {
            case FLOOR_BRUSH -> MapPaintData.Layer.FLOOR;
            case WALL_BRUSH -> MapPaintData.Layer.WALL;
            case DOOR_BRUSH -> MapPaintData.Layer.DOOR;
            case ROOF_BRUSH -> MapPaintData.Layer.ROOF;
            default -> null;
        };
    }

    private List<PlaceableOption> sortedPlaceableOptions(List<PlaceableOption> options) {
        List<PlaceableOption> sortedOptions = new ArrayList<>(options);
        sortedOptions.sort(Comparator
                .comparing((PlaceableOption option) -> option.kind() == null ? 0 : 1)
                .thenComparing(PlaceableOption::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlaceableOption::id, String.CASE_INSENSITIVE_ORDER));
        return sortedOptions;
    }

    private List<PlaceableOption> placeableOptionsFor(PlaceableCategory selectedCategory, boolean includeNone) {
        List<PlaceableOption> options = new ArrayList<>();
        if (includeNone) {
            options.add(new PlaceableOption("None", null, ""));
        }

        for (CraftingStationType node : CraftingStationType.values()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Crafting: " + node.name(),
                    MapDesignLibrary.PlacementKind.CRAFTING_NODE,
                    node.name()));
        }

        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Custom NPC: " + npc.displayName(),
                    MapDesignLibrary.PlacementKind.CUSTOM_NPC, npc.npcId()));
        }

        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Gathering: " + node.displayName(),
                    MapDesignLibrary.PlacementKind.GATHERING_NODE,
                    node.nodeId()));
        }

        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Furniture: " + furniture.displayName(),
                    MapDesignLibrary.PlacementKind.FURNITURE,
                    furniture.furnitureId()));
        }

        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Item: " + item.displayName(),
                    MapDesignLibrary.PlacementKind.ITEM, item.itemId()));
        }

        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Custom Limb: " + limb.displayName(),
                    MapDesignLibrary.PlacementKind.ITEM, limb.limbId()));
        }

        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Enemy: " + mob.displayName(),
                    MapDesignLibrary.PlacementKind.ENEMY,
                    mob.mobId()));
        }

        for (InteractionSystem.EditorInteractionDefinition interaction : InteractionSystem.EDITOR_INTERACTIONS) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Interaction: " + interaction.displayName(),
                    MapDesignLibrary.PlacementKind.INTERACTION,
                    interaction.interactionId()));
        }

        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                    && placement.id().startsWith("map_link|")) {
                addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                        "Map Link: " + mapLinkLabel(placement.id()),
                        MapDesignLibrary.PlacementKind.INTERACTION,
                        placement.id()));
            }
        }
        return options;
    }

    private List<PlaceableOption> transformedObjectOptions() {
        List<PlaceableOption> options = new ArrayList<>();
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            options.add(new PlaceableOption(
                    "Furniture: " + furniture.displayName(),
                    MapDesignLibrary.PlacementKind.FURNITURE,
                    furniture.furnitureId()));
        }
        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            if (node.modelPaths().isEmpty()) {
                continue;
            }
            options.add(new PlaceableOption(
                    "3D Gathering: " + node.displayName(),
                    MapDesignLibrary.PlacementKind.GATHERING_NODE,
                    node.nodeId()));
        }
        return sortedPlaceableOptions(options);
    }

    private List<PlaceableOption> transformedObjectOptionsFor(MapDesignLibrary.PlacedObjectInstance object) {
        List<PlaceableOption> options = new ArrayList<>(transformedObjectOptions());
        if (object == null || object.id().isBlank()) {
            return options;
        }
        boolean hasCurrent = options.stream()
                .anyMatch(option -> option.kind() == object.kind() && object.id().equals(option.id()));
        if (!hasCurrent) {
            options.add(new PlaceableOption(
                    "Current / Missing: " + object.kind() + " " + object.id(),
                    object.kind(),
                    object.id()));
        }
        return options;
    }

    private boolean isTransformablePlaceable(PlaceableOption option) {
        if (option == null || option.kind() == null || option.id().isBlank()) {
            return false;
        }
        if (option.kind() == MapDesignLibrary.PlacementKind.FURNITURE) {
            return true;
        }
        if (option.kind() != MapDesignLibrary.PlacementKind.GATHERING_NODE) {
            return false;
        }
        MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(option.id());
        return node != null && !node.modelPaths().isEmpty();
    }

    private boolean defaultBlocksMovementFor(PlaceableOption option) {
        if (option == null || option.kind() == null) {
            return false;
        }
        if (option.kind() == MapDesignLibrary.PlacementKind.FURNITURE) {
            MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(option.id());
            return furniture != null && furniture.defaultBlocksMovement();
        }
        if (option.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE) {
            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(option.id());
            return node != null && node.nodeType() != MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
        }
        return false;
    }

    private void addPlaceableIfSelected(List<PlaceableOption> options, PlaceableCategory category,
            PlaceableOption option) {
        if (category != null && category.includes(option)) {
            options.add(option);
        }
    }

    private void refreshContentBrowser() {
        if (contentModel == null) {
            return;
        }

        ContentEntry selected = contentList.getSelectedValue();
        String selectedKey = selected == null ? "" : selected.key();
        ContentCategory category = (ContentCategory) contentCategoryBox.getSelectedItem();
        if (category == null) {
            category = ContentCategory.ALL;
        }
        ContentCategory selectedCategory = category;
        String filter = contentSearchField.getText() == null
                ? ""
                : contentSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);

        List<ContentEntry> entries = buildContentEntries().stream()
                .filter(entry -> selectedCategory == ContentCategory.ALL || entry.category() == selectedCategory)
                .filter(entry -> filter.isBlank() || entry.searchText().contains(filter))
                .sorted(Comparator
                        .comparing((ContentEntry entry) -> entry.category().label(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ContentEntry::label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ContentEntry::id, String.CASE_INSENSITIVE_ORDER))
                .toList();

        contentModel.clear();
        int selectedIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            ContentEntry entry = entries.get(i);
            contentModel.addElement(entry);
            if (entry.key().equals(selectedKey)) {
                selectedIndex = i;
            }
        }

        if (contentModel.isEmpty()) {
            inspectorArea.setText("No matching content.");
            return;
        }
        contentList.setSelectedIndex(selectedIndex >= 0 ? selectedIndex : 0);
        updateInspector();
    }

    private void revealContentEntry(Object value, ContentCategory category) {
        if (value == null || category == null) {
            return;
        }

        if (!contentSearchField.getText().isBlank()) {
            contentSearchField.setText("");
        }
        contentCategoryBox.setSelectedItem(category);
        refreshContentBrowser();

        for (int i = 0; i < contentModel.size(); i++) {
            ContentEntry entry = contentModel.get(i);
            if (entry.value() == value) {
                contentList.setSelectedIndex(i);
                contentList.ensureIndexIsVisible(i);
                updateInspector();
                return;
            }
        }
    }

    private List<ContentEntry> buildContentEntries() {
        List<ContentEntry> entries = new ArrayList<>();
        for (MaterialDefinition material : MaterialCatalog.snapshot().definitions()) {
            if (!material.id().equals("none")) {
                entries.add(new ContentEntry(ContentCategory.MATERIALS, material.displayName(),
                        material.id(), "Material", material));
            }
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            entries.add(new ContentEntry(ContentCategory.ITEMS, item.displayName(), item.itemId(), "Item", item));
        }
        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            entries.add(new ContentEntry(ContentCategory.ENEMIES, mob.displayName(), mob.mobId(), "Enemy", mob));
        }
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            entries.add(new ContentEntry(ContentCategory.NPCS, npc.displayName(), npc.npcId(), "NPC", npc));
        }
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            entries.add(new ContentEntry(
                    ContentCategory.FURNITURE,
                    furniture.displayName(),
                    furniture.furnitureId(),
                    "Furniture",
                    furniture));
        }
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            entries.add(new ContentEntry(ContentCategory.LIMBS, limb.displayName(), limb.limbId(), "Limb", limb));
        }
        for (SkillDefinition skill : BattleContentCatalog.current().skills().values()) {
            entries.add(new ContentEntry(
                    ContentCategory.BATTLE_SKILLS,
                    skill.displayName(),
                    skill.id(),
                    "Battle Skill",
                    skill));
        }
        for (StatusDefinition status : BattleContentCatalog.current().statuses().values()) {
            entries.add(new ContentEntry(
                    ContentCategory.STATUSES,
                    status.displayName(),
                    status.id(),
                    "Status",
                    status));
        }
        FirstPersonCombatLibrary.Content firstPerson = FirstPersonCombatLibrary.load();
        for (FirstPersonCombatLibrary.RigDefinition rig : firstPerson.rigs().values()) {
            entries.add(new ContentEntry(
                    ContentCategory.FIRST_PERSON_VIEWMODELS,
                    rig.displayName(),
                    rig.rigId(),
                    "First-Person Rig",
                    rig));
        }
        for (FirstPersonCombatLibrary.AnimationSet set : firstPerson.animationSets().values()) {
            entries.add(new ContentEntry(
                    ContentCategory.FIRST_PERSON_VIEWMODELS,
                    set.displayName(),
                    set.id(),
                    "First-Person Motion Set",
                    set));
        }
        for (FirstPersonCombatLibrary.ItemProfile profile : firstPerson.itemProfiles().values()) {
            entries.add(new ContentEntry(
                    ContentCategory.FIRST_PERSON_VIEWMODELS,
                    "Equipment " + profile.itemId(),
                    profile.itemId(),
                    "First-Person Equipment Profile",
                    profile));
        }
        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            entries.add(new ContentEntry(ContentCategory.GATHERING, node.displayName(), node.nodeId(), "Gathering Node",
                    node));
        }
        for (MapDesignLibrary.CustomCookingRecipe recipe : design.customCookingRecipes()) {
            entries.add(new ContentEntry(ContentCategory.COOKING, recipe.displayName(), recipe.recipeId(),
                    "Cooking Recipe", recipe));
        }
        for (MapDesignLibrary.CraftingRecipe recipe : design.craftingRecipes()) {
            entries.add(new ContentEntry(ContentCategory.CRAFTING_RECIPES, recipe.displayName(), recipe.recipeId(),
                    "Crafting Recipe", recipe));
        }
        for (MapDesignLibrary.AuthoredQuest quest : design.authoredQuests()) {
            entries.add(new ContentEntry(ContentCategory.QUESTS, quest.displayName(), quest.questId(), "Quest", quest));
        }
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            entries.add(new ContentEntry(ContentCategory.DIALOGUES, dialogue.speakerName(), dialogue.interactionId(),
                    "Dialogue", dialogue));
        }
        for (String areaId : design.mobAreas().areaIds()) {
            MobAreaEntry area = new MobAreaEntry(areaId);
            entries.add(new ContentEntry(ContentCategory.AREAS, areaId, areaId, "Mob Area", area));
        }
        for (MapLight light : design.lights()) {
            entries.add(new ContentEntry(ContentCategory.LIGHTS, light.id(), light.id(), "Light", light));
        }
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            entries.add(new ContentEntry(ContentCategory.TRIGGERS, trigger.id(), trigger.id(), "Trigger", trigger));
        }
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            String label = placement.kind() + " " + placement.id() + " @ " + placement.x() + "," + placement.y();
            entries.add(new ContentEntry(ContentCategory.PLACEMENTS, label, placement.id(), "Placement", placement));
        }
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            String label = placedObjectDisplayName(object) + " @ " + object.x() + "," + object.y()
                    + " [" + object.kind() + "]";
            entries.add(new ContentEntry(ContentCategory.PLACEMENTS, label, object.instanceId(), "Placed Object", object));
        }
        for (MapDesignLibrary.ValidationIssue issue : MapDesignLibrary.validate(design)) {
            entries.add(new ContentEntry(ContentCategory.DIAGNOSTICS, issue.toString(), issue.message(), "Diagnostic",
                    issue));
        }
        return entries;
    }

    private void updateInspector() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            inspectorArea.setText("No content selected.");
            clearMapSelection();
            return;
        }

        inspectorArea.setText(describeContent(entry));
        inspectorArea.setCaretPosition(0);
        updateMapSelectionFromContent(entry.value());
    }

    private void updateMapSelectionFromContent(Object value) {
        if (value instanceof MapDesignLibrary.MapPlacement placement) {
            setInspectedMapSelection(placement);
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            inspectedTile = new Point(object.x(), object.y());
            inspectedPlacement = null;
            inspectedPlacedObject = object;
            inspectedTrigger = null;
            inspectedLight = null;
            inspectedTriggerTarget = null;
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            inspectedTile = new Point(trigger.x(), trigger.y());
            inspectedPlacement = null;
            inspectedTrigger = trigger;
            inspectedLight = null;
            inspectedTriggerTarget = null;
        } else if (value instanceof MapLight light) {
            inspectedTile = new Point(light.x(), light.y());
            inspectedPlacement = null;
            inspectedTrigger = null;
            inspectedLight = light;
            inspectedTriggerTarget = null;
        } else {
            clearMapSelection();
            return;
        }
        mapCanvas.repaint();
    }

    private void setInspectedMapSelection(MapDesignLibrary.MapPlacement placement) {
        inspectedTile = new Point(placement.x(), placement.y());
        inspectedPlacement = placement;
        inspectedPlacedObject = null;
        inspectedTrigger = null;
        inspectedLight = null;
        inspectedTriggerTarget = null;
        mapCanvas.repaint();
    }

    private void clearMapSelection() {
        inspectedTile = null;
        inspectedPlacement = null;
        inspectedPlacedObject = null;
        inspectedTrigger = null;
        inspectedLight = null;
        inspectedTriggerTarget = null;
        mapCanvas.repaint();
    }

    private String describeContent(ContentEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.type()).append('\n');
        builder.append(entry.label()).append('\n');
        builder.append("Id: ").append(entry.id()).append("\n\n");
        appendContentDetails(builder, entry.value());
        appendReferences(builder, entry);
        return builder.toString();
    }

    private void appendContentDetails(StringBuilder builder, Object value) {
        if (value instanceof MaterialDefinition material) {
            builder.append("Family: ").append(material.family()).append('\n');
            builder.append("Order: ").append(material.sortOrder()).append('\n');
            builder.append("Stat bonus: ").append(material.statBonus()).append('\n');
            builder.append("Value multiplier: ").append(material.priceMultiplier()).append('\n');
            builder.append("Tint: ").append(material.tintHex())
                    .append(" @ ").append(material.tintStrength()).append('\n');
            if (!material.rawResourceItemId().isBlank()) {
                builder.append("Raw resource: ").append(material.rawResourceItemId()).append('\n');
            }
            if (!material.processedResourceItemId().isBlank()) {
                builder.append("Processed resource: ").append(material.processedResourceItemId()).append('\n');
            }
            if (material.family() == GearMaterial.MaterialFamily.METAL) {
                builder.append("Lantern capacity: ")
                        .append(LanternSystem.formatClock(material.lanternFuelCapacitySeconds() * 1_000L))
                        .append('\n');
            } else if (material.family() == GearMaterial.MaterialFamily.WOOD) {
                builder.append("Burn time per log: ")
                        .append(LanternSystem.formatClock(material.lanternBurnSecondsPerLog() * 1_000L))
                        .append('\n');
            }
        } else if (value instanceof MapDesignLibrary.CustomItem item) {
            builder.append("Type: ").append(item.itemType()).append('\n');
            builder.append("Material: ").append(item.material()).append('\n');
            if (item.equipmentSkill() != null) {
                builder.append("Requires: ").append(item.equipmentSkill().getDisplayName()).append(" level ")
                        .append(EquipmentRequirementRules.requiredLevel(item.equipmentSkill(), item.material()))
                        .append('\n');
            }
            if (item.itemType() == InventorySystem.ItemType.WEAPON) {
                builder.append("Weapon: ").append(item.weaponType())
                        .append(item.twoHanded() ? " two-handed" : "")
                        .append('\n');
            }
            if (item.itemType() == InventorySystem.ItemType.CONSUMABLE) {
                builder.append("Heal: ").append(item.healAmount()).append('\n');
            }
            if (item.smithingRecipeEnabled()) {
                builder.append("Smithing: ")
                        .append(item.smithingRequiredBars()).append(" bar(s), level ")
                        .append(item.smithingRequiredLevel()).append(", ")
                        .append(SmithingExperienceRules.calculate(
                                item.material(), item.smithingRequiredBars())).append(" xp\n");
            }
            if (item.itemType() == InventorySystem.ItemType.WEAPON
                    && !item.firstPersonModelPath().isBlank()) {
                builder.append("Icon: live model ").append(item.firstPersonModelPath()).append('\n');
            } else {
                builder.append("Icon: ").append(item.iconPath()).append('\n');
            }
            if (item.lanternDefinition().enabled()) {
                builder.append("Pocket light: ")
                        .append(String.format(Locale.ROOT, "#%06X", item.lanternDefinition().colorRgb()))
                        .append(", radius ").append(item.lanternDefinition().radius())
                        .append(", intensity ").append(item.lanternDefinition().intensity())
                        .append('\n');
            }
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            DifficultyResolver.DifficultyRating rating = DifficultyResolver.rateMonsterProfile(mob.displayName(),
                    mob.statValues(), mob.skillIds());
            builder.append("Level: ").append(rating.level()).append(" (power ")
                    .append(String.format(java.util.Locale.US, "%.2f", rating.power())).append(")\n");
            builder.append("AI: ").append(mob.combatAiIntelligence()).append('\n');
            builder.append("Awareness: ").append(mob.awarenessRadius()).append(" tile(s)\n");
            builder.append("Movement: ").append(mob.movementIntervalMs() / 1000.0).append(" seconds\n");
            builder.append("Respawn: ").append(mob.respawnDelayMs() == 0
                    ? "Disabled"
                    : mob.respawnDelayMs() / 1000.0 + " seconds").append('\n');
            builder.append("XP: ").append(mob.xpReward()).append('\n');
            builder.append("Skills: ").append(mob.skillIds().isEmpty() ? "None" : mob.skillIds()).append('\n');
            builder.append("Drops: ").append(mob.dropEntries().isEmpty() ? "None" : mob.dropEntries()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            builder.append("Dialogue: ").append(npc.interactionId().isBlank() ? "None" : npc.interactionId())
                    .append('\n');
            builder.append("Sprite: ").append(npc.imagePath()).append('\n');
            builder.append("Talk Sound: ").append(npc.talkSoundPath().isBlank() ? "None" : npc.talkSoundPath())
                    .append('\n');
            if (npc.shop() != null) {
                builder.append("Shop: ").append(npc.shop().shopName()).append('\n');
                builder.append("Stock: ").append(npc.shop().stock().size()).append(" item(s)\n");
            }
        } else if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            builder.append("Category: ").append(furniture.category().isBlank() ? "None" : furniture.category())
                    .append('\n');
            builder.append("Model: ").append(furniture.modelPath().isBlank() ? "None" : furniture.modelPath())
                    .append('\n');
            builder.append("Default Scale: ").append(formatDouble(furniture.defaultScale())).append('\n');
            builder.append("Blocks Movement: ").append(furniture.defaultBlocksMovement()).append('\n');
            builder.append("Interaction: ")
                    .append(furniture.interactionId().isBlank() ? "None" : furniture.interactionId())
                    .append('\n');
            builder.append("Attached Light: ").append(furniture.lightAttachment() == null ? "None" : "Yes")
                    .append('\n');
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            builder.append("Slot: ").append(limb.limbSlot()).append('\n');
            builder.append("Source: ").append(limb.sourceCreatureId().isBlank() ? "None" : limb.sourceCreatureId())
                    .append('\n');
            builder.append("Stats: ").append(limb.statBonuses()).append('\n');
            builder.append("Skills: ").append(limb.skillIds().isEmpty() ? "None" : limb.skillIds()).append('\n');
        } else if (value instanceof SkillDefinition skill) {
            builder.append("Target: ").append(skill.targetTeam()).append(" / ")
                    .append(skill.targetShape()).append('\n');
            builder.append("Mode: ").append(skill.targetingMode()).append('\n');
            builder.append("Cooldown: ").append(skill.cooldownSeconds()).append(" seconds\n");
            builder.append("Presentation: ").append(skill.presentationStyle()).append('\n');
            builder.append("Effects: ").append(skill.effects().size()).append('\n');
            for (var effect : skill.effects()) {
                builder.append("- ").append(effect.kindId())
                        .append(" -> ").append(effect.recipientScope())
                        .append(" [").append(Math.round(effect.chance() * 100)).append("%]\n");
            }
        } else if (value instanceof StatusDefinition status) {
            builder.append("Polarity: ").append(status.polarity()).append('\n');
            builder.append("Behavior: ").append(status.behaviorKindId()).append('\n');
            builder.append("Duration: ").append(status.defaultDuration()).append(" turn(s)\n");
            builder.append("Stacking: ").append(status.stackingPolicy());
            if (status.stackingPolicy() == StatusDefinition.StackingPolicy.STACK) {
                builder.append(" up to ").append(status.maxStacks());
            }
            builder.append('\n');
            builder.append("Parameters: ").append(status.parameters()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            builder.append("Type: ").append(node.nodeType()).append('\n');
            builder.append("Skill: ").append(node.gatheringSkill()).append(" level ").append(node.requiredLevel())
                    .append('\n');
            builder.append("Gather XP: ").append(node.gatherXpReward()).append('\n');
            builder.append("Loot: ").append(node.lootEntries()).append('\n');
            builder.append("Image States: ").append(node.framePaths().isEmpty() ? "None" : node.framePaths())
                    .append('\n');
            builder.append("3D Model States: ").append(node.modelPaths().isEmpty() ? "None" : node.modelPaths())
                    .append('\n');
            builder.append("Attached Light: ").append(node.lightAttachment() == null ? "None" : "Yes")
                    .append('\n');
            if (!node.smeltOutputItemId().isBlank()) {
                builder.append("Smelts to: ").append(node.smeltOutputItemId())
                        .append(" at level ").append(node.smeltRequiredLevel())
                        .append(" for ").append(node.smeltXpReward()).append(" xp\n");
            }
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            builder.append("Raw: ").append(recipe.rawItemId()).append('\n');
            builder.append("Cooked: ").append(recipe.cookedItemId()).append('\n');
            builder.append("Burnt: ").append(recipe.burntItemId()).append('\n');
            builder.append("Cooking: level ").append(recipe.requiredLevel())
                    .append(", ").append(recipe.xpReward()).append(" xp\n");
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            builder.append("Category: ").append(recipe.category()).append('\n');
            builder.append("Input: ").append(recipe.primaryItemId()).append(" + ").append(recipe.secondaryItemId())
                    .append('\n');
            builder.append("Output: ").append(recipe.outputItemId()).append('\n');
            builder.append("Skill: ").append(recipe.requiredSkill()).append(" level ").append(recipe.requiredLevel())
                    .append(", ").append(recipe.xpReward()).append(" xp\n");
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            builder.append("Stages: ").append(quest.stages().size()).append('\n');
            for (int i = 0; i < quest.stages().size(); i++) {
                builder.append(i).append(": ").append(quest.stages().get(i).journalText()).append('\n');
            }
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            builder.append("Default Text: ").append(dialogue.bodyText()).append('\n');
            builder.append("Nodes: ").append(dialogue.nodes().size()).append('\n');
            builder.append("Choices: ").append(dialogue.choices().size()).append('\n');
        } else if (value instanceof MobAreaEntry area) {
            long tiles = 0;
            for (int y = 0; y < design.height(); y++) {
                for (int x = 0; x < design.width(); x++) {
                    if (area.areaId().equals(design.mobAreas().get(x, y))) {
                        tiles++;
                    }
                }
            }
            builder.append("Painted Tiles: ").append(tiles).append('\n');
            builder.append("Cross-chunk areas join when their IDs match.\n");
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            builder.append("Tile: ").append(trigger.x()).append(',').append(trigger.y()).append('\n');
            builder.append("Activation: ").append(trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS
                    ? "Quest reaches stage"
                    : "Player enters tile").append('\n');
            if (trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS) {
                builder.append("Quest: ").append(trigger.requiredQuestId()).append('\n');
                builder.append("Required progress: ").append(trigger.requiredQuestProgress()).append('\n');
            }
            builder.append("One Shot: ").append(trigger.oneShot()).append('\n');
            builder.append("Actions: ").append(trigger.actions()).append('\n');
        } else if (value instanceof MapLight light) {
            builder.append("Tile: ").append(light.x()).append(',').append(light.y()).append('\n');
            builder.append("Color: ").append(MapLightingSettings.colorHex(light.colorRgb())).append('\n');
            builder.append("Radius: ").append(light.radius()).append('\n');
            builder.append("Intensity: ").append(light.intensity()).append('\n');
            builder.append("Height Offset: ").append(light.heightOffset()).append('\n');
            builder.append("Flicker: ").append(light.flickerAmount()).append('\n');
            builder.append("Enabled: ").append(light.enabled()).append('\n');
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            builder.append("Kind: ").append(placement.kind()).append('\n');
            builder.append("Tile: ").append(placement.x()).append(',').append(placement.y()).append('\n');
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            builder.append("Kind: ").append(object.kind()).append('\n');
            builder.append("Content Id: ").append(object.id()).append('\n');
            builder.append("Tile: ").append(object.x()).append(',').append(object.y()).append('\n');
            builder.append("Offset: ")
                    .append(formatDouble(object.offsetX())).append(", ")
                    .append(formatDouble(object.offsetY())).append(", ")
                    .append(formatDouble(object.offsetZ())).append('\n');
            builder.append("Rotation: ")
                    .append(formatDouble(object.yawDegrees())).append(", ")
                    .append(formatDouble(object.pitchDegrees())).append(", ")
                    .append(formatDouble(object.rollDegrees())).append('\n');
            builder.append("Scale: ").append(formatDouble(object.scale())).append('\n');
            builder.append("Model Brightness: ").append(formatDouble(object.modelBrightness())).append('\n');
            builder.append("Blocks Movement: ").append(object.blocksMovement()).append('\n');
            builder.append("Light Override: ").append(object.lightOverride() == null ? "None" : "Yes").append('\n');
        } else if (value instanceof MapDesignLibrary.ValidationIssue issue) {
            builder.append("Severity: ").append(issue.severity()).append('\n');
            builder.append("Message: ").append(issue.message()).append('\n');
        }
        builder.append('\n');
    }

    private String formatDouble(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private double rotatedLocalOffsetX(double yawDegrees, double offsetX, double offsetZ) {
        double radians = Math.toRadians(yawDegrees);
        return offsetX * Math.cos(radians) - offsetZ * Math.sin(radians);
    }

    private double rotatedLocalOffsetZ(double yawDegrees, double offsetX, double offsetZ) {
        double radians = Math.toRadians(yawDegrees);
        return offsetX * Math.sin(radians) + offsetZ * Math.cos(radians);
    }

    private void appendReferences(StringBuilder builder, ContentEntry entry) {
        List<String> references = findReferences(entry);
        builder.append("References\n");
        if (references.isEmpty()) {
            builder.append("- None found\n");
            return;
        }
        for (String reference : references) {
            builder.append("- ").append(reference).append('\n');
        }
    }

    private void showSelectedDependencies() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            setStatus("Select content before opening dependencies.");
            return;
        }

        JTextArea dependencyArea = new JTextArea(dependencyReport(entry), 24, 72);
        dependencyArea.setEditable(false);
        dependencyArea.setLineWrap(true);
        dependencyArea.setWrapStyleWord(true);
        showScrollableMessageDialog(
                dependencyArea,
                "Dependencies: " + entry.label(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String dependencyReport(ContentEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.type()).append(": ").append(entry.label()).append('\n');
        builder.append("Id: ").append(entry.id()).append("\n\n");

        builder.append("Uses\n");
        List<String> dependencies = findDependencies(entry);
        if (dependencies.isEmpty()) {
            builder.append("- None\n");
        } else {
            for (String dependency : dependencies) {
                builder.append("- ").append(dependency).append('\n');
            }
        }

        builder.append("\nUsed By\n");
        List<String> references = findReferences(entry);
        if (references.isEmpty()) {
            builder.append("- None\n");
        } else {
            for (String reference : references) {
                builder.append("- ").append(reference).append('\n');
            }
        }

        return builder.toString();
    }

    private void showSelectedGraph() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            setStatus("Select content before opening a graph.");
            return;
        }

        if (entry.value() instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            showDialogueGraph(entry, dialogue);
            return;
        }

        ContentGraph graph = new ContentGraph(
                entry.type() + ": " + entry.label(),
                findDependencies(entry),
                findReferences(entry));
        ContentGraphPanel graphPanel = new ContentGraphPanel(graph);
        JTextArea graphArea = new JTextArea(graphReport(entry), 24, 72);
        graphArea.setEditable(false);
        graphArea.setLineWrap(false);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(graphPanel),
                new JScrollPane(graphArea));
        splitPane.setResizeWeight(0.72);
        showScrollableMessageDialog(
                splitPane,
                "Graph: " + entry.label(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDialogueGraph(ContentEntry entry, MapDesignLibrary.AuthoredDialogue dialogue) {
        DialogueGraphPanel graphPanel = new DialogueGraphPanel(dialogue);
        JTextArea graphArea = new JTextArea(dialogueGraphReport(dialogue), 18, 72);
        graphArea.setEditable(false);
        graphArea.setLineWrap(false);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(graphPanel),
                new JScrollPane(graphArea));
        splitPane.setResizeWeight(0.74);

        JButton editButton = new JButton("Edit Dialogue");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                splitPane,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[] { editButton, closeButton },
                closeButton);
        var dialog = pane.createDialog(this, "Dialogue Graph: " + entry.label());

        editButton.addActionListener(event -> {
            dialog.dispose();
            openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind.DIALOGUE, dialogue.interactionId());
            refreshContentBrowser();
        });
        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private String dialogueGraphReport(MapDesignLibrary.AuthoredDialogue dialogue) {
        StringBuilder builder = new StringBuilder();
        builder.append("graph TD\n");
        builder.append("  start[\"").append(escapeGraphLabel("start: " + dialogue.speakerName())).append("\"]\n");
        for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
            builder.append("  ").append(graphNodeId(node.nodeId()))
                    .append("[\"").append(escapeGraphLabel(node.nodeId())).append("\"]\n");
        }
        appendDialogueChoiceReport(builder, "start", dialogue.choices());
        for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
            appendDialogueChoiceReport(builder, graphNodeId(node.nodeId()), node.choices());
        }
        if (dialogue.choices().isEmpty() && dialogue.nodes().isEmpty()) {
            builder.append("  start --> terminal[\"No choices\"]\n");
        }
        return builder.toString();
    }

    private void appendDialogueChoiceReport(
            StringBuilder builder,
            String sourceNodeId,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            MapDesignLibrary.AuthoredDialogueChoice choice = choices.get(i);
            String target = choice.targetNodeId().isBlank()
                    ? sourceNodeId + "_terminal_" + i
                    : graphNodeId(choice.targetNodeId());
            if (choice.targetNodeId().isBlank()) {
                String terminalLabel = choice.bodyText().isBlank() ? "terminal response" : choice.bodyText();
                builder.append("  ").append(target)
                        .append("[\"").append(escapeGraphLabel(truncateForGraph(terminalLabel, 46))).append("\"]\n");
            }
            builder.append("  ").append(sourceNodeId)
                    .append(" -- \"").append(escapeGraphLabel(choiceLabelWithTags(choice))).append("\" --> ")
                    .append(target)
                    .append('\n');
        }
    }

    private String graphNodeId(String raw) {
        String safe = raw == null || raw.isBlank() ? "blank" : raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (!safe.matches("[A-Za-z_].*")) {
            safe = "node_" + safe;
        }
        return safe;
    }

    private String choiceLabelWithTags(MapDesignLibrary.AuthoredDialogueChoice choice) {
        List<String> tags = new ArrayList<>();
        if (!choice.requiredItemName().isBlank()) {
            tags.add("has " + choice.requiredItemName());
        }
        if (!choice.takeItemName().isBlank()) {
            tags.add("take " + choice.takeItemName());
        }
        for (MapDesignLibrary.RewardDefinition reward : choice.rewards()) {
            switch (reward.type()) {
                case ITEM -> tags.add("give " + reward.itemId() + " x" + reward.amount());
                case GOLD -> tags.add("+" + reward.amount() + "g");
                case SKILL_XP -> tags.add("+" + reward.amount() + " " + reward.skill());
            }
        }
        String label = choice.label();
        if (!tags.isEmpty()) {
            label += " (" + String.join(", ", tags) + ")";
        }
        return truncateForGraph(label, 72);
    }

    private String truncateForGraph(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String graphReport(ContentEntry entry) {
        String center = entry.type() + ": " + entry.label();
        List<String> dependencies = findDependencies(entry);
        List<String> references = findReferences(entry);
        StringBuilder builder = new StringBuilder();
        builder.append("graph TD\n");
        builder.append("  selected[\"").append(escapeGraphLabel(center)).append("\"]\n");
        for (int i = 0; i < dependencies.size(); i++) {
            builder.append("  selected --> dep").append(i)
                    .append("[\"").append(escapeGraphLabel(dependencies.get(i))).append("\"]\n");
        }
        for (int i = 0; i < references.size(); i++) {
            builder.append("  ref").append(i)
                    .append("[\"").append(escapeGraphLabel(references.get(i))).append("\"] --> selected\n");
        }
        if (dependencies.isEmpty() && references.isEmpty()) {
            builder.append("  selected --> none[\"No dependencies or references found\"]\n");
        }
        return builder.toString();
    }

    private String escapeGraphLabel(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private List<String> findDependencies(ContentEntry entry) {
        List<String> dependencies = new ArrayList<>();
        Object value = entry.value();
        if (value instanceof MaterialDefinition material) {
            editMaterialTier(material);
        } else if (value instanceof MapDesignLibrary.CustomItem item) {
            if (item.itemType() == InventorySystem.ItemType.WEAPON
                    && !item.firstPersonModelPath().isBlank()) {
                addAssetDependency(dependencies, "Live inventory icon model", item.firstPersonModelPath());
            } else {
                addAssetDependency(dependencies, "Icon", item.iconPath());
            }
            addAssetDependency(dependencies, "Paper-doll overlay", item.paperDollOverlayPath());
            addAssetDependency(dependencies, "First-person model", item.firstPersonModelPath());
            addAssetDependency(dependencies, "Use sound", item.useSoundPath());
            if (item.smithingRecipeEnabled()) {
                dependencies.add("Smithing material " + item.material() + ", bars " + item.smithingRequiredBars());
            }
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            addAssetDependency(dependencies, "Sprite", mob.imagePath());
            addAssetDependency(dependencies, "Paper-doll source", mob.paperDollSourcePath());
            addAssetDependency(dependencies, "Attack sound", mob.attackSoundPath());
            addAssetDependency(dependencies, "Hit sound", mob.damageSoundPath());
            addCharacterModelDependencies(dependencies, mob.characterModel());
            for (String skillId : mob.skillIds()) {
                dependencies.add("Skill " + skillId);
            }
            for (MapDesignLibrary.CustomDropEntry drop : mob.dropEntries()) {
                dependencies.add("Drop " + drop);
            }
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            addAssetDependency(dependencies, "Sprite", npc.imagePath());
            addAssetDependency(dependencies, "Talk sound", npc.talkSoundPath());
            addCharacterModelDependencies(dependencies, npc.characterModel());
            if (!npc.interactionId().isBlank()) {
                dependencies.add("Dialogue " + npc.interactionId());
            }
            if (npc.shop() != null) {
                for (MapDesignLibrary.CustomShopStock stock : npc.shop().stock()) {
                    dependencies.add("Shop stock " + stock.itemId());
                }
            }
        } else if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            addAssetDependency(dependencies, "Model", furniture.modelPath());
            if (!furniture.interactionId().isBlank()) {
                dependencies.add("Interaction " + furniture.interactionId());
            }
            if (furniture.lightAttachment() != null) {
                dependencies.add("Attached light radius " + formatDouble(furniture.lightAttachment().radius()));
            }
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            addAssetDependency(dependencies, "Icon", limb.iconPath());
            addAssetDependency(dependencies, "Paper-doll source", limb.paperDollSourcePath());
            addAssetDependency(dependencies, "First-person arm model", limb.firstPersonModelPath());
            if (!limb.sourceCreatureId().isBlank()) {
                dependencies.add("Source creature " + limb.sourceCreatureId());
            }
            for (String skillId : limb.skillIds()) {
                dependencies.add("Skill " + skillId);
            }
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            dependencies.add("Gathering skill " + node.gatheringSkill() + " level " + node.requiredLevel());
            for (MapDesignLibrary.CustomDropEntry drop : node.lootEntries()) {
                dependencies.add("Loot " + drop.itemId() + " weight/chance " + drop.chance());
            }
            for (String framePath : node.framePaths()) {
                addAssetDependency(dependencies, "Frame", framePath);
            }
            for (String modelPath : node.modelPaths()) {
                addAssetDependency(dependencies, "3D model state", modelPath);
            }
            if (node.lightAttachment() != null) {
                dependencies.add("Attached light radius " + formatDouble(node.lightAttachment().radius()));
            }
            if (!node.smeltOutputItemId().isBlank()) {
                dependencies.add("Smelt output " + node.smeltOutputItemId() + " level " + node.smeltRequiredLevel());
            }
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            dependencies.add("Raw item " + recipe.rawItemId());
            dependencies.add("Cooked item " + recipe.cookedItemId());
            dependencies.add("Burnt item " + recipe.burntItemId());
            dependencies.add("Cooking level " + recipe.requiredLevel());
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            dependencies.add("Primary item " + recipe.primaryItemId() + " x" + recipe.primaryQuantity());
            if (!recipe.secondaryItemId().isBlank()) {
                dependencies.add("Secondary item " + recipe.secondaryItemId() + " x" + recipe.secondaryQuantity());
            }
            if (recipe.outputsStation()) {
                dependencies.add("Temporary station " + recipe.outputStationType()
                        + " for " + recipe.stationLifetimeMs() / 1000 + " seconds");
            } else {
                dependencies.add("Output item " + recipe.outputItemId());
            }
            dependencies.add("Skill " + recipe.requiredSkill() + " level " + recipe.requiredLevel());
            if (!recipe.smeltOutputItemId().isBlank()) {
                dependencies
                        .add("Smelt output " + recipe.smeltOutputItemId() + " level " + recipe.smeltRequiredLevel());
            }
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            dependencies.add("Stage count " + quest.stages().size());
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            if (!dialogue.followUpInteractionId().isBlank()) {
                dependencies.add("Follow-up " + dialogue.followUpInteractionId());
            }
            collectChoiceDependencies(dependencies, dialogue.choices());
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                dependencies.add("Node " + node.nodeId());
                collectChoiceDependencies(dependencies, node.choices());
            }
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            dependencies.add("Trigger tile " + trigger.x() + "," + trigger.y());
            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                dependencies.add("Action " + action.type() + " -> " + action.targetX() + "," + action.targetY());
            }
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            dependencies.add("Placed " + placement.kind() + " " + placement.id());
            dependencies.add("Tile " + placement.x() + "," + placement.y());
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            dependencies.add("Placed " + object.kind() + " " + placedObjectDisplayName(object));
            String modelPath = placedObjectModelPath(object);
            if (!modelPath.isBlank()) {
                dependencies.add("Model " + modelPath);
            }
            dependencies.add("Tile " + object.x() + "," + object.y());
            dependencies.add("Transform offset "
                    + formatDouble(object.offsetX()) + ","
                    + formatDouble(object.offsetY()) + ","
                    + formatDouble(object.offsetZ()));
            dependencies.add("Transform rotation "
                    + formatDouble(object.yawDegrees()) + ","
                    + formatDouble(object.pitchDegrees()) + ","
                    + formatDouble(object.rollDegrees()));
            if (object.lightOverride() != null) {
                dependencies.add("Light override radius " + formatDouble(object.lightOverride().radius()));
            }
        } else if (value instanceof MapDesignLibrary.ValidationIssue issue) {
            dependencies.add(issue.severity() + " diagnostic");
        }
        return dependencies;
    }

    private void collectChoiceDependencies(List<String> dependencies,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices) {
        for (MapDesignLibrary.AuthoredDialogueChoice choice : choices) {
            if (!choice.targetNodeId().isBlank()) {
                dependencies.add("Choice target " + choice.targetNodeId());
            }
            if (!choice.requiredItemName().isBlank()) {
                dependencies.add("Requires item " + choice.requiredItemName());
            }
            if (!choice.takeItemName().isBlank()) {
                dependencies.add("Takes item " + choice.takeItemName());
            }
            for (MapDesignLibrary.RewardDefinition reward : choice.rewards()) {
                switch (reward.type()) {
                    case ITEM -> dependencies.add("Gives item " + reward.itemId() + " x" + reward.amount());
                    case GOLD -> dependencies.add("Gives " + reward.amount() + " gold");
                    case SKILL_XP ->
                        dependencies.add("Gives " + reward.amount() + " " + reward.skill() + " XP");
                }
            }
        }
    }

    private void addAssetDependency(List<String> dependencies, String label, String path) {
        if (path != null && !path.isBlank()) {
            dependencies.add(label + " asset " + path);
        }
    }

    private List<String> findReferences(ContentEntry entry) {
        List<String> references = new ArrayList<>();
        String id = entry.id();
        String label = entry.label();
        String alternateId = alternateReferenceId(entry.value());

        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (id.equals(placement.id()) || (!alternateId.isBlank() && alternateId.equals(placement.id()))) {
                references.add("Placed at " + placement.x() + "," + placement.y() + " as " + placement.kind());
            }
        }
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (id.equals(object.id()) || (!alternateId.isBlank() && alternateId.equals(object.id()))) {
                references.add("Placed object at " + object.x() + "," + object.y() + " as " + object.kind());
            }
        }
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            if (id.equals(dialogue.followUpInteractionId())) {
                references.add("Dialogue " + dialogue.interactionId() + " follows up to this");
            }
            collectRewardReferences(references, "Dialogue " + dialogue.interactionId(), dialogue.rewards(), id);
            collectChoiceReferences(references, dialogue.interactionId(), dialogue.choices(), id, label);
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                collectChoiceReferences(references, dialogue.interactionId() + "::" + node.nodeId(), node.choices(), id,
                        label);
            }
        }
        for (MapDesignLibrary.AuthoredQuest quest : design.authoredQuests()) {
            collectRequirementReferences(references, "Quest " + quest.questId(), quest.requirements(), id);
            collectQuestFlowReferences(references, "Quest " + quest.questId() + " offer", quest.offerFlow(), id);
            for (MapDesignLibrary.QuestStage stage : quest.stages()) {
                String owner = "Quest " + quest.questId() + " stage " + stage.stageId();
                for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
                    if (isItemOrLimbObjective(objective.type()) && id.equals(objective.targetId())) {
                        references.add(owner + " objective " + objective.objectiveId());
                    }
                }
                collectRewardReferences(references, owner, stage.rewards(), id);
                collectQuestFlowReferences(references, owner + " flow", stage.flow(), id);
            }
            collectRewardReferences(references, "Quest " + quest.questId() + " completion",
                    quest.finalRewards(), id);
            collectQuestFlowReferences(references, "Quest " + quest.questId() + " epilogue",
                    quest.epilogueFlow(), id);
        }
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            if (id.equals(npc.interactionId())) {
                references.add("NPC " + npc.npcId() + " uses this dialogue");
            }
            if (npc.shop() != null && npc.shop().stock().stream().anyMatch(stock -> id.equals(stock.itemId()))) {
                references.add("Shop stock for NPC " + npc.npcId());
            }
        }
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            if (id.equals(furniture.interactionId())) {
                references.add("Furniture " + furniture.furnitureId() + " uses this interaction");
            }
        }
        for (MapDesignLibrary.CustomCookingRecipe recipe : design.customCookingRecipes()) {
            if (id.equals(recipe.rawItemId()) || id.equals(recipe.cookedItemId()) || id.equals(recipe.burntItemId())) {
                references.add("Cooking recipe " + recipe.recipeId());
            }
        }
        for (MapDesignLibrary.CraftingRecipe recipe : design.craftingRecipes()) {
            if (id.equals(recipe.primaryItemId())
                    || id.equals(recipe.secondaryItemId())
                    || id.equals(recipe.outputItemId())
                    || id.equals(recipe.smeltOutputItemId())) {
                references.add("Crafting recipe " + recipe.recipeId());
            }
        }
        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            if (id.equals(node.outputItemId()) || id.equals(node.smeltOutputItemId())) {
                references.add("Gathering node " + node.nodeId());
            }
            for (MapDesignLibrary.CustomDropEntry drop : node.lootEntries()) {
                if (id.equals(drop.itemId())) {
                    references.add("Gathering node loot " + node.nodeId());
                }
            }
        }
        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            if (id.equals(mob.butcheryProfile().leatherItemId())
                    || mob.butcheryProfile().limbProductIds().containsValue(id)) {
                references.add("Enemy butchery product " + mob.mobId());
            }
            for (MapDesignLibrary.CustomDropEntry drop : mob.dropEntries()) {
                if (id.equals(drop.itemId())) {
                    references.add("Enemy drop " + mob.mobId());
                }
            }
        }
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            if (id.equals(limb.sourceCreatureId())) {
                references.add("Limb " + limb.limbId() + " uses this as source creature");
            }
        }
        for (MaterialDefinition material : MaterialCatalog.snapshot().definitions()) {
            if (id.equals(material.rawResourceItemId())) {
                references.add("Material " + material.displayName() + " uses this as its raw resource");
            }
            if (id.equals(material.processedResourceItemId())) {
                references.add("Material " + material.displayName() + " uses this as its processed resource");
            }
        }
        return references;
    }

    private void collectQuestFlowReferences(
            List<String> references,
            String owner,
            MapDesignLibrary.QuestFlow flow,
            String id
    ) {
        if (flow == null) {
            return;
        }
        for (MapDesignLibrary.QuestFlowNode node : flow.nodes()) {
            for (MapDesignLibrary.QuestFlowChoice choice : node.choices()) {
                if (id.equals(choice.requiredItemId()) || id.equals(choice.takeItemId())) {
                    references.add(owner + " choice " + choice.choiceId());
                }
                collectRequirementReferences(references, owner + " choice " + choice.choiceId(),
                        choice.conditions(), id);
                collectRewardReferences(references, owner + " choice " + choice.choiceId(),
                        choice.rewards(), id);
            }
        }
    }

    private void collectRequirementReferences(
            List<String> references,
            String owner,
            List<MapDesignLibrary.QuestRequirement> requirements,
            String id
    ) {
        for (MapDesignLibrary.QuestRequirement requirement : requirements == null
                ? List.<MapDesignLibrary.QuestRequirement>of()
                : requirements) {
            if ((requirement.type() == MapDesignLibrary.QuestRequirementType.POSSESS_ITEM
                    || requirement.type() == MapDesignLibrary.QuestRequirementType.EQUIPPED_ITEM_OR_LIMB)
                    && id.equals(requirement.targetId())) {
                references.add(owner + " requirement");
            }
        }
    }

    private void collectRewardReferences(
            List<String> references,
            String owner,
            List<MapDesignLibrary.RewardDefinition> rewards,
            String id
    ) {
        if (rewards != null && rewards.stream().anyMatch(reward ->
                reward.type() == MapDesignLibrary.QuestRewardType.ITEM && id.equals(reward.itemId()))) {
            references.add(owner + " reward");
        }
    }

    private boolean isItemOrLimbObjective(MapDesignLibrary.QuestObjectiveType type) {
        return type == MapDesignLibrary.QuestObjectiveType.POSSESS_ITEM
                || type == MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM
                || type == MapDesignLibrary.QuestObjectiveType.EQUIPPED_ITEM_OR_LIMB;
    }

    private String alternateReferenceId(Object value) {
        if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            return node.interactionId();
        }
        return "";
    }

    private void collectChoiceReferences(
            List<String> references,
            String dialogueLocation,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            String id,
            String label) {
        for (MapDesignLibrary.AuthoredDialogueChoice choice : choices) {
            if (label.equalsIgnoreCase(choice.requiredItemName())
                    || label.equalsIgnoreCase(choice.takeItemName())
                    || choice.rewards().stream().anyMatch(reward ->
                            reward.type() == MapDesignLibrary.QuestRewardType.ITEM
                                    && id.equals(reward.itemId()))) {
                references.add("Choice " + dialogueLocation + " references item name " + label);
            }
        }
    }

    private void editSelectedContent() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            return;
        }
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.CustomItem item) {
            editCustomItem(item);
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            editCustomMob(mob);
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            editCustomNpc(npc);
        } else if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            editCustomFurniture(furniture);
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            editCustomLimb(limb);
        } else if (value instanceof SkillDefinition skill) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.SKILL, skill.id());
        } else if (value instanceof StatusDefinition status) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.STATUS, status.id());
        } else if (value instanceof FirstPersonCombatLibrary.RigDefinition
                || value instanceof FirstPersonCombatLibrary.AnimationSet
                || value instanceof FirstPersonCombatLibrary.ItemProfile) {
            openFirstPersonViewmodelEditor();
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            editCustomGatheringNode(node);
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            editCookingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            editCraftingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind.QUEST, quest.questId());
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind.DIALOGUE, dialogue.interactionId());
        } else if (value instanceof MobAreaEntry area) {
            editMobArea(area);
        } else if (value instanceof MapLight light) {
            editLight(light);
        } else if (value instanceof MapDesignLibrary.MapTrigger) {
            manageTriggers();
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            editMapPlacement(placement);
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            editPlacedObject(object);
        } else if (value instanceof MapDesignLibrary.ValidationIssue) {
            validateMap();
        }
        refreshContentBrowser();
    }

    private void duplicateSelectedContent() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            return;
        }

        String copiedName = entry.label() + " Copy";
        Object value = entry.value();
        if (value instanceof SkillDefinition skill) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.SKILL, skill.id());
            return;
        } else if (value instanceof StatusDefinition status) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.STATUS, status.id());
            return;
        } else if (value instanceof FirstPersonCombatLibrary.RigDefinition
                || value instanceof FirstPersonCombatLibrary.AnimationSet
                || value instanceof FirstPersonCombatLibrary.ItemProfile) {
            openFirstPersonViewmodelEditor();
            return;
        }
        if (value instanceof MaterialDefinition material) {
            duplicateMaterialTier(material);
        } else if (value instanceof MapDesignLibrary.CustomItem item) {
            design.customItems().add(new MapDesignLibrary.CustomItem(
                    nextCustomItemId(copiedName),
                    copiedName,
                    item.itemType(),
                    item.iconPath(),
                    item.paperDollOverlayPath(),
                    item.useSoundPath(),
                    item.weaponType(),
                    item.twoHanded(),
                    item.material(),
                    item.healAmount(),
                    item.baseGoldValue(),
                    item.examineText(),
                    item.statBonusTarget(),
                    item.stackable(),
                    item.smithingRecipeEnabled(),
                    item.smithingRequiredBars(),
                    item.smithingRequiredLevel(),
                    item.magicAccuracyBonus(),
                    item.magicPowerBonus(),
                    item.firstPersonModelPath(),
                    item.viewModelProfile(),
                    item.modelIconProfile()));
            persistSharedContent("custom item");
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            duplicateEnemyWithProducts(mob, copiedName);
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            design.customNpcs().add(new MapDesignLibrary.CustomNpc(
                    nextCustomNpcId(copiedName),
                    copiedName,
                    npc.imagePath(),
                    npc.talkSoundPath(),
                    npc.interactionId(),
                    npc.shop(),
                    npc.characterModel(),
                    npc.questIds()));
            persistSharedContent("custom NPC");
        } else if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            design.customFurniture().add(new MapDesignLibrary.CustomFurnitureDefinition(
                    nextCustomFurnitureId(copiedName),
                    copiedName,
                    furniture.category(),
                    furniture.modelPath(),
                    furniture.defaultScale(),
                    furniture.defaultBlocksMovement(),
                    furniture.interactionId(),
                    furniture.lightAttachment()));
            persistSharedContent("custom furniture");
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            design.customLimbs().add(new MapDesignLibrary.CustomLimb(
                    nextCustomLimbId(copiedName),
                    copiedName,
                    limb.limbSlot(),
                    limb.iconPath(),
                    limb.condition(),
                    limb.description(),
                    limb.sourceCreatureId(),
                    limb.paperDollSourcePath(),
                    limb.statBonuses(),
                    limb.skillIds(),
                    limb.firstPersonModelPath(),
                    limb.firstPersonRigId(),
                    limb.paperDollDerivedIcon(),
                    limb.baseGoldValue()));
            persistSharedContent("custom limb");
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            design.customGatheringNodes().add(new MapDesignLibrary.CustomGatheringNode(
                    nextCustomGatheringNodeId(copiedName),
                    copiedName,
                    node.nodeType(),
                    node.requiredLevel(),
                    node.outputItemId(),
                    node.gatherXpReward(),
                    node.smeltOutputItemId(),
                    node.smeltXpReward(),
                    node.framePaths(),
                    node.modelPaths(),
                    node.frameDurationMs(),
                    node.visualScale(),
                    node.gatheringSkill(),
                    node.lootEntries(),
                    node.smeltRequiredLevel(),
                    node.lightAttachment()));
            persistSharedContent("gathering node");
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            design.customCookingRecipes().add(new MapDesignLibrary.CustomCookingRecipe(
                    nextCookingRecipeId(copiedName),
                    copiedName,
                    recipe.rawItemId(),
                    recipe.cookedItemId(),
                    recipe.burntItemId(),
                    recipe.requiredLevel(),
                    recipe.xpReward()));
            persistSharedContent("cooking recipe");
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            design.craftingRecipes().add(new MapDesignLibrary.CraftingRecipe(
                    nextCraftingRecipeId(copiedName),
                    copiedName,
                    recipe.category(),
                    recipe.primaryItemId(),
                    recipe.secondaryItemId(),
                    recipe.outputItemId(),
                    recipe.requiredSkill(),
                    recipe.requiredLevel(),
                    recipe.xpReward(),
                    recipe.consumePrimary(),
                    recipe.consumeSecondary(),
                    recipe.smeltOutputItemId(),
                    recipe.smeltRequiredLevel(),
                    recipe.smeltXpReward(),
                    recipe.primaryQuantity(),
                    recipe.secondaryQuantity(),
                    recipe.outputType(),
                    recipe.outputStationType(),
                    recipe.stationLifetimeMs()));
            persistSharedContent("crafting recipe");
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            design.authoredQuests().add(new MapDesignLibrary.AuthoredQuest(
                    nextAuthoredQuestId(copiedName),
                    copiedName,
                    quest.summary(),
                    quest.requirements(),
                    quest.offerFlow(),
                    quest.stages(),
                    quest.finalRewards(),
                    quest.epilogueFlow()));
            persistSharedContent("authored quest");
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            design.authoredDialogues().add(new MapDesignLibrary.AuthoredDialogue(
                    nextAuthoredInteractionId(copiedName),
                    copiedName,
                    dialogue.bodyText(),
                    dialogue.followUpInteractionId(),
                    MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                    dialogue.choices(),
                    dialogue.nodes(),
                    dialogue.rewards(),
                    dialogue.firstTalkNodeId(),
                    dialogue.repeatTalkNodeId()));
            persistSharedContent("authored dialogue NPC");
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            duplicateMapPlacement(placement);
            return;
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            duplicatePlacedObject(object);
            return;
        } else {
            setStatus(entry.type() + " cannot be duplicated.");
            return;
        }

        populatePlaceables();
        refreshContentBrowser();
        setStatus("Duplicated " + entry.label() + ".");
    }

    private void duplicateMapPlacement(MapDesignLibrary.MapPlacement placement) {
        Point target = duplicatePlacementTarget(placement);
        if (target == null) {
            setStatus("No empty tile found for duplicated placement.");
            return;
        }

        PlaceableOption placementOption = new PlaceableOption("", placement.kind(), placement.id());
        if (isTransformablePlaceable(placementOption)) {
            MapDesignLibrary.PlacedObjectInstance duplicated = new MapDesignLibrary.PlacedObjectInstance(
                    nextPlacedObjectId(placement.id()),
                    placement.kind(),
                    placement.id(),
                    target.x,
                    target.y,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    defaultBlocksMovementFor(placementOption),
                    null);
            captureHistory("duplicate transformable placement");
            design.placedObjects().add(duplicated);
            markDirty(true);
            refreshContentBrowser();
            revealPlacedObject(duplicated);
            setStatus("Duplicated placed object " + placement.id() + " at " + target.x + "," + target.y + ".");
            return;
        }

        captureHistory("duplicate placement");
        MapDesignLibrary.MapPlacement duplicated = new MapDesignLibrary.MapPlacement(
                placement.kind(),
                placement.id(),
                target.x,
                target.y);
        design.placements().add(duplicated);
        markDirty(true);
        refreshContentBrowser();
        revealMapPlacement(duplicated);
        setStatus("Duplicated placement " + placement.id() + " at " + target.x + "," + target.y + ".");
    }

    private Point duplicatePlacementTarget(MapDesignLibrary.MapPlacement placement) {
        return placement == null ? null : duplicatePlacementTarget(placement.x(), placement.y());
    }

    private Point duplicatePlacementTarget(MapDesignLibrary.PlacedObjectInstance object) {
        return object == null ? null : duplicatePlacementTarget(object.x(), object.y());
    }

    private Point duplicatePlacementTarget(int originX, int originY) {
        int[][] offsets = {
                { 1, 0 },
                { 0, 1 },
                { -1, 0 },
                { 0, -1 }
        };
        for (int[] offset : offsets) {
            int x = originX + offset[0];
            int y = originY + offset[1];
            if (isPlacementTargetOpen(x, y)) {
                return new Point(x, y);
            }
        }

        for (int radius = 2; radius < Math.max(design.width(), design.height()); radius++) {
            for (int y = originY - radius; y <= originY + radius; y++) {
                for (int x = originX - radius; x <= originX + radius; x++) {
                    boolean edge = x == originX - radius
                            || x == originX + radius
                            || y == originY - radius
                            || y == originY + radius;
                    if (edge && isPlacementTargetOpen(x, y)) {
                        return new Point(x, y);
                    }
                }
            }
        }
        return null;
    }

    private void duplicatePlacedObject(MapDesignLibrary.PlacedObjectInstance object) {
        Point target = duplicatePlacementTarget(object);
        if (target == null) {
            setStatus("No empty tile found for duplicated placed object.");
            return;
        }

        captureHistory("duplicate placed object");
        MapDesignLibrary.PlacedObjectInstance duplicated = copyPlacedObjectAt(
                object,
                nextPlacedObjectId(object.id()),
                target.x,
                target.y);
        design.placedObjects().add(duplicated);
        markDirty(true);
        refreshContentBrowser();
        revealPlacedObject(duplicated);
        setStatus("Duplicated placed object " + object.id() + " at " + target.x + "," + target.y + ".");
    }

    private MapDesignLibrary.PlacedObjectInstance copyPlacedObjectAt(
            MapDesignLibrary.PlacedObjectInstance source,
            String instanceId,
            int x,
            int y) {
        return new MapDesignLibrary.PlacedObjectInstance(
                instanceId,
                source.kind(),
                source.id(),
                x,
                y,
                source.offsetX(),
                source.offsetY(),
                source.offsetZ(),
                source.yawDegrees(),
                source.pitchDegrees(),
                source.rollDegrees(),
                source.scale(),
                source.modelBrightness(),
                source.blocksMovement(),
                source.lightOverride());
    }

    private String nextPlacedObjectId(String contentId) {
        String base = safeId(contentId);
        if (base.isBlank()) {
            base = "object";
        }
        String prefix = "placed_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasPlacedObjectId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasPlacedObjectId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return true;
        }
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (object.instanceId().equals(instanceId)) {
                return true;
            }
        }
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.id().equals(instanceId)) {
                return true;
            }
        }
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            if (trigger.id().equals(instanceId)) {
                return true;
            }
        }
        for (MapLight light : design.lights()) {
            if (light.id().equals(instanceId)) {
                return true;
            }
        }
        return hasCustomFurnitureId(instanceId)
                || hasCustomItemId(instanceId)
                || hasCustomMobId(instanceId)
                || hasCustomNpcId(instanceId)
                || hasCustomLimbId(instanceId)
                || hasCustomGatheringNodeId(instanceId)
                || hasAuthoredDialogueId(instanceId)
                || hasAuthoredQuestId(instanceId);
    }

    private List<MapDesignLibrary.PlacedObjectInstance> placedObjectsAt(int x, int y) {
        List<MapDesignLibrary.PlacedObjectInstance> matches = new ArrayList<>();
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (object.x() == x && object.y() == y) {
                matches.add(object);
            }
        }
        return matches;
    }

    private List<MapDesignLibrary.MapPlacement> mapPlacementsAt(int x, int y) {
        List<MapDesignLibrary.MapPlacement> matches = new ArrayList<>();
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.x() == x && placement.y() == y) {
                matches.add(placement);
            }
        }
        return matches;
    }

    private boolean isDesignTileInBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < design.width() && y < design.height();
    }

    private int designHeightLevelAt(int x, int y) {
        if (!isDesignTileInBounds(x, y) || design.mapGeometry() == null) {
            return MapGeometryData.DEFAULT_HEIGHT_LEVEL;
        }
        return design.mapGeometry().getHeightLevel(x, y);
    }

    private double clampTileOffset(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(-0.5, Math.min(0.5, value));
    }

    private void launchPlacedObject3dPreview(Supplier<MapDesignLibrary.PlacedObjectInstance> objectSupplier) {
        List<EnvironmentTheme> themes = List.of(
                design.primaryTheme().getTheme(),
                design.alternateTheme().getTheme());
        Thread previewThread = new Thread(() -> runPlacedObject3dPreview(objectSupplier, themes),
                "Aether-ConstructionKit-3D-Preview");
        previewThread.setDaemon(true);
        previewThread.start();
        setStatus("Opened 3D placed-object preview.");
    }

    private void launchMapPlacement3dPreview(
            Supplier<MapDesignLibrary.MapPlacement> placementSupplier,
            MapDesignLibrary.MapPlacement originalPlacement
    ) {
        List<EnvironmentTheme> themes = List.of(
                design.primaryTheme().getTheme(),
                design.alternateTheme().getTheme());
        Thread previewThread = new Thread(
                () -> runMapPlacement3dPreview(placementSupplier, originalPlacement, themes),
                "Aether-ConstructionKit-Placement-3D-Preview");
        previewThread.setDaemon(true);
        previewThread.start();
        setStatus("Opened 3D placement preview.");
    }

    private void runPlacedObject3dPreview(
            Supplier<MapDesignLibrary.PlacedObjectInstance> objectSupplier,
            List<EnvironmentTheme> themes
    ) {
        TextureManager textureManager = new TextureManager();
        textureManager.loadFromFolder("assets/images/building");
        LwjglDungeonViewport viewport = new LwjglDungeonViewport(textureManager, themes);
        try {
            viewport.initialize();
            PlacedObjectPreviewCamera previewCamera = new PlacedObjectPreviewCamera();
            while (!viewport.shouldClose()) {
                previewCamera.update(viewport.windowHandle());
                DungeonRenderContext context = placedObjectPreviewContext(objectSupplier, previewCamera.viewpoint());
                if (context != null) {
                    viewport.renderFrame(context, previewCamera.lookState());
                }
                viewport.pollEvents();
                Thread.sleep(16L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            SwingUtilities.invokeLater(() -> setStatus("3D preview failed: " + exception.getMessage()));
        } finally {
            viewport.shutdown();
        }
    }

    private void runMapPlacement3dPreview(
            Supplier<MapDesignLibrary.MapPlacement> placementSupplier,
            MapDesignLibrary.MapPlacement originalPlacement,
            List<EnvironmentTheme> themes
    ) {
        TextureManager textureManager = new TextureManager();
        textureManager.loadFromFolder("assets/images/building");
        LwjglDungeonViewport viewport = new LwjglDungeonViewport(textureManager, themes);
        try {
            viewport.initialize();
            PlacedObjectPreviewCamera previewCamera = new PlacedObjectPreviewCamera();
            while (!viewport.shouldClose()) {
                previewCamera.update(viewport.windowHandle());
                DungeonRenderContext context = mapPlacementPreviewContext(
                        placementSupplier,
                        originalPlacement,
                        previewCamera.viewpoint());
                if (context != null) {
                    viewport.renderFrame(context, previewCamera.lookState());
                }
                viewport.pollEvents();
                Thread.sleep(16L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            SwingUtilities.invokeLater(() -> setStatus("3D preview failed: " + exception.getMessage()));
        } finally {
            viewport.shutdown();
        }
    }

    private static final class PlacedObjectPreviewCamera {
        private static final double BASE_PITCH_DEGREES = -8.0;
        private static final double SENSITIVITY = 0.18;
        private static final double MIN_PITCH_DEGREES = -55.0;
        private static final double MAX_PITCH_DEGREES = 35.0;

        private boolean rotating;
        private double lastMouseX;
        private double lastMouseY;
        private double yawDegrees;
        private double pitchDegrees = BASE_PITCH_DEGREES;
        private PreviewViewpoint viewpoint = PreviewViewpoint.SOUTH;

        private void update(long windowHandle) {
            if (windowHandle == 0L) {
                rotating = false;
                return;
            }
            updateViewpoint(windowHandle);
            boolean rightHeld = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
            double[] mouseX = new double[1];
            double[] mouseY = new double[1];
            glfwGetCursorPos(windowHandle, mouseX, mouseY);
            if (!rightHeld) {
                rotating = false;
                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
                return;
            }
            if (!rotating) {
                rotating = true;
                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
                return;
            }
            double deltaX = mouseX[0] - lastMouseX;
            double deltaY = mouseY[0] - lastMouseY;
            lastMouseX = mouseX[0];
            lastMouseY = mouseY[0];
            yawDegrees = normalizeDegrees(yawDegrees + deltaX * SENSITIVITY);
            pitchDegrees = Math.max(MIN_PITCH_DEGREES,
                    Math.min(MAX_PITCH_DEGREES, pitchDegrees + deltaY * SENSITIVITY));
        }

        private CameraLookState lookState() {
            return new CameraLookState(yawDegrees, pitchDegrees, rotating);
        }

        private PreviewViewpoint viewpoint() {
            return viewpoint;
        }

        private void updateViewpoint(long windowHandle) {
            if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS) {
                viewpoint = PreviewViewpoint.NORTH;
                yawDegrees = 0.0;
            } else if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS) {
                viewpoint = PreviewViewpoint.WEST;
                yawDegrees = 0.0;
            } else if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS) {
                viewpoint = PreviewViewpoint.SOUTH;
                yawDegrees = 0.0;
            } else if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS) {
                viewpoint = PreviewViewpoint.EAST;
                yawDegrees = 0.0;
            }
        }

        private static double normalizeDegrees(double value) {
            double normalized = value % 360.0;
            return normalized < 0.0 ? normalized + 360.0 : normalized;
        }
    }

    private enum PreviewViewpoint {
        NORTH(1, 0, 2),
        WEST(0, 1, 1),
        SOUTH(1, 2, 0),
        EAST(2, 1, 3);

        private final int playerX;
        private final int playerY;
        private final int direction;

        PreviewViewpoint(int playerX, int playerY, int direction) {
            this.playerX = playerX;
            this.playerY = playerY;
            this.direction = direction;
        }
    }

    private DungeonRenderContext placedObjectPreviewContext(
            Supplier<MapDesignLibrary.PlacedObjectInstance> objectSupplier,
            PreviewViewpoint viewpoint
    ) {
        if (SwingUtilities.isEventDispatchThread()) {
            return buildPlacedObjectPreviewContext(objectSupplier.get(), viewpoint);
        }
        final DungeonRenderContext[] result = new DungeonRenderContext[1];
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = buildPlacedObjectPreviewContext(objectSupplier.get(), viewpoint));
        } catch (Exception exception) {
            SwingUtilities.invokeLater(() -> setStatus("3D preview update failed: " + exception.getMessage()));
            return null;
        }
        return result[0];
    }

    private DungeonRenderContext buildPlacedObjectPreviewContext(
            MapDesignLibrary.PlacedObjectInstance editedObject,
            PreviewViewpoint viewpoint
    ) {
        if (editedObject == null) {
            return null;
        }
        return buildLocalPreviewContext(
                editedObject.x(),
                editedObject.y(),
                viewpoint,
                editedObject,
                null,
                null);
    }

    private DungeonRenderContext mapPlacementPreviewContext(
            Supplier<MapDesignLibrary.MapPlacement> placementSupplier,
            MapDesignLibrary.MapPlacement originalPlacement,
            PreviewViewpoint viewpoint
    ) {
        if (SwingUtilities.isEventDispatchThread()) {
            return buildMapPlacementPreviewContext(placementSupplier.get(), originalPlacement, viewpoint);
        }
        final DungeonRenderContext[] result = new DungeonRenderContext[1];
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = buildMapPlacementPreviewContext(
                    placementSupplier.get(),
                    originalPlacement,
                    viewpoint));
        } catch (Exception exception) {
            SwingUtilities.invokeLater(() -> setStatus("3D preview update failed: " + exception.getMessage()));
            return null;
        }
        return result[0];
    }

    private DungeonRenderContext buildMapPlacementPreviewContext(
            MapDesignLibrary.MapPlacement editedPlacement,
            MapDesignLibrary.MapPlacement originalPlacement,
            PreviewViewpoint viewpoint
    ) {
        if (editedPlacement == null) {
            return null;
        }
        return buildLocalPreviewContext(
                editedPlacement.x(),
                editedPlacement.y(),
                viewpoint,
                null,
                editedPlacement,
                originalPlacement);
    }

    private DungeonRenderContext buildLocalPreviewContext(
            int centerX,
            int centerY,
            PreviewViewpoint viewpoint,
            MapDesignLibrary.PlacedObjectInstance editedObject,
            MapDesignLibrary.MapPlacement editedPlacement,
            MapDesignLibrary.MapPlacement originalPlacement
    ) {
        final int previewSize = 3;
        final int center = 1;
        Library.TileType[][] tiles = new Library.TileType[previewSize][previewSize];
        int[][] themeIndexes = new int[previewSize][previewSize];
        int[][] heightLevels = new int[previewSize][previewSize];
        String[][] mobAreas = new String[previewSize][previewSize];
        Map<MapPaintData.Layer, String[][]> paintLayers = new EnumMap<>(MapPaintData.Layer.class);
        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
            paintLayers.put(layer, new String[previewSize][previewSize]);
        }

        for (int localY = 0; localY < previewSize; localY++) {
            for (int localX = 0; localX < previewSize; localX++) {
                int sourceX = centerX + localX - center;
                int sourceY = centerY + localY - center;
                if (isDesignTileInBounds(sourceX, sourceY)) {
                    tiles[localY][localX] = design.tiles()[sourceY][sourceX];
                    themeIndexes[localY][localX] = design.themeIndexes()[sourceY][sourceX];
                    heightLevels[localY][localX] = designHeightLevelAt(sourceX, sourceY);
                    mobAreas[localY][localX] = design.mobAreas().get(sourceX, sourceY);
                    for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                        paintLayers.get(layer)[localY][localX] = design.mapPaint().get(layer, sourceX, sourceY);
                    }
                } else {
                    tiles[localY][localX] = Library.TileType.WALL;
                    themeIndexes[localY][localX] = 0;
                    heightLevels[localY][localX] = MapGeometryData.DEFAULT_HEIGHT_LEVEL;
                    mobAreas[localY][localX] = "";
                }
            }
        }

        List<MapEntity> entities = new ArrayList<>();
        List<MapLight> lights = previewMapLights(centerX, centerY, center);
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (editedObject != null && object.instanceId().equals(editedObject.instanceId())) {
                continue;
            }
            addPreviewPlacedObject(entities, lights, localPreviewObject(object, centerX, centerY, center));
        }
        if (editedObject != null) {
            addPreviewPlacedObject(entities, lights, localPreviewObject(editedObject, centerX, centerY, center));
        }
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement == originalPlacement) {
                continue;
            }
            addPreviewMapPlacement(entities, lights, localPreviewPlacement(placement, centerX, centerY, center));
        }
        addPreviewMapPlacement(entities, lights, localPreviewPlacement(editedPlacement, centerX, centerY, center));

        DungeonMap map = new DungeonMap(
                tiles,
                themeIndexes,
                MapPaintData.of(
                        previewSize,
                        previewSize,
                        paintLayers.get(MapPaintData.Layer.FLOOR),
                        paintLayers.get(MapPaintData.Layer.WALL),
                        paintLayers.get(MapPaintData.Layer.DOOR),
                        paintLayers.get(MapPaintData.Layer.ROOF)),
                MapGeometryData.of(previewSize, previewSize, heightLevels),
                MobAreaData.of(previewSize, previewSize, mobAreas),
                design.lightingSettings(),
                lights);
        PreviewViewpoint safeViewpoint = viewpoint == null ? PreviewViewpoint.SOUTH : viewpoint;
        return new DungeonRenderContext(map, entities, null,
                safeViewpoint.playerX,
                safeViewpoint.playerY,
                safeViewpoint.direction,
                640, 360, 0.0, 0.0, 0.0);
    }

    private List<MapLight> previewMapLights(int centerX, int centerY, int center) {
        List<MapLight> lights = new ArrayList<>();
        for (MapLight light : design.lights()) {
            if (light == null) {
                continue;
            }
            int localX = light.x() - centerX + center;
            int localY = light.y() - centerY + center;
            if (localX < 0 || localY < 0 || localX >= 3 || localY >= 3) {
                continue;
            }
            lights.add(new MapLight(
                    light.id(),
                    localX,
                    localY,
                    light.colorRgb(),
                    light.radius(),
                    light.intensity(),
                    light.heightOffset(),
                    light.offsetX(),
                    light.offsetZ(),
                    light.flickerAmount(),
                    light.enabled()));
        }
        return lights;
    }

    private MapDesignLibrary.PlacedObjectInstance localPreviewObject(
            MapDesignLibrary.PlacedObjectInstance object,
            int centerX,
            int centerY,
            int center
    ) {
        if (object == null) {
            return null;
        }
        int localX = object.x() - centerX + center;
        int localY = object.y() - centerY + center;
        if (localX < 0 || localY < 0 || localX >= 3 || localY >= 3) {
            return null;
        }
        return new MapDesignLibrary.PlacedObjectInstance(
                object.instanceId(),
                object.kind(),
                object.id(),
                localX,
                localY,
                object.offsetX(),
                object.offsetY(),
                object.offsetZ(),
                object.yawDegrees(),
                object.pitchDegrees(),
                object.rollDegrees(),
                object.scale(),
                object.modelBrightness(),
                object.blocksMovement(),
                object.lightOverride());
    }

    private MapDesignLibrary.MapPlacement localPreviewPlacement(
            MapDesignLibrary.MapPlacement placement,
            int centerX,
            int centerY,
            int center
    ) {
        if (placement == null) {
            return null;
        }
        int localX = placement.x() - centerX + center;
        int localY = placement.y() - centerY + center;
        if (localX < 0 || localY < 0 || localX >= 3 || localY >= 3) {
            return null;
        }
        return new MapDesignLibrary.MapPlacement(placement.kind(), placement.id(), localX, localY);
    }

    private void addPreviewPlacedObject(
            List<MapEntity> entities,
            List<MapLight> lights,
            MapDesignLibrary.PlacedObjectInstance object
    ) {
        if (object == null) {
            return;
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.FURNITURE) {
            MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(object.id());
            if (furniture == null) {
                return;
            }
            MapEntity entity = furniture.createEntity(object);
            if (entity != null) {
                entities.add(entity);
            }
            MapLight light = furniture.createLight(object);
            if (light != null) {
                lights.add(light);
            }
            return;
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE) {
            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(object.id());
            if (node == null) {
                return;
            }
            MapEntity entity = node.createEntity(object.x(), object.y());
            if (entity != null) {
                entity.withStaticModelTransform(
                        object.offsetX(),
                        object.offsetY(),
                        object.offsetZ(),
                        object.yawDegrees(),
                        object.pitchDegrees(),
                        object.rollDegrees(),
                        object.scale());
                entity.withStaticModelBrightness(object.modelBrightness());
                entities.add(entity);
            }
            MapLight light = node.createLight(object);
            if (light != null) {
                lights.add(light);
            }
        }
    }

    private void addPreviewMapPlacement(
            List<MapEntity> entities,
            List<MapLight> lights,
            MapDesignLibrary.MapPlacement placement
    ) {
        if (placement == null || placement.kind() == null) {
            return;
        }
        try {
            switch (placement.kind()) {
                case CRAFTING_NODE -> entities.add(CraftingStationType.valueOf(placement.id())
                        .createEntity(placement.x(), placement.y()));
                case GATHERING_NODE -> addPreviewGatheringPlacement(entities, lights, placement);
                case FURNITURE -> addPreviewFurniturePlacement(entities, lights, placement);
                case CUSTOM_NPC -> {
                    MapDesignLibrary.CustomNpc npc = findCustomNpc(placement.id());
                    if (npc != null) {
                        entities.add(npc.createEntity(placement.x(), placement.y()));
                    }
                }
                case ENEMY -> {
                    MapDesignLibrary.CustomMob mob = findCustomMob(placement.id());
                    if (mob != null) {
                        entities.add(new MapEntity(mob.createMonster(), placement.x(), placement.y()));
                    }
                }
                case ITEM -> {
                    InventorySystem.Item item = createPreviewItem(placement.id());
                    if (item != null) {
                        entities.add(new MapEntity(item, placement.x(), placement.y()));
                    }
                }
                case INTERACTION -> {
                    // Interactions are invisible tile logic, so the 3D preview has nothing to render.
                }
            }
        } catch (RuntimeException ignored) {
            // A malformed placement should not kill the live preview window.
        }
    }

    private void addPreviewGatheringPlacement(
            List<MapEntity> entities,
            List<MapLight> lights,
            MapDesignLibrary.MapPlacement placement
    ) {
        MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(placement.id());
        if (node == null) {
            return;
        }
        MapEntity entity = node.createEntity(placement.x(), placement.y());
        if (entity != null) {
            entities.add(entity);
        }
        MapDesignLibrary.PlacedObjectInstance object = new MapDesignLibrary.PlacedObjectInstance(
                "preview_gathering_" + placement.id() + "_" + placement.x() + "_" + placement.y(),
                placement.kind(),
                placement.id(),
                placement.x(),
                placement.y(),
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                false,
                null);
        MapLight light = node.createLight(object);
        if (light != null) {
            lights.add(light);
        }
    }

    private void addPreviewFurniturePlacement(
            List<MapEntity> entities,
            List<MapLight> lights,
            MapDesignLibrary.MapPlacement placement
    ) {
        MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(placement.id());
        if (furniture == null) {
            return;
        }
        MapDesignLibrary.PlacedObjectInstance object = MapDesignLibrary.PlacedObjectInstance.furniture(
                "preview_furniture_" + placement.id() + "_" + placement.x() + "_" + placement.y(),
                placement.id(),
                placement.x(),
                placement.y(),
                furniture.defaultBlocksMovement());
        MapEntity entity = furniture.createEntity(object);
        if (entity != null) {
            entities.add(entity);
        }
        MapLight light = furniture.createLight(object);
        if (light != null) {
            lights.add(light);
        }
    }

    private InventorySystem.Item createPreviewItem(String itemId) {
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            if (item.itemId().equals(itemId)) {
                return item.createItem();
            }
        }
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            if (limb.limbId().equals(itemId)) {
                return limb.createLimb();
            }
        }
        return null;
    }

    private MapDesignLibrary.CustomGatheringNode findCustomGatheringNode(String nodeId) {
        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            if (node.nodeId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    private MapDesignLibrary.CustomNpc findCustomNpc(String npcId) {
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            if (npc.npcId().equals(npcId)) {
                return npc;
            }
        }
        return null;
    }

    private MapDesignLibrary.CustomMob findCustomMob(String mobId) {
        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            if (mob.mobId().equals(mobId)) {
                return mob;
            }
        }
        return null;
    }

    private MapDesignLibrary.AuthoredDialogue findAuthoredDialogue(String interactionId) {
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            if (dialogue.interactionId().equals(interactionId)) {
                return dialogue;
            }
        }
        return null;
    }

    private MapDesignLibrary.LightAttachment effectiveLightFor(MapDesignLibrary.PlacedObjectInstance object) {
        if (object == null) {
            return null;
        }
        if (object.lightOverride() != null) {
            return object.lightOverride();
        }
        MapDesignLibrary.CustomFurnitureDefinition furniture = object.kind() == MapDesignLibrary.PlacementKind.FURNITURE
                ? findFurnitureDefinition(object.id())
                : null;
        return furniture == null ? null : furniture.lightAttachment();
    }

    private MapDesignLibrary.LightAttachment defaultLightForPlaceable(PlaceableOption option) {
        if (option == null || option.kind() != MapDesignLibrary.PlacementKind.FURNITURE) {
            if (option == null || option.kind() != MapDesignLibrary.PlacementKind.GATHERING_NODE) {
                return null;
            }
            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(option.id());
            return node == null ? null : node.lightAttachment();
        }
        MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(option.id());
        return furniture == null ? null : furniture.lightAttachment();
    }

    private MapDesignLibrary.LightAttachment lightAttachmentFromControls(
            JCheckBox enabledBox,
            JTextField colorField,
            JSpinner radiusSpinner,
            JSpinner intensitySpinner,
            JSpinner offsetXSpinner,
            JSpinner offsetYSpinner,
            JSpinner offsetZSpinner,
            JSpinner flickerSpinner) {
        return new MapDesignLibrary.LightAttachment(
                enabledBox.isSelected(),
                MapLightingSettings.parseColor(colorField.getText(), 0xFF8B42),
                ((Number) radiusSpinner.getValue()).doubleValue(),
                ((Number) intensitySpinner.getValue()).doubleValue(),
                ((Number) offsetXSpinner.getValue()).doubleValue(),
                ((Number) offsetYSpinner.getValue()).doubleValue(),
                ((Number) offsetZSpinner.getValue()).doubleValue(),
                ((Number) flickerSpinner.getValue()).doubleValue());
    }

    private void applyLightAttachmentToControls(
            MapDesignLibrary.LightAttachment light,
            JCheckBox enabledBox,
            JTextField colorField,
            JSpinner radiusSpinner,
            JSpinner intensitySpinner,
            JSpinner offsetXSpinner,
            JSpinner offsetYSpinner,
            JSpinner offsetZSpinner,
            JSpinner flickerSpinner) {
        if (light == null) {
            return;
        }
        enabledBox.setSelected(light.enabled());
        colorField.setText(MapLightingSettings.colorHex(light.colorRgb()));
        radiusSpinner.setValue(light.radius());
        intensitySpinner.setValue(light.intensity());
        offsetXSpinner.setValue(light.offsetX());
        offsetYSpinner.setValue(light.offsetY());
        offsetZSpinner.setValue(light.offsetZ());
        flickerSpinner.setValue(light.flickerAmount());
    }

    private String placedObjectLabel(MapDesignLibrary.PlacedObjectInstance object) {
        if (object == null) {
            return "";
        }
        return placedObjectDisplayName(object)
                + " [" + object.instanceId() + "] "
                + "off " + formatDouble(object.offsetX()) + "," + formatDouble(object.offsetZ())
                + " yaw " + formatDouble(object.yawDegrees());
    }

    private String placedObjectDisplayName(MapDesignLibrary.PlacedObjectInstance object) {
        if (object == null) {
            return "";
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.FURNITURE) {
            MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(object.id());
            return furniture == null ? object.id() : furniture.displayName();
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE) {
            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(object.id());
            return node == null ? object.id() : node.displayName();
        }
        return object.id();
    }

    private String placedObjectModelPath(MapDesignLibrary.PlacedObjectInstance object) {
        if (object == null) {
            return "";
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.FURNITURE) {
            MapDesignLibrary.CustomFurnitureDefinition furniture = findFurnitureDefinition(object.id());
            return furniture == null ? "" : furniture.modelPath();
        }
        if (object.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE) {
            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(object.id());
            return node == null ? "" : node.getModelForExhaustion(0);
        }
        return "";
    }

    private MapDesignLibrary.CustomFurnitureDefinition findFurnitureDefinition(String furnitureId) {
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            if (furniture.furnitureId().equals(furnitureId)) {
                return furniture;
            }
        }
        return null;
    }

    private boolean isPlacementTargetOpen(int x, int y) {
        if (x < 0 || y < 0 || x >= design.width() || y >= design.height()) {
            return false;
        }
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.x() == x && placement.y() == y) {
                return false;
            }
        }
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (object.x() == x && object.y() == y) {
                return false;
            }
        }
        return true;
    }

    private void deleteSelectedContent() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            return;
        }
        Object value = entry.value();
        if (value instanceof SkillDefinition skill) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.SKILL, skill.id());
            return;
        } else if (value instanceof StatusDefinition status) {
            openBattleContentEditor(BattleSkillEditorWorkspace.Kind.STATUS, status.id());
            return;
        } else if (value instanceof FirstPersonCombatLibrary.RigDefinition
                || value instanceof FirstPersonCombatLibrary.AnimationSet
                || value instanceof FirstPersonCombatLibrary.ItemProfile) {
            openFirstPersonViewmodelEditor();
            setStatus("Use the First-Person Viewmodels workspace Delete action so references are rewritten.");
            return;
        }
        if (value instanceof MaterialDefinition material) {
            deleteMaterialTier(material);
        } else if (value instanceof MapDesignLibrary.CustomItem item) {
            deleteCustomItem(item);
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            deleteCustomMob(mob);
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            deleteCustomNpc(npc);
        } else if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            deleteCustomFurniture(furniture);
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            deleteCustomLimb(limb);
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            deleteCustomGatheringNode(node);
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            deleteCookingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            deleteCraftingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind.QUEST, quest.questId());
            setStatus("Use the Quest workspace Delete action so references are checked.");
            return;
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            openQuestDialogueEditor(QuestDialogueEditorWorkspace.Kind.DIALOGUE, dialogue.interactionId());
            setStatus("Use the Dialogue workspace Delete action so references are checked.");
            return;
        } else if (value instanceof MobAreaEntry area) {
            deleteMobArea(area);
        } else if (value instanceof MapLight light) {
            deleteLight(light);
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            deleteTrigger(trigger);
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            deleteMapPlacement(placement);
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            deletePlacedObject(object);
        } else if (value instanceof MapDesignLibrary.ValidationIssue) {
            setStatus("Diagnostics cannot be deleted; fix the referenced content instead.");
        }
        refreshContentBrowser();
    }

    private void deleteMaterialTier(MaterialDefinition material) {
        if (material == null || material.id().equals("none")) {
            setStatus("The reserved none material cannot be deleted.");
            return;
        }
        List<MapDesignLibrary.CustomItem> references = design.customItems().stream()
                .filter(item -> item.material().id().equals(material.id()))
                .toList();
        GearMaterial replacement = GearMaterial.NONE;
        if (!references.isEmpty()) {
            List<GearMaterial> choices = java.util.Arrays.stream(GearMaterial.values())
                    .filter(candidate -> !candidate.id().equals(material.id()))
                    .toList();
            JComboBox<GearMaterial> replacementBox = new JComboBox<>(choices.toArray(new GearMaterial[0]));
            JPanel panel = createFormPanel();
            panel.add(new JLabel(references.size() + " item(s) use this material. Select their replacement."));
            panel.add(formRow("Replacement", replacementBox));
            if (showScrollableFormDialog(panel, "Delete Material " + material.displayName())
                    != JOptionPane.OK_OPTION) {
                return;
            }
            replacement = (GearMaterial) replacementBox.getSelectedItem();
            if (replacement == null) {
                setStatus("Referenced materials require a replacement.");
                return;
            }
        } else if (showAdaptiveTextConfirmDialog(this,
                "Delete material '" + material.displayName() + "'?",
                "Delete Material", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        List<MaterialDefinition> beforeMaterials = new ArrayList<>(MaterialCatalog.snapshot().definitions());
        List<MapDesignLibrary.CustomItem> beforeItems = new ArrayList<>(design.customItems());
        for (int index = 0; index < design.customItems().size(); index++) {
            MapDesignLibrary.CustomItem item = design.customItems().get(index);
            if (item.material().id().equals(material.id())) {
                design.customItems().set(index, item.withMaterial(replacement));
            }
        }
        List<MaterialDefinition> after = beforeMaterials.stream()
                .filter(candidate -> !candidate.id().equals(material.id()))
                .toList();
        try {
            MaterialCatalog.write(after);
            if (!references.isEmpty() && !persistSharedContent("material replacement")) {
                throw new IOException("Dependent items could not be saved.");
            }
            removeMaterialConfiguration(material.id());
            MaterialCatalog.refresh();
            refreshContentBrowser();
            setStatus("Deleted material " + material.displayName() + ".");
        } catch (IOException | RuntimeException exception) {
            design.customItems().clear();
            design.customItems().addAll(beforeItems);
            try {
                MaterialCatalog.write(beforeMaterials);
                MaterialCatalog.refresh();
                if (!references.isEmpty()) {
                    persistSharedContent("material deletion rollback");
                }
            } catch (IOException ignored) {
                // Report the original transaction failure.
            }
            setStatus("Material deletion failed: " + exception.getMessage());
        }
    }

    private void removeMaterialConfiguration(String materialId) throws IOException {
        Properties properties = loadPackagedConfigurationProperties();
        List<String> removedKeys = new ArrayList<>();
        for (CharacterSkill skill : CharacterSkill.values()) {
            String key = EquipmentRequirementRules.configurationKey(skill, GearMaterial.of(materialId));
            properties.remove(key);
            removedKeys.add(key);
        }
        String xpKey = SmithingExperienceRules.configurationKey(GearMaterial.of(materialId));
        properties.remove(xpKey);
        removedKeys.add(xpKey);
        writePropertiesAtomically(CONFIG_RESOURCE_PATH, properties,
                "Aether packaged gameplay configuration");
        for (String key : removedKeys) {
            GameConfiguration.removeValue(key);
        }
    }

    private void deleteMapPlacement(MapDesignLibrary.MapPlacement placement) {
        captureHistory("delete placement");
        design.placements().remove(placement);
        clearMapSelection();
        mapCanvas.repaint();
        markDirty(true);
        refreshContentBrowser();
        setStatus("Removed placement " + placement.id() + ".");
    }

    private void deletePlacedObject(MapDesignLibrary.PlacedObjectInstance object) {
        captureHistory("delete placed object");
        design.placedObjects().remove(object);
        clearMapSelection();
        mapCanvas.repaint();
        markDirty(true);
        refreshContentBrowser();
        setStatus("Removed placed object " + object.id() + ".");
    }

    private void editPlacedObject(MapDesignLibrary.PlacedObjectInstance object) {
        MapDesignLibrary.PlacedObjectInstance edited = showPlacedObjectDialog(object);
        if (edited == null) {
            return;
        }

        int index = design.placedObjects().indexOf(object);
        if (index < 0) {
            setStatus("Placed object no longer exists.");
            return;
        }

        captureHistory("edit placed object");
        design.placedObjects().set(index, edited);
        markDirty(true);
        refreshContentBrowser();
        revealPlacedObject(edited);
        setStatus("Updated placed object " + edited.id() + ".");
    }

    private void manageTileObjects(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= design.width() || tileY >= design.height()) {
            setStatus("Tile is outside the map.");
            return;
        }

        DefaultListModel<MapDesignLibrary.PlacedObjectInstance> model = new DefaultListModel<>();
        for (MapDesignLibrary.PlacedObjectInstance object : placedObjectsAt(tileX, tileY)) {
            model.addElement(object);
        }
        JList<MapDesignLibrary.PlacedObjectInstance> objectList = new JList<>(model);
        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText(placedObjectLabel(value));
            return label;
        });
        if (!model.isEmpty()) {
            objectList.setSelectedIndex(0);
        }

        TileObjectPreviewPanel previewPanel = new TileObjectPreviewPanel(tileX, tileY, model, objectList);
        objectList.addListSelectionListener(event -> previewPanel.repaint());

        JButton addButton = new JButton("Add Object");
        JButton editButton = new JButton("Edit");
        JButton duplicateButton = new JButton("Duplicate");
        JButton deleteButton = new JButton("Delete");
        JButton upButton = new JButton("Move Up");
        JButton downButton = new JButton("Move Down");

        Runnable refreshAfterChange = () -> {
            markDirty(true);
            refreshContentBrowser();
            previewPanel.repaint();
            mapCanvas.repaint();
        };

        addButton.addActionListener(event -> {
            MapDesignLibrary.PlacedObjectInstance initial = defaultPlacedObjectAt(tileX, tileY);
            if (initial == null) {
                return;
            }
            MapDesignLibrary.PlacedObjectInstance created = showPlacedObjectDialog(initial);
            if (created == null) {
                return;
            }
            captureHistory("add placed object");
            design.placedObjects().add(created);
            if (created.x() == tileX && created.y() == tileY) {
                model.addElement(created);
                objectList.setSelectedValue(created, true);
            }
            refreshAfterChange.run();
            setStatus("Added placed object " + created.id() + " at " + created.x() + "," + created.y() + ".");
        });

        editButton.addActionListener(event -> {
            MapDesignLibrary.PlacedObjectInstance selected = objectList.getSelectedValue();
            if (selected == null) {
                setStatus("Select a placed object to edit.");
                return;
            }
            MapDesignLibrary.PlacedObjectInstance edited = showPlacedObjectDialog(selected);
            if (edited == null) {
                return;
            }
            int designIndex = design.placedObjects().indexOf(selected);
            int listIndex = objectList.getSelectedIndex();
            if (designIndex < 0 || listIndex < 0) {
                setStatus("Placed object no longer exists.");
                return;
            }
            captureHistory("edit placed object");
            design.placedObjects().set(designIndex, edited);
            if (edited.x() == tileX && edited.y() == tileY) {
                model.set(listIndex, edited);
                objectList.setSelectedIndex(listIndex);
            } else {
                model.remove(listIndex);
            }
            refreshAfterChange.run();
            revealPlacedObject(edited);
            setStatus("Updated placed object " + edited.id() + ".");
        });

        duplicateButton.addActionListener(event -> {
            MapDesignLibrary.PlacedObjectInstance selected = objectList.getSelectedValue();
            if (selected == null) {
                setStatus("Select a placed object to duplicate.");
                return;
            }
            captureHistory("duplicate placed object on tile");
            MapDesignLibrary.PlacedObjectInstance duplicated = copyPlacedObjectAt(
                    selected,
                    nextPlacedObjectId(selected.id()),
                    tileX,
                    tileY);
            design.placedObjects().add(duplicated);
            model.addElement(duplicated);
            objectList.setSelectedValue(duplicated, true);
            refreshAfterChange.run();
            setStatus("Duplicated placed object " + selected.id() + " on tile " + tileX + "," + tileY + ".");
        });

        deleteButton.addActionListener(event -> {
            MapDesignLibrary.PlacedObjectInstance selected = objectList.getSelectedValue();
            int listIndex = objectList.getSelectedIndex();
            if (selected == null || listIndex < 0) {
                setStatus("Select a placed object to delete.");
                return;
            }
            captureHistory("delete placed object");
            design.placedObjects().remove(selected);
            model.remove(listIndex);
            if (!model.isEmpty()) {
                objectList.setSelectedIndex(Math.min(listIndex, model.size() - 1));
            }
            refreshAfterChange.run();
            setStatus("Removed placed object " + selected.id() + ".");
        });

        upButton.addActionListener(event -> moveTileObjectInOrder(objectList, model, -1, refreshAfterChange));
        downButton.addActionListener(event -> moveTileObjectInOrder(objectList, model, 1, refreshAfterChange));

        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(duplicateButton);
        buttons.add(deleteButton);
        buttons.add(upButton);
        buttons.add(downButton);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(new JScrollPane(objectList), BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("Tile " + tileX + "," + tileY + " placed objects");
        content.add(title, BorderLayout.NORTH);
        content.add(left, BorderLayout.CENTER);
        content.add(previewPanel, BorderLayout.EAST);

        showScrollableFormDialog(content, "Manage Tile Objects");
        refreshContentBrowser();
        mapCanvas.repaint();
    }

    private void moveTileObjectInOrder(
            JList<MapDesignLibrary.PlacedObjectInstance> objectList,
            DefaultListModel<MapDesignLibrary.PlacedObjectInstance> model,
            int delta,
            Runnable refreshAfterChange) {
        int index = objectList.getSelectedIndex();
        if (index < 0) {
            setStatus("Select a placed object to reorder.");
            return;
        }
        int targetIndex = index + delta;
        if (targetIndex < 0 || targetIndex >= model.size()) {
            return;
        }

        MapDesignLibrary.PlacedObjectInstance selected = model.get(index);
        MapDesignLibrary.PlacedObjectInstance target = model.get(targetIndex);
        int selectedDesignIndex = design.placedObjects().indexOf(selected);
        int targetDesignIndex = design.placedObjects().indexOf(target);
        if (selectedDesignIndex < 0 || targetDesignIndex < 0) {
            setStatus("Placed object no longer exists.");
            return;
        }

        captureHistory("reorder placed object");
        design.placedObjects().set(selectedDesignIndex, target);
        design.placedObjects().set(targetDesignIndex, selected);
        model.set(index, target);
        model.set(targetIndex, selected);
        objectList.setSelectedIndex(targetIndex);
        refreshAfterChange.run();
        setStatus("Moved placed object " + selected.id() + ".");
    }

    private MapDesignLibrary.PlacedObjectInstance defaultPlacedObjectAt(int x, int y) {
        List<PlaceableOption> objectOptions = transformedObjectOptions();
        if (objectOptions.isEmpty()) {
            setStatus("Create a furniture definition or a 3D gathering node first.");
            return null;
        }
        PlaceableOption option = objectOptions.get(0);
        return new MapDesignLibrary.PlacedObjectInstance(
                nextPlacedObjectId(option.id()),
                option.kind(),
                option.id(),
                x,
                y,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                defaultBlocksMovementFor(option),
                null);
    }

    private MapDesignLibrary.PlacedObjectInstance showPlacedObjectDialog(MapDesignLibrary.PlacedObjectInstance object) {
        if (object == null) {
            return null;
        }

        JComboBox<PlaceableOption> optionBox = new JComboBox<>(
                transformedObjectOptionsFor(object).toArray(new PlaceableOption[0]));
        if (!selectPlaceableOption(optionBox, object.kind(), object.id())) {
            optionBox.setSelectedIndex(Math.max(0, optionBox.getItemCount() - 1));
        }
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(object.x(), 0, design.width() - 1, 1));
        JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(object.y(), 0, design.height() - 1, 1));
        JSpinner offsetXSpinner = new JSpinner(new SpinnerNumberModel(clampTileOffset(object.offsetX()), -0.5, 0.5, 0.05));
        JSpinner offsetYSpinner = new JSpinner(new SpinnerNumberModel(object.offsetY(), -8.0, 8.0, 0.05));
        JSpinner offsetZSpinner = new JSpinner(new SpinnerNumberModel(clampTileOffset(object.offsetZ()), -0.5, 0.5, 0.05));
        JSpinner yawSpinner = new JSpinner(new SpinnerNumberModel(object.yawDegrees(), 0.0, 359.0, 5.0));
        JSpinner pitchSpinner = new JSpinner(new SpinnerNumberModel(object.pitchDegrees(), 0.0, 359.0, 5.0));
        JSpinner rollSpinner = new JSpinner(new SpinnerNumberModel(object.rollDegrees(), 0.0, 359.0, 5.0));
        JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(object.scale(), 0.05, 20.0, 0.05));
        JSpinner modelBrightnessSpinner = new JSpinner(new SpinnerNumberModel(object.modelBrightness(), 0.0, 4.0, 0.05));
        JCheckBox blocksMovementBox = new JCheckBox("Blocks movement", object.blocksMovement());
        JButton resetTransformButton = new JButton("Reset Transform");
        JButton open3dPreviewButton = new JButton("Open 3D Preview");

        MapDesignLibrary.LightAttachment light = object.lightOverride();
        JCheckBox overrideLightBox = new JCheckBox("Override attached light", light != null);
        JCheckBox lightEnabledBox = new JCheckBox("Light enabled", light == null || light.enabled());
        JTextField lightColorField = new JTextField(
                MapLightingSettings.colorHex(light == null ? 0xFF8B42 : light.colorRgb()),
                10);
        JSpinner lightRadiusSpinner = new JSpinner(new SpinnerNumberModel(light == null ? 5.0 : light.radius(), 0.1, 64.0, 0.1));
        JSpinner lightIntensitySpinner = new JSpinner(new SpinnerNumberModel(light == null ? 1.0 : light.intensity(), 0.0, 8.0, 0.05));
        JSpinner lightOffsetXSpinner = new JSpinner(new SpinnerNumberModel(light == null ? 0.0 : light.offsetX(), -4.0, 4.0, 0.05));
        JSpinner lightOffsetYSpinner = new JSpinner(new SpinnerNumberModel(light == null ? 0.65 : light.offsetY(), -8.0, 8.0, 0.05));
        JSpinner lightOffsetZSpinner = new JSpinner(new SpinnerNumberModel(light == null ? 0.0 : light.offsetZ(), -4.0, 4.0, 0.05));
        JSpinner flickerSpinner = new JSpinner(new SpinnerNumberModel(light == null ? 0.0 : light.flickerAmount(), 0.0, 1.0, 0.01));
        JButton copyDefaultLightButton = new JButton("Copy Default Light");
        copyDefaultLightButton.setEnabled(defaultLightForPlaceable((PlaceableOption) optionBox.getSelectedItem()) != null);

        JPanel fields = createFormPanel();
        addFormRow(fields, "Object", optionBox);
        addFormRow(fields, "Tile X", xSpinner);
        addFormRow(fields, "Tile Y", ySpinner);
        addFormRow(fields, "Offset X", offsetXSpinner);
        addFormRow(fields, "Offset Y", offsetYSpinner);
        addFormRow(fields, "Offset Z", offsetZSpinner);
        addFormRow(fields, "Yaw", yawSpinner);
        addFormRow(fields, "Pitch", pitchSpinner);
        addFormRow(fields, "Roll", rollSpinner);
        addFormRow(fields, "Scale", scaleSpinner);
        addFormRow(fields, "Model Brightness", modelBrightnessSpinner);
        addFormRow(fields, "", blocksMovementBox);
        addFormRow(fields, "", resetTransformButton);
        addFormRow(fields, "", open3dPreviewButton);
        addFormRow(fields, "", overrideLightBox);
        addFormRow(fields, "", copyDefaultLightButton);
        JPanel lightEnabledRow = formRow("", lightEnabledBox);
        JPanel lightColorRow = formRow("Light Color", lightColorField);
        JPanel lightRadiusRow = formRow("Light Radius", lightRadiusSpinner);
        JPanel lightIntensityRow = formRow("Light Intensity", lightIntensitySpinner);
        JPanel lightOffsetXRow = formRow("Light Offset X", lightOffsetXSpinner);
        JPanel lightOffsetYRow = formRow("Light Offset Y", lightOffsetYSpinner);
        JPanel lightOffsetZRow = formRow("Light Offset Z", lightOffsetZSpinner);
        JPanel flickerRow = formRow("Flicker", flickerSpinner);
        fields.add(lightEnabledRow);
        fields.add(lightColorRow);
        fields.add(lightRadiusRow);
        fields.add(lightIntensityRow);
        fields.add(lightOffsetXRow);
        fields.add(lightOffsetYRow);
        fields.add(lightOffsetZRow);
        fields.add(flickerRow);

        Supplier<MapDesignLibrary.PlacedObjectInstance> previewObject = () -> {
            PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
            String selectedId = selected == null ? object.id() : selected.id();
            MapDesignLibrary.PlacementKind selectedKind = selected == null ? object.kind() : selected.kind();
            MapDesignLibrary.LightAttachment lightOverride = overrideLightBox.isSelected()
                    ? new MapDesignLibrary.LightAttachment(
                            lightEnabledBox.isSelected(),
                            MapLightingSettings.parseColor(lightColorField.getText(), 0xFF8B42),
                            ((Number) lightRadiusSpinner.getValue()).doubleValue(),
                            ((Number) lightIntensitySpinner.getValue()).doubleValue(),
                            ((Number) lightOffsetXSpinner.getValue()).doubleValue(),
                            ((Number) lightOffsetYSpinner.getValue()).doubleValue(),
                            ((Number) lightOffsetZSpinner.getValue()).doubleValue(),
                            ((Number) flickerSpinner.getValue()).doubleValue())
                    : null;
            return new MapDesignLibrary.PlacedObjectInstance(
                    object.instanceId(),
                    selectedKind,
                    selectedId,
                    ((Number) xSpinner.getValue()).intValue(),
                    ((Number) ySpinner.getValue()).intValue(),
                    ((Number) offsetXSpinner.getValue()).doubleValue(),
                    ((Number) offsetYSpinner.getValue()).doubleValue(),
                    ((Number) offsetZSpinner.getValue()).doubleValue(),
                    ((Number) yawSpinner.getValue()).doubleValue(),
                    ((Number) pitchSpinner.getValue()).doubleValue(),
                    ((Number) rollSpinner.getValue()).doubleValue(),
                    ((Number) scaleSpinner.getValue()).doubleValue(),
                    ((Number) modelBrightnessSpinner.getValue()).doubleValue(),
                    blocksMovementBox.isSelected(),
                    lightOverride);
        };
        PlacedObjectEditPreviewPanel previewPanel = new PlacedObjectEditPreviewPanel(
                previewObject,
                offsetXSpinner,
                offsetZSpinner,
                yawSpinner);

        ChangeListener previewChangeListener = event -> previewPanel.repaint();
        xSpinner.addChangeListener(previewChangeListener);
        ySpinner.addChangeListener(previewChangeListener);
        offsetXSpinner.addChangeListener(previewChangeListener);
        offsetYSpinner.addChangeListener(previewChangeListener);
        offsetZSpinner.addChangeListener(previewChangeListener);
        yawSpinner.addChangeListener(previewChangeListener);
        pitchSpinner.addChangeListener(previewChangeListener);
        rollSpinner.addChangeListener(previewChangeListener);
        scaleSpinner.addChangeListener(previewChangeListener);
        modelBrightnessSpinner.addChangeListener(previewChangeListener);
        lightRadiusSpinner.addChangeListener(previewChangeListener);
        lightIntensitySpinner.addChangeListener(previewChangeListener);
        lightOffsetXSpinner.addChangeListener(previewChangeListener);
        lightOffsetYSpinner.addChangeListener(previewChangeListener);
        lightOffsetZSpinner.addChangeListener(previewChangeListener);
        flickerSpinner.addChangeListener(previewChangeListener);
        optionBox.addActionListener(event -> {
            PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
            blocksMovementBox.setSelected(defaultBlocksMovementFor(selected));
            copyDefaultLightButton.setEnabled(defaultLightForPlaceable(selected) != null);
            previewPanel.repaint();
        });
        resetTransformButton.addActionListener(event -> {
            PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
            offsetXSpinner.setValue(0.0);
            offsetYSpinner.setValue(0.0);
            offsetZSpinner.setValue(0.0);
            yawSpinner.setValue(0.0);
            pitchSpinner.setValue(0.0);
            rollSpinner.setValue(0.0);
            scaleSpinner.setValue(1.0);
            modelBrightnessSpinner.setValue(1.0);
            blocksMovementBox.setSelected(defaultBlocksMovementFor(selected));
            previewPanel.repaint();
            setStatus("Reset placed object transform to definition defaults.");
        });
        open3dPreviewButton.addActionListener(event -> launchPlacedObject3dPreview(previewObject));
        blocksMovementBox.addActionListener(event -> previewPanel.repaint());
        lightEnabledBox.addActionListener(event -> previewPanel.repaint());
        lightColorField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                previewPanel.repaint();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                previewPanel.repaint();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                previewPanel.repaint();
            }
        });

        Runnable updateLightRows = () -> {
            boolean visible = overrideLightBox.isSelected();
            lightEnabledRow.setVisible(visible);
            lightColorRow.setVisible(visible);
            lightRadiusRow.setVisible(visible);
            lightIntensityRow.setVisible(visible);
            lightOffsetXRow.setVisible(visible);
            lightOffsetYRow.setVisible(visible);
            lightOffsetZRow.setVisible(visible);
            flickerRow.setVisible(visible);
            fields.revalidate();
            fields.repaint();
            previewPanel.repaint();
        };
        overrideLightBox.addActionListener(event -> updateLightRows.run());
        copyDefaultLightButton.addActionListener(event -> {
            PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
            MapDesignLibrary.LightAttachment defaultLight = defaultLightForPlaceable(selected);
            if (defaultLight == null) {
                setStatus("Selected object has no default attached light.");
                return;
            }
            overrideLightBox.setSelected(true);
            lightEnabledBox.setSelected(defaultLight.enabled());
            lightColorField.setText(MapLightingSettings.colorHex(defaultLight.colorRgb()));
            lightRadiusSpinner.setValue(defaultLight.radius());
            lightIntensitySpinner.setValue(defaultLight.intensity());
            lightOffsetXSpinner.setValue(defaultLight.offsetX());
            lightOffsetYSpinner.setValue(defaultLight.offsetY());
            lightOffsetZSpinner.setValue(defaultLight.offsetZ());
            flickerSpinner.setValue(defaultLight.flickerAmount());
            updateLightRows.run();
            setStatus("Copied default attached light for " + selected.label() + ".");
        });
        updateLightRows.run();

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.add(fields, BorderLayout.CENTER);
        content.add(previewPanel, BorderLayout.EAST);

        if (showScrollableFormDialog(content, "Edit Placed Object") != JOptionPane.OK_OPTION) {
            return null;
        }

        PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
        if (selected == null || selected.id().isBlank()) {
            setStatus("Placed object needs a furniture or 3D gathering definition.");
            return null;
        }

        MapDesignLibrary.LightAttachment lightOverride = overrideLightBox.isSelected()
                ? new MapDesignLibrary.LightAttachment(
                        lightEnabledBox.isSelected(),
                        MapLightingSettings.parseColor(lightColorField.getText(), 0xFF8B42),
                        ((Number) lightRadiusSpinner.getValue()).doubleValue(),
                        ((Number) lightIntensitySpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetXSpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetYSpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetZSpinner.getValue()).doubleValue(),
                        ((Number) flickerSpinner.getValue()).doubleValue())
                : null;
        return new MapDesignLibrary.PlacedObjectInstance(
                object.instanceId(),
                selected.kind(),
                selected.id(),
                ((Number) xSpinner.getValue()).intValue(),
                ((Number) ySpinner.getValue()).intValue(),
                ((Number) offsetXSpinner.getValue()).doubleValue(),
                ((Number) offsetYSpinner.getValue()).doubleValue(),
                ((Number) offsetZSpinner.getValue()).doubleValue(),
                ((Number) yawSpinner.getValue()).doubleValue(),
                        ((Number) pitchSpinner.getValue()).doubleValue(),
                        ((Number) rollSpinner.getValue()).doubleValue(),
                        ((Number) scaleSpinner.getValue()).doubleValue(),
                        ((Number) modelBrightnessSpinner.getValue()).doubleValue(),
                        blocksMovementBox.isSelected(),
                        lightOverride);
    }

    private void selectContentForPlacement() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            return;
        }
        if (entry.value() instanceof MobAreaEntry area) {
            mobAreaBox.setSelectedItem(area.areaId());
            paintModeBox.setSelectedItem(PaintMode.MOB_AREA);
            setStatus("Selected mob area " + area.areaId() + " for painting.");
            return;
        }
        PlaceableCategory category = switch (entry.category()) {
            case ITEMS, LIMBS -> PlaceableCategory.ITEMS;
            case ENEMIES -> PlaceableCategory.ENEMIES;
            case NPCS -> PlaceableCategory.NPCS;
            case FURNITURE -> PlaceableCategory.FURNITURE;
            case GATHERING -> PlaceableCategory.GATHERING_NODES;
            case DIALOGUES -> null;
            case PLACEMENTS -> placementCategory(entry.value());
            case DIAGNOSTICS, AREAS -> null;
            default -> null;
        };
        if (category == null) {
            setStatus(entry.value() instanceof MapDesignLibrary.AuthoredDialogue
                    ? "Assign this dialogue from the Dialogue field in Create/Edit NPC."
                    : entry.type() + " cannot be painted directly.");
            return;
        }

        String id = entry.value() instanceof MapDesignLibrary.MapPlacement placement
                ? placement.id()
                : entry.value() instanceof MapDesignLibrary.PlacedObjectInstance object
                ? object.id()
                : entry.id();
        placeableCategoryBox.setSelectedItem(category);
        populatePlaceables();
        selectPlaceable(id);
        paintModeBox.setSelectedItem(PaintMode.PLACE_OBJECT);
        setStatus("Selected " + entry.label() + " for placement.");
    }

    private void findSelectedContentOnMap() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            setStatus("Select content before finding it on the map.");
            return;
        }

        if (entry.value() instanceof MapDesignLibrary.MapTrigger trigger) {
            revealMapTrigger(trigger);
            setStatus("Found trigger " + trigger.id() + " at " + trigger.x() + "," + trigger.y() + ".");
            return;
        }
        if (entry.value() instanceof MapDesignLibrary.PlacedObjectInstance object) {
            revealPlacedObject(object);
            setStatus("Found placed object " + object.id() + " at " + object.x() + "," + object.y() + ".");
            return;
        }
        if (entry.value() instanceof MapDesignLibrary.ValidationIssue issue) {
            navigateDiagnostic(issue);
            return;
        }

        List<MapDesignLibrary.PlacedObjectInstance> placedObjects = placedObjectsForContent(entry);
        if (!placedObjects.isEmpty()) {
            String findKey = "placed|" + entry.category() + "|" + entry.type() + "|" + entry.id();
            if (!findKey.equals(lastFindKey)) {
                lastFindKey = findKey;
                lastFindIndex = -1;
            }
            lastFindIndex = (lastFindIndex + 1) % placedObjects.size();
            MapDesignLibrary.PlacedObjectInstance object = placedObjects.get(lastFindIndex);
            revealPlacedObject(object);
            setStatus("Found " + entry.label() + " placed object "
                    + (lastFindIndex + 1) + "/" + placedObjects.size()
                    + " at " + object.x() + "," + object.y() + ".");
            return;
        }

        List<MapDesignLibrary.MapPlacement> placements = placementsForContent(entry);
        if (placements.isEmpty()) {
            setStatus("No placed instances found for " + entry.label() + ".");
            return;
        }

        String findKey = "placement|" + entry.category() + "|" + entry.type() + "|" + entry.id();
        if (!findKey.equals(lastFindKey)) {
            lastFindKey = findKey;
            lastFindIndex = -1;
        }
        lastFindIndex = (lastFindIndex + 1) % placements.size();
        MapDesignLibrary.MapPlacement placement = placements.get(lastFindIndex);
        revealMapPlacement(placement);
        setStatus("Found " + entry.label() + " instance "
                + (lastFindIndex + 1) + "/" + placements.size()
                + " at " + placement.x() + "," + placement.y() + ".");
    }

    private List<MapDesignLibrary.MapPlacement> placementsForContent(ContentEntry entry) {
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.MapPlacement placement) {
            return List.of(placement);
        }

        MapDesignLibrary.PlacementKind kind = placementKindForContent(entry);
        String id = placementIdForContent(entry);
        if (kind == null || id.isBlank()) {
            return List.of();
        }

        return design.placements().stream()
                .filter(placement -> placement.kind() == kind)
                .filter(placement -> id.equals(placement.id()))
                .toList();
    }

    private List<MapDesignLibrary.PlacedObjectInstance> placedObjectsForContent(ContentEntry entry) {
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            return List.of(object);
        }
        if (value instanceof MapDesignLibrary.CustomFurnitureDefinition furniture) {
            return design.placedObjects().stream()
                    .filter(object -> object.kind() == MapDesignLibrary.PlacementKind.FURNITURE)
                    .filter(object -> furniture.furnitureId().equals(object.id()))
                    .toList();
        }
        if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            return design.placedObjects().stream()
                    .filter(object -> object.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE)
                    .filter(object -> node.nodeId().equals(object.id()))
                    .toList();
        }
        return List.of();
    }

    private MapDesignLibrary.PlacementKind placementKindForContent(ContentEntry entry) {
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.CustomItem || value instanceof MapDesignLibrary.CustomLimb) {
            return MapDesignLibrary.PlacementKind.ITEM;
        }
        if (value instanceof MapDesignLibrary.CustomMob) {
            return MapDesignLibrary.PlacementKind.ENEMY;
        }
        if (value instanceof MapDesignLibrary.CustomNpc) {
            return MapDesignLibrary.PlacementKind.CUSTOM_NPC;
        }
        if (value instanceof MapDesignLibrary.CustomGatheringNode) {
            return MapDesignLibrary.PlacementKind.GATHERING_NODE;
        }
        return null;
    }

    private String placementIdForContent(ContentEntry entry) {
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.CustomItem item) {
            return item.itemId();
        }
        if (value instanceof MapDesignLibrary.CustomLimb limb) {
            return limb.limbId();
        }
        if (value instanceof MapDesignLibrary.CustomMob mob) {
            return mob.mobId();
        }
        if (value instanceof MapDesignLibrary.CustomNpc npc) {
            return npc.npcId();
        }
        if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            return node.nodeId();
        }
        if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            return dialogue.interactionId();
        }
        return "";
    }

    private void revealMapPlacement(MapDesignLibrary.MapPlacement placement) {
        revealContentEntry(placement, ContentCategory.PLACEMENTS);
        setInspectedMapSelection(placement);
        mapCanvas.scrollToTile(placement.x(), placement.y());
    }

    private void revealPlacedObject(MapDesignLibrary.PlacedObjectInstance object) {
        revealContentEntry(object, ContentCategory.PLACEMENTS);
        inspectedTile = new Point(object.x(), object.y());
        inspectedPlacement = null;
        inspectedPlacedObject = object;
        inspectedTrigger = null;
        inspectedLight = null;
        inspectedTriggerTarget = null;
        mapCanvas.scrollToTile(object.x(), object.y());
        mapCanvas.repaint();
    }

    private void revealMapTrigger(MapDesignLibrary.MapTrigger trigger) {
        revealContentEntry(trigger, ContentCategory.TRIGGERS);
        inspectedTile = new Point(trigger.x(), trigger.y());
        inspectedPlacement = null;
        inspectedPlacedObject = null;
        inspectedTrigger = trigger;
        inspectedTriggerTarget = null;
        mapCanvas.scrollToTile(trigger.x(), trigger.y());
        mapCanvas.repaint();
    }

    private void navigateDiagnostic(MapDesignLibrary.ValidationIssue issue) {
        String message = issue == null ? "" : issue.message();
        if (message.isBlank()) {
            setStatus("Diagnostic has no navigation target.");
            return;
        }

        if (message.startsWith("Spawn ")) {
            revealMapTile(design.spawnX(), design.spawnY(), "Spawn");
            return;
        }

        MapDesignLibrary.MapPlacement placement = diagnosticPlacement(message);
        if (placement != null) {
            revealMapPlacement(placement);
            setStatus("Found diagnostic placement target " + placement.id() + " at " + placement.x() + ","
                    + placement.y() + ".");
            return;
        }

        MapDesignLibrary.MapTrigger trigger = diagnosticTrigger(message);
        if (trigger != null) {
            revealMapTrigger(trigger);
            setStatus(
                    "Found diagnostic trigger target " + trigger.id() + " at " + trigger.x() + "," + trigger.y() + ".");
            return;
        }

        if (revealDiagnosticContent(message)) {
            return;
        }

        setStatus("No direct navigation target found for diagnostic.");
    }

    private void revealMapTile(int x, int y, String label) {
        if (x < 0 || y < 0 || x >= design.width() || y >= design.height()) {
            setStatus(label + " is outside the current map.");
            return;
        }
        inspectedTile = new Point(x, y);
        inspectedPlacement = null;
        inspectedTrigger = null;
        inspectedTriggerTarget = null;
        inspectorArea.setText(mapCanvas.tileInspectionText(x, y));
        inspectorArea.setCaretPosition(0);
        mapCanvas.scrollToTile(x, y);
        mapCanvas.repaint();
        setStatus("Found " + label + " at " + x + "," + y + ".");
    }

    private MapDesignLibrary.MapPlacement diagnosticPlacement(String message) {
        String placementId = tokenAfter(message, "Placement ");
        if (!placementId.isBlank()) {
            MapDesignLibrary.MapPlacement placement = firstPlacementWithId(placementId);
            if (placement != null) {
                return placement;
            }
        }

        String interactionId = tokenAfter(message, "Interaction ");
        if (!interactionId.isBlank()) {
            return firstPlacement(MapDesignLibrary.PlacementKind.INTERACTION, interactionId);
        }

        return null;
    }

    private MapDesignLibrary.MapPlacement firstPlacementWithId(String id) {
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (id.equals(placement.id())) {
                return placement;
            }
        }
        return null;
    }

    private MapDesignLibrary.MapPlacement firstPlacement(MapDesignLibrary.PlacementKind kind, String id) {
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.kind() == kind && id.equals(placement.id())) {
                return placement;
            }
        }
        return null;
    }

    private MapDesignLibrary.MapTrigger diagnosticTrigger(String message) {
        String triggerId = tokenAfter(message, "Trigger ");
        if (triggerId.isBlank() && message.startsWith("Trigger id ")) {
            triggerId = tokenAfter(message, "Trigger id ");
        }
        if (triggerId.isBlank()) {
            return null;
        }
        return findTrigger(triggerId);
    }

    private boolean revealDiagnosticContent(String message) {
        if (revealFirstMatchingContent(design.customNpcs(), ContentCategory.NPCS,
                npc -> message.startsWith("Custom NPC " + npc.npcId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customMobs(), ContentCategory.ENEMIES,
                mob -> message.startsWith("Enemy " + mob.mobId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customItems(), ContentCategory.ITEMS,
                item -> message.startsWith("Item " + item.itemId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customLimbs(), ContentCategory.LIMBS,
                limb -> message.startsWith("Limb " + limb.limbId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customGatheringNodes(), ContentCategory.GATHERING,
                node -> message.startsWith("Gathering node " + node.nodeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customFurniture(), ContentCategory.FURNITURE,
                furniture -> message.startsWith("Furniture " + furniture.furnitureId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customCookingRecipes(), ContentCategory.COOKING,
                recipe -> message.startsWith("Cooking recipe " + recipe.recipeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.craftingRecipes(), ContentCategory.CRAFTING_RECIPES,
                recipe -> message.startsWith("Crafting recipe " + recipe.recipeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.authoredDialogues(), ContentCategory.DIALOGUES,
                dialogue -> message.startsWith("Authored dialogue " + dialogue.interactionId() + " "))) {
            return true;
        }
        return false;
    }

    private <T> boolean revealFirstMatchingContent(List<T> values, ContentCategory category,
            java.util.function.Predicate<T> predicate) {
        for (T value : values) {
            if (predicate.test(value)) {
                revealContentEntry(value, category);
                setStatus("Found diagnostic content target.");
                return true;
            }
        }
        return false;
    }

    private String tokenAfter(String message, String prefix) {
        int start = message.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = start;
        while (end < message.length()) {
            char character = message.charAt(end);
            if (Character.isWhitespace(character) || character == '.' || character == ',') {
                break;
            }
            end++;
        }
        return message.substring(start, end).trim();
    }

    private PlaceableCategory placementCategory(Object value) {
        MapDesignLibrary.PlacementKind kind;
        String id;
        if (value instanceof MapDesignLibrary.MapPlacement placement) {
            kind = placement.kind();
            id = placement.id();
        } else if (value instanceof MapDesignLibrary.PlacedObjectInstance object) {
            kind = object.kind();
            id = object.id();
        } else {
            return null;
        }
        return switch (kind) {
            case ITEM -> PlaceableCategory.ITEMS;
            case ENEMY -> PlaceableCategory.ENEMIES;
            case CUSTOM_NPC -> PlaceableCategory.NPCS;
            case GATHERING_NODE -> PlaceableCategory.GATHERING_NODES;
            case FURNITURE -> PlaceableCategory.FURNITURE;
            case CRAFTING_NODE -> PlaceableCategory.CRAFTING_NODES;
            case INTERACTION ->
                id.startsWith("map_link|") ? PlaceableCategory.MAP_LINKS : PlaceableCategory.INTERACTIONS;
        };
    }

    private void editMapPlacement(MapDesignLibrary.MapPlacement placement) {
        PlaceableCategory initialCategory = placementCategory(placement);
        if (initialCategory == null) {
            initialCategory = PlaceableCategory.ITEMS;
        }

        JComboBox<PlaceableCategory> categoryBox = new JComboBox<>(PlaceableCategory.values());
        JComboBox<PlaceableOption> optionBox = new JComboBox<>();
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(placement.x(), 0, Math.max(0, design.width() - 1), 1));
        JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(placement.y(), 0, Math.max(0, design.height() - 1), 1));
        JButton open3dPreviewButton = new JButton("Open 3D Preview");
        categoryBox.setSelectedItem(initialCategory);

        Runnable refreshOptions = () -> {
            PlaceableCategory category = (PlaceableCategory) categoryBox.getSelectedItem();
            optionBox.removeAllItems();
            for (PlaceableOption option : placeableOptionsFor(category, false)) {
                optionBox.addItem(option);
            }
            if (!selectPlaceableOption(optionBox, placement.kind(), placement.id())) {
                PlaceableOption current = new PlaceableOption(
                        "Current: " + placement.kind() + " " + placement.id(),
                        placement.kind(),
                        placement.id());
                optionBox.addItem(current);
                optionBox.setSelectedItem(current);
            }
        };
        categoryBox.addActionListener(event -> refreshOptions.run());
        refreshOptions.run();

        JPanel fields = createFormPanel();
        addFormRow(fields, "Category", categoryBox);
        addFormRow(fields, "Object", optionBox);
        addFormRow(fields, "X Position", xSpinner);
        addFormRow(fields, "Y Position", ySpinner);
        addFormRow(fields, "", open3dPreviewButton);

        PlacementPreviewPanel previewPanel = new PlacementPreviewPanel(() ->
                (PlaceableOption) optionBox.getSelectedItem());
        optionBox.addActionListener(event -> previewPanel.repaint());
        categoryBox.addActionListener(event -> previewPanel.repaint());
        open3dPreviewButton.addActionListener(event -> launchMapPlacement3dPreview(() -> {
            PlaceableOption selected = (PlaceableOption) optionBox.getSelectedItem();
            if (selected == null || selected.kind() == null) {
                return null;
            }
            return new MapDesignLibrary.MapPlacement(
                    selected.kind(),
                    selected.id(),
                    ((Number) xSpinner.getValue()).intValue(),
                    ((Number) ySpinner.getValue()).intValue());
        }, placement));

        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.add(fields, BorderLayout.CENTER);
        content.add(previewPanel, BorderLayout.EAST);

        int result = showScrollableFormDialog(content, "Edit Placement");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        PlaceableOption selectedOption = (PlaceableOption) optionBox.getSelectedItem();
        if (selectedOption == null || selectedOption.kind() == null || selectedOption.id().isBlank()) {
            setStatus("Placement needs a valid object.");
            return;
        }

        int x = ((Number) xSpinner.getValue()).intValue();
        int y = ((Number) ySpinner.getValue()).intValue();
        if (isTransformablePlaceable(selectedOption)) {
            int index = design.placements().indexOf(placement);
            if (index < 0) {
                setStatus("Placement no longer exists.");
                return;
            }
            MapDesignLibrary.PlacedObjectInstance initial = new MapDesignLibrary.PlacedObjectInstance(
                    nextPlacedObjectId(selectedOption.id()),
                    selectedOption.kind(),
                    selectedOption.id(),
                    x,
                    y,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    defaultBlocksMovementFor(selectedOption),
                    null);
            MapDesignLibrary.PlacedObjectInstance created = showPlacedObjectDialog(initial);
            if (created == null) {
                return;
            }
            captureHistory("convert placement to placed object");
            design.placements().remove(index);
            design.placedObjects().add(created);
            markDirty(true);
            refreshContentBrowser();
            revealPlacedObject(created);
            mapCanvas.repaint();
            setStatus("Converted placement to placed object " + selectedOption.label() + ".");
            return;
        }

        List<MapDesignLibrary.MapPlacement> targetConflicts = design.placements().stream()
                .filter(existing -> existing != placement)
                .filter(existing -> existing.x() == x && existing.y() == y)
                .toList();
        if (!targetConflicts.isEmpty()) {
            int replaceResult = showAdaptiveTextConfirmDialog(
                    this,
                    "Tile " + x + "," + y + " already has " + targetConflicts.size()
                            + " placement(s).\n\nReplace them?",
                    "Replace Placement",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (replaceResult != JOptionPane.OK_OPTION) {
                return;
            }
        }

        int index = design.placements().indexOf(placement);
        if (index < 0) {
            setStatus("Placement no longer exists.");
            return;
        }

        captureHistory("edit placement");
        design.placements().removeIf(existing -> targetConflicts.stream().anyMatch(conflict -> conflict == existing));
        MapDesignLibrary.MapPlacement updated = new MapDesignLibrary.MapPlacement(
                selectedOption.kind(),
                selectedOption.id(),
                x,
                y);
        index = Math.min(index, design.placements().size() - 1);
        design.placements().set(index, updated);
        markDirty(true);
        revealMapPlacement(updated);
        mapCanvas.repaint();
        setStatus("Updated placement " + selectedOption.label() + " at " + x + "," + y + ".");
    }

    private boolean selectPlaceableOption(
            JComboBox<PlaceableOption> optionBox,
            MapDesignLibrary.PlacementKind kind,
            String id) {
        for (int i = 0; i < optionBox.getItemCount(); i++) {
            PlaceableOption option = optionBox.getItemAt(i);
            if (option != null && option.kind() == kind && id.equals(option.id())) {
                optionBox.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private void selectPlaceable(String id) {
        for (int i = 0; i < placeableBox.getItemCount(); i++) {
            PlaceableOption option = placeableBox.getItemAt(i);
            if (option != null && id.equals(option.id())) {
                placeableBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void createMaterialTier() {
        showMaterialTierDialog(null, false);
    }

    private void editMaterialTier(MaterialDefinition material) {
        if (material == null || material.id().equals("none")) {
            setStatus("The reserved none material cannot be edited.");
            return;
        }
        showMaterialTierDialog(material, false);
    }

    private void duplicateMaterialTier(MaterialDefinition material) {
        if (material != null) {
            showMaterialTierDialog(material, true);
        }
    }

    private void showMaterialTierDialog(MaterialDefinition existing, boolean duplicate) {
        String initialName = existing == null ? "New Material" : existing.displayName() + (duplicate ? " Copy" : "");
        String initialId = existing == null ? "new_material"
                : existing.id() + (duplicate ? "_copy" : "");
        JTextField idField = new JTextField(initialId, 24);
        JTextField nameField = new JTextField(initialName, 24);
        JComboBox<GearMaterial.MaterialFamily> familyBox = new JComboBox<>(new GearMaterial.MaterialFamily[] {
                GearMaterial.MaterialFamily.METAL,
                GearMaterial.MaterialFamily.WOOD,
                GearMaterial.MaterialFamily.HIDE
        });
        familyBox.setSelectedItem(existing == null ? GearMaterial.MaterialFamily.METAL : existing.family());
        JSpinner orderSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? MaterialCatalog.snapshot().definitions().size() * 10 : existing.sortOrder(),
                1, 100000, 1));
        JSpinner statSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : existing.statBonus(), 0, 10000, 1));
        JSpinner multiplierSpinner = decimalSpinner(
                existing == null ? 1.0 : existing.priceMultiplier(), 0.1, 1000, 0.05);
        int[] tintRgb = { existing == null ? 0xB4B4B4 : existing.tintRgb() };
        JSpinner tintStrengthSpinner = decimalSpinner(
                existing == null ? 0.35 : existing.tintStrength(), 0, 1, 0.05);
        JPanel tintVisualPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int margin = 12;
                    int sampleWidth = Math.max(30, (getWidth() - margin * 3) / 2);
                    int sampleHeight = Math.max(24, getHeight() - margin * 2);
                    Color base = new Color(184, 184, 184);
                    float strength = (float) number(tintStrengthSpinner);
                    Color tint = new Color(tintRgb[0]);
                    Color result = new Color(
                            Math.round(base.getRed() * (1 - strength) + tint.getRed() * strength),
                            Math.round(base.getGreen() * (1 - strength) + tint.getGreen() * strength),
                            Math.round(base.getBlue() * (1 - strength) + tint.getBlue() * strength));
                    g.setColor(base);
                    g.fillRoundRect(margin, margin, sampleWidth, sampleHeight, 8, 8);
                    g.setColor(Color.DARK_GRAY);
                    g.drawRoundRect(margin, margin, sampleWidth, sampleHeight, 8, 8);
                    int secondX = margin * 2 + sampleWidth;
                    g.setColor(result);
                    g.fillRoundRect(secondX, margin, sampleWidth, sampleHeight, 8, 8);
                    g.setColor(Color.DARK_GRAY);
                    g.drawRoundRect(secondX, margin, sampleWidth, sampleHeight, 8, 8);
                    g.drawString("Base", margin + 8, margin + 17);
                    g.drawString("Tinted", secondX + 8, margin + 17);
                } finally {
                    g.dispose();
                }
            }
        };
        tintVisualPreview.setPreferredSize(new Dimension(260, 72));
        JLabel tintPreview = new JLabel(existing == null ? "#B4B4B4" : existing.tintHex(), SwingConstants.CENTER);
        tintPreview.setOpaque(true);
        tintPreview.setPreferredSize(new Dimension(120, 28));
        Runnable refreshTint = () -> {
            Color color = new Color(tintRgb[0]);
            tintPreview.setBackground(color);
            tintPreview.setForeground((color.getRed() + color.getGreen() + color.getBlue()) < 360
                    ? Color.WHITE : Color.BLACK);
            tintPreview.setText(String.format(Locale.ROOT, "#%06X", tintRgb[0]));
        };
        JButton chooseTint = new JButton("Choose Color");
        chooseTint.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(this, "Material Tint", new Color(tintRgb[0]));
            if (selected != null) {
                tintRgb[0] = selected.getRGB() & 0xFFFFFF;
                refreshTint.run();
                tintVisualPreview.repaint();
            }
        });
        tintStrengthSpinner.addChangeListener(event -> tintVisualPreview.repaint());
        refreshTint.run();

        JComboBox<DropItemOption> rawItemBox = new JComboBox<>(materialItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> processedItemBox = new JComboBox<>(materialItemOptions().toArray(new DropItemOption[0]));
        if (existing != null) {
            selectDropItem(rawItemBox, existing.rawResourceItemId());
            selectDropItem(processedItemBox, existing.processedResourceItemId());
        }
        JCheckBox generateResources = new JCheckBox("Generate standard stackable resource items", false);
        GearMaterial.MaterialFamily initialFamily = existing == null
                ? GearMaterial.MaterialFamily.METAL : existing.family();
        String proposedRawName = initialName + switch (initialFamily) {
            case METAL -> " Ore";
            case WOOD -> " Logs";
            case HIDE -> " Leather";
            default -> " Resource";
        };
        String proposedProcessedName = initialName + " Bar";
        JTextField rawResourceIdField = new JTextField(
                existing != null && !existing.rawResourceItemId().isBlank()
                        ? existing.rawResourceItemId() : nextCustomItemId(proposedRawName), 24);
        JTextField rawResourceNameField = new JTextField(proposedRawName, 24);
        JSpinner rawResourceValueSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
        JTextField processedResourceIdField = new JTextField(
                existing != null && !existing.processedResourceItemId().isBlank()
                        ? existing.processedResourceItemId() : nextCustomItemId(proposedProcessedName), 24);
        JTextField processedResourceNameField = new JTextField(proposedProcessedName, 24);
        JSpinner processedResourceValueSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
        JTextField rawIconField = new JTextField("assets/images/generated/items/" + initialId + "_resource.png", 28);
        JTextField processedIconField = new JTextField("assets/images/generated/items/" + initialId + "_processed.png", 28);
        JButton rawBrowse = new JButton("Browse");
        JButton processedBrowse = new JButton("Browse");
        rawBrowse.addActionListener(event -> browsePathInto(rawIconField));
        processedBrowse.addActionListener(event -> browsePathInto(processedIconField));

        Map<CharacterSkill, JSpinner> gateSpinners = new EnumMap<>(CharacterSkill.class);
        JPanel gateFields = configGridPanel();
        gateFields.add(new JLabel("Equipment Skill"));
        gateFields.add(new JLabel("Required Level"));
        gateFields.add(new JLabel("Configuration Key"));
        GearMaterial initialMaterial = GearMaterial.of(initialId);
        for (CharacterSkill skill : CharacterSkill.values()) {
            String key = EquipmentRequirementRules.configurationKey(skill, initialMaterial);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    existing == null ? EquipmentRequirementRules.defaultLevel(initialMaterial)
                            : EquipmentRequirementRules.requiredLevel(skill, GearMaterial.of(existing.id())),
                    1, 100, 1));
            gateSpinners.put(skill, spinner);
            gateFields.add(new JLabel(skill.getDisplayName()));
            gateFields.add(spinner);
            gateFields.add(new JLabel(key + (GameConfiguration.hasValue(key) ? "" : " (fallback)")));
        }
        JSpinner smithingXpSpinner = new JSpinner(new SpinnerNumberModel(
                existing != null && existing.family() == GearMaterial.MaterialFamily.METAL
                        ? SmithingExperienceRules.xpPerBar(GearMaterial.of(existing.id())) : 12,
                0, 100000, 1));
        JSpinner lanternCapacitySpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 600 : existing.lanternFuelCapacitySeconds(), 1, 864000, 30));
        JSpinner lanternBurnSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 300 : existing.lanternBurnSecondsPerLog(), 1, 864000, 30));
        JLabel lanternCompatibility = new JLabel();
        JLabel lanternResourceWarning = new JLabel();
        lanternResourceWarning.setForeground(new Color(170, 84, 38));

        JPanel identity = createFormPanel();
        identity.add(formRow("Stable ID", idField));
        identity.add(formRow("Display Name", nameField));
        identity.add(formRow("Family", familyBox));
        identity.add(formRow("Display Order", orderSpinner));
        identity.add(formRow("Equipment Stat Bonus", statSpinner));
        identity.add(formRow("Value Multiplier", multiplierSpinner));
        JPanel tintPanel = new JPanel(new BorderLayout(4, 4));
        tintPanel.add(tintPreview, BorderLayout.CENTER);
        tintPanel.add(chooseTint, BorderLayout.EAST);
        identity.add(formRow("Tint", tintPanel));
        identity.add(formRow("Tint Strength", tintStrengthSpinner));
        identity.add(formRow("Live Tint Preview", tintVisualPreview));

        JPanel resources = createFormPanel();
        resources.add(formRow("Existing Raw Resource", rawItemBox));
        resources.add(formRow("Existing Processed / Bar", processedItemBox));
        resources.add(formRow("Resource Generator", generateResources));
        resources.add(formRow("Generated Raw ID", rawResourceIdField));
        resources.add(formRow("Generated Raw Name", rawResourceNameField));
        resources.add(formRow("Generated Raw Value", rawResourceValueSpinner));
        resources.add(formRow("Raw Resource Icon", pathFieldPanel(rawIconField, rawBrowse)));
        resources.add(formRow("Generated Processed ID", processedResourceIdField));
        resources.add(formRow("Generated Processed Name", processedResourceNameField));
        resources.add(formRow("Generated Processed Value", processedResourceValueSpinner));
        resources.add(formRow("Processed / Bar Icon", pathFieldPanel(processedIconField, processedBrowse)));
        resources.add(formRow("Smithing XP Per Bar", smithingXpSpinner));
        resources.add(formRow("Lantern Capacity (seconds)", lanternCapacitySpinner));
        resources.add(formRow("Burn Time per Log (seconds)", lanternBurnSpinner));
        resources.add(formRow("Lantern Compatibility", lanternCompatibility));
        resources.add(formRow("Fuel Diagnostic", lanternResourceWarning));

        Runnable updateFamilyRows = () -> {
            GearMaterial.MaterialFamily family = (GearMaterial.MaterialFamily) familyBox.getSelectedItem();
            boolean metal = family == GearMaterial.MaterialFamily.METAL;
            boolean wood = family == GearMaterial.MaterialFamily.WOOD;
            processedItemBox.setEnabled(metal);
            processedResourceIdField.setEnabled(metal);
            processedResourceNameField.setEnabled(metal);
            processedResourceValueSpinner.setEnabled(metal);
            processedIconField.setEnabled(metal);
            processedBrowse.setEnabled(metal);
            smithingXpSpinner.setEnabled(metal);
            lanternCapacitySpinner.setEnabled(metal);
            lanternBurnSpinner.setEnabled(wood);
            MaterialDefinition preview = new MaterialDefinition(
                    MaterialDefinition.normalizeId(idField.getText()), nameField.getText(), family,
                    ((Number) orderSpinner.getValue()).intValue(), ((Number) statSpinner.getValue()).intValue(),
                    number(multiplierSpinner), tintRgb[0], (float) number(tintStrengthSpinner),
                    "", "", ((Number) lanternCapacitySpinner.getValue()).intValue(),
                    ((Number) lanternBurnSpinner.getValue()).intValue());
            lanternCompatibility.setText(LanternSystem.compatibilitySummary(preview));
            DropItemOption rawOption = (DropItemOption) rawItemBox.getSelectedItem();
            String rawId = rawOption == null ? "" : rawOption.itemId();
            boolean validRaw = design.customItems().stream().anyMatch(item ->
                    item.itemId().equalsIgnoreCase(rawId) && item.stackable());
            lanternResourceWarning.setText(wood && !validRaw
                    ? "Warning: select a valid stackable raw-resource log." : "Ready");
        };
        familyBox.addActionListener(event -> updateFamilyRows.run());
        rawItemBox.addActionListener(event -> updateFamilyRows.run());
        orderSpinner.addChangeListener(event -> updateFamilyRows.run());
        lanternCapacitySpinner.addChangeListener(event -> updateFamilyRows.run());
        lanternBurnSpinner.addChangeListener(event -> updateFamilyRows.run());
        updateFamilyRows.run();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Material", ConstructionKitUi.scrollingForm(topAlignedForm(identity)));
        tabs.addTab("Resources", ConstructionKitUi.scrollingForm(topAlignedForm(resources)));
        tabs.addTab("Equipment Gates", new JScrollPane(gateFields));
        if (showScrollableFormDialog(tabs, existing == null || duplicate ? "Create Material" : "Edit Material")
                != JOptionPane.OK_OPTION) {
            return;
        }

        String newId = MaterialDefinition.normalizeId(idField.getText());
        String newName = nameField.getText() == null ? "" : nameField.getText().trim();
        if (newId.isBlank() || newId.equals("none") || newName.isBlank()) {
            setStatus("Material requires a non-reserved stable ID and display name.");
            return;
        }
        MaterialDefinition collision = MaterialCatalog.snapshot().find(newId);
        if (collision != null && (existing == null || duplicate || !collision.id().equals(existing.id()))) {
            setStatus("Material ID already exists: " + newId);
            return;
        }

        GearMaterial.MaterialFamily family = (GearMaterial.MaterialFamily) familyBox.getSelectedItem();
        DropItemOption selectedRaw = (DropItemOption) rawItemBox.getSelectedItem();
        DropItemOption selectedProcessed = (DropItemOption) processedItemBox.getSelectedItem();
        String rawId = selectedRaw == null ? "" : selectedRaw.itemId();
        String processedId = family == GearMaterial.MaterialFamily.METAL && selectedProcessed != null
                ? selectedProcessed.itemId() : "";
        List<MapDesignLibrary.CustomItem> generatedItems = new ArrayList<>();
        if (generateResources.isSelected()) {
            String rawName = rawResourceNameField.getText() == null ? "" : rawResourceNameField.getText().trim();
            rawId = normalizeContentId(rawResourceIdField.getText());
            if (rawId.isBlank() || rawName.isBlank() || hasCustomItemId(rawId)) {
                setStatus("Generated raw resource requires a unique stable ID and a name.");
                return;
            }
            generatedItems.add(generatedMaterialResource(rawId, rawName, rawIconField.getText(), newId,
                    ((Number) rawResourceValueSpinner.getValue()).intValue()));
            if (family == GearMaterial.MaterialFamily.METAL) {
                String barName = processedResourceNameField.getText() == null
                        ? "" : processedResourceNameField.getText().trim();
                processedId = normalizeContentId(processedResourceIdField.getText());
                if (processedId.isBlank() || barName.isBlank() || hasCustomItemId(processedId)
                        || containsPendingItemId(generatedItems, processedId)) {
                    setStatus("Generated processed resource requires a unique stable ID and a name.");
                    return;
                }
                generatedItems.add(generatedMaterialResource(
                        processedId, barName, processedIconField.getText(), newId,
                        ((Number) processedResourceValueSpinner.getValue()).intValue()));
            }
        }
        MaterialDefinition updated = new MaterialDefinition(
                newId, newName, family, ((Number) orderSpinner.getValue()).intValue(),
                ((Number) statSpinner.getValue()).intValue(), number(multiplierSpinner), tintRgb[0],
                (float) number(tintStrengthSpinner), rawId, processedId,
                ((Number) lanternCapacitySpinner.getValue()).intValue(),
                ((Number) lanternBurnSpinner.getValue()).intValue());
        applyMaterialTransaction(existing, updated, gateSpinners,
                ((Number) smithingXpSpinner.getValue()).intValue(), generatedItems);
    }

    private List<DropItemOption> materialItemOptions() {
        List<DropItemOption> options = new ArrayList<>();
        options.add(new DropItemOption("", "None"));
        options.addAll(gatheringOutputItemOptions());
        return options;
    }

    private MapDesignLibrary.CustomItem generatedMaterialResource(
            String id, String name, String iconPath, String materialId, int value) {
        return new MapDesignLibrary.CustomItem(
                id, name, InventorySystem.ItemType.MISC,
                iconPath == null ? "" : iconPath.trim(), "", "", WeaponType.NONE, false,
                GearMaterial.of(materialId), 0, Math.max(1, value),
                "A refined " + name.toLowerCase(Locale.ROOT) + ".",
                null, true, false, 1, 1, 0, 0, "",
                EquipmentViewModelProfile.defaults(), "", ItemModelIconProfile.defaults(), null);
    }

    private String nextCustomItemIdAvoiding(String name, List<MapDesignLibrary.CustomItem> pending) {
        String base = safeId(name);
        String candidate = base;
        int suffix = 2;
        while (hasCustomItemId(candidate) || containsPendingItemId(pending, candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private boolean containsPendingItemId(List<MapDesignLibrary.CustomItem> pending, String id) {
        for (MapDesignLibrary.CustomItem item : pending) {
            if (item.itemId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void applyMaterialTransaction(
            MaterialDefinition existing,
            MaterialDefinition updated,
            Map<CharacterSkill, JSpinner> gates,
            int smithingXp,
            List<MapDesignLibrary.CustomItem> generatedItems) {
        List<MaterialDefinition> beforeMaterials = new ArrayList<>(MaterialCatalog.snapshot().definitions());
        List<MapDesignLibrary.CustomItem> beforeItems = new ArrayList<>(design.customItems());
        List<MaterialDefinition> after = new ArrayList<>(beforeMaterials);
        if (existing != null) {
            after.removeIf(material -> material.id().equals(existing.id()));
        }
        after.add(updated);
        if (existing != null && !existing.id().equals(updated.id())) {
            for (int i = 0; i < design.customItems().size(); i++) {
                MapDesignLibrary.CustomItem item = design.customItems().get(i);
                if (item.material().id().equals(existing.id())) {
                    design.customItems().set(i, item.withMaterial(GearMaterial.of(updated.id())));
                }
            }
        }
        design.customItems().addAll(generatedItems);
        try {
            MaterialCatalog.write(after);
            if ((!generatedItems.isEmpty() || existing != null) && !persistSharedContent("material references")) {
                throw new IOException("Dependent item catalog could not be saved.");
            }
            saveMaterialConfiguration(existing == null ? "" : existing.id(), updated, gates, smithingXp);
            MaterialCatalog.refresh();
            refreshContentBrowser();
            setStatus((existing == null ? "Created " : "Updated ") + "material " + updated.displayName() + ".");
        } catch (IOException | RuntimeException exception) {
            design.customItems().clear();
            design.customItems().addAll(beforeItems);
            try {
                MaterialCatalog.write(beforeMaterials);
                MaterialCatalog.refresh();
                if (!generatedItems.isEmpty() || existing != null) {
                    persistSharedContent("material rollback");
                }
            } catch (IOException ignored) {
                // Report the original transaction failure.
            }
            setStatus("Material transaction failed: " + exception.getMessage());
        }
    }

    private void saveMaterialConfiguration(
            String previousId,
            MaterialDefinition material,
            Map<CharacterSkill, JSpinner> gates,
            int smithingXp) throws IOException {
        Properties properties = loadPackagedConfigurationProperties();
        List<String> oldKeys = new ArrayList<>();
        if (previousId != null && !previousId.isBlank() && !previousId.equals(material.id())) {
            for (CharacterSkill skill : CharacterSkill.values()) {
                String oldKey = EquipmentRequirementRules.configurationKey(skill, GearMaterial.of(previousId));
                properties.remove(oldKey);
                oldKeys.add(oldKey);
            }
            String oldXpKey = SmithingExperienceRules.configurationKey(GearMaterial.of(previousId));
            properties.remove(oldXpKey);
            oldKeys.add(oldXpKey);
        }
        for (Map.Entry<CharacterSkill, JSpinner> entry : gates.entrySet()) {
            String key = EquipmentRequirementRules.configurationKey(entry.getKey(), GearMaterial.of(material.id()));
            String value = String.valueOf(((Number) entry.getValue().getValue()).intValue());
            properties.setProperty(key, value);
        }
        if (material.family() == GearMaterial.MaterialFamily.METAL) {
            properties.setProperty(SmithingExperienceRules.configurationKey(GearMaterial.of(material.id())),
                    String.valueOf(Math.max(0, smithingXp)));
        } else {
            properties.remove(SmithingExperienceRules.configurationKey(GearMaterial.of(material.id())));
        }
        writePropertiesAtomically(CONFIG_RESOURCE_PATH, properties,
                "Aether packaged gameplay configuration");
        for (String oldKey : oldKeys) {
            GameConfiguration.removeValue(oldKey);
        }
        for (Map.Entry<CharacterSkill, JSpinner> entry : gates.entrySet()) {
            GameConfiguration.setValue(
                    EquipmentRequirementRules.configurationKey(entry.getKey(), GearMaterial.of(material.id())),
                    String.valueOf(((Number) entry.getValue().getValue()).intValue()));
        }
        if (material.family() == GearMaterial.MaterialFamily.METAL) {
            GameConfiguration.setValue(SmithingExperienceRules.configurationKey(GearMaterial.of(material.id())),
                    String.valueOf(Math.max(0, smithingXp)));
        } else {
            GameConfiguration.removeValue(SmithingExperienceRules.configurationKey(GearMaterial.of(material.id())));
        }
    }

    private void createCustomItem() {
        pendingItemFirstPersonProfile = null;
        MapDesignLibrary.CustomItem item = showCustomItemDialog("Create Item", null);
        if (item == null) {
            return;
        }

        design.customItems().add(item);
        if (persistSharedContent("custom item") && pendingItemFirstPersonProfile != null) {
            persistItemFirstPersonProfile(pendingItemFirstPersonProfile);
        }
        pendingItemFirstPersonProfile = null;
        populatePlaceables();
        setStatus("Created custom item " + item.displayName() + ".");
    }

    private MapDesignLibrary.CustomItem showCustomItemDialog(String title, MapDesignLibrary.CustomItem existing) {
        JTextField nameField = new JTextField(existing == null ? "Custom Item" : existing.displayName(), 24);
        JTextField iconPathField = new JTextField(
                existing == null ? "assets/images/generated/items/custom_item.png" : existing.iconPath(),
                28);
        JTextField paperDollOverlayField = new JTextField(existing == null ? "" : existing.paperDollOverlayPath(), 28);
        JTextField firstPersonModelField = new JTextField(
                existing == null ? "" : existing.firstPersonModelPath(), 28);
        JTextField useSoundField = new JTextField(existing == null ? "" : existing.useSoundPath(), 28);
        EquipmentViewModelProfile existingPose = existing == null
                ? EquipmentViewModelProfile.defaults()
                : existing.viewModelProfile();
        ItemModelIconProfile[] modelIconProfile = {
                existing == null ? ItemModelIconProfile.defaults() : existing.modelIconProfile()
        };
        FirstPersonCombatLibrary.ItemProfile[] firstPersonProfile = {
                existing == null ? null
                        : FirstPersonCombatLibrary.load().itemProfiles().get(
                                normalizeContentId(existing.itemId()))
        };
        EquipmentViewModelProfile editableSocket = firstPersonProfile[0] == null
                ? existingPose : firstPersonProfile[0].socketTransform();
        JSpinner viewX = decimalSpinner(editableSocket.positionX(), -10, 10, 0.01);
        JSpinner viewY = decimalSpinner(editableSocket.positionY(), -10, 10, 0.01);
        JSpinner viewZ = decimalSpinner(editableSocket.positionZ(), -10, 10, 0.01);
        JSpinner viewRotX = decimalSpinner(editableSocket.rotationX(), -360, 360, 1);
        JSpinner viewRotY = decimalSpinner(editableSocket.rotationY(), -360, 360, 1);
        JSpinner viewRotZ = decimalSpinner(editableSocket.rotationZ(), -360, 360, 1);
        JSpinner viewHeight = decimalSpinner(editableSocket.normalizedHeight(), 0.02, 20, 0.02);
        JSpinner swingX = decimalSpinner(existingPose.swingAxisX(), -1, 1, 0.05);
        JSpinner swingY = decimalSpinner(existingPose.swingAxisY(), -1, 1, 0.05);
        JSpinner swingZ = decimalSpinner(existingPose.swingAxisZ(), -1, 1, 0.05);
        JCheckBox pairedHands = new JCheckBox("Model contains both hands", existingPose.pairedHands());
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JButton firstPersonModelBrowseButton = new JButton("Browse");
        JButton useSoundBrowseButton = new JButton("Browse");
        JButton firstPersonAuthoringButton = new JButton("Edit Rig, Hand, Armor & Clip Overrides");
        JButton autoPlaceButton = new JButton("Snap to Bone");
        JButton editGripButton = new JButton("Edit Grip");
        JButton undoPlacementButton = new JButton("Undo Bone/Snap Change");
        JButton flipPlacementButton = new JButton("Flip Grip End");
        AttachmentBoneSelector attachmentBoneSelector = new AttachmentBoneSelector();
        JButton pickAttachmentBoneButton = new JButton("Pick Bone in Viewport");
        JLabel placementSummary = new JLabel(firstPersonProfile[0] == null
                ? "Not placed yet" : "Manual placement");
        undoPlacementButton.setEnabled(false);
        FirstPersonCombatLibrary.ItemProfile[] placementUndo = {null};
        EquipmentAutoPlacementService.PlacementProposal[] lastPlacement = {null};
        boolean[] placementBusy = {false};
        boolean[] attachmentBoneLoading = {true};
        String[] authoredAttachmentBone = {
                firstPersonProfile[0] == null ? "" : firstPersonProfile[0].attachmentBone()
        };
        FirstPersonCombatLibrary.WieldHand[] authoredAttachmentHand = {
                firstPersonProfile[0] == null
                        ? FirstPersonCombatLibrary.WieldHand.RIGHT
                        : firstPersonProfile[0].wieldHand()
        };
        boolean[] initialPlacementAttempted = {firstPersonProfile[0] != null};
        JButton setIconButton = new JButton("Set Icon");
        JLabel modelIconSummary = new JLabel();
        JComboBox<ItemTemplateOption> templateBox = new JComboBox<>(itemTemplateOptions());
        JComboBox<InventorySystem.ItemType> typeBox = new JComboBox<>(new InventorySystem.ItemType[] {
                InventorySystem.ItemType.MISC,
                InventorySystem.ItemType.CONSUMABLE,
                InventorySystem.ItemType.WEAPON,
                InventorySystem.ItemType.SHIELD,
                InventorySystem.ItemType.UTILITY,
                InventorySystem.ItemType.HEAD_GEAR,
                InventorySystem.ItemType.CHEST_ARMOR,
                InventorySystem.ItemType.LEG_ARMOR,
                InventorySystem.ItemType.RING
        });
        JComboBox<GearMaterial> materialBox = new JComboBox<>(GearMaterial.values());
        EquipmentSkillOption[] equipmentSkillOptions = new EquipmentSkillOption[CharacterSkill.values().length + 1];
        equipmentSkillOptions[0] = new EquipmentSkillOption(null);
        for (int i = 0; i < CharacterSkill.values().length; i++) {
            equipmentSkillOptions[i + 1] = new EquipmentSkillOption(CharacterSkill.values()[i]);
        }
        JComboBox<EquipmentSkillOption> equipmentSkillBox = new JComboBox<>(equipmentSkillOptions);
        JLabel equipmentRequirementLabel = new JLabel();
        JComboBox<WeaponType> weaponTypeBox = new JComboBox<>(new WeaponType[] {
                WeaponType.DAGGER,
                WeaponType.SWORD,
                WeaponType.MACE,
                WeaponType.STAFF,
                WeaponType.GREATSWORD
        });
        JLabel weaponTypeLabel = new JLabel("Weapon Type");
        JLabel twoHandedLabel = new JLabel("Hands");
        JCheckBox twoHandedBox = new JCheckBox("Two-handed", existing != null && existing.twoHanded());
        JSpinner magicAccuracySpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 0 : existing.magicAccuracyBonus(), 0, 1000, 1));
        JSpinner magicPowerSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 0 : existing.magicPowerBonus(), 0, 1000, 1));
        JComboBox<StatTargetOption> statTargetBox = new JComboBox<>(statTargetOptions());
        JSpinner healSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 0 : existing.healAmount(), 0, 1000, 1));
        JSpinner valueSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 10 : existing.baseGoldValue(), 1, 100000, 1));
        JCheckBox stackableBox = new JCheckBox("Stackable", existing != null && existing.stackable());
        JCheckBox smithingRecipeBox = new JCheckBox("Add smithing recipe");
        JSpinner smithingBarsSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 1 : existing.smithingRequiredBars(), 1, 100, 1));
        JSpinner smithingLevelSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 1 : existing.smithingRequiredLevel(), 1, 100, 1));
        JLabel smithingXpLabel = new JLabel();
        JLabel smithingMaterialLabel = new JLabel();
        LanternDefinition initialLantern = existing == null
                ? LanternDefinition.pocketLanternDefaults() : existing.lanternDefinition();
        JCheckBox lanternEnabledBox = new JCheckBox("Passive Pocket lantern",
                existing != null && initialLantern.enabled());
        int[] lanternColor = {initialLantern.colorRgb()};
        JButton lanternColorButton = new JButton(String.format(Locale.ROOT, "#%06X", lanternColor[0]));
        JSpinner lanternRadiusSpinner = decimalSpinner(initialLantern.enabled() ? initialLantern.radius() : 5.0,
                0.1, 64.0, 0.1);
        JSpinner lanternIntensitySpinner = decimalSpinner(initialLantern.enabled() ? initialLantern.intensity() : 1.0,
                0.01, 8.0, 0.05);
        JSpinner lanternFlickerSpinner = decimalSpinner(initialLantern.enabled() ? initialLantern.flickerAmount() : 0.12,
                0.0, 1.0, 0.01);
        JLabel lanternCapacityLabel = new JLabel();
        JLabel lanternCompatibilityLabel = new JLabel();
        JPanel lanternPreview = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    int size = Math.min(getWidth(), getHeight()) - 12;
                    int x = (getWidth() - size) / 2;
                    int y = (getHeight() - size) / 2;
                    Color color = new Color(lanternColor[0]);
                    for (int ring = size; ring > 0; ring -= 4) {
                        float alpha = (float) Math.pow(1.0 - ring / (double) size, 1.5) * 0.42f;
                        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255)));
                        g.fillOval(x + (size - ring) / 2, y + (size - ring) / 2, ring, ring);
                    }
                    g.setColor(color);
                    g.fillOval(getWidth() / 2 - 7, getHeight() / 2 - 7, 14, 14);
                } finally {
                    g.dispose();
                }
            }
        };
        lanternPreview.setPreferredSize(new Dimension(220, 110));
        JButton createLanternButton = new JButton("Create Lantern from Material");
        JTextArea examineArea = new JTextArea(existing == null ? "A custom item." : existing.examineText(), 4, 30);
        examineArea.setLineWrap(true);
        examineArea.setWrapStyleWord(true);
        if (existing != null) {
            typeBox.setSelectedItem(existing.itemType());
            materialBox.setSelectedItem(existing.material());
            selectEquipmentSkill(equipmentSkillBox, existing.equipmentSkill());
            weaponTypeBox.setSelectedItem(existing.weaponType());
            twoHandedBox.setSelected(existing.twoHanded());
            selectStatTargetOption(statTargetBox, existing.statBonusTarget());
            smithingRecipeBox.setSelected(existing.smithingRecipeEnabled());
        }

        Runnable updateSmithingRecipeControls = () -> {
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            boolean metal = material != null && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            if (!metal) {
                smithingRecipeBox.setSelected(false);
            }
            smithingMaterialLabel.setText(metal
                    ? "Uses " + CraftingSystem.smithingMaterialNameFor(material)
                    : "Metal materials only");
            smithingXpLabel.setText(metal
                    ? SmithingExperienceRules.calculate(
                            material, ((Number) smithingBarsSpinner.getValue()).intValue()) + " XP"
                    : "Not applicable");
        };
        Runnable updateEquipmentRequirement = () -> {
            EquipmentSkillOption option = (EquipmentSkillOption) equipmentSkillBox.getSelectedItem();
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            equipmentRequirementLabel.setText(option == null || option.skill() == null
                    ? "Unrestricted"
                    : option.skill().getDisplayName() + " level "
                    + EquipmentRequirementRules.requiredLevel(option.skill(), material)
                    + (GameConfiguration.hasValue(
                            EquipmentRequirementRules.configurationKey(option.skill(), material))
                            ? "" : " (fallback)"));
        };
        Runnable updateLanternControls = () -> {
            GearMaterial selected = (GearMaterial) materialBox.getSelectedItem();
            MaterialDefinition definition = selected == null ? null : MaterialCatalog.snapshot().find(selected.id());
            boolean metal = definition != null && definition.family() == GearMaterial.MaterialFamily.METAL;
            lanternCapacityLabel.setText(metal
                    ? LanternSystem.formatClock(definition.lanternFuelCapacitySeconds() * 1_000L)
                    : "Select a Metal material");
            lanternCompatibilityLabel.setText(metal
                    ? LanternSystem.compatibilitySummary(definition)
                    : "Metal materials only");
            boolean enabled = lanternEnabledBox.isSelected();
            lanternColorButton.setEnabled(enabled);
            lanternRadiusSpinner.setEnabled(enabled);
            lanternIntensitySpinner.setEnabled(enabled);
            lanternFlickerSpinner.setEnabled(enabled);
            lanternPreview.repaint();
        };
        lanternColorButton.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(this, "Lantern Light Color", new Color(lanternColor[0]));
            if (selected != null) {
                lanternColor[0] = selected.getRGB() & 0xFFFFFF;
                lanternColorButton.setText(String.format(Locale.ROOT, "#%06X", lanternColor[0]));
                lanternPreview.repaint();
            }
        });
        lanternEnabledBox.addActionListener(event -> updateLanternControls.run());
        lanternRadiusSpinner.addChangeListener(event -> lanternPreview.repaint());
        lanternIntensitySpinner.addChangeListener(event -> lanternPreview.repaint());
        lanternFlickerSpinner.addChangeListener(event -> lanternPreview.repaint());
        createLanternButton.addActionListener(event -> {
            GearMaterial selected = (GearMaterial) materialBox.getSelectedItem();
            if (selected == null || selected.getFamily() != GearMaterial.MaterialFamily.METAL) {
                setStatus("Choose a Metal material before creating a lantern.");
                return;
            }
            typeBox.setSelectedItem(InventorySystem.ItemType.UTILITY);
            nameField.setText(selected.getDisplayName() + " Lantern");
            iconPathField.setText("assets/images/monster/Nov-2015/item/misc/misc_lantern.png");
            valueSpinner.setValue(30);
            examineArea.setText("A reusable pocket lantern. Fuel it with compatible logs and equip it to light the way.");
            smithingRecipeBox.setSelected(true);
            smithingBarsSpinner.setValue(2);
            smithingLevelSpinner.setValue(Math.max(1, EquipmentRequirementRules.defaultLevel(selected)));
            lanternEnabledBox.setSelected(true);
            updateSmithingRecipeControls.run();
            updateLanternControls.run();
        });
        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollOverlayField));
        firstPersonModelBrowseButton
                .addActionListener(event -> showAssetBrowser(firstPersonModelField, AssetBrowserType.MODELS));
        useSoundBrowseButton.addActionListener(event -> browsePathInto(useSoundField));
        weaponTypeBox.addActionListener(event -> {
            if (typeBox.getSelectedItem() == InventorySystem.ItemType.WEAPON
                    && weaponTypeBox.getSelectedItem() == WeaponType.GREATSWORD) {
                twoHandedBox.setSelected(true);
            }
        });
        templateBox.addActionListener(event -> {
            ItemTemplateOption template = (ItemTemplateOption) templateBox.getSelectedItem();
            if (template == null || template.item() == null) {
                return;
            }
            MapDesignLibrary.CustomItem item = template.item();
            nameField.setText(item.displayName() + " Copy");
            iconPathField.setText(item.iconPath());
            paperDollOverlayField.setText(item.paperDollOverlayPath());
            firstPersonModelField.setText(item.firstPersonModelPath());
            modelIconProfile[0] = item.modelIconProfile();
            firstPersonProfile[0] = FirstPersonCombatLibrary.load()
                    .itemProfiles().get(normalizeContentId(item.itemId()));
            EquipmentViewModelProfile pose = firstPersonProfile[0] == null
                    ? item.viewModelProfile() : firstPersonProfile[0].socketTransform();
            viewX.setValue(pose.positionX());
            viewY.setValue(pose.positionY());
            viewZ.setValue(pose.positionZ());
            viewRotX.setValue(pose.rotationX());
            viewRotY.setValue(pose.rotationY());
            viewRotZ.setValue(pose.rotationZ());
            viewHeight.setValue(pose.normalizedHeight());
            swingX.setValue(pose.swingAxisX());
            swingY.setValue(pose.swingAxisY());
            swingZ.setValue(pose.swingAxisZ());
            pairedHands.setSelected(pose.pairedHands());
            useSoundField.setText(item.useSoundPath());
            typeBox.setSelectedItem(item.itemType());
            materialBox.setSelectedItem(item.material());
            selectEquipmentSkill(equipmentSkillBox, item.equipmentSkill());
            weaponTypeBox.setSelectedItem(item.weaponType() == WeaponType.NONE ? WeaponType.SWORD : item.weaponType());
            twoHandedBox.setSelected(item.twoHanded());
            magicAccuracySpinner.setValue(item.magicAccuracyBonus());
            magicPowerSpinner.setValue(item.magicPowerBonus());
            selectStatTargetOption(statTargetBox, item.statBonusTarget());
            healSpinner.setValue(item.healAmount());
            valueSpinner.setValue(item.baseGoldValue());
            stackableBox.setSelected(item.stackable());
            smithingRecipeBox.setSelected(item.smithingRecipeEnabled());
            smithingBarsSpinner.setValue(item.smithingRequiredBars());
            smithingLevelSpinner.setValue(item.smithingRequiredLevel());
            LanternDefinition copiedLantern = item.lanternDefinition();
            lanternEnabledBox.setSelected(copiedLantern.enabled());
            lanternColor[0] = copiedLantern.colorRgb();
            lanternColorButton.setText(String.format(Locale.ROOT, "#%06X", lanternColor[0]));
            if (copiedLantern.enabled()) {
                lanternRadiusSpinner.setValue(copiedLantern.radius());
                lanternIntensitySpinner.setValue(copiedLantern.intensity());
                lanternFlickerSpinner.setValue(copiedLantern.flickerAmount());
            }
            examineArea.setText(item.examineText());
            updateSmithingRecipeControls.run();
            updateLanternControls.run();
        });
        JPanel imagePanel = new JPanel(new BorderLayout(4, 4));
        imagePanel.add(iconPathField, BorderLayout.CENTER);
        imagePanel.add(browseButton, BorderLayout.EAST);
        JPanel paperDollPanel = new JPanel(new BorderLayout(4, 4));
        paperDollPanel.add(paperDollOverlayField, BorderLayout.CENTER);
        paperDollPanel.add(paperDollBrowseButton, BorderLayout.EAST);
        JPanel nameRow = formRow("Name", nameField);
        JPanel imageRow = formRow("Image", imagePanel);
        JPanel modelIconPanel = new JPanel(new BorderLayout(6, 4));
        modelIconPanel.add(modelIconSummary, BorderLayout.CENTER);
        modelIconPanel.add(setIconButton, BorderLayout.EAST);
        JPanel modelIconRow = formRow("Weapon Inventory Icon", modelIconPanel);
        JPanel paperDollRow = formRow("Paper Doll Sprite", paperDollPanel);
        JPanel firstPersonModelRow = formRow(
                "First-Person Model",
                pathFieldPanel(firstPersonModelField, firstPersonModelBrowseButton));
        JPanel useSoundRow = formRow("Use Sound", pathFieldPanel(useSoundField, useSoundBrowseButton));
        JPanel viewPositionRow = formRow("Hand Socket Position X / Y / Z",
                compactSpinnerRow(viewX, viewY, viewZ));
        JPanel viewRotationRow = formRow("Hand Socket Rotation X / Y / Z",
                compactSpinnerRow(viewRotX, viewRotY, viewRotZ));
        JPanel viewHeightRow = formRow("Socket Model Height", viewHeight);
        JPanel swingAxisRow = formRow("Legacy Fallback Swing Axis X / Y / Z",
                compactSpinnerRow(swingX, swingY, swingZ));
        JPanel pairedHandsRow = formRow("Chest Hands", pairedHands);
        JPanel firstPersonAuthoringRow = formRow("Skeletal First-Person", firstPersonAuthoringButton);
        JPanel placementButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        placementButtonPanel.add(autoPlaceButton);
        placementButtonPanel.add(editGripButton);
        placementButtonPanel.add(undoPlacementButton);
        placementButtonPanel.add(flipPlacementButton);
        placementButtonPanel.add(pickAttachmentBoneButton);
        JPanel placementButtonsRow = formRow("Automatic Placement", placementButtonPanel);
        JPanel attachmentBoneRow = formRow("Attachment Bone", attachmentBoneSelector);
        JPanel placementSummaryRow = formRow("Placement Result", placementSummary);
        FirstPersonCombatLibrary.Content itemFirstPersonContent = FirstPersonCombatLibrary.load();
        FirstPersonCombatLibrary.ItemProfile initialFirstPersonProfile = firstPersonProfile[0];
        FirstPersonCombatLibrary.WieldHand initialHand = initialFirstPersonProfile == null
                ? ((InventorySystem.ItemType) typeBox.getSelectedItem() == InventorySystem.ItemType.SHIELD
                ? FirstPersonCombatLibrary.WieldHand.LEFT : FirstPersonCombatLibrary.WieldHand.RIGHT)
                : initialFirstPersonProfile.wieldHand();
        authoredAttachmentHand[0] = initialHand;
        attachmentBoneSelector.populate(itemFirstPersonContent.rigFor(initialFirstPersonProfile),
                List.of(), initialHand,
                initialFirstPersonProfile == null ? "" : initialFirstPersonProfile.attachmentBone());
        attachmentBoneLoading[0] = false;
        Supplier<FirstPersonCombatLibrary.ItemProfile> workingFirstPersonProfile = () -> {
            FirstPersonCombatLibrary.ItemProfile base = firstPersonProfile[0];
            String provisionalId = existing == null
                    ? normalizeContentId(nameField.getText()) : existing.itemId();
            WeaponType selectedWeaponType = (WeaponType) weaponTypeBox.getSelectedItem();
            String rigId = base == null || base.rigId().isBlank()
                    ? itemFirstPersonContent.defaultRigId() : base.rigId();
            String animationSetId = base == null || base.animationSetId().isBlank()
                    ? itemFirstPersonContent.weaponDefaults().getOrDefault(
                    selectedWeaponType, FirstPersonCombatLibrary.defaultSetId(selectedWeaponType))
                    : base.animationSetId();
            EquipmentViewModelProfile socket = new EquipmentViewModelProfile(
                    number(viewX), number(viewY), number(viewZ),
                    number(viewRotX), number(viewRotY), number(viewRotZ), number(viewHeight),
                    0, 0, 1, false);
            return new FirstPersonCombatLibrary.ItemProfile(
                    provisionalId, rigId,
                    base == null && typeBox.getSelectedItem() == InventorySystem.ItemType.SHIELD
                            ? FirstPersonCombatLibrary.WieldHand.LEFT
                            : base == null ? FirstPersonCombatLibrary.WieldHand.RIGHT : base.wieldHand(),
                    animationSetId, socket,
                    base == null ? 0 : base.secondaryGripX(),
                    base == null ? 0 : base.secondaryGripY(),
                    base == null ? 0 : base.secondaryGripZ(),
                    base == null ? "" : base.leftArmorPath(),
                    base == null ? "" : base.rightArmorPath(),
                    base == null ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : base.leftCoverage(),
                    base == null ? FirstPersonCombatLibrary.ArmCoverage.OVERLAY : base.rightCoverage(),
                    attachmentBoneSelector.attachmentBone(),
                    base == null ? Map.of() : base.overrides());
        };
        EquipmentCombinationPreviewPanel equipmentPreview = new EquipmentCombinationPreviewPanel(
                firstPersonModelField::getText,
                () -> new EquipmentViewModelProfile(number(viewX), number(viewY), number(viewZ),
                        number(viewRotX), number(viewRotY), number(viewRotZ), number(viewHeight),
                        number(swingX), number(swingY), number(swingZ), pairedHands.isSelected()),
                () -> (InventorySystem.ItemType) typeBox.getSelectedItem(),
                twoHandedBox::isSelected,
                workingFirstPersonProfile,
                () -> (WeaponType) weaponTypeBox.getSelectedItem());
        equipmentPreview.bindModelScale(
                () -> number(viewHeight), value -> viewHeight.setValue(value));
        equipmentPreview.setSelectedAttachmentBone(itemFirstPersonContent.resolveAttachmentBone(
                workingFirstPersonProfile.get()));
        Runnable updatePlacementControls = () -> {
            InventorySystem.ItemType type = (InventorySystem.ItemType) typeBox.getSelectedItem();
            boolean supported = (type == InventorySystem.ItemType.WEAPON
                    || type == InventorySystem.ItemType.SHIELD)
                    && firstPersonModelField.getText() != null
                    && !firstPersonModelField.getText().trim().isBlank();
            autoPlaceButton.setEnabled(supported && !placementBusy[0]);
            editGripButton.setEnabled(supported && !placementBusy[0]);
            flipPlacementButton.setEnabled(supported && !placementBusy[0]);
            flipPlacementButton.setText(type == InventorySystem.ItemType.SHIELD
                    ? "Flip Shield Face" : "Flip Grip End");
        };
        Consumer<EquipmentAutoPlacementService.PlacementProposal> applyPlacement = proposal -> {
            if (proposal == null) return;
            FirstPersonCombatLibrary.ItemProfile base = workingFirstPersonProfile.get();
            EquipmentViewModelProfile socket = proposal.socket();
            firstPersonProfile[0] = new FirstPersonCombatLibrary.ItemProfile(
                    base.itemId(), base.rigId(), base.wieldHand(), base.animationSetId(), socket,
                    proposal.secondaryGrip() == null ? 0 : proposal.secondaryGrip().x,
                    proposal.secondaryGrip() == null ? 0 : proposal.secondaryGrip().y,
                    proposal.secondaryGrip() == null ? 0 : proposal.secondaryGrip().z,
                    base.leftArmorPath(), base.rightArmorPath(), base.leftCoverage(),
                    base.rightCoverage(), base.attachmentBone(), base.overrides());
            viewX.setValue(socket.positionX());
            viewY.setValue(socket.positionY());
            viewZ.setValue(socket.positionZ());
            viewRotX.setValue(socket.rotationX());
            viewRotY.setValue(socket.rotationY());
            viewRotZ.setValue(socket.rotationZ());
            viewHeight.setValue(socket.normalizedHeight());
            lastPlacement[0] = proposal;
            placementSummary.setText(proposal.summary());
            placementSummary.setToolTipText(String.join(" ", proposal.diagnostics()));
            equipmentPreview.refreshPose();
        };
        Consumer<Boolean> runAutoPlacement = flipped -> {
            if (placementBusy[0]) return;
            InventorySystem.ItemType type = (InventorySystem.ItemType) typeBox.getSelectedItem();
            String modelPath = firstPersonModelField.getText() == null
                    ? "" : firstPersonModelField.getText().trim();
            if ((type != InventorySystem.ItemType.WEAPON
                    && type != InventorySystem.ItemType.SHIELD) || modelPath.isBlank()) {
                placementSummary.setText("Select a weapon or shield model first");
                return;
            }
            placementUndo[0] = workingFirstPersonProfile.get();
            undoPlacementButton.setEnabled(true);
            placementBusy[0] = true;
            updatePlacementControls.run();
            placementSummary.setText("Analyzing model...");
            FirstPersonCombatLibrary.ItemProfile placementProfile = workingFirstPersonProfile.get();
            new SwingWorker<EquipmentAutoPlacementService.PlacementProposal, Void>() {
                @Override protected EquipmentAutoPlacementService.PlacementProposal doInBackground()
                        throws Exception {
                    return EquipmentAutoPlacementService.proposeForAttachment(modelPath, type,
                            (WeaponType) weaponTypeBox.getSelectedItem(), twoHandedBox.isSelected(),
                            Boolean.TRUE.equals(flipped), itemFirstPersonContent, placementProfile);
                }

                @Override protected void done() {
                    placementBusy[0] = false;
                    updatePlacementControls.run();
                    try {
                        applyPlacement.accept(get());
                    } catch (Exception exception) {
                        placementSummary.setText("Placement failed: " + placementRootMessage(exception));
                        placementSummary.setToolTipText(placementRootMessage(exception));
                    }
                }
            }.execute();
        };
        Consumer<String> refreshAttachmentBoneChoices = retainedBone -> {
            FirstPersonCombatLibrary.ItemProfile profile = workingFirstPersonProfile.get();
            FirstPersonCombatLibrary.RigDefinition selectedRig = itemFirstPersonContent.rigFor(profile);
            attachmentBoneLoading[0] = true;
            attachmentBoneSelector.populate(selectedRig, List.of(), profile.wieldHand(), retainedBone);
            attachmentBoneLoading[0] = false;
            new SwingWorker<List<LwjglSkinnedModel.SkeletonNodeMetadata>, Void>() {
                @Override protected List<LwjglSkinnedModel.SkeletonNodeMetadata> doInBackground()
                        throws Exception {
                    InventorySystem.ItemType itemType =
                            (InventorySystem.ItemType) typeBox.getSelectedItem();
                    CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                            itemFirstPersonContent, selectedRig,
                            itemType == InventorySystem.ItemType.SHIELD
                                    ? WeaponType.NONE : (WeaponType) weaponTypeBox.getSelectedItem(),
                            profile, profile.wieldHand());
                    return LwjglSkinnedModel.loadCached(definition).skeletonNodes();
                }

                @Override protected void done() {
                    try {
                        String current = attachmentBoneSelector.attachmentBone();
                        attachmentBoneLoading[0] = true;
                        attachmentBoneSelector.populate(selectedRig, get(), profile.wieldHand(), current);
                    } catch (Exception ignored) {
                        // Normal first-person validation reports a missing or unreadable rig.
                    } finally {
                        attachmentBoneLoading[0] = false;
                    }
                }
            }.execute();
        };
        refreshAttachmentBoneChoices.accept(
                initialFirstPersonProfile == null ? "" : initialFirstPersonProfile.attachmentBone());
        attachmentBoneSelector.addActionListener(event -> {
            if (attachmentBoneLoading[0] || placementBusy[0]) return;
            FirstPersonCombatLibrary.ItemProfile currentView = workingFirstPersonProfile.get();
            FirstPersonCombatLibrary.ItemProfile before = firstPersonProfile[0] == null
                    ? new FirstPersonCombatLibrary.ItemProfile(
                    currentView.itemId(), currentView.rigId(), authoredAttachmentHand[0],
                    currentView.animationSetId(), currentView.socketTransform(),
                    currentView.secondaryGripX(), currentView.secondaryGripY(),
                    currentView.secondaryGripZ(), currentView.leftArmorPath(),
                    currentView.rightArmorPath(), currentView.leftCoverage(),
                    currentView.rightCoverage(), authoredAttachmentBone[0], currentView.overrides())
                    : firstPersonProfile[0];
            FirstPersonCombatLibrary.WieldHand inherited = attachmentBoneSelector.inheritedHand();
            FirstPersonCombatLibrary.WieldHand selectedHand = inherited == null
                    ? before.wieldHand() : inherited;
            String selectedBone = attachmentBoneSelector.attachmentBone();
            if (authoredAttachmentBone[0].equals(selectedBone)
                    && authoredAttachmentHand[0] == selectedHand) return;
            FirstPersonCombatLibrary.ItemProfile changed = new FirstPersonCombatLibrary.ItemProfile(
                    before.itemId(), before.rigId(), selectedHand, before.animationSetId(),
                    before.socketTransform(), before.secondaryGripX(), before.secondaryGripY(),
                    before.secondaryGripZ(), before.leftArmorPath(), before.rightArmorPath(),
                    before.leftCoverage(), before.rightCoverage(), selectedBone, before.overrides());
            firstPersonProfile[0] = changed;
            authoredAttachmentBone[0] = selectedBone;
            authoredAttachmentHand[0] = selectedHand;
            equipmentPreview.setSelectedAttachmentBone(
                    itemFirstPersonContent.resolveAttachmentBone(changed));
            runAutoPlacement.accept(false);
            placementUndo[0] = before;
            undoPlacementButton.setEnabled(true);
        });
        pickAttachmentBoneButton.addActionListener(event ->
                equipmentPreview.pickAttachmentBone(attachmentBoneSelector::selectExplicit));
        autoPlaceButton.addActionListener(event -> runAutoPlacement.accept(false));
        flipPlacementButton.addActionListener(event -> runAutoPlacement.accept(
                lastPlacement[0] == null || !lastPlacement[0].flipped()));
        undoPlacementButton.addActionListener(event -> {
            FirstPersonCombatLibrary.ItemProfile restore = placementUndo[0];
            if (restore == null) return;
            placementUndo[0] = workingFirstPersonProfile.get();
            firstPersonProfile[0] = restore;
            authoredAttachmentBone[0] = restore.attachmentBone();
            authoredAttachmentHand[0] = restore.wieldHand();
            refreshAttachmentBoneChoices.accept(restore.attachmentBone());
            EquipmentViewModelProfile socket = restore.socketTransform();
            viewX.setValue(socket.positionX()); viewY.setValue(socket.positionY());
            viewZ.setValue(socket.positionZ()); viewRotX.setValue(socket.rotationX());
            viewRotY.setValue(socket.rotationY()); viewRotZ.setValue(socket.rotationZ());
            viewHeight.setValue(socket.normalizedHeight());
            lastPlacement[0] = null;
            placementSummary.setText("Previous placement restored");
            equipmentPreview.refreshPose();
        });
        editGripButton.addActionListener(event -> {
            InventorySystem.ItemType type = (InventorySystem.ItemType) typeBox.getSelectedItem();
            String modelPath = firstPersonModelField.getText() == null
                    ? "" : firstPersonModelField.getText().trim();
            if (modelPath.isBlank()) return;
            FirstPersonCombatLibrary.ItemProfile current = workingFirstPersonProfile.get();
            try {
                EquipmentGripEditorDialog.Result edited = EquipmentGripEditorDialog.show(
                        this, modelPath, type, (WeaponType) weaponTypeBox.getSelectedItem(),
                        twoHandedBox.isSelected(), itemFirstPersonContent, current,
                        lastPlacement[0]);
                if (edited == null) return;
                placementUndo[0] = current;
                undoPlacementButton.setEnabled(true);
                FirstPersonCombatLibrary.ItemProfile changed = new FirstPersonCombatLibrary.ItemProfile(
                        current.itemId(), current.rigId(), current.wieldHand(),
                        current.animationSetId(), edited.socket(), edited.secondaryX(),
                        edited.secondaryY(), edited.secondaryZ(), current.leftArmorPath(),
                        current.rightArmorPath(), current.leftCoverage(), current.rightCoverage(),
                        current.attachmentBone(), current.overrides());
                firstPersonProfile[0] = changed;
                EquipmentViewModelProfile socket = edited.socket();
                viewX.setValue(socket.positionX()); viewY.setValue(socket.positionY());
                viewZ.setValue(socket.positionZ()); viewRotX.setValue(socket.rotationX());
                viewRotY.setValue(socket.rotationY()); viewRotZ.setValue(socket.rotationZ());
                viewHeight.setValue(socket.normalizedHeight());
                placementSummary.setText("Visual grip adjustment");
                equipmentPreview.refreshPose();
            } catch (Exception exception) {
                showAdaptiveTextMessageDialog("Grip editor could not open: "
                                + placementRootMessage(exception), "Edit Grip", JOptionPane.ERROR_MESSAGE);
            }
        });
        Timer initialPlacementTimer = new Timer(350, event -> {
            if (initialPlacementAttempted[0] || firstPersonProfile[0] != null) return;
            InventorySystem.ItemType type = (InventorySystem.ItemType) typeBox.getSelectedItem();
            String path = firstPersonModelField.getText() == null
                    ? "" : firstPersonModelField.getText().trim();
            if (EquipmentAutoPlacementService.shouldAutoPlace(
                    firstPersonProfile[0], type, path)
                    && isSupportedCharacterModelAsset(path)) {
                initialPlacementAttempted[0] = true;
                runAutoPlacement.accept(false);
            }
        });
        initialPlacementTimer.setRepeats(false);
        firstPersonModelField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                updatePlacementControls.run();
                if (!initialPlacementAttempted[0] && firstPersonProfile[0] == null) {
                    initialPlacementTimer.restart();
                }
            }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        Runnable updateModelIconSummary = () -> {
            ItemModelIconProfile framing = modelIconProfile[0];
            String path = firstPersonModelField.getText() == null
                    ? "" : firstPersonModelField.getText().trim();
            modelIconSummary.setText(path.isBlank()
                    ? "Select a First-Person Model, then set its inventory view."
                    : String.format(Locale.US,
                    "Live model · rot %.0f / %.0f / %.0f · zoom %.2fx",
                    framing.rotationX(), framing.rotationY(), framing.rotationZ(), framing.zoom()));
        };
        setIconButton.addActionListener(event -> {
            String modelPath = firstPersonModelField.getText() == null
                    ? "" : firstPersonModelField.getText().trim();
            if (modelPath.isBlank()) {
                showAssetBrowser(firstPersonModelField, AssetBrowserType.MODELS);
                modelPath = firstPersonModelField.getText() == null
                        ? "" : firstPersonModelField.getText().trim();
                updateModelIconSummary.run();
                equipmentPreview.reloadPreview();
                if (modelPath.isBlank()) return;
            }
            if (!isSupportedCharacterModelAsset(modelPath)) {
                showAdaptiveTextMessageDialog(
                        "Weapon model icons require a .glb or .fbx model.",
                        "Set Weapon Icon",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            ItemModelIconProfile edited = showWeaponModelIconDialog(
                    modelPath, modelIconProfile[0]);
            if (edited != null) {
                modelIconProfile[0] = edited;
                updateModelIconSummary.run();
            }
        });
        for (JSpinner poseSpinner : new JSpinner[] {
                viewX, viewY, viewZ, viewRotX, viewRotY, viewRotZ,
                viewHeight, swingX, swingY, swingZ
        }) {
            poseSpinner.addChangeListener(event -> {
                equipmentPreview.syncModelScale();
                equipmentPreview.refreshPose();
            });
        }
        pairedHands.addActionListener(event -> equipmentPreview.refreshPose());
        twoHandedBox.addActionListener(event -> equipmentPreview.refreshPose());
        typeBox.addActionListener(event -> equipmentPreview.refreshPose());
        weaponTypeBox.addActionListener(event -> equipmentPreview.reloadPreview());
        firstPersonModelBrowseButton.addActionListener(
                event -> SwingUtilities.invokeLater(() -> {
                    equipmentPreview.reloadPreview();
                    updateModelIconSummary.run();
                    updatePlacementControls.run();
                    InventorySystem.ItemType type = (InventorySystem.ItemType) typeBox.getSelectedItem();
                    if (!initialPlacementAttempted[0]
                            && EquipmentAutoPlacementService.shouldAutoPlace(
                            firstPersonProfile[0], type, firstPersonModelField.getText())) {
                        initialPlacementAttempted[0] = true;
                        runAutoPlacement.accept(false);
                    }
                }));
        templateBox.addActionListener(
                event -> SwingUtilities.invokeLater(() -> {
                    equipmentPreview.reloadPreview();
                    updateModelIconSummary.run();
                }));
        firstPersonAuthoringButton.addActionListener(event -> {
            String provisionalId = existing == null
                    ? normalizeContentId(nameField.getText())
                    : existing.itemId();
            FirstPersonCombatLibrary.ItemProfile edited = showItemFirstPersonProfileDialog(
                    provisionalId,
                    workingFirstPersonProfile.get(),
                    (InventorySystem.ItemType) typeBox.getSelectedItem());
            if (edited != null) {
                firstPersonProfile[0] = edited;
                authoredAttachmentBone[0] = edited.attachmentBone();
                authoredAttachmentHand[0] = edited.wieldHand();
                refreshAttachmentBoneChoices.accept(edited.attachmentBone());
                EquipmentViewModelProfile editedSocket = edited.socketTransform();
                viewX.setValue(editedSocket.positionX());
                viewY.setValue(editedSocket.positionY());
                viewZ.setValue(editedSocket.positionZ());
                viewRotX.setValue(editedSocket.rotationX());
                viewRotY.setValue(editedSocket.rotationY());
                viewRotZ.setValue(editedSocket.rotationZ());
                viewHeight.setValue(editedSocket.normalizedHeight());
                equipmentPreview.reloadPreview();
            }
        });
        JPanel equipmentPreviewRow = formRow("Equipment Preview", equipmentPreview);
        JPanel typeRow = formRow("Type", typeBox);
        JPanel materialRow = formRow("Material", materialBox);
        JPanel equipmentSkillRow = formRow("Equipment Skill", equipmentSkillBox);
        JPanel equipmentRequirementRow = formRow("Required Level", equipmentRequirementLabel);
        JPanel weaponTypeRow = formRow("Weapon Type", weaponTypeBox);
        JPanel twoHandedRow = formRow("Hands", twoHandedBox);
        JPanel magicAccuracyRow = formRow("Magic Accuracy", magicAccuracySpinner);
        JPanel magicPowerRow = formRow("Magic Power", magicPowerSpinner);
        JPanel ringStatRow = formRow("Ring Stat", statTargetBox);
        JPanel healRow = formRow("HP Restore", healSpinner);
        JPanel valueRow = formRow("Base Value", valueSpinner);
        JPanel stackableRow = formRow("Stackable", stackableBox);
        JPanel smithingRecipeRow = formRow("Smithing Recipe", smithingRecipeBox);
        JPanel smithingMaterialRow = formRow("Recipe Material", smithingMaterialLabel);
        JPanel smithingBarsRow = formRow("Bars Required", smithingBarsSpinner);
        JPanel smithingLevelRow = formRow("Smithing Level", smithingLevelSpinner);
        JPanel smithingXpRow = formRow("Calculated Smithing XP", smithingXpLabel);
        JPanel lanternEnabledRow = formRow("Lantern Behavior", lanternEnabledBox);
        JPanel lanternColorRow = formRow("Light Color", lanternColorButton);
        JPanel lanternRadiusRow = formRow("Light Radius", lanternRadiusSpinner);
        JPanel lanternIntensityRow = formRow("Light Intensity", lanternIntensitySpinner);
        JPanel lanternFlickerRow = formRow("Light Flicker", lanternFlickerSpinner);
        JPanel lanternCapacityRow = formRow("Material Fuel Capacity", lanternCapacityLabel);
        JPanel lanternCompatibilityRow = formRow("Compatible Wood", lanternCompatibilityLabel);
        JPanel lanternPreviewRow = formRow("Live Light Preview", lanternPreview);
        JPanel createLanternRow = formRow("Generator", createLanternButton);

        JTabbedPane itemTabs = new JTabbedPane();

        JPanel basicFields = createFormPanel();
        if (existing == null) {
            basicFields.add(formRow("Template", templateBox));
        }
        basicFields.add(nameRow);
        basicFields.add(typeRow);
        basicFields.add(imageRow);
        basicFields.add(modelIconRow);
        basicFields.add(valueRow);
        basicFields.add(stackableRow);
        JPanel examineBox = new JPanel(new BorderLayout(4, 4));
        examineBox.setBorder(BorderFactory.createTitledBorder("Examine Description"));
        JScrollPane examineScroll = ConstructionKitUi.scrollingForm(examineArea);
        examineBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        examineBox.add(examineScroll, BorderLayout.CENTER);
        basicFields.add(examineBox);

        JPanel basicPanel = new JPanel(new BorderLayout(6, 6));
        basicPanel.add(basicFields, BorderLayout.NORTH);

        JPanel equipmentFields = createFormPanel();
        equipmentFields.add(materialRow);
        equipmentFields.add(equipmentSkillRow);
        equipmentFields.add(equipmentRequirementRow);
        equipmentFields.add(weaponTypeRow);
        equipmentFields.add(twoHandedRow);
        equipmentFields.add(magicAccuracyRow);
        equipmentFields.add(magicPowerRow);
        equipmentFields.add(ringStatRow);
        equipmentFields.add(healRow);

        JPanel visualFields = createFormPanel();
        visualFields.add(paperDollRow);
        visualFields.add(useSoundRow);
        visualFields.add(firstPersonModelRow);
        visualFields.add(viewPositionRow);
        visualFields.add(viewRotationRow);
        visualFields.add(viewHeightRow);
        visualFields.add(attachmentBoneRow);
        visualFields.add(placementButtonsRow);
        visualFields.add(placementSummaryRow);
        visualFields.add(swingAxisRow);
        visualFields.add(pairedHandsRow);
        visualFields.add(firstPersonAuthoringRow);
        visualFields.add(equipmentPreviewRow);

        JPanel smithingFields = createFormPanel();
        smithingFields.add(smithingRecipeRow);
        smithingFields.add(smithingMaterialRow);
        smithingFields.add(smithingBarsRow);
        smithingFields.add(smithingLevelRow);
        smithingFields.add(smithingXpRow);

        JPanel lanternFields = createFormPanel();
        lanternFields.add(createLanternRow);
        lanternFields.add(lanternEnabledRow);
        lanternFields.add(lanternColorRow);
        lanternFields.add(lanternRadiusRow);
        lanternFields.add(lanternIntensityRow);
        lanternFields.add(lanternFlickerRow);
        lanternFields.add(lanternCapacityRow);
        lanternFields.add(lanternCompatibilityRow);
        lanternFields.add(lanternPreviewRow);

        itemTabs.addTab("Basic Info", basicPanel);
        itemTabs.addTab("Equipment & Stats", ConstructionKitUi.scrollingForm(topAlignedForm(equipmentFields)));
        itemTabs.addTab("Visuals & Sound", ConstructionKitUi.scrollingForm(topAlignedForm(visualFields)));
        itemTabs.addTab("Smithing Recipe", ConstructionKitUi.scrollingForm(topAlignedForm(smithingFields)));
        itemTabs.addTab("Pocket Lantern", ConstructionKitUi.scrollingForm(topAlignedForm(lanternFields)));

        Runnable updateItemTypeRows = () -> {
            InventorySystem.ItemType itemType = (InventorySystem.ItemType) typeBox.getSelectedItem();
            boolean weapon = itemType == InventorySystem.ItemType.WEAPON;
            boolean ring = itemType == InventorySystem.ItemType.RING;
            boolean consumable = itemType == InventorySystem.ItemType.CONSUMABLE;
            boolean utility = itemType == InventorySystem.ItemType.UTILITY;
            boolean stackableAllowed = itemType == InventorySystem.ItemType.MISC || consumable;
            boolean paperDollAllowed = itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD
                    || itemType == InventorySystem.ItemType.HEAD_GEAR
                    || itemType == InventorySystem.ItemType.CHEST_ARMOR
                    || itemType == InventorySystem.ItemType.LEG_ARMOR;
            boolean firstPersonModelAllowed = itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD
                    || itemType == InventorySystem.ItemType.CHEST_ARMOR;
            boolean equippable = itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD
                    || itemType == InventorySystem.ItemType.HEAD_GEAR
                    || itemType == InventorySystem.ItemType.CHEST_ARMOR
                    || itemType == InventorySystem.ItemType.LEG_ARMOR
                    || itemType == InventorySystem.ItemType.RING
                    || utility;
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            boolean metal = material != null && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            boolean smithingEnabled = metal && smithingRecipeBox.isSelected();

            if (!weapon) {
                weaponTypeBox.setSelectedItem(WeaponType.SWORD);
                twoHandedBox.setSelected(false);
            }
            paperDollRow.setVisible(paperDollAllowed);
            imageRow.setVisible(!weapon);
            modelIconRow.setVisible(weapon);
            firstPersonModelRow.setVisible(firstPersonModelAllowed);
            viewPositionRow.setVisible(firstPersonModelAllowed);
            viewRotationRow.setVisible(firstPersonModelAllowed);
            viewHeightRow.setVisible(firstPersonModelAllowed);
            attachmentBoneRow.setVisible(itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD);
            placementButtonsRow.setVisible(itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD);
            placementSummaryRow.setVisible(itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD);
            swingAxisRow.setVisible(firstPersonModelAllowed);
            pairedHandsRow.setVisible(itemType == InventorySystem.ItemType.CHEST_ARMOR && firstPersonModelAllowed);
            firstPersonAuthoringRow.setVisible(firstPersonModelAllowed);
            equipmentPreviewRow.setVisible(firstPersonModelAllowed);
            weaponTypeRow.setVisible(weapon);
            twoHandedRow.setVisible(weapon);
            magicAccuracyRow.setVisible(weapon);
            magicPowerRow.setVisible(weapon);
            equipmentSkillRow.setVisible(equippable && !utility);
            equipmentRequirementRow.setVisible(equippable && !utility);
            ringStatRow.setVisible(ring);
            healRow.setVisible(consumable);
            stackableRow.setVisible(stackableAllowed);
            smithingRecipeRow.setVisible(metal);
            smithingMaterialRow.setVisible(metal);
            smithingBarsRow.setVisible(smithingEnabled);
            smithingLevelRow.setVisible(smithingEnabled);
            smithingXpRow.setVisible(smithingEnabled);
            lanternEnabledRow.setVisible(utility);
            lanternColorRow.setVisible(utility);
            lanternRadiusRow.setVisible(utility);
            lanternIntensityRow.setVisible(utility);
            lanternFlickerRow.setVisible(utility);
            lanternCapacityRow.setVisible(utility);
            lanternCompatibilityRow.setVisible(utility);
            lanternPreviewRow.setVisible(utility);

            if (!stackableAllowed) {
                stackableBox.setSelected(false);
            }
            if (!ring) {
                selectStatTargetOption(statTargetBox, null);
            }
            EquipmentSkillOption selectedSkill = (EquipmentSkillOption) equipmentSkillBox.getSelectedItem();
            if (!equippable || utility) {
                selectEquipmentSkill(equipmentSkillBox, null);
            } else if ((selectedSkill == null || selectedSkill.skill() == null)
                    && itemType != InventorySystem.ItemType.RING) {
                selectEquipmentSkill(equipmentSkillBox,
                        MapDesignLibrary.CustomItem.defaultEquipmentSkill(
                                itemType, (WeaponType) weaponTypeBox.getSelectedItem()));
            }
            updateEquipmentRequirement.run();
            updateLanternControls.run();
            itemTabs.revalidate();
            itemTabs.repaint();
            updateModelIconSummary.run();
            updatePlacementControls.run();
            if (!initialPlacementAttempted[0]
                    && EquipmentAutoPlacementService.shouldAutoPlace(
                    firstPersonProfile[0], itemType, firstPersonModelField.getText())) {
                initialPlacementTimer.restart();
            }
        };
        typeBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
        });
        materialBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
            updateEquipmentRequirement.run();
            updateLanternControls.run();
        });
        equipmentSkillBox.addActionListener(event -> updateEquipmentRequirement.run());
        weaponTypeBox.addActionListener(event -> {
            EquipmentSkillOption selectedSkill = (EquipmentSkillOption) equipmentSkillBox.getSelectedItem();
            if (typeBox.getSelectedItem() == InventorySystem.ItemType.WEAPON
                    && selectedSkill != null
                    && (selectedSkill.skill() == CharacterSkill.ATTACK
                    || selectedSkill.skill() == CharacterSkill.MAGIC_ACCURACY)) {
                selectEquipmentSkill(equipmentSkillBox,
                        MapDesignLibrary.CustomItem.defaultEquipmentSkill(
                                InventorySystem.ItemType.WEAPON,
                                (WeaponType) weaponTypeBox.getSelectedItem()));
            }
            updateEquipmentRequirement.run();
        });
        smithingRecipeBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
        });
        smithingBarsSpinner.addChangeListener(event -> updateSmithingRecipeControls.run());
        templateBox.addActionListener(event -> updateItemTypeRows.run());
        updateSmithingRecipeControls.run();
        updateLanternControls.run();
        updateItemTypeRows.run();
        updateModelIconSummary.run();

        int result = showScrollableFormDialog(itemTabs, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom item needs a name.");
            return null;
        }

        String iconPath;
        String paperDollOverlayPath;
        InventorySystem.ItemType selectedItemType = (InventorySystem.ItemType) typeBox.getSelectedItem();
        boolean selectedWeapon = selectedItemType == InventorySystem.ItemType.WEAPON;
        boolean selectedRing = selectedItemType == InventorySystem.ItemType.RING;
        boolean selectedConsumable = selectedItemType == InventorySystem.ItemType.CONSUMABLE;
        boolean selectedUtility = selectedItemType == InventorySystem.ItemType.UTILITY;
        boolean selectedStackableAllowed = selectedItemType == InventorySystem.ItemType.MISC || selectedConsumable;
        boolean selectedPaperDollAllowed = selectedItemType == InventorySystem.ItemType.WEAPON
                || selectedItemType == InventorySystem.ItemType.SHIELD
                || selectedItemType == InventorySystem.ItemType.HEAD_GEAR
                || selectedItemType == InventorySystem.ItemType.CHEST_ARMOR
                || selectedItemType == InventorySystem.ItemType.LEG_ARMOR;
        boolean selectedFirstPersonModelAllowed = selectedItemType == InventorySystem.ItemType.WEAPON
                || selectedItemType == InventorySystem.ItemType.SHIELD
                || selectedItemType == InventorySystem.ItemType.CHEST_ARMOR;
        GearMaterial selectedMaterial = (GearMaterial) materialBox.getSelectedItem();
        boolean selectedMetal = selectedMaterial != null
                && selectedMaterial.getFamily() == GearMaterial.MaterialFamily.METAL;
        MaterialDefinition selectedMaterialDefinition = selectedMaterial == null
                ? null : MaterialCatalog.snapshot().find(selectedMaterial.id());
        if (selectedUtility && lanternEnabledBox.isSelected()
                && (!selectedMetal || selectedMaterialDefinition == null
                || selectedMaterialDefinition.lanternFuelCapacitySeconds() <= 0)) {
            setStatus("Pocket lanterns require a Metal material with positive lantern capacity.");
            return null;
        }
        try {
            iconPath = normalizeCustomItemImagePath(iconPathField.getText(), name);
            paperDollOverlayPath = selectedPaperDollAllowed
                    ? normalizeOptionalCustomItemImagePath(paperDollOverlayField.getText(), name + "_paper_doll")
                    : "";
        } catch (Exception exception) {
            setStatus("Item image failed: " + exception.getMessage());
            return null;
        }
        String firstPersonModelPath = selectedFirstPersonModelAllowed && firstPersonModelField.getText() != null
                ? firstPersonModelField.getText().trim()
                : "";
        if (!firstPersonModelPath.isBlank() && !isSupportedCharacterModelAsset(firstPersonModelPath)) {
            setStatus("First-person equipment models must be .glb or .fbx assets.");
            return null;
        }
        if (selectedWeapon && firstPersonModelPath.isBlank()) {
            setStatus("Weapons need a 3D model for their live inventory icon.");
            return null;
        }

        String resultItemId = existing == null ? nextCustomItemId(name) : existing.itemId();
        if (selectedFirstPersonModelAllowed
                && (!firstPersonModelPath.isBlank() || firstPersonProfile[0] != null)) {
            FirstPersonCombatLibrary.ItemProfile profile = workingFirstPersonProfile.get();
            pendingItemFirstPersonProfile = new FirstPersonCombatLibrary.ItemProfile(
                    resultItemId,
                    profile.rigId(),
                    profile.wieldHand(),
                    profile.animationSetId(),
                    profile.socketTransform(),
                    profile.secondaryGripX(),
                    profile.secondaryGripY(),
                    profile.secondaryGripZ(),
                    profile.leftArmorPath(),
                    profile.rightArmorPath(),
                    profile.leftCoverage(),
                    profile.rightCoverage(),
                    profile.attachmentBone(),
                    profile.overrides());
        }
        return new MapDesignLibrary.CustomItem(
                resultItemId,
                name,
                selectedItemType,
                iconPath,
                paperDollOverlayPath,
                useSoundField.getText() == null ? "" : useSoundField.getText().trim(),
                selectedWeapon
                        ? (WeaponType) weaponTypeBox.getSelectedItem()
                        : WeaponType.NONE,
                selectedWeapon && twoHandedBox.isSelected(),
                selectedMaterial,
                selectedConsumable ? ((Number) healSpinner.getValue()).intValue() : 0,
                ((Number) valueSpinner.getValue()).intValue(),
                examineArea.getText() == null ? "" : examineArea.getText().trim(),
                selectedRing ? ((StatTargetOption) statTargetBox.getSelectedItem()).stat() : null,
                selectedStackableAllowed && stackableBox.isSelected(),
                selectedMetal && smithingRecipeBox.isSelected(),
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingBarsSpinner.getValue()).intValue()
                        : 1,
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingLevelSpinner.getValue()).intValue()
                        : 1,
                selectedWeapon ? ((Number) magicAccuracySpinner.getValue()).intValue() : 0,
                selectedWeapon ? ((Number) magicPowerSpinner.getValue()).intValue() : 0,
                firstPersonModelPath,
                new EquipmentViewModelProfile(number(viewX), number(viewY), number(viewZ),
                        number(viewRotX), number(viewRotY), number(viewRotZ), number(viewHeight),
                        number(swingX), number(swingY), number(swingZ), pairedHands.isSelected()),
                existing == null ? "" : existing.sourceEnemyId(),
                modelIconProfile[0],
                selectedUtility ? null : ((EquipmentSkillOption) equipmentSkillBox.getSelectedItem()).skill(),
                selectedUtility && lanternEnabledBox.isSelected()
                        ? new LanternDefinition(true, lanternColor[0], number(lanternRadiusSpinner),
                        number(lanternIntensitySpinner), number(lanternFlickerSpinner))
                        : LanternDefinition.none());
    }

    private void selectEquipmentSkill(JComboBox<EquipmentSkillOption> box, CharacterSkill skill) {
        for (int index = 0; index < box.getItemCount(); index++) {
            EquipmentSkillOption option = box.getItemAt(index);
            if (option != null && option.skill() == skill) {
                box.setSelectedIndex(index);
                return;
            }
        }
        box.setSelectedIndex(0);
    }

    private ItemModelIconProfile showWeaponModelIconDialog(
            String modelPath,
            ItemModelIconProfile existing
    ) {
        WeaponModelIconEditorPanel preview = new WeaponModelIconEditorPanel(modelPath, existing);
        JButton reset = new JButton("Reset View");
        reset.addActionListener(event -> preview.resetView());
        JPanel toolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        toolbar.add(reset);
        toolbar.add(new JLabel("The game renders this model directly; no image file is created."));
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.add(toolbar, BorderLayout.NORTH);
        content.add(preview, BorderLayout.CENTER);
        int result = showResizableOptionDialog(
                this,
                content,
                "Set Weapon Inventory Icon",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        if (!preview.hasUsableModel()) {
            setStatus("Weapon icon model failed to load: " + preview.loadError());
            return null;
        }
        return preview.profile();
    }

    private FirstPersonCombatLibrary.ItemProfile showItemFirstPersonProfileDialog(
            String itemId,
            FirstPersonCombatLibrary.ItemProfile existing,
            InventorySystem.ItemType itemType
    ) {
        FirstPersonCombatLibrary.ItemProfile base = existing == null
                ? new FirstPersonCombatLibrary.ItemProfile(
                        itemId,
                        itemType == InventorySystem.ItemType.SHIELD
                                ? FirstPersonCombatLibrary.WieldHand.LEFT
                                : FirstPersonCombatLibrary.WieldHand.RIGHT,
                        "",
                        FirstPersonCombatLibrary.ItemProfile.socketDefaults(),
                        0, 0, 0, "", "",
                        FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                        FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                        Map.of())
                : existing;
        JComboBox<FirstPersonCombatLibrary.WieldHand> wieldHand =
                new JComboBox<>(FirstPersonCombatLibrary.WieldHand.values());
        wieldHand.setSelectedItem(base.wieldHand());
        FirstPersonCombatLibrary.Content firstPersonContent = FirstPersonCombatLibrary.load();
        List<String> rigIds = new ArrayList<>(firstPersonContent.rigs().keySet());
        rigIds.sort(String::compareToIgnoreCase);
        JComboBox<String> rig = new JComboBox<>(rigIds.toArray(String[]::new));
        rig.setEditable(true);
        rig.setSelectedItem(base.rigId().isBlank() ? firstPersonContent.defaultRigId() : base.rigId());
        List<String> setIds = new ArrayList<>(firstPersonContent.animationSets().keySet());
        setIds.sort(String::compareToIgnoreCase);
        JComboBox<String> animationSet = new JComboBox<>(setIds.toArray(String[]::new));
        animationSet.setEditable(true);
        animationSet.setSelectedItem(base.animationSetId());
        JSpinner secondaryX = decimalSpinner(base.secondaryGripX(), -10, 10, 0.01);
        JSpinner secondaryY = decimalSpinner(base.secondaryGripY(), -10, 10, 0.01);
        JSpinner secondaryZ = decimalSpinner(base.secondaryGripZ(), -10, 10, 0.01);
        AttachmentBoneSelector attachmentBone = new AttachmentBoneSelector();
        FirstPersonCombatLibrary.RigDefinition selectedRig = firstPersonContent.rig(
                String.valueOf(rig.getSelectedItem()));
        attachmentBone.populate(selectedRig, List.of(), base.wieldHand(), base.attachmentBone());
        Runnable refreshBones = () -> {
            String retained = attachmentBone.attachmentBone();
            FirstPersonCombatLibrary.RigDefinition candidate = firstPersonContent.rig(
                    String.valueOf(rig.getSelectedItem()));
            attachmentBone.populate(candidate, List.of(),
                    (FirstPersonCombatLibrary.WieldHand) wieldHand.getSelectedItem(), retained);
            new SwingWorker<List<LwjglSkinnedModel.SkeletonNodeMetadata>, Void>() {
                @Override protected List<LwjglSkinnedModel.SkeletonNodeMetadata> doInBackground()
                        throws Exception {
                    FirstPersonCombatLibrary.ItemProfile inspectionProfile =
                            new FirstPersonCombatLibrary.ItemProfile(base.itemId(), candidate.rigId(),
                                    (FirstPersonCombatLibrary.WieldHand) wieldHand.getSelectedItem(),
                                    base.animationSetId(), base.socketTransform(), base.secondaryGripX(),
                                    base.secondaryGripY(), base.secondaryGripZ(), base.leftArmorPath(),
                                    base.rightArmorPath(), base.leftCoverage(), base.rightCoverage(),
                                    retained, base.overrides());
                    CharacterModelDefinition definition = FirstPersonAnimationRuntime.definitionFor(
                            firstPersonContent, candidate, WeaponType.NONE, inspectionProfile,
                            inspectionProfile.wieldHand());
                    return LwjglSkinnedModel.loadCached(definition).skeletonNodes();
                }

                @Override protected void done() {
                    try {
                        attachmentBone.populate(candidate, get(),
                                (FirstPersonCombatLibrary.WieldHand) wieldHand.getSelectedItem(),
                                attachmentBone.attachmentBone());
                    } catch (Exception ignored) { }
                }
            }.execute();
        };
        rig.addActionListener(event -> refreshBones.run());
        wieldHand.addActionListener(event -> {
            if (attachmentBone.attachmentBone().isBlank()) refreshBones.run();
        });
        refreshBones.run();

        JPanel socketFields = createFormPanel();
        socketFields.add(formRow("Rig", rig));
        socketFields.add(formRow("Attachment Hand", wieldHand));
        if (itemType == InventorySystem.ItemType.WEAPON
                || itemType == InventorySystem.ItemType.SHIELD) {
            socketFields.add(formRow("Attachment Bone", attachmentBone));
        }
        socketFields.add(formRow("Animation Set", animationSet));
        socketFields.add(formRow("Secondary Grip X / Y / Z",
                compactSpinnerRow(secondaryX, secondaryY, secondaryZ)));

        JTextField leftArmor = new JTextField(base.leftArmorPath(), 28);
        JTextField rightArmor = new JTextField(base.rightArmorPath(), 28);
        JComboBox<FirstPersonCombatLibrary.ArmCoverage> leftCoverage =
                new JComboBox<>(FirstPersonCombatLibrary.ArmCoverage.values());
        JComboBox<FirstPersonCombatLibrary.ArmCoverage> rightCoverage =
                new JComboBox<>(FirstPersonCombatLibrary.ArmCoverage.values());
        leftCoverage.setSelectedItem(base.leftCoverage());
        rightCoverage.setSelectedItem(base.rightCoverage());
        JPanel armorFields = createFormPanel();
        armorFields.add(formRow("Left Glove / Sleeve", modelPathBrowser(leftArmor)));
        armorFields.add(formRow("Left Coverage", leftCoverage));
        armorFields.add(formRow("Right Glove / Sleeve", modelPathBrowser(rightArmor)));
        armorFields.add(formRow("Right Coverage", rightCoverage));

        String[] columns = {"Slot", "Path", "Clip", "Speed", "Impact"};
        DefaultTableModel overrideModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int column) {
                return column >= 3 ? Double.class : String.class;
            }
        };
        for (FirstPersonCombatLibrary.AnimationSlot slot
                : FirstPersonCombatLibrary.AnimationSlot.values()) {
            FirstPersonCombatLibrary.ClipBinding binding = base.override(slot);
            overrideModel.addRow(new Object[] {
                    slot.name(),
                    binding == null ? "" : binding.path(),
                    binding == null ? "" : binding.clipName(),
                    binding == null ? 1.0 : binding.playbackSpeed(),
                    binding == null ? 0.55 : binding.impactFraction()
            });
        }
        JTable overrides = new JTable(overrideModel);
        overrides.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {120, 290, 160, 70, 70};
        for (int column = 0; column < widths.length; column++) {
            overrides.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        JButton browseOverride = new JButton("Browse Selected Override Path");
        browseOverride.addActionListener(event -> {
            int row = overrides.getSelectedRow();
            if (row < 0) return;
            JTextField path = new JTextField(String.valueOf(overrideModel.getValueAt(row, 1)));
            showAssetBrowser(path, AssetBrowserType.MODELS);
            overrideModel.setValueAt(path.getText(), row, 1);
        });
        JPanel overridePanel = new JPanel(new BorderLayout(4, 4));
        overridePanel.add(browseOverride, BorderLayout.NORTH);
        overridePanel.add(new JScrollPane(overrides), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Rig & Grip", ConstructionKitUi.scrollingForm(topAlignedForm(socketFields)));
        tabs.addTab("Armor Attachments", ConstructionKitUi.scrollingForm(topAlignedForm(armorFields)));
        tabs.addTab("Clip Overrides", overridePanel);
        if (itemType == InventorySystem.ItemType.CHEST_ARMOR) tabs.setSelectedIndex(1);
        if (showScrollableFormDialog(tabs, "First-Person Item Profile") != JOptionPane.OK_OPTION) {
            return null;
        }

        EnumMap<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> overrideBindings =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        for (int row = 0; row < overrideModel.getRowCount(); row++) {
            String path = normalizedPath(String.valueOf(overrideModel.getValueAt(row, 1)));
            if (path.isBlank()) continue;
            FirstPersonCombatLibrary.AnimationSlot slot =
                    FirstPersonCombatLibrary.AnimationSlot.valueOf(
                            String.valueOf(overrideModel.getValueAt(row, 0)));
            overrideBindings.put(slot, new FirstPersonCombatLibrary.ClipBinding(
                    path,
                    String.valueOf(overrideModel.getValueAt(row, 2)).trim(),
                    tableNumber(overrideModel.getValueAt(row, 3), 1),
                    tableNumber(overrideModel.getValueAt(row, 4), 0.55)));
        }
        return new FirstPersonCombatLibrary.ItemProfile(
                itemId,
                normalizeContentId(String.valueOf(rig.getSelectedItem())),
                attachmentBone.inheritedHand() == null
                        ? (FirstPersonCombatLibrary.WieldHand) wieldHand.getSelectedItem()
                        : attachmentBone.inheritedHand(),
                normalizeContentId(String.valueOf(animationSet.getSelectedItem())),
                base.socketTransform(),
                number(secondaryX), number(secondaryY), number(secondaryZ),
                normalizedPath(leftArmor.getText()), normalizedPath(rightArmor.getText()),
                (FirstPersonCombatLibrary.ArmCoverage) leftCoverage.getSelectedItem(),
                (FirstPersonCombatLibrary.ArmCoverage) rightCoverage.getSelectedItem(),
                attachmentBone.attachmentBone(),
                overrideBindings);
    }

    private void persistItemFirstPersonProfile(FirstPersonCombatLibrary.ItemProfile updated) {
        if (updated == null || updated.itemId().isBlank()) return;
        FirstPersonCombatLibrary.Content updatedContent =
                FirstPersonCombatLibrary.loadFresh().withItemProfile(updated);
        try {
            FirstPersonCombatLibrary.save(FirstPersonCombatLibrary.RESOURCE_PATH, updatedContent);
            FirstPersonCombatLibrary.install(updatedContent);
            FirstPersonAnimationRuntime.clearCaches();
            CharacterAnimationMetadataResolver.clear();
            LwjglSkinnedModel.clearSharedCache();
        } catch (IOException exception) {
            setStatus("First-person equipment profile save failed: " + exception.getMessage());
        }
    }

    private void removeItemFirstPersonProfile(String itemId) {
        FirstPersonCombatLibrary.Content current = FirstPersonCombatLibrary.loadFresh();
        if (!current.itemProfiles().containsKey(FirstPersonCombatLibrary.normalizeId(itemId))) return;
        FirstPersonCombatLibrary.Content updated = current.withoutItemProfile(itemId);
        try {
            FirstPersonCombatLibrary.save(FirstPersonCombatLibrary.RESOURCE_PATH, updated);
            FirstPersonCombatLibrary.install(updated);
            FirstPersonAnimationRuntime.clearCaches();
            CharacterAnimationMetadataResolver.clear();
            LwjglSkinnedModel.clearSharedCache();
        } catch (IOException exception) {
            setStatus("First-person equipment profile removal failed: " + exception.getMessage());
        }
    }

    private JSpinner decimalSpinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private JPanel compactSpinnerRow(JSpinner... spinners) {
        JPanel row = new JPanel(new java.awt.GridLayout(1, spinners.length, 4, 0));
        for (JSpinner spinner : spinners)
            row.add(spinner);
        return row;
    }

    private double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private Component buildMobTabbedForm(
            JTextField nameField,
            JTextField imagePathField,
            JButton browseButton,
            JTextField paperDollSourceField,
            JButton paperDollBrowseButton,
            JSpinner xpSpinner,
            JTextArea descriptionArea,
            JList<SkillDefinition> skillList,
            Map<PlayerStat, JSpinner> statSpinners,
            JLabel hpLabel,
            JLabel difficultyPreviewLabel,
            JLabel meleeMaxDamageLabel,
            JSpinner spellBaseDamageSpinner,
            JLabel spellMaxDamageLabel,
            JSpinner combatAiSpinner,
            JSpinner awarenessRadiusSpinner,
            JSpinner movementIntervalSpinner,
            JSpinner respawnDelaySpinner,
            CharacterModelEditorFields characterModelFields,
            JTextField attackSoundField,
            JButton attackSoundBrowseButton,
            JTextField damageSoundField,
            JButton damageSoundBrowseButton,
            JButton dropsButton,
            Component butcheryEditor
    ) {
        JTabbedPane tabs = new JTabbedPane();

        JPanel generalPanel = new JPanel(new BorderLayout(6, 6));
        JPanel generalFields = createFormPanel();
        addFormRow(generalFields, "Name", nameField);
        addFormRow(generalFields, "Fallback Sprite PNG", pathFieldPanel(imagePathField, browseButton));
        addFormRow(generalFields, "Paper-Doll Source", pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        addFormRow(generalFields, "XP Reward", xpSpinner);
        generalPanel.add(generalFields, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Description & Abilities"));
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setBorder(BorderFactory.createTitledBorder("Description"));
        JScrollPane skillScroll = new JScrollPane(skillList);
        skillScroll.setBorder(BorderFactory.createTitledBorder("Skills"));
        JSplitPane descriptionSkillsSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                descriptionScroll,
                skillScroll);
        descriptionSkillsSplit.setResizeWeight(0.68);
        descriptionSkillsSplit.setDividerLocation(0.68);
        centerPanel.add(descriptionSkillsSplit, BorderLayout.CENTER);
        generalPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel combatFields = createFormPanel();
        for (PlayerStat stat : PlayerStat.values()) {
            addFormRow(combatFields, stat.getDisplayName(), statSpinners.get(stat));
        }
        addFormRow(combatFields, "Derived HP", hpLabel);
        addFormRow(combatFields, "Difficulty Rating", difficultyPreviewLabel);
        addFormRow(combatFields, "Melee Max Hit", meleeMaxDamageLabel);
        addFormRow(combatFields, "Spell Base Damage", spellBaseDamageSpinner);
        addFormRow(combatFields, "Spell Max Hit", spellMaxDamageLabel);
        addFormRow(combatFields, "Combat AI Level", combatAiSpinner);
        addFormRow(combatFields, "Awareness Radius (tiles)", awarenessRadiusSpinner);
        addFormRow(combatFields, "Movement Interval (sec)", movementIntervalSpinner);
        addFormRow(combatFields, "Respawn Delay (sec)", respawnDelaySpinner);

        JPanel modelFields = createFormPanel();
        addCharacterModelRows(modelFields, characterModelFields);

        JPanel audioLootFields = createFormPanel();
        addFormRow(audioLootFields, "Attack Sound", pathFieldPanel(attackSoundField, attackSoundBrowseButton));
        addFormRow(audioLootFields, "Hit Sound", pathFieldPanel(damageSoundField, damageSoundBrowseButton));
        addFormRow(audioLootFields, "Loot Table", dropsButton);
        if (butcheryEditor != null) {
            JPanel butcheryBox = new JPanel(new BorderLayout(4, 4));
            butcheryBox.setBorder(BorderFactory.createTitledBorder("Butchery Products"));
            butcheryBox.add(butcheryEditor, BorderLayout.CENTER);
            audioLootFields.add(butcheryBox);
        }

        JPanel audioLootPanel = new JPanel(new BorderLayout(6, 6));
        audioLootPanel.add(audioLootFields, BorderLayout.NORTH);

        tabs.addTab("General & Identity", generalPanel);
        tabs.addTab("Combat Stats & AI", ConstructionKitUi.scrollingForm(topAlignedForm(combatFields)));
        tabs.addTab("3D Model & Rig", ConstructionKitUi.scrollingForm(topAlignedForm(modelFields)));
        tabs.addTab("Audio & Loot", audioLootPanel);

        return tabs;
    }

    private final class ButcheryEditorControls {
        private final JTextField nameField;
        private final JTextField paperDollSourceField;
        private final Map<PlayerStat, JSpinner> statSpinners;
        private final JList<SkillDefinition> skillList;
        private final String existingMobId;
        private final EnemyButcheryProfile originalProfile;
        private final JRadioButton humanoidButton = new JRadioButton("Humanoid Limbs");
        private final JRadioButton leatherButton = new JRadioButton("Leather");
        private final JCheckBox valueOverrideCheck = new JCheckBox("Override calculated yield value");
        private final JSpinner valueOverrideSpinner = new JSpinner(
                new SpinnerNumberModel(25, 1, 100000, 1));
        private final JTextField leatherImageField = new JTextField(DEFAULT_LEATHER_ICON, 24);
        private final JTextArea productDescriptionArea = new JTextArea(3, 28);
        private final JLabel valuePreviewLabel = new JLabel();
        private final JLabel productSummaryLabel = new JLabel("No products generated.");
        private final JButton generateButton = new JButton();
        private final JPanel panel = new JPanel(new BorderLayout(5, 5));
        private final List<MapDesignLibrary.CustomLimb> pendingLimbs = new ArrayList<>();
        private MapDesignLibrary.CustomItem pendingLeather;
        private String generatedMobId = "";

        private ButcheryEditorControls(
                MapDesignLibrary.CustomMob existing,
                JTextField nameField,
                JTextField paperDollSourceField,
                Map<PlayerStat, JSpinner> statSpinners,
                JList<SkillDefinition> skillList,
                JTextArea descriptionArea
        ) {
            this.nameField = nameField;
            this.paperDollSourceField = paperDollSourceField;
            this.statSpinners = statSpinners;
            this.skillList = skillList;
            this.existingMobId = existing == null ? "" : existing.mobId();
            this.generatedMobId = this.existingMobId;

            EnemyButcheryProfile profile = existing == null
                    ? EnemyButcheryProfile.defaultHumanoid()
                    : existing.butcheryProfile();
            originalProfile = profile;
            humanoidButton.setSelected(profile.type() == EnemyButcheryProfile.Type.HUMANOID_LIMBS);
            leatherButton.setSelected(profile.type() == EnemyButcheryProfile.Type.LEATHER);
            ButtonGroup group = new ButtonGroup();
            group.add(humanoidButton);
            group.add(leatherButton);

            valueOverrideCheck.setSelected(profile.hasValueOverride());
            valueOverrideSpinner.setValue(profile.baseValueOverride() == null
                    ? calculatedBaseValue()
                    : profile.baseValueOverride());
            valueOverrideSpinner.setEnabled(valueOverrideCheck.isSelected());

            if (existing != null) {
                for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
                    boolean linkedByProfile = profile.limbProductIds().containsValue(limb.limbId());
                    boolean inferredLegacyLink = profile.limbProductIds().isEmpty()
                            && existing.mobId().equals(limb.sourceCreatureId());
                    if (linkedByProfile || inferredLegacyLink) {
                        pendingLimbs.add(limb);
                    }
                }
                if (!profile.leatherItemId().isBlank()) {
                    pendingLeather = design.customItems().stream()
                            .filter(item -> profile.leatherItemId().equals(item.itemId()))
                            .findFirst()
                            .orElse(null);
                    if (pendingLeather != null) {
                        leatherImageField.setText(pendingLeather.iconPath());
                        productDescriptionArea.setText(pendingLeather.examineText());
                    }
                }
            }
            if (productDescriptionArea.getText().isBlank()) {
                productDescriptionArea.setText(existing == null
                        ? "A remnant taken from " + nameField.getText() + "."
                        : existing.description());
            }
            productDescriptionArea.setLineWrap(true);
            productDescriptionArea.setWrapStyleWord(true);

            JButton leatherBrowse = new JButton("Browse");
            leatherBrowse.addActionListener(event -> browsePathInto(leatherImageField));
            JPanel leatherImageRow = pathFieldPanel(leatherImageField, leatherBrowse);
            leatherImageRow.setName("leatherImageRow");

            JPanel fields = createFormPanel();
            JPanel typeRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            typeRow.add(humanoidButton);
            typeRow.add(leatherButton);
            addFormRow(fields, "Enemy Type", typeRow);
            JPanel overrideRow = new JPanel(new BorderLayout(5, 0));
            overrideRow.add(valueOverrideCheck, BorderLayout.CENTER);
            overrideRow.add(valueOverrideSpinner, BorderLayout.EAST);
            addFormRow(fields, "Yield Value", overrideRow);
            addFormRow(fields, "Calculated Prices", valuePreviewLabel);
            addFormRow(fields, "Leather Image", leatherImageRow);
            addFormRow(fields, "Products", productSummaryLabel);
            addFormRow(fields, "Generate", generateButton);
            JPanel descriptionPanel = new JPanel(new BorderLayout());
            descriptionPanel.setBorder(BorderFactory.createTitledBorder("Product Description"));
            descriptionPanel.add(new JScrollPane(productDescriptionArea), BorderLayout.CENTER);
            fields.add(descriptionPanel);
            panel.add(fields, BorderLayout.NORTH);

            Runnable refresh = () -> {
                boolean leather = leatherButton.isSelected();
                generateButton.setText(leather
                        ? (pendingLeather == null ? "Create Leather" : "Regenerate Leather")
                        : (pendingLimbs.isEmpty() ? "Create Limbs" : "Regenerate Limbs"));
                leatherImageRow.setVisible(leather);
                paperDollSourceField.setEnabled(!leather);
                valueOverrideSpinner.setEnabled(valueOverrideCheck.isSelected());
                updatePricePreview();
                updateProductSummary();
                panel.revalidate();
                panel.repaint();
            };
            humanoidButton.addActionListener(event -> refresh.run());
            leatherButton.addActionListener(event -> refresh.run());
            valueOverrideCheck.addActionListener(event -> refresh.run());
            valueOverrideSpinner.addChangeListener(event -> updatePricePreview());
            for (JSpinner spinner : statSpinners.values()) {
                spinner.addChangeListener(event -> updatePricePreview());
            }
            skillList.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    updatePricePreview();
                }
            });
            generateButton.addActionListener(event -> {
                String name = safeText(nameField);
                if (name.isBlank()) {
                    setStatus("Name the enemy before generating butchery products.");
                    return;
                }
                String mobId = existingMobId.isBlank() ? nextCustomMobId(name) : existingMobId;
                generatedMobId = mobId;
                if (leatherButton.isSelected()) {
                    generateLeather(mobId, name);
                } else {
                    if (safeText(paperDollSourceField).isBlank()) {
                        setStatus("Humanoid limb generation requires a paper-doll source.");
                        return;
                    }
                    regenerateLimbs(mobId, name);
                }
                refresh.run();
            });
            refresh.run();
        }

        private Component component() {
            return panel;
        }

        private boolean confirmTypeSwitchImpact() {
            if (existingMobId.isBlank() || originalProfile.type() == selectedType()) {
                return true;
            }
            List<String> productIds = originalProductIds();
            StringBuilder impact = new StringBuilder(
                    "Changing the butchery type will detach the old generated products.\n\n");
            for (String productId : productIds) {
                List<String> references = externalProductReferences(productId);
                impact.append("- ").append(productId).append(": ");
                if (references.isEmpty()) {
                    impact.append("will be removed (no external references).");
                } else {
                    impact.append("will remain as standalone content because it is used by ")
                            .append(String.join(", ", references)).append('.');
                }
                impact.append('\n');
            }
            impact.append("\nApply this complete catalog change?");
            return showAdaptiveTextConfirmDialog(
                    AetherConstructionKit.this,
                    impact.toString(),
                    "Change Butchery Type",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
        }

        private boolean validateProducts() {
            String mobId = generatedMobId.isBlank() ? existingMobId : generatedMobId;
            if (leatherButton.isSelected()) {
                if (pendingLeather == null) {
                    setStatus("Create the enemy's leather product before saving.");
                    return false;
                }
                if (!pendingLeather.stackable()
                        || pendingLeather.itemType() != InventorySystem.ItemType.MISC
                        || pendingLeather.material().getFamily() != GearMaterial.MaterialFamily.HIDE) {
                    setStatus("The generated leather product is not a stackable leather material.");
                    return false;
                }
                if (!mobId.equals(pendingLeather.sourceEnemyId())) {
                    setStatus("The leather product is not linked back to this enemy.");
                    return false;
                }
                MapDesignLibrary.CustomItem collision = design.customItems().stream()
                        .filter(item -> item.itemId().equals(pendingLeather.itemId()))
                        .findFirst()
                        .orElse(null);
                if (collision != null && !mobId.equals(collision.sourceEnemyId())) {
                    setStatus("Generated leather ID " + pendingLeather.itemId()
                            + " belongs to unrelated content.");
                    return false;
                }
                if (AssetLoader.loadImage(pendingLeather.iconPath()) == null) {
                    setStatus("The leather image cannot be resolved: " + pendingLeather.iconPath());
                    return false;
                }
                return true;
            }
            if (safeText(paperDollSourceField).isBlank()) {
                setStatus("Humanoid enemies require a paper-doll source.");
                return false;
            }
            if (AssetLoader.loadImage(safeText(paperDollSourceField)) == null) {
                setStatus("The paper-doll source cannot be resolved: " + safeText(paperDollSourceField));
                return false;
            }
            Set<LimbSlot> slots = new HashSet<>();
            Set<String> ids = new HashSet<>();
            for (MapDesignLibrary.CustomLimb limb : pendingLimbs) {
                slots.add(limb.limbSlot());
                if (!ids.add(limb.limbId())) {
                    setStatus("Generated limb IDs must be unique.");
                    return false;
                }
                if (!mobId.equals(limb.sourceCreatureId())) {
                    setStatus("Generated limb " + limb.limbId() + " is linked to the wrong enemy.");
                    return false;
                }
                MapDesignLibrary.CustomLimb collision = design.customLimbs().stream()
                        .filter(existing -> existing.limbId().equals(limb.limbId()))
                        .findFirst()
                        .orElse(null);
                if (collision != null && !mobId.equals(collision.sourceCreatureId())) {
                    setStatus("Generated limb ID " + limb.limbId() + " belongs to unrelated content.");
                    return false;
                }
            }
            if (slots.size() != LimbSlot.values().length) {
                setStatus("Humanoid enemies require one generated product for every limb slot.");
                return false;
            }
            return true;
        }

        private EnemyButcheryProfile profile() {
            Integer override = valueOverrideCheck.isSelected()
                    ? ((Number) valueOverrideSpinner.getValue()).intValue()
                    : null;
            if (leatherButton.isSelected()) {
                return new EnemyButcheryProfile(
                        EnemyButcheryProfile.Type.LEATHER,
                        Map.of(),
                        pendingLeather == null ? "" : pendingLeather.itemId(),
                        override);
            }
            EnumMap<LimbSlot, String> ids = new EnumMap<>(LimbSlot.class);
            for (MapDesignLibrary.CustomLimb limb : pendingLimbs) {
                ids.put(limb.limbSlot(), limb.limbId());
            }
            return new EnemyButcheryProfile(
                    EnemyButcheryProfile.Type.HUMANOID_LIMBS,
                    ids,
                    "",
                    override);
        }

        private String mobIdForSave(String name) {
            return generatedMobId.isBlank() ? nextCustomMobId(name) : generatedMobId;
        }

        private boolean applyProducts(String appliedMobId) {
            String mobId = appliedMobId == null || appliedMobId.isBlank()
                    ? existingMobId
                    : appliedMobId;
            if (leatherButton.isSelected()) {
                try {
                    leatherImageField.setText(normalizeCustomItemImagePath(
                            safeText(leatherImageField),
                            safeText(nameField) + "_leather"));
                } catch (IOException exception) {
                    setStatus("Leather image import failed: " + exception.getMessage());
                    return false;
                }
                generateLeather(mobId, safeText(nameField));
            } else {
                regenerateLimbs(mobId, safeText(nameField));
            }
            if (leatherButton.isSelected() && pendingLeather != null) {
                replaceCustomItem(pendingLeather);
            }
            if (humanoidButton.isSelected()) {
                for (MapDesignLibrary.CustomLimb limb : pendingLimbs) {
                    replaceCustomLimb(limb);
                }
            }
            cleanupObsoleteProducts();
            return true;
        }

        private EnemyButcheryProfile.Type selectedType() {
            return leatherButton.isSelected()
                    ? EnemyButcheryProfile.Type.LEATHER
                    : EnemyButcheryProfile.Type.HUMANOID_LIMBS;
        }

        private List<String> originalProductIds() {
            if (originalProfile.type() == EnemyButcheryProfile.Type.LEATHER) {
                return originalProfile.leatherItemId().isBlank()
                        ? List.of()
                        : List.of(originalProfile.leatherItemId());
            }
            return java.util.Arrays.stream(LimbSlot.values())
                    .map(originalProfile::productId)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
        }

        private List<String> externalProductReferences(String productId) {
            MapDesignLibrary.CustomItem item = design.customItems().stream()
                    .filter(candidate -> productId.equals(candidate.itemId()))
                    .findFirst()
                    .orElse(null);
            MapDesignLibrary.CustomLimb limb = design.customLimbs().stream()
                    .filter(candidate -> productId.equals(candidate.limbId()))
                    .findFirst()
                    .orElse(null);
            ContentEntry entry = item != null
                    ? new ContentEntry(ContentCategory.ITEMS, item.displayName(), item.itemId(), "Item", item)
                    : limb != null
                            ? new ContentEntry(ContentCategory.LIMBS, limb.displayName(), limb.limbId(), "Limb", limb)
                            : null;
            if (entry == null) {
                return List.of();
            }
            String ownLink = "Enemy butchery product " + existingMobId;
            return findReferences(entry).stream()
                    .filter(reference -> !ownLink.equals(reference))
                    .distinct()
                    .toList();
        }

        private void cleanupObsoleteProducts() {
            if (existingMobId.isBlank() || originalProfile.type() == selectedType()) {
                return;
            }
            for (String productId : originalProductIds()) {
                List<String> externalReferences = externalProductReferences(productId);
                if (originalProfile.type() == EnemyButcheryProfile.Type.LEATHER) {
                    if (externalReferences.isEmpty()) {
                        design.customItems().removeIf(item -> productId.equals(item.itemId()));
                    } else {
                        for (int index = 0; index < design.customItems().size(); index++) {
                            MapDesignLibrary.CustomItem item = design.customItems().get(index);
                            if (productId.equals(item.itemId())) {
                                design.customItems().set(index, item.withSourceEnemyId(""));
                                break;
                            }
                        }
                    }
                    continue;
                }
                if (externalReferences.isEmpty()) {
                    design.customLimbs().removeIf(limb -> productId.equals(limb.limbId()));
                } else {
                    for (int index = 0; index < design.customLimbs().size(); index++) {
                        MapDesignLibrary.CustomLimb limb = design.customLimbs().get(index);
                        if (!productId.equals(limb.limbId())) {
                            continue;
                        }
                        design.customLimbs().set(index, new MapDesignLibrary.CustomLimb(
                                limb.limbId(),
                                limb.displayName(),
                                limb.limbSlot(),
                                limb.iconPath(),
                                limb.condition(),
                                limb.description(),
                                "",
                                limb.paperDollSourcePath(),
                                limb.statBonuses(),
                                limb.skillIds(),
                                limb.firstPersonModelPath(),
                                limb.firstPersonRigId(),
                                limb.paperDollDerivedIcon(),
                                limb.baseGoldValue()));
                        break;
                    }
                }
            }
        }

        private void regenerateLimbs(String mobId, String name) {
            Map<LimbSlot, MapDesignLibrary.CustomLimb> existingBySlot = new EnumMap<>(LimbSlot.class);
            pendingLimbs.forEach(limb -> existingBySlot.put(limb.limbSlot(), limb));
            List<MapDesignLibrary.CustomLimb> generated = generateLimbsForEnemy(
                    mobId,
                    name,
                    statValuesFromSpinners(statSpinners),
                    productDescriptionArea.getText(),
                    safeText(paperDollSourceField),
                    skillList.getSelectedValuesList());
            pendingLimbs.clear();
            for (MapDesignLibrary.CustomLimb limb : generated) {
                MapDesignLibrary.CustomLimb previous = existingBySlot.get(limb.limbSlot());
                if (previous == null) {
                    pendingLimbs.add(new MapDesignLibrary.CustomLimb(
                            limb.limbId(),
                            limb.displayName(),
                            limb.limbSlot(),
                            "",
                            limb.condition(),
                            limb.description(),
                            mobId,
                            safeText(paperDollSourceField),
                            limb.statBonuses(),
                            limb.skillIds(),
                            limb.firstPersonModelPath(),
                            limb.firstPersonRigId(),
                            true,
                            productValue(limb.limbSlot())));
                    continue;
                }
                pendingLimbs.add(new MapDesignLibrary.CustomLimb(
                        previous.limbId(),
                        limb.displayName(),
                        limb.limbSlot(),
                        previous.paperDollDerivedIcon() ? "" : previous.iconPath(),
                        previous.condition(),
                        previous.description(),
                        mobId,
                        safeText(paperDollSourceField),
                        limb.statBonuses(),
                        previous.skillIds(),
                        previous.firstPersonModelPath(),
                        previous.firstPersonRigId(),
                        previous.paperDollDerivedIcon()
                                || DEFAULT_LIMB_ICON.equals(previous.iconPath()),
                        productValue(limb.limbSlot())));
            }
        }

        private void generateLeather(String mobId, String name) {
            String itemId = pendingLeather == null
                    ? "item_" + limbSlugFromMobId(mobId) + "_leather"
                    : pendingLeather.itemId();
            String examineText = pendingLeather == null
                    ? productDescriptionArea.getText()
                    : pendingLeather.examineText();
            pendingLeather = new MapDesignLibrary.CustomItem(
                    itemId,
                    name + " Leather",
                    InventorySystem.ItemType.MISC,
                    safeText(leatherImageField).isBlank() ? DEFAULT_LEATHER_ICON : safeText(leatherImageField),
                    "",
                    "",
                    WeaponType.NONE,
                    false,
                    GearMaterial.LEATHER,
                    0,
                    productValue(null),
                    examineText,
                    null,
                    true,
                    false,
                    1,
                    1,
                    0,
                    0,
                    "")
                    .withSourceEnemyId(mobId);
        }

        private int calculatedLevel() {
            return DifficultyResolver.rateMonsterProfile(
                    safeText(nameField),
                    statValuesFromSpinners(statSpinners),
                    selectedSkillIds(skillList)).level();
        }

        private int calculatedBaseValue() {
            return 10 + 5 * calculatedLevel();
        }

        private int selectedBaseValue() {
            return valueOverrideCheck.isSelected()
                    ? ((Number) valueOverrideSpinner.getValue()).intValue()
                    : calculatedBaseValue();
        }

        private int productValue(LimbSlot slot) {
            double multiplier = slot == LimbSlot.BODY ? 1.25 : slot == LimbSlot.HEAD ? 1.5 : 1.0;
            return Math.max(1, (int) Math.round(selectedBaseValue() * multiplier));
        }

        private void updatePricePreview() {
            int base = selectedBaseValue();
            String prices = leatherButton.isSelected()
                    ? "Leather " + productValue(null) + "g"
                    : "Head " + productValue(LimbSlot.HEAD)
                            + "g, Body " + productValue(LimbSlot.BODY)
                            + "g, Left Arm " + productValue(LimbSlot.LEFT_ARM)
                            + "g, Right Arm " + productValue(LimbSlot.RIGHT_ARM)
                            + "g, Legs " + productValue(LimbSlot.LEGS) + "g";
            valuePreviewLabel.setText("<html><div style='width:360px'>Level " + calculatedLevel()
                    + ", base " + base + "g<br>" + prices + "</div></html>");
        }

        private void updateProductSummary() {
            productSummaryLabel.setText(leatherButton.isSelected()
                    ? pendingLeather == null ? "Leather not generated" : pendingLeather.displayName()
                    : pendingLimbs.size() + " / " + LimbSlot.values().length + " limbs linked");
        }

    }

    private void replaceCustomItem(MapDesignLibrary.CustomItem replacement) {
        for (int index = 0; index < design.customItems().size(); index++) {
            if (design.customItems().get(index).itemId().equals(replacement.itemId())) {
                design.customItems().set(index, replacement);
                return;
            }
        }
        design.customItems().add(replacement);
    }

    private void replaceCustomLimb(MapDesignLibrary.CustomLimb replacement) {
        for (int index = 0; index < design.customLimbs().size(); index++) {
            if (design.customLimbs().get(index).limbId().equals(replacement.limbId())) {
                design.customLimbs().set(index, replacement);
                return;
            }
        }
        design.customLimbs().add(replacement);
    }

    private void createCustomMob() {
        JTextField nameField = new JTextField("Custom Enemy", 24);
        JTextField imagePathField = new JTextField("assets/images/generated/mobs/custom_enemy.png", 28);
        JTextField paperDollSourceField = new JTextField("", 28);
        CharacterModelEditorFields characterModelFields = new CharacterModelEditorFields(CharacterModelDefinition.empty());
        Map<PlayerStat, JSpinner> statSpinners = enemyStatSpinners();
        JLabel hpLabel = new JLabel();
        JSpinner combatAiSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        JSpinner awarenessRadiusSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 64, 1));
        JSpinner movementIntervalSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 3600, 1));
        JSpinner respawnDelaySpinner = new JSpinner(new SpinnerNumberModel(300, 0, 86400, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 100000, 1));
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JTextField attackSoundField = new JTextField("", 24);
        JTextField damageSoundField = new JTextField("", 24);
        JButton attackSoundBrowseButton = new JButton("Browse");
        JButton damageSoundBrowseButton = new JButton("Browse");
        JButton dropsButton = new JButton("Drops");
        JLabel meleeMaxDamageLabel = new JLabel();
        JSpinner spellBaseDamageSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
        JLabel spellMaxDamageLabel = new JLabel();
        JList<SkillDefinition> skillList = skillDefinitionList();
        JTextArea descriptionArea = new JTextArea("A custom enemy.", 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JLabel difficultyPreviewLabel = new JLabel();
        List<MapDesignLibrary.CustomDropEntry> dropEntries = new ArrayList<>();

        browseButton.addActionListener(event -> browsePathInto(imagePathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        attackSoundBrowseButton.addActionListener(event -> browsePathInto(attackSoundField));
        damageSoundBrowseButton.addActionListener(event -> browsePathInto(damageSoundField));
        Runnable updateDifficultyPreview = () -> {
            EnumMap<PlayerStat, Integer> statValues = statValuesFromSpinners(statSpinners);
            hpLabel.setText("HP = " + statValues.getOrDefault(PlayerStat.VITALITY, 1));
            difficultyPreviewLabel.setText(customEnemyDifficultyPreview(statValues, selectedSkillIds(skillList)));
            meleeMaxDamageLabel.setText(enemyMeleeMaxDamagePreview(statValues));
            spellMaxDamageLabel.setText(enemySpellMaxDamagePreview(
                    statValues,
                    ((Number) spellBaseDamageSpinner.getValue()).intValue()));
        };
        ChangeListener difficultyChangeListener = event -> updateDifficultyPreview.run();
        for (JSpinner spinner : statSpinners.values()) {
            spinner.addChangeListener(difficultyChangeListener);
        }
        spellBaseDamageSpinner.addChangeListener(difficultyChangeListener);
        skillList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDifficultyPreview.run();
            }
        });
        dropsButton.addActionListener(event -> editDropEntries(dropEntries));
        updateDifficultyPreview.run();
        ButcheryEditorControls butcheryControls = new ButcheryEditorControls(
                null,
                nameField,
                paperDollSourceField,
                statSpinners,
                skillList,
                descriptionArea);

        Component formTabs = buildMobTabbedForm(
                nameField, imagePathField, browseButton, paperDollSourceField, paperDollBrowseButton,
                xpSpinner, descriptionArea, skillList, statSpinners, hpLabel, difficultyPreviewLabel,
                meleeMaxDamageLabel, spellBaseDamageSpinner, spellMaxDamageLabel, combatAiSpinner,
                awarenessRadiusSpinner, movementIntervalSpinner, respawnDelaySpinner, characterModelFields,
                attackSoundField, attackSoundBrowseButton, damageSoundField, damageSoundBrowseButton,
                dropsButton, butcheryControls.component()
        );

        int result = showScrollableFormDialog(formTabs, "Create Enemy");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom enemy needs a name.");
            return;
        }
        CharacterModelDefinition characterModel = characterModelFields.toDefinition();
        if (!validateCharacterModelDefinition(characterModel, "enemy")) {
            return;
        }
        if (!butcheryControls.validateProducts()) {
            return;
        }

        String mobId = butcheryControls.mobIdForSave(name);
        MapDesignLibrary.CustomMob mob = new MapDesignLibrary.CustomMob(
                mobId,
                name,
                imagePathField.getText() == null ? "" : imagePathField.getText().trim(),
                paperDollSourceField.getText() == null ? "" : paperDollSourceField.getText().trim(),
                statValuesFromSpinners(statSpinners),
                ((Number) xpSpinner.getValue()).intValue(),
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                attackSoundField.getText() == null ? "" : attackSoundField.getText().trim(),
                damageSoundField.getText() == null ? "" : damageSoundField.getText().trim(),
                ((Number) combatAiSpinner.getValue()).intValue(),
                ((Number) awarenessRadiusSpinner.getValue()).intValue(),
                ((Number) movementIntervalSpinner.getValue()).intValue() * 1000,
                ((Number) respawnDelaySpinner.getValue()).intValue() * 1000,
                selectedSkillIds(skillList),
                dropEntries,
                characterModel,
                butcheryControls.profile());
        List<MapDesignLibrary.CustomMob> mobsBefore = new ArrayList<>(design.customMobs());
        List<MapDesignLibrary.CustomItem> itemsBefore = new ArrayList<>(design.customItems());
        List<MapDesignLibrary.CustomLimb> limbsBefore = new ArrayList<>(design.customLimbs());
        design.customMobs().add(mob);
        if (!butcheryControls.applyProducts(mobId)) {
            restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
            return;
        }
        if (!confirmAppliedButcheryValidation(mob)) {
            restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
            return;
        }
        if (!persistSharedContent("custom enemy")) {
            restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
            return;
        }
        populatePlaceables();
        setStatus("Created custom enemy " + mob.displayName() + " with authored butchery products.");
    }

    private String customEnemyDifficultyPreview(
            Map<PlayerStat, Integer> statValues,
            List<String> skillIds) {
        DifficultyResolver.DifficultyRating rating = DifficultyResolver.rateMonsterProfile(
                "Preview Enemy",
                statValues,
                skillIds);
        return String.format("Level %d (power %.2f)", rating.level(), rating.power());
    }

    private String enemyMeleeMaxDamagePreview(Map<PlayerStat, Integer> statValues) {
        int strength = Math.max(0, statValues.getOrDefault(PlayerStat.STRENGTH, 0));
        int strengthSkill = Math.max(1, strength);
        int maxHit = Math.max(1, 1 + (strength + strengthSkill) / 3);
        return String.valueOf(maxHit);
    }

    private String enemySpellMaxDamagePreview(Map<PlayerStat, Integer> statValues, int spellBaseDamage) {
        int willpower = Math.max(0, statValues.getOrDefault(PlayerStat.WILLPOWER, 0));
        int magicPowerSkill = Math.max(1, willpower);
        int maxHit = Math.max(0, Math.max(0, spellBaseDamage) + willpower / 3 + magicPowerSkill / 3);
        return String.valueOf(maxHit);
    }

    private void createCustomNpc() {
        MapDesignLibrary.CustomNpc npc = showCustomNpcDialog("Create NPC", null, false);
        if (npc == null) {
            return;
        }

        design.customNpcs().add(npc);
        persistSharedContent("custom NPC");
        populatePlaceables();
        setStatus("Created custom NPC " + npc.displayName() + ".");
    }

    private MapDesignLibrary.CustomNpc showCustomNpcDialog(
            String title,
            MapDesignLibrary.CustomNpc existing,
            boolean shopByDefault) {
        JTextField nameField = new JTextField(existing == null ? "Custom NPC" : existing.displayName(), 24);
        JTextField imagePathField = new JTextField(
                existing == null ? "assets/images/generated/npcs/custom_npc.png" : existing.imagePath(),
                28);
        JTextField talkSoundField = new JTextField(existing == null ? "" : existing.talkSoundPath(), 24);
        CharacterModelEditorFields characterModelFields = new CharacterModelEditorFields(
                existing == null ? CharacterModelDefinition.empty() : existing.characterModel());
        JButton imageBrowseButton = new JButton("Browse");
        JButton talkSoundBrowseButton = new JButton("Browse");
        JComboBox<DialogueOption> dialogueBox = new JComboBox<>(dialogueOptions());
        if (existing != null) {
            selectDialogueOption(dialogueBox, existing.interactionId());
        }
        DefaultListModel<String> questAssignmentModel = new DefaultListModel<>();
        if (existing != null) {
            for (String questId : existing.questIds()) {
                questAssignmentModel.addElement(questId);
            }
        }
        JList<String> questAssignmentList = new JList<>(questAssignmentModel);
        questAssignmentList.setVisibleRowCount(7);
        questAssignmentList.setCellRenderer((list, questId, index, selected, focus) -> {
            MapDesignLibrary.AuthoredQuest value = design.authoredQuests().stream()
                    .filter(quest -> quest.questId().equals(questId))
                    .findFirst()
                    .orElse(null);
            JLabel label = new JLabel(value == null
                    ? "[Unavailable]  [" + questId + "]"
                    : value.displayName() + "  [" + value.questId() + "]");
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        JComboBox<MapDesignLibrary.AuthoredQuest> availableQuestBox = new JComboBox<>(
                design.authoredQuests().toArray(new MapDesignLibrary.AuthoredQuest[0]));
        availableQuestBox.setMaximumRowCount(10);
        availableQuestBox.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "No quests available" : value.displayName() + "  [" + value.questId() + "]");
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        JButton addQuestButton = new JButton("Add");
        JButton removeQuestButton = new JButton("Remove");
        JButton questUpButton = new JButton("Up");
        JButton questDownButton = new JButton("Down");
        addQuestButton.addActionListener(event -> {
            MapDesignLibrary.AuthoredQuest selected = (MapDesignLibrary.AuthoredQuest) availableQuestBox.getSelectedItem();
            if (selected == null) {
                return;
            }
            for (int index = 0; index < questAssignmentModel.size(); index++) {
                if (questAssignmentModel.get(index).equals(selected.questId())) {
                    questAssignmentList.setSelectedIndex(index);
                    return;
                }
            }
            questAssignmentModel.addElement(selected.questId());
            questAssignmentList.setSelectedIndex(questAssignmentModel.size() - 1);
        });
        removeQuestButton.addActionListener(event -> {
            int index = questAssignmentList.getSelectedIndex();
            if (index >= 0) {
                questAssignmentModel.remove(index);
            }
        });
        questUpButton.addActionListener(event -> {
            int index = questAssignmentList.getSelectedIndex();
            if (index > 0) {
                String value = questAssignmentModel.remove(index);
                questAssignmentModel.add(index - 1, value);
                questAssignmentList.setSelectedIndex(index - 1);
            }
        });
        questDownButton.addActionListener(event -> {
            int index = questAssignmentList.getSelectedIndex();
            if (index >= 0 && index < questAssignmentModel.size() - 1) {
                String value = questAssignmentModel.remove(index);
                questAssignmentModel.add(index + 1, value);
                questAssignmentList.setSelectedIndex(index + 1);
            }
        });
        JComboBox<NpcBaseOption> baseBox = new JComboBox<>(npcBaseOptions(existing).toArray(new NpcBaseOption[0]));
        JCheckBox shopkeeperBox = new JCheckBox(
                "Enable shop",
                existing == null ? shopByDefault : existing.shop() != null);
        JTextField shopNameField = new JTextField(
                existing != null && existing.shop() != null
                        ? existing.shop().shopName()
                        : nameField.getText() + "'s Shop",
                24);
        JTextArea greetingArea = new JTextArea(
                existing != null && existing.shop() != null
                        ? existing.shop().greeting()
                        : "Take a look at my wares.",
                3,
                28);
        greetingArea.setLineWrap(true);
        greetingArea.setWrapStyleWord(true);
        DefaultListModel<MapDesignLibrary.CustomShopStock> stockModel = new DefaultListModel<>();
        if (existing != null && existing.shop() != null) {
            existing.shop().stock().forEach(stockModel::addElement);
        }
        JList<MapDesignLibrary.CustomShopStock> stockList = new JList<>(stockModel);
        stockList.setVisibleRowCount(7);
        stockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockList.setCellRenderer((list, value, index, selected, focus) -> {
            String quantity = value.quantity() == -1 ? "∞" : String.valueOf(value.quantity());
            String buyPrice = value.buyPrice() < 0 ? "default" : value.buyPrice() + "g";
            String sellPrice = value.sellPrice() < 0 ? "default" : value.sellPrice() + "g";
            JLabel label = new JLabel(value.itemId() + " — qty " + quantity
                    + ", buy " + buyPrice + ", sell " + sellPrice);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });

        imageBrowseButton.addActionListener(event -> browsePathInto(imagePathField));
        talkSoundBrowseButton.addActionListener(event -> browsePathInto(talkSoundField));
        baseBox.addActionListener(event -> {
            NpcBaseOption base = (NpcBaseOption) baseBox.getSelectedItem();
            if (base == null || base.manual()) {
                return;
            }
            nameField.setText(base.displayName());
            imagePathField.setText(base.imagePath());
            talkSoundField.setText(base.talkSoundPath());
            characterModelFields.apply(base.characterModel());
            shopNameField.setText(base.displayName() + "'s Shop");
        });

        JPanel identityPanel = new JPanel(new BorderLayout(6, 6));
        JPanel identityFields = createFormPanel();
        addFormRow(identityFields, "Base NPC", baseBox);
        addFormRow(identityFields, "Name", nameField);
        addFormRow(identityFields, "Fallback Sprite PNG", pathFieldPanel(imagePathField, imageBrowseButton));
        addFormRow(identityFields, "Talk Sound", pathFieldPanel(talkSoundField, talkSoundBrowseButton));
        addFormRow(identityFields, "Dialogue", dialogueBox);
        identityPanel.add(identityFields, BorderLayout.NORTH);

        JPanel modelFields = createFormPanel();
        addCharacterModelRows(modelFields, characterModelFields);

        JPanel questPanel = new JPanel(new BorderLayout(6, 6));
        questPanel.add(availableQuestBox, BorderLayout.NORTH);
        questPanel.add(new JScrollPane(questAssignmentList), BorderLayout.CENTER);
        JPanel questButtons = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        questButtons.add(addQuestButton);
        questButtons.add(removeQuestButton);
        questButtons.add(questUpButton);
        questButtons.add(questDownButton);
        questPanel.add(questButtons, BorderLayout.SOUTH);

        JButton addStockButton = new JButton("Add");
        JButton editStockButton = new JButton("Edit");
        JButton removeStockButton = new JButton("Remove");
        addStockButton.addActionListener(event -> {
            MapDesignLibrary.CustomShopStock stock = showShopStockDialog(null);
            if (stock != null) {
                stockModel.addElement(stock);
            }
        });
        editStockButton.addActionListener(event -> {
            int index = stockList.getSelectedIndex();
            if (index < 0) {
                return;
            }
            MapDesignLibrary.CustomShopStock stock = showShopStockDialog(stockModel.get(index));
            if (stock != null) {
                stockModel.set(index, stock);
            }
        });
        removeStockButton.addActionListener(event -> {
            int index = stockList.getSelectedIndex();
            if (index >= 0) {
                stockModel.remove(index);
            }
        });

        JPanel shopPanel = new JPanel(new BorderLayout(6, 6));
        JPanel shopFields = createFormPanel();
        addFormRow(shopFields, "", shopkeeperBox);
        addFormRow(shopFields, "Shop Name", shopNameField);
        addFormRow(shopFields, "Greeting", new JScrollPane(greetingArea));
        shopPanel.add(shopFields, BorderLayout.NORTH);
        shopPanel.add(new JScrollPane(stockList), BorderLayout.CENTER);
        JPanel stockButtons = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        stockButtons.add(addStockButton);
        stockButtons.add(editStockButton);
        stockButtons.add(removeStockButton);
        shopPanel.add(stockButtons, BorderLayout.SOUTH);

        Runnable updateShopControls = () -> {
            boolean enabled = shopkeeperBox.isSelected();
            shopNameField.setEnabled(enabled);
            greetingArea.setEnabled(enabled);
            stockList.setEnabled(enabled);
            addStockButton.setEnabled(enabled);
            editStockButton.setEnabled(enabled);
            removeStockButton.setEnabled(enabled);
        };
        shopkeeperBox.addActionListener(event -> updateShopControls.run());
        updateShopControls.run();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Identity", identityPanel);
        tabs.addTab("3D Model & Rig", ConstructionKitUi.scrollingForm(topAlignedForm(modelFields)));
        tabs.addTab("Quests", questPanel);
        tabs.addTab("Shop & Stock", shopPanel);

        int result = showScrollableFormDialog(tabs, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom NPC needs a name.");
            return null;
        }
        CharacterModelDefinition characterModel = characterModelFields.toDefinition();
        if (!validateCharacterModelDefinition(characterModel, "NPC")) {
            return null;
        }

        DialogueOption dialogueOption = (DialogueOption) dialogueBox.getSelectedItem();
        MapDesignLibrary.CustomShop shop = null;
        if (shopkeeperBox.isSelected()) {
            List<MapDesignLibrary.CustomShopStock> stock = new ArrayList<>();
            for (int i = 0; i < stockModel.size(); i++) {
                stock.add(stockModel.get(i));
            }
            shop = new MapDesignLibrary.CustomShop(
                    shopNameField.getText(),
                    greetingArea.getText(),
                    stock);
        }
        return new MapDesignLibrary.CustomNpc(
                existing == null ? nextCustomNpcId(name) : existing.npcId(),
                name,
                imagePathField.getText() == null ? "" : imagePathField.getText().trim(),
                talkSoundField.getText() == null ? "" : talkSoundField.getText().trim(),
                dialogueOption == null ? "" : dialogueOption.interactionId(),
                shop,
                characterModel,
                java.util.stream.IntStream.range(0, questAssignmentModel.size())
                        .mapToObj(questAssignmentModel::get)
                        .toList());
    }

    private void createCustomFurniture() {
        MapDesignLibrary.CustomFurnitureDefinition furniture = showCustomFurnitureDialog("Create Furniture", null);
        if (furniture == null) {
            return;
        }

        design.customFurniture().add(furniture);
        persistSharedContent("custom furniture");
        populatePlaceables();
        setStatus("Created furniture " + furniture.displayName() + ".");
    }

    private MapDesignLibrary.CustomFurnitureDefinition showCustomFurnitureDialog(
            String title,
            MapDesignLibrary.CustomFurnitureDefinition existing
    ) {
        JTextField nameField = new JTextField(existing == null ? "New Furniture" : existing.displayName(), 24);
        JTextField categoryField = new JTextField(existing == null ? "Furniture" : existing.category(), 20);
        JTextField modelPathField = new JTextField(existing == null ? "" : existing.modelPath(), 28);
        JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1.0 : existing.defaultScale(),
                0.05,
                20.0,
                0.05));
        JCheckBox blocksMovementBox = new JCheckBox(
                "Blocks movement",
                existing != null && existing.defaultBlocksMovement());
        JTextField interactionField = new JTextField(existing == null ? "" : existing.interactionId(), 24);
        JButton browseModelButton = new JButton("Browse");
        browseModelButton.addActionListener(event -> browsePathInto(modelPathField));

        MapDesignLibrary.LightAttachment existingLight = existing == null ? null : existing.lightAttachment();
        JCheckBox lightEnabledBox = new JCheckBox("Attach light", existingLight != null && existingLight.enabled());
        JComboBox<LightPreset> presetBox = new JComboBox<>(LIGHT_PRESETS.toArray(new LightPreset[0]));
        JTextField lightColorField = new JTextField(
                existingLight == null ? MapLightingSettings.colorHex(0xFF8B42) : MapLightingSettings.colorHex(existingLight.colorRgb()),
                10);
        JSpinner lightRadiusSpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 5.0 : existingLight.radius(),
                0.1,
                64.0,
                0.1));
        JSpinner lightIntensitySpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 1.0 : existingLight.intensity(),
                0.0,
                8.0,
                0.05));
        JSpinner lightOffsetXSpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 0.0 : existingLight.offsetX(),
                -4.0,
                4.0,
                0.05));
        JSpinner lightOffsetYSpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 0.65 : existingLight.offsetY(),
                -8.0,
                8.0,
                0.05));
        JSpinner lightOffsetZSpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 0.0 : existingLight.offsetZ(),
                -4.0,
                4.0,
                0.05));
        JSpinner flickerSpinner = new JSpinner(new SpinnerNumberModel(
                existingLight == null ? 0.0 : existingLight.flickerAmount(),
                0.0,
                1.0,
                0.01));

        presetBox.addActionListener(event -> {
            LightPreset preset = (LightPreset) presetBox.getSelectedItem();
            if (preset == null) {
                return;
            }
            lightColorField.setText(MapLightingSettings.colorHex(preset.colorRgb()));
            lightRadiusSpinner.setValue(preset.radius());
            lightIntensitySpinner.setValue(preset.intensity());
            lightOffsetYSpinner.setValue(preset.heightOffset());
            flickerSpinner.setValue(preset.flickerAmount());
        });

        JPanel fields = createFormPanel();
        addFormRow(fields, "Name", nameField);
        addFormRow(fields, "Category", categoryField);
        addFormRow(fields, "Model", modelPathFieldPanel(modelPathField, browseModelButton, "furniture"));
        addFormRow(fields, "Default Scale", scaleSpinner);
        addFormRow(fields, "", blocksMovementBox);
        addFormRow(fields, "Interaction Id", interactionField);
        addFormRow(fields, "", lightEnabledBox);
        addFormRow(fields, "Light Preset", presetBox);
        addFormRow(fields, "Light Color", lightColorField);
        addFormRow(fields, "Light Radius", lightRadiusSpinner);
        addFormRow(fields, "Light Intensity", lightIntensitySpinner);
        addFormRow(fields, "Light Offset X", lightOffsetXSpinner);
        addFormRow(fields, "Light Offset Y", lightOffsetYSpinner);
        addFormRow(fields, "Light Offset Z", lightOffsetZSpinner);
        addFormRow(fields, "Flicker", flickerSpinner);

        int result = showScrollableFormDialog(fields, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Furniture needs a name.");
            return null;
        }
        String modelPath = modelPathField.getText() == null ? "" : modelPathField.getText().trim();
        if (modelPath.isBlank()) {
            setStatus("Furniture needs a 3D model path.");
            return null;
        }
        MapDesignLibrary.LightAttachment light = lightEnabledBox.isSelected()
                ? new MapDesignLibrary.LightAttachment(
                        true,
                        MapLightingSettings.parseColor(lightColorField.getText(), 0xFF8B42),
                        ((Number) lightRadiusSpinner.getValue()).doubleValue(),
                        ((Number) lightIntensitySpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetXSpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetYSpinner.getValue()).doubleValue(),
                        ((Number) lightOffsetZSpinner.getValue()).doubleValue(),
                        ((Number) flickerSpinner.getValue()).doubleValue())
                : null;
        return new MapDesignLibrary.CustomFurnitureDefinition(
                existing == null ? nextCustomFurnitureId(name) : existing.furnitureId(),
                name,
                categoryField.getText() == null ? "" : categoryField.getText().trim(),
                modelPath,
                ((Number) scaleSpinner.getValue()).doubleValue(),
                blocksMovementBox.isSelected(),
                interactionField.getText() == null ? "" : interactionField.getText().trim(),
                light);
    }

    private List<NpcBaseOption> npcBaseOptions(MapDesignLibrary.CustomNpc existing) {
        List<NpcBaseOption> options = new ArrayList<>();
        options.add(new NpcBaseOption(
                "Manual / Custom Sprite", "", "", "", CharacterModelDefinition.empty(), true));
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            if (existing != null && existing.npcId().equals(npc.npcId())) {
                continue;
            }
            options.add(new NpcBaseOption(
                    "Custom: " + npc.displayName(),
                    npc.displayName(),
                    npc.imagePath(),
                    npc.talkSoundPath(),
                    npc.characterModel(),
                    false));
        }
        return options;
    }

    private MapDesignLibrary.CustomShopStock showShopStockDialog(
            MapDesignLibrary.CustomShopStock existing) {
        JComboBox<DropItemOption> itemBox = new JComboBox<>(dropItemOptions().toArray(new DropItemOption[0]));
        if (existing != null) {
            selectDropItem(itemBox, existing.itemId());
        }
        JCheckBox infiniteBox = new JCheckBox("Infinite stock", existing != null && existing.quantity() == -1);
        JSpinner quantityField = new JSpinner(new SpinnerNumberModel(
                existing == null || existing.quantity() < 1 ? 1 : existing.quantity(),
                1,
                9999,
                1));
        JSpinner buyPriceField = new JSpinner(new SpinnerNumberModel(
                existing == null ? -1 : existing.buyPrice(),
                -1,
                1_000_000,
                1));
        JSpinner sellPriceField = new JSpinner(new SpinnerNumberModel(
                existing == null ? -1 : existing.sellPrice(),
                -1,
                1_000_000,
                1));
        infiniteBox.addActionListener(event -> quantityField.setEnabled(!infiniteBox.isSelected()));
        quantityField.setEnabled(!infiniteBox.isSelected());

        JPanel fields = createFormPanel();
        addFormRow(fields, "Item", itemBox);
        addFormRow(fields, "Quantity", quantityField);
        addFormRow(fields, "", infiniteBox);
        addFormRow(fields, "Buy Price (-1 = default)", buyPriceField);
        addFormRow(fields, "Sell Price (-1 = default)", sellPriceField);
        if (showScrollableFormDialog(
                fields,
                existing == null ? "Add Shop Stock" : "Edit Shop Stock") != JOptionPane.OK_OPTION) {
            return null;
        }

        DropItemOption item = (DropItemOption) itemBox.getSelectedItem();
        if (item == null) {
            return null;
        }
        return new MapDesignLibrary.CustomShopStock(
                item.itemId(),
                infiniteBox.isSelected() ? -1 : ((Number) quantityField.getValue()).intValue(),
                ((Number) buyPriceField.getValue()).intValue(),
                ((Number) sellPriceField.getValue()).intValue());
    }

    private void createCustomLimb() {
        MapDesignLibrary.CustomLimb limb = showCustomLimbDialog(
                "Create Limb",
                "",
                "Custom Limb",
                LimbSlot.HEAD,
                "assets/images/generated/limbs/custom_limb.png",
                "",
                "",
                "",
                emptyStatMap(),
                List.of(),
                "",
                "",
                false,
                25);
        if (limb == null) {
            return;
        }

        design.customLimbs().add(limb);
        persistSharedContent("custom limb");
        populatePlaceables();
        setStatus("Created custom limb " + limb.displayName() + ".");
    }

    private void createCustomGatheringNode() {
        JTextField nameField = new JTextField("Gathering Node", 24);
        JComboBox<MapDesignLibrary.GatheringNodeType> typeBox = new JComboBox<>(
                MapDesignLibrary.GatheringNodeType.values());
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner gatherXpSpinner = new JSpinner(new SpinnerNumberModel(18, 0, 100000, 1));
        JSpinner frameDurationSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 100000, 1));
        JSpinner visualScaleSpinner = new JSpinner(new SpinnerNumberModel(1.35, 0.1, 10.0, 0.05));
        JCheckBox autoMaterialOutputBox = new JCheckBox("Auto-create metal ore from stage 0 image");
        JComboBox<GearMaterial> materialBox = new JComboBox<>(metalMaterials());
        JCheckBox smeltingBox = new JCheckBox("Create smelting recipe");
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(7, 0, 100000, 1));
        JTextField barImageField = new JTextField("assets/images/resourceMaterial/bronze_bar.png", 28);
        JButton barBrowse = new JButton("Browse");
        JTextField frameOneField = new JTextField("assets/images/generic/64x64/A_Rock1_Node1.png", 28);
        JTextField frameTwoField = new JTextField("assets/images/generic/64x64/A_Rock1_Node2.png", 28);
        JTextField frameThreeField = new JTextField("assets/images/generic/64x64/A_Rock1_Node3.png", 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        JTextField modelOneField = new JTextField("", 28);
        JTextField modelTwoField = new JTextField("", 28);
        JTextField modelThreeField = new JTextField("", 28);
        JButton modelOneBrowse = new JButton("Browse");
        JButton modelTwoBrowse = new JButton("Browse");
        JButton modelThreeBrowse = new JButton("Browse");
        JCheckBox lightAttachedBox = new JCheckBox("Attach default light");
        JCheckBox lightEnabledBox = new JCheckBox("Light enabled", true);
        JComboBox<LightPreset> lightPresetBox = new JComboBox<>(LIGHT_PRESETS.toArray(new LightPreset[0]));
        JTextField lightColorField = new JTextField(MapLightingSettings.colorHex(0xFF8B42), 10);
        JSpinner lightRadiusSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 64.0, 0.1));
        JSpinner lightIntensitySpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 8.0, 0.05));
        JSpinner lightOffsetXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -4.0, 4.0, 0.05));
        JSpinner lightOffsetYSpinner = new JSpinner(new SpinnerNumberModel(0.65, -8.0, 8.0, 0.05));
        JSpinner lightOffsetZSpinner = new JSpinner(new SpinnerNumberModel(0.0, -4.0, 4.0, 0.05));
        JSpinner lightFlickerSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.01));
        JButton lootButton = new JButton("Edit Loot Table");
        List<MapDesignLibrary.CustomDropEntry> lootEntries = new ArrayList<>();

        skillBox.setSelectedItem(CharacterSkill.MINING);
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));
        modelOneBrowse.addActionListener(event -> browsePathInto(modelOneField));
        modelTwoBrowse.addActionListener(event -> browsePathInto(modelTwoField));
        modelThreeBrowse.addActionListener(event -> browsePathInto(modelThreeField));
        lightPresetBox.addActionListener(event -> {
            LightPreset preset = (LightPreset) lightPresetBox.getSelectedItem();
            if (preset == null) {
                return;
            }
            lightColorField.setText(MapLightingSettings.colorHex(preset.colorRgb()));
            lightRadiusSpinner.setValue(preset.radius());
            lightIntensitySpinner.setValue(preset.intensity());
            lightOffsetYSpinner.setValue(preset.heightOffset());
            lightFlickerSpinner.setValue(preset.flickerAmount());
        });
        barBrowse.addActionListener(event -> browsePathInto(barImageField));
        lootButton.addActionListener(event -> editGatheringLootEntries(lootEntries));

        JPanel fields = createFormPanel();
        JPanel frameDurationRow = formRow("Frame Duration Ms", frameDurationSpinner);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Type", typeBox));
        fields.add(formRow("Skill", skillBox));
        fields.add(formRow("Required Level", requiredLevelSpinner));
        fields.add(formRow("Gather XP", gatherXpSpinner));
        fields.add(frameDurationRow);
        fields.add(formRow("Visual Scale", visualScaleSpinner));
        fields.add(formRow("Loot Table", lootButton));
        JPanel materialHelperRow = formRow("Metal Helper", autoMaterialOutputBox);
        JPanel materialRow = formRow("Metal", materialBox);
        JPanel smeltingRow = formRow("Smelting", smeltingBox);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        JPanel barIconRow = formRow("Bar Icon", pathFieldPanel(barImageField, barBrowse));
        JPanel frameOneRow = formRow("Stage / Frame 0", pathFieldPanel(frameOneField, frameOneBrowse));
        JPanel frameTwoRow = formRow("Stage / Frame 1", pathFieldPanel(frameTwoField, frameTwoBrowse));
        JPanel frameThreeRow = formRow("Stage / Frame 2", pathFieldPanel(frameThreeField, frameThreeBrowse));
        JPanel modelOneRow = formRow("Stage Model 0", modelPathFieldPanel(modelOneField, modelOneBrowse, "gathering"));
        JPanel modelTwoRow = formRow("Stage Model 1", modelPathFieldPanel(modelTwoField, modelTwoBrowse, "gathering"));
        JPanel modelThreeRow = formRow("Stage Model 2", modelPathFieldPanel(modelThreeField, modelThreeBrowse, "gathering"));
        JPanel lightAttachedRow = formRow("", lightAttachedBox);
        JPanel lightEnabledRow = formRow("", lightEnabledBox);
        JPanel lightPresetRow = formRow("Light Preset", lightPresetBox);
        JPanel lightColorRow = formRow("Light Color", lightColorField);
        JPanel lightRadiusRow = formRow("Light Radius", lightRadiusSpinner);
        JPanel lightIntensityRow = formRow("Light Intensity", lightIntensitySpinner);
        JPanel lightOffsetXRow = formRow("Light Offset X", lightOffsetXSpinner);
        JPanel lightOffsetYRow = formRow("Light Offset Y", lightOffsetYSpinner);
        JPanel lightOffsetZRow = formRow("Light Offset Z", lightOffsetZSpinner);
        JPanel lightFlickerRow = formRow("Flicker", lightFlickerSpinner);
        fields.add(materialHelperRow);
        fields.add(materialRow);
        fields.add(smeltingRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        fields.add(barIconRow);
        fields.add(frameOneRow);
        fields.add(frameTwoRow);
        fields.add(frameThreeRow);
        fields.add(modelOneRow);
        fields.add(modelTwoRow);
        fields.add(modelThreeRow);
        fields.add(lightAttachedRow);
        fields.add(lightEnabledRow);
        fields.add(lightPresetRow);
        fields.add(lightColorRow);
        fields.add(lightRadiusRow);
        fields.add(lightIntensityRow);
        fields.add(lightOffsetXRow);
        fields.add(lightOffsetYRow);
        fields.add(lightOffsetZRow);
        fields.add(lightFlickerRow);

        Runnable updateGatheringNodeFields = () -> {
            MapDesignLibrary.GatheringNodeType type = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            skillBox.setSelectedItem(MapDesignLibrary.defaultGatheringSkill(type));
            boolean fishing = type == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = type == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            boolean tree = type == MapDesignLibrary.GatheringNodeType.TREE;
            setGatheringMaterialModel(materialBox, type);
            frameDurationRow.setVisible(false);
            materialHelperRow.setVisible(mining || tree);
            materialRow.setVisible(mining || tree);
            smeltingRow.setVisible(mining);
            smeltingLevelRow.setVisible(mining && smeltingBox.isSelected());
            smeltingXpRow.setVisible(mining && smeltingBox.isSelected());
            barIconRow.setVisible(mining && smeltingBox.isSelected());
            frameOneRow.setVisible(!fishing);
            frameTwoRow.setVisible(!fishing);
            frameThreeRow.setVisible(!fishing && !tree);
            modelOneRow.setVisible(!fishing);
            modelTwoRow.setVisible(!fishing);
            modelThreeRow.setVisible(!fishing && !tree);
            boolean lightRowsVisible = !fishing && lightAttachedBox.isSelected();
            lightAttachedRow.setVisible(!fishing);
            lightEnabledRow.setVisible(lightRowsVisible);
            lightPresetRow.setVisible(lightRowsVisible);
            lightColorRow.setVisible(lightRowsVisible);
            lightRadiusRow.setVisible(lightRowsVisible);
            lightIntensityRow.setVisible(lightRowsVisible);
            lightOffsetXRow.setVisible(lightRowsVisible);
            lightOffsetYRow.setVisible(lightRowsVisible);
            lightOffsetZRow.setVisible(lightRowsVisible);
            lightFlickerRow.setVisible(lightRowsVisible);
            setFormRowLabel(frameOneRow, tree ? "Full Tree" : "Stage / Frame 0");
            setFormRowLabel(frameTwoRow, tree ? "Stump" : "Stage / Frame 1");
            setFormRowLabel(modelOneRow, tree ? "Full Tree Model" : "Stage Model 0");
            setFormRowLabel(modelTwoRow, tree ? "Stump Model" : "Stage Model 1");
            autoMaterialOutputBox.setText(tree
                    ? "Auto-create wood logs from stage 0 image"
                    : "Auto-create metal ore from stage 0 image");
            setFormRowLabel(materialHelperRow, tree ? "Wood Helper" : "Metal Helper");
            setFormRowLabel(materialRow, tree ? "Wood" : "Metal");
            if (!mining) {
                smeltingBox.setSelected(false);
            }
            frameDurationSpinner.setValue(fishing ? DEFAULT_FISHING_FRAME_DURATION_MS : 1000);
            visualScaleSpinner.setValue(fishing ? 1.0 : 1.35);
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
        smeltingBox.addActionListener(event -> updateGatheringNodeFields.run());
        lightAttachedBox.addActionListener(event -> updateGatheringNodeFields.run());
        updateGatheringNodeFields.run();

        if (showScrollableFormDialog(fields, "Create Gathering Node") != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Gathering node needs a name.");
            return;
        }

        try {
            MapDesignLibrary.GatheringNodeType nodeType = (MapDesignLibrary.GatheringNodeType) typeBox
                    .getSelectedItem();
            boolean fishing = nodeType == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = nodeType == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            boolean tree = nodeType == MapDesignLibrary.GatheringNodeType.TREE;
            List<String> frames = fishing
                    ? defaultFishingFramePaths()
                    : tree
                            ? List.of(
                                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_full",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stump",
                                            "gathering"))
                            : List.of(
                                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2",
                                            "gathering"));
            List<String> models = fishing
                    ? List.of()
                    : tree
                            ? normalizedOptionalPaths(modelOneField.getText(), modelTwoField.getText())
                            : normalizedOptionalPaths(modelOneField.getText(), modelTwoField.getText(), modelThreeField.getText());
            String smeltOutputItemId = "";
            String outputItemId = lootEntries.isEmpty() ? "" : lootEntries.get(0).itemId();

            if ((mining || tree) && autoMaterialOutputBox.isSelected()) {
                GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
                String materialName = material == null ? (tree ? "Wood" : "Metal") : material.getDisplayName();
                String outputName = tree ? materialName + " Logs" : materialName + " Ore";
                outputItemId = findItemIdByDisplayName(outputName);
                if (outputItemId.isBlank()) {
                    outputItemId = nextCustomItemId(outputName);
                    design.customItems().add(new MapDesignLibrary.CustomItem(
                            outputItemId,
                            outputName,
                            InventorySystem.ItemType.MISC,
                            frames.get(0),
                            "",
                            "",
                            WeaponType.NONE,
                            GearMaterial.NONE,
                            0,
                            tree ? 4 : 6,
                            tree
                                    ? "Fresh " + materialName.toLowerCase(java.util.Locale.ROOT)
                                            + " logs. Useful for future crafting."
                                    : "Raw " + materialName.toLowerCase(java.util.Locale.ROOT)
                                            + " ore. Smelt it into a bar at a furnace.",
                            null,
                            tree,
                            false,
                            1,
                            1));
                }
                lootEntries.clear();
                lootEntries.add(new MapDesignLibrary.CustomDropEntry(outputItemId, 1.0));

                if (mining && smeltingBox.isSelected()) {
                    String barName = CraftingSystem.smithingMaterialNameFor(material);
                    if (barName.isBlank()) {
                        barName = materialName + " Bar";
                    }
                    smeltOutputItemId = findItemIdByDisplayName(barName);
                    if (smeltOutputItemId.isBlank()) {
                        smeltOutputItemId = nextCustomItemId(barName);
                        String barImage = normalizeGeneratedImagePath(barImageField.getText(), safeId(barName),
                                "items");
                        design.customItems().add(new MapDesignLibrary.CustomItem(
                                smeltOutputItemId,
                                barName,
                                InventorySystem.ItemType.MISC,
                                barImage,
                                "",
                                "",
                                WeaponType.NONE,
                                GearMaterial.NONE,
                                0,
                                12,
                                "A " + materialName.toLowerCase(java.util.Locale.ROOT) + " bar ready for smithing.",
                                null,
                                false,
                                false,
                                1,
                                1));
                    }
                }
            }

            if (lootEntries.isEmpty()) {
                setStatus("Gathering node needs at least one loot entry or an automatic material output.");
                return;
            }

            MapDesignLibrary.CustomGatheringNode node = new MapDesignLibrary.CustomGatheringNode(
                    nextCustomGatheringNodeId(name),
                    name,
                    nodeType,
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    outputItemId,
                    ((Number) gatherXpSpinner.getValue()).intValue(),
                    mining ? smeltOutputItemId : "",
                    mining ? ((Number) smeltingXpSpinner.getValue()).intValue() : 0,
                    frames,
                    models,
                    fishing ? DEFAULT_FISHING_FRAME_DURATION_MS : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1,
                    !fishing && lightAttachedBox.isSelected()
                            ? lightAttachmentFromControls(
                                    lightEnabledBox,
                                    lightColorField,
                                    lightRadiusSpinner,
                                    lightIntensitySpinner,
                                    lightOffsetXSpinner,
                                    lightOffsetYSpinner,
                                    lightOffsetZSpinner,
                                    lightFlickerSpinner)
                            : null);
            design.customGatheringNodes().add(node);
            persistSharedContent("gathering node");
            populatePlaceables();
            setStatus("Created gathering node " + node.displayName() + ".");
        } catch (IOException exception) {
            setStatus("Gathering node image save failed: " + exception.getMessage());
        }
    }

    private void createCraftingRecipe() {
        MapDesignLibrary.CraftingRecipe recipe = showCraftingRecipeDialog("Create Crafting Recipe", null);
        if (recipe == null) {
            return;
        }
        design.craftingRecipes().add(recipe);
        persistSharedContent("crafting recipe");
        setStatus("Created crafting recipe " + recipe.displayName() + ".");
    }

    private MapDesignLibrary.CraftingRecipe showCraftingRecipeDialog(
            String title,
            MapDesignLibrary.CraftingRecipe existing) {
        JTextField nameField = new JTextField(existing == null ? "Crafting Recipe" : existing.displayName(), 24);
        JComboBox<MapDesignLibrary.CraftingRecipeCategory> categoryBox = new JComboBox<>(
                MapDesignLibrary.CraftingRecipeCategory.values());
        JComboBox<DropItemOption> primaryBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> secondaryBox = new JComboBox<>();
        secondaryBox.addItem(new DropItemOption("", "None (single ingredient)"));
        for (DropItemOption option : gatheringOutputItemOptions()) {
            secondaryBox.addItem(option);
        }
        JComboBox<DropItemOption> outputBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner primaryQuantitySpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : existing.primaryQuantity(), 1, 1000, 1));
        JSpinner secondaryQuantitySpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : Math.max(1, existing.secondaryQuantity()), 1, 1000, 1));
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : existing.requiredLevel(), 1, 100, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 0 : existing.xpReward(), 0, 100000, 1));
        JCheckBox consumePrimaryBox = new JCheckBox("Consume primary", existing == null || existing.consumePrimary());
        JCheckBox consumeSecondaryBox = new JCheckBox("Consume secondary",
                existing == null || existing.consumeSecondary());
        JComboBox<MapDesignLibrary.CraftingOutputType> outputTypeBox = new JComboBox<>(
                MapDesignLibrary.CraftingOutputType.values());
        JComboBox<CraftingStationType> stationBox = new JComboBox<>(CraftingStationType.values());
        JSpinner stationLifetimeSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 300 : Math.max(1, existing.stationLifetimeMs() / 1000),
                1, 86400, 1));
        JCheckBox smeltingBox = new JCheckBox(
                "Output can be smelted",
                existing != null && !existing.smeltOutputItemId().isBlank());
        JComboBox<DropItemOption> smeltOutputBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : existing.smeltRequiredLevel(), 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(
                existing == null ? 0 : existing.smeltXpReward(), 0, 100000, 1));
        skillBox.setSelectedItem(existing == null ? CharacterSkill.CRAFTING : existing.requiredSkill());
        if (existing != null) {
            categoryBox.setSelectedItem(existing.category());
            selectDropItem(primaryBox, existing.primaryItemId());
            selectDropItem(secondaryBox, existing.secondaryItemId());
            selectDropItem(outputBox, existing.outputItemId());
            selectDropItem(smeltOutputBox, existing.smeltOutputItemId());
            outputTypeBox.setSelectedItem(existing.outputType());
            if (existing.outputStationType() != null) {
                stationBox.setSelectedItem(existing.outputStationType());
            }
        }

        JPanel fields = createFormPanel();
        JPanel secondaryQuantityRow = formRow("Secondary Quantity", secondaryQuantitySpinner);
        JPanel consumeSecondaryRow = formRow("Secondary", consumeSecondaryBox);
        JPanel outputItemRow = formRow("Output Item", outputBox);
        JPanel stationRow = formRow("Output Station", stationBox);
        JPanel stationLifetimeRow = formRow("Lifetime Seconds", stationLifetimeSpinner);
        JPanel smeltingRow = formRow("Smelting", smeltingBox);
        JPanel smeltOutputRow = formRow("Smelt Output", smeltOutputBox);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Category", categoryBox));
        fields.add(formRow("Primary Item", primaryBox));
        fields.add(formRow("Primary Quantity", primaryQuantitySpinner));
        fields.add(formRow("Secondary Item", secondaryBox));
        fields.add(secondaryQuantityRow);
        fields.add(formRow("Output Type", outputTypeBox));
        fields.add(outputItemRow);
        fields.add(stationRow);
        fields.add(stationLifetimeRow);
        fields.add(formRow("Skill", skillBox));
        fields.add(formRow("Required Level", requiredLevelSpinner));
        fields.add(formRow("XP Reward", xpSpinner));
        fields.add(formRow("Primary", consumePrimaryBox));
        fields.add(consumeSecondaryRow);
        fields.add(smeltingRow);
        fields.add(smeltOutputRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        Runnable updateRows = () -> {
            DropItemOption secondary = (DropItemOption) secondaryBox.getSelectedItem();
            boolean hasSecondary = secondary != null && !secondary.itemId().isBlank();
            boolean stationOutput = outputTypeBox
                    .getSelectedItem() == MapDesignLibrary.CraftingOutputType.CRAFTING_STATION;
            boolean smelting = !stationOutput && smeltingBox.isSelected();
            secondaryQuantityRow.setVisible(hasSecondary);
            consumeSecondaryRow.setVisible(hasSecondary);
            outputItemRow.setVisible(!stationOutput);
            stationRow.setVisible(stationOutput);
            stationLifetimeRow.setVisible(stationOutput);
            smeltingRow.setVisible(!stationOutput);
            smeltOutputRow.setVisible(smelting);
            smeltingLevelRow.setVisible(smelting);
            smeltingXpRow.setVisible(smelting);
            fields.revalidate();
            fields.repaint();
        };
        smeltingBox.addActionListener(event -> updateRows.run());
        secondaryBox.addActionListener(event -> updateRows.run());
        outputTypeBox.addActionListener(event -> updateRows.run());
        updateRows.run();

        if (showScrollableFormDialog(fields, title) != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        DropItemOption primary = (DropItemOption) primaryBox.getSelectedItem();
        DropItemOption secondary = (DropItemOption) secondaryBox.getSelectedItem();
        DropItemOption output = (DropItemOption) outputBox.getSelectedItem();
        MapDesignLibrary.CraftingOutputType outputType = (MapDesignLibrary.CraftingOutputType) outputTypeBox
                .getSelectedItem();
        if (name.isBlank() || primary == null || primary.itemId().isBlank()) {
            setStatus("Crafting recipe needs a name and primary ingredient.");
            return null;
        }
        boolean hasSecondary = secondary != null && !secondary.itemId().isBlank();
        if (hasSecondary && primary.itemId().equalsIgnoreCase(secondary.itemId())) {
            setStatus("Use one ingredient entry with a larger quantity instead of duplicate ingredients.");
            return null;
        }
        if (outputType == MapDesignLibrary.CraftingOutputType.ITEM && output == null) {
            setStatus("Choose an authored output item.");
            return null;
        }

        return new MapDesignLibrary.CraftingRecipe(
                existing == null ? nextCraftingRecipeId(name) : existing.recipeId(),
                name,
                (MapDesignLibrary.CraftingRecipeCategory) categoryBox.getSelectedItem(),
                primary.itemId(),
                hasSecondary ? secondary.itemId() : "",
                outputType == MapDesignLibrary.CraftingOutputType.ITEM ? output.itemId() : "",
                (CharacterSkill) skillBox.getSelectedItem(),
                ((Number) requiredLevelSpinner.getValue()).intValue(),
                ((Number) xpSpinner.getValue()).intValue(),
                consumePrimaryBox.isSelected(),
                hasSecondary && consumeSecondaryBox.isSelected(),
                outputType == MapDesignLibrary.CraftingOutputType.ITEM
                        && smeltingBox.isSelected()
                        && smeltOutputBox.getSelectedItem() instanceof DropItemOption smeltOutput
                                ? smeltOutput.itemId()
                                : "",
                outputType == MapDesignLibrary.CraftingOutputType.ITEM && smeltingBox.isSelected()
                        ? ((Number) smeltingLevelSpinner.getValue()).intValue()
                        : 1,
                outputType == MapDesignLibrary.CraftingOutputType.ITEM && smeltingBox.isSelected()
                        ? ((Number) smeltingXpSpinner.getValue()).intValue()
                        : 0,
                ((Number) primaryQuantitySpinner.getValue()).intValue(),
                hasSecondary ? ((Number) secondaryQuantitySpinner.getValue()).intValue() : 0,
                outputType,
                outputType == MapDesignLibrary.CraftingOutputType.CRAFTING_STATION
                        ? (CraftingStationType) stationBox.getSelectedItem()
                        : null,
                outputType == MapDesignLibrary.CraftingOutputType.CRAFTING_STATION
                        ? ((Number) stationLifetimeSpinner.getValue()).intValue() * 1000
                        : 0);
    }

    private void createCookingRecipe() {
        MapDesignLibrary.CustomCookingRecipe recipe = showCookingRecipeDialog("Create Cooking Recipe", null);
        if (recipe == null) {
            return;
        }

        design.customCookingRecipes().add(recipe);
        persistSharedContent("cooking recipe");
        setStatus("Created cooking recipe " + recipe.displayName() + ".");
    }

    private MapDesignLibrary.CustomCookingRecipe showCookingRecipeDialog(
            String title,
            MapDesignLibrary.CustomCookingRecipe existing) {
        JComboBox<DropItemOption> rawBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> cookedBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> burntBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JTextField nameField = new JTextField(existing == null ? "Cooking Recipe" : existing.displayName(), 24);
        JSpinner requiredLevelSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 1 : existing.requiredLevel(), 1, 100, 1));
        JSpinner xpSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 20 : existing.xpReward(), 0, 100000, 1));
        JCheckBox autoCookedBox = new JCheckBox("Auto-generate cooked item", existing == null);
        JSpinner cookedHealSpinner = new JSpinner(new SpinnerNumberModel(6, 0, 1000, 1));
        JCheckBox autoBurntBox = new JCheckBox("Auto-generate burnt item", existing == null);

        if (existing != null) {
            selectDropItem(rawBox, existing.rawItemId());
            selectDropItem(cookedBox, existing.cookedItemId());
            selectDropItem(burntBox, existing.burntItemId());
        }

        JPanel fields = createFormPanel();
        JPanel cookedRow = formRow("Cooked Item", cookedBox);
        JPanel cookedHealRow = formRow("Cooked Heal Amount", cookedHealSpinner);
        JPanel burntRow = formRow("Burnt Item", burntBox);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Raw Item", rawBox));
        fields.add(formRow("Cooked Helper", autoCookedBox));
        fields.add(cookedRow);
        fields.add(cookedHealRow);
        fields.add(formRow("Burnt Helper", autoBurntBox));
        fields.add(burntRow);
        fields.add(formRow("Cooking Level", requiredLevelSpinner));
        fields.add(formRow("Cooking XP", xpSpinner));

        Runnable updateOutputRows = () -> {
            cookedRow.setVisible(!autoCookedBox.isSelected());
            cookedHealRow.setVisible(autoCookedBox.isSelected());
            burntRow.setVisible(!autoBurntBox.isSelected());
            fields.revalidate();
            fields.repaint();
        };
        autoCookedBox.addActionListener(event -> updateOutputRows.run());
        autoBurntBox.addActionListener(event -> updateOutputRows.run());
        updateOutputRows.run();

        int result = showScrollableFormDialog(fields, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        DropItemOption raw = (DropItemOption) rawBox.getSelectedItem();
        DropItemOption cooked = (DropItemOption) cookedBox.getSelectedItem();
        DropItemOption burnt = (DropItemOption) burntBox.getSelectedItem();
        if (name.isBlank() || raw == null || (!autoCookedBox.isSelected() && cooked == null)) {
            setStatus("Cooking recipe needs a name, raw item, and cooked item.");
            return null;
        }

        String cookedItemId = autoCookedBox.isSelected()
                ? ensureCookedFoodItem(raw, ((Number) cookedHealSpinner.getValue()).intValue())
                : cooked.itemId();
        if (cookedItemId.isBlank()) {
            setStatus("Cooking recipe needs a cooked item.");
            return null;
        }

        String burntItemId = autoBurntBox.isSelected()
                ? ensureBurntFoodItem(raw, cookedItemId)
                : burnt == null ? "" : burnt.itemId();
        if (burntItemId.isBlank()) {
            setStatus("Cooking recipe needs a burnt item.");
            return null;
        }

        return new MapDesignLibrary.CustomCookingRecipe(
                existing == null ? nextCookingRecipeId(name) : existing.recipeId(),
                name,
                raw.itemId(),
                cookedItemId,
                burntItemId,
                ((Number) requiredLevelSpinner.getValue()).intValue(),
                ((Number) xpSpinner.getValue()).intValue());
    }

    private String ensureCookedFoodItem(DropItemOption raw, int healAmount) {
        if (raw == null) {
            return "";
        }
        String cookedName = "Cooked " + raw.displayName();
        String existingId = findItemIdByDisplayName(cookedName);
        if (!existingId.isBlank()) {
            return existingId;
        }

        int safeHealAmount = Math.max(0, healAmount);
        String iconPath = createCookedFoodIconPath(raw);
        String itemId = nextCustomItemId(cookedName);
        design.customItems().add(new MapDesignLibrary.CustomItem(
                itemId,
                cookedName,
                InventorySystem.ItemType.CONSUMABLE,
                iconPath,
                "",
                "",
                WeaponType.NONE,
                GearMaterial.NONE,
                safeHealAmount,
                Math.max(1, safeHealAmount + 2),
                "A cooked version of " + raw.displayName().toLowerCase(java.util.Locale.ROOT)
                        + ". Restores " + safeHealAmount + " HP.",
                null,
                false,
                false,
                1,
                1));
        return itemId;
    }

    private String ensureBurntFoodItem(DropItemOption raw, String cookedItemId) {
        if (raw == null) {
            return "";
        }
        String burntName = "Burnt " + raw.displayName();
        String existingId = findItemIdByDisplayName(burntName);
        if (!existingId.isBlank()) {
            return existingId;
        }

        String iconPath = createBurntFoodIconPath(raw, cookedItemId);
        String itemId = nextCustomItemId(burntName);
        design.customItems().add(new MapDesignLibrary.CustomItem(
                itemId,
                burntName,
                InventorySystem.ItemType.MISC,
                iconPath,
                "",
                "",
                WeaponType.NONE,
                GearMaterial.NONE,
                0,
                1,
                "A blackened version of " + raw.displayName().toLowerCase(java.util.Locale.ROOT)
                        + ". It is not worth eating.",
                null,
                false,
                false,
                1,
                1));
        return itemId;
    }

    private String createCookedFoodIconPath(DropItemOption raw) {
        String sourcePath = itemIconPathForOption(raw);
        if (sourcePath.isBlank()) {
            return "";
        }

        try {
            BufferedImage source = AssetLoader.loadImage(sourcePath);
            BufferedImage cooked = applyCookedTint(source);
            Path targetFolder = Path.of("src", "main", "resources", "assets", "images", "generated", "items");
            Files.createDirectories(targetFolder);
            String fileName = safeId("cooked_" + raw.displayName()) + ".png";
            Path target = targetFolder.resolve(fileName);
            ImageIO.write(cooked, "png", target.toFile());
            return "assets/images/generated/items/" + fileName;
        } catch (Exception exception) {
            return sourcePath;
        }
    }

    private String createBurntFoodIconPath(DropItemOption raw, String cookedItemId) {
        String sourcePath = itemIconPathForId(cookedItemId);
        if (sourcePath.isBlank()) {
            sourcePath = itemIconPathForOption(raw);
        }
        if (sourcePath.isBlank()) {
            return "";
        }

        try {
            BufferedImage source = AssetLoader.loadImage(sourcePath);
            BufferedImage burnt = InventorySystem.Item.applyBurntTint(source);
            Path targetFolder = Path.of("src", "main", "resources", "assets", "images", "generated", "items");
            Files.createDirectories(targetFolder);
            String fileName = safeId("burnt_" + raw.displayName()) + ".png";
            Path target = targetFolder.resolve(fileName);
            ImageIO.write(burnt, "png", target.toFile());
            return "assets/images/generated/items/" + fileName;
        } catch (Exception exception) {
            return sourcePath;
        }
    }

    private BufferedImage applyCookedTint(BufferedImage source) {
        if (source == null) {
            return null;
        }

        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tinted.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.setComposite(java.awt.AlphaComposite.SrcAtop.derive(0.34f));
        graphics.setColor(new Color(166, 104, 46));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        return tinted;
    }

    private void createCustomMiningRock() {
        JTextField nameField = new JTextField("Copper Rock", 24);
        JComboBox<GearMaterial> materialBox = new JComboBox<>(metalMaterials());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner miningXpSpinner = new JSpinner(new SpinnerNumberModel(18, 0, 100000, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(7, 0, 100000, 1));
        JTextField frameOneField = new JTextField("assets/images/generic/64x64/A_Rock1_Node1.png", 28);
        JTextField frameTwoField = new JTextField("assets/images/generic/64x64/A_Rock1_Node2.png", 28);
        JTextField frameThreeField = new JTextField("assets/images/generic/64x64/A_Rock1_Node3.png", 28);
        JTextField barImageField = new JTextField("assets/images/resourceMaterial/bronze_bar.png", 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        JButton barBrowse = new JButton("Browse");
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));
        barBrowse.addActionListener(event -> browsePathInto(barImageField));

        JPanel fields = createFormPanel();
        addFormRow(fields, "Name", nameField);
        addFormRow(fields, "Metal", materialBox);
        addFormRow(fields, "Mining Level", requiredLevelSpinner);
        addFormRow(fields, "Mining XP", miningXpSpinner);
        addFormRow(fields, "Smelting XP", smeltingXpSpinner);
        addFormRow(fields, "Rock Stage 0 / Ore Icon", pathFieldPanel(frameOneField, frameOneBrowse));
        addFormRow(fields, "Rock Stage 1", pathFieldPanel(frameTwoField, frameTwoBrowse));
        addFormRow(fields, "Rock Stage 2", pathFieldPanel(frameThreeField, frameThreeBrowse));
        addFormRow(fields, "Bar Icon", pathFieldPanel(barImageField, barBrowse));

        if (showScrollableFormDialog(fields, "Create Mining Rock") != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Mining rock needs a name.");
            return;
        }

        GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
        String metalName = material == null ? "Metal" : material.getDisplayName();
        String oreName = metalName + " Ore";
        String barName = CraftingSystem.smithingMaterialNameFor(material);
        if (barName.isBlank()) {
            barName = metalName + " Bar";
        }

        try {
            List<String> frames = List.of(
                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering"));
            String oreItemId = findItemIdByDisplayName(oreName);
            if (oreItemId.isBlank()) {
                oreItemId = nextCustomItemId(oreName);
                design.customItems().add(new MapDesignLibrary.CustomItem(
                        oreItemId,
                        oreName,
                        InventorySystem.ItemType.MISC,
                        frames.get(0),
                        "",
                        "",
                        WeaponType.NONE,
                        GearMaterial.NONE,
                        0,
                        6,
                        "Raw " + metalName.toLowerCase(java.util.Locale.ROOT)
                                + " ore. Smelt it into a bar at a furnace.",
                        null,
                        false,
                        false,
                        1,
                        1));
            }

            String barItemId = findItemIdByDisplayName(barName);
            if (barItemId.isBlank()) {
                barItemId = nextCustomItemId(barName);
                String barImage = normalizeGeneratedImagePath(barImageField.getText(), safeId(barName), "items");
                design.customItems().add(new MapDesignLibrary.CustomItem(
                        barItemId,
                        barName,
                        InventorySystem.ItemType.MISC,
                        barImage,
                        "",
                        "",
                        WeaponType.NONE,
                        GearMaterial.NONE,
                        0,
                        12,
                        "A " + metalName.toLowerCase(java.util.Locale.ROOT) + " bar ready for smithing.",
                        null,
                        false,
                        false,
                        1,
                        1));
            }

            MapDesignLibrary.CustomGatheringNode node = new MapDesignLibrary.CustomGatheringNode(
                    nextCustomGatheringNodeId(name),
                    name,
                    MapDesignLibrary.GatheringNodeType.MINING_ROCK,
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    oreItemId,
                    ((Number) miningXpSpinner.getValue()).intValue(),
                    barItemId,
                    ((Number) smeltingXpSpinner.getValue()).intValue(),
                    frames,
                    1000,
                    1.35);
            design.customGatheringNodes().add(node);
            persistSharedContent("mining rock");
            populatePlaceables();
            setStatus(
                    "Created mining rock " + node.displayName() + " and generated " + oreName + " / " + barName + ".");
        } catch (IOException exception) {
            setStatus("Mining rock image save failed: " + exception.getMessage());
        }
    }

    private void createCustomFishingSpot() {
        JTextField nameField = new JTextField("Fishing Spot", 24);
        JComboBox<DropItemOption> outputBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner fishingXpSpinner = new JSpinner(new SpinnerNumberModel(18, 0, 100000, 1));
        JTextField frameOneField = new JTextField(
                "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance1.png", 28);
        JTextField frameTwoField = new JTextField(
                "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance2.png", 28);
        JTextField frameThreeField = new JTextField(
                "assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance3.png", 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));

        JPanel fields = createFormPanel();
        addFormRow(fields, "Name", nameField);
        addFormRow(fields, "Output Item", outputBox);
        addFormRow(fields, "Fishing Level", requiredLevelSpinner);
        addFormRow(fields, "Fishing XP", fishingXpSpinner);
        addFormRow(fields, "Frame 1", pathFieldPanel(frameOneField, frameOneBrowse));
        addFormRow(fields, "Frame 2", pathFieldPanel(frameTwoField, frameTwoBrowse));
        addFormRow(fields, "Frame 3", pathFieldPanel(frameThreeField, frameThreeBrowse));

        if (showScrollableFormDialog(fields, "Create Fishing Spot") != JOptionPane.OK_OPTION) {
            return;
        }

        DropItemOption output = (DropItemOption) outputBox.getSelectedItem();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank() || output == null) {
            setStatus("Fishing spot needs a name and output item.");
            return;
        }

        try {
            List<String> frames = List.of(
                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_frame_1", "gathering"),
                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_frame_2", "gathering"),
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_frame_3", "gathering"));
            MapDesignLibrary.CustomGatheringNode node = new MapDesignLibrary.CustomGatheringNode(
                    nextCustomGatheringNodeId(name),
                    name,
                    MapDesignLibrary.GatheringNodeType.FISHING_SPOT,
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    output.itemId(),
                    ((Number) fishingXpSpinner.getValue()).intValue(),
                    "",
                    0,
                    frames,
                    260,
                    1.0);
            design.customGatheringNodes().add(node);
            persistSharedContent("fishing spot");
            populatePlaceables();
            setStatus("Created fishing spot " + node.displayName() + ".");
        } catch (IOException exception) {
            setStatus("Fishing spot image save failed: " + exception.getMessage());
        }
    }

    private void editCustomItem(MapDesignLibrary.CustomItem selected) {
        pendingItemFirstPersonProfile = null;
        MapDesignLibrary.CustomItem edited = showCustomItemDialog("Edit Item", selected);
        if (edited == null) {
            return;
        }

        int index = design.customItems().indexOf(selected);
        if (index >= 0) {
            design.customItems().set(index, edited);
            if (persistSharedContent("custom item") && pendingItemFirstPersonProfile != null) {
                persistItemFirstPersonProfile(pendingItemFirstPersonProfile);
            }
            pendingItemFirstPersonProfile = null;
            populatePlaceables();
            setStatus("Updated custom item " + edited.displayName() + ".");
        }
    }

    private void deleteCustomItem(MapDesignLibrary.CustomItem selected) {
        List<String> linkedEnemies = linkedEnemyProductOwners(selected.itemId());
        if (!linkedEnemies.isEmpty()) {
            showAdaptiveTextMessageDialog(
                    "This item cannot be deleted while it is linked as a butchery product by:\n- "
                            + String.join("\n- ", linkedEnemies),
                    "Linked Butchery Product",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!confirmDelete("item", selected.displayName(), ContentCategory.ITEMS, selected.itemId(), selected)) {
            return;
        }

        design.customItems().remove(selected);
        if (persistSharedContent("custom item")) {
            removeItemFirstPersonProfile(selected.itemId());
        }
        populatePlaceables();
        setStatus("Deleted custom item " + selected.displayName() + ".");
    }

    private void editCustomLimb(MapDesignLibrary.CustomLimb selected) {
        MapDesignLibrary.CustomLimb edited = showCustomLimbDialog(
                "Edit Limb",
                selected.limbId(),
                selected.displayName(),
                selected.limbSlot(),
                selected.iconPath(),
                selected.description(),
                selected.sourceCreatureId(),
                selected.paperDollSourcePath(),
                selected.statBonuses(),
                selected.skillIds(),
                selected.firstPersonModelPath(),
                selected.firstPersonRigId(),
                selected.paperDollDerivedIcon(),
                selected.baseGoldValue());
        if (edited == null) {
            return;
        }

        int index = design.customLimbs().indexOf(selected);
        if (index >= 0) {
            design.customLimbs().set(index, edited);
            persistSharedContent("custom limb");
            populatePlaceables();
            setStatus("Updated custom limb " + edited.displayName() + ".");
        }
    }

    private void deleteCustomLimb(MapDesignLibrary.CustomLimb selected) {
        List<String> linkedEnemies = linkedEnemyProductOwners(selected.limbId());
        if (!linkedEnemies.isEmpty()) {
            showAdaptiveTextMessageDialog(
                    "This limb cannot be deleted while it is linked as a butchery product by:\n- "
                            + String.join("\n- ", linkedEnemies),
                    "Linked Butchery Product",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!confirmDelete("limb", selected.displayName(), ContentCategory.LIMBS, selected.limbId(), selected)) {
            return;
        }

        design.customLimbs().remove(selected);
        persistSharedContent("custom limb");
        populatePlaceables();
        setStatus("Deleted custom limb " + selected.displayName() + ".");
    }

    private List<String> linkedEnemyProductOwners(String productId) {
        if (productId == null || productId.isBlank()) {
            return List.of();
        }
        return design.customMobs().stream()
                .filter(mob -> productId.equals(mob.butcheryProfile().leatherItemId())
                        || mob.butcheryProfile().limbProductIds().containsValue(productId))
                .map(mob -> mob.displayName() + " [" + mob.mobId() + "]")
                .toList();
    }

    private void deleteCustomGatheringNode(MapDesignLibrary.CustomGatheringNode selected) {
        if (!confirmDelete("gathering node", selected.displayName(), ContentCategory.GATHERING, selected.nodeId(),
                selected)) {
            return;
        }

        design.customGatheringNodes().remove(selected);
        design.placements().removeIf(placement -> placement.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE
                && (selected.nodeId().equals(placement.id()) || selected.interactionId().equals(placement.id())));
        design.placedObjects().removeIf(object -> object.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE
                && (selected.nodeId().equals(object.id()) || selected.interactionId().equals(object.id())));
        persistSharedContent("gathering node");
        populatePlaceables();
        setStatus("Deleted gathering node " + selected.displayName() + ".");
    }

    private void editCustomGatheringNode(MapDesignLibrary.CustomGatheringNode selected) {
        JTextField nameField = new JTextField(selected.displayName(), 24);
        JComboBox<MapDesignLibrary.GatheringNodeType> typeBox = new JComboBox<>(
                MapDesignLibrary.GatheringNodeType.values());
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.requiredLevel(), 1, 100, 1));
        JSpinner gatherXpSpinner = new JSpinner(new SpinnerNumberModel(selected.gatherXpReward(), 0, 100000, 1));
        JSpinner frameDurationSpinner = new JSpinner(new SpinnerNumberModel(selected.frameDurationMs(), 1, 100000, 1));
        JSpinner visualScaleSpinner = new JSpinner(new SpinnerNumberModel(selected.visualScale(), 0.1, 10.0, 0.05));
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltRequiredLevel(), 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltXpReward(), 0, 100000, 1));
        JCheckBox smeltingBox = new JCheckBox("Output can be smelted", !selected.smeltOutputItemId().isBlank());
        JComboBox<DropItemOption> smeltOutputBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        if (!selectDropItem(smeltOutputBox, selected.smeltOutputItemId())
                && !selected.smeltOutputItemId().isBlank()) {
            DropItemOption unavailableOutput = new DropItemOption(
                    selected.smeltOutputItemId(),
                    "Current / Missing Item");
            smeltOutputBox.addItem(unavailableOutput);
            smeltOutputBox.setSelectedItem(unavailableOutput);
        }
        JTextField frameOneField = new JTextField(framePathAt(selected, 0), 28);
        JTextField frameTwoField = new JTextField(framePathAt(selected, 1), 28);
        JTextField frameThreeField = new JTextField(framePathAt(selected, 2), 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        JTextField modelOneField = new JTextField(modelPathAt(selected, 0), 28);
        JTextField modelTwoField = new JTextField(modelPathAt(selected, 1), 28);
        JTextField modelThreeField = new JTextField(modelPathAt(selected, 2), 28);
        JButton modelOneBrowse = new JButton("Browse");
        JButton modelTwoBrowse = new JButton("Browse");
        JButton modelThreeBrowse = new JButton("Browse");
        MapDesignLibrary.LightAttachment selectedLight = selected.lightAttachment();
        JCheckBox lightAttachedBox = new JCheckBox("Attach default light", selectedLight != null);
        JCheckBox lightEnabledBox = new JCheckBox("Light enabled", selectedLight == null || selectedLight.enabled());
        JComboBox<LightPreset> lightPresetBox = new JComboBox<>(LIGHT_PRESETS.toArray(new LightPreset[0]));
        JTextField lightColorField = new JTextField(
                selectedLight == null ? MapLightingSettings.colorHex(0xFF8B42) : MapLightingSettings.colorHex(selectedLight.colorRgb()),
                10);
        JSpinner lightRadiusSpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 5.0 : selectedLight.radius(),
                0.1,
                64.0,
                0.1));
        JSpinner lightIntensitySpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 1.0 : selectedLight.intensity(),
                0.0,
                8.0,
                0.05));
        JSpinner lightOffsetXSpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 0.0 : selectedLight.offsetX(),
                -4.0,
                4.0,
                0.05));
        JSpinner lightOffsetYSpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 0.65 : selectedLight.offsetY(),
                -8.0,
                8.0,
                0.05));
        JSpinner lightOffsetZSpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 0.0 : selectedLight.offsetZ(),
                -4.0,
                4.0,
                0.05));
        JSpinner lightFlickerSpinner = new JSpinner(new SpinnerNumberModel(
                selectedLight == null ? 0.0 : selectedLight.flickerAmount(),
                0.0,
                1.0,
                0.01));
        JButton lootButton = new JButton("Edit Loot Table");
        List<MapDesignLibrary.CustomDropEntry> lootEntries = new ArrayList<>(selected.lootEntries());

        typeBox.setSelectedItem(selected.nodeType());
        skillBox.setSelectedItem(selected.gatheringSkill());
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));
        modelOneBrowse.addActionListener(event -> browsePathInto(modelOneField));
        modelTwoBrowse.addActionListener(event -> browsePathInto(modelTwoField));
        modelThreeBrowse.addActionListener(event -> browsePathInto(modelThreeField));
        lightPresetBox.addActionListener(event -> {
            LightPreset preset = (LightPreset) lightPresetBox.getSelectedItem();
            if (preset == null) {
                return;
            }
            lightColorField.setText(MapLightingSettings.colorHex(preset.colorRgb()));
            lightRadiusSpinner.setValue(preset.radius());
            lightIntensitySpinner.setValue(preset.intensity());
            lightOffsetYSpinner.setValue(preset.heightOffset());
            lightFlickerSpinner.setValue(preset.flickerAmount());
        });
        lootButton.addActionListener(event -> editGatheringLootEntries(lootEntries));

        JPanel fields = createFormPanel();
        JPanel frameDurationRow = formRow("Frame Duration Ms", frameDurationSpinner);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Type", typeBox));
        fields.add(formRow("Skill", skillBox));
        fields.add(formRow("Required Level", requiredLevelSpinner));
        fields.add(formRow("Gather XP", gatherXpSpinner));
        fields.add(frameDurationRow);
        fields.add(formRow("Visual Scale", visualScaleSpinner));
        fields.add(formRow("Loot Table", lootButton));
        JPanel smeltingRow = formRow("Smelting", smeltingBox);
        JPanel smeltOutputRow = formRow("Smelt Output", smeltOutputBox);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        JPanel frameOneRow = formRow("Stage / Frame 0", pathFieldPanel(frameOneField, frameOneBrowse));
        JPanel frameTwoRow = formRow("Stage / Frame 1", pathFieldPanel(frameTwoField, frameTwoBrowse));
        JPanel frameThreeRow = formRow("Stage / Frame 2", pathFieldPanel(frameThreeField, frameThreeBrowse));
        JPanel modelOneRow = formRow("Stage Model 0", modelPathFieldPanel(modelOneField, modelOneBrowse, "gathering"));
        JPanel modelTwoRow = formRow("Stage Model 1", modelPathFieldPanel(modelTwoField, modelTwoBrowse, "gathering"));
        JPanel modelThreeRow = formRow("Stage Model 2", modelPathFieldPanel(modelThreeField, modelThreeBrowse, "gathering"));
        JPanel lightAttachedRow = formRow("", lightAttachedBox);
        JPanel lightEnabledRow = formRow("", lightEnabledBox);
        JPanel lightPresetRow = formRow("Light Preset", lightPresetBox);
        JPanel lightColorRow = formRow("Light Color", lightColorField);
        JPanel lightRadiusRow = formRow("Light Radius", lightRadiusSpinner);
        JPanel lightIntensityRow = formRow("Light Intensity", lightIntensitySpinner);
        JPanel lightOffsetXRow = formRow("Light Offset X", lightOffsetXSpinner);
        JPanel lightOffsetYRow = formRow("Light Offset Y", lightOffsetYSpinner);
        JPanel lightOffsetZRow = formRow("Light Offset Z", lightOffsetZSpinner);
        JPanel lightFlickerRow = formRow("Flicker", lightFlickerSpinner);
        fields.add(smeltingRow);
        fields.add(smeltOutputRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        fields.add(frameOneRow);
        fields.add(frameTwoRow);
        fields.add(frameThreeRow);
        fields.add(modelOneRow);
        fields.add(modelTwoRow);
        fields.add(modelThreeRow);
        fields.add(lightAttachedRow);
        fields.add(lightEnabledRow);
        fields.add(lightPresetRow);
        fields.add(lightColorRow);
        fields.add(lightRadiusRow);
        fields.add(lightIntensityRow);
        fields.add(lightOffsetXRow);
        fields.add(lightOffsetYRow);
        fields.add(lightOffsetZRow);
        fields.add(lightFlickerRow);

        Runnable updateGatheringNodeFields = () -> {
            MapDesignLibrary.GatheringNodeType type = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            boolean fishing = type == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = type == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            boolean tree = type == MapDesignLibrary.GatheringNodeType.TREE;
            frameDurationRow.setVisible(false);
            smeltingRow.setVisible(mining);
            smeltOutputRow.setVisible(mining && smeltingBox.isSelected());
            smeltingLevelRow.setVisible(mining && smeltingBox.isSelected());
            smeltingXpRow.setVisible(mining && smeltingBox.isSelected());
            frameOneRow.setVisible(!fishing);
            frameTwoRow.setVisible(!fishing);
            frameThreeRow.setVisible(!fishing && !tree);
            modelOneRow.setVisible(!fishing);
            modelTwoRow.setVisible(!fishing);
            modelThreeRow.setVisible(!fishing && !tree);
            boolean lightRowsVisible = !fishing && lightAttachedBox.isSelected();
            lightAttachedRow.setVisible(!fishing);
            lightEnabledRow.setVisible(lightRowsVisible);
            lightPresetRow.setVisible(lightRowsVisible);
            lightColorRow.setVisible(lightRowsVisible);
            lightRadiusRow.setVisible(lightRowsVisible);
            lightIntensityRow.setVisible(lightRowsVisible);
            lightOffsetXRow.setVisible(lightRowsVisible);
            lightOffsetYRow.setVisible(lightRowsVisible);
            lightOffsetZRow.setVisible(lightRowsVisible);
            lightFlickerRow.setVisible(lightRowsVisible);
            setFormRowLabel(frameOneRow, tree ? "Full Tree" : "Stage / Frame 0");
            setFormRowLabel(frameTwoRow, tree ? "Stump" : "Stage / Frame 1");
            setFormRowLabel(modelOneRow, tree ? "Full Tree Model" : "Stage Model 0");
            setFormRowLabel(modelTwoRow, tree ? "Stump Model" : "Stage Model 1");
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
        smeltingBox.addActionListener(event -> updateGatheringNodeFields.run());
        lightAttachedBox.addActionListener(event -> updateGatheringNodeFields.run());
        updateGatheringNodeFields.run();

        if (showScrollableFormDialog(fields, "Edit Gathering Node") != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Gathering node needs a name.");
            return;
        }
        if (lootEntries.isEmpty()) {
            setStatus("Gathering node needs at least one loot entry.");
            return;
        }

        try {
            MapDesignLibrary.GatheringNodeType nodeType = (MapDesignLibrary.GatheringNodeType) typeBox
                    .getSelectedItem();
            boolean fishing = nodeType == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = nodeType == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            boolean tree = nodeType == MapDesignLibrary.GatheringNodeType.TREE;
            DropItemOption smeltOutput = (DropItemOption) smeltOutputBox.getSelectedItem();
            if (mining && smeltingBox.isSelected() && smeltOutput == null) {
                setStatus("Choose an authored item for the smelting output.");
                return;
            }
            List<String> frames = fishing
                    ? defaultFishingFramePaths()
                    : tree
                            ? List.of(
                                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_full",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stump",
                                            "gathering"))
                            : List.of(
                                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1",
                                            "gathering"),
                                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2",
                                            "gathering"));
            List<String> models = fishing
                    ? List.of()
                    : tree
                            ? normalizedOptionalPaths(modelOneField.getText(), modelTwoField.getText())
                            : normalizedOptionalPaths(modelOneField.getText(), modelTwoField.getText(), modelThreeField.getText());
            MapDesignLibrary.CustomGatheringNode edited = new MapDesignLibrary.CustomGatheringNode(
                    selected.nodeId(),
                    name,
                    nodeType,
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    lootEntries.get(0).itemId(),
                    ((Number) gatherXpSpinner.getValue()).intValue(),
                    mining && smeltingBox.isSelected() ? smeltOutput.itemId() : "",
                    mining && smeltingBox.isSelected() ? ((Number) smeltingXpSpinner.getValue()).intValue() : 0,
                    frames,
                    models,
                    fishing ? DEFAULT_FISHING_FRAME_DURATION_MS : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining && smeltingBox.isSelected()
                            ? ((Number) smeltingLevelSpinner.getValue()).intValue()
                            : 1,
                    !fishing && lightAttachedBox.isSelected()
                            ? lightAttachmentFromControls(
                                    lightEnabledBox,
                                    lightColorField,
                                    lightRadiusSpinner,
                                    lightIntensitySpinner,
                                    lightOffsetXSpinner,
                                    lightOffsetYSpinner,
                                    lightOffsetZSpinner,
                                    lightFlickerSpinner)
                            : null);
            int index = design.customGatheringNodes().indexOf(selected);
            if (index >= 0) {
                design.customGatheringNodes().set(index, edited);
                persistSharedContent("gathering node");
                populatePlaceables();
                setStatus("Updated gathering node " + edited.displayName() + ".");
            }
        } catch (IOException exception) {
            setStatus("Gathering node image save failed: " + exception.getMessage());
        }
    }

    private void deleteCraftingRecipe(MapDesignLibrary.CraftingRecipe selected) {
        if (!confirmDelete("crafting recipe", selected.displayName(), ContentCategory.CRAFTING_RECIPES,
                selected.recipeId(), selected)) {
            return;
        }

        design.craftingRecipes().remove(selected);
        persistSharedContent("crafting recipe");
        setStatus("Deleted crafting recipe " + selected.displayName() + ".");
    }

    private void editCookingRecipe(MapDesignLibrary.CustomCookingRecipe selected) {
        MapDesignLibrary.CustomCookingRecipe edited = showCookingRecipeDialog("Edit Cooking Recipe", selected);
        if (edited == null) {
            return;
        }

        int index = design.customCookingRecipes().indexOf(selected);
        if (index >= 0) {
            design.customCookingRecipes().set(index, edited);
            persistSharedContent("cooking recipe");
            setStatus("Updated cooking recipe " + edited.displayName() + ".");
        }
    }

    private void deleteCookingRecipe(MapDesignLibrary.CustomCookingRecipe selected) {
        if (!confirmDelete("cooking recipe", selected.displayName(), ContentCategory.COOKING, selected.recipeId(),
                selected)) {
            return;
        }

        design.customCookingRecipes().remove(selected);
        persistSharedContent("cooking recipe");
        setStatus("Deleted cooking recipe " + selected.displayName() + ".");
    }

    private void editCraftingRecipe(MapDesignLibrary.CraftingRecipe selected) {
        MapDesignLibrary.CraftingRecipe edited = showCraftingRecipeDialog("Edit Crafting Recipe", selected);
        if (edited == null) {
            return;
        }
        int index = design.craftingRecipes().indexOf(selected);
        if (index >= 0) {
            design.craftingRecipes().set(index, edited);
            persistSharedContent("crafting recipe");
            setStatus("Updated crafting recipe " + edited.displayName() + ".");
        }
    }

    private void editCustomMob(MapDesignLibrary.CustomMob selected) {
        JTextField nameField = new JTextField(selected.displayName(), 24);
        JTextField imagePathField = new JTextField(selected.imagePath(), 28);
        JTextField paperDollSourceField = new JTextField(selected.paperDollSourcePath(), 28);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        Map<PlayerStat, JSpinner> statSpinners = enemyStatSpinners();
        applyStatValuesToSpinners(statSpinners, selected.statValues());
        JLabel hpLabel = new JLabel();
        JLabel difficultyPreviewLabel = new JLabel();
        JLabel meleeMaxDamageLabel = new JLabel();
        JSpinner spellBaseDamageSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
        JLabel spellMaxDamageLabel = new JLabel();
        JSpinner combatAiSpinner = new JSpinner(
                new SpinnerNumberModel(Math.min(10, selected.combatAiIntelligence()), 0, 10, 1));
        JSpinner awarenessRadiusSpinner = new JSpinner(new SpinnerNumberModel(selected.awarenessRadius(), 0, 64, 1));
        JSpinner movementIntervalSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1L, selected.movementIntervalMs() / 1000L), 1L, 3600L, 1L));
        JSpinner respawnDelaySpinner = new JSpinner(new SpinnerNumberModel(
                selected.respawnDelayMs() / 1000L, 0L, 86400L, 1L));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(selected.xpReward(), 0, 100000, 1));
        JTextField attackSoundField = new JTextField(selected.attackSoundPath(), 24);
        JTextField damageSoundField = new JTextField(selected.damageSoundPath(), 24);
        CharacterModelEditorFields characterModelFields = new CharacterModelEditorFields(selected.characterModel());
        JButton attackSoundBrowseButton = new JButton("Browse");
        JButton damageSoundBrowseButton = new JButton("Browse");
        JList<SkillDefinition> skillList = skillDefinitionList(selected.skillIds());
        selectSkills(skillList, selected.skillIds());
        JTextArea descriptionArea = new JTextArea(selected.description(), 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        List<MapDesignLibrary.CustomDropEntry> dropEntries = new ArrayList<>(selected.dropEntries());
        JButton dropsButton = new JButton("Drops");
        ButcheryEditorControls butcheryControls = new ButcheryEditorControls(
                selected,
                nameField,
                paperDollSourceField,
                statSpinners,
                skillList,
                descriptionArea);

        browseButton.addActionListener(event -> browsePathInto(imagePathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        attackSoundBrowseButton.addActionListener(event -> browsePathInto(attackSoundField));
        damageSoundBrowseButton.addActionListener(event -> browsePathInto(damageSoundField));
        dropsButton.addActionListener(event -> editDropEntries(dropEntries));
        Runnable updatePreview = () -> {
            EnumMap<PlayerStat, Integer> statValues = statValuesFromSpinners(statSpinners);
            hpLabel.setText("HP = " + statValues.getOrDefault(PlayerStat.VITALITY, 1));
            difficultyPreviewLabel.setText(customEnemyDifficultyPreview(statValues, selectedSkillIds(skillList)));
            meleeMaxDamageLabel.setText(enemyMeleeMaxDamagePreview(statValues));
            spellMaxDamageLabel.setText(enemySpellMaxDamagePreview(
                    statValues,
                    ((Number) spellBaseDamageSpinner.getValue()).intValue()));
        };
        ChangeListener previewChangeListener = event -> updatePreview.run();
        for (JSpinner spinner : statSpinners.values()) {
            spinner.addChangeListener(previewChangeListener);
        }
        spellBaseDamageSpinner.addChangeListener(previewChangeListener);
        skillList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updatePreview.run();
            }
        });
        updatePreview.run();

        Component formTabs = buildMobTabbedForm(
                nameField, imagePathField, browseButton, paperDollSourceField, paperDollBrowseButton,
                xpSpinner, descriptionArea, skillList, statSpinners, hpLabel, difficultyPreviewLabel,
                meleeMaxDamageLabel, spellBaseDamageSpinner, spellMaxDamageLabel, combatAiSpinner,
                awarenessRadiusSpinner, movementIntervalSpinner, respawnDelaySpinner, characterModelFields,
                attackSoundField, attackSoundBrowseButton, damageSoundField, damageSoundBrowseButton,
                dropsButton, butcheryControls.component()
        );

        int result = showScrollableFormDialog(formTabs, "Edit Enemy");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom enemy needs a name.");
            return;
        }
        CharacterModelDefinition characterModel = characterModelFields.toDefinition();
        if (!validateCharacterModelDefinition(characterModel, "enemy")) {
            return;
        }
        if (!butcheryControls.confirmTypeSwitchImpact()) {
            return;
        }
        if (!butcheryControls.validateProducts()) {
            return;
        }

        MapDesignLibrary.CustomMob edited = new MapDesignLibrary.CustomMob(
                selected.mobId(),
                name,
                imagePathField.getText() == null ? "" : imagePathField.getText().trim(),
                paperDollSourceField.getText() == null ? "" : paperDollSourceField.getText().trim(),
                statValuesFromSpinners(statSpinners),
                ((Number) xpSpinner.getValue()).intValue(),
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                attackSoundField.getText() == null ? "" : attackSoundField.getText().trim(),
                damageSoundField.getText() == null ? "" : damageSoundField.getText().trim(),
                ((Number) combatAiSpinner.getValue()).intValue(),
                ((Number) awarenessRadiusSpinner.getValue()).intValue(),
                ((Number) movementIntervalSpinner.getValue()).intValue() * 1000,
                ((Number) respawnDelaySpinner.getValue()).intValue() * 1000,
                selectedSkillIds(skillList),
                dropEntries,
                characterModel,
                butcheryControls.profile());
        int index = design.customMobs().indexOf(selected);
        if (index >= 0) {
            List<MapDesignLibrary.CustomMob> mobsBefore = new ArrayList<>(design.customMobs());
            List<MapDesignLibrary.CustomItem> itemsBefore = new ArrayList<>(design.customItems());
            List<MapDesignLibrary.CustomLimb> limbsBefore = new ArrayList<>(design.customLimbs());
            design.customMobs().set(index, edited);
            if (!butcheryControls.applyProducts(edited.mobId())) {
                restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
                return;
            }
            if (!confirmAppliedButcheryValidation(edited)) {
                restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
                return;
            }
            if (!persistSharedContent("custom enemy")) {
                restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
                return;
            }
            populatePlaceables();
            setStatus("Updated custom enemy " + edited.displayName() + ".");
        }
    }

    private void restoreEnemyCatalogDraft(
            List<MapDesignLibrary.CustomMob> mobs,
            List<MapDesignLibrary.CustomItem> items,
            List<MapDesignLibrary.CustomLimb> limbs
    ) {
        design.customMobs().clear();
        design.customMobs().addAll(mobs);
        design.customItems().clear();
        design.customItems().addAll(items);
        design.customLimbs().clear();
        design.customLimbs().addAll(limbs);
        refreshContentBrowser();
    }

    private boolean confirmAppliedButcheryValidation(MapDesignLibrary.CustomMob mob) {
        if (mob == null) {
            return false;
        }
        Set<String> relatedIds = new HashSet<>();
        relatedIds.add(mob.mobId());
        relatedIds.addAll(mob.butcheryProfile().limbProductIds().values());
        if (!mob.butcheryProfile().leatherItemId().isBlank()) {
            relatedIds.add(mob.butcheryProfile().leatherItemId());
        }
        List<MapDesignLibrary.ValidationIssue> issues = MapDesignLibrary.validate(design).stream()
                .filter(issue -> relatedIds.stream().anyMatch(id -> issue.message().contains(id)))
                .toList();
        if (issues.isEmpty()) {
            return true;
        }
        String message = validationMessage(issues);
        if (hasValidationError(issues)) {
            showAdaptiveTextMessageDialog(
                    message,
                    "Enemy Product Validation",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return showAdaptiveTextConfirmDialog(
                this,
                message + "\n\nApply with these warnings?",
                "Enemy Product Validation",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void duplicateEnemyWithProducts(MapDesignLibrary.CustomMob source, String copiedName) {
        String copiedMobId = nextCustomMobId(copiedName);
        EnemyButcheryProfile copiedProfile;
        List<MapDesignLibrary.CustomItem> copiedItems = new ArrayList<>();
        List<MapDesignLibrary.CustomLimb> copiedLimbs = new ArrayList<>();
        if (source.butcheryProfile().type() == EnemyButcheryProfile.Type.LEATHER) {
            MapDesignLibrary.CustomItem sourceLeather = design.customItems().stream()
                    .filter(item -> source.butcheryProfile().leatherItemId().equals(item.itemId()))
                    .findFirst()
                    .orElse(null);
            if (sourceLeather == null) {
                setStatus("Cannot duplicate enemy: its linked leather product is missing.");
                return;
            }
            String leatherId = "item_" + limbSlugFromMobId(copiedMobId) + "_leather";
            if (design.customItems().stream().anyMatch(item -> leatherId.equals(item.itemId()))) {
                setStatus("Cannot duplicate enemy: generated leather ID already exists: " + leatherId + ".");
                return;
            }
            copiedItems.add(new MapDesignLibrary.CustomItem(
                    leatherId,
                    copiedName + " Leather",
                    InventorySystem.ItemType.MISC,
                    sourceLeather.iconPath(),
                    "",
                    "",
                    WeaponType.NONE,
                    false,
                    GearMaterial.LEATHER,
                    0,
                    sourceLeather.baseGoldValue(),
                    sourceLeather.examineText(),
                    null,
                    true,
                    false,
                    1,
                    1,
                    0,
                    0,
                    "",
                    EquipmentViewModelProfile.defaults())
                    .withSourceEnemyId(copiedMobId));
            copiedProfile = new EnemyButcheryProfile(
                    EnemyButcheryProfile.Type.LEATHER,
                    Map.of(),
                    leatherId,
                    source.butcheryProfile().baseValueOverride());
        } else {
            EnumMap<LimbSlot, String> copiedIds = new EnumMap<>(LimbSlot.class);
            for (LimbSlot slot : LimbSlot.values()) {
                MapDesignLibrary.CustomLimb sourceLimb = design.customLimbs().stream()
                        .filter(limb -> source.butcheryProfile().productId(slot).equals(limb.limbId()))
                        .findFirst()
                        .orElse(null);
                if (sourceLimb == null) {
                    setStatus("Cannot duplicate enemy: its " + slot.getDisplayName() + " product is missing.");
                    return;
                }
                String limbId = "limb_" + limbSlugFromMobId(copiedMobId)
                        + "_" + slot.name().toLowerCase(Locale.ROOT);
                if (design.customLimbs().stream().anyMatch(limb -> limbId.equals(limb.limbId()))) {
                    setStatus("Cannot duplicate enemy: generated limb ID already exists: " + limbId + ".");
                    return;
                }
                copiedIds.put(slot, limbId);
                copiedLimbs.add(new MapDesignLibrary.CustomLimb(
                        limbId,
                        copiedName + " " + slot.getDisplayName(),
                        slot,
                        sourceLimb.iconPath(),
                        sourceLimb.condition(),
                        sourceLimb.description(),
                        copiedMobId,
                        sourceLimb.paperDollSourcePath(),
                        sourceLimb.statBonuses(),
                        sourceLimb.skillIds(),
                        sourceLimb.firstPersonModelPath(),
                        sourceLimb.firstPersonRigId(),
                        sourceLimb.paperDollDerivedIcon(),
                        sourceLimb.baseGoldValue()));
            }
            copiedProfile = new EnemyButcheryProfile(
                    EnemyButcheryProfile.Type.HUMANOID_LIMBS,
                    copiedIds,
                    "",
                    source.butcheryProfile().baseValueOverride());
        }

        MapDesignLibrary.CustomMob copied = new MapDesignLibrary.CustomMob(
                copiedMobId,
                copiedName,
                source.imagePath(),
                source.paperDollSourcePath(),
                source.statValues(),
                source.xpReward(),
                source.description(),
                source.attackSoundPath(),
                source.damageSoundPath(),
                source.combatAiIntelligence(),
                source.awarenessRadius(),
                source.movementIntervalMs(),
                source.respawnDelayMs(),
                source.skillIds(),
                source.dropEntries(),
                source.characterModel(),
                copiedProfile);
        List<MapDesignLibrary.CustomMob> mobsBefore = new ArrayList<>(design.customMobs());
        List<MapDesignLibrary.CustomItem> itemsBefore = new ArrayList<>(design.customItems());
        List<MapDesignLibrary.CustomLimb> limbsBefore = new ArrayList<>(design.customLimbs());
        design.customMobs().add(copied);
        design.customItems().addAll(copiedItems);
        design.customLimbs().addAll(copiedLimbs);
        if (!confirmAppliedButcheryValidation(copied)) {
            restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
            return;
        }
        if (!persistSharedContent("custom enemy")) {
            restoreEnemyCatalogDraft(mobsBefore, itemsBefore, limbsBefore);
            return;
        }
        populatePlaceables();
        setStatus("Duplicated enemy " + copiedName + " with independent butchery products.");
    }

    private void deleteCustomMob(MapDesignLibrary.CustomMob selected) {
        if (!confirmDelete("enemy", selected.displayName(), ContentCategory.ENEMIES, selected.mobId(), selected)) {
            return;
        }

        design.customMobs().remove(selected);
        persistSharedContent("custom enemy");
        populatePlaceables();
        setStatus("Deleted custom enemy " + selected.displayName() + ".");
    }

    private void editCustomNpc(MapDesignLibrary.CustomNpc selected) {
        MapDesignLibrary.CustomNpc edited = showCustomNpcDialog("Edit NPC", selected, selected.shop() != null);
        if (edited == null) {
            return;
        }

        int index = design.customNpcs().indexOf(selected);
        if (index >= 0) {
            design.customNpcs().set(index, edited);
            persistSharedContent("custom NPC");
            populatePlaceables();
            setStatus("Updated custom NPC " + edited.displayName() + ".");
        }
    }

    private void deleteCustomNpc(MapDesignLibrary.CustomNpc selected) {
        if (!confirmDelete("NPC", selected.displayName(), ContentCategory.NPCS, selected.npcId(), selected)) {
            return;
        }

        design.customNpcs().remove(selected);
        persistSharedContent("custom NPC");
        populatePlaceables();
        setStatus("Deleted custom NPC " + selected.displayName() + ".");
    }

    private void editCustomFurniture(MapDesignLibrary.CustomFurnitureDefinition selected) {
        MapDesignLibrary.CustomFurnitureDefinition edited = showCustomFurnitureDialog("Edit Furniture", selected);
        if (edited == null) {
            return;
        }

        int index = design.customFurniture().indexOf(selected);
        if (index >= 0) {
            design.customFurniture().set(index, edited);
            persistSharedContent("custom furniture");
            populatePlaceables();
            setStatus("Updated furniture " + edited.displayName() + ".");
        }
    }

    private void deleteCustomFurniture(MapDesignLibrary.CustomFurnitureDefinition selected) {
        if (!confirmDelete("furniture", selected.displayName(), ContentCategory.FURNITURE, selected.furnitureId(), selected)) {
            return;
        }

        design.customFurniture().remove(selected);
        design.placements().removeIf(placement -> placement.kind() == MapDesignLibrary.PlacementKind.FURNITURE
                && selected.furnitureId().equals(placement.id()));
        design.placedObjects().removeIf(object -> object.kind() == MapDesignLibrary.PlacementKind.FURNITURE
                && selected.furnitureId().equals(object.id()));
        persistSharedContent("custom furniture");
        populatePlaceables();
        setStatus("Deleted furniture " + selected.displayName() + ".");
    }

    private boolean confirmDelete(String type, String name) {
        return confirmDelete(type, name, null, "", null);
    }

    private boolean confirmDelete(String type, String name, ContentCategory category, String id, Object value) {
        List<String> references = category == null
                ? List.of()
                : findReferences(new ContentEntry(category, name, id, type, value));
        String message = deleteMessage("Delete " + name + "?", references);

        int result = showAdaptiveTextConfirmDialog(
                this,
                message,
                "Delete " + type,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.OK_OPTION;
    }

    private String deleteMessage(String header, List<String> references) {
        StringBuilder message = new StringBuilder();
        message.append(header);
        if (references.isEmpty()) {
            message.append("\n\nNo references were found in the current map/content set.");
        } else {
            message.append("\n\nReferences that may be affected:");
            int count = 0;
            for (String reference : references) {
                message.append("\n- ").append(reference);
                count++;
                if (count >= 8 && references.size() > count) {
                    message.append("\n- ...and ").append(references.size() - count).append(" more.");
                    break;
                }
            }
        }
        message.append("\n\nThis cannot be undone through the map undo stack.");
        return message.toString();
    }

    private List<MapDesignLibrary.CustomLimb> generateLimbsForEnemy(
            String mobId,
            String enemyName,
            Map<PlayerStat, Integer> monsterStats,
            String limbDescription,
            String paperDollSourcePath,
            List<SkillDefinition> enemySkills) {
        Map<LimbSlot, List<String>> skillAssignments = assignSkillsToLimbs(enemySkills);
        List<MapDesignLibrary.CustomLimb> limbs = new ArrayList<>();
        String slug = limbSlugFromMobId(mobId);

        for (LimbSlot slot : LimbSlot.values()) {
            EnumMap<PlayerStat, Integer> limbStats = emptyStatMap();
            for (PlayerStat stat : PlayerStat.values()) {
                limbStats.put(stat, allocatedStat(monsterStats.getOrDefault(stat, 0), stat, slot));
            }

            limbs.add(new MapDesignLibrary.CustomLimb(
                    "limb_" + slug + "_" + slot.name().toLowerCase(),
                    enemyName + " " + slot.getDisplayName(),
                    slot,
                    DEFAULT_LIMB_ICON,
                    GearDurability.PERFECT,
                    limbDescription,
                    mobId,
                    paperDollSourcePath,
                    limbStats,
                    skillAssignments.getOrDefault(slot, List.of())));
        }

        return limbs;
    }

    private Map<PlayerStat, JSpinner> enemyStatSpinners() {
        EnumMap<PlayerStat, JSpinner> spinners = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            int initialValue = switch (stat) {
                case VITALITY -> 10;
                case ATTACK, STRENGTH, DEFENSE, AGILITY, INTELLIGENCE, WILLPOWER -> 1;
            };
            int minimum = stat == PlayerStat.VITALITY ? 1 : 0;
            spinners.put(stat, new JSpinner(new SpinnerNumberModel(initialValue, minimum, 1000, 1)));
        }
        return spinners;
    }

    private void applyStatValuesToSpinners(Map<PlayerStat, JSpinner> spinners, Map<PlayerStat, Integer> statValues) {
        for (PlayerStat stat : PlayerStat.values()) {
            JSpinner spinner = spinners.get(stat);
            if (spinner != null) {
                int defaultValue = stat == PlayerStat.VITALITY ? 1 : 0;
                int value = statValues == null ? defaultValue : statValues.getOrDefault(stat, defaultValue);
                spinner.setValue(stat == PlayerStat.VITALITY ? Math.max(1, value) : Math.max(0, value));
            }
        }
    }

    private EnumMap<PlayerStat, Integer> statValuesFromSpinners(Map<PlayerStat, JSpinner> spinners) {
        EnumMap<PlayerStat, Integer> stats = emptyStatMap();
        for (PlayerStat stat : PlayerStat.values()) {
            JSpinner spinner = spinners.get(stat);
            int value = spinner == null ? 0 : ((Number) spinner.getValue()).intValue();
            stats.put(stat, stat == PlayerStat.VITALITY ? Math.max(1, value) : Math.max(0, value));
        }
        return stats;
    }

    private void selectSkills(JList<SkillDefinition> skillList, List<String> selectedSkillIds) {
        if (skillList == null || selectedSkillIds == null || selectedSkillIds.isEmpty()) {
            return;
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < skillList.getModel().getSize(); i++) {
            SkillDefinition skill = skillList.getModel().getElementAt(i);
            if (selectedSkillIds.contains(skill.id())) {
                indices.add(i);
            }
        }
        skillList.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
    }

    private JList<SkillDefinition> skillDefinitionList() {
        return skillDefinitionList(List.of());
    }

    private JList<SkillDefinition> skillDefinitionList(List<String> referencedIds) {
        List<SkillDefinition> definitions = new ArrayList<>(
                BattleContentCatalog.current().skills().values());
        if (referencedIds != null) {
            for (String rawId : referencedIds) {
                String id = BattleContentCatalog.normalizeId(rawId);
                if (id.isBlank() || definitions.stream().anyMatch(skill -> skill.id().equals(id))) {
                    continue;
                }
                definitions.add(new SkillDefinition(
                        id,
                        "Unavailable [" + id + "]",
                        "Missing battle-skill reference.",
                        Library.SkillTargetShape.SINGLE_TARGET,
                        Library.EntityType.ALLY,
                        Library.BattleTargetingMode.MAGIC,
                        "",
                        "UTILITY",
                        0,
                        true,
                        List.of()));
            }
        }
        JList<SkillDefinition> list = new JList<>(definitions.toArray(new SkillDefinition[0]));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> component,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                super.getListCellRendererComponent(component, value, index, selected, focused);
                if (value instanceof SkillDefinition skill) {
                    setText(skill.displayName() + "  [" + skill.id() + "]");
                }
                return this;
            }
        });
        return list;
    }

    private List<String> selectedSkillIds(JList<SkillDefinition> list) {
        return list == null ? List.of() : list.getSelectedValuesList().stream()
                .map(SkillDefinition::id)
                .toList();
    }

    private boolean hasSkillEffect(SkillDefinition skill, String kindId) {
        return skill != null && skill.effects().stream().anyMatch(effect -> kindId.equals(effect.kindId()));
    }

    private boolean appliesStatus(SkillDefinition skill, String statusId) {
        String normalized = BattleContentCatalog.normalizeId(statusId);
        return skill != null && skill.effects().stream()
                .filter(effect -> "apply_status".equals(effect.kindId()))
                .anyMatch(effect -> normalized.equals(BattleContentCatalog.normalizeId(
                        effect.parameter("statusId", ""))));
    }

    private void editGeneratedLimbs(List<MapDesignLibrary.CustomLimb> limbs) {
        if (limbs == null || limbs.isEmpty()) {
            return;
        }

        JList<MapDesignLibrary.CustomLimb> limbList = new JList<>(limbs.toArray(new MapDesignLibrary.CustomLimb[0]));
        JButton editButton = new JButton("Edit Selected");
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(limbList), BorderLayout.CENTER);
        panel.add(editButton, BorderLayout.SOUTH);

        editButton.addActionListener(event -> {
            int index = limbList.getSelectedIndex();
            if (index < 0) {
                return;
            }

            MapDesignLibrary.CustomLimb selected = limbs.get(index);
            MapDesignLibrary.CustomLimb edited = showCustomLimbDialog(
                    "Edit Generated Limb",
                    selected.limbId(),
                    selected.displayName(),
                    selected.limbSlot(),
                    selected.iconPath(),
                    selected.description(),
                    selected.sourceCreatureId(),
                    selected.paperDollSourcePath(),
                    selected.statBonuses(),
                    selected.skillIds(),
                    selected.firstPersonModelPath(),
                    selected.firstPersonRigId(),
                    selected.paperDollDerivedIcon(),
                    selected.baseGoldValue());
            if (edited != null) {
                limbs.set(index, edited);
                limbList.setListData(limbs.toArray(new MapDesignLibrary.CustomLimb[0]));
                limbList.setSelectedIndex(index);
            }
        });

        showScrollableMessageDialog(panel, "Generated Limbs", JOptionPane.PLAIN_MESSAGE);
    }

    private void editDropEntries(List<MapDesignLibrary.CustomDropEntry> drops) {
        if (drops == null) {
            return;
        }

        DefaultListModel<MapDesignLibrary.CustomDropEntry> model = new DefaultListModel<>();
        for (MapDesignLibrary.CustomDropEntry drop : drops) {
            model.addElement(drop);
        }

        JList<MapDesignLibrary.CustomDropEntry> dropList = new JList<>(model);
        JComboBox<DropItemOption> itemBox = new JComboBox<>(dropItemOptions().toArray(new DropItemOption[0]));
        JSpinner chanceSpinner = new JSpinner(new SpinnerNumberModel(100.0, 0.0, 100.0, 0.001));
        chanceSpinner.setEditor(new JSpinner.NumberEditor(chanceSpinner, "0.###"));
        JButton addButton = new JButton("Add Drop");
        JButton removeButton = new JButton("Remove Selected");

        addButton.addActionListener(event -> {
            DropItemOption option = (DropItemOption) itemBox.getSelectedItem();
            if (option == null || option.itemId().isBlank()) {
                return;
            }
            double chance = ((Number) chanceSpinner.getValue()).doubleValue() / 100.0;
            model.addElement(new MapDesignLibrary.CustomDropEntry(option.itemId(), chance));
        });

        removeButton.addActionListener(event -> {
            int index = dropList.getSelectedIndex();
            if (index >= 0) {
                model.remove(index);
            }
        });

        JPanel controls = createFormPanel();
        addFormRow(controls, "Item", itemBox);
        addFormRow(controls, "Chance %", chanceSpinner);
        JPanel buttonRow = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        buttonRow.add(addButton);
        buttonRow.add(removeButton);
        controls.add(buttonRow);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(dropList), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Enemy Drops");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        drops.clear();
        for (int i = 0; i < model.size(); i++) {
            drops.add(model.get(i));
        }
    }

    private void editGatheringLootEntries(List<MapDesignLibrary.CustomDropEntry> drops) {
        if (drops == null) {
            return;
        }

        DefaultListModel<MapDesignLibrary.CustomDropEntry> model = new DefaultListModel<>();
        for (MapDesignLibrary.CustomDropEntry drop : drops) {
            model.addElement(drop);
        }

        JList<MapDesignLibrary.CustomDropEntry> dropList = new JList<>(model);
        JComboBox<DropItemOption> itemBox = new JComboBox<>(
                gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 100.0, 0.05));
        JButton addButton = new JButton("Add Loot");
        JButton removeButton = new JButton("Remove Selected");

        addButton.addActionListener(event -> {
            DropItemOption option = (DropItemOption) itemBox.getSelectedItem();
            if (option == null || option.itemId().isBlank()) {
                return;
            }
            model.addElement(new MapDesignLibrary.CustomDropEntry(option.itemId(),
                    ((Number) weightSpinner.getValue()).doubleValue()));
        });

        removeButton.addActionListener(event -> {
            int index = dropList.getSelectedIndex();
            if (index >= 0) {
                model.remove(index);
            }
        });

        JPanel controls = createFormPanel();
        addFormRow(controls, "Item", itemBox);
        addFormRow(controls, "Weight", weightSpinner);
        JPanel lootButtonRow = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        lootButtonRow.add(addButton);
        lootButtonRow.add(removeButton);
        controls.add(lootButtonRow);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(dropList), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Gathering Loot");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        drops.clear();
        for (int i = 0; i < model.size(); i++) {
            drops.add(model.get(i));
        }
    }

    private List<DropItemOption> dropItemOptions() {
        List<DropItemOption> options = new ArrayList<>();
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            options.add(new DropItemOption(item.itemId(), item.displayName()));
        }
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            options.add(new DropItemOption(limb.limbId(), limb.displayName()));
        }
        return options;
    }

    private List<DropItemOption> gatheringOutputItemOptions() {
        List<DropItemOption> options = new ArrayList<>();
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            options.add(new DropItemOption(item.itemId(), item.displayName()));
        }
        return options;
    }

    private ItemTemplateOption[] itemTemplateOptions() {
        List<ItemTemplateOption> options = new ArrayList<>();
        options.add(new ItemTemplateOption("None", null));
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            options.add(new ItemTemplateOption(item.displayName(), item));
        }
        return options.toArray(new ItemTemplateOption[0]);
    }

    private String findItemIdByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            if (displayName.equalsIgnoreCase(item.displayName())) {
                return item.itemId();
            }
        }
        return "";
    }

    private String itemIconPathForOption(DropItemOption option) {
        if (option == null || option.itemId().isBlank()) {
            return "";
        }
        return itemIconPathForId(option.itemId());
    }

    private String itemIconPathForId(String itemIdOrName) {
        if (itemIdOrName == null || itemIdOrName.isBlank()) {
            return "";
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            if (itemIdOrName.equalsIgnoreCase(item.itemId())
                    || itemIdOrName.equalsIgnoreCase(item.displayName())) {
                return item.iconPath();
            }
        }
        return "";
    }

    private boolean selectDropItem(JComboBox<DropItemOption> comboBox, String itemId) {
        if (comboBox == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            DropItemOption option = comboBox.getItemAt(i);
            if (option != null && itemId.equals(option.itemId())) {
                comboBox.setSelectedIndex(i);
                return true;
            }
        }
        DropItemOption unavailable = new DropItemOption(itemId, "[Unavailable]");
        comboBox.addItem(unavailable);
        comboBox.setSelectedItem(unavailable);
        return false;
    }

    private record DropItemOption(String itemId, String displayName) {
        private DropItemOption {
            itemId = itemId == null ? "" : itemId;
            displayName = displayName == null || displayName.isBlank() ? itemId : displayName;
        }

        @Override
        public String toString() {
            return displayName + " [" + itemId + "]";
        }
    }

    private record NpcBaseOption(
            String label,
            String displayName,
            String imagePath,
            String talkSoundPath,
            CharacterModelDefinition characterModel,
            boolean manual) {
        private NpcBaseOption {
            characterModel = characterModel == null
                    ? CharacterModelDefinition.empty()
                    : characterModel;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void addCharacterModelDependencies(
            List<String> dependencies,
            CharacterModelDefinition definition) {
        if (definition == null) {
            return;
        }
        addAssetDependency(dependencies, "3D character model", definition.modelPath());
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            addAssetDependency(dependencies, animationSlotLabel(slot) + " animation",
                    definition.animationPath(slot));
        }
    }

    private final class CharacterModelEditorFields {
        private final JTextField modelPathField = new JTextField(28);
        private final JTextField rigIdField = new JTextField(20);
        private final JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.05));
        private final JSpinner facingSpinner = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 1.0));
        private final JSpinner verticalOffsetSpinner = new JSpinner(new SpinnerNumberModel(0.0, -20.0, 20.0, 0.05));
        private final EnumMap<CharacterModelDefinition.AnimationSlot, JTextField> animationFields = new EnumMap<>(
                CharacterModelDefinition.AnimationSlot.class);
        private final EnumMap<CharacterModelDefinition.AnimationSlot, JTextField> clipNameFields = new EnumMap<>(
                CharacterModelDefinition.AnimationSlot.class);
        private final EnumMap<CharacterModelDefinition.AnimationSlot, JSpinner> speedSpinners = new EnumMap<>(
                CharacterModelDefinition.AnimationSlot.class);
        private final EnumMap<CharacterModelDefinition.AnimationSlot, JSpinner> impactSpinners = new EnumMap<>(
                CharacterModelDefinition.AnimationSlot.class);
        private final CharacterAnimationPreviewPanel previewPanel;

        private CharacterModelEditorFields(CharacterModelDefinition definition) {
            for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
                animationFields.put(slot, new JTextField(28));
                clipNameFields.put(slot, new JTextField(18));
                speedSpinners.put(slot, new JSpinner(new SpinnerNumberModel(1.0, 0.05, 10.0, 0.05)));
                impactSpinners.put(slot, new JSpinner(new SpinnerNumberModel(
                        CharacterModelDefinition.DEFAULT_IMPACT_FRACTION, 0.05, 0.95, 0.01)));
            }
            apply(definition);
            previewPanel = new CharacterAnimationPreviewPanel(
                    this::toDefinition,
                    this::selectedImpactFraction,
                    this::setSelectedImpactFraction,
                    this::selectedClipName,
                    this::setSelectedClipName);
        }

        private void apply(CharacterModelDefinition definition) {
            CharacterModelDefinition safe = definition == null
                    ? CharacterModelDefinition.empty()
                    : definition;
            modelPathField.setText(safe.modelPath());
            rigIdField.setText(safe.rigId());
            scaleSpinner.setValue(safe.scale());
            facingSpinner.setValue(safe.facingRotationDegrees());
            verticalOffsetSpinner.setValue(safe.verticalOffset());
            for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
                CharacterModelDefinition.AnimationBinding binding = safe.animationBinding(slot);
                animationFields.get(slot).setText(binding.path());
                clipNameFields.get(slot).setText(binding.clipName());
                speedSpinners.get(slot).setValue(binding.playbackSpeed());
                impactSpinners.get(slot).setValue(binding.impactFraction());
            }
        }

        private CharacterModelDefinition toDefinition() {
            EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> animations = new EnumMap<>(
                    CharacterModelDefinition.AnimationSlot.class);
            for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
                String path = animationFields.get(slot).getText();
                String clipName = clipNameFields.get(slot).getText();
                if ((path == null || path.isBlank())
                        && clipName != null
                        && !clipName.isBlank()) {
                    path = modelPathField.getText();
                }
                if (path != null && !path.isBlank()) {
                    animations.put(slot, new CharacterModelDefinition.AnimationBinding(
                            path,
                            clipName,
                            ((Number) speedSpinners.get(slot).getValue()).doubleValue(),
                            ((Number) impactSpinners.get(slot).getValue()).doubleValue()));
                }
            }
            return new CharacterModelDefinition(
                    modelPathField.getText(),
                    rigIdField.getText(),
                    ((Number) scaleSpinner.getValue()).doubleValue(),
                    ((Number) facingSpinner.getValue()).doubleValue(),
                    ((Number) verticalOffsetSpinner.getValue()).doubleValue(),
                    animations);
        }

        private double selectedImpactFraction() {
            return ((Number) impactSpinners.get(previewPanel.selectedSlot()).getValue()).doubleValue();
        }

        private void setSelectedImpactFraction(double value) {
            impactSpinners.get(previewPanel.selectedSlot()).setValue(value);
        }

        private String selectedClipName() {
            return clipNameFields.get(previewPanel.selectedSlot()).getText();
        }

        private void setSelectedClipName(String value) {
            CharacterModelDefinition.AnimationSlot slot = previewPanel.selectedSlot();
            String clipName = value == null ? "" : value;
            clipNameFields.get(slot).setText(clipName);
            if (!clipName.isBlank() && animationFields.get(slot).getText().isBlank()) {
                animationFields.get(slot).setText(modelPathField.getText());
            }
        }
    }

    private record ItemTemplateOption(String label, MapDesignLibrary.CustomItem item) {
        @Override
        public String toString() {
            return label;
        }
    }

    private EnumMap<PlayerStat, Integer> emptyStatMap() {
        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, 0);
        }
        return stats;
    }

    private int allocatedStat(int total, PlayerStat stat, LimbSlot slot) {
        if (total <= 0) {
            return 0;
        }

        double weight = allocationWeight(stat, slot);
        if (weight <= 0.0) {
            return 0;
        }

        int allocated = (int) Math.floor(total * weight);
        return allocated == 0 ? 1 : allocated;
    }

    private double allocationWeight(PlayerStat stat, LimbSlot slot) {
        return switch (stat) {
            case ATTACK ->
                slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM || slot == LimbSlot.HEAD ? 0.35 : 0.0;
            case STRENGTH -> slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM ? 0.50 : 0.0;
            case DEFENSE -> slot == LimbSlot.BODY ? 0.80 : slot == LimbSlot.HEAD ? 0.20 : 0.0;
            case AGILITY ->
                slot == LimbSlot.LEGS ? 0.70 : slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM ? 0.15 : 0.0;
            case INTELLIGENCE -> slot == LimbSlot.HEAD ? 1.0 : 0.0;
            case WILLPOWER -> slot == LimbSlot.HEAD ? 0.55 : slot == LimbSlot.BODY ? 0.35 : 0.0;
            case VITALITY -> slot == LimbSlot.BODY ? 0.80 : slot == LimbSlot.LEGS ? 0.20 : 0.0;
        };
    }

    private Map<LimbSlot, List<String>> assignSkillsToLimbs(List<SkillDefinition> skills) {
        Map<LimbSlot, List<String>> assignments = new EnumMap<>(LimbSlot.class);
        for (LimbSlot slot : LimbSlot.values()) {
            assignments.put(slot, new ArrayList<>());
        }

        if (skills == null) {
            return assignments;
        }

        for (SkillDefinition skill : skills) {
            LimbSlot slot = bestSlotForSkill(skill, assignments);
            assignments.get(slot).add(skill.id());
        }

        return assignments;
    }

    private LimbSlot bestSlotForSkill(SkillDefinition skill, Map<LimbSlot, List<String>> assignments) {
        List<LimbSlot> preferences;
        if ("absorb".equals(skill.id()) || hasSkillEffect(skill, "heal_from_damage")) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (hasSkillEffect(skill, "summon")) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (appliesStatus(skill, "guard")) {
            preferences = List.of(LimbSlot.BODY, LimbSlot.LEGS);
        } else if (skill.targetingMode() == Library.BattleTargetingMode.MAGIC) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (skill.targetingMode() == Library.BattleTargetingMode.RANGED
                || skill.targetingMode() == Library.BattleTargetingMode.NORMAL_MELEE
                || skill.targetingMode() == Library.BattleTargetingMode.REACH_MELEE) {
            preferences = List.of(LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM, LimbSlot.HEAD);
        } else {
            preferences = List.of(LimbSlot.LEGS, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        }

        LimbSlot bestSlot = preferences.get(0);
        int bestCount = Integer.MAX_VALUE;
        for (LimbSlot slot : preferences) {
            int count = assignments.getOrDefault(slot, List.of()).size();
            if (count < bestCount) {
                bestSlot = slot;
                bestCount = count;
            }
        }
        return bestSlot;
    }

    private String limbSlugFromMobId(String mobId) {
        String slug = mobId == null ? "" : mobId;
        if (slug.startsWith("custom_mob_")) {
            slug = slug.substring("custom_mob_".length());
        }
        return safeId(slug).isBlank() ? "enemy" : safeId(slug);
    }

    private MapDesignLibrary.CustomLimb showCustomLimbDialog(
            String title,
            String limbId,
            String displayName,
            LimbSlot limbSlot,
            String iconPath,
            String description,
            String sourceCreatureId,
            String paperDollSourcePath,
            Map<PlayerStat, Integer> statValues,
            List<String> selectedSkills,
            String firstPersonModelPath,
            String firstPersonRigId,
            boolean paperDollDerivedIcon,
            int baseGoldValue) {
        JTextField nameField = new JTextField(displayName, 24);
        JTextField iconPathField = new JTextField(iconPath, 28);
        JTextField sourceCreatureIdField = new JTextField(sourceCreatureId == null ? "" : sourceCreatureId, 28);
        JTextField paperDollSourceField = new JTextField(paperDollSourcePath == null ? "" : paperDollSourcePath, 28);
        JTextField firstPersonModelField = new JTextField(
                firstPersonModelPath == null ? "" : firstPersonModelPath, 28);
        record RigOption(String id, String label) {
            @Override public String toString() {
                return id.isBlank() ? label : label + "  [" + id + "]";
            }
        }
        FirstPersonCombatLibrary.Content firstPersonContent = FirstPersonCombatLibrary.load();
        JComboBox<RigOption> firstPersonRigBox = new JComboBox<>();
        firstPersonRigBox.addItem(new RigOption("", "Use equipment/default rig"));
        firstPersonContent.rigs().values().forEach(rig -> firstPersonRigBox.addItem(
                new RigOption(rig.rigId(), rig.displayName())));
        String selectedRigId = FirstPersonCombatLibrary.normalizeId(firstPersonRigId);
        boolean foundRig = selectedRigId.isBlank();
        for (int index = 0; index < firstPersonRigBox.getItemCount(); index++) {
            if (firstPersonRigBox.getItemAt(index).id().equals(selectedRigId)) {
                firstPersonRigBox.setSelectedIndex(index);
                foundRig = true;
                break;
            }
        }
        if (!foundRig) {
            RigOption unavailable = new RigOption(selectedRigId, "Unavailable rig");
            firstPersonRigBox.addItem(unavailable);
            firstPersonRigBox.setSelectedItem(unavailable);
        }
        JTextArea descriptionArea = new JTextArea(description == null ? "" : description, 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JButton firstPersonBrowseButton = new JButton("Browse");
        JComboBox<LimbSlot> slotBox = new JComboBox<>(LimbSlot.values());
        JCheckBox derivedIconBox = new JCheckBox("Use paper-doll slice", paperDollDerivedIcon);
        JSpinner baseValueSpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(1, baseGoldValue), 1, 100000, 1));
        slotBox.setSelectedItem(limbSlot == null ? LimbSlot.HEAD : limbSlot);
        Map<PlayerStat, JSpinner> statSpinners = new EnumMap<>(PlayerStat.class);
        Map<String, JCheckBox> skillBoxes = new LinkedHashMap<>();

        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        firstPersonBrowseButton.addActionListener(
                event -> showAssetBrowser(firstPersonModelField, AssetBrowserType.MODELS));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = createFormPanel();
        addFormRow(fields, "Name", nameField);
        addFormRow(fields, "Icon PNG", pathFieldPanel(iconPathField, browseButton));
        addFormRow(fields, "Paper-Doll Source", pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        addFormRow(fields, "Inventory Icon", derivedIconBox);
        addFormRow(fields, "Base Gold Value", baseValueSpinner);
        addFormRow(fields, "First-Person Arm Model", pathFieldPanel(firstPersonModelField, firstPersonBrowseButton));
        addFormRow(fields, "First-Person Rig ID", firstPersonRigBox);
        addFormRow(fields, "Source Creature Id", sourceCreatureIdField);
        addFormRow(fields, "Slot", slotBox);
        addFormRow(fields, "Condition", new JLabel(GearDurability.PERFECT.name()));
        for (PlayerStat stat : PlayerStat.values()) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    Math.max(0, statValues == null ? 0 : statValues.getOrDefault(stat, 0)), 0, 1000, 1));
            statSpinners.put(stat, spinner);
            addFormRow(fields, stat.getDisplayName(), spinner);
        }
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.add(ConstructionKitUi.scrollingForm(topAlignedForm(fields)), BorderLayout.CENTER);
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setBorder(BorderFactory.createTitledBorder("Description"));
        centerPanel.add(descriptionScroll, BorderLayout.SOUTH);
        JPanel skillsPanel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        for (SkillDefinition skill : BattleContentCatalog.current().skills().values()) {
            JCheckBox checkBox = new JCheckBox(skill.displayName() + "  [" + skill.id() + "]");
            checkBox.setSelected(selectedSkills != null && selectedSkills.contains(skill.id()));
            skillBoxes.put(skill.id(), checkBox);
            skillsPanel.add(checkBox);
        }
        panel.add(centerPanel, BorderLayout.CENTER);
        JScrollPane skillsScroll = new JScrollPane(skillsPanel);
        skillsScroll.setBorder(BorderFactory.createTitledBorder("Skills"));
        panel.add(skillsScroll, BorderLayout.EAST);

        int result = showScrollableFormDialog(panel, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom limb needs a name.");
            return null;
        }
        LimbSlot selectedSlot = (LimbSlot) slotBox.getSelectedItem();
        String firstPersonPath = normalizedPath(firstPersonModelField.getText());
        if (!firstPersonPath.isBlank()
                && (selectedSlot == null || !selectedSlot.isArm())) {
            setStatus("Only left- and right-arm limbs can use a first-person arm model.");
            return null;
        }
        if (!firstPersonPath.isBlank() && !isSupportedCharacterModelAsset(firstPersonPath)) {
            setStatus("First-person limb models must be .glb or .fbx assets.");
            return null;
        }

        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        for (Map.Entry<PlayerStat, JSpinner> entry : statSpinners.entrySet()) {
            stats.put(entry.getKey(), ((Number) entry.getValue().getValue()).intValue());
        }
        List<String> skills = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : skillBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                skills.add(entry.getKey());
            }
        }

        RigOption selectedRig = (RigOption) firstPersonRigBox.getSelectedItem();
        return new MapDesignLibrary.CustomLimb(
                limbId == null || limbId.isBlank() ? nextCustomLimbId(name) : limbId,
                name,
                selectedSlot,
                iconPathField.getText() == null ? "" : iconPathField.getText().trim(),
                GearDurability.PERFECT,
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                sourceCreatureIdField.getText() == null ? "" : sourceCreatureIdField.getText().trim(),
                paperDollSourceField.getText() == null ? "" : paperDollSourceField.getText().trim(),
                stats,
                skills,
                firstPersonPath,
                selectedRig == null ? "" : selectedRig.id(),
                derivedIconBox.isSelected(),
                ((Number) baseValueSpinner.getValue()).intValue());
    }

    private void addCharacterModelRows(JPanel fields, CharacterModelEditorFields editor) {
        JButton modelBrowse = new JButton("Browse");
        modelBrowse.addActionListener(event -> showAssetBrowser(editor.modelPathField, AssetBrowserType.MODELS));
        fields.add(new JLabel("3D Character Model (.glb/.fbx)"));
        fields.add(pathFieldPanel(editor.modelPathField, modelBrowse));
        fields.add(new JLabel("Skeleton / Rig ID"));
        fields.add(editor.rigIdField);
        fields.add(new JLabel("Character Size (1.0 = standard eye-level)"));
        fields.add(editor.scaleSpinner);
        fields.add(new JLabel("Model Facing Rotation"));
        fields.add(editor.facingSpinner);
        fields.add(new JLabel("Ground Offset"));
        fields.add(editor.verticalOffsetSpinner);
        fields.add(new JLabel("Empty Animation Slots"));
        fields.add(new JLabel("Use procedural placeholders"));
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            JTextField pathField = editor.animationFields.get(slot);
            JButton browse = new JButton("Browse");
            browse.addActionListener(event -> showAssetBrowser(pathField, AssetBrowserType.MODELS));
            fields.add(new JLabel(animationSlotLabel(slot)
                    + " Animation Source (.glb/.fbx; blank uses base model)"));
            fields.add(pathFieldPanel(pathField, browse));
            fields.add(new JLabel(animationSlotLabel(slot) + " Named Clip (blank = first)"));
            fields.add(editor.clipNameFields.get(slot));
            fields.add(new JLabel(animationSlotLabel(slot) + " Playback Speed"));
            fields.add(editor.speedSpinners.get(slot));
            fields.add(new JLabel(animationSlotLabel(slot) + " Impact Fraction"));
            fields.add(editor.impactSpinners.get(slot));
        }
        fields.add(new JLabel("Animation / Rig Preview"));
        fields.add(editor.previewPanel);
    }

    private String animationSlotLabel(CharacterModelDefinition.AnimationSlot slot) {
        String lower = slot.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private boolean validateCharacterModelDefinition(
            CharacterModelDefinition definition,
            String contentType) {
        if (definition == null) {
            return true;
        }
        if (!definition.modelPath().isBlank() && !isSupportedCharacterModelAsset(definition.modelPath())) {
            setStatus("The " + contentType + " 3D model must be a .glb or .fbx asset.");
            return false;
        }
        boolean hasAnimation = false;
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            String path = definition.animationPath(slot);
            if (path.isBlank()) {
                continue;
            }
            hasAnimation = true;
            if (!isSupportedCharacterModelAsset(path)) {
                setStatus(animationSlotLabel(slot) + " animation must be a .glb or .fbx asset.");
                return false;
            }
        }
        if (hasAnimation && definition.modelPath().isBlank()) {
            setStatus("Choose a base 3D character model before assigning animation clips.");
            return false;
        }
        if (!definition.hasModel()) {
            return true;
        }
        try {
            CharacterAnimationMetadataResolver.ModelMetadata metadata =
                    CharacterAnimationMetadataResolver.resolve(definition);
            for (CharacterModelDefinition.AnimationSlot slot
                    : CharacterModelDefinition.AnimationSlot.values()) {
                CharacterModelDefinition.AnimationBinding binding =
                        definition.animationBinding(slot);
                CharacterAnimationMetadataResolver.SlotMetadata resolved =
                        metadata.slot(slot);
                if (binding.isPresent() && !resolved.available()) {
                    String requestedClip = binding.clipName().isBlank()
                            ? "(automatic)"
                            : binding.clipName();
                    String detail = resolved.diagnostic().isBlank()
                            ? slot.displayName() + " clip is unavailable."
                            : resolved.diagnostic();
                    setStatus("Cannot apply " + contentType + ": " + requestedClip
                            + " in " + binding.path() + " is invalid. " + detail);
                    return false;
                }
            }
        } catch (Exception exception) {
            setStatus("Cannot inspect the " + contentType + " 3D model: "
                    + exception.getMessage());
            return false;
        }
        return true;
    }

    private boolean isSupportedCharacterModelAsset(String path) {
        String normalized = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".glb") || normalized.endsWith(".fbx");
    }

    private JPanel pathFieldPanel(JTextField pathField, JButton browseButton) {
        JButton assetButton = new JButton("Assets");
        assetButton.addActionListener(event -> showAssetBrowser(pathField));
        return ConstructionKitUi.inlineFields(pathField, browseButton, assetButton);
    }

    private JPanel modelPathFieldPanel(JTextField pathField, JButton browseButton, String generatedFolder) {
        JButton assetButton = new JButton("Assets");
        JButton importButton = new JButton("Import");
        assetButton.addActionListener(event -> showAssetBrowser(pathField, AssetBrowserType.MODELS));
        importButton.addActionListener(event -> importModelInto(pathField, generatedFolder));
        return ConstructionKitUi.inlineFields(pathField, browseButton, assetButton, importButton);
    }

    private void importModelInto(JTextField pathField, String generatedFolder) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("3D models (.glb, .fbx)", "glb", "fbx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path source = chooser.getSelectedFile().toPath();
        String sourceName = source.getFileName() == null ? "model.glb" : source.getFileName().toString();
        if (!isSupportedCharacterModelAsset(sourceName)) {
            setStatus("3D models must be .glb or .fbx assets.");
            return;
        }

        String extension = getFileExtension(sourceName);
        String baseName = sourceName.substring(0, sourceName.length() - extension.length());
        String safeFolder = generatedFolder == null || generatedFolder.isBlank() ? "models" : safeId(generatedFolder);
        String safeFileName = safeId(baseName) + extension.toLowerCase(Locale.ROOT);
        Path targetFolder = Path.of("src", "main", "resources", "assets", "3D", "generated", safeFolder);
        Path target = targetFolder.resolve(safeFileName);
        try {
            Files.createDirectories(targetFolder);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            invalidateModelAssetCaches();
            String assetPath = "assets/3D/generated/" + safeFolder + "/" + safeFileName;
            pathField.setText(assetPath);
            setStatus("Imported model " + assetPath + ".");
        } catch (IOException exception) {
            setStatus("Model import failed: " + exception.getMessage());
        }
    }

    private void invalidateModelAssetCaches() {
        CharacterAnimationMetadataResolver.clear();
        LwjglSkinnedModel.clearSharedCache();
        FirstPersonAnimationRuntime.clearCaches();
    }

    private JPanel createFormPanel() {
        return ConstructionKitUi.formPanel();
    }

    private JPanel formRow(String label, Component component) {
        return ConstructionKitUi.formRow(label, component);
    }

    private JPanel inlinePanel(Component first, Component second) {
        return ConstructionKitUi.inlineFields(first, second);
    }

    private void addFormRow(java.awt.Container container, String label, Component component) {
        container.add(formRow(label, component));
    }

    private int showScrollableFormDialog(Component form, String title) {
        return showScrollableFormDialog(this, form, title);
    }

    private int showScrollableFormDialog(Component parent, Component form, String title) {
        return ConstructionKitUi.showFormDialog(parent, form, title, popupSizes);
    }

    private Component topAlignedForm(Component fields) {
        return ConstructionKitUi.topAligned(fields);
    }

    private Component screenAwarePopupContent(Component form) {
        return ConstructionKitUi.messageContent(form);
    }

    private void showScrollableMessageDialog(Component content, String title, int messageType) {
        showResizableOptionDialog(
                this,
                screenAwarePopupContent(content),
                title,
                JOptionPane.DEFAULT_OPTION,
                messageType);
    }

    private int showAdaptiveTextConfirmDialog(
            Component parent,
            String message,
            String title,
            int optionType,
            int messageType) {
        String safeMessage = message == null ? "" : message;
        long lineCount = safeMessage.lines().count();
        if (safeMessage.length() < 280 && lineCount < 9) {
            return showResizableOptionDialog(parent, safeMessage, title, optionType, messageType);
        }
        JTextArea area = new JTextArea(safeMessage, Math.min(24, (int) Math.max(8, lineCount)), 68);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        return showResizableOptionDialog(
                parent,
                screenAwarePopupContent(area),
                title,
                optionType,
                messageType);
    }

    private void showAdaptiveTextMessageDialog(String message, String title, int messageType) {
        showAdaptiveTextMessageDialog(this, message, title, messageType);
    }

    private void showAdaptiveTextMessageDialog(Component parent, String message, String title, int messageType) {
        String safeMessage = message == null ? "" : message;
        long lineCount = safeMessage.lines().count();
        if (safeMessage.length() < 280 && lineCount < 9) {
            showResizableOptionDialog(parent, safeMessage, title, JOptionPane.DEFAULT_OPTION, messageType);
            return;
        }
        JTextArea area = new JTextArea(safeMessage, Math.min(24, (int) Math.max(8, lineCount)), 68);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        showResizableOptionDialog(
                parent,
                screenAwarePopupContent(area),
                title,
                JOptionPane.DEFAULT_OPTION,
                messageType);
    }

    private int showResizableOptionDialog(
            Component parent,
            Object message,
            String title,
            int optionType,
            int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType);
        JDialog dialog = pane.createDialog(parent, title);
        showManagedDialog(dialog);
        Object value = pane.getValue();
        return value instanceof Integer selected ? selected : JOptionPane.CLOSED_OPTION;
    }

    private String showResizableInputDialog(
            Component parent,
            Object message,
            String title,
            Object initialValue) {
        JOptionPane pane = new JOptionPane(
                message,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                null,
                null);
        pane.setWantsInput(true);
        pane.setInitialSelectionValue(initialValue);
        JDialog dialog = pane.createDialog(parent, title);
        showManagedDialog(dialog);
        Object option = pane.getValue();
        if (!(option instanceof Integer selected) || selected != JOptionPane.OK_OPTION) {
            return null;
        }
        Object input = pane.getInputValue();
        return input == JOptionPane.UNINITIALIZED_VALUE || input == null ? null : input.toString();
    }

    private String showResizableChoiceDialog(
            Component parent,
            Object message,
            String title,
            Object[] choices,
            Object initialValue) {
        JOptionPane pane = new JOptionPane(
                message,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                null,
                null);
        pane.setWantsInput(true);
        pane.setSelectionValues(choices);
        pane.setInitialSelectionValue(initialValue);
        JDialog dialog = pane.createDialog(parent, title);
        showManagedDialog(dialog);
        Object option = pane.getValue();
        if (!(option instanceof Integer selected) || selected != JOptionPane.OK_OPTION) {
            return null;
        }
        Object input = pane.getInputValue();
        return input == JOptionPane.UNINITIALIZED_VALUE || input == null ? null : input.toString();
    }

    private void showManagedDialog(JDialog dialog) {
        ConstructionKitUi.showManagedDialog(dialog, popupSizes);
    }

    private void setFormRowLabel(JPanel row, String label) {
        if (row == null || label == null) {
            return;
        }
        Component component = row.getComponentCount() == 0 ? null : row.getComponent(0);
        if (component instanceof JLabel rowLabel) {
            rowLabel.setText(label);
        }
    }

    private String framePathAt(MapDesignLibrary.CustomGatheringNode node, int index) {
        if (node == null || node.framePaths() == null || index < 0 || index >= node.framePaths().size()) {
            return "";
        }
        return node.framePaths().get(index);
    }

    private String modelPathAt(MapDesignLibrary.CustomGatheringNode node, int index) {
        if (node == null || node.modelPaths() == null || index < 0 || index >= node.modelPaths().size()) {
            return "";
        }
        return node.modelPaths().get(index);
    }

    private List<String> normalizedOptionalPaths(String... paths) {
        List<String> normalized = new ArrayList<>();
        if (paths == null) {
            return normalized;
        }
        for (String path : paths) {
            String value = path == null ? "" : path.trim().replace('\\', '/');
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void browsePathInto(JTextField pathField) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void loadPrefabs() {
        prefabBox.removeAllItems();
        if (!Files.isDirectory(PREFAB_FOLDER)) {
            return;
        }

        try (var stream = Files.list(PREFAB_FOLDER)) {
            stream.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".properties"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(this::loadPrefab)
                    .filter(prefab -> prefab != null)
                    .forEach(prefabBox::addItem);
        } catch (IOException exception) {
            setStatus("Prefab load warning: " + exception.getMessage());
        }
    }

    private MapPrefab loadPrefab(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            setStatus("Prefab load failed: " + exception.getMessage());
            return null;
        }

        int width = Math.max(1, readPrefabInt(properties, "width", 1));
        int height = Math.max(1, readPrefabInt(properties, "height", 1));
        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];
        for (int y = 0; y < height; y++) {
            String[] tileValues = properties.getProperty("tile." + y, "").split(",");
            String[] themeValues = properties.getProperty("theme." + y, "").split(",");
            for (int x = 0; x < width; x++) {
                tiles[y][x] = readPrefabTile(tileValues, x, Library.TileType.FLOOR);
                themes[y][x] = Math.max(0, Math.min(1, readPrefabListInt(themeValues, x, 0)));
            }
        }

        List<MapDesignLibrary.MapPlacement> placements = new ArrayList<>();
        int placementCount = readPrefabInt(properties, "placement.count", 0);
        for (int i = 0; i < placementCount; i++) {
            String prefix = "placement." + i + ".";
            MapDesignLibrary.PlacementKind kind = readPrefabPlacementKind(properties.getProperty(prefix + "kind", ""));
            String id = properties.getProperty(prefix + "id", "");
            int x = readPrefabInt(properties, prefix + "x", 0);
            int y = readPrefabInt(properties, prefix + "y", 0);
            if (kind != null && !id.isBlank() && x >= 0 && y >= 0 && x < width && y < height) {
                placements.add(new MapDesignLibrary.MapPlacement(kind, id, x, y));
            }
        }

        List<MapDesignLibrary.MapTrigger> triggers = new ArrayList<>();
        int triggerCount = readPrefabInt(properties, "trigger.count", 0);
        for (int i = 0; i < triggerCount; i++) {
            String prefix = "trigger." + i + ".";
            String id = properties.getProperty(prefix + "id", "");
            int x = readPrefabInt(properties, prefix + "x", 0);
            int y = readPrefabInt(properties, prefix + "y", 0);
            MapDesignLibrary.TriggerFireMode fireMode = readPrefabTriggerFireMode(
                    properties.getProperty(prefix + "fireMode", ""));
            boolean oneShot = Boolean.parseBoolean(properties.getProperty(prefix + "oneShot", "true"));
            String requiredQuestId = properties.getProperty(prefix + "requiredQuestId", "");
            String requiredQuestProgress = properties.getProperty(prefix + "requiredQuestProgress", "");
            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>();
            int actionCount = readPrefabInt(properties, prefix + "action.count", 0);
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                String actionPrefix = prefix + "action." + actionIndex + ".";
                MapDesignLibrary.TriggerActionType type = readPrefabTriggerActionType(
                        properties.getProperty(actionPrefix + "type", ""));
                int targetX = readPrefabInt(properties, actionPrefix + "targetX", 0);
                int targetY = readPrefabInt(properties, actionPrefix + "targetY", 0);
                if (type != null && targetX >= 0 && targetY >= 0 && targetX < width && targetY < height) {
                    actions.add(new MapDesignLibrary.TriggerAction(type, targetX, targetY));
                }
            }
            if (!id.isBlank() && x >= 0 && y >= 0 && x < width && y < height) {
                triggers.add(new MapDesignLibrary.MapTrigger(
                        id,
                        x,
                        y,
                        fireMode,
                        oneShot,
                        requiredQuestId,
                        requiredQuestProgress,
                        actions));
            }
        }

        String name = properties.getProperty("name", path.getFileName().toString().replaceFirst("[.][^.]+$", ""));
        return new MapPrefab(
                name,
                width,
                height,
                tiles,
                themes,
                readPrefabPaintData(properties, width, height),
                readPrefabGeometryData(properties, width, height),
                readPrefabMobAreas(properties, width, height),
                placements,
                triggers);
    }

    private void createPrefabFromRegion() {
        JTextField nameField = new JTextField("New Prefab", 20);
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(0, design.width() - 1), 1));
        JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(0, design.height() - 1), 1));
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(Math.min(3, design.width()), 1, design.width(), 1));
        JSpinner heightSpinner = new JSpinner(
                new SpinnerNumberModel(Math.min(3, design.height()), 1, design.height(), 1));

        JPanel panel = createFormPanel();
        addFormRow(panel, "Name", nameField);
        addFormRow(panel, "X", xSpinner);
        addFormRow(panel, "Y", ySpinner);
        addFormRow(panel, "Width", widthSpinner);
        addFormRow(panel, "Height", heightSpinner);

        int result = showScrollableFormDialog(panel, "Create Prefab From Region");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Prefab needs a name.");
            return;
        }

        int startX = ((Number) xSpinner.getValue()).intValue();
        int startY = ((Number) ySpinner.getValue()).intValue();
        int width = ((Number) widthSpinner.getValue()).intValue();
        int height = ((Number) heightSpinner.getValue()).intValue();
        if (startX + width > design.width() || startY + height > design.height()) {
            setStatus("Prefab region is outside the map.");
            return;
        }

        MapPrefab prefab = capturePrefab(name, startX, startY, width, height);
        try {
            savePrefab(prefab);
            loadPrefabs();
            selectPrefab(prefab.name());
            setStatus("Created prefab " + prefab.name() + ".");
        } catch (IOException exception) {
            setStatus("Prefab save failed: " + exception.getMessage());
        }
    }

    private MapPrefab capturePrefab(String name, int startX, int startY, int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];
        int[][] heightLevels = new int[height][width];
        String[][] floorBrushes = new String[height][width];
        String[][] wallBrushes = new String[height][width];
        String[][] doorBrushes = new String[height][width];
        String[][] roofBrushes = new String[height][width];
        String[][] mobAreas = new String[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(design.tiles()[startY + y], startX, tiles[y], 0, width);
            System.arraycopy(design.themeIndexes()[startY + y], startX, themes[y], 0, width);
            for (int x = 0; x < width; x++) {
                int worldX = startX + x;
                int worldY = startY + y;
                heightLevels[y][x] = design.mapGeometry() == null
                        ? MapGeometryData.DEFAULT_HEIGHT_LEVEL
                        : design.mapGeometry().getHeightLevel(worldX, worldY);
                floorBrushes[y][x] = design.mapPaint() == null ? ""
                        : design.mapPaint().get(MapPaintData.Layer.FLOOR, worldX, worldY);
                wallBrushes[y][x] = design.mapPaint() == null ? ""
                        : design.mapPaint().get(MapPaintData.Layer.WALL, worldX, worldY);
                doorBrushes[y][x] = design.mapPaint() == null ? ""
                        : design.mapPaint().get(MapPaintData.Layer.DOOR, worldX, worldY);
                roofBrushes[y][x] = design.mapPaint() == null ? ""
                        : design.mapPaint().get(MapPaintData.Layer.ROOF, worldX, worldY);
                mobAreas[y][x] = design.mobAreas() == null ? "" : design.mobAreas().get(worldX, worldY);
            }
        }

        List<MapDesignLibrary.MapPlacement> placements = new ArrayList<>();
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.x() >= startX && placement.y() >= startY
                    && placement.x() < startX + width && placement.y() < startY + height) {
                placements.add(new MapDesignLibrary.MapPlacement(
                        placement.kind(),
                        placement.id(),
                        placement.x() - startX,
                        placement.y() - startY));
            }
        }

        List<MapDesignLibrary.MapTrigger> triggers = new ArrayList<>();
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            if (trigger.x() < startX || trigger.y() < startY
                    || trigger.x() >= startX + width || trigger.y() >= startY + height) {
                continue;
            }
            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>();
            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                if (action.targetX() >= startX && action.targetY() >= startY
                        && action.targetX() < startX + width && action.targetY() < startY + height) {
                    actions.add(new MapDesignLibrary.TriggerAction(
                            action.type(),
                            action.targetX() - startX,
                            action.targetY() - startY));
                }
            }
            triggers.add(new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x() - startX,
                    trigger.y() - startY,
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    actions));
        }

        return new MapPrefab(
                name,
                width,
                height,
                tiles,
                themes,
                MapPaintData.of(width, height, floorBrushes, wallBrushes, doorBrushes, roofBrushes),
                MapGeometryData.of(width, height, heightLevels),
                MobAreaData.of(width, height, mobAreas),
                placements,
                triggers);
    }

    private void savePrefab(MapPrefab prefab) throws IOException {
        Files.createDirectories(PREFAB_FOLDER);
        Properties properties = new Properties();
        properties.setProperty("name", prefab.name());
        properties.setProperty("width", String.valueOf(prefab.width()));
        properties.setProperty("height", String.valueOf(prefab.height()));
        for (int y = 0; y < prefab.height(); y++) {
            List<String> tileValues = new ArrayList<>();
            List<String> themeValues = new ArrayList<>();
            List<String> heightValues = new ArrayList<>();
            for (int x = 0; x < prefab.width(); x++) {
                tileValues.add(prefab.tiles()[y][x].name());
                themeValues.add(String.valueOf(prefab.themes()[y][x]));
                heightValues.add(String.valueOf(prefab.geometryData().getHeightLevel(x, y)));
            }
            properties.setProperty("tile." + y, String.join(",", tileValues));
            properties.setProperty("theme." + y, String.join(",", themeValues));
            properties.setProperty("geometry.height." + y, String.join(",", heightValues));
            properties.setProperty("mobArea." + y, joinPrefabPaintRow(prefab.mobAreas().copyRows()[y]));
            for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                properties.setProperty("paint." + layer.name().toLowerCase(Locale.ROOT) + "." + y,
                        joinPrefabPaintRow(prefab.paintData().copyLayer(layer)[y]));
            }
        }
        properties.setProperty("placement.count", String.valueOf(prefab.placements().size()));
        for (int i = 0; i < prefab.placements().size(); i++) {
            MapDesignLibrary.MapPlacement placement = prefab.placements().get(i);
            String prefix = "placement." + i + ".";
            properties.setProperty(prefix + "kind", placement.kind().name());
            properties.setProperty(prefix + "id", placement.id());
            properties.setProperty(prefix + "x", String.valueOf(placement.x()));
            properties.setProperty(prefix + "y", String.valueOf(placement.y()));
        }
        properties.setProperty("trigger.count", String.valueOf(prefab.triggers().size()));
        for (int i = 0; i < prefab.triggers().size(); i++) {
            MapDesignLibrary.MapTrigger trigger = prefab.triggers().get(i);
            String prefix = "trigger." + i + ".";
            properties.setProperty(prefix + "id", trigger.id());
            properties.setProperty(prefix + "x", String.valueOf(trigger.x()));
            properties.setProperty(prefix + "y", String.valueOf(trigger.y()));
            properties.setProperty(prefix + "fireMode", trigger.fireMode().name());
            properties.setProperty(prefix + "oneShot", String.valueOf(trigger.oneShot()));
            properties.setProperty(prefix + "requiredQuestId", trigger.requiredQuestId());
            properties.setProperty(prefix + "requiredQuestProgress", trigger.requiredQuestProgress());
            properties.setProperty(prefix + "action.count", String.valueOf(trigger.actions().size()));
            for (int actionIndex = 0; actionIndex < trigger.actions().size(); actionIndex++) {
                MapDesignLibrary.TriggerAction action = trigger.actions().get(actionIndex);
                String actionPrefix = prefix + "action." + actionIndex + ".";
                properties.setProperty(actionPrefix + "type", action.type().name());
                properties.setProperty(actionPrefix + "targetX", String.valueOf(action.targetX()));
                properties.setProperty(actionPrefix + "targetY", String.valueOf(action.targetY()));
            }
        }

        Path path = PREFAB_FOLDER
                .resolve(safeId(prefab.name()).isBlank() ? "prefab.properties" : safeId(prefab.name()) + ".properties");
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "Aether Construction Kit prefab");
        }
    }

    private void managePrefabs() {
        if (prefabBox.getItemCount() == 0) {
            setStatus("No prefabs to manage.");
            return;
        }

        DefaultListModel<MapPrefab> model = new DefaultListModel<>();
        for (int i = 0; i < prefabBox.getItemCount(); i++) {
            model.addElement(prefabBox.getItemAt(i));
        }
        JList<MapPrefab> prefabList = new JList<>(model);
        prefabList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        prefabList.setSelectedIndex(0);

        JButton selectButton = new JButton("Select");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                new JScrollPane(prefabList),
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[] { selectButton, deleteButton, closeButton },
                closeButton);
        var dialog = pane.createDialog(this, "Manage Prefabs");
        selectButton.addActionListener(event -> {
            MapPrefab selected = prefabList.getSelectedValue();
            if (selected != null) {
                selectPrefab(selected.name());
                paintModeBox.setSelectedItem(PaintMode.PLACE_PREFAB);
                setStatus("Selected prefab " + selected.name() + ".");
                dialog.dispose();
            }
        });
        deleteButton.addActionListener(event -> {
            MapPrefab selected = prefabList.getSelectedValue();
            if (selected == null || showAdaptiveTextConfirmDialog(
                    dialog,
                    "Delete prefab " + selected.name() + "?",
                    "Delete Prefab",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                Files.deleteIfExists(PREFAB_FOLDER.resolve(safeId(selected.name()) + ".properties"));
                loadPrefabs();
                model.removeElement(selected);
                setStatus("Deleted prefab " + selected.name() + ".");
            } catch (IOException exception) {
                setStatus("Prefab delete failed: " + exception.getMessage());
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private void selectPrefab(String prefabName) {
        for (int i = 0; i < prefabBox.getItemCount(); i++) {
            MapPrefab prefab = prefabBox.getItemAt(i);
            if (prefab != null && prefab.name().equals(prefabName)) {
                prefabBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private MapPaintData readPrefabPaintData(Properties properties, int width, int height) {
        return MapPaintData.of(
                width,
                height,
                readPrefabPaintLayer(properties, "paint.floor.", width, height),
                readPrefabPaintLayer(properties, "paint.wall.", width, height),
                readPrefabPaintLayer(properties, "paint.door.", width, height),
                readPrefabPaintLayer(properties, "paint.roof.", width, height));
    }

    private String[][] readPrefabPaintLayer(Properties properties, String prefix, int width, int height) {
        String[][] layer = new String[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            String[] values = properties.getProperty(prefix + y, "").split(",", -1);
            for (int x = 0; x < width; x++) {
                layer[y][x] = x < values.length ? values[x].trim() : "";
            }
        }
        return layer;
    }

    private MapGeometryData readPrefabGeometryData(Properties properties, int width, int height) {
        int[][] heightLevels = new int[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            String[] values = properties.getProperty("geometry.height." + y, "").split(",", -1);
            for (int x = 0; x < width; x++) {
                heightLevels[y][x] = MapGeometryData.clampHeightLevel(
                        readPrefabListInt(values, x, MapGeometryData.DEFAULT_HEIGHT_LEVEL));
            }
        }
        return MapGeometryData.of(width, height, heightLevels);
    }

    private MobAreaData readPrefabMobAreas(Properties properties, int width, int height) {
        return MobAreaData.of(width, height, readPrefabPaintLayer(properties, "mobArea.", width, height));
    }

    private String joinPrefabPaintRow(String[] row) {
        if (row == null || row.length == 0) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (String value : row) {
            values.add(value == null ? "" : value.trim());
        }
        return String.join(",", values);
    }

    private int readPrefabInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int readPrefabListInt(String[] values, int index, int fallback) {
        if (index < 0 || index >= values.length) {
            return fallback;
        }
        try {
            return Integer.parseInt(values[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Library.TileType readPrefabTile(String[] values, int index, Library.TileType fallback) {
        if (index < 0 || index >= values.length) {
            return fallback;
        }
        try {
            return Library.TileType.valueOf(values[index]);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private MapDesignLibrary.PlacementKind readPrefabPlacementKind(String value) {
        try {
            return MapDesignLibrary.PlacementKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private MapDesignLibrary.TriggerActionType readPrefabTriggerActionType(String value) {
        try {
            return MapDesignLibrary.TriggerActionType.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private MapDesignLibrary.TriggerFireMode readPrefabTriggerFireMode(String value) {
        try {
            return MapDesignLibrary.TriggerFireMode.valueOf(value);
        } catch (RuntimeException ignored) {
            return MapDesignLibrary.TriggerFireMode.ON_ENTRY;
        }
    }

    private void createMapLink() {
        try {
            Files.createDirectories(MapDesignLibrary.MAP_FOLDER);
        } catch (IOException exception) {
            setStatus("Map folder failed: " + exception.getMessage());
            return;
        }

        JFileChooser chooser = new JFileChooser(MapDesignLibrary.EDITOR_RESOURCE_FOLDER.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Aether map or world", "properties"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        MapDesignLibrary.MapDesign targetDesign;
        WorldManifest targetWorld = null;
        ChunkCoordinate targetChunk = null;
        try {
            Path selectedPath = chooser.getSelectedFile().toPath();
            if (WorldManifestLibrary.isWorldManifest(selectedPath)) {
                targetWorld = WorldManifestLibrary.load(selectedPath);
                List<ChunkCoordinate> coordinates = targetWorld.chunks().keySet().stream().sorted().toList();
                JComboBox<ChunkCoordinate> chunkBox = new JComboBox<>(coordinates.toArray(new ChunkCoordinate[0]));
                if (showScrollableFormDialog(chunkBox, "Choose World Chunk") != JOptionPane.OK_OPTION) {
                    return;
                }
                targetChunk = (ChunkCoordinate) chunkBox.getSelectedItem();
                if (targetChunk == null) {
                    setStatus("No world chunk selected.");
                    return;
                }
                targetDesign = MapDesignLibrary.load(WorldManifestLibrary.resolveChunkPath(
                        selectedPath,
                        targetWorld.chunks().get(targetChunk)));
            } else {
                targetDesign = MapDesignLibrary.load(selectedPath);
            }
        } catch (IOException exception) {
            setStatus("Target map or world failed: " + exception.getMessage());
            return;
        }

        TargetMapPickerPanel pickerPanel = new TargetMapPickerPanel(targetDesign);
        int result = showScrollableFormDialog(pickerPanel, "Choose Map Link Target");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Point selectedTarget = pickerPanel.getSelectedTile();
        if (selectedTarget == null) {
            setStatus("Map link target was not selected.");
            return;
        }

        if (targetDesign.tiles()[selectedTarget.y][selectedTarget.x].blocksMovement()) {
            int confirm = showAdaptiveTextConfirmDialog(
                    this,
                    "The selected target tile blocks movement. Create this link anyway?",
                    "Blocking Target",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
        }

        int targetX = selectedTarget.x;
        int targetY = selectedTarget.y;
        if (targetWorld != null && targetChunk != null) {
            targetX = targetWorld.globalX(targetChunk, selectedTarget.x);
            targetY = targetWorld.globalY(targetChunk, selectedTarget.y);
        }
        String id = "map_link|"
                + mapLinkPathForEditor(chooser.getSelectedFile().toPath())
                + "|"
                + targetX
                + "|"
                + targetY;
        PlaceableOption option = new PlaceableOption(
                "Map Link: " + mapLinkLabel(id),
                MapDesignLibrary.PlacementKind.INTERACTION,
                id);
        placeableCategoryBox.setSelectedItem(PlaceableCategory.MAP_LINKS);
        placeableBox.addItem(option);
        placeableBox.setSelectedItem(option);
        paintModeBox.setSelectedItem(PaintMode.PLACE_OBJECT);
        setStatus("Created map link placement. Paint it onto a tile.");
    }

    private void createTrigger() {
        TriggerSettings settings = showTriggerSettings(this, null, "Create Trigger");
        if (settings == null) {
            return;
        }

        if (findTrigger(settings.id()) != null) {
            setStatus("Trigger id already exists: " + settings.id() + ".");
            return;
        }

        pendingTriggerId = settings.id();
        pendingTriggerFireMode = settings.fireMode();
        pendingTriggerOneShot = settings.oneShot();
        pendingTriggerQuestId = settings.requiredQuestId();
        pendingTriggerQuestProgress = settings.requiredQuestProgress();
        wiringTriggerId = "";
        paintModeBox.setSelectedItem(PaintMode.PLACE_TRIGGER);
        setStatus("Click a floor tile to place trigger " + settings.id() + ".");
    }

    private void createLight() {
        paintModeBox.setSelectedItem(PaintMode.PLACE_LIGHT);
        setStatus("Click a tile to place a light source.");
    }

    private void placeLight(int x, int y) {
        MapLight draft = new MapLight(nextLightId(), x, y, 0xFF8B42, 5.0, 1.0, 0.65, 0.12, true);
        MapLight light = showLightDialog(this, draft, "Place Light");
        if (light == null) {
            setStatus("Light placement cancelled.");
            return;
        }
        if (findLight(light.id()) != null) {
            setStatus("Light id already exists: " + light.id() + ".");
            return;
        }
        design.lights().add(light);
        refreshContentBrowser();
        revealContentEntry(light, ContentCategory.LIGHTS);
        setStatus("Placed light " + light.id() + " at " + light.x() + "," + light.y() + ".");
    }

    private void manageLights() {
        DefaultListModel<MapLight> model = new DefaultListModel<>();
        refreshLightList(model);
        JList<MapLight> lightList = new JList<>(model);
        lightList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lightList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(lightLabel(value));
            label.setOpaque(true);
            label.setBackground(selected ? new Color(65, 95, 140) : list.getBackground());
            label.setForeground(selected ? Color.WHITE : list.getForeground());
            return label;
        });

        JButton addButton = new JButton("Place New");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel();
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(closeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(lightList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {});
        var dialog = pane.createDialog(this, "Manage Lights");

        addButton.addActionListener(event -> {
            paintModeBox.setSelectedItem(PaintMode.PLACE_LIGHT);
            setStatus("Click a tile to place a light source.");
            dialog.dispose();
        });
        editButton.addActionListener(event -> {
            MapLight selected = lightList.getSelectedValue();
            if (selected == null) {
                return;
            }
            editLight(selected);
            refreshLightList(model);
        });
        deleteButton.addActionListener(event -> {
            MapLight selected = lightList.getSelectedValue();
            if (selected == null) {
                return;
            }
            deleteLight(selected);
            refreshLightList(model);
        });
        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private MapLight showLightDialog(Component parent, MapLight light, String title) {
        MapLight safeLight = light == null
                ? new MapLight(nextLightId(), 0, 0, 0xFF8B42, 5.0, 1.0, 0.65, 0.12, true)
                : light;
        JTextField idField = new JTextField(safeLight.id(), 22);
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(safeLight.x(), 0, Math.max(0, design.width() - 1), 1));
        JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(safeLight.y(), 0, Math.max(0, design.height() - 1), 1));
        JTextField colorField = new JTextField(MapLightingSettings.colorHex(safeLight.colorRgb()), 10);
        JSpinner radiusSpinner = new JSpinner(new SpinnerNumberModel(safeLight.radius(), 0.1, 64.0, 0.25));
        JSpinner intensitySpinner = new JSpinner(new SpinnerNumberModel(safeLight.intensity(), 0.0, 4.0, 0.05));
        JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(safeLight.heightOffset(), -2.0, 8.0, 0.05));
        JSpinner flickerSpinner = new JSpinner(new SpinnerNumberModel(safeLight.flickerAmount(), 0.0, 1.0, 0.05));
        JCheckBox enabledBox = new JCheckBox("Enabled", safeLight.enabled());
        JComboBox<LightPreset> presetBox = new JComboBox<>(LIGHT_PRESETS.toArray(new LightPreset[0]));
        JButton applyPresetButton = new JButton("Apply Preset");
        applyPresetButton.addActionListener(event -> {
            LightPreset preset = (LightPreset) presetBox.getSelectedItem();
            if (preset == null) {
                return;
            }
            colorField.setText(MapLightingSettings.colorHex(preset.colorRgb()));
            radiusSpinner.setValue(preset.radius());
            intensitySpinner.setValue(preset.intensity());
            heightSpinner.setValue(preset.heightOffset());
            flickerSpinner.setValue(preset.flickerAmount());
        });

        JPanel fields = createFormPanel();
        addFormRow(fields, "Preset", inlinePanel(presetBox, applyPresetButton));
        addFormRow(fields, "Id", idField);
        addFormRow(fields, "X", xSpinner);
        addFormRow(fields, "Y", ySpinner);
        addFormRow(fields, "Color", colorField);
        addFormRow(fields, "Radius", radiusSpinner);
        addFormRow(fields, "Intensity", intensitySpinner);
        addFormRow(fields, "Height Offset", heightSpinner);
        addFormRow(fields, "Flicker", flickerSpinner);
        addFormRow(fields, "Enabled", enabledBox);

        while (showScrollableFormDialog(parent, fields, title) == JOptionPane.OK_OPTION) {
            String id = idField.getText() == null ? "" : idField.getText().trim();
            if (id.isBlank()) {
                showAdaptiveTextMessageDialog(parent, "Light id cannot be blank.", title, JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return new MapLight(
                    id,
                    ((Number) xSpinner.getValue()).intValue(),
                    ((Number) ySpinner.getValue()).intValue(),
                    MapLightingSettings.parseColor(colorField.getText(), safeLight.colorRgb()),
                    ((Number) radiusSpinner.getValue()).doubleValue(),
                    ((Number) intensitySpinner.getValue()).doubleValue(),
                    ((Number) heightSpinner.getValue()).doubleValue(),
                    ((Number) flickerSpinner.getValue()).doubleValue(),
                    enabledBox.isSelected());
        }
        return null;
    }

    private void editLight(MapLight light) {
        MapLight updated = showLightDialog(this, light, "Edit Light");
        if (updated == null) {
            return;
        }
        if (!updated.id().equals(light.id()) && findLight(updated.id()) != null) {
            setStatus("Light id already exists: " + updated.id() + ".");
            return;
        }
        captureHistory("edit light");
        replaceLight(light, updated);
        refreshContentBrowser();
        revealContentEntry(updated, ContentCategory.LIGHTS);
        mapCanvas.repaint();
        markDirty(true);
        setStatus("Updated light " + updated.id() + ".");
    }

    private void deleteLight(MapLight light) {
        int result = showAdaptiveTextConfirmDialog(
                this,
                "Delete light " + light.id() + "?",
                "Delete Light",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        captureHistory("delete light");
        design.lights().remove(light);
        refreshContentBrowser();
        mapCanvas.repaint();
        markDirty(true);
        setStatus("Deleted light " + light.id() + ".");
    }

    private void refreshLightList(DefaultListModel<MapLight> model) {
        model.clear();
        for (MapLight light : design.lights()) {
            model.addElement(light);
        }
    }

    private String nextLightId() {
        int index = design.lights().size() + 1;
        while (findLight("light_" + index) != null) {
            index++;
        }
        return "light_" + index;
    }

    private MapLight findLight(String id) {
        if (id == null) {
            return null;
        }
        for (MapLight light : design.lights()) {
            if (id.equals(light.id())) {
                return light;
            }
        }
        return null;
    }

    private void replaceLight(MapLight oldLight, MapLight newLight) {
        int index = design.lights().indexOf(oldLight);
        if (index >= 0) {
            design.lights().set(index, newLight);
        }
    }

    private String lightLabel(MapLight light) {
        if (light == null) {
            return "";
        }
        return light.id() + " @ " + light.x() + "," + light.y()
                + " r" + light.radius()
                + " " + MapLightingSettings.colorHex(light.colorRgb())
                + (light.enabled() ? "" : " disabled");
    }

    private TriggerSettings showTriggerSettings(
            Component parent,
            MapDesignLibrary.MapTrigger trigger,
            String title) {
        JTextField idField = new JTextField(trigger == null ? nextTriggerId() : trigger.id(), 22);
        JComboBox<TriggerActivationOption> activationBox = new JComboBox<>(new TriggerActivationOption[] {
                new TriggerActivationOption("Player enters trigger tile", MapDesignLibrary.TriggerFireMode.ON_ENTRY),
                new TriggerActivationOption("Quest reaches progress", MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS)
        });
        if (trigger != null && trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS) {
            activationBox.setSelectedIndex(1);
        }

        JComboBox<QuestActionOption> questBox = new JComboBox<>(questActionOptions());
        if (trigger != null) {
            selectQuestActionOption(questBox, trigger.requiredQuestId());
        }
        JComboBox<QuestProgressOption> progressBox = new JComboBox<>();
        Runnable refreshProgressOptions = () -> {
            QuestActionOption quest = (QuestActionOption) questBox.getSelectedItem();
            String questId = quest == null ? "" : quest.questId();
            String selectedProgress = progressBox.getSelectedItem() instanceof QuestProgressOption option
                    ? option.progressId()
                    : trigger == null ? "" : trigger.requiredQuestProgress();
            progressBox.removeAllItems();
            for (QuestProgressOption option : questProgressOptions(questId)) {
                progressBox.addItem(option);
                if (option.progressId().equals(selectedProgress)) {
                    progressBox.setSelectedItem(option);
                }
            }
        };
        questBox.addActionListener(event -> refreshProgressOptions.run());
        refreshProgressOptions.run();
        JCheckBox oneShotBox = new JCheckBox("Fire only once", trigger == null || trigger.oneShot());
        JLabel stageHint = new JLabel("Stages use their stable authored IDs.");

        Runnable updateQuestControls = () -> {
            TriggerActivationOption activation = (TriggerActivationOption) activationBox.getSelectedItem();
            boolean questActivation = activation != null
                    && activation.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS;
            questBox.setEnabled(questActivation);
            progressBox.setEnabled(questActivation);
            stageHint.setEnabled(questActivation);
        };
        activationBox.addActionListener(event -> updateQuestControls.run());
        updateQuestControls.run();

        JPanel panel = createFormPanel();
        addFormRow(panel, "Trigger id", idField);
        addFormRow(panel, "Activate when", activationBox);
        addFormRow(panel, "Quest", questBox);
        addFormRow(panel, "Required progress", progressBox);
        addFormRow(panel, "", stageHint);
        addFormRow(panel, "", oneShotBox);

        while (showScrollableFormDialog(parent, panel, title) == JOptionPane.OK_OPTION) {
            String id = idField.getText() == null ? "" : idField.getText().trim();
            TriggerActivationOption activation = (TriggerActivationOption) activationBox.getSelectedItem();
            MapDesignLibrary.TriggerFireMode fireMode = activation == null
                    ? MapDesignLibrary.TriggerFireMode.ON_ENTRY
                    : activation.fireMode();
            QuestActionOption quest = (QuestActionOption) questBox.getSelectedItem();
            String questId = fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS && quest != null
                    ? quest.questId()
                    : "";
            QuestProgressOption progress = (QuestProgressOption) progressBox.getSelectedItem();
            String requiredProgress = fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS
                    && progress != null
                    ? progress.progressId()
                    : "";
            if (id.isBlank()) {
                showAdaptiveTextMessageDialog(
                        parent,
                        "Trigger id cannot be blank.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS && questId.isBlank()) {
                showAdaptiveTextMessageDialog(
                        parent,
                        "Choose an authored quest for a quest-stage trigger.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS && requiredProgress.isBlank()) {
                showAdaptiveTextMessageDialog(
                        parent,
                        "Choose the required quest progress.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return new TriggerSettings(
                    id,
                    fireMode,
                    oneShotBox.isSelected(),
                    questId,
                    requiredProgress);
        }
        return null;
    }

    private void manageTriggers() {
        if (design.triggers().isEmpty()) {
            setStatus("No triggers to manage.");
            return;
        }

        DefaultListModel<MapDesignLibrary.MapTrigger> model = new DefaultListModel<>();
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            model.addElement(trigger);
        }

        JList<MapDesignLibrary.MapTrigger> triggerList = new JList<>(model);
        triggerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        triggerList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(triggerLabel(value));
            label.setOpaque(true);
            label.setBackground(selected ? new Color(65, 95, 140) : list.getBackground());
            label.setForeground(selected ? Color.WHITE : list.getForeground());
            return label;
        });

        JButton renameButton = new JButton("Rename");
        JButton configureButton = new JButton("Configure");
        JButton wireButton = new JButton("Wire Targets");
        JButton removeTargetButton = new JButton("Remove Target");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel();
        buttons.add(renameButton);
        buttons.add(configureButton);
        buttons.add(wireButton);
        buttons.add(removeTargetButton);
        buttons.add(deleteButton);
        buttons.add(closeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(triggerList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {});
        var dialog = pane.createDialog(this, "Manage Triggers");

        renameButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null) {
                return;
            }
            String newId = showResizableInputDialog(
                    dialog,
                    "Trigger id",
                    "Rename Trigger",
                    trigger.id());
            if (newId == null || newId.trim().isBlank()) {
                return;
            }
            newId = newId.trim();
            if (!newId.equals(trigger.id()) && findTrigger(newId) != null) {
                setStatus("Trigger id already exists: " + newId + ".");
                return;
            }
            replaceTrigger(trigger, new MapDesignLibrary.MapTrigger(
                    newId,
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    trigger.actions()));
            refreshTriggerList(model);
            setStatus("Renamed trigger to " + newId + ".");
            mapCanvas.repaint();
        });

        configureButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null) {
                return;
            }
            TriggerSettings settings = showTriggerSettings(dialog, trigger, "Configure Trigger");
            if (settings == null) {
                return;
            }
            if (!settings.id().equals(trigger.id()) && findTrigger(settings.id()) != null) {
                setStatus("Trigger id already exists: " + settings.id() + ".");
                return;
            }
            captureHistory("configure trigger");
            MapDesignLibrary.TriggerActionType actionType = settings
                    .fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS
                            ? MapDesignLibrary.TriggerActionType.OPEN_DOOR
                            : MapDesignLibrary.TriggerActionType.CLOSE_DOOR;
            List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                    .map(action -> new MapDesignLibrary.TriggerAction(
                            actionType,
                            action.targetX(),
                            action.targetY()))
                    .toList();
            replaceTrigger(trigger, new MapDesignLibrary.MapTrigger(
                    settings.id(),
                    trigger.x(),
                    trigger.y(),
                    settings.fireMode(),
                    settings.oneShot(),
                    settings.requiredQuestId(),
                    settings.requiredQuestProgress(),
                    actions));
            refreshTriggerList(model);
            markDirty(true);
            setStatus("Configured trigger " + settings.id() + ".");
            mapCanvas.repaint();
        });

        wireButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null) {
                return;
            }
            wiringTriggerId = trigger.id();
            pendingTriggerId = "";
            paintModeBox.setSelectedItem(PaintMode.WIRE_TRIGGER);
            String action = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS ? "open" : "close";
            setStatus("Click door tiles to wire " + action + " targets for " + trigger.id() + ".");
            dialog.dispose();
        });

        removeTargetButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null || trigger.actions().isEmpty()) {
                return;
            }
            String[] targets = trigger.actions().stream()
                    .map(action -> action.targetX() + "," + action.targetY())
                    .toArray(String[]::new);
            String selected = showResizableChoiceDialog(
                    dialog,
                    "Remove target",
                    "Trigger Target",
                    targets,
                    targets[0]);
            if (selected == null) {
                return;
            }
            removeTriggerTarget(trigger.id(), selected);
            refreshTriggerList(model);
            setStatus("Removed trigger target " + selected + ".");
            mapCanvas.repaint();
        });

        deleteButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null) {
                return;
            }
            deleteTrigger(trigger);
            refreshTriggerList(model);
            refreshContentBrowser();
            mapCanvas.repaint();
        });

        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private void deleteTrigger(MapDesignLibrary.MapTrigger trigger) {
        if (trigger == null) {
            return;
        }

        int result = showAdaptiveTextConfirmDialog(
                this,
                "Delete trigger " + trigger.id() + "?",
                "Delete Trigger",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        captureHistory("delete trigger");
        design.triggers().remove(trigger);
        mapCanvas.repaint();
        markDirty(true);
        setStatus("Deleted trigger " + trigger.id() + ".");
    }

    private String nextTriggerId() {
        int index = design.triggers().size() + 1;
        while (findTrigger("trigger_" + index) != null) {
            index++;
        }
        return "trigger_" + index;
    }

    private MapDesignLibrary.MapTrigger findTrigger(String id) {
        if (id == null) {
            return null;
        }
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            if (id.equals(trigger.id())) {
                return trigger;
            }
        }
        return null;
    }

    private String triggerLabel(MapDesignLibrary.MapTrigger trigger) {
        if (trigger == null) {
            return "";
        }
        String activation = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS
                ? "Quest " + trigger.requiredQuestId() + " reaches " + trigger.requiredQuestProgress()
                : "On Entry";
        return trigger.id() + " @ " + trigger.x() + "," + trigger.y()
                + " -> " + trigger.actions().size() + " door target(s)"
                + " [" + activation + (trigger.oneShot() ? ", One Shot" : "") + "]";
    }

    private void refreshTriggerList(DefaultListModel<MapDesignLibrary.MapTrigger> model) {
        model.clear();
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            model.addElement(trigger);
        }
    }

    private void replaceTrigger(MapDesignLibrary.MapTrigger oldTrigger, MapDesignLibrary.MapTrigger newTrigger) {
        int index = design.triggers().indexOf(oldTrigger);
        if (index >= 0) {
            design.triggers().set(index, newTrigger);
        }
    }

    private void removeTriggerTarget(String triggerId, String targetText) {
        MapDesignLibrary.MapTrigger trigger = findTrigger(triggerId);
        if (trigger == null || targetText == null) {
            return;
        }

        String[] parts = targetText.split(",");
        if (parts.length != 2) {
            return;
        }

        try {
            int targetX = Integer.parseInt(parts[0].trim());
            int targetY = Integer.parseInt(parts[1].trim());
            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>(trigger.actions());
            actions.removeIf(action -> action.targetX() == targetX && action.targetY() == targetY);
            replaceTrigger(trigger, new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    actions));
        } catch (NumberFormatException ignored) {
            // Ignore malformed editor selection.
        }
    }

    private void showAuthoringHelp() {
        JTextArea helpArea = new JTextArea(authoringHelpText(), 28, 82);
        helpArea.setEditable(false);
        helpArea.setLineWrap(true);
        helpArea.setWrapStyleWord(true);
        helpArea.setCaretPosition(0);

        showScrollableMessageDialog(
                helpArea,
                "Aether Construction Kit Authoring Help",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String authoringHelpText() {
        return """
                Dialogue Basics
                - The top text box is the NPC's default repeat text when no ::repeatTalk node is defined.
                - Use ::firstTalk or ::firstTime for the first thing the NPC says only once.
                - Use ::repeatTalk for the menu shown whenever the player talks to the NPC again.
                - Define nodes with ::node_id.
                - Choices use: Button text => response text
                - To jump to another node, use: Button text => node_id
                - Choices placed before the first ::node are also reusable choices for the default repeat text.
                - Topic responses automatically receive an Other topics button when a repeat menu exists.

                Reusable Topic Example
                ::firstTalk
                Did I just see you step out of the catacombs? You look lost.
                - Where am I? => where_am_i

                ::repeatTalk
                What else would you like to know?
                - Where am I? => where_am_i
                - What can I do around here? => local_work

                ::where_am_i
                You are in a village. Probably the safest one you will find for some time.

                ::local_work
                The villagers always need help gathering food and repairing the walls.

                Quest Stages
                - A quest choice uses: "quest_id"[nextStage] Button => node_id
                - It only appears when the quest is currently at the previous stage.
                - Example: "warrior_training"[2] I caught a fish => cooking_task
                  This appears only while warrior_training is stage 1.

                Item, Gold, And XP Tags
                - [hasItem=Raw Fish] hides the option unless the player has that item.
                - [takeItem=Cooked Fish] removes one matching item when clicked.
                - [giveItem=Leather Cap] gives an item reward.
                - [giveGold=25] gives gold.
                - [giveXp=Cooking:45] gives skill XP. Skill names may use display names or enum names.

                First Talk Example
                ::firstTalk
                I am Captain Arlen. If you want to survive, learn the basics.
                - "warrior_training"[1] Begin training => fishing_task

                ::fishing_task
                Catch a fish from the nearby shoal, then return to me.
                - [hasItem=Raw Fish] "warrior_training"[2] I caught one => cooking_task

                ::cooking_task
                Use the raw fish, cook it at the campfire, then bring it back.
                - [hasItem=Cooked Fish] [takeItem=Cooked Fish] "warrior_training"[3] Here is the cooked fish => mining_task

                ::mining_task
                Mine copper ore from the nearby rock. Keep the ore.
                - [hasItem=Copper Ore] "warrior_training"[4] I mined copper => smelting_task

                ::smelting_task
                Smelt the copper ore into a copper bar.
                - [hasItem=Copper Bar] "warrior_training"[5] Here is a bar => smithing_task

                ::smithing_task
                Use the copper bar at the anvil and make a copper dagger.
                - [hasItem=Copper Dagger] "warrior_training"[6] I made the dagger => complete

                ::complete
                Good. Take this old gear and keep moving.
                - [giveItem=Leather Cap] [giveXp=Smithing:25] Thank you => Training complete.

                Map Links
                - Use Create Map Link, choose a target map, choose target coordinates, then paint the link onto a tile.
                - At runtime the player will be prompted before traveling.

                Map Inspection
                - Right-click a map tile to inspect it.
                - Right-click a placement, trigger, or trigger wire target to reveal it in the Content Browser.

                Keyboard Shortcuts
                - Ctrl+S saves the current map.
                - Ctrl+O loads a map.
                - Ctrl+Z undoes map edits.
                - Ctrl+Y or Ctrl+Shift+Z redoes map edits.
                - Delete removes the selected content browser entry.
                - Ctrl+D duplicates the selected content browser entry when supported.
                - Ctrl+F finds the selected content on the map.
                - Ctrl+Enter selects the current content browser entry for placement.
                - F7 validates the map.

                Source Launch Commands
                - Run the Construction Kit with: mvn -Pconstruction-kit exec:java
                - Run the Sound Designer with: mvn -Psound-designer exec:java
                - Run the Song Designer with: mvn -Psong-designer exec:java
                - Run the Sprite Sheet Splitter with: mvn -Psprite-sheet-splitter exec:java
                """;
    }

    private String mapLinkPathForEditor(Path path) {
        if (path == null) {
            return "";
        }
        Path absolutePath = path.toAbsolutePath().normalize();
        Path resourceRoot = Path.of("src", "main", "resources").toAbsolutePath().normalize();
        if (absolutePath.startsWith(resourceRoot)) {
            return resourceRoot.relativize(absolutePath).toString().replace('\\', '/');
        }
        return MapDesignLibrary.resourcePathForMap(path);
    }

    private String mapLinkLabel(String id) {
        String[] parts = id.split("\\|");
        return parts.length >= 2 ? parts[1] : id;
    }

    private String normalizeCustomItemImagePath(String rawPath, String itemName) throws IOException {
        return normalizeGeneratedImagePath(rawPath, itemName, "items");
    }

    private String normalizeGeneratedImagePath(String rawPath, String itemName, String folderName) throws IOException {
        String trimmedPath = rawPath == null ? "" : rawPath.trim();
        if (trimmedPath.startsWith("assets/") || trimmedPath.startsWith("assets\\")) {
            return trimmedPath.replace('\\', '/');
        }

        Path source = Path.of(trimmedPath);
        if (!Files.isRegularFile(source)) {
            return trimmedPath.replace('\\', '/');
        }

        String safeFolderName = folderName == null || folderName.isBlank() ? "items" : safeId(folderName);
        Path targetFolder = Path.of("src", "main", "resources", "assets", "images", "generated", safeFolderName);
        Files.createDirectories(targetFolder);
        String fileName = safeId(itemName) + getFileExtension(source.getFileName().toString());
        Path target = targetFolder.resolve(fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return "assets/images/generated/" + safeFolderName + "/" + fileName;
    }

    private String normalizeOptionalCustomItemImagePath(String rawPath, String itemName) throws IOException {
        String trimmedPath = rawPath == null ? "" : rawPath.trim();
        if (trimmedPath.isBlank()) {
            return "";
        }

        return normalizeCustomItemImagePath(trimmedPath, itemName);
    }

    private String getFileExtension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? ".png" : fileName.substring(index);
    }

    private String nextAuthoredInteractionId(String speakerName) {
        String base = safeId(speakerName);
        if (base.isBlank()) {
            base = "npc";
        }

        String prefix = "authored_dialogue_" + base;
        String candidate = prefix;
        int suffix = 2;

        while (hasAuthoredDialogueId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    private boolean hasAuthoredDialogueId(String interactionId) {
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            if (dialogue.interactionId().equals(interactionId)) {
                return true;
            }
        }

        return false;
    }

    private String nextAuthoredQuestId(String questName) {
        String base = safeId(questName);
        if (base.isBlank()) {
            base = "quest";
        }

        String prefix = "authored_quest_" + base;
        String candidate = prefix;
        int suffix = 2;

        while (hasAuthoredQuestId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    private boolean hasAuthoredQuestId(String questId) {
        for (MapDesignLibrary.AuthoredQuest quest : design.authoredQuests()) {
            if (quest.questId().equals(questId)) {
                return true;
            }
        }

        return false;
    }

    private String nextCustomItemId(String itemName) {
        String base = safeId(itemName);
        if (base.isBlank()) {
            base = "item";
        }

        String prefix = "custom_item_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomItemId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomItemId(String itemId) {
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            if (item.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCustomMobId(String mobName) {
        String base = safeId(mobName);
        if (base.isBlank()) {
            base = "mob";
        }

        String prefix = "custom_mob_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomMobId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomMobId(String mobId) {
        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            if (mob.mobId().equals(mobId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCustomLimbId(String limbName) {
        String base = safeId(limbName);
        if (base.isBlank()) {
            base = "limb";
        }

        String prefix = "custom_limb_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomLimbId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomLimbId(String limbId) {
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            if (limb.limbId().equals(limbId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCustomNpcId(String npcName) {
        String base = safeId(npcName);
        if (base.isBlank()) {
            base = "npc";
        }

        String prefix = "npc_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomNpcId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomNpcId(String npcId) {
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            if (npc.npcId().equals(npcId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCustomFurnitureId(String furnitureName) {
        String base = safeId(furnitureName);
        if (base.isBlank()) {
            base = "furniture";
        }

        String prefix = "furniture_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomFurnitureId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomFurnitureId(String furnitureId) {
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : design.customFurniture()) {
            if (furniture.furnitureId().equals(furnitureId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCustomGatheringNodeId(String nodeName) {
        String base = safeId(nodeName);
        if (base.isBlank()) {
            base = "resource_node";
        }

        String prefix = "node_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCustomGatheringNodeId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCustomGatheringNodeId(String nodeId) {
        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            if (node.nodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCraftingRecipeId(String recipeName) {
        String base = safeId(recipeName);
        if (base.isBlank()) {
            base = "crafting_recipe";
        }

        String prefix = "recipe_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCraftingRecipeId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCraftingRecipeId(String recipeId) {
        for (MapDesignLibrary.CraftingRecipe recipe : design.craftingRecipes()) {
            if (recipe.recipeId().equals(recipeId)) {
                return true;
            }
        }
        return false;
    }

    private String nextCookingRecipeId(String recipeName) {
        String base = safeId(recipeName);
        if (base.isBlank()) {
            base = "cooking_recipe";
        }

        String prefix = "cooking_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCookingRecipeId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCookingRecipeId(String recipeId) {
        for (MapDesignLibrary.CustomCookingRecipe recipe : design.customCookingRecipes()) {
            if (recipe.recipeId().equals(recipeId)) {
                return true;
            }
        }
        return false;
    }

    private GearMaterial[] metalMaterials() {
        List<GearMaterial> materials = new ArrayList<>();
        for (GearMaterial material : GearMaterial.values()) {
            if (material.getFamily() == GearMaterial.MaterialFamily.METAL) {
                materials.add(material);
            }
        }
        return materials.toArray(new GearMaterial[0]);
    }

    private GearMaterial[] woodMaterials() {
        List<GearMaterial> materials = new ArrayList<>();
        for (GearMaterial material : GearMaterial.values()) {
            if (material.getFamily() == GearMaterial.MaterialFamily.WOOD) {
                materials.add(material);
            }
        }
        return materials.toArray(new GearMaterial[0]);
    }

    private void setGatheringMaterialModel(
            JComboBox<GearMaterial> materialBox,
            MapDesignLibrary.GatheringNodeType nodeType) {
        if (materialBox == null) {
            return;
        }

        GearMaterial selected = (GearMaterial) materialBox.getSelectedItem();
        GearMaterial.MaterialFamily desiredFamily = nodeType == MapDesignLibrary.GatheringNodeType.TREE
                ? GearMaterial.MaterialFamily.WOOD
                : GearMaterial.MaterialFamily.METAL;
        GearMaterial[] materials = desiredFamily == GearMaterial.MaterialFamily.WOOD
                ? woodMaterials()
                : metalMaterials();
        materialBox.setModel(new DefaultComboBoxModel<>(materials));

        if (selected != null && selected.getFamily() == desiredFamily) {
            materialBox.setSelectedItem(selected);
        } else if (materials.length > 0) {
            materialBox.setSelectedIndex(0);
        }
    }

    private List<String> defaultFishingFramePaths() {
        return DEFAULT_FISHING_FRAME_PATHS;
    }

    private String safeId(String value) {
        return value == null
                ? ""
                : value.toLowerCase()
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", "");
    }

    private StatTargetOption[] statTargetOptions() {
        List<StatTargetOption> options = new ArrayList<>();
        options.add(new StatTargetOption("Default", null));

        for (PlayerStat stat : PlayerStat.values()) {
            options.add(new StatTargetOption(stat.getDisplayName(), stat));
        }

        return options.toArray(new StatTargetOption[0]);
    }

    private QuestActionOption[] questActionOptions() {
        List<QuestActionOption> options = new ArrayList<>();
        options.add(new QuestActionOption("None", ""));

        for (MapDesignLibrary.AuthoredQuest quest : design.authoredQuests()) {
            options.add(new QuestActionOption(quest.displayName(), quest.questId()));
        }

        return options.toArray(new QuestActionOption[0]);
    }

    private QuestProgressOption[] questProgressOptions(String questId) {
        List<QuestProgressOption> options = new ArrayList<>();
        if (questId == null || questId.isBlank()) {
            return new QuestProgressOption[0];
        }
        options.add(new QuestProgressOption("Quest accepted", "ACTIVE"));
        design.authoredQuests().stream()
                .filter(quest -> quest.questId().equals(questId))
                .findFirst()
                .ifPresent(quest -> quest.stages().forEach(stage -> options.add(
                        new QuestProgressOption(
                                stage.title() + "  [" + stage.stageId() + "]",
                                "STAGE:" + stage.stageId()))));
        options.add(new QuestProgressOption("Quest completed", "COMPLETED"));
        return options.toArray(new QuestProgressOption[0]);
    }

    private DialogueOption[] dialogueOptions() {
        List<DialogueOption> options = new ArrayList<>();
        options.add(new DialogueOption("None", ""));

        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            options.add(new DialogueOption(dialogue.speakerName(), dialogue.interactionId()));
        }

        return options.toArray(new DialogueOption[0]);
    }

    private void selectQuestActionOption(JComboBox<QuestActionOption> questActionBox, String questId) {
        String safeQuestId = questId == null ? "" : questId;
        for (int i = 0; i < questActionBox.getItemCount(); i++) {
            QuestActionOption option = questActionBox.getItemAt(i);
            if (option != null && safeQuestId.equals(option.questId())) {
                questActionBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectDialogueOption(JComboBox<DialogueOption> dialogueBox, String interactionId) {
        String safeInteractionId = interactionId == null ? "" : interactionId;
        for (int i = 0; i < dialogueBox.getItemCount(); i++) {
            DialogueOption option = dialogueBox.getItemAt(i);
            if (option != null && safeInteractionId.equals(option.interactionId())) {
                dialogueBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectStatTargetOption(JComboBox<StatTargetOption> statTargetBox, PlayerStat stat) {
        for (int i = 0; i < statTargetBox.getItemCount(); i++) {
            StatTargetOption option = statTargetBox.getItemAt(i);
            if (option != null && option.stat() == stat) {
                statTargetBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void createNewWorld() {
        JTextField nameField = new JTextField("New World", 22);
        JTextField idField = new JTextField("new_world", 22);
        JSpinner chunkWidthField = new JSpinner(new SpinnerNumberModel(
                WorldManifestLibrary.DEFAULT_CHUNK_SIZE,
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JSpinner chunkHeightField = new JSpinner(new SpinnerNumberModel(
                WorldManifestLibrary.DEFAULT_CHUNK_SIZE,
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JPanel fields = createFormPanel();
        addFormRow(fields, "World Name", nameField);
        addFormRow(fields, "World ID", idField);
        addFormRow(fields, "Chunk Width", chunkWidthField);
        addFormRow(fields, "Chunk Height", chunkHeightField);

        if (showScrollableFormDialog(fields, "New Open World") != JOptionPane.OK_OPTION) {
            return;
        }

        String worldId = WorldManifestLibrary.safeId(idField.getText());
        Path manifestPath = WorldManifestLibrary.defaultManifestPath(worldId);
        if (Files.exists(manifestPath)) {
            setStatus("World already exists: " + manifestPath + ".");
            return;
        }

        try {
            WorldManifest manifest = WorldManifestLibrary.create(
                    worldId,
                    nameField.getText(),
                    ((Number) chunkWidthField.getValue()).intValue(),
                    ((Number) chunkHeightField.getValue()).intValue());
            ChunkCoordinate origin = new ChunkCoordinate(0, 0);
            Path chunkPath = WorldManifestLibrary.defaultChunkPath(manifestPath, origin);
            MapDesignLibrary.MapDesign chunk = createOpenWorldChunk(
                    manifest.chunkWidth(),
                    manifest.chunkHeight(),
                    manifest.displayName() + " " + origin);
            MapDesignLibrary.save(chunk, chunkPath);
            manifest = manifest.withChunk(origin, WorldManifestLibrary.relativeChunkPath(manifestPath, chunkPath));
            WorldManifestLibrary.save(manifest, manifestPath);
            activateWorld(manifestPath, manifest, origin);
            setStatus("Created open world " + manifest.displayName() + " with chunk " + origin + ".");
        } catch (IOException exception) {
            setStatus("World creation failed: " + exception.getMessage());
        }
    }

    private void openWorld() {
        try {
            Files.createDirectories(WorldManifestLibrary.WORLD_FOLDER);
            JFileChooser chooser = new JFileChooser(WorldManifestLibrary.WORLD_FOLDER.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Aether world manifest", "properties"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path path = chooser.getSelectedFile().toPath();
            WorldManifest manifest = WorldManifestLibrary.load(path);
            ChunkCoordinate start = manifest.chunkForGlobal(manifest.startX(), manifest.startY());
            activateWorld(path, manifest, start);
            setStatus("Opened world " + manifest.displayName() + " at chunk " + start + ".");
        } catch (IOException exception) {
            setStatus("Open world failed: " + exception.getMessage());
        }
    }

    private void activateWorld(Path manifestPath, WorldManifest manifest, ChunkCoordinate coordinate)
            throws IOException {
        currentWorldManifestPath = manifestPath;
        activeWorld = manifest;
        activeWorldChunk = coordinate;
        activeWorldContent = WorldManifestLibrary.loadWorldContent(manifest, manifestPath);
        loadWorldChunk(coordinate);
    }

    private void loadWorldChunk(ChunkCoordinate coordinate) throws IOException {
        String relativePath = activeWorld == null ? null : activeWorld.chunks().get(coordinate);
        if (relativePath == null) {
            throw new IOException("World has no chunk at " + coordinate + ".");
        }
        Path chunkPath = WorldManifestLibrary.resolveChunkPath(currentWorldManifestPath, relativePath);
        design = MapDesignLibrary.load(chunkPath);
        currentMapPath = chunkPath;
        activeWorldChunk = coordinate;
        loadSharedContentIntoDesign();
        mergeSharedContent(activeWorldContent);
        undoStack.clear();
        redoStack.clear();
        refreshWorldNeighbors();
        syncEditorFromDesign();
        markDirty(false);
        clearAutosaveRecovery();
        mapCanvas.revalidate();
        mapCanvas.repaint();
    }

    private void refreshWorldNeighbors() {
        worldNeighborDesigns.clear();
        if (activeWorld == null || activeWorldChunk == null) {
            return;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(activeWorldChunk.x() + dx, activeWorldChunk.y() + dy);
                String relativePath = activeWorld.chunks().get(coordinate);
                if (relativePath == null) {
                    continue;
                }
                try {
                    MapDesignLibrary.MapDesign neighbor = coordinate.equals(activeWorldChunk)
                            ? design
                            : MapDesignLibrary.load(
                                    WorldManifestLibrary.resolveChunkPath(currentWorldManifestPath, relativePath));
                    worldNeighborDesigns.put(coordinate, neighbor);
                } catch (IOException exception) {
                    setStatus("Chunk preview failed for " + coordinate + ": " + exception.getMessage());
                }
            }
        }
    }

    private boolean switchWorldChunk(ChunkCoordinate coordinate) {
        if (activeWorld == null || coordinate == null || coordinate.equals(activeWorldChunk)) {
            return true;
        }
        if (dirty) {
            int choice = showAdaptiveTextConfirmDialog(
                    this,
                    "Save changes to chunk " + activeWorldChunk + " before switching?\n"
                            + "Yes = Save, No = Discard, Cancel = Stay",
                    "Unsaved Chunk",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return false;
            }
            if (choice == JOptionPane.YES_OPTION && !saveCurrentWorldChunk()) {
                return false;
            }
        }
        if (!activeWorld.chunks().containsKey(coordinate)) {
            int create = showAdaptiveTextConfirmDialog(
                    this,
                    "Create missing chunk " + coordinate + "?",
                    "Create World Chunk",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (create != JOptionPane.OK_OPTION || !createWorldChunk(coordinate)) {
                return false;
            }
        }
        try {
            loadWorldChunk(coordinate);
            setStatus("Editing world chunk " + coordinate + ".");
            return true;
        } catch (IOException exception) {
            setStatus("Chunk switch failed: " + exception.getMessage());
            return false;
        }
    }

    private boolean saveCurrentWorldChunk() {
        if (activeWorld == null || currentWorldManifestPath == null || activeWorldChunk == null) {
            return false;
        }
        String relativePath = activeWorld.chunks().get(activeWorldChunk);
        if (relativePath == null) {
            setStatus("Active chunk is not registered in the world.");
            return false;
        }
        try {
            syncThemes();
            if (!confirmSaveWithValidationIssues()) {
                return false;
            }
            Path chunkPath = WorldManifestLibrary.resolveChunkPath(currentWorldManifestPath, relativePath);
            MapDesignLibrary.save(design, chunkPath);
            WorldManifestLibrary.save(activeWorld, currentWorldManifestPath);
            activeWorldContent = WorldManifestLibrary.loadWorldContent(activeWorld, currentWorldManifestPath);
            currentMapPath = chunkPath;
            markDirty(false);
            clearAutosaveRecovery();
            refreshWorldNeighbors();
            saveSharedContentFromDesign();
            setStatus("Saved world chunk " + activeWorldChunk + ".");
            return true;
        } catch (IOException exception) {
            setStatus("World chunk save failed: " + exception.getMessage());
            return false;
        }
    }

    private void addWorldChunk() {
        if (activeWorld == null || activeWorldChunk == null) {
            setStatus("Open a world before adding chunks.");
            return;
        }
        ChunkCoordinate coordinate = promptChunkCoordinate(
                "Add World Chunk",
                new ChunkCoordinate(activeWorldChunk.x() + 1, activeWorldChunk.y()));
        if (coordinate != null && createWorldChunk(coordinate)) {
            refreshWorldNeighbors();
            mapCanvas.revalidate();
            mapCanvas.repaint();
            setStatus("Added chunk " + coordinate + ".");
        }
    }

    private boolean createWorldChunk(ChunkCoordinate coordinate) {
        if (activeWorld.chunks().containsKey(coordinate)) {
            setStatus("Chunk already exists at " + coordinate + ".");
            return false;
        }
        try {
            Path chunkPath = WorldManifestLibrary.defaultChunkPath(currentWorldManifestPath, coordinate);
            MapDesignLibrary.save(
                    createOpenWorldChunk(
                            activeWorld.chunkWidth(),
                            activeWorld.chunkHeight(),
                            activeWorld.displayName() + " " + coordinate),
                    chunkPath);
            activeWorld = activeWorld.withChunk(
                    coordinate,
                    WorldManifestLibrary.relativeChunkPath(currentWorldManifestPath, chunkPath));
            WorldManifestLibrary.save(activeWorld, currentWorldManifestPath);
            return true;
        } catch (IOException exception) {
            setStatus("Chunk creation failed: " + exception.getMessage());
            return false;
        }
    }

    private void removeWorldChunk() {
        if (activeWorld == null || activeWorldChunk == null) {
            setStatus("Open a world before removing chunks.");
            return;
        }
        ChunkCoordinate coordinate = promptChunkCoordinate("Remove World Chunk", activeWorldChunk);
        if (coordinate == null) {
            return;
        }
        if (coordinate.equals(activeWorldChunk)) {
            setStatus("Switch away from a chunk before removing it.");
            return;
        }
        if (!activeWorld.chunks().containsKey(coordinate)) {
            setStatus("No chunk exists at " + coordinate + ".");
            return;
        }
        ChunkCoordinate startChunk = activeWorld.chunkForGlobal(activeWorld.startX(), activeWorld.startY());
        if (coordinate.equals(startChunk)) {
            setStatus("Move the world start before removing its chunk.");
            return;
        }
        activeWorld = activeWorld.withoutChunk(coordinate);
        try {
            WorldManifestLibrary.save(activeWorld, currentWorldManifestPath);
            refreshWorldNeighbors();
            mapCanvas.revalidate();
            mapCanvas.repaint();
            setStatus("Detached chunk " + coordinate + " from the world; its file was retained.");
        } catch (IOException exception) {
            setStatus("Chunk removal failed: " + exception.getMessage());
        }
    }

    private void editWorldSettings() {
        if (activeWorld == null) {
            setStatus("Open a world before editing world settings.");
            return;
        }
        JTextField nameField = new JTextField(activeWorld.displayName(), 24);
        JSpinner startXField = new JSpinner(new SpinnerNumberModel(activeWorld.startX(), -1_000_000, 1_000_000, 1));
        JSpinner startYField = new JSpinner(new SpinnerNumberModel(activeWorld.startY(), -1_000_000, 1_000_000, 1));
        JTextArea descriptionArea = new JTextArea(activeWorld.description(), 5, 28);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JPanel fields = createFormPanel();
        addFormRow(fields, "World Name", nameField);
        addFormRow(fields, "Global Start X", startXField);
        addFormRow(fields, "Global Start Y", startYField);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(fields, BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        if (showScrollableFormDialog(panel, "World Settings") != JOptionPane.OK_OPTION) {
            return;
        }
        int startX = ((Number) startXField.getValue()).intValue();
        int startY = ((Number) startYField.getValue()).intValue();
        ChunkCoordinate startChunk = activeWorld.chunkForGlobal(startX, startY);
        if (!activeWorld.chunks().containsKey(startChunk)) {
            setStatus("World start resolves to missing chunk " + startChunk + ".");
            return;
        }
        activeWorld = activeWorld.withMetadata(
                nameField.getText(),
                descriptionArea.getText(),
                startX,
                startY);
        try {
            WorldManifestLibrary.save(activeWorld, currentWorldManifestPath);
            setStatus("Updated world settings.");
        } catch (IOException exception) {
            setStatus("World settings save failed: " + exception.getMessage());
        }
    }

    private void validateWorld() {
        if (activeWorld == null) {
            setStatus("Open a world before validating it.");
            return;
        }
        List<MapDesignLibrary.ValidationIssue> issues = WorldManifestLibrary.validate(activeWorld,
                currentWorldManifestPath);
        if (issues.isEmpty()) {
            showAdaptiveTextMessageDialog(
                    "World validation passed.",
                    "Validate World",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea area = new JTextArea(issues.stream()
                .map(issue -> issue.severity() + ": " + issue.message())
                .collect(java.util.stream.Collectors.joining("\n")), 18, 64);
        area.setEditable(false);
        showScrollableMessageDialog(area, "World Validation", JOptionPane.WARNING_MESSAGE);
    }

    private ChunkCoordinate promptChunkCoordinate(String title, ChunkCoordinate initial) {
        JSpinner xField = new JSpinner(new SpinnerNumberModel(initial.x(), -10_000, 10_000, 1));
        JSpinner yField = new JSpinner(new SpinnerNumberModel(initial.y(), -10_000, 10_000, 1));
        JPanel fields = createFormPanel();
        addFormRow(fields, "Chunk X", xField);
        addFormRow(fields, "Chunk Y", yField);
        if (showScrollableFormDialog(fields, title) != JOptionPane.OK_OPTION) {
            return null;
        }
        return new ChunkCoordinate(
                ((Number) xField.getValue()).intValue(),
                ((Number) yField.getValue()).intValue());
    }

    private MapDesignLibrary.MapDesign createOpenWorldChunk(int width, int height, String title) {
        MapDesignLibrary.MapDesign blank = MapDesignLibrary.createBlank(
                width,
                height,
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem());
        for (int y = 0; y < blank.height(); y++) {
            for (int x = 0; x < blank.width(); x++) {
                blank.tiles()[y][x] = Library.TileType.FLOOR;
            }
        }
        return new MapDesignLibrary.MapDesign(
                blank.width(), blank.height(), title, "", blank.musicPath(), blank.skyboxPath(),
                blank.primaryTheme(), blank.alternateTheme(), blank.tiles(), blank.themeIndexes(),
                blank.mapPaint(), blank.mapGeometry(), blank.mobAreas(), blank.placements(), blank.placedObjects(), blank.authoredDialogues(),
                blank.authoredQuests(), blank.customItems(), blank.customMobs(), blank.customLimbs(),
                blank.customNpcs(), blank.customFurniture(), blank.customGatheringNodes(), blank.customCookingRecipes(),
                blank.craftingRecipes(), blank.triggers(), blank.lightingSettings(), blank.lights(), 1, 1);
    }

    private void createNewMap() {
        if (!promptNewMapSettings()) {
            return;
        }

        undoStack.clear();
        redoStack.clear();
        currentWorldManifestPath = null;
        activeWorld = null;
        activeWorldChunk = null;
        activeWorldContent = null;
        worldNeighborDesigns.clear();
        currentMapPath = null;
        MapDesignLibrary.MapDesign blank = MapDesignLibrary.createBlank(
                ((Number) widthSpinner.getValue()).intValue(),
                ((Number) heightSpinner.getValue()).intValue(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem());
        design = new MapDesignLibrary.MapDesign(
                blank.width(),
                blank.height(),
                mapTitle(),
                blank.description(),
                blank.musicPath(),
                blank.skyboxPath(),
                blank.primaryTheme(),
                blank.primaryTheme(),
                blank.tiles(),
                blank.themeIndexes(),
                blank.mapPaint(),
                blank.mapGeometry(),
                blank.mobAreas(),
                blank.placements(),
                blank.placedObjects(),
                blank.authoredDialogues(),
                blank.authoredQuests(),
                blank.customItems(),
                blank.customMobs(),
                blank.customLimbs(),
                blank.customNpcs(),
                blank.customFurniture(),
                blank.customGatheringNodes(),
                blank.customCookingRecipes(),
                blank.craftingRecipes(),
                blank.triggers(),
                blank.lightingSettings(),
                blank.lights(),
                blank.spawnX(),
                blank.spawnY());
        loadSharedContentIntoDesign();
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Created " + design.displayName() + " " + design.width() + "x" + design.height() + ".");
    }

    private void resizeCurrentMap() {
        if (!promptResizeSettings()) {
            return;
        }

        int newWidth = ((Number) widthSpinner.getValue()).intValue();
        int newHeight = ((Number) heightSpinner.getValue()).intValue();
        if (newWidth == design.width() && newHeight == design.height()) {
            setStatus("Map is already " + newWidth + "x" + newHeight + ".");
            return;
        }

        int droppedPlacements = 0;
        int droppedPlacedObjects = 0;
        int droppedTriggers = 0;
        int droppedTriggerActions = 0;
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (!isInsideDimensions(placement.x(), placement.y(), newWidth, newHeight)) {
                droppedPlacements++;
            }
        }
        for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
            if (!isInsideDimensions(object.x(), object.y(), newWidth, newHeight)) {
                droppedPlacedObjects++;
            }
        }
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            if (!isInsideDimensions(trigger.x(), trigger.y(), newWidth, newHeight)) {
                droppedTriggers++;
                continue;
            }
            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                if (!isInsideDimensions(action.targetX(), action.targetY(), newWidth, newHeight)) {
                    droppedTriggerActions++;
                }
            }
        }

        if (droppedPlacements > 0 || droppedPlacedObjects > 0 || droppedTriggers > 0 || droppedTriggerActions > 0) {
            int result = showAdaptiveTextConfirmDialog(
                    this,
                    "Resize will remove content outside the new bounds:\n"
                            + "- Placements: " + droppedPlacements + "\n"
                            + "- Placed objects: " + droppedPlacedObjects + "\n"
                            + "- Triggers: " + droppedTriggers + "\n"
                            + "- Trigger wire targets: " + droppedTriggerActions + "\n\n"
                            + "Continue?",
                    "Resize Map",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
        }

        captureHistory("resize map");
        MapDesignLibrary.MapDesign blank = MapDesignLibrary.createBlank(
                newWidth,
                newHeight,
                design.primaryTheme(),
                design.primaryTheme());
        int copyWidth = Math.min(design.width(), blank.width());
        int copyHeight = Math.min(design.height(), blank.height());
        for (int y = 0; y < copyHeight; y++) {
            System.arraycopy(design.tiles()[y], 0, blank.tiles()[y], 0, copyWidth);
            System.arraycopy(design.themeIndexes()[y], 0, blank.themeIndexes()[y], 0, copyWidth);
        }

        List<MapDesignLibrary.MapPlacement> placements = design.placements().stream()
                .filter(placement -> isInsideDimensions(placement.x(), placement.y(), blank.width(), blank.height()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<MapDesignLibrary.PlacedObjectInstance> placedObjects = design.placedObjects().stream()
                .filter(object -> isInsideDimensions(object.x(), object.y(), blank.width(), blank.height()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<MapDesignLibrary.MapTrigger> triggers = new ArrayList<>();
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            if (!isInsideDimensions(trigger.x(), trigger.y(), blank.width(), blank.height())) {
                continue;
            }
            List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                    .filter(action -> isInsideDimensions(action.targetX(), action.targetY(), blank.width(),
                            blank.height()))
                    .toList();
            triggers.add(new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    actions));
        }

        int spawnX = Math.min(Math.max(0, design.spawnX()), blank.width() - 1);
        int spawnY = Math.min(Math.max(0, design.spawnY()), blank.height() - 1);
        if (blank.tiles()[spawnY][spawnX].blocksMovement()) {
            Point fallbackSpawn = firstWalkableTile(blank.tiles(), blank.width(), blank.height());
            spawnX = fallbackSpawn.x;
            spawnY = fallbackSpawn.y;
        }

        design = new MapDesignLibrary.MapDesign(
                blank.width(),
                blank.height(),
                design.displayName(),
                design.description(),
                design.musicPath(),
                design.skyboxPath(),
                design.primaryTheme(),
                design.primaryTheme(),
                blank.tiles(),
                blank.themeIndexes(),
                design.mapPaint() == null
                        ? MapPaintData.blank(blank.width(), blank.height())
                        : design.mapPaint().resized(blank.width(), blank.height()),
                design.mapGeometry() == null
                        ? MapGeometryData.blank(blank.width(), blank.height())
                        : design.mapGeometry().resized(blank.width(), blank.height()),
                design.mobAreas() == null
                        ? MobAreaData.blank(blank.width(), blank.height())
                        : design.mobAreas().resized(blank.width(), blank.height()),
                placements,
                placedObjects,
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.craftingRecipes(),
                triggers,
                design.lightingSettings(),
                design.lights().stream()
                        .filter(light -> isInsideDimensions(light.x(), light.y(), blank.width(), blank.height()))
                        .toList(),
                spawnX,
                spawnY);
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Resized map to " + design.width() + "x" + design.height() + ".");
    }

    private boolean promptNewMapSettings() {
        JTextField nameField = new JTextField(mapTitle(), 22);
        JSpinner widthField = new JSpinner(new SpinnerNumberModel(
                ((Number) widthSpinner.getValue()).intValue(),
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JSpinner heightField = new JSpinner(new SpinnerNumberModel(
                ((Number) heightSpinner.getValue()).intValue(),
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JPanel fields = createFormPanel();
        addFormRow(fields, "Name", nameField);
        addFormRow(fields, "Width", widthField);
        addFormRow(fields, "Height", heightField);

        int result = showScrollableFormDialog(fields, "New Map");
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        mapNameField.setText(nameField.getText() == null || nameField.getText().isBlank()
                ? "New Map"
                : nameField.getText().trim());
        widthSpinner.setValue(((Number) widthField.getValue()).intValue());
        heightSpinner.setValue(((Number) heightField.getValue()).intValue());
        return true;
    }

    private boolean promptResizeSettings() {
        JSpinner widthField = new JSpinner(new SpinnerNumberModel(
                design.width(),
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JSpinner heightField = new JSpinner(new SpinnerNumberModel(
                design.height(),
                MIN_DIMENSION,
                MAX_DIMENSION,
                1));
        JPanel fields = createFormPanel();
        addFormRow(fields, "Width", widthField);
        addFormRow(fields, "Height", heightField);

        int result = showScrollableFormDialog(fields, "Resize Map");
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        widthSpinner.setValue(((Number) widthField.getValue()).intValue());
        heightSpinner.setValue(((Number) heightField.getValue()).intValue());
        return true;
    }

    private boolean isInsideDimensions(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private Point firstWalkableTile(Library.TileType[][] tiles, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!tiles[y][x].blocksMovement()) {
                    return new Point(x, y);
                }
            }
        }
        return new Point(0, 0);
    }

    private void saveMap() {
        try {
            if (activeWorld != null && currentWorldManifestPath != null && activeWorldChunk != null) {
                saveCurrentWorldChunk();
                return;
            }
            Files.createDirectories(MapDesignLibrary.MAP_FOLDER);
            JFileChooser chooser = new JFileChooser(MapDesignLibrary.MAP_FOLDER.toFile());
            chooser.setSelectedFile(MapDesignLibrary.MAP_FOLDER.resolve(safeMapFileName()).toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Aether map design", "properties"));

            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            syncThemes();
            if (!confirmSaveWithValidationIssues()) {
                return;
            }

            Path path = ensurePropertiesExtension(chooser.getSelectedFile().toPath());
            MapDesignLibrary.save(design, path);
            currentMapPath = path;
            markDirty(false);
            clearAutosaveRecovery();
            if (saveSharedContentFromDesign()) {
                setStatus("Saved " + path.getFileName() + ".");
            } else {
                setStatus("Saved map, but shared content save failed.");
            }
        } catch (Exception e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void loadMap() {
        try {
            Files.createDirectories(MapDesignLibrary.MAP_FOLDER);
            JFileChooser chooser = new JFileChooser(MapDesignLibrary.MAP_FOLDER.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Aether map design", "properties"));

            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            design = MapDesignLibrary.load(chooser.getSelectedFile().toPath());
            currentMapPath = chooser.getSelectedFile().toPath();
            currentWorldManifestPath = null;
            activeWorld = null;
            activeWorldChunk = null;
            activeWorldContent = null;
            worldNeighborDesigns.clear();
            loadSharedContentIntoDesign();
            undoStack.clear();
            redoStack.clear();
            syncEditorFromDesign();
            markDirty(false);
            clearAutosaveRecovery();
            setStatus("Loaded " + chooser.getSelectedFile().getName() + ".");
        } catch (Exception e) {
            setStatus("Load failed: " + e.getMessage());
        }
    }

    private void syncThemes() {
        design = new MapDesignLibrary.MapDesign(
                design.width(),
                design.height(),
                mapTitle(),
                design.description(),
                design.musicPath(),
                design.skyboxPath(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                design.tiles(),
                design.themeIndexes(),
                design.mapPaint(),
                design.mapGeometry(),
                design.mobAreas(),
                design.placements(),
                design.placedObjects(),
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.craftingRecipes(),
                design.triggers(),
                design.lightingSettings(),
                design.lights(),
                design.spawnX(),
                design.spawnY());
    }

    private void editMetadata() {
        JTextField titleField = new JTextField(mapTitle(), 24);
        JTextField musicField = new JTextField(design.musicPath(), 28);
        SkyboxSpec skybox = SkyboxSpec.parseOrDefault(design.skyboxPath());
        JTextField skyboxBackField = new JTextField(skybox.back(), 28);
        JTextField skyboxBottomField = new JTextField(skybox.bottom(), 28);
        JTextField skyboxFrontField = new JTextField(skybox.front(), 28);
        JTextField skyboxLeftField = new JTextField(skybox.left(), 28);
        JTextField skyboxRightField = new JTextField(skybox.right(), 28);
        JTextField skyboxTopField = new JTextField(skybox.top(), 28);
        MapLightingSettings lighting = design.lightingSettings();
        JCheckBox lightingEnabledBox = new JCheckBox("Lighting enabled", lighting.lightingEnabled());
        JTextField ambientColorField = new JTextField(MapLightingSettings.colorHex(lighting.ambientColorRgb()), 10);
        JSpinner ambientIntensitySpinner = new JSpinner(
                new SpinnerNumberModel(lighting.ambientIntensity(), 0.0, 2.0, 0.05));
        JCheckBox fogEnabledBox = new JCheckBox("Fog enabled", lighting.fogEnabled());
        JTextField fogColorField = new JTextField(MapLightingSettings.colorHex(lighting.fogColorRgb()), 10);
        JSpinner fogDensitySpinner = new JSpinner(new SpinnerNumberModel(lighting.fogDensity(), 0.0, 1.0, 0.005));
        JButton musicBrowseButton = new JButton("Browse");
        JButton skyboxBackBrowseButton = new JButton("Browse");
        JButton skyboxBottomBrowseButton = new JButton("Browse");
        JButton skyboxFrontBrowseButton = new JButton("Browse");
        JButton skyboxLeftBrowseButton = new JButton("Browse");
        JButton skyboxRightBrowseButton = new JButton("Browse");
        JButton skyboxTopBrowseButton = new JButton("Browse");
        JTextArea descriptionArea = new JTextArea(design.description(), 6, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        musicBrowseButton.addActionListener(event -> browsePathInto(musicField));
        skyboxBackBrowseButton.addActionListener(event -> browsePathInto(skyboxBackField));
        skyboxBottomBrowseButton.addActionListener(event -> browsePathInto(skyboxBottomField));
        skyboxFrontBrowseButton.addActionListener(event -> browsePathInto(skyboxFrontField));
        skyboxLeftBrowseButton.addActionListener(event -> browsePathInto(skyboxLeftField));
        skyboxRightBrowseButton.addActionListener(event -> browsePathInto(skyboxRightField));
        skyboxTopBrowseButton.addActionListener(event -> browsePathInto(skyboxTopField));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = createFormPanel();
        fields.add(formRow("Title", titleField));
        fields.add(formRow("Chunk Ambience", pathFieldPanel(musicField, musicBrowseButton)));
        fields.add(formRow("Skybox Back", pathFieldPanel(skyboxBackField, skyboxBackBrowseButton)));
        fields.add(formRow("Skybox Bottom", pathFieldPanel(skyboxBottomField, skyboxBottomBrowseButton)));
        fields.add(formRow("Skybox Front", pathFieldPanel(skyboxFrontField, skyboxFrontBrowseButton)));
        fields.add(formRow("Skybox Left", pathFieldPanel(skyboxLeftField, skyboxLeftBrowseButton)));
        fields.add(formRow("Skybox Right", pathFieldPanel(skyboxRightField, skyboxRightBrowseButton)));
        fields.add(formRow("Skybox Top", pathFieldPanel(skyboxTopField, skyboxTopBrowseButton)));
        fields.add(formRow("Lighting", lightingEnabledBox));
        fields.add(formRow("Ambient Color", ambientColorField));
        fields.add(formRow("Ambient Intensity", ambientIntensitySpinner));
        fields.add(formRow("Fog", fogEnabledBox));
        fields.add(formRow("Fog Color", fogColorField));
        fields.add(formRow("Fog Density", fogDensitySpinner));
        panel.add(fields, BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

        int result = showScrollableFormDialog(panel, "Map Metadata");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isBlank()) {
            setStatus("Map title cannot be blank.");
            return;
        }

        captureHistory("metadata");
        mapNameField.setText(title);
        MapLightingSettings updatedLighting = new MapLightingSettings(
                lightingEnabledBox.isSelected(),
                MapLightingSettings.parseColor(ambientColorField.getText(), lighting.ambientColorRgb()),
                ((Number) ambientIntensitySpinner.getValue()).doubleValue(),
                fogEnabledBox.isSelected(),
                MapLightingSettings.parseColor(fogColorField.getText(), lighting.fogColorRgb()),
                ((Number) fogDensitySpinner.getValue()).doubleValue());
        design = new MapDesignLibrary.MapDesign(
                design.width(),
                design.height(),
                title,
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                musicField.getText() == null ? "" : musicField.getText().trim(),
                new SkyboxSpec(
                        skyboxBackField.getText(),
                        skyboxBottomField.getText(),
                        skyboxFrontField.getText(),
                        skyboxLeftField.getText(),
                        skyboxRightField.getText(),
                        skyboxTopField.getText()
                ).encode(),
                design.primaryTheme(),
                design.primaryTheme(),
                design.tiles(),
                design.themeIndexes(),
                design.mapPaint(),
                design.mapGeometry(),
                design.mobAreas(),
                design.placements(),
                design.placedObjects(),
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.craftingRecipes(),
                design.triggers(),
                updatedLighting,
                design.lights(),
                design.spawnX(),
                design.spawnY());
        markDirty(true);
        refreshContentBrowser();
        setStatus("Updated metadata for " + title + ".");
    }

    private void validateMap() {
        syncThemes();
        List<MapDesignLibrary.ValidationIssue> issues = MapDesignLibrary.validate(design);

        if (issues.isEmpty()) {
            setStatus("Validation passed.");
            showAdaptiveTextMessageDialog(
                    "Validation passed.",
                    "Map Validation",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String message = validationMessage(issues);
        setStatus("Validation found " + issues.size() + " issue(s).");
        showAdaptiveTextMessageDialog(message, "Map Validation", JOptionPane.WARNING_MESSAGE);
    }

    private boolean confirmSaveWithValidationIssues() {
        List<MapDesignLibrary.ValidationIssue> issues = MapDesignLibrary.validate(design);
        if (issues.isEmpty()) {
            return true;
        }

        int result = showAdaptiveTextConfirmDialog(
                this,
                validationMessage(issues) + "\n\nSave anyway?",
                "Map Validation",
                JOptionPane.OK_CANCEL_OPTION,
                hasValidationError(issues) ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);

        return result == JOptionPane.OK_OPTION;
    }

    private void loadSharedContentIntoDesign() {
        try {
            MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
            mergeSharedContent(content);
            sharedContentBaseline = content;
        } catch (IOException exception) {
            setStatus("Shared content load failed: " + exception.getMessage());
        }
    }

    private void mergeSharedContent(MapDesignLibrary.AuthoredContent content) {
        if (content == null) {
            return;
        }

        mergeSharedEntries(design.authoredQuests(), content.authoredQuests(), MapDesignLibrary.AuthoredQuest::questId);
        mergeSharedEntries(design.authoredDialogues(), content.authoredDialogues(),
                MapDesignLibrary.AuthoredDialogue::interactionId);
        mergeSharedEntries(design.customItems(), content.customItems(), MapDesignLibrary.CustomItem::itemId);
        mergeSharedEntries(design.customMobs(), content.customMobs(), MapDesignLibrary.CustomMob::mobId);
        mergeSharedEntries(design.customLimbs(), content.customLimbs(), MapDesignLibrary.CustomLimb::limbId);
        mergeSharedEntries(design.customNpcs(), content.customNpcs(), MapDesignLibrary.CustomNpc::npcId);
        mergeSharedEntries(design.customFurniture(), content.customFurniture(),
                MapDesignLibrary.CustomFurnitureDefinition::furnitureId);
        mergeSharedEntries(design.customGatheringNodes(), content.customGatheringNodes(),
                MapDesignLibrary.CustomGatheringNode::nodeId);
        mergeSharedEntries(design.customCookingRecipes(), content.customCookingRecipes(),
                MapDesignLibrary.CustomCookingRecipe::recipeId);
        mergeSharedEntries(design.craftingRecipes(), content.craftingRecipes(),
                MapDesignLibrary.CraftingRecipe::recipeId);
    }

    private <T> void mergeSharedEntries(List<T> target, List<T> sharedEntries, Function<T, String> idFunction) {
        for (T sharedEntry : sharedEntries) {
            String sharedId = idFunction.apply(sharedEntry);
            target.removeIf(existing -> sharedId.equals(idFunction.apply(existing)));
            target.add(sharedEntry);
        }
    }

    private boolean saveSharedContentFromDesign() {
        return persistSharedContent("shared content");
    }

    private boolean persistSharedContent(String contentType) {
        try {
            MapDesignLibrary.AuthoredContent desired = new MapDesignLibrary.AuthoredContent(
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
                    design.customFurniture(),
                    design.customGatheringNodes(),
                    design.customCookingRecipes(),
                    design.craftingRecipes());
            MapDesignLibrary.AuthoredContent latest = MapDesignLibrary.loadSharedContent();
            MapDesignLibrary.AuthoredContent merged = mergeSharedContentChanges(
                    latest,
                    sharedContentBaseline,
                    desired);
            MapDesignLibrary.saveSharedContent(merged);
            replaceSharedContentInDesign(merged);
            sharedContentBaseline = merged;
            refreshContentBrowser();
            return true;
        } catch (IOException exception) {
            setStatus("Shared content save failed: " + exception.getMessage());
            return false;
        }
    }

    private MapDesignLibrary.AuthoredContent mergeSharedContentChanges(
            MapDesignLibrary.AuthoredContent latest,
            MapDesignLibrary.AuthoredContent baseline,
            MapDesignLibrary.AuthoredContent desired) {
        MapDesignLibrary.AuthoredContent safeLatest = latest == null ? emptyAuthoredContent() : latest;
        MapDesignLibrary.AuthoredContent safeBaseline = baseline == null ? emptyAuthoredContent() : baseline;
        MapDesignLibrary.AuthoredContent safeDesired = desired == null ? emptyAuthoredContent() : desired;
        return new MapDesignLibrary.AuthoredContent(
                mergeChangedEntries(
                        safeLatest.authoredDialogues(),
                        safeBaseline.authoredDialogues(),
                        safeDesired.authoredDialogues(),
                        MapDesignLibrary.AuthoredDialogue::interactionId),
                mergeChangedEntries(
                        safeLatest.authoredQuests(),
                        safeBaseline.authoredQuests(),
                        safeDesired.authoredQuests(),
                        MapDesignLibrary.AuthoredQuest::questId),
                mergeChangedEntries(
                        safeLatest.customItems(),
                        safeBaseline.customItems(),
                        safeDesired.customItems(),
                        MapDesignLibrary.CustomItem::itemId),
                mergeChangedEntries(
                        safeLatest.customMobs(),
                        safeBaseline.customMobs(),
                        safeDesired.customMobs(),
                        MapDesignLibrary.CustomMob::mobId),
                mergeChangedEntries(
                        safeLatest.customLimbs(),
                        safeBaseline.customLimbs(),
                        safeDesired.customLimbs(),
                        MapDesignLibrary.CustomLimb::limbId),
                mergeChangedEntries(
                        safeLatest.customNpcs(),
                        safeBaseline.customNpcs(),
                        safeDesired.customNpcs(),
                        MapDesignLibrary.CustomNpc::npcId),
                mergeChangedEntries(
                        safeLatest.customFurniture(),
                        safeBaseline.customFurniture(),
                        safeDesired.customFurniture(),
                        MapDesignLibrary.CustomFurnitureDefinition::furnitureId),
                mergeChangedEntries(
                        safeLatest.customGatheringNodes(),
                        safeBaseline.customGatheringNodes(),
                        safeDesired.customGatheringNodes(),
                        MapDesignLibrary.CustomGatheringNode::nodeId),
                mergeChangedEntries(
                        safeLatest.customCookingRecipes(),
                        safeBaseline.customCookingRecipes(),
                        safeDesired.customCookingRecipes(),
                        MapDesignLibrary.CustomCookingRecipe::recipeId),
                mergeChangedEntries(
                        safeLatest.craftingRecipes(),
                        safeBaseline.craftingRecipes(),
                        safeDesired.craftingRecipes(),
                        MapDesignLibrary.CraftingRecipe::recipeId));
    }

    private <T> List<T> mergeChangedEntries(
            List<T> latest,
            List<T> baseline,
            List<T> desired,
            Function<T, String> idFunction) {
        Map<String, T> latestById = entriesById(latest, idFunction);
        Map<String, T> baselineById = entriesById(baseline, idFunction);
        Map<String, T> desiredById = entriesById(desired, idFunction);
        Set<String> candidateIds = new HashSet<>(baselineById.keySet());
        candidateIds.addAll(desiredById.keySet());
        for (String id : candidateIds) {
            T before = baselineById.get(id);
            T after = desiredById.get(id);
            if (Objects.equals(before, after)) {
                continue;
            }
            if (after == null) {
                latestById.remove(id);
            } else {
                latestById.put(id, after);
            }
        }
        return new ArrayList<>(latestById.values());
    }

    private <T> Map<String, T> entriesById(List<T> entries, Function<T, String> idFunction) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T entry : entries == null ? List.<T>of() : entries) {
            if (entry == null) {
                continue;
            }
            String id = idFunction.apply(entry);
            if (id != null && !id.isBlank()) {
                result.put(id, entry);
            }
        }
        return result;
    }

    private static MapDesignLibrary.AuthoredContent emptyAuthoredContent() {
        return new MapDesignLibrary.AuthoredContent(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private boolean hasValidationError(List<MapDesignLibrary.ValidationIssue> issues) {
        for (MapDesignLibrary.ValidationIssue issue : issues) {
            if (issue.severity() == MapDesignLibrary.ValidationSeverity.ERROR) {
                return true;
            }
        }

        return false;
    }

    private String validationMessage(List<MapDesignLibrary.ValidationIssue> issues) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (MapDesignLibrary.ValidationIssue issue : issues) {
            builder.append(issue).append('\n');
            count++;
            if (count >= 12 && issues.size() > count) {
                builder.append("...and ").append(issues.size() - count).append(" more.");
                break;
            }
        }

        return builder.toString();
    }

    private String safeMapFileName() {
        String rawName = mapTitle();
        String safeName = rawName.replaceAll("[^a-zA-Z0-9_-]+", "_").replaceAll("_+", "_");
        if (safeName.isBlank()) {
            safeName = "new_map";
        }
        return safeName + ".properties";
    }

    private String mapTitle() {
        String title = mapNameField.getText() == null ? "" : mapNameField.getText().trim();
        return title.isBlank() ? "Untitled Map" : title;
    }

    private Path ensurePropertiesExtension(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".properties")) {
            return path;
        }
        return path.resolveSibling(fileName + ".properties");
    }

    private void setStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AetherConstructionKit().setVisible(true));
    }

    private static Color editorTileColor(Library.TileType tile, int adjustment) {
        return switch (tile) {
            case FLOOR -> new Color(84 + adjustment, 76 + adjustment, 64 + adjustment);
            case WALL -> new Color(92 + adjustment, 96 + adjustment, 104 + adjustment);
            case DOOR_CLOSED -> new Color(105 + adjustment, 72 + adjustment, 40 + adjustment);
            case DOOR_OPEN -> new Color(72 + adjustment, 48 + adjustment, 28 + adjustment);
            case QUEST_DOOR_OPEN -> new Color(138 + adjustment, 92 + adjustment, 36 + adjustment);
            case QUEST_DOOR_CLOSED -> new Color(72 + adjustment, 32 + adjustment, 24 + adjustment);
            case FISHING_WATER -> new Color(38, 88 + adjustment, 132 + adjustment);
            case WATER -> new Color(24, 60 + adjustment, 104 + adjustment);
            case TRAP -> new Color(120 + adjustment, 68, 68);
            case STAIRS_DOWN -> new Color(60, 116 + adjustment, 72);
            case STAIRS_UP -> new Color(116 + adjustment, 116 + adjustment, 72);
        };
    }

    private static Color placementColor(MapDesignLibrary.PlacementKind kind) {
        return switch (kind) {
            case CRAFTING_NODE -> new Color(240, 130, 70);
            case GATHERING_NODE -> new Color(90, 220, 130);
            case FURNITURE -> new Color(178, 145, 96);
            case CUSTOM_NPC -> new Color(220, 90, 220);
            case ITEM -> new Color(230, 210, 80);
            case ENEMY -> new Color(230, 80, 70);
            case INTERACTION -> new Color(90, 200, 230);
        };
    }

    private static boolean isDoorTile(Library.TileType tile) {
        return tile == Library.TileType.DOOR_OPEN
                || tile == Library.TileType.DOOR_CLOSED
                || tile == Library.TileType.QUEST_DOOR_OPEN
                || tile == Library.TileType.QUEST_DOOR_CLOSED;
    }

    private record PlacementVisualInfo(
            String title,
            String subtitle,
            String imagePath,
            String modelPath,
            String note
    ) {
        private PlacementVisualInfo {
            title = title == null || title.isBlank() ? "Placement" : title;
            subtitle = subtitle == null ? "" : subtitle;
            imagePath = imagePath == null ? "" : imagePath;
            modelPath = modelPath == null ? "" : modelPath;
            note = note == null ? "" : note;
        }
    }

    private final class PlacementPreviewPanel extends JPanel {
        private static final int IMAGE_SIZE = 132;
        private final Supplier<PlaceableOption> optionSupplier;

        private PlacementPreviewPanel(Supplier<PlaceableOption> optionSupplier) {
            this.optionSupplier = optionSupplier;
            setPreferredSize(new Dimension(240, 300));
            setMinimumSize(new Dimension(220, 260));
            setBorder(BorderFactory.createTitledBorder("Placement Preview"));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                PlacementVisualInfo info = resolvePlacementVisualInfo(optionSupplier.get());
                int imageLeft = (getWidth() - IMAGE_SIZE) / 2;
                int imageTop = 42;
                g.setColor(new Color(24, 24, 28));
                g.fillRoundRect(imageLeft, imageTop, IMAGE_SIZE, IMAGE_SIZE, 6, 6);
                g.setColor(new Color(110, 110, 120));
                g.drawRoundRect(imageLeft, imageTop, IMAGE_SIZE, IMAGE_SIZE, 6, 6);

                BufferedImage image = loadPlacementPreviewImage(info.imagePath());
                if (image != null) {
                    drawCenteredPreviewImage(g, image, imageLeft + 8, imageTop + 8,
                            IMAGE_SIZE - 16, IMAGE_SIZE - 16);
                } else {
                    g.setColor(new Color(150, 150, 160));
                    drawCenteredText(g, info.modelPath().isBlank() ? "No sprite" : "3D model", imageLeft,
                            imageTop + IMAGE_SIZE / 2 - 8, IMAGE_SIZE);
                    if (!info.modelPath().isBlank()) {
                        drawCenteredText(g, "assigned", imageLeft, imageTop + IMAGE_SIZE / 2 + 10, IMAGE_SIZE);
                    }
                }

                FontMetrics metrics = g.getFontMetrics();
                int textLeft = 14;
                int textWidth = Math.max(1, getWidth() - 28);
                int y = imageTop + IMAGE_SIZE + 26;
                g.setColor(new Color(230, 230, 235));
                g.drawString(ellipsize(info.title(), textWidth, metrics), textLeft, y);
                y += 18;
                if (!info.subtitle().isBlank()) {
                    g.setColor(new Color(185, 185, 195));
                    g.drawString(ellipsize(info.subtitle(), textWidth, metrics), textLeft, y);
                    y += 18;
                }
                if (!info.modelPath().isBlank()) {
                    g.setColor(new Color(150, 210, 180));
                    g.drawString(ellipsize("Model: " + info.modelPath(), textWidth, metrics), textLeft, y);
                    y += 18;
                }
                if (!info.note().isBlank()) {
                    g.setColor(new Color(170, 170, 178));
                    g.drawString(ellipsize(info.note(), textWidth, metrics), textLeft, y);
                }
            } finally {
                g.dispose();
            }
        }

        private PlacementVisualInfo resolvePlacementVisualInfo(PlaceableOption option) {
            if (option == null || option.kind() == null) {
                return new PlacementVisualInfo("No placement selected", "", "", "", "");
            }
            return switch (option.kind()) {
                case CUSTOM_NPC -> customNpcVisualInfo(option);
                case ENEMY -> enemyVisualInfo(option);
                default -> new PlacementVisualInfo(
                        option.label(),
                        option.kind().name(),
                        "",
                        "",
                        "Preview is for NPC/enemy placements.");
            };
        }

        private PlacementVisualInfo customNpcVisualInfo(PlaceableOption option) {
            MapDesignLibrary.CustomNpc npc = findCustomNpc(option.id());
            if (npc == null) {
                return new PlacementVisualInfo(option.label(), option.kind().name(), "", "", "Custom NPC not found.");
            }
            CharacterModelDefinition model = npc.characterModel();
            return new PlacementVisualInfo(
                    npc.displayName(),
                    "Custom NPC",
                    npc.imagePath(),
                    model.hasModel() ? model.modelPath() : "",
                    npc.interactionId().isBlank() ? "" : "Dialogue: " + npc.interactionId());
        }

        private PlacementVisualInfo enemyVisualInfo(PlaceableOption option) {
            MapDesignLibrary.CustomMob mob = findCustomMob(option.id());
            if (mob == null) {
                return new PlacementVisualInfo(option.label(), option.kind().name(), "", "", "Enemy not found.");
            }
            CharacterModelDefinition model = mob.characterModel();
            return new PlacementVisualInfo(
                    mob.displayName(),
                    "Enemy",
                    mob.imagePath(),
                    model.hasModel() ? model.modelPath() : "",
                    "AI " + mob.combatAiIntelligence());
        }

        private BufferedImage loadPlacementPreviewImage(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            try {
                return AssetLoader.loadImage(path);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private void drawCenteredPreviewImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
            double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int drawX = x + (width - drawWidth) / 2;
            int drawY = y + (height - drawHeight) / 2;
            g.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
        }

        private void drawCenteredText(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, y);
        }

        private String ellipsize(String value, int maxWidth, FontMetrics metrics) {
            if (value == null || value.isBlank() || metrics.stringWidth(value) <= maxWidth) {
                return value == null ? "" : value;
            }
            String suffix = "...";
            int suffixWidth = metrics.stringWidth(suffix);
            int end = value.length();
            while (end > 0 && metrics.stringWidth(value.substring(0, end)) + suffixWidth > maxWidth) {
                end--;
            }
            return end <= 0 ? suffix : value.substring(0, end) + suffix;
        }
    }

    private final class PlacedObjectEditPreviewPanel extends JPanel {
        private static final int PREVIEW_RADIUS = 1;
        private static final int PREVIEW_TILE_COUNT = PREVIEW_RADIUS * 2 + 1;
        private final Supplier<MapDesignLibrary.PlacedObjectInstance> objectSupplier;
        private final JSpinner offsetXSpinner;
        private final JSpinner offsetZSpinner;
        private final JSpinner yawSpinner;
        private int lastLeft;
        private int lastTop;
        private int lastCellSize;

        private PlacedObjectEditPreviewPanel(
                Supplier<MapDesignLibrary.PlacedObjectInstance> objectSupplier,
                JSpinner offsetXSpinner,
                JSpinner offsetZSpinner,
                JSpinner yawSpinner) {
            this.objectSupplier = objectSupplier;
            this.offsetXSpinner = offsetXSpinner;
            this.offsetZSpinner = offsetZSpinner;
            this.yawSpinner = yawSpinner;
            setPreferredSize(new Dimension(280, 360));
            setMinimumSize(new Dimension(260, 320));
            setBorder(BorderFactory.createTitledBorder("2D Edit Preview"));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            MouseAdapter dragHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    updateOffsetsFromMouse(event);
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    updateOffsetsFromMouse(event);
                }
            };
            addMouseListener(dragHandler);
            addMouseMotionListener(dragHandler);
            addMouseWheelListener(event -> {
                double step = event.isShiftDown() ? 15.0 : 5.0;
                double currentYaw = ((Number) yawSpinner.getValue()).doubleValue();
                double nextYaw = normalizePreviewDegrees(currentYaw - event.getPreciseWheelRotation() * step);
                yawSpinner.setValue(roundToStep(nextYaw, 5.0));
                repaint();
            });
            setToolTipText("Drag inside the highlighted tile to update X/Z offsets. Mouse wheel rotates yaw. In 3D preview, WASD changes view side.");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                MapDesignLibrary.PlacedObjectInstance object = objectSupplier.get();
                int mapX = object.x();
                int mapY = object.y();
                int size = Math.min(getWidth() - 32, 210);
                int cellSize = Math.max(26, size / PREVIEW_TILE_COUNT);
                size = cellSize * PREVIEW_TILE_COUNT;
                int left = (getWidth() - size) / 2;
                int top = 28;
                lastLeft = left;
                lastTop = top;
                lastCellSize = cellSize;

                drawEditPreviewTiles(g, left, top, cellSize, mapX, mapY);
                drawEditPreviewLight(g, left, top, cellSize, object);
                drawEditPreviewObject(g, left, top, cellSize, object);
                drawEditPreviewText(g, object, top + size + 20);
            } finally {
                g.dispose();
            }
        }

        private void drawEditPreviewTiles(Graphics2D g, int left, int top, int cellSize, int centerMapX, int centerMapY) {
            for (int localY = 0; localY < PREVIEW_TILE_COUNT; localY++) {
                for (int localX = 0; localX < PREVIEW_TILE_COUNT; localX++) {
                    int mapX = centerMapX + localX - PREVIEW_RADIUS;
                    int mapY = centerMapY + localY - PREVIEW_RADIUS;
                    int drawX = left + localX * cellSize;
                    int drawY = top + localY * cellSize;
                    if (!isDesignTileInBounds(mapX, mapY)) {
                        g.setColor(new Color(18, 18, 22));
                        g.fillRect(drawX, drawY, cellSize, cellSize);
                        g.setColor(new Color(60, 60, 66));
                        g.drawRect(drawX, drawY, cellSize, cellSize);
                        continue;
                    }

                    g.setColor(editorTileColor(design.tiles()[mapY][mapX], 0));
                    g.fillRect(drawX, drawY, cellSize, cellSize);
                    int heightLevel = designHeightLevelAt(mapX, mapY);
                    if (heightLevel != MapGeometryData.DEFAULT_HEIGHT_LEVEL) {
                        g.setColor(new Color(80, 170, 205, 92));
                        g.fillRect(drawX + 1, drawY + 1, cellSize - 2, cellSize - 2);
                        g.setColor(new Color(210, 235, 255));
                        g.drawString("H" + heightLevel, drawX + 4, drawY + 14);
                    }
                    g.setColor(new Color(8, 8, 10, 150));
                    g.drawRect(drawX, drawY, cellSize, cellSize);
                }
            }

            g.setColor(new Color(255, 230, 120));
            g.setStroke(new BasicStroke(3.0f));
            g.drawRect(left + PREVIEW_RADIUS * cellSize + 2, top + PREVIEW_RADIUS * cellSize + 2,
                    cellSize - 4, cellSize - 4);
            g.setStroke(new BasicStroke(1.0f));
        }

        private void drawEditPreviewLight(
                Graphics2D g,
                int left,
                int top,
                int cellSize,
                MapDesignLibrary.PlacedObjectInstance object) {
            MapDesignLibrary.LightAttachment light = effectiveLightFor(object);
            if (light == null || !light.enabled()) {
                return;
            }
            Point center = editPreviewObjectPoint(
                    left,
                    top,
                    cellSize,
                    object,
                    rotatedLocalOffsetX(object.yawDegrees(), light.offsetX(), light.offsetZ()),
                    rotatedLocalOffsetZ(object.yawDegrees(), light.offsetX(), light.offsetZ()));
            int radius = Math.max(3, (int) Math.round(light.radius() * cellSize));
            Color color = new Color(light.colorRgb());
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 42));
            g.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
            g.drawOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
        }

        private void drawEditPreviewObject(
                Graphics2D g,
                int left,
                int top,
                int cellSize,
                MapDesignLibrary.PlacedObjectInstance object) {
            Point point = editPreviewObjectPoint(left, top, cellSize, object, 0.0, 0.0);
            int radius = Math.max(6, (int) Math.round(7 * Math.min(2.0, object.scale())));
            g.setColor(object.blocksMovement() ? new Color(232, 166, 92) : new Color(255, 220, 110));
            g.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
            g.setColor(Color.BLACK);
            g.drawOval(point.x - radius, point.y - radius, radius * 2, radius * 2);

            double radians = Math.toRadians(object.yawDegrees() - 90.0);
            int endX = point.x + (int) Math.round(Math.cos(radians) * Math.max(18, cellSize * 0.34));
            int endY = point.y + (int) Math.round(Math.sin(radians) * Math.max(18, cellSize * 0.34));
            g.setStroke(new BasicStroke(3.0f));
            g.drawLine(point.x, point.y, endX, endY);
            g.setStroke(new BasicStroke(1.0f));
        }

        private Point editPreviewObjectPoint(
                int left,
                int top,
                int cellSize,
                MapDesignLibrary.PlacedObjectInstance object,
                double extraOffsetX,
                double extraOffsetZ) {
            int baseX = left + PREVIEW_RADIUS * cellSize + cellSize / 2;
            int baseY = top + PREVIEW_RADIUS * cellSize + cellSize / 2;
            return new Point(
                    baseX + previewOffset(object.offsetX() + extraOffsetX, cellSize),
                    baseY + previewOffset(object.offsetZ() + extraOffsetZ, cellSize));
        }

        private void drawEditPreviewText(Graphics2D g, MapDesignLibrary.PlacedObjectInstance object, int textTop) {
            String displayName = placedObjectDisplayName(object);
            String modelPath = placedObjectModelPath(object);
            g.setColor(Color.DARK_GRAY);
            g.drawString(ellipsize(displayName, getWidth() - 24, g.getFontMetrics()), 12, textTop);
            g.drawString("Tile " + object.x() + "," + object.y()
                            + " H" + designHeightLevelAt(object.x(), object.y()),
                    12,
                    textTop + 16);
            g.drawString("Offset "
                            + formatDouble(object.offsetX()) + ", "
                            + formatDouble(object.offsetY()) + ", "
                            + formatDouble(object.offsetZ()),
                    12,
                    textTop + 32);
            g.drawString("Yaw/Pitch/Roll "
                            + formatDouble(object.yawDegrees()) + ", "
                            + formatDouble(object.pitchDegrees()) + ", "
                            + formatDouble(object.rollDegrees()),
                    12,
                    textTop + 48);
            g.drawString("Scale " + formatDouble(object.scale())
                            + " | Bright " + formatDouble(object.modelBrightness()),
                    12,
                    textTop + 64);
            g.drawString("Blocks " + object.blocksMovement(),
                    12,
                    textTop + 80);
            MapDesignLibrary.LightAttachment light = effectiveLightFor(object);
            g.drawString(light == null || !light.enabled()
                            ? "Light: none"
                            : "Light r" + formatDouble(light.radius()) + " i" + formatDouble(light.intensity()),
                    12,
                    textTop + 96);
            g.drawString(ellipsize(modelPath.isBlank() ? "Model: missing" : "Model: " + modelPath,
                            getWidth() - 24,
                            g.getFontMetrics()),
                    12,
                    textTop + 112);
            g.drawString("3D Preview: WASD side, right-drag rotates.", 12, textTop + 128);
        }

        private void updateOffsetsFromMouse(MouseEvent event) {
            if (lastCellSize <= 0) {
                return;
            }
            int centerX = lastLeft + PREVIEW_RADIUS * lastCellSize + lastCellSize / 2;
            int centerY = lastTop + PREVIEW_RADIUS * lastCellSize + lastCellSize / 2;
            double scale = lastCellSize * 0.42;
            if (scale <= 0.0) {
                return;
            }
            double offsetX = clampPreviewOffset((event.getX() - centerX) / scale);
            double offsetZ = clampPreviewOffset((event.getY() - centerY) / scale);
            offsetXSpinner.setValue(roundToStep(offsetX, 0.05));
            offsetZSpinner.setValue(roundToStep(offsetZ, 0.05));
            repaint();
        }

        private double clampPreviewOffset(double value) {
            return Math.max(-0.5, Math.min(0.5, value));
        }

        private double roundToStep(double value, double step) {
            if (step <= 0.0) {
                return value;
            }
            return Math.round(value / step) * step;
        }

        private double normalizePreviewDegrees(double degrees) {
            if (!Double.isFinite(degrees)) {
                return 0.0;
            }
            double normalized = degrees % 360.0;
            return normalized < 0.0 ? normalized + 360.0 : normalized;
        }

        private int previewOffset(double value, int cellSize) {
            double clamped = Math.max(-0.5, Math.min(0.5, value));
            return (int) Math.round(clamped * cellSize * 0.84);
        }

        private String ellipsize(String value, int maxWidth, FontMetrics metrics) {
            if (metrics.stringWidth(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int suffixWidth = metrics.stringWidth(suffix);
            int end = value.length();
            while (end > 0 && metrics.stringWidth(value.substring(0, end)) + suffixWidth > maxWidth) {
                end--;
            }
            return end <= 0 ? suffix : value.substring(0, end) + suffix;
        }
    }

    private final class TileObjectPreviewPanel extends JPanel {
        private static final int PREVIEW_RADIUS = 2;
        private static final int PREVIEW_TILE_COUNT = PREVIEW_RADIUS * 2 + 1;
        private static final int PREVIEW_SIZE = 330;
        private final int tileX;
        private final int tileY;
        private final DefaultListModel<MapDesignLibrary.PlacedObjectInstance> model;
        private final JList<MapDesignLibrary.PlacedObjectInstance> objectList;

        private TileObjectPreviewPanel(
                int tileX,
                int tileY,
                DefaultListModel<MapDesignLibrary.PlacedObjectInstance> model,
                JList<MapDesignLibrary.PlacedObjectInstance> objectList) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.model = model;
            this.objectList = objectList;
            setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE + 108));
            setMinimumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE + 108));
            setBorder(BorderFactory.createTitledBorder("Live Tile Preview"));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth() - 34, getHeight() - 118);
                int left = (getWidth() - size) / 2;
                int top = 28;
                int cellSize = Math.max(12, size / PREVIEW_TILE_COUNT);
                size = cellSize * PREVIEW_TILE_COUNT;
                left = (getWidth() - size) / 2;
                int centerTileLeft = left + PREVIEW_RADIUS * cellSize;
                int centerTileTop = top + PREVIEW_RADIUS * cellSize;
                int centerX = centerTileLeft + cellSize / 2;
                int centerY = centerTileTop + cellSize / 2;

                g.setColor(new Color(34, 34, 34));
                g.fillRect(left, top, size, size);
                drawNeighborhood(g, left, top, cellSize);
                drawGameplayPlacementHints(g, left, top, cellSize);
                drawPlacedObjectLights(g, left, top, cellSize);
                drawPlacedObjectMarkers(g, left, top, cellSize);

                g.setStroke(new BasicStroke(3.0f));
                g.setColor(new Color(255, 230, 120));
                g.drawRect(centerTileLeft + 2, centerTileTop + 2, cellSize - 4, cellSize - 4);
                g.setStroke(new BasicStroke(1.0f));
                g.setColor(new Color(170, 170, 170, 180));
                g.drawLine(centerX, centerTileTop + 6, centerX, centerTileTop + cellSize - 6);
                g.drawLine(centerTileLeft + 6, centerY, centerTileLeft + cellSize - 6, centerY);

                drawPreviewText(g, top + size + 20);
            } finally {
                g.dispose();
            }
        }

        private void drawNeighborhood(Graphics2D g, int left, int top, int cellSize) {
            for (int localY = 0; localY < PREVIEW_TILE_COUNT; localY++) {
                for (int localX = 0; localX < PREVIEW_TILE_COUNT; localX++) {
                    int mapX = tileX + localX - PREVIEW_RADIUS;
                    int mapY = tileY + localY - PREVIEW_RADIUS;
                    int drawX = left + localX * cellSize;
                    int drawY = top + localY * cellSize;

                    if (!isDesignTileInBounds(mapX, mapY)) {
                        g.setColor(new Color(18, 18, 22));
                        g.fillRect(drawX, drawY, cellSize, cellSize);
                        g.setColor(new Color(60, 60, 66));
                        g.drawRect(drawX, drawY, cellSize, cellSize);
                        continue;
                    }

                    Library.TileType tile = design.tiles()[mapY][mapX];
                    g.setColor(editorTileColor(tile, 0));
                    g.fillRect(drawX, drawY, cellSize, cellSize);

                    int heightLevel = designHeightLevelAt(mapX, mapY);
                    if (heightLevel != MapGeometryData.DEFAULT_HEIGHT_LEVEL) {
                        float ratio = (heightLevel - MapGeometryData.MIN_HEIGHT_LEVEL)
                                / (float) Math.max(1, MapGeometryData.MAX_HEIGHT_LEVEL - MapGeometryData.MIN_HEIGHT_LEVEL);
                        Color tint = Color.getHSBColor(0.34f - ratio * 0.24f, 0.55f, 0.92f);
                        g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 92));
                        g.fillRect(drawX + 1, drawY + 1, cellSize - 2, cellSize - 2);
                    }

                    g.setColor(new Color(8, 8, 10, 150));
                    g.drawRect(drawX, drawY, cellSize, cellSize);
                    if (heightLevel != MapGeometryData.DEFAULT_HEIGHT_LEVEL) {
                        g.setColor(new Color(210, 235, 255));
                        g.drawString("H" + heightLevel, drawX + 4, drawY + 14);
                    }
                    drawPreviewTerrainEdges(g, drawX, drawY, cellSize, mapX, mapY);
                }
            }
        }

        private void drawPreviewTerrainEdges(Graphics2D g, int drawX, int drawY, int cellSize, int mapX, int mapY) {
            drawPreviewTerrainEdge(g, drawX, drawY, cellSize, mapX, mapY, mapX + 1, mapY, true);
            drawPreviewTerrainEdge(g, drawX, drawY, cellSize, mapX, mapY, mapX, mapY + 1, false);
        }

        private void drawPreviewTerrainEdge(
                Graphics2D g,
                int drawX,
                int drawY,
                int cellSize,
                int mapX,
                int mapY,
                int neighborX,
                int neighborY,
                boolean vertical) {
            if (!isDesignTileInBounds(neighborX, neighborY)) {
                return;
            }
            TerrainEdgeKind kind = TerrainGeometry.edgeKind(
                    designHeightLevelAt(mapX, mapY),
                    designHeightLevelAt(neighborX, neighborY));
            if (kind != TerrainEdgeKind.SLOPE && kind != TerrainEdgeKind.CLIFF) {
                return;
            }
            java.awt.Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(kind == TerrainEdgeKind.CLIFF ? 3.0f : 1.5f));
            g.setColor(kind == TerrainEdgeKind.CLIFF
                    ? new Color(190, 65, 42, 230)
                    : new Color(245, 214, 90, 215));
            if (vertical) {
                int x = drawX + cellSize;
                g.drawLine(x, drawY + 3, x, drawY + cellSize - 3);
            } else {
                int y = drawY + cellSize;
                g.drawLine(drawX + 3, y, drawX + cellSize - 3, y);
            }
            g.setStroke(oldStroke);
        }

        private void drawGameplayPlacementHints(Graphics2D g, int left, int top, int cellSize) {
            FontMetrics metrics = g.getFontMetrics();
            for (int localY = 0; localY < PREVIEW_TILE_COUNT; localY++) {
                for (int localX = 0; localX < PREVIEW_TILE_COUNT; localX++) {
                    int mapX = tileX + localX - PREVIEW_RADIUS;
                    int mapY = tileY + localY - PREVIEW_RADIUS;
                    if (!isDesignTileInBounds(mapX, mapY)) {
                        continue;
                    }
                    List<MapDesignLibrary.MapPlacement> placements = mapPlacementsAt(mapX, mapY);
                    if (placements.isEmpty()) {
                        continue;
                    }
                    int drawX = left + localX * cellSize;
                    int drawY = top + localY * cellSize;
                    int badgeX = drawX + 4;
                    int badgeY = drawY + cellSize - 17;
                    for (int i = 0; i < placements.size(); i++) {
                        MapDesignLibrary.MapPlacement placement = placements.get(i);
                        g.setColor(new Color(
                                placementColor(placement.kind()).getRed(),
                                placementColor(placement.kind()).getGreen(),
                                placementColor(placement.kind()).getBlue(),
                                215));
                        g.fillOval(badgeX + i * 14, badgeY, 12, 12);
                        g.setColor(Color.BLACK);
                        g.drawOval(badgeX + i * 14, badgeY, 12, 12);
                        if (placement.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE) {
                            MapDesignLibrary.CustomGatheringNode node = findCustomGatheringNode(placement.id());
                            if (node != null && !node.modelPaths().isEmpty()) {
                                String label = "3D";
                                g.setColor(new Color(230, 250, 230));
                                g.drawString(label, drawX + cellSize - metrics.stringWidth(label) - 4, drawY + cellSize - 5);
                            }
                        }
                    }
                }
            }
        }

        private void drawPlacedObjectLights(Graphics2D g, int left, int top, int cellSize) {
            for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
                if (!isObjectInPreview(object)) {
                    continue;
                }
                MapDesignLibrary.LightAttachment light = effectiveLightFor(object);
                if (light == null || !light.enabled()) {
                    continue;
                }
                Point center = objectPoint(
                        object,
                        left,
                        top,
                        cellSize,
                        rotatedLocalOffsetX(object.yawDegrees(), light.offsetX(), light.offsetZ()),
                        rotatedLocalOffsetZ(object.yawDegrees(), light.offsetX(), light.offsetZ()));
                int radius = Math.max(3, (int) Math.round(light.radius() * cellSize));
                Color color = new Color(light.colorRgb());
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
                g.fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 145));
                g.drawOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
            }
        }

        private void drawPlacedObjectMarkers(Graphics2D g, int left, int top, int cellSize) {
            MapDesignLibrary.PlacedObjectInstance selected = objectList.getSelectedValue();
            int indexOnCenterTile = 1;
            for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
                if (!isObjectInPreview(object)) {
                    continue;
                }
                boolean active = object == selected || object.instanceId().equals(selected == null ? "" : selected.instanceId());
                Point point = objectPoint(object, left, top, cellSize, 0.0, 0.0);
                int radius = active ? 8 : 6;
                g.setColor(active ? new Color(255, 220, 110) : new Color(185, 145, 90));
                g.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
                g.setColor(Color.BLACK);
                g.drawOval(point.x - radius, point.y - radius, radius * 2, radius * 2);

                double radians = Math.toRadians(object.yawDegrees() - 90.0);
                int endX = point.x + (int) Math.round(Math.cos(radians) * Math.max(16, cellSize * 0.36));
                int endY = point.y + (int) Math.round(Math.sin(radians) * Math.max(16, cellSize * 0.36));
                g.setStroke(new BasicStroke(active ? 3.0f : 2.0f));
                g.drawLine(point.x, point.y, endX, endY);
                g.setStroke(new BasicStroke(1.0f));

                if (object.x() == tileX && object.y() == tileY) {
                    g.setColor(Color.WHITE);
                    g.drawString(String.valueOf(indexOnCenterTile), point.x + 9, point.y - 9);
                    indexOnCenterTile++;
                }
            }
        }

        private void drawPreviewText(Graphics2D g, int textTop) {
            MapDesignLibrary.PlacedObjectInstance selected = objectList.getSelectedValue();
            g.setColor(Color.DARK_GRAY);
            g.drawString("Center tile " + tileX + "," + tileY
                            + " | H" + designHeightLevelAt(tileX, tileY)
                            + " | " + model.size() + " object(s)",
                    12,
                    textTop);
            g.drawString("Yellow edges are slopes; red edges are cliffs.", 12, textTop + 16);
            g.drawString("Circles show attached light radius. 3D marks model-backed nodes.", 12, textTop + 32);
            if (selected != null) {
                String details = "Selected: off "
                        + formatDouble(selected.offsetX()) + ","
                        + formatDouble(selected.offsetY()) + ","
                        + formatDouble(selected.offsetZ())
                        + " yaw " + formatDouble(selected.yawDegrees())
                        + " scale " + formatDouble(selected.scale());
                g.drawString(ellipsize(details, getWidth() - 24, g.getFontMetrics()), 12, textTop + 52);
                MapDesignLibrary.LightAttachment light = effectiveLightFor(selected);
                if (light != null && light.enabled()) {
                    g.drawString("Light: r" + formatDouble(light.radius())
                                    + " i" + formatDouble(light.intensity())
                                    + " off " + formatDouble(light.offsetX())
                                    + "," + formatDouble(light.offsetY())
                                    + "," + formatDouble(light.offsetZ()),
                            12,
                            textTop + 68);
                }
            }
        }

        private Point objectPoint(
                MapDesignLibrary.PlacedObjectInstance object,
                int left,
                int top,
                int cellSize,
                double extraOffsetX,
                double extraOffsetZ) {
            int localX = object.x() - tileX + PREVIEW_RADIUS;
            int localY = object.y() - tileY + PREVIEW_RADIUS;
            int baseX = left + localX * cellSize + cellSize / 2;
            int baseY = top + localY * cellSize + cellSize / 2;
            return new Point(
                    baseX + previewOffset(object.offsetX() + extraOffsetX, cellSize),
                    baseY + previewOffset(object.offsetZ() + extraOffsetZ, cellSize));
        }

        private boolean isObjectInPreview(MapDesignLibrary.PlacedObjectInstance object) {
            return object.x() >= tileX - PREVIEW_RADIUS
                    && object.x() <= tileX + PREVIEW_RADIUS
                    && object.y() >= tileY - PREVIEW_RADIUS
                    && object.y() <= tileY + PREVIEW_RADIUS;
        }

        private int previewOffset(double value, int cellSize) {
            double clamped = Math.max(-1.0, Math.min(1.0, value));
            return (int) Math.round(clamped * cellSize * 0.42);
        }

        private String ellipsize(String value, int maxWidth, FontMetrics metrics) {
            if (metrics.stringWidth(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int suffixWidth = metrics.stringWidth(suffix);
            int end = value.length();
            while (end > 0 && metrics.stringWidth(value.substring(0, end)) + suffixWidth > maxWidth) {
                end--;
            }
            return end <= 0 ? suffix : value.substring(0, end) + suffix;
        }
    }

    private static class TargetMapPickerPanel extends JPanel {
        private static final int CELL_SIZE = 28;
        private static final int PADDING = 12;
        private final MapDesignLibrary.MapDesign targetDesign;
        private Point selectedTile;

        TargetMapPickerPanel(MapDesignLibrary.MapDesign targetDesign) {
            this.targetDesign = targetDesign;
            selectedTile = new Point(targetDesign.spawnX(), targetDesign.spawnY());
            setPreferredSize(new Dimension(
                    targetDesign.width() * CELL_SIZE + PADDING * 2,
                    targetDesign.height() * CELL_SIZE + PADDING * 2));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    int x = (event.getX() - PADDING) / CELL_SIZE;
                    int y = (event.getY() - PADDING) / CELL_SIZE;
                    if (x >= 0 && x < targetDesign.width() && y >= 0 && y < targetDesign.height()) {
                        selectedTile = new Point(x, y);
                        repaint();
                    }
                }
            });
        }

        Point getSelectedTile() {
            return selectedTile == null ? null : new Point(selectedTile);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            for (int y = 0; y < targetDesign.height(); y++) {
                for (int x = 0; x < targetDesign.width(); x++) {
                    int adjustment = 0;
                    int drawX = PADDING + x * CELL_SIZE;
                    int drawY = PADDING + y * CELL_SIZE;
                    g.setColor(editorTileColor(targetDesign.tiles()[y][x], adjustment));
                    g.fillRect(drawX, drawY, CELL_SIZE, CELL_SIZE);
                    g.setColor(new Color(25, 25, 25));
                    g.drawRect(drawX, drawY, CELL_SIZE, CELL_SIZE);
                }
            }

            for (MapDesignLibrary.MapPlacement placement : targetDesign.placements()) {
                g.setColor(placementColor(placement.kind()));
                g.fillOval(
                        PADDING + placement.x() * CELL_SIZE + CELL_SIZE / 4,
                        PADDING + placement.y() * CELL_SIZE + CELL_SIZE / 4,
                        CELL_SIZE / 2,
                        CELL_SIZE / 2);
            }

            g.setColor(Color.WHITE);
            g.drawString("S", PADDING + targetDesign.spawnX() * CELL_SIZE + 9,
                    PADDING + targetDesign.spawnY() * CELL_SIZE + 19);

            if (selectedTile != null) {
                g.setStroke(new BasicStroke(3));
                g.setColor(Color.YELLOW);
                g.drawRect(
                        PADDING + selectedTile.x * CELL_SIZE + 2,
                        PADDING + selectedTile.y * CELL_SIZE + 2,
                        CELL_SIZE - 4,
                        CELL_SIZE - 4);
            }
            g.dispose();
        }
    }

    private final class MapCanvas extends JPanel {
        private static final int BASE_CELL_SIZE = 32;

        private MapCanvas() {
            setBackground(new Color(22, 24, 30));
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (handleWorldChunkClick(event.getPoint())) {
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(event)) {
                        inspectAt(event.getPoint());
                        showMapContextMenu(event);
                        return;
                    }
                    paintAt(event.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) {
                        return;
                    }
                    paintAt(event.getPoint());
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        @Override
        public Dimension getPreferredSize() {
            int cellSize = cellSize();
            int multiplier = activeWorld == null ? 1 : 3;
            return new Dimension(
                    design.width() * cellSize * multiplier + 1,
                    design.height() * cellSize * multiplier + 1);
        }

        private void scrollToTile(int x, int y) {
            int cellSize = cellSize();
            int originX = activeWorld == null ? 0 : design.width() * cellSize;
            int originY = activeWorld == null ? 0 : design.height() * cellSize;
            scrollRectToVisible(new Rectangle(
                    Math.max(0, originX + x * cellSize - cellSize),
                    Math.max(0, originY + y * cellSize - cellSize),
                    cellSize * 3,
                    cellSize * 3));
        }

        private int cellSize() {
            int percent = ((Number) zoomSpinner.getValue()).intValue();
            return Math.max(8, Math.round(BASE_CELL_SIZE * percent / 100.0f));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int originX = activeWorld == null ? 0 : design.width() * cellSize();
            int originY = activeWorld == null ? 0 : design.height() * cellSize();
            if (activeWorld != null) {
                drawWorldContext(g);
                g.translate(originX, originY);
            }
            for (int y = 0; y < design.height(); y++) {
                for (int x = 0; x < design.width(); x++) {
                    drawCell(g, x, y);
                }
            }

            drawPlacements(g);
            drawLights(g);
            drawTriggers(g);
            drawSpawn(g);
            drawInspectionSelection(g);
            if (activeWorld != null) {
                g.translate(-originX, -originY);
                drawWorldSeams(g);
            }
            g.dispose();
        }

        private void drawWorldContext(Graphics2D g) {
            int cellSize = cellSize();
            int chunkPixelWidth = design.width() * cellSize;
            int chunkPixelHeight = design.height() * cellSize;
            for (int slotY = 0; slotY < 3; slotY++) {
                for (int slotX = 0; slotX < 3; slotX++) {
                    if (slotX == 1 && slotY == 1) {
                        continue;
                    }
                    ChunkCoordinate coordinate = new ChunkCoordinate(
                            activeWorldChunk.x() + slotX - 1,
                            activeWorldChunk.y() + slotY - 1);
                    MapDesignLibrary.MapDesign neighbor = worldNeighborDesigns.get(coordinate);
                    int offsetX = slotX * chunkPixelWidth;
                    int offsetY = slotY * chunkPixelHeight;
                    if (neighbor == null) {
                        g.setColor(new Color(12, 14, 18));
                        g.fillRect(offsetX, offsetY, chunkPixelWidth, chunkPixelHeight);
                        g.setColor(new Color(95, 100, 112));
                        g.drawString("Missing chunk " + coordinate, offsetX + 10, offsetY + 20);
                        continue;
                    }
                    for (int y = 0; y < neighbor.height(); y++) {
                        for (int x = 0; x < neighbor.width(); x++) {
                            int px = offsetX + x * cellSize;
                            int py = offsetY + y * cellSize;
                            Color base = tileColor(neighbor.tiles()[y][x]);
                            g.setColor(new Color(
                                    Math.max(0, base.getRed() / 2),
                                    Math.max(0, base.getGreen() / 2),
                                    Math.max(0, base.getBlue() / 2)));
                            g.fillRect(px, py, cellSize, cellSize);
                            g.setColor(new Color(8, 8, 10, 150));
                            g.drawRect(px, py, cellSize, cellSize);
                            drawMobAreaOverlay(g, neighbor.mobAreas().get(x, y),
                                    new Rectangle(px, py, cellSize, cellSize));
                            if (neighbor.mapPaint() != null && neighbor.mapPaint().hasBrush(x, y)) {
                                g.setColor(new Color(90, 160, 205, 150));
                                g.fillRect(px + 2, py + 2, Math.max(2, cellSize / 6), Math.max(2, cellSize / 6));
                            }
                            if (neighbor.mapGeometry() != null
                                    && neighbor.mapGeometry().getHeightLevel(x,
                                            y) != MapGeometryData.DEFAULT_HEIGHT_LEVEL) {
                                g.setColor(new Color(80, 170, 205, 180));
                                g.drawRect(px + 3, py + 3, Math.max(2, cellSize - 7), Math.max(2, cellSize - 7));
                            }
                        }
                    }
                    for (MapDesignLibrary.MapPlacement placement : neighbor.placements()) {
                        g.setColor(new Color(170, 150, 95));
                        g.drawOval(
                                offsetX + placement.x() * cellSize + 4,
                                offsetY + placement.y() * cellSize + 4,
                                Math.max(2, cellSize - 8),
                                Math.max(2, cellSize - 8));
                    }
                    for (MapDesignLibrary.MapTrigger trigger : neighbor.triggers()) {
                        g.setColor(new Color(180, 145, 40));
                        g.drawRect(
                                offsetX + trigger.x() * cellSize + 4,
                                offsetY + trigger.y() * cellSize + 4,
                                Math.max(2, cellSize - 8),
                                Math.max(2, cellSize - 8));
                    }
                    g.setColor(new Color(225, 225, 225));
                    g.drawString("Chunk " + coordinate + " (read-only)", offsetX + 10, offsetY + 20);
                }
            }
        }

        private void drawWorldSeams(Graphics2D g) {
            int chunkPixelWidth = design.width() * cellSize();
            int chunkPixelHeight = design.height() * cellSize();
            g.setColor(new Color(90, 205, 255));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(chunkPixelWidth, chunkPixelHeight, chunkPixelWidth, chunkPixelHeight);
            g.setColor(new Color(175, 185, 198));
            for (int i = 1; i < 3; i++) {
                g.drawLine(i * chunkPixelWidth, 0, i * chunkPixelWidth, chunkPixelHeight * 3);
                g.drawLine(0, i * chunkPixelHeight, chunkPixelWidth * 3, i * chunkPixelHeight);
            }
            g.setColor(Color.WHITE);
            g.drawString("Editing chunk " + activeWorldChunk, chunkPixelWidth + 10, chunkPixelHeight + 20);
        }

        private boolean handleWorldChunkClick(Point point) {
            if (activeWorld == null || activeWorldChunk == null) {
                return false;
            }
            int chunkPixelWidth = design.width() * cellSize();
            int chunkPixelHeight = design.height() * cellSize();
            int slotX = Math.floorDiv(point.x, chunkPixelWidth);
            int slotY = Math.floorDiv(point.y, chunkPixelHeight);
            if (slotX < 0 || slotX > 2 || slotY < 0 || slotY > 2 || (slotX == 1 && slotY == 1)) {
                return false;
            }
            ChunkCoordinate coordinate = new ChunkCoordinate(
                    activeWorldChunk.x() + slotX - 1,
                    activeWorldChunk.y() + slotY - 1);
            switchWorldChunk(coordinate);
            return true;
        }

        private Point activeTileAt(Point point) {
            int cellSize = cellSize();
            int originX = activeWorld == null ? 0 : design.width() * cellSize;
            int originY = activeWorld == null ? 0 : design.height() * cellSize;
            return new Point(
                    Math.floorDiv(point.x - originX, cellSize),
                    Math.floorDiv(point.y - originY, cellSize));
        }

        private void drawCell(Graphics2D g, int x, int y) {
            int cellSize = cellSize();
            Rectangle bounds = new Rectangle(x * cellSize, y * cellSize, cellSize, cellSize);
            Library.TileType tile = design.tiles()[y][x];
            g.setColor(tileColor(tile));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            drawTerrainTint(g, bounds, x, y);
            g.setColor(new Color(10, 10, 12, 120));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            drawMobAreaOverlay(g, design.mobAreas().get(x, y), bounds);

            if (design.mapPaint() != null && design.mapPaint().hasBrush(x, y)) {
                drawBrushIndicators(g, bounds, x, y);
            }
            drawHeightIndicator(g, bounds, x, y);
            drawTerrainEdges(g, bounds, x, y);
        }

        private boolean shouldDrawTerrainOverlay() {
            PaintMode mode = (PaintMode) paintModeBox.getSelectedItem();
            return terrainOverlayBox.isSelected() || mode == PaintMode.SET_HEIGHT;
        }

        private void drawTerrainTint(Graphics2D g, Rectangle bounds, int x, int y) {
            if (!shouldDrawTerrainOverlay()) {
                return;
            }

            int heightLevel = heightLevelAt(x, y);
            float ratio = (heightLevel - MapGeometryData.MIN_HEIGHT_LEVEL)
                    / (float) Math.max(1, MapGeometryData.MAX_HEIGHT_LEVEL - MapGeometryData.MIN_HEIGHT_LEVEL);
            Color tint = Color.getHSBColor(0.34f - ratio * 0.24f, 0.55f, 0.92f);
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 72));
            g.fillRect(bounds.x + 1, bounds.y + 1, Math.max(1, bounds.width - 2), Math.max(1, bounds.height - 2));
        }

        private void drawMobAreaOverlay(Graphics2D g, String areaId, Rectangle bounds) {
            PaintMode mode = (PaintMode) paintModeBox.getSelectedItem();
            if (areaId == null || areaId.isBlank()
                    || (mode != PaintMode.MOB_AREA && mode != PaintMode.CLEAR_MOB_AREA)) {
                return;
            }
            Color color = Color.getHSBColor(Math.floorMod(areaId.hashCode(), 360) / 360.0f, 0.65f, 0.95f);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
            g.fillRect(bounds.x + 1, bounds.y + 1, Math.max(1, bounds.width - 2), Math.max(1, bounds.height - 2));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
            g.drawRect(bounds.x + 2, bounds.y + 2, Math.max(1, bounds.width - 5), Math.max(1, bounds.height - 5));
        }

        private void drawHeightIndicator(Graphics2D g, Rectangle bounds, int x, int y) {
            int heightLevel = heightLevelAt(x, y);
            if (heightLevel == MapGeometryData.DEFAULT_HEIGHT_LEVEL) {
                return;
            }

            String label = "H" + heightLevel;
            FontMetrics metrics = g.getFontMetrics();
            int padding = 3;
            int labelWidth = metrics.stringWidth(label) + padding * 2;
            int labelHeight = metrics.getHeight();
            int labelX = bounds.x + bounds.width - labelWidth - 2;
            int labelY = bounds.y + bounds.height - labelHeight - 2;
            g.setColor(new Color(20, 24, 30, 205));
            g.fillRect(labelX, labelY, labelWidth, labelHeight);
            g.setColor(new Color(120, 215, 255));
            g.drawRect(labelX, labelY, labelWidth, labelHeight);
            g.drawString(label, labelX + padding, labelY + metrics.getAscent());
        }

        private void drawTerrainEdges(Graphics2D g, Rectangle bounds, int x, int y) {
            if (!shouldDrawTerrainOverlay() || design.mapGeometry() == null) {
                return;
            }

            if (x + 1 < design.width()) {
                drawTerrainEdge(g, bounds, x, y, x + 1, y, true);
            }
            if (y + 1 < design.height()) {
                drawTerrainEdge(g, bounds, x, y, x, y + 1, false);
            }
        }

        private void drawTerrainEdge(
                Graphics2D g,
                Rectangle bounds,
                int x1,
                int y1,
                int x2,
                int y2,
                boolean vertical
        ) {
            TerrainEdgeKind kind = TerrainGeometry.edgeKind(heightLevelAt(x1, y1), heightLevelAt(x2, y2));
            if (kind == TerrainEdgeKind.FLAT) {
                return;
            }

            java.awt.Stroke oldStroke = g.getStroke();
            if (kind == TerrainEdgeKind.CLIFF) {
                g.setColor(new Color(150, 55, 35, 235));
                g.setStroke(new BasicStroke(Math.max(3f, bounds.width / 9f)));
            } else {
                g.setColor(new Color(240, 214, 95, 220));
                g.setStroke(new BasicStroke(Math.max(1.5f, bounds.width / 18f)));
            }

            if (vertical) {
                int edgeX = bounds.x + bounds.width;
                g.drawLine(edgeX, bounds.y + 2, edgeX, bounds.y + bounds.height - 2);
            } else {
                int edgeY = bounds.y + bounds.height;
                g.drawLine(bounds.x + 2, edgeY, bounds.x + bounds.width - 2, edgeY);
            }
            g.setStroke(oldStroke);
        }

        private int heightLevelAt(int x, int y) {
            return design.mapGeometry() == null
                    ? MapGeometryData.DEFAULT_HEIGHT_LEVEL
                    : design.mapGeometry().getHeightLevel(x, y);
        }

        private void drawBrushIndicators(Graphics2D g, Rectangle bounds, int x, int y) {
            int cellSize = bounds.width;
            int badgeSize = Math.max(9, Math.min(16, cellSize / 3));
            int offset = 2;
            int index = 0;
            for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                String brushId = design.mapPaint().get(layer, x, y);
                if (brushId.isBlank()) {
                    continue;
                }

                Color color = brushIndicatorColor(brushId, layer);
                int badgeX = bounds.x + offset + index * (badgeSize + 2);
                int badgeY = bounds.y + offset;
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
                g.fillRect(badgeX, badgeY, badgeSize, badgeSize);
                g.setColor(new Color(10, 12, 14, 210));
                g.drawRect(badgeX, badgeY, badgeSize, badgeSize);
                g.setColor(Color.WHITE);
                g.drawString(layer.name().substring(0, 1), badgeX + 3, badgeY + badgeSize - 3);
                index++;

                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
                switch (layer) {
                    case FLOOR -> g.fillRect(bounds.x + 2, bounds.y + bounds.height - 5, bounds.width - 4, 3);
                    case WALL -> g.fillRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, 3);
                    case DOOR -> g.fillRect(bounds.x + 2, bounds.y + 2, 3, bounds.height - 4);
                    case ROOF -> g.fillRect(bounds.x + bounds.width - 5, bounds.y + 2, 3, bounds.height - 4);
                }
            }
        }

        private Color brushIndicatorColor(String brushId, MapPaintData.Layer layer) {
            PaintBrushLibrary.PaintBrush brush = PaintBrushLibrary.find(brushId);
            int seed = brush == null ? brushId.hashCode() : brush.paletteId().hashCode();
            float hue = Math.floorMod(seed, 360) / 360.0f;
            float saturation = switch (layer) {
                case FLOOR -> 0.55f;
                case WALL -> 0.70f;
                case DOOR -> 0.85f;
                case ROOF -> 0.45f;
            };
            return Color.getHSBColor(hue, saturation, 0.88f);
        }

        private void drawPlacements(Graphics2D g) {
            int cellSize = cellSize();
            int inset = Math.max(2, Math.min(6, cellSize / 5));
            g.setStroke(new BasicStroke(2f));
            FontMetrics metrics = g.getFontMetrics();
            for (MapDesignLibrary.MapPlacement placement : design.placements()) {
                int x = placement.x() * cellSize;
                int y = placement.y() * cellSize;
                g.setColor(placementColor(placement.kind()));
                g.drawOval(x + inset, y + inset, cellSize - inset * 2, cellSize - inset * 2);
                String label = placement.kind().name().substring(0, 1);
                g.drawString(label, x + (cellSize - metrics.stringWidth(label)) / 2,
                        y + Math.max(14, cellSize / 2 + 5));
            }
            for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
                int x = object.x() * cellSize;
                int y = object.y() * cellSize;
                int centerX = x + cellSize / 2;
                int centerY = y + cellSize / 2;
                g.setColor(placementColor(object.kind()));
                g.drawRect(x + inset, y + inset, cellSize - inset * 2, cellSize - inset * 2);
                double radians = Math.toRadians(object.yawDegrees() - 90.0);
                int tickLength = Math.max(6, cellSize / 3);
                int endX = centerX + (int) Math.round(Math.cos(radians) * tickLength);
                int endY = centerY + (int) Math.round(Math.sin(radians) * tickLength);
                g.drawLine(centerX, centerY, endX, endY);
                String label = object.kind().name().substring(0, 1);
                g.drawString(label, x + (cellSize - metrics.stringWidth(label)) / 2,
                        y + Math.max(14, cellSize / 2 + 5));
            }
        }

        private void drawTriggers(Graphics2D g) {
            int cellSize = cellSize();
            int inset = Math.max(2, Math.min(7, cellSize / 5));
            g.setStroke(new BasicStroke(2.4f));
            for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
                int triggerCenterX = trigger.x() * cellSize + cellSize / 2;
                int triggerCenterY = trigger.y() * cellSize + cellSize / 2;

                g.setColor(new Color(255, 205, 70));
                g.drawRect(trigger.x() * cellSize + inset, trigger.y() * cellSize + inset, cellSize - inset * 2,
                        cellSize - inset * 2);
                g.drawString("T", trigger.x() * cellSize + 10, trigger.y() * cellSize + Math.max(14, cellSize / 2 + 5));

                for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                    int targetCenterX = action.targetX() * cellSize + cellSize / 2;
                    int targetCenterY = action.targetY() * cellSize + cellSize / 2;
                    g.setColor(new Color(255, 205, 70, 150));
                    g.drawLine(triggerCenterX, triggerCenterY, targetCenterX, targetCenterY);
                    g.setColor(new Color(255, 105, 80));
                    g.drawOval(action.targetX() * cellSize + inset, action.targetY() * cellSize + inset,
                            cellSize - inset * 2, cellSize - inset * 2);
                }
            }
        }

        private void drawLights(Graphics2D g) {
            int cellSize = cellSize();
            java.awt.Stroke oldStroke = g.getStroke();
            FontMetrics metrics = g.getFontMetrics();
            for (MapLight light : design.lights()) {
                int centerX = light.x() * cellSize + cellSize / 2;
                int centerY = light.y() * cellSize + cellSize / 2;
                drawLightRadiusMarker(g, metrics, centerX, centerY, light.radius(), light.colorRgb(),
                        light.enabled(), "L", cellSize);
            }
            for (MapDesignLibrary.PlacedObjectInstance object : design.placedObjects()) {
                MapDesignLibrary.LightAttachment light = effectiveLightFor(object);
                if (light == null) {
                    continue;
                }
                double rotatedOffsetX = rotatedLocalOffsetX(object.yawDegrees(), light.offsetX(), light.offsetZ());
                double rotatedOffsetZ = rotatedLocalOffsetZ(object.yawDegrees(), light.offsetX(), light.offsetZ());
                int centerX = (int) Math.round((object.x() + 0.5 + object.offsetX() + rotatedOffsetX) * cellSize);
                int centerY = (int) Math.round((object.y() + 0.5 + object.offsetZ() + rotatedOffsetZ) * cellSize);
                String label = object.kind() == MapDesignLibrary.PlacementKind.FURNITURE ? "F" : "G";
                drawLightRadiusMarker(g, metrics, centerX, centerY, light.radius(), light.colorRgb(),
                        light.enabled(), label, cellSize);
            }
            g.setStroke(oldStroke);
        }

        private void drawLightRadiusMarker(
                Graphics2D g,
                FontMetrics metrics,
                int centerX,
                int centerY,
                double radius,
                int colorRgb,
                boolean enabled,
                String label,
                int cellSize
        ) {
            int radiusPixels = (int) Math.round(radius * cellSize);
            Color color = new Color(colorRgb);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), enabled ? 50 : 24));
            g.fillOval(centerX - radiusPixels, centerY - radiusPixels, radiusPixels * 2, radiusPixels * 2);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), enabled ? 180 : 90));
            g.setStroke(new BasicStroke(Math.max(1.5f, cellSize / 18f)));
            g.drawOval(centerX - radiusPixels, centerY - radiusPixels, radiusPixels * 2, radiusPixels * 2);
            g.setColor(new Color(20, 20, 22, 220));
            int markerSize = Math.max(8, cellSize / 3);
            g.fillOval(centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize);
            g.setColor(color);
            g.drawOval(centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize);
            g.drawString(label, centerX - metrics.stringWidth(label) / 2, centerY + metrics.getAscent() / 2 - 1);
        }

        private void drawSpawn(Graphics2D g) {
            int cellSize = cellSize();
            int spawnTileX = design.spawnX();
            int spawnTileY = design.spawnY();
            if (activeWorld != null) {
                ChunkCoordinate spawnChunk = activeWorld.chunkForGlobal(activeWorld.startX(), activeWorld.startY());
                if (!spawnChunk.equals(activeWorldChunk)) {
                    return;
                }
                spawnTileX = activeWorld.localX(activeWorld.startX());
                spawnTileY = activeWorld.localY(activeWorld.startY());
            }
            int x = spawnTileX * cellSize;
            int y = spawnTileY * cellSize;
            int inset = Math.max(2, Math.min(8, cellSize / 4));
            g.setColor(new Color(80, 220, 255));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(x + inset, y + inset, cellSize - inset * 2, cellSize - inset * 2);
            g.drawLine(x + cellSize / 2, y + inset, x + cellSize / 2, y + cellSize - inset);
            g.drawLine(x + inset, y + cellSize / 2, x + cellSize - inset, y + cellSize / 2);
        }

        private void drawInspectionSelection(Graphics2D g) {
            if (inspectedTile == null || !isTileInBounds(inspectedTile.x, inspectedTile.y)) {
                return;
            }

            int cellSize = cellSize();
            g.setStroke(new BasicStroke(3f));
            Color color = new Color(255, 238, 90);
            if (inspectedPlacement != null && design.placements().contains(inspectedPlacement)) {
                color = new Color(120, 190, 255);
            } else if (inspectedPlacedObject != null && design.placedObjects().contains(inspectedPlacedObject)) {
                color = new Color(190, 155, 95);
            } else if (inspectedTrigger != null && design.triggers().contains(inspectedTrigger)) {
                color = inspectedTriggerTarget == null ? new Color(255, 210, 70) : new Color(255, 135, 90);
            } else if (inspectedLight != null && design.lights().contains(inspectedLight)) {
                color = new Color(inspectedLight.colorRgb());
            }

            g.setColor(color);
            g.drawRect(
                    inspectedTile.x * cellSize + 2,
                    inspectedTile.y * cellSize + 2,
                    cellSize - 5,
                    cellSize - 5);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            g.fillRect(
                    inspectedTile.x * cellSize + 3,
                    inspectedTile.y * cellSize + 3,
                    cellSize - 6,
                    cellSize - 6);
        }

        private void inspectAt(Point point) {
            Point tile = activeTileAt(point);
            int x = tile.x;
            int y = tile.y;

            if (x < 0 || y < 0 || x >= design.width() || y >= design.height()) {
                return;
            }

            MapDesignLibrary.MapPlacement placement = placementAt(x, y);
            if (placement != null) {
                revealContentEntry(placement, ContentCategory.PLACEMENTS);
                setInspectedSelection(x, y, placement, null, null);
                setStatus("Selected placement " + placement.kind() + " " + placement.id() + " at " + x + "," + y + ".");
                return;
            }

            MapDesignLibrary.PlacedObjectInstance object = placedObjectAt(x, y);
            if (object != null) {
                revealContentEntry(object, ContentCategory.PLACEMENTS);
                setInspectedPlacedObjectSelection(x, y, object);
                setStatus("Selected placed object " + object.kind() + " " + object.id() + " at " + x + "," + y + ".");
                return;
            }

            MapLight light = lightAt(x, y);
            if (light != null) {
                revealContentEntry(light, ContentCategory.LIGHTS);
                setInspectedSelection(x, y, null, null, null);
                inspectedLight = light;
                mapCanvas.repaint();
                setStatus("Selected light " + light.id() + " at " + x + "," + y + ".");
                return;
            }

            MapDesignLibrary.MapTrigger trigger = triggerAt(x, y);
            if (trigger != null) {
                revealContentEntry(trigger, ContentCategory.TRIGGERS);
                setInspectedSelection(x, y, null, trigger, null);
                setStatus("Selected trigger " + trigger.id() + " at " + x + "," + y + ".");
                return;
            }

            MapDesignLibrary.MapTrigger wiredTrigger = triggerTargetAt(x, y);
            if (wiredTrigger != null) {
                revealContentEntry(wiredTrigger, ContentCategory.TRIGGERS);
                setInspectedSelection(x, y, null, wiredTrigger, new Point(x, y));
                setStatus("Selected trigger " + wiredTrigger.id() + " wired to " + x + "," + y + ".");
                return;
            }

            contentList.clearSelection();
            setInspectedSelection(x, y, null, null, null);
            inspectorArea.setText(tileInspectionText(x, y));
            inspectorArea.setCaretPosition(0);
            setStatus("Inspecting tile " + x + "," + y + ".");
        }

        private void showMapContextMenu(MouseEvent event) {
            Point tile = activeTileAt(event.getPoint());
            int x = tile.x;
            int y = tile.y;
            if (!isTileInBounds(x, y)) {
                return;
            }

            MapDesignLibrary.MapPlacement placement = placementAt(x, y);
            MapDesignLibrary.PlacedObjectInstance object = placedObjectAt(x, y);
            MapLight light = lightAt(x, y);
            MapDesignLibrary.MapTrigger trigger = triggerAt(x, y);
            JPopupMenu menu = new JPopupMenu();

            if (placement != null) {
                addMenuItem(menu, "Edit Placement", () -> editMapPlacement(placement));
                addMenuItem(menu, "Duplicate Placement", () -> duplicateMapPlacement(placement));
                addMenuItem(menu, "Delete Placement", () -> deleteMapPlacement(placement));
                menu.addSeparator();
                addMenuItem(menu, "Select For Painting", () -> {
                    revealContentEntry(placement, ContentCategory.PLACEMENTS);
                    selectContentForPlacement();
                });
                menu.addSeparator();
            }

            if (object != null) {
                addMenuItem(menu, "Edit Placed Object", () -> editPlacedObject(object));
                addMenuItem(menu, "Duplicate Placed Object", () -> duplicatePlacedObject(object));
                addMenuItem(menu, "Delete Placed Object", () -> deletePlacedObject(object));
                menu.addSeparator();
            }

            addMenuItem(menu, "Manage Tile Objects", () -> manageTileObjects(x, y));

            if (light != null) {
                menu.addSeparator();
                addMenuItem(menu, "Edit Light", () -> editLight(light));
                addMenuItem(menu, "Delete Light", () -> deleteLight(light));
                menu.addSeparator();
            }

            if (trigger != null) {
                addMenuItem(menu, "Manage Trigger", AetherConstructionKit.this::manageTriggers);
                addMenuItem(menu, "Wire Trigger", () -> {
                    wiringTriggerId = trigger.id();
                    pendingTriggerId = "";
                    paintModeBox.setSelectedItem(PaintMode.WIRE_TRIGGER);
                    setStatus("Click door tiles to wire targets for " + trigger.id() + ".");
                });
                menu.addSeparator();
            }

            addMenuItem(menu, "Set Spawn Here", () -> {
                captureHistory("set spawn");
                setSpawn(x, y);
                markDirty(true);
                repaint();
            });
            addMenuItem(menu, "Set Height Here", () -> {
                captureHistory("set height");
                setHeightLevel(x, y);
                markDirty(true);
                repaint();
            });
            addMenuItem(menu, "Copy Coordinates", () -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(x + "," + y), null);
                setStatus("Copied coordinates " + x + "," + y + ".");
            });
            addMenuItem(menu, "Inspect Tile", () -> inspectAt(event.getPoint()));

            menu.show(this, event.getX(), event.getY());
        }

        private boolean isTileInBounds(int x, int y) {
            return x >= 0 && y >= 0 && x < design.width() && y < design.height();
        }

        private void setInspectedSelection(
                int x,
                int y,
                MapDesignLibrary.MapPlacement placement,
                MapDesignLibrary.MapTrigger trigger,
                Point triggerTarget) {
            inspectedTile = new Point(x, y);
            inspectedPlacement = placement;
            inspectedPlacedObject = null;
            inspectedTrigger = trigger;
            inspectedLight = null;
            inspectedTriggerTarget = triggerTarget == null ? null : new Point(triggerTarget);
            repaint();
        }

        private void setInspectedPlacedObjectSelection(
                int x,
                int y,
                MapDesignLibrary.PlacedObjectInstance object) {
            inspectedTile = new Point(x, y);
            inspectedPlacement = null;
            inspectedPlacedObject = object;
            inspectedTrigger = null;
            inspectedLight = null;
            inspectedTriggerTarget = null;
            repaint();
        }

        private MapDesignLibrary.MapPlacement placementAt(int x, int y) {
            for (int i = design.placements().size() - 1; i >= 0; i--) {
                MapDesignLibrary.MapPlacement placement = design.placements().get(i);
                if (placement.x() == x && placement.y() == y) {
                    return placement;
                }
            }
            return null;
        }

        private MapDesignLibrary.PlacedObjectInstance placedObjectAt(int x, int y) {
            for (int i = design.placedObjects().size() - 1; i >= 0; i--) {
                MapDesignLibrary.PlacedObjectInstance object = design.placedObjects().get(i);
                if (object.x() == x && object.y() == y) {
                    return object;
                }
            }
            return null;
        }

        private MapDesignLibrary.MapTrigger triggerAt(int x, int y) {
            for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
                if (trigger.x() == x && trigger.y() == y) {
                    return trigger;
                }
            }
            return null;
        }

        private MapLight lightAt(int x, int y) {
            for (int i = design.lights().size() - 1; i >= 0; i--) {
                MapLight light = design.lights().get(i);
                if (light.x() == x && light.y() == y) {
                    return light;
                }
            }
            return null;
        }

        private MapDesignLibrary.MapTrigger triggerTargetAt(int x, int y) {
            for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
                for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                    if (action.targetX() == x && action.targetY() == y) {
                        return trigger;
                    }
                }
            }
            return null;
        }

        private String tileInspectionText(int x, int y) {
            Library.TileType tile = design.tiles()[y][x];
            StringBuilder builder = new StringBuilder();
            builder.append("Tile\n");
            builder.append(x).append(',').append(y).append("\n\n");
            builder.append("Type: ").append(tile).append('\n');
            builder.append("Blocks Movement: ").append(tile.blocksMovement()).append('\n');
            builder.append("Height Level: ")
                    .append(heightLevelAt(x, y))
                    .append('\n');
            builder.append("Terrain Edges: ").append(terrainEdgeSummary(x, y)).append('\n');
            builder.append("Default Theme: ").append(design.primaryTheme().getDisplayName()).append('\n');
            List<MapDesignLibrary.PlacedObjectInstance> objects = placedObjectsAt(x, y);
            if (!objects.isEmpty()) {
                builder.append('\n').append("Placed Objects").append('\n');
                for (MapDesignLibrary.PlacedObjectInstance object : objects) {
                    builder.append("- ").append(placedObjectLabel(object)).append('\n');
                }
            }
            if (design.mapPaint() != null && design.mapPaint().hasBrush(x, y)) {
                builder.append('\n').append("Brush Overrides").append('\n');
                for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                    String brushId = design.mapPaint().get(layer, x, y);
                    if (brushId.isBlank()) {
                        continue;
                    }
                    PaintBrushLibrary.PaintBrush brush = PaintBrushLibrary.find(brushId);
                    builder.append(layer).append(": ")
                            .append(brush == null ? brushId : brush.displayName())
                            .append('\n');
                }
            }
            builder.append("Spawn: ").append(x == design.spawnX() && y == design.spawnY()).append('\n');
            return builder.toString();
        }

        private String terrainEdgeSummary(int x, int y) {
            List<String> parts = new ArrayList<>();
            addTerrainEdgeSummary(parts, "N", x, y, x, y - 1);
            addTerrainEdgeSummary(parts, "E", x, y, x + 1, y);
            addTerrainEdgeSummary(parts, "S", x, y, x, y + 1);
            addTerrainEdgeSummary(parts, "W", x, y, x - 1, y);
            return String.join(", ", parts);
        }

        private void addTerrainEdgeSummary(List<String> parts, String label, int x1, int y1, int x2, int y2) {
            if (!isTileInBounds(x2, y2)) {
                parts.add(label + " edge");
                return;
            }
            TerrainEdgeKind kind = TerrainGeometry.edgeKind(heightLevelAt(x1, y1), heightLevelAt(x2, y2));
            int delta = heightLevelAt(x2, y2) - heightLevelAt(x1, y1);
            parts.add(label + " " + kind.name().toLowerCase(Locale.ROOT) + " " + signed(delta));
        }

        private String signed(int value) {
            return value > 0 ? "+" + value : String.valueOf(value);
        }

        private void paintAt(Point point) {
            Point tile = activeTileAt(point);
            int x = tile.x;
            int y = tile.y;

            if (x < 0 || y < 0 || x >= design.width() || y >= design.height()) {
                return;
            }

            PaintMode mode = (PaintMode) paintModeBox.getSelectedItem();
            if (mode == null) {
                return;
            }

            captureHistory(mode.toString());
            if (usesBrush(mode)) {
                int brushSize = ((Number) brushSizeSpinner.getValue()).intValue();
                int radius = Math.max(0, brushSize / 2);
                for (int brushY = y - radius; brushY <= y + radius; brushY++) {
                    for (int brushX = x - radius; brushX <= x + radius; brushX++) {
                        if (brushX >= 0 && brushY >= 0 && brushX < design.width() && brushY < design.height()) {
                            applyPaintMode(mode, brushX, brushY);
                        }
                    }
                }
            } else {
                applyPaintMode(mode, x, y);
            }

            markDirty(true);
            repaint();
        }

        private boolean usesBrush(PaintMode mode) {
            return mode == PaintMode.TILE
                    || mode == PaintMode.FLOOR_BRUSH
                    || mode == PaintMode.WALL_BRUSH
                    || mode == PaintMode.DOOR_BRUSH
                    || mode == PaintMode.ROOF_BRUSH
                    || mode == PaintMode.MOB_AREA
                    || mode == PaintMode.CLEAR_MOB_AREA
                    || mode == PaintMode.CLEAR_BRUSH
                    || mode == PaintMode.SET_HEIGHT
                    || mode == PaintMode.ERASE_OBJECT;
        }

        private void applyPaintMode(PaintMode mode, int x, int y) {
            switch (mode) {
                case TILE -> {
                    design.tiles()[y][x] = (Library.TileType) tileTypeBox.getSelectedItem();
                    design.themeIndexes()[y][x] = 0;
                    if (x == design.spawnX() && y == design.spawnY() && design.tiles()[y][x].blocksMovement()) {
                        setStatus("Spawn is now blocked; set a new spawn on a walkable tile.");
                    }
                }
                case FLOOR_BRUSH -> applyBrush(MapPaintData.Layer.FLOOR, x, y);
                case WALL_BRUSH -> applyBrush(MapPaintData.Layer.WALL, x, y);
                case DOOR_BRUSH -> applyBrush(MapPaintData.Layer.DOOR, x, y);
                case ROOF_BRUSH -> applyBrush(MapPaintData.Layer.ROOF, x, y);
                case MOB_AREA -> paintMobArea(x, y);
                case CLEAR_MOB_AREA -> clearMobArea(x, y);
                case CLEAR_BRUSH -> clearBrushes(x, y);
                case SET_HEIGHT -> setHeightLevel(x, y);
                case PLACE_OBJECT -> placeObject(x, y);
                case PLACE_LIGHT -> placeLight(x, y);
                case ERASE_OBJECT -> eraseObject(x, y);
                case SET_SPAWN -> setSpawn(x, y);
                case PLACE_TRIGGER -> placeTrigger(x, y);
                case WIRE_TRIGGER -> wireTriggerTarget(x, y);
                case PLACE_PREFAB -> placePrefab(x, y);
            }
        }

        private void applyBrush(MapPaintData.Layer layer, int x, int y) {
            PaintBrushLibrary.PaintBrush brush = (PaintBrushLibrary.PaintBrush) brushBox.getSelectedItem();
            if (brush == null) {
                setStatus("No brush selected.");
                return;
            }
            if (brush.layer() != layer) {
                populateBrushes();
                brush = (PaintBrushLibrary.PaintBrush) brushBox.getSelectedItem();
                if (brush == null || brush.layer() != layer) {
                    setStatus("No " + layer.name().toLowerCase(Locale.ROOT) + " brush selected.");
                    return;
                }
            }

            design.mapPaint().set(layer, x, y, brush.id());
            setStatus("Painted " + brush.displayName() + " at " + x + "," + y + ".");
        }

        private void paintMobArea(int x, int y) {
            String areaId = (String) mobAreaBox.getSelectedItem();
            if (areaId == null || areaId.isBlank()) {
                setStatus("Create or select a mob area first.");
                return;
            }
            design.mobAreas().set(x, y, areaId);
            setStatus("Painted mob area " + areaId + " at " + x + "," + y + ".");
        }

        private void clearMobArea(int x, int y) {
            design.mobAreas().set(x, y, "");
            setStatus("Cleared mob area at " + x + "," + y + ".");
        }

        private void clearBrushes(int x, int y) {
            for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                design.mapPaint().set(layer, x, y, "");
            }
            setStatus("Cleared brush overrides at " + x + "," + y + ".");
        }

        private void setHeightLevel(int x, int y) {
            int heightLevel = ((Number) heightLevelSpinner.getValue()).intValue();
            if (design.mapGeometry() != null) {
                design.mapGeometry().setHeightLevel(x, y, heightLevel);
            }
            setStatus("Set elevation " + heightLevel + " at " + x + "," + y + ".");
        }

        private void setSpawn(int x, int y) {
            if (design.tiles()[y][x].blocksMovement()) {
                setStatus("Spawn must be on a walkable tile.");
                return;
            }

            if (activeWorld != null && activeWorldChunk != null) {
                int globalX = activeWorld.globalX(activeWorldChunk, x);
                int globalY = activeWorld.globalY(activeWorldChunk, y);
                activeWorld = activeWorld.withMetadata(
                        activeWorld.displayName(),
                        activeWorld.description(),
                        globalX,
                        globalY);
                setStatus("Set world spawn to global " + globalX + "," + globalY + ".");
                return;
            }

            design = new MapDesignLibrary.MapDesign(
                    design.width(),
                    design.height(),
                    design.displayName(),
                    design.description(),
                    design.musicPath(),
                    design.skyboxPath(),
                    design.primaryTheme(),
                    design.primaryTheme(),
                    design.tiles(),
                    design.themeIndexes(),
                    design.mapPaint(),
                    design.mapGeometry(),
                    design.mobAreas(),
                    design.placements(),
                    design.placedObjects(),
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
                    design.customFurniture(),
                    design.customGatheringNodes(),
                    design.customCookingRecipes(),
                    design.craftingRecipes(),
                    design.triggers(),
                    design.lightingSettings(),
                    design.lights(),
                    x,
                    y);
            setStatus("Set spawn to " + x + "," + y + ".");
        }

        private void placeObject(int x, int y) {
            PlaceableOption option = (PlaceableOption) placeableBox.getSelectedItem();
            if (option == null || option.kind() == null || option.id().isBlank()) {
                return;
            }

            if (isTransformablePlaceable(option)) {
                MapDesignLibrary.PlacedObjectInstance object = new MapDesignLibrary.PlacedObjectInstance(
                        nextPlacedObjectId(option.id()),
                        option.kind(),
                        option.id(),
                        x,
                        y,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        1.0,
                        defaultBlocksMovementFor(option),
                        null);
                design.placedObjects().add(object);
                refreshContentBrowser();
                revealPlacedObject(object);
                setStatus("Placed " + option.label() + " at " + x + "," + y + ".");
                return;
            }

            eraseObject(x, y);
            design.placements().add(new MapDesignLibrary.MapPlacement(option.kind(), option.id(), x, y));
            refreshContentBrowser();
            setStatus("Placed " + option.label() + " at " + x + "," + y + ".");
        }

        private void placePrefab(int x, int y) {
            MapPrefab prefab = (MapPrefab) prefabBox.getSelectedItem();
            if (prefab == null) {
                setStatus("No prefab selected.");
                return;
            }
            if (x + prefab.width() > design.width() || y + prefab.height() > design.height()) {
                setStatus("Prefab does not fit at " + x + "," + y + ".");
                return;
            }

            for (int prefabY = 0; prefabY < prefab.height(); prefabY++) {
                for (int prefabX = 0; prefabX < prefab.width(); prefabX++) {
                    int worldX = x + prefabX;
                    int worldY = y + prefabY;
                    design.tiles()[worldY][worldX] = prefab.tiles()[prefabY][prefabX];
                    design.themeIndexes()[worldY][worldX] = 0;
                    if (design.mapGeometry() != null) {
                        design.mapGeometry().setHeightLevel(worldX, worldY,
                                prefab.geometryData().getHeightLevel(prefabX, prefabY));
                    }
                    if (design.mapPaint() != null) {
                        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                            design.mapPaint().set(layer, worldX, worldY,
                                    prefab.paintData().get(layer, prefabX, prefabY));
                        }
                    }
                    if (design.mobAreas() != null) {
                        design.mobAreas().set(worldX, worldY, prefab.mobAreas().get(prefabX, prefabY));
                    }
                    eraseObject(worldX, worldY);
                }
            }
            for (MapDesignLibrary.MapPlacement placement : prefab.placements()) {
                design.placements().add(new MapDesignLibrary.MapPlacement(
                        placement.kind(),
                        placement.id(),
                        x + placement.x(),
                        y + placement.y()));
            }
            for (MapDesignLibrary.MapTrigger trigger : prefab.triggers()) {
                String triggerId = uniqueTriggerId(trigger.id());
                List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                        .map(action -> new MapDesignLibrary.TriggerAction(
                                action.type(),
                                x + action.targetX(),
                                y + action.targetY()))
                        .toList();
                design.triggers().add(new MapDesignLibrary.MapTrigger(
                        triggerId,
                        x + trigger.x(),
                        y + trigger.y(),
                        trigger.fireMode(),
                        trigger.oneShot(),
                        trigger.requiredQuestId(),
                        trigger.requiredQuestProgress(),
                        actions));
            }
            refreshContentBrowser();
            setStatus("Stamped prefab " + prefab.name() + " at " + x + "," + y + ".");
        }

        private String uniqueTriggerId(String baseId) {
            String base = safeId(baseId).isBlank() ? "trigger" : safeId(baseId);
            String candidate = base;
            int suffix = 2;
            while (findTrigger(candidate) != null) {
                candidate = base + "_" + suffix;
                suffix++;
            }
            return candidate;
        }

        private void eraseObject(int x, int y) {
            int placementsBefore = design.placements().size();
            design.placements().removeIf(placement -> placement.x() == x && placement.y() == y);

            int placedObjectsBefore = design.placedObjects().size();
            design.placedObjects().removeIf(object -> object.x() == x && object.y() == y);

            int lightsBefore = design.lights().size();
            design.lights().removeIf(light -> light.x() == x && light.y() == y);

            int triggersBefore = design.triggers().size();
            design.triggers().removeIf(trigger -> trigger.x() == x && trigger.y() == y);

            int removedWireTargets = removeTriggerTargetsAt(x, y);
            int removedPlacements = placementsBefore - design.placements().size();
            int removedPlacedObjects = placedObjectsBefore - design.placedObjects().size();
            int removedLights = lightsBefore - design.lights().size();
            int removedTriggers = triggersBefore - design.triggers().size();

            if (removedTriggers > 0) {
                setStatus("Removed trigger at " + x + "," + y + ".");
            } else if (removedWireTargets > 0) {
                setStatus("Removed " + removedWireTargets + " trigger wire target(s) at " + x + "," + y + ".");
            } else if (removedLights > 0) {
                setStatus("Removed light at " + x + "," + y + ".");
            } else if (removedPlacedObjects > 0) {
                setStatus("Removed placed object at " + x + "," + y + ".");
            } else if (removedPlacements > 0) {
                setStatus("Removed placement at " + x + "," + y + ".");
            } else {
                setStatus("Nothing to erase at " + x + "," + y + ".");
            }
            if (removedTriggers > 0 || removedWireTargets > 0 || removedLights > 0
                    || removedPlacedObjects > 0 || removedPlacements > 0) {
                refreshContentBrowser();
            }
        }

        private int removeTriggerTargetsAt(int x, int y) {
            int removed = 0;
            List<MapDesignLibrary.MapTrigger> updatedTriggers = new ArrayList<>();

            for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
                List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>(trigger.actions());
                int before = actions.size();
                actions.removeIf(action -> action.targetX() == x && action.targetY() == y);
                removed += before - actions.size();

                if (before != actions.size()) {
                    updatedTriggers.add(new MapDesignLibrary.MapTrigger(
                            trigger.id(),
                            trigger.x(),
                            trigger.y(),
                            trigger.fireMode(),
                            trigger.oneShot(),
                            trigger.requiredQuestId(),
                            trigger.requiredQuestProgress(),
                            actions));
                } else {
                    updatedTriggers.add(trigger);
                }
            }

            if (removed > 0) {
                design.triggers().clear();
                design.triggers().addAll(updatedTriggers);
            }

            return removed;
        }

        private void placeTrigger(int x, int y) {
            if (pendingTriggerId == null || pendingTriggerId.isBlank()) {
                setStatus("Use Create > Trigger first.");
                return;
            }

            if (design.tiles()[y][x].blocksMovement()) {
                setStatus("Triggers should be placed on walkable tiles.");
                return;
            }

            MapDesignLibrary.MapTrigger trigger = new MapDesignLibrary.MapTrigger(
                    pendingTriggerId,
                    x,
                    y,
                    pendingTriggerFireMode,
                    pendingTriggerOneShot,
                    pendingTriggerQuestId,
                    pendingTriggerQuestProgress,
                    List.of());
            design.triggers().removeIf(existing -> existing.id().equals(trigger.id()));
            design.triggers().add(trigger);
            wiringTriggerId = trigger.id();
            pendingTriggerId = "";
            pendingTriggerFireMode = MapDesignLibrary.TriggerFireMode.ON_ENTRY;
            pendingTriggerOneShot = true;
            pendingTriggerQuestId = "";
            pendingTriggerQuestProgress = "";
            paintModeBox.setSelectedItem(PaintMode.WIRE_TRIGGER);
            refreshContentBrowser();
            String action = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS ? "open" : "close";
            setStatus("Placed " + trigger.id() + ". Click door tiles to wire " + action + " targets.");
        }

        private void wireTriggerTarget(int x, int y) {
            MapDesignLibrary.MapTrigger trigger = findTrigger(wiringTriggerId);
            if (trigger == null) {
                setStatus("Choose Manage > Triggers > Wire Targets first.");
                return;
            }

            if (!isDoorTile(design.tiles()[y][x])) {
                setStatus("Trigger targets must be door tiles.");
                return;
            }

            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                if (action.targetX() == x && action.targetY() == y) {
                    removeTriggerTarget(trigger.id(), x + "," + y);
                    refreshContentBrowser();
                    setStatus("Removed wire from " + trigger.id() + " to door at " + x + "," + y + ".");
                    return;
                }
            }

            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>(trigger.actions());
            MapDesignLibrary.TriggerActionType actionType = trigger
                    .fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS
                            ? MapDesignLibrary.TriggerActionType.OPEN_DOOR
                            : MapDesignLibrary.TriggerActionType.CLOSE_DOOR;
            actions.add(new MapDesignLibrary.TriggerAction(actionType, x, y));
            replaceTrigger(trigger, new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    actions));
            refreshContentBrowser();
            String action = actionType == MapDesignLibrary.TriggerActionType.OPEN_DOOR ? "open" : "close";
            setStatus("Wired " + trigger.id() + " to " + action + " door at " + x + "," + y + ".");
        }

        private Color tileColor(Library.TileType tile) {
            int adjustment = 0;
            return switch (tile) {
                case FLOOR -> editorTileColor(tile, adjustment);
                case WALL -> editorTileColor(tile, adjustment);
                case DOOR_CLOSED -> editorTileColor(tile, adjustment);
                case DOOR_OPEN -> editorTileColor(tile, adjustment);
                case QUEST_DOOR_OPEN -> editorTileColor(tile, adjustment);
                case QUEST_DOOR_CLOSED -> editorTileColor(tile, adjustment);
                case FISHING_WATER -> editorTileColor(tile, adjustment);
                case WATER -> editorTileColor(tile, adjustment);
                case TRAP -> editorTileColor(tile, adjustment);
                case STAIRS_DOWN -> editorTileColor(tile, adjustment);
                case STAIRS_UP -> editorTileColor(tile, adjustment);
            };
        }

        private Color placementColor(MapDesignLibrary.PlacementKind kind) {
            return switch (kind) {
                case CRAFTING_NODE -> new Color(240, 130, 70);
                case GATHERING_NODE -> new Color(90, 220, 130);
                case FURNITURE -> new Color(178, 145, 96);
                case CUSTOM_NPC -> new Color(220, 90, 220);
                case ITEM -> new Color(230, 210, 80);
                case ENEMY -> new Color(230, 80, 70);
                case INTERACTION -> new Color(90, 200, 230);
            };
        }
    }

}

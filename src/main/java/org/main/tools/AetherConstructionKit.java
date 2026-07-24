package org.main.tools;

import org.main.battle.DifficultyResolver;
import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.PaintBrushLibrary;
import org.main.core.CraftingSystem;
import org.main.core.CraftingStationType;
import org.main.content.SkillLibrary;
import org.main.content.ThemeLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.content.WorldManifestLibrary.ChunkCoordinate;
import org.main.content.WorldManifestLibrary.WorldManifest;
import org.main.core.CharacterSkill;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.GameConfiguration;
import org.main.core.GearMaterial;
import org.main.core.GearDurability;
import org.main.core.InventorySystem;
import org.main.core.InteractionSystem;
import org.main.core.Library;
import org.main.core.LimbSlot;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerCharacterModelConfiguration;
import org.main.core.PlayerStat;
import org.main.core.WeaponType;
import org.main.engine.AssetLoader;
import org.main.engine.MapLight;
import org.main.engine.MapLightingSettings;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.engine.SkyboxSpec;
import org.main.engine.TerrainEdgeKind;
import org.main.engine.TerrainGeometry;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
    private static final Path CONFIG_RESOURCE_PATH = Path.of("src", "main", "resources", "assets",
            "configuration.properties");
    private static final Path FIRST_PERSON_RIG_RESOURCE_PATH = Path.of(
            "src", "main", "resources", "assets", "editor", "content",
            "first_person_rig.properties");
    private static final Path WEAPON_ANIMATION_RESOURCE_PATH = Path.of(
            "src", "main", "resources", "assets", "editor", "content",
            "weapon_animation.properties");
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
    private final JComboBox<PlaceableCategory> placeableCategoryBox = new JComboBox<>(
            java.util.Arrays.stream(PlaceableCategory.values())
                    .filter(category -> category != PlaceableCategory.DIALOGUE_NPCS)
                    .toArray(PlaceableCategory[]::new));
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
    private boolean dirty;
    private String pendingTriggerId = "";
    private MapDesignLibrary.TriggerFireMode pendingTriggerFireMode = MapDesignLibrary.TriggerFireMode.ON_ENTRY;
    private boolean pendingTriggerOneShot = true;
    private String pendingTriggerQuestId = "";
    private int pendingTriggerQuestStage;
    private String wiringTriggerId = "";
    private String lastFindKey = "";
    private int lastFindIndex = -1;
    private Point inspectedTile;
    private MapDesignLibrary.MapPlacement inspectedPlacement;
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
        addMenuItem(menu, "Player Battle Model", this::managePlayerBattleModel);
        addMenuItem(menu, "First-Person Combat Rig", this::manageFirstPersonCombatRig);
        addMenuItem(menu, "Weapon Animation Sets", this::manageWeaponAnimationSets);
        return menuButton("Modify", menu);
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
        addMenuItem(menu, "Dialogue", this::createAuthoredDialogueNpc);
        addMenuItem(menu, "Quest", this::createAuthoredQuest);
        addMenuItem(menu, "Item", this::createCustomItem);
        addMenuItem(menu, "Enemy", this::createCustomMob);
        addMenuItem(menu, "NPC", this::createCustomNpc);
        addMenuItem(menu, "Limb", this::createCustomLimb);
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
        addMenuItem(menu, "Ability Cooldowns", this::manageAbilityConfiguration);
        addMenuItem(menu, "Level Gates", this::manageLevelGates);
        addMenuItem(menu, "Gathering Tool Animations", this::manageAnimations);
        addMenuItem(menu, "Light Manager", this::manageLights);
        addMenuItem(menu, "Trigger Manager", this::manageTriggers);
        addMenuItem(menu, "Sound Designer", () -> openToolWindow(new SoundDesignerTool()));
        addMenuItem(menu, "Song Designer", () -> openToolWindow(new SongDesignerTool()));
        addMenuItem(menu, "Sprite Sheet Splitter", () -> openToolWindow(new SpriteSheetSplitterTool()));
        return menuButton("Tools", menu);
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
        splitPane.setPreferredSize(new Dimension(820, 520));

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
            Files.createDirectories(CONFIG_RESOURCE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_RESOURCE_PATH)) {
                properties.store(outputStream, "Aether packaged gameplay configuration");
            }
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

    private void showContentBackupManager() {
        DefaultListModel<Path> backupModel = new DefaultListModel<>();
        JList<Path> backupList = new JList<>(backupModel);
        JTextArea detailArea = new JTextArea(10, 34);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);

        Runnable refreshBackups = () -> {
            backupModel.clear();
            for (Path backup : listSharedContentBackups()) {
                backupModel.addElement(backup);
            }
            if (!backupModel.isEmpty()) {
                backupList.setSelectedIndex(0);
            } else {
                detailArea.setText("No authored-content backups found.");
            }
        };

        backupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backupList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(backupLabel(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        backupList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                detailArea.setText(backupDetails(backupList.getSelectedValue()));
                detailArea.setCaretPosition(0);
            }
        });

        JButton restoreButton = new JButton("Restore");
        JButton deleteButton = new JButton("Delete Backup");
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel();
        buttons.add(restoreButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);
        buttons.add(closeButton);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(backupList),
                new JScrollPane(detailArea));
        splitPane.setResizeWeight(0.55);
        splitPane.setPreferredSize(new Dimension(780, 420));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Backups are stored in assets/editor/content/backups and are safe to commit if desired."),
                BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshBackups.run();

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] {}, null);
        var dialog = pane.createDialog(this, "Content Backups");

        restoreButton.addActionListener(event -> {
            Path selected = backupList.getSelectedValue();
            if (selected == null) {
                return;
            }
            restoreSharedContentBackup(selected);
            dialog.dispose();
        });
        deleteButton.addActionListener(event -> {
            Path selected = backupList.getSelectedValue();
            if (selected == null) {
                return;
            }
            int result = showAdaptiveTextConfirmDialog(
                    dialog,
                    "Delete backup " + selected.getFileName() + "?",
                    "Delete Content Backup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                Files.deleteIfExists(selected);
                setStatus("Deleted content backup " + selected.getFileName() + ".");
                refreshBackups.run();
            } catch (IOException exception) {
                setStatus("Backup delete failed: " + exception.getMessage());
            }
        });
        refreshButton.addActionListener(event -> refreshBackups.run());
        closeButton.addActionListener(event -> dialog.dispose());
        showManagedDialog(dialog);
    }

    private List<Path> listSharedContentBackups() {
        return List.of();
    }

    private String backupLabel(Path backup) {
        if (backup == null || backup.getFileName() == null) {
            return "";
        }
        return backup.getFileName().toString();
    }

    private String backupDetails(Path backup) {
        if (backup == null) {
            return "No backup selected.";
        }

        try {
            MapDesignLibrary.AuthoredContent content = loadAuthoredContentFromDesignFile(backup);
            StringBuilder builder = new StringBuilder();
            builder.append("File: ").append(backup.toAbsolutePath().normalize()).append('\n');
            builder.append("Dialogues: ").append(content.authoredDialogues().size()).append('\n');
            builder.append("Quests: ").append(content.authoredQuests().size()).append('\n');
            builder.append("Items: ").append(content.customItems().size()).append('\n');
            builder.append("Enemies: ").append(content.customMobs().size()).append('\n');
            builder.append("Limbs: ").append(content.customLimbs().size()).append('\n');
            builder.append("NPCs: ").append(content.customNpcs().size()).append('\n');
            builder.append("Gathering Nodes: ").append(content.customGatheringNodes().size()).append('\n');
            builder.append("Cooking Recipes: ").append(content.customCookingRecipes().size()).append('\n');
            builder.append("Crafting Recipes: ").append(content.craftingRecipes().size()).append('\n');
            return builder.toString();
        } catch (IOException exception) {
            return "Backup could not be read:\n" + exception.getMessage();
        }
    }

    private void restoreSharedContentBackup(Path backup) {
        int result = showAdaptiveTextConfirmDialog(
                this,
                "Restore " + backup.getFileName() + " as the current authored content?\n\n"
                        + "The current authored_content.properties file will be backed up first.",
                "Restore Content Backup",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            MapDesignLibrary.AuthoredContent content = loadAuthoredContentFromDesignFile(backup);
            replaceSharedContentInDesign(content);
            if (persistSharedContent("content backup restore")) {
                populatePlaceables();
                refreshContentBrowser();
                mapCanvas.repaint();
                setStatus("Restored authored content from " + backup.getFileName() + ".");
            }
        } catch (IOException exception) {
            setStatus("Backup restore failed: " + exception.getMessage());
        }
    }

    private MapDesignLibrary.AuthoredContent loadAuthoredContentFromDesignFile(Path path) throws IOException {
        MapDesignLibrary.MapDesign contentDesign = MapDesignLibrary.load(path);
        return new MapDesignLibrary.AuthoredContent(
                contentDesign.authoredDialogues(),
                contentDesign.authoredQuests(),
                contentDesign.customItems(),
                contentDesign.customMobs(),
                contentDesign.customLimbs(),
                contentDesign.customNpcs(),
                contentDesign.customGatheringNodes(),
                contentDesign.customCookingRecipes(),
                contentDesign.craftingRecipes());
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

    private void manageAbilityConfiguration() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel fields = createFormPanel();
        Map<SkillLibrary, JSpinner> cooldownSpinners = new EnumMap<>(SkillLibrary.class);

        for (SkillLibrary skill : SkillLibrary.values()) {
            String key = abilityCooldownKey(skill);
            double currentCooldown = GameConfiguration.doubleValue(key, 0.0);
            JSpinner cooldownSpinner = new JSpinner(new SpinnerNumberModel(currentCooldown, 0.0, 3600.0, 0.5));
            cooldownSpinners.put(skill, cooldownSpinner);
            addFormRow(fields, skill.getDisplayName(), cooldownSpinner);
        }

        JTextArea note = new JTextArea(
                "Cooldowns are saved to the packaged configuration and mirrored to the editable runtime configuration.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        panel.add(new JScrollPane(fields), BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, "Ability Cooldowns");
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Properties properties = loadPackagedConfigurationProperties();
        for (Map.Entry<SkillLibrary, JSpinner> entry : cooldownSpinners.entrySet()) {
            String key = abilityCooldownKey(entry.getKey());
            String value = formatConfigNumber(((Number) entry.getValue().getValue()).doubleValue());
            properties.setProperty(key, value);
            GameConfiguration.setValue(key, value);
        }

        try {
            Files.createDirectories(CONFIG_RESOURCE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_RESOURCE_PATH)) {
                properties.store(outputStream, "Aether packaged gameplay configuration");
            }
            setStatus("Updated ability cooldown configuration.");
        } catch (IOException exception) {
            setStatus("Ability configuration save failed: " + exception.getMessage());
        }
    }

    private void manageLevelGates() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTabbedPane tabs = new JTabbedPane();
        Map<String, JSpinner> intSpinners = new LinkedHashMap<>();
        Map<String, JSpinner> doubleSpinners = new LinkedHashMap<>();

        JPanel equipmentFields = configGridPanel();
        equipmentFields.add(new JLabel("Material"));
        equipmentFields.add(new JLabel("Defense Level"));
        equipmentFields.add(new JLabel("Key"));

        for (GearMaterial material : GearMaterial.values()) {
            String key = PlayerCharacter.equipmentDefenseRequirementKey(material);
            addIntConfigRow(equipmentFields, intSpinners, material.getDisplayName(), key,
                    PlayerCharacter.defenseRequirementFor(material), 0, 100);
        }
        tabs.addTab("Equipment", new JScrollPane(equipmentFields));

        JPanel butcheryFields = configGridPanel();
        butcheryFields.add(new JLabel("Butchery Gate"));
        butcheryFields.add(new JLabel("Required Level"));
        butcheryFields.add(new JLabel("Key"));
        addIntConfigRow(butcheryFields, intSpinners, "Legs Unlock", "butchery.targetLegsLevel",
                GameConfiguration.intValue("butchery.targetLegsLevel", 10), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Arms Unlock", "butchery.targetArmsLevel",
                GameConfiguration.intValue("butchery.targetArmsLevel", 20), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Body Unlock", "butchery.targetBodyLevel",
                GameConfiguration.intValue("butchery.targetBodyLevel", 30), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Head Unlock", "butchery.targetHeadLevel",
                GameConfiguration.intValue("butchery.targetHeadLevel", 40), 1, 100);
        tabs.addTab("Butchery", new JScrollPane(butcheryFields));

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

        JTextArea note = new JTextArea(
                "These values are saved to the packaged configuration and mirrored to the editable runtime configuration."
                        + " Equipment gates control the Defense skill level required to wear gear by material.");
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
        for (Map.Entry<String, JSpinner> entry : intSpinners.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(Math.max(0, ((Number) entry.getValue().getValue()).intValue()));
            properties.setProperty(key, value);
            GameConfiguration.setValue(key, value);
        }
        for (Map.Entry<String, JSpinner> entry : doubleSpinners.entrySet()) {
            String key = entry.getKey();
            String value = formatConfigNumber(((Number) entry.getValue().getValue()).doubleValue());
            properties.setProperty(key, value);
            GameConfiguration.setValue(key, value);
        }

        try {
            Files.createDirectories(CONFIG_RESOURCE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_RESOURCE_PATH)) {
                properties.store(outputStream, "Aether packaged gameplay configuration");
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

    private static String abilityCooldownKey(SkillLibrary skill) {
        return "battle.skillCooldown." + skill.name() + ".seconds";
    }

    private static String formatConfigNumber(double value) {
        double safeValue = Math.max(0.0, value);
        if (Math.rint(safeValue) == safeValue) {
            return String.valueOf((long) safeValue);
        }
        return String.format(java.util.Locale.US, "%.2f", safeValue).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void managePlayerBattleModel() {
        CharacterModelEditorFields editor = new CharacterModelEditorFields(PlayerCharacterModelConfiguration.load());
        JPanel fields = createFormPanel();
        addCharacterModelRows(fields, editor);
        if (showScrollableFormDialog(fields, "Player Battle Model") != JOptionPane.OK_OPTION)
            return;
        CharacterModelDefinition definition = editor.toDefinition();
        if (!validateCharacterModelDefinition(definition, "player"))
            return;
        Properties properties = loadPackagedConfigurationProperties();
        String prefix = PlayerCharacterModelConfiguration.PREFIX;
        putConfiguration(properties, prefix + "path", definition.modelPath());
        putConfiguration(properties, prefix + "rigId", definition.rigId());
        putConfiguration(properties, prefix + "scale", String.valueOf(definition.scale()));
        putConfiguration(properties, prefix + "facingRotationDegrees",
                String.valueOf(definition.facingRotationDegrees()));
        putConfiguration(properties, prefix + "verticalOffset", String.valueOf(definition.verticalOffset()));
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            CharacterModelDefinition.AnimationBinding binding = definition.animationBinding(slot);
            String slotPrefix = prefix + "animation." + slot.name();
            putConfiguration(properties, slotPrefix + ".path", binding.path());
            putConfiguration(properties, slotPrefix + ".clipName", binding.clipName());
            putConfiguration(properties, slotPrefix + ".speed", String.valueOf(binding.playbackSpeed()));
            putConfiguration(properties, slotPrefix + ".impactFraction", String.valueOf(binding.impactFraction()));
        }
        try {
            Files.createDirectories(CONFIG_RESOURCE_PATH.getParent());
            try (OutputStream outputStream = Files.newOutputStream(CONFIG_RESOURCE_PATH)) {
                properties.store(outputStream, "Aether packaged gameplay configuration");
            }
            setStatus("Updated the player battle model and animation bindings.");
        } catch (IOException exception) {
            setStatus("Player battle model save failed: " + exception.getMessage());
        }
    }

    private void manageFirstPersonCombatRig() {
        Properties properties = loadEditorProperties(FIRST_PERSON_RIG_RESOURCE_PATH);
        JTextField modelPath = new JTextField(properties.getProperty("rig.modelPath", ""), 28);
        JTextField rigId = new JTextField(properties.getProperty("rig.rigId", ""), 20);
        JTextField leftArm = new JTextField(properties.getProperty("rig.defaultLeftArmPath", ""), 28);
        JTextField rightArm = new JTextField(properties.getProperty("rig.defaultRightArmPath", ""), 28);
        JTextField leftBone = new JTextField(properties.getProperty("rig.leftHandBone", "Hand.L"), 18);
        JTextField rightBone = new JTextField(properties.getProperty("rig.rightHandBone", "Hand.R"), 18);
        JSpinner px = decimalSpinner(readPropertyDouble(properties, "rig.positionX", 0), -10, 10, 0.01);
        JSpinner py = decimalSpinner(readPropertyDouble(properties, "rig.positionY", 0), -10, 10, 0.01);
        JSpinner pz = decimalSpinner(readPropertyDouble(properties, "rig.positionZ", -0.75), -10, 10, 0.01);
        JSpinner rx = decimalSpinner(readPropertyDouble(properties, "rig.rotationX", 0), -360, 360, 1);
        JSpinner ry = decimalSpinner(readPropertyDouble(properties, "rig.rotationY", 0), -360, 360, 1);
        JSpinner rz = decimalSpinner(readPropertyDouble(properties, "rig.rotationZ", 0), -360, 360, 1);
        JSpinner scale = decimalSpinner(readPropertyDouble(properties, "rig.scale", 1), 0.01, 100, 0.01);

        JPanel rigFields = createFormPanel();
        rigFields.add(formRow("Skeleton / Base Rig", modelPathBrowser(modelPath)));
        rigFields.add(formRow("Rig ID", rigId));
        rigFields.add(formRow("Default Left Arm", modelPathBrowser(leftArm)));
        rigFields.add(formRow("Default Right Arm", modelPathBrowser(rightArm)));
        rigFields.add(formRow("Left Hand Bone", leftBone));
        rigFields.add(formRow("Right Hand Bone", rightBone));
        rigFields.add(formRow("Camera Position X / Y / Z", compactSpinnerRow(px, py, pz)));
        rigFields.add(formRow("Camera Rotation X / Y / Z", compactSpinnerRow(rx, ry, rz)));
        rigFields.add(formRow("Rig Scale", scale));

        EnumMap<FirstPersonCombatLibrary.AnimationSlot, JTextField> paths =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        EnumMap<FirstPersonCombatLibrary.AnimationSlot, JTextField> clips =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        EnumMap<FirstPersonCombatLibrary.AnimationSlot, JSpinner> speeds =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        EnumMap<FirstPersonCombatLibrary.AnimationSlot, JSpinner> impacts =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        JPanel animationFields = createFormPanel();
        for (FirstPersonCombatLibrary.AnimationSlot slot
                : FirstPersonCombatLibrary.AnimationSlot.values()) {
            String prefix = "rig.animation." + slot.name() + ".";
            JTextField path = new JTextField(properties.getProperty(prefix + "path", ""), 24);
            JTextField clip = new JTextField(properties.getProperty(prefix + "clipName", ""), 16);
            JSpinner speed = decimalSpinner(readPropertyDouble(properties, prefix + "speed", 1),
                    0.05, 10, 0.05);
            JSpinner impact = decimalSpinner(
                    readPropertyDouble(properties, prefix + "impactFraction", 0.55),
                    0.05, 0.95, 0.01);
            paths.put(slot, path);
            clips.put(slot, clip);
            speeds.put(slot, speed);
            impacts.put(slot, impact);
            JPanel binding = new JPanel(new BorderLayout(4, 4));
            binding.add(modelPathBrowser(path), BorderLayout.NORTH);
            JPanel details = new JPanel(new java.awt.GridLayout(1, 3, 4, 0));
            details.add(clip);
            details.add(speed);
            details.add(impact);
            binding.add(details, BorderLayout.SOUTH);
            animationFields.add(formRow(slot.name() + " Path / Clip / Speed / Impact", binding));
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Rig & Attachments", new JScrollPane(topAlignedForm(rigFields)));
        tabs.addTab("Fallback Clips", new JScrollPane(topAlignedForm(animationFields)));
        if (showScrollableFormDialog(tabs, "First-Person Combat Rig") != JOptionPane.OK_OPTION) return;

        properties.setProperty("rig.modelPath", normalizedPath(modelPath.getText()));
        properties.setProperty("rig.rigId", safeText(rigId));
        properties.setProperty("rig.defaultLeftArmPath", normalizedPath(leftArm.getText()));
        properties.setProperty("rig.defaultRightArmPath", normalizedPath(rightArm.getText()));
        properties.setProperty("rig.leftHandBone", safeText(leftBone));
        properties.setProperty("rig.rightHandBone", safeText(rightBone));
        setNumber(properties, "rig.positionX", number(px));
        setNumber(properties, "rig.positionY", number(py));
        setNumber(properties, "rig.positionZ", number(pz));
        setNumber(properties, "rig.rotationX", number(rx));
        setNumber(properties, "rig.rotationY", number(ry));
        setNumber(properties, "rig.rotationZ", number(rz));
        setNumber(properties, "rig.scale", number(scale));
        for (FirstPersonCombatLibrary.AnimationSlot slot
                : FirstPersonCombatLibrary.AnimationSlot.values()) {
            String prefix = "rig.animation." + slot.name() + ".";
            properties.setProperty(prefix + "path", normalizedPath(paths.get(slot).getText()));
            properties.setProperty(prefix + "clipName", safeText(clips.get(slot)));
            setNumber(properties, prefix + "speed", number(speeds.get(slot)));
            setNumber(properties, prefix + "impactFraction", number(impacts.get(slot)));
        }
        if (saveEditorProperties(FIRST_PERSON_RIG_RESOURCE_PATH, properties,
                "Aether first-person combat rig")) {
            FirstPersonCombatLibrary.reload();
            setStatus("Updated first-person combat rig and fallback clips.");
        }
    }

    private void manageWeaponAnimationSets() {
        Properties properties = loadEditorProperties(WEAPON_ANIMATION_RESOURCE_PATH);
        String[] columns = {"Set ID", "Display Name", "Slot", "Path", "Clip", "Speed", "Impact"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int column) {
                return column >= 5 ? Double.class : String.class;
            }
        };
        int count = readPropertyInt(properties, "animationSet.count", 0);
        for (int index = 0; index < count; index++) {
            String prefix = "animationSet." + index + ".";
            String id = properties.getProperty(prefix + "id", "");
            String display = properties.getProperty(prefix + "displayName", id);
            if (id.isBlank()) continue;
            for (FirstPersonCombatLibrary.AnimationSlot slot
                    : FirstPersonCombatLibrary.AnimationSlot.values()) {
                String binding = prefix + "animation." + slot.name() + ".";
                model.addRow(new Object[] {
                        id, display, slot.name(),
                        properties.getProperty(binding + "path", ""),
                        properties.getProperty(binding + "clipName", ""),
                        readPropertyDouble(properties, binding + "speed", 1),
                        readPropertyDouble(properties, binding + "impactFraction", 0.55)
                });
            }
        }
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {105, 130, 115, 260, 150, 65, 65};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        JTextField newSetId = new JTextField(14);
        JTextField newSetName = new JTextField(16);
        JButton addSet = new JButton("Add Set");
        JButton deleteSet = new JButton("Delete Selected Set");
        JButton browse = new JButton("Browse Selected Path");
        addSet.addActionListener(event -> {
            String id = normalizeContentId(newSetId.getText());
            if (id.isBlank()) return;
            String display = newSetName.getText() == null || newSetName.getText().isBlank()
                    ? id : newSetName.getText().trim();
            for (FirstPersonCombatLibrary.AnimationSlot slot
                    : FirstPersonCombatLibrary.AnimationSlot.values()) {
                model.addRow(new Object[] {id, display, slot.name(), "", "", 1.0, 0.55});
            }
            newSetId.setText("");
            newSetName.setText("");
        });
        deleteSet.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String id = String.valueOf(model.getValueAt(row, 0));
            for (int index = model.getRowCount() - 1; index >= 0; index--) {
                if (id.equalsIgnoreCase(String.valueOf(model.getValueAt(index, 0)))) {
                    model.removeRow(index);
                }
            }
        });
        browse.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            JTextField selected = new JTextField(String.valueOf(model.getValueAt(row, 3)));
            showAssetBrowser(selected, AssetBrowserType.MODELS);
            model.setValueAt(selected.getText(), row, 3);
        });

        JPanel addControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        addControls.add(new JLabel("New ID"));
        addControls.add(newSetId);
        addControls.add(new JLabel("Name"));
        addControls.add(newSetName);
        addControls.add(addSet);
        addControls.add(deleteSet);
        addControls.add(browse);

        Map<WeaponType, JTextField> defaults = new EnumMap<>(WeaponType.class);
        JPanel defaultFields = createFormPanel();
        for (WeaponType type : WeaponType.values()) {
            if (type == WeaponType.NONE) continue;
            JTextField field = new JTextField(properties.getProperty(
                    "weaponDefault." + type.name(),
                    FirstPersonCombatLibrary.defaultSetId(type)), 18);
            defaults.put(type, field);
            defaultFields.add(formRow(type.getDisplayName(), field));
        }
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(addControls, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(new JScrollPane(defaultFields), BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(920, 620));
        if (showScrollableFormDialog(panel, "Weapon Animation Sets") != JOptionPane.OK_OPTION) return;

        removePropertiesWithPrefix(properties, "animationSet.");
        LinkedHashMap<String, List<Integer>> rowsBySet = new LinkedHashMap<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            String id = normalizeContentId(String.valueOf(model.getValueAt(row, 0)));
            if (!id.isBlank()) rowsBySet.computeIfAbsent(id, ignored -> new ArrayList<>()).add(row);
        }
        properties.setProperty("animationSet.count", String.valueOf(rowsBySet.size()));
        int setIndex = 0;
        for (Map.Entry<String, List<Integer>> entry : rowsBySet.entrySet()) {
            String prefix = "animationSet." + setIndex++ + ".";
            int firstRow = entry.getValue().get(0);
            properties.setProperty(prefix + "id", entry.getKey());
            properties.setProperty(prefix + "displayName",
                    String.valueOf(model.getValueAt(firstRow, 1)));
            for (int row : entry.getValue()) {
                FirstPersonCombatLibrary.AnimationSlot slot;
                try {
                    slot = FirstPersonCombatLibrary.AnimationSlot.valueOf(
                            String.valueOf(model.getValueAt(row, 2)));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String binding = prefix + "animation." + slot.name() + ".";
                properties.setProperty(binding + "path",
                        normalizedPath(String.valueOf(model.getValueAt(row, 3))));
                properties.setProperty(binding + "clipName",
                        String.valueOf(model.getValueAt(row, 4)).trim());
                setNumber(properties, binding + "speed",
                        tableNumber(model.getValueAt(row, 5), 1));
                setNumber(properties, binding + "impactFraction",
                        tableNumber(model.getValueAt(row, 6), 0.55));
            }
        }
        defaults.forEach((type, field) -> properties.setProperty(
                "weaponDefault." + type.name(), normalizeContentId(field.getText())));
        if (saveEditorProperties(WEAPON_ANIMATION_RESOURCE_PATH, properties,
                "Aether first-person weapon animation sets")) {
            FirstPersonCombatLibrary.reload();
            setStatus("Updated weapon animation sets.");
        }
    }

    private JPanel modelPathBrowser(JTextField field) {
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> showAssetBrowser(field, AssetBrowserType.MODELS));
        return pathFieldPanel(field, browse);
    }

    private Properties loadEditorProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) return properties;
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            setStatus("Content configuration load warning: " + exception.getMessage());
        }
        return properties;
    }

    private boolean saveEditorProperties(Path path, Properties properties, String comment) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, comment);
            }
            return true;
        } catch (IOException exception) {
            setStatus("Content configuration save failed: " + exception.getMessage());
            return false;
        }
    }

    private static void removePropertiesWithPrefix(Properties properties, String prefix) {
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix)).toList()
                .forEach(properties::remove);
    }

    private static double readPropertyDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int readPropertyInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void setNumber(Properties properties, String key, double value) {
        properties.setProperty(key, formatSignedConfigNumber(value));
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
                new ArrayList<>(source.authoredDialogues()),
                new ArrayList<>(source.authoredQuests()),
                new ArrayList<>(source.customItems()),
                new ArrayList<>(source.customMobs()),
                new ArrayList<>(source.customLimbs()),
                new ArrayList<>(source.customNpcs()),
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
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            entries.add(new ContentEntry(ContentCategory.ITEMS, item.displayName(), item.itemId(), "Item", item));
        }
        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            entries.add(new ContentEntry(ContentCategory.ENEMIES, mob.displayName(), mob.mobId(), "Enemy", mob));
        }
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            entries.add(new ContentEntry(ContentCategory.NPCS, npc.displayName(), npc.npcId(), "NPC", npc));
        }
        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            entries.add(new ContentEntry(ContentCategory.LIMBS, limb.displayName(), limb.limbId(), "Limb", limb));
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
        inspectedTrigger = null;
        inspectedLight = null;
        inspectedTriggerTarget = null;
        mapCanvas.repaint();
    }

    private void clearMapSelection() {
        inspectedTile = null;
        inspectedPlacement = null;
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
        if (value instanceof MapDesignLibrary.CustomItem item) {
            builder.append("Type: ").append(item.itemType()).append('\n');
            builder.append("Material: ").append(item.material()).append('\n');
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
                        .append(item.smithingXpReward()).append(" xp\n");
            }
            builder.append("Icon: ").append(item.iconPath()).append('\n');
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
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            builder.append("Slot: ").append(limb.limbSlot()).append('\n');
            builder.append("Source: ").append(limb.sourceCreatureId().isBlank() ? "None" : limb.sourceCreatureId())
                    .append('\n');
            builder.append("Stats: ").append(limb.statBonuses()).append('\n');
            builder.append("Skills: ").append(limb.skillIds().isEmpty() ? "None" : limb.skillIds()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            builder.append("Type: ").append(node.nodeType()).append('\n');
            builder.append("Skill: ").append(node.gatheringSkill()).append(" level ").append(node.requiredLevel())
                    .append('\n');
            builder.append("Gather XP: ").append(node.gatherXpReward()).append('\n');
            builder.append("Loot: ").append(node.lootEntries()).append('\n');
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
            builder.append("Stages: ").append(quest.stageDescriptions().size()).append('\n');
            for (int i = 0; i < quest.stageDescriptions().size(); i++) {
                builder.append(i).append(": ").append(quest.stageDescriptions().get(i)).append('\n');
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
            builder.append("Activation: ").append(trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE
                    ? "Quest reaches stage"
                    : "Player enters tile").append('\n');
            if (trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE) {
                builder.append("Quest: ").append(trigger.requiredQuestId()).append('\n');
                builder.append("Minimum Stage: ").append(trigger.requiredQuestStage()).append('\n');
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
        } else if (value instanceof MapDesignLibrary.ValidationIssue issue) {
            builder.append("Severity: ").append(issue.severity()).append('\n');
            builder.append("Message: ").append(issue.message()).append('\n');
        }
        builder.append('\n');
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
        splitPane.setPreferredSize(new Dimension(860, 640));
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
        splitPane.setPreferredSize(new Dimension(940, 690));

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
            editAuthoredDialogue(dialogue);
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
        if (!choice.giveItemName().isBlank()) {
            tags.add("give " + choice.giveItemName());
        }
        if (!choice.questId().isBlank()) {
            tags.add(choice.questId() + "[" + choice.questStage() + "]");
        }
        if (choice.giveGold() > 0) {
            tags.add("+" + choice.giveGold() + "g");
        }
        if (choice.giveSkill() != null && choice.giveSkillXp() > 0) {
            tags.add("+" + choice.giveSkillXp() + " " + choice.giveSkill());
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
        if (value instanceof MapDesignLibrary.CustomItem item) {
            addAssetDependency(dependencies, "Icon", item.iconPath());
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
            for (SkillLibrary skill : mob.skillIds()) {
                dependencies.add("Skill " + skill.name());
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
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            addAssetDependency(dependencies, "Icon", limb.iconPath());
            addAssetDependency(dependencies, "Paper-doll source", limb.paperDollSourcePath());
            addAssetDependency(dependencies, "First-person arm model", limb.firstPersonModelPath());
            if (!limb.sourceCreatureId().isBlank()) {
                dependencies.add("Source creature " + limb.sourceCreatureId());
            }
            for (SkillLibrary skill : limb.skillIds()) {
                dependencies.add("Skill " + skill.name());
            }
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            dependencies.add("Gathering skill " + node.gatheringSkill() + " level " + node.requiredLevel());
            for (MapDesignLibrary.CustomDropEntry drop : node.lootEntries()) {
                dependencies.add("Loot " + drop.itemId() + " weight/chance " + drop.chance());
            }
            if (!node.smeltOutputItemId().isBlank()) {
                dependencies.add("Smelt output " + node.smeltOutputItemId() + " level " + node.smeltRequiredLevel());
            }
            for (String framePath : node.framePaths()) {
                addAssetDependency(dependencies, "Frame", framePath);
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
            dependencies.add("Stage count " + quest.stageDescriptions().size());
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            if (!dialogue.followUpInteractionId().isBlank()) {
                dependencies.add("Follow-up " + dialogue.followUpInteractionId());
            }
            if (!dialogue.questId().isBlank()) {
                dependencies.add("Quest " + dialogue.questId() + " stage " + dialogue.questStage());
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
            if (!choice.questId().isBlank()) {
                dependencies.add("Choice quest " + choice.questId() + " stage " + choice.questStage());
            }
            if (!choice.requiredItemName().isBlank()) {
                dependencies.add("Requires item " + choice.requiredItemName());
            }
            if (!choice.takeItemName().isBlank()) {
                dependencies.add("Takes item " + choice.takeItemName());
            }
            if (!choice.giveItemName().isBlank()) {
                dependencies.add("Gives item " + choice.giveItemName());
            }
            if (choice.giveSkill() != null && choice.giveSkillXp() > 0) {
                dependencies.add("Gives " + choice.giveSkillXp() + " " + choice.giveSkill() + " XP");
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
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            if (id.equals(dialogue.followUpInteractionId())) {
                references.add("Dialogue " + dialogue.interactionId() + " follows up to this");
            }
            if (id.equals(dialogue.questId())) {
                references.add("Dialogue " + dialogue.interactionId() + " sets this quest");
            }
            collectChoiceReferences(references, dialogue.interactionId(), dialogue.choices(), id, label);
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                collectChoiceReferences(references, dialogue.interactionId() + "::" + node.nodeId(), node.choices(), id,
                        label);
            }
        }
        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            if (id.equals(npc.interactionId())) {
                references.add("NPC " + npc.npcId() + " uses this dialogue");
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
        return references;
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
            if (id.equals(choice.questId())) {
                references.add("Choice " + dialogueLocation + " advances this quest");
            }
            if (label.equalsIgnoreCase(choice.requiredItemName())
                    || label.equalsIgnoreCase(choice.takeItemName())
                    || label.equalsIgnoreCase(choice.giveItemName())) {
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
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            editCustomLimb(limb);
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            editCustomGatheringNode(node);
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            editCookingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            editCraftingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            editAuthoredQuest(quest);
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            editAuthoredDialogue(dialogue);
        } else if (value instanceof MobAreaEntry area) {
            editMobArea(area);
        } else if (value instanceof MapLight light) {
            editLight(light);
        } else if (value instanceof MapDesignLibrary.MapTrigger) {
            manageTriggers();
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            editMapPlacement(placement);
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
        if (value instanceof MapDesignLibrary.CustomItem item) {
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
                    item.smithingXpReward(),
                    item.magicAccuracyBonus(),
                    item.magicPowerBonus(),
                    item.firstPersonModelPath(),
                    item.viewModelProfile()));
            persistSharedContent("custom item");
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            design.customMobs().add(new MapDesignLibrary.CustomMob(
                    nextCustomMobId(copiedName),
                    copiedName,
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
                    mob.skillIds(),
                    mob.dropEntries(),
                    mob.characterModel()));
            persistSharedContent("custom enemy");
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            design.customNpcs().add(new MapDesignLibrary.CustomNpc(
                    nextCustomNpcId(copiedName),
                    copiedName,
                    npc.imagePath(),
                    npc.talkSoundPath(),
                    npc.interactionId(),
                    npc.shop(),
                    npc.characterModel()));
            persistSharedContent("custom NPC");
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
                    limb.firstPersonRigId()));
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
                    node.frameDurationMs(),
                    node.visualScale(),
                    node.gatheringSkill(),
                    node.lootEntries(),
                    node.smeltRequiredLevel()));
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
                    quest.stageDescriptions()));
            persistSharedContent("authored quest");
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            design.authoredDialogues().add(new MapDesignLibrary.AuthoredDialogue(
                    nextAuthoredInteractionId(copiedName),
                    copiedName,
                    dialogue.bodyText(),
                    dialogue.followUpInteractionId(),
                    MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                    "",
                    null,
                    0,
                    0,
                    dialogue.questId(),
                    dialogue.questStage(),
                    dialogue.choices(),
                    dialogue.nodes()));
            persistSharedContent("authored dialogue NPC");
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            duplicateMapPlacement(placement);
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
        int[][] offsets = {
                { 1, 0 },
                { 0, 1 },
                { -1, 0 },
                { 0, -1 }
        };
        for (int[] offset : offsets) {
            int x = placement.x() + offset[0];
            int y = placement.y() + offset[1];
            if (isPlacementTargetOpen(x, y)) {
                return new Point(x, y);
            }
        }

        for (int radius = 2; radius < Math.max(design.width(), design.height()); radius++) {
            for (int y = placement.y() - radius; y <= placement.y() + radius; y++) {
                for (int x = placement.x() - radius; x <= placement.x() + radius; x++) {
                    boolean edge = x == placement.x() - radius
                            || x == placement.x() + radius
                            || y == placement.y() - radius
                            || y == placement.y() + radius;
                    if (edge && isPlacementTargetOpen(x, y)) {
                        return new Point(x, y);
                    }
                }
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
        return true;
    }

    private void deleteSelectedContent() {
        ContentEntry entry = contentList.getSelectedValue();
        if (entry == null) {
            return;
        }
        Object value = entry.value();
        if (value instanceof MapDesignLibrary.CustomItem item) {
            deleteCustomItem(item);
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            deleteCustomMob(mob);
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            deleteCustomNpc(npc);
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            deleteCustomLimb(limb);
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            deleteCustomGatheringNode(node);
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            deleteCookingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.CraftingRecipe recipe) {
            deleteCraftingRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            deleteAuthoredQuest(quest);
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            deleteAuthoredDialogue(dialogue);
        } else if (value instanceof MobAreaEntry area) {
            deleteMobArea(area);
        } else if (value instanceof MapLight light) {
            deleteLight(light);
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            deleteTrigger(trigger);
        } else if (value instanceof MapDesignLibrary.MapPlacement placement) {
            deleteMapPlacement(placement);
        } else if (value instanceof MapDesignLibrary.ValidationIssue) {
            setStatus("Diagnostics cannot be deleted; fix the referenced content instead.");
        }
        refreshContentBrowser();
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

        String id = entry.value() instanceof MapDesignLibrary.MapPlacement placement ? placement.id() : entry.id();
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
        if (entry.value() instanceof MapDesignLibrary.ValidationIssue issue) {
            navigateDiagnostic(issue);
            return;
        }

        List<MapDesignLibrary.MapPlacement> placements = placementsForContent(entry);
        if (placements.isEmpty()) {
            setStatus("No placed instances found for " + entry.label() + ".");
            return;
        }

        String findKey = entry.category() + "|" + entry.type() + "|" + entry.id();
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
        if (value instanceof MapDesignLibrary.AuthoredDialogue) {
            return MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC;
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

    private void revealMapTrigger(MapDesignLibrary.MapTrigger trigger) {
        revealContentEntry(trigger, ContentCategory.TRIGGERS);
        inspectedTile = new Point(trigger.x(), trigger.y());
        inspectedPlacement = null;
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

        String dialogueId = tokenAfter(message, "Authored NPC placement references missing dialogue ");
        if (!dialogueId.isBlank()) {
            return firstPlacement(MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC, dialogueId);
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
        if (!(value instanceof MapDesignLibrary.MapPlacement placement)) {
            return null;
        }
        return switch (placement.kind()) {
            case ITEM -> PlaceableCategory.ITEMS;
            case ENEMY -> PlaceableCategory.ENEMIES;
            case GENERIC_NPC, MAIN_NPC, CUSTOM_NPC -> PlaceableCategory.NPCS;
            case AUTHORED_DIALOGUE_NPC -> PlaceableCategory.DIALOGUE_NPCS;
            case GATHERING_NODE -> PlaceableCategory.GATHERING_NODES;
            case CRAFTING_NODE -> PlaceableCategory.CRAFTING_NODES;
            case INTERACTION ->
                placement.id().startsWith("map_link|") ? PlaceableCategory.MAP_LINKS : PlaceableCategory.INTERACTIONS;
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

        int result = showScrollableFormDialog(fields, "Edit Placement");
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

    private void createAuthoredDialogueNpc() {
        AuthoredDialogueDraft draft = showAuthoredDialogueDialog(
                "New Dialogue",
                "New Dialogue",
                "Hello there.",
                "",
                "",
                -1,
                List.of(),
                List.of());
        if (draft == null) {
            return;
        }

        MapDesignLibrary.AuthoredDialogue authoredDialogue = new MapDesignLibrary.AuthoredDialogue(
                nextAuthoredInteractionId(draft.speakerName()),
                draft.speakerName(),
                draft.bodyText(),
                draft.followUpInteractionId(),
                MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                "",
                null,
                0,
                0,
                draft.questId(),
                draft.questStage(),
                draft.choices(),
                draft.nodes());
        design.authoredDialogues().add(authoredDialogue);
        persistSharedContent("authored dialogue");
        populatePlaceables();
        setStatus("Created dialogue " + draft.speakerName() + ". Assign it from an NPC's Dialogue field.");
    }

    private void createAuthoredQuest() {
        AuthoredQuestDraft draft = showAuthoredQuestDialog(
                "New Quest",
                "New Quest",
                List.of("Begin the quest.", "Complete."));
        if (draft == null) {
            return;
        }

        MapDesignLibrary.AuthoredQuest authoredQuest = new MapDesignLibrary.AuthoredQuest(
                nextAuthoredQuestId(draft.displayName()),
                draft.displayName(),
                draft.stageDescriptions());
        design.authoredQuests().add(authoredQuest);
        persistSharedContent("authored quest");
        setStatus("Created authored quest " + authoredQuest.displayName() + ".");
    }

    private void editAuthoredQuest(MapDesignLibrary.AuthoredQuest selected) {
        AuthoredQuestDraft draft = showAuthoredQuestDialog(
                "Edit Quest",
                selected.displayName(),
                selected.stageDescriptions());
        if (draft == null) {
            return;
        }

        int index = design.authoredQuests().indexOf(selected);
        if (index >= 0) {
            design.authoredQuests().set(index, new MapDesignLibrary.AuthoredQuest(
                    selected.questId(),
                    draft.displayName(),
                    draft.stageDescriptions()));
            persistSharedContent("authored quest");
            setStatus("Updated authored quest " + draft.displayName() + ".");
        }
    }

    private void deleteAuthoredQuest(MapDesignLibrary.AuthoredQuest selected) {
        int result = showAdaptiveTextConfirmDialog(
                this,
                deleteMessage(
                        "Delete " + selected.displayName() + "? Dialogue actions using it will be cleared.",
                        findReferences(new ContentEntry(ContentCategory.QUESTS, selected.displayName(),
                                selected.questId(), "Quest", selected))),
                "Delete Quest",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        design.authoredQuests().remove(selected);
        for (int i = 0; i < design.authoredDialogues().size(); i++) {
            MapDesignLibrary.AuthoredDialogue dialogue = design.authoredDialogues().get(i);
            if (selected.questId().equals(dialogue.questId())) {
                design.authoredDialogues().set(i, new MapDesignLibrary.AuthoredDialogue(
                        dialogue.interactionId(),
                        dialogue.speakerName(),
                        dialogue.bodyText(),
                        dialogue.followUpInteractionId(),
                        MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                        "",
                        null,
                        0,
                        0,
                        "",
                        -1,
                        dialogue.choices(),
                        dialogue.nodes()));
            }
        }
        persistSharedContent("authored quest");
        setStatus("Deleted authored quest " + selected.displayName() + ".");
    }

    private void createCustomItem() {
        MapDesignLibrary.CustomItem item = showCustomItemDialog("Create Item", null);
        if (item == null) {
            return;
        }

        design.customItems().add(item);
        persistSharedContent("custom item");
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
        FirstPersonCombatLibrary.ItemProfile[] firstPersonProfile = {
                existing == null ? null
                        : FirstPersonCombatLibrary.load().itemProfiles().get(
                                normalizeContentId(existing.itemId()))
        };
        JSpinner viewX = decimalSpinner(existingPose.positionX(), -10, 10, 0.01);
        JSpinner viewY = decimalSpinner(existingPose.positionY(), -10, 10, 0.01);
        JSpinner viewZ = decimalSpinner(existingPose.positionZ(), -10, 10, 0.01);
        JSpinner viewRotX = decimalSpinner(existingPose.rotationX(), -360, 360, 1);
        JSpinner viewRotY = decimalSpinner(existingPose.rotationY(), -360, 360, 1);
        JSpinner viewRotZ = decimalSpinner(existingPose.rotationZ(), -360, 360, 1);
        JSpinner viewHeight = decimalSpinner(existingPose.normalizedHeight(), 0.02, 20, 0.02);
        JSpinner swingX = decimalSpinner(existingPose.swingAxisX(), -1, 1, 0.05);
        JSpinner swingY = decimalSpinner(existingPose.swingAxisY(), -1, 1, 0.05);
        JSpinner swingZ = decimalSpinner(existingPose.swingAxisZ(), -1, 1, 0.05);
        JCheckBox pairedHands = new JCheckBox("Model contains both hands", existingPose.pairedHands());
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JButton firstPersonModelBrowseButton = new JButton("Browse");
        JButton useSoundBrowseButton = new JButton("Browse");
        JButton firstPersonAuthoringButton = new JButton("Edit Socket, Hand, Armor & Clip Overrides");
        JComboBox<ItemTemplateOption> templateBox = new JComboBox<>(itemTemplateOptions());
        JComboBox<InventorySystem.ItemType> typeBox = new JComboBox<>(new InventorySystem.ItemType[] {
                InventorySystem.ItemType.MISC,
                InventorySystem.ItemType.CONSUMABLE,
                InventorySystem.ItemType.WEAPON,
                InventorySystem.ItemType.SHIELD,
                InventorySystem.ItemType.HEAD_GEAR,
                InventorySystem.ItemType.CHEST_ARMOR,
                InventorySystem.ItemType.LEG_ARMOR,
                InventorySystem.ItemType.RING
        });
        JComboBox<GearMaterial> materialBox = new JComboBox<>(GearMaterial.values());
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
        JSpinner smithingXpSpinner = new JSpinner(
                new SpinnerNumberModel(existing == null ? 25 : existing.smithingXpReward(), 0, 100000, 1));
        JLabel smithingMaterialLabel = new JLabel();
        JTextArea examineArea = new JTextArea(existing == null ? "A custom item." : existing.examineText(), 4, 30);
        examineArea.setLineWrap(true);
        examineArea.setWrapStyleWord(true);
        if (existing != null) {
            typeBox.setSelectedItem(existing.itemType());
            materialBox.setSelectedItem(existing.material());
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
        };
        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollOverlayField));
        firstPersonModelBrowseButton
                .addActionListener(event -> showAssetBrowser(firstPersonModelField, AssetBrowserType.MODELS));
        useSoundBrowseButton.addActionListener(event -> browsePathInto(useSoundField));
        firstPersonAuthoringButton.addActionListener(event -> {
            String provisionalId = existing == null
                    ? normalizeContentId(nameField.getText())
                    : existing.itemId();
            FirstPersonCombatLibrary.ItemProfile edited = showItemFirstPersonProfileDialog(
                    provisionalId,
                    firstPersonProfile[0],
                    (InventorySystem.ItemType) typeBox.getSelectedItem());
            if (edited != null) firstPersonProfile[0] = edited;
        });
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
            EquipmentViewModelProfile pose = item.viewModelProfile();
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
            smithingXpSpinner.setValue(item.smithingXpReward());
            examineArea.setText(item.examineText());
            updateSmithingRecipeControls.run();
        });
        JPanel imagePanel = new JPanel(new BorderLayout(4, 4));
        imagePanel.add(iconPathField, BorderLayout.CENTER);
        imagePanel.add(browseButton, BorderLayout.EAST);
        JPanel paperDollPanel = new JPanel(new BorderLayout(4, 4));
        paperDollPanel.add(paperDollOverlayField, BorderLayout.CENTER);
        paperDollPanel.add(paperDollBrowseButton, BorderLayout.EAST);
        JPanel nameRow = formRow("Name", nameField);
        JPanel imageRow = formRow("Image", imagePanel);
        JPanel paperDollRow = formRow("Paper Doll Sprite", paperDollPanel);
        JPanel firstPersonModelRow = formRow(
                "First-Person Model",
                pathFieldPanel(firstPersonModelField, firstPersonModelBrowseButton));
        JPanel useSoundRow = formRow("Use Sound", pathFieldPanel(useSoundField, useSoundBrowseButton));
        JPanel viewPositionRow = formRow("View Position X / Y / Z", compactSpinnerRow(viewX, viewY, viewZ));
        JPanel viewRotationRow = formRow("View Rotation X / Y / Z", compactSpinnerRow(viewRotX, viewRotY, viewRotZ));
        JPanel viewHeightRow = formRow("Normalized Height", viewHeight);
        JPanel swingAxisRow = formRow("Swing Axis X / Y / Z", compactSpinnerRow(swingX, swingY, swingZ));
        JPanel pairedHandsRow = formRow("Chest Hands", pairedHands);
        JPanel firstPersonAuthoringRow = formRow("Skeletal First-Person", firstPersonAuthoringButton);
        EquipmentCombinationPreviewPanel equipmentPreview = new EquipmentCombinationPreviewPanel(
                firstPersonModelField::getText,
                () -> new EquipmentViewModelProfile(number(viewX), number(viewY), number(viewZ),
                        number(viewRotX), number(viewRotY), number(viewRotZ), number(viewHeight),
                        number(swingX), number(swingY), number(swingZ), pairedHands.isSelected()),
                () -> (InventorySystem.ItemType) typeBox.getSelectedItem(),
                twoHandedBox::isSelected,
                () -> firstPersonProfile[0],
                () -> (WeaponType) weaponTypeBox.getSelectedItem());
        for (JSpinner poseSpinner : new JSpinner[] {
                viewX, viewY, viewZ, viewRotX, viewRotY, viewRotZ,
                viewHeight, swingX, swingY, swingZ
        }) {
            poseSpinner.addChangeListener(event -> equipmentPreview.refreshPose());
        }
        pairedHands.addActionListener(event -> equipmentPreview.refreshPose());
        twoHandedBox.addActionListener(event -> equipmentPreview.refreshPose());
        typeBox.addActionListener(event -> equipmentPreview.refreshPose());
        JPanel equipmentPreviewRow = formRow("Equipment Preview", equipmentPreview);
        JPanel typeRow = formRow("Type", typeBox);
        JPanel materialRow = formRow("Material", materialBox);
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
        JPanel smithingXpRow = formRow("Smithing XP", smithingXpSpinner);

        JTabbedPane itemTabs = new JTabbedPane();

        JPanel basicFields = createFormPanel();
        if (existing == null) {
            basicFields.add(formRow("Template", templateBox));
        }
        basicFields.add(nameRow);
        basicFields.add(typeRow);
        basicFields.add(imageRow);
        basicFields.add(valueRow);
        basicFields.add(stackableRow);
        JPanel examineBox = new JPanel(new BorderLayout(4, 4));
        examineBox.setBorder(BorderFactory.createTitledBorder("Examine Description"));
        examineBox.add(new JScrollPane(examineArea), BorderLayout.CENTER);

        JPanel basicPanel = new JPanel(new BorderLayout(6, 6));
        basicPanel.add(basicFields, BorderLayout.NORTH);
        basicPanel.add(examineBox, BorderLayout.CENTER);

        JPanel equipmentFields = createFormPanel();
        equipmentFields.add(materialRow);
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

        itemTabs.addTab("Basic Info", basicPanel);
        itemTabs.addTab("Equipment & Stats", new JScrollPane(topAlignedForm(equipmentFields)));
        itemTabs.addTab("Visuals & Sound", new JScrollPane(topAlignedForm(visualFields)));
        itemTabs.addTab("Smithing Recipe", new JScrollPane(topAlignedForm(smithingFields)));

        Runnable updateItemTypeRows = () -> {
            InventorySystem.ItemType itemType = (InventorySystem.ItemType) typeBox.getSelectedItem();
            boolean weapon = itemType == InventorySystem.ItemType.WEAPON;
            boolean ring = itemType == InventorySystem.ItemType.RING;
            boolean consumable = itemType == InventorySystem.ItemType.CONSUMABLE;
            boolean stackableAllowed = itemType == InventorySystem.ItemType.MISC || consumable;
            boolean paperDollAllowed = itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD
                    || itemType == InventorySystem.ItemType.HEAD_GEAR
                    || itemType == InventorySystem.ItemType.CHEST_ARMOR
                    || itemType == InventorySystem.ItemType.LEG_ARMOR;
            boolean firstPersonModelAllowed = itemType == InventorySystem.ItemType.WEAPON
                    || itemType == InventorySystem.ItemType.SHIELD
                    || itemType == InventorySystem.ItemType.CHEST_ARMOR;
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            boolean metal = material != null && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            boolean smithingEnabled = metal && smithingRecipeBox.isSelected();

            if (!weapon) {
                weaponTypeBox.setSelectedItem(WeaponType.SWORD);
                twoHandedBox.setSelected(false);
            }
            paperDollRow.setVisible(paperDollAllowed);
            firstPersonModelRow.setVisible(firstPersonModelAllowed);
            viewPositionRow.setVisible(firstPersonModelAllowed);
            viewRotationRow.setVisible(firstPersonModelAllowed);
            viewHeightRow.setVisible(firstPersonModelAllowed);
            swingAxisRow.setVisible(firstPersonModelAllowed);
            pairedHandsRow.setVisible(itemType == InventorySystem.ItemType.CHEST_ARMOR && firstPersonModelAllowed);
            firstPersonAuthoringRow.setVisible(firstPersonModelAllowed);
            equipmentPreviewRow.setVisible(firstPersonModelAllowed);
            weaponTypeRow.setVisible(weapon);
            twoHandedRow.setVisible(weapon);
            magicAccuracyRow.setVisible(weapon);
            magicPowerRow.setVisible(weapon);
            ringStatRow.setVisible(ring);
            healRow.setVisible(consumable);
            stackableRow.setVisible(stackableAllowed);
            smithingRecipeRow.setVisible(metal);
            smithingMaterialRow.setVisible(metal);
            smithingBarsRow.setVisible(smithingEnabled);
            smithingLevelRow.setVisible(smithingEnabled);
            smithingXpRow.setVisible(smithingEnabled);

            if (!stackableAllowed) {
                stackableBox.setSelected(false);
            }
            if (!ring) {
                selectStatTargetOption(statTargetBox, null);
            }
            itemTabs.revalidate();
            itemTabs.repaint();
        };
        typeBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
        });
        materialBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
        });
        smithingRecipeBox.addActionListener(event -> {
            updateSmithingRecipeControls.run();
            updateItemTypeRows.run();
        });
        templateBox.addActionListener(event -> updateItemTypeRows.run());
        updateSmithingRecipeControls.run();
        updateItemTypeRows.run();

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

        String resultItemId = existing == null ? nextCustomItemId(name) : existing.itemId();
        if (firstPersonProfile[0] != null) {
            FirstPersonCombatLibrary.ItemProfile profile = firstPersonProfile[0];
            persistItemFirstPersonProfile(new FirstPersonCombatLibrary.ItemProfile(
                    resultItemId,
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
                    profile.overrides()));
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
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingXpSpinner.getValue()).intValue()
                        : 0,
                selectedWeapon ? ((Number) magicAccuracySpinner.getValue()).intValue() : 0,
                selectedWeapon ? ((Number) magicPowerSpinner.getValue()).intValue() : 0,
                firstPersonModelPath,
                new EquipmentViewModelProfile(number(viewX), number(viewY), number(viewZ),
                        number(viewRotX), number(viewRotY), number(viewRotZ), number(viewHeight),
                        number(swingX), number(swingY), number(swingZ), pairedHands.isSelected()));
    }

    private FirstPersonCombatLibrary.ItemProfile showItemFirstPersonProfileDialog(
            String itemId,
            FirstPersonCombatLibrary.ItemProfile existing,
            InventorySystem.ItemType itemType
    ) {
        FirstPersonCombatLibrary.ItemProfile base = existing == null
                ? new FirstPersonCombatLibrary.ItemProfile(
                        itemId,
                        FirstPersonCombatLibrary.WieldHand.RIGHT,
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
        List<String> setIds = new ArrayList<>(FirstPersonCombatLibrary.load().animationSets().keySet());
        setIds.sort(String::compareToIgnoreCase);
        JComboBox<String> animationSet = new JComboBox<>(setIds.toArray(String[]::new));
        animationSet.setEditable(true);
        animationSet.setSelectedItem(base.animationSetId());
        EquipmentViewModelProfile transform = base.socketTransform();
        JSpinner px = decimalSpinner(transform.positionX(), -10, 10, 0.01);
        JSpinner py = decimalSpinner(transform.positionY(), -10, 10, 0.01);
        JSpinner pz = decimalSpinner(transform.positionZ(), -10, 10, 0.01);
        JSpinner rx = decimalSpinner(transform.rotationX(), -360, 360, 1);
        JSpinner ry = decimalSpinner(transform.rotationY(), -360, 360, 1);
        JSpinner rz = decimalSpinner(transform.rotationZ(), -360, 360, 1);
        JSpinner socketScale = decimalSpinner(transform.normalizedHeight(), 0.001, 100, 0.01);
        JSpinner secondaryX = decimalSpinner(base.secondaryGripX(), -10, 10, 0.01);
        JSpinner secondaryY = decimalSpinner(base.secondaryGripY(), -10, 10, 0.01);
        JSpinner secondaryZ = decimalSpinner(base.secondaryGripZ(), -10, 10, 0.01);

        JPanel socketFields = createFormPanel();
        socketFields.add(formRow("Wield Hand", wieldHand));
        socketFields.add(formRow("Animation Set", animationSet));
        socketFields.add(formRow("Socket Position X / Y / Z", compactSpinnerRow(px, py, pz)));
        socketFields.add(formRow("Socket Rotation X / Y / Z", compactSpinnerRow(rx, ry, rz)));
        socketFields.add(formRow("Socket Scale", socketScale));
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
        tabs.addTab("Hand Socket", new JScrollPane(topAlignedForm(socketFields)));
        tabs.addTab("Armor Attachments", new JScrollPane(topAlignedForm(armorFields)));
        tabs.addTab("Clip Overrides", overridePanel);
        tabs.setPreferredSize(new Dimension(790, 510));
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
                (FirstPersonCombatLibrary.WieldHand) wieldHand.getSelectedItem(),
                normalizeContentId(String.valueOf(animationSet.getSelectedItem())),
                new EquipmentViewModelProfile(
                        number(px), number(py), number(pz),
                        number(rx), number(ry), number(rz), number(socketScale),
                        0, 0, 1, false),
                number(secondaryX), number(secondaryY), number(secondaryZ),
                normalizedPath(leftArmor.getText()), normalizedPath(rightArmor.getText()),
                (FirstPersonCombatLibrary.ArmCoverage) leftCoverage.getSelectedItem(),
                (FirstPersonCombatLibrary.ArmCoverage) rightCoverage.getSelectedItem(),
                overrideBindings);
    }

    private void persistItemFirstPersonProfile(FirstPersonCombatLibrary.ItemProfile updated) {
        if (updated == null || updated.itemId().isBlank()) return;
        Properties properties = loadEditorProperties(WEAPON_ANIMATION_RESOURCE_PATH);
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles =
                new LinkedHashMap<>(FirstPersonCombatLibrary.loadFresh().itemProfiles());
        profiles.put(normalizeContentId(updated.itemId()), updated);
        removePropertiesWithPrefix(properties, "itemProfile.");
        properties.setProperty("itemProfile.count", String.valueOf(profiles.size()));
        int index = 0;
        for (FirstPersonCombatLibrary.ItemProfile profile : profiles.values()) {
            String prefix = "itemProfile." + index++ + ".";
            properties.setProperty(prefix + "itemId", profile.itemId());
            properties.setProperty(prefix + "wieldHand", profile.wieldHand().name());
            properties.setProperty(prefix + "animationSetId", profile.animationSetId());
            EquipmentViewModelProfile transform = profile.socketTransform();
            setNumber(properties, prefix + "socket.positionX", transform.positionX());
            setNumber(properties, prefix + "socket.positionY", transform.positionY());
            setNumber(properties, prefix + "socket.positionZ", transform.positionZ());
            setNumber(properties, prefix + "socket.rotationX", transform.rotationX());
            setNumber(properties, prefix + "socket.rotationY", transform.rotationY());
            setNumber(properties, prefix + "socket.rotationZ", transform.rotationZ());
            setNumber(properties, prefix + "socket.scale", transform.normalizedHeight());
            setNumber(properties, prefix + "secondaryGripX", profile.secondaryGripX());
            setNumber(properties, prefix + "secondaryGripY", profile.secondaryGripY());
            setNumber(properties, prefix + "secondaryGripZ", profile.secondaryGripZ());
            properties.setProperty(prefix + "leftArmorPath", profile.leftArmorPath());
            properties.setProperty(prefix + "rightArmorPath", profile.rightArmorPath());
            properties.setProperty(prefix + "leftCoverage", profile.leftCoverage().name());
            properties.setProperty(prefix + "rightCoverage", profile.rightCoverage().name());
            for (Map.Entry<FirstPersonCombatLibrary.AnimationSlot,
                    FirstPersonCombatLibrary.ClipBinding> entry : profile.overrides().entrySet()) {
                String binding = prefix + "override." + entry.getKey().name() + ".";
                FirstPersonCombatLibrary.ClipBinding value = entry.getValue();
                properties.setProperty(binding + "path", value.path());
                properties.setProperty(binding + "clipName", value.clipName());
                setNumber(properties, binding + "speed", value.playbackSpeed());
                setNumber(properties, binding + "impactFraction", value.impactFraction());
            }
        }
        if (saveEditorProperties(WEAPON_ANIMATION_RESOURCE_PATH, properties,
                "Aether first-person weapon animation sets and item profiles")) {
            FirstPersonCombatLibrary.reload();
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
            JList<SkillLibrary> skillList,
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
            JButton generateLimbsButton,
            JTextArea limbDescriptionArea
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
        centerPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        JScrollPane skillScroll = new JScrollPane(skillList);
        skillScroll.setPreferredSize(new Dimension(180, 120));
        skillScroll.setBorder(BorderFactory.createTitledBorder("Skills"));
        centerPanel.add(skillScroll, BorderLayout.EAST);
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
        if (generateLimbsButton != null) {
            addFormRow(audioLootFields, "Limbs Generator", generateLimbsButton);
        }
        if (limbDescriptionArea != null) {
            JPanel limbBox = new JPanel(new BorderLayout(4, 4));
            limbBox.setBorder(BorderFactory.createTitledBorder("Limb Description"));
            limbBox.add(new JScrollPane(limbDescriptionArea), BorderLayout.CENTER);
            audioLootFields.add(limbBox);
        }

        JPanel audioLootPanel = new JPanel(new BorderLayout(6, 6));
        audioLootPanel.add(audioLootFields, BorderLayout.NORTH);

        tabs.addTab("General & Identity", generalPanel);
        tabs.addTab("Combat Stats & AI", new JScrollPane(topAlignedForm(combatFields)));
        tabs.addTab("3D Model & Rig", new JScrollPane(topAlignedForm(modelFields)));
        tabs.addTab("Audio & Loot", audioLootPanel);

        return tabs;
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
        JButton generateLimbsButton = new JButton("Generate Default Limbs");
        JTextField attackSoundField = new JTextField("", 24);
        JTextField damageSoundField = new JTextField("", 24);
        JButton attackSoundBrowseButton = new JButton("Browse");
        JButton damageSoundBrowseButton = new JButton("Browse");
        JButton dropsButton = new JButton("Drops");
        JLabel meleeMaxDamageLabel = new JLabel();
        JSpinner spellBaseDamageSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
        JLabel spellMaxDamageLabel = new JLabel();
        JList<SkillLibrary> skillList = new JList<>(SkillLibrary.values());
        skillList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JTextArea descriptionArea = new JTextArea("A custom enemy.", 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextArea limbDescriptionArea = new JTextArea("A remnant taken from " + nameField.getText() + ".", 3, 30);
        limbDescriptionArea.setLineWrap(true);
        limbDescriptionArea.setWrapStyleWord(true);
        JLabel difficultyPreviewLabel = new JLabel();
        List<MapDesignLibrary.CustomLimb> generatedLimbs = new ArrayList<>();
        List<MapDesignLibrary.CustomDropEntry> dropEntries = new ArrayList<>();
        String[] generatedMobId = { "" };

        browseButton.addActionListener(event -> browsePathInto(imagePathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        attackSoundBrowseButton.addActionListener(event -> browsePathInto(attackSoundField));
        damageSoundBrowseButton.addActionListener(event -> browsePathInto(damageSoundField));
        Runnable updateDifficultyPreview = () -> {
            EnumMap<PlayerStat, Integer> statValues = statValuesFromSpinners(statSpinners);
            hpLabel.setText("HP = " + statValues.getOrDefault(PlayerStat.VITALITY, 1));
            difficultyPreviewLabel.setText(customEnemyDifficultyPreview(statValues, skillList.getSelectedValuesList()));
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
        generateLimbsButton.addActionListener(event -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isBlank()) {
                setStatus("Name the enemy before generating limbs.");
                return;
            }

            String mobId = nextCustomMobId(name);
            generatedMobId[0] = mobId;
            generatedLimbs.clear();
            generatedLimbs.addAll(generateLimbsForEnemy(
                    mobId,
                    name,
                    statValuesFromSpinners(statSpinners),
                    limbDescriptionArea.getText() == null ? "" : limbDescriptionArea.getText().trim(),
                    paperDollSourceField.getText() == null ? "" : paperDollSourceField.getText().trim(),
                    skillList.getSelectedValuesList()));
            editGeneratedLimbs(generatedLimbs);
        });

        Component formTabs = buildMobTabbedForm(
                nameField, imagePathField, browseButton, paperDollSourceField, paperDollBrowseButton,
                xpSpinner, descriptionArea, skillList, statSpinners, hpLabel, difficultyPreviewLabel,
                meleeMaxDamageLabel, spellBaseDamageSpinner, spellMaxDamageLabel, combatAiSpinner,
                awarenessRadiusSpinner, movementIntervalSpinner, respawnDelaySpinner, characterModelFields,
                attackSoundField, attackSoundBrowseButton, damageSoundField, damageSoundBrowseButton,
                dropsButton, generateLimbsButton, limbDescriptionArea
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

        String mobId = generatedMobId[0].isBlank() ? nextCustomMobId(name) : generatedMobId[0];
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
                skillList.getSelectedValuesList(),
                dropEntries,
                characterModel);
        design.customMobs().add(mob);
        for (MapDesignLibrary.CustomLimb limb : generatedLimbs) {
            if (!hasCustomLimbId(limb.limbId())) {
                design.customLimbs().add(limb);
            }
        }
        persistSharedContent("custom enemy");
        populatePlaceables();
        setStatus("Created custom enemy " + mob.displayName()
                + (generatedLimbs.isEmpty() ? "." : " with " + generatedLimbs.size() + " limbs."));
    }

    private String customEnemyDifficultyPreview(
            Map<PlayerStat, Integer> statValues,
            List<SkillLibrary> skills) {
        DifficultyResolver.DifficultyRating rating = DifficultyResolver.rateMonsterProfile(
                "Preview Enemy",
                statValues,
                skills);
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
            dialogueBox.setEnabled(!enabled);
        };
        shopkeeperBox.addActionListener(event -> updateShopControls.run());
        updateShopControls.run();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Identity", identityPanel);
        tabs.addTab("3D Model & Rig", new JScrollPane(topAlignedForm(modelFields)));
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
                shop == null && dialogueOption != null ? dialogueOption.interactionId() : "",
                shop,
                characterModel);
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

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Item"));
        fields.add(itemBox);
        fields.add(new JLabel("Quantity"));
        fields.add(quantityField);
        fields.add(new JLabel(""));
        fields.add(infiniteBox);
        fields.add(new JLabel("Buy Price (-1 = default)"));
        fields.add(buyPriceField);
        fields.add(new JLabel("Sell Price (-1 = default)"));
        fields.add(sellPriceField);
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
                "");
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
        JButton lootButton = new JButton("Edit Loot Table");
        List<MapDesignLibrary.CustomDropEntry> lootEntries = new ArrayList<>();

        skillBox.setSelectedItem(CharacterSkill.MINING);
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));
        barBrowse.addActionListener(event -> browsePathInto(barImageField));
        lootButton.addActionListener(event -> editGatheringLootEntries(lootEntries));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
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
        fields.add(materialHelperRow);
        fields.add(materialRow);
        fields.add(smeltingRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        fields.add(barIconRow);
        fields.add(frameOneRow);
        fields.add(frameTwoRow);
        fields.add(frameThreeRow);

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
            setFormRowLabel(frameOneRow, tree ? "Full Tree" : "Stage / Frame 0");
            setFormRowLabel(frameTwoRow, tree ? "Stump" : "Stage / Frame 1");
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
                            1,
                            0));
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
                                1,
                                0));
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
                    fishing ? DEFAULT_FISHING_FRAME_DURATION_MS : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1);
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

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
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

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
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
                1,
                0));
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
                1,
                0));
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
                        1,
                        0));
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
                        1,
                        0));
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
        MapDesignLibrary.CustomItem edited = showCustomItemDialog("Edit Item", selected);
        if (edited == null) {
            return;
        }

        int index = design.customItems().indexOf(selected);
        if (index >= 0) {
            design.customItems().set(index, edited);
            persistSharedContent("custom item");
            populatePlaceables();
            setStatus("Updated custom item " + edited.displayName() + ".");
        }
    }

    private void deleteCustomItem(MapDesignLibrary.CustomItem selected) {
        if (!confirmDelete("item", selected.displayName(), ContentCategory.ITEMS, selected.itemId(), selected)) {
            return;
        }

        design.customItems().remove(selected);
        persistSharedContent("custom item");
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
                selected.firstPersonRigId());
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
        if (!confirmDelete("limb", selected.displayName(), ContentCategory.LIMBS, selected.limbId(), selected)) {
            return;
        }

        design.customLimbs().remove(selected);
        persistSharedContent("custom limb");
        populatePlaceables();
        setStatus("Deleted custom limb " + selected.displayName() + ".");
    }

    private void deleteCustomGatheringNode(MapDesignLibrary.CustomGatheringNode selected) {
        if (!confirmDelete("gathering node", selected.displayName(), ContentCategory.GATHERING, selected.nodeId(),
                selected)) {
            return;
        }

        design.customGatheringNodes().remove(selected);
        design.placements().removeIf(placement -> placement.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE
                && (selected.nodeId().equals(placement.id()) || selected.interactionId().equals(placement.id())));
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
            DropItemOption legacyOutput = new DropItemOption(
                    selected.smeltOutputItemId(),
                    "Current / Missing Item");
            smeltOutputBox.addItem(legacyOutput);
            smeltOutputBox.setSelectedItem(legacyOutput);
        }
        JTextField frameOneField = new JTextField(framePathAt(selected, 0), 28);
        JTextField frameTwoField = new JTextField(framePathAt(selected, 1), 28);
        JTextField frameThreeField = new JTextField(framePathAt(selected, 2), 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        JButton lootButton = new JButton("Edit Loot Table");
        List<MapDesignLibrary.CustomDropEntry> lootEntries = new ArrayList<>(selected.lootEntries());

        typeBox.setSelectedItem(selected.nodeType());
        skillBox.setSelectedItem(selected.gatheringSkill());
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));
        lootButton.addActionListener(event -> editGatheringLootEntries(lootEntries));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
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
        fields.add(smeltingRow);
        fields.add(smeltOutputRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        fields.add(frameOneRow);
        fields.add(frameTwoRow);
        fields.add(frameThreeRow);

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
            setFormRowLabel(frameOneRow, tree ? "Full Tree" : "Stage / Frame 0");
            setFormRowLabel(frameTwoRow, tree ? "Stump" : "Stage / Frame 1");
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
        smeltingBox.addActionListener(event -> updateGatheringNodeFields.run());
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
                    fishing ? DEFAULT_FISHING_FRAME_DURATION_MS : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining && smeltingBox.isSelected()
                            ? ((Number) smeltingLevelSpinner.getValue()).intValue()
                            : 1);
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
        JList<SkillLibrary> skillList = new JList<>(SkillLibrary.values());
        skillList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selectSkills(skillList, selected.skillIds());
        JTextArea descriptionArea = new JTextArea(selected.description(), 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        List<MapDesignLibrary.CustomDropEntry> dropEntries = new ArrayList<>(selected.dropEntries());
        JButton dropsButton = new JButton("Drops");

        browseButton.addActionListener(event -> browsePathInto(imagePathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        attackSoundBrowseButton.addActionListener(event -> browsePathInto(attackSoundField));
        damageSoundBrowseButton.addActionListener(event -> browsePathInto(damageSoundField));
        dropsButton.addActionListener(event -> editDropEntries(dropEntries));
        Runnable updatePreview = () -> {
            EnumMap<PlayerStat, Integer> statValues = statValuesFromSpinners(statSpinners);
            hpLabel.setText("HP = " + statValues.getOrDefault(PlayerStat.VITALITY, 1));
            difficultyPreviewLabel.setText(customEnemyDifficultyPreview(statValues, skillList.getSelectedValuesList()));
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
                dropsButton, null, null
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
                skillList.getSelectedValuesList(),
                dropEntries,
                characterModel);
        int index = design.customMobs().indexOf(selected);
        if (index >= 0) {
            design.customMobs().set(index, edited);
            persistSharedContent("custom enemy");
            populatePlaceables();
            setStatus("Updated custom enemy " + edited.displayName() + ".");
        }
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
        message.append(
                "\nA timestamped authored-content backup will be written under assets/editor/content/backups before saving.");
        return message.toString();
    }

    private List<MapDesignLibrary.CustomLimb> generateLimbsForEnemy(
            String mobId,
            String enemyName,
            Map<PlayerStat, Integer> monsterStats,
            String limbDescription,
            String paperDollSourcePath,
            List<SkillLibrary> enemySkills) {
        Map<LimbSlot, List<SkillLibrary>> skillAssignments = assignSkillsToLimbs(enemySkills);
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

    private void selectSkills(JList<SkillLibrary> skillList, List<SkillLibrary> selectedSkills) {
        if (skillList == null || selectedSkills == null || selectedSkills.isEmpty()) {
            return;
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < skillList.getModel().getSize(); i++) {
            SkillLibrary skill = skillList.getModel().getElementAt(i);
            if (selectedSkills.contains(skill)) {
                indices.add(i);
            }
        }
        skillList.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
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
                    selected.firstPersonRigId());
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

    private Map<LimbSlot, List<SkillLibrary>> assignSkillsToLimbs(List<SkillLibrary> skills) {
        Map<LimbSlot, List<SkillLibrary>> assignments = new EnumMap<>(LimbSlot.class);
        for (LimbSlot slot : LimbSlot.values()) {
            assignments.put(slot, new ArrayList<>());
        }

        if (skills == null) {
            return assignments;
        }

        for (SkillLibrary skill : skills) {
            LimbSlot slot = bestSlotForSkill(skill, assignments);
            assignments.get(slot).add(skill);
        }

        return assignments;
    }

    private LimbSlot bestSlotForSkill(SkillLibrary skill, Map<LimbSlot, List<SkillLibrary>> assignments) {
        List<LimbSlot> preferences;
        if (skill == SkillLibrary.ABSORB || skill.getSelfHealPercent() > 0.0) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (skill.getEffectType() == Library.EffectType.SUMMON) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (skill.getEffectType() == Library.EffectType.DEFEND || skill.getDamageReduction() > 0.0) {
            preferences = List.of(LimbSlot.BODY, LimbSlot.LEGS);
        } else if (skill.getTargetingMode() == Library.BattleTargetingMode.MAGIC) {
            preferences = List.of(LimbSlot.HEAD, LimbSlot.LEFT_ARM, LimbSlot.RIGHT_ARM);
        } else if (skill.getTargetingMode() == Library.BattleTargetingMode.RANGED
                || skill.getTargetingMode() == Library.BattleTargetingMode.NORMAL_MELEE
                || skill.getTargetingMode() == Library.BattleTargetingMode.REACH_MELEE) {
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
            List<SkillLibrary> selectedSkills,
            String firstPersonModelPath,
            String firstPersonRigId) {
        JTextField nameField = new JTextField(displayName, 24);
        JTextField iconPathField = new JTextField(iconPath, 28);
        JTextField sourceCreatureIdField = new JTextField(sourceCreatureId == null ? "" : sourceCreatureId, 28);
        JTextField paperDollSourceField = new JTextField(paperDollSourcePath == null ? "" : paperDollSourcePath, 28);
        JTextField firstPersonModelField = new JTextField(
                firstPersonModelPath == null ? "" : firstPersonModelPath, 28);
        JTextField firstPersonRigField = new JTextField(
                firstPersonRigId == null ? "" : firstPersonRigId, 18);
        JTextArea descriptionArea = new JTextArea(description == null ? "" : description, 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JButton firstPersonBrowseButton = new JButton("Browse");
        JComboBox<LimbSlot> slotBox = new JComboBox<>(LimbSlot.values());
        slotBox.setSelectedItem(limbSlot == null ? LimbSlot.HEAD : limbSlot);
        Map<PlayerStat, JSpinner> statSpinners = new EnumMap<>(PlayerStat.class);
        Map<SkillLibrary, JCheckBox> skillBoxes = new EnumMap<>(SkillLibrary.class);

        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));
        firstPersonBrowseButton.addActionListener(
                event -> showAssetBrowser(firstPersonModelField, AssetBrowserType.MODELS));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Icon PNG"));
        fields.add(pathFieldPanel(iconPathField, browseButton));
        fields.add(new JLabel("Paper-Doll Source"));
        fields.add(pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        fields.add(new JLabel("First-Person Arm Model"));
        fields.add(pathFieldPanel(firstPersonModelField, firstPersonBrowseButton));
        fields.add(new JLabel("First-Person Rig ID"));
        fields.add(firstPersonRigField);
        fields.add(new JLabel("Source Creature Id"));
        fields.add(sourceCreatureIdField);
        fields.add(new JLabel("Slot"));
        fields.add(slotBox);
        fields.add(new JLabel("Condition"));
        fields.add(new JLabel(GearDurability.PERFECT.name()));
        for (PlayerStat stat : PlayerStat.values()) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    Math.max(0, statValues == null ? 0 : statValues.getOrDefault(stat, 0)), 0, 1000, 1));
            statSpinners.put(stat, spinner);
            fields.add(new JLabel(stat.getDisplayName()));
            fields.add(spinner);
        }
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.add(new JScrollPane(fields), BorderLayout.CENTER);
        centerPanel.add(new JScrollPane(descriptionArea), BorderLayout.SOUTH);
        JPanel skillsPanel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        for (SkillLibrary skill : SkillLibrary.values()) {
            JCheckBox checkBox = new JCheckBox(skill.getDisplayName());
            checkBox.setSelected(selectedSkills != null && selectedSkills.contains(skill));
            skillBoxes.put(skill, checkBox);
            skillsPanel.add(checkBox);
        }
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(new JScrollPane(skillsPanel), BorderLayout.EAST);

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
        List<SkillLibrary> skills = new ArrayList<>();
        for (Map.Entry<SkillLibrary, JCheckBox> entry : skillBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                skills.add(entry.getKey());
            }
        }

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
                firstPersonRigField.getText() == null ? "" : firstPersonRigField.getText().trim());
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
        return true;
    }

    private boolean isSupportedCharacterModelAsset(String path) {
        String normalized = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".glb") || normalized.endsWith(".fbx");
    }

    private JPanel pathFieldPanel(JTextField pathField, JButton browseButton) {
        JPanel imagePanel = new JPanel(new BorderLayout(4, 4));
        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 0, 4, 0));
        JButton assetButton = new JButton("Assets");
        assetButton.addActionListener(event -> showAssetBrowser(pathField));
        buttons.add(browseButton);
        buttons.add(assetButton);
        imagePanel.add(pathField, BorderLayout.CENTER);
        imagePanel.add(buttons, BorderLayout.EAST);
        return imagePanel;
    }

    private JPanel createFormPanel() {
        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        return fields;
    }

    private JPanel formRow(String label, Component component) {
        JPanel row = new JPanel(new BorderLayout(6, 6));
        JLabel rowLabel = new JLabel(label);
        rowLabel.setPreferredSize(new Dimension(170, 24));
        row.add(rowLabel, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    private JPanel inlinePanel(Component first, Component second) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(first, BorderLayout.CENTER);
        panel.add(second, BorderLayout.EAST);
        return panel;
    }

    private void addFormRow(java.awt.Container container, String label, Component component) {
        container.add(formRow(label, component));
    }

    private int showScrollableFormDialog(Component form, String title) {
        return showScrollableFormDialog(this, form, title);
    }

    private int showScrollableFormDialog(Component parent, Component form, String title) {
        Component content = screenAwarePopupContent(form);
        return showResizableOptionDialog(
                parent,
                content,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
    }

    private Component topAlignedForm(Component fields) {
        if (fields == null) {
            return new JPanel();
        }
        if (fields instanceof JScrollPane || fields instanceof JTabbedPane || fields instanceof JSplitPane) {
            return fields;
        }
        JPanel wrapper = new JPanel(new BorderLayout(6, 6));
        wrapper.add(fields, BorderLayout.NORTH);
        return wrapper;
    }

    private Component screenAwarePopupContent(Component form) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension preferred = form == null ? new Dimension(600, 460) : form.getPreferredSize();
        int availableWidth = Math.max(480, screen.width - 180);
        int availableHeight = Math.max(280, screen.height - 200);
        int viewportWidth = Math.min(680, Math.min(availableWidth, Math.max(540, preferred.width + 28)));
        int viewportHeight = Math.min(520, Math.min(availableHeight, Math.max(380, preferred.height + 28)));

        Component viewContent = topAlignedForm(form);
        JScrollPane scrollPane = form instanceof JScrollPane existing
                ? existing
                : new JScrollPane(
                        viewContent,
                        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(viewportWidth, viewportHeight));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        return scrollPane;
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
        if (dialog == null) {
            return;
        }
        dialog.setResizable(true);
        dialog.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = Math.max(420, screen.width - 140);
        int maxHeight = Math.max(260, screen.height - 160);
        String sizeKey = dialog.getTitle() == null || dialog.getTitle().isBlank()
                ? "Construction Kit Popup"
                : dialog.getTitle();
        Dimension remembered = popupSizes.get(sizeKey);
        int width = remembered == null ? dialog.getWidth() : remembered.width;
        int height = remembered == null ? dialog.getHeight() : remembered.height;
        dialog.setMinimumSize(new Dimension(
                Math.min(360, maxWidth),
                Math.min(180, maxHeight)));
        dialog.setSize(
                Math.min(Math.max(dialog.getMinimumSize().width, width), maxWidth),
                Math.min(Math.max(dialog.getMinimumSize().height, height), maxHeight));
        dialog.setLocationRelativeTo(dialog.getOwner() == null ? this : dialog.getOwner());
        dialog.setVisible(true);
        popupSizes.put(sizeKey, dialog.getSize());
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
            int requiredQuestStage = readPrefabInt(properties, prefix + "requiredQuestStage", 0);
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
                        requiredQuestStage,
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

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Name"));
        panel.add(nameField);
        panel.add(new JLabel("X"));
        panel.add(xSpinner);
        panel.add(new JLabel("Y"));
        panel.add(ySpinner);
        panel.add(new JLabel("Width"));
        panel.add(widthSpinner);
        panel.add(new JLabel("Height"));
        panel.add(heightSpinner);

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
                    trigger.requiredQuestStage(),
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
            properties.setProperty(prefix + "requiredQuestStage", String.valueOf(trigger.requiredQuestStage()));
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
        pendingTriggerQuestStage = settings.requiredQuestStage();
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
        dialog.setVisible(true);
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
                new TriggerActivationOption("Quest reaches stage", MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE)
        });
        if (trigger != null && trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE) {
            activationBox.setSelectedIndex(1);
        }

        JComboBox<QuestActionOption> questBox = new JComboBox<>(questActionOptions());
        if (trigger != null) {
            selectQuestActionOption(questBox, trigger.requiredQuestId());
        }
        JSpinner stageSpinner = new JSpinner(new SpinnerNumberModel(
                trigger == null ? 0 : trigger.requiredQuestStage(),
                0,
                999,
                1));
        JCheckBox oneShotBox = new JCheckBox("Fire only once", trigger == null || trigger.oneShot());
        JLabel stageHint = new JLabel("Quest stages are numbered from 0.");

        Runnable updateQuestControls = () -> {
            TriggerActivationOption activation = (TriggerActivationOption) activationBox.getSelectedItem();
            boolean questActivation = activation != null
                    && activation.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE;
            questBox.setEnabled(questActivation);
            stageSpinner.setEnabled(questActivation);
            stageHint.setEnabled(questActivation);
        };
        activationBox.addActionListener(event -> updateQuestControls.run());
        updateQuestControls.run();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Trigger id"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(idField, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0;
        panel.add(new JLabel("Activate when"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(activationBox, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0;
        panel.add(new JLabel("Quest"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(questBox, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0;
        panel.add(new JLabel("Minimum stage"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(stageSpinner, constraints);
        constraints.gridx = 1;
        constraints.gridy++;
        panel.add(stageHint, constraints);
        constraints.gridy++;
        panel.add(oneShotBox, constraints);

        while (showScrollableFormDialog(parent, panel, title) == JOptionPane.OK_OPTION) {
            String id = idField.getText() == null ? "" : idField.getText().trim();
            TriggerActivationOption activation = (TriggerActivationOption) activationBox.getSelectedItem();
            MapDesignLibrary.TriggerFireMode fireMode = activation == null
                    ? MapDesignLibrary.TriggerFireMode.ON_ENTRY
                    : activation.fireMode();
            QuestActionOption quest = (QuestActionOption) questBox.getSelectedItem();
            String questId = fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE && quest != null
                    ? quest.questId()
                    : "";
            if (id.isBlank()) {
                showAdaptiveTextMessageDialog(
                        parent,
                        "Trigger id cannot be blank.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (fireMode == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE && questId.isBlank()) {
                showAdaptiveTextMessageDialog(
                        parent,
                        "Choose an authored quest for a quest-stage trigger.",
                        title,
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return new TriggerSettings(
                    id,
                    fireMode,
                    oneShotBox.isSelected(),
                    questId,
                    ((Number) stageSpinner.getValue()).intValue());
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
                    trigger.requiredQuestStage(),
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
                    .fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE
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
                    settings.requiredQuestStage(),
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
            String action = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE ? "open" : "close";
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
        String activation = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE
                ? "Quest " + trigger.requiredQuestId() + " >= " + trigger.requiredQuestStage()
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
                    trigger.requiredQuestStage(),
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

    private void editAuthoredDialogue(MapDesignLibrary.AuthoredDialogue selected) {
        AuthoredDialogueDraft draft = showAuthoredDialogueDialog(
                "Edit Dialogue",
                selected.speakerName(),
                selected.bodyText(),
                selected.followUpInteractionId(),
                selected.questId(),
                selected.questStage(),
                selected.choices(),
                selected.nodes());
        if (draft == null) {
            return;
        }

        int index = design.authoredDialogues().indexOf(selected);
        if (index < 0) {
            return;
        }

        design.authoredDialogues().set(index, new MapDesignLibrary.AuthoredDialogue(
                selected.interactionId(),
                draft.speakerName(),
                draft.bodyText(),
                draft.followUpInteractionId(),
                MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                "",
                null,
                0,
                0,
                draft.questId(),
                draft.questStage(),
                draft.choices(),
                draft.nodes()));
        persistSharedContent("authored dialogue");
        populatePlaceables();
        mapCanvas.repaint();
        setStatus("Updated dialogue " + draft.speakerName() + ".");
    }

    private void deleteAuthoredDialogue(MapDesignLibrary.AuthoredDialogue selected) {
        int result = showAdaptiveTextConfirmDialog(
                this,
                deleteMessage(
                        "Delete " + selected.speakerName() + " and remove its placed NPCs?",
                        findReferences(new ContentEntry(ContentCategory.DIALOGUES, selected.speakerName(),
                                selected.interactionId(), "Dialogue", selected))),
                "Delete Dialogue",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        design.authoredDialogues().remove(selected);
        int clearedNpcAssignments = 0;
        for (int i = 0; i < design.customNpcs().size(); i++) {
            MapDesignLibrary.CustomNpc npc = design.customNpcs().get(i);
            if (!selected.interactionId().equals(npc.interactionId())) {
                continue;
            }
            design.customNpcs().set(i, new MapDesignLibrary.CustomNpc(
                    npc.npcId(),
                    npc.displayName(),
                    npc.imagePath(),
                    npc.talkSoundPath(),
                    "",
                    npc.shop()));
            clearedNpcAssignments++;
        }
        int beforePlacements = design.placements().size();
        design.placements()
                .removeIf(placement -> placement.kind() == MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC
                        && selected.interactionId().equals(placement.id()));
        int removedPlacements = beforePlacements - design.placements().size();
        persistSharedContent("authored dialogue");
        populatePlaceables();
        mapCanvas.repaint();
        setStatus("Deleted dialogue " + selected.speakerName()
                + ", cleared " + clearedNpcAssignments + " NPC assignments, and removed "
                + removedPlacements + " legacy dialogue placements.");
    }

    private AuthoredDialogueDraft showAuthoredDialogueDialog(
            String title,
            String speakerName,
            String bodyText,
            String followUpInteractionId,
            String questId,
            int questStage,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes) {
        JTextField speakerField = new JTextField(speakerName, 24);
        JTextArea bodyArea = new JTextArea(bodyText, 7, 28);
        boolean newDialogue = title != null
                && title.startsWith("New ")
                && (choices == null || choices.isEmpty())
                && (nodes == null || nodes.isEmpty());
        JTextArea branchArea = new JTextArea(
                newDialogue ? newDialogueExampleTemplate() : formatDialogueTree(choices, nodes),
                9,
                32);
        JComboBox<FollowUpInteractionOption> followUpBox = new JComboBox<>(followUpOptions());
        selectFollowUpOption(followUpBox, followUpInteractionId);
        JComboBox<QuestActionOption> questActionBox = new JComboBox<>(questActionOptions());
        selectQuestActionOption(questActionBox, questId);
        JSpinner questStageSpinner = new JSpinner(new SpinnerNumberModel(Math.max(-1, questStage), -1, 100, 1));
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        branchArea.setLineWrap(true);
        branchArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel speakerPanel = new JPanel(new BorderLayout(6, 6));
        speakerPanel.add(new JLabel("Speaker"), BorderLayout.WEST);
        speakerPanel.add(speakerField, BorderLayout.CENTER);
        panel.add(speakerPanel, BorderLayout.NORTH);
        JPanel textPanel = new JPanel(new BorderLayout(6, 6));
        textPanel.add(new JLabel("Default repeat text"), BorderLayout.NORTH);
        textPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        JPanel branchPanel = new JPanel(new BorderLayout(4, 4));
        branchPanel.add(new JLabel("Dialogue Tree: use ::firstTime once and ::repeatTalk for reusable topics"),
                BorderLayout.NORTH);
        branchPanel.add(new JScrollPane(branchArea), BorderLayout.CENTER);
        branchPanel.add(
                new JLabel("Choice: - Button => node_id. Quest: [hasItem=Raw Fish] \"quest_id\"[2] Button => node_id"),
                BorderLayout.SOUTH);
        textPanel.add(branchPanel, BorderLayout.SOUTH);
        panel.add(textPanel, BorderLayout.CENTER);
        JPanel optionsPanel = new JPanel(new BorderLayout(6, 6));
        JPanel followUpPanel = new JPanel(new BorderLayout(6, 6));
        followUpPanel.add(new JLabel("Then"), BorderLayout.WEST);
        followUpPanel.add(followUpBox, BorderLayout.CENTER);
        JPanel questActionPanel = new JPanel(new BorderLayout(6, 6));
        questActionPanel.add(new JLabel("Set Quest"), BorderLayout.WEST);
        questActionPanel.add(questActionBox, BorderLayout.CENTER);
        questActionPanel.add(questStageSpinner, BorderLayout.EAST);
        JPanel rewardPanel = new JPanel(new BorderLayout(6, 6));
        rewardPanel.add(questActionPanel, BorderLayout.CENTER);
        optionsPanel.add(followUpPanel, BorderLayout.CENTER);
        optionsPanel.add(rewardPanel, BorderLayout.SOUTH);
        panel.add(optionsPanel, BorderLayout.SOUTH);

        int result = showScrollableFormDialog(panel, title);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String enteredSpeakerName = speakerField.getText() == null ? "" : speakerField.getText().trim();
        String enteredBodyText = bodyArea.getText() == null ? "" : bodyArea.getText().trim();
        DialogueTreeDraft treeDraft = parseDialogueTree(branchArea.getText());

        if (enteredSpeakerName.isBlank() || enteredBodyText.isBlank()) {
            setStatus("Dialogue needs a speaker and dialogue text.");
            return null;
        }

        FollowUpInteractionOption selectedFollowUp = (FollowUpInteractionOption) followUpBox.getSelectedItem();
        QuestActionOption selectedQuest = (QuestActionOption) questActionBox.getSelectedItem();
        return new AuthoredDialogueDraft(
                enteredSpeakerName,
                enteredBodyText,
                selectedFollowUp == null ? "" : selectedFollowUp.interactionId(),
                selectedQuest == null ? "" : selectedQuest.questId(),
                ((Number) questStageSpinner.getValue()).intValue(),
                treeDraft.choices(),
                treeDraft.nodes());
    }

    private String newDialogueExampleTemplate() {
        return """
                # EXAMPLE TEMPLATE - lines beginning with # are ignored when saved.
                # Remove the leading "# " from any section you want to use.
                #
                # ::firstTime
                # This text appears only the first time the player talks to this NPC.
                # - Tell me about this place => about_place
                #
                # ::repeatTalk
                # What would you like to discuss?
                # - Tell me about this place => about_place
                # - [hasItem=Raw Fish] I found the item you wanted => item_turn_in
                # - "quest_id"[2] I completed the quest step => quest_progress
                # - Ask a simple question => This is a direct response without another node.
                #
                # ::about_place
                # This is an example topic response. It automatically gets an Other topics button.
                #
                # ::item_turn_in
                # You can require, remove, and reward items from a choice.
                # - [takeItem=Raw Fish] [giveItem=Cooked Fish] [giveGold=10] [giveXp=Cooking:25] Hand it over => item_complete
                #
                # ::item_complete
                # Thank you. Here is your reward.
                #
                # ::quest_progress
                # Quest choices appear at the preceding stage and set the quest to the stage in brackets.
                """;
    }

    private String formatDialogueTree(
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes) {
        List<String> lines = new ArrayList<>();
        if (choices != null) {
            for (MapDesignLibrary.AuthoredDialogueChoice choice : choices) {
                lines.add(formatDialogueChoice(choice, false));
            }
        }
        if (nodes != null) {
            for (MapDesignLibrary.AuthoredDialogueNode node : nodes) {
                if (!lines.isEmpty()) {
                    lines.add("");
                }
                lines.add("::" + node.nodeId());
                lines.add(node.bodyText());
                for (MapDesignLibrary.AuthoredDialogueChoice choice : node.choices()) {
                    lines.add(formatDialogueChoice(choice, true));
                }
            }
        }
        return String.join("\n", lines);
    }

    private String formatDialogueChoice(MapDesignLibrary.AuthoredDialogueChoice choice, boolean nodeChoice) {
        StringBuilder line = new StringBuilder(nodeChoice ? "- " : "");
        if (!choice.requiredItemName().isBlank()) {
            line.append("[hasItem=").append(choice.requiredItemName()).append("] ");
        }
        if (!choice.takeItemName().isBlank()) {
            line.append("[takeItem=").append(choice.takeItemName()).append("] ");
        }
        if (!choice.giveItemName().isBlank()) {
            line.append("[giveItem=").append(choice.giveItemName()).append("] ");
        }
        if (choice.giveGold() > 0) {
            line.append("[giveGold=").append(choice.giveGold()).append("] ");
        }
        if (choice.giveSkill() != null && choice.giveSkillXp() > 0) {
            line.append("[giveXp=")
                    .append(choice.giveSkill().getDisplayName())
                    .append(":")
                    .append(choice.giveSkillXp())
                    .append("] ");
        }
        if (!choice.questId().isBlank() && choice.questStage() >= 0) {
            line.append('"').append(choice.questId()).append("\"[").append(choice.questStage()).append("] ");
        }
        line.append(choice.label()).append(" => ");
        line.append(choice.targetNodeId().isBlank() ? choice.bodyText() : choice.targetNodeId());
        return line.toString();
    }

    private DialogueTreeDraft parseDialogueTree(String text) {
        List<PendingChoice> rootChoices = new ArrayList<>();
        List<PendingNode> pendingNodes = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new DialogueTreeDraft(List.of(), List.of());
        }

        PendingNode currentNode = null;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }

            if (trimmed.startsWith("::")) {
                String nodeId = trimmed.substring(2).trim();
                if (!nodeId.isBlank()) {
                    currentNode = new PendingNode(nodeId);
                    pendingNodes.add(currentNode);
                }
                continue;
            }

            boolean optionLine = trimmed.startsWith("-");
            PendingChoice choice = parsePendingDialogueChoice(optionLine ? trimmed.substring(1).trim() : trimmed);
            if (choice != null) {
                if (currentNode == null) {
                    rootChoices.add(choice);
                } else {
                    currentNode.choices().add(choice);
                }
            } else if (currentNode != null) {
                currentNode.bodyLines().add(trimmed);
            }
        }

        List<String> nodeIds = pendingNodes.stream().map(PendingNode::nodeId).toList();
        List<MapDesignLibrary.AuthoredDialogueChoice> choices = toAuthoredChoices(rootChoices, nodeIds);
        List<MapDesignLibrary.AuthoredDialogueNode> nodes = new ArrayList<>();
        for (PendingNode node : pendingNodes) {
            String nodeBody = String.join("\n", node.bodyLines()).trim();
            if (!node.nodeId().isBlank() && !nodeBody.isBlank()) {
                nodes.add(new MapDesignLibrary.AuthoredDialogueNode(
                        node.nodeId(),
                        nodeBody,
                        toAuthoredChoices(node.choices(), nodeIds)));
            }
        }
        return new DialogueTreeDraft(choices, nodes);
    }

    private PendingChoice parsePendingDialogueChoice(String line) {
        String remaining = line == null ? "" : line.trim();
        if (remaining.isBlank()) {
            return null;
        }

        String requiredItemName = "";
        String takeItemName = "";
        String giveItemName = "";
        int giveGold = 0;
        CharacterSkill giveSkill = null;
        int giveSkillXp = 0;
        boolean firstTalkOnly = false;
        boolean parsedTag = true;
        while (parsedTag && remaining.startsWith("[")) {
            parsedTag = false;
            int tagEnd = remaining.indexOf(']');
            if (tagEnd > 0) {
                String tag = remaining.substring(1, tagEnd).trim();
                int equalsIndex = tag.indexOf('=');
                if ("firstTalk".equalsIgnoreCase(tag) || "firstTime".equalsIgnoreCase(tag)) {
                    firstTalkOnly = true;
                    parsedTag = true;
                } else if (equalsIndex > 0) {
                    String key = tag.substring(0, equalsIndex).trim();
                    String value = tag.substring(equalsIndex + 1).trim();
                    if ("hasItem".equalsIgnoreCase(key)) {
                        requiredItemName = value;
                        parsedTag = true;
                    } else if ("takeItem".equalsIgnoreCase(key)) {
                        takeItemName = value;
                        parsedTag = true;
                    } else if ("giveItem".equalsIgnoreCase(key) || "rewardItem".equalsIgnoreCase(key)) {
                        giveItemName = value;
                        parsedTag = true;
                    } else if ("giveGold".equalsIgnoreCase(key) || "gold".equalsIgnoreCase(key)) {
                        giveGold = parseNonNegativeInt(value);
                        parsedTag = true;
                    } else if ("giveXp".equalsIgnoreCase(key)
                            || "giveSkillXp".equalsIgnoreCase(key)
                            || "rewardXp".equalsIgnoreCase(key)) {
                        SkillXpTag skillXpTag = parseSkillXpTag(value);
                        giveSkill = skillXpTag.skill();
                        giveSkillXp = skillXpTag.amount();
                        parsedTag = true;
                    }
                }
                if (parsedTag) {
                    remaining = remaining.substring(tagEnd + 1).trim();
                }
            }
        }

        String questId = "";
        int questStage = -1;
        if (remaining.startsWith("\"")) {
            int quoteEnd = remaining.indexOf('"', 1);
            int bracketStart = quoteEnd < 0 ? -1 : remaining.indexOf('[', quoteEnd + 1);
            int bracketEnd = bracketStart < 0 ? -1 : remaining.indexOf(']', bracketStart + 1);
            if (quoteEnd > 0 && bracketStart == quoteEnd + 1 && bracketEnd > bracketStart) {
                questId = remaining.substring(1, quoteEnd).trim();
                try {
                    questStage = Integer.parseInt(remaining.substring(bracketStart + 1, bracketEnd).trim());
                } catch (NumberFormatException ignored) {
                    questStage = -1;
                }
                remaining = remaining.substring(bracketEnd + 1).trim();
            }
        }

        String[] parts = remaining.split("=>", 2);
        if (parts.length != 2 || parts[1].trim().isBlank()) {
            return null;
        }

        String label = parts[0].trim();
        if (label.isBlank()) {
            label = "Continue";
        }
        return new PendingChoice(
                label,
                parts[1].trim(),
                questId,
                questStage,
                requiredItemName,
                takeItemName,
                giveItemName,
                giveGold,
                giveSkill,
                giveSkillXp,
                firstTalkOnly);
    }

    private int parseNonNegativeInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "" : value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private SkillXpTag parseSkillXpTag(String value) {
        String safeValue = value == null ? "" : value.trim();
        int separator = safeValue.lastIndexOf(':');
        if (separator < 0) {
            separator = safeValue.lastIndexOf(',');
        }
        if (separator < 0) {
            return new SkillXpTag(null, 0);
        }

        CharacterSkill skill = parseCharacterSkill(safeValue.substring(0, separator));
        int amount = parseNonNegativeInt(safeValue.substring(separator + 1));
        return new SkillXpTag(skill, amount);
    }

    private CharacterSkill parseCharacterSkill(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        for (CharacterSkill skill : CharacterSkill.values()) {
            if (normalized.equalsIgnoreCase(skill.name())
                    || normalized.equalsIgnoreCase(skill.getDisplayName())) {
                return skill;
            }
        }
        return null;
    }

    private List<MapDesignLibrary.AuthoredDialogueChoice> toAuthoredChoices(
            List<PendingChoice> pendingChoices,
            List<String> nodeIds) {
        List<MapDesignLibrary.AuthoredDialogueChoice> choices = new ArrayList<>();
        for (PendingChoice choice : pendingChoices) {
            boolean targetsNode = "start".equals(choice.destination()) || nodeIds.contains(choice.destination());
            choices.add(new MapDesignLibrary.AuthoredDialogueChoice(
                    choice.label(),
                    targetsNode ? "" : choice.destination(),
                    targetsNode ? choice.destination() : "",
                    choice.questId(),
                    choice.questStage(),
                    choice.requiredItemName(),
                    choice.takeItemName(),
                    choice.giveItemName(),
                    choice.giveGold(),
                    choice.giveSkill(),
                    choice.giveSkillXp(),
                    choice.firstTalkOnly()));
        }
        return choices;
    }

    private AuthoredQuestDraft showAuthoredQuestDialog(String title, String displayName,
            List<String> stageDescriptions) {
        JTextField nameField = new JTextField(displayName, 24);
        JTextArea stagesArea = new JTextArea(String.join("\n", stageDescriptions), 8, 30);
        stagesArea.setLineWrap(true);
        stagesArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel namePanel = new JPanel(new BorderLayout(6, 6));
        namePanel.add(new JLabel("Quest"), BorderLayout.WEST);
        namePanel.add(nameField, BorderLayout.CENTER);
        panel.add(namePanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(stagesArea), BorderLayout.CENTER);

        int result = showScrollableFormDialog(panel, title);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String enteredName = nameField.getText() == null ? "" : nameField.getText().trim();
        List<String> stages = new ArrayList<>();
        for (String line : stagesArea.getText().split("\\R")) {
            String stage = line.trim();
            if (!stage.isBlank()) {
                stages.add(stage);
            }
        }

        if (enteredName.isBlank() || stages.isEmpty()) {
            setStatus("Quest needs a name and at least one stage.");
            return null;
        }

        return new AuthoredQuestDraft(enteredName, stages);
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

    private void selectFollowUpOption(JComboBox<FollowUpInteractionOption> followUpBox, String interactionId) {
        if (followUpBox == null) {
            return;
        }

        String safeInteractionId = interactionId == null ? "" : interactionId;
        for (int i = 0; i < followUpBox.getItemCount(); i++) {
            FollowUpInteractionOption option = followUpBox.getItemAt(i);
            if (option != null && safeInteractionId.equals(option.interactionId())) {
                followUpBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private FollowUpInteractionOption[] followUpOptions() {
        List<FollowUpInteractionOption> options = new ArrayList<>();
        options.add(new FollowUpInteractionOption("None", ""));

        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            options.add(new FollowUpInteractionOption(
                    dialogue.speakerName(),
                    dialogue.interactionId()));
        }

        return options.toArray(new FollowUpInteractionOption[0]);
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
                blank.mapPaint(), blank.mapGeometry(), blank.mobAreas(), blank.placements(), blank.authoredDialogues(),
                blank.authoredQuests(), blank.customItems(), blank.customMobs(), blank.customLimbs(),
                blank.customNpcs(), blank.customGatheringNodes(), blank.customCookingRecipes(),
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
                blank.authoredDialogues(),
                blank.authoredQuests(),
                blank.customItems(),
                blank.customMobs(),
                blank.customLimbs(),
                blank.customNpcs(),
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
        int droppedTriggers = 0;
        int droppedTriggerActions = 0;
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (!isInsideDimensions(placement.x(), placement.y(), newWidth, newHeight)) {
                droppedPlacements++;
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

        if (droppedPlacements > 0 || droppedTriggers > 0 || droppedTriggerActions > 0) {
            int result = showAdaptiveTextConfirmDialog(
                    this,
                    "Resize will remove content outside the new bounds:\n"
                            + "- Placements: " + droppedPlacements + "\n"
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
                    trigger.requiredQuestStage(),
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
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
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
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
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
        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
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
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
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
            case GENERIC_NPC, MAIN_NPC, CUSTOM_NPC -> new Color(220, 90, 220);
            case ITEM -> new Color(230, 210, 80);
            case ENEMY -> new Color(230, 80, 70);
            case AUTHORED_DIALOGUE_NPC -> new Color(130, 170, 245);
            case INTERACTION -> new Color(90, 200, 230);
        };
    }

    private static boolean isDoorTile(Library.TileType tile) {
        return tile == Library.TileType.DOOR_OPEN
                || tile == Library.TileType.DOOR_CLOSED
                || tile == Library.TileType.QUEST_DOOR_OPEN
                || tile == Library.TileType.QUEST_DOOR_CLOSED;
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
                int radiusPixels = (int) Math.round(light.radius() * cellSize);
                Color color = new Color(light.colorRgb());
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), light.enabled() ? 50 : 24));
                g.fillOval(centerX - radiusPixels, centerY - radiusPixels, radiusPixels * 2, radiusPixels * 2);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), light.enabled() ? 180 : 90));
                g.setStroke(new BasicStroke(Math.max(1.5f, cellSize / 18f)));
                g.drawOval(centerX - radiusPixels, centerY - radiusPixels, radiusPixels * 2, radiusPixels * 2);
                g.setColor(new Color(20, 20, 22, 220));
                int markerSize = Math.max(8, cellSize / 3);
                g.fillOval(centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize);
                g.setColor(color);
                g.drawOval(centerX - markerSize / 2, centerY - markerSize / 2, markerSize, markerSize);
                g.drawString("L", centerX - metrics.stringWidth("L") / 2, centerY + metrics.getAscent() / 2 - 1);
            }
            g.setStroke(oldStroke);
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

            if (light != null) {
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
            inspectedTrigger = trigger;
            inspectedLight = null;
            inspectedTriggerTarget = triggerTarget == null ? null : new Point(triggerTarget);
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
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
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
                        trigger.requiredQuestStage(),
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

            int lightsBefore = design.lights().size();
            design.lights().removeIf(light -> light.x() == x && light.y() == y);

            int triggersBefore = design.triggers().size();
            design.triggers().removeIf(trigger -> trigger.x() == x && trigger.y() == y);

            int removedWireTargets = removeTriggerTargetsAt(x, y);
            int removedPlacements = placementsBefore - design.placements().size();
            int removedLights = lightsBefore - design.lights().size();
            int removedTriggers = triggersBefore - design.triggers().size();

            if (removedTriggers > 0) {
                setStatus("Removed trigger at " + x + "," + y + ".");
            } else if (removedWireTargets > 0) {
                setStatus("Removed " + removedWireTargets + " trigger wire target(s) at " + x + "," + y + ".");
            } else if (removedLights > 0) {
                setStatus("Removed light at " + x + "," + y + ".");
            } else if (removedPlacements > 0) {
                setStatus("Removed placement at " + x + "," + y + ".");
            } else {
                setStatus("Nothing to erase at " + x + "," + y + ".");
            }
            if (removedTriggers > 0 || removedWireTargets > 0 || removedLights > 0 || removedPlacements > 0) {
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
                            trigger.requiredQuestStage(),
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
                    pendingTriggerQuestStage,
                    List.of());
            design.triggers().removeIf(existing -> existing.id().equals(trigger.id()));
            design.triggers().add(trigger);
            wiringTriggerId = trigger.id();
            pendingTriggerId = "";
            pendingTriggerFireMode = MapDesignLibrary.TriggerFireMode.ON_ENTRY;
            pendingTriggerOneShot = true;
            pendingTriggerQuestId = "";
            pendingTriggerQuestStage = 0;
            paintModeBox.setSelectedItem(PaintMode.WIRE_TRIGGER);
            refreshContentBrowser();
            String action = trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE ? "open" : "close";
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
                    .fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE
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
                    trigger.requiredQuestStage(),
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
                case GENERIC_NPC, MAIN_NPC, CUSTOM_NPC -> new Color(220, 90, 220);
                case ITEM -> new Color(230, 210, 80);
                case ENEMY -> new Color(230, 80, 70);
                case AUTHORED_DIALOGUE_NPC -> new Color(130, 170, 245);
                case INTERACTION -> new Color(90, 200, 230);
            };
        }
    }

}

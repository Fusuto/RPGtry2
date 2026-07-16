package org.main.tools;

import org.main.battle.DifficultyResolver;
import org.main.content.CraftingNodeLibrary;
import org.main.content.GatheringNodeLibrary;
import org.main.content.GenericNpcLibrary;
import org.main.content.InteractionLibrary;
import org.main.content.ItemLibrary;
import org.main.content.MainNpcLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.RecipeLibrary;
import org.main.content.SkillLibrary;
import org.main.content.ThemeLibrary;
import org.main.core.CharacterSkill;
import org.main.core.GameConfiguration;
import org.main.core.GearMaterial;
import org.main.core.GearDurability;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.LimbSlot;
import org.main.core.PlayerCharacter;
import org.main.core.PlayerStat;
import org.main.core.WeaponType;
import org.main.engine.AssetLoader;

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
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
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
import java.awt.Image;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

public class AetherConstructionKit extends JFrame {
    private static final int DEFAULT_WIDTH = 14;
    private static final int DEFAULT_HEIGHT = 12;
    private static final int MIN_DIMENSION = 3;
    private static final int MAX_DIMENSION = 80;
    private static final int MAX_HISTORY_STATES = 80;
    private static final int AUTOSAVE_INTERVAL_MS = 60_000;
    private static final String DEFAULT_LIMB_ICON = "assets/images/monster/Ancient/Oct-5-2010/player/hand1/misc/head.png";
    private static final Path CONFIG_RESOURCE_PATH = Path.of("src", "main", "resources", "assets", "configuration.properties");
    private static final Path AUTOSAVE_PATH = Path.of("data", "editor", "autosave", "aether_construction_kit_recovery.properties");
    private static final Path PREFAB_FOLDER = Path.of("src", "main", "resources", "assets", "editor", "prefabs");
    private static final Path SHARED_CONTENT_BACKUP_FOLDER = Path.of("src", "main", "resources", "assets", "editor", "content", "backups");
    private static final int MAX_SHARED_CONTENT_BACKUPS = 25;

    private final MapCanvas mapCanvas = new MapCanvas();
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_WIDTH, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_HEIGHT, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JComboBox<ThemeLibrary> primaryThemeBox = new JComboBox<>(ThemeLibrary.values());
    private final JComboBox<ThemeLibrary> alternateThemeBox = new JComboBox<>(ThemeLibrary.values());
    private final JComboBox<PaintMode> paintModeBox = new JComboBox<>(PaintMode.values());
    private final JComboBox<Library.TileType> tileTypeBox = new JComboBox<>(Library.TileType.values());
    private final JSpinner brushSizeSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9, 1));
    private final JSpinner zoomSpinner = new JSpinner(new SpinnerNumberModel(100, 25, 300, 25));
    private final JComboBox<MapPrefab> prefabBox = new JComboBox<>();
    private final JComboBox<PlaceableCategory> placeableCategoryBox = new JComboBox<>(PlaceableCategory.values());
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
    private boolean dirty;
    private String pendingTriggerId = "";
    private String wiringTriggerId = "";
    private String lastFindKey = "";
    private int lastFindIndex = -1;
    private Point inspectedTile;
    private MapDesignLibrary.MapPlacement inspectedPlacement;
    private MapDesignLibrary.MapTrigger inspectedTrigger;
    private Point inspectedTriggerTarget;

    private MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            ThemeLibrary.STONE_WOOD,
            ThemeLibrary.SANDSTONE_GATE
    );

    public AetherConstructionKit() {
        super("Aether Construction Kit");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(980, 720));

        loadSharedContentIntoDesign();
        loadPrefabs();
        populatePlaceables();
        alternateThemeBox.setSelectedItem(ThemeLibrary.SANDSTONE_GATE);

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

        JButton newButton = new JButton("New");
        newButton.addActionListener(event -> createNewMap());
        JButton resizeButton = new JButton("Resize");
        resizeButton.addActionListener(event -> resizeCurrentMap());
        toolbar.add(new JLabel("Name"));
        toolbar.add(mapNameField);
        JButton metadataButton = new JButton("Metadata");
        metadataButton.addActionListener(event -> editMetadata());
        toolbar.add(metadataButton);
        toolbar.add(new JLabel("W"));
        toolbar.add(widthSpinner);
        toolbar.add(new JLabel("H"));
        toolbar.add(heightSpinner);
        toolbar.add(newButton);
        toolbar.add(resizeButton);

        toolbar.addSeparator();
        toolbar.add(new JLabel("Primary"));
        toolbar.add(primaryThemeBox);
        toolbar.add(new JLabel("Alt"));
        toolbar.add(alternateThemeBox);

        toolbar.addSeparator();
        toolbar.add(new JLabel("Mode"));
        toolbar.add(paintModeBox);
        toolbar.add(new JLabel("Brush"));
        toolbar.add(brushSizeSpinner);
        toolbar.add(new JLabel("Zoom"));
        zoomSpinner.addChangeListener(event -> updateMapZoom());
        toolbar.add(zoomSpinner);
        toolbar.add(new JLabel("Prefab"));
        prefabBox.setPrototypeDisplayValue(new MapPrefab("Long Prefab Name", 1, 1, new Library.TileType[][]{{Library.TileType.FLOOR}}, new int[][]{{0}}, List.of(), List.of()));
        toolbar.add(prefabBox);
        toolbar.add(new JLabel("Tile"));
        toolbar.add(tileTypeBox);
        toolbar.add(new JLabel("Object Type"));
        placeableCategoryBox.addActionListener(event -> populatePlaceables());
        toolbar.add(placeableCategoryBox);
        toolbar.add(new JLabel("Object"));
        toolbar.add(placeableBox);

        toolbar.addSeparator();
        toolbar.add(createCreateMenuButton());
        toolbar.add(createManageMenuButton());
        toolbar.add(createToolsMenuButton());
        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(event -> showAuthoringHelp());
        toolbar.add(helpButton);

        toolbar.addSeparator();
        JButton validateButton = new JButton("Validate");
        validateButton.addActionListener(event -> validateMap());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(event -> saveMap());
        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(event -> loadMap());
        toolbar.add(validateButton);
        toolbar.add(saveButton);
        toolbar.add(loadButton);

        toolbar.addSeparator();
        undoButton.addActionListener(event -> undoMapEdit());
        redoButton.addActionListener(event -> redoMapEdit());
        toolbar.add(undoButton);
        toolbar.add(redoButton);
        updateHistoryButtons();

        return toolbar;
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

        JPanel filters = new JPanel(new BorderLayout(4, 4));
        filters.add(contentCategoryBox, BorderLayout.NORTH);
        filters.add(contentSearchField, BorderLayout.SOUTH);
        panel.add(filters, BorderLayout.NORTH);

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
                new JScrollPane(inspectorArea)
        );
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
        panel.add(buttons, BorderLayout.SOUTH);

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

    private JButton createCreateMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Dialogue NPC", this::createAuthoredDialogueNpc);
        addMenuItem(menu, "Quest", this::createAuthoredQuest);
        addMenuItem(menu, "Item", this::createCustomItem);
        addMenuItem(menu, "Enemy", this::createCustomMob);
        addMenuItem(menu, "NPC", this::createCustomNpc);
        addMenuItem(menu, "Limb", this::createCustomLimb);
        addMenuItem(menu, "Gathering Node", this::createCustomGatheringNode);
        addMenuItem(menu, "Cooking Recipe", this::createCookingRecipe);
        addMenuItem(menu, "Composite Recipe", this::createCompositeRecipe);
        addMenuItem(menu, "Map Link", this::createMapLink);
        addMenuItem(menu, "Trigger", this::createTrigger);
        addMenuItem(menu, "Prefab From Region", this::createPrefabFromRegion);
        return menuButton("Create", menu);
    }

    private JButton createManageMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Dialogue NPCs", this::manageAuthoredDialogues);
        addMenuItem(menu, "Quests", this::manageAuthoredQuests);
        addMenuItem(menu, "Items", this::manageCustomItems);
        addMenuItem(menu, "Enemies", this::manageCustomMobs);
        addMenuItem(menu, "NPCs", this::manageCustomNpcs);
        addMenuItem(menu, "Limbs", this::manageCustomLimbs);
        addMenuItem(menu, "Gathering Nodes", this::manageCustomGatheringNodes);
        addMenuItem(menu, "Cooking Recipes", this::manageCookingRecipes);
        addMenuItem(menu, "Composite Recipes", this::manageCompositeRecipes);
        addMenuItem(menu, "Abilities", this::manageAbilityConfiguration);
        addMenuItem(menu, "Level Gates", this::manageLevelGates);
        addMenuItem(menu, "Triggers", this::manageTriggers);
        addMenuItem(menu, "Prefabs", this::managePrefabs);
        return menuButton("Manage", menu);
    }

    private JButton createToolsMenuButton() {
        JPopupMenu menu = new JPopupMenu();
        addMenuItem(menu, "Asset Browser", () -> showAssetBrowser(null));
        addMenuItem(menu, "Content Backups", this::showContentBackupManager);
        addMenuItem(menu, "Sound Designer", () -> openToolWindow(new SoundDesignerTool()));
        addMenuItem(menu, "Song Designer", () -> openToolWindow(new SongDesignerTool()));
        addMenuItem(menu, "Sprite Sheet Splitter", () -> openToolWindow(new SpriteSheetSplitterTool()));
        return menuButton("Tools", menu);
    }

    private void openToolWindow(JFrame toolWindow) {
        toolWindow.setLocationRelativeTo(this);
        toolWindow.setVisible(true);
        setStatus("Opened " + toolWindow.getTitle() + ".");
    }

    private void showAssetBrowser(JTextField targetField) {
        List<AssetBrowserEntry> assets = scanEditorAssets();
        DefaultListModel<AssetBrowserEntry> assetModel = new DefaultListModel<>();
        JList<AssetBrowserEntry> assetList = new JList<>(assetModel);
        JTextField searchField = new JTextField(24);
        JComboBox<AssetBrowserType> typeBox = new JComboBox<>(AssetBrowserType.values());
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
                    .filter(asset -> selectedType == null || selectedType == AssetBrowserType.ALL || asset.type() == selectedType)
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
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected.assetPath()), null);
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

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        var dialog = pane.createDialog(this, "Asset Browser");
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
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
                new JScrollPane(detailArea)
        );
        splitPane.setResizeWeight(0.55);
        splitPane.setPreferredSize(new Dimension(780, 420));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Backups are stored in assets/editor/content/backups and are safe to commit if desired."), BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshBackups.run();

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
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
            int result = JOptionPane.showConfirmDialog(
                    dialog,
                    "Delete backup " + selected.getFileName() + "?",
                    "Delete Content Backup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
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
        dialog.setVisible(true);
    }

    private List<Path> listSharedContentBackups() {
        if (!Files.isDirectory(SHARED_CONTENT_BACKUP_FOLDER)) {
            return List.of();
        }

        try (var stream = Files.list(SHARED_CONTENT_BACKUP_FOLDER)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("authored_content_"))
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted((left, right) -> {
                        try {
                            return Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
                        } catch (IOException exception) {
                            return right.getFileName().toString().compareToIgnoreCase(left.getFileName().toString());
                        }
                    })
                    .toList();
        } catch (IOException exception) {
            setStatus("Backup scan failed: " + exception.getMessage());
            return List.of();
        }
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
            builder.append("Composite Recipes: ").append(content.customCompositeRecipes().size()).append('\n');
            return builder.toString();
        } catch (IOException exception) {
            return "Backup could not be read:\n" + exception.getMessage();
        }
    }

    private void restoreSharedContentBackup(Path backup) {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Restore " + backup.getFileName() + " as the current authored content?\n\n"
                        + "The current authored_content.properties file will be backed up first.",
                "Restore Content Backup",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
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
                contentDesign.customCompositeRecipes()
        );
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
        design.customCompositeRecipes().clear();
        design.customCompositeRecipes().addAll(content.customCompositeRecipes());
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
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif")) {
            type = AssetBrowserType.IMAGES;
        } else if (lowerName.endsWith(".wav") || lowerName.endsWith(".aiff") || lowerName.endsWith(".au")) {
            type = AssetBrowserType.SOUNDS;
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
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 3, 8, 6));
        Map<SkillLibrary, JSpinner> cooldownSpinners = new EnumMap<>(SkillLibrary.class);

        fields.add(new JLabel("Ability"));
        fields.add(new JLabel("Cooldown"));
        fields.add(new JLabel("Key"));

        for (SkillLibrary skill : SkillLibrary.values()) {
            String key = abilityCooldownKey(skill);
            double currentCooldown = GameConfiguration.doubleValue(key, 0.0);
            JSpinner cooldownSpinner = new JSpinner(new SpinnerNumberModel(currentCooldown, 0.0, 3600.0, 0.5));
            cooldownSpinners.put(skill, cooldownSpinner);

            fields.add(new JLabel(skill.getDisplayName()));
            fields.add(cooldownSpinner);
            fields.add(new JLabel(key));
        }

        JTextArea note = new JTextArea(
                "Cooldowns are saved to the packaged configuration and mirrored to the editable runtime configuration."
        );
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        panel.add(new JScrollPane(fields), BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Modify Abilities",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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
            addIntConfigRow(equipmentFields, intSpinners, material.getDisplayName(), key, PlayerCharacter.defenseRequirementFor(material), 0, 100);
        }
        tabs.addTab("Equipment", new JScrollPane(equipmentFields));

        JPanel butcheryFields = configGridPanel();
        butcheryFields.add(new JLabel("Butchery Gate"));
        butcheryFields.add(new JLabel("Required Level"));
        butcheryFields.add(new JLabel("Key"));
        addIntConfigRow(butcheryFields, intSpinners, "Legs Unlock", "butchery.targetLegsLevel", GameConfiguration.intValue("butchery.targetLegsLevel", 10), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Arms Unlock", "butchery.targetArmsLevel", GameConfiguration.intValue("butchery.targetArmsLevel", 20), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Body Unlock", "butchery.targetBodyLevel", GameConfiguration.intValue("butchery.targetBodyLevel", 30), 1, 100);
        addIntConfigRow(butcheryFields, intSpinners, "Head Unlock", "butchery.targetHeadLevel", GameConfiguration.intValue("butchery.targetHeadLevel", 40), 1, 100);
        tabs.addTab("Butchery", new JScrollPane(butcheryFields));

        JPanel resourceFields = configGridPanel();
        resourceFields.add(new JLabel("Resource Tuning"));
        resourceFields.add(new JLabel("Value"));
        resourceFields.add(new JLabel("Key"));
        addIntConfigRow(resourceFields, intSpinners, "Gathering Attempt MS", "resource.gatheringAttemptIntervalMs", GameConfiguration.intValue("resource.gatheringAttemptIntervalMs", 2500), 1, 600_000);
        addIntConfigRow(resourceFields, intSpinners, "Resource Respawn MS", "resource.respawnMs", GameConfiguration.intValue("resource.respawnMs", 300_000), 1, 3_600_000);
        addIntConfigRow(resourceFields, intSpinners, "Attempts Per Exhaustion Roll", "resource.attemptsPerExhaustionRoll", GameConfiguration.intValue("resource.attemptsPerExhaustionRoll", 2), 1, 100);
        addIntConfigRow(resourceFields, intSpinners, "Max Exhaustion Level", "resource.maxExhaustionLevel", GameConfiguration.intValue("resource.maxExhaustionLevel", 2), 0, 10);
        addDoubleConfigRow(resourceFields, doubleSpinners, "Exhaustion Chance", "resource.exhaustionChance", GameConfiguration.doubleValue("resource.exhaustionChance", 0.50), 0.0, 1.0, 0.05);
        tabs.addTab("Resources", new JScrollPane(resourceFields));

        JPanel skillFields = configGridPanel();
        skillFields.add(new JLabel("Skill Tuning"));
        skillFields.add(new JLabel("Value"));
        skillFields.add(new JLabel("Key"));
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Base Chance", "fishing.baseSuccessChance", GameConfiguration.doubleValue("fishing.baseSuccessChance", 0.35), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Chance Per Level", "fishing.successChancePerLevel", GameConfiguration.doubleValue("fishing.successChancePerLevel", 0.03), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Fishing Max Chance", "fishing.maxSuccessChance", GameConfiguration.doubleValue("fishing.maxSuccessChance", 0.85), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Fishing XP", "fishing.xpReward", GameConfiguration.intValue("fishing.xpReward", 18), 0, 10_000);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Base Chance", "mining.baseSuccessChance", GameConfiguration.doubleValue("mining.baseSuccessChance", 0.40), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Chance Per Level", "mining.successChancePerLevel", GameConfiguration.doubleValue("mining.successChancePerLevel", 0.03), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Mining Max Chance", "mining.maxSuccessChance", GameConfiguration.doubleValue("mining.maxSuccessChance", 0.88), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Mining XP", "mining.xpReward", GameConfiguration.intValue("mining.xpReward", 18), 0, 10_000);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Base Chance", "cooking.baseSuccessChance", GameConfiguration.doubleValue("cooking.baseSuccessChance", 0.45), 0.0, 1.0, 0.05);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Chance Per Level", "cooking.successChancePerLevel", GameConfiguration.doubleValue("cooking.successChancePerLevel", 0.035), 0.0, 1.0, 0.005);
        addDoubleConfigRow(skillFields, doubleSpinners, "Cooking Max Chance", "cooking.maxSuccessChance", GameConfiguration.doubleValue("cooking.maxSuccessChance", 0.90), 0.0, 1.0, 0.05);
        addIntConfigRow(skillFields, intSpinners, "Cooking XP", "cooking.xpReward", GameConfiguration.intValue("cooking.xpReward", 20), 0, 10_000);
        tabs.addTab("Skill Rates", new JScrollPane(skillFields));

        JTextArea note = new JTextArea(
                "These values are saved to the packaged configuration and mirrored to the editable runtime configuration."
                        + " Equipment gates control the Defense skill level required to wear gear by material."
        );
        note.setEditable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        panel.add(tabs, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Level Gates",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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
            int maximum
    ) {
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
            double step
    ) {
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

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private void installKeyboardShortcuts() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        bindShortcut(inputMap, actionMap, "save-map", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), true, this::saveMap);
        bindShortcut(inputMap, actionMap, "load-map", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), true, this::loadMap);
        bindShortcut(inputMap, actionMap, "validate-map", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), true, this::validateMap);
        bindShortcut(inputMap, actionMap, "undo-map", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), false, this::undoMapEdit);
        bindShortcut(inputMap, actionMap, "redo-map", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), false, this::redoMapEdit);
        bindShortcut(inputMap, actionMap, "redo-map-shift", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false, this::redoMapEdit);
        bindShortcut(inputMap, actionMap, "delete-selection", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), false, this::deleteSelectedContent);
        bindShortcut(inputMap, actionMap, "duplicate-selection", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), false, this::duplicateSelectedContent);
        bindShortcut(inputMap, actionMap, "find-selection", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), false, this::findSelectedContentOnMap);
        bindShortcut(inputMap, actionMap, "paint-selection", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), false, this::selectContentForPlacement);
    }

    private void bindShortcut(InputMap inputMap, ActionMap actionMap, String id, KeyStroke keyStroke, boolean allowWhileTyping, Runnable action) {
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
        mapNameField.setText(design.displayName());
        widthSpinner.setValue(design.width());
        heightSpinner.setValue(design.height());
        primaryThemeBox.setSelectedItem(design.primaryTheme());
        alternateThemeBox.setSelectedItem(design.alternateTheme());
        populatePlaceables();
        refreshContentBrowser();
        mapCanvas.revalidate();
        mapCanvas.repaint();
        updateHistoryButtons();
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

        int result = JOptionPane.showConfirmDialog(
                this,
                "A Construction Kit recovery file was found.\n\nLoad it now?",
                "Recover Unsaved Map",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
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
                source.primaryTheme(),
                source.alternateTheme(),
                tiles,
                themeIndexes,
                new ArrayList<>(source.placements()),
                new ArrayList<>(source.authoredDialogues()),
                new ArrayList<>(source.authoredQuests()),
                new ArrayList<>(source.customItems()),
                new ArrayList<>(source.customMobs()),
                new ArrayList<>(source.customLimbs()),
                new ArrayList<>(source.customNpcs()),
                new ArrayList<>(source.customGatheringNodes()),
                new ArrayList<>(source.customCookingRecipes()),
                new ArrayList<>(source.customCompositeRecipes()),
                new ArrayList<>(source.triggers()),
                source.spawnX(),
                source.spawnY()
        );
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

        for (GatheringNodeLibrary node : GatheringNodeLibrary.values()) {
            if (node.getInteractionId() != null && !node.getInteractionId().isBlank()) {
                addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                        "Gathering: " + node.name(),
                        MapDesignLibrary.PlacementKind.GATHERING_NODE,
                        node.name()
                ));
            }
        }

        for (CraftingNodeLibrary node : CraftingNodeLibrary.values()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Crafting: " + node.name(),
                    MapDesignLibrary.PlacementKind.CRAFTING_NODE,
                    node.name()
            ));
        }

        for (GenericNpcLibrary npc : GenericNpcLibrary.values()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Generic NPC: " + npc.name(), MapDesignLibrary.PlacementKind.GENERIC_NPC, npc.name()));
        }

        for (MainNpcLibrary npc : MainNpcLibrary.values()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Main NPC: " + npc.name(), MapDesignLibrary.PlacementKind.MAIN_NPC, npc.name()));
        }

        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Custom NPC: " + npc.displayName(), MapDesignLibrary.PlacementKind.CUSTOM_NPC, npc.npcId()));
        }

        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Gathering: " + node.displayName(),
                    MapDesignLibrary.PlacementKind.GATHERING_NODE,
                    node.nodeId()
            ));
        }

        for (ItemLibrary item : ItemLibrary.values()) {
            if (hasCustomItemId(item.name())) {
                continue;
            }
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Item: " + item.name(), MapDesignLibrary.PlacementKind.ITEM, item.name()));
        }

        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Custom Item: " + item.displayName(), MapDesignLibrary.PlacementKind.ITEM, item.itemId()));
        }

        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption("Custom Limb: " + limb.displayName(), MapDesignLibrary.PlacementKind.ITEM, limb.limbId()));
        }

        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Enemy: " + mob.displayName(),
                    MapDesignLibrary.PlacementKind.ENEMY,
                    mob.mobId()
            ));
        }

        for (InteractionLibrary interaction : InteractionLibrary.values()) {
            if (interaction.isPlaceable()) {
                addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                        "Interaction: " + interaction.getDisplayName(),
                        MapDesignLibrary.PlacementKind.INTERACTION,
                        interaction.getInteractionId()
                ));
            }
        }

        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                    "Authored NPC: " + dialogue.speakerName(),
                    MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC,
                    dialogue.interactionId()
            ));
        }

        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                    && placement.id().startsWith("map_link|")) {
                addPlaceableIfSelected(options, selectedCategory, new PlaceableOption(
                        "Map Link: " + mapLinkLabel(placement.id()),
                        MapDesignLibrary.PlacementKind.INTERACTION,
                        placement.id()
                ));
            }
        }
        return options;
    }

    private void addPlaceableIfSelected(List<PlaceableOption> options, PlaceableCategory category, PlaceableOption option) {
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
            entries.add(new ContentEntry(ContentCategory.GATHERING, node.displayName(), node.nodeId(), "Gathering Node", node));
        }
        for (MapDesignLibrary.CustomCookingRecipe recipe : design.customCookingRecipes()) {
            entries.add(new ContentEntry(ContentCategory.COOKING, recipe.displayName(), recipe.recipeId(), "Cooking Recipe", recipe));
        }
        for (MapDesignLibrary.CustomCompositeRecipe recipe : design.customCompositeRecipes()) {
            entries.add(new ContentEntry(ContentCategory.COMPOSITES, recipe.displayName(), recipe.recipeId(), "Composite Recipe", recipe));
        }
        for (MapDesignLibrary.AuthoredQuest quest : design.authoredQuests()) {
            entries.add(new ContentEntry(ContentCategory.QUESTS, quest.displayName(), quest.questId(), "Quest", quest));
        }
        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            entries.add(new ContentEntry(ContentCategory.DIALOGUES, dialogue.speakerName(), dialogue.interactionId(), "Dialogue NPC", dialogue));
        }
        for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
            entries.add(new ContentEntry(ContentCategory.TRIGGERS, trigger.id(), trigger.id(), "Trigger", trigger));
        }
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            String label = placement.kind() + " " + placement.id() + " @ " + placement.x() + "," + placement.y();
            entries.add(new ContentEntry(ContentCategory.PLACEMENTS, label, placement.id(), "Placement", placement));
        }
        for (MapDesignLibrary.ValidationIssue issue : MapDesignLibrary.validate(design)) {
            entries.add(new ContentEntry(ContentCategory.DIAGNOSTICS, issue.toString(), issue.message(), "Diagnostic", issue));
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
        inspectedTriggerTarget = null;
        mapCanvas.repaint();
    }

    private void clearMapSelection() {
        inspectedTile = null;
        inspectedPlacement = null;
        inspectedTrigger = null;
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
            DifficultyResolver.DifficultyRating rating = DifficultyResolver.rateMonsterProfile(mob.displayName(), mob.statValues(), mob.skillIds());
            builder.append("Level: ").append(rating.level()).append(" (power ")
                    .append(String.format(java.util.Locale.US, "%.2f", rating.power())).append(")\n");
            builder.append("AI: ").append(mob.combatAiIntelligence()).append('\n');
            builder.append("XP: ").append(mob.xpReward()).append('\n');
            builder.append("Skills: ").append(mob.skillIds().isEmpty() ? "None" : mob.skillIds()).append('\n');
            builder.append("Drops: ").append(mob.dropEntries().isEmpty() ? "None" : mob.dropEntries()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            builder.append("Dialogue: ").append(npc.interactionId().isBlank() ? "None" : npc.interactionId()).append('\n');
            builder.append("Sprite: ").append(npc.imagePath()).append('\n');
            builder.append("Talk Sound: ").append(npc.talkSoundPath().isBlank() ? "None" : npc.talkSoundPath()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            builder.append("Slot: ").append(limb.limbSlot()).append('\n');
            builder.append("Source: ").append(limb.sourceCreatureId().isBlank() ? "None" : limb.sourceCreatureId()).append('\n');
            builder.append("Stats: ").append(limb.statBonuses()).append('\n');
            builder.append("Skills: ").append(limb.skillIds().isEmpty() ? "None" : limb.skillIds()).append('\n');
        } else if (value instanceof MapDesignLibrary.CustomGatheringNode node) {
            builder.append("Type: ").append(node.nodeType()).append('\n');
            builder.append("Skill: ").append(node.gatheringSkill()).append(" level ").append(node.requiredLevel()).append('\n');
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
        } else if (value instanceof MapDesignLibrary.CustomCompositeRecipe recipe) {
            builder.append("Category: ").append(recipe.category()).append('\n');
            builder.append("Input: ").append(recipe.primaryItemId()).append(" + ").append(recipe.secondaryItemId()).append('\n');
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
        } else if (value instanceof MapDesignLibrary.MapTrigger trigger) {
            builder.append("Tile: ").append(trigger.x()).append(',').append(trigger.y()).append('\n');
            builder.append("One Shot: ").append(trigger.oneShot()).append('\n');
            builder.append("Actions: ").append(trigger.actions()).append('\n');
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
        JOptionPane.showMessageDialog(
                this,
                new JScrollPane(dependencyArea),
                "Dependencies: " + entry.label(),
                JOptionPane.INFORMATION_MESSAGE
        );
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
                findReferences(entry)
        );
        ContentGraphPanel graphPanel = new ContentGraphPanel(graph);
        JTextArea graphArea = new JTextArea(graphReport(entry), 24, 72);
        graphArea.setEditable(false);
        graphArea.setLineWrap(false);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(graphPanel),
                new JScrollPane(graphArea)
        );
        splitPane.setResizeWeight(0.72);
        splitPane.setPreferredSize(new Dimension(860, 640));
        JOptionPane.showMessageDialog(
                this,
                splitPane,
                "Graph: " + entry.label(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showDialogueGraph(ContentEntry entry, MapDesignLibrary.AuthoredDialogue dialogue) {
        DialogueGraphPanel graphPanel = new DialogueGraphPanel(dialogue);
        JTextArea graphArea = new JTextArea(dialogueGraphReport(dialogue), 18, 72);
        graphArea.setEditable(false);
        graphArea.setLineWrap(false);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(graphPanel),
                new JScrollPane(graphArea)
        );
        splitPane.setResizeWeight(0.74);
        splitPane.setPreferredSize(new Dimension(940, 690));

        JButton editButton = new JButton("Edit Dialogue");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                splitPane,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{editButton, closeButton},
                closeButton
        );
        var dialog = pane.createDialog(this, "Dialogue Graph: " + entry.label());

        editButton.addActionListener(event -> {
            dialog.dispose();
            editAuthoredDialogue(dialogue);
            refreshContentBrowser();
        });
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
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
            List<MapDesignLibrary.AuthoredDialogueChoice> choices
    ) {
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
            addAssetDependency(dependencies, "Use sound", item.useSoundPath());
            if (item.smithingRecipeEnabled()) {
                dependencies.add("Smithing material " + item.material() + ", bars " + item.smithingRequiredBars());
            }
        } else if (value instanceof MapDesignLibrary.CustomMob mob) {
            addAssetDependency(dependencies, "Sprite", mob.imagePath());
            addAssetDependency(dependencies, "Paper-doll source", mob.paperDollSourcePath());
            addAssetDependency(dependencies, "Attack sound", mob.attackSoundPath());
            addAssetDependency(dependencies, "Hit sound", mob.damageSoundPath());
            for (SkillLibrary skill : mob.skillIds()) {
                dependencies.add("Skill " + skill.name());
            }
            for (MapDesignLibrary.CustomDropEntry drop : mob.dropEntries()) {
                dependencies.add("Drop " + drop);
            }
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            addAssetDependency(dependencies, "Sprite", npc.imagePath());
            addAssetDependency(dependencies, "Talk sound", npc.talkSoundPath());
            if (!npc.interactionId().isBlank()) {
                dependencies.add("Dialogue " + npc.interactionId());
            }
        } else if (value instanceof MapDesignLibrary.CustomLimb limb) {
            addAssetDependency(dependencies, "Icon", limb.iconPath());
            addAssetDependency(dependencies, "Paper-doll source", limb.paperDollSourcePath());
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
        } else if (value instanceof MapDesignLibrary.CustomCompositeRecipe recipe) {
            dependencies.add("Primary item " + recipe.primaryItemId());
            dependencies.add("Secondary item " + recipe.secondaryItemId());
            dependencies.add("Output item " + recipe.outputItemId());
            dependencies.add("Skill " + recipe.requiredSkill() + " level " + recipe.requiredLevel());
            if (!recipe.smeltOutputItemId().isBlank()) {
                dependencies.add("Smelt output " + recipe.smeltOutputItemId() + " level " + recipe.smeltRequiredLevel());
            }
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            dependencies.add("Stage count " + quest.stageDescriptions().size());
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            addAssetDependency(dependencies, "Visual", dialogue.visualPath());
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

    private void collectChoiceDependencies(List<String> dependencies, List<MapDesignLibrary.AuthoredDialogueChoice> choices) {
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
                collectChoiceReferences(references, dialogue.interactionId() + "::" + node.nodeId(), node.choices(), id, label);
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
        for (MapDesignLibrary.CustomCompositeRecipe recipe : design.customCompositeRecipes()) {
            if (id.equals(recipe.primaryItemId())
                    || id.equals(recipe.secondaryItemId())
                    || id.equals(recipe.outputItemId())
                    || id.equals(recipe.smeltOutputItemId())) {
                references.add("Composite recipe " + recipe.recipeId());
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
            String label
    ) {
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
        } else if (value instanceof MapDesignLibrary.CustomCompositeRecipe recipe) {
            editCompositeRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            editAuthoredQuest(quest);
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            editAuthoredDialogue(dialogue);
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
                    item.smithingXpReward()
            ));
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
                    mob.skillIds(),
                    mob.dropEntries()
            ));
            persistSharedContent("custom enemy");
        } else if (value instanceof MapDesignLibrary.CustomNpc npc) {
            design.customNpcs().add(new MapDesignLibrary.CustomNpc(
                    nextCustomNpcId(copiedName),
                    copiedName,
                    npc.imagePath(),
                    npc.talkSoundPath(),
                    npc.interactionId()
            ));
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
                    limb.skillIds()
            ));
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
                    node.smeltRequiredLevel()
            ));
            persistSharedContent("gathering node");
        } else if (value instanceof MapDesignLibrary.CustomCookingRecipe recipe) {
            design.customCookingRecipes().add(new MapDesignLibrary.CustomCookingRecipe(
                    nextCookingRecipeId(copiedName),
                    copiedName,
                    recipe.rawItemId(),
                    recipe.cookedItemId(),
                    recipe.burntItemId(),
                    recipe.requiredLevel(),
                    recipe.xpReward()
            ));
            persistSharedContent("cooking recipe");
        } else if (value instanceof MapDesignLibrary.CustomCompositeRecipe recipe) {
            design.customCompositeRecipes().add(new MapDesignLibrary.CustomCompositeRecipe(
                    nextCompositeRecipeId(copiedName),
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
                    recipe.smeltXpReward()
            ));
            persistSharedContent("composite recipe");
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            design.authoredQuests().add(new MapDesignLibrary.AuthoredQuest(
                    nextAuthoredQuestId(copiedName),
                    copiedName,
                    quest.stageDescriptions()
            ));
            persistSharedContent("authored quest");
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            design.authoredDialogues().add(new MapDesignLibrary.AuthoredDialogue(
                    nextAuthoredInteractionId(copiedName),
                    copiedName,
                    dialogue.bodyText(),
                    dialogue.followUpInteractionId(),
                    dialogue.visualPath(),
                    "",
                    null,
                    0,
                    0,
                    dialogue.questId(),
                    dialogue.questStage(),
                    dialogue.choices(),
                    dialogue.nodes()
            ));
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
                target.y
        );
        design.placements().add(duplicated);
        markDirty(true);
        refreshContentBrowser();
        revealMapPlacement(duplicated);
        setStatus("Duplicated placement " + placement.id() + " at " + target.x + "," + target.y + ".");
    }

    private Point duplicatePlacementTarget(MapDesignLibrary.MapPlacement placement) {
        int[][] offsets = {
                {1, 0},
                {0, 1},
                {-1, 0},
                {0, -1}
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
        } else if (value instanceof MapDesignLibrary.CustomCompositeRecipe recipe) {
            deleteCompositeRecipe(recipe);
        } else if (value instanceof MapDesignLibrary.AuthoredQuest quest) {
            deleteAuthoredQuest(quest);
        } else if (value instanceof MapDesignLibrary.AuthoredDialogue dialogue) {
            deleteAuthoredDialogue(dialogue);
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
        PlaceableCategory category = switch (entry.category()) {
            case ITEMS, LIMBS -> PlaceableCategory.ITEMS;
            case ENEMIES -> PlaceableCategory.ENEMIES;
            case NPCS -> PlaceableCategory.NPCS;
            case GATHERING -> PlaceableCategory.GATHERING_NODES;
            case DIALOGUES -> PlaceableCategory.DIALOGUE_NPCS;
            case PLACEMENTS -> placementCategory(entry.value());
            case DIAGNOSTICS -> null;
            default -> null;
        };
        if (category == null) {
            setStatus(entry.type() + " cannot be painted directly.");
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
            setStatus("Found diagnostic placement target " + placement.id() + " at " + placement.x() + "," + placement.y() + ".");
            return;
        }

        MapDesignLibrary.MapTrigger trigger = diagnosticTrigger(message);
        if (trigger != null) {
            revealMapTrigger(trigger);
            setStatus("Found diagnostic trigger target " + trigger.id() + " at " + trigger.x() + "," + trigger.y() + ".");
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
        if (revealFirstMatchingContent(design.customNpcs(), ContentCategory.NPCS, npc ->
                message.startsWith("Custom NPC " + npc.npcId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customMobs(), ContentCategory.ENEMIES, mob ->
                message.startsWith("Enemy " + mob.mobId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customItems(), ContentCategory.ITEMS, item ->
                message.startsWith("Item " + item.itemId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customLimbs(), ContentCategory.LIMBS, limb ->
                message.startsWith("Limb " + limb.limbId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customGatheringNodes(), ContentCategory.GATHERING, node ->
                message.startsWith("Gathering node " + node.nodeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customCookingRecipes(), ContentCategory.COOKING, recipe ->
                message.startsWith("Cooking recipe " + recipe.recipeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.customCompositeRecipes(), ContentCategory.COMPOSITES, recipe ->
                message.startsWith("Composite recipe " + recipe.recipeId() + " "))) {
            return true;
        }
        if (revealFirstMatchingContent(design.authoredDialogues(), ContentCategory.DIALOGUES, dialogue ->
                message.startsWith("Authored dialogue " + dialogue.interactionId() + " "))) {
            return true;
        }
        return false;
    }

    private <T> boolean revealFirstMatchingContent(List<T> values, ContentCategory category, java.util.function.Predicate<T> predicate) {
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
            case INTERACTION -> placement.id().startsWith("map_link|") ? PlaceableCategory.MAP_LINKS : PlaceableCategory.INTERACTIONS;
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
                        placement.id()
                );
                optionBox.addItem(current);
                optionBox.setSelectedItem(current);
            }
        };
        categoryBox.addActionListener(event -> refreshOptions.run());
        refreshOptions.run();

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Category"));
        fields.add(categoryBox);
        fields.add(new JLabel("Object"));
        fields.add(optionBox);
        fields.add(new JLabel("X"));
        fields.add(xSpinner);
        fields.add(new JLabel("Y"));
        fields.add(ySpinner);

        int result = JOptionPane.showConfirmDialog(
                this,
                fields,
                "Edit Placement",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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
            int replaceResult = JOptionPane.showConfirmDialog(
                    this,
                    "Tile " + x + "," + y + " already has " + targetConflicts.size()
                            + " placement(s).\n\nReplace them?",
                    "Replace Placement",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
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
                y
        );
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
            String id
    ) {
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
                "New Dialogue NPC",
                "New NPC",
                "Hello there.",
                "",
                MapDesignLibrary.defaultEnemy(MapDesignLibrary.ENEMY_GOBLIN).imagePath(),
                "",
                -1,
                List.of(),
                List.of()
        );
        if (draft == null) {
            return;
        }

        MapDesignLibrary.AuthoredDialogue authoredDialogue = new MapDesignLibrary.AuthoredDialogue(
                nextAuthoredInteractionId(draft.speakerName()),
                draft.speakerName(),
                draft.bodyText(),
                draft.followUpInteractionId(),
                draft.visualPath(),
                "",
                null,
                0,
                0,
                draft.questId(),
                draft.questStage(),
                draft.choices(),
                draft.nodes()
        );
        design.authoredDialogues().add(authoredDialogue);
        persistSharedContent("authored dialogue NPC");
        placeableCategoryBox.setSelectedItem(PlaceableCategory.DIALOGUE_NPCS);
        populatePlaceables();
        placeableBox.setSelectedItem(new PlaceableOption(
                "Authored NPC: " + authoredDialogue.speakerName(),
                MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC,
                authoredDialogue.interactionId()
        ));
        setStatus("Created authored dialogue NPC " + draft.speakerName() + ".");
    }

    private void createAuthoredQuest() {
        AuthoredQuestDraft draft = showAuthoredQuestDialog(
                "New Quest",
                "New Quest",
                List.of("Begin the quest.", "Complete.")
        );
        if (draft == null) {
            return;
        }

        MapDesignLibrary.AuthoredQuest authoredQuest = new MapDesignLibrary.AuthoredQuest(
                nextAuthoredQuestId(draft.displayName()),
                draft.displayName(),
                draft.stageDescriptions()
        );
        design.authoredQuests().add(authoredQuest);
        persistSharedContent("authored quest");
        setStatus("Created authored quest " + authoredQuest.displayName() + ".");
    }

    private void manageAuthoredQuests() {
        if (design.authoredQuests().isEmpty()) {
            setStatus("No authored quests to manage.");
            return;
        }

        List<MapDesignLibrary.AuthoredQuest> sortedQuests = new ArrayList<>(design.authoredQuests());
        sortedQuests.sort(Comparator.comparing(MapDesignLibrary.AuthoredQuest::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MapDesignLibrary.AuthoredQuest::questId, String.CASE_INSENSITIVE_ORDER));
        JList<MapDesignLibrary.AuthoredQuest> questList = new JList<>(
                sortedQuests.toArray(new MapDesignLibrary.AuthoredQuest[0])
        );
        questList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        questList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.displayName() + " [" + value.questId() + "]");
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        questList.setSelectedIndex(0);

        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                new JScrollPane(questList),
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{editButton, deleteButton, closeButton},
                closeButton
        );
        var dialog = pane.createDialog(this, "Manage Quests");

        editButton.addActionListener(event -> {
            MapDesignLibrary.AuthoredQuest selected = questList.getSelectedValue();
            if (selected != null) {
                editAuthoredQuest(selected);
                dialog.dispose();
            }
        });
        deleteButton.addActionListener(event -> {
            MapDesignLibrary.AuthoredQuest selected = questList.getSelectedValue();
            if (selected != null) {
                deleteAuthoredQuest(selected);
                dialog.dispose();
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void editAuthoredQuest(MapDesignLibrary.AuthoredQuest selected) {
        AuthoredQuestDraft draft = showAuthoredQuestDialog(
                "Edit Quest",
                selected.displayName(),
                selected.stageDescriptions()
        );
        if (draft == null) {
            return;
        }

        int index = design.authoredQuests().indexOf(selected);
        if (index >= 0) {
            design.authoredQuests().set(index, new MapDesignLibrary.AuthoredQuest(
                    selected.questId(),
                    draft.displayName(),
                    draft.stageDescriptions()
            ));
            persistSharedContent("authored quest");
            setStatus("Updated authored quest " + draft.displayName() + ".");
        }
    }

    private void deleteAuthoredQuest(MapDesignLibrary.AuthoredQuest selected) {
        int result = JOptionPane.showConfirmDialog(
                this,
                deleteMessage(
                        "Delete " + selected.displayName() + "? Dialogue actions using it will be cleared.",
                        findReferences(new ContentEntry(ContentCategory.QUESTS, selected.displayName(), selected.questId(), "Quest", selected))
                ),
                "Delete Quest",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
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
                        dialogue.visualPath(),
                        "",
                        null,
                        0,
                        0,
                        "",
                        -1,
                        dialogue.choices(),
                        dialogue.nodes()
                ));
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
                28
        );
        JTextField paperDollOverlayField = new JTextField(existing == null ? "" : existing.paperDollOverlayPath(), 28);
        JTextField useSoundField = new JTextField(existing == null ? "" : existing.useSoundPath(), 28);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JButton useSoundBrowseButton = new JButton("Browse");
        JComboBox<ItemTemplateOption> templateBox = new JComboBox<>(itemTemplateOptions());
        JComboBox<InventorySystem.ItemType> typeBox = new JComboBox<>(new InventorySystem.ItemType[]{
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
        JComboBox<WeaponType> weaponTypeBox = new JComboBox<>(new WeaponType[]{
                WeaponType.DAGGER,
                WeaponType.SWORD,
                WeaponType.MACE,
                WeaponType.GREATSWORD
        });
        JLabel weaponTypeLabel = new JLabel("Weapon Type");
        JLabel twoHandedLabel = new JLabel("Hands");
        JCheckBox twoHandedBox = new JCheckBox("Two-handed", existing != null && existing.twoHanded());
        JComboBox<StatTargetOption> statTargetBox = new JComboBox<>(statTargetOptions());
        JSpinner healSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 0 : existing.healAmount(), 0, 1000, 1));
        JSpinner valueSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 10 : existing.baseGoldValue(), 1, 100000, 1));
        JCheckBox stackableBox = new JCheckBox("Stackable", existing != null && existing.stackable());
        JCheckBox smithingRecipeBox = new JCheckBox("Add smithing recipe");
        JSpinner smithingBarsSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 1 : existing.smithingRequiredBars(), 1, 100, 1));
        JSpinner smithingLevelSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 1 : existing.smithingRequiredLevel(), 1, 100, 1));
        JSpinner smithingXpSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 25 : existing.smithingXpReward(), 0, 100000, 1));
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
                ? "Uses " + RecipeLibrary.smithingMaterialNameFor(material)
                : "Metal materials only");
        };
        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollOverlayField));
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
            useSoundField.setText(item.useSoundPath());
            typeBox.setSelectedItem(item.itemType());
            materialBox.setSelectedItem(item.material());
            weaponTypeBox.setSelectedItem(item.weaponType() == WeaponType.NONE ? WeaponType.SWORD : item.weaponType());
            twoHandedBox.setSelected(item.twoHanded());
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
        updateSmithingRecipeControls.run();

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        if (existing == null) {
            fields.add(formRow("Template", templateBox));
        }
        JPanel imagePanel = new JPanel(new BorderLayout(4, 4));
        imagePanel.add(iconPathField, BorderLayout.CENTER);
        imagePanel.add(browseButton, BorderLayout.EAST);
        JPanel paperDollPanel = new JPanel(new BorderLayout(4, 4));
        paperDollPanel.add(paperDollOverlayField, BorderLayout.CENTER);
        paperDollPanel.add(paperDollBrowseButton, BorderLayout.EAST);
        JPanel nameRow = formRow("Name", nameField);
        JPanel imageRow = formRow("Image", imagePanel);
        JPanel paperDollRow = formRow("Paper Doll Sprite", paperDollPanel);
        JPanel useSoundRow = formRow("Use Sound", pathFieldPanel(useSoundField, useSoundBrowseButton));
        JPanel typeRow = formRow("Type", typeBox);
        JPanel materialRow = formRow("Material", materialBox);
        JPanel weaponTypeRow = formRow("Weapon Type", weaponTypeBox);
        JPanel twoHandedRow = formRow("Hands", twoHandedBox);
        JPanel ringStatRow = formRow("Ring Stat", statTargetBox);
        JPanel healRow = formRow("HP Restore", healSpinner);
        JPanel valueRow = formRow("Base Value", valueSpinner);
        JPanel stackableRow = formRow("Stackable", stackableBox);
        JPanel smithingRecipeRow = formRow("Smithing Recipe", smithingRecipeBox);
        JPanel smithingMaterialRow = formRow("Recipe Material", smithingMaterialLabel);
        JPanel smithingBarsRow = formRow("Bars Required", smithingBarsSpinner);
        JPanel smithingLevelRow = formRow("Smithing Level", smithingLevelSpinner);
        JPanel smithingXpRow = formRow("Smithing XP", smithingXpSpinner);
        fields.add(nameRow);
        fields.add(imageRow);
        fields.add(paperDollRow);
        fields.add(useSoundRow);
        fields.add(typeRow);
        fields.add(materialRow);
        fields.add(weaponTypeRow);
        fields.add(twoHandedRow);
        fields.add(ringStatRow);
        fields.add(healRow);
        fields.add(valueRow);
        fields.add(stackableRow);
        fields.add(smithingRecipeRow);
        fields.add(smithingMaterialRow);
        fields.add(smithingBarsRow);
        fields.add(smithingLevelRow);
        fields.add(smithingXpRow);
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
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            boolean metal = material != null && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            boolean smithingEnabled = metal && smithingRecipeBox.isSelected();

            if (!weapon) {
                weaponTypeBox.setSelectedItem(WeaponType.SWORD);
                twoHandedBox.setSelected(false);
            }
            paperDollRow.setVisible(paperDollAllowed);
            weaponTypeRow.setVisible(weapon);
            twoHandedRow.setVisible(weapon);
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
            fields.revalidate();
            fields.repaint();
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
        updateItemTypeRows.run();
        panel.add(fields, BorderLayout.NORTH);
        panel.add(new JScrollPane(examineArea), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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
        GearMaterial selectedMaterial = (GearMaterial) materialBox.getSelectedItem();
        boolean selectedMetal = selectedMaterial != null && selectedMaterial.getFamily() == GearMaterial.MaterialFamily.METAL;
        try {
            iconPath = normalizeCustomItemImagePath(iconPathField.getText(), name);
            paperDollOverlayPath = selectedPaperDollAllowed
                    ? normalizeOptionalCustomItemImagePath(paperDollOverlayField.getText(), name + "_paper_doll")
                    : "";
        } catch (Exception exception) {
            setStatus("Item image failed: " + exception.getMessage());
            return null;
        }

        return new MapDesignLibrary.CustomItem(
                existing == null ? nextCustomItemId(name) : existing.itemId(),
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
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingBarsSpinner.getValue()).intValue() : 1,
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingLevelSpinner.getValue()).intValue() : 1,
                selectedMetal && smithingRecipeBox.isSelected() ? ((Number) smithingXpSpinner.getValue()).intValue() : 0
        );
    }

    private void createCustomMob() {
        JTextField nameField = new JTextField("Custom Enemy", 24);
        JTextField imagePathField = new JTextField("assets/images/generated/mobs/custom_enemy.png", 28);
        JTextField paperDollSourceField = new JTextField("", 28);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        Map<PlayerStat, JSpinner> statSpinners = enemyStatSpinners();
        JLabel hpLabel = new JLabel();
        JSpinner combatAiSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 100000, 1));
        JTextField attackSoundField = new JTextField("", 24);
        JTextField damageSoundField = new JTextField("", 24);
        JButton attackSoundBrowseButton = new JButton("Browse");
        JButton damageSoundBrowseButton = new JButton("Browse");
        JButton generateLimbsButton = new JButton("Generate Limbs");
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
        String[] generatedMobId = {""};

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
                    ((Number) spellBaseDamageSpinner.getValue()).intValue()
            ));
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
                    skillList.getSelectedValuesList()
            ));
            editGeneratedLimbs(generatedLimbs);
        });

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Sprite PNG"));
        fields.add(pathFieldPanel(imagePathField, browseButton));
        fields.add(new JLabel("Paper-Doll Source"));
        fields.add(pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        for (PlayerStat stat : PlayerStat.values()) {
            fields.add(new JLabel(stat.getDisplayName()));
            fields.add(statSpinners.get(stat));
        }
        fields.add(new JLabel("Derived HP"));
        fields.add(hpLabel);
        fields.add(new JLabel("Difficulty"));
        fields.add(difficultyPreviewLabel);
        fields.add(new JLabel("Melee Max Hit"));
        fields.add(meleeMaxDamageLabel);
        fields.add(new JLabel("Spell Base Damage"));
        fields.add(spellBaseDamageSpinner);
        fields.add(new JLabel("Spell Max Hit"));
        fields.add(spellMaxDamageLabel);
        fields.add(new JLabel("Combat AI Intelligence"));
        fields.add(combatAiSpinner);
        fields.add(new JLabel("XP Reward"));
        fields.add(xpSpinner);
        fields.add(new JLabel("Attack Sound"));
        fields.add(pathFieldPanel(attackSoundField, attackSoundBrowseButton));
        fields.add(new JLabel("Hit Sound"));
        fields.add(pathFieldPanel(damageSoundField, damageSoundBrowseButton));
        fields.add(new JLabel("Limbs"));
        fields.add(generateLimbsButton);
        fields.add(new JLabel("Loot Table"));
        fields.add(dropsButton);
        panel.add(fields, BorderLayout.NORTH);
        JPanel textPanel = new JPanel(new BorderLayout(6, 6));
        textPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        textPanel.add(new JScrollPane(limbDescriptionArea), BorderLayout.SOUTH);
        panel.add(textPanel, BorderLayout.CENTER);
        panel.add(new JScrollPane(skillList), BorderLayout.EAST);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Enemy", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom enemy needs a name.");
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
                skillList.getSelectedValuesList(),
                dropEntries
        );
        design.customMobs().add(mob);
        for (MapDesignLibrary.CustomLimb limb : generatedLimbs) {
            if (!hasCustomLimbId(limb.limbId())) {
                design.customLimbs().add(limb);
            }
        }
        persistSharedContent("custom enemy");
        populatePlaceables();
        setStatus("Created custom enemy " + mob.displayName() + (generatedLimbs.isEmpty() ? "." : " with " + generatedLimbs.size() + " limbs."));
    }

    private String customEnemyDifficultyPreview(
            Map<PlayerStat, Integer> statValues,
            List<SkillLibrary> skills
    ) {
        DifficultyResolver.DifficultyRating rating = DifficultyResolver.rateMonsterProfile(
                "Preview Enemy",
                statValues,
                skills
        );
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
        MapDesignLibrary.CustomNpc npc = showCustomNpcDialog("Create NPC", null);
        if (npc == null) {
            return;
        }

        design.customNpcs().add(npc);
        persistSharedContent("custom NPC");
        populatePlaceables();
        setStatus("Created custom NPC " + npc.displayName() + ".");
    }

    private MapDesignLibrary.CustomNpc showCustomNpcDialog(String title, MapDesignLibrary.CustomNpc existing) {
        JTextField nameField = new JTextField(existing == null ? "Custom NPC" : existing.displayName(), 24);
        JTextField imagePathField = new JTextField(
                existing == null ? "assets/images/generated/npcs/custom_npc.png" : existing.imagePath(),
                28
        );
        JTextField talkSoundField = new JTextField(existing == null ? "" : existing.talkSoundPath(), 24);
        JButton imageBrowseButton = new JButton("Browse");
        JButton talkSoundBrowseButton = new JButton("Browse");
        JComboBox<DialogueOption> dialogueBox = new JComboBox<>(dialogueOptions());
        if (existing != null) {
            selectDialogueOption(dialogueBox, existing.interactionId());
        }

        imageBrowseButton.addActionListener(event -> browsePathInto(imagePathField));
        talkSoundBrowseButton.addActionListener(event -> browsePathInto(talkSoundField));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Sprite PNG"));
        fields.add(pathFieldPanel(imagePathField, imageBrowseButton));
        fields.add(new JLabel("Talk Sound"));
        fields.add(pathFieldPanel(talkSoundField, talkSoundBrowseButton));
        fields.add(new JLabel("Dialogue"));
        fields.add(dialogueBox);
        panel.add(fields, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom NPC needs a name.");
            return null;
        }

        DialogueOption dialogueOption = (DialogueOption) dialogueBox.getSelectedItem();
        return new MapDesignLibrary.CustomNpc(
                existing == null ? nextCustomNpcId(name) : existing.npcId(),
                name,
                imagePathField.getText() == null ? "" : imagePathField.getText().trim(),
                talkSoundField.getText() == null ? "" : talkSoundField.getText().trim(),
                dialogueOption == null ? "" : dialogueOption.interactionId()
        );
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
                List.of()
        );
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
        JComboBox<MapDesignLibrary.GatheringNodeType> typeBox = new JComboBox<>(MapDesignLibrary.GatheringNodeType.values());
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
            frameThreeRow.setVisible(!fishing);
            autoMaterialOutputBox.setText(tree
                    ? "Auto-create wood logs from stage 0 image"
                    : "Auto-create metal ore from stage 0 image");
            setFormRowLabel(materialHelperRow, tree ? "Wood Helper" : "Metal Helper");
            setFormRowLabel(materialRow, tree ? "Wood" : "Metal");
            if (!mining) {
                smeltingBox.setSelected(false);
            }
            frameDurationSpinner.setValue(fishing ? GatheringNodeLibrary.FISHING_SHOAL.getFrameDurationMs() : 1000);
            visualScaleSpinner.setValue(fishing ? 1.0 : 1.35);
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
        smeltingBox.addActionListener(event -> updateGatheringNodeFields.run());
        updateGatheringNodeFields.run();

        if (JOptionPane.showConfirmDialog(this, fields, "Create Gathering Node", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Gathering node needs a name.");
            return;
        }

        try {
            MapDesignLibrary.GatheringNodeType nodeType = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            boolean fishing = nodeType == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = nodeType == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            boolean tree = nodeType == MapDesignLibrary.GatheringNodeType.TREE;
            List<String> frames = fishing
                    ? defaultFishingFramePaths()
                    : List.of(
                            normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                            normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                            normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering")
                    );
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
                                    ? "Fresh " + materialName.toLowerCase(java.util.Locale.ROOT) + " logs. Useful for future crafting."
                                    : "Raw " + materialName.toLowerCase(java.util.Locale.ROOT) + " ore. Smelt it into a bar at a furnace.",
                            null,
                            false,
                            false,
                            1,
                            1,
                            0
                    ));
                }
                lootEntries.clear();
                lootEntries.add(new MapDesignLibrary.CustomDropEntry(outputItemId, 1.0));

                if (mining && smeltingBox.isSelected()) {
                    String barName = RecipeLibrary.smithingMaterialNameFor(material);
                    if (barName.isBlank()) {
                        barName = materialName + " Bar";
                    }
                    smeltOutputItemId = findItemIdByDisplayName(barName);
                    if (smeltOutputItemId.isBlank()) {
                        smeltOutputItemId = nextCustomItemId(barName);
                        String barImage = normalizeGeneratedImagePath(barImageField.getText(), safeId(barName), "items");
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
                                0
                        ));
                    }
                }
            }

            if (lootEntries.isEmpty()) {
                setStatus("Gathering node needs at least one loot entry or an auto metal output.");
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
                    fishing ? GatheringNodeLibrary.FISHING_SHOAL.getFrameDurationMs() : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1
            );
            design.customGatheringNodes().add(node);
            persistSharedContent("gathering node");
            populatePlaceables();
            setStatus("Created gathering node " + node.displayName() + ".");
        } catch (IOException exception) {
            setStatus("Gathering node image save failed: " + exception.getMessage());
        }
    }

    private void createCompositeRecipe() {
        JTextField nameField = new JTextField("Composite Recipe", 24);
        JComboBox<MapDesignLibrary.CompositeRecipeCategory> categoryBox =
                new JComboBox<>(MapDesignLibrary.CompositeRecipeCategory.values());
        JComboBox<DropItemOption> primaryBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> secondaryBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> outputBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
        JCheckBox consumePrimaryBox = new JCheckBox("Consume primary", true);
        JCheckBox consumeSecondaryBox = new JCheckBox("Consume secondary", true);
        JCheckBox smeltingBox = new JCheckBox("Output can be smelted");
        JComboBox<DropItemOption> smeltOutputBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
        skillBox.setSelectedItem(CharacterSkill.SMITHING);

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        JPanel smeltOutputRow = formRow("Smelt Output", smeltOutputBox);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Category", categoryBox));
        fields.add(formRow("Primary Item", primaryBox));
        fields.add(formRow("Secondary Item", secondaryBox));
        fields.add(formRow("Output Item", outputBox));
        fields.add(formRow("Skill", skillBox));
        fields.add(formRow("Required Level", requiredLevelSpinner));
        fields.add(formRow("XP Reward", xpSpinner));
        fields.add(formRow("Primary", consumePrimaryBox));
        fields.add(formRow("Secondary", consumeSecondaryBox));
        fields.add(formRow("Smelting", smeltingBox));
        fields.add(smeltOutputRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        Runnable updateCompositeSmeltingRows = () -> {
            boolean smelting = smeltingBox.isSelected();
            smeltOutputRow.setVisible(smelting);
            smeltingLevelRow.setVisible(smelting);
            smeltingXpRow.setVisible(smelting);
            fields.revalidate();
            fields.repaint();
        };
        smeltingBox.addActionListener(event -> updateCompositeSmeltingRows.run());
        updateCompositeSmeltingRows.run();

        if (JOptionPane.showConfirmDialog(this, fields, "Create Composite Recipe", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        DropItemOption primary = (DropItemOption) primaryBox.getSelectedItem();
        DropItemOption secondary = (DropItemOption) secondaryBox.getSelectedItem();
        DropItemOption output = (DropItemOption) outputBox.getSelectedItem();
        if (name.isBlank() || primary == null || secondary == null || output == null) {
            setStatus("Composite recipe needs a name, two ingredients, and an output.");
            return;
        }

        MapDesignLibrary.CustomCompositeRecipe recipe = new MapDesignLibrary.CustomCompositeRecipe(
                nextCompositeRecipeId(name),
                name,
                (MapDesignLibrary.CompositeRecipeCategory) categoryBox.getSelectedItem(),
                primary.itemId(),
                secondary.itemId(),
                output.itemId(),
                (CharacterSkill) skillBox.getSelectedItem(),
                ((Number) requiredLevelSpinner.getValue()).intValue(),
                ((Number) xpSpinner.getValue()).intValue(),
                consumePrimaryBox.isSelected(),
                consumeSecondaryBox.isSelected(),
                smeltingBox.isSelected() && smeltOutputBox.getSelectedItem() instanceof DropItemOption smeltOutput
                        ? smeltOutput.itemId()
                        : "",
                smeltingBox.isSelected() ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1,
                smeltingBox.isSelected() ? ((Number) smeltingXpSpinner.getValue()).intValue() : 0
        );
        design.customCompositeRecipes().add(recipe);
        persistSharedContent("composite recipe");
        setStatus("Created composite recipe " + recipe.displayName() + ".");
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
            MapDesignLibrary.CustomCookingRecipe existing
    ) {
        JComboBox<DropItemOption> rawBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> cookedBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> burntBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JTextField nameField = new JTextField(existing == null ? "Cooking Recipe" : existing.displayName(), 24);
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 1 : existing.requiredLevel(), 1, 100, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 20 : existing.xpReward(), 0, 100000, 1));
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

        int result = JOptionPane.showConfirmDialog(this, fields, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
                ((Number) xpSpinner.getValue()).intValue()
        );
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
                0
        ));
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
                "A blackened version of " + raw.displayName().toLowerCase(java.util.Locale.ROOT) + ". It is not worth eating.",
                null,
                false,
                false,
                1,
                1,
                0
        ));
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

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Metal"));
        fields.add(materialBox);
        fields.add(new JLabel("Mining Level"));
        fields.add(requiredLevelSpinner);
        fields.add(new JLabel("Mining XP"));
        fields.add(miningXpSpinner);
        fields.add(new JLabel("Smelting XP"));
        fields.add(smeltingXpSpinner);
        fields.add(new JLabel("Rock Stage 0 / Ore Icon"));
        fields.add(pathFieldPanel(frameOneField, frameOneBrowse));
        fields.add(new JLabel("Rock Stage 1"));
        fields.add(pathFieldPanel(frameTwoField, frameTwoBrowse));
        fields.add(new JLabel("Rock Stage 2"));
        fields.add(pathFieldPanel(frameThreeField, frameThreeBrowse));
        fields.add(new JLabel("Bar Icon"));
        fields.add(pathFieldPanel(barImageField, barBrowse));

        if (JOptionPane.showConfirmDialog(this, fields, "Create Mining Rock", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
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
        String barName = RecipeLibrary.smithingMaterialNameFor(material);
        if (barName.isBlank()) {
            barName = metalName + " Bar";
        }

        try {
            List<String> frames = List.of(
                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering")
            );
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
                        "Raw " + metalName.toLowerCase(java.util.Locale.ROOT) + " ore. Smelt it into a bar at a furnace.",
                        null,
                        false,
                        false,
                        1,
                        1,
                        0
                ));
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
                        0
                ));
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
                    1.35
            );
            design.customGatheringNodes().add(node);
            persistSharedContent("mining rock");
            populatePlaceables();
            setStatus("Created mining rock " + node.displayName() + " and generated " + oreName + " / " + barName + ".");
        } catch (IOException exception) {
            setStatus("Mining rock image save failed: " + exception.getMessage());
        }
    }

    private void createCustomFishingSpot() {
        JTextField nameField = new JTextField("Fishing Spot", 24);
        JComboBox<DropItemOption> outputBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JSpinner fishingXpSpinner = new JSpinner(new SpinnerNumberModel(18, 0, 100000, 1));
        JTextField frameOneField = new JTextField("assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance1.png", 28);
        JTextField frameTwoField = new JTextField("assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance2.png", 28);
        JTextField frameThreeField = new JTextField("assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water_disturbance3.png", 28);
        JButton frameOneBrowse = new JButton("Browse");
        JButton frameTwoBrowse = new JButton("Browse");
        JButton frameThreeBrowse = new JButton("Browse");
        frameOneBrowse.addActionListener(event -> browsePathInto(frameOneField));
        frameTwoBrowse.addActionListener(event -> browsePathInto(frameTwoField));
        frameThreeBrowse.addActionListener(event -> browsePathInto(frameThreeField));

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Output Item"));
        fields.add(outputBox);
        fields.add(new JLabel("Fishing Level"));
        fields.add(requiredLevelSpinner);
        fields.add(new JLabel("Fishing XP"));
        fields.add(fishingXpSpinner);
        fields.add(new JLabel("Frame 1"));
        fields.add(pathFieldPanel(frameOneField, frameOneBrowse));
        fields.add(new JLabel("Frame 2"));
        fields.add(pathFieldPanel(frameTwoField, frameTwoBrowse));
        fields.add(new JLabel("Frame 3"));
        fields.add(pathFieldPanel(frameThreeField, frameThreeBrowse));

        if (JOptionPane.showConfirmDialog(this, fields, "Create Fishing Spot", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
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
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_frame_3", "gathering")
            );
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
                    1.0
            );
            design.customGatheringNodes().add(node);
            persistSharedContent("fishing spot");
            populatePlaceables();
            setStatus("Created fishing spot " + node.displayName() + ".");
        } catch (IOException exception) {
            setStatus("Fishing spot image save failed: " + exception.getMessage());
        }
    }

    private void manageCustomItems() {
        manageCustomContent(
                "Items",
                design.customItems(),
                item -> item.displayName() + " [" + item.itemId() + "]",
                Comparator.comparing(MapDesignLibrary.CustomItem::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomItem::itemId, String.CASE_INSENSITIVE_ORDER),
                this::editCustomItem,
                this::deleteCustomItem
        );
    }

    private void manageCustomMobs() {
        manageCustomContent(
                "Enemies",
                design.customMobs(),
                mob -> mob.displayName() + " [" + mob.mobId() + "]",
                Comparator.comparing(MapDesignLibrary.CustomMob::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomMob::mobId, String.CASE_INSENSITIVE_ORDER),
                this::editCustomMob,
                this::deleteCustomMob
        );
    }

    private void manageCustomNpcs() {
        manageCustomContent(
                "NPCs",
                design.customNpcs(),
                npc -> npc.displayName() + " [" + npc.npcId() + "]",
                Comparator.comparing(MapDesignLibrary.CustomNpc::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomNpc::npcId, String.CASE_INSENSITIVE_ORDER),
                this::editCustomNpc,
                this::deleteCustomNpc
        );
    }

    private void manageCustomLimbs() {
        manageCustomContent(
                "Limbs",
                design.customLimbs(),
                limb -> limb.displayName() + " [" + limb.limbId() + "]",
                Comparator.comparing(MapDesignLibrary.CustomLimb::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomLimb::limbId, String.CASE_INSENSITIVE_ORDER),
                this::editCustomLimb,
                this::deleteCustomLimb
        );
    }

    private void manageCustomGatheringNodes() {
        manageCustomContent(
                "Gathering Nodes",
                design.customGatheringNodes(),
                node -> node.displayName() + " [" + node.nodeId() + ", " + node.nodeType() + "]",
                Comparator.comparing(MapDesignLibrary.CustomGatheringNode::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomGatheringNode::nodeId, String.CASE_INSENSITIVE_ORDER),
                this::editCustomGatheringNode,
                this::deleteCustomGatheringNode
        );
    }

    private void manageCompositeRecipes() {
        manageCustomContent(
                "Composite Recipes",
                design.customCompositeRecipes(),
                recipe -> recipe.displayName() + " [" + recipe.recipeId() + ", " + recipe.category() + "]",
                Comparator.comparing(MapDesignLibrary.CustomCompositeRecipe::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomCompositeRecipe::recipeId, String.CASE_INSENSITIVE_ORDER),
                this::editCompositeRecipe,
                this::deleteCompositeRecipe
        );
    }

    private void manageCookingRecipes() {
        manageCustomContent(
                "Cooking Recipes",
                design.customCookingRecipes(),
                recipe -> recipe.displayName() + " [" + recipe.recipeId() + "]",
                Comparator.comparing(MapDesignLibrary.CustomCookingRecipe::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MapDesignLibrary.CustomCookingRecipe::recipeId, String.CASE_INSENSITIVE_ORDER),
                this::editCookingRecipe,
                this::deleteCookingRecipe
        );
    }

    private <T> void manageCustomContent(
            String title,
            List<T> entries,
            Function<T, String> labeler,
            Consumer<T> editAction,
            Consumer<T> deleteAction
    ) {
        manageCustomContent(title, entries, labeler, null, editAction, deleteAction);
    }

    private <T> void manageCustomContent(
            String title,
            List<T> entries,
            Function<T, String> labeler,
            Comparator<T> displayComparator,
            Consumer<T> editAction,
            Consumer<T> deleteAction
    ) {
        if (entries.isEmpty()) {
            setStatus("No " + title.toLowerCase() + " to manage.");
            return;
        }

        DefaultListModel<T> model = new DefaultListModel<>();
        List<T> displayEntries = new ArrayList<>(entries);
        if (displayComparator != null) {
            displayEntries.sort(displayComparator);
        }
        for (T entry : displayEntries) {
            model.addElement(entry);
        }
        JList<T> entryList = new JList<>(model);
        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(labeler.apply(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        entryList.setSelectedIndex(0);

        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                new JScrollPane(entryList),
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{editButton, deleteButton, closeButton},
                closeButton
        );
        var dialog = pane.createDialog(this, "Manage " + title);

        editButton.addActionListener(event -> {
            T selected = entryList.getSelectedValue();
            if (selected != null) {
                editAction.accept(selected);
                dialog.dispose();
            }
        });
        deleteButton.addActionListener(event -> {
            T selected = entryList.getSelectedValue();
            if (selected != null) {
                deleteAction.accept(selected);
                dialog.dispose();
            }
        });
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
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
                selected.skillIds()
        );
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
        if (!confirmDelete("gathering node", selected.displayName(), ContentCategory.GATHERING, selected.nodeId(), selected)) {
            return;
        }

        design.customGatheringNodes().remove(selected);
        design.placements().removeIf(placement ->
                placement.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE
                        && (selected.nodeId().equals(placement.id()) || selected.interactionId().equals(placement.id())));
        persistSharedContent("gathering node");
        populatePlaceables();
        setStatus("Deleted gathering node " + selected.displayName() + ".");
    }

    private void editCustomGatheringNode(MapDesignLibrary.CustomGatheringNode selected) {
        JTextField nameField = new JTextField(selected.displayName(), 24);
        JComboBox<MapDesignLibrary.GatheringNodeType> typeBox = new JComboBox<>(MapDesignLibrary.GatheringNodeType.values());
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.requiredLevel(), 1, 100, 1));
        JSpinner gatherXpSpinner = new JSpinner(new SpinnerNumberModel(selected.gatherXpReward(), 0, 100000, 1));
        JSpinner frameDurationSpinner = new JSpinner(new SpinnerNumberModel(selected.frameDurationMs(), 1, 100000, 1));
        JSpinner visualScaleSpinner = new JSpinner(new SpinnerNumberModel(selected.visualScale(), 0.1, 10.0, 0.05));
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltRequiredLevel(), 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltXpReward(), 0, 100000, 1));
        JTextField smeltOutputField = new JTextField(selected.smeltOutputItemId(), 28);
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
        JPanel smeltOutputRow = formRow("Smelt Output Item Id", smeltOutputField);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        JPanel frameOneRow = formRow("Stage / Frame 0", pathFieldPanel(frameOneField, frameOneBrowse));
        JPanel frameTwoRow = formRow("Stage / Frame 1", pathFieldPanel(frameTwoField, frameTwoBrowse));
        JPanel frameThreeRow = formRow("Stage / Frame 2", pathFieldPanel(frameThreeField, frameThreeBrowse));
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
            frameDurationRow.setVisible(false);
            smeltOutputRow.setVisible(mining);
            smeltingLevelRow.setVisible(mining);
            smeltingXpRow.setVisible(mining);
            frameOneRow.setVisible(!fishing);
            frameTwoRow.setVisible(!fishing);
            frameThreeRow.setVisible(!fishing);
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
        updateGatheringNodeFields.run();

        if (JOptionPane.showConfirmDialog(this, fields, "Edit Gathering Node", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
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
            MapDesignLibrary.GatheringNodeType nodeType = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            boolean fishing = nodeType == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
            boolean mining = nodeType == MapDesignLibrary.GatheringNodeType.MINING_ROCK;
            List<String> frames = fishing
                    ? defaultFishingFramePaths()
                    : List.of(
                            normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                            normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                            normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering")
                    );
            MapDesignLibrary.CustomGatheringNode edited = new MapDesignLibrary.CustomGatheringNode(
                    selected.nodeId(),
                    name,
                    nodeType,
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    lootEntries.get(0).itemId(),
                    ((Number) gatherXpSpinner.getValue()).intValue(),
                    mining && smeltOutputField.getText() != null ? smeltOutputField.getText().trim() : "",
                    mining ? ((Number) smeltingXpSpinner.getValue()).intValue() : 0,
                    frames,
                    fishing ? GatheringNodeLibrary.FISHING_SHOAL.getFrameDurationMs() : ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    mining ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1
            );
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

    private void deleteCompositeRecipe(MapDesignLibrary.CustomCompositeRecipe selected) {
        if (!confirmDelete("composite recipe", selected.displayName(), ContentCategory.COMPOSITES, selected.recipeId(), selected)) {
            return;
        }

        design.customCompositeRecipes().remove(selected);
        persistSharedContent("composite recipe");
        setStatus("Deleted composite recipe " + selected.displayName() + ".");
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
        if (!confirmDelete("cooking recipe", selected.displayName(), ContentCategory.COOKING, selected.recipeId(), selected)) {
            return;
        }

        design.customCookingRecipes().remove(selected);
        persistSharedContent("cooking recipe");
        setStatus("Deleted cooking recipe " + selected.displayName() + ".");
    }

    private void editCompositeRecipe(MapDesignLibrary.CustomCompositeRecipe selected) {
        JTextField nameField = new JTextField(selected.displayName(), 24);
        JComboBox<MapDesignLibrary.CompositeRecipeCategory> categoryBox =
                new JComboBox<>(MapDesignLibrary.CompositeRecipeCategory.values());
        JComboBox<DropItemOption> primaryBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> secondaryBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<DropItemOption> outputBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JComboBox<CharacterSkill> skillBox = new JComboBox<>(CharacterSkill.values());
        JSpinner requiredLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.requiredLevel(), 1, 100, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(selected.xpReward(), 0, 100000, 1));
        JCheckBox consumePrimaryBox = new JCheckBox("Consume primary", selected.consumePrimary());
        JCheckBox consumeSecondaryBox = new JCheckBox("Consume secondary", selected.consumeSecondary());
        JCheckBox smeltingBox = new JCheckBox("Output can be smelted", !selected.smeltOutputItemId().isBlank());
        JComboBox<DropItemOption> smeltOutputBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner smeltingLevelSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltRequiredLevel(), 1, 100, 1));
        JSpinner smeltingXpSpinner = new JSpinner(new SpinnerNumberModel(selected.smeltXpReward(), 0, 100000, 1));

        categoryBox.setSelectedItem(selected.category());
        skillBox.setSelectedItem(selected.requiredSkill());
        selectDropItem(primaryBox, selected.primaryItemId());
        selectDropItem(secondaryBox, selected.secondaryItemId());
        selectDropItem(outputBox, selected.outputItemId());
        selectDropItem(smeltOutputBox, selected.smeltOutputItemId());

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        JPanel smeltOutputRow = formRow("Smelt Output", smeltOutputBox);
        JPanel smeltingLevelRow = formRow("Smelting Level", smeltingLevelSpinner);
        JPanel smeltingXpRow = formRow("Smelting XP", smeltingXpSpinner);
        fields.add(formRow("Name", nameField));
        fields.add(formRow("Category", categoryBox));
        fields.add(formRow("Primary Item", primaryBox));
        fields.add(formRow("Secondary Item", secondaryBox));
        fields.add(formRow("Output Item", outputBox));
        fields.add(formRow("Skill", skillBox));
        fields.add(formRow("Required Level", requiredLevelSpinner));
        fields.add(formRow("XP Reward", xpSpinner));
        fields.add(formRow("Primary", consumePrimaryBox));
        fields.add(formRow("Secondary", consumeSecondaryBox));
        fields.add(formRow("Smelting", smeltingBox));
        fields.add(smeltOutputRow);
        fields.add(smeltingLevelRow);
        fields.add(smeltingXpRow);
        Runnable updateCompositeSmeltingRows = () -> {
            boolean smelting = smeltingBox.isSelected();
            smeltOutputRow.setVisible(smelting);
            smeltingLevelRow.setVisible(smelting);
            smeltingXpRow.setVisible(smelting);
            fields.revalidate();
            fields.repaint();
        };
        smeltingBox.addActionListener(event -> updateCompositeSmeltingRows.run());
        updateCompositeSmeltingRows.run();

        if (JOptionPane.showConfirmDialog(this, fields, "Edit Composite Recipe", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        DropItemOption primary = (DropItemOption) primaryBox.getSelectedItem();
        DropItemOption secondary = (DropItemOption) secondaryBox.getSelectedItem();
        DropItemOption output = (DropItemOption) outputBox.getSelectedItem();
        if (name.isBlank() || primary == null || secondary == null || output == null) {
            setStatus("Composite recipe needs a name, two ingredients, and an output.");
            return;
        }

        MapDesignLibrary.CustomCompositeRecipe edited = new MapDesignLibrary.CustomCompositeRecipe(
                selected.recipeId(),
                name,
                (MapDesignLibrary.CompositeRecipeCategory) categoryBox.getSelectedItem(),
                primary.itemId(),
                secondary.itemId(),
                output.itemId(),
                (CharacterSkill) skillBox.getSelectedItem(),
                ((Number) requiredLevelSpinner.getValue()).intValue(),
                ((Number) xpSpinner.getValue()).intValue(),
                consumePrimaryBox.isSelected(),
                consumeSecondaryBox.isSelected(),
                smeltingBox.isSelected() && smeltOutputBox.getSelectedItem() instanceof DropItemOption smeltOutput
                        ? smeltOutput.itemId()
                        : "",
                smeltingBox.isSelected() ? ((Number) smeltingLevelSpinner.getValue()).intValue() : 1,
                smeltingBox.isSelected() ? ((Number) smeltingXpSpinner.getValue()).intValue() : 0
        );
        int index = design.customCompositeRecipes().indexOf(selected);
        if (index >= 0) {
            design.customCompositeRecipes().set(index, edited);
            persistSharedContent("composite recipe");
            setStatus("Updated composite recipe " + edited.displayName() + ".");
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
        JSpinner combatAiSpinner = new JSpinner(new SpinnerNumberModel(Math.min(10, selected.combatAiIntelligence()), 0, 10, 1));
        JSpinner xpSpinner = new JSpinner(new SpinnerNumberModel(selected.xpReward(), 0, 100000, 1));
        JTextField attackSoundField = new JTextField(selected.attackSoundPath(), 24);
        JTextField damageSoundField = new JTextField(selected.damageSoundPath(), 24);
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
                    ((Number) spellBaseDamageSpinner.getValue()).intValue()
            ));
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

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Sprite PNG"));
        fields.add(pathFieldPanel(imagePathField, browseButton));
        fields.add(new JLabel("Paper-Doll Source"));
        fields.add(pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        for (PlayerStat stat : PlayerStat.values()) {
            fields.add(new JLabel(stat.getDisplayName()));
            fields.add(statSpinners.get(stat));
        }
        fields.add(new JLabel("Derived HP"));
        fields.add(hpLabel);
        fields.add(new JLabel("Difficulty"));
        fields.add(difficultyPreviewLabel);
        fields.add(new JLabel("Melee Max Hit"));
        fields.add(meleeMaxDamageLabel);
        fields.add(new JLabel("Spell Base Damage"));
        fields.add(spellBaseDamageSpinner);
        fields.add(new JLabel("Spell Max Hit"));
        fields.add(spellMaxDamageLabel);
        fields.add(new JLabel("Combat AI Intelligence"));
        fields.add(combatAiSpinner);
        fields.add(new JLabel("XP Reward"));
        fields.add(xpSpinner);
        fields.add(new JLabel("Attack Sound"));
        fields.add(pathFieldPanel(attackSoundField, attackSoundBrowseButton));
        fields.add(new JLabel("Hit Sound"));
        fields.add(pathFieldPanel(damageSoundField, damageSoundBrowseButton));
        fields.add(new JLabel("Loot Table"));
        fields.add(dropsButton);
        panel.add(fields, BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        panel.add(new JScrollPane(skillList), BorderLayout.EAST);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Enemy", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom enemy needs a name.");
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
                skillList.getSelectedValuesList(),
                dropEntries
        );
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
        MapDesignLibrary.CustomNpc edited = showCustomNpcDialog("Edit NPC", selected);
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

        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Delete " + type,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
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
        message.append("\nA timestamped authored-content backup will be written under assets/editor/content/backups before saving.");
        return message.toString();
    }

    private List<MapDesignLibrary.CustomLimb> generateLimbsForEnemy(
            String mobId,
            String enemyName,
            Map<PlayerStat, Integer> monsterStats,
            String limbDescription,
            String paperDollSourcePath,
            List<SkillLibrary> enemySkills
    ) {
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
                    skillAssignments.getOrDefault(slot, List.of())
            ));
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
                    selected.skillIds()
            );
            if (edited != null) {
                limbs.set(index, edited);
                limbList.setListData(limbs.toArray(new MapDesignLibrary.CustomLimb[0]));
                limbList.setSelectedIndex(index);
            }
        });

        JOptionPane.showMessageDialog(this, panel, "Generated Limbs", JOptionPane.PLAIN_MESSAGE);
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

        JPanel controls = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        controls.add(new JLabel("Item"));
        controls.add(itemBox);
        controls.add(new JLabel("Chance %"));
        controls.add(chanceSpinner);
        controls.add(addButton);
        controls.add(removeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(dropList), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Enemy Drops", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        JComboBox<DropItemOption> itemBox = new JComboBox<>(gatheringOutputItemOptions().toArray(new DropItemOption[0]));
        JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 100.0, 0.05));
        JButton addButton = new JButton("Add Loot");
        JButton removeButton = new JButton("Remove Selected");

        addButton.addActionListener(event -> {
            DropItemOption option = (DropItemOption) itemBox.getSelectedItem();
            if (option == null || option.itemId().isBlank()) {
                return;
            }
            model.addElement(new MapDesignLibrary.CustomDropEntry(option.itemId(), ((Number) weightSpinner.getValue()).doubleValue()));
        });

        removeButton.addActionListener(event -> {
            int index = dropList.getSelectedIndex();
            if (index >= 0) {
                model.remove(index);
            }
        });

        JPanel controls = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        controls.add(new JLabel("Item"));
        controls.add(itemBox);
        controls.add(new JLabel("Weight"));
        controls.add(weightSpinner);
        controls.add(addButton);
        controls.add(removeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(dropList), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Gathering Loot", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        for (ItemLibrary item : ItemLibrary.values()) {
            if (hasCustomItemId(item.name())) {
                continue;
            }
            options.add(new DropItemOption(item.name(), item.getDisplayName()));
        }
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
        for (ItemLibrary item : ItemLibrary.values()) {
            if (hasCustomItemId(item.name())) {
                continue;
            }
            options.add(new DropItemOption(item.name(), item.getDisplayName()));
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            options.add(new DropItemOption(item.itemId(), item.displayName()));
        }
        return options;
    }

    private ItemTemplateOption[] itemTemplateOptions() {
        List<ItemTemplateOption> options = new ArrayList<>();
        options.add(new ItemTemplateOption("None", null));
        for (ItemLibrary item : ItemLibrary.values()) {
            if (hasCustomItemId(item.name())) {
                continue;
            }
            options.add(new ItemTemplateOption(item.getDisplayName() + " [built-in]", builtInItemTemplate(item)));
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            options.add(new ItemTemplateOption(item.displayName() + " [custom]", item));
        }
        return options.toArray(new ItemTemplateOption[0]);
    }

    private MapDesignLibrary.CustomItem builtInItemTemplate(ItemLibrary item) {
        return new MapDesignLibrary.CustomItem(
                "",
                item.getDisplayName(),
                item.getItemType(),
                item.getIconPath(),
                "",
                item.getUseSoundPath(),
                item.getWeaponType(),
                item.isTwoHanded(),
                item.getMaterial(),
                item.getHealAmount(),
                item.getBaseGoldValue(),
                item.getExamineText(),
                null,
                item.isStackable(),
                false,
                1,
                1,
                0
        );
    }

    private String findItemIdByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        for (ItemLibrary item : ItemLibrary.values()) {
            if (displayName.equalsIgnoreCase(item.getDisplayName())) {
                return item.name();
            }
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
        for (ItemLibrary item : ItemLibrary.values()) {
            if (itemIdOrName.equalsIgnoreCase(item.name())
                    || itemIdOrName.equalsIgnoreCase(item.getDisplayName())) {
                return item.getIconPath();
            }
        }
        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            if (itemIdOrName.equalsIgnoreCase(item.itemId())
                    || itemIdOrName.equalsIgnoreCase(item.displayName())) {
                return item.iconPath();
            }
        }
        return "";
    }

    private void selectDropItem(JComboBox<DropItemOption> comboBox, String itemId) {
        if (comboBox == null || itemId == null || itemId.isBlank()) {
            return;
        }
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            DropItemOption option = comboBox.getItemAt(i);
            if (option != null && itemId.equals(option.itemId())) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
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
            case ATTACK -> slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM || slot == LimbSlot.HEAD ? 0.35 : 0.0;
            case STRENGTH -> slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM ? 0.50 : 0.0;
            case DEFENSE -> slot == LimbSlot.BODY ? 0.80 : slot == LimbSlot.HEAD ? 0.20 : 0.0;
            case AGILITY -> slot == LimbSlot.LEGS ? 0.70 : slot == LimbSlot.LEFT_ARM || slot == LimbSlot.RIGHT_ARM ? 0.15 : 0.0;
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
            List<SkillLibrary> selectedSkills
    ) {
        JTextField nameField = new JTextField(displayName, 24);
        JTextField iconPathField = new JTextField(iconPath, 28);
        JTextField sourceCreatureIdField = new JTextField(sourceCreatureId == null ? "" : sourceCreatureId, 28);
        JTextField paperDollSourceField = new JTextField(paperDollSourcePath == null ? "" : paperDollSourcePath, 28);
        JTextArea descriptionArea = new JTextArea(description == null ? "" : description, 4, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
        JComboBox<LimbSlot> slotBox = new JComboBox<>(LimbSlot.values());
        slotBox.setSelectedItem(limbSlot == null ? LimbSlot.HEAD : limbSlot);
        Map<PlayerStat, JSpinner> statSpinners = new EnumMap<>(PlayerStat.class);
        Map<SkillLibrary, JCheckBox> skillBoxes = new EnumMap<>(SkillLibrary.class);

        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollSourceField));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Icon PNG"));
        fields.add(pathFieldPanel(iconPathField, browseButton));
        fields.add(new JLabel("Paper-Doll Source"));
        fields.add(pathFieldPanel(paperDollSourceField, paperDollBrowseButton));
        fields.add(new JLabel("Source Creature Id"));
        fields.add(sourceCreatureIdField);
        fields.add(new JLabel("Slot"));
        fields.add(slotBox);
        fields.add(new JLabel("Condition"));
        fields.add(new JLabel(GearDurability.PERFECT.name()));
        for (PlayerStat stat : PlayerStat.values()) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(0, statValues == null ? 0 : statValues.getOrDefault(stat, 0)), 0, 1000, 1));
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

        int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            setStatus("Custom limb needs a name.");
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
                (LimbSlot) slotBox.getSelectedItem(),
                iconPathField.getText() == null ? "" : iconPathField.getText().trim(),
                GearDurability.PERFECT,
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                sourceCreatureIdField.getText() == null ? "" : sourceCreatureIdField.getText().trim(),
                paperDollSourceField.getText() == null ? "" : paperDollSourceField.getText().trim(),
                stats,
                skills
        );
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

    private JPanel formRow(String label, Component component) {
        JPanel row = new JPanel(new BorderLayout(6, 6));
        JLabel rowLabel = new JLabel(label);
        rowLabel.setPreferredSize(new Dimension(140, rowLabel.getPreferredSize().height));
        row.add(rowLabel, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
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
            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>();
            int actionCount = readPrefabInt(properties, prefix + "action.count", 0);
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                String actionPrefix = prefix + "action." + actionIndex + ".";
                MapDesignLibrary.TriggerActionType type = readPrefabTriggerActionType(properties.getProperty(actionPrefix + "type", ""));
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
                        MapDesignLibrary.TriggerFireMode.ON_ENTRY,
                        true,
                        actions
                ));
            }
        }

        String name = properties.getProperty("name", path.getFileName().toString().replaceFirst("[.][^.]+$", ""));
        return new MapPrefab(name, width, height, tiles, themes, placements, triggers);
    }

    private void createPrefabFromRegion() {
        JTextField nameField = new JTextField("New Prefab", 20);
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(0, design.width() - 1), 1));
        JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(0, design.height() - 1), 1));
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(Math.min(3, design.width()), 1, design.width(), 1));
        JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(Math.min(3, design.height()), 1, design.height(), 1));

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

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Prefab From Region", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        for (int y = 0; y < height; y++) {
            System.arraycopy(design.tiles()[startY + y], startX, tiles[y], 0, width);
            System.arraycopy(design.themeIndexes()[startY + y], startX, themes[y], 0, width);
        }

        List<MapDesignLibrary.MapPlacement> placements = new ArrayList<>();
        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.x() >= startX && placement.y() >= startY
                    && placement.x() < startX + width && placement.y() < startY + height) {
                placements.add(new MapDesignLibrary.MapPlacement(
                        placement.kind(),
                        placement.id(),
                        placement.x() - startX,
                        placement.y() - startY
                ));
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
                            action.targetY() - startY
                    ));
                }
            }
            triggers.add(new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x() - startX,
                    trigger.y() - startY,
                    trigger.fireMode(),
                    trigger.oneShot(),
                    actions
            ));
        }

        return new MapPrefab(name, width, height, tiles, themes, placements, triggers);
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
            for (int x = 0; x < prefab.width(); x++) {
                tileValues.add(prefab.tiles()[y][x].name());
                themeValues.add(String.valueOf(prefab.themes()[y][x]));
            }
            properties.setProperty("tile." + y, String.join(",", tileValues));
            properties.setProperty("theme." + y, String.join(",", themeValues));
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
            properties.setProperty(prefix + "action.count", String.valueOf(trigger.actions().size()));
            for (int actionIndex = 0; actionIndex < trigger.actions().size(); actionIndex++) {
                MapDesignLibrary.TriggerAction action = trigger.actions().get(actionIndex);
                String actionPrefix = prefix + "action." + actionIndex + ".";
                properties.setProperty(actionPrefix + "type", action.type().name());
                properties.setProperty(actionPrefix + "targetX", String.valueOf(action.targetX()));
                properties.setProperty(actionPrefix + "targetY", String.valueOf(action.targetY()));
            }
        }

        Path path = PREFAB_FOLDER.resolve(safeId(prefab.name()).isBlank() ? "prefab.properties" : safeId(prefab.name()) + ".properties");
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
                new Object[]{selectButton, deleteButton, closeButton},
                closeButton
        );
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
            if (selected == null || JOptionPane.showConfirmDialog(
                    dialog,
                    "Delete prefab " + selected.name() + "?",
                    "Delete Prefab",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            ) != JOptionPane.OK_OPTION) {
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
        dialog.setVisible(true);
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

    private void createMapLink() {
        try {
            Files.createDirectories(MapDesignLibrary.MAP_FOLDER);
        } catch (IOException exception) {
            setStatus("Map folder failed: " + exception.getMessage());
            return;
        }

        JFileChooser chooser = new JFileChooser(MapDesignLibrary.MAP_FOLDER.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Aether map design", "properties"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        MapDesignLibrary.MapDesign targetDesign;
        try {
            targetDesign = MapDesignLibrary.load(chooser.getSelectedFile().toPath());
        } catch (IOException exception) {
            setStatus("Target map failed: " + exception.getMessage());
            return;
        }

        TargetMapPickerPanel pickerPanel = new TargetMapPickerPanel(targetDesign);
        int result = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(pickerPanel),
                "Choose Map Link Target",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        Point selectedTarget = pickerPanel.getSelectedTile();
        if (selectedTarget == null) {
            setStatus("Map link target was not selected.");
            return;
        }

        if (targetDesign.tiles()[selectedTarget.y][selectedTarget.x].blocksMovement()) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "The selected target tile blocks movement. Create this link anyway?",
                    "Blocking Target",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
        }

        String id = "map_link|"
                + mapLinkPathForEditor(chooser.getSelectedFile().toPath())
                + "|"
                + selectedTarget.x
                + "|"
                + selectedTarget.y;
        PlaceableOption option = new PlaceableOption(
                "Map Link: " + mapLinkLabel(id),
                MapDesignLibrary.PlacementKind.INTERACTION,
                id
        );
        placeableCategoryBox.setSelectedItem(PlaceableCategory.MAP_LINKS);
        placeableBox.addItem(option);
        placeableBox.setSelectedItem(option);
        paintModeBox.setSelectedItem(PaintMode.PLACE_OBJECT);
        setStatus("Created map link placement. Paint it onto a tile.");
    }

    private void createTrigger() {
        String defaultId = nextTriggerId();
        String id = JOptionPane.showInputDialog(this, "Trigger id", defaultId);
        if (id == null) {
            return;
        }

        id = id.trim();
        if (id.isBlank()) {
            setStatus("Trigger id cannot be blank.");
            return;
        }

        if (findTrigger(id) != null) {
            setStatus("Trigger id already exists: " + id + ".");
            return;
        }

        pendingTriggerId = id;
        wiringTriggerId = "";
        paintModeBox.setSelectedItem(PaintMode.PLACE_TRIGGER);
        setStatus("Click a floor tile to place trigger " + id + ".");
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
        JButton wireButton = new JButton("Wire Targets");
        JButton removeTargetButton = new JButton("Remove Target");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel();
        buttons.add(renameButton);
        buttons.add(wireButton);
        buttons.add(removeTargetButton);
        buttons.add(deleteButton);
        buttons.add(closeButton);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JScrollPane(triggerList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{});
        var dialog = pane.createDialog(this, "Manage Triggers");

        renameButton.addActionListener(event -> {
            MapDesignLibrary.MapTrigger trigger = triggerList.getSelectedValue();
            if (trigger == null) {
                return;
            }
            String newId = JOptionPane.showInputDialog(dialog, "Trigger id", trigger.id());
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
                    trigger.actions()
            ));
            refreshTriggerList(model);
            setStatus("Renamed trigger to " + newId + ".");
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
            setStatus("Click door tiles to wire targets for " + trigger.id() + ".");
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
            String selected = (String) JOptionPane.showInputDialog(
                    dialog,
                    "Remove target",
                    "Trigger Target",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    targets,
                    targets[0]
            );
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
        dialog.setVisible(true);
    }

    private void deleteTrigger(MapDesignLibrary.MapTrigger trigger) {
        if (trigger == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete trigger " + trigger.id() + "?",
                "Delete Trigger",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
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
        return trigger.id() + " @ " + trigger.x() + "," + trigger.y()
                + " -> " + trigger.actions().size() + " door target(s)"
                + " [On Entry, One Shot]";
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
                    actions
            ));
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

        JOptionPane.showMessageDialog(
                this,
                new JScrollPane(helpArea),
                "Aether Construction Kit Authoring Help",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private String authoringHelpText() {
        return """
                Dialogue Basics
                - The top text box is the NPC's default repeat text.
                - Use ::firstTalk for the first thing the NPC says only once.
                - Define nodes with ::node_id.
                - Choices use: Button text => response text
                - To jump to another node, use: Button text => node_id

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

    private void manageAuthoredDialogues() {
        if (design.authoredDialogues().isEmpty()) {
            setStatus("No authored dialogue NPCs to manage.");
            return;
        }

        List<MapDesignLibrary.AuthoredDialogue> sortedDialogues = new ArrayList<>(design.authoredDialogues());
        sortedDialogues.sort(Comparator.comparing(MapDesignLibrary.AuthoredDialogue::speakerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MapDesignLibrary.AuthoredDialogue::interactionId, String.CASE_INSENSITIVE_ORDER));
        JList<MapDesignLibrary.AuthoredDialogue> dialogueList = new JList<>(
                sortedDialogues.toArray(new MapDesignLibrary.AuthoredDialogue[0])
        );
        dialogueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dialogueList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.speakerName() + " [" + value.visualPath() + ", " + value.interactionId() + "]");
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        dialogueList.setSelectedIndex(0);

        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton closeButton = new JButton("Close");
        JOptionPane pane = new JOptionPane(
                new JScrollPane(dialogueList),
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{editButton, deleteButton, closeButton},
                closeButton
        );
        var dialog = pane.createDialog(this, "Manage Dialogue NPCs");

        editButton.addActionListener(event -> {
            MapDesignLibrary.AuthoredDialogue selected = dialogueList.getSelectedValue();
            if (selected == null) {
                return;
            }

            editAuthoredDialogue(selected);
            dialog.dispose();
        });
        deleteButton.addActionListener(event -> {
            MapDesignLibrary.AuthoredDialogue selected = dialogueList.getSelectedValue();
            if (selected == null) {
                return;
            }

            deleteAuthoredDialogue(selected);
            dialog.dispose();
        });
        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
    }

    private void editAuthoredDialogue(MapDesignLibrary.AuthoredDialogue selected) {
        AuthoredDialogueDraft draft = showAuthoredDialogueDialog(
                "Edit Dialogue NPC",
                selected.speakerName(),
                selected.bodyText(),
                selected.followUpInteractionId(),
                selected.visualPath(),
                selected.questId(),
                selected.questStage(),
                selected.choices(),
                selected.nodes()
        );
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
                draft.visualPath(),
                "",
                null,
                0,
                0,
                draft.questId(),
                draft.questStage(),
                draft.choices(),
                draft.nodes()
        ));
        persistSharedContent("authored dialogue NPC");
        populatePlaceables();
        mapCanvas.repaint();
        setStatus("Updated authored dialogue NPC " + draft.speakerName() + ".");
    }

    private void deleteAuthoredDialogue(MapDesignLibrary.AuthoredDialogue selected) {
        int result = JOptionPane.showConfirmDialog(
                this,
                deleteMessage(
                        "Delete " + selected.speakerName() + " and remove its placed NPCs?",
                        findReferences(new ContentEntry(ContentCategory.DIALOGUES, selected.speakerName(), selected.interactionId(), "Dialogue NPC", selected))
                ),
                "Delete Dialogue NPC",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        design.authoredDialogues().remove(selected);
        int beforePlacements = design.placements().size();
        design.placements().removeIf(placement ->
                placement.kind() == MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC
                        && selected.interactionId().equals(placement.id())
        );
        int removedPlacements = beforePlacements - design.placements().size();
        persistSharedContent("authored dialogue NPC");
        populatePlaceables();
        mapCanvas.repaint();
        setStatus("Deleted " + selected.speakerName() + " and removed " + removedPlacements + " placed NPCs.");
    }

    private AuthoredDialogueDraft showAuthoredDialogueDialog(
            String title,
            String speakerName,
            String bodyText,
            String followUpInteractionId,
            String visualPath,
            String questId,
            int questStage,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes
    ) {
        JTextField speakerField = new JTextField(speakerName, 24);
        JTextArea bodyArea = new JTextArea(bodyText, 7, 28);
        JTextArea branchArea = new JTextArea(formatDialogueTree(choices, nodes), 9, 32);
        JTextField visualPathField = new JTextField(
                visualPath == null || visualPath.isBlank()
                        ? MapDesignLibrary.defaultEnemy(MapDesignLibrary.ENEMY_GOBLIN).imagePath()
                        : visualPath,
                24
        );
        JButton visualBrowseButton = new JButton("Browse");
        visualBrowseButton.addActionListener(event -> browsePathInto(visualPathField));
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
        textPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        JPanel branchPanel = new JPanel(new BorderLayout(4, 4));
        branchPanel.add(new JLabel("Dialogue Tree: Button => response/node, define nodes with ::node_id"), BorderLayout.NORTH);
        branchPanel.add(new JScrollPane(branchArea), BorderLayout.CENTER);
        branchPanel.add(new JLabel("Examples: ::firstTalk for intro text, [hasItem=Raw Fish] \"quest_id\"[2] Button => node_id"), BorderLayout.SOUTH);
        textPanel.add(branchPanel, BorderLayout.SOUTH);
        panel.add(textPanel, BorderLayout.CENTER);
        JPanel optionsPanel = new JPanel(new BorderLayout(6, 6));
        JPanel visualPanel = new JPanel(new BorderLayout(6, 6));
        visualPanel.add(new JLabel("Visual"), BorderLayout.WEST);
        visualPanel.add(pathFieldPanel(visualPathField, visualBrowseButton), BorderLayout.CENTER);
        JPanel followUpPanel = new JPanel(new BorderLayout(6, 6));
        followUpPanel.add(new JLabel("Then"), BorderLayout.WEST);
        followUpPanel.add(followUpBox, BorderLayout.CENTER);
        JPanel questActionPanel = new JPanel(new BorderLayout(6, 6));
        questActionPanel.add(new JLabel("Set Quest"), BorderLayout.WEST);
        questActionPanel.add(questActionBox, BorderLayout.CENTER);
        questActionPanel.add(questStageSpinner, BorderLayout.EAST);
        JPanel rewardPanel = new JPanel(new BorderLayout(6, 6));
        rewardPanel.add(questActionPanel, BorderLayout.CENTER);
        optionsPanel.add(visualPanel, BorderLayout.NORTH);
        optionsPanel.add(followUpPanel, BorderLayout.CENTER);
        optionsPanel.add(rewardPanel, BorderLayout.SOUTH);
        panel.add(optionsPanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String enteredSpeakerName = speakerField.getText() == null ? "" : speakerField.getText().trim();
        String enteredBodyText = bodyArea.getText() == null ? "" : bodyArea.getText().trim();
        DialogueTreeDraft treeDraft = parseDialogueTree(branchArea.getText());

        if (enteredSpeakerName.isBlank() || enteredBodyText.isBlank()) {
            setStatus("Dialogue NPC needs a speaker and dialogue text.");
            return null;
        }

        FollowUpInteractionOption selectedFollowUp = (FollowUpInteractionOption) followUpBox.getSelectedItem();
        QuestActionOption selectedQuest = (QuestActionOption) questActionBox.getSelectedItem();
        return new AuthoredDialogueDraft(
                enteredSpeakerName,
                enteredBodyText,
                selectedFollowUp == null ? "" : selectedFollowUp.interactionId(),
                visualPathField.getText() == null ? "" : visualPathField.getText().trim(),
                selectedQuest == null ? "" : selectedQuest.questId(),
                ((Number) questStageSpinner.getValue()).intValue(),
                treeDraft.choices(),
                treeDraft.nodes()
        );
    }

    private String formatDialogueTree(
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes
    ) {
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
                        toAuthoredChoices(node.choices(), nodeIds)
                ));
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
                firstTalkOnly
        );
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
            List<String> nodeIds
    ) {
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
                    choice.firstTalkOnly()
            ));
        }
        return choices;
    }

    private AuthoredQuestDraft showAuthoredQuestDialog(String title, String displayName, List<String> stageDescriptions) {
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

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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

    private String nextCompositeRecipeId(String recipeName) {
        String base = safeId(recipeName);
        if (base.isBlank()) {
            base = "composite_recipe";
        }

        String prefix = "recipe_" + base;
        String candidate = prefix;
        int suffix = 2;
        while (hasCompositeRecipeId(candidate)) {
            candidate = prefix + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean hasCompositeRecipeId(String recipeId) {
        for (MapDesignLibrary.CustomCompositeRecipe recipe : design.customCompositeRecipes()) {
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
            MapDesignLibrary.GatheringNodeType nodeType
    ) {
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
        return List.of(GatheringNodeLibrary.FISHING_SHOAL.getFramePaths());
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

        for (InteractionLibrary interaction : InteractionLibrary.values()) {
            if (interaction.isFollowUp()) {
                options.add(new FollowUpInteractionOption(
                        interaction.getDisplayName(),
                        interaction.getInteractionId()
                ));
            }
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

    private void createNewMap() {
        undoStack.clear();
        redoStack.clear();
        design = MapDesignLibrary.createBlank(
                ((Number) widthSpinner.getValue()).intValue(),
                ((Number) heightSpinner.getValue()).intValue(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) alternateThemeBox.getSelectedItem()
        );
        loadSharedContentIntoDesign();
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Created " + design.displayName() + " " + design.width() + "x" + design.height() + ".");
    }

    private void resizeCurrentMap() {
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
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Resize will remove content outside the new bounds:\n"
                            + "- Placements: " + droppedPlacements + "\n"
                            + "- Triggers: " + droppedTriggers + "\n"
                            + "- Trigger wire targets: " + droppedTriggerActions + "\n\n"
                            + "Continue?",
                    "Resize Map",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
        }

        captureHistory("resize map");
        MapDesignLibrary.MapDesign blank = MapDesignLibrary.createBlank(
                newWidth,
                newHeight,
                design.primaryTheme(),
                design.alternateTheme()
        );
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
                    .filter(action -> isInsideDimensions(action.targetX(), action.targetY(), blank.width(), blank.height()))
                    .toList();
            triggers.add(new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    actions
            ));
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
                design.primaryTheme(),
                design.alternateTheme(),
                blank.tiles(),
                blank.themeIndexes(),
                placements,
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.customCompositeRecipes(),
                triggers,
                spawnX,
                spawnY
        );
        syncEditorFromDesign();
        markDirty(true);
        setStatus("Resized map to " + design.width() + "x" + design.height() + ".");
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
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) alternateThemeBox.getSelectedItem(),
                design.tiles(),
                design.themeIndexes(),
                design.placements(),
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.customCompositeRecipes(),
                design.triggers(),
                design.spawnX(),
                design.spawnY()
        );
    }

    private void editMetadata() {
        JTextField titleField = new JTextField(mapTitle(), 24);
        JTextArea descriptionArea = new JTextArea(design.description(), 6, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel titlePanel = new JPanel(new BorderLayout(6, 6));
        titlePanel.add(new JLabel("Title"), BorderLayout.WEST);
        titlePanel.add(titleField, BorderLayout.CENTER);
        panel.add(titlePanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Map Metadata",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
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
        design = new MapDesignLibrary.MapDesign(
                design.width(),
                design.height(),
                title,
                descriptionArea.getText() == null ? "" : descriptionArea.getText().trim(),
                design.primaryTheme(),
                design.alternateTheme(),
                design.tiles(),
                design.themeIndexes(),
                design.placements(),
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.customCompositeRecipes(),
                design.triggers(),
                design.spawnX(),
                design.spawnY()
        );
        markDirty(true);
        refreshContentBrowser();
        setStatus("Updated metadata for " + title + ".");
    }

    private void validateMap() {
        syncThemes();
        List<MapDesignLibrary.ValidationIssue> issues = MapDesignLibrary.validate(design);

        if (issues.isEmpty()) {
            setStatus("Validation passed.");
            JOptionPane.showMessageDialog(this, "Validation passed.", "Map Validation", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String message = validationMessage(issues);
        setStatus("Validation found " + issues.size() + " issue(s).");
        JOptionPane.showMessageDialog(this, message, "Map Validation", JOptionPane.WARNING_MESSAGE);
    }

    private boolean confirmSaveWithValidationIssues() {
        List<MapDesignLibrary.ValidationIssue> issues = MapDesignLibrary.validate(design);
        if (issues.isEmpty()) {
            return true;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                validationMessage(issues) + "\n\nSave anyway?",
                "Map Validation",
                JOptionPane.OK_CANCEL_OPTION,
                hasValidationError(issues) ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE
        );

        return result == JOptionPane.OK_OPTION;
    }

    private void loadSharedContentIntoDesign() {
        try {
            mergeSharedContent(MapDesignLibrary.loadSharedContent());
            if (seedBuiltInItemsIntoDesign()) {
                persistSharedContent("built-in item seed");
            }
        } catch (IOException exception) {
            setStatus("Shared content load failed: " + exception.getMessage());
        }
    }

    private boolean seedBuiltInItemsIntoDesign() {
        boolean changed = false;
        for (MapDesignLibrary.CustomItem item : MapDesignLibrary.builtInItemDefinitions()) {
            if (!hasCustomItemId(item.itemId())) {
                design.customItems().add(item);
                changed = true;
            }
        }
        return changed;
    }

    private void mergeSharedContent(MapDesignLibrary.AuthoredContent content) {
        if (content == null) {
            return;
        }

        for (MapDesignLibrary.AuthoredQuest quest : content.authoredQuests()) {
            if (!hasAuthoredQuestId(quest.questId())) {
                design.authoredQuests().add(quest);
            }
        }
        for (MapDesignLibrary.AuthoredDialogue dialogue : content.authoredDialogues()) {
            if (!hasAuthoredDialogueId(dialogue.interactionId())) {
                design.authoredDialogues().add(dialogue);
            }
        }
        for (MapDesignLibrary.CustomItem item : content.customItems()) {
            if (!hasCustomItemId(item.itemId())) {
                design.customItems().add(item);
            }
        }
        for (MapDesignLibrary.CustomMob mob : content.customMobs()) {
            if (!hasCustomMobId(mob.mobId())) {
                design.customMobs().add(mob);
            }
        }
        for (MapDesignLibrary.CustomLimb limb : content.customLimbs()) {
            if (!hasCustomLimbId(limb.limbId())) {
                design.customLimbs().add(limb);
            }
        }
        for (MapDesignLibrary.CustomNpc npc : content.customNpcs()) {
            if (!hasCustomNpcId(npc.npcId())) {
                design.customNpcs().add(npc);
            }
        }
        for (MapDesignLibrary.CustomGatheringNode node : content.customGatheringNodes()) {
            if (!hasCustomGatheringNodeId(node.nodeId())) {
                design.customGatheringNodes().add(node);
            }
        }
        for (MapDesignLibrary.CustomCookingRecipe recipe : content.customCookingRecipes()) {
            if (!hasCookingRecipeId(recipe.recipeId())) {
                design.customCookingRecipes().add(recipe);
            }
        }
        for (MapDesignLibrary.CustomCompositeRecipe recipe : content.customCompositeRecipes()) {
            if (!hasCompositeRecipeId(recipe.recipeId())) {
                design.customCompositeRecipes().add(recipe);
            }
        }
    }

    private boolean saveSharedContentFromDesign() {
        return persistSharedContent("shared content");
    }

    private boolean persistSharedContent(String contentType) {
        try {
            backupSharedContentIfPresent();
            MapDesignLibrary.saveSharedContent(new MapDesignLibrary.AuthoredContent(
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
                    design.customGatheringNodes(),
                    design.customCookingRecipes(),
                    design.customCompositeRecipes()
            ));
            refreshContentBrowser();
            return true;
        } catch (IOException exception) {
            setStatus("Shared content save failed: " + exception.getMessage());
            return false;
        }
    }

    private void backupSharedContentIfPresent() throws IOException {
        Path source = MapDesignLibrary.SHARED_CONTENT_PATH;
        if (!Files.isRegularFile(source)) {
            return;
        }

        Files.createDirectories(SHARED_CONTENT_BACKUP_FOLDER);
        String backupName = "authored_content_" + System.currentTimeMillis() + ".properties";
        Files.copy(source, SHARED_CONTENT_BACKUP_FOLDER.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);
        pruneSharedContentBackups();
    }

    private void pruneSharedContentBackups() throws IOException {
        if (!Files.isDirectory(SHARED_CONTENT_BACKUP_FOLDER)) {
            return;
        }

        try (var stream = Files.list(SHARED_CONTENT_BACKUP_FOLDER)) {
            List<Path> backups = stream
                    .filter(path -> path.getFileName().toString().startsWith("authored_content_"))
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted(Comparator
                            .comparing((Path path) -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                            .reversed())
                    .toList();
            for (int i = MAX_SHARED_CONTENT_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
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
                    targetDesign.height() * CELL_SIZE + PADDING * 2
            ));
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
                    int adjustment = targetDesign.themeIndexes()[y][x] == 1 ? 24 : 0;
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
                        CELL_SIZE / 2
                );
            }

            g.setColor(Color.WHITE);
            g.drawString("S", PADDING + targetDesign.spawnX() * CELL_SIZE + 9, PADDING + targetDesign.spawnY() * CELL_SIZE + 19);

            if (selectedTile != null) {
                g.setStroke(new BasicStroke(3));
                g.setColor(Color.YELLOW);
                g.drawRect(
                        PADDING + selectedTile.x * CELL_SIZE + 2,
                        PADDING + selectedTile.y * CELL_SIZE + 2,
                        CELL_SIZE - 4,
                        CELL_SIZE - 4
                );
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
            return new Dimension(design.width() * cellSize + 1, design.height() * cellSize + 1);
        }

        private void scrollToTile(int x, int y) {
            int cellSize = cellSize();
            scrollRectToVisible(new Rectangle(
                    Math.max(0, x * cellSize - cellSize),
                    Math.max(0, y * cellSize - cellSize),
                    cellSize * 3,
                    cellSize * 3
            ));
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

            for (int y = 0; y < design.height(); y++) {
                for (int x = 0; x < design.width(); x++) {
                    drawCell(g, x, y);
                }
            }

            drawPlacements(g);
            drawTriggers(g);
            drawSpawn(g);
            drawInspectionSelection(g);
            g.dispose();
        }

        private void drawCell(Graphics2D g, int x, int y) {
            int cellSize = cellSize();
            Rectangle bounds = new Rectangle(x * cellSize, y * cellSize, cellSize, cellSize);
            Library.TileType tile = design.tiles()[y][x];
            int themeIndex = design.themeIndexes()[y][x];
            g.setColor(tileColor(tile, themeIndex));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setColor(new Color(10, 10, 12, 120));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

            if (themeIndex == 1) {
                g.setColor(new Color(255, 238, 128, 160));
                g.drawLine(bounds.x + 4, bounds.y + 4, bounds.x + bounds.width - 5, bounds.y + bounds.height - 5);
            }
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
                g.drawString(label, x + (cellSize - metrics.stringWidth(label)) / 2, y + Math.max(14, cellSize / 2 + 5));
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
                g.drawRect(trigger.x() * cellSize + inset, trigger.y() * cellSize + inset, cellSize - inset * 2, cellSize - inset * 2);
                g.drawString("T", trigger.x() * cellSize + 10, trigger.y() * cellSize + Math.max(14, cellSize / 2 + 5));

                for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                    int targetCenterX = action.targetX() * cellSize + cellSize / 2;
                    int targetCenterY = action.targetY() * cellSize + cellSize / 2;
                    g.setColor(new Color(255, 205, 70, 150));
                    g.drawLine(triggerCenterX, triggerCenterY, targetCenterX, targetCenterY);
                    g.setColor(new Color(255, 105, 80));
                    g.drawOval(action.targetX() * cellSize + inset, action.targetY() * cellSize + inset, cellSize - inset * 2, cellSize - inset * 2);
                }
            }
        }

        private void drawSpawn(Graphics2D g) {
            int cellSize = cellSize();
            int x = design.spawnX() * cellSize;
            int y = design.spawnY() * cellSize;
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
            }

            g.setColor(color);
            g.drawRect(
                    inspectedTile.x * cellSize + 2,
                    inspectedTile.y * cellSize + 2,
                    cellSize - 5,
                    cellSize - 5
            );
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            g.fillRect(
                    inspectedTile.x * cellSize + 3,
                    inspectedTile.y * cellSize + 3,
                    cellSize - 6,
                    cellSize - 6
            );
        }

        private void inspectAt(Point point) {
            int cellSize = cellSize();
            int x = point.x / cellSize;
            int y = point.y / cellSize;

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
            int cellSize = cellSize();
            int x = event.getX() / cellSize;
            int y = event.getY() / cellSize;
            if (!isTileInBounds(x, y)) {
                return;
            }

            MapDesignLibrary.MapPlacement placement = placementAt(x, y);
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
                Point triggerTarget
        ) {
            inspectedTile = new Point(x, y);
            inspectedPlacement = placement;
            inspectedTrigger = trigger;
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
            int themeIndex = design.themeIndexes()[y][x];
            String themeName = themeIndex == 1
                    ? design.alternateTheme().getDisplayName()
                    : design.primaryTheme().getDisplayName();
            StringBuilder builder = new StringBuilder();
            builder.append("Tile\n");
            builder.append(x).append(',').append(y).append("\n\n");
            builder.append("Type: ").append(tile).append('\n');
            builder.append("Blocks Movement: ").append(tile.blocksMovement()).append('\n');
            builder.append("Theme Slot: ").append(themeIndex == 1 ? "Alternate" : "Primary").append('\n');
            builder.append("Theme: ").append(themeName).append('\n');
            builder.append("Spawn: ").append(x == design.spawnX() && y == design.spawnY()).append('\n');
            return builder.toString();
        }

        private void paintAt(Point point) {
            int cellSize = cellSize();
            int x = point.x / cellSize;
            int y = point.y / cellSize;

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
                    || mode == PaintMode.PRIMARY_THEME
                    || mode == PaintMode.ALTERNATE_THEME
                    || mode == PaintMode.ERASE_OBJECT;
        }

        private void applyPaintMode(PaintMode mode, int x, int y) {
            switch (mode) {
                case TILE -> {
                    design.tiles()[y][x] = (Library.TileType) tileTypeBox.getSelectedItem();
                    if (x == design.spawnX() && y == design.spawnY() && design.tiles()[y][x].blocksMovement()) {
                        setStatus("Spawn is now blocked; set a new spawn on a walkable tile.");
                    }
                }
                case PRIMARY_THEME -> design.themeIndexes()[y][x] = 0;
                case ALTERNATE_THEME -> design.themeIndexes()[y][x] = 1;
                case PLACE_OBJECT -> placeObject(x, y);
                case ERASE_OBJECT -> eraseObject(x, y);
                case SET_SPAWN -> setSpawn(x, y);
                case PLACE_TRIGGER -> placeTrigger(x, y);
                case WIRE_TRIGGER -> wireTriggerTarget(x, y);
                case PLACE_PREFAB -> placePrefab(x, y);
            }
        }

        private void setSpawn(int x, int y) {
            if (design.tiles()[y][x].blocksMovement()) {
                setStatus("Spawn must be on a walkable tile.");
                return;
            }

            design = new MapDesignLibrary.MapDesign(
                    design.width(),
                    design.height(),
                    design.displayName(),
                    design.description(),
                    design.primaryTheme(),
                    design.alternateTheme(),
                    design.tiles(),
                    design.themeIndexes(),
                    design.placements(),
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
                    design.customGatheringNodes(),
                    design.customCookingRecipes(),
                    design.customCompositeRecipes(),
                    design.triggers(),
                    x,
                    y
            );
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
                    design.tiles()[y + prefabY][x + prefabX] = prefab.tiles()[prefabY][prefabX];
                    design.themeIndexes()[y + prefabY][x + prefabX] = prefab.themes()[prefabY][prefabX];
                    eraseObject(x + prefabX, y + prefabY);
                }
            }
            for (MapDesignLibrary.MapPlacement placement : prefab.placements()) {
                design.placements().add(new MapDesignLibrary.MapPlacement(
                        placement.kind(),
                        placement.id(),
                        x + placement.x(),
                        y + placement.y()
                ));
            }
            for (MapDesignLibrary.MapTrigger trigger : prefab.triggers()) {
                String triggerId = uniqueTriggerId(trigger.id());
                List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                        .map(action -> new MapDesignLibrary.TriggerAction(
                                action.type(),
                                x + action.targetX(),
                                y + action.targetY()
                        ))
                        .toList();
                design.triggers().add(new MapDesignLibrary.MapTrigger(
                        triggerId,
                        x + trigger.x(),
                        y + trigger.y(),
                        trigger.fireMode(),
                        trigger.oneShot(),
                        actions
                ));
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

            int triggersBefore = design.triggers().size();
            design.triggers().removeIf(trigger -> trigger.x() == x && trigger.y() == y);

            int removedWireTargets = removeTriggerTargetsAt(x, y);
            int removedPlacements = placementsBefore - design.placements().size();
            int removedTriggers = triggersBefore - design.triggers().size();

            if (removedTriggers > 0) {
                setStatus("Removed trigger at " + x + "," + y + ".");
            } else if (removedWireTargets > 0) {
                setStatus("Removed " + removedWireTargets + " trigger wire target(s) at " + x + "," + y + ".");
            } else if (removedPlacements > 0) {
                setStatus("Removed placement at " + x + "," + y + ".");
            } else {
                setStatus("Nothing to erase at " + x + "," + y + ".");
            }
            if (removedTriggers > 0 || removedWireTargets > 0 || removedPlacements > 0) {
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
                            actions
                    ));
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
                    MapDesignLibrary.TriggerFireMode.ON_ENTRY,
                    true,
                    List.of()
            );
            design.triggers().removeIf(existing -> existing.id().equals(trigger.id()));
            design.triggers().add(trigger);
            wiringTriggerId = trigger.id();
            pendingTriggerId = "";
            paintModeBox.setSelectedItem(PaintMode.WIRE_TRIGGER);
            refreshContentBrowser();
            setStatus("Placed " + trigger.id() + ". Click door tiles to wire close targets.");
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
            actions.add(new MapDesignLibrary.TriggerAction(MapDesignLibrary.TriggerActionType.CLOSE_DOOR, x, y));
            replaceTrigger(trigger, new MapDesignLibrary.MapTrigger(
                    trigger.id(),
                    trigger.x(),
                    trigger.y(),
                    trigger.fireMode(),
                    trigger.oneShot(),
                    actions
            ));
            refreshContentBrowser();
            setStatus("Wired " + trigger.id() + " to close door at " + x + "," + y + ".");
        }

        private Color tileColor(Library.TileType tile, int themeIndex) {
            int adjustment = themeIndex == 1 ? 24 : 0;
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

    private enum PlaceableCategory {
        ITEMS("Items"),
        ENEMIES("Enemies"),
        NPCS("NPCs"),
        DIALOGUE_NPCS("Dialogue NPCs"),
        GATHERING_NODES("Gathering Nodes"),
        CRAFTING_NODES("Crafting Nodes"),
        INTERACTIONS("Interactions"),
        MAP_LINKS("Map Links");

        private final String label;

        PlaceableCategory(String label) {
            this.label = label;
        }

        private boolean includes(PlaceableOption option) {
            if (option == null || option.kind() == null) {
                return false;
            }

            return switch (this) {
                case ITEMS -> option.kind() == MapDesignLibrary.PlacementKind.ITEM;
                case ENEMIES -> option.kind() == MapDesignLibrary.PlacementKind.ENEMY;
                case NPCS -> option.kind() == MapDesignLibrary.PlacementKind.GENERIC_NPC
                        || option.kind() == MapDesignLibrary.PlacementKind.MAIN_NPC
                        || option.kind() == MapDesignLibrary.PlacementKind.CUSTOM_NPC;
                case DIALOGUE_NPCS -> option.kind() == MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC;
                case GATHERING_NODES -> option.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE;
                case CRAFTING_NODES -> option.kind() == MapDesignLibrary.PlacementKind.CRAFTING_NODE;
                case INTERACTIONS -> option.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                        && !option.id().startsWith("map_link|");
                case MAP_LINKS -> option.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                        && option.id().startsWith("map_link|");
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record PlaceableOption(String label, MapDesignLibrary.PlacementKind kind, String id) {
        @Override
        public String toString() {
            return label;
        }
    }

    private enum ContentCategory {
        ALL("All"),
        ITEMS("Items"),
        ENEMIES("Enemies"),
        NPCS("NPCs"),
        LIMBS("Limbs"),
        GATHERING("Gathering"),
        COOKING("Cooking"),
        COMPOSITES("Composite Recipes"),
        QUESTS("Quests"),
        DIALOGUES("Dialogues"),
        TRIGGERS("Triggers"),
        PLACEMENTS("Placements"),
        DIAGNOSTICS("Diagnostics");

        private final String label;

        ContentCategory(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record ContentEntry(ContentCategory category, String label, String id, String type, Object value) {
        private ContentEntry {
            label = label == null || label.isBlank() ? id : label;
            id = id == null ? "" : id;
            type = type == null ? "" : type;
        }

        private String key() {
            return category.name() + "|" + type + "|" + id + "|" + System.identityHashCode(value);
        }

        private String searchText() {
            return (category.label() + " " + label + " " + id + " " + type)
                    .toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum AssetBrowserType {
        ALL("All"),
        IMAGES("Images"),
        SOUNDS("Sounds"),
        DATA("Data"),
        OTHER("Other");

        private final String label;

        AssetBrowserType(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record AssetBrowserEntry(String assetPath, AssetBrowserType type, Path sourcePath) {
        private String searchText() {
            return (assetPath + " " + type.label()).toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return type.label() + ": " + assetPath;
        }
    }

    private record FollowUpInteractionOption(String label, String interactionId) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record StatTargetOption(String label, PlayerStat stat) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record QuestActionOption(String label, String questId) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record DialogueOption(String label, String interactionId) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record AuthoredDialogueDraft(
            String speakerName,
            String bodyText,
            String followUpInteractionId,
            String visualPath,
            String questId,
            int questStage,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes
    ) {
    }

    private record DialogueTreeDraft(
            List<MapDesignLibrary.AuthoredDialogueChoice> choices,
            List<MapDesignLibrary.AuthoredDialogueNode> nodes
    ) {
    }

    private record PendingChoice(
            String label,
            String destination,
            String questId,
            int questStage,
            String requiredItemName,
            String takeItemName,
            String giveItemName,
            int giveGold,
            CharacterSkill giveSkill,
            int giveSkillXp,
            boolean firstTalkOnly
    ) {
    }

    private record SkillXpTag(CharacterSkill skill, int amount) {
    }

    private record PendingNode(String nodeId, List<String> bodyLines, List<PendingChoice> choices) {
        private PendingNode(String nodeId) {
            this(nodeId, new ArrayList<>(), new ArrayList<>());
        }
    }

    private record AuthoredQuestDraft(String displayName, List<String> stageDescriptions) {
    }

    private record MapPrefab(
            String name,
            int width,
            int height,
            Library.TileType[][] tiles,
            int[][] themes,
            List<MapDesignLibrary.MapPlacement> placements,
            List<MapDesignLibrary.MapTrigger> triggers
    ) {
        private MapPrefab {
            name = name == null || name.isBlank() ? "Prefab" : name;
            width = Math.max(1, width);
            height = Math.max(1, height);
            placements = placements == null ? List.of() : List.copyOf(placements);
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
        }

        @Override
        public String toString() {
            return name + " (" + width + "x" + height + ")";
        }
    }

    private record ContentGraph(String selectedLabel, List<String> dependencies, List<String> references) {
        private ContentGraph {
            selectedLabel = selectedLabel == null || selectedLabel.isBlank() ? "Selected Content" : selectedLabel;
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            references = references == null ? List.of() : List.copyOf(references);
        }
    }

    private record DialogueTerminal(String sourceId, int choiceIndex, String label) {
    }

    private static final class DialogueGraphPanel extends JPanel {
        private static final int NODE_WIDTH = 210;
        private static final int NODE_HEIGHT = 58;
        private static final int TERMINAL_WIDTH = 230;
        private static final int H_GAP = 96;
        private static final int V_GAP = 26;
        private static final int PADDING = 30;

        private final MapDesignLibrary.AuthoredDialogue dialogue;
        private final List<DialogueTerminal> terminals;

        private DialogueGraphPanel(MapDesignLibrary.AuthoredDialogue dialogue) {
            this.dialogue = dialogue;
            this.terminals = collectTerminals(dialogue);
            setBackground(new Color(24, 26, 32));
            int rows = Math.max(2, Math.max(dialogue.nodes().size() + 1, terminals.size() + 1));
            int width = PADDING * 2 + NODE_WIDTH * 2 + TERMINAL_WIDTH + H_GAP * 2;
            int height = PADDING * 2 + rows * (NODE_HEIGHT + V_GAP) + 72;
            setPreferredSize(new Dimension(width, Math.max(430, height)));
        }

        private static List<DialogueTerminal> collectTerminals(MapDesignLibrary.AuthoredDialogue dialogue) {
            List<DialogueTerminal> result = new ArrayList<>();
            collectTerminalsForSource(result, "start", dialogue.choices());
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                collectTerminalsForSource(result, node.nodeId(), node.choices());
            }
            return result;
        }

        private static void collectTerminalsForSource(
                List<DialogueTerminal> result,
                String sourceId,
                List<MapDesignLibrary.AuthoredDialogueChoice> choices
        ) {
            for (int i = 0; i < choices.size(); i++) {
                MapDesignLibrary.AuthoredDialogueChoice choice = choices.get(i);
                if (choice.targetNodeId().isBlank()) {
                    String label = choice.bodyText().isBlank() ? "Terminal response" : choice.bodyText();
                    result.add(new DialogueTerminal(sourceId, i, label));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle startRect = new Rectangle(PADDING, PADDING + 76, NODE_WIDTH, NODE_HEIGHT);
            Map<String, Rectangle> nodeRects = layoutDialogueNodes();
            Map<DialogueTerminal, Rectangle> terminalRects = layoutTerminals();

            drawTitle(g);
            drawEdges(g, "start", startRect, dialogue.choices(), nodeRects, terminalRects);
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                Rectangle source = nodeRects.get(node.nodeId());
                if (source != null) {
                    drawEdges(g, node.nodeId(), source, node.choices(), nodeRects, terminalRects);
                }
            }

            drawNode(g, startRect, "start: " + dialogue.speakerName(), dialogue.bodyText(), new Color(58, 110, 178));
            for (MapDesignLibrary.AuthoredDialogueNode node : dialogue.nodes()) {
                Rectangle bounds = nodeRects.get(node.nodeId());
                if (bounds != null) {
                    drawNode(g, bounds, "::" + node.nodeId(), node.bodyText(), new Color(60, 78, 96));
                }
            }
            for (Map.Entry<DialogueTerminal, Rectangle> entry : terminalRects.entrySet()) {
                drawNode(g, entry.getValue(), "response", entry.getKey().label(), new Color(72, 62, 76));
            }

            g.dispose();
        }

        private void drawTitle(Graphics2D g) {
            g.setColor(new Color(230, 234, 242));
            g.drawString("Dialogue Flow: " + dialogue.interactionId(), PADDING, PADDING);
            g.setColor(new Color(166, 176, 192));
            g.drawString("Choice arrows show quest/item tags. Edit button below opens the dialogue authoring form.", PADDING, PADDING + 20);
        }

        private Map<String, Rectangle> layoutDialogueNodes() {
            Map<String, Rectangle> nodeRects = new java.util.LinkedHashMap<>();
            int x = PADDING + NODE_WIDTH + H_GAP;
            int y = PADDING + 50;
            for (int i = 0; i < dialogue.nodes().size(); i++) {
                MapDesignLibrary.AuthoredDialogueNode node = dialogue.nodes().get(i);
                nodeRects.put(node.nodeId(), new Rectangle(x, y + i * (NODE_HEIGHT + V_GAP), NODE_WIDTH, NODE_HEIGHT));
            }
            return nodeRects;
        }

        private Map<DialogueTerminal, Rectangle> layoutTerminals() {
            Map<DialogueTerminal, Rectangle> terminalRects = new java.util.LinkedHashMap<>();
            int x = PADDING + NODE_WIDTH * 2 + H_GAP * 2;
            int y = PADDING + 50;
            for (int i = 0; i < terminals.size(); i++) {
                DialogueTerminal terminal = terminals.get(i);
                terminalRects.put(terminal, new Rectangle(x, y + i * (NODE_HEIGHT + V_GAP), TERMINAL_WIDTH, NODE_HEIGHT));
            }
            return terminalRects;
        }

        private void drawEdges(
                Graphics2D g,
                String sourceId,
                Rectangle source,
                List<MapDesignLibrary.AuthoredDialogueChoice> choices,
                Map<String, Rectangle> nodeRects,
                Map<DialogueTerminal, Rectangle> terminalRects
        ) {
            for (int i = 0; i < choices.size(); i++) {
                MapDesignLibrary.AuthoredDialogueChoice choice = choices.get(i);
                Rectangle target = choice.targetNodeId().isBlank()
                        ? findTerminalRect(terminalRects, sourceId, i)
                        : nodeRects.get(choice.targetNodeId());
                if (target == null) {
                    continue;
                }
                drawConnector(g, source, target, shortChoiceLabel(choice));
            }
        }

        private Rectangle findTerminalRect(Map<DialogueTerminal, Rectangle> terminalRects, String sourceId, int choiceIndex) {
            for (Map.Entry<DialogueTerminal, Rectangle> entry : terminalRects.entrySet()) {
                DialogueTerminal terminal = entry.getKey();
                if (terminal.sourceId().equals(sourceId) && terminal.choiceIndex() == choiceIndex) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private void drawConnector(Graphics2D g, Rectangle source, Rectangle target, String label) {
            int startX = source.x + source.width;
            int startY = source.y + source.height / 2;
            int endX = target.x;
            int endY = target.y + target.height / 2;
            int midX = (startX + endX) / 2;

            g.setColor(new Color(124, 138, 160));
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(startX, startY, midX, startY);
            g.drawLine(midX, startY, midX, endY);
            g.drawLine(midX, endY, endX, endY);
            g.drawLine(endX, endY, endX - 8, endY - 5);
            g.drawLine(endX, endY, endX - 8, endY + 5);

            g.setColor(new Color(220, 224, 232));
            g.drawString(label, Math.min(startX + 8, midX - 4), Math.min(startY, endY) - 6);
        }

        private String shortChoiceLabel(MapDesignLibrary.AuthoredDialogueChoice choice) {
            List<String> tags = new ArrayList<>();
            if (!choice.requiredItemName().isBlank()) {
                tags.add("has");
            }
            if (!choice.takeItemName().isBlank()) {
                tags.add("take");
            }
            if (!choice.giveItemName().isBlank()) {
                tags.add("give");
            }
            if (!choice.questId().isBlank()) {
                tags.add("q" + choice.questStage());
            }
            String label = choice.label();
            if (!tags.isEmpty()) {
                label += " [" + String.join("/", tags) + "]";
            }
            return truncate(label, 34);
        }

        private void drawNode(Graphics2D g, Rectangle bounds, String title, String body, Color fill) {
            g.setColor(fill);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setColor(new Color(180, 188, 202));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(truncate(title, 26), bounds.x + 8, bounds.y + 17);
            g.setColor(new Color(226, 230, 238));
            drawWrappedText(g, body, new Rectangle(bounds.x + 8, bounds.y + 24, bounds.width - 16, bounds.height - 28));
        }

        private void drawWrappedText(Graphics2D g, String text, Rectangle bounds) {
            FontMetrics metrics = g.getFontMetrics();
            List<String> lines = wrapText(text == null ? "" : text.replace('\n', ' '), metrics, bounds.width);
            int y = bounds.y + metrics.getAscent();
            for (String line : lines.stream().limit(2).toList()) {
                g.drawString(line, bounds.x, y);
                y += metrics.getHeight();
            }
        }

        private List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) <= maxWidth || line.isEmpty()) {
                    line = new StringBuilder(candidate);
                } else {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private String truncate(String value, int maxLength) {
            String safe = value == null ? "" : value.replace('\n', ' ').trim();
            if (safe.length() <= maxLength) {
                return safe;
            }
            return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
        }
    }

    private static final class ContentGraphPanel extends JPanel {
        private static final int NODE_WIDTH = 210;
        private static final int NODE_HEIGHT = 42;
        private static final int H_GAP = 90;
        private static final int V_GAP = 18;
        private static final int PADDING = 28;

        private final ContentGraph graph;

        private ContentGraphPanel(ContentGraph graph) {
            this.graph = graph;
            setBackground(new Color(24, 26, 32));
            int rows = Math.max(1, Math.max(graph.dependencies().size(), graph.references().size()));
            int width = PADDING * 2 + NODE_WIDTH * 3 + H_GAP * 2;
            int height = PADDING * 2 + rows * (NODE_HEIGHT + V_GAP) + 80;
            setPreferredSize(new Dimension(width, Math.max(360, height)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int centerX = getWidth() / 2 - NODE_WIDTH / 2;
            int centerY = Math.max(PADDING + 48, getHeight() / 2 - NODE_HEIGHT / 2);
            Rectangle selected = new Rectangle(centerX, centerY, NODE_WIDTH, NODE_HEIGHT);

            drawColumn(g, "Used By", graph.references(), PADDING, selected, true);
            drawColumn(g, "Uses", graph.dependencies(), getWidth() - PADDING - NODE_WIDTH, selected, false);
            drawNode(g, selected, graph.selectedLabel(), new Color(60, 112, 184), Color.WHITE);

            g.dispose();
        }

        private void drawColumn(Graphics2D g, String title, List<String> values, int x, Rectangle selected, boolean incoming) {
            g.setColor(new Color(220, 224, 232));
            g.drawString(title, x, PADDING);
            if (values.isEmpty()) {
                Rectangle none = new Rectangle(x, PADDING + 20, NODE_WIDTH, NODE_HEIGHT);
                drawNode(g, none, "None", new Color(58, 58, 66), new Color(220, 224, 232));
                drawConnector(g, none, selected, incoming);
                return;
            }

            int totalHeight = values.size() * NODE_HEIGHT + (values.size() - 1) * V_GAP;
            int startY = Math.max(PADDING + 24, selected.y + selected.height / 2 - totalHeight / 2);
            for (int i = 0; i < values.size(); i++) {
                Rectangle node = new Rectangle(x, startY + i * (NODE_HEIGHT + V_GAP), NODE_WIDTH, NODE_HEIGHT);
                drawNode(g, node, values.get(i), new Color(54, 62, 74), new Color(235, 238, 245));
                drawConnector(g, node, selected, incoming);
            }
        }

        private void drawConnector(Graphics2D g, Rectangle sideNode, Rectangle selected, boolean incoming) {
            int sideX = incoming ? sideNode.x + sideNode.width : sideNode.x;
            int selectedX = incoming ? selected.x : selected.x + selected.width;
            int sideY = sideNode.y + sideNode.height / 2;
            int selectedY = selected.y + selected.height / 2;
            g.setColor(new Color(132, 146, 166));
            g.drawLine(sideX, sideY, selectedX, selectedY);
            int arrowX = incoming ? selectedX : sideX;
            int arrowY = incoming ? selectedY : sideY;
            int direction = incoming ? -1 : 1;
            g.drawLine(arrowX, arrowY, arrowX + direction * 8, arrowY - 5);
            g.drawLine(arrowX, arrowY, arrowX + direction * 8, arrowY + 5);
        }

        private void drawNode(Graphics2D g, Rectangle bounds, String label, Color fill, Color textColor) {
            g.setColor(fill);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setColor(new Color(180, 188, 202));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            g.setColor(textColor);
            drawWrappedNodeText(g, label, bounds);
        }

        private void drawWrappedNodeText(Graphics2D g, String text, Rectangle bounds) {
            FontMetrics metrics = g.getFontMetrics();
            String safeText = text == null ? "" : text;
            List<String> lines = wrapText(safeText, metrics, bounds.width - 12);
            int lineHeight = metrics.getHeight();
            int y = bounds.y + Math.max(metrics.getAscent() + 4, (bounds.height - lines.size() * lineHeight) / 2 + metrics.getAscent());
            for (String line : lines.stream().limit(2).toList()) {
                g.drawString(line, bounds.x + 6, y);
                y += lineHeight;
            }
        }

        private List<String> wrapText(String text, FontMetrics metrics, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) <= maxWidth || line.isEmpty()) {
                    line = new StringBuilder(candidate);
                } else {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }
    }

    private enum PaintMode {
        TILE("Tile"),
        PRIMARY_THEME("Primary Theme"),
        ALTERNATE_THEME("Alt Theme"),
        PLACE_OBJECT("Place Object"),
        ERASE_OBJECT("Erase Object"),
        SET_SPAWN("Set Spawn"),
        PLACE_TRIGGER("Place Trigger"),
        WIRE_TRIGGER("Wire Trigger"),
        PLACE_PREFAB("Place Prefab");

        private final String label;

        PaintMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

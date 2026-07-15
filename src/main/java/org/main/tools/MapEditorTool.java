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
import org.main.core.PlayerStat;
import org.main.core.WeaponType;

import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapEditorTool extends JFrame {
    private static final int DEFAULT_WIDTH = 14;
    private static final int DEFAULT_HEIGHT = 12;
    private static final int MIN_DIMENSION = 3;
    private static final int MAX_DIMENSION = 80;
    private static final String DEFAULT_LIMB_ICON = "assets/images/monster/Ancient/Oct-5-2010/player/hand1/misc/head.png";
    private static final Path CONFIG_RESOURCE_PATH = Path.of("src", "main", "resources", "assets", "configuration.properties");

    private final MapCanvas mapCanvas = new MapCanvas();
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_WIDTH, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_HEIGHT, MIN_DIMENSION, MAX_DIMENSION, 1));
    private final JComboBox<ThemeLibrary> primaryThemeBox = new JComboBox<>(ThemeLibrary.values());
    private final JComboBox<ThemeLibrary> alternateThemeBox = new JComboBox<>(ThemeLibrary.values());
    private final JComboBox<PaintMode> paintModeBox = new JComboBox<>(PaintMode.values());
    private final JComboBox<Library.TileType> tileTypeBox = new JComboBox<>(Library.TileType.values());
    private final JComboBox<PlaceableOption> placeableBox = new JComboBox<>();
    private final JTextField mapNameField = new JTextField("new_map", 14);
    private String pendingTriggerId = "";
    private String wiringTriggerId = "";

    private MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            ThemeLibrary.STONE_WOOD,
            ThemeLibrary.SANDSTONE_GATE
    );

    public MapEditorTool() {
        super("Map Editor");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(980, 720));

        loadSharedContentIntoDesign();
        populatePlaceables();
        alternateThemeBox.setSelectedItem(ThemeLibrary.SANDSTONE_GATE);

        add(createToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(mapCanvas), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton newButton = new JButton("New");
        newButton.addActionListener(event -> createNewMap());
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

        toolbar.addSeparator();
        toolbar.add(new JLabel("Primary"));
        toolbar.add(primaryThemeBox);
        toolbar.add(new JLabel("Alt"));
        toolbar.add(alternateThemeBox);

        toolbar.addSeparator();
        toolbar.add(new JLabel("Mode"));
        toolbar.add(paintModeBox);
        toolbar.add(new JLabel("Tile"));
        toolbar.add(tileTypeBox);
        toolbar.add(new JLabel("Object"));
        toolbar.add(placeableBox);

        toolbar.addSeparator();
        toolbar.add(createCreateMenuButton());
        toolbar.add(createManageMenuButton());
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

        return toolbar;
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
        addMenuItem(menu, "Composite Recipe", this::createCompositeRecipe);
        addMenuItem(menu, "Map Link", this::createMapLink);
        addMenuItem(menu, "Trigger", this::createTrigger);
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
        addMenuItem(menu, "Composite Recipes", this::manageCompositeRecipes);
        addMenuItem(menu, "Abilities", this::manageAbilityConfiguration);
        addMenuItem(menu, "Triggers", this::manageTriggers);
        return menuButton("Manage", menu);
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

    private void populatePlaceables() {
        placeableBox.removeAllItems();
        placeableBox.addItem(new PlaceableOption("None", null, ""));

        for (GatheringNodeLibrary node : GatheringNodeLibrary.values()) {
            if (node.getInteractionId() != null && !node.getInteractionId().isBlank()) {
                placeableBox.addItem(new PlaceableOption(
                        "Gathering: " + node.name(),
                        MapDesignLibrary.PlacementKind.GATHERING_NODE,
                        node.name()
                ));
            }
        }

        for (CraftingNodeLibrary node : CraftingNodeLibrary.values()) {
            placeableBox.addItem(new PlaceableOption(
                    "Crafting: " + node.name(),
                    MapDesignLibrary.PlacementKind.CRAFTING_NODE,
                    node.name()
            ));
        }

        for (GenericNpcLibrary npc : GenericNpcLibrary.values()) {
            placeableBox.addItem(new PlaceableOption("Generic NPC: " + npc.name(), MapDesignLibrary.PlacementKind.GENERIC_NPC, npc.name()));
        }

        for (MainNpcLibrary npc : MainNpcLibrary.values()) {
            placeableBox.addItem(new PlaceableOption("Main NPC: " + npc.name(), MapDesignLibrary.PlacementKind.MAIN_NPC, npc.name()));
        }

        for (MapDesignLibrary.CustomNpc npc : design.customNpcs()) {
            placeableBox.addItem(new PlaceableOption("Custom NPC: " + npc.displayName(), MapDesignLibrary.PlacementKind.CUSTOM_NPC, npc.npcId()));
        }

        for (MapDesignLibrary.CustomGatheringNode node : design.customGatheringNodes()) {
            placeableBox.addItem(new PlaceableOption(
                    "Gathering: " + node.displayName(),
                    MapDesignLibrary.PlacementKind.GATHERING_NODE,
                    node.nodeId()
            ));
        }

        for (ItemLibrary item : ItemLibrary.values()) {
            placeableBox.addItem(new PlaceableOption("Item: " + item.name(), MapDesignLibrary.PlacementKind.ITEM, item.name()));
        }

        for (MapDesignLibrary.CustomItem item : design.customItems()) {
            placeableBox.addItem(new PlaceableOption("Custom Item: " + item.displayName(), MapDesignLibrary.PlacementKind.ITEM, item.itemId()));
        }

        for (MapDesignLibrary.CustomLimb limb : design.customLimbs()) {
            placeableBox.addItem(new PlaceableOption("Custom Limb: " + limb.displayName(), MapDesignLibrary.PlacementKind.ITEM, limb.limbId()));
        }

        for (MapDesignLibrary.CustomMob mob : design.customMobs()) {
            placeableBox.addItem(new PlaceableOption(
                    "Enemy: " + mob.displayName(),
                    MapDesignLibrary.PlacementKind.ENEMY,
                    mob.mobId()
            ));
        }

        for (InteractionLibrary interaction : InteractionLibrary.values()) {
            if (interaction.isPlaceable()) {
                placeableBox.addItem(new PlaceableOption(
                        "Interaction: " + interaction.getDisplayName(),
                        MapDesignLibrary.PlacementKind.INTERACTION,
                        interaction.getInteractionId()
                ));
            }
        }

        for (MapDesignLibrary.AuthoredDialogue dialogue : design.authoredDialogues()) {
            placeableBox.addItem(new PlaceableOption(
                    "Authored NPC: " + dialogue.speakerName(),
                    MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC,
                    dialogue.interactionId()
            ));
        }

        for (MapDesignLibrary.MapPlacement placement : design.placements()) {
            if (placement.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                    && placement.id().startsWith("map_link|")) {
                placeableBox.addItem(new PlaceableOption(
                        "Map Link: " + mapLinkLabel(placement.id()),
                        MapDesignLibrary.PlacementKind.INTERACTION,
                        placement.id()
                ));
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

        JList<MapDesignLibrary.AuthoredQuest> questList = new JList<>(
                design.authoredQuests().toArray(new MapDesignLibrary.AuthoredQuest[0])
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
                "Delete " + selected.displayName() + "? Dialogue actions using it will be cleared.",
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
        JButton browseButton = new JButton("Browse");
        JButton paperDollBrowseButton = new JButton("Browse");
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
        JComboBox<StatTargetOption> statTargetBox = new JComboBox<>(statTargetOptions());
        JSpinner healSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 0 : existing.healAmount(), 0, 1000, 1));
        JSpinner valueSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 10 : existing.baseGoldValue(), 1, 100000, 1));
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
            selectStatTargetOption(statTargetBox, existing.statBonusTarget());
            smithingRecipeBox.setSelected(existing.smithingRecipeEnabled());
        }

        Runnable updateSmithingRecipeControls = () -> {
            GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
            boolean metal = material != null && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            smithingRecipeBox.setEnabled(metal);
            if (!metal) {
                smithingRecipeBox.setSelected(false);
            }
            boolean enabled = metal && smithingRecipeBox.isSelected();
            smithingBarsSpinner.setEnabled(enabled);
            smithingLevelSpinner.setEnabled(enabled);
            smithingXpSpinner.setEnabled(enabled);
            smithingMaterialLabel.setText(metal
                ? "Uses " + RecipeLibrary.smithingMaterialNameFor(material)
                : "Metal materials only");
        };
        Runnable updateWeaponTypeControls = () -> {
            boolean weapon = typeBox.getSelectedItem() == InventorySystem.ItemType.WEAPON;
            weaponTypeLabel.setVisible(weapon);
            weaponTypeBox.setEnabled(weapon);
            weaponTypeBox.setVisible(weapon);
            if (!weapon) {
                weaponTypeBox.setSelectedItem(WeaponType.SWORD);
            }
        };

        browseButton.addActionListener(event -> browsePathInto(iconPathField));
        paperDollBrowseButton.addActionListener(event -> browsePathInto(paperDollOverlayField));
        typeBox.addActionListener(event -> {
            updateWeaponTypeControls.run();
            updateSmithingRecipeControls.run();
        });
        materialBox.addActionListener(event -> updateSmithingRecipeControls.run());
        smithingRecipeBox.addActionListener(event -> updateSmithingRecipeControls.run());
        templateBox.addActionListener(event -> {
            ItemTemplateOption template = (ItemTemplateOption) templateBox.getSelectedItem();
            if (template == null || template.item() == null) {
                return;
            }
            MapDesignLibrary.CustomItem item = template.item();
            nameField.setText(item.displayName() + " Copy");
            iconPathField.setText(item.iconPath());
            paperDollOverlayField.setText(item.paperDollOverlayPath());
            typeBox.setSelectedItem(item.itemType());
            materialBox.setSelectedItem(item.material());
            weaponTypeBox.setSelectedItem(item.weaponType() == WeaponType.NONE ? WeaponType.SWORD : item.weaponType());
            selectStatTargetOption(statTargetBox, item.statBonusTarget());
            healSpinner.setValue(item.healAmount());
            valueSpinner.setValue(item.baseGoldValue());
            smithingRecipeBox.setSelected(item.smithingRecipeEnabled());
            smithingBarsSpinner.setValue(item.smithingRequiredBars());
            smithingLevelSpinner.setValue(item.smithingRequiredLevel());
            smithingXpSpinner.setValue(item.smithingXpReward());
            examineArea.setText(item.examineText());
            updateWeaponTypeControls.run();
            updateSmithingRecipeControls.run();
        });
        updateWeaponTypeControls.run();
        updateSmithingRecipeControls.run();

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        if (existing == null) {
            fields.add(new JLabel("Template"));
            fields.add(templateBox);
        }
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Image"));
        JPanel imagePanel = new JPanel(new BorderLayout(4, 4));
        imagePanel.add(iconPathField, BorderLayout.CENTER);
        imagePanel.add(browseButton, BorderLayout.EAST);
        fields.add(imagePanel);
        fields.add(new JLabel("Paper Doll Sprite"));
        JPanel paperDollPanel = new JPanel(new BorderLayout(4, 4));
        paperDollPanel.add(paperDollOverlayField, BorderLayout.CENTER);
        paperDollPanel.add(paperDollBrowseButton, BorderLayout.EAST);
        fields.add(paperDollPanel);
        fields.add(new JLabel("Type"));
        fields.add(typeBox);
        fields.add(new JLabel("Material"));
        fields.add(materialBox);
        fields.add(weaponTypeLabel);
        fields.add(weaponTypeBox);
        fields.add(new JLabel("Ring Stat"));
        fields.add(statTargetBox);
        fields.add(new JLabel("HP Restore"));
        fields.add(healSpinner);
        fields.add(new JLabel("Base Value"));
        fields.add(valueSpinner);
        fields.add(new JLabel("Smithing Recipe"));
        fields.add(smithingRecipeBox);
        fields.add(new JLabel("Recipe Material"));
        fields.add(smithingMaterialLabel);
        fields.add(new JLabel("Bars Required"));
        fields.add(smithingBarsSpinner);
        fields.add(new JLabel("Smithing Level"));
        fields.add(smithingLevelSpinner);
        fields.add(new JLabel("Smithing XP"));
        fields.add(smithingXpSpinner);
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
        try {
            iconPath = normalizeCustomItemImagePath(iconPathField.getText(), name);
            paperDollOverlayPath = normalizeOptionalCustomItemImagePath(paperDollOverlayField.getText(), name + "_paper_doll");
        } catch (Exception exception) {
            setStatus("Item image failed: " + exception.getMessage());
            return null;
        }

        return new MapDesignLibrary.CustomItem(
                existing == null ? nextCustomItemId(name) : existing.itemId(),
                name,
                (InventorySystem.ItemType) typeBox.getSelectedItem(),
                iconPath,
                paperDollOverlayPath,
                typeBox.getSelectedItem() == InventorySystem.ItemType.WEAPON
                        ? (WeaponType) weaponTypeBox.getSelectedItem()
                        : WeaponType.NONE,
                (GearMaterial) materialBox.getSelectedItem(),
                ((Number) healSpinner.getValue()).intValue(),
                ((Number) valueSpinner.getValue()).intValue(),
                examineArea.getText() == null ? "" : examineArea.getText().trim(),
                ((StatTargetOption) statTargetBox.getSelectedItem()).stat(),
                smithingRecipeBox.isSelected(),
                ((Number) smithingBarsSpinner.getValue()).intValue(),
                ((Number) smithingLevelSpinner.getValue()).intValue(),
                ((Number) smithingXpSpinner.getValue()).intValue()
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
        JCheckBox autoMetalOreBox = new JCheckBox("Auto-create metal ore from stage 0 image");
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
        fields.add(formRow("Metal Helper", autoMetalOreBox));
        fields.add(formRow("Metal", materialBox));
        fields.add(formRow("Smelting", smeltingBox));
        fields.add(formRow("Smelting Level", smeltingLevelSpinner));
        fields.add(formRow("Smelting XP", smeltingXpSpinner));
        fields.add(formRow("Bar Icon", pathFieldPanel(barImageField, barBrowse)));
        fields.add(formRow("Stage / Frame 0", pathFieldPanel(frameOneField, frameOneBrowse)));
        fields.add(formRow("Stage / Frame 1", pathFieldPanel(frameTwoField, frameTwoBrowse)));
        fields.add(formRow("Stage / Frame 2", pathFieldPanel(frameThreeField, frameThreeBrowse)));

        Runnable updateGatheringNodeFields = () -> {
            MapDesignLibrary.GatheringNodeType type = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            skillBox.setSelectedItem(MapDesignLibrary.defaultGatheringSkill(type));
            boolean animated = gatheringNodeUsesAnimation(type);
            frameDurationRow.setVisible(animated);
            frameDurationSpinner.setValue(animated ? 260 : 1000);
            visualScaleSpinner.setValue(animated ? 1.0 : 1.35);
            fields.revalidate();
            fields.repaint();
        };
        typeBox.addActionListener(event -> updateGatheringNodeFields.run());
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
            List<String> frames = List.of(
                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering")
            );
            String smeltOutputItemId = "";
            String outputItemId = lootEntries.isEmpty() ? "" : lootEntries.get(0).itemId();

            if (autoMetalOreBox.isSelected()) {
                GearMaterial material = (GearMaterial) materialBox.getSelectedItem();
                String metalName = material == null ? "Metal" : material.getDisplayName();
                String oreName = metalName + " Ore";
                outputItemId = findItemIdByDisplayName(oreName);
                if (outputItemId.isBlank()) {
                    outputItemId = nextCustomItemId(oreName);
                    design.customItems().add(new MapDesignLibrary.CustomItem(
                            outputItemId,
                            oreName,
                            InventorySystem.ItemType.MISC,
                            frames.get(0),
                            "",
                            WeaponType.NONE,
                            GearMaterial.NONE,
                            0,
                            6,
                            "Raw " + metalName.toLowerCase(java.util.Locale.ROOT) + " ore. Smelt it into a bar at a furnace.",
                            null,
                            false,
                            1,
                            1,
                            0
                    ));
                }
                lootEntries.clear();
                lootEntries.add(new MapDesignLibrary.CustomDropEntry(outputItemId, 1.0));

                if (smeltingBox.isSelected()) {
                    String barName = RecipeLibrary.smithingMaterialNameFor(material);
                    if (barName.isBlank()) {
                        barName = metalName + " Bar";
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
                                WeaponType.NONE,
                                GearMaterial.NONE,
                                0,
                                12,
                                "A " + metalName.toLowerCase(java.util.Locale.ROOT) + " bar ready for smithing.",
                                null,
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
                    (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem(),
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    outputItemId,
                    ((Number) gatherXpSpinner.getValue()).intValue(),
                    smeltOutputItemId,
                    ((Number) smeltingXpSpinner.getValue()).intValue(),
                    frames,
                    ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    ((Number) smeltingLevelSpinner.getValue()).intValue()
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

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Category"));
        fields.add(categoryBox);
        fields.add(new JLabel("Primary Item"));
        fields.add(primaryBox);
        fields.add(new JLabel("Secondary Item"));
        fields.add(secondaryBox);
        fields.add(new JLabel("Output Item"));
        fields.add(outputBox);
        fields.add(new JLabel("Skill"));
        fields.add(skillBox);
        fields.add(new JLabel("Required Level"));
        fields.add(requiredLevelSpinner);
        fields.add(new JLabel("XP Reward"));
        fields.add(xpSpinner);
        fields.add(new JLabel("Primary"));
        fields.add(consumePrimaryBox);
        fields.add(new JLabel("Secondary"));
        fields.add(consumeSecondaryBox);
        fields.add(new JLabel("Smelting"));
        fields.add(smeltingBox);
        fields.add(new JLabel("Smelt Output"));
        fields.add(smeltOutputBox);
        fields.add(new JLabel("Smelting Level"));
        fields.add(smeltingLevelSpinner);
        fields.add(new JLabel("Smelting XP"));
        fields.add(smeltingXpSpinner);

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
                ((Number) smeltingLevelSpinner.getValue()).intValue(),
                ((Number) smeltingXpSpinner.getValue()).intValue()
        );
        design.customCompositeRecipes().add(recipe);
        persistSharedContent("composite recipe");
        setStatus("Created composite recipe " + recipe.displayName() + ".");
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
                        WeaponType.NONE,
                        GearMaterial.NONE,
                        0,
                        6,
                        "Raw " + metalName.toLowerCase(java.util.Locale.ROOT) + " ore. Smelt it into a bar at a furnace.",
                        null,
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
                        WeaponType.NONE,
                        GearMaterial.NONE,
                        0,
                        12,
                        "A " + metalName.toLowerCase(java.util.Locale.ROOT) + " bar ready for smithing.",
                        null,
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
                this::editCustomItem,
                this::deleteCustomItem
        );
    }

    private void manageCustomMobs() {
        manageCustomContent(
                "Enemies",
                design.customMobs(),
                mob -> mob.displayName() + " [" + mob.mobId() + "]",
                this::editCustomMob,
                this::deleteCustomMob
        );
    }

    private void manageCustomNpcs() {
        manageCustomContent(
                "NPCs",
                design.customNpcs(),
                npc -> npc.displayName() + " [" + npc.npcId() + "]",
                this::editCustomNpc,
                this::deleteCustomNpc
        );
    }

    private void manageCustomLimbs() {
        manageCustomContent(
                "Limbs",
                design.customLimbs(),
                limb -> limb.displayName() + " [" + limb.limbId() + "]",
                this::editCustomLimb,
                this::deleteCustomLimb
        );
    }

    private void manageCustomGatheringNodes() {
        manageCustomContent(
                "Gathering Nodes",
                design.customGatheringNodes(),
                node -> node.displayName() + " [" + node.nodeId() + ", " + node.nodeType() + "]",
                this::editCustomGatheringNode,
                this::deleteCustomGatheringNode
        );
    }

    private void manageCompositeRecipes() {
        manageCustomContent(
                "Composite Recipes",
                design.customCompositeRecipes(),
                recipe -> recipe.displayName() + " [" + recipe.recipeId() + ", " + recipe.category() + "]",
                this::editCompositeRecipe,
                this::deleteCompositeRecipe
        );
    }

    private <T> void manageCustomContent(
            String title,
            List<T> entries,
            Function<T, String> labeler,
            Consumer<T> editAction,
            Consumer<T> deleteAction
    ) {
        if (entries.isEmpty()) {
            setStatus("No " + title.toLowerCase() + " to manage.");
            return;
        }

        DefaultListModel<T> model = new DefaultListModel<>();
        for (T entry : entries) {
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
        if (!confirmDelete("item", selected.displayName())) {
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
        if (!confirmDelete("limb", selected.displayName())) {
            return;
        }

        design.customLimbs().remove(selected);
        persistSharedContent("custom limb");
        populatePlaceables();
        setStatus("Deleted custom limb " + selected.displayName() + ".");
    }

    private void deleteCustomGatheringNode(MapDesignLibrary.CustomGatheringNode selected) {
        if (!confirmDelete("gathering node", selected.displayName())) {
            return;
        }

        design.customGatheringNodes().remove(selected);
        design.placements().removeIf(placement ->
                placement.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE
                        && selected.nodeId().equals(placement.id()));
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
        fields.add(formRow("Smelt Output Item Id", smeltOutputField));
        fields.add(formRow("Smelting Level", smeltingLevelSpinner));
        fields.add(formRow("Smelting XP", smeltingXpSpinner));
        fields.add(formRow("Stage / Frame 0", pathFieldPanel(frameOneField, frameOneBrowse)));
        fields.add(formRow("Stage / Frame 1", pathFieldPanel(frameTwoField, frameTwoBrowse)));
        fields.add(formRow("Stage / Frame 2", pathFieldPanel(frameThreeField, frameThreeBrowse)));

        Runnable updateGatheringNodeFields = () -> {
            MapDesignLibrary.GatheringNodeType type = (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem();
            boolean animated = gatheringNodeUsesAnimation(type);
            frameDurationRow.setVisible(animated);
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
            List<String> frames = List.of(
                    normalizeGeneratedImagePath(frameOneField.getText(), safeId(name) + "_stage_0", "gathering"),
                    normalizeGeneratedImagePath(frameTwoField.getText(), safeId(name) + "_stage_1", "gathering"),
                    normalizeGeneratedImagePath(frameThreeField.getText(), safeId(name) + "_stage_2", "gathering")
            );
            MapDesignLibrary.CustomGatheringNode edited = new MapDesignLibrary.CustomGatheringNode(
                    selected.nodeId(),
                    name,
                    (MapDesignLibrary.GatheringNodeType) typeBox.getSelectedItem(),
                    ((Number) requiredLevelSpinner.getValue()).intValue(),
                    lootEntries.get(0).itemId(),
                    ((Number) gatherXpSpinner.getValue()).intValue(),
                    smeltOutputField.getText() == null ? "" : smeltOutputField.getText().trim(),
                    ((Number) smeltingXpSpinner.getValue()).intValue(),
                    frames,
                    ((Number) frameDurationSpinner.getValue()).intValue(),
                    ((Number) visualScaleSpinner.getValue()).doubleValue(),
                    (CharacterSkill) skillBox.getSelectedItem(),
                    new ArrayList<>(lootEntries),
                    ((Number) smeltingLevelSpinner.getValue()).intValue()
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
        if (!confirmDelete("composite recipe", selected.displayName())) {
            return;
        }

        design.customCompositeRecipes().remove(selected);
        persistSharedContent("composite recipe");
        setStatus("Deleted composite recipe " + selected.displayName() + ".");
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

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Name"));
        fields.add(nameField);
        fields.add(new JLabel("Category"));
        fields.add(categoryBox);
        fields.add(new JLabel("Primary Item"));
        fields.add(primaryBox);
        fields.add(new JLabel("Secondary Item"));
        fields.add(secondaryBox);
        fields.add(new JLabel("Output Item"));
        fields.add(outputBox);
        fields.add(new JLabel("Skill"));
        fields.add(skillBox);
        fields.add(new JLabel("Required Level"));
        fields.add(requiredLevelSpinner);
        fields.add(new JLabel("XP Reward"));
        fields.add(xpSpinner);
        fields.add(new JLabel("Primary"));
        fields.add(consumePrimaryBox);
        fields.add(new JLabel("Secondary"));
        fields.add(consumeSecondaryBox);
        fields.add(new JLabel("Smelting"));
        fields.add(smeltingBox);
        fields.add(new JLabel("Smelt Output"));
        fields.add(smeltOutputBox);
        fields.add(new JLabel("Smelting Level"));
        fields.add(smeltingLevelSpinner);
        fields.add(new JLabel("Smelting XP"));
        fields.add(smeltingXpSpinner);

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
                ((Number) smeltingLevelSpinner.getValue()).intValue(),
                ((Number) smeltingXpSpinner.getValue()).intValue()
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
        if (!confirmDelete("enemy", selected.displayName())) {
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
        if (!confirmDelete("NPC", selected.displayName())) {
            return;
        }

        design.customNpcs().remove(selected);
        persistSharedContent("custom NPC");
        populatePlaceables();
        setStatus("Deleted custom NPC " + selected.displayName() + ".");
    }

    private boolean confirmDelete(String type, String name) {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete " + name + "? Existing map placements using this " + type + " may stop resolving.",
                "Delete " + type,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return result == JOptionPane.OK_OPTION;
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
        JSpinner chanceSpinner = new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
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
                item.getWeaponType(),
                item.getMaterial(),
                item.getHealAmount(),
                item.getBaseGoldValue(),
                item.getExamineText(),
                null,
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
        imagePanel.add(pathField, BorderLayout.CENTER);
        imagePanel.add(browseButton, BorderLayout.EAST);
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

    private String framePathAt(MapDesignLibrary.CustomGatheringNode node, int index) {
        if (node == null || node.framePaths() == null || index < 0 || index >= node.framePaths().size()) {
            return "";
        }
        return node.framePaths().get(index);
    }

    private boolean gatheringNodeUsesAnimation(MapDesignLibrary.GatheringNodeType type) {
        return type == MapDesignLibrary.GatheringNodeType.FISHING_SPOT;
    }

    private void browsePathInto(JTextField pathField) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().toPath().toString());
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
            design.triggers().remove(trigger);
            refreshTriggerList(model);
            setStatus("Deleted trigger " + trigger.id() + ".");
            mapCanvas.repaint();
        });

        closeButton.addActionListener(event -> dialog.dispose());
        dialog.setVisible(true);
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
                "Map Editor Authoring Help",
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

        JList<MapDesignLibrary.AuthoredDialogue> dialogueList = new JList<>(
                design.authoredDialogues().toArray(new MapDesignLibrary.AuthoredDialogue[0])
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
                "Delete " + selected.speakerName() + " and remove its placed NPCs?",
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

    private GearMaterial[] metalMaterials() {
        List<GearMaterial> materials = new ArrayList<>();
        for (GearMaterial material : GearMaterial.values()) {
            if (material.getFamily() == GearMaterial.MaterialFamily.METAL) {
                materials.add(material);
            }
        }
        return materials.toArray(new GearMaterial[0]);
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
        design = MapDesignLibrary.createBlank(
                ((Number) widthSpinner.getValue()).intValue(),
                ((Number) heightSpinner.getValue()).intValue(),
                (ThemeLibrary) primaryThemeBox.getSelectedItem(),
                (ThemeLibrary) alternateThemeBox.getSelectedItem()
        );
        loadSharedContentIntoDesign();
        mapNameField.setText(design.displayName());
        populatePlaceables();
        mapCanvas.revalidate();
        mapCanvas.repaint();
        setStatus("Created " + design.displayName() + " " + design.width() + "x" + design.height() + ".");
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
            mapNameField.setText(design.displayName());
            widthSpinner.setValue(design.width());
            heightSpinner.setValue(design.height());
            primaryThemeBox.setSelectedItem(design.primaryTheme());
            alternateThemeBox.setSelectedItem(design.alternateTheme());
            populatePlaceables();
            mapCanvas.revalidate();
            mapCanvas.repaint();
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
                design.customCompositeRecipes(),
                design.triggers(),
                design.spawnX(),
                design.spawnY()
        );
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
        } catch (IOException exception) {
            setStatus("Shared content load failed: " + exception.getMessage());
        }
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
            MapDesignLibrary.saveSharedContent(new MapDesignLibrary.AuthoredContent(
                    design.authoredDialogues(),
                    design.authoredQuests(),
                    design.customItems(),
                    design.customMobs(),
                    design.customLimbs(),
                    design.customNpcs(),
                    design.customGatheringNodes(),
                    design.customCompositeRecipes()
            ));
            return true;
        } catch (IOException exception) {
            setStatus("Shared content save failed: " + exception.getMessage());
            return false;
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
        SwingUtilities.invokeLater(() -> new MapEditorTool().setVisible(true));
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
        private static final int CELL_SIZE = 32;

        private MapCanvas() {
            setBackground(new Color(22, 24, 30));
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    paintAt(event.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    paintAt(event.getPoint());
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(design.width() * CELL_SIZE + 1, design.height() * CELL_SIZE + 1);
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
            g.dispose();
        }

        private void drawCell(Graphics2D g, int x, int y) {
            Rectangle bounds = new Rectangle(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
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
            g.setStroke(new BasicStroke(2f));
            FontMetrics metrics = g.getFontMetrics();
            for (MapDesignLibrary.MapPlacement placement : design.placements()) {
                int x = placement.x() * CELL_SIZE;
                int y = placement.y() * CELL_SIZE;
                g.setColor(placementColor(placement.kind()));
                g.drawOval(x + 6, y + 6, CELL_SIZE - 12, CELL_SIZE - 12);
                String label = placement.kind().name().substring(0, 1);
                g.drawString(label, x + (CELL_SIZE - metrics.stringWidth(label)) / 2, y + 20);
            }
        }

        private void drawTriggers(Graphics2D g) {
            g.setStroke(new BasicStroke(2.4f));
            for (MapDesignLibrary.MapTrigger trigger : design.triggers()) {
                int triggerCenterX = trigger.x() * CELL_SIZE + CELL_SIZE / 2;
                int triggerCenterY = trigger.y() * CELL_SIZE + CELL_SIZE / 2;

                g.setColor(new Color(255, 205, 70));
                g.drawRect(trigger.x() * CELL_SIZE + 5, trigger.y() * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                g.drawString("T", trigger.x() * CELL_SIZE + 10, trigger.y() * CELL_SIZE + 21);

                for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                    int targetCenterX = action.targetX() * CELL_SIZE + CELL_SIZE / 2;
                    int targetCenterY = action.targetY() * CELL_SIZE + CELL_SIZE / 2;
                    g.setColor(new Color(255, 205, 70, 150));
                    g.drawLine(triggerCenterX, triggerCenterY, targetCenterX, targetCenterY);
                    g.setColor(new Color(255, 105, 80));
                    g.drawOval(action.targetX() * CELL_SIZE + 7, action.targetY() * CELL_SIZE + 7, CELL_SIZE - 14, CELL_SIZE - 14);
                }
            }
        }

        private void drawSpawn(Graphics2D g) {
            int x = design.spawnX() * CELL_SIZE;
            int y = design.spawnY() * CELL_SIZE;
            g.setColor(new Color(80, 220, 255));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(x + 5, y + 5, CELL_SIZE - 10, CELL_SIZE - 10);
            g.drawLine(x + CELL_SIZE / 2, y + 8, x + CELL_SIZE / 2, y + CELL_SIZE - 8);
            g.drawLine(x + 8, y + CELL_SIZE / 2, x + CELL_SIZE - 8, y + CELL_SIZE / 2);
        }

        private void paintAt(Point point) {
            int x = point.x / CELL_SIZE;
            int y = point.y / CELL_SIZE;

            if (x < 0 || y < 0 || x >= design.width() || y >= design.height()) {
                return;
            }

            PaintMode mode = (PaintMode) paintModeBox.getSelectedItem();
            if (mode == null) {
                return;
            }

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
            }

            repaint();
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
            setStatus("Placed " + option.label() + " at " + x + "," + y + ".");
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

    private record PlaceableOption(String label, MapDesignLibrary.PlacementKind kind, String id) {
        @Override
        public String toString() {
            return label;
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

    private enum PaintMode {
        TILE("Tile"),
        PRIMARY_THEME("Primary Theme"),
        ALTERNATE_THEME("Alt Theme"),
        PLACE_OBJECT("Place Object"),
        ERASE_OBJECT("Erase Object"),
        SET_SPAWN("Set Spawn"),
        PLACE_TRIGGER("Place Trigger"),
        WIRE_TRIGGER("Wire Trigger");

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

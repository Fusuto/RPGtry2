package org.main.tools;

import org.main.content.CharacterModelDefinition;
import org.main.content.FirstPersonCombatLibrary;
import org.main.content.FirstPersonViewmodelValidator;
import org.main.core.EquipmentViewModelProfile;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;
import org.main.experimental.CharacterAnimationMetadataResolver;
import org.main.experimental.FirstPersonAnimationRuntime;
import org.main.experimental.LwjglSkinnedModel;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Unified, draft-driven authoring workspace for first-person combat viewmodels. */
final class FirstPersonViewmodelEditorWorkspace extends JDialog {
    record ItemOption(
            String id,
            String displayName,
            InventorySystem.ItemType itemType,
            WeaponType weaponType,
            boolean twoHanded,
            String modelPath,
            EquipmentViewModelProfile legacyPose
    ) {
        ItemOption {
            id = FirstPersonCombatLibrary.normalizeId(id);
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            itemType = itemType == null ? InventorySystem.ItemType.WEAPON : itemType;
            weaponType = weaponType == null ? WeaponType.NONE : weaponType;
            modelPath = modelPath == null ? "" : modelPath;
            legacyPose = legacyPose == null ? EquipmentViewModelProfile.defaults() : legacyPose;
        }

        @Override
        public String toString() {
            return displayName + " [" + id + "]";
        }
    }

    private enum Kind { RIG, ANIMATION_SET, ITEM_PROFILE }

    private record CatalogEntry(String id, String label) {
        @Override public String toString() { return label + " [" + id + "]"; }
    }

    private record ModelInspection(
            List<String> nodes,
            List<String> meshes,
            List<String> clips,
            String signature,
            List<String> diagnostics
    ) {
    }

    private record ArmInspection(ModelInspection arm, String baseSignature) { }

    private final Consumer<JTextField> modelBrowser;
    private final Consumer<Map<String, String>> appliedCallback;
    private final LinkedHashMap<String, String> rigRenames = new LinkedHashMap<>();
    private final List<ItemOption> items;
    private final Map<String, ItemOption> itemsById = new LinkedHashMap<>();
    private FirstPersonCombatLibrary.Content committed;
    private volatile FirstPersonCombatLibrary.Content draft;
    private boolean dirty;
    private boolean loading;
    private Kind activeKind = Kind.RIG;
    private String loadedRigId = "";
    private String loadedSetId = "";
    private String loadedProfileId = "";

    private final JTextField searchField = new JTextField(18);
    private final JTabbedPane catalogTabs = new JTabbedPane();
    private final DefaultListModel<CatalogEntry> rigListModel = new DefaultListModel<>();
    private final DefaultListModel<CatalogEntry> setListModel = new DefaultListModel<>();
    private final DefaultListModel<CatalogEntry> profileListModel = new DefaultListModel<>();
    private final JList<CatalogEntry> rigList = list(rigListModel);
    private final JList<CatalogEntry> setList = list(setListModel);
    private final JList<CatalogEntry> profileList = list(profileListModel);
    private final JPanel inspectorCards = new JPanel(new CardLayout());
    private final JTextArea diagnosticsArea = new JTextArea(7, 80);
    private final JLabel stateLabel = new JLabel("Ready");

    private final RigForm rigForm = new RigForm();
    private final AnimationSetForm animationForm = new AnimationSetForm();
    private final ProfileForm profileForm = new ProfileForm();
    private final JComboBox<ItemOption> previewItem = new JComboBox<>();
    private final EquipmentCombinationPreviewPanel preview;

    FirstPersonViewmodelEditorWorkspace(
            Window owner,
            List<ItemOption> items,
            Consumer<JTextField> modelBrowser,
            Consumer<Map<String, String>> appliedCallback
    ) {
        super(owner, "First-Person Viewmodels", ModalityType.APPLICATION_MODAL);
        this.items = items == null ? List.of() : List.copyOf(items);
        this.items.forEach(item -> itemsById.put(item.id(), item));
        this.modelBrowser = modelBrowser == null ? ignored -> { } : modelBrowser;
        this.appliedCallback = appliedCallback == null ? ignored -> { } : appliedCallback;
        committed = FirstPersonCombatLibrary.loadFresh();
        draft = committed;
        this.items.forEach(previewItem::addItem);

        preview = new EquipmentCombinationPreviewPanel(
                this::previewModelPath,
                this::previewLegacyPose,
                this::previewItemType,
                this::previewTwoHanded,
                this::previewProfile,
                this::previewWeaponType,
                () -> draft,
                this::previewRigId,
                this::previewCameraFraming);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setPreferredSize(new Dimension(1480, 900));
        setLayout(new BorderLayout(7, 7));
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
        installListeners();
        refreshCatalogs();
        selectInitialContent();
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeWorkspace(); }
        });
    }

    private JComponent buildToolbar() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        left.add(new JLabel("Search"));
        left.add(searchField);
        JButton add = new JButton("Add");
        JButton duplicate = new JButton("Duplicate");
        JButton remove = new JButton("Delete");
        add.addActionListener(event -> addCurrent());
        duplicate.addActionListener(event -> duplicateCurrent());
        remove.addActionListener(event -> deleteCurrent());
        left.add(add);
        left.add(duplicate);
        left.add(remove);
        panel.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        right.add(new JLabel("Preview Loadout"));
        previewItem.setPreferredSize(new Dimension(260, 26));
        right.add(previewItem);
        JButton refresh = new JButton("Refresh Draft Preview");
        refresh.addActionListener(event -> {
            commitCurrentEditor();
            preview.reloadPreview();
            runValidation(false);
        });
        right.add(refresh);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildBody() {
        catalogTabs.addTab("Rigs", new JScrollPane(rigList));
        catalogTabs.addTab("Motion Sets", new JScrollPane(setList));
        catalogTabs.addTab("Equipment", new JScrollPane(profileList));
        catalogTabs.setPreferredSize(new Dimension(255, 650));

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setBorder(BorderFactory.createTitledBorder("Runtime Viewport"));
        center.add(preview, BorderLayout.CENTER);

        inspectorCards.add(new JScrollPane(rigForm.panel), Kind.RIG.name());
        inspectorCards.add(new JScrollPane(animationForm.panel), Kind.ANIMATION_SET.name());
        inspectorCards.add(new JScrollPane(profileForm.panel), Kind.ITEM_PROFILE.name());
        inspectorCards.setPreferredSize(new Dimension(410, 650));
        inspectorCards.setBorder(BorderFactory.createTitledBorder("Inspector"));

        JSplitPane centerRight = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, center, inspectorCards);
        centerRight.setResizeWeight(0.68);
        centerRight.setDividerLocation(780);
        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalogTabs, centerRight);
        all.setResizeWeight(0.0);
        all.setDividerLocation(260);
        return all;
    }

    private JComponent buildBottom() {
        diagnosticsArea.setEditable(false);
        diagnosticsArea.setLineWrap(true);
        diagnosticsArea.setWrapStyleWord(true);
        diagnosticsArea.setBackground(new Color(30, 32, 36));
        diagnosticsArea.setForeground(new Color(235, 235, 235));
        JScrollPane diagnostics = new JScrollPane(diagnosticsArea);
        diagnostics.setBorder(BorderFactory.createTitledBorder("Diagnostics"));
        diagnostics.setPreferredSize(new Dimension(900, 145));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        JButton validate = new JButton("Validate");
        JButton revert = new JButton("Revert");
        JButton apply = new JButton("Apply");
        JButton close = new JButton("Close");
        validate.addActionListener(event -> {
            commitCurrentEditor();
            runValidation(false);
        });
        revert.addActionListener(event -> revertDraft());
        apply.addActionListener(event -> applyDraft());
        close.addActionListener(event -> closeWorkspace());
        actions.add(stateLabel);
        actions.add(validate);
        actions.add(revert);
        actions.add(apply);
        actions.add(close);

        JPanel panel = new JPanel(new BorderLayout(5, 4));
        panel.add(diagnostics, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void installListeners() {
        searchField.getDocument().addDocumentListener(documentListener(this::refreshCatalogs));
        rigList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !loading) selectRig();
        });
        setList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !loading) selectSet();
        });
        profileList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !loading) selectProfile();
        });
        catalogTabs.addChangeListener(event -> {
            if (loading) return;
            commitCurrentEditor();
            activeKind = switch (catalogTabs.getSelectedIndex()) {
                case 1 -> Kind.ANIMATION_SET;
                case 2 -> Kind.ITEM_PROFILE;
                default -> Kind.RIG;
            };
            showInspector(activeKind);
            ensureSelection(activeKind);
        });
        previewItem.addActionListener(event -> preview.reloadPreview());
    }

    private void selectInitialContent() {
        if (!draft.rigs().isEmpty()) {
            selectEntry(rigList, draft.defaultRigId());
        }
        showInspector(Kind.RIG);
        SwingUtilities.invokeLater(preview::reloadPreview);
    }

    private void selectRig() {
        commitCurrentEditor();
        CatalogEntry entry = rigList.getSelectedValue();
        if (entry == null) return;
        loadedRigId = entry.id();
        activeKind = Kind.RIG;
        rigForm.load(draft.rigs().get(entry.id()));
        showInspector(activeKind);
    }

    private void selectSet() {
        commitCurrentEditor();
        CatalogEntry entry = setList.getSelectedValue();
        if (entry == null) return;
        loadedSetId = entry.id();
        activeKind = Kind.ANIMATION_SET;
        animationForm.load(draft.animationSets().get(entry.id()));
        showInspector(activeKind);
        refreshSelectedAnimationPreview(true);
    }

    private void selectProfile() {
        commitCurrentEditor();
        CatalogEntry entry = profileList.getSelectedValue();
        if (entry == null) return;
        loadedProfileId = entry.id();
        activeKind = Kind.ITEM_PROFILE;
        profileForm.load(draft.itemProfiles().get(entry.id()));
        showInspector(activeKind);
    }

    private void showInspector(Kind kind) {
        ((CardLayout) inspectorCards.getLayout()).show(inspectorCards, kind.name());
    }

    private void ensureSelection(Kind kind) {
        JList<CatalogEntry> list = listFor(kind);
        if (list.getSelectedIndex() < 0 && list.getModel().getSize() > 0) {
            list.setSelectedIndex(0);
        }
    }

    private void commitCurrentEditor() {
        if (loading) return;
        switch (activeKind) {
            case RIG -> rigForm.commit();
            case ANIMATION_SET -> animationForm.commit();
            case ITEM_PROFILE -> profileForm.commit();
        }
    }

    private void addCurrent() {
        commitCurrentEditor();
        switch (activeKind) {
            case RIG -> {
                String id = uniqueId("new_rig", draft.rigs().keySet());
                draft = draft.withRig(FirstPersonCombatLibrary.newRig(id, "New Rig"));
                loadedRigId = id;
                markDirty();
                refreshCatalogs();
                selectEntry(rigList, id);
            }
            case ANIMATION_SET -> {
                String id = uniqueId("new_motion_set", draft.animationSets().keySet());
                FirstPersonCombatLibrary.AnimationSet set = new FirstPersonCombatLibrary.AnimationSet(
                        id, "New Motion Set", draft.defaultRigId(), Map.of());
                draft = draft.withAnimationSet(set);
                loadedSetId = id;
                markDirty();
                refreshCatalogs();
                selectEntry(setList, id);
            }
            case ITEM_PROFILE -> addProfileForSelectedItem();
        }
    }

    private void addProfileForSelectedItem() {
        ItemOption selected = (ItemOption) previewItem.getSelectedItem();
        if (selected == null && !items.isEmpty()) selected = items.getFirst();
        if (selected == null) {
            state("No weapon, shield, or chest item is available for a profile.", true);
            return;
        }
        if (draft.itemProfiles().containsKey(selected.id())) {
            selectEntry(profileList, selected.id());
            state("That item already has a profile.", true);
            return;
        }
        FirstPersonCombatLibrary.ItemProfile profile = new FirstPersonCombatLibrary.ItemProfile(
                selected.id(), draft.defaultRigId(), FirstPersonCombatLibrary.WieldHand.RIGHT,
                FirstPersonCombatLibrary.defaultSetId(selected.weaponType()),
                FirstPersonCombatLibrary.ItemProfile.socketDefaults(),
                0, 0, 0, "", "",
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of());
        draft = draft.withItemProfile(profile);
        loadedProfileId = profile.itemId();
        markDirty();
        refreshCatalogs();
        selectEntry(profileList, profile.itemId());
    }

    private void duplicateCurrent() {
        commitCurrentEditor();
        switch (activeKind) {
            case RIG -> {
                FirstPersonCombatLibrary.RigDefinition source = draft.rigs().get(loadedRigId);
                if (source == null) return;
                String id = uniqueId(source.rigId() + "_copy", draft.rigs().keySet());
                FirstPersonCombatLibrary.RigDefinition copy = copyRig(source, id,
                        source.displayName() + " Copy");
                draft = draft.withRig(copy);
                markDirty(); refreshCatalogs(); selectEntry(rigList, id);
            }
            case ANIMATION_SET -> {
                FirstPersonCombatLibrary.AnimationSet source = draft.animationSets().get(loadedSetId);
                if (source == null) return;
                String id = uniqueId(source.id() + "_copy", draft.animationSets().keySet());
                draft = draft.withAnimationSet(new FirstPersonCombatLibrary.AnimationSet(
                        id, source.displayName() + " Copy", source.rigId(), source.bindings()));
                markDirty(); refreshCatalogs(); selectEntry(setList, id);
            }
            case ITEM_PROFILE -> state("Equipment profiles are keyed by item and cannot be duplicated.", true);
        }
    }

    private void deleteCurrent() {
        commitCurrentEditor();
        switch (activeKind) {
            case RIG -> deleteRig();
            case ANIMATION_SET -> deleteSet();
            case ITEM_PROFILE -> deleteProfile();
        }
    }

    private void deleteRig() {
        if (draft.rigs().size() <= 1) {
            state("The default catalog must retain at least one rig.", true);
            return;
        }
        FirstPersonCombatLibrary.RigDefinition selected = draft.rigs().get(loadedRigId);
        if (selected == null || !confirm("Delete rig " + selected.displayName()
                + "? References will be reassigned to the default remaining rig.")) return;
        String deletedId = loadedRigId;
        LinkedHashMap<String, FirstPersonCombatLibrary.RigDefinition> rigs = new LinkedHashMap<>(draft.rigs());
        rigs.remove(loadedRigId);
        String replacement = draft.defaultRigId().equals(loadedRigId)
                ? rigs.keySet().iterator().next() : draft.defaultRigId();
        LinkedHashMap<String, FirstPersonCombatLibrary.AnimationSet> sets = new LinkedHashMap<>();
        draft.animationSets().forEach((id, set) -> sets.put(id,
                set.rigId().equals(loadedRigId)
                        ? new FirstPersonCombatLibrary.AnimationSet(set.id(), set.displayName(), replacement, set.bindings())
                        : set));
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles = new LinkedHashMap<>();
        draft.itemProfiles().forEach((id, profile) -> profiles.put(id,
                profile.rigId().equals(loadedRigId) ? withRig(profile, replacement) : profile));
        draft = new FirstPersonCombatLibrary.Content(replacement, rigs, sets, profiles, draft.weaponDefaults());
        recordRigRename(deletedId, replacement);
        loadedRigId = "";
        markDirty(); refreshCatalogs(); ensureSelection(Kind.RIG);
    }

    private void deleteSet() {
        FirstPersonCombatLibrary.AnimationSet selected = draft.animationSets().get(loadedSetId);
        if (selected == null || !confirm("Delete motion set " + selected.displayName()
                + "? Equipment using it will fall back to its weapon default.")) return;
        LinkedHashMap<String, FirstPersonCombatLibrary.AnimationSet> sets = new LinkedHashMap<>(draft.animationSets());
        sets.remove(loadedSetId);
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles = new LinkedHashMap<>();
        draft.itemProfiles().forEach((id, profile) -> profiles.put(id,
                profile.animationSetId().equals(loadedSetId) ? withAnimationSet(profile, "") : profile));
        EnumMap<WeaponType, String> defaults = new EnumMap<>(WeaponType.class);
        draft.weaponDefaults().forEach((type, id) -> defaults.put(type,
                id.equals(loadedSetId) ? "" : id));
        draft = new FirstPersonCombatLibrary.Content(
                draft.defaultRigId(), draft.rigs(), sets, profiles, defaults);
        loadedSetId = "";
        markDirty(); refreshCatalogs(); ensureSelection(Kind.ANIMATION_SET);
    }

    private void deleteProfile() {
        if (loadedProfileId.isBlank() || !confirm("Delete the equipment profile for "
                + itemLabel(loadedProfileId) + "?")) return;
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles =
                new LinkedHashMap<>(draft.itemProfiles());
        profiles.remove(loadedProfileId);
        draft = new FirstPersonCombatLibrary.Content(
                draft.defaultRigId(), draft.rigs(), draft.animationSets(), profiles,
                draft.weaponDefaults());
        loadedProfileId = "";
        markDirty(); refreshCatalogs(); ensureSelection(Kind.ITEM_PROFILE);
    }

    private void refreshCatalogs() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String rigSelection = selectedId(rigList);
        String setSelection = selectedId(setList);
        String profileSelection = selectedId(profileList);
        loading = true;
        rigListModel.clear();
        draft.rigs().values().stream()
                .filter(rig -> matches(search, rig.rigId(), rig.displayName()))
                .forEach(rig -> rigListModel.addElement(new CatalogEntry(rig.rigId(), rig.displayName())));
        setListModel.clear();
        draft.animationSets().values().stream()
                .filter(set -> matches(search, set.id(), set.displayName(), set.rigId()))
                .forEach(set -> setListModel.addElement(new CatalogEntry(set.id(), set.displayName())));
        profileListModel.clear();
        draft.itemProfiles().values().stream()
                .filter(profile -> matches(search, profile.itemId(), itemLabel(profile.itemId()), profile.rigId()))
                .forEach(profile -> profileListModel.addElement(
                        new CatalogEntry(profile.itemId(), itemLabel(profile.itemId()))));
        restoreSelection(rigList, rigSelection.isBlank() ? loadedRigId : rigSelection);
        restoreSelection(setList, setSelection.isBlank() ? loadedSetId : setSelection);
        restoreSelection(profileList, profileSelection.isBlank() ? loadedProfileId : profileSelection);
        rigForm.refreshReferences();
        animationForm.refreshReferences();
        profileForm.refreshReferences();
        loading = false;
    }

    private List<FirstPersonCombatLibrary.Diagnostic> runValidation(boolean applying) {
        Set<String> itemIds = new LinkedHashSet<>(itemsById.keySet());
        List<FirstPersonCombatLibrary.Diagnostic> issues =
                FirstPersonViewmodelValidator.validate(draft, itemIds);
        StringBuilder text = new StringBuilder();
        if (issues.isEmpty()) text.append("No diagnostics. The catalog is ready to apply.");
        for (FirstPersonCombatLibrary.Diagnostic issue : issues) {
            text.append(issue.severity()).append(" — ")
                    .append(issue.ownerId()).append(": ")
                    .append(issue.message()).append('\n');
        }
        diagnosticsArea.setText(text.toString().trim());
        long errors = issues.stream().filter(issue ->
                issue.severity() == FirstPersonCombatLibrary.Diagnostic.Severity.ERROR).count();
        long warnings = issues.stream().filter(issue ->
                issue.severity() == FirstPersonCombatLibrary.Diagnostic.Severity.WARNING).count();
        stateLabel.setText(errors + " error(s), " + warnings + " warning(s)"
                + (dirty ? " — Unsaved draft" : ""));
        if (applying && errors > 0) {
            JOptionPane.showMessageDialog(this,
                    "The catalog has " + errors + " error(s). Fix them before applying.",
                    "First-Person Validation", JOptionPane.ERROR_MESSAGE);
        }
        return issues;
    }

    private void applyDraft() {
        commitCurrentEditor();
        List<FirstPersonCombatLibrary.Diagnostic> issues = runValidation(true);
        if (issues.stream().anyMatch(issue ->
                issue.severity() == FirstPersonCombatLibrary.Diagnostic.Severity.ERROR)) return;
        long warnings = issues.stream().filter(issue ->
                issue.severity() == FirstPersonCombatLibrary.Diagnostic.Severity.WARNING).count();
        if (warnings > 0 && JOptionPane.showConfirmDialog(
                this,
                "Apply the catalog with " + warnings + " warning(s)?",
                "Confirm First-Person Catalog",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            FirstPersonCombatLibrary.save(FirstPersonCombatLibrary.RESOURCE_PATH, draft);
            FirstPersonCombatLibrary.install(draft);
            FirstPersonAnimationRuntime.clearCaches();
            CharacterAnimationMetadataResolver.clear();
            LwjglSkinnedModel.clearSharedCache();
            committed = draft;
            dirty = false;
            appliedCallback.accept(Map.copyOf(rigRenames));
            rigRenames.clear();
            state("Applied the complete first-person catalog.", false);
            runValidation(false);
            preview.reloadPreview();
        } catch (IOException exception) {
            state("Catalog save failed: " + exception.getMessage(), true);
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "First-Person Save Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void revertDraft() {
        commitCurrentEditor();
        if (dirty && !confirm("Discard every unapplied first-person change?")) return;
        draft = committed;
        rigRenames.clear();
        dirty = false;
        loadedRigId = loadedSetId = loadedProfileId = "";
        refreshCatalogs();
        selectInitialContent();
        runValidation(false);
        state("Reverted to the last applied catalog.", false);
    }

    private void closeWorkspace() {
        commitCurrentEditor();
        if (dirty && !confirm("Close and discard unapplied first-person changes?")) return;
        dispose();
    }

    private String previewModelPath() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        return item == null ? "" : item.modelPath();
    }

    private EquipmentViewModelProfile previewLegacyPose() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        return item == null ? EquipmentViewModelProfile.defaults() : item.legacyPose();
    }

    private InventorySystem.ItemType previewItemType() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        return item == null ? InventorySystem.ItemType.WEAPON : item.itemType();
    }

    private WeaponType previewWeaponType() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        return item == null ? WeaponType.NONE : item.weaponType();
    }

    private boolean previewTwoHanded() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        return item != null && item.twoHanded();
    }

    private String previewRigId() {
        if (activeKind == Kind.ANIMATION_SET) {
            FirstPersonCombatLibrary.AnimationSet set = draft.animationSets().get(loadedSetId);
            if (set != null && !set.rigId().isBlank()) return set.rigId();
        }
        FirstPersonCombatLibrary.ItemProfile profile = previewProfile();
        if (profile != null && !profile.rigId().isBlank()) return profile.rigId();
        if (!loadedRigId.isBlank()) return loadedRigId;
        return draft.defaultRigId();
    }

    private FirstPersonCombatLibrary.ItemProfile previewProfile() {
        ItemOption item = (ItemOption) previewItem.getSelectedItem();
        FirstPersonCombatLibrary.ItemProfile stored = item == null
                ? null : draft.itemProfiles().get(item.id());
        if (activeKind == Kind.ANIMATION_SET && !loadedSetId.isBlank()) {
            FirstPersonCombatLibrary.AnimationSet selectedSet = draft.animationSets().get(loadedSetId);
            String rigId = selectedSet == null || selectedSet.rigId().isBlank()
                    ? draft.defaultRigId() : selectedSet.rigId();
            FirstPersonCombatLibrary.ItemProfile base = stored == null
                    ? new FirstPersonCombatLibrary.ItemProfile(
                    item == null ? "preview" : item.id(), rigId,
                    FirstPersonCombatLibrary.WieldHand.RIGHT, loadedSetId,
                    FirstPersonCombatLibrary.ItemProfile.socketDefaults(),
                    0, 0, 0, "", "",
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                    FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of())
                    : stored;
            FirstPersonCombatLibrary.WieldHand hand = animationForm.previewHand(base.wieldHand());
            return new FirstPersonCombatLibrary.ItemProfile(
                    base.itemId(), rigId, hand, loadedSetId,
                    base.socketTransform(), base.secondaryGripX(), base.secondaryGripY(),
                    base.secondaryGripZ(), base.leftArmorPath(), base.rightArmorPath(),
                    base.leftCoverage(), base.rightCoverage(), Map.of());
        }
        if (stored != null) return stored;
        String rigId = !loadedRigId.isBlank() && draft.rigs().containsKey(loadedRigId)
                ? loadedRigId : draft.defaultRigId();
        String setId = !loadedSetId.isBlank() && draft.animationSets().containsKey(loadedSetId)
                ? loadedSetId : "";
        return new FirstPersonCombatLibrary.ItemProfile(
                item == null ? "preview" : item.id(), rigId,
                FirstPersonCombatLibrary.WieldHand.RIGHT, setId,
                FirstPersonCombatLibrary.ItemProfile.socketDefaults(),
                0, 0, 0, "", "",
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY,
                FirstPersonCombatLibrary.ArmCoverage.OVERLAY, Map.of());
    }

    private FirstPersonCombatLibrary.CameraFraming previewCameraFraming() {
        return activeKind == Kind.ANIMATION_SET
                ? animationForm.currentCameraFraming()
                : null;
    }

    private void refreshSelectedAnimationPreview(boolean reloadModel) {
        if (preview == null || activeKind != Kind.ANIMATION_SET
                || animationForm.loadedSlot == null) return;
        preview.selectAnimationSlot(animationForm.loadedSlot);
        if (reloadModel) preview.reloadPreview();
        else preview.refreshPose();
    }

    private final class RigForm {
        final JPanel panel = formPanel();
        final JTextField id = new JTextField(22);
        final JTextField name = new JTextField(22);
        final JTextField model = new JTextField(27);
        final JTextField leftArm = new JTextField(27);
        final JTextField rightArm = new JTextField(27);
        final JComboBox<String> leftShoulder = editableCombo();
        final JComboBox<String> leftElbow = editableCombo();
        final JComboBox<String> leftHand = editableCombo();
        final JComboBox<String> rightShoulder = editableCombo();
        final JComboBox<String> rightElbow = editableCombo();
        final JComboBox<String> rightHand = editableCombo();
        final JComboBox<String> cameraAnchor = editableCombo();
        final JList<String> leftMeshes = new JList<>();
        final JList<String> rightMeshes = new JList<>();
        final JSpinner px = decimal(0, -10, 10, 0.01);
        final JSpinner py = decimal(0, -10, 10, 0.01);
        final JSpinner pz = decimal(-0.75, -10, 10, 0.01);
        final JSpinner rx = decimal(0, -360, 360, 1);
        final JSpinner ry = decimal(0, -360, 360, 1);
        final JSpinner rz = decimal(0, -360, 360, 1);
        final JSpinner scale = decimal(1, 0.001, 100, 0.01);
        final JSpinner fov = decimal(70, 30, 120, 1);
        final JSpinner near = decimal(0.05, 0.001, 1, 0.005);
        final JSpinner crossfade = integer(120, 0, 2000, 10);
        Map<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> fallbacks = Map.of();

        RigForm() {
            row(panel, "Stable ID", id);
            row(panel, "Display Name", name);
            row(panel, "Skeleton / Base Model", browseRow(model));
            JButton inspect = new JButton("Inspect Model / Auto Detect");
            inspect.addActionListener(event -> inspectRigModel());
            row(panel, "Model Metadata", inspect);
            row(panel, "Default Left Arm", armPathRow(leftArm, true));
            row(panel, "Default Right Arm", armPathRow(rightArm, false));
            row(panel, "Left Shoulder / Elbow / Hand", compact(leftShoulder, leftElbow, leftHand));
            row(panel, "Right Shoulder / Elbow / Hand", compact(rightShoulder, rightElbow, rightHand));
            row(panel, "Camera Anchor Bone", cameraAnchor);
            leftMeshes.setVisibleRowCount(5);
            leftMeshes.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            rightMeshes.setVisibleRowCount(5);
            rightMeshes.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            row(panel, "Visible Left-Arm Meshes", new JScrollPane(leftMeshes));
            row(panel, "Visible Right-Arm Meshes", new JScrollPane(rightMeshes));
            row(panel, "Root Position X / Y / Z", compact(px, py, pz));
            row(panel, "Root Rotation X / Y / Z", compact(rx, ry, rz));
            row(panel, "Root Scale", scale);
            row(panel, "Field of View", fov);
            row(panel, "Near Clip Plane", near);
            row(panel, "Crossfade (ms)", crossfade);
            JButton editFallbacks = new JButton("Edit Rig Fallback Clips...");
            editFallbacks.addActionListener(event -> {
                Map<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> edited =
                        editOverrides(fallbacks);
                if (edited != null) {
                    fallbacks = edited;
                    markDirty();
                }
            });
            row(panel, "Fallback Animations", editFallbacks);
            JButton makeDefault = new JButton("Make Selected Rig Default");
            makeDefault.addActionListener(event -> {
                commit();
                if (draft.rigs().containsKey(loadedRigId)) {
                    draft = new FirstPersonCombatLibrary.Content(
                            loadedRigId, draft.rigs(), draft.animationSets(),
                            draft.itemProfiles(), draft.weaponDefaults());
                    markDirty(); refreshCatalogs();
                }
            });
            row(panel, "Catalog Default", makeDefault);
        }

        void load(FirstPersonCombatLibrary.RigDefinition rig) {
            if (rig == null) return;
            loading = true;
            id.setText(rig.rigId()); name.setText(rig.displayName()); model.setText(rig.modelPath());
            leftArm.setText(rig.defaultLeftArmPath()); rightArm.setText(rig.defaultRightArmPath());
            setCombo(leftShoulder, rig.leftShoulderBone()); setCombo(leftElbow, rig.leftElbowBone());
            setCombo(leftHand, rig.leftHandBone()); setCombo(rightShoulder, rig.rightShoulderBone());
            setCombo(rightElbow, rig.rightElbowBone()); setCombo(rightHand, rig.rightHandBone());
            setCombo(cameraAnchor, rig.cameraAnchorBone());
            setMeshModel(leftMeshes, new ArrayList<>(rig.leftVisibleMeshes()), rig.leftVisibleMeshes());
            setMeshModel(rightMeshes, new ArrayList<>(rig.rightVisibleMeshes()), rig.rightVisibleMeshes());
            px.setValue(rig.positionX()); py.setValue(rig.positionY()); pz.setValue(rig.positionZ());
            rx.setValue(rig.rotationX()); ry.setValue(rig.rotationY()); rz.setValue(rig.rotationZ());
            scale.setValue(rig.scale()); fov.setValue(rig.fieldOfViewDegrees()); near.setValue(rig.nearPlane());
            crossfade.setValue(rig.crossfadeMs()); fallbacks = rig.fallbackBindings();
            loading = false;
            if (!rig.modelPath().isBlank()) inspectRigModel();
        }

        void commit() {
            if (loading || loadedRigId.isBlank() || !draft.rigs().containsKey(loadedRigId)) return;
            String requestedId = FirstPersonCombatLibrary.normalizeId(id.getText());
            if (requestedId.isBlank()) requestedId = loadedRigId;
            if (!requestedId.equals(loadedRigId) && draft.rigs().containsKey(requestedId)) {
                state("Rig ID " + requestedId + " already exists.", true);
                id.setText(loadedRigId);
                requestedId = loadedRigId;
            }
            FirstPersonCombatLibrary.RigDefinition updated = new FirstPersonCombatLibrary.RigDefinition(
                    requestedId, name.getText(), model.getText(), leftArm.getText(), rightArm.getText(),
                    comboText(leftShoulder), comboText(leftElbow), comboText(leftHand),
                    comboText(rightShoulder), comboText(rightElbow), comboText(rightHand),
                    comboText(cameraAnchor),
                    new LinkedHashSet<>(leftMeshes.getSelectedValuesList()),
                    new LinkedHashSet<>(rightMeshes.getSelectedValuesList()),
                    value(px), value(py), value(pz), value(rx), value(ry), value(rz), value(scale),
                    value(fov), value(near), ((Number) crossfade.getValue()).intValue(), fallbacks);
            replaceRig(loadedRigId, updated);
            loadedRigId = requestedId;
        }

        void refreshReferences() { }

        private JPanel armPathRow(JTextField field, boolean left) {
            JPanel result = new JPanel(new BorderLayout(4, 0));
            result.add(browseRow(field), BorderLayout.CENTER);
            JButton inspect = new JButton("Inspect");
            inspect.addActionListener(event -> inspectArmModel(left));
            result.add(inspect, BorderLayout.EAST);
            return result;
        }

        private void inspectArmModel(boolean left) {
            JTextField field = left ? leftArm : rightArm;
            String path = field.getText().trim();
            if (path.isBlank()) {
                state("Choose an arm model before inspecting it.", true);
                return;
            }
            String inspectedRigId = loadedRigId;
            String basePath = model.getText().trim();
            state("Inspecting " + (left ? "left" : "right") + " arm model...", false);
            new SwingWorker<ArmInspection, Void>() {
                @Override protected ArmInspection doInBackground() throws Exception {
                    ModelInspection arm = inspect(path);
                    String baseSignature = basePath.isBlank() ? "" : inspect(basePath).signature();
                    return new ArmInspection(arm, baseSignature);
                }

                @Override protected void done() {
                    try {
                        ArmInspection inspection = get();
                        ModelInspection info = inspection.arm();
                        if (!inspectedRigId.equals(loadedRigId)
                                || !path.equals(field.getText().trim())) return;
                        JList<String> meshes = left ? leftMeshes : rightMeshes;
                        setMeshModel(meshes, info.meshes(), new LinkedHashSet<>(
                                meshes.getSelectedValuesList()));
                        String signatureMessage = "Skeleton signature: " + info.signature();
                        if (!inspection.baseSignature().isBlank()
                                && !inspection.baseSignature().equals(info.signature())) {
                            signatureMessage += "\nERROR: This arm skeleton does not match the base rig.";
                        }
                        diagnosticsArea.setText(signatureMessage + "\nMeshes:\n- "
                                + String.join("\n- ", info.meshes()));
                        FirstPersonViewmodelEditorWorkspace.this.state(
                                "Arm metadata loaded; select the meshes this side should render.", false);
                        preview.reloadPreview();
                    } catch (Exception exception) {
                        FirstPersonViewmodelEditorWorkspace.this.state(
                                "Arm inspection failed: " + rootMessage(exception), true);
                    }
                }
            }.execute();
        }

        private void inspectRigModel() {
            String path = model.getText().trim();
            if (path.isBlank()) return;
            String inspectedRigId = loadedRigId;
            state("Inspecting rig model...", false);
            new SwingWorker<ModelInspection, Void>() {
                @Override protected ModelInspection doInBackground() throws Exception { return inspect(path); }
                @Override protected void done() {
                    try {
                        ModelInspection info = get();
                        if (!inspectedRigId.equals(loadedRigId)
                                || !path.equals(model.getText().trim())) return;
                        populateCombo(leftShoulder, info.nodes()); populateCombo(leftElbow, info.nodes());
                        populateCombo(leftHand, info.nodes()); populateCombo(rightShoulder, info.nodes());
                        populateCombo(rightElbow, info.nodes()); populateCombo(rightHand, info.nodes());
                        populateCombo(cameraAnchor, info.nodes());
                        if (leftArm.getText().isBlank()) {
                            setMeshModel(leftMeshes, info.meshes(),
                                    new LinkedHashSet<>(leftMeshes.getSelectedValuesList()));
                        }
                        if (rightArm.getText().isBlank()) {
                            setMeshModel(rightMeshes, info.meshes(),
                                    new LinkedHashSet<>(rightMeshes.getSelectedValuesList()));
                        }
                        autoDetectBones(info.nodes()); autoDetectMeshes(info.meshes());
                        diagnosticsArea.setText("Skeleton signature: " + info.signature() + "\n"
                                + "Nodes: " + info.nodes().size() + ", meshes: " + info.meshes().size()
                                + ", clips: " + info.clips().size() + "\n"
                                + String.join("\n", info.diagnostics()));
                        FirstPersonViewmodelEditorWorkspace.this.state("Rig metadata loaded.", false);
                        preview.reloadPreview();
                        if (!leftArm.getText().isBlank()) inspectArmModel(true);
                        if (!rightArm.getText().isBlank()) inspectArmModel(false);
                    } catch (Exception exception) {
                        FirstPersonViewmodelEditorWorkspace.this.state(
                                "Rig inspection failed: " + rootMessage(exception), true);
                    }
                }
            }.execute();
        }

        private void autoDetectBones(List<String> nodes) {
            autoCombo(leftShoulder, nodes, "shoulder", "clavicle", ".l", "_l", "left");
            autoCombo(leftElbow, nodes, "forearm", "lowerarm", ".l", "_l", "left");
            autoCombo(leftHand, nodes, "hand", "wrist", ".l", "_l", "left");
            autoCombo(rightShoulder, nodes, "shoulder", "clavicle", ".r", "_r", "right");
            autoCombo(rightElbow, nodes, "forearm", "lowerarm", ".r", "_r", "right");
            autoCombo(rightHand, nodes, "hand", "wrist", ".r", "_r", "right");
            autoCombo(cameraAnchor, nodes, "head", "camera", "eyes", "neck");
        }

        private void autoDetectMeshes(List<String> meshes) {
            if (leftArm.getText().isBlank() && leftMeshes.getSelectedIndices().length == 0) {
                selectMatching(leftMeshes, meshes, "left", "_l", ".l", "arml", "handl");
            }
            if (rightArm.getText().isBlank() && rightMeshes.getSelectedIndices().length == 0) {
                selectMatching(rightMeshes, meshes, "right", "_r", ".r", "armr", "handr");
            }
        }
    }

    private final class AnimationSetForm {
        final JPanel panel = formPanel();
        final JTextField id = new JTextField(22);
        final JTextField name = new JTextField(22);
        final JComboBox<CatalogEntry> rig = new JComboBox<>();
        final JList<FirstPersonCombatLibrary.AnimationSlot> slots =
                new JList<>(FirstPersonCombatLibrary.AnimationSlot.values());
        final JTextField path = new JTextField(26);
        final JComboBox<String> clip = editableCombo();
        final JSpinner speed = decimal(1, 0.01, 20, 0.05);
        final JSpinner impact = decimal(0.55, 0, 1, 0.01);
        final JSpinner cameraX = decimal(0, -10, 10, 0.01);
        final JSpinner cameraY = decimal(0, -10, 10, 0.01);
        final JSpinner cameraZ = decimal(0, -10, 10, 0.01);
        final JSpinner cameraRotationX = decimal(0, -360, 360, 1);
        final JSpinner cameraRotationY = decimal(0, -360, 360, 1);
        final JSpinner cameraRotationZ = decimal(0, -360, 360, 1);
        final JCheckBox enabled = new JCheckBox("Provide this binding");
        final EnumMap<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> bindings =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        FirstPersonCombatLibrary.AnimationSlot loadedSlot;

        AnimationSetForm() {
            row(panel, "Stable ID", id); row(panel, "Display Name", name); row(panel, "Compatible Rig", rig);
            slots.setVisibleRowCount(9); slots.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            slots.setCellRenderer((list, value, index, selected, focus) -> {
                JLabel label = (JLabel) new javax.swing.DefaultListCellRenderer()
                        .getListCellRendererComponent(list, value, index, selected, focus);
                label.setText((bindings.containsKey(value) ? "● " : "○ ")
                        + value.name().replace('_', ' '));
                return label;
            });
            JPanel slotChooser = new JPanel(new BorderLayout(4, 4));
            slotChooser.add(new JScrollPane(slots), BorderLayout.CENTER);
            JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JButton previousUsed = new JButton("Previous Configured");
            JButton nextUsed = new JButton("Next Configured");
            previousUsed.addActionListener(event -> navigateConfiguredSlot(-1));
            nextUsed.addActionListener(event -> navigateConfiguredSlot(1));
            navigation.add(previousUsed); navigation.add(nextUsed);
            slotChooser.add(navigation, BorderLayout.SOUTH);
            row(panel, "Action Slot", slotChooser);
            row(panel, "Enabled", enabled); row(panel, "Animation Model", browseRow(path));
            JButton inspect = new JButton("Inspect Clips");
            inspect.addActionListener(event -> inspectClips());
            row(panel, "Clip Metadata", inspect); row(panel, "Clip", clip);
            row(panel, "Playback Speed", speed); row(panel, "Impact Fraction", impact);
            String cameraHelp = "Saved only for the selected action slot and added to the rig's baseline framing.";
            cameraX.setToolTipText(cameraHelp); cameraY.setToolTipText(cameraHelp);
            cameraZ.setToolTipText(cameraHelp); cameraRotationX.setToolTipText(cameraHelp);
            cameraRotationY.setToolTipText(cameraHelp); cameraRotationZ.setToolTipText(cameraHelp);
            row(panel, "Camera Position X / Y / Z", compact(cameraX, cameraY, cameraZ));
            row(panel, "Camera Rotation X / Y / Z",
                    compact(cameraRotationX, cameraRotationY, cameraRotationZ));
            JButton resetCamera = new JButton("Reset Selected Animation Camera");
            resetCamera.addActionListener(event -> resetCameraFraming());
            row(panel, "Camera Framing", resetCamera);
            JComboBox<WeaponType> defaultType = new JComboBox<>(java.util.Arrays.stream(WeaponType.values())
                    .filter(type -> type != WeaponType.NONE).toArray(WeaponType[]::new));
            JButton makeDefault = new JButton("Use Selected Set");
            makeDefault.addActionListener(event -> {
                commit();
                WeaponType type = (WeaponType) defaultType.getSelectedItem();
                if (type == null || loadedSetId.isBlank()) return;
                EnumMap<WeaponType, String> defaults = new EnumMap<>(WeaponType.class);
                defaults.putAll(draft.weaponDefaults()); defaults.put(type, loadedSetId);
                draft = new FirstPersonCombatLibrary.Content(draft.defaultRigId(), draft.rigs(),
                        draft.animationSets(), draft.itemProfiles(), defaults);
                markDirty();
            });
            row(panel, "Weapon Default", compact(defaultType, makeDefault));
            slots.addListSelectionListener(event -> {
                if (event.getValueIsAdjusting() || loading) return;
                commitBinding();
                loadBinding(slots.getSelectedValue());
                commit();
                refreshSelectedAnimationPreview(true);
            });
            enabled.addActionListener(event -> updateBindingEnabled());
            cameraX.addChangeListener(event -> cameraFramingChanged());
            cameraY.addChangeListener(event -> cameraFramingChanged());
            cameraZ.addChangeListener(event -> cameraFramingChanged());
            cameraRotationX.addChangeListener(event -> cameraFramingChanged());
            cameraRotationY.addChangeListener(event -> cameraFramingChanged());
            cameraRotationZ.addChangeListener(event -> cameraFramingChanged());
        }

        void load(FirstPersonCombatLibrary.AnimationSet set) {
            if (set == null) return;
            loading = true;
            id.setText(set.id()); name.setText(set.displayName());
            refreshReferences(); selectComboEntryPreserving(rig, set.rigId(), "Unavailable rig");
            bindings.clear(); bindings.putAll(set.bindings());
            FirstPersonCombatLibrary.AnimationSlot initial = bindings.keySet().stream()
                    .findFirst().orElse(FirstPersonCombatLibrary.AnimationSlot.IDLE_LEFT);
            slots.setSelectedValue(initial, true); loadedSlot = slots.getSelectedValue();
            slots.repaint();
            loading = false; loadBinding(loadedSlot);
        }

        void commit() {
            if (loading || loadedSetId.isBlank() || !draft.animationSets().containsKey(loadedSetId)) return;
            commitBinding();
            String requestedId = FirstPersonCombatLibrary.normalizeId(id.getText());
            if (requestedId.isBlank()) requestedId = loadedSetId;
            if (!requestedId.equals(loadedSetId) && draft.animationSets().containsKey(requestedId)) {
                state("Motion-set ID " + requestedId + " already exists.", true);
                id.setText(loadedSetId); requestedId = loadedSetId;
            }
            CatalogEntry rigEntry = (CatalogEntry) rig.getSelectedItem();
            FirstPersonCombatLibrary.AnimationSet updated = new FirstPersonCombatLibrary.AnimationSet(
                    requestedId, name.getText(), rigEntry == null ? "" : rigEntry.id(), bindings);
            replaceSet(loadedSetId, updated);
            loadedSetId = requestedId;
        }

        void refreshReferences() {
            String selected = comboEntryId(rig);
            rig.removeAllItems();
            draft.rigs().values().forEach(value -> rig.addItem(new CatalogEntry(value.rigId(), value.displayName())));
            selectComboEntryPreserving(rig, selected, "Unavailable rig");
        }

        private void commitBinding() {
            if (loading || loadedSlot == null) return;
            if (enabled.isSelected() && !path.getText().isBlank()) {
                bindings.put(loadedSlot, new FirstPersonCombatLibrary.ClipBinding(
                        path.getText(), comboText(clip), value(speed), value(impact),
                        currentCameraFraming()));
            } else {
                bindings.remove(loadedSlot);
            }
            slots.repaint();
        }

        private void loadBinding(FirstPersonCombatLibrary.AnimationSlot slot) {
            if (slot == null) return;
            loadedSlot = slot;
            FirstPersonCombatLibrary.ClipBinding binding = bindings.get(slot);
            loading = true;
            enabled.setSelected(binding != null);
            path.setText(binding == null ? "" : binding.path());
            setCombo(clip, binding == null ? "" : binding.clipName());
            speed.setValue(binding == null ? 1.0 : binding.playbackSpeed());
            impact.setValue(binding == null ? 0.55 : binding.impactFraction());
            FirstPersonCombatLibrary.CameraFraming camera = binding == null
                    ? FirstPersonCombatLibrary.CameraFraming.identity()
                    : binding.cameraFraming();
            cameraX.setValue(camera.positionX()); cameraY.setValue(camera.positionY());
            cameraZ.setValue(camera.positionZ());
            cameraRotationX.setValue(camera.rotationX());
            cameraRotationY.setValue(camera.rotationY());
            cameraRotationZ.setValue(camera.rotationZ());
            loading = false; updateBindingEnabled();
        }

        private void updateBindingEnabled() {
            boolean active = enabled.isSelected();
            path.setEnabled(active); clip.setEnabled(active); speed.setEnabled(active); impact.setEnabled(active);
            cameraX.setEnabled(active); cameraY.setEnabled(active); cameraZ.setEnabled(active);
            cameraRotationX.setEnabled(active); cameraRotationY.setEnabled(active);
            cameraRotationZ.setEnabled(active);
        }

        FirstPersonCombatLibrary.CameraFraming currentCameraFraming() {
            return new FirstPersonCombatLibrary.CameraFraming(
                    value(cameraX), value(cameraY), value(cameraZ),
                    value(cameraRotationX), value(cameraRotationY), value(cameraRotationZ));
        }

        FirstPersonCombatLibrary.WieldHand previewHand(
                FirstPersonCombatLibrary.WieldHand fallback
        ) {
            if (loadedSlot == null) return fallback;
            return switch (loadedSlot) {
                case IDLE_LEFT, ATTACK_LEFT, BLOCK_RIGHT -> FirstPersonCombatLibrary.WieldHand.LEFT;
                case IDLE_RIGHT, ATTACK_RIGHT, BLOCK_LEFT -> FirstPersonCombatLibrary.WieldHand.RIGHT;
                default -> fallback;
            };
        }

        private void cameraFramingChanged() {
            if (loading || loadedSlot == null) return;
            commitBinding();
            commit();
            refreshSelectedAnimationPreview(false);
        }

        private void resetCameraFraming() {
            loading = true;
            cameraX.setValue(0.0); cameraY.setValue(0.0); cameraZ.setValue(0.0);
            cameraRotationX.setValue(0.0); cameraRotationY.setValue(0.0);
            cameraRotationZ.setValue(0.0);
            loading = false;
            cameraFramingChanged();
        }

        private void navigateConfiguredSlot(int direction) {
            commitBinding();
            List<FirstPersonCombatLibrary.AnimationSlot> configured = new ArrayList<>();
            for (FirstPersonCombatLibrary.AnimationSlot slot
                    : FirstPersonCombatLibrary.AnimationSlot.values()) {
                if (bindings.containsKey(slot)) configured.add(slot);
            }
            if (configured.isEmpty()) return;
            int current = configured.indexOf(loadedSlot);
            int next = current < 0
                    ? (direction < 0 ? configured.size() - 1 : 0)
                    : Math.floorMod(current + direction, configured.size());
            slots.setSelectedValue(configured.get(next), true);
        }

        private void inspectClips() {
            if (path.getText().isBlank()) return;
            String inspectedSetId = loadedSetId;
            FirstPersonCombatLibrary.AnimationSlot inspectedSlot = loadedSlot;
            String inspectedPath = path.getText();
            state("Inspecting animation clips...", false);
            new SwingWorker<ModelInspection, Void>() {
                @Override protected ModelInspection doInBackground() throws Exception { return inspect(inspectedPath); }
                @Override protected void done() {
                    try {
                        ModelInspection info = get();
                        if (!inspectedSetId.equals(loadedSetId) || inspectedSlot != loadedSlot
                                || !inspectedPath.equals(path.getText())) return;
                        populateCombo(clip, info.clips());
                        diagnosticsArea.setText("Animation skeleton: " + info.signature() + "\nClips:\n- "
                                + String.join("\n- ", info.clips()));
                        FirstPersonViewmodelEditorWorkspace.this.state("Animation clips loaded.", false);
                    } catch (Exception exception) {
                        FirstPersonViewmodelEditorWorkspace.this.state(
                                "Animation inspection failed: " + rootMessage(exception), true);
                    }
                }
            }.execute();
        }
    }

    private final class ProfileForm {
        final JPanel panel = formPanel();
        final JComboBox<CatalogEntry> item = new JComboBox<>();
        final JComboBox<CatalogEntry> rig = new JComboBox<>();
        final JComboBox<CatalogEntry> set = new JComboBox<>();
        final JComboBox<FirstPersonCombatLibrary.WieldHand> hand =
                new JComboBox<>(FirstPersonCombatLibrary.WieldHand.values());
        final JSpinner px = decimal(0, -10, 10, 0.01), py = decimal(0, -10, 10, 0.01),
                pz = decimal(0, -10, 10, 0.01);
        final JSpinner rx = decimal(0, -360, 360, 1), ry = decimal(0, -360, 360, 1),
                rz = decimal(0, -360, 360, 1), scale = decimal(1, 0.001, 100, 0.01);
        final JSpinner secondaryX = decimal(0, -10, 10, 0.01),
                secondaryY = decimal(0, -10, 10, 0.01), secondaryZ = decimal(0, -10, 10, 0.01);
        final JTextField leftArmor = new JTextField(25), rightArmor = new JTextField(25);
        final JComboBox<FirstPersonCombatLibrary.ArmCoverage> leftCoverage =
                new JComboBox<>(FirstPersonCombatLibrary.ArmCoverage.values());
        final JComboBox<FirstPersonCombatLibrary.ArmCoverage> rightCoverage =
                new JComboBox<>(FirstPersonCombatLibrary.ArmCoverage.values());
        Map<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> overrides = Map.of();

        ProfileForm() {
            row(panel, "Item", item); row(panel, "Rig", rig); row(panel, "Motion Set", set);
            row(panel, "Wielding Hand", hand); row(panel, "Socket Position X / Y / Z", compact(px, py, pz));
            row(panel, "Socket Rotation X / Y / Z", compact(rx, ry, rz));
            row(panel, "Socket Model Height", scale);
            row(panel, "Secondary Grip X / Y / Z", compact(secondaryX, secondaryY, secondaryZ));
            row(panel, "Left Glove / Sleeve", browseRow(leftArmor)); row(panel, "Left Coverage", leftCoverage);
            row(panel, "Right Glove / Sleeve", browseRow(rightArmor)); row(panel, "Right Coverage", rightCoverage);
            JButton editOverrides = new JButton("Edit Per-Action Overrides...");
            editOverrides.addActionListener(event -> {
                Map<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> edited =
                        editOverrides(overrides);
                if (edited != null) {
                    overrides = edited;
                    markDirty();
                }
            });
            row(panel, "Animation Overrides", editOverrides);
            JButton usePreview = new JButton("Use This Item in Preview");
            usePreview.addActionListener(event -> {
                CatalogEntry selected = (CatalogEntry) item.getSelectedItem();
                if (selected == null) return;
                for (int index = 0; index < previewItem.getItemCount(); index++) {
                    if (previewItem.getItemAt(index).id().equals(selected.id())) {
                        previewItem.setSelectedIndex(index); break;
                    }
                }
                commit(); preview.reloadPreview();
            });
            row(panel, "Preview", usePreview);
        }

        void load(FirstPersonCombatLibrary.ItemProfile profile) {
            if (profile == null) return;
            loading = true; refreshReferences();
            selectComboEntryPreserving(item, profile.itemId(), "Unavailable item");
            selectComboEntryPreserving(rig, profile.rigId(), "Unavailable rig");
            selectComboEntryPreserving(set, profile.animationSetId(), "Unavailable motion set");
            hand.setSelectedItem(profile.wieldHand());
            EquipmentViewModelProfile socket = profile.socketTransform();
            px.setValue(socket.positionX()); py.setValue(socket.positionY()); pz.setValue(socket.positionZ());
            rx.setValue(socket.rotationX()); ry.setValue(socket.rotationY()); rz.setValue(socket.rotationZ());
            scale.setValue(socket.normalizedHeight()); secondaryX.setValue(profile.secondaryGripX());
            secondaryY.setValue(profile.secondaryGripY()); secondaryZ.setValue(profile.secondaryGripZ());
            leftArmor.setText(profile.leftArmorPath()); rightArmor.setText(profile.rightArmorPath());
            leftCoverage.setSelectedItem(profile.leftCoverage()); rightCoverage.setSelectedItem(profile.rightCoverage());
            overrides = profile.overrides(); loading = false;
        }

        void commit() {
            if (loading || loadedProfileId.isBlank() || !draft.itemProfiles().containsKey(loadedProfileId)) return;
            CatalogEntry itemEntry = (CatalogEntry) item.getSelectedItem();
            CatalogEntry rigEntry = (CatalogEntry) rig.getSelectedItem();
            CatalogEntry setEntry = (CatalogEntry) set.getSelectedItem();
            String itemId = itemEntry == null ? loadedProfileId : itemEntry.id();
            if (!itemId.equals(loadedProfileId) && draft.itemProfiles().containsKey(itemId)) {
                state("Item " + itemId + " already has an equipment profile.", true);
                itemId = loadedProfileId;
            }
            FirstPersonCombatLibrary.ItemProfile updated = new FirstPersonCombatLibrary.ItemProfile(
                    itemId, rigEntry == null ? "" : rigEntry.id(),
                    (FirstPersonCombatLibrary.WieldHand) hand.getSelectedItem(),
                    setEntry == null ? "" : setEntry.id(),
                    new EquipmentViewModelProfile(value(px), value(py), value(pz),
                            value(rx), value(ry), value(rz), value(scale), 0, 0, 1, false),
                    value(secondaryX), value(secondaryY), value(secondaryZ),
                    leftArmor.getText(), rightArmor.getText(),
                    (FirstPersonCombatLibrary.ArmCoverage) leftCoverage.getSelectedItem(),
                    (FirstPersonCombatLibrary.ArmCoverage) rightCoverage.getSelectedItem(), overrides);
            replaceProfile(loadedProfileId, updated); loadedProfileId = itemId;
        }

        void refreshReferences() {
            String itemId = comboEntryId(item), rigId = comboEntryId(rig), setId = comboEntryId(set);
            item.removeAllItems(); items.forEach(value -> item.addItem(new CatalogEntry(value.id(), value.displayName())));
            rig.removeAllItems(); draft.rigs().values().forEach(value ->
                    rig.addItem(new CatalogEntry(value.rigId(), value.displayName())));
            set.removeAllItems(); draft.animationSets().values().forEach(value ->
                    set.addItem(new CatalogEntry(value.id(), value.displayName())));
            selectComboEntryPreserving(item, itemId, "Unavailable item");
            selectComboEntryPreserving(rig, rigId, "Unavailable rig");
            selectComboEntryPreserving(set, setId, "Unavailable motion set");
        }
    }

    private Map<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding>
            editOverrides(Map<FirstPersonCombatLibrary.AnimationSlot,
                    FirstPersonCombatLibrary.ClipBinding> existing) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[] {"Use", "Action", "Animation Model", "Clip", "Speed", "Impact"}, 0) {
            @Override public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 0 -> Boolean.class;
                    case 4, 5 -> Double.class;
                    default -> String.class;
                };
            }
            @Override public boolean isCellEditable(int row, int column) { return column != 1; }
        };
        for (FirstPersonCombatLibrary.AnimationSlot slot
                : FirstPersonCombatLibrary.AnimationSlot.values()) {
            FirstPersonCombatLibrary.ClipBinding binding = existing == null ? null : existing.get(slot);
            model.addRow(new Object[] {binding != null, slot.name(),
                    binding == null ? "" : binding.path(), binding == null ? "" : binding.clipName(),
                    binding == null ? 1.0 : binding.playbackSpeed(),
                    binding == null ? 0.55 : binding.impactFraction()});
        }
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {45, 115, 310, 180, 70, 70};
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
        JButton browse = new JButton("Browse Selected Model");
        browse.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            JTextField field = new JTextField(String.valueOf(model.getValueAt(row, 2)));
            modelBrowser.accept(field);
            model.setValueAt(field.getText(), row, 2);
            model.setValueAt(true, row, 0);
        });
        JButton inspectClips = new JButton("Choose Clip from Model");
        inspectClips.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String path = String.valueOf(model.getValueAt(row, 2)).trim();
            if (path.isBlank()) return;
            try {
                List<String> clips = inspect(path).clips();
                Object selected = JOptionPane.showInputDialog(this, "Animation clip", "Choose Clip",
                        JOptionPane.PLAIN_MESSAGE, null, clips.toArray(),
                        clips.isEmpty() ? null : clips.get(0));
                if (selected != null) {
                    model.setValueAt(String.valueOf(selected), row, 3);
                    model.setValueAt(true, row, 0);
                }
            } catch (Exception exception) {
                state("Animation inspection failed: " + rootMessage(exception), true);
            }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        controls.add(browse); controls.add(inspectClips);
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(table), BorderLayout.CENTER); panel.add(controls, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(850, 430));
        if (JOptionPane.showConfirmDialog(this, panel, "Equipment Animation Overrides",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        EnumMap<FirstPersonCombatLibrary.AnimationSlot, FirstPersonCombatLibrary.ClipBinding> result =
                new EnumMap<>(FirstPersonCombatLibrary.AnimationSlot.class);
        for (int row = 0; row < model.getRowCount(); row++) {
            if (!Boolean.TRUE.equals(model.getValueAt(row, 0))) continue;
            String path = String.valueOf(model.getValueAt(row, 2)).trim();
            if (path.isBlank()) continue;
            FirstPersonCombatLibrary.AnimationSlot slot = FirstPersonCombatLibrary.AnimationSlot.valueOf(
                    String.valueOf(model.getValueAt(row, 1)));
            FirstPersonCombatLibrary.ClipBinding previous = existing == null
                    ? null : existing.get(slot);
            result.put(slot, new FirstPersonCombatLibrary.ClipBinding(path,
                    String.valueOf(model.getValueAt(row, 3)), tableNumber(model.getValueAt(row, 4), 1),
                    tableNumber(model.getValueAt(row, 5), 0.55),
                    previous == null
                            ? FirstPersonCombatLibrary.CameraFraming.identity()
                            : previous.cameraFraming()));
        }
        return Map.copyOf(result);
    }

    private void replaceRig(String oldId, FirstPersonCombatLibrary.RigDefinition updated) {
        FirstPersonCombatLibrary.RigDefinition old = draft.rigs().get(oldId);
        if (old != null && old.equals(updated)) return;
        LinkedHashMap<String, FirstPersonCombatLibrary.RigDefinition> rigs = new LinkedHashMap<>(draft.rigs());
        rigs.remove(oldId); rigs.put(updated.rigId(), updated);
        String defaultId = draft.defaultRigId().equals(oldId) ? updated.rigId() : draft.defaultRigId();
        LinkedHashMap<String, FirstPersonCombatLibrary.AnimationSet> sets = new LinkedHashMap<>();
        draft.animationSets().forEach((id, set) -> sets.put(id,
                set.rigId().equals(oldId)
                        ? new FirstPersonCombatLibrary.AnimationSet(set.id(), set.displayName(), updated.rigId(), set.bindings())
                        : set));
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles = new LinkedHashMap<>();
        draft.itemProfiles().forEach((id, profile) -> profiles.put(id,
                profile.rigId().equals(oldId) ? withRig(profile, updated.rigId()) : profile));
        draft = new FirstPersonCombatLibrary.Content(defaultId, rigs, sets, profiles, draft.weaponDefaults());
        if (!oldId.equals(updated.rigId())) recordRigRename(oldId, updated.rigId());
        markDirty();
    }

    private void recordRigRename(String oldId, String newId) {
        if (oldId == null || newId == null || oldId.isBlank() || newId.isBlank()
                || oldId.equals(newId)) return;
        rigRenames.replaceAll((source, target) -> target.equals(oldId) ? newId : target);
        rigRenames.put(oldId, newId);
    }

    private void replaceSet(String oldId, FirstPersonCombatLibrary.AnimationSet updated) {
        FirstPersonCombatLibrary.AnimationSet old = draft.animationSets().get(oldId);
        if (old != null && old.equals(updated)) return;
        LinkedHashMap<String, FirstPersonCombatLibrary.AnimationSet> sets = new LinkedHashMap<>(draft.animationSets());
        sets.remove(oldId); sets.put(updated.id(), updated);
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles = new LinkedHashMap<>();
        draft.itemProfiles().forEach((id, profile) -> profiles.put(id,
                profile.animationSetId().equals(oldId) ? withAnimationSet(profile, updated.id()) : profile));
        EnumMap<WeaponType, String> defaults = new EnumMap<>(WeaponType.class);
        draft.weaponDefaults().forEach((type, id) -> defaults.put(type,
                id.equals(oldId) ? updated.id() : id));
        draft = new FirstPersonCombatLibrary.Content(draft.defaultRigId(), draft.rigs(), sets, profiles, defaults);
        markDirty();
    }

    private void replaceProfile(String oldId, FirstPersonCombatLibrary.ItemProfile updated) {
        FirstPersonCombatLibrary.ItemProfile old = draft.itemProfiles().get(oldId);
        if (old != null && old.equals(updated)) return;
        LinkedHashMap<String, FirstPersonCombatLibrary.ItemProfile> profiles = new LinkedHashMap<>(draft.itemProfiles());
        profiles.remove(oldId); profiles.put(updated.itemId(), updated);
        draft = new FirstPersonCombatLibrary.Content(draft.defaultRigId(), draft.rigs(),
                draft.animationSets(), profiles, draft.weaponDefaults());
        markDirty();
    }

    private void markDirty() {
        if (loading) return;
        dirty = true; stateLabel.setText("Unsaved draft");
    }

    private void state(String text, boolean error) {
        stateLabel.setText(text);
        stateLabel.setForeground(error ? new Color(190, 45, 45) : Color.DARK_GRAY);
    }

    private JPanel browseRow(JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(4, 0)); panel.add(field, BorderLayout.CENTER);
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> modelBrowser.accept(field)); panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return panel;
    }

    private static void row(JPanel panel, String label, Component component) {
        int y = panel.getComponentCount() / 2;
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = y; left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(4, 3, 4, 8); panel.add(new JLabel(label), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = y; right.weightx = 1; right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.NORTHWEST; right.insets = new Insets(3, 3, 3, 3);
        panel.add(component, right);
    }

    private static JPanel compact(Component... components) {
        JPanel panel = new JPanel(new java.awt.GridLayout(1, components.length, 4, 0));
        for (Component component : components) panel.add(component);
        return panel;
    }

    private static JSpinner decimal(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JSpinner integer(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static double value(JSpinner spinner) { return ((Number) spinner.getValue()).doubleValue(); }

    private static double tableNumber(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static JComboBox<String> editableCombo() {
        JComboBox<String> combo = new JComboBox<>(); combo.setEditable(true); return combo;
    }

    private static String comboText(JComboBox<?> combo) {
        Object value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }

    private static void setCombo(JComboBox<String> combo, String value) {
        combo.getEditor().setItem(value == null ? "" : value);
    }

    private static void populateCombo(JComboBox<String> combo, List<String> values) {
        String selected = comboText(combo); combo.removeAllItems();
        values.forEach(combo::addItem); setCombo(combo, selected);
    }

    private static <T> JList<T> list(DefaultListModel<T> model) {
        JList<T> list = new JList<>(model); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); return list;
    }

    private static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(DocumentEvent event) { action.run(); }
        };
    }

    private ModelInspection inspect(String path) throws Exception {
        CharacterModelDefinition definition = new CharacterModelDefinition(
                path == null ? "" : path.trim(), "inspection", 1, 0, 0, Map.of());
        LwjglSkinnedModel model = LwjglSkinnedModel.loadCached(definition);
        return new ModelInspection(model.nodeNames(), model.meshNames(),
                new ArrayList<>(model.clipNames()), model.skeletonSignature(), model.diagnostics());
    }

    private static void populateComboEntry(JComboBox<CatalogEntry> combo, List<CatalogEntry> entries) {
        String selected = comboEntryId(combo); combo.removeAllItems(); entries.forEach(combo::addItem);
        selectComboEntry(combo, selected);
    }

    private static String comboEntryId(JComboBox<CatalogEntry> combo) {
        CatalogEntry entry = (CatalogEntry) combo.getSelectedItem(); return entry == null ? "" : entry.id();
    }

    private static void selectComboEntry(JComboBox<CatalogEntry> combo, String id) {
        if (id == null) return;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).id().equals(id)) { combo.setSelectedIndex(index); return; }
        }
    }

    private static void selectComboEntryPreserving(
            JComboBox<CatalogEntry> combo,
            String id,
            String unavailableLabel
    ) {
        if (id == null || id.isBlank()) return;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).id().equals(id)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        CatalogEntry missing = new CatalogEntry(id, unavailableLabel);
        combo.addItem(missing);
        combo.setSelectedItem(missing);
    }

    private static void setMeshModel(JList<String> list, List<String> meshes, Set<String> selected) {
        list.setListData(meshes.toArray(String[]::new));
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < meshes.size(); index++) {
            String mesh = meshes.get(index);
            if (selected.stream().anyMatch(value -> value.equalsIgnoreCase(mesh))) indices.add(index);
        }
        list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
    }

    private static void autoCombo(JComboBox<String> combo, List<String> names, String... hints) {
        String current = comboText(combo);
        if (!current.isBlank() && names.stream().anyMatch(current::equalsIgnoreCase)) return;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            boolean primary = lower.contains(hints[0]) || lower.contains(hints[1]);
            boolean side = false;
            for (int index = 2; index < hints.length; index++) side |= lower.contains(hints[index]);
            if (primary && side) { setCombo(combo, name); return; }
        }
    }

    private static void selectMatching(JList<String> list, List<String> names, String... hints) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            String lower = names.get(index).toLowerCase(Locale.ROOT);
            for (String hint : hints) if (lower.contains(hint)) { indexes.add(index); break; }
        }
        list.setSelectedIndices(indexes.stream().mapToInt(Integer::intValue).toArray());
    }

    private static String selectedId(JList<CatalogEntry> list) {
        CatalogEntry entry = list.getSelectedValue(); return entry == null ? "" : entry.id();
    }

    private static void restoreSelection(JList<CatalogEntry> list, String id) {
        if (id == null || id.isBlank()) return;
        selectEntry(list, id);
    }

    private static void selectEntry(JList<CatalogEntry> list, String id) {
        for (int index = 0; index < list.getModel().getSize(); index++) {
            if (list.getModel().getElementAt(index).id().equals(id)) {
                list.setSelectedIndex(index); list.ensureIndexIsVisible(index); return;
            }
        }
    }

    private JList<CatalogEntry> listFor(Kind kind) {
        return switch (kind) { case RIG -> rigList; case ANIMATION_SET -> setList; case ITEM_PROFILE -> profileList; };
    }

    private String itemLabel(String id) {
        ItemOption item = itemsById.get(id); return item == null ? "Unavailable " + id : item.displayName();
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.isBlank()) return true;
        for (String value : values) if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    private static String uniqueId(String base, Set<String> existing) {
        String normalized = FirstPersonCombatLibrary.normalizeId(base);
        if (!existing.contains(normalized)) return normalized;
        int suffix = 2; while (existing.contains(normalized + "_" + suffix)) suffix++;
        return normalized + "_" + suffix;
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "First-Person Viewmodels",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static FirstPersonCombatLibrary.RigDefinition copyRig(
            FirstPersonCombatLibrary.RigDefinition source, String id, String name) {
        return new FirstPersonCombatLibrary.RigDefinition(id, name, source.modelPath(),
                source.defaultLeftArmPath(), source.defaultRightArmPath(),
                source.leftShoulderBone(), source.leftElbowBone(), source.leftHandBone(),
                source.rightShoulderBone(), source.rightElbowBone(), source.rightHandBone(),
                source.cameraAnchorBone(),
                source.leftVisibleMeshes(), source.rightVisibleMeshes(),
                source.positionX(), source.positionY(), source.positionZ(),
                source.rotationX(), source.rotationY(), source.rotationZ(), source.scale(),
                source.fieldOfViewDegrees(), source.nearPlane(), source.crossfadeMs(), source.fallbackBindings());
    }

    private static FirstPersonCombatLibrary.ItemProfile withRig(
            FirstPersonCombatLibrary.ItemProfile profile, String rigId) {
        return new FirstPersonCombatLibrary.ItemProfile(profile.itemId(), rigId, profile.wieldHand(),
                profile.animationSetId(), profile.socketTransform(), profile.secondaryGripX(),
                profile.secondaryGripY(), profile.secondaryGripZ(), profile.leftArmorPath(),
                profile.rightArmorPath(), profile.leftCoverage(), profile.rightCoverage(), profile.overrides());
    }

    private static FirstPersonCombatLibrary.ItemProfile withAnimationSet(
            FirstPersonCombatLibrary.ItemProfile profile, String setId) {
        return new FirstPersonCombatLibrary.ItemProfile(profile.itemId(), profile.rigId(), profile.wieldHand(),
                setId, profile.socketTransform(), profile.secondaryGripX(), profile.secondaryGripY(),
                profile.secondaryGripZ(), profile.leftArmorPath(), profile.rightArmorPath(),
                profile.leftCoverage(), profile.rightCoverage(), profile.overrides());
    }
}

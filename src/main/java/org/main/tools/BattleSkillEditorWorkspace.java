package org.main.tools;

import org.main.battle.BattleEncounter;
import org.main.battle.BattleSkillSandbox;
import org.main.content.AuthoringFieldDescriptor;
import org.main.content.BattleContentCatalog;
import org.main.content.BattleContentTypeRegistry;
import org.main.content.SkillDefinition;
import org.main.content.SkillEffectDefinition;
import org.main.content.StatusDefinition;
import org.main.core.Library;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resizable, catalog-level battle skill and status authoring workspace.
 */
public final class BattleSkillEditorWorkspace extends JDialog {
    public enum Kind { SKILL, STATUS }

    public interface Host {
        default List<String> referencesToSkill(String id) { return List.of(); }

        default List<String> referencesToStatus(String id) { return List.of(); }

        default void catalogsSaved(
                Map<String, String> skillReplacements,
                Map<String, String> statusReplacements
        ) throws IOException {
        }
    }

    private final Host host;
    private final JComboBox<Kind> kindBox = new JComboBox<>(Kind.values());
    private final JTextField searchField = new JTextField(18);
    private final DefaultListModel<CatalogEntry> catalogModel = new DefaultListModel<>();
    private final JList<CatalogEntry> catalogList = new JList<>(catalogModel);
    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorPanel = new JPanel(editorCards);
    private final JLabel stateLabel = new JLabel("Ready.");

    private final LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>();
    private final LinkedHashMap<String, StatusDefinition> statuses = new LinkedHashMap<>();
    private final List<String> defaultPool = new ArrayList<>();
    private final List<String> universalPool = new ArrayList<>();
    private final List<String> debugPool = new ArrayList<>();
    private final LinkedHashMap<String, String> skillReplacements = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> statusReplacements = new LinkedHashMap<>();

    private final JTextField skillId = new JTextField(24);
    private final JTextField skillName = new JTextField(24);
    private final JTextArea skillDescription = new JTextArea(4, 30);
    private final JComboBox<Library.EntityType> skillTeam = new JComboBox<>(
            new Library.EntityType[]{Library.EntityType.ENEMY, Library.EntityType.ALLY});
    private final JComboBox<Library.SkillTargetShape> skillShape =
            new JComboBox<>(Library.SkillTargetShape.values());
    private final JComboBox<Library.BattleTargetingMode> skillMode =
            new JComboBox<>(Library.BattleTargetingMode.values());
    private final JTextField skillSound = new JTextField(28);
    private final JComboBox<String> skillPresentation = new JComboBox<>(new String[]{
            "AUTO", "PHYSICAL_SKILL", "RANGED", "SPELL", "HEAL", "DEFEND", "SUMMON", "UTILITY", "DEBUG"
    });
    private final JSpinner skillCooldown = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 86400.0, 0.25));
    private final JCheckBox consumesAutoAction = new JCheckBox("Consumes auto-action timer", true);
    private final DefaultListModel<SkillEffectDefinition> effectModel = new DefaultListModel<>();
    private final JList<SkillEffectDefinition> effectList = new JList<>(effectModel);
    private final JCheckBox defaultPoolBox = new JCheckBox("Default player pool");
    private final JCheckBox universalPoolBox = new JCheckBox("Universal player pool");
    private final JCheckBox debugPoolBox = new JCheckBox("Debug player pool");
    private final JTextArea skillReferences = readOnlyArea();
    private final JTextArea skillPreview = readOnlyArea();
    private String loadedSkillId = "";

    private final JTextField statusId = new JTextField(24);
    private final JTextField statusName = new JTextField(24);
    private final JTextArea statusDescription = new JTextArea(4, 30);
    private final JTextField statusIcon = new JTextField(28);
    private final JComboBox<StatusDefinition.Polarity> statusPolarity =
            new JComboBox<>(StatusDefinition.Polarity.values());
    private final JComboBox<BattleContentTypeRegistry.HandlerDescriptor> statusBehavior =
            new JComboBox<>(BattleContentTypeRegistry.statusBehaviorDescriptors()
                    .toArray(new BattleContentTypeRegistry.HandlerDescriptor[0]));
    private final JSpinner statusDuration = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<StatusDefinition.StackingPolicy> statusStacking =
            new JComboBox<>(StatusDefinition.StackingPolicy.values());
    private final JSpinner statusMaxStacks = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JPanel statusParameterPanel = new JPanel(new GridBagLayout());
    private final Map<String, Component> statusParameterInputs = new LinkedHashMap<>();
    private final JTextArea statusReferences = readOnlyArea();
    private String loadedStatusId = "";
    private Map<String, String> loadedStatusParameters = Map.of();

    public static void open(Window owner, Kind kind, String selectedId, Host host) {
        BattleSkillEditorWorkspace workspace =
                new BattleSkillEditorWorkspace(owner, kind, selectedId, host);
        workspace.setVisible(true);
    }

    public static void openNew(Window owner, Kind kind, Host host) {
        BattleSkillEditorWorkspace workspace =
                new BattleSkillEditorWorkspace(owner, kind, "", host);
        workspace.kindBox.setSelectedItem(kind == null ? Kind.SKILL : kind);
        workspace.createEntry();
        workspace.setVisible(true);
    }

    private BattleSkillEditorWorkspace(Window owner, Kind kind, String selectedId, Host host) {
        super(owner, "Battle Skill & Status Editor", ModalityType.MODELESS);
        this.host = host == null ? new Host() { } : host;
        BattleContentCatalog.Snapshot snapshot = BattleContentCatalog.current();
        skills.putAll(snapshot.skills());
        statuses.putAll(snapshot.statuses());
        defaultPool.addAll(snapshot.defaultPlayerSkillIds());
        universalPool.addAll(snapshot.universalPlayerSkillIds());
        debugPool.addAll(snapshot.debugPlayerSkillIds());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        kindBox.addActionListener(event -> refreshCatalog(""));
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener(() ->
                refreshCatalog(selectedEntryId())));
        catalogList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelection();
        });
        statusBehavior.addActionListener(event -> rebuildStatusParameters(Map.of()));
        statusStacking.addActionListener(event -> statusMaxStacks.setEnabled(
                statusStacking.getSelectedItem() == StatusDefinition.StackingPolicy.STACK));
        kindBox.setSelectedItem(kind == null ? Kind.SKILL : kind);
        refreshCatalog(BattleContentCatalog.normalizeId(selectedId));
        ConstructionKitUi.configureWorkspace(this);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Catalog"));
        panel.add(kindBox);
        panel.add(new JLabel("Search"));
        panel.add(searchField);
        JButton create = new JButton("New");
        JButton duplicate = new JButton("Duplicate");
        JButton delete = new JButton("Delete");
        create.addActionListener(event -> createEntry());
        duplicate.addActionListener(event -> duplicateEntry());
        delete.addActionListener(event -> deleteEntry());
        panel.add(create);
        panel.add(duplicate);
        panel.add(delete);
        return panel;
    }

    private JSplitPane buildBody() {
        catalogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane catalogScroll = new JScrollPane(catalogList);
        catalogScroll.setBorder(BorderFactory.createTitledBorder("Authored Content"));
        catalogScroll.setPreferredSize(new Dimension(260, 600));

        editorPanel.add(buildSkillEditor(), Kind.SKILL.name());
        editorPanel.add(buildStatusEditor(), Kind.STATUS.name());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalogScroll, editorPanel);
        split.setDividerLocation(270);
        split.setResizeWeight(0.0);
        return split;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(stateLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton apply = new JButton("Apply Draft");
        JButton revert = new JButton("Revert");
        JButton save = new JButton("Save Catalogs");
        JButton close = new JButton("Close");
        apply.addActionListener(event -> applyDraft());
        revert.addActionListener(event -> loadSelection());
        save.addActionListener(event -> saveCatalogs());
        close.addActionListener(event -> dispose());
        buttons.add(apply);
        buttons.add(revert);
        buttons.add(save);
        buttons.add(close);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildSkillEditor() {
        JTabbedPane tabs = new JTabbedPane();
        JPanel identity = formPanel();
        addRow(identity, "Stable ID", skillId);
        addRow(identity, "Display Name", skillName);
        addRow(identity, "Description", new JScrollPane(skillDescription));
        addRow(identity, "Target Team", skillTeam);
        addRow(identity, "Target Shape", skillShape);
        addRow(identity, "Targeting Mode", skillMode);
        addRow(identity, "Cooldown (seconds)", skillCooldown);
        addRow(identity, "Use Sound", skillSound);
        addRow(identity, "Presentation", skillPresentation);
        addRow(identity, "", consumesAutoAction);
        tabs.addTab("Identity & Targeting", new JScrollPane(identity));

        JPanel effects = new JPanel(new BorderLayout(6, 6));
        effectList.setCellRenderer((list, value, index, selected, focus) -> {
            BattleContentTypeRegistry.HandlerDescriptor descriptor =
                    BattleContentTypeRegistry.effectDescriptor(value.kindId());
            String label = descriptor == null ? value.kindId() : descriptor.label();
            JLabel result = new JLabel((index + 1) + ". " + label + " — "
                    + value.recipientScope() + " / " + value.condition()
                    + " / " + Math.round(value.chance() * 100) + "%");
            result.setOpaque(true);
            result.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            result.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return result;
        });
        effects.add(new JScrollPane(effectList), BorderLayout.CENTER);
        JPanel effectButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton duplicate = new JButton("Duplicate");
        JButton up = new JButton("Move Up");
        JButton down = new JButton("Move Down");
        JButton remove = new JButton("Remove");
        add.addActionListener(event -> editEffect(null, -1));
        edit.addActionListener(event -> editEffect(effectList.getSelectedValue(), effectList.getSelectedIndex()));
        duplicate.addActionListener(event -> {
            SkillEffectDefinition selected = effectList.getSelectedValue();
            if (selected != null) editEffect(selected, -1);
        });
        up.addActionListener(event -> moveEffect(-1));
        down.addActionListener(event -> moveEffect(1));
        remove.addActionListener(event -> {
            int index = effectList.getSelectedIndex();
            if (index >= 0) effectModel.remove(index);
        });
        for (JButton button : List.of(add, edit, duplicate, up, down, remove)) effectButtons.add(button);
        effects.add(effectButtons, BorderLayout.SOUTH);
        tabs.addTab("Ordered Effects", effects);

        JPanel assignments = formPanel();
        addRow(assignments, "Player Pools", stack(defaultPoolBox, universalPoolBox, debugPoolBox));
        addRow(assignments, "References", new JScrollPane(skillReferences));
        tabs.addTab("Assignments & References", new JScrollPane(assignments));

        JPanel preview = new JPanel(new BorderLayout(6, 6));
        preview.add(new JScrollPane(skillPreview), BorderLayout.CENTER);
        JPanel previewButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton calculate = new JButton("Refresh Preview");
        JButton sandbox = new JButton("Run Seeded Battle Sandbox");
        calculate.addActionListener(event -> refreshPreview(draftSkill()));
        sandbox.addActionListener(event -> runSandbox(draftSkill()));
        previewButtons.add(calculate);
        previewButtons.add(sandbox);
        preview.add(previewButtons, BorderLayout.SOUTH);
        tabs.addTab("Preview & Sandbox", preview);

        JPanel result = new JPanel(new BorderLayout());
        result.add(tabs, BorderLayout.CENTER);
        return result;
    }

    private JPanel buildStatusEditor() {
        JTabbedPane tabs = new JTabbedPane();
        JPanel identity = formPanel();
        addRow(identity, "Stable ID", statusId);
        addRow(identity, "Display Name", statusName);
        addRow(identity, "Description", new JScrollPane(statusDescription));
        addRow(identity, "Icon Asset", statusIcon);
        addRow(identity, "Polarity", statusPolarity);
        addRow(identity, "Behavior", statusBehavior);
        addRow(identity, "Default Duration", statusDuration);
        addRow(identity, "Reapplication", statusStacking);
        addRow(identity, "Maximum Stacks", statusMaxStacks);
        tabs.addTab("Identity & Lifetime", new JScrollPane(identity));

        JPanel behavior = new JPanel(new BorderLayout());
        behavior.add(statusParameterPanel, BorderLayout.NORTH);
        tabs.addTab("Behavior Parameters", new JScrollPane(behavior));

        JPanel references = new JPanel(new BorderLayout());
        references.add(new JScrollPane(statusReferences), BorderLayout.CENTER);
        tabs.addTab("References & Diagnostics", references);

        JPanel result = new JPanel(new BorderLayout());
        result.add(tabs, BorderLayout.CENTER);
        return result;
    }

    private void refreshCatalog(String preferredId) {
        Kind kind = (Kind) kindBox.getSelectedItem();
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        catalogModel.clear();
        if (kind == Kind.STATUS) {
            statuses.values().stream()
                    .sorted(Comparator.comparing(StatusDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                    .filter(value -> matches(search, value.id(), value.displayName(), value.behaviorKindId(),
                            value.polarity().name()))
                    .map(value -> new CatalogEntry(value.id(), value.displayName(), Kind.STATUS))
                    .forEach(catalogModel::addElement);
        } else {
            skills.values().stream()
                    .sorted(Comparator.comparing(SkillDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
                    .filter(value -> matches(search, value.id(), value.displayName(),
                            value.targetingMode().name(), effectRoles(value)))
                    .map(value -> new CatalogEntry(value.id(), value.displayName(), Kind.SKILL))
                    .forEach(catalogModel::addElement);
        }
        selectId(preferredId);
        editorCards.show(editorPanel, (kind == null ? Kind.SKILL : kind).name());
    }

    private void loadSelection() {
        CatalogEntry entry = catalogList.getSelectedValue();
        if (entry == null) return;
        if (entry.kind() == Kind.SKILL) loadSkill(skills.get(entry.id()));
        else loadStatus(statuses.get(entry.id()));
    }

    private void loadSkill(SkillDefinition skill) {
        if (skill == null) return;
        loadedSkillId = skill.id();
        skillId.setText(skill.id());
        skillName.setText(skill.displayName());
        skillDescription.setText(skill.description());
        skillTeam.setSelectedItem(skill.targetTeam());
        skillShape.setSelectedItem(skill.targetShape());
        skillMode.setSelectedItem(skill.targetingMode());
        skillCooldown.setValue(skill.cooldownSeconds());
        skillSound.setText(skill.useSoundPath());
        skillPresentation.setSelectedItem(skill.presentationStyle());
        consumesAutoAction.setSelected(skill.consumesAutoAction());
        effectModel.clear();
        skill.effects().forEach(effectModel::addElement);
        defaultPoolBox.setSelected(defaultPool.contains(skill.id()));
        universalPoolBox.setSelected(universalPool.contains(skill.id()));
        debugPoolBox.setSelected(debugPool.contains(skill.id()));
        skillReferences.setText(referenceText(host.referencesToSkill(skill.id())));
        refreshPreview(skill);
        stateLabel.setText("Loaded battle skill " + skill.displayName() + ".");
    }

    private void loadStatus(StatusDefinition status) {
        if (status == null) return;
        loadedStatusId = status.id();
        loadedStatusParameters = status.parameters();
        statusId.setText(status.id());
        statusName.setText(status.displayName());
        statusDescription.setText(status.description());
        statusIcon.setText(status.iconPath());
        statusPolarity.setSelectedItem(status.polarity());
        selectDescriptor(statusBehavior, status.behaviorKindId());
        statusDuration.setValue(status.defaultDuration());
        statusStacking.setSelectedItem(status.stackingPolicy());
        statusMaxStacks.setValue(status.maxStacks());
        rebuildStatusParameters(status.parameters());
        List<String> references = new ArrayList<>(host.referencesToStatus(status.id()));
        for (SkillDefinition skill : skills.values()) {
            if (skill.effects().stream().anyMatch(effect ->
                    status.id().equals(BattleContentCatalog.normalizeId(effect.parameter("statusId", ""))))) {
                references.add("Battle skill " + skill.displayName());
            }
        }
        statusReferences.setText(referenceText(references));
        stateLabel.setText("Loaded status " + status.displayName() + ".");
    }

    private void applyDraft() {
        CatalogEntry entry = catalogList.getSelectedValue();
        if (entry == null) return;
        if (entry.kind() == Kind.SKILL) {
            SkillDefinition draft = draftSkill();
            if (draft == null) return;
            List<String> errors = validateSkillDraft(draft);
            if (!errors.isEmpty()) {
                showErrors(errors);
                return;
            }
            replaceSkill(loadedSkillId, draft);
            refreshCatalog(draft.id());
            stateLabel.setText("Applied skill draft. Save Catalogs to write it.");
        } else {
            StatusDefinition draft = draftStatus();
            if (draft == null) return;
            List<String> errors = new ArrayList<>(BattleContentTypeRegistry.validateDefinition(draft));
            if (!errors.isEmpty()) {
                showErrors(errors);
                return;
            }
            replaceStatus(loadedStatusId, draft);
            refreshCatalog(draft.id());
            stateLabel.setText("Applied status draft. Save Catalogs to write it.");
        }
    }

    private SkillDefinition draftSkill() {
        String id = BattleContentCatalog.normalizeId(skillId.getText());
        if (id.isBlank()) {
            stateLabel.setText("A stable skill id is required.");
            return null;
        }
        List<SkillEffectDefinition> effects = new ArrayList<>();
        for (int index = 0; index < effectModel.size(); index++) effects.add(effectModel.get(index));
        return new SkillDefinition(
                id,
                skillName.getText(),
                skillDescription.getText(),
                (Library.SkillTargetShape) skillShape.getSelectedItem(),
                (Library.EntityType) skillTeam.getSelectedItem(),
                (Library.BattleTargetingMode) skillMode.getSelectedItem(),
                skillSound.getText(),
                String.valueOf(skillPresentation.getSelectedItem()),
                ((Number) skillCooldown.getValue()).doubleValue(),
                consumesAutoAction.isSelected(),
                effects);
    }

    private StatusDefinition draftStatus() {
        String id = BattleContentCatalog.normalizeId(statusId.getText());
        if (id.isBlank()) {
            stateLabel.setText("A stable status id is required.");
            return null;
        }
        BattleContentTypeRegistry.HandlerDescriptor descriptor =
                (BattleContentTypeRegistry.HandlerDescriptor) statusBehavior.getSelectedItem();
        return new StatusDefinition(
                id,
                statusName.getText(),
                statusDescription.getText(),
                statusIcon.getText(),
                (StatusDefinition.Polarity) statusPolarity.getSelectedItem(),
                descriptor == null ? "" : descriptor.id(),
                ((Number) statusDuration.getValue()).intValue(),
                (StatusDefinition.StackingPolicy) statusStacking.getSelectedItem(),
                ((Number) statusMaxStacks.getValue()).intValue(),
                parameterValues(statusParameterInputs));
    }

    private void replaceSkill(String oldId, SkillDefinition replacement) {
        String normalizedOld = BattleContentCatalog.normalizeId(oldId);
        if (!normalizedOld.equals(replacement.id()) && skills.containsKey(replacement.id())) {
            throw new IllegalStateException("Skill id already exists: " + replacement.id());
        }
        LinkedHashMap<String, SkillDefinition> reordered = new LinkedHashMap<>();
        skills.forEach((id, value) -> reordered.put(
                id.equals(normalizedOld) ? replacement.id() : id,
                id.equals(normalizedOld) ? replacement : value));
        skills.clear();
        skills.putAll(reordered);
        if (!normalizedOld.equals(replacement.id())) {
            rewritePool(defaultPool, normalizedOld, replacement.id());
            rewritePool(universalPool, normalizedOld, replacement.id());
            rewritePool(debugPool, normalizedOld, replacement.id());
            skillReplacements.put(normalizedOld, replacement.id());
        }
        setPool(defaultPool, replacement.id(), defaultPoolBox.isSelected());
        setPool(universalPool, replacement.id(), universalPoolBox.isSelected());
        setPool(debugPool, replacement.id(), debugPoolBox.isSelected());
        loadedSkillId = replacement.id();
    }

    private void replaceStatus(String oldId, StatusDefinition replacement) {
        String normalizedOld = BattleContentCatalog.normalizeId(oldId);
        if (!normalizedOld.equals(replacement.id()) && statuses.containsKey(replacement.id())) {
            throw new IllegalStateException("Status id already exists: " + replacement.id());
        }
        LinkedHashMap<String, StatusDefinition> reordered = new LinkedHashMap<>();
        statuses.forEach((id, value) -> reordered.put(
                id.equals(normalizedOld) ? replacement.id() : id,
                id.equals(normalizedOld) ? replacement : value));
        statuses.clear();
        statuses.putAll(reordered);
        if (!normalizedOld.equals(replacement.id())) {
            rewriteStatusReferences(normalizedOld, replacement.id());
            statusReplacements.put(normalizedOld, replacement.id());
        }
        loadedStatusId = replacement.id();
    }

    private void createEntry() {
        if (kindBox.getSelectedItem() == Kind.STATUS) {
            String id = uniqueId("new_status", statuses.keySet());
            StatusDefinition created = new StatusDefinition(
                    id, "New Status", "", "", StatusDefinition.Polarity.HARMFUL,
                    "stat_modifier", 2, StatusDefinition.StackingPolicy.REFRESH, 1,
                    Map.of("stat", "AGILITY", "magnitude", "-1"));
            statuses.put(id, created);
            refreshCatalog(id);
        } else {
            String id = uniqueId("new_skill", skills.keySet());
            SkillDefinition created = new SkillDefinition(
                    id, "New Skill", "", Library.SkillTargetShape.SINGLE_TARGET,
                    Library.EntityType.ENEMY, Library.BattleTargetingMode.MAGIC,
                    "", "AUTO", 5, true,
                    List.of(new SkillEffectDefinition(
                            "damage", SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                            SkillEffectDefinition.ActivationCondition.ALWAYS, 1.0,
                            Map.of("potency", "5"))));
            skills.put(id, created);
            refreshCatalog(id);
        }
    }

    private void duplicateEntry() {
        CatalogEntry selected = catalogList.getSelectedValue();
        if (selected == null) return;
        if (selected.kind() == Kind.STATUS) {
            StatusDefinition source = statuses.get(selected.id());
            String id = uniqueId(source.id() + "_copy", statuses.keySet());
            statuses.put(id, new StatusDefinition(
                    id, source.displayName() + " Copy", source.description(), source.iconPath(),
                    source.polarity(), source.behaviorKindId(), source.defaultDuration(),
                    source.stackingPolicy(), source.maxStacks(), source.parameters()));
            refreshCatalog(id);
        } else {
            SkillDefinition source = skills.get(selected.id());
            String id = uniqueId(source.id() + "_copy", skills.keySet());
            skills.put(id, new SkillDefinition(
                    id, source.displayName() + " Copy", source.description(), source.targetShape(),
                    source.targetTeam(), source.targetingMode(), source.useSoundPath(),
                    source.presentationStyle(), source.cooldownSeconds(),
                    source.consumesAutoAction(), source.effects()));
            refreshCatalog(id);
        }
    }

    private void deleteEntry() {
        CatalogEntry selected = catalogList.getSelectedValue();
        if (selected == null) return;
        if (selected.kind() == Kind.SKILL) {
            List<String> references = new ArrayList<>(host.referencesToSkill(selected.id()));
            if (defaultPool.contains(selected.id()) || universalPool.contains(selected.id())
                    || debugPool.contains(selected.id())) references.add("Player skill pool");
            String replacement = chooseReplacement("skill", selected.id(), skills.keySet(), references);
            if (replacement == null) return;
            skills.remove(selected.id());
            rewritePool(defaultPool, selected.id(), replacement);
            rewritePool(universalPool, selected.id(), replacement);
            rewritePool(debugPool, selected.id(), replacement);
            if (!replacement.isBlank()) skillReplacements.put(selected.id(), replacement);
        } else {
            List<String> references = new ArrayList<>(host.referencesToStatus(selected.id()));
            skills.values().forEach(skill -> skill.effects().stream()
                    .filter(effect -> selected.id().equals(BattleContentCatalog.normalizeId(
                            effect.parameter("statusId", ""))))
                    .findAny().ifPresent(ignored -> references.add("Battle skill " + skill.displayName())));
            String replacement = chooseReplacement("status", selected.id(), statuses.keySet(), references);
            if (replacement == null) return;
            statuses.remove(selected.id());
            rewriteStatusReferences(selected.id(), replacement);
            if (!replacement.isBlank()) statusReplacements.put(selected.id(), replacement);
        }
        refreshCatalog("");
    }

    private String chooseReplacement(
            String label, String deletedId, Collection<String> candidates, List<String> references) {
        if (references.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Delete " + label + " '" + deletedId + "'?",
                    "Delete " + label, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            return result == JOptionPane.OK_OPTION ? "" : null;
        }
        List<String> replacements = candidates.stream().filter(id -> !id.equals(deletedId)).toList();
        if (replacements.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Deletion is blocked because this entry is referenced:\n" + referenceText(references),
                    "Referenced " + label, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        JComboBox<String> replacement = new JComboBox<>(replacements.toArray(new String[0]));
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel("Replace all references before deleting:"), BorderLayout.NORTH);
        panel.add(replacement, BorderLayout.CENTER);
        JTextArea details = readOnlyArea();
        details.setText(referenceText(references));
        panel.add(new JScrollPane(details), BorderLayout.SOUTH);
        int result = JOptionPane.showConfirmDialog(
                this, panel, "Replace Referenced " + label,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.OK_OPTION ? String.valueOf(replacement.getSelectedItem()) : null;
    }

    private void saveCatalogs() {
        BattleContentCatalog.Snapshot snapshot = new BattleContentCatalog.Snapshot(
                BattleContentCatalog.SCHEMA_VERSION,
                skills,
                statuses,
                defaultPool,
                universalPool,
                debugPool);
        List<String> errors = BattleContentCatalog.validate(snapshot);
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }
        List<String> warnings = catalogWarnings(snapshot);
        if (!warnings.isEmpty()) {
            JTextArea area = readOnlyArea();
            area.setText("- " + String.join("\n- ", warnings)
                    + "\n\nSave despite these warnings?");
            int confirmation = JOptionPane.showConfirmDialog(
                    this, new JScrollPane(area), "Battle Content Warnings",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmation != JOptionPane.OK_OPTION) return;
        }
        try {
            BattleContentCatalog.save(snapshot);
            host.catalogsSaved(Map.copyOf(skillReplacements), Map.copyOf(statusReplacements));
            skillReplacements.clear();
            statusReplacements.clear();
            stateLabel.setText("Saved skill and status catalogs.");
            refreshCatalog(selectedEntryId());
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Battle Content Save Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> catalogWarnings(BattleContentCatalog.Snapshot snapshot) {
        List<String> warnings = new ArrayList<>();
        for (SkillDefinition skill : snapshot.skills().values()) {
            if (!skill.useSoundPath().isBlank() && !assetExists(skill.useSoundPath())) {
                warnings.add("Skill " + skill.id() + " references missing sound " + skill.useSoundPath() + ".");
            }
            for (SkillEffectDefinition effect : skill.effects()) {
                if (BattleContentTypeRegistry.effectDescriptor(effect.kindId()) == null) {
                    warnings.add("Skill " + skill.id() + " uses unavailable handler "
                            + effect.kindId() + "; its raw data will be preserved.");
                }
            }
        }
        for (StatusDefinition status : snapshot.statuses().values()) {
            if (!status.iconPath().isBlank() && !assetExists(status.iconPath())) {
                warnings.add("Status " + status.id() + " references missing icon " + status.iconPath() + ".");
            }
            if (BattleContentTypeRegistry.statusBehaviorDescriptor(status.behaviorKindId()) == null) {
                warnings.add("Status " + status.id() + " uses unavailable behavior "
                        + status.behaviorKindId() + "; its raw data will be preserved.");
            }
        }
        return warnings;
    }

    private boolean assetExists(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.trim().replace('\\', '/');
        if (normalized.isBlank()) return true;
        Path source = Path.of("src", "main", "resources").resolve(normalized);
        return Files.isRegularFile(source)
                || getClass().getClassLoader().getResource(normalized) != null;
    }

    private void editEffect(SkillEffectDefinition source, int replaceIndex) {
        List<BattleContentTypeRegistry.HandlerDescriptor> descriptors =
                new ArrayList<>(BattleContentTypeRegistry.effectDescriptors());
        JComboBox<BattleContentTypeRegistry.HandlerDescriptor> kind = new JComboBox<>(
                descriptors.toArray(new BattleContentTypeRegistry.HandlerDescriptor[0]));
        JComboBox<SkillEffectDefinition.RecipientScope> recipient =
                new JComboBox<>(SkillEffectDefinition.RecipientScope.values());
        JComboBox<SkillEffectDefinition.ActivationCondition> condition =
                new JComboBox<>(SkillEffectDefinition.ActivationCondition.values());
        JSpinner chance = new JSpinner(new SpinnerNumberModel(
                source == null ? 100.0 : source.chance() * 100.0, 0.0, 100.0, 0.5));
        JPanel parameters = new JPanel(new GridBagLayout());
        Map<String, Component> inputs = new LinkedHashMap<>();
        Runnable rebuild = () -> {
            BattleContentTypeRegistry.HandlerDescriptor selected =
                    (BattleContentTypeRegistry.HandlerDescriptor) kind.getSelectedItem();
            rebuildParameterPanel(parameters, inputs, selected,
                    source == null ? Map.of() : source.parameters());
        };
        kind.addActionListener(event -> rebuild.run());
        if (source != null) selectDescriptor(kind, source.kindId());
        recipient.setSelectedItem(source == null
                ? SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS : source.recipientScope());
        condition.setSelectedItem(source == null
                ? SkillEffectDefinition.ActivationCondition.ALWAYS : source.condition());
        rebuild.run();

        JPanel form = formPanel();
        addRow(form, "Effect Kind", kind);
        addRow(form, "Recipients", recipient);
        addRow(form, "Condition", condition);
        addRow(form, "Chance %", chance);
        addRow(form, "Parameters", parameters);
        int result = JOptionPane.showConfirmDialog(
                this, new JScrollPane(form), source == null ? "Add Effect" : "Edit Effect",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        BattleContentTypeRegistry.HandlerDescriptor descriptor =
                (BattleContentTypeRegistry.HandlerDescriptor) kind.getSelectedItem();
        SkillEffectDefinition edited = new SkillEffectDefinition(
                descriptor == null ? "" : descriptor.id(),
                (SkillEffectDefinition.RecipientScope) recipient.getSelectedItem(),
                (SkillEffectDefinition.ActivationCondition) condition.getSelectedItem(),
                ((Number) chance.getValue()).doubleValue() / 100.0,
                parameterValues(inputs));
        List<String> errors = BattleContentTypeRegistry.validateDefinition(edited);
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }
        if (replaceIndex >= 0) effectModel.set(replaceIndex, edited);
        else effectModel.addElement(edited);
    }

    private void rebuildStatusParameters(Map<String, String> values) {
        BattleContentTypeRegistry.HandlerDescriptor descriptor =
                (BattleContentTypeRegistry.HandlerDescriptor) statusBehavior.getSelectedItem();
        Map<String, String> effective = values.isEmpty() ? loadedStatusParameters : values;
        rebuildParameterPanel(statusParameterPanel, statusParameterInputs, descriptor, effective);
    }

    private void rebuildParameterPanel(
            JPanel panel,
            Map<String, Component> inputs,
            BattleContentTypeRegistry.HandlerDescriptor descriptor,
            Map<String, String> values) {
        panel.removeAll();
        inputs.clear();
        if (descriptor == null) {
            panel.add(new JLabel("Handler is unavailable; raw values are preserved."));
            panel.revalidate();
            panel.repaint();
            return;
        }
        int row = 0;
        for (AuthoringFieldDescriptor field : descriptor.fields()) {
            Component input = inputFor(field, values.getOrDefault(field.key(), field.defaultValue()));
            inputs.put(field.key(), input);
            GridBagConstraints label = constraints(0, row);
            label.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel(field.label()), label);
            GridBagConstraints value = constraints(1, row++);
            value.weightx = 1.0;
            value.fill = GridBagConstraints.HORIZONTAL;
            JPanel cell = new JPanel(new BorderLayout());
            cell.add(input, BorderLayout.NORTH);
            if (!field.help().isBlank()) {
                JLabel help = new JLabel("<html><small>" + field.help() + "</small></html>");
                cell.add(help, BorderLayout.SOUTH);
            }
            panel.add(cell, value);
        }
        GridBagConstraints filler = constraints(0, row);
        filler.gridwidth = 2;
        filler.weighty = 1;
        panel.add(new JPanel(), filler);
        panel.revalidate();
        panel.repaint();
    }

    private Component inputFor(AuthoringFieldDescriptor field, String value) {
        return switch (field.type()) {
            case INTEGER -> new JSpinner(new SpinnerNumberModel(
                    integer(value, integer(field.defaultValue(), 0)),
                    (int) field.minimum(), (int) field.maximum(), 1));
            case DECIMAL, PERCENT -> new JSpinner(new SpinnerNumberModel(
                    decimal(value, decimal(field.defaultValue(), 0)),
                    field.minimum(), field.maximum(), 0.1));
            case CHOICE -> {
                JComboBox<String> combo = new JComboBox<>(field.options().toArray(new String[0]));
                combo.setSelectedItem(value);
                yield combo;
            }
            case STATUS_REFERENCE -> {
                JComboBox<String> combo = new JComboBox<>(statuses.keySet().toArray(new String[0]));
                combo.setEditable(true);
                combo.setSelectedItem(value);
                yield combo;
            }
            default -> new JTextField(value, 24);
        };
    }

    private Map<String, String> parameterValues(Map<String, Component> inputs) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        inputs.forEach((key, input) -> {
            String value;
            if (input instanceof JSpinner spinner) value = String.valueOf(spinner.getValue());
            else if (input instanceof JComboBox<?> combo) value = String.valueOf(combo.getSelectedItem());
            else if (input instanceof JTextField field) value = field.getText();
            else value = "";
            values.put(key, value == null ? "" : value.trim());
        });
        return values;
    }

    private void refreshPreview(SkillDefinition skill) {
        if (skill == null) return;
        StringBuilder text = new StringBuilder();
        text.append(skill.displayName()).append(" [").append(skill.id()).append("]\n");
        text.append(skill.targetTeam()).append(" / ").append(skill.targetShape())
                .append(" / ").append(skill.targetingMode()).append('\n');
        text.append("Cooldown: ").append(skill.cooldownSeconds()).append(" sec\n\n");
        int index = 1;
        for (SkillEffectDefinition effect : skill.effects()) {
            BattleContentTypeRegistry.HandlerDescriptor descriptor =
                    BattleContentTypeRegistry.effectDescriptor(effect.kindId());
            text.append(index++).append(". ")
                    .append(descriptor == null ? effect.kindId() : descriptor.label())
                    .append(" [").append(effect.recipientScope()).append("]\n")
                    .append("   Condition: ").append(effect.condition())
                    .append(", chance ").append(Math.round(effect.chance() * 100)).append("%\n")
                    .append("   Values: ").append(effect.parameters()).append('\n');
        }
        List<String> errors = validateSkillDraft(skill);
        text.append("\nDiagnostics: ").append(errors.isEmpty() ? "No errors." : String.join("\n- ", errors));
        skillPreview.setText(text.toString());
    }

    private void runSandbox(SkillDefinition skill) {
        if (skill == null) return;
        List<String> errors = validateSkillDraft(skill);
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }
        BattleEncounter.SandboxResult result = BattleSkillSandbox.run(skill, 42L);
        skillPreview.append("\n\nSeed 42 sandbox\n");
        skillPreview.append(result.message() + "\n");
        skillPreview.append("Caster HP: " + result.casterHpBefore() + " -> " + result.casterHpAfter() + "\n");
        skillPreview.append("Target HP: " + result.targetHpBefore() + " -> " + result.targetHpAfter() + "\n");
        skillPreview.append("Statuses: " + (result.statuses().isEmpty() ? "none" : result.statuses()));
    }

    private List<String> validateSkillDraft(SkillDefinition skill) {
        List<String> errors = new ArrayList<>();
        if (skill.effects().isEmpty()) errors.add("At least one effect is required.");
        for (SkillEffectDefinition effect : skill.effects()) {
            errors.addAll(BattleContentTypeRegistry.validateDefinition(effect));
            if (("apply_status".equals(effect.kindId()) || "remove_status".equals(effect.kindId()))
                    && !effect.parameter("statusId", "").isBlank()
                    && !statuses.containsKey(BattleContentCatalog.normalizeId(effect.parameter("statusId", "")))) {
                errors.add("Unknown status " + effect.parameter("statusId", "") + ".");
            }
        }
        return errors;
    }

    private void rewriteStatusReferences(String oldId, String newId) {
        List<SkillDefinition> replacements = new ArrayList<>();
        for (SkillDefinition skill : skills.values()) {
            List<SkillEffectDefinition> effects = new ArrayList<>();
            for (SkillEffectDefinition effect : skill.effects()) {
                if (oldId.equals(BattleContentCatalog.normalizeId(effect.parameter("statusId", "")))) {
                    LinkedHashMap<String, String> parameters = new LinkedHashMap<>(effect.parameters());
                    parameters.put("statusId", newId);
                    effects.add(new SkillEffectDefinition(
                            effect.kindId(), effect.recipientScope(), effect.condition(),
                            effect.chance(), parameters));
                } else {
                    effects.add(effect);
                }
            }
            replacements.add(new SkillDefinition(
                    skill.id(), skill.displayName(), skill.description(), skill.targetShape(),
                    skill.targetTeam(), skill.targetingMode(), skill.useSoundPath(),
                    skill.presentationStyle(), skill.cooldownSeconds(),
                    skill.consumesAutoAction(), effects));
        }
        skills.clear();
        replacements.forEach(skill -> skills.put(skill.id(), skill));
    }

    private void moveEffect(int delta) {
        int index = effectList.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= effectModel.size()) return;
        SkillEffectDefinition value = effectModel.remove(index);
        effectModel.add(target, value);
        effectList.setSelectedIndex(target);
    }

    private void showErrors(List<String> errors) {
        JTextArea area = readOnlyArea();
        area.setText("- " + String.join("\n- ", errors));
        JOptionPane.showMessageDialog(
                this, new JScrollPane(area), "Validation Errors", JOptionPane.ERROR_MESSAGE);
    }

    private String selectedEntryId() {
        CatalogEntry entry = catalogList.getSelectedValue();
        return entry == null ? "" : entry.id();
    }

    private void selectId(String id) {
        for (int index = 0; index < catalogModel.size(); index++) {
            if (catalogModel.get(index).id().equals(id)) {
                catalogList.setSelectedIndex(index);
                catalogList.ensureIndexIsVisible(index);
                return;
            }
        }
        if (!catalogModel.isEmpty()) catalogList.setSelectedIndex(0);
    }

    private static void selectDescriptor(
            JComboBox<BattleContentTypeRegistry.HandlerDescriptor> combo, String id) {
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).id().equalsIgnoreCase(id)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private static JPanel formPanel() {
        return new JPanel(new GridBagLayout());
    }

    private static void addRow(JPanel panel, String labelText, Component component) {
        int row = panel.getComponentCount() / 2;
        GridBagConstraints label = constraints(0, row);
        label.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel(labelText), label);
        GridBagConstraints value = constraints(1, row);
        value.weightx = 1.0;
        value.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, value);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.insets = new Insets(4, 6, 4, 6);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        return constraints;
    }

    private static JPanel stack(Component... components) {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        for (Component component : components) panel.add(component);
        return panel;
    }

    private static JTextArea readOnlyArea() {
        JTextArea area = new JTextArea(8, 40);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static boolean matches(String search, String... values) {
        if (search.isBlank()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(search)) return true;
        }
        return false;
    }

    private static String effectRoles(SkillDefinition skill) {
        return skill.effects().stream()
                .map(effect -> BattleContentTypeRegistry.effectDescriptor(effect.kindId()))
                .filter(java.util.Objects::nonNull)
                .map(BattleContentTypeRegistry.HandlerDescriptor::aiRole)
                .distinct()
                .reduce("", (left, right) -> left + " " + right);
    }

    private static String referenceText(List<String> references) {
        return references == null || references.isEmpty()
                ? "No references."
                : "- " + String.join("\n- ", references);
    }

    private static String uniqueId(String base, Collection<String> existing) {
        String normalized = BattleContentCatalog.normalizeId(base);
        String result = normalized;
        int suffix = 2;
        while (existing.contains(result)) result = normalized + "_" + suffix++;
        return result;
    }

    private static void rewritePool(List<String> pool, String oldId, String newId) {
        for (int index = pool.size() - 1; index >= 0; index--) {
            if (!pool.get(index).equals(oldId)) continue;
            if (newId == null || newId.isBlank()) pool.remove(index);
            else pool.set(index, newId);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(pool);
        pool.clear();
        pool.addAll(unique);
    }

    private static void setPool(List<String> pool, String id, boolean selected) {
        pool.removeIf(id::equals);
        if (selected) pool.add(id);
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; }
    }

    private static double decimal(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (RuntimeException ignored) { return fallback; }
    }

    private record CatalogEntry(String id, String label, Kind kind) {
        @Override public String toString() { return label + "  [" + id + "]"; }
    }

    @FunctionalInterface
    private interface ChangeAction { void run(); }

    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final ChangeAction action;

        private SimpleDocumentListener(ChangeAction action) { this.action = action; }

        @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
    }
}

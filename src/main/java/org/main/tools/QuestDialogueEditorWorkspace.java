package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.core.CharacterSkill;

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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modeless visual authoring workspace shared by standalone dialogue and quests.
 */
public final class QuestDialogueEditorWorkspace extends JDialog {
    public enum Kind {
        QUEST,
        DIALOGUE
    }

    public interface Host {
        List<MapDesignLibrary.AuthoredQuest> quests();

        List<MapDesignLibrary.AuthoredDialogue> dialogues();

        List<MapDesignLibrary.CustomNpc> npcs();

        List<MapDesignLibrary.CustomItem> items();

        List<MapDesignLibrary.CustomLimb> limbs();

        List<MapDesignLibrary.CustomMob> mobs();

        List<MapDesignLibrary.ValidationIssue> validate(
                List<MapDesignLibrary.AuthoredQuest> quests,
                List<MapDesignLibrary.AuthoredDialogue> dialogues,
                List<MapDesignLibrary.CustomNpc> npcs
        );

        void save(
                List<MapDesignLibrary.AuthoredQuest> quests,
                List<MapDesignLibrary.AuthoredDialogue> dialogues,
                List<MapDesignLibrary.CustomNpc> npcs
        ) throws IOException;
    }

    private final Host host;
    private final Kind kind;
    private final LinkedHashMap<String, MapDesignLibrary.AuthoredQuest> quests = new LinkedHashMap<>();
    private final LinkedHashMap<String, MapDesignLibrary.AuthoredDialogue> dialogues = new LinkedHashMap<>();
    private final List<MapDesignLibrary.CustomNpc> npcs = new ArrayList<>();
    private final DefaultListModel<CatalogEntry> catalogModel = new DefaultListModel<>();
    private final JList<CatalogEntry> catalogList = new JList<>(catalogModel);
    private final JTextField searchField = new JTextField(18);
    private final JTextField idField = new JTextField(24);
    private final JTextField nameField = new JTextField(24);
    private final JTextArea summaryArea = new JTextArea(3, 28);
    private final SearchableReferenceBox followUpBox = new SearchableReferenceBox(List.of(""));
    private final DefaultListModel<FlowSlot> flowSlotModel = new DefaultListModel<>();
    private final JList<FlowSlot> flowSlotList = new JList<>(flowSlotModel);
    private final QuestFlowCanvas canvas = new QuestFlowCanvas();
    private final JTextField nodeIdField = new JTextField(20);
    private final JTextArea nodeBodyArea = new JTextArea(7, 26);
    private final DefaultListModel<MapDesignLibrary.QuestFlowChoice> choiceModel = new DefaultListModel<>();
    private final JList<MapDesignLibrary.QuestFlowChoice> choiceList = new JList<>(choiceModel);
    private final JComboBox<MapDesignLibrary.QuestCompletionMode> completionMode =
            new JComboBox<>(MapDesignLibrary.QuestCompletionMode.values());
    private final JLabel selectedStageLabel = new JLabel("Click a stage section on the flow map.");
    private final JTextField stageTitleField = new JTextField(22);
    private final JTextArea stageJournalArea = new JTextArea(4, 24);
    private final DefaultListModel<MapDesignLibrary.QuestRequirement> requirementModel = new DefaultListModel<>();
    private final DefaultListModel<MapDesignLibrary.QuestObjective> objectiveModel = new DefaultListModel<>();
    private final DefaultListModel<MapDesignLibrary.RewardDefinition> dialogueRewardModel = new DefaultListModel<>();
    private final DefaultListModel<MapDesignLibrary.RewardDefinition> stageRewardModel = new DefaultListModel<>();
    private final DefaultListModel<MapDesignLibrary.RewardDefinition> finalRewardModel = new DefaultListModel<>();
    private final DefaultListModel<String> assignmentModel = new DefaultListModel<>();
    private final JTextArea diagnosticsArea = new JTextArea(8, 35);
    private final JLabel stateLabel = new JLabel("Ready.");
    private final JLabel activeFlowSectionLabel = new JLabel("Selected flow: none");
    private final JLabel nodeFlowSectionLabel = new JLabel("Editing flow: none");
    private final JTabbedPane inspectorTabs = new JTabbedPane();
    private String loadedId = "";
    private String dialogueFirstTalkNodeId = "";
    private String dialogueRepeatTalkNodeId = "";
    private FlowSlot loadedFlowSlot;
    private String questInspectorOwnerId = "";
    private String stageEditorQuestId = "";
    private String stageEditorStageId = "";
    private boolean dirty;
    private boolean loading;

    public static void open(Window owner, Kind kind, String selectedId, Host host) {
        new QuestDialogueEditorWorkspace(owner, kind, selectedId, host).setVisible(true);
    }

    public static void openNew(Window owner, Kind kind, Host host) {
        QuestDialogueEditorWorkspace workspace =
                new QuestDialogueEditorWorkspace(owner, kind, "", host);
        workspace.createEntry();
        workspace.setVisible(true);
    }

    private QuestDialogueEditorWorkspace(Window owner, Kind kind, String selectedId, Host host) {
        super(owner, kind == Kind.QUEST ? "Quest Flow Editor" : "Dialogue Flow Editor", ModalityType.MODELESS);
        this.host = host;
        this.kind = kind == null ? Kind.QUEST : kind;
        host.quests().forEach(quest -> quests.put(quest.questId(), quest));
        host.dialogues().forEach(dialogue -> dialogues.put(dialogue.interactionId(), dialogue));
        npcs.addAll(host.npcs());
        followUpBox.replaceOptions(dialogueOptions());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        canvas.setSelectionListener(this::loadNodeInspector);
        canvas.setSectionSelectionListener(ignored -> synchronizeNavigatorWithCanvas());
        canvas.setChangeListener(this::markDirty);
        canvas.setMessageListener(stateLabel::setText);
        catalogList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !loading) {
                switchSelection();
            }
        });
        flowSlotList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !loading) {
                commitCurrentFlow();
                loadSelectedFlow();
            }
        });
        searchField.getDocument().addDocumentListener(documentListener(() -> refreshCatalog(loadedId)));
        DocumentListener dirtyListener = documentListener(this::markDirty);
        idField.getDocument().addDocumentListener(dirtyListener);
        nameField.getDocument().addDocumentListener(dirtyListener);
        summaryArea.getDocument().addDocumentListener(dirtyListener);
        followUpBox.addActionListener(event -> markDirty());
        stageTitleField.getDocument().addDocumentListener(dirtyListener);
        stageJournalArea.getDocument().addDocumentListener(dirtyListener);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (!dirty || confirmDiscard()) {
                    dispose();
                }
            }
        });
        refreshCatalog(selectedId);
        ConstructionKitUi.configureWorkspace(this);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(kind == Kind.QUEST ? "Quests" : "Dialogues"));
        panel.add(searchField);
        JButton create = new JButton("New");
        JButton duplicate = new JButton("Duplicate");
        JButton rename = new JButton("Rename ID");
        JButton delete = new JButton("Delete");
        create.addActionListener(event -> createEntry());
        duplicate.addActionListener(event -> duplicateEntry());
        rename.addActionListener(event -> renameEntry());
        delete.addActionListener(event -> deleteEntry());
        panel.add(create);
        panel.add(duplicate);
        panel.add(rename);
        panel.add(delete);
        return panel;
    }

    private Component buildBody() {
        catalogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : value.label() + "  [" + value.id() + "]");
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JScrollPane(catalogList), BorderLayout.CENTER);
        if (kind == Kind.DIALOGUE) {
            left.add(new JScrollPane(flowSlotList), BorderLayout.SOUTH);
            flowSlotList.setVisibleRowCount(3);
        } else {
            JLabel mapHint = new JLabel(
                    "<html><b>One quest-wide Flow Map</b><br>"
                            + "Click a section or node to inspect it.<br>"
                            + "The ENTRY node in Starting Dialogue / Offer is the NPC's opening quest line.</html>"
            );
            mapHint.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            left.add(mapHint, BorderLayout.SOUTH);
        }

        JPanel center = new JPanel(new BorderLayout(4, 4));
        JPanel canvasButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton autoLayout = new JButton("Auto Layout");
        autoLayout.addActionListener(event -> canvas.autoLayout());
        canvasButtons.add(activeFlowSectionLabel);
        canvasButtons.add(autoLayout);
        if (kind == Kind.QUEST) {
            canvasButtons.add(new JLabel(
                    "Green dashed = acceptance; amber dashed = stage progression"
            ));
        }
        center.add(canvasButtons, BorderLayout.NORTH);
        center.add(new JScrollPane(canvas), BorderLayout.CENTER);

        inspectorTabs.addTab("Identity", buildIdentityPanel());
        inspectorTabs.addTab("Node", buildNodePanel());
        if (kind == Kind.QUEST) {
            inspectorTabs.addTab("Stage", buildStagePanel());
            inspectorTabs.addTab("Requirements", listEditorPanel(
                    requirementModel,
                    this::addRequirement,
                    () -> removeSelected(requirementModel, null),
                    "All start requirements must pass; unavailable quests remain hidden."
            ));
            inspectorTabs.addTab("Objectives", listEditorPanel(
                    objectiveModel,
                    this::addObjective,
                    () -> removeSelected(objectiveModel, null),
                    "Objectives belong to the selected journal stage."
            ));
            inspectorTabs.addTab("Rewards", buildRewardsPanel());
            inspectorTabs.addTab("NPC Assignments", buildAssignmentsPanel());
        } else {
            inspectorTabs.addTab("Default Rewards", buildDialogueRewardsPanel());
        }
        diagnosticsArea.setEditable(false);
        diagnosticsArea.setLineWrap(true);
        diagnosticsArea.setWrapStyleWord(true);
        inspectorTabs.addTab("Diagnostics", new JScrollPane(diagnosticsArea));

        JSplitPane centerRight = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                center,
                inspectorTabs
        );
        centerRight.setResizeWeight(0.72);
        JSplitPane all = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                left,
                centerRight
        );
        all.setResizeWeight(0.16);
        return all;
    }

    private JPanel buildIdentityPanel() {
        JPanel form = form();
        idField.setEditable(false);
        idField.setToolTipText("Use Rename ID so every reference is rewritten transactionally.");
        if (kind == Kind.DIALOGUE) {
            summaryArea.setEditable(false);
            summaryArea.setToolTipText("Edit the entry node's NPC text on the Node tab.");
        }
        addRow(form, "Stable ID", idField);
        addRow(form, kind == Kind.QUEST ? "Quest Name" : "Speaker", nameField);
        addRow(form, kind == Kind.QUEST ? "Summary" : "Entry Text (Node tab)", new JScrollPane(summaryArea));
        if (kind == Kind.DIALOGUE) {
            addRow(form, "Follow-up Dialogue", followUpBox);
        }
        return form;
    }

    private JPanel buildNodePanel() {
        nodeBodyArea.setLineWrap(true);
        nodeBodyArea.setWrapStyleWord(true);
        choiceList.setVisibleRowCount(8);
        choiceList.setCellRenderer((list, value, index, selected, focus) -> {
            String action = value == null || value.action() == MapDesignLibrary.QuestFlowAction.NONE
                    ? ""
                    : "  {" + value.action() + "}";
            String target = value == null || value.targetNodeId().isBlank() ? "END" : value.targetNodeId();
            JLabel label = new JLabel(value == null ? "" : value.label() + " -> " + target + action);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel form = form();
        addRow(form, "Node ID", nodeIdField);
        addRow(form, "NPC Text", new JScrollPane(nodeBodyArea));
        JPanel nodeHeader = new JPanel(new BorderLayout(4, 4));
        nodeFlowSectionLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        nodeHeader.add(nodeFlowSectionLabel, BorderLayout.NORTH);
        nodeHeader.add(form, BorderLayout.CENTER);
        panel.add(nodeHeader, BorderLayout.NORTH);
        panel.add(new JScrollPane(choiceList), BorderLayout.CENTER);
        JPanel nodeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addNode = new JButton("Add Node");
        JButton duplicateNode = new JButton("Duplicate Node");
        JButton deleteNode = new JButton("Delete Node");
        JButton entryNode = new JButton(
                kind == Kind.QUEST ? "Make Entry" : "Set First Talk"
        );
        JButton repeatEntryNode = new JButton("Set Repeat Talk");
        JButton saveNode = new JButton("Update Node");
        addNode.setToolTipText(
                "Adds a node to the flow named above. The first node becomes its entry automatically."
        );
        addNode.addActionListener(event -> addNode());
        duplicateNode.addActionListener(event -> duplicateNode());
        deleteNode.addActionListener(event -> canvas.removeSelectedNode());
        entryNode.addActionListener(event -> setSelectedEntryNode());
        repeatEntryNode.addActionListener(event -> {
            MapDesignLibrary.QuestFlowNode node = canvas.selectedNode();
            if (node == null) {
                stateLabel.setText("Select a node before setting the repeat-talk entry.");
                return;
            }
            dialogueRepeatTalkNodeId = node.nodeId();
            markDirty();
            stateLabel.setText("Repeat-talk entry set to " + node.nodeId() + ".");
        });
        saveNode.addActionListener(event -> saveNode());
        nodeButtons.add(addNode);
        nodeButtons.add(duplicateNode);
        nodeButtons.add(deleteNode);
        nodeButtons.add(entryNode);
        if (kind == Kind.DIALOGUE) {
            nodeButtons.add(repeatEntryNode);
        }
        nodeButtons.add(saveNode);

        JPanel choiceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addChoice = new JButton("Add Choice");
        JButton editChoice = new JButton("Edit Choice");
        JButton removeChoice = new JButton("Remove Choice");
        JButton choiceUp = new JButton("Up");
        JButton choiceDown = new JButton("Down");
        addChoice.addActionListener(event -> editChoice(-1));
        editChoice.addActionListener(event -> editChoice(choiceList.getSelectedIndex()));
        removeChoice.addActionListener(event -> {
            int index = choiceList.getSelectedIndex();
            if (index >= 0) {
                choiceModel.remove(index);
                saveNode();
            }
        });
        choiceUp.addActionListener(event -> moveChoice(-1));
        choiceDown.addActionListener(event -> moveChoice(1));
        choiceButtons.add(addChoice);
        choiceButtons.add(editChoice);
        choiceButtons.add(removeChoice);
        choiceButtons.add(choiceUp);
        choiceButtons.add(choiceDown);

        JPanel buttons = new JPanel(new java.awt.GridLayout(2, 1, 2, 2));
        buttons.add(nodeButtons);
        buttons.add(choiceButtons);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStagePanel() {
        stageJournalArea.setLineWrap(true);
        stageJournalArea.setWrapStyleWord(true);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel form = form();
        addRow(form, "Selected Section", selectedStageLabel);
        addRow(form, "Journal Title", stageTitleField);
        addRow(form, "Journal Text", new JScrollPane(stageJournalArea));
        addRow(form, "Completion", completionMode);
        panel.add(form, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton update = new JButton("Update Stage");
        JButton add = new JButton("Add Stage");
        JButton remove = new JButton("Remove Stage");
        JButton up = new JButton("Move Up");
        JButton down = new JButton("Move Down");
        update.addActionListener(event -> updateStage());
        add.addActionListener(event -> addStage());
        remove.addActionListener(event -> removeStage());
        up.addActionListener(event -> moveStage(-1));
        down.addActionListener(event -> moveStage(1));
        buttons.add(update);
        buttons.add(add);
        buttons.add(remove);
        buttons.add(up);
        buttons.add(down);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildRewardsPanel() {
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 1, 4, 4));
        panel.add(rewardEditorPanel(stageRewardModel, "Selected stage rewards", true));
        panel.add(rewardEditorPanel(finalRewardModel, "Quest completion rewards", true));
        return panel;
    }

    private JPanel buildDialogueRewardsPanel() {
        return rewardEditorPanel(
                dialogueRewardModel,
                "Once-only rewards granted when this dialogue opens",
                true
        );
    }

    private JPanel rewardEditorPanel(
            DefaultListModel<MapDesignLibrary.RewardDefinition> model,
            String title,
            boolean commitDirty
    ) {
        JList<MapDesignLibrary.RewardDefinition> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((component, reward, index, selected, focus) -> {
            String detail = reward == null ? "" : switch (reward.type()) {
                case ITEM -> reward.amount() + " × " + referenceDisplay(reward.itemId(), itemOptions());
                case GOLD -> reward.amount() + " gold";
                case SKILL_XP -> reward.amount() + " "
                        + (reward.skill() == null ? "[Missing skill]" : reward.skill().getDisplayName()) + " XP";
            };
            JLabel label = new JLabel(reward == null ? "" : detail + "  [" + reward.rewardId() + "]");
            label.setOpaque(true);
            label.setBackground(selected ? component.getSelectionBackground() : component.getBackground());
            label.setForeground(selected ? component.getSelectionForeground() : component.getForeground());
            return label;
        });
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton duplicate = new JButton("Duplicate");
        JButton remove = new JButton("Remove");
        JButton up = new JButton("Up");
        JButton down = new JButton("Down");
        add.addActionListener(event -> {
            if (!canEditRewardModel(model)) {
                return;
            }
            MapDesignLibrary.RewardDefinition reward = showRewardDialog(null, list(model));
            if (reward != null) {
                model.addElement(reward);
                if (commitDirty) {
                    markDirty();
                }
            }
        });
        edit.addActionListener(event -> {
            if (!canEditRewardModel(model)) {
                return;
            }
            int index = list.getSelectedIndex();
            if (index >= 0) {
                MapDesignLibrary.RewardDefinition reward = showRewardDialog(model.get(index), list(model));
                if (reward != null) {
                    model.set(index, reward);
                    if (commitDirty) {
                        markDirty();
                    }
                }
            }
        });
        duplicate.addActionListener(event -> {
            if (!canEditRewardModel(model)) {
                return;
            }
            int index = list.getSelectedIndex();
            if (index >= 0) {
                MapDesignLibrary.RewardDefinition source = model.get(index);
                MapDesignLibrary.RewardDefinition copy = new MapDesignLibrary.RewardDefinition(
                        uniqueRewardId(list(model), source.rewardId() + "_copy"),
                        source.type(),
                        source.itemId(),
                        source.skill(),
                        source.amount()
                );
                model.add(index + 1, copy);
                list.setSelectedIndex(index + 1);
                if (commitDirty) {
                    markDirty();
                }
            }
        });
        remove.addActionListener(event -> {
            if (!canEditRewardModel(model)) {
                return;
            }
            int index = list.getSelectedIndex();
            if (index >= 0) {
                model.remove(index);
                if (commitDirty) {
                    markDirty();
                }
            }
        });
        java.util.function.IntConsumer move = delta -> {
            if (!canEditRewardModel(model)) {
                return;
            }
            int index = list.getSelectedIndex();
            int destination = index + delta;
            if (index >= 0 && destination >= 0 && destination < model.size()) {
                MapDesignLibrary.RewardDefinition reward = model.remove(index);
                model.add(destination, reward);
                list.setSelectedIndex(destination);
                if (commitDirty) {
                    markDirty();
                }
            }
        };
        up.addActionListener(event -> move.accept(-1));
        down.addActionListener(event -> move.accept(1));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(duplicate);
        buttons.add(remove);
        buttons.add(up);
        buttons.add(down);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private boolean canEditRewardModel(
            DefaultListModel<MapDesignLibrary.RewardDefinition> model
    ) {
        if (model != stageRewardModel || hasSelectedStage()) {
            return true;
        }
        stateLabel.setText("Select a journal-stage section on the Flow Map before editing stage rewards.");
        return false;
    }

    private JPanel buildAssignmentsPanel() {
        JList<String> assignments = new JList<>(assignmentModel);
        assignments.setCellRenderer((list, npcId, index, selected, focus) -> {
            MapDesignLibrary.CustomNpc npc = npcs.stream()
                    .filter(candidate -> candidate.npcId().equals(npcId)).findFirst().orElse(null);
            int order = npc == null ? -1 : npc.questIds().indexOf(loadedId);
            String labelText = npc == null
                    ? npcId + " [Unavailable]"
                    : npc.displayName() + "  [" + npc.npcId() + "]  topic " + (order + 1);
            JLabel label = new JLabel(labelText);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JScrollPane(assignments), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Assign NPC");
        JButton remove = new JButton("Remove");
        JButton earlier = new JButton("Earlier");
        JButton later = new JButton("Later");
        add.addActionListener(event -> {
            List<String> available = npcs.stream()
                    .filter(npc -> !npc.questIds().contains(loadedId))
                    .map(npc -> npc.displayName() + " [" + npc.npcId() + "]")
                    .toList();
            if (available.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this, "Every authored NPC is already assigned to this quest.",
                        "NPC Assignment", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Object selected = JOptionPane.showInputDialog(
                    this, "Assign this quest to an NPC:", "NPC Assignment",
                    JOptionPane.PLAIN_MESSAGE, null, available.toArray(),
                    available.isEmpty() ? null : available.get(0));
            if (selected == null) {
                return;
            }
            String label = selected.toString();
            String npcId = label.substring(label.lastIndexOf('[') + 1, label.length() - 1);
            updateNpcQuestAssignment(npcId, 0, true);
        });
        remove.addActionListener(event -> {
            String npcId = assignments.getSelectedValue();
            if (npcId != null) {
                updateNpcQuestAssignment(npcId, 0, false);
            }
        });
        earlier.addActionListener(event -> {
            String npcId = assignments.getSelectedValue();
            if (npcId != null) {
                updateNpcQuestAssignment(npcId, -1, true);
            }
        });
        later.addActionListener(event -> {
            String npcId = assignments.getSelectedValue();
            if (npcId != null) {
                updateNpcQuestAssignment(npcId, 1, true);
            }
        });
        buttons.add(add);
        buttons.add(remove);
        buttons.add(earlier);
        buttons.add(later);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void updateNpcQuestAssignment(String npcId, int move, boolean assigned) {
        for (int index = 0; index < npcs.size(); index++) {
            MapDesignLibrary.CustomNpc npc = npcs.get(index);
            if (!npc.npcId().equals(npcId)) {
                continue;
            }
            List<String> ids = new ArrayList<>(npc.questIds());
            int current = ids.indexOf(loadedId);
            if (!assigned) {
                ids.remove(loadedId);
            } else if (current < 0) {
                ids.add(loadedId);
            } else if (move != 0) {
                int target = Math.max(0, Math.min(ids.size() - 1, current + move));
                if (target != current) {
                    ids.remove(current);
                    ids.add(target, loadedId);
                }
            }
            npcs.set(index, copyNpc(npc, npc.interactionId(), ids));
            refreshAssignments();
            markDirty();
            return;
        }
    }

    private void refreshAssignments() {
        assignmentModel.clear();
        if (loadedId.isBlank()) {
            return;
        }
        npcs.stream().filter(npc -> npc.questIds().contains(loadedId))
                .map(MapDesignLibrary.CustomNpc::npcId)
                .forEach(assignmentModel::addElement);
    }

    private <T> JPanel listEditorPanel(
            DefaultListModel<T> model,
            Runnable addAction,
            Runnable removeAction,
            String title
    ) {
        JList<T> list = new JList<>(model);
        list.putClientProperty("model.owner", model);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton remove = new JButton("Remove");
        add.addActionListener(event -> addAction.run());
        edit.addActionListener(event -> {
            if (model == requirementModel) {
                editRequirement(list.getSelectedIndex());
            } else if (model == objectiveModel) {
                editObjective(list.getSelectedIndex());
            } else if (model == stageRewardModel) {
                editReward(stageRewardModel, list.getSelectedIndex());
            } else if (model == finalRewardModel) {
                editReward(finalRewardModel, list.getSelectedIndex());
            }
        });
        remove.addActionListener(event -> {
            if (!canEditInspectorModel(model)) {
                return;
            }
            int index = list.getSelectedIndex();
            if (index >= 0) {
                model.remove(index);
                markDirty();
            } else {
                removeAction.run();
            }
        });
        buttons.add(add);
        buttons.add(edit);
        buttons.add(remove);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private boolean canEditInspectorModel(DefaultListModel<?> model) {
        if (model == objectiveModel && !hasSelectedStage()) {
            stateLabel.setText("Select a journal-stage section on the Flow Map before editing objectives.");
            return false;
        }
        if (model == requirementModel && !hasOwnedQuestInspector()) {
            stateLabel.setText("Select a quest before editing its requirements.");
            return false;
        }
        return true;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(stateLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton revert = new JButton("Revert");
        JButton validate = new JButton("Validate");
        JButton apply = new JButton("Apply");
        JButton close = new JButton("Close");
        revert.addActionListener(event -> revert());
        validate.addActionListener(event -> validateDraft());
        apply.addActionListener(event -> apply());
        close.addActionListener(event -> {
            if (!dirty || confirmDiscard()) {
                dispose();
            }
        });
        buttons.add(revert);
        buttons.add(validate);
        buttons.add(apply);
        buttons.add(close);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private void refreshCatalog(String selectedId) {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        catalogModel.clear();
        if (kind == Kind.QUEST) {
            quests.values().stream()
                    .filter(quest -> query.isBlank()
                            || quest.displayName().toLowerCase().contains(query)
                            || quest.questId().toLowerCase().contains(query))
                    .forEach(quest -> catalogModel.addElement(
                            new CatalogEntry(quest.questId(), quest.displayName())));
        } else {
            dialogues.values().stream()
                    .filter(dialogue -> query.isBlank()
                            || dialogue.speakerName().toLowerCase().contains(query)
                            || dialogue.interactionId().toLowerCase().contains(query))
                    .forEach(dialogue -> catalogModel.addElement(
                            new CatalogEntry(dialogue.interactionId(), dialogue.speakerName())));
        }
        for (int index = 0; index < catalogModel.size(); index++) {
            if (catalogModel.get(index).id().equals(selectedId)) {
                catalogList.setSelectedIndex(index);
                return;
            }
        }
        if (!catalogModel.isEmpty()) {
            catalogList.setSelectedIndex(0);
        }
    }

    private void switchSelection() {
        CatalogEntry selected = catalogList.getSelectedValue();
        if (selected == null || selected.id().equals(loadedId)) {
            return;
        }
        if (dirty && !confirmDiscard()) {
            refreshCatalog(loadedId);
            return;
        }
        if (dirty) {
            String targetId = selected.id();
            restoreDraftsFromHost();
            refreshCatalog(targetId);
            return;
        }
        loadEntry(selected.id());
    }

    private void loadEntry(String id) {
        loading = true;
        loadedFlowSlot = null;
        questInspectorOwnerId = "";
        clearStageEditorOwnership();
        requirementModel.clear();
        objectiveModel.clear();
        stageRewardModel.clear();
        finalRewardModel.clear();
        loadedId = id == null ? "" : id;
        flowSlotModel.clear();
        if (kind == Kind.QUEST) {
            MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
            if (quest == null) {
                loading = false;
                return;
            }
            idField.setText(quest.questId());
            nameField.setText(quest.displayName());
            summaryArea.setText(quest.summary());
            requirementModel.clear();
            quest.requirements().forEach(requirementModel::addElement);
            finalRewardModel.clear();
            quest.finalRewards().forEach(finalRewardModel::addElement);
            questInspectorOwnerId = quest.questId();
            refreshAssignments();
            flowSlotModel.addElement(new FlowSlot(FlowSlotKind.OFFER, -1, "Starting Dialogue / Offer"));
            for (int index = 0; index < quest.stages().size(); index++) {
                flowSlotModel.addElement(new FlowSlot(
                        FlowSlotKind.STAGE,
                        index,
                        (index + 1) + ". " + quest.stages().get(index).title()
                ));
            }
            flowSlotModel.addElement(new FlowSlot(FlowSlotKind.EPILOGUE, -1, "Completed Epilogue"));
            loadQuestCanvas(quest);
        } else {
            MapDesignLibrary.AuthoredDialogue dialogue = dialogues.get(loadedId);
            if (dialogue == null) {
                loading = false;
                return;
            }
            idField.setText(dialogue.interactionId());
            nameField.setText(dialogue.speakerName());
            summaryArea.setText(dialogue.bodyText());
            followUpBox.replaceOptions(dialogueOptions());
            followUpBox.setSelectedItem(dialogue.followUpInteractionId());
            dialogueRewardModel.clear();
            dialogue.rewards().forEach(dialogueRewardModel::addElement);
            dialogueFirstTalkNodeId = dialogue.firstTalkNodeId();
            dialogueRepeatTalkNodeId = dialogue.repeatTalkNodeId();
            flowSlotModel.addElement(new FlowSlot(FlowSlotKind.DIALOGUE, -1, "Dialogue Flow"));
        }
        flowSlotList.setSelectedIndex(0);
        loadSelectedFlow();
        dirty = false;
        loading = false;
        updateDiagnostics();
        updateState();
    }

    private void loadSelectedFlow() {
        FlowSlot slot = flowSlotList.getSelectedValue();
        if (slot == null) {
            if (kind == Kind.DIALOGUE) {
                canvas.setFlow(MapDesignLibrary.QuestFlow.empty());
            }
            return;
        }
        loading = true;
        if (kind == Kind.DIALOGUE) {
            canvas.setFlow(dialogueFlow(dialogues.get(loadedId)));
        } else {
            MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
            if (quest == null) {
                canvas.setSections(List.of());
            } else {
                canvas.focusSection(sectionId(slot, quest));
            }
            if (quest != null && slot.kind() == FlowSlotKind.STAGE) {
                loadStageFields(quest.stages().get(slot.index()));
            } else if (quest != null) {
                clearStageFields(slot);
            }
        }
        loadNodeInspector(null);
        loadedFlowSlot = slot;
        updateActiveFlowSectionLabel();
        loading = false;
        updateDiagnostics();
    }

    private void commitCurrentFlow() {
        if (loading || loadedId.isBlank()) {
            return;
        }
        FlowSlot slot = loadedFlowSlot;
        if (slot == null) {
            return;
        }
        if (kind == Kind.DIALOGUE) {
            MapDesignLibrary.AuthoredDialogue existing = dialogues.get(loadedId);
            if (existing != null) {
                dialogues.put(loadedId, dialogueFromFlow(existing, canvas.flow()));
            }
            return;
        }
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (quest == null) {
            return;
        }
        MapDesignLibrary.QuestFlow offer = quest.offerFlow();
        MapDesignLibrary.QuestFlow epilogue = quest.epilogueFlow();
        List<MapDesignLibrary.QuestStage> stages = new ArrayList<>(quest.stages());
        offer = canvas.sectionFlow(sectionId(new FlowSlot(
                FlowSlotKind.OFFER, -1, "Starting Dialogue / Offer"), quest));
        epilogue = canvas.sectionFlow(sectionId(new FlowSlot(
                FlowSlotKind.EPILOGUE, -1, "Completed Epilogue"), quest));
        for (int index = 0; index < stages.size(); index++) {
            MapDesignLibrary.QuestStage old = stages.get(index);
            stages.set(index, new MapDesignLibrary.QuestStage(
                    old.stageId(),
                    old.title(),
                    old.journalText(),
                    old.completionMode(),
                    old.objectives(),
                    old.rewards(),
                    canvas.sectionFlow(stageSectionId(index, old))
            ));
        }
        if (slot.kind() == FlowSlotKind.STAGE && slot.index() >= 0 && slot.index() < stages.size()) {
            MapDesignLibrary.QuestStage old = stages.get(slot.index());
            if (stageEditorOwns(loadedId, old)) {
                stages.set(slot.index(), new MapDesignLibrary.QuestStage(
                        old.stageId(),
                        text(stageTitleField, old.title()),
                        areaText(stageJournalArea, old.journalText()),
                        (MapDesignLibrary.QuestCompletionMode) completionMode.getSelectedItem(),
                        list(objectiveModel),
                        list(stageRewardModel),
                        old.flow()
                ));
            }
        }
        quests.put(loadedId, copyQuest(quest, offer, stages, epilogue));
    }

    private void loadQuestCanvas(MapDesignLibrary.AuthoredQuest quest) {
        if (quest == null) {
            canvas.setSections(List.of());
            return;
        }
        List<QuestFlowCanvas.FlowSection> sections = new ArrayList<>();
        sections.add(new QuestFlowCanvas.FlowSection(
                "offer",
                "Starting Dialogue / Offer",
                quest.offerFlow()
        ));
        for (int index = 0; index < quest.stages().size(); index++) {
            MapDesignLibrary.QuestStage stage = quest.stages().get(index);
            sections.add(new QuestFlowCanvas.FlowSection(
                    stageSectionId(index, stage),
                    "Stage " + (index + 1) + ": " + stage.title(),
                    stage.flow()
            ));
        }
        sections.add(new QuestFlowCanvas.FlowSection(
                "epilogue",
                "Completed Epilogue",
                quest.epilogueFlow()
        ));
        canvas.setSections(sections);
    }

    private String sectionId(FlowSlot slot, MapDesignLibrary.AuthoredQuest quest) {
        if (slot == null) {
            return "";
        }
        return switch (slot.kind()) {
            case OFFER -> "offer";
            case EPILOGUE -> "epilogue";
            case STAGE -> slot.index() >= 0 && slot.index() < quest.stages().size()
                    ? stageSectionId(slot.index(), quest.stages().get(slot.index()))
                    : "";
            case DIALOGUE -> "flow";
        };
    }

    private String stageSectionId(int index, MapDesignLibrary.QuestStage stage) {
        return "stage:" + index + ":" + stage.stageId();
    }

    private void updateIdentity() {
        if (loading || loadedId.isBlank()) {
            return;
        }
        if (kind == Kind.QUEST) {
            MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
            if (quest != null && loadedId.equals(questInspectorOwnerId)) {
                quests.put(loadedId, new MapDesignLibrary.AuthoredQuest(
                        loadedId,
                        text(nameField, quest.displayName()),
                        areaText(summaryArea, ""),
                        list(requirementModel),
                        quest.offerFlow(),
                        quest.stages(),
                        list(finalRewardModel),
                        quest.epilogueFlow()
                ));
            }
        } else {
            MapDesignLibrary.AuthoredDialogue old = dialogues.get(loadedId);
            if (old != null) {
                dialogues.put(loadedId, new MapDesignLibrary.AuthoredDialogue(
                        loadedId,
                        text(nameField, old.speakerName()),
                        old.bodyText(),
                        selectedText(followUpBox),
                        old.visualPath(),
                        old.choices(),
                        old.nodes(),
                        list(dialogueRewardModel),
                        old.firstTalkNodeId(),
                        old.repeatTalkNodeId()
                ));
            }
        }
    }

    private void loadNodeInspector(MapDesignLibrary.QuestFlowNode node) {
        loading = true;
        nodeIdField.setText(node == null ? "" : node.nodeId());
        nodeBodyArea.setText(node == null ? "" : node.bodyText());
        choiceModel.clear();
        if (node != null) {
            node.choices().forEach(choiceModel::addElement);
        }
        loading = false;
    }

    private void synchronizeNavigatorWithCanvas() {
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (quest == null) {
            return;
        }
        String activeSection = canvas.activeSectionId();
        updateActiveFlowSectionLabel();
        if (loadedFlowSlot != null && activeSection.equals(sectionId(loadedFlowSlot, quest))) {
            return;
        }
        commitCurrentFlow();
        quest = quests.get(loadedId);
        if (quest == null) {
            return;
        }
        for (int index = 0; index < flowSlotModel.size(); index++) {
            FlowSlot candidate = flowSlotModel.get(index);
            if (!activeSection.equals(sectionId(candidate, quest))) {
                continue;
            }
            boolean wasLoading = loading;
            loading = true;
            flowSlotList.setSelectedIndex(index);
            loadedFlowSlot = candidate;
            if (candidate.kind() == FlowSlotKind.STAGE) {
                loadStageFields(quest.stages().get(candidate.index()));
            } else {
                clearStageFields(candidate);
            }
            loading = wasLoading;
            updateActiveFlowSectionLabel();
            return;
        }
    }

    private void updateActiveFlowSectionLabel() {
        String title = canvas.activeSectionTitle();
        String display = title == null || title.isBlank() ? "none" : title;
        activeFlowSectionLabel.setText("Selected flow: " + display);
        nodeFlowSectionLabel.setText("Editing flow: " + display);
    }

    private void setSelectedEntryNode() {
        MapDesignLibrary.QuestFlowNode node = canvas.selectedNode();
        if (node == null) {
            stateLabel.setText("Select a dialogue node first, then make it the entry.");
            return;
        }
        if (!canvas.setEntryNode(node.nodeId())) {
            stateLabel.setText("Could not set entry: the selected node is not in the active flow section.");
            return;
        }
        if (kind == Kind.DIALOGUE) {
            dialogueFirstTalkNodeId = node.nodeId();
            markDirty();
        }
        stateLabel.setText("Entry set to " + node.nodeId() + " for "
                + canvas.activeSectionTitle() + ".");
    }

    private void loadStageFields(MapDesignLibrary.QuestStage stage) {
        stageEditorQuestId = loadedId;
        stageEditorStageId = stage.stageId();
        selectedStageLabel.setText(stage.title() + "  [" + stage.stageId() + "]");
        stageTitleField.setEnabled(true);
        stageJournalArea.setEnabled(true);
        completionMode.setEnabled(true);
        stageTitleField.setText(stage.title());
        stageJournalArea.setText(stage.journalText());
        completionMode.setSelectedItem(stage.completionMode());
        objectiveModel.clear();
        stage.objectives().forEach(objectiveModel::addElement);
        stageRewardModel.clear();
        stage.rewards().forEach(stageRewardModel::addElement);
        setInspectorTabEnabled("Objectives", true);
    }

    private void clearStageFields(FlowSlot slot) {
        clearStageEditorOwnership();
        String section = slot != null && slot.kind() == FlowSlotKind.EPILOGUE
                ? "Completed Epilogue"
                : "Starting Dialogue / Offer";
        selectedStageLabel.setText(section + " — no journal stage selected");
        stageTitleField.setText("");
        stageJournalArea.setText("");
        completionMode.setSelectedItem(MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED);
        stageTitleField.setEnabled(false);
        stageJournalArea.setEnabled(false);
        completionMode.setEnabled(false);
        objectiveModel.clear();
        stageRewardModel.clear();
        setInspectorTabEnabled("Objectives", false);
    }

    private void setInspectorTabEnabled(String title, boolean enabled) {
        for (int index = 0; index < inspectorTabs.getTabCount(); index++) {
            if (title.equals(inspectorTabs.getTitleAt(index))) {
                inspectorTabs.setEnabledAt(index, enabled);
                return;
            }
        }
    }

    private boolean hasSelectedStage() {
        if (loadedFlowSlot == null
                || loadedFlowSlot.kind() != FlowSlotKind.STAGE
                || !loadedId.equals(stageEditorQuestId)) {
            return false;
        }
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        return quest != null
                && loadedFlowSlot.index() >= 0
                && loadedFlowSlot.index() < quest.stages().size()
                && quest.stages().get(loadedFlowSlot.index()).stageId()
                        .equals(stageEditorStageId);
    }

    private boolean hasOwnedQuestInspector() {
        return kind == Kind.QUEST
                && !loadedId.isBlank()
                && loadedId.equals(questInspectorOwnerId)
                && quests.containsKey(loadedId);
    }

    private boolean stageEditorOwns(
            String questId,
            MapDesignLibrary.QuestStage stage
    ) {
        return stage != null
                && questId != null
                && questId.equals(stageEditorQuestId)
                && stage.stageId().equals(stageEditorStageId);
    }

    private void clearStageEditorOwnership() {
        stageEditorQuestId = "";
        stageEditorStageId = "";
    }

    private void saveNode() {
        MapDesignLibrary.QuestFlowNode selected = canvas.selectedNode();
        if (selected == null) {
            return;
        }
        String nodeId = nodeIdField.getText().trim();
        if (nodeId.isBlank()) {
            stateLabel.setText("Node ID is required.");
            return;
        }
        canvas.replaceNode(new MapDesignLibrary.QuestFlowNode(
                nodeId,
                nodeBodyArea.getText().trim(),
                selected.canvasX(),
                selected.canvasY(),
                list(choiceModel)
        ));
        commitCurrentFlow();
        if (kind == Kind.DIALOGUE && nodeId.equals(canvas.flow().entryNodeId())) {
            loading = true;
            summaryArea.setText(nodeBodyArea.getText().trim());
            loading = false;
        }
        markDirty();
    }

    private void addNode() {
        String sectionTitle = canvas.activeSectionTitle();
        boolean createsEntry = !canvas.activeSectionHasEntry();
        Set<String> ids = new LinkedHashSet<>();
        canvas.flow().nodes().forEach(node -> ids.add(node.nodeId()));
        String id = uniqueId(createsEntry ? "entry" : "node", ids);
        canvas.addNode(new MapDesignLibrary.QuestFlowNode(
                id,
                "New dialogue text.",
                80 + canvas.flow().nodes().size() * 40,
                80 + canvas.flow().nodes().size() * 30,
                List.of()
        ));
        stateLabel.setText(createsEntry
                ? "Created entry node " + id + " in " + sectionTitle + "."
                : "Added node " + id + " to " + sectionTitle + ".");
    }

    private void duplicateNode() {
        MapDesignLibrary.QuestFlowNode node = canvas.selectedNode();
        if (node == null) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        canvas.flow().nodes().forEach(candidate -> ids.add(candidate.nodeId()));
        canvas.duplicateSelectedNode(uniqueId(node.nodeId() + "_copy", ids));
    }

    private void editChoice(int index) {
        MapDesignLibrary.QuestFlowChoice existing = index < 0 ? null : choiceModel.get(index);
        JTextField label = new JTextField(existing == null ? "Continue" : existing.label(), 24);
        JTextArea terminalBody = new JTextArea(existing == null ? "" : existing.terminalBodyText(), 3, 24);
        terminalBody.setLineWrap(true);
        terminalBody.setWrapStyleWord(true);
        JComboBox<String> target = editableContentBox(nodeOptions());
        target.setSelectedItem(existing == null ? "" : existing.targetNodeId());
        JComboBox<MapDesignLibrary.QuestFlowAction> action =
                new JComboBox<>(MapDesignLibrary.QuestFlowAction.values());
        action.setSelectedItem(existing == null ? MapDesignLibrary.QuestFlowAction.NONE : existing.action());
        if (kind == Kind.DIALOGUE) {
            action.setEnabled(false);
            action.setSelectedItem(MapDesignLibrary.QuestFlowAction.NONE);
        }
        Runnable updateActionControls = () -> {
            MapDesignLibrary.QuestFlowAction selected =
                    (MapDesignLibrary.QuestFlowAction) action.getSelectedItem();
            target.setEnabled(selected == MapDesignLibrary.QuestFlowAction.NONE);
        };
        action.addActionListener(event -> updateActionControls.run());
        updateActionControls.run();
        JComboBox<String> requiredItem = editableContentBox(itemOptions());
        JComboBox<String> takeItem = editableContentBox(itemOptions());
        JSpinner takeItemAmount = new JSpinner(new SpinnerNumberModel(
                existing == null || existing.takeItemId().isBlank() ? 1 : existing.takeItemAmount(),
                1,
                999_999,
                1
        ));
        requiredItem.setSelectedItem(existing == null ? "" : existing.requiredItemId());
        takeItem.setSelectedItem(existing == null ? "" : existing.takeItemId());
        Runnable updateTakeItemControls =
                () -> takeItemAmount.setEnabled(!selectedText(takeItem).isBlank());
        takeItem.addActionListener(event -> updateTakeItemControls.run());
        updateTakeItemControls.run();
        JCheckBox firstTalk = new JCheckBox("First talk only", existing != null && existing.firstTalkOnly());
        firstTalk.setEnabled(kind == Kind.DIALOGUE);

        DefaultListModel<MapDesignLibrary.QuestRequirement> conditionModel = new DefaultListModel<>();
        if (existing != null) {
            existing.conditions().forEach(conditionModel::addElement);
        }
        JList<MapDesignLibrary.QuestRequirement> conditionList = new JList<>(conditionModel);
        conditionList.setVisibleRowCount(3);
        JButton addCondition = new JButton("Add");
        JButton editCondition = new JButton("Edit");
        JButton removeCondition = new JButton("Remove");
        addCondition.addActionListener(event -> {
            MapDesignLibrary.QuestRequirement condition = showRequirementDialog(null);
            if (condition != null) {
                conditionModel.addElement(condition);
            }
        });
        editCondition.addActionListener(event -> {
            int selected = conditionList.getSelectedIndex();
            if (selected >= 0) {
                MapDesignLibrary.QuestRequirement condition =
                        showRequirementDialog(conditionModel.get(selected));
                if (condition != null) {
                    conditionModel.set(selected, condition);
                }
            }
        });
        removeCondition.addActionListener(event -> {
            int selected = conditionList.getSelectedIndex();
            if (selected >= 0) {
                conditionModel.remove(selected);
            }
        });
        JPanel conditionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        conditionButtons.add(addCondition);
        conditionButtons.add(editCondition);
        conditionButtons.add(removeCondition);
        JPanel conditionPanel = new JPanel(new BorderLayout(4, 4));
        conditionPanel.add(new JScrollPane(conditionList), BorderLayout.CENTER);
        conditionPanel.add(conditionButtons, BorderLayout.SOUTH);
        conditionPanel.setPreferredSize(new Dimension(330, 100));
        boolean questChoice = kind == Kind.QUEST;
        conditionList.setEnabled(questChoice);
        addCondition.setEnabled(questChoice);
        editCondition.setEnabled(questChoice);
        removeCondition.setEnabled(questChoice);

        DefaultListModel<MapDesignLibrary.RewardDefinition> choiceRewardModel = new DefaultListModel<>();
        if (existing != null) {
            existing.rewards().forEach(choiceRewardModel::addElement);
        }
        JPanel choiceRewards = rewardEditorPanel(
                choiceRewardModel,
                "Once-only choice rewards",
                false
        );
        choiceRewards.setPreferredSize(new Dimension(420, 155));

        JPanel form = form();
        addRow(form, "Player Response", label);
        addRow(form, "Arrow Target", target);
        addRow(form, "Terminal Response", new JScrollPane(terminalBody));
        addRow(form, "Quest Action", action);
        addRow(form, "Requires Item/Limb", requiredItem);
        addRow(form, "Choice Consumes Item/Limb", takeItem);
        addRow(form, "Choice Consumes Quantity", takeItemAmount);
        addRow(form, "", new JLabel(
                "Stage TURN_IN_ITEM objectives are consumed automatically; do not repeat them here."
        ));
        addRow(form, "AND Conditions", conditionPanel);
        addRow(form, "Rewards", choiceRewards);
        addRow(form, "", firstTalk);
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setPreferredSize(new Dimension(620, 620));
        if (JOptionPane.showConfirmDialog(this, formScroll, existing == null ? "Add Choice" : "Edit Choice",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String choiceId = existing == null
                ? uniqueId("choice", choiceModelToIds())
                : existing.choiceId();
        MapDesignLibrary.QuestFlowAction selectedAction =
                (MapDesignLibrary.QuestFlowAction) action.getSelectedItem();
        String selectedTarget = selectedAction == MapDesignLibrary.QuestFlowAction.NONE
                ? selectedText(target)
                : "";
        if (selectedAction == MapDesignLibrary.QuestFlowAction.NONE
                && !selectedTarget.isBlank()
                && !terminalBody.getText().trim().isBlank()) {
            stateLabel.setText("A normal choice cannot have both an arrow target and terminal response.");
            return;
        }
        List<MapDesignLibrary.QuestRequirement> conditions = new ArrayList<>(list(conditionModel));
        String requiredId = selectedText(requiredItem);
        boolean alreadyRequiresItem = conditions.stream().anyMatch(condition ->
                condition.type() == MapDesignLibrary.QuestRequirementType.POSSESS_ITEM
                        && condition.targetId().equals(requiredId));
        if (!requiredId.isBlank() && !alreadyRequiresItem) {
            conditions.add(new MapDesignLibrary.QuestRequirement(
                    MapDesignLibrary.QuestRequirementType.POSSESS_ITEM,
                    requiredId,
                    null,
                    1
            ));
        }
        List<MapDesignLibrary.RewardDefinition> authoredChoiceRewards = list(choiceRewardModel);
        String selectedTakeItemId = selectedText(takeItem);
        int selectedTakeItemAmount = selectedTakeItemId.isBlank()
                ? 0
                : ((Number) takeItemAmount.getValue()).intValue();
        boolean progressionAction = selectedAction == MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE
                || selectedAction == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST;
        boolean duplicatesTurnInObjective = progressionAction
                && hasSelectedStage()
                && list(objectiveModel).stream().anyMatch(objective ->
                        objective.type() == MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM
                                && objective.targetId().equals(selectedTakeItemId));
        if (duplicatesTurnInObjective) {
            stateLabel.setText(
                    "This stage already consumes " + selectedTakeItemId
                            + " through its TURN_IN_ITEM objective. Clear the choice consumption field."
            );
            return;
        }
        MapDesignLibrary.QuestFlowChoice updated = new MapDesignLibrary.QuestFlowChoice(
                choiceId,
                label.getText().trim(),
                selectedTarget,
                conditions,
                selectedAction,
                requiredId,
                selectedTakeItemId,
                selectedTakeItemAmount,
                authoredChoiceRewards,
                firstTalk.isSelected(),
                terminalBody.getText()
        );
        if (index < 0) {
            choiceModel.addElement(updated);
        } else {
            choiceModel.set(index, updated);
        }
        saveNode();
    }

    private void moveChoice(int delta) {
        int index = choiceList.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= choiceModel.size()) {
            return;
        }
        MapDesignLibrary.QuestFlowChoice value = choiceModel.remove(index);
        choiceModel.add(target, value);
        choiceList.setSelectedIndex(target);
        saveNode();
    }

    private void updateStage() {
        if (!hasSelectedStage()) {
            stateLabel.setText("Select a journal-stage section on the Flow Map first.");
            return;
        }
        commitCurrentFlow();
        markDirty();
        rebuildFlowSlots();
    }

    private void addStage() {
        commitCurrentFlow();
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (quest == null) {
            return;
        }
        List<MapDesignLibrary.QuestStage> stages = new ArrayList<>(quest.stages());
        String stageId = uniqueId("stage_" + (stages.size() + 1),
                stages.stream().map(MapDesignLibrary.QuestStage::stageId).collect(java.util.stream.Collectors.toSet()));
        String entryNodeId = stageId + "_entry";
        stages.add(new MapDesignLibrary.QuestStage(
                stageId,
                "Stage " + (stages.size() + 1),
                "Describe the player's current objective.",
                MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED,
                List.of(),
                List.of(),
                new MapDesignLibrary.QuestFlow(
                        entryNodeId,
                        List.of(new MapDesignLibrary.QuestFlowNode(
                                entryNodeId,
                                "Describe what the NPC says during this stage.",
                                80,
                                80,
                                List.of(new MapDesignLibrary.QuestFlowChoice(
                                        stageId + "_complete",
                                        "Complete quest",
                                        "",
                                        List.of(),
                                        MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST,
                                        "",
                                        ""
                                ))
                        ))
                )
        ));
        stages = normalizeStageProgressionActions(stages);
        MapDesignLibrary.AuthoredQuest updated =
                copyQuest(quest, quest.offerFlow(), stages, quest.epilogueFlow());
        quests.put(loadedId, updated);
        loadQuestCanvas(updated);
        flagDraftDirty();
        loadedFlowSlot = null;
        clearStageEditorOwnership();
        rebuildFlowSlots();
        flowSlotList.setSelectedIndex(stages.size());
    }

    private void removeStage() {
        commitCurrentFlow();
        FlowSlot slot = flowSlotList.getSelectedValue();
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (slot == null || slot.kind() != FlowSlotKind.STAGE || quest == null) {
            stateLabel.setText("Select the journal-stage section you want to remove.");
            return;
        }
        if (quest.stages().size() <= 1) {
            stateLabel.setText("A quest must retain at least one stage.");
            return;
        }
        List<MapDesignLibrary.QuestStage> stages = new ArrayList<>(quest.stages());
        stages.remove(slot.index());
        stages = normalizeStageProgressionActions(stages);
        MapDesignLibrary.AuthoredQuest updated =
                copyQuest(quest, quest.offerFlow(), stages, quest.epilogueFlow());
        quests.put(loadedId, updated);
        loadQuestCanvas(updated);
        flagDraftDirty();
        loadedFlowSlot = null;
        clearStageEditorOwnership();
        rebuildFlowSlots();
        flowSlotList.setSelectedIndex(Math.min(slot.index() + 1, flowSlotModel.size() - 2));
    }

    private void moveStage(int delta) {
        FlowSlot slot = flowSlotList.getSelectedValue();
        commitCurrentFlow();
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (slot == null || slot.kind() != FlowSlotKind.STAGE || quest == null) {
            stateLabel.setText("Select the journal-stage section you want to reorder.");
            return;
        }
        int target = slot.index() + delta;
        if (target < 0 || target >= quest.stages().size()) {
            return;
        }
        List<MapDesignLibrary.QuestStage> stages = new ArrayList<>(quest.stages());
        MapDesignLibrary.QuestStage stage = stages.remove(slot.index());
        stages.add(target, stage);
        stages = normalizeStageProgressionActions(stages);
        MapDesignLibrary.AuthoredQuest updated =
                copyQuest(quest, quest.offerFlow(), stages, quest.epilogueFlow());
        quests.put(loadedId, updated);
        loadQuestCanvas(updated);
        flagDraftDirty();
        loadedFlowSlot = null;
        clearStageEditorOwnership();
        rebuildFlowSlots();
        flowSlotList.setSelectedIndex(target + 1);
    }

    private List<MapDesignLibrary.QuestStage> normalizeStageProgressionActions(
            List<MapDesignLibrary.QuestStage> source
    ) {
        List<MapDesignLibrary.QuestStage> normalized = new ArrayList<>();
        for (int stageIndex = 0; stageIndex < source.size(); stageIndex++) {
            MapDesignLibrary.QuestStage stage = source.get(stageIndex);
            if (stage.completionMode() == MapDesignLibrary.QuestCompletionMode.AUTOMATIC) {
                normalized.add(stage);
                continue;
            }
            MapDesignLibrary.QuestFlowAction expected = stageIndex == source.size() - 1
                    ? MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST
                    : MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE;
            List<MapDesignLibrary.QuestFlowNode> nodes = stage.flow().nodes().stream()
                    .map(node -> new MapDesignLibrary.QuestFlowNode(
                            node.nodeId(),
                            node.bodyText(),
                            node.canvasX(),
                            node.canvasY(),
                            node.choices().stream().map(choice -> {
                                if (choice.action() != MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE
                                        && choice.action() != MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST) {
                                    return choice;
                                }
                                return new MapDesignLibrary.QuestFlowChoice(
                                        choice.choiceId(),
                                        choice.label(),
                                        "",
                                        choice.conditions(),
                                        expected,
                                        choice.requiredItemId(),
                                        choice.takeItemId(),
                                        choice.takeItemAmount(),
                                        choice.rewards(),
                                        choice.firstTalkOnly(),
                                        choice.terminalBodyText()
                                );
                            }).toList()
                    ))
                    .toList();
            normalized.add(new MapDesignLibrary.QuestStage(
                    stage.stageId(),
                    stage.title(),
                    stage.journalText(),
                    stage.completionMode(),
                    stage.objectives(),
                    stage.rewards(),
                    new MapDesignLibrary.QuestFlow(stage.flow().entryNodeId(), nodes)
            ));
        }
        return normalized;
    }

    private void rebuildFlowSlots() {
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (quest == null) {
            return;
        }
        boolean wasLoading = loading;
        loading = true;
        flowSlotModel.clear();
        flowSlotModel.addElement(new FlowSlot(FlowSlotKind.OFFER, -1, "Starting Dialogue / Offer"));
        for (int index = 0; index < quest.stages().size(); index++) {
            flowSlotModel.addElement(new FlowSlot(
                    FlowSlotKind.STAGE, index, (index + 1) + ". " + quest.stages().get(index).title()));
        }
        flowSlotModel.addElement(new FlowSlot(FlowSlotKind.EPILOGUE, -1, "Completed Epilogue"));
        if (loadedFlowSlot != null) {
            int selectedIndex = switch (loadedFlowSlot.kind()) {
                case OFFER -> 0;
                case STAGE -> Math.min(loadedFlowSlot.index() + 1, quest.stages().size());
                case EPILOGUE -> flowSlotModel.size() - 1;
                case DIALOGUE -> 0;
            };
            flowSlotList.setSelectedIndex(selectedIndex);
        }
        loading = wasLoading;
    }

    private void addRequirement() {
        if (!hasOwnedQuestInspector()) {
            stateLabel.setText("Select a quest before adding requirements.");
            return;
        }
        MapDesignLibrary.QuestRequirement value = showRequirementDialog(null);
        if (value != null) {
            requirementModel.addElement(value);
            markDirty();
        }
    }

    private void editRequirement(int index) {
        if (index < 0 || !hasOwnedQuestInspector()) {
            return;
        }
        MapDesignLibrary.QuestRequirement value = showRequirementDialog(requirementModel.get(index));
        if (value != null) {
            requirementModel.set(index, value);
            markDirty();
        }
    }

    private MapDesignLibrary.QuestRequirement showRequirementDialog(MapDesignLibrary.QuestRequirement existing) {
        JComboBox<MapDesignLibrary.QuestRequirementType> type =
                new JComboBox<>(MapDesignLibrary.QuestRequirementType.values());
        SearchableReferenceBox target = (SearchableReferenceBox) editableContentBox(itemOptions());
        JComboBox<CharacterSkill> skill = new JComboBox<>(CharacterSkill.values());
        JSpinner amount = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        if (existing != null) {
            type.setSelectedItem(existing.type());
            target.setSelectedItem(existing.targetId());
            skill.setSelectedItem(existing.skill());
            amount.setValue(existing.amount());
        }
        Runnable updateFields = () -> {
            MapDesignLibrary.QuestRequirementType selected =
                    (MapDesignLibrary.QuestRequirementType) type.getSelectedItem();
            String selectedId = target.selectedId();
            target.replaceOptions(requirementTargetOptions(selected));
            target.selectId(selectedId);
            boolean needsTarget = selected == MapDesignLibrary.QuestRequirementType.POSSESS_ITEM
                    || selected == MapDesignLibrary.QuestRequirementType.EQUIPPED_ITEM_OR_LIMB
                    || selected == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST;
            target.setEnabled(needsTarget);
            skill.setEnabled(selected == MapDesignLibrary.QuestRequirementType.SKILL_LEVEL);
            boolean singleEquip = selected == MapDesignLibrary.QuestRequirementType.EQUIPPED_ITEM_OR_LIMB;
            if (singleEquip) {
                amount.setValue(1);
            }
            amount.setEnabled(!singleEquip);
        };
        type.addActionListener(event -> updateFields.run());
        updateFields.run();
        JPanel form = form();
        addRow(form, "Type", type);
        addRow(form, "Content Target", target);
        addRow(form, "Skill", skill);
        addRow(form, "Required Amount/Level", amount);
        if (JOptionPane.showConfirmDialog(this, form, "Quest Requirement",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        return new MapDesignLibrary.QuestRequirement(
                (MapDesignLibrary.QuestRequirementType) type.getSelectedItem(),
                selectedText(target),
                (CharacterSkill) skill.getSelectedItem(),
                ((Number) amount.getValue()).intValue()
        );
    }

    private void addObjective() {
        if (!hasSelectedStage()) {
            stateLabel.setText("Select a journal-stage section on the Flow Map before adding objectives.");
            return;
        }
        MapDesignLibrary.QuestObjective value = showObjectiveDialog(null);
        if (value != null) {
            objectiveModel.addElement(value);
            markDirty();
        }
    }

    private void editObjective(int index) {
        if (index < 0 || !hasSelectedStage()) {
            return;
        }
        MapDesignLibrary.QuestObjective value = showObjectiveDialog(objectiveModel.get(index));
        if (value != null) {
            objectiveModel.set(index, value);
            markDirty();
        }
    }

    private MapDesignLibrary.QuestObjective showObjectiveDialog(MapDesignLibrary.QuestObjective existing) {
        JComboBox<MapDesignLibrary.QuestObjectiveType> type =
                new JComboBox<>(MapDesignLibrary.QuestObjectiveType.values());
        SearchableReferenceBox target = (SearchableReferenceBox) editableContentBox(itemOptions());
        JComboBox<CharacterSkill> skill = new JComboBox<>(CharacterSkill.values());
        JSpinner amount = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        JTextField journal = new JTextField(28);
        JTextField objectiveId = new JTextField(20);
        JCheckBox visible = new JCheckBox("Show in quest journal", true);
        if (existing != null) {
            type.setSelectedItem(existing.type());
            target.setSelectedItem(existing.targetId());
            skill.setSelectedItem(existing.skill());
            amount.setValue(existing.amount());
            journal.setText(existing.journalText());
            objectiveId.setText(existing.objectiveId());
            visible.setSelected(existing.visible());
        } else {
            objectiveId.setText(uniqueId("objective", allObjectiveIds()));
        }
        Runnable updateFields = () -> {
            MapDesignLibrary.QuestObjectiveType selected =
                    (MapDesignLibrary.QuestObjectiveType) type.getSelectedItem();
            String selectedId = target.selectedId();
            target.replaceOptions(objectiveTargetOptions(selected));
            target.selectId(selectedId);
            boolean needsTarget = selected != MapDesignLibrary.QuestObjectiveType.PLAYER_LEVEL
                    && selected != MapDesignLibrary.QuestObjectiveType.SKILL_LEVEL;
            target.setEnabled(needsTarget);
            skill.setEnabled(selected == MapDesignLibrary.QuestObjectiveType.SKILL_LEVEL);
            boolean singleEquip = selected == MapDesignLibrary.QuestObjectiveType.EQUIPPED_ITEM_OR_LIMB;
            if (singleEquip) {
                amount.setValue(1);
            }
            amount.setEnabled(!singleEquip);
        };
        type.addActionListener(event -> updateFields.run());
        updateFields.run();
        JPanel form = form();
        addRow(form, "Stable Objective ID", objectiveId);
        addRow(form, "Type", type);
        addRow(form, "Content Target", target);
        addRow(form, "Skill", skill);
        addRow(form, "Required Count/Level", amount);
        addRow(form, "Journal Text", journal);
        addRow(form, "", visible);
        if (JOptionPane.showConfirmDialog(this, form, "Stage Objective",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        String id = objectiveId.getText().trim();
        if (id.isBlank() || allObjectiveIds().stream()
                .anyMatch(existingId -> existingId.equals(id)
                        && (existing == null || !existing.objectiveId().equals(id)))) {
            stateLabel.setText("Objective ID must be non-blank and unique across the quest.");
            return null;
        }
        return new MapDesignLibrary.QuestObjective(
                id,
                (MapDesignLibrary.QuestObjectiveType) type.getSelectedItem(),
                selectedText(target),
                (CharacterSkill) skill.getSelectedItem(),
                ((Number) amount.getValue()).intValue(),
                journal.getText().trim(),
                visible.isSelected()
        );
    }

    private void editReward(DefaultListModel<MapDesignLibrary.RewardDefinition> model, int index) {
        if (index < 0) {
            return;
        }
        MapDesignLibrary.RewardDefinition reward = showRewardDialog(model.get(index));
        if (reward != null) {
            model.set(index, reward);
            markDirty();
        }
    }

    private MapDesignLibrary.RewardDefinition showRewardDialog(MapDesignLibrary.RewardDefinition existing) {
        return showRewardDialog(existing, List.of());
    }

    private MapDesignLibrary.RewardDefinition showRewardDialog(
            MapDesignLibrary.RewardDefinition existing,
            List<MapDesignLibrary.RewardDefinition> siblings
    ) {
        JComboBox<MapDesignLibrary.QuestRewardType> type =
                new JComboBox<>(MapDesignLibrary.QuestRewardType.values());
        JComboBox<String> item = editableContentBox(itemOptions());
        JComboBox<CharacterSkill> skill = new JComboBox<>(CharacterSkill.values());
        JSpinner amount = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
        JTextField rewardId = new JTextField(
                existing == null
                        ? uniqueRewardId(siblings, "reward")
                        : existing.rewardId(),
                20
        );
        if (existing != null) {
            type.setSelectedItem(existing.type());
            item.setSelectedItem(existing.itemId());
            skill.setSelectedItem(existing.skill());
            amount.setValue(existing.amount());
        }
        Runnable updateFields = () -> {
            MapDesignLibrary.QuestRewardType selected =
                    (MapDesignLibrary.QuestRewardType) type.getSelectedItem();
            item.setEnabled(selected == MapDesignLibrary.QuestRewardType.ITEM);
            skill.setEnabled(selected == MapDesignLibrary.QuestRewardType.SKILL_XP);
        };
        type.addActionListener(event -> updateFields.run());
        updateFields.run();
        JPanel form = form();
        addRow(form, "Stable Reward ID", rewardId);
        addRow(form, "Reward Type", type);
        addRow(form, "Item/Limb", item);
        addRow(form, "Skill", skill);
        addRow(form, "Quantity/Gold/XP", amount);
        if (JOptionPane.showConfirmDialog(this, form, "Quest Reward",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        String stableId = rewardId.getText().trim();
        if (stableId.isBlank()) {
            stateLabel.setText("Reward ID is required.");
            return null;
        }
        boolean duplicate = siblings.stream().anyMatch(reward ->
                reward != existing && reward.rewardId().equals(stableId));
        if (duplicate) {
            stateLabel.setText("Reward ID must be unique within this reward list.");
            return null;
        }
        MapDesignLibrary.QuestRewardType selectedType =
                (MapDesignLibrary.QuestRewardType) type.getSelectedItem();
        return new MapDesignLibrary.RewardDefinition(
                stableId,
                selectedType,
                selectedType == MapDesignLibrary.QuestRewardType.ITEM ? selectedText(item) : "",
                selectedType == MapDesignLibrary.QuestRewardType.SKILL_XP
                        ? (CharacterSkill) skill.getSelectedItem()
                        : null,
                ((Number) amount.getValue()).intValue()
        );
    }

    private void createEntry() {
        if (dirty && !confirmDiscard()) {
            return;
        }
        if (dirty) {
            restoreDraftsFromHost();
        }
        if (kind == Kind.QUEST) {
            String id = uniqueId("new_quest", quests.keySet());
            MapDesignLibrary.QuestFlow offer = new MapDesignLibrary.QuestFlow(
                    "offer",
                    List.of(new MapDesignLibrary.QuestFlowNode(
                            "offer",
                            "Write the NPC's opening quest dialogue here.",
                            80,
                            80,
                            List.of(new MapDesignLibrary.QuestFlowChoice(
                                    "accept",
                                    "Accept",
                                    "",
                                    List.of(),
                                    MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST,
                                    "",
                                    ""
                            ))
                    ))
            );
            MapDesignLibrary.QuestStage stage = new MapDesignLibrary.QuestStage(
                    "stage_1",
                    "First Stage",
                    "Describe the player's current objective.",
                    MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED,
                    List.of(),
                    List.of(),
                    new MapDesignLibrary.QuestFlow(
                            "stage_1",
                            List.of(new MapDesignLibrary.QuestFlowNode(
                                    "stage_1",
                                    "Confirm that the quest is complete.",
                                    80,
                                    80,
                                    List.of(new MapDesignLibrary.QuestFlowChoice(
                                            "complete",
                                            "Complete quest",
                                            "",
                                            List.of(),
                                            MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST,
                                            "",
                                            ""
                                    ))
                            ))
                    )
            );
            quests.put(id, new MapDesignLibrary.AuthoredQuest(
                    id, "New Quest", "", List.of(), offer,
                    List.of(stage), List.of(), MapDesignLibrary.QuestFlow.empty()));
            loadedId = "";
            refreshCatalog(id);
        } else {
            String id = uniqueId("new_dialogue", dialogues.keySet());
            dialogues.put(id, new MapDesignLibrary.AuthoredDialogue(
                    id, "New Speaker", "Hello there.", "", MapDesignLibrary.DEFAULT_NPC_VISUAL_PATH,
                    List.of(), List.of(), List.of(), "start", "start"));
            loadedId = "";
            refreshCatalog(id);
        }
        markDirty();
    }

    private void duplicateEntry() {
        commitAllFields();
        if (kind == Kind.QUEST) {
            MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
            if (quest == null) {
                return;
            }
            String id = uniqueId(loadedId + "_copy", quests.keySet());
            quests.put(id, new MapDesignLibrary.AuthoredQuest(
                    id, quest.displayName() + " Copy", quest.summary(),
                    quest.requirements(), quest.offerFlow(), quest.stages(), quest.finalRewards(), quest.epilogueFlow()));
            loadedId = "";
            refreshCatalog(id);
        } else {
            MapDesignLibrary.AuthoredDialogue dialogue = dialogues.get(loadedId);
            if (dialogue == null) {
                return;
            }
            String id = uniqueId(loadedId + "_copy", dialogues.keySet());
            dialogues.put(id, new MapDesignLibrary.AuthoredDialogue(
                    id, dialogue.speakerName() + " Copy", dialogue.bodyText(), dialogue.followUpInteractionId(),
                    dialogue.visualPath(), dialogue.choices(), dialogue.nodes(),
                    dialogue.rewards(), dialogue.firstTalkNodeId(), dialogue.repeatTalkNodeId()));
            loadedId = "";
            refreshCatalog(id);
        }
        markDirty();
    }

    private void renameEntry() {
        if (loadedId.isBlank()) {
            return;
        }
        String value = JOptionPane.showInputDialog(this, "New stable ID", loadedId);
        String newId = value == null ? "" : value.trim();
        if (newId.isBlank() || newId.equals(loadedId)) {
            return;
        }
        if (quests.containsKey(newId) || dialogues.containsKey(newId)) {
            stateLabel.setText("That content ID is already in use.");
            return;
        }
        commitAllFields();
        String oldId = loadedId;
        if (kind == Kind.QUEST) {
            MapDesignLibrary.AuthoredQuest quest = quests.remove(oldId);
            quests.put(newId, new MapDesignLibrary.AuthoredQuest(
                    newId, quest.displayName(), quest.summary(), quest.requirements(),
                    quest.offerFlow(), quest.stages(), quest.finalRewards(), quest.epilogueFlow()));
            rewriteQuestReferences(oldId, newId);
        } else {
            MapDesignLibrary.AuthoredDialogue dialogue = dialogues.remove(oldId);
            dialogues.put(newId, new MapDesignLibrary.AuthoredDialogue(
                    newId, dialogue.speakerName(), dialogue.bodyText(), dialogue.followUpInteractionId(),
                    dialogue.visualPath(), dialogue.choices(), dialogue.nodes(),
                    dialogue.rewards(), dialogue.firstTalkNodeId(), dialogue.repeatTalkNodeId()));
            replaceOrRemoveDialogueReferences(oldId, newId);
        }
        loadedId = "";
        refreshCatalog(newId);
        markDirty();
    }

    private void deleteEntry() {
        if (loadedId.isBlank()) {
            return;
        }
        List<String> references = kind == Kind.QUEST ? questReferences(loadedId) : dialogueReferences(loadedId);
        String replacement = "";
        if (!references.isEmpty()) {
            List<String> replacements = new ArrayList<>();
            replacements.add("<Remove references>");
            if (kind == Kind.QUEST) {
                quests.keySet().stream().filter(id -> !id.equals(loadedId)).forEach(replacements::add);
            } else {
                dialogues.keySet().stream().filter(id -> !id.equals(loadedId)).forEach(replacements::add);
            }
            Object selected = JOptionPane.showInputDialog(
                    this,
                    "This content is referenced by:\n- " + String.join("\n- ", references)
                            + "\n\nChoose a replacement, or explicitly remove every reference:",
                    "Resolve References",
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    replacements.toArray(),
                    replacements.get(0)
            );
            if (selected == null) {
                return;
            }
            replacement = "<Remove references>".equals(selected) ? "" : selected.toString();
        }
        if (JOptionPane.showConfirmDialog(this, "Delete " + loadedId + "?",
                "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        if (!references.isEmpty()) {
            if (kind == Kind.QUEST) {
                replaceOrRemoveQuestReferences(loadedId, replacement);
            } else {
                replaceOrRemoveDialogueReferences(loadedId, replacement);
            }
        }
        if (kind == Kind.QUEST) {
            quests.remove(loadedId);
        } else {
            dialogues.remove(loadedId);
        }
        loadedId = "";
        dirty = true;
        refreshCatalog("");
    }

    private void replaceOrRemoveQuestReferences(String oldId, String replacement) {
        for (int index = 0; index < npcs.size(); index++) {
            MapDesignLibrary.CustomNpc npc = npcs.get(index);
            List<String> ids = new ArrayList<>();
            for (String id : npc.questIds()) {
                String updated = id.equals(oldId) ? replacement : id;
                if (!updated.isBlank() && !ids.contains(updated)) {
                    ids.add(updated);
                }
            }
            npcs.set(index, copyNpc(npc, npc.interactionId(), ids));
        }
        List<MapDesignLibrary.AuthoredQuest> values = new ArrayList<>(quests.values());
        for (MapDesignLibrary.AuthoredQuest quest : values) {
            if (quest.questId().equals(oldId)) {
                continue;
            }
            List<MapDesignLibrary.QuestRequirement> requirements = quest.requirements().stream()
                    .filter(requirement -> requirement.type()
                            != MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                            || !requirement.targetId().equals(oldId)
                            || !replacement.isBlank())
                    .map(requirement -> requirement.type()
                                    == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                                    && requirement.targetId().equals(oldId)
                            ? new MapDesignLibrary.QuestRequirement(
                                    requirement.type(), replacement, requirement.skill(), requirement.amount())
                            : requirement)
                    .toList();
            List<MapDesignLibrary.QuestStage> stages = quest.stages().stream()
                    .map(stage -> new MapDesignLibrary.QuestStage(
                            stage.stageId(), stage.title(), stage.journalText(), stage.completionMode(),
                            stage.objectives().stream()
                                    .filter(objective -> objective.type()
                                            != MapDesignLibrary.QuestObjectiveType.COMPLETE_QUEST
                                            || !objective.targetId().equals(oldId)
                                            || !replacement.isBlank())
                                    .map(objective -> objective.type()
                                                    == MapDesignLibrary.QuestObjectiveType.COMPLETE_QUEST
                                                    && objective.targetId().equals(oldId)
                                            ? new MapDesignLibrary.QuestObjective(
                                                    objective.objectiveId(), objective.type(), replacement,
                                                    objective.skill(), objective.amount(),
                                                    objective.journalText(), objective.visible())
                                            : objective)
                                    .toList(),
                            stage.rewards(), rewriteQuestFlowReferences(stage.flow(), oldId, replacement)))
                    .toList();
            quests.put(quest.questId(), new MapDesignLibrary.AuthoredQuest(
                    quest.questId(), quest.displayName(), quest.summary(),
                    requirements,
                    rewriteQuestFlowReferences(quest.offerFlow(), oldId, replacement),
                    stages,
                    quest.finalRewards(),
                    rewriteQuestFlowReferences(quest.epilogueFlow(), oldId, replacement)));
        }
    }

    private void replaceOrRemoveDialogueReferences(String oldId, String replacement) {
        for (int index = 0; index < npcs.size(); index++) {
            MapDesignLibrary.CustomNpc npc = npcs.get(index);
            if (npc.interactionId().equals(oldId)) {
                npcs.set(index, copyNpc(npc, replacement, npc.questIds()));
            }
        }
        List<MapDesignLibrary.AuthoredDialogue> values = new ArrayList<>(dialogues.values());
        for (MapDesignLibrary.AuthoredDialogue dialogue : values) {
            if (!dialogue.interactionId().equals(oldId)
                    && dialogue.followUpInteractionId().equals(oldId)) {
                dialogues.put(dialogue.interactionId(), new MapDesignLibrary.AuthoredDialogue(
                        dialogue.interactionId(), dialogue.speakerName(), dialogue.bodyText(), replacement,
                        dialogue.visualPath(), dialogue.choices(), dialogue.nodes(), dialogue.rewards(),
                        dialogue.firstTalkNodeId(), dialogue.repeatTalkNodeId()));
            }
        }
    }

    private void rewriteQuestReferences(String oldId, String newId) {
        for (int index = 0; index < npcs.size(); index++) {
            MapDesignLibrary.CustomNpc npc = npcs.get(index);
            List<String> ids = npc.questIds().stream().map(id -> id.equals(oldId) ? newId : id).toList();
            npcs.set(index, copyNpc(npc, npc.interactionId(), ids));
        }
        List<MapDesignLibrary.AuthoredQuest> values = new ArrayList<>(quests.values());
        quests.clear();
        for (MapDesignLibrary.AuthoredQuest quest : values) {
            List<MapDesignLibrary.QuestRequirement> requirements = quest.requirements().stream()
                    .map(requirement -> requirement.type() == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                            && requirement.targetId().equals(oldId)
                            ? new MapDesignLibrary.QuestRequirement(
                                    requirement.type(), newId, requirement.skill(), requirement.amount())
                            : requirement)
                    .toList();
            List<MapDesignLibrary.QuestStage> stages = quest.stages().stream()
                    .map(stage -> new MapDesignLibrary.QuestStage(
                            stage.stageId(), stage.title(), stage.journalText(), stage.completionMode(),
                            stage.objectives().stream()
                                    .map(objective -> objective.type() == MapDesignLibrary.QuestObjectiveType.COMPLETE_QUEST
                                            && objective.targetId().equals(oldId)
                                            ? new MapDesignLibrary.QuestObjective(
                                                    objective.objectiveId(), objective.type(), newId, objective.skill(),
                                                    objective.amount(), objective.journalText(), objective.visible())
                                            : objective)
                                    .toList(),
                            stage.rewards(), rewriteQuestFlowReferences(stage.flow(), oldId, newId)))
                    .toList();
            quests.put(quest.questId(), new MapDesignLibrary.AuthoredQuest(
                    quest.questId(), quest.displayName(), quest.summary(), requirements,
                    rewriteQuestFlowReferences(quest.offerFlow(), oldId, newId),
                    stages, quest.finalRewards(),
                    rewriteQuestFlowReferences(quest.epilogueFlow(), oldId, newId)));
        }
    }

    private MapDesignLibrary.QuestFlow rewriteQuestFlowReferences(
            MapDesignLibrary.QuestFlow flow,
            String oldId,
            String replacement
    ) {
        if (flow == null) {
            return MapDesignLibrary.QuestFlow.empty();
        }
        return new MapDesignLibrary.QuestFlow(
                flow.entryNodeId(),
                flow.nodes().stream().map(node -> new MapDesignLibrary.QuestFlowNode(
                        node.nodeId(),
                        node.bodyText(),
                        node.canvasX(),
                        node.canvasY(),
                        node.choices().stream().map(choice -> new MapDesignLibrary.QuestFlowChoice(
                                choice.choiceId(),
                                choice.label(),
                                choice.targetNodeId(),
                                choice.conditions().stream()
                                        .filter(requirement -> requirement.type()
                                                != MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                                                || !requirement.targetId().equals(oldId)
                                                || !replacement.isBlank())
                                        .map(requirement -> requirement.type()
                                                        == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                                                        && requirement.targetId().equals(oldId)
                                                ? new MapDesignLibrary.QuestRequirement(
                                                        requirement.type(), replacement,
                                                        requirement.skill(), requirement.amount())
                                                : requirement)
                                        .toList(),
                                choice.action(),
                                choice.requiredItemId(),
                                choice.takeItemId(),
                                choice.takeItemAmount(),
                                choice.rewards(),
                                choice.firstTalkOnly(),
                                choice.terminalBodyText()
                        )).toList()
                )).toList()
        );
    }

    private List<String> questReferences(String id) {
        List<String> result = new ArrayList<>();
        for (MapDesignLibrary.CustomNpc npc : npcs) {
            if (npc.questIds().contains(id)) {
                result.add("NPC " + npc.displayName());
            }
        }
        for (MapDesignLibrary.AuthoredQuest quest : quests.values()) {
            if (quest.questId().equals(id)) {
                continue;
            }
            if (quest.requirements().stream().anyMatch(requirement -> requirement.targetId().equals(id))
                    || quest.stages().stream().flatMap(stage -> stage.objectives().stream())
                    .anyMatch(objective -> objective.targetId().equals(id))
                    || flowReferencesQuest(quest.offerFlow(), id)
                    || quest.stages().stream().anyMatch(stage -> flowReferencesQuest(stage.flow(), id))
                    || flowReferencesQuest(quest.epilogueFlow(), id)) {
                result.add("Quest " + quest.displayName());
            }
        }
        return result;
    }

    private boolean flowReferencesQuest(MapDesignLibrary.QuestFlow flow, String questId) {
        return flow != null && flow.nodes().stream()
                .flatMap(node -> node.choices().stream())
                .flatMap(choice -> choice.conditions().stream())
                .anyMatch(requirement ->
                        requirement.type() == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST
                                && requirement.targetId().equals(questId));
    }

    private List<String> dialogueReferences(String id) {
        List<String> result = new ArrayList<>();
        for (MapDesignLibrary.CustomNpc npc : npcs) {
            if (npc.interactionId().equals(id)) {
                result.add("NPC " + npc.displayName());
            }
        }
        for (MapDesignLibrary.AuthoredDialogue dialogue : dialogues.values()) {
            if (dialogue.followUpInteractionId().equals(id)) {
                result.add("Dialogue " + dialogue.speakerName());
            }
        }
        return result;
    }

    private void commitAllFields() {
        commitCurrentFlow();
        updateIdentity();
    }

    private void apply() {
        commitAllFields();
        List<String> diagnostics = validateAll();
        showValidationDiagnostics(diagnostics);
        List<String> errors = diagnostics.stream()
                .filter(message -> message.startsWith("Error:")).toList();
        if (!errors.isEmpty()) {
            stateLabel.setText("Fix validation errors before applying.");
            return;
        }
        List<String> warnings = diagnostics.stream()
                .filter(message -> message.startsWith("Warning:")).toList();
        if (!warnings.isEmpty()
                && JOptionPane.showConfirmDialog(
                        this,
                        "This content has warnings:\n\n" + String.join("\n", warnings)
                                + "\n\nApply it anyway?",
                        "Confirm Warnings",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE
                ) != JOptionPane.OK_OPTION) {
            stateLabel.setText("Apply cancelled.");
            return;
        }
        try {
            host.save(List.copyOf(quests.values()), List.copyOf(dialogues.values()), List.copyOf(npcs));
            dirty = false;
            stateLabel.setText("Saved all affected content catalogs.");
        } catch (IOException exception) {
            stateLabel.setText("Save failed and was not applied: " + exception.getMessage());
        }
    }

    private void validateDraft() {
        commitAllFields();
        List<String> diagnostics = validateAll();
        showValidationDiagnostics(diagnostics);
        long errors = diagnostics.stream().filter(message -> message.startsWith("Error:")).count();
        long warnings = diagnostics.stream().filter(message -> message.startsWith("Warning:")).count();
        if (errors == 0 && warnings == 0) {
            stateLabel.setText("Validation passed.");
        } else {
            stateLabel.setText("Validation found " + errors + " error(s) and "
                    + warnings + " warning(s).");
        }
    }

    private void showValidationDiagnostics(List<String> diagnostics) {
        diagnosticsArea.setText(diagnostics.isEmpty()
                ? "Validation passed. No quest or dialogue issues found."
                : String.join("\n", diagnostics));
        diagnosticsArea.setCaretPosition(0);
        inspectorTabs.setSelectedIndex(inspectorTabs.getTabCount() - 1);
    }

    private List<String> validateAll() {
        List<String> errors = new ArrayList<>();
        for (MapDesignLibrary.ValidationIssue issue : host.validate(
                List.copyOf(quests.values()),
                List.copyOf(dialogues.values()),
                List.copyOf(npcs)
        )) {
            errors.add((issue.severity() == MapDesignLibrary.ValidationSeverity.ERROR
                    ? "Error: "
                    : "Warning: ") + issue.message());
        }
        if (kind == Kind.QUEST) {
            for (MapDesignLibrary.AuthoredQuest quest : quests.values()) {
                if (quest.questId().isBlank() || quest.stages().isEmpty()) {
                    errors.add("Error: " + quest.displayName() + " requires an ID and at least one stage.");
                }
                boolean accepts = quest.offerFlow().nodes().stream().flatMap(node -> node.choices().stream())
                        .anyMatch(choice -> choice.action() == MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST);
                if (!accepts) {
                    errors.add("Error: " + quest.displayName() + " offer has no Accept Quest transition.");
                }
                Set<String> objectiveIds = new LinkedHashSet<>();
                for (MapDesignLibrary.QuestStage stage : quest.stages()) {
                    for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
                        if (!objectiveIds.add(objective.objectiveId())) {
                            errors.add("Error: duplicate objective ID " + objective.objectiveId()
                                    + " in " + quest.displayName() + ".");
                        }
                    }
                }
            }
            detectRequirementCycles(errors);
        }
        if (kind == Kind.QUEST) {
            for (MapDesignLibrary.AuthoredQuest quest : quests.values()) {
                addFlowDiagnostics(errors, quest.displayName() + " / Offer", quest.offerFlow());
                for (MapDesignLibrary.QuestStage stage : quest.stages()) {
                    addFlowDiagnostics(errors, quest.displayName() + " / " + stage.title(), stage.flow());
                }
                if (!quest.epilogueFlow().nodes().isEmpty()) {
                    addFlowDiagnostics(errors, quest.displayName() + " / Epilogue", quest.epilogueFlow());
                }
            }
        } else {
            for (MapDesignLibrary.AuthoredDialogue dialogue : dialogues.values()) {
                addFlowDiagnostics(errors, dialogue.speakerName(), dialogueFlow(dialogue));
            }
        }
        return errors;
    }

    private void addFlowDiagnostics(
            List<String> diagnostics,
            String owner,
            MapDesignLibrary.QuestFlow flow
    ) {
        for (String message : QuestFlowCanvas.diagnostics(flow)) {
            int separator = message.indexOf(':');
            String severity = separator < 0 ? "Warning:" : message.substring(0, separator + 1);
            String detail = separator < 0 ? message : message.substring(separator + 1).trim();
            diagnostics.add(severity + " " + owner + ": " + detail);
        }
    }

    private void detectRequirementCycles(List<String> errors) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        quests.values().forEach(quest -> dependencies.put(
                quest.questId(),
                quest.requirements().stream()
                        .filter(requirement -> requirement.type()
                                == MapDesignLibrary.QuestRequirementType.COMPLETED_QUEST)
                        .map(MapDesignLibrary.QuestRequirement::targetId)
                        .toList()
        ));
        for (String id : dependencies.keySet()) {
            if (hasCycle(id, id, dependencies, new LinkedHashSet<>())) {
                errors.add("Error: circular quest prerequisite involving " + id + ".");
            }
        }
    }

    private boolean hasCycle(
            String origin,
            String current,
            Map<String, List<String>> dependencies,
            Set<String> visited
    ) {
        if (!visited.add(current)) {
            return false;
        }
        for (String target : dependencies.getOrDefault(current, List.of())) {
            if (target.equals(origin) || hasCycle(origin, target, dependencies, visited)) {
                return true;
            }
        }
        return false;
    }

    private void revert() {
        if (dirty && !confirmDiscard()) {
            return;
        }
        restoreDraftsFromHost();
        String id = loadedId;
        loadedId = "";
        dirty = false;
        refreshCatalog(id);
    }

    private void restoreDraftsFromHost() {
        quests.clear();
        dialogues.clear();
        npcs.clear();
        loadedFlowSlot = null;
        questInspectorOwnerId = "";
        clearStageEditorOwnership();
        host.quests().forEach(quest -> quests.put(quest.questId(), quest));
        host.dialogues().forEach(dialogue -> dialogues.put(dialogue.interactionId(), dialogue));
        npcs.addAll(host.npcs());
        dirty = false;
    }

    private void updateDiagnostics() {
        List<String> diagnostics = new ArrayList<>(canvas.diagnostics());
        if (diagnostics.isEmpty()) {
            diagnostics.add("No flow diagnostics.");
        }
        diagnosticsArea.setText(String.join("\n", diagnostics));
        diagnosticsArea.setCaretPosition(0);
    }

    private boolean confirmDiscard() {
        return JOptionPane.showConfirmDialog(
                this,
                "Discard unapplied changes?",
                "Unsaved Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION;
    }

    private void markDirty() {
        if (loading) {
            return;
        }
        dirty = true;
        commitCurrentFlow();
        updateDiagnostics();
        updateState();
    }

    private void flagDraftDirty() {
        if (loading) {
            return;
        }
        dirty = true;
        updateDiagnostics();
        updateState();
    }

    private void updateState() {
        stateLabel.setText(dirty ? "Unapplied changes." : "Ready.");
    }

    private MapDesignLibrary.AuthoredQuest copyQuest(
            MapDesignLibrary.AuthoredQuest quest,
            MapDesignLibrary.QuestFlow offer,
            List<MapDesignLibrary.QuestStage> stages,
            MapDesignLibrary.QuestFlow epilogue
    ) {
        boolean ownsInspector = quest != null
                && quest.questId().equals(loadedId)
                && loadedId.equals(questInspectorOwnerId);
        return new MapDesignLibrary.AuthoredQuest(
                quest.questId(),
                quest.displayName(),
                quest.summary(),
                ownsInspector ? list(requirementModel) : quest.requirements(),
                offer,
                stages,
                ownsInspector ? list(finalRewardModel) : quest.finalRewards(),
                epilogue
        );
    }

    private MapDesignLibrary.CustomNpc copyNpc(
            MapDesignLibrary.CustomNpc npc,
            String dialogueId,
            List<String> questIds
    ) {
        return new MapDesignLibrary.CustomNpc(
                npc.npcId(), npc.displayName(), npc.imagePath(), npc.talkSoundPath(), dialogueId,
                npc.shop(), npc.characterModel(), questIds);
    }

    private MapDesignLibrary.QuestFlow dialogueFlow(MapDesignLibrary.AuthoredDialogue dialogue) {
        if (dialogue == null) {
            return MapDesignLibrary.QuestFlow.empty();
        }
        List<MapDesignLibrary.QuestFlowNode> nodes = new ArrayList<>();
        nodes.add(new MapDesignLibrary.QuestFlowNode(
                "start",
                dialogue.bodyText(),
                70,
                80,
                dialogueChoicesToFlow("start", dialogue.choices())
        ));
        for (int index = 0; index < dialogue.nodes().size(); index++) {
            MapDesignLibrary.AuthoredDialogueNode node = dialogue.nodes().get(index);
            nodes.add(new MapDesignLibrary.QuestFlowNode(
                    node.nodeId(),
                    node.bodyText(),
                    node.canvasX(),
                    node.canvasY(),
                    dialogueChoicesToFlow(node.nodeId(), node.choices())
            ));
        }
        String entry = nodes.stream().anyMatch(node -> dialogue.firstTalkNodeId().equals(node.nodeId()))
                ? dialogue.firstTalkNodeId()
                : "start";
        return new MapDesignLibrary.QuestFlow(entry, nodes);
    }

    private List<MapDesignLibrary.QuestFlowChoice> dialogueChoicesToFlow(
            String sourceId,
            List<MapDesignLibrary.AuthoredDialogueChoice> choices
    ) {
        List<MapDesignLibrary.QuestFlowChoice> result = new ArrayList<>();
        for (int index = 0; index < choices.size(); index++) {
            MapDesignLibrary.AuthoredDialogueChoice choice = choices.get(index);
            List<MapDesignLibrary.QuestRequirement> conditions = choice.requiredItemName().isBlank()
                    ? List.of()
                    : List.of(new MapDesignLibrary.QuestRequirement(
                            MapDesignLibrary.QuestRequirementType.POSSESS_ITEM,
                            choice.requiredItemName(),
                            null,
                            1
                    ));
            List<MapDesignLibrary.RewardDefinition> rewards = choice.rewards();
            result.add(new MapDesignLibrary.QuestFlowChoice(
                    choice.choiceId().isBlank() ? sourceId + "_choice_" + index : choice.choiceId(),
                    choice.label(),
                    choice.targetNodeId(),
                    conditions,
                    MapDesignLibrary.QuestFlowAction.NONE,
                    choice.requiredItemName(),
                    choice.takeItemName(),
                    choice.takeItemAmount(),
                    rewards,
                    choice.firstTalkOnly(),
                    choice.bodyText()
            ));
        }
        return result;
    }

    private MapDesignLibrary.AuthoredDialogue dialogueFromFlow(
            MapDesignLibrary.AuthoredDialogue existing,
            MapDesignLibrary.QuestFlow flow
    ) {
        MapDesignLibrary.QuestFlowNode start = flow.nodes().stream()
                .filter(node -> "start".equals(node.nodeId())).findFirst()
                .orElse(flow.nodes().isEmpty() ? null : flow.nodes().get(0));
        List<MapDesignLibrary.AuthoredDialogueNode> nodes = flow.nodes().stream()
                .filter(node -> start == null || !node.nodeId().equals(start.nodeId()))
                .map(node -> new MapDesignLibrary.AuthoredDialogueNode(
                        node.nodeId(),
                        node.bodyText(),
                        node.canvasX(),
                        node.canvasY(),
                        flowChoicesToDialogue(node.choices())))
                .toList();
        return new MapDesignLibrary.AuthoredDialogue(
                existing.interactionId(),
                existing.speakerName(),
                start == null ? existing.bodyText() : start.bodyText(),
                existing.followUpInteractionId(),
                existing.visualPath(),
                start == null ? List.of() : flowChoicesToDialogue(start.choices()),
                nodes,
                existing.rewards(),
                dialogueFirstTalkNodeId,
                dialogueRepeatTalkNodeId
        );
    }

    private List<MapDesignLibrary.AuthoredDialogueChoice> flowChoicesToDialogue(
            List<MapDesignLibrary.QuestFlowChoice> choices
    ) {
        return choices.stream()
                .map(choice -> new MapDesignLibrary.AuthoredDialogueChoice(
                    choice.label(),
                    choice.terminalBodyText(),
                    choice.targetNodeId(),
                    choice.requiredItemId(),
                    choice.takeItemId(),
                    choice.takeItemAmount(),
                    choice.firstTalkOnly(),
                    choice.choiceId(),
                    choice.rewards()
                ))
                .toList();
    }

    private List<String> itemOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        host.items().forEach(item -> values.add(referenceLabel(item.displayName(), item.itemId())));
        host.limbs().forEach(limb -> values.add(referenceLabel(limb.displayName(), limb.limbId())));
        return values;
    }

    private List<String> npcOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        host.npcs().forEach(npc -> values.add(referenceLabel(npc.displayName(), npc.npcId())));
        return values;
    }

    private List<String> mobOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        host.mobs().forEach(mob -> values.add(referenceLabel(mob.displayName(), mob.mobId())));
        return values;
    }

    private List<String> questOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        quests.values().forEach(quest -> values.add(referenceLabel(quest.displayName(), quest.questId())));
        return values;
    }

    private List<String> dialogueOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        dialogues.values().forEach(dialogue ->
                values.add(referenceLabel(dialogue.speakerName(), dialogue.interactionId())));
        return values;
    }

    private List<String> requirementTargetOptions(MapDesignLibrary.QuestRequirementType type) {
        if (type == null) {
            return List.of("");
        }
        return switch (type) {
            case POSSESS_ITEM, EQUIPPED_ITEM_OR_LIMB -> itemOptions();
            case COMPLETED_QUEST -> questOptions();
            case PLAYER_LEVEL, SKILL_LEVEL -> List.of("");
        };
    }

    private List<String> objectiveTargetOptions(MapDesignLibrary.QuestObjectiveType type) {
        if (type == null) {
            return List.of("");
        }
        return switch (type) {
            case POSSESS_ITEM, TURN_IN_ITEM, EQUIPPED_ITEM_OR_LIMB -> itemOptions();
            case TALK_TO_NPC -> npcOptions();
            case DEFEAT_ENEMY -> mobOptions();
            case COMPLETE_QUEST -> questOptions();
            case PLAYER_LEVEL, SKILL_LEVEL -> List.of("");
        };
    }

    private Set<String> allObjectiveIds() {
        Set<String> ids = new LinkedHashSet<>();
        MapDesignLibrary.AuthoredQuest quest = quests.get(loadedId);
        if (quest != null) {
            quest.stages().forEach(stage ->
                    stage.objectives().forEach(objective -> ids.add(objective.objectiveId())));
        }
        for (int index = 0; index < objectiveModel.size(); index++) {
            ids.add(objectiveModel.get(index).objectiveId());
        }
        return ids;
    }

    private List<String> nodeOptions() {
        List<String> values = new ArrayList<>();
        values.add("");
        canvas.flow().nodes().forEach(node -> values.add(referenceLabel(
                node.bodyText().isBlank() ? "Node" : abbreviate(node.bodyText(), 35),
                node.nodeId()
        )));
        return values;
    }

    private JComboBox<String> editableContentBox(List<String> values) {
        return new SearchableReferenceBox(values);
    }

    private String selectedText(JComboBox<String> box) {
        if (box instanceof SearchableReferenceBox referenceBox) {
            return referenceBox.selectedId();
        }
        Object value = box.getEditor().getItem();
        return referenceId(value == null ? "" : value.toString());
    }

    private String referenceDisplay(String id, List<String> options) {
        if (id == null || id.isBlank()) {
            return "";
        }
        return options.stream()
                .filter(option -> referenceId(option).equals(id))
                .findFirst()
                .orElse("[Unavailable] [" + id + "]");
    }

    private static String referenceLabel(String displayName, String id) {
        String safeName = displayName == null || displayName.isBlank() ? "Unnamed" : displayName.trim();
        return safeName + " [" + id + "]";
    }

    private static String referenceId(String value) {
        String safe = value == null ? "" : value.trim();
        int close = safe.lastIndexOf(']');
        int open = close < 0 ? -1 : safe.lastIndexOf('[', close);
        return open >= 0 && close == safe.length() - 1
                ? safe.substring(open + 1, close).trim()
                : safe;
    }

    private static String abbreviate(String value, int maximum) {
        String safe = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(1, maximum - 1)) + "…";
    }

    private static final class SearchableReferenceBox extends JComboBox<String> {
        private SearchableReferenceBox(List<String> options) {
            replaceOptions(options);
            setEditable(false);
            setToolTipText("Type while focused to jump to a matching display name or stable ID.");
            setKeySelectionManager((key, model) -> {
                String needle = String.valueOf(key).toLowerCase();
                for (int index = 0; index < model.getSize(); index++) {
                    Object element = model.getElementAt(index);
                    String option = element == null ? "" : element.toString();
                    if (option != null && option.toLowerCase().contains(needle)) {
                        return index;
                    }
                }
                return -1;
            });
        }

        private void replaceOptions(List<String> options) {
            String selected = selectedId();
            setModel(new DefaultComboBoxModel<>(
                    (options == null ? List.<String>of("") : options).toArray(String[]::new)
            ));
            selectId(selected);
        }

        private void selectId(String id) {
            String safeId = id == null ? "" : id.trim();
            for (int index = 0; index < getItemCount(); index++) {
                String option = getItemAt(index);
                if (referenceId(option).equals(safeId)) {
                    super.setSelectedItem(option);
                    return;
                }
            }
            if (!safeId.isBlank()) {
                String unavailable = "[Unavailable] [" + safeId + "]";
                addItem(unavailable);
                super.setSelectedItem(unavailable);
            } else if (getItemCount() > 0) {
                super.setSelectedItem(getItemAt(0));
            }
        }

        private String selectedId() {
            Object value = getSelectedItem();
            return referenceId(value == null ? "" : value.toString());
        }

        @Override
        public void setSelectedItem(Object value) {
            selectId(referenceId(value == null ? "" : value.toString()));
        }
    }

    private Set<String> choiceModelToIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < choiceModel.size(); index++) {
            ids.add(choiceModel.get(index).choiceId());
        }
        return ids;
    }

    private String uniqueId(String base, java.util.Collection<String> existing) {
        String normalized = base == null ? "entry" : base.trim().toLowerCase()
                .replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            normalized = "entry";
        }
        String candidate = normalized;
        int suffix = 2;
        while (existing.contains(candidate)) {
            candidate = normalized + "_" + suffix++;
        }
        return candidate;
    }

    private String uniqueRewardId(
            java.util.Collection<MapDesignLibrary.RewardDefinition> existing,
            String base
    ) {
        Set<String> ids = new LinkedHashSet<>();
        if (existing != null) {
            existing.forEach(reward -> ids.add(reward.rewardId()));
        }
        return uniqueId(base, ids);
    }

    private <T> List<T> list(DefaultListModel<T> model) {
        List<T> result = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            result.add(model.get(index));
        }
        return List.copyOf(result);
    }

    private <T> void removeSelected(DefaultListModel<T> model, JList<T> list) {
        int index = list == null ? -1 : list.getSelectedIndex();
        if (index >= 0) {
            model.remove(index);
            markDirty();
        }
    }

    private String text(JTextField field, String fallback) {
        String value = field.getText() == null ? "" : field.getText().trim();
        return value.isBlank() ? fallback : value;
    }

    private String areaText(JTextArea area, String fallback) {
        String value = area.getText() == null ? "" : area.getText().trim();
        return value.isBlank() ? fallback : value;
    }

    private JPanel form() {
        return new JPanel(new GridBagLayout());
    }

    private void addRow(JPanel panel, String label, Component component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = panel.getComponentCount() / 2;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel(label), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = left.gridy;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.NORTHWEST;
        right.insets = new Insets(4, 4, 4, 4);
        panel.add(component, right);
    }

    private DocumentListener documentListener(Runnable runnable) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                runnable.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                runnable.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                runnable.run();
            }
        };
    }

    private record CatalogEntry(String id, String label) {
    }

    private enum FlowSlotKind {
        OFFER,
        STAGE,
        EPILOGUE,
        DIALOGUE
    }

    private record FlowSlot(FlowSlotKind kind, int index, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}

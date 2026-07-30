package org.main.tools;

import org.main.content.MapDesignLibrary;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Editable quest/dialogue graph surface. Drag nodes to position them and drag a
 * choice port onto another node to create or replace an arrow.
 */
final class QuestFlowCanvas extends JPanel {
    private static final String NODE_KEY_SEPARATOR = "\u001f";
    private static final String SINGLE_SECTION_ID = "flow";
    private static final int NODE_WIDTH = 250;
    private static final int HEADER_HEIGHT = 30;
    private static final int CHOICE_HEIGHT = 25;
    private static final int MIN_NODE_HEIGHT = 86;

    private final List<MapDesignLibrary.QuestFlowNode> nodes = new ArrayList<>();
    private final LinkedHashMap<String, String> sectionTitles = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> entryNodeIds = new LinkedHashMap<>();
    private final LinkedHashMap<String, Point> sectionAnchors = new LinkedHashMap<>();
    private String activeSectionId = SINGLE_SECTION_ID;
    private String selectedNodeId = "";
    private String connectingNodeId = "";
    private int connectingChoiceIndex = -1;
    private Point connectionPoint;
    private Point dragOffset;
    private Point panAnchor;
    private double zoom = 1.0;
    private int panX;
    private int panY;
    private Consumer<MapDesignLibrary.QuestFlowNode> selectionListener = ignored -> { };
    private Consumer<String> sectionSelectionListener = ignored -> { };
    private Consumer<String> messageListener = ignored -> { };
    private Runnable changeListener = () -> { };

    QuestFlowCanvas() {
        setBackground(new Color(27, 29, 35));
        setPreferredSize(new Dimension(1500, 950));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                Point world = toWorld(event.getPoint());
                if (SwingUtilities.isMiddleMouseButton(event) || SwingUtilities.isRightMouseButton(event)) {
                    panAnchor = event.getPoint();
                    return;
                }
                ChoicePort port = choicePortAt(world);
                if (port != null) {
                    connectingNodeId = port.nodeId();
                    connectingChoiceIndex = port.choiceIndex();
                    connectionPoint = world;
                    return;
                }
                MapDesignLibrary.QuestFlowNode node = nodeAt(world);
                selectedNodeId = node == null ? "" : node.nodeId();
                if (node != null) {
                    activeSectionId = sectionId(node.nodeId());
                } else {
                    String sectionId = sectionAt(world);
                    if (!sectionId.isBlank()) {
                        activeSectionId = sectionId;
                    }
                }
                sectionSelectionListener.accept(activeSectionId);
                selectionListener.accept(selectedNode());
                if (node != null) {
                    dragOffset = new Point(world.x - node.canvasX(), world.y - node.canvasY());
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (panAnchor != null) {
                    panX += event.getX() - panAnchor.x;
                    panY += event.getY() - panAnchor.y;
                    panAnchor = event.getPoint();
                    repaint();
                    return;
                }
                Point world = toWorld(event.getPoint());
                if (!connectingNodeId.isBlank()) {
                    connectionPoint = world;
                    repaint();
                    return;
                }
                if (!selectedNodeId.isBlank() && dragOffset != null) {
                    moveNode(selectedNodeId, world.x - dragOffset.x, world.y - dragOffset.y);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                panAnchor = null;
                dragOffset = null;
                if (!connectingNodeId.isBlank()) {
                    MapDesignLibrary.QuestFlowNode target = nodeAt(toWorld(event.getPoint()));
                    if (target != null && !target.nodeId().equals(connectingNodeId)) {
                        connect(connectingNodeId, connectingChoiceIndex, target.nodeId());
                    }
                    connectingNodeId = "";
                    connectingChoiceIndex = -1;
                    connectionPoint = null;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                double oldZoom = zoom;
                zoom = Math.max(0.35, Math.min(2.25, zoom * (event.getWheelRotation() < 0 ? 1.1 : 0.9)));
                Point point = event.getPoint();
                panX = (int) Math.round(point.x - (point.x - panX) * zoom / oldZoom);
                panY = (int) Math.round(point.y - (point.y - panY) * zoom / oldZoom);
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setSelectionListener(Consumer<MapDesignLibrary.QuestFlowNode> listener) {
        selectionListener = listener == null ? ignored -> { } : listener;
    }

    void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> { } : listener;
    }

    void setSectionSelectionListener(Consumer<String> listener) {
        sectionSelectionListener = listener == null ? ignored -> { } : listener;
    }

    void setMessageListener(Consumer<String> listener) {
        messageListener = listener == null ? ignored -> { } : listener;
    }

    void setFlow(MapDesignLibrary.QuestFlow flow) {
        setSections(List.of(new FlowSection(SINGLE_SECTION_ID, "Flow", flow)));
        activeSectionId = SINGLE_SECTION_ID;
    }

    void setSections(List<FlowSection> sections) {
        nodes.clear();
        sectionTitles.clear();
        entryNodeIds.clear();
        sectionAnchors.clear();
        List<FlowSection> safeSections = sections == null ? List.of() : sections;
        for (FlowSection section : safeSections) {
            if (section == null || section.id() == null || section.id().isBlank()) {
                continue;
            }
            String sectionId = section.id();
            sectionTitles.put(sectionId, section.title() == null ? sectionId : section.title());
            MapDesignLibrary.QuestFlow flow =
                    section.flow() == null ? MapDesignLibrary.QuestFlow.empty() : section.flow();
            entryNodeIds.put(sectionId, key(sectionId, flow.entryNodeId()));
            for (MapDesignLibrary.QuestFlowNode node : flow.nodes()) {
                nodes.add(toCanvasNode(sectionId, node));
            }
        }
        activeSectionId = sectionTitles.isEmpty() ? SINGLE_SECTION_ID : sectionTitles.keySet().iterator().next();
        separateOverlappingSections();
        updateSectionAnchors();
        selectedNodeId = "";
        revalidate();
        repaint();
    }

    MapDesignLibrary.QuestFlow flow() {
        return sectionFlow(activeSectionId);
    }

    MapDesignLibrary.QuestFlow sectionFlow(String sectionId) {
        if (sectionId == null || !sectionTitles.containsKey(sectionId)) {
            return MapDesignLibrary.QuestFlow.empty();
        }
        List<MapDesignLibrary.QuestFlowNode> authoredNodes = nodes.stream()
                .filter(node -> sectionId.equals(sectionId(node.nodeId())))
                .map(this::toAuthoredNode)
                .toList();
        return new MapDesignLibrary.QuestFlow(
                localId(entryNodeIds.getOrDefault(sectionId, "")),
                authoredNodes
        );
    }

    void focusSection(String sectionId) {
        if (sectionId == null || !sectionTitles.containsKey(sectionId)) {
            return;
        }
        activeSectionId = sectionId;
        selectedNodeId = "";
        nodes.stream()
                .filter(node -> sectionId.equals(sectionId(node.nodeId())))
                .findFirst()
                .ifPresent(node -> {
                    panX = 45 - (int) Math.round(node.canvasX() * zoom);
                    panY = 85 - (int) Math.round(node.canvasY() * zoom);
                });
        if (nodes.stream().noneMatch(node -> sectionId.equals(sectionId(node.nodeId())))) {
            Point anchor = sectionAnchors.get(sectionId);
            if (anchor != null) {
                panX = 45 - (int) Math.round(anchor.x * zoom);
                panY = 85 - (int) Math.round(anchor.y * zoom);
            }
        }
        selectionListener.accept(null);
        repaint();
    }

    String activeSectionId() {
        return activeSectionId;
    }

    MapDesignLibrary.QuestFlowNode selectedNode() {
        return nodes.stream()
                .filter(node -> node.nodeId().equals(selectedNodeId))
                .findFirst()
                .map(this::toAuthoredNode)
                .orElse(null);
    }

    void selectNode(String nodeId) {
        selectedNodeId = nodeId == null || nodeId.isBlank() ? "" : key(activeSectionId, nodeId);
        selectionListener.accept(selectedNode());
        repaint();
    }

    boolean setEntryNode(String nodeId) {
        String nodeKey = key(activeSectionId, nodeId);
        if (nodeId != null && nodes.stream().anyMatch(node -> node.nodeId().equals(nodeKey))) {
            entryNodeIds.put(activeSectionId, nodeKey);
            changeListener.run();
            repaint();
            return true;
        }
        return false;
    }

    String activeSectionTitle() {
        return sectionTitles.getOrDefault(activeSectionId, activeSectionId);
    }

    boolean activeSectionHasEntry() {
        String entryNodeId = entryNodeIds.getOrDefault(activeSectionId, "");
        return !entryNodeId.isBlank()
                && nodes.stream().anyMatch(node -> node.nodeId().equals(entryNodeId));
    }

    void addNode(MapDesignLibrary.QuestFlowNode node) {
        if (node == null) {
            return;
        }
        MapDesignLibrary.QuestFlowNode canvasNode = toCanvasNode(activeSectionId, node);
        canvasNode = positionNewSectionNode(canvasNode);
        nodes.add(canvasNode);
        if (entryNodeIds.getOrDefault(activeSectionId, "").isBlank()) {
            entryNodeIds.put(activeSectionId, canvasNode.nodeId());
        }
        selectNode(node.nodeId());
        changeListener.run();
    }

    void replaceNode(MapDesignLibrary.QuestFlowNode replacement) {
        if (replacement == null) {
            return;
        }
        MapDesignLibrary.QuestFlowNode canvasReplacement = toCanvasNode(activeSectionId, replacement);
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).nodeId().equals(selectedNodeId)) {
                String oldId = selectedNodeId;
                nodes.set(index, canvasReplacement);
                if (!oldId.equals(canvasReplacement.nodeId())) {
                    for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                        MapDesignLibrary.QuestFlowNode node = nodes.get(nodeIndex);
                        if (!sectionId(node.nodeId()).equals(activeSectionId)) {
                            continue;
                        }
                        List<MapDesignLibrary.QuestFlowChoice> choices = node.choices().stream()
                                .map(choice -> choice.targetNodeId().equals(oldId)
                                        ? new MapDesignLibrary.QuestFlowChoice(
                                                choice.choiceId(), choice.label(), canvasReplacement.nodeId(),
                                                choice.conditions(), choice.action(), choice.requiredItemId(),
                                                choice.takeItemId(), choice.takeItemAmount(),
                                                choice.rewards(), choice.firstTalkOnly(),
                                                choice.terminalBodyText())
                                        : choice)
                                .toList();
                        nodes.set(nodeIndex, new MapDesignLibrary.QuestFlowNode(
                                node.nodeId(), node.bodyText(), node.canvasX(), node.canvasY(), choices));
                    }
                    if (entryNodeIds.getOrDefault(activeSectionId, "").equals(oldId)) {
                        entryNodeIds.put(activeSectionId, canvasReplacement.nodeId());
                    }
                }
                selectedNodeId = canvasReplacement.nodeId();
                selectionListener.accept(replacement);
                changeListener.run();
                repaint();
                return;
            }
        }
    }

    void removeSelectedNode() {
        if (selectedNodeId.isBlank()) {
            return;
        }
        String removed = selectedNodeId;
        String removedSection = sectionId(removed);
        nodes.removeIf(node -> node.nodeId().equals(removed));
        for (int index = 0; index < nodes.size(); index++) {
            MapDesignLibrary.QuestFlowNode node = nodes.get(index);
            if (!sectionId(node.nodeId()).equals(removedSection)) {
                continue;
            }
            List<MapDesignLibrary.QuestFlowChoice> choices = node.choices().stream()
                    .map(choice -> choice.targetNodeId().equals(removed)
                            ? new MapDesignLibrary.QuestFlowChoice(
                                    choice.choiceId(), choice.label(), "", choice.conditions(), choice.action(),
                                    choice.requiredItemId(), choice.takeItemId(), choice.takeItemAmount(),
                                    choice.rewards(),
                                    choice.firstTalkOnly(), choice.terminalBodyText())
                            : choice)
                    .toList();
            nodes.set(index, new MapDesignLibrary.QuestFlowNode(
                    node.nodeId(), node.bodyText(), node.canvasX(), node.canvasY(), choices));
        }
        if (entryNodeIds.getOrDefault(removedSection, "").equals(removed)) {
            entryNodeIds.put(removedSection, nodes.stream()
                    .filter(node -> sectionId(node.nodeId()).equals(removedSection))
                    .map(MapDesignLibrary.QuestFlowNode::nodeId)
                    .findFirst()
                    .orElse(""));
        }
        selectedNodeId = "";
        selectionListener.accept(null);
        changeListener.run();
        repaint();
    }

    void duplicateSelectedNode(String newId) {
        MapDesignLibrary.QuestFlowNode selected = selectedNode();
        if (selected == null || newId == null || newId.isBlank()) {
            return;
        }
        addNode(new MapDesignLibrary.QuestFlowNode(
                newId,
                selected.bodyText(),
                selected.canvasX() + 40,
                selected.canvasY() + 40,
                selected.choices().stream()
                        .map(choice -> new MapDesignLibrary.QuestFlowChoice(
                                newId + "_" + choice.choiceId(),
                                choice.label(),
                                choice.targetNodeId(),
                                choice.conditions(),
                                choice.action(),
                                choice.requiredItemId(),
                                choice.takeItemId(),
                                choice.takeItemAmount(),
                                choice.rewards(),
                                choice.firstTalkOnly(),
                                choice.terminalBodyText()))
                        .toList()
        ));
    }

    void autoLayout() {
        int sectionX = 80;
        for (String sectionId : sectionTitles.keySet()) {
            List<Integer> indexes = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                if (sectionId.equals(sectionId(nodes.get(index).nodeId()))) {
                    indexes.add(index);
                }
            }
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, indexes.size()))));
            for (int localIndex = 0; localIndex < indexes.size(); localIndex++) {
                int nodeIndex = indexes.get(localIndex);
                MapDesignLibrary.QuestFlowNode node = nodes.get(nodeIndex);
                nodes.set(nodeIndex, new MapDesignLibrary.QuestFlowNode(
                        node.nodeId(),
                        node.bodyText(),
                        sectionX + (localIndex % columns) * 310,
                        120 + (localIndex / columns) * 190,
                        node.choices()
                ));
            }
            sectionX += Math.max(1, columns) * 310 + 120;
        }
        updateSectionAnchors();
        changeListener.run();
        repaint();
    }

    List<String> diagnostics() {
        List<String> issues = new ArrayList<>();
        for (String sectionId : sectionTitles.keySet()) {
            String owner = sectionTitles.getOrDefault(sectionId, sectionId);
            diagnostics(sectionFlow(sectionId)).forEach(issue -> issues.add(owner + ": " + issue));
        }
        return List.copyOf(issues);
    }

    static List<String> diagnostics(MapDesignLibrary.QuestFlow flow) {
        List<String> issues = new ArrayList<>();
        List<MapDesignLibrary.QuestFlowNode> nodes =
                flow == null ? List.of() : flow.nodes();
        String entryNodeId = flow == null ? "" : flow.entryNodeId();
        Map<String, MapDesignLibrary.QuestFlowNode> byId = new LinkedHashMap<>();
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            if (node.nodeId().isBlank()) {
                issues.add("Error: a node has no ID.");
            } else if (byId.put(node.nodeId(), node) != null) {
                issues.add("Error: duplicate node ID " + node.nodeId() + ".");
            }
            if (node.bodyText().isBlank()) {
                issues.add("Warning: node " + node.nodeId() + " has no dialogue text.");
            }
            if (node.choices().isEmpty()) {
                issues.add("Warning: node " + node.nodeId() + " is a dead end.");
            }
        }
        if (!nodes.isEmpty() && !byId.containsKey(entryNodeId)) {
            issues.add("Error: entry node " + entryNodeId + " is missing.");
        }
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            for (MapDesignLibrary.QuestFlowChoice choice : node.choices()) {
                if (!choice.targetNodeId().isBlank() && !byId.containsKey(choice.targetNodeId())) {
                    issues.add("Error: " + node.nodeId() + " choice " + choice.label()
                            + " points to missing node " + choice.targetNodeId() + ".");
                }
            }
        }
        if (!nodes.isEmpty()) {
            java.util.Set<String> reachable = new java.util.LinkedHashSet<>();
            collectReachable(entryNodeId, byId, reachable);
            byId.keySet().stream().filter(id -> !reachable.contains(id))
                    .forEach(id -> issues.add("Warning: node " + id + " is unreachable."));
        }
        return List.copyOf(issues);
    }

    private static void collectReachable(
            String nodeId,
            Map<String, MapDesignLibrary.QuestFlowNode> byId,
            java.util.Set<String> visited
    ) {
        if (!visited.add(nodeId)) {
            return;
        }
        MapDesignLibrary.QuestFlowNode node = byId.get(nodeId);
        if (node == null) {
            return;
        }
        node.choices().stream().map(MapDesignLibrary.QuestFlowChoice::targetNodeId)
                .filter(target -> target != null && !target.isBlank())
                .forEach(target -> collectReachable(target, byId, visited));
    }

    private void moveNode(String nodeId, int x, int y) {
        for (int index = 0; index < nodes.size(); index++) {
            MapDesignLibrary.QuestFlowNode node = nodes.get(index);
            if (node.nodeId().equals(nodeId)) {
                nodes.set(index, new MapDesignLibrary.QuestFlowNode(
                        node.nodeId(), node.bodyText(), Math.max(0, x), Math.max(0, y), node.choices()));
                changeListener.run();
                repaint();
                return;
            }
        }
    }

    private void connect(String nodeId, int choiceIndex, String targetNodeId) {
        if (!sectionId(nodeId).equals(sectionId(targetNodeId))) {
            messageListener.accept(
                    "Arrows stay within one quest section. Use ADVANCE_STAGE or COMPLETE_QUEST "
                            + "to move between stages."
            );
            return;
        }
        for (int index = 0; index < nodes.size(); index++) {
            MapDesignLibrary.QuestFlowNode node = nodes.get(index);
            if (!node.nodeId().equals(nodeId) || choiceIndex < 0 || choiceIndex >= node.choices().size()) {
                continue;
            }
            List<MapDesignLibrary.QuestFlowChoice> choices = new ArrayList<>(node.choices());
            MapDesignLibrary.QuestFlowChoice choice = choices.get(choiceIndex);
            choices.set(choiceIndex, new MapDesignLibrary.QuestFlowChoice(
                    choice.choiceId(), choice.label(), targetNodeId, choice.conditions(), choice.action(),
                    choice.requiredItemId(), choice.takeItemId(), choice.takeItemAmount(), choice.rewards(),
                    choice.firstTalkOnly(), choice.terminalBodyText()));
            nodes.set(index, new MapDesignLibrary.QuestFlowNode(
                    node.nodeId(), node.bodyText(), node.canvasX(), node.canvasY(), choices));
            selectionListener.accept(toAuthoredNode(nodes.get(index)));
            changeListener.run();
            repaint();
            return;
        }
    }

    private MapDesignLibrary.QuestFlowNode toCanvasNode(
            String sectionId,
            MapDesignLibrary.QuestFlowNode node
    ) {
        List<MapDesignLibrary.QuestFlowChoice> choices = node.choices().stream()
                .map(choice -> new MapDesignLibrary.QuestFlowChoice(
                        choice.choiceId(),
                        choice.label(),
                        choice.targetNodeId().isBlank() ? "" : key(sectionId, choice.targetNodeId()),
                        choice.conditions(),
                        choice.action(),
                        choice.requiredItemId(),
                        choice.takeItemId(),
                        choice.takeItemAmount(),
                        choice.rewards(),
                        choice.firstTalkOnly(),
                        choice.terminalBodyText()
                ))
                .toList();
        return new MapDesignLibrary.QuestFlowNode(
                key(sectionId, node.nodeId()),
                node.bodyText(),
                node.canvasX(),
                node.canvasY(),
                choices
        );
    }

    private MapDesignLibrary.QuestFlowNode toAuthoredNode(MapDesignLibrary.QuestFlowNode node) {
        List<MapDesignLibrary.QuestFlowChoice> choices = node.choices().stream()
                .map(choice -> new MapDesignLibrary.QuestFlowChoice(
                        choice.choiceId(),
                        choice.label(),
                        localId(choice.targetNodeId()),
                        choice.conditions(),
                        choice.action(),
                        choice.requiredItemId(),
                        choice.takeItemId(),
                        choice.takeItemAmount(),
                        choice.rewards(),
                        choice.firstTalkOnly(),
                        choice.terminalBodyText()
                ))
                .toList();
        return new MapDesignLibrary.QuestFlowNode(
                localId(node.nodeId()),
                node.bodyText(),
                node.canvasX(),
                node.canvasY(),
                choices
        );
    }

    private void separateOverlappingSections() {
        List<Rectangle> occupied = new ArrayList<>();
        int furthestX = 0;
        for (String sectionId : sectionTitles.keySet()) {
            List<Integer> indexes = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                if (sectionId.equals(sectionId(nodes.get(index).nodeId()))) {
                    indexes.add(index);
                }
            }
            if (indexes.isEmpty()) {
                continue;
            }
            int minX = indexes.stream().mapToInt(index -> nodes.get(index).canvasX()).min().orElse(80);
            boolean overlaps = indexes.stream()
                    .map(index -> expanded(nodeBounds(nodes.get(index)), 35))
                    .anyMatch(candidate -> occupied.stream().anyMatch(candidate::intersects));
            int shiftX = overlaps ? Math.max(0, furthestX + 140 - minX) : 0;
            for (int index : indexes) {
                MapDesignLibrary.QuestFlowNode node = nodes.get(index);
                if (shiftX > 0) {
                    node = new MapDesignLibrary.QuestFlowNode(
                            node.nodeId(),
                            node.bodyText(),
                            node.canvasX() + shiftX,
                            node.canvasY(),
                            node.choices()
                    );
                    nodes.set(index, node);
                }
                Rectangle bounds = expanded(nodeBounds(node), 35);
                occupied.add(bounds);
                furthestX = Math.max(furthestX, bounds.x + bounds.width);
            }
        }
    }

    private MapDesignLibrary.QuestFlowNode positionNewSectionNode(
            MapDesignLibrary.QuestFlowNode candidate
    ) {
        boolean sectionAlreadyHasNodes = nodes.stream()
                .anyMatch(node -> activeSectionId.equals(sectionId(node.nodeId())));
        if (sectionAlreadyHasNodes) {
            return candidate;
        }
        Point anchor = sectionAnchors.get(activeSectionId);
        if (anchor != null) {
            candidate = new MapDesignLibrary.QuestFlowNode(
                    candidate.nodeId(),
                    candidate.bodyText(),
                    anchor.x + 30,
                    anchor.y + 55,
                    candidate.choices()
            );
        }
        Rectangle candidateBounds = expanded(nodeBounds(candidate), 35);
        boolean overlaps = nodes.stream()
                .map(this::nodeBounds)
                .map(bounds -> expanded(bounds, 35))
                .anyMatch(candidateBounds::intersects);
        if (!overlaps) {
            return candidate;
        }
        int nextX = nodes.stream()
                .mapToInt(node -> node.canvasX() + NODE_WIDTH)
                .max()
                .orElse(80) + 140;
        return new MapDesignLibrary.QuestFlowNode(
                candidate.nodeId(),
                candidate.bodyText(),
                nextX,
                Math.max(120, candidate.canvasY()),
                candidate.choices()
        );
    }

    private void updateSectionAnchors() {
        sectionAnchors.clear();
        int nextX = 50;
        for (String sectionId : sectionTitles.keySet()) {
            List<MapDesignLibrary.QuestFlowNode> sectionNodes = nodes.stream()
                    .filter(node -> sectionId.equals(sectionId(node.nodeId())))
                    .toList();
            if (sectionNodes.isEmpty()) {
                sectionAnchors.put(sectionId, new Point(nextX, 65));
                nextX += 380;
                continue;
            }
            int minX = sectionNodes.stream().mapToInt(MapDesignLibrary.QuestFlowNode::canvasX).min().orElse(nextX);
            int minY = sectionNodes.stream().mapToInt(MapDesignLibrary.QuestFlowNode::canvasY).min().orElse(120);
            int maxX = sectionNodes.stream()
                    .mapToInt(node -> node.canvasX() + NODE_WIDTH)
                    .max()
                    .orElse(minX + NODE_WIDTH);
            sectionAnchors.put(sectionId, new Point(minX - 30, minY - 55));
            nextX = Math.max(nextX, maxX + 140);
        }
    }

    private Rectangle expanded(Rectangle bounds, int amount) {
        return new Rectangle(
                bounds.x - amount,
                bounds.y - amount,
                bounds.width + amount * 2,
                bounds.height + amount * 2
        );
    }

    private String key(String sectionId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        return sectionId + NODE_KEY_SEPARATOR + nodeId;
    }

    private String sectionId(String nodeKey) {
        if (nodeKey == null) {
            return "";
        }
        int separator = nodeKey.indexOf(NODE_KEY_SEPARATOR);
        return separator < 0 ? activeSectionId : nodeKey.substring(0, separator);
    }

    private String localId(String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            return "";
        }
        int separator = nodeKey.indexOf(NODE_KEY_SEPARATOR);
        return separator < 0 ? nodeKey : nodeKey.substring(separator + NODE_KEY_SEPARATOR.length());
    }

    private boolean isEntryNode(String nodeKey) {
        return nodeKey != null
                && nodeKey.equals(entryNodeIds.getOrDefault(sectionId(nodeKey), ""));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(panX, panY);
        g.scale(zoom, zoom);
        drawGrid(g);
        drawSectionBackgrounds(g);
        drawEdges(g);
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            drawNode(g, node);
        }
        if (!connectingNodeId.isBlank() && connectionPoint != null) {
            Rectangle port = choicePort(connectingNodeId, connectingChoiceIndex);
            if (port != null) {
                g.setColor(new Color(115, 190, 255));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(port.x + port.width / 2, port.y + port.height / 2,
                        connectionPoint.x, connectionPoint.y);
            }
        }
        drawMinimap(g);
        g.dispose();
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(38, 41, 49));
        Rectangle clip = g.getClipBounds();
        for (int x = -panX; x < clip.width / zoom - panX + 80; x += 40) {
            g.drawLine(x, -panY, x, (int) (clip.height / zoom - panY));
        }
        for (int y = -panY; y < clip.height / zoom - panY + 80; y += 40) {
            g.drawLine(-panX, y, (int) (clip.width / zoom - panX), y);
        }
    }

    private void drawSectionBackgrounds(Graphics2D g) {
        for (String sectionId : sectionTitles.keySet()) {
            List<MapDesignLibrary.QuestFlowNode> sectionNodes = nodes.stream()
                    .filter(node -> sectionId.equals(sectionId(node.nodeId())))
                    .toList();
            if (sectionNodes.isEmpty()) {
                Rectangle bounds = sectionBounds(sectionId);
                boolean active = sectionId.equals(activeSectionId);
                g.setColor(active ? new Color(48, 72, 97, 72) : new Color(31, 35, 43, 110));
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18);
                g.setColor(active ? new Color(118, 184, 242) : new Color(86, 98, 116));
                g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18);
                g.setColor(active ? new Color(190, 224, 255) : new Color(151, 161, 177));
                g.drawString(sectionTitles.getOrDefault(sectionId, sectionId)
                        + " (no nodes)", bounds.x + 12, bounds.y + 20);
                continue;
            }
            Rectangle bounds = sectionBounds(sectionId);
            boolean active = sectionId.equals(activeSectionId);
            g.setColor(active ? new Color(48, 72, 97, 72) : new Color(31, 35, 43, 110));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18);
            g.setColor(active ? new Color(118, 184, 242) : new Color(86, 98, 116));
            g.setStroke(new BasicStroke(active ? 2f : 1f));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18);
            g.setColor(active ? new Color(190, 224, 255) : new Color(151, 161, 177));
            g.drawString(sectionTitles.getOrDefault(sectionId, sectionId), bounds.x + 12, bounds.y + 20);
        }
    }

    private String sectionAt(Point point) {
        List<String> sections = new ArrayList<>(sectionTitles.keySet());
        for (int index = sections.size() - 1; index >= 0; index--) {
            String sectionId = sections.get(index);
            if (sectionBounds(sectionId).contains(point)) {
                return sectionId;
            }
        }
        return "";
    }

    private Rectangle sectionBounds(String sectionId) {
        List<MapDesignLibrary.QuestFlowNode> sectionNodes = nodes.stream()
                .filter(node -> sectionId.equals(sectionId(node.nodeId())))
                .toList();
        if (sectionNodes.isEmpty()) {
            Point anchor = sectionAnchors.getOrDefault(sectionId, new Point(50, 65));
            return new Rectangle(anchor.x, anchor.y, 300, 145);
        }
        int minX = sectionNodes.stream().mapToInt(MapDesignLibrary.QuestFlowNode::canvasX).min().orElse(0) - 30;
        int minY = sectionNodes.stream().mapToInt(MapDesignLibrary.QuestFlowNode::canvasY).min().orElse(0) - 55;
        int maxX = sectionNodes.stream()
                .mapToInt(node -> node.canvasX() + NODE_WIDTH).max().orElse(minX + NODE_WIDTH) + 30;
        int maxY = sectionNodes.stream()
                .mapToInt(node -> node.canvasY() + nodeHeight(node)).max().orElse(minY + 100) + 30;
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private void drawEdges(Graphics2D g) {
        g.setStroke(new BasicStroke(2f));
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            for (int choiceIndex = 0; choiceIndex < node.choices().size(); choiceIndex++) {
                MapDesignLibrary.QuestFlowChoice choice = node.choices().get(choiceIndex);
                MapDesignLibrary.QuestFlowNode target = nodes.stream()
                        .filter(candidate -> candidate.nodeId().equals(choice.targetNodeId()))
                        .findFirst().orElse(null);
                if (target == null) {
                    continue;
                }
                Rectangle port = choicePort(node.nodeId(), choiceIndex);
                Rectangle targetBounds = nodeBounds(target);
                int startX = port.x + port.width / 2;
                int startY = port.y + port.height / 2;
                int endX = targetBounds.x;
                int endY = targetBounds.y + HEADER_HEIGHT / 2;
                int bendX = startX + Math.max(30, (endX - startX) / 2);
                g.setColor(new Color(112, 150, 190));
                g.drawLine(startX, startY, bendX, startY);
                g.drawLine(bendX, startY, bendX, endY);
                g.drawLine(bendX, endY, endX, endY);
                g.drawLine(endX, endY, endX - 8, endY - 5);
                g.drawLine(endX, endY, endX - 8, endY + 5);
            }
        }
        drawProgressionEdges(g);
    }

    private void drawProgressionEdges(Graphics2D g) {
        List<String> orderedSections = List.copyOf(sectionTitles.keySet());
        g.setStroke(new BasicStroke(
                2.3f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                1f,
                new float[]{9f, 6f},
                0f
        ));
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            String sourceSection = sectionId(node.nodeId());
            for (int choiceIndex = 0; choiceIndex < node.choices().size(); choiceIndex++) {
                MapDesignLibrary.QuestFlowChoice choice = node.choices().get(choiceIndex);
                String targetSection = progressionTargetSection(
                        sourceSection,
                        choice.action(),
                        orderedSections
                );
                String targetNodeId = entryNodeIds.getOrDefault(targetSection, "");
                MapDesignLibrary.QuestFlowNode target = nodes.stream()
                        .filter(candidate -> candidate.nodeId().equals(targetNodeId))
                        .findFirst()
                        .orElse(null);
                Rectangle port = choicePort(node.nodeId(), choiceIndex);
                if (target == null || port == null) {
                    continue;
                }
                Rectangle targetBounds = nodeBounds(target);
                int startX = port.x + port.width / 2;
                int startY = port.y + port.height / 2;
                int endX = targetBounds.x;
                int endY = targetBounds.y + HEADER_HEIGHT / 2;
                int bendX = startX + Math.max(40, (endX - startX) / 2);
                g.setColor(choice.action() == MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST
                        ? new Color(103, 211, 151)
                        : new Color(226, 174, 91));
                g.drawLine(startX, startY, bendX, startY);
                g.drawLine(bendX, startY, bendX, endY);
                g.drawLine(bendX, endY, endX, endY);
                g.drawLine(endX, endY, endX - 9, endY - 6);
                g.drawLine(endX, endY, endX - 9, endY + 6);
            }
        }
        g.setStroke(new BasicStroke(2f));
    }

    private String progressionTargetSection(
            String sourceSection,
            MapDesignLibrary.QuestFlowAction action,
            List<String> orderedSections
    ) {
        if (action == MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST
                && "offer".equals(sourceSection)) {
            return orderedSections.stream()
                    .filter(section -> section.startsWith("stage:"))
                    .findFirst()
                    .orElse("");
        }
        if (action == MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE
                && sourceSection.startsWith("stage:")) {
            int current = orderedSections.indexOf(sourceSection);
            for (int index = current + 1; index < orderedSections.size(); index++) {
                if (orderedSections.get(index).startsWith("stage:")) {
                    return orderedSections.get(index);
                }
            }
        }
        if (action == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST
                && sourceSection.startsWith("stage:")) {
            return sectionTitles.containsKey("epilogue") ? "epilogue" : "";
        }
        return "";
    }

    private void drawNode(Graphics2D g, MapDesignLibrary.QuestFlowNode node) {
        Rectangle bounds = nodeBounds(node);
        boolean selected = node.nodeId().equals(selectedNodeId);
        boolean active = sectionId(node.nodeId()).equals(activeSectionId);
        g.setColor(selected ? new Color(57, 91, 130)
                : active ? new Color(49, 58, 72) : new Color(42, 47, 57));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
        g.setColor(isEntryNode(node.nodeId()) ? new Color(98, 210, 146)
                : selected ? new Color(130, 200, 255) : new Color(104, 119, 141));
        g.setStroke(new BasicStroke(selected ? 2.5f : 1.3f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
        g.setColor(Color.WHITE);
        g.drawString((isEntryNode(node.nodeId()) ? "ENTRY  " : "") + localId(node.nodeId()),
                bounds.x + 10, bounds.y + 20);
        g.setColor(new Color(219, 224, 234));
        drawWrapped(g, node.bodyText(), bounds.x + 10, bounds.y + 40, bounds.width - 20, 2);
        for (int index = 0; index < node.choices().size(); index++) {
            Rectangle port = choicePort(node.nodeId(), index);
            int y = bounds.y + MIN_NODE_HEIGHT + index * CHOICE_HEIGHT;
            g.setColor(new Color(35, 40, 49));
            g.fillRect(bounds.x + 7, y, bounds.width - 14, CHOICE_HEIGHT - 2);
            g.setColor(new Color(225, 229, 237));
            g.drawString(trim(node.choices().get(index).label(), 30), bounds.x + 13, y + 17);
            g.setColor(new Color(115, 190, 255));
            g.fillOval(port.x, port.y, port.width, port.height);
        }
    }

    private void drawMinimap(Graphics2D g) {
        if (nodes.isEmpty()) {
            return;
        }
        Rectangle clip = g.getClipBounds();
        int x = (int) (clip.getMaxX() - 180);
        int y = (int) (clip.getMinY() + 15);
        g.setColor(new Color(12, 14, 18, 210));
        g.fillRoundRect(x, y, 165, 115, 8, 8);
        int maxX = nodes.stream().mapToInt(node -> node.canvasX() + NODE_WIDTH).max().orElse(1);
        int maxY = nodes.stream().mapToInt(node -> node.canvasY() + nodeHeight(node)).max().orElse(1);
        double scale = Math.min(145.0 / Math.max(1, maxX), 95.0 / Math.max(1, maxY));
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            g.setColor(node.nodeId().equals(selectedNodeId) ? new Color(130, 200, 255) : new Color(94, 111, 136));
            g.fillRect(x + 10 + (int) (node.canvasX() * scale),
                    y + 10 + (int) (node.canvasY() * scale),
                    Math.max(4, (int) (NODE_WIDTH * scale)),
                    Math.max(3, (int) (nodeHeight(node) * scale)));
        }
    }

    private void drawWrapped(Graphics2D g, String text, int x, int y, int width, int lineLimit) {
        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : (text == null ? "" : text.replace('\n', ' ')).split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(candidate) > width) {
                g.drawString(line.toString(), x, y + lines * metrics.getHeight());
                line = new StringBuilder(word);
                if (++lines >= lineLimit) {
                    return;
                }
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty() && lines < lineLimit) {
            g.drawString(line.toString(), x, y + lines * metrics.getHeight());
        }
    }

    private MapDesignLibrary.QuestFlowNode nodeAt(Point point) {
        for (int index = nodes.size() - 1; index >= 0; index--) {
            MapDesignLibrary.QuestFlowNode node = nodes.get(index);
            if (nodeBounds(node).contains(point)) {
                return node;
            }
        }
        return null;
    }

    private ChoicePort choicePortAt(Point point) {
        for (MapDesignLibrary.QuestFlowNode node : nodes) {
            for (int index = 0; index < node.choices().size(); index++) {
                Rectangle bounds = choicePort(node.nodeId(), index);
                if (bounds != null && bounds.contains(point)) {
                    return new ChoicePort(node.nodeId(), index);
                }
            }
        }
        return null;
    }

    private Rectangle nodeBounds(MapDesignLibrary.QuestFlowNode node) {
        return new Rectangle(node.canvasX(), node.canvasY(), NODE_WIDTH, nodeHeight(node));
    }

    private int nodeHeight(MapDesignLibrary.QuestFlowNode node) {
        return MIN_NODE_HEIGHT + Math.max(1, node.choices().size()) * CHOICE_HEIGHT + 8;
    }

    private Rectangle choicePort(String nodeId, int choiceIndex) {
        MapDesignLibrary.QuestFlowNode node = nodes.stream()
                .filter(candidate -> candidate.nodeId().equals(nodeId)).findFirst().orElse(null);
        if (node == null || choiceIndex < 0 || choiceIndex >= node.choices().size()) {
            return null;
        }
        int y = node.canvasY() + MIN_NODE_HEIGHT + choiceIndex * CHOICE_HEIGHT + 6;
        return new Rectangle(node.canvasX() + NODE_WIDTH - 7, y, 14, 14);
    }

    private Point toWorld(Point screen) {
        return new Point(
                (int) Math.round((screen.x - panX) / zoom),
                (int) Math.round((screen.y - panY) / zoom)
        );
    }

    private String trim(String value, int length) {
        String safe = value == null ? "" : value;
        return safe.length() <= length ? safe : safe.substring(0, length - 3) + "...";
    }

    private record ChoicePort(String nodeId, int choiceIndex) {
    }

    record FlowSection(String id, String title, MapDesignLibrary.QuestFlow flow) {
    }
}

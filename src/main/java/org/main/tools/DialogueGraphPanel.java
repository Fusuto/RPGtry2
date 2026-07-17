package org.main.tools;

import org.main.content.MapDesignLibrary;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DialogueGraphPanel extends JPanel {
    private static final int NODE_WIDTH = 210;
    private static final int NODE_HEIGHT = 58;
    private static final int TERMINAL_WIDTH = 230;
    private static final int H_GAP = 96;
    private static final int V_GAP = 26;
    private static final int PADDING = 30;

    private final MapDesignLibrary.AuthoredDialogue dialogue;
    private final List<DialogueTerminal> terminals;

    DialogueGraphPanel(MapDesignLibrary.AuthoredDialogue dialogue) {
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
        Map<String, Rectangle> nodeRects = new LinkedHashMap<>();
        int x = PADDING + NODE_WIDTH + H_GAP;
        int y = PADDING + 50;
        for (int i = 0; i < dialogue.nodes().size(); i++) {
            MapDesignLibrary.AuthoredDialogueNode node = dialogue.nodes().get(i);
            nodeRects.put(node.nodeId(), new Rectangle(x, y + i * (NODE_HEIGHT + V_GAP), NODE_WIDTH, NODE_HEIGHT));
        }
        return nodeRects;
    }

    private Map<DialogueTerminal, Rectangle> layoutTerminals() {
        Map<DialogueTerminal, Rectangle> terminalRects = new LinkedHashMap<>();
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

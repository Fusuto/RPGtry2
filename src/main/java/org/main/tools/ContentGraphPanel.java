package org.main.tools;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

final class ContentGraphPanel extends JPanel {
    private static final int NODE_WIDTH = 210;
    private static final int NODE_HEIGHT = 42;
    private static final int H_GAP = 90;
    private static final int V_GAP = 18;
    private static final int PADDING = 28;

    private final ContentGraph graph;

    ContentGraphPanel(ContentGraph graph) {
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

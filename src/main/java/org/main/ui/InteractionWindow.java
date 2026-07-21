package org.main.ui;

import org.main.core.InteractionSystem;
import org.main.core.InteractionSystem.Interaction;
import org.main.core.InteractionSystem.InteractionModel;
import org.main.core.InteractionSystem.InteractionOption;
import org.main.engine.SoundSystem;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public final class InteractionWindow {
    private static final int WINDOW_MARGIN = 36;
    private static final int WINDOW_HEIGHT = 310;

    private static final int PORTRAIT_SIZE = 96;
    private static final int CONTENT_PADDING = 22;

    private static final int OPTION_HEIGHT = 32;
    private static final int OPTION_GAP = 7;
    private static final int SCROLL_STEP = 28;

    private final List<Rectangle> optionBounds = new ArrayList<>();
    private final List<Integer> optionIndexes = new ArrayList<>();
    private final SoundSystem soundSystem;
    private int bodyScrollOffset = 0;
    private int optionScrollOffset = 0;
    private Rectangle lastBodyClip = new Rectangle();
    private Rectangle lastOptionsClip = new Rectangle();
    private Rectangle lastWindowBounds = new Rectangle();
    private int lastBodyContentHeight = 0;
    private int lastOptionsContentHeight = 0;

    public InteractionWindow() {
        this(null);
    }

    public InteractionWindow(SoundSystem soundSystem) {
        this.soundSystem = soundSystem;
    }

    public void draw(Graphics2D g, Interaction interaction, int panelWidth, int panelHeight) {
        optionBounds.clear();
        optionIndexes.clear();

        if (interaction == null || interaction.isClosed()) {
            return;
        }

        InteractionModel model = interaction.getModel();

        if (model == null) {
            return;
        }

        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        drawDimBackground(g, panelWidth, panelHeight);

        Rectangle windowBounds = calculateWindowBounds(panelWidth, panelHeight, model);
        lastWindowBounds = new Rectangle(windowBounds);

        drawWindowBackground(g, windowBounds);
        drawPortraits(g, model, windowBounds);
        drawText(g, model, windowBounds);
        drawOptions(g, model, windowBounds);
        interaction.drawCustom(g, windowBounds);

        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    public boolean handleMousePressed(MouseEvent e, Interaction interaction) {
        if (interaction == null || interaction.isClosed()) {
            return false;
        }

        Point point = e.getPoint();
        if (interaction.handleMousePressed(e, lastWindowBounds)) {
            return true;
        }

        for (int i = 0; i < optionBounds.size(); i++) {
            Rectangle bounds = optionBounds.get(i);

            if (bounds.contains(point)) {
                playSelectionSound(interaction);
                interaction.selectOption(optionIndexes.get(i), SwingUtilities.isRightMouseButton(e));
                return true;
            }
        }

        return false;
    }

    public boolean handleMouseWheelMoved(MouseWheelEvent e, Interaction interaction) {
        if (interaction == null || interaction.isClosed()) {
            return false;
        }

        Point point = e.getPoint();
        int amount = e.getWheelRotation() * SCROLL_STEP;

        if (lastOptionsClip.contains(point) && canScrollOptions()) {
            optionScrollOffset = clampScroll(
                    optionScrollOffset + amount,
                    lastOptionsContentHeight,
                    lastOptionsClip.height
            );
            return true;
        }

        if (lastBodyClip.contains(point) && canScrollBody()) {
            bodyScrollOffset = clampScroll(
                    bodyScrollOffset + amount,
                    lastBodyContentHeight,
                    lastBodyClip.height
                );
                return true;
            }

            if (canScrollOptions()) {
                optionScrollOffset = clampScroll(
                        optionScrollOffset + amount,
                        lastOptionsContentHeight,
                        lastOptionsClip.height
                );
                return true;
            }

            if (canScrollBody()) {
                bodyScrollOffset = clampScroll(
                        bodyScrollOffset + amount,
                        lastBodyContentHeight,
                        lastBodyClip.height
                );
                return true;
            }

            return false;
        }

        public boolean handleMouseMoved(Point point, Interaction interaction) {
            if (interaction == null || interaction.isClosed()) {
                return false;
            }

            return interaction.handleMouseMoved(point, lastWindowBounds);
        }

        public boolean handleKeyPressed(KeyEvent e, Interaction interaction) {
            if (interaction == null || interaction.isClosed()) {
                return false;
            }

            if (interaction.handleKeyPressed(e)) {
                return true;
            }

            InteractionModel model = interaction.getModel();

            if (model == null) {
                return false;
            }

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE && model.isEscapeCloses()) {
                interaction.close();
                return true;
            }

            int optionIndex = getNumberKeyOptionIndex(e);

            if (optionIndex >= 0 && optionIndex < model.getOptions().size()) {
                playSelectionSound(interaction);
                interaction.selectOption(optionIndex);
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_ENTER && model.getOptions().size() == 1) {
                playSelectionSound(interaction);
                interaction.selectOption(0);
                return true;
            }

            if (handleScrollKey(e)) {
                return true;
            }

            return false;
        }

        private void playSelectionSound(Interaction interaction) {
            if (soundSystem != null && interaction != null) {
                soundSystem.playSound(interaction.getSelectionSoundPath());
            }
        }

        private boolean handleScrollKey(KeyEvent e) {
            int direction = switch (e.getKeyCode()) {
                case KeyEvent.VK_DOWN -> 1;
                case KeyEvent.VK_UP -> -1;
                case KeyEvent.VK_PAGE_DOWN -> 4;
                case KeyEvent.VK_PAGE_UP -> -4;
                default -> 0;
            };

            if (direction == 0) {
                return false;
            }

            if (canScrollOptions()) {
                optionScrollOffset = clampScroll(
                        optionScrollOffset + direction * SCROLL_STEP,
                        lastOptionsContentHeight,
                        lastOptionsClip.height
                );
                return true;
            }

            if (canScrollBody()) {
                bodyScrollOffset = clampScroll(
                        bodyScrollOffset + direction * SCROLL_STEP,
                        lastBodyContentHeight,
                        lastBodyClip.height
                );
                return true;
            }

            return false;
        }

        private int getNumberKeyOptionIndex(KeyEvent e) {
            return switch (e.getKeyCode()) {
                case KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> 0;
                case KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> 1;
                case KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> 2;
                case KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> 3;
                case KeyEvent.VK_5, KeyEvent.VK_NUMPAD5 -> 4;
                case KeyEvent.VK_6, KeyEvent.VK_NUMPAD6 -> 5;
                case KeyEvent.VK_7, KeyEvent.VK_NUMPAD7 -> 6;
                case KeyEvent.VK_8, KeyEvent.VK_NUMPAD8 -> 7;
                case KeyEvent.VK_9, KeyEvent.VK_NUMPAD9 -> 8;
                default -> -1;
            };
        }

        private Rectangle calculateWindowBounds(int panelWidth, int panelHeight, InteractionModel model) {
            int margin = model != null && model.isMenu()
                    ? 18
                    : WINDOW_MARGIN;
            int width = model != null && model.isMenu()
                    ? panelWidth - margin * 2
                    : Math.min(780, panelWidth - margin * 2);
            int height = model != null && model.isMenu()
                    ? panelHeight - margin * 2
                    : Math.min(WINDOW_HEIGHT, panelHeight - margin * 2);

            int x = (panelWidth - width) / 2;
            int y = model != null && model.isMenu()
                    ? margin
                    : panelHeight - height - margin;

            return new Rectangle(x, y, width, height);
        }

        private Rectangle calculateBodyClip(
                InteractionModel model,
                Rectangle windowBounds,
                int textX,
                int textY,
                int textWidth
        ) {
            int portraitBottom = windowBounds.y + CONTENT_PADDING + 34 + PORTRAIT_SIZE + 18;
            int optionsTop = calculateOptionsClip(windowBounds, textX, textWidth).y;
            int clipY = textY + 40;
            int clipBottom = Math.max(clipY + 24, optionsTop - 10);

            if (model.getLeftPortrait() != null || model.getRightPortrait() != null) {
                clipBottom = Math.max(clipBottom, portraitBottom);
            }

            return new Rectangle(textX, clipY, textWidth, Math.max(24, clipBottom - clipY));
        }

        private Rectangle calculateOptionsClip(Rectangle windowBounds, int x, int width) {
            int y = windowBounds.y + windowBounds.height - CONTENT_PADDING - 118;
            int height = 118;

            return new Rectangle(x, y, width, height);
        }

        private boolean canScrollBody() {
            return lastBodyContentHeight > lastBodyClip.height;
        }

        private boolean canScrollOptions() {
            return lastOptionsContentHeight > lastOptionsClip.height;
        }

        private int clampScroll(int offset, int contentHeight, int viewportHeight) {
            int maxOffset = Math.max(0, contentHeight - viewportHeight);
            return Math.max(0, Math.min(offset, maxOffset));
        }

        private void drawScrollBar(Graphics2D g, Rectangle clip, int contentHeight, int scrollOffset) {
            if (contentHeight <= clip.height || clip.height <= 0) {
                return;
            }

            int trackWidth = 5;
            int trackX = clip.x + clip.width - trackWidth - 2;
            int trackY = clip.y + 2;
            int trackHeight = clip.height - 4;
            int thumbHeight = Math.max(18, (int) Math.round((double) trackHeight * clip.height / contentHeight));
            int maxOffset = Math.max(1, contentHeight - clip.height);
            int maxThumbY = Math.max(trackY, trackY + trackHeight - thumbHeight);
            int thumbY = trackY + (int) Math.round((double) scrollOffset / maxOffset * (maxThumbY - trackY));

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g.setColor(new Color(10, 10, 14));
            g.fillRoundRect(trackX, trackY, trackWidth, trackHeight, 4, 4);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g.setColor(new Color(210, 210, 220));
            g.fillRoundRect(trackX, thumbY, trackWidth, thumbHeight, 4, 4);
            g.setComposite(oldComposite);
        }

        private void drawDimBackground(Graphics2D g, int panelWidth, int panelHeight) {
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, panelWidth, panelHeight);

            g.setComposite(oldComposite);
        }

        private void drawWindowBackground(Graphics2D g, Rectangle bounds) {
            Composite oldComposite = g.getComposite();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            g.setColor(new Color(18, 18, 24));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
            g.setColor(new Color(230, 230, 230));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);

            g.setComposite(oldComposite);
        }

        private void drawPortraits(Graphics2D g, InteractionModel model, Rectangle windowBounds) {
            int portraitY = windowBounds.y + CONTENT_PADDING + 34;

            Rectangle leftBounds = new Rectangle(
                    windowBounds.x + CONTENT_PADDING,
                    portraitY,
                    PORTRAIT_SIZE,
                    PORTRAIT_SIZE
            );

            Rectangle rightBounds = new Rectangle(
                    windowBounds.x + windowBounds.width - CONTENT_PADDING - PORTRAIT_SIZE,
                    portraitY,
                    PORTRAIT_SIZE,
                    PORTRAIT_SIZE
            );

            if (model.getLeftPortrait() != null) {
                drawPortrait(g, model.getLeftPortrait(), model.getLeftPortraitLabel(), leftBounds);
            }

            if (model.getRightPortrait() != null) {
                drawPortrait(g, model.getRightPortrait(), model.getRightPortraitLabel(), rightBounds);
            }
        }

        private void drawPortrait(
                Graphics2D g,
                BufferedImage portrait,
                String label,
                Rectangle bounds
        ) {
            g.setColor(new Color(35, 35, 42));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);

            g.setColor(new Color(180, 180, 180));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);

            if (portrait != null) {
                g.drawImage(
                        portrait,
                        bounds.x + 4,
                        bounds.y + 4,
                        bounds.width - 8,
                        bounds.height - 8,
                        null
                );
            }

            if (label != null && !label.isBlank()) {
                Font oldFont = g.getFont();
                g.setFont(oldFont.deriveFont(10f));

                FontMetrics metrics = g.getFontMetrics();
                int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
                int textY = bounds.y + bounds.height + 14;

                g.setColor(Color.WHITE);
                g.drawString(label, textX, textY);

                g.setFont(oldFont);
            }
        }

        private void drawText(Graphics2D g, InteractionModel model, Rectangle windowBounds) {
            int leftInset = model.getLeftPortrait() == null
                    ? CONTENT_PADDING
                    : CONTENT_PADDING + PORTRAIT_SIZE + 24;
            int rightInset = model.getRightPortrait() == null
                    ? CONTENT_PADDING
                    : CONTENT_PADDING + PORTRAIT_SIZE + 24;

            int textX = windowBounds.x + leftInset;
            int textY = windowBounds.y + CONTENT_PADDING;
            int textWidth = windowBounds.width - leftInset - rightInset;
            Rectangle bodyClip = calculateBodyClip(model, windowBounds, textX, textY, textWidth);
            lastBodyClip = bodyClip;

            Font oldFont = g.getFont();

            g.setColor(Color.WHITE);
            g.setFont(oldFont.deriveFont(Font.BOLD, 16f));
            g.drawString(model.getTitle(), textX, textY + 16);

            g.setFont(oldFont.deriveFont(14f));

            List<String> lines = wrapText(g, model.getBodyText(), textWidth);
            lastBodyContentHeight = lines.size() * 19;
            bodyScrollOffset = clampScroll(bodyScrollOffset, lastBodyContentHeight, bodyClip.height);

            Shape oldClip = g.getClip();
            g.setClip(bodyClip);

            int lineY = bodyClip.y + 14 - bodyScrollOffset;
            for (String line : lines) {
                g.drawString(line, textX, lineY);
                lineY += 19;
            }

            g.setClip(oldClip);
            drawScrollBar(g, bodyClip, lastBodyContentHeight, bodyScrollOffset);

            g.setFont(oldFont);
        }

        private void drawOptions(Graphics2D g, InteractionModel model, Rectangle windowBounds) {
            List<InteractionOption> options = model.getOptions();

            if (options.isEmpty()) {
                return;
            }

            int leftInset = model.getLeftPortrait() == null
                    ? CONTENT_PADDING
                    : CONTENT_PADDING + PORTRAIT_SIZE + 24;
            int rightInset = model.getRightPortrait() == null
                    ? CONTENT_PADDING
                    : CONTENT_PADDING + PORTRAIT_SIZE + 24;

            int textX = windowBounds.x + leftInset;
            int optionWidth = windowBounds.width - leftInset - rightInset;

            int totalOptionHeight = options.size() * OPTION_HEIGHT
                    + Math.max(0, options.size() - 1) * OPTION_GAP;

            Rectangle optionsClip = calculateOptionsClip(windowBounds, textX, optionWidth);
            lastOptionsClip = optionsClip;
            lastOptionsContentHeight = totalOptionHeight;
            optionScrollOffset = clampScroll(optionScrollOffset, lastOptionsContentHeight, optionsClip.height);

            Shape oldClip = g.getClip();
            g.setClip(optionsClip);

            int startY = optionsClip.y - optionScrollOffset;

            for (int i = 0; i < options.size(); i++) {
                InteractionOption option = options.get(i);

                Rectangle optionBounds = new Rectangle(
                        textX,
                        startY + i * (OPTION_HEIGHT + OPTION_GAP),
                        optionWidth,
                        OPTION_HEIGHT
                );

                if (optionBounds.intersects(optionsClip)) {
                    this.optionBounds.add(optionBounds);
                    this.optionIndexes.add(i);
                    drawOption(g, i, option, optionBounds);
                }
            }

            g.setClip(oldClip);
            drawScrollBar(g, optionsClip, lastOptionsContentHeight, optionScrollOffset);
        }

        private void drawOption(
                Graphics2D g,
                int index,
                InteractionOption option,
                Rectangle bounds
        ) {
            Composite oldComposite = g.getComposite();
            Stroke oldStroke = g.getStroke();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
            g.setColor(new Color(42, 42, 52));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.96f));
            g.setColor(new Color(210, 210, 210));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

            String label = (index + 1) + ". " + option.getLabel();

            FontMetrics metrics = g.getFontMetrics();

            int textX = bounds.x + 12;
            int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();

            g.setColor(Color.WHITE);
            g.drawString(trimTextToFit(g, label, bounds.width - 24), textX, textY);

            g.setStroke(oldStroke);
            g.setComposite(oldComposite);
        }

        private List<String> wrapText(Graphics2D g, String text, int maxWidth) {
            List<String> lines = new ArrayList<>();

            if (text == null || text.isBlank()) {
                return lines;
            }

            FontMetrics metrics = g.getFontMetrics();

            for (String paragraph : text.split("\\R")) {
                String[] words = paragraph.split("\\s+");
                StringBuilder currentLine = new StringBuilder();

                for (String word : words) {
                    if (word.isBlank()) {
                        continue;
                    }

                    String candidate;

                    if (currentLine.isEmpty()) {
                        candidate = word;
                    } else {
                        candidate = currentLine + " " + word;
                    }

                    if (metrics.stringWidth(candidate) <= maxWidth) {
                        currentLine = new StringBuilder(candidate);
                    } else {
                        if (!currentLine.isEmpty()) {
                            lines.add(currentLine.toString());
                        }

                        currentLine = new StringBuilder(word);
                    }
                }

                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
            }

            return lines;
        }

        private String trimTextToFit(Graphics2D g, String text, int maxWidth) {
            FontMetrics metrics = g.getFontMetrics();

            if (metrics.stringWidth(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "...";

            for (int i = text.length() - 1; i >= 0; i--) {
                String candidate = text.substring(0, i) + ellipsis;

                if (metrics.stringWidth(candidate) <= maxWidth) {
                    return candidate;
                }
            }

            return ellipsis;
        }
}

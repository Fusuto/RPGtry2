package org.main.core;

import org.main.content.DialogueLibrary;
import org.main.engine.MapEntity;
import org.main.engine.SoundSystem;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InteractionSystem {

    private InteractionSystem() {
    }

    public static Interaction prompt(
            String title,
            String bodyText,
            InteractionOption... options
    ) {
        InteractionModel model = new InteractionModel(
                title,
                bodyText,
                null,
                null,
                null,
                null,
                true,
                List.of(options)
        );

        return new Interaction(new StaticInteractionContent(model));
    }

    public static Interaction dialogue(
            String speakerName,
            String bodyText,
            BufferedImage leftPortrait,
            BufferedImage rightPortrait,
            InteractionOption... options
    ) {
        InteractionModel model = new InteractionModel(
                speakerName,
                bodyText,
                leftPortrait,
                rightPortrait,
                "Player",
                speakerName,
                true,
                List.of(options)
        );

        return new Interaction(new StaticInteractionContent(model));
    }

    public static Interaction conversation(Conversation conversation) {
        return new Interaction(new ConversationInteractionContent(conversation));
    }

    public static Interaction configMenu(SoundSystem soundSystem, Runnable exitAction) {
        return configMenu(soundSystem, null, exitAction, null);
    }

    public static Interaction configMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return new Interaction(new ConfigInteractionContent(soundSystem, gameState, exitAction, controlsAction));
    }

    public static Interaction settingsMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return new Interaction(new SettingsInteractionContent(soundSystem, gameState, exitAction, controlsAction));
    }

    public static Interaction volumeMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return new Interaction(new VolumeInteractionContent(soundSystem, gameState, exitAction, controlsAction));
    }

    public static Interaction controlsMenu(InputBindings inputBindings) {
        return new Interaction(new ControlsInteractionContent(inputBindings));
    }

    public static InteractionOption closeOption(String label) {
        return new InteractionOption(label, null, true);
    }

    public static InteractionOption option(String label, Runnable action) {
        return new InteractionOption(label, action, true);
    }

    public static InteractionOption stayOpenOption(String label, Runnable action) {
        return new InteractionOption(label, action, false);
    }

    public static InteractionOption stayOpenOption(
            String label,
            Runnable action,
            Runnable alternateAction
    ) {
        return new InteractionOption(label, action, alternateAction, false);
    }

    public static class Interaction {
        private final InteractionContent content;
        private boolean closed = false;

        private Interaction(InteractionContent content) {
            this.content = content;
        }

        public InteractionModel getModel() {
            return content.getModel();
        }

        public void selectOption(int optionIndex) {
            selectOption(optionIndex, false);
        }

        public void selectOption(int optionIndex, boolean alternateAction) {
            if (closed) {
                return;
            }

            content.selectOption(optionIndex, this, alternateAction);
        }

        private boolean handleKeyPressed(KeyEvent e) {
            return content.handleKeyPressed(e, this);
        }

        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private interface InteractionContent {
        InteractionModel getModel();

        void selectOption(int optionIndex, Interaction interaction, boolean alternateAction);

        default boolean handleKeyPressed(KeyEvent e, Interaction interaction) {
            return false;
        }
    }

    private static class StaticInteractionContent implements InteractionContent {
        private final InteractionModel model;

        private StaticInteractionContent(InteractionModel model) {
            this.model = model;
        }

        @Override
        public InteractionModel getModel() {
            return model;
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            if (optionIndex < 0 || optionIndex >= model.getOptions().size()) {
                return;
            }

            InteractionOption option = model.getOptions().get(optionIndex);
            option.run(alternateAction);

            if (option.shouldCloseAfterSelection()) {
                interaction.close();
            }
        }
    }

    private static class ConversationInteractionContent implements InteractionContent {
        private final Conversation conversation;

        private ConversationInteractionContent(Conversation conversation) {
            this.conversation = conversation;
        }

        @Override
        public InteractionModel getModel() {
            ConversationNode node = conversation.getCurrentNode();

            if (node == null) {
                return new InteractionModel(
                        "",
                        "",
                        null,
                        null,
                        null,
                        null,
                        true,
                        List.of(closeOption("Close"))
                );
            }

            List<InteractionOption> options = new ArrayList<>();

            for (ConversationChoice choice : node.getChoices()) {
                options.add(new InteractionOption(choice.getLabel(), null, false));
            }

            return new InteractionModel(
                    node.getSpeakerName(),
                    node.getText(),
                    node.getLeftPortrait(),
                    node.getRightPortrait(),
                    node.getLeftPortraitLabel(),
                    node.getRightPortraitLabel(),
                    true,
                    options
            );
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            conversation.choose(optionIndex, interaction);
        }
    }

    public static class InteractionModel {
        private final String title;
        private final String bodyText;
        private final BufferedImage leftPortrait;
        private final BufferedImage rightPortrait;
        private final String leftPortraitLabel;
        private final String rightPortraitLabel;
        private final boolean escapeCloses;
        private final boolean menu;
        private final List<InteractionOption> options;

        public InteractionModel(
                String title,
                String bodyText,
                BufferedImage leftPortrait,
                BufferedImage rightPortrait,
                String leftPortraitLabel,
                String rightPortraitLabel,
                boolean escapeCloses,
                List<InteractionOption> options
        ) {
            this(
                    title,
                    bodyText,
                    leftPortrait,
                    rightPortrait,
                    leftPortraitLabel,
                    rightPortraitLabel,
                    escapeCloses,
                    false,
                    options
            );
        }

        public InteractionModel(
                String title,
                String bodyText,
                BufferedImage leftPortrait,
                BufferedImage rightPortrait,
                String leftPortraitLabel,
                String rightPortraitLabel,
                boolean escapeCloses,
                boolean menu,
                List<InteractionOption> options
        ) {
            this.title = title == null ? "" : title;
            this.bodyText = bodyText == null ? "" : bodyText;
            this.leftPortrait = leftPortrait;
            this.rightPortrait = rightPortrait;
            this.leftPortraitLabel = leftPortraitLabel;
            this.rightPortraitLabel = rightPortraitLabel;
            this.escapeCloses = escapeCloses;
            this.menu = menu;
            this.options = options == null ? List.of() : List.copyOf(options);
        }

        public String getTitle() {
            return title;
        }

        public String getBodyText() {
            return bodyText;
        }

        public BufferedImage getLeftPortrait() {
            return leftPortrait;
        }

        public BufferedImage getRightPortrait() {
            return rightPortrait;
        }

        public String getLeftPortraitLabel() {
            return leftPortraitLabel;
        }

        public String getRightPortraitLabel() {
            return rightPortraitLabel;
        }

        public boolean isEscapeCloses() {
            return escapeCloses;
        }

        public boolean isMenu() {
            return menu;
        }

        public List<InteractionOption> getOptions() {
            return options;
        }
    }

    public static class InteractionOption {
        private final String label;
        private final Runnable action;
        private final Runnable alternateAction;
        private final boolean closeAfterSelection;

        public InteractionOption(String label, Runnable action, boolean closeAfterSelection) {
            this(label, action, null, closeAfterSelection);
        }

        public InteractionOption(
                String label,
                Runnable action,
                Runnable alternateAction,
                boolean closeAfterSelection
        ) {
            this.label = label == null ? "" : label;
            this.action = action;
            this.alternateAction = alternateAction;
            this.closeAfterSelection = closeAfterSelection;
        }

        public String getLabel() {
            return label;
        }

        public void run() {
            run(false);
        }

        public void run(boolean alternateActionRequested) {
            if (alternateActionRequested && alternateAction != null) {
                alternateAction.run();
                return;
            }

            if (action != null) {
                action.run();
            }
        }

        public boolean shouldCloseAfterSelection() {
            return closeAfterSelection;
        }
    }

    public static class Conversation {
        private final Map<String, ConversationNode> nodes = new HashMap<>();

        private String currentNodeId;

        public Conversation(String startingNodeId) {
            this.currentNodeId = startingNodeId;
        }

        public Conversation addNode(ConversationNode node) {
            if (node != null) {
                nodes.put(node.getId(), node);
            }

            return this;
        }

        public ConversationNode getCurrentNode() {
            return nodes.get(currentNodeId);
        }

        public void choose(int choiceIndex, Interaction interaction) {
            ConversationNode currentNode = getCurrentNode();

            if (currentNode == null) {
                interaction.close();
                return;
            }

            if (choiceIndex < 0 || choiceIndex >= currentNode.getChoices().size()) {
                return;
            }

            ConversationChoice choice = currentNode.getChoices().get(choiceIndex);
            choice.run();

            String nextNodeId = choice.getNextNodeId();

            if (nextNodeId == null || nextNodeId.isBlank()) {
                interaction.close();
                return;
            }

            if (!nodes.containsKey(nextNodeId)) {
                System.out.println("Conversation node not found: " + nextNodeId);
                interaction.close();
                return;
            }

            currentNodeId = nextNodeId;
        }
    }

    public static class ConversationNode {
        private final String id;
        private final String speakerName;
        private final String text;
        private final BufferedImage leftPortrait;
        private final BufferedImage rightPortrait;
        private final String leftPortraitLabel;
        private final String rightPortraitLabel;
        private final List<ConversationChoice> choices;

        public ConversationNode(
                String id,
                String speakerName,
                String text,
                BufferedImage leftPortrait,
                BufferedImage rightPortrait,
                String leftPortraitLabel,
                String rightPortraitLabel,
                ConversationChoice... choices
        ) {
            this.id = id;
            this.speakerName = speakerName == null ? "" : speakerName;
            this.text = text == null ? "" : text;
            this.leftPortrait = leftPortrait;
            this.rightPortrait = rightPortrait;
            this.leftPortraitLabel = leftPortraitLabel;
            this.rightPortraitLabel = rightPortraitLabel;
            this.choices = choices == null ? List.of() : List.of(choices);
        }

        public String getId() {
            return id;
        }

        public String getSpeakerName() {
            return speakerName;
        }

        public String getText() {
            return text;
        }

        public BufferedImage getLeftPortrait() {
            return leftPortrait;
        }

        public BufferedImage getRightPortrait() {
            return rightPortrait;
        }

        public String getLeftPortraitLabel() {
            return leftPortraitLabel;
        }

        public String getRightPortraitLabel() {
            return rightPortraitLabel;
        }

        public List<ConversationChoice> getChoices() {
            return choices;
        }
    }

    public static class ConversationChoice {
        private final String label;
        private final String nextNodeId;
        private final Runnable action;

        public ConversationChoice(String label, String nextNodeId) {
            this(label, nextNodeId, null);
        }

        public ConversationChoice(String label, String nextNodeId, Runnable action) {
            this.label = label == null ? "" : label;
            this.nextNodeId = nextNodeId;
            this.action = action;
        }

        public String getLabel() {
            return label;
        }

        public String getNextNodeId() {
            return nextNodeId;
        }

        public void run() {
            if (action != null) {
                action.run();
            }
        }
    }

    public static class InteractionWindow {
        private static final int WINDOW_MARGIN = 36;
        private static final int WINDOW_HEIGHT = 310;

        private static final int PORTRAIT_SIZE = 96;
        private static final int CONTENT_PADDING = 22;

        private static final int OPTION_HEIGHT = 32;
        private static final int OPTION_GAP = 7;
        private static final int SCROLL_STEP = 28;

        private final List<Rectangle> optionBounds = new ArrayList<>();
        private final List<Integer> optionIndexes = new ArrayList<>();
        private int bodyScrollOffset = 0;
        private int optionScrollOffset = 0;
        private Rectangle lastBodyClip = new Rectangle();
        private Rectangle lastOptionsClip = new Rectangle();
        private int lastBodyContentHeight = 0;
        private int lastOptionsContentHeight = 0;

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

            drawWindowBackground(g, windowBounds);
            drawPortraits(g, model, windowBounds);
            drawText(g, model, windowBounds);
            drawOptions(g, model, windowBounds);

            if (oldInterpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            }
        }

        public boolean handleMousePressed(MouseEvent e, Interaction interaction) {
            if (interaction == null || interaction.isClosed()) {
                return false;
            }

            Point point = e.getPoint();

            for (int i = 0; i < optionBounds.size(); i++) {
                Rectangle bounds = optionBounds.get(i);

                if (bounds.contains(point)) {
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
                interaction.selectOption(optionIndex);
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_ENTER && model.getOptions().size() == 1) {
                interaction.selectOption(0);
                return true;
            }

            if (handleScrollKey(e)) {
                return true;
            }

            return false;
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
    @FunctionalInterface
    public interface InteractionFactory {
        Interaction create(InteractionContext context);
    }

    public static class InteractionContext {
        private final GameState gameState;
        private final MapEntity entity;

        public InteractionContext(GameState gameState, MapEntity entity) {
            this.gameState = gameState;
            this.entity = entity;
        }

        public GameState getGameState() {
            return gameState;
        }

        public MapEntity getEntity() {
            return entity;
        }
    }

    public static class InteractionRegistry {
        private final Map<String, InteractionFactory> factories = new HashMap<>();

        public InteractionRegistry register(String interactionId, InteractionFactory factory) {
            if (interactionId == null || interactionId.isBlank() || factory == null) {
                return this;
            }

            factories.put(interactionId, factory);
            return this;
        }

        public boolean has(String interactionId) {
            return interactionId != null && factories.containsKey(interactionId);
        }

        public Interaction create(String interactionId, GameState gameState, MapEntity entity) {
            if (interactionId == null || interactionId.isBlank()) {
                return null;
            }

            InteractionFactory factory = factories.get(interactionId);

            if (factory == null) {
                System.out.println("No interaction registered for id: " + interactionId);
                return null;
            }

            return factory.create(new InteractionContext(gameState, entity));
        }

        public static InteractionRegistry createDefault() {
            InteractionRegistry registry = new InteractionRegistry();

            for (DialogueLibrary dialogue : DialogueLibrary.values()) {
                registry.register(dialogue.getInteractionId(), dialogue::create);
            }

            registry.register("generated_dungeon_gate", context -> prompt(
                    "Dungeon Gate",
                    "Go one floor deeper into a newly generated dungeon?",
                    option("Enter", () -> {
                        GeneratedDungeon generatedDungeon = new DungeonGenerator().generate();
                        context.getGameState().changeDungeon(generatedDungeon);
                    }),
                    closeOption("Stay")
            ));

            return registry;
        }
    }

    private abstract static class SettingsMenuContent implements InteractionContent {
        private final SoundSystem soundSystem;
        private final GameState gameState;
        private final Runnable exitAction;
        private final Runnable controlsAction;

        private SettingsMenuContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction
        ) {
            this.soundSystem = soundSystem;
            this.gameState = gameState;
            this.exitAction = exitAction;
            this.controlsAction = controlsAction;
        }

        protected SoundSystem soundSystem() {
            return soundSystem;
        }

        protected GameState gameState() {
            return gameState;
        }

        protected Runnable controlsAction() {
            return controlsAction;
        }

        protected Runnable exitAction() {
            return exitAction;
        }

        protected InteractionOption backToConfigOption() {
            return option("Back", () -> openInteraction(configMenu(soundSystem, gameState, exitAction, controlsAction)));
        }

        protected InteractionOption settingsOption() {
            return option("Settings", () -> openInteraction(settingsMenu(soundSystem, gameState, exitAction, controlsAction)));
        }

        protected InteractionOption volumeOption() {
            return option("Volume", () -> openInteraction(volumeMenu(soundSystem, gameState, exitAction, controlsAction)));
        }

        protected InteractionOption exitGameOption() {
            return option("Exit Game", () -> {
                if (soundSystem != null) {
                    soundSystem.stopAll();
                }

                if (exitAction != null) {
                    exitAction.run();
                }
            });
        }

        protected void openInteraction(Interaction interaction) {
            if (gameState != null) {
                gameState.openInteraction(interaction);
            }
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            InteractionModel model = getModel();

            if (optionIndex < 0 || optionIndex >= model.getOptions().size()) {
                return;
            }

            InteractionOption option = model.getOptions().get(optionIndex);
            option.run(alternateAction);

            if (option.shouldCloseAfterSelection()) {
                interaction.close();
            }
        }
    }

    private static class ConfigInteractionContent extends SettingsMenuContent {
        private ConfigInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction);
        }

        @Override
        public InteractionModel getModel() {
            return new InteractionModel(
                    "Escape",
                    "Pause menu",
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(
                            settingsOption(),
                            exitGameOption(),
                            closeOption("Close")
                    )
            );
        }
    }

    private static class SettingsInteractionContent extends SettingsMenuContent {
        private SettingsInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction);
        }

        @Override
        public InteractionModel getModel() {
            return new InteractionModel(
                    "Settings",
                    "Choose a settings category.",
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(
                            stayOpenOption(miniMapLabel(), this::toggleMiniMap),
                            option("Controls", controlsAction()),
                            volumeOption(),
                            backToConfigOption(),
                            closeOption("Close")
                    )
            );
        }

        private String miniMapLabel() {
            if (gameState() == null) {
                return "Minimap [OFF]";
            }

            return switch (gameState().getMiniMapMode()) {
                case OFF -> "Minimap [OFF]";
                case DISCOVERED -> "Minimap [DISCOVERED]";
                case DEBUG -> "Minimap [DEBUG]";
            };
        }

        private void toggleMiniMap() {
            if (gameState() != null) {
                gameState().cycleMiniMapMode();
            }
        }
    }

    private static class VolumeInteractionContent extends SettingsMenuContent {
        private static final double VOLUME_STEP = 0.10;

        private VolumeInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction);
        }

        @Override
        public InteractionModel getModel() {
            int soundEffectPercent = toPercent(soundSystem() != null ? soundSystem().getSoundEffectVolume() : 1.0);
            int ambiencePercent = toPercent(soundSystem() != null ? soundSystem().getAmbienceVolume() : 1.0);
            int musicPercent = toPercent(soundSystem() != null ? soundSystem().getMusicVolume() : 1.0);

            return new InteractionModel(
                    "Volume",
                    "Left click a volume bar to raise it. Right click to lower it.",
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(
                            stayOpenOption(
                                    volumeLabel("Sound Effects", soundEffectPercent),
                                    () -> adjustSoundEffects(VOLUME_STEP),
                                    () -> adjustSoundEffects(-VOLUME_STEP)
                            ),
                            stayOpenOption(
                                    volumeLabel("Overworld", ambiencePercent),
                                    () -> adjustAmbience(VOLUME_STEP),
                                    () -> adjustAmbience(-VOLUME_STEP)
                            ),
                            stayOpenOption(
                                    volumeLabel("Combat Music", musicPercent),
                                    () -> adjustMusic(VOLUME_STEP),
                                    () -> adjustMusic(-VOLUME_STEP)
                            ),
                            backToSettingsOption(),
                            closeOption("Close")
                    )
            );
        }

        private InteractionOption backToSettingsOption() {
            return option("Back", () -> openInteraction(settingsMenu(
                    soundSystem(),
                    gameState(),
                    exitAction(),
                    controlsAction()
            )));
        }

        private void adjustSoundEffects(double amount) {
            if (soundSystem() != null) {
                soundSystem().adjustSoundEffectVolume(amount);
            }
        }

        private void adjustAmbience(double amount) {
            if (soundSystem() != null) {
                soundSystem().adjustAmbienceVolume(amount);
            }
        }

        private void adjustMusic(double amount) {
            if (soundSystem() != null) {
                soundSystem().adjustMusicVolume(amount);
            }
        }

        private int toPercent(double volume) {
            return (int) Math.round(volume * 100.0);
        }

        private String volumeLabel(String label, int percent) {
            int filledBlocks = Math.max(0, Math.min(10, percent / 10));
            StringBuilder bar = new StringBuilder("[");

            for (int i = 0; i < 10; i++) {
                bar.append(i < filledBlocks ? "#" : ".");
            }

            bar.append("] ");
            bar.append(percent);
            bar.append("%");

            return label + " " + bar;
        }
    }

    private static class ControlsInteractionContent implements InteractionContent {
        private final InputBindings inputBindings;
        private InputBindings.Action pendingAction;
        private String statusMessage = "Choose an action to reassign, then press the next key you want to use.";

        private ControlsInteractionContent(InputBindings inputBindings) {
            this.inputBindings = inputBindings == null ? new InputBindings() : inputBindings;
        }

        @Override
        public InteractionModel getModel() {
            List<InteractionOption> options = new ArrayList<>();

            for (InputBindings.Action action : InputBindings.Action.values()) {
                options.add(stayOpenOption(controlLabel(action), () -> pendingAction = action));
            }

            options.add(closeOption("Close"));

            return new InteractionModel(
                    "Controls",
                    statusMessage + "\n\n" + controlsSummary(),
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    options
            );
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            InteractionModel model = getModel();

            if (optionIndex < 0 || optionIndex >= model.getOptions().size()) {
                return;
            }

            InteractionOption option = model.getOptions().get(optionIndex);
            option.run(alternateAction);

            if (pendingAction != null && !option.shouldCloseAfterSelection()) {
                statusMessage = "Press a key for " + pendingAction.getLabel() + ".";
            }

            if (option.shouldCloseAfterSelection()) {
                interaction.close();
            }
        }

        @Override
        public boolean handleKeyPressed(KeyEvent e, Interaction interaction) {
            if (pendingAction == null) {
                return false;
            }

            int keyCode = e.getKeyCode();

            if (keyCode == KeyEvent.VK_ESCAPE) {
                pendingAction = null;
                statusMessage = "Assignment cancelled.";
                return true;
            }

            inputBindings.assignKey(pendingAction, keyCode);
            statusMessage = pendingAction.getLabel()
                    + " assigned to "
                    + KeyEvent.getKeyText(keyCode)
                    + ".";
            pendingAction = null;
            return true;
        }

        private String controlLabel(InputBindings.Action action) {
            String keyText = inputBindings.getKeyCode(action) == KeyEvent.VK_UNDEFINED
                    ? "Unassigned"
                    : inputBindings.getKeyText(action);

            return action.getLabel() + " [ " + keyText + " ] [Assign new button]";
        }

        private String controlsSummary() {
            StringBuilder builder = new StringBuilder();

            for (InputBindings.Action action : InputBindings.Action.values()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }

                builder
                        .append(action.getLabel())
                        .append(": ")
                        .append(inputBindings.getKeyCode(action) == KeyEvent.VK_UNDEFINED
                                ? "Unassigned"
                                : inputBindings.getKeyText(action));
            }

            return builder.toString();
        }
    }
}

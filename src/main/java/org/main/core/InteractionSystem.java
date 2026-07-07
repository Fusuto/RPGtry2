package org.main.core;

import org.main.engine.MapEntity;

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
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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

    public static InteractionOption closeOption(String label) {
        return new InteractionOption(label, null, true);
    }

    public static InteractionOption option(String label, Runnable action) {
        return new InteractionOption(label, action, true);
    }

    public static InteractionOption stayOpenOption(String label, Runnable action) {
        return new InteractionOption(label, action, false);
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
            if (closed) {
                return;
            }

            content.selectOption(optionIndex, this);
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

        void selectOption(int optionIndex, Interaction interaction);
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
        public void selectOption(int optionIndex, Interaction interaction) {
            if (optionIndex < 0 || optionIndex >= model.getOptions().size()) {
                return;
            }

            InteractionOption option = model.getOptions().get(optionIndex);
            option.run();

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
        public void selectOption(int optionIndex, Interaction interaction) {
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
            this.title = title == null ? "" : title;
            this.bodyText = bodyText == null ? "" : bodyText;
            this.leftPortrait = leftPortrait;
            this.rightPortrait = rightPortrait;
            this.leftPortraitLabel = leftPortraitLabel;
            this.rightPortraitLabel = rightPortraitLabel;
            this.escapeCloses = escapeCloses;
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

        public List<InteractionOption> getOptions() {
            return options;
        }
    }

    public static class InteractionOption {
        private final String label;
        private final Runnable action;
        private final boolean closeAfterSelection;

        public InteractionOption(String label, Runnable action, boolean closeAfterSelection) {
            this.label = label == null ? "" : label;
            this.action = action;
            this.closeAfterSelection = closeAfterSelection;
        }

        public String getLabel() {
            return label;
        }

        public void run() {
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

        private final List<Rectangle> optionBounds = new ArrayList<>();

        public void draw(Graphics2D g, Interaction interaction, int panelWidth, int panelHeight) {
            optionBounds.clear();

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

            Rectangle windowBounds = calculateWindowBounds(panelWidth, panelHeight);

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
                    interaction.selectOption(i);
                    return true;
                }
            }

            return false;
        }

        public boolean handleKeyPressed(KeyEvent e, Interaction interaction) {
            if (interaction == null || interaction.isClosed()) {
                return false;
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

        private Rectangle calculateWindowBounds(int panelWidth, int panelHeight) {
            int width = Math.min(780, panelWidth - WINDOW_MARGIN * 2);
            int height = Math.min(WINDOW_HEIGHT, panelHeight - WINDOW_MARGIN * 2);

            int x = (panelWidth - width) / 2;
            int y = panelHeight - height - WINDOW_MARGIN;

            return new Rectangle(x, y, width, height);
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

            drawPortrait(g, model.getLeftPortrait(), model.getLeftPortraitLabel(), leftBounds);
            drawPortrait(g, model.getRightPortrait(), model.getRightPortraitLabel(), rightBounds);
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
            } else {
                drawFallbackPortrait(g, label, bounds);
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

        private void drawFallbackPortrait(Graphics2D g, String label, Rectangle bounds) {
            String text = "?";

            if (label != null && !label.isBlank()) {
                text = label.substring(0, 1).toUpperCase();
            }

            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 34f));

            FontMetrics metrics = g.getFontMetrics();

            int textX = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
            int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();

            g.setColor(new Color(90, 90, 110));
            g.fillRoundRect(bounds.x + 4, bounds.y + 4, bounds.width - 8, bounds.height - 8, 8, 8);

            g.setColor(Color.WHITE);
            g.drawString(text, textX, textY);

            g.setFont(oldFont);
        }

        private void drawText(Graphics2D g, InteractionModel model, Rectangle windowBounds) {
            int textX = windowBounds.x + CONTENT_PADDING + PORTRAIT_SIZE + 24;
            int textY = windowBounds.y + CONTENT_PADDING;
            int textWidth = windowBounds.width - (CONTENT_PADDING + PORTRAIT_SIZE + 24) * 2;

            Font oldFont = g.getFont();

            g.setColor(Color.WHITE);
            g.setFont(oldFont.deriveFont(Font.BOLD, 16f));
            g.drawString(model.getTitle(), textX, textY + 16);

            g.setFont(oldFont.deriveFont(14f));

            List<String> lines = wrapText(g, model.getBodyText(), textWidth);

            int lineY = textY + 48;

            for (String line : lines) {
                if (lineY > windowBounds.y + windowBounds.height - 115) {
                    g.drawString("...", textX, lineY);
                    break;
                }

                g.drawString(line, textX, lineY);
                lineY += 19;
            }

            g.setFont(oldFont);
        }

        private void drawOptions(Graphics2D g, InteractionModel model, Rectangle windowBounds) {
            List<InteractionOption> options = model.getOptions();

            if (options.isEmpty()) {
                return;
            }

            int textX = windowBounds.x + CONTENT_PADDING + PORTRAIT_SIZE + 24;
            int optionWidth = windowBounds.width - (CONTENT_PADDING + PORTRAIT_SIZE + 24) * 2;

            int totalOptionHeight = options.size() * OPTION_HEIGHT
                    + Math.max(0, options.size() - 1) * OPTION_GAP;

            int startY = windowBounds.y + windowBounds.height - CONTENT_PADDING - totalOptionHeight;

            for (int i = 0; i < options.size(); i++) {
                InteractionOption option = options.get(i);

                Rectangle optionBounds = new Rectangle(
                        textX,
                        startY + i * (OPTION_HEIGHT + OPTION_GAP),
                        optionWidth,
                        OPTION_HEIGHT
                );

                this.optionBounds.add(optionBounds);

                drawOption(g, i, option, optionBounds);
            }
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

            String[] words = text.split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
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

            registry.register("old_guard_intro", InteractionRegistry::createOldGuardIntro);
            registry.register("merchant_basic", InteractionRegistry::createMerchantBasic);
            registry.register("chest_basic", InteractionRegistry::createChestBasic);
            registry.register("dungeon_exit", InteractionRegistry::createDungeonExitPrompt);

            return registry;
        }

        private static Interaction createOldGuardIntro(InteractionContext context) {
            MapEntity entity = context.getEntity();

            String npcName = entity != null ? entity.getName() : "Old Guard";

            Conversation conversation =
                    new Conversation("start")
                            .addNode(new ConversationNode(
                                    "start",
                                    npcName,
                                    "You look new here. Are you heading deeper into the dungeon?",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new ConversationChoice("Who are you?", "who_are_you"),
                                    new ConversationChoice("What is below?", "below"),
                                    new ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new ConversationNode(
                                    "who_are_you",
                                    npcName,
                                    "I used to guard the lower floors. Now I mostly warn fools away from them.",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new ConversationChoice("What is below?", "below"),
                                    new ConversationChoice("Goodbye.", null)
                            ))
                            .addNode(new ConversationNode(
                                    "below",
                                    npcName,
                                    "Old things. Hungry things. If you go down there, keep your weapon close.",
                                    null,
                                    entity != null ? entity.getStaticImage() : null,
                                    "Player",
                                    npcName,
                                    new ConversationChoice("I understand.", null)
                            ));

            return conversation(conversation);
        }

        private static Interaction createMerchantBasic(InteractionContext context) {
            MapEntity entity = context.getEntity();

            String merchantName = entity != null ? entity.getName() : "Merchant";

            return prompt(
                    merchantName,
                    "Looking to buy or sell?",
                    option("Trade", () -> {
                        context.getGameState().openShop(
                                ShopSystem.createBasicMerchantShop(merchantName)
                        );
                    }),
                    closeOption("Leave")
            );
        }

        private static Interaction createChestBasic(InteractionContext context) {
            MapEntity entity = context.getEntity();

            String chestName = entity != null ? entity.getName() : "Chest";

            return prompt(
                    chestName,
                    "Open the chest?",
                    option("Open", () -> {
                        System.out.println("Opened " + chestName + ".");
                        // Later: add loot here, then maybe remove or mark chest opened.
                    }),
                    closeOption("Leave")
            );
        }

        private static Interaction createDungeonExitPrompt(InteractionContext context) {
            return prompt(
                    "Dungeon Exit",
                    "Leave the dungeon?",
                    option("Yes", () -> {
                        System.out.println("Leaving dungeon...");
                        // Later: change area / load town / return to menu.
                    }),
                    closeOption("No")
            );
        }


    }
}
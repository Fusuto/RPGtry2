package org.main.core;

import org.main.content.MapDesignLibrary;
import org.main.engine.MapEntity;
import org.main.engine.SoundSystem;
import org.main.monsters.Monster;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class InteractionSystem {
    public static final String GENERATED_DUNGEON_GATE_ID = "generated_dungeon_gate";
    public static final List<EditorInteractionDefinition> EDITOR_INTERACTIONS = List.of(
            new EditorInteractionDefinition("Chest Prompt", "chest_basic"),
            new EditorInteractionDefinition("Dungeon Exit", "dungeon_exit")
    );
    private static final Logger LOGGER = Logger.getLogger(InteractionSystem.class.getName());

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

    private static Interaction handledWithoutOverlay() {
        return prompt("", "", closeOption("Close")).withoutOverlay();
    }

    public static Interaction configMenu(SoundSystem soundSystem, Runnable exitAction) {
        return configMenu(soundSystem, null, exitAction, null, null, null);
    }

    public static Interaction configMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return configMenu(soundSystem, gameState, exitAction, controlsAction, null, null);
    }

    public static Interaction configMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction
    ) {
        return new Interaction(new ConfigInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction))
                .pauseGameplay();
    }

    public static Interaction settingsMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return settingsMenu(soundSystem, gameState, exitAction, controlsAction, null, null);
    }

    private static Interaction settingsMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction
    ) {
        return new Interaction(new SettingsInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction))
                .pauseGameplay();
    }

    public static Interaction volumeMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction
    ) {
        return volumeMenu(soundSystem, gameState, exitAction, controlsAction, null, null);
    }

    private static Interaction volumeMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction
    ) {
        return new Interaction(new VolumeInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction))
                .pauseGameplay();
    }

    private static Interaction debugMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction
    ) {
        return new Interaction(new DebugInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction))
                .pauseGameplay();
    }

    private static Interaction debugItemCategoryMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction
    ) {
        return new Interaction(new DebugItemCategoryInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction))
                .pauseGameplay();
    }

    private static Interaction debugItemMenu(
            SoundSystem soundSystem,
            GameState gameState,
            Runnable exitAction,
            Runnable controlsAction,
            Runnable saveAction,
            Runnable loadAction,
            DebugItemCategory category
    ) {
        return new Interaction(new DebugItemInteractionContent(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction, category))
                .pauseGameplay();
    }

    public static Interaction controlsMenu(InputBindings inputBindings) {
        return new Interaction(new ControlsInteractionContent(inputBindings)).pauseGameplay();
    }

    public static Interaction levelUpMenu(GameState gameState) {
        return new Interaction(new LevelUpInteractionContent(gameState));
    }



    public static Interaction cookingMenu(GameState gameState) {
        return new Interaction(new SkillingInteractionContent(
                gameState,
                "Campfire",
                CharacterSkill.COOKING,
                () -> gameState == null ? "No campfire found." : gameState.getCookingMessage(),
                "Wait a few seconds for each cooking attempt.",
                "Stop Cooking",
                () -> {
                    if (gameState != null) {
                        gameState.stopCooking();
                    }
                }
        )).allowCharacterMenuOverlay();
    }

    public static Interaction smeltingMenu(GameState gameState) {
        return new Interaction(new SkillingInteractionContent(
                gameState,
                "Furnace",
                CharacterSkill.SMITHING,
                () -> gameState == null ? "No furnace found." : gameState.getSmeltingMessage(),
                "Wait a few seconds for each smelting attempt.",
                "Stop Smelting",
                () -> {
                    if (gameState != null) {
                        gameState.stopSmelting();
                    }
                }
        )).allowCharacterMenuOverlay();
    }

    public static Interaction anvilMenu(GameState gameState) {
        return new Interaction(new AnvilInteractionContent(gameState)).allowCharacterMenuOverlay();
    }

    public static Interaction postBattleMenu(GameState gameState, Monster monster, int experienceReward, int hpLost) {
        List<InteractionOption> options = new ArrayList<>();
        PlayerCharacter player = gameState == null ? null : gameState.getPlayerCharacter();

        options.add(option("Butcher Random Limb", () -> resolveButchery(gameState, monster, null)));

        if (player != null) {
            for (LimbSlot slot : ButcherySystem.unlockedButcheryTargets(player)) {
                options.add(option("Target " + slot.getDisplayName(), () -> resolveButchery(gameState, monster, slot)));
            }
        }

        options.add(closeOption("Leave Remains"));

        String bodyText = "Victory.\nXP earned: "
                + Math.max(0, experienceReward)
                + "\nHP lost: "
                + Math.max(0, hpLost)
                + "\nYou can attempt to butcher the "
                + (monster == null ? "creature" : monster.getName())
                + " for a graftable limb.";

        InteractionModel model = new InteractionModel(
                "Battle Results",
                bodyText,
                null,
                null,
                null,
                null,
                true,
                options
        );

        return new Interaction(new StaticInteractionContent(model));
    }

    public static Interaction graftMenu(GameState gameState, LimbItem limb, Runnable removeLimbFromInventory) {
        List<InteractionOption> options = new ArrayList<>();
        PlayerCharacter player = gameState == null ? null : gameState.getPlayerCharacter();
        int graftingLevel = player == null ? 1 : player.getSkillLevel(CharacterSkill.GRAFTING);

        if (graftingLevel >= 5) {
            options.add(option("Hazardous", () -> resolveGraft(gameState, limb, ButcherySystem.GraftApproach.HAZARDOUS, removeLimbFromInventory)));
            options.add(option("Perfectly", () -> resolveGraft(gameState, limb, ButcherySystem.GraftApproach.PERFECT, removeLimbFromInventory)));
        } else {
            options.add(option("Unskilled", () -> resolveGraft(gameState, limb, ButcherySystem.GraftApproach.UNSKILLED, removeLimbFromInventory)));
        }

        options.add(closeOption("Cancel"));

        String bodyText = limb == null
                ? "There is no limb to graft."
                : "Graft "
                + limb.getName()
                + " onto "
                + limb.getLimbSlot().getDisplayName()
                + ".\nCondition: "
                + limb.getCondition().getDisplayName()
                + "\nGrafting level: "
                + graftingLevel;

        return new Interaction(new StaticInteractionContent(new InteractionModel(
                "Grafting",
                bodyText,
                null,
                null,
                null,
                null,
                true,
                options
        )));
    }

    private static void resolveButchery(GameState gameState, Monster monster, LimbSlot requestedSlot) {
        if (gameState == null || gameState.getPlayerCharacter() == null) {
            return;
        }

        var result = ButcherySystem.butcher(gameState.getPlayerCharacter(), monster, requestedSlot);
        String message;

        if (result.isPresent()) {
            LimbItem limb = result.get();
            boolean stored = gameState.getInventory().addItem(limb);
            if (!stored) {
                gameState.addEntity(new MapEntity(limb, gameState.getPlayerX(), gameState.getPlayerY()));
            }
            message = stored
                    ? "Recovered " + limb.getCondition().getDisplayName() + " " + limb.getName() + "."
                    : "Recovered " + limb.getName() + ", but your inventory is full. It falls to the floor.";
        } else {
            message = "The butchery fails and no usable limb remains.";
        }

        gameState.openInteraction(prompt("Butchery", message, closeOption("Continue")));
    }

    private static void resolveGraft(
            GameState gameState,
            LimbItem limb,
            ButcherySystem.GraftApproach approach,
            Runnable removeLimbFromInventory
    ) {
        if (gameState == null || gameState.getPlayerCharacter() == null) {
            return;
        }

        ButcherySystem.GraftResult result = ButcherySystem.graft(gameState.getPlayerCharacter(), limb, approach);

        if (result.success() && removeLimbFromInventory != null) {
            removeLimbFromInventory.run();
        }

        gameState.openInteraction(prompt("Grafting", result.message(), closeOption("Continue")));
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
        private String selectionSoundPath;
        private boolean closed = false;
        private boolean inventoryOverlayAllowed = false;
        private boolean characterMenuOverlayAllowed = false;
        private boolean gameplayPaused = false;
        private boolean opensOverlay = true;

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

        public String getSelectionSoundPath() {
            return selectionSoundPath;
        }

        public Interaction withSelectionSoundPath(String selectionSoundPath) {
            this.selectionSoundPath = selectionSoundPath;
            return this;
        }

        public Interaction allowInventoryOverlay() {
            inventoryOverlayAllowed = true;
            return this;
        }

        public Interaction allowCharacterMenuOverlay() {
            characterMenuOverlayAllowed = true;
            inventoryOverlayAllowed = true;
            return this;
        }

        public boolean isInventoryOverlayAllowed() {
            return inventoryOverlayAllowed;
        }

        public boolean isCharacterMenuOverlayAllowed() {
            return characterMenuOverlayAllowed;
        }

        public Interaction pauseGameplay() {
            gameplayPaused = true;
            return this;
        }

        public boolean pausesGameplay() {
            return gameplayPaused;
        }

        private Interaction withoutOverlay() {
            opensOverlay = false;
            return this;
        }

        public boolean opensOverlay() {
            return opensOverlay;
        }

        public boolean handleKeyPressed(KeyEvent e) {
            return content.handleKeyPressed(e, this);
        }

        public boolean handleMousePressed(MouseEvent e, Rectangle windowBounds) {
            return content.handleMousePressed(e, this, windowBounds);
        }

        public boolean handleMouseMoved(Point point, Rectangle windowBounds) {
            return content.handleMouseMoved(point, this, windowBounds);
        }

        public void drawCustom(Graphics2D g, Rectangle windowBounds) {
            content.drawCustom(g, this, windowBounds);
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

        default boolean handleMousePressed(MouseEvent e, Interaction interaction, Rectangle windowBounds) {
            return false;
        }

        default boolean handleMouseMoved(Point point, Interaction interaction, Rectangle windowBounds) {
            return false;
        }

        default void drawCustom(Graphics2D g, Interaction interaction, Rectangle windowBounds) {
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
                LOGGER.warning(() -> "Conversation node not found: " + nextNodeId);
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
    @FunctionalInterface
    public interface InteractionFactory {
        Interaction create(InteractionContext context);
    }

    public static class InteractionContext {
        private final GameState gameState;
        private final MapEntity entity;
        private final int tileX;
        private final int tileY;

        public InteractionContext(GameState gameState, MapEntity entity) {
            this(gameState, entity, -1, -1);
        }

        public InteractionContext(GameState gameState, MapEntity entity, int tileX, int tileY) {
            this.gameState = gameState;
            this.entity = entity;
            this.tileX = tileX;
            this.tileY = tileY;
        }

        public GameState getGameState() {
            return gameState;
        }

        public MapEntity getEntity() {
            return entity;
        }

        public int getTileX() {
            return tileX;
        }

        public int getTileY() {
            return tileY;
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
            return create(interactionId, gameState, entity, -1, -1);
        }

        public Interaction create(String interactionId, GameState gameState, MapEntity entity, int tileX, int tileY) {
            if (interactionId == null || interactionId.isBlank()) {
                return null;
            }

            InteractionFactory factory = factories.get(interactionId);

            if (factory == null) {
                if ("custom_shop".equals(interactionId) && entity != null && entity.getShopBlueprint() != null) {
                    return createCustomShopInteraction(gameState, entity);
                }

                if (interactionId != null && interactionId.startsWith("map_link|")) {
                    return createMapLinkInteraction(interactionId, gameState);
                }

                if (interactionId != null && interactionId.startsWith("custom_fishing_")) {
                    int interactionX = tileX >= 0 ? tileX : entity == null ? tileX : entity.getX();
                    int interactionY = tileY >= 0 ? tileY : entity == null ? tileY : entity.getY();
                    return createGatheringInteraction(gameState, interactionX, interactionY, GameState.GatheringToolType.FISHING);
                }

                if (interactionId != null && interactionId.startsWith("custom_mining_")) {
                    int interactionX = tileX >= 0 ? tileX : entity == null ? tileX : entity.getX();
                    int interactionY = tileY >= 0 ? tileY : entity == null ? tileY : entity.getY();
                    MapDesignLibrary.CustomGatheringNode node = gameState == null
                            ? null
                            : gameState.getCustomGatheringNodeAtPosition(interactionX, interactionY);
                    if (node != null && node.nodeType() == MapDesignLibrary.GatheringNodeType.TREE) {
                        return createGatheringInteraction(gameState, interactionX, interactionY, GameState.GatheringToolType.WOODCUTTING);
                    }
                    return createGatheringInteraction(gameState, interactionX, interactionY, GameState.GatheringToolType.MINING);
                }

                if (interactionId != null && interactionId.startsWith("custom_woodcutting_")) {
                    int interactionX = tileX >= 0 ? tileX : entity == null ? tileX : entity.getX();
                    int interactionY = tileY >= 0 ? tileY : entity == null ? tileY : entity.getY();
                    return createGatheringInteraction(gameState, interactionX, interactionY, GameState.GatheringToolType.WOODCUTTING);
                }

                Interaction authoredInteraction = createAuthoredInteraction(interactionId, gameState, entity, tileX, tileY);
                if (authoredInteraction != null) {
                    return authoredInteraction;
                }

                LOGGER.warning(() -> "No interaction registered for id: " + interactionId);
                return null;
            }

            return factory.create(new InteractionContext(gameState, entity, tileX, tileY));
        }

        private Interaction createCustomShopInteraction(GameState gameState, MapEntity entity) {
            ShopSystem.ShopBlueprint blueprint = entity.getShopBlueprint();
            if (gameState == null || blueprint == null) {
                return null;
            }
            String merchantName = entity.getName() == null || entity.getName().isBlank()
                    ? blueprint.shopName()
                    : entity.getName();
            return dialogue(
                    merchantName,
                    blueprint.greeting(),
                    null,
                    entity.getStaticImage(),
                    option("Trade", () -> {
                        ShopSystem.ShopSession shop = entity.getShopSession();
                        if (shop == null) {
                            shop = ShopSystem.createAuthoredShop(gameState, blueprint);
                            entity.setShopSession(shop);
                        }
                        if (shop != null) {
                            gameState.openShop(shop);
                        }
                    }),
                    closeOption("Leave")
            );
        }

        private Interaction createMapLinkInteraction(String interactionId, GameState gameState) {
            String[] parts = interactionId.split("\\|");
            if (parts.length < 4) {
                return prompt("Map Link", "This map link is incomplete.", closeOption("Close"));
            }

            String mapPathText = parts[1];
            int targetX = parseInteractionInt(parts[2], 1);
            int targetY = parseInteractionInt(parts[3], 1);
            return prompt(
                    "Map Link",
                    "Travel to " + mapPathText + "?",
                    option("Travel", () -> openMapLink(gameState, mapPathText, targetX, targetY)),
                    closeOption("Stay")
            );
        }

        private void openMapLink(GameState gameState, String mapPathText, int targetX, int targetY) {
            if (gameState == null || mapPathText == null || mapPathText.isBlank()) {
                return;
            }

            try {
                Path path = Path.of(mapPathText);
                if (!Files.isRegularFile(path) && !mapPathText.replace('\\', '/').startsWith("assets/")) {
                    path = MapDesignLibrary.MAP_FOLDER.resolve(mapPathText).normalize();
                }
                gameState.travelToMapLink(path, targetX, targetY);
                gameState.closeInteraction();
            } catch (Exception exception) {
                gameState.openInteraction(prompt(
                        "Map Link",
                        "Failed to travel: " + exception.getMessage(),
                        closeOption("Close")
                ));
            }
        }

        private int parseInteractionInt(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private Interaction createAuthoredInteraction(
                String interactionId,
                GameState gameState,
                MapEntity entity,
                int tileX,
                int tileY
        ) {
            if (gameState == null) {
                return null;
            }

            var authoredDialogue = gameState.getAuthoredDialogue(interactionId);
            if (authoredDialogue == null) {
                return null;
            }

            boolean firstTalk = !gameState.hasSpokenToAuthoredDialogue(authoredDialogue.interactionId());
            String startingNodeId = startingAuthoredNodeId(authoredDialogue, gameState, firstTalk);
            Interaction interaction = createAuthoredNodeInteraction(
                    authoredDialogue,
                    gameState,
                    entity,
                    tileX,
                    tileY,
                    startingNodeId,
                    "",
                    firstTalk
            );
            gameState.markSpokenToAuthoredDialogue(authoredDialogue.interactionId());
            return interaction;
        }

        private String startingAuthoredNodeId(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                GameState gameState,
                boolean firstTalk
        ) {
            String firstTalkNodeId = firstTalkNodeId(authoredDialogue);
            if (firstTalk && !firstTalkNodeId.isBlank()) {
                return firstTalkNodeId;
            }
            String repeatTalkNodeId = repeatTalkNodeId(authoredDialogue);
            if (!repeatTalkNodeId.isBlank()) {
                return repeatTalkNodeId;
            }
            if (!authoredDialogue.choices().isEmpty()) {
                return "start";
            }

            MapDesignLibrary.AuthoredDialogueNode questNode = findRelevantAuthoredQuestNode(authoredDialogue, gameState);
            return questNode == null ? "start" : questNode.nodeId();
        }

        private MapDesignLibrary.AuthoredDialogueNode findRelevantAuthoredQuestNode(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                GameState gameState
        ) {
            if (authoredDialogue == null || gameState == null) {
                return null;
            }

            for (MapDesignLibrary.AuthoredDialogueNode node : authoredDialogue.nodes()) {
                if (isFirstTalkNode(node)) {
                    continue;
                }
                for (MapDesignLibrary.AuthoredDialogueChoice choice : node.choices()) {
                    if (isAuthoredChoiceRelevantForCurrentQuestStage(gameState, choice)) {
                        return node;
                    }
                }
            }
            return null;
        }

        private boolean isAuthoredChoiceRelevantForCurrentQuestStage(
                GameState gameState,
                MapDesignLibrary.AuthoredDialogueChoice choice
        ) {
            if (choice == null || choice.questId().isBlank() || choice.questStage() < 0) {
                return false;
            }

            int currentStage = gameState.getQuestStagesView().getOrDefault(choice.questId(), 0);
            int requiredStage = Math.max(0, choice.questStage() - 1);
            return currentStage == requiredStage;
        }

        private Interaction createAuthoredNodeInteraction(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                GameState gameState,
                MapEntity entity,
                int tileX,
                int tileY,
                String nodeId,
                String extraText,
                boolean firstTalk
        ) {
            boolean rootNode = nodeId == null || nodeId.isBlank() || "start".equals(nodeId);
            MapDesignLibrary.AuthoredDialogueNode node = rootNode ? null : findAuthoredDialogueNode(authoredDialogue, nodeId);
            String bodyText = rootNode
                    ? authoredDialogue.bodyText()
                    : node == null ? "This conversation path is missing." : node.bodyText();
            if (extraText != null && !extraText.isBlank()) {
                bodyText += extraText;
            }
            List<MapDesignLibrary.AuthoredDialogueChoice> choices = rootNode
                    ? authoredDialogue.choices()
                    : node == null ? List.of() : node.choices();
            List<MapDesignLibrary.AuthoredDialogueChoice> visibleChoices = visibleAuthoredChoices(gameState, choices, firstTalk);
            String conversationHubNodeId = conversationHubNodeId(authoredDialogue);
            boolean canReturnToTopics = !conversationHubNodeId.isBlank()
                    && !conversationHubNodeId.equals(nodeId);
            if (!visibleChoices.isEmpty()) {
                List<InteractionOption> options = new ArrayList<>();
                for (MapDesignLibrary.AuthoredDialogueChoice choice : visibleChoices) {
                    options.add(option(choice.label(), () -> openAuthoredDialogueChoice(
                            authoredDialogue,
                            choice,
                            gameState,
                            entity,
                            tileX,
                            tileY,
                            firstTalk
                    )));
                }
                if (canReturnToTopics) {
                    options.add(authoredTopicsOption(
                            authoredDialogue,
                            gameState,
                            entity,
                            tileX,
                            tileY,
                            conversationHubNodeId,
                            firstTalk
                    ));
                }
                options.add(closeOption("Close"));
                return dialogue(
                        authoredDialogue.speakerName(),
                        bodyText,
                        null,
                        entity == null ? null : entity.getStaticImage(),
                        options.toArray(new InteractionOption[0])
                );
            }

            String followUpInteractionId = authoredDialogue.followUpInteractionId();
            if (rootNode
                    && followUpInteractionId != null
                    && !followUpInteractionId.isBlank()
                    && !followUpInteractionId.equals(authoredDialogue.interactionId())
                    && factories.containsKey(followUpInteractionId)) {
                return dialogue(
                        authoredDialogue.speakerName(),
                        bodyText,
                        null,
                        entity == null ? null : entity.getStaticImage(),
                        option("Continue", () -> {
                            InteractionFactory followUpFactory = factories.get(followUpInteractionId);
                            if (followUpFactory != null) {
                                gameState.openInteraction(followUpFactory.create(new InteractionContext(gameState, entity, tileX, tileY)));
                            }
                        }),
                        closeOption("Close")
                );
            }

            if (canReturnToTopics) {
                return dialogue(
                        authoredDialogue.speakerName(),
                        bodyText,
                        null,
                        entity == null ? null : entity.getStaticImage(),
                        authoredTopicsOption(
                                authoredDialogue,
                                gameState,
                                entity,
                                tileX,
                                tileY,
                                conversationHubNodeId,
                                firstTalk
                        ),
                        closeOption("Close")
                );
            }

            return dialogue(
                    authoredDialogue.speakerName(),
                    bodyText,
                    null,
                    entity == null ? null : entity.getStaticImage(),
                    closeOption("Close")
            );
        }

        private void openAuthoredDialogueChoice(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                MapDesignLibrary.AuthoredDialogueChoice choice,
                GameState gameState,
                MapEntity entity,
                int tileX,
                int tileY,
                boolean firstTalk
        ) {
            String itemFailure = applyAuthoredChoiceItemAction(gameState, choice);
            if (!itemFailure.isBlank()) {
                gameState.openInteraction(dialogue(
                        authoredDialogue.speakerName(),
                        itemFailure,
                        null,
                        entity == null ? null : entity.getStaticImage(),
                        closeOption("Close")
                ));
                return;
            }

            String rewardText = applyAuthoredChoiceReward(gameState, choice);
            String questText = applyAuthoredChoiceQuestAction(gameState, choice);
            if (!choice.targetNodeId().isBlank()) {
                gameState.openInteraction(createAuthoredNodeInteraction(
                        authoredDialogue,
                        gameState,
                        entity,
                        tileX,
                        tileY,
                        choice.targetNodeId(),
                        rewardText + questText,
                        firstTalk
                ));
                return;
            }

            String conversationHubNodeId = conversationHubNodeId(authoredDialogue);
            if (!conversationHubNodeId.isBlank()) {
                gameState.openInteraction(dialogue(
                        authoredDialogue.speakerName(),
                        choice.bodyText() + rewardText + questText,
                        null,
                        entity == null ? null : entity.getStaticImage(),
                        authoredTopicsOption(
                                authoredDialogue,
                                gameState,
                                entity,
                                tileX,
                                tileY,
                                conversationHubNodeId,
                                firstTalk
                        ),
                        closeOption("Close")
                ));
                return;
            }

            gameState.openInteraction(dialogue(
                    authoredDialogue.speakerName(),
                    choice.bodyText() + rewardText + questText,
                    null,
                    entity == null ? null : entity.getStaticImage(),
                    closeOption("Close")
            ));
        }

        private InteractionOption authoredTopicsOption(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                GameState gameState,
                MapEntity entity,
                int tileX,
                int tileY,
                String conversationHubNodeId,
                boolean firstTalk
        ) {
            return option("Other topics", () -> gameState.openInteraction(createAuthoredNodeInteraction(
                    authoredDialogue,
                    gameState,
                    entity,
                    tileX,
                    tileY,
                    conversationHubNodeId,
                    "",
                    firstTalk
            )));
        }

        private String conversationHubNodeId(MapDesignLibrary.AuthoredDialogue authoredDialogue) {
            String repeatNodeId = repeatTalkNodeId(authoredDialogue);
            if (!repeatNodeId.isBlank()) {
                return repeatNodeId;
            }
            return authoredDialogue != null && !authoredDialogue.choices().isEmpty() ? "start" : "";
        }

        private String firstTalkNodeId(MapDesignLibrary.AuthoredDialogue authoredDialogue) {
            if (authoredDialogue == null) {
                return "";
            }
            for (MapDesignLibrary.AuthoredDialogueNode node : authoredDialogue.nodes()) {
                if (isFirstTalkNode(node)) {
                    return node.nodeId();
                }
            }
            return "";
        }

        private boolean isFirstTalkNode(MapDesignLibrary.AuthoredDialogueNode node) {
            return node != null
                    && ("firstTalk".equalsIgnoreCase(node.nodeId())
                    || "firstTime".equalsIgnoreCase(node.nodeId()));
        }

        private String repeatTalkNodeId(MapDesignLibrary.AuthoredDialogue authoredDialogue) {
            if (authoredDialogue == null) {
                return "";
            }
            for (MapDesignLibrary.AuthoredDialogueNode node : authoredDialogue.nodes()) {
                if ("repeatTalk".equalsIgnoreCase(node.nodeId())
                        || "topics".equalsIgnoreCase(node.nodeId())) {
                    return node.nodeId();
                }
            }
            return "";
        }

        private List<MapDesignLibrary.AuthoredDialogueChoice> visibleAuthoredChoices(
                GameState gameState,
                List<MapDesignLibrary.AuthoredDialogueChoice> choices,
                boolean firstTalk
        ) {
            if (choices == null || choices.isEmpty()) {
                return List.of();
            }
            List<MapDesignLibrary.AuthoredDialogueChoice> visibleChoices = new ArrayList<>();
            for (MapDesignLibrary.AuthoredDialogueChoice choice : choices) {
                if (isAuthoredChoiceVisible(gameState, choice, firstTalk)) {
                    visibleChoices.add(choice);
                }
            }
            return visibleChoices;
        }

        private boolean isAuthoredChoiceVisible(GameState gameState, MapDesignLibrary.AuthoredDialogueChoice choice, boolean firstTalk) {
            if (choice == null) {
                return false;
            }
            if (choice.firstTalkOnly() && !firstTalk) {
                return false;
            }
            if (gameState != null && !choice.questId().isBlank() && choice.questStage() >= 0) {
                int currentStage = gameState.getQuestStagesView().getOrDefault(choice.questId(), 0);
                int requiredStage = Math.max(0, choice.questStage() - 1);
                if (currentStage != requiredStage) {
                    return false;
                }
            }
            return gameState == null
                    || (hasRequiredAuthoredChoiceItem(gameState, choice.requiredItemName())
                    && hasRequiredAuthoredChoiceItem(gameState, choice.takeItemName()));
        }

        private boolean hasRequiredAuthoredChoiceItem(GameState gameState, String itemName) {
            return itemName == null
                    || itemName.isBlank()
                    || gameState.getInventory().hasItemNamed(itemName);
        }

        private String applyAuthoredChoiceItemAction(GameState gameState, MapDesignLibrary.AuthoredDialogueChoice choice) {
            if (gameState == null || choice == null || choice.takeItemName().isBlank()) {
                return "";
            }
            if (gameState.getInventory().removeFirstItemNamed(choice.takeItemName())) {
                return "";
            }
            return "You need " + choice.takeItemName() + " for that.";
        }

        private String applyAuthoredChoiceReward(GameState gameState, MapDesignLibrary.AuthoredDialogueChoice choice) {
            if (gameState == null || choice == null) {
                return "";
            }

            StringBuilder rewardText = new StringBuilder();
            if (!choice.giveItemName().isBlank()) {
                InventorySystem.Item item = createAuthoredChoiceRewardItem(gameState, choice.giveItemName());
                if (item == null) {
                    rewardText.append("\nMissing reward item: ").append(choice.giveItemName());
                } else if (!gameState.getInventory().addItem(item)) {
                    rewardText.append("\nInventory full: ").append(item.getName()).append(" lost");
                } else {
                    rewardText.append("\n+1 ").append(item.getName());
                }
            }

            if (choice.giveGold() > 0) {
                gameState.addGold(choice.giveGold());
                rewardText.append("\n+").append(choice.giveGold()).append(" gold");
            }

            if (choice.giveSkill() != null && choice.giveSkillXp() > 0) {
                gameState.getPlayerCharacter().addSkillExperience(choice.giveSkill(), choice.giveSkillXp());
                rewardText.append("\n")
                        .append("+")
                        .append(Math.max(0, choice.giveSkillXp()))
                        .append(" ")
                        .append(choice.giveSkill() == null ? "Skill" : choice.giveSkill().getDisplayName())
                        .append(" xp");
            }

            return rewardText.isEmpty() ? "" : "\n\n" + rewardText;
        }

        private InventorySystem.Item createAuthoredChoiceRewardItem(GameState gameState, String itemName) {
            return gameState.createItemByNameOrId(itemName);
        }

        private MapDesignLibrary.AuthoredDialogueNode findAuthoredDialogueNode(
                MapDesignLibrary.AuthoredDialogue authoredDialogue,
                String nodeId
        ) {
            if (authoredDialogue == null || nodeId == null || nodeId.isBlank()) {
                return null;
            }
            for (MapDesignLibrary.AuthoredDialogueNode node : authoredDialogue.nodes()) {
                if (nodeId.equals(node.nodeId())) {
                    return node;
                }
            }
            return null;
        }

        private String applyAuthoredChoiceQuestAction(GameState gameState, MapDesignLibrary.AuthoredDialogueChoice choice) {
            if (gameState == null
                    || choice == null
                    || choice.questId().isBlank()
                    || choice.questStage() < 0) {
                return "";
            }

            gameState.setQuestStage(choice.questId(), choice.questStage());
            GameState.QuestDefinition quest = gameState.getQuestDefinition(choice.questId());
            String questName = quest == null ? choice.questId() : quest.displayName();
            return "\n\nQuest updated: " + questName;
        }

        public static InteractionRegistry createDefault() {
            InteractionRegistry registry = new InteractionRegistry();

            registry.register(GENERATED_DUNGEON_GATE_ID, context -> prompt(
                    "Dungeon Gate",
                    "Go one floor deeper into a newly generated dungeon?",
                    option("Enter", () -> {
                        GeneratedDungeon generatedDungeon = new DungeonGenerator().generate();
                        context.getGameState().setCurrentFloor(context.getGameState().getCurrentFloor() + 1);
                        context.getGameState().changeDungeon(generatedDungeon);
                    }),
                    closeOption("Stay")
            ));

            registry.register("fishing_shoal", context -> {
                return createGatheringInteraction(context.getGameState(), context.getTileX(), context.getTileY(), GameState.GatheringToolType.FISHING);
            });

            registry.register("mineral_rock_basic", context -> {
                return createGatheringInteraction(context.getGameState(), context.getTileX(), context.getTileY(), GameState.GatheringToolType.MINING);
            });

            registry.register("campfire_basic", context -> {
                if (!context.getGameState().startCooking(context.getTileX(), context.getTileY())) {
                    return prompt(
                            "Campfire",
                            context.getGameState().getCookingMessage(),
                            closeOption("Close")
                    );
                }

                return cookingMenu(context.getGameState());
            });

            registry.register("furnace_basic", context -> {
                if (!context.getGameState().startSmelting(context.getTileX(), context.getTileY())) {
                    return prompt(
                            "Furnace",
                            context.getGameState().getSmeltingMessage(),
                            closeOption("Close")
                    );
                }

                return smeltingMenu(context.getGameState());
            });

            registry.register("anvil_basic", context -> {
                if (!context.getGameState().startSmithing(context.getTileX(), context.getTileY())) {
                    return prompt(
                            "Anvil",
                            context.getGameState().getSmithingMessage(),
                            closeOption("Close")
                    );
                }

                return anvilMenu(context.getGameState());
            });

            return registry;
        }

        private static Interaction createGatheringInteraction(
                GameState gameState, int tileX, int tileY,
                GameState.GatheringToolType toolType) {
            if (toolType == GameState.GatheringToolType.FISHING) {
                if (!gameState.startFishing(tileX, tileY)) {
                    gameState.getWorldMessageLog().post(
                            WorldMessageLog.Category.WARNING, gameState.getFishingMessage());
                    return handledWithoutOverlay();
                }
                gameState.getWorldMessageLog().post(
                        WorldMessageLog.Category.SYSTEM, gameState.getFishingMessage());
            } else {
                if (!gameState.startMining(tileX, tileY)) {
                    gameState.getWorldMessageLog().post(
                            WorldMessageLog.Category.WARNING, gameState.getMiningMessage());
                    return handledWithoutOverlay();
                }
                gameState.getWorldMessageLog().post(
                        WorldMessageLog.Category.SYSTEM, gameState.getMiningMessage());
            }
            return handledWithoutOverlay();
        }
    }

    public record EditorInteractionDefinition(String displayName, String interactionId) {
    }

    private abstract static class SettingsMenuContent implements InteractionContent {
        private final SoundSystem soundSystem;
        private final GameState gameState;
        private final Runnable exitAction;
        private final Runnable controlsAction;
        private final Runnable saveAction;
        private final Runnable loadAction;

        private SettingsMenuContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            this.soundSystem = soundSystem;
            this.gameState = gameState;
            this.exitAction = exitAction;
            this.controlsAction = controlsAction;
            this.saveAction = saveAction;
            this.loadAction = loadAction;
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

        protected Runnable saveAction() {
            return saveAction;
        }

        protected Runnable loadAction() {
            return loadAction;
        }

        protected Runnable exitAction() {
            return exitAction;
        }

        protected InteractionOption backToConfigOption() {
            return option("Back", () -> openInteraction(configMenu(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction)));
        }

        protected InteractionOption settingsOption() {
            return option("Settings", () -> openInteraction(settingsMenu(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction)));
        }

        protected InteractionOption volumeOption() {
            return option("Volume", () -> openInteraction(volumeMenu(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction)));
        }

        protected InteractionOption debugOption() {
            return option("Debug", () -> openInteraction(debugMenu(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction)));
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

    private static class LevelUpInteractionContent implements InteractionContent {
        private final GameState gameState;

        private LevelUpInteractionContent(GameState gameState) {
            this.gameState = gameState;
        }

        @Override
        public InteractionModel getModel() {
            PlayerCharacter player = gameState == null ? null : gameState.getPlayerCharacter();

            if (player == null) {
                return new InteractionModel(
                        "Level Up",
                        "No player found.",
                        null,
                        null,
                        null,
                        null,
                        true,
                        true,
                        List.of(closeOption("Close"))
                );
            }

            List<InteractionOption> options = new ArrayList<>();

            for (PlayerStat stat : PlayerStat.values()) {
                options.add(stayOpenOption(
                        stat.getDisplayName() + " [" + player.getStat(stat) + "] +",
                        () -> player.spendStatPoint(stat)
                ));
            }

            options.add(closeOption("Done"));

            return new InteractionModel(
                    "Level Up",
                    "Level "
                            + player.getLevel()
                            + "\nAvailable stat points: "
                            + player.getAvailableStatPoints()
                            + "\n\nAllocate your points now, or close this and spend them later from the next level-up prompt.",
                    null,
                    null,
                    null,
                    null,
                    false,
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

            if (option.shouldCloseAfterSelection()) {
                interaction.close();
            }
        }
    }

    private static class SkillingInteractionContent implements InteractionContent {
        private final GameState gameState;
        private final String title;
        private final CharacterSkill skill;
        private final Supplier<String> messageSupplier;
        private final String footerText;
        private final String stopLabel;
        private final Runnable stopAction;

        private SkillingInteractionContent(
                GameState gameState,
                String title,
                CharacterSkill skill,
                Supplier<String> messageSupplier,
                String footerText,
                String stopLabel,
                Runnable stopAction
        ) {
            this.gameState = gameState;
            this.title = title;
            this.skill = skill;
            this.messageSupplier = messageSupplier;
            this.footerText = footerText;
            this.stopLabel = stopLabel;
            this.stopAction = stopAction;
        }

        @Override
        public InteractionModel getModel() {
            String body = gameState == null
                    ? messageSupplier.get()
                    : messageSupplier.get()
                    + "\n\n"
                    + skill.getDisplayName()
                    + " level "
                    + gameState.getPlayerCharacter().getSkillLevel(skill)
                    + "  XP "
                    + gameState.getPlayerCharacter().getSkillExperience(skill)
                    + "/"
                    + gameState.getPlayerCharacter().getSkillExperienceRequired(skill)
                    + "\n\n"
                    + footerText;

            return new InteractionModel(
                    title,
                    body,
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(closeOption(stopLabel))
            );
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            if (stopAction != null) {
                stopAction.run();
            }

            interaction.close();
        }
    }

    private static class AnvilInteractionContent implements InteractionContent {
        private static final int SLOT_SIZE = 54;
        private static final int SLOT_GAP = 10;
        private static final int GRID_COLUMNS = 6;
        private static final int GRID_PADDING = 22;

        private final GameState gameState;
        private final List<Rectangle> recipeBounds = new ArrayList<>();
        private int hoveredRecipeIndex = -1;

        private AnvilInteractionContent(GameState gameState) {
            this.gameState = gameState;
        }

        @Override
        public InteractionModel getModel() {
            String material = gameState == null || gameState.getSmithingMaterialName() == null
                    ? "No material selected."
                    : "Material: " + gameState.getSmithingMaterialName();
            String body = material
                    + "\n"
                    + (gameState == null ? "" : gameState.getSmithingMessage())
                    + "\n\n"
                    + "Smithing level "
                    + (gameState == null ? 1 : gameState.getPlayerCharacter().getSkillLevel(CharacterSkill.SMITHING))
                    + "  XP "
                    + (gameState == null ? 0 : gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.SMITHING))
                    + "/"
                    + (gameState == null ? 0 : gameState.getPlayerCharacter().getSkillExperienceRequired(CharacterSkill.SMITHING));

            return new InteractionModel(
                    "Anvil",
                    body,
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(closeOption("Close"))
            );
        }

        @Override
        public void drawCustom(Graphics2D g, Interaction interaction, Rectangle windowBounds) {
            recipeBounds.clear();

            if (gameState == null) {
                return;
            }

            List<CraftingSystem.SmithingRecipe> recipes = gameState.getAvailableSmithingRecipes();
            Rectangle grid = gridBounds(windowBounds);
            Font oldFont = g.getFont();

            for (int i = 0; i < recipes.size(); i++) {
                CraftingSystem.SmithingRecipe recipe = recipes.get(i);
                int column = i % GRID_COLUMNS;
                int row = i / GRID_COLUMNS;
                Rectangle slot = new Rectangle(
                        grid.x + column * (SLOT_SIZE + SLOT_GAP),
                        grid.y + row * (SLOT_SIZE + SLOT_GAP),
                        SLOT_SIZE,
                        SLOT_SIZE
                );
                recipeBounds.add(slot);

                boolean canCraft = canCraft(recipe);
                g.setColor(new Color(20, 22, 28, 230));
                g.fillRoundRect(slot.x, slot.y, slot.width, slot.height, 6, 6);
                g.setColor(canCraft ? new Color(218, 196, 126) : new Color(150, 55, 55));
                g.drawRoundRect(slot.x, slot.y, slot.width, slot.height, 6, 6);

                BufferedImage icon = recipe.previewItem() == null ? null : recipe.previewItem().getIcon();
                if (icon != null) {
                    g.drawImage(icon, slot.x + 7, slot.y + 7, slot.width - 14, slot.height - 14, null);
                }

                if (!canCraft) {
                    Composite oldComposite = g.getComposite();
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                    g.setColor(new Color(120, 0, 0));
                    g.fillRoundRect(slot.x, slot.y, slot.width, slot.height, 6, 6);
                    g.setComposite(oldComposite);
                }
            }

            if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < recipes.size() && hoveredRecipeIndex < recipeBounds.size()) {
                drawRecipeTooltip(g, recipes.get(hoveredRecipeIndex), recipeBounds.get(hoveredRecipeIndex), windowBounds);
            }

            g.setFont(oldFont);
        }

        @Override
        public boolean handleMousePressed(MouseEvent e, Interaction interaction, Rectangle windowBounds) {
            if (gameState == null || e == null) {
                return false;
            }

            List<CraftingSystem.SmithingRecipe> recipes = gameState.getAvailableSmithingRecipes();
            Point point = e.getPoint();
            for (int i = 0; i < recipeBounds.size() && i < recipes.size(); i++) {
                if (!recipeBounds.get(i).contains(point)) {
                    continue;
                }

                gameState.craftSmithingRecipe(recipes.get(i));
                return true;
            }

            return false;
        }

        @Override
        public boolean handleMouseMoved(Point point, Interaction interaction, Rectangle windowBounds) {
            int previousHover = hoveredRecipeIndex;
            hoveredRecipeIndex = -1;

            if (point != null) {
                for (int i = 0; i < recipeBounds.size(); i++) {
                    if (recipeBounds.get(i).contains(point)) {
                        hoveredRecipeIndex = i;
                        break;
                    }
                }
            }

            return previousHover != hoveredRecipeIndex;
        }

        @Override
        public void selectOption(int optionIndex, Interaction interaction, boolean alternateAction) {
            interaction.close();
        }

        private Rectangle gridBounds(Rectangle windowBounds) {
            return new Rectangle(
                    windowBounds.x + GRID_PADDING,
                    windowBounds.y + 132,
                    windowBounds.width - GRID_PADDING * 2,
                    windowBounds.height - 210
            );
        }

        private boolean canCraft(CraftingSystem.SmithingRecipe recipe) {
            return gameState != null
                    && recipe != null
                    && gameState.getPlayerCharacter().getSkillLevel(CharacterSkill.SMITHING) >= recipe.requiredLevel();
        }

        private void drawRecipeTooltip(Graphics2D g, CraftingSystem.SmithingRecipe recipe, Rectangle slot, Rectangle windowBounds) {
            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.PLAIN, 13f));
            FontMetrics metrics = g.getFontMetrics();
            String name = recipe.displayName();
            String bars = recipe.requiredBars() + " " + recipe.materialName();
            String level = "Requires Smithing " + recipe.requiredLevel();
            int width = Math.max(metrics.stringWidth(name), Math.max(metrics.stringWidth(bars), metrics.stringWidth(level))) + 20;
            int height = 58;
            int x = Math.min(windowBounds.x + windowBounds.width - width - 12, slot.x + slot.width + 10);
            int y = Math.max(windowBounds.y + 10, slot.y);

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.94f));
            g.setColor(new Color(8, 9, 13));
            g.fillRoundRect(x, y, width, height, 7, 7);
            g.setComposite(oldComposite);

            g.setColor(new Color(112, 92, 58));
            g.drawRoundRect(x, y, width, height, 7, 7);
            g.setColor(new Color(238, 228, 190));
            g.drawString(name, x + 10, y + 18);
            g.setColor(new Color(218, 210, 180));
            g.drawString(bars, x + 10, y + 35);
            g.drawString(level, x + 10, y + 51);
            g.setFont(oldFont);
        }
    }

    private static class ConfigInteractionContent extends SettingsMenuContent {
        private ConfigInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
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
                            option("Save", saveAction()),
                            option("Load", loadAction()),
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
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
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
                            stayOpenOption(cameraMovementLabel(), this::toggleCameraMovement),
                            option("Controls", controlsAction()),
                            volumeOption(),
                            debugOption(),
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

        private String cameraMovementLabel() {
            if (gameState() == null) {
                return "Movement [STATIC]";
            }

            return switch (gameState().getCameraMovementMode()) {
                case STATIC -> "Movement [STATIC]";
                case FLUID -> "Movement [FLUID]";
            };
        }

        private void toggleCameraMovement() {
            if (gameState() != null) {
                gameState().toggleCameraMovementMode();
            }
        }

        private void toggleMiniMap() {
            if (gameState() != null) {
                gameState().cycleMiniMapMode();
            }
        }
    }

    private static class DebugInteractionContent extends SettingsMenuContent {
        private DebugInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
        }

        @Override
        public InteractionModel getModel() {
            return new InteractionModel(
                    "Debug",
                    "Debug tools",
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(
                            stayOpenOption(debugInfoLabel(), this::toggleDebugInfo),
                            stayOpenOption(debugSkillsLabel(), this::loadDebugSkills),
                            option("Add Item", this::openDebugItemMenu),
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
                    controlsAction(),
                    saveAction(),
                    loadAction()
            )));
        }

        private String debugInfoLabel() {
            if (gameState() == null || !gameState().isPerformanceOverlayVisible()) {
                return "Debug Info [OFF]";
            }

            return "Debug Info [ON]";
        }

        private String debugSkillsLabel() {
            if (gameState() == null || gameState().getPlayerCharacter() == null) {
                return "Load Debug Skills";
            }

            return gameState().getPlayerCharacter().isDebugSkillsLoaded()
                    ? "Debug Skills [LOADED]"
                    : "Load Debug Skills";
        }

        private void toggleDebugInfo() {
            if (gameState() != null) {
                gameState().togglePerformanceOverlayVisible();
            }
        }

        private void loadDebugSkills() {
            if (gameState() != null && gameState().getPlayerCharacter() != null) {
                gameState().getPlayerCharacter().loadDebugSkills();
            }
        }

        private void openDebugItemMenu() {
            openInteraction(debugItemCategoryMenu(
                    soundSystem(),
                    gameState(),
                    exitAction(),
                    controlsAction(),
                    saveAction(),
                    loadAction()
            ));
        }
    }

    private enum DebugItemCategory {
        ITEMS("Items"),
        CUSTOM_LIMBS("Custom Limbs");

        private final String displayName;

        DebugItemCategory(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }

    private record DebugItemEntry(String label, Supplier<InventorySystem.Item> itemSupplier) {
    }

    private static class DebugItemCategoryInteractionContent extends SettingsMenuContent {
        private DebugItemCategoryInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
        }

        @Override
        public InteractionModel getModel() {
            return new InteractionModel(
                    "Debug Items",
                    "Choose an item source.",
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    List.of(
                            option(DebugItemCategory.ITEMS.displayName(), () -> openDebugItemMenu(DebugItemCategory.ITEMS)),
                            option(DebugItemCategory.CUSTOM_LIMBS.displayName(), () -> openDebugItemMenu(DebugItemCategory.CUSTOM_LIMBS)),
                            backToDebugOption(),
                            closeOption("Close")
                    )
            );
        }

        private void openDebugItemMenu(DebugItemCategory category) {
            openInteraction(debugItemMenu(
                    soundSystem(),
                    gameState(),
                    exitAction(),
                    controlsAction(),
                    saveAction(),
                    loadAction(),
                    category
            ));
        }

        private InteractionOption backToDebugOption() {
            return option("Back", () -> openInteraction(debugMenu(
                    soundSystem(),
                    gameState(),
                    exitAction(),
                    controlsAction(),
                    saveAction(),
                    loadAction()
            )));
        }
    }

    private static class DebugItemInteractionContent extends SettingsMenuContent {
        private final DebugItemCategory category;
        private List<DebugItemEntry> cachedEntries;
        private String lastMessage = "Click an item to add one copy to your inventory.";

        private DebugItemInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction,
                DebugItemCategory category
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
            this.category = category == null ? DebugItemCategory.ITEMS : category;
        }

        @Override
        public InteractionModel getModel() {
            List<DebugItemEntry> entries = itemEntries();
            List<InteractionOption> options = new ArrayList<>();

            for (DebugItemEntry entry : entries) {
                options.add(stayOpenOption(entry.label(), () -> addItem(entry)));
            }

            options.add(backToCategoriesOption());
            options.add(closeOption("Close"));

            String body = lastMessage
                    + "\n\n"
                    + entries.size()
                    + " available "
                    + category.displayName().toLowerCase()
                    + ".";

            if (entries.isEmpty()) {
                body += "\n\nNo entries were found for this category.";
            }

            return new InteractionModel(
                    category.displayName(),
                    body,
                    null,
                    null,
                    null,
                    null,
                    true,
                    true,
                    options
            );
        }

        private void addItem(DebugItemEntry entry) {
            if (gameState() == null || gameState().getInventory() == null || entry == null) {
                lastMessage = "No inventory is available.";
                return;
            }

            InventorySystem.Item item = entry.itemSupplier().get();
            if (item == null) {
                lastMessage = "That item could not be created.";
                return;
            }

            if (!gameState().getInventory().addItem(item)) {
                lastMessage = "Inventory is full. Could not add " + item.getName() + ".";
                return;
            }

            lastMessage = "Added " + item.getName() + ".";
        }

        private List<DebugItemEntry> itemEntries() {
            if (cachedEntries == null) {
                cachedEntries = switch (category) {
                    case ITEMS -> customItemEntries();
                    case CUSTOM_LIMBS -> customLimbEntries();
                };
            }
            return cachedEntries;
        }

        private List<DebugItemEntry> customItemEntries() {
            Map<String, MapDesignLibrary.CustomItem> customItems = new LinkedHashMap<>();
            loadSharedContentItems(customItems);
            if (gameState() != null) {
                for (MapDesignLibrary.CustomItem item : gameState().getCustomItems()) {
                    customItems.put(item.itemId(), item);
                }
            }

            List<DebugItemEntry> entries = new ArrayList<>();
            for (MapDesignLibrary.CustomItem item : customItems.values()) {
                entries.add(new DebugItemEntry(item.displayName(), item::createItem));
            }
            entries.sort(Comparator.comparing(DebugItemEntry::label, String.CASE_INSENSITIVE_ORDER));
            return entries;
        }

        private List<DebugItemEntry> customLimbEntries() {
            Map<String, MapDesignLibrary.CustomLimb> customLimbs = new LinkedHashMap<>();
            loadSharedContentLimbs(customLimbs);
            if (gameState() != null) {
                for (MapDesignLibrary.CustomLimb limb : gameState().getCustomLimbs()) {
                    customLimbs.put(limb.limbId(), limb);
                }
            }

            List<DebugItemEntry> entries = new ArrayList<>();
            for (MapDesignLibrary.CustomLimb limb : customLimbs.values()) {
                entries.add(new DebugItemEntry(limb.displayName(), limb::createLimb));
            }
            entries.sort(Comparator.comparing(DebugItemEntry::label, String.CASE_INSENSITIVE_ORDER));
            return entries;
        }

        private void loadSharedContentItems(Map<String, MapDesignLibrary.CustomItem> customItems) {
            try {
                for (MapDesignLibrary.CustomItem item : MapDesignLibrary.loadSharedContent().customItems()) {
                    customItems.put(item.itemId(), item);
                }
            } catch (Exception ignored) {
                // Debug menu should remain usable even if authored content is temporarily invalid.
            }
        }

        private void loadSharedContentLimbs(Map<String, MapDesignLibrary.CustomLimb> customLimbs) {
            try {
                for (MapDesignLibrary.CustomLimb limb : MapDesignLibrary.loadSharedContent().customLimbs()) {
                    customLimbs.put(limb.limbId(), limb);
                }
            } catch (Exception ignored) {
                // Debug menu should remain usable even if authored content is temporarily invalid.
            }
        }

        private InteractionOption backToCategoriesOption() {
            return option("Back", () -> openInteraction(debugItemCategoryMenu(
                    soundSystem(),
                    gameState(),
                    exitAction(),
                    controlsAction(),
                    saveAction(),
                    loadAction()
            )));
        }
    }

    private static class VolumeInteractionContent extends SettingsMenuContent {
        private static final double VOLUME_STEP = 0.10;

        private VolumeInteractionContent(
                SoundSystem soundSystem,
                GameState gameState,
                Runnable exitAction,
                Runnable controlsAction,
                Runnable saveAction,
                Runnable loadAction
        ) {
            super(soundSystem, gameState, exitAction, controlsAction, saveAction, loadAction);
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
                    controlsAction(),
                    saveAction(),
                    loadAction()
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

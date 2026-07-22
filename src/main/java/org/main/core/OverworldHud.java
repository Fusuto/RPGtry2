package org.main.core;

import org.main.battle.DifficultyResolver;
import org.main.engine.AssetLoader;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverworldHud {
    private static final String FOOZLE_PATH = "assets/images/Foozle_UI_0001_RPG_Set_1/";
    private static final String A1_ICON_PATH = "assets/images/ui/01_UI_Resources/A1ICON/";
    private static final String SKILL_ICON_PATH = "assets/images/skill-icons/";
    private static final String BATTLE_UI_PATH = "assets/images/ui/01_UI_Resources/01Battle/";
    private static final String MAIN_MENU_PATH = "assets/images/ui/01_UI_Resources/04MainMenu/";

    private static final int BOTTOM_BAR_HEIGHT = 72;
    private static final int BOTTOM_BAR_OVERSCAN = 8;
    private static final int BUTTON_SIZE = 58;
    private static final int BUTTON_CENTER_GAP = 112;
    private static final int FOUR_BUTTON_GAP = 84;
    private static final int FIVE_BUTTON_GAP = 74;
    private static final int BUTTON_ICON_SIZE = 32;
    private static final int CORNER_SIZE = 96;
    private static final int SKILL_PANEL_WIDTH = 250;
    private static final int SKILL_PANEL_HEIGHT = 420;
    private static final int SKILL_ICON_SIZE = 34;
    private static final int SKILL_CELL_SIZE = 52;
    private static final int CHARACTER_CARD_WIDTH = 300;
    private static final int CHARACTER_CARD_HEIGHT = 102;
    private static final int PORTRAIT_SIZE = 62;
    private static final int HP_BAR_WIDTH = 150;
    private static final int HP_BAR_HEIGHT = 12;
    private static final int QUEST_PANEL_WIDTH = 430;
    private static final int QUEST_PANEL_HEIGHT = 190;
    private static final int STATS_PANEL_WIDTH = 320;
    private static final int STATS_PANEL_HEIGHT = 240;
    private static final int STATS_SCROLL_STEP = 24;
    private static final int SKILL_TOOLTIP_WIDTH = 158;
    private static final int SKILL_TOOLTIP_HEIGHT = 54;
    private static final int HELD_ITEM_SLOT_SIZE = 48;
    private static final int HELD_ITEM_ICON_PADDING = 7;
    private static final int HELD_ITEM_CLUSTER_GAP = 24;
    private static final int MESSAGE_FEED_WIDTH = 470;
    private static final int MESSAGE_PANEL_WIDTH = 540;
    private static final int MESSAGE_PANEL_HEIGHT = 310;
    private static final int MESSAGE_ROW_HEIGHT = 22;

    private final BufferedImage bottomPanel = AssetLoader.loadImage(FOOZLE_PATH + "Panel_2.png");
    private final BufferedImage corner = AssetLoader.loadImage(FOOZLE_PATH + "Corner.png");
    private final BufferedImage button = AssetLoader.loadImage(FOOZLE_PATH + "Button.png");
    private final BufferedImage skillPanel = rotateClockwise(AssetLoader.loadImage(FOOZLE_PATH + "Panel_1.png"));
    private final BufferedImage characterCardBackground = AssetLoader.loadImage(MAIN_MENU_PATH + "menu_character_bg.png");
    private final BufferedImage hpBar = AssetLoader.loadImage(BATTLE_UI_PATH + "battle_friend_attack_hp.png");
    private final BufferedImage hpBarBackground = AssetLoader.loadImage(BATTLE_UI_PATH + "battle_friend_attack_statusbg.png");

    private final BufferedImage inventoryIcon = AssetLoader.loadImage(A1_ICON_PATH + "BodyArmor.png");
    private final BufferedImage skillsIcon = AssetLoader.loadImage(A1_ICON_PATH + "Skill.png");
    private final BufferedImage questsIcon = AssetLoader.loadImage(A1_ICON_PATH + "AskAround.png");
    private final BufferedImage statsIcon = AssetLoader.loadImage(A1_ICON_PATH + "MaxHP.png");
    private final BufferedImage escapeIcon = AssetLoader.loadImage(A1_ICON_PATH + "SavePoint.png");

    private final Map<CharacterSkill, BufferedImage> skillIcons = new EnumMap<>(CharacterSkill.class);
    private final PaperDollRenderer paperDollRenderer = new PaperDollRenderer();
    private final Rectangle inventoryButtonBounds = new Rectangle();
    private final Rectangle skillsButtonBounds = new Rectangle();
    private final Rectangle questsButtonBounds = new Rectangle();
    private final Rectangle statsButtonBounds = new Rectangle();
    private final Rectangle escapeButtonBounds = new Rectangle();
    private final Map<String, Rectangle> questRowBounds = new HashMap<>();
    private final Map<CharacterSkill, Rectangle> skillCellBounds = new EnumMap<>(CharacterSkill.class);
    private final Rectangle statsPanelBounds = new Rectangle();
    private final Rectangle statsClipBounds = new Rectangle();
    private final Rectangle messageFeedBounds = new Rectangle();
    private final Rectangle messagePanelBounds = new Rectangle();
    private final Rectangle messageCloseBounds = new Rectangle();
    private Point mousePoint = new Point(-1, -1);
    private int statsScrollOffset = 0;
    private int statsContentHeight = 0;
    private boolean messageLogExpanded;
    private int messageLogScroll;

    public OverworldHud() {
        skillIcons.put(CharacterSkill.MINING, AssetLoader.loadImage(SKILL_ICON_PATH + "mining.png"));
        skillIcons.put(CharacterSkill.SMITHING, AssetLoader.loadImage(SKILL_ICON_PATH + "anvil.png"));
        skillIcons.put(CharacterSkill.FISHING, AssetLoader.loadImage(SKILL_ICON_PATH + "fishing-hook.png"));
        skillIcons.put(CharacterSkill.COOKING, AssetLoader.loadImage(SKILL_ICON_PATH + "cooking-pot.png"));
        skillIcons.put(CharacterSkill.BUTCHERING, AssetLoader.loadImage(SKILL_ICON_PATH + "meat-cleaver.png"));
        skillIcons.put(CharacterSkill.GRAFTING, AssetLoader.loadImage(SKILL_ICON_PATH + "grab.png"));
        skillIcons.put(CharacterSkill.ATTACK, AssetLoader.loadImage(SKILL_ICON_PATH + "piercing-sword.png"));
        skillIcons.put(CharacterSkill.STRENGTH, AssetLoader.loadImage(SKILL_ICON_PATH + "biceps.png"));
        skillIcons.put(CharacterSkill.MAGIC_ACCURACY, AssetLoader.loadImage(SKILL_ICON_PATH + "magic-palm.png"));
        skillIcons.put(CharacterSkill.MAGIC_POWER, AssetLoader.loadImage(SKILL_ICON_PATH + "fire-spell-cast.png"));
        skillIcons.put(CharacterSkill.WOODCUTTING, AssetLoader.loadImage(SKILL_ICON_PATH + "axe-in-log.png"));
        skillIcons.put(CharacterSkill.CRAFTING, AssetLoader.loadImage(SKILL_ICON_PATH + "froe-and-mallet.png"));
        skillIcons.put(CharacterSkill.DEFENSE, AssetLoader.loadImage(SKILL_ICON_PATH + "shield.png"));
    }

    public int getBottomReservedHeight() {
        return BOTTOM_BAR_HEIGHT;
    }

    public void draw(Graphics2D g, GameState gameState, int width, int height) {
        if (g == null || gameState == null) {
            return;
        }

        calculateButtonBounds(width, height);
        drawBottomBar(g, width, height);
        drawCorners(g, width);
        drawHeldWorldUseItem(g, gameState, width, height);
        drawWorldMessages(g, gameState, width, height);

        if (gameState.isSkillsOpen()) {
            drawSkillsPanel(g, gameState, width, height);
        }

        if (gameState.isInventoryOpen()) {
            drawCharacterStatus(g, gameState, width, height);
        }

        if (gameState.isQuestsOpen()) {
            drawQuestPanel(g, gameState, width, height);
        }

        if (gameState.isStatsOpen()) {
            drawStatsPanel(g, gameState, width, height);
        }

        drawButton(g, inventoryButtonBounds, inventoryIcon);
        drawButton(g, skillsButtonBounds, skillsIcon);
        drawButton(g, questsButtonBounds, questsIcon);
        drawButton(g, statsButtonBounds, statsIcon);
        drawButton(g, escapeButtonBounds, escapeIcon);
    }

    public boolean handleMousePressed(
            Point point,
            GameState gameState,
            int width,
            int height,
            Runnable escapeMenuAction
    ) {
        if (point == null || gameState == null) {
            return false;
        }

        calculateButtonBounds(width, height);

        if (messageLogExpanded) {
            if (messageCloseBounds.contains(point)) {
                messageLogExpanded = false;
                return true;
            }
            if (messagePanelBounds.contains(point)) {
                return true;
            }
        } else if (messageFeedBounds.contains(point)) {
            messageLogExpanded = true;
            messageLogScroll = 0;
            return true;
        }

        if (inventoryButtonBounds.contains(point)) {
            gameState.toggleInventory();
            return true;
        }

        if (skillsButtonBounds.contains(point)) {
            gameState.toggleSkills();
            return true;
        }

        if (questsButtonBounds.contains(point)) {
            gameState.toggleQuests();
            return true;
        }

        if (statsButtonBounds.contains(point)) {
            gameState.toggleStats();
            return true;
        }

        if (gameState.isQuestsOpen() && handleQuestPanelClick(point, gameState, width, height)) {
            return true;
        }

        if (escapeButtonBounds.contains(point)) {
            gameState.closeInventory();
            gameState.closeSkills();
            gameState.closeQuests();
            gameState.closeStats();

            if (escapeMenuAction != null) {
                escapeMenuAction.run();
            }

            return true;
        }

        return false;
    }

    public boolean handleInventoryButtonPressed(Point point, GameState gameState, int width, int height) {
        if (point == null || gameState == null) {
            return false;
        }

        calculateButtonBounds(width, height);

        if (!inventoryButtonBounds.contains(point)) {
            return false;
        }

        gameState.toggleInventory();
        return true;
    }

    public boolean handleMouseMoved(Point point, GameState gameState) {
        if (point == null || gameState == null) {
            return false;
        }

        mousePoint = point;
        return gameState.isSkillsOpen();
    }

    public boolean handleMouseWheelMoved(MouseWheelEvent e, GameState gameState, int width, int height) {
        if (e != null && gameState != null && messageLogExpanded && messagePanelBounds.contains(e.getPoint())) {
            int visibleRows = Math.max(1, (messagePanelBounds.height - 58) / MESSAGE_ROW_HEIGHT);
            int maximum = Math.max(0, gameState.getWorldMessageLog().entries().size() - visibleRows);
            messageLogScroll = Math.max(0, Math.min(maximum, messageLogScroll + e.getWheelRotation()));
            return true;
        }
        if (e == null || gameState == null || !gameState.isStatsOpen()) {
            return false;
        }

        Rectangle bounds = calculateStatsPanelBounds(width, height);

        if (!bounds.contains(e.getPoint())) {
            return false;
        }

        int clipHeight = Math.max(1, STATS_PANEL_HEIGHT - 58);
        int maxScroll = Math.max(0, statsContentHeight - clipHeight);

        if (maxScroll <= 0) {
            statsScrollOffset = 0;
            return true;
        }

        statsScrollOffset = clamp(statsScrollOffset + e.getWheelRotation() * STATS_SCROLL_STEP, 0, maxScroll);
        return true;
    }

    private void drawWorldMessages(Graphics2D g, GameState gameState, int width, int height) {
        if (gameState.getWorldMessageLog().entries().isEmpty()) {
            messageLogExpanded = false;
            messageFeedBounds.setBounds(0, 0, 0, 0);
            messagePanelBounds.setBounds(0, 0, 0, 0);
            return;
        }
        if (messageLogExpanded) {
            drawExpandedWorldMessages(g, gameState, width, height);
            messageFeedBounds.setBounds(0, 0, 0, 0);
            return;
        }

        List<WorldMessageLog.Message> messages = gameState.getWorldMessageLog().recent(4);
        int feedWidth = Math.min(MESSAGE_FEED_WIDTH, Math.max(240, width - 36));
        int feedHeight = messages.size() * MESSAGE_ROW_HEIGHT + 32;
        int x = 18;
        int y = Math.max(18, height - BOTTOM_BAR_HEIGHT - feedHeight - 12);
        messageFeedBounds.setBounds(x, y, feedWidth, feedHeight);

        Composite previousComposite = g.getComposite();
        Font previousFont = g.getFont();
        g.setComposite(AlphaComposite.SrcOver.derive(0.82f));
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRoundRect(x, y, 126, 22, 8, 8);
        g.setColor(new Color(205, 215, 232));
        g.setFont(previousFont.deriveFont(Font.BOLD, 12f));
        g.drawString("Messages (" + gameState.getWorldMessageLog().entries().size() + ")", x + 8, y + 15);
        g.setFont(previousFont.deriveFont(Font.PLAIN, 13f));
        for (int index = 0; index < messages.size(); index++) {
            WorldMessageLog.Message message = messages.get(index);
            long ageMs = gameState.getWorldMessageLog().ageMs(message);
            float alpha = ageMs <= 6_000L
                    ? 1.0f
                    : Math.max(0.0f, 1.0f - (ageMs - 6_000L) / 2_000.0f);
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            int rowY = y + 28 + index * MESSAGE_ROW_HEIGHT;
            g.setColor(new Color(0, 0, 0, 165));
            g.fillRoundRect(x, rowY, feedWidth, MESSAGE_ROW_HEIGHT - 2, 8, 8);
            g.setColor(messageColor(message.category()));
            g.drawString(fitMessage(g, message.displayText(), feedWidth - 18), x + 9, rowY + 15);
        }
        g.setComposite(previousComposite);
        g.setFont(previousFont);
    }

    private void drawExpandedWorldMessages(Graphics2D g, GameState gameState, int width, int height) {
        int panelWidth = Math.min(MESSAGE_PANEL_WIDTH, Math.max(300, width - 36));
        int panelHeight = Math.min(MESSAGE_PANEL_HEIGHT, Math.max(180, height - BOTTOM_BAR_HEIGHT - 36));
        int x = 18;
        int y = Math.max(18, height - BOTTOM_BAR_HEIGHT - panelHeight - 12);
        messagePanelBounds.setBounds(x, y, panelWidth, panelHeight);
        messageCloseBounds.setBounds(x + panelWidth - 34, y + 10, 22, 22);

        g.setColor(new Color(12, 16, 23, 230));
        g.fillRoundRect(x, y, panelWidth, panelHeight, 12, 12);
        g.setColor(new Color(180, 196, 220, 210));
        g.drawRoundRect(x, y, panelWidth, panelHeight, 12, 12);
        Font previousFont = g.getFont();
        g.setFont(previousFont.deriveFont(Font.BOLD, 15f));
        g.setColor(Color.WHITE);
        g.drawString("World Messages", x + 14, y + 27);
        g.drawString("X", messageCloseBounds.x + 5, messageCloseBounds.y + 16);

        List<WorldMessageLog.Message> entries = gameState.getWorldMessageLog().entries();
        int visibleRows = Math.max(1, (panelHeight - 58) / MESSAGE_ROW_HEIGHT);
        int maximumScroll = Math.max(0, entries.size() - visibleRows);
        messageLogScroll = Math.max(0, Math.min(maximumScroll, messageLogScroll));
        int end = Math.max(0, entries.size() - messageLogScroll);
        int start = Math.max(0, end - visibleRows);

        g.setFont(previousFont.deriveFont(Font.PLAIN, 13f));
        int rowY = y + 50;
        for (int index = start; index < end; index++) {
            WorldMessageLog.Message message = entries.get(index);
            g.setColor(messageColor(message.category()));
            g.drawString(fitMessage(g, message.displayText(), panelWidth - 30), x + 14, rowY + 15);
            rowY += MESSAGE_ROW_HEIGHT;
        }
        g.setFont(previousFont);
    }

    private Color messageColor(WorldMessageLog.Category category) {
        if (category == null) {
            return new Color(225, 230, 238);
        }
        return switch (category) {
            case SUCCESS -> new Color(132, 235, 142);
            case FAILURE -> new Color(218, 193, 132);
            case WARNING -> new Color(255, 130, 120);
            case SPEECH -> new Color(255, 220, 126);
            case SYSTEM -> new Color(215, 225, 242);
        };
    }

    private String fitMessage(Graphics2D g, String text, int width) {
        String safeText = text == null ? "" : text;
        if (g.getFontMetrics().stringWidth(safeText) <= width) {
            return safeText;
        }
        String ellipsis = "...";
        int end = safeText.length();
        while (end > 0 && g.getFontMetrics().stringWidth(safeText.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return safeText.substring(0, end) + ellipsis;
    }

    private void calculateButtonBounds(int width, int height) {
        int y = height - BOTTOM_BAR_HEIGHT + (BOTTOM_BAR_HEIGHT - BUTTON_SIZE) / 2;
        int centerX = Math.max(BUTTON_SIZE, width / 2);

        setCenteredBounds(inventoryButtonBounds, centerX - FIVE_BUTTON_GAP * 2, y, BUTTON_SIZE);
        setCenteredBounds(skillsButtonBounds, centerX - FIVE_BUTTON_GAP, y, BUTTON_SIZE);
        setCenteredBounds(questsButtonBounds, centerX, y, BUTTON_SIZE);
        setCenteredBounds(statsButtonBounds, centerX + FIVE_BUTTON_GAP, y, BUTTON_SIZE);
        setCenteredBounds(escapeButtonBounds, centerX + FIVE_BUTTON_GAP * 2, y, BUTTON_SIZE);
    }

    private void setCenteredBounds(Rectangle bounds, int centerX, int y, int size) {
        bounds.setBounds(centerX - size / 2, y, size, size);
    }

    private void drawBottomBar(Graphics2D g, int width, int height) {
        int y = height - BOTTOM_BAR_HEIGHT;
        int halfWidth = Math.max(1, width / 2);

        drawImage(g, bottomPanel, -BOTTOM_BAR_OVERSCAN, y, halfWidth + BOTTOM_BAR_OVERSCAN, BOTTOM_BAR_HEIGHT + BOTTOM_BAR_OVERSCAN, true, true);
        drawImage(g, bottomPanel, halfWidth, y, width - halfWidth + BOTTOM_BAR_OVERSCAN, BOTTOM_BAR_HEIGHT + BOTTOM_BAR_OVERSCAN, false, true);
    }

    private void drawCorners(Graphics2D g, int width) {
        drawImage(g, corner, 0, 0, CORNER_SIZE, CORNER_SIZE, false);
        drawImage(g, corner, width - CORNER_SIZE, 0, CORNER_SIZE, CORNER_SIZE, true);
    }

    private void drawButton(Graphics2D g, Rectangle bounds, BufferedImage icon) {
        drawImage(g, button, bounds.x, bounds.y, bounds.width, bounds.height, false);

        int iconX = bounds.x + (bounds.width - BUTTON_ICON_SIZE) / 2;
        int iconY = bounds.y + (bounds.height - BUTTON_ICON_SIZE) / 2;
        drawImage(g, icon, iconX, iconY, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE, false);
    }

    private void drawHeldWorldUseItem(Graphics2D g, GameState gameState, int width, int height) {
        InventorySystem.Item heldItem = gameState.getSelectedWorldUseItem();

        if (heldItem == null) {
            return;
        }

        int y = height - BOTTOM_BAR_HEIGHT + (BOTTOM_BAR_HEIGHT - HELD_ITEM_SLOT_SIZE) / 2;
        int x = Math.max(
                14,
                inventoryButtonBounds.x - HELD_ITEM_CLUSTER_GAP - HELD_ITEM_SLOT_SIZE
        );

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(0.88f));
        g.setColor(new Color(8, 9, 13));
        g.fillRoundRect(x, y, HELD_ITEM_SLOT_SIZE, HELD_ITEM_SLOT_SIZE, 8, 8);
        g.setComposite(oldComposite);

        g.setColor(new Color(226, 194, 104));
        g.drawRoundRect(x, y, HELD_ITEM_SLOT_SIZE, HELD_ITEM_SLOT_SIZE, 8, 8);
        g.setColor(new Color(90, 68, 36));
        g.drawRoundRect(x + 2, y + 2, HELD_ITEM_SLOT_SIZE - 4, HELD_ITEM_SLOT_SIZE - 4, 6, 6);

        BufferedImage icon = heldItem.getIcon();
        if (icon != null) {
            drawImage(
                    g,
                    icon,
                    x + HELD_ITEM_ICON_PADDING,
                    y + HELD_ITEM_ICON_PADDING,
                    HELD_ITEM_SLOT_SIZE - HELD_ITEM_ICON_PADDING * 2,
                    HELD_ITEM_SLOT_SIZE - HELD_ITEM_ICON_PADDING * 2,
                    false
            );
        } else {
            String label = heldItem.getName() == null || heldItem.getName().isBlank()
                    ? "?"
                    : heldItem.getName().substring(0, 1).toUpperCase();
            Font oldFont = g.getFont();
            g.setFont(oldFont.deriveFont(Font.BOLD, 18f));
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(new Color(238, 228, 190));
            g.drawString(
                    label,
                    x + (HELD_ITEM_SLOT_SIZE - metrics.stringWidth(label)) / 2,
                    y + (HELD_ITEM_SLOT_SIZE - metrics.getHeight()) / 2 + metrics.getAscent()
            );
            g.setFont(oldFont);
        }

        g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
        g.setColor(new Color(246, 236, 176));
        g.drawString("USE", x + 13, y - 3);
    }

    private void drawSkillsPanel(Graphics2D g, GameState gameState, int width, int height) {
        int x = Math.max(18, width - SKILL_PANEL_WIDTH - 28);
        int y = Math.max(60, height - BOTTOM_BAR_HEIGHT - SKILL_PANEL_HEIGHT - 20);
        skillCellBounds.clear();

        drawImage(g, skillPanel, x, y, SKILL_PANEL_WIDTH, SKILL_PANEL_HEIGHT, false);

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(0.72f));
        g.setColor(new Color(10, 12, 18, 185));
        g.fillRoundRect(x + 22, y + 22, SKILL_PANEL_WIDTH - 44, SKILL_PANEL_HEIGHT - 44, 8, 8);
        g.setComposite(oldComposite);

        int columns = 2;
        int startX = x + 52;
        int startY = y + 40;
        int index = 0;

        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics metrics = g.getFontMetrics();

        for (CharacterSkill skill : CharacterSkill.values()) {
            int column = index % columns;
            int row = index / columns;
            int cellX = startX + column * 88;
            int cellY = startY + row * SKILL_CELL_SIZE;
            int level = Math.min(99, Math.max(1, gameState.getPlayerCharacter().getSkillLevel(skill)));

            skillCellBounds.put(skill, new Rectangle(cellX - 6, cellY - 6, 68, 46));
            drawSkillCell(g, skillIcons.get(skill), cellX, cellY, level, metrics);
            index++;
        }

        drawSkillTooltip(g, gameState, width, height);
    }

    private void drawCharacterStatus(Graphics2D g, GameState gameState, int width, int height) {
        PlayerCharacter playerCharacter = gameState.getPlayerCharacter();
        int x = 18;
        int y = Math.max(62, height - BOTTOM_BAR_HEIGHT - CHARACTER_CARD_HEIGHT - 14);

        drawImage(g, characterCardBackground, x, y, CHARACTER_CARD_WIDTH, CHARACTER_CARD_HEIGHT, false);

        Rectangle portraitBounds = new Rectangle(x + 22, y + 20, PORTRAIT_SIZE, PORTRAIT_SIZE);
        drawPaperDoll(g, playerCharacter, portraitBounds);

        g.setFont(g.getFont().deriveFont(Font.BOLD, 21f));
        g.setColor(new Color(248, 238, 205));
        g.drawString(playerCharacter.getName(), x + 102, y + 36);

        int hpX = x + 112;
        int hpY = y + 58;
        int hpWidth = HP_BAR_WIDTH;
        double hpPercent = playerCharacter.getMaxHp() <= 0
                ? 0.0
                : (double) playerCharacter.getCurrHp() / (double) playerCharacter.getMaxHp();
        int fillWidth = (int) Math.round(hpWidth * Math.max(0.0, Math.min(1.0, hpPercent)));

        drawImage(g, hpBarBackground, hpX, hpY, hpWidth, HP_BAR_HEIGHT, false);

        if (fillWidth > 0) {
            Graphics2D clipped = (Graphics2D) g.create();
            clipped.setClip(hpX, hpY, fillWidth, HP_BAR_HEIGHT);
            drawImage(clipped, hpBar, hpX, hpY, hpWidth, HP_BAR_HEIGHT, false);
            clipped.dispose();
        }

        String hpText = playerCharacter.getCurrHp() + "/" + playerCharacter.getMaxHp();
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(new Color(12, 18, 12));
        g.drawString(hpText, hpX + 48, hpY + 11);
        g.setColor(new Color(120, 245, 91));
        g.drawString(hpText, hpX + 47, hpY + 10);
    }

    private void drawQuestPanel(Graphics2D g, GameState gameState, int width, int height) {
        int x = Math.max(18, width - QUEST_PANEL_WIDTH - 28);
        int y = Math.max(62, height - BOTTOM_BAR_HEIGHT - QUEST_PANEL_HEIGHT - 16);

        g.setColor(new Color(8, 9, 13, 220));
        g.fillRoundRect(x, y, QUEST_PANEL_WIDTH, QUEST_PANEL_HEIGHT, 8, 8);
        g.setColor(new Color(112, 92, 58));
        g.drawRoundRect(x, y, QUEST_PANEL_WIDTH, QUEST_PANEL_HEIGHT, 8, 8);

        g.setFont(g.getFont().deriveFont(Font.BOLD, 17f));
        g.setColor(new Color(238, 228, 190));
        g.drawString("Quests", x + 18, y + 28);

        int rowY = y + 52;
        int selectedStage = 0;
        GameState.QuestDefinition selectedQuest = null;

        for (GameState.QuestDefinition quest : gameState.getQuestDefinitions()) {
            int stage = gameState.getQuestStagesView().getOrDefault(quest.id(), 0);
            boolean selected = quest.id().equals(gameState.getSelectedQuestId());

            if (selected || selectedQuest == null) {
                selectedQuest = quest;
                selectedStage = stage;
            }

            Rectangle rowBounds = new Rectangle(x + 18, rowY - 16, 168, 24);
            questRowBounds.put(quest.id(), rowBounds);

            g.setColor(selected ? new Color(42, 44, 52, 210) : new Color(0, 0, 0, 0));
            g.fillRoundRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, 4, 4);

            g.setColor(questColor(quest, stage));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
            g.drawString(quest.displayName(), x + 26, rowY);
            rowY += 28;
        }

        if (selectedQuest == null) {
            return;
        }

        int textX = x + 205;
        int textY = y + 56;

        g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
        g.setColor(new Color(238, 228, 190));
        g.drawString(selectedQuest.displayName(), textX, textY);

        g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
        g.setColor(new Color(210, 204, 178));
        drawWrappedText(
                g,
                selectedQuest.stageDescription(selectedStage),
                textX,
                textY + 26,
                QUEST_PANEL_WIDTH - 225,
                18
        );
    }

    private void drawStatsPanel(Graphics2D g, GameState gameState, int width, int height) {
        PlayerCharacter player = gameState.getPlayerCharacter();
        Rectangle bounds = calculateStatsPanelBounds(width, height);
        statsPanelBounds.setBounds(bounds);
        int x = bounds.x;
        int y = bounds.y;

        g.setColor(new Color(8, 9, 13, 220));
        g.fillRoundRect(x, y, STATS_PANEL_WIDTH, STATS_PANEL_HEIGHT, 8, 8);
        g.setColor(new Color(112, 92, 58));
        g.drawRoundRect(x, y, STATS_PANEL_WIDTH, STATS_PANEL_HEIGHT, 8, 8);

        g.setFont(g.getFont().deriveFont(Font.BOLD, 17f));
        g.setColor(new Color(238, 228, 190));
        g.drawString(player.getName(), x + 18, y + 28);

        List<String> statLines = new ArrayList<>();
        DifficultyResolver.DifficultyRating buildRating = DifficultyResolver.ratePlayer(player);
        statLines.add("Starting Region");
        statLines.add(player.getPlayerRegion() == null ? "Unknown" : player.getPlayerRegion().getDisplayName());
        statLines.add("B Level " + buildRating.level());
        statLines.add("------------------");
        statLines.add("Stats");
        statLines.add("HP " + player.getCurrHp() + "/" + player.getMaxHp());
        for (PlayerStat stat : PlayerStat.values()) {
            statLines.add(stat.getDisplayName() + " " + player.getStat(stat));
        }
        statLines.add("------------------");
        statLines.add("Combined Stats");
        statLines.add("Melee Accuracy " + player.getMeleeAccuracy());
        statLines.add("Melee Power " + player.getMeleePower());
        statLines.add("Defense Roll " + player.getDefenseRoll());
        statLines.add("Spellcasting " + player.getSpellcasting());
        statLines.add("Spell Potency " + player.getSpellPotency());

        Rectangle clipBounds = new Rectangle(x + 16, y + 42, STATS_PANEL_WIDTH - 34, STATS_PANEL_HEIGHT - 58);
        statsClipBounds.setBounds(clipBounds);
        int lineHeight = 18;
        statsContentHeight = statLines.size() * lineHeight + 10;
        int maxScroll = Math.max(0, statsContentHeight - clipBounds.height);
        statsScrollOffset = clamp(statsScrollOffset, 0, maxScroll);

        Graphics2D clipped = (Graphics2D) g.create();
        clipped.setClip(clipBounds);
        clipped.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
        clipped.setColor(new Color(210, 204, 178));

        int lineY = clipBounds.y + 16 - statsScrollOffset;
        for (String line : statLines) {
            clipped.drawString(line, clipBounds.x + 2, lineY);
            lineY += lineHeight;
        }
        clipped.dispose();

        drawStatsScrollbar(g, clipBounds, maxScroll);
    }

    private void drawSkillTooltip(Graphics2D g, GameState gameState, int width, int height) {
        CharacterSkill hoveredSkill = getHoveredSkill();

        if (hoveredSkill == null) {
            return;
        }

        PlayerCharacter player = gameState.getPlayerCharacter();
        int experience = player.getSkillExperience(hoveredSkill);
        int required = player.getSkillExperienceRequired(hoveredSkill);
        int remaining = Math.max(0, required - experience);

        int tooltipX = clamp(mousePoint.x + 14, 10, width - SKILL_TOOLTIP_WIDTH - 10);
        int tooltipY = clamp(mousePoint.y + 14, 10, height - BOTTOM_BAR_HEIGHT - SKILL_TOOLTIP_HEIGHT - 8);

        g.setColor(new Color(7, 8, 12, 235));
        g.fillRoundRect(tooltipX, tooltipY, SKILL_TOOLTIP_WIDTH, SKILL_TOOLTIP_HEIGHT, 7, 7);
        g.setColor(new Color(126, 105, 65));
        g.drawRoundRect(tooltipX, tooltipY, SKILL_TOOLTIP_WIDTH, SKILL_TOOLTIP_HEIGHT, 7, 7);

        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(new Color(246, 236, 176));
        g.drawString(hoveredSkill.getDisplayName(), tooltipX + 10, tooltipY + 18);

        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        g.setColor(new Color(210, 204, 178));
        g.drawString("XP " + experience + "/" + required, tooltipX + 10, tooltipY + 35);
        g.drawString("Left " + remaining, tooltipX + 10, tooltipY + 49);
    }

    private CharacterSkill getHoveredSkill() {
        for (Map.Entry<CharacterSkill, Rectangle> entry : skillCellBounds.entrySet()) {
            if (entry.getValue().contains(mousePoint)) {
                return entry.getKey();
            }
        }

        return null;
    }

    private Rectangle calculateStatsPanelBounds(int width, int height) {
        return new Rectangle(
                22,
                Math.max(62, height - BOTTOM_BAR_HEIGHT - STATS_PANEL_HEIGHT - 16),
                STATS_PANEL_WIDTH,
                STATS_PANEL_HEIGHT
        );
    }

    private void drawStatsScrollbar(Graphics2D g, Rectangle clipBounds, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }

        int trackX = clipBounds.x + clipBounds.width - 6;
        int trackY = clipBounds.y + 4;
        int trackHeight = clipBounds.height - 8;
        int thumbHeight = Math.max(24, (int) Math.round(trackHeight * (clipBounds.height / (double) statsContentHeight)));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = trackY + (int) Math.round(thumbTravel * (statsScrollOffset / (double) maxScroll));

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(trackX, trackY, 4, trackHeight, 4, 4);
        g.setColor(new Color(210, 204, 178, 190));
        g.fillRoundRect(trackX, thumbY, 4, thumbHeight, 4, 4);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean handleQuestPanelClick(Point point, GameState gameState, int width, int height) {
        drawQuestPanelLayoutOnly(gameState, width, height);

        for (Map.Entry<String, Rectangle> entry : questRowBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                gameState.setSelectedQuestId(entry.getKey());
                return true;
            }
        }

        return false;
    }

    private void drawQuestPanelLayoutOnly(GameState gameState, int width, int height) {
        questRowBounds.clear();
        int x = Math.max(18, width - QUEST_PANEL_WIDTH - 28);
        int y = Math.max(62, height - BOTTOM_BAR_HEIGHT - QUEST_PANEL_HEIGHT - 16);
        int rowY = y + 52;

        for (GameState.QuestDefinition quest : gameState.getQuestDefinitions()) {
            questRowBounds.put(quest.id(), new Rectangle(x + 18, rowY - 16, 168, 24));
            rowY += 28;

            if (gameState.getSelectedQuestId() == null) {
                gameState.setSelectedQuestId(quest.id());
            }
        }
    }

    private Color questColor(GameState.QuestDefinition quest, int stage) {
        if (quest.isComplete(stage)) {
            return new Color(92, 225, 112);
        }

        if (stage > 0) {
            return new Color(245, 166, 72);
        }

        return new Color(224, 74, 74);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isBlank()) {
            return;
        }

        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();

        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;

            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g.drawString(line.toString(), x, y);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }

        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, y);
        }
    }

    private void drawPaperDoll(Graphics2D g, PlayerCharacter playerCharacter, Rectangle bounds) {
        g.setColor(new Color(17, 18, 21));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(new Color(80, 82, 88));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        BufferedImage paperDoll = playerCharacter == null ? null : paperDollRenderer.render(playerCharacter);
        if (paperDoll != null) {
            int size = Math.min(bounds.width, bounds.height);
            drawImage(g, paperDoll, bounds.x + (bounds.width - size) / 2, bounds.y + (bounds.height - size) / 2, size, size, false);
            return;
        }

        g.setColor(new Color(175, 175, 175));
        g.fillOval(bounds.x + 18, bounds.y + 10, 26, 26);
        g.fillRoundRect(bounds.x + 12, bounds.y + 36, 38, 20, 14, 14);
    }

    private void drawSkillCell(Graphics2D g, BufferedImage icon, int x, int y, int level, FontMetrics metrics) {
        g.setColor(new Color(235, 218, 158));
        g.drawRoundRect(x - 4, y - 4, SKILL_ICON_SIZE + 8, SKILL_ICON_SIZE + 8, 6, 6);
        drawImage(g, icon, x, y, SKILL_ICON_SIZE, SKILL_ICON_SIZE, false);

        String levelText = String.valueOf(level);
        int textX = x + SKILL_ICON_SIZE + 8;
        int textY = y + (SKILL_ICON_SIZE + metrics.getAscent() - metrics.getDescent()) / 2;

        g.setColor(new Color(15, 15, 18));
        g.drawString(levelText, textX + 1, textY + 1);
        g.setColor(new Color(246, 236, 176));
        g.drawString(levelText, textX, textY);
    }

    private void drawImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height, boolean flipHorizontal) {
        drawImage(g, image, x, y, width, height, flipHorizontal, false);
    }

    private void drawImage(
            Graphics2D g,
            BufferedImage image,
            int x,
            int y,
            int width,
            int height,
            boolean flipHorizontal,
            boolean flipVertical
    ) {
        if (image == null || width <= 0 || height <= 0) {
            return;
        }

        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int drawX = flipHorizontal ? x + width : x;
        int drawY = flipVertical ? y + height : y;
        int drawWidth = flipHorizontal ? -width : width;
        int drawHeight = flipVertical ? -height : height;

        g.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);

        if (oldInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    private BufferedImage rotateClockwise(BufferedImage source) {
        if (source == null) {
            return null;
        }

        BufferedImage rotated = new BufferedImage(source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotated.createGraphics();
        g.translate(source.getHeight(), 0);
        g.rotate(Math.toRadians(90));
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return rotated;
    }
}

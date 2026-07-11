package org.main.core;

import org.main.engine.AssetLoader;
import org.main.content.QuestLibrary;

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
    private static final String SKILL_ICON_PATH = "assets/images/ui/custom/skills/";
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
    private static final int SKILL_PANEL_WIDTH = 210;
    private static final int SKILL_PANEL_HEIGHT = 220;
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
    private final Map<String, BufferedImage> portraitCache = new HashMap<>();
    private final Rectangle inventoryButtonBounds = new Rectangle();
    private final Rectangle skillsButtonBounds = new Rectangle();
    private final Rectangle questsButtonBounds = new Rectangle();
    private final Rectangle statsButtonBounds = new Rectangle();
    private final Rectangle escapeButtonBounds = new Rectangle();
    private final Map<QuestLibrary, Rectangle> questRowBounds = new EnumMap<>(QuestLibrary.class);
    private final Map<CharacterSkill, Rectangle> skillCellBounds = new EnumMap<>(CharacterSkill.class);
    private final Rectangle statsPanelBounds = new Rectangle();
    private final Rectangle statsClipBounds = new Rectangle();
    private Point mousePoint = new Point(-1, -1);
    private int statsScrollOffset = 0;
    private int statsContentHeight = 0;

    public OverworldHud() {
        skillIcons.put(CharacterSkill.MINING, AssetLoader.loadImage(SKILL_ICON_PATH + "mining.png"));
        skillIcons.put(CharacterSkill.SMITHING, AssetLoader.loadImage(SKILL_ICON_PATH + "anvil.png"));
        skillIcons.put(CharacterSkill.FISHING, AssetLoader.loadImage(SKILL_ICON_PATH + "fishing-hook.png"));
        skillIcons.put(CharacterSkill.COOKING, AssetLoader.loadImage(SKILL_ICON_PATH + "cooking-pot.png"));
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

    public boolean handleMouseMoved(Point point, GameState gameState) {
        if (point == null || gameState == null) {
            return false;
        }

        mousePoint = point;
        return gameState.isSkillsOpen();
    }

    public boolean handleMouseWheelMoved(MouseWheelEvent e, GameState gameState, int width, int height) {
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
        int startX = x + 44;
        int startY = y + 40;
        int index = 0;

        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics metrics = g.getFontMetrics();

        for (CharacterSkill skill : CharacterSkill.values()) {
            int column = index % columns;
            int row = index / columns;
            int cellX = startX + column * 74;
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
        drawPortrait(g, playerCharacter.getPortraitPath(), portraitBounds);

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
        QuestLibrary selectedQuest = null;

        for (QuestLibrary quest : QuestLibrary.values()) {
            int stage = gameState.getQuestStage(quest);
            boolean selected = quest.getId().equals(gameState.getSelectedQuestId());

            if (selected || selectedQuest == null) {
                selectedQuest = quest;
                selectedStage = stage;
            }

            Rectangle rowBounds = new Rectangle(x + 18, rowY - 16, 168, 24);
            questRowBounds.put(quest, rowBounds);

            g.setColor(selected ? new Color(42, 44, 52, 210) : new Color(0, 0, 0, 0));
            g.fillRoundRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, 4, 4);

            g.setColor(questColor(quest, stage));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
            g.drawString(quest.getDisplayName(), x + 26, rowY);
            rowY += 28;
        }

        if (selectedQuest == null) {
            return;
        }

        int textX = x + 205;
        int textY = y + 56;

        g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
        g.setColor(new Color(238, 228, 190));
        g.drawString(selectedQuest.getDisplayName(), textX, textY);

        g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
        g.setColor(new Color(210, 204, 178));
        drawWrappedText(
                g,
                selectedQuest.getStageDescription(selectedStage),
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
        statLines.add("Class " + (player.getPlayerClass() == null ? "Unknown" : player.getPlayerClass().getDisplayName()));
        statLines.add("Level " + player.getLevel() + "  XP " + player.getClassExperience() + "/" + player.getClassExperienceRequiredForNextLevel());
        statLines.add("HP " + player.getCurrHp() + "/" + player.getMaxHp());
        statLines.add("Attack " + (5 + player.getStat(PlayerStat.STRENGTH) + player.getInventory().getWeaponStatBonus()));
        statLines.add("Defense " + (player.getStat(PlayerStat.DEFENSE) + player.getInventory().getArmorStatBonus()));
        for (PlayerStat stat : PlayerStat.values()) {
            statLines.add(stat.getDisplayName() + " " + player.getStat(stat));
        }

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

        for (Map.Entry<QuestLibrary, Rectangle> entry : questRowBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                gameState.setSelectedQuestId(entry.getKey().getId());
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

        for (QuestLibrary quest : QuestLibrary.values()) {
            questRowBounds.put(quest, new Rectangle(x + 18, rowY - 16, 168, 24));
            rowY += 28;

            if (gameState.getSelectedQuestId() == null) {
                gameState.setSelectedQuestId(quest.getId());
            }
        }
    }

    private Color questColor(QuestLibrary quest, int stage) {
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

    private void drawPortrait(Graphics2D g, String portraitPath, Rectangle bounds) {
        BufferedImage portrait = loadPortrait(portraitPath);

        g.setColor(new Color(17, 18, 21));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(new Color(80, 82, 88));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        if (portrait != null) {
            int size = Math.min(bounds.width, bounds.height);
            drawImage(g, portrait, bounds.x + (bounds.width - size) / 2, bounds.y + (bounds.height - size) / 2, size, size, false);
            return;
        }

        g.setColor(new Color(175, 175, 175));
        g.fillOval(bounds.x + 18, bounds.y + 10, 26, 26);
        g.fillRoundRect(bounds.x + 12, bounds.y + 36, 38, 20, 14, 14);
    }

    private BufferedImage loadPortrait(String portraitPath) {
        if (portraitPath == null || portraitPath.isBlank()) {
            return null;
        }

        if (!portraitCache.containsKey(portraitPath)) {
            portraitCache.put(portraitPath, AssetLoader.loadImage(portraitPath));
        }

        return portraitCache.get(portraitPath);
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

package org.main.battle;

import org.main.core.Library;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class BattleRenderer {

    private static final int PANEL_PADDING = 24;
    private static final int BUTTON_GAP = 8;

    private static final int MESSAGE_BOX_HEIGHT = 44;
    private static final int MESSAGE_BOX_MARGIN = 20;
    private static final int MESSAGE_BOX_MAX_WIDTH = 520;

    private final Map<Library.BattleCommand, Rectangle> commandBounds = new EnumMap<>(Library.BattleCommand.class);
    private final Map<BattleActor, Rectangle> actorBounds = new IdentityHashMap<>();
    private final Set<BattleActor> selectableTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<BattleSkill, Rectangle> skillBounds = new IdentityHashMap<>();

    private BattleSkill previewSkill = null;
    private Point mousePoint = null;
    private boolean skillWindowOpen = false;
    private Rectangle skillWindowCloseBounds = new Rectangle();

    private BattleAssets assets;

    private static final double BUTTON_WIDTH_RATIO = 1.0;

    public Library.BattleCommand getCommandAt(Point point) {
        for (Map.Entry<Library.BattleCommand, Rectangle> entry : commandBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public void setMousePoint(Point mousePoint) {
        this.mousePoint = mousePoint == null ? null : new Point(mousePoint);
    }

    public void setPreviewSkill(BattleSkill previewSkill) {
        this.previewSkill = previewSkill;
    }

    public void clearPreviewSkill() {
        this.previewSkill = null;
    }

    public void setAssets(BattleAssets assets) {
        this.assets = assets;
    }

    public BattleActor getActorAt(Point point) {
        for (Map.Entry<BattleActor, Rectangle> entry : actorBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public void setSelectableTargets(Collection<BattleActor> targets) {
        selectableTargets.clear();

        if (targets != null) {
            selectableTargets.addAll(targets);
        }
    }

    public void clearSelectableTargets() {
        selectableTargets.clear();
        clearPreviewSkill();
    }

    public void openSkillWindow() {
        skillWindowOpen = true;
    }

    public void closeSkillWindow() {
        skillWindowOpen = false;
        skillWindowCloseBounds = new Rectangle();
        skillBounds.clear();
    }

    public boolean isSkillWindowOpen() {
        return skillWindowOpen;
    }

    public void draw(Graphics2D g, BattleEncounter encounter, int width, int height) {
        actorBounds.clear();
        commandBounds.clear();
        skillBounds.clear();

        if (encounter == null) {
            return;
        }

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        int battleAreaHeight = (int) (height * 0.70);
        int bottomHeight = height - battleAreaHeight;
        int menuWidth = width / 2;

        drawBattleArea(g, encounter, 0, 0, width, battleAreaHeight);

        /*
         * Actor bounds are created inside drawBattleArea(...), so skill/target
         * highlights must be drawn after the battle area.
         */
        drawTargetingPreview(g);

        /*
         * The battle message now floats over the battle area instead of
         * consuming space inside the command panel.
         */
        drawBattleMessageOverlay(g, encounter, 0, 0, width, battleAreaHeight);

        drawCommandMenu(g, 0, battleAreaHeight, menuWidth, bottomHeight);
        drawPartyStatus(g, encounter, menuWidth, battleAreaHeight, width - menuWidth, bottomHeight);

        if (skillWindowOpen) {
            drawSkillWindow(g, encounter, width, height);
        }
    }

    public BattleSkill getSkillAt(Point point) {
        if (!skillWindowOpen) {
            return null;
        }

        for (Map.Entry<BattleSkill, Rectangle> entry : skillBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public boolean isSkillWindowCloseButtonAt(Point point) {
        return skillWindowOpen && skillWindowCloseBounds.contains(point);
    }

    private void drawSkillWindow(Graphics2D g, BattleEncounter encounter, int width, int height) {
        BattleActor actor = getFirstLivingAlly(encounter);
        List<BattleSkill> skills = actor != null ? actor.getSkills() : List.of();

        int windowWidth = Math.min(420, width - 80);
        int windowHeight = Math.min(320, height - 80);

        int windowX = (width - windowWidth) / 2;
        int windowY = (height - windowHeight) / 2;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        g.setComposite(oldComposite);

        g.setColor(new Color(25, 25, 25));
        g.fillRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        g.setColor(Color.WHITE);
        g.drawRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        drawSkillWindowTitle(g, windowX, windowY, windowWidth);
        drawSkillWindowCloseButton(g, windowX, windowY, windowWidth);
        drawSkillRows(g, skills, windowX, windowY, windowWidth, windowHeight);
    }

    private BattleActor getFirstLivingAlly(BattleEncounter encounter) {
        for (BattleActor ally : encounter.getAllies()) {
            if (ally.isAlive()) {
                return ally;
            }
        }

        return null;
    }

    private void drawSkillWindowTitle(Graphics2D g, int windowX, int windowY, int windowWidth) {
        g.setColor(Color.WHITE);

        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, 16f));

        g.drawString("Skills", windowX + 20, windowY + 30);

        g.setFont(oldFont);

        g.setColor(new Color(180, 180, 180));
        g.drawLine(windowX + 16, windowY + 44, windowX + windowWidth - 16, windowY + 44);
    }

    private void drawSkillWindowCloseButton(Graphics2D g, int windowX, int windowY, int windowWidth) {
        int closeSize = 24;

        int closeX = windowX + windowWidth - closeSize - 14;
        int closeY = windowY + 10;

        skillWindowCloseBounds = new Rectangle(closeX, closeY, closeSize, closeSize);

        g.setColor(new Color(60, 60, 60));
        g.fillRect(closeX, closeY, closeSize, closeSize);

        g.setColor(Color.WHITE);
        g.drawRect(closeX, closeY, closeSize, closeSize);

        FontMetrics metrics = g.getFontMetrics();

        String label = "X";

        int textX = closeX + (closeSize - metrics.stringWidth(label)) / 2;
        int textY = closeY
                + (closeSize - metrics.getHeight()) / 2
                + metrics.getAscent();

        g.drawString(label, textX, textY);
    }

    private void drawSkillRows(
            Graphics2D g,
            List<BattleSkill> skills,
            int windowX,
            int windowY,
            int windowWidth,
            int windowHeight
    ) {
        int contentX = windowX + 20;
        int contentY = windowY + 60;
        int contentWidth = windowWidth - 40;

        int rowHeight = 34;
        int rowGap = 8;

        for (int i = 0; i < skills.size(); i++) {
            BattleSkill skill = skills.get(i);

            int rowX = contentX;
            int rowY = contentY + i * (rowHeight + rowGap);

            if (rowY + rowHeight > windowY + windowHeight - 20) {
                break;
            }

            Rectangle rowBounds = new Rectangle(rowX, rowY, contentWidth, rowHeight);
            skillBounds.put(skill, rowBounds);

            g.setColor(new Color(45, 45, 45));
            g.fillRect(rowX, rowY, contentWidth, rowHeight);

            g.setColor(Color.WHITE);
            g.drawRect(rowX, rowY, contentWidth, rowHeight);

            FontMetrics metrics = g.getFontMetrics();

            int textX = rowX + 10;
            int textY = rowY
                    + (rowHeight - metrics.getHeight()) / 2
                    + metrics.getAscent();

            g.drawString(skill.getName(), textX, textY);
        }
    }

    private void drawBattleArea(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
        BufferedImage background = assets != null ? assets.getBattleBackground() : null;

        drawImageOrFill(
                g,
                background,
                x,
                y,
                width,
                height,
                new Color(235, 235, 230)
        );

        drawPanelBorder(g, x, y, width, height);

        int halfWidth = width / 2;

        drawFormation(
                g,
                encounter.getAllies(),
                Library.EntityType.ALLY,
                x,
                y,
                halfWidth,
                height
        );

        drawFormation(
                g,
                encounter.getEnemies(),
                Library.EntityType.ENEMY,
                x + halfWidth,
                y,
                width - halfWidth,
                height
        );
    }

    private void drawFormation(
            Graphics2D g,
            List<BattleActor> actors,
            Library.EntityType side,
            int x,
            int y,
            int width,
            int height
    ) {
        drawBattleRow(g, actors, side, Library.BattleRow.BACK, x, y, width, height);
        drawBattleRow(g, actors, side, Library.BattleRow.FRONT, x, y, width, height);
    }

    private void drawBattleRow(
            Graphics2D g,
            List<BattleActor> actors,
            Library.EntityType side,
            Library.BattleRow row,
            int x,
            int y,
            int width,
            int height
    ) {
        int slots = 3;

        int frontSpriteSize = 64;
        int backSpriteSize = 56;

        int spriteSize = row == Library.BattleRow.FRONT ? frontSpriteSize : backSpriteSize;

        int verticalGap = 24;
        int totalFormationHeight = slots * frontSpriteSize + (slots - 1) * verticalGap;

        int startY = y + (height - totalFormationHeight) / 2;

        int rowGap = 36;
        int formationPadding = 80;

        int backX;
        int frontX;

        if (side == Library.EntityType.ALLY) {
            backX = x + formationPadding;
            frontX = backX + frontSpriteSize + rowGap;
        } else {
            frontX = x + formationPadding;
            backX = frontX + frontSpriteSize + rowGap;
        }

        int columnX = row == Library.BattleRow.FRONT ? frontX : backX;

        for (BattleActor actor : actors) {
            if (actor.getRow() != row) {
                continue;
            }

            if (!actor.isAlive()) {
                continue;
            }

            int slot = actor.getSlot();

            if (slot < 0 || slot >= slots) {
                continue;
            }

            int laneY = startY + slot * (frontSpriteSize + verticalGap);
            int laneCenterY = laneY + frontSpriteSize / 2;

            int drawX = columnX + (frontSpriteSize - spriteSize) / 2;
            int drawY = laneCenterY - spriteSize / 2;

            Rectangle actorRectangle = new Rectangle(drawX, drawY, spriteSize, spriteSize);
            actorBounds.put(actor, actorRectangle);

            drawHpBar(g, drawX, drawY - 18, spriteSize, 10, actor);
            drawStatusIcons(g, actor, drawX, drawY - 34);
            drawActorSprite(g, actor, drawX, drawY, spriteSize, spriteSize);
        }
    }

    private void drawTargetingPreview(Graphics2D g) {
        if (selectableTargets.isEmpty()) {
            return;
        }

        BattleActor hoveredActor = getHoveredSelectableActor();

        /*
         * If this is normal targeting, such as basic Attack,
         * just show all valid selectable targets.
         */
        if (previewSkill == null || hoveredActor == null) {
            for (BattleActor actor : selectableTargets) {
                Rectangle bounds = actorBounds.get(actor);

                if (bounds != null) {
                    drawSelectableTargetHighlight(g, bounds);
                }
            }

            return;
        }

        /*
         * If this is skill targeting and the mouse is hovering over
         * a valid target, show the resolved skill area.
         */
        Set<BattleActor> previewTargets = getPreviewTargetsForSkill(hoveredActor, previewSkill);

        for (BattleActor actor : selectableTargets) {
            Rectangle bounds = actorBounds.get(actor);

            if (bounds == null) {
                continue;
            }

            if (previewTargets.contains(actor)) {
                drawResolvedTargetHighlight(g, bounds);
            } else {
                drawSelectableTargetHighlight(g, bounds);
            }
        }
    }

    private BattleActor getHoveredSelectableActor() {
        if (mousePoint == null) {
            return null;
        }

        BattleActor hoveredActor = getActorAt(mousePoint);

        if (hoveredActor == null) {
            return null;
        }

        if (!selectableTargets.contains(hoveredActor)) {
            return null;
        }

        return hoveredActor;
    }

    private Set<BattleActor> getPreviewTargetsForSkill(BattleActor hoveredActor, BattleSkill skill) {
        Set<BattleActor> previewTargets = Collections.newSetFromMap(new IdentityHashMap<>());

        if (hoveredActor == null || skill == null) {
            return previewTargets;
        }

        for (BattleActor actor : selectableTargets) {


            boolean shouldHighlight = BattleTargetResolver.matchesSkillShape(actor, hoveredActor, skill.getTargetShape());

            if (shouldHighlight) {
                previewTargets.add(actor);
            }
        }

        return previewTargets;
    }

    private void drawSelectableTargetHighlight(Graphics2D g, Rectangle bounds) {
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(Color.YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawRect(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);

        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
    }

    private void drawResolvedTargetHighlight(Graphics2D g, Rectangle bounds) {
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        g.setColor(Color.YELLOW);
        g.fillRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
        g.setColor(Color.ORANGE);
        g.setStroke(new BasicStroke(4));
        g.drawRect(bounds.x - 5, bounds.y - 5, bounds.width + 10, bounds.height + 10);

        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
    }

    private void drawActorSprite(Graphics2D g, BattleActor actor, int x, int y, int width, int height) {
        BufferedImage image = actor.getImage();

        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(actor.isEnemy() ? Color.RED : Color.BLUE);
            g.fillRect(x, y, width, height);

            g.setColor(Color.WHITE);
            g.drawString(actor.getName(), x + 8, y + height / 2);
        }

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
    }

    private void drawCommandMenu(
            Graphics2D g,
            int x,
            int y,
            int width,
            int height
    ) {
        BufferedImage panelImage = assets != null ? assets.getCommandPanelBackground() : null;

        drawImageOrFill(
                g,
                panelImage,
                x,
                y,
                width,
                height,
                Color.WHITE
        );

        drawPanelBorder(g, x, y, width, height);

        BufferedImage buttonImage = assets != null ? assets.getButtonNormal() : null;

        int contentX = x + PANEL_PADDING;
        int contentY = y + PANEL_PADDING;
        int contentWidth = width - PANEL_PADDING * 2;
        int contentHeight = height - PANEL_PADDING * 2;

        Dimension buttonSize = calculateButtonSize(
                buttonImage,
                contentWidth,
                contentHeight,
                3,
                BUTTON_GAP
        );

        int buttonWidth = buttonSize.width;
        int buttonHeight = buttonSize.height;

        drawCommandButton(
                g,
                Library.BattleCommand.ATTACK,
                "Attack",
                new Rectangle(contentX, contentY, buttonWidth, buttonHeight)
        );

        drawCommandButton(
                g,
                Library.BattleCommand.SKILL,
                "Skill",
                new Rectangle(contentX, contentY + buttonHeight + BUTTON_GAP, buttonWidth, buttonHeight)
        );

        drawCommandButton(
                g,
                Library.BattleCommand.RUN,
                "Run",
                new Rectangle(contentX, contentY + (buttonHeight + BUTTON_GAP) * 2, buttonWidth, buttonHeight)
        );
    }

    private Dimension calculateButtonSize(
            BufferedImage buttonImage,
            int availableWidth,
            int availableHeight,
            int buttonCount,
            int gap
    ) {
        int totalGapHeight = gap * Math.max(0, buttonCount - 1);
        int maxButtonHeight = (availableHeight - totalGapHeight) / buttonCount;

        if (buttonImage == null) {
            return new Dimension(
                    Math.max(1, availableWidth),
                    Math.max(1, Math.min(30, maxButtonHeight))
            );
        }

        double imageAspect = buttonImage.getHeight() / (double) buttonImage.getWidth();

        int buttonWidth = (int) Math.round(availableWidth * BUTTON_WIDTH_RATIO);
        int buttonHeight = (int) Math.round(buttonWidth * imageAspect);

        /*
         * If preserving the image ratio makes the buttons too tall,
         * shrink by height instead.
         */
        if (buttonHeight > maxButtonHeight) {
            buttonHeight = Math.max(1, maxButtonHeight);
            buttonWidth = (int) Math.round(buttonHeight / imageAspect);
        }

        return new Dimension(
                Math.max(1, buttonWidth),
                Math.max(1, buttonHeight)
        );
    }

    private void drawCommandButton(
            Graphics2D g,
            Library.BattleCommand command,
            String label,
            Rectangle bounds
    ) {
        commandBounds.put(command, bounds);

        BufferedImage buttonImage = assets != null ? assets.getButtonNormal() : null;

        drawImageOrFill(
                g,
                buttonImage,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                new Color(245, 245, 245)
        );

        g.setColor(Color.BLACK);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        FontMetrics metrics = g.getFontMetrics();

        int textX = bounds.x + 12;
        int textY = bounds.y
                + (bounds.height - metrics.getHeight()) / 2
                + metrics.getAscent();

        g.drawString(label, textX, textY);
    }

    private void drawBattleMessageOverlay(
            Graphics2D g,
            BattleEncounter encounter,
            int x,
            int y,
            int width,
            int height
    ) {
        String message = encounter.getBattleMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        int boxWidth = Math.min(width - MESSAGE_BOX_MARGIN * 2, MESSAGE_BOX_MAX_WIDTH);
        int boxHeight = MESSAGE_BOX_HEIGHT;

        int boxX = x + MESSAGE_BOX_MARGIN;
        int boxY = y + height - boxHeight - MESSAGE_BOX_MARGIN;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f));
        g.setColor(Color.WHITE);
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12);

        g.setComposite(oldComposite);

        FontMetrics metrics = g.getFontMetrics();
        String displayText = trimTextToFit(g, message, boxWidth - 24);

        int textX = boxX + 12;
        int textY = boxY
                + (boxHeight - metrics.getHeight()) / 2
                + metrics.getAscent();

        g.setColor(Color.WHITE);
        g.drawString(displayText, textX, textY);
    }

    private void drawPartyStatus(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
        BufferedImage panelImage = assets != null ? assets.getStatusPanelBackground() : null;

        drawImageOrFill(
                g,
                panelImage,
                x,
                y,
                width,
                height,
                Color.WHITE
        );

        drawPanelBorder(g, x, y, width, height);

        int contentX = x + PANEL_PADDING;
        int contentY = y + PANEL_PADDING;
        int contentWidth = width - PANEL_PADDING * 2;

        int currentY = contentY + 10;

        for (BattleActor ally : encounter.getAllies()) {
            g.setColor(Color.WHITE);
            g.drawString(ally.getName(), contentX, currentY);

            drawHpBar(
                    g,
                    contentX + 100,
                    currentY - 12,
                    contentWidth - 120,
                    12,
                    ally
            );
            drawStatusIcons(g, ally, contentX + 100, currentY + 4);

            currentY += 35;
        }
    }

    private void drawStatusIcons(Graphics2D g, BattleActor actor, int x, int y) {
        int iconSize = 14;
        int gap = 3;
        int index = 0;

        for (BattleStatus status : actor.getStatuses()) {
            BattleStatusType type = status.getType();
            int iconX = x + index * (iconSize + gap);
            BufferedImage icon = type.getIcon();

            if (icon != null) {
                g.drawImage(icon, iconX, y, iconSize, iconSize, null);
            } else {
                g.setColor(new Color(85, 85, 95));
                g.fillRect(iconX, y, iconSize, iconSize);
                g.setColor(Color.WHITE);
                g.drawRect(iconX, y, iconSize, iconSize);
            }

            g.setColor(Color.WHITE);
            g.drawString(String.valueOf(status.getRemainingTurns()), iconX + iconSize - 5, y + iconSize - 2);
            index++;
        }
    }

    private void drawHpBar(Graphics2D g, int x, int y, int width, int height, BattleActor actor) {
        double percent = actor.getCurrentHp() / (double) actor.getMaxHp();
        int filledWidth = (int) Math.round(width * percent);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(x, y, width, height);

        g.setColor(new Color(80, 180, 80));
        g.fillRect(x, y, filledWidth, height);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
    }

    private void drawImageOrFill(
            Graphics2D g,
            BufferedImage image,
            int x,
            int y,
            int width,
            int height,
            Color fallbackColor
    ) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(fallbackColor);
            g.fillRect(x, y, width, height);
        }
    }

    private void drawPanelBorder(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(Color.BLACK);
        g.drawRect(x, y, width - 1, height - 1);
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

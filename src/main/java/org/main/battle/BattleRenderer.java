package org.main.battle;

import org.main.core.Library;
import org.main.core.InventorySystem;
import org.main.core.WeaponType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class BattleRenderer {

    private static final int PANEL_PADDING = 24;
    private static final int COMPACT_PANEL_PADDING = 12;
    private static final int BUTTON_GAP = 8;
    private static final int HUD_MARGIN = 12;

    private static final int MESSAGE_BOX_HEIGHT = 44;
    private static final int MESSAGE_BOX_MARGIN = 20;
    private static final int MESSAGE_BOX_MAX_WIDTH = 520;
    private static final float MODAL_BACKDROP_ALPHA = 0.35f;
    private static final float TARGET_OUTLINE_ALPHA = 0.55f;
    private static final float TARGET_PREVIEW_FILL_ALPHA = 0.28f;
    private static final float TARGET_PREVIEW_OUTLINE_ALPHA = 0.95f;
    private static final float MESSAGE_BACKGROUND_ALPHA = 0.72f;
    private static final float MESSAGE_OVERLAY_ALPHA = 0.90f;

    private final Map<Library.BattleCommand, Rectangle> commandBounds = new EnumMap<>(Library.BattleCommand.class);
    private final Map<BattleActor, Rectangle> actorBounds = new IdentityHashMap<>();
    private Map<BattleActor, Point> projectedActorPositions = Map.of();
    private final Set<BattleActor> selectableTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<BattleSkill, Rectangle> skillBounds = new IdentityHashMap<>();
    private final Map<Integer, Rectangle> itemBounds = new HashMap<>();
    private final List<BattleItemEntry> battleItems = new ArrayList<>();

    private BattleSkill previewSkill = null;
    private Point mousePoint = null;
    private boolean skillWindowOpen = false;
    private boolean itemWindowOpen = false;
    private boolean autoCombatPaused = false;
    private Rectangle skillWindowCloseBounds = new Rectangle();
    private Rectangle itemWindowCloseBounds = new Rectangle();

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

    public void setAutoCombatPaused(boolean autoCombatPaused) {
        this.autoCombatPaused = autoCombatPaused;
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

    public List<BattleActor> getSelectableTargetsView() {
        return List.copyOf(selectableTargets);
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

    public List<BattleSkill> getSkillChoices(BattleEncounter encounter) {
        BattleActor actor = getFirstLivingAlly(encounter);
        return actor == null ? List.of() : List.copyOf(actor.getSkills());
    }

    public void openItemWindow(InventorySystem.Inventory inventory) {
        itemWindowOpen = true;
        battleItems.clear();
        itemBounds.clear();

        if (inventory == null) {
            return;
        }

        for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
            InventorySystem.Item item = inventory.getItem(i);

            if (item != null
                    && item.getItemType() == InventorySystem.ItemType.CONSUMABLE
                    && item.getHealAmount() > 0) {
                battleItems.add(new BattleItemEntry(i, item));
            }
        }
    }

    public void closeItemWindow() {
        itemWindowOpen = false;
        itemWindowCloseBounds = new Rectangle();
        itemBounds.clear();
        battleItems.clear();
    }

    public boolean isItemWindowOpen() {
        return itemWindowOpen;
    }

    public List<BattleItemEntry> getBattleItemsView() {
        return List.copyOf(battleItems);
    }

    public void draw(Graphics2D g, BattleEncounter encounter, int width, int height) {
        actorBounds.clear();
        commandBounds.clear();
        skillBounds.clear();
        itemBounds.clear();

        if (encounter == null) {
            return;
        }

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        BattleHudLayout hud = battleHudLayout(width, height, encounter.getAllies().size());

        drawBattleArea(g, encounter, 0, 0, width, height);

        /*
         * Actor bounds are created inside drawBattleArea(...), so skill/target
         * highlights must be drawn after the battle area.
         */
        drawTargetingPreview(g);

        /*
         * The battle message now floats over the battle area instead of
         * consuming space inside the command panel.
         */
        drawBattleMessageOverlay(g, encounter, 0, 0, width, hud.clearViewBottom());
        drawPausedIndicator(g, width, hud.clearViewBottom());

        drawCommandMenu(g, hud.command().x, hud.command().y, hud.command().width, hud.command().height);
        drawPartyStatus(g, encounter, hud.party().x, hud.party().y, hud.party().width, hud.party().height);

        if (skillWindowOpen) {
            drawSkillWindow(g, encounter, width, height);
        }

        if (itemWindowOpen) {
            drawItemWindow(g, width, height);
        }
    }

    /** Draws controls over the native 3D scene without repainting its first-person area. */
    public void drawLwjglOverlay(Graphics2D g, BattleEncounter encounter, int width, int height) {
        actorBounds.clear(); commandBounds.clear(); skillBounds.clear(); itemBounds.clear();
        if (encounter == null) return;
        BattleHudLayout hud = battleHudLayout(width, height, encounter.getAllies().size());
        if (!selectableTargets.isEmpty()) {
            drawTacticalBattleArea(g, encounter, 0, 0, width, hud.clearViewBottom());
            drawTargetingPreview(g);
        } else {
            drawProjectedActorMarkers(g, encounter);
        }
        drawBattleMessageOverlay(g, encounter, 0, 0, width, hud.clearViewBottom());
        drawPausedIndicator(g, width, hud.clearViewBottom());
        drawCommandMenu(g, hud.command().x, hud.command().y, hud.command().width, hud.command().height);
        drawPartyStatus(g, encounter, hud.party().x, hud.party().y, hud.party().width, hud.party().height);
        if (skillWindowOpen) drawSkillWindow(g, encounter, width, height);
        if (itemWindowOpen) drawItemWindow(g, width, height);
    }

    private BattleHudLayout battleHudLayout(int width, int height, int allyCount) {
        int safeWidth = Math.max(320, width);
        int safeHeight = Math.max(240, height);

        int commandWidth = clamp((int) Math.round(safeWidth * 0.16), 172, 218);
        int commandHeight = clamp((int) Math.round(safeHeight * 0.27), 176, 208);

        int partyColumns = Math.max(1, allyCount) > 3 ? 2 : 1;
        int partyRows = Math.max(1, (Math.max(1, allyCount) + partyColumns - 1) / partyColumns);
        int partyHeight = COMPACT_PANEL_PADDING * 2 + partyRows * 35 + 8;
        int desiredPartyWidth = clamp((int) Math.round(safeWidth * 0.38), 300, 520);
        int availablePartyWidth = safeWidth - HUD_MARGIN * 3 - commandWidth;
        int partyWidth = Math.min(desiredPartyWidth, Math.max(220, availablePartyWidth));

        Rectangle command = new Rectangle(
                HUD_MARGIN,
                Math.max(HUD_MARGIN, height - HUD_MARGIN - commandHeight),
                commandWidth,
                commandHeight);
        Rectangle party = new Rectangle(
                Math.max(HUD_MARGIN, width - HUD_MARGIN - partyWidth),
                HUD_MARGIN,
                partyWidth,
                partyHeight);
        int clearViewBottom = Math.max(
                MESSAGE_BOX_HEIGHT + MESSAGE_BOX_MARGIN * 2,
                command.y);
        return new BattleHudLayout(command, party, clearViewBottom);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public void setProjectedActorPositions(Map<BattleActor, Point> positions) {
        projectedActorPositions = positions == null ? Map.of() : Map.copyOf(positions);
    }

    private void drawProjectedActorMarkers(Graphics2D g, BattleEncounter encounter) {
        Set<BattleActor> activeAttackers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BattleActor> activeTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BattlePresentationDirector.ActionSnapshot action : encounter.getPresentationDirector().snapshots()) {
            activeAttackers.add(action.attacker());
            action.targets().forEach(target -> activeTargets.add(target.target()));
        }
        BattleActor focusedTarget = encounter.getFirstLivingAlly() == null
                ? null : encounter.getFirstLivingAlly().getPreferredAutoAttackTarget();
        for (Map.Entry<BattleActor, Point> entry : projectedActorPositions.entrySet()) {
            BattleActor actor = entry.getKey(); Point point = entry.getValue();
            if (actor == null || point == null || !actor.isAlive()) continue;
            int width = 96, x = point.x - width / 2, y = point.y - 24;
            drawHpBar(g, x, y, width, 9, actor);
            drawAttackChargeBar(g, x, y + 11, width, 4, actor);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            g.setColor(actor.isEnemy() ? new Color(255, 215, 205) : new Color(205, 235, 255));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(actor.getName(), point.x - metrics.stringWidth(actor.getName()) / 2, y - 4);
            if (activeAttackers.contains(actor)) {
                g.setColor(new Color(255, 207, 78, 220));
                g.drawOval(point.x - 19, point.y - 19, 38, 38);
            }
            if (activeTargets.contains(actor) || actor == focusedTarget) {
                g.setColor(actor == focusedTarget ? new Color(255, 92, 82, 235) : new Color(255, 235, 135, 220));
                int markerY = point.y + 10;
                g.fillPolygon(new int[]{point.x - 7, point.x + 7, point.x},
                        new int[]{markerY, markerY, markerY + 10}, 3);
            }
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

    public Integer getBattleItemIndexAt(Point point) {
        if (!itemWindowOpen) {
            return null;
        }

        for (Map.Entry<Integer, Rectangle> entry : itemBounds.entrySet()) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public boolean isItemWindowCloseButtonAt(Point point) {
        return itemWindowOpen && itemWindowCloseBounds.contains(point);
    }

    private void drawSkillWindow(Graphics2D g, BattleEncounter encounter, int width, int height) {
        BattleActor actor = getFirstLivingAlly(encounter);
        List<BattleSkill> skills = actor != null ? actor.getSkills() : List.of();

        int windowWidth = Math.min(420, width - 80);
        int windowHeight = Math.min(320, height - 80);

        int windowX = (width - windowWidth) / 2;
        int windowY = (height - windowHeight) / 2;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MODAL_BACKDROP_ALPHA));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        g.setComposite(oldComposite);

        g.setColor(new Color(25, 25, 25));
        g.fillRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        g.setColor(Color.WHITE);
        g.drawRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        drawSkillWindowTitle(g, windowX, windowY, windowWidth);
        drawSkillWindowCloseButton(g, windowX, windowY, windowWidth);
        drawSkillRows(g, actor, skills, windowX, windowY, windowWidth, windowHeight);
    }

    private void drawItemWindow(Graphics2D g, int width, int height) {
        int windowWidth = Math.min(420, width - 80);
        int windowHeight = Math.min(320, height - 80);

        int windowX = (width - windowWidth) / 2;
        int windowY = (height - windowHeight) / 2;

        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MODAL_BACKDROP_ALPHA));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        g.setComposite(oldComposite);

        g.setColor(new Color(25, 25, 25));
        g.fillRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        g.setColor(Color.WHITE);
        g.drawRoundRect(windowX, windowY, windowWidth, windowHeight, 14, 14);

        drawItemWindowTitle(g, windowX, windowY, windowWidth);
        drawItemWindowCloseButton(g, windowX, windowY, windowWidth);
        drawBattleItemRows(g, windowX, windowY, windowWidth, windowHeight);
    }

    private void drawItemWindowTitle(Graphics2D g, int windowX, int windowY, int windowWidth) {
        g.setColor(Color.WHITE);

        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, 16f));

        g.drawString("Items", windowX + 20, windowY + 30);

        g.setFont(oldFont);

        g.setColor(new Color(180, 180, 180));
        g.drawLine(windowX + 16, windowY + 44, windowX + windowWidth - 16, windowY + 44);
    }

    private void drawItemWindowCloseButton(Graphics2D g, int windowX, int windowY, int windowWidth) {
        int closeSize = 24;

        int closeX = windowX + windowWidth - closeSize - 14;
        int closeY = windowY + 10;

        itemWindowCloseBounds = new Rectangle(closeX, closeY, closeSize, closeSize);

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

    private void drawBattleItemRows(Graphics2D g, int windowX, int windowY, int windowWidth, int windowHeight) {
        int contentX = windowX + 20;
        int contentY = windowY + 60;
        int contentWidth = windowWidth - 40;

        int rowHeight = 34;
        int rowGap = 8;

        if (battleItems.isEmpty()) {
            g.setColor(new Color(210, 210, 210));
            g.drawString("No usable healing items.", contentX, contentY + 22);
            return;
        }

        for (int i = 0; i < battleItems.size(); i++) {
            BattleItemEntry entry = battleItems.get(i);

            int rowX = contentX;
            int rowY = contentY + i * (rowHeight + rowGap);

            if (rowY + rowHeight > windowY + windowHeight - 20) {
                break;
            }

            Rectangle rowBounds = new Rectangle(rowX, rowY, contentWidth, rowHeight);
            itemBounds.put(entry.inventoryIndex(), rowBounds);

            g.setColor(new Color(45, 45, 45));
            g.fillRect(rowX, rowY, contentWidth, rowHeight);

            g.setColor(Color.WHITE);
            g.drawRect(rowX, rowY, contentWidth, rowHeight);

            FontMetrics metrics = g.getFontMetrics();
            String label = entry.item().getName() + "  +" + entry.item().getHealAmount() + " HP";

            int textX = rowX + 10;
            int textY = rowY
                    + (rowHeight - metrics.getHeight()) / 2
                    + metrics.getAscent();

            g.drawString(label, textX, textY);
        }
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
            BattleActor actor,
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

            boolean ready = actor == null || actor.isSkillReady(skill);
            g.setColor(ready ? new Color(45, 45, 45) : new Color(30, 30, 34));
            g.fillRect(rowX, rowY, contentWidth, rowHeight);

            g.setColor(ready ? Color.WHITE : new Color(120, 120, 125));
            g.drawRect(rowX, rowY, contentWidth, rowHeight);

            FontMetrics metrics = g.getFontMetrics();

            int textX = rowX + 10;
            int textY = rowY
                    + (rowHeight - metrics.getHeight()) / 2
                    + metrics.getAscent();

            g.drawString(skill.getName(), textX, textY);
            if (!ready) {
                String cooldownText = Math.max(1, (int) Math.ceil(actor.getSkillCooldownRemainingSeconds(skill))) + "s";
                g.drawString(cooldownText, rowX + contentWidth - metrics.stringWidth(cooldownText) - 10, textY);
            }
        }
    }

    private void drawBattleArea(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
        if (selectableTargets.isEmpty()) {
            drawFirstPersonBattleArea(g, encounter, x, y, width, height);
            return;
        }
        drawTacticalBattleArea(g, encounter, x, y, width, height);
    }

    private void drawTacticalBattleArea(Graphics2D g, BattleEncounter encounter, int x, int y, int width, int height) {
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

    private void drawFirstPersonBattleArea(
            Graphics2D g,
            BattleEncounter encounter,
            int x,
            int y,
            int width,
            int height
    ) {
        BufferedImage background = assets != null ? assets.getBattleBackground() : null;
        drawImageOrFill(g, background, x, y, width, height, new Color(42, 47, 55));
        drawPanelBorder(g, x, y, width, height);

        List<BattlePresentationDirector.ActionSnapshot> actions =
                encounter.getPresentationDirector().snapshots();
        Graphics2D scene = (Graphics2D) g.create();
        BattleActor pointOfViewActor = encounter.getFirstLivingAlly();
        BattlePresentationDirector.TargetReaction pointOfViewReaction =
                targetReaction(pointOfViewActor, actions);
        double pointOfViewAmount = targetReactionAmount(pointOfViewActor, actions);
        if (pointOfViewReaction != null) {
            if (pointOfViewReaction.reaction() == BattlePresentationDirector.Reaction.DODGE) {
                scene.translate(Math.round(16 * pointOfViewAmount), 0);
            } else if (pointOfViewReaction.reaction() == BattlePresentationDirector.Reaction.HIT) {
                scene.translate(0, Math.round(10 * pointOfViewAmount));
            }
        }
        drawFirstPersonEnemyFormation(scene, encounter, actions, x, y, width, height);
        drawPeripheralAllies(scene, encounter, actions, x, y, width, height);
        drawProceduralActionEffects(scene, encounter, actions, x, y, width, height);
        drawFirstPersonEquipment(scene, encounter, actions, x, y, width, height);
        scene.dispose();
        drawPresentationCue(g, actions, x, y, width);
    }

    private void drawFirstPersonEnemyFormation(
            Graphics2D g,
            BattleEncounter encounter,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int x,
            int y,
            int width,
            int height
    ) {
        for (BattleActor actor : encounter.getEnemies()) {
            if (!actor.isAlive()) {
                continue;
            }
            boolean front = actor.getRow() == Library.BattleRow.FRONT;
            int spriteSize = front ? Math.max(74, height / 4) : Math.max(56, height / 6);
            int laneWidth = Math.max(1, width / 4);
            int centerX = x + width / 2 + (actor.getSlot() - 1) * laneWidth;
            int baseY = front ? y + (int) (height * 0.72) : y + (int) (height * 0.43);
            int drawX = centerX - spriteSize / 2;
            int drawY = baseY - spriteSize;

            double lunge = attackerLunge(actor, actions);
            drawY += (int) Math.round(lunge * Math.max(18, height * 0.08));
            BattlePresentationDirector.TargetReaction reaction = targetReaction(actor, actions);
            if (reaction != null) {
                double reactionAmount = targetReactionAmount(actor, actions);
                if (reaction.reaction() == BattlePresentationDirector.Reaction.DODGE) {
                    drawX += (actor.getSlot() % 2 == 0 ? -1 : 1) * (int) Math.round(28 * reactionAmount);
                } else if (reaction.reaction() == BattlePresentationDirector.Reaction.HIT) {
                    drawY += (int) Math.round(14 * reactionAmount);
                }
            }

            drawHpBar(g, drawX, drawY - 18, spriteSize, 10, actor);
            drawAttackChargeBar(g, drawX, drawY - 5, spriteSize, 5, actor);
            drawActorSprite(g, actor, drawX, drawY, spriteSize, spriteSize);
            drawActorNameTag(g, actor, drawX, drawY + spriteSize + 17, spriteSize);
            if (reaction != null && reaction.reaction() == BattlePresentationDirector.Reaction.BLOCK) {
                drawBlockPlaceholder(g, drawX, drawY, spriteSize);
            }
        }
    }

    private void drawPeripheralAllies(
            Graphics2D g,
            BattleEncounter encounter,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int x,
            int y,
            int width,
            int height
    ) {
        int index = 0;
        List<BattleActor> allies = encounter.getAllies();
        for (int i = 1; i < allies.size(); i++) {
            BattleActor actor = allies.get(i);
            if (!actor.isAlive()) {
                continue;
            }
            int size = 46;
            int drawX = i % 2 == 0 ? x + 18 : x + width - size - 18;
            int drawY = y + 58 + index * 58;
            drawActorSprite(g, actor, drawX, drawY, size, size);
            drawHpBar(g, drawX, drawY - 10, size, 6, actor);
            if (targetReaction(actor, actions) != null) {
                g.setColor(new Color(255, 226, 120, 210));
                g.drawRoundRect(drawX - 3, drawY - 3, size + 6, size + 6, 8, 8);
            }
            index++;
        }
    }

    private void drawFirstPersonEquipment(
            Graphics2D g,
            BattleEncounter encounter,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int x,
            int y,
            int width,
            int height
    ) {
        BattleActor player = encounter.getFirstLivingAlly();
        if (player == null || player.getSourcePlayer() == null
                || player.getSourcePlayer().getInventory() == null) {
            drawPlaceholderHands(g, x, y, width, height, new Color(170, 119, 82));
            return;
        }
        InventorySystem.Inventory inventory = player.getSourcePlayer().getInventory();
        InventorySystem.Item weapon = inventory.getEquippedItem(InventorySystem.EquipmentSlot.WEAPON);
        InventorySystem.Item shield = inventory.getEquippedItem(InventorySystem.EquipmentSlot.SHIELD);
        InventorySystem.Item armor = inventory.getEquippedItem(InventorySystem.EquipmentSlot.CHEST);
        Color gloveColor = armor == null ? new Color(170, 119, 82) : materialColor(armor);

        double swing = attackerSwing(player, actions);
        BattlePresentationDirector.TargetReaction playerReaction = targetReaction(player, actions);
        double blockRaise = playerReaction != null
                && playerReaction.reaction() == BattlePresentationDirector.Reaction.BLOCK
                ? targetReactionAmount(player, actions)
                : 0.0;
        Graphics2D equipment = (Graphics2D) g.create();
        equipment.rotate(Math.toRadians(-48.0 * swing), x + width * 0.79, y + height * 0.84);
        drawPlaceholderHands(equipment, x, y, width, height, gloveColor);
        drawWeaponPlaceholder(equipment, weapon, x, y, width, height);
        equipment.dispose();
        if (shield != null) {
            drawShieldPlaceholder(g, shield, x, y, width, height, blockRaise);
        }
    }

    private void drawProceduralActionEffects(
            Graphics2D g,
            BattleEncounter encounter,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int x,
            int y,
            int width,
            int height
    ) {
        for (BattlePresentationDirector.ActionSnapshot action : actions) {
            if (action.phase() == BattlePresentationDirector.Phase.WINDUP) {
                continue;
            }
            double intensity = action.phase() == BattlePresentationDirector.Phase.IMPACT
                    ? 1.0 - Math.abs(action.progress() * 2.0 - 1.0)
                    : 1.0 - action.progress();
            if (intensity <= 0.0) {
                continue;
            }
            boolean spellLike = action.actionType() == BattlePresentationDirector.ActionType.SPELL
                    || action.actionType() == BattlePresentationDirector.ActionType.HEAL
                    || action.actionType() == BattlePresentationDirector.ActionType.SUMMON
                    || action.actionType() == BattlePresentationDirector.ActionType.DEFEND;
            for (BattlePresentationDirector.TargetReaction targetReaction : action.targets()) {
                Point anchor = firstPersonActorAnchor(
                        encounter, targetReaction.target(), x, y, width, height);
                if (anchor == null) {
                    continue;
                }
                if (spellLike) {
                    drawSpellPlaceholder(g, anchor, action.actionType(), intensity);
                }
                if (targetReaction.damage() > 0 && intensity > 0.35) {
                    drawDamageNumber(g, anchor, targetReaction.damage(), intensity);
                } else if (targetReaction.reaction() == BattlePresentationDirector.Reaction.DODGE
                        && intensity > 0.35) {
                    drawReactionWord(g, anchor, "DODGE", new Color(190, 235, 255), intensity);
                } else if (targetReaction.reaction() == BattlePresentationDirector.Reaction.BLOCK
                        && intensity > 0.35) {
                    drawReactionWord(g, anchor, "BLOCK", new Color(170, 215, 255), intensity);
                }
            }
        }
    }

    private Point firstPersonActorAnchor(
            BattleEncounter encounter,
            BattleActor actor,
            int x,
            int y,
            int width,
            int height
    ) {
        if (actor == null) {
            return null;
        }
        if (actor.isEnemy()) {
            boolean front = actor.getRow() == Library.BattleRow.FRONT;
            int laneWidth = Math.max(1, width / 4);
            int centerX = x + width / 2 + (actor.getSlot() - 1) * laneWidth;
            int centerY = front ? y + (int) (height * 0.58) : y + (int) (height * 0.34);
            return new Point(centerX, centerY);
        }
        int allyIndex = encounter.getAllies().indexOf(actor);
        if (allyIndex <= 0) {
            return new Point(x + width / 2, y + (int) (height * 0.82));
        }
        int drawX = allyIndex % 2 == 0 ? x + 41 : x + width - 41;
        int drawY = y + 81 + (allyIndex - 1) * 58;
        return new Point(drawX, drawY);
    }

    private void drawSpellPlaceholder(
            Graphics2D g,
            Point anchor,
            BattlePresentationDirector.ActionType actionType,
            double intensity
    ) {
        Color color = switch (actionType) {
            case HEAL -> new Color(110, 255, 155);
            case DEFEND -> new Color(120, 190, 255);
            case SUMMON -> new Color(205, 135, 255);
            default -> new Color(255, 135, 80);
        };
        int radius = 18 + (int) Math.round(46 * intensity);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(210, (int) Math.round(190 * intensity)))));
        g.setStroke(new BasicStroke(2.0f));
        g.drawOval(anchor.x - radius, anchor.y - radius, radius * 2, radius * 2);
        g.drawLine(anchor.x - radius, anchor.y, anchor.x + radius, anchor.y);
        g.drawLine(anchor.x, anchor.y - radius, anchor.x, anchor.y + radius);
        g.setStroke(new BasicStroke(1.0f));
    }

    private void drawDamageNumber(Graphics2D g, Point anchor, int damage, double intensity) {
        drawReactionWord(g, new Point(anchor.x, anchor.y - 18), "-" + damage,
                new Color(255, 115, 92), intensity);
    }

    private void drawReactionWord(Graphics2D g, Point anchor, String text, Color color, double intensity) {
        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, Math.max(14f, oldFont.getSize2D() + 3f)));
        FontMetrics metrics = g.getFontMetrics();
        int drawX = anchor.x - metrics.stringWidth(text) / 2;
        int drawY = anchor.y - (int) Math.round(22 * intensity);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(text, drawX + 1, drawY + 1);
        g.setColor(color);
        g.drawString(text, drawX, drawY);
        g.setFont(oldFont);
    }

    private void drawPlaceholderHands(Graphics2D g, int x, int y, int width, int height, Color color) {
        g.setColor(color.darker());
        g.fillRoundRect(x + (int) (width * 0.68), y + (int) (height * 0.78),
                Math.max(32, width / 11), Math.max(45, height / 7), 18, 18);
        g.setColor(color);
        g.fillOval(x + (int) (width * 0.72), y + (int) (height * 0.71),
                Math.max(34, width / 12), Math.max(34, width / 12));
    }

    private void drawWeaponPlaceholder(
            Graphics2D g,
            InventorySystem.Item weapon,
            int x,
            int y,
            int width,
            int height
    ) {
        if (weapon == null) {
            return;
        }
        Color material = materialColor(weapon);
        int gripX = x + (int) (width * 0.76);
        int gripY = y + (int) (height * 0.76);
        int tipX = x + (int) (width * 0.90);
        int tipY = y + (int) (height * 0.20);
        if (weapon.getWeaponType() == WeaponType.DAGGER) {
            tipX = x + (int) (width * 0.84);
            tipY = y + (int) (height * 0.48);
        } else if (weapon.getWeaponType() == WeaponType.GREATSWORD) {
            tipX = x + (int) (width * 0.94);
            tipY = y + (int) (height * 0.10);
        }
        g.setStroke(new BasicStroke(Math.max(8f, width / 90f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(91, 58, 37));
        g.drawLine(gripX, gripY, gripX + 18, gripY - 72);
        int bladeStartX = gripX + 18;
        int bladeStartY = gripY - 72;
        if (weapon.getWeaponType() == WeaponType.MACE) {
            g.setStroke(new BasicStroke(Math.max(10f, width / 80f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(material.darker());
            g.drawLine(bladeStartX, bladeStartY, tipX, tipY);
            int headSize = Math.max(24, width / 30);
            g.setColor(material);
            g.fillOval(tipX - headSize / 2, tipY - headSize / 2, headSize, headSize);
        } else if (weapon.getWeaponType() == WeaponType.STAFF) {
            g.setStroke(new BasicStroke(Math.max(10f, width / 78f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(material);
            g.drawLine(gripX, gripY, tipX, tipY);
            int focusSize = Math.max(18, width / 38);
            g.setColor(new Color(125, 185, 255));
            g.fillOval(tipX - focusSize / 2, tipY - focusSize / 2, focusSize, focusSize);
        } else {
            float bladeWidth = weapon.getWeaponType() == WeaponType.GREATSWORD
                    ? Math.max(18f, width / 55f)
                    : Math.max(12f, width / 70f);
            g.setStroke(new BasicStroke(bladeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(material);
            g.drawLine(bladeStartX, bladeStartY, tipX, tipY);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawShieldPlaceholder(
            Graphics2D g,
            InventorySystem.Item shield,
            int x,
            int y,
            int width,
            int height,
            double blockRaise
    ) {
        int shieldWidth = Math.max(86, width / 8);
        int shieldHeight = Math.max(112, height / 3);
        int shieldX = x + width / 12;
        int shieldY = y + height - shieldHeight - 14
                - (int) Math.round(blockRaise * height * 0.18);
        Polygon polygon = new Polygon(
                new int[]{shieldX, shieldX + shieldWidth, shieldX + shieldWidth - 12, shieldX + shieldWidth / 2, shieldX + 12},
                new int[]{shieldY, shieldY, shieldY + shieldHeight * 2 / 3, shieldY + shieldHeight, shieldY + shieldHeight * 2 / 3},
                5);
        g.setColor(materialColor(shield));
        g.fillPolygon(polygon);
        g.setColor(new Color(238, 226, 185, 210));
        g.drawPolygon(polygon);
    }

    private void drawBlockPlaceholder(Graphics2D g, int drawX, int drawY, int spriteSize) {
        g.setColor(new Color(155, 205, 255, 150));
        g.fillArc(drawX - 8, drawY - 8, spriteSize + 16, spriteSize + 16, 65, 230);
        g.setColor(new Color(225, 245, 255));
        g.drawArc(drawX - 8, drawY - 8, spriteSize + 16, spriteSize + 16, 65, 230);
    }

    private void drawActorNameTag(Graphics2D g, BattleActor actor, int drawX, int baselineY, int width) {
        String name = actor.getName();
        FontMetrics metrics = g.getFontMetrics();
        int labelX = drawX + Math.max(0, (width - metrics.stringWidth(name)) / 2);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(labelX - 5, baselineY - metrics.getAscent(), metrics.stringWidth(name) + 10,
                metrics.getHeight(), 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(name, labelX, baselineY);
    }

    private void drawPresentationCue(
            Graphics2D g,
            List<BattlePresentationDirector.ActionSnapshot> actions,
            int x,
            int y,
            int width
    ) {
        if (actions.isEmpty()) {
            return;
        }
        BattlePresentationDirector.ActionSnapshot primary = actions.getFirst();
        String target = primary.targets().isEmpty() ? "" : " → " + primary.targets().getFirst().target().getName();
        String text = primary.attacker().getName() + ": " + primary.actionName() + target;
        FontMetrics metrics = g.getFontMetrics();
        int labelWidth = metrics.stringWidth(text) + 24;
        int labelX = x + (width - labelWidth) / 2;
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(labelX, y + 52, labelWidth, 28, 10, 10);
        g.setColor(new Color(255, 232, 146));
        g.drawRoundRect(labelX, y + 52, labelWidth, 28, 10, 10);
        g.drawString(text, labelX + 12, y + 71);
    }

    private double attackerLunge(
            BattleActor actor,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        return actions.stream()
                .filter(action -> action.attacker() == actor)
                .mapToDouble(action -> action.phase() == BattlePresentationDirector.Phase.WINDUP
                        ? action.progress() * -0.35
                        : action.phase() == BattlePresentationDirector.Phase.IMPACT
                        ? 1.0 - Math.abs(action.progress() * 2.0 - 1.0)
                        : (1.0 - action.progress()) * 0.35)
                .findFirst().orElse(0.0);
    }

    private double attackerSwing(BattleActor actor, List<BattlePresentationDirector.ActionSnapshot> actions) {
        return actions.stream()
                .filter(action -> action.attacker() == actor)
                .mapToDouble(action -> action.phase() == BattlePresentationDirector.Phase.WINDUP
                        ? -0.35 * action.progress()
                        : action.phase() == BattlePresentationDirector.Phase.IMPACT
                        ? action.progress()
                        : 1.0 - action.progress())
                .findFirst().orElse(0.0);
    }

    private BattlePresentationDirector.TargetReaction targetReaction(
            BattleActor actor,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        return actions.stream()
                .filter(action -> action.phase() != BattlePresentationDirector.Phase.WINDUP)
                .flatMap(action -> action.targets().stream())
                .filter(target -> target.target() == actor)
                .findFirst().orElse(null);
    }

    private double targetReactionAmount(
            BattleActor actor,
            List<BattlePresentationDirector.ActionSnapshot> actions
    ) {
        return actions.stream()
                .filter(action -> action.targets().stream().anyMatch(target -> target.target() == actor))
                .mapToDouble(action -> action.phase() == BattlePresentationDirector.Phase.IMPACT
                        ? 1.0 - Math.abs(action.progress() * 2.0 - 1.0)
                        : action.phase() == BattlePresentationDirector.Phase.RECOVERY
                        ? 1.0 - action.progress() : 0.0)
                .findFirst().orElse(0.0);
    }

    private Color materialColor(InventorySystem.Item item) {
        if (item == null || item.getMaterial() == null) {
            return new Color(165, 165, 172);
        }
        return switch (item.getMaterial()) {
            case COPPER -> new Color(184, 103, 66);
            case TIN, SILVER -> new Color(205, 212, 220);
            case BRONZE -> new Color(160, 112, 56);
            case IRON -> new Color(130, 137, 145);
            case STEEL -> new Color(175, 185, 195);
            case OAK -> new Color(132, 87, 48);
            case YEW -> new Color(104, 62, 37);
            case IRONWOOD -> new Color(73, 48, 34);
            case LEATHER -> new Color(111, 72, 45);
            case NONE -> new Color(165, 165, 172);
        };
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
            drawAttackChargeBar(g, drawX, drawY - 5, spriteSize, 5, actor);
            drawActorSprite(g, actor, drawX, drawY, spriteSize, spriteSize);
            drawActorStatusIcons(g, actor, side, drawX, drawY, spriteSize, x, width);
        }
    }

    private void drawActorStatusIcons(
            Graphics2D g,
            BattleActor actor,
            Library.EntityType side,
            int spriteX,
            int spriteY,
            int spriteSize,
            int areaX,
            int areaWidth
    ) {
        List<BattleStatus> statuses = actor.getStatuses();
        if (statuses.isEmpty()) {
            return;
        }

        int iconSize = 18;
        int gap = 4;
        int totalWidth = statuses.size() * iconSize + (statuses.size() - 1) * gap;
        int preferredX = side == Library.EntityType.ENEMY
                ? spriteX + spriteSize + 8
                : spriteX - totalWidth - 8;
        int iconX = Math.max(areaX + 4, Math.min(areaX + areaWidth - totalWidth - 4, preferredX));
        int iconY = spriteY + Math.max(0, (spriteSize - iconSize) / 2);

        drawStatusIcons(g, statuses, iconX, iconY, iconSize, gap);
    }

    private void drawAttackChargeBar(Graphics2D g, int x, int y, int width, int height, BattleActor actor) {
        g.setColor(new Color(18, 18, 18, 185));
        g.fillRect(x, y, width, height);

        int fillWidth = (int) Math.round(width * actor.getAttackCooldownProgress());
        g.setColor(actor.isEnemy() ? new Color(224, 98, 76) : new Color(84, 180, 255));
        g.fillRect(x, y, fillWidth, height);

        g.setColor(new Color(245, 245, 245, 160));
        g.drawRect(x, y, width, height);
    }

    private void drawPausedIndicator(Graphics2D g, int width, int battleAreaHeight) {
        if (!autoCombatPaused) {
            return;
        }

        String text = "Paused";
        FontMetrics metrics = g.getFontMetrics();
        int labelWidth = metrics.stringWidth(text) + 22;
        int labelHeight = metrics.getHeight() + 10;
        int labelX = (width - labelWidth) / 2;
        int labelY = 16;

        g.setColor(new Color(0, 0, 0, 155));
        g.fillRoundRect(labelX, labelY, labelWidth, labelHeight, 10, 10);
        g.setColor(new Color(255, 235, 150));
        g.drawRoundRect(labelX, labelY, labelWidth, labelHeight, 10, 10);
        g.drawString(text, labelX + 11, labelY + 5 + metrics.getAscent());
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

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TARGET_OUTLINE_ALPHA));
        g.setColor(Color.YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawRect(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);

        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
    }

    private void drawResolvedTargetHighlight(Graphics2D g, Rectangle bounds) {
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TARGET_PREVIEW_FILL_ALPHA));
        g.setColor(Color.YELLOW);
        g.fillRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TARGET_PREVIEW_OUTLINE_ALPHA));
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

        int contentX = x + COMPACT_PANEL_PADDING;
        int contentY = y + COMPACT_PANEL_PADDING;
        int contentWidth = width - COMPACT_PANEL_PADDING * 2;
        int contentHeight = height - COMPACT_PANEL_PADDING * 2;

        Dimension buttonSize = calculateButtonSize(
                buttonImage,
                contentWidth,
                contentHeight,
                4,
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
                Library.BattleCommand.ITEMS,
                "Items",
                new Rectangle(contentX, contentY + (buttonHeight + BUTTON_GAP) * 2, buttonWidth, buttonHeight)
        );

        drawCommandButton(
                g,
                Library.BattleCommand.RUN,
                "Run",
                new Rectangle(contentX, contentY + (buttonHeight + BUTTON_GAP) * 3, buttonWidth, buttonHeight)
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

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MESSAGE_BACKGROUND_ALPHA));
        g.setColor(Color.BLACK);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 12, 12);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MESSAGE_OVERLAY_ALPHA));
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

        int contentX = x + COMPACT_PANEL_PADDING;
        int contentY = y + COMPACT_PANEL_PADDING;
        int contentWidth = width - COMPACT_PANEL_PADDING * 2;
        List<BattleActor> allies = encounter.getAllies();
        int columns = allies.size() > 3 ? 2 : 1;
        int rows = Math.max(1, (Math.max(1, allies.size()) + columns - 1) / columns);
        int columnGap = columns > 1 ? 14 : 0;
        int columnWidth = Math.max(1, (contentWidth - columnGap * (columns - 1)) / columns);

        for (int index = 0; index < allies.size(); index++) {
            BattleActor ally = allies.get(index);
            int column = index / rows;
            int row = index % rows;
            int actorX = contentX + column * (columnWidth + columnGap);
            int currentY = contentY + 10 + row * 35;
            int nameWidth = Math.min(82, Math.max(54, columnWidth / 3));
            int barX = actorX + nameWidth;
            int barWidth = Math.max(28, columnWidth - nameWidth);

            g.setColor(Color.WHITE);
            g.drawString(trimTextToFit(g, ally.getName(), nameWidth - 6), actorX, currentY);

            drawHpBar(
                    g,
                    barX,
                    currentY - 12,
                    barWidth,
                    12,
                    ally
            );
            drawStatusIcons(g, ally, barX, currentY + 4);
        }
    }

    private void drawStatusIcons(Graphics2D g, BattleActor actor, int x, int y) {
        drawStatusIcons(g, actor.getStatuses(), x, y, 14, 3);
    }

    private void drawStatusIcons(Graphics2D g, List<BattleStatus> statuses, int x, int y, int iconSize, int gap) {
        int index = 0;

        for (BattleStatus status : statuses) {
            BattleStatusType type = status.getType();
            int iconX = x + index * (iconSize + gap);
            BufferedImage icon = type.getIcon();

            g.setColor(new Color(0, 0, 0, 165));
            g.fillRoundRect(iconX - 2, y - 2, iconSize + 4, iconSize + 4, 5, 5);

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

    private record BattleHudLayout(Rectangle command, Rectangle party, int clearViewBottom) {
    }

    public record BattleItemEntry(int inventoryIndex, InventorySystem.Item item) {
    }
}

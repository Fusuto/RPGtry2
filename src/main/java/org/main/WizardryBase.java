package org.main;

import org.main.battle.*;
import org.main.content.EnvironmentLibrary;
import org.main.content.PlayerClassLibrary;
import org.main.core.*;
import org.main.engine.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WizardryBase extends JPanel implements KeyListener {

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();
    private final OverworldHud overworldHud = new OverworldHud();

    private final MovementEngine movementEngine = new MovementEngine();
    private final SoundSystem soundSystem = new SoundSystem();
    private final EnvironmentLibrary environment = EnvironmentLibrary.STARTER_DUNGEON;

    private final BattleRenderer battleRenderer = new BattleRenderer();

    private final InteractionSystem.InteractionWindow interactionWindow =
            new InteractionSystem.InteractionWindow();
    private final ShopSystem.ShopWindow shopWindow = new ShopSystem.ShopWindow();
    private final InteractionSystem.InteractionRegistry interactionRegistry =
            InteractionSystem.InteractionRegistry.createDefault();
    private final GameState gameState = new GameState(
            DungeonMap.testMap(),
            GameBootstrap.createDefaultPlayerCharacter()
    );
    private final DungeonController dungeonController;
    private final BattleController battleController;
    private final InventorySystem.InventoryPanel inventoryPanel;

    private long lastUpdateTime = System.currentTimeMillis();
    private double averageFrameMs = 16.0;
    private double lastUpdateMs = 0.0;
    private double lastRenderMs = 0.0;
    private String startMenuMessage = "";
    private PlayerClassLibrary selectedPlayerClass = PlayerClassLibrary.WARRIOR;
    private String characterName = "Player";
    private String characterCreationMessage = "";

    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        dungeonController = new DungeonController(
                gameState,
                movementEngine,
                interactionRegistry,
                soundSystem,
                environment
        );

        battleController = new BattleController(gameState, battleRenderer, soundSystem, environment);
        inventoryPanel = new InventorySystem.InventoryPanel(gameState.getInventory(), gameState, soundSystem);

        /*
         * Temporary debug unlock.
         * Remove or comment this out when you want the minimap to require
         * a spell, item, or ability unlock.
         */
        gameState.unlockMiniMap();

        RendererBootstrap.configureDefaultRenderers(dungeonRenderer, battleRenderer);
        installMouseInput();
        GameBootstrap.seedTestContent(gameState);

        Timer timer = new Timer(16, e -> {
            long now = System.currentTimeMillis();
            int deltaMs = (int) (now - lastUpdateTime);
            lastUpdateTime = now;

            updateGame(deltaMs);
            repaint();
        });

        timer.start();
    }

    private void installMouseInput() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (gameState.isStartMenuMode()) {
                    handleStartMenuMousePressed(e.getPoint());
                }

                if (gameState.isCharacterCreationMode()) {
                    handleCharacterCreationMousePressed(e.getPoint());
                }
            }
        });

        new GameMouseInputRouter(
                this,
                gameState,
                battleController,
                interactionWindow,
                shopWindow,
                inventoryPanel,
                overworldHud,
                this::openConfigMenu
        ).install();
    }

    private void updateGame(int deltaMs) {
        long updateStart = System.nanoTime();
        averageFrameMs = averageFrameMs * 0.90 + deltaMs * 0.10;

        gameState.updateMovementAnimation(deltaMs);

        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }

        lastUpdateMs = (System.nanoTime() - updateStart) / 1_000_000.0;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Wizardry-esque Dungeon Base");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new WizardryBase());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        long renderStart = System.nanoTime();
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (gameState.isStartMenuMode()) {
            drawStartMenu(g2);
            lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
            return;
        }

        if (gameState.isCharacterCreationMode()) {
            drawCharacterCreation(g2);
            lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
            return;
        }

        if (gameState.isDungeonMode()) {
            dungeonRenderer.draw(
                    g2,
                    gameState.getDungeonMap(),
                    gameState.getEntities(),
                    gameState.getPlayerX(),
                    gameState.getPlayerY(),
                    gameState.getDirection(),
                    getWidth(),
                    getHeight(),
                    gameState.getCameraOffsetForward(),
                    gameState.getCameraOffsetSide(),
                    gameState.getCameraRotationRadians()
            );

            miniMapRenderer.draw(g2, gameState);

            if (gameState.isInventoryOpen()) {
                inventoryPanel.draw(
                        g2,
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }

            overworldHud.draw(g2, gameState, getWidth(), getHeight());

            if (gameState.hasActiveShop()) {
                shopWindow.draw(g2, gameState, getWidth(), getHeight());
            }

            if (gameState.hasActiveInteraction()) {
                interactionWindow.draw(
                        g2,
                        gameState.getActiveInteraction(),
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }
        }

        if (gameState.isBattleMode()) {
            battleRenderer.draw(
                    g2,
                    gameState.getCurrentEncounter(),
                    getWidth(),
                    getHeight()
            );
        }

        lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
        drawPerformanceOverlay(g2);
    }

    private void drawStartMenu(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();

        GradientPaint background = new GradientPaint(
                0,
                0,
                new Color(12, 12, 18),
                0,
                height,
                new Color(35, 27, 20)
        );
        Paint previousPaint = g.getPaint();
        g.setPaint(background);
        g.fillRect(0, 0, width, height);
        g.setPaint(previousPaint);

        g.setColor(new Color(170, 36, 32));
        g.drawRect(18, 18, width - 36, height - 36);
        g.setColor(new Color(95, 76, 54));
        g.drawRect(24, 24, width - 48, height - 48);

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SERIF, Font.BOLD, 52));
        FontMetrics titleMetrics = g.getFontMetrics();
        String title = "Wizardry";
        int titleX = (width - titleMetrics.stringWidth(title)) / 2;
        int titleY = height / 4;
        g.setColor(new Color(15, 10, 8, 180));
        g.drawString(title, titleX + 3, titleY + 3);
        g.setColor(new Color(235, 225, 200));
        g.drawString(title, titleX, titleY);

        drawStartMenuButton(g, "New", getStartMenuButtonBounds(0));
        drawStartMenuButton(g, "Load", getStartMenuButtonBounds(1));
        drawStartMenuButton(g, "Quit", getStartMenuButtonBounds(2));

        if (!startMenuMessage.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            FontMetrics messageMetrics = g.getFontMetrics();
            int messageX = (width - messageMetrics.stringWidth(startMenuMessage)) / 2;
            int messageY = getStartMenuButtonBounds(2).y + getStartMenuButtonBounds(2).height + 34;
            g.setColor(new Color(220, 205, 170));
            g.drawString(startMenuMessage, messageX, messageY);
        }

        g.setFont(previousFont);
    }

    private void drawStartMenuButton(Graphics2D g, String label, Rectangle bounds) {
        g.setColor(new Color(12, 12, 12, 215));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(170, 36, 32));
        g.drawLine(bounds.x + 14, bounds.y + bounds.height - 7, bounds.x + bounds.width - 14, bounds.y + bounds.height - 7);

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        FontMetrics metrics = g.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.setColor(new Color(235, 230, 210));
        g.drawString(label, textX, textY);
        g.setFont(previousFont);
    }

    private Rectangle getStartMenuButtonBounds(int index) {
        int buttonWidth = 220;
        int buttonHeight = 48;
        int gap = 16;
        int totalHeight = buttonHeight * 3 + gap * 2;
        int x = (getWidth() - buttonWidth) / 2;
        int y = getHeight() / 2 - totalHeight / 2 + index * (buttonHeight + gap);
        return new Rectangle(x, y, buttonWidth, buttonHeight);
    }

    private void handleStartMenuMousePressed(Point point) {
        requestFocusInWindow();

        if (getStartMenuButtonBounds(0).contains(point)) {
            startNewGame();
            return;
        }

        if (getStartMenuButtonBounds(1).contains(point)) {
            loadGameFromStartMenu();
            return;
        }

        if (getStartMenuButtonBounds(2).contains(point)) {
            quitGame();
        }
    }

    private void startNewGame() {
        startMenuMessage = "";
        characterCreationMessage = "";
        gameState.setGameMode(GameState.GameMode.CHARACTER_CREATION);
        repaint();
    }

    private void drawCharacterCreation(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        Paint previousPaint = g.getPaint();
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 18), 0, height, new Color(28, 25, 22)));
        g.fillRect(0, 0, width, height);
        g.setPaint(previousPaint);

        Font previousFont = g.getFont();
        g.setColor(new Color(95, 76, 54));
        g.drawRect(34, 34, width - 68, height - 68);
        g.setColor(new Color(170, 36, 32));
        g.drawRect(42, 42, width - 84, height - 84);

        g.setFont(new Font(Font.SERIF, Font.BOLD, 42));
        drawCenteredText(g, "Create Character", height / 6, new Color(235, 225, 200));

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(230, 220, 195));
        g.drawString("Name", getCharacterPanelX(), 190);
        drawNameField(g, getNameFieldBounds());

        g.drawString("Class", getCharacterPanelX(), 270);

        int index = 0;
        for (PlayerClassLibrary playerClass : PlayerClassLibrary.values()) {
            drawClassOption(g, playerClass, getClassButtonBounds(index));
            index++;
        }

        drawClassDetails(g, getClassDetailsBounds());
        drawStartMenuButton(g, "Confirm", getConfirmCharacterButtonBounds());
        drawStartMenuButton(g, "Back", getBackCharacterButtonBounds());

        if (!characterCreationMessage.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            drawCenteredText(g, characterCreationMessage, getConfirmCharacterButtonBounds().y + 76, new Color(220, 205, 170));
        }

        g.setFont(previousFont);
    }

    private void drawNameField(Graphics2D g, Rectangle bounds) {
        g.setColor(new Color(8, 8, 10, 230));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setColor(new Color(235, 230, 210));
        g.drawString(characterName + "_", bounds.x + 14, bounds.y + 31);
    }

    private void drawClassOption(Graphics2D g, PlayerClassLibrary playerClass, Rectangle bounds) {
        boolean selected = playerClass == selectedPlayerClass;
        g.setColor(selected ? new Color(75, 45, 38, 235) : new Color(12, 12, 12, 215));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(selected ? new Color(210, 70, 58) : new Color(105, 88, 62));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(235, 230, 210));
        g.drawString(playerClass.getDisplayName(), bounds.x + 16, bounds.y + 30);
    }

    private void drawClassDetails(Graphics2D g, Rectangle bounds) {
        g.setColor(new Color(8, 8, 10, 190));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g.setColor(new Color(95, 76, 54));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(new Color(235, 230, 210));
        g.drawString(selectedPlayerClass.getDisplayName(), bounds.x + 18, bounds.y + 32);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(210, 200, 180));
        g.drawString(selectedPlayerClass.getDescription(), bounds.x + 18, bounds.y + 60);

        int y = bounds.y + 92;
        g.drawString("Preferred stats:", bounds.x + 18, y);
        y += 24;

        for (Map.Entry<PlayerStat, Integer> entry : selectedPlayerClass.getPreferredStatGrowth().entrySet()) {
            g.drawString(entry.getKey().getDisplayName() + " +" + entry.getValue(), bounds.x + 34, y);
            y += 20;
        }

        y += 8;
        g.drawString("Starter skills:", bounds.x + 18, y);
        y += 24;

        for (var skill : selectedPlayerClass.getStarterSkills()) {
            g.drawString(skill.getDisplayName(), bounds.x + 34, y);
            y += 20;
        }

        g.drawString(
                "Branches: " + selectedPlayerClass.getFirstBranchOption() + " / " + selectedPlayerClass.getSecondBranchOption(),
                bounds.x + 18,
                bounds.y + bounds.height - 22
        );
    }

    private int getCharacterPanelX() {
        return Math.max(80, getWidth() / 2 - 320);
    }

    private Rectangle getNameFieldBounds() {
        return new Rectangle(getCharacterPanelX(), 205, 300, 46);
    }

    private Rectangle getClassButtonBounds(int index) {
        return new Rectangle(getCharacterPanelX(), 286 + index * 58, 220, 46);
    }

    private Rectangle getClassDetailsBounds() {
        return new Rectangle(getCharacterPanelX() + 260, 205, 330, 245);
    }

    private Rectangle getConfirmCharacterButtonBounds() {
        return new Rectangle(getWidth() / 2 - 230, getHeight() - 112, 200, 48);
    }

    private Rectangle getBackCharacterButtonBounds() {
        return new Rectangle(getWidth() / 2 + 30, getHeight() - 112, 200, 48);
    }

    private void handleCharacterCreationMousePressed(Point point) {
        requestFocusInWindow();

        int index = 0;
        for (PlayerClassLibrary playerClass : PlayerClassLibrary.values()) {
            if (getClassButtonBounds(index).contains(point)) {
                selectedPlayerClass = playerClass;
                repaint();
                return;
            }

            index++;
        }

        if (getConfirmCharacterButtonBounds().contains(point)) {
            confirmCharacterCreation();
            return;
        }

        if (getBackCharacterButtonBounds().contains(point)) {
            gameState.setGameMode(GameState.GameMode.START_MENU);
            repaint();
        }
    }

    private void confirmCharacterCreation() {
        String trimmedName = characterName.trim();

        if (trimmedName.isBlank()) {
            characterCreationMessage = "Enter a character name.";
            repaint();
            return;
        }

        gameState.setPlayerCharacter(GameBootstrap.createPlayerCharacter(trimmedName, selectedPlayerClass));
        gameState.changeDungeon(DungeonMap.testMap(), 1, 1, List.of());
        GameBootstrap.seedTestContent(gameState);
        gameState.setGameMode(GameState.GameMode.DUNGEON);
        soundSystem.playAmbience(environment.getAmbienceSoundPath());
        repaint();
    }

    private void drawCenteredText(Graphics2D g, String text, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(text)) / 2;
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawPerformanceOverlay(Graphics2D g) {
        int fps = averageFrameMs <= 0.0 ? 0 : (int) Math.round(1000.0 / averageFrameMs);
        String[] lines = {
                "FPS " + fps,
                String.format("Frame %.1f ms", averageFrameMs),
                String.format("Update %.2f ms", lastUpdateMs),
                String.format("Render %.2f ms", lastRenderMs),
                "Entities " + gameState.getEntities().size(),
                "Map " + gameState.getDungeonMap().getWidth() + "x" + gameState.getDungeonMap().getHeight()
        };

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int width = 0;

        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }

        int x = getWidth() - width - 18;
        int y = 14;
        int height = lineHeight * lines.length + 8;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x - 6, y - 2, width + 12, height);
        g.setColor(new Color(220, 240, 220));

        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], x, y + lineHeight * (i + 1));
        }

        g.setFont(previousFont);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameState.isStartMenuMode()) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                startNewGame();
            }

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                quitGame();
            }

            return;
        }

        if (gameState.isCharacterCreationMode()) {
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && !characterName.isEmpty()) {
                characterName = characterName.substring(0, characterName.length() - 1);
                repaint();
            }

            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                confirmCharacterCreation();
            }

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                gameState.setGameMode(GameState.GameMode.START_MENU);
                repaint();
            }

            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            boolean consumed = interactionWindow.handleKeyPressed(
                    e,
                    gameState.getActiveInteraction()
            );

            if (consumed) {
                repaint();
            }

            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveShop()) {
            boolean consumed = shopWindow.handleKeyPressed(e, gameState);

            if (consumed) {
                repaint();
            }

            return;
        }

        InputBindings bindings = gameState.getInputBindings();
        int keyCode = e.getKeyCode();

        if (bindings.matches(InputBindings.Action.ESCAPE_MENU, keyCode) && gameState.isDungeonMode()) {
            openConfigMenu();
            repaint();
            return;
        }

        if (bindings.matches(InputBindings.Action.INVENTORY, keyCode) && gameState.isDungeonMode()) {
            gameState.toggleInventory();
            repaint();
            return;
        }

        if (bindings.matches(InputBindings.Action.SKILLS, keyCode) && gameState.isDungeonMode()) {
            gameState.toggleSkills();
            repaint();
            return;
        }

        if (gameState.isBattleMode()) {
            battleController.handleInput(e);
            repaint();
            return;
        }

        if (gameState.isInventoryOpen()) {
            return;
        }

        dungeonController.handleInput(e);
        repaint();
    }

    private void openConfigMenu() {
        gameState.openInteraction(InteractionSystem.configMenu(
                soundSystem,
                gameState,
                this::quitGame,
                () -> gameState.openInteraction(InteractionSystem.controlsMenu(gameState.getInputBindings())),
                this::saveGameFromMenu,
                this::loadGameFromMenu
        ));
    }

    private void saveGameFromMenu() {
        try {
            SaveSystem.save(gameState);
            gameState.openInteraction(InteractionSystem.prompt(
                    "Saved",
                    "Game saved to " + SaveSystem.getSavePath() + ".",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Save Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private void loadGameFromMenu() {
        try {
            SaveSystem.load(gameState);
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            gameState.openInteraction(InteractionSystem.prompt(
                    "Loaded",
                    "Saved game loaded.",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Load Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private void loadGameFromStartMenu() {
        try {
            SaveSystem.load(gameState);
            startMenuMessage = "";
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            repaint();
        } catch (IOException exception) {
            startMenuMessage = exception.getMessage();
            repaint();
        }
    }

    private void quitGame() {
        Window window = SwingUtilities.getWindowAncestor(this);

        if (window != null) {
            window.dispose();
        }

        System.exit(0);
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (!gameState.isCharacterCreationMode()) {
            return;
        }

        char typed = e.getKeyChar();

        if (Character.isISOControl(typed) || characterName.length() >= 16) {
            return;
        }

        if (Character.isLetterOrDigit(typed) || typed == ' ' || typed == '-' || typed == '_') {
            characterName += typed;
            characterCreationMessage = "";
            repaint();
        }
    }
}

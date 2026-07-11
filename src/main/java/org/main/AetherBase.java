package org.main;

import org.main.battle.*;
import org.main.content.EnvironmentLibrary;
import org.main.content.PlayerClassLibrary;
import org.main.core.*;
import org.main.engine.*;
import org.main.ui.AetherMenuScreens;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;

public class AetherBase extends JPanel implements KeyListener {

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();
    private final OverworldHud overworldHud = new OverworldHud();

    private final MovementEngine movementEngine = new MovementEngine();
    private final SoundSystem soundSystem = new SoundSystem();
    private final EnvironmentLibrary environment = EnvironmentLibrary.STARTER_DUNGEON;

    private final BattleRenderer battleRenderer = new BattleRenderer();

    private final InteractionSystem.InteractionWindow interactionWindow =
            new InteractionSystem.InteractionWindow(soundSystem);
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
    private String gameOverMessage = "";
    private boolean gameOverMusicStarted = false;
    private final Image gameOverPreview = AssetLoader.loadImage("assets/images/ui/02_PreviewUI/Battle_over.png");
    private static final String LEVEL_UP_JINGLE_PATH = null;
    private static final String GAME_OVER_MUSIC_PATH = null;

    public AetherBase() {
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

                if (gameState.isGameOverMode()) {
                    handleGameOverMousePressed(e.getPoint());
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

        if (gameState.isGameOverMode() && !gameOverMusicStarted) {
            soundSystem.stopAll();
            soundSystem.playMusic(GAME_OVER_MUSIC_PATH);
            gameOverMusicStarted = true;
        }

        gameState.updateMovementAnimation(deltaMs);
        gameState.updateFishing(deltaMs);

        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }

        if (gameState.isDungeonMode()
                && gameState.isLevelUpPending()
                && !gameState.hasActiveInteraction()
                && !gameState.hasActiveShop()) {
            gameState.setLevelUpPending(false);
            soundSystem.playSound(LEVEL_UP_JINGLE_PATH);
            gameState.openInteraction(InteractionSystem.levelUpMenu(gameState));
        }

        lastUpdateMs = (System.nanoTime() - updateStart) / 1_000_000.0;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Aether");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new AetherBase());
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
            AetherMenuScreens.drawStartMenu(g2, getWidth(), getHeight(), startMenuMessage);
            lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
            return;
        }

        if (gameState.isCharacterCreationMode()) {
            AetherMenuScreens.drawCharacterCreation(
                    g2,
                    getWidth(),
                    getHeight(),
                    characterName,
                    characterCreationMessage,
                    selectedPlayerClass
            );
            lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
            return;
        }

        if (gameState.isGameOverMode()) {
            AetherMenuScreens.drawGameOver(g2, getWidth(), getHeight(), gameOverPreview, gameOverMessage);
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

    private void handleGameOverMousePressed(Point point) {
        requestFocusInWindow();

        if (AetherMenuScreens.gameOverButtonBounds(getWidth(), getHeight(), 0).contains(point)) {
            returnToMainMenu();
            return;
        }

        if (AetherMenuScreens.gameOverButtonBounds(getWidth(), getHeight(), 1).contains(point)) {
            loadGameFromGameOver();
        }
    }

    private void returnToMainMenu() {
        gameOverMessage = "";
        soundSystem.stopAll();
        gameOverMusicStarted = false;
        gameState.setGameMode(GameState.GameMode.START_MENU);
        repaint();
    }

    private void handleStartMenuMousePressed(Point point) {
        requestFocusInWindow();

        if (AetherMenuScreens.startMenuButtonBounds(getWidth(), getHeight(), 0).contains(point)) {
            startNewGame();
            return;
        }

        if (AetherMenuScreens.startMenuButtonBounds(getWidth(), getHeight(), 1).contains(point)) {
            loadGameFromStartMenu();
            return;
        }

        if (AetherMenuScreens.startMenuButtonBounds(getWidth(), getHeight(), 2).contains(point)) {
            quitGame();
        }
    }

    private void startNewGame() {
        startMenuMessage = "";
        characterCreationMessage = "";
        gameState.setGameMode(GameState.GameMode.CHARACTER_CREATION);
        repaint();
    }

    private void handleCharacterCreationMousePressed(Point point) {
        requestFocusInWindow();

        int index = 0;
        for (PlayerClassLibrary playerClass : PlayerClassLibrary.values()) {
            if (AetherMenuScreens.classButtonBounds(getWidth(), index).contains(point)) {
                selectedPlayerClass = playerClass;
                repaint();
                return;
            }

            index++;
        }

        if (AetherMenuScreens.confirmCharacterButtonBounds(getWidth(), getHeight()).contains(point)) {
            confirmCharacterCreation();
            return;
        }

        if (AetherMenuScreens.backCharacterButtonBounds(getWidth(), getHeight()).contains(point)) {
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

        if (gameState.isGameOverMode()) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                loadGameFromGameOver();
            }

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                returnToMainMenu();
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
            gameOverMusicStarted = false;
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
            gameOverMusicStarted = false;
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            repaint();
        } catch (IOException exception) {
            startMenuMessage = exception.getMessage();
            repaint();
        }
    }

    private void loadGameFromGameOver() {
        try {
            SaveSystem.load(gameState);
            gameOverMessage = "";
            gameOverMusicStarted = false;
            soundSystem.stopAll();
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            repaint();
        } catch (IOException exception) {
            gameOverMessage = exception.getMessage();
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

package org.main;

import org.main.battle.*;
import org.main.content.EnvironmentLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.core.*;
import org.main.engine.*;
import org.main.ui.AetherMenuScreens;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class AetherBase extends JPanel implements KeyListener {
    private static final int DEFAULT_WINDOW_WIDTH = 900;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;
    private static final int TARGET_FRAME_MS = 16;
    private static final double INITIAL_AVERAGE_FRAME_MS = TARGET_FRAME_MS;
    private static final double FRAME_AVERAGE_PREVIOUS_WEIGHT = 0.90;
    private static final double FRAME_AVERAGE_CURRENT_WEIGHT = 0.10;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final int MAX_CHARACTER_NAME_LENGTH = 16;
    private static final int PERFORMANCE_FONT_SIZE = 12;
    private static final int PERFORMANCE_RIGHT_MARGIN = 18;
    private static final int PERFORMANCE_TOP_MARGIN = 14;
    private static final int PERFORMANCE_BACKGROUND_HORIZONTAL_PADDING = 6;
    private static final int PERFORMANCE_BACKGROUND_TOP_OFFSET = 2;
    private static final int PERFORMANCE_BACKGROUND_EXTRA_HEIGHT = 8;
    private static final int PERFORMANCE_BACKGROUND_ALPHA = 150;
    private static final Color PERFORMANCE_TEXT_COLOR = new Color(220, 240, 220);

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
    private double averageFrameMs = INITIAL_AVERAGE_FRAME_MS;
    private double lastUpdateMs = 0.0;
    private double lastRenderMs = 0.0;
    private String startMenuMessage = "";
    private PlayerRegionLibrary selectedPlayerRegion = PlayerRegionLibrary.MIDLANDS;
    private String characterName = "Player";
    private String characterCreationMessage = "";
    private String gameOverMessage = "";
    private boolean gameOverMusicStarted = false;
    private final Image gameOverCover = AssetLoader.loadImage("assets/images/ui/01_UI_Resources/01Battle/battle_gameover_cover.png");
    private final Image gameOverTitleBackground = AssetLoader.loadImage("assets/images/ui/01_UI_Resources/01Battle/battle_gameover_bg.png");
    private static final String LEVEL_UP_JINGLE_PATH = null;
    private static final String GAME_OVER_MUSIC_PATH = null;

    public AetherBase() {
        setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
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

        Timer timer = new Timer(TARGET_FRAME_MS, e -> {
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
        averageFrameMs = averageFrameMs * FRAME_AVERAGE_PREVIOUS_WEIGHT
                + deltaMs * FRAME_AVERAGE_CURRENT_WEIGHT;

        if (gameState.isGameOverMode() && !gameOverMusicStarted) {
            soundSystem.stopAll();
            soundSystem.playMusic(GAME_OVER_MUSIC_PATH);
            gameOverMusicStarted = true;
        }

        gameState.updateMovementAnimation(deltaMs);
        gameState.updateResourceNodes(deltaMs);
        gameState.updateFishing(deltaMs);
        gameState.updateMining(deltaMs);
        gameState.updateCooking(deltaMs);
        gameState.updateSmelting(deltaMs);
        battleController.update();

        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }

        /*
         * Class-style leveling is paused while limb progression replaces classes.
         * Keep the old menu/system in place for later design decisions, but do
         * not surface it during normal play.
         */

        lastUpdateMs = (System.nanoTime() - updateStart) / NANOS_PER_MILLISECOND;
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
            lastRenderMs = (System.nanoTime() - renderStart) / NANOS_PER_MILLISECOND;
            return;
        }

        if (gameState.isCharacterCreationMode()) {
            AetherMenuScreens.drawCharacterCreation(
                    g2,
                    getWidth(),
                    getHeight(),
                    characterName,
                    characterCreationMessage,
                    selectedPlayerRegion
            );
            lastRenderMs = (System.nanoTime() - renderStart) / NANOS_PER_MILLISECOND;
            return;
        }

        if (gameState.isGameOverMode()) {
            AetherMenuScreens.drawGameOver(g2, getWidth(), getHeight(), gameOverCover, gameOverTitleBackground, gameOverMessage);
            lastRenderMs = (System.nanoTime() - renderStart) / NANOS_PER_MILLISECOND;
            return;
        }

        if (gameState.isDungeonMode()) {
            dungeonRenderer.setPlayerCharacter(gameState.getPlayerCharacter());
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

            if (gameState.hasActiveInteraction() && gameState.isInventoryOverlayAllowed()) {
                interactionWindow.draw(
                        g2,
                        gameState.getActiveInteraction(),
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }

            if (gameState.isInventoryOpen()) {
                inventoryPanel.draw(
                        g2,
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }

            overworldHud.draw(g2, gameState, getWidth(), getHeight());

            if (gameState.isInventoryOpen() && inventoryPanel.hasActiveExamineInteraction()) {
                interactionWindow.draw(
                        g2,
                        inventoryPanel.getActiveExamineInteraction(),
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }

            if (gameState.hasActiveShop()) {
                shopWindow.draw(g2, gameState, getWidth(), getHeight());
            }

            if (gameState.hasActiveInteraction() && !gameState.isInventoryOverlayAllowed()) {
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

        lastRenderMs = (System.nanoTime() - renderStart) / NANOS_PER_MILLISECOND;

        if (gameState.isPerformanceOverlayVisible()) {
            drawPerformanceOverlay(g2);
        }
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
            loadCustomMapFromStartMenu();
            return;
        }

        if (AetherMenuScreens.startMenuButtonBounds(getWidth(), getHeight(), 3).contains(point)) {
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
        for (PlayerRegionLibrary playerRegion : PlayerRegionLibrary.values()) {
            if (AetherMenuScreens.regionButtonBounds(getWidth(), index).contains(point)) {
                selectedPlayerRegion = playerRegion;
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

        gameState.setPlayerCharacter(GameBootstrap.createPlayerCharacter(trimmedName, selectedPlayerRegion));
        gameState.changeDungeon(DungeonMap.testMap(), 1, 1, List.of());
        applyStarterEnvironmentThemes();
        GameBootstrap.seedTestContent(gameState);
        gameState.setGameMode(GameState.GameMode.DUNGEON);
        soundSystem.playAmbience(environment.getAmbienceSoundPath());
        repaint();
    }

    private void drawPerformanceOverlay(Graphics2D g) {
        int fps = averageFrameMs <= 0.0 ? 0 : (int) Math.round(MILLIS_PER_SECOND / averageFrameMs);
        DifficultyResolver.DifficultyRating playerRating = DifficultyResolver.ratePlayer(gameState.getPlayerCharacter());
        String enemyDifficultyLine = gameState.isDungeonMode()
                ? dungeonRenderer.getLastVisibleEnemyDifficultyDebugLine()
                : "Enemy n/a";
        String[] lines = {
                "FPS " + fps,
                String.format("Frame %.1f ms", averageFrameMs),
                String.format("Update %.2f ms", lastUpdateMs),
                String.format("Render %.2f ms", lastRenderMs),
                "Build Lv " + playerRating.level(),
                String.format("Power %.1f", playerRating.power()),
                enemyDifficultyLine,
                "Entities " + gameState.getEntities().size(),
                "Map " + gameState.getDungeonMap().getWidth() + "x" + gameState.getDungeonMap().getHeight()
        };

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, PERFORMANCE_FONT_SIZE));
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int width = 0;

        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }

        int x = getWidth() - width - PERFORMANCE_RIGHT_MARGIN;
        int y = PERFORMANCE_TOP_MARGIN;
        int height = lineHeight * lines.length + PERFORMANCE_BACKGROUND_EXTRA_HEIGHT;

        g.setColor(new Color(0, 0, 0, PERFORMANCE_BACKGROUND_ALPHA));
        g.fillRect(
                x - PERFORMANCE_BACKGROUND_HORIZONTAL_PADDING,
                y - PERFORMANCE_BACKGROUND_TOP_OFFSET,
                width + PERFORMANCE_BACKGROUND_HORIZONTAL_PADDING * 2,
                height
        );
        g.setColor(PERFORMANCE_TEXT_COLOR);

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

        InputBindings bindings = gameState.getInputBindings();
        int keyCode = e.getKeyCode();

        if (bindings.matches(InputBindings.Action.INVENTORY, keyCode)
                && gameState.isDungeonMode()
                && gameState.isInventoryOverlayAllowed()) {
            gameState.toggleInventory();
            repaint();
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
        List<InteractionSystem.InteractionOption> options = new java.util.ArrayList<>();
        options.add(InteractionSystem.option("Saved Game", this::loadSavedGameFromMenu));

        try {
            List<Path> mapPaths = MapDesignLibrary.listSavedMaps();
            if (mapPaths.isEmpty()) {
                options.add(InteractionSystem.closeOption("No authored maps found"));
            }

            for (Path mapPath : mapPaths) {
                options.add(InteractionSystem.option("Map: " + mapLoadLabel(mapPath), () -> loadAuthoredMapFromMenu(mapPath)));
            }
        } catch (IOException exception) {
            options.add(InteractionSystem.closeOption("No authored maps found"));
        }

        options.add(InteractionSystem.closeOption("Cancel"));
        gameState.openInteraction(InteractionSystem.prompt(
                "Load",
                "Choose what to load.",
                options.toArray(new InteractionSystem.InteractionOption[0])
        ));
    }

    private void loadSavedGameFromMenu() {
        try {
            SaveSystem.load(gameState);
            applyLoadedEnvironmentThemes();
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

    private void loadAuthoredMapFromMenu(Path mapPath) {
        try {
            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(mapPath);
            gameState.changeDungeon(mapDesign, mapPath);
            applyMapEnvironmentThemes(mapDesign);
            gameState.setGameMode(GameState.GameMode.DUNGEON);
            gameOverMusicStarted = false;
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            gameState.openInteraction(InteractionSystem.prompt(
                    "Loaded Map",
                    "Loaded " + mapDesign.displayName() + ".",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Map Load Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private String mapLoadLabel(Path mapPath) {
        try {
            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(mapPath);
            if (mapDesign.description().isBlank()) {
                return mapDesign.displayName();
            }

            return mapDesign.displayName() + " - " + mapDesign.description();
        } catch (IOException exception) {
            return mapPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        }
    }

    private void loadGameFromStartMenu() {
        try {
            SaveSystem.load(gameState);
            applyLoadedEnvironmentThemes();
            startMenuMessage = "";
            gameOverMusicStarted = false;
            soundSystem.playAmbience(environment.getAmbienceSoundPath());
            repaint();
        } catch (IOException exception) {
            startMenuMessage = exception.getMessage();
            repaint();
        }
    }

    private void loadCustomMapFromStartMenu() {
        try {
            Files.createDirectories(MapDesignLibrary.MAP_FOLDER);
            JFileChooser chooser = new JFileChooser(MapDesignLibrary.MAP_FOLDER.toFile());
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(chooser.getSelectedFile().toPath());
            gameState.setPlayerCharacter(GameBootstrap.createDefaultPlayerCharacter());
            gameState.changeDungeon(mapDesign, chooser.getSelectedFile().toPath());
            applyMapEnvironmentThemes(mapDesign);
            startMenuMessage = "";
            gameOverMusicStarted = false;
            gameState.setGameMode(GameState.GameMode.DUNGEON);
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
            applyLoadedEnvironmentThemes();
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

    private void applyStarterEnvironmentThemes() {
        dungeonRenderer.setEnvironmentThemes(environment.getThemes());
    }

    private void applyLoadedEnvironmentThemes() {
        Path mapDesignPath = gameState.getCurrentMapDesignPath();
        if (mapDesignPath == null) {
            applyStarterEnvironmentThemes();
            return;
        }

        try {
            applyMapEnvironmentThemes(MapDesignLibrary.load(mapDesignPath));
        } catch (IOException exception) {
            applyStarterEnvironmentThemes();
        }
    }

    private void applyMapEnvironmentThemes(MapDesignLibrary.MapDesign mapDesign) {
        if (mapDesign == null) {
            applyStarterEnvironmentThemes();
            return;
        }

        dungeonRenderer.setEnvironmentThemes(List.of(
                mapDesign.primaryTheme().getTheme(),
                mapDesign.alternateTheme().getTheme()
        ));
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

        if (Character.isISOControl(typed) || characterName.length() >= MAX_CHARACTER_NAME_LENGTH) {
            return;
        }

        if (Character.isLetterOrDigit(typed) || typed == ' ' || typed == '-' || typed == '_') {
            characterName += typed;
            characterCreationMessage = "";
            repaint();
        }
    }
}

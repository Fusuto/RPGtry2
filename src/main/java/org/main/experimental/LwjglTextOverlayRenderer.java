package org.main.experimental;

import org.main.battle.BattleEncounter;
import org.main.core.AetherGameRuntime;
import org.main.core.GameState;
import org.main.core.InteractionSystem;
import org.main.core.InventorySystem;
import org.main.core.MiniMapRenderer;
import org.main.core.OverworldHud;
import org.main.core.ShopSystem;
import org.main.engine.AssetLoader;
import org.main.ui.AetherMenuScreens;

import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.main.content.PlayerRegionLibrary;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glVertex2f;

public final class LwjglTextOverlayRenderer {
    private static final int MAX_CHARACTER_NAME_LENGTH = 16;
    private static final Color PANEL = new Color(10, 10, 14, 188);
    private static final Color PANEL_BORDER = new Color(196, 168, 98, 220);
    private static final Color TEXT = new Color(236, 234, 222);
    private static final Color MUTED = new Color(172, 170, 162);
    private static final Color DANGER = new Color(255, 116, 102);
    private static final Color DIFFICULTY_TRIVIAL_COLOR = new Color(165, 170, 175);
    private static final Color DIFFICULTY_EASY_COLOR = new Color(86, 205, 105);
    private static final Color DIFFICULTY_FAIR_COLOR = new Color(235, 215, 92);
    private static final Color DIFFICULTY_DANGEROUS_COLOR = new Color(235, 145, 58);
    private static final Color DIFFICULTY_DEADLY_COLOR = new Color(230, 70, 70);

    private final Font titleFont = new Font(Font.MONOSPACED, Font.BOLD, 18);
    private final Font bodyFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private final Font smallFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();
    private final OverworldHud overworldHud = new OverworldHud();
    private final Canvas mouseEventSource = new Canvas();
    private final ShopSystem.ShopWindow shopWindow = new ShopSystem.ShopWindow();
    private final Image gameOverCover = AssetLoader.loadImage("assets/images/ui/01_UI_Resources/01Battle/battle_gameover_cover.png");
    private final Image gameOverTitleBackground = AssetLoader.loadImage("assets/images/ui/01_UI_Resources/01Battle/battle_gameover_bg.png");

    private int textureId;
    private int textureWidth;
    private int textureHeight;
    private ByteBuffer uploadBuffer;
    private BufferedImage overlayImage;
    private Graphics2D overlayGraphics;
    private int[] uploadedPixels;
    private final List<OverlayAction> overlayActions = new ArrayList<>();
    private Runnable quitAction = () -> {
    };
    private boolean customMapPickerOpen = false;
    private String customMapMessage = "";
    private Runnable mapChangedAction = () -> {
    };
    private boolean characterCreationInputActive = false;
    private String characterName = "Player";
    private PlayerRegionLibrary selectedPlayerRegion = PlayerRegionLibrary.MIDLANDS;
    private String characterCreationMessage = "";
    private String gameOverMessage = "";
    private ScrollTarget activeScrollTarget = ScrollTarget.NONE;
    private int customMapScroll;
    private int lastOverlayWidth;
    private int lastOverlayHeight;
    private InventorySystem.InventoryPanel inventoryPanel;
    private GameState inventoryPanelGameState;
    private InteractionSystem.InteractionWindow interactionWindow;
    private AetherGameRuntime interactionWindowRuntime;
    private List<LwjglDungeonViewport.EnemyLabel> enemyLabels = List.of();
    private List<String> viewportDebugLines = List.of();

    public void setQuitAction(Runnable quitAction) {
        this.quitAction = quitAction == null ? () -> {
        } : quitAction;
    }

    public void setMapChangedAction(Runnable mapChangedAction) {
        this.mapChangedAction = mapChangedAction == null ? () -> {
        } : mapChangedAction;
    }

    public void openCustomMapPicker() {
        customMapPickerOpen = true;
        customMapMessage = "";
    }

    public void closeCustomMapPicker() {
        customMapPickerOpen = false;
        customMapMessage = "";
    }

    public boolean isCustomMapPickerOpen() {
        return customMapPickerOpen;
    }

    public boolean selectCustomMap(AetherGameRuntime runtime, int choiceIndex) {
        if (runtime == null || choiceIndex < 0) {
            return false;
        }

        try {
            List<Path> maps = runtime.listAvailableMaps();
            int resolvedIndex = customMapScroll + choiceIndex;
            if (resolvedIndex >= maps.size()) {
                return false;
            }

            runtime.startCustomMap(maps.get(resolvedIndex));
            closeCustomMapPicker();
            mapChangedAction.run();
            return true;
        } catch (IOException exception) {
            customMapMessage = exception.getMessage();
            return true;
        }
    }

    public void beginCharacterCreation(GameState gameState) {
        if (gameState != null) {
            gameState.setGameMode(GameState.GameMode.CHARACTER_CREATION);
        }
        characterCreationInputActive = true;
        characterName = "Player";
        selectedPlayerRegion = PlayerRegionLibrary.MIDLANDS;
        characterCreationMessage = "";
        closeCustomMapPicker();
    }

    public void appendCharacterNameCodePoint(int codePoint) {
        if (!characterCreationInputActive) {
            return;
        }

        if (codePoint < 0
                || Character.isISOControl(codePoint)
                || characterName.length() >= MAX_CHARACTER_NAME_LENGTH
                || !isAllowedCharacterNameCodePoint(codePoint)) {
            return;
        }

        characterName += new String(Character.toChars(codePoint));
        characterCreationMessage = "";
    }

    private boolean isAllowedCharacterNameCodePoint(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == ' '
                || codePoint == '-'
                || codePoint == '_';
    }

    public void backspaceCharacterName() {
        if (characterName == null || characterName.isEmpty()) {
            return;
        }

        int previous = characterName.offsetByCodePoints(characterName.length(), -1);
        characterName = characterName.substring(0, previous);
    }

    public void cancelCharacterCreation(GameState gameState) {
        if (gameState != null) {
            gameState.setGameMode(GameState.GameMode.START_MENU);
        }
        characterCreationInputActive = false;
        characterCreationMessage = "";
    }

    public void confirmCharacterCreation(AetherGameRuntime runtime) {
        String trimmedName = characterName == null ? "" : characterName.trim();
        if (trimmedName.isBlank()) {
            characterCreationMessage = "Enter a character name.";
            return;
        }

        runtime.startNewGame(trimmedName, selectedPlayerRegion);
        characterCreationInputActive = false;
        characterCreationMessage = "";
        mapChangedAction.run();
    }

    public void selectPlayerRegion(PlayerRegionLibrary playerRegion) {
        if (playerRegion != null) {
            selectedPlayerRegion = playerRegion;
        }
    }

    public void setGameOverMessage(String gameOverMessage) {
        this.gameOverMessage = gameOverMessage == null ? "" : gameOverMessage;
    }

    public void setStartMenuMessage(String startMenuMessage) {
        this.customMapMessage = startMenuMessage == null ? "" : startMenuMessage;
    }

    public void setEnemyLabels(List<LwjglDungeonViewport.EnemyLabel> enemyLabels) {
        this.enemyLabels = enemyLabels == null ? List.of() : List.copyOf(enemyLabels);
    }

    public void setViewportDebugLines(List<String> viewportDebugLines) {
        this.viewportDebugLines = viewportDebugLines == null ? List.of() : List.copyOf(viewportDebugLines);
    }

    public void render(AetherGameRuntime runtime, int width, int height) {
        if (runtime == null || width <= 0 || height <= 0) {
            return;
        }

        overlayActions.clear();
        ensureOverlaySurface(width, height);
        clearOverlaySurface(width, height);
        drawGameOverlay(overlayGraphics, runtime, width, height);
        uploadChangedRegion();
        drawOverlayQuad(width, height);
    }

    public void shutdown() {
        if (overlayGraphics != null) {
            overlayGraphics.dispose();
            overlayGraphics = null;
        }
        overlayImage = null;
        uploadedPixels = null;
        uploadBuffer = null;
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    public boolean handleMouseClick(int x, int y) {
        return handleMouseClick(x, y, null);
    }

    public boolean handleMouseClick(int x, int y, AetherGameRuntime runtime) {
        return handleMousePressed(x, y, MouseEvent.BUTTON1, runtime);
    }

    public boolean handleMousePressed(int x, int y, int button, AetherGameRuntime runtime) {
        if (runtime != null && runtime.gameState().isDungeonMode()) {
            GameState gameState = runtime.gameState();
            java.awt.Point point = new java.awt.Point(x, y);
            boolean primaryClick = button == MouseEvent.BUTTON1;

            if (gameState.isCharacterMenuOverlayAllowed()
                    && primaryClick
                    && overworldHud.handleMousePressed(
                    point,
                    gameState,
                    lastOverlayWidth,
                    lastOverlayHeight,
                    () -> openConfigMenu(runtime, gameState)
            )) {
                return true;
            }

            if (gameState.isInventoryOpen()) {
                if (!gameState.isCharacterMenuOverlayAllowed()
                        && primaryClick
                        && overworldHud.handleInventoryButtonPressed(point, gameState, lastOverlayWidth, lastOverlayHeight)) {
                    return true;
                }
                InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
                if (panel != null) {
                    panel.handleMousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button));
                }
                return true;
            }

            if (gameState.hasActiveInteraction()) {
                ensureInteractionWindow(runtime).handleMousePressed(
                        mouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button),
                        gameState.getActiveInteraction()
                );
                return true;
            }

            if (gameState.hasActiveShop()) {
                shopWindow.handleMousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button), gameState);
                return true;
            }

            if (primaryClick && overworldHud.handleMousePressed(
                    point,
                    gameState,
                    lastOverlayWidth,
                    lastOverlayHeight,
                    () -> openConfigMenu(runtime, gameState)
            )) {
                return true;
            }
        }

        if (handleOverlayActionAt(x, y)) {
            return true;
        }

        if (runtime != null && runtime.gameState().isBattleMode()) {
            runtime.battleController().handleMouseClick(new java.awt.Point(x, y));
            return true;
        }

        return false;
    }

    public boolean handleMouseReleased(int x, int y, int button, AetherGameRuntime runtime) {
        if (runtime == null || !runtime.gameState().isDungeonMode() || !runtime.gameState().isInventoryOpen()) {
            return false;
        }

        InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
        return panel != null && panel.handleMouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, x, y, button));
    }

    public boolean handleMouseDragged(int x, int y, AetherGameRuntime runtime) {
        if (runtime == null || !runtime.gameState().isDungeonMode() || !runtime.gameState().isInventoryOpen()) {
            return false;
        }

        InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
        return panel != null && panel.handleMouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, x, y, MouseEvent.BUTTON1));
    }

    private boolean handleOverlayActionAt(int x, int y) {
        for (int i = overlayActions.size() - 1; i >= 0; i--) {
            OverlayAction action = overlayActions.get(i);
            if (action.bounds().contains(x, y)) {
                action.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseWheel(double yOffset) {
        return handleMouseWheel(yOffset, -1, -1);
    }

    public boolean handleMouseWheel(double yOffset, int mouseX, int mouseY) {
        return handleMouseWheel(yOffset, mouseX, mouseY, null);
    }

    public boolean handleMouseWheel(double yOffset, int mouseX, int mouseY, AetherGameRuntime runtime) {
        if (runtime != null && runtime.gameState().isDungeonMode()) {
            GameState gameState = runtime.gameState();
            if (gameState.isCharacterMenuOverlayAllowed()
                    && handleOverworldHudMouseWheel(yOffset, mouseX, mouseY, runtime)) {
                return true;
            }

            if (gameState.hasActiveInteraction()) {
                return ensureInteractionWindow(runtime).handleMouseWheelMoved(
                        mouseWheelEvent(yOffset, mouseX, mouseY),
                        gameState.getActiveInteraction()
                );
            }

            if (gameState.hasActiveShop()) {
                return true;
            }
        }

        if (yOffset == 0.0 || activeScrollTarget != ScrollTarget.CUSTOM_MAP) {
            return handleOverworldHudMouseWheel(yOffset, mouseX, mouseY, runtime);
        }

        int delta = yOffset < 0.0 ? 1 : -1;
        customMapScroll = Math.max(0, customMapScroll + delta);
        return true;
    }

    private boolean handleOverworldHudMouseWheel(double yOffset, int mouseX, int mouseY, AetherGameRuntime runtime) {
        if (runtime == null || !runtime.gameState().isDungeonMode()) {
            return false;
        }

        int wheelRotation = yOffset < 0.0 ? 1 : -1;
        java.awt.event.MouseWheelEvent event = new java.awt.event.MouseWheelEvent(
                new java.awt.Canvas(),
                java.awt.event.MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                0,
                mouseX,
                mouseY,
                0,
                false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                wheelRotation
        );
        return overworldHud.handleMouseWheelMoved(event, runtime.gameState(), lastOverlayWidth, lastOverlayHeight);
    }

    public void handleMouseMoved(int x, int y, AetherGameRuntime runtime) {
        if (runtime == null || !runtime.gameState().isDungeonMode()) {
            return;
        }

        GameState gameState = runtime.gameState();
        if (gameState.isCharacterMenuOverlayAllowed()) {
            overworldHud.handleMouseMoved(new java.awt.Point(x, y), gameState);
            if (gameState.isInventoryOpen()) {
                InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
                if (panel != null && panel.handleMouseMoved(mouseEvent(MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON))) {
                    return;
                }
            }
        }

        if (gameState.hasActiveInteraction()) {
            ensureInteractionWindow(runtime).handleMouseMoved(
                    new java.awt.Point(x, y),
                    gameState.getActiveInteraction()
            );
            return;
        }

        if (gameState.isInventoryOpen()) {
            InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
            if (panel != null && panel.handleMouseMoved(mouseEvent(MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON))) {
                return;
            }
        }

        overworldHud.handleMouseMoved(new java.awt.Point(x, y), gameState);
    }

    private void drawGameOverlay(Graphics2D graphics, AetherGameRuntime runtime, int width, int height) {
        lastOverlayWidth = width;
        lastOverlayHeight = height;
        GameState gameState = runtime.gameState();
        activeScrollTarget = ScrollTarget.NONE;

        if (gameState.isStartMenuMode()) {
            if (customMapPickerOpen) {
                drawCustomMapPicker(graphics, runtime, width, height);
                return;
            }
            drawStartMenu(graphics, runtime, width, height, false);
            return;
        }

        if (gameState.isGameOverMode()) {
            drawStartMenu(graphics, runtime, width, height, true);
            return;
        }

        if (gameState.isCharacterCreationMode()) {
            drawCharacterCreation(graphics, runtime, width, height);
            return;
        }

        if (gameState.isBattleMode()) {
            drawBattle(graphics, runtime, width, height);
            if (gameState.isPerformanceOverlayVisible()) {
                drawDebugHud(graphics, runtime, width);
            }
            return;
        }

        InteractionSystem.Interaction interaction = gameState.getActiveInteraction();
        int contentHeight = Math.max(1, height - overworldHud.getBottomReservedHeight());

        drawEnemyLabels(graphics, width);
        miniMapRenderer.draw(graphics, gameState);

        if (interaction != null && interaction.isInventoryOverlayAllowed()) {
            ensureInteractionWindow(runtime).draw(graphics, interaction, width, contentHeight);
        }

        if (gameState.isInventoryOpen()) {
            InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
            if (panel != null) {
                panel.draw(graphics, width, contentHeight);
            }
        }

        overworldHud.draw(graphics, gameState, width, height);

        if (gameState.getActiveShop() != null) {
            shopWindow.draw(graphics, gameState, width, height);
        }

        if (interaction != null && !interaction.isInventoryOverlayAllowed()) {
            ensureInteractionWindow(runtime).draw(graphics, interaction, width, contentHeight);
        }

        if (gameState.isPerformanceOverlayVisible()) {
            drawDebugHud(graphics, runtime, width);
        }
    }

    private InventorySystem.InventoryPanel ensureInventoryPanel(AetherGameRuntime runtime) {
        if (runtime == null || runtime.gameState() == null) {
            return null;
        }

        GameState gameState = runtime.gameState();
        if (inventoryPanel == null || inventoryPanelGameState != gameState) {
            inventoryPanel = new InventorySystem.InventoryPanel(
                    gameState.getInventory(),
                    gameState,
                    runtime.soundSystem()
            );
            inventoryPanelGameState = gameState;
        }
        return inventoryPanel;
    }

    private InteractionSystem.InteractionWindow ensureInteractionWindow(AetherGameRuntime runtime) {
        if (interactionWindow == null || interactionWindowRuntime != runtime) {
            interactionWindow = new InteractionSystem.InteractionWindow(runtime == null ? null : runtime.soundSystem());
            interactionWindowRuntime = runtime;
        }
        return interactionWindow;
    }

    private MouseEvent mouseEvent(int eventId, int x, int y, int button) {
        if (button == MouseEvent.NOBUTTON) {
            return new MouseEvent(
                    mouseEventSource,
                    eventId,
                    System.currentTimeMillis(),
                    0,
                    x,
                    y,
                    0,
                    false,
                    MouseEvent.NOBUTTON
            );
        }

        int safeButton = button == MouseEvent.BUTTON2 || button == MouseEvent.BUTTON3
                ? button
                : MouseEvent.BUTTON1;
        int modifiers = switch (safeButton) {
            case MouseEvent.BUTTON2 -> MouseEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3 -> MouseEvent.BUTTON3_DOWN_MASK;
            default -> MouseEvent.BUTTON1_DOWN_MASK;
        };
        return new MouseEvent(
                mouseEventSource,
                eventId,
                System.currentTimeMillis(),
                modifiers,
                x,
                y,
                1,
                false,
                safeButton
        );
    }

    private java.awt.event.MouseWheelEvent mouseWheelEvent(double yOffset, int mouseX, int mouseY) {
        int wheelRotation = yOffset < 0.0 ? 1 : -1;
        return new java.awt.event.MouseWheelEvent(
                mouseEventSource,
                java.awt.event.MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                0,
                mouseX,
                mouseY,
                0,
                false,
                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                wheelRotation
        );
    }

    private void openConfigMenu(AetherGameRuntime runtime, GameState gameState) {
        if (runtime == null || gameState == null) {
            return;
        }

        gameState.closeInventory();
        gameState.closeSkills();
        gameState.closeQuests();
        gameState.closeStats();
        gameState.openInteraction(InteractionSystem.configMenu(
                runtime.soundSystem(),
                gameState,
                quitAction,
                () -> gameState.openInteraction(InteractionSystem.controlsMenu(gameState.getInputBindings())),
                () -> saveGameFromHud(runtime, gameState),
                () -> openLoadMenu(runtime, gameState)
        ));
    }

    private void saveGameFromHud(AetherGameRuntime runtime, GameState gameState) {
        try {
            runtime.saveGame();
            gameState.openInteraction(InteractionSystem.prompt(
                    "Saved",
                    "Game saved to " + org.main.core.SaveSystem.getSavePath() + ".",
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

    private void openLoadMenu(AetherGameRuntime runtime, GameState gameState) {
        List<InteractionSystem.InteractionOption> options = new ArrayList<>();
        options.add(InteractionSystem.option("Saved Game", () -> {
            try {
                runtime.loadGame();
                mapChangedAction.run();
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
        }));

        try {
            for (Path mapPath : runtime.listAvailableMaps()) {
                options.add(InteractionSystem.option("Map: " + runtime.describeMap(mapPath), () -> {
                    try {
                        org.main.content.MapDesignLibrary.MapDesign mapDesign = runtime.loadAuthoredMap(mapPath);
                        mapChangedAction.run();
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
                }));
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

    private void drawDebugHud(Graphics2D graphics, AetherGameRuntime runtime, int width) {
        GameState gameState = runtime.gameState();
        List<String> lines = new ArrayList<>();
        lines.addAll(viewportDebugLines);
        lines.add("Mode " + gameState.getGameMode());
        lines.add("Map " + gameState.getDungeonMap().getWidth() + "x" + gameState.getDungeonMap().getHeight());
        lines.add("Entities " + gameState.getEntities().size());
        lines.add("Minimap " + gameState.getMiniMapMode());
        lines.add("Audio " + audioSummary(runtime));
        graphics.setFont(smallFont);
        FontMetrics metrics = graphics.getFontMetrics();
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, metrics.stringWidth(line));
        }
        int x = Math.max(18, width - boxWidth - 36);
        int y = 112;
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(x - 8, y - 16, boxWidth + 16, lines.size() * 16 + 10, 8, 8);
        graphics.setColor(MUTED);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(lines.get(i), x, y + i * 16);
        }
    }

    private String audioSummary(AetherGameRuntime runtime) {
        if (runtime == null || runtime.soundSystem() == null) {
            return "none";
        }

        org.main.engine.SoundSystem soundSystem = runtime.soundSystem();
        return "A:" + onOff(soundSystem.isAmbienceRunning())
                + " M:" + onOff(soundSystem.isMusicRunning())
                + " L:" + onOff(soundSystem.isLoopingSoundRunning())
                + " "
                + percent(soundSystem.getAmbienceVolume())
                + "/"
                + percent(soundSystem.getMusicVolume())
                + "/"
                + percent(soundSystem.getSoundEffectVolume());
    }

    private String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private int percent(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }

    private void drawEnemyLabels(Graphics2D graphics, int viewWidth) {
        if (enemyLabels.isEmpty()) {
            return;
        }

        Font previousFont = graphics.getFont();
        graphics.setFont(previousFont.deriveFont(Font.BOLD, 11f));
        FontMetrics metrics = graphics.getFontMetrics();
        int paddingX = 5;
        int paddingY = 2;

        for (LwjglDungeonViewport.EnemyLabel label : enemyLabels) {
            if (label == null || label.text() == null || label.text().isBlank()) {
                continue;
            }

            int labelWidth = metrics.stringWidth(label.text()) + paddingX * 2;
            int labelHeight = metrics.getHeight() + paddingY * 2;
            int x = Math.max(4, Math.min(viewWidth - labelWidth - 4, label.x() - labelWidth / 2));
            int y = Math.max(4, label.y() - labelHeight - 3);

            graphics.setColor(new Color(0, 0, 0, 165));
            graphics.fillRoundRect(x, y, labelWidth, labelHeight, 6, 6);
            graphics.setColor(colorForDifficultyBand(label.band()));
            graphics.drawRoundRect(x, y, labelWidth, labelHeight, 6, 6);
            graphics.drawString(label.text(), x + paddingX, y + paddingY + metrics.getAscent());
        }

        graphics.setFont(previousFont);
    }

    private Color colorForDifficultyBand(org.main.battle.DifficultyResolver.DifficultyBand band) {
        if (band == null) {
            return DIFFICULTY_FAIR_COLOR;
        }

        return switch (band) {
            case TRIVIAL -> DIFFICULTY_TRIVIAL_COLOR;
            case EASY -> DIFFICULTY_EASY_COLOR;
            case FAIR -> DIFFICULTY_FAIR_COLOR;
            case DANGEROUS -> DIFFICULTY_DANGEROUS_COLOR;
            case DEADLY -> DIFFICULTY_DEADLY_COLOR;
        };
    }

    private void drawStartMenu(Graphics2D graphics, AetherGameRuntime runtime, int width, int height, boolean gameOver) {
        if (gameOver) {
            AetherMenuScreens.drawGameOver(graphics, width, height, gameOverCover, gameOverTitleBackground, gameOverMessage);
            overlayActions.add(new OverlayAction(AetherMenuScreens.gameOverButtonBounds(width, height, 0), () -> {
                gameOverMessage = "";
                runtime.returnToMainMenu();
            }));
            overlayActions.add(new OverlayAction(AetherMenuScreens.gameOverButtonBounds(width, height, 1), () -> {
                try {
                    runtime.loadGame();
                    gameOverMessage = "";
                    mapChangedAction.run();
                } catch (IOException exception) {
                    gameOverMessage = exception.getMessage();
                }
            }));
            return;
        }

        AetherMenuScreens.drawStartMenu(graphics, width, height, customMapMessage);
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 0), () -> beginCharacterCreation(runtime.gameState())));
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 1), () -> {
            try {
                runtime.loadGame();
                customMapPickerOpen = false;
                customMapMessage = "";
                mapChangedAction.run();
            } catch (IOException exception) {
                customMapMessage = exception.getMessage();
            }
        }));
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 2), this::openCustomMapPicker));
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 3), quitAction));
    }

    private void drawCustomMapPicker(Graphics2D graphics, AetherGameRuntime runtime, int width, int height) {
        activeScrollTarget = ScrollTarget.CUSTOM_MAP;
        int panelWidth = Math.min(760, width - 80);
        int panelHeight = Math.min(520, height - 120);
        int x = (width - panelWidth) / 2;
        int y = 58;
        drawPanel(graphics, x, y, panelWidth, panelHeight, "Custom Map");

        drawMenuButton(graphics, x + panelWidth - 82, y + 14, 58, 26, "Back", () -> {
            closeCustomMapPicker();
        });

        List<Path> maps;
        try {
            maps = runtime.listAvailableMaps();
        } catch (IOException exception) {
            maps = List.of();
            customMapMessage = exception.getMessage();
        }

        graphics.setFont(smallFont);
        graphics.setColor(MUTED);
        graphics.drawString("Choose a packaged or saved map to launch directly.", x + 24, y + 54);

        int rowY = y + 78;
        int rowHeight = 30;
        int maxRows = Math.max(1, (panelHeight - 126) / rowHeight);
        if (maps.isEmpty()) {
            graphics.setFont(bodyFont);
            graphics.setColor(TEXT);
            graphics.drawString("No maps found.", x + 24, rowY + 22);
        }

        customMapScroll = clampScroll(customMapScroll, maps.size(), maxRows);
        for (int visibleIndex = 0; visibleIndex < maxRows && customMapScroll + visibleIndex < maps.size(); visibleIndex++) {
            int i = customMapScroll + visibleIndex;
            Path mapPath = maps.get(i);
            String label = (i + 1) + ". " + runtime.describeMap(mapPath);
            drawMenuButton(graphics, x + 24, rowY + visibleIndex * rowHeight, panelWidth - 48, 24, label, () -> {
                try {
                    runtime.startCustomMap(mapPath);
                    customMapPickerOpen = false;
                    customMapMessage = "";
                    mapChangedAction.run();
                } catch (IOException exception) {
                    customMapMessage = exception.getMessage();
                }
            });
        }
        drawScrollHint(graphics, x + 24, y + panelHeight - 38, maps.size(), maxRows, customMapScroll);

        if (customMapMessage != null && !customMapMessage.isBlank()) {
            graphics.setFont(smallFont);
            graphics.setColor(DANGER);
            graphics.drawString(fitLine(graphics, customMapMessage, panelWidth - 48), x + 24, y + panelHeight - 18);
        }
    }

    private void drawCharacterCreation(Graphics2D graphics, AetherGameRuntime runtime, int width, int height) {
        AetherMenuScreens.drawCharacterCreation(
                graphics,
                width,
                height,
                characterName,
                characterCreationMessage,
                selectedPlayerRegion
        );

        PlayerRegionLibrary[] regions = PlayerRegionLibrary.values();
        for (int i = 0; i < regions.length; i++) {
            PlayerRegionLibrary region = regions[i];
            overlayActions.add(new OverlayAction(AetherMenuScreens.regionButtonBounds(width, i), () -> selectedPlayerRegion = region));
        }
        overlayActions.add(new OverlayAction(AetherMenuScreens.confirmCharacterButtonBounds(width, height), () -> confirmCharacterCreation(runtime)));
        overlayActions.add(new OverlayAction(AetherMenuScreens.backCharacterButtonBounds(width, height), () -> cancelCharacterCreation(runtime.gameState())));
    }

    private void drawBattle(Graphics2D graphics, AetherGameRuntime runtime, int width, int height) {
        GameState gameState = runtime.gameState();
        BattleEncounter encounter = gameState.getCurrentEncounter();
        if (encounter == null) {
            return;
        }

        runtime.battleRenderer().drawLwjglOverlay(graphics, encounter, width, height);
    }

    private void drawPanel(Graphics2D graphics, int x, int y, int width, int height, String title) {
        graphics.setColor(PANEL);
        graphics.fillRoundRect(x, y, width, height, 10, 10);
        graphics.setColor(PANEL_BORDER);
        graphics.drawRoundRect(x, y, width, height, 10, 10);
        graphics.setFont(titleFont);
        graphics.setColor(TEXT);
        graphics.drawString(title == null || title.isBlank() ? "Aether" : title, x + 22, y + 31);
    }

    private void drawMenuButton(Graphics2D graphics, int x, int y, int width, int height, String label, Runnable action) {
        Rectangle bounds = new Rectangle(x, y, width, height);
        graphics.setColor(new Color(24, 25, 26, 220));
        graphics.fillRoundRect(x, y, width, height, 8, 8);
        graphics.setColor(PANEL_BORDER);
        graphics.drawRoundRect(x, y, width, height, 8, 8);
        graphics.setFont(bodyFont);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(TEXT);
        graphics.drawString(
                label,
                x + Math.max(8, (width - metrics.stringWidth(label)) / 2),
                y + (height + metrics.getAscent()) / 2 - 3
        );
        overlayActions.add(new OverlayAction(bounds, action));
    }

    private List<String> wrap(Graphics2D graphics, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        for (String paragraph : text.split("\\R")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (graphics.getFontMetrics().stringWidth(candidate) <= width) {
                    current = new StringBuilder(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private String fitLine(Graphics2D graphics, String line, int width) {
        if (line == null) {
            return "";
        }
        if (graphics.getFontMetrics().stringWidth(line) <= width) {
            return line;
        }
        String ellipsis = "...";
        String value = line;
        while (!value.isEmpty() && graphics.getFontMetrics().stringWidth(value + ellipsis) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private int clampScroll(int scroll, int total, int visible) {
        int max = Math.max(0, total - Math.max(1, visible));
        return Math.max(0, Math.min(scroll, max));
    }

    private void drawScrollHint(Graphics2D graphics, int x, int y, int total, int visible, int scroll) {
        if (total <= visible) {
            return;
        }

        graphics.setFont(smallFont);
        graphics.setColor(MUTED);
        int end = Math.min(total, scroll + visible);
        graphics.drawString("Showing " + (scroll + 1) + "-" + end + " of " + total + "  [mouse wheel]", x, y);
    }

    private void ensureOverlaySurface(int width, int height) {
        if (overlayImage != null
                && overlayImage.getWidth() == width
                && overlayImage.getHeight() == height) {
            return;
        }

        if (overlayGraphics != null) {
            overlayGraphics.dispose();
        }
        overlayImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        overlayGraphics = overlayImage.createGraphics();
        overlayGraphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        overlayGraphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        uploadedPixels = null;
    }

    private void clearOverlaySurface(int width, int height) {
        overlayGraphics.setComposite(AlphaComposite.Clear);
        overlayGraphics.fillRect(0, 0, width, height);
        overlayGraphics.setComposite(AlphaComposite.SrcOver);
    }

    private void uploadChangedRegion() {
        int width = overlayImage.getWidth();
        int height = overlayImage.getHeight();
        int[] pixels = ((DataBufferInt) overlayImage.getRaster().getDataBuffer()).getData();
        boolean textureNeedsAllocation = textureId == 0
                || textureWidth != width
                || textureHeight != height
                || uploadedPixels == null
                || uploadedPixels.length != pixels.length;

        ensureOverlayTexture();
        if (textureNeedsAllocation) {
            uploadRegion(pixels, width, 0, 0, width, height, true);
            uploadedPixels = pixels.clone();
            textureWidth = width;
            textureHeight = height;
            return;
        }

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int index = row + x;
                if (pixels[index] == uploadedPixels[index]) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return;
        }

        int dirtyWidth = maxX - minX + 1;
        int dirtyHeight = maxY - minY + 1;
        uploadRegion(pixels, width, minX, minY, dirtyWidth, dirtyHeight, false);
        for (int y = minY; y <= maxY; y++) {
            int offset = y * width + minX;
            System.arraycopy(pixels, offset, uploadedPixels, offset, dirtyWidth);
        }
    }

    private void ensureOverlayTexture() {
        if (textureId != 0) {
            return;
        }
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    private void uploadRegion(
            int[] pixels,
            int sourceWidth,
            int x,
            int y,
            int width,
            int height,
            boolean allocateTexture
    ) {
        int requiredBytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        if (uploadBuffer == null || uploadBuffer.capacity() < requiredBytes) {
            uploadBuffer = createByteBuffer(requiredBytes);
        }

        uploadBuffer.clear();
        for (int row = y; row < y + height; row++) {
            int offset = row * sourceWidth + x;
            for (int column = 0; column < width; column++) {
                int argb = pixels[offset + column];
                uploadBuffer.put((byte) ((argb >>> 16) & 0xFF));
                uploadBuffer.put((byte) ((argb >>> 8) & 0xFF));
                uploadBuffer.put((byte) (argb & 0xFF));
                uploadBuffer.put((byte) ((argb >>> 24) & 0xFF));
            }
        }
        uploadBuffer.flip();

        glBindTexture(GL_TEXTURE_2D, textureId);
        if (allocateTexture) {
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    width,
                    height,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    uploadBuffer
            );
        } else {
            glTexSubImage2D(
                    GL_TEXTURE_2D,
                    0,
                    x,
                    y,
                    width,
                    height,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    uploadBuffer
            );
        }
    }

    private void drawOverlayQuad(int width, int height) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex2f(0.0f, 0.0f);
        glTexCoord2f(1.0f, 0.0f);
        glVertex2f(width, 0.0f);
        glTexCoord2f(1.0f, 1.0f);
        glVertex2f(width, height);
        glTexCoord2f(0.0f, 1.0f);
        glVertex2f(0.0f, height);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_ALPHA_TEST);
    }

    private record OverlayAction(Rectangle bounds, Runnable action) {
    }

    private enum ScrollTarget {
        NONE,
        CUSTOM_MAP
    }
}

package org.main.experimental;

import org.main.battle.BattleEncounter;
import org.main.content.PlayerRegionLibrary;
import org.main.core.*;
import org.main.engine.AssetLoader;
import org.main.engine.AssetRepository;
import org.main.engine.AttributionNotices;
import org.main.engine.TextWrapping;
import org.main.pack.ContentMount;
import org.main.pack.ContentPackManifest;
import org.main.pack.ContentPackRegistry;
import org.main.pack.ContentPackScreenModel;
import org.main.ui.AetherMenuScreens;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;

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
    private final int[] uploadPixelBuffers = new int[2];
    private final int[] worldUploadPixelBuffers = new int[2];
    private final List<OverlayAction> overlayActions = new ArrayList<>();
    private final ContentPackScreenModel contentPackScreenModel =
            new ContentPackScreenModel(AssetRepository.shared().registry());
    private int textureId;
    private FixedFunctionPrimitives fixedPrimitives;
    private int textureWidth;
    private int textureHeight;
    private int uploadPixelBufferCursor;
    private ByteBuffer uploadBuffer;
    private BufferedImage overlayImage;
    private Graphics2D overlayGraphics;
    private long renderedUiRevision = Long.MIN_VALUE;
    private int worldTextureId;
    private int worldTextureWidth;
    private int worldTextureHeight;
    private ByteBuffer worldUploadBuffer;
    private BufferedImage worldOverlayImage;
    private Graphics2D worldOverlayGraphics;
    private int worldUploadPixelBufferCursor;
    private long renderedWorldRevision = Long.MIN_VALUE;
    private List<Rectangle> renderedWorldBounds = List.of();
    private long localUiRevision;
    private int renderedEnemyLabelsHash;
    private List<ItemModelIconRenderQueue.Request> modelIconRequests = List.of();
    private Runnable quitAction = () -> {
    };
    private boolean customMapPickerOpen = false;
    private String customMapMessage = "";
    private boolean creditsOpen = false;
    private boolean contentPackManagerOpen = false;
    private String selectedContentPackId = "";
    private String draggedContentPackId = "";
    private String contentPackMessage = "";
    private int contentPackScroll;
    private List<PackRow> contentPackRows = List.of();
    private String creditsText = "";
    private String creditsMessage = "";
    private int creditsScroll;
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
    private boolean deferRedrawOnce;

    public void setQuitAction(Runnable quitAction) {
        this.quitAction = quitAction == null ? () -> {
        } : quitAction;
    }

    public void setMapChangedAction(Runnable mapChangedAction) {
        this.mapChangedAction = mapChangedAction == null ? () -> {
        } : mapChangedAction;
    }

    public void openCustomMapPicker() {
        localUiRevision++;
        customMapPickerOpen = true;
        creditsOpen = false;
        customMapMessage = "";
    }

    public void closeCustomMapPicker() {
        localUiRevision++;
        customMapPickerOpen = false;
        customMapMessage = "";
    }

    public boolean isCustomMapPickerOpen() {
        return customMapPickerOpen;
    }

    public void openCredits() {
        localUiRevision++;
        customMapPickerOpen = false;
        creditsOpen = true;
        creditsScroll = 0;
        creditsMessage = "";

        try {
            creditsText = AttributionNotices.loadRendered();
        } catch (IOException exception) {
            creditsText = "";
            creditsMessage = "Unable to load credits: " + exception.getMessage();
        }
    }

    public void closeCredits() {
        localUiRevision++;
        creditsOpen = false;
        creditsMessage = "";
        creditsScroll = 0;
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
        closeCredits();
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
        localUiRevision++;
        this.gameOverMessage = gameOverMessage == null ? "" : gameOverMessage;
    }

    public void setStartMenuMessage(String startMenuMessage) {
        localUiRevision++;
        this.customMapMessage = startMenuMessage == null ? "" : startMenuMessage;
    }

    public void setEnemyLabels(List<LwjglDungeonViewport.EnemyLabel> enemyLabels) {
        List<LwjglDungeonViewport.EnemyLabel> value = enemyLabels == null ? List.of() : enemyLabels;
        if (!this.enemyLabels.equals(value)) {
            this.enemyLabels = List.copyOf(value);
        }
    }

    public void setViewportDebugLines(List<String> viewportDebugLines) {
        List<String> value = viewportDebugLines == null ? List.of() : viewportDebugLines;
        if (!this.viewportDebugLines.equals(value)) {
            this.viewportDebugLines = List.copyOf(value);
        }
    }

    public void deferRedrawOnce() {
        deferRedrawOnce = true;
    }

    public void render(AetherGameRuntime runtime, int width, int height) {
        if (runtime == null || width <= 0 || height <= 0) {
            return;
        }

        ensureOverlaySurface(width, height);
        if (fixedPrimitives == null) {
            fixedPrimitives = new FixedFunctionPrimitives();
        }
        GameState gameState = runtime.gameState();
        int labelsHash = enemyLabels.hashCode();
        if (gameState.isDungeonMode()) {
            ensureWorldOverlaySurface(width, height);
            long uiRevision = gameState.hudPresentationSignature() * 31L + localUiRevision;
            long worldRevision = gameState.minimapPresentationSignature() * 31L + labelsHash;
            boolean redrawMain = renderedUiRevision != uiRevision
                    || textureId == 0
                    || textureWidth != width
                    || textureHeight != height;
            if (redrawMain && deferRedrawOnce && textureId != 0
                    && textureWidth == width && textureHeight == height) {
                deferRedrawOnce = false;
            } else if (redrawMain) {
                overlayActions.clear();
                clearOverlaySurface(width, height);
                ItemModelIconRenderQueue.beginCapture();
                try {
                    drawDungeonPersistentOverlay(overlayGraphics, runtime, width, height);
                } finally {
                    modelIconRequests = ItemModelIconRenderQueue.finishCapture();
                }
                uploadOverlaySurface();
                renderedUiRevision = uiRevision;
                deferRedrawOnce = false;
            } else {
                deferRedrawOnce = false;
            }
            if (renderedWorldRevision != worldRevision
                    || worldTextureId == 0
                    || worldTextureWidth != width
                    || worldTextureHeight != height) {
                List<Rectangle> currentWorldBounds = worldOverlayBounds(gameState, width, height);
                List<Rectangle> dirtyWorldBounds = mergeDirtyBounds(renderedWorldBounds, currentWorldBounds);
                clearWorldOverlaySurface(dirtyWorldBounds);
                drawEnemyLabels(worldOverlayGraphics, width);
                miniMapRenderer.draw(worldOverlayGraphics, gameState);
                uploadWorldOverlaySurface(dirtyWorldBounds);
                renderedWorldBounds = currentWorldBounds;
                renderedWorldRevision = worldRevision;
            }
            drawOverlayTexture(worldTextureId, width, height);
            drawOverlayQuad(width, height);
            renderedEnemyLabelsHash = labelsHash;
            return;
        }

        long uiRevision = gameState.uiPresentationRevision() * 31L + localUiRevision;
        boolean redraw = renderedUiRevision != uiRevision
                || renderedEnemyLabelsHash != labelsHash
                || textureId == 0
                || textureWidth != width
                || textureHeight != height;
        if (redraw && deferRedrawOnce && textureId != 0
                && textureWidth == width && textureHeight == height) {
            deferRedrawOnce = false;
            drawOverlayQuad(width, height);
            return;
        }
        deferRedrawOnce = false;
        if (!redraw) {
            drawOverlayQuad(width, height);
            return;
        }
        overlayActions.clear();
        clearOverlaySurface(width, height);
        ItemModelIconRenderQueue.beginCapture();
        try {
            drawGameOverlay(overlayGraphics, runtime, width, height);
        } finally {
            modelIconRequests = ItemModelIconRenderQueue.finishCapture();
        }
        uploadOverlaySurface();
        renderedUiRevision = uiRevision;
        renderedEnemyLabelsHash = labelsHash;
        drawOverlayQuad(width, height);
    }

    public List<ItemModelIconRenderQueue.Request> modelIconRequests() {
        return modelIconRequests;
    }

    public void shutdown() {
        if (overlayGraphics != null) {
            overlayGraphics.dispose();
            overlayGraphics = null;
        }
        overlayImage = null;
        uploadBuffer = null;
        if (worldOverlayGraphics != null) {
            worldOverlayGraphics.dispose();
            worldOverlayGraphics = null;
        }
        worldOverlayImage = null;
        worldUploadBuffer = null;
        renderedWorldBounds = List.of();
        modelIconRequests = List.of();
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        for (int index = 0; index < uploadPixelBuffers.length; index++) {
            if (uploadPixelBuffers[index] != 0) {
                glDeleteBuffers(uploadPixelBuffers[index]);
                uploadPixelBuffers[index] = 0;
            }
        }
        if (worldTextureId != 0) {
            glDeleteTextures(worldTextureId);
            worldTextureId = 0;
        }
        for (int index = 0; index < worldUploadPixelBuffers.length; index++) {
            if (worldUploadPixelBuffers[index] != 0) {
                glDeleteBuffers(worldUploadPixelBuffers[index]);
                worldUploadPixelBuffers[index] = 0;
            }
        }
        if (fixedPrimitives != null) {
            fixedPrimitives.shutdown();
            fixedPrimitives = null;
        }
    }

    public boolean handleMouseClick(int x, int y) {
        return handleMouseClick(x, y, null);
    }

    public boolean handleMouseClick(int x, int y, AetherGameRuntime runtime) {
        return handleMousePressed(x, y, MouseEvent.BUTTON1, runtime);
    }

    public boolean handleMousePressed(int x, int y, int button, AetherGameRuntime runtime) {
        localUiRevision++;
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
                    gameState.evaluateLiveQuestConditions();
                }
                return true;
            }

            if (gameState.hasActiveInteraction()) {
                boolean consumed = ensureInteractionWindow(runtime).handleMousePressed(
                        mouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button),
                        gameState.getActiveInteraction()
                );
                if (consumed) {
                    gameState.evaluateLiveQuestConditions();
                }
                return true;
            }

            if (gameState.hasActiveShop()) {
                shopWindow.handleMousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button), gameState);
                gameState.evaluateLiveQuestConditions();
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

    public boolean closeSkillProgressionGuide() {
        return overworldHud.closeSkillProgressionGuide();
    }

    public boolean handleMouseReleased(int x, int y, int button, AetherGameRuntime runtime) {
        localUiRevision++;
        if (contentPackManagerOpen) {
            draggedContentPackId = "";
            return true;
        }
        if (runtime == null || !runtime.gameState().isDungeonMode()) {
            return false;
        }
        if (runtime.gameState().hasActiveInteraction()) {
            boolean consumed = ensureInteractionWindow(runtime).handleMouseReleased(
                    mouseEvent(MouseEvent.MOUSE_RELEASED, x, y, button),
                    runtime.gameState().getActiveInteraction());
            if (consumed) {
                runtime.gameState().evaluateLiveQuestConditions();
            }
            return consumed;
        }
        if (!runtime.gameState().isInventoryOpen()) {
            return false;
        }

        InventorySystem.InventoryPanel panel = ensureInventoryPanel(runtime);
        boolean consumed = panel != null
                && panel.handleMouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, x, y, button));
        if (consumed) {
            runtime.gameState().evaluateLiveQuestConditions();
        }
        return consumed;
    }

    public boolean handleMouseDragged(int x, int y, AetherGameRuntime runtime) {
        localUiRevision++;
        if (contentPackManagerOpen && !draggedContentPackId.isBlank()) {
            for (PackRow row : contentPackRows) {
                if (row.bounds().contains(x, y) && !row.packId().equals(draggedContentPackId)) {
                    moveContentPack(draggedContentPackId, row.packId());
                    return true;
                }
            }
            return true;
        }
        if (runtime == null || !runtime.gameState().isDungeonMode()) {
            return false;
        }
        if (runtime.gameState().hasActiveInteraction()) {
            return ensureInteractionWindow(runtime).handleMouseDragged(
                    mouseEvent(MouseEvent.MOUSE_DRAGGED, x, y, MouseEvent.BUTTON1),
                    runtime.gameState().getActiveInteraction());
        }
        if (!runtime.gameState().isInventoryOpen()) {
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
        localUiRevision++;
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

        if (yOffset == 0.0) {
            return handleOverworldHudMouseWheel(yOffset, mouseX, mouseY, runtime);
        }

        int delta = yOffset < 0.0 ? 1 : -1;
        if (activeScrollTarget == ScrollTarget.CUSTOM_MAP) {
            customMapScroll = Math.max(0, customMapScroll + delta);
            return true;
        }
        if (activeScrollTarget == ScrollTarget.CREDITS) {
            creditsScroll = Math.max(0, creditsScroll + delta);
            return true;
        }
        if (activeScrollTarget == ScrollTarget.CONTENT_PACKS) {
            contentPackScroll = Math.max(0, contentPackScroll + delta);
            return true;
        }
        return handleOverworldHudMouseWheel(yOffset, mouseX, mouseY, runtime);
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
        localUiRevision++;
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
            if (contentPackManagerOpen) {
                drawContentPackManager(graphics, width, height);
                return;
            }
            if (creditsOpen) {
                drawCredits(graphics, width, height);
                return;
            }
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

    private void drawDungeonPersistentOverlay(
            Graphics2D graphics,
            AetherGameRuntime runtime,
            int width,
            int height
    ) {
        lastOverlayWidth = width;
        lastOverlayHeight = height;
        GameState gameState = runtime.gameState();
        activeScrollTarget = ScrollTarget.NONE;
        InteractionSystem.Interaction interaction = gameState.getActiveInteraction();
        int contentHeight = Math.max(1, height - overworldHud.getBottomReservedHeight());

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
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 3), this::openContentPackManager));
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 4), this::openCredits));
        overlayActions.add(new OverlayAction(AetherMenuScreens.startMenuButtonBounds(width, height, 5), quitAction));
    }

    private void openContentPackManager() {
        contentPackManagerOpen = true;
        creditsOpen = false;
        customMapPickerOpen = false;
        contentPackMessage = "Changes apply on the next game start.";
        ContentPackRegistry.Snapshot snapshot = AssetRepository.shared().registry().snapshot();
        if (selectedContentPackId.isBlank() || !snapshot.available().containsKey(selectedContentPackId)) {
            selectedContentPackId = visibleContentPacks(snapshot).stream()
                    .map(mount -> mount.manifest().id())
                    .findFirst().orElse("");
        }
    }

    private void closeContentPackManager() {
        contentPackManagerOpen = false;
        draggedContentPackId = "";
        contentPackRows = List.of();
    }

    private void drawContentPackManager(Graphics2D graphics, int width, int height) {
        activeScrollTarget = ScrollTarget.CONTENT_PACKS;
        AetherMenuScreens.drawMenuBackdrop(graphics, width, height, "Content Packs");
        int panelWidth = Math.min(900, width - 70);
        int panelHeight = Math.min(570, height - 110);
        int x = (width - panelWidth) / 2;
        int y = 52;
        drawPanel(graphics, x, y, panelWidth, panelHeight, "Enabled packs load from top to bottom");
        drawMenuButton(graphics, x + panelWidth - 82, y + 14, 58, 26, "Back", this::closeContentPackManager);

        ContentPackRegistry.Snapshot snapshot = AssetRepository.shared().registry().snapshot();
        List<ContentMount> packs = visibleContentPacks(snapshot);
        List<String> enabledOrder = enabledContentPackIds(snapshot);
        int rowHeight = 34;
        int rowStartY = y + 70;
        int maxRows = Math.max(1, (panelHeight - 165) / rowHeight);
        contentPackScroll = clampScroll(contentPackScroll, packs.size(), maxRows);
        List<PackRow> rows = new ArrayList<>();
        for (int visibleIndex = 0;
             visibleIndex < maxRows && contentPackScroll + visibleIndex < packs.size();
             visibleIndex++) {
            ContentMount mount = packs.get(contentPackScroll + visibleIndex);
            String id = mount.manifest().id();
            boolean enabled = mount.origin() == ContentMount.Origin.BUNDLED || enabledOrder.contains(id);
            boolean selected = id.equals(selectedContentPackId);
            String kind = mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE
                    ? "source" : mount.origin().name().toLowerCase();
            String label = (selected ? "> " : "  ") + (enabled ? "[x] " : "[ ] ")
                    + mount.manifest().title() + "  " + mount.manifest().version()
                    + "  (" + id + ", " + kind + ")";
            Rectangle bounds = new Rectangle(x + 24, rowStartY + visibleIndex * rowHeight,
                    panelWidth - 48, 28);
            drawMenuButton(graphics, bounds.x, bounds.y, bounds.width, bounds.height, label, () -> {
                selectedContentPackId = id;
                draggedContentPackId = enabled ? id : "";
            });
            rows.add(new PackRow(id, bounds));
        }
        contentPackRows = List.copyOf(rows);
        drawScrollHint(graphics, x + 24, y + panelHeight - 86, packs.size(), maxRows, contentPackScroll);

        ContentMount selected = snapshot.available().get(selectedContentPackId);
        int buttonY = y + panelHeight - 70;
        if (selected != null) {
            boolean enabled = enabledOrder.contains(selectedContentPackId);
            String toggleLabel = selected.origin() == ContentMount.Origin.BUNDLED
                    ? "Core (Fixed)"
                    : selected.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE
                    ? "Source Only" : enabled ? "Disable" : "Enable";
            drawMenuButton(graphics, x + 24, buttonY, 112, 28, toggleLabel,
                    () -> toggleContentPack(selectedContentPackId));
            drawMenuButton(graphics, x + 146, buttonY, 90, 28, "Move Up",
                    () -> moveContentPackBy(selectedContentPackId, -1));
            drawMenuButton(graphics, x + 246, buttonY, 100, 28, "Move Down",
                    () -> moveContentPackBy(selectedContentPackId, 1));
        }

        graphics.setFont(smallFont);
        graphics.setColor(snapshot.diagnostics().isEmpty() ? MUTED : DANGER);
        String diagnostic = snapshot.diagnostics().isEmpty()
                ? contentPackMessage
                : String.join(" | ", snapshot.diagnostics());
        graphics.drawString(fitLine(graphics, diagnostic, panelWidth - 390),
                x + 370, buttonY + 20);
    }

    private List<ContentMount> visibleContentPacks(ContentPackRegistry.Snapshot snapshot) {
        return contentPackScreenModel.rows().stream()
                .map(row -> snapshot.available().get(row.id()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<String> enabledContentPackIds(ContentPackRegistry.Snapshot snapshot) {
        return snapshot.activeHighestPriorityFirst().stream()
                .filter(mount -> mount.origin() == ContentMount.Origin.INSTALLED
                        || mount.origin() == ContentMount.Origin.WORKSHOP)
                .map(mount -> mount.manifest().id())
                .toList();
    }

    private void toggleContentPack(String packId) {
        ContentPackRegistry registry = AssetRepository.shared().registry();
        ContentMount mount = registry.snapshot().available().get(packId);
        if (mount == null || mount.origin() == ContentMount.Origin.BUNDLED) {
            contentPackMessage = "Aether Core is immutable and fixed at the bottom of the load order.";
            return;
        }
        if (mount.manifest().type() == ContentPackManifest.PackType.AUTHORING_SOURCE) {
            contentPackMessage = "Authoring-source packs are visible only in the Construction Kit.";
            return;
        }
        boolean enabled = enabledContentPackIds(registry.snapshot()).contains(packId);
        try {
            contentPackScreenModel.setEnabled(packId, !enabled);
            AssetRepository.shared().clearDecodedImages();
            contentPackMessage = (enabled ? "Disabled " : "Enabled ") + packId
                    + ". Restart to apply gameplay changes.";
        } catch (IOException exception) {
            contentPackMessage = exception.getMessage();
        }
    }

    private void moveContentPackBy(String packId, int delta) {
        try {
            contentPackScreenModel.move(packId, delta);
            AssetRepository.shared().clearDecodedImages();
            contentPackMessage = "Load order saved. Restart to apply gameplay changes.";
        } catch (IOException exception) {
            contentPackMessage = exception.getMessage();
        }
    }

    private void moveContentPack(String packId, String targetPackId) {
        try {
            contentPackScreenModel.moveBefore(packId, targetPackId);
            AssetRepository.shared().clearDecodedImages();
            contentPackMessage = "Load order saved. Restart to apply gameplay changes.";
        } catch (IOException exception) {
            contentPackMessage = exception.getMessage();
        }
    }

    private void drawCredits(Graphics2D graphics, int width, int height) {
        activeScrollTarget = ScrollTarget.CREDITS;
        AetherMenuScreens.drawMenuBackdrop(graphics, width, height, "Credits");

        int horizontalMargin = Math.max(24, Math.min(64, width / 12));
        int panelWidth = Math.min(900, Math.max(240, width - horizontalMargin * 2));
        int panelHeight = Math.min(520, Math.max(220, height - 190));
        int x = (width - panelWidth) / 2;
        int y = Math.max(112, (height - panelHeight) / 2 + 44);
        if (y + panelHeight > height - 24) {
            y = Math.max(24, height - panelHeight - 24);
        }

        drawPanel(graphics, x, y, panelWidth, panelHeight, "Attributions");
        drawMenuButton(graphics, x + panelWidth - 82, y + 14, 58, 26, "Back", this::closeCredits);

        graphics.setFont(bodyFont);
        int textX = x + 24;
        int textY = y + 64;
        int textWidth = panelWidth - 48;
        int lineHeight = Math.max(18, graphics.getFontMetrics().getHeight() + 4);
        int footerHeight = 38;
        int visibleLines = Math.max(1, (panelHeight - 64 - footerHeight) / lineHeight);
        String displayedCredits = creditsText == null || creditsText.isBlank()
                ? "No attributions have been added yet."
                : creditsText;
        List<String> lines = TextWrapping.wrap(graphics.getFontMetrics(), displayedCredits, textWidth);
        creditsScroll = clampScroll(creditsScroll, lines.size(), visibleLines);

        java.awt.Shape previousClip = graphics.getClip();
        graphics.clipRect(textX, textY - graphics.getFontMetrics().getAscent(), textWidth, visibleLines * lineHeight);
        graphics.setColor(TEXT);
        for (int visibleIndex = 0;
             visibleIndex < visibleLines && creditsScroll + visibleIndex < lines.size();
             visibleIndex++) {
            graphics.drawString(lines.get(creditsScroll + visibleIndex), textX, textY + visibleIndex * lineHeight);
        }
        graphics.setClip(previousClip);

        drawScrollHint(
                graphics,
                textX,
                y + panelHeight - 18,
                lines.size(),
                visibleLines,
                creditsScroll
        );

        if (creditsMessage != null && !creditsMessage.isBlank()) {
            graphics.setFont(smallFont);
            graphics.setColor(DANGER);
            graphics.drawString(fitLine(graphics, creditsMessage, textWidth), textX, y + panelHeight - 18);
        }
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
        renderedUiRevision = Long.MIN_VALUE;
    }

    private void ensureWorldOverlaySurface(int width, int height) {
        if (worldOverlayImage != null
                && worldOverlayImage.getWidth() == width
                && worldOverlayImage.getHeight() == height) {
            return;
        }
        if (worldOverlayGraphics != null) {
            worldOverlayGraphics.dispose();
        }
        worldOverlayImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        worldOverlayGraphics = worldOverlayImage.createGraphics();
        worldOverlayGraphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        worldOverlayGraphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        renderedWorldBounds = List.of();
        renderedWorldRevision = Long.MIN_VALUE;
    }

    private void clearOverlaySurface(int width, int height) {
        overlayGraphics.setComposite(AlphaComposite.Clear);
        overlayGraphics.fillRect(0, 0, width, height);
        overlayGraphics.setComposite(AlphaComposite.SrcOver);
    }

    private void clearWorldOverlaySurface(List<Rectangle> dirtyBounds) {
        if (dirtyBounds.isEmpty()) {
            return;
        }
        worldOverlayGraphics.setComposite(AlphaComposite.Clear);
        for (Rectangle bounds : dirtyBounds) {
            worldOverlayGraphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        worldOverlayGraphics.setComposite(AlphaComposite.SrcOver);
    }

    private void uploadOverlaySurface() {
        int width = overlayImage.getWidth();
        int height = overlayImage.getHeight();
        int[] pixels = ((DataBufferInt) overlayImage.getRaster().getDataBuffer()).getData();
        boolean textureNeedsAllocation = textureId == 0
                || textureWidth != width
                || textureHeight != height;

        ensureOverlayTexture();
        int requiredBytes = Math.multiplyExact(Math.multiplyExact(width, height), Integer.BYTES);
        if (uploadBuffer == null || uploadBuffer.capacity() < requiredBytes) {
            uploadBuffer = createByteBuffer(requiredBytes);
        }
        uploadBuffer.clear();
        IntBuffer integers = uploadBuffer.asIntBuffer();
        integers.put(pixels);
        uploadBuffer.limit(requiredBytes);
        glBindTexture(GL_TEXTURE_2D, textureId);
        if (textureNeedsAllocation) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, uploadBuffer);
        } else {
            int pixelBuffer = nextUploadPixelBuffer();
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pixelBuffer);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, requiredBytes, GL_STREAM_DRAW);
            glBufferSubData(GL_PIXEL_UNPACK_BUFFER, 0L, uploadBuffer);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        }
        textureWidth = width;
        textureHeight = height;
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

    private void uploadWorldOverlaySurface(List<Rectangle> dirtyBounds) {
        int width = worldOverlayImage.getWidth();
        int height = worldOverlayImage.getHeight();
        boolean textureNeedsAllocation = worldTextureId == 0
                || worldTextureWidth != width
                || worldTextureHeight != height;
        ensureWorldOverlayTexture();
        glBindTexture(GL_TEXTURE_2D, worldTextureId);
        if (textureNeedsAllocation) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
            worldTextureWidth = width;
            worldTextureHeight = height;
        }
        int[] pixels = ((DataBufferInt) worldOverlayImage.getRaster().getDataBuffer()).getData();
        for (Rectangle bounds : dirtyBounds) {
            uploadWorldOverlayRegion(pixels, width, bounds);
        }
    }

    private void uploadWorldOverlayRegion(int[] pixels, int sourceWidth, Rectangle bounds) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int requiredBytes = Math.multiplyExact(Math.multiplyExact(bounds.width, bounds.height), Integer.BYTES);
        if (worldUploadBuffer == null || worldUploadBuffer.capacity() < requiredBytes) {
            worldUploadBuffer = createByteBuffer(requiredBytes);
        }
        worldUploadBuffer.clear();
        IntBuffer integers = worldUploadBuffer.asIntBuffer();
        for (int row = bounds.y; row < bounds.y + bounds.height; row++) {
            integers.put(pixels, row * sourceWidth + bounds.x, bounds.width);
        }
        worldUploadBuffer.limit(requiredBytes);
        int pixelBuffer = nextWorldUploadPixelBuffer();
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pixelBuffer);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, requiredBytes, GL_STREAM_DRAW);
        glBufferSubData(GL_PIXEL_UNPACK_BUFFER, 0L, worldUploadBuffer);
        glBindTexture(GL_TEXTURE_2D, worldTextureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, bounds.x, bounds.y, bounds.width, bounds.height,
                GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0L);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    private void ensureWorldOverlayTexture() {
        if (worldTextureId != 0) {
            return;
        }
        worldTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, worldTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    private List<Rectangle> worldOverlayBounds(GameState gameState, int width, int height) {
        List<Rectangle> bounds = new ArrayList<>(enemyLabels.size() + 1);
        if (gameState.isMiniMapVisible()
                && (gameState.isMiniMapUnlocked() || gameState.isMiniMapDebugMode())) {
            bounds.add(clampBounds(new Rectangle(16, 16, 206, 206), width, height));
        }
        Font previousFont = worldOverlayGraphics.getFont();
        worldOverlayGraphics.setFont(previousFont.deriveFont(Font.BOLD, 11f));
        FontMetrics metrics = worldOverlayGraphics.getFontMetrics();
        for (LwjglDungeonViewport.EnemyLabel label : enemyLabels) {
            if (label == null || label.text() == null || label.text().isBlank()) {
                continue;
            }
            int labelWidth = metrics.stringWidth(label.text()) + 10;
            int labelHeight = metrics.getHeight() + 4;
            int x = Math.max(4, Math.min(width - labelWidth - 4, label.x() - labelWidth / 2));
            int y = Math.max(4, label.y() - labelHeight - 3);
            bounds.add(clampBounds(new Rectangle(x - 2, y - 2, labelWidth + 4, labelHeight + 4), width, height));
        }
        worldOverlayGraphics.setFont(previousFont);
        bounds.removeIf(Rectangle::isEmpty);
        return List.copyOf(bounds);
    }

    private List<Rectangle> mergeDirtyBounds(List<Rectangle> previous, List<Rectangle> current) {
        List<Rectangle> merged = new ArrayList<>(previous.size() + current.size());
        for (Rectangle bounds : previous) {
            mergeDirtyBound(merged, bounds);
        }
        for (Rectangle bounds : current) {
            mergeDirtyBound(merged, bounds);
        }
        return merged;
    }

    private void mergeDirtyBound(List<Rectangle> merged, Rectangle candidate) {
        Rectangle combined = new Rectangle(candidate);
        for (int index = 0; index < merged.size(); ) {
            Rectangle existing = merged.get(index);
            Rectangle padded = new Rectangle(existing.x - 2, existing.y - 2,
                    existing.width + 4, existing.height + 4);
            if (!padded.intersects(combined)) {
                index++;
                continue;
            }
            combined = combined.union(existing);
            merged.remove(index);
            index = 0;
        }
        merged.add(combined);
    }

    private Rectangle clampBounds(Rectangle bounds, int width, int height) {
        int x = Math.max(0, bounds.x);
        int y = Math.max(0, bounds.y);
        int right = Math.min(width, bounds.x + bounds.width);
        int bottom = Math.min(height, bounds.y + bounds.height);
        return new Rectangle(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private void drawOverlayQuad(int width, int height) {
        drawOverlayTexture(textureId, width, height);
    }

    private void drawOverlayTexture(int targetTextureId, int width, int height) {
        if (targetTextureId == 0) {
            return;
        }
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, targetTextureId);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        fixedPrimitives.drawScreenQuad(width, height);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_ALPHA_TEST);
    }

    private int nextUploadPixelBuffer() {
        int index = uploadPixelBufferCursor++ & 1;
        if (uploadPixelBuffers[index] == 0) {
            uploadPixelBuffers[index] = glGenBuffers();
        }
        return uploadPixelBuffers[index];
    }

    private int nextWorldUploadPixelBuffer() {
        int index = worldUploadPixelBufferCursor++ & 1;
        if (worldUploadPixelBuffers[index] == 0) {
            worldUploadPixelBuffers[index] = glGenBuffers();
        }
        return worldUploadPixelBuffers[index];
    }

    private enum ScrollTarget {
        NONE,
        CUSTOM_MAP,
        CREDITS,
        CONTENT_PACKS
    }

    private record OverlayAction(Rectangle bounds, Runnable action) {
    }

    private record PackRow(String packId, Rectangle bounds) {
    }
}

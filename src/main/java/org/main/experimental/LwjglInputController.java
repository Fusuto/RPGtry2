package org.main.experimental;

import org.main.core.AetherGameRuntime;
import org.main.core.GameConfiguration;
import org.main.core.GameState;
import org.main.core.InputBindings;
import org.main.core.InteractionSystem;
import org.main.content.MapDesignLibrary;
import org.main.core.SaveSystem;
import org.main.core.ShopSystem;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public final class LwjglInputController {
    private static final int DEFAULT_ACTION_COOLDOWN_MS = 150;
    private static final double FORWARD_LOOK_MAX_YAW_DEGREES = 90.0;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> consumedKeys = new HashSet<>();
    private final int actionCooldownMs;
    private final boolean mouseLookEnabled;
    private final boolean invertMouseLookX;
    private final boolean invertMouseLookY;
    private final double sensitivity;
    private final double maxYawDegrees;
    private final double maxPitchDegrees;

    private long window;
    private int actionCooldownRemainingMs;
    private boolean leftMouseHeld;
    private boolean rightMouseHeld;
    private boolean battleMouseClickArmed;
    private boolean skipNextMouseDelta;
    private double previousMouseX;
    private double previousMouseY;
    private double yawOffsetDegrees;
    private double pitchOffsetDegrees;
    private GameState.GameMode lastGameMode;
    private Runnable mapChangedAction = () -> {
    };
    private LwjglTextOverlayRenderer overlayRenderer;
    private InteractionSystem.InteractionWindow interactionWindow;
    private final ShopSystem.ShopWindow shopWindow = new ShopSystem.ShopWindow();

    public LwjglInputController() {
        this.actionCooldownMs = Math.max(1, GameConfiguration.intValue(
                "renderer.prototype.input.actionCooldownMs",
                DEFAULT_ACTION_COOLDOWN_MS
        ));
        this.mouseLookEnabled = Boolean.parseBoolean(GameConfiguration.stringValue(
                "renderer.prototype.mouseLook.enabled",
                "true"
        ));
        this.invertMouseLookX = Boolean.parseBoolean(GameConfiguration.stringValue(
                "renderer.prototype.mouseLook.invertX",
                "true"
        ));
        this.invertMouseLookY = Boolean.parseBoolean(GameConfiguration.stringValue(
                "renderer.prototype.mouseLook.invertY",
                "false"
        ));
        this.sensitivity = Math.max(0.001, GameConfiguration.doubleValue(
                "renderer.prototype.mouseLook.sensitivity",
                0.12
        ));
        double configuredMaxYaw = Math.max(0.0, GameConfiguration.doubleValue(
                "renderer.prototype.mouseLook.maxYawDegrees",
                FORWARD_LOOK_MAX_YAW_DEGREES
        ));
        this.maxYawDegrees = Math.min(FORWARD_LOOK_MAX_YAW_DEGREES, configuredMaxYaw);
        this.maxPitchDegrees = Math.max(0.0, GameConfiguration.doubleValue(
                "renderer.prototype.mouseLook.maxPitchDegrees",
                35.0
        ));
    }

    public void install(
            long window,
            LwjglDungeonViewport viewport,
            LwjglTextOverlayRenderer overlayRenderer,
            AetherGameRuntime runtime
    ) {
        this.window = window;
        this.overlayRenderer = overlayRenderer;
        this.interactionWindow = new InteractionSystem.InteractionWindow(runtime.soundSystem());
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                pressedKeys.add(key);
                consumedKeys.remove(key);
                handleImmediateKey(handle, key, viewport, runtime);
            } else if (action == GLFW_RELEASE) {
                pressedKeys.remove(key);
                consumedKeys.remove(key);
            }
        });

        glfwSetCharCallback(window, (handle, codepoint) -> {
            if (overlayRenderer != null) {
                overlayRenderer.appendCharacterNameCodePoint((int) codepoint);
            }
        });

        glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            double[] x = new double[1];
            double[] y = new double[1];
            glfwGetCursorPos(handle, x, y);
            Point framebufferPoint = viewport.framebufferPoint(x[0], y[0]);
            int awtButton = glfwToAwtMouseButton(button);

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMouseHeld = action == GLFW_PRESS;
            }

            if (runtime != null && runtime.gameState().isBattleMode()) {
                handleBattleMouseButton(action, framebufferPoint, runtime);
                return;
            }

            if (overlayRenderer != null && (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT)) {
                if (action == GLFW_PRESS
                        && overlayRenderer.handleMousePressed(framebufferPoint.x, framebufferPoint.y, awtButton, runtime)) {
                    return;
                }

                if (action == GLFW_RELEASE
                        && overlayRenderer.handleMouseReleased(framebufferPoint.x, framebufferPoint.y, awtButton, runtime)) {
                    return;
                }
            }

            if (!mouseLookEnabled || button != GLFW_MOUSE_BUTTON_RIGHT) {
                return;
            }

            if (action == GLFW_PRESS) {
                if (!canUseMouseLook(runtime)) {
                    return;
                }
                rightMouseHeld = true;
                skipNextMouseDelta = true;
                double[] cursorX = new double[1];
                double[] cursorY = new double[1];
                glfwGetCursorPos(handle, cursorX, cursorY);
                previousMouseX = cursorX[0];
                previousMouseY = cursorY[0];
                glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            } else if (action == GLFW_RELEASE) {
                releaseMouseLook();
            }
        });

        glfwSetCursorPosCallback(window, (handle, x, y) -> {
            Point framebufferPoint = viewport.framebufferPoint(x, y);
            if (leftMouseHeld && overlayRenderer != null
                    && overlayRenderer.handleMouseDragged(framebufferPoint.x, framebufferPoint.y, runtime)) {
                return;
            }

            if (rightMouseHeld) {
                updateMouseLook(x, y);
                return;
            }

            if (runtime != null && runtime.gameState().isBattleMode()) {
                runtime.battleController().handleMouseMoved(framebufferPoint);
            } else if (overlayRenderer != null) {
                overlayRenderer.handleMouseMoved(framebufferPoint.x, framebufferPoint.y, runtime);
            }

            previousMouseX = x;
            previousMouseY = y;
        });


        glfwSetScrollCallback(window, (handle, xOffset, yOffset) -> {
            if (overlayRenderer != null) {
                double[] x = new double[1];
                double[] y = new double[1];
                glfwGetCursorPos(handle, x, y);
                Point framebufferPoint = viewport.framebufferPoint(x[0], y[0]);
                overlayRenderer.handleMouseWheel(yOffset, framebufferPoint.x, framebufferPoint.y, runtime);
            }
        });

        glfwSetWindowFocusCallback(window, (handle, focused) -> {
            if (focused) {
                return;
            }

            pressedKeys.clear();
            consumedKeys.clear();
            leftMouseHeld = false;
            battleMouseClickArmed = false;
            actionCooldownRemainingMs = 0;
            releaseMouseLook();
        });
    }

    private void handleBattleMouseButton(int action, Point framebufferPoint, AetherGameRuntime runtime) {
        if (action == GLFW_PRESS) {
            battleMouseClickArmed = true;
            return;
        }

        if (action != GLFW_RELEASE || !battleMouseClickArmed) {
            return;
        }

        battleMouseClickArmed = false;
        runtime.battleController().handleMouseClick(framebufferPoint);
    }

    private void updateMouseLook(double x, double y) {
        if (skipNextMouseDelta) {
            previousMouseX = x;
            previousMouseY = y;
            skipNextMouseDelta = false;
            return;
        }

        double deltaX = x - previousMouseX;
        double deltaY = y - previousMouseY;
        previousMouseX = x;
        previousMouseY = y;
        double yawDelta = deltaX * sensitivity * (invertMouseLookX ? -1.0 : 1.0);
        double pitchDelta = deltaY * sensitivity * (invertMouseLookY ? -1.0 : 1.0);
        yawOffsetDegrees = clamp(yawOffsetDegrees + yawDelta, -maxYawDegrees, maxYawDegrees);
        pitchOffsetDegrees = clamp(pitchOffsetDegrees + pitchDelta, -maxPitchDegrees, maxPitchDegrees);
    }

    public void setMapChangedAction(Runnable mapChangedAction) {
        this.mapChangedAction = mapChangedAction == null ? () -> {
        } : mapChangedAction;
    }

    public void update(int deltaMs, AetherGameRuntime runtime, LwjglDungeonViewport viewport) {
        actionCooldownRemainingMs = Math.max(0, actionCooldownRemainingMs - Math.max(0, deltaMs));
        GameState gameState = runtime.gameState();
        handleGameModeTransition(gameState);
        if (!canUseMouseLook(runtime)) {
            releaseMouseLook();
        }

        if (handleStartOrGameOverInput(runtime, gameState)) {
            return;
        }

        if (gameState.isBattleMode()) {
            handleBattleInput(runtime);
            return;
        }

        if (consume(GLFW_KEY_ESCAPE)
                || (gameState.isDungeonMode() && consumeBound(gameState, InputBindings.Action.ESCAPE_MENU))) {
            handleEscape(runtime, gameState);
            return;
        }

        if (handleCharacterCreationInput(runtime, gameState)) {
            return;
        }

        if (gameState.isDungeonMode()
                && gameState.hasActiveInteraction()
                && gameState.isCharacterMenuOverlayAllowed()) {
            if (consumeBound(gameState, InputBindings.Action.INVENTORY)) {
                gameState.toggleInventory();
                return;
            }
            if (consumeBound(gameState, InputBindings.Action.SKILLS)) {
                gameState.toggleSkills();
                return;
            }
        }

        if (gameState.isDungeonMode() && handleInteractionInput(gameState, runtime)) {
            return;
        }

        if (gameState.isDungeonMode() && handleShopInput(gameState)) {
            return;
        }

        if (gameState.isDungeonMode() && consumeBound(gameState, InputBindings.Action.INVENTORY)) {
            gameState.toggleInventory();
            return;
        }
        if (gameState.isDungeonMode() && consumeBound(gameState, InputBindings.Action.SKILLS)) {
            gameState.toggleSkills();
            return;
        }

        if (rightMouseHeld) {
            return;
        }

        if (gameState.isCameraAnimating()) {
            return;
        }

        if (!gameState.isDungeonMode() || hasBlockingOverlay(gameState)) {
            return;
        }

        if (actionCooldownRemainingMs > 0) {
            return;
        }

        Integer dungeonKeyCode = pressedDungeonActionKey(gameState);
        if (dungeonKeyCode != null) {
            runtime.dungeonController().handleInput(keyEvent(dungeonKeyCode));
            markAction();
        }
    }

    public CameraLookState cameraLook() {
        return new CameraLookState(yawOffsetDegrees, pitchOffsetDegrees, rightMouseHeld);
    }

    private void handleImmediateKey(
            long window,
            int key,
            LwjglDungeonViewport viewport,
        AetherGameRuntime runtime
    ) {
        if (key == GLFW_KEY_F3) {
            boolean debugVisible = viewport.toggleDebug();
            if (runtime != null && runtime.gameState() != null) {
                runtime.gameState().setPerformanceOverlayVisible(debugVisible);
            }
            consumedKeys.add(key);
        } else if (key == GLFW_KEY_LEFT_BRACKET) {
            viewport.decreaseDepth();
            consumedKeys.add(key);
        } else if (key == GLFW_KEY_RIGHT_BRACKET) {
            viewport.increaseDepth();
            consumedKeys.add(key);
        }
    }

    private boolean handleStartOrGameOverInput(AetherGameRuntime runtime, GameState gameState) {
        if (!gameState.isStartMenuMode() && !gameState.isGameOverMode()) {
            return false;
        }

        if (gameState.isGameOverMode()) {
            if (consume(GLFW_KEY_ENTER)) {
                try {
                    runtime.loadGame();
                    if (overlayRenderer != null) {
                        overlayRenderer.setGameOverMessage("");
                    }
                    mapChangedAction.run();
                } catch (java.io.IOException exception) {
                    if (overlayRenderer != null) {
                        overlayRenderer.setGameOverMessage(exception.getMessage());
                    }
                }
                return true;
            }

            if (consume(GLFW_KEY_ESCAPE)) {
                if (overlayRenderer != null) {
                    overlayRenderer.setGameOverMessage("");
                }
                runtime.returnToMainMenu();
                return true;
            }

            return false;
        }

        if (overlayRenderer != null && overlayRenderer.isCustomMapPickerOpen()) {
            for (int key = GLFW_KEY_1; key <= GLFW_KEY_9; key++) {
                if (consume(key)) {
                    overlayRenderer.selectCustomMap(runtime, key - GLFW_KEY_1);
                    return true;
                }
            }
            if (consume(GLFW_KEY_B) || consume(GLFW_KEY_BACKSPACE)) {
                overlayRenderer.closeCustomMapPicker();
                return true;
            }
            return false;
        }

        if (consume(GLFW_KEY_ENTER)) {
            if (overlayRenderer != null) {
                overlayRenderer.beginCharacterCreation(gameState);
            }
            return true;
        }

        if (consume(GLFW_KEY_ESCAPE)) {
            glfwSetWindowShouldClose(window, true);
            return true;
        }

        return false;
    }

    private boolean handleCharacterCreationInput(AetherGameRuntime runtime, GameState gameState) {
        if (!gameState.isCharacterCreationMode()) {
            return false;
        }

        if (consume(GLFW_KEY_BACKSPACE)) {
            if (overlayRenderer != null) {
                overlayRenderer.backspaceCharacterName();
            }
            return true;
        }

        if (consume(GLFW_KEY_ENTER)) {
            if (overlayRenderer != null) {
                overlayRenderer.confirmCharacterCreation(runtime);
            }
            return true;
        }

        if (consume(GLFW_KEY_ESCAPE)) {
            if (overlayRenderer != null) {
                overlayRenderer.cancelCharacterCreation(gameState);
            } else {
                gameState.setGameMode(GameState.GameMode.START_MENU);
            }
            return true;
        }

        return false;
    }

    private boolean handleInteractionInput(GameState gameState, AetherGameRuntime runtime) {
        InteractionSystem.Interaction interaction = gameState.getActiveInteraction();
        if (interaction == null) {
            return false;
        }

        if (interactionWindow == null) {
            interactionWindow = new InteractionSystem.InteractionWindow(runtime.soundSystem());
        }

        for (int key : new HashSet<>(pressedKeys)) {
            if (consumedKeys.contains(key)) {
                continue;
            }

            int awtKey = glfwToAwtKey(key);
            if (awtKey == KeyEvent.VK_UNDEFINED) {
                continue;
            }

            if (interactionWindow.handleKeyPressed(keyEvent(awtKey), interaction)) {
                consumedKeys.add(key);
                return true;
            }
        }

        return false;
    }

    private boolean handleShopInput(GameState gameState) {
        if (!gameState.hasActiveShop()) {
            return false;
        }

        for (int key : new HashSet<>(pressedKeys)) {
            if (consumedKeys.contains(key)) {
                continue;
            }

            int awtKey = glfwToAwtKey(key);
            if (awtKey == KeyEvent.VK_UNDEFINED) {
                continue;
            }

            if (shopWindow.handleKeyPressed(keyEvent(awtKey), gameState)) {
                consumedKeys.add(key);
                return true;
            }
        }

        return false;
    }

    private void handleBattleInput(AetherGameRuntime runtime) {
        for (int key : new HashSet<>(pressedKeys)) {
            if (consumedKeys.contains(key)) {
                continue;
            }

            int awtKey = glfwToAwtKey(key);
            if (awtKey == KeyEvent.VK_UNDEFINED) {
                continue;
            }

            runtime.battleController().handleInput(keyEvent(awtKey));
            consumedKeys.add(key);
            return;
        }
    }

    private java.awt.event.KeyEvent keyEvent(int keyCode) {
        return new java.awt.event.KeyEvent(
                new java.awt.Canvas(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                java.awt.event.KeyEvent.CHAR_UNDEFINED
        );
    }

    private int glfwToAwtMouseButton(int button) {
        return switch (button) {
            case GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            case GLFW_MOUSE_BUTTON_MIDDLE -> MouseEvent.BUTTON2;
            default -> MouseEvent.BUTTON1;
        };
    }

    private void handleEscape(AetherGameRuntime runtime, GameState gameState) {
        if (overlayRenderer != null && overlayRenderer.isCustomMapPickerOpen()) {
            overlayRenderer.closeCustomMapPicker();
            return;
        }

        if (gameState.isCharacterCreationMode()) {
            if (overlayRenderer != null) {
                overlayRenderer.cancelCharacterCreation(gameState);
            } else {
                gameState.setGameMode(GameState.GameMode.START_MENU);
            }
            return;
        }

        InteractionSystem.Interaction interaction = gameState.getActiveInteraction();
        if (interaction != null) {
            if (interaction.isCharacterMenuOverlayAllowed()) {
                if (gameState.isInventoryOpen()) {
                    gameState.closeInventory();
                    return;
                }
                if (gameState.isSkillsOpen()) {
                    gameState.closeSkills();
                    return;
                }
                if (gameState.isQuestsOpen()) {
                    gameState.closeQuests();
                    return;
                }
                if (gameState.isStatsOpen()) {
                    gameState.closeStats();
                    return;
                }

                openConfigMenu(runtime, gameState);
                return;
            }

            if (interaction.getModel().isEscapeCloses()) {
                gameState.closeInteraction();
            }
            return;
        }

        if (gameState.getActiveShop() != null) {
            gameState.closeShop();
            return;
        }

        if (gameState.isInventoryOpen()) {
            gameState.closeInventory();
            return;
        }

        if (gameState.isSkillsOpen()) {
            gameState.closeSkills();
            return;
        }

        if (gameState.isQuestsOpen()) {
            gameState.closeQuests();
            return;
        }

        if (gameState.isStatsOpen()) {
            gameState.closeStats();
            return;
        }

        if (gameState.isBattleMode()) {
            runtime.battleController().cancelBattleSelection();
            return;
        }

        if (gameState.isDungeonMode()) {
            openConfigMenu(runtime, gameState);
            return;
        }

        glfwSetWindowShouldClose(window, true);
    }

    private void openConfigMenu(AetherGameRuntime runtime, GameState gameState) {
        gameState.openInteraction(InteractionSystem.configMenu(
                runtime.soundSystem(),
                gameState,
                () -> glfwSetWindowShouldClose(window, true),
                () -> gameState.openInteraction(InteractionSystem.controlsMenu(gameState.getInputBindings())),
                () -> saveGameFromMenu(runtime, gameState),
                () -> loadGameFromMenu(runtime, gameState)
        ));
    }

    private void saveGameFromMenu(AetherGameRuntime runtime, GameState gameState) {
        try {
            runtime.saveGame();
            gameState.openInteraction(InteractionSystem.prompt(
                    "Saved",
                    "Game saved to " + SaveSystem.getSavePath() + ".",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (java.io.IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Save Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private void loadGameFromMenu(AetherGameRuntime runtime, GameState gameState) {
        List<InteractionSystem.InteractionOption> options = new ArrayList<>();
        options.add(InteractionSystem.option("Saved Game", () -> loadSavedGameFromMenu(runtime, gameState)));

        try {
            List<Path> mapPaths = runtime.listAvailableMaps();
            if (mapPaths.isEmpty()) {
                options.add(InteractionSystem.closeOption("No authored maps found"));
            }

            for (Path mapPath : mapPaths) {
                options.add(InteractionSystem.option(
                        "Map: " + runtime.describeMap(mapPath),
                        () -> loadAuthoredMapFromMenu(runtime, gameState, mapPath)
                ));
            }
        } catch (java.io.IOException exception) {
            options.add(InteractionSystem.closeOption("No authored maps found"));
        }

        options.add(InteractionSystem.closeOption("Cancel"));
        gameState.openInteraction(InteractionSystem.prompt(
                "Load",
                "Choose what to load.",
                options.toArray(new InteractionSystem.InteractionOption[0])
        ));
    }

    private void loadSavedGameFromMenu(AetherGameRuntime runtime, GameState gameState) {
        try {
            runtime.loadGame();
            mapChangedAction.run();
            gameState.openInteraction(InteractionSystem.prompt(
                    "Loaded",
                    "Saved game loaded.",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (java.io.IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Load Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private void loadAuthoredMapFromMenu(AetherGameRuntime runtime, GameState gameState, Path mapPath) {
        try {
            MapDesignLibrary.MapDesign mapDesign = runtime.loadAuthoredMap(mapPath);
            mapChangedAction.run();
            gameState.openInteraction(InteractionSystem.prompt(
                    "Loaded Map",
                    "Loaded " + mapDesign.displayName() + ".",
                    InteractionSystem.closeOption("Close")
            ));
        } catch (java.io.IOException exception) {
            gameState.openInteraction(InteractionSystem.prompt(
                    "Map Load Failed",
                    exception.getMessage(),
                    InteractionSystem.closeOption("Close")
            ));
        }
    }

    private boolean hasBlockingOverlay(GameState gameState) {
        return gameState.hasActiveInteraction()
                || gameState.hasActiveShop()
                || gameState.isInventoryOpen()
                || gameState.isSkillsOpen()
                || gameState.isQuestsOpen()
                || gameState.isStatsOpen();
    }

    private void handleGameModeTransition(GameState gameState) {
        if (gameState == null) {
            lastGameMode = null;
            return;
        }

        GameState.GameMode currentMode = gameState.getGameMode();
        if (lastGameMode == null) {
            lastGameMode = currentMode;
            return;
        }

        if (lastGameMode == currentMode) {
            return;
        }

        lastGameMode = currentMode;
        consumedKeys.addAll(pressedKeys);
        actionCooldownRemainingMs = actionCooldownMs;
        battleMouseClickArmed = false;
        releaseMouseLook();
    }

    private boolean canUseMouseLook(AetherGameRuntime runtime) {
        if (!mouseLookEnabled || runtime == null || runtime.gameState() == null) {
            return false;
        }

        GameState gameState = runtime.gameState();
        return gameState.isDungeonMode()
                && !gameState.isCameraAnimating()
                && !hasBlockingOverlay(gameState);
    }

    private Integer pressedDungeonActionKey(GameState gameState) {
        Integer keyCode = pressedActionKey(gameState, InputBindings.Action.MOVE_FORWARD, GLFW_KEY_UP, GLFW_KEY_KP_8);
        if (keyCode != null) {
            return keyCode;
        }

        keyCode = pressedActionKey(gameState, InputBindings.Action.STRAFE_LEFT, GLFW_KEY_KP_4);
        if (keyCode != null) {
            return keyCode;
        }

        keyCode = pressedActionKey(gameState, InputBindings.Action.STRAFE_RIGHT, GLFW_KEY_KP_6);
        if (keyCode != null) {
            return keyCode;
        }

        keyCode = pressedActionKey(gameState, InputBindings.Action.MOVE_BACKWARD, GLFW_KEY_DOWN, GLFW_KEY_KP_2);
        if (keyCode != null) {
            return keyCode;
        }

        keyCode = pressedActionKey(gameState, InputBindings.Action.TURN_LEFT, GLFW_KEY_LEFT, GLFW_KEY_KP_7);
        if (keyCode != null) {
            return keyCode;
        }

        keyCode = pressedActionKey(gameState, InputBindings.Action.TURN_RIGHT, GLFW_KEY_RIGHT, GLFW_KEY_KP_9);
        if (keyCode != null) {
            return keyCode;
        }

        if (consumeBound(gameState, InputBindings.Action.INTERACT)
                || consume(GLFW_KEY_ENTER)
                || consume(GLFW_KEY_KP_ENTER)
                || consume(GLFW_KEY_SPACE)) {
            return gameState.getInputBindings().getKeyCode(InputBindings.Action.INTERACT);
        }

        return null;
    }

    private Integer pressedActionKey(GameState gameState, InputBindings.Action action, int... alternateGlfwKeys) {
        Integer boundKey = pressedBoundKey(gameState, action);
        if (boundKey != null) {
            return boundKey;
        }

        for (int alternateKey : alternateGlfwKeys) {
            if (pressed(alternateKey)) {
                return gameState.getInputBindings().getKeyCode(action);
            }
        }

        return null;
    }

    private Integer pressedBoundKey(GameState gameState, InputBindings.Action action) {
        if (gameState == null || action == null) {
            return null;
        }

        InputBindings bindings = gameState.getInputBindings();
        for (int key : pressedKeys) {
            int awtKey = glfwToAwtKey(key);
            if (bindings.matches(action, awtKey)) {
                return awtKey;
            }
        }

        return null;
    }

    private void releaseMouseLook() {
        if (rightMouseHeld && window != 0L) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }

        rightMouseHeld = false;
        skipNextMouseDelta = false;
        yawOffsetDegrees = 0.0;
        pitchOffsetDegrees = 0.0;
    }

    private boolean pressed(int key) {
        return pressedKeys.contains(key);
    }

    private boolean pressedBound(GameState gameState, InputBindings.Action action) {
        if (gameState == null || action == null) {
            return false;
        }

        InputBindings bindings = gameState.getInputBindings();
        for (int key : pressedKeys) {
            if (bindings.matches(action, glfwToAwtKey(key))) {
                return true;
            }
        }

        return false;
    }

    private boolean consume(int key) {
        if (!pressedKeys.contains(key) || consumedKeys.contains(key)) {
            return false;
        }

        consumedKeys.add(key);
        return true;
    }

    private boolean consumeBound(GameState gameState, InputBindings.Action action) {
        if (gameState == null || action == null) {
            return false;
        }

        InputBindings bindings = gameState.getInputBindings();
        for (int key : new HashSet<>(pressedKeys)) {
            if (!consumedKeys.contains(key) && bindings.matches(action, glfwToAwtKey(key))) {
                consumedKeys.add(key);
                return true;
            }
        }

        return false;
    }

    private void markAction() {
        actionCooldownRemainingMs = actionCooldownMs;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int glfwToAwtKey(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) {
            return KeyEvent.VK_A + (key - GLFW_KEY_A);
        }

        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) {
            return KeyEvent.VK_0 + (key - GLFW_KEY_0);
        }

        if (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9) {
            return KeyEvent.VK_NUMPAD0 + (key - GLFW_KEY_KP_0);
        }

        if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F12) {
            return KeyEvent.VK_F1 + (key - GLFW_KEY_F1);
        }

        return switch (key) {
            case GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
            case GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
            case GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
            case GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
            case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }
}

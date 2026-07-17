package org.main.experimental;

import org.main.core.AetherGameRuntime;
import org.main.core.GameState;
import org.main.core.InteractionSystem;
import org.main.core.Library;
import org.main.core.SaveSystem;
import org.main.content.PlayerRegionLibrary;
import org.main.engine.DungeonRenderContext;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.TextureManager;

import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public final class LwjglDungeonPrototype {
    private final AetherGameRuntime runtime;
    private final LwjglDungeonViewport viewport;
    private final LwjglInputController inputController = new LwjglInputController();
    private final LwjglTextOverlayRenderer overlayRenderer = new LwjglTextOverlayRenderer();
    private final int smokeRunMs;
    private final int smokeFrameLimit;
    private final boolean smokeStartNewGame;
    private final boolean launchNewGame;
    private final boolean launchLoadGame;
    private final String smokeActions;
    private final String startMapName;
    private final String characterName;
    private final String regionName;
    private boolean smokeActionsApplied;
    private String lastMapThemeKey = "";

    private LwjglDungeonPrototype(PrototypeOptions options) {
        this.runtime = new AetherGameRuntime();
        this.smokeRunMs = options.smokeRunMs();
        this.smokeFrameLimit = options.smokeFrameLimit();
        this.smokeStartNewGame = options.smokeStartNewGame();
        this.launchNewGame = options.launchNewGame();
        this.launchLoadGame = options.launchLoadGame();
        this.smokeActions = options.smokeActions();
        this.startMapName = options.startMapName();
        this.characterName = options.characterName();
        this.regionName = options.regionName();

        TextureManager textureManager = new TextureManager();
        textureManager.loadFromFolder("assets/images/building");
        this.viewport = new LwjglDungeonViewport(textureManager, runtime.activeEnvironmentThemes());
    }

    public static void main(String[] args) {
        new LwjglDungeonPrototype(PrototypeOptions.parse(args)).run();
    }

    private void run() {
        viewport.initialize();
        overlayRenderer.setQuitAction(viewport::requestClose);
        overlayRenderer.setMapChangedAction(this::refreshViewportChunkSettings);
        inputController.setMapChangedAction(this::refreshViewportChunkSettings);
        inputController.install(viewport.windowHandle(), viewport, overlayRenderer, runtime);
        boolean startedCharacter = false;
        if (launchLoadGame) {
            startedCharacter = loadGameAtStartup();
        }
        if (!startedCharacter && (smokeStartNewGame || launchNewGame)) {
            runtime.startNewGame(characterName.isBlank() ? "Smoke" : characterName, resolveRegion(regionName));
            refreshViewportChunkSettings();
            startedCharacter = true;
        }
        if (!startMapName.isBlank()) {
            startAuthoredMap(startMapName, startedCharacter);
        }
        applySmokeActionsIfNeeded();
        long lastFrameTime = System.currentTimeMillis();
        long runStartTime = lastFrameTime;
        long smokeDeadline = smokeRunMs <= 0 ? Long.MAX_VALUE : lastFrameTime + smokeRunMs;
        int renderedFrames = 0;

        try {
            while (!viewport.shouldClose()) {
                long now = System.currentTimeMillis();
                int deltaMs = (int) Math.max(0, now - lastFrameTime);
                lastFrameTime = now;

                inputController.update(deltaMs, runtime, viewport);
                runtime.update(deltaMs);
                refreshViewportChunkSettingsIfMapChanged();
                viewport.renderFrame(createContext(), inputController.cameraLook(), overlayRenderer, runtime);
                viewport.pollEvents();
                renderedFrames++;
                if (now >= smokeDeadline || (smokeFrameLimit > 0 && renderedFrames >= smokeFrameLimit)) {
                    viewport.requestClose();
                }
            }
            if (isSmokeRun()) {
                long elapsedMs = Math.max(0, System.currentTimeMillis() - runStartTime);
                System.out.println("LWJGL smoke run completed: frames=" + renderedFrames
                        + ", elapsedMs=" + elapsedMs
                        + ", mode=" + runtime.gameState().getGameMode()
                        + ", character=" + playerSummary()
                        + ", mapPath=" + currentMapSummary()
                        + ", map=" + runtime.gameState().getDungeonMap().getWidth()
                        + "x" + runtime.gameState().getDungeonMap().getHeight()
                        + ", mapStates=" + runtime.gameState().getMapRuntimeStatesView().size()
                        + ", player=" + runtime.gameState().getPlayerX()
                        + "," + runtime.gameState().getPlayerY()
                        + " dir=" + runtime.gameState().getDirection()
                        + ", entities=" + runtime.gameState().getEntities().size()
                        + ", interaction=" + runtime.gameState().hasActiveInteraction()
                        + ", interactionDetails=" + interactionSummary()
                        + ", shop=" + runtime.gameState().hasActiveShop()
                        + ", ui=" + activePanelsSummary()
                        + ", save=" + (SaveSystem.hasSave() ? "present" : "missing")
                        + ", battleChoices=" + battleChoicesSummary()
                        + ", audio=" + smokeAudioSummary()
                        + ", " + viewport.sceneSummary());
            }
        } finally {
            runtime.soundSystem().stopAll();
            overlayRenderer.shutdown();
            viewport.shutdown();
        }
    }

    private DungeonRenderContext createContext() {
        Dimension framebufferSize = viewport.framebufferSize();
        return runtime.renderContext(framebufferSize.width, framebufferSize.height);
    }

    private void refreshViewportChunkSettingsIfMapChanged() {
        Path path = runtime.gameState().getCurrentMapDesignPath();
        String key = path == null ? "" : path.toAbsolutePath().normalize().toString();
        if (key.equals(lastMapThemeKey)) {
            return;
        }

        lastMapThemeKey = key;
        refreshViewportChunkSettings();
    }

    private void refreshViewportChunkSettings() {
        viewport.setEnvironmentThemes(runtime.activeEnvironmentThemes());
        viewport.setSkyboxPath(runtime.activeSkyboxPath());
    }

    private boolean isSmokeRun() {
        return smokeRunMs > 0
                || smokeFrameLimit > 0
                || smokeStartNewGame
                || launchNewGame
                || launchLoadGame
                || !smokeActions.isBlank()
                || !startMapName.isBlank();
    }

    private boolean loadGameAtStartup() {
        try {
            runtime.loadGame();
            refreshViewportChunkSettings();
            return true;
        } catch (IOException exception) {
            System.out.println("LWJGL prototype failed to load saved game: " + exception.getMessage());
            return false;
        }
    }

    private void startAuthoredMap(String mapName, boolean preserveCurrentCharacter) {
        Optional<Path> mapPath = resolveMapPath(mapName);
        if (mapPath.isEmpty()) {
            System.out.println("LWJGL prototype could not find map: " + mapName);
            return;
        }

        try {
            if (preserveCurrentCharacter) {
                runtime.loadAuthoredMap(mapPath.get());
            } else {
                runtime.startCustomMap(mapPath.get());
            }
            refreshViewportChunkSettings();
        } catch (IOException exception) {
            System.out.println("LWJGL prototype failed to load map " + mapName + ": " + exception.getMessage());
        }
    }

    private PlayerRegionLibrary resolveRegion(String rawRegionName) {
        if (rawRegionName == null || rawRegionName.isBlank()) {
            return PlayerRegionLibrary.MIDLANDS;
        }

        String normalized = rawRegionName.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase();
        for (PlayerRegionLibrary region : PlayerRegionLibrary.values()) {
            if (region.name().equals(normalized)
                    || region.getDisplayName().replace(' ', '_').equalsIgnoreCase(normalized)) {
                return region;
            }
        }

        System.out.println("LWJGL prototype unknown region '" + rawRegionName + "', using Midlands.");
        return PlayerRegionLibrary.MIDLANDS;
    }

    private Optional<Path> resolveMapPath(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return Optional.empty();
        }

        Path directPath = Path.of(mapName);
        if (java.nio.file.Files.isRegularFile(directPath)) {
            return Optional.of(directPath);
        }

        String normalizedName = normalizeMapName(mapName);
        try {
            return runtime.listAvailableMaps().stream()
                    .filter(path -> normalizedName.equals(normalizeMapName(path.getFileName() == null
                            ? path.toString()
                            : path.getFileName().toString())))
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String normalizeMapName(String mapName) {
        String value = mapName == null ? "" : mapName.trim().replace('\\', '/');
        int slashIndex = value.lastIndexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(slashIndex + 1);
        }
        value = value.toLowerCase();
        if (value.endsWith(".properties")) {
            value = value.substring(0, value.length() - ".properties".length());
        }
        return value;
    }

    private void applySmokeActionsIfNeeded() {
        if (smokeActionsApplied || smokeActions == null || smokeActions.isBlank()) {
            return;
        }

        smokeActionsApplied = true;
        for (String rawAction : smokeActions.split(",")) {
            applySmokeAction(rawAction == null ? "" : rawAction.trim().toLowerCase());
        }
    }

    private void applySmokeAction(String action) {
        if (tryIndexedSmokeAction(action)) {
            return;
        }

        switch (action) {
            case "", "none" -> {
            }
            case "forward", "move-forward" -> runtime.dungeonController().moveForward();
            case "back", "backward", "move-backward" -> runtime.dungeonController().moveBackward();
            case "strafe-left", "left" -> runtime.dungeonController().strafeLeft();
            case "strafe-right", "right" -> runtime.dungeonController().strafeRight();
            case "turn-left" -> runtime.dungeonController().turnLeft();
            case "turn-right" -> runtime.dungeonController().turnRight();
            case "interact" -> runtime.dungeonController().interact();
            case "inventory", "toggle-inventory" -> runtime.gameState().toggleInventory();
            case "skills", "toggle-skills" -> runtime.gameState().toggleSkills();
            case "quests", "toggle-quests" -> runtime.gameState().toggleQuests();
            case "stats", "toggle-stats" -> runtime.gameState().toggleStats();
            case "close-ui", "close-overlays" -> closeAllOverlays();
            case "close-interaction" -> runtime.gameState().closeInteraction();
            case "config", "escape-menu", "settings" -> openConfigMenu();
            case "save" -> saveGame();
            case "load" -> loadGame();
            case "force-battle", "battle-nearest" -> forceNearestBattle();
            case "force-interaction", "interaction-nearest" -> forceNearestInteraction();
            case "force-map-link", "map-link-nearest" -> forceNearestMapLink(false);
            case "travel-map-link", "travel-nearest-map-link" -> forceNearestMapLink(true);
            case "battle-attack" -> handleBattleCommand(Library.BattleCommand.ATTACK);
            case "battle-skill" -> handleBattleCommand(Library.BattleCommand.SKILL);
            case "battle-items", "battle-item" -> handleBattleCommand(Library.BattleCommand.ITEMS);
            case "battle-run" -> handleBattleCommand(Library.BattleCommand.RUN);
            case "battle-cancel" -> runtime.battleController().cancelBattleSelection();
            default -> System.out.println("Ignoring unknown LWJGL smoke action: " + action);
        }
    }

    private void closeAllOverlays() {
        GameState gameState = runtime.gameState();
        gameState.closeInteraction();
        gameState.closeShop();
        gameState.closeInventory();
        gameState.closeSkills();
        gameState.closeQuests();
        gameState.closeStats();
        runtime.battleController().cancelBattleSelection();
    }

    private void openConfigMenu() {
        GameState gameState = runtime.gameState();
        gameState.openInteraction(InteractionSystem.configMenu(
                runtime.soundSystem(),
                gameState,
                viewport::requestClose,
                () -> gameState.openInteraction(InteractionSystem.controlsMenu(gameState.getInputBindings())),
                this::saveGame,
                this::loadGame
        ));
    }

    private void saveGame() {
        try {
            runtime.saveGame();
        } catch (Exception exception) {
            System.out.println("LWJGL smoke save failed: " + exception.getMessage());
        }
    }

    private void loadGame() {
        try {
            runtime.loadGame();
            refreshViewportChunkSettings();
        } catch (Exception exception) {
            System.out.println("LWJGL smoke load failed: " + exception.getMessage());
        }
    }

    private void handleBattleCommand(Library.BattleCommand command) {
        if (!runtime.gameState().isBattleMode()) {
            System.out.println("Ignoring battle smoke command outside battle: " + command);
            return;
        }

        runtime.battleController().handleBattleCommand(command);
    }

    private boolean tryIndexedSmokeAction(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }

        if (action.startsWith("battle-select-")) {
            selectBattleChoice(action.substring("battle-select-".length()));
            return true;
        }

        if (action.startsWith("battle-choice-")) {
            selectBattleChoice(action.substring("battle-choice-".length()));
            return true;
        }

        if (action.startsWith("interaction-select-")) {
            selectInteractionChoice(action.substring("interaction-select-".length()));
            return true;
        }

        if (action.startsWith("interaction-choice-")) {
            selectInteractionChoice(action.substring("interaction-choice-".length()));
            return true;
        }

        if (action.startsWith("travel-map:")) {
            travelToMap(action.substring("travel-map:".length()));
            return true;
        }

        if (action.startsWith("travel-to-map:")) {
            travelToMap(action.substring("travel-to-map:".length()));
            return true;
        }

        if (action.startsWith("set-position:")) {
            setPlayerPosition(action.substring("set-position:".length()));
            return true;
        }

        return false;
    }

    private void travelToMap(String rawValue) {
        String[] parts = splitActionArgs(rawValue, 3);
        if (parts.length != 3) {
            System.out.println("Ignoring invalid travel-map smoke action: " + rawValue);
            return;
        }

        Optional<Path> mapPath = resolveMapPath(parts[0]);
        if (mapPath.isEmpty()) {
            System.out.println("Ignoring travel-map smoke action with unknown map: " + parts[0]);
            return;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            runtime.gameState().travelToMapLink(mapPath.get(), x, y);
            refreshViewportChunkSettings();
        } catch (IOException | NumberFormatException exception) {
            System.out.println("Ignoring failed travel-map smoke action: " + exception.getMessage());
        }
    }

    private void setPlayerPosition(String rawValue) {
        String[] parts = splitActionArgs(rawValue, 3);
        if (parts.length < 2) {
            System.out.println("Ignoring invalid set-position smoke action: " + rawValue);
            return;
        }

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int direction = parts.length >= 3 && !parts[2].isBlank()
                    ? Integer.parseInt(parts[2])
                    : runtime.gameState().getDirection();
            runtime.gameState().setPlayerPosition(x, y);
            runtime.gameState().setDirection(direction);
        } catch (NumberFormatException exception) {
            System.out.println("Ignoring invalid set-position smoke action: " + rawValue);
        }
    }

    private String[] splitActionArgs(String rawValue, int maxParts) {
        if (rawValue == null || rawValue.isBlank()) {
            return new String[0];
        }
        return rawValue.split(":", maxParts);
    }

    private void selectBattleChoice(String rawIndex) {
        if (!runtime.gameState().isBattleMode()) {
            System.out.println("Ignoring battle smoke selection outside battle: " + rawIndex);
            return;
        }

        try {
            runtime.battleController().selectCurrentBattleChoice(Math.max(0, Integer.parseInt(rawIndex)));
        } catch (NumberFormatException exception) {
            System.out.println("Ignoring invalid battle smoke selection: " + rawIndex);
        }
    }

    private void selectInteractionChoice(String rawIndex) {
        if (!runtime.gameState().hasActiveInteraction()) {
            System.out.println("Ignoring interaction smoke selection with no active interaction: " + rawIndex);
            return;
        }

        try {
            runtime.gameState().getActiveInteraction().selectOption(Math.max(0, Integer.parseInt(rawIndex)));
        } catch (NumberFormatException exception) {
            System.out.println("Ignoring invalid interaction smoke selection: " + rawIndex);
        }
    }

    private void forceNearestBattle() {
        Optional<MapEntity> target = runtime.gameState().getEntities().stream()
                .filter(entity -> entity != null && entity.getType() == Library.EntityType.ENEMY)
                .min(Comparator.comparingDouble(this::distanceToPlayer));
        if (target.isEmpty()) {
            System.out.println("LWJGL smoke force-battle found no enemy.");
            return;
        }

        if (!placePlayerAdjacentTo(target.get().getX(), target.get().getY())) {
            System.out.println("LWJGL smoke force-battle could not place player near " + target.get().getName() + ".");
            return;
        }

        runtime.dungeonController().moveForward();
    }

    private void forceNearestInteraction() {
        Optional<MapEntity> targetEntity = runtime.gameState().getEntities().stream()
                .filter(entity -> entity != null
                        && entity.getType() != Library.EntityType.ENEMY
                        && entity.hasInteractionId())
                .min(Comparator.comparingDouble(this::distanceToPlayer));
        if (targetEntity.isPresent()) {
            MapEntity entity = targetEntity.get();
            if (placePlayerAdjacentTo(entity.getX(), entity.getY())) {
                runtime.dungeonController().interact();
            } else {
                System.out.println("LWJGL smoke force-interaction could not place player near " + entity.getName() + ".");
            }
            return;
        }

        Optional<Point> targetTile = runtime.gameState().getTileInteractionIdsView().entrySet().stream()
                .map(Map.Entry::getKey)
                .map(this::parseTileKey)
                .filter(point -> point != null)
                .min(Comparator.comparingDouble(point -> distanceToPlayer(point.x, point.y)));
        if (targetTile.isEmpty()) {
            System.out.println("LWJGL smoke force-interaction found no entity or tile interaction.");
            return;
        }

        Point point = targetTile.get();
        if (!placePlayerAdjacentTo(point.x, point.y)) {
            System.out.println("LWJGL smoke force-interaction could not place player near tile " + point.x + "," + point.y + ".");
            return;
        }

        runtime.dungeonController().interact();
    }

    private void forceNearestMapLink(boolean travel) {
        Optional<Point> targetTile = runtime.gameState().getTileInteractionIdsView().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().startsWith("map_link|"))
                .map(Map.Entry::getKey)
                .map(this::parseTileKey)
                .filter(point -> point != null)
                .min(Comparator.comparingDouble(point -> distanceToPlayer(point.x, point.y)));
        if (targetTile.isEmpty()) {
            System.out.println("LWJGL smoke force-map-link found no map link tile.");
            return;
        }

        Point point = targetTile.get();
        if (!placePlayerAdjacentTo(point.x, point.y)) {
            System.out.println("LWJGL smoke force-map-link could not place player near tile " + point.x + "," + point.y + ".");
            return;
        }

        runtime.dungeonController().interact();
        if (travel && runtime.gameState().hasActiveInteraction()) {
            runtime.gameState().getActiveInteraction().selectOption(0);
            refreshViewportChunkSettings();
        }
    }

    private boolean placePlayerAdjacentTo(int targetX, int targetY) {
        int[][] candidates = {
                {-1, 0, 1},
                {1, 0, 3},
                {0, -1, 2},
                {0, 1, 0}
        };

        for (int[] candidate : candidates) {
            int playerX = targetX + candidate[0];
            int playerY = targetY + candidate[1];
            int direction = candidate[2];
            if (!canStandAt(playerX, playerY)) {
                continue;
            }

            GameState gameState = runtime.gameState();
            gameState.setPlayerPosition(playerX, playerY);
            gameState.setDirection(direction);
            return true;
        }

        return false;
    }

    private boolean canStandAt(int x, int y) {
        DungeonMap map = runtime.gameState().getDungeonMap();
        if (map == null || map.isOutOfBounds(x, y) || !map.isWalkable(x, y)) {
            return false;
        }

        for (MapEntity entity : runtime.gameState().getEntities()) {
            if (entity != null && entity.isAt(x, y) && entity.blocksMovement()) {
                return false;
            }
        }

        return true;
    }

    private Point parseTileKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String[] parts = key.split(",", 2);
        if (parts.length != 2) {
            return null;
        }

        try {
            return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double distanceToPlayer(MapEntity entity) {
        return entity == null ? Double.MAX_VALUE : distanceToPlayer(entity.getX(), entity.getY());
    }

    private double distanceToPlayer(int x, int y) {
        int dx = x - runtime.gameState().getPlayerX();
        int dy = y - runtime.gameState().getPlayerY();
        return Math.hypot(dx, dy);
    }

    private String smokeAudioSummary() {
        return "A:" + onOff(runtime.soundSystem().isAmbienceRunning())
                + "/M:" + onOff(runtime.soundSystem().isMusicRunning())
                + "/L:" + onOff(runtime.soundSystem().isLoopingSoundRunning());
    }

    private String activePanelsSummary() {
        GameState gameState = runtime.gameState();
        return "I:" + onOff(gameState.isInventoryOpen())
                + "/S:" + onOff(gameState.isSkillsOpen())
                + "/Q:" + onOff(gameState.isQuestsOpen())
                + "/St:" + onOff(gameState.isStatsOpen());
    }

    private String currentMapSummary() {
        Path path = runtime.gameState().getCurrentMapDesignPath();
        if (path == null) {
            return "test";
        }
        return (path.getFileName() == null ? path.toString() : path.getFileName().toString())
                .replaceAll("[,\\s]+", "_");
    }

    private String playerSummary() {
        if (runtime.gameState().getPlayerCharacter() == null) {
            return "none";
        }
        String name = runtime.gameState().getPlayerCharacter().getName();
        return (name == null || name.isBlank() ? "Player" : name).replaceAll("[,\\s]+", "_");
    }

    private String battleChoicesSummary() {
        if (!runtime.gameState().isBattleMode()) {
            return "off";
        }

        return runtime.battleController().hasCurrentBattleChoices()
                ? "open"
                : "closed";
    }

    private String interactionSummary() {
        if (!runtime.gameState().hasActiveInteraction()) {
            return "off";
        }

        org.main.core.InteractionSystem.Interaction interaction = runtime.gameState().getActiveInteraction();
        org.main.core.InteractionSystem.InteractionModel model = interaction == null ? null : interaction.getModel();
        if (model == null) {
            return "unknown";
        }

        String title = model.getTitle() == null || model.getTitle().isBlank()
                ? "Untitled"
                : model.getTitle().replaceAll("[,\\s]+", "_");
        return title + ":" + model.getOptions().size();
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private record PrototypeOptions(
            int smokeRunMs,
            int smokeFrameLimit,
            boolean smokeStartNewGame,
            boolean launchNewGame,
            boolean launchLoadGame,
            String smokeActions,
            String startMapName,
            String characterName,
            String regionName
    ) {
        private static PrototypeOptions parse(String[] args) {
            int smokeRunMs = 0;
            int smokeFrameLimit = 0;
            boolean smokeStartNewGame = false;
            boolean launchNewGame = false;
            boolean launchLoadGame = false;
            String smokeActions = "";
            String startMapName = "";
            String characterName = "";
            String regionName = "";
            if (args == null) {
                return new PrototypeOptions(
                        smokeRunMs,
                        smokeFrameLimit,
                        smokeStartNewGame,
                        launchNewGame,
                        launchLoadGame,
                        smokeActions,
                        startMapName,
                        characterName,
                        regionName
                );
            }

            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }

                if (arg.startsWith("--smoke-ms=")) {
                    smokeRunMs = parseNonNegativeInt(arg.substring("--smoke-ms=".length()), smokeRunMs);
                } else if (arg.startsWith("--smoke-frames=")) {
                    smokeFrameLimit = parseNonNegativeInt(arg.substring("--smoke-frames=".length()), smokeFrameLimit);
                } else if ("--smoke-new-game".equals(arg)) {
                    smokeStartNewGame = true;
                } else if ("--new-game".equals(arg)) {
                    launchNewGame = true;
                } else if ("--load-game".equals(arg)) {
                    launchLoadGame = true;
                } else if (arg.startsWith("--smoke-actions=")) {
                    smokeActions = arg.substring("--smoke-actions=".length());
                } else if (arg.startsWith("--map=")) {
                    startMapName = arg.substring("--map=".length()).trim();
                } else if (arg.startsWith("--name=")) {
                    characterName = arg.substring("--name=".length()).trim();
                } else if (arg.startsWith("--region=")) {
                    regionName = arg.substring("--region=".length()).trim();
                }
            }
            return new PrototypeOptions(
                    smokeRunMs,
                    smokeFrameLimit,
                    smokeStartNewGame,
                    launchNewGame,
                    launchLoadGame,
                    smokeActions,
                    startMapName,
                    characterName,
                    regionName
            );
        }

        private static int parseNonNegativeInt(String rawValue, int fallback) {
            try {
                return Math.max(0, Integer.parseInt(rawValue));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}

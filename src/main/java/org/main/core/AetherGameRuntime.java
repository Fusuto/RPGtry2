package org.main.core;

import org.main.battle.BattleController;
import org.main.battle.BattleAssets;
import org.main.battle.BattleRenderer;
import org.main.content.EnvironmentLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.engine.DungeonRenderContext;
import org.main.engine.DungeonMap;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MovementEngine;
import org.main.engine.SoundSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class AetherGameRuntime {
    private static final String TUTORIAL_MAP_FILE = "tutorial.properties";
    private static final String GAME_OVER_MUSIC_PATH = null;

    private final EnvironmentLibrary environment = EnvironmentLibrary.STARTER_DUNGEON;
    private final MovementEngine movementEngine = new MovementEngine();
    private final SoundSystem soundSystem = new SoundSystem();
    private final InteractionSystem.InteractionRegistry interactionRegistry =
            InteractionSystem.InteractionRegistry.createDefault();
    private final BattleRenderer battleRenderer = new BattleRenderer();
    private final GameState gameState = new GameState(
            DungeonMap.testMap(),
            GameBootstrap.createDefaultPlayerCharacter()
    );
    private final DungeonController dungeonController = new DungeonController(
            gameState,
            movementEngine,
            interactionRegistry,
            soundSystem,
            environment
    );
    private final BattleController battleController = new BattleController(
            gameState,
            battleRenderer,
            soundSystem,
            environment
    );
    private boolean gameOverMusicStarted = false;
    private String lastChunkAmbienceKey = "";

    public AetherGameRuntime() {
        battleRenderer.setAssets(BattleAssets.loadDefault());
        gameState.unlockMiniMap();
        loadInitialTestMap();
        gameState.setGameMode(GameState.GameMode.START_MENU);
    }

    public GameState gameState() {
        return gameState;
    }

    public DungeonController dungeonController() {
        return dungeonController;
    }

    public BattleController battleController() {
        return battleController;
    }

    public BattleRenderer battleRenderer() {
        return battleRenderer;
    }

    public SoundSystem soundSystem() {
        return soundSystem;
    }

    public EnvironmentLibrary environment() {
        return environment;
    }

    public List<EnvironmentTheme> activeEnvironmentThemes() {
        Path mapDesignPath = gameState.getCurrentMapDesignPath();
        if (mapDesignPath == null) {
            return environment.getThemes();
        }

        try {
            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(mapDesignPath);
            return List.of(mapDesign.primaryTheme().getTheme(), mapDesign.alternateTheme().getTheme());
        } catch (IOException exception) {
            return environment.getThemes();
        }
    }

    public void startNewGame(String characterName, PlayerRegionLibrary playerRegion) {
        String safeName = characterName == null || characterName.isBlank() ? "Player" : characterName.trim();
        PlayerRegionLibrary safeRegion = playerRegion == null ? PlayerRegionLibrary.MIDLANDS : playerRegion;
        gameState.setPlayerCharacter(GameBootstrap.createPlayerCharacter(safeName, safeRegion));
        if (!loadTutorialMap()) {
            loadInitialTestMap();
        }
        gameState.setGameMode(GameState.GameMode.DUNGEON);
        gameOverMusicStarted = false;
        soundSystem.stopAll();
        playActiveChunkAmbience();
    }

    public void loadInitialTestMap() {
        gameState.changeDungeon(DungeonMap.testMap(), 1, 1, List.of());
        GameBootstrap.seedTestContent(gameState);
    }

    public boolean loadTutorialMap() {
        try {
            Path tutorialPath = findTutorialMapPath();
            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(tutorialPath);
            gameState.changeDungeon(mapDesign, tutorialPath);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public List<Path> listAvailableMaps() throws IOException {
        return MapDesignLibrary.listSavedMaps();
    }

    public String describeMap(Path mapPath) {
        if (mapPath == null) {
            return "Unknown map";
        }

        try {
            MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(mapPath);
            if (mapDesign.description().isBlank()) {
                return mapDesign.displayName();
            }

            return mapDesign.displayName() + " - " + mapDesign.description();
        } catch (IOException exception) {
            return mapPath.getFileName() == null
                    ? mapPath.toString()
                    : mapPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        }
    }

    public void startCustomMap(Path mapPath) throws IOException {
        if (mapPath == null) {
            throw new IOException("No map selected.");
        }

        gameState.setPlayerCharacter(GameBootstrap.createDefaultPlayerCharacter());
        loadAuthoredMap(mapPath);
    }

    public MapDesignLibrary.MapDesign loadAuthoredMap(Path mapPath) throws IOException {
        if (mapPath == null) {
            throw new IOException("No map selected.");
        }

        MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(mapPath);
        gameState.changeDungeon(mapDesign, mapPath);
        gameState.setGameMode(GameState.GameMode.DUNGEON);
        gameOverMusicStarted = false;
        soundSystem.stopAll();
        playActiveChunkAmbience();
        return mapDesign;
    }

    public void loadGame() throws IOException {
        SaveSystem.load(gameState);
        gameOverMusicStarted = false;
        soundSystem.stopAll();
        lastChunkAmbienceKey = "";
        refreshChunkAmbienceIfNeeded();
    }

    public void saveGame() throws IOException {
        SaveSystem.save(gameState);
    }

    public void returnToMainMenu() {
        soundSystem.stopAll();
        gameOverMusicStarted = false;
        gameState.setGameMode(GameState.GameMode.START_MENU);
    }

    public void update(int deltaMs) {
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
        battleController.update(deltaMs);
        refreshChunkAmbienceIfNeeded();

        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }
    }

    public DungeonRenderContext renderContext(int viewportWidth, int viewportHeight) {
        return new DungeonRenderContext(
                gameState.getDungeonMap(),
                gameState.getEntities(),
                gameState.getPlayerCharacter(),
                gameState.getPlayerX(),
                gameState.getPlayerY(),
                gameState.getDirection(),
                viewportWidth,
                viewportHeight,
                gameState.getCameraOffsetForward(),
                gameState.getCameraOffsetSide(),
                gameState.getCameraRotationRadians()
        );
    }

    public String activeChunkAmbiencePath() {
        MapDesignLibrary.MapDesign mapDesign = activeMapDesign();
        if (mapDesign != null && !mapDesign.musicPath().isBlank()) {
            return mapDesign.musicPath();
        }
        return environment.getAmbienceSoundPath();
    }

    public String activeSkyboxPath() {
        MapDesignLibrary.MapDesign mapDesign = activeMapDesign();
        return mapDesign == null ? "" : mapDesign.skyboxPath();
    }

    private MapDesignLibrary.MapDesign activeMapDesign() {
        Path mapDesignPath = gameState.getCurrentMapDesignPath();
        if (mapDesignPath == null) {
            return null;
        }

        try {
            return MapDesignLibrary.load(mapDesignPath);
        } catch (IOException exception) {
            return null;
        }
    }

    private void refreshChunkAmbienceIfNeeded() {
        if (!gameState.isDungeonMode()) {
            return;
        }

        String ambiencePath = activeChunkAmbiencePath();
        String key = gameState.getCurrentMapDesignPath() + "|" + ambiencePath;
        if (key.equals(lastChunkAmbienceKey)) {
            return;
        }

        lastChunkAmbienceKey = key;
        playActiveChunkAmbience();
    }

    private void playActiveChunkAmbience() {
        soundSystem.playAmbience(activeChunkAmbiencePath());
    }

    private Path findTutorialMapPath() throws IOException {
        List<Path> savedMaps = MapDesignLibrary.listSavedMaps();
        for (Path mapPath : savedMaps) {
            if (mapPath.getFileName() != null
                    && TUTORIAL_MAP_FILE.equalsIgnoreCase(mapPath.getFileName().toString())) {
                return mapPath;
            }
        }

        return Path.of(MapDesignLibrary.MAP_RESOURCE_FOLDER, TUTORIAL_MAP_FILE);
    }
}

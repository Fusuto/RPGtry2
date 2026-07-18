package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.battle.BattleSkill;
import org.main.content.GatheringNodeLibrary;
import org.main.content.ItemLibrary;
import org.main.content.MapDesignLibrary;
import org.main.content.QuestLibrary;
import org.main.content.RecipeLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameState {
    private DungeonMap dungeonMap;
    private final List<MapEntity> entities = new ArrayList<>();
    private final Map<String, String> tileInteractionIds = new HashMap<>();
    private final List<MapDesignLibrary.MapTrigger> mapTriggers = new ArrayList<>();
    private final Map<String, MapDesignLibrary.AuthoredDialogue> authoredDialogues = new HashMap<>();
    private final Map<String, QuestDefinition> authoredQuestDefinitions = new HashMap<>();
    private final Map<String, MapDesignLibrary.CustomItem> customItems = new HashMap<>();
    private final Map<String, MapDesignLibrary.CustomLimb> customLimbs = new HashMap<>();
    private final Map<String, MapDesignLibrary.CustomGatheringNode> customGatheringNodes = new HashMap<>();
    private final Map<String, MapDesignLibrary.CustomCookingRecipe> customCookingRecipes = new HashMap<>();
    private final Map<String, MapDesignLibrary.CustomCompositeRecipe> customCompositeRecipes = new HashMap<>();
    private final Map<String, MapRuntimeState> mapRuntimeStates = new HashMap<>();
    private final Map<String, OpenWorldSession> openWorldSessions = new HashMap<>();
    private final Set<String> spokenAuthoredDialogues = new HashSet<>();
    private final Set<String> removedEntityKeys = new HashSet<>();
    private final Set<String> firedMapTriggerIds = new HashSet<>();
    private final Map<String, Integer> questStages = new HashMap<>();
    private final InputBindings inputBindings = new InputBindings();

    private GameMode gameMode = GameMode.START_MENU;

    private int playerX = 1;
    private int playerY = 1;
    private int currentFloor = 1;
    private Path currentMapDesignPath;
    private OpenWorldSession currentOpenWorldSession;
    private double movementStartX = 1.0;
    private double movementStartY = 1.0;
    private double movementProgress = 1.0;
    private double rotationStartOffsetRadians = 0.0;
    private double rotationProgress = 1.0;
    private CameraMovementMode cameraMovementMode = CameraMovementMode.FLUID;

    private boolean miniMapUnlocked = false;
    private MiniMapMode miniMapMode = MiniMapMode.DISCOVERED;
    private boolean[][] discoveredMiniMapTiles = new boolean[0][0];
    private boolean performanceOverlayVisible = false;

    // 0 = north, 1 = east, 2 = south, 3 = west
    private int direction = 1;

    private BattleEncounter currentEncounter;
    private MapEntity currentEnemyEntity;
    private PlayerCharacter playerCharacter;
    private boolean inventoryOpen = false;
    private boolean skillsOpen = false;
    private boolean questsOpen = false;
    private boolean statsOpen = false;
    private boolean levelUpPending = false;
    private boolean fishingActive = false;
    private int fishingX = -1;
    private int fishingY = -1;
    private int fishingElapsedMs = 0;
    private String fishingInteractionId = "";
    private String fishingMessage = "Cast your line.";
    private boolean miningActive = false;
    private int miningX = -1;
    private int miningY = -1;
    private int miningElapsedMs = 0;
    private String miningInteractionId = "";
    private String miningMessage = "Strike the rock.";
    private final Map<String, ResourceNodeState> resourceNodeStates = new HashMap<>();
    private boolean cookingActive = false;
    private int cookingX = -1;
    private int cookingY = -1;
    private int cookingElapsedMs = 0;
    private int selectedWorldItemIndex = -1;
    private InventorySystem.Item selectedWorldItem;
    private String selectedWorldItemName;
    private String cookingItemName;
    private String cookingMessage = "Use raw fish, then interact with a campfire.";
    private boolean smeltingActive = false;
    private int smeltingX = -1;
    private int smeltingY = -1;
    private int smeltingElapsedMs = 0;
    private String smeltingItemName;
    private String smeltingMessage = "Use ore, then interact with a furnace.";
    private String smithingMaterialName;
    private String smithingMessage = "Use a metal bar, then interact with an anvil.";
    private String selectedQuestId;
    private InteractionSystem.Interaction activeInteraction;
    private ShopSystem.ShopSession activeShop;

    public enum GameMode {
        START_MENU,
        CHARACTER_CREATION,
        DUNGEON,
        BATTLE,
        GAME_OVER;

        public boolean isDungeon() {
            return this == DUNGEON;
        }

        public boolean isBattle() {
            return this == BATTLE;
        }
    }

    public InteractionSystem.Interaction getActiveInteraction() {
        if (activeInteraction != null && activeInteraction.isClosed()) {
            activeInteraction = null;
            stopFishing();
            stopCooking();
            stopMining();
            stopSmelting();
        }

        return activeInteraction;
    }

    public boolean hasActiveInteraction() {
        return getActiveInteraction() != null;
    }

    public void openInteraction(InteractionSystem.Interaction interaction) {
        closeInventory();
        closeSkills();
        closeQuests();
        closeStats();
        closeShop();
        activeInteraction = interaction;
    }

    public void closeInteraction() {
        if (activeInteraction != null) {
            activeInteraction.close();
        }

        activeInteraction = null;
        stopFishing();
        stopCooking();
        stopMining();
    }

    public PlayerCharacter getPlayerCharacter() {
        return playerCharacter;
    }

    public void setPlayerCharacter(PlayerCharacter playerCharacter) {
        this.playerCharacter = playerCharacter == null
                ? GameBootstrap.createDefaultPlayerCharacter()
                : playerCharacter;
    }

    public InventorySystem.Inventory getInventory() {
        return playerCharacter.getInventory();
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public void setInventoryOpen(boolean inventoryOpen) {
        if (inventoryOpen) {
            closeSkills();
            closeQuests();
            closeStats();
            closeShop();

            if (!isInventoryOverlayAllowed()) {
                closeInteraction();
            }
        }

        this.inventoryOpen = inventoryOpen;
    }

    public void toggleInventory() {
        setInventoryOpen(!inventoryOpen);
    }

    public void closeInventory() {
        inventoryOpen = false;
    }

    public boolean isInventoryOverlayAllowed() {
        InteractionSystem.Interaction interaction = getActiveInteraction();
        return interaction != null && interaction.isInventoryOverlayAllowed();
    }

    public boolean isSkillsOpen() {
        return skillsOpen;
    }

    public void setSkillsOpen(boolean skillsOpen) {
        if (skillsOpen) {
            closeInventory();
            closeQuests();
            closeStats();
            closeShop();
            closeInteraction();
        }

        this.skillsOpen = skillsOpen;
    }

    public void toggleSkills() {
        setSkillsOpen(!skillsOpen);
    }

    public void closeSkills() {
        skillsOpen = false;
    }

    public boolean isQuestsOpen() {
        return questsOpen;
    }

    public void setQuestsOpen(boolean questsOpen) {
        if (questsOpen) {
            closeInventory();
            closeSkills();
            closeStats();
            closeShop();
            closeInteraction();
        }

        this.questsOpen = questsOpen;
    }

    public void toggleQuests() {
        setQuestsOpen(!questsOpen);
    }

    public void closeQuests() {
        questsOpen = false;
    }

    public boolean isStatsOpen() {
        return statsOpen;
    }

    public void setStatsOpen(boolean statsOpen) {
        if (statsOpen) {
            closeInventory();
            closeSkills();
            closeQuests();
            closeShop();
            closeInteraction();
        }

        this.statsOpen = statsOpen;
    }

    public void toggleStats() {
        setStatsOpen(!statsOpen);
    }

    public void closeStats() {
        statsOpen = false;
    }

    public boolean isLevelUpPending() {
        return levelUpPending;
    }

    public void setLevelUpPending(boolean levelUpPending) {
        this.levelUpPending = levelUpPending;
    }

    public String getSelectedQuestId() {
        return selectedQuestId;
    }

    public void setSelectedQuestId(String selectedQuestId) {
        this.selectedQuestId = selectedQuestId;
    }

    public GameState(DungeonMap dungeonMap) {
        this(dungeonMap, GameBootstrap.createDefaultPlayerCharacter());
    }

    public GameState(DungeonMap dungeonMap, PlayerCharacter playerCharacter) {
        this.dungeonMap = dungeonMap;
        this.playerCharacter = playerCharacter == null
                ? GameBootstrap.createDefaultPlayerCharacter()
                : playerCharacter;
        resetMiniMapDiscovery();
        revealMiniMapTile(playerX, playerY);
    }

    public boolean isMiniMapUnlocked() {
        return miniMapUnlocked;
    }

    public void setMiniMapUnlocked(boolean miniMapUnlocked) {
        this.miniMapUnlocked = miniMapUnlocked;
    }

    public void unlockMiniMap() {
        miniMapUnlocked = true;
    }

    public void lockMiniMap() {
        miniMapUnlocked = false;
    }

    public boolean isMiniMapVisible() {
        return miniMapMode != MiniMapMode.OFF;
    }

    public void setMiniMapVisible(boolean miniMapVisible) {
        miniMapMode = miniMapVisible ? MiniMapMode.DISCOVERED : MiniMapMode.OFF;
    }

    public void toggleMiniMapVisible() {
        cycleMiniMapMode();
    }

    public MiniMapMode getMiniMapMode() {
        return miniMapMode;
    }

    public void setMiniMapMode(MiniMapMode miniMapMode) {
        this.miniMapMode = miniMapMode == null ? MiniMapMode.OFF : miniMapMode;
    }

    public void cycleMiniMapMode() {
        miniMapMode = switch (miniMapMode) {
            case OFF -> MiniMapMode.DISCOVERED;
            case DISCOVERED -> MiniMapMode.DEBUG;
            case DEBUG -> MiniMapMode.OFF;
        };
    }

    public boolean isMiniMapDebugMode() {
        return miniMapMode == MiniMapMode.DEBUG;
    }

    public boolean isPerformanceOverlayVisible() {
        return performanceOverlayVisible;
    }

    public void setPerformanceOverlayVisible(boolean performanceOverlayVisible) {
        this.performanceOverlayVisible = performanceOverlayVisible;
    }

    public void togglePerformanceOverlayVisible() {
        performanceOverlayVisible = !performanceOverlayVisible;
    }

    public boolean isMiniMapTileDiscovered(int x, int y) {
        if (isMiniMapDebugMode()) {
            return true;
        }

        return y >= 0
                && y < discoveredMiniMapTiles.length
                && x >= 0
                && discoveredMiniMapTiles.length > 0
                && x < discoveredMiniMapTiles[y].length
                && discoveredMiniMapTiles[y][x];
    }

    public DungeonMap getDungeonMap() {
        return dungeonMap;
    }

    public void changeDungeon(GeneratedDungeon generatedDungeon) {
        if (generatedDungeon == null) {
            return;
        }

        changeDungeon(
                generatedDungeon.dungeonMap(),
                generatedDungeon.playerX(),
                generatedDungeon.playerY(),
                generatedDungeon.entities()
        );

        for (GeneratedDungeon.TileInteraction tileInteraction : generatedDungeon.tileInteractions()) {
            setTileInteractionId(tileInteraction.x(), tileInteraction.y(), tileInteraction.interactionId());
        }

        setAuthoredDialogues(generatedDungeon.authoredDialogues());
        setAuthoredQuests(generatedDungeon.authoredQuests());
        setCustomItems(generatedDungeon.customItems());
        setCustomLimbs(generatedDungeon.customLimbs());
        setCustomGatheringNodes(generatedDungeon.customGatheringNodes());
        setCustomCookingRecipes(generatedDungeon.customCookingRecipes());
        setCustomCompositeRecipes(generatedDungeon.customCompositeRecipes());
        setMapTriggers(generatedDungeon.mapTriggers());
        currentMapDesignPath = null;
    }

    public void changeDungeon(MapDesignLibrary.MapDesign mapDesign, int playerX, int playerY) {
        changeDungeon(mapDesign, playerX, playerY, null);
    }

    public void changeDungeon(MapDesignLibrary.MapDesign mapDesign, int playerX, int playerY, Path mapDesignPath) {
        if (mapDesign == null) {
            return;
        }

        changeDungeon(MapDesignLibrary.toGeneratedDungeon(mapDesign, playerX, playerY));
        currentMapDesignPath = mapDesignPath;
    }

    public void changeDungeon(MapDesignLibrary.MapDesign mapDesign) {
        changeDungeon(mapDesign, null);
    }

    public void changeDungeon(MapDesignLibrary.MapDesign mapDesign, Path mapDesignPath) {
        if (mapDesign == null) {
            return;
        }

        changeDungeon(MapDesignLibrary.toGeneratedDungeon(mapDesign));
        currentMapDesignPath = mapDesignPath;
    }

    public Path getCurrentMapDesignPath() {
        return currentMapDesignPath;
    }

    public boolean isOpenWorldActive() {
        return currentOpenWorldSession != null;
    }

    public Path getCurrentWorldManifestPath() {
        return currentOpenWorldSession == null ? null : currentOpenWorldSession.manifestPath();
    }

    public WorldManifestLibrary.WorldManifest getCurrentWorldManifest() {
        return currentOpenWorldSession == null ? null : currentOpenWorldSession.manifest();
    }

    public WorldManifestLibrary.ChunkCoordinate getCurrentChunkCoordinate() {
        return currentOpenWorldSession == null ? null : currentOpenWorldSession.center();
    }

    public int getGlobalPlayerX() {
        return currentOpenWorldSession == null ? playerX : currentOpenWorldSession.globalXForWindow(playerX);
    }

    public int getGlobalPlayerY() {
        return currentOpenWorldSession == null ? playerY : currentOpenWorldSession.globalYForWindow(playerY);
    }

    public List<EnvironmentTheme> getOpenWorldEnvironmentThemes() {
        return currentOpenWorldSession == null ? List.of() : currentOpenWorldSession.currentEnvironmentThemes();
    }

    public void clearCurrentMapDesignPath() {
        currentMapDesignPath = null;
    }

    public void captureCurrentMapState() {
        if (currentOpenWorldSession != null) {
            currentOpenWorldSession.setResumePosition(getGlobalPlayerX(), getGlobalPlayerY());
            currentOpenWorldSession.captureWindow(openWorldCapture());
            return;
        }
        if (currentMapDesignPath == null || dungeonMap == null) {
            return;
        }

        mapRuntimeStates.put(mapRuntimeKey(currentMapDesignPath), new MapRuntimeState(
                currentMapDesignPath,
                copyDungeonMap(dungeonMap),
                new ArrayList<>(entities),
                true,
                new HashMap<>(tileInteractionIds),
                new HashSet<>(removedEntityKeys),
                copyResourceNodeSnapshots(),
                getDiscoveredMiniMapTileKeys(),
                new ArrayList<>(mapTriggers),
                new HashSet<>(firedMapTriggerIds),
                new ArrayList<>(authoredDialogues.values()),
                authoredQuestDefinitions.values().stream()
                        .map(quest -> new MapDesignLibrary.AuthoredQuest(
                                quest.id(),
                                quest.displayName(),
                                quest.stageDescriptions()
                        ))
                        .toList(),
                new ArrayList<>(customItems.values()),
                new ArrayList<>(customLimbs.values()),
                new ArrayList<>(getCustomGatheringNodes()),
                new ArrayList<>(customCookingRecipes.values()),
                new ArrayList<>(customCompositeRecipes.values())
        ));
    }

    public void travelToMapLink(Path targetPath, int targetX, int targetY) throws java.io.IOException {
        captureCurrentMapState();
        if (WorldManifestLibrary.isWorldManifest(targetPath)) {
            openWorld(targetPath, targetX, targetY);
        } else {
            currentOpenWorldSession = null;
            restoreOrLoadMap(targetPath, targetX, targetY);
        }
    }

    public void restoreOrLoadMap(Path targetPath, int targetX, int targetY) throws java.io.IOException {
        if (targetPath == null) {
            throw new java.io.IOException("Target map path is missing.");
        }
        if (WorldManifestLibrary.isWorldManifest(targetPath)) {
            openWorld(targetPath, targetX, targetY);
            return;
        }

        currentOpenWorldSession = null;
        String key = mapRuntimeKey(targetPath);
        MapRuntimeState state = mapRuntimeStates.get(key);
        if (state != null && state.hasEntitySnapshot()) {
            restoreRuntimeState(state, targetPath, targetX, targetY);
            return;
        }

        MapDesignLibrary.MapDesign mapDesign = MapDesignLibrary.load(targetPath);
        changeDungeon(mapDesign, targetX, targetY, targetPath);

        state = mapRuntimeStates.get(key);
        if (state != null) {
            applySavedRuntimeState(state);
        }
    }

    public void openWorld(Path manifestPath) throws IOException {
        OpenWorldSession session = openWorldSession(manifestPath);
        openWorld(manifestPath, session.manifest().startX(), session.manifest().startY());
    }

    public void openWorld(Path manifestPath, int globalX, int globalY) throws IOException {
        if (manifestPath == null) {
            throw new IOException("World manifest path is missing.");
        }
        if (currentOpenWorldSession != null) {
            currentOpenWorldSession.captureWindow(openWorldCapture());
        }

        OpenWorldSession session = openWorldSession(manifestPath);
        currentOpenWorldSession = session;
        applyOpenWorldWindow(session.openAtGlobal(globalX, globalY), true);
    }

    public MovementCoordinates recenterOpenWorldIfNeeded(
            int previousWindowX,
            int previousWindowY,
            int nextWindowX,
            int nextWindowY
    ) throws IOException {
        if (currentOpenWorldSession == null) {
            return new MovementCoordinates(previousWindowX, previousWindowY, nextWindowX, nextWindowY, false);
        }
        int previousGlobalX = currentOpenWorldSession.globalXForWindow(previousWindowX);
        int previousGlobalY = currentOpenWorldSession.globalYForWindow(previousWindowY);
        OpenWorldSession.RecenterResult result = currentOpenWorldSession.recenterIfNeeded(
                nextWindowX,
                nextWindowY,
                openWorldCapture()
        );
        if (result == null) {
            return new MovementCoordinates(previousWindowX, previousWindowY, nextWindowX, nextWindowY, false);
        }

        applyOpenWorldWindow(result.window(), false);
        return new MovementCoordinates(
                currentOpenWorldSession.windowXForGlobal(previousGlobalX),
                currentOpenWorldSession.windowYForGlobal(previousGlobalY),
                result.window().playerX(),
                result.window().playerY(),
                true
        );
    }

    public Map<String, MapRuntimeState> getMapRuntimeStatesView() {
        if (currentOpenWorldSession == null) {
            captureCurrentMapState();
        }
        return Map.copyOf(mapRuntimeStates);
    }

    public Map<String, OpenWorldRuntimeState> getOpenWorldRuntimeStatesView() {
        if (currentOpenWorldSession != null && currentOpenWorldSession.isWindowMaterialized()) {
            int globalX = getGlobalPlayerX();
            int globalY = getGlobalPlayerY();
            currentOpenWorldSession.setResumePosition(globalX, globalY);
            currentOpenWorldSession.captureWindow(openWorldCapture());
            try {
                applyOpenWorldWindow(currentOpenWorldSession.openAtGlobal(globalX, globalY), false);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to restore open-world window after capture.", exception);
            }
        }

        Map<String, OpenWorldRuntimeState> states = new HashMap<>();
        for (Map.Entry<String, OpenWorldSession> entry : openWorldSessions.entrySet()) {
            OpenWorldSession session = entry.getValue();
            states.put(entry.getKey(), new OpenWorldRuntimeState(
                    session.manifestPath(),
                    session.resumeGlobalX(),
                    session.resumeGlobalY(),
                    session.snapshotChunkStates()
            ));
        }
        return Map.copyOf(states);
    }

    public void setOpenWorldRuntimeStates(Map<String, OpenWorldRuntimeState> states) throws IOException {
        openWorldSessions.clear();
        currentOpenWorldSession = null;
        if (states == null) {
            return;
        }
        for (Map.Entry<String, OpenWorldRuntimeState> entry : states.entrySet()) {
            OpenWorldRuntimeState saved = entry.getValue();
            if (saved == null || saved.manifestPath() == null) {
                continue;
            }
            OpenWorldSession session = OpenWorldSession.load(saved.manifestPath());
            session.restoreChunkStates(saved.chunkStates());
            session.setResumePosition(saved.resumeGlobalX(), saved.resumeGlobalY());
            openWorldSessions.put(entry.getKey(), session);
        }
    }

    private OpenWorldSession openWorldSession(Path manifestPath) throws IOException {
        String key = mapRuntimeKey(manifestPath);
        OpenWorldSession existing = openWorldSessions.get(key);
        if (existing != null) {
            return existing;
        }
        OpenWorldSession loaded = OpenWorldSession.load(manifestPath);
        openWorldSessions.put(key, loaded);
        return loaded;
    }

    private OpenWorldSession.WindowCapture openWorldCapture() {
        return new OpenWorldSession.WindowCapture(
                dungeonMap,
                new ArrayList<>(entities),
                new HashMap<>(tileInteractionIds),
                copyResourceNodeSnapshots(),
                getDiscoveredMiniMapTileKeys(),
                new HashSet<>(removedEntityKeys),
                new ArrayList<>(mapTriggers),
                new HashSet<>(firedMapTriggerIds)
        );
    }

    private void applyOpenWorldWindow(OpenWorldSession.WindowState window, boolean resetPanels) {
        dungeonMap = window.map();
        entities.clear();
        entities.addAll(window.entities());
        tileInteractionIds.clear();
        tileInteractionIds.putAll(window.tileInteractions());
        mapTriggers.clear();
        mapTriggers.addAll(window.triggers());
        firedMapTriggerIds.clear();
        firedMapTriggerIds.addAll(window.firedTriggerIds());
        removedEntityKeys.clear();
        removedEntityKeys.addAll(window.removedEntityKeys());
        currentMapDesignPath = window.centerChunkPath();
        setAuthoredDialogues(window.dialogues());
        setAuthoredQuests(window.quests());
        setCustomItems(window.items());
        setCustomLimbs(window.limbs());
        setCustomGatheringNodes(window.gatheringNodes());
        setCustomCookingRecipes(window.cookingRecipes());
        setCustomCompositeRecipes(window.compositeRecipes());
        restoreResourceNodeSnapshots(window.resourceNodeStates());
        resetMiniMapDiscovery();
        setDiscoveredMiniMapTileKeys(window.discoveredTiles());

        if (resetPanels) {
            setPlayerPosition(window.playerX(), window.playerY());
            closeInventory();
            closeSkills();
            closeQuests();
            closeStats();
            closeShop();
            closeInteraction();
            clearBattleState();
        }
    }

    public void setMapRuntimeStates(Map<String, MapRuntimeState> states) {
        mapRuntimeStates.clear();
        if (states != null) {
            mapRuntimeStates.putAll(states);
        }
    }

    public enum MiniMapMode {
        OFF,
        DISCOVERED,
        DEBUG
    }

    public enum CameraMovementMode {
        STATIC,
        FLUID
    }

    public void changeDungeon(DungeonMap dungeonMap, int playerX, int playerY, List<MapEntity> newEntities) {
        if (dungeonMap == null) {
            return;
        }

        this.dungeonMap = dungeonMap;
        currentOpenWorldSession = null;
        entities.clear();
        tileInteractionIds.clear();
        mapTriggers.clear();
        firedMapTriggerIds.clear();
        authoredDialogues.clear();
        authoredQuestDefinitions.clear();
        customItems.clear();
        customLimbs.clear();
        customGatheringNodes.clear();
        customCookingRecipes.clear();
        customCompositeRecipes.clear();
        currentMapDesignPath = null;
        resourceNodeStates.clear();
        resetMiniMapDiscovery();

        if (newEntities != null) {
            for (MapEntity entity : newEntities) {
                addEntityUnlessRemoved(entity);
            }
        }

        setPlayerPosition(playerX, playerY);
        closeInventory();
        closeSkills();
        closeQuests();
        closeStats();
        closeShop();
        closeInteraction();
        clearBattleState();
    }

    private void restoreRuntimeState(MapRuntimeState state, Path targetPath, int targetX, int targetY) {
        dungeonMap = copyDungeonMap(state.dungeonMap());
        entities.clear();
        entities.addAll(state.entities());
        tileInteractionIds.clear();
        tileInteractionIds.putAll(state.tileInteractionIds());
        mapTriggers.clear();
        mapTriggers.addAll(state.mapTriggers());
        firedMapTriggerIds.clear();
        firedMapTriggerIds.addAll(state.firedTriggerIds());
        removedEntityKeys.clear();
        removedEntityKeys.addAll(state.removedEntityKeys());
        restoreResourceNodeSnapshots(state.resourceNodeStates());
        currentMapDesignPath = targetPath;
        setAuthoredDialogues(state.authoredDialogues());
        setAuthoredQuests(state.authoredQuests());
        setCustomItems(state.customItems());
        setCustomLimbs(state.customLimbs());
        setCustomGatheringNodes(state.customGatheringNodes());
        setCustomCookingRecipes(state.customCookingRecipes());
        setCustomCompositeRecipes(state.customCompositeRecipes());
        resetMiniMapDiscovery();
        Point target = resolveTarget(targetX, targetY);
        setPlayerPosition(target.x, target.y);
        setDiscoveredMiniMapTileKeys(state.discoveredMiniMapTiles());
        revealMiniMapTile(target.x, target.y);
        closeInventory();
        closeSkills();
        closeQuests();
        closeStats();
        closeShop();
        closeInteraction();
        clearBattleState();
    }

    private void applySavedRuntimeState(MapRuntimeState state) {
        if (state.dungeonMap() != null) {
            dungeonMap = copyDungeonMap(state.dungeonMap());
        }
        Point currentPosition = new Point(playerX, playerY);
        tileInteractionIds.clear();
        tileInteractionIds.putAll(state.tileInteractionIds());
        if (!state.mapTriggers().isEmpty()) {
            mapTriggers.clear();
            mapTriggers.addAll(state.mapTriggers());
        }
        firedMapTriggerIds.clear();
        firedMapTriggerIds.addAll(state.firedTriggerIds());
        removedEntityKeys.clear();
        removedEntityKeys.addAll(state.removedEntityKeys());
        entities.removeIf(this::isEntityRemoved);
        restoreResourceNodeSnapshots(state.resourceNodeStates());
        setDiscoveredMiniMapTileKeys(state.discoveredMiniMapTiles());
        revealMiniMapTile(currentPosition.x, currentPosition.y);
    }

    private Point resolveTarget(int targetX, int targetY) {
        if (dungeonMap == null) {
            return new Point(targetX, targetY);
        }

        int clampedX = Math.max(0, Math.min(dungeonMap.getWidth() - 1, targetX));
        int clampedY = Math.max(0, Math.min(dungeonMap.getHeight() - 1, targetY));
        if (!dungeonMap.getTile(clampedX, clampedY).blocksMovement()) {
            return new Point(clampedX, clampedY);
        }

        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                if (!dungeonMap.getTile(x, y).blocksMovement()) {
                    return new Point(x, y);
                }
            }
        }

        return new Point(1, 1);
    }

    public void setTileInteractionId(int x, int y, String interactionId) {
        if (interactionId == null || interactionId.isBlank()) {
            tileInteractionIds.remove(tileKey(x, y));
            return;
        }

        tileInteractionIds.put(tileKey(x, y), interactionId);
    }

    public String getTileInteractionId(int x, int y) {
        return tileInteractionIds.get(tileKey(x, y));
    }

    public Map<String, String> getTileInteractionIdsView() {
        return Map.copyOf(tileInteractionIds);
    }

    public boolean hasTileInteractionId(int x, int y) {
        String interactionId = getTileInteractionId(x, y);
        return interactionId != null && !interactionId.isBlank();
    }

    public List<MapDesignLibrary.MapTrigger> getMapTriggersView() {
        return List.copyOf(mapTriggers);
    }

    public Set<String> getFiredMapTriggerIdsView() {
        return Set.copyOf(firedMapTriggerIds);
    }

    public void setMapTriggers(List<MapDesignLibrary.MapTrigger> triggers) {
        mapTriggers.clear();
        if (triggers == null) {
            return;
        }

        for (MapDesignLibrary.MapTrigger trigger : triggers) {
            if (trigger != null && !trigger.id().isBlank()) {
                mapTriggers.add(trigger);
            }
        }
    }

    public void setFiredMapTriggerIds(Set<String> triggerIds) {
        firedMapTriggerIds.clear();
        if (triggerIds != null) {
            firedMapTriggerIds.addAll(triggerIds);
        }
    }

    public boolean evaluateEntryTrigger(int x, int y) {
        if (dungeonMap == null || mapTriggers.isEmpty()) {
            return false;
        }

        boolean doorClosed = false;
        for (MapDesignLibrary.MapTrigger trigger : mapTriggers) {
            if (trigger == null
                    || trigger.fireMode() != MapDesignLibrary.TriggerFireMode.ON_ENTRY
                    || trigger.x() != x
                    || trigger.y() != y) {
                continue;
            }

            if (trigger.oneShot() && firedMapTriggerIds.contains(trigger.id())) {
                continue;
            }

            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                doorClosed |= runTriggerAction(action);
            }

            if (trigger.oneShot()) {
                firedMapTriggerIds.add(trigger.id());
            }
        }
        return doorClosed;
    }

    private boolean runTriggerAction(MapDesignLibrary.TriggerAction action) {
        if (action == null || action.type() != MapDesignLibrary.TriggerActionType.CLOSE_DOOR || dungeonMap == null) {
            return false;
        }

        int targetX = action.targetX();
        int targetY = action.targetY();
        if (dungeonMap.isOutOfBounds(targetX, targetY)) {
            return false;
        }

        Library.TileType tile = dungeonMap.getTile(targetX, targetY);
        if (tile == Library.TileType.DOOR_OPEN) {
            dungeonMap.setTile(targetX, targetY, Library.TileType.DOOR_CLOSED);
            return true;
        } else if (tile == Library.TileType.QUEST_DOOR_OPEN) {
            dungeonMap.setTile(targetX, targetY, Library.TileType.QUEST_DOOR_CLOSED);
            return true;
        }
        return false;
    }

    public void setAuthoredDialogues(List<MapDesignLibrary.AuthoredDialogue> dialogues) {
        authoredDialogues.clear();
        if (dialogues == null) {
            return;
        }

        for (MapDesignLibrary.AuthoredDialogue dialogue : dialogues) {
            if (dialogue != null && dialogue.interactionId() != null && !dialogue.interactionId().isBlank()) {
                authoredDialogues.put(dialogue.interactionId(), dialogue);
            }
        }
    }

    public MapDesignLibrary.AuthoredDialogue getAuthoredDialogue(String interactionId) {
        if (interactionId == null || interactionId.isBlank()) {
            return null;
        }

        return authoredDialogues.get(interactionId);
    }

    public void setAuthoredQuests(List<MapDesignLibrary.AuthoredQuest> quests) {
        authoredQuestDefinitions.clear();
        if (quests == null) {
            return;
        }

        for (MapDesignLibrary.AuthoredQuest quest : quests) {
            if (quest != null && !quest.questId().isBlank()) {
                authoredQuestDefinitions.put(quest.questId(), new QuestDefinition(
                        quest.questId(),
                        quest.displayName(),
                        quest.stageDescriptions()
                ));
            }
        }
    }

    public void setCustomItems(List<MapDesignLibrary.CustomItem> items) {
        customItems.clear();
        if (items == null) {
            return;
        }

        for (MapDesignLibrary.CustomItem item : items) {
            if (item != null && !item.itemId().isBlank()) {
                customItems.put(item.itemId(), item);
            }
        }
    }

    public List<MapDesignLibrary.CustomItem> getCustomItems() {
        return List.copyOf(customItems.values());
    }

    public void setCustomLimbs(List<MapDesignLibrary.CustomLimb> limbs) {
        customLimbs.clear();
        if (limbs == null) {
            return;
        }

        for (MapDesignLibrary.CustomLimb limb : limbs) {
            if (limb != null && !limb.limbId().isBlank()) {
                customLimbs.put(limb.limbId(), limb);
            }
        }
    }

    public List<MapDesignLibrary.CustomLimb> getCustomLimbs() {
        return List.copyOf(customLimbs.values());
    }

    public void setCustomGatheringNodes(List<MapDesignLibrary.CustomGatheringNode> nodes) {
        customGatheringNodes.clear();
        if (nodes == null) {
            return;
        }

        for (MapDesignLibrary.CustomGatheringNode node : nodes) {
            if (node != null && !node.nodeId().isBlank()) {
                customGatheringNodes.put(node.nodeId(), node);
                customGatheringNodes.put(node.interactionId(), node);
            }
        }
    }

    public List<MapDesignLibrary.CustomGatheringNode> getCustomGatheringNodes() {
        return customGatheringNodes.values().stream().distinct().toList();
    }

    public void setCustomCookingRecipes(List<MapDesignLibrary.CustomCookingRecipe> recipes) {
        customCookingRecipes.clear();
        if (recipes == null) {
            return;
        }

        for (MapDesignLibrary.CustomCookingRecipe recipe : recipes) {
            if (recipe != null && !recipe.recipeId().isBlank()) {
                customCookingRecipes.put(recipe.recipeId(), recipe);
            }
        }
    }

    public List<MapDesignLibrary.CustomCookingRecipe> getCustomCookingRecipes() {
        return List.copyOf(customCookingRecipes.values());
    }

    public void setCustomCompositeRecipes(List<MapDesignLibrary.CustomCompositeRecipe> recipes) {
        customCompositeRecipes.clear();
        if (recipes == null) {
            return;
        }

        for (MapDesignLibrary.CustomCompositeRecipe recipe : recipes) {
            if (recipe != null && !recipe.recipeId().isBlank()) {
                customCompositeRecipes.put(recipe.recipeId(), recipe);
            }
        }
    }

    public InventorySystem.Item createCustomItem(String itemId) {
        MapDesignLibrary.CustomItem item = customItems.get(itemId);
        return item == null ? null : item.createItem();
    }

    public InventorySystem.Item createItemByNameOrId(String itemNameOrId) {
        if (itemNameOrId == null || itemNameOrId.isBlank()) {
            return null;
        }
        InventorySystem.Item customItem = createCustomItemByNameOrId(itemNameOrId);
        if (customItem != null) {
            return customItem;
        }

        try {
            MapDesignLibrary.AuthoredContent sharedContent = MapDesignLibrary.loadSharedContent();
            for (MapDesignLibrary.CustomItem sharedItem : sharedContent.customItems()) {
                if (itemNameOrId.equalsIgnoreCase(sharedItem.itemId())
                        || itemNameOrId.equalsIgnoreCase(sharedItem.displayName())) {
                    return sharedItem.createItem();
                }
            }
            for (MapDesignLibrary.CustomLimb sharedLimb : sharedContent.customLimbs()) {
                if (itemNameOrId.equalsIgnoreCase(sharedLimb.limbId())
                        || itemNameOrId.equalsIgnoreCase(sharedLimb.displayName())) {
                    return sharedLimb.createLimb();
                }
            }
        } catch (java.io.IOException ignored) {
            // Fall back to enum-backed items below.
        }

        for (ItemLibrary libraryItem : ItemLibrary.values()) {
            if (itemNameOrId.equalsIgnoreCase(libraryItem.name())
                    || itemNameOrId.equalsIgnoreCase(libraryItem.getDisplayName())) {
                return libraryItem.createItem();
            }
        }

        return null;
    }

    public InventorySystem.Item createCustomItemByNameOrId(String itemNameOrId) {
        if (itemNameOrId == null || itemNameOrId.isBlank()) {
            return null;
        }
        MapDesignLibrary.CustomItem item = customItems.get(itemNameOrId);
        if (item != null) {
            return item.createItem();
        }
        MapDesignLibrary.CustomLimb limb = customLimbs.get(itemNameOrId);
        if (limb != null) {
            return limb.createLimb();
        }
        for (MapDesignLibrary.CustomItem customItem : customItems.values()) {
            if (itemNameOrId.equalsIgnoreCase(customItem.displayName())) {
                return customItem.createItem();
            }
        }
        for (MapDesignLibrary.CustomLimb customLimb : customLimbs.values()) {
            if (itemNameOrId.equalsIgnoreCase(customLimb.displayName())) {
                return customLimb.createLimb();
            }
        }
        return null;
    }

    public boolean hasSpokenToAuthoredDialogue(String interactionId) {
        return interactionId != null && spokenAuthoredDialogues.contains(interactionId);
    }

    public void markSpokenToAuthoredDialogue(String interactionId) {
        if (interactionId != null && !interactionId.isBlank()) {
            spokenAuthoredDialogues.add(interactionId);
        }
    }

    public Set<String> getSpokenAuthoredDialogueIdsView() {
        return Set.copyOf(spokenAuthoredDialogues);
    }

    public void setSpokenAuthoredDialogueIds(Set<String> interactionIds) {
        spokenAuthoredDialogues.clear();
        if (interactionIds != null) {
            for (String interactionId : interactionIds) {
                if (interactionId != null && !interactionId.isBlank()) {
                    spokenAuthoredDialogues.add(interactionId);
                }
            }
        }
    }

    private String tileKey(int x, int y) {
        return x + "," + y;
    }

    public InputBindings getInputBindings() {
        return inputBindings;
    }

    public List<MapEntity> getEntities() {
        return entities;
    }

    public void addEntity(MapEntity entity) {
        if (entity != null) {
            entities.add(entity);
        }
    }

    public void addEntityUnlessRemoved(MapEntity entity) {
        if (entity != null && !isEntityRemoved(entity)) {
            addEntity(entity);
        }
    }

    public void removeEntity(MapEntity entity) {
        if (entity != null && entities.remove(entity)) {
            removedEntityKeys.add(entityKey(entity));
        }
    }

    public boolean isEntityRemoved(MapEntity entity) {
        return entity != null && removedEntityKeys.contains(entityKey(entity));
    }

    public Set<String> getRemovedEntityKeysView() {
        return Set.copyOf(removedEntityKeys);
    }

    public void setRemovedEntityKeys(Set<String> removedEntityKeys) {
        this.removedEntityKeys.clear();

        if (removedEntityKeys != null) {
            this.removedEntityKeys.addAll(removedEntityKeys);
        }
    }

    private String entityKey(MapEntity entity) {
        return entity.getName()
                + "|"
                + entity.getType()
                + "|"
                + entity.getX()
                + "|"
                + entity.getY();
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public boolean isDungeonMode() {
        return gameMode == GameMode.DUNGEON;
    }

    public boolean isBattleMode() {
        return gameMode == GameMode.BATTLE;
    }

    public boolean isStartMenuMode() {
        return gameMode == GameMode.START_MENU;
    }

    public boolean isCharacterCreationMode() {
        return gameMode == GameMode.CHARACTER_CREATION;
    }

    public boolean isGameOverMode() {
        return gameMode == GameMode.GAME_OVER;
    }

    public int getPlayerX() {
        return playerX;
    }

    public void setPlayerX(int playerX) {
        this.playerX = playerX;
        revealMiniMapTile(this.playerX, this.playerY);
    }

    public int getPlayerY() {
        return playerY;
    }

    public void setPlayerY(int playerY) {
        this.playerY = playerY;
        revealMiniMapTile(this.playerX, this.playerY);
    }

    public void setPlayerPosition(int playerX, int playerY) {
        this.playerX = playerX;
        this.playerY = playerY;
        revealMiniMapTile(playerX, playerY);
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = Math.max(1, currentFloor);
    }

    public void startMovementAnimation(int fromX, int fromY, int toX, int toY) {
        if (!isFluidCameraMovement()) {
            movementProgress = 1.0;
            return;
        }

        movementStartX = fromX;
        movementStartY = fromY;
        movementProgress = fromX == toX && fromY == toY ? 1.0 : 0.0;
    }

    public void updateMovementAnimation(int deltaMs) {
        if (!isFluidCameraMovement()) {
            movementProgress = 1.0;
            rotationProgress = 1.0;
            return;
        }

        if (isMovementAnimating()) {
            movementProgress = Math.min(
                    1.0,
                    movementProgress + (double) Math.max(0, deltaMs) / movementAnimationDurationMs()
            );
        }

        if (isRotationAnimating()) {
            rotationProgress = Math.min(
                    1.0,
                    rotationProgress + (double) Math.max(0, deltaMs) / rotationAnimationDurationMs()
            );
        }
    }

    public boolean isMovementAnimating() {
        return movementProgress < 1.0;
    }

    public boolean isRotationAnimating() {
        return rotationProgress < 1.0;
    }

    public boolean isCameraAnimating() {
        return isFluidCameraMovement() && (isMovementAnimating() || isRotationAnimating());
    }

    public double getCameraOffsetForward() {
        if (!isFluidCameraMovement()) {
            return 0.0;
        }

        double renderX = interpolate(movementStartX, playerX, movementProgress);
        double renderY = interpolate(movementStartY, playerY, movementProgress);
        double offsetX = renderX - playerX;
        double offsetY = renderY - playerY;
        return offsetX * forwardX() + offsetY * forwardY();
    }

    public double getCameraOffsetSide() {
        if (!isFluidCameraMovement()) {
            return 0.0;
        }

        double renderX = interpolate(movementStartX, playerX, movementProgress);
        double renderY = interpolate(movementStartY, playerY, movementProgress);
        double offsetX = renderX - playerX;
        double offsetY = renderY - playerY;
        return offsetX * rightX() + offsetY * rightY();
    }

    public double getCameraRotationRadians() {
        if (!isFluidCameraMovement()) {
            return 0.0;
        }

        return interpolate(rotationStartOffsetRadians, 0.0, rotationProgress);
    }

    private double interpolate(double start, double end, double progress) {
        double easedProgress = 1.0 - Math.pow(1.0 - progress, 3.0);
        return start + (end - start) * easedProgress;
    }

    private int forwardX() {
        return switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private int forwardY() {
        return switch (direction) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }

    private int rightX() {
        return switch (direction) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
    }

    private int rightY() {
        return switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private void resetMiniMapDiscovery() {
        if (dungeonMap == null) {
            discoveredMiniMapTiles = new boolean[0][0];
            return;
        }

        discoveredMiniMapTiles = new boolean[dungeonMap.getHeight()][dungeonMap.getWidth()];
    }

    private void revealMiniMapTile(int centerX, int centerY) {
        if (discoveredMiniMapTiles.length == 0) {
            return;
        }

        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (y >= 0
                        && y < discoveredMiniMapTiles.length
                        && x >= 0
                        && x < discoveredMiniMapTiles[y].length) {
                    discoveredMiniMapTiles[y][x] = true;
                }
            }
        }
    }

    public Set<String> getDiscoveredMiniMapTileKeys() {
        Set<String> discovered = new HashSet<>();

        for (int y = 0; y < discoveredMiniMapTiles.length; y++) {
            for (int x = 0; x < discoveredMiniMapTiles[y].length; x++) {
                if (discoveredMiniMapTiles[y][x]) {
                    discovered.add(tileKey(x, y));
                }
            }
        }

        return discovered;
    }

    public void setDiscoveredMiniMapTileKeys(Set<String> discoveredTileKeys) {
        resetMiniMapDiscovery();

        if (discoveredTileKeys == null) {
            revealMiniMapTile(playerX, playerY);
            return;
        }

        for (String key : discoveredTileKeys) {
            String[] parts = key.split(",");

            if (parts.length != 2) {
                continue;
            }

            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (y >= 0
                        && y < discoveredMiniMapTiles.length
                        && x >= 0
                        && x < discoveredMiniMapTiles[y].length) {
                    discoveredMiniMapTiles[y][x] = true;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed save entries.
            }
        }
    }

    public int getQuestStage(QuestLibrary quest) {
        return quest == null ? 0 : questStages.getOrDefault(quest.getId(), 0);
    }

    public void setQuestStage(QuestLibrary quest, int stage) {
        if (quest != null) {
            questStages.put(quest.getId(), Math.max(0, Math.min(quest.getMaxStage(), stage)));
        }
    }

    public Map<String, Integer> getQuestStagesView() {
        return Map.copyOf(questStages);
    }

    public List<QuestDefinition> getQuestDefinitions() {
        List<QuestDefinition> definitions = new ArrayList<>();
        for (QuestLibrary quest : QuestLibrary.values()) {
            List<String> stages = new ArrayList<>();
            for (int stage = 0; stage <= quest.getMaxStage(); stage++) {
                stages.add(quest.getStageDescription(stage));
            }
            definitions.add(new QuestDefinition(quest.getId(), quest.getDisplayName(), stages));
        }
        definitions.addAll(authoredQuestDefinitions.values());
        return definitions;
    }

    public QuestDefinition getQuestDefinition(String questId) {
        if (questId == null || questId.isBlank()) {
            return null;
        }

        for (QuestDefinition definition : getQuestDefinitions()) {
            if (questId.equals(definition.id())) {
                return definition;
            }
        }

        return null;
    }

    public void setQuestStages(Map<String, Integer> questStages) {
        this.questStages.clear();

        if (questStages != null) {
            questStages.forEach((id, stage) -> {
                if (id != null && stage != null) {
                    this.questStages.put(id, Math.max(0, stage));
                }
            });
        }
    }

    public void setQuestStage(String questId, int stage) {
        if (questId == null || questId.isBlank()) {
            return;
        }

        QuestDefinition definition = getQuestDefinition(questId);
        int safeStage = definition == null
                ? Math.max(0, stage)
                : Math.max(0, Math.min(definition.maxStage(), stage));
        questStages.put(questId, safeStage);
    }

    public int getGold() {
        return getInventory().countItemNamed(ItemLibrary.GOLD.getDisplayName());
    }

    public void addGold(int amount) {
        if (amount <= 0) {
            return;
        }

        getInventory().addItem(ItemLibrary.createGold(amount));
    }

    public boolean canSpendGold(int amount) {
        return amount >= 0 && getGold() >= amount;
    }

    public boolean spendGold(int amount) {
        if (!canSpendGold(amount)) {
            return false;
        }

        return amount == 0 || getInventory().removeItemQuantityNamed(ItemLibrary.GOLD.getDisplayName(), amount);
    }

    public ShopSystem.ShopSession getActiveShop() {
        return activeShop;
    }

    public boolean hasActiveShop() {
        return activeShop != null;
    }

    public void openShop(ShopSystem.ShopSession shopSession) {
        activeShop = shopSession;

        closeInventory();
        closeSkills();

        if (activeInteraction != null) {
            activeInteraction.close();
        }

        activeInteraction = null;
    }

    public void closeShop() {
        activeShop = null;
    }

    public boolean startFishing(int x, int y) {
        stopCooking();
        stopMining();
        MapDesignLibrary.CustomGatheringNode node = getCustomGatheringNodeAt(x, y);
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.FISHING : node.gatheringSkill();
        if (node != null && playerCharacter != null
                && playerCharacter.getSkillLevel(gatheringSkill) < node.requiredLevel()) {
            fishingMessage = "You need " + gatheringSkill.getDisplayName() + " level " + node.requiredLevel() + " to gather from " + node.displayName() + ".";
            return false;
        }

        if (isResourceExhausted(x, y)) {
            fishingMessage = "The shoal is quiet. Give it time to recover.";
            return false;
        }

        fishingActive = true;
        fishingX = x;
        fishingY = y;
        fishingElapsedMs = 0;
        fishingInteractionId = node == null ? "" : node.interactionId();
        fishingMessage = "You cast your line into the " + (node == null ? "shoal" : node.displayName()) + ".";
        return true;
    }

    public void stopFishing() {
        fishingActive = false;
        fishingX = -1;
        fishingY = -1;
        fishingElapsedMs = 0;
        fishingInteractionId = "";
    }

    public boolean isFishingActive() {
        return fishingActive;
    }

    public boolean isFishingAt(int x, int y) {
        return fishingActive && fishingX == x && fishingY == y;
    }

    public void updateFishing(int deltaMs) {
        if (!fishingActive || playerCharacter == null) {
            return;
        }

        fishingElapsedMs += Math.max(0, deltaMs);

        if (fishingElapsedMs < gatheringAttemptIntervalMs()) {
            return;
        }

        fishingElapsedMs = 0;

        if (isResourceExhausted(fishingX, fishingY)) {
            fishingMessage = "The shoal is quiet. Give it time to recover.";
            stopFishing();
            return;
        }

        recordResourceAttempt(fishingX, fishingY);

        if (isResourceExhausted(fishingX, fishingY)) {
            fishingMessage = "The shoal goes still. It needs time to recover.";
            stopFishing();
            return;
        }

        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(fishingInteractionId);
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.FISHING : node.gatheringSkill();
        int fishingLevel = Math.max(1, playerCharacter.getSkillLevel(gatheringSkill));
        double successChance = Math.min(
                maxFishingSuccessChance(),
                baseFishingSuccessChance() + fishingLevel * fishingSuccessChancePerLevel()
        );

        InventorySystem.Item gatheredItem = node == null
                ? org.main.content.ItemLibrary.RAW_FISH.createItem()
                : createGatheredItem(node);
        int xpReward = node == null ? fishingXpReward() : node.gatherXpReward();
        String outputName = gatheredItem == null ? "fish" : gatheredItem.getName();

        if (Math.random() <= successChance && gatheredItem != null && getInventory().addItem(gatheredItem)) {
            int levelsGained = playerCharacter.addSkillExperience(gatheringSkill, xpReward);
            advanceQuestIfAt(QuestLibrary.GATHERING_BASICS, 0, 1);
            fishingMessage = levelsGained > 0
                    ? "You catch " + outputName + ". " + gatheringSkill.getDisplayName() + " level " + playerCharacter.getSkillLevel(gatheringSkill) + "!"
                    : "You catch " + outputName + ". " + gatheringSkill.getDisplayName() + " XP "
                    + playerCharacter.getSkillExperience(gatheringSkill)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(gatheringSkill)
                    + ".";
            return;
        }

        if (!getInventory().hasFreeSlot()) {
            fishingMessage = "Your inventory is too full to hold any fish.";
            return;
        }

        fishingMessage = "The fish slip away.";
    }

    public String getFishingMessage() {
        return fishingMessage;
    }

    public boolean startMining(int x, int y) {
        stopFishing();
        stopCooking();
        stopSmelting();
        MapDesignLibrary.CustomGatheringNode node = getCustomGatheringNodeAt(x, y);
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.MINING : node.gatheringSkill();
        if (node != null && playerCharacter != null
                && playerCharacter.getSkillLevel(gatheringSkill) < node.requiredLevel()) {
            miningMessage = "You need " + gatheringSkill.getDisplayName() + " level " + node.requiredLevel() + " to gather from " + node.displayName() + ".";
            return false;
        }

        if (isResourceExhausted(x, y)) {
            miningMessage = "The rock is depleted. Give it time to recover.";
            return false;
        }

        miningActive = true;
        miningX = x;
        miningY = y;
        miningElapsedMs = 0;
        miningInteractionId = node == null ? "" : node.interactionId();
        miningMessage = "You swing at the " + (node == null ? "mineral rock" : node.displayName()) + ".";
        return true;
    }

    public void stopMining() {
        miningActive = false;
        miningX = -1;
        miningY = -1;
        miningElapsedMs = 0;
        miningInteractionId = "";
    }

    public boolean isMiningActive() {
        return miningActive;
    }

    public boolean isMiningAt(int x, int y) {
        return miningActive && miningX == x && miningY == y;
    }

    public void updateMining(int deltaMs) {
        if (!miningActive || playerCharacter == null) {
            return;
        }

        miningElapsedMs += Math.max(0, deltaMs);

        if (miningElapsedMs < gatheringAttemptIntervalMs()) {
            return;
        }

        miningElapsedMs = 0;

        if (isResourceExhausted(miningX, miningY)) {
            miningMessage = "The rock is depleted. Give it time to recover.";
            stopMining();
            return;
        }

        recordResourceAttempt(miningX, miningY);

        if (isResourceExhausted(miningX, miningY)) {
            miningMessage = "The rock crumbles down to bare stone. It needs time to recover.";
            stopMining();
            return;
        }

        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(miningInteractionId);
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.MINING : node.gatheringSkill();
        int miningLevel = Math.max(1, playerCharacter.getSkillLevel(gatheringSkill));
        double successChance = Math.min(
                maxMiningSuccessChance(),
                baseMiningSuccessChance() + miningLevel * miningSuccessChancePerLevel()
        );

        InventorySystem.Item gatheredItem = node == null
                ? ItemLibrary.COPPER_ORE.createItem()
                : createGatheredItem(node);
        int xpReward = node == null ? miningXpReward() : node.gatherXpReward();
        String outputName = gatheredItem == null ? "ore" : gatheredItem.getName();

        if (Math.random() <= successChance && gatheredItem != null && getInventory().addItem(gatheredItem)) {
            int levelsGained = playerCharacter.addSkillExperience(gatheringSkill, xpReward);
            advanceQuestIfAt(QuestLibrary.GATHERING_BASICS, 2, 3);
            miningMessage = levelsGained > 0
                    ? "You gather " + outputName + ". " + gatheringSkill.getDisplayName() + " level " + playerCharacter.getSkillLevel(gatheringSkill) + "!"
                    : "You gather " + outputName + ". " + gatheringSkill.getDisplayName() + " XP "
                    + playerCharacter.getSkillExperience(gatheringSkill)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(gatheringSkill)
                    + ".";
            return;
        }

        if (!getInventory().hasFreeSlot()) {
            miningMessage = "Your inventory is too full to hold any ore.";
            return;
        }

        miningMessage = "You chip the rock, but no usable ore breaks free.";
    }

    public String getMiningMessage() {
        return miningMessage;
    }

    private MapDesignLibrary.CustomGatheringNode getCustomGatheringNodeAt(int x, int y) {
        MapEntity entity = getEntityAt(x, y);
        if (entity != null && entity.getInteractionId() != null) {
            MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(entity.getInteractionId());
            if (node != null) {
                return node;
            }
        }

        String tileInteractionId = getTileInteractionId(x, y);
        return tileInteractionId == null ? null : customGatheringNodes.get(tileInteractionId);
    }

    private InventorySystem.Item createGatheredItem(MapDesignLibrary.CustomGatheringNode node) {
        if (node == null) {
            return null;
        }

        String itemId = chooseGatheringLootItemId(node);
        if (itemId.isBlank()) {
            return null;
        }

        return createItemByNameOrId(itemId);
    }

    private String chooseGatheringLootItemId(MapDesignLibrary.CustomGatheringNode node) {
        List<MapDesignLibrary.CustomDropEntry> lootEntries = node.lootEntries();
        if (lootEntries.isEmpty()) {
            return node.outputItemId();
        }

        double totalWeight = 0.0;
        for (MapDesignLibrary.CustomDropEntry entry : lootEntries) {
            totalWeight += Math.max(0.0, entry.chance());
        }

        if (totalWeight <= 0.0) {
            return "";
        }

        double roll = Math.random() * totalWeight;
        double cursor = 0.0;
        for (MapDesignLibrary.CustomDropEntry entry : lootEntries) {
            cursor += Math.max(0.0, entry.chance());
            if (roll <= cursor) {
                return entry.itemId();
            }
        }

        return lootEntries.get(lootEntries.size() - 1).itemId();
    }

    public void updateResourceNodes(int deltaMs) {
        if (resourceNodeStates.isEmpty()) {
            return;
        }

        int safeDelta = Math.max(0, deltaMs);

        for (Map.Entry<String, ResourceNodeState> entry : resourceNodeStates.entrySet()) {
            ResourceNodeState state = entry.getValue();

            if (state.respawnRemainingMs <= 0) {
                continue;
            }

            state.respawnRemainingMs = Math.max(0, state.respawnRemainingMs - safeDelta);

            if (state.respawnRemainingMs == 0) {
                state.exhaustionLevel = 0;
                state.attemptsSinceLastExhaustionRoll = 0;
                restoreResourceNode(entry.getKey());
            }
        }
    }

    public int getResourceExhaustionLevel(int x, int y) {
        return getResourceNodeState(x, y).exhaustionLevel;
    }

    private void recordResourceAttempt(int x, int y) {
        ResourceNodeState state = getResourceNodeState(x, y);

        if (state.exhaustionLevel >= maxResourceExhaustionLevel()) {
            return;
        }

        state.attemptsSinceLastExhaustionRoll++;

        if (state.attemptsSinceLastExhaustionRoll < resourceAttemptsPerExhaustionRoll()) {
            return;
        }

        state.attemptsSinceLastExhaustionRoll = 0;

        if (Math.random() <= resourceExhaustionChance()) {
            state.exhaustionLevel = Math.min(maxResourceExhaustionLevel(), state.exhaustionLevel + 1);
            updateResourceNodeVisual(x, y);

            if (state.exhaustionLevel >= maxResourceExhaustionLevel()) {
                state.respawnRemainingMs = resourceRespawnMs();
            }
        }
    }

    private boolean isResourceExhausted(int x, int y) {
        return getResourceNodeState(x, y).exhaustionLevel >= maxResourceExhaustionLevel();
    }

    private ResourceNodeState getResourceNodeState(int x, int y) {
        return resourceNodeStates.computeIfAbsent(tileKey(x, y), ignored -> new ResourceNodeState());
    }

    private void restoreResourceNode(String key) {
        Point point = parseTileKey(key);

        if (point == null) {
            return;
        }

        updateResourceNodeVisual(point.x, point.y);
    }

    private void updateResourceNodeVisual(int x, int y) {
        int exhaustionLevel = getResourceExhaustionLevel(x, y);
        MapEntity entity = getEntityAt(x, y);

        if (entity != null && GatheringNodeLibrary.MINERAL_ROCK_A.getInteractionId().equals(entity.getInteractionId())) {
            entity.setStaticImage(GatheringNodeLibrary.MINERAL_ROCK_A.getImageForExhaustion(exhaustionLevel));
            return;
        }

        if (entity != null) {
            MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(entity.getInteractionId());
            if (node != null && node.nodeType() != MapDesignLibrary.GatheringNodeType.FISHING_SPOT) {
                entity.setStaticImage(node.getImageForExhaustion(exhaustionLevel));
                return;
            }
        }

        if (GatheringNodeLibrary.FISHING_SHOAL.getInteractionId().equals(getTileInteractionId(x, y))
                && dungeonMap != null) {
            dungeonMap.setTile(x, y, exhaustionLevel >= 2 ? Library.TileType.WATER : Library.TileType.FISHING_WATER);
        }

        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(getTileInteractionId(x, y));
        if (node != null && node.nodeType() == MapDesignLibrary.GatheringNodeType.FISHING_SPOT && dungeonMap != null) {
            dungeonMap.setTile(x, y, exhaustionLevel >= 2 ? Library.TileType.WATER : Library.TileType.FISHING_WATER);
        }
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
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MapEntity getEntityAt(int x, int y) {
        for (MapEntity entity : entities) {
            if (entity.isAt(x, y)) {
                return entity;
            }
        }

        return null;
    }

    public void selectWorldUseItem(int inventoryIndex) {
        selectedWorldItemIndex = inventoryIndex;
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        selectedWorldItem = item;
        selectedWorldItemName = item == null ? null : item.getName();
    }

    public boolean useInventoryItemForWorld(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        if (item == null) {
            return false;
        }

        selectWorldUseItem(inventoryIndex);
        return true;
    }

    public boolean equipInventoryItem(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        if (item == null || !item.isEquippable()) {
            return false;
        }

        InventorySystem.EquipmentSlot slot = preferredEquipmentSlot(item);
        if (slot == null || !playerCharacter.canUseEquipment(item, slot)) {
            return false;
        }

        return getInventory().equipFromInventory(inventoryIndex, slot);
    }

    public boolean unequipInventoryItem(InventorySystem.EquipmentSlot slot) {
        return slot != null && getInventory().unequipToInventory(slot);
    }

    public boolean graftInventoryLimb(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        if (!(item instanceof LimbItem limb)) {
            return false;
        }

        int indexToRemove = inventoryIndex;
        openInteraction(InteractionSystem.graftMenu(
                this,
                limb,
                () -> getInventory().removeItem(indexToRemove)
        ));
        return true;
    }

    public boolean dropInventoryItem(int inventoryIndex) {
        InventorySystem.Item item = getInventory().removeItem(inventoryIndex);
        if (item == null) {
            return false;
        }

        int dropX = playerX + forwardX(direction);
        int dropY = playerY + forwardY(direction);

        if (dungeonMap == null || !dungeonMap.isWalkable(dropX, dropY)) {
            dropX = playerX;
            dropY = playerY;
        }

        addEntity(new MapEntity(item, dropX, dropY));
        return true;
    }

    public String getInventoryExamineTitle(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        return item == null ? "Examine" : item.getName();
    }

    public String getInventoryExamineText(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        if (item instanceof LimbItem limb) {
            return limbExamineText(limb);
        }

        return item == null ? "There is nothing to examine." : item.getExamineText();
    }

    private String limbExamineText(LimbItem limb) {
        StringBuilder builder = new StringBuilder();

        if (limb.getExamineText() != null && !limb.getExamineText().isBlank()) {
            builder.append(limb.getExamineText().trim()).append("\n\n");
        }

        builder.append(limb.getName())
                .append("\nSource: ")
                .append(limb.getSourceCreatureName() == null || limb.getSourceCreatureName().isBlank()
                        ? "Unknown creature"
                        : limb.getSourceCreatureName())
                .append("\nCondition: ")
                .append(limb.getCondition().getDisplayName())
                .append("\nSlot: ")
                .append(limb.getLimbSlot().getDisplayName());

        LimbItem currentLimb = playerCharacter == null ? null : playerCharacter.getEquippedLimb(limb.getLimbSlot());
        builder.append("\n\nStats");
        for (Map.Entry<PlayerStat, Integer> entry : limb.getBaseStatsView().entrySet()) {
            int value = limb.getEffectiveStat(entry.getKey());
            int currentValue = currentLimb == null ? 0 : currentLimb.getEffectiveStat(entry.getKey());
            int delta = value - currentValue;
            builder.append("\n")
                    .append(entry.getKey().getDisplayName())
                    .append(" ")
                    .append(value)
                    .append(delta == 0 ? "" : " (" + (delta > 0 ? "+" : "") + delta + ")");
        }

        if (!limb.getSkills().isEmpty()) {
            builder.append("\n\nAbilities");
            for (BattleSkill skill : limb.getSkills()) {
                builder.append("\n+ ").append(skill.getName());
            }
        }

        return builder.toString();
    }

    private InventorySystem.EquipmentSlot preferredEquipmentSlot(InventorySystem.Item item) {
        if (item == null) {
            return null;
        }

        return switch (item.getItemType()) {
            case HEAD_GEAR -> InventorySystem.EquipmentSlot.HEAD;
            case CHEST_ARMOR -> InventorySystem.EquipmentSlot.CHEST;
            case LEG_ARMOR -> InventorySystem.EquipmentSlot.LEGS;
            case WEAPON -> InventorySystem.EquipmentSlot.WEAPON;
            case SHIELD -> InventorySystem.EquipmentSlot.SHIELD;
            case RING -> getInventory().getEquippedItem(InventorySystem.EquipmentSlot.RING_LEFT) == null
                    ? InventorySystem.EquipmentSlot.RING_LEFT
                    : InventorySystem.EquipmentSlot.RING_RIGHT;
            default -> null;
        };
    }

    private int forwardX(int direction) {
        return switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private int forwardY(int direction) {
        return switch (direction) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }

    public InventorySystem.Item getSelectedWorldUseItem() {
        if (selectedWorldItemIndex < 0) {
            return null;
        }

        InventorySystem.Item item = getInventory().getItem(selectedWorldItemIndex);
        if (item == selectedWorldItem) {
            return item;
        }

        if (selectedWorldItem != null && inventoryContains(selectedWorldItem)) {
            return selectedWorldItem;
        }

        if (item == null || selectedWorldItemName == null || !selectedWorldItemName.equals(item.getName())) {
            clearSelectedWorldUseItem();
            return null;
        }

        return item;
    }

    public boolean hasSelectedWorldUseItem() {
        return getSelectedWorldUseItem() != null;
    }

    public boolean tryUseSelectedWorldItemOnInventoryItem(int targetInventoryIndex) {
        InventorySystem.Item selectedItem = getSelectedWorldUseItem();
        InventorySystem.Item targetItem = getInventory().getItem(targetInventoryIndex);
        if (selectedItem == null
                || targetItem == null
                || selectedWorldItemIndex == targetInventoryIndex) {
            return false;
        }

        MapDesignLibrary.CustomCompositeRecipe recipe = findCompositeRecipe(selectedItem, targetItem);
        if (recipe == null) {
            return false;
        }

        if (playerCharacter.getSkillLevel(recipe.requiredSkill()) < recipe.requiredLevel()) {
            openInteraction(InteractionSystem.prompt(
                    "Not Ready",
                    "You need " + recipe.requiredSkill().getDisplayName() + " level " + recipe.requiredLevel() + " to make " + recipe.displayName() + ".",
                    InteractionSystem.closeOption("Close")
            ));
            return true;
        }

        InventorySystem.Item output = createItemByNameOrId(recipe.outputItemId());
        if (output == null) {
            openInteraction(InteractionSystem.prompt(
                    "Recipe Failed",
                    "The output item for " + recipe.displayName() + " could not be found.",
                    InteractionSystem.closeOption("Close")
            ));
            return true;
        }

        boolean selectedMatchesPrimary = recipeItemMatches(recipe.primaryItemId(), selectedItem);
        boolean selectedMatchesSecondary = recipeItemMatches(recipe.secondaryItemId(), selectedItem);
        boolean targetMatchesPrimary = recipeItemMatches(recipe.primaryItemId(), targetItem);
        boolean targetMatchesSecondary = recipeItemMatches(recipe.secondaryItemId(), targetItem);

        if (recipe.consumePrimary()) {
            InventorySystem.Item itemToConsume = selectedMatchesPrimary ? selectedItem : targetMatchesPrimary ? targetItem : null;
            if (itemToConsume != null && !getInventory().removeFirstItemNamed(itemToConsume.getName())) {
                return true;
            }
        }
        if (recipe.consumeSecondary()) {
            InventorySystem.Item itemToConsume = selectedMatchesSecondary ? selectedItem : targetMatchesSecondary ? targetItem : null;
            if (itemToConsume != null && !getInventory().removeFirstItemNamed(itemToConsume.getName())) {
                return true;
            }
        }

        clearSelectedWorldUseItem();
        if (!getInventory().addItem(output)) {
            openInteraction(InteractionSystem.prompt(
                    "Inventory Full",
                    "You made " + output.getName() + ", but your inventory is too full to hold it.",
                    InteractionSystem.closeOption("Close")
            ));
            return true;
        }

        int levelsGained = playerCharacter.addSkillExperience(recipe.requiredSkill(), recipe.xpReward());
        String message = levelsGained > 0
                ? "You make " + output.getName() + ". " + recipe.requiredSkill().getDisplayName()
                + " level " + playerCharacter.getSkillLevel(recipe.requiredSkill()) + "!"
                : "You make " + output.getName() + ".";
        openInteraction(InteractionSystem.prompt(
                recipe.displayName(),
                message,
                InteractionSystem.closeOption("Close")
        ));
        return true;
    }

    private MapDesignLibrary.CustomCompositeRecipe findCompositeRecipe(
            InventorySystem.Item first,
            InventorySystem.Item second
    ) {
        for (MapDesignLibrary.CustomCompositeRecipe recipe : customCompositeRecipes.values()) {
            if ((recipeItemMatches(recipe.primaryItemId(), first) && recipeItemMatches(recipe.secondaryItemId(), second))
                    || (recipeItemMatches(recipe.primaryItemId(), second) && recipeItemMatches(recipe.secondaryItemId(), first))) {
                return recipe;
            }
        }

        try {
            for (MapDesignLibrary.CustomCompositeRecipe recipe : MapDesignLibrary.loadSharedContent().customCompositeRecipes()) {
                if ((recipeItemMatches(recipe.primaryItemId(), first) && recipeItemMatches(recipe.secondaryItemId(), second))
                        || (recipeItemMatches(recipe.primaryItemId(), second) && recipeItemMatches(recipe.secondaryItemId(), first))) {
                    return recipe;
                }
            }
        } catch (java.io.IOException ignored) {
            return null;
        }
        return null;
    }

    private boolean recipeItemMatches(String configuredItemId, InventorySystem.Item item) {
        if (configuredItemId == null || configuredItemId.isBlank() || item == null) {
            return false;
        }

        if (configuredItemId.equalsIgnoreCase(item.getName())) {
            return true;
        }

        InventorySystem.Item configuredItem = createItemByNameOrId(configuredItemId);
        return configuredItem != null && configuredItem.getName().equalsIgnoreCase(item.getName());
    }

    private boolean inventoryContains(InventorySystem.Item targetItem) {
        for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
            if (getInventory().getItem(i) == targetItem) {
                return true;
            }
        }

        return false;
    }

    public void clearSelectedWorldUseItem() {
        selectedWorldItemIndex = -1;
        selectedWorldItem = null;
        selectedWorldItemName = null;
    }

    public boolean startCooking(int x, int y) {
        stopFishing();
        stopMining();
        stopSmelting();
        InventorySystem.Item selectedItem = getSelectedWorldUseItem();

        if (!isCookableItem(selectedItem)) {
            cookingMessage = "Use a raw fish from your inventory first, then interact with the campfire.";
            return false;
        }

        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(selectedItem);
        int cookingLevel = playerCharacter == null ? 1 : Math.max(1, playerCharacter.getSkillLevel(CharacterSkill.COOKING));
        if (recipe != null && cookingLevel < recipe.requiredLevel()) {
            cookingMessage = "You need Cooking level " + recipe.requiredLevel() + " to cook " + selectedItem.getName() + ".";
            return false;
        }

        cookingActive = true;
        cookingX = x;
        cookingY = y;
        cookingElapsedMs = 0;
        cookingItemName = selectedItem.getName();
        cookingMessage = "You hold " + cookingItemName + " near the flames.";
        return true;
    }

    public void stopCooking() {
        cookingActive = false;
        cookingX = -1;
        cookingY = -1;
        cookingElapsedMs = 0;
        cookingItemName = null;
    }

    public boolean isCookingActive() {
        return cookingActive;
    }

    public boolean isCookingAt(int x, int y) {
        return cookingActive && cookingX == x && cookingY == y;
    }

    public void updateCooking(int deltaMs) {
        if (!cookingActive || playerCharacter == null) {
            return;
        }

        cookingElapsedMs += Math.max(0, deltaMs);

        if (cookingElapsedMs < gatheringAttemptIntervalMs()) {
            return;
        }

        cookingElapsedMs = 0;

        if (cookingItemName == null || cookingItemName.isBlank()) {
            cookingMessage = "Use a raw fish from your inventory first, then interact with the campfire.";
            clearSelectedWorldUseItem();
            return;
        }

        InventorySystem.Item selectedItem = getSelectedWorldUseItem();

        if (selectedItem == null || !cookingItemName.equalsIgnoreCase(selectedItem.getName())) {
            int nextMatchingFishIndex = getInventory().findFirstItemIndexNamed(cookingItemName);

            if (nextMatchingFishIndex < 0) {
                cookingMessage = "You have no more " + cookingItemName + " to cook.";
                cookingItemName = null;
                cookingActive = false;
                clearSelectedWorldUseItem();
                return;
            }

            selectWorldUseItem(nextMatchingFishIndex);
            selectedItem = getSelectedWorldUseItem();
        }

        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(selectedItem);
        if (!isCookableItem(selectedItem) || !cookingItemName.equalsIgnoreCase(selectedItem.getName())) {
            cookingMessage = "That item cannot be cooked here.";
            cookingItemName = null;
            cookingActive = false;
            clearSelectedWorldUseItem();
            return;
        }

        int cookingLevel = Math.max(1, playerCharacter.getSkillLevel(CharacterSkill.COOKING));
        if (recipe != null && cookingLevel < recipe.requiredLevel()) {
            cookingMessage = "You need Cooking level " + recipe.requiredLevel() + " to cook " + cookingItemName + ".";
            cookingItemName = null;
            cookingActive = false;
            clearSelectedWorldUseItem();
            return;
        }

        getInventory().removeItem(selectedWorldItemIndex);
        clearSelectedWorldUseItem();

        double successChance = Math.min(
                maxCookingSuccessChance(),
                baseCookingSuccessChance() + cookingLevel * cookingSuccessChancePerLevel()
        );
        boolean cooked = Math.random() <= successChance;
        InventorySystem.Item resultItem = cooked
                ? createCookedItem(cookingItemName)
                : createBurntItem(cookingItemName);

        if (!getInventory().addItem(resultItem)) {
            cookingMessage = "Your inventory is too full to hold the result.";
            return;
        }

        advanceQuestIfAt(QuestLibrary.GATHERING_BASICS, 1, 2);

        if (cooked) {
            int xpReward = recipe == null ? cookingXpReward() : recipe.xpReward();
            int levelsGained = playerCharacter.addSkillExperience(CharacterSkill.COOKING, xpReward);
            cookingMessage = levelsGained > 0
                    ? "You cook the " + cookingItemName + ". Cooking level " + playerCharacter.getSkillLevel(CharacterSkill.COOKING) + "!"
                    : "You cook the " + cookingItemName + ". Cooking XP "
                    + playerCharacter.getSkillExperience(CharacterSkill.COOKING)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(CharacterSkill.COOKING)
                    + ".";
        } else {
            cookingMessage = "You burn the " + cookingItemName + ".";
        }

        int nextMatchingFishIndex = getInventory().findFirstItemIndexNamed(cookingItemName);
        if (nextMatchingFishIndex >= 0) {
            selectWorldUseItem(nextMatchingFishIndex);
        } else {
            cookingMessage = cookingMessage + " You have no more " + cookingItemName + " to cook.";
            cookingItemName = null;
            cookingActive = false;
        }
    }

    public String getCookingMessage() {
        return cookingMessage;
    }

    private boolean isCookableItem(InventorySystem.Item item) {
        return item != null
                && (findCookingRecipe(item) != null || ItemLibrary.RAW_FISH.getDisplayName().equalsIgnoreCase(item.getName()));
    }

    private InventorySystem.Item createCookedItem(String rawItemName) {
        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(rawItemName);
        if (recipe != null) {
            InventorySystem.Item item = createItemByNameOrId(recipe.cookedItemId());
            if (item != null) {
                return item;
            }
        }
        return ItemLibrary.COOKED_FISH.createItem();
    }

    private InventorySystem.Item createBurntItem(String rawItemName) {
        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(rawItemName);
        if (recipe != null) {
            InventorySystem.Item item = createItemByNameOrId(recipe.burntItemId());
            if (item != null) {
                return item;
            }
        }
        return ItemLibrary.BURNT_FISH.createItem();
    }

    private MapDesignLibrary.CustomCookingRecipe findCookingRecipe(InventorySystem.Item item) {
        return item == null ? null : findCookingRecipe(item.getName());
    }

    private MapDesignLibrary.CustomCookingRecipe findCookingRecipe(String itemNameOrId) {
        if (itemNameOrId == null || itemNameOrId.isBlank()) {
            return null;
        }

        List<MapDesignLibrary.CustomItem> itemDefinitions = new ArrayList<>(customItems.values());
        for (MapDesignLibrary.CustomCookingRecipe recipe : customCookingRecipes.values()) {
            if (recipe.matches(itemNameOrId, itemDefinitions)) {
                return recipe;
            }
        }

        try {
            MapDesignLibrary.AuthoredContent content = MapDesignLibrary.loadSharedContent();
            for (MapDesignLibrary.CustomCookingRecipe recipe : content.customCookingRecipes()) {
                if (recipe.matches(itemNameOrId, content.customItems())) {
                    return recipe;
                }
            }
        } catch (IOException ignored) {
            // Built-in raw fish fallback remains available below.
        }

        return null;
    }

    public boolean startSmelting(int x, int y) {
        stopFishing();
        stopMining();
        stopCooking();
        InventorySystem.Item selectedItem = getSelectedWorldUseItem();

        if (!RecipeLibrary.isSmeltableItem(selectedItem)) {
            smeltingMessage = "Use copper ore from your inventory first, then interact with the furnace.";
            return false;
        }

        smeltingActive = true;
        smeltingX = x;
        smeltingY = y;
        smeltingElapsedMs = 0;
        smeltingItemName = selectedItem.getName();
        smeltingMessage = "You place " + smeltingItemName + " into the furnace.";
        return true;
    }

    public void stopSmelting() {
        smeltingActive = false;
        smeltingX = -1;
        smeltingY = -1;
        smeltingElapsedMs = 0;
        smeltingItemName = null;
    }

    public boolean isSmeltingActive() {
        return smeltingActive;
    }

    public boolean isSmeltingAt(int x, int y) {
        return smeltingActive && smeltingX == x && smeltingY == y;
    }

    public void updateSmelting(int deltaMs) {
        if (!smeltingActive || playerCharacter == null) {
            return;
        }

        smeltingElapsedMs += Math.max(0, deltaMs);

        if (smeltingElapsedMs < gatheringAttemptIntervalMs()) {
            return;
        }

        smeltingElapsedMs = 0;

        if (smeltingItemName == null || smeltingItemName.isBlank()) {
            smeltingMessage = "Use ore from your inventory first, then interact with the furnace.";
            clearSelectedWorldUseItem();
            return;
        }

        InventorySystem.Item selectedItem = getSelectedWorldUseItem();

        if (selectedItem == null || !smeltingItemName.equalsIgnoreCase(selectedItem.getName())) {
            int nextMatchingOreIndex = getInventory().findFirstItemIndexNamed(smeltingItemName);

            if (nextMatchingOreIndex < 0) {
                smeltingMessage = "You have no more " + smeltingItemName + " to smelt.";
                smeltingItemName = null;
                smeltingActive = false;
                clearSelectedWorldUseItem();
                return;
            }

            selectWorldUseItem(nextMatchingOreIndex);
            selectedItem = getSelectedWorldUseItem();
        }

        RecipeLibrary.SmeltingRecipe recipe = RecipeLibrary.smeltingRecipeFor(selectedItem);
        if (recipe == null || !recipe.inputName().equalsIgnoreCase(smeltingItemName)) {
            smeltingMessage = "That item cannot be smelted here.";
            smeltingItemName = null;
            smeltingActive = false;
            clearSelectedWorldUseItem();
            return;
        }

        if (playerCharacter.getSkillLevel(CharacterSkill.SMITHING) < recipe.requiredLevel()) {
            smeltingMessage = "You need Smithing level " + recipe.requiredLevel() + " to smelt " + recipe.inputName() + ".";
            smeltingItemName = null;
            smeltingActive = false;
            clearSelectedWorldUseItem();
            return;
        }

        getInventory().removeItem(selectedWorldItemIndex);
        clearSelectedWorldUseItem();

        if (!getInventory().addItem(recipe.createOutput())) {
            smeltingMessage = "Your inventory is too full to hold the bar.";
            return;
        }

        advanceQuestIfAt(QuestLibrary.GATHERING_BASICS, 3, 4);

        int levelsGained = playerCharacter.addSkillExperience(CharacterSkill.SMITHING, recipe.xpReward());
        smeltingMessage = levelsGained > 0
                ? "You smelt a " + recipe.outputName() + ". Smithing level "
                + playerCharacter.getSkillLevel(CharacterSkill.SMITHING) + "!"
                : "You smelt a " + recipe.outputName() + ". Smithing XP "
                + playerCharacter.getSkillExperience(CharacterSkill.SMITHING)
                + "/"
                + playerCharacter.getSkillExperienceRequired(CharacterSkill.SMITHING)
                + ".";

        int nextMatchingOreIndex = getInventory().findFirstItemIndexNamed(smeltingItemName);
        if (nextMatchingOreIndex >= 0) {
            selectWorldUseItem(nextMatchingOreIndex);
        } else {
            smeltingMessage = smeltingMessage + " You have no more " + smeltingItemName + " to smelt.";
            smeltingItemName = null;
            smeltingActive = false;
        }
    }

    public String getSmeltingMessage() {
        return smeltingMessage;
    }

    public boolean startSmithing(int x, int y) {
        stopFishing();
        stopMining();
        stopCooking();
        stopSmelting();

        InventorySystem.Item selectedItem = getSelectedWorldUseItem();
        if (!RecipeLibrary.isSmithingMaterial(selectedItem)) {
            smithingMaterialName = null;
            smithingMessage = "Use a metal bar from your inventory first, then interact with the anvil.";
            return false;
        }

        smithingMaterialName = selectedItem.getName();
        smithingMessage = "Choose what to make with " + smithingMaterialName + ".";
        return true;
    }

    public List<RecipeLibrary.SmithingRecipe> getAvailableSmithingRecipes() {
        return RecipeLibrary.smithingRecipesForMaterial(smithingMaterialName);
    }

    public String getSmithingMaterialName() {
        return smithingMaterialName;
    }

    public String getSmithingMessage() {
        return smithingMessage;
    }

    public boolean craftSmithingRecipe(RecipeLibrary.SmithingRecipe recipe) {
        if (recipe == null || playerCharacter == null) {
            return false;
        }

        if (playerCharacter.getSkillLevel(CharacterSkill.SMITHING) < recipe.requiredLevel()) {
            smithingMessage = "You need Smithing level " + recipe.requiredLevel() + " to make " + recipe.displayName() + ".";
            return false;
        }

        int barCount = countInventoryItemsNamed(recipe.materialName());
        if (barCount < recipe.requiredBars()) {
            smithingMessage = "You need " + recipe.requiredBars() + " " + recipe.materialName() + " to make " + recipe.displayName() + ".";
            return false;
        }

        if (!getInventory().hasFreeSlot() && recipe.requiredBars() <= 0) {
            smithingMessage = "Your inventory is too full to hold the result.";
            return false;
        }

        for (int i = 0; i < recipe.requiredBars(); i++) {
            getInventory().removeFirstItemNamed(recipe.materialName());
        }

        if (!getInventory().addItem(recipe.createResult())) {
            smithingMessage = "Your inventory is too full to hold the result.";
            return false;
        }

        int levelsGained = playerCharacter.addSkillExperience(CharacterSkill.SMITHING, recipe.xpReward());
        smithingMessage = levelsGained > 0
                ? "You smith a " + recipe.displayName() + ". Smithing level "
                + playerCharacter.getSkillLevel(CharacterSkill.SMITHING) + "!"
                : "You smith a " + recipe.displayName() + ". Smithing XP "
                + playerCharacter.getSkillExperience(CharacterSkill.SMITHING)
                + "/"
                + playerCharacter.getSkillExperienceRequired(CharacterSkill.SMITHING)
                + ".";

        if (countInventoryItemsNamed(recipe.materialName()) <= 0) {
            smithingMaterialName = null;
            clearSelectedWorldUseItem();
            smithingMessage += " You have no more " + recipe.materialName() + ".";
        }

        return true;
    }

    private int countInventoryItemsNamed(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return 0;
        }

        int count = 0;
        InventorySystem.Inventory inventory = getInventory();

        for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
            InventorySystem.Item item = inventory.getItem(i);
            if (item != null && itemName.equalsIgnoreCase(item.getName())) {
                count++;
            }
        }

        return count;
    }

    private void advanceQuestIfAt(QuestLibrary quest, int expectedStage, int nextStage) {
        if (getQuestStage(quest) == expectedStage) {
            setQuestStage(quest, nextStage);
        }
    }

    private static class ResourceNodeState {
        private int exhaustionLevel = 0;
        private int attemptsSinceLastExhaustionRoll = 0;
        private int respawnRemainingMs = 0;
    }

    private static String mapRuntimeKey(Path path) {
        return path == null ? "" : MapDesignLibrary.resourcePathForMap(path).replace('\\', '/');
    }

    private static DungeonMap copyDungeonMap(DungeonMap source) {
        if (source == null) {
            return null;
        }

        Library.TileType[][] tiles = new Library.TileType[source.getHeight()][source.getWidth()];
        int[][] themes = new int[source.getHeight()][source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                tiles[y][x] = source.getTile(x, y);
                themes[y][x] = source.getEnvironmentThemeIndex(x, y);
            }
        }
        MapPaintData paintData = source.getPaintData() == null
                ? MapPaintData.blank(source.getWidth(), source.getHeight())
                : source.getPaintData().copy();
        MapGeometryData geometryData = source.getGeometryData() == null
                ? MapGeometryData.blank(source.getWidth(), source.getHeight())
                : source.getGeometryData().copy();
        return new DungeonMap(tiles, themes, paintData, geometryData);
    }

    private Map<String, ResourceNodeSnapshot> copyResourceNodeSnapshots() {
        Map<String, ResourceNodeSnapshot> snapshots = new HashMap<>();
        for (Map.Entry<String, ResourceNodeState> entry : resourceNodeStates.entrySet()) {
            ResourceNodeState state = entry.getValue();
            snapshots.put(entry.getKey(), new ResourceNodeSnapshot(
                    state.exhaustionLevel,
                    state.attemptsSinceLastExhaustionRoll,
                    state.respawnRemainingMs
            ));
        }
        return snapshots;
    }

    public Map<String, ResourceNodeSnapshot> getResourceNodeSnapshotsView() {
        return Map.copyOf(copyResourceNodeSnapshots());
    }

    private void restoreResourceNodeSnapshots(Map<String, ResourceNodeSnapshot> snapshots) {
        resourceNodeStates.clear();
        if (snapshots == null) {
            return;
        }

        for (Map.Entry<String, ResourceNodeSnapshot> entry : snapshots.entrySet()) {
            ResourceNodeSnapshot snapshot = entry.getValue();
            if (entry.getKey() == null || snapshot == null) {
                continue;
            }
            ResourceNodeState state = new ResourceNodeState();
            state.exhaustionLevel = Math.max(0, snapshot.exhaustionLevel());
            state.attemptsSinceLastExhaustionRoll = Math.max(0, snapshot.attemptsSinceLastExhaustionRoll());
            state.respawnRemainingMs = Math.max(0, snapshot.respawnRemainingMs());
            resourceNodeStates.put(entry.getKey(), state);
            restoreResourceNode(entry.getKey());
        }
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public void startRotationAnimation(int previousDirection, int nextDirection) {
        if (!isFluidCameraMovement()) {
            rotationProgress = 1.0;
            return;
        }

        int turnDelta = Math.floorMod(nextDirection - previousDirection, 4);

        rotationStartOffsetRadians = switch (turnDelta) {
            case 1 -> -Math.PI / 2.0;
            case 3 -> Math.PI / 2.0;
            case 2 -> Math.PI;
            default -> 0.0;
        };
        rotationProgress = rotationStartOffsetRadians == 0.0 ? 1.0 : 0.0;
    }

    public CameraMovementMode getCameraMovementMode() {
        return cameraMovementMode;
    }

    public void setCameraMovementMode(CameraMovementMode cameraMovementMode) {
        this.cameraMovementMode = cameraMovementMode == null ? CameraMovementMode.STATIC : cameraMovementMode;

        if (!isFluidCameraMovement()) {
            movementProgress = 1.0;
            rotationProgress = 1.0;
        }
    }

    public void toggleCameraMovementMode() {
        setCameraMovementMode(isFluidCameraMovement()
                ? CameraMovementMode.STATIC
                : CameraMovementMode.FLUID);
    }

    public boolean isFluidCameraMovement() {
        return cameraMovementMode == CameraMovementMode.FLUID;
    }

    public BattleEncounter getCurrentEncounter() {
        return currentEncounter;
    }

    public void setCurrentEncounter(BattleEncounter currentEncounter) {
        this.currentEncounter = currentEncounter;
    }

    public MapEntity getCurrentEnemyEntity() {
        return currentEnemyEntity;
    }

    public void setCurrentEnemyEntity(MapEntity currentEnemyEntity) {
        this.currentEnemyEntity = currentEnemyEntity;
    }

    public void clearBattleState() {
        currentEncounter = null;
        currentEnemyEntity = null;
        gameMode = GameMode.DUNGEON;
    }

    public void enterGameOver() {
        currentEncounter = null;
        currentEnemyEntity = null;
        closeInventory();
        closeSkills();
        closeQuests();
        closeStats();
        closeShop();
        closeInteraction();
        gameMode = GameMode.GAME_OVER;
    }

    private int movementAnimationDurationMs() {
        return Math.max(1, GameConfiguration.intValue("movement.animationDurationMs", 160));
    }

    private int rotationAnimationDurationMs() {
        return Math.max(1, GameConfiguration.intValue("rotation.animationDurationMs", 360));
    }

    private int resourceRespawnMs() {
        return Math.max(0, GameConfiguration.intValue("resource.respawnMs", 300000));
    }

    private int gatheringAttemptIntervalMs() {
        return Math.max(1, GameConfiguration.intValue("resource.gatheringAttemptIntervalMs", 2500));
    }

    private int resourceAttemptsPerExhaustionRoll() {
        return Math.max(1, GameConfiguration.intValue("resource.attemptsPerExhaustionRoll", 2));
    }

    private double resourceExhaustionChance() {
        return clampChance(GameConfiguration.doubleValue("resource.exhaustionChance", 0.50));
    }

    private int maxResourceExhaustionLevel() {
        return Math.max(1, GameConfiguration.intValue("resource.maxExhaustionLevel", 2));
    }

    private double baseFishingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("fishing.baseSuccessChance", 0.35));
    }

    private double fishingSuccessChancePerLevel() {
        return Math.max(0.0, GameConfiguration.doubleValue("fishing.successChancePerLevel", 0.03));
    }

    private double maxFishingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("fishing.maxSuccessChance", 0.85));
    }

    private int fishingXpReward() {
        return Math.max(0, GameConfiguration.intValue("fishing.xpReward", 18));
    }

    private double baseMiningSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("mining.baseSuccessChance", 0.40));
    }

    private double miningSuccessChancePerLevel() {
        return Math.max(0.0, GameConfiguration.doubleValue("mining.successChancePerLevel", 0.03));
    }

    private double maxMiningSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("mining.maxSuccessChance", 0.88));
    }

    private int miningXpReward() {
        return Math.max(0, GameConfiguration.intValue("mining.xpReward", 18));
    }

    private double baseCookingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("cooking.baseSuccessChance", 0.45));
    }

    private double cookingSuccessChancePerLevel() {
        return Math.max(0.0, GameConfiguration.doubleValue("cooking.successChancePerLevel", 0.035));
    }

    private double maxCookingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("cooking.maxSuccessChance", 0.90));
    }

    private int cookingXpReward() {
        return Math.max(0, GameConfiguration.intValue("cooking.xpReward", 20));
    }

    private double clampChance(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record QuestDefinition(String id, String displayName, List<String> stageDescriptions) {
        public QuestDefinition {
            id = id == null ? "" : id;
            displayName = displayName == null || displayName.isBlank() ? "Untitled Quest" : displayName;
            stageDescriptions = stageDescriptions == null || stageDescriptions.isEmpty()
                    ? List.of("Begin the quest.", "Complete.")
                    : List.copyOf(stageDescriptions);
        }

        public int maxStage() {
            return stageDescriptions.size() - 1;
        }

        public boolean isComplete(int stage) {
            return stage >= maxStage();
        }

        public String stageDescription(int stage) {
            int safeStage = Math.max(0, Math.min(maxStage(), stage));
            return stageDescriptions.get(safeStage);
        }
    }

    public record ResourceNodeSnapshot(
            int exhaustionLevel,
            int attemptsSinceLastExhaustionRoll,
            int respawnRemainingMs
    ) {
    }

    public record MovementCoordinates(
            int previousX,
            int previousY,
            int nextX,
            int nextY,
            boolean recentered
    ) {
    }

    public record OpenWorldRuntimeState(
            Path manifestPath,
            int resumeGlobalX,
            int resumeGlobalY,
            Map<WorldManifestLibrary.ChunkCoordinate, OpenWorldSession.PersistedChunkState> chunkStates
    ) {
        public OpenWorldRuntimeState {
            chunkStates = chunkStates == null ? Map.of() : Map.copyOf(chunkStates);
        }
    }

    public record MapRuntimeState(
            Path mapPath,
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            boolean hasEntitySnapshot,
            Map<String, String> tileInteractionIds,
            Set<String> removedEntityKeys,
            Map<String, ResourceNodeSnapshot> resourceNodeStates,
            Set<String> discoveredMiniMapTiles,
            List<MapDesignLibrary.MapTrigger> mapTriggers,
            Set<String> firedTriggerIds,
            List<MapDesignLibrary.AuthoredDialogue> authoredDialogues,
            List<MapDesignLibrary.AuthoredQuest> authoredQuests,
            List<MapDesignLibrary.CustomItem> customItems,
            List<MapDesignLibrary.CustomLimb> customLimbs,
            List<MapDesignLibrary.CustomGatheringNode> customGatheringNodes,
            List<MapDesignLibrary.CustomCookingRecipe> customCookingRecipes,
            List<MapDesignLibrary.CustomCompositeRecipe> customCompositeRecipes
    ) {
        public MapRuntimeState {
            entities = entities == null ? List.of() : List.copyOf(entities);
            tileInteractionIds = tileInteractionIds == null ? Map.of() : Map.copyOf(tileInteractionIds);
            removedEntityKeys = removedEntityKeys == null ? Set.of() : Set.copyOf(removedEntityKeys);
            resourceNodeStates = resourceNodeStates == null ? Map.of() : Map.copyOf(resourceNodeStates);
            discoveredMiniMapTiles = discoveredMiniMapTiles == null ? Set.of() : Set.copyOf(discoveredMiniMapTiles);
            mapTriggers = mapTriggers == null ? List.of() : List.copyOf(mapTriggers);
            firedTriggerIds = firedTriggerIds == null ? Set.of() : Set.copyOf(firedTriggerIds);
            authoredDialogues = authoredDialogues == null ? List.of() : List.copyOf(authoredDialogues);
            authoredQuests = authoredQuests == null ? List.of() : List.copyOf(authoredQuests);
            customItems = customItems == null ? List.of() : List.copyOf(customItems);
            customLimbs = customLimbs == null ? List.of() : List.copyOf(customLimbs);
            customGatheringNodes = customGatheringNodes == null ? List.of() : List.copyOf(customGatheringNodes);
            customCookingRecipes = customCookingRecipes == null ? List.of() : List.copyOf(customCookingRecipes);
            customCompositeRecipes = customCompositeRecipes == null ? List.of() : List.copyOf(customCompositeRecipes);
        }
    }
}

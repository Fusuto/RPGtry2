package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.battle.BattleSkill;
import org.main.content.MapDesignLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.engine.AssetLoader;
import org.main.monsters.Monster;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameState {
    private static final String GOLD_ITEM_ID = "GOLD";
    private static final String GOLD_ITEM_NAME = "Gold";
    private static final String RAW_FISH_ITEM_ID = "RAW_FISH";
    private static final String RAW_FISH_ITEM_NAME = "Raw Fish";
    private static final String COOKED_FISH_ITEM_ID = "COOKED_FISH";
    private static final String COPPER_ORE_ITEM_ID = "COPPER_ORE";
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
    private final Map<String, MapDesignLibrary.CraftingRecipe> craftingRecipes = new HashMap<>();
    private final Map<String, MapRuntimeState> mapRuntimeStates = new HashMap<>();
    private final Map<String, OpenWorldSession> openWorldSessions = new HashMap<>();
    private final Set<String> spokenAuthoredDialogues = new HashSet<>();
    private final Set<String> removedEntityKeys = new HashSet<>();
    private final Set<String> firedMapTriggerIds = new HashSet<>();
    private final Map<String, Integer> questStages = new HashMap<>();
    private final InputBindings inputBindings = new InputBindings();
    private final WorldMessageLog worldMessageLog = new WorldMessageLog();

    private final NavigationState navigationState = new NavigationState();
    private final MiniMapState miniMapState = new MiniMapState();
    private final QuestState questState = new QuestState();
    private final SkillingState skillingState = new SkillingState();

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
    private MiningAttemptOutcome fishingPendingOutcome = MiningAttemptOutcome.NONE;
    private InventorySystem.Item fishingPendingItem;
    private int fishingRecoveryElapsedMs = -1;
    private MiningAttemptOutcome fishingRecoveryOutcome = MiningAttemptOutcome.NONE;
    private boolean miningActive = false;
    private int miningX = -1;
    private int miningY = -1;
    private int miningElapsedMs = 0;
    private String miningInteractionId = "";
    private String miningMessage = "Strike the rock.";
    private GatheringToolType miningToolType = GatheringToolType.MINING;
    private MiningAttemptOutcome miningPendingOutcome = MiningAttemptOutcome.NONE;
    private InventorySystem.Item miningPendingItem;
    private int miningRecoveryElapsedMs = -1;
    private MiningAttemptOutcome miningRecoveryOutcome = MiningAttemptOutcome.NONE;
    private GatheringImpactEvent pendingGatheringImpactEvent;
    private boolean gatheringSuspendedByPauseOverlay = false;
    private final Map<String, ResourceNodeState> resourceNodeStates = new HashMap<>();
    private final Map<String, EnemyRespawnState> enemyRespawnStates = new HashMap<>();
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
    private InteractionSystem.Interaction suspendedInteraction;
    private ShopSystem.ShopSession activeShop;

    public enum GatheringToolType {
        MINING("mining"),
        FISHING("fishing"),
        WOODCUTTING("woodcutting");

        private final String configurationPrefix;

        GatheringToolType(String configurationPrefix) {
            this.configurationPrefix = configurationPrefix;
        }

        public String configurationPrefix() {
            return configurationPrefix;
        }
    }

    public enum MiningAttemptOutcome {
        NONE,
        SUCCESS,
        FAILURE
    }

    public enum MiningViewMotion {
        REST,
        WINDUP,
        SUCCESS_STRIKE,
        FAILURE_STRIKE,
        SUCCESS_RECOVERY,
        FAILURE_RECOVERY
    }

    public record MiningViewModelState(
            GatheringToolType toolType,
            boolean visible,
            MiningViewMotion motion,
            double progress
    ) {
        public MiningViewModelState {
            toolType = toolType == null ? GatheringToolType.MINING : toolType;
            progress = Math.max(0.0, Math.min(1.0, progress));
        }

        public static MiningViewModelState hidden() {
            return new MiningViewModelState(GatheringToolType.MINING, false, MiningViewMotion.REST, 0.0);
        }
    }

    public record GatheringImpactEvent(
            GatheringToolType toolType,
            MiningAttemptOutcome outcome
    ) {
    }

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
            restoreSuspendedInteractionOrStopActivities();
        }

        return activeInteraction;
    }

    public boolean hasActiveInteraction() {
        return getActiveInteraction() != null;
    }

    public void openInteraction(InteractionSystem.Interaction interaction) {
        if (interaction == null) {
            return;
        }

        InteractionSystem.Interaction current = getActiveInteraction();
        if (current == null && interaction.pausesGameplay() && isFirstPersonGatheringActive()) {
            gatheringSuspendedByPauseOverlay = true;
        }
        if (current != null && current != interaction) {
            if (interaction.pausesGameplay()
                    && current.isCharacterMenuOverlayAllowed()
                    && !current.pausesGameplay()) {
                suspendedInteraction = current;
            } else if (current.pausesGameplay()) {
                interaction.pauseGameplay();
            } else {
                current.close();
                suspendedInteraction = null;
                if (current.isCharacterMenuOverlayAllowed()) {
                    stopSkillingActivities();
                }
            }
        }

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

        restoreSuspendedInteractionOrStopActivities();
    }

    private void restoreSuspendedInteractionOrStopActivities() {
        if (activeInteraction != null
                && activeInteraction.pausesGameplay()
                && suspendedInteraction != null
                && !suspendedInteraction.isClosed()) {
            activeInteraction = suspendedInteraction;
            suspendedInteraction = null;
            return;
        }

        if (activeInteraction != null
                && activeInteraction.pausesGameplay()
                && gatheringSuspendedByPauseOverlay
                && isFirstPersonGatheringActive()) {
            activeInteraction = null;
            suspendedInteraction = null;
            gatheringSuspendedByPauseOverlay = false;
            return;
        }

        activeInteraction = null;
        suspendedInteraction = null;
        gatheringSuspendedByPauseOverlay = false;
        stopSkillingActivities();
    }

    public boolean isFirstPersonGatheringActive() {
        return miningActive || fishingActive || miningRecoveryElapsedMs >= 0 || fishingRecoveryElapsedMs >= 0;
    }

    public boolean isFirstPersonGatheringAt(int x, int y) {
        return (miningActive && miningX == x && miningY == y)
                || (fishingActive && fishingX == x && fishingY == y);
    }

    public void cancelFirstPersonGathering() {
        stopFishing();
        stopMining();
        fishingRecoveryElapsedMs = -1;
        fishingRecoveryOutcome = MiningAttemptOutcome.NONE;
        miningRecoveryElapsedMs = -1;
        miningRecoveryOutcome = MiningAttemptOutcome.NONE;
        pendingGatheringImpactEvent = null;
    }

    public MiningViewModelState getGatheringViewModelState() {
        if (!isFirstPersonGatheringActive()) {
            return MiningViewModelState.hidden();
        }
        if (miningRecoveryElapsedMs >= 0) {
            MiningViewMotion motion = miningRecoveryOutcome == MiningAttemptOutcome.SUCCESS
                    ? MiningViewMotion.SUCCESS_RECOVERY : MiningViewMotion.FAILURE_RECOVERY;
            return new MiningViewModelState(miningToolType, true, motion,
                    miningRecoveryElapsedMs / (double) gatheringRecoveryDurationMs());
        }
        if (fishingRecoveryElapsedMs >= 0) {
            MiningViewMotion motion = fishingRecoveryOutcome == MiningAttemptOutcome.SUCCESS
                    ? MiningViewMotion.SUCCESS_RECOVERY : MiningViewMotion.FAILURE_RECOVERY;
            return new MiningViewModelState(GatheringToolType.FISHING, true, motion,
                    fishingRecoveryElapsedMs / (double) gatheringRecoveryDurationMs());
        }

        boolean fishing = fishingActive;
        int elapsedMs = fishing ? fishingElapsedMs : miningElapsedMs;
        MiningAttemptOutcome pendingOutcome = fishing ? fishingPendingOutcome : miningPendingOutcome;
        GatheringToolType toolType = fishing ? GatheringToolType.FISHING : miningToolType;
        int intervalMs = gatheringAttemptIntervalMs();
        int strikeDurationMs = gatheringStrikeDurationMs();
        int strikeStartMs = Math.max(0, intervalMs - strikeDurationMs);
        if (elapsedMs < strikeStartMs || pendingOutcome == MiningAttemptOutcome.NONE) {
            return new MiningViewModelState(toolType, true, MiningViewMotion.REST, 0.0);
        }
        double strikeProgress = (elapsedMs - strikeStartMs) / (double) Math.max(1, strikeDurationMs);
        MiningViewMotion motion = pendingOutcome == MiningAttemptOutcome.SUCCESS
                ? MiningViewMotion.SUCCESS_STRIKE : MiningViewMotion.FAILURE_STRIKE;
        return new MiningViewModelState(toolType, true, motion, strikeProgress);
    }

    public GatheringImpactEvent consumeGatheringImpactEvent() {
        GatheringImpactEvent event = pendingGatheringImpactEvent;
        pendingGatheringImpactEvent = null;
        return event;
    }

    public WorldMessageLog getWorldMessageLog() {
        return worldMessageLog;
    }

    public NavigationState getNavigationState() {
        return navigationState;
    }

    public MiniMapState getMiniMapState() {
        return miniMapState;
    }

    public QuestState getQuestState() {
        return questState;
    }

    public SkillingState getSkillingState() {
        return skillingState;
    }

    private void clearInteractionsAndStopActivities() {
        if (activeInteraction != null) {
            activeInteraction.close();
        }
        if (suspendedInteraction != null) {
            suspendedInteraction.close();
        }

        activeInteraction = null;
        suspendedInteraction = null;
        gatheringSuspendedByPauseOverlay = false;
        stopSkillingActivities();
    }

    public void prepareForEnemyEngagement() {
        closeInventory();
        closeSkills();
        closeQuests();
        closeStats();
        closeShop();
        clearInteractionsAndStopActivities();
    }

    private void stopSkillingActivities() {
        cancelFirstPersonGathering();
        stopCooking();
        stopSmelting();
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
        return isFirstPersonGatheringActive()
                || interaction != null && interaction.isInventoryOverlayAllowed();
    }

    public boolean isCharacterMenuOverlayAllowed() {
        InteractionSystem.Interaction interaction = getActiveInteraction();
        return isFirstPersonGatheringActive()
                || interaction != null && interaction.isCharacterMenuOverlayAllowed();
    }

    public boolean isGameplayPaused() {
        InteractionSystem.Interaction interaction = getActiveInteraction();
        return interaction != null && interaction.pausesGameplay();
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
            if (!isCharacterMenuOverlayAllowed()) {
                closeInteraction();
            }
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
            if (!isCharacterMenuOverlayAllowed()) {
                closeInteraction();
            }
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
            if (!isCharacterMenuOverlayAllowed()) {
                closeInteraction();
            }
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
        setCraftingRecipes(generatedDungeon.craftingRecipes());
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
        qualifyAuthoredEnemySpawns(mapDesignPath);
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
        qualifyAuthoredEnemySpawns(mapDesignPath);
        currentMapDesignPath = mapDesignPath;
    }

    private void qualifyAuthoredEnemySpawns(Path mapDesignPath) {
        String sourceId = mapRuntimeKey(mapDesignPath);
        if (sourceId.isBlank()) {
            return;
        }
        for (MapEntity entity : entities) {
            if (entity.getType() != Library.EntityType.ENEMY || entity.getEnemySpawnId().isBlank()) {
                continue;
            }
            int cooldown = entity.getWorldAiCooldownMs();
            boolean alerted = entity.isWorldAlerted();
            entity.configureEnemySpawn(
                    sourceId + "|" + entity.getEnemySpawnId(),
                    entity.getSpawnX(),
                    entity.getSpawnY(),
                    entity.getRoamingAreaId(),
                    entity.getAwarenessRadius(),
                    entity.getMovementIntervalMs(),
                    entity.getRespawnDelayMs()
            );
            entity.setWorldAiCooldownMs(cooldown);
            entity.setWorldAlerted(alerted);
        }
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
                getEnemyRespawnSnapshots(),
                getEnemyActiveSnapshots(),
                System.currentTimeMillis(),
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
                new ArrayList<>(craftingRecipes.values()),
                getTemporaryStationSnapshots()
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
                getEnemyRespawnSnapshots(),
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
        setCraftingRecipes(window.craftingRecipes());
        restoreResourceNodeSnapshots(window.resourceNodeStates());
        restoreEnemyRespawnSnapshots(window.enemyRespawns());
        resetMiniMapDiscovery();
        setDiscoveredMiniMapTileKeys(window.discoveredTiles());

        if (resetPanels) {
            setPlayerPosition(window.playerX(), window.playerY());
            closeInventory();
            closeSkills();
            closeQuests();
            closeStats();
            closeShop();
            clearInteractionsAndStopActivities();
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
        craftingRecipes.clear();
        currentMapDesignPath = null;
        resourceNodeStates.clear();
        enemyRespawnStates.clear();
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
        clearInteractionsAndStopActivities();
        clearBattleState();
    }

    private void restoreRuntimeState(MapRuntimeState state, Path targetPath, int targetX, int targetY) {
        dungeonMap = copyDungeonMap(state.dungeonMap());
        entities.clear();
        entities.addAll(state.entities());
        restoreTemporaryStationSnapshots(
                state.temporaryStations(),
                Math.max(0L, System.currentTimeMillis() - state.lastUpdatedEpochMs())
        );
        tileInteractionIds.clear();
        tileInteractionIds.putAll(state.tileInteractionIds());
        mapTriggers.clear();
        mapTriggers.addAll(state.mapTriggers());
        firedMapTriggerIds.clear();
        firedMapTriggerIds.addAll(state.firedTriggerIds());
        removedEntityKeys.clear();
        removedEntityKeys.addAll(state.removedEntityKeys());
        restoreResourceNodeSnapshots(state.resourceNodeStates());
        restoreEnemySpawnSnapshots(state);
        currentMapDesignPath = targetPath;
        setAuthoredDialogues(state.authoredDialogues());
        setAuthoredQuests(state.authoredQuests());
        setCustomItems(state.customItems());
        setCustomLimbs(state.customLimbs());
        setCustomGatheringNodes(state.customGatheringNodes());
        setCustomCookingRecipes(state.customCookingRecipes());
        setCraftingRecipes(state.craftingRecipes());
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
        clearInteractionsAndStopActivities();
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
        restoreEnemySpawnSnapshots(state);
        restoreTemporaryStationSnapshots(
                state.temporaryStations(),
                Math.max(0L, System.currentTimeMillis() - state.lastUpdatedEpochMs())
        );
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
        evaluateQuestStageTriggers();
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

    public boolean evaluateQuestStageTriggers() {
        if (dungeonMap == null || mapTriggers.isEmpty()) {
            return false;
        }

        boolean doorChanged = false;
        for (MapDesignLibrary.MapTrigger trigger : mapTriggers) {
            if (trigger == null
                    || trigger.fireMode() != MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE
                    || trigger.requiredQuestId().isBlank()
                    || getQuestStage(trigger.requiredQuestId()) < trigger.requiredQuestStage()) {
                continue;
            }
            if (trigger.oneShot() && firedMapTriggerIds.contains(trigger.id())) {
                continue;
            }

            for (MapDesignLibrary.TriggerAction action : trigger.actions()) {
                doorChanged |= runTriggerAction(action);
            }
            if (trigger.oneShot()) {
                firedMapTriggerIds.add(trigger.id());
            }
        }
        return doorChanged;
    }

    private boolean runTriggerAction(MapDesignLibrary.TriggerAction action) {
        if (action == null || dungeonMap == null) {
            return false;
        }

        int targetX = action.targetX();
        int targetY = action.targetY();
        if (dungeonMap.isOutOfBounds(targetX, targetY)) {
            return false;
        }

        Library.TileType tile = dungeonMap.getTile(targetX, targetY);
        return switch (action.type()) {
            case CLOSE_DOOR -> {
                if (tile == Library.TileType.DOOR_OPEN) {
                    dungeonMap.setTile(targetX, targetY, Library.TileType.DOOR_CLOSED);
                    yield true;
                }
                if (tile == Library.TileType.QUEST_DOOR_OPEN) {
                    dungeonMap.setTile(targetX, targetY, Library.TileType.QUEST_DOOR_CLOSED);
                    yield true;
                }
                yield false;
            }
            case OPEN_DOOR -> {
                if (tile == Library.TileType.DOOR_CLOSED) {
                    dungeonMap.setTile(targetX, targetY, Library.TileType.DOOR_OPEN);
                    yield true;
                }
                if (tile == Library.TileType.QUEST_DOOR_CLOSED) {
                    dungeonMap.setTile(targetX, targetY, Library.TileType.QUEST_DOOR_OPEN);
                    yield true;
                }
                yield false;
            }
        };
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

    public void setCraftingRecipes(List<MapDesignLibrary.CraftingRecipe> recipes) {
        craftingRecipes.clear();
        if (recipes == null) {
            return;
        }

        for (MapDesignLibrary.CraftingRecipe recipe : recipes) {
            if (recipe != null && !recipe.recipeId().isBlank()) {
                craftingRecipes.put(recipe.recipeId(), recipe);
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
            return null;
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

    public Map<String, Integer> getQuestStagesView() {
        return Map.copyOf(questStages);
    }

    public int getQuestStage(String questId) {
        if (questId == null || questId.isBlank()) {
            return -1;
        }
        return questStages.getOrDefault(questId, -1);
    }

    public List<QuestDefinition> getQuestDefinitions() {
        return List.copyOf(authoredQuestDefinitions.values());
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
        evaluateQuestStageTriggers();
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
        evaluateQuestStageTriggers();
    }

    public int getGold() {
        return getInventory().countItemNamed(GOLD_ITEM_NAME);
    }

    public void addGold(int amount) {
        if (amount <= 0) {
            return;
        }

        InventorySystem.Item gold = createItemByNameOrId(GOLD_ITEM_ID);
        if (gold != null) {
            gold.addQuantity(Math.max(0, amount - 1));
            getInventory().addItem(gold);
        }
    }

    public boolean canSpendGold(int amount) {
        return amount >= 0 && getGold() >= amount;
    }

    public boolean spendGold(int amount) {
        if (!canSpendGold(amount)) {
            return false;
        }

        return amount == 0 || getInventory().removeItemQuantityNamed(GOLD_ITEM_NAME, amount);
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
        if (isFishingAt(x, y)) {
            stopFishing();
            fishingMessage = "You reel in your line.";
            return false;
        }
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
        fishingPendingOutcome = MiningAttemptOutcome.NONE;
        fishingPendingItem = null;
        fishingRecoveryElapsedMs = -1;
        fishingRecoveryOutcome = MiningAttemptOutcome.NONE;
        return true;
    }

    public void stopFishing() {
        fishingActive = false;
        fishingX = -1;
        fishingY = -1;
        fishingElapsedMs = 0;
        fishingInteractionId = "";
        fishingPendingOutcome = MiningAttemptOutcome.NONE;
        fishingPendingItem = null;
    }

    public boolean isFishingActive() {
        return fishingActive;
    }

    public boolean isFishingAt(int x, int y) {
        return fishingActive && fishingX == x && fishingY == y;
    }

    public void updateFishing(int deltaMs) {
        if (fishingRecoveryElapsedMs >= 0) {
            fishingRecoveryElapsedMs += Math.max(0, deltaMs);
            if (fishingRecoveryElapsedMs >= gatheringRecoveryDurationMs()) {
                fishingRecoveryElapsedMs = -1;
                fishingRecoveryOutcome = MiningAttemptOutcome.NONE;
                if (!fishingActive) {
                    stopFishing();
                    return;
                }
                if (isResourceExhausted(fishingX, fishingY)) {
                    fishingMessage = "The shoal goes still. It needs time to recover.";
                    worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
                    stopFishing();
                    return;
                }
            }
            return;
        }

        if (!fishingActive || playerCharacter == null) {
            return;
        }

        fishingElapsedMs += Math.max(0, deltaMs);

        int intervalMs = gatheringAttemptIntervalMs();
        int strikeStartMs = Math.max(0, intervalMs - gatheringStrikeDurationMs());
        if (fishingPendingOutcome == MiningAttemptOutcome.NONE && fishingElapsedMs >= strikeStartMs) {
            prepareFishingAttempt();
            if (!fishingActive) {
                return;
            }
        }

        if (fishingElapsedMs < intervalMs) {
            return;
        }

        fishingElapsedMs = 0;
        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(fishingInteractionId);
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.FISHING : node.gatheringSkill();
        int xpReward = node == null ? fishingXpReward() : node.gatherXpReward();
        MiningAttemptOutcome outcome = fishingPendingOutcome;
        InventorySystem.Item gatheredItem = fishingPendingItem;
        fishingPendingOutcome = MiningAttemptOutcome.NONE;
        fishingPendingItem = null;

        if (outcome == MiningAttemptOutcome.SUCCESS && gatheredItem != null && getInventory().addItem(gatheredItem)) {
            int levelsGained = playerCharacter.addSkillExperience(gatheringSkill, xpReward);
            String outputName = gatheredItem.getName();
            fishingMessage = levelsGained > 0
                    ? "You catch " + outputName + ". " + gatheringSkill.getDisplayName() + " level " + playerCharacter.getSkillLevel(gatheringSkill) + "!"
                    : "You catch " + outputName + ". " + gatheringSkill.getDisplayName() + " XP "
                    + playerCharacter.getSkillExperience(gatheringSkill)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(gatheringSkill)
                    + ".";
            worldMessageLog.post(WorldMessageLog.Category.SUCCESS, fishingMessage);
        } else if (outcome == MiningAttemptOutcome.SUCCESS) {
            fishingMessage = "Your inventory is too full to hold any fish.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
            stopFishing();
            return;
        } else {
            fishingMessage = "The fish slip away.";
            outcome = MiningAttemptOutcome.FAILURE;
            worldMessageLog.post(WorldMessageLog.Category.FAILURE, fishingMessage);
        }

        recordResourceAttempt(fishingX, fishingY);
        fishingRecoveryElapsedMs = 0;
        fishingRecoveryOutcome = outcome;
        pendingGatheringImpactEvent = new GatheringImpactEvent(GatheringToolType.FISHING, outcome);

        if (isResourceExhausted(fishingX, fishingY)) {
            fishingMessage = "The shoal goes still. It needs time to recover.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
            fishingActive = false;
        }
    }

    private void prepareFishingAttempt() {
        if (isResourceExhausted(fishingX, fishingY)) {
            fishingMessage = "The shoal is quiet. Give it time to recover.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
            stopFishing();
            return;
        }
        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(fishingInteractionId);
        InventorySystem.Item item = node == null ? createItemByNameOrId(RAW_FISH_ITEM_ID) : createGatheredItem(node);
        if (item == null) {
            fishingMessage = "This fishing spot has no usable catch configured.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
            stopFishing();
            return;
        }
        if (!getInventory().canAddItem(item)) {
            fishingMessage = "Your inventory is too full to hold any fish.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, fishingMessage);
            stopFishing();
            return;
        }
        CharacterSkill skill = node == null ? CharacterSkill.FISHING : node.gatheringSkill();
        int level = Math.max(1, playerCharacter.getSkillLevel(skill));
        double chance = Math.min(maxFishingSuccessChance(), baseFishingSuccessChance() + level * fishingSuccessChancePerLevel());
        fishingPendingOutcome = Math.random() <= chance ? MiningAttemptOutcome.SUCCESS : MiningAttemptOutcome.FAILURE;
        fishingPendingItem = item;
    }

    public String getFishingMessage() {
        return fishingMessage;
    }

    public boolean startMining(int x, int y) {
        if (isMiningAt(x, y)) {
            stopMining();
            miningMessage = "You stop gathering.";
            return false;
        }
        stopFishing();
        stopCooking();
        stopSmelting();
        MapDesignLibrary.CustomGatheringNode node = getCustomGatheringNodeAt(x, y);
        boolean tree = node != null && node.nodeType() == MapDesignLibrary.GatheringNodeType.TREE;
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.MINING : node.gatheringSkill();
        if (node != null && playerCharacter != null
                && playerCharacter.getSkillLevel(gatheringSkill) < node.requiredLevel()) {
            miningMessage = "You need " + gatheringSkill.getDisplayName() + " level " + node.requiredLevel() + " to gather from " + node.displayName() + ".";
            return false;
        }

        if (isResourceExhausted(x, y)) {
            miningMessage = tree
                    ? "Only a stump remains. Give the tree time to regrow."
                    : "The rock is depleted. Give it time to recover.";
            return false;
        }

        GatheringToolType toolType = tree ? GatheringToolType.WOODCUTTING : GatheringToolType.MINING;
        miningActive = true;
        miningX = x;
        miningY = y;
        miningElapsedMs = 0;
        miningInteractionId = node == null ? "" : node.interactionId();
        miningMessage = tree
                ? "You begin cutting the " + node.displayName() + "."
                : "You swing at the " + (node == null ? "mineral rock" : node.displayName()) + ".";
        miningToolType = toolType;
        miningPendingOutcome = MiningAttemptOutcome.NONE;
        miningPendingItem = null;
        miningRecoveryElapsedMs = -1;
        miningRecoveryOutcome = MiningAttemptOutcome.NONE;
        return true;
    }

    public void stopMining() {
        miningActive = false;
        miningX = -1;
        miningY = -1;
        miningElapsedMs = 0;
        miningInteractionId = "";
        miningPendingOutcome = MiningAttemptOutcome.NONE;
        miningPendingItem = null;
    }

    public boolean isMiningActive() {
        return miningActive;
    }

    public boolean isMiningAt(int x, int y) {
        return miningActive && miningX == x && miningY == y;
    }

    public void updateMining(int deltaMs) {
        if (miningRecoveryElapsedMs >= 0) {
            miningRecoveryElapsedMs += Math.max(0, deltaMs);
            if (miningRecoveryElapsedMs >= gatheringRecoveryDurationMs()) {
                miningRecoveryElapsedMs = -1;
                miningRecoveryOutcome = MiningAttemptOutcome.NONE;
                if (!miningActive) {
                    stopMining();
                    return;
                }
                if (isResourceExhausted(miningX, miningY)) {
                    MapDesignLibrary.CustomGatheringNode exhaustedNode = customGatheringNodes.get(miningInteractionId);
                    boolean exhaustedTree = exhaustedNode != null && exhaustedNode.nodeType() == MapDesignLibrary.GatheringNodeType.TREE;
                    miningMessage = exhaustedTree
                            ? "The tree falls, leaving only a stump. It needs time to regrow."
                            : "The rock crumbles down to bare stone. It needs time to recover.";
                    worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
                    stopMining();
                    return;
                }
            }
            return;
        }

        if (!miningActive || playerCharacter == null) {
            return;
        }

        miningElapsedMs += Math.max(0, deltaMs);

        int intervalMs = gatheringAttemptIntervalMs();
        int strikeStartMs = Math.max(0, intervalMs - gatheringStrikeDurationMs());
        if (miningPendingOutcome == MiningAttemptOutcome.NONE && miningElapsedMs >= strikeStartMs) {
            prepareMiningAttempt();
            if (!miningActive) {
                return;
            }
        }

        if (miningElapsedMs < intervalMs) {
            return;
        }

        miningElapsedMs = 0;
        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(miningInteractionId);
        boolean tree = node != null && node.nodeType() == MapDesignLibrary.GatheringNodeType.TREE;
        GatheringToolType toolType = tree ? GatheringToolType.WOODCUTTING : GatheringToolType.MINING;
        CharacterSkill gatheringSkill = node == null ? CharacterSkill.MINING : node.gatheringSkill();
        int xpReward = node == null ? miningXpReward() : node.gatherXpReward();
        MiningAttemptOutcome outcome = miningPendingOutcome;
        InventorySystem.Item gatheredItem = miningPendingItem;
        miningPendingOutcome = MiningAttemptOutcome.NONE;
        miningPendingItem = null;

        if (outcome == MiningAttemptOutcome.SUCCESS && gatheredItem != null && getInventory().addItem(gatheredItem)) {
            int levelsGained = playerCharacter.addSkillExperience(gatheringSkill, xpReward);
            String outputName = gatheredItem.getName();
            miningMessage = levelsGained > 0
                    ? "You " + (tree ? "cut" : "gather") + " " + outputName + ". " + gatheringSkill.getDisplayName() + " level " + playerCharacter.getSkillLevel(gatheringSkill) + "!"
                    : "You " + (tree ? "cut" : "gather") + " " + outputName + ". " + gatheringSkill.getDisplayName() + " XP "
                    + playerCharacter.getSkillExperience(gatheringSkill)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(gatheringSkill)
                    + ".";
            worldMessageLog.post(WorldMessageLog.Category.SUCCESS, miningMessage);
        } else if (outcome == MiningAttemptOutcome.SUCCESS) {
            miningMessage = "Your inventory is too full to hold any " + (tree ? "wood." : "ore.");
            worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
            stopMining();
            return;
        } else {
            miningMessage = tree
                    ? "Your cuts fail to produce usable wood."
                    : "You chip the rock, but no usable ore breaks free.";
            outcome = MiningAttemptOutcome.FAILURE;
            worldMessageLog.post(WorldMessageLog.Category.FAILURE, miningMessage);
        }

        recordResourceAttempt(miningX, miningY);
        miningRecoveryElapsedMs = 0;
        miningRecoveryOutcome = outcome;
        pendingGatheringImpactEvent = new GatheringImpactEvent(toolType, outcome);

        if (isResourceExhausted(miningX, miningY)) {
            miningMessage = tree
                    ? "The tree falls, leaving only a stump. It needs time to regrow."
                    : "The rock crumbles down to bare stone. It needs time to recover.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
            miningActive = false;
        }
    }

    private void prepareMiningAttempt() {
        MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(miningInteractionId);
        boolean tree = node != null && node.nodeType() == MapDesignLibrary.GatheringNodeType.TREE;
        if (isResourceExhausted(miningX, miningY)) {
            miningMessage = tree ? "Only a stump remains. Give the tree time to regrow."
                    : "The rock is depleted. Give it time to recover.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
            stopMining();
            return;
        }
        InventorySystem.Item item = node == null ? createItemByNameOrId(COPPER_ORE_ITEM_ID) : createGatheredItem(node);
        if (item == null) {
            miningMessage = tree ? "This tree has no usable wood configured."
                    : "This rock has no usable ore configured.";
            worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
            stopMining();
            return;
        }
        if (!getInventory().canAddItem(item)) {
            miningMessage = "Your inventory is too full to hold any " + (tree ? "wood." : "ore.");
            worldMessageLog.post(WorldMessageLog.Category.WARNING, miningMessage);
            stopMining();
            return;
        }
        CharacterSkill skill = node == null ? CharacterSkill.MINING : node.gatheringSkill();
        int level = Math.max(1, playerCharacter.getSkillLevel(skill));
        double chance = Math.min(
                tree ? maxWoodcuttingSuccessChance() : maxMiningSuccessChance(),
                (tree ? baseWoodcuttingSuccessChance() : baseMiningSuccessChance())
                        + level * (tree ? woodcuttingSuccessChancePerLevel() : miningSuccessChancePerLevel()));
        miningPendingOutcome = Math.random() <= chance ? MiningAttemptOutcome.SUCCESS : MiningAttemptOutcome.FAILURE;
        miningPendingItem = item;
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

    public MapDesignLibrary.CustomGatheringNode getCustomGatheringNodeAtPosition(int x, int y) {
        return getCustomGatheringNodeAt(x, y);
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

    public void defeatEnemy(MapEntity enemy) {
        if (enemy == null || enemy.getType() != Library.EntityType.ENEMY || !entities.remove(enemy)) {
            return;
        }
        if (enemy.getEnemySpawnId().isBlank()) {
            removedEntityKeys.add(entityKey(enemy));
            return;
        }
        enemyRespawnStates.put(enemy.getEnemySpawnId(), new EnemyRespawnState(
                enemy.getEnemySpawnId(),
                enemy.getMonster().freshCopy(),
                enemy.getSpawnX(),
                enemy.getSpawnY(),
                enemy.getRoamingAreaId(),
                enemy.getAwarenessRadius(),
                enemy.getMovementIntervalMs(),
                enemy.getRespawnDelayMs(),
                enemy.getRespawnDelayMs()
        ));
    }

    public void updateEnemyRespawns(int deltaMs) {
        if (enemyRespawnStates.isEmpty() || !isDungeonMode()) {
            return;
        }
        List<String> ready = new ArrayList<>();
        for (EnemyRespawnState state : enemyRespawnStates.values()) {
            if (state.respawnDelayMs <= 0) {
                continue;
            }
            state.remainingMs = Math.max(0, state.remainingMs - Math.max(0, deltaMs));
            if (state.remainingMs == 0 && canRespawnEnemy(state)) {
                ready.add(state.spawnId);
            }
        }
        for (String spawnId : ready) {
            EnemyRespawnState state = enemyRespawnStates.remove(spawnId);
            MapEntity enemy = new MapEntity(state.monster.freshCopy(), state.spawnX, state.spawnY)
                    .configureEnemySpawn(
                            state.spawnId,
                            state.spawnX,
                            state.spawnY,
                            state.areaId,
                            state.awarenessRadius,
                            state.movementIntervalMs,
                            state.respawnDelayMs
                    );
            entities.add(enemy);
        }
    }

    private boolean canRespawnEnemy(EnemyRespawnState state) {
        if (dungeonMap == null || !dungeonMap.isWalkable(state.spawnX, state.spawnY)) {
            return false;
        }
        int safeDistance = Math.max(1, state.awarenessRadius);
        if (Math.max(Math.abs(playerX - state.spawnX), Math.abs(playerY - state.spawnY)) <= safeDistance) {
            return false;
        }
        for (MapEntity entity : entities) {
            if (entity.blocksMovement() && entity.isAt(state.spawnX, state.spawnY)) {
                return false;
            }
        }
        return true;
    }

    public Map<String, EnemyRespawnSnapshot> getEnemyRespawnSnapshots() {
        Map<String, EnemyRespawnSnapshot> snapshots = new HashMap<>();
        for (EnemyRespawnState state : enemyRespawnStates.values()) {
            snapshots.put(state.spawnId, new EnemyRespawnSnapshot(
                    state.spawnId,
                    state.monster.getCustomId(),
                    state.spawnX,
                    state.spawnY,
                    state.areaId,
                    state.awarenessRadius,
                    state.movementIntervalMs,
                    state.respawnDelayMs,
                    state.remainingMs
            ));
        }
        return Map.copyOf(snapshots);
    }

    public Map<String, EnemyActiveSnapshot> getEnemyActiveSnapshots() {
        Map<String, EnemyActiveSnapshot> snapshots = new HashMap<>();
        for (MapEntity entity : entities) {
            if (entity.getType() != Library.EntityType.ENEMY || entity.getEnemySpawnId().isBlank()) {
                continue;
            }
            snapshots.put(entity.getEnemySpawnId(), new EnemyActiveSnapshot(
                    entity.getEnemySpawnId(),
                    entity.getX(),
                    entity.getY(),
                    entity.getWorldAiCooldownMs(),
                    entity.isWorldAlerted()
            ));
        }
        return Map.copyOf(snapshots);
    }

    private void restoreEnemySpawnSnapshots(MapRuntimeState state) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - state.lastUpdatedEpochMs());
        Map<String, EnemyRespawnSnapshot> advancedRespawns = new HashMap<>();
        for (EnemyRespawnSnapshot snapshot : state.enemyRespawns().values()) {
            advancedRespawns.put(snapshot.spawnId(), new EnemyRespawnSnapshot(
                    snapshot.spawnId(),
                    snapshot.mobId(),
                    snapshot.spawnX(),
                    snapshot.spawnY(),
                    snapshot.areaId(),
                    snapshot.awarenessRadius(),
                    snapshot.movementIntervalMs(),
                    snapshot.respawnDelayMs(),
                    (int) Math.max(0L, (long) snapshot.remainingMs() - elapsed)
            ));
        }
        restoreEnemyRespawnSnapshots(advancedRespawns);
        entities.removeIf(entity -> entity.getType() == Library.EntityType.ENEMY
                && advancedRespawns.containsKey(entity.getEnemySpawnId()));
        for (EnemyActiveSnapshot snapshot : state.activeEnemies().values()) {
            for (MapEntity entity : entities) {
                if (snapshot.spawnId().equals(entity.getEnemySpawnId())) {
                    entity.setPosition(snapshot.currentX(), snapshot.currentY());
                    entity.setWorldAiCooldownMs(snapshot.aiCooldownMs());
                    entity.setWorldAlerted(snapshot.alerted());
                    break;
                }
            }
        }
    }

    public void restoreEnemyRespawnSnapshots(Map<String, EnemyRespawnSnapshot> snapshots) {
        Map<String, Monster> authoredMonsters = new HashMap<>();
        for (MapEntity entity : entities) {
            if (entity.getMonster() != null && !entity.getEnemySpawnId().isBlank()) {
                authoredMonsters.put(entity.getEnemySpawnId(), entity.getMonster().freshCopy());
            }
        }
        enemyRespawnStates.clear();
        if (snapshots == null) {
            return;
        }
        for (EnemyRespawnSnapshot snapshot : snapshots.values()) {
            Monster monster = authoredMonsters.get(snapshot.spawnId());
            if (monster == null) {
                monster = MapDesignLibrary.createEnemyById(snapshot.mobId());
            }
            if (monster != null) {
                enemyRespawnStates.put(snapshot.spawnId(), new EnemyRespawnState(
                        snapshot.spawnId(),
                        monster,
                        snapshot.spawnX(),
                        snapshot.spawnY(),
                        snapshot.areaId(),
                        snapshot.awarenessRadius(),
                        snapshot.movementIntervalMs(),
                        snapshot.respawnDelayMs(),
                        snapshot.remainingMs()
                ));
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

        if (entity != null && "mineral_rock_basic".equals(entity.getInteractionId())) {
            int frame = Math.max(1, Math.min(3, exhaustionLevel + 1));
            entity.setStaticImage(AssetLoader.loadImage("assets/images/generic/64x64/A_Rock1_Node" + frame + ".png"));
            return;
        }

        if (entity != null) {
            MapDesignLibrary.CustomGatheringNode node = customGatheringNodes.get(entity.getInteractionId());
            if (node != null && node.nodeType() != MapDesignLibrary.GatheringNodeType.FISHING_SPOT) {
                entity.setStaticImage(node.getImageForExhaustion(exhaustionLevel));
                entity.setStaticModelVisible(exhaustionLevel < maxResourceExhaustionLevel());
                return;
            }
        }

        if ("fishing_shoal".equals(getTileInteractionId(x, y))
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

        MapDesignLibrary.CraftingRecipe recipe = findCraftingRecipe(selectedItem, targetItem);
        if (recipe == null) {
            return false;
        }
        craftRecipe(recipe);
        return true;
    }

    public boolean hasSingleIngredientCraftingRecipe(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        return item != null && !matchingSingleIngredientRecipes(item).isEmpty();
    }

    public boolean openSingleIngredientCraftingMenu(int inventoryIndex) {
        InventorySystem.Item item = getInventory().getItem(inventoryIndex);
        List<MapDesignLibrary.CraftingRecipe> recipes = matchingSingleIngredientRecipes(item);
        if (item == null || recipes.isEmpty()) {
            return false;
        }

        List<InteractionSystem.InteractionOption> options = new ArrayList<>();
        for (MapDesignLibrary.CraftingRecipe recipe : recipes) {
            options.add(InteractionSystem.option(
                    recipe.displayName(),
                    () -> craftRecipe(recipe)
            ));
        }
        options.add(InteractionSystem.closeOption("Close"));
        openInteraction(InteractionSystem.prompt(
                "Craft with " + item.getName(),
                "Choose what you want to craft.",
                options.toArray(new InteractionSystem.InteractionOption[0])
        ));
        return true;
    }

    private boolean craftRecipe(MapDesignLibrary.CraftingRecipe recipe) {
        if (recipe == null || playerCharacter == null) {
            return false;
        }
        if (playerCharacter.getSkillLevel(recipe.requiredSkill()) < recipe.requiredLevel()) {
            showCraftingMessage(
                    "Not Ready",
                    "You need " + recipe.requiredSkill().getDisplayName() + " level "
                            + recipe.requiredLevel() + " to make " + recipe.displayName() + "."
            );
            return false;
        }

        InventorySystem.Item primary = createItemByNameOrId(recipe.primaryItemId());
        InventorySystem.Item secondary = recipe.secondaryItemId().isBlank()
                ? null
                : createItemByNameOrId(recipe.secondaryItemId());
        if (primary == null || (!recipe.secondaryItemId().isBlank() && secondary == null)) {
            showCraftingMessage("Recipe Failed", "One or more ingredients for " + recipe.displayName() + " could not be found.");
            return false;
        }
        if (getInventory().countItemNamed(primary.getName()) < recipe.primaryQuantity()
                || secondary != null
                && getInventory().countItemNamed(secondary.getName()) < recipe.secondaryQuantity()) {
            showCraftingMessage("Missing Ingredients", ingredientRequirementText(recipe, primary, secondary));
            return false;
        }

        InventorySystem.Item output = null;
        int stationX = playerX + forwardX(direction);
        int stationY = playerY + forwardY(direction);
        if (recipe.outputsStation()) {
            if (recipe.outputStationType() == null
                    || dungeonMap == null
                    || !dungeonMap.isWalkable(stationX, stationY)
                    || getEntityAt(stationX, stationY) != null
                    || stationX == playerX && stationY == playerY) {
                showCraftingMessage(
                        "No Room",
                        "The tile directly in front of you must be walkable and unoccupied."
                );
                return false;
            }
        } else {
            output = createItemByNameOrId(recipe.outputItemId());
            if (output == null) {
                showCraftingMessage(
                        "Recipe Failed",
                        "The output item for " + recipe.displayName() + " could not be found."
                );
                return false;
            }
            if (!canHoldCraftingOutput(output, recipe, primary, secondary)) {
                showCraftingMessage("Inventory Full", "You do not have room for the crafted item.");
                return false;
            }
        }

        if (recipe.consumePrimary()
                && !getInventory().removeItemQuantityNamed(primary.getName(), recipe.primaryQuantity())) {
            showCraftingMessage("Recipe Failed", "The primary ingredient could not be consumed.");
            return false;
        }
        if (secondary != null
                && recipe.consumeSecondary()
                && !getInventory().removeItemQuantityNamed(secondary.getName(), recipe.secondaryQuantity())) {
            showCraftingMessage("Recipe Failed", "The secondary ingredient could not be consumed.");
            return false;
        }

        String resultName;
        if (recipe.outputsStation()) {
            CraftingStationType stationType = recipe.outputStationType();
            MapEntity station = stationType.createEntity(stationX, stationY)
                    .configureTemporaryStation(
                            UUID.randomUUID().toString(),
                            stationType,
                            recipe.stationLifetimeMs(),
                            false
                    );
            entities.add(station);
            resultName = stationType.getDisplayName();
        } else {
            getInventory().addItem(output);
            resultName = output.getName();
        }

        clearSelectedWorldUseItem();
        int levelsGained = playerCharacter.addSkillExperience(recipe.requiredSkill(), recipe.xpReward());
        String message = recipe.outputsStation()
                ? "You place a temporary " + resultName + " in front of you."
                : "You make " + resultName + ".";
        if (levelsGained > 0) {
            message += " " + recipe.requiredSkill().getDisplayName() + " level "
                    + playerCharacter.getSkillLevel(recipe.requiredSkill()) + "!";
        }
        showCraftingMessage(recipe.displayName(), message);
        return true;
    }

    private boolean canHoldCraftingOutput(
            InventorySystem.Item output,
            MapDesignLibrary.CraftingRecipe recipe,
            InventorySystem.Item primary,
            InventorySystem.Item secondary
    ) {
        if (getInventory().canAddItem(output)) {
            return true;
        }
        return recipe.consumePrimary()
                && getInventory().wouldRemovingQuantityFreeSlot(primary.getName(), recipe.primaryQuantity())
                || secondary != null
                && recipe.consumeSecondary()
                && getInventory().wouldRemovingQuantityFreeSlot(secondary.getName(), recipe.secondaryQuantity());
    }

    private String ingredientRequirementText(
            MapDesignLibrary.CraftingRecipe recipe,
            InventorySystem.Item primary,
            InventorySystem.Item secondary
    ) {
        String text = "You need " + recipe.primaryQuantity() + " " + primary.getName();
        if (secondary != null) {
            text += " and " + recipe.secondaryQuantity() + " " + secondary.getName();
        }
        return text + ".";
    }

    private void showCraftingMessage(String title, String message) {
        openInteraction(InteractionSystem.prompt(
                title,
                message,
                InteractionSystem.closeOption("Close")
        ));
    }

    private List<MapDesignLibrary.CraftingRecipe> matchingSingleIngredientRecipes(InventorySystem.Item item) {
        if (item == null) {
            return List.of();
        }
        List<MapDesignLibrary.CraftingRecipe> matches = new ArrayList<>();
        for (MapDesignLibrary.CraftingRecipe recipe : allCraftingRecipes()) {
            if (recipe.isSingleIngredient() && recipeItemMatches(recipe.primaryItemId(), item)) {
                matches.add(recipe);
            }
        }
        return matches;
    }

    private MapDesignLibrary.CraftingRecipe findCraftingRecipe(
            InventorySystem.Item first,
            InventorySystem.Item second
    ) {
        for (MapDesignLibrary.CraftingRecipe recipe : allCraftingRecipes()) {
            if (recipe.isSingleIngredient()) {
                continue;
            }
            if ((recipeItemMatches(recipe.primaryItemId(), first) && recipeItemMatches(recipe.secondaryItemId(), second))
                    || (recipeItemMatches(recipe.primaryItemId(), second) && recipeItemMatches(recipe.secondaryItemId(), first))) {
                return recipe;
            }
        }
        return null;
    }

    private List<MapDesignLibrary.CraftingRecipe> allCraftingRecipes() {
        Map<String, MapDesignLibrary.CraftingRecipe> recipes = new HashMap<>();
        try {
            for (MapDesignLibrary.CraftingRecipe recipe : MapDesignLibrary.loadSharedContent().craftingRecipes()) {
                recipes.put(recipe.recipeId().toLowerCase(), recipe);
            }
        } catch (java.io.IOException ignored) {
            // The currently loaded map content remains available.
        }
        for (MapDesignLibrary.CraftingRecipe recipe : craftingRecipes.values()) {
            recipes.put(recipe.recipeId().toLowerCase(), recipe);
        }
        return new ArrayList<>(recipes.values());
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
        removeExpiredTemporaryStations();
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
                && (findCookingRecipe(item) != null || RAW_FISH_ITEM_NAME.equalsIgnoreCase(item.getName()));
    }

    private InventorySystem.Item createCookedItem(String rawItemName) {
        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(rawItemName);
        if (recipe != null) {
            InventorySystem.Item item = createItemByNameOrId(recipe.cookedItemId());
            if (item != null) {
                return item;
            }
        }
        return createItemByNameOrId(COOKED_FISH_ITEM_ID);
    }

    private InventorySystem.Item createBurntItem(String rawItemName) {
        MapDesignLibrary.CustomCookingRecipe recipe = findCookingRecipe(rawItemName);
        if (recipe != null) {
            InventorySystem.Item item = createItemByNameOrId(recipe.burntItemId());
            if (item != null) {
                return item;
            }
        }
        InventorySystem.Item cookedFish = createItemByNameOrId(COOKED_FISH_ITEM_ID);
        if (cookedFish == null) {
            return null;
        }
        return new InventorySystem.Item(
                "Burnt Fish",
                InventorySystem.ItemType.MISC,
                InventorySystem.Item.applyBurntTint(cookedFish.getIcon()),
                "",
                0,
                GearMaterial.NONE,
                GearDurability.PERFECT,
                1,
                "A sad blackened fish. It smells like ambition meeting reality."
        );
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

        if (!CraftingSystem.isSmeltableItem(selectedItem)) {
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
        removeExpiredTemporaryStations();
    }

    public boolean isSmeltingActive() {
        return smeltingActive;
    }

    public boolean isSmeltingAt(int x, int y) {
        return smeltingActive && smeltingX == x && smeltingY == y;
    }

    public void updateTemporaryStations(int deltaMs) {
        if (isBattleMode()) {
            return;
        }
        int elapsed = Math.max(0, deltaMs);
        if (elapsed == 0) {
            return;
        }
        for (MapEntity entity : new ArrayList<>(entities)) {
            if (!entity.isTemporaryStation() || !entity.advanceTemporaryStationTimer(elapsed)) {
                continue;
            }
            if (isTemporaryStationInUse(entity)) {
                entity.setTemporaryStationPendingExpiry(true);
            } else {
                entities.remove(entity);
            }
        }
    }

    private void removeExpiredTemporaryStations() {
        entities.removeIf(entity -> entity.isTemporaryStation()
                && entity.getTemporaryStationRemainingMs() <= 0
                && !isTemporaryStationInUse(entity));
    }

    private boolean isTemporaryStationInUse(MapEntity entity) {
        if (entity == null || entity.getTemporaryStationType() == null) {
            return false;
        }
        return switch (entity.getTemporaryStationType()) {
            case CAMPFIRE -> isCookingAt(entity.getX(), entity.getY());
            case FURNACE -> isSmeltingAt(entity.getX(), entity.getY());
            case ANVIL -> false;
        };
    }

    private List<TemporaryStationSnapshot> getTemporaryStationSnapshots() {
        List<TemporaryStationSnapshot> snapshots = new ArrayList<>();
        for (MapEntity entity : entities) {
            if (!entity.isTemporaryStation()) {
                continue;
            }
            snapshots.add(new TemporaryStationSnapshot(
                    entity.getTemporaryStationId(),
                    entity.getTemporaryStationType(),
                    entity.getX(),
                    entity.getY(),
                    entity.getTemporaryStationRemainingMs(),
                    entity.isTemporaryStationPendingExpiry()
            ));
        }
        return List.copyOf(snapshots);
    }

    private void restoreTemporaryStationSnapshots(
            List<TemporaryStationSnapshot> snapshots,
            long unloadedElapsedMs
    ) {
        entities.removeIf(MapEntity::isTemporaryStation);
        for (TemporaryStationSnapshot snapshot : snapshots == null
                ? List.<TemporaryStationSnapshot>of()
                : snapshots) {
            if (snapshot.stationType() == null || snapshot.stationId().isBlank()) {
                continue;
            }
            int remaining = (int) Math.max(
                    0L,
                    (long) snapshot.remainingMs() - Math.max(0L, unloadedElapsedMs)
            );
            if (remaining <= 0) {
                continue;
            }
            entities.add(snapshot.stationType()
                    .createEntity(snapshot.x(), snapshot.y())
                    .configureTemporaryStation(
                            snapshot.stationId(),
                            snapshot.stationType(),
                            remaining,
                            false
                    ));
        }
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

        CraftingSystem.SmeltingRecipe recipe = CraftingSystem.smeltingRecipeFor(selectedItem);
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
        if (!CraftingSystem.isSmithingMaterial(selectedItem)) {
            smithingMaterialName = null;
            smithingMessage = "Use a metal bar from your inventory first, then interact with the anvil.";
            return false;
        }

        smithingMaterialName = selectedItem.getName();
        smithingMessage = "Choose what to make with " + smithingMaterialName + ".";
        return true;
    }

    public List<CraftingSystem.SmithingRecipe> getAvailableSmithingRecipes() {
        return CraftingSystem.smithingRecipesForMaterial(smithingMaterialName);
    }

    public String getSmithingMaterialName() {
        return smithingMaterialName;
    }

    public String getSmithingMessage() {
        return smithingMessage;
    }

    public boolean craftSmithingRecipe(CraftingSystem.SmithingRecipe recipe) {
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

    public static class ResourceNodeState {
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
        MobAreaData mobAreaData = source.getMobAreaData() == null
                ? MobAreaData.blank(source.getWidth(), source.getHeight())
                : source.getMobAreaData().copy();
        return new DungeonMap(
                tiles,
                themes,
                paintData,
                geometryData,
                mobAreaData,
                source.getLightingSettings(),
                source.getLightsView());
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
        clearInteractionsAndStopActivities();
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

    private int gatheringStrikeDurationMs() {
        return Math.min(450, gatheringAttemptIntervalMs());
    }

    private int gatheringRecoveryDurationMs() {
        return 350;
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

    private double baseWoodcuttingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("woodcutting.baseSuccessChance", 0.40));
    }

    private double woodcuttingSuccessChancePerLevel() {
        return Math.max(0.0, GameConfiguration.doubleValue("woodcutting.successChancePerLevel", 0.03));
    }

    private double maxWoodcuttingSuccessChance() {
        return clampChance(GameConfiguration.doubleValue("woodcutting.maxSuccessChance", 0.88));
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

    public record EnemyRespawnSnapshot(
            String spawnId,
            String mobId,
            int spawnX,
            int spawnY,
            String areaId,
            int awarenessRadius,
            int movementIntervalMs,
            int respawnDelayMs,
            int remainingMs
    ) {
        public EnemyRespawnSnapshot {
            spawnId = spawnId == null ? "" : spawnId;
            mobId = mobId == null ? "" : mobId;
            areaId = areaId == null ? "" : areaId;
            awarenessRadius = Math.max(0, awarenessRadius);
            movementIntervalMs = Math.max(250, movementIntervalMs);
            respawnDelayMs = Math.max(0, respawnDelayMs);
            remainingMs = Math.max(0, remainingMs);
        }
    }

    public record EnemyActiveSnapshot(
            String spawnId,
            int currentX,
            int currentY,
            int aiCooldownMs,
            boolean alerted
    ) {
        public EnemyActiveSnapshot {
            spawnId = spawnId == null ? "" : spawnId;
            aiCooldownMs = Math.max(0, aiCooldownMs);
        }
    }

    public static final class EnemyRespawnState {
        private final String spawnId;
        private final Monster monster;
        private final int spawnX;
        private final int spawnY;
        private final String areaId;
        private final int awarenessRadius;
        private final int movementIntervalMs;
        private final int respawnDelayMs;
        private int remainingMs;

        public EnemyRespawnState(
                String spawnId,
                Monster monster,
                int spawnX,
                int spawnY,
                String areaId,
                int awarenessRadius,
                int movementIntervalMs,
                int respawnDelayMs,
                int remainingMs
        ) {
            this.spawnId = spawnId;
            this.monster = monster;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.areaId = areaId;
            this.awarenessRadius = awarenessRadius;
            this.movementIntervalMs = movementIntervalMs;
            this.respawnDelayMs = respawnDelayMs;
            this.remainingMs = remainingMs;
        }
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

    public record TemporaryStationSnapshot(
            String stationId,
            CraftingStationType stationType,
            int x,
            int y,
            int remainingMs,
            boolean pendingExpiry
    ) {
        public TemporaryStationSnapshot {
            stationId = stationId == null ? "" : stationId;
            remainingMs = Math.max(0, remainingMs);
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
            Map<String, EnemyRespawnSnapshot> enemyRespawns,
            Map<String, EnemyActiveSnapshot> activeEnemies,
            long lastUpdatedEpochMs,
            Set<String> discoveredMiniMapTiles,
            List<MapDesignLibrary.MapTrigger> mapTriggers,
            Set<String> firedTriggerIds,
            List<MapDesignLibrary.AuthoredDialogue> authoredDialogues,
            List<MapDesignLibrary.AuthoredQuest> authoredQuests,
            List<MapDesignLibrary.CustomItem> customItems,
            List<MapDesignLibrary.CustomLimb> customLimbs,
            List<MapDesignLibrary.CustomGatheringNode> customGatheringNodes,
            List<MapDesignLibrary.CustomCookingRecipe> customCookingRecipes,
            List<MapDesignLibrary.CraftingRecipe> craftingRecipes,
            List<TemporaryStationSnapshot> temporaryStations
    ) {
        public MapRuntimeState {
            entities = entities == null ? List.of() : List.copyOf(entities);
            tileInteractionIds = tileInteractionIds == null ? Map.of() : Map.copyOf(tileInteractionIds);
            removedEntityKeys = removedEntityKeys == null ? Set.of() : Set.copyOf(removedEntityKeys);
            resourceNodeStates = resourceNodeStates == null ? Map.of() : Map.copyOf(resourceNodeStates);
            enemyRespawns = enemyRespawns == null ? Map.of() : Map.copyOf(enemyRespawns);
            activeEnemies = activeEnemies == null ? Map.of() : Map.copyOf(activeEnemies);
            lastUpdatedEpochMs = lastUpdatedEpochMs <= 0L ? System.currentTimeMillis() : lastUpdatedEpochMs;
            discoveredMiniMapTiles = discoveredMiniMapTiles == null ? Set.of() : Set.copyOf(discoveredMiniMapTiles);
            mapTriggers = mapTriggers == null ? List.of() : List.copyOf(mapTriggers);
            firedTriggerIds = firedTriggerIds == null ? Set.of() : Set.copyOf(firedTriggerIds);
            authoredDialogues = authoredDialogues == null ? List.of() : List.copyOf(authoredDialogues);
            authoredQuests = authoredQuests == null ? List.of() : List.copyOf(authoredQuests);
            customItems = customItems == null ? List.of() : List.copyOf(customItems);
            customLimbs = customLimbs == null ? List.of() : List.copyOf(customLimbs);
            customGatheringNodes = customGatheringNodes == null ? List.of() : List.copyOf(customGatheringNodes);
            customCookingRecipes = customCookingRecipes == null ? List.of() : List.copyOf(customCookingRecipes);
            craftingRecipes = craftingRecipes == null ? List.of() : List.copyOf(craftingRecipes);
            temporaryStations = temporaryStations == null ? List.of() : List.copyOf(temporaryStations);
        }

        public MapRuntimeState(
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
                List<MapDesignLibrary.CraftingRecipe> craftingRecipes
        ) {
            this(mapPath, dungeonMap, entities, hasEntitySnapshot, tileInteractionIds, removedEntityKeys,
                    resourceNodeStates, Map.of(), Map.of(), System.currentTimeMillis(),
                    discoveredMiniMapTiles, mapTriggers, firedTriggerIds,
                    authoredDialogues, authoredQuests, customItems, customLimbs, customGatheringNodes,
                    customCookingRecipes, craftingRecipes, List.of());
        }
    }
}

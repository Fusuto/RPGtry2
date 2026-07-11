package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.content.QuestLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

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
    private final Set<String> removedEntityKeys = new HashSet<>();
    private final Map<String, Integer> questStages = new HashMap<>();
    private final InputBindings inputBindings = new InputBindings();

    private GameMode gameMode = GameMode.START_MENU;

    private int playerX = 1;
    private int playerY = 1;
    private int currentFloor = 1;
    private double movementStartX = 1.0;
    private double movementStartY = 1.0;
    private double movementProgress = 1.0;
    private static final int MOVEMENT_ANIMATION_DURATION_MS = 160;
    private double rotationStartOffsetRadians = 0.0;
    private double rotationProgress = 1.0;
    private static final int ROTATION_ANIMATION_DURATION_MS = 220;
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
    private String fishingMessage = "Cast your line.";
    private String selectedQuestId;
    private InteractionSystem.Interaction activeInteraction;
    private ShopSystem.ShopSession activeShop;
    private int gold = 100;

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
            closeInteraction();
        }

        this.inventoryOpen = inventoryOpen;
    }

    public void toggleInventory() {
        setInventoryOpen(!inventoryOpen);
    }

    public void closeInventory() {
        inventoryOpen = false;
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
        entities.clear();
        tileInteractionIds.clear();
        resetMiniMapDiscovery();

        if (newEntities != null) {
            for (MapEntity entity : newEntities) {
                addEntity(entity);
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
                    movementProgress + (double) Math.max(0, deltaMs) / MOVEMENT_ANIMATION_DURATION_MS
            );
        }

        if (isRotationAnimating()) {
            rotationProgress = Math.min(
                    1.0,
                    rotationProgress + (double) Math.max(0, deltaMs) / ROTATION_ANIMATION_DURATION_MS
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

    public int getGold() {
        return gold;
    }

    public void addGold(int amount) {
        if (amount <= 0) {
            return;
        }

        gold += amount;
    }

    public boolean canSpendGold(int amount) {
        return amount >= 0 && gold >= amount;
    }

    public boolean spendGold(int amount) {
        if (!canSpendGold(amount)) {
            return false;
        }

        gold -= amount;
        return true;
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

    public void startFishing(int x, int y) {
        fishingActive = true;
        fishingX = x;
        fishingY = y;
        fishingElapsedMs = 0;
        fishingMessage = "You cast your line into the shoal.";
    }

    public void stopFishing() {
        fishingActive = false;
        fishingX = -1;
        fishingY = -1;
        fishingElapsedMs = 0;
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

        if (fishingElapsedMs < 2500) {
            return;
        }

        fishingElapsedMs = 0;

        int fishingLevel = Math.max(1, playerCharacter.getSkillLevel(CharacterSkill.FISHING));
        double successChance = Math.min(0.85, 0.35 + fishingLevel * 0.03);

        if (Math.random() <= successChance && getInventory().addItem(org.main.content.ItemLibrary.RAW_FISH.createItem())) {
            int levelsGained = playerCharacter.addSkillExperience(CharacterSkill.FISHING, 18);
            fishingMessage = levelsGained > 0
                    ? "You catch a fish. Fishing level " + playerCharacter.getSkillLevel(CharacterSkill.FISHING) + "!"
                    : "You catch a fish. Fishing XP "
                    + playerCharacter.getSkillExperience(CharacterSkill.FISHING)
                    + "/"
                    + playerCharacter.getSkillExperienceRequired(CharacterSkill.FISHING)
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
}

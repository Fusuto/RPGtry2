package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private final DungeonMap dungeonMap;
    private final List<MapEntity> entities = new ArrayList<>();

    private GameMode gameMode = GameMode.DUNGEON;

    private int playerX = 1;
    private int playerY = 1;

    private boolean miniMapUnlocked = false;

    // 0 = north, 1 = east, 2 = south, 3 = west
    private int direction = 1;

    private BattleEncounter currentEncounter;
    private MapEntity currentEnemyEntity;
    private final InventorySystem.Inventory inventory = new InventorySystem.Inventory();
    private boolean inventoryOpen = false;
    private InteractionSystem.Interaction activeInteraction;
    private ShopSystem.ShopSession activeShop;
    private int gold = 100;

    public enum GameMode {
        DUNGEON,
        BATTLE;

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
        }

        return activeInteraction;
    }

    public boolean hasActiveInteraction() {
        return getActiveInteraction() != null;
    }

    public void openInteraction(InteractionSystem.Interaction interaction) {
        activeInteraction = interaction;
    }

    public void closeInteraction() {
        if (activeInteraction != null) {
            activeInteraction.close();
        }

        activeInteraction = null;
    }

    public InventorySystem.Inventory getInventory() {
        return inventory;
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public void setInventoryOpen(boolean inventoryOpen) {
        this.inventoryOpen = inventoryOpen;
    }

    public void toggleInventory() {
        inventoryOpen = !inventoryOpen;
    }

    public void closeInventory() {
        inventoryOpen = false;
    }

    public GameState(DungeonMap dungeonMap) {
        this.dungeonMap = dungeonMap;
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

    public DungeonMap getDungeonMap() {
        return dungeonMap;
    }

    public List<MapEntity> getEntities() {
        return entities;
    }

    public void addEntity(MapEntity entity) {
        if (entity != null) {
            entities.add(entity);
        }
    }

    public void removeEntity(MapEntity entity) {
        entities.remove(entity);
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

    public int getPlayerX() {
        return playerX;
    }

    public void setPlayerX(int playerX) {
        this.playerX = playerX;
    }

    public int getPlayerY() {
        return playerY;
    }

    public void setPlayerY(int playerY) {
        this.playerY = playerY;
    }

    public void setPlayerPosition(int playerX, int playerY) {
        this.playerX = playerX;
        this.playerY = playerY;
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

        if (activeInteraction != null) {
            activeInteraction.close();
        }

        activeInteraction = null;
    }

    public void closeShop() {
        activeShop = null;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
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
}
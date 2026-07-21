package org.main.core;

import java.util.HashMap;
import java.util.Map;

public class SkillingState {
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

    private boolean cookingActive = false;
    private int cookingX = -1;
    private int cookingY = -1;
    private int cookingElapsedMs = 0;
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

    private final Map<String, GameState.ResourceNodeState> resourceNodeStates = new HashMap<>();
    private final Map<String, GameState.EnemyRespawnState> enemyRespawnStates = new HashMap<>();

    // Fishing Getters/Setters
    public boolean isFishingActive() { return fishingActive; }
    public void setFishingActive(boolean fishingActive) { this.fishingActive = fishingActive; }
    public int getFishingX() { return fishingX; }
    public void setFishingX(int fishingX) { this.fishingX = fishingX; }
    public int getFishingY() { return fishingY; }
    public void setFishingY(int fishingY) { this.fishingY = fishingY; }
    public int getFishingElapsedMs() { return fishingElapsedMs; }
    public void setFishingElapsedMs(int fishingElapsedMs) { this.fishingElapsedMs = fishingElapsedMs; }
    public String getFishingInteractionId() { return fishingInteractionId; }
    public void setFishingInteractionId(String fishingInteractionId) { this.fishingInteractionId = fishingInteractionId; }
    public String getFishingMessage() { return fishingMessage; }
    public void setFishingMessage(String fishingMessage) { this.fishingMessage = fishingMessage; }

    // Mining Getters/Setters
    public boolean isMiningActive() { return miningActive; }
    public void setMiningActive(boolean miningActive) { this.miningActive = miningActive; }
    public int getMiningX() { return miningX; }
    public void setMiningX(int miningX) { this.miningX = miningX; }
    public int getMiningY() { return miningY; }
    public void setMiningY(int miningY) { this.miningY = miningY; }
    public int getMiningElapsedMs() { return miningElapsedMs; }
    public void setMiningElapsedMs(int miningElapsedMs) { this.miningElapsedMs = miningElapsedMs; }
    public String getMiningInteractionId() { return miningInteractionId; }
    public void setMiningInteractionId(String miningInteractionId) { this.miningInteractionId = miningInteractionId; }
    public String getMiningMessage() { return miningMessage; }
    public void setMiningMessage(String miningMessage) { this.miningMessage = miningMessage; }

    // Cooking Getters/Setters
    public boolean isCookingActive() { return cookingActive; }
    public void setCookingActive(boolean cookingActive) { this.cookingActive = cookingActive; }
    public int getCookingX() { return cookingX; }
    public void setCookingX(int cookingX) { this.cookingX = cookingX; }
    public int getCookingY() { return cookingY; }
    public void setCookingY(int cookingY) { this.cookingY = cookingY; }
    public int getCookingElapsedMs() { return cookingElapsedMs; }
    public void setCookingElapsedMs(int cookingElapsedMs) { this.cookingElapsedMs = cookingElapsedMs; }
    public String getCookingItemName() { return cookingItemName; }
    public void setCookingItemName(String cookingItemName) { this.cookingItemName = cookingItemName; }
    public String getCookingMessage() { return cookingMessage; }
    public void setCookingMessage(String cookingMessage) { this.cookingMessage = cookingMessage; }

    // Smelting Getters/Setters
    public boolean isSmeltingActive() { return smeltingActive; }
    public void setSmeltingActive(boolean smeltingActive) { this.smeltingActive = smeltingActive; }
    public int getSmeltingX() { return smeltingX; }
    public void setSmeltingX(int smeltingX) { this.smeltingX = smeltingX; }
    public int getSmeltingY() { return smeltingY; }
    public void setSmeltingY(int smeltingY) { this.smeltingY = smeltingY; }
    public int getSmeltingElapsedMs() { return smeltingElapsedMs; }
    public void setSmeltingElapsedMs(int smeltingElapsedMs) { this.smeltingElapsedMs = smeltingElapsedMs; }
    public String getSmeltingItemName() { return smeltingItemName; }
    public void setSmeltingItemName(String smeltingItemName) { this.smeltingItemName = smeltingItemName; }
    public String getSmeltingMessage() { return smeltingMessage; }
    public void setSmeltingMessage(String smeltingMessage) { this.smeltingMessage = smeltingMessage; }

    // Smithing Getters/Setters
    public String getSmithingMaterialName() { return smithingMaterialName; }
    public void setSmithingMaterialName(String smithingMaterialName) { this.smithingMaterialName = smithingMaterialName; }
    public String getSmithingMessage() { return smithingMessage; }
    public void setSmithingMessage(String smithingMessage) { this.smithingMessage = smithingMessage; }

    // Resource Node States & Enemy Respawns
    public Map<String, GameState.ResourceNodeState> getResourceNodeStates() { return resourceNodeStates; }
    public Map<String, GameState.EnemyRespawnState> getEnemyRespawnStates() { return enemyRespawnStates; }
}

package org.main.core;

import org.main.content.ItemLibrary;
import org.main.content.PlayerClassLibrary;
import org.main.engine.DungeonMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class SaveSystem {
    private static final Path SAVE_PATH = Path.of("data", "saves", "save.properties");

    private SaveSystem() {
    }

    public static Path getSavePath() {
        return SAVE_PATH;
    }

    public static boolean hasSave() {
        return Files.exists(SAVE_PATH);
    }

    public static void save(GameState gameState) throws IOException {
        if (gameState == null || gameState.getPlayerCharacter() == null) {
            return;
        }

        Files.createDirectories(SAVE_PATH.getParent());

        Properties properties = new Properties();
        PlayerCharacter player = gameState.getPlayerCharacter();

        properties.setProperty("player.name", player.getName());
        properties.setProperty("player.class", player.getPlayerClass() == null ? PlayerClassLibrary.WARRIOR.name() : player.getPlayerClass().name());
        properties.setProperty("player.level", String.valueOf(player.getLevel()));
        properties.setProperty("player.classXp", String.valueOf(player.getClassExperience()));
        properties.setProperty("player.statPoints", String.valueOf(player.getAvailableStatPoints()));
        properties.setProperty("player.maxHp", String.valueOf(player.getMaxHp()));
        properties.setProperty("player.currHp", String.valueOf(player.getCurrHp()));
        properties.setProperty("player.portrait", nullToBlank(player.getPortraitPath()));

        for (PlayerStat stat : PlayerStat.values()) {
            properties.setProperty("stat." + stat.name(), String.valueOf(player.getStat(stat)));
        }

        for (CharacterSkill skill : CharacterSkill.values()) {
            properties.setProperty("skill." + skill.name(), String.valueOf(player.getSkillLevel(skill)));
            properties.setProperty("skillXp." + skill.name(), String.valueOf(player.getSkillExperience(skill)));
        }

        saveInventory(properties, player.getInventory());

        properties.setProperty("world.x", String.valueOf(gameState.getPlayerX()));
        properties.setProperty("world.y", String.valueOf(gameState.getPlayerY()));
        properties.setProperty("world.direction", String.valueOf(gameState.getDirection()));
        properties.setProperty("world.floor", String.valueOf(gameState.getCurrentFloor()));
        properties.setProperty("world.gold", String.valueOf(gameState.getGold()));
        properties.setProperty("world.minimapMode", gameState.getMiniMapMode().name());
        properties.setProperty("world.minimapUnlocked", String.valueOf(gameState.isMiniMapUnlocked()));
        properties.setProperty("world.discovered", join(gameState.getDiscoveredMiniMapTileKeys()));
        properties.setProperty("world.removedEntities", join(gameState.getRemovedEntityKeysView()));

        gameState.getQuestStagesView().forEach((id, stage) -> properties.setProperty("quest." + id, String.valueOf(stage)));

        try (OutputStream outputStream = Files.newOutputStream(SAVE_PATH)) {
            properties.store(outputStream, "Wizardry save");
        }
    }

    public static void load(GameState gameState) throws IOException {
        if (gameState == null || !hasSave()) {
            throw new IOException("No saved game found.");
        }

        Properties properties = new Properties();

        try (InputStream inputStream = Files.newInputStream(SAVE_PATH)) {
            properties.load(inputStream);
        }

        PlayerClassLibrary playerClass = readEnum(properties, "player.class", PlayerClassLibrary.class, PlayerClassLibrary.WARRIOR);
        PlayerCharacter player = GameBootstrap.createPlayerCharacter(
                properties.getProperty("player.name", "Player"),
                playerClass
        );

        player.restoreProgress(
                readInt(properties, "player.level", 1),
                readInt(properties, "player.classXp", 0),
                readInt(properties, "player.statPoints", 0)
        );

        for (PlayerStat stat : PlayerStat.values()) {
            player.setStat(stat, readInt(properties, "stat." + stat.name(), player.getStat(stat)));
        }

        for (CharacterSkill skill : CharacterSkill.values()) {
            player.setSkillLevel(skill, readInt(properties, "skill." + skill.name(), player.getSkillLevel(skill)));
            player.setSkillExperience(skill, readInt(properties, "skillXp." + skill.name(), 0));
        }

        loadInventory(properties, player.getInventory());
        player.setCurrHp(readInt(properties, "player.currHp", player.getCurrHp()));

        gameState.setPlayerCharacter(player);
        gameState.setRemovedEntityKeys(readSet(properties.getProperty("world.removedEntities", "")));
        gameState.changeDungeon(
                DungeonMap.testMap(),
                readInt(properties, "world.x", 1),
                readInt(properties, "world.y", 1),
                java.util.List.of()
        );
        gameState.setCurrentFloor(readInt(properties, "world.floor", 1));
        gameState.setDirection(readInt(properties, "world.direction", 1));
        gameState.setMiniMapUnlocked(Boolean.parseBoolean(properties.getProperty("world.minimapUnlocked", "false")));
        gameState.setMiniMapMode(readEnum(properties, "world.minimapMode", GameState.MiniMapMode.class, GameState.MiniMapMode.DISCOVERED));
        gameState.setDiscoveredMiniMapTileKeys(readSet(properties.getProperty("world.discovered", "")));
        restoreGold(gameState, readInt(properties, "world.gold", gameState.getGold()));
        gameState.setQuestStages(readQuestStages(properties));

        GameBootstrap.seedTestContent(gameState);
        player.getInventory().clear();
        loadInventory(properties, player.getInventory());
        player.setCurrHp(readInt(properties, "player.currHp", player.getCurrHp()));
        gameState.setGameMode(GameState.GameMode.DUNGEON);
    }

    private static void saveInventory(Properties properties, InventorySystem.Inventory inventory) {
        if (inventory == null) {
            return;
        }

        for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
            properties.setProperty("inventory." + i, itemKey(inventory.getItem(i)));
        }

        for (InventorySystem.EquipmentSlot slot : InventorySystem.EquipmentSlot.values()) {
            properties.setProperty("equipment." + slot.name(), itemKey(inventory.getEquippedItem(slot)));
        }
    }

    private static void loadInventory(Properties properties, InventorySystem.Inventory inventory) {
        inventory.clear();

        for (int i = 0; i < InventorySystem.Inventory.SLOT_COUNT; i++) {
            ItemLibrary item = readItem(properties.getProperty("inventory." + i, ""));

            if (item != null) {
                inventory.addItemAt(item.createItem(), i);
            }
        }

        for (InventorySystem.EquipmentSlot slot : InventorySystem.EquipmentSlot.values()) {
            ItemLibrary item = readItem(properties.getProperty("equipment." + slot.name(), ""));

            if (item != null) {
                inventory.setEquippedItem(slot, item.createItem());
            }
        }
    }

    private static String itemKey(InventorySystem.Item item) {
        if (item == null) {
            return "";
        }

        ItemLibrary libraryItem = ItemLibrary.fromDisplayName(item.getName());
        return libraryItem == null ? "" : libraryItem.name();
    }

    private static ItemLibrary readItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        try {
            return ItemLibrary.valueOf(itemName);
        } catch (IllegalArgumentException ignored) {
            return ItemLibrary.fromDisplayName(itemName);
        }
    }

    private static void restoreGold(GameState gameState, int gold) {
        if (gameState.getGold() > 0) {
            gameState.spendGold(gameState.getGold());
        }

        gameState.addGold(Math.max(0, gold));
    }

    private static Map<String, Integer> readQuestStages(Properties properties) {
        Map<String, Integer> quests = new HashMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("quest.")) {
                quests.put(key.substring("quest.".length()), readInt(properties, key, 0));
            }
        }

        return quests;
    }

    private static Set<String> readSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .collect(Collectors.toSet());
    }

    private static String join(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        return values.stream().sorted().collect(Collectors.joining(";"));
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, properties.getProperty(key, fallback.name()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}

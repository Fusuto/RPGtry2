package org.main.core;

import org.main.content.ItemLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.engine.DungeonMap;
import org.main.monsters.MonsterType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
        properties.setProperty("player.region", player.getPlayerRegion() == null ? PlayerRegionLibrary.MIDLANDS.name() : player.getPlayerRegion().name());
        properties.setProperty("player.level", String.valueOf(player.getLevel()));
        properties.setProperty("player.classXp", String.valueOf(player.getClassExperience()));
        properties.setProperty("player.statPoints", String.valueOf(player.getAvailableStatPoints()));
        properties.setProperty("player.maxHp", String.valueOf(player.getMaxHp()));
        properties.setProperty("player.currHp", String.valueOf(player.getCurrHp()));
        properties.setProperty("player.portrait", nullToBlank(player.getPortraitPath()));

        for (PlayerStat stat : PlayerStat.values()) {
            properties.setProperty("stat." + stat.name(), String.valueOf(player.getBaseStat(stat)));
        }

        for (CharacterSkill skill : CharacterSkill.values()) {
            properties.setProperty("skill." + skill.name(), String.valueOf(player.getSkillLevel(skill)));
            properties.setProperty("skillXp." + skill.name(), String.valueOf(player.getSkillExperience(skill)));
        }

        saveInventory(properties, player.getInventory());
        saveEquippedLimbs(properties, player);

        properties.setProperty("world.x", String.valueOf(gameState.getPlayerX()));
        properties.setProperty("world.y", String.valueOf(gameState.getPlayerY()));
        properties.setProperty("world.direction", String.valueOf(gameState.getDirection()));
        properties.setProperty("world.floor", String.valueOf(gameState.getCurrentFloor()));
        properties.setProperty("world.gold", String.valueOf(gameState.getGold()));
        properties.setProperty("world.minimapMode", gameState.getMiniMapMode().name());
        properties.setProperty("world.minimapUnlocked", String.valueOf(gameState.isMiniMapUnlocked()));
        properties.setProperty("world.discovered", join(gameState.getDiscoveredMiniMapTileKeys()));
        properties.setProperty("world.removedEntities", join(gameState.getRemovedEntityKeysView()));
        saveDungeonMap(properties, gameState.getDungeonMap());
        saveTileInteractions(properties, gameState);

        gameState.getQuestStagesView().forEach((id, stage) -> properties.setProperty("quest." + id, String.valueOf(stage)));

        try (OutputStream outputStream = Files.newOutputStream(SAVE_PATH)) {
            properties.store(outputStream, "Aether save");
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

        PlayerRegionLibrary playerRegion = readEnum(properties, "player.region", PlayerRegionLibrary.class, PlayerRegionLibrary.MIDLANDS);
        PlayerCharacter player = GameBootstrap.createPlayerCharacter(
                properties.getProperty("player.name", "Player"),
                playerRegion
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
        loadEquippedLimbs(properties, player);
        player.setCurrHp(readInt(properties, "player.currHp", player.getCurrHp()));

        int currentFloor = readInt(properties, "world.floor", 1);
        DungeonMap dungeonMap = loadDungeonMap(properties);
        int playerX = readInt(properties, "world.x", 1);
        int playerY = readInt(properties, "world.y", 1);

        if (dungeonMap.isOutOfBounds(playerX, playerY)) {
            playerX = 1;
            playerY = 1;
        }

        gameState.setPlayerCharacter(player);
        gameState.setRemovedEntityKeys(readSet(properties.getProperty("world.removedEntities", "")));
        gameState.changeDungeon(
                dungeonMap,
                playerX,
                playerY,
                java.util.List.of()
        );
        loadTileInteractions(properties, gameState);
        gameState.setCurrentFloor(currentFloor);
        gameState.setDirection(readInt(properties, "world.direction", 1));
        gameState.setMiniMapUnlocked(Boolean.parseBoolean(properties.getProperty("world.minimapUnlocked", "false")));
        gameState.setMiniMapMode(readEnum(properties, "world.minimapMode", GameState.MiniMapMode.class, GameState.MiniMapMode.DISCOVERED));
        gameState.setDiscoveredMiniMapTileKeys(readSet(properties.getProperty("world.discovered", "")));
        restoreGold(gameState, readInt(properties, "world.gold", gameState.getGold()));
        gameState.setQuestStages(readQuestStages(properties));

        if (currentFloor <= 1) {
            GameBootstrap.seedTestContent(gameState);
        }

        player.getInventory().clear();
        loadInventory(properties, player.getInventory());
        loadEquippedLimbs(properties, player);
        player.setCurrHp(readInt(properties, "player.currHp", player.getCurrHp()));
        gameState.setGameMode(GameState.GameMode.DUNGEON);
    }

    private static void saveDungeonMap(Properties properties, DungeonMap dungeonMap) {
        if (dungeonMap == null) {
            return;
        }

        properties.setProperty("world.map.width", String.valueOf(dungeonMap.getWidth()));
        properties.setProperty("world.map.height", String.valueOf(dungeonMap.getHeight()));

        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            StringBuilder tileRow = new StringBuilder();
            StringBuilder themeRow = new StringBuilder();

            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                if (x > 0) {
                    tileRow.append(",");
                    themeRow.append(",");
                }

                tileRow.append(dungeonMap.getTile(x, y).name());
                themeRow.append(dungeonMap.getEnvironmentThemeIndex(x, y));
            }

            properties.setProperty("world.map.tiles." + y, tileRow.toString());
            properties.setProperty("world.map.themes." + y, themeRow.toString());
        }
    }

    private static DungeonMap loadDungeonMap(Properties properties) {
        int width = readInt(properties, "world.map.width", 0);
        int height = readInt(properties, "world.map.height", 0);

        if (width <= 0 || height <= 0) {
            return DungeonMap.testMap();
        }

        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];

        for (int y = 0; y < height; y++) {
            List<String> tileValues = splitCsv(properties.getProperty("world.map.tiles." + y, ""));
            List<String> themeValues = splitCsv(properties.getProperty("world.map.themes." + y, ""));

            for (int x = 0; x < width; x++) {
                tiles[y][x] = readTileType(tileValues, x);
                themes[y][x] = readListInt(themeValues, x, 0);
            }
        }

        return new DungeonMap(tiles, themes);
    }

    private static void saveTileInteractions(Properties properties, GameState gameState) {
        Map<String, String> interactions = gameState.getTileInteractionIdsView();
        properties.setProperty("world.tileInteractions.count", String.valueOf(interactions.size()));

        int index = 0;
        for (Map.Entry<String, String> entry : interactions.entrySet()) {
            properties.setProperty("world.tileInteractions." + index, entry.getKey() + "|" + entry.getValue());
            index++;
        }
    }

    private static void loadTileInteractions(Properties properties, GameState gameState) {
        int count = readInt(properties, "world.tileInteractions.count", 0);

        for (int i = 0; i < count; i++) {
            String value = properties.getProperty("world.tileInteractions." + i, "");
            int separator = value.indexOf('|');

            if (separator <= 0 || separator >= value.length() - 1) {
                continue;
            }

            String[] coordinates = value.substring(0, separator).split(",");
            if (coordinates.length != 2) {
                continue;
            }

            try {
                int x = Integer.parseInt(coordinates[0]);
                int y = Integer.parseInt(coordinates[1]);
                gameState.setTileInteractionId(x, y, value.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                // Ignore malformed save entries.
            }
        }
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
            InventorySystem.Item item = readInventoryItem(properties.getProperty("inventory." + i, ""));

            if (item != null) {
                inventory.addItemAt(item, i);
            }
        }

        for (InventorySystem.EquipmentSlot slot : InventorySystem.EquipmentSlot.values()) {
            InventorySystem.Item item = readInventoryItem(properties.getProperty("equipment." + slot.name(), ""));

            if (item != null) {
                inventory.setEquippedItem(slot, item);
            }
        }
    }

    private static void saveEquippedLimbs(Properties properties, PlayerCharacter player) {
        for (LimbSlot slot : LimbSlot.values()) {
            properties.setProperty("limb." + slot.name(), limbKey(player.getEquippedLimb(slot)));
        }
    }

    private static void loadEquippedLimbs(Properties properties, PlayerCharacter player) {
        for (LimbSlot slot : LimbSlot.values()) {
            LimbItem limb = readLimb(properties.getProperty("limb." + slot.name(), ""));

            if (limb != null) {
                player.equipLimb(limb);
            }
        }
    }

    private static String itemKey(InventorySystem.Item item) {
        if (item == null) {
            return "";
        }

        if (item instanceof LimbItem limb) {
            return limbKey(limb);
        }

        ItemLibrary libraryItem = ItemLibrary.fromDisplayName(item.getName());
        return libraryItem == null ? "" : libraryItem.name();
    }

    private static InventorySystem.Item readInventoryItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        if (itemName.startsWith("LIMB|")) {
            return readLimb(itemName);
        }

        try {
            return ItemLibrary.valueOf(itemName).createItem();
        } catch (IllegalArgumentException ignored) {
            ItemLibrary item = ItemLibrary.fromDisplayName(itemName);
            return item == null ? null : item.createItem();
        }
    }

    private static String limbKey(LimbItem limb) {
        if (limb == null || limb.getMonsterType() == null) {
            return "";
        }

        return "LIMB|"
                + limb.getMonsterType().name()
                + "|"
                + limb.getLimbSlot().name()
                + "|"
                + limb.getCondition().name();
    }

    private static LimbItem readLimb(String value) {
        if (value == null || !value.startsWith("LIMB|")) {
            return null;
        }

        String[] parts = value.split("\\|");
        if (parts.length < 4) {
            return null;
        }

        try {
            MonsterType monsterType = MonsterType.valueOf(parts[1]);
            LimbSlot slot = LimbSlot.valueOf(parts[2]);
            GearDurability condition = GearDurability.valueOf(parts[3]);
            return ButcherySystem.recreateLimb(monsterType, slot, condition);
        } catch (IllegalArgumentException ignored) {
            return null;
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

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .toList();
    }

    private static Library.TileType readTileType(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return Library.TileType.WALL;
        }

        try {
            return Library.TileType.valueOf(values.get(index));
        } catch (IllegalArgumentException ignored) {
            return Library.TileType.WALL;
        }
    }

    private static int readListInt(List<String> values, int index, int fallback) {
        if (index < 0 || index >= values.size()) {
            return fallback;
        }

        try {
            return Integer.parseInt(values.get(index));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

package org.main.core;

import org.main.content.MapDesignLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
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
        properties.setProperty("world.mode", gameState.isOpenWorldActive() ? "OPEN_WORLD" : "STANDALONE");
        properties.setProperty("world.globalX", String.valueOf(gameState.getGlobalPlayerX()));
        properties.setProperty("world.globalY", String.valueOf(gameState.getGlobalPlayerY()));
        properties.setProperty(
                "world.manifestPath",
                mapDesignPathForSave(gameState.getCurrentWorldManifestPath())
        );
        properties.setProperty("world.direction", String.valueOf(gameState.getDirection()));
        properties.setProperty("world.floor", String.valueOf(gameState.getCurrentFloor()));
        properties.setProperty("world.gold", String.valueOf(gameState.getGold()));
        properties.setProperty("world.minimapMode", gameState.getMiniMapMode().name());
        properties.setProperty("world.minimapUnlocked", String.valueOf(gameState.isMiniMapUnlocked()));
        properties.setProperty("world.discovered", join(gameState.getDiscoveredMiniMapTileKeys()));
        properties.setProperty("world.removedEntities", join(gameState.getRemovedEntityKeysView()));
        properties.setProperty("world.spokenAuthoredDialogues", join(gameState.getSpokenAuthoredDialogueIdsView()));
        properties.setProperty("world.mapDesignPath", mapDesignPathForSave(gameState.getCurrentMapDesignPath()));
        saveDungeonMap(properties, gameState.getDungeonMap());
        saveTileInteractions(properties, gameState);
        saveMapRuntimeStates(properties, gameState.getMapRuntimeStatesView());
        saveOpenWorldRuntimeStates(properties, gameState.getOpenWorldRuntimeStatesView());

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
        Path mapDesignPath = readMapDesignPath(properties.getProperty("world.mapDesignPath", ""));
        Path worldManifestPath = readMapDesignPath(properties.getProperty("world.manifestPath", ""));
        int globalX = readInt(properties, "world.globalX", playerX);
        int globalY = readInt(properties, "world.globalY", playerY);
        Map<String, GameState.MapRuntimeState> runtimeStates = loadMapRuntimeStates(properties);
        if (runtimeStates.isEmpty() && mapDesignPath != null) {
            runtimeStates.put(mapDesignPathForSave(mapDesignPath), new GameState.MapRuntimeState(
                    mapDesignPath,
                    dungeonMap,
                    List.of(),
                    false,
                    loadTileInteractionMap(properties, "world.tileInteractions."),
                    readSet(properties.getProperty("world.removedEntities", "")),
                    Map.of(),
                    readSet(properties.getProperty("world.discovered", "")),
                    List.of(),
                    Set.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }
        gameState.setMapRuntimeStates(runtimeStates);
        gameState.setOpenWorldRuntimeStates(loadOpenWorldRuntimeStates(properties));

        if (dungeonMap.isOutOfBounds(playerX, playerY)) {
            playerX = 1;
            playerY = 1;
        }

        gameState.setPlayerCharacter(player);
        gameState.setRemovedEntityKeys(readSet(properties.getProperty("world.removedEntities", "")));
        boolean loadedAuthoredMap = false;
        if (worldManifestPath != null
                && "OPEN_WORLD".equalsIgnoreCase(properties.getProperty("world.mode", ""))) {
            try {
                gameState.restoreOrLoadMap(worldManifestPath, globalX, globalY);
                loadedAuthoredMap = true;
            } catch (IOException ignored) {
                loadedAuthoredMap = false;
            }
        } else if (mapDesignPath != null) {
            try {
                gameState.restoreOrLoadMap(mapDesignPath, playerX, playerY);
                loadedAuthoredMap = true;
            } catch (IOException ignored) {
                loadedAuthoredMap = false;
            }
        }
        if (!loadedAuthoredMap) {
            gameState.changeDungeon(
                    dungeonMap,
                    playerX,
                    playerY,
                    java.util.List.of()
            );
        }
        loadTileInteractions(properties, gameState);
        gameState.setCurrentFloor(currentFloor);
        gameState.setDirection(readInt(properties, "world.direction", 1));
        gameState.setMiniMapUnlocked(Boolean.parseBoolean(properties.getProperty("world.minimapUnlocked", "false")));
        gameState.setMiniMapMode(readEnum(properties, "world.minimapMode", GameState.MiniMapMode.class, GameState.MiniMapMode.DISCOVERED));
        gameState.setDiscoveredMiniMapTileKeys(readSet(properties.getProperty("world.discovered", "")));
        gameState.setSpokenAuthoredDialogueIds(readSet(properties.getProperty("world.spokenAuthoredDialogues", "")));
        restoreGold(gameState, readInt(properties, "world.gold", gameState.getGold()));
        gameState.setQuestStages(readQuestStages(properties));

        if (currentFloor <= 1 && !loadedAuthoredMap) {
            GameBootstrap.seedTestContent(gameState);
        }

        player.getInventory().clear();
        loadInventory(properties, player.getInventory());
        loadEquippedLimbs(properties, player);
        player.setCurrHp(readInt(properties, "player.currHp", player.getCurrHp()));
        gameState.setGameMode(GameState.GameMode.DUNGEON);
    }

    private static void saveDungeonMap(Properties properties, DungeonMap dungeonMap) {
        saveDungeonMap(properties, "world.map.", dungeonMap);
    }

    private static void saveDungeonMap(Properties properties, String prefix, DungeonMap dungeonMap) {
        if (dungeonMap == null) {
            return;
        }

        properties.setProperty(prefix + "width", String.valueOf(dungeonMap.getWidth()));
        properties.setProperty(prefix + "height", String.valueOf(dungeonMap.getHeight()));

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

            properties.setProperty(prefix + "tiles." + y, tileRow.toString());
            properties.setProperty(prefix + "themes." + y, themeRow.toString());
        }
        saveMapPaint(properties, prefix + "paint.", dungeonMap.getPaintData());
        saveMapGeometry(properties, prefix + "geometry.", dungeonMap.getGeometryData());
    }

    private static DungeonMap loadDungeonMap(Properties properties) {
        return loadDungeonMap(properties, "world.map.");
    }

    private static DungeonMap loadDungeonMap(Properties properties, String prefix) {
        int width = readInt(properties, prefix + "width", 0);
        int height = readInt(properties, prefix + "height", 0);

        if (width <= 0 || height <= 0) {
            return DungeonMap.testMap();
        }

        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];

        for (int y = 0; y < height; y++) {
            List<String> tileValues = splitCsv(properties.getProperty(prefix + "tiles." + y, ""));
            List<String> themeValues = splitCsv(properties.getProperty(prefix + "themes." + y, ""));

            for (int x = 0; x < width; x++) {
                tiles[y][x] = readTileType(tileValues, x);
                themes[y][x] = readListInt(themeValues, x, 0);
            }
        }

        return new DungeonMap(
                tiles,
                themes,
                loadMapPaint(properties, prefix + "paint.", width, height),
                loadMapGeometry(properties, prefix + "geometry.", width, height)
        );
    }

    private static void saveMapPaint(Properties properties, String prefix, MapPaintData paintData) {
        if (paintData == null) {
            paintData = MapPaintData.blank(1, 1);
        }

        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
            String layerPrefix = prefix + layer.name().toLowerCase(java.util.Locale.ROOT) + ".";
            String[][] rows = paintData.copyLayer(layer);
            for (int y = 0; y < rows.length; y++) {
                properties.setProperty(layerPrefix + y, joinPaintRow(rows[y]));
            }
        }
    }

    private static MapPaintData loadMapPaint(Properties properties, String prefix, int width, int height) {
        return MapPaintData.of(
                width,
                height,
                loadPaintLayer(properties, prefix + "floor.", width, height),
                loadPaintLayer(properties, prefix + "wall.", width, height),
                loadPaintLayer(properties, prefix + "door.", width, height),
                loadPaintLayer(properties, prefix + "roof.", width, height)
        );
    }

    private static String[][] loadPaintLayer(Properties properties, String prefix, int width, int height) {
        String[][] layer = new String[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            String[] values = properties.getProperty(prefix + y, "").split(",", -1);
            for (int x = 0; x < width; x++) {
                layer[y][x] = x < values.length ? values[x].trim() : "";
            }
        }
        return layer;
    }

    private static void saveMapGeometry(Properties properties, String prefix, MapGeometryData geometryData) {
        if (geometryData == null) {
            geometryData = MapGeometryData.blank(1, 1);
        }

        int[][] rows = geometryData.copyHeightLevels();
        for (int y = 0; y < rows.length; y++) {
            List<String> values = new ArrayList<>();
            for (int value : rows[y]) {
                values.add(String.valueOf(MapGeometryData.clampHeightLevel(value)));
            }
            properties.setProperty(prefix + "height." + y, String.join(",", values));
        }
    }

    private static MapGeometryData loadMapGeometry(Properties properties, String prefix, int width, int height) {
        int[][] heightLevels = new int[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            List<String> values = splitCsv(properties.getProperty(prefix + "height." + y, ""));
            for (int x = 0; x < width; x++) {
                heightLevels[y][x] = MapGeometryData.clampHeightLevel(
                        readListInt(values, x, MapGeometryData.DEFAULT_HEIGHT_LEVEL)
                );
            }
        }
        return MapGeometryData.of(width, height, heightLevels);
    }

    private static String joinPaintRow(String[] row) {
        if (row == null || row.length == 0) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (String value : row) {
            values.add(value == null ? "" : value.trim());
        }
        return String.join(",", values);
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

    private static void saveMapRuntimeStates(Properties properties, Map<String, GameState.MapRuntimeState> states) {
        if (states == null || states.isEmpty()) {
            properties.setProperty("mapState.count", "0");
            return;
        }

        properties.setProperty("mapState.count", String.valueOf(states.size()));
        int index = 0;
        for (Map.Entry<String, GameState.MapRuntimeState> entry : states.entrySet()) {
            GameState.MapRuntimeState state = entry.getValue();
            String prefix = "mapState." + index + ".";
            properties.setProperty(prefix + "key", entry.getKey());
            properties.setProperty(prefix + "path", mapDesignPathForSave(state.mapPath()));
            properties.setProperty(prefix + "removed", join(state.removedEntityKeys()));
            properties.setProperty(prefix + "discovered", join(state.discoveredMiniMapTiles()));
            properties.setProperty(prefix + "firedTriggers", join(state.firedTriggerIds()));
            saveDungeonMap(properties, prefix + "map.", state.dungeonMap());
            saveTileInteractionMap(properties, prefix + "tileInteraction.", state.tileInteractionIds());
            saveResourceNodeSnapshots(properties, prefix + "resource.", state.resourceNodeStates());
            saveMapTriggers(properties, prefix + "trigger.", state.mapTriggers());
            index++;
        }
    }

    private static Map<String, GameState.MapRuntimeState> loadMapRuntimeStates(Properties properties) {
        int count = readInt(properties, "mapState.count", 0);
        Map<String, GameState.MapRuntimeState> states = new HashMap<>();

        for (int i = 0; i < count; i++) {
            String prefix = "mapState." + i + ".";
            Path path = readMapDesignPath(properties.getProperty(prefix + "path", ""));
            String key = properties.getProperty(prefix + "key", mapDesignPathForSave(path));
            DungeonMap map = loadDungeonMap(properties, prefix + "map.");
            states.put(key, new GameState.MapRuntimeState(
                    path,
                    map,
                    List.of(),
                    false,
                    loadTileInteractionMap(properties, prefix + "tileInteraction."),
                    readSet(properties.getProperty(prefix + "removed", "")),
                    loadResourceNodeSnapshots(properties, prefix + "resource."),
                    readSet(properties.getProperty(prefix + "discovered", "")),
                    loadMapTriggers(properties, prefix + "trigger."),
                    readSet(properties.getProperty(prefix + "firedTriggers", "")),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        return states;
    }

    private static void saveOpenWorldRuntimeStates(
            Properties properties,
            Map<String, GameState.OpenWorldRuntimeState> states
    ) {
        properties.setProperty("openWorldState.count", String.valueOf(states == null ? 0 : states.size()));
        if (states == null) {
            return;
        }

        int stateIndex = 0;
        for (Map.Entry<String, GameState.OpenWorldRuntimeState> entry : states.entrySet()) {
            GameState.OpenWorldRuntimeState state = entry.getValue();
            String prefix = "openWorldState." + stateIndex + ".";
            properties.setProperty(prefix + "key", entry.getKey());
            properties.setProperty(prefix + "manifestPath", mapDesignPathForSave(state.manifestPath()));
            properties.setProperty(prefix + "resumeGlobalX", String.valueOf(state.resumeGlobalX()));
            properties.setProperty(prefix + "resumeGlobalY", String.valueOf(state.resumeGlobalY()));
            properties.setProperty(prefix + "chunk.count", String.valueOf(state.chunkStates().size()));

            int chunkIndex = 0;
            for (Map.Entry<WorldManifestLibrary.ChunkCoordinate, OpenWorldSession.PersistedChunkState> chunkEntry
                    : state.chunkStates().entrySet()) {
                String chunkPrefix = prefix + "chunk." + chunkIndex + ".";
                WorldManifestLibrary.ChunkCoordinate coordinate = chunkEntry.getKey();
                OpenWorldSession.PersistedChunkState chunk = chunkEntry.getValue();
                properties.setProperty(chunkPrefix + "x", String.valueOf(coordinate.x()));
                properties.setProperty(chunkPrefix + "y", String.valueOf(coordinate.y()));
                properties.setProperty(chunkPrefix + "lastUpdatedEpochMs", String.valueOf(chunk.lastUpdatedEpochMs()));
                properties.setProperty(chunkPrefix + "removed", join(chunk.removedEntityKeys()));
                properties.setProperty(chunkPrefix + "discovered", join(chunk.discoveredTiles()));
                properties.setProperty(chunkPrefix + "firedTriggers", join(chunk.firedTriggerIds()));
                saveDungeonMap(properties, chunkPrefix + "map.", chunk.map());
                saveOpenWorldEntities(properties, chunkPrefix + "entity.", chunk.entities());
                saveTileInteractionMap(properties, chunkPrefix + "tileInteraction.", chunk.tileInteractions());
                saveResourceNodeSnapshots(properties, chunkPrefix + "resource.", chunk.resourceNodeStates());
                saveMapTriggers(properties, chunkPrefix + "trigger.", chunk.triggers());
                chunkIndex++;
            }
            stateIndex++;
        }
    }

    private static Map<String, GameState.OpenWorldRuntimeState> loadOpenWorldRuntimeStates(Properties properties) {
        int count = Math.max(0, readInt(properties, "openWorldState.count", 0));
        Map<String, GameState.OpenWorldRuntimeState> states = new HashMap<>();
        for (int stateIndex = 0; stateIndex < count; stateIndex++) {
            String prefix = "openWorldState." + stateIndex + ".";
            Path manifestPath = readMapDesignPath(properties.getProperty(prefix + "manifestPath", ""));
            if (manifestPath == null) {
                continue;
            }
            String key = properties.getProperty(prefix + "key", mapDesignPathForSave(manifestPath));
            int resumeGlobalX = readInt(properties, prefix + "resumeGlobalX", 1);
            int resumeGlobalY = readInt(properties, prefix + "resumeGlobalY", 1);
            int chunkCount = Math.max(0, readInt(properties, prefix + "chunk.count", 0));
            Map<WorldManifestLibrary.ChunkCoordinate, OpenWorldSession.PersistedChunkState> chunks = new HashMap<>();
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                String chunkPrefix = prefix + "chunk." + chunkIndex + ".";
                WorldManifestLibrary.ChunkCoordinate coordinate = new WorldManifestLibrary.ChunkCoordinate(
                        readInt(properties, chunkPrefix + "x", 0),
                        readInt(properties, chunkPrefix + "y", 0)
                );
                chunks.put(coordinate, new OpenWorldSession.PersistedChunkState(
                        loadDungeonMap(properties, chunkPrefix + "map."),
                        loadOpenWorldEntities(properties, chunkPrefix + "entity."),
                        loadTileInteractionMap(properties, chunkPrefix + "tileInteraction."),
                        readSet(properties.getProperty(chunkPrefix + "removed", "")),
                        loadResourceNodeSnapshots(properties, chunkPrefix + "resource."),
                        readSet(properties.getProperty(chunkPrefix + "discovered", "")),
                        loadMapTriggers(properties, chunkPrefix + "trigger."),
                        readSet(properties.getProperty(chunkPrefix + "firedTriggers", "")),
                        readLong(properties, chunkPrefix + "lastUpdatedEpochMs", System.currentTimeMillis())
                ));
            }
            states.put(key, new GameState.OpenWorldRuntimeState(
                    manifestPath,
                    resumeGlobalX,
                    resumeGlobalY,
                    chunks
            ));
        }
        return states;
    }

    private static void saveOpenWorldEntities(
            Properties properties,
            String prefix,
            List<OpenWorldSession.PersistedEntityState> entities
    ) {
        properties.setProperty(prefix + "count", String.valueOf(entities == null ? 0 : entities.size()));
        if (entities == null) {
            return;
        }
        for (int i = 0; i < entities.size(); i++) {
            OpenWorldSession.PersistedEntityState entity = entities.get(i);
            String entityPrefix = prefix + i + ".";
            properties.setProperty(entityPrefix + "name", encode(entity.name()));
            properties.setProperty(entityPrefix + "type", entity.type().name());
            properties.setProperty(entityPrefix + "x", String.valueOf(entity.x()));
            properties.setProperty(entityPrefix + "y", String.valueOf(entity.y()));
            properties.setProperty(entityPrefix + "interactionId", encode(entity.interactionId()));
            properties.setProperty(entityPrefix + "talkSoundPath", encode(entity.talkSoundPath()));
            properties.setProperty(entityPrefix + "blocksMovement", String.valueOf(entity.blocksMovement()));
            properties.setProperty(entityPrefix + "renderOnWall", String.valueOf(entity.renderOnWall()));
            properties.setProperty(entityPrefix + "visualScale", String.valueOf(entity.visualScale()));
            properties.setProperty(entityPrefix + "item", itemKey(entity.item()));
        }
    }

    private static List<OpenWorldSession.PersistedEntityState> loadOpenWorldEntities(
            Properties properties,
            String prefix
    ) {
        int count = Math.max(0, readInt(properties, prefix + "count", 0));
        List<OpenWorldSession.PersistedEntityState> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String entityPrefix = prefix + i + ".";
            Library.EntityType type;
            try {
                type = Library.EntityType.valueOf(
                        properties.getProperty(entityPrefix + "type", Library.EntityType.ITEM.name()));
            } catch (IllegalArgumentException ignored) {
                type = Library.EntityType.ITEM;
            }
            entities.add(new OpenWorldSession.PersistedEntityState(
                    decode(properties.getProperty(entityPrefix + "name", "")),
                    type,
                    readInt(properties, entityPrefix + "x", 0),
                    readInt(properties, entityPrefix + "y", 0),
                    decode(properties.getProperty(entityPrefix + "interactionId", "")),
                    decode(properties.getProperty(entityPrefix + "talkSoundPath", "")),
                    Boolean.parseBoolean(properties.getProperty(entityPrefix + "blocksMovement", "false")),
                    Boolean.parseBoolean(properties.getProperty(entityPrefix + "renderOnWall", "false")),
                    readDouble(properties, entityPrefix + "visualScale", 1.0),
                    readInventoryItem(properties.getProperty(entityPrefix + "item", ""))
            ));
        }
        return entities;
    }

    private static void saveMapTriggers(
            Properties properties,
            String prefix,
            List<MapDesignLibrary.MapTrigger> triggers
    ) {
        properties.setProperty(prefix + "count", String.valueOf(triggers == null ? 0 : triggers.size()));
        if (triggers == null) {
            return;
        }

        for (int i = 0; i < triggers.size(); i++) {
            MapDesignLibrary.MapTrigger trigger = triggers.get(i);
            String triggerPrefix = prefix + i + ".";
            properties.setProperty(triggerPrefix + "id", trigger.id());
            properties.setProperty(triggerPrefix + "x", String.valueOf(trigger.x()));
            properties.setProperty(triggerPrefix + "y", String.valueOf(trigger.y()));
            properties.setProperty(triggerPrefix + "fireMode", trigger.fireMode().name());
            properties.setProperty(triggerPrefix + "oneShot", String.valueOf(trigger.oneShot()));
            properties.setProperty(triggerPrefix + "requiredQuestId", trigger.requiredQuestId());
            properties.setProperty(triggerPrefix + "requiredQuestStage", String.valueOf(trigger.requiredQuestStage()));
            properties.setProperty(triggerPrefix + "action.count", String.valueOf(trigger.actions().size()));
            for (int actionIndex = 0; actionIndex < trigger.actions().size(); actionIndex++) {
                MapDesignLibrary.TriggerAction action = trigger.actions().get(actionIndex);
                String actionPrefix = triggerPrefix + "action." + actionIndex + ".";
                properties.setProperty(actionPrefix + "type", action.type().name());
                properties.setProperty(actionPrefix + "targetX", String.valueOf(action.targetX()));
                properties.setProperty(actionPrefix + "targetY", String.valueOf(action.targetY()));
            }
        }
    }

    private static List<MapDesignLibrary.MapTrigger> loadMapTriggers(Properties properties, String prefix) {
        int count = readInt(properties, prefix + "count", 0);
        List<MapDesignLibrary.MapTrigger> triggers = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String triggerPrefix = prefix + i + ".";
            String id = properties.getProperty(triggerPrefix + "id", "");
            if (id.isBlank()) {
                continue;
            }

            int actionCount = readInt(properties, triggerPrefix + "action.count", 0);
            List<MapDesignLibrary.TriggerAction> actions = new ArrayList<>();
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                String actionPrefix = triggerPrefix + "action." + actionIndex + ".";
                MapDesignLibrary.TriggerActionType type = readEnum(
                        properties,
                        actionPrefix + "type",
                        MapDesignLibrary.TriggerActionType.class,
                        MapDesignLibrary.TriggerActionType.CLOSE_DOOR
                );
                actions.add(new MapDesignLibrary.TriggerAction(
                        type,
                        readInt(properties, actionPrefix + "targetX", 0),
                        readInt(properties, actionPrefix + "targetY", 0)
                ));
            }

            triggers.add(new MapDesignLibrary.MapTrigger(
                    id,
                    readInt(properties, triggerPrefix + "x", 0),
                    readInt(properties, triggerPrefix + "y", 0),
                    readEnum(
                            properties,
                            triggerPrefix + "fireMode",
                            MapDesignLibrary.TriggerFireMode.class,
                            MapDesignLibrary.TriggerFireMode.ON_ENTRY
                    ),
                    Boolean.parseBoolean(properties.getProperty(triggerPrefix + "oneShot", "true")),
                    properties.getProperty(triggerPrefix + "requiredQuestId", ""),
                    readInt(properties, triggerPrefix + "requiredQuestStage", 0),
                    actions
            ));
        }

        return triggers;
    }

    private static void saveTileInteractionMap(Properties properties, String prefix, Map<String, String> interactions) {
        properties.setProperty(prefix + "count", String.valueOf(interactions == null ? 0 : interactions.size()));
        if (interactions == null) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, String> entry : interactions.entrySet()) {
            properties.setProperty(prefix + index, entry.getKey() + "|" + entry.getValue());
            index++;
        }
    }

    private static Map<String, String> loadTileInteractionMap(Properties properties, String prefix) {
        int count = readInt(properties, prefix + "count", 0);
        Map<String, String> interactions = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String value = properties.getProperty(prefix + i, "");
            int separator = value.indexOf('|');
            if (separator > 0 && separator < value.length() - 1) {
                interactions.put(value.substring(0, separator), value.substring(separator + 1));
            }
        }
        return interactions;
    }

    private static void saveResourceNodeSnapshots(
            Properties properties,
            String prefix,
            Map<String, GameState.ResourceNodeSnapshot> resources
    ) {
        properties.setProperty(prefix + "count", String.valueOf(resources == null ? 0 : resources.size()));
        if (resources == null) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, GameState.ResourceNodeSnapshot> entry : resources.entrySet()) {
            GameState.ResourceNodeSnapshot state = entry.getValue();
            String itemPrefix = prefix + index + ".";
            properties.setProperty(itemPrefix + "key", entry.getKey());
            properties.setProperty(itemPrefix + "exhaustion", String.valueOf(state.exhaustionLevel()));
            properties.setProperty(itemPrefix + "attempts", String.valueOf(state.attemptsSinceLastExhaustionRoll()));
            properties.setProperty(itemPrefix + "respawn", String.valueOf(state.respawnRemainingMs()));
            index++;
        }
    }

    private static Map<String, GameState.ResourceNodeSnapshot> loadResourceNodeSnapshots(Properties properties, String prefix) {
        int count = readInt(properties, prefix + "count", 0);
        Map<String, GameState.ResourceNodeSnapshot> resources = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String itemPrefix = prefix + i + ".";
            String key = properties.getProperty(itemPrefix + "key", "");
            if (key.isBlank()) {
                continue;
            }
            resources.put(key, new GameState.ResourceNodeSnapshot(
                    readInt(properties, itemPrefix + "exhaustion", 0),
                    readInt(properties, itemPrefix + "attempts", 0),
                    readInt(properties, itemPrefix + "respawn", 0)
            ));
        }
        return resources;
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
            LimbItem limb = readCustomLimb(properties.getProperty("limb." + slot.name(), ""));

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

        if (item.isStackable()) {
            String itemId = sharedItemId(item.getName());
            if (!itemId.isBlank()) {
                return "STACK|" + itemId + "|" + item.getQuantity();
            }
        }

        String customItemKey = customItemKey(item);
        if (!customItemKey.isBlank()) {
            return customItemKey;
        }

        return CraftingSystem.isSmithingResult(item) ? "RECIPE|" + item.getName() : "";
    }

    private static String customItemKey(InventorySystem.Item item) {
        if (item == null) {
            return "";
        }

        try {
            for (MapDesignLibrary.CustomItem customItem : MapDesignLibrary.loadSharedContent().customItems()) {
                if (customItem.displayName().equalsIgnoreCase(item.getName())) {
                    return "CUSTOM_ITEM|" + customItem.itemId();
                }
            }
        } catch (IOException ignored) {
            return "";
        }

        return "";
    }

    private static InventorySystem.Item readInventoryItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        if (itemName.startsWith("CUSTOM_LIMB|")) {
            return readCustomLimb(itemName);
        }

        if (itemName.startsWith("CUSTOM_ITEM|")) {
            return readCustomItem(itemName.substring("CUSTOM_ITEM|".length()));
        }

        if (itemName.startsWith("RECIPE|")) {
            return CraftingSystem.createSmithingResultByDisplayName(itemName.substring("RECIPE|".length()));
        }

        if (itemName.startsWith("STACK|")) {
            String[] parts = itemName.split("\\|");
            if (parts.length >= 3) {
                try {
                    int quantity = Math.max(1, Integer.parseInt(parts[2]));
                    InventorySystem.Item created = readSharedItem(parts[1]);
                    if (created.isStackable()) {
                        created.addQuantity(quantity - 1);
                        return created;
                    }
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    return null;
                }
            }
        }

        return readSharedItem(itemName);
    }

    private static InventorySystem.Item readCustomItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        try {
            for (MapDesignLibrary.CustomItem customItem : MapDesignLibrary.loadSharedContent().customItems()) {
                if (itemId.equals(customItem.itemId())) {
                    return customItem.createItem();
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }

    private static InventorySystem.Item readSharedItem(String itemIdOrName) {
        if (itemIdOrName == null || itemIdOrName.isBlank()) {
            return null;
        }
        try {
            for (MapDesignLibrary.CustomItem item : MapDesignLibrary.loadSharedContent().customItems()) {
                if (itemIdOrName.equalsIgnoreCase(item.itemId())
                        || itemIdOrName.equalsIgnoreCase(item.displayName())) {
                    return item.createItem();
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static String sharedItemId(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        try {
            for (MapDesignLibrary.CustomItem item : MapDesignLibrary.loadSharedContent().customItems()) {
                if (displayName.equalsIgnoreCase(item.displayName())) {
                    return item.itemId();
                }
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    private static String limbKey(LimbItem limb) {
        if (limb == null) {
            return "";
        }

        return customLimbKey(limb);
    }

    private static String customLimbKey(LimbItem limb) {
        StringBuilder stats = new StringBuilder();
        for (PlayerStat stat : PlayerStat.values()) {
            if (!stats.isEmpty()) {
                stats.append(',');
            }
            stats.append(stat.name()).append('=').append(limb.getBaseStatsView().getOrDefault(stat, 0));
        }

        return "CUSTOM_LIMB|"
                + encode(limb.getName())
                + "|"
                + limb.getLimbSlot().name()
                + "|"
                + limb.getCondition().name()
                + "|"
                + encode(limb.getIconPath())
                + "|"
                + encode(stats.toString())
                + "|"
                + encode(limb.getExamineText())
                + "|"
                + encode(limb.getPaperDollSourcePath())
                + "|"
                + encode(limb.getSourceCreatureName())
                + "|"
                + encode(limb.getSourceCreatureId());
    }

    private static LimbItem readCustomLimb(String value) {
        if (value == null || !value.startsWith("CUSTOM_LIMB|")) {
            return null;
        }

        String[] parts = value.split("\\|");
        if (parts.length < 6) {
            return null;
        }

        try {
            String name = decode(parts[1]);
            LimbSlot slot = LimbSlot.valueOf(parts[2]);
            GearDurability condition = GearDurability.valueOf(parts[3]);
            String iconPath = decode(parts[4]);
            EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
            for (String statPart : decode(parts[5]).split(",")) {
                String[] statValue = statPart.split("=");
                if (statValue.length == 2) {
                    stats.put(PlayerStat.valueOf(statValue[0]), Integer.parseInt(statValue[1]));
                }
            }
            String examineText = parts.length >= 7 ? decode(parts[6]) : "";
            String paperDollSourcePath = parts.length >= 8 ? decode(parts[7]) : "";
            String sourceCreatureName = parts.length >= 9 ? decode(parts[8]) : "";
            String sourceCreatureId = parts.length >= 10 ? decode(parts[9]) : "";
            return new LimbItem(name, sourceCreatureId, sourceCreatureName, slot, stats, List.of(), condition, iconPath, examineText, paperDollSourcePath);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void restoreGold(GameState gameState, int gold) {
        if (gold > 0 && gameState.getGold() == 0) {
            gameState.addGold(gold);
        }
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

    private static String mapDesignPathForSave(Path path) {
        if (path == null) {
            return "";
        }
        Path absolutePath = path.toAbsolutePath().normalize();
        Path resourceRoot = Path.of("src", "main", "resources").toAbsolutePath().normalize();
        if (absolutePath.startsWith(resourceRoot)) {
            return resourceRoot.relativize(absolutePath).toString().replace('\\', '/');
        }
        return MapDesignLibrary.resourcePathForMap(path);
    }

    private static Path readMapDesignPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        Path path = Path.of(value);
        if (Files.isRegularFile(path)) {
            return path;
        }

        Path mapFolderPath = MapDesignLibrary.MAP_FOLDER.resolve(value).normalize();
        if (Files.isRegularFile(mapFolderPath)) {
            return mapFolderPath;
        }
        if (value.replace('\\', '/').startsWith("assets/")) {
            Path resourcePath = Path.of("src", "main", "resources").resolve(value).normalize();
            if (Files.isRegularFile(resourcePath)) {
                return resourcePath;
            }
        }
        return path;
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

    private static long readLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double readDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        } catch (RuntimeException ignored) {
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

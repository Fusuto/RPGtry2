package org.main.core;

import org.main.content.MapDesignLibrary;
import org.main.content.CharacterModelDefinition;
import org.main.content.ThemeLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.content.WorldManifestLibrary.ChunkCoordinate;
import org.main.content.WorldManifestLibrary.WorldManifest;
import org.main.engine.DungeonMap;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.MapLight;
import org.main.engine.MapLightingSettings;
import org.main.engine.MapGeometryData;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OpenWorldSession {
    private static final Logger LOGGER = Logger.getLogger(OpenWorldSession.class.getName());
    public static final int WINDOW_RADIUS = 1;
    public static final int WINDOW_DIAMETER = WINDOW_RADIUS * 2 + 1;

    private final Path manifestPath;
    private final WorldManifest manifest;
    private final Map<ChunkCoordinate, ChunkState> chunkStates = new ConcurrentHashMap<>();
    private final Map<ChunkCoordinate, Future<?>> chunkPrefetches = new ConcurrentHashMap<>();
    private final Map<ChunkCoordinate, Future<?>> terrainPreparationTasks = new ConcurrentHashMap<>();
    private final Map<ChunkCoordinate, PreparedTerrainWindow> preparedTerrainWindows = new ConcurrentHashMap<>();
    private final AtomicLong preparedTerrainRevision = new AtomicLong();
    private final Set<String> prefetchedModelPaths = ConcurrentHashMap.newKeySet();
    private final Set<CharacterModelDefinition> prefetchedCharacterModels = ConcurrentHashMap.newKeySet();
    private final Set<String> prefetchedModelPathsView = java.util.Collections.unmodifiableSet(prefetchedModelPaths);
    private final Set<CharacterModelDefinition> prefetchedCharacterModelsView =
            java.util.Collections.unmodifiableSet(prefetchedCharacterModels);
    private final ExecutorService chunkPrefetchExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Aether-Chunk-Prefetch");
        thread.setDaemon(true);
        return thread;
    });
    private final Object chunkLoadLock = new Object();
    private final Set<ChunkCoordinate> loadedCoordinates = new HashSet<>();
    private MapDesignLibrary.AuthoredContent worldContent;
    private ChunkCoordinate center;
    private List<EnvironmentTheme> currentEnvironmentThemes = List.of();
    private int resumeGlobalX;
    private int resumeGlobalY;
    private boolean windowMaterialized;

    public OpenWorldSession(Path manifestPath, WorldManifest manifest) {
        this.manifestPath = manifestPath;
        this.manifest = manifest;
    }

    public static OpenWorldSession load(Path manifestPath) throws IOException {
        return new OpenWorldSession(manifestPath, WorldManifestLibrary.load(manifestPath));
    }

    public Path manifestPath() {
        return manifestPath;
    }

    public WorldManifest manifest() {
        return manifest;
    }

    public ChunkCoordinate center() {
        return center;
    }

    public Path centerChunkPath() {
        if (center == null) {
            return null;
        }
        String chunkPath = manifest.chunks().get(center);
        return WorldManifestLibrary.resolveChunkPath(manifestPath, chunkPath);
    }

    public MapDesignLibrary.MapDesign centerDesign() {
        ChunkState state = center == null ? null : chunkStates.get(center);
        return state == null ? null : state.design;
    }

    public List<EnvironmentTheme> currentEnvironmentThemes() {
        return currentEnvironmentThemes;
    }

    public int resumeGlobalX() {
        return resumeGlobalX;
    }

    public int resumeGlobalY() {
        return resumeGlobalY;
    }

    public void setResumePosition(int globalX, int globalY) {
        resumeGlobalX = globalX;
        resumeGlobalY = globalY;
    }

    public boolean isWindowMaterialized() {
        return windowMaterialized;
    }

    public int globalXForWindow(int windowX) {
        return center == null
                ? windowX
                : manifest.globalX(new ChunkCoordinate(center.x() - WINDOW_RADIUS, center.y()), windowX);
    }

    public int globalYForWindow(int windowY) {
        return center == null
                ? windowY
                : manifest.globalY(new ChunkCoordinate(center.x(), center.y() - WINDOW_RADIUS), windowY);
    }

    public int windowXForGlobal(int globalX) {
        return center == null
                ? globalX
                : globalX - (center.x() - WINDOW_RADIUS) * manifest.chunkWidth();
    }

    public int windowYForGlobal(int globalY) {
        return center == null
                ? globalY
                : globalY - (center.y() - WINDOW_RADIUS) * manifest.chunkHeight();
    }

    public boolean containsGlobalTile(int globalX, int globalY) {
        return manifest.chunks().containsKey(manifest.chunkForGlobal(globalX, globalY));
    }

    public WindowState openAtGlobal(int globalX, int globalY) throws IOException {
        ChunkCoordinate requestedCenter = manifest.chunkForGlobal(globalX, globalY);
        if (!manifest.chunks().containsKey(requestedCenter)) {
            throw new IOException("World position resolves to missing chunk " + requestedCenter + ".");
        }
        if (windowMaterialized) {
            throw new IOException("Current world window must be captured before it is reopened.");
        }
        center = requestedCenter;
        setResumePosition(globalX, globalY);
        WindowState state = materializeWindow();
        prefetchAround(center);
        return state.withPlayer(windowXForGlobal(globalX), windowYForGlobal(globalY));
    }

    public RecenterResult recenterIfNeeded(int playerWindowX, int playerWindowY, WindowCapture capture) throws IOException {
        int globalX = globalXForWindow(playerWindowX);
        int globalY = globalYForWindow(playerWindowY);
        ChunkCoordinate playerChunk = manifest.chunkForGlobal(globalX, globalY);
        if (playerChunk.equals(center)) {
            return null;
        }
        if (!manifest.chunks().containsKey(playerChunk)) {
            return null;
        }

        captureWindow(capture);
        center = playerChunk;
        setResumePosition(globalX, globalY);
        WindowState state = materializeWindow()
                .withPlayer(windowXForGlobal(globalX), windowYForGlobal(globalY));
        prefetchAround(center);
        return new RecenterResult(state, globalX, globalY);
    }

    public void captureWindow(WindowCapture capture) {
        if (!windowMaterialized || capture == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int chunkWidth = manifest.chunkWidth();
        int chunkHeight = manifest.chunkHeight();
        Set<ChunkCoordinate> tileDirtyChunks = dirtyTileChunks(capture.map(), chunkWidth, chunkHeight);

        for (ChunkCoordinate coordinate : loadedCoordinates) {
            ChunkState state = chunkStates.get(coordinate);
            if (state == null) {
                continue;
            }
            int offsetX = windowOffsetX(coordinate);
            int offsetY = windowOffsetY(coordinate);
            if (tileDirtyChunks.contains(coordinate)) {
                for (int y = 0; y < chunkHeight; y++) {
                    for (int x = 0; x < chunkWidth; x++) {
                        state.map.setTile(x, y, capture.map().getTile(offsetX + x, offsetY + y));
                    }
                }
            }
            state.entities.clear();
            state.tileInteractions.clear();
            state.resourceNodeStates.clear();
            state.enemyRespawns.clear();
            state.discoveredTiles.clear();
            state.triggers.clear();
            state.firedTriggerIds.clear();
            state.removedEntityKeys.clear();
            state.lastUpdatedEpochMs = now;
        }

        for (MapEntity entity : capture.entities()) {
            ChunkCoordinate coordinate = entity.getEnemySpawnId().isBlank()
                    ? coordinateForWindow(entity.getX(), entity.getY())
                    : coordinateForWindow(entity.getSpawnX(), entity.getSpawnY());
            ChunkState state = chunkStates.get(coordinate);
            if (state == null || !loadedCoordinates.contains(coordinate)) {
                continue;
            }
            MapEntity localEntity = entity.copy();
            localEntity.leaveWorldWindow(windowOffsetX(coordinate), windowOffsetY(coordinate));
            localEntity.translateWorldPosition(
                    -windowOffsetX(coordinate),
                    -windowOffsetY(coordinate)
            );
            state.entities.add(localEntity);
        }
        distributeStringMap(capture.tileInteractions(), (state, key, value) -> state.tileInteractions.put(key, value));
        distributeResourceMap(capture.resourceNodeStates());
        distributeEnemyRespawns(capture.enemyRespawns());
        distributeCoordinateSet(capture.discoveredTiles(), (state, key) -> state.discoveredTiles.add(key));
        distributeCoordinateSet(capture.removedEntityKeys(), (state, key) -> state.removedEntityKeys.add(key));

        for (MapDesignLibrary.MapTrigger trigger : capture.triggers()) {
            ChunkCoordinate coordinate = coordinateForWindow(trigger.x(), trigger.y());
            ChunkState state = chunkStates.get(coordinate);
            if (state == null || !loadedCoordinates.contains(coordinate)) {
                continue;
            }
            int offsetX = windowOffsetX(coordinate);
            int offsetY = windowOffsetY(coordinate);
            List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                    .map(action -> new MapDesignLibrary.TriggerAction(
                            action.type(),
                            action.targetX() - offsetX,
                            action.targetY() - offsetY
                    ))
                    .toList();
            state.triggers.add(new MapDesignLibrary.MapTrigger(
                    stripRuntimeTriggerPrefix(trigger.id()),
                    trigger.x() - offsetX,
                    trigger.y() - offsetY,
                    trigger.fireMode(),
                    trigger.oneShot(),
                    trigger.requiredQuestId(),
                    trigger.requiredQuestProgress(),
                    actions
            ));
        }
        for (String runtimeId : capture.firedTriggerIds()) {
            ParsedTriggerId parsed = parseRuntimeTriggerId(runtimeId);
            ChunkState state = parsed == null ? null : chunkStates.get(parsed.coordinate());
            if (state != null) {
                state.firedTriggerIds.add(parsed.localId());
            }
        }

        windowMaterialized = false;
    }

    private Set<ChunkCoordinate> dirtyTileChunks(DungeonMap map, int chunkWidth, int chunkHeight) {
        if (map == null || center == null) {
            return Set.of();
        }
        Set<ChunkCoordinate> result = new HashSet<>();
        for (DungeonMap.TileCoordinate tile : map.dirtyTilesView()) {
            int relativeChunkX = Math.floorDiv(tile.x(), Math.max(1, chunkWidth));
            int relativeChunkY = Math.floorDiv(tile.y(), Math.max(1, chunkHeight));
            result.add(new ChunkCoordinate(
                    center.x() + relativeChunkX - WINDOW_RADIUS,
                    center.y() + relativeChunkY - WINDOW_RADIUS));
        }
        return result;
    }

    public Map<ChunkCoordinate, PersistedChunkState> snapshotChunkStates(WindowCapture capture) {
        captureWindow(capture);
        Map<ChunkCoordinate, PersistedChunkState> snapshots = new LinkedHashMap<>();
        for (Map.Entry<ChunkCoordinate, ChunkState> entry : chunkStates.entrySet()) {
            ChunkState state = entry.getValue();
            snapshots.put(entry.getKey(), state.persisted());
        }
        return Map.copyOf(snapshots);
    }

    public Map<ChunkCoordinate, PersistedChunkState> snapshotChunkStates() {
        Map<ChunkCoordinate, PersistedChunkState> snapshots = new LinkedHashMap<>();
        for (Map.Entry<ChunkCoordinate, ChunkState> entry : chunkStates.entrySet()) {
            snapshots.put(entry.getKey(), entry.getValue().persisted());
        }
        return Map.copyOf(snapshots);
    }

    public void restoreChunkStates(Map<ChunkCoordinate, PersistedChunkState> snapshots) throws IOException {
        chunkStates.clear();
        if (snapshots == null) {
            return;
        }
        for (Map.Entry<ChunkCoordinate, PersistedChunkState> entry : snapshots.entrySet()) {
            ChunkState state = loadChunk(entry.getKey(), false);
            if (state == null) {
                continue;
            }
            PersistedChunkState snapshot = entry.getValue();
            if (snapshot.map() != null) {
                state.map = copyDungeonMap(snapshot.map());
            }
            restoreEntities(state, snapshot.entities());
            state.tileInteractions.clear();
            state.tileInteractions.putAll(snapshot.tileInteractions());
            state.removedEntityKeys.clear();
            state.removedEntityKeys.addAll(snapshot.removedEntityKeys());
            state.resourceNodeStates.clear();
            state.resourceNodeStates.putAll(snapshot.resourceNodeStates());
            state.enemyRespawns.clear();
            state.enemyRespawns.putAll(snapshot.enemyRespawns());
            state.discoveredTiles.clear();
            state.discoveredTiles.addAll(snapshot.discoveredTiles());
            state.firedTriggerIds.clear();
            state.firedTriggerIds.addAll(snapshot.firedTriggerIds());
            state.lastUpdatedEpochMs = snapshot.lastUpdatedEpochMs();
            chunkStates.put(entry.getKey(), state);
        }
    }

    private static void restoreEntities(ChunkState state, List<PersistedEntityState> snapshots) {
        List<MapEntity> authoredEntities = new ArrayList<>(state.entities);
        Set<MapEntity> claimed = new HashSet<>();
        state.entities.clear();
        for (PersistedEntityState snapshot : snapshots == null ? List.<PersistedEntityState>of() : snapshots) {
            MapEntity entity = null;
            boolean authoredEntity = false;
            if (snapshot.temporaryStationType() != null && !snapshot.temporaryStationId().isBlank()) {
                entity = snapshot.temporaryStationType()
                        .createEntity(snapshot.x(), snapshot.y())
                        .configureTemporaryStation(
                                snapshot.temporaryStationId(),
                                snapshot.temporaryStationType(),
                                snapshot.temporaryStationRemainingMs(),
                                snapshot.temporaryStationPendingExpiry()
                        );
            } else if (snapshot.type() == Library.EntityType.CORPSE && !snapshot.monsterId().isBlank()) {
                var monster = MapDesignLibrary.createEnemyById(snapshot.monsterId());
                if (monster != null) {
                    CorpseState corpse = new CorpseState(
                            monster,
                            snapshot.corpseSourceSpawnId(),
                            snapshot.corpseItems());
                    corpse.restoreButcheryState(
                            snapshot.corpseButcheryAttempted(),
                            snapshot.corpseStatus());
                    entity = new MapEntity(corpse, snapshot.x(), snapshot.y());
                    entity.finishCorpseDeathAnimation();
                }
            } else if (snapshot.item() != null) {
                entity = new MapEntity(snapshot.item(), snapshot.x(), snapshot.y());
            } else {
                for (MapEntity candidate : authoredEntities) {
                    boolean stableEnemyMatch = snapshot.type() == Library.EntityType.ENEMY
                            && !snapshot.enemySpawnId().isBlank()
                            && snapshot.enemySpawnId().equals(candidate.getEnemySpawnId());
                    boolean stableContentMatch = !snapshot.contentId().isBlank()
                            && snapshot.contentId().equals(candidate.getContentId());
                    if (!claimed.contains(candidate)
                            && (stableEnemyMatch || stableContentMatch)) {
                        entity = candidate;
                        authoredEntity = true;
                        claimed.add(candidate);
                        break;
                    }
                }
                if (entity == null && snapshot.type() == Library.EntityType.ENEMY
                        && !snapshot.monsterId().isBlank()) {
                    var monster = MapDesignLibrary.createEnemyById(snapshot.monsterId());
                    if (monster != null) {
                        entity = new MapEntity(monster, snapshot.x(), snapshot.y());
                    }
                }
                if (entity == null) {
                    entity = new MapEntity(snapshot.name(), snapshot.type(), snapshot.x(), snapshot.y());
                }
            }
            entity.setPosition(snapshot.x(), snapshot.y());
            entity.setWorldFacingYawDegrees(snapshot.worldFacingYawDegrees());
            if (!authoredEntity) {
                entity.setInteractionId(snapshot.interactionId());
                entity.withContentId(snapshot.contentId());
                entity.withQuestIds(snapshot.questIds());
                entity.setTalkSoundPath(snapshot.talkSoundPath());
            }
            entity.blocksMovement(snapshot.blocksMovement());
            entity.renderOnWall(snapshot.renderOnWall());
            entity.withVisualScale(snapshot.visualScale());
            if (!snapshot.staticModelPath().isBlank()) {
                entity.withStaticModel(snapshot.staticModelPath());
                entity.withStaticModelTransform(
                        snapshot.staticModelOffsetX(),
                        snapshot.staticModelOffsetY(),
                        snapshot.staticModelOffsetZ(),
                        snapshot.staticModelYawDegrees(),
                        snapshot.staticModelPitchDegrees(),
                        snapshot.staticModelRollDegrees(),
                        snapshot.staticModelScaleMultiplier()
                );
                entity.withStaticModelBrightness(snapshot.staticModelBrightness());
                entity.setStaticModelVisible(snapshot.staticModelVisible());
            }
            if (entity.getMonster() != null && !snapshot.enemySpawnId().isBlank()) {
                entity.configureEnemySpawn(
                        snapshot.enemySpawnId(),
                        snapshot.spawnX(),
                        snapshot.spawnY(),
                        snapshot.areaId(),
                        snapshot.awarenessRadius(),
                        snapshot.movementIntervalMs(),
                        snapshot.respawnDelayMs()
                );
                entity.setWorldAiCooldownMs(snapshot.aiCooldownMs());
                entity.setWorldAlerted(snapshot.alerted());
            }
            state.entities.add(entity);
        }
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value;
    }

    private PreparedTerrainWindow buildPreparedTerrainWindow(ChunkCoordinate requestedCenter) throws IOException {
        int width = manifest.chunkWidth() * WINDOW_DIAMETER;
        int height = manifest.chunkHeight() * WINDOW_DIAMETER;
        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];
        int[][] heights = new int[height][width];
        String[][] mobAreas = new String[height][width];
        Map<MapPaintData.Layer, String[][]> paintLayers = new LinkedHashMap<>();
        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
            paintLayers.put(layer, new String[height][width]);
        }
        for (Library.TileType[] row : tiles) {
            java.util.Arrays.fill(row, Library.TileType.WALL);
        }
        List<EnvironmentTheme> environmentThemes = new ArrayList<>();
        List<MapLight> lights = new ArrayList<>();
        Map<ChunkCoordinate, PreparedChunkRevision> revisions = new HashMap<>();
        MapLightingSettings lightingSettings = MapLightingSettings.defaultSettings();

        for (int dy = -WINDOW_RADIUS; dy <= WINDOW_RADIUS; dy++) {
            for (int dx = -WINDOW_RADIUS; dx <= WINDOW_RADIUS; dx++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        requestedCenter.x() + dx, requestedCenter.y() + dy);
                if (!manifest.chunks().containsKey(coordinate)) {
                    continue;
                }
                ChunkState chunk = loadChunk(coordinate, false);
                if (chunk == null) {
                    continue;
                }
                revisions.put(coordinate, PreparedChunkRevision.of(chunk.map));
                int offsetX = (dx + WINDOW_RADIUS) * manifest.chunkWidth();
                int offsetY = (dy + WINDOW_RADIUS) * manifest.chunkHeight();
                int primaryIndex = themeIndex(environmentThemes, chunk.design.primaryTheme());
                int alternateIndex = themeIndex(environmentThemes, chunk.design.alternateTheme());
                if (coordinate.equals(requestedCenter)) {
                    lightingSettings = chunk.map.getLightingSettings();
                }
                for (int y = 0; y < manifest.chunkHeight(); y++) {
                    for (int x = 0; x < manifest.chunkWidth(); x++) {
                        int windowX = offsetX + x;
                        int windowY = offsetY + y;
                        tiles[windowY][windowX] = chunk.map.getTile(x, y);
                        themes[windowY][windowX] = chunk.map.getEnvironmentThemeIndex(x, y) == 0
                                ? primaryIndex : alternateIndex;
                        heights[windowY][windowX] = chunk.map.getHeightLevel(x, y);
                        mobAreas[windowY][windowX] = chunk.map.getMobAreaId(x, y);
                        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                            paintLayers.get(layer)[windowY][windowX] =
                                    chunk.map.getPaintBrushId(layer, x, y);
                        }
                    }
                }
                for (MapLight light : chunk.map.getLightsView()) {
                    lights.add(light.translated(
                            offsetX, offsetY,
                            "chunk[" + coordinate.x() + "," + coordinate.y() + "]::"));
                }
            }
        }
        DungeonMap map = new DungeonMap(
                tiles,
                themes,
                MapPaintData.of(
                        width,
                        height,
                        paintLayers.get(MapPaintData.Layer.FLOOR),
                        paintLayers.get(MapPaintData.Layer.WALL),
                        paintLayers.get(MapPaintData.Layer.DOOR),
                        paintLayers.get(MapPaintData.Layer.ROOF)),
                MapGeometryData.of(width, height, heights),
                MobAreaData.of(width, height, mobAreas),
                lightingSettings,
                lights);
        return new PreparedTerrainWindow(
                map, List.copyOf(environmentThemes), Map.copyOf(revisions));
    }

    private WindowState materializeWindow() throws IOException {
        int width = manifest.chunkWidth() * WINDOW_DIAMETER;
        int height = manifest.chunkHeight() * WINDOW_DIAMETER;
        PreparedTerrainWindow preparedTerrain = preparedTerrainWindows.remove(center);
        if (preparedTerrain != null) {
            preparedTerrainRevision.incrementAndGet();
        }
        if (preparedTerrain != null && !preparedLightingStillValid(preparedTerrain, center)) {
            preparedTerrain = null;
        }
        boolean usePreparedTerrain = preparedTerrain != null;
        Library.TileType[][] tiles = usePreparedTerrain ? null : new Library.TileType[height][width];
        int[][] themes = usePreparedTerrain ? null : new int[height][width];
        int[][] heights = usePreparedTerrain ? null : new int[height][width];
        String[][] mobAreas = usePreparedTerrain ? null : new String[height][width];
        Map<MapPaintData.Layer, String[][]> paintLayers = new LinkedHashMap<>();
        if (!usePreparedTerrain) {
            for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                paintLayers.put(layer, new String[height][width]);
            }
            for (Library.TileType[] row : tiles) {
                java.util.Arrays.fill(row, Library.TileType.WALL);
            }
        }

        List<EnvironmentTheme> environmentThemes = usePreparedTerrain
                ? new ArrayList<>(preparedTerrain.environmentThemes())
                : new ArrayList<>();
        List<MapEntity> entities = new ArrayList<>();
        Map<String, String> tileInteractions = new HashMap<>();
        Map<String, GameState.ResourceNodeSnapshot> resources = new HashMap<>();
        Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns = new HashMap<>();
        Set<String> discovered = new HashSet<>();
        Set<String> removed = new HashSet<>();
        List<MapDesignLibrary.MapTrigger> triggers = new ArrayList<>();
        List<MapLight> lights = usePreparedTerrain ? null : new ArrayList<>();
        Set<String> fired = new HashSet<>();
        MapDesignLibrary.AuthoredContent content = worldContent();
        MapLightingSettings lightingSettings = usePreparedTerrain
                ? preparedTerrain.map().getLightingSettings()
                : MapLightingSettings.defaultSettings();
        loadedCoordinates.clear();

        for (int dy = -WINDOW_RADIUS; dy <= WINDOW_RADIUS; dy++) {
            for (int dx = -WINDOW_RADIUS; dx <= WINDOW_RADIUS; dx++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(center.x() + dx, center.y() + dy);
                if (!manifest.chunks().containsKey(coordinate)) {
                    continue;
                }
                ChunkState chunk = loadChunk(coordinate, true);
                loadedCoordinates.add(coordinate);
                int offsetX = (dx + WINDOW_RADIUS) * manifest.chunkWidth();
                int offsetY = (dy + WINDOW_RADIUS) * manifest.chunkHeight();
                if (usePreparedTerrain) {
                    patchPreparedTerrainChunk(preparedTerrain, coordinate, chunk, offsetX, offsetY);
                } else {
                    int primaryIndex = themeIndex(environmentThemes, chunk.design.primaryTheme());
                    int alternateIndex = themeIndex(environmentThemes, chunk.design.alternateTheme());
                    if (coordinate.equals(center)) {
                        lightingSettings = chunk.map.getLightingSettings();
                    }
                    for (int y = 0; y < manifest.chunkHeight(); y++) {
                        for (int x = 0; x < manifest.chunkWidth(); x++) {
                            int windowX = offsetX + x;
                            int windowY = offsetY + y;
                            tiles[windowY][windowX] = chunk.map.getTile(x, y);
                            themes[windowY][windowX] = chunk.map.getEnvironmentThemeIndex(x, y) == 0
                                    ? primaryIndex
                                    : alternateIndex;
                            heights[windowY][windowX] = chunk.map.getHeightLevel(x, y);
                            mobAreas[windowY][windowX] = chunk.map.getMobAreaId(x, y);
                            for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                                paintLayers.get(layer)[windowY][windowX] =
                                        chunk.map.getPaintBrushId(layer, x, y);
                            }
                        }
                    }
                }

                for (MapEntity entity : chunk.entities) {
                    MapEntity windowEntity = entity.copy();
                    windowEntity.enterWorldWindow(
                            runtimeEnemySpawnId(coordinate, windowEntity.getEnemySpawnId()),
                            offsetX,
                            offsetY
                    );
                    windowEntity.translateWorldPosition(offsetX, offsetY);
                    entities.add(windowEntity);
                }
                translateStringMap(chunk.tileInteractions, offsetX, offsetY, tileInteractions);
                translateResourceMap(chunk.resourceNodeStates, offsetX, offsetY, resources);
                translateEnemyRespawns(coordinate, chunk.enemyRespawns, offsetX, offsetY, enemyRespawns);
                translateCoordinateSet(chunk.discoveredTiles, offsetX, offsetY, discovered);
                translateCoordinateSet(chunk.removedEntityKeys, offsetX, offsetY, removed);
                if (!usePreparedTerrain) {
                    for (MapLight light : chunk.map.getLightsView()) {
                        lights.add(light.translated(
                                offsetX,
                                offsetY,
                                "chunk[" + coordinate.x() + "," + coordinate.y() + "]::"));
                    }
                }
                for (MapDesignLibrary.MapTrigger trigger : chunk.triggers) {
                    String runtimeId = runtimeTriggerId(coordinate, trigger.id());
                    List<MapDesignLibrary.TriggerAction> actions = trigger.actions().stream()
                            .map(action -> new MapDesignLibrary.TriggerAction(
                                    action.type(),
                                    action.targetX() + offsetX,
                                    action.targetY() + offsetY
                            ))
                            .toList();
                    triggers.add(new MapDesignLibrary.MapTrigger(
                            runtimeId,
                            trigger.x() + offsetX,
                            trigger.y() + offsetY,
                            trigger.fireMode(),
                            trigger.oneShot(),
                            trigger.requiredQuestId(),
                            trigger.requiredQuestProgress(),
                            actions
                    ));
                    if (chunk.firedTriggerIds.contains(trigger.id())) {
                        fired.add(runtimeId);
                    }
                }
            }
        }

        DungeonMap map;
        if (usePreparedTerrain) {
            map = preparedTerrain.map();
        } else {
            MapPaintData paint = MapPaintData.of(
                    width,
                    height,
                    paintLayers.get(MapPaintData.Layer.FLOOR),
                    paintLayers.get(MapPaintData.Layer.WALL),
                    paintLayers.get(MapPaintData.Layer.DOOR),
                    paintLayers.get(MapPaintData.Layer.ROOF)
            );
            map = new DungeonMap(
                    tiles,
                    themes,
                    paint,
                    MapGeometryData.of(width, height, heights),
                    MobAreaData.of(width, height, mobAreas),
                    lightingSettings,
                    lights
            );
        }
        map.clearRenderDirtyTiles();
        currentEnvironmentThemes = List.copyOf(environmentThemes);
        windowMaterialized = true;
        return new WindowState(
                map,
                entities,
                tileInteractions,
                resources,
                enemyRespawns,
                discovered,
                removed,
                triggers,
                fired,
                content.authoredDialogues(),
                content.authoredQuests(),
                content.customItems(),
                content.customLimbs(),
                content.customFurniture(),
                content.customGatheringNodes(),
                content.customCookingRecipes(),
                content.craftingRecipes(),
                environmentThemes,
                centerChunkPath(),
                -1,
                -1
        );
    }

    private ChunkState loadChunk(ChunkCoordinate coordinate, boolean applyElapsedTime) throws IOException {
        ChunkState existing = chunkStates.get(coordinate);
        if (existing != null) {
            if (applyElapsedTime && !loadedCoordinates.contains(coordinate)) {
                existing.advanceTimers(System.currentTimeMillis());
            }
            return existing;
        }
        synchronized (chunkLoadLock) {
            existing = chunkStates.get(coordinate);
            if (existing != null) {
                return existing;
            }
            String relativePath = manifest.chunks().get(coordinate);
            if (relativePath == null) {
                return null;
            }
            Path path = WorldManifestLibrary.resolveChunkPath(manifestPath, relativePath);
            MapDesignLibrary.MapDesign design = MapDesignLibrary.load(path);
            MapDesignLibrary.mergeAuthoredContent(design, worldContent());
            if (design.width() != manifest.chunkWidth() || design.height() != manifest.chunkHeight()) {
                throw new IOException("Chunk " + coordinate + " has incompatible dimensions.");
            }
            GeneratedDungeon generated = MapDesignLibrary.toGeneratedDungeon(design);
            Map<String, String> interactions = new HashMap<>();
            for (GeneratedDungeon.TileInteraction interaction : generated.tileInteractions()) {
                interactions.put(key(interaction.x(), interaction.y()), interaction.interactionId());
            }
            ChunkState state = new ChunkState(
                    path,
                    design,
                    generated.dungeonMap(),
                    new ArrayList<>(generated.entities()),
                    interactions,
                    new HashSet<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashSet<>(),
                    new ArrayList<>(generated.mapTriggers()),
                    new HashSet<>(),
                    System.currentTimeMillis()
            );
            chunkStates.put(coordinate, state);
            for (MapEntity entity : state.entities) {
                if (entity == null) {
                    continue;
                }
                if (entity.hasVisibleStaticModel() && !entity.getStaticModelPath().isBlank()) {
                    prefetchedModelPaths.add(entity.getStaticModelPath());
                }
                if (entity.getCharacterModel() != null && entity.getCharacterModel().hasModel()) {
                    prefetchedCharacterModels.add(entity.getCharacterModel());
                }
            }
            return state;
        }
    }

    public Set<String> prefetchedModelPathsView() {
        return prefetchedModelPathsView;
    }

    public Set<CharacterModelDefinition> prefetchedCharacterModelsView() {
        return prefetchedCharacterModelsView;
    }

    public long preparedTerrainRevision() {
        return preparedTerrainRevision.get();
    }

    public List<TerrainPrefetch> preparedTerrainPrefetches() {
        List<TerrainPrefetch> result = new ArrayList<>(preparedTerrainWindows.size());
        preparedTerrainWindows.forEach((coordinate, prepared) -> result.add(new TerrainPrefetch(
                coordinate, prepared.map(), prepared.environmentThemes())));
        return List.copyOf(result);
    }

    public record TerrainPrefetch(
            ChunkCoordinate center,
            DungeonMap map,
            List<EnvironmentTheme> environmentThemes
    ) {
        public TerrainPrefetch {
            environmentThemes = environmentThemes == null ? List.of() : List.copyOf(environmentThemes);
        }
    }

    private boolean preparedLightingStillValid(
            PreparedTerrainWindow prepared,
            ChunkCoordinate requestedCenter
    ) {
        for (int dy = -WINDOW_RADIUS; dy <= WINDOW_RADIUS; dy++) {
            for (int dx = -WINDOW_RADIUS; dx <= WINDOW_RADIUS; dx++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        requestedCenter.x() + dx, requestedCenter.y() + dy);
                ChunkState state = chunkStates.get(coordinate);
                PreparedChunkRevision revision = prepared.chunkRevisions().get(coordinate);
                if (state != null && revision != null
                        && state.map.lightingRevision() != revision.lightingRevision()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void patchPreparedTerrainChunk(
            PreparedTerrainWindow prepared,
            ChunkCoordinate coordinate,
            ChunkState chunk,
            int offsetX,
            int offsetY
    ) {
        PreparedChunkRevision revision = prepared.chunkRevisions().get(coordinate);
        if (revision == null) {
            return;
        }
        boolean tilesChanged = revision.tileRevision() != chunk.map.tileRevision();
        boolean paintChanged = revision.paintRevision() != chunk.map.paintRevision();
        boolean geometryChanged = revision.geometryRevision() != chunk.map.geometryRevision();
        if (!tilesChanged && !paintChanged && !geometryChanged) {
            return;
        }
        DungeonMap target = prepared.map();
        for (int y = 0; y < manifest.chunkHeight(); y++) {
            for (int x = 0; x < manifest.chunkWidth(); x++) {
                int windowX = offsetX + x;
                int windowY = offsetY + y;
                if (tilesChanged) {
                    target.setTile(windowX, windowY, chunk.map.getTile(x, y));
                }
                if (paintChanged) {
                    for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                        target.getPaintData().set(
                                layer, windowX, windowY, chunk.map.getPaintBrushId(layer, x, y));
                    }
                }
                if (geometryChanged) {
                    target.getGeometryData().setHeightLevel(
                            windowX, windowY, chunk.map.getHeightLevel(x, y));
                }
            }
        }
    }

    /** Begins CPU-side loading of the 5x5 ring while the current 3x3 remains playable. */
    private void prefetchAround(ChunkCoordinate requestedCenter) {
        if (requestedCenter == null) {
            return;
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        requestedCenter.x() + dx, requestedCenter.y() + dy);
                if (!manifest.chunks().containsKey(coordinate)
                        || chunkStates.containsKey(coordinate)
                        || chunkPrefetches.containsKey(coordinate)) {
                    continue;
                }
                FutureTask<Void> task = new FutureTask<>(() -> {
                    try {
                        loadChunk(coordinate, false);
                    } catch (IOException exception) {
                        LOGGER.log(Level.WARNING, "Failed to prefetch world chunk " + coordinate + ".", exception);
                    } finally {
                        chunkPrefetches.remove(coordinate);
                    }
                    return null;
                });
                chunkPrefetches.put(coordinate, task);
                chunkPrefetchExecutor.execute(task);
            }
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                ChunkCoordinate candidate = new ChunkCoordinate(
                        requestedCenter.x() + dx, requestedCenter.y() + dy);
                if (!manifest.chunks().containsKey(candidate)
                        || preparedTerrainWindows.containsKey(candidate)
                        || terrainPreparationTasks.containsKey(candidate)) {
                    continue;
                }
                FutureTask<Void> task = new FutureTask<>(() -> {
                    try {
                        PreparedTerrainWindow prepared = buildPreparedTerrainWindow(candidate);
                        if (prepared != null) {
                            preparedTerrainWindows.put(candidate, prepared);
                            preparedTerrainRevision.incrementAndGet();
                        }
                    } catch (IOException exception) {
                        LOGGER.log(Level.WARNING,
                                "Failed to prepare terrain window around " + candidate + ".", exception);
                    } finally {
                        terrainPreparationTasks.remove(candidate);
                    }
                    return null;
                });
                terrainPreparationTasks.put(candidate, task);
                chunkPrefetchExecutor.execute(task);
            }
        }
    }

    private MapDesignLibrary.AuthoredContent worldContent() throws IOException {
        if (worldContent == null) {
            worldContent = WorldManifestLibrary.loadWorldContent(manifest, manifestPath);
        }
        return worldContent;
    }

    private int themeIndex(List<EnvironmentTheme> themes, ThemeLibrary theme) {
        EnvironmentTheme environmentTheme = theme.getTheme();
        int index = themes.indexOf(environmentTheme);
        if (index >= 0) {
            return index;
        }
        themes.add(environmentTheme);
        return themes.size() - 1;
    }

    private int windowOffsetX(ChunkCoordinate coordinate) {
        return (coordinate.x() - center.x() + WINDOW_RADIUS) * manifest.chunkWidth();
    }

    private int windowOffsetY(ChunkCoordinate coordinate) {
        return (coordinate.y() - center.y() + WINDOW_RADIUS) * manifest.chunkHeight();
    }

    private ChunkCoordinate coordinateForWindow(int x, int y) {
        int dx = Math.floorDiv(x, manifest.chunkWidth()) - WINDOW_RADIUS;
        int dy = Math.floorDiv(y, manifest.chunkHeight()) - WINDOW_RADIUS;
        return new ChunkCoordinate(center.x() + dx, center.y() + dy);
    }

    private void distributeStringMap(Map<String, String> values, StringMapConsumer consumer) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            PointKey point = parseKey(entry.getKey());
            if (point == null) {
                continue;
            }
            ChunkCoordinate coordinate = coordinateForWindow(point.x(), point.y());
            ChunkState state = chunkStates.get(coordinate);
            if (state != null && loadedCoordinates.contains(coordinate)) {
                consumer.accept(state, key(point.x() - windowOffsetX(coordinate), point.y() - windowOffsetY(coordinate)), entry.getValue());
            }
        }
    }

    private void distributeResourceMap(Map<String, GameState.ResourceNodeSnapshot> values) {
        for (Map.Entry<String, GameState.ResourceNodeSnapshot> entry : values.entrySet()) {
            PointKey point = parseKey(entry.getKey());
            if (point == null) {
                continue;
            }
            ChunkCoordinate coordinate = coordinateForWindow(point.x(), point.y());
            ChunkState state = chunkStates.get(coordinate);
            if (state != null && loadedCoordinates.contains(coordinate)) {
                state.resourceNodeStates.put(
                        key(point.x() - windowOffsetX(coordinate), point.y() - windowOffsetY(coordinate)),
                        entry.getValue()
                );
            }
        }
    }

    private void distributeEnemyRespawns(Map<String, GameState.EnemyRespawnSnapshot> values) {
        for (GameState.EnemyRespawnSnapshot snapshot : values.values()) {
            ChunkCoordinate coordinate = coordinateForWindow(snapshot.spawnX(), snapshot.spawnY());
            ChunkState state = chunkStates.get(coordinate);
            if (state == null || !loadedCoordinates.contains(coordinate)) {
                continue;
            }
            String localId = stripRuntimeEnemySpawnPrefix(snapshot.spawnId());
            state.enemyRespawns.put(localId, new GameState.EnemyRespawnSnapshot(
                    localId,
                    snapshot.mobId(),
                    snapshot.spawnX() - windowOffsetX(coordinate),
                    snapshot.spawnY() - windowOffsetY(coordinate),
                    snapshot.areaId(),
                    snapshot.awarenessRadius(),
                    snapshot.movementIntervalMs(),
                    snapshot.respawnDelayMs(),
                    snapshot.remainingMs()
            ));
        }
    }

    private void translateEnemyRespawns(
            ChunkCoordinate coordinate,
            Map<String, GameState.EnemyRespawnSnapshot> source,
            int offsetX,
            int offsetY,
            Map<String, GameState.EnemyRespawnSnapshot> target
    ) {
        for (GameState.EnemyRespawnSnapshot snapshot : source.values()) {
            String runtimeId = runtimeEnemySpawnId(coordinate, snapshot.spawnId());
            target.put(runtimeId, new GameState.EnemyRespawnSnapshot(
                    runtimeId,
                    snapshot.mobId(),
                    snapshot.spawnX() + offsetX,
                    snapshot.spawnY() + offsetY,
                    snapshot.areaId(),
                    snapshot.awarenessRadius(),
                    snapshot.movementIntervalMs(),
                    snapshot.respawnDelayMs(),
                    snapshot.remainingMs()
            ));
        }
    }

    private void distributeCoordinateSet(Set<String> values, CoordinateSetConsumer consumer) {
        for (String value : values) {
            PointKey point = parseCoordinateSuffix(value);
            if (point == null) {
                continue;
            }
            ChunkCoordinate coordinate = coordinateForWindow(point.x(), point.y());
            ChunkState state = chunkStates.get(coordinate);
            if (state == null || !loadedCoordinates.contains(coordinate)) {
                continue;
            }
            int localX = point.x() - windowOffsetX(coordinate);
            int localY = point.y() - windowOffsetY(coordinate);
            String localValue = replaceCoordinateSuffix(value, localX, localY);
            consumer.accept(state, localValue);
        }
    }

    private void translateStringMap(Map<String, String> source, int offsetX, int offsetY, Map<String, String> target) {
        for (Map.Entry<String, String> entry : source.entrySet()) {
            PointKey point = parseKey(entry.getKey());
            if (point != null) {
                target.put(key(point.x() + offsetX, point.y() + offsetY), entry.getValue());
            }
        }
    }

    private void translateResourceMap(
            Map<String, GameState.ResourceNodeSnapshot> source,
            int offsetX,
            int offsetY,
            Map<String, GameState.ResourceNodeSnapshot> target
    ) {
        for (Map.Entry<String, GameState.ResourceNodeSnapshot> entry : source.entrySet()) {
            PointKey point = parseKey(entry.getKey());
            if (point != null) {
                target.put(key(point.x() + offsetX, point.y() + offsetY), entry.getValue());
            }
        }
    }

    private void translateCoordinateSet(Set<String> source, int offsetX, int offsetY, Set<String> target) {
        for (String value : source) {
            PointKey point = parseCoordinateSuffix(value);
            if (point != null) {
                target.add(replaceCoordinateSuffix(value, point.x() + offsetX, point.y() + offsetY));
            }
        }
    }

    private static PointKey parseKey(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new PointKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static PointKey parseCoordinateSuffix(String value) {
        if (value == null) {
            return null;
        }
        String[] entityParts = value.split("\\|", -1);
        if (entityParts.length >= 4) {
            try {
                return new PointKey(
                        Integer.parseInt(entityParts[entityParts.length - 2]),
                        Integer.parseInt(entityParts[entityParts.length - 1])
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return parseKey(value);
    }

    private static String replaceCoordinateSuffix(String value, int x, int y) {
        String[] entityParts = value.split("\\|", -1);
        if (entityParts.length >= 4) {
            entityParts[entityParts.length - 2] = String.valueOf(x);
            entityParts[entityParts.length - 1] = String.valueOf(y);
            return String.join("|", entityParts);
        }
        return key(x, y);
    }

    private static String key(int x, int y) {
        return x + "," + y;
    }

    private static String runtimeTriggerId(ChunkCoordinate coordinate, String localId) {
        return "chunk[" + coordinate.x() + "," + coordinate.y() + "]::" + localId;
    }

    private static String runtimeEnemySpawnId(ChunkCoordinate coordinate, String localId) {
        return "chunk[" + coordinate.x() + "," + coordinate.y() + "]::enemy::" + localId;
    }

    private static String stripRuntimeEnemySpawnPrefix(String runtimeId) {
        int separator = runtimeId == null ? -1 : runtimeId.indexOf("::enemy::");
        return separator < 0 ? runtimeId : runtimeId.substring(separator + 9);
    }

    private static String stripRuntimeTriggerPrefix(String runtimeId) {
        int separator = runtimeId == null ? -1 : runtimeId.indexOf("]::");
        return separator < 0 ? runtimeId : runtimeId.substring(separator + 3);
    }

    private static ParsedTriggerId parseRuntimeTriggerId(String runtimeId) {
        if (runtimeId == null || !runtimeId.startsWith("chunk[")) {
            return null;
        }
        int end = runtimeId.indexOf("]::");
        if (end < 0) {
            return null;
        }
        String[] coordinates = runtimeId.substring(6, end).split(",");
        if (coordinates.length != 2) {
            return null;
        }
        try {
            return new ParsedTriggerId(
                    new ChunkCoordinate(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])),
                    runtimeId.substring(end + 3)
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static DungeonMap copyDungeonMap(DungeonMap source) {
        Library.TileType[][] tiles = new Library.TileType[source.getHeight()][source.getWidth()];
        int[][] themes = new int[source.getHeight()][source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                tiles[y][x] = source.getTile(x, y);
                themes[y][x] = source.getEnvironmentThemeIndex(x, y);
            }
        }
        return new DungeonMap(
                tiles,
                themes,
                source.getPaintData().copy(),
                source.getGeometryData().copy(),
                source.getMobAreaData().copy(),
                source.getLightingSettings(),
                source.getLightsView()
        );
    }

    public record WindowState(
            DungeonMap map,
            List<MapEntity> entities,
            Map<String, String> tileInteractions,
            Map<String, GameState.ResourceNodeSnapshot> resourceNodeStates,
            Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns,
            Set<String> discoveredTiles,
            Set<String> removedEntityKeys,
            List<MapDesignLibrary.MapTrigger> triggers,
            Set<String> firedTriggerIds,
            List<MapDesignLibrary.AuthoredDialogue> dialogues,
            List<MapDesignLibrary.AuthoredQuest> quests,
            List<MapDesignLibrary.CustomItem> items,
            List<MapDesignLibrary.CustomLimb> limbs,
            List<MapDesignLibrary.CustomFurnitureDefinition> furniture,
            List<MapDesignLibrary.CustomGatheringNode> gatheringNodes,
            List<MapDesignLibrary.CustomCookingRecipe> cookingRecipes,
            List<MapDesignLibrary.CraftingRecipe> craftingRecipes,
            List<EnvironmentTheme> environmentThemes,
            Path centerChunkPath,
            int playerX,
            int playerY
    ) {
        public WindowState withPlayer(int x, int y) {
            return new WindowState(
                    map, entities, tileInteractions, resourceNodeStates, enemyRespawns, discoveredTiles, removedEntityKeys,
                    triggers, firedTriggerIds, dialogues, quests, items, limbs, furniture, gatheringNodes,
                    cookingRecipes, craftingRecipes, environmentThemes, centerChunkPath, x, y
            );
        }
    }

    public record WindowCapture(
            DungeonMap map,
            List<MapEntity> entities,
            Map<String, String> tileInteractions,
            Map<String, GameState.ResourceNodeSnapshot> resourceNodeStates,
            Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns,
            Set<String> discoveredTiles,
            Set<String> removedEntityKeys,
            List<MapDesignLibrary.MapTrigger> triggers,
            Set<String> firedTriggerIds
    ) {
    }

    public record RecenterResult(WindowState window, int globalX, int globalY) {
    }

    public record PersistedChunkState(
            DungeonMap map,
            List<PersistedEntityState> entities,
            Map<String, String> tileInteractions,
            Set<String> removedEntityKeys,
            Map<String, GameState.ResourceNodeSnapshot> resourceNodeStates,
            Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns,
            Set<String> discoveredTiles,
            List<MapDesignLibrary.MapTrigger> triggers,
            Set<String> firedTriggerIds,
            long lastUpdatedEpochMs
    ) {
        public PersistedChunkState {
            entities = entities == null ? List.of() : List.copyOf(entities);
            tileInteractions = tileInteractions == null ? Map.of() : Map.copyOf(tileInteractions);
            removedEntityKeys = removedEntityKeys == null ? Set.of() : Set.copyOf(removedEntityKeys);
            resourceNodeStates = resourceNodeStates == null ? Map.of() : Map.copyOf(resourceNodeStates);
            enemyRespawns = enemyRespawns == null ? Map.of() : Map.copyOf(enemyRespawns);
            discoveredTiles = discoveredTiles == null ? Set.of() : Set.copyOf(discoveredTiles);
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
            firedTriggerIds = firedTriggerIds == null ? Set.of() : Set.copyOf(firedTriggerIds);
        }
    }

    public record PersistedEntityState(
            String name,
            Library.EntityType type,
            int x,
            int y,
            double worldFacingYawDegrees,
            String interactionId,
            String contentId,
            List<String> questIds,
            String talkSoundPath,
            boolean blocksMovement,
            boolean renderOnWall,
            double visualScale,
            String staticModelPath,
            boolean staticModelVisible,
            double staticModelOffsetX,
            double staticModelOffsetY,
            double staticModelOffsetZ,
            double staticModelYawDegrees,
            double staticModelPitchDegrees,
            double staticModelRollDegrees,
            double staticModelScaleMultiplier,
            double staticModelBrightness,
            InventorySystem.Item item,
            String monsterId,
            String enemySpawnId,
            int spawnX,
            int spawnY,
            String areaId,
            int awarenessRadius,
            int movementIntervalMs,
            int respawnDelayMs,
            int aiCooldownMs,
            boolean alerted,
            String corpseSourceSpawnId,
            List<InventorySystem.Item> corpseItems,
            boolean corpseButcheryAttempted,
            String corpseStatus,
            String temporaryStationId,
            CraftingStationType temporaryStationType,
            int temporaryStationRemainingMs,
            boolean temporaryStationPendingExpiry
    ) {
        public PersistedEntityState {
            name = name == null ? "" : name;
            type = type == null ? Library.EntityType.ITEM : type;
            interactionId = interactionId == null ? "" : interactionId;
            contentId = contentId == null ? "" : contentId.trim();
            questIds = questIds == null
                    ? List.of()
                    : questIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
            talkSoundPath = talkSoundPath == null ? "" : talkSoundPath;
            staticModelPath = staticModelPath == null ? "" : staticModelPath.trim().replace('\\', '/');
            monsterId = monsterId == null ? "" : monsterId;
            enemySpawnId = enemySpawnId == null ? "" : enemySpawnId;
            areaId = areaId == null ? "" : areaId;
            awarenessRadius = Math.max(0, awarenessRadius);
            movementIntervalMs = Math.max(250, movementIntervalMs);
            respawnDelayMs = Math.max(0, respawnDelayMs);
            aiCooldownMs = Math.max(0, aiCooldownMs);
            corpseSourceSpawnId = corpseSourceSpawnId == null ? "" : corpseSourceSpawnId;
            corpseItems = corpseItems == null ? List.of() : List.copyOf(corpseItems);
            corpseStatus = corpseStatus == null ? "" : corpseStatus;
            visualScale = Math.max(0.10, visualScale);
            staticModelScaleMultiplier = Math.max(0.05, staticModelScaleMultiplier);
            staticModelBrightness = Math.max(0.0, Math.min(4.0, staticModelBrightness));
            temporaryStationId = temporaryStationId == null ? "" : temporaryStationId;
            temporaryStationRemainingMs = Math.max(0, temporaryStationRemainingMs);
        }

        public PersistedEntityState(
                String name,
                Library.EntityType type,
                int x,
                int y,
                String interactionId,
                String talkSoundPath,
                boolean blocksMovement,
                boolean renderOnWall,
                double visualScale,
                InventorySystem.Item item
        ) {
            this(name, type, x, y, 0.0, interactionId, "", List.of(), talkSoundPath,
                    blocksMovement, renderOnWall,
                    visualScale, "", false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0,
                    1.0, item, "", "", x, y, "", 4, 3000, 300000, 0, false,
                    "", List.of(), false, "",
                    "", null, 0, false);
        }
    }

    private static final class ChunkState {
        private final Path path;
        private final MapDesignLibrary.MapDesign design;
        private DungeonMap map;
        private final List<MapEntity> entities;
        private final Map<String, String> tileInteractions;
        private final Set<String> removedEntityKeys;
        private final Map<String, GameState.ResourceNodeSnapshot> resourceNodeStates;
        private final Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns;
        private final Set<String> discoveredTiles;
        private final List<MapDesignLibrary.MapTrigger> triggers;
        private final Set<String> firedTriggerIds;
        private long lastUpdatedEpochMs;

        private ChunkState(
                Path path,
                MapDesignLibrary.MapDesign design,
                DungeonMap map,
                List<MapEntity> entities,
                Map<String, String> tileInteractions,
                Set<String> removedEntityKeys,
                Map<String, GameState.ResourceNodeSnapshot> resourceNodeStates,
                Map<String, GameState.EnemyRespawnSnapshot> enemyRespawns,
                Set<String> discoveredTiles,
                List<MapDesignLibrary.MapTrigger> triggers,
                Set<String> firedTriggerIds,
                long lastUpdatedEpochMs
        ) {
            this.path = path;
            this.design = design;
            this.map = map;
            this.entities = entities;
            this.tileInteractions = tileInteractions;
            this.removedEntityKeys = removedEntityKeys;
            this.resourceNodeStates = resourceNodeStates;
            this.enemyRespawns = enemyRespawns;
            this.discoveredTiles = discoveredTiles;
            this.triggers = triggers;
            this.firedTriggerIds = firedTriggerIds;
            this.lastUpdatedEpochMs = lastUpdatedEpochMs;
        }

        private void advanceTimers(long now) {
            long elapsed = Math.max(0L, now - lastUpdatedEpochMs);
            if (elapsed > 0L) {
                Map<String, GameState.ResourceNodeSnapshot> advanced = new HashMap<>();
                for (Map.Entry<String, GameState.ResourceNodeSnapshot> entry : resourceNodeStates.entrySet()) {
                    GameState.ResourceNodeSnapshot snapshot = entry.getValue();
                    long remaining = Math.max(0L, (long) snapshot.respawnRemainingMs() - elapsed);
                    advanced.put(entry.getKey(), remaining == 0L
                            ? new GameState.ResourceNodeSnapshot(0, 0, 0)
                            : new GameState.ResourceNodeSnapshot(
                                    snapshot.exhaustionLevel(),
                                    snapshot.attemptsSinceLastExhaustionRoll(),
                                    (int) Math.min(Integer.MAX_VALUE, remaining)
                            ));
                }
                resourceNodeStates.clear();
                resourceNodeStates.putAll(advanced);

                Map<String, GameState.EnemyRespawnSnapshot> advancedEnemies = new HashMap<>();
                for (GameState.EnemyRespawnSnapshot snapshot : enemyRespawns.values()) {
                    advancedEnemies.put(snapshot.spawnId(), new GameState.EnemyRespawnSnapshot(
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
                enemyRespawns.clear();
                enemyRespawns.putAll(advancedEnemies);
                Set<String> expiredSpawnIds = advancedEnemies.values().stream()
                        .filter(snapshot -> snapshot.respawnDelayMs() > 0 && snapshot.remainingMs() == 0)
                        .map(GameState.EnemyRespawnSnapshot::spawnId)
                        .collect(java.util.stream.Collectors.toSet());
                entities.removeIf(entity -> entity.isCorpse()
                        && expiredSpawnIds.contains(stripRuntimeEnemySpawnPrefix(
                                entity.getCorpseState().sourceSpawnId())));

                entities.removeIf(entity -> entity.isTemporaryStation()
                        && entity.advanceTemporaryStationTimer(elapsed));
            }
            lastUpdatedEpochMs = now;
        }

        private PersistedChunkState persisted() {
            validateEntityCoordinatesForPersistence();
            return new PersistedChunkState(
                    copyDungeonMap(map),
                    entities.stream()
                            .map(entity -> new PersistedEntityState(
                                    entity.getName(),
                                    entity.getType(),
                                    entity.getPersistenceX(),
                                    entity.getPersistenceY(),
                                    entity.getPersistenceFacingYawDegrees(),
                                    entity.getInteractionId(),
                                    entity.getContentId(),
                                    entity.getQuestIds(),
                                    entity.getTalkSoundPath(),
                                    entity.blocksMovement(),
                                    entity.shouldRenderOnWall(),
                                    entity.getVisualScale(),
                                    entity.getStaticModelPath(),
                                    entity.hasVisibleStaticModel(),
                                    entity.getStaticModelOffsetX(),
                                    entity.getStaticModelOffsetY(),
                                    entity.getStaticModelOffsetZ(),
                                    entity.getStaticModelYawDegrees(),
                                    entity.getStaticModelPitchDegrees(),
                                    entity.getStaticModelRollDegrees(),
                                    entity.getStaticModelScaleMultiplier(),
                                    entity.getStaticModelBrightness(),
                                    entity.getItem(),
                                    entity.getMonster() == null ? "" : entity.getMonster().getCustomId(),
                                    entity.getEnemySpawnId(),
                                    entity.getSpawnX(),
                                    entity.getSpawnY(),
                                    entity.getRoamingAreaId(),
                                    entity.getAwarenessRadius(),
                                    entity.getMovementIntervalMs(),
                                    entity.getRespawnDelayMs(),
                                    entity.getWorldAiCooldownMs(),
                                    entity.isWorldAlerted(),
                                    entity.getCorpseState() == null ? "" : entity.getCorpseState().sourceSpawnId(),
                                    entity.getCorpseState() == null ? List.of() : entity.getCorpseState().contents(),
                                    entity.getCorpseState() != null && entity.getCorpseState().butcheryAttempted(),
                                    entity.getCorpseState() == null ? "" : entity.getCorpseState().statusMessage(),
                                    entity.getTemporaryStationId(),
                                    entity.getTemporaryStationType(),
                                    entity.getTemporaryStationRemainingMs(),
                                    entity.isTemporaryStationPendingExpiry()
                            ))
                            .toList(),
                    tileInteractions,
                    removedEntityKeys,
                    resourceNodeStates,
                    enemyRespawns,
                    discoveredTiles,
                    triggers,
                    firedTriggerIds,
                    lastUpdatedEpochMs
            );
        }

        private void validateEntityCoordinatesForPersistence() {
            int width = map.getWidth();
            int height = map.getHeight();
            for (MapEntity entity : entities) {
                boolean stableEnemy = entity.getType() == Library.EntityType.ENEMY
                        && !entity.getEnemySpawnId().isBlank();
                int localX = stableEnemy ? entity.getSpawnX() : entity.getPersistenceX();
                int localY = stableEnemy ? entity.getSpawnY() : entity.getPersistenceY();
                if (localX >= 0 && localX < width && localY >= 0 && localY < height) {
                    continue;
                }
                throw new IllegalStateException(
                        "Refusing to persist non-local entity " + entity.getName()
                                + " at " + localX + "," + localY
                                + " for chunk " + path + " (" + width + "x" + height + ")."
                );
            }
        }
    }

    private interface StringMapConsumer {
        void accept(ChunkState state, String key, String value);
    }

    private interface CoordinateSetConsumer {
        void accept(ChunkState state, String value);
    }

    private record PointKey(int x, int y) {
    }

    private record ParsedTriggerId(ChunkCoordinate coordinate, String localId) {
    }

    private record PreparedTerrainWindow(
            DungeonMap map,
            List<EnvironmentTheme> environmentThemes,
            Map<ChunkCoordinate, PreparedChunkRevision> chunkRevisions
    ) {
    }

    private record PreparedChunkRevision(
            long tileRevision,
            long paintRevision,
            long geometryRevision,
            long lightingRevision
    ) {
        private static PreparedChunkRevision of(DungeonMap map) {
            return new PreparedChunkRevision(
                    map.tileRevision(), map.paintRevision(),
                    map.geometryRevision(), map.lightingRevision());
        }
    }
}

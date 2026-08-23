package org.main.experimental;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector4d;
import org.main.battle.DifficultyResolver;
import org.main.content.CharacterModelDefinition;
import org.main.content.MapDesignLibrary;
import org.main.core.GameConfiguration;
import org.main.core.GameState;
import org.main.core.Library;
import org.main.core.ItemModelIconProfile;
import org.main.core.ItemModelIconRenderQueue;
import org.main.core.InventorySystem;
import org.main.core.LanternSystem;
import org.main.core.OpenWorldSession;
import org.main.core.RenderSettings;
import org.main.engine.AssetLoader;
import org.main.engine.DungeonMap;
import org.main.engine.DungeonRenderContext;
import org.main.engine.DungeonRenderDebugInfo;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapLight;
import org.main.engine.MapEntity;
import org.main.engine.MapGeometryData;
import org.main.engine.MapLightingSettings;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.engine.RealtimeDungeonViewport;
import org.main.engine.SkyboxSpec;
import org.main.engine.TerrainGeometry;
import org.main.engine.TextureManager;
import org.lwjgl.opengl.GLCapabilities;
import org.main.engine.GridDirection;
import org.main.engine.LineOfSight;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class LwjglDungeonViewport implements RealtimeDungeonViewport {
    private static final int MAX_DIFFICULTY_LABEL_DEPTH = 3;
    private static final int MIN_VIEW_DEPTH = 4;
    private static final int MAX_VIEW_DEPTH = 32;
    private static final double MIN_FOV_DEGREES = 45.0;
    private static final double MAX_FOV_DEGREES = 100.0;
    private static final Logger LOGGER = Logger.getLogger(LwjglDungeonViewport.class.getName());

    private final LwjglTextureCache textureCache = new LwjglTextureCache();
    private final TextureManager textureManager;
    private final LwjglWorldBatchBuilder worldBatchBuilder = new LwjglWorldBatchBuilder();
    private final LightmapBaker lightmapBaker = new LightmapBaker();
    private final ExecutorService lightmapExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Aether-Lightmap-Baker");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, BufferedImage> chunkLightmapCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
            return size() > Math.max(16, GameConfiguration.intValue("lighting.lightmap.chunkCache.maxEntries", 96));
        }
    };
    private final LwjglBattleSceneRenderer battleSceneRenderer = new LwjglBattleSceneRenderer(textureCache);
    private final Map<String, LwjglStaticModel> staticModelCache = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<LwjglStaticModel.Mesh, FixedMeshBuffers> fixedViewModelMeshes =
            new IdentityHashMap<>();
    private final Map<ModelIconBoundsKey, ModelIconBounds> modelIconBoundsCache = new HashMap<>();
    private final Deque<String> pendingStaticModelPreloads = new ArrayDeque<>();
    private final Set<String> queuedStaticModelPreloads = new HashSet<>();
    private final Map<String, Future<LwjglStaticModel>> pendingStaticModelImports = new HashMap<>();
    private final ExecutorService staticModelExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Aether-Model-Importer");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<CharacterModelDefinition, LwjglSkinnedModel> worldSkinnedModelCache =
            new LinkedHashMap<>(64, 0.75f, true);
    private final Map<CharacterModelDefinition, Future<LwjglSkinnedModel>> pendingWorldSkinnedImports = new HashMap<>();
    private final Set<CharacterModelDefinition> failedWorldSkinnedModels = new HashSet<>();
    private final Set<CharacterModelDefinition> failedGpuSkinningModels = new HashSet<>();
    private final Set<String> failedStaticModels = new HashSet<>();
    private final LwjglDungeonSceneBuilder sceneBuilder;
    private final FrameProfiler frameProfiler = new FrameProfiler();
    private static final int TERRAIN_CELL_SIZE = 8;
    private TerrainBuildKey terrainBuildKey;
    private Map<LwjglDungeonSceneBuilder.CellCoordinate, LwjglDungeonSceneBuilder.TerrainCell> terrainCells = Map.of();
    private final Map<LwjglDungeonSceneBuilder.CellCoordinate, TerrainCacheKey> currentTerrainKeys = new HashMap<>();
    private final List<LwjglRenderDevice.PreparedWorld> visibleTerrainWorlds = new ArrayList<>();
    private final Set<TerrainCacheKey> pinnedTerrainKeys = new HashSet<>();
    private final Map<TerrainBuildKey, Future<Map<LwjglDungeonSceneBuilder.CellCoordinate,
            LwjglDungeonSceneBuilder.TerrainCell>>> pendingTerrainCellBuilds = new HashMap<>();
    private final LinkedHashMap<TerrainBuildKey, Map<LwjglDungeonSceneBuilder.CellCoordinate,
            LwjglDungeonSceneBuilder.TerrainCell>> preparedTerrainCellBuilds =
            new LinkedHashMap<>(12, 0.75f, true);
    private final Deque<PendingTerrainCellUpload> pendingTerrainCellUploads = new ArrayDeque<>();
    private final Set<TerrainCacheKey> queuedTerrainCellUploads = new HashSet<>();
    private long observedPreparedTerrainRevision = Long.MIN_VALUE;
    private List<EnvironmentTheme> currentEnvironmentThemes;
    private final LinkedHashMap<TerrainCacheKey, TerrainCacheEntry> terrainCache =
            new LinkedHashMap<>(48, 0.75f, true);
    private LwjglRenderDevice renderDevice;
    private FixedFunctionPrimitives fixedPrimitives;
    private GpuFrameTimer gpuFrameTimer;
    private GpuTexture lightmapTexture;
    private long lightmapSignature = Long.MIN_VALUE;
    private long pendingLightmapSignature = Long.MIN_VALUE;
    private Future<PreparedLightmap> pendingLightmap;
    private double lightmapBakeMs;
    private int lightmapWidth;
    private int lightmapHeight;
    private BufferedImage currentLightmapImage;
    private int lightmapChunkHits;
    private int lightmapChunkMisses;
    private int lightmapChunkEntries;
    private boolean lightmapPending;
    private int worldBatchCount;
    private int worldRenderedIndices;
    private final Matrix4f currentProjectionView = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f staticModelMatrix = new Matrix4f();
    private final Matrix4f skinnedModelMatrix = new Matrix4f();
    private DungeonMap currentRenderMap;
    private MapLightingSettings currentLightingSettings = MapLightingSettings.defaultSettings();
    private LightSpatialIndex lightSpatialIndex;
    private DungeonMap lightSpatialIndexMap;
    private long lightSpatialIndexRevision = Long.MIN_VALUE;
    private double currentCameraX;
    private double currentCameraY;
    private double currentCameraZ;
    private RuntimeLight currentPlayerLanternLight;
    private final int windowWidth;
    private final int windowHeight;
    private final double wallHeight;
    private final double roofPitchHeight;
    private final double eyeHeight;
    private double fovDegrees;
    private final double nearPlane;
    private final double farPlane;
    private final boolean resizable;
    private int maxDepth;
    private long window;
    private boolean debugVisible;
    private RenderSettings renderSettings;
    private long appliedConfigurationRevision = Long.MIN_VALUE;
    private int displayRefreshRate = 60;
    private final boolean benchmarkMode;
    private double simulationInterpolationAlpha;
    private int visibleTiles;
    private int floorQuads;
    private int wallQuads;
    private int roofQuads;
    private int spriteQuads;
    private int staticModels;
    private int staticModelPreloadQueueSize;
    private int staticModelPreloadsThisFrame;
    private int staticModelCacheHitsThisFrame;
    private int staticModelCacheMissesThisFrame;
    private int staticModelFallbacksThisFrame;
    private double lastFrameMs;
    private double smoothedFrameMs = 16.0;
    private CameraLookState lastLookState = CameraLookState.centered();
    private double currentSkyboxYawDegrees;
    private List<EnemyLabel> enemyLabels = List.of();
    private String skyboxPath = "";
    private SkyboxSpec skyboxSpec = SkyboxSpec.DEFAULT;
    private final Map<String, BufferedImage> skyboxFaceImages = new HashMap<>();

    public LwjglDungeonViewport(TextureManager textureManager, List<EnvironmentTheme> environmentThemes) {
        this(textureManager, environmentThemes, false);
    }

    LwjglDungeonViewport(
            TextureManager textureManager,
            List<EnvironmentTheme> environmentThemes,
            boolean benchmarkMode
    ) {
        List<EnvironmentTheme> safeThemes = environmentThemes == null || environmentThemes.isEmpty()
                ? List.of(EnvironmentTheme.defaultTheme())
                : new ArrayList<>(environmentThemes);
        this.textureManager = textureManager;
        this.currentEnvironmentThemes = List.copyOf(safeThemes);
        this.sceneBuilder = new LwjglDungeonSceneBuilder(textureManager, safeThemes);
        this.benchmarkMode = benchmarkMode;
        this.windowWidth = benchmarkMode ? 1920
                : Math.max(320, GameConfiguration.intValue("renderer.prototype.windowWidth", 1280));
        this.windowHeight = benchmarkMode ? 1080
                : Math.max(240, GameConfiguration.intValue("renderer.prototype.windowHeight", 720));
        this.maxDepth = clamp(GameConfiguration.intValue("renderer.prototype.maxDepth", 12), MIN_VIEW_DEPTH, MAX_VIEW_DEPTH);
        this.wallHeight = Math.max(0.1, GameConfiguration.doubleValue("renderer.prototype.wallHeight", 1.0));
        this.roofPitchHeight = Math.max(0.0, GameConfiguration.doubleValue("renderer.prototype.roofPitchHeight", 0.45));
        this.eyeHeight = Math.max(0.05, GameConfiguration.doubleValue("renderer.prototype.eyeHeight", 0.55));
        this.fovDegrees = clamp(GameConfiguration.doubleValue("renderer.prototype.fovDegrees", 70.0), MIN_FOV_DEGREES, MAX_FOV_DEGREES);
        this.nearPlane = Math.max(0.01, GameConfiguration.doubleValue("renderer.prototype.nearPlane", 0.05));
        this.farPlane = Math.max(nearPlane + 1.0, GameConfiguration.doubleValue("renderer.prototype.farPlane", 64.0));
        this.resizable = Boolean.parseBoolean(GameConfiguration.stringValue("renderer.prototype.resizable", "true"));
        this.debugVisible = Boolean.parseBoolean(GameConfiguration.stringValue(
                "renderer.prototype.debug.defaultVisible",
                "false"
        ));
        this.renderSettings = RenderSettings.load();
    }

    @Override
    public void initialize() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW.");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, GameConfiguration.intValue("renderer.opengl.major", 4));
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, GameConfiguration.intValue("renderer.opengl.minor", 1));
        // The current renderer still uses fixed-function OpenGL calls. Requesting
        // compatibility explicitly prevents drivers from selecting a core-only
        // context where calls such as glAlphaFunc are unavailable.
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        window = glfwCreateWindow(windowWidth, windowHeight, "Aether LWJGL Dungeon Prototype", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new IllegalStateException("Unable to create an OpenGL compatibility-profile window. "
                    + "Update the graphics driver and ensure Aether uses the dedicated GPU.");
        }

        glfwMakeContextCurrent(window);
        GLCapabilities capabilities;
        try {
            capabilities = createCapabilities();
        } catch (RuntimeException | Error error) {
            cleanupFailedContext();
            throw new IllegalStateException("Unable to initialize the OpenGL compatibility context.", error);
        }
        logOpenGlContext();
        requireFixedFunctionCapabilities(capabilities);
        fixedPrimitives = new FixedFunctionPrimitives();
        updateDisplayRefreshRate();
        applyRenderSettings(true);
        renderDevice = new LwjglRenderDevice(textureCache);
        gpuFrameTimer = new GpuFrameTimer();
        lightmapTexture = new GpuTexture();
        glfwShowWindow(window);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.10f);
        glClearColor(0.04f, 0.04f, 0.07f, 1.0f);
    }

    private void logOpenGlContext() {
        String vendor = openGlString(GL_VENDOR);
        String renderer = openGlString(GL_RENDERER);
        String version = openGlString(GL_VERSION);
        int profile = glfwGetWindowAttrib(window, GLFW_OPENGL_PROFILE);
        String profileName = switch (profile) {
            case GLFW_OPENGL_COMPAT_PROFILE -> "compatibility";
            case GLFW_OPENGL_CORE_PROFILE -> "core";
            default -> "unspecified";
        };
        LOGGER.info(() -> "OpenGL context: vendor=" + vendor
                + ", renderer=" + renderer
                + ", version=" + version
                + ", profile=" + profileName);
    }

    private static String openGlString(int name) {
        String value = glGetString(name);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void requireFixedFunctionCapabilities(GLCapabilities capabilities) {
        List<String> missing = new ArrayList<>();
        if (capabilities.glAlphaFunc == NULL) missing.add("glAlphaFunc");
        if (capabilities.glBegin == NULL) missing.add("glBegin");
        if (capabilities.glMatrixMode == NULL) missing.add("glMatrixMode");
        if (missing.isEmpty()) {
            return;
        }

        String details = String.join(", ", missing);
        cleanupFailedContext();
        throw new IllegalStateException("The graphics driver did not provide the OpenGL compatibility functions "
                + "required by Aether: " + details + ". Update the graphics driver and ensure Aether uses the "
                + "dedicated GPU.");
    }

    private void cleanupFailedContext() {
        if (window != NULL) {
            glfwMakeContextCurrent(NULL);
            org.lwjgl.opengl.GL.setCapabilities(null);
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
    }

    @Override
    public void renderFrame(DungeonRenderContext context) {
        renderFrame(context, CameraLookState.centered());
    }

    public void renderFrame(DungeonRenderContext context, CameraLookState lookState) {
        renderFrame(context, lookState, null, null);
    }

    public void renderFrame(
            DungeonRenderContext context,
            CameraLookState lookState,
            LwjglTextOverlayRenderer overlayRenderer,
            org.main.core.AetherGameRuntime runtime
    ) {
        long frameStart = System.nanoTime();
        if (appliedConfigurationRevision == Long.MIN_VALUE) {
            frameProfiler.beginFrame(frameStart);
        }
        applyRenderSettings(false);
        syncViewSettingsFromConfiguration();
        textureCache.invalidateBindings();
        if (renderDevice != null) {
            renderDevice.beginFrameStats();
        }
        if (gpuFrameTimer != null) {
            gpuFrameTimer.begin();
        }
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        int framebufferWidth = Math.max(1, width[0]);
        int framebufferHeight = Math.max(1, height[0]);
        CameraLookState safeLookState = lookState == null ? CameraLookState.centered() : lookState;
        lastLookState = safeLookState;
        currentSkyboxYawDegrees = context == null
                ? safeLookState.yawOffsetDegrees()
                : animatedYawDegrees(context) + safeLookState.yawOffsetDegrees();

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawSkybox(framebufferWidth, framebufferHeight);

        if (shouldRenderDungeon(runtime)) {
            frameProfiler.begin(FrameProfiler.Phase.SCENE_PREPARATION);
            double cameraYawDegrees = animatedYawDegrees(context) + safeLookState.yawOffsetDegrees();
            DungeonRenderContext sceneContext = battleBackdropContext(context, runtime);
            double cameraX = animatedCameraX(sceneContext);
            double cameraZ = animatedCameraZ(sceneContext);
            double cameraY = cameraY(sceneContext, cameraX, cameraZ);
            Matrix4f projectionView = projectionViewMatrix(
                    framebufferWidth,
                    framebufferHeight,
                    sceneContext,
                    safeLookState,
                    cameraX,
                    cameraY,
                    cameraZ);
            currentProjectionView.set(projectionView);
            currentRenderMap = sceneContext.map();
            currentLightingSettings = sceneContext.map().getLightingSettings();
            currentCameraX = cameraX;
            currentCameraY = cameraY;
            currentCameraZ = cameraZ;
            currentPlayerLanternLight = playerLanternLight(runtime == null ? null : runtime.gameState());
            ensureLightmap(sceneContext.map());
            scheduleStaticModelPreloads(sceneContext, runtime);
            scheduleTerrainCellPreloads(runtime);
            processTerrainCellPreloads();
            processTerrainCellUploads();
            processStaticModelPreloads();
            long requestedRenderRevision = sceneContext.map().renderRevision();
            int requestedThemeHash = currentEnvironmentThemes.hashCode();
            TerrainBuildKey requestedTerrainKey = terrainBuildKey;
            if (requestedTerrainKey == null
                    || requestedTerrainKey.map() != sceneContext.map()
                    || requestedTerrainKey.renderRevision() != requestedRenderRevision
                    || requestedTerrainKey.themeSignature() != requestedThemeHash) {
                requestedTerrainKey = new TerrainBuildKey(
                        sceneContext.map(), requestedRenderRevision,
                        wallHeight, roofPitchHeight, requestedThemeHash);
            }
            if (!requestedTerrainKey.equals(terrainBuildKey)) {
                DungeonRenderContext terrainContext = new DungeonRenderContext(
                        sceneContext.map(), List.of(), sceneContext.playerCharacter(),
                        sceneContext.playerX(), sceneContext.playerY(), sceneContext.direction(),
                        sceneContext.viewportWidth(), sceneContext.viewportHeight(),
                        sceneContext.cameraOffsetForward(), sceneContext.cameraOffsetSide(),
                        sceneContext.cameraRotationRadians());
                Map<LwjglDungeonSceneBuilder.CellCoordinate, LwjglDungeonSceneBuilder.TerrainCell> prepared =
                        preparedTerrainCellBuilds.remove(requestedTerrainKey);
                terrainCells = prepared == null
                        ? sceneBuilder.buildTerrainCells(
                                terrainContext, TERRAIN_CELL_SIZE, wallHeight, roofPitchHeight)
                        : prepared;
                terrainBuildKey = requestedTerrainKey;
                currentTerrainKeys.clear();
                for (LwjglDungeonSceneBuilder.TerrainCell cell : terrainCells.values()) {
                    currentTerrainKeys.put(cell.coordinate(), new TerrainCacheKey(
                            requestedTerrainKey, cell.coordinate().x(), cell.coordinate().y()));
                }
            }
            visibleTerrainWorlds.clear();
            pinnedTerrainKeys.clear();
            int terrainVisibleTiles = 0;
            int terrainFloorQuads = 0;
            int terrainWallQuads = 0;
            int terrainRoofQuads = 0;
            for (LwjglDungeonSceneBuilder.TerrainCell cell : terrainCells.values()) {
                if (!terrainCellVisible(cell, cameraX, cameraZ, cameraYawDegrees, fovDegrees, maxDepth)) {
                    continue;
                }
                TerrainCacheKey cellKey = currentTerrainKeys.get(cell.coordinate());
                TerrainCacheEntry cached = terrainCache.get(cellKey);
                if (cached == null) {
                    cached = new TerrainCacheEntry(cell.scene(), renderDevice.prepareWorld(
                            worldBatchBuilder.build(cell.scene().quads())));
                    terrainCache.put(cellKey, cached);
                    frameProfiler.recordCacheMiss();
                } else {
                    frameProfiler.recordCacheHit();
                }
                pinnedTerrainKeys.add(cellKey);
                visibleTerrainWorlds.add(cached.world());
                terrainVisibleTiles += cell.scene().visibleTiles();
                terrainFloorQuads += cell.scene().floorQuads();
                terrainWallQuads += cell.scene().wallQuads();
                terrainRoofQuads += cell.scene().roofQuads();
            }
            trimTerrainCache(pinnedTerrainKeys);
            LwjglDungeonSceneBuilder.Scene entityScene = sceneBuilder.buildEntities(
                    sceneContext, maxDepth, cameraYawDegrees, fovDegrees,
                    simulationInterpolationAlpha);
            frameProfiler.end(FrameProfiler.Phase.SCENE_PREPARATION);
            visibleTiles = terrainVisibleTiles;
            floorQuads = terrainFloorQuads;
            wallQuads = terrainWallQuads;
            roofQuads = terrainRoofQuads;
            spriteQuads = entityScene.spriteQuads();
            staticModels = entityScene.models().size();
            enemyLabels = runtime != null && runtime.gameState().isBattleMode() ? List.of() : projectEnemyLabels(
                    sceneContext,
                    safeLookState,
                    framebufferWidth,
                    framebufferHeight
            );

            frameProfiler.begin(FrameProfiler.Phase.TERRAIN_RENDERING);
            List<RuntimeLight> bridgeLights = withReservedPlayerLight(lightmapPending
                    ? dynamicLightsNearCamera(sceneContext.map()) : List.of());
            renderDevice.renderPreparedWorlds(
                    visibleTerrainWorlds, projectionView, lightmapTexture,
                    sceneContext.map().getWidth(), sceneContext.map().getHeight(),
                    cameraX, cameraY, cameraZ,
                    sceneContext.map().getLightingSettings(), bridgeLights);
            int terrainBatches = renderDevice.batchCount();
            int terrainIndices = renderDevice.renderedIndices();
            renderDevice.renderWorld(
                    worldBatchBuilder.build(entityScene.quads()),
                    projectionView,
                    lightmapTexture,
                    sceneContext.map().getWidth(),
                    sceneContext.map().getHeight(),
                    cameraX,
                    cameraY,
                    cameraZ,
                    sceneContext.map().getLightingSettings(),
                    bridgeLights);
            frameProfiler.end(FrameProfiler.Phase.TERRAIN_RENDERING);
            worldBatchCount = terrainBatches + renderDevice.batchCount();
            worldRenderedIndices = terrainIndices + renderDevice.renderedIndices();

            frameProfiler.begin(FrameProfiler.Phase.MODEL_RENDERING);
            staticModelCacheHitsThisFrame = 0;
            staticModelCacheMissesThisFrame = 0;
            staticModelFallbacksThisFrame = 0;
            configureProjection(framebufferWidth, framebufferHeight);
            configureCamera(sceneContext, safeLookState);
            for (LwjglDungeonSceneBuilder.ModelInstance model : entityScene.models()) {
                drawStaticModel(model);
            }
            frameProfiler.end(FrameProfiler.Phase.MODEL_RENDERING);
            if (runtime != null && runtime.gameState() != null && runtime.gameState().isBattleMode()) {
                frameProfiler.begin(FrameProfiler.Phase.BATTLE_RENDERING);
                runtime.battleRenderer().setProjectedActorPositions(
                        battleSceneRenderer.render(
                                context,
                                safeLookState,
                                runtime,
                                framebufferWidth,
                                framebufferHeight,
                                sampleBattleLight(
                                        context.playerX() + 0.5,
                                        context.playerY() + 0.5,
                                        0.02f)));
                frameProfiler.end(FrameProfiler.Phase.BATTLE_RENDERING);
            } else if (runtime != null && runtime.gameState() != null) {
                frameProfiler.begin(FrameProfiler.Phase.MODEL_RENDERING);
                renderGatheringToolViewModel(runtime.gameState().getGatheringViewModelState(), framebufferWidth, framebufferHeight);
                frameProfiler.end(FrameProfiler.Phase.MODEL_RENDERING);
            }
        } else {
            visibleTiles = 0;
            floorQuads = 0;
            wallQuads = 0;
            roofQuads = 0;
            spriteQuads = 0;
            staticModels = 0;
            staticModelCacheHitsThisFrame = 0;
            staticModelCacheMissesThisFrame = 0;
            staticModelFallbacksThisFrame = 0;
            worldBatchCount = 0;
            worldRenderedIndices = 0;
            enemyLabels = List.of();
        }

        glDisable(GL_FOG);
        if (renderDevice != null) {
            frameProfiler.recordDraws(renderDevice.frameDrawCalls(), renderDevice.frameDrawnIndices());
            frameProfiler.recordUpload(renderDevice.frameUploadedBytes());
            frameProfiler.recordSkinnedVertices(renderDevice.frameSkinnedVertices());
        }
        if (overlayRenderer != null && runtime != null) {
            frameProfiler.begin(FrameProfiler.Phase.HUD);
            overlayRenderer.setEnemyLabels(enemyLabels);
            overlayRenderer.setViewportDebugLines(viewportDebugLines());
            overlayRenderer.render(runtime, framebufferWidth, framebufferHeight);
            renderItemModelIcons(
                    overlayRenderer.modelIconRequests(), framebufferWidth, framebufferHeight);
            frameProfiler.end(FrameProfiler.Phase.HUD);
        }

        if (gpuFrameTimer != null) {
            frameProfiler.recordGpuNanos(gpuFrameTimer.end());
        }
        frameProfiler.begin(FrameProfiler.Phase.BUFFER_SWAP);
        glfwSwapBuffers(window);
        frameProfiler.end(FrameProfiler.Phase.BUFFER_SWAP);
        lastFrameMs = (System.nanoTime() - frameStart) / 1_000_000.0;
        smoothedFrameMs = smoothedFrameMs * 0.90 + lastFrameMs * 0.10;
        frameProfiler.endFrame(System.nanoTime());
        if (debugVisible) {
            updateWindowTitle();
        }
    }

    private boolean shouldRenderDungeon(org.main.core.AetherGameRuntime runtime) {
        return runtime == null || runtime.gameState().isDungeonMode() || runtime.gameState().isBattleMode();
    }

    private DungeonRenderContext battleBackdropContext(
            DungeonRenderContext context,
            org.main.core.AetherGameRuntime runtime
    ) {
        if (context == null || runtime == null || runtime.gameState() == null || !runtime.gameState().isBattleMode()) {
            return context;
        }
        List<MapEntity> backdropEntities = context.entities().stream()
                .filter(entity -> entity.getType() != Library.EntityType.ENEMY
                        && entity.getType() != Library.EntityType.ALLY
                        && entity.getType() != Library.EntityType.NPC)
                .toList();
        return new DungeonRenderContext(context.map(), backdropEntities, context.playerCharacter(),
                context.playerX(), context.playerY(), context.direction(), context.viewportWidth(),
                context.viewportHeight(), context.cameraOffsetForward(), context.cameraOffsetSide(),
                context.cameraRotationRadians());
    }

    @Override
    public void shutdown() {
        battleSceneRenderer.shutdown();
        lightmapExecutor.shutdownNow();
        staticModelExecutor.shutdownNow();
        if (renderDevice != null) {
            clearTerrainCache();
            terrainBuildKey = null;
            terrainCells = Map.of();
            currentTerrainKeys.clear();
            visibleTerrainWorlds.clear();
            renderDevice.shutdown();
            renderDevice = null;
        }
        if (gpuFrameTimer != null) {
            gpuFrameTimer.shutdown();
            gpuFrameTimer = null;
        }
        if (lightmapTexture != null) {
            lightmapTexture.shutdown();
            lightmapTexture = null;
        }
        staticModelCache.clear();
        pendingStaticModelPreloads.clear();
        queuedStaticModelPreloads.clear();
        for (Future<LwjglStaticModel> pending : pendingStaticModelImports.values()) {
            pending.cancel(false);
        }
        pendingStaticModelImports.clear();
        worldSkinnedModelCache.clear();
        for (Future<LwjglSkinnedModel> pending : pendingWorldSkinnedImports.values()) {
            pending.cancel(false);
        }
        pendingWorldSkinnedImports.clear();
        for (Future<?> pending : pendingTerrainCellBuilds.values()) {
            pending.cancel(true);
        }
        pendingTerrainCellBuilds.clear();
        preparedTerrainCellBuilds.clear();
        pendingTerrainCellUploads.clear();
        queuedTerrainCellUploads.clear();
        for (FixedMeshBuffers buffers : fixedViewModelMeshes.values()) {
            buffers.shutdown();
        }
        fixedViewModelMeshes.clear();
        modelIconBoundsCache.clear();
        failedWorldSkinnedModels.clear();
        failedGpuSkinningModels.clear();
        CharacterAnimationMetadataResolver.clear();
        LwjglSkinnedModel.clearSharedCache();
        failedStaticModels.clear();
        synchronized (chunkLightmapCache) {
            chunkLightmapCache.clear();
        }
        if (fixedPrimitives != null) {
            fixedPrimitives.shutdown();
            fixedPrimitives = null;
        }
        textureCache.shutdown();
        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
    }

    @Override
    public DungeonRenderDebugInfo getDebugInfo() {
        return new DungeonRenderDebugInfo(
                "Tiles " + visibleTiles + " Quads " + totalQuads(),
                maxDepth,
                "lwjgl"
        );
    }

    public String sceneSummary() {
        return "depth=" + maxDepth
                + ", tiles=" + visibleTiles
                + ", floors=" + floorQuads
                + ", walls=" + wallQuads
                + ", roofs=" + roofQuads
                + ", sprites=" + spriteQuads
                + ", models=" + staticModels
                + ", batches=" + worldBatchCount
                + ", lightmap=" + lightmapWidth + "x" + lightmapHeight
                + ", lightmapChunks=" + lightmapChunkHits + "/" + lightmapChunkMisses
                + (lightmapPending ? " pending" : "")
                + ", textures=" + textureCache.textureCount();
    }

    public long windowHandle() {
        return window;
    }

    public Dimension framebufferSize() {
        if (window == NULL) {
            return new Dimension(windowWidth, windowHeight);
        }

        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        return new Dimension(Math.max(1, width[0]), Math.max(1, height[0]));
    }

    public Point framebufferPoint(double cursorX, double cursorY) {
        if (window == NULL) {
            return new Point((int) Math.round(cursorX), (int) Math.round(cursorY));
        }

        int[] windowWidthValue = new int[1];
        int[] windowHeightValue = new int[1];
        glfwGetWindowSize(window, windowWidthValue, windowHeightValue);
        int safeWindowWidth = Math.max(1, windowWidthValue[0]);
        int safeWindowHeight = Math.max(1, windowHeightValue[0]);
        Dimension framebufferSize = framebufferSize();

        int framebufferX = (int) Math.round(cursorX * framebufferSize.width / (double) safeWindowWidth);
        int framebufferY = (int) Math.round(cursorY * framebufferSize.height / (double) safeWindowHeight);
        return new Point(
                Math.max(0, Math.min(framebufferSize.width - 1, framebufferX)),
                Math.max(0, Math.min(framebufferSize.height - 1, framebufferY))
        );
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(window, true);
    }

    /** Captures the most recently presented frame for automated visual smoke checks. */
    public void captureFrontBuffer(Path outputPath) throws IOException {
        if (window == NULL || outputPath == null) {
            throw new IOException("Cannot capture an unavailable viewport.");
        }
        Dimension size = framebufferSize();
        int width = Math.max(1, size.width);
        int height = Math.max(1, size.height);
        ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);
        glReadBuffer(GL_FRONT);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int sourceY = 0; sourceY < height; sourceY++) {
            int targetY = height - sourceY - 1;
            for (int x = 0; x < width; x++) {
                int offset = (sourceY * width + x) * 4;
                int red = Byte.toUnsignedInt(rgba.get(offset));
                int green = Byte.toUnsignedInt(rgba.get(offset + 1));
                int blue = Byte.toUnsignedInt(rgba.get(offset + 2));
                int alpha = Byte.toUnsignedInt(rgba.get(offset + 3));
                image.setRGB(x, targetY, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", outputPath.toFile())) {
            throw new IOException("No PNG writer is available.");
        }
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    void beginProfileFrame(long nowNanos) {
        frameProfiler.beginFrame(nowNanos);
    }

    void recordProfilePhase(FrameProfiler.Phase phase, long nanos) {
        frameProfiler.recordPhase(phase, nanos);
    }

    public RenderSettings renderSettings() {
        return renderSettings;
    }

    public int displayRefreshRate() {
        return displayRefreshRate;
    }

    void setSimulationInterpolationAlpha(double interpolationAlpha) {
        simulationInterpolationAlpha = Math.max(0.0, Math.min(1.0, interpolationAlpha));
    }

    FrameProfiler.Snapshot profilerSnapshot() {
        return frameProfiler.snapshot();
    }

    FrameProfiler.Snapshot benchmarkProfilerSnapshot() {
        return frameProfiler.benchmarkSnapshot();
    }

    RendererResidencyMetrics rendererResidencyMetrics() {
        return new RendererResidencyMetrics(
                textureCache.textureCount(), textureCache.uploadCount(), textureCache.uploadedBytes(),
                staticModelCache.size(), pendingStaticModelImports.size(),
                worldSkinnedModelCache.size(), pendingWorldSkinnedImports.size(),
                terrainCache.size(), preparedTerrainCellBuilds.size(), pendingTerrainCellUploads.size());
    }

    void resetProfiler() {
        frameProfiler.reset();
    }

    String glVendor() {
        String value = glGetString(GL_VENDOR);
        return value == null ? "unknown" : value;
    }

    String glRenderer() {
        String value = glGetString(GL_RENDERER);
        return value == null ? "unknown" : value;
    }

    public void increaseDepth() {
        setMaxDepth(maxDepth + 1);
    }

    public void decreaseDepth() {
        setMaxDepth(maxDepth - 1);
    }

    private void setMaxDepth(int requestedDepth) {
        maxDepth = clamp(requestedDepth, MIN_VIEW_DEPTH, MAX_VIEW_DEPTH);
        GameConfiguration.setValue("renderer.prototype.maxDepth", String.valueOf(maxDepth));
    }

    private void syncViewSettingsFromConfiguration() {
        maxDepth = clamp(GameConfiguration.intValue("renderer.prototype.maxDepth", maxDepth), MIN_VIEW_DEPTH, MAX_VIEW_DEPTH);
        fovDegrees = clamp(
                GameConfiguration.doubleValue("renderer.prototype.fovDegrees", fovDegrees),
                MIN_FOV_DEGREES,
                MAX_FOV_DEGREES
        );
    }

    private void applyRenderSettings(boolean force) {
        long revision = GameConfiguration.revision();
        if (!force && revision == appliedConfigurationRevision) {
            return;
        }
        RenderSettings loaded = RenderSettings.load();
        renderSettings = benchmarkMode
                ? new RenderSettings(false, RenderSettings.FrameLimit.UNCAPPED,
                        loaded.fieldOfViewDegrees(), loaded.renderDistance(), false)
                : loaded;
        glfwSwapInterval(renderSettings.vSync() ? 1 : 0);
        appliedConfigurationRevision = revision;
    }

    private void updateDisplayRefreshRate() {
        long monitor = glfwGetPrimaryMonitor();
        org.lwjgl.glfw.GLFWVidMode mode = monitor == NULL ? null : glfwGetVideoMode(monitor);
        displayRefreshRate = mode == null ? 60 : Math.max(1, mode.refreshRate());
    }

    public boolean toggleDebug() {
        debugVisible = !debugVisible;
        if (!debugVisible) {
            glfwSetWindowTitle(window, "Aether LWJGL Dungeon Prototype");
        }
        return debugVisible;
    }

    public void setEnvironmentThemes(List<EnvironmentTheme> environmentThemes) {
        List<EnvironmentTheme> requested = environmentThemes == null || environmentThemes.isEmpty()
                ? List.of(EnvironmentTheme.defaultTheme())
                : List.copyOf(environmentThemes);
        if (requested.equals(currentEnvironmentThemes)) {
            return;
        }
        currentEnvironmentThemes = requested;
        sceneBuilder.setEnvironmentThemes(currentEnvironmentThemes);
        clearTerrainCache();
        terrainBuildKey = null;
    }

    public void setSkyboxPath(String skyboxPath) {
        String safePath = skyboxPath == null || skyboxPath.isBlank()
                ? SkyboxSpec.DEFAULT.encode()
                : skyboxPath.trim();
        if (safePath.equals(this.skyboxPath)) {
            return;
        }

        this.skyboxPath = safePath;
        this.skyboxFaceImages.clear();
        this.skyboxSpec = SkyboxSpec.parseOrDefault(safePath);
    }

    public List<EnemyLabel> getEnemyLabelsView() {
        return List.copyOf(enemyLabels);
    }

    private void drawSkybox(int framebufferWidth, int framebufferHeight) {
        if (skyboxSpec == null || !skyboxSpec.complete()) {
            return;
        }

        glDisable(GL_FOG);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        drawSkyboxCube(framebufferWidth, framebufferHeight);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void drawSkyboxCube(int framebufferWidth, int framebufferHeight) {
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        double aspect = framebufferWidth / (double) framebufferHeight;
        double top = Math.tan(Math.toRadians(fovDegrees) / 2.0) * nearPlane;
        double right = top * aspect;
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glFrustum(-right, right, -top, top, nearPlane, farPlane);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glRotated(lastLookState.pitchOffsetDegrees(), 1.0, 0.0, 0.0);
        glRotated(-currentSkyboxYawDegrees, 0.0, 1.0, 0.0);
        double size = Math.max(8.0, farPlane * 0.45);
        drawSkyboxFace(skyboxSpec.front(), -size, -size, -size, size, -size, -size, size, size, -size, -size, size, -size);
        drawSkyboxFace(skyboxSpec.back(), size, -size, size, -size, -size, size, -size, size, size, size, size, size);
        drawSkyboxFace(skyboxSpec.left(), -size, -size, size, -size, -size, -size, -size, size, -size, -size, size, size);
        drawSkyboxFace(skyboxSpec.right(), size, -size, -size, size, -size, size, size, size, size, size, size, -size);
        drawSkyboxFace(skyboxSpec.top(), -size, size, -size, size, size, -size, size, size, size, -size, size, size);
        drawSkyboxFace(skyboxSpec.bottom(), -size, -size, size, size, -size, size, size, -size, -size, -size, -size, -size);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void drawSkyboxFace(
            String path,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            double x4,
            double y4,
            double z4
    ) {
        BufferedImage image = skyboxFaceImages.computeIfAbsent(path, AssetLoader::loadImage);
        if (image == null) {
            return;
        }
        textureCache.bind(image);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        fixedPrimitives.drawTexturedQuad(
                x1, y1, z1, x2, y2, z2,
                x3, y3, z3, x4, y4, z4);
    }

    private void configureProjection(int framebufferWidth, int framebufferHeight) {
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        double aspect = framebufferWidth / (double) framebufferHeight;
        double top = Math.tan(Math.toRadians(fovDegrees) / 2.0) * nearPlane;
        double right = top * aspect;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(-right, right, -top, top, nearPlane, farPlane);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private Matrix4f projectionViewMatrix(
            int framebufferWidth,
            int framebufferHeight,
            DungeonRenderContext context,
            CameraLookState lookState,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        double aspect = framebufferWidth / (double) framebufferHeight;
        double yaw = animatedYawDegrees(context) + lookState.yawOffsetDegrees();
        Matrix4f projection = projectionMatrix.identity().perspective(
                (float) Math.toRadians(fovDegrees),
                (float) aspect,
                (float) nearPlane,
                (float) farPlane);
        Matrix4f view = viewMatrix.identity()
                .rotateX((float) Math.toRadians(lookState.pitchOffsetDegrees()))
                .rotateY((float) Math.toRadians(-yaw))
                .translate((float) -cameraX, (float) -cameraY, (float) -cameraZ);
        return currentProjectionView.set(projection).mul(view);
    }

    private void ensureLightmap(DungeonMap map) {
        if (map == null || lightmapTexture == null) {
            return;
        }
        publishReadyLightmap();
        long signature = fastLightmapSignature(map);
        if (signature == lightmapSignature) {
            return;
        }
        if (pendingLightmap != null && !pendingLightmap.isDone() && pendingLightmapSignature == signature) {
            lightmapPending = true;
            return;
        }
        LightmapJob job = prepareLightmapJob(map, signature);
        if (lightmapSignature == Long.MIN_VALUE) {
            uploadPreparedLightmap(buildLightmap(job));
            return;
        }
        if (pendingLightmap != null && !pendingLightmap.isDone()) {
            pendingLightmap.cancel(false);
        }
        pendingLightmapSignature = signature;
        lightmapPending = true;
        pendingLightmap = lightmapExecutor.submit(() -> buildLightmap(job));
    }

    private void publishReadyLightmap() {
        if (pendingLightmap == null || !pendingLightmap.isDone()) {
            lightmapPending = pendingLightmap != null;
            return;
        }
        try {
            PreparedLightmap prepared = pendingLightmap.get();
            uploadPreparedLightmap(prepared);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            LOGGER.log(Level.WARNING, "Failed to prepare chunked lightmap.", exception.getCause());
        } finally {
            pendingLightmap = null;
            pendingLightmapSignature = Long.MIN_VALUE;
            lightmapPending = false;
        }
    }

    private void uploadPreparedLightmap(PreparedLightmap prepared) {
        if (prepared == null || prepared.image() == null) {
            return;
        }
        lightmapTexture.upload(prepared.image(), true);
        currentLightmapImage = prepared.image();
        lightmapSignature = prepared.signature();
        lightmapBakeMs = prepared.bakeMs();
        lightmapWidth = prepared.image().getWidth();
        lightmapHeight = prepared.image().getHeight();
        lightmapChunkHits = prepared.chunkHits();
        lightmapChunkMisses = prepared.chunkMisses();
        synchronized (chunkLightmapCache) {
            lightmapChunkEntries = chunkLightmapCache.size();
        }
    }

    private LightmapJob prepareLightmapJob(DungeonMap map, long signature) {
        int split = OpenWorldSession.WINDOW_DIAMETER;
        boolean chunked = map.getWidth() >= split
                && map.getHeight() >= split
                && map.getWidth() % split == 0
                && map.getHeight() % split == 0
                && GameConfiguration.booleanValue("lighting.lightmap.chunkCache.enabled", true);
        if (!chunked) {
            return new LightmapJob(signature, map.getWidth(), map.getHeight(), List.of(
                    new LightmapChunk(0, 0, map.getWidth(), map.getHeight(), 0, 0,
                            copyRegion(map, 0, 0, map.getWidth(), map.getHeight()))
            ));
        }

        int chunkWidth = map.getWidth() / split;
        int chunkHeight = map.getHeight() / split;
        int lightBorder = lightmapInfluenceBorder(map);
        List<LightmapChunk> chunks = new ArrayList<>(split * split);
        for (int chunkY = 0; chunkY < split; chunkY++) {
            for (int chunkX = 0; chunkX < split; chunkX++) {
                int offsetX = chunkX * chunkWidth;
                int offsetY = chunkY * chunkHeight;
                int bakeX = Math.max(0, offsetX - lightBorder);
                int bakeY = Math.max(0, offsetY - lightBorder);
                int bakeRight = Math.min(map.getWidth(), offsetX + chunkWidth + lightBorder);
                int bakeBottom = Math.min(map.getHeight(), offsetY + chunkHeight + lightBorder);
                DungeonMap chunkMap = copyRegion(
                        map, bakeX, bakeY, bakeRight - bakeX, bakeBottom - bakeY);
                chunks.add(new LightmapChunk(
                        offsetX, offsetY, chunkWidth, chunkHeight,
                        offsetX - bakeX, offsetY - bakeY, chunkMap));
            }
        }
        return new LightmapJob(signature, map.getWidth(), map.getHeight(), chunks);
    }

    private PreparedLightmap buildLightmap(LightmapJob job) {
        long startNanos = System.nanoTime();
        int pixelsPerTile = Math.max(1, GameConfiguration.intValue("lighting.lightmap.pixelsPerTile", 4));
        BufferedImage atlas = new BufferedImage(
                Math.max(1, job.width() * pixelsPerTile),
                Math.max(1, job.height() * pixelsPerTile),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = atlas.createGraphics();
        int hits = 0;
        int misses = 0;
        try {
            for (LightmapChunk chunk : job.chunks()) {
                ChunkBakeResult chunkResult = cachedOrBakeChunk(chunk);
                if (chunkResult.cacheHit()) {
                    hits++;
                } else {
                    misses++;
                }
                int x = chunk.offsetX() * pixelsPerTile;
                int y = atlas.getHeight() - (chunk.offsetY() + chunk.height()) * pixelsPerTile;
                graphics.drawImage(chunkResult.image(), x, y, null);
            }
        } finally {
            graphics.dispose();
        }
        double bakeMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        return new PreparedLightmap(job.signature(), atlas, bakeMs, hits, misses);
    }

    private ChunkBakeResult cachedOrBakeChunk(LightmapChunk chunk) {
        long signature = deepLightmapSignature(chunk.map());
        signature = mix(signature, chunk.width());
        signature = mix(signature, chunk.height());
        signature = mix(signature, chunk.cropX());
        signature = mix(signature, chunk.cropY());
        synchronized (chunkLightmapCache) {
            BufferedImage cached = chunkLightmapCache.get(signature);
            if (cached != null) {
                return new ChunkBakeResult(cached, true);
            }
        }

        LightmapBaker.LightmapBakeResult result = lightmapBaker.bake(chunk.map());
        BufferedImage cropped = cropLightmapChunk(result.image(), chunk);
        synchronized (chunkLightmapCache) {
            chunkLightmapCache.put(signature, cropped);
        }
        return new ChunkBakeResult(cropped, false);
    }

    /**
     * Every cached chunk is baked with enough neighboring terrain and lights to
     * cover any authored light whose influence reaches its central area. The
     * overlap is discarded only after lighting and occlusion have been resolved.
     */
    private int lightmapInfluenceBorder(DungeonMap map) {
        double border = 0.0;
        for (MapLight light : map.getLightsView()) {
            if (light == null || !light.enabled()) continue;
            border = Math.max(border, light.radius()
                    + Math.max(Math.abs(light.offsetX()), Math.abs(light.offsetZ())));
        }
        return Math.max(0, (int) Math.ceil(border) + 1);
    }

    private BufferedImage cropLightmapChunk(BufferedImage baked, LightmapChunk chunk) {
        int pixelsPerTile = Math.max(1,
                GameConfiguration.intValue("lighting.lightmap.pixelsPerTile", 4));
        int width = Math.max(1, chunk.width() * pixelsPerTile);
        int height = Math.max(1, chunk.height() * pixelsPerTile);
        int sourceX = chunk.cropX() * pixelsPerTile;
        int sourceY = baked.getHeight()
                - (chunk.cropY() + chunk.height()) * pixelsPerTile;
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(baked,
                    0, 0, width, height,
                    sourceX, sourceY, sourceX + width, sourceY + height,
                    null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private DungeonMap copyRegion(DungeonMap source, int offsetX, int offsetY, int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themes = new int[height][width];
        int[][] heights = new int[height][width];
        String[][] mobAreas = new String[height][width];
        Map<MapPaintData.Layer, String[][]> paintLayers = new LinkedHashMap<>();
        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
            paintLayers.put(layer, new String[height][width]);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sourceX = offsetX + x;
                int sourceY = offsetY + y;
                tiles[y][x] = source.getTile(sourceX, sourceY);
                themes[y][x] = source.getEnvironmentThemeIndex(sourceX, sourceY);
                heights[y][x] = source.getHeightLevel(sourceX, sourceY);
                mobAreas[y][x] = source.getMobAreaId(sourceX, sourceY);
                for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                    paintLayers.get(layer)[y][x] = source.getPaintBrushId(layer, sourceX, sourceY);
                }
            }
        }

        List<MapLight> lights = new ArrayList<>();
        for (MapLight light : source.getLightsView()) {
            if (light == null) {
                continue;
            }
            if (light.x() < offsetX || light.y() < offsetY
                    || light.x() >= offsetX + width || light.y() >= offsetY + height) {
                continue;
            }
            lights.add(new MapLight(
                    light.id(),
                    light.x() - offsetX,
                    light.y() - offsetY,
                    light.colorRgb(),
                    light.radius(),
                    light.intensity(),
                    light.heightOffset(),
                    light.offsetX(),
                    light.offsetZ(),
                    light.flickerAmount(),
                    light.enabled()
            ));
        }

        return new DungeonMap(
                tiles,
                themes,
                MapPaintData.of(
                        width,
                        height,
                        paintLayers.get(MapPaintData.Layer.FLOOR),
                        paintLayers.get(MapPaintData.Layer.WALL),
                        paintLayers.get(MapPaintData.Layer.DOOR),
                        paintLayers.get(MapPaintData.Layer.ROOF)
                ),
                MapGeometryData.of(width, height, heights),
                MobAreaData.of(width, height, mobAreas),
                source.getLightingSettings(),
                lights
        );
    }

    /** Constant-time validity key used on every rendered frame. */
    private long fastLightmapSignature(DungeonMap map) {
        long hash = 1469598103934665603L;
        hash = mix(hash, System.identityHashCode(map));
        hash = mix(hash, map.getWidth());
        hash = mix(hash, map.getHeight());
        hash = mix(hash, map.renderRevision());
        MapLightingSettings settings = map.getLightingSettings();
        hash = mix(hash, settings.hashCode());
        hash = mix(hash, GameConfiguration.intValue("lighting.lightmap.pixelsPerTile", 4));
        hash = mix(hash, GameConfiguration.intValue("lighting.maxLights", 64));
        hash = mix(hash, GameConfiguration.booleanValue("lighting.enabled", true) ? 1 : 0);
        hash = mix(hash, GameConfiguration.booleanValue("lighting.occlusion.enabled", true) ? 1 : 0);
        return hash;
    }

    /** Content key used only while preparing/reusing a chunk bake. */
    private long deepLightmapSignature(DungeonMap map) {
        long hash = 1469598103934665603L;
        hash = mix(hash, map.getWidth());
        hash = mix(hash, map.getHeight());
        MapLightingSettings settings = map.getLightingSettings();
        hash = mix(hash, settings.lightingEnabled() ? 1 : 0);
        hash = mix(hash, settings.ambientColorRgb());
        hash = mix(hash, Double.doubleToLongBits(settings.ambientIntensity()));
        hash = mix(hash, settings.fogEnabled() ? 1 : 0);
        hash = mix(hash, settings.fogColorRgb());
        hash = mix(hash, Double.doubleToLongBits(settings.fogDensity()));
        hash = mix(hash, GameConfiguration.intValue("lighting.lightmap.pixelsPerTile", 4));
        hash = mix(hash, GameConfiguration.intValue("lighting.maxLights", 64));
        hash = mix(hash, GameConfiguration.booleanValue("lighting.enabled", true) ? 1 : 0);
        hash = mix(hash, GameConfiguration.booleanValue("lighting.occlusion.enabled", true) ? 1 : 0);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                hash = mix(hash, map.getTile(x, y).ordinal());
                hash = mix(hash, map.getHeightLevel(x, y));
            }
        }
        for (MapLight light : map.getLightsView()) {
            hash = mix(hash, light.id().hashCode());
            hash = mix(hash, light.x());
            hash = mix(hash, light.y());
            hash = mix(hash, light.colorRgb());
            hash = mix(hash, Double.doubleToLongBits(light.radius()));
            hash = mix(hash, Double.doubleToLongBits(light.intensity()));
            hash = mix(hash, Double.doubleToLongBits(light.heightOffset()));
            hash = mix(hash, Double.doubleToLongBits(light.offsetX()));
            hash = mix(hash, Double.doubleToLongBits(light.offsetZ()));
            hash = mix(hash, light.enabled() ? 1 : 0);
        }
        return hash;
    }

    private long mix(long hash, long value) {
        hash ^= value;
        return hash * 1099511628211L;
    }

    private void configureCamera(DungeonRenderContext context, CameraLookState lookState) {
        double yaw = animatedYawDegrees(context) + lookState.yawOffsetDegrees();
        double cameraX = animatedCameraX(context);
        double cameraZ = animatedCameraZ(context);
        glRotated(lookState.pitchOffsetDegrees(), 1.0, 0.0, 0.0);
        glRotated(-yaw, 0.0, 1.0, 0.0);
        glTranslated(
                -cameraX,
                -cameraY(context, cameraX, cameraZ),
                -cameraZ
        );
    }

    private double cameraY(DungeonRenderContext context, double cameraX, double cameraZ) {
        return TerrainGeometry.groundYAtWorld(context.map(), cameraX, cameraZ) + eyeHeight;
    }

    private double animatedYawDegrees(DungeonRenderContext context) {
        return GridDirection.yawDegrees(context.direction()) - Math.toDegrees(context.cameraRotationRadians());
    }

    private double animatedCameraX(DungeonRenderContext context) {
        return context.playerX()
                + 0.5
                + GridDirection.forwardX(context.direction()) * context.cameraOffsetForward()
                + GridDirection.rightX(context.direction()) * context.cameraOffsetSide();
    }

    private double animatedCameraZ(DungeonRenderContext context) {
        return context.playerY()
                + 0.5
                + GridDirection.forwardY(context.direction()) * context.cameraOffsetForward()
                + GridDirection.rightY(context.direction()) * context.cameraOffsetSide();
    }

    private List<EnemyLabel> projectEnemyLabels(
            DungeonRenderContext context,
            CameraLookState lookState,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (context == null || context.playerCharacter() == null || context.entities() == null) {
            return List.of();
        }

        DifficultyResolver.DifficultyRating playerRating = DifficultyResolver.ratePlayer(context.playerCharacter());
        List<EnemyLabel> labels = new ArrayList<>();
        for (MapEntity entity : context.entities()) {
            EnemyLabel label = projectEnemyLabel(context, lookState, framebufferWidth, framebufferHeight, playerRating, entity);
            if (label != null) {
                labels.add(label);
            }
        }
        return labels;
    }

    private EnemyLabel projectEnemyLabel(
            DungeonRenderContext context,
            CameraLookState lookState,
            int framebufferWidth,
            int framebufferHeight,
            DifficultyResolver.DifficultyRating playerRating,
            MapEntity entity
    ) {
        if (entity == null
                || entity.getType() != Library.EntityType.ENEMY
                || entity.getMonster() == null
                || context.map() == null
                || context.map().isOutOfBounds(entity.getX(), entity.getY())
                || (context.map().getTile(entity.getX(), entity.getY()).isWallLike() && !entity.shouldRenderOnWall())) {
            return null;
        }

        double tileDistance = Math.hypot(entity.getX() - context.playerX(), entity.getY() - context.playerY());
        if (tileDistance > Math.min(maxDepth, MAX_DIFFICULTY_LABEL_DEPTH)
                || !hasLineOfSight(context, entity.getX(), entity.getY())) {
            return null;
        }

        Point screenPoint = projectWorldPoint(
                context,
                lookState,
                entity.getX() + 0.5,
                TerrainGeometry.groundYAtWorld(context.map(), entity.getX() + 0.5, entity.getY() + 0.5)
                        + Math.max(0.45, 0.90 * entity.getVisualScale()),
                entity.getY() + 0.5,
                framebufferWidth,
                framebufferHeight
        );
        if (screenPoint == null) {
            return null;
        }

        DifficultyResolver.DifficultyComparison comparison = DifficultyResolver.compare(
                playerRating,
                DifficultyResolver.rateMonster(entity.getMonster())
        );
        return new EnemyLabel(screenPoint.x, screenPoint.y, comparison.compactLabel(), comparison.band());
    }

    private Point projectWorldPoint(
            DungeonRenderContext context,
            CameraLookState lookState,
            double worldX,
            double worldY,
            double worldZ,
            int framebufferWidth,
            int framebufferHeight
    ) {
        double cameraX = animatedCameraX(context);
        double cameraZ = animatedCameraZ(context);
        double dx = worldX - cameraX;
        double dy = worldY - cameraY(context, cameraX, cameraZ);
        double dz = worldZ - cameraZ;

        double yawRadians = Math.toRadians(-(animatedYawDegrees(context) + lookState.yawOffsetDegrees()));
        double yawCos = Math.cos(yawRadians);
        double yawSin = Math.sin(yawRadians);
        double viewX = dx * yawCos + dz * yawSin;
        double yawedZ = -dx * yawSin + dz * yawCos;

        double pitchRadians = Math.toRadians(lookState.pitchOffsetDegrees());
        double pitchCos = Math.cos(pitchRadians);
        double pitchSin = Math.sin(pitchRadians);
        double viewY = dy * pitchCos - yawedZ * pitchSin;
        double viewZ = dy * pitchSin + yawedZ * pitchCos;

        if (viewZ >= -nearPlane) {
            return null;
        }

        double aspect = framebufferWidth / (double) framebufferHeight;
        double tanHalfFov = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        double ndcX = (viewX / -viewZ) / (tanHalfFov * aspect);
        double ndcY = (viewY / -viewZ) / tanHalfFov;
        if (ndcX < -1.1 || ndcX > 1.1 || ndcY < -1.1 || ndcY > 1.1) {
            return null;
        }

        int screenX = (int) Math.round((ndcX + 1.0) * 0.5 * framebufferWidth);
        int screenY = (int) Math.round((1.0 - ndcY) * 0.5 * framebufferHeight);
        return new Point(screenX, screenY);
    }

    private boolean hasLineOfSight(DungeonRenderContext context, int targetX, int targetY) {
        return context != null && LineOfSight.between(context.map(), context.playerX(), context.playerY(),
                targetX, targetY, LineOfSight.Blocker.WALL_LIKE, true);
    }

    private void drawStaticModel(LwjglDungeonSceneBuilder.ModelInstance instance) {
        if (instance.characterModel() != null && instance.characterModel().hasModel()) {
            LwjglSkinnedModel skinned = getWorldSkinnedModel(instance.characterModel());
            if (skinned != null) {
                drawWorldSkinnedModelLit(instance, skinned);
                return;
            }
        }
        LwjglStaticModel model = cachedStaticModel(instance.assetPath());
        if (model == null) {
            staticModelCacheMissesThisFrame++;
            model = GameConfiguration.booleanValue("renderer.staticModel.loadVisibleImmediately", true)
                    ? getStaticModel(instance.assetPath())
                    : null;
            if (model == null) {
                enqueueStaticModelPreload(instance.assetPath());
            }
        }
        if (model == null) {
            if (instance.fallbackSprite() != null) {
                staticModelFallbacksThisFrame++;
                renderDevice.renderWorld(
                        worldBatchBuilder.build(List.of(instance.fallbackSprite())),
                        currentProjectionView,
                        lightmapTexture,
                        currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                        currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                        currentCameraX,
                        currentCameraY,
                        currentCameraZ,
                        currentLightingSettings,
                        withReservedPlayerLight(List.of()));
            }
            return;
        }
        staticModelCacheHitsThisFrame++;

        double scale = model.normalizedScaleForHeight(instance.height());
        Matrix4f modelMatrix = staticModelMatrix.identity()
                .translate((float) instance.centerX(), (float) instance.baseY(), (float) instance.centerZ())
                .rotateY((float) Math.toRadians(instance.yawDegrees()))
                .rotateX((float) Math.toRadians(instance.pitchDegrees()))
                .rotateZ((float) Math.toRadians(instance.rollDegrees()))
                .scale((float) (scale * Math.max(0.05, instance.scaleMultiplier())))
                .translate((float) -model.centerX(), (float) -model.baseY(), (float) -model.centerZ());

        List<RuntimeLight> dynamicLights = dynamicLightsForModel(instance);
        renderDevice.renderStaticMeshes(
                model.meshes(), currentProjectionView, modelMatrix, lightmapTexture,
                currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                currentCameraX, currentCameraY, currentCameraZ,
                currentLightingSettings, instance.brightness(), dynamicLights);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);
    }

    private LwjglStaticModel cachedStaticModel(String assetPath) {
        String normalizedPath = normalizeStaticModelPath(assetPath);
        if (normalizedPath.isBlank() || failedStaticModels.contains(normalizedPath)) {
            return null;
        }
        return staticModelCache.get(normalizedPath);
    }

    private List<RuntimeLight> dynamicLightsForModel(LwjglDungeonSceneBuilder.ModelInstance instance) {
        if (instance == null || currentRenderMap == null || !GameConfiguration.booleanValue("lighting.enabled", true)) {
            return List.of();
        }
        int maxLights = Math.max(currentPlayerLanternLight == null ? 0 : 1,
                Math.min(8, GameConfiguration.intValue("lighting.dynamic.maxLights", 8)));
        if (maxLights == 0) {
            return List.of();
        }
        ensureLightSpatialIndex(currentRenderMap);
        List<RuntimeLight> mapLights = lightSpatialIndex == null
                ? List.of()
                : lightSpatialIndex.forModel(instance.centerX(), instance.centerZ(),
                Math.max(0, maxLights - (currentPlayerLanternLight == null ? 0 : 1)));
        return withReservedPlayerLight(mapLights);
    }

    private List<RuntimeLight> dynamicLightsNearCamera(DungeonMap map) {
        if (map == null || !GameConfiguration.booleanValue("lighting.enabled", true)) {
            return List.of();
        }
        MapLightingSettings settings = map.getLightingSettings();
        if (settings != null && !settings.lightingEnabled()) {
            return List.of();
        }
        int maxLights = Math.max(0, Math.min(8, GameConfiguration.intValue("lighting.dynamic.maxLights", 8)));
        if (maxLights == 0 || map.getLightsView().isEmpty()) {
            return List.of();
        }
        double maxRange = Math.max(maxDepth + 2.0, GameConfiguration.doubleValue("lighting.dynamic.transitionBridgeRange", 16.0));
        ensureLightSpatialIndex(map);
        return lightSpatialIndex == null
                ? List.of()
                : lightSpatialIndex.nearCamera(currentCameraX, currentCameraZ, maxRange, maxLights);
    }

    private RuntimeLight playerLanternLight(GameState gameState) {
        if (gameState == null || !GameConfiguration.booleanValue("lighting.enabled", true)
                || currentLightingSettings == null || !currentLightingSettings.lightingEnabled()) {
            return null;
        }
        InventorySystem.Item lantern = LanternSystem.activeLantern(gameState.getInventory());
        if (lantern == null) {
            return null;
        }
        var light = lantern.getLanternDefinition();
        double flickerWave = Math.sin(System.nanoTime() / 1_000_000_000.0 * 7.3)
                * 0.65 + Math.sin(System.nanoTime() / 1_000_000_000.0 * 13.1) * 0.35;
        double renderedIntensity = light.intensity()
                * Math.max(0.0, 1.0 + flickerWave * light.flickerAmount());
        return new RuntimeLight(
                currentCameraX,
                currentCameraY - Math.max(0.05, eyeHeight * 0.25),
                currentCameraZ,
                light.colorRgb(), light.radius(), renderedIntensity, light.flickerAmount(), 0.0);
    }

    private List<RuntimeLight> withReservedPlayerLight(List<RuntimeLight> mapLights) {
        if (currentPlayerLanternLight == null) {
            return mapLights == null ? List.of() : mapLights;
        }
        int maximum = Math.max(1, Math.min(8,
                GameConfiguration.intValue("lighting.dynamic.maxLights", 8)));
        List<RuntimeLight> combined = new ArrayList<>(maximum);
        combined.add(currentPlayerLanternLight);
        if (mapLights != null) {
            for (RuntimeLight light : mapLights) {
                if (combined.size() >= maximum) {
                    break;
                }
                combined.add(light);
            }
        }
        return List.copyOf(combined);
    }

    private float[] sampleBattleLight(double worldX, double worldZ, float minimum) {
        float[] sampled = sampleWorldLight(worldX, worldZ, minimum);
        if (currentPlayerLanternLight == null) {
            return sampled;
        }
        int color = currentPlayerLanternLight.colorRgb();
        float intensity = (float) Math.max(0.0, currentPlayerLanternLight.intensity());
        sampled[0] = Math.max(sampled[0], ((color >> 16) & 0xFF) / 255.0f * intensity);
        sampled[1] = Math.max(sampled[1], ((color >> 8) & 0xFF) / 255.0f * intensity);
        sampled[2] = Math.max(sampled[2], (color & 0xFF) / 255.0f * intensity);
        return sampled;
    }

    private void ensureLightSpatialIndex(DungeonMap map) {
        if (map == null) {
            lightSpatialIndex = null;
            lightSpatialIndexMap = null;
            lightSpatialIndexRevision = Long.MIN_VALUE;
            return;
        }
        if (lightSpatialIndex != null
                && lightSpatialIndexMap == map
                && lightSpatialIndexRevision == map.renderRevision()) {
            return;
        }
        lightSpatialIndex = new LightSpatialIndex(map);
        lightSpatialIndexMap = map;
        lightSpatialIndexRevision = map.renderRevision();
    }

    private void renderGatheringToolViewModel(GameState.MiningViewModelState viewModelState, int framebufferWidth, int framebufferHeight) {
        if (viewModelState == null || !viewModelState.visible()) {
            return;
        }

        String assetPath = switch (viewModelState.toolType()) {
            case WOODCUTTING -> "assets/3D/gatheringTool/toReplace_axe.glb";
            case FISHING -> "assets/3D/gatheringTool/toReplace_fishing_rod_stick.glb";
            default -> "assets/3D/gatheringTool/toReplace_pickaxe.glb";
        };

        LwjglStaticModel model = getStaticModel(assetPath);
        if (model == null) {
            return;
        }

        String prefix = viewModelState.toolType().configurationPrefix();
        double progress = smoothStep(viewModelState.progress());
        double windup = GameConfiguration.doubleValue(prefix + ".viewModel.windupDegrees", 25.0);
        double successDegrees = GameConfiguration.doubleValue(prefix + ".viewModel.successDegrees", -55.0);
        double failureDegrees = GameConfiguration.doubleValue(prefix + ".viewModel.failureDegrees", -20.0);
        double successPenetration = GameConfiguration.doubleValue(prefix + ".viewModel.successPenetration", 0.32);
        double failurePenetration = GameConfiguration.doubleValue(prefix + ".viewModel.failurePenetration", 0.10);
        double angleDegrees = 0.0;
        double penetration = 0.0;

        switch (viewModelState.motion()) {
            case WINDUP -> {
                angleDegrees = windup * progress;
            }
            case SUCCESS_STRIKE -> {
                angleDegrees = windup + (successDegrees - windup) * progress;
                penetration = successPenetration * progress;
            }
            case FAILURE_STRIKE -> {
                angleDegrees = windup + (failureDegrees - windup) * progress;
                penetration = failurePenetration * progress;
            }
            case SUCCESS_RECOVERY -> {
                angleDegrees = successDegrees * (1.0 - progress);
                penetration = successPenetration * (1.0 - progress);
            }
            case FAILURE_RECOVERY -> {
                angleDegrees = failureDegrees * (1.0 - progress);
                penetration = failurePenetration * (1.0 - progress);
            }
            default -> {}
        }

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        double aspect = Math.max(1.0, framebufferWidth) / (double) Math.max(1, framebufferHeight);
        double tanHalfFov = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        double top = nearPlane * tanHalfFov;
        double right = top * aspect;
        glFrustum(-right, right, -top, top, nearPlane, farPlane);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glClear(GL_DEPTH_BUFFER_BIT);
        float[] localLight = sampleViewModelLight();

        glTranslated(
                GameConfiguration.doubleValue(prefix + ".viewModel.positionX", 0.42),
                GameConfiguration.doubleValue(prefix + ".viewModel.positionY", -0.46),
                GameConfiguration.doubleValue(prefix + ".viewModel.positionZ", -0.92) - penetration);
        glRotated(GameConfiguration.doubleValue(prefix + ".viewModel.rotationX", -18.0), 1.0, 0.0, 0.0);
        glRotated(GameConfiguration.doubleValue(prefix + ".viewModel.rotationY", 0.0), 0.0, 1.0, 0.0);
        glRotated(GameConfiguration.doubleValue(prefix + ".viewModel.rotationZ", -24.0), 0.0, 0.0, 1.0);
        double axisX = GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisX", 0.0);
        double axisY = GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisY", 0.0);
        double axisZ = GameConfiguration.doubleValue(prefix + ".viewModel.swingAxisZ", 1.0);
        if (Math.abs(axisX) + Math.abs(axisY) + Math.abs(axisZ) < 0.0001) {
            axisZ = 1.0;
        }
        glRotated(angleDegrees, axisX, axisY, axisZ);

        double scale = model.normalizedScaleForHeight(Math.max(0.05,
                GameConfiguration.doubleValue(prefix + ".viewModel.height", 0.76)));
        glScaled(scale, scale, scale);
        glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());

        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            drawModelMesh(mesh, localLight);
        }

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);
    }

    private void drawWorldSkinnedModelLit(
            LwjglDungeonSceneBuilder.ModelInstance instance,
            LwjglSkinnedModel model
    ) {
        CharacterModelDefinition.AnimationSlot currentSlot = resolveWorldAnimationSlot(
                model,
                instance.animationSlot());
        CharacterModelDefinition.AnimationSlot previousSlot = currentSlot;
        boolean crossfading = instance.previousAnimationSlot() != null
                && instance.animationBlend() < 1.0;
        if (crossfading) {
            previousSlot = resolveWorldAnimationSlot(model, instance.previousAnimationSlot());
        }

        LwjglSkinnedModel.Pose currentPose = model.pose(
                currentSlot, instance.animationElapsedSeconds());
        LwjglSkinnedModel.Pose previousPose = crossfading
                ? model.pose(previousSlot, instance.previousAnimationElapsedSeconds())
                : currentPose;

        double scale = model.normalizedScaleForHeight(instance.height());
        double adjustedScale = scale * Math.max(0.05, instance.scaleMultiplier());
        Matrix4f modelMatrix = skinnedModelMatrix.identity()
                .translate(
                        (float) instance.centerX(),
                        (float) (instance.baseY()
                                + instance.characterModel().verticalOffset()),
                        (float) instance.centerZ())
                .rotateY((float) Math.toRadians(
                        instance.characterModel().facingRotationDegrees()
                                + instance.yawDegrees()))
                .rotateX((float) Math.toRadians(instance.pitchDegrees()))
                .rotateZ((float) Math.toRadians(instance.rollDegrees()))
                .scale((float) adjustedScale)
                .translate(
                        (float) -model.centerX(),
                        (float) -model.baseY(),
                        (float) -model.centerZ());
        List<RuntimeLight> dynamicLights = dynamicLightsForModel(instance);
        if (GameConfiguration.booleanValue("renderer.gpuSkinning.enabled", true)
                && !failedGpuSkinningModels.contains(instance.characterModel())) {
            try {
                renderDevice.prepareGpuSkinning(previousPose, currentPose,
                        crossfading ? instance.animationBlend() : 1.0);
                renderDevice.renderGpuSkinnedMeshes(
                        model.meshes(), previousPose, currentPose,
                        currentProjectionView, modelMatrix, lightmapTexture,
                        currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                        currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                        currentCameraX, currentCameraY, currentCameraZ,
                        currentLightingSettings, instance.brightness(), dynamicLights);
                glEnable(GL_TEXTURE_2D);
                glColor4f(1, 1, 1, 1);
                return;
            } catch (RuntimeException exception) {
                failedGpuSkinningModels.add(instance.characterModel());
                LOGGER.log(Level.WARNING,
                        "GPU skinning fallback for " + instance.characterModel().modelPath(), exception);
            }
        }

        LwjglSkinnedModel.Frame frame = model.skin(currentSlot, instance.animationElapsedSeconds());
        if (crossfading) {
            frame = LwjglSkinnedModel.blendFrames(
                    model.skin(previousSlot, instance.previousAnimationElapsedSeconds()),
                    frame,
                    instance.animationBlend());
        }
        renderDevice.renderSkinnedMeshes(
                model.meshes(), frame.meshPositions(), currentProjectionView, modelMatrix,
                lightmapTexture,
                currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                currentCameraX, currentCameraY, currentCameraZ,
                currentLightingSettings, instance.brightness(), dynamicLights);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    private static CharacterModelDefinition.AnimationSlot resolveWorldAnimationSlot(
            LwjglSkinnedModel model,
            CharacterModelDefinition.AnimationSlot requested
    ) {
        CharacterModelDefinition.AnimationSlot safe = requested == null
                ? CharacterModelDefinition.AnimationSlot.IDLE
                : requested;
        if (model.hasClip(safe)) {
            return safe;
        }
        return model.hasClip(CharacterModelDefinition.AnimationSlot.IDLE)
                ? CharacterModelDefinition.AnimationSlot.IDLE
                : safe;
    }

    private LwjglSkinnedModel getWorldSkinnedModel(CharacterModelDefinition definition) {
        if (definition == null || failedWorldSkinnedModels.contains(definition)) return null;
        LwjglSkinnedModel cached = worldSkinnedModelCache.get(definition);
        if (cached != null) return cached;
        enqueueWorldSkinnedModelPreload(definition);
        Future<LwjglSkinnedModel> pending = pendingWorldSkinnedImports.get(definition);
        boolean mayWait = GameConfiguration.booleanValue(
                "renderer.staticModel.loadVisibleImmediately", true);
        if (pending == null || (!pending.isDone() && !mayWait)) {
            return null;
        }
        try {
            LwjglSkinnedModel loaded = pending.get();
            pendingWorldSkinnedImports.remove(definition);
            worldSkinnedModelCache.put(definition, loaded);
            trimWorldSkinnedModelCache();
            return loaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException exception) {
            pendingWorldSkinnedImports.remove(definition);
            failedWorldSkinnedModels.add(definition);
            LOGGER.log(Level.WARNING,
                    "Failed to load animated world model " + definition.modelPath(), exception.getCause());
            return null;
        }
    }

    private void enqueueWorldSkinnedModelPreload(CharacterModelDefinition definition) {
        if (definition == null || !definition.hasModel()
                || worldSkinnedModelCache.containsKey(definition)
                || failedWorldSkinnedModels.contains(definition)
                || pendingWorldSkinnedImports.containsKey(definition)) {
            return;
        }
        int maximumPending = Math.max(4, GameConfiguration.intValue(
                "renderer.staticModel.maxPendingImports", 64));
        if (pendingWorldSkinnedImports.size() >= maximumPending) {
            return;
        }
        pendingWorldSkinnedImports.put(definition,
                staticModelExecutor.submit(() -> LwjglSkinnedModel.loadCached(definition)));
    }

    private void trimWorldSkinnedModelCache() {
        int maximum = Math.max(8, GameConfiguration.intValue(
                "renderer.skinnedModel.gpuCache.maxEntries", 64));
        while (worldSkinnedModelCache.size() > maximum) {
            var iterator = worldSkinnedModelCache.entrySet().iterator();
            Map.Entry<CharacterModelDefinition, LwjglSkinnedModel> eldest = iterator.next();
            iterator.remove();
            if (renderDevice != null) {
                renderDevice.evictSkinnedModel(eldest.getValue());
            }
        }
    }

    private void drawModelMesh(LwjglStaticModel.Mesh mesh) {
        drawModelMesh(mesh, null);
    }

    private void drawModelMesh(LwjglStaticModel.Mesh mesh, float[] lightTint) {
        if (mesh.texture() == null) {
            glDisable(GL_TEXTURE_2D);
        } else {
            glEnable(GL_TEXTURE_2D);
            textureCache.bind(mesh.texture());
        }
        float red = mesh.red();
        float green = mesh.green();
        float blue = mesh.blue();
        if (lightTint != null && lightTint.length >= 3) {
            red *= lightTint[0];
            green *= lightTint[1];
            blue *= lightTint[2];
        }
        glColor4f(red, green, blue, mesh.alpha());
        FixedMeshBuffers buffers = fixedViewModelMeshes.computeIfAbsent(
                mesh, FixedMeshBuffers::new);
        buffers.draw(mesh.texture() != null);
    }

    private void renderItemModelIcons(
            List<ItemModelIconRenderQueue.Request> requests,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (requests == null || requests.isEmpty()) return;

        boolean cullEnabled = glIsEnabled(GL_CULL_FACE);
        boolean depthEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        boolean alphaTestEnabled = glIsEnabled(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.03f);
        glDisable(GL_FOG);
        org.lwjgl.opengl.GL20.glUseProgram(0);
        glEnable(GL_SCISSOR_TEST);
        textureCache.invalidateBindings();

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        try {
            for (ItemModelIconRenderQueue.Request request : requests) {
                java.awt.Rectangle bounds = request.bounds();
                int viewportX = clamp(bounds.x, 0, framebufferWidth - 1);
                int viewportTop = clamp(bounds.y, 0, framebufferHeight - 1);
                int viewportWidth = Math.max(1,
                        Math.min(bounds.width, framebufferWidth - viewportX));
                int viewportHeight = Math.max(1,
                        Math.min(bounds.height, framebufferHeight - viewportTop));
                int viewportY = framebufferHeight - viewportTop - viewportHeight;
                if (viewportY < 0) continue;

                LwjglStaticModel model = getStaticModel(request.modelPath());
                if (model == null) continue;
                ItemModelIconProfile profile = request.profile();
                ModelIconBounds rotated = modelIconBounds(model, profile);
                double largestExtent = Math.max(0.000001,
                        Math.max(rotated.width(), rotated.height()));
                double scale = 1.56 * profile.zoom() / largestExtent;
                double outlineScale = scale * modelIconOutlineScale(
                        viewportWidth, viewportHeight, profile.zoom());

                glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
                glScissor(viewportX, viewportY, viewportWidth, viewportHeight);
                glClear(GL_DEPTH_BUFFER_BIT);
                glMatrixMode(GL_PROJECTION);
                glLoadIdentity();
                glOrtho(-1.0, 1.0, -1.0, 1.0, -1000.0, 1000.0);
                glMatrixMode(GL_MODELVIEW);
                glLoadIdentity();
                applyModelIconTransform(model, profile, rotated, outlineScale);
                for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                    drawModelMeshOutline(mesh);
                }

                // The enlarged silhouette establishes the border. Resetting depth lets the
                // normally-sized textured model cover its center without z-fighting.
                glClear(GL_DEPTH_BUFFER_BIT);
                glLoadIdentity();
                applyModelIconTransform(model, profile, rotated, scale);
                for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                    drawModelMesh(mesh);
                }
            }
        } finally {
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            glDisable(GL_SCISSOR_TEST);
            if (cullEnabled) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            if (depthEnabled) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            if (blendEnabled) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            if (alphaTestEnabled) glEnable(GL_ALPHA_TEST); else glDisable(GL_ALPHA_TEST);
            glEnable(GL_TEXTURE_2D);
            glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private void applyModelIconTransform(
            LwjglStaticModel model,
            ItemModelIconProfile profile,
            ModelIconBounds rotated,
            double scale
    ) {
        glTranslated(profile.offsetX() * 0.84, -profile.offsetY() * 0.84, 0.0);
        glScaled(scale, scale, scale);
        glTranslated(-rotated.centerX(), -rotated.centerY(), 0.0);
        glRotated(profile.rotationX(), 1.0, 0.0, 0.0);
        glRotated(profile.rotationY(), 0.0, 1.0, 0.0);
        glRotated(profile.rotationZ(), 0.0, 0.0, 1.0);
        glTranslated(-model.centerX(), -model.centerY(), -model.centerZ());
    }

    private double modelIconOutlineScale(int width, int height, double zoom) {
        double minimumDimension = Math.max(1.0, Math.min(width, height));
        double visibleRadius = 0.39 * minimumDimension * Math.max(0.1, zoom);
        double scale = 1.0 + 1.25 / Math.max(1.0, visibleRadius);
        return Math.max(1.025, Math.min(1.14, scale));
    }

    private void drawModelMeshOutline(LwjglStaticModel.Mesh mesh) {
        if (mesh.texture() == null) {
            glDisable(GL_TEXTURE_2D);
        } else {
            glEnable(GL_TEXTURE_2D);
            textureCache.bind(mesh.texture());
        }
        glColor4f(0.045f, 0.04f, 0.035f, Math.max(0.92f, mesh.alpha()));
        FixedMeshBuffers buffers = fixedViewModelMeshes.computeIfAbsent(mesh, FixedMeshBuffers::new);
        buffers.draw(mesh.texture() != null);
    }

    private ModelIconBounds modelIconBounds(
            LwjglStaticModel model,
            ItemModelIconProfile profile
    ) {
        ModelIconBoundsKey key = new ModelIconBoundsKey(model, profile);
        ModelIconBounds cached = modelIconBoundsCache.get(key);
        if (cached != null) return cached;
        Matrix4d rotation = new Matrix4d()
                .rotateX(Math.toRadians(profile.rotationX()))
                .rotateY(Math.toRadians(profile.rotationY()))
                .rotateZ(Math.toRadians(profile.rotationZ()));
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            float[] positions = mesh.positions();
            for (int offset = 0; offset + 2 < positions.length; offset += 3) {
                Vector4d point = new Vector4d(
                        positions[offset] - model.centerX(),
                        positions[offset + 1] - model.centerY(),
                        positions[offset + 2] - model.centerZ(), 1.0);
                rotation.transform(point);
                minimumX = Math.min(minimumX, point.x);
                minimumY = Math.min(minimumY, point.y);
                maximumX = Math.max(maximumX, point.x);
                maximumY = Math.max(maximumY, point.y);
            }
        }
        if (!Double.isFinite(minimumX) || !Double.isFinite(minimumY)) {
            ModelIconBounds fallback = new ModelIconBounds(-0.5, -0.5, 0.5, 0.5);
            modelIconBoundsCache.put(key, fallback);
            return fallback;
        }
        ModelIconBounds result = new ModelIconBounds(
                minimumX, minimumY, maximumX, maximumY);
        modelIconBoundsCache.put(key, result);
        return result;
    }

    private float[] sampleViewModelLight() {
        float minimum = (float) clamp(
                GameConfiguration.doubleValue("renderer.prototype.viewModel.lightMinimum", 0.22),
                0.0,
                1.0
        );
        return sampleBattleLight(currentCameraX, currentCameraZ, minimum);
    }

    private float[] sampleWorldLight(double worldX, double worldZ, float minimum) {
        if (currentLightmapImage == null
                || currentRenderMap == null
                || currentLightingSettings == null
                || !GameConfiguration.booleanValue("lighting.enabled", true)
                || !currentLightingSettings.lightingEnabled()) {
            return new float[] {1f, 1f, 1f};
        }

        int width = Math.max(1, currentLightmapImage.getWidth());
        int height = Math.max(1, currentLightmapImage.getHeight());
        double normalizedX = worldX / Math.max(1.0, currentRenderMap.getWidth());
        double normalizedZ = worldZ / Math.max(1.0, currentRenderMap.getHeight());
        int sampleX = clamp((int) Math.round(normalizedX * (width - 1)), 0, width - 1);
        int sampleY = clamp((int) Math.round((1.0 - normalizedZ) * (height - 1)), 0, height - 1);
        int rgb = currentLightmapImage.getRGB(sampleX, sampleY);
        float safeMinimum = (float) clamp(minimum, 0.0, 1.0);
        return new float[] {
                Math.max(safeMinimum, ((rgb >> 16) & 0xFF) / 255.0f),
                Math.max(safeMinimum, ((rgb >> 8) & 0xFF) / 255.0f),
                Math.max(safeMinimum, (rgb & 0xFF) / 255.0f)
        };
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private void scheduleTerrainCellPreloads(org.main.core.AetherGameRuntime runtime) {
        if (runtime == null || runtime.gameState() == null) {
            return;
        }
        GameState gameState = runtime.gameState();
        long revision = gameState.getPreparedOpenWorldTerrainRevision();
        if (revision == observedPreparedTerrainRevision) {
            return;
        }
        observedPreparedTerrainRevision = revision;
        for (OpenWorldSession.TerrainPrefetch prefetch
                : gameState.getPreparedOpenWorldTerrainPrefetches()) {
            DungeonMap map = prefetch.map();
            if (map == null) {
                continue;
            }
            List<EnvironmentTheme> themes = prefetch.environmentThemes().isEmpty()
                    ? List.of(EnvironmentTheme.defaultTheme())
                    : prefetch.environmentThemes();
            TerrainBuildKey key = new TerrainBuildKey(
                    map, map.renderRevision(), wallHeight, roofPitchHeight, themes.hashCode());
            if (key.equals(terrainBuildKey)
                    || preparedTerrainCellBuilds.containsKey(key)
                    || pendingTerrainCellBuilds.containsKey(key)) {
                continue;
            }
            pendingTerrainCellBuilds.put(key, staticModelExecutor.submit(() -> {
                LwjglDungeonSceneBuilder builder = new LwjglDungeonSceneBuilder(textureManager, themes);
                DungeonRenderContext context = new DungeonRenderContext(
                        map, List.of(), gameState.getPlayerCharacter(),
                        map.getWidth() / 2, map.getHeight() / 2, 0,
                        windowWidth, windowHeight, 0.0, 0.0, 0.0);
                return builder.buildTerrainCells(
                        context, TERRAIN_CELL_SIZE, wallHeight, roofPitchHeight);
            }));
        }
    }

    private void processTerrainCellPreloads() {
        if (pendingTerrainCellBuilds.isEmpty()) {
            return;
        }
        List<TerrainBuildKey> completed = new ArrayList<>();
        pendingTerrainCellBuilds.forEach((key, future) -> {
            if (future.isDone()) {
                completed.add(key);
            }
        });
        for (TerrainBuildKey key : completed) {
            Future<Map<LwjglDungeonSceneBuilder.CellCoordinate,
                    LwjglDungeonSceneBuilder.TerrainCell>> future = pendingTerrainCellBuilds.remove(key);
            if (future == null) {
                continue;
            }
            try {
                Map<LwjglDungeonSceneBuilder.CellCoordinate,
                        LwjglDungeonSceneBuilder.TerrainCell> cells = future.get();
                if (key.map().renderRevision() == key.renderRevision()) {
                    preparedTerrainCellBuilds.put(key, cells);
                    for (LwjglDungeonSceneBuilder.TerrainCell cell : cells.values()) {
                        TerrainCacheKey cellKey = new TerrainCacheKey(
                                key, cell.coordinate().x(), cell.coordinate().y());
                        if (!terrainCache.containsKey(cellKey)
                                && queuedTerrainCellUploads.add(cellKey)) {
                            pendingTerrainCellUploads.addLast(
                                    new PendingTerrainCellUpload(cellKey, cell.scene()));
                        }
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException exception) {
                LOGGER.log(Level.WARNING, "Failed to prepare prefetched terrain cells.", exception.getCause());
            }
        }
        while (preparedTerrainCellBuilds.size() > 12) {
            TerrainBuildKey eldest = preparedTerrainCellBuilds.keySet().iterator().next();
            preparedTerrainCellBuilds.remove(eldest);
        }
    }

    private void processTerrainCellUploads() {
        if (renderDevice == null || pendingTerrainCellUploads.isEmpty()) {
            return;
        }
        long budgetNanos = Math.max(0L, (long) (GameConfiguration.doubleValue(
                "renderer.terrainCell.uploadBudgetMs", 1.0) * 1_000_000.0));
        long started = System.nanoTime();
        do {
            PendingTerrainCellUpload pending = pendingTerrainCellUploads.pollFirst();
            if (pending == null) {
                break;
            }
            queuedTerrainCellUploads.remove(pending.key());
            if (pending.key().build().map().renderRevision()
                    != pending.key().build().renderRevision()
                    || terrainCache.containsKey(pending.key())) {
                continue;
            }
            TerrainCacheEntry uploaded = new TerrainCacheEntry(
                    pending.scene(), renderDevice.prepareWorld(
                            worldBatchBuilder.build(pending.scene().quads())));
            terrainCache.put(pending.key(), uploaded);
        } while (!pendingTerrainCellUploads.isEmpty()
                && (budgetNanos <= 0L || System.nanoTime() - started < budgetNanos));
        trimTerrainCache(pinnedTerrainKeys);
    }

    private void scheduleStaticModelPreloads(DungeonRenderContext context, org.main.core.AetherGameRuntime runtime) {
        if (context == null) {
            return;
        }
        int preloadDepth = maxDepth + Math.max(0, GameConfiguration.intValue("renderer.staticModel.preloadExtraDepth", 4));
        double maxDistanceSquared = preloadDepth * preloadDepth;
        for (MapEntity entity : context.entities()) {
            if (entity == null || !entity.hasVisibleStaticModel()) {
                continue;
            }
            double dx = entity.getX() + 0.5 - (context.playerX() + 0.5);
            double dz = entity.getY() + 0.5 - (context.playerY() + 0.5);
            if (dx * dx + dz * dz <= maxDistanceSquared) {
                enqueueStaticModelPreload(entity.getStaticModelPath());
            }
        }

        if (runtime == null || runtime.gameState() == null) {
            return;
        }
        for (MapDesignLibrary.CustomFurnitureDefinition furniture : runtime.gameState().getCustomFurniture()) {
            enqueueStaticModelPreload(furniture.modelPath());
        }
        for (MapDesignLibrary.CustomGatheringNode node : runtime.gameState().getCustomGatheringNodes()) {
            for (String modelPath : node.modelPaths()) {
                enqueueStaticModelPreload(modelPath);
            }
        }
        for (String modelPath : runtime.gameState().getPrefetchedOpenWorldModelPaths()) {
            enqueueStaticModelPreload(modelPath);
        }
        for (CharacterModelDefinition model : runtime.gameState().getPrefetchedOpenWorldCharacterModels()) {
            enqueueWorldSkinnedModelPreload(model);
        }
    }

    private void enqueueStaticModelPreload(String assetPath) {
        String normalizedPath = normalizeStaticModelPath(assetPath);
        if (normalizedPath.isBlank()
                || staticModelCache.containsKey(normalizedPath)
                || failedStaticModels.contains(normalizedPath)
                || queuedStaticModelPreloads.contains(normalizedPath)) {
            return;
        }
        int maximumPending = Math.max(4, GameConfiguration.intValue(
                "renderer.staticModel.maxPendingImports", 64));
        if (pendingStaticModelImports.size() >= maximumPending) {
            return;
        }
        pendingStaticModelPreloads.addLast(normalizedPath);
        queuedStaticModelPreloads.add(normalizedPath);
        pendingStaticModelImports.put(normalizedPath,
                staticModelExecutor.submit(() -> LwjglStaticModel.load(normalizedPath)));
    }

    private void processStaticModelPreloads() {
        staticModelPreloadsThisFrame = 0;
        int budget = Math.max(0, GameConfiguration.intValue("renderer.staticModel.preloadPerFrame", 2));
        int scansRemaining = pendingStaticModelPreloads.size();
        while (staticModelPreloadsThisFrame < budget
                && scansRemaining-- > 0
                && !pendingStaticModelPreloads.isEmpty()) {
            String assetPath = pendingStaticModelPreloads.removeFirst();
            Future<LwjglStaticModel> pending = pendingStaticModelImports.get(assetPath);
            if (pending != null && !pending.isDone()) {
                pendingStaticModelPreloads.addLast(assetPath);
                continue;
            }
            queuedStaticModelPreloads.remove(assetPath);
            if (staticModelCache.containsKey(assetPath) || failedStaticModels.contains(assetPath)) {
                pendingStaticModelImports.remove(assetPath);
                continue;
            }
            publishStaticModelImport(assetPath, pending, false);
            staticModelPreloadsThisFrame++;
        }
        staticModelPreloadQueueSize = pendingStaticModelPreloads.size();
    }

    private LwjglStaticModel getStaticModel(String assetPath) {
        String normalizedPath = normalizeStaticModelPath(assetPath);
        if (normalizedPath.isBlank() || failedStaticModels.contains(normalizedPath)) {
            return null;
        }
        LwjglStaticModel cached = staticModelCache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }
        if (!pendingStaticModelImports.containsKey(normalizedPath)) {
            enqueueStaticModelPreload(normalizedPath);
        }
        Future<LwjglStaticModel> pending = pendingStaticModelImports.get(normalizedPath);
        boolean mayWait = GameConfiguration.booleanValue(
                "renderer.staticModel.loadVisibleImmediately", true);
        return publishStaticModelImport(normalizedPath, pending, mayWait);
    }

    private LwjglStaticModel publishStaticModelImport(
            String normalizedPath,
            Future<LwjglStaticModel> pending,
            boolean mayWait
    ) {
        if (pending == null || (!mayWait && !pending.isDone())) {
            return null;
        }
        try {
            LwjglStaticModel loaded = pending.get();
            pendingStaticModelImports.remove(normalizedPath);
            pendingStaticModelPreloads.remove(normalizedPath);
            queuedStaticModelPreloads.remove(normalizedPath);
            if (loaded != null) {
                staticModelCache.put(normalizedPath, loaded);
                trimStaticModelCache();
            }
            return loaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException exception) {
            pendingStaticModelImports.remove(normalizedPath);
            pendingStaticModelPreloads.remove(normalizedPath);
            queuedStaticModelPreloads.remove(normalizedPath);
            failedStaticModels.add(normalizedPath);
            LOGGER.log(Level.WARNING,
                    "Failed to load static model " + normalizedPath, exception.getCause());
            return null;
        }
    }

    private void trimStaticModelCache() {
        int maximum = Math.max(16, GameConfiguration.intValue(
                "renderer.staticModel.gpuCache.maxEntries", 128));
        while (staticModelCache.size() > maximum) {
            var iterator = staticModelCache.entrySet().iterator();
            Map.Entry<String, LwjglStaticModel> eldest = iterator.next();
            LwjglStaticModel model = eldest.getValue();
            iterator.remove();
            if (renderDevice != null) {
                renderDevice.evictStaticModel(model);
            }
            for (LwjglStaticModel.Mesh mesh : model.meshes()) {
                FixedMeshBuffers buffers = fixedViewModelMeshes.remove(mesh);
                if (buffers != null) {
                    buffers.shutdown();
                }
            }
            modelIconBoundsCache.keySet().removeIf(key -> key.model() == model);
        }
    }

    private String normalizeStaticModelPath(String assetPath) {
        return assetPath == null ? "" : assetPath.trim().replace('\\', '/');
    }

    private int totalQuads() {
        return floorQuads + wallQuads + roofQuads + spriteQuads;
    }

    private void updateWindowTitle() {
        int fps = smoothedFrameMs <= 0.0 ? 0 : (int) Math.round(1000.0 / smoothedFrameMs);
        glfwSetWindowTitle(
                window,
                "Aether LWJGL Mesh Prototype | FPS " + fps
                        + " | Frame " + String.format("%.2f", smoothedFrameMs) + " ms"
                        + " | Depth " + maxDepth
                        + " | Tiles " + visibleTiles
                        + " | Floors " + floorQuads
                        + " | Walls " + wallQuads
                        + " | Roofs " + roofQuads
                        + " | Sprites " + spriteQuads
                        + " | Models " + staticModels
                        + " | ModelCache " + staticModelCache.size()
                        + "/" + staticModelPreloadQueueSize
                        + " h" + staticModelCacheHitsThisFrame
                        + " m" + staticModelCacheMissesThisFrame
                        + " f" + staticModelFallbacksThisFrame
                        + " | Batches " + worldBatchCount
                        + " | LM " + lightmapWidth + "x" + lightmapHeight
                        + " | Textures " + textureCache.textureCount()
        );
    }

    private List<String> viewportDebugLines() {
        int fps = smoothedFrameMs <= 0.0 ? 0 : (int) Math.round(1000.0 / smoothedFrameMs);
        List<String> lines = new ArrayList<>();
        lines.add("Renderer lwjgl");
        lines.add("FPS " + fps);
        lines.add("Frame " + String.format("%.2f", smoothedFrameMs) + " ms");
        lines.add("Depth " + maxDepth);
        lines.add("Tiles " + visibleTiles);
        lines.add("Quads F" + floorQuads + " W" + wallQuads + " R" + roofQuads + " S" + spriteQuads);
        lines.add("Models " + staticModels);
        lines.add("Model cache " + staticModelCache.size()
                + " queue " + staticModelPreloadQueueSize
                + " loaded " + staticModelPreloadsThisFrame);
        lines.add("Model hits " + staticModelCacheHitsThisFrame
                + " miss " + staticModelCacheMissesThisFrame
                + " fallback " + staticModelFallbacksThisFrame);
        lines.add("Batches " + worldBatchCount);
        lines.add("Lightmap " + lightmapWidth + "x" + lightmapHeight);
        lines.add(String.format("Bake %.1f ms", lightmapBakeMs));
        lines.add("LM chunks " + lightmapChunkHits + "/" + lightmapChunkMisses
                + " cache " + lightmapChunkEntries
                + (lightmapPending ? " pending" : ""));
        lines.add("Textures " + textureCache.textureCount());
        if (renderSettings.performanceOverlayVisible()) {
            lines.addAll(frameProfiler.overlayLines());
        }
        if (lastLookState.active()
                || Math.abs(lastLookState.yawOffsetDegrees()) > 0.001
                || Math.abs(lastLookState.pitchOffsetDegrees()) > 0.001) {
            lines.add("Look "
                    + String.format("%.1f", lastLookState.yawOffsetDegrees())
                    + "/"
                    + String.format("%.1f", lastLookState.pitchOffsetDegrees()));
        }
        return lines;
    }

    record RendererResidencyMetrics(
            int textures,
            long textureUploads,
            long textureUploadedBytes,
            int staticModels,
            int pendingStaticModels,
            int skinnedModels,
            int pendingSkinnedModels,
            int terrainCells,
            int preparedTerrainBuilds,
            int pendingTerrainUploads
    ) {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TerrainBuildKey(
            DungeonMap map,
            long renderRevision,
            double wallHeight,
            double roofPitchHeight,
            int themeSignature
    ) {
    }

    private record TerrainCacheKey(TerrainBuildKey build, int cellX, int cellY) {
    }

    private record TerrainCacheEntry(
            LwjglDungeonSceneBuilder.Scene scene,
            LwjglRenderDevice.PreparedWorld world
    ) {
    }

    private record PendingTerrainCellUpload(
            TerrainCacheKey key,
            LwjglDungeonSceneBuilder.Scene scene
    ) {
    }

    private record ModelIconBounds(
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY
    ) {
        private double width() {
            return maximumX - minimumX;
        }

        private double height() {
            return maximumY - minimumY;
        }

        private double centerX() {
            return (minimumX + maximumX) * 0.5;
        }

        private double centerY() {
            return (minimumY + maximumY) * 0.5;
        }
    }

    private record ModelIconBoundsKey(
            LwjglStaticModel model,
            ItemModelIconProfile profile
    ) {
    }

    private static final class FixedMeshBuffers {
        private final int positionBuffer = glGenBuffers();
        private final int textureBuffer = glGenBuffers();
        private final int indexBuffer = glGenBuffers();
        private final int indexCount;

        private FixedMeshBuffers(LwjglStaticModel.Mesh mesh) {
            FloatBuffer positions = BufferUtils.createFloatBuffer(mesh.positions().length)
                    .put(mesh.positions()).flip();
            FloatBuffer coordinates = BufferUtils.createFloatBuffer(mesh.texCoords().length)
                    .put(mesh.texCoords()).flip();
            IntBuffer indices = BufferUtils.createIntBuffer(mesh.indices().length)
                    .put(mesh.indices()).flip();
            glBindBuffer(GL_ARRAY_BUFFER, positionBuffer);
            glBufferData(GL_ARRAY_BUFFER, positions, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, textureBuffer);
            glBufferData(GL_ARRAY_BUFFER, coordinates, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            indexCount = mesh.indices().length;
        }

        private void draw(boolean textured) {
            glBindBuffer(GL_ARRAY_BUFFER, positionBuffer);
            glEnableClientState(GL_VERTEX_ARRAY);
            glVertexPointer(3, GL_FLOAT, 0, 0L);
            if (textured) {
                glBindBuffer(GL_ARRAY_BUFFER, textureBuffer);
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glTexCoordPointer(2, GL_FLOAT, 0, 0L);
            } else {
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
            glDisableClientState(GL_VERTEX_ARRAY);
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private void shutdown() {
            glDeleteBuffers(positionBuffer);
            glDeleteBuffers(textureBuffer);
            glDeleteBuffers(indexBuffer);
        }
    }

    private void trimTerrainCache(Set<TerrainCacheKey> pinned) {
        int maximum = Math.max(36, GameConfiguration.intValue(
                "renderer.terrainCell.gpuCache.maxEntries", 192));
        while (terrainCache.size() > maximum) {
            Map.Entry<TerrainCacheKey, TerrainCacheEntry> eldest = terrainCache.entrySet().stream()
                    .filter(entry -> pinned == null || !pinned.contains(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (eldest == null) {
                break;
            }
            terrainCache.remove(eldest.getKey());
            if (renderDevice != null) {
                renderDevice.releasePreparedWorld(eldest.getValue().world());
            }
        }
    }

    private void clearTerrainCache() {
        if (renderDevice != null) {
            for (TerrainCacheEntry entry : terrainCache.values()) {
                renderDevice.releasePreparedWorld(entry.world());
            }
        }
        terrainCache.clear();
        visibleTerrainWorlds.clear();
        pinnedTerrainKeys.clear();
    }

    private static boolean terrainCellVisible(
            LwjglDungeonSceneBuilder.TerrainCell cell,
            double cameraX,
            double cameraZ,
            double yawDegrees,
            double fieldOfViewDegrees,
            int maxDepth
    ) {
        double closestX = Math.max(cell.minX(), Math.min(cameraX, cell.maxX()));
        double closestZ = Math.max(cell.minZ(), Math.min(cameraZ, cell.maxZ()));
        double nearestDx = closestX - cameraX;
        double nearestDz = closestZ - cameraZ;
        double paddedDepth = maxDepth + 2.0;
        if (nearestDx * nearestDx + nearestDz * nearestDz > paddedDepth * paddedDepth) {
            return false;
        }
        double centerX = (cell.minX() + cell.maxX()) * 0.5;
        double centerZ = (cell.minZ() + cell.maxZ()) * 0.5;
        double radius = Math.hypot(cell.maxX() - cell.minX(), cell.maxZ() - cell.minZ()) * 0.5 + 1.0;
        return ConservativeFrustum.includes(
                centerX - cameraX, centerZ - cameraZ,
                yawDegrees, fieldOfViewDegrees, paddedDepth, radius);
    }

    private static final class LightSpatialIndex {
        private static final int CELL_SIZE = 8;
        private final List<RuntimeLight> lights;
        private final int columns;
        private final int rows;
        private final Object[] modelSelections;
        private int modelSelectionMaximum = -1;
        private int cameraCellX = Integer.MIN_VALUE;
        private int cameraCellZ = Integer.MIN_VALUE;
        private int cameraRangeTenths = Integer.MIN_VALUE;
        private int cameraMaximum = Integer.MIN_VALUE;
        private List<RuntimeLight> cameraSelection = List.of();

        private LightSpatialIndex(DungeonMap map) {
            List<RuntimeLight> resolved = new ArrayList<>();
            for (MapLight light : map.getLightsView()) {
                if (light == null || !light.enabled()) {
                    continue;
                }
                double x = light.x() + 0.5 + light.offsetX();
                double z = light.y() + 0.5 + light.offsetZ();
                double y = TerrainGeometry.groundYAtWorld(map, x, z) + light.heightOffset();
                resolved.add(new RuntimeLight(
                        x, y, z, light.colorRgb(), Math.max(0.1, light.radius()),
                        light.intensity(), light.flickerAmount(), 0.0));
            }
            lights = List.copyOf(resolved);
            columns = Math.max(1, (map.getWidth() + CELL_SIZE - 1) / CELL_SIZE);
            rows = Math.max(1, (map.getHeight() + CELL_SIZE - 1) / CELL_SIZE);
            modelSelections = new Object[columns * rows];
        }

        @SuppressWarnings("unchecked")
        private List<RuntimeLight> forModel(double x, double z, int maximum) {
            int cellX = Math.max(0, Math.min(columns - 1,
                    Math.floorDiv((int) Math.floor(x), CELL_SIZE)));
            int cellZ = Math.max(0, Math.min(rows - 1,
                    Math.floorDiv((int) Math.floor(z), CELL_SIZE)));
            if (modelSelectionMaximum != maximum) {
                java.util.Arrays.fill(modelSelections, null);
                modelSelectionMaximum = maximum;
            }
            int index = cellZ * columns + cellX;
            List<RuntimeLight> cached = (List<RuntimeLight>) modelSelections[index];
            if (cached != null) {
                return cached;
            }
            double centerX = cellX * CELL_SIZE + CELL_SIZE * 0.5;
            double centerZ = cellZ * CELL_SIZE + CELL_SIZE * 0.5;
            double padding = Math.sqrt(2.0) * CELL_SIZE * 0.5;
            List<RuntimeLight> selected = new ArrayList<>();
            for (RuntimeLight light : lights) {
                double dx = light.x() - centerX;
                double dz = light.z() - centerZ;
                double range = light.radius() + padding;
                if (dx * dx + dz * dz <= range * range) {
                    selected.add(light);
                }
            }
            selected.sort((left, right) -> Double.compare(
                    distanceSquared(left, centerX, centerZ),
                    distanceSquared(right, centerX, centerZ)));
            cached = List.copyOf(selected.subList(0, Math.min(Math.max(0, maximum), selected.size())));
            modelSelections[index] = cached;
            return cached;
        }

        private List<RuntimeLight> nearCamera(double x, double z, double range, int maximum) {
            int cellX = Math.floorDiv((int) Math.floor(x), CELL_SIZE);
            int cellZ = Math.floorDiv((int) Math.floor(z), CELL_SIZE);
            int rangeTenths = (int) Math.ceil(range * 10.0);
            if (cellX == cameraCellX && cellZ == cameraCellZ
                    && rangeTenths == cameraRangeTenths && maximum == cameraMaximum) {
                return cameraSelection;
            }
            double centerX = cellX * CELL_SIZE + CELL_SIZE * 0.5;
            double centerZ = cellZ * CELL_SIZE + CELL_SIZE * 0.5;
            double paddedRange = range + Math.sqrt(2.0) * CELL_SIZE * 0.5;
            double rangeSquared = paddedRange * paddedRange;
            List<RuntimeLight> selected = new ArrayList<>();
            for (RuntimeLight light : lights) {
                if (distanceSquared(light, centerX, centerZ) <= rangeSquared) {
                    selected.add(light);
                }
            }
            selected.sort((left, right) -> Double.compare(
                    distanceSquared(left, centerX, centerZ),
                    distanceSquared(right, centerX, centerZ)));
            cameraSelection = List.copyOf(selected.subList(
                    0, Math.min(Math.max(0, maximum), selected.size())));
            cameraCellX = cellX;
            cameraCellZ = cellZ;
            cameraRangeTenths = rangeTenths;
            cameraMaximum = maximum;
            return cameraSelection;
        }

        private static double distanceSquared(RuntimeLight light, double x, double z) {
            double dx = light.x() - x;
            double dz = light.z() - z;
            return dx * dx + dz * dz;
        }
    }

    public record EnemyLabel(
            int x,
            int y,
            String text,
            DifficultyResolver.DifficultyBand band
    ) {
    }

    private record LightmapJob(
            long signature,
            int width,
            int height,
            List<LightmapChunk> chunks
    ) {
    }

    private record LightmapChunk(
            int offsetX,
            int offsetY,
            int width,
            int height,
            int cropX,
            int cropY,
            DungeonMap map
    ) {
    }

    private record PreparedLightmap(
            long signature,
            BufferedImage image,
            double bakeMs,
            int chunkHits,
            int chunkMisses
    ) {
    }

    private record ChunkBakeResult(
            BufferedImage image,
            boolean cacheHit
    ) {
    }
}

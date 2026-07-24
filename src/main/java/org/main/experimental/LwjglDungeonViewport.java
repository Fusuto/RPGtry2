package org.main.experimental;

import org.joml.Matrix4f;
import org.main.battle.DifficultyResolver;
import org.main.content.CharacterModelDefinition;
import org.main.core.GameConfiguration;
import org.main.core.GameState;
import org.main.core.Library;
import org.main.core.OpenWorldSession;
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
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import static org.lwjgl.system.MemoryUtil.NULL;

public class LwjglDungeonViewport implements RealtimeDungeonViewport {
    private static final int MAX_DIFFICULTY_LABEL_DEPTH = 3;
    private static final int MIN_VIEW_DEPTH = 4;
    private static final int MAX_VIEW_DEPTH = 32;
    private static final double MIN_FOV_DEGREES = 45.0;
    private static final double MAX_FOV_DEGREES = 100.0;
    private static final Logger LOGGER = Logger.getLogger(LwjglDungeonViewport.class.getName());

    private final LwjglTextureCache textureCache = new LwjglTextureCache();
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
    private final Map<String, LwjglStaticModel> staticModelCache = new HashMap<>();
    private final Map<CharacterModelDefinition, LwjglSkinnedModel> worldSkinnedModelCache = new HashMap<>();
    private final Set<CharacterModelDefinition> failedWorldSkinnedModels = new HashSet<>();
    private final Set<String> failedStaticModels = new HashSet<>();
    private final LwjglDungeonSceneBuilder sceneBuilder;
    private LwjglRenderDevice renderDevice;
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
    private Matrix4f currentProjectionView = new Matrix4f();
    private DungeonMap currentRenderMap;
    private MapLightingSettings currentLightingSettings = MapLightingSettings.defaultSettings();
    private double currentCameraX;
    private double currentCameraY;
    private double currentCameraZ;
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
    private int visibleTiles;
    private int floorQuads;
    private int wallQuads;
    private int roofQuads;
    private int spriteQuads;
    private int staticModels;
    private double lastFrameMs;
    private double smoothedFrameMs = 16.0;
    private CameraLookState lastLookState = CameraLookState.centered();
    private double currentSkyboxYawDegrees;
    private List<EnemyLabel> enemyLabels = List.of();
    private String skyboxPath = "";
    private SkyboxSpec skyboxSpec = SkyboxSpec.DEFAULT;
    private final Map<String, BufferedImage> skyboxFaceImages = new HashMap<>();

    public LwjglDungeonViewport(TextureManager textureManager, List<EnvironmentTheme> environmentThemes) {
        List<EnvironmentTheme> safeThemes = environmentThemes == null || environmentThemes.isEmpty()
                ? List.of(EnvironmentTheme.defaultTheme())
                : new ArrayList<>(environmentThemes);
        this.sceneBuilder = new LwjglDungeonSceneBuilder(textureManager, safeThemes);
        this.windowWidth = Math.max(320, GameConfiguration.intValue("renderer.prototype.windowWidth", 1280));
        this.windowHeight = Math.max(240, GameConfiguration.intValue("renderer.prototype.windowHeight", 720));
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
    }

    @Override
    public void initialize() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW.");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, GameConfiguration.intValue("renderer.opengl.major", 4));
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, GameConfiguration.intValue("renderer.opengl.minor", 1));
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        window = glfwCreateWindow(windowWidth, windowHeight, "Aether LWJGL Dungeon Prototype", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new IllegalStateException("Unable to create LWJGL prototype window.");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        createCapabilities();
        renderDevice = new LwjglRenderDevice(textureCache);
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
        syncViewSettingsFromConfiguration();
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
            currentProjectionView = new Matrix4f(projectionView);
            currentRenderMap = sceneContext.map();
            currentLightingSettings = sceneContext.map().getLightingSettings();
            currentCameraX = cameraX;
            currentCameraY = cameraY;
            currentCameraZ = cameraZ;
            ensureLightmap(sceneContext.map());
            LwjglDungeonSceneBuilder.Scene scene = sceneBuilder.build(
                    sceneContext,
                    maxDepth,
                    wallHeight,
                    roofPitchHeight,
                    cameraYawDegrees
            );
            visibleTiles = scene.visibleTiles();
            floorQuads = scene.floorQuads();
            wallQuads = scene.wallQuads();
            roofQuads = scene.roofQuads();
            spriteQuads = scene.spriteQuads();
            staticModels = scene.models().size();
            enemyLabels = runtime != null && runtime.gameState().isBattleMode() ? List.of() : projectEnemyLabels(
                    sceneContext,
                    safeLookState,
                    framebufferWidth,
                    framebufferHeight
            );

            renderDevice.renderWorld(
                    worldBatchBuilder.build(scene.quads()),
                    projectionView,
                    lightmapTexture,
                    sceneContext.map().getWidth(),
                    sceneContext.map().getHeight(),
                    cameraX,
                    cameraY,
                    cameraZ,
                    sceneContext.map().getLightingSettings());
            worldBatchCount = renderDevice.batchCount();
            worldRenderedIndices = renderDevice.renderedIndices();

            configureProjection(framebufferWidth, framebufferHeight);
            configureCamera(sceneContext, safeLookState);
            for (LwjglDungeonSceneBuilder.ModelInstance model : scene.models()) {
                drawStaticModel(model);
            }
            if (runtime != null && runtime.gameState() != null && runtime.gameState().isBattleMode()) {
                runtime.battleRenderer().setProjectedActorPositions(
                        battleSceneRenderer.render(context, safeLookState, runtime, framebufferWidth, framebufferHeight));
            } else if (runtime != null && runtime.gameState() != null) {
                renderGatheringToolViewModel(runtime.gameState().getGatheringViewModelState(), framebufferWidth, framebufferHeight);
            }
        } else {
            visibleTiles = 0;
            floorQuads = 0;
            wallQuads = 0;
            roofQuads = 0;
            spriteQuads = 0;
            staticModels = 0;
            worldBatchCount = 0;
            worldRenderedIndices = 0;
            enemyLabels = List.of();
        }

        glDisable(GL_FOG);
        if (overlayRenderer != null && runtime != null) {
            overlayRenderer.setEnemyLabels(enemyLabels);
            overlayRenderer.setViewportDebugLines(viewportDebugLines());
            overlayRenderer.render(runtime, framebufferWidth, framebufferHeight);
        }

        glfwSwapBuffers(window);
        lastFrameMs = (System.nanoTime() - frameStart) / 1_000_000.0;
        smoothedFrameMs = smoothedFrameMs * 0.90 + lastFrameMs * 0.10;
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
        if (renderDevice != null) {
            renderDevice.shutdown();
            renderDevice = null;
        }
        if (lightmapTexture != null) {
            lightmapTexture.shutdown();
            lightmapTexture = null;
        }
        staticModelCache.clear();
        worldSkinnedModelCache.clear();
        failedWorldSkinnedModels.clear();
        failedStaticModels.clear();
        synchronized (chunkLightmapCache) {
            chunkLightmapCache.clear();
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

    public boolean toggleDebug() {
        debugVisible = !debugVisible;
        if (!debugVisible) {
            glfwSetWindowTitle(window, "Aether LWJGL Dungeon Prototype");
        }
        return debugVisible;
    }

    public void setEnvironmentThemes(List<EnvironmentTheme> environmentThemes) {
        sceneBuilder.setEnvironmentThemes(environmentThemes);
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
        glBegin(GL_QUADS);
        glTexCoord2d(0.0, 0.0);
        glVertex3d(x1, y1, z1);
        glTexCoord2d(1.0, 0.0);
        glVertex3d(x2, y2, z2);
        glTexCoord2d(1.0, 1.0);
        glVertex3d(x3, y3, z3);
        glTexCoord2d(0.0, 1.0);
        glVertex3d(x4, y4, z4);
        glEnd();
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
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(fovDegrees),
                (float) aspect,
                (float) nearPlane,
                (float) farPlane);
        Matrix4f view = new Matrix4f()
                .rotateX((float) Math.toRadians(lookState.pitchOffsetDegrees()))
                .rotateY((float) Math.toRadians(-yaw))
                .translate((float) -cameraX, (float) -cameraY, (float) -cameraZ);
        return projection.mul(view);
    }

    private void ensureLightmap(DungeonMap map) {
        if (map == null || lightmapTexture == null) {
            return;
        }
        publishReadyLightmap();
        long signature = lightmapSignature(map);
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
                    new LightmapChunk(0, 0, map.getWidth(), map.getHeight(), copyRegion(map, 0, 0, map.getWidth(), map.getHeight()))
            ));
        }

        int chunkWidth = map.getWidth() / split;
        int chunkHeight = map.getHeight() / split;
        List<LightmapChunk> chunks = new ArrayList<>(split * split);
        for (int chunkY = 0; chunkY < split; chunkY++) {
            for (int chunkX = 0; chunkX < split; chunkX++) {
                int offsetX = chunkX * chunkWidth;
                int offsetY = chunkY * chunkHeight;
                DungeonMap chunkMap = copyRegion(map, offsetX, offsetY, chunkWidth, chunkHeight);
                chunks.add(new LightmapChunk(offsetX, offsetY, chunkWidth, chunkHeight, chunkMap));
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
        long signature = lightmapSignature(chunk.map());
        synchronized (chunkLightmapCache) {
            BufferedImage cached = chunkLightmapCache.get(signature);
            if (cached != null) {
                return new ChunkBakeResult(cached, true);
            }
        }

        LightmapBaker.LightmapBakeResult result = lightmapBaker.bake(chunk.map());
        synchronized (chunkLightmapCache) {
            chunkLightmapCache.put(signature, result.image());
        }
        return new ChunkBakeResult(result.image(), false);
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

    private long lightmapSignature(DungeonMap map) {
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
        return yawDegrees(context.direction()) - Math.toDegrees(context.cameraRotationRadians());
    }

    private double animatedCameraX(DungeonRenderContext context) {
        return context.playerX()
                + 0.5
                + forwardX(context.direction()) * context.cameraOffsetForward()
                + rightX(context.direction()) * context.cameraOffsetSide();
    }

    private double animatedCameraZ(DungeonRenderContext context) {
        return context.playerY()
                + 0.5
                + forwardY(context.direction()) * context.cameraOffsetForward()
                + rightY(context.direction()) * context.cameraOffsetSide();
    }

    private double yawDegrees(int direction) {
        return switch (direction) {
            case 1 -> -90.0;
            case 2 -> 180.0;
            case 3 -> 90.0;
            default -> 0.0;
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

    private int rightX(int direction) {
        return switch (direction) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
    }

    private int rightY(int direction) {
        return switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
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
        int x0 = context.playerX();
        int y0 = context.playerY();
        int dx = Math.abs(targetX - x0);
        int dy = Math.abs(targetY - y0);
        int sx = x0 < targetX ? 1 : -1;
        int sy = y0 < targetY ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;

        while (x != targetX || y != targetY) {
            int previousX = x;
            int previousY = y;
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += sx;
            }
            if (doubledError < dx) {
                error += dx;
                y += sy;
            }

            if (context.map().isWallLike(x, y)
                    || TerrainGeometry.edgeKind(context.map(), previousX, previousY, x, y) == org.main.engine.TerrainEdgeKind.CLIFF) {
                return false;
            }

            if (x == targetX && y == targetY) {
                return true;
            }
        }
        return true;
    }

    private void drawStaticModel(LwjglDungeonSceneBuilder.ModelInstance instance) {
        if (instance.characterModel() != null && instance.characterModel().hasModel()) {
            LwjglSkinnedModel skinned = getWorldSkinnedModel(instance.characterModel());
            if (skinned != null && skinned.hasClip(instance.animationSlot())) {
                drawWorldSkinnedModel(instance, skinned);
                return;
            }
        }
        LwjglStaticModel model = getStaticModel(instance.assetPath());
        if (model == null) {
            if (instance.fallbackSprite() != null) {
                renderDevice.renderWorld(
                        worldBatchBuilder.build(List.of(instance.fallbackSprite())),
                        currentProjectionView,
                        lightmapTexture,
                        currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                        currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                        currentCameraX,
                        currentCameraY,
                        currentCameraZ,
                        currentLightingSettings);
            }
            return;
        }

        double scale = model.normalizedScaleForHeight(instance.height());
        Matrix4f modelMatrix = new Matrix4f()
                .translate((float) instance.centerX(), (float) instance.baseY(), (float) instance.centerZ())
                .scale((float) scale)
                .translate((float) -model.centerX(), (float) -model.baseY(), (float) -model.centerZ());

        for (LwjglStaticModel.Mesh mesh : model.meshes()) {
            renderDevice.renderStaticMesh(
                    mesh,
                    currentProjectionView,
                    modelMatrix,
                    lightmapTexture,
                    currentRenderMap == null ? 1 : currentRenderMap.getWidth(),
                    currentRenderMap == null ? 1 : currentRenderMap.getHeight(),
                    currentCameraX,
                    currentCameraY,
                    currentCameraZ,
                    currentLightingSettings);
        }
        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);
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

    private void drawWorldSkinnedModel(LwjglDungeonSceneBuilder.ModelInstance instance, LwjglSkinnedModel model) {
        double elapsed = System.nanoTime() / 1_000_000_000.0;
        LwjglSkinnedModel.Frame frame = model.skin(instance.animationSlot(), elapsed);
        glPushMatrix();
        glTranslated(instance.centerX(), instance.baseY() + instance.characterModel().verticalOffset(), instance.centerZ());
        glRotated(instance.characterModel().facingRotationDegrees(), 0, 1, 0);
        double scale = model.normalizedScaleForHeight(instance.height());
        glScaled(scale, scale, scale);
        glTranslated(-model.centerX(), -model.baseY(), -model.centerZ());
        for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
            LwjglSkinnedModel.SkinnedMesh mesh = model.meshes().get(meshIndex);
            float[] positions = frame.meshPositions().get(meshIndex);
            if (mesh.material().texture() == null) glDisable(GL_TEXTURE_2D);
            else { glEnable(GL_TEXTURE_2D); textureCache.bind(mesh.material().texture()); }
            glColor4f(mesh.material().red(), mesh.material().green(), mesh.material().blue(), mesh.material().alpha());
            glBegin(GL_TRIANGLES);
            for (int index : mesh.indices()) {
                if (mesh.material().texture() != null) glTexCoord2f(mesh.texCoords()[index * 2], mesh.texCoords()[index * 2 + 1]);
                glVertex3f(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
            }
            glEnd();
        }
        glPopMatrix(); glEnable(GL_TEXTURE_2D); glColor4f(1, 1, 1, 1);
    }

    private LwjglSkinnedModel getWorldSkinnedModel(CharacterModelDefinition definition) {
        if (definition == null || failedWorldSkinnedModels.contains(definition)) return null;
        LwjglSkinnedModel cached = worldSkinnedModelCache.get(definition);
        if (cached != null) return cached;
        try {
            LwjglSkinnedModel loaded = LwjglSkinnedModel.load(definition);
            worldSkinnedModelCache.put(definition, loaded);
            return loaded;
        } catch (Exception exception) {
            failedWorldSkinnedModels.add(definition);
            LOGGER.log(Level.WARNING, "Failed to load animated world model " + definition.modelPath(), exception);
            return null;
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
        float[] positions = mesh.positions();
        float[] texCoords = mesh.texCoords();
        glBegin(GL_TRIANGLES);
        for (int index : mesh.indices()) {
            int textureOffset = index * 2;
            int positionOffset = index * 3;
            if (mesh.texture() != null && texCoords != null && textureOffset + 1 < texCoords.length) {
                glTexCoord2f(texCoords[textureOffset], texCoords[textureOffset + 1]);
            }
            glVertex3f(positions[positionOffset], positions[positionOffset + 1], positions[positionOffset + 2]);
        }
        glEnd();
    }

    private float[] sampleViewModelLight() {
        if (currentLightmapImage == null
                || currentRenderMap == null
                || currentLightingSettings == null
                || !GameConfiguration.booleanValue("lighting.enabled", true)
                || !currentLightingSettings.lightingEnabled()) {
            return new float[] {1f, 1f, 1f};
        }

        int width = Math.max(1, currentLightmapImage.getWidth());
        int height = Math.max(1, currentLightmapImage.getHeight());
        double normalizedX = currentCameraX / Math.max(1.0, currentRenderMap.getWidth());
        double normalizedZ = currentCameraZ / Math.max(1.0, currentRenderMap.getHeight());
        int sampleX = clamp((int) Math.round(normalizedX * (width - 1)), 0, width - 1);
        int sampleY = clamp((int) Math.round((1.0 - normalizedZ) * (height - 1)), 0, height - 1);
        int rgb = currentLightmapImage.getRGB(sampleX, sampleY);
        float minimum = (float) clamp(
                GameConfiguration.doubleValue("renderer.prototype.viewModel.lightMinimum", 0.22),
                0.0,
                1.0
        );
        return new float[] {
                Math.max(minimum, ((rgb >> 16) & 0xFF) / 255.0f),
                Math.max(minimum, ((rgb >> 8) & 0xFF) / 255.0f),
                Math.max(minimum, (rgb & 0xFF) / 255.0f)
        };
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private LwjglStaticModel getStaticModel(String assetPath) {
        if (assetPath == null || assetPath.isBlank() || failedStaticModels.contains(assetPath)) {
            return null;
        }
        LwjglStaticModel cached = staticModelCache.get(assetPath);
        if (cached != null) {
            return cached;
        }
        try {
            LwjglStaticModel loaded = LwjglStaticModel.load(assetPath);
            staticModelCache.put(assetPath, loaded);
            return loaded;
        } catch (Exception exception) {
            failedStaticModels.add(assetPath);
            LOGGER.log(Level.WARNING, "Failed to load static model " + assetPath, exception);
            return null;
        }
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
        lines.add("Batches " + worldBatchCount);
        lines.add("Lightmap " + lightmapWidth + "x" + lightmapHeight);
        lines.add(String.format("Bake %.1f ms", lightmapBakeMs));
        lines.add("LM chunks " + lightmapChunkHits + "/" + lightmapChunkMisses
                + " cache " + lightmapChunkEntries
                + (lightmapPending ? " pending" : ""));
        lines.add("Textures " + textureCache.textureCount());
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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

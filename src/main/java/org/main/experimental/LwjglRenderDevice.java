package org.main.experimental;

import org.joml.Matrix4f;
import org.main.engine.MapLightingSettings;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER;

final class LwjglRenderDevice {
    private static final int MAX_DYNAMIC_LIGHTS = 8;
    private static final String[] DYNAMIC_LIGHT_POSITION_UNIFORMS =
            new String[MAX_DYNAMIC_LIGHTS];
    private static final String[] DYNAMIC_LIGHT_COLOR_UNIFORMS =
            new String[MAX_DYNAMIC_LIGHTS];

    static {
        for (int index = 0; index < MAX_DYNAMIC_LIGHTS; index++) {
            DYNAMIC_LIGHT_POSITION_UNIFORMS[index] =
                    "uDynamicLightPositionRadius[" + index + "]";
            DYNAMIC_LIGHT_COLOR_UNIFORMS[index] =
                    "uDynamicLightColorIntensity[" + index + "]";
        }
    }

    private final LwjglTextureCache textureCache;
    private final ShaderProgram worldShader;
    private final ShaderProgram skinnedShader;
    private final GpuBonePalette bonePaletteA = new GpuBonePalette();
    private final GpuBonePalette bonePaletteB = new GpuBonePalette();
    private final Map<MaterialKey, GpuMesh> meshes = new HashMap<>();
    private final Map<LwjglStaticModel.Mesh, GpuMesh> staticModelMeshes = new IdentityHashMap<>();
    private final Map<LwjglSkinnedModel.SkinnedMesh, GpuMesh> skinnedModelMeshes =
            new IdentityHashMap<>();
    private final Map<LwjglSkinnedModel.SkinnedMesh, GpuSkinnedMesh> gpuSkinnedMeshes =
            new IdentityHashMap<>();
    private final Map<LwjglSkinnedModel.SkinnedMesh, float[]> skinnedVertexWorkspaces =
            new IdentityHashMap<>();
    private final Matrix4f identityMatrix = new Matrix4f();
    private int batchCount;
    private int renderedIndices;
    private long frameDrawCalls;
    private long frameDrawnIndices;
    private long frameUploadedBytes;
    private long frameSkinnedVertices;

    LwjglRenderDevice(LwjglTextureCache textureCache) {
        this.textureCache = textureCache;
        this.worldShader = new ShaderProgram(WORLD_VERTEX_SHADER, WORLD_FRAGMENT_SHADER);
        this.skinnedShader = new ShaderProgram(SKINNED_VERTEX_SHADER, WORLD_FRAGMENT_SHADER);
    }

    void prepareGpuSkinning(
            LwjglSkinnedModel.Pose from,
            LwjglSkinnedModel.Pose to,
            double blend
    ) {
        LwjglSkinnedModel.Pose safeTo = to == null ? from : to;
        LwjglSkinnedModel.Pose safeFrom = from == null ? safeTo : from;
        if (safeFrom == null || safeTo == null) {
            return;
        }
        boolean sharedPose = safeFrom == safeTo;
        bonePaletteA.upload(safeFrom.boneMatrices());
        if (!sharedPose) {
            bonePaletteB.upload(safeTo.boneMatrices());
        }
        frameUploadedBytes += (long) (safeFrom.boneMatrices().length
                + (sharedPose ? 0 : safeTo.boneMatrices().length)) * 16L * Float.BYTES;
        skinnedShader.bind();
        glActiveTexture(GL_TEXTURE0 + 2);
        glBindTexture(GL_TEXTURE_BUFFER, bonePaletteA.textureId());
        glActiveTexture(GL_TEXTURE0 + 3);
        glBindTexture(GL_TEXTURE_BUFFER,
                sharedPose ? bonePaletteA.textureId() : bonePaletteB.textureId());
        glActiveTexture(GL_TEXTURE0);
        skinnedShader.setUniform("uBonePaletteA", 2);
        skinnedShader.setUniform("uBonePaletteB", 3);
        skinnedShader.setUniform("uPoseBlend", (float) Math.max(0.0, Math.min(1.0, blend)));
    }

    void renderGpuSkinnedMeshes(
            List<LwjglSkinnedModel.SkinnedMesh> sourceMeshes,
            LwjglSkinnedModel.Pose from,
            LwjglSkinnedModel.Pose to,
            Matrix4f projectionView,
            Matrix4f model,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            double modelBrightness,
            List<RuntimeLight> dynamicLights
    ) {
        if (sourceMeshes == null || sourceMeshes.isEmpty() || from == null || to == null) {
            return;
        }
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        skinnedShader.bind();
        skinnedShader.setUniformMatrix("uProjectionView", projectionView);
        skinnedShader.setUniformMatrix("uModel", model == null ? identityMatrix.identity() : model);
        skinnedShader.setUniform("uDiffuse", 0);
        skinnedShader.setUniform("uLightmap", 1);
        skinnedShader.setUniform("uModelBrightness",
                (float) Math.max(0.0, Math.min(4.0, modelBrightness)));
        skinnedShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        skinnedShader.setUniform3("uCameraPosition", (float) cameraX, (float) cameraY, (float) cameraZ);
        skinnedShader.setUniform("uFogEnabled",
                lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        configureDynamicLights(skinnedShader, dynamicLights);
        skinnedShader.setUniform3(
                "uFogColor",
                lightingSettings == null ? 0.08f : lightingSettings.fogRed(),
                lightingSettings == null ? 0.04f : lightingSettings.fogGreen(),
                lightingSettings == null ? 0.07f : lightingSettings.fogBlue());
        skinnedShader.setUniform("uFogDensity",
                lightingSettings == null ? 0.0f : (float) lightingSettings.fogDensity());
        if (lightmap != null) {
            lightmap.bind(1);
        }
        for (LwjglSkinnedModel.SkinnedMesh sourceMesh : sourceMeshes) {
            if (sourceMesh == null || sourceMesh.indices().length == 0) {
                continue;
            }
            skinnedShader.setUniformMatrix("uFallbackTransformA",
                    from.nodeMatrix(sourceMesh.nodeIndex(), sourceMesh.nodeTransform()));
            skinnedShader.setUniformMatrix("uFallbackTransformB",
                    to.nodeMatrix(sourceMesh.nodeIndex(), sourceMesh.nodeTransform()));
            LwjglSkinnedModel.Material material = sourceMesh.material();
            skinnedShader.setUniform4("uMaterialColor",
                    material.red(), material.green(), material.blue(), material.alpha());
            if (material.texture() == null) {
                textureCache.bindWhite(0);
            } else {
                textureCache.bind(material.texture(), 0);
            }
            GpuSkinnedMesh mesh = gpuSkinnedMeshes.computeIfAbsent(sourceMesh, GpuSkinnedMesh::new);
            mesh.draw();
            frameDrawCalls++;
            frameDrawnIndices += mesh.indexCount();
            frameSkinnedVertices += sourceMesh.vertexCount();
        }
        skinnedShader.unbind();
    }

    void renderWorld(
            List<RenderBatch> batches,
            Matrix4f projectionView,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            List<RuntimeLight> dynamicLights
    ) {
        if (batches == null || batches.isEmpty()) {
            batchCount = 0;
            renderedIndices = 0;
            return;
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        worldShader.bind();
        worldShader.setUniformMatrix("uProjectionView", projectionView);
        worldShader.setUniformMatrix("uModel", identityMatrix.identity());
        worldShader.setUniform("uDiffuse", 0);
        worldShader.setUniform("uLightmap", 1);
        worldShader.setUniform("uModelBrightness", 1.0f);
        worldShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        worldShader.setUniform3("uCameraPosition", (float) cameraX, (float) cameraY, (float) cameraZ);
        worldShader.setUniform("uFogEnabled", lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        configureDynamicLights(dynamicLights);
        worldShader.setUniform3(
                "uFogColor",
                lightingSettings == null ? 0.08f : lightingSettings.fogRed(),
                lightingSettings == null ? 0.04f : lightingSettings.fogGreen(),
                lightingSettings == null ? 0.07f : lightingSettings.fogBlue());
        worldShader.setUniform("uFogDensity", lightingSettings == null ? 0.0f : (float) lightingSettings.fogDensity());
        if (lightmap != null) {
            lightmap.bind(1);
        }

        batchCount = 0;
        renderedIndices = 0;
        for (RenderBatch batch : batches) {
            if (batch == null || batch.indexCount() <= 0) {
                continue;
            }
            textureCache.bind(batch.material().currentTexture(), 0);
            GpuMesh mesh = meshes.computeIfAbsent(batch.material(), ignored -> new GpuMesh());
            mesh.update(batch.vertices(), batch.indices(), batch.indexCount());
            frameUploadedBytes += mesh.consumeUploadedBytes();
            mesh.draw();
            frameDrawCalls++;
            frameDrawnIndices += batch.indexCount();
            batchCount++;
            renderedIndices += batch.indexCount();
        }

        worldShader.unbind();
        glDisable(GL_TEXTURE_2D);
    }

    PreparedWorld prepareWorld(List<RenderBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return new PreparedWorld(List.of());
        }
        List<PreparedBatch> prepared = new java.util.ArrayList<>(batches.size());
        for (RenderBatch batch : batches) {
            if (batch == null || batch.indexCount() <= 0) {
                continue;
            }
            GpuMesh mesh = new GpuMesh(true);
            mesh.update(batch.vertices(), batch.indices(), batch.indexCount());
            frameUploadedBytes += mesh.consumeUploadedBytes();
            prepared.add(new PreparedBatch(batch.material(), mesh, batch.indexCount()));
        }
        return new PreparedWorld(List.copyOf(prepared));
    }

    void renderPreparedWorld(
            PreparedWorld prepared,
            Matrix4f projectionView,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            List<RuntimeLight> dynamicLights
    ) {
        if (prepared == null || prepared.batches().isEmpty()) {
            batchCount = 0;
            renderedIndices = 0;
            return;
        }
        beginWorldPass(projectionView, lightmap, mapWidth, mapHeight,
                cameraX, cameraY, cameraZ, lightingSettings, dynamicLights, identityMatrix.identity(), 1.0f);
        batchCount = 0;
        renderedIndices = 0;
        for (PreparedBatch batch : prepared.batches()) {
            textureCache.bind(batch.material().currentTexture(), 0);
            batch.mesh().draw();
            frameDrawCalls++;
            frameDrawnIndices += batch.indexCount();
            batchCount++;
            renderedIndices += batch.indexCount();
        }
        worldShader.unbind();
        glDisable(GL_TEXTURE_2D);
    }

    void renderPreparedWorlds(
            Iterable<PreparedWorld> preparedWorlds,
            Matrix4f projectionView,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            List<RuntimeLight> dynamicLights
    ) {
        if (preparedWorlds == null) {
            batchCount = 0;
            renderedIndices = 0;
            return;
        }
        beginWorldPass(projectionView, lightmap, mapWidth, mapHeight,
                cameraX, cameraY, cameraZ, lightingSettings, dynamicLights, identityMatrix.identity(), 1.0f);
        batchCount = 0;
        renderedIndices = 0;
        for (PreparedWorld prepared : preparedWorlds) {
            if (prepared == null) {
                continue;
            }
            for (PreparedBatch batch : prepared.batches()) {
                textureCache.bind(batch.material().currentTexture(), 0);
                batch.mesh().draw();
                frameDrawCalls++;
                frameDrawnIndices += batch.indexCount();
                batchCount++;
                renderedIndices += batch.indexCount();
            }
        }
        worldShader.unbind();
        glDisable(GL_TEXTURE_2D);
    }

    void releasePreparedWorld(PreparedWorld prepared) {
        if (prepared == null) {
            return;
        }
        for (PreparedBatch batch : prepared.batches()) {
            batch.mesh().shutdown();
        }
    }

    private void beginWorldPass(
            Matrix4f projectionView,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            List<RuntimeLight> dynamicLights,
            Matrix4f model,
            float modelBrightness
    ) {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        worldShader.bind();
        worldShader.setUniformMatrix("uProjectionView", projectionView);
        worldShader.setUniformMatrix("uModel", model);
        worldShader.setUniform("uDiffuse", 0);
        worldShader.setUniform("uLightmap", 1);
        worldShader.setUniform("uModelBrightness", modelBrightness);
        worldShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        worldShader.setUniform3("uCameraPosition", (float) cameraX, (float) cameraY, (float) cameraZ);
        worldShader.setUniform("uFogEnabled", lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        configureDynamicLights(dynamicLights);
        worldShader.setUniform3(
                "uFogColor",
                lightingSettings == null ? 0.08f : lightingSettings.fogRed(),
                lightingSettings == null ? 0.04f : lightingSettings.fogGreen(),
                lightingSettings == null ? 0.07f : lightingSettings.fogBlue());
        worldShader.setUniform("uFogDensity", lightingSettings == null ? 0.0f : (float) lightingSettings.fogDensity());
        if (lightmap != null) {
            lightmap.bind(1);
        }
    }

    void renderStaticMeshes(
            List<LwjglStaticModel.Mesh> sourceMeshes,
            Matrix4f projectionView,
            Matrix4f model,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            double modelBrightness,
            List<RuntimeLight> dynamicLights
    ) {
        if (sourceMeshes == null || sourceMeshes.isEmpty()) {
            return;
        }
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        beginWorldPass(projectionView, lightmap, mapWidth, mapHeight,
                cameraX, cameraY, cameraZ, lightingSettings, dynamicLights,
                model == null ? identityMatrix.identity() : model, (float) modelBrightness);
        for (LwjglStaticModel.Mesh sourceMesh : sourceMeshes) {
            if (sourceMesh == null || sourceMesh.indices().length == 0) {
                continue;
            }
            if (sourceMesh.texture() == null) {
                textureCache.bindWhite(0);
            } else {
                textureCache.bind(sourceMesh.texture(), 0);
            }
            GpuMesh mesh = staticModelMeshes.computeIfAbsent(sourceMesh, this::createStaticModelMesh);
            frameUploadedBytes += mesh.consumeUploadedBytes();
            mesh.draw();
            frameDrawCalls++;
            frameDrawnIndices += sourceMesh.indices().length;
        }
        worldShader.unbind();
    }

    void renderSkinnedMeshes(
            List<LwjglSkinnedModel.SkinnedMesh> sourceMeshes,
            List<float[]> meshPositions,
            Matrix4f projectionView,
            Matrix4f model,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings,
            double modelBrightness,
            List<RuntimeLight> dynamicLights
    ) {
        if (sourceMeshes == null || meshPositions == null || sourceMeshes.isEmpty()) {
            return;
        }
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        worldShader.bind();
        worldShader.setUniformMatrix("uProjectionView", projectionView);
        worldShader.setUniformMatrix("uModel", model == null ? identityMatrix.identity() : model);
        worldShader.setUniform("uDiffuse", 0);
        worldShader.setUniform("uLightmap", 1);
        worldShader.setUniform("uModelBrightness",
                (float) Math.max(0.0, Math.min(4.0, modelBrightness)));
        worldShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        worldShader.setUniform3("uCameraPosition",
                (float) cameraX, (float) cameraY, (float) cameraZ);
        worldShader.setUniform("uFogEnabled",
                lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        configureDynamicLights(dynamicLights);
        worldShader.setUniform3(
                "uFogColor",
                lightingSettings == null ? 0.08f : lightingSettings.fogRed(),
                lightingSettings == null ? 0.04f : lightingSettings.fogGreen(),
                lightingSettings == null ? 0.07f : lightingSettings.fogBlue());
        worldShader.setUniform("uFogDensity",
                lightingSettings == null ? 0.0f : (float) lightingSettings.fogDensity());
        if (lightmap != null) {
            lightmap.bind(1);
        }
        int count = Math.min(sourceMeshes.size(), meshPositions.size());
        for (int meshIndex = 0; meshIndex < count; meshIndex++) {
            LwjglSkinnedModel.SkinnedMesh sourceMesh = sourceMeshes.get(meshIndex);
            float[] positions = meshPositions.get(meshIndex);
            if (sourceMesh == null || positions == null
                    || positions.length != sourceMesh.bindPositions().length
                    || sourceMesh.indices().length == 0) {
                continue;
            }
            LwjglSkinnedModel.Material material = sourceMesh.material();
            if (material.texture() == null) {
                textureCache.bindWhite(0);
            } else {
                textureCache.bind(material.texture(), 0);
            }

            int vertexCount = positions.length / 3;
            float[] vertices = skinnedVertexWorkspaces.computeIfAbsent(
                    sourceMesh,
                    ignored -> new float[vertexCount * GpuMesh.FLOATS_PER_VERTEX]);
            float[] textureCoordinates = sourceMesh.texCoords();
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                int positionOffset = vertex * 3;
                int uvOffset = vertex * 2;
                int target = vertex * GpuMesh.FLOATS_PER_VERTEX;
                vertices[target] = positions[positionOffset];
                vertices[target + 1] = positions[positionOffset + 1];
                vertices[target + 2] = positions[positionOffset + 2];
                vertices[target + 3] = uvOffset + 1 < textureCoordinates.length
                        ? textureCoordinates[uvOffset] : 0.0f;
                vertices[target + 4] = uvOffset + 1 < textureCoordinates.length
                        ? textureCoordinates[uvOffset + 1] : 0.0f;
                vertices[target + 5] = 0.0f;
                vertices[target + 6] = 1.0f;
                vertices[target + 7] = 0.0f;
                vertices[target + 8] = material.red();
                vertices[target + 9] = material.green();
                vertices[target + 10] = material.blue();
                vertices[target + 11] = material.alpha();
                vertices[target + 12] = 0.0f;
            }
            GpuMesh mesh = skinnedModelMeshes.computeIfAbsent(
                    sourceMesh,
                    ignored -> new GpuMesh());
            mesh.update(vertices, sourceMesh.indices(), sourceMesh.indices().length);
            frameUploadedBytes += mesh.consumeUploadedBytes();
            frameSkinnedVertices += vertexCount;
            mesh.draw();
            frameDrawCalls++;
            frameDrawnIndices += sourceMesh.indices().length;
        }
        worldShader.unbind();
    }

    private void configureDynamicLights(List<RuntimeLight> dynamicLights) {
        configureDynamicLights(worldShader, dynamicLights);
    }

    private void configureDynamicLights(ShaderProgram shader, List<RuntimeLight> dynamicLights) {
        int count = dynamicLights == null ? 0 : Math.min(MAX_DYNAMIC_LIGHTS, dynamicLights.size());
        shader.setUniform("uDynamicLightCount", count);
        for (int i = 0; i < count; i++) {
            RuntimeLight light = dynamicLights.get(i);
            shader.setUniform4(
                    DYNAMIC_LIGHT_POSITION_UNIFORMS[i],
                    (float) light.x(),
                    (float) light.y(),
                    (float) light.z(),
                    (float) light.radius());
            shader.setUniform4(
                    DYNAMIC_LIGHT_COLOR_UNIFORMS[i],
                    colorRed(light.colorRgb()),
                    colorGreen(light.colorRgb()),
                    colorBlue(light.colorRgb()),
                    (float) light.intensity());
        }
    }

    private static float colorRed(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    private static float colorGreen(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    private static float colorBlue(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }

    int batchCount() {
        return batchCount;
    }

    int renderedIndices() {
        return renderedIndices;
    }

    void beginFrameStats() {
        frameDrawCalls = 0L;
        frameDrawnIndices = 0L;
        frameUploadedBytes = 0L;
        frameSkinnedVertices = 0L;
    }

    long frameDrawCalls() {
        return frameDrawCalls;
    }

    long frameDrawnIndices() {
        return frameDrawnIndices;
    }

    long frameUploadedBytes() {
        return frameUploadedBytes;
    }

    long frameSkinnedVertices() {
        return frameSkinnedVertices;
    }

    void shutdown() {
        for (GpuMesh mesh : meshes.values()) {
            mesh.shutdown();
        }
        meshes.clear();
        for (GpuMesh mesh : staticModelMeshes.values()) {
            mesh.shutdown();
        }
        staticModelMeshes.clear();
        clearSkinnedModelMeshes();
        for (GpuSkinnedMesh mesh : gpuSkinnedMeshes.values()) {
            mesh.shutdown();
        }
        gpuSkinnedMeshes.clear();
        bonePaletteA.shutdown();
        bonePaletteB.shutdown();
        worldShader.shutdown();
        skinnedShader.shutdown();
    }

    record PreparedWorld(List<PreparedBatch> batches) {
    }

    private record PreparedBatch(MaterialKey material, GpuMesh mesh, int indexCount) {
    }

    void clearSkinnedModelMeshes() {
        for (GpuMesh mesh : skinnedModelMeshes.values()) {
            mesh.shutdown();
        }
        skinnedModelMeshes.clear();
        skinnedVertexWorkspaces.clear();
        for (GpuSkinnedMesh mesh : gpuSkinnedMeshes.values()) {
            mesh.shutdown();
        }
        gpuSkinnedMeshes.clear();
    }

    private GpuMesh createStaticModelMesh(LwjglStaticModel.Mesh sourceMesh) {
        int vertexCount = sourceMesh.positions().length / 3;
        float[] vertices = new float[vertexCount * GpuMesh.FLOATS_PER_VERTEX];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int positionOffset = vertex * 3;
            int uvOffset = vertex * 2;
            int target = vertex * GpuMesh.FLOATS_PER_VERTEX;
            vertices[target] = sourceMesh.positions()[positionOffset];
            vertices[target + 1] = sourceMesh.positions()[positionOffset + 1];
            vertices[target + 2] = sourceMesh.positions()[positionOffset + 2];
            vertices[target + 3] = uvOffset + 1 < sourceMesh.texCoords().length ? sourceMesh.texCoords()[uvOffset] : 0.0f;
            vertices[target + 4] = uvOffset + 1 < sourceMesh.texCoords().length ? sourceMesh.texCoords()[uvOffset + 1] : 0.0f;
            vertices[target + 5] = 0.0f;
            vertices[target + 6] = 1.0f;
            vertices[target + 7] = 0.0f;
            vertices[target + 8] = sourceMesh.red();
            vertices[target + 9] = sourceMesh.green();
            vertices[target + 10] = sourceMesh.blue();
            vertices[target + 11] = sourceMesh.alpha();
            vertices[target + 12] = 0.0f;
        }
        GpuMesh mesh = new GpuMesh(true);
        mesh.update(vertices, sourceMesh.indices(), sourceMesh.indices().length);
        return mesh;
    }

    private static final String WORLD_VERTEX_SHADER = """
            #version 410 core
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec3 aNormal;
            layout(location = 3) in vec4 aColor;
            layout(location = 4) in float aFlags;

            uniform mat4 uProjectionView;
            uniform mat4 uModel;
            uniform vec2 uMapSize;

            out vec2 vUv;
            out vec2 vLightUv;
            out vec3 vWorldPosition;
            out vec4 vColor;
            out float vFlags;

            void main() {
                vec4 worldPosition = uModel * vec4(aPosition, 1.0);
                vUv = aUv;
                vLightUv = clamp(vec2(worldPosition.x / uMapSize.x, worldPosition.z / uMapSize.y), 0.0, 1.0);
                vWorldPosition = worldPosition.xyz;
                vColor = aColor;
                vFlags = aFlags;
                gl_Position = uProjectionView * worldPosition;
            }
            """;

    private static final String SKINNED_VERTEX_SHADER = """
            #version 410 core
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in uvec4 aBoneIndices;
            layout(location = 3) in vec4 aBoneWeights;

            uniform mat4 uProjectionView;
            uniform mat4 uModel;
            uniform vec2 uMapSize;
            uniform samplerBuffer uBonePaletteA;
            uniform samplerBuffer uBonePaletteB;
            uniform float uPoseBlend;
            uniform mat4 uFallbackTransformA;
            uniform mat4 uFallbackTransformB;
            uniform vec4 uMaterialColor;

            out vec2 vUv;
            out vec2 vLightUv;
            out vec3 vWorldPosition;
            out vec4 vColor;
            out float vFlags;

            mat4 paletteMatrix(samplerBuffer palette, uint boneIndex) {
                int start = int(boneIndex) * 4;
                return mat4(
                    texelFetch(palette, start),
                    texelFetch(palette, start + 1),
                    texelFetch(palette, start + 2),
                    texelFetch(palette, start + 3));
            }

            vec4 skinnedPosition(samplerBuffer palette, mat4 fallbackTransform) {
                float totalWeight = dot(aBoneWeights, vec4(1.0));
                vec4 source = vec4(aPosition, 1.0);
                if (totalWeight <= 0.00001) {
                    return fallbackTransform * source;
                }
                vec4 result = vec4(0.0);
                result += paletteMatrix(palette, aBoneIndices.x) * source * aBoneWeights.x;
                result += paletteMatrix(palette, aBoneIndices.y) * source * aBoneWeights.y;
                result += paletteMatrix(palette, aBoneIndices.z) * source * aBoneWeights.z;
                result += paletteMatrix(palette, aBoneIndices.w) * source * aBoneWeights.w;
                return result;
            }

            void main() {
                vec4 poseA = skinnedPosition(uBonePaletteA, uFallbackTransformA);
                vec4 poseB = skinnedPosition(uBonePaletteB, uFallbackTransformB);
                float blend = smoothstep(0.0, 1.0, clamp(uPoseBlend, 0.0, 1.0));
                vec4 worldPosition = uModel * mix(poseA, poseB, blend);
                vUv = aUv;
                vLightUv = clamp(vec2(worldPosition.x / uMapSize.x, worldPosition.z / uMapSize.y), 0.0, 1.0);
                vWorldPosition = worldPosition.xyz;
                vColor = uMaterialColor;
                vFlags = 0.0;
                gl_Position = uProjectionView * worldPosition;
            }
            """;

    private static final String WORLD_FRAGMENT_SHADER = """
            #version 410 core
            in vec2 vUv;
            in vec2 vLightUv;
            in vec3 vWorldPosition;
            in vec4 vColor;
            in float vFlags;

            uniform sampler2D uDiffuse;
            uniform sampler2D uLightmap;
            uniform float uModelBrightness;
            uniform vec3 uCameraPosition;
            uniform int uFogEnabled;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform int uDynamicLightCount;
            uniform vec4 uDynamicLightPositionRadius[8];
            uniform vec4 uDynamicLightColorIntensity[8];

            out vec4 fragColor;

            void main() {
                vec4 diffuse = texture(uDiffuse, vUv) * vColor * vec4(vec3(uModelBrightness), 1.0);
                if (diffuse.a <= 0.10) {
                    discard;
                }
                vec3 light = texture(uLightmap, vLightUv).rgb;
                for (int i = 0; i < uDynamicLightCount; i++) {
                    vec3 delta = uDynamicLightPositionRadius[i].xyz - vWorldPosition;
                    float radius = max(uDynamicLightPositionRadius[i].w, 0.001);
                    float distanceToLight = length(delta);
                    if (distanceToLight <= radius) {
                        float falloff = 1.0 - distanceToLight / radius;
                        light += uDynamicLightColorIntensity[i].rgb
                                * uDynamicLightColorIntensity[i].a
                                * falloff
                                * falloff;
                    }
                }
                vec3 color = diffuse.rgb * max(light, vec3(0.02));
                if (uFogEnabled == 1 && uFogDensity > 0.0) {
                    float distanceToCamera = length(vWorldPosition - uCameraPosition);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * distanceToCamera * distanceToCamera);
                    color = mix(color, uFogColor, clamp(fog, 0.0, 1.0));
                }
                fragColor = vec4(color, diffuse.a);
            }
            """;
}

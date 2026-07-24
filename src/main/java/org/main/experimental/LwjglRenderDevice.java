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

final class LwjglRenderDevice {
    private final LwjglTextureCache textureCache;
    private final ShaderProgram worldShader;
    private final Map<MaterialKey, GpuMesh> meshes = new HashMap<>();
    private final Map<LwjglStaticModel.Mesh, GpuMesh> staticModelMeshes = new IdentityHashMap<>();
    private int batchCount;
    private int renderedIndices;

    LwjglRenderDevice(LwjglTextureCache textureCache) {
        this.textureCache = textureCache;
        this.worldShader = new ShaderProgram(WORLD_VERTEX_SHADER, WORLD_FRAGMENT_SHADER);
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
            MapLightingSettings lightingSettings
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
        worldShader.setUniformMatrix("uModel", new Matrix4f());
        worldShader.setUniform("uDiffuse", 0);
        worldShader.setUniform("uLightmap", 1);
        worldShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        worldShader.setUniform3("uCameraPosition", (float) cameraX, (float) cameraY, (float) cameraZ);
        worldShader.setUniform("uFogEnabled", lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        worldShader.setUniform("uDynamicLightCount", 0);
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
            textureCache.bind(batch.material().texture(), 0);
            GpuMesh mesh = meshes.computeIfAbsent(batch.material(), ignored -> new GpuMesh());
            mesh.update(batch.vertices(), batch.indices(), batch.indexCount());
            mesh.draw();
            batchCount++;
            renderedIndices += batch.indexCount();
        }

        worldShader.unbind();
        glDisable(GL_TEXTURE_2D);
    }

    void renderStaticMesh(
            LwjglStaticModel.Mesh sourceMesh,
            Matrix4f projectionView,
            Matrix4f model,
            GpuTexture lightmap,
            int mapWidth,
            int mapHeight,
            double cameraX,
            double cameraY,
            double cameraZ,
            MapLightingSettings lightingSettings
    ) {
        if (sourceMesh == null || sourceMesh.indices().length == 0) {
            return;
        }
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        worldShader.bind();
        worldShader.setUniformMatrix("uProjectionView", projectionView);
        worldShader.setUniformMatrix("uModel", model == null ? new Matrix4f() : model);
        worldShader.setUniform("uDiffuse", 0);
        worldShader.setUniform("uLightmap", 1);
        worldShader.setUniform2("uMapSize", Math.max(1f, mapWidth), Math.max(1f, mapHeight));
        worldShader.setUniform3("uCameraPosition", (float) cameraX, (float) cameraY, (float) cameraZ);
        worldShader.setUniform("uFogEnabled", lightingSettings != null && lightingSettings.fogEnabled() ? 1 : 0);
        worldShader.setUniform("uDynamicLightCount", 0);
        worldShader.setUniform3(
                "uFogColor",
                lightingSettings == null ? 0.08f : lightingSettings.fogRed(),
                lightingSettings == null ? 0.04f : lightingSettings.fogGreen(),
                lightingSettings == null ? 0.07f : lightingSettings.fogBlue());
        worldShader.setUniform("uFogDensity", lightingSettings == null ? 0.0f : (float) lightingSettings.fogDensity());
        if (lightmap != null) {
            lightmap.bind(1);
        }
        textureCache.bind(sourceMesh.texture(), 0);
        GpuMesh mesh = staticModelMeshes.computeIfAbsent(sourceMesh, this::createStaticModelMesh);
        mesh.draw();
        worldShader.unbind();
    }

    int batchCount() {
        return batchCount;
    }

    int renderedIndices() {
        return renderedIndices;
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
        worldShader.shutdown();
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
        GpuMesh mesh = new GpuMesh();
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

    private static final String WORLD_FRAGMENT_SHADER = """
            #version 410 core
            in vec2 vUv;
            in vec2 vLightUv;
            in vec3 vWorldPosition;
            in vec4 vColor;
            in float vFlags;

            uniform sampler2D uDiffuse;
            uniform sampler2D uLightmap;
            uniform vec3 uCameraPosition;
            uniform int uFogEnabled;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform int uDynamicLightCount;
            uniform vec4 uDynamicLightPositionRadius[8];
            uniform vec4 uDynamicLightColorIntensity[8];

            out vec4 fragColor;

            void main() {
                vec4 diffuse = texture(uDiffuse, vUv) * vColor;
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

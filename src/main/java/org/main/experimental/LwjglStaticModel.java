package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.main.engine.AssetLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.aiImportFileFromMemory;
import static org.lwjgl.assimp.Assimp.aiProcess_FlipUVs;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_PreTransformVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_SortByPType;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

final class LwjglStaticModel {
    private final List<Mesh> meshes;
    private final BufferedImage texture;
    private final float minX;
    private final float minY;
    private final float minZ;
    private final float maxX;
    private final float maxY;
    private final float maxZ;

    private LwjglStaticModel(
            List<Mesh> meshes,
            BufferedImage texture,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ
    ) {
        this.meshes = List.copyOf(meshes);
        this.texture = texture;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    static LwjglStaticModel load(String assetPath) throws IOException {
        byte[] assetBytes;
        try (InputStream stream = AssetLoader.openAssetStream(assetPath)) {
            assetBytes = stream.readAllBytes();
        }

        ByteBuffer source = BufferUtils.createByteBuffer(assetBytes.length);
        source.put(assetBytes).flip();
        AIScene scene = aiImportFileFromMemory(
                source,
                aiProcess_Triangulate
                        | aiProcess_JoinIdenticalVertices
                        | aiProcess_PreTransformVertices
                        | aiProcess_SortByPType
                        | aiProcess_FlipUVs,
                "glb"
        );
        if (scene == null || scene.mRootNode() == null || scene.mNumMeshes() == 0) {
            throw new IOException("Assimp could not import model: " + assetPath);
        }

        try {
            List<Mesh> meshes = new ArrayList<>();
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            PointerBuffer sceneMeshes = scene.mMeshes();

            for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
                AIMesh sourceMesh = AIMesh.create(sceneMeshes.get(meshIndex));
                AIVector3D.Buffer sourceVertices = sourceMesh.mVertices();
                AIVector3D.Buffer sourceTexCoords = sourceMesh.mTextureCoords(0);
                float[] positions = new float[sourceMesh.mNumVertices() * 3];
                float[] texCoords = new float[sourceMesh.mNumVertices() * 2];

                for (int vertexIndex = 0; vertexIndex < sourceMesh.mNumVertices(); vertexIndex++) {
                    AIVector3D vertex = sourceVertices.get(vertexIndex);
                    int positionOffset = vertexIndex * 3;
                    positions[positionOffset] = vertex.x();
                    positions[positionOffset + 1] = vertex.y();
                    positions[positionOffset + 2] = vertex.z();
                    minX = Math.min(minX, vertex.x());
                    minY = Math.min(minY, vertex.y());
                    minZ = Math.min(minZ, vertex.z());
                    maxX = Math.max(maxX, vertex.x());
                    maxY = Math.max(maxY, vertex.y());
                    maxZ = Math.max(maxZ, vertex.z());

                    if (sourceTexCoords != null) {
                        AIVector3D texCoord = sourceTexCoords.get(vertexIndex);
                        int textureOffset = vertexIndex * 2;
                        texCoords[textureOffset] = texCoord.x();
                        texCoords[textureOffset + 1] = texCoord.y();
                    }
                }

                List<Integer> indices = new ArrayList<>(sourceMesh.mNumFaces() * 3);
                AIFace.Buffer faces = sourceMesh.mFaces();
                for (int faceIndex = 0; faceIndex < sourceMesh.mNumFaces(); faceIndex++) {
                    IntBuffer faceIndices = faces.get(faceIndex).mIndices();
                    if (faceIndices.remaining() != 3) {
                        continue;
                    }
                    indices.add(faceIndices.get(0));
                    indices.add(faceIndices.get(1));
                    indices.add(faceIndices.get(2));
                }
                int[] triangleIndices = indices.stream().mapToInt(Integer::intValue).toArray();
                meshes.add(new Mesh(positions, texCoords, triangleIndices));
            }

            if (meshes.isEmpty() || !Float.isFinite(minY) || maxY <= minY) {
                throw new IOException("Model contains no renderable triangles: " + assetPath);
            }
            return new LwjglStaticModel(
                    meshes,
                    loadFirstEmbeddedTexture(scene),
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ
            );
        } finally {
            aiReleaseImport(scene);
        }
    }

    private static BufferedImage loadFirstEmbeddedTexture(AIScene scene) throws IOException {
        if (scene.mNumTextures() == 0 || scene.mTextures() == null) {
            return null;
        }
        AITexture sourceTexture = AITexture.create(scene.mTextures().get(0));
        if (sourceTexture.mHeight() != 0) {
            return null;
        }

        ByteBuffer compressed = sourceTexture.pcDataCompressed();
        if (compressed == null || sourceTexture.mWidth() <= 0) {
            return null;
        }
        byte[] imageBytes = new byte[sourceTexture.mWidth()];
        compressed.get(0, imageBytes);
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }

    List<Mesh> meshes() {
        return meshes;
    }

    BufferedImage texture() {
        return texture;
    }

    double normalizedScaleForHeight(double targetHeight) {
        return targetHeight / Math.max(0.0001, maxY - minY);
    }

    double centerX() {
        return (minX + maxX) * 0.5;
    }

    double baseY() {
        return minY;
    }

    double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    record Mesh(float[] positions, float[] texCoords, int[] indices) {
    }
}

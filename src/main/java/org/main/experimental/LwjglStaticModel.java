package org.main.experimental;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AIVector3D;
import org.main.engine.AssetLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.assimp.Assimp.aiImportFileFromMemory;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_BASE_COLOR;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiGetMaterialColor;
import static org.lwjgl.assimp.Assimp.aiGetMaterialTexture;
import static org.lwjgl.assimp.Assimp.aiReturn_SUCCESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_BASE_COLOR;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_PreTransformVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_SortByPType;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

public final class LwjglStaticModel {
    private final List<Mesh> meshes;
    private final float minX;
    private final float minY;
    private final float minZ;
    private final float maxX;
    private final float maxY;
    private final float maxZ;

    private LwjglStaticModel(
            List<Mesh> meshes,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ
    ) {
        this.meshes = List.copyOf(meshes);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static LwjglStaticModel load(String assetPath) throws IOException {
        String formatHint = formatHint(assetPath);
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
                        | aiProcess_SortByPType,
                formatHint
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
            List<BufferedImage> embeddedTextures = loadEmbeddedTextures(scene);

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
                MaterialAppearance appearance = resolveMaterialAppearance(
                        scene, sourceMesh, embeddedTextures, assetPath);
                meshes.add(new Mesh(
                        positions,
                        texCoords,
                        triangleIndices,
                        appearance.texture(),
                        appearance.red(),
                        appearance.green(),
                        appearance.blue(),
                        appearance.alpha()
                ));
            }

            if (meshes.isEmpty() || !Float.isFinite(minY) || maxY <= minY) {
                throw new IOException("Model contains no renderable triangles: " + assetPath);
            }
            return new LwjglStaticModel(
                    meshes,
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

    private static List<BufferedImage> loadEmbeddedTextures(AIScene scene) throws IOException {
        List<BufferedImage> textures = new ArrayList<>();
        if (scene.mNumTextures() == 0 || scene.mTextures() == null) {
            return textures;
        }
        for (int index = 0; index < scene.mNumTextures(); index++) {
            AITexture sourceTexture = AITexture.create(scene.mTextures().get(index));
            BufferedImage image = null;
            if (sourceTexture.mHeight() == 0 && sourceTexture.mWidth() > 0) {
                ByteBuffer compressed = sourceTexture.pcDataCompressed();
                if (compressed != null) {
                    byte[] imageBytes = new byte[sourceTexture.mWidth()];
                    compressed.get(0, imageBytes);
                    image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                }
            } else if (sourceTexture.mWidth() > 0 && sourceTexture.mHeight() > 0) {
                int width = sourceTexture.mWidth();
                int height = sourceTexture.mHeight();
                AITexel.Buffer pixels = sourceTexture.pcData();
                if (pixels != null) {
                    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            AITexel pixel = pixels.get(y * width + x);
                            int argb = Byte.toUnsignedInt(pixel.a()) << 24
                                    | Byte.toUnsignedInt(pixel.r()) << 16
                                    | Byte.toUnsignedInt(pixel.g()) << 8
                                    | Byte.toUnsignedInt(pixel.b());
                            image.setRGB(x, y, argb);
                        }
                    }
                }
            }
            textures.add(image);
        }
        return textures;
    }

    private static MaterialAppearance resolveMaterialAppearance(
            AIScene scene,
            AIMesh mesh,
            List<BufferedImage> embeddedTextures,
            String assetPath
    ) {
        AIMaterial material = null;
        if (scene.mMaterials() != null
                && mesh.mMaterialIndex() >= 0
                && mesh.mMaterialIndex() < scene.mNumMaterials()) {
            material = AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()));
        }
        float[] color = resolveBaseColor(material);
        BufferedImage texture = resolveMaterialTexture(
                material, aiTextureType_BASE_COLOR, embeddedTextures, assetPath);
        if (texture == null) {
            texture = resolveMaterialTexture(
                    material, aiTextureType_DIFFUSE, embeddedTextures, assetPath);
        }
        if (texture == null && embeddedTextures.size() == 1) {
            texture = embeddedTextures.get(0);
        }
        return new MaterialAppearance(texture, color[0], color[1], color[2], color[3]);
    }

    private static float[] resolveBaseColor(AIMaterial material) {
        if (material == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        try (AIColor4D color = AIColor4D.calloc()) {
            int result = aiGetMaterialColor(material, AI_MATKEY_BASE_COLOR, 0, 0, color);
            if (result != aiReturn_SUCCESS) {
                result = aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, 0, 0, color);
            }
            if (result == aiReturn_SUCCESS) {
                return new float[]{color.r(), color.g(), color.b(), color.a()};
            }
        }
        return new float[]{1f, 1f, 1f, 1f};
    }

    private static BufferedImage resolveMaterialTexture(
            AIMaterial material,
            int textureType,
            List<BufferedImage> embeddedTextures,
            String assetPath
    ) {
        if (material == null || embeddedTextures.isEmpty()) {
            return null;
        }
        try (AIString path = AIString.calloc()) {
            if (aiGetMaterialTexture(
                    material,
                    textureType,
                    0,
                    path,
                    (IntBuffer) null,
                    (IntBuffer) null,
                    (FloatBuffer) null,
                    (IntBuffer) null,
                    (IntBuffer) null,
                    (IntBuffer) null)
                    != aiReturn_SUCCESS) {
                return null;
            }
            String value = path.dataString();
            if (value.startsWith("*")) {
                try {
                    int index = Integer.parseInt(value.substring(1));
                    return index >= 0 && index < embeddedTextures.size() ? embeddedTextures.get(index) : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            BufferedImage externalTexture = loadExternalMaterialTexture(assetPath, value);
            if (externalTexture != null) {
                return externalTexture;
            }
        }
        return embeddedTextures.size() == 1 ? embeddedTextures.get(0) : null;
    }

    private static BufferedImage loadExternalMaterialTexture(String modelPath, String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        String normalizedTexture = texturePath.trim().replace('\\', '/');
        String normalizedModel = modelPath == null ? "" : modelPath.replace('\\', '/');
        int slash = normalizedModel.lastIndexOf('/');
        String modelFolder = slash < 0 ? "" : normalizedModel.substring(0, slash + 1);
        String fileName = normalizedTexture.substring(normalizedTexture.lastIndexOf('/') + 1);

        BufferedImage image = AssetLoader.loadImage(modelFolder + fileName);
        if (image != null) {
            return image;
        }
        if (!normalizedTexture.equals(fileName)) {
            return AssetLoader.loadImage(modelFolder + normalizedTexture);
        }
        return null;
    }

    private static String formatHint(String assetPath) throws IOException {
        String normalized = assetPath == null ? "" : assetPath.trim().toLowerCase(Locale.ROOT);
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (normalized.endsWith(".glb")) {
            return "glb";
        }
        if (normalized.endsWith(".fbx")) {
            return "fbx";
        }
        throw new IOException("Unsupported 3D model format (expected .glb or .fbx): " + assetPath);
    }

    public List<Mesh> meshes() {
        return meshes;
    }

    public double normalizedScaleForHeight(double targetHeight) {
        return targetHeight / Math.max(0.0001, maxY - minY);
    }

    public double centerX() {
        return (minX + maxX) * 0.5;
    }

    public double baseY() {
        return minY;
    }

    public double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    public record Mesh(
            float[] positions,
            float[] texCoords,
            int[] indices,
            BufferedImage texture,
            float red,
            float green,
            float blue,
            float alpha
    ) {
    }

    private record MaterialAppearance(BufferedImage texture, float red, float green, float blue, float alpha) {
    }
}

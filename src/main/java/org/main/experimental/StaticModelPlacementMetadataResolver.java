package org.main.experimental;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.main.engine.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

import static org.lwjgl.assimp.Assimp.aiImportFileFromMemory;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_SortByPType;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

/** CPU-only model information used by Construction Kit equipment placement. */
public final class StaticModelPlacementMetadataResolver {
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private StaticModelPlacementMetadataResolver() {
    }

    public static Metadata resolve(String assetPath) throws IOException {
        String path = normalizePath(assetPath);
        if (path.isBlank()) throw new IOException("Equipment model path is missing.");
        byte[] bytes;
        try (InputStream input = AssetLoader.openAssetStream(path)) {
            bytes = input.readAllBytes();
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        long fingerprint = (crc.getValue() << 32) ^ bytes.length;
        Cached cached = CACHE.get(path);
        if (cached != null && cached.fingerprint() == fingerprint) return cached.metadata();
        Metadata metadata = importMetadata(path, bytes, fingerprint);
        CACHE.put(path, new Cached(fingerprint, metadata));
        return metadata;
    }

    public static void invalidate(String assetPath) {
        CACHE.remove(normalizePath(assetPath));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Metadata importMetadata(String path, byte[] bytes, long fingerprint) throws IOException {
        ByteBuffer source = BufferUtils.createByteBuffer(bytes.length);
        source.put(bytes).flip();
        AIScene scene = aiImportFileFromMemory(source,
                aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_SortByPType,
                formatHint(path));
        if (scene == null || scene.mRootNode() == null) {
            throw new IOException("Assimp could not import equipment model: " + path);
        }
        try {
            List<Vector3d> vertices = new ArrayList<>();
            LinkedHashMap<String, Matrix4d> nodes = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> nodeCounts = new LinkedHashMap<>();
            boolean[] referencedMeshes = new boolean[Math.max(0, scene.mNumMeshes())];
            collect(scene, scene.mRootNode(), new Matrix4d(), vertices, nodes, nodeCounts,
                    referencedMeshes);
            PointerBuffer meshes = scene.mMeshes();
            if (meshes != null) {
                for (int index = 0; index < referencedMeshes.length; index++) {
                    if (!referencedMeshes[index]) {
                        appendMesh(AIMesh.create(meshes.get(index)), new Matrix4d(), vertices);
                    }
                }
            }
            if (vertices.size() < 3) {
                throw new IOException("Equipment model contains no usable geometry: " + path);
            }
            Bounds bounds = Bounds.of(vertices);
            PrincipalAxes axes = principalAxes(vertices, bounds.center());
            List<String> duplicates = nodeCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey).toList();
            return new Metadata(path, fingerprint, List.copyOf(vertices), bounds, axes,
                    copyMatrices(nodes), duplicates);
        } finally {
            aiReleaseImport(scene);
        }
    }

    private static void collect(
            AIScene scene,
            AINode node,
            Matrix4d parent,
            List<Vector3d> vertices,
            Map<String, Matrix4d> nodes,
            Map<String, Integer> nodeCounts,
            boolean[] referencedMeshes
    ) {
        Matrix4d world = new Matrix4d(parent).mul(matrix(node.mTransformation()));
        String originalName = node.mName().dataString();
        String normalizedName = normalizeNodeName(originalName);
        nodeCounts.merge(normalizedName, 1, Integer::sum);
        nodes.putIfAbsent(normalizedName, new Matrix4d(world));
        IntBuffer meshIndices = node.mMeshes();
        PointerBuffer sceneMeshes = scene.mMeshes();
        if (meshIndices != null && sceneMeshes != null) {
            for (int index = 0; index < node.mNumMeshes(); index++) {
                int meshIndex = meshIndices.get(index);
                if (meshIndex < 0 || meshIndex >= referencedMeshes.length) continue;
                referencedMeshes[meshIndex] = true;
                appendMesh(AIMesh.create(sceneMeshes.get(meshIndex)), world, vertices);
            }
        }
        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int index = 0; index < node.mNumChildren(); index++) {
                collect(scene, AINode.create(children.get(index)), world, vertices, nodes,
                        nodeCounts, referencedMeshes);
            }
        }
    }

    private static void appendMesh(AIMesh mesh, Matrix4d transform, List<Vector3d> vertices) {
        AIVector3D.Buffer source = mesh.mVertices();
        if (source == null) return;
        for (int index = 0; index < mesh.mNumVertices(); index++) {
            AIVector3D vertex = source.get(index);
            vertices.add(transform.transformPosition(
                    new Vector3d(vertex.x(), vertex.y(), vertex.z())));
        }
    }

    private static PrincipalAxes principalAxes(List<Vector3d> vertices, Vector3d center) {
        Matrix3d covariance = new Matrix3d().zero();
        for (Vector3d vertex : vertices) {
            double x = vertex.x - center.x, y = vertex.y - center.y, z = vertex.z - center.z;
            covariance.m00(covariance.m00() + x * x);
            covariance.m01(covariance.m01() + x * y);
            covariance.m02(covariance.m02() + x * z);
            covariance.m10(covariance.m10() + y * x);
            covariance.m11(covariance.m11() + y * y);
            covariance.m12(covariance.m12() + y * z);
            covariance.m20(covariance.m20() + z * x);
            covariance.m21(covariance.m21() + z * y);
            covariance.m22(covariance.m22() + z * z);
        }
        double divisor = Math.max(1, vertices.size());
        covariance.scale(1.0 / divisor);
        Eigen eigen = jacobi(covariance);
        Integer[] order = {0, 1, 2};
        Arrays.sort(order, (left, right) -> Double.compare(eigen.values()[right], eigen.values()[left]));
        Vector3d major = eigen.vector(order[0]).normalize();
        Vector3d middle = eigen.vector(order[1]).normalize();
        Vector3d minor = eigen.vector(order[2]).normalize();
        if (new Vector3d(major).cross(middle).dot(minor) < 0) minor.negate();
        return new PrincipalAxes(major, middle, minor,
                eigen.values()[order[0]], eigen.values()[order[1]], eigen.values()[order[2]]);
    }

    private static Eigen jacobi(Matrix3d source) {
        double[][] a = {
                {source.m00(), source.m10(), source.m20()},
                {source.m01(), source.m11(), source.m21()},
                {source.m02(), source.m12(), source.m22()}
        };
        double[][] v = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int iteration = 0; iteration < 32; iteration++) {
            int p = 0, q = 1;
            if (Math.abs(a[0][2]) > Math.abs(a[p][q])) { p = 0; q = 2; }
            if (Math.abs(a[1][2]) > Math.abs(a[p][q])) { p = 1; q = 2; }
            if (Math.abs(a[p][q]) < 1e-12) break;
            double angle = 0.5 * Math.atan2(2 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(angle), s = Math.sin(angle);
            for (int k = 0; k < 3; k++) {
                double apk = a[p][k], aqk = a[q][k];
                a[p][k] = c * apk - s * aqk;
                a[q][k] = s * apk + c * aqk;
            }
            for (int k = 0; k < 3; k++) {
                double akp = a[k][p], akq = a[k][q];
                a[k][p] = c * akp - s * akq;
                a[k][q] = s * akp + c * akq;
                double vkp = v[k][p], vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        return new Eigen(new double[]{a[0][0], a[1][1], a[2][2]}, v);
    }

    private static Matrix4d matrix(AIMatrix4x4 value) {
        return new Matrix4d(
                value.a1(), value.b1(), value.c1(), value.d1(),
                value.a2(), value.b2(), value.c2(), value.d2(),
                value.a3(), value.b3(), value.c3(), value.d3(),
                value.a4(), value.b4(), value.c4(), value.d4());
    }

    private static Map<String, Matrix4d> copyMatrices(Map<String, Matrix4d> source) {
        LinkedHashMap<String, Matrix4d> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new Matrix4d(value)));
        return Map.copyOf(copy);
    }

    private static String formatHint(String assetPath) throws IOException {
        String lower = assetPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".glb")) return "glb";
        if (lower.endsWith(".fbx")) return "fbx";
        throw new IOException("Unsupported equipment model format: " + assetPath);
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    private static String normalizeNodeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Cached(long fingerprint, Metadata metadata) {
    }

    private record Eigen(double[] values, double[][] vectors) {
        Vector3d vector(int index) {
            return new Vector3d(vectors[0][index], vectors[1][index], vectors[2][index]);
        }
    }

    public record Metadata(
            String assetPath,
            long fingerprint,
            List<Vector3d> vertices,
            Bounds bounds,
            PrincipalAxes axes,
            Map<String, Matrix4d> nodes,
            List<String> duplicateNodeNames
    ) {
        public Matrix4d node(String name) {
            Matrix4d value = nodes.get(normalizeNodeName(name));
            return value == null ? null : new Matrix4d(value);
        }
    }

    public record Bounds(Vector3d minimum, Vector3d maximum) {
        public static Bounds of(List<Vector3d> vertices) {
            Vector3d minimum = new Vector3d(Double.POSITIVE_INFINITY);
            Vector3d maximum = new Vector3d(Double.NEGATIVE_INFINITY);
            for (Vector3d vertex : vertices) {
                minimum.min(vertex);
                maximum.max(vertex);
            }
            return new Bounds(minimum, maximum);
        }

        public Vector3d center() {
            return new Vector3d(minimum).add(maximum).mul(0.5);
        }

        public double height() {
            return Math.max(0.0001, maximum.y - minimum.y);
        }

        public Vector3d renderPivot() {
            return new Vector3d((minimum.x + maximum.x) * 0.5, minimum.y,
                    (minimum.z + maximum.z) * 0.5);
        }
    }

    public record PrincipalAxes(
            Vector3d major,
            Vector3d middle,
            Vector3d minor,
            double majorVariance,
            double middleVariance,
            double minorVariance
    ) {
    }
}

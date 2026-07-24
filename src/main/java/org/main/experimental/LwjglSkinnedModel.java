package org.main.experimental;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.main.content.CharacterModelDefinition;
import org.main.engine.AssetLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;

/** Assimp-backed skeletal model with OpenGL-2.1-friendly CPU skinning. */
public final class LwjglSkinnedModel {
    public static final int MAX_BONES_PER_VERTEX = 4;
    public static final int EXCESSIVE_VERTEX_WARNING = 100_000;

    public record Material(BufferedImage texture, float red, float green, float blue, float alpha) { }

    public record SkinnedMesh(
            String name,
            float[] bindPositions,
            float[] texCoords,
            int[] indices,
            int[] boneIndices,
            float[] boneWeights,
            int nodeIndex,
            Matrix4f nodeTransform,
            Material material
    ) {
        public int vertexCount() { return bindPositions.length / 3; }
    }

    public record ClipBinding(AnimationClip clip, double speed, double impactFraction, boolean looping) { }

    public record AnimationClip(String name, double durationTicks, double ticksPerSecond,
                                Map<String, NodeChannel> channels) {
        public double durationSeconds() {
            return durationTicks / Math.max(0.0001, ticksPerSecond <= 0.0 ? 25.0 : ticksPerSecond);
        }
    }

    public record NodeChannel(List<VectorKey> positions, List<RotationKey> rotations, List<VectorKey> scales) { }
    public record VectorKey(double time, Vector3f value) { }
    public record RotationKey(double time, Quaternionf value) { }
    public record Frame(List<float[]> meshPositions, double normalizedProgress) { }

    private record Node(String name, int parent, Matrix4f bindLocal, int[] meshIndices) { }
    private record Bone(String name, int nodeIndex, Matrix4f inverseBind) { }
    private record ImportedScene(List<Node> nodes, Map<String, Integer> nodeIndexByName,
                                 List<SkinnedMesh> meshes, List<Bone> bones,
                                 List<AnimationClip> clips, String signature) { }

    private final CharacterModelDefinition definition;
    private final List<Node> nodes;
    private final List<SkinnedMesh> meshes;
    private final List<Bone> bones;
    private final Map<CharacterModelDefinition.AnimationSlot, ClipBinding> bindings;
    private final Map<String, AnimationClip> embeddedClipsByName;
    private final String skeletonSignature;
    private final List<String> diagnostics;
    private final Set<String> availableClipNames;
    private final Matrix4f inverseRoot;
    private final int rootMotionNodeIndex;
    private final float minY;
    private final float maxY;
    private final float minX, maxX, minZ, maxZ;

    private LwjglSkinnedModel(CharacterModelDefinition definition, ImportedScene base,
                              Map<CharacterModelDefinition.AnimationSlot, ClipBinding> bindings,
                              Set<String> availableClipNames, List<String> diagnostics, float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ) {
        this.definition = definition;
        this.nodes = base.nodes();
        this.meshes = base.meshes();
        this.bones = base.bones();
        this.bindings = Map.copyOf(bindings);
        Map<String, AnimationClip> embeddedClips = new LinkedHashMap<>();
        for (AnimationClip clip : base.clips()) {
            embeddedClips.putIfAbsent(clip.name().toLowerCase(Locale.ROOT), clip);
        }
        this.embeddedClipsByName = Map.copyOf(embeddedClips);
        this.skeletonSignature = base.signature();
        this.diagnostics = List.copyOf(diagnostics);
        this.availableClipNames = Set.copyOf(availableClipNames);
        this.inverseRoot = base.nodes().isEmpty() ? new Matrix4f()
                : new Matrix4f(base.nodes().get(0).bindLocal()).invert();
        this.rootMotionNodeIndex = findRootMotionNode(base.nodes(), base.bones());
        this.minY = minY;
        this.maxY = maxY;
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
    }

    public static LwjglSkinnedModel load(CharacterModelDefinition definition) throws IOException {
        CharacterModelDefinition safe = definition == null ? CharacterModelDefinition.empty() : definition;
        if (!safe.hasModel()) throw new IOException("Character model path is blank.");
        ImportedScene base = importScene(safe.modelPath(), true);
        List<String> diagnostics = new ArrayList<>();
        if (base.bones().isEmpty()) diagnostics.add("Model has no bones; procedural whole-model fallback will be used.");
        int vertexCount = base.meshes().stream().mapToInt(SkinnedMesh::vertexCount).sum();
        if (vertexCount > EXCESSIVE_VERTEX_WARNING) {
            diagnostics.add("Model has " + vertexCount + " vertices; CPU skinning may be expensive.");
        }
        long untexturedMeshes = base.meshes().stream()
                .filter(mesh -> mesh.material().texture() == null).count();
        if (untexturedMeshes > 0) {
            diagnostics.add(untexturedMeshes + " mesh material(s) have no resolved texture; base-color factors will be used.");
        }

        Map<String, ImportedScene> externalScenes = new HashMap<>();
        LinkedHashSet<String> availableClipNames = new LinkedHashSet<>();
        base.clips().forEach(clip -> availableClipNames.add(clip.name()));
        EnumMap<CharacterModelDefinition.AnimationSlot, ClipBinding> bindings =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            CharacterModelDefinition.AnimationBinding authored = safe.animationBinding(slot);
            ImportedScene source = base;
            if (authored.isPresent() && !samePath(authored.path(), safe.modelPath())) {
                try {
                    source = externalScenes.computeIfAbsent(authored.path(), path -> {
                        try { return importScene(path, false); }
                        catch (IOException exception) { throw new ImportFailure(exception); }
                    });
                } catch (ImportFailure failure) {
                    diagnostics.add(slot.displayName() + ": " + failure.getCause().getMessage());
                    continue;
                }
                if (!base.signature().equals(source.signature())) {
                    diagnostics.add(slot.displayName() + ": skeleton hierarchy does not match base model (rigId '"
                            + safe.rigId() + "').");
                    continue;
                }
                source.clips().forEach(clip -> availableClipNames.add(clip.name()));
            }
            AnimationClip clip = authored.isPresent()
                    ? selectClip(source.clips(), authored.clipName(), slot)
                    : source.clips().stream()
                    .filter(candidate -> candidate.name().toLowerCase(Locale.ROOT)
                            .contains(slot.name().toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
            if (clip == null) {
                diagnostics.add(slot.displayName() + ": no compatible animation clip; using procedural fallback.");
                continue;
            }
            bindings.put(slot, new ClipBinding(clip, authored.playbackSpeed(), authored.impactFraction(), slot.looping()));
        }

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (SkinnedMesh mesh : base.meshes()) {
            for (int index = 0; index < mesh.bindPositions().length; index += 3) {
                minX = Math.min(minX, mesh.bindPositions()[index]); maxX = Math.max(maxX, mesh.bindPositions()[index]);
                minY = Math.min(minY, mesh.bindPositions()[index + 1]); maxY = Math.max(maxY, mesh.bindPositions()[index + 1]);
                minZ = Math.min(minZ, mesh.bindPositions()[index + 2]); maxZ = Math.max(maxZ, mesh.bindPositions()[index + 2]);
            }
        }
        if (!Float.isFinite(minY)) { minX = minZ = minY = 0f; maxX = maxZ = 0f; maxY = 1f; }
        return new LwjglSkinnedModel(safe, base, bindings, availableClipNames, diagnostics,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    public CharacterModelDefinition definition() { return definition; }
    public List<SkinnedMesh> meshes() { return meshes; }
    public String skeletonSignature() { return skeletonSignature; }
    public List<String> diagnostics() { return diagnostics; }
    public Set<String> clipNames() {
        return availableClipNames;
    }
    public boolean hasClip(CharacterModelDefinition.AnimationSlot slot) { return bindings.containsKey(slot); }
    public double clipDurationSeconds(CharacterModelDefinition.AnimationSlot slot) {
        ClipBinding binding = bindings.get(slot);
        return binding == null ? 0.0
                : binding.clip().durationSeconds() / Math.max(0.0001, binding.speed());
    }
    public double impactFraction(CharacterModelDefinition.AnimationSlot slot) {
        ClipBinding binding = bindings.get(slot);
        return binding == null ? CharacterModelDefinition.DEFAULT_IMPACT_FRACTION
                : binding.impactFraction();
    }
    public double normalizedScaleForHeight(double targetHeight) {
        return targetHeight / Math.max(0.0001, maxY - minY);
    }
    public double centerX() { return (minX + maxX) * 0.5; }
    public double baseY() { return minY; }
    public double centerZ() { return (minZ + maxZ) * 0.5; }

    public Frame skin(CharacterModelDefinition.AnimationSlot slot, double elapsedSeconds) {
        ClipBinding binding = bindings.get(slot);
        if (binding == null) {
            return new Frame(meshes.stream().map(mesh -> mesh.bindPositions().clone()).toList(), 0.0);
        }
        return skin(binding, elapsedSeconds);
    }

    private Frame skin(ClipBinding binding, double elapsedSeconds) {
        AnimationClip clip = binding.clip();
        double duration = Math.max(0.0001, clip.durationTicks());
        double ticks = Math.max(0.0, elapsedSeconds) * Math.max(0.0001, clip.ticksPerSecond()) * binding.speed();
        double time = binding.looping() ? ticks % duration : Math.min(duration, ticks);
        Matrix4f[] globals = new Matrix4f[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            Matrix4f local = evaluateLocal(node, clip.channels().get(node.name()), time,
                    index == rootMotionNodeIndex);
            globals[index] = node.parent() < 0 ? local : new Matrix4f(globals[node.parent()]).mul(local);
        }
        Matrix4f[] skinMatrices = new Matrix4f[bones.size()];
        for (int index = 0; index < bones.size(); index++) {
            Bone bone = bones.get(index);
            skinMatrices[index] = new Matrix4f(inverseRoot).mul(globals[bone.nodeIndex()]).mul(bone.inverseBind());
        }
        List<float[]> positions = new ArrayList<>(meshes.size());
        Vector3f source = new Vector3f();
        Vector3f transformed = new Vector3f();
        for (SkinnedMesh mesh : meshes) {
            float[] output = new float[mesh.bindPositions().length];
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                int p = vertex * 3;
                source.set(mesh.bindPositions()[p], mesh.bindPositions()[p + 1], mesh.bindPositions()[p + 2]);
                transformed.zero();
                float total = 0f;
                for (int influence = 0; influence < MAX_BONES_PER_VERTEX; influence++) {
                    int offset = vertex * MAX_BONES_PER_VERTEX + influence;
                    float weight = mesh.boneWeights()[offset];
                    int boneIndex = mesh.boneIndices()[offset];
                    if (weight <= 0f || boneIndex < 0 || boneIndex >= skinMatrices.length) continue;
                    Vector3f weighted = skinMatrices[boneIndex].transformPosition(new Vector3f(source)).mul(weight);
                    transformed.add(weighted);
                    total += weight;
                }
                if (total <= 0f) {
                    Matrix4f nodeMatrix = mesh.nodeIndex() >= 0 && mesh.nodeIndex() < globals.length
                            ? new Matrix4f(inverseRoot).mul(globals[mesh.nodeIndex()])
                            : mesh.nodeTransform();
                    nodeMatrix.transformPosition(source, transformed);
                }
                output[p] = transformed.x; output[p + 1] = transformed.y; output[p + 2] = transformed.z;
            }
            positions.add(output);
        }
        return new Frame(List.copyOf(positions), time / duration);
    }

    public Frame skinNormalized(CharacterModelDefinition.AnimationSlot slot, double normalizedProgress) {
        ClipBinding binding = bindings.get(slot);
        if (binding == null) return skin(slot, 0.0);
        double progress = Math.max(0.0, Math.min(1.0, normalizedProgress));
        return skin(binding, progress * binding.clip().durationSeconds() / Math.max(0.0001, binding.speed()));
    }

    /** Returns an animated node/socket transform in model space. */
    public Matrix4f nodeTransformNormalized(
            CharacterModelDefinition.AnimationSlot slot,
            double normalizedProgress,
            String nodeName
    ) {
        Integer nodeIndex = nodeName == null ? null : baseNodeIndex(nodeName);
        if (nodeIndex == null) return null;
        ClipBinding binding = bindings.get(slot);
        if (binding == null) {
            Matrix4f[] globals = bindGlobals();
            return new Matrix4f(inverseRoot).mul(globals[nodeIndex]);
        }
        double progress = Math.max(0.0, Math.min(1.0, normalizedProgress));
        double elapsed = progress * binding.clip().durationSeconds()
                / Math.max(0.0001, binding.speed());
        Matrix4f[] globals = evaluateGlobals(binding, elapsed);
        return new Matrix4f(inverseRoot).mul(globals[nodeIndex]);
    }

    public boolean hasNode(String nodeName) {
        return baseNodeIndex(nodeName) != null;
    }

    private Integer baseNodeIndex(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return null;
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).name().equalsIgnoreCase(nodeName.trim())) return index;
        }
        return null;
    }

    private Matrix4f[] bindGlobals() {
        Matrix4f[] globals = new Matrix4f[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            globals[index] = node.parent() < 0
                    ? new Matrix4f(node.bindLocal())
                    : new Matrix4f(globals[node.parent()]).mul(node.bindLocal());
        }
        return globals;
    }

    private Matrix4f[] evaluateGlobals(ClipBinding binding, double elapsedSeconds) {
        AnimationClip clip = binding.clip();
        double duration = Math.max(0.0001, clip.durationTicks());
        double ticks = Math.max(0.0, elapsedSeconds)
                * Math.max(0.0001, clip.ticksPerSecond()) * binding.speed();
        double time = binding.looping() ? ticks % duration : Math.min(duration, ticks);
        Matrix4f[] globals = new Matrix4f[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            Matrix4f local = evaluateLocal(node, clip.channels().get(node.name()), time,
                    index == rootMotionNodeIndex);
            globals[index] = node.parent() < 0
                    ? local
                    : new Matrix4f(globals[node.parent()]).mul(local);
        }
        return globals;
    }

    /**
     * Previews a clip embedded in the base character file without reimporting the
     * model. Runtime slot bindings continue to use {@link #skinNormalized}.
     */
    public Frame skinEmbeddedClipNormalized(
            String clipName,
            CharacterModelDefinition.AnimationSlot fallbackSlot,
            double normalizedProgress
    ) {
        String normalizedName = clipName == null ? "" : clipName.trim().toLowerCase(Locale.ROOT);
        AnimationClip clip = embeddedClipsByName.get(normalizedName);
        if (clip == null) {
            return skinNormalized(fallbackSlot, normalizedProgress);
        }
        CharacterModelDefinition.AnimationBinding authored = definition.animationBinding(fallbackSlot);
        ClipBinding previewBinding = new ClipBinding(
                clip,
                authored.playbackSpeed(),
                authored.impactFraction(),
                fallbackSlot != null && fallbackSlot.looping());
        double progress = Math.max(0.0, Math.min(1.0, normalizedProgress));
        return skin(previewBinding,
                progress * clip.durationSeconds() / Math.max(0.0001, previewBinding.speed()));
    }

    private static Matrix4f evaluateLocal(Node node, NodeChannel channel, double time, boolean stripRootMotion) {
        if (channel == null) return new Matrix4f(node.bindLocal());
        Vector3f translation = interpolateVector(channel.positions(), time, new Vector3f(node.bindLocal().m30(), node.bindLocal().m31(), node.bindLocal().m32()));
        if (stripRootMotion) { translation.x = node.bindLocal().m30(); translation.z = node.bindLocal().m32(); }
        Vector3f scale = interpolateVector(channel.scales(), time, node.bindLocal().getScale(new Vector3f()));
        Quaternionf rotation = interpolateRotation(channel.rotations(), time, node.bindLocal().getUnnormalizedRotation(new Quaternionf()).normalize());
        return new Matrix4f().translationRotateScale(translation, rotation, scale);
    }

    private static int findRootMotionNode(List<Node> nodes, List<Bone> bones) {
        if (nodes.isEmpty() || bones.isEmpty()) return 0;
        int index = Math.max(0, bones.get(0).nodeIndex());
        while (index > 0 && nodes.get(index).parent() > 0) index = nodes.get(index).parent();
        return index;
    }

    private static Vector3f interpolateVector(List<VectorKey> keys, double time, Vector3f fallback) {
        if (keys == null || keys.isEmpty()) return fallback;
        if (keys.size() == 1 || time <= keys.get(0).time()) return new Vector3f(keys.get(0).value());
        for (int i = 0; i < keys.size() - 1; i++) {
            VectorKey a = keys.get(i), b = keys.get(i + 1);
            if (time <= b.time()) {
                float t = (float) ((time - a.time()) / Math.max(0.0001, b.time() - a.time()));
                return new Vector3f(a.value()).lerp(b.value(), t);
            }
        }
        return new Vector3f(keys.get(keys.size() - 1).value());
    }

    private static Quaternionf interpolateRotation(List<RotationKey> keys, double time, Quaternionf fallback) {
        if (keys == null || keys.isEmpty()) return fallback;
        if (keys.size() == 1 || time <= keys.get(0).time()) return new Quaternionf(keys.get(0).value());
        for (int i = 0; i < keys.size() - 1; i++) {
            RotationKey a = keys.get(i), b = keys.get(i + 1);
            if (time <= b.time()) {
                float t = (float) ((time - a.time()) / Math.max(0.0001, b.time() - a.time()));
                return new Quaternionf(a.value()).slerp(b.value(), t).normalize();
            }
        }
        return new Quaternionf(keys.get(keys.size() - 1).value());
    }

    private static ImportedScene importScene(String assetPath, boolean includeMeshes) throws IOException {
        byte[] bytes;
        try (InputStream stream = AssetLoader.openAssetStream(assetPath)) { bytes = stream.readAllBytes(); }
        ByteBuffer source = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        AIScene scene = aiImportFileFromMemory(source,
                aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_LimitBoneWeights
                        | aiProcess_SortByPType, formatHint(assetPath));
        if (scene == null || scene.mRootNode() == null) throw new IOException("Assimp could not import model: " + assetPath);
        try {
            List<Node> nodes = new ArrayList<>();
            Map<String, Integer> byName = new LinkedHashMap<>();
            collectNodes(scene.mRootNode(), -1, nodes, byName);
            List<BufferedImage> textures = loadEmbeddedTextures(scene);
            List<SkinnedMesh> meshes = new ArrayList<>();
            List<Bone> bones = new ArrayList<>();
            Map<String, Integer> boneByName = new HashMap<>();
            if (includeMeshes && scene.mMeshes() != null) {
                int[] meshNodes = meshNodeIndices(nodes, scene.mNumMeshes());
                for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
                    AIMesh mesh = AIMesh.create(scene.mMeshes().get(meshIndex));
                    meshes.add(readMesh(scene, mesh, assetPath, textures, meshNodes[meshIndex], byName, bones, boneByName));
                }
            }
            List<AnimationClip> clips = readAnimations(scene);
            String signature = skeletonSignature(nodes, bones, clips);
            return new ImportedScene(List.copyOf(nodes), Map.copyOf(byName), List.copyOf(meshes),
                    List.copyOf(bones), List.copyOf(clips), signature);
        } finally { aiReleaseImport(scene); }
    }

    private static void collectNodes(AINode source, int parent, List<Node> nodes, Map<String, Integer> byName) {
        int index = nodes.size();
        int[] meshes = new int[source.mNumMeshes()];
        IntBuffer sourceMeshes = source.mMeshes();
        if (sourceMeshes != null) for (int i = 0; i < meshes.length; i++) meshes[i] = sourceMeshes.get(i);
        String name = source.mName().dataString();
        nodes.add(new Node(name, parent, matrix(source.mTransformation()), meshes));
        byName.putIfAbsent(name, index);
        PointerBuffer children = source.mChildren();
        if (children != null) for (int i = 0; i < source.mNumChildren(); i++)
            collectNodes(AINode.create(children.get(i)), index, nodes, byName);
    }

    private static int[] meshNodeIndices(List<Node> nodes, int count) {
        int[] result = new int[count];
        Arrays.fill(result, 0);
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            for (int meshIndex : nodes.get(nodeIndex).meshIndices()) if (meshIndex >= 0 && meshIndex < count) result[meshIndex] = nodeIndex;
        }
        return result;
    }

    private static SkinnedMesh readMesh(AIScene scene, AIMesh source, String assetPath,
                                        List<BufferedImage> textures, int nodeIndex,
                                        Map<String, Integer> nodeByName, List<Bone> bones,
                                        Map<String, Integer> boneByName) {
        int count = source.mNumVertices();
        float[] positions = new float[count * 3], uv = new float[count * 2], weights = new float[count * 4];
        int[] boneIndices = new int[count * 4];
        Arrays.fill(boneIndices, -1);
        AIVector3D.Buffer vertices = source.mVertices(), texCoords = source.mTextureCoords(0);
        for (int i = 0; i < count; i++) {
            AIVector3D v = vertices.get(i); positions[i * 3] = v.x(); positions[i * 3 + 1] = v.y(); positions[i * 3 + 2] = v.z();
            if (texCoords != null) { AIVector3D t = texCoords.get(i); uv[i * 2] = t.x(); uv[i * 2 + 1] = t.y(); }
        }
        PointerBuffer sourceBones = source.mBones();
        if (sourceBones != null) for (int i = 0; i < source.mNumBones(); i++) {
            AIBone sourceBone = AIBone.create(sourceBones.get(i));
            String name = sourceBone.mName().dataString();
            int global = boneByName.computeIfAbsent(name, key -> {
                int created = bones.size();
                bones.add(new Bone(key, nodeByName.getOrDefault(key, 0), matrix(sourceBone.mOffsetMatrix())));
                return created;
            });
            AIVertexWeight.Buffer sourceWeights = sourceBone.mWeights();
            for (int w = 0; w < sourceBone.mNumWeights(); w++) addWeight(boneIndices, weights,
                    sourceWeights.get(w).mVertexId(), global, sourceWeights.get(w).mWeight());
        }
        normalizeWeights(weights, count);
        List<Integer> indexList = new ArrayList<>();
        AIFace.Buffer faces = source.mFaces();
        for (int i = 0; i < source.mNumFaces(); i++) {
            IntBuffer face = faces.get(i).mIndices();
            if (face.remaining() == 3) { indexList.add(face.get(0)); indexList.add(face.get(1)); indexList.add(face.get(2)); }
        }
        return new SkinnedMesh(source.mName().dataString(), positions, uv,
                indexList.stream().mapToInt(Integer::intValue).toArray(),
                boneIndices, weights, nodeIndex, new Matrix4f(), material(scene, source, textures, assetPath));
    }

    private static void addWeight(int[] indices, float[] weights, int vertex, int bone, float weight) {
        if (vertex < 0 || vertex * 4 + 3 >= weights.length || weight <= 0f) return;
        int base = vertex * 4, target = -1;
        for (int i = 0; i < 4; i++) if (weights[base + i] == 0f) { target = i; break; }
        if (target < 0) {
            target = 0;
            for (int i = 1; i < 4; i++) if (weights[base + i] < weights[base + target]) target = i;
            if (weight <= weights[base + target]) return;
        }
        indices[base + target] = bone; weights[base + target] = weight;
    }

    private static void normalizeWeights(float[] weights, int count) {
        for (int vertex = 0; vertex < count; vertex++) {
            int base = vertex * 4; float sum = 0f;
            for (int i = 0; i < 4; i++) sum += weights[base + i];
            if (sum > 0f) for (int i = 0; i < 4; i++) weights[base + i] /= sum;
        }
    }

    private static List<AnimationClip> readAnimations(AIScene scene) {
        List<AnimationClip> clips = new ArrayList<>();
        PointerBuffer animations = scene.mAnimations();
        if (animations == null) return clips;
        for (int i = 0; i < scene.mNumAnimations(); i++) {
            AIAnimation animation = AIAnimation.create(animations.get(i));
            Map<String, NodeChannel> channels = new LinkedHashMap<>();
            PointerBuffer sourceChannels = animation.mChannels();
            if (sourceChannels != null) for (int c = 0; c < animation.mNumChannels(); c++) {
                AINodeAnim channel = AINodeAnim.create(sourceChannels.get(c));
                List<VectorKey> positions = new ArrayList<>(), scales = new ArrayList<>();
                List<RotationKey> rotations = new ArrayList<>();
                AIVectorKey.Buffer pk = channel.mPositionKeys();
                for (int k = 0; k < channel.mNumPositionKeys(); k++) positions.add(vectorKey(pk.get(k)));
                AIVectorKey.Buffer sk = channel.mScalingKeys();
                for (int k = 0; k < channel.mNumScalingKeys(); k++) scales.add(vectorKey(sk.get(k)));
                AIQuatKey.Buffer rk = channel.mRotationKeys();
                for (int k = 0; k < channel.mNumRotationKeys(); k++) {
                    AIQuatKey key = rk.get(k); AIQuaternion q = key.mValue();
                    rotations.add(new RotationKey(key.mTime(), new Quaternionf(q.x(), q.y(), q.z(), q.w()).normalize()));
                }
                channels.put(channel.mNodeName().dataString(), new NodeChannel(List.copyOf(positions), List.copyOf(rotations), List.copyOf(scales)));
            }
            String name = animation.mName().dataString();
            clips.add(new AnimationClip(name.isBlank() ? "Animation " + (i + 1) : name,
                    animation.mDuration(), animation.mTicksPerSecond() <= 0 ? 25.0 : animation.mTicksPerSecond(), Map.copyOf(channels)));
        }
        return clips;
    }

    private static VectorKey vectorKey(AIVectorKey key) {
        AIVector3D value = key.mValue();
        return new VectorKey(key.mTime(), new Vector3f(value.x(), value.y(), value.z()));
    }

    private static AnimationClip selectClip(List<AnimationClip> clips, String requested,
                                            CharacterModelDefinition.AnimationSlot slot) {
        if (clips == null || clips.isEmpty()) return null;
        if (requested != null && !requested.isBlank())
            return clips.stream().filter(clip -> clip.name().equalsIgnoreCase(requested.trim())).findFirst().orElse(null);
        return clips.stream().filter(clip -> clip.name().toLowerCase(Locale.ROOT)
                        .contains(slot.name().toLowerCase(Locale.ROOT)))
                .findFirst().orElse(clips.get(0));
    }

    private static Matrix4f matrix(AIMatrix4x4 m) {
        return new Matrix4f(m.a1(), m.b1(), m.c1(), m.d1(), m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(), m.a4(), m.b4(), m.c4(), m.d4());
    }

    private static String skeletonSignature(
            List<Node> nodes,
            List<Bone> bones,
            List<AnimationClip> clips
    ) {
        LinkedHashSet<String> relevantNames = new LinkedHashSet<>();
        for (Bone bone : bones) relevantNames.add(bone.name());
        for (AnimationClip clip : clips) relevantNames.addAll(clip.channels().keySet());
        if (relevantNames.isEmpty()) {
            for (Node node : nodes) relevantNames.add(node.name());
        }
        Set<Integer> relevantIndexes = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            if (!relevantNames.contains(nodes.get(index).name())) continue;
            int current = index;
            while (current >= 0 && relevantIndexes.add(current)) current = nodes.get(current).parent();
        }
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < nodes.size(); index++) {
            if (!relevantIndexes.contains(index)) continue;
            Node node = nodes.get(index);
            String parentName = node.parent() >= 0 && relevantIndexes.contains(node.parent())
                    ? nodes.get(node.parent()).name() : "";
            value.append(node.name()).append('@').append(parentName).append(';');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) { return Integer.toHexString(value.toString().hashCode()); }
    }

    private static Material material(AIScene scene, AIMesh mesh, List<BufferedImage> textures, String assetPath) {
        AIMaterial material = scene.mMaterials() == null || mesh.mMaterialIndex() < 0
                || mesh.mMaterialIndex() >= scene.mNumMaterials()
                ? null : AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()));
        float[] color = {1f, 1f, 1f, 1f};
        if (material != null) try (AIColor4D aiColor = AIColor4D.calloc()) {
            int result = aiGetMaterialColor(material, AI_MATKEY_BASE_COLOR, 0, 0, aiColor);
            if (result != aiReturn_SUCCESS) result = aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, 0, 0, aiColor);
            if (result == aiReturn_SUCCESS) color = new float[]{aiColor.r(), aiColor.g(), aiColor.b(), aiColor.a()};
        }
        BufferedImage texture = materialTexture(material, aiTextureType_BASE_COLOR, textures, assetPath);
        if (texture == null) texture = materialTexture(material, aiTextureType_DIFFUSE, textures, assetPath);
        if (texture == null && textures.size() == 1) texture = textures.get(0);
        return new Material(texture, color[0], color[1], color[2], color[3]);
    }

    private static BufferedImage materialTexture(AIMaterial material, int type, List<BufferedImage> embedded, String modelPath) {
        if (material == null) return null;
        try (AIString path = AIString.calloc()) {
            if (aiGetMaterialTexture(material, type, 0, path,
                    (IntBuffer) null, (IntBuffer) null, (FloatBuffer) null,
                    (IntBuffer) null, (IntBuffer) null, (IntBuffer) null) != aiReturn_SUCCESS) return null;
            String value = path.dataString().replace('\\', '/');
            if (value.startsWith("*")) {
                try { int index = Integer.parseInt(value.substring(1)); return index >= 0 && index < embedded.size() ? embedded.get(index) : null; }
                catch (NumberFormatException ignored) { return null; }
            }
            String normalizedModel = modelPath.replace('\\', '/');
            int slash = normalizedModel.lastIndexOf('/');
            String folder = slash < 0 ? "" : normalizedModel.substring(0, slash + 1);
            String file = value.substring(value.lastIndexOf('/') + 1);
            BufferedImage image = AssetLoader.loadImage(folder + file);
            return image != null ? image : AssetLoader.loadImage(folder + value);
        }
    }

    private static List<BufferedImage> loadEmbeddedTextures(AIScene scene) throws IOException {
        List<BufferedImage> result = new ArrayList<>();
        PointerBuffer textures = scene.mTextures();
        if (textures == null) return result;
        for (int i = 0; i < scene.mNumTextures(); i++) {
            AITexture texture = AITexture.create(textures.get(i)); BufferedImage image = null;
            if (texture.mHeight() == 0 && texture.mWidth() > 0) {
                ByteBuffer bytes = texture.pcDataCompressed(); byte[] data = new byte[texture.mWidth()]; bytes.get(0, data);
                image = ImageIO.read(new ByteArrayInputStream(data));
            } else if (texture.mWidth() > 0 && texture.mHeight() > 0) {
                image = new BufferedImage(texture.mWidth(), texture.mHeight(), BufferedImage.TYPE_INT_ARGB);
                AITexel.Buffer pixels = texture.pcData();
                for (int y = 0; y < texture.mHeight(); y++) for (int x = 0; x < texture.mWidth(); x++) {
                    AITexel p = pixels.get(y * texture.mWidth() + x);
                    image.setRGB(x, y, Byte.toUnsignedInt(p.a()) << 24 | Byte.toUnsignedInt(p.r()) << 16
                            | Byte.toUnsignedInt(p.g()) << 8 | Byte.toUnsignedInt(p.b()));
                }
            }
            result.add(image);
        }
        return result;
    }

    private static String formatHint(String path) throws IOException {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (value.endsWith(".glb") || value.endsWith(".gltf")) return value.endsWith(".glb") ? "glb" : "gltf";
        if (value.endsWith(".fbx")) return "fbx";
        throw new IOException("Unsupported character model format: " + path);
    }

    private static boolean samePath(String a, String b) {
        return a != null && b != null && a.replace('\\', '/').equalsIgnoreCase(b.replace('\\', '/'));
    }

    private static final class ImportFailure extends RuntimeException {
        ImportFailure(IOException cause) { super(cause); }
        @Override public synchronized IOException getCause() { return (IOException) super.getCause(); }
    }
}

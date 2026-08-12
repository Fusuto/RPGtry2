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
    private static final Map<CharacterModelDefinition, LwjglSkinnedModel> SHARED_CACHE = new HashMap<>();

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
    public record Pose(Matrix4f[] boneMatrices, Matrix4f[] nodeMatrices, double normalizedProgress) {
        public Matrix4f nodeMatrix(int nodeIndex, Matrix4f fallback) {
            return nodeIndex >= 0 && nodeIndex < nodeMatrices.length
                    ? nodeMatrices[nodeIndex]
                    : fallback;
        }
    }

    /** Stable skeleton information used by authoring tools and attachment validation. */
    public record SkeletonNodeMetadata(
            String name,
            String parentName,
            boolean weightedBone,
            boolean animatedNode,
            boolean socketOrHelper,
            boolean meshNode,
            Matrix4f bindPoseTransform
    ) {
        public SkeletonNodeMetadata {
            bindPoseTransform = new Matrix4f(bindPoseTransform);
        }

        @Override public Matrix4f bindPoseTransform() {
            return new Matrix4f(bindPoseTransform);
        }

        public String categoryLabel() {
            if (weightedBone) return "Bone";
            if (animatedNode) return "Animated";
            if (socketOrHelper) return "Helper";
            if (meshNode) return "Mesh";
            return "Node";
        }

        public String displayLabel() {
            return name + " [" + categoryLabel() + "]";
        }
    }

    public record SkeletonNodePose(SkeletonNodeMetadata metadata, Matrix4f currentTransform) {
        public SkeletonNodePose {
            currentTransform = new Matrix4f(currentTransform);
        }

        @Override public Matrix4f currentTransform() {
            return new Matrix4f(currentTransform);
        }
    }

    private record Node(String name, int parent, Matrix4f bindLocal, int[] meshIndices) { }
    private record Bone(String name, int nodeIndex, Matrix4f inverseBind) { }
    private record ImportedScene(List<Node> nodes, Map<String, Integer> nodeIndexByName,
                                 List<SkinnedMesh> meshes, List<Bone> bones,
                                 List<AnimationClip> clips, String signature) { }
    private record ModelBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }

    private final CharacterModelDefinition definition;
    private final List<Node> nodes;
    private final List<SkinnedMesh> meshes;
    private final List<Bone> bones;
    private final List<float[]> bindPosePositions;
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
    private final ThreadLocal<SkinWorkspaceRing> skinWorkspaces;
    private final ThreadLocal<PoseWorkspaceRing> poseWorkspaces;
    private final Pose bindPose;
    private final List<SkeletonNodeMetadata> skeletonMetadata;

    private LwjglSkinnedModel(CharacterModelDefinition definition, ImportedScene base,
                              Map<CharacterModelDefinition.AnimationSlot, ClipBinding> bindings,
                              Set<String> availableClipNames, List<String> diagnostics) {
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
        this.skinWorkspaces = ThreadLocal.withInitial(() -> new SkinWorkspaceRing(this.meshes));
        this.poseWorkspaces = ThreadLocal.withInitial(() ->
                new PoseWorkspaceRing(this.nodes.size(), this.bones.size()));
        this.bindPose = copyPose(poseFromGlobals(bindGlobals(), 0.0));
        this.skeletonMetadata = buildSkeletonMetadata();
        this.bindPosePositions = copyPositions(skinPositions(bindPose));
        ModelBounds bounds = boundsOf(bindPosePositions);
        this.minY = bounds.minY();
        this.maxY = bounds.maxY();
        this.minX = bounds.minX();
        this.maxX = bounds.maxX();
        this.minZ = bounds.minZ();
        this.maxZ = bounds.maxZ();
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

        return new LwjglSkinnedModel(safe, base, bindings, availableClipNames, diagnostics);
    }

    public static synchronized LwjglSkinnedModel loadCached(
            CharacterModelDefinition definition
    ) throws IOException {
        CharacterModelDefinition safe = definition == null
                ? CharacterModelDefinition.empty()
                : definition;
        LwjglSkinnedModel cached = SHARED_CACHE.get(safe);
        if (cached != null) {
            return cached;
        }
        LwjglSkinnedModel loaded = load(safe);
        SHARED_CACHE.put(safe, loaded);
        return loaded;
    }

    public static synchronized void clearSharedCache() {
        SHARED_CACHE.clear();
    }

    public CharacterModelDefinition definition() { return definition; }
    public List<SkinnedMesh> meshes() { return meshes; }
    public String skeletonSignature() { return skeletonSignature; }
    public List<String> diagnostics() { return diagnostics; }
    public Set<String> clipNames() {
        return availableClipNames;
    }
    public List<String> nodeNames() {
        return nodes.stream().map(Node::name).toList();
    }
    public List<SkeletonNodeMetadata> skeletonNodes() {
        return skeletonMetadata;
    }

    /** True when the node, or one of its parents, participates in skeletal animation. */
    public boolean followsAnimatedHierarchy(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return false;
        Map<String, SkeletonNodeMetadata> byName = new HashMap<>();
        for (SkeletonNodeMetadata node : skeletonMetadata) {
            byName.put(node.name().toLowerCase(Locale.ROOT), node);
        }
        SkeletonNodeMetadata current = byName.get(nodeName.trim().toLowerCase(Locale.ROOT));
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current.name().toLowerCase(Locale.ROOT))) {
            if (current.weightedBone() || current.animatedNode()) return true;
            current = byName.get(current.parentName().toLowerCase(Locale.ROOT));
        }
        return false;
    }

    /**
     * Returns the direction continuing from a node's parent through the node,
     * expressed in the selected node's local coordinates.
     */
    public Vector3f outwardDirectionFromParentNormalized(
            CharacterModelDefinition.AnimationSlot slot,
            double normalizedProgress,
            String nodeName
    ) {
        Integer nodeIndex = baseNodeIndex(nodeName);
        if (nodeIndex == null) return new Vector3f(0, 1, 0);
        int parentIndex = nodes.get(nodeIndex).parent();
        if (parentIndex < 0 || parentIndex >= nodes.size()) return new Vector3f(0, 1, 0);
        Pose pose = poseNormalized(slot, normalizedProgress);
        Matrix4f node = pose.nodeMatrices()[nodeIndex];
        Matrix4f parent = pose.nodeMatrices()[parentIndex];
        Vector3f direction = node.getTranslation(new Vector3f())
                .sub(parent.getTranslation(new Vector3f()));
        if (direction.lengthSquared() < 0.000001f) return new Vector3f(0, 1, 0);
        Quaternionf inverseNodeRotation = node.getUnnormalizedRotation(
                new Quaternionf()).normalize().conjugate();
        return inverseNodeRotation.transform(direction).normalize();
    }

    /**
     * Returns a practical grip point beneath a hand/wrist attachment instead
     * of the skeletal joint at the base of the wrist. The direct child joints
     * (normally the finger roots) provide a model-authored palm location.
     * Non-hand attachment nodes deliberately remain at their exact origin.
     */
    public Vector3f handGripOffsetNormalized(
            CharacterModelDefinition.AnimationSlot slot,
            double normalizedProgress,
            String nodeName
    ) {
        Integer nodeIndex = baseNodeIndex(nodeName);
        if (nodeIndex == null) return new Vector3f();
        String normalized = nodes.get(nodeIndex).name().toLowerCase(Locale.ROOT);
        if (!normalized.contains("hand") && !normalized.contains("wrist")) {
            return new Vector3f();
        }
        // Hand joints in common Blender/GLTF rigs sit at the wrist. Continue
        // along the authored forearm direction to reach the closed-palm grip.
        // A fixed normalized hand depth is more stable than averaging finger
        // roots, which are frequently spread asymmetrically by the rest pose.
        return outwardDirectionFromParentNormalized(slot, normalizedProgress, nodeName)
                .mul(0.20f);
    }

    private List<SkeletonNodeMetadata> buildSkeletonMetadata() {
        Set<String> weighted = new HashSet<>();
        bones.forEach(bone -> weighted.add(bone.name().toLowerCase(Locale.ROOT)));
        Set<String> animated = new HashSet<>();
        embeddedClipsByName.values().forEach(clip -> clip.channels().keySet()
                .forEach(name -> animated.add(name.toLowerCase(Locale.ROOT))));
        bindings.values().forEach(binding -> binding.clip().channels().keySet()
                .forEach(name -> animated.add(name.toLowerCase(Locale.ROOT))));
        List<SkeletonNodeMetadata> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            String lower = node.name().toLowerCase(Locale.ROOT);
            String parent = node.parent() >= 0 && node.parent() < nodes.size()
                    ? nodes.get(node.parent()).name() : "";
            boolean helper = lower.contains("socket") || lower.contains("helper")
                    || lower.contains("grip") || lower.contains("prop")
                    || lower.contains("attach");
            Matrix4f bindTransform = index < bindPose.nodeMatrices().length
                    ? bindPose.nodeMatrices()[index] : node.bindLocal();
            result.add(new SkeletonNodeMetadata(node.name(), parent,
                    weighted.contains(lower), animated.contains(lower), helper,
                    node.meshIndices().length > 0, bindTransform));
        }
        return List.copyOf(result);
    }

    public List<SkeletonNodePose> skeletonPose(
            CharacterModelDefinition.AnimationSlot slot,
            double normalizedProgress
    ) {
        Pose pose = poseNormalized(slot, normalizedProgress);
        List<SkeletonNodeMetadata> metadata = skeletonNodes();
        List<SkeletonNodePose> result = new ArrayList<>(metadata.size());
        for (int index = 0; index < metadata.size(); index++) {
            Matrix4f current = index < pose.nodeMatrices().length
                    ? pose.nodeMatrices()[index]
                    : metadata.get(index).bindPoseTransform();
            result.add(new SkeletonNodePose(metadata.get(index), current));
        }
        return List.copyOf(result);
    }
    public List<String> meshNames() {
        return meshes.stream().map(SkinnedMesh::name).toList();
    }
    public boolean hasClip(CharacterModelDefinition.AnimationSlot slot) { return bindings.containsKey(slot); }
    public double clipDurationSeconds(CharacterModelDefinition.AnimationSlot slot) {
        ClipBinding binding = bindings.get(slot);
        return binding == null ? 0.0
                : binding.clip().durationSeconds() / Math.max(0.0001, binding.speed());
    }
    public double clipNaturalDurationSeconds(CharacterModelDefinition.AnimationSlot slot) {
        ClipBinding binding = bindings.get(slot);
        return binding == null ? 0.0 : binding.clip().durationSeconds();
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
        Pose pose = pose(slot, elapsedSeconds);
        return new Frame(skinPositions(pose), pose.normalizedProgress());
    }

    public Pose pose(CharacterModelDefinition.AnimationSlot slot, double elapsedSeconds) {
        ClipBinding binding = bindings.get(slot);
        if (binding == null) {
            return bindPose;
        }
        return pose(binding, elapsedSeconds);
    }

    private Frame skin(ClipBinding binding, double elapsedSeconds) {
        Pose pose = pose(binding, elapsedSeconds);
        return new Frame(skinPositions(pose), pose.normalizedProgress());
    }

    private Pose pose(ClipBinding binding, double elapsedSeconds) {
        AnimationClip clip = binding.clip();
        double duration = Math.max(0.0001, clip.durationTicks());
        double ticks = Math.max(0.0, elapsedSeconds) * Math.max(0.0001, clip.ticksPerSecond()) * binding.speed();
        double time = binding.looping() ? ticks % duration : Math.min(duration, ticks);
        PoseWorkspace workspace = poseWorkspaces.get().next();
        Matrix4f[] globals = workspace.globals;
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            Matrix4f local = evaluateLocal(
                    node, clip.channels().get(node.name()), time,
                    index == rootMotionNodeIndex, workspace.locals[index],
                    workspace.translation, workspace.scale, workspace.rotation);
            if (node.parent() < 0) {
                globals[index].set(local);
            } else {
                globals[index].set(globals[node.parent()]).mul(local);
            }
        }
        return poseFromGlobals(globals, time / duration, workspace);
    }

    private Pose poseFromGlobals(Matrix4f[] globals, double normalizedProgress) {
        return poseFromGlobals(globals, normalizedProgress, poseWorkspaces.get().next());
    }

    private Pose poseFromGlobals(
            Matrix4f[] globals,
            double normalizedProgress,
            PoseWorkspace workspace
    ) {
        Matrix4f[] skinMatrices = workspace.skinMatrices;
        for (int index = 0; index < bones.size(); index++) {
            Bone bone = bones.get(index);
            skinMatrices[index].set(inverseRoot)
                    .mul(globals[bone.nodeIndex()])
                    .mul(bone.inverseBind());
        }
        Matrix4f[] rootedNodes = workspace.rootedNodes;
        for (int index = 0; index < globals.length; index++) {
            rootedNodes[index].set(inverseRoot).mul(globals[index]);
        }
        return new Pose(skinMatrices, rootedNodes, normalizedProgress);
    }

    private List<float[]> skinPositions(Pose pose) {
        Matrix4f[] skinMatrices = pose.boneMatrices();
        Matrix4f[] rootedNodes = pose.nodeMatrices();
        SkinWorkspace workspace = skinWorkspaces.get().next();
        for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            SkinnedMesh mesh = meshes.get(meshIndex);
            float[] output = workspace.positions[meshIndex];
            Matrix4f nodeMatrix = mesh.nodeIndex() >= 0 && mesh.nodeIndex() < rootedNodes.length
                    ? rootedNodes[mesh.nodeIndex()]
                    : mesh.nodeTransform();
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                int p = vertex * 3;
                float sourceX = mesh.bindPositions()[p];
                float sourceY = mesh.bindPositions()[p + 1];
                float sourceZ = mesh.bindPositions()[p + 2];
                float transformedX = 0f;
                float transformedY = 0f;
                float transformedZ = 0f;
                float total = 0f;
                for (int influence = 0; influence < MAX_BONES_PER_VERTEX; influence++) {
                    int offset = vertex * MAX_BONES_PER_VERTEX + influence;
                    float weight = mesh.boneWeights()[offset];
                    int boneIndex = mesh.boneIndices()[offset];
                    if (weight <= 0f || boneIndex < 0 || boneIndex >= skinMatrices.length) continue;
                    Matrix4f matrix = skinMatrices[boneIndex];
                    transformedX += (matrix.m00() * sourceX + matrix.m10() * sourceY
                            + matrix.m20() * sourceZ + matrix.m30()) * weight;
                    transformedY += (matrix.m01() * sourceX + matrix.m11() * sourceY
                            + matrix.m21() * sourceZ + matrix.m31()) * weight;
                    transformedZ += (matrix.m02() * sourceX + matrix.m12() * sourceY
                            + matrix.m22() * sourceZ + matrix.m32()) * weight;
                    total += weight;
                }
                if (total <= 0f) {
                    transformedX = nodeMatrix.m00() * sourceX + nodeMatrix.m10() * sourceY
                            + nodeMatrix.m20() * sourceZ + nodeMatrix.m30();
                    transformedY = nodeMatrix.m01() * sourceX + nodeMatrix.m11() * sourceY
                            + nodeMatrix.m21() * sourceZ + nodeMatrix.m31();
                    transformedZ = nodeMatrix.m02() * sourceX + nodeMatrix.m12() * sourceY
                            + nodeMatrix.m22() * sourceZ + nodeMatrix.m32();
                }
                output[p] = transformedX;
                output[p + 1] = transformedY;
                output[p + 2] = transformedZ;
            }
        }
        return workspace.view;
    }

    private static List<float[]> copyPositions(List<float[]> source) {
        return source.stream().map(float[]::clone).toList();
    }

    private static Pose copyPose(Pose source) {
        Matrix4f[] bones = new Matrix4f[source.boneMatrices().length];
        Matrix4f[] nodes = new Matrix4f[source.nodeMatrices().length];
        for (int index = 0; index < bones.length; index++) {
            bones[index] = new Matrix4f(source.boneMatrices()[index]);
        }
        for (int index = 0; index < nodes.length; index++) {
            nodes[index] = new Matrix4f(source.nodeMatrices()[index]);
        }
        return new Pose(bones, nodes, source.normalizedProgress());
    }

    private static final class PoseWorkspaceRing {
        private static final int RING_SIZE = 8;
        private final PoseWorkspace[] workspaces = new PoseWorkspace[RING_SIZE];
        private int cursor;

        private PoseWorkspaceRing(int nodeCount, int boneCount) {
            for (int index = 0; index < workspaces.length; index++) {
                workspaces[index] = new PoseWorkspace(nodeCount, boneCount);
            }
        }

        private PoseWorkspace next() {
            PoseWorkspace result = workspaces[cursor];
            cursor = (cursor + 1) % workspaces.length;
            return result;
        }
    }

    private static final class PoseWorkspace {
        private final Matrix4f[] globals;
        private final Matrix4f[] locals;
        private final Matrix4f[] skinMatrices;
        private final Matrix4f[] rootedNodes;
        private final Vector3f translation = new Vector3f();
        private final Vector3f scale = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();

        private PoseWorkspace(int nodeCount, int boneCount) {
            globals = matrices(nodeCount);
            locals = matrices(nodeCount);
            skinMatrices = matrices(boneCount);
            rootedNodes = matrices(nodeCount);
        }

        private static Matrix4f[] matrices(int count) {
            Matrix4f[] result = new Matrix4f[Math.max(0, count)];
            for (int index = 0; index < result.length; index++) {
                result[index] = new Matrix4f();
            }
            return result;
        }
    }

    private static final class SkinWorkspaceRing {
        private static final int RING_SIZE = 4;
        private final SkinWorkspace[] workspaces = new SkinWorkspace[RING_SIZE];
        private int cursor;

        private SkinWorkspaceRing(List<SkinnedMesh> meshes) {
            for (int index = 0; index < workspaces.length; index++) {
                workspaces[index] = new SkinWorkspace(meshes);
            }
        }

        private SkinWorkspace next() {
            SkinWorkspace value = workspaces[cursor];
            cursor = (cursor + 1) % workspaces.length;
            return value;
        }
    }

    private static final class SkinWorkspace {
        private final float[][] positions;
        private final List<float[]> view;

        private SkinWorkspace(List<SkinnedMesh> meshes) {
            positions = new float[meshes.size()][];
            for (int index = 0; index < meshes.size(); index++) {
                positions[index] = new float[meshes.get(index).bindPositions().length];
            }
            view = Collections.unmodifiableList(Arrays.asList(positions));
        }
    }

    public static Frame blendFrames(Frame from, Frame to, double amount) {
        if (to == null) {
            return from;
        }
        if (from == null) {
            return to;
        }
        float blend = (float) smoothStep(amount);
        for (int mesh = 0; mesh < to.meshPositions().size(); mesh++) {
            float[] target = to.meshPositions().get(mesh);
            float[] source = mesh < from.meshPositions().size()
                    ? from.meshPositions().get(mesh)
                    : target;
            float[] output = target;
            for (int index = 0; index < output.length && index < source.length; index++) {
                output[index] = source[index] + (target[index] - source[index]) * blend;
            }
        }
        return new Frame(to.meshPositions(), to.normalizedProgress());
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static ModelBounds boundsOf(List<float[]> positions) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (float[] meshPositions : positions) {
            if (meshPositions == null) {
                continue;
            }
            for (int index = 0; index + 2 < meshPositions.length; index += 3) {
                float x = meshPositions[index];
                float y = meshPositions[index + 1];
                float z = meshPositions[index + 2];
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }

        if (!Float.isFinite(minY)) {
            return new ModelBounds(0f, 0f, 0f, 0f, 1f, 0f);
        }
        return new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Frame skinNormalized(CharacterModelDefinition.AnimationSlot slot, double normalizedProgress) {
        ClipBinding binding = bindings.get(slot);
        if (binding == null) return skin(slot, 0.0);
        double progress = Math.max(0.0, Math.min(1.0, normalizedProgress));
        return skin(binding, progress * binding.clip().durationSeconds() / Math.max(0.0001, binding.speed()));
    }

    public Pose poseNormalized(CharacterModelDefinition.AnimationSlot slot, double normalizedProgress) {
        ClipBinding binding = bindings.get(slot);
        if (binding == null) {
            return pose(slot, 0.0);
        }
        double progress = Math.max(0.0, Math.min(1.0, normalizedProgress));
        return pose(binding,
                progress * binding.clip().durationSeconds() / Math.max(0.0001, binding.speed()));
    }

    /** Returns an animated node/socket transform in model space. */
    public Matrix4f nodeTransformNormalized(
            CharacterModelDefinition.AnimationSlot slot,
            double normalizedProgress,
            String nodeName
    ) {
        Integer nodeIndex = nodeName == null ? null : baseNodeIndex(nodeName);
        if (nodeIndex == null) return null;
        Pose pose = poseNormalized(slot, normalizedProgress);
        return nodeIndex < pose.nodeMatrices().length
                ? new Matrix4f(pose.nodeMatrices()[nodeIndex])
                : null;
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

    private static Matrix4f evaluateLocal(
            Node node,
            NodeChannel channel,
            double time,
            boolean stripRootMotion,
            Matrix4f destination,
            Vector3f translation,
            Vector3f scale,
            Quaternionf rotation
    ) {
        if (channel == null) {
            return destination.set(node.bindLocal());
        }
        translation.set(node.bindLocal().m30(), node.bindLocal().m31(), node.bindLocal().m32());
        interpolateVector(channel.positions(), time, translation, translation);
        if (stripRootMotion) {
            translation.x = node.bindLocal().m30();
            translation.z = node.bindLocal().m32();
        }
        node.bindLocal().getScale(scale);
        interpolateVector(channel.scales(), time, scale, scale);
        node.bindLocal().getUnnormalizedRotation(rotation).normalize();
        interpolateRotation(channel.rotations(), time, rotation, rotation);
        return destination.translationRotateScale(translation, rotation, scale);
    }

    private static int findRootMotionNode(List<Node> nodes, List<Bone> bones) {
        if (nodes.isEmpty() || bones.isEmpty()) return 0;
        int index = Math.max(0, bones.get(0).nodeIndex());
        while (index > 0 && nodes.get(index).parent() > 0) index = nodes.get(index).parent();
        return index;
    }

    private static Vector3f interpolateVector(
            List<VectorKey> keys,
            double time,
            Vector3f fallback,
            Vector3f destination
    ) {
        if (keys == null || keys.isEmpty()) return destination.set(fallback);
        if (keys.size() == 1 || time <= keys.get(0).time()) return destination.set(keys.get(0).value());
        int upper = upperVectorKey(keys, time);
        if (upper < keys.size()) {
            VectorKey a = keys.get(upper - 1), b = keys.get(upper);
            float t = (float) ((time - a.time()) / Math.max(0.0001, b.time() - a.time()));
            return destination.set(a.value()).lerp(b.value(), t);
        }
        return destination.set(keys.get(keys.size() - 1).value());
    }

    private static Quaternionf interpolateRotation(
            List<RotationKey> keys,
            double time,
            Quaternionf fallback,
            Quaternionf destination
    ) {
        if (keys == null || keys.isEmpty()) return destination.set(fallback);
        if (keys.size() == 1 || time <= keys.get(0).time()) return destination.set(keys.get(0).value());
        int upper = upperRotationKey(keys, time);
        if (upper < keys.size()) {
            RotationKey a = keys.get(upper - 1), b = keys.get(upper);
            float t = (float) ((time - a.time()) / Math.max(0.0001, b.time() - a.time()));
            return destination.set(a.value()).slerp(b.value(), t).normalize();
        }
        return destination.set(keys.get(keys.size() - 1).value());
    }

    private static int upperVectorKey(List<VectorKey> keys, double time) {
        int low = 1;
        int high = keys.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keys.get(middle).time() < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperRotationKey(List<RotationKey> keys, double time) {
        int low = 1;
        int high = keys.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keys.get(middle).time() < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
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

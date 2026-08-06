package org.main.experimental;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LwjglWorldBatchBuilder {
    List<RenderBatch> build(List<LwjglDungeonSceneBuilder.TexturedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return List.of();
        }
        Map<MaterialKey, BatchAccumulator> accumulators = new HashMap<>();
        for (LwjglDungeonSceneBuilder.TexturedQuad quad : quads) {
            MaterialKey key = new MaterialKey(
                    quad.texture(), materialFlags(quad.kind()), quad.animatedTexture());
            accumulators.computeIfAbsent(key, BatchAccumulator::new).add(quad);
        }
        List<RenderBatch> batches = new ArrayList<>();
        for (BatchAccumulator accumulator : accumulators.values()) {
            batches.add(accumulator.toBatch());
        }
        return batches;
    }

    private static int materialFlags(LwjglDungeonSceneBuilder.QuadKind kind) {
        return kind == LwjglDungeonSceneBuilder.QuadKind.SPRITE ? 1 : 0;
    }

    private static final class BatchAccumulator {
        private final MaterialKey material;
        private final FloatArray vertices = new FloatArray(256);
        private final IntArray indices = new IntArray(96);
        private int vertexCount;

        private BatchAccumulator(MaterialKey material) {
            this.material = material;
        }

        private void add(LwjglDungeonSceneBuilder.TexturedQuad quad) {
            int start = vertexCount;
            Normal normal = normal(quad);
            addVertex(quad.topLeft(), normal, material.flags());
            addVertex(quad.topRight(), normal, material.flags());
            addVertex(quad.bottomRight(), normal, material.flags());
            addVertex(quad.bottomLeft(), normal, material.flags());
            indices.add(start);
            indices.add(start + 1);
            indices.add(start + 2);
            indices.add(start + 2);
            indices.add(start + 3);
            indices.add(start);
        }

        private void addVertex(LwjglDungeonSceneBuilder.Vertex vertex, Normal normal, int flags) {
            vertices.add((float) vertex.x());
            vertices.add((float) vertex.y());
            vertices.add((float) vertex.z());
            vertices.add((float) vertex.u());
            vertices.add((float) vertex.v());
            vertices.add(normal.x());
            vertices.add(normal.y());
            vertices.add(normal.z());
            vertices.add(1.0f);
            vertices.add(1.0f);
            vertices.add(1.0f);
            vertices.add(1.0f);
            vertices.add((float) flags);
            vertexCount++;
        }

        private RenderBatch toBatch() {
            float[] vertexArray = vertices.toArray();
            int[] indexArray = indices.toArray();
            return new RenderBatch(material, vertexArray, indexArray, vertexCount, indexArray.length);
        }

        private static Normal normal(LwjglDungeonSceneBuilder.TexturedQuad quad) {
            LwjglDungeonSceneBuilder.Vertex a = quad.topLeft();
            LwjglDungeonSceneBuilder.Vertex b = quad.topRight();
            LwjglDungeonSceneBuilder.Vertex c = quad.bottomRight();
            double ux = b.x() - a.x();
            double uy = b.y() - a.y();
            double uz = b.z() - a.z();
            double vx = c.x() - a.x();
            double vy = c.y() - a.y();
            double vz = c.z() - a.z();
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 0.0001) {
                return new Normal(0.0f, 1.0f, 0.0f);
            }
            return new Normal((float) (nx / length), (float) (ny / length), (float) (nz / length));
        }
    }

    private record Normal(float x, float y, float z) {
    }

    private static final class FloatArray {
        private float[] values;
        private int size;

        private FloatArray(int initialCapacity) {
            values = new float[Math.max(1, initialCapacity)];
        }

        private void add(float value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        private float[] toArray() {
            return java.util.Arrays.copyOf(values, size);
        }

        private void ensureCapacity(int required) {
            if (required <= values.length) {
                return;
            }
            values = java.util.Arrays.copyOf(values,
                    Math.max(required, values.length + (values.length >> 1)));
        }
    }

    private static final class IntArray {
        private int[] values;
        private int size;

        private IntArray(int initialCapacity) {
            values = new int[Math.max(1, initialCapacity)];
        }

        private void add(int value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        private int[] toArray() {
            return java.util.Arrays.copyOf(values, size);
        }

        private void ensureCapacity(int required) {
            if (required <= values.length) {
                return;
            }
            values = java.util.Arrays.copyOf(values,
                    Math.max(required, values.length + (values.length >> 1)));
        }
    }
}

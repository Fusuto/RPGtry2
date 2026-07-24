package org.main.experimental;

record RenderBatch(
        MaterialKey material,
        float[] vertices,
        int[] indices,
        int vertexCount,
        int indexCount
) {
}

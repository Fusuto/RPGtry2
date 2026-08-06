package org.main.experimental;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;

/** Immutable bind-pose mesh consumed by the GPU skinning shader. */
final class GpuSkinnedMesh {
    private final int vao = glGenVertexArrays();
    private final int positionBuffer = glGenBuffers();
    private final int uvBuffer = glGenBuffers();
    private final int boneBuffer = glGenBuffers();
    private final int weightBuffer = glGenBuffers();
    private final int indexBuffer = glGenBuffers();
    private final int indexCount;

    GpuSkinnedMesh(LwjglSkinnedModel.SkinnedMesh mesh) {
        indexCount = mesh.indices().length;
        glBindVertexArray(vao);
        uploadFloats(positionBuffer, mesh.bindPositions());
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L);
        uploadFloats(uvBuffer, mesh.texCoords());
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0L);
        uploadInts(boneBuffer, mesh.boneIndices(), GL_ARRAY_BUFFER);
        glEnableVertexAttribArray(2);
        glVertexAttribIPointer(2, 4, GL_UNSIGNED_INT, 0, 0L);
        uploadFloats(weightBuffer, mesh.boneWeights());
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, 0, 0L);
        uploadInts(indexBuffer, mesh.indices(), GL_ELEMENT_ARRAY_BUFFER);
        glBindVertexArray(0);
    }

    void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    int indexCount() {
        return indexCount;
    }

    void shutdown() {
        glDeleteBuffers(positionBuffer);
        glDeleteBuffers(uvBuffer);
        glDeleteBuffers(boneBuffer);
        glDeleteBuffers(weightBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteVertexArrays(vao);
    }

    private static void uploadFloats(int buffer, float[] values) {
        FloatBuffer data = BufferUtils.createFloatBuffer(values.length).put(values).flip();
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
    }

    private static void uploadInts(int buffer, int[] values, int target) {
        IntBuffer data = BufferUtils.createIntBuffer(values.length);
        if (target == GL_ARRAY_BUFFER) {
            for (int value : values) {
                data.put(Math.max(0, value));
            }
        } else {
            data.put(values);
        }
        data.flip();
        glBindBuffer(target, buffer);
        glBufferData(target, data, GL_STATIC_DRAW);
    }
}

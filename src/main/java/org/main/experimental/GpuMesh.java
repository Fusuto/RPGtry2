package org.main.experimental;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

final class GpuMesh {
    static final int FLOATS_PER_VERTEX = 13;
    private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private int indexCount;

    GpuMesh() {
        vao = glGenVertexArrays();
        vertexBuffer = glGenBuffers();
        indexBuffer = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        enableAttribute(0, 3, 0);
        enableAttribute(1, 2, 3);
        enableAttribute(2, 3, 5);
        enableAttribute(3, 4, 8);
        enableAttribute(4, 1, 12);
        glBindVertexArray(0);
    }

    void update(float[] vertices, int[] indices, int indexCount) {
        this.indexCount = indexCount;
        FloatBuffer vertexData = BufferUtils.createFloatBuffer(vertices.length);
        vertexData.put(vertices).flip();
        IntBuffer indexData = BufferUtils.createIntBuffer(indices.length);
        indexData.put(indices).flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertexData, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_DYNAMIC_DRAW);
        glBindVertexArray(0);
    }

    void draw() {
        if (indexCount <= 0) {
            return;
        }
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    void shutdown() {
        glDeleteBuffers(vertexBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteVertexArrays(vao);
    }

    private static void enableAttribute(int index, int size, int floatOffset) {
        glEnableVertexAttribArray(index);
        glVertexAttribPointer(index, size, GL_FLOAT, false, STRIDE_BYTES, (long) floatOffset * Float.BYTES);
    }
}

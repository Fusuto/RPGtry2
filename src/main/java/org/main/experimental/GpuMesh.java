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
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
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
    private final int usage;
    private FloatBuffer vertexStaging;
    private IntBuffer indexStaging;
    private int vertexCapacityBytes;
    private int indexCapacityBytes;
    private int uploadedIndexCount = -1;
    private long uploadedBytes;
    private int indexCount;

    GpuMesh() {
        this(false);
    }

    GpuMesh(boolean immutable) {
        usage = immutable ? GL_STATIC_DRAW : GL_DYNAMIC_DRAW;
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
        vertexStaging = ensureFloatCapacity(vertexStaging, vertices.length);
        vertexStaging.clear();
        vertexStaging.put(vertices).flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        int requiredVertexBytes = vertices.length * Float.BYTES;
        if (requiredVertexBytes > vertexCapacityBytes || usage == GL_STATIC_DRAW) {
            glBufferData(GL_ARRAY_BUFFER, vertexStaging, usage);
            vertexCapacityBytes = requiredVertexBytes;
        } else {
            glBufferData(GL_ARRAY_BUFFER, vertexCapacityBytes, usage);
            glBufferSubData(GL_ARRAY_BUFFER, 0L, vertexStaging);
        }
        uploadedBytes += requiredVertexBytes;

        if (uploadedIndexCount != indices.length) {
            indexStaging = ensureIntCapacity(indexStaging, indices.length);
            indexStaging.clear();
            indexStaging.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            int requiredIndexBytes = indices.length * Integer.BYTES;
            if (requiredIndexBytes > indexCapacityBytes || usage == GL_STATIC_DRAW) {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexStaging, usage);
                indexCapacityBytes = requiredIndexBytes;
            } else {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexCapacityBytes, usage);
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0L, indexStaging);
            }
            uploadedIndexCount = indices.length;
            uploadedBytes += requiredIndexBytes;
        }
        glBindVertexArray(0);
    }

    long consumeUploadedBytes() {
        long value = uploadedBytes;
        uploadedBytes = 0L;
        return value;
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

    private static FloatBuffer ensureFloatCapacity(FloatBuffer buffer, int required) {
        if (buffer != null && buffer.capacity() >= required) {
            return buffer;
        }
        return BufferUtils.createFloatBuffer(growCapacity(buffer == null ? 0 : buffer.capacity(), required));
    }

    private static IntBuffer ensureIntCapacity(IntBuffer buffer, int required) {
        if (buffer != null && buffer.capacity() >= required) {
            return buffer;
        }
        return BufferUtils.createIntBuffer(growCapacity(buffer == null ? 0 : buffer.capacity(), required));
    }

    private static int growCapacity(int current, int required) {
        int capacity = Math.max(16, current);
        while (capacity < required) {
            capacity = Math.max(required, capacity + (capacity >> 1));
        }
        return capacity;
    }
}

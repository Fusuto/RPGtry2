package org.main.experimental;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL31.glTexBuffer;
import static org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER;

/** Reusable OpenGL 4.1 texture-buffer matrix palette. */
final class GpuBonePalette {
    private final int bufferId = glGenBuffers();
    private final int textureId = glGenTextures();
    private FloatBuffer staging = BufferUtils.createFloatBuffer(16);
    private int capacityBytes;

    GpuBonePalette() {
        glBindBuffer(GL_TEXTURE_BUFFER, bufferId);
        glBufferData(GL_TEXTURE_BUFFER, 16L * Float.BYTES, GL_DYNAMIC_DRAW);
        capacityBytes = 16 * Float.BYTES;
        glBindTexture(GL_TEXTURE_BUFFER, textureId);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, bufferId);
        glBindTexture(GL_TEXTURE_BUFFER, 0);
    }

    void upload(Matrix4f[] matrices) {
        int count = Math.max(1, matrices == null ? 0 : matrices.length);
        int requiredFloats = count * 16;
        if (staging.capacity() < requiredFloats) {
            staging = BufferUtils.createFloatBuffer(grow(staging.capacity(), requiredFloats));
        }
        staging.clear();
        if (matrices == null || matrices.length == 0) {
            putMatrix(staging, new Matrix4f());
        } else {
            for (Matrix4f matrix : matrices) {
                putMatrix(staging, matrix == null ? new Matrix4f() : matrix);
            }
        }
        staging.flip();
        int requiredBytes = staging.remaining() * Float.BYTES;
        glBindBuffer(GL_TEXTURE_BUFFER, bufferId);
        if (requiredBytes > capacityBytes) {
            glBufferData(GL_TEXTURE_BUFFER, staging, GL_DYNAMIC_DRAW);
            capacityBytes = requiredBytes;
        } else {
            glBufferData(GL_TEXTURE_BUFFER, capacityBytes, GL_DYNAMIC_DRAW);
            glBufferSubData(GL_TEXTURE_BUFFER, 0L, staging);
        }
    }

    int textureId() {
        return textureId;
    }

    void shutdown() {
        glDeleteTextures(textureId);
        glDeleteBuffers(bufferId);
    }

    private static void putMatrix(FloatBuffer target, Matrix4f matrix) {
        target.put(matrix.m00()).put(matrix.m01()).put(matrix.m02()).put(matrix.m03());
        target.put(matrix.m10()).put(matrix.m11()).put(matrix.m12()).put(matrix.m13());
        target.put(matrix.m20()).put(matrix.m21()).put(matrix.m22()).put(matrix.m23());
        target.put(matrix.m30()).put(matrix.m31()).put(matrix.m32()).put(matrix.m33());
    }

    private static int grow(int current, int required) {
        int capacity = Math.max(16, current);
        while (capacity < required) {
            capacity = Math.max(required, capacity + (capacity >> 1));
        }
        return capacity;
    }
}

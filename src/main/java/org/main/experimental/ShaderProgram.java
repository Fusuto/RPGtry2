package org.main.experimental;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;

final class ShaderProgram {
    private final int programId;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    ShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource);
        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            glDeleteProgram(programId);
            throw new IllegalStateException("Shader link failed: " + log);
        }
        glDetachShader(programId, vertexShader);
        glDetachShader(programId, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    void bind() {
        glUseProgram(programId);
    }

    void unbind() {
        glUseProgram(0);
    }

    void setUniform(String name, int value) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform1i(location, value);
        }
    }

    void setUniform(String name, float value) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform1f(location, value);
        }
    }

    void setUniform2(String name, float x, float y) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform2f(location, x, y);
        }
    }

    void setUniform3(String name, float x, float y, float z) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform3f(location, x, y, z);
        }
    }

    void setUniformMatrix(String name, Matrix4f value) {
        int location = glGetUniformLocation(programId, name);
        if (location < 0) {
            return;
        }
        matrixBuffer.clear();
        value.get(matrixBuffer);
        glUniformMatrix4fv(location, false, matrixBuffer);
    }

    void shutdown() {
        glDeleteProgram(programId);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }
}

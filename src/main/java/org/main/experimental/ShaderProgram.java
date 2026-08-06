package org.main.experimental;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

final class ShaderProgram {
    private final int programId;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Map<String, Integer> integerValues = new HashMap<>();
    private final Map<String, Float> floatValues = new HashMap<>();
    private final Map<String, float[]> vectorValues = new HashMap<>();
    private final Map<String, Matrix4f> matrixValues = new HashMap<>();

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
        if (Integer.valueOf(value).equals(integerValues.get(name))) {
            return;
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            glUniform1i(location, value);
            integerValues.put(name, value);
        }
    }

    void setUniform(String name, float value) {
        if (Float.valueOf(value).equals(floatValues.get(name))) {
            return;
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            glUniform1f(location, value);
            floatValues.put(name, value);
        }
    }

    void setUniform2(String name, float x, float y) {
        if (sameVector2(name, x, y)) {
            return;
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            glUniform2f(location, x, y);
            rememberVector2(name, x, y);
        }
    }

    void setUniform3(String name, float x, float y, float z) {
        if (sameVector3(name, x, y, z)) {
            return;
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            glUniform3f(location, x, y, z);
            rememberVector3(name, x, y, z);
        }
    }

    void setUniform4(String name, float x, float y, float z, float w) {
        if (sameVector4(name, x, y, z, w)) {
            return;
        }
        int location = uniformLocation(name);
        if (location >= 0) {
            glUniform4f(location, x, y, z, w);
            rememberVector4(name, x, y, z, w);
        }
    }

    void setUniformMatrix(String name, Matrix4f value) {
        Matrix4f previous = matrixValues.get(name);
        if (previous != null && previous.equals(value)) {
            return;
        }
        int location = uniformLocation(name);
        if (location < 0) {
            return;
        }
        matrixBuffer.clear();
        value.get(matrixBuffer);
        glUniformMatrix4fv(location, false, matrixBuffer);
        if (previous == null) {
            matrixValues.put(name, new Matrix4f(value));
        } else {
            previous.set(value);
        }
    }

    void shutdown() {
        uniformLocations.clear();
        integerValues.clear();
        floatValues.clear();
        vectorValues.clear();
        matrixValues.clear();
        glDeleteProgram(programId);
    }

    private boolean sameVector2(String name, float x, float y) {
        float[] previous = vectorValues.get(name);
        return previous != null && previous.length == 2
                && Float.floatToIntBits(previous[0]) == Float.floatToIntBits(x)
                && Float.floatToIntBits(previous[1]) == Float.floatToIntBits(y);
    }

    private boolean sameVector3(String name, float x, float y, float z) {
        float[] previous = vectorValues.get(name);
        return previous != null && previous.length == 3
                && Float.floatToIntBits(previous[0]) == Float.floatToIntBits(x)
                && Float.floatToIntBits(previous[1]) == Float.floatToIntBits(y)
                && Float.floatToIntBits(previous[2]) == Float.floatToIntBits(z);
    }

    private boolean sameVector4(String name, float x, float y, float z, float w) {
        float[] previous = vectorValues.get(name);
        return previous != null && previous.length == 4
                && Float.floatToIntBits(previous[0]) == Float.floatToIntBits(x)
                && Float.floatToIntBits(previous[1]) == Float.floatToIntBits(y)
                && Float.floatToIntBits(previous[2]) == Float.floatToIntBits(z)
                && Float.floatToIntBits(previous[3]) == Float.floatToIntBits(w);
    }

    private void rememberVector2(String name, float x, float y) {
        float[] value = vectorValues.computeIfAbsent(name, ignored -> new float[2]);
        value[0] = x;
        value[1] = y;
    }

    private void rememberVector3(String name, float x, float y, float z) {
        float[] value = vectorValues.computeIfAbsent(name, ignored -> new float[3]);
        value[0] = x;
        value[1] = y;
        value[2] = z;
    }

    private void rememberVector4(String name, float x, float y, float z, float w) {
        float[] value = vectorValues.computeIfAbsent(name, ignored -> new float[4]);
        value[0] = x;
        value[1] = y;
        value[2] = z;
        value[3] = w;
    }

    private int uniformLocation(String name) {
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        int location = glGetUniformLocation(programId, name);
        uniformLocations.put(name, location);
        return location;
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

package org.main.experimental;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScaled;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

/** Cached compatibility-profile primitives used instead of glBegin/glEnd. */
final class FixedFunctionPrimitives {
    private static final float[] BILLBOARD = {
            -0.5f, 0f, 0f, 0.5f, 0f, 0f, 0.5f, 1f, 0f, -0.5f, 1f, 0f
    };
    private static final float[] CENTERED_QUAD = {
            -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f,
            0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f
    };
    private static final float[] SCREEN_QUAD = {
            0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f
    };
    private static final float[] UV_NORMAL = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
    private static final float[] UV_BILLBOARD = {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
    private static final float[] CUBE = {
            -1,-1, 1,  1,-1, 1,  1, 1, 1, -1, 1, 1,
             1,-1,-1, -1,-1,-1, -1, 1,-1,  1, 1,-1,
            -1,-1,-1, -1,-1, 1, -1, 1, 1, -1, 1,-1,
             1,-1, 1,  1,-1,-1,  1, 1,-1,  1, 1, 1,
            -1, 1, 1,  1, 1, 1,  1, 1,-1, -1, 1,-1,
            -1,-1,-1,  1,-1,-1,  1,-1, 1, -1,-1, 1
    };

    private final int billboard = staticBuffer(BILLBOARD);
    private final int centeredQuad = staticBuffer(CENTERED_QUAD);
    private final int screenQuad = staticBuffer(SCREEN_QUAD);
    private final int cube = staticBuffer(CUBE);
    private final int uvNormal = staticBuffer(UV_NORMAL);
    private final int uvBillboard = staticBuffer(UV_BILLBOARD);
    private final int dynamicQuad = glGenBuffers();
    private final FloatBuffer dynamicStaging = BufferUtils.createFloatBuffer(12);

    FixedFunctionPrimitives() {
        glBindBuffer(GL_ARRAY_BUFFER, dynamicQuad);
        glBufferData(GL_ARRAY_BUFFER, 12L * Float.BYTES, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void drawBillboard(double width, double height, boolean textured) {
        glPushMatrix();
        glScaled(width, height, 1.0);
        draw(billboard, 4, textured ? uvBillboard : 0);
        glPopMatrix();
    }

    void drawCenteredQuad(double width, double height, boolean textured) {
        glPushMatrix();
        glScaled(width, height, 1.0);
        draw(centeredQuad, 4, textured ? uvNormal : 0);
        glPopMatrix();
    }

    void drawScreenQuad(double width, double height) {
        glPushMatrix();
        glScaled(width, height, 1.0);
        draw(screenQuad, 4, uvNormal);
        glPopMatrix();
    }

    void drawBox(double halfWidth, double halfHeight, double halfDepth) {
        glPushMatrix();
        glScaled(halfWidth, halfHeight, halfDepth);
        draw(cube, 24, 0);
        glPopMatrix();
    }

    void drawTexturedQuad(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4
    ) {
        dynamicStaging.clear();
        dynamicStaging.put((float) x1).put((float) y1).put((float) z1);
        dynamicStaging.put((float) x2).put((float) y2).put((float) z2);
        dynamicStaging.put((float) x3).put((float) y3).put((float) z3);
        dynamicStaging.put((float) x4).put((float) y4).put((float) z4).flip();
        glBindBuffer(GL_ARRAY_BUFFER, dynamicQuad);
        glBufferData(GL_ARRAY_BUFFER, 12L * Float.BYTES, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, dynamicStaging);
        draw(dynamicQuad, 4, uvNormal);
    }

    void shutdown() {
        glDeleteBuffers(billboard);
        glDeleteBuffers(centeredQuad);
        glDeleteBuffers(screenQuad);
        glDeleteBuffers(cube);
        glDeleteBuffers(uvNormal);
        glDeleteBuffers(uvBillboard);
        glDeleteBuffers(dynamicQuad);
    }

    private static int staticBuffer(float[] data) {
        int id = glGenBuffers();
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length).put(data).flip();
        glBindBuffer(GL_ARRAY_BUFFER, id);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return id;
    }

    private static void draw(int positionBuffer, int vertexCount, int textureBuffer) {
        glBindBuffer(GL_ARRAY_BUFFER, positionBuffer);
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, 0, 0L);
        if (textureBuffer != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, textureBuffer);
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glTexCoordPointer(2, GL_FLOAT, 0, 0L);
        } else {
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        glDrawArrays(GL_QUADS, 0, vertexCount);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

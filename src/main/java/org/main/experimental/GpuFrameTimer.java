package org.main.experimental;

import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL15.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

/** Double-buffered asynchronous GPU frame timer; never stalls for a result. */
final class GpuFrameTimer {
    private final int[] queries = {glGenQueries(), glGenQueries()};
    private final boolean[] pending = new boolean[2];
    private int current;
    private long lastElapsedNanos;

    void begin() {
        int read = (current + 1) & 1;
        if (pending[read]
                && glGetQueryObjecti(queries[read], GL_QUERY_RESULT_AVAILABLE) != 0) {
            lastElapsedNanos = glGetQueryObjectui64(queries[read], GL_QUERY_RESULT);
            pending[read] = false;
        }
        glBeginQuery(GL_TIME_ELAPSED, queries[current]);
    }

    long end() {
        glEndQuery(GL_TIME_ELAPSED);
        pending[current] = true;
        current = (current + 1) & 1;
        return lastElapsedNanos;
    }

    void shutdown() {
        glDeleteQueries(queries[0]);
        glDeleteQueries(queries[1]);
    }
}

package org.main.engine;

public interface RealtimeDungeonViewport {
    void initialize();

    void renderFrame(DungeonRenderContext context);

    void shutdown();

    DungeonRenderDebugInfo getDebugInfo();
}

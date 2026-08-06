package org.main.experimental;

import org.junit.jupiter.api.Test;
import org.main.core.Library;
import org.main.core.GameState;
import org.main.core.PlayerCharacter;
import org.main.core.WorldMessageLog;
import org.main.engine.DungeonMap;
import org.main.engine.DungeonRenderContext;
import org.main.engine.EnvironmentTheme;
import org.main.engine.MapEntity;
import org.main.engine.TextureManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePerformanceInfrastructureTest {
    @Test
    void fixedStepClockRunsSixtyUpdatesAndBoundsCatchUp() {
        long step = 1_000_000_000L / 60L;
        FixedStepAccumulator clock = new FixedStepAccumulator(step, 4);
        List<Integer> deltas = new ArrayList<>();

        for (int index = 0; index < 60; index++) {
            FixedStepAccumulator.Result result = clock.advance(step, deltas::add);
            assertEquals(1, result.steps());
            assertFalse(result.droppedExcessTime());
        }
        assertEquals(60, deltas.size());
        assertEquals(1_000, deltas.stream().mapToInt(Integer::intValue).sum());

        FixedStepAccumulator.Result stalled = clock.advance(step * 10L, deltas::add);
        assertEquals(4, stalled.steps());
        assertTrue(stalled.droppedExcessTime());
        assertTrue(stalled.interpolationAlpha() >= 0.0 && stalled.interpolationAlpha() < 1.0);
    }

    @Test
    void conservativeFrustumKeepsNearAndEdgeObjectsButRejectsDistantOrRearObjects() {
        assertTrue(ConservativeFrustum.includes(0.0, -0.25, 0.0, 70.0, 12.0, 0.5));
        assertTrue(ConservativeFrustum.includes(10.0, -1.0, 0.0, 70.0, 12.0, 1.0));
        assertFalse(ConservativeFrustum.includes(0.0, 10.0, 0.0, 70.0, 12.0, 0.25));
        assertFalse(ConservativeFrustum.includes(20.0, 0.0, 0.0, 70.0, 12.0, 0.5));
    }

    @Test
    void conservativeFrustumUsesTheViewportYawConventionForEveryCardinalDirection() {
        assertTrue(ConservativeFrustum.includes(0.0, -10.0, 0.0, 70.0, 12.0, 0.5),
                "North-facing cameras must keep northern terrain.");
        assertTrue(ConservativeFrustum.includes(10.0, 0.0, -90.0, 70.0, 12.0, 0.5),
                "East-facing cameras must keep eastern terrain.");
        assertTrue(ConservativeFrustum.includes(0.0, 10.0, 180.0, 70.0, 12.0, 0.5),
                "South-facing cameras must keep southern terrain.");
        assertTrue(ConservativeFrustum.includes(-10.0, 0.0, 90.0, 70.0, 12.0, 0.5),
                "West-facing cameras must keep western terrain.");
        assertFalse(ConservativeFrustum.includes(-10.0, 0.0, -90.0, 70.0, 12.0, 0.5),
                "Terrain directly behind an east-facing camera may be rejected.");
    }

    @Test
    void entityRenderInterpolationDoesNotMutateSimulationPosition() {
        MapEntity entity = new MapEntity("walker", Library.EntityType.ENEMY, 2, 2);
        entity.setWorldFacingYawDegrees(90.0);
        assertTrue(entity.beginWorldMovement(3, 2, 1_000, true));
        entity.update(100);

        double simulated = entity.getRenderX();
        double interpolated = entity.getRenderX(0.5);
        assertTrue(interpolated > simulated);
        assertEquals(2, entity.getX());
        assertEquals(2, entity.getY());
    }

    @Test
    void terrainPartitionPreservesEveryAuthoredQuadAcrossEightTileCells() {
        DungeonMap map = floorMap(16, 16);
        PlayerCharacter player = new PlayerCharacter("Profiler", 10, 10);
        DungeonRenderContext context = new DungeonRenderContext(
                map, List.of(), player, 8, 8, 0, 1280, 720, 0.0, 0.0, 0.0);
        LwjglDungeonSceneBuilder builder = new LwjglDungeonSceneBuilder(
                new TextureManager(), List.of(EnvironmentTheme.defaultTheme()));

        LwjglDungeonSceneBuilder.Scene complete = builder.build(
                context, 32, 1.0, 0.45, 0.0);
        var cells = builder.buildTerrainCells(context, 8, 1.0, 0.45);
        int partitionedQuads = cells.values().stream()
                .mapToInt(cell -> cell.scene().quads().size())
                .sum();

        assertTrue(cells.size() >= 4);
        assertEquals(complete.quads().size(), partitionedQuads,
                "Partitioning must move, not regenerate or duplicate, authored geometry.");
    }

    @Test
    void overlaySignatureStaysStableWhenIdleAndTracksLiveMessageAging() {
        GameState state = new GameState(floorMap(4, 4),
                new PlayerCharacter("Profiler", 10, 10));
        long idle = state.uiPresentationSignature();
        state.getWorldMessageLog().advance(1_000);
        assertEquals(idle, state.uiPresentationSignature(),
                "An idle HUD must not be invalidated by simulation time alone.");

        state.getWorldMessageLog().post(WorldMessageLog.Category.SYSTEM, "Live update");
        long posted = state.uiPresentationSignature();
        assertNotEquals(idle, posted);
        state.getWorldMessageLog().advance(50);
        assertNotEquals(posted, state.uiPresentationSignature(),
                "Visible message fading must continue to invalidate its presentation layer.");
    }

    @Test
    void benchmarkProfilerRetainsTransitionMaximumBeyondRollingWindow() {
        FrameProfiler profiler = new FrameProfiler();
        long now = 1_000_000_000L;
        for (int index = 0; index < 650; index++) {
            profiler.beginFrame(now);
            long duration = index == 5 ? 18_000_000L : 1_000_000L;
            now += duration;
            profiler.endFrame(now);
        }

        assertTrue(profiler.snapshot().maximumMs() < 2.0,
                "The short rolling overlay should age out an old transition.");
        assertEquals(18.0, profiler.benchmarkSnapshot().maximumMs(), 0.001,
                "Benchmark metrics must retain the complete post-warmup transition history.");

        profiler.reset();
        assertEquals(0.0, profiler.benchmarkSnapshot().maximumMs());
    }

    private static DungeonMap floorMap(int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];
        for (Library.TileType[] row : tiles) {
            java.util.Arrays.fill(row, Library.TileType.FLOOR);
        }
        return new DungeonMap(tiles);
    }
}

package org.main.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.content.MapDesignLibrary;
import org.main.content.ThemeLibrary;
import org.main.content.WorldManifestLibrary;
import org.main.content.WorldManifestLibrary.ChunkCoordinate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenWorldDirtyChunkTransitionTest {
    @TempDir
    Path temporaryWorld;

    @Test
    void dirtyTileSurvivesLeavingAndReturningToChunk() throws Exception {
        Path chunkFolder = temporaryWorld.resolve("chunks");
        Path west = chunkFolder.resolve("0_0.properties");
        Path east = chunkFolder.resolve("1_0.properties");
        MapDesignLibrary.save(floorChunk(), west);
        MapDesignLibrary.save(floorChunk(), east);

        Map<ChunkCoordinate, String> chunks = new LinkedHashMap<>();
        chunks.put(new ChunkCoordinate(0, 0), "chunks/0_0.properties");
        chunks.put(new ChunkCoordinate(1, 0), "chunks/1_0.properties");
        WorldManifestLibrary.WorldManifest manifest = new WorldManifestLibrary.WorldManifest(
                WorldManifestLibrary.FORMAT_VERSION,
                "transition_test",
                "Transition Test",
                "",
                3,
                3,
                1,
                1,
                chunks);
        Path manifestPath = temporaryWorld.resolve("world.properties");
        WorldManifestLibrary.save(manifest, manifestPath);

        OpenWorldSession session = new OpenWorldSession(manifestPath, manifest);
        OpenWorldSession.WindowState initial = session.openAtGlobal(1, 1);
        initial.map().setTile(initial.playerX(), initial.playerY(), Library.TileType.DOOR_OPEN);

        OpenWorldSession.RecenterResult eastWindow = session.recenterIfNeeded(
                session.windowXForGlobal(4),
                session.windowYForGlobal(1),
                capture(initial));
        assertNotNull(eastWindow);

        OpenWorldSession.RecenterResult returned = session.recenterIfNeeded(
                session.windowXForGlobal(1),
                session.windowYForGlobal(1),
                capture(eastWindow.window()));
        assertNotNull(returned);
        assertEquals(Library.TileType.DOOR_OPEN,
                returned.window().map().getTile(
                        session.windowXForGlobal(1),
                        session.windowYForGlobal(1)));
    }

    private static MapDesignLibrary.MapDesign floorChunk() {
        MapDesignLibrary.MapDesign design = MapDesignLibrary.createBlank(
                3, 3, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        for (Library.TileType[] row : design.tiles()) {
            java.util.Arrays.fill(row, Library.TileType.FLOOR);
        }
        return design;
    }

    private static OpenWorldSession.WindowCapture capture(OpenWorldSession.WindowState state) {
        return new OpenWorldSession.WindowCapture(
                state.map(),
                state.entities(),
                state.tileInteractions(),
                state.resourceNodeStates(),
                state.enemyRespawns(),
                state.discoveredTiles(),
                state.removedEntityKeys(),
                state.triggers(),
                state.firedTriggerIds());
    }
}

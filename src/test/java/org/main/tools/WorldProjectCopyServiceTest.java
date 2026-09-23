package org.main.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.content.MapDesignLibrary;
import org.main.content.WorldManifestLibrary;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProjectCopyServiceTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void bundledOverworldMaterializesEveryChunkAndPlacementAsProjectFiles() throws Exception {
        Path destination = temporaryFolder.resolve("assets/packs/test/editor/worlds/overworld/world.properties");
        WorldManifestLibrary.WorldManifest copied = new WorldProjectCopyService().copy(
                Path.of("assets/editor/worlds/overworld/world.properties"),
                destination);

        assertEquals("overworld", copied.worldId());
        assertEquals(17, copied.chunks().size());
        assertTrue(copied.chunks().values().stream().allMatch(path -> path.startsWith("chunks/")));
        assertTrue(java.nio.file.Files.isRegularFile(destination));

        Path centerPath = WorldManifestLibrary.resolveChunkPath(
                destination,
                copied.chunks().get(new WorldManifestLibrary.ChunkCoordinate(0, 0)));
        MapDesignLibrary.MapDesign center = MapDesignLibrary.loadGeometryOnly(centerPath);
        assertEquals(6, center.placements().size());
        assertEquals(6, center.placedObjects().size());
    }
}

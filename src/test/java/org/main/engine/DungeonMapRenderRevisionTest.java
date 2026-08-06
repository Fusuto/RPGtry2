package org.main.engine;

import org.junit.jupiter.api.Test;
import org.main.core.Library;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonMapRenderRevisionTest {
    @Test
    void tilePaintGeometryAndLightsInvalidateWithoutCopyingLightView() {
        DungeonMap map = floorMap(24, 24);
        List<MapLight> stableLights = map.getLightsView();
        long initial = map.renderRevision();

        map.setTile(8, 8, Library.TileType.WALL);
        long tileRevision = map.renderRevision();
        assertNotEquals(initial, tileRevision);
        assertTrue(map.dirtyRenderCells(8).contains(new DungeonMap.RenderCellCoordinate(1, 1)));
        assertTrue(map.dirtyRenderCells(8).contains(new DungeonMap.RenderCellCoordinate(0, 0)));
        assertTrue(map.dirtyRenderCells(8).contains(new DungeonMap.RenderCellCoordinate(2, 2)));

        map.setTile(8, 8, Library.TileType.WALL);
        assertEquals(tileRevision, map.renderRevision(), "No-op writes must not invalidate GPU caches.");

        map.getPaintData().set(MapPaintData.Layer.FLOOR, 2, 3, "stone");
        long paintRevision = map.renderRevision();
        assertNotEquals(tileRevision, paintRevision);

        map.getGeometryData().setHeightLevel(2, 3, 2);
        long geometryRevision = map.renderRevision();
        assertNotEquals(paintRevision, geometryRevision);

        map.addLight(new MapLight("test", 3, 4, 0xFFFFFF,
                5.0, 1.0, 0.5, 0.0, true));
        assertNotEquals(geometryRevision, map.renderRevision());
        assertSame(stableLights, map.getLightsView());
        assertEquals(1, stableLights.size(), "The stable read-only view must reflect authored changes.");

        map.clearRenderDirtyTiles();
        assertTrue(map.dirtyRenderCells(8).isEmpty());
    }

    private DungeonMap floorMap(int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];
        for (int y = 0; y < height; y++) {
            java.util.Arrays.fill(tiles[y], Library.TileType.FLOOR);
        }
        return new DungeonMap(tiles);
    }
}

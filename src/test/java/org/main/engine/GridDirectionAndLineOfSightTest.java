package org.main.engine;

import org.junit.jupiter.api.Test;
import org.main.core.Library;

import static org.junit.jupiter.api.Assertions.*;

class GridDirectionAndLineOfSightTest {
    @Test
    void cardinalMappingWrapsAndKeepsRightHandedAxes() {
        assertEquals(GridDirection.NORTH, GridDirection.fromIndex(4));
        assertEquals(GridDirection.WEST, GridDirection.fromIndex(-1));
        assertEquals(1, GridDirection.forwardX(1));
        assertEquals(-1, GridDirection.forwardY(0));
        assertEquals(1, GridDirection.rightX(0));
        assertEquals(-1, GridDirection.rightY(3));
    }

    @Test
    void sharedRayAllowsOpaqueTargetButBlocksOpaqueIntermediateTile() {
        Library.TileType[][] tiles = {
                {Library.TileType.FLOOR, Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.WALL, Library.TileType.WALL},
                {Library.TileType.FLOOR, Library.TileType.FLOOR, Library.TileType.FLOOR}
        };
        DungeonMap map = new DungeonMap(tiles);
        assertFalse(LineOfSight.between(map, 0, 1, 2, 1,
                LineOfSight.Blocker.WALL_LIKE, false));
        assertTrue(LineOfSight.between(map, 0, 0, 1, 1,
                LineOfSight.Blocker.WALL_LIKE, false));
        assertFalse(LineOfSight.between(map, 0, 0, 1, 1,
                LineOfSight.Blocker.WALL_LIKE, true));
    }
}

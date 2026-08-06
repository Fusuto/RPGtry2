package org.main.core;

import org.junit.jupiter.api.Test;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldCreaturePathCacheTest {
    @Test
    void occupancyRevisionInvalidatesCachedPursuitPath() {
        DungeonMap map = floorMap(5, 3);
        MapEntity enemy = new MapEntity("Pursuer", Library.EntityType.ENEMY, 0, 1);
        MapEntity blocker = new MapEntity("Blocker", Library.EntityType.NPC, 1, 1);
        WorldCreatureSystem creatures = new WorldCreatureSystem();

        Point detour = creatures.nextPathStepForTesting(
                map, enemy, "", 4, 1, List.of(enemy, blocker));
        assertNotEquals(new Point(1, 1), detour,
                "The occupancy index must keep a cached path out of a reserved tile.");

        Point direct = creatures.nextPathStepForTesting(
                map, enemy, "", 4, 1, List.of(enemy));
        assertEquals(new Point(1, 1), direct,
                "Changing occupancy must invalidate the detour and rebuild the bounded A* path.");
    }

    private static DungeonMap floorMap(int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];
        for (Library.TileType[] row : tiles) {
            java.util.Arrays.fill(row, Library.TileType.FLOOR);
        }
        return new DungeonMap(tiles);
    }
}

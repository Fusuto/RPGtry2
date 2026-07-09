package org.main.core;

import org.main.content.NpcLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonGenerator {
    private static final int MIN_SIZE = 17;
    private static final int MAX_SIZE = 29;
    private static final double MERCHANT_CHANCE = 0.10;
    private static final double ROOM_CHANCE = 0.06;
    private static final double MEDIUM_ROOM_CHANCE = 0.20;
    private static final double DOOR_CHANCE = 0.22;
    private static final double MONO_TYPE_CHANCE = 0.30;
    private static final String DEEPER_DOOR_INTERACTION_ID = "generated_dungeon_gate";

    private final Random random;

    public DungeonGenerator() {
        this(new Random());
    }

    public DungeonGenerator(Random random) {
        this.random = random == null ? new Random() : random;
    }

    public GeneratedDungeon generate() {
        int width = randomOddSize();
        int height = randomOddSize();
        Library.TileType[][] tiles = createFilledMap(width, height);
        int[][] themeIndexes = new int[height][width];

        Point start = new Point(1, 1);
        carveCorridors(tiles, start);
        placeDoors(tiles);
        Point deeperDoor = placeDeeperDoor(tiles, start);
        assignThemeIndexes(tiles, themeIndexes);

        List<MapEntity> entities = new ArrayList<>();
        addMonsters(tiles, entities, chooseDungeonMonsterType());
        maybeAddMerchant(tiles, entities);

        List<GeneratedDungeon.TileInteraction> tileInteractions = deeperDoor == null
                ? List.of()
                : List.of(new GeneratedDungeon.TileInteraction(
                        deeperDoor.x(),
                        deeperDoor.y(),
                        DEEPER_DOOR_INTERACTION_ID
                ));

        return new GeneratedDungeon(
                new DungeonMap(tiles, themeIndexes),
                entities,
                start.x(),
                start.y(),
                tileInteractions
        );
    }

    private int randomOddSize() {
        int size = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
        return size % 2 == 0 ? size + 1 : size;
    }

    private Library.TileType[][] createFilledMap(int width, int height) {
        Library.TileType[][] tiles = new Library.TileType[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles[y][x] = Library.TileType.WALL;
            }
        }

        return tiles;
    }

    private void carveCorridors(Library.TileType[][] tiles, Point start) {
        List<Point> carvedCells = new ArrayList<>();
        carveFloor(tiles, start.x(), start.y(), carvedCells);

        int width = tiles[0].length;
        int height = tiles.length;
        int targetCells = (width * height) / 5;
        Point current = start;

        while (carvedCells.size() < targetCells) {
            Direction direction = Direction.random(random);
            int nextX = current.x() + direction.dx * 2;
            int nextY = current.y() + direction.dy * 2;

            if (!isInterior(tiles, nextX, nextY)) {
                if (!carvedCells.isEmpty()) {
                    current = carvedCells.get(random.nextInt(carvedCells.size()));
                }
                continue;
            }

            carveFloor(tiles, current.x() + direction.dx, current.y() + direction.dy, carvedCells);
            carveFloor(tiles, nextX, nextY, carvedCells);
            current = new Point(nextX, nextY);

            if (random.nextDouble() < ROOM_CHANCE) {
                carveRoom(tiles, current, carvedCells);
            }
        }
    }

    private void carveRoom(Library.TileType[][] tiles, Point center, List<Point> carvedCells) {
        int halfWidth = random.nextDouble() < MEDIUM_ROOM_CHANCE ? 2 : 1;
        int halfHeight = random.nextDouble() < MEDIUM_ROOM_CHANCE ? 2 : 1;

        for (int y = center.y() - halfHeight; y <= center.y() + halfHeight; y++) {
            for (int x = center.x() - halfWidth; x <= center.x() + halfWidth; x++) {
                if (isInterior(tiles, x, y)) {
                    carveFloor(tiles, x, y, carvedCells);
                }
            }
        }
    }

    private void carveFloor(Library.TileType[][] tiles, int x, int y, List<Point> carvedCells) {
        if (tiles[y][x] == Library.TileType.FLOOR) {
            return;
        }

        tiles[y][x] = Library.TileType.FLOOR;
        carvedCells.add(new Point(x, y));
    }

    private void placeDoors(Library.TileType[][] tiles) {
        for (int y = 1; y < tiles.length - 1; y++) {
            for (int x = 1; x < tiles[0].length - 1; x++) {
                if (tiles[y][x] != Library.TileType.FLOOR || random.nextDouble() >= DOOR_CHANCE) {
                    continue;
                }

                boolean eastWestCorridor = isFloor(tiles, x - 1, y)
                        && isFloor(tiles, x + 1, y)
                        && isWall(tiles, x, y - 1)
                        && isWall(tiles, x, y + 1);
                boolean northSouthCorridor = isFloor(tiles, x, y - 1)
                        && isFloor(tiles, x, y + 1)
                        && isWall(tiles, x - 1, y)
                        && isWall(tiles, x + 1, y);

                if (eastWestCorridor || northSouthCorridor) {
                    tiles[y][x] = Library.TileType.DOOR_CLOSED;
                }
            }
        }
    }

    private Point placeDeeperDoor(Library.TileType[][] tiles, Point start) {
        Point deeperDoor = findFarthestFloorTile(tiles, start);

        if (deeperDoor == null) {
            return null;
        }

        tiles[deeperDoor.y()][deeperDoor.x()] = Library.TileType.DOOR_CLOSED;
        return deeperDoor;
    }

    private Point findFarthestFloorTile(Library.TileType[][] tiles, Point start) {
        Point best = null;
        int bestDistance = -1;

        for (int y = 1; y < tiles.length - 1; y++) {
            for (int x = 1; x < tiles[0].length - 1; x++) {
                if (tiles[y][x] != Library.TileType.FLOOR || (x == start.x() && y == start.y())) {
                    continue;
                }

                int distance = Math.abs(x - start.x()) + Math.abs(y - start.y());

                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = new Point(x, y);
                }
            }
        }

        return best;
    }

    private void assignThemeIndexes(Library.TileType[][] tiles, int[][] themeIndexes) {
        for (int y = 0; y < tiles.length; y++) {
            for (int x = 0; x < tiles[y].length; x++) {
                themeIndexes[y][x] = random.nextDouble() < 0.16 ? 1 : 0;
            }
        }
    }

    private void addMonsters(Library.TileType[][] tiles, List<MapEntity> entities, MonsterType dungeonMonsterType) {
        int openTiles = countOpenTiles(tiles);
        int monsterCount = Math.max(3, Math.min(9, openTiles / 18));

        for (int i = 0; i < monsterCount; i++) {
            Point candidate = findOpenTile(tiles, entities);

            if (candidate == null) {
                return;
            }

            MonsterType monsterType = dungeonMonsterType == null ? randomMonsterType() : dungeonMonsterType;
            entities.add(new MapEntity(new Monster(monsterType), candidate.x(), candidate.y()));
        }
    }

    private MonsterType chooseDungeonMonsterType() {
        return random.nextDouble() < MONO_TYPE_CHANCE ? randomMonsterType() : null;
    }

    private MonsterType randomMonsterType() {
        MonsterType[] monsterTypes = MonsterType.values();
        return monsterTypes[random.nextInt(monsterTypes.length)];
    }

    private void maybeAddMerchant(Library.TileType[][] tiles, List<MapEntity> entities) {
        if (random.nextDouble() >= MERCHANT_CHANCE) {
            return;
        }

        Point candidate = findOpenTile(tiles, entities);

        if (candidate != null) {
            entities.add(NpcLibrary.GOBLIN_MERCHANT.createEntity(candidate.x(), candidate.y()));
        }
    }

    private Point findOpenTile(Library.TileType[][] tiles, List<MapEntity> entities) {
        for (int attempts = 0; attempts < 240; attempts++) {
            int x = random.nextInt(tiles[0].length - 2) + 1;
            int y = random.nextInt(tiles.length - 2) + 1;

            if ((x == 1 && y == 1)
                    || tiles[y][x] != Library.TileType.FLOOR
                    || isEntityAt(entities, x, y)) {
                continue;
            }

            return new Point(x, y);
        }

        return null;
    }

    private int countOpenTiles(Library.TileType[][] tiles) {
        int count = 0;

        for (Library.TileType[] row : tiles) {
            for (Library.TileType tile : row) {
                if (tile == Library.TileType.FLOOR) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean isEntityAt(List<MapEntity> entities, int x, int y) {
        for (MapEntity entity : entities) {
            if (entity.isAt(x, y)) {
                return true;
            }
        }

        return false;
    }

    private boolean isInterior(Library.TileType[][] tiles, int x, int y) {
        return y > 0 && y < tiles.length - 1 && x > 0 && x < tiles[0].length - 1;
    }

    private boolean isFloor(Library.TileType[][] tiles, int x, int y) {
        return isInterior(tiles, x, y) && tiles[y][x] == Library.TileType.FLOOR;
    }

    private boolean isWall(Library.TileType[][] tiles, int x, int y) {
        return !isInterior(tiles, x, y) || tiles[y][x] == Library.TileType.WALL;
    }

    private enum Direction {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        private static Direction random(Random random) {
            Direction[] values = values();
            return values[random.nextInt(values.length)];
        }
    }

    private record Point(int x, int y) {
    }
}

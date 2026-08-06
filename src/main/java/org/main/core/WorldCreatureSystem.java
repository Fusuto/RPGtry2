package org.main.core;

import org.main.battle.DifficultyResolver;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.TerrainGeometry;
import org.main.experimental.CharacterAnimationMetadataResolver;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class WorldCreatureSystem {
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int FALLBACK_MOVE_DURATION_MS = 280;
    private final PathWorkspace pathWorkspace = new PathWorkspace();
    private final Map<MapEntity, CachedPath> pathCache = new WeakHashMap<>();
    private OccupancyIndex occupancyIndex;
    private int decisionCursor;

    public void update(GameState gameState, int deltaMs, Consumer<MapEntity> engageEnemy) {
        if (gameState == null || !gameState.isDungeonMode() || gameState.isBattleMode()) {
            return;
        }
        DungeonMap map = gameState.getDungeonMap();
        if (map == null) {
            return;
        }
        if (occupancyIndex == null || !occupancyIndex.compatible(map)) {
            occupancyIndex = new OccupancyIndex(map);
            pathCache.clear();
        }
        occupancyIndex.rebuild(gameState.getEntities());
        OccupancyIndex occupancy = occupancyIndex;
        List<MapEntity> entities = gameState.getEntities();
        for (MapEntity enemy : entities) {
            if (enemy.getType() != Library.EntityType.ENEMY
                    || enemy.getMonster() == null
                    || enemy.getRoamingAreaId().isBlank()) {
                continue;
            }
            enemy.advanceWorldAiCooldown(deltaMs);
        }
        int maximumDecisions = Math.max(1, GameConfiguration.intValue(
                "movement.ai.maxDecisionsPerStep", 4));
        int decisions = 0;
        int entityCount = entities.size();
        int start = entityCount == 0 ? 0 : Math.floorMod(decisionCursor, entityCount);
        int visited = 0;
        for (; visited < entityCount && decisions < maximumDecisions; visited++) {
            int index = (start + visited) % entityCount;
            MapEntity enemy = entities.get(index);
            if (enemy.getType() != Library.EntityType.ENEMY
                    || enemy.getMonster() == null
                    || enemy.getRoamingAreaId().isBlank()
                    || enemy.isWorldMotionActive()
                    || enemy.getWorldAiCooldownMs() > 0) {
                continue;
            }
            enemy.resetWorldAiCooldown();
            updateEnemy(gameState, map, enemy, engageEnemy, occupancy);
            decisions++;
            if (gameState.isBattleMode()) {
                return;
            }
        }
        if (entityCount > 0) {
            decisionCursor = (start + Math.max(1, visited)) % entityCount;
        }
    }

    private void updateEnemy(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            Consumer<MapEntity> engage,
            OccupancyIndex occupancy
    ) {
        String area = enemy.getRoamingAreaId();
        int px = state.getPlayerX();
        int py = state.getPlayerY();
        if (!area.equals(map.getMobAreaId(px, py))) {
            enemy.setWorldAlerted(false);
            wander(state, map, enemy, area, occupancy);
            return;
        }
        if (!enemy.isWorldAlerted()) {
            int distance = Math.max(Math.abs(enemy.getX() - px), Math.abs(enemy.getY() - py));
            if (enemy.getAwarenessRadius() == 0
                    || distance > enemy.getAwarenessRadius()
                    || !hasLineOfSight(map, enemy.getX(), enemy.getY(), px, py)) {
                wander(state, map, enemy, area, occupancy);
                return;
            }
            enemy.setWorldAlerted(true);
        }

        DifficultyResolver.DifficultyBand band = DifficultyResolver.compare(
                DifficultyResolver.ratePlayer(state.getPlayerCharacter()),
                DifficultyResolver.rateMonster(enemy.getMonster())
        ).band();
        boolean cautious = enemy.getMonster().getCombatAiIntelligence() >= 5;
        if (cautious && (band == DifficultyResolver.DifficultyBand.TRIVIAL
                || band == DifficultyResolver.DifficultyBand.EASY)) {
            flee(state, map, enemy, area, occupancy);
        } else {
            pursue(state, map, enemy, area, engage, occupancy);
        }
    }

    private void wander(GameState state, DungeonMap map, MapEntity enemy, String area, OccupancyIndex occupancy) {
        List<Point> choices = legalNeighbors(state, map, enemy, area, occupancy);
        choices.add(new Point(enemy.getX(), enemy.getY()));
        Collections.shuffle(choices);
        Point choice = choices.get(0);
        beginMovement(enemy, choice, occupancy);
    }

    private void flee(GameState state, DungeonMap map, MapEntity enemy, String area, OccupancyIndex occupancy) {
        Point best = new Point(enemy.getX(), enemy.getY());
        int bestDistance = chebyshev(best.x, best.y, state.getPlayerX(), state.getPlayerY());
        for (Point choice : legalNeighbors(state, map, enemy, area, occupancy)) {
            int distance = chebyshev(choice.x, choice.y, state.getPlayerX(), state.getPlayerY());
            if (distance > bestDistance) {
                best = choice;
                bestDistance = distance;
            }
        }
        beginMovement(enemy, best, occupancy);
    }

    private void pursue(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            String area,
            Consumer<MapEntity> engage,
            OccupancyIndex occupancy
    ) {
        if (manhattan(enemy.getX(), enemy.getY(), state.getPlayerX(), state.getPlayerY()) == 1) {
            engage.accept(enemy);
            return;
        }
        Point next = nextPathStep(state, map, enemy, area,
                state.getPlayerX(), state.getPlayerY(), occupancy);
        if (next != null) {
            beginMovement(enemy, next, occupancy);
        }
    }

    private Point nextPathStep(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            String area,
            int targetX,
            int targetY,
            OccupancyIndex occupancy
    ) {
        CachedPath cached = pathCache.get(enemy);
        if (cached != null && cached.validFor(
                map, enemy, area, targetX, targetY, occupancy)) {
            Point next = cached.next(enemy.getX(), enemy.getY());
            if (next != null) {
                return next;
            }
        }
        int[] path = pathWorkspace.findPath(
                map, enemy, area, targetX, targetY, occupancy,
                Math.max(64, GameConfiguration.intValue("movement.path.maxVisitedTiles", 2048)));
        if (path == null || path.length < 2) {
            pathCache.remove(enemy);
            return null;
        }
        CachedPath replacement = new CachedPath(
                map, area, targetX, targetY,
                map.tileRevision(), map.geometryRevision(), occupancy.revision(), path);
        pathCache.put(enemy, replacement);
        return replacement.next(enemy.getX(), enemy.getY());
    }

    private List<Point> legalNeighbors(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            String area,
            OccupancyIndex occupancy
    ) {
        List<Point> result = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            int x = enemy.getX() + direction[0];
            int y = enemy.getY() + direction[1];
            if (TerrainGeometry.canTraverse(map, enemy.getX(), enemy.getY(), x, y)
                    && area.equals(map.getMobAreaId(x, y))
                    && !(x == state.getPlayerX() && y == state.getPlayerY())
                    && !occupancy.occupied(x, y, enemy)) {
                result.add(new Point(x, y));
            }
        }
        return result;
    }

    private void beginMovement(MapEntity enemy, Point destination, OccupancyIndex occupancy) {
        if (enemy == null
                || destination == null
                || (destination.x == enemy.getX() && destination.y == enemy.getY())) {
            return;
        }

        boolean hasWalkAnimation = false;
        int durationMs = FALLBACK_MOVE_DURATION_MS;
        if (enemy.getCharacterModel() != null && enemy.getCharacterModel().hasModel()) {
            try {
                CharacterAnimationMetadataResolver.SlotMetadata walk =
                        CharacterAnimationMetadataResolver.resolve(enemy.getCharacterModel())
                                .slot(org.main.content.CharacterModelDefinition.AnimationSlot.WALK);
                hasWalkAnimation = walk.available();
                if (hasWalkAnimation) {
                    durationMs = Math.max(1, (int) Math.round(
                            walk.durationSeconds() * 1000.0
                    ));
                }
            } catch (Exception ignored) {
                hasWalkAnimation = false;
                durationMs = FALLBACK_MOVE_DURATION_MS;
            }
        }
        boolean began = enemy.beginWorldMovement(
                destination.x,
                destination.y,
                durationMs,
                hasWalkAnimation
        );
        if (began) {
            occupancy.reserve(destination.x, destination.y, enemy);
        }
    }

    private boolean hasLineOfSight(DungeonMap map, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (x != x1 || y != y1) {
            int previousX = x;
            int previousY = y;
            int twice = error * 2;
            if (twice > -dy) {
                error -= dy;
                x += sx;
            }
            if (twice < dx) {
                error += dx;
                y += sy;
            }
            if (TerrainGeometry.edgeKind(map, previousX, previousY, x, y) == org.main.engine.TerrainEdgeKind.CLIFF) {
                return false;
            }
            if ((x != x1 || y != y1) && map.getTile(x, y).blocksMovement()) {
                return false;
            }
        }
        return true;
    }

    private int chebyshev(int x0, int y0, int x1, int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    private int manhattan(int x0, int y0, int x1, int y1) {
        return Math.abs(x1 - x0) + Math.abs(y1 - y0);
    }

    Point nextPathStepForTesting(
            DungeonMap map,
            MapEntity enemy,
            String area,
            int targetX,
            int targetY,
            List<MapEntity> entities
    ) {
        if (occupancyIndex == null || !occupancyIndex.compatible(map)) {
            occupancyIndex = new OccupancyIndex(map);
            pathCache.clear();
        }
        occupancyIndex.rebuild(entities == null ? List.of() : entities);
        return nextPathStep(null, map, enemy, area == null ? "" : area,
                targetX, targetY, occupancyIndex);
    }

    private static final class OccupancyIndex {
        private final int width;
        private final int height;
        private final MapEntity[] occupants;
        private long revision;
        private long signature = 1L;

        private OccupancyIndex(DungeonMap map) {
            width = map.getWidth();
            height = map.getHeight();
            occupants = new MapEntity[Math.max(1, width * height)];
        }

        private boolean compatible(DungeonMap map) {
            return map != null && width == map.getWidth() && height == map.getHeight();
        }

        private void rebuild(List<MapEntity> entities) {
            long previousSignature = signature;
            java.util.Arrays.fill(occupants, null);
            for (MapEntity entity : entities) {
                if (entity == null || !entity.blocksMovement()) {
                    continue;
                }
                reserve(entity.getX(), entity.getY(), entity);
                if (entity.isWorldMotionActive()) {
                    reserve(entity.getPersistenceX(), entity.getPersistenceY(), entity);
                }
            }
            signature = occupancySignature();
            if (previousSignature != signature) {
                revision++;
            }
        }

        private boolean occupied(int x, int y, MapEntity ignored) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return true;
            }
            MapEntity occupant = occupants[y * width + x];
            return occupant != null && occupant != ignored;
        }

        private void reserve(int x, int y, MapEntity entity) {
            if (x >= 0 && y >= 0 && x < width && y < height) {
                occupants[y * width + x] = entity;
            }
        }

        private long revision() {
            return revision;
        }

        private long occupancySignature() {
            long result = 1L;
            for (int index = 0; index < occupants.length; index++) {
                MapEntity entity = occupants[index];
                if (entity != null) {
                    result = 31L * result + index;
                    result = 31L * result + System.identityHashCode(entity);
                }
            }
            return result;
        }
    }

    private static final class PathWorkspace {
        private int[] heap = new int[0];
        private int[] heapPositions = new int[0];
        private int[] pathCosts = new int[0];
        private int[] previous = new int[0];
        private int[] visited = new int[0];
        private int visitStamp = 1;
        private int heapSize;
        private int targetX;
        private int targetY;
        private int width;

        private int[] findPath(
                DungeonMap map,
                MapEntity enemy,
                String area,
                int targetX,
                int targetY,
                OccupancyIndex occupancy,
                int maximumVisited
        ) {
            width = map.getWidth();
            int height = map.getHeight();
            int tileCount = Math.max(1, width * height);
            ensureCapacity(tileCount);
            if (++visitStamp == Integer.MAX_VALUE) {
                java.util.Arrays.fill(visited, 0);
                visitStamp = 1;
            }
            int start = enemy.getY() * width + enemy.getX();
            int target = targetY * width + targetX;
            this.targetX = targetX;
            this.targetY = targetY;
            heapSize = 0;
            visited[start] = visitStamp;
            previous[start] = -1;
            pathCosts[start] = 0;
            addToHeap(start);
            int examined = 0;
            while (heapSize > 0 && examined++ < maximumVisited) {
                int current = removeHeapMinimum();
                if (current == target) {
                    int length = 1;
                    int step = current;
                    while (previous[step] >= 0) {
                        length++;
                        step = previous[step];
                    }
                    int[] path = new int[length];
                    step = current;
                    for (int index = length - 1; index >= 0; index--) {
                        path[index] = step;
                        step = previous[step];
                    }
                    return path;
                }
                int currentX = current % width;
                int currentY = current / width;
                for (int[] direction : DIRECTIONS) {
                    int nextX = currentX + direction[0];
                    int nextY = currentY + direction[1];
                    if (nextX < 0 || nextY < 0 || nextX >= width || nextY >= height) {
                        continue;
                    }
                    int next = nextY * width + nextX;
                    boolean playerTile = next == target;
                    if (!TerrainGeometry.canTraverse(map, currentX, currentY, nextX, nextY)
                            || !area.equals(map.getMobAreaId(nextX, nextY))
                            || (!playerTile && occupancy.occupied(nextX, nextY, enemy))) {
                        continue;
                    }
                    int candidateCost = pathCosts[current] + 1;
                    if (visited[next] != visitStamp) {
                        visited[next] = visitStamp;
                        previous[next] = current;
                        pathCosts[next] = candidateCost;
                        addToHeap(next);
                    } else if (heapPositions[next] >= 0 && candidateCost < pathCosts[next]) {
                        previous[next] = current;
                        pathCosts[next] = candidateCost;
                        bubbleUp(heapPositions[next]);
                    }
                }
            }
            return null;
        }

        private void ensureCapacity(int required) {
            if (heap.length >= required) {
                return;
            }
            heap = new int[required];
            heapPositions = new int[required];
            java.util.Arrays.fill(heapPositions, -1);
            pathCosts = new int[required];
            previous = new int[required];
            visited = new int[required];
        }

        private void addToHeap(int tile) {
            int index = heapSize++;
            heap[index] = tile;
            heapPositions[tile] = index;
            bubbleUp(index);
        }

        private int removeHeapMinimum() {
            int result = heap[0];
            heapPositions[result] = -1;
            heapSize--;
            if (heapSize > 0) {
                int replacement = heap[heapSize];
                heap[0] = replacement;
                heapPositions[replacement] = 0;
                bubbleDown(0);
            }
            return result;
        }

        private void bubbleUp(int index) {
            int cursor = index;
            while (cursor > 0) {
                int parent = (cursor - 1) >>> 1;
                if (!less(heap[cursor], heap[parent])) {
                    break;
                }
                swapHeap(cursor, parent);
                cursor = parent;
            }
        }

        private void bubbleDown(int index) {
            int cursor = index;
            while (true) {
                int left = cursor * 2 + 1;
                if (left >= heapSize) {
                    return;
                }
                int right = left + 1;
                int smallest = right < heapSize && less(heap[right], heap[left]) ? right : left;
                if (!less(heap[smallest], heap[cursor])) {
                    return;
                }
                swapHeap(cursor, smallest);
                cursor = smallest;
            }
        }

        private boolean less(int leftTile, int rightTile) {
            int leftScore = pathCosts[leftTile] + heuristic(leftTile);
            int rightScore = pathCosts[rightTile] + heuristic(rightTile);
            return leftScore < rightScore
                    || (leftScore == rightScore && pathCosts[leftTile] > pathCosts[rightTile]);
        }

        private int heuristic(int tile) {
            return Math.abs(tile % width - targetX) + Math.abs(tile / width - targetY);
        }

        private void swapHeap(int left, int right) {
            int leftTile = heap[left];
            int rightTile = heap[right];
            heap[left] = rightTile;
            heap[right] = leftTile;
            heapPositions[leftTile] = right;
            heapPositions[rightTile] = left;
        }
    }

    private static final class CachedPath {
        private final DungeonMap map;
        private final String area;
        private final int targetX;
        private final int targetY;
        private final long tileRevision;
        private final long geometryRevision;
        private final long occupancyRevision;
        private final int[] path;
        private int cursor;

        private CachedPath(
                DungeonMap map,
                String area,
                int targetX,
                int targetY,
                long tileRevision,
                long geometryRevision,
                long occupancyRevision,
                int[] path
        ) {
            this.map = map;
            this.area = area;
            this.targetX = targetX;
            this.targetY = targetY;
            this.tileRevision = tileRevision;
            this.geometryRevision = geometryRevision;
            this.occupancyRevision = occupancyRevision;
            this.path = path;
        }

        private boolean validFor(
                DungeonMap map,
                MapEntity enemy,
                String area,
                int targetX,
                int targetY,
                OccupancyIndex occupancy
        ) {
            if (this.map != map
                    || this.targetX != targetX
                    || this.targetY != targetY
                    || !this.area.equals(area)
                    || tileRevision != map.tileRevision()
                    || geometryRevision != map.geometryRevision()
                    || occupancyRevision != occupancy.revision()) {
                return false;
            }
            Point next = peekNext(enemy.getX(), enemy.getY());
            return next != null
                    && TerrainGeometry.canTraverse(map, enemy.getX(), enemy.getY(), next.x, next.y)
                    && !occupancy.occupied(next.x, next.y, enemy);
        }

        private Point next(int currentX, int currentY) {
            Point result = peekNext(currentX, currentY);
            if (result != null) {
                cursor++;
            }
            return result;
        }

        private Point peekNext(int currentX, int currentY) {
            int width = map.getWidth();
            while (cursor < path.length
                    && (path[cursor] % width != currentX || path[cursor] / width != currentY)) {
                cursor++;
            }
            if (cursor < 0 || cursor + 1 >= path.length) {
                return null;
            }
            int next = path[cursor + 1];
            return new Point(next % width, next / width);
        }
    }
}

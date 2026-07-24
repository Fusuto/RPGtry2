package org.main.core;

import org.main.battle.DifficultyResolver;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.TerrainGeometry;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class WorldCreatureSystem {
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    public void update(GameState gameState, int deltaMs, Consumer<MapEntity> engageEnemy) {
        if (gameState == null || !gameState.isDungeonMode() || gameState.isBattleMode()) {
            return;
        }
        DungeonMap map = gameState.getDungeonMap();
        if (map == null) {
            return;
        }
        for (MapEntity enemy : new ArrayList<>(gameState.getEntities())) {
            if (enemy.getType() != Library.EntityType.ENEMY
                    || enemy.getMonster() == null
                    || enemy.getRoamingAreaId().isBlank()
                    || !enemy.advanceWorldAiCooldown(deltaMs)) {
                continue;
            }
            updateEnemy(gameState, map, enemy, engageEnemy);
            if (gameState.isBattleMode()) {
                return;
            }
        }
    }

    private void updateEnemy(GameState state, DungeonMap map, MapEntity enemy, Consumer<MapEntity> engage) {
        String area = enemy.getRoamingAreaId();
        int px = state.getPlayerX();
        int py = state.getPlayerY();
        if (!area.equals(map.getMobAreaId(px, py))) {
            enemy.setWorldAlerted(false);
            wander(state, map, enemy, area);
            return;
        }
        if (!enemy.isWorldAlerted()) {
            int distance = Math.max(Math.abs(enemy.getX() - px), Math.abs(enemy.getY() - py));
            if (enemy.getAwarenessRadius() == 0
                    || distance > enemy.getAwarenessRadius()
                    || !hasLineOfSight(map, enemy.getX(), enemy.getY(), px, py)) {
                wander(state, map, enemy, area);
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
            flee(state, map, enemy, area);
        } else {
            pursue(state, map, enemy, area, engage);
        }
    }

    private void wander(GameState state, DungeonMap map, MapEntity enemy, String area) {
        List<Point> choices = legalNeighbors(state, map, enemy, area);
        choices.add(new Point(enemy.getX(), enemy.getY()));
        Collections.shuffle(choices);
        Point choice = choices.get(0);
        enemy.setPosition(choice.x, choice.y);
    }

    private void flee(GameState state, DungeonMap map, MapEntity enemy, String area) {
        Point best = new Point(enemy.getX(), enemy.getY());
        int bestDistance = chebyshev(best.x, best.y, state.getPlayerX(), state.getPlayerY());
        for (Point choice : legalNeighbors(state, map, enemy, area)) {
            int distance = chebyshev(choice.x, choice.y, state.getPlayerX(), state.getPlayerY());
            if (distance > bestDistance) {
                best = choice;
                bestDistance = distance;
            }
        }
        enemy.setPosition(best.x, best.y);
    }

    private void pursue(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            String area,
            Consumer<MapEntity> engage
    ) {
        if (manhattan(enemy.getX(), enemy.getY(), state.getPlayerX(), state.getPlayerY()) == 1) {
            engage.accept(enemy);
            return;
        }
        Point next = nextPathStep(state, map, enemy, area, state.getPlayerX(), state.getPlayerY());
        if (next != null) {
            enemy.setPosition(next.x, next.y);
        }
    }

    private Point nextPathStep(
            GameState state,
            DungeonMap map,
            MapEntity enemy,
            String area,
            int targetX,
            int targetY
    ) {
        Point start = new Point(enemy.getX(), enemy.getY());
        Point target = new Point(targetX, targetY);
        ArrayDeque<Point> queue = new ArrayDeque<>();
        Map<Point, Point> previous = new HashMap<>();
        Set<Point> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (current.equals(target)) {
                Point step = current;
                while (previous.containsKey(step) && !previous.get(step).equals(start)) {
                    step = previous.get(step);
                }
                return step;
            }
            for (int[] direction : DIRECTIONS) {
                Point next = new Point(current.x + direction[0], current.y + direction[1]);
                boolean playerTile = next.equals(target);
                if (visited.contains(next)
                        || !TerrainGeometry.canTraverse(map, current.x, current.y, next.x, next.y)
                        || !area.equals(map.getMobAreaId(next.x, next.y))
                        || (!playerTile && occupied(state, next.x, next.y, enemy))) {
                    continue;
                }
                visited.add(next);
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        return null;
    }

    private List<Point> legalNeighbors(GameState state, DungeonMap map, MapEntity enemy, String area) {
        List<Point> result = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            int x = enemy.getX() + direction[0];
            int y = enemy.getY() + direction[1];
            if (TerrainGeometry.canTraverse(map, enemy.getX(), enemy.getY(), x, y)
                    && area.equals(map.getMobAreaId(x, y))
                    && !(x == state.getPlayerX() && y == state.getPlayerY())
                    && !occupied(state, x, y, enemy)) {
                result.add(new Point(x, y));
            }
        }
        return result;
    }

    private boolean occupied(GameState state, int x, int y, MapEntity ignored) {
        for (MapEntity entity : state.getEntities()) {
            if (entity != ignored && entity.blocksMovement() && entity.isAt(x, y)) {
                return true;
            }
        }
        return false;
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
}

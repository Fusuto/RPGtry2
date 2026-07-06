package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.MovementEngine;
import org.main.monsters.Monster;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class DungeonController {
    private final GameState gameState;
    private final MovementEngine movementEngine;

    public DungeonController(GameState gameState, MovementEngine movementEngine) {
        this.gameState = gameState;
        this.movementEngine = movementEngine;
    }

    public void handleInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_NUMPAD8 -> moveForward();
            case KeyEvent.VK_A, KeyEvent.VK_NUMPAD4 -> strafeLeft();
            case KeyEvent.VK_D, KeyEvent.VK_NUMPAD6 -> strafeRight();
            case KeyEvent.VK_S, KeyEvent.VK_NUMPAD2 -> moveBackward();
            case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> turnLeft();
            case KeyEvent.VK_E, KeyEvent.VK_NUMPAD9 -> turnRight();
            case KeyEvent.VK_F, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> interact();
        }
    }

    public void moveForward() {
        move(forwardX(), forwardY());
    }

    public void moveBackward() {
        move(-forwardX(), -forwardY());
    }

    public void strafeLeft() {
        move(leftX(), leftY());
    }

    public void strafeRight() {
        move(rightX(), rightY());
    }

    public void turnLeft() {
        gameState.setDirection(
                movementEngine.turnLeft(gameState.getDirection())
        );
    }

    public void turnRight() {
        gameState.setDirection(
                movementEngine.turnRight(gameState.getDirection())
        );
    }

    public void interact() {
        int targetX = gameState.getPlayerX() + forwardX();
        int targetY = gameState.getPlayerY() + forwardY();

        MapEntity targetEntity = getEntityAt(targetX, targetY);

        /*
         * Enemies are intentionally not activated with the interact button.
         * Combat starts by bumping into them through movement.
         */
        if (targetEntity != null) {
            if (targetEntity.getType() == Library.EntityType.ENEMY) {
                System.out.println("Move into " + targetEntity.getName() + " to engage.");
                return;
            }

            interactWithEntity(targetEntity);
            return;
        }

        DungeonMap dungeonMap = gameState.getDungeonMap();
        Library.TileType targetTile = dungeonMap.getTile(targetX, targetY);

        if (targetTile == Library.TileType.DOOR_CLOSED) {
            dungeonMap.setTile(targetX, targetY, Library.TileType.DOOR_OPEN);
            return;
        }

        if (targetTile == Library.TileType.DOOR_OPEN) {
            if (isOccupied(targetX, targetY)) {
                System.out.println("Something is blocking the door.");
                return;
            }

            dungeonMap.setTile(targetX, targetY, Library.TileType.DOOR_CLOSED);
        }
    }

    private void move(int dx, int dy) {
        Point nextPosition = movementEngine.move(
                gameState.getPlayerX(),
                gameState.getPlayerY(),
                dx,
                dy,
                gameState.getDungeonMap()
        );

        if (nextPosition.x == gameState.getPlayerX()
                && nextPosition.y == gameState.getPlayerY()) {
            return;
        }

        MapEntity blockingEntity = getBlockingEntityAt(nextPosition.x, nextPosition.y);

        if (blockingEntity != null) {
            if (blockingEntity.getType() == Library.EntityType.ENEMY) {
                startBattle(blockingEntity);
            }

            return;
        }

        gameState.setPlayerPosition(nextPosition.x, nextPosition.y);
    }

    private void startBattle(MapEntity enemyEntity) {
        if (gameState.isBattleMode()) {
            return;
        }

        if (enemyEntity.getMonster() == null) {
            System.out.println("Enemy entity has no monster data.");
            return;
        }

        gameState.setCurrentEnemyEntity(enemyEntity);

        List<Monster> monsters = new ArrayList<>();
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());

        gameState.setCurrentEncounter(BattleEncounter.fromMonster(monsters));
        gameState.setGameMode(GameState.GameMode.BATTLE);
    }

    private void interactWithEntity(MapEntity entity) {
        switch (entity.getType()) {
            case ITEM -> {
                if (entity.getItem() == null) {
                    System.out.println("There is no item here.");
                    return;
                }

                boolean added = gameState.getInventory().addItem(entity.getItem());

                if (!added) {
                    System.out.println("Inventory full.");
                    return;
                }

                gameState.removeEntity(entity);
                System.out.println("Picked up " + entity.getName() + ".");
            }
            case NPC -> System.out.println("You talk to " + entity.getName() + ".");
            case ALLY -> System.out.println("You speak with " + entity.getName() + ".");
            case CHEST -> System.out.println("You inspect " + entity.getName() + ".");
            case TRAP -> System.out.println("You examine " + entity.getName() + ".");
            default -> System.out.println("You interact with " + entity.getName() + ".");
        }
    }

    private boolean isPlayerAt(int x, int y) {
        return gameState.getPlayerX() == x
                && gameState.getPlayerY() == y;
    }

    private MapEntity getEntityAt(int x, int y) {
        for (MapEntity entity : gameState.getEntities()) {
            if (entity.isAt(x, y)) {
                return entity;
            }
        }

        return null;
    }

    private MapEntity getBlockingEntityAt(int x, int y) {
        for (MapEntity entity : gameState.getEntities()) {
            if (entity.isAt(x, y) && entity.blocksMovement()) {
                return entity;
            }
        }

        return null;
    }

    private boolean isOccupied(int x, int y) {
        return isPlayerAt(x, y) || getBlockingEntityAt(x, y) != null;
    }

    private int forwardX() {
        return movementEngine.forwardX(gameState.getDirection());
    }

    private int forwardY() {
        return movementEngine.forwardY(gameState.getDirection());
    }

    private int leftX() {
        return movementEngine.leftX(gameState.getDirection());
    }

    private int leftY() {
        return movementEngine.leftY(gameState.getDirection());
    }

    private int rightX() {
        return movementEngine.rightX(gameState.getDirection());
    }

    private int rightY() {
        return movementEngine.rightY(gameState.getDirection());
    }
}
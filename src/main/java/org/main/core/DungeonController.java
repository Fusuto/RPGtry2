package org.main.core;

import org.main.battle.BattleEncounter;
import org.main.content.EnvironmentLibrary;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.MovementEngine;
import org.main.engine.SoundSystem;
import org.main.monsters.Monster;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class DungeonController {
    private static final String DEFAULT_PICKUP_SOUND_PATH = "assets/sounds/generated/pickup_sound.wav";

    private final GameState gameState;
    private final MovementEngine movementEngine;
    private final InteractionSystem.InteractionRegistry interactionRegistry;
    private final SoundSystem soundSystem;
    private final EnvironmentLibrary environment;

    public DungeonController(
            GameState gameState,
            MovementEngine movementEngine,
            InteractionSystem.InteractionRegistry interactionRegistry
    ) {
        this(gameState, movementEngine, interactionRegistry, null, null);
    }

    public DungeonController(
            GameState gameState,
            MovementEngine movementEngine,
            InteractionSystem.InteractionRegistry interactionRegistry,
            SoundSystem soundSystem,
            EnvironmentLibrary environment
    ) {
        this.gameState = gameState;
        this.movementEngine = movementEngine;
        this.interactionRegistry = interactionRegistry;
        this.soundSystem = soundSystem;
        this.environment = environment;
    }

    public void handleInput(KeyEvent e) {
        if (gameState.isCameraAnimating()) {
            return;
        }

        InputBindings bindings = gameState.getInputBindings();
        int keyCode = e.getKeyCode();

        if (bindings.matches(InputBindings.Action.MOVE_FORWARD, keyCode) || keyCode == KeyEvent.VK_NUMPAD8) {
            moveForward();
        } else if (bindings.matches(InputBindings.Action.STRAFE_LEFT, keyCode) || keyCode == KeyEvent.VK_NUMPAD4) {
            strafeLeft();
        } else if (bindings.matches(InputBindings.Action.STRAFE_RIGHT, keyCode) || keyCode == KeyEvent.VK_NUMPAD6) {
            strafeRight();
        } else if (bindings.matches(InputBindings.Action.MOVE_BACKWARD, keyCode) || keyCode == KeyEvent.VK_NUMPAD2) {
            moveBackward();
        } else if (bindings.matches(InputBindings.Action.TURN_LEFT, keyCode) || keyCode == KeyEvent.VK_NUMPAD7) {
            turnLeft();
        } else if (bindings.matches(InputBindings.Action.TURN_RIGHT, keyCode) || keyCode == KeyEvent.VK_NUMPAD9) {
            turnRight();
        } else if (bindings.matches(InputBindings.Action.INTERACT, keyCode)
                || keyCode == KeyEvent.VK_ENTER
                || keyCode == KeyEvent.VK_SPACE) {
            interact();
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
        int previousDirection = gameState.getDirection();
        int nextDirection = movementEngine.turnLeft(previousDirection);
        gameState.setDirection(nextDirection);
        gameState.startRotationAnimation(previousDirection, nextDirection);
    }

    public void turnRight() {
        int previousDirection = gameState.getDirection();
        int nextDirection = movementEngine.turnRight(previousDirection);
        gameState.setDirection(nextDirection);
        gameState.startRotationAnimation(previousDirection, nextDirection);
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

            playTalkSound(targetEntity);
            interactWithEntity(targetEntity);
            return;
        }

        DungeonMap dungeonMap = gameState.getDungeonMap();
        Library.TileType targetTile = dungeonMap.getTile(targetX, targetY);

        if (tryOpenRegisteredTileInteraction(targetX, targetY)) {
            return;
        }

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
        int previousX = gameState.getPlayerX();
        int previousY = gameState.getPlayerY();
        Point nextPosition = movementEngine.move(
                previousX,
                previousY,
                dx,
                dy,
                gameState.getDungeonMap()
        );

        if (nextPosition.x == previousX
                && nextPosition.y == previousY) {
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
        gameState.startMovementAnimation(previousX, previousY, nextPosition.x, nextPosition.y);
        sealQuestDoorBehindPlayer(previousX, previousY);
        playFootstepSound();
    }

    private void sealQuestDoorBehindPlayer(int previousX, int previousY) {
        DungeonMap dungeonMap = gameState.getDungeonMap();
        if (dungeonMap == null || dungeonMap.getTile(previousX, previousY) != Library.TileType.QUEST_DOOR_OPEN) {
            return;
        }

        dungeonMap.setTile(previousX, previousY, Library.TileType.QUEST_DOOR_CLOSED);
    }

    private void playFootstepSound() {
        if (soundSystem == null || environment == null) {
            return;
        }

        soundSystem.playSound(environment.getFootstepSoundPath());
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

        gameState.setCurrentEncounter(BattleEncounter.fromMonster(
                monsters,
                gameState.getPlayerCharacter(),
                soundSystem,
                environment
        ));
        gameState.setGameMode(GameState.GameMode.BATTLE);

        if (soundSystem != null && environment != null) {
            soundSystem.stopAmbience();
            soundSystem.playMusic(environment.getCombatMusicPath());
        }
    }

    private void interactWithEntity(MapEntity entity) {
        if (tryOpenRegisteredInteraction(entity)) {
            return;
        }

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
                playPickupSound();
                System.out.println("Picked up " + entity.getName() + ".");
            }
            case NPC -> System.out.println("You talk to " + entity.getName() + ".");
            case ALLY -> System.out.println("You speak with " + entity.getName() + ".");
            case CHEST -> System.out.println("You inspect " + entity.getName() + ".");
            case TRAP -> System.out.println("You examine " + entity.getName() + ".");
            default -> System.out.println("You interact with " + entity.getName() + ".");
        }
    }

    private void playPickupSound() {
        if (soundSystem != null) {
            soundSystem.playSound(DEFAULT_PICKUP_SOUND_PATH);
        }
    }

    private void playTalkSound(MapEntity entity) {
        if (soundSystem == null || entity == null) {
            return;
        }

        if (entity.getType() != Library.EntityType.NPC
                && entity.getType() != Library.EntityType.ALLY) {
            return;
        }

        soundSystem.playSound(entity.getTalkSoundPath());
    }

    private boolean tryOpenRegisteredInteraction(MapEntity entity) {
        if (entity == null || !entity.hasInteractionId()) {
            return false;
        }

        if (interactionRegistry == null) {
            return false;
        }

        InteractionSystem.Interaction interaction = interactionRegistry.create(
                entity.getInteractionId(),
                gameState,
                entity,
                entity.getX(),
                entity.getY()
        );

        if (interaction == null) {
            return false;
        }

        gameState.openInteraction(interaction.withSelectionSoundPath(entity.getTalkSoundPath()));
        return true;
    }

    private boolean tryOpenRegisteredTileInteraction(int x, int y) {
        if (!gameState.hasTileInteractionId(x, y)) {
            return false;
        }

        if (interactionRegistry == null) {
            return false;
        }

        InteractionSystem.Interaction interaction = interactionRegistry.create(
                gameState.getTileInteractionId(x, y),
                gameState,
                null,
                x,
                y
        );

        if (interaction == null) {
            return false;
        }

        gameState.openInteraction(interaction);
        return true;
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

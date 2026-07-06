package org.main;

import org.main.battle.*;
import org.main.engine.*;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import org.main.battle.BattleAssets;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class WizardryBase extends JPanel implements KeyListener {
    private static final int TILE_EMPTY = 0;
    private static final int TILE_WALL = 1;

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MovementEngine movementEngine = new MovementEngine();
    private final BattleAssets battleAssets = BattleAssets.loadDefault();
    private final DungeonMap dungeonMap = DungeonMap.testMap();
    private BattleSkill pendingSkill = null;

    private int playerX = 1;
    private int playerY = 1;

    private boolean inBattle = false;
    private BattleCommand pendingBattleCommand = null;

    // 0 = north, 1 = east, 2 = south, 3 = west
    private int dir = 1;

    private final List<MapEntity> entities = new ArrayList<MapEntity>();
    private final TextureManager textureManager = new TextureManager();
    private long lastUpdateTime = System.currentTimeMillis();

    /// Combat logic
    private enum GameMode {
        DUNGEON,
        BATTLE
    }

    private GameMode gameMode = GameMode.DUNGEON;

    private BattleEncounter currentEncounter;
    private MapEntity currentEnemyEntity;

    private final BattleRenderer battleRenderer = new BattleRenderer();

    ///
    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        textureManager.loadFromFolder("src/main/java/org/main/images/building");

        dungeonRenderer.setTextureManager(textureManager);
        dungeonRenderer.setWallTextureTheme("wall", "brick", "stone");
        dungeonRenderer.setFloorTextureTheme("floor", "wood", "planks", "wide");

        battleRenderer.setAssets(battleAssets);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                battleRenderer.setMousePoint(e.getPoint());
                handleMouseClick(e);
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                battleRenderer.setMousePoint(e.getPoint());

                if (gameMode == GameMode.BATTLE) {
                    repaint();
                }
            }
        });

        entities.add(new MapEntity(new Monster(MonsterType.SLIME), 4, 3));
        entities.add(new MapEntity(new Monster(MonsterType.SKELETON), 6, 1));

        Timer timer = new Timer(16, e -> {
            long now = System.currentTimeMillis();
            int deltaMs = (int) (now - lastUpdateTime);
            lastUpdateTime = now;

            updateGame(deltaMs);
            repaint();
        });

        timer.start();
    }

    private void handleMouseClick(MouseEvent e) {
        requestFocusInWindow();

        if (gameMode != GameMode.BATTLE) {
            return;
        }

        Point clickPoint = e.getPoint();

        if (battleRenderer.isSkillWindowOpen()) {
            handleSkillWindowClick(clickPoint);
            repaint();
            return;
        }

        if (pendingSkill != null) {
            handleSkillTargetClick(clickPoint);
            repaint();
            return;
        }

        if (pendingBattleCommand != null) {
            handleTargetClick(clickPoint);
            repaint();
            return;
        }

        BattleCommand command = battleRenderer.getCommandAt(clickPoint);

        if (command != null) {
            handleBattleCommand(command);
            repaint();
        }
    }

    private void handleSkillWindowClick(Point clickPoint) {
        if (battleRenderer.isSkillWindowCloseButtonAt(clickPoint)) {
            battleRenderer.closeSkillWindow();
            return;
        }

        BattleSkill clickedSkill = battleRenderer.getSkillAt(clickPoint);

        if (clickedSkill == null) {
            return;
        }

        beginSkillTargeting(clickedSkill);
    }

    private void beginSkillTargeting(BattleSkill skill) {
        BattleActor caster = currentEncounter.getFirstLivingAlly();

        if (caster == null) {
            currentEncounter.setBattleMessage("No one can use that skill.");
            battleRenderer.closeSkillWindow();
            return;
        }

        List<BattleActor> selectableTargets = currentEncounter.getSelectableActorsForSkill(
                caster,
                skill
        );

        if (selectableTargets.isEmpty()) {
            currentEncounter.setBattleMessage("No valid targets.");
            battleRenderer.closeSkillWindow();
            return;
        }

        pendingBattleCommand = null;
        pendingSkill = skill;

        battleRenderer.closeSkillWindow();
        battleRenderer.setSelectableTargets(selectableTargets);
        battleRenderer.setPreviewSkill(skill);

        currentEncounter.setBattleMessage("Choose a target for " + skill.getName() + ".");
    }

    private void handleSkillTargetClick(Point clickPoint) {
        BattleActor selectedActor = battleRenderer.getActorAt(clickPoint);

        if (selectedActor == null) {
            return;
        }

        BattleActor caster = currentEncounter.getFirstLivingAlly();

        if (!currentEncounter.canSelectActorForSkill(caster, selectedActor, pendingSkill)) {
            currentEncounter.setBattleMessage("Invalid target.");
            return;
        }

        List<BattleActor> resolvedTargets = currentEncounter.resolveSkillTargets(
                caster,
                selectedActor,
                pendingSkill
        );

        BattleResult result = currentEncounter.handleSkill(
                caster,
                pendingSkill,
                resolvedTargets
        );

        pendingSkill = null;
        battleRenderer.clearSelectableTargets();
        battleRenderer.clearPreviewSkill();

        switch (result) {
            case CONTINUE -> {
            }
            case VICTORY -> endBattle(true);
            case RAN, DEFEAT -> endBattle(false);
        }
    }

    private void handleTargetClick(Point clickPoint) {
        BattleActor target = battleRenderer.getActorAt(clickPoint);

        if (target == null) {
            return;
        }

        if (pendingBattleCommand == BattleCommand.ATTACK) {
            BattleResult result = currentEncounter.handleAttack(target);

            pendingBattleCommand = null;
            battleRenderer.clearSelectableTargets();

            switch (result) {
                case CONTINUE -> {
                }
                case VICTORY -> endBattle(true);
                case RAN, DEFEAT -> endBattle(false);
            }
        }
    }

    private void handleBattleCommand(BattleCommand command) {
        if (currentEncounter == null) {
            return;
        }

        if (command == BattleCommand.ATTACK) {
            BattleActor attacker = currentEncounter.getFirstLivingAlly();

            List<BattleActor> validTargets = currentEncounter.getValidTargets(
                    attacker,
                    BattleTargetingMode.NORMAL_MELEE
            );

            pendingBattleCommand = BattleCommand.ATTACK;
            battleRenderer.setSelectableTargets(validTargets);

            currentEncounter.setBattleMessage("Choose a target.");
            repaint();
            return;
        }

        if (command == BattleCommand.SKILL) {
            pendingBattleCommand = null;
            battleRenderer.clearSelectableTargets();

            battleRenderer.openSkillWindow();
            currentEncounter.setBattleMessage("Choose a skill.");

            repaint();
            return;
        }

        BattleResult result = currentEncounter.handleCommand(command);

        switch (result) {
            case CONTINUE -> {
            }
            case VICTORY -> endBattle(true);
            case RAN, DEFEAT -> endBattle(false);
        }
    }



    private void updateGame(int deltaMs) {
        for (MapEntity entity : entities) {
            entity.update(deltaMs);
        }
    }

    private boolean isPlayerAt(int x, int y) {
        return playerX == x && playerY == y;
    }

    private MapEntity getBlockingEntityAt(int x, int y) {
        for (MapEntity entity : entities) {
            if (entity.isAt(x, y) && entity.blocksMovement()) {
                return entity;
            }
        }

        return null;
    }

    private boolean isOccupied(int x, int y) {
        return isPlayerAt(x, y) || getBlockingEntityAt(x, y) != null;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Wizardry-esque Dungeon Base");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new WizardryBase());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (gameMode == GameMode.DUNGEON) {
            dungeonRenderer.draw(
                    g2,
                    dungeonMap,
                    entities,
                    playerX,
                    playerY,
                    dir,
                    getWidth(),
                    getHeight()
            );

            drawMiniMap(g2);
            drawHud(g2);
        }

        if (gameMode == GameMode.BATTLE) {
            battleRenderer.draw(
                    g2,
                    currentEncounter,
                    getWidth(),
                    getHeight()
            );
        }
    }

    private void drawMiniMap(Graphics2D g) {
        int tile = 18;
        int startX = 20;
        int startY = 20;

        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                TileType tileType = dungeonMap.getTile(x, y);

                g.setColor(tileType.isWallLike() ? Color.DARK_GRAY : Color.GRAY);
                g.fillRect(startX + x * tile, startY + y * tile, tile - 2, tile - 2);
            }
        }

        for (MapEntity entity : entities) {
            g.setColor(entity.getType() == EntityType.ENEMY ? Color.MAGENTA : Color.CYAN);

            g.fillOval(
                    startX + entity.getX() * tile + 4,
                    startY + entity.getY() * tile + 4,
                    tile - 10,
                    tile - 10
            );
        }

        g.setColor(Color.RED);
        g.fillOval(startX + playerX * tile + 3, startY + playerY * tile + 3, tile - 8, tile - 8);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.drawString("W / Numpad 8: Move Forward", 20, getHeight() - 90);
        g.drawString("S / Numpad 2: Move Backward", 20, getHeight() - 70);
        g.drawString("A/D / Numpad 4/6: Strafe", 20, getHeight() - 50);
        g.drawString("Q/E / Numpad 7/9: Turn", 20, getHeight() - 30);
        g.drawString("F / Enter / Space: Interact", 20, getHeight() - 10);
    }

    private void movement(int dx, int dy) {
        if (inBattle) {
            return;
        }

        Point nextPosition = movementEngine.move(playerX, playerY, dx, dy, dungeonMap);

        if (nextPosition.x == playerX && nextPosition.y == playerY) {
            return;
        }

        MapEntity blockingEntity = getBlockingEntityAt(nextPosition.x, nextPosition.y);

        if (blockingEntity != null) {
            if (blockingEntity.getType() == EntityType.ENEMY) {
                startBattle(blockingEntity);
            }

            return;
        }

        playerX = nextPosition.x;
        playerY = nextPosition.y;
    }

    private void startBattle(MapEntity enemyEntity) {
        if (gameMode == GameMode.BATTLE) {
            return;
        }

        if (enemyEntity.getMonster() == null) {
            System.out.println("Enemy entity has no monster data.");
            return;
        }

        currentEnemyEntity = enemyEntity;
        List<Monster> monsters = new ArrayList<>();
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());
        monsters.add(enemyEntity.getMonster());

        currentEncounter = BattleEncounter.fromMonster(monsters);

        gameMode = GameMode.BATTLE;
        repaint();
    }

    private void turnLeft() {
        dir = movementEngine.turnLeft(dir);
    }

    private void turnRight() {
        dir = movementEngine.turnRight(dir);
    }

    private int forwardX() {
        return movementEngine.forwardX(dir);
    }

    private int forwardY() {
        return movementEngine.forwardY(dir);
    }

    private int leftX() {
        return movementEngine.leftX(dir);
    }

    private int leftY() {
        return movementEngine.leftY(dir);
    }

    private int rightX() {
        return movementEngine.rightX(dir);
    }

    private int rightY() {
        return movementEngine.rightY(dir);
    }

    private String directionName() {
        return movementEngine.directionName(dir);
    }

    private void interact() {
        int targetX = playerX + forwardX();
        int targetY = playerY + forwardY();

        TileType targetTile = dungeonMap.getTile(targetX, targetY);

        if (targetTile == TileType.DOOR_CLOSED) {
            dungeonMap.setTile(targetX, targetY, TileType.DOOR_OPEN);
            return;
        }

        if (targetTile == TileType.DOOR_OPEN) {
            if (isOccupied(targetX, targetY)) {
                System.out.println("Something is blocking the door.");
                return;
            }

            dungeonMap.setTile(targetX, targetY, TileType.DOOR_CLOSED);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameMode == GameMode.BATTLE) {
            handleBattleInput(e);
            repaint();
            return;
        }

        handleDungeonInput(e);
        repaint();
    }

    private void handleBattleInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_1, KeyEvent.VK_A -> handleBattleCommand(BattleCommand.ATTACK);
            case KeyEvent.VK_2, KeyEvent.VK_S -> handleBattleCommand(BattleCommand.SKILL);
            case KeyEvent.VK_3, KeyEvent.VK_R -> handleBattleCommand(BattleCommand.RUN);
            case KeyEvent.VK_ESCAPE -> endBattle(false);
        }
    }

    private void endBattle(boolean removeEnemy) {
        if (removeEnemy && currentEnemyEntity != null) {
            entities.remove(currentEnemyEntity);
        }

        currentEncounter = null;
        currentEnemyEntity = null;
        gameMode = GameMode.DUNGEON;

        repaint();
    }

    public void handleDungeonInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            //Forward
            case KeyEvent.VK_W, KeyEvent.VK_NUMPAD8 -> movement(forwardX(), forwardY());
            //Strife Left
            case KeyEvent.VK_A, KeyEvent.VK_NUMPAD4 -> movement(leftX(), leftY());
            //Strife Right
            case KeyEvent.VK_D, KeyEvent.VK_NUMPAD6 -> movement(rightX(), rightY());
            //Backwards
            case KeyEvent.VK_S, KeyEvent.VK_NUMPAD2 -> movement(-forwardX(), -forwardY());
            //Turn Left
            case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> turnLeft();
            //Turn Right
            case KeyEvent.VK_E, KeyEvent.VK_NUMPAD9 -> turnRight();
            //Intract
            case KeyEvent.VK_F, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> interact();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
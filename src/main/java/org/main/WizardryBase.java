package org.main;

import org.main.battle.*;
import org.main.core.GameState;
import org.main.core.DungeonController;
import org.main.core.MiniMapRenderer;
import org.main.engine.*;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class WizardryBase extends JPanel implements KeyListener {

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();

    private final MovementEngine movementEngine = new MovementEngine();

    private final BattleAssets battleAssets = BattleAssets.loadDefault();
    private final BattleRenderer battleRenderer = new BattleRenderer();

    private final TextureManager textureManager = new TextureManager();

    private final GameState gameState = new GameState(DungeonMap.testMap());
    private final DungeonController dungeonController;
    private final BattleController battleController;

    private long lastUpdateTime = System.currentTimeMillis();

    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        dungeonController = new DungeonController(gameState, movementEngine);
        battleController = new BattleController(gameState, battleRenderer);

        /*
         * Temporary debug unlock.
         * Remove or comment this out when you want the minimap to require
         * a spell, item, or ability unlock.
         */
         gameState.unlockMiniMap();

        textureManager.loadFromFolder("src/main/java/org/main/images/building");

        dungeonRenderer.setTextureManager(textureManager);
        dungeonRenderer.setWallTextureTheme("wall", "brick", "stone");
        dungeonRenderer.setFloorTextureTheme("floor", "wood", "planks", "wide");

        battleRenderer.setAssets(battleAssets);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e);
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }
        });

        gameState.addEntity(new MapEntity(new Monster(MonsterType.SLIME), 4, 3));
        gameState.addEntity(new MapEntity(new Monster(MonsterType.SKELETON), 6, 1));

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

        if (!gameState.isBattleMode()) {
            return;
        }

        battleController.handleMouseClick(e.getPoint());
        repaint();
    }

    private void handleMouseMoved(MouseEvent e) {
        if (!gameState.isBattleMode()) {
            return;
        }

        battleController.handleMouseMoved(e.getPoint());
        repaint();
    }

    private void updateGame(int deltaMs) {
        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }
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

        if (gameState.isDungeonMode()) {
            dungeonRenderer.draw(
                    g2,
                    gameState.getDungeonMap(),
                    gameState.getEntities(),
                    gameState.getPlayerX(),
                    gameState.getPlayerY(),
                    gameState.getDirection(),
                    getWidth(),
                    getHeight()
            );

            miniMapRenderer.draw(g2, gameState);
        }

        if (gameState.isBattleMode()) {
            battleRenderer.draw(
                    g2,
                    gameState.getCurrentEncounter(),
                    getWidth(),
                    getHeight()
            );
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameState.isBattleMode()) {
            battleController.handleInput(e);
            repaint();
            return;
        }

        dungeonController.handleInput(e);
        repaint();
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
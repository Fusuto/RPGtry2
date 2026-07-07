package org.main;

import org.main.battle.*;
import org.main.core.*;
import org.main.engine.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class WizardryBase extends JPanel implements KeyListener {

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();

    private final MovementEngine movementEngine = new MovementEngine();

    private final BattleRenderer battleRenderer = new BattleRenderer();

    private final InteractionSystem.InteractionWindow interactionWindow =
            new InteractionSystem.InteractionWindow();
    private final ShopSystem.ShopWindow shopWindow = new ShopSystem.ShopWindow();
    private final InteractionSystem.InteractionRegistry interactionRegistry =
            InteractionSystem.InteractionRegistry.createDefault();
    private final GameState gameState = new GameState(DungeonMap.testMap());
    private final DungeonController dungeonController;
    private final BattleController battleController;
    private final InventorySystem.InventoryPanel inventoryPanel;

    private long lastUpdateTime = System.currentTimeMillis();

    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        dungeonController = new DungeonController(
                gameState,
                movementEngine,
                interactionRegistry
        );

        battleController = new BattleController(gameState, battleRenderer);
        inventoryPanel = new InventorySystem.InventoryPanel(gameState.getInventory());

        /*
         * Temporary debug unlock.
         * Remove or comment this out when you want the minimap to require
         * a spell, item, or ability unlock.
         */
        gameState.unlockMiniMap();

        RendererBootstrap.configureDefaultRenderers(dungeonRenderer, battleRenderer);
        installMouseInput();
        GameBootstrap.seedTestContent(gameState);

        Timer timer = new Timer(16, e -> {
            long now = System.currentTimeMillis();
            int deltaMs = (int) (now - lastUpdateTime);
            lastUpdateTime = now;

            updateGame(deltaMs);
            repaint();
        });

        timer.start();
    }

    private void installMouseInput() {
        new GameMouseInputRouter(
                this,
                gameState,
                battleController,
                interactionWindow,
                shopWindow,
                inventoryPanel
        ).install();
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

            if (gameState.isInventoryOpen()) {
                inventoryPanel.draw(g2, getWidth(), getHeight());
            }

            if (gameState.hasActiveShop()) {
                shopWindow.draw(g2, gameState, getWidth(), getHeight());
            }

            if (gameState.hasActiveInteraction()) {
                interactionWindow.draw(
                        g2,
                        gameState.getActiveInteraction(),
                        getWidth(),
                        getHeight()
                );
            }
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
        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            boolean consumed = interactionWindow.handleKeyPressed(
                    e,
                    gameState.getActiveInteraction()
            );

            if (consumed) {
                repaint();
            }

            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveShop()) {
            boolean consumed = shopWindow.handleKeyPressed(e, gameState);

            if (consumed) {
                repaint();
            }

            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_I && gameState.isDungeonMode()) {
            gameState.toggleInventory();
            repaint();
            return;
        }

        if (gameState.isBattleMode()) {
            battleController.handleInput(e);
            repaint();
            return;
        }

        if (gameState.isInventoryOpen()) {
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
package org.main;

import org.main.battle.*;
import org.main.content.EnvironmentLibrary;
import org.main.core.*;
import org.main.engine.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class WizardryBase extends JPanel implements KeyListener {

    private final DungeonRenderer dungeonRenderer = new DungeonRenderer();
    private final MiniMapRenderer miniMapRenderer = new MiniMapRenderer();
    private final OverworldHud overworldHud = new OverworldHud();

    private final MovementEngine movementEngine = new MovementEngine();
    private final SoundSystem soundSystem = new SoundSystem();
    private final EnvironmentLibrary environment = EnvironmentLibrary.STARTER_DUNGEON;

    private final BattleRenderer battleRenderer = new BattleRenderer();

    private final InteractionSystem.InteractionWindow interactionWindow =
            new InteractionSystem.InteractionWindow();
    private final ShopSystem.ShopWindow shopWindow = new ShopSystem.ShopWindow();
    private final InteractionSystem.InteractionRegistry interactionRegistry =
            InteractionSystem.InteractionRegistry.createDefault();
    private final GameState gameState = new GameState(
            DungeonMap.testMap(),
            GameBootstrap.createDefaultPlayerCharacter()
    );
    private final DungeonController dungeonController;
    private final BattleController battleController;
    private final InventorySystem.InventoryPanel inventoryPanel;

    private long lastUpdateTime = System.currentTimeMillis();
    private double averageFrameMs = 16.0;
    private double lastUpdateMs = 0.0;
    private double lastRenderMs = 0.0;

    public WizardryBase() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        dungeonController = new DungeonController(
                gameState,
                movementEngine,
                interactionRegistry,
                soundSystem,
                environment
        );

        battleController = new BattleController(gameState, battleRenderer, soundSystem, environment);
        inventoryPanel = new InventorySystem.InventoryPanel(gameState.getInventory(), gameState, soundSystem);

        /*
         * Temporary debug unlock.
         * Remove or comment this out when you want the minimap to require
         * a spell, item, or ability unlock.
         */
        gameState.unlockMiniMap();

        RendererBootstrap.configureDefaultRenderers(dungeonRenderer, battleRenderer);
        installMouseInput();
        GameBootstrap.seedTestContent(gameState);
        soundSystem.playAmbience(environment.getAmbienceSoundPath());

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
                inventoryPanel,
                overworldHud,
                this::openConfigMenu
        ).install();
    }

    private void updateGame(int deltaMs) {
        long updateStart = System.nanoTime();
        averageFrameMs = averageFrameMs * 0.90 + deltaMs * 0.10;

        gameState.updateMovementAnimation(deltaMs);

        for (MapEntity entity : gameState.getEntities()) {
            entity.update(deltaMs);
        }

        lastUpdateMs = (System.nanoTime() - updateStart) / 1_000_000.0;
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
        long renderStart = System.nanoTime();
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
                    getHeight(),
                    gameState.getCameraOffsetForward(),
                    gameState.getCameraOffsetSide(),
                    gameState.getCameraRotationRadians()
            );

            miniMapRenderer.draw(g2, gameState);

            if (gameState.isInventoryOpen()) {
                inventoryPanel.draw(
                        g2,
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
                );
            }

            overworldHud.draw(g2, gameState, getWidth(), getHeight());

            if (gameState.hasActiveShop()) {
                shopWindow.draw(g2, gameState, getWidth(), getHeight());
            }

            if (gameState.hasActiveInteraction()) {
                interactionWindow.draw(
                        g2,
                        gameState.getActiveInteraction(),
                        getWidth(),
                        getHeight() - overworldHud.getBottomReservedHeight()
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

        lastRenderMs = (System.nanoTime() - renderStart) / 1_000_000.0;
        drawPerformanceOverlay(g2);
    }

    private void drawPerformanceOverlay(Graphics2D g) {
        int fps = averageFrameMs <= 0.0 ? 0 : (int) Math.round(1000.0 / averageFrameMs);
        String[] lines = {
                "FPS " + fps,
                String.format("Frame %.1f ms", averageFrameMs),
                String.format("Update %.2f ms", lastUpdateMs),
                String.format("Render %.2f ms", lastRenderMs),
                "Entities " + gameState.getEntities().size(),
                "Map " + gameState.getDungeonMap().getWidth() + "x" + gameState.getDungeonMap().getHeight()
        };

        Font previousFont = g.getFont();
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int width = 0;

        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }

        int x = getWidth() - width - 18;
        int y = 14;
        int height = lineHeight * lines.length + 8;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x - 6, y - 2, width + 12, height);
        g.setColor(new Color(220, 240, 220));

        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], x, y + lineHeight * (i + 1));
        }

        g.setFont(previousFont);
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

        InputBindings bindings = gameState.getInputBindings();
        int keyCode = e.getKeyCode();

        if (bindings.matches(InputBindings.Action.ESCAPE_MENU, keyCode) && gameState.isDungeonMode()) {
            openConfigMenu();
            repaint();
            return;
        }

        if (bindings.matches(InputBindings.Action.INVENTORY, keyCode) && gameState.isDungeonMode()) {
            gameState.toggleInventory();
            repaint();
            return;
        }

        if (bindings.matches(InputBindings.Action.SKILLS, keyCode) && gameState.isDungeonMode()) {
            gameState.toggleSkills();
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

    private void openConfigMenu() {
        gameState.openInteraction(InteractionSystem.configMenu(
                soundSystem,
                gameState,
                () -> {
                    Window window = SwingUtilities.getWindowAncestor(this);

                    if (window != null) {
                        window.dispose();
                    }

                    System.exit(0);
                },
                () -> gameState.openInteraction(InteractionSystem.controlsMenu(gameState.getInputBindings()))
        ));
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}

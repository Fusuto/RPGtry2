package org.main.core;

import org.main.battle.BattleController;

import javax.swing.JComponent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class GameMouseInputRouter {
    private final JComponent component;
    private final GameState gameState;
    private final BattleController battleController;
    private final InteractionSystem.InteractionWindow interactionWindow;
    private final ShopSystem.ShopWindow shopWindow;
    private final InventorySystem.InventoryPanel inventoryPanel;
    private final OverworldHud overworldHud;
    private final Runnable escapeMenuAction;

    public GameMouseInputRouter(
            JComponent component,
            GameState gameState,
            BattleController battleController,
            InteractionSystem.InteractionWindow interactionWindow,
            ShopSystem.ShopWindow shopWindow,
            InventorySystem.InventoryPanel inventoryPanel,
            OverworldHud overworldHud,
            Runnable escapeMenuAction
    ) {
        this.component = component;
        this.gameState = gameState;
        this.battleController = battleController;
        this.interactionWindow = interactionWindow;
        this.shopWindow = shopWindow;
        this.inventoryPanel = inventoryPanel;
        this.overworldHud = overworldHud;
        this.escapeMenuAction = escapeMenuAction;
    }

    public void install() {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClicked(e);
            }
        });

        component.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }
        });

        component.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouseWheelMoved(e);
            }
        });
    }

    private void handleMousePressed(MouseEvent e) {
        component.requestFocusInWindow();

        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            repaintIfConsumed(interactionWindow.handleMousePressed(
                    e,
                    gameState.getActiveInteraction()
            ));
            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveShop()) {
            repaintIfConsumed(shopWindow.handleMousePressed(e, gameState));
            return;
        }

        if (gameState.isDungeonMode()
                && overworldHud != null
                && overworldHud.handleMousePressed(
                e.getPoint(),
                gameState,
                component.getWidth(),
                component.getHeight(),
                escapeMenuAction
        )) {
            component.repaint();
            return;
        }

        if (gameState.isDungeonMode() && gameState.isInventoryOpen()) {
            repaintIfConsumed(inventoryPanel.handleMousePressed(e));
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveShop()) {
            return;
        }

        if (gameState.isDungeonMode() && gameState.isInventoryOpen()) {
            repaintIfConsumed(inventoryPanel.handleMouseReleased(e));
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        component.requestFocusInWindow();

        if (!gameState.isBattleMode()) {
            return;
        }

        battleController.handleMouseClick(e.getPoint());
        component.repaint();
    }

    private void handleMouseDragged(MouseEvent e) {
        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            return;
        }

        if (gameState.isDungeonMode() && gameState.hasActiveShop()) {
            return;
        }

        if (gameState.isDungeonMode() && gameState.isInventoryOpen()) {
            repaintIfConsumed(inventoryPanel.handleMouseDragged(e));
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        if (!gameState.isBattleMode()) {
            return;
        }

        battleController.handleMouseMoved(e.getPoint());
        component.repaint();
    }

    private void handleMouseWheelMoved(MouseWheelEvent e) {
        if (gameState.isDungeonMode() && gameState.hasActiveInteraction()) {
            repaintIfConsumed(interactionWindow.handleMouseWheelMoved(
                    e,
                    gameState.getActiveInteraction()
            ));
        }
    }

    private void repaintIfConsumed(boolean consumed) {
        if (consumed) {
            component.repaint();
        }
    }
}

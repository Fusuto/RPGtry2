package org.main.core;

import org.main.content.ItemLibrary;
import org.main.content.NpcLibrary;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

public final class GameBootstrap {
    private GameBootstrap() {
    }

    public static void seedTestContent(GameState gameState) {
        if (gameState == null) {
            return;
        }

        gameState.addEntity(new MapEntity(new Monster(MonsterType.SLIME), 4, 3));
        gameState.addEntity(NpcLibrary.OLD_GUARD.createEntity(6, 1));
        gameState.addEntity(NpcLibrary.GOBLIN_MERCHANT.createEntity(5, 7));

        gameState.addEntity(new MapEntity(ItemLibrary.POTION.createItem(), 2, 1));

        gameState.getInventory().addItem(ItemLibrary.IRON_SWORD.createItem());
        gameState.getInventory().addItem(ItemLibrary.LEATHER_CAP.createItem());
        gameState.getInventory().addItem(ItemLibrary.SILVER_RING.createItem());
    }
}

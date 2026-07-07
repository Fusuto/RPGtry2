package org.main.core;

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

        gameState.addEntity(
                new MapEntity(
                        new Monster(MonsterType.SKELETON),
                        6,
                        1,
                        Library.EntityType.NPC
                ).withInteractionId("old_guard_intro")
        );

        gameState.addEntity(
                new MapEntity(
                        new Monster(MonsterType.GOBLIN),
                        5,
                        7,
                        Library.EntityType.NPC
                ).withInteractionId("merchant_basic")
        );

        InventorySystem.Item potion = new InventorySystem.Item(
                "Potion",
                InventorySystem.ItemType.CONSUMABLE,
                "src/main/java/org/main/images/monster/Nov-2015/item/potion/brilliant_blue.png"
        );
        gameState.addEntity(new MapEntity(potion, 2, 1));

        gameState.getInventory().addItem(new InventorySystem.Item(
                "Iron Sword",
                InventorySystem.ItemType.WEAPON,
                "src/main/java/org/main/images/monster/Nov-2015/item/weapon/long_sword1.png"
        ));

        gameState.getInventory().addItem(new InventorySystem.Item(
                "Leather Cap",
                InventorySystem.ItemType.HEAD_GEAR,
                "src/main/java/org/main/images/monster/Nov-2015/item/armour/headgear/elven_leather_helm.png"
        ));

        gameState.getInventory().addItem(new InventorySystem.Item(
                "Silver Ring",
                InventorySystem.ItemType.RING,
                "src/main/java/org/main/images/monster/Nov-2015/item/ring/artefact/urand_shadows.png"
        ));
    }
}

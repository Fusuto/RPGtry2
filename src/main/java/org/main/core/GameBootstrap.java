package org.main.core;

import org.main.content.CraftingNodeLibrary;
import org.main.content.InteractionLibrary;
import org.main.content.ItemLibrary;
import org.main.content.GenericNpcLibrary;
import org.main.content.GatheringNodeLibrary;
import org.main.content.MainNpcLibrary;
import org.main.content.PlayerRegionLibrary;
import org.main.content.SkillLibrary;
import org.main.engine.MapEntity;
import org.main.monsters.Monster;
import org.main.monsters.MonsterType;

import java.util.ArrayList;
import java.util.EnumMap;

public final class GameBootstrap {
    private static final int FURNACE_X = 4;
    private static final int FURNACE_Y = 5;
    private static final int ANVIL_X = 10;
    private static final int ANVIL_Y = 5;
    private static final int STARTING_GOLD = 100;

    private GameBootstrap() {
    }

    public static PlayerCharacter createDefaultPlayerCharacter() {
        return createPlayerCharacter("Player", PlayerRegionLibrary.MIDLANDS);
    }

    public static PlayerCharacter createPlayerCharacter(String name, PlayerRegionLibrary playerRegion) {
        PlayerRegionLibrary selectedRegion = playerRegion == null ? PlayerRegionLibrary.MIDLANDS : playerRegion;
        EnumMap<PlayerStat, Integer> stats = PlayerCharacter.createDefaultStats();
        var battleSkills = new ArrayList<>(SkillLibrary.createUniversalPlayerSkills());

        PlayerCharacter player = new PlayerCharacter(
                name,
                startingMaxHp(stats),
                startingMaxHp(stats),
                new InventorySystem.Inventory(),
                PlayerCharacter.createDefaultSkills(),
                "assets/images/monster/Nov-2015/player/base/human_m.png",
                selectedRegion,
                stats,
                battleSkills
        );
        player.equipStarterLimbs(selectedRegion.createStarterLimbs());
        return player;
    }

    private static int startingMaxHp(EnumMap<PlayerStat, Integer> stats) {
        return Math.max(1, stats.get(PlayerStat.VITALITY));
    }

    public static void seedTestContent(GameState gameState) {
        if (gameState == null) {
            return;
        }

        gameState.addEntityUnlessRemoved(new MapEntity(new Monster(MonsterType.SLIME), 8, 6));
        gameState.addEntityUnlessRemoved(MainNpcLibrary.TIPPING_THE_HAT_SKELETON.createEntity(8, 1));
        gameState.addEntityUnlessRemoved(GenericNpcLibrary.GOBLIN_MERCHANT.createEntity(2, 9));
        gameState.addEntityUnlessRemoved(CraftingNodeLibrary.CAMPFIRE.createEntity(6, 8));
        if (gameState.getDungeonMap() != null) {
            gameState.getDungeonMap().setTile(FURNACE_X, FURNACE_Y, Library.TileType.FLOOR);
        }
        gameState.addEntityUnlessRemoved(CraftingNodeLibrary.FURNACE.createEntity(FURNACE_X, FURNACE_Y));
        if (gameState.getDungeonMap() != null) {
            gameState.getDungeonMap().setTile(ANVIL_X, ANVIL_Y, Library.TileType.FLOOR);
        }
        gameState.addEntityUnlessRemoved(CraftingNodeLibrary.ANVIL.createEntity(ANVIL_X, ANVIL_Y));
        gameState.addEntityUnlessRemoved(GatheringNodeLibrary.MINERAL_ROCK_A.createEntity(8, 4));
        gameState.setTileInteractionId(10, 9, InteractionLibrary.GENERATED_DUNGEON_GATE.getInteractionId());

        gameState.setTileInteractionId(4, 3, GatheringNodeLibrary.FISHING_SHOAL.getInteractionId());

        gameState.addEntityUnlessRemoved(new MapEntity(ItemLibrary.POTION.createItem(), 2, 1));

        gameState.getInventory().addItem(ItemLibrary.IRON_SWORD.createItem());
        gameState.getInventory().addItem(ItemLibrary.SILVER_RING.createItem());
        gameState.addGold(STARTING_GOLD);
    }
}

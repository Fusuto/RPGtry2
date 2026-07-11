package org.main.core;

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
                20 + stats.get(PlayerStat.VITALITY) * 5,
                20 + stats.get(PlayerStat.VITALITY) * 5,
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

    public static void seedTestContent(GameState gameState) {
        if (gameState == null) {
            return;
        }

        gameState.addEntityUnlessRemoved(new MapEntity(new Monster(MonsterType.SLIME), 8, 6));
        gameState.addEntityUnlessRemoved(MainNpcLibrary.TIPPING_THE_HAT_SKELETON.createEntity(8, 1));
        gameState.addEntityUnlessRemoved(GenericNpcLibrary.GOBLIN_MERCHANT.createEntity(2, 9));
        gameState.setTileInteractionId(10, 9, "generated_dungeon_gate");
        gameState.setTileInteractionId(4, 3, GatheringNodeLibrary.FISHING_SHOAL.getInteractionId());

        gameState.addEntityUnlessRemoved(new MapEntity(ItemLibrary.POTION.createItem(), 2, 1));

        gameState.getInventory().addItem(ItemLibrary.IRON_SWORD.createItem());
        gameState.getInventory().addItem(ItemLibrary.SILVER_RING.createItem());
    }
}

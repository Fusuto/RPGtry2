package org.main.core;

import org.main.content.ItemLibrary;
import org.main.content.GenericNpcLibrary;
import org.main.content.GatheringNodeLibrary;
import org.main.content.MainNpcLibrary;
import org.main.content.PlayerClassLibrary;
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
        return createPlayerCharacter("Player", PlayerClassLibrary.WARRIOR);
    }

    public static PlayerCharacter createPlayerCharacter(String name, PlayerClassLibrary playerClass) {
        PlayerClassLibrary selectedClass = playerClass == null ? PlayerClassLibrary.WARRIOR : playerClass;
        EnumMap<PlayerStat, Integer> stats = PlayerCharacter.createDefaultStats();
        selectedClass.getPreferredStatGrowth().forEach((stat, amount) -> stats.put(stat, stats.get(stat) + amount));
        var battleSkills = new ArrayList<>(SkillLibrary.createUniversalPlayerSkills());
        battleSkills.addAll(selectedClass.getStarterSkills().stream()
                .map(skill -> skill.createSkill())
                .toList());

        return new PlayerCharacter(
                name,
                20 + stats.get(PlayerStat.VITALITY) * 5,
                20 + stats.get(PlayerStat.VITALITY) * 5,
                new InventorySystem.Inventory(),
                PlayerCharacter.createDefaultSkills(),
                "assets/images/monster/Nov-2015/player/base/human_m.png",
                selectedClass,
                stats,
                battleSkills
        );
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

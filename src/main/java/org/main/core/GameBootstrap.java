package org.main.core;

import org.main.content.PlayerRegionLibrary;
import org.main.content.SkillLibrary;

import java.util.ArrayList;
import java.util.EnumMap;

public final class GameBootstrap {
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

        gameState.addGold(STARTING_GOLD);
    }
}

package org.main.core;

import org.junit.jupiter.api.Test;
import org.main.content.MapDesignLibrary;
import org.main.engine.DungeonMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestRewardServiceTest {
    @Test
    void skillAndInventoryRewardsShareValidationAndApplication() {
        Library.TileType[][] tiles = {{Library.TileType.FLOOR}};
        GameState state = new GameState(new DungeonMap(tiles));
        state.setPlayerCharacter(GameBootstrap.createPlayerCharacter("Reward Test", null));
        MapDesignLibrary.RewardDefinition xp = new MapDesignLibrary.RewardDefinition(
                "xp", MapDesignLibrary.QuestRewardType.SKILL_XP, "", CharacterSkill.MINING, 10);
        MapDesignLibrary.RewardDefinition missing = new MapDesignLibrary.RewardDefinition(
                "missing", MapDesignLibrary.QuestRewardType.ITEM, "missing_item", null, 1);

        assertEquals("", QuestRewardService.validate(state, xp));
        assertTrue(QuestRewardService.grant(state, xp));
        assertTrue(QuestRewardService.validate(state, missing).contains("missing_item"));
        assertFalse(QuestRewardService.grant(state, missing));
    }
}

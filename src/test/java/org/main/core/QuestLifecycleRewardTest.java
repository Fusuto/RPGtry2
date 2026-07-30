package org.main.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.main.content.MapDesignLibrary;
import org.main.engine.DungeonMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestLifecycleRewardTest {
    private static final String QUEST_ID = "reward_lifecycle";
    private static final String TOKEN = "TEST_TOKEN";
    private static final String HERB = "TEST_HERB";
    private static final String TROPHY = "TEST_TROPHY";
    private static final String FILLER = "TEST_FILLER";

    private GameState gameState;
    private MapDesignLibrary.AuthoredQuest quest;
    private MapDesignLibrary.QuestFlowChoice acceptChoice;
    private MapDesignLibrary.QuestFlowChoice advanceChoice;
    private MapDesignLibrary.QuestFlowChoice completeChoice;
    private MapDesignLibrary.QuestFlowChoice epilogueChoice;

    @BeforeEach
    void setUp() {
        Library.TileType[][] tiles = {
                {Library.TileType.FLOOR, Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.FLOOR, Library.TileType.FLOOR},
                {Library.TileType.FLOOR, Library.TileType.FLOOR, Library.TileType.FLOOR}
        };
        gameState = new GameState(new DungeonMap(tiles), new PlayerCharacter("Tester", 100, 100));
        gameState.setCustomItems(List.of(
                item("GOLD", "Gold", true),
                item(TOKEN, "Quest Token", true),
                item(HERB, "Reward Herb", true),
                item(TROPHY, "Reward Trophy", false),
                item(FILLER, "Inventory Filler", false)
        ));
        quest = lifecycleQuest();
        gameState.setAuthoredQuests(List.of(quest));
    }

    @Test
    void questLifecycleGrantsEveryRewardExactlyOnce() {
        QuestRuntime runtime = gameState.getQuestRuntime();
        assertNull(runtime.state(QUEST_ID), "The unmet start requirement must hide the quest.");

        InventorySystem.Item tokens = gameState.createItemByNameOrId(TOKEN);
        tokens.addQuantity(1);
        assertTrue(gameState.getInventory().addItem(tokens));
        assertEquals(QuestRuntime.State.AVAILABLE, runtime.state(QUEST_ID));

        QuestRuntime.TransitionResult accepted = runtime.applyChoice(QUEST_ID, acceptChoice);
        assertTrue(accepted.success(), accepted.message());
        assertEquals(QuestRuntime.State.ACTIVE, runtime.state(QUEST_ID));
        assertEquals("turn_in", runtime.currentStageId(QUEST_ID));
        assertEquals(2, count(HERB));
        assertEquals(5, gameState.getGold());

        assertTrue(runtime.applyChoice(QUEST_ID, advanceChoice).success());
        assertEquals(QuestRuntime.State.ACTIVE, runtime.state(QUEST_ID));
        assertEquals("report", runtime.currentStageId(QUEST_ID));
        assertEquals(0, count(TOKEN), "Both same-ID turn-ins must consume their own quantity.");
        assertEquals(3, count(HERB));
        assertEquals(2, count(TROPHY));
        assertEquals(12, gameState.getGold());
        assertEquals(20, gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.COOKING));

        runtime.recordTalk("reward_npc");
        assertTrue(runtime.applyChoice(QUEST_ID, completeChoice).success());
        assertEquals(QuestRuntime.State.COMPLETED, runtime.state(QUEST_ID));
        assertEquals("", runtime.currentStageId(QUEST_ID));
        assertEquals(7, count(HERB));
        assertEquals(3, count(TROPHY));
        assertEquals(36, gameState.getGold());
        assertEquals(35, gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.COOKING));
        assertEquals(25, gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.SMITHING));

        assertTrue(runtime.applyChoice(QUEST_ID, epilogueChoice).success());
        assertEquals(53, gameState.getGold());
        assertTrue(runtime.applyChoice(QUEST_ID, epilogueChoice).success());
        assertEquals(53, gameState.getGold(), "Epilogue rewards must not repeat.");

        int herbBeforeRetry = count(HERB);
        int goldBeforeRetry = gameState.getGold();
        assertFalse(runtime.applyChoice(QUEST_ID, completeChoice).success());
        assertEquals(herbBeforeRetry, count(HERB));
        assertEquals(goldBeforeRetry, gameState.getGold());
    }

    @Test
    void failedTransitionGrantsNothing() {
        QuestRuntime runtime = gameState.getQuestRuntime();
        InventorySystem.Item tokens = gameState.createItemByNameOrId(TOKEN);
        tokens.addQuantity(1);
        assertTrue(gameState.getInventory().addItem(tokens));
        QuestRuntime.TransitionResult accepted = runtime.applyChoice(QUEST_ID, acceptChoice);
        assertTrue(accepted.success(), accepted.message());

        while (gameState.getInventory().freeSlotCount() > 0) {
            assertTrue(gameState.getInventory().addItem(gameState.createItemByNameOrId(FILLER)));
        }
        int goldBefore = gameState.getGold();
        int herbBefore = count(HERB);
        int cookingXpBefore =
                gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.COOKING);
        int claimsBefore = runtime.snapshots().get(QUEST_ID).claimedRewardKeys().size();

        QuestRuntime.TransitionResult result = runtime.applyChoice(QUEST_ID, advanceChoice);

        assertFalse(result.success());
        assertEquals("turn_in", runtime.currentStageId(QUEST_ID));
        assertEquals(2, count(TOKEN));
        assertEquals(goldBefore, gameState.getGold());
        assertEquals(herbBefore, count(HERB));
        assertEquals(0, count(TROPHY));
        assertEquals(cookingXpBefore,
                gameState.getPlayerCharacter().getSkillExperience(CharacterSkill.COOKING));
        assertEquals(claimsBefore, runtime.snapshots().get(QUEST_ID).claimedRewardKeys().size());
    }

    @Test
    void rewardClaimsSurviveSnapshotRestore() {
        QuestRuntime runtime = gameState.getQuestRuntime();
        InventorySystem.Item tokens = gameState.createItemByNameOrId(TOKEN);
        tokens.addQuantity(1);
        assertTrue(gameState.getInventory().addItem(tokens));
        QuestRuntime.TransitionResult accepted = runtime.applyChoice(QUEST_ID, acceptChoice);
        assertTrue(accepted.success(), accepted.message());
        assertTrue(runtime.applyChoice(QUEST_ID, advanceChoice).success());
        runtime.recordTalk("reward_npc");
        assertTrue(runtime.applyChoice(QUEST_ID, completeChoice).success());
        assertTrue(runtime.applyChoice(QUEST_ID, epilogueChoice).success());
        int goldAfterClaim = gameState.getGold();

        var snapshots = runtime.snapshots();
        runtime.restore(snapshots);

        assertTrue(runtime.applyChoice(QUEST_ID, epilogueChoice).success());
        assertEquals(goldAfterClaim, gameState.getGold());
        assertTrue(runtime.snapshots().get(QUEST_ID).claimedRewardKeys().stream()
                .anyMatch(key -> key.contains("epilogue")));
    }

    @Test
    void choiceConsumptionUsesAuthoredQuantity() {
        MapDesignLibrary.QuestFlowChoice consumeThree = new MapDesignLibrary.QuestFlowChoice(
                "consume_three",
                "Hand over three",
                "",
                List.of(),
                MapDesignLibrary.QuestFlowAction.NONE,
                "",
                TOKEN,
                3,
                List.of(),
                false,
                "Thank you."
        );
        MapDesignLibrary.AuthoredQuest quantityQuest = new MapDesignLibrary.AuthoredQuest(
                "quantity_quest",
                "Quantity Quest",
                "",
                List.of(),
                flow("quantity_offer", consumeThree),
                List.of(),
                List.of(),
                MapDesignLibrary.QuestFlow.empty()
        );
        gameState.setAuthoredQuests(List.of(quantityQuest));
        InventorySystem.Item tokens = gameState.createItemByNameOrId(TOKEN);
        tokens.addQuantity(3);
        assertTrue(gameState.getInventory().addItem(tokens));

        QuestRuntime.TransitionResult first =
                gameState.getQuestRuntime().applyChoice("quantity_quest", consumeThree);
        assertTrue(first.success(), first.message());
        assertEquals(1, count(TOKEN));

        QuestRuntime.TransitionResult second =
                gameState.getQuestRuntime().applyChoice("quantity_quest", consumeThree);
        assertFalse(second.success());
        assertEquals(1, count(TOKEN));
    }

    @Test
    void questOnlyNpcRetainsHubAssignmentWhenCopied() {
        InventorySystem.Item tokens = gameState.createItemByNameOrId(TOKEN);
        tokens.addQuantity(1);
        assertTrue(gameState.getInventory().addItem(tokens));
        MapDesignLibrary.CustomNpc npc = new MapDesignLibrary.CustomNpc(
                "quest_only_npc",
                "Quest Only NPC",
                "",
                "",
                "",
                null,
                org.main.content.CharacterModelDefinition.empty(),
                List.of(QUEST_ID)
        );

        var entity = npc.createEntity(1, 1).copy();

        assertEquals("quest_only_npc", entity.getContentId());
        assertEquals(List.of(QUEST_ID), entity.getQuestIds());
        assertEquals("npc_hub", entity.getInteractionId());
        var interaction = InteractionSystem.InteractionRegistry.createDefault()
                .create(entity.getInteractionId(), gameState, entity, 1, 1);
        assertEquals("Test dialogue.", interaction.getModel().getBodyText());
        assertTrue(interaction.getModel().getOptions().stream()
                .anyMatch(option -> option.getLabel().equals("accept")));
        assertFalse(interaction.getModel().getOptions().stream()
                .anyMatch(option -> option.getLabel().equals("Back to topics")));
    }

    private MapDesignLibrary.AuthoredQuest lifecycleQuest() {
        acceptChoice = choice(
                "accept",
                MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST,
                List.of(
                        reward("accept_herbs", MapDesignLibrary.QuestRewardType.ITEM, HERB, null, 2),
                        reward("accept_gold", MapDesignLibrary.QuestRewardType.GOLD, "", null, 5)
                )
        );
        advanceChoice = choice(
                "advance",
                MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE,
                List.of(
                        reward("choice_herb", MapDesignLibrary.QuestRewardType.ITEM, HERB, null, 1),
                        reward("choice_cooking", MapDesignLibrary.QuestRewardType.SKILL_XP, "",
                                CharacterSkill.COOKING, 20)
                )
        );
        completeChoice = choice(
                "complete",
                MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST,
                List.of(reward("choice_gold", MapDesignLibrary.QuestRewardType.GOLD, "", null, 11))
        );
        epilogueChoice = choice(
                "epilogue_reward",
                MapDesignLibrary.QuestFlowAction.NONE,
                List.of(reward("epilogue_gold", MapDesignLibrary.QuestRewardType.GOLD, "", null, 17))
        );

        MapDesignLibrary.QuestStage turnIn = new MapDesignLibrary.QuestStage(
                "turn_in",
                "Turn In",
                "Turn in both tokens.",
                MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED,
                List.of(
                        objective("token_a", MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM, TOKEN, 1),
                        objective("token_b", MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM, TOKEN, 1)
                ),
                List.of(
                        reward("stage_gold", MapDesignLibrary.QuestRewardType.GOLD, "", null, 7),
                        reward("stage_trophies", MapDesignLibrary.QuestRewardType.ITEM, TROPHY, null, 2)
                ),
                flow("turn_in_node", advanceChoice)
        );
        MapDesignLibrary.QuestStage report = new MapDesignLibrary.QuestStage(
                "report",
                "Report",
                "Report back to the NPC.",
                MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED,
                List.of(objective("talk", MapDesignLibrary.QuestObjectiveType.TALK_TO_NPC,
                        "reward_npc", 1)),
                List.of(reward("report_herbs", MapDesignLibrary.QuestRewardType.ITEM, HERB, null, 3)),
                flow("report_node", completeChoice)
        );
        return new MapDesignLibrary.AuthoredQuest(
                QUEST_ID,
                "Reward Lifecycle",
                "Exercises every quest state and reward point.",
                List.of(new MapDesignLibrary.QuestRequirement(
                        MapDesignLibrary.QuestRequirementType.POSSESS_ITEM,
                        TOKEN,
                        null,
                        2
                )),
                flow("offer_node", acceptChoice),
                List.of(turnIn, report),
                List.of(
                        reward("final_herb", MapDesignLibrary.QuestRewardType.ITEM, HERB, null, 1),
                        reward("final_trophy", MapDesignLibrary.QuestRewardType.ITEM, TROPHY, null, 1),
                        reward("final_gold", MapDesignLibrary.QuestRewardType.GOLD, "", null, 13),
                        reward("final_cooking", MapDesignLibrary.QuestRewardType.SKILL_XP, "",
                                CharacterSkill.COOKING, 15),
                        reward("final_smithing", MapDesignLibrary.QuestRewardType.SKILL_XP, "",
                                CharacterSkill.SMITHING, 25)
                ),
                flow("epilogue_node", epilogueChoice)
        );
    }

    private static MapDesignLibrary.QuestFlow flow(
            String nodeId,
            MapDesignLibrary.QuestFlowChoice choice
    ) {
        return new MapDesignLibrary.QuestFlow(
                nodeId,
                List.of(new MapDesignLibrary.QuestFlowNode(
                        nodeId,
                        "Test dialogue.",
                        80,
                        80,
                        List.of(choice)
                ))
        );
    }

    private static MapDesignLibrary.QuestFlowChoice choice(
            String id,
            MapDesignLibrary.QuestFlowAction action,
            List<MapDesignLibrary.RewardDefinition> rewards
    ) {
        return new MapDesignLibrary.QuestFlowChoice(
                id,
                id,
                "",
                List.of(),
                action,
                "",
                "",
                rewards,
                false,
                "Done."
        );
    }

    private static MapDesignLibrary.QuestObjective objective(
            String id,
            MapDesignLibrary.QuestObjectiveType type,
            String targetId,
            int amount
    ) {
        return new MapDesignLibrary.QuestObjective(
                id,
                type,
                targetId,
                null,
                amount,
                id,
                true
        );
    }

    private static MapDesignLibrary.RewardDefinition reward(
            String id,
            MapDesignLibrary.QuestRewardType type,
            String itemId,
            CharacterSkill skill,
            int amount
    ) {
        return new MapDesignLibrary.RewardDefinition(id, type, itemId, skill, amount);
    }

    private static MapDesignLibrary.CustomItem item(String id, String name, boolean stackable) {
        return new MapDesignLibrary.CustomItem(
                id,
                name,
                InventorySystem.ItemType.MISC,
                "",
                "",
                "",
                WeaponType.NONE,
                GearMaterial.NONE,
                0,
                1,
                "",
                null,
                stackable,
                false,
                1,
                1,
                0
        );
    }

    private int count(String itemId) {
        return gameState.getInventory().countItemByContentId(itemId);
    }
}

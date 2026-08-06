package org.main.core;

import org.main.content.MapDesignLibrary;
import org.main.engine.MapEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns immutable authored quest definitions and transactional player progress.
 */
public final class QuestRuntime {
    public enum State {
        AVAILABLE,
        ACTIVE,
        COMPLETED
    }

    public record Snapshot(
            State state,
            String stageId,
            Map<String, Integer> counters,
            Set<String> claimedRewardKeys
    ) {
        public Snapshot(State state, String stageId, Map<String, Integer> counters) {
            this(state, stageId, counters, Set.of());
        }

        public Snapshot {
            state = state == null ? State.AVAILABLE : state;
            stageId = stageId == null ? "" : stageId;
            counters = counters == null ? Map.of() : Map.copyOf(counters);
            claimedRewardKeys = claimedRewardKeys == null ? Set.of() : Set.copyOf(claimedRewardKeys);
        }
    }

    public record ObjectiveView(
            String objectiveId,
            String text,
            int current,
            int required,
            boolean complete,
            boolean visible
    ) {
    }

    public record TransitionResult(boolean success, String message) {
        static TransitionResult ok(String message) {
            return new TransitionResult(true, message == null ? "" : message);
        }

        static TransitionResult failed(String message) {
            return new TransitionResult(false, message == null ? "" : message);
        }
    }

    private static final class Progress {
        private State state = State.AVAILABLE;
        private String stageId = "";
        private final Map<String, Integer> counters = new LinkedHashMap<>();
        private final Set<String> claimedRewardKeys = new LinkedHashSet<>();

        private Progress copy() {
            Progress copy = new Progress();
            copy.state = state;
            copy.stageId = stageId;
            copy.counters.putAll(counters);
            copy.claimedRewardKeys.addAll(claimedRewardKeys);
            return copy;
        }

        private Snapshot snapshot() {
            return new Snapshot(state, stageId, counters, claimedRewardKeys);
        }
    }

    private record ChoiceLocation(
            String flowKey,
            MapDesignLibrary.QuestFlowChoice choice
    ) {
    }

    private record RewardGrant(
            String claimKey,
            MapDesignLibrary.RewardDefinition reward
    ) {
    }

    private final GameState gameState;
    private final Map<String, MapDesignLibrary.AuthoredQuest> definitions = new LinkedHashMap<>();
    private final Map<String, Progress> progress = new LinkedHashMap<>();
    private final Set<String> automaticBlockKeys = new LinkedHashSet<>();
    private boolean transactionActive;
    private boolean refreshingAutomatic;

    public QuestRuntime(GameState gameState) {
        this.gameState = gameState;
    }

    public void setDefinitions(Collection<MapDesignLibrary.AuthoredQuest> quests) {
        definitions.clear();
        if (quests != null) {
            for (MapDesignLibrary.AuthoredQuest quest : quests) {
                if (quest != null && !quest.questId().isBlank()) {
                    definitions.put(quest.questId(), quest);
                }
            }
        }
        progress.keySet().retainAll(definitions.keySet());
    }

    /** Allocation-free fingerprint of the state displayed by the quest HUD. */
    public int presentationSignature() {
        int result = definitions.keySet().hashCode();
        for (Map.Entry<String, Progress> entry : progress.entrySet()) {
            Progress value = entry.getValue();
            result = 31 * result + entry.getKey().hashCode();
            result = 31 * result + value.state.ordinal();
            result = 31 * result + value.stageId.hashCode();
            result = 31 * result + value.counters.hashCode();
            result = 31 * result + value.claimedRewardKeys.hashCode();
        }
        return result;
    }

    public MapDesignLibrary.AuthoredQuest definition(String questId) {
        return questId == null ? null : definitions.get(questId);
    }

    public List<MapDesignLibrary.AuthoredQuest> definitions() {
        return List.copyOf(definitions.values());
    }

    public State state(String questId) {
        return state(questId, new LinkedHashSet<>());
    }

    private State state(String questId, Set<String> visiting) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        if (quest == null) {
            return null;
        }
        Progress saved = progress.get(questId);
        if (saved != null && saved.state != State.AVAILABLE) {
            return saved.state;
        }
        if (!visiting.add(questId)) {
            return null;
        }
        boolean available = requirementsSatisfied(quest.requirements(), visiting);
        visiting.remove(questId);
        return available ? State.AVAILABLE : null;
    }

    public boolean isVisibleAtNpc(String questId, String npcId) {
        State state = state(questId);
        if (state == null) {
            return false;
        }
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        return state != State.COMPLETED || quest != null && !quest.epilogueFlow().nodes().isEmpty();
    }

    public String currentStageId(String questId) {
        Progress value = progress.get(questId);
        return value == null ? "" : value.stageId;
    }

    public int currentStageIndex(String questId) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        if (quest == null) {
            return -1;
        }
        String stageId = currentStageId(questId);
        for (int index = 0; index < quest.stages().size(); index++) {
            if (quest.stages().get(index).stageId().equals(stageId)) {
                return index;
            }
        }
        return -1;
    }

    public MapDesignLibrary.QuestStage currentStage(String questId) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        int index = currentStageIndex(questId);
        return quest == null || index < 0 || index >= quest.stages().size()
                ? null
                : quest.stages().get(index);
    }

    public MapDesignLibrary.QuestFlow flowFor(String questId) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        State state = state(questId);
        if (quest == null || state == null) {
            return MapDesignLibrary.QuestFlow.empty();
        }
        if (state == State.AVAILABLE) {
            return quest.offerFlow();
        }
        if (state == State.COMPLETED) {
            return quest.epilogueFlow();
        }
        MapDesignLibrary.QuestStage stage = currentStage(questId);
        return stage == null ? MapDesignLibrary.QuestFlow.empty() : stage.flow();
    }

    public TransitionResult applyChoice(String questId, MapDesignLibrary.QuestFlowChoice submittedChoice) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        if (quest == null || submittedChoice == null) {
            return TransitionResult.failed("This quest choice is unavailable.");
        }
        ChoiceLocation location = locateCurrentChoice(questId, submittedChoice);
        if (location == null) {
            return TransitionResult.failed("That response is not part of the current quest flow.");
        }
        MapDesignLibrary.QuestFlowChoice choice = location.choice();
        if (!requirementsSatisfied(choice.conditions())) {
            return TransitionResult.failed("You do not meet the requirements for that response.");
        }
        if (!choice.requiredItemId().isBlank() && countItem(choice.requiredItemId()) < 1) {
            return TransitionResult.failed("You do not have the required item.");
        }

        Progress existing = progress.get(questId);
        Progress working = existing == null ? new Progress() : existing.copy();
        MapDesignLibrary.QuestStage stage = currentStage(questId);
        int stageIndex = currentStageIndex(questId);
        Map<String, Integer> consumption = new LinkedHashMap<>();
        if (!choice.takeItemId().isBlank()) {
            consumption.merge(choice.takeItemId(), choice.takeItemAmount(), Integer::sum);
        }
        String message = "";

        switch (choice.action()) {
            case NONE -> {
                // Dialogue-only choice; rewards and item removal still form one transaction.
            }
            case ACCEPT_QUEST -> {
                if (state(questId) != State.AVAILABLE || !location.flowKey().startsWith("offer:")) {
                    return TransitionResult.failed("That quest cannot be accepted from this response.");
                }
                if (quest.stages().isEmpty()) {
                    return TransitionResult.failed("This quest has no authored stages.");
                }
                working.state = State.ACTIVE;
                working.stageId = quest.stages().getFirst().stageId();
                message = "Quest started: " + quest.displayName();
            }
            case ADVANCE_STAGE -> {
                TransitionResult validity = validateStageTransition(
                        questId,
                        stage,
                        stageIndex,
                        false
                );
                if (!validity.success()) {
                    return validity;
                }
                collectTurnIns(stage, consumption);
                working.state = State.ACTIVE;
                working.stageId = quest.stages().get(stageIndex + 1).stageId();
                message = "Quest updated: " + quest.displayName();
            }
            case COMPLETE_QUEST -> {
                TransitionResult validity = validateStageTransition(
                        questId,
                        stage,
                        stageIndex,
                        true
                );
                if (!validity.success()) {
                    return validity;
                }
                collectTurnIns(stage, consumption);
                working.state = State.COMPLETED;
                working.stageId = "";
                message = "Quest complete: " + quest.displayName();
            }
        }

        List<RewardGrant> grants = new ArrayList<>();
        collectUnclaimedRewards(
                "quest:" + questId + ":" + location.flowKey() + ":choice:" + choice.choiceId(),
                choice.rewards(),
                working.claimedRewardKeys,
                grants
        );
        if (choice.action() == MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE
                || choice.action() == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST) {
            collectUnclaimedRewards(
                    "quest:" + questId + ":stage:" + stage.stageId(),
                    stage.rewards(),
                    working.claimedRewardKeys,
                    grants
            );
        }
        if (choice.action() == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST) {
            collectUnclaimedRewards(
                    "quest:" + questId + ":final",
                    quest.finalRewards(),
                    working.claimedRewardKeys,
                    grants
            );
        }

        TransitionResult result = executeTransaction(questId, working, consumption, grants, message);
        if (result.success()) {
            gameState.evaluateLiveQuestConditions();
        }
        return result;
    }

    public boolean choiceVisible(MapDesignLibrary.QuestFlowChoice choice) {
        return choice != null
                && requirementsSatisfied(choice.conditions())
                && (choice.requiredItemId().isBlank() || countItem(choice.requiredItemId()) >= 1)
                && (choice.takeItemId().isBlank()
                        || countItem(choice.takeItemId()) >= choice.takeItemAmount());
    }

    public TransitionResult accept(String questId) {
        MapDesignLibrary.QuestFlowChoice accept = flowFor(questId).nodes().stream()
                .flatMap(node -> node.choices().stream())
                .filter(choice -> choice.action() == MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST)
                .findFirst()
                .orElse(null);
        return accept == null
                ? TransitionResult.failed("This quest has no acceptance response.")
                : applyChoice(questId, accept);
    }

    public TransitionResult advance(String questId, boolean automatic) {
        if (!automatic) {
            MapDesignLibrary.QuestFlowChoice advance = flowFor(questId).nodes().stream()
                    .flatMap(node -> node.choices().stream())
                    .filter(choice -> choice.action() == MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE)
                    .findFirst()
                    .orElse(null);
            return advance == null
                    ? TransitionResult.failed("This stage has no advance response.")
                    : applyChoice(questId, advance);
        }
        return advanceAutomatic(questId);
    }

    public TransitionResult complete(String questId) {
        MapDesignLibrary.QuestFlowChoice complete = flowFor(questId).nodes().stream()
                .flatMap(node -> node.choices().stream())
                .filter(choice -> choice.action() == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST)
                .findFirst()
                .orElse(null);
        return complete == null
                ? TransitionResult.failed("This stage has no completion response.")
                : applyChoice(questId, complete);
    }

    public List<ObjectiveView> objectiveViews(String questId) {
        MapDesignLibrary.QuestStage stage = currentStage(questId);
        if (stage == null) {
            return List.of();
        }
        List<ObjectiveView> result = new ArrayList<>();
        for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
            int current = objectiveProgress(questId, objective);
            result.add(new ObjectiveView(
                    objective.objectiveId(),
                    objective.journalText().isBlank() ? defaultObjectiveText(objective) : objective.journalText(),
                    Math.min(current, objective.amount()),
                    objective.amount(),
                    current >= objective.amount(),
                    objective.visible()
            ));
        }
        return List.copyOf(result);
    }

    public void recordTalk(String npcId) {
        recordCounter(MapDesignLibrary.QuestObjectiveType.TALK_TO_NPC, npcId);
    }

    public void recordDefeat(String enemyId) {
        recordCounter(MapDesignLibrary.QuestObjectiveType.DEFEAT_ENEMY, enemyId);
    }

    public void recordDefeat(MapEntity enemy) {
        if (enemy == null) {
            return;
        }
        String enemyId = enemy.getMonster() != null && !enemy.getMonster().getCustomId().isBlank()
                ? enemy.getMonster().getCustomId()
                : enemy.getContentId();
        if (enemyId.isBlank() && enemy.getMonster() != null) {
            enemyId = enemy.getMonster().getName();
        }
        recordDefeat(enemyId);
    }

    private void recordCounter(MapDesignLibrary.QuestObjectiveType type, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        for (Map.Entry<String, Progress> entry : progress.entrySet()) {
            if (entry.getValue().state != State.ACTIVE) {
                continue;
            }
            MapDesignLibrary.QuestStage stage = currentStage(entry.getKey());
            if (stage == null) {
                continue;
            }
            for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
                if (objective.type() == type && targetId.equalsIgnoreCase(objective.targetId())) {
                    entry.getValue().counters.merge(objective.objectiveId(), 1, Integer::sum);
                }
            }
        }
        gameState.evaluateLiveQuestConditions();
    }

    public void refreshAutomaticProgression() {
        if (transactionActive || refreshingAutomatic) {
            return;
        }
        refreshingAutomatic = true;
        try {
            boolean advanced;
            int guard = 0;
            do {
                advanced = false;
                for (String questId : List.copyOf(progress.keySet())) {
                    MapDesignLibrary.QuestStage stage = currentStage(questId);
                    if (stage == null
                            || stage.completionMode() != MapDesignLibrary.QuestCompletionMode.AUTOMATIC
                            || !allObjectivesComplete(questId, stage)) {
                        continue;
                    }
                    TransitionResult result = advanceAutomatic(questId);
                    String blockKey = questId + ":" + stage.stageId();
                    if (result.success()) {
                        automaticBlockKeys.remove(blockKey);
                        advanced = true;
                    } else if (automaticBlockKeys.add(blockKey)) {
                        gameState.getWorldMessageLog().post(
                                WorldMessageLog.Category.WARNING,
                                result.message()
                        );
                    }
                }
            } while (advanced && ++guard < 100);
        } finally {
            refreshingAutomatic = false;
        }
    }

    private TransitionResult advanceAutomatic(String questId) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        MapDesignLibrary.QuestStage stage = currentStage(questId);
        int stageIndex = currentStageIndex(questId);
        if (quest == null || stage == null || state(questId) != State.ACTIVE) {
            return TransitionResult.failed("This quest is not active.");
        }
        if (stage.completionMode() != MapDesignLibrary.QuestCompletionMode.AUTOMATIC) {
            return TransitionResult.failed("This stage requires a conversation choice.");
        }
        if (!allObjectivesComplete(questId, stage)) {
            return TransitionResult.failed("The current quest objectives are not complete.");
        }

        Progress working = progress.get(questId).copy();
        boolean finalStage = stageIndex == quest.stages().size() - 1;
        Map<String, Integer> consumption = new LinkedHashMap<>();
        collectTurnIns(stage, consumption);
        List<RewardGrant> grants = new ArrayList<>();
        collectUnclaimedRewards(
                "quest:" + questId + ":stage:" + stage.stageId(),
                stage.rewards(),
                working.claimedRewardKeys,
                grants
        );
        if (finalStage) {
            collectUnclaimedRewards(
                    "quest:" + questId + ":final",
                    quest.finalRewards(),
                    working.claimedRewardKeys,
                    grants
            );
            working.state = State.COMPLETED;
            working.stageId = "";
        } else {
            working.stageId = quest.stages().get(stageIndex + 1).stageId();
        }
        return executeTransaction(
                questId,
                working,
                consumption,
                grants,
                finalStage
                        ? "Quest complete: " + quest.displayName()
                        : "Quest updated: " + quest.displayName()
        );
    }

    public Map<String, Snapshot> snapshots() {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        progress.forEach((id, value) -> result.put(id, value.snapshot()));
        return Map.copyOf(result);
    }

    public void restore(Map<String, Snapshot> snapshots) {
        progress.clear();
        if (snapshots == null) {
            return;
        }
        snapshots.forEach((questId, snapshot) -> {
            if (!definitions.containsKey(questId) || snapshot == null) {
                return;
            }
            Progress value = new Progress();
            value.state = snapshot.state();
            value.stageId = snapshot.stageId();
            value.counters.putAll(snapshot.counters());
            value.claimedRewardKeys.addAll(snapshot.claimedRewardKeys());
            normalizeProgress(questId, value);
            progress.put(questId, value);
        });
    }

    private ChoiceLocation locateCurrentChoice(
            String questId,
            MapDesignLibrary.QuestFlowChoice submitted
    ) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        State state = state(questId);
        if (quest == null || state == null) {
            return null;
        }
        String flowKey;
        if (state == State.AVAILABLE) {
            flowKey = "offer";
        } else if (state == State.COMPLETED) {
            flowKey = "epilogue";
        } else {
            flowKey = "stage:" + currentStageId(questId);
        }
        for (MapDesignLibrary.QuestFlowNode node : flowFor(questId).nodes()) {
            for (MapDesignLibrary.QuestFlowChoice choice : node.choices()) {
                boolean matches = !submitted.choiceId().isBlank()
                        ? submitted.choiceId().equals(choice.choiceId())
                        : submitted == choice;
                if (matches) {
                    return new ChoiceLocation(flowKey + ":node:" + node.nodeId(), choice);
                }
            }
        }
        return null;
    }

    private TransitionResult validateStageTransition(
            String questId,
            MapDesignLibrary.QuestStage stage,
            int stageIndex,
            boolean completing
    ) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        if (quest == null || stage == null || state(questId) != State.ACTIVE) {
            return TransitionResult.failed("This quest is not active.");
        }
        if (stage.completionMode() != MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED) {
            return TransitionResult.failed("Automatic stages cannot use dialogue progression actions.");
        }
        if (!allObjectivesComplete(questId, stage)) {
            return TransitionResult.failed("The current quest objectives are not complete.");
        }
        boolean finalStage = stageIndex == quest.stages().size() - 1;
        if (completing != finalStage) {
            return TransitionResult.failed(completing
                    ? "Only the final quest stage can complete the quest."
                    : "The final quest stage must use Complete Quest.");
        }
        return TransitionResult.ok("");
    }

    private TransitionResult executeTransaction(
            String questId,
            Progress working,
            Map<String, Integer> consumption,
            List<RewardGrant> grants,
            String successMessage
    ) {
        TransitionResult preflight = preflight(consumption, grants);
        if (!preflight.success()) {
            return preflight;
        }

        InventorySystem.Inventory inventory = gameState.getInventory();
        InventorySystem.Inventory.Snapshot inventorySnapshot = inventory.snapshot();
        Map<CharacterSkill, Integer> skillLevels = gameState.getPlayerCharacter().getSkillsView();
        Map<CharacterSkill, Integer> skillXp = gameState.getPlayerCharacter().getSkillExperienceView();
        transactionActive = true;
        try {
            for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
                if (!removeItem(entry.getKey(), entry.getValue())) {
                    throw new IllegalStateException("Required item removal failed: " + entry.getKey());
                }
            }
            for (RewardGrant grant : grants) {
                if (!grantReward(grant.reward())) {
                    throw new IllegalStateException("Reward grant failed: " + grant.reward().rewardId());
                }
                working.claimedRewardKeys.add(grant.claimKey());
            }
            progress.put(questId, working);
            return TransitionResult.ok(successMessage);
        } catch (RuntimeException exception) {
            inventory.restore(inventorySnapshot);
            for (CharacterSkill skill : CharacterSkill.values()) {
                gameState.getPlayerCharacter().setSkillLevel(skill, skillLevels.getOrDefault(skill, 0));
                gameState.getPlayerCharacter().setSkillExperience(skill, skillXp.getOrDefault(skill, 0));
            }
            return TransitionResult.failed("The quest transition could not be completed safely.");
        } finally {
            transactionActive = false;
        }
    }

    private TransitionResult preflight(
            Map<String, Integer> consumption,
            List<RewardGrant> grants
    ) {
        for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
            if (countItem(entry.getKey()) < entry.getValue()) {
                return TransitionResult.failed("You do not have enough " + entry.getKey() + ".");
            }
        }
        for (RewardGrant grant : grants) {
            MapDesignLibrary.RewardDefinition reward = grant.reward();
            if (reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP && reward.skill() == null) {
                return TransitionResult.failed("A skill XP reward has no skill assigned.");
            }
            if (reward.type() != MapDesignLibrary.QuestRewardType.SKILL_XP) {
                String itemId = reward.type() == MapDesignLibrary.QuestRewardType.GOLD
                        ? "GOLD"
                        : reward.itemId();
                if (gameState.createItemByNameOrId(itemId) == null) {
                    return TransitionResult.failed("Quest reward item is missing: " + itemId);
                }
            }
        }

        InventorySystem.Inventory inventory = gameState.getInventory();
        InventorySystem.Inventory.Snapshot snapshot = inventory.snapshot();
        try {
            for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
                if (!removeItem(entry.getKey(), entry.getValue())) {
                    return TransitionResult.failed("You do not have enough " + entry.getKey() + ".");
                }
            }
            for (RewardGrant grant : grants) {
                MapDesignLibrary.RewardDefinition reward = grant.reward();
                if (reward.type() != MapDesignLibrary.QuestRewardType.SKILL_XP
                        && !grantInventoryReward(reward)) {
                    return TransitionResult.failed(
                            "You need more inventory space before completing this quest transition."
                    );
                }
            }
            return TransitionResult.ok("");
        } finally {
            inventory.restore(snapshot);
        }
    }

    private boolean grantReward(MapDesignLibrary.RewardDefinition reward) {
        if (reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP) {
            if (reward.skill() == null) {
                return false;
            }
            gameState.getPlayerCharacter().addSkillExperience(reward.skill(), reward.amount());
            return true;
        }
        return grantInventoryReward(reward);
    }

    private boolean grantInventoryReward(MapDesignLibrary.RewardDefinition reward) {
        String itemId = reward.type() == MapDesignLibrary.QuestRewardType.GOLD
                ? "GOLD"
                : reward.itemId();
        InventorySystem.Item first = gameState.createItemByNameOrId(itemId);
        if (first == null) {
            return false;
        }
        if (first.isStackable()) {
            first.addQuantity(reward.amount() - 1);
            return gameState.getInventory().addItem(first);
        }
        if (!gameState.getInventory().addItem(first)) {
            return false;
        }
        for (int count = 1; count < reward.amount(); count++) {
            InventorySystem.Item next = gameState.createItemByNameOrId(itemId);
            if (next == null || !gameState.getInventory().addItem(next)) {
                return false;
            }
        }
        return true;
    }

    private void collectTurnIns(
            MapDesignLibrary.QuestStage stage,
            Map<String, Integer> consumption
    ) {
        for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
            if (objective.type() == MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM) {
                consumption.merge(objective.targetId(), objective.amount(), Integer::sum);
            }
        }
    }

    private void collectUnclaimedRewards(
            String owner,
            List<MapDesignLibrary.RewardDefinition> rewards,
            Set<String> claimed,
            List<RewardGrant> result
    ) {
        for (int index = 0; index < rewards.size(); index++) {
            MapDesignLibrary.RewardDefinition reward = rewards.get(index);
            String rewardId = reward.rewardId().isBlank() ? "reward_" + index : reward.rewardId();
            String claimKey = owner + ":reward:" + rewardId;
            if (!claimed.contains(claimKey)) {
                result.add(new RewardGrant(claimKey, reward));
            }
        }
    }

    private void normalizeProgress(String questId, Progress value) {
        MapDesignLibrary.AuthoredQuest quest = definition(questId);
        if (quest == null) {
            return;
        }
        if (value.state == State.ACTIVE
                && quest.stages().stream().noneMatch(stage -> stage.stageId().equals(value.stageId))) {
            value.stageId = quest.stages().isEmpty() ? "" : quest.stages().getFirst().stageId();
        }
    }

    private boolean requirementsSatisfied(List<MapDesignLibrary.QuestRequirement> requirements) {
        return requirementsSatisfied(requirements, new LinkedHashSet<>());
    }

    private boolean requirementsSatisfied(
            List<MapDesignLibrary.QuestRequirement> requirements,
            Set<String> visiting
    ) {
        if (requirements == null) {
            return true;
        }
        for (MapDesignLibrary.QuestRequirement requirement : requirements) {
            if (!requirementSatisfied(requirement, visiting)) {
                return false;
            }
        }
        return true;
    }

    private boolean requirementSatisfied(
            MapDesignLibrary.QuestRequirement requirement,
            Set<String> visiting
    ) {
        if (requirement == null || gameState.getPlayerCharacter() == null) {
            return false;
        }
        return switch (requirement.type()) {
            case POSSESS_ITEM -> countItem(requirement.targetId()) >= requirement.amount();
            case EQUIPPED_ITEM_OR_LIMB -> isEquipped(requirement.targetId());
            case COMPLETED_QUEST -> state(requirement.targetId(), visiting) == State.COMPLETED;
            case PLAYER_LEVEL -> gameState.getPlayerCharacter().getLevel() >= requirement.amount();
            case SKILL_LEVEL -> requirement.skill() != null
                    && gameState.getPlayerCharacter().getSkillLevel(requirement.skill()) >= requirement.amount();
        };
    }

    private boolean allObjectivesComplete(String questId, MapDesignLibrary.QuestStage stage) {
        for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
            if (objectiveProgress(questId, objective) < objective.amount()) {
                return false;
            }
        }
        return true;
    }

    private int objectiveProgress(String questId, MapDesignLibrary.QuestObjective objective) {
        if (objective == null || gameState.getPlayerCharacter() == null) {
            return 0;
        }
        return switch (objective.type()) {
            case POSSESS_ITEM, TURN_IN_ITEM -> countItem(objective.targetId());
            case EQUIPPED_ITEM_OR_LIMB -> isEquipped(objective.targetId()) ? objective.amount() : 0;
            case TALK_TO_NPC, DEFEAT_ENEMY -> progress.getOrDefault(questId, new Progress())
                    .counters.getOrDefault(objective.objectiveId(), 0);
            case PLAYER_LEVEL -> gameState.getPlayerCharacter().getLevel();
            case SKILL_LEVEL -> objective.skill() == null
                    ? 0
                    : gameState.getPlayerCharacter().getSkillLevel(objective.skill());
            case COMPLETE_QUEST -> state(objective.targetId()) == State.COMPLETED ? objective.amount() : 0;
        };
    }

    private int countItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        int count = gameState.getInventory().countItemByContentId(itemId);
        if (count > 0) {
            return count;
        }
        InventorySystem.Item resolved = gameState.createItemByNameOrId(itemId);
        return resolved == null ? 0 : gameState.getInventory().countItemNamed(resolved.getName());
    }

    private boolean removeItem(String itemId, int amount) {
        if (gameState.getInventory().countItemByContentId(itemId) >= amount) {
            return gameState.getInventory().removeItemQuantityByContentId(itemId, amount);
        }
        InventorySystem.Item resolved = gameState.createItemByNameOrId(itemId);
        return resolved != null && gameState.getInventory().removeItemQuantityNamed(resolved.getName(), amount);
    }

    private boolean isEquipped(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        for (InventorySystem.Item item : gameState.getInventory().getEquippedItemsView().values()) {
            if (item != null && itemId.equalsIgnoreCase(item.getContentId())) {
                return true;
            }
        }
        for (LimbItem limb : gameState.getPlayerCharacter().getEquippedLimbsView().values()) {
            if (limb != null && itemId.equalsIgnoreCase(limb.getContentId())) {
                return true;
            }
        }
        return false;
    }

    private String defaultObjectiveText(MapDesignLibrary.QuestObjective objective) {
        return switch (objective.type()) {
            case POSSESS_ITEM -> "Obtain " + objective.amount() + " × " + objective.targetId();
            case TURN_IN_ITEM -> "Turn in " + objective.amount() + " × " + objective.targetId();
            case EQUIPPED_ITEM_OR_LIMB -> "Equip " + objective.targetId();
            case TALK_TO_NPC -> "Talk to " + objective.targetId();
            case DEFEAT_ENEMY -> "Defeat " + objective.amount() + " × " + objective.targetId();
            case PLAYER_LEVEL -> "Reach player level " + objective.amount();
            case SKILL_LEVEL -> "Reach " + (objective.skill() == null ? "skill" : objective.skill().getDisplayName())
                    + " level " + objective.amount();
            case COMPLETE_QUEST -> "Complete " + objective.targetId();
        };
    }
}

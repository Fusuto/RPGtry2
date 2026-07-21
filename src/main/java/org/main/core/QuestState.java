package org.main.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class QuestState {
    private final Map<String, GameState.QuestDefinition> authoredQuestDefinitions = new HashMap<>();
    private final Map<String, Integer> questStages = new HashMap<>();
    private String selectedQuestId;

    public Map<String, GameState.QuestDefinition> getAuthoredQuestDefinitions() {
        return authoredQuestDefinitions;
    }

    public Map<String, Integer> getQuestStagesView() {
        return Collections.unmodifiableMap(questStages);
    }

    public Map<String, Integer> getQuestStagesMutable() {
        return questStages;
    }

    public int getQuestStage(String questId) {
        if (questId == null) {
            return 0;
        }
        return questStages.getOrDefault(questId, 0);
    }

    public void setQuestStage(String questId, int stage) {
        if (questId != null && !questId.isBlank()) {
            questStages.put(questId, Math.max(0, stage));
        }
    }

    public String getSelectedQuestId() {
        return selectedQuestId;
    }

    public void setSelectedQuestId(String selectedQuestId) {
        this.selectedQuestId = selectedQuestId;
    }

    public void registerQuestDefinition(GameState.QuestDefinition questDefinition) {
        if (questDefinition != null && questDefinition.id() != null) {
            authoredQuestDefinitions.put(questDefinition.id(), questDefinition);
        }
    }
}

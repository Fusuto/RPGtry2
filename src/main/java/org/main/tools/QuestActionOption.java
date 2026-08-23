package org.main.tools;

record QuestActionOption(String label, String questId) {
    @Override
    public String toString() {
        return label;
    }
}


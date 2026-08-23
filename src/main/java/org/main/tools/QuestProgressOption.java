package org.main.tools;

record QuestProgressOption(String label, String progressId) {
    @Override
    public String toString() {
        return label;
    }
}


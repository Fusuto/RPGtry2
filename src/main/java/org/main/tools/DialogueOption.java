package org.main.tools;

record DialogueOption(String label, String interactionId) {
    @Override
    public String toString() {
        return label;
    }
}


package org.main.tools;

enum ContentCategory {
    ALL("All"),
    MATERIALS("Materials"),
    ITEMS("Items"),
    ENEMIES("Enemies"),
    NPCS("NPCs"),
    FURNITURE("Furniture"),
    LIMBS("Limbs"),
    BATTLE_SKILLS("Battle Skills"),
    STATUSES("Statuses"),
    FIRST_PERSON_VIEWMODELS("First-Person Viewmodels"),
    GATHERING("Gathering"),
    COOKING("Cooking"),
    CRAFTING_RECIPES("Crafting Recipes"),
    QUESTS("Quests"),
    DIALOGUES("Dialogues"),
    AREAS("Mob Areas"),
    LIGHTS("Lights"),
    TRIGGERS("Triggers"),
    PLACEMENTS("Placements"),
    DIAGNOSTICS("Diagnostics");

    private final String label;

    ContentCategory(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}


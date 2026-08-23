package org.main.tools;

import org.main.content.MapDesignLibrary;

enum PlaceableCategory {
    ITEMS("Items"),
    ENEMIES("Enemies"),
    NPCS("NPCs"),
    GATHERING_NODES("Gathering Nodes"),
    FURNITURE("Furniture"),
    CRAFTING_NODES("Crafting Nodes"),
    INTERACTIONS("Interactions"),
    MAP_LINKS("Map Links");

    private final String label;

    PlaceableCategory(String label) {
        this.label = label;
    }

    boolean includes(PlaceableOption option) {
        if (option == null || option.kind() == null) {
            return false;
        }

        return switch (this) {
            case ITEMS -> option.kind() == MapDesignLibrary.PlacementKind.ITEM;
            case ENEMIES -> option.kind() == MapDesignLibrary.PlacementKind.ENEMY;
            case NPCS -> option.kind() == MapDesignLibrary.PlacementKind.CUSTOM_NPC;
            case GATHERING_NODES -> option.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE;
            case FURNITURE -> option.kind() == MapDesignLibrary.PlacementKind.FURNITURE;
            case CRAFTING_NODES -> option.kind() == MapDesignLibrary.PlacementKind.CRAFTING_NODE;
            case INTERACTIONS -> option.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                    && !option.id().startsWith("map_link|");
            case MAP_LINKS -> option.kind() == MapDesignLibrary.PlacementKind.INTERACTION
                    && option.id().startsWith("map_link|");
        };
    }

    @Override
    public String toString() {
        return label;
    }
}


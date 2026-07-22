package org.main.tools;

import org.main.content.MapDesignLibrary;
import org.main.core.CharacterSkill;
import org.main.core.Library;
import org.main.core.PlayerStat;
import org.main.engine.MapGeometryData;
import org.main.engine.MobAreaData;
import org.main.engine.MapPaintData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

enum PlaceableCategory {
    ITEMS("Items"),
    ENEMIES("Enemies"),
    NPCS("NPCs"),
    DIALOGUE_NPCS("Dialogue NPCs"),
    GATHERING_NODES("Gathering Nodes"),
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
            case NPCS -> option.kind() == MapDesignLibrary.PlacementKind.GENERIC_NPC
                    || option.kind() == MapDesignLibrary.PlacementKind.MAIN_NPC
                    || option.kind() == MapDesignLibrary.PlacementKind.CUSTOM_NPC;
            case DIALOGUE_NPCS -> option.kind() == MapDesignLibrary.PlacementKind.AUTHORED_DIALOGUE_NPC;
            case GATHERING_NODES -> option.kind() == MapDesignLibrary.PlacementKind.GATHERING_NODE;
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

record PlaceableOption(String label, MapDesignLibrary.PlacementKind kind, String id) {
    @Override
    public String toString() {
        return label;
    }
}

enum ContentCategory {
    ALL("All"),
    ITEMS("Items"),
    ENEMIES("Enemies"),
    NPCS("NPCs"),
    LIMBS("Limbs"),
    GATHERING("Gathering"),
    COOKING("Cooking"),
    CRAFTING_RECIPES("Crafting Recipes"),
    QUESTS("Quests"),
    DIALOGUES("Dialogues"),
    AREAS("Mob Areas"),
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

record ContentEntry(ContentCategory category, String label, String id, String type, Object value) {
    ContentEntry {
        label = label == null || label.isBlank() ? id : label;
        id = id == null ? "" : id;
        type = type == null ? "" : type;
    }

    String key() {
        return category.name() + "|" + type + "|" + id + "|" + System.identityHashCode(value);
    }

    String searchText() {
        return (category.label() + " " + label + " " + id + " " + type)
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return label;
    }
}

record MobAreaEntry(String areaId) {
    @Override
    public String toString() {
        return areaId;
    }
}

enum AssetBrowserType {
    ALL("All"),
    IMAGES("Images"),
    SOUNDS("Sounds"),
    MODELS("3D Models"),
    DATA("Data"),
    OTHER("Other");

    private final String label;

    AssetBrowserType(String label) {
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

record AssetBrowserEntry(String assetPath, AssetBrowserType type, Path sourcePath) {
    String searchText() {
        return (assetPath + " " + type.label()).toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return type.label() + ": " + assetPath;
    }
}

record FollowUpInteractionOption(String label, String interactionId) {
    @Override
    public String toString() {
        return label;
    }
}

record StatTargetOption(String label, PlayerStat stat) {
    @Override
    public String toString() {
        return label;
    }
}

record QuestActionOption(String label, String questId) {
    @Override
    public String toString() {
        return label;
    }
}

record TriggerActivationOption(String label, MapDesignLibrary.TriggerFireMode fireMode) {
    @Override
    public String toString() {
        return label;
    }
}

record TriggerSettings(
        String id,
        MapDesignLibrary.TriggerFireMode fireMode,
        boolean oneShot,
        String requiredQuestId,
        int requiredQuestStage
) {
}

record DialogueOption(String label, String interactionId) {
    @Override
    public String toString() {
        return label;
    }
}

record AuthoredDialogueDraft(
        String speakerName,
        String bodyText,
        String followUpInteractionId,
        String questId,
        int questStage,
        List<MapDesignLibrary.AuthoredDialogueChoice> choices,
        List<MapDesignLibrary.AuthoredDialogueNode> nodes
) {
}

record DialogueTreeDraft(
        List<MapDesignLibrary.AuthoredDialogueChoice> choices,
        List<MapDesignLibrary.AuthoredDialogueNode> nodes
) {
}

record PendingChoice(
        String label,
        String destination,
        String questId,
        int questStage,
        String requiredItemName,
        String takeItemName,
        String giveItemName,
        int giveGold,
        CharacterSkill giveSkill,
        int giveSkillXp,
        boolean firstTalkOnly
) {
}

record SkillXpTag(CharacterSkill skill, int amount) {
}

record PendingNode(String nodeId, List<String> bodyLines, List<PendingChoice> choices) {
    PendingNode(String nodeId) {
        this(nodeId, new ArrayList<>(), new ArrayList<>());
    }
}

record AuthoredQuestDraft(String displayName, List<String> stageDescriptions) {
}

record MapPrefab(
        String name,
        int width,
        int height,
        Library.TileType[][] tiles,
        int[][] themes,
        MapPaintData paintData,
        MapGeometryData geometryData,
        MobAreaData mobAreas,
        List<MapDesignLibrary.MapPlacement> placements,
        List<MapDesignLibrary.MapTrigger> triggers
) {
    MapPrefab {
        name = name == null || name.isBlank() ? "Prefab" : name;
        width = Math.max(1, width);
        height = Math.max(1, height);
        paintData = paintData == null ? MapPaintData.blank(width, height) : paintData;
        geometryData = geometryData == null ? MapGeometryData.blank(width, height) : geometryData;
        mobAreas = mobAreas == null ? MobAreaData.blank(width, height) : mobAreas;
        placements = placements == null ? List.of() : List.copyOf(placements);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + ")";
    }
}

record ContentGraph(String selectedLabel, List<String> dependencies, List<String> references) {
    ContentGraph {
        selectedLabel = selectedLabel == null || selectedLabel.isBlank() ? "Selected Content" : selectedLabel;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        references = references == null ? List.of() : List.copyOf(references);
    }
}

record DialogueTerminal(String sourceId, int choiceIndex, String label) {
}

enum PaintMode {
    TILE("Tile"),
    FLOOR_BRUSH("Floor Brush"),
    WALL_BRUSH("Wall Brush"),
    DOOR_BRUSH("Door Brush"),
    ROOF_BRUSH("Roof Brush"),
    MOB_AREA("Mob Area"),
    CLEAR_MOB_AREA("Clear Mob Area"),
    CLEAR_BRUSH("Clear Brushes"),
    SET_HEIGHT("Set Height"),
    PLACE_OBJECT("Place Object"),
    ERASE_OBJECT("Erase Object"),
    SET_SPAWN("Set Spawn"),
    PLACE_TRIGGER("Place Trigger"),
    WIRE_TRIGGER("Wire Trigger"),
    PLACE_PREFAB("Place Prefab");

    private final String label;

    PaintMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

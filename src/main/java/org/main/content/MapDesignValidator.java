package org.main.content;

import org.main.core.CraftingStationType;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.engine.MapGeometryData;
import org.main.engine.MapLight;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.experimental.CharacterAnimationMetadataResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.main.content.MapDesignLibrary.*;

final class MapDesignValidator {
    private MapDesignValidator() {
    }

    static List<ValidationIssue> validate(MapDesign design) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (design == null) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Map design is missing."));
            return issues;
        }

        if (design.width() < 3 || design.height() < 3) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Map dimensions must be at least 3x3."));
        }

        if (design.tiles() == null || design.tiles().length != design.height()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Tile rows do not match map height."));
            return issues;
        }

        if (isNotInside(design, design.spawnX(), design.spawnY())) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Spawn is outside the map."));
        } else if (design.tiles()[design.spawnY()][design.spawnX()].blocksMovement()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Spawn is on a blocking tile."));
        }

        List<AuthoredDialogue> authoredDialogues = design.authoredDialogues() == null
                ? List.of()
                : design.authoredDialogues();
        List<MapPlacement> placements = design.placements() == null
                ? List.of()
                : design.placements();

        for (int y = 0; y < design.height(); y++) {
            if (design.tiles()[y] == null || design.tiles()[y].length != design.width()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Tile row " + y + " does not match map width."));
            }

            if (design.themeIndexes() == null
                    || y >= design.themeIndexes().length
                    || design.themeIndexes()[y] == null
                    || design.themeIndexes()[y].length != design.width()) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Theme row " + y + " does not match map width."));
            }
        }
        validatePaintBrushes(design, issues);
        validateGeometry(design, issues);
        validateMobAreas(design, issues);

        for (MapPlacement placement : placements) {
            validatePlacement(design, issues, placement);
        }
        validatePlacedObjects(design, issues);
        validateTriggers(design, issues);
        validateLights(design, issues);
        validateAuthoredDialogueActions(issues, authoredDialogues, design.customItems(), design.customLimbs());
        validateCustomContent(issues, design);

        return issues;
    }

    static boolean hasValidationErrors(MapDesign design) {
        return validate(design).stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    static List<ValidationIssue> validateQuestDialogueContent(
            List<AuthoredQuest> authoredQuests,
            List<AuthoredDialogue> authoredDialogues,
            List<CustomNpc> customNpcs,
            List<CustomItem> customItems,
            List<CustomLimb> customLimbs,
            List<CustomMob> customMobs
    ) {
        List<AuthoredQuest> quests = authoredQuests == null ? List.of() : authoredQuests;
        List<AuthoredDialogue> dialogues = authoredDialogues == null ? List.of() : authoredDialogues;
        List<CustomNpc> npcs = customNpcs == null ? List.of() : customNpcs;
        List<CustomItem> items = customItems == null ? List.of() : customItems;
        List<CustomLimb> limbs = customLimbs == null ? List.of() : customLimbs;
        List<CustomMob> mobs = customMobs == null ? List.of() : customMobs;

        List<ValidationIssue> issues = new ArrayList<>();
        validateDuplicateIds(issues, "quest", quests.stream().map(AuthoredQuest::questId).toList());
        validateDuplicateIds(issues, "dialogue", dialogues.stream().map(AuthoredDialogue::interactionId).toList());
        validateAuthoredDialogueActions(issues, dialogues, items, limbs);
        validateQuestDefinitions(issues, quests, items, limbs, npcs, mobs);

        Set<String> questIds = quests.stream()
                .map(AuthoredQuest::questId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> dialogueIds = dialogues.stream()
                .map(AuthoredDialogue::interactionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (AuthoredDialogue dialogue : dialogues) {
            if (!dialogue.followUpInteractionId().isBlank()
                    && !dialogueIds.contains(dialogue.followUpInteractionId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId()
                                + " references missing follow-up dialogue "
                                + dialogue.followUpInteractionId() + "."
                ));
            }
        }
        for (CustomNpc npc : npcs) {
            if (!npc.interactionId().isBlank() && !dialogueIds.contains(npc.interactionId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Custom NPC " + npc.npcId() + " references missing dialogue "
                                + npc.interactionId() + "."
                ));
            }
            for (String questId : npc.questIds()) {
                if (!questIds.contains(questId)) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Custom NPC " + npc.npcId() + " references missing quest " + questId + "."
                    ));
                }
            }
        }
        return issues;
    }

    private static void validatePaintBrushes(MapDesign design, List<ValidationIssue> issues) {
        if (design.mapPaint() == null) {
            return;
        }

        for (int y = 0; y < design.height(); y++) {
            for (int x = 0; x < design.width(); x++) {
                for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
                    String brushId = design.mapPaint().get(layer, x, y);
                    if (!brushId.isBlank() && PaintBrushLibrary.find(brushId) == null) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.WARNING,
                                "Unknown " + layer.name().toLowerCase() + " brush '" + brushId + "' at " + x + "," + y + "."
                        ));
                    }
                }
            }
        }
    }

    private static void validateGeometry(MapDesign design, List<ValidationIssue> issues) {
        MapGeometryData geometry = design.mapGeometry();
        if (geometry == null) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Map geometry data is missing; height levels will default to 1."));
            return;
        }

        if (geometry.width() != design.width() || geometry.height() != design.height()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Map geometry dimensions do not match map dimensions."));
        }
    }

    private static void validateMobAreas(MapDesign design, List<ValidationIssue> issues) {
        MobAreaData areas = design.mobAreas();
        if (areas == null) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Mob Area data is missing; enemies will remain stationary."));
            return;
        }
        if (areas.width() != design.width() || areas.height() != design.height()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Mob Area rows do not match the map dimensions."));
            return;
        }

        for (MapPlacement placement : design.placements()) {
            if (placement == null || placement.kind() != PlacementKind.ENEMY
                    || isNotInside(design, placement.x(), placement.y())) {
                continue;
            }
            String areaId = areas.get(placement.x(), placement.y());
            if (areaId.isBlank()) {
                continue;
            }
            boolean hasWalkableTile = false;
            for (int y = 0; y < design.height() && !hasWalkableTile; y++) {
                for (int x = 0; x < design.width(); x++) {
                    if (areaId.equals(areas.get(x, y)) && !design.tiles()[y][x].blocksMovement()) {
                        hasWalkableTile = true;
                        break;
                    }
                }
            }
            if (!hasWalkableTile) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + placement.id() + " is assigned to completely unwalkable Mob Area '" + areaId + "'."
                ));
            }
        }
    }

    private static void validatePlacement(
            MapDesign design,
            List<ValidationIssue> issues,
            MapPlacement placement
    ) {
        if (placement == null || placement.kind() == null || placement.id() == null || placement.id().isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A placement is incomplete."));
            return;
        }

        if (isNotInside(design, placement.x(), placement.y())) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Placement " + placement.id() + " is outside the map."));
            return;
        }

        Library.TileType tile = design.tiles()[placement.y()][placement.x()];
        if (placement.kind() != PlacementKind.INTERACTION && tile.blocksMovement()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "Placement " + placement.id() + " is on a blocking tile and will convert it to floor at runtime."
            ));
        }

        try {
            switch (placement.kind()) {
                case CRAFTING_NODE -> CraftingStationType.valueOf(placement.id());
                case GATHERING_NODE -> {
                    if (MapDesignLibrary.findCustomGatheringNode(
                            placement.id(), design.customGatheringNodes()) == null) {
                        throw new IllegalArgumentException("Unknown gathering node");
                    }
                }
                case FURNITURE -> {
                    if (MapDesignLibrary.findCustomFurniture(placement.id(), design.customFurniture()) == null) {
                        throw new IllegalArgumentException("Unknown furniture");
                    }
                }
                case CUSTOM_NPC -> {
                    if (MapDesignLibrary.findCustomNpc(placement.id(), design.customNpcs()) == null) {
                        throw new IllegalArgumentException("Unknown custom NPC");
                    }
                }
                case ITEM -> {
                    if (MapDesignLibrary.findCustomItem(placement.id(), design.customItems()) == null
                            && MapDesignLibrary.findCustomLimb(placement.id(), design.customLimbs()) == null) {
                        throw new IllegalArgumentException("Unknown item");
                    }
                }
                case ENEMY -> {
                    if (MapDesignLibrary.findCustomMob(placement.id(), design.customMobs()) == null) {
                        throw new IllegalArgumentException("Unknown enemy");
                    }
                }
                case INTERACTION -> {
                    if (isMapLinkInteractionId(placement.id())) {
                        validateMapLinkInteraction(issues, placement.id());
                        break;
                    }

                    boolean knownInteraction = org.main.core.InteractionSystem.EDITOR_INTERACTIONS.stream()
                            .anyMatch(interaction -> interaction.interactionId().equals(placement.id()));
                    if (!knownInteraction) {
                        issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Interaction " + placement.id() + " is not a registered editor interaction."));
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "Placement " + placement.id() + " is not a valid " + placement.kind() + "."
            ));
        }
    }

    private static void validatePlacedObjects(MapDesign design, List<ValidationIssue> issues) {
        List<PlacedObjectInstance> placedObjects = design.placedObjects() == null
                ? List.of()
                : design.placedObjects();
        List<String> instanceIds = new ArrayList<>();

        for (PlacedObjectInstance object : placedObjects) {
            if (object == null || object.instanceId().isBlank()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A placed object is missing an instance id."));
                continue;
            }
            if (containsIgnoreCase(instanceIds, object.instanceId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Placed object instance id " + object.instanceId() + " is duplicated."
                ));
            }
            instanceIds.add(object.instanceId());

            if (isNotInside(design, object.x(), object.y())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Placed object " + object.instanceId() + " is outside the map."
                ));
                continue;
            }

            if (object.lightOverride() != null
                    && object.lightOverride().enabled()
                    && object.lightOverride().intensity() <= 0.0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Placed object " + object.instanceId() + " has an enabled attached light with no intensity."
                ));
            }

            switch (object.kind()) {
                case FURNITURE -> {
                    if (MapDesignLibrary.findCustomFurniture(object.id(), design.customFurniture()) == null) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Placed object " + object.instanceId() + " references missing furniture " + object.id() + "."
                        ));
                    }
                }
                case GATHERING_NODE -> {
                    CustomGatheringNode node = MapDesignLibrary.findCustomGatheringNode(
                            object.id(),
                            design.customGatheringNodes());
                    if (node == null) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Placed object " + object.instanceId() + " references missing gathering node "
                                        + object.id() + "."
                        ));
                    } else if (node.modelPaths().isEmpty()) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.WARNING,
                                "Placed object " + object.instanceId() + " uses gathering node " + object.id()
                                        + " without 3D model states."
                        ));
                    }
                }
                default -> issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Placed object " + object.instanceId() + " uses unsupported kind " + object.kind() + "."
                ));
            }
        }
    }

    private static boolean isMapLinkInteractionId(String interactionId) {
        return interactionId != null && interactionId.startsWith("map_link|");
    }

    private static void validateMapLinkInteraction(List<ValidationIssue> issues, String interactionId) {
        String[] parts = interactionId.split("\\|", -1);
        if (parts.length != 4
                || parts[1].isBlank()
                || isNotInteger(parts[2])
                || isNotInteger(parts[3])) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "Map link interaction " + interactionId + " is malformed. Expected map_link|targetPath|x|y."
            ));
        }
    }

    private static boolean isNotInteger(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            Integer.parseInt(value);
            return false;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private static void validateTriggers(MapDesign design, List<ValidationIssue> issues) {
        List<MapTrigger> triggers = design.triggers() == null ? List.of() : design.triggers();
        List<String> ids = new ArrayList<>();

        for (MapTrigger trigger : triggers) {
            if (trigger == null || trigger.id().isBlank()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A trigger is missing an id."));
                continue;
            }

            if (ids.contains(trigger.id())) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Trigger id " + trigger.id() + " is duplicated."));
            }
            ids.add(trigger.id());

            if (isNotInside(design, trigger.x(), trigger.y())) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Trigger " + trigger.id() + " is outside the map."));
            }

            if (trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_PROGRESS) {
                AuthoredQuest requiredQuest = design.authoredQuests().stream()
                        .filter(quest -> quest.questId().equals(trigger.requiredQuestId()))
                        .findFirst()
                        .orElse(null);
                if (trigger.requiredQuestId().isBlank() || requiredQuest == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Trigger " + trigger.id() + " references missing quest " + trigger.requiredQuestId() + "."
                    ));
                } else if (!"ACTIVE".equals(trigger.requiredQuestProgress())
                        && !"COMPLETED".equals(trigger.requiredQuestProgress())
                        && (!trigger.requiredQuestProgress().startsWith("STAGE:")
                        || requiredQuest.stages().stream().noneMatch(stage ->
                        ("STAGE:" + stage.stageId()).equals(trigger.requiredQuestProgress())))) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Trigger " + trigger.id() + " references missing quest progress "
                                    + trigger.requiredQuestProgress() + "."
                    ));
                }
            }

            for (TriggerAction action : trigger.actions()) {
                if (action == null) {
                    continue;
                }
                if (isNotInside(design, action.targetX(), action.targetY())) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Trigger " + trigger.id() + " targets outside the map."));
                    continue;
                }

                if (!isDoorTile(design.tiles()[action.targetY()][action.targetX()])) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Trigger " + trigger.id() + " targets " + action.targetX() + "," + action.targetY() + ", which is not a door tile."
                    ));
                }
            }
        }
    }

    private static void validateLights(MapDesign design, List<ValidationIssue> issues) {
        List<MapLight> lights = design.lights() == null ? List.of() : design.lights();
        List<String> ids = new ArrayList<>();

        for (MapLight light : lights) {
            if (light == null || light.id().isBlank()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A light is missing an id."));
                continue;
            }
            if (ids.contains(light.id())) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Light id " + light.id() + " is duplicated."));
            }
            ids.add(light.id());

            if (isNotInside(design, light.x(), light.y())) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Light " + light.id() + " is outside the map."));
            }
            if (light.radius() <= 0.0) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Light " + light.id() + " has no visible radius."));
            }
            if (light.enabled() && light.intensity() <= 0.0) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Light " + light.id() + " is enabled but has no intensity."));
            }
        }
    }

    private static boolean isDoorTile(Library.TileType tile) {
        return tile == Library.TileType.DOOR_OPEN
                || tile == Library.TileType.DOOR_CLOSED
                || tile == Library.TileType.QUEST_DOOR_OPEN
                || tile == Library.TileType.QUEST_DOOR_CLOSED;
    }

    private static void validateAuthoredDialogueActions(
            List<ValidationIssue> issues,
            List<AuthoredDialogue> authoredDialogues,
            List<CustomItem> customItems,
            List<CustomLimb> customLimbs
    ) {
        List<String> knownItemNames = knownItemIdsAndNames(customItems, customLimbs);
        Set<String> itemIds = new LinkedHashSet<>();
        customItems.forEach(item -> itemIds.add(item.itemId()));
        customLimbs.forEach(limb -> itemIds.add(limb.limbId()));

        for (AuthoredDialogue dialogue : authoredDialogues) {
            validateQuestRewards(
                    issues,
                    "Authored dialogue " + dialogue.interactionId(),
                    dialogue.rewards(),
                    itemIds
            );

            List<String> nodeIds = dialogue.nodes().stream().map(AuthoredDialogueNode::nodeId).toList();
            if (!dialogue.firstTalkNodeId().isBlank()
                    && !"start".equals(dialogue.firstTalkNodeId())
                    && !nodeIds.contains(dialogue.firstTalkNodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " has a missing first-talk entry."));
            }
            if (!dialogue.repeatTalkNodeId().isBlank()
                    && !"start".equals(dialogue.repeatTalkNodeId())
                    && !nodeIds.contains(dialogue.repeatTalkNodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " has a missing repeat-talk entry."));
            }
            Set<String> choiceIds = new LinkedHashSet<>();
            validateAuthoredDialogueChoices(
                    issues, dialogue, dialogue.choices(), nodeIds, knownItemNames, itemIds, choiceIds);
            for (AuthoredDialogueNode node : dialogue.nodes()) {
                validateAuthoredDialogueChoices(
                        issues, dialogue, node.choices(), nodeIds, knownItemNames, itemIds, choiceIds);
            }
        }
    }

    private static List<String> knownItemIdsAndNames(List<CustomItem> customItems, List<CustomLimb> customLimbs) {
        List<String> values = new ArrayList<>();
        if (customItems != null) {
            for (CustomItem item : customItems) {
                values.add(item.itemId());
                values.add(item.displayName());
            }
        }
        if (customLimbs != null) {
            for (CustomLimb limb : customLimbs) {
                values.add(limb.limbId());
                values.add(limb.displayName());
            }
        }
        return values;
    }

    private static void validateAuthoredDialogueChoices(
            List<ValidationIssue> issues,
            AuthoredDialogue dialogue,
            List<AuthoredDialogueChoice> choices,
            List<String> nodeIds,
            List<String> knownItemNames,
            Set<String> itemIds,
            Set<String> choiceIds
    ) {
        for (AuthoredDialogueChoice choice : choices) {
            if (choice.choiceId().isBlank() || !choiceIds.add(choice.choiceId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId()
                                + " has a blank or duplicate choice ID."));
            }
            if (!choice.targetNodeId().isBlank()
                    && !"start".equals(choice.targetNodeId())
                    && !nodeIds.contains(choice.targetNodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " points to missing node " + choice.targetNodeId() + "."
                ));
            }
            if (!choice.requiredItemName().isBlank() && !containsIgnoreCase(knownItemNames, choice.requiredItemName())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " requires unknown item " + choice.requiredItemName() + "."
                ));
            }
            if (!choice.takeItemName().isBlank() && !containsIgnoreCase(knownItemNames, choice.takeItemName())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " takes unknown item " + choice.takeItemName() + "."
                ));
            }
            validateQuestRewards(
                    issues,
                    "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label(),
                    choice.rewards(),
                    itemIds
            );
        }
    }

    private static void validateQuestDefinitions(List<ValidationIssue> issues, MapDesign design) {
        validateQuestDefinitions(
                issues,
                design.authoredQuests(),
                design.customItems(),
                design.customLimbs(),
                design.customNpcs(),
                design.customMobs()
        );
    }

    private static void validateQuestDefinitions(
            List<ValidationIssue> issues,
            List<AuthoredQuest> authoredQuests,
            List<CustomItem> customItems,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs,
            List<CustomMob> customMobs
    ) {
        Set<String> itemIds = new LinkedHashSet<>();
        customItems.forEach(item -> itemIds.add(item.itemId()));
        customLimbs.forEach(limb -> itemIds.add(limb.limbId()));
        Set<String> npcIds = customNpcs.stream()
                .map(CustomNpc::npcId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> enemyIds = customMobs.stream()
                .map(CustomMob::mobId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> questIds = authoredQuests.stream()
                .map(AuthoredQuest::questId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, Set<String>> prerequisites = new LinkedHashMap<>();

        for (AuthoredQuest quest : authoredQuests) {
            String owner = "Quest " + quest.questId();
            Set<String> requiredQuests = new LinkedHashSet<>();
            validateQuestRequirements(issues, owner, quest.requirements(), itemIds, questIds, requiredQuests);
            prerequisites.put(quest.questId(), requiredQuests);
            validateQuestFlow(issues, owner + " offer", quest.offerFlow(), true,
                    itemIds, questIds, requiredQuests);

            if (quest.stages().isEmpty()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, owner + " has no journal stages."));
            }
            Set<String> offerNodeIds = quest.offerFlow().nodes().stream()
                    .map(MapDesignLibrary.QuestFlowNode::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<Set<String>, String> flowOwnersByNodeSet = new LinkedHashMap<>();
            if (offerNodeIds.size() > 1) {
                flowOwnersByNodeSet.put(Set.copyOf(offerNodeIds), "offer");
            }
            Set<String> stageIds = new LinkedHashSet<>();
            Set<String> objectiveIds = new LinkedHashSet<>();
            for (int stageIndex = 0; stageIndex < quest.stages().size(); stageIndex++) {
                MapDesignLibrary.QuestStage stage = quest.stages().get(stageIndex);
                String stageOwner = owner + " stage " + stage.stageId();
                Set<String> stageNodeIds = stage.flow().nodes().stream()
                        .map(MapDesignLibrary.QuestFlowNode::nodeId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (stageNodeIds.size() > 1) {
                    String duplicateOwner = flowOwnersByNodeSet.putIfAbsent(
                            Set.copyOf(stageNodeIds),
                            "stage " + stage.stageId()
                    );
                    if (duplicateOwner != null) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                stageOwner + " duplicates the complete " + duplicateOwner
                                        + " graph instead of owning only its stage-local dialogue."
                        ));
                    }
                }
                if (stage.stageId().isBlank() || !stageIds.add(stage.stageId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, stageOwner + " has a blank or duplicate stable ID."));
                }
                if (stage.title().isBlank() || stage.journalText().isBlank()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING, stageOwner + " has incomplete journal text."));
                }
                for (MapDesignLibrary.QuestObjective objective : stage.objectives()) {
                    if (objective.objectiveId().isBlank() || !objectiveIds.add(objective.objectiveId())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                stageOwner + " has a blank or duplicate objective ID."));
                    }
                    validateQuestObjective(issues, stageOwner, objective,
                            itemIds, npcIds, enemyIds, questIds);
                }
                validateQuestRewards(issues, stageOwner, stage.rewards(), itemIds);
                validateQuestFlow(issues, stageOwner + " flow", stage.flow(), false,
                        itemIds, questIds, requiredQuests);
                validateStageProgressionActions(
                        issues,
                        stageOwner,
                        stage,
                        stageIndex == quest.stages().size() - 1
                );
            }
            validateQuestRewards(issues, owner, quest.finalRewards(), itemIds);
            validateQuestFlow(issues, owner + " epilogue", quest.epilogueFlow(), false,
                    itemIds, questIds, requiredQuests);
            validateEpilogueActions(issues, owner, quest.epilogueFlow());
            Set<String> epilogueNodeIds = quest.epilogueFlow().nodes().stream()
                    .map(MapDesignLibrary.QuestFlowNode::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (epilogueNodeIds.size() > 1) {
                String duplicateOwner = flowOwnersByNodeSet.get(Set.copyOf(epilogueNodeIds));
                if (duplicateOwner != null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " epilogue duplicates the complete " + duplicateOwner
                                    + " graph instead of containing only completed-state dialogue."
                    ));
                }
            }
        }

        for (String questId : prerequisites.keySet()) {
            detectQuestCycle(questId, prerequisites, new LinkedHashSet<>(), new LinkedHashSet<>(), issues);
        }
    }

    private static void validateQuestRequirements(
            List<ValidationIssue> issues,
            String owner,
            List<MapDesignLibrary.QuestRequirement> requirements,
            Set<String> itemIds,
            Set<String> questIds,
            Set<String> requiredQuests
    ) {
        for (MapDesignLibrary.QuestRequirement requirement : requirements) {
            switch (requirement.type()) {
                case POSSESS_ITEM, EQUIPPED_ITEM_OR_LIMB -> {
                    if (!itemIds.contains(requirement.targetId())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                owner + " references unavailable item or limb " + requirement.targetId() + "."));
                    }
                }
                case COMPLETED_QUEST -> {
                    requiredQuests.add(requirement.targetId());
                    if (!questIds.contains(requirement.targetId())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                owner + " references unavailable prerequisite quest "
                                        + requirement.targetId() + "."));
                    }
                }
                case SKILL_LEVEL -> {
                    if (requirement.skill() == null) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR, owner + " has a skill-level requirement without a skill."));
                    }
                }
                case PLAYER_LEVEL -> {
                    // The authored amount is sufficient for this requirement.
                }
            }
        }
    }

    private static void validateQuestObjective(
            List<ValidationIssue> issues,
            String owner,
            MapDesignLibrary.QuestObjective objective,
            Set<String> itemIds,
            Set<String> npcIds,
            Set<String> enemyIds,
            Set<String> questIds
    ) {
        boolean valid = switch (objective.type()) {
            case POSSESS_ITEM, TURN_IN_ITEM, EQUIPPED_ITEM_OR_LIMB -> itemIds.contains(objective.targetId());
            case TALK_TO_NPC -> npcIds.contains(objective.targetId());
            case DEFEAT_ENEMY -> enemyIds.contains(objective.targetId());
            case COMPLETE_QUEST -> questIds.contains(objective.targetId());
            case SKILL_LEVEL -> objective.skill() != null;
            case PLAYER_LEVEL -> true;
        };
        if (!valid) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " objective " + objective.objectiveId()
                            + " has an unavailable or incomplete target."));
        }
    }

    private static void validateQuestRewards(
            List<ValidationIssue> issues,
            String owner,
            List<MapDesignLibrary.RewardDefinition> rewards,
            Set<String> itemIds
    ) {
        Set<String> rewardIds = new LinkedHashSet<>();
        for (MapDesignLibrary.RewardDefinition reward : rewards) {
            if (reward.rewardId().isBlank()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, owner + " reward row is missing a stable ID."));
            } else if (!rewardIds.add(reward.rewardId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, owner + " has duplicate reward ID "
                        + reward.rewardId() + "."));
            }
            if (reward.type() == MapDesignLibrary.QuestRewardType.ITEM
                    && !itemIds.contains(reward.itemId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, owner + " rewards unavailable item or limb "
                        + reward.itemId() + "."));
            }
            if (reward.type() == MapDesignLibrary.QuestRewardType.SKILL_XP && reward.skill() == null) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, owner + " has a skill-XP reward without a skill."));
            }
        }
    }

    private static void validateQuestFlow(
            List<ValidationIssue> issues,
            String owner,
            MapDesignLibrary.QuestFlow flow,
            boolean offer,
            Set<String> itemIds,
            Set<String> questIds,
            Set<String> requiredQuests
    ) {
        if (flow == null || flow.nodes().isEmpty()) {
            if (offer) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, owner + " has no dialogue nodes."));
            }
            return;
        }
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> choiceIds = new LinkedHashSet<>();
        int acceptActions = 0;
        for (MapDesignLibrary.QuestFlowNode node : flow.nodes()) {
            if (node.nodeId().isBlank() || !nodeIds.add(node.nodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, owner + " has a blank or duplicate node ID."));
            }
        }
        if (!nodeIds.contains(flow.entryNodeId())) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, owner + " points to a missing entry node."));
        }
        for (MapDesignLibrary.QuestFlowNode node : flow.nodes()) {
            for (MapDesignLibrary.QuestFlowChoice choice : node.choices()) {
                if (choice.choiceId().isBlank() || !choiceIds.add(choice.choiceId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " has a blank or duplicate choice ID."));
                }
                if (!choice.targetNodeId().isBlank() && !nodeIds.contains(choice.targetNodeId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " choice " + choice.label() + " points to missing node "
                                    + choice.targetNodeId() + "."));
                }
                if (choice.action() == MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST) {
                    acceptActions++;
                    if (!offer) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                owner + " contains ACCEPT_QUEST outside the offer flow."));
                    }
                }
                if (offer
                        && choice.action() != MapDesignLibrary.QuestFlowAction.NONE
                        && choice.action() != MapDesignLibrary.QuestFlowAction.ACCEPT_QUEST) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " contains a stage progression action in the offer flow."));
                }
                if (choice.action() != MapDesignLibrary.QuestFlowAction.NONE
                        && !choice.targetNodeId().isBlank()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " choice " + choice.label()
                                    + " cannot combine a progression action with an arrow target."));
                }
                if (choice.action() == MapDesignLibrary.QuestFlowAction.NONE
                        && !choice.targetNodeId().isBlank()
                        && !choice.terminalBodyText().isBlank()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " choice " + choice.label()
                                    + " cannot have both an arrow target and terminal response."));
                }
                validateQuestRequirements(
                        issues, owner + " choice " + choice.label(), choice.conditions(),
                        itemIds, questIds, requiredQuests);
                if (!choice.requiredItemId().isBlank() && !itemIds.contains(choice.requiredItemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " choice " + choice.label() + " requires unavailable item "
                                    + choice.requiredItemId() + "."));
                }
                if (!choice.takeItemId().isBlank() && !itemIds.contains(choice.takeItemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " choice " + choice.label() + " consumes unavailable item "
                                    + choice.takeItemId() + "."));
                }
                validateQuestRewards(
                        issues,
                        owner + " choice " + choice.label(),
                        choice.rewards(),
                        itemIds
                );
            }
        }
        if (offer && acceptActions == 0) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, owner + " has no explicit ACCEPT_QUEST choice."));
        }
    }

    private static void validateStageProgressionActions(
            List<ValidationIssue> issues,
            String owner,
            MapDesignLibrary.QuestStage stage,
            boolean finalStage
    ) {
        if (stage.completionMode() == MapDesignLibrary.QuestCompletionMode.FLOW_CONFIRMED
                && stage.flow().nodes().isEmpty()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " is flow-confirmed but has no entry dialogue node."
            ));
        }
        List<MapDesignLibrary.QuestFlowAction> actions = stage.flow().nodes().stream()
                .flatMap(node -> node.choices().stream())
                .map(MapDesignLibrary.QuestFlowChoice::action)
                .filter(action -> action != MapDesignLibrary.QuestFlowAction.NONE)
                .toList();
        if (stage.completionMode() == MapDesignLibrary.QuestCompletionMode.AUTOMATIC) {
            if (!actions.isEmpty()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        owner + " is automatic but contains authored progression actions."));
            }
            return;
        }
        MapDesignLibrary.QuestFlowAction expected = finalStage
                ? MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST
                : MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE;
        Set<String> turnInItemIds = stage.objectives().stream()
                .filter(objective -> objective.type() == MapDesignLibrary.QuestObjectiveType.TURN_IN_ITEM)
                .map(MapDesignLibrary.QuestObjective::targetId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (MapDesignLibrary.QuestFlowChoice choice : stage.flow().nodes().stream()
                .flatMap(node -> node.choices().stream())
                .filter(choice -> choice.action() == MapDesignLibrary.QuestFlowAction.ADVANCE_STAGE
                        || choice.action() == MapDesignLibrary.QuestFlowAction.COMPLETE_QUEST)
                .toList()) {
            if (!choice.takeItemId().isBlank() && turnInItemIds.contains(choice.takeItemId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        owner + " progression choice " + choice.label()
                                + " consumes " + choice.takeItemId()
                                + " twice: once on the choice and once through a TURN_IN_ITEM objective."
                ));
            }
        }
        if (actions.stream().noneMatch(action -> action == expected)) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " requires a " + expected + " choice."));
        }
        for (MapDesignLibrary.QuestFlowAction action : actions) {
            if (action != expected) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        owner + " contains incompatible progression action " + action + "."));
            }
        }
    }

    private static void validateEpilogueActions(
            List<ValidationIssue> issues,
            String owner,
            MapDesignLibrary.QuestFlow epilogue
    ) {
        boolean hasAction = epilogue.nodes().stream()
                .flatMap(node -> node.choices().stream())
                .anyMatch(choice -> choice.action() != MapDesignLibrary.QuestFlowAction.NONE);
        if (hasAction) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " epilogue cannot mutate quest state."));
        }
    }

    private static void detectQuestCycle(
            String questId,
            Map<String, Set<String>> prerequisites,
            Set<String> visiting,
            Set<String> visited,
            List<ValidationIssue> issues
    ) {
        if (visited.contains(questId)) {
            return;
        }
        if (!visiting.add(questId)) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "Quest prerequisite cycle includes " + questId + "."));
            return;
        }
        for (String dependency : prerequisites.getOrDefault(questId, Set.of())) {
            if (prerequisites.containsKey(dependency)) {
                detectQuestCycle(dependency, prerequisites, visiting, visited, issues);
            }
        }
        visiting.remove(questId);
        visited.add(questId);
    }

    private static void validateCustomContent(List<ValidationIssue> issues, MapDesign design) {
        validateDuplicateIds(issues, "item", design.customItems().stream().map(CustomItem::itemId).toList());
        validateDuplicateIds(issues, "enemy", design.customMobs().stream().map(CustomMob::mobId).toList());
        validateDuplicateIds(issues, "NPC", design.customNpcs().stream().map(CustomNpc::npcId).toList());
        validateDuplicateIds(issues, "furniture", design.customFurniture().stream().map(CustomFurnitureDefinition::furnitureId).toList());
        validateDuplicateIds(issues, "limb", design.customLimbs().stream().map(CustomLimb::limbId).toList());
        validateDuplicateIds(issues, "gathering node", design.customGatheringNodes().stream().map(CustomGatheringNode::nodeId).toList());
        validateDuplicateIds(issues, "cooking recipe", design.customCookingRecipes().stream().map(CustomCookingRecipe::recipeId).toList());
        validateDuplicateIds(issues, "crafting recipe", design.craftingRecipes().stream().map(CraftingRecipe::recipeId).toList());
        validateDuplicateIds(issues, "quest", design.authoredQuests().stream().map(AuthoredQuest::questId).toList());
        validateDuplicateIds(issues, "dialogue", design.authoredDialogues().stream().map(AuthoredDialogue::interactionId).toList());
        validateQuestDefinitions(issues, design);
        validateButcheryProducts(issues, design);

        List<String> knownItems = knownItemIdsAndNames(design.customItems(), design.customLimbs());
        List<String> dialogueIds = design.authoredDialogues().stream().map(AuthoredDialogue::interactionId).toList();

        for (CustomNpc npc : design.customNpcs()) {
            validateAssetPath(issues, "Custom NPC " + npc.npcId(), "sprite", npc.imagePath(),
                    npc.characterModel() == null || !npc.characterModel().hasModel());
            validateAssetPath(issues, "Custom NPC " + npc.npcId(), "talk sound", npc.talkSoundPath(), false);
            validateCharacterModel(issues, "Custom NPC " + npc.npcId(), npc.characterModel());
            if (!npc.interactionId().isBlank() && !dialogueIds.contains(npc.interactionId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Custom NPC " + npc.npcId() + " references missing dialogue " + npc.interactionId() + "."
                ));
            }
            for (String questId : npc.questIds()) {
                if (design.authoredQuests().stream().noneMatch(quest -> quest.questId().equals(questId))) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Custom NPC " + npc.npcId() + " references missing quest " + questId + "."
                    ));
                }
            }
            if (npc.shop() != null) {
                if (npc.shop().stock().isEmpty()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Shop NPC " + npc.npcId() + " has no stock; it will only buy items from the player."
                    ));
                }
                for (MapDesignLibrary.CustomShopStock stock : npc.shop().stock()) {
                    if (!containsIgnoreCase(knownItems, stock.itemId())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Shop NPC " + npc.npcId() + " references unknown item " + stock.itemId() + "."
                        ));
                    }
                    if (stock.quantity() == 0 || stock.quantity() < -1) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Shop NPC " + npc.npcId() + " has invalid stock quantity for " + stock.itemId() + "."
                        ));
                    }
                    if (stock.buyPrice() < -1 || stock.sellPrice() < -1) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Shop NPC " + npc.npcId() + " has an invalid price for " + stock.itemId() + "."
                        ));
                    }
                }
            }
        }

        for (CustomFurnitureDefinition furniture : design.customFurniture()) {
            validateModelAssetPath(issues, "Furniture " + furniture.furnitureId(), "model", furniture.modelPath(), true);
            if (!furniture.interactionId().isBlank() && !dialogueIds.contains(furniture.interactionId())) {
                boolean knownInteraction = org.main.core.InteractionSystem.EDITOR_INTERACTIONS.stream()
                        .anyMatch(interaction -> interaction.interactionId().equals(furniture.interactionId()));
                if (!knownInteraction && !isMapLinkInteractionId(furniture.interactionId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Furniture " + furniture.furnitureId() + " references unknown interaction "
                                    + furniture.interactionId() + "."
                    ));
                }
            }
            if (furniture.lightAttachment() != null && furniture.lightAttachment().enabled()
                    && furniture.lightAttachment().intensity() <= 0.0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Furniture " + furniture.furnitureId() + " has an enabled attached light with no intensity."
                ));
            }
        }

        for (CustomMob mob : design.customMobs()) {
            for (String skillId : mob.skillIds()) {
                if (BattleContentCatalog.findSkill(skillId) == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Enemy " + mob.mobId() + " references unknown battle skill " + skillId + "."));
                }
            }
            if (mob.combatAiIntelligence() < 0 || mob.combatAiIntelligence() > 10) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + mob.mobId() + " has Combat AI Intelligence outside 0-10."
                ));
            }
            if (mob.awarenessRadius() < 0 || mob.awarenessRadius() > 64) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + mob.mobId() + " has an invalid awareness radius."
                ));
            }
            if (mob.movementIntervalMs() < 250) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + mob.mobId() + " has an invalid movement interval."
                ));
            }
            if (mob.respawnDelayMs() < 0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + mob.mobId() + " has an invalid respawn delay."
                ));
            }
            validateAssetPath(issues, "Enemy " + mob.mobId(), "sprite", mob.imagePath(),
                    mob.characterModel() == null || !mob.characterModel().hasModel());
            validateAssetPath(issues, "Enemy " + mob.mobId(), "paper-doll source", mob.paperDollSourcePath(), false);
            validateAssetPath(issues, "Enemy " + mob.mobId(), "attack sound", mob.attackSoundPath(), false);
            validateAssetPath(issues, "Enemy " + mob.mobId(), "hit sound", mob.damageSoundPath(), false);
            validateCharacterModel(issues, "Enemy " + mob.mobId(), mob.characterModel());
            for (CustomDropEntry drop : mob.dropEntries()) {
                if (!containsIgnoreCase(knownItems, drop.itemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Enemy " + mob.mobId() + " drops unknown item " + drop.itemId() + "."
                    ));
                }
            }
        }

        FirstPersonCombatLibrary.Content firstPersonContent = FirstPersonCombatLibrary.load();
        for (CustomLimb limb : design.customLimbs()) {
            for (String skillId : limb.skillIds()) {
                if (BattleContentCatalog.findSkill(skillId) == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Limb " + limb.limbId() + " references unknown battle skill " + skillId + "."));
                }
            }
            String rigId = FirstPersonCombatLibrary.normalizeId(limb.firstPersonRigId());
            if (!rigId.isBlank() && !firstPersonContent.rigs().containsKey(rigId)) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Limb " + limb.limbId() + " references unknown first-person rig "
                                + rigId + "."));
            }
        }

        for (CustomItem item : design.customItems()) {
            boolean modelBackedWeapon = item.itemType() == InventorySystem.ItemType.WEAPON
                    && !item.firstPersonModelPath().isBlank();
            validateAssetPath(issues, "Item " + item.itemId(), "icon", item.iconPath(), !modelBackedWeapon);
            validateAssetPath(issues, "Item " + item.itemId(), "paper-doll overlay", item.paperDollOverlayPath(), false);
            validateAssetPath(issues, "Item " + item.itemId(), "use sound", item.useSoundPath(), false);
            validateModelAssetPath(issues, "Item " + item.itemId(), "first-person model",
                    item.firstPersonModelPath(), false);
            if (item.itemType() == InventorySystem.ItemType.WEAPON
                    && item.firstPersonModelPath().isBlank()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Weapon " + item.itemId()
                                + " has no 3D model and will use its legacy bitmap icon."));
            }
        }

        for (CustomLimb limb : design.customLimbs()) {
            String owner = "Limb " + limb.limbId();
            if (limb.paperDollDerivedIcon()) {
                validateAssetPath(
                        issues,
                        owner,
                        "paper-doll icon source",
                        limb.paperDollSourcePath(),
                        true);
            } else {
                validateAssetPath(issues, owner, "icon", limb.iconPath(), true);
                validateAssetPath(issues, owner, "paper-doll source", limb.paperDollSourcePath(), false);
            }
            validateModelAssetPath(issues, "Limb " + limb.limbId(), "first-person arm model",
                    limb.firstPersonModelPath(), false);
        }

        for (CustomGatheringNode node : design.customGatheringNodes()) {
            if (node.lootEntries().isEmpty()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Gathering node " + node.nodeId() + " has no loot entries."
                ));
            }
            for (CustomDropEntry drop : node.lootEntries()) {
                if (!containsIgnoreCase(knownItems, drop.itemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Gathering node " + node.nodeId() + " outputs unknown item " + drop.itemId() + "."
                    ));
                }
            }
            if (!node.outputItemId().isBlank() && !containsIgnoreCase(knownItems, node.outputItemId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Gathering node " + node.nodeId() + " primary output is unknown item " + node.outputItemId() + "."
                ));
            }
            if (!node.smeltOutputItemId().isBlank() && !containsIgnoreCase(knownItems, node.smeltOutputItemId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Gathering node " + node.nodeId() + " smelts to unknown item " + node.smeltOutputItemId() + "."
                ));
            }
            if (node.nodeType() == GatheringNodeType.TREE && node.framePaths().size() != 2) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Tree gathering node " + node.nodeId() + " must have exactly 2 images: full tree and stump."
                ));
            } else if (node.nodeType() != GatheringNodeType.FISHING_SPOT
                    && node.nodeType() != GatheringNodeType.TREE
                    && node.framePaths().size() < 3) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Gathering node " + node.nodeId() + " has fewer than 3 stage images."
                ));
            }
            for (String framePath : node.framePaths()) {
                validateAssetPath(issues, "Gathering node " + node.nodeId(), "frame", framePath, true);
            }
            if (!node.modelPaths().isEmpty()) {
                if (node.nodeType() == GatheringNodeType.TREE && node.modelPaths().size() != 2) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Tree gathering node " + node.nodeId() + " must have exactly 2 3D models: full tree and stump."
                    ));
                } else if (node.nodeType() != GatheringNodeType.FISHING_SPOT
                        && node.nodeType() != GatheringNodeType.TREE
                        && node.modelPaths().size() < 3) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Gathering node " + node.nodeId() + " has fewer than 3 3D model states."
                    ));
                }
                for (String modelPath : node.modelPaths()) {
                    validateModelAssetPath(issues, "Gathering node " + node.nodeId(), "3D model state", modelPath, true);
                }
            }
            if (node.lightAttachment() != null && node.lightAttachment().enabled()
                    && node.lightAttachment().intensity() <= 0.0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Gathering node " + node.nodeId() + " has an enabled attached light with no intensity."
                ));
            }
        }

        for (CustomCookingRecipe recipe : design.customCookingRecipes()) {
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "raw item", recipe.rawItemId());
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "cooked item", recipe.cookedItemId());
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "burnt item", recipe.burntItemId());
        }

        for (CraftingRecipe recipe : design.craftingRecipes()) {
            String owner = "Crafting recipe " + recipe.recipeId();
            validateRecipeItem(issues, knownItems, owner, "primary item", recipe.primaryItemId());
            if (!recipe.secondaryItemId().isBlank()) {
                validateRecipeItem(issues, knownItems, owner, "secondary item", recipe.secondaryItemId());
                if (recipe.primaryItemId().equalsIgnoreCase(recipe.secondaryItemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " repeats the same ingredient; use one ingredient with a larger quantity."
                    ));
                }
            }
            if (recipe.primaryQuantity() <= 0
                    || (!recipe.secondaryItemId().isBlank() && recipe.secondaryQuantity() <= 0)) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        owner + " has an invalid ingredient quantity."
                ));
            }
            if (recipe.outputType() == MapDesignLibrary.CraftingOutputType.ITEM) {
                validateRecipeItem(issues, knownItems, owner, "output item", recipe.outputItemId());
            } else {
                if (recipe.outputStationType() == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " is missing its crafting-station output."
                    ));
                }
                if (recipe.stationLifetimeMs() <= 0) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " needs a positive temporary-station lifetime."
                    ));
                }
            }
            if (!recipe.smeltOutputItemId().isBlank()) {
                validateRecipeItem(issues, knownItems, owner, "smelt output item", recipe.smeltOutputItemId());
            }
        }
    }

    private static void validateButcheryProducts(List<ValidationIssue> issues, MapDesign design) {
        Map<String, CustomItem> items = new LinkedHashMap<>();
        design.customItems().forEach(item -> items.put(item.itemId(), item));
        Map<String, CustomLimb> limbs = new LinkedHashMap<>();
        design.customLimbs().forEach(limb -> limbs.put(limb.limbId(), limb));

        for (CustomMob mob : design.customMobs()) {
            EnemyButcheryProfile profile = mob.butcheryProfile();
            if (profile.hasValueOverride() && profile.baseValueOverride() <= 0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Enemy " + mob.mobId() + " has an invalid butchery value override."));
            }
            if (profile.type() == EnemyButcheryProfile.Type.LEATHER) {
                if (!profile.limbProductIds().isEmpty()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Leather enemy " + mob.mobId() + " also links humanoid limbs."));
                }
                CustomItem leather = items.get(profile.leatherItemId());
                if (leather == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Leather enemy " + mob.mobId() + " references missing product "
                                    + profile.leatherItemId() + "."));
                } else if (leather.itemType() != org.main.core.InventorySystem.ItemType.MISC
                        || leather.material() != org.main.core.GearMaterial.LEATHER
                        || !leather.stackable()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Leather enemy " + mob.mobId()
                                    + " must use a stackable MISC item with LEATHER material."));
                } else {
                    if (!mob.mobId().equals(leather.sourceEnemyId())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Leather product " + leather.itemId()
                                        + " is not linked back to enemy " + mob.mobId() + "."));
                    }
                    if (assetPathDoesNotLooksResolvable(leather.iconPath())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Leather product " + leather.itemId()
                                        + " has an unresolved image: " + leather.iconPath() + "."));
                    }
                }
                continue;
            }

            if (!profile.leatherItemId().isBlank()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Humanoid enemy " + mob.mobId() + " also links a leather product."));
            }
            Set<String> uniqueIds = new LinkedHashSet<>();
            for (org.main.core.LimbSlot slot : org.main.core.LimbSlot.values()) {
                String limbId = profile.productId(slot);
                CustomLimb limb = limbs.get(limbId);
                if (limb == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Humanoid enemy " + mob.mobId() + " is missing its "
                                    + slot.getDisplayName() + " product."));
                    continue;
                }
                if (!uniqueIds.add(limbId)) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Humanoid enemy " + mob.mobId() + " reuses limb product " + limbId + "."));
                }
                if (limb.limbSlot() != slot || !mob.mobId().equals(limb.sourceCreatureId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Humanoid enemy " + mob.mobId() + " has an incompatible " + slot.getDisplayName()
                                    + " product."));
                }
            }
            if (mob.paperDollSourcePath().isBlank()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Humanoid enemy " + mob.mobId() + " requires a paper-doll source."));
            } else if (assetPathDoesNotLooksResolvable(mob.paperDollSourcePath())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Humanoid enemy " + mob.mobId() + " has an unresolved paper-doll source: "
                                + mob.paperDollSourcePath() + "."));
            }
        }
    }

    private static void validateAssetPath(
            List<ValidationIssue> issues,
            String owner,
            String role,
            String assetPath,
            boolean required
    ) {
        if (assetPath == null || assetPath.isBlank()) {
            if (required) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, owner + " is missing " + role + " asset."));
            }
            return;
        }
        if (assetPathDoesNotLooksResolvable(assetPath)) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    owner + " " + role + " asset may be missing: " + assetPath + "."
            ));
        }
    }

    private static void validateCharacterModel(
            List<ValidationIssue> issues,
            String owner,
            CharacterModelDefinition definition
    ) {
        if (definition == null) {
            return;
        }
        validateModelAssetPath(issues, owner, "3D character model", definition.modelPath(), false);
        boolean hasAnimation = false;
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            String path = definition.animationPath(slot);
            hasAnimation |= !path.isBlank();
            validateModelAssetPath(issues, owner, slot.name().toLowerCase() + " animation", path, false);
        }
        if (hasAnimation && definition.modelPath().isBlank()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " has animation clips but no base 3D character model."
            ));
            return;
        }
        if (!definition.hasModel()) {
            return;
        }
        try {
            CharacterAnimationMetadataResolver.ModelMetadata metadata =
                    CharacterAnimationMetadataResolver.resolve(definition);
            for (CharacterModelDefinition.AnimationSlot slot
                    : CharacterModelDefinition.AnimationSlot.values()) {
                CharacterModelDefinition.AnimationBinding binding =
                        definition.animationBinding(slot);
                CharacterAnimationMetadataResolver.SlotMetadata resolved =
                        metadata.slot(slot);
                if (binding.isPresent() && !resolved.available()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            owner + " explicitly assigns " + slot.displayName()
                                    + " to unavailable or rig-incompatible clip '"
                                    + (binding.clipName().isBlank()
                                    ? "(automatic)"
                                    : binding.clipName())
                                    + "' in " + binding.path() + "."
                                    + (resolved.diagnostic().isBlank()
                                    ? ""
                                    : " " + resolved.diagnostic())
                    ));
                }
            }
        } catch (Exception exception) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " 3D character model could not be inspected: "
                            + exception.getMessage()
            ));
        }
    }

    private static void validateModelAssetPath(
            List<ValidationIssue> issues,
            String owner,
            String role,
            String assetPath,
            boolean required
    ) {
        validateAssetPath(issues, owner, role, assetPath, required);
        if (assetPath == null || assetPath.isBlank()) {
            return;
        }
        String normalized = assetPath.trim().toLowerCase();
        if (!normalized.endsWith(".glb") && !normalized.endsWith(".fbx")) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    owner + " " + role + " must use a .glb or .fbx asset."
            ));
        }
    }

    private static boolean assetPathDoesNotLooksResolvable(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            return true;
        }

        Path directPath = Path.of(normalized);
        if (Files.exists(directPath)) {
            return false;
        }

        if (normalized.startsWith("assets/")) {
            return !Files.exists(Path.of("src", "main", "resources").resolve(normalized));
        }
        if (normalized.startsWith("data/")) {
            return !Files.exists(Path.of(normalized));
        }
        if (normalized.startsWith("src/main/resources/") || normalized.startsWith("src/main/java/")) {
            return !Files.exists(Path.of(normalized));
        }

        return !Files.exists(Path.of("src", "main", "resources", "assets").resolve(normalized));
    }

    private static void validateDuplicateIds(List<ValidationIssue> issues, String label, List<String> ids) {
        List<String> seen = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A " + label + " is missing an id."));
                continue;
            }
            if (containsIgnoreCase(seen, id)) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Duplicate " + label + " id " + id + "."));
            }
            seen.add(id);
        }
    }

    private static void validateRecipeItem(
            List<ValidationIssue> issues,
            List<String> knownItems,
            String owner,
            String role,
            String itemId
    ) {
        if (itemId == null || itemId.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, owner + " is missing " + role + "."));
            return;
        }
        if (!containsIgnoreCase(knownItems, itemId)) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, owner + " references unknown " + role + " " + itemId + "."));
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null || target.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (target.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotInside(MapDesign design, int x, int y) {
        return design == null
                || x < 0
                || y < 0
                || x >= design.width()
                || y >= design.height();
    }
}

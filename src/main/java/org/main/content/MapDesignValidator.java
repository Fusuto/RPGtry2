package org.main.content;

import org.main.core.Library;
import org.main.core.CraftingStationType;
import org.main.engine.MapGeometryData;
import org.main.engine.MobAreaData;
import org.main.engine.MapPaintData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.main.content.MapDesignLibrary.AuthoredDialogue;
import static org.main.content.MapDesignLibrary.AuthoredDialogueChoice;
import static org.main.content.MapDesignLibrary.AuthoredDialogueNode;
import static org.main.content.MapDesignLibrary.AuthoredQuest;
import static org.main.content.MapDesignLibrary.CustomCompositeRecipe;
import static org.main.content.MapDesignLibrary.CustomCookingRecipe;
import static org.main.content.MapDesignLibrary.CustomDropEntry;
import static org.main.content.MapDesignLibrary.CustomGatheringNode;
import static org.main.content.MapDesignLibrary.CustomItem;
import static org.main.content.MapDesignLibrary.CustomLimb;
import static org.main.content.MapDesignLibrary.CustomMob;
import static org.main.content.MapDesignLibrary.CustomNpc;
import static org.main.content.MapDesignLibrary.GatheringNodeType;
import static org.main.content.MapDesignLibrary.MapDesign;
import static org.main.content.MapDesignLibrary.MapPlacement;
import static org.main.content.MapDesignLibrary.MapTrigger;
import static org.main.content.MapDesignLibrary.PlacementKind;
import static org.main.content.MapDesignLibrary.TriggerAction;
import static org.main.content.MapDesignLibrary.TriggerActionType;
import static org.main.content.MapDesignLibrary.ValidationIssue;
import static org.main.content.MapDesignLibrary.ValidationSeverity;

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

        if (!isInside(design, design.spawnX(), design.spawnY())) {
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

        List<String> authoredIds = authoredDialogues.stream()
                .map(AuthoredDialogue::interactionId)
                .toList();

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
            validatePlacement(design, issues, authoredIds, placement);
        }
        validateTriggers(design, issues);
        validateAuthoredDialogueActions(issues, authoredDialogues, design.authoredQuests(), design.customItems(), design.customLimbs());
        validateCustomContent(issues, design);

        return issues;
    }

    static boolean hasValidationErrors(MapDesign design) {
        return validate(design).stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
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
                    || !isInside(design, placement.x(), placement.y())) {
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
            List<String> authoredIds,
            MapPlacement placement
    ) {
        if (placement == null || placement.kind() == null || placement.id() == null || placement.id().isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "A placement is incomplete."));
            return;
        }

        if (!isInside(design, placement.x(), placement.y())) {
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
                    if (MapDesignLibrary.findCustomGatheringNode(placement.id(), design.customGatheringNodes()) == null
                            && !List.of("FISHING_SHOAL", "DECORATIVE_SHOAL", "MINERAL_ROCK_A").contains(placement.id())) {
                        throw new IllegalArgumentException("Unknown gathering node");
                    }
                }
                case GENERIC_NPC -> {
                    if (!"GOBLIN_MERCHANT".equals(placement.id())) {
                        throw new IllegalArgumentException("Unknown legacy generic NPC");
                    }
                }
                case MAIN_NPC -> {
                    if (!"TIPPING_THE_HAT_SKELETON".equals(placement.id())) {
                        throw new IllegalArgumentException("Unknown legacy main NPC");
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
                case AUTHORED_DIALOGUE_NPC -> {
                    if (!authoredIds.contains(placement.id())) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR,
                                "Authored NPC placement references missing dialogue " + placement.id() + "."
                        ));
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

    private static boolean isMapLinkInteractionId(String interactionId) {
        return interactionId != null && interactionId.startsWith("map_link|");
    }

    private static void validateMapLinkInteraction(List<ValidationIssue> issues, String interactionId) {
        String[] parts = interactionId.split("\\|", -1);
        if (parts.length != 4
                || parts[1].isBlank()
                || !isInteger(parts[2])
                || !isInteger(parts[3])) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "Map link interaction " + interactionId + " is malformed. Expected map_link|targetPath|x|y."
            ));
        }
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
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

            if (!isInside(design, trigger.x(), trigger.y())) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "Trigger " + trigger.id() + " is outside the map."));
            }

            if (trigger.fireMode() == MapDesignLibrary.TriggerFireMode.ON_QUEST_STAGE) {
                int maxStage = maxQuestStage(trigger.requiredQuestId(), design.authoredQuests());
                if (trigger.requiredQuestId().isBlank() || maxStage < 0) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Trigger " + trigger.id() + " references missing quest " + trigger.requiredQuestId() + "."
                    ));
                } else if (trigger.requiredQuestStage() > maxStage) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Trigger " + trigger.id() + " requires stage " + trigger.requiredQuestStage()
                                    + " past quest max stage " + maxStage + "."
                    ));
                }
            }

            for (TriggerAction action : trigger.actions()) {
                if (action == null) {
                    continue;
                }
                if (!isInside(design, action.targetX(), action.targetY())) {
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

    private static boolean isDoorTile(Library.TileType tile) {
        return tile == Library.TileType.DOOR_OPEN
                || tile == Library.TileType.DOOR_CLOSED
                || tile == Library.TileType.QUEST_DOOR_OPEN
                || tile == Library.TileType.QUEST_DOOR_CLOSED;
    }

    private static void validateAuthoredDialogueActions(
            List<ValidationIssue> issues,
            List<AuthoredDialogue> authoredDialogues,
            List<AuthoredQuest> authoredQuests,
            List<CustomItem> customItems,
            List<CustomLimb> customLimbs
    ) {
        List<String> authoredQuestIds = authoredQuests == null
                ? List.of()
                : authoredQuests.stream().map(AuthoredQuest::questId).toList();
        List<String> knownItemNames = knownItemDisplayNames(customItems, customLimbs);

        for (AuthoredDialogue dialogue : authoredDialogues) {
            if (!dialogue.rewardItemId().isBlank()) {
                if (MapDesignLibrary.findCustomItem(dialogue.rewardItemId(), customItems) == null
                        && MapDesignLibrary.findCustomLimb(dialogue.rewardItemId(), customLimbs) == null) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Authored dialogue " + dialogue.interactionId() + " has unknown reward item " + dialogue.rewardItemId() + "."
                    ));
                }
            }

            if (!dialogue.questId().isBlank()
                    && !authoredQuestIds.contains(dialogue.questId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " references missing quest " + dialogue.questId() + "."
                ));
            }
            List<String> nodeIds = dialogue.nodes().stream().map(AuthoredDialogueNode::nodeId).toList();
            validateAuthoredDialogueChoices(issues, dialogue, dialogue.choices(), authoredQuestIds, nodeIds, knownItemNames, authoredQuests);
            for (AuthoredDialogueNode node : dialogue.nodes()) {
                validateAuthoredDialogueChoices(issues, dialogue, node.choices(), authoredQuestIds, nodeIds, knownItemNames, authoredQuests);
            }
        }
    }

    private static List<String> knownItemDisplayNames(List<CustomItem> customItems, List<CustomLimb> customLimbs) {
        List<String> names = new ArrayList<>();
        if (customItems != null) {
            for (CustomItem item : customItems) {
                names.add(item.displayName());
            }
        }
        if (customLimbs != null) {
            for (CustomLimb limb : customLimbs) {
                names.add(limb.displayName());
            }
        }
        return names;
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
            List<String> authoredQuestIds,
            List<String> nodeIds,
            List<String> knownItemNames,
            List<AuthoredQuest> authoredQuests
    ) {
        for (AuthoredDialogueChoice choice : choices) {
            if (!choice.questId().isBlank()
                    && !authoredQuestIds.contains(choice.questId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " references missing quest " + choice.questId() + "."
                ));
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
            if (!choice.giveItemName().isBlank() && !containsIgnoreCase(knownItemNames, choice.giveItemName())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " gives unknown item " + choice.giveItemName() + "."
                ));
            }
            if (!choice.questId().isBlank() && choice.questStage() >= 0) {
                int maxStage = maxQuestStage(choice.questId(), authoredQuests);
                if (maxStage >= 0 && choice.questStage() > maxStage) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Authored dialogue " + dialogue.interactionId() + " choice " + choice.label() + " advances past quest max stage."
                    ));
                }
            }
        }
    }

    private static int maxQuestStage(String questId, List<AuthoredQuest> authoredQuests) {
        if (authoredQuests != null) {
            for (AuthoredQuest quest : authoredQuests) {
                if (quest.questId().equals(questId)) {
                    return quest.stageDescriptions().size() - 1;
                }
            }
        }
        return -1;
    }

    private static void validateCustomContent(List<ValidationIssue> issues, MapDesign design) {
        validateDuplicateIds(issues, "item", design.customItems().stream().map(CustomItem::itemId).toList());
        validateDuplicateIds(issues, "enemy", design.customMobs().stream().map(CustomMob::mobId).toList());
        validateDuplicateIds(issues, "NPC", design.customNpcs().stream().map(CustomNpc::npcId).toList());
        validateDuplicateIds(issues, "limb", design.customLimbs().stream().map(CustomLimb::limbId).toList());
        validateDuplicateIds(issues, "gathering node", design.customGatheringNodes().stream().map(CustomGatheringNode::nodeId).toList());
        validateDuplicateIds(issues, "cooking recipe", design.customCookingRecipes().stream().map(CustomCookingRecipe::recipeId).toList());
        validateDuplicateIds(issues, "composite recipe", design.customCompositeRecipes().stream().map(CustomCompositeRecipe::recipeId).toList());
        validateDuplicateIds(issues, "quest", design.authoredQuests().stream().map(AuthoredQuest::questId).toList());
        validateDuplicateIds(issues, "dialogue", design.authoredDialogues().stream().map(AuthoredDialogue::interactionId).toList());

        List<String> knownItems = knownItemIdsAndNames(design.customItems(), design.customLimbs());
        List<String> dialogueIds = design.authoredDialogues().stream().map(AuthoredDialogue::interactionId).toList();

        for (CustomNpc npc : design.customNpcs()) {
            validateAssetPath(issues, "Custom NPC " + npc.npcId(), "sprite", npc.imagePath(), true);
            validateAssetPath(issues, "Custom NPC " + npc.npcId(), "talk sound", npc.talkSoundPath(), false);
            if (npc.shop() == null && !npc.interactionId().isBlank() && !dialogueIds.contains(npc.interactionId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Custom NPC " + npc.npcId() + " references missing dialogue " + npc.interactionId() + "."
                ));
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

        for (CustomMob mob : design.customMobs()) {
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
            validateAssetPath(issues, "Enemy " + mob.mobId(), "sprite", mob.imagePath(), true);
            validateAssetPath(issues, "Enemy " + mob.mobId(), "paper-doll source", mob.paperDollSourcePath(), false);
            validateAssetPath(issues, "Enemy " + mob.mobId(), "attack sound", mob.attackSoundPath(), false);
            validateAssetPath(issues, "Enemy " + mob.mobId(), "hit sound", mob.damageSoundPath(), false);
            for (CustomDropEntry drop : mob.dropEntries()) {
                if (!containsIgnoreCase(knownItems, drop.itemId())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "Enemy " + mob.mobId() + " drops unknown item " + drop.itemId() + "."
                    ));
                }
            }
        }

        for (CustomItem item : design.customItems()) {
            validateAssetPath(issues, "Item " + item.itemId(), "icon", item.iconPath(), true);
            validateAssetPath(issues, "Item " + item.itemId(), "paper-doll overlay", item.paperDollOverlayPath(), false);
            validateAssetPath(issues, "Item " + item.itemId(), "use sound", item.useSoundPath(), false);
        }

        for (CustomLimb limb : design.customLimbs()) {
            validateAssetPath(issues, "Limb " + limb.limbId(), "icon", limb.iconPath(), true);
            validateAssetPath(issues, "Limb " + limb.limbId(), "paper-doll source", limb.paperDollSourcePath(), false);
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
            if (node.nodeType() != GatheringNodeType.FISHING_SPOT && node.framePaths().size() < 3) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "Gathering node " + node.nodeId() + " has fewer than 3 stage images."
                ));
            }
            for (String framePath : node.framePaths()) {
                validateAssetPath(issues, "Gathering node " + node.nodeId(), "frame", framePath, true);
            }
        }

        for (CustomCookingRecipe recipe : design.customCookingRecipes()) {
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "raw item", recipe.rawItemId());
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "cooked item", recipe.cookedItemId());
            validateRecipeItem(issues, knownItems, "Cooking recipe " + recipe.recipeId(), "burnt item", recipe.burntItemId());
        }

        for (CustomCompositeRecipe recipe : design.customCompositeRecipes()) {
            validateRecipeItem(issues, knownItems, "Composite recipe " + recipe.recipeId(), "primary item", recipe.primaryItemId());
            validateRecipeItem(issues, knownItems, "Composite recipe " + recipe.recipeId(), "secondary item", recipe.secondaryItemId());
            validateRecipeItem(issues, knownItems, "Composite recipe " + recipe.recipeId(), "output item", recipe.outputItemId());
            if (!recipe.smeltOutputItemId().isBlank()) {
                validateRecipeItem(issues, knownItems, "Composite recipe " + recipe.recipeId(), "smelt output item", recipe.smeltOutputItemId());
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
        if (!assetPathLooksResolvable(assetPath)) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    owner + " " + role + " asset may be missing: " + assetPath + "."
            ));
        }
    }

    private static boolean assetPathLooksResolvable(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            return false;
        }

        Path directPath = Path.of(normalized);
        if (Files.exists(directPath)) {
            return true;
        }

        if (normalized.startsWith("assets/")) {
            return Files.exists(Path.of("src", "main", "resources").resolve(normalized));
        }
        if (normalized.startsWith("data/")) {
            return Files.exists(Path.of(normalized));
        }
        if (normalized.startsWith("src/main/resources/") || normalized.startsWith("src/main/java/")) {
            return Files.exists(Path.of(normalized));
        }

        return Files.exists(Path.of("src", "main", "resources", "assets").resolve(normalized));
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

    private static boolean isInside(MapDesign design, int x, int y) {
        return design != null
                && x >= 0
                && y >= 0
                && x < design.width()
                && y < design.height();
    }
}

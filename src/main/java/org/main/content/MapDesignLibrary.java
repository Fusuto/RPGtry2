package org.main.content;

import org.main.core.CharacterSkill;
import org.main.core.CraftingStationType;
import org.main.core.GearDurability;
import org.main.core.GearMaterial;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.LimbItem;
import org.main.core.LimbSlot;
import org.main.core.PaperDollAssetLibrary;
import org.main.core.PlayerStat;
import org.main.core.ShopSystem;
import org.main.core.WeaponType;
import org.main.core.GeneratedDungeon;
import org.main.core.EquipmentViewModelProfile;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.MapGeometryData;
import org.main.engine.MapLight;
import org.main.engine.MapLightingSettings;
import org.main.engine.MapPaintData;
import org.main.engine.MobAreaData;
import org.main.engine.AssetLoader;
import org.main.engine.SkyboxSpec;
import org.main.engine.SpriteAnimation;
import org.main.monsters.Monster;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

public final class MapDesignLibrary {
    public static final String DEFAULT_NPC_VISUAL_PATH = "assets/images/monster/Nov-2015/mon/goblin.png";
    public static final String MAP_RESOURCE_FOLDER = "assets/editor/maps";
    public static final String CONTENT_RESOURCE_FOLDER = "assets/editor/content";
    public static final Path EDITOR_RESOURCE_FOLDER = Path.of("src", "main", "resources", "assets", "editor");
    public static final Path MAP_FOLDER = EDITOR_RESOURCE_FOLDER.resolve("maps");
    public static final Path CONTENT_FOLDER = EDITOR_RESOURCE_FOLDER.resolve("content");
    public static final Path DATA_MAP_FOLDER = Path.of("data", "maps");
    private static final String OAK_TREE_TEST_MODEL_PATH = "assets/3D/gatheringNode/Tree3.glb";

    private MapDesignLibrary() {
    }

    public static Monster createEnemyById(String enemyId) {
        if (enemyId == null || enemyId.isBlank()) {
            return null;
        }

        try {
            CustomMob customMob = findCustomMob(enemyId, loadSharedContent().customMobs());
            if (customMob != null) {
                return customMob.createMonster();
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    public static MapDesign createBlank(int width, int height, ThemeLibrary primaryTheme, ThemeLibrary alternateTheme) {
        int safeWidth = Math.max(3, width);
        int safeHeight = Math.max(3, height);
        Library.TileType[][] tiles = new Library.TileType[safeHeight][safeWidth];
        int[][] themeIndexes = new int[safeHeight][safeWidth];

        for (int y = 0; y < safeHeight; y++) {
            for (int x = 0; x < safeWidth; x++) {
                tiles[y][x] = isBorder(x, y, safeWidth, safeHeight)
                        ? Library.TileType.WALL
                        : Library.TileType.FLOOR;
            }
        }

        return new MapDesign(
                safeWidth,
                safeHeight,
                "New Map",
                "",
                primaryTheme == null ? ThemeLibrary.STONE_WOOD : primaryTheme,
                alternateTheme == null ? ThemeLibrary.SANDSTONE_GATE : alternateTheme,
                tiles,
                themeIndexes,
                MapPaintData.blank(safeWidth, safeHeight),
                MapGeometryData.blank(safeWidth, safeHeight),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                1,
                1
        );
    }

    public static void save(MapDesign design, Path path) throws IOException {
        saveInternal(design, path, false);
    }

    static void saveContentSegment(MapDesign design, Path path) throws IOException {
        saveInternal(design, path, true);
    }

    private static void saveInternal(MapDesign design, Path path, boolean includeContent) throws IOException {
        if (design == null || path == null) {
            return;
        }

        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.setProperty("displayName", design.displayName());
        properties.setProperty("description", design.description());
        properties.setProperty("musicPath", design.musicPath());
        properties.setProperty("skyboxPath", design.skyboxPath());
        writeLighting(properties, design.lightingSettings(), design.lights());
        properties.setProperty("width", String.valueOf(design.width()));
        properties.setProperty("height", String.valueOf(design.height()));
        properties.setProperty("primaryTheme", design.primaryTheme().name());
        properties.setProperty("alternateTheme", design.alternateTheme().name());
        properties.setProperty("spawnX", String.valueOf(design.spawnX()));
        properties.setProperty("spawnY", String.valueOf(design.spawnY()));

        for (int y = 0; y < design.height(); y++) {
            properties.setProperty("tiles." + y, joinTileRow(design.tiles()[y]));
            properties.setProperty("themes." + y, joinThemeRow(design.themeIndexes()[y]));
        }
        writeMapPaint(properties, design.mapPaint());
        writeMapGeometry(properties, design.mapGeometry());
        writeMobAreas(properties, design.mobAreas());

        properties.setProperty("placement.count", String.valueOf(design.placements().size()));
        for (int i = 0; i < design.placements().size(); i++) {
            MapPlacement placement = design.placements().get(i);
            String prefix = "placement." + i + ".";
            properties.setProperty(prefix + "kind", placement.kind().name());
            properties.setProperty(prefix + "id", placement.id());
            properties.setProperty(prefix + "x", String.valueOf(placement.x()));
            properties.setProperty(prefix + "y", String.valueOf(placement.y()));
        }

        properties.setProperty("placedObject.count", String.valueOf(design.placedObjects().size()));
        for (int i = 0; i < design.placedObjects().size(); i++) {
            writePlacedObject(properties, "placedObject." + i + ".", design.placedObjects().get(i));
        }

        properties.setProperty("trigger.count", String.valueOf(design.triggers().size()));
        for (int i = 0; i < design.triggers().size(); i++) {
            MapTrigger trigger = design.triggers().get(i);
            String prefix = "trigger." + i + ".";
            properties.setProperty(prefix + "id", trigger.id());
            properties.setProperty(prefix + "x", String.valueOf(trigger.x()));
            properties.setProperty(prefix + "y", String.valueOf(trigger.y()));
            properties.setProperty(prefix + "fireMode", trigger.fireMode().name());
            properties.setProperty(prefix + "oneShot", String.valueOf(trigger.oneShot()));
            properties.setProperty(prefix + "requiredQuestId", trigger.requiredQuestId());
            properties.setProperty(prefix + "requiredQuestProgress", trigger.requiredQuestProgress());
            properties.setProperty(prefix + "action.count", String.valueOf(trigger.actions().size()));
            for (int actionIndex = 0; actionIndex < trigger.actions().size(); actionIndex++) {
                TriggerAction action = trigger.actions().get(actionIndex);
                String actionPrefix = prefix + "action." + actionIndex + ".";
                properties.setProperty(actionPrefix + "type", action.type().name());
                properties.setProperty(actionPrefix + "targetX", String.valueOf(action.targetX()));
                properties.setProperty(actionPrefix + "targetY", String.valueOf(action.targetY()));
            }
        }

        if (includeContent) {
        properties.setProperty("dialogue.schemaVersion", "3");
        properties.setProperty("dialogue.count", String.valueOf(design.authoredDialogues().size()));
        for (int i = 0; i < design.authoredDialogues().size(); i++) {
            AuthoredDialogue authoredDialogue = design.authoredDialogues().get(i);
            String prefix = "dialogue." + i + ".";
            properties.setProperty(prefix + "interactionId", authoredDialogue.interactionId());
            properties.setProperty(prefix + "speakerName", authoredDialogue.speakerName());
            properties.setProperty(prefix + "bodyText", authoredDialogue.bodyText());
            properties.setProperty(prefix + "followUpInteractionId", authoredDialogue.followUpInteractionId());
            properties.setProperty(prefix + "visualPath", authoredDialogue.visualPath());
            properties.setProperty(prefix + "firstTalkNodeId", authoredDialogue.firstTalkNodeId());
            properties.setProperty(prefix + "repeatTalkNodeId", authoredDialogue.repeatTalkNodeId());
            writeQuestRewards(properties, prefix + "reward.", authoredDialogue.rewards());
            properties.setProperty(prefix + "choice.count", String.valueOf(authoredDialogue.choices().size()));
            for (int choiceIndex = 0; choiceIndex < authoredDialogue.choices().size(); choiceIndex++) {
                writeAuthoredDialogueChoice(properties, prefix + "choice." + choiceIndex + ".", authoredDialogue.choices().get(choiceIndex));
            }
            properties.setProperty(prefix + "node.count", String.valueOf(authoredDialogue.nodes().size()));
            for (int nodeIndex = 0; nodeIndex < authoredDialogue.nodes().size(); nodeIndex++) {
                AuthoredDialogueNode node = authoredDialogue.nodes().get(nodeIndex);
                String nodePrefix = prefix + "node." + nodeIndex + ".";
                properties.setProperty(nodePrefix + "nodeId", node.nodeId());
                properties.setProperty(nodePrefix + "bodyText", node.bodyText());
                properties.setProperty(nodePrefix + "canvasX", String.valueOf(node.canvasX()));
                properties.setProperty(nodePrefix + "canvasY", String.valueOf(node.canvasY()));
                properties.setProperty(nodePrefix + "choice.count", String.valueOf(node.choices().size()));
                for (int choiceIndex = 0; choiceIndex < node.choices().size(); choiceIndex++) {
                    writeAuthoredDialogueChoice(properties, nodePrefix + "choice." + choiceIndex + ".", node.choices().get(choiceIndex));
                }
            }
        }

        properties.setProperty("quest.schemaVersion", "3");
        properties.setProperty("quest.count", String.valueOf(design.authoredQuests().size()));
        for (int i = 0; i < design.authoredQuests().size(); i++) {
            AuthoredQuest authoredQuest = design.authoredQuests().get(i);
            String prefix = "quest." + i + ".";
            properties.setProperty(prefix + "questId", authoredQuest.questId());
            properties.setProperty(prefix + "displayName", authoredQuest.displayName());
            properties.setProperty(prefix + "summary", authoredQuest.summary());
            writeQuestRequirements(properties, prefix + "requirement.", authoredQuest.requirements());
            writeQuestFlow(properties, prefix + "offer.", authoredQuest.offerFlow());
            properties.setProperty(prefix + "stage.count", String.valueOf(authoredQuest.stages().size()));
            for (int stageIndex = 0; stageIndex < authoredQuest.stages().size(); stageIndex++) {
                QuestStage stage = authoredQuest.stages().get(stageIndex);
                String stagePrefix = prefix + "stage." + stageIndex + ".";
                properties.setProperty(stagePrefix + "stageId", stage.stageId());
                properties.setProperty(stagePrefix + "title", stage.title());
                properties.setProperty(stagePrefix + "journalText", stage.journalText());
                properties.setProperty(stagePrefix + "completionMode", stage.completionMode().name());
                writeQuestObjectives(properties, stagePrefix + "objective.", stage.objectives());
                writeQuestRewards(properties, stagePrefix + "reward.", stage.rewards());
                writeQuestFlow(properties, stagePrefix + "flow.", stage.flow());
            }
            writeQuestRewards(properties, prefix + "finalReward.", authoredQuest.finalRewards());
            writeQuestFlow(properties, prefix + "epilogue.", authoredQuest.epilogueFlow());
        }

        properties.setProperty("item.count", String.valueOf(design.customItems().size()));
        for (int i = 0; i < design.customItems().size(); i++) {
            CustomItem customItem = design.customItems().get(i);
            String prefix = "item." + i + ".";
            properties.setProperty(prefix + "itemId", customItem.itemId());
            properties.setProperty(prefix + "displayName", customItem.displayName());
            properties.setProperty(prefix + "itemType", customItem.itemType().name());
            properties.setProperty(prefix + "iconPath", customItem.iconPath());
            properties.setProperty(prefix + "paperDollOverlayPath", customItem.paperDollOverlayPath());
            properties.setProperty(prefix + "useSoundPath", customItem.useSoundPath());
            properties.setProperty(prefix + "weaponType", customItem.weaponType().name());
            properties.setProperty(prefix + "twoHanded", String.valueOf(customItem.twoHanded()));
            properties.setProperty(prefix + "material", customItem.material().name());
            properties.setProperty(prefix + "healAmount", String.valueOf(customItem.healAmount()));
            properties.setProperty(prefix + "baseGoldValue", String.valueOf(customItem.baseGoldValue()));
            properties.setProperty(prefix + "examineText", customItem.examineText());
            properties.setProperty(prefix + "statBonusTarget", customItem.statBonusTarget() == null ? "" : customItem.statBonusTarget().name());
            properties.setProperty(prefix + "stackable", String.valueOf(customItem.stackable()));
            properties.setProperty(prefix + "smithingRecipeEnabled", String.valueOf(customItem.smithingRecipeEnabled()));
            properties.setProperty(prefix + "smithingRequiredBars", String.valueOf(customItem.smithingRequiredBars()));
            properties.setProperty(prefix + "smithingRequiredLevel", String.valueOf(customItem.smithingRequiredLevel()));
            properties.setProperty(prefix + "smithingXpReward", String.valueOf(customItem.smithingXpReward()));
            properties.setProperty(prefix + "magicAccuracyBonus", String.valueOf(customItem.magicAccuracyBonus()));
            properties.setProperty(prefix + "magicPowerBonus", String.valueOf(customItem.magicPowerBonus()));
            properties.setProperty(prefix + "firstPersonModelPath", customItem.firstPersonModelPath());
            EquipmentViewModelProfile pose = customItem.viewModelProfile();
            properties.setProperty(prefix + "viewModel.positionX", String.valueOf(pose.positionX()));
            properties.setProperty(prefix + "viewModel.positionY", String.valueOf(pose.positionY()));
            properties.setProperty(prefix + "viewModel.positionZ", String.valueOf(pose.positionZ()));
            properties.setProperty(prefix + "viewModel.rotationX", String.valueOf(pose.rotationX()));
            properties.setProperty(prefix + "viewModel.rotationY", String.valueOf(pose.rotationY()));
            properties.setProperty(prefix + "viewModel.rotationZ", String.valueOf(pose.rotationZ()));
            properties.setProperty(prefix + "viewModel.normalizedHeight", String.valueOf(pose.normalizedHeight()));
            properties.setProperty(prefix + "viewModel.swingAxisX", String.valueOf(pose.swingAxisX()));
            properties.setProperty(prefix + "viewModel.swingAxisY", String.valueOf(pose.swingAxisY()));
            properties.setProperty(prefix + "viewModel.swingAxisZ", String.valueOf(pose.swingAxisZ()));
            properties.setProperty(prefix + "viewModel.pairedHands", String.valueOf(pose.pairedHands()));
        }

        properties.setProperty("mob.count", String.valueOf(design.customMobs().size()));
        for (int i = 0; i < design.customMobs().size(); i++) {
            CustomMob customMob = design.customMobs().get(i);
            String prefix = "mob." + i + ".";
            properties.setProperty(prefix + "mobId", customMob.mobId());
            properties.setProperty(prefix + "displayName", customMob.displayName());
            properties.setProperty(prefix + "imagePath", customMob.imagePath());
            properties.setProperty(prefix + "paperDollSourcePath", customMob.paperDollSourcePath());
            for (PlayerStat stat : PlayerStat.values()) {
                properties.setProperty(prefix + "stat." + stat.name(), String.valueOf(customMob.statValues().getOrDefault(stat, 0)));
            }
            properties.setProperty(prefix + "xpReward", String.valueOf(customMob.xpReward()));
            properties.setProperty(prefix + "description", customMob.description());
            properties.setProperty(prefix + "attackSoundPath", customMob.attackSoundPath());
            properties.setProperty(prefix + "damageSoundPath", customMob.damageSoundPath());
            properties.setProperty(prefix + "combatAiIntelligence", String.valueOf(customMob.combatAiIntelligence()));
            properties.setProperty(prefix + "awarenessRadius", String.valueOf(customMob.awarenessRadius()));
            properties.setProperty(prefix + "movementIntervalMs", String.valueOf(customMob.movementIntervalMs()));
            properties.setProperty(prefix + "respawnDelayMs", String.valueOf(customMob.respawnDelayMs()));
            writeCharacterModel(properties, prefix + "model.", customMob.characterModel());
            properties.setProperty(prefix + "skillIds", joinSkills(customMob.skillIds()));
            properties.setProperty(prefix + "drop.count", String.valueOf(customMob.dropEntries().size()));
            for (int dropIndex = 0; dropIndex < customMob.dropEntries().size(); dropIndex++) {
                CustomDropEntry drop = customMob.dropEntries().get(dropIndex);
                String dropPrefix = prefix + "drop." + dropIndex + ".";
                properties.setProperty(dropPrefix + "itemId", drop.itemId());
                properties.setProperty(dropPrefix + "chance", String.valueOf(drop.chance()));
            }
        }

        properties.setProperty("limb.count", String.valueOf(design.customLimbs().size()));
        for (int i = 0; i < design.customLimbs().size(); i++) {
            CustomLimb customLimb = design.customLimbs().get(i);
            String prefix = "limb." + i + ".";
            properties.setProperty(prefix + "limbId", customLimb.limbId());
            properties.setProperty(prefix + "displayName", customLimb.displayName());
            properties.setProperty(prefix + "limbSlot", customLimb.limbSlot().name());
            properties.setProperty(prefix + "iconPath", customLimb.iconPath());
            properties.setProperty(prefix + "condition", customLimb.condition().name());
            properties.setProperty(prefix + "description", customLimb.description());
            properties.setProperty(prefix + "sourceCreatureId", customLimb.sourceCreatureId());
            properties.setProperty(prefix + "paperDollSourcePath", customLimb.paperDollSourcePath());
            properties.setProperty(prefix + "firstPersonModelPath", customLimb.firstPersonModelPath());
            properties.setProperty(prefix + "firstPersonRigId", customLimb.firstPersonRigId());
            properties.setProperty(prefix + "skillIds", joinSkills(customLimb.skillIds()));
            for (PlayerStat stat : PlayerStat.values()) {
                properties.setProperty(prefix + "stat." + stat.name(), String.valueOf(customLimb.statBonuses().getOrDefault(stat, 0)));
            }
        }

        properties.setProperty("npc.schemaVersion", "2");
        properties.setProperty("npc.count", String.valueOf(design.customNpcs().size()));
        for (int i = 0; i < design.customNpcs().size(); i++) {
            CustomNpc customNpc = design.customNpcs().get(i);
            String prefix = "npc." + i + ".";
            properties.setProperty(prefix + "npcId", customNpc.npcId());
            properties.setProperty(prefix + "displayName", customNpc.displayName());
            properties.setProperty(prefix + "imagePath", customNpc.imagePath());
            properties.setProperty(prefix + "talkSoundPath", customNpc.talkSoundPath());
            properties.setProperty(prefix + "interactionId", customNpc.interactionId());
            properties.setProperty(prefix + "quest.count", String.valueOf(customNpc.questIds().size()));
            for (int questIndex = 0; questIndex < customNpc.questIds().size(); questIndex++) {
                properties.setProperty(prefix + "quest." + questIndex, customNpc.questIds().get(questIndex));
            }
            writeCharacterModel(properties, prefix + "model.", customNpc.characterModel());
            CustomShop customShop = customNpc.shop();
            properties.setProperty(prefix + "shop.enabled", String.valueOf(customShop != null));
            if (customShop != null) {
                properties.setProperty(prefix + "shop.name", customShop.shopName());
                properties.setProperty(prefix + "shop.greeting", customShop.greeting());
                properties.setProperty(prefix + "shop.stock.count", String.valueOf(customShop.stock().size()));
                for (int stockIndex = 0; stockIndex < customShop.stock().size(); stockIndex++) {
                    CustomShopStock stock = customShop.stock().get(stockIndex);
                    String stockPrefix = prefix + "shop.stock." + stockIndex + ".";
                    properties.setProperty(stockPrefix + "itemId", stock.itemId());
                    properties.setProperty(stockPrefix + "quantity", String.valueOf(stock.quantity()));
                    properties.setProperty(stockPrefix + "buyPrice", String.valueOf(stock.buyPrice()));
                    properties.setProperty(stockPrefix + "sellPrice", String.valueOf(stock.sellPrice()));
                }
            }
        }

        properties.setProperty("furniture.count", String.valueOf(design.customFurniture().size()));
        for (int i = 0; i < design.customFurniture().size(); i++) {
            CustomFurnitureDefinition furniture = design.customFurniture().get(i);
            String prefix = "furniture." + i + ".";
            properties.setProperty(prefix + "furnitureId", furniture.furnitureId());
            properties.setProperty(prefix + "displayName", furniture.displayName());
            properties.setProperty(prefix + "category", furniture.category());
            properties.setProperty(prefix + "modelPath", furniture.modelPath());
            properties.setProperty(prefix + "defaultScale", String.valueOf(furniture.defaultScale()));
            properties.setProperty(prefix + "defaultBlocksMovement", String.valueOf(furniture.defaultBlocksMovement()));
            properties.setProperty(prefix + "interactionId", furniture.interactionId());
            writeLightAttachment(properties, prefix + "light.", furniture.lightAttachment());
        }

        properties.setProperty("gatheringNode.count", String.valueOf(design.customGatheringNodes().size()));
        for (int i = 0; i < design.customGatheringNodes().size(); i++) {
            CustomGatheringNode node = design.customGatheringNodes().get(i);
            String prefix = "gatheringNode." + i + ".";
            properties.setProperty(prefix + "nodeId", node.nodeId());
            properties.setProperty(prefix + "displayName", node.displayName());
            properties.setProperty(prefix + "nodeType", node.nodeType().name());
            properties.setProperty(prefix + "gatheringSkill", node.gatheringSkill().name());
            properties.setProperty(prefix + "requiredLevel", String.valueOf(node.requiredLevel()));
            properties.setProperty(prefix + "outputItemId", node.outputItemId());
            properties.setProperty(prefix + "gatherXpReward", String.valueOf(node.gatherXpReward()));
            properties.setProperty(prefix + "smeltOutputItemId", node.smeltOutputItemId());
            properties.setProperty(prefix + "smeltRequiredLevel", String.valueOf(node.smeltRequiredLevel()));
            properties.setProperty(prefix + "smeltXpReward", String.valueOf(node.smeltXpReward()));
            properties.setProperty(prefix + "visualScale", String.valueOf(node.visualScale()));
            properties.setProperty(prefix + "frameDurationMs", String.valueOf(node.frameDurationMs()));
            writeLightAttachment(properties, prefix + "light.", node.lightAttachment());
            properties.setProperty(prefix + "loot.count", String.valueOf(node.lootEntries().size()));
            for (int lootIndex = 0; lootIndex < node.lootEntries().size(); lootIndex++) {
                CustomDropEntry loot = node.lootEntries().get(lootIndex);
                String lootPrefix = prefix + "loot." + lootIndex + ".";
                properties.setProperty(lootPrefix + "itemId", loot.itemId());
                properties.setProperty(lootPrefix + "chance", String.valueOf(loot.chance()));
            }
            properties.setProperty(prefix + "frame.count", String.valueOf(node.framePaths().size()));
            for (int frameIndex = 0; frameIndex < node.framePaths().size(); frameIndex++) {
                properties.setProperty(prefix + "frame." + frameIndex, node.framePaths().get(frameIndex));
            }
            properties.setProperty(prefix + "model.count", String.valueOf(node.modelPaths().size()));
            for (int modelIndex = 0; modelIndex < node.modelPaths().size(); modelIndex++) {
                properties.setProperty(prefix + "model." + modelIndex, node.modelPaths().get(modelIndex));
            }
        }

        properties.setProperty("cookingRecipe.count", String.valueOf(design.customCookingRecipes().size()));
        for (int i = 0; i < design.customCookingRecipes().size(); i++) {
            CustomCookingRecipe recipe = design.customCookingRecipes().get(i);
            String prefix = "cookingRecipe." + i + ".";
            properties.setProperty(prefix + "recipeId", recipe.recipeId());
            properties.setProperty(prefix + "displayName", recipe.displayName());
            properties.setProperty(prefix + "rawItemId", recipe.rawItemId());
            properties.setProperty(prefix + "cookedItemId", recipe.cookedItemId());
            properties.setProperty(prefix + "burntItemId", recipe.burntItemId());
            properties.setProperty(prefix + "requiredLevel", String.valueOf(recipe.requiredLevel()));
            properties.setProperty(prefix + "xpReward", String.valueOf(recipe.xpReward()));
        }

        properties.setProperty("craftingRecipe.count", String.valueOf(design.craftingRecipes().size()));
        for (int i = 0; i < design.craftingRecipes().size(); i++) {
            CraftingRecipe recipe = design.craftingRecipes().get(i);
            String prefix = "craftingRecipe." + i + ".";
            properties.setProperty(prefix + "recipeId", recipe.recipeId());
            properties.setProperty(prefix + "displayName", recipe.displayName());
            properties.setProperty(prefix + "category", recipe.category().name());
            properties.setProperty(prefix + "primaryItemId", recipe.primaryItemId());
            properties.setProperty(prefix + "secondaryItemId", recipe.secondaryItemId());
            properties.setProperty(prefix + "outputItemId", recipe.outputItemId());
            properties.setProperty(prefix + "requiredSkill", recipe.requiredSkill().name());
            properties.setProperty(prefix + "requiredLevel", String.valueOf(recipe.requiredLevel()));
            properties.setProperty(prefix + "xpReward", String.valueOf(recipe.xpReward()));
            properties.setProperty(prefix + "consumePrimary", String.valueOf(recipe.consumePrimary()));
            properties.setProperty(prefix + "consumeSecondary", String.valueOf(recipe.consumeSecondary()));
            properties.setProperty(prefix + "smeltOutputItemId", recipe.smeltOutputItemId());
            properties.setProperty(prefix + "smeltRequiredLevel", String.valueOf(recipe.smeltRequiredLevel()));
            properties.setProperty(prefix + "smeltXpReward", String.valueOf(recipe.smeltXpReward()));
            properties.setProperty(prefix + "primaryQuantity", String.valueOf(recipe.primaryQuantity()));
            properties.setProperty(prefix + "secondaryQuantity", String.valueOf(recipe.secondaryQuantity()));
            properties.setProperty(prefix + "outputType", recipe.outputType().name());
            properties.setProperty(prefix + "outputStationType", recipe.outputStationType() == null
                    ? ""
                    : recipe.outputStationType().name());
            properties.setProperty(prefix + "stationLifetimeMs", String.valueOf(recipe.stationLifetimeMs()));
        }
        }

        if (includeContent) {
            retainRequestedContentSegment(properties, path);
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "Aether map design");
        }
    }

    public static MapDesign load(Path path) throws IOException {
        MapDesign design = loadContentSegment(path);
        if (!MapDesignContentStore.isContentCatalogPath(path)) {
            replaceAuthoredContent(design, loadSharedContent());
        }
        return design;
    }

    static MapDesign loadContentSegment(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = openMapDesignStream(path)) {
            properties.load(inputStream);
        }
        validateCurrentContentSchema(path, properties);

        int width = readInt(properties, "width", 12);
        int height = readInt(properties, "height", 12);
        String displayName = properties.getProperty("displayName", fallbackDisplayName(path));
        String description = properties.getProperty("description", "");
        String musicPath = properties.getProperty("musicPath", "");
        String skyboxPath = properties.getProperty("skyboxPath", "");
        MapLightingSettings lightingSettings = readLightingSettings(properties);
        List<MapLight> lights = readLights(properties);
        int spawnX = readInt(properties, "spawnX", 1);
        int spawnY = readInt(properties, "spawnY", 1);
        ThemeLibrary primaryTheme = readTheme(properties, "primaryTheme", ThemeLibrary.STONE_WOOD);
        ThemeLibrary alternateTheme = readTheme(properties, "alternateTheme", ThemeLibrary.SANDSTONE_GATE);
        Library.TileType[][] tiles = new Library.TileType[height][width];
        int[][] themeIndexes = new int[height][width];

        for (int y = 0; y < height; y++) {
            String[] tileValues = properties.getProperty("tiles." + y, "").split(",");
            String[] themeValues = properties.getProperty("themes." + y, "").split(",");

            for (int x = 0; x < width; x++) {
                tiles[y][x] = readTile(tileValues, x, Library.TileType.FLOOR);
                themeIndexes[y][x] = Math.max(0, Math.min(1, readListInt(themeValues, x, 0)));
            }
        }
        MapPaintData mapPaint = readMapPaint(properties, width, height);
        MapGeometryData mapGeometry = readMapGeometry(properties, width, height);
        MobAreaData mobAreas = readMobAreas(properties, width, height);

        int placementCount = readInt(properties, "placement.count", 0);
        List<MapPlacement> placements = new ArrayList<>();
        for (int i = 0; i < placementCount; i++) {
            String prefix = "placement." + i + ".";
            PlacementKind kind = readPlacementKind(properties.getProperty(prefix + "kind", ""));
            String id = properties.getProperty(prefix + "id", "");
            int x = readInt(properties, prefix + "x", 0);
            int y = readInt(properties, prefix + "y", 0);

            if (kind != null && !id.isBlank()) {
                placements.add(new MapPlacement(kind, id, x, y));
            }
        }

        int placedObjectCount = readInt(properties, "placedObject.count", 0);
        List<PlacedObjectInstance> placedObjects = new ArrayList<>();
        for (int i = 0; i < placedObjectCount; i++) {
            PlacedObjectInstance instance = readPlacedObject(properties, "placedObject." + i + ".");
            if (instance != null) {
                placedObjects.add(instance);
            }
        }

        int triggerCount = readInt(properties, "trigger.count", 0);
        List<MapTrigger> triggers = new ArrayList<>();
        for (int i = 0; i < triggerCount; i++) {
            String prefix = "trigger." + i + ".";
            String triggerId = properties.getProperty(prefix + "id", "");
            int x = readInt(properties, prefix + "x", 0);
            int y = readInt(properties, prefix + "y", 0);
            TriggerFireMode fireMode = readTriggerFireMode(properties.getProperty(prefix + "fireMode", ""));
            boolean oneShot = Boolean.parseBoolean(properties.getProperty(prefix + "oneShot", "true"));
            String requiredQuestId = properties.getProperty(prefix + "requiredQuestId", "");
            String requiredQuestProgress = properties.getProperty(prefix + "requiredQuestProgress", "");
            int actionCount = readInt(properties, prefix + "action.count", 0);
            List<TriggerAction> actions = new ArrayList<>();
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                String actionPrefix = prefix + "action." + actionIndex + ".";
                TriggerActionType type = readTriggerActionType(properties.getProperty(actionPrefix + "type", ""));
                int targetX = readInt(properties, actionPrefix + "targetX", 0);
                int targetY = readInt(properties, actionPrefix + "targetY", 0);
                if (type != null) {
                    actions.add(new TriggerAction(type, targetX, targetY));
                }
            }

            if (!triggerId.isBlank()) {
                triggers.add(new MapTrigger(
                        triggerId,
                        x,
                        y,
                        fireMode,
                        oneShot,
                        requiredQuestId,
                        requiredQuestProgress,
                        actions
                ));
            }
        }

        String dialogueRoot = "dialogue";
        int authoredDialogueCount = readInt(properties, dialogueRoot + ".count", 0);
        List<AuthoredDialogue> authoredDialogues = new ArrayList<>();
        for (int i = 0; i < authoredDialogueCount; i++) {
            String prefix = dialogueRoot + "." + i + ".";
            String interactionId = properties.getProperty(prefix + "interactionId", "");
            String speakerName = properties.getProperty(prefix + "speakerName", "");
            String bodyText = properties.getProperty(prefix + "bodyText", "");
            String followUpInteractionId = properties.getProperty(prefix + "followUpInteractionId", "");
            String visualPath = properties.getProperty(prefix + "visualPath", DEFAULT_NPC_VISUAL_PATH);
            int choiceCount = readInt(properties, prefix + "choice.count", 0);
            List<AuthoredDialogueChoice> choices = new ArrayList<>();
            for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
                AuthoredDialogueChoice choice = readAuthoredDialogueChoice(properties, prefix + "choice." + choiceIndex + ".");
                if (choice != null) {
                    choices.add(choice);
                }
            }
            int nodeCount = readInt(properties, prefix + "node.count", 0);
            List<AuthoredDialogueNode> nodes = new ArrayList<>();
            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                String nodePrefix = prefix + "node." + nodeIndex + ".";
                String nodeId = properties.getProperty(nodePrefix + "nodeId", "");
                String nodeBodyText = properties.getProperty(nodePrefix + "bodyText", "");
                int nodeChoiceCount = readInt(properties, nodePrefix + "choice.count", 0);
                List<AuthoredDialogueChoice> nodeChoices = new ArrayList<>();
                for (int choiceIndex = 0; choiceIndex < nodeChoiceCount; choiceIndex++) {
                    AuthoredDialogueChoice choice = readAuthoredDialogueChoice(properties, nodePrefix + "choice." + choiceIndex + ".");
                    if (choice != null) {
                        nodeChoices.add(choice);
                    }
                }
                if (!nodeId.isBlank() && !nodeBodyText.isBlank()) {
                    nodes.add(new AuthoredDialogueNode(
                            nodeId,
                            nodeBodyText,
                            readInt(properties, nodePrefix + "canvasX", 80 + nodeIndex * 260),
                            readInt(properties, nodePrefix + "canvasY", 80),
                            nodeChoices
                    ));
                }
            }

            if (!interactionId.isBlank() && !speakerName.isBlank() && !bodyText.isBlank()) {
                authoredDialogues.add(new AuthoredDialogue(
                        interactionId,
                        speakerName,
                        bodyText,
                        followUpInteractionId,
                        visualPath,
                        choices,
                        nodes,
                        readQuestRewards(properties, prefix + "reward."),
                        properties.getProperty(prefix + "firstTalkNodeId", ""),
                        properties.getProperty(prefix + "repeatTalkNodeId", "")
                ));
            }
        }

        String questRoot = "quest";
        int authoredQuestCount = readInt(properties, questRoot + ".count", 0);
        List<AuthoredQuest> authoredQuests = new ArrayList<>();
        for (int i = 0; i < authoredQuestCount; i++) {
            String prefix = questRoot + "." + i + ".";
            String questId = properties.getProperty(prefix + "questId", "");
            String questName = properties.getProperty(prefix + "displayName", "");
            int stageCount = readInt(properties, prefix + "stage.count", 0);
            List<QuestStage> stages = new ArrayList<>();
            for (int stageIndex = 0; stageIndex < stageCount; stageIndex++) {
                String stagePrefix = prefix + "stage." + stageIndex + ".";
                String stageId = properties.getProperty(stagePrefix + "stageId", "");
                String journalText = properties.getProperty(stagePrefix + "journalText", "");
                String title = properties.getProperty(stagePrefix + "title", "");
                stages.add(new QuestStage(
                        stageId,
                        title,
                        journalText,
                        readEnum(
                                properties,
                                stagePrefix + "completionMode",
                                QuestCompletionMode.FLOW_CONFIRMED
                        ),
                        readQuestObjectives(properties, stagePrefix + "objective."),
                        readQuestRewards(properties, stagePrefix + "reward."),
                        readQuestFlow(properties, stagePrefix + "flow.")
                ));
            }
            if (!questId.isBlank() && !questName.isBlank() && !stages.isEmpty()) {
                authoredQuests.add(new AuthoredQuest(
                        questId,
                        questName,
                        properties.getProperty(prefix + "summary", ""),
                        readQuestRequirements(properties, prefix + "requirement."),
                        readQuestFlow(properties, prefix + "offer."),
                        stages,
                        readQuestRewards(properties, prefix + "finalReward."),
                        readQuestFlow(properties, prefix + "epilogue.")
                ));
            }
        }

        String itemRoot = "item";
        int customItemCount = readInt(properties, itemRoot + ".count", 0);
        List<CustomItem> customItems = new ArrayList<>();
        for (int i = 0; i < customItemCount; i++) {
            String prefix = itemRoot + "." + i + ".";
            String itemId = properties.getProperty(prefix + "itemId", "");
            String itemName = properties.getProperty(prefix + "displayName", "");
            InventorySystem.ItemType itemType = readItemType(properties.getProperty(prefix + "itemType", ""), InventorySystem.ItemType.MISC);
            String iconPath = properties.getProperty(prefix + "iconPath", "");
            String paperDollOverlayPath = properties.getProperty(prefix + "paperDollOverlayPath", "");
            String useSoundPath = properties.getProperty(prefix + "useSoundPath", "");
            WeaponType weaponType = readWeaponType(properties.getProperty(prefix + "weaponType", ""), itemType);
            boolean twoHanded = Boolean.parseBoolean(properties.getProperty(prefix + "twoHanded", "false"));
            GearMaterial material = readMaterial(properties.getProperty(prefix + "material", ""), GearMaterial.NONE);
            int healAmount = readInt(properties, prefix + "healAmount", 0);
            int baseGoldValue = readInt(properties, prefix + "baseGoldValue", 10);
            String examineText = properties.getProperty(prefix + "examineText", "");
            PlayerStat statBonusTarget = readPlayerStat(properties.getProperty(prefix + "statBonusTarget", ""));
            boolean stackable = Boolean.parseBoolean(properties.getProperty(prefix + "stackable", "false"));
            boolean smithingRecipeEnabled = Boolean.parseBoolean(properties.getProperty(prefix + "smithingRecipeEnabled", "false"));
            int smithingRequiredBars = readInt(properties, prefix + "smithingRequiredBars", 1);
            int smithingRequiredLevel = readInt(properties, prefix + "smithingRequiredLevel", 1);
            int smithingXpReward = readInt(properties, prefix + "smithingXpReward", 25);
            int magicAccuracyBonus = readInt(properties, prefix + "magicAccuracyBonus", 0);
            int magicPowerBonus = readInt(properties, prefix + "magicPowerBonus", 0);
            String firstPersonModelPath = properties.getProperty(prefix + "firstPersonModelPath", "");
            EquipmentViewModelProfile viewModelProfile = new EquipmentViewModelProfile(
                    readDouble(properties, prefix + "viewModel.positionX", 0.38),
                    readDouble(properties, prefix + "viewModel.positionY", -0.45),
                    readDouble(properties, prefix + "viewModel.positionZ", -0.86),
                    readDouble(properties, prefix + "viewModel.rotationX", -16.0),
                    readDouble(properties, prefix + "viewModel.rotationY", 8.0),
                    readDouble(properties, prefix + "viewModel.rotationZ", -28.0),
                    readDouble(properties, prefix + "viewModel.normalizedHeight", 0.72),
                    readDouble(properties, prefix + "viewModel.swingAxisX", 0.0),
                    readDouble(properties, prefix + "viewModel.swingAxisY", 0.0),
                    readDouble(properties, prefix + "viewModel.swingAxisZ", 1.0),
                    Boolean.parseBoolean(properties.getProperty(prefix + "viewModel.pairedHands", "false"))
            );
            if (!itemId.isBlank() && !itemName.isBlank()) {
                customItems.add(new CustomItem(
                        itemId,
                        itemName,
                        itemType,
                        iconPath,
                        paperDollOverlayPath,
                        useSoundPath,
                        weaponType,
                        twoHanded,
                        material,
                        healAmount,
                        baseGoldValue,
                        examineText,
                        statBonusTarget,
                        stackable,
                        smithingRecipeEnabled,
                        smithingRequiredBars,
                        smithingRequiredLevel,
                        smithingXpReward,
                        magicAccuracyBonus,
                        magicPowerBonus,
                        firstPersonModelPath,
                        viewModelProfile
                ));
            }
        }

        String gatheringNodeRoot = "gatheringNode";
        int customGatheringNodeCount = readInt(properties, gatheringNodeRoot + ".count", 0);
        List<CustomGatheringNode> customGatheringNodes = new ArrayList<>();
        for (int i = 0; i < customGatheringNodeCount; i++) {
            String prefix = gatheringNodeRoot + "." + i + ".";
            String nodeId = properties.getProperty(prefix + "nodeId", "");
            String nodeName = properties.getProperty(prefix + "displayName", "");
            GatheringNodeType nodeType = readGatheringNodeType(properties.getProperty(prefix + "nodeType", ""));
            CharacterSkill gatheringSkill = readCharacterSkill(
                    properties.getProperty(prefix + "gatheringSkill", ""),
                    defaultGatheringSkill(nodeType)
            );
            if (nodeType == GatheringNodeType.TREE && gatheringSkill == CharacterSkill.MINING) {
                gatheringSkill = CharacterSkill.WOODCUTTING;
            }
            int requiredLevel = readInt(properties, prefix + "requiredLevel", 1);
            String outputItemId = properties.getProperty(prefix + "outputItemId", "");
            int gatherXpReward = readInt(properties, prefix + "gatherXpReward", 18);
            String smeltOutputItemId = properties.getProperty(prefix + "smeltOutputItemId", "");
            int smeltRequiredLevel = readInt(properties, prefix + "smeltRequiredLevel", 1);
            int smeltXpReward = readInt(properties, prefix + "smeltXpReward", 7);
            double visualScale = readDouble(properties, prefix + "visualScale", nodeType == GatheringNodeType.MINING_ROCK ? 1.35 : 1.0);
            int frameDurationMs = readInt(properties, prefix + "frameDurationMs", nodeType == GatheringNodeType.FISHING_SPOT ? 260 : 1000);
            int lootCount = readInt(properties, prefix + "loot.count", 0);
            List<CustomDropEntry> lootEntries = new ArrayList<>();
            for (int lootIndex = 0; lootIndex < lootCount; lootIndex++) {
                String lootPrefix = prefix + "loot." + lootIndex + ".";
                String itemId = properties.getProperty(lootPrefix + "itemId", "");
                double chance = readDouble(properties, lootPrefix + "chance", 1.0);
                if (!itemId.isBlank()) {
                    lootEntries.add(new CustomDropEntry(itemId, chance));
                }
            }
            int frameCount = readInt(properties, prefix + "frame.count", 0);
            List<String> framePaths = new ArrayList<>();
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                String framePath = properties.getProperty(prefix + "frame." + frameIndex, "");
                if (!framePath.isBlank()) {
                    framePaths.add(framePath);
                }
            }
            int modelCount = readInt(properties, prefix + "model.count", 0);
            List<String> modelPaths = new ArrayList<>();
            for (int modelIndex = 0; modelIndex < modelCount; modelIndex++) {
                String modelPath = properties.getProperty(prefix + "model." + modelIndex, "");
                if (!modelPath.isBlank()) {
                    modelPaths.add(modelPath);
                }
            }
            if (nodeType == GatheringNodeType.TREE && framePaths.size() > 2) {
                framePaths = List.of(framePaths.get(0), framePaths.get(framePaths.size() - 1));
            }
            LightAttachment light = readLightAttachment(properties, prefix + "light.");

            if (!nodeId.isBlank() && !nodeName.isBlank()) {
                customGatheringNodes.add(new CustomGatheringNode(
                        nodeId,
                        nodeName,
                        nodeType,
                        requiredLevel,
                        outputItemId,
                        gatherXpReward,
                        smeltOutputItemId,
                        smeltXpReward,
                        framePaths,
                        modelPaths,
                        frameDurationMs,
                        visualScale,
                        gatheringSkill,
                        lootEntries,
                        smeltRequiredLevel,
                        light
                ));
            }
        }

        String cookingRecipeRoot = "cookingRecipe";
        int customCookingRecipeCount = readInt(properties, cookingRecipeRoot + ".count", 0);
        List<CustomCookingRecipe> customCookingRecipes = new ArrayList<>();
        for (int i = 0; i < customCookingRecipeCount; i++) {
            String prefix = cookingRecipeRoot + "." + i + ".";
            String recipeId = properties.getProperty(prefix + "recipeId", "");
            String recipeName = properties.getProperty(prefix + "displayName", "");
            String rawItemId = properties.getProperty(prefix + "rawItemId", "");
            String cookedItemId = properties.getProperty(prefix + "cookedItemId", "");
            String burntItemId = properties.getProperty(prefix + "burntItemId", "");
            int requiredLevel = readInt(properties, prefix + "requiredLevel", 1);
            int xpReward = readInt(properties, prefix + "xpReward", 20);
            if (!recipeId.isBlank() && !recipeName.isBlank()) {
                customCookingRecipes.add(new CustomCookingRecipe(
                        recipeId,
                        recipeName,
                        rawItemId,
                        cookedItemId,
                        burntItemId,
                        requiredLevel,
                        xpReward
                ));
            }
        }

        String craftingRecipeRoot = "craftingRecipe";
        int customCompositeRecipeCount = readInt(properties, craftingRecipeRoot + ".count", 0);
        List<CraftingRecipe> craftingRecipes = new ArrayList<>();
        for (int i = 0; i < customCompositeRecipeCount; i++) {
            String prefix = craftingRecipeRoot + "." + i + ".";
            String recipeId = properties.getProperty(prefix + "recipeId", "");
            String recipeName = properties.getProperty(prefix + "displayName", "");
            CraftingRecipeCategory category = readCraftingRecipeCategory(properties.getProperty(prefix + "category", ""));
            String primaryItemId = properties.getProperty(prefix + "primaryItemId", "");
            String secondaryItemId = properties.getProperty(prefix + "secondaryItemId", "");
            String outputItemId = properties.getProperty(prefix + "outputItemId", "");
            CharacterSkill requiredSkill = readCharacterSkill(
                    properties.getProperty(prefix + "requiredSkill", ""),
                    CharacterSkill.CRAFTING
            );
            int requiredLevel = readInt(properties, prefix + "requiredLevel", 1);
            int xpReward = readInt(properties, prefix + "xpReward", 0);
            boolean consumePrimary = Boolean.parseBoolean(properties.getProperty(prefix + "consumePrimary", "true"));
            boolean consumeSecondary = Boolean.parseBoolean(properties.getProperty(prefix + "consumeSecondary", "true"));
            String smeltOutputItemId = properties.getProperty(prefix + "smeltOutputItemId", "");
            int smeltRequiredLevel = readInt(properties, prefix + "smeltRequiredLevel", 1);
            int smeltXpReward = readInt(properties, prefix + "smeltXpReward", 0);
            int primaryQuantity = readInt(properties, prefix + "primaryQuantity", 1);
            int secondaryQuantity = secondaryItemId.isBlank()
                    ? 0
                    : readInt(properties, prefix + "secondaryQuantity", 1);
            CraftingOutputType outputType = readCraftingOutputType(
                    properties.getProperty(prefix + "outputType", ""),
                    CraftingOutputType.ITEM
            );
            CraftingStationType outputStationType = readCraftingStationType(
                    properties.getProperty(prefix + "outputStationType", "")
            );
            int stationLifetimeMs = readInt(properties, prefix + "stationLifetimeMs", 300000);
            if (!recipeId.isBlank() && !recipeName.isBlank()) {
                craftingRecipes.add(new CraftingRecipe(
                        recipeId,
                        recipeName,
                        category,
                        primaryItemId,
                        secondaryItemId,
                        outputItemId,
                        requiredSkill,
                        requiredLevel,
                        xpReward,
                        consumePrimary,
                        consumeSecondary,
                        smeltOutputItemId,
                        smeltRequiredLevel,
                        smeltXpReward,
                        primaryQuantity,
                        secondaryQuantity,
                        outputType,
                        outputStationType,
                        stationLifetimeMs
                ));
            }
        }

        String mobRoot = "mob";
        int customMobCount = readInt(properties, mobRoot + ".count", 0);
        List<CustomMob> customMobs = new ArrayList<>();
        for (int i = 0; i < customMobCount; i++) {
            String prefix = mobRoot + "." + i + ".";
            String mobId = properties.getProperty(prefix + "mobId", "");
            String mobName = properties.getProperty(prefix + "displayName", "");
            String imagePath = properties.getProperty(prefix + "imagePath", "");
            String paperDollSourcePath = properties.getProperty(prefix + "paperDollSourcePath", "");
            EnumMap<PlayerStat, Integer> statValues = new EnumMap<>(PlayerStat.class);
            for (PlayerStat stat : PlayerStat.values()) {
                statValues.put(stat, readInt(properties, prefix + "stat." + stat.name(), stat == PlayerStat.VITALITY ? 1 : 0));
            }
            int xpReward = readInt(properties, prefix + "xpReward", 10);
            String mobDescription = properties.getProperty(prefix + "description", "");
            String attackSoundPath = properties.getProperty(prefix + "attackSoundPath", "");
            String damageSoundPath = properties.getProperty(prefix + "damageSoundPath", "");
            int combatAiIntelligence = readInt(properties, prefix + "combatAiIntelligence", statValues.getOrDefault(PlayerStat.INTELLIGENCE, 0));
            int awarenessRadius = readInt(properties, prefix + "awarenessRadius", 4);
            int movementIntervalMs = readInt(properties, prefix + "movementIntervalMs", 3000);
            int respawnDelayMs = readInt(properties, prefix + "respawnDelayMs", 300000);
            CharacterModelDefinition characterModel = readCharacterModel(properties, prefix + "model.");
            List<String> skillIds = readSkillIds(properties.getProperty(prefix + "skillIds", ""));
            int dropCount = readInt(properties, prefix + "drop.count", 0);
            List<CustomDropEntry> dropEntries = new ArrayList<>();
            for (int dropIndex = 0; dropIndex < dropCount; dropIndex++) {
                String dropPrefix = prefix + "drop." + dropIndex + ".";
                String itemId = properties.getProperty(dropPrefix + "itemId", "");
                double chance = readDouble(properties, dropPrefix + "chance", 0.0);
                if (!itemId.isBlank()) {
                    dropEntries.add(new CustomDropEntry(itemId, chance));
                }
            }
            if (!mobId.isBlank() && !mobName.isBlank()) {
                customMobs.add(new CustomMob(mobId, mobName, imagePath, paperDollSourcePath, statValues,
                        xpReward, mobDescription, attackSoundPath, damageSoundPath, combatAiIntelligence,
                        awarenessRadius, movementIntervalMs, respawnDelayMs, skillIds, dropEntries,
                        characterModel));
            }
        }

        String limbRoot = "limb";
        int customLimbCount = readInt(properties, limbRoot + ".count", 0);
        List<CustomLimb> customLimbs = new ArrayList<>();
        for (int i = 0; i < customLimbCount; i++) {
            String prefix = limbRoot + "." + i + ".";
            String limbId = properties.getProperty(prefix + "limbId", "");
            String limbName = properties.getProperty(prefix + "displayName", "");
            LimbSlot limbSlot = readLimbSlot(properties.getProperty(prefix + "limbSlot", ""), LimbSlot.HEAD);
            String iconPath = properties.getProperty(prefix + "iconPath", "");
            GearDurability condition = readDurability(properties.getProperty(prefix + "condition", ""), GearDurability.PERFECT);
            String limbDescription = properties.getProperty(prefix + "description", "");
            String sourceCreatureId = properties.getProperty(prefix + "sourceCreatureId", "");
            String paperDollSourcePath = properties.getProperty(prefix + "paperDollSourcePath", "");
            String firstPersonModelPath = properties.getProperty(prefix + "firstPersonModelPath", "");
            String firstPersonRigId = properties.getProperty(prefix + "firstPersonRigId", "");
            List<String> skillIds = readSkillIds(properties.getProperty(prefix + "skillIds", ""));
            EnumMap<PlayerStat, Integer> statBonuses = new EnumMap<>(PlayerStat.class);
            for (PlayerStat stat : PlayerStat.values()) {
                statBonuses.put(stat, readInt(properties, prefix + "stat." + stat.name(), 0));
            }
            if (!limbId.isBlank() && !limbName.isBlank()) {
                customLimbs.add(new CustomLimb(limbId, limbName, limbSlot, iconPath, condition,
                        limbDescription, sourceCreatureId, paperDollSourcePath, statBonuses, skillIds,
                        firstPersonModelPath, firstPersonRigId));
            }
        }

        String npcRoot = "npc";
        int customNpcCount = readInt(properties, npcRoot + ".count", 0);
        List<CustomNpc> customNpcs = new ArrayList<>();
        for (int i = 0; i < customNpcCount; i++) {
            String prefix = npcRoot + "." + i + ".";
            String npcId = properties.getProperty(prefix + "npcId", "");
            String npcName = properties.getProperty(prefix + "displayName", "");
            String imagePath = properties.getProperty(prefix + "imagePath", "");
            String talkSoundPath = properties.getProperty(prefix + "talkSoundPath", "");
            String interactionId = properties.getProperty(prefix + "interactionId", "");
            int questCount = Math.max(0, readInt(properties, prefix + "quest.count", 0));
            List<String> questIds = new ArrayList<>();
            for (int questIndex = 0; questIndex < questCount; questIndex++) {
                String assignedQuestId = properties.getProperty(prefix + "quest." + questIndex, "").trim();
                if (!assignedQuestId.isBlank()) {
                    questIds.add(assignedQuestId);
                }
            }
            CharacterModelDefinition characterModel = readCharacterModel(properties, prefix + "model.");
            CustomShop shop = null;
            if (Boolean.parseBoolean(properties.getProperty(prefix + "shop.enabled", "false"))) {
                int stockCount = Math.max(0, readInt(properties, prefix + "shop.stock.count", 0));
                List<CustomShopStock> stock = new ArrayList<>();
                for (int stockIndex = 0; stockIndex < stockCount; stockIndex++) {
                    String stockPrefix = prefix + "shop.stock." + stockIndex + ".";
                    stock.add(new CustomShopStock(
                            properties.getProperty(stockPrefix + "itemId", ""),
                            readInt(properties, stockPrefix + "quantity", 1),
                            readInt(properties, stockPrefix + "buyPrice", -1),
                            readInt(properties, stockPrefix + "sellPrice", -1)
                    ));
                }
                shop = new CustomShop(
                        properties.getProperty(prefix + "shop.name", npcName + "'s Shop"),
                        properties.getProperty(prefix + "shop.greeting", "Take a look at my wares."),
                        stock
                );
            }
            if (!npcId.isBlank() && !npcName.isBlank()) {
                customNpcs.add(new CustomNpc(
                        npcId, npcName, imagePath, talkSoundPath, interactionId, shop, characterModel, questIds));
            }
        }

        String furnitureRoot = "furniture";
        int customFurnitureCount = readInt(properties, furnitureRoot + ".count", 0);
        List<CustomFurnitureDefinition> customFurniture = new ArrayList<>();
        for (int i = 0; i < customFurnitureCount; i++) {
            String prefix = furnitureRoot + "." + i + ".";
            String furnitureId = properties.getProperty(prefix + "furnitureId", "");
            String furnitureName = properties.getProperty(prefix + "displayName", "");
            String category = properties.getProperty(prefix + "category", "");
            String modelPath = properties.getProperty(prefix + "modelPath", "");
            double defaultScale = readDouble(properties, prefix + "defaultScale", 1.0);
            boolean defaultBlocksMovement = Boolean.parseBoolean(properties.getProperty(prefix + "defaultBlocksMovement", "false"));
            String interactionId = properties.getProperty(prefix + "interactionId", "");
            LightAttachment light = readLightAttachment(properties, prefix + "light.");
            if (!furnitureId.isBlank() && !furnitureName.isBlank()) {
                customFurniture.add(new CustomFurnitureDefinition(
                        furnitureId,
                        furnitureName,
                        category,
                        modelPath,
                        defaultScale,
                        defaultBlocksMovement,
                        interactionId,
                        light
                ));
            }
        }

        MapDesign design = new MapDesign(width, height, displayName, description, musicPath, skyboxPath,
                primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry, mobAreas,
                placements, authoredDialogues, authoredQuests, customItems, customMobs, customLimbs,
                customNpcs, customFurniture, customGatheringNodes, customCookingRecipes, craftingRecipes, triggers,
                lightingSettings, lights, spawnX, spawnY);
        design.placedObjects().addAll(placedObjects);
        return design;
    }

    private static InputStream openMapDesignStream(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Map path is missing.");
        }

        if (Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }

        return AssetLoader.openAssetStream(resourcePath(path));
    }

    private static String resourcePath(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String resourcePrefix = "src/main/resources/";
        if (normalized.startsWith(resourcePrefix)) {
            return normalized.substring(resourcePrefix.length());
        }
        if (normalized.startsWith("assets/")) {
            return normalized;
        }
        if (!normalized.contains("/") && !normalized.contains("\\")) {
            return MAP_RESOURCE_FOLDER + "/" + normalized;
        }
        return normalized;
    }

    public static AuthoredContent loadSharedContent() throws IOException {
        return MapDesignContentStore.loadSharedContent();
    }

    public static AuthoredContent authoredContentOf(MapDesign design) {
        if (design == null) {
            return new AuthoredContent(
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of()
            );
        }
        return new AuthoredContent(
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.craftingRecipes()
        );
    }

    public static void mergeAuthoredContent(MapDesign design, AuthoredContent content) {
        if (design == null || content == null) {
            return;
        }
        mergeMissingById(design.authoredDialogues(), content.authoredDialogues(), AuthoredDialogue::interactionId);
        mergeMissingById(design.authoredQuests(), content.authoredQuests(), AuthoredQuest::questId);
        mergeMissingById(design.customItems(), content.customItems(), CustomItem::itemId);
        mergeMissingById(design.customMobs(), content.customMobs(), CustomMob::mobId);
        mergeMissingById(design.customLimbs(), content.customLimbs(), CustomLimb::limbId);
        mergeMissingById(design.customNpcs(), content.customNpcs(), CustomNpc::npcId);
        mergeMissingById(design.customFurniture(), content.customFurniture(), CustomFurnitureDefinition::furnitureId);
        mergeMissingById(design.customGatheringNodes(), content.customGatheringNodes(), CustomGatheringNode::nodeId);
        mergeMissingById(design.customCookingRecipes(), content.customCookingRecipes(), CustomCookingRecipe::recipeId);
        mergeMissingById(design.craftingRecipes(), content.craftingRecipes(), CraftingRecipe::recipeId);
    }

    static void replaceAuthoredContent(MapDesign design, AuthoredContent content) {
        if (design == null || content == null) {
            return;
        }
        replaceEntries(design.authoredDialogues(), content.authoredDialogues());
        replaceEntries(design.authoredQuests(), content.authoredQuests());
        replaceEntries(design.customItems(), content.customItems());
        replaceEntries(design.customMobs(), content.customMobs());
        replaceEntries(design.customLimbs(), content.customLimbs());
        replaceEntries(design.customNpcs(), content.customNpcs());
        replaceEntries(design.customFurniture(), content.customFurniture());
        replaceEntries(design.customGatheringNodes(), content.customGatheringNodes());
        replaceEntries(design.customCookingRecipes(), content.customCookingRecipes());
        replaceEntries(design.craftingRecipes(), content.craftingRecipes());
    }

    private static <T> void replaceEntries(List<T> target, List<T> source) {
        target.clear();
        target.addAll(source == null ? List.of() : source);
    }

    private static <T> void mergeMissingById(List<T> target, List<T> source, Function<T, String> idFunction) {
        for (T value : source == null ? List.<T>of() : source) {
            if (value == null) {
                continue;
            }
            String id = idFunction.apply(value);
            boolean exists = target.stream().anyMatch(existing ->
                    java.util.Objects.equals(idFunction.apply(existing), id)
            );
            if (!exists) {
                target.add(value);
            }
        }
    }

    public static void saveSharedContent(AuthoredContent content) throws IOException {
        MapDesignContentStore.saveSharedContent(content);
    }

    public static List<Path> listSavedMaps() throws IOException {
        List<Path> maps = new ArrayList<>();
        addMapFiles(maps, MAP_FOLDER);
        for (String resourcePath : AssetLoader.listAssetFiles(MAP_RESOURCE_FOLDER)) {
            if (resourcePath.toLowerCase(Locale.ROOT).endsWith(".properties")) {
                Path resourceMap = Path.of(resourcePath);
                if (maps.stream().noneMatch(path -> path.getFileName().equals(resourceMap.getFileName()))) {
                    maps.add(resourceMap);
                }
            }
        }
        addMapFiles(maps, DATA_MAP_FOLDER);
        maps.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return maps;
    }

    private static void addMapFiles(List<Path> maps, Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }

        try (Stream<Path> paths = Files.list(folder)) {
            paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".properties"))
                    .forEach(path -> {
                        if (maps.stream().noneMatch(existing -> existing.getFileName().equals(path.getFileName()))) {
                            maps.add(path);
                        }
                    });
        }
    }

    public static String resourcePathForMap(Path path) {
        if (path == null) {
            return "";
        }
        Path absoluteMapFolder = MAP_FOLDER.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        if (absolutePath.startsWith(absoluteMapFolder)) {
            return MAP_RESOURCE_FOLDER + "/" + absoluteMapFolder.relativize(absolutePath).toString().replace('\\', '/');
        }
        return resourcePath(path);
    }

    public static List<ValidationIssue> validate(MapDesign design) {
        return MapDesignValidator.validate(design);
    }

    public static List<ValidationIssue> validateQuestDialogueContent(
            List<AuthoredQuest> quests,
            List<AuthoredDialogue> dialogues,
            List<CustomNpc> npcs,
            List<CustomItem> items,
            List<CustomLimb> limbs,
            List<CustomMob> mobs
    ) {
        return MapDesignValidator.validateQuestDialogueContent(
                quests, dialogues, npcs, items, limbs, mobs);
    }

    public static boolean hasValidationErrors(MapDesign design) {
        return MapDesignValidator.hasValidationErrors(design);
    }

    public static DungeonMap toDungeonMap(MapDesign design) {
        return new DungeonMap(
                copyTiles(design.tiles()),
                copyThemes(design.themeIndexes()),
                design.mapPaint() == null
                        ? MapPaintData.blank(design.width(), design.height())
                        : design.mapPaint().copy(),
                design.mapGeometry() == null
                        ? MapGeometryData.blank(design.width(), design.height())
                        : design.mapGeometry().copy(),
                design.mobAreas() == null
                        ? MobAreaData.blank(design.width(), design.height())
                        : design.mobAreas().copy(),
                design.lightingSettings(),
                design.lights()
        );
    }

    public static GeneratedDungeon toGeneratedDungeon(MapDesign design, int playerX, int playerY) {
        DungeonMap dungeonMap = toDungeonMap(design);
        List<MapEntity> entities = new ArrayList<>();
        List<GeneratedDungeon.TileInteraction> tileInteractions = new ArrayList<>();

        for (MapPlacement placement : design.placements()) {
            hydratePlacement(dungeonMap, entities, tileInteractions, design.customItems(), design.customMobs(),
                    design.customLimbs(), design.customNpcs(), design.customFurniture(),
                    design.customGatheringNodes(), placement);
        }

        for (PlacedObjectInstance object : design.placedObjects()) {
            hydratePlacedObject(dungeonMap, entities, tileInteractions, design, object);
        }

        GridPoint spawn = resolveSpawn(dungeonMap, playerX, playerY);
        return new GeneratedDungeon(
                dungeonMap,
                entities,
                spawn.x(),
                spawn.y(),
                tileInteractions,
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customLimbs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                design.customCookingRecipes(),
                design.craftingRecipes(),
                design.triggers()
        );
    }

    public static GeneratedDungeon toGeneratedDungeon(MapDesign design) {
        return toGeneratedDungeon(design, design.spawnX(), design.spawnY());
    }

    private static boolean isBorder(int x, int y, int width, int height) {
        return x == 0 || y == 0 || x == width - 1 || y == height - 1;
    }

    private static String fallbackDisplayName(Path path) {
        if (path == null || path.getFileName() == null) {
            return "Untitled Map";
        }

        return path.getFileName().toString().replaceFirst("[.][^.]+$", "");
    }

    private static void hydratePlacement(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            List<GeneratedDungeon.TileInteraction> tileInteractions,
            List<CustomItem> customItems,
            List<CustomMob> customMobs,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs,
            List<CustomFurnitureDefinition> customFurniture,
            List<CustomGatheringNode> customGatheringNodes,
            MapPlacement placement
    ) {
        if (placement == null || !isInside(dungeonMap, placement.x(), placement.y())) {
            return;
        }

        try {
            switch (placement.kind()) {
                case CRAFTING_NODE -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(CraftingStationType.valueOf(placement.id()).createEntity(placement.x(), placement.y()));
                }
                case GATHERING_NODE -> hydrateGatheringNode(dungeonMap, entities, tileInteractions, customGatheringNodes, placement);
                case FURNITURE -> {
                    CustomFurnitureDefinition furniture = findCustomFurniture(placement.id(), customFurniture);
                    if (furniture != null) {
                        PlacedObjectInstance object = PlacedObjectInstance.furniture(
                                "furniture_" + placement.id() + "_" + placement.x() + "_" + placement.y(),
                                placement.id(),
                                placement.x(),
                                placement.y(),
                                furniture.defaultBlocksMovement()
                        );
                        hydrateFurniture(dungeonMap, entities, furniture, object);
                    }
                }
                case CUSTOM_NPC -> {
                    CustomNpc npc = findCustomNpc(placement.id(), customNpcs);
                    if (npc == null) {
                        return;
                    }
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(npc.createEntity(placement.x(), placement.y()));
                }
                case ITEM -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(new MapEntity(createItem(placement.id(), customItems, customLimbs), placement.x(), placement.y()));
                }
                case ENEMY -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    CustomMob customMob = findCustomMob(placement.id(), customMobs);
                    if (customMob != null) {
                        String spawnId = placement.id() + "|" + placement.x() + "|" + placement.y();
                        entities.add(new MapEntity(customMob.createMonster(), placement.x(), placement.y())
                                .configureEnemySpawn(
                                        spawnId,
                                        placement.x(),
                                        placement.y(),
                                        dungeonMap.getMobAreaId(placement.x(), placement.y()),
                                        customMob.awarenessRadius(),
                                        customMob.movementIntervalMs(),
                                        customMob.respawnDelayMs()
                                ));
                    }
                }
                case INTERACTION -> tileInteractions.add(new GeneratedDungeon.TileInteraction(
                        placement.x(),
                        placement.y(),
                        placement.id()
                ));
            }
        } catch (IllegalArgumentException ignored) {
            // Bad editor/project data should not prevent the rest of the map from loading.
        }
    }

    private static InventorySystem.Item createItem(String itemId, List<CustomItem> customItems, List<CustomLimb> customLimbs) {
        CustomItem customItem = findCustomItem(itemId, customItems);
        if (customItem != null) {
            return customItem.createItem();
        }

        CustomLimb customLimb = findCustomLimb(itemId, customLimbs);
        if (customLimb != null) {
            return customLimb.createLimb();
        }

        return null;
    }

    public static String itemDisplayName(String itemIdOrName, List<CustomItem> customItems) {
        if (itemIdOrName == null || itemIdOrName.isBlank()) {
            return "";
        }
        CustomItem customItem = findCustomItem(itemIdOrName, customItems);
        if (customItem != null) {
            return customItem.displayName();
        }
        if (customItems != null) {
            for (CustomItem item : customItems) {
                if (itemIdOrName.equalsIgnoreCase(item.displayName())) {
                    return item.displayName();
                }
            }
        }
        return itemIdOrName;
    }

    static CustomItem findCustomItem(String itemId, List<CustomItem> customItems) {
        if (itemId == null || customItems == null) {
            return null;
        }

        for (CustomItem item : customItems) {
            if (itemId.equals(item.itemId())) {
                return item;
            }
        }

        return null;
    }

    static CustomMob findCustomMob(String mobId, List<CustomMob> customMobs) {
        if (mobId == null || customMobs == null) {
            return null;
        }

        for (CustomMob mob : customMobs) {
            if (mobId.equals(mob.mobId())) {
                return mob;
            }
        }

        return null;
    }

    static CustomLimb findCustomLimb(String limbId, List<CustomLimb> customLimbs) {
        if (limbId == null || customLimbs == null) {
            return null;
        }

        for (CustomLimb limb : customLimbs) {
            if (limbId.equals(limb.limbId())) {
                return limb;
            }
        }

        return null;
    }

    static CustomNpc findCustomNpc(String npcId, List<CustomNpc> customNpcs) {
        if (npcId == null || customNpcs == null) {
            return null;
        }

        for (CustomNpc npc : customNpcs) {
            if (npcId.equals(npc.npcId())) {
                return npc;
            }
        }

        return null;
    }

    static CustomFurnitureDefinition findCustomFurniture(String furnitureId, List<CustomFurnitureDefinition> customFurniture) {
        if (furnitureId == null || customFurniture == null) {
            return null;
        }

        for (CustomFurnitureDefinition furniture : customFurniture) {
            if (furnitureId.equals(furniture.furnitureId())) {
                return furniture;
            }
        }

        return null;
    }

    public static CustomGatheringNode findCustomGatheringNode(String nodeId, List<CustomGatheringNode> customGatheringNodes) {
        if (nodeId == null || customGatheringNodes == null) {
            return null;
        }

        for (CustomGatheringNode node : customGatheringNodes) {
            if (nodeId.equals(node.nodeId()) || nodeId.equals(node.interactionId())) {
                return node;
            }
        }

        return null;
    }

    private static void hydrateGatheringNode(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            List<GeneratedDungeon.TileInteraction> tileInteractions,
            List<CustomGatheringNode> customGatheringNodes,
            MapPlacement placement
    ) {
        CustomGatheringNode customNode = findCustomGatheringNode(placement.id(), customGatheringNodes);
        if (customNode != null) {
            PlacedObjectInstance object = defaultGatheringObjectForPlacement(customNode, placement);
            if (customNode.nodeType() == GatheringNodeType.FISHING_SPOT) {
                dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FISHING_WATER);
                tileInteractions.add(new GeneratedDungeon.TileInteraction(
                        placement.x(),
                        placement.y(),
                        customNode.interactionId()
                ));
                addGatheringNodeLight(dungeonMap, customNode, object);
                return;
            }

            dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
            entities.add(customNode.createEntity(placement.x(), placement.y()));
            addGatheringNodeLight(dungeonMap, customNode, object);
            return;
        }

        dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
    }

    private static GridPoint resolveSpawn(DungeonMap dungeonMap, int requestedX, int requestedY) {
        if (isWalkableSpawn(dungeonMap, requestedX, requestedY)) {
            return new GridPoint(requestedX, requestedY);
        }

        for (int y = 0; y < dungeonMap.getHeight(); y++) {
            for (int x = 0; x < dungeonMap.getWidth(); x++) {
                if (isWalkableSpawn(dungeonMap, x, y)) {
                    return new GridPoint(x, y);
                }
            }
        }

        return new GridPoint(0, 0);
    }

    private static boolean isWalkableSpawn(DungeonMap dungeonMap, int x, int y) {
        return isInside(dungeonMap, x, y) && dungeonMap.isWalkable(x, y);
    }

    private static boolean isInside(DungeonMap dungeonMap, int x, int y) {
        return dungeonMap != null
                && x >= 0
                && y >= 0
                && x < dungeonMap.getWidth()
                && y < dungeonMap.getHeight();
    }

    private static boolean isInside(MapDesign design, int x, int y) {
        return design != null
                && x >= 0
                && y >= 0
                && x < design.width()
                && y < design.height();
    }

    private static void writeAuthoredDialogueChoice(Properties properties, String prefix, AuthoredDialogueChoice choice) {
        properties.setProperty(prefix + "choiceId", choice.choiceId());
        properties.setProperty(prefix + "label", choice.label());
        properties.setProperty(prefix + "bodyText", choice.bodyText());
        properties.setProperty(prefix + "targetNodeId", choice.targetNodeId());
        properties.setProperty(prefix + "requiredItemName", choice.requiredItemName());
        properties.setProperty(prefix + "takeItemName", choice.takeItemName());
        properties.setProperty(prefix + "takeItemAmount", String.valueOf(choice.takeItemAmount()));
        properties.setProperty(prefix + "firstTalkOnly", String.valueOf(choice.firstTalkOnly()));
        writeQuestRewards(properties, prefix + "reward.", choice.rewards());
    }

    private static AuthoredDialogueChoice readAuthoredDialogueChoice(Properties properties, String prefix) {
        String label = properties.getProperty(prefix + "label", "");
        String choiceBodyText = properties.getProperty(prefix + "bodyText", "");
        String targetNodeId = properties.getProperty(prefix + "targetNodeId", "");
        String requiredItemName = properties.getProperty(prefix + "requiredItemName", "");
        String takeItemName = properties.getProperty(prefix + "takeItemName", "");
        int takeItemAmount = readInt(properties, prefix + "takeItemAmount", 0);
        boolean firstTalkOnly = Boolean.parseBoolean(properties.getProperty(prefix + "firstTalkOnly", "false"));
        if (label.isBlank() || (choiceBodyText.isBlank() && targetNodeId.isBlank())) {
            return null;
        }
        return new AuthoredDialogueChoice(
                label,
                choiceBodyText,
                targetNodeId,
                requiredItemName,
                takeItemName,
                takeItemAmount,
                firstTalkOnly,
                properties.getProperty(prefix + "choiceId", ""),
                readQuestRewards(properties, prefix + "reward.")
        );
    }

    private static void writeQuestRequirements(
            Properties properties,
            String prefix,
            List<QuestRequirement> requirements
    ) {
        List<QuestRequirement> safe = requirements == null ? List.of() : requirements;
        properties.setProperty(prefix + "count", String.valueOf(safe.size()));
        for (int index = 0; index < safe.size(); index++) {
            QuestRequirement requirement = safe.get(index);
            String entryPrefix = prefix + index + ".";
            properties.setProperty(entryPrefix + "type", requirement.type().name());
            properties.setProperty(entryPrefix + "targetId", requirement.targetId());
            properties.setProperty(entryPrefix + "skill", requirement.skill() == null ? "" : requirement.skill().name());
            properties.setProperty(entryPrefix + "amount", String.valueOf(requirement.amount()));
        }
    }

    private static List<QuestRequirement> readQuestRequirements(Properties properties, String prefix) {
        int count = Math.max(0, readInt(properties, prefix + "count", 0));
        List<QuestRequirement> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String entryPrefix = prefix + index + ".";
            result.add(new QuestRequirement(
                    readEnum(properties, entryPrefix + "type", QuestRequirementType.POSSESS_ITEM),
                    properties.getProperty(entryPrefix + "targetId", ""),
                    readSkill(properties.getProperty(entryPrefix + "skill", "")),
                    readInt(properties, entryPrefix + "amount", 1)
            ));
        }
        return List.copyOf(result);
    }

    private static void writeQuestObjectives(
            Properties properties,
            String prefix,
            List<QuestObjective> objectives
    ) {
        List<QuestObjective> safe = objectives == null ? List.of() : objectives;
        properties.setProperty(prefix + "count", String.valueOf(safe.size()));
        for (int index = 0; index < safe.size(); index++) {
            QuestObjective objective = safe.get(index);
            String entryPrefix = prefix + index + ".";
            properties.setProperty(entryPrefix + "objectiveId", objective.objectiveId());
            properties.setProperty(entryPrefix + "type", objective.type().name());
            properties.setProperty(entryPrefix + "targetId", objective.targetId());
            properties.setProperty(entryPrefix + "skill", objective.skill() == null ? "" : objective.skill().name());
            properties.setProperty(entryPrefix + "amount", String.valueOf(objective.amount()));
            properties.setProperty(entryPrefix + "journalText", objective.journalText());
            properties.setProperty(entryPrefix + "visible", String.valueOf(objective.visible()));
        }
    }

    private static List<QuestObjective> readQuestObjectives(Properties properties, String prefix) {
        int count = Math.max(0, readInt(properties, prefix + "count", 0));
        List<QuestObjective> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String entryPrefix = prefix + index + ".";
            result.add(new QuestObjective(
                    properties.getProperty(entryPrefix + "objectiveId", ""),
                    readEnum(properties, entryPrefix + "type", QuestObjectiveType.POSSESS_ITEM),
                    properties.getProperty(entryPrefix + "targetId", ""),
                    readSkill(properties.getProperty(entryPrefix + "skill", "")),
                    readInt(properties, entryPrefix + "amount", 1),
                    properties.getProperty(entryPrefix + "journalText", ""),
                    Boolean.parseBoolean(properties.getProperty(entryPrefix + "visible", "true"))
            ));
        }
        return List.copyOf(result);
    }

    private static void writeQuestRewards(
            Properties properties,
            String prefix,
            List<RewardDefinition> rewards
    ) {
        List<RewardDefinition> safe = rewards == null ? List.of() : rewards;
        properties.setProperty(prefix + "count", String.valueOf(safe.size()));
        for (int index = 0; index < safe.size(); index++) {
            RewardDefinition reward = safe.get(index);
            String entryPrefix = prefix + index + ".";
            properties.setProperty(entryPrefix + "rewardId", reward.rewardId());
            properties.setProperty(entryPrefix + "type", reward.type().name());
            properties.setProperty(entryPrefix + "itemId", reward.itemId());
            properties.setProperty(entryPrefix + "skill", reward.skill() == null ? "" : reward.skill().name());
            properties.setProperty(entryPrefix + "amount", String.valueOf(reward.amount()));
        }
    }

    private static List<RewardDefinition> readQuestRewards(Properties properties, String prefix) {
        int count = Math.max(0, readInt(properties, prefix + "count", 0));
        List<RewardDefinition> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String entryPrefix = prefix + index + ".";
            result.add(new RewardDefinition(
                    properties.getProperty(entryPrefix + "rewardId", ""),
                    readEnum(properties, entryPrefix + "type", QuestRewardType.ITEM),
                    properties.getProperty(entryPrefix + "itemId", ""),
                    readSkill(properties.getProperty(entryPrefix + "skill", "")),
                    readInt(properties, entryPrefix + "amount", 1)
            ));
        }
        return List.copyOf(result);
    }

    private static void writeQuestFlow(Properties properties, String prefix, QuestFlow flow) {
        QuestFlow safe = flow == null ? QuestFlow.empty() : flow;
        properties.setProperty(prefix + "entryNodeId", safe.entryNodeId());
        properties.setProperty(prefix + "node.count", String.valueOf(safe.nodes().size()));
        for (int nodeIndex = 0; nodeIndex < safe.nodes().size(); nodeIndex++) {
            QuestFlowNode node = safe.nodes().get(nodeIndex);
            String nodePrefix = prefix + "node." + nodeIndex + ".";
            properties.setProperty(nodePrefix + "nodeId", node.nodeId());
            properties.setProperty(nodePrefix + "bodyText", node.bodyText());
            properties.setProperty(nodePrefix + "canvasX", String.valueOf(node.canvasX()));
            properties.setProperty(nodePrefix + "canvasY", String.valueOf(node.canvasY()));
            properties.setProperty(nodePrefix + "choice.count", String.valueOf(node.choices().size()));
            for (int choiceIndex = 0; choiceIndex < node.choices().size(); choiceIndex++) {
                QuestFlowChoice choice = node.choices().get(choiceIndex);
                String choicePrefix = nodePrefix + "choice." + choiceIndex + ".";
                properties.setProperty(choicePrefix + "choiceId", choice.choiceId());
                properties.setProperty(choicePrefix + "label", choice.label());
                properties.setProperty(choicePrefix + "targetNodeId", choice.targetNodeId());
                properties.setProperty(choicePrefix + "action", choice.action().name());
                properties.setProperty(choicePrefix + "requiredItemId", choice.requiredItemId());
                properties.setProperty(choicePrefix + "takeItemId", choice.takeItemId());
                properties.setProperty(choicePrefix + "takeItemAmount", String.valueOf(choice.takeItemAmount()));
                properties.setProperty(choicePrefix + "firstTalkOnly", String.valueOf(choice.firstTalkOnly()));
                properties.setProperty(choicePrefix + "terminalBodyText", choice.terminalBodyText());
                writeQuestRequirements(properties, choicePrefix + "condition.", choice.conditions());
                writeQuestRewards(properties, choicePrefix + "reward.", choice.rewards());
            }
        }
    }

    private static QuestFlow readQuestFlow(Properties properties, String prefix) {
        int nodeCount = Math.max(0, readInt(properties, prefix + "node.count", 0));
        List<QuestFlowNode> nodes = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            String nodePrefix = prefix + "node." + nodeIndex + ".";
            int choiceCount = Math.max(0, readInt(properties, nodePrefix + "choice.count", 0));
            List<QuestFlowChoice> choices = new ArrayList<>();
            for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
                String choicePrefix = nodePrefix + "choice." + choiceIndex + ".";
                choices.add(new QuestFlowChoice(
                        properties.getProperty(
                                choicePrefix + "choiceId",
                                "node_" + nodeIndex + "_choice_" + choiceIndex
                        ),
                        properties.getProperty(choicePrefix + "label", "Continue"),
                        properties.getProperty(choicePrefix + "targetNodeId", ""),
                        readQuestRequirements(properties, choicePrefix + "condition."),
                        readEnum(properties, choicePrefix + "action", QuestFlowAction.NONE),
                        properties.getProperty(choicePrefix + "requiredItemId", ""),
                        properties.getProperty(choicePrefix + "takeItemId", ""),
                        readInt(
                                properties,
                                choicePrefix + "takeItemAmount",
                                properties.getProperty(choicePrefix + "takeItemId", "").isBlank() ? 0 : 1
                        ),
                        readQuestRewards(properties, choicePrefix + "reward."),
                        Boolean.parseBoolean(properties.getProperty(choicePrefix + "firstTalkOnly", "false")),
                        properties.getProperty(choicePrefix + "terminalBodyText", "")
                ));
            }
            nodes.add(new QuestFlowNode(
                    properties.getProperty(nodePrefix + "nodeId", "node_" + nodeIndex),
                    properties.getProperty(nodePrefix + "bodyText", ""),
                    readInt(properties, nodePrefix + "canvasX", 80 + nodeIndex * 260),
                    readInt(properties, nodePrefix + "canvasY", 80),
                    choices
            ));
        }
        String entryNodeId = properties.getProperty(prefix + "entryNodeId", "");
        if (entryNodeId.isBlank() && !nodes.isEmpty()) {
            entryNodeId = nodes.get(0).nodeId();
        }
        return new QuestFlow(entryNodeId, nodes);
    }

    private static String joinTileRow(Library.TileType[] row) {
        List<String> values = new ArrayList<>();
        for (Library.TileType tile : row) {
            values.add(tile.name());
        }
        return String.join(",", values);
    }

    private static String joinThemeRow(int[] row) {
        List<String> values = new ArrayList<>();
        for (int themeIndex : row) {
            values.add(String.valueOf(Math.max(0, Math.min(1, themeIndex))));
        }
        return String.join(",", values);
    }

    private static void writeMapPaint(Properties properties, MapPaintData mapPaint) {
        if (mapPaint == null) {
            return;
        }

        for (MapPaintData.Layer layer : MapPaintData.Layer.values()) {
            String prefix = "paint." + layer.name().toLowerCase(Locale.ROOT) + ".";
            String[][] rows = mapPaint.copyLayer(layer);
            for (int y = 0; y < rows.length; y++) {
                properties.setProperty(prefix + y, joinPaintRow(rows[y]));
            }
        }
    }

    private static void hydratePlacedObject(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            List<GeneratedDungeon.TileInteraction> tileInteractions,
            MapDesign design,
            PlacedObjectInstance object
    ) {
        if (object == null || !isInside(dungeonMap, object.x(), object.y())) {
            return;
        }

        if (object.kind() == PlacementKind.FURNITURE) {
            CustomFurnitureDefinition furniture = findCustomFurniture(object.id(), design.customFurniture());
            if (furniture != null) {
                hydrateFurniture(dungeonMap, entities, furniture, object);
            }
            return;
        }

        if (object.kind() == PlacementKind.GATHERING_NODE) {
            hydrateGatheringNodeObject(dungeonMap, entities, tileInteractions, design.customGatheringNodes(), object);
            return;
        }

        hydratePlacement(
                dungeonMap,
                entities,
                tileInteractions,
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
                design.customFurniture(),
                design.customGatheringNodes(),
                new MapPlacement(object.kind(), object.id(), object.x(), object.y())
        );
    }

    private static void hydrateGatheringNodeObject(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            List<GeneratedDungeon.TileInteraction> tileInteractions,
            List<CustomGatheringNode> customGatheringNodes,
            PlacedObjectInstance object
    ) {
        CustomGatheringNode customNode = findCustomGatheringNode(object.id(), customGatheringNodes);
        if (customNode == null) {
            return;
        }

        if (customNode.nodeType() == GatheringNodeType.FISHING_SPOT) {
            dungeonMap.setTile(object.x(), object.y(), Library.TileType.FISHING_WATER);
            tileInteractions.add(new GeneratedDungeon.TileInteraction(
                    object.x(),
                    object.y(),
                    customNode.interactionId()
            ));
            if (customNode.modelPaths().isEmpty()) {
                addGatheringNodeLight(dungeonMap, customNode, object);
                return;
            }
        } else {
            dungeonMap.setTile(object.x(), object.y(), Library.TileType.FLOOR);
        }

        MapEntity entity = customNode.createEntity(object.x(), object.y());
        if (entity != null) {
            applyPlacedObjectTransform(entity, object);
            entities.add(entity);
        }
        addGatheringNodeLight(dungeonMap, customNode, object);
    }

    private static PlacedObjectInstance defaultGatheringObjectForPlacement(
            CustomGatheringNode customNode,
            MapPlacement placement
    ) {
        boolean blocksMovement = customNode.nodeType() == GatheringNodeType.MINING_ROCK
                || customNode.nodeType() == GatheringNodeType.TREE;
        return new PlacedObjectInstance(
                "gathering_" + customNode.nodeId() + "_" + placement.x() + "_" + placement.y(),
                PlacementKind.GATHERING_NODE,
                customNode.nodeId(),
                placement.x(),
                placement.y(),
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                blocksMovement,
                null
        );
    }

    private static void addGatheringNodeLight(
            DungeonMap dungeonMap,
            CustomGatheringNode customNode,
            PlacedObjectInstance object
    ) {
        if (dungeonMap == null || customNode == null || object == null) {
            return;
        }
        MapLight light = customNode.createLight(object);
        if (light != null) {
            dungeonMap.addLight(light);
        }
    }

    private static void applyPlacedObjectTransform(MapEntity entity, PlacedObjectInstance object) {
        entity.withStaticModelTransform(
                object.offsetX(),
                object.offsetY(),
                object.offsetZ(),
                object.yawDegrees(),
                object.pitchDegrees(),
                object.rollDegrees(),
                object.scale()
        );
        entity.withStaticModelBrightness(object.modelBrightness());
        if (object.blocksMovement()) {
            entity.blocksMovement(true);
        }
    }

    private static void addPlacedObjectLight(DungeonMap dungeonMap, PlacedObjectInstance object) {
        if (dungeonMap == null || object == null || object.lightOverride() == null) {
            return;
        }
        MapLight light = object.lightOverride().toMapLight("object_" + object.instanceId(), object);
        if (light != null) {
            dungeonMap.addLight(light);
        }
    }

    private static void hydrateFurniture(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            CustomFurnitureDefinition furniture,
            PlacedObjectInstance object
    ) {
        dungeonMap.setTile(object.x(), object.y(), Library.TileType.FLOOR);
        MapEntity entity = furniture.createEntity(object);
        if (entity != null) {
            entities.add(entity);
        }
        MapLight light = furniture.createLight(object);
        if (light != null) {
            dungeonMap.addLight(light);
        }
    }

    private static void writeLighting(Properties properties, MapLightingSettings settings, List<MapLight> lights) {
        MapLightingSettings safeSettings = settings == null ? MapLightingSettings.defaultSettings() : settings;
        properties.setProperty("lighting.enabled", String.valueOf(safeSettings.lightingEnabled()));
        properties.setProperty("lighting.ambientColor", MapLightingSettings.colorHex(safeSettings.ambientColorRgb()));
        properties.setProperty("lighting.ambientIntensity", String.valueOf(safeSettings.ambientIntensity()));
        properties.setProperty("lighting.fogEnabled", String.valueOf(safeSettings.fogEnabled()));
        properties.setProperty("lighting.fogColor", MapLightingSettings.colorHex(safeSettings.fogColorRgb()));
        properties.setProperty("lighting.fogDensity", String.valueOf(safeSettings.fogDensity()));

        List<MapLight> safeLights = lights == null ? List.of() : lights;
        properties.setProperty("light.count", String.valueOf(safeLights.size()));
        for (int i = 0; i < safeLights.size(); i++) {
            MapLight light = safeLights.get(i);
            String prefix = "light." + i + ".";
            properties.setProperty(prefix + "id", light.id());
            properties.setProperty(prefix + "x", String.valueOf(light.x()));
            properties.setProperty(prefix + "y", String.valueOf(light.y()));
            properties.setProperty(prefix + "color", MapLightingSettings.colorHex(light.colorRgb()));
            properties.setProperty(prefix + "radius", String.valueOf(light.radius()));
            properties.setProperty(prefix + "intensity", String.valueOf(light.intensity()));
            properties.setProperty(prefix + "heightOffset", String.valueOf(light.heightOffset()));
            properties.setProperty(prefix + "offsetX", String.valueOf(light.offsetX()));
            properties.setProperty(prefix + "offsetZ", String.valueOf(light.offsetZ()));
            properties.setProperty(prefix + "flicker", String.valueOf(light.flickerAmount()));
            properties.setProperty(prefix + "enabled", String.valueOf(light.enabled()));
        }
    }

    private static MapLightingSettings readLightingSettings(Properties properties) {
        MapLightingSettings defaults = MapLightingSettings.defaultSettings();
        return new MapLightingSettings(
                Boolean.parseBoolean(properties.getProperty("lighting.enabled", String.valueOf(defaults.lightingEnabled()))),
                MapLightingSettings.parseColor(properties.getProperty("lighting.ambientColor"), defaults.ambientColorRgb()),
                readDouble(properties, "lighting.ambientIntensity", defaults.ambientIntensity()),
                Boolean.parseBoolean(properties.getProperty("lighting.fogEnabled", String.valueOf(defaults.fogEnabled()))),
                MapLightingSettings.parseColor(properties.getProperty("lighting.fogColor"), defaults.fogColorRgb()),
                readDouble(properties, "lighting.fogDensity", defaults.fogDensity())
        );
    }

    private static List<MapLight> readLights(Properties properties) {
        int count = readInt(properties, "light.count", 0);
        List<MapLight> lights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "light." + i + ".";
            String id = properties.getProperty(prefix + "id", "");
            if (id.isBlank()) {
                continue;
            }
            lights.add(new MapLight(
                    id,
                    readInt(properties, prefix + "x", 0),
                    readInt(properties, prefix + "y", 0),
                    MapLightingSettings.parseColor(properties.getProperty(prefix + "color"), 0xFF8B42),
                    readDouble(properties, prefix + "radius", 5.0),
                    readDouble(properties, prefix + "intensity", 1.0),
                    readDouble(properties, prefix + "heightOffset", 0.65),
                    readDouble(properties, prefix + "offsetX", 0.0),
                    readDouble(properties, prefix + "offsetZ", 0.0),
                    readDouble(properties, prefix + "flicker", 0.0),
                    Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "true"))
            ));
        }
        return lights;
    }

    private static void writePlacedObject(Properties properties, String prefix, PlacedObjectInstance object) {
        if (object == null) {
            return;
        }
        properties.setProperty(prefix + "instanceId", object.instanceId());
        properties.setProperty(prefix + "kind", object.kind().name());
        properties.setProperty(prefix + "id", object.id());
        properties.setProperty(prefix + "x", String.valueOf(object.x()));
        properties.setProperty(prefix + "y", String.valueOf(object.y()));
        properties.setProperty(prefix + "offsetX", String.valueOf(object.offsetX()));
        properties.setProperty(prefix + "offsetY", String.valueOf(object.offsetY()));
        properties.setProperty(prefix + "offsetZ", String.valueOf(object.offsetZ()));
        properties.setProperty(prefix + "yawDegrees", String.valueOf(object.yawDegrees()));
        properties.setProperty(prefix + "pitchDegrees", String.valueOf(object.pitchDegrees()));
        properties.setProperty(prefix + "rollDegrees", String.valueOf(object.rollDegrees()));
        properties.setProperty(prefix + "scale", String.valueOf(object.scale()));
        properties.setProperty(prefix + "modelBrightness", String.valueOf(object.modelBrightness()));
        properties.setProperty(prefix + "blocksMovement", String.valueOf(object.blocksMovement()));
        writeLightAttachment(properties, prefix + "light.", object.lightOverride());
    }

    private static PlacedObjectInstance readPlacedObject(Properties properties, String prefix) {
        String id = properties.getProperty(prefix + "id", "").trim();
        if (id.isBlank()) {
            return null;
        }
        return new PlacedObjectInstance(
                properties.getProperty(prefix + "instanceId", id),
                readEnum(properties, prefix + "kind", PlacementKind.FURNITURE),
                id,
                readInt(properties, prefix + "x", 0),
                readInt(properties, prefix + "y", 0),
                readDouble(properties, prefix + "offsetX", 0.0),
                readDouble(properties, prefix + "offsetY", 0.0),
                readDouble(properties, prefix + "offsetZ", 0.0),
                readDouble(properties, prefix + "yawDegrees", 0.0),
                readDouble(properties, prefix + "pitchDegrees", 0.0),
                readDouble(properties, prefix + "rollDegrees", 0.0),
                readDouble(properties, prefix + "scale", 1.0),
                readDouble(properties, prefix + "modelBrightness", 1.0),
                Boolean.parseBoolean(properties.getProperty(prefix + "blocksMovement", "false")),
                readLightAttachment(properties, prefix + "light.")
        );
    }

    private static void writeLightAttachment(Properties properties, String prefix, LightAttachment light) {
        properties.setProperty(prefix + "present", String.valueOf(light != null));
        if (light == null) {
            return;
        }
        properties.setProperty(prefix + "enabled", String.valueOf(light.enabled()));
        properties.setProperty(prefix + "color", MapLightingSettings.colorHex(light.colorRgb()));
        properties.setProperty(prefix + "radius", String.valueOf(light.radius()));
        properties.setProperty(prefix + "intensity", String.valueOf(light.intensity()));
        properties.setProperty(prefix + "offsetX", String.valueOf(light.offsetX()));
        properties.setProperty(prefix + "offsetY", String.valueOf(light.offsetY()));
        properties.setProperty(prefix + "offsetZ", String.valueOf(light.offsetZ()));
        properties.setProperty(prefix + "flicker", String.valueOf(light.flickerAmount()));
    }

    private static LightAttachment readLightAttachment(Properties properties, String prefix) {
        boolean present = Boolean.parseBoolean(properties.getProperty(prefix + "present", "false"));
        if (!present) {
            return null;
        }
        return new LightAttachment(
                Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "true")),
                MapLightingSettings.parseColor(properties.getProperty(prefix + "color"), 0xFF8B42),
                readDouble(properties, prefix + "radius", 5.0),
                readDouble(properties, prefix + "intensity", 1.0),
                readDouble(properties, prefix + "offsetX", 0.0),
                readDouble(properties, prefix + "offsetY", 0.65),
                readDouble(properties, prefix + "offsetZ", 0.0),
                readDouble(properties, prefix + "flicker", 0.0)
        );
    }

    private static void writeCharacterModel(
            Properties properties,
            String prefix,
            CharacterModelDefinition definition
    ) {
        CharacterModelDefinition safe = definition == null
                ? CharacterModelDefinition.empty()
                : definition;
        properties.setProperty(prefix + "path", safe.modelPath());
        properties.setProperty(prefix + "rigId", safe.rigId());
        properties.setProperty(prefix + "scale", String.valueOf(safe.scale()));
        properties.setProperty(prefix + "facingRotationDegrees", String.valueOf(safe.facingRotationDegrees()));
        properties.setProperty(prefix + "verticalOffset", String.valueOf(safe.verticalOffset()));
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            CharacterModelDefinition.AnimationBinding binding = safe.animationBinding(slot);
            String animationPrefix = prefix + "animation." + slot.name();
            properties.setProperty(animationPrefix, binding.path());
            properties.setProperty(animationPrefix + ".clipName", binding.clipName());
            properties.setProperty(animationPrefix + ".speed", String.valueOf(binding.playbackSpeed()));
            properties.setProperty(animationPrefix + ".impactFraction", String.valueOf(binding.impactFraction()));
        }
    }

    private static CharacterModelDefinition readCharacterModel(Properties properties, String prefix) {
        EnumMap<CharacterModelDefinition.AnimationSlot, CharacterModelDefinition.AnimationBinding> animationBindings =
                new EnumMap<>(CharacterModelDefinition.AnimationSlot.class);
        for (CharacterModelDefinition.AnimationSlot slot : CharacterModelDefinition.AnimationSlot.values()) {
            String animationPrefix = prefix + "animation." + slot.name();
            String path = properties.getProperty(animationPrefix, "");
            if (!path.isBlank()) {
                animationBindings.put(slot, new CharacterModelDefinition.AnimationBinding(
                        path,
                        properties.getProperty(animationPrefix + ".clipName", ""),
                        readDouble(properties, animationPrefix + ".speed", 1.0),
                        readDouble(properties, animationPrefix + ".impactFraction",
                                CharacterModelDefinition.DEFAULT_IMPACT_FRACTION)
                ));
            }
        }
        return new CharacterModelDefinition(
                properties.getProperty(prefix + "path", ""),
                properties.getProperty(prefix + "rigId", ""),
                readDouble(properties, prefix + "scale", 1.0),
                readDouble(properties, prefix + "facingRotationDegrees", 0.0),
                readDouble(properties, prefix + "verticalOffset", 0.0),
                animationBindings
        );
    }

    private static MapPaintData readMapPaint(Properties properties, int width, int height) {
        String[][] floorBrushes = readPaintLayer(properties, "paint.floor.", width, height);
        String[][] wallBrushes = readPaintLayer(properties, "paint.wall.", width, height);
        String[][] doorBrushes = readPaintLayer(properties, "paint.door.", width, height);
        String[][] roofBrushes = readPaintLayer(properties, "paint.roof.", width, height);
        return MapPaintData.of(width, height, floorBrushes, wallBrushes, doorBrushes, roofBrushes);
    }

    private static void writeMapGeometry(Properties properties, MapGeometryData mapGeometry) {
        if (mapGeometry == null) {
            return;
        }

        int[][] rows = mapGeometry.copyHeightLevels();
        for (int y = 0; y < rows.length; y++) {
            properties.setProperty("geometry.height." + y, joinHeightRow(rows[y]));
        }
    }

    private static MapGeometryData readMapGeometry(Properties properties, int width, int height) {
        int[][] heightLevels = new int[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            String[] values = properties.getProperty("geometry.height." + y, "").split(",", -1);
            for (int x = 0; x < width; x++) {
                heightLevels[y][x] = MapGeometryData.clampHeightLevel(
                        readListInt(values, x, MapGeometryData.DEFAULT_HEIGHT_LEVEL)
                );
            }
        }
        return MapGeometryData.of(width, height, heightLevels);
    }

    private static void writeMobAreas(Properties properties, MobAreaData mobAreas) {
        if (mobAreas == null) {
            return;
        }
        String[][] rows = mobAreas.copyRows();
        for (int y = 0; y < rows.length; y++) {
            properties.setProperty("mobArea." + y, joinPaintRow(rows[y]));
        }
    }

    private static MobAreaData readMobAreas(Properties properties, int width, int height) {
        return MobAreaData.of(width, height, readPaintLayer(properties, "mobArea.", width, height));
    }

    private static String[][] readPaintLayer(Properties properties, String prefix, int width, int height) {
        String[][] layer = new String[Math.max(1, height)][Math.max(1, width)];
        for (int y = 0; y < height; y++) {
            String[] values = properties.getProperty(prefix + y, "").split(",", -1);
            for (int x = 0; x < width; x++) {
                layer[y][x] = x < values.length ? values[x].trim() : "";
            }
        }
        return layer;
    }

    private static String joinPaintRow(String[] row) {
        if (row == null || row.length == 0) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (String value : row) {
            values.add(value == null ? "" : value.trim());
        }
        return String.join(",", values);
    }

    private static String joinHeightRow(int[] row) {
        if (row == null || row.length == 0) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (int value : row) {
            values.add(String.valueOf(MapGeometryData.clampHeightLevel(value)));
        }
        return String.join(",", values);
    }

    private static Library.TileType readTile(String[] values, int index, Library.TileType fallback) {
        if (index < 0 || index >= values.length) {
            return fallback;
        }

        try {
            return Library.TileType.valueOf(values[index]);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static ThemeLibrary readTheme(Properties properties, String key, ThemeLibrary fallback) {
        try {
            return ThemeLibrary.valueOf(properties.getProperty(key, fallback.name()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static InventorySystem.ItemType readItemType(String value, InventorySystem.ItemType fallback) {
        try {
            return InventorySystem.ItemType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static GearMaterial readMaterial(String value, GearMaterial fallback) {
        try {
            return GearMaterial.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static WeaponType readWeaponType(String value, InventorySystem.ItemType itemType) {
        if (itemType != InventorySystem.ItemType.WEAPON) {
            return WeaponType.NONE;
        }
        try {
            WeaponType weaponType = WeaponType.valueOf(value);
            return weaponType == WeaponType.NONE ? WeaponType.SWORD : weaponType;
        } catch (IllegalArgumentException ignored) {
            return WeaponType.SWORD;
        }
    }

    private static GearDurability readDurability(String value, GearDurability fallback) {
        try {
            return GearDurability.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static LimbSlot readLimbSlot(String value, LimbSlot fallback) {
        try {
            return LimbSlot.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static List<String> readSkillIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> skills = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            // Preserve unresolved ids so the Construction Kit can diagnose and
            // repair references instead of silently dropping authored data.
            skills.add(BattleContentCatalog.normalizeId(trimmed));
        }

        return skills;
    }

    private static String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        return String.join(",", skills.stream().map(BattleContentCatalog::normalizeId).toList());
    }

    private static CharacterSkill readSkill(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return CharacterSkill.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PlayerStat readPlayerStat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return PlayerStat.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PlacementKind readPlacementKind(String value) {
        try {
            return PlacementKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static TriggerFireMode readTriggerFireMode(String value) {
        try {
            return TriggerFireMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TriggerFireMode.ON_ENTRY;
        }
    }

    private static TriggerActionType readTriggerActionType(String value) {
        try {
            return TriggerActionType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static GatheringNodeType readGatheringNodeType(String value) {
        try {
            return GatheringNodeType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return GatheringNodeType.MINING_ROCK;
        }
    }

    private static CharacterSkill readCharacterSkill(String value, CharacterSkill fallback) {
        try {
            return CharacterSkill.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return fallback == null ? CharacterSkill.MINING : fallback;
        }
    }

    private static CraftingRecipeCategory readCraftingRecipeCategory(String value) {
        try {
            return CraftingRecipeCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return CraftingRecipeCategory.MATERIAL;
        }
    }

    private static CraftingOutputType readCraftingOutputType(String value, CraftingOutputType fallback) {
        try {
            return CraftingOutputType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static CraftingStationType readCraftingStationType(String value) {
        try {
            return CraftingStationType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static CharacterSkill defaultGatheringSkill(GatheringNodeType nodeType) {
        if (nodeType == GatheringNodeType.FISHING_SPOT) {
            return CharacterSkill.FISHING;
        }
        if (nodeType == GatheringNodeType.TREE) {
            return CharacterSkill.WOODCUTTING;
        }
        return CharacterSkill.MINING;
    }

    private static List<CustomDropEntry> normalizeGatheringLoot(List<CustomDropEntry> lootEntries, String fallbackOutputItemId) {
        List<CustomDropEntry> normalized = new ArrayList<>();
        if (lootEntries != null) {
            for (CustomDropEntry entry : lootEntries) {
                if (entry != null && !entry.itemId().isBlank() && entry.chance() > 0.0) {
                    normalized.add(entry);
                }
            }
        }

        if (normalized.isEmpty() && fallbackOutputItemId != null && !fallbackOutputItemId.isBlank()) {
            normalized.add(new CustomDropEntry(fallbackOutputItemId, 1.0));
        }

        return List.copyOf(normalized);
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void validateCurrentContentSchema(Path path, Properties properties) throws IOException {
        if (!MapDesignContentStore.isContentCatalogPath(path)) {
            return;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String root = switch (fileName) {
            case MapDesignContentStore.DIALOGUE_FILE -> "dialogue";
            case MapDesignContentStore.QUEST_FILE -> "quest";
            case MapDesignContentStore.ITEM_FILE -> "item";
            case MapDesignContentStore.MOB_FILE -> "mob";
            case MapDesignContentStore.LIMB_FILE -> "limb";
            case MapDesignContentStore.NPC_FILE -> "npc";
            case MapDesignContentStore.FURNITURE_FILE -> "furniture";
            case MapDesignContentStore.GATHERING_NODE_FILE -> "gatheringNode";
            case MapDesignContentStore.COOKING_RECIPE_FILE -> "cookingRecipe";
            case MapDesignContentStore.CRAFTING_RECIPE_FILE -> "craftingRecipe";
            default -> "";
        };
        if (root.isBlank() || !properties.containsKey(root + ".count")) {
            throw new IOException("Content catalog " + fileName + " is not in the current format.");
        }
        int expectedVersion = switch (fileName) {
            case MapDesignContentStore.DIALOGUE_FILE, MapDesignContentStore.QUEST_FILE -> 3;
            case MapDesignContentStore.NPC_FILE -> 2;
            default -> 0;
        };
        if (expectedVersion > 0) {
            String key = root + ".schemaVersion";
            int actualVersion = readInt(properties, key, -1);
            if (actualVersion != expectedVersion) {
                throw new IOException("Unsupported " + fileName + " schema version "
                        + actualVersion + "; expected " + expectedVersion + ".");
            }
        }
    }

    private static void retainRequestedContentSegment(Properties properties, Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String requestedRoot = fileName.endsWith(".properties")
                ? fileName.substring(0, fileName.length() - ".properties".length())
                : fileName;
        String retainedRoot = switch (requestedRoot) {
            case "gathering_node" -> "gatheringNode";
            case "cooking_recipe" -> "cookingRecipe";
            case "crafting_recipe" -> "craftingRecipe";
            default -> requestedRoot;
        };
        properties.keySet().removeIf(rawKey ->
                !String.valueOf(rawKey).startsWith(retainedRoot + ".")
        );
    }

    private static double readDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T readEnum(Properties properties, String key, T fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("Fallback enum is required.");
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), properties.getProperty(key, fallback.name()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String sanitizeIdentifier(String value, String fallback) {
        String safeFallback = fallback == null || fallback.isBlank() ? "id" : fallback;
        if (value == null || value.isBlank()) {
            return safeFallback;
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double normalizeDegrees(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 0.0;
        }
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static int readListInt(String[] values, int index, int fallback) {
        if (index < 0 || index >= values.length) {
            return fallback;
        }

        try {
            return Integer.parseInt(values[index]);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Library.TileType[][] copyTiles(Library.TileType[][] source) {
        Library.TileType[][] copy = new Library.TileType[source.length][source[0].length];
        for (int y = 0; y < source.length; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, source[y].length);
        }
        return copy;
    }

    private static int[][] copyThemes(int[][] source) {
        int[][] copy = new int[source.length][source[0].length];
        for (int y = 0; y < source.length; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, source[y].length);
        }
        return copy;
    }

    public record MapDesign(
            int width,
            int height,
            String displayName,
            String description,
            String musicPath,
            String skyboxPath,
            ThemeLibrary primaryTheme,
            ThemeLibrary alternateTheme,
            Library.TileType[][] tiles,
            int[][] themeIndexes,
            MapPaintData mapPaint,
            MapGeometryData mapGeometry,
            MobAreaData mobAreas,
            List<MapPlacement> placements,
            List<PlacedObjectInstance> placedObjects,
            List<AuthoredDialogue> authoredDialogues,
            List<AuthoredQuest> authoredQuests,
            List<CustomItem> customItems,
            List<CustomMob> customMobs,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs,
            List<CustomFurnitureDefinition> customFurniture,
            List<CustomGatheringNode> customGatheringNodes,
            List<CustomCookingRecipe> customCookingRecipes,
            List<CraftingRecipe> craftingRecipes,
            List<MapTrigger> triggers,
            MapLightingSettings lightingSettings,
            List<MapLight> lights,
            int spawnX,
            int spawnY
    ) {
        public MapDesign {
            displayName = displayName == null || displayName.isBlank() ? "Untitled Map" : displayName;
            description = description == null ? "" : description;
            musicPath = musicPath == null ? "" : musicPath.trim();
            skyboxPath = SkyboxSpec.parseOrDefault(skyboxPath).encode();
            mapPaint = mapPaint == null ? MapPaintData.blank(width, height) : mapPaint;
            mapGeometry = mapGeometry == null ? MapGeometryData.blank(width, height) : mapGeometry;
            mobAreas = mobAreas == null ? MobAreaData.blank(width, height) : mobAreas;
            placements = placements == null ? new ArrayList<>() : placements;
            placedObjects = placedObjects == null ? new ArrayList<>() : placedObjects;
            authoredQuests = authoredQuests == null ? new ArrayList<>() : authoredQuests;
            customItems = customItems == null ? new ArrayList<>() : customItems;
            customMobs = customMobs == null ? new ArrayList<>() : customMobs;
            customLimbs = customLimbs == null ? new ArrayList<>() : customLimbs;
            customNpcs = customNpcs == null ? new ArrayList<>() : customNpcs;
            customFurniture = customFurniture == null ? new ArrayList<>() : customFurniture;
            customGatheringNodes = customGatheringNodes == null ? new ArrayList<>() : customGatheringNodes;
            customCookingRecipes = customCookingRecipes == null ? new ArrayList<>() : customCookingRecipes;
            craftingRecipes = craftingRecipes == null ? new ArrayList<>() : craftingRecipes;
            triggers = triggers == null ? new ArrayList<>() : triggers;
            lightingSettings = lightingSettings == null ? MapLightingSettings.defaultSettings() : lightingSettings;
            lights = lights == null ? new ArrayList<>() : new ArrayList<>(lights);
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                String musicPath,
                String skyboxPath,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                MapGeometryData mapGeometry,
                MobAreaData mobAreas,
                List<MapPlacement> placements,
                List<PlacedObjectInstance> placedObjects,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomFurnitureDefinition> customFurniture,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                int spawnX,
                int spawnY
        ) {
            this(width, height, displayName, description, musicPath, skyboxPath,
                    primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry,
                    mobAreas, placements, placedObjects, authoredDialogues, authoredQuests, customItems, customMobs,
                    customLimbs, customNpcs, customFurniture, customGatheringNodes, customCookingRecipes, craftingRecipes,
                    triggers, MapLightingSettings.defaultSettings(), List.of(), spawnX, spawnY);
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                String musicPath,
                String skyboxPath,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                MapGeometryData mapGeometry,
                MobAreaData mobAreas,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomFurnitureDefinition> customFurniture,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                MapLightingSettings lightingSettings,
                List<MapLight> lights,
                int spawnX,
                int spawnY
        ) {
            this(width, height, displayName, description, musicPath, skyboxPath,
                    primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry,
                    mobAreas, placements, new ArrayList<>(), authoredDialogues, authoredQuests,
                    customItems, customMobs, customLimbs, customNpcs, customFurniture,
                    customGatheringNodes, customCookingRecipes, craftingRecipes, triggers,
                    lightingSettings, lights, spawnX, spawnY);
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                String musicPath,
                String skyboxPath,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                MapGeometryData mapGeometry,
                MobAreaData mobAreas,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                MapLightingSettings lightingSettings,
                List<MapLight> lights,
                int spawnX,
                int spawnY
        ) {
            this(width, height, displayName, description, musicPath, skyboxPath,
                    primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry,
                    mobAreas, placements, new ArrayList<>(), authoredDialogues, authoredQuests,
                    customItems, customMobs, customLimbs, customNpcs, new ArrayList<>(),
                    customGatheringNodes, customCookingRecipes, craftingRecipes, triggers,
                    lightingSettings, lights, spawnX, spawnY);
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                String musicPath,
                String skyboxPath,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                MapGeometryData mapGeometry,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                int spawnX,
                int spawnY
        ) {
            this(width, height, displayName, description, musicPath, skyboxPath,
                    primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry,
                    MobAreaData.blank(width, height), placements, new ArrayList<>(), authoredDialogues, authoredQuests,
                    customItems, customMobs, customLimbs, customNpcs, new ArrayList<>(), customGatheringNodes,
                    customCookingRecipes, craftingRecipes, triggers, spawnX, spawnY);
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                String musicPath,
                String skyboxPath,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                int spawnX,
                int spawnY
        ) {
            this(
                    width,
                    height,
                    displayName,
                    description,
                    musicPath,
                    skyboxPath,
                    primaryTheme,
                    alternateTheme,
                    tiles,
                    themeIndexes,
                    mapPaint,
                    MapGeometryData.blank(width, height),
                    placements,
                    authoredDialogues,
                    authoredQuests,
                    customItems,
                    customMobs,
                    customLimbs,
                    customNpcs,
                    customGatheringNodes,
                    customCookingRecipes,
                    craftingRecipes,
                    triggers,
                    spawnX,
                    spawnY
            );
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                MapPaintData mapPaint,
                MapGeometryData mapGeometry,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                int spawnX,
                int spawnY
        ) {
            this(
                    width,
                    height,
                    displayName,
                    description,
                    "",
                    "",
                    primaryTheme,
                    alternateTheme,
                    tiles,
                    themeIndexes,
                    mapPaint,
                    mapGeometry,
                    placements,
                    authoredDialogues,
                    authoredQuests,
                    customItems,
                    customMobs,
                    customLimbs,
                    customNpcs,
                    customGatheringNodes,
                    customCookingRecipes,
                    craftingRecipes,
                    triggers,
                    spawnX,
                    spawnY
            );
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes,
                List<MapTrigger> triggers,
                int spawnX,
                int spawnY
        ) {
            this(
                    width,
                    height,
                    displayName,
                    description,
                    "",
                    "",
                    primaryTheme,
                    alternateTheme,
                    tiles,
                    themeIndexes,
                    MapPaintData.blank(width, height),
                    MapGeometryData.blank(width, height),
                    placements,
                    authoredDialogues,
                    authoredQuests,
                    customItems,
                    customMobs,
                    customLimbs,
                    customNpcs,
                    customGatheringNodes,
                    customCookingRecipes,
                    craftingRecipes,
                    triggers,
                    spawnX,
                    spawnY
            );
        }

        public MapDesign(
                int width,
                int height,
                String displayName,
                String description,
                ThemeLibrary primaryTheme,
                ThemeLibrary alternateTheme,
                Library.TileType[][] tiles,
                int[][] themeIndexes,
                List<MapPlacement> placements,
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                int spawnX,
                int spawnY
        ) {
            this(
                    width,
                    height,
                    displayName,
                    description,
                    primaryTheme,
                    alternateTheme,
                    tiles,
                    themeIndexes,
                    MapPaintData.blank(width, height),
                    MapGeometryData.blank(width, height),
                    placements,
                    authoredDialogues,
                    authoredQuests,
                    customItems,
                    customMobs,
                    customLimbs,
                    customNpcs,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    spawnX,
                    spawnY
            );
        }
    }

    public record MapPlacement(PlacementKind kind, String id, int x, int y) {
    }

    public record PlacedObjectInstance(
            String instanceId,
            PlacementKind kind,
            String id,
            int x,
            int y,
            double offsetX,
            double offsetY,
            double offsetZ,
            double yawDegrees,
            double pitchDegrees,
            double rollDegrees,
            double scale,
            double modelBrightness,
            boolean blocksMovement,
            LightAttachment lightOverride
    ) {
        public PlacedObjectInstance {
            instanceId = sanitizeIdentifier(instanceId, "placed_object");
            kind = kind == null ? PlacementKind.FURNITURE : kind;
            id = id == null ? "" : id.trim();
            offsetX = clampFinite(offsetX, -4.0, 4.0, 0.0);
            offsetY = clampFinite(offsetY, -8.0, 8.0, 0.0);
            offsetZ = clampFinite(offsetZ, -4.0, 4.0, 0.0);
            yawDegrees = normalizeDegrees(yawDegrees);
            pitchDegrees = normalizeDegrees(pitchDegrees);
            rollDegrees = normalizeDegrees(rollDegrees);
            scale = clampFinite(scale, 0.05, 20.0, 1.0);
            modelBrightness = clampFinite(modelBrightness, 0.0, 4.0, 1.0);
        }

        public PlacedObjectInstance(
                String instanceId,
                PlacementKind kind,
                String id,
                int x,
                int y,
                double offsetX,
                double offsetY,
                double offsetZ,
                double yawDegrees,
                double pitchDegrees,
                double rollDegrees,
                double scale,
                boolean blocksMovement,
                LightAttachment lightOverride
        ) {
            this(instanceId, kind, id, x, y, offsetX, offsetY, offsetZ, yawDegrees, pitchDegrees, rollDegrees, scale,
                    1.0, blocksMovement, lightOverride);
        }

        public static PlacedObjectInstance furniture(String instanceId, String furnitureId, int x, int y, boolean blocksMovement) {
            return new PlacedObjectInstance(
                    instanceId,
                    PlacementKind.FURNITURE,
                    furnitureId,
                    x,
                    y,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    1.0,
                    blocksMovement,
                    null
            );
        }
    }

    public record LightAttachment(
            boolean enabled,
            int colorRgb,
            double radius,
            double intensity,
            double offsetX,
            double offsetY,
            double offsetZ,
            double flickerAmount
    ) {
        public LightAttachment {
            colorRgb &= 0xFFFFFF;
            radius = clampFinite(radius, 0.1, 64.0, 5.0);
            intensity = clampFinite(intensity, 0.0, 8.0, 1.0);
            offsetX = clampFinite(offsetX, -4.0, 4.0, 0.0);
            offsetY = clampFinite(offsetY, -8.0, 8.0, 0.65);
            offsetZ = clampFinite(offsetZ, -4.0, 4.0, 0.0);
            flickerAmount = clampFinite(flickerAmount, 0.0, 1.0, 0.0);
        }

        public MapLight toMapLight(String lightId, PlacedObjectInstance instance) {
            if (!enabled || instance == null) {
                return null;
            }
            double yawRadians = Math.toRadians(instance.yawDegrees());
            double cos = Math.cos(yawRadians);
            double sin = Math.sin(yawRadians);
            double rotatedOffsetX = offsetX * cos - offsetZ * sin;
            double rotatedOffsetZ = offsetX * sin + offsetZ * cos;
            return new MapLight(
                    sanitizeIdentifier(lightId, "object_light"),
                    instance.x(),
                    instance.y(),
                    colorRgb,
                    radius,
                    intensity,
                    offsetY + instance.offsetY(),
                    rotatedOffsetX + instance.offsetX(),
                    rotatedOffsetZ + instance.offsetZ(),
                    flickerAmount,
                    true
            );
        }
    }

    public record CustomFurnitureDefinition(
            String furnitureId,
            String displayName,
            String category,
            String modelPath,
            double defaultScale,
            boolean defaultBlocksMovement,
            String interactionId,
            LightAttachment lightAttachment
    ) {
        public CustomFurnitureDefinition {
            furnitureId = sanitizeIdentifier(furnitureId, "furniture");
            displayName = displayName == null || displayName.isBlank() ? "Furniture" : displayName.trim();
            category = category == null ? "" : category.trim();
            modelPath = modelPath == null ? "" : modelPath.trim().replace('\\', '/');
            defaultScale = clampFinite(defaultScale, 0.05, 20.0, 1.0);
            interactionId = interactionId == null ? "" : interactionId.trim();
        }

        public MapEntity createEntity(PlacedObjectInstance instance) {
            if (instance == null || modelPath.isBlank()) {
                return null;
            }
            boolean blocks = instance.blocksMovement() || defaultBlocksMovement;
            MapEntity entity = new MapEntity(displayName, Library.EntityType.TRAP, instance.x(), instance.y())
                    .withStaticModel(modelPath)
                    .withVisualScale(defaultScale)
                    .withStaticModelTransform(
                            instance.offsetX(),
                            instance.offsetY(),
                            instance.offsetZ(),
                            instance.yawDegrees(),
                            instance.pitchDegrees(),
                            instance.rollDegrees(),
                            instance.scale()
                    )
                    .withStaticModelBrightness(instance.modelBrightness())
                    .blocksMovement(blocks);
            if (!interactionId.isBlank()) {
                entity.withInteractionId(interactionId);
            }
            return entity;
        }

        public MapLight createLight(PlacedObjectInstance instance) {
            LightAttachment attachment = instance != null && instance.lightOverride() != null
                    ? instance.lightOverride()
                    : lightAttachment;
            return attachment == null
                    ? null
                    : attachment.toMapLight("furniture_" + instance.instanceId(), instance);
        }
    }

    public record MapTrigger(
            String id,
            int x,
            int y,
            TriggerFireMode fireMode,
            boolean oneShot,
            String requiredQuestId,
            String requiredQuestProgress,
            List<TriggerAction> actions
    ) {
        public MapTrigger {
            id = id == null ? "" : id;
            fireMode = fireMode == null ? TriggerFireMode.ON_ENTRY : fireMode;
            requiredQuestId = requiredQuestId == null ? "" : requiredQuestId.trim();
            requiredQuestProgress = requiredQuestProgress == null ? "" : requiredQuestProgress.trim();
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        public MapTrigger(
                String id,
                int x,
                int y,
                TriggerFireMode fireMode,
                boolean oneShot,
                List<TriggerAction> actions
        ) {
            this(id, x, y, fireMode, oneShot, "", "", actions);
        }
    }

    public record TriggerAction(TriggerActionType type, int targetX, int targetY) {
        public TriggerAction {
            type = type == null ? TriggerActionType.CLOSE_DOOR : type;
        }
    }

    public record AuthoredContent(
            List<AuthoredDialogue> authoredDialogues,
            List<AuthoredQuest> authoredQuests,
            List<CustomItem> customItems,
            List<CustomMob> customMobs,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs,
            List<CustomFurnitureDefinition> customFurniture,
            List<CustomGatheringNode> customGatheringNodes,
            List<CustomCookingRecipe> customCookingRecipes,
            List<CraftingRecipe> craftingRecipes
    ) {
        public AuthoredContent(
                List<AuthoredDialogue> authoredDialogues,
                List<AuthoredQuest> authoredQuests,
                List<CustomItem> customItems,
                List<CustomMob> customMobs,
                List<CustomLimb> customLimbs,
                List<CustomNpc> customNpcs,
                List<CustomGatheringNode> customGatheringNodes,
                List<CustomCookingRecipe> customCookingRecipes,
                List<CraftingRecipe> craftingRecipes
        ) {
            this(
                    authoredDialogues,
                    authoredQuests,
                    customItems,
                    customMobs,
                    customLimbs,
                    customNpcs,
                    List.of(),
                    customGatheringNodes,
                    customCookingRecipes,
                    craftingRecipes
            );
        }

        public AuthoredContent {
            authoredDialogues = authoredDialogues == null ? List.of() : List.copyOf(authoredDialogues);
            authoredQuests = authoredQuests == null ? List.of() : List.copyOf(authoredQuests);
            customItems = customItems == null ? List.of() : List.copyOf(customItems);
            customMobs = customMobs == null ? List.of() : List.copyOf(customMobs);
            customLimbs = customLimbs == null ? List.of() : List.copyOf(customLimbs);
            customNpcs = customNpcs == null ? List.of() : List.copyOf(customNpcs);
            customFurniture = customFurniture == null ? List.of() : List.copyOf(customFurniture);
            customGatheringNodes = customGatheringNodes == null ? List.of() : List.copyOf(customGatheringNodes);
            customCookingRecipes = customCookingRecipes == null ? List.of() : List.copyOf(customCookingRecipes);
            craftingRecipes = craftingRecipes == null ? List.of() : List.copyOf(craftingRecipes);
        }
    }

    public record AuthoredDialogue(
            String interactionId,
            String speakerName,
            String bodyText,
            String followUpInteractionId,
            String visualPath,
            List<AuthoredDialogueChoice> choices,
            List<AuthoredDialogueNode> nodes,
            List<RewardDefinition> rewards,
            String firstTalkNodeId,
            String repeatTalkNodeId
    ) {
        public AuthoredDialogue {
            interactionId = interactionId == null ? "" : interactionId.trim();
            speakerName = speakerName == null ? "" : speakerName.trim();
            bodyText = bodyText == null ? "" : bodyText;
            followUpInteractionId = followUpInteractionId == null ? "" : followUpInteractionId;
            visualPath = visualPath == null || visualPath.isBlank() ? DEFAULT_NPC_VISUAL_PATH : visualPath;
            choices = choices == null ? List.of() : List.copyOf(choices);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
            firstTalkNodeId = firstTalkNodeId == null ? "" : firstTalkNodeId.trim();
            repeatTalkNodeId = repeatTalkNodeId == null ? "" : repeatTalkNodeId.trim();
        }
    }

    public record AuthoredDialogueChoice(
            String label,
            String bodyText,
            String targetNodeId,
            String requiredItemName,
            String takeItemName,
            int takeItemAmount,
            boolean firstTalkOnly,
            String choiceId,
            List<RewardDefinition> rewards
    ) {
        public AuthoredDialogueChoice {
            label = label == null || label.isBlank() ? "Continue" : label;
            bodyText = bodyText == null ? "" : bodyText;
            targetNodeId = targetNodeId == null ? "" : targetNodeId;
            requiredItemName = requiredItemName == null ? "" : requiredItemName;
            takeItemName = takeItemName == null ? "" : takeItemName;
            takeItemAmount = takeItemName.isBlank() ? 0 : Math.max(1, takeItemAmount);
            choiceId = choiceId == null ? "" : choiceId.trim();
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
        }
    }

    public record AuthoredDialogueNode(
            String nodeId,
            String bodyText,
            int canvasX,
            int canvasY,
            List<AuthoredDialogueChoice> choices
    ) {
        public AuthoredDialogueNode(String nodeId, String bodyText, List<AuthoredDialogueChoice> choices) {
            this(nodeId, bodyText, 80, 80, choices);
        }

        public AuthoredDialogueNode {
            nodeId = nodeId == null ? "" : nodeId.trim();
            bodyText = bodyText == null ? "" : bodyText;
            choices = choices == null ? List.of() : List.copyOf(choices);
        }
    }

    public enum QuestRequirementType {
        POSSESS_ITEM,
        EQUIPPED_ITEM_OR_LIMB,
        COMPLETED_QUEST,
        PLAYER_LEVEL,
        SKILL_LEVEL
    }

    public enum QuestObjectiveType {
        POSSESS_ITEM,
        TURN_IN_ITEM,
        EQUIPPED_ITEM_OR_LIMB,
        TALK_TO_NPC,
        DEFEAT_ENEMY,
        PLAYER_LEVEL,
        SKILL_LEVEL,
        COMPLETE_QUEST
    }

    public enum QuestRewardType {
        ITEM,
        GOLD,
        SKILL_XP
    }

    public enum QuestCompletionMode {
        AUTOMATIC,
        FLOW_CONFIRMED
    }

    public enum QuestFlowAction {
        NONE,
        ACCEPT_QUEST,
        ADVANCE_STAGE,
        COMPLETE_QUEST
    }

    public record QuestRequirement(
            QuestRequirementType type,
            String targetId,
            CharacterSkill skill,
            int amount
    ) {
        public QuestRequirement {
            type = type == null ? QuestRequirementType.POSSESS_ITEM : type;
            targetId = targetId == null ? "" : targetId.trim();
            amount = Math.max(1, amount);
        }
    }

    public record QuestObjective(
            String objectiveId,
            QuestObjectiveType type,
            String targetId,
            CharacterSkill skill,
            int amount,
            String journalText,
            boolean visible
    ) {
        public QuestObjective {
            objectiveId = objectiveId == null ? "" : objectiveId.trim();
            type = type == null ? QuestObjectiveType.POSSESS_ITEM : type;
            targetId = targetId == null ? "" : targetId.trim();
            amount = Math.max(1, amount);
            journalText = journalText == null ? "" : journalText.trim();
        }
    }

    public record RewardDefinition(
            String rewardId,
            QuestRewardType type,
            String itemId,
            CharacterSkill skill,
            int amount
    ) {
        public RewardDefinition(QuestRewardType type, String itemId, CharacterSkill skill, int amount) {
            this("", type, itemId, skill, amount);
        }

        public RewardDefinition {
            rewardId = rewardId == null ? "" : rewardId.trim();
            type = type == null ? QuestRewardType.ITEM : type;
            itemId = itemId == null ? "" : itemId.trim();
            amount = Math.max(1, amount);
        }
    }

    public record QuestFlowChoice(
            String choiceId,
            String label,
            String targetNodeId,
            List<QuestRequirement> conditions,
            QuestFlowAction action,
            String requiredItemId,
            String takeItemId,
            int takeItemAmount,
            List<RewardDefinition> rewards,
            boolean firstTalkOnly,
            String terminalBodyText
    ) {
        public QuestFlowChoice(
                String choiceId,
                String label,
                String targetNodeId,
                List<QuestRequirement> conditions,
                QuestFlowAction action,
                String requiredItemId,
                String takeItemId
        ) {
            this(choiceId, label, targetNodeId, conditions, action, requiredItemId, takeItemId,
                    takeItemId == null || takeItemId.isBlank() ? 0 : 1, List.of(), false, "");
        }

        public QuestFlowChoice(
                String choiceId,
                String label,
                String targetNodeId,
                List<QuestRequirement> conditions,
                QuestFlowAction action,
                String requiredItemId,
                String takeItemId,
                List<RewardDefinition> rewards,
                boolean firstTalkOnly
        ) {
            this(choiceId, label, targetNodeId, conditions, action, requiredItemId, takeItemId,
                    takeItemId == null || takeItemId.isBlank() ? 0 : 1, rewards, firstTalkOnly, "");
        }

        public QuestFlowChoice(
                String choiceId,
                String label,
                String targetNodeId,
                List<QuestRequirement> conditions,
                QuestFlowAction action,
                String requiredItemId,
                String takeItemId,
                List<RewardDefinition> rewards,
                boolean firstTalkOnly,
                String terminalBodyText
        ) {
            this(
                    choiceId,
                    label,
                    targetNodeId,
                    conditions,
                    action,
                    requiredItemId,
                    takeItemId,
                    takeItemId == null || takeItemId.isBlank() ? 0 : 1,
                    rewards,
                    firstTalkOnly,
                    terminalBodyText
            );
        }

        public QuestFlowChoice {
            choiceId = choiceId == null ? "" : choiceId.trim();
            label = label == null || label.isBlank() ? "Continue" : label.trim();
            targetNodeId = targetNodeId == null ? "" : targetNodeId.trim();
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            action = action == null ? QuestFlowAction.NONE : action;
            requiredItemId = requiredItemId == null ? "" : requiredItemId.trim();
            takeItemId = takeItemId == null ? "" : takeItemId.trim();
            takeItemAmount = takeItemId.isBlank() ? 0 : Math.max(1, takeItemAmount);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
            terminalBodyText = terminalBodyText == null ? "" : terminalBodyText;
        }
    }

    public record QuestFlowNode(
            String nodeId,
            String bodyText,
            int canvasX,
            int canvasY,
            List<QuestFlowChoice> choices
    ) {
        public QuestFlowNode {
            nodeId = nodeId == null ? "" : nodeId.trim();
            bodyText = bodyText == null ? "" : bodyText;
            choices = choices == null ? List.of() : List.copyOf(choices);
        }
    }

    public record QuestFlow(String entryNodeId, List<QuestFlowNode> nodes) {
        public QuestFlow {
            entryNodeId = entryNodeId == null ? "" : entryNodeId.trim();
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }

        public static QuestFlow empty() {
            return new QuestFlow("", List.of());
        }
    }

    public record QuestStage(
            String stageId,
            String title,
            String journalText,
            QuestCompletionMode completionMode,
            List<QuestObjective> objectives,
            List<RewardDefinition> rewards,
            QuestFlow flow
    ) {
        public QuestStage {
            stageId = stageId == null ? "" : stageId.trim();
            title = title == null || title.isBlank() ? "Quest Stage" : title.trim();
            journalText = journalText == null ? "" : journalText.trim();
            completionMode = completionMode == null ? QuestCompletionMode.FLOW_CONFIRMED : completionMode;
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
            flow = flow == null ? QuestFlow.empty() : flow;
        }
    }

    public record AuthoredQuest(
            String questId,
            String displayName,
            String summary,
            List<QuestRequirement> requirements,
            QuestFlow offerFlow,
            List<QuestStage> stages,
            List<RewardDefinition> finalRewards,
            QuestFlow epilogueFlow
    ) {
        public AuthoredQuest {
            questId = questId == null ? "" : questId;
            displayName = displayName == null || displayName.isBlank() ? "Untitled Quest" : displayName;
            summary = summary == null ? "" : summary.trim();
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            offerFlow = offerFlow == null ? QuestFlow.empty() : offerFlow;
            stages = stages == null ? List.of() : List.copyOf(stages);
            finalRewards = finalRewards == null ? List.of() : List.copyOf(finalRewards);
            epilogueFlow = epilogueFlow == null ? QuestFlow.empty() : epilogueFlow;
        }

        public List<String> journalEntries() {
            return stages.stream()
                    .map(stage -> stage.journalText().isBlank() ? stage.title() : stage.journalText())
                    .toList();
        }
    }

    public record CustomItem(
            String itemId,
            String displayName,
            InventorySystem.ItemType itemType,
            String iconPath,
            String paperDollOverlayPath,
            String useSoundPath,
            WeaponType weaponType,
            boolean twoHanded,
            GearMaterial material,
            int healAmount,
            int baseGoldValue,
            String examineText,
            PlayerStat statBonusTarget,
            boolean stackable,
            boolean smithingRecipeEnabled,
            int smithingRequiredBars,
            int smithingRequiredLevel,
            int smithingXpReward,
            int magicAccuracyBonus,
            int magicPowerBonus,
            String firstPersonModelPath,
            EquipmentViewModelProfile viewModelProfile
    ) {
        public CustomItem {
            itemId = itemId == null ? "" : itemId;
            displayName = displayName == null || displayName.isBlank() ? "Custom Item" : displayName;
            itemType = itemType == null ? InventorySystem.ItemType.MISC : itemType;
            iconPath = iconPath == null ? "" : iconPath;
            paperDollOverlayPath = paperDollOverlayPath == null ? "" : paperDollOverlayPath;
            useSoundPath = useSoundPath == null ? "" : useSoundPath;
            weaponType = itemType == InventorySystem.ItemType.WEAPON
                    ? (weaponType == null || weaponType == WeaponType.NONE ? WeaponType.SWORD : weaponType)
                    : WeaponType.NONE;
            twoHanded = itemType == InventorySystem.ItemType.WEAPON && twoHanded;
            material = material == null ? GearMaterial.NONE : material;
            healAmount = Math.max(0, healAmount);
            baseGoldValue = Math.max(1, baseGoldValue);
            examineText = examineText == null ? "" : examineText;
            stackable = stackable && (itemType == InventorySystem.ItemType.MISC || itemType == InventorySystem.ItemType.CONSUMABLE);
            smithingRecipeEnabled = smithingRecipeEnabled && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            smithingRequiredBars = Math.max(1, smithingRequiredBars);
            smithingRequiredLevel = Math.max(1, smithingRequiredLevel);
            smithingXpReward = Math.max(0, smithingXpReward);
            magicAccuracyBonus = itemType == InventorySystem.ItemType.WEAPON ? Math.max(0, magicAccuracyBonus) : 0;
            magicPowerBonus = itemType == InventorySystem.ItemType.WEAPON ? Math.max(0, magicPowerBonus) : 0;
            firstPersonModelPath = firstPersonModelPath == null
                    ? ""
                    : firstPersonModelPath.trim().replace('\\', '/');
            viewModelProfile = viewModelProfile == null ? EquipmentViewModelProfile.defaults() : viewModelProfile;
        }

        public InventorySystem.Item createItem() {
            return new InventorySystem.Item(
                    displayName,
                    itemType,
                    iconPath,
                    useSoundPath,
                    healAmount,
                    material,
                    GearDurability.PERFECT,
                    baseGoldValue,
                    examineText,
                    statBonusTarget,
                    stackable,
                    1,
                    paperDollOverlayPath,
                    weaponType,
                    twoHanded
            ).withMagicBonuses(magicAccuracyBonus, magicPowerBonus)
                    .withContentId(itemId)
                    .withFirstPersonModel(firstPersonModelPath)
                    .withViewModelProfile(viewModelProfile);
        }

        public CustomItem(
                String itemId, String displayName, InventorySystem.ItemType itemType, String iconPath,
                String paperDollOverlayPath, String useSoundPath, WeaponType weaponType, boolean twoHanded,
                GearMaterial material, int healAmount, int baseGoldValue, String examineText,
                PlayerStat statBonusTarget, boolean stackable, boolean smithingRecipeEnabled,
                int smithingRequiredBars, int smithingRequiredLevel, int smithingXpReward,
                int magicAccuracyBonus, int magicPowerBonus, String firstPersonModelPath
        ) {
            this(itemId, displayName, itemType, iconPath, paperDollOverlayPath, useSoundPath, weaponType,
                    twoHanded, material, healAmount, baseGoldValue, examineText, statBonusTarget, stackable,
                    smithingRecipeEnabled, smithingRequiredBars, smithingRequiredLevel, smithingXpReward,
                    magicAccuracyBonus, magicPowerBonus, firstPersonModelPath, EquipmentViewModelProfile.defaults());
        }

        public CustomItem(
                String itemId,
                String displayName,
                InventorySystem.ItemType itemType,
                String iconPath,
                String paperDollOverlayPath,
                String useSoundPath,
                WeaponType weaponType,
                boolean twoHanded,
                GearMaterial material,
                int healAmount,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                boolean smithingRecipeEnabled,
                int smithingRequiredBars,
                int smithingRequiredLevel,
                int smithingXpReward,
                int magicAccuracyBonus,
                int magicPowerBonus
        ) {
            this(itemId, displayName, itemType, iconPath, paperDollOverlayPath, useSoundPath,
                    weaponType, twoHanded, material, healAmount, baseGoldValue, examineText,
                    statBonusTarget, stackable, smithingRecipeEnabled, smithingRequiredBars,
                    smithingRequiredLevel, smithingXpReward, magicAccuracyBonus, magicPowerBonus, "");
        }

        public CustomItem(
                String itemId,
                String displayName,
                InventorySystem.ItemType itemType,
                String iconPath,
                String paperDollOverlayPath,
                String useSoundPath,
                WeaponType weaponType,
                boolean twoHanded,
                GearMaterial material,
                int healAmount,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                boolean smithingRecipeEnabled,
                int smithingRequiredBars,
                int smithingRequiredLevel,
                int smithingXpReward
        ) {
            this(itemId, displayName, itemType, iconPath, paperDollOverlayPath, useSoundPath,
                    weaponType, twoHanded, material, healAmount, baseGoldValue, examineText,
                    statBonusTarget, stackable, smithingRecipeEnabled, smithingRequiredBars,
                    smithingRequiredLevel, smithingXpReward, 0, 0);
        }

        public CustomItem(
                String itemId,
                String displayName,
                InventorySystem.ItemType itemType,
                String iconPath,
                String paperDollOverlayPath,
                String useSoundPath,
                WeaponType weaponType,
                GearMaterial material,
                int healAmount,
                int baseGoldValue,
                String examineText,
                PlayerStat statBonusTarget,
                boolean stackable,
                boolean smithingRecipeEnabled,
                int smithingRequiredBars,
                int smithingRequiredLevel,
                int smithingXpReward
        ) {
            this(
                    itemId,
                    displayName,
                    itemType,
                    iconPath,
                    paperDollOverlayPath,
                    useSoundPath,
                    weaponType,
                    false,
                    material,
                    healAmount,
                    baseGoldValue,
                    examineText,
                    statBonusTarget,
                    stackable,
                    smithingRecipeEnabled,
                    smithingRequiredBars,
                    smithingRequiredLevel,
                    smithingXpReward,
                    0,
                    0
            );
        }
    }

    public record CustomMob(
            String mobId,
            String displayName,
            String imagePath,
            String paperDollSourcePath,
            Map<PlayerStat, Integer> statValues,
            int xpReward,
            String description,
            String attackSoundPath,
            String damageSoundPath,
            int combatAiIntelligence,
            int awarenessRadius,
            int movementIntervalMs,
            int respawnDelayMs,
            List<String> skillIds,
            List<CustomDropEntry> dropEntries,
            CharacterModelDefinition characterModel
    ) {
        public CustomMob {
            mobId = mobId == null ? "" : mobId;
            displayName = displayName == null || displayName.isBlank() ? "Custom Enemy" : displayName;
            imagePath = imagePath == null ? "" : imagePath;
            paperDollSourcePath = paperDollSourcePath == null ? "" : paperDollSourcePath;
            EnumMap<PlayerStat, Integer> safeStats = new EnumMap<>(PlayerStat.class);
            for (PlayerStat stat : PlayerStat.values()) {
                int defaultValue = stat == PlayerStat.VITALITY ? 1 : 0;
                int value = Math.max(0, statValues == null ? defaultValue : statValues.getOrDefault(stat, defaultValue));
                safeStats.put(stat, stat == PlayerStat.VITALITY ? Math.max(1, value) : value);
            }
            statValues = Map.copyOf(safeStats);
            xpReward = Math.max(0, xpReward);
            description = description == null ? "" : description;
            attackSoundPath = attackSoundPath == null ? "" : attackSoundPath;
            damageSoundPath = damageSoundPath == null ? "" : damageSoundPath;
            combatAiIntelligence = Math.max(0, combatAiIntelligence);
            awarenessRadius = Math.max(0, awarenessRadius);
            movementIntervalMs = Math.max(250, movementIntervalMs);
            respawnDelayMs = Math.max(0, respawnDelayMs);
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
            dropEntries = dropEntries == null ? List.of() : List.copyOf(dropEntries);
            characterModel = characterModel == null ? CharacterModelDefinition.empty() : characterModel;
        }

        public CustomMob(
                String mobId,
                String displayName,
                String imagePath,
                String paperDollSourcePath,
                Map<PlayerStat, Integer> statValues,
                int xpReward,
                String description,
                String attackSoundPath,
                String damageSoundPath,
                int combatAiIntelligence,
                int awarenessRadius,
                int movementIntervalMs,
                int respawnDelayMs,
                List<String> skillIds,
                List<CustomDropEntry> dropEntries
        ) {
            this(mobId, displayName, imagePath, paperDollSourcePath, statValues, xpReward,
                    description, attackSoundPath, damageSoundPath, combatAiIntelligence,
                    awarenessRadius, movementIntervalMs, respawnDelayMs, skillIds, dropEntries,
                    CharacterModelDefinition.empty());
        }

        public CustomMob(
                String mobId,
                String displayName,
                String imagePath,
                String paperDollSourcePath,
                Map<PlayerStat, Integer> statValues,
                int xpReward,
                String description,
                String attackSoundPath,
                String damageSoundPath,
                int combatAiIntelligence,
                List<String> skillIds,
                List<CustomDropEntry> dropEntries
        ) {
            this(mobId, displayName, imagePath, paperDollSourcePath, statValues, xpReward,
                    description, attackSoundPath, damageSoundPath, combatAiIntelligence,
                    4, 3000, 300000, skillIds, dropEntries, CharacterModelDefinition.empty());
        }

        public Monster createMonster() {
            return new Monster(
                    mobId,
                    displayName,
                    statValues,
                    xpReward,
                    description,
                    imagePath,
                    paperDollSourcePath,
                    attackSoundPath,
                    damageSoundPath,
                    combatAiIntelligence,
                    skillIds,
                    dropEntries.stream()
                            .map(drop -> new Monster.DropEntry(drop.itemId(), drop.chance()))
                            .toList(),
                    characterModel
            );
        }
    }

    public record CustomDropEntry(String itemId, double chance) {
        public CustomDropEntry {
            itemId = itemId == null ? "" : itemId;
            chance = Math.max(0.0, Math.min(1.0, chance));
        }

        @Override
        public String toString() {
            return itemId + " [" + formatDropChance(chance) + "%]";
        }

        private static String formatDropChance(double chance) {
            String formatted = String.format(Locale.US, "%.3f", chance * 100.0);
            return formatted
                    .replaceFirst("\\.?0+$", "");
        }
    }

    public record CraftingRecipe(
            String recipeId,
            String displayName,
            CraftingRecipeCategory category,
            String primaryItemId,
            String secondaryItemId,
            String outputItemId,
            CharacterSkill requiredSkill,
            int requiredLevel,
            int xpReward,
            boolean consumePrimary,
            boolean consumeSecondary,
            String smeltOutputItemId,
            int smeltRequiredLevel,
            int smeltXpReward,
            int primaryQuantity,
            int secondaryQuantity,
            CraftingOutputType outputType,
            CraftingStationType outputStationType,
            int stationLifetimeMs
    ) {
        public CraftingRecipe {
            recipeId = recipeId == null ? "" : recipeId;
            displayName = displayName == null || displayName.isBlank() ? "Crafting Recipe" : displayName;
            category = category == null ? CraftingRecipeCategory.MATERIAL : category;
            primaryItemId = primaryItemId == null ? "" : primaryItemId;
            secondaryItemId = secondaryItemId == null ? "" : secondaryItemId;
            outputItemId = outputItemId == null ? "" : outputItemId;
            requiredSkill = requiredSkill == null ? CharacterSkill.CRAFTING : requiredSkill;
            requiredLevel = Math.max(1, requiredLevel);
            xpReward = Math.max(0, xpReward);
            smeltOutputItemId = smeltOutputItemId == null ? "" : smeltOutputItemId;
            smeltRequiredLevel = Math.max(1, smeltRequiredLevel);
            smeltXpReward = Math.max(0, smeltXpReward);
            primaryQuantity = Math.max(1, primaryQuantity);
            secondaryQuantity = secondaryItemId.isBlank() ? 0 : Math.max(1, secondaryQuantity);
            outputType = outputType == null ? CraftingOutputType.ITEM : outputType;
            outputStationType = outputType == CraftingOutputType.CRAFTING_STATION
                    ? outputStationType
                    : null;
            stationLifetimeMs = outputType == CraftingOutputType.CRAFTING_STATION
                    ? Math.max(1, stationLifetimeMs)
                    : 0;
        }

        public CraftingRecipe(
                String recipeId,
                String displayName,
                CraftingRecipeCategory category,
                String primaryItemId,
                String secondaryItemId,
                String outputItemId,
                CharacterSkill requiredSkill,
                int requiredLevel,
                int xpReward,
                boolean consumePrimary,
                boolean consumeSecondary,
                String smeltOutputItemId,
                int smeltRequiredLevel,
                int smeltXpReward
        ) {
            this(recipeId, displayName, category, primaryItemId, secondaryItemId, outputItemId,
                    requiredSkill, requiredLevel, xpReward, consumePrimary, consumeSecondary,
                    smeltOutputItemId, smeltRequiredLevel, smeltXpReward,
                    1, secondaryItemId == null || secondaryItemId.isBlank() ? 0 : 1,
                    CraftingOutputType.ITEM, null, 0);
        }

        public CraftingRecipe(
                String recipeId,
                String displayName,
                CraftingRecipeCategory category,
                String primaryItemId,
                String secondaryItemId,
                String outputItemId,
                CharacterSkill requiredSkill,
                int requiredLevel,
                int xpReward,
                boolean consumePrimary,
                boolean consumeSecondary
        ) {
            this(
                    recipeId,
                    displayName,
                    category,
                    primaryItemId,
                    secondaryItemId,
                    outputItemId,
                    requiredSkill,
                    requiredLevel,
                    xpReward,
                    consumePrimary,
                    consumeSecondary,
                    "",
                    1,
                    0,
                    1,
                    secondaryItemId == null || secondaryItemId.isBlank() ? 0 : 1,
                    CraftingOutputType.ITEM,
                    null,
                    0
            );
        }

        public boolean isSingleIngredient() {
            return secondaryItemId.isBlank();
        }

        public boolean outputsStation() {
            return outputType == CraftingOutputType.CRAFTING_STATION;
        }

        public boolean matches(String firstItemIdOrName, String secondItemIdOrName) {
            return matchesOrdered(firstItemIdOrName, secondItemIdOrName)
                    || matchesOrdered(secondItemIdOrName, firstItemIdOrName);
        }

        private boolean matchesOrdered(String firstItemIdOrName, String secondItemIdOrName) {
            return itemMatches(primaryItemId, firstItemIdOrName)
                    && itemMatches(secondaryItemId, secondItemIdOrName);
        }

        private boolean itemMatches(String configuredId, String itemIdOrName) {
            return configuredId != null
                    && itemIdOrName != null
                    && !configuredId.isBlank()
                    && configuredId.equalsIgnoreCase(itemIdOrName);
        }
    }

    public record CustomCookingRecipe(
            String recipeId,
            String displayName,
            String rawItemId,
            String cookedItemId,
            String burntItemId,
            int requiredLevel,
            int xpReward
    ) {
        public CustomCookingRecipe {
            recipeId = recipeId == null ? "" : recipeId;
            displayName = displayName == null || displayName.isBlank() ? "Cooking Recipe" : displayName;
            rawItemId = rawItemId == null ? "" : rawItemId;
            cookedItemId = cookedItemId == null ? "" : cookedItemId;
            burntItemId = burntItemId == null ? "" : burntItemId;
            requiredLevel = Math.max(1, requiredLevel);
            xpReward = Math.max(0, xpReward);
        }

        public boolean matches(String itemIdOrName, List<CustomItem> customItems) {
            if (itemIdOrName == null || itemIdOrName.isBlank()) {
                return false;
            }
            if (rawItemId.equalsIgnoreCase(itemIdOrName)) {
                return true;
            }
            String rawName = itemDisplayName(rawItemId, customItems);
            return !rawName.isBlank() && rawName.equalsIgnoreCase(itemIdOrName);
        }
    }

    public record CustomLimb(
            String limbId,
            String displayName,
            LimbSlot limbSlot,
            String iconPath,
            GearDurability condition,
            String description,
            String sourceCreatureId,
            String paperDollSourcePath,
            Map<PlayerStat, Integer> statBonuses,
            List<String> skillIds,
            String firstPersonModelPath,
            String firstPersonRigId
    ) {
        public CustomLimb {
            limbId = limbId == null ? "" : limbId;
            displayName = displayName == null || displayName.isBlank() ? "Custom Limb" : displayName;
            limbSlot = limbSlot == null ? LimbSlot.HEAD : limbSlot;
            iconPath = iconPath == null ? "" : iconPath;
            condition = condition == null ? GearDurability.PERFECT : condition;
            description = description == null ? "" : description;
            sourceCreatureId = sourceCreatureId == null ? "" : sourceCreatureId;
            paperDollSourcePath = paperDollSourcePath == null ? "" : paperDollSourcePath;
            firstPersonModelPath = firstPersonModelPath == null
                    ? "" : firstPersonModelPath.trim().replace('\\', '/');
            firstPersonRigId = firstPersonRigId == null ? "" : firstPersonRigId.trim();
            EnumMap<PlayerStat, Integer> safeStats = new EnumMap<>(PlayerStat.class);
            if (statBonuses != null) {
                for (PlayerStat stat : PlayerStat.values()) {
                    safeStats.put(stat, Math.max(0, statBonuses.getOrDefault(stat, 0)));
                }
            }
            statBonuses = safeStats;
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        }

        public CustomLimb(
                String limbId,
                String displayName,
                LimbSlot limbSlot,
                String iconPath,
                GearDurability condition,
                String description,
                String sourceCreatureId,
                String paperDollSourcePath,
                Map<PlayerStat, Integer> statBonuses,
                List<String> skillIds
        ) {
            this(limbId, displayName, limbSlot, iconPath, condition, description,
                    sourceCreatureId, paperDollSourcePath, statBonuses, skillIds, "", "");
        }

        public LimbItem createLimb() {
            LimbItem limb = new LimbItem(
                    displayName,
                    sourceCreatureId,
                    sourceCreatureId,
                    limbSlot,
                    statBonuses,
                    BattleContentCatalog.createSkills(skillIds),
                    condition,
                    iconPath,
                    description,
                    paperDollSourcePath
            ).withFirstPersonModel(firstPersonModelPath, firstPersonRigId);
            limb.withContentId(limbId);
            return limb;
        }
    }

    public record CustomNpc(
            String npcId,
            String displayName,
            String imagePath,
            String talkSoundPath,
            String interactionId,
            CustomShop shop,
            CharacterModelDefinition characterModel,
            List<String> questIds
    ) {
        public CustomNpc(
                String npcId,
                String displayName,
                String imagePath,
                String talkSoundPath,
                String interactionId
        ) {
            this(npcId, displayName, imagePath, talkSoundPath, interactionId, null,
                    CharacterModelDefinition.empty(), List.of());
        }

        public CustomNpc(
                String npcId,
                String displayName,
                String imagePath,
                String talkSoundPath,
                String interactionId,
                CustomShop shop
        ) {
            this(npcId, displayName, imagePath, talkSoundPath, interactionId, shop,
                    CharacterModelDefinition.empty(), List.of());
        }

        public CustomNpc(
                String npcId,
                String displayName,
                String imagePath,
                String talkSoundPath,
                String interactionId,
                CustomShop shop,
                CharacterModelDefinition characterModel
        ) {
            this(npcId, displayName, imagePath, talkSoundPath, interactionId, shop, characterModel, List.of());
        }

        public CustomNpc {
            npcId = npcId == null ? "" : npcId;
            displayName = displayName == null || displayName.isBlank() ? "Custom NPC" : displayName;
            imagePath = imagePath == null ? "" : imagePath;
            talkSoundPath = talkSoundPath == null ? "" : talkSoundPath;
            interactionId = interactionId == null ? "" : interactionId;
            characterModel = characterModel == null ? CharacterModelDefinition.empty() : characterModel;
            questIds = questIds == null
                    ? List.of()
                    : questIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        }

        public MapEntity createEntity(int x, int y) {
            MapEntity entity = new MapEntity(
                    displayName,
                    Library.EntityType.NPC,
                    x,
                    y,
                    imagePath.isBlank() ? null : AssetLoader.loadImage(imagePath)
            ).withTalkSoundPath(talkSoundPath);
            if (characterModel.hasModel()) {
                entity.withCharacterModel(characterModel);
            }
            String runtimeInteractionId = interactionId;
            if (runtimeInteractionId.isBlank() && (shop != null || !questIds.isEmpty())) {
                runtimeInteractionId = "npc_hub";
            }
            entity.withContentId(npcId)
                    .withInteractionId(runtimeInteractionId)
                    .withQuestIds(questIds);
            if (shop != null) {
                entity.withShopBlueprint(shop.toBlueprint());
            }
            return entity;
        }
    }

    public record CustomShop(
            String shopName,
            String greeting,
            List<CustomShopStock> stock
    ) {
        public CustomShop {
            shopName = shopName == null || shopName.isBlank() ? "Shop" : shopName.trim();
            greeting = greeting == null || greeting.isBlank()
                    ? "Take a look at my wares."
                    : greeting.trim();
            stock = stock == null ? List.of() : List.copyOf(stock);
        }

        public ShopSystem.ShopBlueprint toBlueprint() {
            return new ShopSystem.ShopBlueprint(
                    shopName,
                    greeting,
                    stock.stream()
                            .map(entry -> new ShopSystem.ShopStockDefinition(
                                    entry.itemId(),
                                    entry.quantity(),
                                    entry.buyPrice(),
                                    entry.sellPrice()
                            ))
                            .toList()
            );
        }
    }

    public record CustomShopStock(
            String itemId,
            int quantity,
            int buyPrice,
            int sellPrice
    ) {
        public CustomShopStock {
            itemId = itemId == null ? "" : itemId.trim();
        }
    }

    public record CustomGatheringNode(
            String nodeId,
            String displayName,
            GatheringNodeType nodeType,
            int requiredLevel,
            String outputItemId,
            int gatherXpReward,
            String smeltOutputItemId,
            int smeltXpReward,
            List<String> framePaths,
            List<String> modelPaths,
            int frameDurationMs,
            double visualScale,
            CharacterSkill gatheringSkill,
            List<CustomDropEntry> lootEntries,
            int smeltRequiredLevel,
            LightAttachment lightAttachment
    ) {
        public CustomGatheringNode {
            nodeId = nodeId == null ? "" : nodeId;
            displayName = displayName == null || displayName.isBlank() ? "Resource Node" : displayName;
            nodeType = nodeType == null ? GatheringNodeType.MINING_ROCK : nodeType;
            gatheringSkill = gatheringSkill == null ? defaultGatheringSkill(nodeType) : gatheringSkill;
            requiredLevel = Math.max(1, requiredLevel);
            outputItemId = outputItemId == null ? "" : outputItemId;
            gatherXpReward = Math.max(0, gatherXpReward);
            smeltOutputItemId = smeltOutputItemId == null ? "" : smeltOutputItemId;
            smeltRequiredLevel = Math.max(1, smeltRequiredLevel);
            smeltXpReward = Math.max(0, smeltXpReward);
            lootEntries = normalizeGatheringLoot(lootEntries, outputItemId);
            if (outputItemId.isBlank() && !lootEntries.isEmpty()) {
                outputItemId = lootEntries.get(0).itemId();
            }
            framePaths = framePaths == null ? List.of() : framePaths.stream()
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> path.replace('\\', '/'))
                    .toList();
            modelPaths = modelPaths == null ? List.of() : modelPaths.stream()
                    .filter(path -> path != null && !path.isBlank())
                    .map(path -> path.replace('\\', '/'))
                    .toList();
            frameDurationMs = Math.max(1, frameDurationMs);
            visualScale = Math.max(0.1, visualScale);
        }

        public CustomGatheringNode(
                String nodeId,
                String displayName,
                GatheringNodeType nodeType,
                int requiredLevel,
                String outputItemId,
                int gatherXpReward,
                String smeltOutputItemId,
                int smeltXpReward,
                List<String> framePaths,
                int frameDurationMs,
                double visualScale,
                CharacterSkill gatheringSkill,
                List<CustomDropEntry> lootEntries,
                int smeltRequiredLevel
        ) {
            this(
                    nodeId,
                    displayName,
                    nodeType,
                    requiredLevel,
                    outputItemId,
                    gatherXpReward,
                    smeltOutputItemId,
                    smeltXpReward,
                    framePaths,
                    List.of(),
                    frameDurationMs,
                    visualScale,
                    gatheringSkill,
                    lootEntries,
                    smeltRequiredLevel,
                    null
            );
        }

        public CustomGatheringNode(
                String nodeId,
                String displayName,
                GatheringNodeType nodeType,
                int requiredLevel,
                String outputItemId,
                int gatherXpReward,
                String smeltOutputItemId,
                int smeltXpReward,
                List<String> framePaths,
                int frameDurationMs,
                double visualScale
        ) {
            this(
                    nodeId,
                    displayName,
                    nodeType,
                    requiredLevel,
                    outputItemId,
                    gatherXpReward,
                    smeltOutputItemId,
                    smeltXpReward,
                    framePaths,
                    List.of(),
                    frameDurationMs,
                    visualScale,
                    defaultGatheringSkill(nodeType),
                    outputItemId == null || outputItemId.isBlank()
                            ? List.of()
                            : List.of(new CustomDropEntry(outputItemId, 1.0)),
                    1,
                    null
            );
        }

        public String interactionId() {
            return switch (nodeType) {
                case FISHING_SPOT -> "custom_fishing_" + nodeId;
                case TREE -> "custom_woodcutting_" + nodeId;
                default -> "custom_mining_" + nodeId;
            };
        }

        public MapEntity createEntity(int x, int y) {
            MapEntity entity;
            if (framePaths.size() > 1 && nodeType == GatheringNodeType.FISHING_SPOT) {
                entity = new MapEntity(
                        displayName,
                        Library.EntityType.TRAP,
                        x,
                        y,
                        new SpriteAnimation(loadFrames(), frameDurationMs)
                );
            } else {
                entity = new MapEntity(
                        displayName,
                        Library.EntityType.TRAP,
                        x,
                        y,
                        getImageForExhaustion(0)
                );
            }

            entity.withInteractionId(interactionId()).withVisualScale(visualScale);
            String defaultModelPath = getModelForExhaustion(0);
            if (!defaultModelPath.isBlank()) {
                entity.withStaticModel(defaultModelPath);
            }
            if (nodeType == GatheringNodeType.MINING_ROCK || nodeType == GatheringNodeType.TREE) {
                entity.blocksMovement(true);
            }
            return entity;
        }

        public MapLight createLight(PlacedObjectInstance instance) {
            LightAttachment attachment = instance != null && instance.lightOverride() != null
                    ? instance.lightOverride()
                    : lightAttachment;
            return attachment == null
                    ? null
                    : attachment.toMapLight("gathering_" + instance.instanceId(), instance);
        }

        public BufferedImage getImageForExhaustion(int exhaustionLevel) {
            if (framePaths.isEmpty()) {
                return null;
            }
            int safeIndex = nodeType == GatheringNodeType.TREE
                    ? (exhaustionLevel >= 2 ? framePaths.size() - 1 : 0)
                    : Math.max(0, Math.min(framePaths.size() - 1, exhaustionLevel));
            return AssetLoader.loadImage(framePaths.get(safeIndex));
        }

        public String getModelForExhaustion(int exhaustionLevel) {
            if (modelPaths.isEmpty()) {
                return "";
            }
            int safeIndex = nodeType == GatheringNodeType.TREE
                    ? (exhaustionLevel >= 2 ? modelPaths.size() - 1 : 0)
                    : Math.max(0, Math.min(modelPaths.size() - 1, exhaustionLevel));
            return modelPaths.get(safeIndex);
        }

        private BufferedImage[] loadFrames() {
            BufferedImage[] frames = new BufferedImage[framePaths.size()];
            for (int i = 0; i < framePaths.size(); i++) {
                frames[i] = AssetLoader.loadImage(framePaths.get(i));
            }
            return frames;
        }
    }

    private record GridPoint(int x, int y) {
    }

    public record ValidationIssue(ValidationSeverity severity, String message) {
        @Override
        public String toString() {
            return severity + ": " + message;
        }
    }

    public enum ValidationSeverity {
        ERROR,
        WARNING
    }

    public enum PlacementKind {
        CRAFTING_NODE,
        GATHERING_NODE,
        FURNITURE,
        CUSTOM_NPC,
        ITEM,
        ENEMY,
        INTERACTION
    }

    public enum GatheringNodeType {
        MINING_ROCK,
        FISHING_SPOT,
        TREE,
        FORAGING
    }

    public enum CraftingRecipeCategory {
        METAL,
        CONSUMABLE,
        MATERIAL,
        ARMOR,
        WEAPON,
        STATION
    }

    public enum CraftingOutputType {
        ITEM,
        CRAFTING_STATION
    }

    public enum TriggerFireMode {
        ON_ENTRY,
        ON_QUEST_PROGRESS
    }

    public enum TriggerActionType {
        CLOSE_DOOR,
        OPEN_DOOR
    }
}

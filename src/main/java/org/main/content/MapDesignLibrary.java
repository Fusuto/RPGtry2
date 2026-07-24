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
    public static final Path LEGACY_SHARED_CONTENT_PATH = CONTENT_FOLDER.resolve("authored_content.properties");
    public static final Path DATA_MAP_FOLDER = Path.of("data", "maps");
    public static final Path DATA_CONTENT_FOLDER = Path.of("data", "content");
    public static final Path DATA_LEGACY_SHARED_CONTENT_PATH = DATA_CONTENT_FOLDER.resolve("authored_content.properties");
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
            properties.setProperty(prefix + "requiredQuestStage", String.valueOf(trigger.requiredQuestStage()));
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
        properties.setProperty("dialogue.count", String.valueOf(design.authoredDialogues().size()));
        for (int i = 0; i < design.authoredDialogues().size(); i++) {
            AuthoredDialogue authoredDialogue = design.authoredDialogues().get(i);
            String prefix = "dialogue." + i + ".";
            properties.setProperty(prefix + "interactionId", authoredDialogue.interactionId());
            properties.setProperty(prefix + "speakerName", authoredDialogue.speakerName());
            properties.setProperty(prefix + "bodyText", authoredDialogue.bodyText());
            properties.setProperty(prefix + "followUpInteractionId", authoredDialogue.followUpInteractionId());
            properties.setProperty(prefix + "rewardItemId", authoredDialogue.rewardItemId());
            properties.setProperty(prefix + "rewardSkill", authoredDialogue.rewardSkill() == null ? "" : authoredDialogue.rewardSkill().name());
            properties.setProperty(prefix + "rewardSkillXp", String.valueOf(authoredDialogue.rewardSkillXp()));
            properties.setProperty(prefix + "rewardGold", String.valueOf(authoredDialogue.rewardGold()));
            properties.setProperty(prefix + "questId", authoredDialogue.questId());
            properties.setProperty(prefix + "questStage", String.valueOf(authoredDialogue.questStage()));
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
                properties.setProperty(nodePrefix + "choice.count", String.valueOf(node.choices().size()));
                for (int choiceIndex = 0; choiceIndex < node.choices().size(); choiceIndex++) {
                    writeAuthoredDialogueChoice(properties, nodePrefix + "choice." + choiceIndex + ".", node.choices().get(choiceIndex));
                }
            }
        }

        properties.setProperty("quest.count", String.valueOf(design.authoredQuests().size()));
        for (int i = 0; i < design.authoredQuests().size(); i++) {
            AuthoredQuest authoredQuest = design.authoredQuests().get(i);
            String prefix = "quest." + i + ".";
            properties.setProperty(prefix + "questId", authoredQuest.questId());
            properties.setProperty(prefix + "displayName", authoredQuest.displayName());
            properties.setProperty(prefix + "stage.count", String.valueOf(authoredQuest.stageDescriptions().size()));
            for (int stage = 0; stage < authoredQuest.stageDescriptions().size(); stage++) {
                properties.setProperty(prefix + "stage." + stage, authoredQuest.stageDescriptions().get(stage));
            }
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

        properties.setProperty("npc.count", String.valueOf(design.customNpcs().size()));
        for (int i = 0; i < design.customNpcs().size(); i++) {
            CustomNpc customNpc = design.customNpcs().get(i);
            String prefix = "npc." + i + ".";
            properties.setProperty(prefix + "npcId", customNpc.npcId());
            properties.setProperty(prefix + "displayName", customNpc.displayName());
            properties.setProperty(prefix + "imagePath", customNpc.imagePath());
            properties.setProperty(prefix + "talkSoundPath", customNpc.talkSoundPath());
            properties.setProperty(prefix + "interactionId", customNpc.interactionId());
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
            int requiredQuestStage = readInt(properties, prefix + "requiredQuestStage", 0);
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
                        requiredQuestStage,
                        actions
                ));
            }
        }

        String dialogueRoot = contentRoot(properties, "dialogue", "authoredDialogue");
        int authoredDialogueCount = readInt(properties, dialogueRoot + ".count", 0);
        List<AuthoredDialogue> authoredDialogues = new ArrayList<>();
        for (int i = 0; i < authoredDialogueCount; i++) {
            String prefix = dialogueRoot + "." + i + ".";
            String interactionId = properties.getProperty(prefix + "interactionId", "");
            String speakerName = properties.getProperty(prefix + "speakerName", "");
            String bodyText = properties.getProperty(prefix + "bodyText", "");
            String followUpInteractionId = properties.getProperty(prefix + "followUpInteractionId", "");
            String visualPath = properties.getProperty(prefix + "visualPath", legacyVisualPath(properties.getProperty(prefix + "visualType", "")));
            String rewardItemId = properties.getProperty(prefix + "rewardItemId", "");
            CharacterSkill rewardSkill = readSkill(properties.getProperty(prefix + "rewardSkill", ""));
            int rewardSkillXp = readInt(properties, prefix + "rewardSkillXp", 0);
            int rewardGold = readInt(properties, prefix + "rewardGold", 0);
            String questId = properties.getProperty(prefix + "questId", "");
            int questStage = readInt(properties, prefix + "questStage", -1);
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
                    nodes.add(new AuthoredDialogueNode(nodeId, nodeBodyText, nodeChoices));
                }
            }

            if (!interactionId.isBlank() && !speakerName.isBlank() && !bodyText.isBlank()) {
                authoredDialogues.add(new AuthoredDialogue(
                        interactionId,
                        speakerName,
                        bodyText,
                        followUpInteractionId,
                        visualPath,
                        rewardItemId,
                        rewardSkill,
                        rewardSkillXp,
                        rewardGold,
                        questId,
                        questStage,
                        choices,
                        nodes
                ));
            }
        }

        String questRoot = contentRoot(properties, "quest", "authoredQuest");
        int authoredQuestCount = readInt(properties, questRoot + ".count", 0);
        List<AuthoredQuest> authoredQuests = new ArrayList<>();
        for (int i = 0; i < authoredQuestCount; i++) {
            String prefix = questRoot + "." + i + ".";
            String questId = properties.getProperty(prefix + "questId", "");
            String questName = properties.getProperty(prefix + "displayName", "");
            int stageCount = readInt(properties, prefix + "stage.count", 0);
            List<String> stageDescriptions = new ArrayList<>();
            for (int stage = 0; stage < stageCount; stage++) {
                String stageText = properties.getProperty(prefix + "stage." + stage, "");
                if (!stageText.isBlank()) {
                    stageDescriptions.add(stageText);
                }
            }

            if (!questId.isBlank() && !questName.isBlank() && !stageDescriptions.isEmpty()) {
                authoredQuests.add(new AuthoredQuest(questId, questName, stageDescriptions));
            }
        }

        String itemRoot = contentRoot(properties, "item", "customItem");
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

        String gatheringNodeRoot = contentRoot(properties, "gatheringNode", "customGatheringNode");
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
            if (nodeType == GatheringNodeType.TREE && framePaths.size() > 2) {
                framePaths = List.of(framePaths.get(0), framePaths.get(framePaths.size() - 1));
            }

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
                        frameDurationMs,
                        visualScale,
                        gatheringSkill,
                        lootEntries,
                        smeltRequiredLevel
                ));
            }
        }

        String cookingRecipeRoot = contentRoot(properties, "cookingRecipe", "customCookingRecipe");
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

        String craftingRecipeRoot = properties.containsKey("craftingRecipe.count")
                ? "craftingRecipe"
                : properties.containsKey("compositeRecipe.count")
                ? "compositeRecipe"
                : "customCompositeRecipe";
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
                    "craftingRecipe".equals(craftingRecipeRoot) ? CharacterSkill.CRAFTING : CharacterSkill.SMITHING
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

        String mobRoot = contentRoot(properties, "mob", "customMob");
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
            List<SkillLibrary> skillIds = readSkillList(properties.getProperty(prefix + "skillIds", ""));
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

        String limbRoot = contentRoot(properties, "limb", "customLimb");
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
            List<SkillLibrary> skillIds = readSkillList(properties.getProperty(prefix + "skillIds", ""));
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

        String npcRoot = contentRoot(properties, "npc", "customNpc");
        int customNpcCount = readInt(properties, npcRoot + ".count", 0);
        List<CustomNpc> customNpcs = new ArrayList<>();
        for (int i = 0; i < customNpcCount; i++) {
            String prefix = npcRoot + "." + i + ".";
            String npcId = properties.getProperty(prefix + "npcId", "");
            String npcName = properties.getProperty(prefix + "displayName", "");
            String imagePath = properties.getProperty(prefix + "imagePath", "");
            String talkSoundPath = properties.getProperty(prefix + "talkSoundPath", "");
            String interactionId = properties.getProperty(prefix + "interactionId", "");
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
                        npcId, npcName, imagePath, talkSoundPath, interactionId, shop, characterModel));
            }
        }

        return new MapDesign(width, height, displayName, description, musicPath, skyboxPath,
                primaryTheme, alternateTheme, tiles, themeIndexes, mapPaint, mapGeometry, mobAreas,
                placements, authoredDialogues, authoredQuests, customItems, customMobs, customLimbs,
                customNpcs, customGatheringNodes, customCookingRecipes, craftingRecipes, triggers,
                lightingSettings, lights, spawnX, spawnY);
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
                    List.of(), List.of(), List.of(), List.of()
            );
        }
        return new AuthoredContent(
                design.authoredDialogues(),
                design.authoredQuests(),
                design.customItems(),
                design.customMobs(),
                design.customLimbs(),
                design.customNpcs(),
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
            hydratePlacement(dungeonMap, entities, tileInteractions, design.authoredDialogues(), design.customItems(), design.customMobs(), design.customLimbs(), design.customNpcs(), design.customGatheringNodes(), placement);
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
            List<AuthoredDialogue> authoredDialogues,
            List<CustomItem> customItems,
            List<CustomMob> customMobs,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs,
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
                case GENERIC_NPC -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    MapEntity legacyNpc = createLegacyNpc(placement.id(), placement.x(), placement.y());
                    if (legacyNpc != null) {
                        entities.add(legacyNpc);
                    }
                }
                case MAIN_NPC -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    MapEntity legacyNpc = createLegacyNpc(placement.id(), placement.x(), placement.y());
                    if (legacyNpc != null) {
                        entities.add(legacyNpc);
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
                case AUTHORED_DIALOGUE_NPC -> {
                    AuthoredDialogue dialogue = findAuthoredDialogue(placement.id(), authoredDialogues);
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(new MapEntity(
                            dialogue == null ? "NPC" : dialogue.speakerName(),
                            Library.EntityType.NPC,
                            placement.x(),
                            placement.y(),
                            AssetLoader.loadImage(DEFAULT_NPC_VISUAL_PATH)
                    ).withInteractionId(placement.id()));
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

    private static AuthoredDialogue findAuthoredDialogue(
            String interactionId,
            List<AuthoredDialogue> authoredDialogues
    ) {
        if (interactionId == null || authoredDialogues == null) {
            return null;
        }

        for (AuthoredDialogue dialogue : authoredDialogues) {
            if (interactionId.equals(dialogue.interactionId())) {
                return dialogue;
            }
        }

        return null;
    }

    private static MapEntity createLegacyNpc(String id, int x, int y) {
        if ("GOBLIN_MERCHANT".equals(id)) {
            return new MapEntity(
                    "Goblin Merchant",
                    Library.EntityType.NPC,
                    x,
                    y,
                    AssetLoader.loadImage("assets/images/monster/Nov-2015/mon/goblin.png")
            ).withTalkSoundPath("assets/sounds/generated/gobbo_talk.wav");
        }
        if ("TIPPING_THE_HAT_SKELETON".equals(id)) {
            return new MapEntity(
                    "Sir Tibia",
                    Library.EntityType.NPC,
                    x,
                    y,
                    AssetLoader.loadImage("assets/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png")
            ).withTalkSoundPath("assets/sounds/generated/skelle_talk.wav");
        }
        return null;
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
            if (customNode.nodeType() == GatheringNodeType.FISHING_SPOT) {
                dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FISHING_WATER);
                tileInteractions.add(new GeneratedDungeon.TileInteraction(
                        placement.x(),
                        placement.y(),
                        customNode.interactionId()
                ));
                return;
            }

            dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
            entities.add(customNode.createEntity(placement.x(), placement.y()));
            return;
        }

        if ("FISHING_SHOAL".equals(placement.id())) {
            dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FISHING_WATER);
            tileInteractions.add(new GeneratedDungeon.TileInteraction(
                    placement.x(),
                    placement.y(),
                    "fishing_shoal"
            ));
            return;
        }

        dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
        if ("MINERAL_ROCK_A".equals(placement.id())) {
            entities.add(new MapEntity(
                    "Mineral Rock",
                    Library.EntityType.TRAP,
                    placement.x(),
                    placement.y(),
                    AssetLoader.loadImage("assets/images/generic/64x64/A_Rock1_Node1.png")
            ).withInteractionId("mineral_rock_basic").blocksMovement(true).withVisualScale(1.35));
        } else if ("DECORATIVE_SHOAL".equals(placement.id())) {
            entities.add(new MapEntity(
                    "Shallow Water",
                    Library.EntityType.TRAP,
                    placement.x(),
                    placement.y(),
                    AssetLoader.loadImage("assets/images/monster/Nov-2015/dngn/water/shoals_shallow_water4.png")
            ));
        }
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
        properties.setProperty(prefix + "label", choice.label());
        properties.setProperty(prefix + "bodyText", choice.bodyText());
        properties.setProperty(prefix + "targetNodeId", choice.targetNodeId());
        properties.setProperty(prefix + "questId", choice.questId());
        properties.setProperty(prefix + "questStage", String.valueOf(choice.questStage()));
        properties.setProperty(prefix + "requiredItemName", choice.requiredItemName());
        properties.setProperty(prefix + "takeItemName", choice.takeItemName());
        properties.setProperty(prefix + "giveItemName", choice.giveItemName());
        properties.setProperty(prefix + "giveGold", String.valueOf(choice.giveGold()));
        properties.setProperty(prefix + "giveSkill", choice.giveSkill() == null ? "" : choice.giveSkill().name());
        properties.setProperty(prefix + "giveSkillXp", String.valueOf(choice.giveSkillXp()));
        properties.setProperty(prefix + "firstTalkOnly", String.valueOf(choice.firstTalkOnly()));
    }

    private static AuthoredDialogueChoice readAuthoredDialogueChoice(Properties properties, String prefix) {
        String label = properties.getProperty(prefix + "label", "");
        String choiceBodyText = properties.getProperty(prefix + "bodyText", "");
        String targetNodeId = properties.getProperty(prefix + "targetNodeId", "");
        String questId = properties.getProperty(prefix + "questId", "");
        int questStage = readInt(properties, prefix + "questStage", -1);
        String requiredItemName = properties.getProperty(prefix + "requiredItemName", "");
        String takeItemName = properties.getProperty(prefix + "takeItemName", "");
        String giveItemName = properties.getProperty(prefix + "giveItemName", "");
        int giveGold = readInt(properties, prefix + "giveGold", 0);
        CharacterSkill giveSkill = readSkill(properties.getProperty(prefix + "giveSkill", ""));
        int giveSkillXp = readInt(properties, prefix + "giveSkillXp", 0);
        boolean firstTalkOnly = Boolean.parseBoolean(properties.getProperty(prefix + "firstTalkOnly", "false"));
        if (label.isBlank() || (choiceBodyText.isBlank() && targetNodeId.isBlank())) {
            return null;
        }
        return new AuthoredDialogueChoice(
                label,
                choiceBodyText,
                targetNodeId,
                questId,
                questStage,
                requiredItemName,
                takeItemName,
                giveItemName,
                giveGold,
                giveSkill,
                giveSkillXp,
                firstTalkOnly
        );
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
                    readDouble(properties, prefix + "flicker", 0.0),
                    Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "true"))
            ));
        }
        return lights;
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

    private static String legacyVisualPath(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_NPC_VISUAL_PATH;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SKELETON" -> "assets/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png";
            case "SLIME" -> "assets/images/monster/Nov-2015/mon/amorphous/jelly.png";
            case "GOBLIN" -> DEFAULT_NPC_VISUAL_PATH;
            default -> value;
        };
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

    private static List<SkillLibrary> readSkillList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<SkillLibrary> skills = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            skills.add(SkillLibrary.valueOf(trimmed));
        }

        return skills;
    }

    private static String joinSkills(List<SkillLibrary> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        return String.join(",", skills.stream().map(SkillLibrary::name).toList());
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

    private static String contentRoot(Properties properties, String currentRoot, String legacyRoot) {
        return properties.containsKey(currentRoot + ".count") ? currentRoot : legacyRoot;
    }

    private static void retainRequestedContentSegment(Properties properties, Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String requestedRoot = fileName.endsWith(".properties")
                ? fileName.substring(0, fileName.length() - ".properties".length())
                : fileName;
        String retainedRoot = switch (requestedRoot) {
            case "gathering_node" -> "gatheringNode";
            case "cooking_recipe" -> "cookingRecipe";
            case "crafting_recipe", "composite_recipe" -> "craftingRecipe";
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
        public MapDesign {
            displayName = displayName == null || displayName.isBlank() ? "Untitled Map" : displayName;
            description = description == null ? "" : description;
            musicPath = musicPath == null ? "" : musicPath.trim();
            skyboxPath = SkyboxSpec.parseOrDefault(skyboxPath).encode();
            mapPaint = mapPaint == null ? MapPaintData.blank(width, height) : mapPaint;
            mapGeometry = mapGeometry == null ? MapGeometryData.blank(width, height) : mapGeometry;
            mobAreas = mobAreas == null ? MobAreaData.blank(width, height) : mobAreas;
            authoredQuests = authoredQuests == null ? new ArrayList<>() : authoredQuests;
            customItems = customItems == null ? new ArrayList<>() : customItems;
            customMobs = customMobs == null ? new ArrayList<>() : customMobs;
            customLimbs = customLimbs == null ? new ArrayList<>() : customLimbs;
            customNpcs = customNpcs == null ? new ArrayList<>() : customNpcs;
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
                    mobAreas, placements, authoredDialogues, authoredQuests, customItems, customMobs,
                    customLimbs, customNpcs, customGatheringNodes, customCookingRecipes, craftingRecipes,
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
                    MobAreaData.blank(width, height), placements, authoredDialogues, authoredQuests,
                    customItems, customMobs, customLimbs, customNpcs, customGatheringNodes,
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

    public record MapTrigger(
            String id,
            int x,
            int y,
            TriggerFireMode fireMode,
            boolean oneShot,
            String requiredQuestId,
            int requiredQuestStage,
            List<TriggerAction> actions
    ) {
        public MapTrigger {
            id = id == null ? "" : id;
            fireMode = fireMode == null ? TriggerFireMode.ON_ENTRY : fireMode;
            requiredQuestId = requiredQuestId == null ? "" : requiredQuestId.trim();
            requiredQuestStage = Math.max(0, requiredQuestStage);
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
            this(id, x, y, fireMode, oneShot, "", 0, actions);
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
            List<CustomGatheringNode> customGatheringNodes,
            List<CustomCookingRecipe> customCookingRecipes,
            List<CraftingRecipe> craftingRecipes
    ) {
        public AuthoredContent {
            authoredDialogues = authoredDialogues == null ? List.of() : List.copyOf(authoredDialogues);
            authoredQuests = authoredQuests == null ? List.of() : List.copyOf(authoredQuests);
            customItems = customItems == null ? List.of() : List.copyOf(customItems);
            customMobs = customMobs == null ? List.of() : List.copyOf(customMobs);
            customLimbs = customLimbs == null ? List.of() : List.copyOf(customLimbs);
            customNpcs = customNpcs == null ? List.of() : List.copyOf(customNpcs);
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
            String rewardItemId,
            CharacterSkill rewardSkill,
            int rewardSkillXp,
            int rewardGold,
            String questId,
            int questStage,
            List<AuthoredDialogueChoice> choices,
            List<AuthoredDialogueNode> nodes
    ) {
        public AuthoredDialogue(String interactionId, String speakerName, String bodyText) {
            this(interactionId, speakerName, bodyText, "", DEFAULT_NPC_VISUAL_PATH);
        }

        public AuthoredDialogue(
                String interactionId,
                String speakerName,
                String bodyText,
                String followUpInteractionId
        ) {
            this(interactionId, speakerName, bodyText, followUpInteractionId, DEFAULT_NPC_VISUAL_PATH);
        }

        public AuthoredDialogue(
                String interactionId,
                String speakerName,
                String bodyText,
                String followUpInteractionId,
                String visualPath
        ) {
            this(interactionId, speakerName, bodyText, followUpInteractionId, visualPath, "", null, 0, 0, "", -1, List.of(), List.of());
        }

        public AuthoredDialogue {
            followUpInteractionId = followUpInteractionId == null ? "" : followUpInteractionId;
            visualPath = visualPath == null || visualPath.isBlank() ? DEFAULT_NPC_VISUAL_PATH : visualPath;
            rewardItemId = rewardItemId == null ? "" : rewardItemId;
            rewardSkillXp = Math.max(0, rewardSkillXp);
            rewardGold = Math.max(0, rewardGold);
            questId = questId == null ? "" : questId;
            questStage = Math.max(-1, questStage);
            choices = choices == null ? List.of() : List.copyOf(choices);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record AuthoredDialogueChoice(
            String label,
            String bodyText,
            String targetNodeId,
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
        public AuthoredDialogueChoice(String label, String bodyText) {
            this(label, bodyText, "", "", -1, "", "", "", 0, null, 0, false);
        }

        public AuthoredDialogueChoice {
            label = label == null || label.isBlank() ? "Continue" : label;
            bodyText = bodyText == null ? "" : bodyText;
            targetNodeId = targetNodeId == null ? "" : targetNodeId;
            questId = questId == null ? "" : questId;
            questStage = Math.max(-1, questStage);
            requiredItemName = requiredItemName == null ? "" : requiredItemName;
            takeItemName = takeItemName == null ? "" : takeItemName;
            giveItemName = giveItemName == null ? "" : giveItemName;
            giveGold = Math.max(0, giveGold);
            giveSkillXp = Math.max(0, giveSkillXp);
        }
    }

    public record AuthoredDialogueNode(String nodeId, String bodyText, List<AuthoredDialogueChoice> choices) {
        public AuthoredDialogueNode {
            nodeId = nodeId == null ? "" : nodeId.trim();
            bodyText = bodyText == null ? "" : bodyText;
            choices = choices == null ? List.of() : List.copyOf(choices);
        }
    }

    public record AuthoredQuest(String questId, String displayName, List<String> stageDescriptions) {
        public AuthoredQuest {
            questId = questId == null ? "" : questId;
            displayName = displayName == null || displayName.isBlank() ? "Untitled Quest" : displayName;
            stageDescriptions = stageDescriptions == null || stageDescriptions.isEmpty()
                    ? List.of("Begin the quest.", "Complete.")
                    : new ArrayList<>(stageDescriptions);
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
            List<SkillLibrary> skillIds,
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
                List<SkillLibrary> skillIds,
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
                List<SkillLibrary> skillIds,
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
            List<SkillLibrary> skillIds,
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
                List<SkillLibrary> skillIds
        ) {
            this(limbId, displayName, limbSlot, iconPath, condition, description,
                    sourceCreatureId, paperDollSourcePath, statBonuses, skillIds, "", "");
        }

        public LimbItem createLimb() {
            return new LimbItem(
                    displayName,
                    sourceCreatureId,
                    sourceCreatureId,
                    limbSlot,
                    statBonuses,
                    skillIds.stream().map(SkillLibrary::createSkill).toList(),
                    condition,
                    iconPath,
                    description,
                    paperDollSourcePath
            ).withFirstPersonModel(firstPersonModelPath, firstPersonRigId);
        }
    }

    public record CustomNpc(
            String npcId,
            String displayName,
            String imagePath,
            String talkSoundPath,
            String interactionId,
            CustomShop shop,
            CharacterModelDefinition characterModel
    ) {
        public CustomNpc(
                String npcId,
                String displayName,
                String imagePath,
                String talkSoundPath,
                String interactionId
        ) {
            this(npcId, displayName, imagePath, talkSoundPath, interactionId, null,
                    CharacterModelDefinition.empty());
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
                    CharacterModelDefinition.empty());
        }

        public CustomNpc {
            npcId = npcId == null ? "" : npcId;
            displayName = displayName == null || displayName.isBlank() ? "Custom NPC" : displayName;
            imagePath = imagePath == null ? "" : imagePath;
            talkSoundPath = talkSoundPath == null ? "" : talkSoundPath;
            interactionId = interactionId == null ? "" : interactionId;
            characterModel = characterModel == null ? CharacterModelDefinition.empty() : characterModel;
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
            if (shop == null) {
                return entity.withInteractionId(interactionId);
            }
            return entity
                    .withInteractionId("custom_shop")
                    .withShopBlueprint(shop.toBlueprint());
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
            int frameDurationMs,
            double visualScale,
            CharacterSkill gatheringSkill,
            List<CustomDropEntry> lootEntries,
            int smeltRequiredLevel
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
                    frameDurationMs,
                    visualScale,
                    defaultGatheringSkill(nodeType),
                    outputItemId == null || outputItemId.isBlank()
                            ? List.of()
                            : List.of(new CustomDropEntry(outputItemId, 1.0)),
                    1
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
            if ("node_oak_tree".equalsIgnoreCase(nodeId)) {
//                entity.withStaticModel(OAK_TREE_TEST_MODEL_PATH);
            }
            if (nodeType == GatheringNodeType.MINING_ROCK || nodeType == GatheringNodeType.TREE) {
                entity.blocksMovement(true);
            }
            return entity;
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
        GENERIC_NPC,
        MAIN_NPC,
        CUSTOM_NPC,
        ITEM,
        ENEMY,
        AUTHORED_DIALOGUE_NPC,
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
        ON_QUEST_STAGE
    }

    public enum TriggerActionType {
        CLOSE_DOOR,
        OPEN_DOOR
    }
}

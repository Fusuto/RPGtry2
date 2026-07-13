package org.main.content;

import org.main.core.CharacterSkill;
import org.main.core.GearDurability;
import org.main.core.GearMaterial;
import org.main.core.InventorySystem;
import org.main.core.Library;
import org.main.core.LimbItem;
import org.main.core.LimbSlot;
import org.main.core.PaperDollAssetLibrary;
import org.main.core.PlayerStat;
import org.main.core.GeneratedDungeon;
import org.main.engine.DungeonMap;
import org.main.engine.MapEntity;
import org.main.engine.AssetLoader;
import org.main.monsters.Monster;

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
import java.util.stream.Stream;

public final class MapDesignLibrary {
    public static final String ENEMY_SLIME = "slime";
    public static final String ENEMY_GOBLIN = "goblin";
    public static final String ENEMY_SKELETON = "skeleton";
    public static final String MAP_RESOURCE_FOLDER = "assets/editor/maps";
    public static final String CONTENT_RESOURCE_FOLDER = "assets/editor/content";
    public static final Path EDITOR_RESOURCE_FOLDER = Path.of("src", "main", "resources", "assets", "editor");
    public static final Path MAP_FOLDER = EDITOR_RESOURCE_FOLDER.resolve("maps");
    public static final Path CONTENT_FOLDER = EDITOR_RESOURCE_FOLDER.resolve("content");
    public static final Path SHARED_CONTENT_PATH = CONTENT_FOLDER.resolve("authored_content.properties");
    public static final Path DATA_MAP_FOLDER = Path.of("data", "maps");
    public static final Path DATA_CONTENT_FOLDER = Path.of("data", "content");
    public static final Path DATA_SHARED_CONTENT_PATH = DATA_CONTENT_FOLDER.resolve("authored_content.properties");

    private MapDesignLibrary() {
    }

    public static List<CustomMob> defaultEnemies() {
        return List.of(defaultSlime(), defaultGoblin(), defaultSkeleton());
    }

    public static CustomMob defaultEnemy(String enemyId) {
        CustomMob enemy = findDefaultEnemy(enemyId);
        return enemy == null ? defaultSlime() : enemy;
    }

    public static CustomMob findDefaultEnemy(String enemyId) {
        if (enemyId == null) {
            return null;
        }
        return switch (enemyId.toLowerCase(Locale.ROOT)) {
            case ENEMY_GOBLIN -> defaultGoblin();
            case ENEMY_SKELETON -> defaultSkeleton();
            case ENEMY_SLIME -> defaultSlime();
            default -> null;
        };
    }

    public static Monster createDefaultEnemy(String enemyId) {
        return defaultEnemy(enemyId).createMonster();
    }

    private static CustomMob defaultSlime() {
        return new CustomMob(
                ENEMY_SLIME,
                "Slime",
                "assets/images/monster/Nov-2015/mon/amorphous/jelly.png",
                PaperDollAssetLibrary.DEFAULT_BASE,
                stats(20, 5, 5, 0, 5, 5, 3),
                10,
                "A quivering mass of dungeon slime.",
                "assets/sounds/generated/enemy_attack.wav",
                "assets/sounds/generated/player_hit.wav",
                5,
                List.of(SkillLibrary.ABSORB),
                List.of(
                        new CustomDropEntry(ItemLibrary.SLIME.name(), 1.0),
                        new CustomDropEntry(ItemLibrary.POTION.name(), 0.15)
                )
        );
    }

    private static CustomMob defaultGoblin() {
        return new CustomMob(
                ENEMY_GOBLIN,
                "Goblin",
                "assets/images/monster/Nov-2015/mon/goblin.png",
                "assets/images/monster/Nov-2015/player/base/kobold_m.png",
                stats(10, 4, 4, 1, 4, 4, 2),
                12,
                "A wiry goblin clutching a crude blade.",
                "assets/sounds/generated/enemy_attack.wav",
                "assets/sounds/generated/player_hit.wav",
                4,
                List.of(),
                List.of(
                        new CustomDropEntry(ItemLibrary.IRON_SWORD.name(), 0.12),
                        new CustomDropEntry(ItemLibrary.POTION.name(), 0.20)
                )
        );
    }

    private static CustomMob defaultSkeleton() {
        return new CustomMob(
                ENEMY_SKELETON,
                "Skeleton",
                "assets/images/monster/Nov-2015/mon/undead/skeletons/skeleton_humanoid_small.png",
                "assets/images/monster/Nov-2015/player/base/mummy_m.png",
                stats(12, 5, 5, 2, 5, 2, 3),
                18,
                "A rattling corpse animated by old magic.",
                "assets/sounds/generated/enemy_attack.wav",
                "assets/sounds/generated/player_hit.wav",
                2,
                List.of(),
                List.of(
                        new CustomDropEntry(ItemLibrary.BONES.name(), 1.0),
                        new CustomDropEntry(ItemLibrary.IRON_SWORD.name(), 0.10),
                        new CustomDropEntry(ItemLibrary.LEATHER_CAP.name(), 0.08)
                )
        );
    }

    private static EnumMap<PlayerStat, Integer> stats(
            int vitality,
            int attack,
            int strength,
            int defense,
            int agility,
            int intelligence,
            int willpower
    ) {
        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        stats.put(PlayerStat.VITALITY, vitality);
        stats.put(PlayerStat.ATTACK, attack);
        stats.put(PlayerStat.STRENGTH, strength);
        stats.put(PlayerStat.DEFENSE, defense);
        stats.put(PlayerStat.AGILITY, agility);
        stats.put(PlayerStat.INTELLIGENCE, intelligence);
        stats.put(PlayerStat.WILLPOWER, willpower);
        return stats;
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
        if (design == null || path == null) {
            return;
        }

        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.setProperty("displayName", design.displayName());
        properties.setProperty("description", design.description());
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

        properties.setProperty("placement.count", String.valueOf(design.placements().size()));
        for (int i = 0; i < design.placements().size(); i++) {
            MapPlacement placement = design.placements().get(i);
            String prefix = "placement." + i + ".";
            properties.setProperty(prefix + "kind", placement.kind().name());
            properties.setProperty(prefix + "id", placement.id());
            properties.setProperty(prefix + "x", String.valueOf(placement.x()));
            properties.setProperty(prefix + "y", String.valueOf(placement.y()));
        }

        properties.setProperty("authoredDialogue.count", String.valueOf(design.authoredDialogues().size()));
        for (int i = 0; i < design.authoredDialogues().size(); i++) {
            AuthoredDialogue authoredDialogue = design.authoredDialogues().get(i);
            String prefix = "authoredDialogue." + i + ".";
            properties.setProperty(prefix + "interactionId", authoredDialogue.interactionId());
            properties.setProperty(prefix + "speakerName", authoredDialogue.speakerName());
            properties.setProperty(prefix + "bodyText", authoredDialogue.bodyText());
            properties.setProperty(prefix + "followUpInteractionId", authoredDialogue.followUpInteractionId());
            properties.setProperty(prefix + "visualPath", authoredDialogue.visualPath());
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

        properties.setProperty("authoredQuest.count", String.valueOf(design.authoredQuests().size()));
        for (int i = 0; i < design.authoredQuests().size(); i++) {
            AuthoredQuest authoredQuest = design.authoredQuests().get(i);
            String prefix = "authoredQuest." + i + ".";
            properties.setProperty(prefix + "questId", authoredQuest.questId());
            properties.setProperty(prefix + "displayName", authoredQuest.displayName());
            properties.setProperty(prefix + "stage.count", String.valueOf(authoredQuest.stageDescriptions().size()));
            for (int stage = 0; stage < authoredQuest.stageDescriptions().size(); stage++) {
                properties.setProperty(prefix + "stage." + stage, authoredQuest.stageDescriptions().get(stage));
            }
        }

        properties.setProperty("customItem.count", String.valueOf(design.customItems().size()));
        for (int i = 0; i < design.customItems().size(); i++) {
            CustomItem customItem = design.customItems().get(i);
            String prefix = "customItem." + i + ".";
            properties.setProperty(prefix + "itemId", customItem.itemId());
            properties.setProperty(prefix + "displayName", customItem.displayName());
            properties.setProperty(prefix + "itemType", customItem.itemType().name());
            properties.setProperty(prefix + "iconPath", customItem.iconPath());
            properties.setProperty(prefix + "material", customItem.material().name());
            properties.setProperty(prefix + "healAmount", String.valueOf(customItem.healAmount()));
            properties.setProperty(prefix + "baseGoldValue", String.valueOf(customItem.baseGoldValue()));
            properties.setProperty(prefix + "examineText", customItem.examineText());
            properties.setProperty(prefix + "statBonusTarget", customItem.statBonusTarget() == null ? "" : customItem.statBonusTarget().name());
            properties.setProperty(prefix + "smithingRecipeEnabled", String.valueOf(customItem.smithingRecipeEnabled()));
            properties.setProperty(prefix + "smithingRequiredBars", String.valueOf(customItem.smithingRequiredBars()));
            properties.setProperty(prefix + "smithingRequiredLevel", String.valueOf(customItem.smithingRequiredLevel()));
            properties.setProperty(prefix + "smithingXpReward", String.valueOf(customItem.smithingXpReward()));
        }

        properties.setProperty("customMob.count", String.valueOf(design.customMobs().size()));
        for (int i = 0; i < design.customMobs().size(); i++) {
            CustomMob customMob = design.customMobs().get(i);
            String prefix = "customMob." + i + ".";
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
            properties.setProperty(prefix + "skillIds", joinSkills(customMob.skillIds()));
            properties.setProperty(prefix + "drop.count", String.valueOf(customMob.dropEntries().size()));
            for (int dropIndex = 0; dropIndex < customMob.dropEntries().size(); dropIndex++) {
                CustomDropEntry drop = customMob.dropEntries().get(dropIndex);
                String dropPrefix = prefix + "drop." + dropIndex + ".";
                properties.setProperty(dropPrefix + "itemId", drop.itemId());
                properties.setProperty(dropPrefix + "chance", String.valueOf(drop.chance()));
            }
        }

        properties.setProperty("customLimb.count", String.valueOf(design.customLimbs().size()));
        for (int i = 0; i < design.customLimbs().size(); i++) {
            CustomLimb customLimb = design.customLimbs().get(i);
            String prefix = "customLimb." + i + ".";
            properties.setProperty(prefix + "limbId", customLimb.limbId());
            properties.setProperty(prefix + "displayName", customLimb.displayName());
            properties.setProperty(prefix + "limbSlot", customLimb.limbSlot().name());
            properties.setProperty(prefix + "iconPath", customLimb.iconPath());
            properties.setProperty(prefix + "condition", customLimb.condition().name());
            properties.setProperty(prefix + "description", customLimb.description());
            properties.setProperty(prefix + "paperDollSourcePath", customLimb.paperDollSourcePath());
            properties.setProperty(prefix + "skillIds", joinSkills(customLimb.skillIds()));
            for (PlayerStat stat : PlayerStat.values()) {
                properties.setProperty(prefix + "stat." + stat.name(), String.valueOf(customLimb.statBonuses().getOrDefault(stat, 0)));
            }
        }

        properties.setProperty("customNpc.count", String.valueOf(design.customNpcs().size()));
        for (int i = 0; i < design.customNpcs().size(); i++) {
            CustomNpc customNpc = design.customNpcs().get(i);
            String prefix = "customNpc." + i + ".";
            properties.setProperty(prefix + "npcId", customNpc.npcId());
            properties.setProperty(prefix + "displayName", customNpc.displayName());
            properties.setProperty(prefix + "imagePath", customNpc.imagePath());
            properties.setProperty(prefix + "talkSoundPath", customNpc.talkSoundPath());
            properties.setProperty(prefix + "interactionId", customNpc.interactionId());
        }

        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "Aether map design");
        }
    }

    public static MapDesign load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = openMapDesignStream(path)) {
            properties.load(inputStream);
        }

        int width = readInt(properties, "width", 12);
        int height = readInt(properties, "height", 12);
        String displayName = properties.getProperty("displayName", fallbackDisplayName(path));
        String description = properties.getProperty("description", "");
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

        int authoredDialogueCount = readInt(properties, "authoredDialogue.count", 0);
        List<AuthoredDialogue> authoredDialogues = new ArrayList<>();
        for (int i = 0; i < authoredDialogueCount; i++) {
            String prefix = "authoredDialogue." + i + ".";
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

        int authoredQuestCount = readInt(properties, "authoredQuest.count", 0);
        List<AuthoredQuest> authoredQuests = new ArrayList<>();
        for (int i = 0; i < authoredQuestCount; i++) {
            String prefix = "authoredQuest." + i + ".";
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

        int customItemCount = readInt(properties, "customItem.count", 0);
        List<CustomItem> customItems = new ArrayList<>();
        for (int i = 0; i < customItemCount; i++) {
            String prefix = "customItem." + i + ".";
            String itemId = properties.getProperty(prefix + "itemId", "");
            String itemName = properties.getProperty(prefix + "displayName", "");
            InventorySystem.ItemType itemType = readItemType(properties.getProperty(prefix + "itemType", ""), InventorySystem.ItemType.MISC);
            String iconPath = properties.getProperty(prefix + "iconPath", "");
            GearMaterial material = readMaterial(properties.getProperty(prefix + "material", ""), GearMaterial.NONE);
            int healAmount = readInt(properties, prefix + "healAmount", 0);
            int baseGoldValue = readInt(properties, prefix + "baseGoldValue", 10);
            String examineText = properties.getProperty(prefix + "examineText", "");
            PlayerStat statBonusTarget = readPlayerStat(properties.getProperty(prefix + "statBonusTarget", ""));
            boolean smithingRecipeEnabled = Boolean.parseBoolean(properties.getProperty(prefix + "smithingRecipeEnabled", "false"));
            int smithingRequiredBars = readInt(properties, prefix + "smithingRequiredBars", 1);
            int smithingRequiredLevel = readInt(properties, prefix + "smithingRequiredLevel", 1);
            int smithingXpReward = readInt(properties, prefix + "smithingXpReward", 25);
            if (!itemId.isBlank() && !itemName.isBlank()) {
                customItems.add(new CustomItem(
                        itemId,
                        itemName,
                        itemType,
                        iconPath,
                        material,
                        healAmount,
                        baseGoldValue,
                        examineText,
                        statBonusTarget,
                        smithingRecipeEnabled,
                        smithingRequiredBars,
                        smithingRequiredLevel,
                        smithingXpReward
                ));
            }
        }

        int customMobCount = readInt(properties, "customMob.count", 0);
        List<CustomMob> customMobs = new ArrayList<>();
        for (int i = 0; i < customMobCount; i++) {
            String prefix = "customMob." + i + ".";
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
                customMobs.add(new CustomMob(mobId, mobName, imagePath, paperDollSourcePath, statValues, xpReward, mobDescription, attackSoundPath, damageSoundPath, combatAiIntelligence, skillIds, dropEntries));
            }
        }

        int customLimbCount = readInt(properties, "customLimb.count", 0);
        List<CustomLimb> customLimbs = new ArrayList<>();
        for (int i = 0; i < customLimbCount; i++) {
            String prefix = "customLimb." + i + ".";
            String limbId = properties.getProperty(prefix + "limbId", "");
            String limbName = properties.getProperty(prefix + "displayName", "");
            LimbSlot limbSlot = readLimbSlot(properties.getProperty(prefix + "limbSlot", ""), LimbSlot.HEAD);
            String iconPath = properties.getProperty(prefix + "iconPath", "");
            GearDurability condition = readDurability(properties.getProperty(prefix + "condition", ""), GearDurability.PERFECT);
            String limbDescription = properties.getProperty(prefix + "description", "");
            String paperDollSourcePath = properties.getProperty(prefix + "paperDollSourcePath", "");
            List<SkillLibrary> skillIds = readSkillList(properties.getProperty(prefix + "skillIds", ""));
            EnumMap<PlayerStat, Integer> statBonuses = new EnumMap<>(PlayerStat.class);
            for (PlayerStat stat : PlayerStat.values()) {
                statBonuses.put(stat, readInt(properties, prefix + "stat." + stat.name(), 0));
            }
            if (!limbId.isBlank() && !limbName.isBlank()) {
                customLimbs.add(new CustomLimb(limbId, limbName, limbSlot, iconPath, condition, limbDescription, paperDollSourcePath, statBonuses, skillIds));
            }
        }

        int customNpcCount = readInt(properties, "customNpc.count", 0);
        List<CustomNpc> customNpcs = new ArrayList<>();
        for (int i = 0; i < customNpcCount; i++) {
            String prefix = "customNpc." + i + ".";
            String npcId = properties.getProperty(prefix + "npcId", "");
            String npcName = properties.getProperty(prefix + "displayName", "");
            String imagePath = properties.getProperty(prefix + "imagePath", "");
            String talkSoundPath = properties.getProperty(prefix + "talkSoundPath", "");
            String interactionId = properties.getProperty(prefix + "interactionId", "");
            if (!npcId.isBlank() && !npcName.isBlank()) {
                customNpcs.add(new CustomNpc(npcId, npcName, imagePath, talkSoundPath, interactionId));
            }
        }

        return new MapDesign(width, height, displayName, description, primaryTheme, alternateTheme, tiles, themeIndexes, placements, authoredDialogues, authoredQuests, customItems, customMobs, customLimbs, customNpcs, spawnX, spawnY);
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
        AuthoredContent defaultContent = defaultAuthoredContent();
        AuthoredContent bundledContent = loadSharedContentFrom(Path.of(CONTENT_RESOURCE_FOLDER, "authored_content.properties"));
        AuthoredContent dataContent = Files.isRegularFile(DATA_SHARED_CONTENT_PATH)
                ? loadSharedContentFrom(DATA_SHARED_CONTENT_PATH)
                : new AuthoredContent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return mergeContent(mergeContent(defaultContent, bundledContent), dataContent);
    }

    private static AuthoredContent defaultAuthoredContent() {
        return new AuthoredContent(List.of(), List.of(), List.of(), defaultEnemies(), List.of(), List.of());
    }

    private static AuthoredContent loadSharedContentFrom(Path path) throws IOException {
        try {
            MapDesign contentDesign = load(path);
            return new AuthoredContent(
                    contentDesign.authoredDialogues(),
                    contentDesign.authoredQuests(),
                    contentDesign.customItems(),
                    contentDesign.customMobs(),
                    contentDesign.customLimbs(),
                    contentDesign.customNpcs()
            );
        } catch (IOException exception) {
            if (Files.isRegularFile(path)) {
                throw exception;
            }
            return new AuthoredContent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private static AuthoredContent mergeContent(AuthoredContent base, AuthoredContent override) {
        List<AuthoredDialogue> dialogues = new ArrayList<>(base.authoredDialogues());
        List<AuthoredQuest> quests = new ArrayList<>(base.authoredQuests());
        List<CustomItem> items = new ArrayList<>(base.customItems());
        List<CustomMob> mobs = new ArrayList<>(base.customMobs());
        List<CustomLimb> limbs = new ArrayList<>(base.customLimbs());
        List<CustomNpc> npcs = new ArrayList<>(base.customNpcs());

        mergeById(dialogues, override.authoredDialogues(), AuthoredDialogue::interactionId);
        mergeById(quests, override.authoredQuests(), AuthoredQuest::questId);
        mergeById(items, override.customItems(), CustomItem::itemId);
        mergeById(mobs, override.customMobs(), CustomMob::mobId);
        mergeById(limbs, override.customLimbs(), CustomLimb::limbId);
        mergeById(npcs, override.customNpcs(), CustomNpc::npcId);
        return new AuthoredContent(dialogues, quests, items, mobs, limbs, npcs);
    }

    private static <T> void mergeById(List<T> target, List<T> source, java.util.function.Function<T, String> idFunction) {
        for (T entry : source) {
            String id = idFunction.apply(entry);
            target.removeIf(existing -> idFunction.apply(existing).equals(id));
            target.add(entry);
        }
    }

    public static void saveSharedContent(AuthoredContent content) throws IOException {
        if (content == null) {
            return;
        }

        MapDesign contentDesign = createBlank(3, 3, ThemeLibrary.STONE_WOOD, ThemeLibrary.SANDSTONE_GATE);
        contentDesign = new MapDesign(
                contentDesign.width(),
                contentDesign.height(),
                "Authored Content",
                "Shared reusable authored content for the map editor.",
                contentDesign.primaryTheme(),
                contentDesign.alternateTheme(),
                contentDesign.tiles(),
                contentDesign.themeIndexes(),
                contentDesign.placements(),
                new ArrayList<>(content.authoredDialogues()),
                new ArrayList<>(content.authoredQuests()),
                new ArrayList<>(content.customItems()),
                new ArrayList<>(content.customMobs()),
                new ArrayList<>(content.customLimbs()),
                new ArrayList<>(content.customNpcs()),
                contentDesign.spawnX(),
                contentDesign.spawnY()
        );
        save(contentDesign, SHARED_CONTENT_PATH);
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

        for (MapPlacement placement : placements) {
            validatePlacement(design, issues, authoredIds, placement);
        }
        validateAuthoredDialogueActions(issues, authoredDialogues, design.authoredQuests(), design.customItems(), design.customLimbs());

        return issues;
    }

    public static boolean hasValidationErrors(MapDesign design) {
        return validate(design).stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    public static DungeonMap toDungeonMap(MapDesign design) {
        return new DungeonMap(copyTiles(design.tiles()), copyThemes(design.themeIndexes()));
    }

    public static GeneratedDungeon toGeneratedDungeon(MapDesign design, int playerX, int playerY) {
        DungeonMap dungeonMap = toDungeonMap(design);
        List<MapEntity> entities = new ArrayList<>();
        List<GeneratedDungeon.TileInteraction> tileInteractions = new ArrayList<>();

        for (MapPlacement placement : design.placements()) {
            hydratePlacement(dungeonMap, entities, tileInteractions, design.authoredDialogues(), design.customItems(), design.customMobs(), design.customLimbs(), design.customNpcs(), placement);
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
                design.customLimbs()
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
            MapPlacement placement
    ) {
        if (placement == null || !isInside(dungeonMap, placement.x(), placement.y())) {
            return;
        }

        try {
            switch (placement.kind()) {
                case CRAFTING_NODE -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(CraftingNodeLibrary.valueOf(placement.id()).createEntity(placement.x(), placement.y()));
                }
                case GATHERING_NODE -> hydrateGatheringNode(dungeonMap, entities, tileInteractions, placement);
                case GENERIC_NPC -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(GenericNpcLibrary.valueOf(placement.id()).createEntity(placement.x(), placement.y()));
                }
                case MAIN_NPC -> {
                    dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
                    entities.add(MainNpcLibrary.valueOf(placement.id()).createEntity(placement.x(), placement.y()));
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
                    if (customMob == null) {
                        customMob = findDefaultEnemy(placement.id());
                    }
                    if (customMob != null) {
                        entities.add(new MapEntity(customMob.createMonster(), placement.x(), placement.y()));
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
                            AssetLoader.loadImage(dialogue == null ? defaultEnemy(ENEMY_GOBLIN).imagePath() : dialogue.visualPath())
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

    private static InventorySystem.Item createItem(String itemId, List<CustomItem> customItems, List<CustomLimb> customLimbs) {
        CustomItem customItem = findCustomItem(itemId, customItems);
        if (customItem != null) {
            return customItem.createItem();
        }

        CustomLimb customLimb = findCustomLimb(itemId, customLimbs);
        if (customLimb != null) {
            return customLimb.createLimb();
        }

        return ItemLibrary.valueOf(itemId).createItem();
    }

    private static CustomItem findCustomItem(String itemId, List<CustomItem> customItems) {
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

    private static CustomMob findCustomMob(String mobId, List<CustomMob> customMobs) {
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

    private static CustomLimb findCustomLimb(String limbId, List<CustomLimb> customLimbs) {
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

    private static CustomNpc findCustomNpc(String npcId, List<CustomNpc> customNpcs) {
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

    private static void hydrateGatheringNode(
            DungeonMap dungeonMap,
            List<MapEntity> entities,
            List<GeneratedDungeon.TileInteraction> tileInteractions,
            MapPlacement placement
    ) {
        GatheringNodeLibrary node = GatheringNodeLibrary.valueOf(placement.id());

        if (node == GatheringNodeLibrary.FISHING_SHOAL) {
            dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FISHING_WATER);
            tileInteractions.add(new GeneratedDungeon.TileInteraction(
                    placement.x(),
                    placement.y(),
                    node.getInteractionId()
            ));
            return;
        }

        dungeonMap.setTile(placement.x(), placement.y(), Library.TileType.FLOOR);
        entities.add(node.createEntity(placement.x(), placement.y()));
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
                case CRAFTING_NODE -> CraftingNodeLibrary.valueOf(placement.id());
                case GATHERING_NODE -> GatheringNodeLibrary.valueOf(placement.id());
                case GENERIC_NPC -> GenericNpcLibrary.valueOf(placement.id());
                case MAIN_NPC -> MainNpcLibrary.valueOf(placement.id());
                case CUSTOM_NPC -> {
                    if (findCustomNpc(placement.id(), design.customNpcs()) == null) {
                        throw new IllegalArgumentException("Unknown custom NPC");
                    }
                }
                case ITEM -> {
                    if (findCustomItem(placement.id(), design.customItems()) == null
                            && findCustomLimb(placement.id(), design.customLimbs()) == null) {
                        ItemLibrary.valueOf(placement.id());
                    }
                }
                case ENEMY -> {
                    if (findCustomMob(placement.id(), design.customMobs()) == null
                            && findDefaultEnemy(placement.id()) == null) {
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
                    boolean knownInteraction = false;
                    for (InteractionLibrary interaction : InteractionLibrary.values()) {
                        if (interaction.getInteractionId().equals(placement.id())) {
                            knownInteraction = true;
                            break;
                        }
                    }
                    if (!knownInteraction) {
                        issues.add(new ValidationIssue(ValidationSeverity.WARNING, "Interaction " + placement.id() + " is not in InteractionLibrary."));
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
        List<String> builtInQuestIds = java.util.Arrays.stream(QuestLibrary.values())
                .map(QuestLibrary::getId)
                .toList();
        List<String> knownItemNames = knownItemDisplayNames(customItems, customLimbs);

        for (AuthoredDialogue dialogue : authoredDialogues) {
            if (!dialogue.rewardItemId().isBlank()) {
                try {
                    if (findCustomItem(dialogue.rewardItemId(), customItems) == null
                            && findCustomLimb(dialogue.rewardItemId(), customLimbs) == null) {
                        ItemLibrary.valueOf(dialogue.rewardItemId());
                    }
                } catch (IllegalArgumentException exception) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            "Authored dialogue " + dialogue.interactionId() + " has unknown reward item " + dialogue.rewardItemId() + "."
                    ));
                }
            }

            if (!dialogue.questId().isBlank()
                    && !authoredQuestIds.contains(dialogue.questId())
                    && !builtInQuestIds.contains(dialogue.questId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "Authored dialogue " + dialogue.interactionId() + " references missing quest " + dialogue.questId() + "."
                ));
            }
            List<String> nodeIds = dialogue.nodes().stream().map(AuthoredDialogueNode::nodeId).toList();
            validateAuthoredDialogueChoices(issues, dialogue, dialogue.choices(), authoredQuestIds, builtInQuestIds, nodeIds, knownItemNames, authoredQuests);
            for (AuthoredDialogueNode node : dialogue.nodes()) {
                validateAuthoredDialogueChoices(issues, dialogue, node.choices(), authoredQuestIds, builtInQuestIds, nodeIds, knownItemNames, authoredQuests);
            }
        }
    }

    private static List<String> knownItemDisplayNames(List<CustomItem> customItems, List<CustomLimb> customLimbs) {
        List<String> names = new ArrayList<>();
        for (ItemLibrary item : ItemLibrary.values()) {
            names.add(item.getDisplayName());
        }
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

    private static void validateAuthoredDialogueChoices(
            List<ValidationIssue> issues,
            AuthoredDialogue dialogue,
            List<AuthoredDialogueChoice> choices,
            List<String> authoredQuestIds,
            List<String> builtInQuestIds,
            List<String> nodeIds,
            List<String> knownItemNames,
            List<AuthoredQuest> authoredQuests
    ) {
        for (AuthoredDialogueChoice choice : choices) {
            if (!choice.questId().isBlank()
                    && !authoredQuestIds.contains(choice.questId())
                    && !builtInQuestIds.contains(choice.questId())) {
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

    private static int maxQuestStage(String questId, List<AuthoredQuest> authoredQuests) {
        for (QuestLibrary quest : QuestLibrary.values()) {
            if (quest.getId().equals(questId)) {
                return quest.getMaxStage();
            }
        }
        if (authoredQuests != null) {
            for (AuthoredQuest quest : authoredQuests) {
                if (quest.questId().equals(questId)) {
                    return quest.stageDescriptions().size() - 1;
                }
            }
        }
        return -1;
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
            return defaultEnemy(ENEMY_GOBLIN).imagePath();
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SKELETON" -> defaultEnemy(ENEMY_SKELETON).imagePath();
            case "SLIME" -> defaultEnemy(ENEMY_SLIME).imagePath();
            case "GOBLIN" -> defaultEnemy(ENEMY_GOBLIN).imagePath();
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

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
        public MapDesign {
            displayName = displayName == null || displayName.isBlank() ? "Untitled Map" : displayName;
            description = description == null ? "" : description;
            authoredQuests = authoredQuests == null ? new ArrayList<>() : authoredQuests;
            customItems = customItems == null ? new ArrayList<>() : customItems;
            customMobs = customMobs == null ? new ArrayList<>() : customMobs;
            customLimbs = customLimbs == null ? new ArrayList<>() : customLimbs;
            customNpcs = customNpcs == null ? new ArrayList<>() : customNpcs;
        }
    }

    public record MapPlacement(PlacementKind kind, String id, int x, int y) {
    }

    public record AuthoredContent(
            List<AuthoredDialogue> authoredDialogues,
            List<AuthoredQuest> authoredQuests,
            List<CustomItem> customItems,
            List<CustomMob> customMobs,
            List<CustomLimb> customLimbs,
            List<CustomNpc> customNpcs
    ) {
        public AuthoredContent {
            authoredDialogues = authoredDialogues == null ? List.of() : List.copyOf(authoredDialogues);
            authoredQuests = authoredQuests == null ? List.of() : List.copyOf(authoredQuests);
            customItems = customItems == null ? List.of() : List.copyOf(customItems);
            customMobs = customMobs == null ? List.of() : List.copyOf(customMobs);
            customLimbs = customLimbs == null ? List.of() : List.copyOf(customLimbs);
            customNpcs = customNpcs == null ? List.of() : List.copyOf(customNpcs);
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
            this(interactionId, speakerName, bodyText, "", defaultEnemy(ENEMY_GOBLIN).imagePath());
        }

        public AuthoredDialogue(
                String interactionId,
                String speakerName,
                String bodyText,
                String followUpInteractionId
        ) {
            this(interactionId, speakerName, bodyText, followUpInteractionId, defaultEnemy(ENEMY_GOBLIN).imagePath());
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
            visualPath = visualPath == null || visualPath.isBlank() ? defaultEnemy(ENEMY_GOBLIN).imagePath() : visualPath;
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
            GearMaterial material,
            int healAmount,
            int baseGoldValue,
            String examineText,
            PlayerStat statBonusTarget,
            boolean smithingRecipeEnabled,
            int smithingRequiredBars,
            int smithingRequiredLevel,
            int smithingXpReward
    ) {
        public CustomItem {
            itemId = itemId == null ? "" : itemId;
            displayName = displayName == null || displayName.isBlank() ? "Custom Item" : displayName;
            itemType = itemType == null ? InventorySystem.ItemType.MISC : itemType;
            iconPath = iconPath == null ? "" : iconPath;
            material = material == null ? GearMaterial.NONE : material;
            healAmount = Math.max(0, healAmount);
            baseGoldValue = Math.max(1, baseGoldValue);
            examineText = examineText == null ? "" : examineText;
            smithingRecipeEnabled = smithingRecipeEnabled && material.getFamily() == GearMaterial.MaterialFamily.METAL;
            smithingRequiredBars = Math.max(1, smithingRequiredBars);
            smithingRequiredLevel = Math.max(1, smithingRequiredLevel);
            smithingXpReward = Math.max(0, smithingXpReward);
        }

        public InventorySystem.Item createItem() {
            return new InventorySystem.Item(
                    displayName,
                    itemType,
                    iconPath,
                    null,
                    healAmount,
                    material,
                    GearDurability.PERFECT,
                    baseGoldValue,
                    examineText,
                    statBonusTarget
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
            List<SkillLibrary> skillIds,
            List<CustomDropEntry> dropEntries
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
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
            dropEntries = dropEntries == null ? List.of() : List.copyOf(dropEntries);
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
                            .toList()
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
            return itemId + " [" + Math.round(chance * 100.0) + "%]";
        }
    }

    public record CustomLimb(
            String limbId,
            String displayName,
            LimbSlot limbSlot,
            String iconPath,
            GearDurability condition,
            String description,
            String paperDollSourcePath,
            Map<PlayerStat, Integer> statBonuses,
            List<SkillLibrary> skillIds
    ) {
        public CustomLimb {
            limbId = limbId == null ? "" : limbId;
            displayName = displayName == null || displayName.isBlank() ? "Custom Limb" : displayName;
            limbSlot = limbSlot == null ? LimbSlot.HEAD : limbSlot;
            iconPath = iconPath == null ? "" : iconPath;
            condition = condition == null ? GearDurability.PERFECT : condition;
            description = description == null ? "" : description;
            paperDollSourcePath = paperDollSourcePath == null ? "" : paperDollSourcePath;
            EnumMap<PlayerStat, Integer> safeStats = new EnumMap<>(PlayerStat.class);
            if (statBonuses != null) {
                for (PlayerStat stat : PlayerStat.values()) {
                    safeStats.put(stat, Math.max(0, statBonuses.getOrDefault(stat, 0)));
                }
            }
            statBonuses = safeStats;
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        }

        public LimbItem createLimb() {
            return new LimbItem(
                    displayName,
                    null,
                    limbSlot,
                    statBonuses,
                    skillIds.stream().map(SkillLibrary::createSkill).toList(),
                    condition,
                    iconPath,
                    description,
                    paperDollSourcePath
            );
        }
    }

    public record CustomNpc(
            String npcId,
            String displayName,
            String imagePath,
            String talkSoundPath,
            String interactionId
    ) {
        public CustomNpc {
            npcId = npcId == null ? "" : npcId;
            displayName = displayName == null || displayName.isBlank() ? "Custom NPC" : displayName;
            imagePath = imagePath == null ? "" : imagePath;
            talkSoundPath = talkSoundPath == null ? "" : talkSoundPath;
            interactionId = interactionId == null ? "" : interactionId;
        }

        public MapEntity createEntity(int x, int y) {
            return new MapEntity(
                    displayName,
                    Library.EntityType.NPC,
                    x,
                    y,
                    imagePath.isBlank() ? null : AssetLoader.loadImage(imagePath)
            )
                    .withInteractionId(interactionId)
                    .withTalkSoundPath(talkSoundPath);
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
}

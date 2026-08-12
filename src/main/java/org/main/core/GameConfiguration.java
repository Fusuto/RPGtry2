package org.main.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GameConfiguration {
    private static final Path CONFIG_PATH = Path.of("data", "configuration.properties");
    private static final String PACKAGED_CONFIG_PATH = "assets/configuration.properties";
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Properties PROPERTIES = new Properties();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Map<String, Integer> INTEGER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Double> DOUBLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> BOOLEAN_CACHE = new ConcurrentHashMap<>();

    static {
        defaults();
        loadPackagedDefaults();
        load();
    }

    private GameConfiguration() {
    }

    public static int intValue(String key, int fallback) {
        Integer cached = INTEGER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            INTEGER_CACHE.put(key, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static double doubleValue(String key, double fallback) {
        Double cached = DOUBLE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            DOUBLE_CACHE.put(key, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static String stringValue(String key, String fallback) {
        String value = PROPERTIES.getProperty(key, DEFAULTS.getOrDefault(key, fallback));
        return value == null ? fallback : value;
    }

    public static boolean hasValue(String key) {
        return key != null && !key.isBlank()
                && (PROPERTIES.containsKey(key) || DEFAULTS.containsKey(key));
    }

    public static boolean booleanValue(String key, boolean fallback) {
        Boolean cached = BOOLEAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String value = configuredValue(key);
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1")) {
            BOOLEAN_CACHE.put(key, true);
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0")) {
            BOOLEAN_CACHE.put(key, false);
            return false;
        }
        return fallback;
    }

    public static void setValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        String safeValue = value == null ? "" : value.trim();
        String previousValue = PROPERTIES.getProperty(key);
        if (safeValue.equals(previousValue)) {
            return;
        }
        PROPERTIES.setProperty(key, safeValue);
        DEFAULTS.putIfAbsent(key, safeValue);
        INTEGER_CACHE.remove(key);
        DOUBLE_CACHE.remove(key);
        BOOLEAN_CACHE.remove(key);
        REVISION.incrementAndGet();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Runtime config edits should not crash editor/game tools.
        }
    }

    public static void removeValue(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        boolean changed = PROPERTIES.remove(key) != null;
        DEFAULTS.remove(key);
        INTEGER_CACHE.remove(key);
        DOUBLE_CACHE.remove(key);
        BOOLEAN_CACHE.remove(key);
        if (!changed) {
            return;
        }
        REVISION.incrementAndGet();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Runtime config edits should not crash editor/game tools.
        }
    }

    /**
     * Monotonically increases when a runtime setting changes. Renderers can use
     * this instead of reparsing every configuration value every frame.
     */
    public static long revision() {
        return REVISION.get();
    }

    private static void load() {
        PROPERTIES.putAll(DEFAULTS);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.isRegularFile(CONFIG_PATH)) {
                try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                    Properties installed = new Properties();
                    installed.load(inputStream);
                    migrateLegacyEquipmentGates(installed);
                    PROPERTIES.putAll(installed);
                }
            }
            writeDefaultsAndCurrentValues();
        } catch (IOException ignored) {
            // Configuration is a convenience layer; bad disk state should not prevent the game from booting.
        }
    }

    private static void loadPackagedDefaults() {
        try (InputStream inputStream = GameConfiguration.class
                .getClassLoader()
                .getResourceAsStream(PACKAGED_CONFIG_PATH)) {
            if (inputStream == null) {
                return;
            }

            Properties packagedDefaults = new Properties();
            packagedDefaults.load(inputStream);
            for (String key : packagedDefaults.stringPropertyNames()) {
                DEFAULTS.put(key, packagedDefaults.getProperty(key));
            }
        } catch (IOException ignored) {
            // Java defaults remain as the final fallback if the packaged config cannot be read.
        }
    }

    private static void writeDefaultsAndCurrentValues() throws IOException {
        Properties output = new Properties();
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            output.setProperty(entry.getKey(), PROPERTIES.getProperty(entry.getKey(), entry.getValue()));
        }

        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            output.store(outputStream, "Aether editable gameplay configuration");
        }
    }

    private static void put(String key, String value) {
        DEFAULTS.put(key, value);
    }

    private static void migrateLegacyEquipmentGates(Properties installed) {
        for (String legacyKey : installed.stringPropertyNames().stream()
                .filter(key -> key.startsWith("levelGate.equipmentDefense."))
                .toList()) {
            String materialId = legacyKey.substring("levelGate.equipmentDefense.".length())
                    .trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (!materialId.isBlank()) {
                String replacementKey = "levelGate.equipment.defense." + materialId;
                installed.putIfAbsent(replacementKey, installed.getProperty(legacyKey));
            }
            installed.remove(legacyKey);
        }
    }

    private static String configuredValue(String key) {
        String value = PROPERTIES.getProperty(key);
        return value == null ? DEFAULTS.get(key) : value;
    }

    private static void defaults() {
        put("battle.attackInterval.slowestSeconds", "4.2");
        put("battle.attackInterval.fastestSeconds", "0.25");
        put("battle.attackInterval.minimumAgility", "1");
        put("battle.attackInterval.targetMaximumAgility", "99");

        put("battle.hitChance.minimum", "0.05");
        put("battle.hitChance.maximum", "0.95");
        put("battle.roll.minimum", "1");
        put("battle.damage.minimumMaxHit", "1");
        put("battle.damage.rollInclusiveOffset", "1");
        put("battle.damage.physicalStatDivisor", "3");
        put("battle.damage.magicStatDivisor", "3");
        put("battle.healing.statDivisor", "5");
        put("battle.magicDefense.willpowerWeight", "0.70");
        put("battle.magicDefense.skillWeight", "0.20");
        put("battle.magicDefense.armorWeight", "0.10");
        put("battle.physicalDefense.statWeight", "0.35");
        put("battle.physicalDefense.skillWeight", "0.35");
        put("battle.physicalDefense.agilityWeight", "0.15");
        put("battle.rollComparison.divisor", "2.0");
        put("battle.rollComparison.offset", "2.0");

        put("battle.xp.defense.minimum", "1");
        put("battle.xp.defense.perDamage", "3");
        put("battle.xp.attack.perAction", "5");
        put("battle.xp.magicAccuracy.perCast", "5");
        put("battle.xp.strength.minimum", "1");
        put("battle.xp.strength.perDamage", "4");
        put("battle.xp.magicPower.minimum", "1");
        put("battle.xp.magicPower.perDamage", "4");
        put("battle.xp.magicHealing.minimum", "4");
        put("battle.xp.magicHealing.perHp", "4");
        put("battle.enemySkill.intelligenceDivisor", "10.0");
        put("battle.enemySkill.smartDamageIntelligence", "7");
        put("battle.enemySkill.smartDebuffIntelligence", "7");
        put("battle.lowHpWarning.threshold", "0.10");
        put("battle.lowHpWarning.soundPath", "assets/sounds/generated/kurt_sample_2.wav");
        put("battle.playerAutoAttack.soundPath", "");
        put("battle.debug.criticalHpPercent", "0.10");
        put("battle.debug.invulnerableTurns", "1");
        put("battle.debug.damageReduction", "1.0");
        put("battle.skillCooldown.willpowerReductionPerPoint", "0.02");
        put("battle.skillCooldown.minimumMultiplier", "0.50");

        put("difficulty.offenseDivisor", "8.0");
        put("difficulty.survivalDivisor", "10.0");
        put("difficulty.minimumLevel", "1");
        put("difficulty.speedMultiplierCap", "4.0");
        put("battle.summon.maxActorsPerSide", "6");

        put("terrain.heightStep", "0.35");
        put("terrain.maxWalkableDelta", "1");
        put("terrain.cliffTexturePath", "assets/images/building/wall_rock.png");

        put("renderer.prototype.maxDepth", "12");
        put("renderer.prototype.windowWidth", "1280");
        put("renderer.prototype.windowHeight", "720");
        put("renderer.prototype.resizable", "true");
        put("renderer.prototype.wallHeight", "1.0");
        put("renderer.prototype.roofPitchHeight", "0.45");
        put("renderer.prototype.eyeHeight", "0.55");
        put("renderer.prototype.fovDegrees", "70");
        put("renderer.prototype.nearPlane", "0.05");
        put("renderer.prototype.farPlane", "64");
        put("renderer.prototype.input.actionCooldownMs", "150");
        put("renderer.prototype.debug.defaultVisible", "false");
        put("renderer.vsync.enabled", "true");
        put("renderer.frameLimit", "DISPLAY");
        put("renderer.performanceOverlay.visible", "false");
        put("renderer.prototype.mouseLook.enabled", "true");
        put("renderer.prototype.mouseLook.sensitivity", "0.12");
        put("renderer.prototype.mouseLook.maxYawDegrees", "90");
        put("renderer.prototype.mouseLook.maxPitchDegrees", "35");
        put("renderer.prototype.mouseLook.recenterOnRelease", "true");
        put("renderer.prototype.mouseLook.invertX", "true");
        put("renderer.prototype.mouseLook.invertY", "false");
        put("renderer.prototype.viewModel.lightMinimum", "0.12");
        put("renderer.staticModel.preloadExtraDepth", "4");
        put("renderer.staticModel.preloadPerFrame", "8");
        put("renderer.staticModel.loadVisibleImmediately", "true");
        put("renderer.gpuSkinning.enabled", "true");
        put("renderer.terrainCell.uploadBudgetMs", "1.0");
        put("renderer.terrainCell.gpuCache.maxEntries", "192");
        put("renderer.opengl.major", "4");
        put("renderer.opengl.minor", "1");

        put("lighting.enabled", "true");
        put("lighting.mode", "lightmap");
        put("lighting.lightmap.chunkCache.enabled", "true");
        put("lighting.lightmap.chunkCache.maxEntries", "96");
        put("lighting.lightmap.pixelsPerTile", "4");
        put("lighting.dynamic.maxLights", "8");
        put("lighting.dynamic.transitionBridgeRange", "16.0");
        put("lighting.maxLights", "64");
        put("lighting.occlusion.enabled", "true");
        put("lighting.flicker.enabled", "true");
        put("lighting.flicker.updateMs", "90");
        put("lighting.fog.enabled", "true");

        put("movement.animationDurationMs", "160");
        put("rotation.animationDurationMs", "360");
        put("movement.path.maxVisitedTiles", "2048");
        put("movement.ai.maxDecisionsPerStep", "4");
        put("sound.defaultVolume", "0.20");
        put("sound.doorOpen.path", "");
        put("sound.doorClose.path", "");

        put("resource.respawnMs", "300000");
        put("resource.gatheringAttemptIntervalMs", "2500");
        put("resource.attemptsPerExhaustionRoll", "2");
        put("resource.exhaustionChance", "0.50");
        put("resource.maxExhaustionLevel", "2");
        put("fishing.baseSuccessChance", "0.35");
        put("fishing.successChancePerLevel", "0.03");
        put("fishing.maxSuccessChance", "0.85");
        put("fishing.xpReward", "18");
        put("mining.baseSuccessChance", "0.40");
        put("mining.successChancePerLevel", "0.03");
        put("mining.maxSuccessChance", "0.88");
        put("mining.xpReward", "18");
        put("woodcutting.baseSuccessChance", "0.40");
        put("woodcutting.successChancePerLevel", "0.03");
        put("woodcutting.maxSuccessChance", "0.88");
        addGatheringToolDefaults("mining", -0.09, -0.55, -0.90, -14.0, 78.0, -14.0);
        addGatheringToolDefaults("woodcutting", 0.42, -0.46, -0.92, -18.0, 0.0, -24.0);
        addGatheringToolDefaults("fishing", 0.42, -0.46, -0.92, -18.0, 0.0, -24.0);
        put("cooking.baseSuccessChance", "0.45");
        put("cooking.successChancePerLevel", "0.035");
        put("cooking.maxSuccessChance", "0.90");
        put("cooking.xpReward", "20");
        put("smithing.xpPerBar.copper", "12");
        put("smithing.xpPerBar.tin", "12");
        put("smithing.xpPerBar.bronze", "20");
        put("smithing.xpPerBar.silver", "28");
        put("smithing.xpPerBar.iron", "37");
        put("smithing.xpPerBar.steel", "55");

        put("dungeonGenerator.minSize", "17");
        put("dungeonGenerator.maxSize", "29");
        put("dungeonGenerator.merchantChance", "0.10");
        put("dungeonGenerator.roomChance", "0.06");
        put("dungeonGenerator.mediumRoomChance", "0.20");
        put("dungeonGenerator.doorChance", "0.22");
        put("dungeonGenerator.monoTypeChance", "0.30");
        put("dungeonGenerator.targetCarvedCellDivisor", "5");

        put("butchery.baseXp", "12");
        put("grafting.conditionHelpMultiplier", "0.20");
        put("grafting.baseSuccess", "0.25");
        put("grafting.successPerLevel", "0.025");
        put("grafting.minSuccess", "0.05");
        put("grafting.maxSuccess", "0.92");
        put("grafting.xpReward", "16");
        put("grafting.conditionRiskChance", "0.35");
        put("butchery.skillInheritChance", "0.35");
        put("butchery.perfectConditionBaseChance", "0.05");
        put("butchery.perfectConditionLevelBonus", "0.015");
        put("butchery.perfectConditionDifficultyPenalty", "0.002");
        put("butchery.perfectConditionMinChance", "0.02");
        put("butchery.perfectConditionMaxChance", "0.70");
        put("butchery.goodConditionRollCutoff", "0.35");
        put("butchery.wornConditionRollCutoff", "0.68");
        put("butchery.damagedConditionRollCutoff", "0.90");
        put("butchery.difficultyHpDivisor", "5.0");
        put("butchery.difficultyXpDivisor", "10.0");
        put("butchery.weight.attackArmOrHead", "0.35");
        put("butchery.weight.strengthArm", "0.50");
        put("butchery.weight.defenseBody", "0.80");
        put("butchery.weight.defenseHead", "0.20");
        put("butchery.weight.agilityLegs", "0.70");
        put("butchery.weight.agilityArm", "0.15");
        put("butchery.weight.intelligenceHead", "1.0");
        put("butchery.weight.willpowerHead", "0.55");
        put("butchery.weight.willpowerBody", "0.35");
        put("butchery.weight.vitalityBody", "0.80");
        put("butchery.weight.vitalityLegs", "0.20");
    }

    private static void addGatheringToolDefaults(
            String prefix,
            double x,
            double y,
            double z,
            double rotationX,
            double rotationY,
            double rotationZ
    ) {
        put(prefix + ".viewModel.positionX", String.valueOf(x));
        put(prefix + ".viewModel.positionY", String.valueOf(y));
        put(prefix + ".viewModel.positionZ", String.valueOf(z));
        put(prefix + ".viewModel.rotationX", String.valueOf(rotationX));
        put(prefix + ".viewModel.rotationY", String.valueOf(rotationY));
        put(prefix + ".viewModel.rotationZ", String.valueOf(rotationZ));
        put(prefix + ".viewModel.swingAxisX", "0");
        put(prefix + ".viewModel.swingAxisY", "0");
        put(prefix + ".viewModel.swingAxisZ", "1");
        put(prefix + ".viewModel.height", "0.76");
        put(prefix + ".viewModel.windupDegrees", "25");
        put(prefix + ".viewModel.successDegrees", "-55");
        put(prefix + ".viewModel.failureDegrees", "-20");
        put(prefix + ".viewModel.successPenetration", "0.32");
        put(prefix + ".viewModel.failurePenetration", "0.10");
        put(prefix + ".success.soundPath", "");
        put(prefix + ".failure.soundPath", "");
    }
}

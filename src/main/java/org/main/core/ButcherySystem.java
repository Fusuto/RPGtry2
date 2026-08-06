package org.main.core;

import org.main.battle.BattleSkill;
import org.main.content.EnemyButcheryProfile;
import org.main.battle.DifficultyResolver;
import org.main.monsters.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ButcherySystem {
    private ButcherySystem() {
    }

    public enum GraftApproach {
        HAZARDOUS("Hazardous", 0.20, true),
        PERFECT("Perfectly", -0.10, false),
        UNSKILLED("Unskilled", 0.05, true);

        private final String displayName;
        private final double chanceModifier;
        private final boolean risksCondition;

        GraftApproach(String displayName, double chanceModifier, boolean risksCondition) {
            this.displayName = displayName;
            this.chanceModifier = chanceModifier;
            this.risksCondition = risksCondition;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ButcheryMethod {
        BASIC("Basic / Random", 1, null),
        TARGET_LEGS("Target Legs", 10, LimbSlot.LEGS),
        TARGET_LEFT_ARM("Target Left Arm", 20, LimbSlot.LEFT_ARM),
        TARGET_RIGHT_ARM("Target Right Arm", 20, LimbSlot.RIGHT_ARM),
        TARGET_BODY("Target Body", 30, LimbSlot.BODY),
        TARGET_HEAD("Target Head", 40, LimbSlot.HEAD);

        private final String displayName;
        private final int requiredLevel;
        private final LimbSlot targetSlot;

        ButcheryMethod(String displayName, int requiredLevel, LimbSlot targetSlot) {
            this.displayName = displayName;
            this.requiredLevel = requiredLevel;
            this.targetSlot = targetSlot;
        }

        public String displayName() {
            return displayName;
        }

        public int requiredLevel() {
            return requiredLevel;
        }

        public LimbSlot targetSlot() {
            return targetSlot;
        }
    }

    public static List<ButcheryMethod> unlockedMethods(PlayerCharacter player, Monster monster) {
        int level = player == null ? 1 : player.getSkillLevel(CharacterSkill.BUTCHERING);
        if (monster != null
                && monster.getButcheryProfile().type() == EnemyButcheryProfile.Type.LEATHER) {
            return List.of(ButcheryMethod.BASIC);
        }
        List<ButcheryMethod> methods = new ArrayList<>();
        for (ButcheryMethod method : ButcheryMethod.values()) {
            if (level >= method.requiredLevel()) {
                methods.add(method);
            }
        }
        return List.copyOf(methods);
    }

    public static ButcheryMethod compatibleMethod(
            PlayerCharacter player,
            Monster monster,
            ButcheryMethod requested
    ) {
        ButcheryMethod candidate = requested == null ? ButcheryMethod.BASIC : requested;
        return unlockedMethods(player, monster).contains(candidate) ? candidate : ButcheryMethod.BASIC;
    }

    /**
     * Executes the authored corpse yield. The caller owns the corpse and is
     * responsible for enforcing the one-attempt rule.
     */
    public static ButcheryResult butcher(
            GameState gameState,
            PlayerCharacter player,
            Monster monster,
            ButcheryMethod requestedMethod
    ) {
        if (gameState == null || player == null || monster == null) {
            return ButcheryResult.failure("There is nothing usable to butcher.");
        }

        ButcheryMethod method = compatibleMethod(player, monster, requestedMethod);
        int butcheringLevel = Math.max(1, player.getSkillLevel(CharacterSkill.BUTCHERING));
        int enemyLevel = DifficultyResolver.rateMonster(monster).level();
        int difference = butcheringLevel - enemyLevel;
        double successChance = successChanceForDifference(difference);
        player.addSkillExperience(CharacterSkill.BUTCHERING, butcheryBaseXp() + monster.getXpReward());

        if (ThreadLocalRandom.current().nextDouble() > successChance) {
            return ButcheryResult.failure("The butchery fails and no usable material remains.");
        }

        EnemyButcheryProfile profile = monster.getButcheryProfile();
        if (profile.type() == EnemyButcheryProfile.Type.LEATHER) {
            InventorySystem.Item leather = gameState.createItemByNameOrId(profile.leatherItemId());
            if (leather == null) {
                return ButcheryResult.failure("The authored leather yield is unavailable.");
            }
            int quantity = leatherQuantity(difference);
            if (leather.isStackable() && quantity > 1) {
                leather.addQuantity(quantity - 1);
            }
            return ButcheryResult.success(
                    leather,
                    "Recovered " + quantity + " " + leather.getName() + ".");
        }

        LimbSlot slot = method.targetSlot() == null ? randomSlot() : method.targetSlot();
        String limbId = profile.productId(slot);
        InventorySystem.Item authored = gameState.createItemByNameOrId(limbId);
        if (!(authored instanceof LimbItem limb)) {
            return ButcheryResult.failure("The authored " + slot.getDisplayName() + " yield is unavailable.");
        }
        GearDurability condition = rollCondition(butcheringLevel, monsterDifficulty(monster));
        LimbItem recovered = limb.withCondition(condition).withSkills(inheritedSkills(limb));
        return ButcheryResult.success(
                recovered,
                "Recovered " + condition.getDisplayName() + " " + recovered.getName() + ".");
    }

    public static double successChanceForDifference(int difference) {
        if (difference <= -4) {
            return 0.01;
        }
        if (difference <= 0) {
            return (50 + difference * 10) / 100.0;
        }
        if (difference >= 10) {
            return 1.0;
        }
        return (50 + difference * 5) / 100.0;
    }

    public static int leatherQuantity(int difference) {
        int quantity = 1;
        double secondChance = clamp(0.25 + difference * 0.05, 0.05, 0.75);
        if (ThreadLocalRandom.current().nextDouble() > secondChance) {
            return quantity;
        }
        quantity++;
        double thirdChance = clamp(0.05 + difference * 0.05, 0.0, 0.50);
        if (ThreadLocalRandom.current().nextDouble() <= thirdChance) {
            quantity++;
        }
        return quantity;
    }

    private static List<BattleSkill> inheritedSkills(LimbItem limb) {
        if (limb == null || limb.getSkills().isEmpty()) {
            return List.of();
        }
        List<BattleSkill> inherited = new ArrayList<>();
        for (BattleSkill skill : limb.getSkills()) {
            if (ThreadLocalRandom.current().nextDouble() <= skillInheritChance()) {
                inherited.add(skill);
            }
        }
        return List.copyOf(inherited);
    }

    public static GraftResult graft(PlayerCharacter player, LimbItem limb, GraftApproach approach) {
        if (player == null || limb == null) {
            return new GraftResult(false, "There is no limb to graft.");
        }

        GraftApproach selectedApproach = approach == null ? GraftApproach.UNSKILLED : approach;
        int graftingLevel = Math.max(1, player.getSkillLevel(CharacterSkill.GRAFTING));
        double conditionHelp = limb.getCondition().getStatMultiplier() * graftConditionHelpMultiplier();
        double chance = clamp(
                graftBaseSuccess() + graftingLevel * graftSuccessPerLevel() + conditionHelp + selectedApproach.chanceModifier,
                graftMinSuccess(),
                graftMaxSuccess()
        );

        player.addSkillExperience(CharacterSkill.GRAFTING, graftXpReward());

        LimbItem graftedLimb = limb;
        if (selectedApproach.risksCondition && ThreadLocalRandom.current().nextDouble() < graftConditionRiskChance()) {
            graftedLimb = limb.withCondition(degrade(limb.getCondition()));
        }

        if (ThreadLocalRandom.current().nextDouble() > chance) {
            return new GraftResult(false, "The graft fails. The " + limb.getName() + " remains usable.");
        }

        player.equipLimb(graftedLimb);
        return new GraftResult(true, "Grafted " + graftedLimb.getName() + " onto " + graftedLimb.getLimbSlot().getDisplayName() + ".");
    }

    private static LimbSlot randomSlot() {
        LimbSlot[] slots = LimbSlot.values();
        return slots[ThreadLocalRandom.current().nextInt(slots.length)];
    }

    private static GearDurability rollCondition(int butcheringLevel, double monsterDifficulty) {
        double perfectChance = clamp(
                perfectConditionBaseChance()
                        + butcheringLevel * perfectConditionLevelBonus()
                        - monsterDifficulty * perfectConditionDifficultyPenalty(),
                perfectConditionMinChance(),
                perfectConditionMaxChance()
        );
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < perfectChance) {
            return GearDurability.PERFECT;
        }

        if (roll < goodConditionRollCutoff()) {
            return GearDurability.GOOD;
        }

        if (roll < wornConditionRollCutoff()) {
            return GearDurability.WORN;
        }

        if (roll < damagedConditionRollCutoff()) {
            return GearDurability.DAMAGED;
        }

        return GearDurability.BROKEN;
    }

    private static GearDurability degrade(GearDurability condition) {
        return switch (condition) {
            case PERFECT -> GearDurability.GOOD;
            case GOOD -> GearDurability.WORN;
            case WORN -> GearDurability.DAMAGED;
            case DAMAGED, BROKEN -> GearDurability.BROKEN;
        };
    }

    private static double monsterDifficulty(Monster monster) {
        if (monster == null) {
            return 1.0;
        }

        double statTotal = 0.0;
        for (PlayerStat stat : PlayerStat.values()) {
            if (stat == PlayerStat.VITALITY) {
                statTotal += monster.getStat(stat) / difficultyHpDivisor();
            } else {
                statTotal += monster.getStat(stat);
            }
        }
        return statTotal + monster.getXpReward() / difficultyXpDivisor();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int butcheryBaseXp() { return GameConfiguration.intValue("butchery.baseXp", 12); }
    private static double graftConditionHelpMultiplier() { return configuredDouble("grafting.conditionHelpMultiplier", 0.20); }
    private static double graftBaseSuccess() { return configuredDouble("grafting.baseSuccess", 0.25); }
    private static double graftSuccessPerLevel() { return configuredDouble("grafting.successPerLevel", 0.025); }
    private static double graftMinSuccess() { return configuredDouble("grafting.minSuccess", 0.05); }
    private static double graftMaxSuccess() { return configuredDouble("grafting.maxSuccess", 0.92); }
    private static int graftXpReward() { return GameConfiguration.intValue("grafting.xpReward", 16); }
    private static double graftConditionRiskChance() { return configuredDouble("grafting.conditionRiskChance", 0.35); }
    private static double skillInheritChance() { return configuredDouble("butchery.skillInheritChance", 0.35); }
    private static double perfectConditionBaseChance() { return configuredDouble("butchery.perfectConditionBaseChance", 0.05); }
    private static double perfectConditionLevelBonus() { return configuredDouble("butchery.perfectConditionLevelBonus", 0.015); }
    private static double perfectConditionDifficultyPenalty() { return configuredDouble("butchery.perfectConditionDifficultyPenalty", 0.002); }
    private static double perfectConditionMinChance() { return configuredDouble("butchery.perfectConditionMinChance", 0.02); }
    private static double perfectConditionMaxChance() { return configuredDouble("butchery.perfectConditionMaxChance", 0.70); }
    private static double goodConditionRollCutoff() { return configuredDouble("butchery.goodConditionRollCutoff", 0.35); }
    private static double wornConditionRollCutoff() { return configuredDouble("butchery.wornConditionRollCutoff", 0.68); }
    private static double damagedConditionRollCutoff() { return configuredDouble("butchery.damagedConditionRollCutoff", 0.90); }
    private static double difficultyHpDivisor() { return Math.max(1.0, configuredDouble("butchery.difficultyHpDivisor", 5.0)); }
    private static double difficultyXpDivisor() { return Math.max(1.0, configuredDouble("butchery.difficultyXpDivisor", 10.0)); }

    private static double configuredDouble(String key, double fallback) {
        return GameConfiguration.doubleValue(key, fallback);
    }

    public record GraftResult(boolean success, String message) {
    }

    public record ButcheryResult(boolean success, InventorySystem.Item output, String message) {
        public ButcheryResult {
            message = message == null ? "" : message;
        }

        public static ButcheryResult success(InventorySystem.Item output, String message) {
            return new ButcheryResult(output != null, output, message);
        }

        public static ButcheryResult failure(String message) {
            return new ButcheryResult(false, null, message);
        }
    }
}

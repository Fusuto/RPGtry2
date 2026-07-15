package org.main.core;

import org.main.battle.BattleSkill;
import org.main.content.SkillLibrary;
import org.main.monsters.Monster;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class ButcherySystem {
    private static final String DEFAULT_LIMB_ICON = "assets/images/monster/Ancient/Oct-5-2010/player/hand1/misc/head.png";
    private static final double NO_STAT_ALLOCATION = 0.0;

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

    public static List<LimbSlot> unlockedButcheryTargets(PlayerCharacter player) {
        int level = player == null ? 1 : player.getSkillLevel(CharacterSkill.BUTCHERING);
        List<LimbSlot> slots = new ArrayList<>();

        if (level >= targetLegsLevel()) {
            slots.add(LimbSlot.LEGS);
        }

        if (level >= targetArmsLevel()) {
            slots.add(LimbSlot.LEFT_ARM);
            slots.add(LimbSlot.RIGHT_ARM);
        }

        if (level >= targetBodyLevel()) {
            slots.add(LimbSlot.BODY);
        }

        if (level >= targetHeadLevel()) {
            slots.add(LimbSlot.HEAD);
        }

        return slots;
    }

    public static Optional<LimbItem> butcher(PlayerCharacter player, Monster monster, LimbSlot requestedSlot) {
        if (monster == null) {
            return Optional.empty();
        }

        if (player == null) {
            return Optional.empty();
        }

        int butcheringLevel = Math.max(1, player.getSkillLevel(CharacterSkill.BUTCHERING));
        double difficulty = monsterDifficulty(monster);
        double successChance = clamp(
                butcheryBaseSuccess()
                        + butcheringLevel * butcherySuccessPerLevel()
                        - difficulty * butcheryDifficultyPenalty(),
                butcheryMinSuccess(),
                butcheryMaxSuccess()
        );
        player.addSkillExperience(CharacterSkill.BUTCHERING, butcheryBaseXp() + monster.getXpReward());

        if (ThreadLocalRandom.current().nextDouble() > successChance) {
            return Optional.empty();
        }

        LimbSlot slot = requestedSlot == null ? randomSlot() : requestedSlot;
        GearDurability condition = rollCondition(butcheringLevel, difficulty);
        return Optional.of(createCustomLimb(monster, slot, condition));
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

    public static LimbItem recreateLimb(Monster monster, LimbSlot slot, GearDurability condition) {
        return createCustomLimb(monster, slot, condition, false);
    }

    private static LimbItem createCustomLimb(Monster monster, LimbSlot slot, GearDurability condition) {
        return createCustomLimb(monster, slot, condition, true);
    }

    private static LimbItem createCustomLimb(Monster monster, LimbSlot slot, GearDurability condition, boolean rollSkills) {
        EnumMap<PlayerStat, Integer> stats = statsFor(monster.getStatsView(), slot);
        String name = monster.getName() + " " + slot.getDisplayName();

        return new LimbItem(
                name,
                monster.getCustomId(),
                monster.getName(),
                slot,
                stats,
                rollSkills ? skillsFor(monster, slot) : List.of(),
                condition,
                DEFAULT_LIMB_ICON,
                monster.getDescription(),
                monster.getPaperDollSourcePath()
        );
    }

    private static EnumMap<PlayerStat, Integer> statsFor(Map<PlayerStat, Integer> sourceStats, LimbSlot slot) {
        EnumMap<PlayerStat, Integer> stats = emptyStats();

        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, allocatedStat(sourceStats.getOrDefault(stat, 0), stat, slot));
        }

        return stats;
    }


    private static EnumMap<PlayerStat, Integer> emptyStats() {
        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, 0);
        }

        return stats;
    }

    private static int allocatedStat(int total, PlayerStat stat, LimbSlot slot) {
        if (total <= 0) {
            return 0;
        }

        double weight = allocationWeight(stat, slot);
        if (weight <= 0.0) {
            return 0;
        }

        int allocated = (int) Math.floor(total * weight);
        return allocated == 0 ? 1 : allocated;
    }

    private static double allocationWeight(PlayerStat stat, LimbSlot slot) {
        return switch (stat) {
            case ATTACK -> switch (slot) {
                case LEFT_ARM, RIGHT_ARM, HEAD -> configuredDouble("butchery.weight.attackArmOrHead", 0.35);
                default -> NO_STAT_ALLOCATION;
            };
            case STRENGTH -> switch (slot) {
                case LEFT_ARM, RIGHT_ARM -> configuredDouble("butchery.weight.strengthArm", 0.50);
                default -> NO_STAT_ALLOCATION;
            };
            case DEFENSE -> switch (slot) {
                case BODY -> configuredDouble("butchery.weight.defenseBody", 0.80);
                case HEAD -> configuredDouble("butchery.weight.defenseHead", 0.20);
                default -> NO_STAT_ALLOCATION;
            };
            case AGILITY -> switch (slot) {
                case LEGS -> configuredDouble("butchery.weight.agilityLegs", 0.70);
                case LEFT_ARM, RIGHT_ARM -> configuredDouble("butchery.weight.agilityArm", 0.15);
                default -> NO_STAT_ALLOCATION;
            };
            case INTELLIGENCE -> slot == LimbSlot.HEAD ? configuredDouble("butchery.weight.intelligenceHead", 1.0) : NO_STAT_ALLOCATION;
            case WILLPOWER -> switch (slot) {
                case HEAD -> configuredDouble("butchery.weight.willpowerHead", 0.55);
                case BODY -> configuredDouble("butchery.weight.willpowerBody", 0.35);
                default -> NO_STAT_ALLOCATION;
            };
            case VITALITY -> switch (slot) {
                case BODY -> configuredDouble("butchery.weight.vitalityBody", 0.80);
                case LEGS -> configuredDouble("butchery.weight.vitalityLegs", 0.20);
                default -> NO_STAT_ALLOCATION;
            };
        };
    }

    private static List<BattleSkill> skillsFor(Monster monster, LimbSlot slot) {
        if (monster == null || slot != LimbSlot.HEAD) {
            return List.of();
        }

        List<BattleSkill> skills = new ArrayList<>();
        for (SkillLibrary skill : monster.getSkills()) {
            if (ThreadLocalRandom.current().nextDouble() <= skillInheritChance()) {
                skills.add(skill.createSkill());
            }
        }

        return skills;
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

    private static int targetLegsLevel() { return GameConfiguration.intValue("butchery.targetLegsLevel", 10); }
    private static int targetArmsLevel() { return GameConfiguration.intValue("butchery.targetArmsLevel", 20); }
    private static int targetBodyLevel() { return GameConfiguration.intValue("butchery.targetBodyLevel", 30); }
    private static int targetHeadLevel() { return GameConfiguration.intValue("butchery.targetHeadLevel", 40); }
    private static double butcheryBaseSuccess() { return configuredDouble("butchery.baseSuccess", 0.28); }
    private static double butcherySuccessPerLevel() { return configuredDouble("butchery.successPerLevel", 0.025); }
    private static double butcheryDifficultyPenalty() { return configuredDouble("butchery.difficultyPenalty", 0.006); }
    private static double butcheryMinSuccess() { return configuredDouble("butchery.minSuccess", 0.08); }
    private static double butcheryMaxSuccess() { return configuredDouble("butchery.maxSuccess", 0.90); }
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
}

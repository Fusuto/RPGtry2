package org.main.core;

import org.main.battle.BattleSkill;
import org.main.content.EnemySkillLibrary;
import org.main.monsters.MonsterType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class ButcherySystem {
    private static final String DEFAULT_LIMB_ICON = "assets/images/monster/Ancient/Oct-5-2010/player/hand1/misc/head.png";
    private static final int TARGET_LEGS_LEVEL = 10;
    private static final int TARGET_ARMS_LEVEL = 20;
    private static final int TARGET_BODY_LEVEL = 30;
    private static final int TARGET_HEAD_LEVEL = 40;
    private static final double BUTCHERY_BASE_SUCCESS = 0.28;
    private static final double BUTCHERY_SUCCESS_PER_LEVEL = 0.025;
    private static final double BUTCHERY_DIFFICULTY_PENALTY = 0.006;
    private static final double BUTCHERY_MIN_SUCCESS = 0.08;
    private static final double BUTCHERY_MAX_SUCCESS = 0.90;
    private static final int BUTCHERY_BASE_XP = 12;
    private static final double GRAFT_CONDITION_HELP_MULTIPLIER = 0.20;
    private static final double GRAFT_BASE_SUCCESS = 0.25;
    private static final double GRAFT_SUCCESS_PER_LEVEL = 0.025;
    private static final double GRAFT_MIN_SUCCESS = 0.05;
    private static final double GRAFT_MAX_SUCCESS = 0.92;
    private static final int GRAFT_XP_REWARD = 16;
    private static final double GRAFT_CONDITION_RISK_CHANCE = 0.35;
    private static final int MONSTER_HP_TO_VITALITY_DIVISOR = 5;
    private static final int MONSTER_INTELLIGENCE_TO_WILLPOWER_DIVISOR = 2;
    private static final int MONSTER_XP_TO_AGILITY_DIVISOR = 6;
    private static final double SKILL_INHERIT_CHANCE = 0.35;
    private static final double PERFECT_CONDITION_BASE_CHANCE = 0.05;
    private static final double PERFECT_CONDITION_LEVEL_BONUS = 0.015;
    private static final double PERFECT_CONDITION_DIFFICULTY_PENALTY = 0.002;
    private static final double PERFECT_CONDITION_MIN_CHANCE = 0.02;
    private static final double PERFECT_CONDITION_MAX_CHANCE = 0.70;
    private static final double GOOD_CONDITION_ROLL_CUTOFF = 0.35;
    private static final double WORN_CONDITION_ROLL_CUTOFF = 0.68;
    private static final double DAMAGED_CONDITION_ROLL_CUTOFF = 0.90;
    private static final double DIFFICULTY_HP_DIVISOR = 5.0;
    private static final double DIFFICULTY_XP_DIVISOR = 10.0;
    private static final double NO_STAT_ALLOCATION = 0.0;
    private static final double ATTACK_ARM_OR_HEAD_WEIGHT = 0.35;
    private static final double STRENGTH_ARM_WEIGHT = 0.50;
    private static final double DEFENSE_BODY_WEIGHT = 0.80;
    private static final double DEFENSE_HEAD_WEIGHT = 0.20;
    private static final double AGILITY_LEGS_WEIGHT = 0.70;
    private static final double AGILITY_ARM_WEIGHT = 0.15;
    private static final double INTELLIGENCE_HEAD_WEIGHT = 1.0;
    private static final double WILLPOWER_HEAD_WEIGHT = 0.55;
    private static final double WILLPOWER_BODY_WEIGHT = 0.35;
    private static final double VITALITY_BODY_WEIGHT = 0.80;
    private static final double VITALITY_LEGS_WEIGHT = 0.20;

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

        if (level >= TARGET_LEGS_LEVEL) {
            slots.add(LimbSlot.LEGS);
        }

        if (level >= TARGET_ARMS_LEVEL) {
            slots.add(LimbSlot.LEFT_ARM);
            slots.add(LimbSlot.RIGHT_ARM);
        }

        if (level >= TARGET_BODY_LEVEL) {
            slots.add(LimbSlot.BODY);
        }

        if (level >= TARGET_HEAD_LEVEL) {
            slots.add(LimbSlot.HEAD);
        }

        return slots;
    }

    public static Optional<LimbItem> butcher(PlayerCharacter player, MonsterType monsterType, LimbSlot requestedSlot) {
        if (player == null || monsterType == null) {
            return Optional.empty();
        }

        int butcheringLevel = Math.max(1, player.getSkillLevel(CharacterSkill.BUTCHERING));
        double successChance = clamp(
                BUTCHERY_BASE_SUCCESS
                        + butcheringLevel * BUTCHERY_SUCCESS_PER_LEVEL
                        - monsterDifficulty(monsterType) * BUTCHERY_DIFFICULTY_PENALTY,
                BUTCHERY_MIN_SUCCESS,
                BUTCHERY_MAX_SUCCESS
        );
        player.addSkillExperience(CharacterSkill.BUTCHERING, BUTCHERY_BASE_XP + monsterType.getXpReward());

        if (ThreadLocalRandom.current().nextDouble() > successChance) {
            return Optional.empty();
        }

        LimbSlot slot = requestedSlot == null ? randomSlot() : requestedSlot;
        GearDurability condition = rollCondition(butcheringLevel, monsterType);
        return Optional.of(createLimb(monsterType, slot, condition));
    }

    public static GraftResult graft(PlayerCharacter player, LimbItem limb, GraftApproach approach) {
        if (player == null || limb == null) {
            return new GraftResult(false, "There is no limb to graft.");
        }

        GraftApproach selectedApproach = approach == null ? GraftApproach.UNSKILLED : approach;
        int graftingLevel = Math.max(1, player.getSkillLevel(CharacterSkill.GRAFTING));
        double conditionHelp = limb.getCondition().getStatMultiplier() * GRAFT_CONDITION_HELP_MULTIPLIER;
        double chance = clamp(
                GRAFT_BASE_SUCCESS + graftingLevel * GRAFT_SUCCESS_PER_LEVEL + conditionHelp + selectedApproach.chanceModifier,
                GRAFT_MIN_SUCCESS,
                GRAFT_MAX_SUCCESS
        );

        player.addSkillExperience(CharacterSkill.GRAFTING, GRAFT_XP_REWARD);

        LimbItem graftedLimb = limb;
        if (selectedApproach.risksCondition && ThreadLocalRandom.current().nextDouble() < GRAFT_CONDITION_RISK_CHANCE) {
            graftedLimb = limb.withCondition(degrade(limb.getCondition()));
        }

        if (ThreadLocalRandom.current().nextDouble() > chance) {
            return new GraftResult(false, "The graft fails. The " + limb.getName() + " remains usable.");
        }

        player.equipLimb(graftedLimb);
        return new GraftResult(true, "Grafted " + graftedLimb.getName() + " onto " + graftedLimb.getLimbSlot().getDisplayName() + ".");
    }

    public static LimbItem createLimb(MonsterType monsterType, LimbSlot slot, GearDurability condition) {
        return createLimb(monsterType, slot, condition, true);
    }

    public static LimbItem recreateLimb(MonsterType monsterType, LimbSlot slot, GearDurability condition) {
        return createLimb(monsterType, slot, condition, false);
    }

    private static LimbItem createLimb(MonsterType monsterType, LimbSlot slot, GearDurability condition, boolean rollSkills) {
        EnumMap<PlayerStat, Integer> stats = statsFor(monsterType, slot);
        List<BattleSkill> skills = rollSkills ? skillsFor(monsterType, slot) : List.of();
        String name = monsterType.getDisplayName() + " " + slot.getDisplayName();

        return new LimbItem(
                name,
                monsterType,
                slot,
                stats,
                skills,
                condition,
                DEFAULT_LIMB_ICON
        );
    }

    private static EnumMap<PlayerStat, Integer> statsFor(MonsterType monsterType, LimbSlot slot) {
        EnumMap<PlayerStat, Integer> stats = emptyStats();
        EnumMap<PlayerStat, Integer> monsterStats = monsterStats(monsterType);

        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, allocatedStat(monsterStats.getOrDefault(stat, 0), stat, slot));
        }

        return stats;
    }

    private static EnumMap<PlayerStat, Integer> monsterStats(MonsterType monsterType) {
        EnumMap<PlayerStat, Integer> stats = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            stats.put(stat, 0);
        }

        stats.put(PlayerStat.VITALITY, Math.max(1, monsterType.getMaxHp() / MONSTER_HP_TO_VITALITY_DIVISOR));
        stats.put(PlayerStat.ATTACK, Math.max(1, monsterType.getAttack()));
        stats.put(PlayerStat.STRENGTH, Math.max(1, monsterType.getAttack()));
        stats.put(PlayerStat.DEFENSE, Math.max(0, monsterType.getDefense()));
        stats.put(PlayerStat.INTELLIGENCE, Math.max(0, monsterType.getIntelligence()));
        stats.put(PlayerStat.WILLPOWER, Math.max(1, monsterType.getDefense() + monsterType.getIntelligence() / MONSTER_INTELLIGENCE_TO_WILLPOWER_DIVISOR));
        stats.put(PlayerStat.AGILITY, Math.max(1, monsterType.getXpReward() / MONSTER_XP_TO_AGILITY_DIVISOR));
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
                case LEFT_ARM, RIGHT_ARM, HEAD -> ATTACK_ARM_OR_HEAD_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
            case STRENGTH -> switch (slot) {
                case LEFT_ARM, RIGHT_ARM -> STRENGTH_ARM_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
            case DEFENSE -> switch (slot) {
                case BODY -> DEFENSE_BODY_WEIGHT;
                case HEAD -> DEFENSE_HEAD_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
            case AGILITY -> switch (slot) {
                case LEGS -> AGILITY_LEGS_WEIGHT;
                case LEFT_ARM, RIGHT_ARM -> AGILITY_ARM_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
            case INTELLIGENCE -> slot == LimbSlot.HEAD ? INTELLIGENCE_HEAD_WEIGHT : NO_STAT_ALLOCATION;
            case WILLPOWER -> switch (slot) {
                case HEAD -> WILLPOWER_HEAD_WEIGHT;
                case BODY -> WILLPOWER_BODY_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
            case VITALITY -> switch (slot) {
                case BODY -> VITALITY_BODY_WEIGHT;
                case LEGS -> VITALITY_LEGS_WEIGHT;
                default -> NO_STAT_ALLOCATION;
            };
        };
    }

    private static List<BattleSkill> skillsFor(MonsterType monsterType, LimbSlot slot) {
        if (monsterType == null || slot != LimbSlot.HEAD) {
            return List.of();
        }

        List<BattleSkill> skills = new ArrayList<>();
        for (EnemySkillLibrary skill : monsterType.getSkills()) {
            if (ThreadLocalRandom.current().nextDouble() <= SKILL_INHERIT_CHANCE) {
                skills.add(skill.createSkill());
            }
        }

        return skills;
    }

    private static LimbSlot randomSlot() {
        LimbSlot[] slots = LimbSlot.values();
        return slots[ThreadLocalRandom.current().nextInt(slots.length)];
    }

    private static GearDurability rollCondition(int butcheringLevel, MonsterType monsterType) {
        double perfectChance = clamp(
                PERFECT_CONDITION_BASE_CHANCE
                        + butcheringLevel * PERFECT_CONDITION_LEVEL_BONUS
                        - monsterDifficulty(monsterType) * PERFECT_CONDITION_DIFFICULTY_PENALTY,
                PERFECT_CONDITION_MIN_CHANCE,
                PERFECT_CONDITION_MAX_CHANCE
        );
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < perfectChance) {
            return GearDurability.PERFECT;
        }

        if (roll < GOOD_CONDITION_ROLL_CUTOFF) {
            return GearDurability.GOOD;
        }

        if (roll < WORN_CONDITION_ROLL_CUTOFF) {
            return GearDurability.WORN;
        }

        if (roll < DAMAGED_CONDITION_ROLL_CUTOFF) {
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

    private static double monsterDifficulty(MonsterType monsterType) {
        return monsterType.getMaxHp() / DIFFICULTY_HP_DIVISOR
                + monsterType.getAttack()
                + monsterType.getDefense()
                + monsterType.getXpReward() / DIFFICULTY_XP_DIVISOR;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record GraftResult(boolean success, String message) {
    }
}

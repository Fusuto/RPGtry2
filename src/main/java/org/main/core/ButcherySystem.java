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

        if (level >= 10) {
            slots.add(LimbSlot.LEGS);
        }

        if (level >= 20) {
            slots.add(LimbSlot.LEFT_ARM);
            slots.add(LimbSlot.RIGHT_ARM);
        }

        if (level >= 30) {
            slots.add(LimbSlot.BODY);
        }

        if (level >= 40) {
            slots.add(LimbSlot.HEAD);
        }

        return slots;
    }

    public static Optional<LimbItem> butcher(PlayerCharacter player, MonsterType monsterType, LimbSlot requestedSlot) {
        if (player == null || monsterType == null) {
            return Optional.empty();
        }

        int butcheringLevel = Math.max(1, player.getSkillLevel(CharacterSkill.BUTCHERING));
        double successChance = clamp(0.28 + butcheringLevel * 0.025 - monsterDifficulty(monsterType) * 0.006, 0.08, 0.90);
        player.addSkillExperience(CharacterSkill.BUTCHERING, 12 + monsterType.getXpReward());

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
        double conditionHelp = limb.getCondition().getStatMultiplier() * 0.20;
        double chance = clamp(0.25 + graftingLevel * 0.025 + conditionHelp + selectedApproach.chanceModifier, 0.05, 0.92);

        player.addSkillExperience(CharacterSkill.GRAFTING, 16);

        LimbItem graftedLimb = limb;
        if (selectedApproach.risksCondition && ThreadLocalRandom.current().nextDouble() < 0.35) {
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

        stats.put(PlayerStat.VITALITY, Math.max(1, monsterType.getMaxHp() / 5));
        stats.put(PlayerStat.STRENGTH, Math.max(1, monsterType.getAttack()));
        stats.put(PlayerStat.DEFENSE, Math.max(0, monsterType.getDefense()));
        stats.put(PlayerStat.INTELLIGENCE, Math.max(0, monsterType.getIntelligence()));
        stats.put(PlayerStat.AGILITY, Math.max(1, monsterType.getXpReward() / 6));
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
            case STRENGTH -> switch (slot) {
                case LEFT_ARM, RIGHT_ARM -> 0.50;
                default -> 0.0;
            };
            case DEFENSE -> switch (slot) {
                case BODY -> 0.80;
                case HEAD -> 0.20;
                default -> 0.0;
            };
            case AGILITY -> switch (slot) {
                case LEGS -> 0.70;
                case LEFT_ARM, RIGHT_ARM -> 0.15;
                default -> 0.0;
            };
            case INTELLIGENCE -> slot == LimbSlot.HEAD ? 1.0 : 0.0;
            case VITALITY -> switch (slot) {
                case BODY -> 0.80;
                case LEGS -> 0.20;
                default -> 0.0;
            };
        };
    }

    private static List<BattleSkill> skillsFor(MonsterType monsterType, LimbSlot slot) {
        if (monsterType == null || slot != LimbSlot.HEAD) {
            return List.of();
        }

        List<BattleSkill> skills = new ArrayList<>();
        for (EnemySkillLibrary skill : monsterType.getSkills()) {
            if (ThreadLocalRandom.current().nextDouble() <= 0.35) {
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
        double perfectChance = clamp(0.05 + butcheringLevel * 0.015 - monsterDifficulty(monsterType) * 0.002, 0.02, 0.70);
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < perfectChance) {
            return GearDurability.PERFECT;
        }

        if (roll < 0.35) {
            return GearDurability.GOOD;
        }

        if (roll < 0.68) {
            return GearDurability.WORN;
        }

        if (roll < 0.90) {
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
        return monsterType.getMaxHp() / 5.0
                + monsterType.getAttack()
                + monsterType.getDefense()
                + monsterType.getXpReward() / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record GraftResult(boolean success, String message) {
    }
}

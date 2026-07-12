package org.main.battle;

import org.main.core.CharacterSkill;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatResolver {
    private static final double MIN_HIT_CHANCE = 0.05;
    private static final double MAX_HIT_CHANCE = 0.95;
    private static final int MIN_ROLL_VALUE = 1;
    private static final int MIN_DAMAGE_MAX_HIT = 1;
    private static final int DAMAGE_ROLL_INCLUSIVE_OFFSET = 1;
    private static final int PHYSICAL_STAT_DAMAGE_DIVISOR = 3;
    private static final int MAGIC_STAT_DAMAGE_DIVISOR = 3;
    private static final int HEALING_STAT_DIVISOR = 5;
    private static final double MAGIC_DEFENSE_WILLPOWER_WEIGHT = 0.70;
    private static final double MAGIC_DEFENSE_SKILL_WEIGHT = 0.20;
    private static final double MAGIC_DEFENSE_ARMOR_WEIGHT = 0.10;
    private static final double PHYSICAL_DEFENSE_STAT_WEIGHT = 0.35;
    private static final double PHYSICAL_DEFENSE_SKILL_WEIGHT = 0.35;
    private static final double PHYSICAL_DEFENSE_AGILITY_WEIGHT = 0.15;
    private static final double ROLL_COMPARISON_DIVISOR = 2.0;
    private static final double ROLL_COMPARISON_OFFSET = 2.0;

    private CombatResolver() {
    }

    public static CombatResult resolveMelee(BattleActor attacker, BattleActor defender) {
        return resolvePhysical(attacker, defender, 0, "hits");
    }

    public static CombatResult resolvePhysicalSkill(BattleActor attacker, BattleActor defender, BattleSkill skill) {
        int skillBonus = skill == null ? 0 : skill.getDamage();
        return resolvePhysical(attacker, defender, skillBonus, "hits");
    }

    public static CombatResult resolveSpell(BattleActor caster, BattleActor defender, BattleSkill skill) {
        int accuracyRoll = Math.max(MIN_ROLL_VALUE,
                caster.getIntelligence()
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY)
        );
        int defenseRoll = Math.max(MIN_ROLL_VALUE, (int) Math.round(
                casterSafeWillpower(defender) * MAGIC_DEFENSE_WILLPOWER_WEIGHT
                        + defender.getCombatSkillLevel(CharacterSkill.DEFENSE) * MAGIC_DEFENSE_SKILL_WEIGHT
                        + defender.getArmorBonus() * MAGIC_DEFENSE_ARMOR_WEIGHT
        ));
        double hitChance = hitChance(accuracyRoll, defenseRoll);
        boolean hit = roll(hitChance);
        int maxHit = Math.max(0,
                (skill == null ? 0 : skill.getDamage())
                        + caster.getWillpowerStat() / MAGIC_STAT_DAMAGE_DIVISOR
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_POWER) / MAGIC_STAT_DAMAGE_DIVISOR
        );
        int damage = hit ? randomDamage(maxHit) : 0;

        return new CombatResult(hit, damage, hitChance, maxHit, hit ? "casts for " + damage : "misses");
    }

    public static int resolveHealingAmount(BattleActor caster, BattleSkill skill) {
        if (caster == null || skill == null) {
            return 0;
        }

        return Math.max(0,
                skill.getDamage()
                        + caster.getWillpowerStat() / HEALING_STAT_DIVISOR
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_POWER) / HEALING_STAT_DIVISOR
        );
    }

    private static CombatResult resolvePhysical(BattleActor attacker, BattleActor defender, int maxHitBonus, String verb) {
        int accuracyRoll = Math.max(MIN_ROLL_VALUE,
                attacker.getAttackStat()
                        + attacker.getCombatSkillLevel(CharacterSkill.ATTACK)
                        + attacker.getWeaponBonus()
        );
        int defenseRoll = physicalDefenseRoll(defender);
        double hitChance = hitChance(accuracyRoll, defenseRoll);
        boolean hit = roll(hitChance);
        int maxHit = Math.max(MIN_DAMAGE_MAX_HIT,
                MIN_DAMAGE_MAX_HIT
                        + (attacker.getStrengthStat() + attacker.getCombatSkillLevel(CharacterSkill.STRENGTH)) / PHYSICAL_STAT_DAMAGE_DIVISOR
                        + attacker.getWeaponBonus()
                        + Math.max(0, maxHitBonus)
        );
        int damage = hit ? randomDamage(maxHit) : 0;

        return new CombatResult(hit, damage, hitChance, maxHit, hit ? verb + " for " + damage : "misses");
    }

    private static int physicalDefenseRoll(BattleActor defender) {
        return Math.max(MIN_ROLL_VALUE, (int) Math.round(
                defender.getDefenseStat() * PHYSICAL_DEFENSE_STAT_WEIGHT
                        + defender.getCombatSkillLevel(CharacterSkill.DEFENSE) * PHYSICAL_DEFENSE_SKILL_WEIGHT
                        + defender.getArmorBonus()
                        + defender.getAgilityStat() * PHYSICAL_DEFENSE_AGILITY_WEIGHT
        ));
    }

    private static int casterSafeWillpower(BattleActor actor) {
        return actor == null ? MIN_ROLL_VALUE : actor.getWillpowerStat();
    }

    private static double hitChance(int attackRoll, int defenseRoll) {
        double chance;

        if (attackRoll > defenseRoll) {
            chance = 1.0 - ((defenseRoll + ROLL_COMPARISON_OFFSET)
                    / (ROLL_COMPARISON_DIVISOR * attackRoll + ROLL_COMPARISON_OFFSET));
        } else {
            chance = attackRoll / (ROLL_COMPARISON_DIVISOR * defenseRoll + ROLL_COMPARISON_OFFSET);
        }

        return Math.max(MIN_HIT_CHANCE, Math.min(MAX_HIT_CHANCE, chance));
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static int randomDamage(int maxHit) {
        if (maxHit <= 0) {
            return 0;
        }

        return ThreadLocalRandom.current().nextInt(maxHit + DAMAGE_ROLL_INCLUSIVE_OFFSET);
    }

    public record CombatResult(boolean hit, int damage, double hitChance, int maxHit, String text) {
    }
}

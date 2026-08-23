package org.main.battle;

import org.main.core.CharacterSkill;
import org.main.core.GameConfiguration;
import org.main.core.CombatElement;

public final class CombatResolver {
    private CombatResolver() {
    }

    public static CombatResult resolveMelee(BattleActor attacker, BattleActor defender) {
        return resolvePhysical(attacker, defender, 0, "hits");
    }

    public static CombatResult resolvePhysicalSkill(BattleActor attacker, BattleActor defender, BattleSkill skill) {
        int skillBonus = skill == null ? 0 : skill.getPrimaryPotency();
        return resolvePhysical(attacker, defender, skillBonus, "hits");
    }

    public static CombatResult resolveSpell(BattleActor caster, BattleActor defender, BattleSkill skill) {
        int accuracyRoll = Math.max(minRollValue(),
                caster.getIntelligence()
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY)
        );
        int defenseRoll = Math.max(minRollValue(), (int) Math.round(
                casterSafeWillpower(defender) * magicDefenseWillpowerWeight()
                        + defender.getCombatSkillLevel(CharacterSkill.DEFENSE) * magicDefenseSkillWeight()
                        + defender.getArmorBonus() * magicDefenseArmorWeight()
        ));
        double hitChance = hitChance(accuracyRoll, defenseRoll);
        boolean hit = roll(hitChance);
        int maxHit = Math.max(0,
                (skill == null ? 0 : skill.getPrimaryPotency())
                        + caster.getWillpowerStat() / magicStatDamageDivisor()
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_POWER) / magicStatDamageDivisor()
        );
        CombatElement element = skill == null ? CombatElement.NEUTRAL : skill.getElement();
        int damage = hit ? applyElementalSpellModifiers(caster, defender, element, randomDamage(maxHit)) : 0;

        return new CombatResult(hit, damage, hitChance, maxHit,
                hit ? spellResultText(defender, element, damage) : "misses", element);
    }

    public static int resolveHealingAmount(BattleActor caster, BattleSkill skill) {
        if (caster == null || skill == null) {
            return 0;
        }

        return Math.max(0,
                        skill.getPrimaryPotency()
                        + caster.getWillpowerStat() / healingStatDivisor()
                        + caster.getCombatSkillLevel(CharacterSkill.MAGIC_POWER) / healingStatDivisor()
        );
    }

    private static CombatResult resolvePhysical(BattleActor attacker, BattleActor defender, int maxHitBonus, String verb) {
        int accuracyRoll = Math.max(minRollValue(),
                attacker.getAttackStat()
                        + attacker.getCombatSkillLevel(CharacterSkill.ATTACK)
                        + attacker.getWeaponAccuracyBonus()
        );
        int defenseRoll = physicalDefenseRoll(defender);
        double hitChance = hitChance(accuracyRoll, defenseRoll);
        boolean hit = roll(hitChance);
        int maxHit = Math.max(minDamageMaxHit(),
                minDamageMaxHit()
                        + (attacker.getStrengthStat() + attacker.getCombatSkillLevel(CharacterSkill.STRENGTH)) / physicalStatDamageDivisor()
                        + attacker.getWeaponPowerBonus()
                        + Math.max(0, maxHitBonus)
        );
        int damage = hit ? applyOutgoingMultiplier(attacker, randomDamage(maxHit)) : 0;

        return new CombatResult(hit, damage, hitChance, maxHit,
                hit ? verb + " for " + damage : "misses", CombatElement.NEUTRAL);
    }

    private static int physicalDefenseRoll(BattleActor defender) {
        return Math.max(minRollValue(), (int) Math.round(
                defender.getDefenseStat() * physicalDefenseStatWeight()
                        + defender.getCombatSkillLevel(CharacterSkill.DEFENSE) * physicalDefenseSkillWeight()
                        + defender.getArmorBonus()
                        + defender.getAgilityStat() * physicalDefenseAgilityWeight()
        ));
    }

    private static int casterSafeWillpower(BattleActor actor) {
        return actor == null ? minRollValue() : actor.getWillpowerStat();
    }

    private static double hitChance(int attackRoll, int defenseRoll) {
        double chance;

        if (attackRoll > defenseRoll) {
            chance = 1.0 - ((defenseRoll + rollComparisonOffset())
                    / (rollComparisonDivisor() * attackRoll + rollComparisonOffset()));
        } else {
            chance = attackRoll / (rollComparisonDivisor() * defenseRoll + rollComparisonOffset());
        }

        return Math.max(minHitChance(), Math.min(maxHitChance(), chance));
    }

    private static boolean roll(double chance) {
        return BattleRandom.nextDouble() < chance;
    }

    private static int randomDamage(int maxHit) {
        if (maxHit <= 0) {
            return 0;
        }

        return BattleRandom.nextInt(maxHit + damageRollInclusiveOffset());
    }

    private static int applyOutgoingMultiplier(BattleActor actor, int damage) {
        return Math.max(0, (int) Math.round(damage
                * (actor == null ? 1.0 : actor.outgoingDamageMultiplier())));
    }

    static int applyElementalSpellModifiers(
            BattleActor caster, BattleActor defender, CombatElement element, int damage) {
        double weaponMultiplier = caster == null ? 1.0 : caster.matchingSpellDamageMultiplier(element);
        double targetMultiplier = defender == null ? 1.0 : defender.getElementalDamageMultiplier(element);
        int elementalDamage = Math.max(0, (int) Math.round(damage * weaponMultiplier * targetMultiplier));
        return applyOutgoingMultiplier(caster, elementalDamage);
    }

    private static String spellResultText(BattleActor defender, CombatElement element, int damage) {
        double multiplier = defender == null ? 1.0 : defender.getElementalDamageMultiplier(element);
        String response = multiplier > 1.000001 ? " (Weak)" : multiplier < 0.999999 ? " (Resisted)" : "";
        return "takes " + damage + " " + element.getDisplayName().toLowerCase() + " spell damage" + response;
    }

    private static double minHitChance() { return GameConfiguration.doubleValue("battle.hitChance.minimum", 0.05); }
    private static double maxHitChance() { return GameConfiguration.doubleValue("battle.hitChance.maximum", 0.95); }
    private static int minRollValue() { return GameConfiguration.intValue("battle.roll.minimum", 1); }
    private static int minDamageMaxHit() { return GameConfiguration.intValue("battle.damage.minimumMaxHit", 1); }
    private static int damageRollInclusiveOffset() { return GameConfiguration.intValue("battle.damage.rollInclusiveOffset", 1); }
    private static int physicalStatDamageDivisor() { return Math.max(1, GameConfiguration.intValue("battle.damage.physicalStatDivisor", 3)); }
    private static int magicStatDamageDivisor() { return Math.max(1, GameConfiguration.intValue("battle.damage.magicStatDivisor", 3)); }
    private static int healingStatDivisor() { return Math.max(1, GameConfiguration.intValue("battle.healing.statDivisor", 5)); }
    private static double magicDefenseWillpowerWeight() { return GameConfiguration.doubleValue("battle.magicDefense.willpowerWeight", 0.70); }
    private static double magicDefenseSkillWeight() { return GameConfiguration.doubleValue("battle.magicDefense.skillWeight", 0.20); }
    private static double magicDefenseArmorWeight() { return GameConfiguration.doubleValue("battle.magicDefense.armorWeight", 0.10); }
    private static double physicalDefenseStatWeight() { return GameConfiguration.doubleValue("battle.physicalDefense.statWeight", 0.35); }
    private static double physicalDefenseSkillWeight() { return GameConfiguration.doubleValue("battle.physicalDefense.skillWeight", 0.35); }
    private static double physicalDefenseAgilityWeight() { return GameConfiguration.doubleValue("battle.physicalDefense.agilityWeight", 0.15); }
    private static double rollComparisonDivisor() { return GameConfiguration.doubleValue("battle.rollComparison.divisor", 2.0); }
    private static double rollComparisonOffset() { return GameConfiguration.doubleValue("battle.rollComparison.offset", 2.0); }

    public record CombatResult(
            boolean hit,
            int damage,
            double hitChance,
            int maxHit,
            String text,
            CombatElement element
    ) {
        public CombatResult(boolean hit, int damage, double hitChance, int maxHit, String text) {
            this(hit, damage, hitChance, maxHit, text, CombatElement.NEUTRAL);
        }

        public CombatResult {
            element = element == null ? CombatElement.NEUTRAL : element;
        }
    }
}

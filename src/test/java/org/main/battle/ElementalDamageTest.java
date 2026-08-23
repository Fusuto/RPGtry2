package org.main.battle;

import org.junit.jupiter.api.Test;
import org.main.core.CombatElement;
import org.main.core.Library;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElementalDamageTest {
    @Test
    void matchingWeaponBonusThenCreatureMultiplierApplyExactly() {
        BattleActor caster = new BattleActor("Mage", 20, 20, null, Library.EntityType.ALLY);
        BattleActor target = new BattleActor("Elemental", 20, 20, null, Library.EntityType.ENEMY);
        caster.setWeaponElement(CombatElement.FIRE);
        caster.setMatchingElementSpellDamageBonus(0.10);
        target.setElementalDamageMultiplier(CombatElement.FIRE, 1.50);

        assertEquals(165, CombatResolver.applyElementalSpellModifiers(
                caster, target, CombatElement.FIRE, 100));
        assertEquals(100, CombatResolver.applyElementalSpellModifiers(
                caster, target, CombatElement.FROST, 100));
        assertEquals(100, CombatResolver.applyElementalSpellModifiers(
                caster, target, CombatElement.NEUTRAL, 100));
    }

    @Test
    void elementalMultiplierIsClampedToAuthoredRange() {
        BattleActor target = new BattleActor("Target", 20, 20, null, Library.EntityType.ENEMY);
        target.setElementalDamageMultiplier(CombatElement.EARTH, 99.0);
        assertEquals(5.0, target.getElementalDamageMultiplier(CombatElement.EARTH));
        target.setElementalDamageMultiplier(CombatElement.EARTH, -2.0);
        assertEquals(0.0, target.getElementalDamageMultiplier(CombatElement.EARTH));
    }
}

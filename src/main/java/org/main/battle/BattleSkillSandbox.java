package org.main.battle;

import org.main.content.SkillDefinition;
import org.main.core.CharacterSkill;
import org.main.core.Library;

import java.util.List;

public final class BattleSkillSandbox {
    private BattleSkillSandbox() {
    }

    public static BattleEncounter.SandboxResult run(SkillDefinition definition, long seed) {
        BattleActor caster = actor("Sandbox Caster", 100, Library.EntityType.ALLY);
        BattleActor ally = actor("Sandbox Ally", 100, Library.EntityType.ALLY);
        ally.setCurrentHp(55);
        BattleActor enemy = actor("Sandbox Target", 100, Library.EntityType.ENEMY);
        BattleEncounter encounter = new BattleEncounter(List.of(caster, ally), List.of(enemy));
        BattleSkill skill = BattleSkill.fromDefinition(definition);
        BattleActor target = definition.targetTeam() == Library.EntityType.ALLY ? ally : enemy;
        return encounter.executeSkillImmediatelyForSandbox(caster, skill, List.of(target), seed);
    }

    private static BattleActor actor(String name, int hp, Library.EntityType type) {
        BattleActor actor = new BattleActor(name, hp, hp, null, type, 10, 5);
        actor.setAttackStat(10);
        actor.setStrengthStat(10);
        actor.setDefenseStat(5);
        actor.setAgilityStat(10);
        actor.setIntelligence(10);
        actor.setWillpowerStat(10);
        actor.setCombatSkillLevel(CharacterSkill.ATTACK, 10);
        actor.setCombatSkillLevel(CharacterSkill.STRENGTH, 10);
        actor.setCombatSkillLevel(CharacterSkill.DEFENSE, 10);
        actor.setCombatSkillLevel(CharacterSkill.MAGIC_ACCURACY, 10);
        actor.setCombatSkillLevel(CharacterSkill.MAGIC_POWER, 10);
        return actor;
    }
}

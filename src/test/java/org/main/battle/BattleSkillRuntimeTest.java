package org.main.battle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.main.content.BattleContentCatalog;
import org.main.content.SkillDefinition;
import org.main.content.SkillEffectDefinition;
import org.main.content.StatusDefinition;
import org.main.core.Library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleSkillRuntimeTest {
    @AfterEach
    void restoreCatalog() {
        BattleContentCatalog.installForTests(BattleContentCatalog.defaults());
    }

    @Test
    void seededSandboxUsesRepeatableRealBattleExecution() {
        SkillDefinition fireball = BattleContentCatalog.defaults().skills().get("fireball");

        BattleEncounter.SandboxResult first = BattleSkillSandbox.run(fireball, 42L);
        BattleEncounter.SandboxResult second = BattleSkillSandbox.run(fireball, 42L);

        assertEquals(first, second);
        assertTrue(first.targetHpAfter().getFirst() <= first.targetHpBefore().getFirst());
    }

    @Test
    void compositeDamageAndOnHitStatusExecutesInOrder() {
        BattleContentCatalog.Snapshot defaults = BattleContentCatalog.defaults();
        StatusDefinition poison = new StatusDefinition(
                "poison", "Poison", "Loses health each turn.", "",
                StatusDefinition.Polarity.HARMFUL, "periodic_health", 3,
                StatusDefinition.StackingPolicy.STACK, 3, Map.of("magnitude", "-2"));
        SkillDefinition venom = new SkillDefinition(
                "venom_strike", "Venom Strike", "",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.MAGIC, "", "SPELL", 3, true,
                List.of(
                        effect("damage", SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                                SkillEffectDefinition.ActivationCondition.ALWAYS, 1,
                                Map.of("potency", "1000")),
                        effect("apply_status", SkillEffectDefinition.RecipientScope.RESOLVED_TARGETS,
                                SkillEffectDefinition.ActivationCondition.PREVIOUS_EFFECT_HIT, 1,
                                Map.of("statusId", "poison", "duration", "3", "magnitude", "-2"))));
        LinkedHashMap<String, StatusDefinition> statuses = new LinkedHashMap<>(defaults.statuses());
        statuses.put(poison.id(), poison);
        LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>(defaults.skills());
        skills.put(venom.id(), venom);
        BattleContentCatalog.installForTests(new BattleContentCatalog.Snapshot(
                1, skills, statuses, defaults.defaultPlayerSkillIds(),
                defaults.universalPlayerSkillIds(), defaults.debugPlayerSkillIds()));

        BattleEncounter.SandboxResult result = BattleSkillSandbox.run(venom, 4096L);

        assertTrue(result.statuses().stream().anyMatch(value -> value.startsWith("Poison")),
                result.toString());
    }

    @Test
    void refreshReplaceAndBoundedStackPoliciesAreEnforced() {
        BattleContentCatalog.Snapshot defaults = BattleContentCatalog.defaults();
        List<StatusDefinition> additions = List.of(
                status("refresh_test", StatusDefinition.StackingPolicy.REFRESH, 1),
                status("replace_test", StatusDefinition.StackingPolicy.REPLACE, 1),
                status("stack_test", StatusDefinition.StackingPolicy.STACK, 2));
        LinkedHashMap<String, StatusDefinition> statuses = new LinkedHashMap<>(defaults.statuses());
        additions.forEach(value -> statuses.put(value.id(), value));
        BattleContentCatalog.installForTests(new BattleContentCatalog.Snapshot(
                1, defaults.skills(), statuses, defaults.defaultPlayerSkillIds(),
                defaults.universalPlayerSkillIds(), defaults.debugPlayerSkillIds()));
        BattleActor actor = new BattleActor("Target", 100, 100, null, Library.EntityType.ENEMY);

        actor.applyStatus("refresh_test", 2, -2);
        actor.applyStatus("refresh_test", 4, -1);
        actor.applyStatus("replace_test", 4, -5);
        actor.applyStatus("replace_test", 1, -1);
        actor.applyStatus("stack_test", 3, -1);
        actor.applyStatus("stack_test", 3, -2);
        actor.applyStatus("stack_test", 3, -3);

        List<BattleStatus> refresh = actor.getStatuses().stream()
                .filter(value -> value.getStatusId().equals("refresh_test")).toList();
        List<BattleStatus> replace = actor.getStatuses().stream()
                .filter(value -> value.getStatusId().equals("replace_test")).toList();
        List<BattleStatus> stack = actor.getStatuses().stream()
                .filter(value -> value.getStatusId().equals("stack_test")).toList();
        assertEquals(1, refresh.size());
        assertEquals(4, refresh.getFirst().getRemainingTurns());
        assertEquals(-2, refresh.getFirst().getPotency());
        assertEquals(1, replace.getFirst().getRemainingTurns());
        assertEquals(-1, replace.getFirst().getPotency());
        assertEquals(2, stack.size());
        assertTrue(stack.stream().anyMatch(value -> value.getPotency() == -3));
    }

    @Test
    void cleanseRemovesOnlyRequestedPolarity() {
        BattleActor actor = new BattleActor("Target", 100, 100, null, Library.EntityType.ALLY);
        actor.applyStatus("stun", 2);
        actor.applyStatus("guard", 2, 50);

        int removed = actor.removeStatuses("", StatusDefinition.Polarity.HARMFUL, 10);

        assertEquals(1, removed);
        assertFalse(actor.hasStatus("stun"));
        assertTrue(actor.hasStatus("guard"));
    }

    private static StatusDefinition status(
            String id, StatusDefinition.StackingPolicy policy, int maxStacks) {
        return new StatusDefinition(
                id, id, "", "", StatusDefinition.Polarity.HARMFUL,
                "stat_modifier", 2, policy, maxStacks,
                Map.of("stat", "AGILITY", "magnitude", "-1"));
    }

    private static SkillEffectDefinition effect(
            String kind,
            SkillEffectDefinition.RecipientScope scope,
            SkillEffectDefinition.ActivationCondition condition,
            double chance,
            Map<String, String> parameters) {
        return new SkillEffectDefinition(kind, scope, condition, chance, parameters);
    }
}

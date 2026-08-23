package org.main.content;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.main.content.SkillEffectDefinition.ActivationCondition;
import org.main.content.SkillEffectDefinition.RecipientScope;
import org.main.core.Library;
import org.main.core.CombatElement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleContentCatalogTest {
    @AfterEach
    void restoreCatalog() {
        BattleContentCatalog.installForTests(BattleContentCatalog.defaults());
    }

    @Test
    void defaultCatalogIsCompleteAndValid() {
        BattleContentCatalog.Snapshot snapshot = BattleContentCatalog.defaults();

        assertEquals(12, snapshot.skills().size());
        assertEquals(3, snapshot.statuses().size());
        assertEquals(List.of("wait", "fireball", "piercing_line", "crush_column", "heal"),
                snapshot.defaultPlayerSkillIds());
        assertTrue(BattleContentCatalog.validate(snapshot).isEmpty());
        assertEquals(150.0, snapshot.skills().get("war_cry").cooldownSeconds());
    }

    @Test
    void bundledVersionedCatalogLoadsAndValidates() throws IOException {
        BattleContentCatalog.Snapshot snapshot = BattleContentCatalog.reload();

        assertEquals(14, snapshot.skills().size());
        assertEquals(4, snapshot.statuses().size());
        assertTrue(BattleContentCatalog.validate(snapshot).isEmpty());
        assertEquals(CombatElement.FIRE, snapshot.skills().get("fireball").element());
        assertEquals(CombatElement.FIRE, snapshot.skills().get("fire_bolt").element());
        assertEquals(CombatElement.NEUTRAL, snapshot.skills().get("heal").element());
    }

    @Test
    void catalogLookupNormalizesIds() {
        BattleContentCatalog.installForTests(BattleContentCatalog.defaults());

        assertEquals("fireball", BattleContentCatalog.findSkill("FIREBALL").id());
        assertEquals("Rotting Grasp", BattleContentCatalog.findSkill("ROTTING_GRASP").displayName());
    }

    @Test
    void registryGeneratedHandlerNeedsNoEditorSpecificCode() {
        String id = "test_effect_" + System.nanoTime();
        BattleContentTypeRegistry.registerEffect(new BattleContentTypeRegistry.HandlerDescriptor(
                id,
                "Test Effect",
                "UTILITY",
                List.of(AuthoringFieldDescriptor.integer("amount", "Amount", 2, 0, 10, "")),
                Set.of(RecipientScope.CASTER),
                Set.of(ActivationCondition.ALWAYS)));

        SkillEffectDefinition effect = new SkillEffectDefinition(
                id, RecipientScope.CASTER, ActivationCondition.ALWAYS, 1.0, Map.of("amount", "4"));

        assertNotNull(BattleContentTypeRegistry.effectDescriptor(id));
        assertTrue(BattleContentTypeRegistry.validateDefinition(effect).isEmpty());
    }

    @Test
    void unavailableFutureHandlerIsPreservedWithoutInvalidatingSnapshot() {
        BattleContentCatalog.Snapshot defaults = BattleContentCatalog.defaults();
        LinkedHashMap<String, SkillDefinition> skills = new LinkedHashMap<>(defaults.skills());
        skills.put("future_skill", new SkillDefinition(
                "future_skill", "Future Skill", "",
                Library.SkillTargetShape.SINGLE_TARGET, Library.EntityType.ENEMY,
                Library.BattleTargetingMode.MAGIC, "", "AUTO", 1, true,
                List.of(new SkillEffectDefinition(
                        "future_plugin_effect", RecipientScope.RESOLVED_TARGETS,
                        ActivationCondition.ALWAYS, 1.0, Map.of("opaque", "preserved")))));
        BattleContentCatalog.Snapshot snapshot = new BattleContentCatalog.Snapshot(
                1, skills, defaults.statuses(), defaults.defaultPlayerSkillIds(),
                defaults.universalPlayerSkillIds(), defaults.debugPlayerSkillIds());

        assertTrue(BattleContentCatalog.validate(snapshot).isEmpty());
        assertEquals("preserved", snapshot.skills().get("future_skill")
                .effects().getFirst().parameters().get("opaque"));
    }
}

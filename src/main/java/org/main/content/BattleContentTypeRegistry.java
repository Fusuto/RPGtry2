package org.main.content;

import org.main.content.AuthoringFieldDescriptor.FieldType;
import org.main.content.SkillEffectDefinition.ActivationCondition;
import org.main.content.SkillEffectDefinition.RecipientScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single extension point for battle content. Runtime handlers and the
 * editor use the same descriptor registry.
 */
public final class BattleContentTypeRegistry {
    private static final Map<String, HandlerDescriptor> EFFECTS = new LinkedHashMap<>();
    private static final Map<String, HandlerDescriptor> STATUS_BEHAVIORS = new LinkedHashMap<>();

    static {
        registerEffect(effect("damage", "Damage", "OFFENSE",
                List.of(AuthoringFieldDescriptor.integer("potency", "Potency", 5, 0, 100000,
                        "Base damage passed to the normal physical or magic combat resolver."))));
        registerEffect(effect("heal", "Heal", "HEAL",
                List.of(AuthoringFieldDescriptor.integer("potency", "Potency", 5, 0, 100000,
                        "Base healing passed to the normal healing resolver."))));
        registerEffect(effect("heal_from_damage", "Heal From Damage", "HEAL",
                List.of(AuthoringFieldDescriptor.decimal("percent", "Damage Percent", 50, 0, 1000,
                        "Percent of damage dealt by earlier effects restored to the recipient."))));
        registerEffect(effect("apply_status", "Apply Status", "STATUS",
                List.of(
                        AuthoringFieldDescriptor.reference("statusId", "Status", FieldType.STATUS_REFERENCE,
                                "Authored status to apply."),
                        AuthoringFieldDescriptor.integer("duration", "Duration", 1, 1, 999,
                                "Turns; zero in imported data uses the status default."),
                        AuthoringFieldDescriptor.integer("magnitude", "Magnitude", 0, -100000, 100000,
                                "Zero uses the status definition's default magnitude."))));
        registerEffect(effect("remove_status", "Cleanse / Dispel", "CLEANSE",
                List.of(
                        AuthoringFieldDescriptor.reference("statusId", "Specific Status", FieldType.STATUS_REFERENCE,
                                "Optional. Blank removes statuses selected by polarity."),
                        AuthoringFieldDescriptor.choice("polarity", "Polarity", "HARMFUL",
                                List.of("BENEFICIAL", "HARMFUL", "NEUTRAL", "ANY"),
                                "Polarity to remove when no specific status is selected."),
                        AuthoringFieldDescriptor.integer("count", "Maximum Removed", 1, 1, 99,
                                "Maximum number of statuses removed per recipient."))));
        registerEffect(effect("summon", "Summon", "SUMMON",
                List.of(
                        AuthoringFieldDescriptor.choice("mode", "Summon Mode", "SAME_SPECIES",
                                List.of("SAME_SPECIES", "SKELETON"), "How the summoned creature is selected."),
                        AuthoringFieldDescriptor.reference("speciesId", "Species", FieldType.MOB_REFERENCE,
                                "Optional explicit custom enemy id."),
                        AuthoringFieldDescriptor.decimal("successPercent", "Success %", 100, 0, 100,
                                "Chance that the summon succeeds after the skill is used."))));
        registerEffect(effect("no_op", "No Operation", "UTILITY", List.of()));
        registerEffect(effect("set_hp_percent", "Set HP Percent", "DEBUG",
                List.of(AuthoringFieldDescriptor.decimal("percent", "HP Percent", 10, 0, 100,
                        "Debug effect that sets current HP to this percentage."))));

        registerStatus(status("action_lock", "Action Lock",
                List.of(AuthoringFieldDescriptor.integer("magnitude", "Magnitude", 0, 0, 0,
                        "Action lock does not use magnitude."))));
        registerStatus(status("stat_modifier", "Stat Modifier",
                List.of(
                        AuthoringFieldDescriptor.choice("stat", "Stat", "AGILITY",
                                List.of("VITALITY", "ATTACK", "STRENGTH", "DEFENSE",
                                        "AGILITY", "INTELLIGENCE", "WILLPOWER"), "Stat modified while active."),
                        AuthoringFieldDescriptor.integer("magnitude", "Signed Magnitude", -2, -100000, 100000,
                                "Positive values buff; negative values debuff."))));
        registerStatus(status("periodic_health", "Periodic Health",
                List.of(AuthoringFieldDescriptor.integer("magnitude", "HP Per Turn", -1, -100000, 100000,
                        "Positive heals and negative damages at turn end."))));
        registerStatus(status("incoming_damage_modifier", "Incoming Damage Modifier",
                List.of(AuthoringFieldDescriptor.decimal("percent", "Damage Change %", -50, -95, 1000,
                        "Negative reduces incoming damage; positive increases it."))));
        registerStatus(status("outgoing_damage_modifier", "Outgoing Damage Modifier",
                List.of(AuthoringFieldDescriptor.decimal("percent", "Damage Change %", 25, -95, 1000,
                        "Signed percentage applied to outgoing damage."))));
        validate();
    }

    private BattleContentTypeRegistry() {
    }

    public static Collection<HandlerDescriptor> effectDescriptors() {
        return List.copyOf(EFFECTS.values());
    }

    public static Collection<HandlerDescriptor> statusBehaviorDescriptors() {
        return List.copyOf(STATUS_BEHAVIORS.values());
    }

    public static HandlerDescriptor effectDescriptor(String id) {
        return EFFECTS.get(normalize(id));
    }

    public static HandlerDescriptor statusBehaviorDescriptor(String id) {
        return STATUS_BEHAVIORS.get(normalize(id));
    }

    public static synchronized void registerEffect(HandlerDescriptor descriptor) {
        register(EFFECTS, descriptor);
    }

    public static synchronized void registerStatus(HandlerDescriptor descriptor) {
        register(STATUS_BEHAVIORS, descriptor);
    }

    public static List<String> validateDefinition(SkillEffectDefinition effect) {
        List<String> issues = new ArrayList<>();
        HandlerDescriptor descriptor = effectDescriptor(effect.kindId());
        if (descriptor == null) {
            issues.add("Unknown effect kind '" + effect.kindId() + "'.");
            return issues;
        }
        if (!descriptor.recipients().contains(effect.recipientScope())) {
            issues.add(descriptor.label() + " does not support recipient " + effect.recipientScope() + ".");
        }
        if (!descriptor.conditions().contains(effect.condition())) {
            issues.add(descriptor.label() + " does not support condition " + effect.condition() + ".");
        }
        validateParameters(effect.parameters(), descriptor, issues);
        return issues;
    }

    public static List<String> validateDefinition(StatusDefinition status) {
        List<String> issues = new ArrayList<>();
        HandlerDescriptor descriptor = statusBehaviorDescriptor(status.behaviorKindId());
        if (descriptor == null) {
            issues.add("Unknown status behavior '" + status.behaviorKindId() + "'.");
            return issues;
        }
        validateParameters(status.parameters(), descriptor, issues);
        return issues;
    }

    private static void validateParameters(
            Map<String, String> values, HandlerDescriptor descriptor, List<String> issues) {
        for (AuthoringFieldDescriptor field : descriptor.fields()) {
            String value = values.getOrDefault(field.key(), field.defaultValue());
            if (field.type() == FieldType.INTEGER || field.type() == FieldType.DECIMAL
                    || field.type() == FieldType.PERCENT) {
                try {
                    double number = Double.parseDouble(value);
                    if (number < field.minimum() || number > field.maximum()) {
                        issues.add(field.label() + " must be between " + field.minimum()
                                + " and " + field.maximum() + ".");
                    }
                } catch (NumberFormatException exception) {
                    issues.add(field.label() + " must be numeric.");
                }
            } else if (field.type() == FieldType.CHOICE
                    && !field.options().isEmpty()
                    && field.options().stream().noneMatch(value::equalsIgnoreCase)) {
                issues.add(field.label() + " has unsupported value '" + value + "'.");
            }
        }
    }

    private static void register(Map<String, HandlerDescriptor> target, HandlerDescriptor descriptor) {
        if (descriptor == null || normalize(descriptor.id()).isBlank()) {
            throw new IllegalArgumentException("Battle content handlers require an id.");
        }
        String id = normalize(descriptor.id());
        if (target.putIfAbsent(id, descriptor) != null) {
            throw new IllegalStateException("Duplicate battle content handler id " + id);
        }
    }

    private static void validate() {
        for (HandlerDescriptor descriptor : concat(EFFECTS.values(), STATUS_BEHAVIORS.values())) {
            if (descriptor.label().isBlank() || descriptor.aiRole().isBlank()) {
                throw new IllegalStateException("Incomplete battle handler descriptor " + descriptor.id());
            }
        }
    }

    private static Collection<HandlerDescriptor> concat(
            Collection<HandlerDescriptor> left, Collection<HandlerDescriptor> right) {
        List<HandlerDescriptor> values = new ArrayList<>(left);
        values.addAll(right);
        return values;
    }

    private static HandlerDescriptor effect(
            String id, String label, String role, List<AuthoringFieldDescriptor> fields) {
        return new HandlerDescriptor(
                id, label, role, fields,
                Set.of(RecipientScope.RESOLVED_TARGETS, RecipientScope.CASTER, RecipientScope.BOTH),
                Set.of(ActivationCondition.values()));
    }

    private static HandlerDescriptor status(
            String id, String label, List<AuthoringFieldDescriptor> fields) {
        return new HandlerDescriptor(
                id, label, "STATUS", fields,
                Set.of(RecipientScope.RESOLVED_TARGETS),
                Set.of(ActivationCondition.ALWAYS));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }

    public record HandlerDescriptor(
            String id,
            String label,
            String aiRole,
            List<AuthoringFieldDescriptor> fields,
            Set<RecipientScope> recipients,
            Set<ActivationCondition> conditions
    ) {
        public HandlerDescriptor {
            id = normalize(id);
            label = label == null ? "" : label;
            aiRole = aiRole == null ? "" : aiRole;
            fields = fields == null ? List.of() : List.copyOf(fields);
            recipients = recipients == null ? Set.of() : Set.copyOf(recipients);
            conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

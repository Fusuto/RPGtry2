package org.main.battle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Schedules readable battle actions and owns their impact-commit boundary. */
public final class BattlePresentationDirector {
    public static final int MAX_SIMULTANEOUS_ACTIONS = 2;

    public enum ActionType { AUTO_ATTACK, PHYSICAL_SKILL, RANGED, SPELL, HEAL, DEFEND, SUMMON, DEATH }
    public enum Reaction { HIT, BLOCK, DODGE, NONE }
    public enum Phase { WINDUP, IMPACT, RECOVERY }

    public record TargetReaction(BattleActor target, Reaction reaction, int damage) {
        public TargetReaction {
            reaction = reaction == null ? Reaction.NONE : reaction;
            damage = Math.max(0, damage);
        }
    }

    public record ActionSnapshot(
            long sequence,
            BattleActor attacker,
            String actionName,
            ActionType actionType,
            List<TargetReaction> targets,
            Phase phase,
            double progress
    ) {
        public ActionSnapshot {
            actionName = actionName == null ? "" : actionName;
            actionType = actionType == null ? ActionType.AUTO_ATTACK : actionType;
            targets = targets == null ? List.of() : List.copyOf(targets);
            phase = phase == null ? Phase.WINDUP : phase;
            progress = Math.max(0.0, Math.min(1.0, progress));
        }
    }

    private final List<ScheduledAction> pending = new ArrayList<>();
    private final List<ActiveAction> active = new ArrayList<>();
    private long nextSequence = 1L;

    public boolean enqueue(BattleActionIntent intent) {
        if (intent == null || (!intent.attacker().isAlive() && intent.actionType() != ActionType.DEATH)) {
            return false;
        }
        ScheduledAction existingPending = pending.stream()
                .filter(action -> action.intent().attacker() == intent.attacker())
                .findFirst().orElse(null);
        ActiveAction existingActive = active.stream()
                .filter(action -> action.intent().attacker() == intent.attacker())
                .findFirst().orElse(null);
        if (existingPending != null) {
            if (existingPending.intent().priority().value() >= intent.priority().value()) {
                return false;
            }
            existingPending.intent().cancelIfUncommitted();
            pending.remove(existingPending);
        }
        if (existingActive != null) {
            if (existingActive.intent().priority().value() >= intent.priority().value()) {
                return false;
            }
            boolean cancelledBeforeImpact = existingActive.intent().cancelIfUncommitted();
            if (!cancelledBeforeImpact && !existingActive.intent().impactCommitted()) return false;
            if (!cancelledBeforeImpact) existingActive.intent().complete();
            active.remove(existingActive);
        }
        pending.add(new ScheduledAction(nextSequence++, intent));
        pending.sort(Comparator.comparingInt((ScheduledAction action) -> action.intent().priority().value())
                .reversed().thenComparingLong(ScheduledAction::sequence));
        startEligibleActions();
        return true;
    }

    public void cancelForActor(BattleActor actor) {
        if (actor == null) return;
        pending.removeIf(action -> action.intent().attacker() == actor
                && action.intent().cancelIfUncommitted());
        active.removeIf(action -> action.intent().attacker() == actor
                && action.intent().cancelIfUncommitted());
        startEligibleActions();
    }

    public void cancelActionsInvolving(BattleActor actor) {
        if (actor == null) return;
        pending.removeIf(action -> involves(action.intent(), actor) && action.intent().cancelIfUncommitted());
        active.removeIf(action -> involves(action.intent(), actor) && action.intent().cancelIfUncommitted());
    }

    public boolean hasActionFor(BattleActor actor) {
        return actor != null && (pending.stream().anyMatch(action -> involves(action.intent(), actor))
                || active.stream().anyMatch(action -> involves(action.intent(), actor)));
    }

    public void clear() {
        pending.forEach(action -> action.intent().cancelIfUncommitted());
        active.forEach(action -> action.intent().cancelIfUncommitted());
        pending.clear();
        active.clear();
    }

    public void update(int deltaMs) {
        int safeDelta = Math.max(0, deltaMs);
        boolean focusFreeze = pausesCombatTimers();
        for (int index = active.size() - 1; index >= 0; index--) {
            ActiveAction current = active.get(index);
            if (focusFreeze && current.intent().actionType() != ActionType.SPELL
                    && current.intent().actionType() != ActionType.HEAL) {
                continue;
            }
            current = current.advance(safeDelta);
            if (current.complete()) active.remove(index); else active.set(index, current);
        }
        pending.removeIf(action -> hasDeadParticipant(action.intent())
                && action.intent().cancelIfUncommitted());
        active.removeIf(action -> hasDeadParticipant(action.intent())
                && action.intent().cancelIfUncommitted());
        if (!pausesCombatTimers()) startEligibleActions();
    }

    public boolean pausesCombatTimers() {
        return active.stream().anyMatch(action -> action.intent().lifecycle() == BattleActionIntent.Lifecycle.IMPACT
                && (action.intent().actionType() == ActionType.SPELL
                || action.intent().actionType() == ActionType.HEAL));
    }

    public List<ActionSnapshot> snapshots() {
        return active.stream().map(ActiveAction::snapshot).toList();
    }

    public boolean hasPresentations() { return !active.isEmpty() || !pending.isEmpty(); }

    private void startEligibleActions() {
        if (pending.isEmpty() || active.size() >= MAX_SIMULTANEOUS_ACTIONS || pausesCombatTimers()) return;
        Set<BattleActor> occupied = new HashSet<>();
        active.forEach(action -> addParticipants(occupied, action.intent()));
        for (int index = 0; index < pending.size() && active.size() < MAX_SIMULTANEOUS_ACTIONS;) {
            ScheduledAction candidate = pending.get(index);
            Set<BattleActor> participants = participants(candidate.intent());
            if (participants.stream().anyMatch(occupied::contains)) {
                index++;
                continue;
            }
            pending.remove(index);
            if (!candidate.intent().beginWindup()) continue;
            ActiveAction started = ActiveAction.start(candidate.sequence(), candidate.intent());
            active.add(started);
            occupied.addAll(participants(candidate.intent()));
        }
    }

    private static boolean involves(BattleActionIntent intent, BattleActor actor) {
        return intent.attacker() == actor
                || intent.participantHints().contains(actor)
                || intent.targets().stream().anyMatch(target -> target.target() == actor);
    }

    private static boolean hasDeadParticipant(BattleActionIntent intent) {
        if (intent.actionType() == ActionType.DEATH) return false;
        if (!intent.attacker().isAlive()) return true;
        return intent.targets().stream().anyMatch(target -> !target.target().isAlive());
    }

    private static Set<BattleActor> participants(BattleActionIntent intent) {
        Set<BattleActor> result = new HashSet<>();
        addParticipants(result, intent);
        return result;
    }

    private static void addParticipants(Set<BattleActor> result, BattleActionIntent intent) {
        result.add(intent.attacker());
        result.addAll(intent.participantHints());
        intent.targets().forEach(target -> result.add(target.target()));
    }

    private static int phaseDurationMs(BattleActionIntent intent, Phase phase) {
        boolean spell = intent.actionType() == ActionType.SPELL || intent.actionType() == ActionType.HEAL
                || intent.actionType() == ActionType.SUMMON || intent.actionType() == ActionType.DEFEND;
        int total = spell ? 1100 : 720;
        int impactStart = Math.max(1, (int) Math.round(total * intent.impactFraction()));
        return switch (phase) {
            case WINDUP -> impactStart;
            case IMPACT -> spell ? 300 : 100;
            case RECOVERY -> Math.max(1, total - impactStart);
        };
    }

    private record ScheduledAction(long sequence, BattleActionIntent intent) { }

    private record ActiveAction(long sequence, BattleActionIntent intent, Phase phase, int elapsedMs, boolean complete) {
        static ActiveAction start(long sequence, BattleActionIntent intent) {
            return new ActiveAction(sequence, intent, Phase.WINDUP, 0, false);
        }

        ActiveAction advance(int deltaMs) {
            int elapsed = elapsedMs + deltaMs;
            Phase next = phase;
            while (elapsed >= phaseDurationMs(intent, next)) {
                elapsed -= phaseDurationMs(intent, next);
                if (next == Phase.WINDUP) {
                    intent.enterImpact();
                    intent.commitImpact();
                    next = Phase.IMPACT;
                } else if (next == Phase.IMPACT) {
                    intent.enterRecovery();
                    if (intent.skipRecovery()) {
                        intent.complete();
                        return new ActiveAction(sequence, intent, Phase.RECOVERY, 0, true);
                    }
                    next = Phase.RECOVERY;
                } else {
                    intent.complete();
                    return new ActiveAction(sequence, intent, Phase.RECOVERY, 0, true);
                }
            }
            return new ActiveAction(sequence, intent, next, elapsed, false);
        }

        ActionSnapshot snapshot() {
            return new ActionSnapshot(sequence, intent.attacker(), intent.actionName(), intent.actionType(),
                    intent.targets(), phase, elapsedMs / (double) Math.max(1, phaseDurationMs(intent, phase)));
        }
    }
}

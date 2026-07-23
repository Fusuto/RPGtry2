package org.main.battle;

import java.util.List;
import java.util.Objects;

/** A combat action whose outcome is rolled at windup and committed exactly once at impact. */
public final class BattleActionIntent {
    public enum Priority {
        AUTOMATIC(0),
        MANUAL(10),
        DEATH(100);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public enum Lifecycle {
        QUEUED,
        WINDUP,
        IMPACT,
        RECOVERY,
        COMPLETE,
        CANCELLED
    }

    @FunctionalInterface
    public interface OutcomePlanner {
        OutcomePlan plan();
    }

    public record OutcomePlan(
            List<BattlePresentationDirector.TargetReaction> targets,
            Runnable impactCommit
    ) {
        public OutcomePlan {
            targets = targets == null ? List.of() : List.copyOf(targets);
            impactCommit = impactCommit == null ? () -> { } : impactCommit;
        }
    }

    private final BattleActor attacker;
    private final String actionName;
    private final BattlePresentationDirector.ActionType actionType;
    private final Priority priority;
    private final OutcomePlanner planner;
    private final double impactFraction;
    private final List<BattleActor> participantHints;
    private OutcomePlan outcome;
    private Lifecycle lifecycle = Lifecycle.QUEUED;
    private boolean impactCommitted;
    private boolean skipRecovery;

    public BattleActionIntent(
            BattleActor attacker,
            String actionName,
            BattlePresentationDirector.ActionType actionType,
            Priority priority,
            double impactFraction,
            OutcomePlanner planner
    ) {
        this(attacker, actionName, actionType, priority, impactFraction, List.of(), planner);
    }

    public BattleActionIntent(
            BattleActor attacker,
            String actionName,
            BattlePresentationDirector.ActionType actionType,
            Priority priority,
            double impactFraction,
            List<BattleActor> participantHints,
            OutcomePlanner planner
    ) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.actionName = actionName == null ? "" : actionName;
        this.actionType = actionType == null
                ? BattlePresentationDirector.ActionType.AUTO_ATTACK
                : actionType;
        this.priority = priority == null ? Priority.AUTOMATIC : priority;
        this.impactFraction = Double.isFinite(impactFraction)
                ? Math.max(0.05, Math.min(0.95, impactFraction))
                : 0.55;
        this.planner = Objects.requireNonNull(planner, "planner");
        this.participantHints = participantHints == null ? List.of() : participantHints.stream()
                .filter(Objects::nonNull).distinct().toList();
    }

    public BattleActor attacker() { return attacker; }
    public String actionName() { return actionName; }
    public BattlePresentationDirector.ActionType actionType() { return actionType; }
    public Priority priority() { return priority; }
    public double impactFraction() { return impactFraction; }
    public Lifecycle lifecycle() { return lifecycle; }
    public boolean impactCommitted() { return impactCommitted; }
    public boolean skipRecovery() { return skipRecovery; }
    public List<BattleActor> participantHints() { return participantHints; }
    public List<BattlePresentationDirector.TargetReaction> targets() {
        return outcome == null ? List.of() : outcome.targets();
    }

    boolean beginWindup() {
        if (lifecycle != Lifecycle.QUEUED
                || (!attacker.isAlive() && actionType != BattlePresentationDirector.ActionType.DEATH)) {
            lifecycle = Lifecycle.CANCELLED;
            return false;
        }
        outcome = planner.plan();
        if (outcome == null) {
            lifecycle = Lifecycle.CANCELLED;
            return false;
        }
        lifecycle = Lifecycle.WINDUP;
        return true;
    }

    void enterImpact() {
        if (lifecycle == Lifecycle.WINDUP) {
            lifecycle = Lifecycle.IMPACT;
        }
    }

    void commitImpact() {
        if (impactCommitted || outcome == null || lifecycle == Lifecycle.CANCELLED) {
            return;
        }
        impactCommitted = true;
        outcome.impactCommit().run();
    }

    void enterRecovery() {
        if (lifecycle == Lifecycle.IMPACT) {
            lifecycle = Lifecycle.RECOVERY;
        }
    }

    void complete() {
        lifecycle = Lifecycle.COMPLETE;
    }

    boolean cancelIfUncommitted() {
        if (impactCommitted || lifecycle == Lifecycle.IMPACT || lifecycle == Lifecycle.RECOVERY) {
            skipRecovery = true;
            return false;
        }
        lifecycle = Lifecycle.CANCELLED;
        return true;
    }
}

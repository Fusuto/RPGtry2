package org.main.battle;

public class BattleStatus {
    private final BattleStatusType type;
    private int remainingTurns;
    private int potency;

    public BattleStatus(BattleStatusType type, int remainingTurns) {
        this(type, remainingTurns, 0);
    }

    public BattleStatus(BattleStatusType type, int remainingTurns, int potency) {
        this.type = type;
        this.remainingTurns = Math.max(0, remainingTurns);
        this.potency = Math.max(0, potency);
    }

    public BattleStatusType getType() {
        return type;
    }

    public int getRemainingTurns() {
        return remainingTurns;
    }

    public int getPotency() {
        return potency;
    }

    public void refresh(int turns) {
        refresh(turns, potency);
    }

    public void refresh(int turns, int potency) {
        remainingTurns = Math.max(remainingTurns, turns);
        this.potency = Math.max(this.potency, potency);
    }

    public void tick() {
        if (remainingTurns > 0) {
            remainingTurns--;
        }
    }

    public boolean isExpired() {
        return remainingTurns <= 0;
    }
}

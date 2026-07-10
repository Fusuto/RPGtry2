package org.main.battle;

public class BattleStatus {
    private final BattleStatusType type;
    private int remainingTurns;

    public BattleStatus(BattleStatusType type, int remainingTurns) {
        this.type = type;
        this.remainingTurns = Math.max(0, remainingTurns);
    }

    public BattleStatusType getType() {
        return type;
    }

    public int getRemainingTurns() {
        return remainingTurns;
    }

    public void refresh(int turns) {
        remainingTurns = Math.max(remainingTurns, turns);
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

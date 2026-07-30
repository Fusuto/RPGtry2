package org.main.battle;

import org.main.content.BattleContentCatalog;
import org.main.content.StatusDefinition;

public class BattleStatus {
    private final String statusId;
    private int remainingTurns;
    private int potency;

    public BattleStatus(String statusId, int remainingTurns) {
        this(statusId, remainingTurns, 0);
    }

    public BattleStatus(String statusId, int remainingTurns, int potency) {
        this.statusId = BattleContentCatalog.normalizeId(statusId);
        this.remainingTurns = Math.max(0, remainingTurns);
        this.potency = potency;
    }

    public String getStatusId() {
        return statusId;
    }

    public StatusDefinition getDefinition() {
        StatusDefinition definition = BattleContentCatalog.findStatus(statusId);
        if (definition == null) {
            throw new IllegalStateException("Battle status definition is unavailable: " + statusId);
        }
        return definition;
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
        if (Math.abs(potency) > Math.abs(this.potency)) {
            this.potency = potency;
        }
    }

    public void replace(int turns, int potency) {
        remainingTurns = Math.max(0, turns);
        this.potency = potency;
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

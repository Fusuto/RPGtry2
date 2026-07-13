package org.main.battle;

import org.main.core.GameConfiguration;

public final class BattleTiming {
    private BattleTiming() {
    }

    public static double calculateAttackIntervalSeconds(int agility) {
        double slowestInterval = GameConfiguration.doubleValue("battle.attackInterval.slowestSeconds", 4.2);
        double fastestInterval = GameConfiguration.doubleValue("battle.attackInterval.fastestSeconds", 0.25);
        int minimumAgility = GameConfiguration.intValue("battle.attackInterval.minimumAgility", 1);
        int targetMaximumAgility = GameConfiguration.intValue("battle.attackInterval.targetMaximumAgility", 99);

        if (targetMaximumAgility <= minimumAgility) {
            return Math.max(fastestInterval, slowestInterval);
        }

        int effectiveAgility = Math.max(minimumAgility, agility);
        double reductionPerPoint = (slowestInterval - fastestInterval) / (targetMaximumAgility - minimumAgility);
        double interval = slowestInterval - ((effectiveAgility - minimumAgility) * reductionPerPoint);
        return Math.max(fastestInterval, interval);
    }
}

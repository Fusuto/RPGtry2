package org.main.tools;

import org.main.core.PlayerStat;

record StatTargetOption(String label, PlayerStat stat) {
    @Override
    public String toString() {
        return label;
    }
}

